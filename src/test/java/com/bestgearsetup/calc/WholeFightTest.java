package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import org.junit.Test;

/** Kill time integrated over the target's HP for attacks that change as it falls. */
public class WholeFightTest
{
	private static Loadout rubyCrossbow()
	{
		GearItem bow = weapon(9185, "rune crossbow", "crossbow", 5, "rapid,ranged,rapid");
		bow.setRangedBonus(90);
		bow.setAmmunition(java.util.Arrays.asList(9242, 9243));
		GearItem bolts = item(9242, "ruby bolts (e)", Slot.AMMO);
		bolts.setRangedStr(103);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, bow);
		l.set(Slot.AMMO, bolts);
		l.setStyle(AttackStyle.parse("rapid,ranged,rapid"));
		return l;
	}

	private static CombatContext context(Monster m)
	{
		return new CombatContext(m, PlayerLevels.maxed(), false, true, null);
	}

	/** Sum of 1/DPS over every integer HP from the start down to 1: the exact fluid kill time. */
	private static double bruteForceDps(Loadout l, CombatContext ctx, int startHp)
	{
		double seconds = 0;
		for (int hp = startHp; hp >= 1; hp--)
		{
			seconds += 1 / DpsCalculator.snapshot(l, ctx.atTargetHp(hp)).getPrimaryDps();
		}
		return startHp / seconds;
	}

	@Test
	public void rubyBoltsAreAveragedAsTheirEffectFallsWithTargetHp()
	{
		Monster target = monster(150, 80);
		target.setHitpoints(900);
		Loadout l = rubyCrossbow();
		CombatContext ctx = context(target);
		DpsResult start = DpsCalculator.snapshot(l, ctx);
		DpsResult fight = DpsCalculator.calculate(l, ctx);
		assertTrue(fight.getDps() < start.getDps());
		assertEquals(bruteForceDps(l, ctx, 900), fight.getPrimaryDps(), fight.getPrimaryDps() * 0.002);
		assertEquals(start.getMaxHit(), fight.getMaxHit());
		assertTrue(fight.getMaxHitDetail(), fight.getMaxHitDetail().contains("whole-fight average from 900 HP"));
	}

	@Test
	public void startingHpSelectsTheRemainingFight()
	{
		Monster target = monster(150, 80);
		target.setHitpoints(900);
		Loadout l = rubyCrossbow();
		CombatContext ctx = context(target).withModifiers(CombatModifiers.builder().monsterHitpoints(300).build());
		assertEquals(bruteForceDps(l, ctx, 300), DpsCalculator.calculate(l, ctx).getPrimaryDps(), 0.002);
	}

	@Test
	public void sunKerisIsExactAcrossItsQuarterHealthBreakpoint()
	{
		Monster akkha = monster(80, 60, "tombs of amascut");
		akkha.setId(11789);
		akkha.setName("akkha");
		akkha.setHitpoints(1000);
		GearItem keris = weapon(27291, "keris partisan of the sun", "spear", 4, "lunge,stab,accurate");
		keris.setStabBonus(58);
		keris.setMeleeStr(75);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, keris);
		l.setStyle(AttackStyle.parse("lunge,stab,accurate"));
		CombatContext ctx = context(akkha);
		double high = DpsCalculator.snapshot(l, ctx).getPrimaryDps();
		double low = DpsCalculator.snapshot(l, ctx.atTargetHp(249)).getPrimaryDps();
		assertTrue(low > high);
		double expected = 1000 / (750 / high + 250 / low);
		assertEquals(expected, DpsCalculator.calculate(l, ctx).getPrimaryDps(), 1e-9);
	}

	@Test
	public void vardorvisDefenceFollowsHisHealthWhenStatesAreSupplied()
	{
		Monster vardorvis = monster(215, 85);
		vardorvis.setId(12223);
		vardorvis.setName("vardorvis");
		vardorvis.setHitpoints(700);
		vardorvis.setStrengthLevel(270);
		GearItem sword = weapon(50000, "test sword", "slash sword", 4, "slash,slash,aggressive");
		sword.setSlashBonus(120);
		sword.setMeleeStr(110);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, sword);
		l.setStyle(AttackStyle.parse("slash,slash,aggressive"));
		CombatContext plain = context(MonsterStates.atHealth(vardorvis, 0));
		assertNull(DpsCalculator.hpBreakpoints(l, plain));
		CombatContext ctx = plain.withHealthStates(hp -> MonsterStates.atHealth(vardorvis, hp));
		DpsResult fight = DpsCalculator.calculate(l, ctx);
		assertTrue(fight.getDps() > DpsCalculator.snapshot(l, ctx).getDps());
		assertEquals(bruteForceDps(l, ctx, 700), fight.getPrimaryDps(), fight.getPrimaryDps() * 0.002);
	}

	@Test
	public void ordinaryAttacksKeepTheirSnapshot()
	{
		Monster target = monster(150, 80);
		target.setHitpoints(900);
		Loadout l = rubyCrossbow();
		l.set(Slot.AMMO, item(9243, "diamond bolts (e)", Slot.AMMO));
		l.get(Slot.AMMO).setRangedStr(105);
		CombatContext ctx = context(target);
		assertNull(DpsCalculator.hpBreakpoints(l, ctx));
		assertEquals(DpsCalculator.snapshot(l, ctx), DpsCalculator.calculate(l, ctx));
	}
}
