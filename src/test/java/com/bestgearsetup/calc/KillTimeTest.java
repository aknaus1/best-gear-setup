package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class KillTimeTest
{
	/** An attack with this max hit, accuracy, average hit (misses included) and speed in ticks. */
	private static DpsResult attack(int maxHit, double accuracy, double averageHit, int speed)
	{
		return new DpsResult(averageHit / (speed * 0.6), maxHit, accuracy, speed, averageHit, null);
	}

	@Test public void expectedAttacksMatchHandWorkedCases()
	{
		// A coin flip of 0 or 1 damage takes two attacks per point.
		assertEquals(2, KillTime.attacks(1, 0, 1, 1), 1e-12);
		assertEquals(4, KillTime.attacks(1, 0, 1, 2), 1e-12);
		// Hits of exactly 10 at 50% accuracy: three hits for 25 HP, two attempts each.
		assertEquals(6, KillTime.attacks(0.5, 10, 10, 25), 1e-12);
		// One attack always kills when every roll is at least the target's HP.
		assertEquals(1, KillTime.attacks(1, 20, 40, 11), 1e-12);
	}

	@Test public void renewalApproximationContinuesTheExactSum()
	{
		int max = 40;
		int last = 16 * max;
		double exact = KillTime.attacks(0.8, 0, max, last);
		double approximate = KillTime.attacks(0.8, 0, max, last + 1);
		double mean = 0.8 * max / 2.0;
		// One more HP adds about one point's worth of attacks.
		assertEquals(exact + 1 / mean, approximate, exact * 0.002);
	}

	@Test public void overkillMattersOnSmallTargetsOnly()
	{
		DpsResult attack = attack(40, 1, 20, 4);
		assertTrue(KillTime.overkillFactor(attack, 11) > 2);
		assertEquals(1, KillTime.overkillFactor(attack, 5000), 0.01);
		assertEquals(5000 / attack.getPrimaryDps(), KillTime.seconds(attack, 5000), 5000 / attack.getPrimaryDps() * 0.01);
	}

	@Test public void aFasterWeaponCanBeatHigherDpsOnSmallTargets()
	{
		DpsResult heavy = attack(60, 1, 30, 6);
		DpsResult quick = attack(20, 1, 10, 3);
		assertTrue(heavy.getPrimaryDps() > quick.getPrimaryDps());
		assertTrue(KillTime.seconds(quick, 11) < KillTime.seconds(heavy, 11));
		assertTrue(KillTime.seconds(heavy, 3000) < KillTime.seconds(quick, 3000));
	}

	@Test public void unknownSpreadAndNoDamageAreHandled()
	{
		// Without a max hit the spread is unknown, so the time stays HP over DPS.
		DpsResult thrallOnly = new DpsResult(1, 0, 0, 4, 0, null);
		assertEquals(11, KillTime.seconds(thrallOnly, 11), 1e-12);
		assertEquals(Double.POSITIVE_INFINITY, KillTime.seconds(DpsResult.ZERO, 11), 0);
	}
}
