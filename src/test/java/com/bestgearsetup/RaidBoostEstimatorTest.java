package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class RaidBoostEstimatorTest
{
	private static final BoostTiming DIVINE = new BoostTiming(true, 50, PlayerLevels.maxed());

	@Test public void longCombatNeedsSeveralDosesAndTwoSeparateBottles()
	{
		SetupResult setup = setup(1, 1);
		List<RaidPlan.InventoryItem> items = estimate(fight(), result(1500, setup, combat()), setup);
		assertEquals(2, items.size());
		assertEquals(5, items.get(0).getEstimatedDoses());
		assertEquals(2, items.get(0).getBottles());
		assertEquals("Each needed bottle gets its own inventory cell", items.get(0).getIds(), items.get(1).getIds());
		List<Integer> ids = items.get(0).getIds();
		assertDoseOrder(ids, ItemID._4DOSEDIVINECOMBAT, ItemID._3DOSEDIVINECOMBAT,
			ItemID._2DOSEDIVINECOMBAT, ItemID._1DOSEDIVINECOMBAT);
		assertDoseOrder(ids, ItemID._4DOSE2COMBAT, ItemID._3DOSE2COMBAT,
			ItemID._2DOSE2COMBAT, ItemID._1DOSE2COMBAT);
		assertTrue(ids.indexOf(ItemID._1DOSEDIVINECOMBAT) < ids.indexOf(ItemID._4DOSE2COMBAT));
	}

	@Test public void samePhysicalPotionForTwoSkillsIsConsumedOnce()
	{
		Potion attack = combat(); attack.setSkill("attack");
		Potion strength = combat(); strength.setSkill("strength");
		SetupResult setup = setup(1, 1);
		List<RaidPlan.InventoryItem> items = estimate(fight(), result(1500, setup, attack, strength), setup);
		assertEquals(2, items.size()); assertEquals(5, items.get(0).getEstimatedDoses());
	}

	@Test public void prepotExcludesOnlyInitialDoseAndExpiresAcrossRooms()
	{
		SetupResult setup = setup(1, 1);
		List<SearchResults> results = Arrays.asList(result(1500, setup, combat()).withPrepotted(), result(100, setup, combat()));
		List<RaidPlan.InventoryItem> items = RaidBoostEstimator.inventory(Arrays.asList(fight().withPrepotted(),
			fight().withEnergy(600, false, 1)), results, Arrays.asList(setup, setup));
		assertEquals(2, items.size()); assertEquals(5, items.get(0).getEstimatedDoses());
		assertEquals(4, estimate(fight(), results.get(0), setup).get(0).getEstimatedDoses());
		// A short later room still covered by the prepot needs no carried dose.
		assertTrue(RaidBoostEstimator.inventory(Arrays.asList(fight(), fight()),
			Arrays.asList(result(100, setup, combat()).withPrepotted(), result(100, setup, combat())),
			Arrays.asList(setup, setup)).isEmpty());
	}

	@Test public void partyAndRepeatedSharesUseSelectedCombatTime()
	{
		SetupResult setup = setup(1, 1);
		List<RaidPlan.InventoryItem> items = estimate(fight().withRepetitions(3).withShare(0.5).withEnergy(0, false, 5),
			result(1500, setup, combat()), setup);
		assertEquals(1, items.size()); assertEquals(2, items.get(0).getEstimatedDoses());
	}

	@Test public void explicitOlmPhaseEntriesKeepDoseCoverageAndOnlyOnePreparationGap() throws Exception
	{
		com.bestgearsetup.data.GameData data = new com.bestgearsetup.data.GameDataLoader(new com.google.gson.Gson()).loadGameData();
		List<RaidEncounter> hands = RaidPlanType.COX_CM.encounters(data).stream()
			.filter(e -> e.getTarget().getName().startsWith("great olm") && e.getTarget().getName().contains("hand"))
			.collect(java.util.stream.Collectors.toList());
		SetupResult setup = setup(1, 1);
		List<SearchResults> results = new ArrayList<>(); List<SetupResult> setups = new ArrayList<>();
		for (RaidEncounter hand : hands) { results.add(result(250, setup, combat())); setups.add(setup); }
		assertEquals(90, hands.stream().mapToDouble(RaidEncounter::getRestBefore).sum(), 0);
		List<RaidPlan.InventoryItem> bottles = RaidBoostEstimator.inventory(hands, results, setups);
		assertEquals(5, bottles.get(0).getEstimatedDoses());
		assertEquals(2, bottles.size());
	}

	@Test public void heartsAreOneReusableItemAndRaidProvidedBoostsOccupyNoSlots()
	{
		SetupResult setup = setup(1, 1);
		Potion heart = potion(ItemID.SATURATED_HEART, "saturated heart");
		List<RaidPlan.InventoryItem> items = estimate(fight(), result(3000, setup, heart), setup);
		assertEquals(1, items.size()); assertTrue(items.get(0).isReusable()); assertEquals(0, items.get(0).getEstimatedDoses());
		Potion overload = potion(20993, "overload"); overload.setAttributes(Collections.singletonList("xerician"));
		Potion salts = potion(27343, "smelling salts"); salts.setAttributes(Collections.singletonList("tombs of amascut"));
		assertTrue(estimate(fight(), result(3000, setup, overload, salts), setup).isEmpty());
		assertTrue(estimate(fight(), result(3000, setup), setup).isEmpty());
	}

	@Test public void fullCandidateCapacityChoosesSlowerFeasibleAlternativeAndKeepsDuplicatesWhenSwitched()
	{
		List<RaidEncounter> encounters = new ArrayList<>();
		List<SearchResults> results = new ArrayList<>();
		SetupResult shared = setup(1000, 0.9);
		for (int room = 0; room < 27; room++)
		{
			encounters.add(fight()); results.add(result(10, setup(1000 + room, 10)));
		}
		SetupResult fast = setup(2000, 1);
		SearchResults last = result(1500, fast, combat());
		last = last.withCandidates(Collections.singletonMap(AttackStyle.Type.CRUSH, Arrays.asList(fast, shared)));
		encounters.add(fight()); results.add(last);
		RaidPlan plan = RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ANCIENT, encounters, results,
			28, Long.MAX_VALUE, id -> false, item -> 0, 0, id -> 0, () -> false);
		assertNotNull(plan);
		assertEquals(1000, plan.getSetups().get(27).getLoadout().getWeapon().getId());
		assertEquals(28, plan.getInventory().size());
		assertEquals(2, plan.inventoryFor(plan.getSetups().get(1)).stream().filter(i -> i.getBottles() > 0).count());
		assertTrue(plan.getWarnings().stream().anyMatch(warning -> warning.contains("not been validated")));
		assertFalse("A live refresh cannot add a new weapon beyond the already full carried inventory",
			plan.supportsSupplies(fast, last, id -> false));
	}

	@Test public void duplicateBottlesCountDuringTwoHandedSpecShieldRemovalAndLiveRefresh()
	{
		SetupResult ordinary = setup(1, 1);
		GearItem shield = new GearItem(); shield.setId(2); shield.setName("Shield"); shield.setSlot(Slot.SHIELD);
		ordinary.getLoadout().set(Slot.SHIELD, shield);
		GearItem weapon = setup(3, 1).getLoadout().getWeapon(); weapon.setTwoHanded(true);
		SpecialPlan special = new SpecialPlan(weapon, null, null, ordinary.getLoadout().getStyle(), null, null,
			null, ordinary.getDps(), ordinary.getDps(), null, 1, 0, 0);
		SetupResult setup = new SetupResult(CombatClass.MELEE, ordinary.getLoadout(), ordinary.getDps(), 0, 0,
			Collections.emptySet(), Collections.emptySet(), special);
		List<RaidPlan.InventoryItem> inventory = new ArrayList<>();
		inventory.add(new RaidPlan.InventoryItem("Spec weapon", Collections.singletonList(3), true));
		for (int slot = 0; slot < 25; slot++)
		{ inventory.add(new RaidPlan.InventoryItem("Supply", Collections.singletonList(100 + slot), false)); }
		inventory.addAll(estimate(fight(), result(1500, setup, combat()), setup));
		assertEquals(28, inventory.size());
		assertEquals(29, RaidPlan.filledMaximumInventory(Collections.singletonList(setup), inventory, new RaidPlan.Memo()));
		RaidPlan plan = new RaidPlan(RaidPlanType.TOB, RaidSpellbook.ANCIENT, Collections.singletonList(fight()),
			Collections.singletonList(result(1500, setup, combat())), Collections.singletonList(setup),
			new ArrayList<>(RaidPlan.gear(setup).values()), inventory, 28, 0);
		assertFalse(plan.supportsSupplies(setup, result(1500, setup, combat()), id -> false));
	}

	@Test public void liveRefreshMayUseSpareDosesWithinExistingFullBottles()
	{
		SetupResult setup = setup(1, 1);
		RaidPlan plan = RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ANCIENT, Collections.singletonList(fight()),
			Collections.singletonList(result(1500, setup, combat())), 28, Long.MAX_VALUE, id -> false, item -> 0,
			0, id -> 0, () -> false);
		assertNotNull(plan); assertEquals(5, plan.getInventory().get(0).getEstimatedDoses());
		assertTrue(plan.supportsSupplies(setup, result(1700, setup, combat()), id -> false)); // six doses, two bottles
		assertTrue(plan.supportsSupplies(setup, result(2400, setup, combat()), id -> false)); // eight doses, two bottles
		assertFalse(plan.supportsSupplies(setup, result(2500, setup, combat()), id -> false)); // nine doses, three bottles
	}

	@Test public void prepotCoverageDoesNotCreateAnUnsafePartialBottleLowerBound()
	{
		List<RaidEncounter> encounters = new ArrayList<>(); List<SearchResults> results = new ArrayList<>();
		for (int room = 0; room < 30; room++)
		{
			SetupResult setup = setup(1000 + room % 29, 10);
			SearchResults result = room < 2 ? result(10, setup, combat()) : result(10, setup);
			if (room == 0) { result = result.withPrepotted(); }
			MonsterSummary target = new MonsterSummary(); target.setName("Boss " + room);
			result.getMonster().setName(target.getName());
			encounters.add(new RaidEncounter(target.getName(), target)); results.add(result);
		}
		RaidPlan plan = RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ANCIENT, encounters, results, 28,
			Long.MAX_VALUE, id -> false, item -> 0, 0, id -> 0, () -> false);
		assertNotNull(plan); assertEquals(28, plan.getInventory().size());
		assertTrue(plan.getInventory().stream().noneMatch(item -> item.getGroup() == RaidPlan.InventoryGroup.BOOSTS));
		assertTrue(plan.supportsSupplies(plan.getSetups().get(1), results.get(1), id -> false));
	}

	@Test public void divineDosesHoldFiveMinutesAndAreWithdrawnAsDivineBottles()
	{
		SetupResult setup = setup(1, 1);
		RaidPlan.InventoryItem item = estimate(fight(), result(1500, setup, combat()), setup).get(0);
		assertEquals(5, item.getEstimatedDoses());
		assertEquals("divine super combat potion", item.getName());
		assertEquals(Integer.valueOf(ItemID._4DOSEDIVINECOMBAT), item.getIds().get(0));
		assertTrue("Ordinary bottles still match in the bank", item.getIds().contains(ItemID._4DOSE2COMBAT));
	}

	@Test public void ordinaryBoostIsRedrunkOnceItDrainsBelowTheThreshold()
	{
		SetupResult setup = setup(1, 1);
		// +19 at 99 drains a level a minute; below half (9) after 10 levels, about 9.5 minutes.
		assertEquals(570, timing(50, 99, 99).doseSeconds(combat()), 0);
		RaidPlan.InventoryItem item = estimate(fight(), result(1500, setup, combat()).withBoostTiming(timing(50, 99, 99)),
			setup).get(0);
		assertEquals(3, item.getEstimatedDoses());
		assertEquals("super combat potion", item.getName());
		assertEquals(Integer.valueOf(ItemID._4DOSEDIVINECOMBAT), item.getIds().get(0));
		// 0% lets the whole boost run out; 100% repots after every level.
		assertEquals(2, doses(1500, timing(0, 99, 99), combat()));
		assertEquals(50, doses(1500, timing(100, 99, 99), combat()));
	}

	@Test public void lowerLevelsDrainSmallerBoostsSooner()
	{
		// +15 at 70: below 7.5 after 8 levels, 7.5 minutes.
		assertEquals(450, timing(50, 70, 70).doseSeconds(combat()), 0);
		assertEquals(4, doses(1500, timing(50, 70, 70), combat()));
	}

	@Test public void combinedPotionIsRedrunkWhenItsFirstSkillDropsBelowTheThreshold()
	{
		Potion attack = combat();
		Potion strength = combat(); strength.setSkill("strength");
		// Strength 80 gets +17 and falls below half after 9 levels (510s), before Attack's 570s.
		assertEquals(2, doses(1100, timing(50, 99, 99), attack, strength));
		assertEquals(3, doses(1100, timing(50, 99, 80), attack, strength));
	}

	@Test public void divineSettingFallsBackToDrainForPotionsWithoutADivineVersion()
	{
		Potion ranging = potion(11722, "super ranging"); ranging.setSkill("ranged");
		ranging.setBaseIncrease(5); ranging.setPercentageIncrease(15);
		BoostTiming divine = new BoostTiming(true, 50, PlayerLevels.maxed());
		assertEquals(570, divine.doseSeconds(ranging), 0);
		assertEquals(BoostTiming.DIVINE_SECONDS, divine.doseSeconds(combat()), 0);
	}

	@Test public void bossTripCoversEveryKillBackToBack()
	{
		SetupResult setup = setup(1, 1);
		SearchResults result = result(200, setup, combat());
		// About 200 seconds a kill on five-minute divine doses.
		RaidPlan.InventoryItem one = RaidBoostEstimator.trip(result, setup, 1).get(0);
		assertEquals(1, one.getEstimatedDoses());
		assertEquals(1, one.getBottles());
		List<RaidPlan.InventoryItem> ten = RaidBoostEstimator.trip(result, setup, 10);
		assertEquals(2, ten.size());
		assertEquals(7, ten.get(0).getEstimatedDoses());
		assertEquals(2, ten.get(0).getBottles());
		assertTrue("No kill, no estimate", RaidBoostEstimator.trip(result(200, setup(1, 0), combat()), setup(1, 0), 10).isEmpty());
	}

	@Test public void setupInventoryListsTripBottlesOnlyForABossTrip()
	{
		SetupResult setup = setup(1, 1);
		List<RaidPlan.InventoryItem> listed = boosts(RaidPlan.inventoryForSetup(setup, result(200, setup, combat()), id -> false));
		assertEquals(1, listed.size());
		assertEquals("Outside a trip a boost is listed once, without quantities", 0, listed.get(0).getBottles());
		List<RaidPlan.InventoryItem> trip = boosts(RaidPlan.inventoryForSetup(setup,
			result(200, setup, combat()).withBoostTiming(new BoostTiming(true, 50, PlayerLevels.maxed(), 10)), id -> false));
		assertEquals(2, trip.size());
		assertEquals(7, trip.get(0).getEstimatedDoses());
	}

	private static List<RaidPlan.InventoryItem> boosts(List<RaidPlan.InventoryItem> inventory)
	{
		List<RaidPlan.InventoryItem> boosts = new ArrayList<>();
		inventory.stream().filter(item -> item.getGroup() == RaidPlan.InventoryGroup.BOOSTS).forEach(boosts::add);
		return boosts;
	}

	private static int doses(int hp, BoostTiming timing, Potion... potions)
	{
		SetupResult setup = setup(1, 1);
		return estimate(fight(), result(hp, setup, potions).withBoostTiming(timing), setup).get(0).getEstimatedDoses();
	}
	private static void assertDoseOrder(List<Integer> ids, int... doses)
	{
		for (int i = 1; i < doses.length; i++) { assertTrue(ids.indexOf(doses[i - 1]) < ids.indexOf(doses[i])); }
	}
	private static BoostTiming timing(int percent, int attack, int strength)
	{
		return new BoostTiming(false, percent, new PlayerLevels(attack, strength, 99, 99, 99, 99, 99, 99));
	}
	private static List<RaidPlan.InventoryItem> estimate(RaidEncounter fight, SearchResults results, SetupResult setup)
	{
		return RaidBoostEstimator.inventory(Collections.singletonList(fight), Collections.singletonList(results), Collections.singletonList(setup));
	}
	private static RaidEncounter fight()
	{
		MonsterSummary summary = new MonsterSummary(); summary.setName("Boss"); return new RaidEncounter("Boss", summary);
	}
	private static Potion combat()
	{
		Potion potion = potion(ItemID._4DOSE2COMBAT, "super combat potion"); potion.setSkill("attack");
		potion.setBaseIncrease(5); potion.setPercentageIncrease(15); return potion;
	}
	private static Potion potion(int id, String name) { Potion potion = new Potion(); potion.setId(id); potion.setName(name); return potion; }
	private static SetupResult setup(int weapon, double dps)
	{
		GearItem item = new GearItem(); item.setId(weapon); item.setName("Weapon " + weapon); item.setSlot(Slot.WEAPON);
		Loadout loadout = new Loadout(); loadout.set(Slot.WEAPON, item); loadout.setStyle(AttackStyle.parse("pound,crush,aggressive"));
		return new SetupResult(CombatClass.MELEE, loadout, new DpsResult(dps, 0, 1, 4, dps * 2.4, null), 0);
	}
	private static SearchResults result(int hp, SetupResult setup, Potion... potions)
	{
		Monster monster = new Monster(); monster.setName("Boss");
		return new SearchResults(monster, hp, Collections.singletonMap(AttackStyle.Type.CRUSH, Collections.singletonList(setup)),
			Collections.emptyList(), Collections.emptyMap(), Collections.singletonMap(CombatClass.MELEE, Arrays.asList(potions)), false).withBoostTiming(DIVINE);
	}
}
