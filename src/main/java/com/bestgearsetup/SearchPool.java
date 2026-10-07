package com.bestgearsetup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Runs a search's independent result tabs on spare cores. Two logical processors are always left for the
 * game client and the system, so machines with three or fewer search on the one thread exactly as before.
 * Helper threads run at minimum priority and stop taking tabs as soon as the game slows down; the calling
 * thread always finishes whatever is left.
 */
final class SearchPool
{
	/** Logical processors kept for the client's own threads and the rest of the system. */
	static final int RESERVED_PROCESSORS = 2;
	/** Helpers stop once the game renders below this fraction of the frame rate it had when the search began. */
	static final double FPS_DROP = 0.8;

	private final ExecutorService helpers;
	private final int helperCount;

	/**
	 * @param processors logical processors available to the client
	 * @param maxTasks   the most tasks one call can run (the number of result tabs)
	 */
	SearchPool(int processors, int maxTasks)
	{
		helperCount = workers(processors, maxTasks) - 1;
		helpers = helperCount <= 0 ? null : Executors.newFixedThreadPool(helperCount, r ->
		{
			Thread t = new Thread(r, "best-gear-setup-helper");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			return t;
		});
	}

	/** Threads one search may use, including the calling thread. */
	static int workers(int processors, int tasks)
	{
		return Math.max(1, Math.min(tasks, processors - RESERVED_PROCESSORS));
	}

	int helperCount()
	{
		return helperCount;
	}

	/**
	 * Results in task order. Returns null if cancelled.
	 *
	 * @param throttled checked by helpers before each task; once true they stop and the caller does the rest
	 * @param cancelled stops every thread before its next task
	 */
	<T, R> List<R> run(List<T> tasks, Function<T, R> work, BooleanSupplier throttled, BooleanSupplier cancelled)
		throws InterruptedException
	{
		Object[] results = new Object[tasks.size()];
		AtomicInteger next = new AtomicInteger();
		List<Future<?>> started = new ArrayList<>();
		int extra = helpers == null ? 0 : Math.min(helperCount, tasks.size() - 1);
		try
		{
			for (int i = 0; i < extra; i++)
			{
				started.add(helpers.submit(() -> drain(tasks, work, results, next, throttled, cancelled)));
			}
		}
		catch (RejectedExecutionException e)
		{
			// The plugin is shutting down; the calling thread runs what no helper took.
		}
		try
		{
			drain(tasks, work, results, next, () -> false, cancelled);
			for (Future<?> f : started)
			{
				f.get();
			}
		}
		catch (ExecutionException e)
		{
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException)
			{
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error)
			{
				throw (Error) cause;
			}
			throw new IllegalStateException(cause);
		}
		catch (CancellationException e)
		{
			return null;
		}
		finally
		{
			// Interrupted or failed: stop helpers mid-task rather than finishing a stale search.
			for (Future<?> f : started)
			{
				f.cancel(true);
			}
		}
		if (cancelled.getAsBoolean())
		{
			return null;
		}
		@SuppressWarnings("unchecked")
		List<R> out = (List<R>) Arrays.asList(results);
		return out;
	}

	private static <T, R> void drain(List<T> tasks, Function<T, R> work, Object[] results, AtomicInteger next,
		BooleanSupplier throttled, BooleanSupplier cancelled)
	{
		while (!cancelled.getAsBoolean() && !throttled.getAsBoolean())
		{
			int i = next.getAndIncrement();
			if (i >= tasks.size())
			{
				return;
			}
			results[i] = work.apply(tasks.get(i));
		}
	}

	/**
	 * Throttle for one search: true from the first time the frame rate falls below {@link #FPS_DROP} of its
	 * value now, and stays true for the rest of the search. Never throttles when the rate is unknown (zero).
	 */
	static BooleanSupplier fpsThrottle(IntSupplier fps)
	{
		int baseline = fps.getAsInt();
		AtomicBoolean slowed = new AtomicBoolean();
		return () ->
		{
			if (!slowed.get() && baseline > 0 && fps.getAsInt() < baseline * FPS_DROP)
			{
				slowed.set(true);
			}
			return slowed.get();
		};
	}

	void shutdown()
	{
		if (helpers != null)
		{
			helpers.shutdownNow();
		}
	}
}
