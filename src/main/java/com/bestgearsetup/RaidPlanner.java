package com.bestgearsetup;

import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SpecialRotation;
import com.bestgearsetup.calc.WeaponRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.DoubleConsumer;
import java.util.function.IntToLongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/** Bounded beam search over encounter candidates, enforcing the shared kit's limits before publication. */
final class RaidPlanner
{
	private static final int BEAM = 128;
	/** Kits left with spec energy within this many percent of each other count as the same kit. */
	private static final double ENERGY_STEP = 10;
	/** Candidates compared between progress reports, less one (a power of two less one, for masking). */
	private static final int PROGRESS_STEP = 255;
	private RaidPlanner() { }

	static RaidPlan select(RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters, List<SearchResults> results,
		int limit, long budget, IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount,
		IntToLongFunction quantities, BooleanSupplier cancelled)
	{
		return select(type, spellbook, encounters, results, limit, budget, owns, price, ammoCount, quantities, cancelled, percent -> { });
	}

	static RaidPlan select(RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters, List<SearchResults> results,
		int limit, long budget, IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount,
		IntToLongFunction quantities, BooleanSupplier cancelled, DoubleConsumer progress)
	{
		return select(type, spellbook, encounters, results, limit, budget, owns, price, ammoCount, quantities, cancelled,
			progress, Double.POSITIVE_INFINITY);
	}

	/**
	 * Only plans faster than {@code bound} seconds are of interest; returns null when none is found. The kit may be
	 * worn in for any encounter of the raid's first room ({@link RaidEncounter#starts}): each is planned as the start
	 * in turn, its encounter searched first so every partial kit already knows what it wears in. A later start must
	 * be strictly faster, so ties keep the earliest.
	 */
	static RaidPlan select(RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters, List<SearchResults> results,
		int limit, long budget, IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount,
		IntToLongFunction quantities, BooleanSupplier cancelled, DoubleConsumer progress, double bound)
	{
		// A room without any setup can never be part of a complete kit.
		if (cancelled.getAsBoolean() || encounters.isEmpty() || results.stream().anyMatch(SearchResults::isEmpty)) { return null; }
		// Plan first from the start that suits the encounters' fastest setups, so the bound tightens early.
		int[] starts = RaidEncounter.starts(encounters);
		int likely = RaidPlan.bestStart(RaidConsolidator.best(results, encounters), results, starts, new RaidPlan.Memo());
		int[] ordered = java.util.stream.IntStream.concat(java.util.stream.IntStream.of(likely),
			java.util.Arrays.stream(starts).filter(start -> start != likely)).toArray();
		return select(type, spellbook, encounters, results, limit, budget, owns, price, ammoCount, quantities, cancelled,
			progress, bound, ordered);
	}

	/** As above, trying only these starting encounters, in this order. */
	static RaidPlan select(RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters, List<SearchResults> results,
		int limit, long budget, IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount,
		IntToLongFunction quantities, BooleanSupplier cancelled, DoubleConsumer progress, double bound, int[] starts)
	{
		RaidPlan best = null;
		double fastest = bound;
		for (int run = 0; run < starts.length; run++)
		{
			if (cancelled.getAsBoolean()) { return null; }
			int start = starts[run];
			List<Integer> order = new ArrayList<>();
			order.add(start);
			for (int index = 0; index < encounters.size(); index++) { if (index != start) { order.add(index); } }
			int done = run;
			State found = beam(order.stream().map(encounters::get).collect(Collectors.toList()),
				order.stream().map(results::get).collect(Collectors.toList()), limit, budget, owns, price, ammoCount,
				quantities, cancelled, percent -> progress.accept((done * 100 + percent) / starts.length), fastest, start);
			progress.accept((run + 1) * 100.0 / starts.length);
			if (found == null) { continue; }
			List<SetupResult> setups = new ArrayList<>(Collections.<SetupResult>nCopies(encounters.size(), null));
			for (int index = 0; index < order.size(); index++) { setups.set(order.get(index), found.setups.get(index)); }
			// The caller's bound keeps the beam's rounding slack; another start must be strictly faster.
			double seconds = RaidConsolidator.seconds(setups, results, encounters);
			if (seconds >= (best == null ? bound * (1 + 1e-9) : fastest)) { continue; }
			fastest = seconds;
			long cost = budget == Long.MAX_VALUE ? cost(setups, owns, price, ammoCount, quantities) : found.cost;
			RaidPlan.Memo memo = new RaidPlan.Memo();
			Map<Integer, GearItem> gear = new LinkedHashMap<>();
			setups.forEach(setup -> gear.putAll(memo.gear(setup)));
			List<SetupResult> filled = RaidPlan.fill(setups, results, memo, start);
			List<RaidPlan.InventoryItem> carried = RaidBoostEstimator.replace(RaidPlan.inventory(setups, results, owns, start),
				encounters, results, filled);
			best = new RaidPlan(type, spellbook, Collections.unmodifiableList(new ArrayList<>(encounters)),
				Collections.unmodifiableList(new ArrayList<>(results)), Collections.unmodifiableList(filled),
				Collections.unmodifiableList(new ArrayList<>(gear.values())),
				Collections.unmodifiableList(carried), limit, cost, encounters.get(start))
				.withRaidNotes(Collections.singletonList(RaidBoostEstimator.ASSUMPTIONS),
					carried.stream().anyMatch(item -> item.getEstimatedDoses() > 0)
						? Collections.singletonList(RaidBoostEstimator.OWNERSHIP_WARNING) : Collections.emptyList());
		}
		return cancelled.getAsBoolean() ? null : best;
	}

	/**
	 * The fastest kit, worn in for the first encounter's setup, faster than {@code bound} seconds; null when none is
	 * found. Every remaining encounter takes at least its fastest candidate's time, so partial kits that cannot
	 * finish under the bound are dropped before their (much more expensive) inventory checks. The encounters are in
	 * planning order: the raid's, with the starting encounter moved to the front from {@code start}. Spec energy
	 * still follows the raid's order.
	 */
	private static State beam(List<RaidEncounter> encounters, List<SearchResults> results, int limit, long budget,
		IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount, IntToLongFunction quantities,
		BooleanSupplier cancelled, DoubleConsumer progress, double bound, int start)
	{
		if (cancelled.getAsBoolean()) { return null; }
		// Planning positions in the order the fights happen: those before the start, the start, then the rest.
		int[] fought = new int[results.size()];
		for (int index = 0; index < fought.length; index++) { fought[index] = index < start ? index + 1 : index == start ? 0 : index; }
		double[] remaining = new double[results.size() + 1];
		double[] fastestSeconds = new double[results.size()];
		// Each usable setup's time, computed once per encounter: it doesn't depend on the kit it joins.
		List<Map<SetupResult, Double>> secondsOf = new ArrayList<>(Collections.<Map<SetupResult, Double>>nCopies(results.size(), null));
		for (int index = results.size() - 1; index >= 0; index--)
		{
			SearchResults result = results.get(index);
			RaidEncounter encounter = encounters.get(index);
			Map<SetupResult, Double> seconds = new IdentityHashMap<>();
			secondsOf.set(index, seconds);
			double fastest = Double.POSITIVE_INFINITY;
			for (List<SetupResult> candidates : result.hasBlockingLock() ? Collections.<List<SetupResult>>emptyList() : result.getByType().values())
			{
				for (SetupResult setup : candidates)
				{
					if (setup.getDps().getPrimaryDps() > 0)
					{ fastest = Math.min(fastest, seconds.computeIfAbsent(setup, s -> encounter.estimatedSeconds(result, s))); }
				}
			}
			if (fastest == Double.POSITIVE_INFINITY) { return null; }
			fastestSeconds[index] = fastest;
			remaining[index] = remaining[index + 1] + fastest;
			progress.accept(10.0 * (results.size() - index) / results.size());
		}
		// Rounding slack: never drop a kit that could tie or beat the bound.
		double cutoff = bound * (1 + 1e-9);
		RaidPlan.Memo memo = new RaidPlan.Memo();
		// Each encounter's usable setups, fastest first, for the look-ahead in fastestFinish().
		List<List<Option>> options = new ArrayList<>();
		for (int index = 0; index < results.size(); index++)
		{
			Map<SetupResult, Double> seconds = secondsOf.get(index);
			options.add(results.get(index).getByType().values().stream().flatMap(List::stream).filter(setup -> setup.getDps().getPrimaryDps() > 0)
				.map(setup -> new Option(memo.gear(setup).keySet(), seconds.get(setup)))
				.sorted(Comparator.comparingDouble(option -> option.seconds)).collect(Collectors.toList()));
		}
		Map<Integer, Long> prices = new HashMap<>();
		ToLongFunction<GearItem> itemCost = item -> prices.computeIfAbsent(item.getId(),
			id -> itemCost(item, owns, price, ammoCount, quantities));
		Map<SetupResult, Collection<GearItem>> pricedItems = new IdentityHashMap<>();
		// Without a budget the cost is only shown, so only the chosen kit is priced.
		Function<SetupResult, Collection<GearItem>> pricing = budget == Long.MAX_VALUE ? null
			: setup -> pricedItems.computeIfAbsent(setup, s -> RaidPlan.requiredItems(s).values());
		List<State> beam = Collections.singletonList(new State());
		// One switch group member must wear a full setup the others quick-switch from; checked as each member joins.
		List<int[]> groups = RaidEncounter.switchGroups(encounters);
		RaidSwitches switches = new RaidSwitches(encounters, results);
		long found = 0;
		for (int index = 0; index < results.size(); index++)
		{
			if (cancelled.getAsBoolean()) { return null; }
			List<State> next = new ArrayList<>();
			Map<List<Object>, State> distinct = new HashMap<>();
			SearchResults result = results.get(index);
			List<SearchResults> context = results.subList(0, index + 1);
			// Only the fastest distinct kits survive (the single best after the last encounter). Once that many are
			// found, a strictly slower candidate can neither join them nor improve one, so its inventory check is
			// skipped. Seconds when first found: each is at least its kit's current time, so the bound stays safe.
			int keep = index == results.size() - 1 ? 1 : BEAM;
			PriorityQueue<Double> kept = new PriorityQueue<>(keep, Comparator.reverseOrder());
			long combinations = (long) beam.size() * result.getByType().values().stream().mapToInt(List::size).sum();
			long compared = 0;
			boolean blocked = result.hasBlockingLock();
			// Each candidate's boosts, as the kit signature records them; the same for every kit it joins.
			Map<SetupResult, List<Integer>> boostIds = new IdentityHashMap<>();
			for (State previous : beam)
			{
				if (cancelled.getAsBoolean()) { return null; }
				// The beam is sorted, so later kits cannot do better either.
				if (kept.size() == keep && previous.seconds + fastestSeconds[index] > kept.peek()) { break; }
				for (List<SetupResult> candidates : result.getByType().values())
				{
					for (SetupResult setup : candidates)
					{
						// Reported in steps from the first: the planner compares millions of candidates in a consolidated raid.
						if ((++compared & PROGRESS_STEP) == 1)
						{ progress.accept(10 + 90.0 * (index + (double) compared / Math.max(1, combinations)) / results.size()); }
						if (setup.getDps().getPrimaryDps() <= 0 || blocked) { continue; }
						// The time bounds are the cheapest rejections, so they come first.
						double seconds = secondsOf.get(index).get(setup);
						if (previous.seconds + seconds + remaining[index + 1] >= cutoff
							|| kept.size() == keep && previous.seconds + seconds > kept.peek()) { continue; }
						List<SetupResult> proposed = new ArrayList<>(previous.setups);
						proposed.add(setup);
						// Every kit in the beam already passed, so only a setup bringing specs can break the rule.
						if (setup.getSpecial() != null && !specialWeaponsAllowed(proposed)) { continue; }
						if (!switches.completable(index, proposed)) { continue; }
						State state = new State(previous, setup, encounters, secondsOf, fought, seconds, memo, pricing, itemCost);
						if (state.cost < 0 && budget != Long.MAX_VALUE || state.cost > budget || Double.isNaN(state.energy)) { continue; }
						if (index == results.size() - 1 && !groups.isEmpty()
							&& switches.fullSetupChoice(state.setups) == null) { continue; }
						List<SetupResult> filled = RaidPlan.fill(state.setups, context, memo);
						// Switches alone are the cheap early reject; the packed spell supplies count too.
						if (RaidPlan.carriedEquipment(state.gear.keySet(), filled.get(0)) > limit) { continue; }
						List<RaidPlan.InventoryItem> inventory = RaidPlan.packedInventory(filled, context, owns, memo, state.gear, 0);
						if (RaidPlan.limitedSlots(inventory) > limit) { continue; }
						if (index < results.size() - 1)
						{
							// Prepot coverage can eliminate a later room's carried dose. Zero boost slots is the
							// safe inexpensive lower bound until the complete chronological timeline is known.
							inventory.removeIf(item -> item.getGroup() == RaidPlan.InventoryGroup.BOOSTS);
						}
						else
						{
							// At completion,
							// count every estimated bottle in fight order before this candidate can displace another.
							List<RaidEncounter> timeline = new ArrayList<>();
							List<SearchResults> boostResults = new ArrayList<>();
							List<SetupResult> boostSetups = new ArrayList<>();
							for (int position : fought)
							{
								timeline.add(encounters.get(position)); boostResults.add(results.get(position));
								boostSetups.add(filled.get(position));
							}
							inventory = RaidBoostEstimator.replace(inventory, timeline, boostResults, boostSetups);
						}
						if (RaidPlan.filledMaximumInventory(filled, state.gear.keySet(), inventory, memo) > 28) { continue; }
						Set<Integer> key = new HashSet<>(state.gear.keySet());
						// Worn items change capacity; keep different starting setups separate.
						for (GearItem worn : memo.gear(filled.get(0)).values())
						{ key.add(-worn.getId()); }
						// Spells/support and potion choices change inventory capacity too.
						inventory.forEach(item -> key.add(item.getIds().get(0)));
						List<Object> signature = new ArrayList<>();
						signature.add(key);
						// Boost requirements can differ between setups with identical gear. Keep that room order
						// available for the final dose estimate rather than merging it by physical item IDs.
						state.boosts = new ArrayList<>(previous.boosts);
						state.boosts.add(boostIds.computeIfAbsent(setup, s -> result.potionsForSetup(s).stream()
							.map(Potion::getId).sorted().collect(Collectors.toList())));
						signature.add(state.boosts);
						state.specWeapons = previous.specWeapons;
						if (setup.getSpecial() != null)
						{
							state.specWeapons = new HashSet<>(previous.specWeapons);
							setup.getSpecials().forEach(p -> state.specWeapons.add(p.getWeapon().getId()));
						}
						signature.add(state.specWeapons);
						// Kits left with different spec energy can finish differently.
						signature.add(Math.round(state.energy / ENERGY_STEP));
						// So can kits whose starting fight, charged only after the fights before it, spends or regenerates differently.
						if (state.charged < state.setups.size())
						{ signature.add(energyEffect(encounters.get(0), state.setups.get(0), secondsOf.get(0))); }
						// The same carried items can belong to different forms, with different valid switch patterns.
						for (int[] group : groups)
						{
							if (group[0] > index || group[group.length - 1] <= index) { continue; }
							for (int member : group)
							{
								if (member <= index)
								{
									SetupResult memberSetup = state.setups.get(member);
									signature.add(java.util.Arrays.asList(memberSetup.getLoadout(), encounters.get(member).isFreeze()
										? memberSetup.getSwitchFrom() : memberSetup.getSwitchFrom() == null));
								}
							}
						}
						state.found = found++;
						State old = distinct.get(signature);
						if (old == null)
						{
							distinct.put(signature, state);
							kept.add(state.seconds);
							if (kept.size() > keep) { kept.poll(); }
						}
						else if (state.seconds < old.seconds) { distinct.put(signature, state); }
					}
				}
			}
			next.addAll(distinct.values());
			// Equally fast kits keep the order they were found in: from faster earlier kits and higher-ranked setups.
			next.sort(Comparator.<State>comparingDouble(s -> s.seconds).thenComparingLong(s -> s.found));
			beam = next.subList(0, Math.min(BEAM, next.size()));
			if (beam.isEmpty()) { return null; }
			// Only kits grown from the beam can complete the raid: stop once none of them can do so within the bound.
			int from = index + 1;
			if (beam.stream().allMatch(state -> fastestFinish(state, options, from, limit) >= cutoff)) { return null; }
			progress.accept(10 + (index + 1) * 90.0 / results.size());
		}
		return beam.get(0);
	}

	/** The carried kit shares one defence reducer and at most one damage spec weapon per melee/ranged/magic class. */
	static boolean specialWeaponsAllowed(List<SetupResult> setups)
	{
		Set<Integer> drains = new HashSet<>();
		Map<CombatClass, Integer> damage = new EnumMap<>(CombatClass.class);
		for (SetupResult setup : setups)
		{
			for (com.bestgearsetup.calc.SpecialPlan plan : setup.getSpecials())
			{
				if (RaidPlan.providedInRaid(plan.getWeapon())) { continue; }
				boolean draining = plan.getSpecial() != null
					&& plan.getSpecial().getDrain() != com.bestgearsetup.calc.SpecialAttack.Drain.NONE;
				if (draining) { drains.add(plan.getWeapon().getId()); }
				else
				{
					// Use the spec's attacking style, not the worn kit or the defence the spec rolls against.
					CombatClass style = plan.getStyle() == null ? setup.getCombatClass() : WeaponRules.classOf(plan.getStyle());
					Integer shared = damage.putIfAbsent(style, plan.getWeapon().getId());
					if (shared != null && shared != plan.getWeapon().getId()) { return false; }
				}
			}
		}
		return drains.size() <= 1;
	}

	/**
	 * For each switch group, the encounter wearing the full setup the others quick-switch from, chosen so that every
	 * boost a quick switch assumes is also carried for a full setup outside the prepotted first room (a switch alone
	 * isn't worth bringing a potion for). Null when no choice does.
	 */
	static int[] fullSetupChoice(List<RaidEncounter> encounters, List<SearchResults> results, List<SetupResult> setups)
	{
		return new RaidSwitches(encounters, results).fullSetupChoice(setups);
	}

	/**
	 * A lower bound on any complete kit grown from this one: each later encounter's fastest setup that alone keeps
	 * the kit within the equipment limit, or infinity when some encounter has none. Carried equipment is the kit's
	 * gear minus the starting setup's worn items, so adding setups never lowers it; the only exception is ammunition
	 * later filled into an empty starting ammunition slot, allowed for with one slot of slack. Seconds are added in
	 * the planner's order, so rounding cannot lift the bound above a real kit's time.
	 */
	private static double fastestFinish(State state, List<List<Option>> options, int from, int limit)
	{
		Loadout start = state.setups.get(0).getLoadout();
		Set<Integer> worn = new HashSet<>();
		for (Slot slot : Slot.values())
		{
			if (start.get(slot) != null) { worn.add(start.get(slot).getId()); }
		}
		// Filling may wear a carried item in any empty slot of the starting setup (RaidPlan.fill).
		int slack = 0;
		for (Slot slot : Slot.values()) { if (slot != Slot.WEAPON && start.get(slot) == null) { slack++; } }
		int carried = 0;
		for (int id : state.gear.keySet()) { if (!worn.contains(id)) { carried++; } }
		double seconds = state.seconds;
		for (int index = from; index < options.size(); index++)
		{
			double fastest = Double.POSITIVE_INFINITY;
			for (Option option : options.get(index))
			{
				int added = 0;
				for (int id : option.gear) { if (!state.gear.containsKey(id) && !worn.contains(id)) { added++; } }
				if (carried + added - slack <= limit) { fastest = option.seconds; break; }
			}
			seconds += fastest;
		}
		return seconds;
	}

	/**
	 * What a setup does to the bar in its fight ({@link RaidEncounter#energyAfter}): the opening a full bar spends,
	 * keeping the bar while speccing on regenerated energy, or what it leaves of an empty bar without specs.
	 */
	private static List<Object> energyEffect(RaidEncounter encounter, SetupResult setup, Map<SetupResult, Double> seconds)
	{
		return java.util.Arrays.asList(setup.usesFullBar(), setup.usesFullBar() ? setup.getSpecial().getOpeningEnergy() : 0,
			Math.round(encounter.energyAfter(100, setup, () -> seconds.get(setup)) / ENERGY_STEP));
	}

	private static final class Option
	{
		final Set<Integer> gear;
		final double seconds;
		Option(Set<Integer> gear, double seconds)
		{
			this.gear = gear;
			this.seconds = seconds;
		}
	}

	/** Price each unique piece once, including loaded darts; unknown purchases cannot fit a finite budget. */
	static long cost(List<SetupResult> setups, IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount,
		IntToLongFunction quantities)
	{
		Map<Integer, GearItem> all = new LinkedHashMap<>();
		setups.forEach(setup -> all.putAll(RaidPlan.requiredItems(setup)));
		long total = 0;
		for (GearItem item : all.values())
		{
			long value = itemCost(item, owns, price, ammoCount, quantities);
			if (value < 0) { return -1; }
			total += value;
		}
		return total;
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
		/** Items already priced (under a budget only); the kit's cost only grows by pieces new to it. */
		final Set<Integer> priced = new HashSet<>();
		double seconds;
		/** Each setup's boost ids and the kit's spec weapons, for the kit signature; never changed once set. */
		List<List<Integer>> boosts = Collections.emptyList();
		Set<Integer> specWeapons = Collections.emptySet();
		/** Search order, which breaks ties between equally fast kits. */
		long found;
		/** Shared kit's purchase cost under a budget, or -1 once any unpriced piece must be bought. */
		long cost;
		/**
		 * Spec energy left after the kit's fights so far ({@link RaidEncounter#energyAfter}), from a full bar at the
		 * raid's start, through the first {@link #charged} fights in the raid's order; NaN once a setup opened on
		 * more than there was.
		 */
		double energy = SpecialRotation.FULL_ENERGY;
		/** Fights, in the raid's order, already charged to {@link #energy}. A later starting encounter is planned first but only charged once every fight before it has a setup. */
		int charged;
		State() { }
		/** {@code secondsOf} holds each encounter's setup times, by planning position. */
		State(State previous, SetupResult setup, List<RaidEncounter> encounters, List<Map<SetupResult, Double>> secondsOf,
			int[] fought, double encounterSeconds, RaidPlan.Memo memo, Function<SetupResult, Collection<GearItem>> pricing,
			ToLongFunction<GearItem> itemCost)
		{
			setups.addAll(previous.setups);
			setups.add(setup);
			energy = previous.energy;
			charged = previous.charged;
			while (charged < fought.length && fought[charged] < setups.size() && !Double.isNaN(energy))
			{
				int at = fought[charged++];
				SetupResult fight = setups.get(at);
				energy = encounters.get(at).energyAfter(energy, fight, () -> secondsOf.get(at).get(fight));
			}
			gear.putAll(previous.gear);
			gear.putAll(memo.gear(setup));
			seconds = previous.seconds + encounterSeconds;
			if (pricing == null) { return; }
			priced.addAll(previous.priced);
			cost = previous.cost;
			for (GearItem item : pricing.apply(setup))
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
