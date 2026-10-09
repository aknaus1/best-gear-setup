package com.bestgearsetup.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link GameData} from JSON in the plugin's catalogue schema (equipment by slot, weapons, spells,
 * prayers and potions), which the bundled Wiki snapshot is written in.
 */
public final class GameDataJson
{
	private GameDataJson()
	{
	}

	static GameData parse(Gson gson, JsonObject monsters, JsonObject equipment, JsonObject weapons,
		JsonObject spells, JsonObject prayers, JsonObject potions)
	{
		TypeToken<List<MonsterSummary>> monsterList = new TypeToken<List<MonsterSummary>>()
		{
		};
		TypeToken<Map<String, List<GearItem>>> equipmentMap = new TypeToken<Map<String, List<GearItem>>>()
		{
		};
		TypeToken<List<GearItem>> gearList = new TypeToken<List<GearItem>>()
		{
		};
		TypeToken<List<Spell>> spellList = new TypeToken<List<Spell>>()
		{
		};
		TypeToken<Map<String, List<Prayer>>> prayerMap = new TypeToken<Map<String, List<Prayer>>>()
		{
		};
		TypeToken<Map<String, List<Potion>>> potionMap = new TypeToken<Map<String, List<Potion>>>()
		{
		};

		return new GameData(
			gson.fromJson(monsters.get("monsters"), monsterList.getType()),
			gson.fromJson(equipment.get("equipment"), equipmentMap.getType()),
			gson.fromJson(weapons.get("weapons"), gearList.getType()),
			gson.fromJson(spells.get("spells"), spellList.getType()),
			gson.fromJson(prayers.get("prayers"), prayerMap.getType()),
			potions == null ? null : gson.fromJson(potions.get("potions"), potionMap.getType()));
	}
}
