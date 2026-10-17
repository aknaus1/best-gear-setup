package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.data.MonsterSummary;
import org.junit.Test;

public class ToaPathLevelsTest
{
	private static MonsterSummary target(String name)
	{
		MonsterSummary summary = new MonsterSummary(); summary.setName(name); return summary;
	}

	@Test public void bothOrdersShareProgressionAcrossBossPhasesAndMinions()
	{
		for (RaidOptions.ToaPathOrder order : RaidOptions.ToaPathOrder.AUTO.candidates())
		{
			BestGearSetupConfig config = new BestGearSetupConfig()
			{
				@Override public RaidOptions.ToaPathOrder toaPathOrder() { return order; }
				@Override public int toaStartingPathLevel() { return 3; }
				@Override public boolean toaWalkThePath() { return true; }
			};
			String[] names = {"ba-ba", "kephri (shielded)", "soldier scarab", "arcane scarab", "spitting scarab",
				"akkha", "akkha's shadow", "zebak (enraged)"};
			int[] positions = order == RaidOptions.ToaPathOrder.BA_BA_FIRST
				? new int[]{0, 1, 1, 1, 1, 2, 2, 3} : new int[]{3, 1, 1, 1, 1, 2, 2, 0};
			for (int i = 0; i < names.length; i++)
			{
				RaidScaling.Settings resolved = ToaPathLevels.resolve(target(names[i]), RaidScaling.Settings.SOLO, config, new LiveRaid(), true);
				assertEquals(names[i], positions[i], resolved.getToaCompletedPaths());
				assertEquals(3, resolved.getToaPathLevel());
				assertTrue(resolved.isToaWalkThePath());
			}
			assertFalse(ToaPathLevels.resolve(target("tumeken's warden (phase 3)"), RaidScaling.Settings.SOLO, config, new LiveRaid(), true).isToaWalkThePath());
		}
	}

	/** A plan made under Auto keeps one fixed order; refreshing its encounters must use that order, not Auto's default. */
	@Test public void planOrderComesFromWhetherZebakOrBaBaIsFoughtFirst()
	{
		java.util.List<RaidEncounter> zebakFirst = java.util.Arrays.asList(new RaidEncounter("Obelisk", target("obelisk")),
			new RaidEncounter("Zebak", target("zebak")), new RaidEncounter("Kephri", target("kephri")),
			new RaidEncounter("Ba-Ba", target("ba-ba")));
		assertEquals(RaidOptions.ToaPathOrder.ZEBAK_FIRST, ToaPathLevels.orderOf(zebakFirst));
		java.util.List<RaidEncounter> baBaFirst = java.util.Arrays.asList(new RaidEncounter("Baboon", target("baboon brawler")),
			new RaidEncounter("Zebak", target("zebak")));
		assertEquals(RaidOptions.ToaPathOrder.BA_BA_FIRST, ToaPathLevels.orderOf(baBaFirst));
	}

	@Test public void liveLevelsIncludingZeroWinAndSingleEncountersKeepManualLevel()
	{
		BestGearSetupConfig config = new BestGearSetupConfig()
		{
			@Override public int toaStartingPathLevel() { return 3; }
			@Override public boolean toaWalkThePath() { return true; }
		};
		RaidScaling.Settings base = RaidScaling.Settings.builder().toaPathLevel(5).build();
		assertEquals(5, ToaPathLevels.resolve(target("kephri"), base, config, new LiveRaid(), false).getToaPathLevel());
		LiveRaid live = new LiveRaid(); live.pathLevels.put("kephri", 0); live.pathLevels.put("akkha", 4);
		for (String name : new String[]{"kephri (shielded)", "soldier scarab", "arcane scarab", "spitting scarab", "akkha's shadow"})
		{
			RaidScaling.Settings resolved = ToaPathLevels.resolve(target(name), base, config, live, true);
			assertEquals(name.startsWith("akkha") ? 4 : 0, resolved.getToaPathLevel());
			assertFalse(resolved.isToaWalkThePath());
		}
		BestGearSetupConfig disabled = new BestGearSetupConfig()
		{
			@Override public int toaStartingPathLevel() { return 2; }
		};
		RaidScaling.Settings resolved = ToaPathLevels.resolve(target("zebak"), base, disabled, new LiveRaid(), true);
		assertEquals(2, resolved.getToaPathLevel()); assertFalse(resolved.isToaWalkThePath());
	}
}
