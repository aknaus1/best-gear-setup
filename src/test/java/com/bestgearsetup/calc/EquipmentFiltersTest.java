package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.EquipmentRequirements;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

public class EquipmentFiltersTest
{
	/** Main code gets the client's Gson in startUp; tests use the same instance. */
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
	}

	private final GearItem sword = weapon(4151, "sword", "slash sword", 4, "slash,slash,aggressive");

	public EquipmentFiltersTest()
	{
		sword.setSlashBonus(80);
		sword.setMeleeStr(80);
	}

	private List<SetupResult> run(OptimizerSettings.OptimizerSettingsBuilder builder, GearItem... gear)
	{
		GameData data = TestData.gameData(Arrays.asList(gear), Collections.emptyList());
		CombatContext ctx = new CombatContext(monster(100, 20), PlayerLevels.maxed(), false, true, piety());
		OptimizerSettings settings = builder.mode(SearchMode.UNLIMITED)
			.spellbooks(Collections.singleton("standard")).resultsPerClass(1).build();
		// Even owned equipment must obey category filters.
		return new Optimizer(data, ctx, settings, id -> true, GearItem::getPrice)
			.optimize(CombatClass.MELEE, () -> false);
	}

	@Test
	public void bountyHunterIsExcludedByDefaultAndCanBeEnabled()
	{
		GearItem bh = weapon(24617, "vesta's blighted longsword", "slash sword", 4, "slash,slash,aggressive");
		bh.setSlashBonus(150);
		bh.setMeleeStr(150);
		assertEquals(sword, run(OptimizerSettings.builder(), sword, bh).get(0).getLoadout().getWeapon());
		assertEquals(bh, run(OptimizerSettings.builder().bountyHunterItems(true), sword, bh)
			.get(0).getLoadout().getWeapon());
	}

	@Test
	public void dmmBetaAndMembersFiltersAreIndependent()
	{
		GearItem dmm = item(22616, "vesta's chainbody (dmm)", Slot.BODY);
		GearItem beta = item(99901, "test helm (beta)", Slot.HEAD);
		GearItem members = item(6737, "berserker ring", Slot.RING);
		dmm.setMeleeStr(10);
		beta.setMeleeStr(10);
		members.setMeleeStr(10);
		members.setMembers(true);
		SetupResult defaults = run(OptimizerSettings.builder(), sword, dmm, beta, members).get(0);
		assertNull(defaults.getLoadout().get(Slot.BODY));
		assertNull(defaults.getLoadout().get(Slot.HEAD));
		assertEquals(members, defaults.getLoadout().get(Slot.RING));
		SetupResult enabled = run(OptimizerSettings.builder().dmmItems(true).betaItems(true).membersItems(false),
			sword, dmm, beta, members).get(0);
		assertEquals(dmm, enabled.getLoadout().get(Slot.BODY));
		assertEquals(beta, enabled.getLoadout().get(Slot.HEAD));
		assertNull(enabled.getLoadout().get(Slot.RING));
	}

	@Test
	public void disabledCategoryCannotBypassFiltersThroughWeaponLock()
	{
		GearItem bh = weapon(24617, "vesta's blighted longsword", "slash sword", 4, "slash,slash,aggressive");
		Map<Slot, SlotLock> locks = Collections.singletonMap(Slot.WEAPON, SlotLock.item(bh.getId()));
		assertTrue(run(OptimizerSettings.builder().locks(locks), sword, bh).isEmpty());
		assertEquals(bh, run(OptimizerSettings.builder().bountyHunterItems(true).locks(locks), sword, bh)
			.get(0).getLoadout().getWeapon());
	}

	@Test
	public void disabledCategoryCannotBypassFiltersThroughArmourLock()
	{
		GearItem dmm = item(22616, "vesta's chainbody (dmm)", Slot.BODY);
		dmm.setMeleeStr(10);
		Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
		locks.put(Slot.BODY, SlotLock.item(dmm.getId()));
		assertNull(run(OptimizerSettings.builder().locks(locks), sword, dmm).get(0).getLoadout().get(Slot.BODY));
		assertEquals(dmm, run(OptimizerSettings.builder().dmmItems(true).locks(locks), sword, dmm)
			.get(0).getLoadout().get(Slot.BODY));
	}

	@Test
	public void ownedAndLockedBlackMaskCannotBypassBaseCombatLevel()
	{
		GearItem mask = item(8921, "black mask", Slot.HEAD);
		mask.setMeleeStr(50);
		// As in the bundled snapshot: 10 Defence, 20 Strength and 40 combat.
		EquipmentRequirements.get().applyReviewed(mask);
		GameData data = TestData.gameData(Arrays.asList(sword, mask), Collections.emptyList());
		PlayerLevels levels = new PlayerLevels(1, 20, 10, 1, 1, 1, 10, 1);
		// Large boosts may change damage, but must not grant permission to equip the mask.
		CombatContext ctx = new CombatContext(monster(1, 0), levels, false, 99, 99, 99, 99, piety(), false);
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED)
			.build();
		List<SetupResult> results = new Optimizer(data, ctx, settings, id -> true, GearItem::getPrice)
			.optimize(CombatClass.MELEE, () -> false);
		assertFalse(results.isEmpty());
		assertNull(results.get(0).getLoadout().get(Slot.HEAD));
		OptimizerSettings locked = settings.toBuilder()
			.locks(Collections.singletonMap(Slot.HEAD, SlotLock.item(mask.getId()))).build();
		List<SetupResult> lockedResults = new Optimizer(data, ctx, locked, id -> true, GearItem::getPrice)
			.optimize(CombatClass.MELEE, () -> false);
		assertFalse(lockedResults.isEmpty());
		assertNull(lockedResults.get(0).getLoadout().get(Slot.HEAD));
	}

	@Test
	public void weaponLockCannotBypassWearRulesOrTheBudget()
	{
		sword.setAttackReq(70);
		sword.setPrice(1000);
		GameData data = TestData.gameData(Collections.singletonList(sword), Collections.emptyList());
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY)
			.locks(Collections.singletonMap(Slot.WEAPON, SlotLock.item(sword.getId()))).build();
		PlayerLevels low = new PlayerLevels(1, 99, 99, 99, 99, 99, 99, 99);
		CombatContext lowContext = new CombatContext(monster(100, 20), low, false, true, piety());
		CombatContext maxed = new CombatContext(monster(100, 20), PlayerLevels.maxed(), false, true, piety());
		assertTrue(new Optimizer(data, lowContext, settings, id -> true, GearItem::getPrice)
			.optimize(CombatClass.MELEE, () -> false).isEmpty());
		// Owned items only and Inventory + equipped only wear an unowned lock, priced as a purchase.
		for (SearchMode mode : new SearchMode[]{SearchMode.OWNED_ONLY, SearchMode.INVENTORY_ONLY})
		{
			SetupResult worn = new Optimizer(data, maxed, settings.toBuilder().mode(mode).build(), id -> false,
				GearItem::getPrice).optimize(CombatClass.MELEE, () -> false).get(0);
			assertEquals(sword, worn.getLoadout().getWeapon());
			assertEquals(1000, worn.getBuyCost());
		}
		SetupResult owned = new Optimizer(data, maxed, settings, id -> true, GearItem::getPrice)
			.optimize(CombatClass.MELEE, () -> false).get(0);
		assertEquals(sword, owned.getLoadout().getWeapon());
		assertEquals(0, owned.getBuyCost());
		// A budget still has to cover the lock.
		assertTrue(new Optimizer(data, maxed, settings.toBuilder().mode(SearchMode.BUDGET).budget(999).build(),
			id -> false, GearItem::getPrice).optimize(CombatClass.MELEE, () -> false).isEmpty());
		assertEquals(1000, new Optimizer(data, maxed, settings.toBuilder().mode(SearchMode.BUDGET).budget(1000).build(),
			id -> false, GearItem::getPrice).optimize(CombatClass.MELEE, () -> false).get(0).getBuyCost());
		sword.setTradeable(false);
		assertTrue(new Optimizer(data, maxed, settings.toBuilder().mode(SearchMode.BUDGET).budget(Long.MAX_VALUE).build(),
			id -> false, GearItem::getPrice).optimize(CombatClass.MELEE, () -> false).isEmpty());
		// Owned items only still wears an unowned untradeable lock; it just can't be bought.
		assertEquals(sword, new Optimizer(data, maxed, settings, id -> false, GearItem::getPrice)
			.optimize(CombatClass.MELEE, () -> false).get(0).getLoadout().getWeapon());
		// Best in slot has no limit: an unowned untradeable is still used.
		assertEquals(sword, new Optimizer(data, maxed, settings.toBuilder().mode(SearchMode.UNLIMITED).build(),
			id -> false, GearItem::getPrice).optimize(CombatClass.MELEE, () -> false).get(0).getLoadout().getWeapon());
	}

	@Test
	public void ammunitionLockCannotBypassWearRulesOrTheBudget()
	{
		GearItem bow = weapon(600, "bow", "bow", 5, "rapid,ranged,rapid");
		bow.setRangedBonus(60);
		bow.setAmmunition(Collections.singletonList(700));
		GearItem arrow = item(700, "arrow", Slot.AMMO);
		arrow.setRangedStr(60);
		arrow.setRangedReq(90);
		arrow.setPrice(1000);
		GameData data = TestData.gameData(Arrays.asList(bow, arrow), Collections.emptyList());
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY)
			.locks(Collections.singletonMap(Slot.AMMO, SlotLock.item(arrow.getId()))).build();
		PlayerLevels low = new PlayerLevels(99, 99, 99, 1, 99, 99, 99, 99);
		CombatContext lowContext = new CombatContext(monster(100, 20), low, false, true, piety());
		CombatContext maxed = new CombatContext(monster(100, 20), PlayerLevels.maxed(), false, true, piety());
		assertTrue(new Optimizer(data, lowContext, settings, id -> true, GearItem::getPrice)
			.optimize(CombatClass.RANGED, () -> false).isEmpty());
		// Owned items only and Inventory + equipped only use unowned locked ammunition, priced as a purchase.
		for (SearchMode mode : new SearchMode[]{SearchMode.OWNED_ONLY, SearchMode.INVENTORY_ONLY})
		{
			SetupResult used = new Optimizer(data, maxed, settings.toBuilder().mode(mode).build(), id -> id == bow.getId(),
				GearItem::getPrice).optimize(CombatClass.RANGED, () -> false).get(0);
			assertEquals(arrow, used.getLoadout().get(Slot.AMMO));
		}
		// Counting ammunition: 100 locked arrows, none held, are priced as 100 to buy.
		SetupResult counted = new Optimizer(data, maxed, settings.toBuilder().ammoCount(100).build(), id -> id == bow.getId(),
			GearItem::getPrice).optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(arrow, counted.getLoadout().get(Slot.AMMO));
		assertEquals(100 * 1000, counted.getBuyCost());
		assertEquals(arrow, new Optimizer(data, maxed, settings, id -> true, GearItem::getPrice)
			.optimize(CombatClass.RANGED, () -> false).get(0).getLoadout().get(Slot.AMMO));
		assertTrue(new Optimizer(data, maxed, settings.toBuilder().mode(SearchMode.BUDGET).budget(999).build(),
			id -> id == bow.getId(), GearItem::getPrice).optimize(CombatClass.RANGED, () -> false).isEmpty());
		arrow.setTradeable(false);
		assertTrue(new Optimizer(data, maxed, settings.toBuilder().mode(SearchMode.BUDGET).budget(Long.MAX_VALUE).build(),
			id -> id == bow.getId(), GearItem::getPrice).optimize(CombatClass.RANGED, () -> false).isEmpty());
		assertEquals(arrow, new Optimizer(data, maxed, settings.toBuilder().mode(SearchMode.UNLIMITED).build(),
			id -> id == bow.getId(), GearItem::getPrice).optimize(CombatClass.RANGED, () -> false).get(0)
			.getLoadout().get(Slot.AMMO));
	}
}
