package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import com.bestgearsetup.data.GearItem;
import org.junit.Test;

public class ObligatorSpeedTest
{
	@Test
	public void smashIsTwoTicksFasterThanThrustOnlyForTheObligator()
	{
		AttackStyle thrust = AttackStyle.parse("thrust,crush,aggressive");
		AttackStyle smash = AttackStyle.parse("smash,crush,aggressive");
		GearItem obligator = weapon(34715, "obligator", "obligator", 5, "thrust,crush,aggressive", "smash,crush,aggressive");
		assertEquals(5, WeaponRules.attackSpeed(obligator, thrust));
		assertEquals(3, WeaponRules.attackSpeed(obligator, smash));
		GearItem maul = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate", "pummel,crush,aggressive");
		assertEquals(6, WeaponRules.attackSpeed(maul, smash));
		GearItem blowpipe = weapon(12926, "toxic blowpipe", "thrown", 3, "rapid,ranged,rapid");
		assertEquals(2, WeaponRules.attackSpeed(blowpipe, AttackStyle.parse("rapid,ranged,rapid")));
	}
}
