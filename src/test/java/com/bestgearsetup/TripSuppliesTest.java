package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SpecialPlan;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.OffensivePrayer;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
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
		// A target whose advice needs no overhead drains only the offensive prayer (24 of 36).
		SearchResults zalcano = results(100, PIETY);
		zalcano.getMonster().setName("zalcano");
		assertEquals(40 / 3.0, TripSupplies.prayerPerKill(setup, zalcano), 1e-9);
		// So does a target without advice: its attacks can't be read, or no prayer helps against them.
		SearchResults unknown = results(100, PIETY);
		unknown.getMonster().setName("no such monster");
		assertEquals(40 / 3.0, TripSupplies.prayerPerKill(setup, unknown), 1e-9);
	}

	@Test
	public void drainEffectsFollowTheWikiPrayerTable()
	{
		// Deadeye drains 12, half of Rigour's 24; with Vorkath's overhead (12) a ranged kill drains 24 or 36.
		SetupResult ranged = new SetupResult(CombatClass.RANGED, new Loadout(), setup(5, 0).getDps(), 0);
		assertEquals(40 / 3.0, TripSupplies.prayerPerKill(ranged, results(100, Collections.singletonMap(CombatClass.RANGED,
			new OffensivePrayer("Deadeye", 18, 18, 62, 1)))), 1e-9);
		assertEquals(20, TripSupplies.prayerPerKill(ranged, results(100, Collections.singletonMap(CombatClass.RANGED,
			new OffensivePrayer("Rigour", 20, 23, 74, 70)))), 1e-9);
		// Below Protect from Magic's level 37 the overhead doesn't drain.
		SearchResults low = results(100, Collections.emptyMap(), new PlayerLevels(99, 99, 99, 99, 99, 36, 99, 99));
		assertEquals(0, TripSupplies.prayerPerKill(ranged, low), 1e-9);
	}

	@Test
	public void sanfewSerumsRestoreTheirOwnAmount()
	{
		// Sanfew: 4 + 99 * 3 / 10 = 33 a dose, so 302 points need 10 doses; at level 40 it's 16, under a super's 18.
		List<TripSupplies.Supply> sanfew = new java.util.ArrayList<>();
		TripSupplies.addRestores(sanfew, 401, 99, id -> id == ItemID.SANFEW_SALVE_4_DOSE);
		assertEquals(TripSupplies.SANFEW, sanfew.get(0).getName());
		assertEquals(3, sanfew.get(0).getCount());
		List<TripSupplies.Supply> low = new java.util.ArrayList<>();
		TripSupplies.addRestores(low, 40 + 17 * 16, 40, id -> id == ItemID.SANFEW_SALVE_4_DOSE);
		assertEquals(5, low.get(0).getCount());
	}

	@Test
	public void aSpecKeepingTheWornWeaponCountsOnlyItsExtraItems()
	{
		GearItem sword = new GearItem();
		sword.setId(1);
		sword.setSlot(Slot.WEAPON);
		GearItem shield = new GearItem();
		shield.setId(2);
		shield.setSlot(Slot.SHIELD);
		Loadout worn = new Loadout();
		worn.set(Slot.WEAPON, sword);
		DpsResult dps = setup(5, 0).getDps();
		// The same weapon with an added off-hand takes one slot; another weapon with it takes two.
		SpecialPlan same = new SpecialPlan(sword, shield, null, null, null, null, null, dps, dps, null, 2, 0, 0);
		SpecialPlan other = new SpecialPlan(new GearItem(), shield, null, null, null, null, null, dps, dps, null, 2, 0, 0);
		assertEquals(1, TripSupplies.usedSlots(new SetupResult(CombatClass.MELEE, worn, dps, 0, 0,
			Collections.emptySet(), Collections.emptySet(), same), Collections.emptyList()));
		assertEquals(2, TripSupplies.usedSlots(new SetupResult(CombatClass.MELEE, worn, dps, 0, 0,
			Collections.emptySet(), Collections.emptySet(), other), Collections.emptyList()));
		// A two-handed spec weapon also takes the worn shield off into the inventory.
		GearItem claws = new GearItem();
		claws.setId(3);
		claws.setSlot(Slot.WEAPON);
		claws.setTwoHanded(true);
		worn.set(Slot.SHIELD, shield);
		SpecialPlan twoHanded = new SpecialPlan(claws, null, null, null, null, null, null, dps, dps, null, 2, 0, 0);
		assertEquals(2, TripSupplies.usedSlots(new SetupResult(CombatClass.MELEE, worn, dps, 0, 0,
			Collections.emptySet(), Collections.emptySet(), twoHanded), Collections.emptyList()));
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
		superCombat.setId(ItemID._4DOSE2COMBAT);
		superCombat.setName("Super combat potion");
		superCombat.setBaseIncrease(5);
		superCombat.setPercentageIncrease(15);
		// +19 at 99: re-sipped once 10 levels have drained, every 9.5 minutes, so an hour is 7 doses in 2 bottles.
		assertEquals(2, TripSupplies.boostBottles(superCombat, 99, 3600));
		assertEquals(1, TripSupplies.boostBottles(superCombat, 99, 60));
		// A wild pie holds two bites (+4 Ranged at 10, a bite every 2.5 minutes): 24 bites in 12 pies.
		Potion pie = new Potion();
		pie.setId(7208);
		pie.setName("wild pie");
		pie.setBaseIncrease(4);
		assertEquals(12, TripSupplies.boostBottles(pie, 10, 3600));
	}

	@Test
	public void nightmareZonePotionsStayInTheirZone()
	{
		Potion nmz = new Potion();
		nmz.setId(11722);
		nmz.setName("super ranging");
		nmz.setSkill("ranged");
		nmz.setBaseIncrease(5);
		nmz.setPercentageIncrease(15);
		nmz.setAttributes(Collections.singletonList("nmz"));
		List<Potion> potions = Collections.singletonList(nmz);
		assertTrue(TripSupplies.plan(setup(5, 0), results(100, PIETY), potions, 1, false, id -> false).isEmpty());
		SearchResults zone = results(100, PIETY);
		zone.getMonster().setName("count draynor (nightmare zone)");
		assertEquals("1 Super Ranging", TripSupplies.summary(TripSupplies.plan(setup(5, 0), zone, potions, 1, false, id -> false)));
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
			Collections.singletonList(superCombat), 10, false, id -> false);
		assertEquals("1 Super Combat Potion, 1 Prayer potion", TripSupplies.summary(supplies));
	}

	@Test
	public void foodFillsTheSlotsTheBottlesLeave()
	{
		// One restore bottle leaves 27 slots: sharks when no listed food is owned.
		List<TripSupplies.Supply> supplies = TripSupplies.plan(setup(5, 0), results(100, PIETY),
			Collections.emptyList(), 10, true, id -> false);
		assertEquals("1 Prayer potion, 27 Shark", TripSupplies.summary(supplies));
		// The best owned food leads, the next owned foods follow as its alternatives.
		supplies = new ArrayList<>();
		TripSupplies.addFood(supplies, 10, id -> id == ItemID.MANTARAY || id == ItemID.LOBSTER || id == ItemID.SHARK);
		assertEquals("Manta ray", supplies.get(0).getName());
		assertEquals(Arrays.asList(ItemID.MANTARAY, ItemID.SHARK, ItemID.LOBSTER), supplies.get(0).getIds());
		assertEquals(10, supplies.get(0).getCount());
	}

	@Test
	public void mixedFoodAddsOwnedKarambwansAndBrewsBesideSuperRestores()
	{
		IntPredicate owns = id -> id == ItemID.ANGLERFISH || id == ItemID.TBWT_COOKED_KARAMBWAN
			|| id == ItemID._4DOSEPOTIONOFSARADOMIN;
		List<TripSupplies.Supply> supplies = new ArrayList<>();
		supplies.add(new TripSupplies.Supply(TripSupplies.SUPER_RESTORE, Collections.singletonList(ItemID._4DOSE2RESTORE), 3));
		TripSupplies.addFood(supplies, 20, owns);
		assertEquals("3 Super restore, 12 Anglerfish, 5 Cooked karambwan, 3 Saradomin brew", TripSupplies.summary(supplies));
		// Without a super restore no brew is packed, and three free slots hold the main food alone.
		List<TripSupplies.Supply> noRestore = new ArrayList<>();
		TripSupplies.addFood(noRestore, 3, owns);
		assertEquals("3 Anglerfish", TripSupplies.summary(noRestore));
	}

	@Test
	public void aTargetThatPoisonsOrEnvenomsAddsACure()
	{
		SearchResults venom = results(100, PIETY);
		venom.getMonster().setPoisons("venom");
		List<TripSupplies.Supply> supplies = TripSupplies.plan(setup(5, 0), venom, Collections.emptyList(), 1, false, id -> false);
		assertEquals("Anti-venom+", supplies.get(supplies.size() - 1).getName());
		assertEquals(ItemID.ANTIVENOM_4, (int) supplies.get(supplies.size() - 1).getIds().get(0));
		SearchResults poison = results(100, PIETY);
		poison.getMonster().setPoisons("poison");
		supplies = TripSupplies.plan(setup(5, 0), poison, Collections.emptyList(), 1, false, id -> false);
		assertEquals("1 Superantipoison", TripSupplies.summary(supplies));
		assertTrue(TripSupplies.plan(setup(5, 0), results(100, PIETY), Collections.emptyList(), 1, false, id -> false).isEmpty());
		// A serpentine helm makes the wearer immune to both.
		SetupResult helmed = setup(5, 0);
		GearItem helm = new GearItem();
		helm.setName("Serpentine helm");
		helm.setSlot(Slot.HEAD);
		helmed.getLoadout().set(Slot.HEAD, helm);
		assertTrue(TripSupplies.plan(helmed, venom, Collections.emptyList(), 1, false, id -> false).isEmpty());
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
		return results(hp, prayers, PlayerLevels.maxed());
	}

	private static SearchResults results(int hp, Map<CombatClass, OffensivePrayer> prayers, PlayerLevels levels)
	{
		Monster monster = new Monster();
		// Vorkath's advice is Protect from Magic for every setup.
		monster.setName("vorkath (post-quest)");
		monster.setHitpoints(hp);
		return new SearchResults(monster, hp, Collections.<AttackStyle.Type, List<SetupResult>>emptyMap(),
			Collections.emptyList(), prayers, Collections.emptyMap(), false, Collections.emptyList(), false, false,
			Collections.emptyList(), null, null, 0, 99, 99, levels);
	}
}
