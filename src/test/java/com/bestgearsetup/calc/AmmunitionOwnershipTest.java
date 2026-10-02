package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.ItemCosts;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;
import org.junit.BeforeClass;
import org.junit.Test;

/** Exercise the real bundled ammo and reward IDs through the optimizer. */
public class AmmunitionOwnershipTest
{
	private static GameData data;
	private static GearItem atlatl;
	private static GearItem dart;
	private static CombatContext context;

	@BeforeClass
	public static void load() throws Exception
	{
		data = WikiGameData.get(new Gson()).gameData(new Gson());
		atlatl = data.getItem(Slot.WEAPON, 29000);
		dart = data.getItem(Slot.AMMO, 28991);
		context = new CombatContext(TestData.monster(100, 20), PlayerLevels.maxed(), false, true, null);
	}

	@Test
	public void ownedAtlatlNeedsOwnedDartsAndCannotUseABlessingInstead()
	{
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY)
			.spellbooks(Collections.emptySet()).build();
		assertTrue(new Optimizer(data, context, settings, id -> id == atlatl.getId() || id == 22947, GearItem::getPrice)
			.optimize(CombatClass.RANGED, () -> false).isEmpty());
		SetupResult result = new Optimizer(data, context, settings,
			id -> id == atlatl.getId() || id == dart.getId() || id == 22947, GearItem::getPrice)
			.optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(dart, result.getLoadout().get(Slot.AMMO));
		assertTrue(WeaponRules.firesAmmoSlot(atlatl));
		assertFalse(WeaponRules.loadsAmmo(atlatl));
	}

	@Test
	public void zeroBonusAtlatlDartsArePricedByQuantity()
	{
		GameData small = TestData.gameData(Arrays.asList(atlatl, dart), Collections.emptyList());
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.BUDGET).budget(7000)
			.ammoCount(1000).spellbooks(Collections.emptySet()).build();
		SetupResult result = new Optimizer(small, context, settings, id -> id == atlatl.getId(), item -> 7)
			.optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(7000, result.getBuyCost());
		assertTrue(new Optimizer(small, context, settings.toBuilder().budget(6999).build(),
			id -> id == atlatl.getId(), item -> 7).optimize(CombatClass.RANGED, () -> false).isEmpty());
	}

	/** Owning part of the requested stack prices only the shortfall; it never makes the whole stack free. */
	@Test
	public void ownedAmmunitionCoversOnlyTheStackHeld()
	{
		GameData small = TestData.gameData(Arrays.asList(atlatl, dart), Collections.emptyList());
		IntPredicate owned = id -> id == atlatl.getId() || id == dart.getId();
		IntToLongFunction held = id -> id == atlatl.getId() ? 1 : id == dart.getId() ? 159 : 0;
		OptimizerSettings budget = OptimizerSettings.builder().mode(SearchMode.BUDGET).budget(0)
			.ammoCount(100_000).spellbooks(Collections.emptySet()).build();
		assertTrue("A zero budget can't buy the 99,841 missing darts",
			new Optimizer(small, context, budget, owned, held, item -> 7).optimize(CombatClass.RANGED, () -> false).isEmpty());

		long shortfall = (100_000 - 159) * 7L;
		SetupResult bought = new Optimizer(small, context, budget.toBuilder().budget(shortfall).build(), owned, held,
			item -> 7).optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(shortfall, bought.getBuyCost());
		assertTrue(new Optimizer(small, context, budget.toBuilder().budget(shortfall - 1).build(), owned, held,
			item -> 7).optimize(CombatClass.RANGED, () -> false).isEmpty());

		SetupResult unlimited = new Optimizer(small, context, budget.toBuilder().mode(SearchMode.UNLIMITED).build(),
			owned, held, item -> 7).optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(shortfall, unlimited.getBuyCost());

		OptimizerSettings ownedOnly = budget.toBuilder().mode(SearchMode.OWNED_ONLY).build();
		Optimizer tooFew = new Optimizer(small, context, ownedOnly, owned, held, item -> 7);
		assertTrue(tooFew.optimize(CombatClass.RANGED, () -> false).isEmpty());
		assertTrue(tooFew.unusableReason(dart).contains("159 of the 100000"));
		SetupResult enough = new Optimizer(small, context, ownedOnly.toBuilder().ammoCount(159).build(), owned, held,
			item -> 7).optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(0, enough.getBuyCost());

		// Unpriced darts: the shortfall can't be priced, so it is reported rather than counted as free.
		SetupResult unpriced = new Optimizer(small, context, budget.toBuilder().mode(SearchMode.UNLIMITED).build(),
			owned, held, item -> item == dart ? ItemCosts.UNKNOWN : 7).optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(1, unpriced.getUnpricedItems());
		SetupResult covered = new Optimizer(small, context, budget.toBuilder().mode(SearchMode.UNLIMITED).ammoCount(100)
			.build(), owned, held, item -> item == dart ? ItemCosts.UNKNOWN : 7).optimize(CombatClass.RANGED, () -> false)
			.get(0);
		assertEquals(0, covered.getUnpricedItems());
	}

	/** Knives, darts and chinchompas are used up from the weapon slot, so the same supply rules apply. */
	@Test
	public void thrownWeaponsArePricedAndOwnedByTheRequestedQuantity()
	{
		GearItem knife = data.getItem(Slot.WEAPON, 868);
		assertEquals("rune knife", knife.getName());
		GameData small = TestData.gameData(Collections.singletonList(knife), Collections.emptyList());
		IntPredicate owned = id -> id == knife.getId();
		IntToLongFunction held = id -> id == knife.getId() ? 6 : 0;
		OptimizerSettings budget = OptimizerSettings.builder().mode(SearchMode.BUDGET).budget(0)
			.ammoCount(100_000).spellbooks(Collections.emptySet()).build();
		assertTrue("Six knives can't supply 100,000 throws on a zero budget",
			new Optimizer(small, context, budget, owned, held, item -> 50).optimize(CombatClass.RANGED, () -> false).isEmpty());
		OptimizerSettings ownedOnly = budget.toBuilder().mode(SearchMode.OWNED_ONLY).build();
		assertTrue(new Optimizer(small, context, ownedOnly, owned, held, item -> 50)
			.optimize(CombatClass.RANGED, () -> false).isEmpty());

		long shortfall = (100_000 - 6) * 50L;
		assertEquals(shortfall, new Optimizer(small, context, budget.toBuilder().budget(shortfall).build(), owned, held,
			item -> 50).optimize(CombatClass.RANGED, () -> false).get(0).getBuyCost());
		assertEquals(shortfall, new Optimizer(small, context, budget.toBuilder().mode(SearchMode.UNLIMITED).build(),
			owned, held, item -> 50).optimize(CombatClass.RANGED, () -> false).get(0).getBuyCost());
		assertEquals(0, new Optimizer(small, context, ownedOnly.toBuilder().ammoCount(6).build(), owned, held,
			item -> 50).optimize(CombatClass.RANGED, () -> false).get(0).getBuyCost());
		assertEquals(1, new Optimizer(small, context, budget.toBuilder().mode(SearchMode.UNLIMITED).build(), owned, held,
			item -> ItemCosts.UNKNOWN).optimize(CombatClass.RANGED, () -> false).get(0).getUnpricedItems());
	}

	@Test
	public void onlyWeaponsUsedUpPerAttackCountAsSupplies()
	{
		for (int id : new int[]{868, 811, 805, 11959, 6522, 33716})
		{
			assertTrue(data.getItem(Slot.WEAPON, id).getName(), WeaponRules.consumedPerAttack(data.getItem(Slot.WEAPON, id)));
		}
		// Blowpipes, Tonalztics of Ralos, the Hunter's spear and the atlatl are thrown-style or ranged but kept.
		for (int id : new int[]{12926, 28922, 29305, atlatl.getId(), 21012})
		{
			assertFalse(data.getItem(Slot.WEAPON, id).getName(), WeaponRules.consumedPerAttack(data.getItem(Slot.WEAPON, id)));
		}
		assertTrue(WeaponRules.consumedPerAttack(dart));
		assertFalse(WeaponRules.consumedPerAttack(data.getItem(Slot.AMMO, 22947)));
	}

	@Test
	public void onlyBestInSlotAssumesAnUnownedDiaryTier()
	{
		GearItem elite = data.getItem(Slot.AMMO, 22947);
		for (SearchMode mode : SearchMode.values())
		{
			OptimizerSettings settings = OptimizerSettings.builder().mode(mode).budget(Long.MAX_VALUE)
				.spellbooks(Collections.emptySet()).build();
			// Best in slot has no limit, so every diary tier is assumed.
			assertEquals(mode == SearchMode.UNLIMITED,
				new Optimizer(data, context, settings, id -> id == 22943, GearItem::getPrice).available(elite));
			assertTrue(new Optimizer(data, context, settings, id -> id == 22947, GearItem::getPrice).available(elite));
		}
	}
}
