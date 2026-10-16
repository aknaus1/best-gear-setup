package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.FreezeDuty;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * Reduce switches by the time they cost across the raid, using only gear from the original best setups: drop an
 * item everywhere, or have the starting setup wear an item another setup carries anyway.
 */
final class RaidConsolidator
{
	@FunctionalInterface
	interface Search
	{
		SearchResults run(int encounter, Set<Integer> excluded, int pass) throws IOException;

		/** Report nested search work within the current trial. */
		default void searching(DoubleConsumer progress) { }

		/** As {@link #run}, with these items locked into the encounter's setups; null when that isn't supported. */
		default SearchResults wearing(int encounter, Set<Integer> excluded, Map<Slot, Integer> worn, int pass) throws IOException
		{
			return null;
		}

		/**
		 * Threads that may search a pass's trials alongside the calling one, which this search must then accept calls
		 * from; null to search them one by one on the calling thread.
		 */
		default Executor helpers() { return null; }

		/** How many {@link #helpers} threads may take part at once. */
		default int helperCount() { return 0; }
	}

	/** Passes planned past a fitting baseline whose kit wasn't found ({@link #select}); each searches every removal. */
	private static final int MAX_FITTING_PASSES = 6;
	/**
	 * Trials planned into kits per pass, those with the fastest setups first. A pass searches every removal (often 30 to
	 * 50) but rarely gains from planning past its first few: in a profiled ToA search every faster kit came from the
	 * first six, while the rest took most of the planning time. A slower trial is still ranked for removal.
	 */
	private static final int PLANNED_TRIALS = 8;

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
		return select(data, type, spellbook, encounters, original, locked, limit, budget, owns, price, ammoCount, quantities,
			cancelled, bound, search, pass -> percent -> { });
	}

	/** Trial and beam progress within each adaptive removal pass. */
	static RaidPlan select(GameData data, RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters,
		List<SearchResults> original, Set<Integer> locked, int limit, long budget, IntPredicate owns,
		ToLongFunction<GearItem> price, int ammoCount, IntToLongFunction quantities, BooleanSupplier cancelled,
		double bound, Search search, IntFunction<DoubleConsumer> passes) throws IOException
	{
		// Removing equipment may make a restricted freeze search fall back to a splashable setup. That fallback
		// describes what the restricted gear can do; it must never relax the original raid duty to free a slot.
		double[] freezeAccuracy = new double[original.size()];
		List<SearchResults> qualified = new ArrayList<>();
		for (int index = 0; index < original.size(); index++)
		{
			SearchResults result = original.get(index);
			if (encounters.get(index).isFreeze())
			{
				freezeAccuracy[index] = result.getByType().values().stream().flatMap(List::stream)
					.mapToDouble(setup -> setup.getDps().getAccuracy()).max().orElse(0);
			}
			qualified.add(keepFreezeAccuracy(result, freezeAccuracy[index]));
		}
		// Restricted searches from earlier passes, per encounter. A search's best setup stays optimal under any
		// larger exclusion set it does not touch, so later passes only re-search encounters the last removal affected.
		List<List<Searched>> searched = new ArrayList<>();
		for (int index = 0; index < original.size(); index++) { searched.add(new ArrayList<>()); }
		Set<Integer> allowed = new HashSet<>(locked);
		for (SetupResult setup : best(qualified, encounters)) { allowed.addAll(gear(setup)); }
		// A room's slower spec alternative can be the raid's shared weapon; keep those choices searchable.
		qualified.forEach(result -> result.getByType().values().forEach(candidates -> candidates.forEach(setup ->
			setup.getSpecials().forEach(plan ->
			{
				allowed.add(plan.getWeapon().getId());
				if (plan.getOffhand() != null) { allowed.add(plan.getOffhand().getId()); }
				if (plan.getAmmo() != null) { allowed.add(plan.getAmmo().getId()); }
			}))));
		Set<Integer> excluded = new HashSet<>();
		for (Slot slot : Slot.values())
		{
			for (GearItem item : data.getItems(slot))
			{
				if (!allowed.contains(item.getId()) && !RaidPlan.providedInRaid(item)) { excluded.add(item.getId()); }
			}
		}
		List<SearchResults> current = qualified.stream().map(result -> filter(result, excluded)).collect(Collectors.toList());
		// Items consolidation removed on top of that base exclusion: few, so cached searches compare cheaply.
		Set<Integer> dropped = new HashSet<>();
		// Carried items a starting setup was chosen to wear, saving their switch at some cost to its DPS, and its index.
		Map<Slot, Integer> worn = new EnumMap<>(Slot.class);
		int wornAt = -1;
		int[] starts = RaidEncounter.starts(encounters);
		int pass = 1;
		RaidPlan feasible = null;
		double feasibleSeconds = bound;
		// Passes that fit the limit yet found no faster kit: in a row, and in all.
		int stale = 0;
		int fittingPasses = 0;
		List<int[]> groups = RaidEncounter.switchGroups(encounters);
		while (!cancelled.getAsBoolean())
		{
			DoubleConsumer passProgress = passes.apply(pass);
			passProgress.accept(0);
			DoubleConsumer progress = passProgress;
			List<SetupResult> baseline = best(current, encounters);
			double baselineSeconds = seconds(baseline, current, encounters);
			// Every later trial only restricts these searches further, and no kit beats its own fastest setups (the
			// same bound that stops trial planning below): once these can't beat the kit in hand, no pass can, so
			// skip this pass's searches.
			if (baselineSeconds >= feasibleSeconds) { return feasible; }
			RaidPlan.Memo baselineMemo = new RaidPlan.Memo();
			int baselineStart = RaidPlan.bestStart(baseline, current, starts, baselineMemo);
			double baselineExcess = excess(baseline, current, encounters, baselineStart, baselineMemo, limit, budget, owns, price, ammoCount,
				quantities);
			// Removals are ranked by the slots they save over this limit.
			int rankLimit = limit;
			double rankExcess = baselineExcess;
			double before = feasibleSeconds;
			if (baselineExcess <= 0)
			{
				// The kit's own planning takes the first fifth of the pass, so trials that follow still advance it.
				RaidPlan fitted = RaidPlanner.select(type, spellbook, encounters, current, limit, budget, owns, price,
					ammoCount, quantities, cancelled, range(passProgress, 0, 20), feasibleSeconds);
				if (fitted != null && seconds(fitted.getSetups(), fitted.getResults(), encounters) < feasibleSeconds) { return fitted; }
				// The fastest setups fit, yet no faster kit was found from them (say a switch group's members, or the
				// boosts, don't agree). A lower limit would have gone on consolidating, and its kit fits this limit
				// too, so go on as if every slot counted, planning each pass's trials, while that still finds faster
				// kits; a few passes at most, since removals can't fix every reason a kit is unplannable.
				if (cancelled.getAsBoolean() || baselineSeconds >= feasibleSeconds || fittingPasses++ >= MAX_FITTING_PASSES)
				{ return feasible; }
				progress = range(passProgress, 20, 80);
				rankLimit = 0;
				rankExcess = excess(baseline, current, encounters, baselineStart, baselineMemo, 0, budget, owns, price, ammoCount,
					quantities);
			}
			List<Integer> removable = baseline.stream().flatMap(setup -> gear(setup).stream()).distinct()
				.filter(id -> !locked.contains(id) && !RaidPlan.providedInRaid(data.findItem(id))).sorted().collect(Collectors.toList());
			List<SearchResults> chosen = null;
			Integer removed = null;
			Map<Slot, Integer> chosenWorn = worn;
			int chosenWornAt = wornAt;
			double smallestLoss = Double.POSITIVE_INFINITY;
			List<Trial> trials = new ArrayList<>();
			int target = worn.isEmpty() ? baselineStart : wornAt;
			boolean targetGrouped = groups.stream().anyMatch(group -> java.util.Arrays.stream(group).anyMatch(member -> member == target));
			List<Map.Entry<Slot, Integer>> wearChoices = targetGrouped ? Collections.emptyList()
				: wearable(baseline, current, worn, target);
			int trialCount = Math.max(1, removable.size() + wearChoices.size());
			// Each trial only reads this pass's state, so the trials may be searched at once. Their new searches are
			// remembered, and the trials ranked, in trial order afterwards, exactly as one by one: no trial can reuse
			// another's search of the same pass, since each drops its own item or wears its own extra one.
			Pass state = new Pass(trialCount, progress, cancelled, excluded, dropped, worn, wornAt, baseline, current, pass,
				baselineSeconds, rankExcess, rankLimit);
			List<Callable<Ranked>> tasks = new ArrayList<>();
			for (int id : removable)
			{
				int work = tasks.size();
				tasks.add(() -> removal(id, work, state, groups, encounters, searched, freezeAccuracy, search, starts, budget,
					owns, price, ammoCount, quantities));
			}
			// Wearing another setup's item in the starting setup frees its switch, e.g. melee boots while maging: the
			// setup the kit starts in now, or the one already wearing such items. Quick switches follow their group's
			// full setups, so a grouped start keeps its own search.
			for (Map.Entry<Slot, Integer> wear : wearChoices)
			{
				int work = tasks.size();
				tasks.add(() -> wearing(wear, work, target, state, encounters, searched, freezeAccuracy, search, starts, budget,
					owns, price, ammoCount, quantities));
			}
			List<Ranked> ranked = runAll(tasks, search.helpers(), search.helperCount());
			if (cancelled.getAsBoolean()) { return null; }
			for (Ranked trial : ranked)
			{
				trial.added.forEach((index, added) -> searched.get(index).addAll(added));
				if (trial.trial == null) { continue; }
				trials.add(trial.trial);
				if (trial.loss < smallestLoss)
				{
					smallestLoss = trial.loss; chosen = trial.trial.results; removed = trial.removed; chosenWorn = trial.worn;
					chosenWornAt = trial.wornAt;
				}
			}
			// No kit beats its trial's independently best setups, so plan the most promising trials first: the
			// best feasible time then tightens early and later trials stop at the planner's lower bound.
			progress.accept(80);
			trials.sort(Comparator.comparingDouble(trial -> trial.bestSeconds));
			int planned = 0;
			int toPlan = Math.min(PLANNED_TRIALS, trials.size());
			for (Trial trial : trials)
			{
				if (cancelled.getAsBoolean()) { return null; }
				if (trial.bestSeconds >= feasibleSeconds || planned >= PLANNED_TRIALS) { break; }
				// Trials only rank removals, so each is planned from the start its fastest setups suit; the final kit
				// below compares every start.
				DoubleConsumer planProgress = range(progress, 80.0 + 20.0 * planned++ / toPlan, 20.0 / toPlan);
				RaidPlan plan = RaidPlanner.select(type, spellbook, encounters, trial.results, limit, budget, owns, price,
					ammoCount, quantities, cancelled, planProgress, feasibleSeconds, new int[]{trial.start});
				double planSeconds = plan == null ? Double.POSITIVE_INFINITY : seconds(plan.getSetups(), trial.results, encounters);
				if (planSeconds < feasibleSeconds) { feasible = plan; feasibleSeconds = planSeconds; }
			}
			progress.accept(100);
			// Keep any feasible fallback, but finish comparing armour sharing before accepting a slow style switch.
			// Within the limit, stop after two passes in a row find nothing faster: a lower limit, still over it, would have
			// taken the same removals and kept going, so a single dry pass could stop short of the kit it finds.
			// A pass that finds no kit at all counts as dry too.
			stale = baselineExcess <= 0 && !(feasibleSeconds < before) ? stale + 1 : 0;
			if (chosen == null || stale >= 2) { return feasible; }
			if (removed != null) { excluded.add(removed); dropped.add(removed); }
			worn = chosenWorn;
			wornAt = worn.isEmpty() ? -1 : chosenWornAt;
			current = chosen;
			pass++;
		}
		return null;
	}

	/** Freeze accuracy is fixed before consolidation, including the best available fallback when 100% is impossible. */
	private static SearchResults keepFreezeAccuracy(SearchResults found, double minimum)
	{
		if (found == null || minimum <= 0) { return found; }
		Map<AttackStyle.Type, List<SetupResult>> candidates = new LinkedHashMap<>();
		found.getByType().forEach((type, setups) ->
		{
			List<SetupResult> kept = setups.stream().filter(setup -> FreezeDuty.reaches(setup, minimum)).collect(Collectors.toList());
			if (!kept.isEmpty()) { candidates.put(type, kept); }
		});
		return found.withCandidates(candidates);
	}

	private static DoubleConsumer range(DoubleConsumer progress, double start, double span)
	{
		return percent -> progress.accept(start + span * percent / 100);
	}

	/**
	 * Reuse an earlier search with the same worn items whose best setup avoids every excluded item; otherwise search
	 * and remember it. Every search shares the base exclusion, so searches are told apart by the items consolidation
	 * {@code dropped}.
	 */
	private static SearchResults restricted(RaidEncounter encounter, List<Searched> earlier, List<Searched> added, int index,
		Set<Integer> excluded, Set<Integer> dropped, Map<Slot, Integer> worn, int pass, Search search) throws IOException
	{
		Searched reusable = null;
		for (Searched entry : earlier)
		{
			// Prefer the most restricted match: its alternatives were found under the closest exclusions.
			if (entry.worn.equals(worn) && dropped.containsAll(entry.dropped) && Collections.disjoint(entry.bestGear, dropped)
				&& (reusable == null || entry.dropped.size() > reusable.dropped.size()))
			{ reusable = entry; }
		}
		if (reusable != null) { return filter(reusable.results, excluded); }
		SearchResults found = worn.isEmpty() ? search.run(index, excluded, pass) : search.wearing(index, excluded, worn, pass);
		if (found != null && !found.isEmpty() && !found.hasBlockingLock())
		{
			added.add(new Searched(new HashSet<>(dropped), new HashMap<>(worn), found,
				gear(fastest(found, encounter))));
		}
		return found;
	}

	/**
	 * Items the starting setup at {@code target} could wear instead of another setup carrying them: each slot's items
	 * in the other setups, except the weapon and ammunition, slots the player locked or already chosen, and slots
	 * whose (filled) item another setup wears too, where a different item would free nothing.
	 */
	private static List<Map.Entry<Slot, Integer>> wearable(List<SetupResult> baseline, List<SearchResults> results,
		Map<Slot, Integer> worn, int target)
	{
		Loadout start = RaidPlan.fill(baseline, results, new RaidPlan.Memo(), target).get(target).getLoadout();
		List<SetupResult> otherSetups = new ArrayList<>(baseline);
		otherSetups.remove(target);
		List<Map.Entry<Slot, Integer>> wearable = new ArrayList<>();
		for (Slot slot : Slot.values())
		{
			GearItem own = start.get(slot);
			List<GearItem> others = otherSetups.stream().map(setup -> setup.getLoadout().get(slot))
				.filter(item -> item != null && !RaidPlan.providedInRaid(item)).collect(Collectors.toList());
			if (slot == Slot.WEAPON || slot == Slot.AMMO || worn.containsKey(slot)
				|| results.get(target).getLocks().stream().anyMatch(lock -> lock.getSlot() == slot)
				|| own != null && others.stream().anyMatch(item -> item.getId() == own.getId())) { continue; }
			others.stream().map(GearItem::getId).distinct()
				.forEach(id -> wearable.add(new java.util.AbstractMap.SimpleImmutableEntry<>(slot, id)));
		}
		return wearable;
	}

	/** A trial's extra seconds per equipment or inventory slot saved, or infinity when it saves none. */
	/** A searched trial, ranked by the time it costs per slot it saves over the pass's limit. */
	private static Ranked lossPerSlot(List<SearchResults> trial, List<RaidEncounter> encounters, int[] starts, Pass state,
		long budget, IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount, IntToLongFunction quantities)
	{
		List<SetupResult> setups = best(trial, encounters);
		double trialSeconds = seconds(setups, trial, encounters);
		RaidPlan.Memo memo = new RaidPlan.Memo();
		int start = RaidPlan.bestStart(setups, trial, starts, memo);
		double saved = state.rankExcess - excess(setups, trial, encounters, start, memo, state.rankLimit, budget, owns, price,
			ammoCount, quantities);
		return new Ranked(new Trial(trial, trialSeconds, start),
			saved <= 0 ? Double.POSITIVE_INFINITY : (trialSeconds - state.baselineSeconds) / saved);
	}

	/**
	 * Drop {@code id} everywhere: re-search each encounter whose best setup uses it (a switch group as a whole) and
	 * rank the result. Searches it adds are returned with it rather than remembered here.
	 */
	private static Ranked removal(int id, int work, Pass state, List<int[]> groups, List<RaidEncounter> encounters,
		List<List<Searched>> searched, double[] freezeAccuracy, Search search, int[] starts, long budget, IntPredicate owns,
		ToLongFunction<GearItem> price, int ammoCount, IntToLongFunction quantities) throws IOException
	{
		Map<Integer, List<Searched>> added = new TreeMap<>();
		if (state.cancelled.getAsBoolean()) { return Ranked.none(added); }
		state.progress.accept(80.0 * work / state.trialCount);
		Set<Integer> trialExcluded = new HashSet<>(state.excluded); trialExcluded.add(id);
		Set<Integer> trialDropped = new HashSet<>(state.dropped); trialDropped.add(id);
		// A dropped item can't stay worn either.
		Map<Slot, Integer> trialWorn = new EnumMap<>(Slot.class);
		state.worn.forEach((slot, wornId) -> { if (wornId != id) { trialWorn.put(slot, wornId); } });
		List<SearchResults> trial = new ArrayList<>();
		// A switch group's quick switches are searched from its other members' setups, so the whole group is
		// re-searched when any member is. Its candidates depend on each other, so earlier searches aren't reused.
		Set<Integer> grouped = new HashSet<>();
		for (int[] group : groups)
		{
			if (java.util.Arrays.stream(group).anyMatch(member -> gear(state.baseline.get(member)).contains(id)))
			{ java.util.Arrays.stream(group).forEach(grouped::add); }
		}
		for (int index = 0; index < state.current.size(); index++)
		{
			DoubleConsumer encounterProgress = range(state.progress,
				80.0 * (work + (double) index / state.current.size()) / state.trialCount, 80.0 / state.trialCount / state.current.size());
			search.searching(encounterProgress);
			encounterProgress.accept(0);
			SearchResults result = state.current.get(index);
			// An unaffected best setup already gives the same optimum under this restriction.
			SearchResults replacement = grouped.contains(index) ? search.run(index, trialExcluded, state.pass)
				: gear(state.baseline.get(index)).contains(id) ? restricted(encounters.get(index), searched.get(index),
					added.computeIfAbsent(index, key -> new ArrayList<>()), index, trialExcluded, trialDropped,
					index == state.wornAt ? trialWorn : Collections.emptyMap(), state.pass, search)
				: filter(result, trialExcluded);
			replacement = keepFreezeAccuracy(replacement, freezeAccuracy[index]);
			if (replacement == null || replacement.isEmpty() || replacement.hasBlockingLock()) { return Ranked.none(added); }
			trial.add(replacement);
			encounterProgress.accept(100);
		}
		return lossPerSlot(trial, encounters, starts, state, budget, owns, price, ammoCount, quantities)
			.with(added, id, trialWorn, state.wornAt);
	}

	/**
	 * Have the starting setup at {@code target} wear another setup's item, freeing its switch, and rank the result.
	 * Searches it adds are returned with it rather than remembered here.
	 */
	private static Ranked wearing(Map.Entry<Slot, Integer> wear, int work, int target, Pass state, List<RaidEncounter> encounters,
		List<List<Searched>> searched, double[] freezeAccuracy, Search search, int[] starts, long budget, IntPredicate owns,
		ToLongFunction<GearItem> price, int ammoCount, IntToLongFunction quantities) throws IOException
	{
		Map<Integer, List<Searched>> added = new TreeMap<>();
		if (state.cancelled.getAsBoolean()) { return Ranked.none(added); }
		DoubleConsumer wearProgress = range(state.progress, 80.0 * work / state.trialCount, 80.0 / state.trialCount);
		search.searching(wearProgress);
		wearProgress.accept(0);
		Map<Slot, Integer> trialWorn = new EnumMap<>(state.worn); trialWorn.put(wear.getKey(), wear.getValue());
		SearchResults replacement = restricted(encounters.get(target), searched.get(target),
			added.computeIfAbsent(target, key -> new ArrayList<>()), target, state.excluded, state.dropped, trialWorn, state.pass, search);
		replacement = keepFreezeAccuracy(replacement, freezeAccuracy[target]);
		if (replacement == null || replacement.isEmpty() || replacement.hasBlockingLock()) { return Ranked.none(added); }
		List<SearchResults> trial = new ArrayList<>(state.current); trial.set(target, replacement);
		return lossPerSlot(trial, encounters, starts, state, budget, owns, price, ammoCount, quantities)
			.with(added, null, trialWorn, target);
	}

	/**
	 * Run the tasks on up to {@code helperCount} helpers as well as the calling thread, which works through them too
	 * (so they finish even when no helper is free), and return their results in task order.
	 */
	private static <T> List<T> runAll(List<Callable<T>> tasks, Executor helpers, int helperCount) throws IOException
	{
		int count = tasks.size();
		Object[] found = new Object[count];
		AtomicInteger next = new AtomicInteger();
		AtomicInteger remaining = new AtomicInteger(count);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		CompletableFuture<Void> done = new CompletableFuture<>();
		if (count == 0) { done.complete(null); }
		Runnable worker = () ->
		{
			for (int task = next.getAndIncrement(); task < count; task = next.getAndIncrement())
			{
				try
				{
					// After a failure the remaining tasks are skipped, though still counted.
					if (failure.get() == null) { found[task] = tasks.get(task).call(); }
				}
				catch (Exception | Error e) { failure.compareAndSet(null, e); }
				finally { if (remaining.decrementAndGet() == 0) { done.complete(null); } }
			}
		};
		for (int helper = 0; helpers != null && helper < Math.min(helperCount, count - 1); helper++)
		{
			try { helpers.execute(worker); }
			// A pool that is shutting down leaves the work to this thread.
			catch (RejectedExecutionException e) { break; }
		}
		worker.run();
		// Tasks a helper took may still be running; join waits for them (and isn't interruptible).
		done.join();
		Throwable thrown = failure.get();
		if (thrown instanceof IOException) { throw (IOException) thrown; }
		if (thrown instanceof RuntimeException) { throw (RuntimeException) thrown; }
		if (thrown instanceof Error) { throw (Error) thrown; }
		if (thrown != null) { throw new IllegalStateException(thrown); }
		List<T> results = new ArrayList<>(count);
		for (Object result : found)
		{
			@SuppressWarnings("unchecked") T typed = (T) result;
			results.add(typed);
		}
		return results;
	}

	/** What every trial of one consolidation pass reads; none of it changes until the pass's trials are all searched. */
	private static final class Pass
	{
		final int trialCount;
		final DoubleConsumer progress;
		final BooleanSupplier cancelled;
		final Set<Integer> excluded;
		final Set<Integer> dropped;
		final Map<Slot, Integer> worn;
		final int wornAt;
		final List<SetupResult> baseline;
		final List<SearchResults> current;
		final int pass;
		final double baselineSeconds;
		/** Removals are ranked by the slots they save over this limit, from this excess. */
		final double rankExcess;
		final int rankLimit;

		Pass(int trialCount, DoubleConsumer progress, BooleanSupplier cancelled, Set<Integer> excluded, Set<Integer> dropped,
			Map<Slot, Integer> worn, int wornAt, List<SetupResult> baseline, List<SearchResults> current, int pass,
			double baselineSeconds, double rankExcess, int rankLimit)
		{
			this.trialCount = trialCount;
			this.progress = progress;
			this.cancelled = cancelled;
			this.excluded = excluded;
			this.dropped = dropped;
			this.worn = worn;
			this.wornAt = wornAt;
			this.baseline = baseline;
			this.current = current;
			this.pass = pass;
			this.baselineSeconds = baselineSeconds;
			this.rankExcess = rankExcess;
			this.rankLimit = rankLimit;
		}
	}

	/**
	 * A searched trial with its loss per slot saved, or none ({@code trial} null, when some encounter had no usable
	 * setup or the search was cancelled), and the restricted searches it added, by encounter.
	 */
	private static final class Ranked
	{
		final Trial trial;
		final double loss;
		Map<Integer, List<Searched>> added = Collections.emptyMap();
		/** The item dropped, or null for a worn item. */
		Integer removed;
		Map<Slot, Integer> worn;
		int wornAt;

		Ranked(Trial trial, double loss)
		{
			this.trial = trial;
			this.loss = loss;
		}

		static Ranked none(Map<Integer, List<Searched>> added)
		{
			return new Ranked(null, Double.POSITIVE_INFINITY).with(added, null, null, -1);
		}

		Ranked with(Map<Integer, List<Searched>> added, Integer removed, Map<Slot, Integer> worn, int wornAt)
		{
			this.added = added;
			this.removed = removed;
			this.worn = worn;
			this.wornAt = wornAt;
			return this;
		}
	}

	private static final class Searched
	{
		final Set<Integer> dropped;
		final Map<Slot, Integer> worn;
		final SearchResults results;
		final Set<Integer> bestGear;
		Searched(Set<Integer> dropped, Map<Slot, Integer> worn, SearchResults results, Set<Integer> bestGear)
		{
			this.dropped = dropped;
			this.worn = worn;
			this.results = results;
			this.bestGear = bestGear;
		}
	}

	private static final class Trial
	{
		final List<SearchResults> results;
		/** Sum of each encounter's fastest setup: no shared kit from these results can be faster. */
		final double bestSeconds;
		/** The starting encounter carrying the fewest switches for those fastest setups. */
		final int start;
		Trial(List<SearchResults> results, double bestSeconds, int start)
		{
			this.results = results;
			this.bestSeconds = bestSeconds;
			this.start = start;
		}
	}

	/**
	 * A search's fastest setup for this encounter. With overkill counted ({@link RaidEncounter#estimatedSeconds}) a lower
	 * DPS can kill small targets sooner, so setups are ranked by time; ties keep the higher DPS, then the search order.
	 */
	static SetupResult fastest(SearchResults result, RaidEncounter encounter)
	{
		return fastest(result.getByType().values().stream().flatMap(List::stream).collect(Collectors.toList()), result, encounter,
			new IdentityHashMap<>());
	}

	/** As above among these setups, remembering each setup's time in {@code times} for later calls. */
	private static SetupResult fastest(List<SetupResult> setups, SearchResults result, RaidEncounter encounter,
		Map<SetupResult, Double> times)
	{
		SetupResult best = null;
		double bestSeconds = Double.POSITIVE_INFINITY;
		for (SetupResult setup : setups)
		{
			double seconds = times.computeIfAbsent(setup, s -> encounter.estimatedSeconds(result, s));
			if (best == null || seconds < bestSeconds
				|| seconds == bestSeconds && setup.getDps().getPrimaryDps() > best.getDps().getPrimaryDps())
			{ best = setup; bestSeconds = seconds; }
		}
		return best;
	}

	/**
	 * Each encounter's fastest setup, except in a switch group: there the member whose fastest setup, with the other
	 * members' fastest quick switches from it, takes the least time (each member's fastest when none qualifies).
	 */
	static List<SetupResult> best(List<SearchResults> results, List<RaidEncounter> encounters)
	{
		// Each setup's time, by encounter: switch groups rank the same candidates for every full setup and extra slots.
		List<Map<SetupResult, Double>> times = new ArrayList<>();
		List<SetupResult> best = new ArrayList<>();
		for (int index = 0; index < results.size(); index++)
		{
			times.add(new IdentityHashMap<>());
			best.add(fastest(results.get(index).getByType().values().stream().flatMap(List::stream).collect(Collectors.toList()),
				results.get(index), encounters.get(index), times.get(index)));
		}
		for (int[] group : RaidEncounter.switchGroups(encounters))
		{
			List<SetupResult> each = best;
			List<SetupResult> chosen = null;
			double fastest = Double.POSITIVE_INFINITY;
			for (int full : group)
			{
				if (!encounters.get(full).isFullSwitchSetup()) { continue; }
				SetupResult base = fastest(results.get(full).getByType().values().stream().flatMap(List::stream)
					.filter(setup -> setup.getSwitchFrom() == null).collect(Collectors.toList()), results.get(full), encounters.get(full),
					times.get(full));
				if (base == null) { continue; }
				// Slots the members' quick switches change besides the weapon; the group shares some of them.
				int limit = encounters.get(full).getSwitchLimit();
				Set<Slot> used = EnumSet.noneOf(Slot.class);
				for (int member : group)
				{
					if (member == full) { continue; }
					RaidEncounter quickMember = encounters.get(member);
					results.get(member).getByType().values().stream().flatMap(List::stream)
						.filter(setup -> setup.getSpecial() == null && quickMember.switchableFrom(setup, base.getLoadout()))
						.map(setup -> setup.getLoadout().switchSlotsFrom(base.getLoadout()))
						.filter(changed -> changed.size() <= limit).forEach(used::addAll);
				}
				for (Set<Slot> extras : RaidEncounter.extraSlotChoices(used, limit))
				{
					List<SetupResult> trial = new ArrayList<>(each);
					trial.set(full, base);
					double seconds = times.get(full).get(base);
					for (int member : group)
					{
						if (member == full) { continue; }
						SetupResult quick = fastest(results.get(member).getByType().values().stream().flatMap(List::stream)
							.filter(setup -> setup.getSpecial() == null && encounters.get(member).switchableFrom(setup, base.getLoadout())
								&& RaidEncounter.quickSwitchAllows(setup.getLoadout(), base.getLoadout(), extras))
							.collect(Collectors.toList()), results.get(member), encounters.get(member), times.get(member));
						if (quick == null) { seconds = Double.POSITIVE_INFINITY; break; }
						trial.set(member, quick);
						seconds += times.get(member).get(quick);
					}
					if (seconds < fastest) { fastest = seconds; chosen = trial; }
				}
			}
			if (chosen != null) { best = chosen; }
		}
		return best;
	}

	private static Set<Integer> gear(SetupResult setup)
	{
		Set<Integer> ids = new HashSet<>(RaidPlan.gear(setup).keySet());
		if (setup.getLoadout().getLoadedAmmo() != null) { ids.add(setup.getLoadout().getLoadedAmmo().getId()); }
		setup.getSpecials().stream().filter(special -> special.getLoadedAmmo() != null)
			.forEach(special -> ids.add(special.getLoadedAmmo().getId()));
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

	/**
	 * Equipment ({@link RaidPlan#limitedSlots}) and inventory slots over the limits, worn in for the setup at
	 * {@code start} (the possible start carrying the fewest switches, from {@link RaidPlan#bestStart} with the same memo).
	 */
	private static double excess(List<SetupResult> setups, List<SearchResults> results, List<RaidEncounter> encounters,
		int start, RaidPlan.Memo memo, int limit,
		long budget, IntPredicate owns, ToLongFunction<GearItem> price, int ammoCount, IntToLongFunction quantities)
	{
		List<SetupResult> filled = RaidPlan.fill(setups, results, memo, start);
		List<RaidPlan.InventoryItem> inventory = RaidBoostEstimator.replace(
			RaidPlan.packedInventory(filled, results, owns, memo, start), encounters, results, filled);
		int equipment = RaidPlan.limitedSlots(inventory);
		long cost = RaidPlanner.cost(setups, owns, price, ammoCount, quantities);
		if (cost < 0 && budget != Long.MAX_VALUE) { return Double.POSITIVE_INFINITY; }
		return Math.max(0, equipment - limit) + Math.max(0, RaidPlan.filledMaximumInventory(filled, inventory, memo) - 28)
			+ (budget == Long.MAX_VALUE ? 0 : Math.max(0, cost - budget) / (double) Math.max(1, budget));
	}
}
