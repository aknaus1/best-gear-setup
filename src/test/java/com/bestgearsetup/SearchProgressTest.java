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
	private final List<Integer> published = new ArrayList<>();

	@Test
	public void averagesTheTabs()
	{
		SearchProgress progress = new SearchProgress(3, published::add, now::get);
		progress.update(0, 1);
		progress.update(1, 0.5);
		assertEquals(50, progress.percent());
		progress.update(2, 0.25);
		assertEquals(58, progress.percent());
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
		assertEquals(Arrays.asList(10, 40), published);
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
		assertEquals(Collections.singletonList(40), published);
	}

	@Test
	public void completionIsPublishedImmediatelyOnce()
	{
		SearchProgress progress = new SearchProgress(2, published::add, now::get);
		progress.update(0, 0.5);
		progress.complete();
		progress.complete();
		assertEquals(Arrays.asList(25, 100), published);
	}
}
