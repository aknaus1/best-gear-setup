package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class RaidSearchProgressTest
{
	private final AtomicLong now = new AtomicLong();
	private final List<Integer> published = new ArrayList<>();

	@Test public void allEncountersShareOneRangeWithRoomForKitSelection()
	{
		RaidSearchProgress progress = new RaidSearchProgress(2, published::add, now::get);
		progress.encounter(0, 0).accept(0);
		now.addAndGet(SearchProgress.INTERVAL_NANOS);
		progress.encounter(0, 0).accept(100);
		now.addAndGet(SearchProgress.INTERVAL_NANOS);
		progress.encounter(0, 1).accept(100);
		now.addAndGet(SearchProgress.INTERVAL_NANOS);
		progress.selection(0).accept(100);
		assertEquals(Arrays.asList(0, 42, 85, 95), published);
		progress.complete();
		progress.complete();
		assertEquals(Arrays.asList(0, 42, 85, 95, 100), published);
	}

	@Test public void consolidationPassesContinueWithoutResettingOrCompletingEarly()
	{
		RaidSearchProgress progress = new RaidSearchProgress(3, published::add, now::get);
		for (int round = 0; round < 8; round++)
		{
			for (int encounter = 0; encounter < 3; encounter++)
			{
				now.addAndGet(SearchProgress.INTERVAL_NANOS);
				progress.encounter(round, encounter).accept(100);
			}
			now.addAndGet(SearchProgress.INTERVAL_NANOS);
			progress.selection(round).accept(100);
		}
		assertTrue(published.stream().allMatch(p -> p < 100));
		for (int i = 1; i < published.size(); i++) { assertTrue(published.get(i) > published.get(i - 1)); }
		assertEquals(Integer.valueOf(99), published.get(published.size() - 1));
		progress.complete();
		assertEquals(Integer.valueOf(100), published.get(published.size() - 1));
	}
}
