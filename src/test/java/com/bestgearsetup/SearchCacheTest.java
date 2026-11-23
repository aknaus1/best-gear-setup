package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class SearchCacheTest
{
	@Test
	public void leastRecentlyUsedEntryIsEvictedFirst()
	{
		SearchCache<String, Integer> cache = new SearchCache<>(2);
		long epoch = cache.epoch();
		cache.put("a", 1, epoch);
		cache.put("b", 2, epoch);
		cache.put("c", 3, epoch);
		assertNull("the oldest entry made room", cache.get("a", epoch));
		assertEquals(Integer.valueOf(2), cache.get("b", epoch));
		assertEquals(Integer.valueOf(3), cache.get("c", epoch));
	}

	@Test
	public void readingAnEntryKeepsItOverOlderOnes()
	{
		SearchCache<String, Integer> cache = new SearchCache<>(2);
		long epoch = cache.epoch();
		cache.put("a", 1, epoch);
		cache.put("b", 2, epoch);
		cache.get("a", epoch);
		cache.put("c", 3, epoch);
		assertEquals(Integer.valueOf(1), cache.get("a", epoch));
		assertNull("b was the least recently used", cache.get("b", epoch));
		assertEquals(Integer.valueOf(3), cache.get("c", epoch));
	}

	@Test
	public void sizeNeverExceedsCapacity()
	{
		SearchCache<Integer, Integer> cache = new SearchCache<>(3);
		long epoch = cache.epoch();
		for (int i = 0; i < 10; i++)
		{
			cache.put(i, i, epoch);
		}
		int kept = 0;
		for (int i = 0; i < 10; i++)
		{
			kept += cache.get(i, epoch) != null ? 1 : 0;
		}
		assertEquals(3, kept);
		for (int i = 7; i < 10; i++)
		{
			assertEquals(Integer.valueOf(i), cache.get(i, epoch));
		}
	}

	@Test
	public void workStartedBeforeAClearNeitherStoresNorReads()
	{
		SearchCache<String, Integer> cache = new SearchCache<>(4);
		long before = cache.epoch();
		cache.put("kept", 1, before);
		cache.clear();
		assertNull("cleared", cache.get("kept", cache.epoch()));
		cache.put("late", 2, before);
		assertNull("a search from before the clear is not stored", cache.get("late", cache.epoch()));
		long after = cache.epoch();
		cache.put("new", 3, after);
		assertEquals(Integer.valueOf(3), cache.get("new", after));
		assertNull("nor can it read what newer searches stored", cache.get("new", before));
	}
}
