package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class RaidSpellbookTest
{
	@Test public void autoComparesAvailableBooksAndManualChoicesAreHonoured()
	{
		BestGearSetupConfig config = new BestGearSetupConfig() {};
		assertEquals(RaidSpellbook.AUTO, config.raidSpellbook());
		assertEquals(Arrays.asList(RaidSpellbook.ARCEUUS, RaidSpellbook.STANDARD, RaidSpellbook.ANCIENT),
			RaidSpellbook.candidates(RaidPlanType.TOA, config));
		BestGearSetupConfig unavailable = SearchAssumptions.capture(config,
			java.util.Map.of("arceuusSpellbook", false, "ancientMagicks", false));
		assertEquals(Collections.singletonList(RaidSpellbook.STANDARD), RaidSpellbook.candidates(RaidPlanType.COX_CM, unavailable));
		BestGearSetupConfig manual = SearchAssumptions.capture(config, Collections.singletonMap("raidSpellbook", RaidSpellbook.STANDARD));
		assertEquals(Collections.singletonList(RaidSpellbook.STANDARD), RaidSpellbook.candidates(RaidPlanType.TOB, manual));
	}

	@Test public void bothFreezeRolesOverrideEvenAManualBook()
	{
		for (RaidOptions.TobRole role : Arrays.asList(RaidOptions.TobRole.SOUTH_FREEZE, RaidOptions.TobRole.NORTH_FREEZE))
		{
			BestGearSetupConfig config = SearchAssumptions.capture(new BestGearSetupConfig() {},
				java.util.Map.of("tobRole", role, "raidSpellbook", RaidSpellbook.STANDARD));
			assertEquals(Collections.singletonList(RaidSpellbook.ANCIENT), RaidSpellbook.candidates(RaidPlanType.TOB, config));
		}
	}

	@Test(expected = IllegalStateException.class) public void freezeCannotBypassAncientAvailability()
	{
		RaidSpellbook.candidates(RaidPlanType.TOB, SearchAssumptions.capture(new BestGearSetupConfig() {},
			java.util.Map.of("tobRole", RaidOptions.TobRole.SOUTH_FREEZE, "ancientMagicks", false)));
	}

	@Test(expected = IllegalStateException.class) public void disabledManualBookCannotProduceAMislabelledPoweredStaffKit()
	{
		RaidSpellbook.candidates(RaidPlanType.TOA, SearchAssumptions.capture(new BestGearSetupConfig() {},
			java.util.Map.of("raidSpellbook", RaidSpellbook.ARCEUUS, "arceuusSpellbook", false)));
	}

	@Test public void bookValueUsesFightTimeWithHpAndRepetitionsRatherThanSummedDps()
	{
		RaidPlan thralls = plan(RaidSpellbook.ARCEUUS, 20, 5);
		RaidPlan fire = plan(RaidSpellbook.STANDARD, 10, 10);
		// Arceuus has the larger sum of DPS, but the repeated high-HP fight makes Standard faster.
		assertEquals(605, RaidSpellbook.seconds(thralls), 1e-9);
		assertEquals(310, RaidSpellbook.seconds(fire), 1e-9);
		assertTrue(RaidSpellbook.faster(fire, thralls));
		assertFalse(RaidSpellbook.faster(thralls, fire));
		assertFalse(RaidSpellbook.faster(plan(RaidSpellbook.ANCIENT, 10, 10), fire));
		assertFalse(RaidSpellbook.faster(null, fire));
		assertTrue(RaidSpellbook.faster(fire, null));
	}

	private static RaidPlan plan(RaidSpellbook book, double smallDps, double largeDps)
	{
		SetupResult small = setup(smallDps), large = setup(largeDps);
		SearchResults smallResult = result(100), largeResult = result(1000);
		return new RaidPlan(RaidPlanType.COX_CM, book,
			Arrays.asList(new RaidEncounter("Small", new MonsterSummary()), new RaidEncounter("Repeated", new MonsterSummary(), 3)),
			Arrays.asList(smallResult, largeResult), Arrays.asList(small, large), Collections.emptyList(), Collections.emptyList(), 20, 0);
	}

	private static SetupResult setup(double dps)
	{
		return new SetupResult(CombatClass.MELEE, new Loadout(), new DpsResult(dps, 0, 1, 4, 10, null), 0);
	}

	private static SearchResults result(int hp)
	{
		return new SearchResults(new Monster(), hp, Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), false);
	}
}
