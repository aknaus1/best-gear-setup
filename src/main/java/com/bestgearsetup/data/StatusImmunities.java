package com.bestgearsetup.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.Value;

/**
 * Poison, venom, freeze and burn immunities from a bundled snapshot of the OSRS Wiki's monster
 * infoboxes (tools/wiki_status_immunities.py), consulted by NPC id, then by the variant's
 * base name. Target flags supplement the snapshot and serve as a fallback when no Wiki entry exists.
 */
public final class StatusImmunities
{
	/** Burn severities a target can resist: a burn is blocked when its severity is at most the immunity. */
	public static final int BURN_NONE = 0;
	public static final int BURN_WEAK = 1;
	public static final int BURN_NORMAL = 2;
	public static final int BURN_STRONG = 3;

	private static final String RESOURCE = "/com/bestgearsetup/status-immunities.json";
	private static volatile Map<String, int[]> byId;
	private static volatile Map<String, int[]> byName;

	private StatusImmunities()
	{
	}

	@Value
	public static class Immunity
	{
		boolean poisonImmune;
		boolean venomImmune;
		/** Venom applied to this target becomes poison. */
		boolean venomBecomesPoison;
		/** Freeze resistance in percent, or -1 when unknown. */
		int freezeResistance;
		/** Highest burn severity this target ignores (0 = none). */
		int burnImmunity;
		/** Whether Wiki data was found; otherwise only the target's flags were used. */
		boolean known;
	}

	public static Immunity of(Monster m)
	{
		load();
		int[] record = byId.get(Integer.toString(m.getId()));
		if (record == null)
		{
			String name = m.getName().toLowerCase(Locale.ROOT);
			int bracket = name.indexOf(" (");
			record = byName.get(bracket > 0 ? name.substring(0, bracket) : name);
		}
		if (record == null)
		{
			return new Immunity(m.isImmunePoison(), m.isImmuneVenom(), m.isImmuneVenom() && !m.isImmunePoison(),
				-1, BURN_NONE, false);
		}
		boolean poison = record[0] >= 100 || m.isImmunePoison();
		boolean venomPoisons = record[1] == -2;
		boolean venom = venomPoisons || record[1] >= 100 || m.isImmuneVenom();
		return new Immunity(poison, venom, venom && !poison, record[2], Math.max(0, record[3]), true);
	}

	private static void load()
	{
		if (byId != null)
		{
			return;
		}
		synchronized (StatusImmunities.class)
		{
			if (byId != null)
			{
				return;
			}
			Map<String, int[]> ids = new HashMap<>();
			Map<String, int[]> names = new HashMap<>();
			try (InputStream in = StatusImmunities.class.getResourceAsStream(RESOURCE))
			{
				if (in != null)
				{
					try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
					{
						JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
						read(root.getAsJsonObject("ids"), ids);
						read(root.getAsJsonObject("names"), names);
					}
				}
			}
			catch (Exception ex)
			{
				// Fall back to the target's flags alone.
				ids.clear();
				names.clear();
			}
			byName = Collections.unmodifiableMap(names);
			byId = Collections.unmodifiableMap(ids);
		}
	}

	private static void read(JsonObject object, Map<String, int[]> out)
	{
		if (object == null)
		{
			return;
		}
		for (Map.Entry<String, JsonElement> e : object.entrySet())
		{
			JsonArray a = e.getValue().getAsJsonArray();
			int[] values = new int[4];
			for (int i = 0; i < 4 && i < a.size(); i++)
			{
				values[i] = a.get(i).getAsInt();
			}
			out.put(e.getKey(), values);
		}
	}
}
