package com.bestgearsetup;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Bounded compressed result records in the selected RuneScape profile; loaded only on an exact-key miss.
 *
 * <p>Every ConfigManager write posts ConfigChanged synchronously to all plugins, so writes go through
 * {@link #write}, which the plugin backs with the client thread. Search workers only read and encode.
 */
@Slf4j
final class ProfileSearchCache<V>
{
	/**
	 * The RS profile is synced per account; together these keep the search cache under 1.75 MiB. A saved raid plan
	 * keeps only its chosen setups (about 30 KiB for ToB), so the raid share holds a dozen; the rest goes to single
	 * targets. Raids' own encounter searches are never saved here.
	 */
	static final int RAID_RECORDS = 12;
	static final int RAID_BYTES = 640 * 1024;
	static final int ENCOUNTER_RECORDS = 64;
	static final int ENCOUNTER_BYTES = 1152 * 1024;
	/** 3: Gson fields with write-order references (SearchResultCodec); raid plans keep only their chosen setups. */
	private static final int SCHEMA = 3;
	private static final int MAX_EXPANDED_BYTES = 32 * 1024 * 1024;
	/** Keep each synced setting small; RuneLite rejects full raid records stored as one large value. */
	static final int CHUNK_SIZE = 16 * 1024;
	private final ConfigManager config;
	private final String profile;
	private final String prefix;
	/** "raid" or "encounter", for log lines. */
	private final String kind;
	private final String build;
	private final int capacity;
	private final int maxBytes;
	private final Class<V> type;
	private final SearchResultCodec codec;
	private final Executor writer;

	/** An encoded result, prepared off the client thread so the profile write does no heavy work. */
	@Value
	static class Record
	{
		String key;
		String value;
		String digest;
	}

	ProfileSearchCache(ConfigManager config, Gson gson, String kind, Class<V> type, int capacity, int maxBytes, Executor writer)
	{
		this(config, gson, kind, type, capacity, maxBytes, writer, CacheIdentity.buildId());
	}

	ProfileSearchCache(ConfigManager config, Gson gson, String kind, Class<V> type, int capacity, int maxBytes, Executor writer,
		String build)
	{
		this.config = config;
		this.profile = config.getRSProfileKey();
		this.prefix = "searchCache." + kind + ".";
		this.kind = kind;
		this.type = type;
		this.capacity = capacity;
		this.maxBytes = maxBytes;
		this.writer = writer;
		this.build = build;
		this.codec = new SearchResultCodec(gson);
	}

	static ProfileSearchCache<RaidPlan> raids(ConfigManager config, Gson gson, Executor writer)
	{
		return new ProfileSearchCache<>(config, gson, "raid", RaidPlan.class, RAID_RECORDS, RAID_BYTES, writer);
	}

	static ProfileSearchCache<SearchResults> encounters(ConfigManager config, Gson gson, Executor writer)
	{
		return new ProfileSearchCache<>(config, gson, "encounter", SearchResults.class, ENCOUNTER_RECORDS, ENCOUNTER_BYTES, writer);
	}

	/** Runs a profile write where other plugins' ConfigChanged handlers expect it (the client thread). */
	void write(Runnable task)
	{
		writer.execute(task);
	}

	V read(String key)
	{
		return read(key, null);
	}

	V read(String key, com.bestgearsetup.data.GameData catalogue)
	{
		if (!current()) { return null; }
		try
		{
			String record = readRecord(key);
			if (record == null || record.length() > maxBytes) { return null; }
			JsonObject saved = new JsonParser().parse(unpack(record)).getAsJsonObject();
			if (saved.get("schema").getAsInt() != SCHEMA || !key.equals(saved.get("key").getAsString())) { return null; }
			if (!build.equals(saved.get("build").getAsString()))
			{
				// Any change to the plugin's code or data may change results, so a rebuilt plugin searches again.
				log.info("Saved {} search is from a different plugin build; searching again", kind);
				return null;
			}
			V result = codec.decode(saved.get("result"), type, catalogue);
			validate(result);
			log.info("Restored {} search from profile cache", kind);
			return result;
		}
		catch (Exception e)
		{
			log.info("Ignoring unreadable {} search cache: {}", kind, e.toString());
			log.debug("Unreadable search cache detail", e);
			return null;
		}
	}

	/** Called on the search worker with a completed result: encodes, compresses and hashes without touching config. */
	Record prepare(String key, V value)
	{
		if (!current()) { return null; }
		try
		{
			JsonObject saved = new JsonObject();
			saved.addProperty("schema", SCHEMA); saved.addProperty("build", build); saved.addProperty("key", key);
			saved.add("result", codec.encode(value instanceof RaidPlan ? ((RaidPlan) value).compacted() : value));
			String record = pack(saved.toString());
			if (record.length() > maxBytes)
			{
				log.info("Not saving {} search: {} characters exceeds the {} character profile cache", kind, record.length(), maxBytes);
				return null;
			}
			return new Record(key, record, CacheIdentity.digest(record));
		}
		catch (Exception e) { log.debug("Could not encode {} search cache", type.getSimpleName(), e); return null; }
	}

	/** Profile writes; call only through {@link #write}. */
	void save(Record record)
	{
		if (record == null || !current()) { return; }
		try
		{
			String key = record.getKey();
			LinkedHashMap<String, Integer> index = adopt(true);
			index.remove(key);
			long bytes = index.values().stream().mapToLong(Integer::longValue).sum();
			for (String old : new ArrayList<>(index.keySet()))
			{
				if (index.size() < capacity && bytes + record.getValue().length() <= maxBytes) { break; }
				bytes -= index.remove(old);
				removeRecord(old);
			}
			index.put(key, record.getValue().length());
			sweep(index);
			String value = record.getValue();
			String partPrefix = key + ".chunk." + record.getDigest() + ".";
			int parts = (value.length() + CHUNK_SIZE - 1) / CHUNK_SIZE;
			for (int part = 0; part < parts; part++)
			{
				config.setConfiguration(BestGearSetupConfig.GROUP, profile, prefix + partPrefix + part,
					value.substring(part * CHUNK_SIZE, Math.min(value.length(), (part + 1) * CHUNK_SIZE)));
			}
			JsonObject manifest = new JsonObject();
			manifest.addProperty("format", 2); manifest.addProperty("parts", parts);
			manifest.addProperty("length", value.length()); manifest.addProperty("digest", record.getDigest());
			// Publish the manifest last. Existing readers can still use the previous generation while chunks write.
			config.setConfiguration(BestGearSetupConfig.GROUP, profile, prefix + key, manifest.toString());
			writeIndex(index);
			for (String old : config.getRSProfileConfigurationKeys(BestGearSetupConfig.GROUP, profile, prefix + key + ".chunk."))
			{
				if (!old.startsWith(prefix + partPrefix)) { config.unsetConfiguration(BestGearSetupConfig.GROUP, profile, old); }
			}
			if (type == RaidPlan.class) { log.info("Saved raid search in {} profile cache chunks ({} characters)", parts, value.length()); }
		}
		catch (Exception e) { log.debug("Could not persist {} search cache", type.getSimpleName(), e); }
	}

	/**
	 * Applies this cache's bounds to what an earlier session saved. Records from another plugin build can never be
	 * read again, so they are dropped outright; the rest are trimmed oldest-first to the current limits.
	 */
	void prune()
	{
		if (!current()) { return; }
		try
		{
			LinkedHashMap<String, Integer> index = adopt(false);
			long bytes = index.values().stream().mapToLong(Integer::longValue).sum();
			boolean trimmed = false;
			for (String old : new ArrayList<>(index.keySet()))
			{
				if (index.size() <= capacity && bytes <= maxBytes) { break; }
				bytes -= index.remove(old);
				removeRecord(old);
				trimmed = true;
			}
			sweep(index);
			if (trimmed) { writeIndex(index); }
		}
		catch (Exception e) { log.debug("Could not prune {} search cache", type.getSimpleName(), e); }
	}

	/** The saved index, after dropping everything another plugin build wrote. Saves (and purges) record this build. */
	private LinkedHashMap<String, Integer> adopt(boolean saving)
	{
		if (build.equals(readConfig("build"))) { return index(); }
		int removed = 0;
		for (String old : config.getRSProfileConfigurationKeys(BestGearSetupConfig.GROUP, profile, prefix))
		{
			config.unsetConfiguration(BestGearSetupConfig.GROUP, profile, old);
			removed++;
		}
		if (removed > 0) { log.info("Cleared {} {} search cache settings saved by a different plugin build", removed, kind); }
		if (saving || removed > 0) { config.setConfiguration(BestGearSetupConfig.GROUP, profile, prefix + "build", build); }
		return new LinkedHashMap<>();
	}

	/** Recovers bounded storage even if a prior write was interrupted before updating its index. */
	private void sweep(Map<String, Integer> index)
	{
		for (String old : config.getRSProfileConfigurationKeys(BestGearSetupConfig.GROUP, profile, prefix))
		{
			String suffix = old.startsWith(prefix) ? old.substring(prefix.length()) : "";
			String digest = suffix.length() >= 64 ? suffix.substring(0, 64) : "";
			if (digest.matches("[0-9a-f]{64}") && !index.containsKey(digest))
			{ config.unsetConfiguration(BestGearSetupConfig.GROUP, profile, old); }
		}
	}

	private void writeIndex(Map<String, Integer> index)
	{
		JsonObject entries = new JsonObject(); index.forEach(entries::addProperty);
		config.setConfiguration(BestGearSetupConfig.GROUP, profile, prefix + "index", entries.toString());
	}

	private boolean current()
	{
		return profile != null && profile.equals(config.getRSProfileKey());
	}

	private LinkedHashMap<String, Integer> index()
	{
		LinkedHashMap<String, Integer> index = new LinkedHashMap<>();
		try
		{
			String saved = readConfig("index");
			if (saved == null || saved.length() > 64 * 1024) { return index; }
			for (Map.Entry<String, com.google.gson.JsonElement> entry : new JsonParser().parse(saved).getAsJsonObject().entrySet())
			{
				int size = entry.getValue().getAsInt();
				if (entry.getKey().matches("[0-9a-f]{64}") && size > 0 && size <= maxBytes && readConfig(entry.getKey()) != null)
				{ index.put(entry.getKey(), size); }
			}
		}
		catch (RuntimeException ignored) { index.clear(); }
		return index;
	}

	private String readConfig(String key)
	{
		return config.getConfiguration(BestGearSetupConfig.GROUP, profile, prefix + key, String.class);
	}

	/** Legacy small records remain readable; format 2 verifies all chunks and their full content checksum. */
	private String readRecord(String key)
	{
		String saved = readConfig(key);
		if (saved == null || !saved.startsWith("{")) { return saved; }
		JsonObject manifest = new JsonParser().parse(saved).getAsJsonObject();
		if (manifest.get("format").getAsInt() != 2) { return null; }
		int count = manifest.get("parts").getAsInt();
		int length = manifest.get("length").getAsInt();
		String digest = manifest.get("digest").getAsString();
		if (length <= 0 || length > maxBytes || count != (length + CHUNK_SIZE - 1) / CHUNK_SIZE
			|| !digest.matches("[0-9a-f]{64}")) { return null; }
		StringBuilder record = new StringBuilder(length);
		for (int part = 0; part < count; part++)
		{
			String chunk = readConfig(key + ".chunk." + digest + "." + part);
			if (chunk == null || chunk.length() != Math.min(CHUNK_SIZE, length - part * CHUNK_SIZE)) { return null; }
			record.append(chunk);
		}
		String value = record.toString();
		return digest.equals(CacheIdentity.digest(value)) ? value : null;
	}

	private void removeRecord(String key)
	{
		config.unsetConfiguration(BestGearSetupConfig.GROUP, profile, prefix + key);
		for (String chunk : config.getRSProfileConfigurationKeys(BestGearSetupConfig.GROUP, profile, prefix + key + ".chunk."))
		{ config.unsetConfiguration(BestGearSetupConfig.GROUP, profile, chunk); }
	}

	private static void validate(Object result)
	{
		if (result instanceof RaidPlan)
		{
			RaidPlan plan = (RaidPlan) result;
			if (plan.getType() == null || plan.getSpellbook() == null || plan.getEncounters() == null
				|| plan.getEncounters().isEmpty() || plan.getResults() == null || plan.getSetups() == null
				|| plan.getResults().size() != plan.getEncounters().size() || plan.getSetups().size() != plan.getEncounters().size()
				|| plan.getEquipment() == null || plan.getInventory() == null || plan.getNotes() == null || plan.getWarnings() == null)
			{ throw new IllegalArgumentException("Incomplete saved raid plan"); }
			plan.getResults().forEach(ProfileSearchCache::validate);
			for (com.bestgearsetup.calc.SetupResult setup : plan.getSetups())
			{
				if (setup == null || setup.getLoadout() == null) { throw new IllegalArgumentException("Missing saved setup"); }
			}
		}
		else if (result instanceof SearchResults)
		{
			SearchResults found = (SearchResults) result;
			if (found.getMonster() == null || found.getByType() == null || found.getNotes() == null || found.getPrayers() == null
				|| found.getPotions() == null || found.getSetupPotions() == null || found.getLocks() == null || found.getWarnings() == null)
			{ throw new IllegalArgumentException("Incomplete saved encounter"); }
		}
	}

	private static String pack(String value) throws IOException
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream output = new SmallestGzip(bytes)) { output.write(value.getBytes(StandardCharsets.UTF_8)); }
		return Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	/** Records are written once and synced with the profile, so spend the extra compression time. */
	private static final class SmallestGzip extends GZIPOutputStream
	{
		SmallestGzip(OutputStream output) throws IOException
		{
			super(output);
			def.setLevel(Deflater.BEST_COMPRESSION);
		}
	}

	private static String unpack(String value) throws IOException
	{
		try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value))))
		{
			byte[] bytes = input.readNBytes(MAX_EXPANDED_BYTES + 1);
			if (bytes.length > MAX_EXPANDED_BYTES) { throw new IOException("Cache result exceeds limit"); }
			return new String(bytes, StandardCharsets.UTF_8);
		}
	}
}
