package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Gear;
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
	private final CombatContext ctx = TestFactories.combatContext(monster(100, 20), PlayerLevels.maxed(), false, true, piety());
	private Gear bow;
	private Gear staff;
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

	private List<Setup> magic(OptimizerSettings.OptimizerSettingsBuilder settings, IntPredicate owned,
		Gear... weapons)
	{
		return com.bestgearsetup.calc.Optimizers.of(TestData.gameData(Arrays.asList(weapons), Arrays.asList(fireBolt, iceBarrage)), ctx,
			settings.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient")))
				.styles(EnumSet.allOf(Style.Type.class)).build(), owned, Gear::getPrice)
			.optimize(Combat.MAGIC, () -> false, done -> { });
	}

	private static OptimizerSettings.OptimizerSettingsBuilder settings()
	{
		return OptimizerSettings.builder().mode(SearchMode.UNLIMITED);
	}

	@Test
	public void weaponLockedToABowStillCastsByHand()
	{
		List<Setup> results = magic(settings().locks(Collections.singletonMap(Slot.WEAPON,
			SlotLock.item(bow.getId()))), id -> true, bow, staff);
		assertFalse(results.isEmpty());
		Outfit l = results.get(0).getLoadout();
		assertSame(bow, l.getWeapon());
		assertTrue(l.getStyle().isManualCast());
		assertEquals(iceBarrage, l.getSpell());
	}

	@Test
	public void allowedSpellTheStaffCannotAutocastIsCastByHand()
	{
		Outfit l = magic(settings().spellFilter(SpellFilter.of(Collections.emptySet(),
			Collections.singleton("ice barrage"))), id -> true, bow, staff).get(0).getLoadout();
		assertSame(staff, l.getWeapon());
		assertTrue(l.getStyle().isManualCast());
		assertEquals(iceBarrage, l.getSpell());
	}

	@Test
	public void autocastableSpellStaysAutocast()
	{
		Outfit l = magic(settings().spellFilter(SpellFilter.of(Collections.emptySet(),
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
		Outfit l = magic(settings().preventManualCast(true), id -> true, bow, staff).get(0).getLoadout();
		assertSame(staff, l.getWeapon());
		assertEquals(fireBolt, l.getSpell());
		assertFalse(l.getStyle().isManualCast());
	}

	@Test
	public void staffBoundSpellIsOnlyCastWithItsStaff()
	{
		Gear ibans = weapon(1409, "iban's staff", "staff", 5, "bash,crush,accurate", "spell,magic,magic");
		Spell ibanBlast = spell("Iban Blast", "standard", 25, 50, ibans.getId());
		OptimizerSettings s = settings().spellbooks(new HashSet<>(Arrays.asList("standard", "ancient")))
			.styles(EnumSet.allOf(Style.Type.class)).resultsPerClass(10).build();
		// The staff of air has the higher Magic bonus, but can't cast Iban Blast by hand.
		List<Setup> results = com.bestgearsetup.calc.Optimizers.of(TestData.gameData(Arrays.asList(bow, staff, ibans),
			Arrays.asList(fireBolt, ibanBlast)), ctx, s, id -> true, Gear::getPrice)
			.optimize(Combat.MAGIC, () -> false, done -> { });
		assertFalse(results.isEmpty());
		for (Setup r : results)
		{
			Outfit l = r.getLoadout();
			if (ibanBlast.equals(l.getSpell()))
			{
				assertSame(ibans, l.getWeapon());
				assertFalse(l.getStyle().isManualCast());
			}
		}
		assertSame(ibans, results.get(0).getLoadout().getWeapon());
		// With no staff that casts it, the spell is not offered at all.
		for (Setup r : com.bestgearsetup.calc.Optimizers.of(TestData.gameData(Arrays.asList(bow, staff),
			Arrays.asList(fireBolt, ibanBlast)), ctx, s, id -> true, Gear::getPrice)
			.optimize(Combat.MAGIC, () -> false, done -> { }))
		{
			assertEquals(fireBolt, r.getLoadout().getSpell());
		}
	}

	@Test
	public void plainGodStaffCastsItsGodSpellByHandOnly()
	{
		Gear zamorak = weapon(2417, "zamorak staff", "staff", 5, "bash,crush,accurate", "spell,magic,magic");
		Spell flames = spell("Flames of Zamorak", "standard", 20, 60, zamorak.getId());
		fireBolt = spell("Fire Bolt", "standard", 12, 35, staff.getId(), zamorak.getId());
		OptimizerSettings.OptimizerSettingsBuilder s = settings().locks(Collections.singletonMap(Slot.WEAPON,
			SlotLock.item(zamorak.getId()))).spellbooks(new HashSet<>(Arrays.asList("standard", "ancient")))
			.styles(EnumSet.allOf(Style.Type.class));
		GameData data = TestData.gameData(Arrays.asList(bow, staff, zamorak), Arrays.asList(fireBolt, flames));
		Outfit l = com.bestgearsetup.calc.Optimizers.of(data, ctx, s.build(), id -> true, Gear::getPrice)
			.optimize(Combat.MAGIC, () -> false, done -> { }).get(0).getLoadout();
		assertEquals(flames, l.getSpell());
		assertTrue(l.getStyle().isManualCast());
		// Autocast only: the staff still autocasts ordinary spells, but not its god spell.
		l = com.bestgearsetup.calc.Optimizers.of(data, ctx, s.preventManualCast(true).build(), id -> true, Gear::getPrice)
			.optimize(Combat.MAGIC, () -> false, done -> { }).get(0).getLoadout();
		assertEquals(fireBolt, l.getSpell());
		assertFalse(l.getStyle().isManualCast());
	}

	@Test
	public void harmonisedStaffCastsByHandAtTheSpellsSpeed()
	{
		Gear harmonised = weapon(24423, "harmonised nightmare staff", "staff", 5, "bash,crush,accurate",
			"spell,magic,magic");
		Spell crumble = spell("Crumble Undead", "standard", 15, 39);
		Outfit l = new Outfit();
		l.set(Slot.WEAPON, harmonised);
		l.setSpell(crumble);
		l.setStyle(Style.MANUAL_CAST);
		assertEquals(5, DpsCalculator.calculate(l, ctx).getAttackSpeedTicks());
		l.setStyle(Style.parse("spell,magic,magic"));
		assertEquals(4, DpsCalculator.calculate(l, ctx).getAttackSpeedTicks());
	}

	@Test
	public void noMagicWeaponHeldStillCastsByHand()
	{
		Outfit l = magic(settings().mode(SearchMode.OWNED_ONLY), id -> id == bow.getId(), bow, staff)
			.get(0).getLoadout();
		assertSame(bow, l.getWeapon());
		assertTrue(l.getStyle().isManualCast());
	}
}
