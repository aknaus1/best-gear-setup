package com.bestgearsetup.calc;

/**
 * Chance that an attack hits, counted exactly from the rolls the game makes.
 *
 * <p>An attack hits when the attack roll is strictly higher than the defence roll. A roll with maximum
 * {@code max >= 0} is uniform on {@code 0..max}. The game draws a roll as {@code (int) (random() * (max + 1))},
 * so a negative maximum (a bonus below -64) truncates towards zero and is uniform on {@code max + 2..0}
 * (just {@code 0} when {@code max} is -1).
 *
 * <p>The miss chance is the average, over every defence roll {@code y}, of the chance that every attack roll
 * is at most {@code y}. With the attack roll uniform on {@code lo..hi}, that chance is
 * {@code clamp(y - lo + 1, 0, n) / n} for {@code n = hi - lo + 1}, so summing it over the defence rolls
 * needs only sums of consecutive integers or squares.
 */
public final class HitChance
{
	private HitChance()
	{
	}

	/** One attack roll against one defence roll (Wiki: "Damage per second/Melee", hit chance). */
	public static double single(long maxAttackRoll, long maxDefenceRoll)
	{
		return hitWithAttackRolls(maxAttackRoll, maxDefenceRoll, 1);
	}

	/**
	 * Two independent attack rolls against one defence roll; either may beat it. Used by Osmumten's fang outside
	 * the Tombs of Amascut and the confliction gauntlets' second roll (Wiki: their item pages).
	 */
	public static double twoAttackRolls(long maxAttackRoll, long maxDefenceRoll)
	{
		return hitWithAttackRolls(maxAttackRoll, maxDefenceRoll, 2);
	}

	/**
	 * Long-run hit rate when every attack after a miss gets {@code retry} instead of {@code normal} (confliction
	 * gauntlets). Attacks alternate between a normal state and a retry state: a hit returns to the normal state
	 * and a miss moves to the retry state. In the steady state, entering the retry state (a normal miss) is as
	 * likely as leaving it (a retry hit): {@code pNormal * (1 - normal) = pRetry * retry}.
	 */
	public static double withRetryAfterMiss(double normal, double retry)
	{
		double total = (1 - normal) + retry;
		if (total <= 0)
		{
			return normal;
		}
		double pNormal = retry / total;
		double pRetry = (1 - normal) / total;
		return pNormal * normal + pRetry * retry;
	}

	/** Chance that the best of {@code attackRolls} (1 or 2) attack rolls beats one defence roll. */
	private static double hitWithAttackRolls(long maxAttackRoll, long maxDefenceRoll, int attackRolls)
	{
		long attackLow = lowest(maxAttackRoll);
		long attackCount = Math.max(0, maxAttackRoll) - attackLow + 1;
		long defenceLow = lowest(maxDefenceRoll);
		long defenceCount = Math.max(0, maxDefenceRoll) - defenceLow + 1;
		// For defence roll y, the number of attack rolls at most y is clamp(y - attackLow + 1, 0, attackCount).
		long from = defenceLow - attackLow + 1;
		long to = from + defenceCount - 1;
		double missWeight = sumClampedPowers(from, to, attackCount, attackRolls);
		return 1 - missWeight / (defenceCount * Math.pow(attackCount, attackRolls));
	}

	private static long lowest(long maxRoll)
	{
		return maxRoll >= 0 ? 0 : Math.min(0, maxRoll + 2);
	}

	/** The sum of {@code clamp(s, 0, cap)^power} for {@code s} from {@code from} to {@code to}; power is 1 or 2. */
	private static double sumClampedPowers(long from, long to, long cap, int power)
	{
		double total = 0;
		long rising = Math.max(from, 1);
		long top = Math.min(to, cap);
		if (rising <= top)
		{
			total += powerSum(top, power) - powerSum(rising - 1, power);
		}
		long saturated = to - Math.max(from, cap + 1) + 1;
		if (saturated > 0)
		{
			total += saturated * Math.pow(cap, power);
		}
		return total;
	}

	/** 1 + 2 + ... + n, or 1 + 4 + ... + n^2. */
	private static double powerSum(long n, int power)
	{
		double x = n;
		return power == 1 ? x * (x + 1) / 2 : x * (x + 1) * (2 * x + 1) / 6;
	}
}
