package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SpecialPlan;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.SupportSpell;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.EnumSet;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class RaidPlannerTest
{
	@Test public void everyRaidTargetExistsAndStartsAtTheRequestedBoss() throws Exception
	{
		GameDataLoader loader = new GameDataLoader(new Gson());
		GameData data = loader.loadGameData();
		assertEquals("tekton (normal)", RaidPlanType.COX_CM.encounters(data).get(0).getTarget().getName());
		// ToA starts on the first path's puzzle: the Apmeken baboons before Ba-Ba.
		assertEquals("baboon brawler (level 56)", RaidPlanType.TOA.encounters(data).get(0).getTarget().getName());
		assertEquals("the maiden of sugadinti (normal)", RaidPlanType.TOB.encounters(data).get(0).getTarget().getName());
		for (RaidPlanType type : RaidPlanType.values())
		{
			for (RaidEncounter encounter : type.encounters(data))
			{
				assertTrue(loader.getMonster(encounter.getTarget()).getHitpoints() > 0);
				assertTrue(!encounter.getTarget().getName().contains("deadman"));
			}
		}
		assertTrue(RaidPlanType.TOA.encounters(data).stream().anyMatch(e -> e.getPhase() == EncounterPhase.AKKHA_IMMUNE_MAGIC));
		assertEquals(4, RaidPlanType.TOA.encounters(data).stream().filter(e -> e.getBossName().equals("Akkha")).count());
		assertTrue(RaidPlanType.COX_CM.encounters(data).stream().anyMatch(e -> e.getTarget().getName().equals("great olm (head)")));
		assertTrue(RaidPlanType.TOB.encounters(data).stream().anyMatch(e -> e.getTarget().getName().equals("verzik vitur (phase 3)")));
	}

	@Test public void olmHandCountsFollowScaleWhileHeadAndOtherTargetsStaySingle() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		for (int party : new int[]{1, 7, 8, 15, 16})
		{
			RaidScaling.Settings scaling = RaidScaling.Settings.builder().partySize(party).coxChallengeMode(true).build();
			for (RaidPlanType type : new RaidPlanType[]{RaidPlanType.COX_CM, RaidPlanType.TOA, RaidPlanType.TOB})
			{
				for (RaidEncounter encounter : type.encounters(data, scaling))
				{
					boolean hand = type == RaidPlanType.COX_CM && encounter.getTarget().getName().startsWith("great olm")
						&& encounter.getTarget().getName().contains("hand");
					assertEquals(hand ? 3 + party / 8 : 1, encounter.getRepetitions());
				}
			}
		}
	}

	@Test public void repeatedFightTimeChangesWhichSwitchThePlannerKeeps()
	{
		GearItem weapon = item(91, Slot.WEAPON);
		GearItem shared = item(92, Slot.BODY);
		SetupResult start = setup(weapon, shared, 10);
		SetupResult early = setup(weapon, item(93, Slot.BODY), 10);
		SetupResult earlyShared = setup(weapon, shared, 8);
		SetupResult olm = setup(weapon, item(94, Slot.BODY), 10);
		SetupResult olmShared = setup(weapon, shared, 9);
		List<SearchResults> results = Arrays.asList(result(start), result(early, earlyShared), result(olm, olmShared));
		for (int repetitions : new int[]{1, 3})
		{
			List<RaidEncounter> encounters = Arrays.asList(new RaidEncounter("Start", new MonsterSummary()),
				new RaidEncounter("Early boss", new MonsterSummary()), new RaidEncounter("Olm hand", new MonsterSummary(), repetitions));
			RaidPlan plan = RaidPlanner.select(RaidPlanType.COX_CM, RaidSpellbook.ARCEUUS, encounters, results, 1,
				Long.MAX_VALUE, id -> false, gear -> 0, 0, id -> 0, () -> false);
			assertNotNull(plan);
			assertEquals(repetitions == 1 ? early : earlyShared, plan.getSetups().get(1));
			assertEquals(repetitions == 1 ? olmShared : olm, plan.getSetups().get(2));
			assertEquals(1, plan.getInventory().size());
			assertEquals(100, results.get(2).getStartingHitpoints());
		}
	}

	@Test public void choosesSlowerSharedGearInsteadOfDroppingItemsToFitTheCap()
	{
		GearItem shared = item(101, Slot.BODY);
		SetupResult start = setup(item(102, Slot.WEAPON), shared, 5);
		SetupResult fastest = setup(item(103, Slot.WEAPON), item(104, Slot.BODY), 10);
		SetupResult compact = setup(item(103, Slot.WEAPON), shared, 8);
		RaidPlan plan = plan(Arrays.asList(result(start), result(fastest, compact)), 1, Long.MAX_VALUE);
		assertNotNull(plan);
		assertEquals(compact, plan.getSetups().get(1));
		assertEquals(1, plan.getInventory().size());
		assertEquals(Integer.valueOf(103), plan.getInventory().get(0).getIds().get(0));
		assertNull(plan(Arrays.asList(result(start), result(fastest)), 1, Long.MAX_VALUE));
	}

	@Test public void raidAmmoStaysWornAndCountsAsEquipmentRatherThanInventory()
	{
		GearItem arrows = item(160, Slot.AMMO); arrows.setRangedStr(40);
		GearItem bow = item(161, Slot.WEAPON); bow.setAmmunition(Collections.singletonList(160));
		SetupResult melee = setup(item(162, Slot.WEAPON), null, 5);
		SetupResult ranged = setup(bow, null, 7); ranged.getLoadout().set(Slot.AMMO, arrows);
		RaidPlan plan = plan(Arrays.asList(result(melee), result(ranged)), 1, Long.MAX_VALUE);
		assertNotNull(plan);
		assertEquals(arrows, plan.getStartingSetup().getLoadout().get(Slot.AMMO));
		assertNull(melee.getLoadout().get(Slot.AMMO)); // published filling does not mutate optimizer candidates
		assertEquals(arrows, plan.getSetups().get(1).getLoadout().get(Slot.AMMO));
		assertEquals(1, plan.getInventory().size());
		assertTrue(plan.getInventory().stream().noneMatch(item -> item.getIds().contains(160)));
		assertEquals(1, RaidPlan.maximumInventory(plan.getSetups(), plan.getResults(), id -> false));
		assertEquals(Integer.valueOf(10), BestGearSetupBankView.raidCellsFor(plan).get(160));
	}

	@Test public void raidAmmoPreservesBlessingsAndExplicitEmptyLocks()
	{
		GearItem arrows = item(170, Slot.AMMO); arrows.setRangedStr(40);
		GearItem blessing = item(171, Slot.AMMO);
		SetupResult melee = setup(item(172, Slot.WEAPON), null, 5); melee.getLoadout().set(Slot.AMMO, blessing);
		SetupResult bow = setup(item(173, Slot.WEAPON), null, 7); bow.getLoadout().set(Slot.AMMO, arrows);
		assertEquals(blessing, RaidPlan.fillAmmo(Arrays.asList(melee, bow), Arrays.asList(result(melee), result(bow))).get(0).getLoadout().get(Slot.AMMO));
		melee.getLoadout().set(Slot.AMMO, null);
		SearchResults empty = new SearchResults(new Monster(), 100, result(melee).getByType(), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), false,
			Collections.singletonList(new LockStatus(Slot.AMMO, SlotLock.empty(), null, null, null)), false, false, Collections.emptyList());
		assertNull(RaidPlan.fillAmmo(Arrays.asList(melee, bow), Arrays.asList(empty, result(bow))).get(0).getLoadout().get(Slot.AMMO));
	}

	@Test public void inventoryBlowpipeShowsLoadedDartsWithoutAnExtraCarriedSlot()
	{
		SetupResult start = setup(item(180, Slot.WEAPON), null, 5);
		GearItem pipe = item(181, Slot.WEAPON); pipe.setName("toxic blowpipe");
		GearItem darts = item(182, Slot.AMMO); darts.setName("amethyst dart"); darts.setRangedStr(28);
		SetupResult ranged = setup(pipe, null, 7); ranged.getLoadout().setLoadedAmmo(darts);
		RaidPlan plan = plan(Arrays.asList(result(start), result(ranged)), 1, Long.MAX_VALUE);
		assertNotNull(plan);
		assertNull(plan.getStartingSetup().getLoadout().get(Slot.AMMO));
		assertNull(plan.getSetups().get(1).getLoadout().get(Slot.AMMO));
		assertEquals(1, plan.getInventory().size());
		assertEquals("amethyst dart", plan.getInventory().get(0).getContents().get(0).getName());
		assertEquals(Integer.valueOf(182), plan.inventoryFor(start).get(0).getContents().get(0).getIds().get(0));
	}

	@Test public void multipleAmmoTypesRemainCorrectForTheirWeapons()
	{
		GearItem arrows = item(190, Slot.AMMO); arrows.setRangedStr(40);
		GearItem bolts = item(191, Slot.AMMO); bolts.setRangedStr(100);
		SetupResult start = setup(item(192, Slot.WEAPON), null, 5);
		SetupResult bow = setup(item(193, Slot.WEAPON), null, 7); bow.getLoadout().set(Slot.AMMO, arrows);
		SetupResult crossbow = setup(item(194, Slot.WEAPON), null, 7); crossbow.getLoadout().set(Slot.AMMO, bolts);
		List<SetupResult> filled = RaidPlan.fillAmmo(Arrays.asList(start, bow, crossbow, crossbow),
			Arrays.asList(result(start), result(bow), result(crossbow), result(crossbow)));
		assertEquals(bolts, filled.get(0).getLoadout().get(Slot.AMMO));
		assertEquals(arrows, filled.get(1).getLoadout().get(Slot.AMMO));
		assertEquals(bolts, filled.get(2).getLoadout().get(Slot.AMMO));
	}

	@Test public void sharedBudgetPricesUniqueItemsAcrossAllBosses()
	{
		GearItem body = item(110, Slot.BODY);
		SetupResult first = setup(item(111, Slot.WEAPON), body, 5);
		SetupResult second = setup(item(112, Slot.WEAPON), body, 7);
		assertNull(plan(Arrays.asList(result(first), result(second)), 28, 25));
		RaidPlan plan = plan(Arrays.asList(result(first), result(second)), 28, 30);
		assertNotNull(plan);
		assertEquals(30, plan.getBuyCost());
	}

	@Test public void inventoryAndBankHaveNoDuplicateStartingItemsAndUseFourColumns()
	{
		SetupResult first = setup(item(121, Slot.WEAPON), item(122, Slot.BODY), 5);
		SetupResult second = setup(item(123, Slot.WEAPON), item(124, Slot.BODY), 7);
		RaidPlan plan = plan(Arrays.asList(result(first), result(second)), 2, Long.MAX_VALUE);
		Map<Integer, Integer> cells = BestGearSetupBankView.raidCellsFor(plan);
		assertEquals(Integer.valueOf(16), cells.get(121));
		assertEquals(Integer.valueOf(17), cells.get(122));
		assertEquals(Integer.valueOf(4), cells.get(123));
		assertEquals(Integer.valueOf(5), cells.get(124));
		assertEquals(55, BestGearSetupBankView.inventoryCell(27));
		assertEquals(4, cells.size());
	}

	@Test public void checksCapacityAfterRemovingWornItemsDuringSwitches()
	{
		SetupResult first = setup(item(131, Slot.WEAPON), item(132, Slot.BODY), 5);
		SetupResult second = setup(item(133, Slot.WEAPON), null, 7);
		assertEquals(1, RaidPlan.inventory(Arrays.asList(first, second), Arrays.asList(result(first), result(second)), id -> false).size());
		assertEquals(2, RaidPlan.maximumInventory(Arrays.asList(first, second), Arrays.asList(result(first), result(second)), id -> false));
	}

	@Test public void cancellationAndMissingEncounterPreventIncompletePublication()
	{
		SetupResult first = setup(item(141, Slot.WEAPON), null, 5);
		assertNull(plan(Arrays.asList(result(first), result()), 28, Long.MAX_VALUE));
		assertNull(RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, Collections.emptyList(), Collections.singletonList(result(first)),
			28, Long.MAX_VALUE, id -> false, item -> 10, 0, id -> 0, () -> true));
	}

	@Test public void runePouchPacksThreeTypesAndDivinePouchPacksFourWithoutExtraSlots()
	{
		SetupResult ordinary = setup(item(145, Slot.WEAPON), null, 5);
		SetupResult spells = new SetupResult(CombatClass.MELEE, ordinary.getLoadout(), ordinary.getDps(), 0, 0,
			Collections.emptySet(), EnumSet.of(SupportSpell.THRALL, SupportSpell.MARK_OF_DARKNESS));
		List<SetupResult> setups = Collections.singletonList(spells);
		List<SearchResults> results = Collections.singletonList(result(spells));
		List<RaidPlan.InventoryItem> regular = RaidPlan.inventory(setups, results, id -> id == ItemID.BH_RUNE_POUCH);
		RaidPlan.InventoryItem pouch = regular.stream().filter(i -> !i.getContents().isEmpty()).findFirst().get();
		assertEquals(3, pouch.getContents().size());
		assertEquals(3, regular.size()); // pouch, overflow rune stack, Book of the dead
		assertEquals(3, RaidPlan.maximumInventory(setups, results, id -> id == ItemID.BH_RUNE_POUCH));
		List<RaidPlan.InventoryItem> divine = RaidPlan.inventory(setups, results,
			id -> id == ItemID.DIVINE_RUNE_POUCH || id == ItemID.BH_RUNE_POUCH);
		RaidPlan.InventoryItem divinePouch = divine.stream().filter(i -> !i.getContents().isEmpty()).findFirst().get();
		assertEquals(4, divinePouch.getContents().size());
		assertEquals(2, divine.size());
		assertTrue(!divinePouch.getIds().contains(ItemID.BH_RUNE_POUCH));
		RaidPlan plan = RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS,
			Collections.singletonList(new RaidEncounter("Boss", new MonsterSummary())), results, 0, Long.MAX_VALUE,
			id -> id == ItemID.DIVINE_RUNE_POUCH, gear -> 10, 0, id -> 0, () -> false);
		assertNotNull(plan);
		assertTrue(plan.supportsSupplies(spells, result(spells), id -> id == ItemID.DIVINE_RUNE_POUCH));
		Map<Integer, Integer> bank = BestGearSetupBankView.raidCellsFor(plan);
		assertEquals(Integer.valueOf(4), bank.get(ItemID.DIVINE_RUNE_POUCH));
		assertTrue(bank.get(ItemID.FIRERUNE) >= 56);
		assertEquals(5, RaidPlan.inventory(setups, results, id -> false).size());
	}

	@Test public void specialSwitchAlsoNeedsRoomForTheUnequippedShield()
	{
		SetupResult ordinary = setup(item(151, Slot.WEAPON), null, 5);
		ordinary.getLoadout().set(Slot.SHIELD, item(152, Slot.SHIELD));
		GearItem specWeapon = item(153, Slot.WEAPON); specWeapon.setTwoHanded(true);
		SpecialPlan special = new SpecialPlan(specWeapon, null, null, ordinary.getLoadout().getStyle(), null, null,
			null, ordinary.getDps(), ordinary.getDps(), null, 1, 0, 0);
		SetupResult setup = new SetupResult(CombatClass.MELEE, ordinary.getLoadout(), ordinary.getDps(), 0, 0,
			Collections.emptySet(), Collections.emptySet(), special);
		assertEquals(1, RaidPlan.inventory(Collections.singletonList(setup), Collections.singletonList(result(setup)), id -> false).size());
		assertEquals(2, RaidPlan.maximumInventory(Collections.singletonList(setup), Collections.singletonList(result(setup)), id -> false));
	}

	private static RaidPlan plan(List<SearchResults> results, int limit, long budget)
	{
		List<RaidEncounter> encounters = new ArrayList<>();
		for (int i = 0; i < results.size(); i++) { encounters.add(new RaidEncounter("Boss " + i, new MonsterSummary())); }
		return RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, encounters, results, limit, budget, id -> false, item -> 10, 0, id -> 0, () -> false);
	}

	private static SearchResults result(SetupResult... setups)
	{
		Monster monster = new Monster();
		monster.setName("test boss");
		return new SearchResults(monster, 100, setups.length == 0 ? Collections.emptyMap()
			: Collections.singletonMap(AttackStyle.Type.CRUSH, Arrays.asList(setups)), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), false);
	}

	private static GearItem item(int id, Slot slot)
	{
		GearItem item = new GearItem();
		item.setId(id); item.setName("item " + id); item.setSlot(slot);
		return item;
	}

	private static SetupResult setup(GearItem weapon, GearItem body, double dps)
	{
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, weapon); loadout.set(Slot.BODY, body);
		loadout.setStyle(AttackStyle.parse("pound,crush,aggressive"));
		return new SetupResult(CombatClass.MELEE, loadout, new DpsResult(dps, 20, 1, 4, dps * 2.4, null), 0);
	}
}
