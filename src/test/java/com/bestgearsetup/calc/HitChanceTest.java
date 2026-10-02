package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class HitChanceTest
{
	private static final double EPS = 1e-12;

	/** The rolls a maximum gives: 0..max, or max + 2..0 when the maximum is negative. */
	private static long[] range(long max)
	{
		return max >= 0 ? new long[]{0, max} : new long[]{Math.min(0, max + 2), 0};
	}

	/** Enumerate every equally likely combination of rolls. */
	private static double brute(long maxAttack, long maxDefence, int attackRolls)
	{
		long[] a = range(maxAttack);
		long[] d = range(maxDefence);
		long hits = 0;
		long total = 0;
		for (long x = a[0]; x <= a[1]; x++)
		{
			for (long y = attackRolls == 2 ? a[0] : 0; y <= (attackRolls == 2 ? a[1] : 0); y++)
			{
				long best = attackRolls == 2 ? Math.max(x, y) : x;
				for (long z = d[0]; z <= d[1]; z++)
				{
					total++;
					hits += best > z ? 1 : 0;
				}
			}
		}
		return hits / (double) total;
	}

	@Test
	public void matchesEnumerationIncludingNegativeRolls()
	{
		for (long a = -12; a <= 40; a++)
		{
			for (long d = -12; d <= 40; d++)
			{
				assertEquals(a + "/" + d, brute(a, d, 1), HitChance.single(a, d), EPS);
				assertEquals(a + "/" + d, brute(a, d, 2), HitChance.twoAttackRolls(a, d), EPS);
			}
		}
	}

	@Test
	public void matchesTheWikiFormulasForLargeRolls()
	{
		// Damage per second/Melee: 1 - (d + 2) / (2(a + 1)) if a > d, else a / (2(d + 1)).
		assertEquals(1 - 1002.0 / (2 * 2001), HitChance.single(2000, 1000), EPS);
		assertEquals(1000.0 / (2 * 2001), HitChance.single(1000, 2000), EPS);
		// Osmumten's fang, outside the Tombs of Amascut.
		double a = 30_000;
		double d = 20_000;
		assertEquals(1 - (d + 2) * (2 * d + 3) / (6 * (a + 1) * (a + 1)), HitChance.twoAttackRolls(30_000, 20_000), 1e-9);
		assertEquals(d * (4 * d + 5) / (6 * (d + 1) * (a + 1)), HitChance.twoAttackRolls(20_000, 30_000), 1e-9);
	}

	@Test
	public void retryAfterMissMatchesTheLongRunRate()
	{
		double normal = 0.4;
		double retry = 0.64;
		// Iterate the two-state chain until it settles.
		double pRetry = 0;
		for (int i = 0; i < 1000; i++)
		{
			pRetry = (1 - pRetry) * (1 - normal) + pRetry * (1 - retry);
		}
		double expected = (1 - pRetry) * normal + pRetry * retry;
		assertEquals(expected, HitChance.withRetryAfterMiss(normal, retry), EPS);
		assertEquals(1.0, HitChance.withRetryAfterMiss(1, 1), EPS);
		assertEquals(0.0, HitChance.withRetryAfterMiss(0, 0), EPS);
	}
}
