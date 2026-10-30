package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.util.Collections;
import java.util.EnumSet;
import org.junit.Test;

/** Thralls, Charge and Mark of Darkness honour rune rules, and the thrall tier falls back to what is allowed. */
public class SupportSpellRunesTest
{
	private static final CombatModifiers ALL = CombatModifiers.builder().markOfDarkness(true).charge(true).build();

	private static RuneFilter blocked(String csv)
	{
		return RuneFilter.parse(csv, "");
	}

	private static Monster monster(boolean demon)
	{
		Monster m = TestData.monster(100, 20, demon ? new String[]{"demon"} : new String[0]);
		m.setHitpoints(100);
		return m;
	}

	private static CombatContext context(Monster target, boolean thralls, int magic, RuneFilter filter)
	{
		PlayerLevels levels = new PlayerLevels(99, 99, 99, 99, magic, 99, 99, 99);
		return new CombatContext(target, levels, false, 99, 99, 99, magic, null, thralls).withModifiers(ALL)
			.withRuneFilter(filter);
	}

	private static Loadout loadout(String spell, String spellbook, String weapon, String cape)
	{
		Loadout l = new Loadout();
		if (spell != null)
		{
			Spell s = new Spell();
			s.setName(spell);
			s.setSpellbook(spellbook);
			l.setSpell(s);
		}
		if (weapon != null)
		{
			l.set(Slot.WEAPON, TestData.item(1, weapon, Slot.WEAPON));
		}
		if (cape != null)
		{
			l.set(Slot.CAPE, TestData.item(3, cape, Slot.CAPE));
		}
		return l;
	}

	private static Thrall tier(int magic, String blockedCsv)
	{
		return SupportSpell.thrallFor(new Loadout(), context(monster(false), true, magic, blocked(blockedCsv)));
	}

	@Test
	public void thrallTierFallsBackWhenItsRunesAreBlocked()
	{
		assertEquals(Thrall.GREATER, tier(99, ""));
		assertEquals(Thrall.SUPERIOR, tier(99, "blood"));
		assertEquals(Thrall.LESSER, tier(99, "blood,death"));
		assertEquals(Thrall.SUPERIOR, tier(99, "fire"));
		assertEquals(Thrall.LESSER, tier(99, "fire,earth"));
		assertNull(tier(99, "cosmic"));
		assertNull(tier(99, "blood,death,air"));
		assertNull(tier(99, "blood,death,mind"));
	}

	@Test
	public void magicLevelCapsTheTier()
	{
		assertEquals(Thrall.SUPERIOR, tier(60, ""));
		assertEquals(Thrall.LESSER, tier(60, "death"));
		assertEquals(Thrall.SUPERIOR, tier(60, "blood"));
		assertEquals(Thrall.LESSER, tier(38, ""));
		assertNull(tier(38, "air"));
		assertNull(tier(37, ""));
	}

	@Test
	public void thrallIsExactlyWhetherThrallForFindsATier()
	{
		for (String blocked : new String[]{"", "blood", "blood,death", "cosmic", "air,mind,earth"})
		{
			for (int magic : new int[]{37, 38, 60, 99})
			{
				CombatContext ctx = context(monster(false), true, magic, blocked(blocked));
				Loadout l = new Loadout();
				assertEquals(blocked + "/" + magic, SupportSpell.thrallFor(l, ctx) != null, SupportSpell.thrall(l, ctx));
			}
		}
		Loadout l = new Loadout();
		assertNull(SupportSpell.thrallFor(l, context(monster(false), false, 99, RuneFilter.NONE)));
		assertFalse(SupportSpell.thrall(l, context(monster(false), false, 99, RuneFilter.NONE)));
	}

	@Test
	public void cosmicBlockedDropsThrallAndMarkOfDarkness()
	{
		RuneFilter noCosmic = blocked("cosmic");
		Loadout mark = loadout("inferior demonbane", "arceuus", "zamorak staff", null);
		CombatContext ctx = context(monster(true), true, 99, noCosmic);
		assertFalse(SupportSpell.thrall(new Loadout(), ctx));
		assertFalse(SupportSpell.markOfDarkness(mark, ctx));
		assertEquals(EnumSet.noneOf(SupportSpell.class), SupportSpell.assumed(mark, ctx));
		assertEquals(EnumSet.of(SupportSpell.THRALL, SupportSpell.MARK_OF_DARKNESS),
			SupportSpell.assumed(mark, context(monster(true), true, 99, RuneFilter.NONE)));
	}

	@Test
	public void suppliedRunesStillCountForTheThrall()
	{
		Loadout fireStaff = loadout(null, null, "staff of fire", null);
		CombatContext noFire = context(monster(false), true, 99, blocked("fire"));
		assertEquals(Thrall.GREATER, SupportSpell.thrallFor(fireStaff, noFire));
		assertEquals(Thrall.SUPERIOR, SupportSpell.thrallFor(new Loadout(), noFire));
		assertEquals(Thrall.SUPERIOR, SupportSpell.thrallFor(loadout(null, null, "staff of air", null), noFire));
		CombatContext noFireNoBlood = context(monster(false), true, 99, blocked("fire,blood"));
		assertEquals(Thrall.SUPERIOR, SupportSpell.thrallFor(fireStaff, noFireNoBlood));
	}

	@Test
	public void onlyListRestrictsThrallTiers()
	{
		assertEquals(Thrall.LESSER, SupportSpell.thrallFor(new Loadout(),
			context(monster(false), true, 99, RuneFilter.parse("", "air,mind,cosmic"))));
		assertEquals(Thrall.GREATER, SupportSpell.thrallFor(new Loadout(),
			context(monster(false), true, 99, RuneFilter.parse("", "fire,blood,cosmic"))));
		assertNull(SupportSpell.thrallFor(new Loadout(),
			context(monster(false), true, 99, RuneFilter.parse("", "fire,blood"))));
	}

	@Test
	public void thrallStillNeedsNoNonArceuusAutocast()
	{
		CombatContext ctx = context(monster(false), true, 99, RuneFilter.NONE);
		assertNull(SupportSpell.thrallFor(loadout("fire surge", "standard", "staff of fire", null), ctx));
		assertEquals(Thrall.GREATER, SupportSpell.thrallFor(loadout("undead grasp", "arceuus", null, null), ctx));
		Monster immune = monster(false);
		immune.setImmuneThrall(true);
		assertNull(SupportSpell.thrallFor(new Loadout(), context(immune, true, 99, RuneFilter.NONE)));
	}

	@Test
	public void dpsCarriesTheFallbackTier()
	{
		GearItem sword = TestData.weapon(100, "sword", "slash sword", 4, "chop,slash,aggressive");
		sword.setSlashBonus(80);
		sword.setMeleeStr(80);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, sword);
		l.setStyle(AttackStyle.parse("chop,slash,aggressive"));
		Monster target = monster(false);
		double without = DpsCalculator.calculate(l, context(target, false, 99, RuneFilter.NONE)).getDps();
		double greater = DpsCalculator.calculate(l, context(target, true, 99, RuneFilter.NONE)).getDps();
		double superior = DpsCalculator.calculate(l, context(target, true, 99, blocked("blood"))).getDps();
		double lesser = DpsCalculator.calculate(l, context(target, true, 99, blocked("blood,death"))).getDps();
		double none = DpsCalculator.calculate(l, context(target, true, 99, blocked("cosmic"))).getDps();
		assertTrue(without > 0);
		assertEquals(Thrall.GREATER.getDps(), greater - without, 1e-9);
		assertEquals(Thrall.SUPERIOR.getDps(), superior - without, 1e-9);
		assertEquals(Thrall.LESSER.getDps(), lesser - without, 1e-9);
		assertEquals(without, none, 1e-9);
	}

	@Test
	public void markOfDarknessNeedsItsRunesAndOnlyThatBlocksIt()
	{
		Loadout l = loadout("inferior demonbane", "arceuus", "zamorak staff", null);
		Monster demon = monster(true);
		assertTrue(SupportSpell.markOfDarkness(l, context(demon, false, 99, RuneFilter.NONE)));
		assertEquals(EnumSet.of(SupportSpell.MARK_OF_DARKNESS),
			SupportSpell.assumed(l, context(demon, false, 99, RuneFilter.NONE)));
		assertFalse(SupportSpell.markOfDarkness(l, context(demon, false, 99, blocked("cosmic"))));
		assertFalse(SupportSpell.markOfDarkness(l, context(demon, false, 99, blocked("soul"))));
		assertFalse(SupportSpell.markOfDarkness(l, context(demon, false, 99, RuneFilter.parse("", "fire,chaos"))));
		assertTrue(SupportSpell.markOfDarkness(l, context(demon, false, 99, RuneFilter.parse("", "fire,chaos,cosmic,soul"))));
		// Blocking a rune Mark does not use leaves it alone.
		assertTrue(SupportSpell.markOfDarkness(l, context(demon, false, 99, blocked("blood"))));
	}

	@Test
	public void markOfDarknessAccuracyBonusFollowsTheSameCondition()
	{
		GearItem staff = TestData.weapon(7, "zamorak staff", "staff", 5, "spell,magic,magic");
		staff.setMagicBonus(100);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, staff);
		l.setStyle(AttackStyle.parse("spell,magic,magic"));
		Spell spell = new Spell();
		spell.setName("inferior demonbane");
		spell.setSpellbook("arceuus");
		spell.setMaxHit(30);
		l.setSpell(spell);
		Monster demon = monster(true);
		CombatContext marked = context(demon, false, 99, RuneFilter.NONE);
		CombatContext blockedMark = context(demon, false, 99, blocked("cosmic"));
		CombatContext unmarked = marked.withModifiers(CombatModifiers.NONE);
		DpsResult withMark = DpsCalculator.calculate(l, marked);
		DpsResult withoutMark = DpsCalculator.calculate(l, unmarked);
		assertTrue(withMark.getAccuracy() > withoutMark.getAccuracy());
		DpsResult blockedResult = DpsCalculator.calculate(l, blockedMark);
		assertEquals(withoutMark.getAccuracy(), blockedResult.getAccuracy(), 1e-12);
		assertEquals(withoutMark.getMaxHit(), blockedResult.getMaxHit());
		assertEquals(withoutMark.getDps(), blockedResult.getDps(), 1e-9);
	}

	private static Loadout zamorak(String weapon)
	{
		return loadout("flames of zamorak", "standard", weapon, "zamorak cape");
	}

	@Test
	public void chargeIsAssumedWithoutRulesAndNeedsItsRunes()
	{
		Monster target = monster(false);
		Loadout l = zamorak("zamorak staff");
		assertEquals(EnumSet.of(SupportSpell.CHARGE), SupportSpell.assumed(l, context(target, false, 99, RuneFilter.NONE)));
		assertTrue(SupportSpell.charge(l, context(target, false, 99, RuneFilter.NONE)));
		assertFalse(SupportSpell.charge(l, context(target, false, 99, blocked("air"))));
		assertFalse(SupportSpell.charge(l, context(target, false, 99, blocked("blood"))));
		assertFalse(SupportSpell.charge(l, context(target, false, 99, blocked("fire"))));
		assertEquals(EnumSet.noneOf(SupportSpell.class),
			SupportSpell.assumed(l, context(target, false, 99, blocked("blood"))));
	}

	@Test
	public void chargeUsesSuppliedRunes()
	{
		Monster target = monster(false);
		assertTrue(SupportSpell.charge(zamorak("staff of air"), context(target, false, 99, blocked("air"))));
		assertFalse(SupportSpell.charge(zamorak("staff of fire"), context(target, false, 99, blocked("air"))));
		assertTrue(SupportSpell.charge(zamorak("staff of fire"), context(target, false, 99, blocked("fire"))));
		assertFalse(SupportSpell.charge(zamorak("staff of air"), context(target, false, 99, blocked("blood"))));
		assertFalse(SupportSpell.charge(zamorak("staff of air"), context(target, false, 99, RuneFilter.parse("", "air"))));
	}

	@Test
	public void contextDefaultsToNoRuleAndCopiesPreserveTheFilter()
	{
		Monster target = monster(false);
		PlayerLevels levels = PlayerLevels.maxed();
		CombatContext plain = new CombatContext(target, levels, false, false, null);
		assertSame(RuneFilter.NONE, plain.getRuneFilter());
		assertSame(RuneFilter.NONE, new CombatContext(target, levels, false, 99, 99, 99, 99, null, true).getRuneFilter());
		RuneFilter filter = blocked("blood");
		CombatContext ruled = plain.withRuneFilter(filter);
		assertEquals(filter, ruled.getRuneFilter());
		assertSame(RuneFilter.NONE, plain.getRuneFilter());
		assertEquals(filter, ruled.withModifiers(ALL).getRuneFilter());
		assertEquals(filter, ruled.withFightOptions(3, 1).getRuneFilter());
		assertEquals(filter, ruled.withHealthStates(hp -> target).getRuneFilter());
		assertEquals(filter, ruled.withModifiers(null).getRuneFilter());
		assertSame(RuneFilter.NONE, ruled.withRuneFilter(null).getRuneFilter());
		assertSame(RuneFilter.NONE, ruled.withRuneFilter(RuneFilter.NONE).getRuneFilter());
		assertEquals(Collections.emptySet(), plain.getRuneFilter().getBlocked());
	}
}
