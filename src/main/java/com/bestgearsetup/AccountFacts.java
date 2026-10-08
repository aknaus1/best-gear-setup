package com.bestgearsetup;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;

/** Account facts are read on the client thread and remembered only in the RuneScape profile. */
final class AccountFacts
{
	private static final Map<String, List<String>> TARGETS = loadTargets();
	private AccountFacts() {}

	/** Parsed without Gson: this runs at class load, before the client's Gson can be passed in. */
	private static Map<String, List<String>> loadTargets()
	{
		try (InputStreamReader reader = new InputStreamReader(AccountFacts.class.getResourceAsStream("slayer-targets.json"), StandardCharsets.UTF_8))
		{
			Map<String, List<String>> targets = new HashMap<>();
			for (Map.Entry<String, JsonElement> entry : new JsonParser().parse(reader).getAsJsonObject().entrySet())
			{
				List<String> aliases = new ArrayList<>();
				for (JsonElement alias : entry.getValue().getAsJsonArray())
				{
					aliases.add(alias.getAsString());
				}
				targets.put(entry.getKey(), aliases);
			}
			return targets;
		}
		catch (Exception e) { throw new IllegalStateException("Cannot load Slayer target aliases", e); }
	}

	static Boolean diary(Client client, ConfigManager manager)
	{
		String key = "detectedKandarinHard";
		if (ready(client))
		{
			boolean complete = client.getVarbitValue(VarbitID.KANDARIN_DIARY_HARD_COMPLETE) == 1;
			if (!String.valueOf(complete).equals(manager.getRSProfileConfiguration(BestGearSetupConfig.GROUP, key)))
			{
				manager.setRSProfileConfiguration(BestGearSetupConfig.GROUP, key, complete);
			}
			return complete;
		}
		String value = manager.getRSProfileConfiguration(BestGearSetupConfig.GROUP, key);
		return "true".equals(value) ? true : "false".equals(value) ? false : null;
	}

	static boolean ready(Client client)
	{
		return client.getGameState() == GameState.LOGGED_IN && client.getRealSkillLevel(Skill.HITPOINTS) > 0;
	}

	/** @param gson the client's Gson, for the remembered assignment */
	static TaskRecord task(Client client, ConfigManager manager, Gson gson)
	{
		String key = "detectedSlayerTask";
		TaskRecord record = null;
		if (ready(client))
		{
			int remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
			if (remaining == 0) { record = new TaskRecord("", 0, false); }
			else
			{
				try
				{
					int task = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
					int row;
					if (task == 98)
					{
						List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,
							DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
						if (rows.isEmpty()) { return null; }
						row = (Integer) client.getDBTableField(rows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0)[0];
					}
					else
					{
						List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, task);
						if (rows.isEmpty()) { return null; }
						row = rows.get(0);
					}
					String name = (String) client.getDBTableField(row, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)[0];
					int area = client.getVarpValue(VarPlayerID.SLAYER_AREA);
					String location = null;
					if (area > 0)
					{
						List<Integer> areas = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, area);
						if (!areas.isEmpty())
						{ location = (String) client.getDBTableField(areas.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0)[0]; }
					}
					record = new TaskRecord(name, remaining, area > 0, location);
				}
				catch (RuntimeException e) { return null; }
			}
			String json = gson.toJson(record);
			if (!json.equals(manager.getRSProfileConfiguration(BestGearSetupConfig.GROUP, key)))
			{
				manager.setRSProfileConfiguration(BestGearSetupConfig.GROUP, key, json);
			}
		}
		else
		{
			String value = manager.getRSProfileConfiguration(BestGearSetupConfig.GROUP, key);
			try { record = value == null ? null : gson.fromJson(value, TaskRecord.class); }
			catch (RuntimeException e) { return null; }
		}
		return record;
	}

	static String normalize(String value)
	{
		return value.toLowerCase(Locale.ROOT).replace('\u00a0', ' ').replace('\u2019', '\'')
			.replaceFirst("^the ", "").trim();
	}

	static Boolean matches(TaskRecord record, String target)
	{
		return matches(record, target, null);
	}

	static Boolean matches(TaskRecord record, String target, WorldPoint location)
	{
		if (record == null || record.name == null || record.remaining < 0) { return null; }
		if (record.remaining == 0) { return false; }
		String name = normalize(record.name);
		List<String> aliases = TARGETS.get(name);
		if (aliases == null && name.endsWith("s")) { aliases = TARGETS.get(name.substring(0, name.length() - 1)); }
		if (aliases == null) { return null; }
		String npc = normalize(target);
		boolean matches = aliases.stream().anyMatch(alias -> Pattern.compile("(?:^|\\s)" + Pattern.quote(normalize(alias))
			+ "(?:$|\\s|\\(|-)").matcher(npc).find());
		if (!matches || !record.locationRestricted) { return matches; }
		return atAssignedLocation(record.location, location);
	}

	/** Unknown areas remain unknown rather than granting a location-restricted task bonus. */
	static Boolean atAssignedLocation(String assigned, WorldPoint location)
	{
		if (assigned == null || location == null) { return null; }
		if (normalize(assigned).equals("fremennik slayer dungeon"))
		{
			// RuneLite's Discord area catalog identifies the three regions of this dungeon.
			int region = location.getRegionID();
			return location.getPlane() == 0 && (region == 10907 || region == 10908 || region == 11164);
		}
		if (isKaruulmAssignment(assigned)) { return inKaruulm(location); }
		return null;
	}

	private static boolean isKaruulmAssignment(String assigned)
	{
		return assigned != null && normalize(assigned).equals("karuulm slayer dungeon");
	}

	/** Dungeon regions from RuneLite's Discord area catalog; all dungeon floors are included. */
	static boolean inKaruulm(WorldPoint location)
	{
		if (location == null) { return false; }
		switch (location.getRegionID())
		{
			case 5280: case 5279: case 5023: case 5535: case 5022: case 4766:
			case 4510: case 4511: case 4767: case 4768: case 4512:
				return true;
			default: return false;
		}
	}

	/** A visible target determines its location; otherwise a matching Konar assignment guides planning. */
	static boolean karuulmSearch(TaskRecord record, String target, WorldPoint liveTarget, WorldPoint player)
	{
		if (liveTarget != null) { return inKaruulm(liveTarget); }
		if (record != null && record.locationRestricted && isKaruulmAssignment(record.location)
			&& Boolean.TRUE.equals(matches(new TaskRecord(record.name, record.remaining, false), target)))
		{
			return true;
		}
		return inKaruulm(player);
	}

	static String describe(TaskRecord record)
	{
		if (record == null) { return "Slayer assignment unavailable."; }
		if (record.remaining == 0) { return "No remaining Slayer assignment."; }
		return "Slayer assignment: " + record.name + " (" + record.remaining + " remaining)"
			+ (record.locationRestricted ? ", assigned location: " + (record.location == null ? "unavailable" : record.location) : "") + ".";
	}

	static final class TaskRecord
	{
		final String name;
		final int remaining;
		final boolean locationRestricted;
		final String location;
		TaskRecord(String name, int remaining, boolean locationRestricted)
		{
			this(name, remaining, locationRestricted, null);
		}
		TaskRecord(String name, int remaining, boolean locationRestricted, String location)
		{
			this.name = name; this.remaining = remaining; this.locationRestricted = locationRestricted; this.location = location;
		}
	}
}
