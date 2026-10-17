package com.bestgearsetup;

import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Compiled switch compatibility shared by beam pruning, trial ranking and published-kit selection. */
final class RaidSwitches
{
	private final List<RaidEncounter> encounters;
	private final List<SearchResults> results;
	/** By planning position, built on first use: usable setups, and the distinct loadouts of usable full setups. */
	private final List<List<SetupResult>> candidates = new ArrayList<>();
	private final List<List<Loadout>> baseLoadouts = new ArrayList<>();
	private final List<int[]> groups;
	private final int[][] groupAt;
	private final Map<List<Object>, Base> bases = new HashMap<>();
	/** {@link #completable} by planning position and what it reads of each chosen member up to it. */
	private final Map<List<Object>, Boolean> checked = new HashMap<>();

	RaidSwitches(List<RaidEncounter> encounters, List<SearchResults> results)
	{
		this.encounters = encounters;
		this.results = results;
		groupAt = new int[encounters.size()][];
		groups = RaidEncounter.switchGroups(encounters);
		candidates.addAll(Collections.nCopies(encounters.size(), null));
		baseLoadouts.addAll(Collections.nCopies(encounters.size(), null));
		for (int[] members : groups) { for (int member : members) { groupAt[member] = members; } }
	}

	/** A partial kit must still have a possible base and a reachable quick switch for each later member. */
	boolean completable(int index, List<SetupResult> setups)
	{
		int[] group = groupAt[index];
		if (group == null) { return true; }
		// Many kits share their group members' setups; only what the check reads tells them apart.
		List<Object> key = new ArrayList<>();
		key.add(index);
		for (int member : group)
		{
			if (member > index) { break; }
			SetupResult setup = setups.get(member);
			// A freeze is only switchable from the gear it was searched from, so keep that provenance apart.
			key.add(Arrays.asList(setup.getLoadout(), setup.getSpecial() == null,
				encounters.get(member).isFreeze() ? setup.getSwitchFrom() : setup.getSwitchFrom() == null));
		}
		return checked.computeIfAbsent(key, ignored -> prefixFits(group, index, setups));
	}

	private boolean prefixFits(int[] group, int index, List<SetupResult> setups)
	{
		for (int full : group)
		{
			if (!encounters.get(full).isFullSwitchSetup()) { continue; }
			if (full <= index)
			{
				SetupResult setup = setups.get(full);
				if (setup.getSwitchFrom() == null && base(group, full, setup.getLoadout()).fits(index, setups)) { return true; }
				continue;
			}
			for (Loadout loadout : baseLoadouts(full)) { if (base(group, full, loadout).fits(index, setups)) { return true; } }
		}
		return false;
	}

	private List<SetupResult> candidates(int index)
	{
		List<SetupResult> usable = candidates.get(index);
		if (usable == null)
		{
			SearchResults result = results.get(index);
			usable = result.hasBlockingLock() ? Collections.emptyList() : result.getByType().values().stream()
				.flatMap(List::stream).filter(setup -> setup.getDps().getPrimaryDps() > 0).collect(Collectors.toList());
			candidates.set(index, usable);
		}
		return usable;
	}

	private List<Loadout> baseLoadouts(int full)
	{
		List<Loadout> loadouts = baseLoadouts.get(full);
		if (loadouts == null)
		{
			loadouts = candidates(full).stream().filter(setup -> setup.getSwitchFrom() == null).map(SetupResult::getLoadout)
				.distinct().collect(Collectors.toList());
			baseLoadouts.set(full, loadouts);
		}
		return loadouts;
	}

	/** Chosen bases must also fund quick switches' carried boosts from independently boosted full setups. */
	int[] fullSetupChoice(List<SetupResult> setups)
	{
		List<List<Integer>> options = new ArrayList<>();
		for (int[] group : groups)
		{
			List<Integer> full = fullSetups(group, setups, encounters);
			if (full.isEmpty()) { return null; }
			options.add(full);
		}
		int[] choice = new int[groups.size()];
		return choose(options, 0, choice, setups) ? choice : null;
	}

	private boolean choose(List<List<Integer>> options, int at, int[] choice, List<SetupResult> setups)
	{
		if (at < groups.size())
		{
			for (int full : options.get(at))
			{
				choice[at] = full;
				if (choose(options, at + 1, choice, setups)) { return true; }
			}
			return false;
		}
		Set<Integer> quick = new HashSet<>();
		for (int group = 0; group < groups.size(); group++)
		{
			for (int member : groups.get(group)) { if (member != choice[group]) { quick.add(member); } }
		}
		Set<Integer> carried = new HashSet<>();
		Set<Integer> needed = new HashSet<>();
		for (int index = 0; index < setups.size(); index++)
		{
			for (Potion potion : results.get(index).carriedPotions(setups.get(index)))
			{
				if (!potion.isRaidSupply()) { (quick.contains(index) ? needed : carried).add(potion.getId()); }
			}
		}
		return carried.containsAll(needed);
	}

	/** Independently fastest setups, with each switch group ranked against the same compiled compatibility. */
	List<SetupResult> best()
	{
		List<Map<SetupResult, Double>> times = new ArrayList<>();
		List<SetupResult> best = new ArrayList<>();
		for (int index = 0; index < encounters.size(); index++)
		{
			int member = index;
			Map<SetupResult, Double> seconds = new IdentityHashMap<>();
			results.get(index).getByType().values().forEach(setups -> setups.forEach(setup ->
				seconds.put(setup, encounters.get(member).estimatedSeconds(results.get(member), setup))));
			times.add(seconds);
			best.add(fastest(index, null, null, times));
		}
		for (int[] group : groups)
		{
			List<SetupResult> chosen = null;
			double fastest = Double.POSITIVE_INFINITY;
			for (int full : group)
			{
				if (!encounters.get(full).isFullSwitchSetup()) { continue; }
				SetupResult setup = fastest(full, null, 0, times);
				if (setup == null) { continue; }
				Base base = base(group, full, setup.getLoadout());
				int used = 0;
				for (int member : group)
				{
					if (member == full) { continue; }
					for (List<SetupResult> options : results.get(member).getByType().values())
					{
						for (SetupResult quick : options)
						{
							int mask = base.mask(member, quick);
							if (mask >= 0) { used |= mask; }
						}
					}
				}
				for (Set<Slot> slots : RaidEncounter.extraSlotChoices(slots(used), base.limit))
				{
					List<SetupResult> trial = new ArrayList<>(best);
					trial.set(full, setup);
					double seconds = times.get(full).get(setup);
					for (int member : group)
					{
						if (member == full) { continue; }
						SetupResult quick = fastest(member, base, mask(slots), times);
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

	private SetupResult fastest(int member, Base base, Integer extras, List<Map<SetupResult, Double>> times)
	{
		return results.get(member).getByType().values().stream().flatMap(List::stream)
			.filter(setup -> extras == null || (base == null ? setup.getSwitchFrom() == null
				: base.mask(member, setup) >= 0 && (base.mask(member, setup) & ~extras) == 0))
			.min(Comparator.<SetupResult>comparingDouble(times.get(member)::get)
				.thenComparingDouble(setup -> -setup.getDps().getPrimaryDps())).orElse(null);
	}

	/** Allowed full-setup encounters whose setup every other member's is a quick switch from. */
	static List<Integer> fullSetups(int[] group, List<SetupResult> setups, List<RaidEncounter> encounters)
	{
		List<Integer> fulls = new ArrayList<>();
		for (int full : group)
		{
			if (!encounters.get(full).isFullSwitchSetup() || setups.get(full).getSwitchFrom() != null) { continue; }
			int extras = 0;
			int limit = encounters.get(full).getSwitchLimit();
			for (int member : group)
			{
				if (member == full) { continue; }
				int changed = change(encounters.get(member), setups.get(member), setups.get(full).getLoadout(), limit);
				if (changed < 0) { extras = -1; break; }
				extras |= changed;
			}
			if (extras >= 0 && Integer.bitCount(extras) < limit) { fulls.add(full); }
		}
		return fulls;
	}

	private Base base(int[] group, int full, Loadout loadout)
	{
		return bases.computeIfAbsent(Arrays.asList(full, loadout), ignored -> new Base(group, full, loadout));
	}

	/** Slot masks are computed once per base/candidate, instead of re-walking equipment for every beam state. */
	private final class Base
	{
		final int[] group;
		final int full;
		final Loadout loadout;
		final int limit;
		final Map<Integer, Map<SetupResult, Integer>> changes = new HashMap<>();
		/** Whether a later member has a quick switch within these extra slots, by member and slot mask. */
		final Map<Long, Boolean> reachable = new HashMap<>();
		Base(int[] group, int full, Loadout loadout)
		{
			this.group = group; this.full = full; this.loadout = loadout;
			limit = encounters.get(full).getSwitchLimit();
		}
		int mask(int member, SetupResult setup)
		{
			return changes.computeIfAbsent(member, ignored -> new IdentityHashMap<>())
				.computeIfAbsent(setup, ignored -> change(encounters.get(member), setup, loadout, limit));
		}
		boolean fits(int index, List<SetupResult> setups)
		{
			int extras = 0;
			for (int member : group)
			{
				if (member == full || member > index) { continue; }
				int changed = mask(member, setups.get(member));
				if (changed < 0) { return false; }
				extras |= changed;
			}
			if (Integer.bitCount(extras) >= limit) { return false; }
			for (int member : group)
			{
				if (member != full && member > index && !reachable(member, extras)) { return false; }
			}
			return true;
		}

		private boolean reachable(int member, int extras)
		{
			return reachable.computeIfAbsent((long) member << Integer.SIZE | extras, ignored -> candidates(member).stream()
				.anyMatch(setup -> mask(member, setup) >= 0 && Integer.bitCount(mask(member, setup) | extras) < limit));
		}
	}

	private static int change(RaidEncounter encounter, SetupResult setup, Loadout base, int limit)
	{
		if (setup.getSpecial() != null || !encounter.switchableFrom(setup, base)) { return -1; }
		Set<Slot> changed = setup.getLoadout().switchSlotsFrom(base);
		if (changed.size() > limit) { return -1; }
		changed.remove(Slot.WEAPON);
		return mask(changed);
	}

	private static int mask(Set<Slot> slots)
	{
		int mask = 0;
		for (Slot slot : slots) { mask |= 1 << slot.ordinal(); }
		return mask;
	}

	private static Set<Slot> slots(int mask)
	{
		Set<Slot> slots = java.util.EnumSet.noneOf(Slot.class);
		for (Slot slot : Slot.values()) { if ((mask & 1 << slot.ordinal()) != 0) { slots.add(slot); } }
		return slots;
	}
}
