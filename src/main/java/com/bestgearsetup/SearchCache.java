package com.bestgearsetup;

import java.util.*;

/**
 * A bounded least-recently-used map, safe to share between threads. Clearing also moves the epoch on: work that
 * started before (an earlier data load, account or plugin run) can then neither read nor store entries, whenever
 * it finishes.
 */
final class SearchCache<K, V>
{
	private final Map<K, V> entries;
	private long epoch;

	SearchCache(int capacity)
	{
		// Access order: every hit makes the entry the most recent, and the least recent is dropped past capacity.
		entries = new LinkedHashMap<K, V>(16, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<K, V> eldest)
			{
				return size() > capacity;
			}
		};
	}

	/** Read when work starts, and passed back to {@link #get} and {@link #put}. */
	synchronized long epoch()
	{
		return epoch;
	}

	/** The value stored for the key, now the most recent; null if none, or if cleared since {@code started}. */
	synchronized V get(K key, long started)
	{
		return started == epoch ? entries.get(key) : null;
	}

	/** Remember a value, unless the cache was cleared since the work producing it started. */
	synchronized void put(K key, V value, long started)
	{
		if (started == epoch)
		{
			entries.put(key, value);
		}
	}

	synchronized void clear()
	{
		epoch++;
		entries.clear();
	}
}
