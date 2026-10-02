package com.bestgearsetup.calc;

import java.util.function.LongUnaryOperator;

/** Per-hitsplat damage operations. Misses are separate from successful zero rolls. */
final class HitDamage
{
	private HitDamage()
	{
	}

	static double average(EncounterDamage.Rule rule, long min, long max)
	{
		if (min == 0 && max == 0)
		{
			return rule.mean(1);
		}
		return rule.average(min, max) + (min == 0 && max > 0 ? (rule.mean(1) - rule.mean(0)) / (max + 1.0) : 0);
	}

	static double average(EncounterDamage.Rule rule, long min, long max, LongUnaryOperator transform)
	{
		return averageTransformed(rule, min, max, h -> Math.max(1, transform.applyAsLong(h)));
	}

	static double averageTransformed(EncounterDamage.Rule rule, long min, long max, LongUnaryOperator transform)
	{
		double sum = 0;
		for (long hit = min; hit <= max; hit++)
		{
			sum += rule.mean(transform.applyAsLong(hit));
		}
		return sum / (max - min + 1.0);
	}
}
