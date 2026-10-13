package com.bestgearsetup;

import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.WeaponRules;
import com.bestgearsetup.data.GearItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.function.IntConsumer;
import java.util.function.IntToLongFunction;
import java.util.function.ToLongFunction;

/** Bounded beam search over encounter candidates, enforcing the shared kit's limits before publication. */
final class RaidPlanner
{
	private static final int BEAM = 128;
	private RaidPlanner() { }

	static RaidPlan select(RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters, List<SearchResults> results,
		int limit, long budget, IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount,
		IntToLongFunction quantities, BooleanSupplier cancelled)
	{
		return select(type, spellbook, encounters, results, limit, budget, owns, price, ammoCount, quantities, cancelled, percent -> { });
	}

	static RaidPlan select(RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters, List<SearchResults> results,
		int limit, long budget, IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount,
		IntToLongFunction quantities, BooleanSupplier cancelled, IntConsumer progress)
	{
		return select(type, spellbook, encounters, results, limit, budget, owns, price, ammoCount, quantities, cancelled,
			progress, Double.POSITIVE_INFINITY);
	}

	/**
	 * Only plans faster than {@code bound} seconds are of interest; returns null when none is found. Every
	 * remaining encounter takes at least its fastest candidate's time, so partial kits that cannot finish under
	 * the bound are dropped before their (much more expensive) inventory checks.
	 */
	static RaidPlan select(RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters, List<SearchResults> results,
		int limit, long budget, IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount,
		IntToLongFunction quantities, BooleanSupplier cancelled, IntConsumer progress, double bound)
	{
		if (cancelled.getAsBoolean()) { return null; }
		double[] remaining = new double[results.size() + 1];
		for (int index = results.size() - 1; index >= 0; index--)
		{
			SearchResults result = results.get(index);
			double fastest = Double.POSITIVE_INFINITY;
			for (List<SetupResult> candidates : result.hasBlockingLock() ? Collections.<List<SetupResult>>emptyList() : result.getByType().values())
			{
				for (SetupResult setup : candidates)
				{
					if (setup.getDps().getPrimaryDps() > 0) { fastest = Math.min(fastest, encounters.get(index).estimatedSeconds(result, setup)); }
				}
			}
			if (fastest == Double.POSITIVE_INFINITY) { return null; }
			remaining[index] = remaining[index + 1] + fastest;
		}
		// Rounding slack: never drop a kit that could tie or beat the bound.
		double cutoff = bound * (1 + 1e-9);
		RaidPlan.Memo memo = new RaidPlan.Memo();
		Map<Integer, Long> prices = new HashMap<>();
		ToLongFunction<GearItem> itemCost = item -> prices.computeIfAbsent(item.getId(),
			id -> itemCost(item, owns, price, ammoCount, quantities));
		List<State> beam = Collections.singletonList(new State());
		for (int index = 0; index < results.size(); index++)
		{
			if (cancelled.getAsBoolean()) { return null; }
			List<State> next = new ArrayList<>();
			Map<Set<Integer>, State> distinct = new LinkedHashMap<>();
			SearchResults result = results.get(index);
			List<SearchResults> context = results.subList(0, index + 1);
			for (State previous : beam)
			{
				for (List<SetupResult> candidates : result.getByType().values())
				{
					for (SetupResult setup : candidates)
					{
						if (setup.getDps().getPrimaryDps() <= 0 || result.hasBlockingLock()) { continue; }
						double seconds = encounters.get(index).estimatedSeconds(result, setup);
						if (previous.seconds + seconds + remaining[index + 1] >= cutoff) { continue; }
						State state = new State(previous, setup, seconds, memo, itemCost);
						if (state.cost < 0 && budget != Long.MAX_VALUE || state.cost > budget) { continue; }
						List<SetupResult> filled = RaidPlan.fillAmmo(state.setups, context, memo);
						List<RaidPlan.InventoryItem> inventory = RaidPlan.filledInventory(filled, context, owns, memo);
						if (inventory.stream().filter(RaidPlan.InventoryItem::isEquipment).count() > limit
							|| RaidPlan.filledMaximumInventory(filled, inventory, memo) > 28) { continue; }
						Set<Integer> key = new HashSet<>(state.gear.keySet());
						// Worn items change capacity; keep different starting setups separate.
						for (GearItem worn : memo.gear(filled.get(0)).values())
						{ key.add(-worn.getId()); }
						// Spells/support and potion choices change inventory capacity too.
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
			progress.accept((index + 1) * 100 / results.size());
		}
		State best = beam.get(0);
		List<SetupResult> filled = RaidPlan.fillAmmo(best.setups, results);
		return new RaidPlan(type, spellbook, Collections.unmodifiableList(new ArrayList<>(encounters)),
			Collections.unmodifiableList(new ArrayList<>(results)), Collections.unmodifiableList(filled),
			Collections.unmodifiableList(new ArrayList<>(best.gear.values())),
			Collections.unmodifiableList(RaidPlan.inventory(filled, results, owns)), limit, best.cost);
	}

	/** Price each unique piece once, including loaded darts; unknown purchases cannot fit a finite budget. */
	static long cost(List<SetupResult> setups, IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount,
		IntToLongFunction quantities)
	{
		RaidPlan.Memo memo = new RaidPlan.Memo();
		Map<Integer, GearItem> all = new LinkedHashMap<>();
		setups.forEach(setup -> all.putAll(priced(setup, memo)));
		long total = 0;
		for (GearItem item : all.values())
		{
			long value = itemCost(item, owns, price, ammoCount, quantities);
			if (value < 0) { return -1; }
			total += value;
		}
		return total;
	}

	/** A setup's gear plus loaded darts, which are bought with the weapon's ammunition count. */
	private static Map<Integer, GearItem> priced(SetupResult setup, RaidPlan.Memo memo)
	{
		Map<Integer, GearItem> items = new LinkedHashMap<>(memo.gear(setup));
		RaidPlan.put(items, setup.getLoadout().getLoadedAmmo());
		if (setup.getSpecial() != null) { RaidPlan.put(items, setup.getSpecial().getLoadedAmmo()); }
		return items;
	}

	/** Purchase cost of one unique piece: 0 when held, -1 when it must be bought but has no price. */
	private static long itemCost(GearItem item, IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount,
		IntToLongFunction quantities)
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
		if (count == 0) { return 0; }
		long value = price.applyAsLong(item);
		return value < 0 ? -1 : value * count;
	}

	private static final class State
	{
		final List<SetupResult> setups = new ArrayList<>();
		final Map<Integer, GearItem> gear = new LinkedHashMap<>();
		/** Items already priced; the kit's cost only grows by pieces new to it. */
		final Set<Integer> priced = new HashSet<>();
		double seconds;
		/** Shared kit's purchase cost, or -1 once any unpriced piece must be bought. */
		long cost;
		State() { }
		State(State previous, SetupResult setup, double encounterSeconds, RaidPlan.Memo memo, ToLongFunction<GearItem> itemCost)
		{
			setups.addAll(previous.setups);
			setups.add(setup);
			gear.putAll(previous.gear);
			gear.putAll(memo.gear(setup));
			seconds = previous.seconds + encounterSeconds;
			priced.addAll(previous.priced);
			cost = previous.cost;
			for (GearItem item : priced(setup, memo).values())
			{
				if (priced.add(item.getId()) && cost >= 0)
				{
					long value = itemCost.applyAsLong(item);
					cost = value < 0 ? -1 : cost + value;
				}
			}
		}
	}
}
