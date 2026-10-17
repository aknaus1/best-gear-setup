package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class SearchCacheTest
{
	@Test
	public void evictsTheLeastRecentlyUsedEntry()
	{
		SearchCache<String, String> cache = new SearchCache<>(2);
		long epoch = cache.epoch();
		cache.put("a", "first", epoch);
		cache.put("b", "second", epoch);
		assertEquals("first", cache.get("a", epoch));
		cache.put("c", "third", epoch);
		assertNull(cache.get("b", epoch));
		assertEquals("first", cache.get("a", epoch));
		assertEquals("third", cache.get("c", epoch));
	}

	@Test
	public void oldWorkCannotReadOrRepopulateAClearedCache()
	{
		SearchCache<String, String> cache = new SearchCache<>(2);
		long old = cache.epoch();
		cache.put("a", "old account", old);
		cache.clear();
		assertNull(cache.get("a", cache.epoch()));
		cache.put("a", "stale work", old);
		assertNull(cache.get("a", cache.epoch()));
		cache.put("a", "new account", cache.epoch());
		assertNull(cache.get("a", old));
		assertEquals("new account", cache.get("a", cache.epoch()));
	}
}
