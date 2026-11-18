package com.bestgearsetup.data;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.*;

/**
 * Monster stats from the bundled OSRS Wiki snapshot (tools/wiki_monsters.py), in the plugin's monster
 * schema. Nothing is downloaded at runtime. Variants are keyed by name because several share an NPC id.
 */
public final class WikiMonsters
{
	private static final String RESOURCE = "/com/bestgearsetup/wiki-monsters.json.gz";
	private static volatile WikiMonsters instance;

	private final List<Summary> summaries;
	private final Map<String, Monster> byName;
	private final String retrieved;

	/** The snapshot as written by the tool. */
	private static final class Snapshot
	{
		private String retrievedUtc;
		private List<Entry> monsters;
	}

	/** A monster record, which also lists every NPC id of the variant. */
	private static final class Entry extends Monster
	{
		private List<Integer> ids;
	}

	private WikiMonsters(List<Summary> summaries, Map<String, Monster> byName, String retrieved)
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
		return WikiGameData.read(gson, RESOURCE, Snapshot.class, "monster", root ->
		{
			List<Summary> summaries = new ArrayList<>();
			Map<String, Monster> byName = new HashMap<>();
			for (Entry monster : root.monsters)
			{
				Summary summary = new Summary();
				summary.setId(monster.getId());
				summary.setName(monster.getName());
				summary.setCombatLevel(monster.getCombatLevel());
				summary.setBoss(monster.isBoss());
				summary.setIds(monster.ids);
				List<String> attributes = new ArrayList<>();
				for (Monster.Attribute a : monster.getAttributes() == null ? List.<Monster.Attribute>of() : monster.getAttributes())
				{
					attributes.add(a.getName());
				}
				summary.setAttributes(attributes);
				summaries.add(summary);
				byName.put(monster.getName(), monster);
			}
			return new WikiMonsters(Collections.unmodifiableList(summaries), byName, WikiGameData.date(root.retrievedUtc));
		});
	}

	public List<Summary> getSummaries()
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
