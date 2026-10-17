package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleConsumer;
import org.junit.Test;

public class RaidSearchProgressTest
{
	private final AtomicLong now = new AtomicLong();
	private final List<Double> published = new ArrayList<>();

	private void update(DoubleConsumer task, double percent)
	{
		now.addAndGet(SearchProgress.INTERVAL_NANOS);
		task.accept(percent);
	}

	@Test public void nestedSearchesAdvanceInFreshRangesWithoutUnusedDisplayEncounters()
	{
		RaidSearchProgress progress = new RaidSearchProgress(2, published::add, now::get);
		progress.beginEncounter(0);
		update(progress.search(), 100);
		assertEquals(10, published.get(published.size() - 1), 0);
		update(progress.search(), 50);
		assertEquals(12.5, published.get(published.size() - 1), 0);
		now.addAndGet(SearchProgress.INTERVAL_NANOS); progress.endEncounter(0);
		progress.beginEncounter(1);
		update(progress.search(), 100);
		now.addAndGet(SearchProgress.INTERVAL_NANOS); progress.endEncounter(1);
		assertEquals(40, published.get(published.size() - 1), 0);
		update(progress.selection(), 100);
		update(progress.independentSelection(), 100);
		assertEquals(60, published.get(published.size() - 1), 0);
	}

	@Test public void everyConsolidationTrialAndNestedSearchCanAdvance()
	{
		RaidSearchProgress progress = new RaidSearchProgress(1, published::add, now::get);
		update(progress.independentSelection(), 100);
		DoubleConsumer pass = progress.consolidation(1);
		for (int trial = 0; trial < 4; trial++)
		{
			int index = trial;
			progress.searching(percent -> pass.accept(index * 20 + percent * 0.2));
			DoubleConsumer first = progress.search();
			update(first, 25); update(first, 100);
			DoubleConsumer second = progress.search();
			update(second, 25); update(second, 100);
		}
		assertEquals(17, published.size());
		assertTrue(published.get(published.size() - 1) > 70);
		for (int round = 1; round < 9; round++) { update(progress.consolidation(round), 100); }
		assertTrue(published.stream().allMatch(percent -> percent < 100));
		for (int i = 1; i < published.size(); i++) { assertTrue(published.get(i) > published.get(i - 1)); }
		progress.finalization(); update(progress.search(), 100);
		assertTrue(published.get(published.size() - 1) > 95);
		progress.complete(); progress.complete();
		assertEquals(100, published.get(published.size() - 1), 0);
	}
}
