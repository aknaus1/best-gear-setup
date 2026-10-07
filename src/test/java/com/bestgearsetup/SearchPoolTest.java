package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.Test;

public class SearchPoolTest
{
	private static final List<Integer> TASKS = Arrays.asList(1, 2, 3, 4, 5);

	@Test
	public void leavesTwoLogicalProcessorsForTheClient()
	{
		assertEquals(1, SearchPool.workers(1, 5));
		assertEquals(1, SearchPool.workers(2, 5));
		assertEquals(1, SearchPool.workers(3, 5));
		assertEquals(2, SearchPool.workers(4, 5));
		assertEquals(4, SearchPool.workers(6, 5));
		assertEquals(5, SearchPool.workers(16, 5));
		assertEquals(1, SearchPool.workers(16, 1));
	}

	@Test
	public void smallMachinesSearchEveryTabOnTheCallingThreadInOrder() throws Exception
	{
		SearchPool pool = new SearchPool(3, 5);
		assertEquals(0, pool.helperCount());
		Thread caller = Thread.currentThread();
		List<Integer> seen = Collections.synchronizedList(new ArrayList<>());
		List<Integer> out = pool.run(TASKS, t ->
		{
			assertTrue(Thread.currentThread() == caller);
			seen.add(t);
			return t * 10;
		}, () -> false, () -> false);
		assertEquals(Arrays.asList(10, 20, 30, 40, 50), out);
		assertEquals(TASKS, seen);
	}

	@Test
	public void spareCoresRunTabsTogetherAndKeepTheirOrder() throws Exception
	{
		SearchPool pool = new SearchPool(8, 5);
		try
		{
			// Helpers share one name, so count distinct threads rather than names.
			Set<Thread> threads = ConcurrentHashMap.newKeySet();
			CountDownLatch together = new CountDownLatch(2);
			List<Integer> out = pool.run(TASKS, t ->
			{
				threads.add(Thread.currentThread());
				together.countDown();
				try
				{
					// The first two tabs only finish once both are running at the same time.
					together.await(5, TimeUnit.SECONDS);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
				return t * 10;
			}, () -> false, () -> false);
			assertEquals(Arrays.asList(10, 20, 30, 40, 50), out);
			assertTrue(threads.size() >= 2);
			assertTrue(threads.stream().anyMatch(t -> t.getName().equals("best-gear-setup-helper")));
		}
		finally
		{
			pool.shutdown();
		}
	}

	@Test
	public void aSlowedGameLeavesTheRemainingTabsToTheCallingThread() throws Exception
	{
		SearchPool pool = new SearchPool(8, 5);
		try
		{
			Thread caller = Thread.currentThread();
			List<Integer> out = pool.run(TASKS, t ->
			{
				assertTrue(Thread.currentThread() == caller);
				return t;
			}, () -> true, () -> false);
			assertEquals(TASKS, out);
		}
		finally
		{
			pool.shutdown();
		}
	}

	@Test
	public void cancelledSearchesReturnNothing() throws Exception
	{
		SearchPool pool = new SearchPool(8, 5);
		try
		{
			AtomicInteger ran = new AtomicInteger();
			assertNull(pool.run(TASKS, t -> ran.incrementAndGet(), () -> false, () -> true));
			assertEquals(0, ran.get());
		}
		finally
		{
			pool.shutdown();
		}
	}

	@Test
	public void aFailingTabFailsTheSearch() throws Exception
	{
		SearchPool pool = new SearchPool(8, 5);
		try
		{
			pool.run(TASKS, t ->
			{
				if (t == 3)
				{
					throw new IllegalArgumentException("tab 3");
				}
				return t;
			}, () -> false, () -> false);
			fail();
		}
		catch (IllegalArgumentException e)
		{
			assertEquals("tab 3", e.getMessage());
		}
		finally
		{
			pool.shutdown();
		}
	}

	@Test
	public void aShutDownPoolStillFinishesOnTheCallingThread() throws Exception
	{
		SearchPool pool = new SearchPool(8, 5);
		pool.shutdown();
		assertEquals(TASKS, pool.run(TASKS, t -> t, () -> false, () -> false));
	}

	@Test
	public void throttlesFromTheFirstFrameRateDropForTheRestOfTheSearch()
	{
		int[] fps = {50};
		BooleanSupplier throttle = SearchPool.fpsThrottle(() -> fps[0]);
		assertFalse(throttle.getAsBoolean());
		fps[0] = 41;
		assertFalse(throttle.getAsBoolean());
		fps[0] = 39;
		assertTrue(throttle.getAsBoolean());
		fps[0] = 50;
		assertTrue(throttle.getAsBoolean());

		fps[0] = 0;
		BooleanSupplier unknown = SearchPool.fpsThrottle(() -> fps[0]);
		assertFalse(unknown.getAsBoolean());
	}
}
