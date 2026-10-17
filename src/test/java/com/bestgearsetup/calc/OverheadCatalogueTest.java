package com.bestgearsetup.calc;

import com.bestgearsetup.calc.ProtectionPrayers.Advice;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exhaustive recommendation coverage, not a claim that every infobox describes all encounter mechanics. */
public class OverheadCatalogueTest
{
	@Test
	public void everyVariantHandlesGearDistanceAndPrayerBoundaries() throws Exception
	{
		GameDataLoader loader = new GameDataLoader(new Gson());
		List<Map<String, Object>> records = new ArrayList<>();
		int cases = 0;
		for (MonsterSummary summary : loader.loadGameData().getMonsters())
		{
			Monster monster = loader.getMonster(summary);
			Map<String, Object> record = new LinkedHashMap<>();
			record.put("name", monster.getName());
			record.put("boss", summary.isBoss());
			record.put("attackStyles", monster.getAttackStyles());
			Map<String, Object> defaults = new LinkedHashMap<>();
			for (CombatClass cls : CombatClass.values())
			{
				for (int profile = 0; profile < 4; profile++)
				{
					SetupResult setup = setup(cls, profile);
					for (int level : new int[]{30, 37, 40, 43, 99})
					{
						PlayerLevels levels = new PlayerLevels(99, 99, 99, 99, 99, level, 99, 99);
						for (int distance : new int[]{0, 1, 2})
						{
							OptimizerSettings settings = OptimizerSettings.builder().targetDistance(distance).build();
							Advice advice = ProtectionPrayers.recommend(monster, setup, level,
								new ProtectionPrayers.Context(monster, levels, settings));
							assertNotNull(monster.getName(), advice);
							assertNotNull(monster.getName(), advice.summary());
							assertNotNull(monster.getName(), advice.getNote());
							if (advice.getPrayer() == null) { assertTrue(monster.getName(), advice.getSwitches().isEmpty()); }
							else
							{
								assertTrue(monster.getName(), advice.getSwitches().contains(advice.getPrayer()));
								assertEquals(monster.getName(), advice.getPrayer().getLevel() > level, advice.isLocked());
							}
							if (advice.isSwitching())
							{
								assertTrue("Mixed infobox data must not invent switches: " + monster.getName(),
								advice.getSource() != null || EncounterPrayers.recommend(monster, cls, distance == 1 || distance == 0 && cls == CombatClass.MELEE) != null
									|| ProtectionPrayers.curated(monster, true) != null);
							}
							if (profile == 1 && level == 99 && distance == 0)
							{
								Map<String, Object> decision = new LinkedHashMap<>();
								decision.put("summary", advice.summary());
								decision.put("note", advice.getNote());
								decision.put("source", advice.getSource());
								decision.put("classification", advice.isUnknown() ? advice.getNote().contains("Special-mode")
									? "unverified special mode" : advice.getNote().contains("fire protection requirements")
									? "insufficient fire protection" : "unknown attack data"
									: advice.getSource() != null || ProtectionPrayers.curated(monster, cls == CombatClass.MELEE) != null
									? "encounter rule" : advice.getPrayer() == null ? "no blockable attack in snapshot"
									: advice.getNote().contains("estimated incoming") ? "ordinary damage estimate" : "style / max-hit fallback");
								defaults.put(cls.name(), decision);
							}
							cases++;
						}
						}
					}
			}
			record.put("representativeAdvice", defaults);
			records.add(record);
		}
		assertTrue("No silent catalogue truncation", records.size() > 3000);
		Path destination = Paths.get("build", "reports", "overheads", "catalogue.json");
		Files.createDirectories(destination.getParent());
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("variants", records.size());
		report.put("cases", cases);
		report.put("method", "Every bundled variant, 3 combat classes, 4 armour / shield profiles, 5 Prayer levels, 3 distances. "
			+ "Representative advice uses 99 base levels, 99 Prayer, plate armour plus anti-dragon shield, and automatic distance. "
			+ "Generic damage estimates are not independent verification of encounter timing or special attacks.");
		report.put("monsters", records);
		Files.write(destination, new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
	}

	private static SetupResult setup(CombatClass cls, int profile)
	{
		GearItem weapon = new GearItem();
		weapon.setName("representative one-handed weapon");
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, weapon);
		loadout.setStyle(AttackStyle.parse(cls == CombatClass.MELEE ? "slash,slash,aggressive"
			: cls == CombatClass.RANGED ? "rapid,ranged,rapid" : "spell,magic,magic"));
		GearItem armour = new GearItem();
		armour.setName("representative armour");
		armour.setStabDef(profile == 1 ? 250 : profile == 2 ? 100 : 0);
		armour.setSlashDef(armour.getStabDef());
		armour.setCrushDef(armour.getStabDef());
		armour.setRangedDef(profile == 1 ? 300 : profile == 2 ? 100 : 0);
		armour.setMagicDef(profile == 1 ? -30 : profile == 2 ? 150 : profile == 3 ? 100 : 0);
		loadout.set(Slot.BODY, armour);
		if (profile == 1)
		{
			GearItem shield = new GearItem();
			shield.setName("anti-dragon shield");
			loadout.set(Slot.SHIELD, shield);
		}
		return new SetupResult(cls, loadout, null, 0);
	}
}
