package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.function.IntPredicate;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ManualCastTest
{
	private final CombatContext ctx = new CombatContext(monster(100, 20), PlayerLevels.maxed(), false, true, piety());
	private GearItem bow;
	private GearItem staff;
	private Spell fireBolt;
	private Spell iceBarrage;

	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
	}

	@Before
	public void setUp()
	{
		bow = weapon(20997, "twisted bow", "bow", 5, "accurate,ranged,accurate");
		bow.setRangedBonus(70);
		bow.setTwoHanded(true);
		staff = weapon(1381, "staff of air", "staff", 5, "bash,crush,accurate", "spell,magic,magic");
		staff.setMagicBonus(10);
		fireBolt = spell("Fire Bolt", "standard", 12, 35, staff.getId());
		iceBarrage = spell("Ice Barrage", "ancient", 30, 94);
	}

	private static Spell spell(String name, String book, int max, int level, int... weapons)
	{
		Spell s = new Spell();
		s.setName(name);
		s.setSpellbook(book);
		s.setMaxHit(max);
		s.setLevel(level);
		String[] ids = new String[weapons.length];
		for (int i = 0; i < weapons.length; i++)
		{
			ids[i] = String.valueOf(weapons[i]);
		}
		s.setSpellWeapons(Arrays.asList(ids));
		return s;
	}

	private List<SetupResult> magic(OptimizerSettings.OptimizerSettingsBuilder settings, IntPredicate owned,
		GearItem... weapons)
	{
		return new Optimizer(TestData.gameData(Arrays.asList(weapons), Arrays.asList(fireBolt, iceBarrage)), ctx,
			settings.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient")))
				.styles(EnumSet.allOf(AttackStyle.Type.class)).build(), owned, GearItem::getPrice)
			.optimize(CombatClass.MAGIC, () -> false);
	}

	private static OptimizerSettings.OptimizerSettingsBuilder settings()
	{
		return OptimizerSettings.builder().mode(SearchMode.UNLIMITED);
	}

	@Test
	public void weaponLockedToABowStillCastsByHand()
	{
		List<SetupResult> results = magic(settings().locks(Collections.singletonMap(Slot.WEAPON,
			SlotLock.item(bow.getId()))), id -> true, bow, staff);
		assertFalse(results.isEmpty());
		Loadout l = results.get(0).getLoadout();
		assertSame(bow, l.getWeapon());
		assertTrue(l.getStyle().isManualCast());
		assertEquals(iceBarrage, l.getSpell());
	}

	@Test
	public void allowedSpellTheStaffCannotAutocastIsCastByHand()
	{
		Loadout l = magic(settings().spellFilter(SpellFilter.of(Collections.emptySet(),
			Collections.singleton("ice barrage"))), id -> true, bow, staff).get(0).getLoadout();
		assertSame(staff, l.getWeapon());
		assertTrue(l.getStyle().isManualCast());
		assertEquals(iceBarrage, l.getSpell());
	}

	@Test
	public void autocastableSpellStaysAutocast()
	{
		Loadout l = magic(settings().spellFilter(SpellFilter.of(Collections.emptySet(),
			Collections.singleton("fire bolt"))), id -> true, bow, staff).get(0).getLoadout();
		assertSame(staff, l.getWeapon());
		assertFalse(l.getStyle().isManualCast());
		assertTrue(l.getStyle().isAutocast());
	}

	@Test
	public void preventingManualCastingKeepsOnlyAutocast()
	{
		assertTrue(magic(settings().preventManualCast(true).locks(Collections.singletonMap(Slot.WEAPON,
			SlotLock.item(bow.getId()))), id -> true, bow, staff).isEmpty());
		Loadout l = magic(settings().preventManualCast(true), id -> true, bow, staff).get(0).getLoadout();
		assertSame(staff, l.getWeapon());
		assertEquals(fireBolt, l.getSpell());
		assertFalse(l.getStyle().isManualCast());
	}

	@Test
	public void noMagicWeaponHeldStillCastsByHand()
	{
		Loadout l = magic(settings().mode(SearchMode.OWNED_ONLY), id -> id == bow.getId(), bow, staff)
			.get(0).getLoadout();
		assertSame(bow, l.getWeapon());
		assertTrue(l.getStyle().isManualCast());
	}
}
