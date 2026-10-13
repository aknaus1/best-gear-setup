package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/** Reduce switches by the time they cost across the raid, using only gear from the original best setups. */
final class RaidConsolidator
{
	@FunctionalInterface
	interface Search
	{
		SearchResults run(int encounter, Set<Integer> excluded, int pass) throws IOException;
	}

	private RaidConsolidator() { }

	static RaidPlan select(GameData data, RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters,
		List<SearchResults> original, Set<Integer> locked, int limit, long budget, IntPredicate owns,
		ToLongFunction<GearItem> price, int ammoCount, IntToLongFunction quantities, BooleanSupplier cancelled,
		Search search) throws IOException
	{
		return select(data, type, spellbook, encounters, original, locked, limit, budget, owns, price, ammoCount, quantities,
			cancelled, Double.POSITIVE_INFINITY, search);
	}

	/** As above, but only plans faster than {@code bound} seconds (e.g. an already-found kit) are returned. */
	static RaidPlan select(GameData data, RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters,
		List<SearchResults> original, Set<Integer> locked, int limit, long budget, IntPredicate owns,
		ToLongFunction<GearItem> price, int ammoCount, IntToLongFunction quantities, BooleanSupplier cancelled,
		double bound, Search search) throws IOException
	{
		// Restricted searches from earlier passes, per encounter. A search's best setup stays optimal under any
		// larger exclusion set it does not touch, so later passes only re-search encounters the last removal affected.
		List<List<Searched>> searched = new ArrayList<>();
		for (int index = 0; index < original.size(); index++) { searched.add(new ArrayList<>()); }
		Set<Integer> allowed = new HashSet<>(locked);
		for (SetupResult setup : best(original)) { allowed.addAll(gear(setup)); }
		Set<Integer> excluded = new HashSet<>();
		for (Slot slot : Slot.values())
		{
			for (GearItem item : data.getItems(slot))
			{
				if (!allowed.contains(item.getId()) && !RaidPlan.providedInRaid(item)) { excluded.add(item.getId()); }
			}
		}
		List<SearchResults> current = original.stream().map(result -> filter(result, excluded)).collect(Collectors.toList());
		// Items consolidation removed on top of that base exclusion: few, so cached searches compare cheaply.
		Set<Integer> dropped = new HashSet<>();
		int pass = 1;
		RaidPlan feasible = null;
		double feasibleSeconds = bound;
		while (!cancelled.getAsBoolean())
		{
			List<SetupResult> baseline = best(current);
			double baselineSeconds = seconds(baseline, current, encounters);
			double baselineExcess = excess(baseline, current, limit, budget, owns, price, ammoCount, quantities);
			if (baselineExcess <= 0)
			{
				RaidPlan fitted = RaidPlanner.select(type, spellbook, encounters, current, limit, budget, owns, price,
					ammoCount, quantities, cancelled, percent -> { }, feasibleSeconds);
				return fitted != null && seconds(fitted.getSetups(), fitted.getResults(), encounters) < feasibleSeconds ? fitted : feasible;
			}
			List<Integer> removable = baseline.stream().flatMap(setup -> gear(setup).stream()).distinct()
				.filter(id -> !locked.contains(id) && !RaidPlan.providedInRaid(data.findItem(id))).sorted().collect(Collectors.toList());
			List<SearchResults> chosen = null;
			int removed = 0;
			double smallestLoss = Double.POSITIVE_INFINITY;
			List<Trial> trials = new ArrayList<>();
			for (int id : removable)
			{
				if (cancelled.getAsBoolean()) { return null; }
				Set<Integer> trialExcluded = new HashSet<>(excluded); trialExcluded.add(id);
				Set<Integer> trialDropped = new HashSet<>(dropped); trialDropped.add(id);
				List<SearchResults> trial = new ArrayList<>();
				for (int index = 0; index < current.size(); index++)
				{
					SearchResults result = current.get(index);
					// An unaffected best setup already gives the same optimum under this restriction.
					SearchResults replacement = gear(baseline.get(index)).contains(id)
						? restricted(searched.get(index), index, trialExcluded, trialDropped, pass, search) : filter(result, trialExcluded);
					if (replacement == null || replacement.isEmpty() || replacement.hasBlockingLock()) { break; }
					trial.add(replacement);
				}
				if (trial.size() != current.size()) { continue; }
				List<SetupResult> setups = best(trial);
				double trialSeconds = seconds(setups, trial, encounters);
				trials.add(new Trial(trial, trialSeconds));
				double saved = baselineExcess - excess(setups, trial, limit, budget, owns, price, ammoCount, quantities);
				if (saved <= 0) { continue; }
				double loss = (trialSeconds - baselineSeconds) / saved;
				if (loss < smallestLoss)
				{ smallestLoss = loss; chosen = trial; removed = id; }
			}
			// No kit beats its trial's independently best setups, so plan the most promising trials first: the
			// best feasible time then tightens early and later trials stop at the planner's lower bound.
			trials.sort(Comparator.comparingDouble(trial -> trial.bestSeconds));
			for (Trial trial : trials)
			{
				if (cancelled.getAsBoolean()) { return null; }
				if (trial.bestSeconds >= feasibleSeconds) { break; }
				RaidPlan plan = RaidPlanner.select(type, spellbook, encounters, trial.results, limit, budget, owns, price,
					ammoCount, quantities, cancelled, percent -> { }, feasibleSeconds);
				double planSeconds = plan == null ? Double.POSITIVE_INFINITY : seconds(plan.getSetups(), trial.results, encounters);
				if (planSeconds < feasibleSeconds) { feasible = plan; feasibleSeconds = planSeconds; }
			}
			// Keep any feasible fallback, but finish comparing armour sharing before accepting a slow style switch.
			if (chosen == null) { return feasible; }
			excluded.add(removed);
			dropped.add(removed);
			current = chosen;
			pass++;
		}
		return null;
	}

	/**
	 * Reuse an earlier search whose best setup avoids every excluded item; otherwise search and remember it. Every
	 * search shares the base exclusion, so searches are told apart by the items consolidation {@code dropped}.
	 */
	private static SearchResults restricted(List<Searched> earlier, int index, Set<Integer> excluded, Set<Integer> dropped,
		int pass, Search search) throws IOException
	{
		Searched reusable = null;
		for (Searched entry : earlier)
		{
			// Prefer the most restricted match: its alternatives were found under the closest exclusions.
			if (dropped.containsAll(entry.dropped) && Collections.disjoint(entry.bestGear, dropped)
				&& (reusable == null || entry.dropped.size() > reusable.dropped.size()))
			{ reusable = entry; }
		}
		if (reusable != null) { return filter(reusable.results, excluded); }
		SearchResults found = search.run(index, excluded, pass);
		if (found != null && !found.isEmpty() && !found.hasBlockingLock())
		{
			earlier.add(new Searched(new HashSet<>(dropped), found, gear(best(Collections.singletonList(found)).get(0))));
		}
		return found;
	}

	private static final class Searched
	{
		final Set<Integer> dropped;
		final SearchResults results;
		final Set<Integer> bestGear;
		Searched(Set<Integer> dropped, SearchResults results, Set<Integer> bestGear)
		{
			this.dropped = dropped;
			this.results = results;
			this.bestGear = bestGear;
		}
	}

	private static final class Trial
	{
		final List<SearchResults> results;
		/** Sum of each encounter's fastest setup: no shared kit from these results can be faster. */
		final double bestSeconds;
		Trial(List<SearchResults> results, double bestSeconds)
		{
			this.results = results;
			this.bestSeconds = bestSeconds;
		}
	}

	static List<SetupResult> best(List<SearchResults> results)
	{
		return results.stream().map(result -> result.getByType().values().stream().flatMap(List::stream)
			.max(Comparator.comparingDouble(setup -> setup.getDps().getPrimaryDps())).get()).collect(Collectors.toList());
	}

	private static Set<Integer> gear(SetupResult setup)
	{
		Set<Integer> ids = new HashSet<>(RaidPlan.gear(setup).keySet());
		if (setup.getLoadout().getLoadedAmmo() != null) { ids.add(setup.getLoadout().getLoadedAmmo().getId()); }
		if (setup.getSpecial() != null && setup.getSpecial().getLoadedAmmo() != null) { ids.add(setup.getSpecial().getLoadedAmmo().getId()); }
		return ids;
	}

	private static SearchResults filter(SearchResults result, Set<Integer> excluded)
	{
		Map<AttackStyle.Type, List<SetupResult>> candidates = new LinkedHashMap<>();
		result.getByType().forEach((type, setups) ->
		{
			List<SetupResult> usable = setups.stream().filter(setup -> gear(setup).stream().noneMatch(excluded::contains)).collect(Collectors.toList());
			if (!usable.isEmpty()) { candidates.put(type, usable); }
		});
		return result.withCandidates(candidates);
	}

	static double seconds(List<SetupResult> setups, List<SearchResults> results, List<RaidEncounter> encounters)
	{
		double total = 0;
		for (int i = 0; i < setups.size(); i++) { total += encounters.get(i).estimatedSeconds(results.get(i), setups.get(i)); }
		return total;
	}

	private static double excess(List<SetupResult> setups, List<SearchResults> results, int limit, long budget,
		IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount, IntToLongFunction quantities)
	{
		long equipment = RaidPlan.inventory(setups, results, owns).stream().filter(RaidPlan.InventoryItem::isEquipment).count();
		long cost = RaidPlanner.cost(setups, owns, price, ammoCount, quantities);
		if (cost < 0 && budget != Long.MAX_VALUE) { return Double.POSITIVE_INFINITY; }
		return Math.max(0, equipment - limit) + Math.max(0, RaidPlan.maximumInventory(setups, results, owns) - 28)
			+ (budget == Long.MAX_VALUE ? 0 : Math.max(0, cost - budget) / (double) Math.max(1, budget));
	}
}
