package com.bestgearsetup.calc;

import lombok.Value;

/**
 * Special attacks spent during a kill. Energy regenerates 10% every 30 seconds (twice as fast with
 * lightbearer); a full bar adds the whole specs it holds at the start of the kill (its remainder is not
 * carried over). Weapon-switch timing and overkill are not modelled.
 *
 * <p>A damage special replaces one ordinary attack for the spec weapon's attack interval, and is used only
 * when it out-damages the ordinary attacks over that interval. A draining special (dragon warhammer,
 * Bandos godsword...) opens each kill instead, so its drain helps the rest of the fight: each opening spec
 * hits or misses at the target's current stats, and the kill finishes with ordinary attacks at the drained
 * stats. With regeneration only, the opening uses the energy regenerated over one kill.
 */
public final class SpecialRotation
{
	/** Percent of special attack energy regenerated per second. */
	public static final double REGEN_PER_SECOND = 10.0 / 30;
	static final int FULL_ENERGY = 100;
	private static final double SECONDS_PER_TICK = 0.6;
	/** Iterations of the regenerated-opening fixed point (specs per kill depend on the kill time). */
	private static final int REGEN_ITERATIONS = 8;

	private SpecialRotation()
	{
	}

	/** Whole-kill damage rates with specs, and the specs used per kill. */
	@Value
	public static class Blend
	{
		DpsResult result;
		double specsPerKill;
	}

	/** The fight's ordinary attacks and special attack once some drains have landed. */
	public interface Fight
	{
		DpsResult ordinary(SpecialAttacks drains);

		DpsResult spec(SpecialAttacks drains);
	}

	public static Blend blend(DpsResult ordinary, DpsResult spec, int energy, int targetHp, SpecEnergy mode)
	{
		return blend(ordinary, spec, energy, targetHp, mode, REGEN_PER_SECOND);
	}

	/**
	 * Blend ordinary attacks with damage specs over a kill of the target's HP, or null when the spec never helps.
	 *
	 * @param ordinary the setup's ordinary attacks over the whole fight
	 * @param spec     one special attack, repeated at its interval
	 * @param energy   energy each spec costs, in percent
	 * @param regen    energy regenerated per second, in percent
	 */
	public static Blend blend(DpsResult ordinary, DpsResult spec, int energy, int targetHp, SpecEnergy mode,
		double regen)
	{
		double ordinaryRate = ordinary.getPrimaryDps();
		double specRate = spec.getPrimaryDps();
		double specSeconds = spec.getExpectedSpeedTicks() * SECONDS_PER_TICK;
		if (energy <= 0 || targetHp <= 0 || specSeconds <= 0 || specRate <= ordinaryRate)
		{
			return null;
		}
		// Share of fight time spent speccing on regenerated energy alone.
		double regenShare = Math.min(1, regen * specSeconds / energy);
		double sustainedRate = regenShare * specRate + (1 - regenShare) * ordinaryRate;
		int opening = mode == SpecEnergy.FULL_BAR ? FULL_ENERGY / energy : 0;
		double specDamage = specRate * specSeconds;

		double seconds;
		double specs;
		if (opening * specDamage >= targetHp)
		{
			// The opening specs kill the target alone.
			seconds = targetHp / specRate;
			specs = targetHp / specDamage;
		}
		else
		{
			double openingSeconds = opening * specSeconds;
			seconds = openingSeconds + (targetHp - opening * specDamage) / sustainedRate;
			specs = opening + regenShare * (seconds - openingSeconds) / specSeconds;
		}
		double specSecondsTotal = specs * specSeconds;
		double dps = (specSecondsTotal * spec.getDps() + (seconds - specSecondsTotal) * ordinary.getDps()) / seconds;
		return new Blend(ordinary.withDamageRates(dps, targetHp / seconds), specs);
	}

	/**
	 * Open each kill with draining specs and finish at the drained stats, or null when the target can't be
	 * killed. The result keeps the undrained ordinary attack's max hit and accuracy.
	 */
	public static Blend draining(Fight fight, SpecialAttack.Drain drain, int energy, int targetHp, SpecEnergy mode,
		double regen)
	{
		return draining(fight, drain, energy, targetHp, mode, regen, false);
	}

	/**
	 * As {@link #draining(Fight, SpecialAttack.Drain, int, int, SpecEnergy, double)}.
	 *
	 * @param allMiss the worst case: every opening spec misses, costing its attack and draining nothing
	 */
	public static Blend draining(Fight fight, SpecialAttack.Drain drain, int energy, int targetHp, SpecEnergy mode,
		double regen, boolean allMiss)
	{
		DpsResult start = fight.ordinary(SpecialAttacks.NONE);
		if (energy <= 0 || targetHp <= 0 || start.getPrimaryDps() <= 0)
		{
			return null;
		}
		int barSpecs = FULL_ENERGY / energy;
		double[] r;
		if (mode == SpecEnergy.FULL_BAR)
		{
			r = new DrainKill(fight, drain, energy, regen, allMiss).path(barSpecs, SpecialAttacks.NONE, targetHp);
		}
		else
		{
			// The regenerated energy banked over one kill opens the next; a fraction is a spec on some kills. A
			// bar holds only so many specs, so energy regenerated beyond it is spent during the kill instead.
			double specSeconds = fight.spec(SpecialAttacks.NONE).getExpectedSpeedTicks() * SECONDS_PER_TICK;
			r = new double[]{targetHp / start.getPrimaryDps(), 0};
			for (int i = 0; i < REGEN_ITERATIONS; i++)
			{
				double regenerated = regen * r[0];
				double opening = Math.min(barSpecs, regenerated / energy);
				double excess = Math.max(0, regenerated - barSpecs * energy);
				double midFight = excess / Math.max(specSeconds, r[0] - barSpecs * specSeconds);
				DrainKill kill = new DrainKill(fight, drain, energy, midFight, allMiss);
				int whole = (int) opening;
				double fraction = opening - whole;
				r = kill.path(whole, SpecialAttacks.NONE, targetHp);
				if (fraction > 0)
				{
					double[] more = kill.path(whole + 1, SpecialAttacks.NONE, targetHp);
					r = new double[]{r[0] + fraction * (more[0] - r[0]), r[1] + fraction * (more[1] - r[1])};
				}
			}
		}
		double seconds = r[0];
		if (!(seconds > 0) || Double.isInfinite(seconds))
		{
			return null;
		}
		double primary = targetHp / seconds;
		double total = primary * start.getDps() / start.getPrimaryDps();
		return new Blend(start.withDamageRates(total, primary), r[1]);
	}

	/** Expected kill time and specs used from a point in the opening. */
	private static final class DrainKill
	{
		private final Fight fight;
		private final SpecialAttack.Drain drain;
		private final int energy;
		/** Energy per second that can be spent after the opening specs. */
		private final double midFightRegen;
		private final boolean allMiss;

		DrainKill(Fight fight, SpecialAttack.Drain drain, int energy, double midFightRegen, boolean allMiss)
		{
			this.fight = fight;
			this.drain = drain;
			this.energy = energy;
			this.midFightRegen = midFightRegen;
			this.allMiss = allMiss;
		}

		/** {seconds, specs} with the given opening specs still to throw and HP left. */
		double[] path(int left, SpecialAttacks drains, double hp)
		{
			if (left <= 0)
			{
				return remainder(drains, hp);
			}
			DpsResult spec = fight.spec(drains);
			double seconds = spec.getExpectedSpeedTicks() * SECONDS_PER_TICK;
			double perSpec = spec.getPrimaryDps() * seconds;
			double accuracy = Math.max(0, Math.min(1, spec.getAccuracy()));
			if (seconds <= 0 || perSpec <= 0 || accuracy <= 0)
			{
				return remainder(drains, hp);
			}
			if (allMiss)
			{
				double[] after = path(left - 1, drains, hp);
				return new double[]{seconds + after[0], 1 + after[1]};
			}
			// A landed spec deals its expected damage given a hit; a miss deals nothing.
			double hitDamage = perSpec / accuracy;
			double[] hit;
			if (hitDamage >= hp)
			{
				hit = new double[]{seconds * hp / hitDamage, 1};
			}
			else
			{
				double[] after = path(left - 1, drains.landed(drain, (int) Math.round(hitDamage)), hp - hitDamage);
				hit = new double[]{seconds + after[0], 1 + after[1]};
			}
			if (accuracy >= 1)
			{
				return hit;
			}
			double[] after = path(left - 1, drains, hp);
			return new double[]{
				accuracy * hit[0] + (1 - accuracy) * (seconds + after[0]),
				accuracy * hit[1] + (1 - accuracy) * (1 + after[1]),
			};
		}

		/**
		 * The rest of the kill at the drained stats, speccing for damage on any energy regenerated meanwhile
		 * (drains from those specs are not applied).
		 */
		private double[] remainder(SpecialAttacks drains, double hp)
		{
			double ordinary = fight.ordinary(drains).getPrimaryDps();
			if (midFightRegen > 0)
			{
				DpsResult spec = fight.spec(drains);
				double seconds = spec.getExpectedSpeedTicks() * SECONDS_PER_TICK;
				if (seconds > 0 && spec.getPrimaryDps() > ordinary)
				{
					double share = Math.min(1, midFightRegen * seconds / energy);
					double time = hp / (share * spec.getPrimaryDps() + (1 - share) * ordinary);
					return new double[]{time, share * time / seconds};
				}
			}
			return new double[]{ordinary > 0 ? hp / ordinary : Double.POSITIVE_INFINITY, 0};
		}
	}
}
