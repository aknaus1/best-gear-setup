package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.WeaponRules;
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

/** Temporary verbatim copy of the pre-optimisation planner and consolidator, for A/B benchmarking. */
final class RaidOrigTmp
{
	private static final int BEAM = 128;

	static RaidPlan plannerSelect(RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters, List<SearchResults> results,
		int limit, long budget, IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount,
		IntToLongFunction quantities, BooleanSupplier cancelled)
	{
		List<State> beam = Collections.singletonList(new State());
		for (int index = 0; index < results.size(); index++)
		{
			if (cancelled.getAsBoolean()) { return null; }
			List<State> next = new ArrayList<>();
			Map<Set<Integer>, State> distinct = new LinkedHashMap<>();
			SearchResults result = results.get(index);
			for (State previous : beam)
			{
				for (List<SetupResult> candidates : result.getByType().values())
				{
					for (SetupResult setup : candidates)
					{
						if (setup.getDps().getPrimaryDps() <= 0 || result.hasBlockingLock()) { continue; }
						State state = new State(previous, setup, encounters.get(index).estimatedSeconds(result, setup));
						List<RaidPlan.InventoryItem> inventory = RaidPlan.inventory(state.setups, results.subList(0, index + 1), owns);
						long cost = cost(state.setups, owns, price, ammoCount, quantities);
						if (RaidPlan.maximumInventory(state.setups, results.subList(0, index + 1), owns) > 28
							|| inventory.stream().filter(RaidPlan.InventoryItem::isEquipment).count() > limit
							|| cost < 0 && budget != Long.MAX_VALUE || cost > budget) { continue; }
						Set<Integer> key = new HashSet<>(state.gear.keySet());
						for (GearItem worn : RaidPlan.gear(RaidPlan.fillAmmo(state.setups, results.subList(0, index + 1)).get(0)).values())
						{ key.add(-worn.getId()); }
						inventory.forEach(item -> key.add(item.getIds().get(0)));
						State old = distinct.get(key);
						if (old == null || state.seconds < old.seconds) { distinct.put(key, state); }
					}
				}
			}
			next.addAll(distinct.values());
			next.sort(Comparator.comparingDouble(s -> s.seconds));
			beam = next.subList(0, Math.min(BEAM, next.size()));
			if (beam.isEmpty()) { return null; }
		}
		State best = beam.get(0);
		List<SetupResult> filled = RaidPlan.fillAmmo(best.setups, results);
		return new RaidPlan(type, spellbook, Collections.unmodifiableList(new ArrayList<>(encounters)),
			Collections.unmodifiableList(new ArrayList<>(results)), Collections.unmodifiableList(filled),
			Collections.unmodifiableList(new ArrayList<>(best.gear.values())),
			Collections.unmodifiableList(RaidPlan.inventory(filled, results, owns)), limit,
			cost(best.setups, owns, price, ammoCount, quantities));
	}

	static long cost(List<SetupResult> setups, IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount,
		IntToLongFunction quantities)
	{
		Map<Integer, GearItem> all = new LinkedHashMap<>();
		for (SetupResult setup : setups)
		{
			all.putAll(RaidPlan.gear(setup));
			RaidPlan.put(all, setup.getLoadout().getLoadedAmmo());
			if (setup.getSpecial() != null) { RaidPlan.put(all, setup.getSpecial().getLoadedAmmo()); }
		}
		long total = 0;
		for (GearItem item : all.values())
		{
			long count;
			if (WeaponRules.consumedPerAttack(item))
			{
				long held = Math.min(ammoCount, Math.max(0, quantities.applyAsLong(item.getId())));
				for (int id : item.getOwnershipVariants())
				{
					if (id != item.getId())
					{ held = Math.min(ammoCount, held + Math.min(ammoCount, Math.max(0, quantities.applyAsLong(id)))); }
				}
				count = Math.max(0, ammoCount - held);
			}
			else { count = owns.test(item.getId()) || item.getOwnershipVariants().stream().anyMatch(owns::test) ? 0 : 1; }
			if (count == 0) { continue; }
			long value = price.applyAsLong(item);
			if (value < 0) { return -1; }
			total += value * count;
		}
		return total;
	}

	private static final class State
	{
		final List<SetupResult> setups = new ArrayList<>();
		final Map<Integer, GearItem> gear = new LinkedHashMap<>();
		double seconds;
		State() { }
		State(State previous, SetupResult setup, double encounterSeconds)
		{
			setups.addAll(previous.setups);
			setups.add(setup);
			gear.putAll(previous.gear);
			gear.putAll(RaidPlan.gear(setup));
			seconds = previous.seconds + encounterSeconds;
		}
	}

	static RaidPlan consolidate(GameData data, RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters,
		List<SearchResults> original, Set<Integer> locked, int limit, long budget, IntPredicate owns,
		ToLongFunction<GearItem> price, int ammoCount, IntToLongFunction quantities, BooleanSupplier cancelled,
		RaidConsolidator.Search search) throws IOException
	{
		Set<Integer> allowed = new HashSet<>(locked);
		for (SetupResult setup : RaidConsolidator.best(original)) { allowed.addAll(gear(setup)); }
		Set<Integer> excluded = new HashSet<>();
		for (Slot slot : Slot.values())
		{
			for (GearItem item : data.getItems(slot))
			{
				if (!allowed.contains(item.getId()) && !RaidPlan.providedInRaid(item)) { excluded.add(item.getId()); }
			}
		}
		List<SearchResults> current = original.stream().map(result -> filter(result, excluded)).collect(Collectors.toList());
		int pass = 1;
		RaidPlan feasible = null;
		while (!cancelled.getAsBoolean())
		{
			List<SetupResult> baseline = RaidConsolidator.best(current);
			double baselineSeconds = RaidConsolidator.seconds(baseline, current, encounters);
			double baselineExcess = excess(baseline, current, limit, budget, owns, price, ammoCount, quantities);
			if (baselineExcess <= 0)
			{
				RaidPlan fitted = plannerSelect(type, spellbook, encounters, current, limit, budget, owns, price,
					ammoCount, quantities, cancelled);
				return fitted != null && (feasible == null || RaidConsolidator.seconds(fitted.getSetups(), fitted.getResults(), encounters)
					< RaidConsolidator.seconds(feasible.getSetups(), feasible.getResults(), encounters)) ? fitted : feasible;
			}
			List<Integer> removable = baseline.stream().flatMap(setup -> gear(setup).stream()).distinct()
				.filter(id -> !locked.contains(id) && !RaidPlan.providedInRaid(data.findItem(id))).sorted().collect(Collectors.toList());
			List<SearchResults> chosen = null;
			int removed = 0;
			double smallestLoss = Double.POSITIVE_INFINITY;
			for (int id : removable)
			{
				if (cancelled.getAsBoolean()) { return null; }
				Set<Integer> trialExcluded = new HashSet<>(excluded); trialExcluded.add(id);
				List<SearchResults> trial = new ArrayList<>();
				for (int index = 0; index < current.size(); index++)
				{
					SearchResults result = current.get(index);
					SearchResults replacement = gear(baseline.get(index)).contains(id)
						? search.run(index, trialExcluded, pass) : filter(result, trialExcluded);
					if (replacement == null || replacement.isEmpty() || replacement.hasBlockingLock()) { break; }
					trial.add(replacement);
				}
				if (trial.size() != current.size()) { continue; }
				RaidPlan plan = plannerSelect(type, spellbook, encounters, trial, limit, budget, owns, price,
					ammoCount, quantities, cancelled);
				if (plan != null && (feasible == null || RaidConsolidator.seconds(plan.getSetups(), trial, encounters)
					< RaidConsolidator.seconds(feasible.getSetups(), feasible.getResults(), encounters)))
				{ feasible = plan; }
				List<SetupResult> setups = RaidConsolidator.best(trial);
				double saved = baselineExcess - excess(setups, trial, limit, budget, owns, price, ammoCount, quantities);
				if (saved <= 0) { continue; }
				double loss = (RaidConsolidator.seconds(setups, trial, encounters) - baselineSeconds) / saved;
				if (loss < smallestLoss)
				{ smallestLoss = loss; chosen = trial; removed = id; }
			}
			if (chosen == null) { return feasible; }
			excluded.add(removed);
			current = chosen;
			pass++;
		}
		return null;
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

	private static double excess(List<SetupResult> setups, List<SearchResults> results, int limit, long budget,
		IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount, IntToLongFunction quantities)
	{
		long equipment = RaidPlan.inventory(setups, results, owns).stream().filter(RaidPlan.InventoryItem::isEquipment).count();
		long cost = cost(setups, owns, price, ammoCount, quantities);
		if (cost < 0 && budget != Long.MAX_VALUE) { return Double.POSITIVE_INFINITY; }
		return Math.max(0, equipment - limit) + Math.max(0, RaidPlan.maximumInventory(setups, results, owns) - 28)
			+ (budget == Long.MAX_VALUE ? 0 : Math.max(0, cost - budget) / (double) Math.max(1, budget));
	}

	private RaidOrigTmp() { }
}
