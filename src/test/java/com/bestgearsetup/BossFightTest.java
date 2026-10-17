package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.MonsterGroup;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;

public class BossFightTest
{
	private static GameData data;

	@BeforeClass public static void load() throws Exception
	{
		data = new GameDataLoader(new Gson()).loadGameData();
	}

	@Test public void eachMultiPhaseBossHasOneFullFight()
	{
		List<RaidPlanType> fights = Arrays.stream(RaidPlanType.values()).filter(RaidPlanType::isBossFight).collect(Collectors.toList());
		assertEquals(9, fights.size());
		for (RaidPlanType fight : fights)
		{
			MonsterGroup group = data.groupOf(fight.encounters(data).stream().filter(e -> !e.isDisplayOnly())
				.filter(e -> RaidPlanType.fightOf(MonsterGroup.baseName(e.getTarget().getName())) == fight)
				.findFirst().get().getTarget(), false);
			assertEquals(fight, RaidPlanType.fightOf(group.getName()));
			assertEquals(fight, RaidPlanType.fightOf(data, group.getName()));
			// Boss fights are found through the boss's own search result, not listed beside the raids.
			assertTrue(RaidPlanType.search(group.getName()).isEmpty());
		}
		assertEquals(RaidPlanType.ZULRAH, RaidPlanType.fightOf("zulrah"));
		assertNull(RaidPlanType.fightOf("goblin"));
		assertFalse(RaidPlanType.TOB.isBossFight());
		// Data missing a target of the fight offers no full fight.
		GameData partial = new GameData(Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		assertNull(RaidPlanType.fightOf(partial, "zulrah"));
	}

	@Test public void phasesAreQuickSwitchesOfOneSoloKitWithinTheSwapLimit()
	{
		RaidScaling.Settings party = RaidScaling.Settings.builder().partySize(5).build();
		RaidOptions options = RaidOptions.builder().switchLimit(4).build();
		for (RaidPlanType fight : RaidPlanType.values())
		{
			if (!fight.isBossFight()) { continue; }
			List<RaidEncounter> encounters = fight.encounters(data, party, options);
			for (RaidEncounter encounter : encounters)
			{
				if (encounter.isDisplayOnly())
				{
					assertNull(encounter.getSwitchGroup());
					continue;
				}
				assertEquals(fight.toString(), encounter.getSwitchGroup());
				assertTrue(encounter.isFullSwitchSetup());
				assertEquals(4, encounter.getSwitchLimit());
				// Solo whatever the raid party size, boosted before the fight and without raid supplies.
				assertEquals(1, encounter.getPartySize());
				assertEquals(1, encounter.getKillShare());
				assertFalse(encounter.isRaidSupplies());
				assertTrue(encounter.isPrepotted());
				assertTrue(encounter.isStartable());
			}
			assertEquals(1, RaidEncounter.switchGroups(encounters).size());
			// The kit is worn in for a fight it plans.
			assertFalse(encounters.get(0).isDisplayOnly());
		}
	}

	@Test public void phasesSplitTheBossHitpoints()
	{
		assertShares(RaidPlanType.ZULRAH, 1, "zulrah");
		assertShares(RaidPlanType.ALCHEMICAL_HYDRA, 1, "alchemical hydra");
		assertShares(RaidPlanType.ARAXXOR, 1, "araxxor");
		assertShares(RaidPlanType.PHANTOM_MUSPAH, 1, "phantom muspah");
		assertShares(RaidPlanType.YAMA, 1, "yama");
		// Phase 1 is only shown; the planned phases deal the rest.
		assertShares(RaidPlanType.ABYSSAL_SIRE, 1, "abyssal sire");
		// Each form has its own HP.
		assertShares(RaidPlanType.KALPHITE_QUEEN, 2, "kalphite queen");
		assertShares(RaidPlanType.VETION, 2, "vet'ion");
		assertShares(RaidPlanType.CALVARION, 2, "calvar'ion");
		Map<String, Double> zulrah = RaidPlanType.ZULRAH.encounters(data).stream()
			.collect(Collectors.toMap(RaidEncounter::getVersionLabel, RaidEncounter::getShare));
		assertEquals(24 / 47.0, zulrah.get("Serpentine"), 1e-9);
		assertEquals(8 / 47.0, zulrah.get("Magma"), 1e-9);
		assertEquals(15 / 47.0, zulrah.get("Tanzanite"), 1e-9);
	}

	@Test public void addsAreKilledBackToBackAndOnlyTheLastPhaseEndsTheFight()
	{
		List<RaidEncounter> vetion = RaidPlanType.VETION.encounters(data);
		assertEquals(Arrays.asList("Vet'ion: Normal", "Skeleton Hellhound", "Vet'ion: Enraged", "Greater Skeleton Hellhound"),
			vetion.stream().map(RaidEncounter::getLabel).collect(Collectors.toList()));
		assertEquals(2, vetion.get(1).getRepetitions(), 0);
		assertEquals(0, vetion.get(1).getFinalBlow(), 0);
		assertFalse(vetion.get(1).isShared());
		assertEquals(0, vetion.get(0).getFinalBlow(), 0);
		assertEquals(1, vetion.get(2).getFinalBlow(), 0);
		List<RaidEncounter> hydra = RaidPlanType.ALCHEMICAL_HYDRA.encounters(data);
		assertEquals(Arrays.asList(0.0, 0.0, 0.0, 1.0), hydra.stream().map(RaidEncounter::getFinalBlow).collect(Collectors.toList()));
		List<RaidEncounter> yama = RaidPlanType.YAMA.encounters(data);
		// The Judge prays against your last style, so each Judge is half melee and half magic.
		List<RaidEncounter> judges = yama.stream().filter(e -> e.getTarget().getName().equals("judge of yama")).collect(Collectors.toList());
		assertEquals(Arrays.asList("Judge Of Yama: Melee", "Judge Of Yama: Magic"),
			judges.stream().map(RaidEncounter::getLabel).collect(Collectors.toList()));
		assertEquals(com.bestgearsetup.data.CombatClass.MELEE, judges.get(0).getFixedClass());
		assertEquals(com.bestgearsetup.data.CombatClass.MAGIC, judges.get(1).getFixedClass());
		for (RaidEncounter judge : judges)
		{
			assertEquals(2, judge.getRepetitions(), 0);
			assertEquals(0.5, judge.getShare(), 0);
		}
	}

	@Test public void fightsIncludeTheTargetsFoughtBesideTheBoss()
	{
		assertTargets(RaidPlanType.YAMA, "yama (normal)", "void flare (fire)", "void flare (shadow)", "judge of yama", "yama (phase 3)");
		assertTargets(RaidPlanType.ABYSSAL_SIRE, "respiratory system", "abyssal sire (phase 1)", "abyssal sire (phase 2)",
			"abyssal sire (phase 3 (stage 1))", "abyssal sire (phase 3 (stage 2))");
		assertTargets(RaidPlanType.ARAXXOR, "araxxor (in combat)", "mirrorback araxyte", "acidic araxyte", "araxxor (enraged)");
		assertTargets(RaidPlanType.VETION, "vet'ion (normal)", "skeleton hellhound (vet'ion)", "vet'ion (enraged)",
			"greater skeleton hellhound (vet'ion)");
		assertTargets(RaidPlanType.CALVARION, "calvar'ion (normal)", "skeleton hellhound (calvar'ion)", "calvar'ion (enraged)",
			"greater skeleton hellhound (calvar'ion)");
		// Phase 3 flares spawn at half their health.
		RaidEncounter lateFlares = RaidPlanType.YAMA.encounters(data).stream().filter(e -> e.getLabel().equals("Void Flare: Phase 3"))
			.findFirst().get();
		assertEquals(0.5, lateFlares.getShare(), 0);
		assertEquals(0, lateFlares.getFinalBlow(), 0);
	}

	private static void assertTargets(RaidPlanType fight, String... targets)
	{
		assertEquals(Arrays.asList(targets), fight.encounters(data).stream().map(e -> e.getTarget().getName()).distinct()
			.collect(Collectors.toList()));
	}

	/** Planned phases of the boss itself (not its adds) deal {@code pools} times its HP. */
	private static void assertShares(RaidPlanType fight, double pools, String boss)
	{
		double total = fight.encounters(data).stream().filter(e -> !e.isDisplayOnly())
			.filter(e -> MonsterGroup.baseName(e.getTarget().getName()).equals(boss))
			.mapToDouble(RaidEncounter::getShare).sum();
		assertEquals(fight.toString(), pools, total, 1e-9);
	}
}
