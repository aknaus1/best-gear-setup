package com.bestgearsetup.calc;

import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Gear;
import java.util.function.IntPredicate;
import java.util.function.ToLongFunction;

/** Test shorthand: an optimizer with every owned item held in any quantity. */
public final class Optimizers
{
	private Optimizers()
	{
	}

	public static Optimizer of(GameData data, CombatContext ctx, OptimizerSettings settings, IntPredicate owned,
		ToLongFunction<Gear> priceFn)
	{
		return new Optimizer(data, ctx, settings, owned, id -> owned.test(id) ? Long.MAX_VALUE : 0, priceFn);
	}
}
