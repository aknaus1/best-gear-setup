package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.OwnershipRules;
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

public class LockStatusTest
{
	/** Main code gets the client's Gson in startUp; tests use the same instance. */
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
	}

	private final GearItem whip = weapon(4151, "abyssal whip", "whip", 4, "flick,slash,accurate");
	private final GearItem bow = weapon(20997, "twisted bow", "bow", 5, "accurate,ranged,accurate");
	private final GearItem helm = item(10828, "helm of neitiznot", Slot.HEAD);
	private final GearItem shield = item(12954, "dragon defender", Slot.SHIELD);
	private final PlayerLevels levels = new PlayerLevels(60, 60, 60, 80, 60, 60, 60, 1);

	{
		whip.setAttackReq(70);
		bow.setTwoHanded(true);
		helm.setPrice(2_000_000);
	}

	private List<LockStatus> check(SearchMode mode, Map<Slot, SlotLock> locks)
	{
		GameData data = TestData.gameData(Arrays.asList(whip, bow, helm, shield), Collections.emptyList());
		CombatContext ctx = new CombatContext(monster(1, 0), levels, false, false, null);
		OptimizerSettings settings = OptimizerSettings.builder().mode(mode).budget(1_000_000).locks(locks).build();
		return LockStatus.check(data, new Optimizer(data, ctx, settings, id -> false, GearItem::getPrice), locks);
	}

	private static Map<Slot, SlotLock> locks(Slot slot, SlotLock lock)
	{
		Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
		locks.put(slot, lock);
		return locks;
	}

	@Test
	public void weaponLockMissingLevelsExplainsWhyNothingIsFound()
	{
		LockStatus status = check(SearchMode.UNLIMITED, locks(Slot.WEAPON, SlotLock.item(whip.getId()))).get(0);
		assertTrue(status.isBlocking());
		assertEquals("it needs 70 Attack (you have 60)", status.getProblem());
		assertEquals("No setup can be found while this lock is set.", status.getEffect());
		assertEquals(whip, status.getItem());
	}

	@Test
	public void unownedLockInOwnedModeLeavesTheSlotEmpty()
	{
		LockStatus status = check(SearchMode.OWNED_ONLY, locks(Slot.HEAD, SlotLock.item(helm.getId()))).get(0);
		assertEquals("you don't own it and the search uses owned items only", status.getProblem());
		assertEquals("The head slot is left empty.", status.getEffect());
	}

	@Test
	public void lockOverBudgetNamesThePrice()
	{
		LockStatus status = check(SearchMode.BUDGET, locks(Slot.HEAD, SlotLock.item(helm.getId()))).get(0);
		assertEquals("it costs 2m, over your 1m budget", status.getProblem());
	}

	@Test
	public void usableLocksAreNotBlockingAndDescribeTheirEffect()
	{
		assertFalse(check(SearchMode.UNLIMITED, locks(Slot.HEAD, SlotLock.item(helm.getId()))).get(0).isBlocking());
		LockStatus weapon = check(SearchMode.UNLIMITED, locks(Slot.WEAPON, SlotLock.item(bow.getId()))).get(0);
		assertNull(weapon.getProblem());
		assertEquals("Only Twisted Bow is searched.", weapon.getEffect());
		LockStatus ammo = check(SearchMode.UNLIMITED, locks(Slot.AMMO, SlotLock.empty())).get(0);
		assertFalse(ammo.isBlocking());
		assertTrue(ammo.getEffect().contains("ammunition are excluded"));
	}

	@Test
	public void twoHandedWeaponLockConflictsWithAShieldLock()
	{
		Map<Slot, SlotLock> locks = locks(Slot.WEAPON, SlotLock.item(bow.getId()));
		locks.put(Slot.SHIELD, SlotLock.item(shield.getId()));
		List<LockStatus> statuses = check(SearchMode.UNLIMITED, locks);
		LockStatus weapon = statuses.stream().filter(s -> s.getSlot() == Slot.WEAPON).findFirst().get();
		assertEquals("it's two-handed and the shield slot is locked", weapon.getProblem());
		LockStatus held = statuses.stream().filter(s -> s.getSlot() == Slot.SHIELD).findFirst().get();
		assertFalse(held.isBlocking());
		assertEquals("Two-handed weapons are excluded.", held.getEffect());
	}

	@Test
	public void missingItemIsReported()
	{
		LockStatus status = check(SearchMode.UNLIMITED, locks(Slot.BODY, SlotLock.item(999))).get(0);
		assertEquals("this item is not in the bundled data", status.getProblem());
		assertNull(status.getItem());
	}
}
