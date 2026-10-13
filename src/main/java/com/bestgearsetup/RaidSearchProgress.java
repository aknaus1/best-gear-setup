package com.bestgearsetup;

import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

/** One monotonic percentage for all encounters, kit selection and any consolidation passes. */
final class RaidSearchProgress
{
	private final int encounters;
	private final SearchProgress overall;

	RaidSearchProgress(int encounters, IntConsumer publish)
	{
		this(encounters, publish, System::nanoTime);
	}

	RaidSearchProgress(int encounters, IntConsumer publish, LongSupplier clock)
	{
		this.encounters = Math.max(1, encounters);
		overall = new SearchProgress(1, publish, clock);
	}

	IntConsumer encounter(int round, int index)
	{
		double start = start(round);
		double span = (end(round) - start) * 0.9;
		return range(start + span * index / encounters, span / encounters);
	}

	IntConsumer selection(int round)
	{
		double span = end(round) - start(round);
		return range(start(round) + span * 0.9, span * 0.1);
	}

	void complete() { overall.complete(); }

	private IntConsumer range(double start, double span)
	{
		return percent -> overall.update(0, Math.min(0.9999, start + span * Math.max(0, Math.min(100, percent)) / 100.0));
	}

	// Extra passes are discovered only after kit selection. Reserve a decreasing part of the remaining
	// range for them, without resetting progress or claiming completion before a valid kit is ready.
	private static double start(int round) { return round == 0 ? 0 : end(round - 1); }
	private static double end(int round) { return 1 - 0.05 / Math.pow(2, round); }
}
