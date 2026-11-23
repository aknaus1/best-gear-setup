package com.bestgearsetup;

import com.bestgearsetup.calc.Outfit;
import com.bestgearsetup.calc.SpecialAttacks;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Slot;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;

/** Test shorthand for fight plans without special attacks: each phase its own health bar and a full spec bar. */
public final class FightPlans
{
	private FightPlans()
	{
	}

	/** A fight with no spec weapons. */
	public static class Plain implements FightPlan.Fight
	{
		private final ToDoubleBiFunction<Outfit, Integer> dps;

		public Plain(ToDoubleBiFunction<Outfit, Integer> dps)
		{
			this.dps = dps;
		}

		@Override
		public double dps(Outfit outfit, int phase, SpecialAttacks drains)
		{
			return dps.applyAsDouble(outfit, phase);
		}

		@Override
		public double[] spec(Outfit held, int phase, SpecialAttacks drains)
		{
			return new double[3];
		}

		@Override
		public List<Outfit> specs(Outfit worn)
		{
			return Collections.emptyList();
		}

		@Override
		public int energy(Outfit held)
		{
			return 50;
		}

		@Override
		public SpecialAttacks landed(Outfit held, SpecialAttacks drains, double damage)
		{
			return drains;
		}

		@Override
		public double regen(Outfit worn)
		{
			return 1.0 / 3;
		}
	}

	public static FightPlan.Plan plan(List<Outfit> candidates, double[] work, ToDoubleBiFunction<Outfit, Integer> dps, int slots,
		Set<Slot> locked, Predicate<Collection<Gear>> allowed, BooleanSupplier cancelled)
	{
		return plan(candidates, work, new Plain(dps), slots, locked, allowed, cancelled);
	}

	public static FightPlan.Plan plan(List<Outfit> candidates, double[] work, FightPlan.Fight fight, int slots,
		Set<Slot> locked, Predicate<Collection<Gear>> allowed, BooleanSupplier cancelled)
	{
		String[] bar = new String[work.length];
		for (int i = 0; i < bar.length; i++)
		{
			bar[i] = "own " + i;
		}
		return FightPlan.plan(candidates, work, bar, work, fight, true, slots, locked, allowed, cancelled);
	}
}
