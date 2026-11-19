package com.bestgearsetup.calc;

import lombok.*;

@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class Dps
{
	public static final Dps ZERO = new Dps(0, 0, 0, 0, 0, null);

	double dps;
	int maxHit;
	/** Probability of a successful hit, 0..1. */
	double accuracy;
	int attackSpeedTicks;
	/** Expected damage per attack, including misses. */
	double averageHit;
	/** How the max hit is made up when it is not a single hit, e.g. "26 + 26" or "7-44"; otherwise null. */
	String maxHitDetail;
	/** DPS on the selected enemy, including any returning venator bounce. */
	double primaryDps;
	/** Distinct enemies hit by this attack. */
	int targetsHit;
	/** Mean attack interval when a set effect shortens some attacks. */
	double expectedSpeedTicks;

	public Dps(double dps, int maxHit, double accuracy, int speed, double averageHit, String detail)
	{
		this(dps, maxHit, accuracy, speed, averageHit, detail, dps, 1, speed);
	}

	public Dps withAreaDamage(double extraAverage, double returningAverage, int targets)
	{
		return withAreaDamage(extraAverage, returningAverage, targets, 0);
	}

	public Dps withAreaDamage(double extraAverage, double returningAverage, int targets, int returningMax)
	{
		double seconds = expectedSpeedTicks * 0.6;
		String detail = returningMax > 0 ? maxHit + " + " + returningMax + " returning bounce"
			+ (maxHitDetail == null ? "" : "; " + maxHitDetail) : maxHitDetail;
		return new Dps(dps + extraAverage / seconds, maxHit + returningMax, accuracy, attackSpeedTicks,
			averageHit + extraAverage, detail, primaryDps + returningAverage / seconds, targets, expectedSpeedTicks);
	}

	public Dps withExtraDps(double extra)
	{
		return withDamageRates(dps + extra, primaryDps + extra);
	}

	public Dps withDetail(String detail)
	{
		return toBuilder().maxHitDetail(detail).build();
	}

	/**
	 * Scale damage to the whole-fight average when the attack changes with the target's HP. Max hit and
	 * accuracy keep their starting values.
	 */
	public Dps withWholeFight(double factor, int fromHp)
	{
		String note = "whole-fight average from " + fromHp + " HP";
		return new Dps(dps * factor, maxHit, accuracy, attackSpeedTicks, averageHit * factor,
			maxHitDetail == null ? note : maxHitDetail + "; " + note, primaryDps * factor, targetsHit, expectedSpeedTicks);
	}

	/** Replace the damage rates, keeping this attack's max hit, accuracy and speed (special attacks mixed in). */
	public Dps withDamageRates(double totalDps, double primary)
	{
		return toBuilder().dps(totalDps).primaryDps(primary).build();
	}

	/** Apply a mean interval before adding independent thrall damage. */
	public Dps withExpectedSpeed(double speed)
	{
		double interval = Math.max(1, speed);
		return toBuilder().dps(dps * expectedSpeedTicks / interval).primaryDps(primaryDps * expectedSpeedTicks / interval)
			.expectedSpeedTicks(interval).build();
	}
}
