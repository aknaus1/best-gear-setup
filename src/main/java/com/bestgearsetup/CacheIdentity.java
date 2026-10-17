package com.bestgearsetup;

import com.bestgearsetup.data.GameData;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Deterministic value identities, including the installed calculation code and bundled data. */
final class CacheIdentity
{
	private static volatile Gson gson;
	private static final Map<GameData, String> DATA = Collections.synchronizedMap(new WeakHashMap<>());
	/** Bundled data the calculations read; every file must be present for a persistent identity. */
	static final List<String> DATA_RESOURCES = List.of("encounter-prayers.json", "equipment-requirements.json",
		"raid-prayers.json", "slayer-targets.json", "status-immunities.json", "wiki-gamedata.json.gz",
		"wiki-monsters.json.gz");
	private static final String PACKAGE = "com/bestgearsetup/";
	private static final String ROOT = PACKAGE + "BestGearSetupPlugin";
	private static final Pattern CLASS_NAME = Pattern.compile(PACKAGE + "[A-Za-z0-9_$/]+");
	/** Bundled resources by path; tests substitute their own. */
	static final Function<String, InputStream> BUNDLED = name -> CacheIdentity.class.getResourceAsStream("/" + name);
	private static final String BUILD = build(BUNDLED);

	private CacheIdentity() { }

	/** Initialized with the client's injected serializer before searches can run. */
	static void init(Gson clientGson)
	{
		gson = java.util.Objects.requireNonNull(clientGson);
	}

	static String buildId() { return BUILD; }

	static String data(GameData data)
	{
		return DATA.computeIfAbsent(data, value -> digest(json(gson.toJsonTree(value))));
	}

	static String digest(String text)
	{
		MessageDigest hash = hash();
		return hex(hash.digest(text.getBytes(StandardCharsets.UTF_8)));
	}

	static String value(Object value)
	{
		if (value == null) { return "null"; }
		if (value instanceof SearchInputs) { return ((SearchInputs) value).persistentKey(); }
		if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte
			|| value instanceof Boolean || value instanceof Double)
		{
			return value.getClass().getName() + ":" + value;
		}
		if (value instanceof Enum)
		{
			Enum<?> item = (Enum<?>) value;
			return item.getDeclaringClass().getName() + ":" + item.name();
		}
		boolean large = value instanceof Collection && ((Collection<?>) value).size() > LARGE_SIZE
			|| value instanceof Map && ((Map<?, ?>) value).size() > LARGE_SIZE;
		if (large)
		{
			String known = LARGE.get(value);
			if (known != null) { return known; }
		}
		String identity = structure(value);
		if (large)
		{
			// Searches share their price, ownership and exclusion snapshots, so remember their digests by content.
			identity = "large:" + digest(identity);
			LARGE.put(copy(value), identity);
		}
		return identity;
	}

	/** Collections whose digest is remembered by content: comparing contents costs far less than serialising them. */
	private static final int LARGE_SIZE = 64;
	private static final Map<Object, String> LARGE = Collections.synchronizedMap(new LinkedHashMap<Object, String>(16, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Object, String> eldest)
		{
			return size() > 32;
		}
	});

	private static Object copy(Object value)
	{
		if (value instanceof Map) { return new HashMap<>((Map<?, ?>) value); }
		if (value instanceof Set) { return new HashSet<>((Set<?>) value); }
		return new ArrayList<>((Collection<?>) value);
	}

	private static String structure(Object value)
	{
		if (value instanceof Map)
		{
			List<String> entries = new ArrayList<>();
			((Map<?, ?>) value).forEach((key, item) -> entries.add(value(key) + "=" + value(item)));
			Collections.sort(entries);
			return "map" + gson.toJson(entries);
		}
		if (value instanceof Collection)
		{
			Collection<?> items = (Collection<?>) value;
			if (value instanceof Set && !items.isEmpty() && items.stream().allMatch(Integer.class::isInstance))
			{
				// Item id sets, the bulk of every key: sort numerically rather than as strings.
				int[] ids = items.stream().mapToInt(Integer.class::cast).sorted().toArray();
				return "intset" + java.util.Arrays.toString(ids);
			}
			List<String> entries = items.stream().map(CacheIdentity::value).collect(Collectors.toList());
			if (value instanceof Set) { Collections.sort(entries); }
			return (value instanceof Set ? "set" : "list") + gson.toJson(entries);
		}
		return value.getClass().getName() + ":" + json(gson.toJsonTree(value));
	}

	/** Gson object member order is not a stable identity; sort members recursively. */
	private static String json(JsonElement element)
	{
		return ordered(element).toString();
	}

	private static JsonElement ordered(JsonElement element)
	{
		if (element.isJsonObject())
		{
			JsonObject sorted = new JsonObject();
			element.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey())
				.forEach(entry -> sorted.add(entry.getKey(), ordered(entry.getValue())));
			return sorted;
		}
		if (element.isJsonArray())
		{
			com.google.gson.JsonArray values = new com.google.gson.JsonArray();
			element.getAsJsonArray().forEach(item -> values.add(ordered(item)));
			return values;
		}
		return element;
	}

	static String build(Function<String, InputStream> resources)
	{
		try
		{
			MessageDigest hash = hash();
			for (String name : DATA_RESOURCES)
			{
				byte[] data = read(resources, PACKAGE + name);
				if (data == null) { throw new IOException("Missing bundled data " + name); }
				update(hash, name, data);
			}
			for (Map.Entry<String, byte[]> entry : classes(resources).entrySet()) { update(hash, entry.getKey(), entry.getValue()); }
			return hex(hash.digest());
		}
		catch (Exception e)
		{
			// A compatibility identity must never silently accept another installation's saved results.
			return "session-" + java.util.UUID.randomUUID();
		}
	}

	/**
	 * Digests of the plugin classes reachable from the plugin entry point. Classes are found by following the
	 * references in each class file's constant pool and read with getResourceAsStream, so nothing depends on how
	 * or where the plugin is installed.
	 */
	static Map<String, byte[]> classes(Function<String, InputStream> resources) throws IOException
	{
		Map<String, byte[]> found = new TreeMap<>();
		Set<String> seen = new HashSet<>();
		Deque<String> pending = new ArrayDeque<>(Collections.singleton(ROOT));
		while (!pending.isEmpty())
		{
			String name = pending.pop();
			if (!seen.add(name)) { continue; }
			// Names matched inside other strings, such as resource paths, simply have no class file.
			byte[] code = read(resources, name + ".class");
			if (code == null) { continue; }
			found.put(name, hash().digest(code));
			pending.addAll(references(code));
		}
		if (!found.containsKey(ROOT)) { throw new IOException("Missing plugin classes"); }
		return found;
	}

	/** Every plugin class name mentioned in a class file's constant pool, including in type descriptors. */
	private static List<String> references(byte[] code) throws IOException
	{
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(code));
		if (in.readInt() != 0xCAFEBABE) { throw new IOException("Invalid class file"); }
		in.readInt();
		int count = in.readUnsignedShort();
		List<String> names = new ArrayList<>();
		for (int index = 1; index < count; index++)
		{
			int tag = in.readUnsignedByte();
			switch (tag)
			{
				case 1:
					byte[] text = new byte[in.readUnsignedShort()];
					in.readFully(text);
					Matcher matcher = CLASS_NAME.matcher(new String(text, StandardCharsets.ISO_8859_1));
					while (matcher.find()) { names.add(matcher.group()); }
					break;
				case 5:
				case 6:
					// Long and double constants occupy two pool slots.
					in.readLong();
					index++;
					break;
				case 3:
				case 4:
				case 9:
				case 10:
				case 11:
				case 12:
				case 17:
				case 18:
					in.readInt();
					break;
				case 15:
					in.readUnsignedByte();
					in.readUnsignedShort();
					break;
				case 7:
				case 8:
				case 16:
				case 19:
				case 20:
					in.readUnsignedShort();
					break;
				default:
					throw new IOException("Unknown class constant " + tag);
			}
		}
		return names;
	}

	private static byte[] read(Function<String, InputStream> resources, String resource) throws IOException
	{
		try (InputStream stream = resources.apply(resource))
		{
			return stream == null ? null : stream.readAllBytes();
		}
	}

	private static void update(MessageDigest hash, String name, byte[] content)
	{
		hash.update(name.getBytes(StandardCharsets.UTF_8));
		hash.update((byte) 0);
		hash.update(content);
	}

	private static MessageDigest hash()
	{
		try { return MessageDigest.getInstance("SHA-256"); }
		catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}

	private static String hex(byte[] bytes)
	{
		StringBuilder result = new StringBuilder();
		for (byte value : bytes) { result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); }
		return result.toString();
	}
}
