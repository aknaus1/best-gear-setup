package com.bestgearsetup;

import java.util.LinkedHashMap;
import java.util.Map;

/** Memory LRU with an optional profile store. Epochs exclude work started before account changes or shutdown. */
final class SearchCache<K, V>
{
	private final int capacity;
	private final Map<K, V> entries = new LinkedHashMap<>(16, 0.75f, true);
	private long epoch;
	private ProfileSearchCache<V> profile;

	SearchCache(int capacity)
	{
		if (capacity < 1) { throw new IllegalArgumentException("Cache capacity must be positive"); }
		this.capacity = capacity;
	}

	synchronized long epoch()
	{
		return epoch;
	}

	V get(K key, long started)
	{
		ProfileSearchCache<V> saved;
		synchronized (this)
		{
			if (started != epoch) { return null; }
			V hit = entries.get(key);
			if (hit != null) { return hit; }
			saved = profile;
		}
		V restored = saved == null || !(key instanceof SearchInputs) ? null
			: saved.read(((SearchInputs) key).persistentKey(), ((SearchInputs) key).catalogue());
		synchronized (this)
		{
			if (started != epoch || saved != profile) { return null; }
			if (restored != null) { remember(key, restored); }
			return restored;
		}
	}

	void put(K key, V value, long started)
	{
		ProfileSearchCache<V> saved;
		synchronized (this)
		{
			if (started != epoch || value == null) { return; }
			remember(key, value);
			saved = profile;
		}
		if (saved != null && key instanceof SearchInputs)
		{
			// Encode on this worker; the profile write itself runs on the store's writer (the client thread).
			ProfileSearchCache.Record record = saved.prepare(((SearchInputs) key).persistentKey(), value);
			if (record != null) { saved.write(() -> { if (owns(saved, started)) { saved.save(record); } }); }
		}
	}

	/**
	 * Whether work started at this epoch may still write the given store. Writers run on the client thread, where
	 * account changes also replace the store, and the store re-checks the account itself before every write. The
	 * lock is not held while writing: ConfigChanged handlers in other plugins may wait on the Swing thread.
	 */
	private synchronized boolean owns(ProfileSearchCache<V> saved, long started)
	{
		return started == epoch && saved == profile;
	}

	private void remember(K key, V value)
	{
		entries.put(key, value);
		while (entries.size() > capacity)
		{
			entries.remove(entries.keySet().iterator().next());
		}
	}

	synchronized void profile(ProfileSearchCache<V> profile)
	{
		clear();
		this.profile = profile;
		if (profile != null)
		{
			long attached = epoch;
			profile.write(() -> { if (owns(profile, attached)) { profile.prune(); } });
		}
	}

	synchronized void clear()
	{
		epoch++;
		entries.clear();
	}
}
