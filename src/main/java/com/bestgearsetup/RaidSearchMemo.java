package com.bestgearsetup;

import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One raid plan's encounter searches, kept for reuse under larger exclusions. Consolidation drops one item per
 * trial and re-searches every encounter whose setup used it, and a switch group as a whole, since its members'
 * quick switches follow each other's full setups. Searched afresh, a group costs every full setup and quick switch
 * again per trial, though most of them can't change: a full search's fastest setup stays optimal under any larger
 * exclusion that doesn't touch it (as {@link RaidConsolidator} reuses ungrouped searches, filtered), and a
 * quick-switch search none of whose switches use a newly excluded item returns the same switches. Only the
 * searches a removal does affect run again, and with them the quick switches from any base they change.
 */
final class RaidSearchMemo
{
	private final Map<List<Object>, List<Entry>> entries = new ConcurrentHashMap<>();

	/** A search's identity besides its exclusions: the encounter, the worn base of a quick switch (null for a full setup) and whether it goes without its boost. */
	static List<Object> key(int index, Loadout switchFrom, boolean unboosted)
	{
		return Arrays.asList(index, switchFrom, unboosted);
	}

	/**
	 * A remembered search standing in for one under {@code excluded}: the most restricted one searched under a subset
	 * of these exclusions that the extra exclusions leave unaffected, with any setups they rule out filtered off. Null
	 * when the search has to run.
	 */
	SearchResults find(List<Object> key, Set<Integer> excluded)
	{
		List<Entry> known = entries.get(key);
		if (known == null) { return null; }
		Entry reusable = null;
		synchronized (known)
		{
			for (Entry entry : known)
			{
				if (entry.excluded.size() <= excluded.size() && (reusable == null || entry.excluded.size() > reusable.excluded.size())
					&& Collections.disjoint(entry.gear, excluded) && excluded.containsAll(entry.excluded))
				{ reusable = entry; }
			}
		}
		if (reusable == null) { return null; }
		return reusable.excluded.size() == excluded.size() ? reusable.results
			: reusable.results.filter(setup -> Collections.disjoint(RaidPlan.requiredItems(setup).keySet(), excluded));
	}

	/** Remember a search with usable setups; an empty or blocked one is searched again when asked for. */
	void remember(List<Object> key, Set<Integer> excluded, SearchResults results, RaidEncounter encounter)
	{
		if (results == null || results.isEmpty() || results.hasBlockingLock()) { return; }
		boolean quick = key.get(1) != null;
		Set<Integer> gear = new HashSet<>();
		if (quick)
		{
			// Each switch is the best of its slot pattern, and the kit planner may need any pattern.
			results.getByType().values().forEach(setups -> setups.forEach(setup -> gear.addAll(RaidPlan.requiredItems(setup).keySet())));
		}
		else
		{
			gear.addAll(RaidPlan.requiredItems(RaidConsolidator.fastest(results, encounter)).keySet());
		}
		List<Entry> known = entries.computeIfAbsent(key, ignored -> new ArrayList<>());
		synchronized (known) { known.add(new Entry(new HashSet<>(excluded), results, gear)); }
	}

	/** A search, its exclusions and the gear whose exclusion would change its answer. */
	private static final class Entry
	{
		final Set<Integer> excluded;
		final SearchResults results;
		final Set<Integer> gear;

		Entry(Set<Integer> excluded, SearchResults results, Set<Integer> gear)
		{
			this.excluded = excluded;
			this.results = results;
			this.gear = gear;
		}
	}
}
