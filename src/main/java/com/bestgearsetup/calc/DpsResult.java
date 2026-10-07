package com.bestgearsetup.calc;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class DpsResult
{
	public static final DpsResult ZERO = new DpsResult(0, 0, 0, 0, 0, null);

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

	public DpsResult(double dps, int maxHit, double accuracy, int speed, double averageHit, String detail,
		double primaryDps, int targetsHit)
	{
		this(dps, maxHit, accuracy, speed, averageHit, detail, primaryDps, targetsHit, speed);
	}

	public DpsResult(double dps, int maxHit, double accuracy, int speed, double averageHit, String detail)
	{
		this(dps, maxHit, accuracy, speed, averageHit, detail, dps, 1);
	}

	public DpsResult withAreaDamage(double extraAverage, double returningAverage, int targets)
	{
		return withAreaDamage(extraAverage, returningAverage, targets, 0);
	}

	public DpsResult withAreaDamage(double extraAverage, double returningAverage, int targets, int returningMax)
	{
		double seconds = expectedSpeedTicks * 0.6;
		String detail = returningMax > 0 ? maxHit + " + " + returningMax + " returning bounce"
			+ (maxHitDetail == null ? "" : "; " + maxHitDetail) : maxHitDetail;
		return new DpsResult(dps + extraAverage / seconds, maxHit + returningMax, accuracy, attackSpeedTicks,
			averageHit + extraAverage, detail, primaryDps + returningAverage / seconds, targets, expectedSpeedTicks);
	}

	public DpsResult withExtraDps(double extra)
	{
		return new DpsResult(dps + extra, maxHit, accuracy, attackSpeedTicks, averageHit, maxHitDetail,
			primaryDps + extra, targetsHit, expectedSpeedTicks);
	}

	public DpsResult withDetail(String detail)
	{
		return new DpsResult(dps, maxHit, accuracy, attackSpeedTicks, averageHit, detail,
			primaryDps, targetsHit, expectedSpeedTicks);
	}

	/**
	 * Scale damage to the whole-fight average when the attack changes with the target's HP. Max hit and
	 * accuracy keep their starting values.
	 */
	public DpsResult withWholeFight(double factor, int fromHp)
	{
		String note = "whole-fight average from " + fromHp + " HP";
		return new DpsResult(dps * factor, maxHit, accuracy, attackSpeedTicks, averageHit * factor,
			maxHitDetail == null ? note : maxHitDetail + "; " + note, primaryDps * factor, targetsHit, expectedSpeedTicks);
	}

	/** Replace the damage rates, keeping this attack's max hit, accuracy and speed (special attacks mixed in). */
	public DpsResult withDamageRates(double totalDps, double primary)
	{
		return new DpsResult(totalDps, maxHit, accuracy, attackSpeedTicks, averageHit, maxHitDetail, primary,
			targetsHit, expectedSpeedTicks);
	}

	/** Apply a mean interval before adding independent thrall damage. */
	public DpsResult withExpectedSpeed(double speed)
	{
		double interval = Math.max(1, speed);
		return new DpsResult(dps * expectedSpeedTicks / interval, maxHit, accuracy, attackSpeedTicks, averageHit,
			maxHitDetail, primaryDps * expectedSpeedTicks / interval, targetsHit, interval);
	}
}
