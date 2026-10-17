package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.PrayerDrain;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.OffensivePrayer;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class TripRestoresTest
{
	/** Piety at +0 prayer bonus drains a point every 1.5 seconds, so a 300-second kill costs 200 points. */
	@Test public void tripListsThePrayerPotionsBeyondTheStartingPoints()
	{
		SetupResult setup = setup();
		PrayerDrain.Estimate estimate = result(300, setup, true).prayerDrainFor(setup);
		assertEquals(300, estimate.getSecondsPerKill(), 1e-9);
		assertEquals(200, estimate.getPointsPerKill(), 1e-9);
		// One kill: 200 - 99 starting points = 101, four 31-point doses in one bottle.
		List<RaidPlan.InventoryItem> one = restores(RaidPlan.inventoryForSetup(setup, trip(300, setup, 1), id -> false));
		assertEquals(1, one.size());
		assertEquals(TripRestores.PRAYER_POTION, one.get(0).getName());
		assertEquals(ItemID._4DOSEPRAYERRESTORE, (int) one.get(0).getIds().get(0));
		assertEquals(4, one.get(0).getEstimatedDoses());
		assertEquals(1, one.get(0).getBottles());
		// Every dose of the other restores follows as a bank alternative, super restores before Sanfew serums.
		List<Integer> ids = one.get(0).getIds();
		assertTrue(ids.containsAll(java.util.Arrays.asList(ItemID._1DOSEPRAYERRESTORE, ItemID._1DOSE2RESTORE, ItemID.SANFEW_SALVE_1_DOSE)));
		assertTrue(ids.indexOf(ItemID._1DOSEPRAYERRESTORE) < ids.indexOf(ItemID._4DOSE2RESTORE));
		assertTrue(ids.indexOf(ItemID._1DOSE2RESTORE) < ids.indexOf(ItemID.SANFEW_SALVE_4_DOSE));
		// Two kills: 301 points beyond the start is ten doses, three bottles.
		List<RaidPlan.InventoryItem> two = restores(RaidPlan.inventoryForSetup(setup, trip(300, setup, 2), id -> false));
		assertEquals(3, two.size());
		assertEquals(10, two.get(0).getEstimatedDoses());
		assertEquals(3, two.get(0).getBottles());
	}

	@Test public void ownedSuperRestoresAreListedInstead()
	{
		SetupResult setup = setup();
		List<RaidPlan.InventoryItem> items = restores(RaidPlan.inventoryForSetup(setup, trip(300, setup, 2),
			id -> id == ItemID._2DOSE2RESTORE));
		// 301 points at 32 a dose is still ten doses.
		assertEquals(3, items.size());
		assertEquals(TripRestores.SUPER_RESTORE, items.get(0).getName());
		assertEquals(ItemID._4DOSE2RESTORE, (int) items.get(0).getIds().get(0));
		assertEquals(10, items.get(0).getEstimatedDoses());
		assertTrue(items.get(0).getIds().indexOf(ItemID._1DOSE2RESTORE) < items.get(0).getIds().indexOf(ItemID.SANFEW_SALVE_4_DOSE));
		assertTrue(items.get(0).getIds().indexOf(ItemID.SANFEW_SALVE_1_DOSE) < items.get(0).getIds().indexOf(ItemID._4DOSEPRAYERRESTORE));
		// Owned Sanfew serums lead when no super restore is owned, restoring the same per dose.
		List<RaidPlan.InventoryItem> sanfews = restores(RaidPlan.inventoryForSetup(setup, trip(300, setup, 2),
			id -> id == ItemID.SANFEW_SALVE_3_DOSE));
		assertEquals(TripRestores.SANFEW_SERUM, sanfews.get(0).getName());
		assertEquals(ItemID.SANFEW_SALVE_4_DOSE, (int) sanfews.get(0).getIds().get(0));
		assertTrue(sanfews.get(0).getIds().indexOf(ItemID.SANFEW_SALVE_1_DOSE) < sanfews.get(0).getIds().indexOf(ItemID._4DOSE2RESTORE));
		assertEquals(10, sanfews.get(0).getEstimatedDoses());
	}

	@Test public void nothingListedWithoutATripPrayersOrAShortfall()
	{
		SetupResult setup = setup();
		assertTrue("No trip, no bottles", restores(RaidPlan.inventoryForSetup(setup, result(300, setup, true), id -> false)).isEmpty());
		// A 60-second kill costs 40 points, within the 99 the player arrives with.
		assertTrue(restores(RaidPlan.inventoryForSetup(setup, trip(60, setup, 1), id -> false)).isEmpty());
		SearchResults unprayed = result(300, setup, false).withBoostTiming(new BoostTiming(false, 50, PlayerLevels.maxed(), 5));
		assertTrue(restores(RaidPlan.inventoryForSetup(setup, unprayed, id -> false)).isEmpty());
		assertTrue(TripRestores.trip(trip(300, setup, 5), setup, 0, id -> false).isEmpty());
	}

	private static List<RaidPlan.InventoryItem> restores(List<RaidPlan.InventoryItem> inventory)
	{
		List<RaidPlan.InventoryItem> out = new ArrayList<>();
		inventory.stream().filter(item -> item.getGroup() == RaidPlan.InventoryGroup.BOOSTS).forEach(out::add);
		return out;
	}

	private static SearchResults trip(int hp, SetupResult setup, int kills)
	{
		return result(hp, setup, true).withBoostTiming(new BoostTiming(false, 50, PlayerLevels.maxed(), kills));
	}

	/** Every attack hits for exactly 10 at 1 DPS, so the kill takes HP seconds without overkill. */
	private static SetupResult setup()
	{
		GearItem item = new GearItem(); item.setId(1); item.setName("Weapon"); item.setSlot(Slot.WEAPON);
		Loadout loadout = new Loadout(); loadout.set(Slot.WEAPON, item); loadout.setStyle(AttackStyle.parse("pound,crush,aggressive"));
		return new SetupResult(CombatClass.MELEE, loadout, new DpsResult(1, 10, 1, 4, 10, null), 0);
	}

	private static SearchResults result(int hp, SetupResult setup, boolean piety)
	{
		Monster monster = new Monster(); monster.setName("Boss"); monster.setHitpoints(hp);
		Map<CombatClass, OffensivePrayer> prayers = piety
			? Collections.singletonMap(CombatClass.MELEE, new OffensivePrayer("piety", 20, 23, 70, 70)) : Collections.emptyMap();
		return new SearchResults(monster, hp, Collections.singletonMap(AttackStyle.Type.CRUSH, Collections.singletonList(setup)),
			Collections.emptyList(), prayers, Collections.emptyMap(), false);
	}
}
