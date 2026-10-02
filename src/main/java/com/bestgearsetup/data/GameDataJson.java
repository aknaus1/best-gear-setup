package com.bestgearsetup.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
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
		Type monsterList = new TypeToken<List<MonsterSummary>>()
		{
		}.getType();
		Type equipmentMap = new TypeToken<Map<String, List<GearItem>>>()
		{
		}.getType();
		Type gearList = new TypeToken<List<GearItem>>()
		{
		}.getType();
		Type spellList = new TypeToken<List<Spell>>()
		{
		}.getType();
		Type prayerMap = new TypeToken<Map<String, List<Prayer>>>()
		{
		}.getType();
		Type potionMap = new TypeToken<Map<String, List<Potion>>>()
		{
		}.getType();

		return new GameData(
			gson.fromJson(monsters.get("monsters"), monsterList),
			gson.fromJson(equipment.get("equipment"), equipmentMap),
			gson.fromJson(weapons.get("weapons"), gearList),
			gson.fromJson(spells.get("spells"), spellList),
			gson.fromJson(prayers.get("prayers"), prayerMap),
			potions == null ? null : gson.fromJson(potions.get("potions"), potionMap));
	}
}
