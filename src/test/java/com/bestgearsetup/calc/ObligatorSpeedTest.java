package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import org.junit.Test;

public class ObligatorSpeedTest
{
	private static final String THRUST = "thrust,crush,aggressive";
	private static final String SMASH = "smash,crush,aggressive";

	private static GearItem obligator()
	{
		GearItem w = weapon(34715, "obligator", "extending chain mace", 5, "thrust,crush,accurate", THRUST, SMASH);
		w.setCrushBonus(120);
		w.setMeleeStr(40);
		return w;
	}

	private static DpsResult dps(GearItem weapon, String style, Monster m)
	{
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, weapon);
		l.setStyle(AttackStyle.parse(style));
		return DpsCalculator.calculate(l, new CombatContext(m, PlayerLevels.maxed(), false, true, piety()));
	}

	@Test
	public void smashIsTwoTicksFasterThanThrustOnlyForTheObligator()
	{
		GearItem obligator = obligator();
		assertEquals(5, WeaponRules.attackSpeed(obligator, AttackStyle.parse(THRUST)));
		assertEquals(3, WeaponRules.attackSpeed(obligator, AttackStyle.parse(SMASH)));
		GearItem hammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate", "pummel,crush,aggressive");
		assertEquals(6, WeaponRules.attackSpeed(hammer, AttackStyle.parse(SMASH)));
		GearItem blowpipe = weapon(12926, "toxic blowpipe", "thrown", 3, "rapid,ranged,rapid");
		assertEquals(2, WeaponRules.attackSpeed(blowpipe, AttackStyle.parse("rapid,ranged,rapid")));
	}

	@Test
	public void thrustRollsMoreTimesTheWeakerTheTargetIsToCrush()
	{
		GearItem obligator = obligator();
		AttackStyle thrust = AttackStyle.parse(THRUST);
		Monster m = monster(100, 50);
		assertEquals(2, WeaponRules.obligatorRolls(obligator, thrust, m));
		m.setDefCrush(0);
		assertEquals(4, WeaponRules.obligatorRolls(obligator, thrust, m));
		m.setDefSlash(0);
		assertEquals(3, WeaponRules.obligatorRolls(obligator, thrust, m));
		assertEquals(1, WeaponRules.obligatorRolls(obligator, AttackStyle.parse(SMASH), m));
	}

	@Test
	public void bestOfOneRollIsTheOrdinaryAverageAndMoreRollsHitHarder()
	{
		Monster m = monster(100, 50);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, obligator());
		l.setStyle(AttackStyle.parse(THRUST));
		EncounterDamage.Rule rule = EncounterDamage.rule(m, l);
		assertEquals(HitDamage.average(rule, 0, 40), HitDamage.bestOf(rule, 40, 1, h -> h), 1e-9);
		// Best of two on 0..1: the best roll is 1 three times in four, and an accurate zero deals 1 anyway.
		assertEquals(1.0, HitDamage.bestOf(rule, 1, 2, h -> h), 1e-9);
		assertTrue(HitDamage.bestOf(rule, 40, 4, h -> h) > HitDamage.bestOf(rule, 40, 2, h -> h));
	}

	@Test
	public void thrustBeatsItsOwnSingleRollAndGainsTheSizeBonusOnBigTargets()
	{
		GearItem obligator = obligator();
		Monster m = monster(100, 50);
		m.setDefCrush(0);
		m.setSize(1);
		DpsResult small = dps(obligator, THRUST, m);
		assertEquals(5, small.getAttackSpeedTicks());
		assertEquals(3, dps(obligator, SMASH, m).getAttackSpeedTicks());
		m.setSize(3);
		DpsResult big = dps(obligator, THRUST, m);
		assertEquals(small.getMaxHit() * 140 / 100, big.getMaxHit(), 1);
		assertTrue(big.getDps() > small.getDps() * 1.3);
	}
}
