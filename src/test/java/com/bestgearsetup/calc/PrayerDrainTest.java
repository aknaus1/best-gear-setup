package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class PrayerDrainTest
{
	@Test public void drainMatchesTheWikiRates()
	{
		// Protect from Melee alone drains a point every 3 seconds; Piety every 1.5; together every second.
		assertEquals(3, PrayerDrain.secondsPerPoint(PrayerDrain.drainEffect("Protect from Melee"), 0), 1e-12);
		assertEquals(1.5, PrayerDrain.secondsPerPoint(PrayerDrain.drainEffect("piety"), 0), 1e-12);
		assertEquals(1, PrayerDrain.secondsPerPoint(36, 0), 1e-12);
		// +30 prayer bonus doubles how long every point lasts.
		assertEquals(2, PrayerDrain.secondsPerPoint(36, 30), 1e-12);
		assertEquals(0, PrayerDrain.drainEffect("zeal"));
		assertEquals(0, PrayerDrain.drainEffect(null));
	}

	@Test public void restoresFollowThePrayerLevel()
	{
		assertEquals(31, PrayerDrain.prayerPotionDose(99));
		assertEquals(32, PrayerDrain.superRestoreDose(99));
		assertEquals(17, PrayerDrain.prayerPotionDose(43));
		assertEquals(7, PrayerDrain.prayerPotionDose(1));
	}

	@Test public void estimateCountsKnownPrayersOnce()
	{
		Loadout loadout = new Loadout();
		GearItem cape = new GearItem();
		cape.setSlot(Slot.CAPE);
		cape.setPrayerBonus(10);
		GearItem amulet = new GearItem();
		amulet.setSlot(Slot.NECK);
		amulet.setPrayerBonus(5);
		loadout.set(Slot.CAPE, cape);
		loadout.set(Slot.NECK, amulet);
		assertEquals(15, PrayerDrain.prayerBonus(loadout));

		PrayerDrain.Estimate estimate = PrayerDrain.estimate(Arrays.asList("piety", "Protect from Melee", "piety", "zeal"),
			PrayerDrain.prayerBonus(loadout), 90);
		assertEquals(Arrays.asList("piety", "Protect from Melee"), estimate.getPrayers());
		assertEquals(36, estimate.getTotalDrain());
		// (2 × 15 + 60) / 36 ticks = 1.5 seconds a point: 60 points over a 90-second kill, 40 a minute.
		assertEquals(1.5, estimate.getSecondsPerPoint(), 1e-12);
		assertEquals(60, estimate.getPointsPerKill(), 1e-12);
		assertEquals(40, estimate.getPointsPerMinute(), 1e-12);
		assertEquals(60 / 31.0, estimate.dosesPerKill(31), 1e-12);
		// Five kills cost 300 points; 99 points at the start leave 201, which is seven 31-point doses.
		assertEquals(7, estimate.dosesFor(5, 31, 99));
		assertEquals(0, estimate.dosesFor(1, 31, 99));
	}

	@Test public void nothingToEstimateWithoutDrainOrAKill()
	{
		assertNull(PrayerDrain.estimate(Collections.emptyList(), 0, 60));
		assertNull(PrayerDrain.estimate(Collections.singletonList("zeal"), 0, 60));
		assertNull(PrayerDrain.estimate(Collections.singletonList("piety"), 0, 0));
		assertNull(PrayerDrain.estimate(Collections.singletonList("piety"), 0, Double.POSITIVE_INFINITY));
	}
}
