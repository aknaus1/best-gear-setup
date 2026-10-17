package com.bestgearsetup.calc;

/**
 * Expected time to deal a target's hitpoints in whole attacks, so the damage wasted on the killing blow counts.
 * HP over DPS treats damage as continuous, which flatters slow, hard-hitting attacks on small targets: an 11 HP
 * nylocas takes a whole attack however much it overkills. Each attack hits with the setup's accuracy for a whole
 * number between a low roll and the max hit, the low roll chosen so the mean matches the setup's average hit (zero
 * for an ordinary hit, higher for minimum-damage effects, the max hit for attacks that always max). The time scales
 * HP over DPS by the expected attacks to kill against the attacks continuous damage would need, so thralls, specs
 * and speed effects already in the DPS keep their share.
 */
public final class KillTime
{
	/**
	 * Beyond this many max hits of HP, the renewal approximation is within a fraction of a percent of the exact
	 * expectation and the exact sum isn't worth its cost.
	 */
	private static final int EXACT_MAX_HITS = 16;

	private KillTime()
	{
	}

	/** Seconds to kill {@code hp} with this attack; infinite when it deals no damage. */
	public static double seconds(DpsResult dps, int hp)
	{
		if (dps.getPrimaryDps() <= 0)
		{
			return Double.POSITIVE_INFINITY;
		}
		return hp / dps.getPrimaryDps() * overkillFactor(dps, hp);
	}

	/**
	 * Expected attacks to kill over the attacks the same mean damage would need without overkill: at least 1, and
	 * 1 when the attack's damage spread is unknown.
	 */
	static double overkillFactor(DpsResult dps, int hp)
	{
		double accuracy = Math.min(1, dps.getAccuracy());
		int high = dps.getMaxHit();
		if (hp <= 0 || accuracy <= 0 || high <= 0 || dps.getAverageHit() <= 0)
		{
			return 1;
		}
		double conditional = dps.getAverageHit() / accuracy;
		int low = conditional >= high ? high : (int) Math.max(0, Math.min(high, Math.round(2 * conditional - high)));
		double mean = accuracy * (low + high) / 2.0;
		return Math.max(1, attacks(accuracy, low, high, hp) * mean / hp);
	}

	/** Expected attacks to deal at least {@code hp}, each hitting with {@code accuracy} for low..high uniformly. */
	static double attacks(double accuracy, int low, int high, int hp)
	{
		if (low == high)
		{
			// Every successful hit deals the same damage: a fixed number of hits, each taking 1/accuracy attempts.
			return Math.ceil(hp / (double) high) / accuracy;
		}
		int span = high - low + 1;
		double each = accuracy / span;
		double zero = 1 - accuracy + (low == 0 ? each : 0);
		if (hp > EXACT_MAX_HITS * high)
		{
			// Lattice renewal function: attacks to reach hp are the renewals within hp - 1, plus the one that kills.
			double mean = accuracy * (low + high) / 2.0;
			double square = 0;
			for (int damage = Math.max(low, 1); damage <= high; damage++) { square += each * damage * (double) damage; }
			return (hp - 1) / mean + (square + mean) / (2 * mean * mean);
		}
		// expected[h] = (1 + sum of P(d) * expected[h - d] over damaging hits) / (1 - P(0)); prefix sums make it linear.
		int first = Math.max(low, 1);
		double[] prefix = new double[hp + 1];
		double expected = 0;
		for (int h = 1; h <= hp; h++)
		{
			int top = h - first;
			int bottom = h - high - 1;
			double reached = top < 1 ? 0 : prefix[top] - (bottom < 1 ? 0 : prefix[bottom]);
			expected = (1 + each * reached) / (1 - zero);
			prefix[h] = prefix[h - 1] + expected;
		}
		return expected;
	}
}
