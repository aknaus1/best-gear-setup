package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.gameData;
import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.ItemCosts;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

public class WildernessRiskTest
{
	/** Main code gets the client's Gson in startUp; tests supply their own. */
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(new Gson());
	}

	private final GearItem sword = weapon(1, "sword", "sword", 4, "slash,slash,aggressive");
	private final GearItem cheapHelm = item(2, "cheap helm", Slot.HEAD);
	private final GearItem expensiveHelm = item(3, "expensive helm", Slot.HEAD);
	private final GearItem ring = item(4, "expensive ring", Slot.RING);
	private final GearItem boots = item(5, "expensive boots", Slot.FEET);

	public WildernessRiskTest()
	{
		sword.setMeleeStr(50);
		sword.setSlashBonus(50);
		sword.setPrice(10);
		cheapHelm.setMeleeStr(6);
		cheapHelm.setPrice(999);
		expensiveHelm.setMeleeStr(20);
		expensiveHelm.setPrice(1_000);
		ring.setMeleeStr(40);
		ring.setPrice(2_000);
		boots.setPrayerBonus(10);
		boots.setPrice(2_000);
	}

	private OptimizerSettings.OptimizerSettingsBuilder settings()
	{
		return OptimizerSettings.builder().mode(SearchMode.UNLIMITED).spellbooks(Collections.emptySet())
			.wildernessRiskLimited(true).maxExpensiveItems(1).expensiveItemThreshold(1_000)
			.resultsPerClass(10);
	}

	private Optimizer optimizer(OptimizerSettings settings, boolean owned)
	{
		CombatContext context = new CombatContext(monster(1, 0), PlayerLevels.maxed(), false,
			99, 99, 99, 99, null, false);
		return new Optimizer(gameData(Arrays.asList(sword, cheapHelm, expensiveHelm, ring, boots),
			Collections.emptyList()), context, settings, id -> owned, GearItem::getPrice);
	}

	private SetupResult best(Optimizer optimizer)
	{
		List<SetupResult> results = optimizer.optimize(CombatClass.MELEE, () -> false);
		assertFalse(results.isEmpty());
		return results.get(0);
	}

	@Test
	public void ownedUpgradesDoNotPruneCheapAlternativesAndPairsReallocateAllowance()
	{
		for (SearchMode mode : SearchMode.values())
		{
			Optimizer optimizer = optimizer(settings().mode(mode).budget(0).build(), true);
			SetupResult result = best(optimizer);
			assertEquals(cheapHelm, result.getLoadout().get(Slot.HEAD));
			assertEquals(ring, result.getLoadout().get(Slot.RING));
			assertEquals(1, optimizer.expensiveItemCount(result.getLoadout()));
			assertEquals(0, result.getBuyCost());
		}
	}

	@Test
	public void zeroLimitAllowsOnlyCheapEquipmentAcrossSearchDepths()
	{
		for (SearchDepth depth : SearchDepth.values())
		{
			Optimizer optimizer = optimizer(settings().maxExpensiveItems(0).depth(depth).build(), false);
			SetupResult result = best(optimizer);
			assertEquals(cheapHelm, result.getLoadout().get(Slot.HEAD));
			assertEquals(0, optimizer.expensiveItemCount(result.getLoadout()));
		}
	}

	@Test
	public void disabledLimitAndLargerAllowanceRetainBothUpgrades()
	{
		for (OptimizerSettings config : Arrays.asList(settings().wildernessRiskLimited(false).build(),
			settings().maxExpensiveItems(2).build()))
		{
			SetupResult result = best(optimizer(config, true));
			assertEquals(expensiveHelm, result.getLoadout().get(Slot.HEAD));
			assertEquals(ring, result.getLoadout().get(Slot.RING));
		}
	}

	@Test
	public void fillAndForcedFillCannotExceedTheLimit()
	{
		for (Map<Slot, SlotLock> locks : Arrays.asList(Collections.<Slot, SlotLock>emptyMap(),
			Collections.singletonMap(Slot.FEET, SlotLock.fill())))
		{
			Optimizer optimizer = optimizer(settings().fillMode(FillMode.PRAYER).locks(locks).build(), true);
			SetupResult result = best(optimizer);
			assertEquals(1, optimizer.expensiveItemCount(result.getLoadout()));
			assertNull(result.getLoadout().get(Slot.FEET));
		}
	}

	@Test
	public void conflictingLocksLeaveNoSetup()
	{
		Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
		locks.put(Slot.HEAD, SlotLock.item(expensiveHelm.getId()));
		locks.put(Slot.RING, SlotLock.item(ring.getId()));
		assertTrue(optimizer(settings().locks(locks).build(), true)
			.optimize(CombatClass.MELEE, () -> false).isEmpty());
	}

	@Test
	public void zeroAllowanceRejectsExpensiveWeaponsEvenWhenOwned()
	{
		sword.setPrice(1_000);
		assertTrue(optimizer(settings().maxExpensiveItems(0).build(), true)
			.optimize(CombatClass.MELEE, () -> false).isEmpty());
	}

	@Test
	public void setBonusSeedsCannotBypassTheCap()
	{
		GearItem head = item(11665, "void melee helm", Slot.HEAD);
		GearItem body = item(8839, "void knight top", Slot.BODY);
		GearItem legs = item(8840, "void knight robe", Slot.LEGS);
		GearItem hands = item(8842, "void knight gloves", Slot.HANDS);
		for (GearItem item : Arrays.asList(head, body, legs, hands))
		{
			item.setPrice(1_000);
		}
		CombatContext context = new CombatContext(monster(1, 0), PlayerLevels.maxed(), false,
			99, 99, 99, 99, null, false);
		Optimizer optimizer = new Optimizer(gameData(Arrays.asList(sword, head, body, legs, hands),
			Collections.emptyList()), context, settings().build(), id -> true, GearItem::getPrice);
		for (SetupResult result : optimizer.optimize(CombatClass.MELEE, () -> false))
		{
			assertTrue(optimizer.expensiveItemCount(result.getLoadout()) <= 1);
		}
		assertFalse(optimizer.optimize(CombatClass.MELEE, () -> false).isEmpty());
	}

	@Test
	public void unknownPricesCountAsExpensiveAndTheThresholdIsInclusive()
	{
		expensiveHelm.setPrice(ItemCosts.UNKNOWN);
		Optimizer optimizer = optimizer(settings().build(), true);
		Loadout loadout = new Loadout();
		loadout.set(Slot.HEAD, expensiveHelm);
		loadout.set(Slot.RING, ring);
		loadout.set(Slot.WEAPON, sword);
		loadout.set(Slot.BODY, cheapHelm);
		assertEquals(2, optimizer.expensiveItemCount(loadout));
		SetupResult result = best(optimizer(settings().maxExpensiveItems(0).build(), true));
		assertEquals(cheapHelm, result.getLoadout().get(Slot.HEAD));
		expensiveHelm.setPrice(1_000);
		assertEquals(2, optimizer(settings().build(), false).expensiveItemCount(loadout));
	}

	@Test
	public void freeBreakableUntradeablesCountTheirRepairFee()
	{
		GearItem fireCape = item(6570, "fire cape", Slot.CAPE);
		fireCape.setMeleeStr(4);
		fireCape.setPrice(0);
		CombatContext context = new CombatContext(monster(1, 0), PlayerLevels.maxed(), false,
			99, 99, 99, 99, null, false);
		OptimizerSettings limit = settings().expensiveItemThreshold(100_000).build();
		Optimizer optimizer = new Optimizer(gameData(Arrays.asList(sword, cheapHelm, expensiveHelm, fireCape),
			Collections.emptyList()), context, limit, id -> true, GearItem::getPrice);
		Loadout loadout = new Loadout();
		loadout.set(Slot.CAPE, fireCape);
		assertEquals("A 150k repair fee reaches the 100k threshold", 1, optimizer.expensiveItemCount(loadout));
		expensiveHelm.setPrice(100_000);
		SetupResult result = best(optimizer);
		assertEquals(1, optimizer.expensiveItemCount(result.getLoadout()));
		assertEquals(expensiveHelm, result.getLoadout().get(Slot.HEAD));
		assertNull("The stronger helm takes the only allowance, so the cape can't join it",
			result.getLoadout().get(Slot.CAPE));
	}

	@Test
	public void budgetAndRiskAreIndependentConstraints()
	{
		Optimizer optimizer = optimizer(settings().mode(SearchMode.BUDGET).budget(1_009).build(), false);
		SetupResult result = best(optimizer);
		assertTrue(result.getBuyCost() <= 1_009);
		assertEquals(cheapHelm, result.getLoadout().get(Slot.HEAD));
		assertEquals(0, optimizer.expensiveItemCount(result.getLoadout()));
	}

	@Test
	public void ammunitionCountsPerEquippedItemAndLoadedAmmoIsExcluded()
	{
		Optimizer optimizer = optimizer(settings().ammoCount(10_000).build(), true);
		Loadout loadout = new Loadout();
		loadout.set(Slot.AMMO, cheapHelm);
		loadout.setLoadedAmmo(ring);
		assertEquals(0, optimizer.expensiveItemCount(loadout));
		loadout.set(Slot.AMMO, ring);
		assertEquals(1, optimizer.expensiveItemCount(loadout));
	}
}
