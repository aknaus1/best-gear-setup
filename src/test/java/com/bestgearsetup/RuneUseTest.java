package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SpecialPlan;
import com.bestgearsetup.calc.SupportSpell;
import com.bestgearsetup.calc.Thrall;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class RuneUseTest
{
	private static final double EPSILON = 1e-9;

	@Test
	public void kodaiSavesWaterAndFifteenPercentOfAttackRunes()
	{
		RuneUse use = RuneUse.of(setup("ice barrage", "kodai wand", null, dps(4, 4, 1, 5),
			Collections.emptySet(), null, null), 200, 99, 99);
		assertEquals(50, use.getSecondsPerKill(), EPSILON);
		assertEquals(50.0 / 3, use.getCastsPerKill(), EPSILON);
		assertRunes(use.getAttack(), ItemID.DEATHRUNE, 56.66666666666667,
			ItemID.BLOODRUNE, 28.333333333333336);
		assertTrue(use.getSupport().isEmpty());
	}

	@Test
	public void areaDamageUsesTotalDps()
	{
		RuneUse use = RuneUse.of(setup("blood barrage", "ancient staff", null, dps(10, 4, 9, 5),
			Collections.emptySet(), null, null), 100, 99, 99);
		assertEquals(10, use.getSecondsPerKill(), EPSILON);
		assertEquals(10.0 / 3, use.getCastsPerKill(), EPSILON);
		assertRunes(use.getAttack(), ItemID.DEATHRUNE, 13.333333333333334,
			ItemID.BLOODRUNE, 13.333333333333334, ItemID.SOULRUNE, 3.3333333333333335);
	}

	@Test
	public void specialsSubtractTimeBeforeCountingCasts()
	{
		DpsResult ordinary = dps(5, 5, 1, 4);
		SpecialPlan special = new SpecialPlan(null, null, null, null, null, null, null,
			dps(6, 6, 1, 5), ordinary, null, 2, 0, 0);
		RuneUse use = RuneUse.of(setup("fire surge", "staff of the dead", "tome of fire",
			dps(6, 6, 1, 4), Collections.emptySet(), null, special), 300, 99, 99);
		assertEquals((50 - 6) / 2.4, use.getCastsPerKill(), EPSILON);
		assertRunes(use.getAttack(), ItemID.AIRRUNE, 110.0, ItemID.WRATHRUNE, 15.714285714285714);
	}

	@Test
	public void thrallsCountWithoutAnAttackSpell()
	{
		RuneUse use = RuneUse.of(setup(null, "abyssal whip", null, dps(2, 2, 1, 4),
			EnumSet.of(SupportSpell.THRALL), Thrall.GREATER, null), 100, 99, 99);
		assertEquals(0, use.getCastsPerKill(), EPSILON);
		assertTrue(use.getAttack().isEmpty());
		assertFalse(use.isEmpty());
		assertRunes(use.getSupport(), ItemID.FIRERUNE, 50.0 / 59.4 * 10,
			ItemID.BLOODRUNE, 50.0 / 59.4 * 5, ItemID.COSMICRUNE, 50.0 / 59.4);
	}

	@Test
	public void suppliedRunesAreRemovedFromSupport()
	{
		RuneUse use = RuneUse.of(setup(null, "staff of fire", null, dps(2, 2, 1, 4),
			EnumSet.of(SupportSpell.THRALL), Thrall.GREATER, null), 100, 99, 99);
		assertRunes(use.getSupport(), ItemID.BLOODRUNE, 50.0 / 59.4 * 5,
			ItemID.COSMICRUNE, 50.0 / 59.4);
	}

	@Test
	public void purgingStaffExtendsMarkOfDarkness()
	{
		RuneUse use = RuneUse.of(setup("dark demonbane", "purging staff", null, dps(5, 5, 1, 5),
			EnumSet.of(SupportSpell.MARK_OF_DARKNESS), null, null), 250, 99, 99);
		assertRunes(use.getAttack(), ItemID.FIRERUNE, 116.66666666666667,
			ItemID.SOULRUNE, 33.333333333333336);
		assertRunes(use.getSupport(), ItemID.SOULRUNE, 50.0 / 891, ItemID.COSMICRUNE, 50.0 / 891);
	}

	@Test
	public void lesserThrallUsesItsOwnRunesAndDuration()
	{
		RuneUse use = RuneUse.of(setup(null, "abyssal whip", null, dps(1, 1, 1, 4),
			EnumSet.of(SupportSpell.THRALL), Thrall.LESSER, null), 60, 50, 50);
		assertRunes(use.getSupport(), ItemID.AIRRUNE, 20, ItemID.MINDRUNE, 10, ItemID.COSMICRUNE, 2);
	}

	@Test
	public void poweredStaffHasNoRuneCost()
	{
		RuneUse use = RuneUse.of(setup(null, "tumeken's shadow", null, dps(5, 5, 1, 5),
			Collections.emptySet(), null, null), 100, 99, 99);
		assertTrue(use.isEmpty());
		assertEquals(0, use.getCastsPerKill(), EPSILON);
	}

	@Test
	public void invalidDpsHpOrSetupIsEmpty()
	{
		for (RuneUse use : List.of(RuneUse.of(null, 100, 99, 99),
			RuneUse.of(setup("fire bolt", "magic staff", null, dps(0, 0, 1, 5),
				Collections.emptySet(), null, null), 100, 99, 99),
			RuneUse.of(setup("fire bolt", "magic staff", null, dps(5, 5, 1, 5),
				Collections.emptySet(), null, null), 0, 99, 99)))
		{
			assertTrue(use.isEmpty());
			assertEquals(0, use.getCastsPerKill(), EPSILON);
			assertEquals(0, use.getSecondsPerKill(), EPSILON);
		}
	}

	@Test
	public void chargeIsASeparateAmortizedSupportCost()
	{
		RuneUse use = RuneUse.of(setup("flames of zamorak", "zamorak staff", null, dps(5, 5, 1, 5),
			EnumSet.of(SupportSpell.CHARGE), null, null), 210, 99, 99);
		assertEquals(14, use.getCastsPerKill(), EPSILON);
		assertRunes(use.getAttack(), ItemID.FIRERUNE, 56, ItemID.BLOODRUNE, 28, ItemID.AIRRUNE, 14);
		assertRunes(use.getSupport(), ItemID.AIRRUNE, 0.3, ItemID.FIRERUNE, 0.3, ItemID.BLOODRUNE, 0.3);
	}

	private static SetupResult setup(String spellName, String weapon, String shield, DpsResult dps,
		Set<SupportSpell> support, Thrall tier, SpecialPlan special)
	{
		Loadout loadout = new Loadout();
		if (spellName != null)
		{
			Spell spell = new Spell();
			spell.setName(spellName);
			loadout.setSpell(spell);
		}
		loadout.set(Slot.WEAPON, gear(weapon));
		if (shield != null)
		{
			loadout.set(Slot.SHIELD, gear(shield));
		}
		return new SetupResult(spellName == null ? CombatClass.MELEE : CombatClass.MAGIC,
			loadout, dps, 0, 0, Collections.emptySet(), support, special, tier);
	}

	private static GearItem gear(String name)
	{
		GearItem item = new GearItem();
		item.setName(name);
		return item;
	}

	private static DpsResult dps(double dps, double primary, int targets, double speed)
	{
		return new DpsResult(dps, 0, 1, (int) speed, 0, null, primary, targets, speed);
	}

	private static void assertRunes(Map<Integer, Double> actual, double... pairs)
	{
		List<Integer> order = new ArrayList<>(actual.keySet());
		assertEquals(pairs.length / 2, order.size());
		for (int i = 0; i < pairs.length; i += 2)
		{
			int rune = (int) pairs[i];
			assertEquals(rune, (int) order.get(i / 2));
			assertEquals(pairs[i + 1], actual.get(rune), EPSILON);
		}
	}
}
