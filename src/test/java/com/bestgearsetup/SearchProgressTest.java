package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class SearchProgressTest
{
	private final AtomicLong now = new AtomicLong();
	private final List<Double> published = new ArrayList<>();

	@Test
	public void averagesTheTabs()
	{
		SearchProgress progress = new SearchProgress(3, published::add, now::get);
		progress.update(0, 1);
		progress.update(1, 0.5);
		assertEquals(50, progress.percent(), 1e-9);
		progress.update(2, 0.25);
		assertEquals(58.333333, progress.percent(), 1e-6);
	}

	@Test
	public void publishesAtMostOncePerInterval()
	{
		SearchProgress progress = new SearchProgress(1, published::add, now::get);
		progress.update(0, 0.1);
		progress.update(0, 0.2);
		now.addAndGet(SearchProgress.INTERVAL_NANOS - 1);
		progress.update(0, 0.3);
		now.addAndGet(1);
		progress.update(0, 0.4);
		assertEquals(Arrays.asList(10.0, 40.0), published);
	}

	@Test
	public void neverPublishesTheSameOrALowerPercentage()
	{
		SearchProgress progress = new SearchProgress(2, published::add, now::get);
		progress.update(0, 0.8);
		now.addAndGet(SearchProgress.INTERVAL_NANOS);
		progress.update(0, 0.8);
		now.addAndGet(SearchProgress.INTERVAL_NANOS);
		progress.update(1, 0);
		assertEquals(Collections.singletonList(40.0), published);
	}

	@Test
	public void completionIsPublishedImmediatelyOnce()
	{
		SearchProgress progress = new SearchProgress(2, published::add, now::get);
		progress.update(0, 0.5);
		progress.complete();
		progress.complete();
		assertEquals(Arrays.asList(25.0, 100.0), published);
	}
	@Test public void fractionalWorkSurvivesNestedStagesAndCannotCompleteBeforePublication()
	{
		SearchProgress outer = new SearchProgress(4, published::add, now::get);
		SearchProgress inner = new SearchProgress(3, percent -> outer.update(0, percent / 100), now::get);
		inner.update(0, 0.003);
		assertEquals(Collections.singletonList(0.02), published);
		now.addAndGet(SearchProgress.INTERVAL_NANOS);
		inner.update(0, 0.006);
		assertEquals(Arrays.asList(0.02, 0.05), published);
		now.addAndGet(SearchProgress.INTERVAL_NANOS);
		inner.update(0, 0.001);
		assertEquals(0.2, inner.percent(), 1e-9);
		for (int tab = 0; tab < 4; tab++)
		{
			now.addAndGet(SearchProgress.INTERVAL_NANOS); outer.update(tab, 1);
		}
		assertEquals(99.99, published.get(published.size() - 1), 0);
		outer.complete();
		assertEquals(100, published.get(published.size() - 1), 0);
	}

}
