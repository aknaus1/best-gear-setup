package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import org.junit.Test;

public class ObligatorSpeedTest
{
	private static final String THRUST = "thrust,crush,aggressive";
	private static final String SMASH = "smash,crush,aggressive";

	private static Gear obligator()
	{
		Gear w = weapon(34715, "obligator", "extending chain mace", 5, "thrust,crush,accurate", THRUST, SMASH);
		w.setCrushBonus(120);
		w.setMeleeStr(40);
		return w;
	}

	private static Dps dps(Gear weapon, String style, Monster m)
	{
		Outfit l = new Outfit();
		l.set(Slot.WEAPON, weapon);
		l.setStyle(Style.parse(style));
		return DpsCalculator.calculate(l, TestFactories.combatContext(m, PlayerLevels.maxed(), false, true, piety()));
	}

	@Test
	public void smashIsTwoTicksFasterThanThrustOnlyForTheObligator()
	{
		Gear obligator = obligator();
		assertEquals(5, Weapons.attackSpeed(obligator, Style.parse(THRUST)));
		assertEquals(3, Weapons.attackSpeed(obligator, Style.parse(SMASH)));
		Gear hammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate", "pummel,crush,aggressive");
		assertEquals(6, Weapons.attackSpeed(hammer, Style.parse(SMASH)));
		Gear blowpipe = weapon(12926, "toxic blowpipe", "thrown", 3, "rapid,ranged,rapid");
		assertEquals(2, Weapons.attackSpeed(blowpipe, Style.parse("rapid,ranged,rapid")));
	}

	@Test
	public void thrustRollsMoreTimesTheWeakerTheTargetIsToCrush()
	{
		Gear obligator = obligator();
		Style thrust = Style.parse(THRUST);
		Monster m = monster(100, 50);
		assertEquals(2, Weapons.obligatorRolls(obligator, thrust, m));
		m.setDefCrush(0);
		assertEquals(4, Weapons.obligatorRolls(obligator, thrust, m));
		m.setDefSlash(0);
		assertEquals(3, Weapons.obligatorRolls(obligator, thrust, m));
		assertEquals(1, Weapons.obligatorRolls(obligator, Style.parse(SMASH), m));
	}

	@Test
	public void bestOfOneRollIsTheOrdinaryAverageAndMoreRollsHitHarder()
	{
		Monster m = monster(100, 50);
		Outfit l = new Outfit();
		l.set(Slot.WEAPON, obligator());
		l.setStyle(Style.parse(THRUST));
		Encounter.Rule rule = Encounter.rule(m, l);
		assertEquals(HitDamage.average(rule, 0, 40), HitDamage.bestOf(rule, 40, 1, h -> h), 1e-9);
		// Best of two on 0..1: the best roll is 1 three times in four, and an accurate zero deals 1 anyway.
		assertEquals(1.0, HitDamage.bestOf(rule, 1, 2, h -> h), 1e-9);
		assertTrue(HitDamage.bestOf(rule, 40, 4, h -> h) > HitDamage.bestOf(rule, 40, 2, h -> h));
	}

	@Test
	public void thrustBeatsItsOwnSingleRollAndGainsTheSizeBonusOnBigTargets()
	{
		Gear obligator = obligator();
		Monster m = monster(100, 50);
		m.setDefCrush(0);
		m.setSize(1);
		Dps small = dps(obligator, THRUST, m);
		assertEquals(5, small.getAttackSpeedTicks());
		assertEquals(3, dps(obligator, SMASH, m).getAttackSpeedTicks());
		m.setSize(3);
		Dps big = dps(obligator, THRUST, m);
		assertEquals(small.getMaxHit() * 140 / 100, big.getMaxHit(), 1);
		assertTrue(big.getDps() > small.getDps() * 1.3);
	}
}
