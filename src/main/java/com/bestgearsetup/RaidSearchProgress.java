package com.bestgearsetup;

import java.util.function.DoubleConsumer;
import java.util.function.LongSupplier;

/** Estimated completed work across encounter searches, packing and adaptive consolidation. */
final class RaidSearchProgress
{
	private final int encounters;
	private final SearchProgress overall;
	/** Per thread: consolidation may search several trials at once, each in its own range. */
	private final ThreadLocal<Tasks> active = new ThreadLocal<>();

	RaidSearchProgress(int encounters, DoubleConsumer publish)
	{
		this(encounters, publish, System::nanoTime);
	}

	RaidSearchProgress(int encounters, DoubleConsumer publish, LongSupplier clock)
	{
		this.encounters = Math.max(1, encounters);
		overall = new SearchProgress(1, publish, clock);
		beginEncounter(0);
	}

	/** One planned encounter, including any full setups, quick switches and fallback searches it needs. */
	void beginEncounter(int index) { active.set(new Tasks(range(0.4 * index / encounters, 0.4 / encounters))); }
	void endEncounter(int index) { overall.update(0, 0.4 * (index + 1) / encounters); }
	DoubleConsumer search() { return tasks().next(); }
	void expectSearches(int searches) { tasks().expected = Math.max(1, searches); }
	DoubleConsumer selection() { return range(0.4, 0.15); }
	DoubleConsumer independentSelection() { return range(0.55, 0.05); }

	/** A trial supplies its known work denominator; its nested searches share that trial's range. */
	void searching(DoubleConsumer work) { active.set(new Tasks(work)); }

	/** Remaining consolidation passes are discovered as gear is removed; reserve half for the next pass. */
	DoubleConsumer consolidation(int pass)
	{
		double remaining = 0.35 / Math.pow(2, Math.max(0, pass - 1));
		return range(0.95 - remaining, remaining / 2);
	}

	void finalization() { active.set(new Tasks(range(0.95, 0.0499))); }
	void complete() { overall.complete(); }

	/** This thread's current work; a thread that was never given any reports nothing. */
	private Tasks tasks()
	{
		Tasks tasks = active.get();
		if (tasks == null) { tasks = new Tasks(percent -> { }); active.set(tasks); }
		return tasks;
	}

	private DoubleConsumer range(double start, double span)
	{
		return percent -> overall.update(0, Math.min(0.9999, start + span * Math.max(0, Math.min(100, percent)) / 100));
	}

	/** Adaptive searches within a trial are unknown in advance; every actual call gets a fresh range. */
	private static final class Tasks
	{
		private final DoubleConsumer progress;
		private int tasks;
		private int expected;
		Tasks(DoubleConsumer progress) { this.progress = progress; }
		DoubleConsumer next()
		{
			int index = tasks++;
			if (expected > 0 && index < expected)
			{
				return percent -> progress.accept(90.0 * (index + Math.max(0, Math.min(100, percent)) / 100) / expected);
			}
			double remaining = Math.pow(0.5, Math.max(0, index - expected));
			double start = expected > 0 ? 90 : 0;
			double span = expected > 0 ? 10 : 100;
			return percent -> progress.accept(start + span * (1 - remaining) + span * remaining * Math.max(0, Math.min(100, percent)) / 200);
		}
	}
}
