package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.MonsterSummary;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

public class FightSearchKeyTest
{
	private static GameData data;
	private static List<RaidEncounter> zulrah;
	private final BestGearSetupConfig config = new BestGearSetupConfig() { };

	@BeforeClass
	public static void load() throws Exception
	{
		CacheIdentity.init(net.runelite.http.api.RuneLiteAPI.GSON);
		data = new GameDataLoader(new Gson()).loadGameData();
		zulrah = RaidPlanType.ZULRAH.encounters(data);
	}

	private SearchInputs key(MonsterSummary from, BestGearSetupConfig settings, List<String> notes)
	{
		return SearchInputs.captureFight(data, data, FightSearchKey.searchedFrom(zulrah, from, settings, notes),
			PlayerLevels.maxed(), false, new HashSet<>(), true, 99, new HashMap<>(), new HashSet<>(), new HashMap<>(),
			settings, new LiveRaid(), notes).with(RaidPlanType.ZULRAH);
	}

	@Test
	public void searchedPhaseOnlyMattersWithLandedSpecsOrNotes()
	{
		MonsterSummary serpentine = zulrah.get(0).getTarget();
		MonsterSummary magma = zulrah.get(1).getTarget();
		assertEquals(Collections.emptyList(), FightSearchKey.searchedFrom(zulrah, serpentine, config, Collections.emptyList()));
		assertEquals(key(serpentine, config, Collections.emptyList()), key(magma, config, Collections.emptyList()));
		BestGearSetupConfig specs = SearchAssumptions.capture(config, Map.of("specDwh", 2));
		assertEquals(List.of(zulrah.get(0).getLabel()), FightSearchKey.searchedFrom(zulrah, serpentine, specs, Collections.emptyList()));
		assertNotEquals(key(serpentine, specs, Collections.emptyList()), key(magma, specs, Collections.emptyList()));
		List<String> notes = List.of("Kandarin hard diary: unknown");
		assertEquals(List.of(zulrah.get(1).getLabel()), FightSearchKey.searchedFrom(zulrah, magma, config, notes));
		assertNotEquals(key(serpentine, config, notes), key(magma, config, notes));
		BestGearSetupConfig vulnerability = SearchAssumptions.capture(config, Map.of("specVulnerability", true));
		assertTrue(FightSearchKey.specsLanded(vulnerability));
		assertFalse(FightSearchKey.specsLanded(config));
	}

	@Test
	public void presentationAndBossStateDoNotChangeTheKeyButCalculationSettingsDo()
	{
		MonsterSummary serpentine = zulrah.get(0).getTarget();
		SearchInputs before = key(serpentine, config, Collections.emptyList());
		for (String name : SearchInputs.PRESENTATION)
		{
			assertEquals(name, before, key(serpentine, SearchAssumptions.capture(config, Map.of(name, changed(name))),
				Collections.emptyList()));
		}
		assertEquals(before, key(serpentine, SearchAssumptions.capture(config,
			Map.of("encounterPhase", EncounterPhase.AKKHA_ENRAGE)), Collections.emptyList()));
		for (String name : Set.of("raidEquipmentLimit", "quickSwitchLimit", "raidSpellbook", "budget", "killSpecials"))
		{
			assertNotEquals(name, before, key(serpentine, SearchAssumptions.capture(config, Map.of(name, changed(name))),
				Collections.emptyList()));
		}
	}

	/** A different value of the named setting, of its own type. */
	private Object changed(String name)
	{
		Object value = ConfigSettings.read(config).get(name);
		if (value instanceof Boolean) { return !(Boolean) value; }
		if (value instanceof Integer) { return (Integer) value == 1 ? 2 : 1; }
		if (value instanceof Long) { return (Long) value + 1; }
		if (value instanceof String) { return value + "x"; }
		if (value instanceof java.awt.Color) { return new java.awt.Color(((java.awt.Color) value).getRGB() ^ 0xFFFFFF); }
		if (value instanceof Enum)
		{
			Object[] constants = ((Enum<?>) value).getDeclaringClass().getEnumConstants();
			return constants[(((Enum<?>) value).ordinal() + 1) % constants.length];
		}
		throw new AssertionError("No changed value for " + name + " (" + value + ")");
	}
}
