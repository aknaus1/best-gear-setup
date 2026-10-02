package com.bestgearsetup.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Monster stats from the bundled OSRS Wiki snapshot (tools/wiki_monsters.py), in the plugin's monster
 * schema. Nothing is downloaded at runtime. Variants are keyed by name because several share an NPC id.
 */
public final class WikiMonsters
{
	private static final String RESOURCE = "/com/bestgearsetup/wiki-monsters.json.gz";
	private static volatile WikiMonsters instance;

	private final List<MonsterSummary> summaries;
	private final Map<String, Monster> byName;
	private final String retrieved;

	private WikiMonsters(List<MonsterSummary> summaries, Map<String, Monster> byName, String retrieved)
	{
		this.summaries = summaries;
		this.byName = byName;
		this.retrieved = retrieved;
	}

	/** Snapshot date (yyyy-mm-dd). */
	public String getRetrieved()
	{
		return retrieved;
	}

	public static WikiMonsters get(Gson gson) throws IOException
	{
		WikiMonsters loaded = instance;
		if (loaded == null)
		{
			synchronized (WikiMonsters.class)
			{
				loaded = instance;
				if (loaded == null)
				{
					loaded = load(gson);
					instance = loaded;
				}
			}
		}
		return loaded;
	}

	private static WikiMonsters load(Gson gson) throws IOException
	{
		InputStream in = WikiMonsters.class.getResourceAsStream(RESOURCE);
		if (in == null)
		{
			throw new IOException("Bundled Wiki monster data is missing");
		}
		List<MonsterSummary> summaries = new ArrayList<>();
		Map<String, Monster> byName = new HashMap<>();
		String retrieved;
		try (Reader reader = new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8))
		{
			JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
			String stamp = root.has("retrievedUtc") ? root.get("retrievedUtc").getAsString() : "";
			retrieved = stamp.length() >= 10 ? stamp.substring(0, 10) : "unknown date";
			for (JsonElement element : root.getAsJsonArray("monsters"))
			{
				JsonObject record = element.getAsJsonObject();
				Monster monster = gson.fromJson(record, Monster.class);
				MonsterSummary summary = new MonsterSummary();
				summary.setId(monster.getId());
				summary.setName(monster.getName());
				summary.setCombatLevel(monster.getCombatLevel());
				summary.setBoss(record.has("boss") && record.get("boss").getAsBoolean());
				List<Integer> ids = new ArrayList<>();
				for (JsonElement id : record.getAsJsonArray("ids"))
				{
					ids.add(id.getAsInt());
				}
				summary.setIds(ids);
				List<String> attributes = new ArrayList<>();
				JsonArray attrs = record.getAsJsonArray("attributes");
				for (JsonElement a : attrs == null ? new JsonArray() : attrs)
				{
					attributes.add(a.getAsJsonObject().get("name").getAsString());
				}
				summary.setAttributes(attributes);
				summaries.add(summary);
				byName.put(monster.getName(), monster);
			}
		}
		catch (RuntimeException e)
		{
			throw new IOException("Bundled Wiki monster data is unreadable", e);
		}
		return new WikiMonsters(Collections.unmodifiableList(summaries), byName, retrieved);
	}

	public List<MonsterSummary> getSummaries()
	{
		return summaries;
	}

	/** A copy of the variant's stats, or null when the name is not in the snapshot. */
	public Monster monster(String name)
	{
		Monster m = byName.get(name);
		return m == null ? null : m.copy();
	}
}
