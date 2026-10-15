package com.bestgearsetup;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleConsumer;
import java.util.function.LongSupplier;

/**
 * Combines the progress of a search's result tabs, which may run on several threads, into one percentage.
 * Each tab counts equally. A new percentage is published at most once per {@link #INTERVAL_NANOS}, and
 * never one lower than already published, so the panel is not flooded with Swing updates.
 */
final class SearchProgress
{
	/** Shortest gap between two published updates. */
	static final long INTERVAL_NANOS = 100_000_000L;

	/** Per-tab progress in hundredths of a percent, for a smooth average without floating-point atomics. */
	private final AtomicIntegerArray tabs;
	private final AtomicInteger published = new AtomicInteger(-1);
	private final AtomicLong lastPublished;
	private final DoubleConsumer publish;
	private final LongSupplier clock;

	/**
	 * @param tabs    number of result tabs searched
	 * @param publish receives the fractional overall percentage (0 to 100); may be called from any search thread
	 */
	SearchProgress(int tabs, DoubleConsumer publish)
	{
		this(tabs, publish, System::nanoTime);
	}

	SearchProgress(int tabs, DoubleConsumer publish, LongSupplier clock)
	{
		this.tabs = new AtomicIntegerArray(Math.max(1, tabs));
		this.publish = publish;
		this.clock = clock;
		this.lastPublished = new AtomicLong(clock.getAsLong() - INTERVAL_NANOS);
	}

	/** Record one tab's fraction done (0 to 1). */
	void update(int tab, double fraction)
	{
		if (!Double.isFinite(fraction)) { return; }
		int value = (int) Math.round(Math.max(0, Math.min(1, fraction)) * 10_000);
		int previous;
		do { previous = tabs.get(tab); }
		while (value > previous && !tabs.compareAndSet(tab, previous, value));
		long now = clock.getAsLong();
		long last = lastPublished.get();
		if (now - last >= INTERVAL_NANOS && lastPublished.compareAndSet(last, now))
		{
			offer(Math.min(9999, (int) Math.floor(percent() * 100)));
		}
	}

	/** Publish 100%, whatever the throttle; the search is finishing up. */
	void complete()
	{
		offer(10_000);
	}

	double percent()
	{
		long sum = 0;
		for (int i = 0; i < tabs.length(); i++)
		{
			sum += tabs.get(i);
		}
		return (double) sum / tabs.length() / 100;
	}

	private synchronized void offer(int percent)
	{
		int previous = published.getAndAccumulate(percent, Math::max);
		if (percent > previous)
		{
			publish.accept(percent / 100.0);
		}
	}
}
