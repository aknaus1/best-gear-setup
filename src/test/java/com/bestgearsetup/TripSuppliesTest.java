package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.OffensivePrayer;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class TripSuppliesTest
{
	private static final Map<CombatClass, OffensivePrayer> PIETY = Collections.singletonMap(CombatClass.MELEE,
		new OffensivePrayer("Piety", 20, 23, 70, 70));

	@Test
	public void pietyAndAProtectionPrayerDrainAPointASecondWithoutPrayerBonus()
	{
		// Drain 24 + 12 against a resistance of 60: a point every 60 / 36 ticks, one a second.
		SetupResult setup = setup(5, 0);
		assertEquals(20, TripSupplies.prayerPerKill(setup, results(100, PIETY)), 1e-9);
		// +30 prayer bonus doubles the resistance, halving the drain.
		assertEquals(10, TripSupplies.prayerPerKill(setup(5, 30), results(100, PIETY)), 1e-9);
		// Without an offensive prayer only the protection prayer drains (12 of 36).
		assertEquals(20 / 3.0, TripSupplies.prayerPerKill(setup, results(100, Collections.emptyMap())), 1e-9);
	}

	@Test
	public void restoresCoverTheDrainBeyondTheStartingPoints()
	{
		List<TripSupplies.Supply> none = new java.util.ArrayList<>();
		TripSupplies.addRestores(none, 99, 99, id -> false);
		assertTrue("Starting points cover the trip", none.isEmpty());
		List<TripSupplies.Supply> prayer = new java.util.ArrayList<>();
		// 401 points beyond 99 need 302: prayer potions restore 31 a dose, so 10 doses in 3 bottles.
		TripSupplies.addRestores(prayer, 401, 99, id -> false);
		assertEquals("Prayer potion", prayer.get(0).getName());
		assertEquals(3, prayer.get(0).getCount());
		assertEquals(ItemID._4DOSEPRAYERRESTORE, (int) prayer.get(0).getIds().get(0));
		List<TripSupplies.Supply> restores = new java.util.ArrayList<>();
		// Super restores restore 32 a dose: still 10 doses, led by the owned super restore.
		TripSupplies.addRestores(restores, 401, 99, id -> id == ItemID._4DOSE2RESTORE);
		assertEquals(TripSupplies.SUPER_RESTORE, restores.get(0).getName());
		assertEquals(ItemID._4DOSE2RESTORE, (int) restores.get(0).getIds().get(0));
		assertTrue(restores.get(0).getIds().contains(ItemID._4DOSEPRAYERRESTORE));
	}

	@Test
	public void boostBottlesLastTheTrip()
	{
		Potion superCombat = new Potion();
		superCombat.setName("Super combat potion");
		superCombat.setBaseIncrease(5);
		superCombat.setPercentageIncrease(15);
		// +19 at 99: re-sipped once 10 levels have drained, every 9.5 minutes, so an hour is 7 doses in 2 bottles.
		assertEquals(2, TripSupplies.boostBottles(superCombat, 99, 3600));
		assertEquals(1, TripSupplies.boostBottles(superCombat, 99, 60));
		Potion divine = new Potion();
		divine.setName("Divine super combat potion");
		divine.setBaseIncrease(5);
		divine.setPercentageIncrease(15);
		// Five minutes a dose: 12 doses in 3 bottles.
		assertEquals(3, TripSupplies.boostBottles(divine, 99, 3600));
	}

	@Test
	public void planListsBoostsThenRestoresForTheKills()
	{
		Potion superCombat = new Potion();
		superCombat.setId(ItemID._4DOSE2COMBAT);
		superCombat.setName("super combat potion");
		superCombat.setSkill("strength");
		superCombat.setBaseIncrease(5);
		superCombat.setPercentageIncrease(15);
		// 20 seconds a kill at a point a second: 10 kills drain 200, 101 beyond 99, 4 doses of prayer potion.
		List<TripSupplies.Supply> supplies = TripSupplies.plan(setup(5, 0), results(100, PIETY),
			Collections.singletonList(superCombat), 10, id -> false);
		assertEquals("1 Super Combat Potion, 1 Prayer potion", TripSupplies.summary(supplies));
	}

	private static SetupResult setup(double dps, int prayerBonus)
	{
		Loadout loadout = new Loadout();
		GearItem item = new GearItem();
		item.setName("test amulet");
		item.setPrayerBonus(prayerBonus);
		loadout.set(Slot.NECK, item);
		return new SetupResult(CombatClass.MELEE, loadout, new DpsResult(dps, 10, 1, 4, 5, null), 0);
	}

	private static SearchResults results(int hp, Map<CombatClass, OffensivePrayer> prayers)
	{
		Monster monster = new Monster();
		monster.setName("test monster");
		monster.setHitpoints(hp);
		return new SearchResults(monster, hp, Collections.<AttackStyle.Type, List<SetupResult>>emptyMap(),
			Collections.emptyList(), prayers, Collections.emptyMap(), false, Collections.emptyList(), false, false,
			Collections.emptyList(), null, null, 0, 99, 99, PlayerLevels.maxed());
	}
}
