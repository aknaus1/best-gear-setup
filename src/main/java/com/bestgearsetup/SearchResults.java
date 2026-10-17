package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.FreezeDuty;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.ProtectionPrayers;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.Thrall;
import com.bestgearsetup.calc.WeaponRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.OffensivePrayer;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Value;
import lombok.With;

/**
 * Everything the results view shows for one search.
 */
@Value
public class SearchResults
{
	/** The target after any pre-fight stat drains. */
	Monster monster;
	/** Effective starting HP used by the calculator, which may be below the target's maximum. */
	int startingHitpoints;
	/** Best setups per attack type (stab, slash, crush, ranged, magic), best first. */
	@With(AccessLevel.PACKAGE)
	Map<AttackStyle.Type, List<SetupResult>> byType;
	List<String> notes;
	Map<CombatClass, OffensivePrayer> prayers;
	/** Boost entries per combat class and skill; combined potions can have multiple entries with the same item ID. */
	Map<CombatClass, List<Potion>> potions;
	boolean assumedLevels;
	/** How each slot lock affected the search; empty when nothing is locked. */
	@With(AccessLevel.PACKAGE)
	List<LockStatus> locks;
	/** Mark of Darkness is assumed for demonbane spells. */
	boolean markOfDarkness;
	/** Logged out, using the levels last seen on this account. */
	boolean rememberedLevels;
	/** Assumptions that change the headline estimates and must stay visible beside them (potion overrides...). */
	List<String> warnings;
	/** The thrall added to setups that can cast one, or null when none is. */
	Thrall thrall;
	/** The raid scaling applied, such as "ToB party: 3 (live raid)", or null outside raids. */
	String raidScaling;
	/** The target's Defence level before pre-fight drains. */
	int baseDefence;
	/** The player's Prayer level, which decides the protection prayers available. */
	int prayerLevel;
	/**
	 * Boosts of setups merged from searches with different boosts (a raid's quick switches, with and without one), keyed
	 * by each setup's DPS result instance, which ammunition-filled copies keep; other setups use {@link #potions}. Always
	 * identity-keyed: a boosted and an unboosted setup can have equal DPS results.
	 */
	@With(AccessLevel.PACKAGE)
	Map<DpsResult, List<Potion>> setupPotions;
	/** The raid's first room: its initial boost dose is drunk before entering. */
	boolean prepotted;
	/** Original incoming stats and search assumptions for equipment-aware overhead advice. */
	@With(AccessLevel.PACKAGE)
	ProtectionPrayers.Context protectionContext;
	/** How long raid boost doses last; null for {@link BoostTiming#DEFAULT}. */
	BoostTiming boostTiming;

	/**
	 * Every field. {@code setupPotions} is copied into an identity-keyed map, so callers can't key boosts by value; build
	 * it as an {@link IdentityHashMap} too, since a value-keyed map has already merged equal DPS results.
	 */
	public SearchResults(Monster monster, int startingHitpoints, Map<AttackStyle.Type, List<SetupResult>> byType,
		List<String> notes, Map<CombatClass, OffensivePrayer> prayers, Map<CombatClass, List<Potion>> potions,
		boolean assumedLevels, List<LockStatus> locks, boolean markOfDarkness, boolean rememberedLevels, List<String> warnings,
		Thrall thrall, String raidScaling, int baseDefence, int prayerLevel,
		Map<DpsResult, List<Potion>> setupPotions, boolean prepotted, ProtectionPrayers.Context protectionContext,
		BoostTiming boostTiming)
	{
		this.monster = monster;
		this.startingHitpoints = startingHitpoints;
		this.byType = byType;
		this.notes = notes;
		this.prayers = prayers;
		this.potions = potions;
		this.assumedLevels = assumedLevels;
		this.locks = locks;
		this.markOfDarkness = markOfDarkness;
		this.rememberedLevels = rememberedLevels;
		this.warnings = warnings;
		this.thrall = thrall;
		this.raidScaling = raidScaling;
		this.baseDefence = baseDefence;
		this.prayerLevel = prayerLevel;
		this.setupPotions = setupPotions.isEmpty() ? Collections.emptyMap()
			: Collections.unmodifiableMap(new IdentityHashMap<>(setupPotions));
		this.prepotted = prepotted;
		this.protectionContext = protectionContext;
		this.boostTiming = boostTiming;
	}

	/** Results with per-setup boosts merged from several searches, optionally in the prepotted first room. */
	public SearchResults(Monster monster, int startingHitpoints, Map<AttackStyle.Type, List<SetupResult>> byType,
		List<String> notes, Map<CombatClass, OffensivePrayer> prayers, Map<CombatClass, List<Potion>> potions,
		boolean assumedLevels, List<LockStatus> locks, boolean markOfDarkness, boolean rememberedLevels, List<String> warnings,
		Thrall thrall, String raidScaling, int baseDefence, int prayerLevel,
		Map<DpsResult, List<Potion>> setupPotions, boolean prepotted)
	{
		this(monster, startingHitpoints, byType, notes, prayers, potions, assumedLevels, locks, markOfDarkness,
			rememberedLevels, warnings, thrall, raidScaling, baseDefence, prayerLevel, setupPotions, prepotted, null, null);
	}

	/** The same results with raid boost doses lasting as {@code timing} says. */
	SearchResults withBoostTiming(BoostTiming timing)
	{
		// Cached results stay the same instance while the timing is unchanged.
		if (java.util.Objects.equals(boostTiming, timing)) { return this; }
		return new SearchResults(monster, startingHitpoints, byType, notes, prayers, potions, assumedLevels, locks,
			markOfDarkness, rememberedLevels, warnings, thrall, raidScaling, baseDefence, prayerLevel,
			setupPotions, prepotted, protectionContext, timing);
	}

	BoostTiming getBoostTiming()
	{
		return boostTiming == null ? BoostTiming.DEFAULT : boostTiming;
	}

	/** Results whose setups all use {@link #potions}, outside the prepotted first room. */
	public SearchResults(Monster monster, int startingHitpoints, Map<AttackStyle.Type, List<SetupResult>> byType,
		List<String> notes, Map<CombatClass, OffensivePrayer> prayers, Map<CombatClass, List<Potion>> potions,
		boolean assumedLevels, List<LockStatus> locks, boolean markOfDarkness, boolean rememberedLevels, List<String> warnings,
		Thrall thrall, String raidScaling, int baseDefence, int prayerLevel)
	{
		this(monster, startingHitpoints, byType, notes, prayers, potions, assumedLevels, locks, markOfDarkness,
			rememberedLevels, warnings, thrall, raidScaling, baseDefence, prayerLevel, Collections.emptyMap(), false);
	}

	public SearchResults(Monster monster, int startingHitpoints, Map<AttackStyle.Type, List<SetupResult>> byType,
		List<String> notes, Map<CombatClass, OffensivePrayer> prayers, Map<CombatClass, List<Potion>> potions,
		boolean assumedLevels)
	{
		this(monster, startingHitpoints, byType, notes, prayers, potions, assumedLevels, Collections.emptyList(), false, false,
			Collections.emptyList());
	}

	public SearchResults(Monster monster, int startingHitpoints, Map<AttackStyle.Type, List<SetupResult>> byType,
		List<String> notes, Map<CombatClass, OffensivePrayer> prayers, Map<CombatClass, List<Potion>> potions,
		boolean assumedLevels, List<LockStatus> locks, boolean markOfDarkness, boolean rememberedLevels, List<String> warnings)
	{
		this(monster, startingHitpoints, byType, notes, prayers, potions, assumedLevels, locks, markOfDarkness,
			rememberedLevels, warnings, null, null, monster == null ? 0 : monster.getDefenceLevel(), 99);
	}

	/** Whether a lock could not be honoured. */
	public boolean hasBlockingLock()
	{
		return locks.stream().anyMatch(LockStatus::isBlocking);
	}

	public boolean isEmpty()
	{
		return byType.isEmpty();
	}

	/** Only show the setup chosen from the shared raid kit. */
	public SearchResults withSetup(SetupResult setup)
	{
		return withCandidates(Collections.singletonMap(setup.getAttackType(), Collections.singletonList(setup)));
	}

	/** Only this setup, keeping just its own merged boosts: what a published raid plan reads of its encounter. */
	SearchResults onlySetup(SetupResult setup)
	{
		Map<DpsResult, List<Potion>> boosts = new IdentityHashMap<>();
		List<Potion> own = setupPotions.get(setup.getDps());
		if (own != null) { boosts.put(setup.getDps(), own); }
		return withCandidates(Collections.singletonMap(setup.getAttackType(), Collections.singletonList(setup)), boosts);
	}

	SearchResults withCandidates(Map<AttackStyle.Type, List<SetupResult>> candidates)
	{
		return withCandidates(candidates, setupPotions);
	}

	/** Filter candidates in search order, dropping empty tabs and retaining each setup's identity-keyed boosts. */
	SearchResults filter(Predicate<SetupResult> usable)
	{
		Map<AttackStyle.Type, List<SetupResult>> candidates = new LinkedHashMap<>();
		byType.forEach((type, setups) ->
		{
			List<SetupResult> kept = setups.stream().filter(usable).collect(Collectors.toList());
			if (!kept.isEmpty()) { candidates.put(type, kept); }
		});
		return withCandidates(candidates);
	}

	/** Candidates from several searches, each setup keeping the boosts its own search assumed. */
	SearchResults withCandidates(Map<AttackStyle.Type, List<SetupResult>> candidates, Map<DpsResult, List<Potion>> boosts)
	{
		return withByType(candidates).withSetupPotions(boosts);
	}

	/**
	 * A freeze duty's candidates ({@link FreezeDuty}): only those landing each cast at least {@code target} of the time
	 * when any does, otherwise the most accurate switch from each worn setup. Best first by
	 * {@link FreezeDuty#preference}: the fewest items, then the highest accuracy.
	 */
	SearchResults forFreeze(double target)
	{
		List<SetupResult> all = byType.values().stream().flatMap(List::stream).collect(Collectors.toList());
		boolean reachable = all.stream().anyMatch(setup -> FreezeDuty.reaches(setup, target));
		Map<Loadout, Double> mostAccurate = new HashMap<>();
		all.forEach(setup -> mostAccurate.merge(setup.getSwitchFrom() == null ? new Loadout() : setup.getSwitchFrom(),
			setup.getDps().getAccuracy(), Math::max));
		Map<AttackStyle.Type, List<SetupResult>> candidates = new LinkedHashMap<>();
		byType.forEach((type, setups) ->
		{
			List<SetupResult> kept = setups.stream().filter(setup -> reachable ? FreezeDuty.reaches(setup, target)
				: FreezeDuty.reaches(setup, mostAccurate.get(setup.getSwitchFrom() == null ? new Loadout() : setup.getSwitchFrom())))
				.sorted(FreezeDuty.preference(target)).collect(Collectors.toList());
			if (!kept.isEmpty()) { candidates.put(type, Collections.unmodifiableList(kept)); }
		});
		return withCandidates(candidates);
	}

	/**
	 * Each candidate whose spec plan opens on a full bar, followed by the same setup on regenerated energy alone
	 * ({@link SetupResult#withRegeneratedEnergy}): a raid has only so many full bars, and the kit planner chooses
	 * where to spend them. Each copy keeps its setup's boosts.
	 */
	SearchResults withRegeneratedChoices()
	{
		Map<AttackStyle.Type, List<SetupResult>> candidates = new LinkedHashMap<>();
		Map<DpsResult, List<Potion>> boosts = new IdentityHashMap<>(setupPotions);
		byType.forEach((type, setups) ->
		{
			List<SetupResult> both = new ArrayList<>();
			for (SetupResult setup : setups)
			{
				both.add(setup);
				if (!setup.usesFullBar()) { continue; }
				SetupResult regenerated = setup.withRegeneratedEnergy();
				both.add(regenerated);
				List<Potion> own = setupPotions.get(setup.getDps());
				if (own != null) { boosts.putIfAbsent(regenerated.getDps(), own); }
			}
			candidates.put(type, Collections.unmodifiableList(both));
		});
		return withCandidates(candidates, boosts);
	}

	/** Results searched with raid switches locked into these slots by the kit planner rather than the player: their lock statuses are dropped and the slots are shown as filled. */
	SearchResults wearing(Set<Slot> slots)
	{
		Map<AttackStyle.Type, List<SetupResult>> marked = new LinkedHashMap<>();
		byType.forEach((type, setups) -> marked.put(type, setups.stream().map(setup -> setup.withFilledSlots(slots))
			.collect(Collectors.toList())));
		List<LockStatus> kept = locks.stream().filter(lock -> !slots.contains(lock.getSlot())).collect(Collectors.toList());
		return withByType(marked).withLocks(kept);
	}

	/** The same results in the raid's first room, whose boosts are drunk before entering. */
	SearchResults withPrepotted()
	{
		return new SearchResults(monster, startingHitpoints, byType, notes, prayers, potions,
			assumedLevels, locks, markOfDarkness, rememberedLevels, warnings, thrall, raidScaling, baseDefence, prayerLevel,
			setupPotions, true, protectionContext, boostTiming);
	}

	/** Explain raid-wide choices beside the encounter's existing assumptions. */
	SearchResults withNotes(List<String> extra)
	{
		List<String> all = new ArrayList<>(notes);
		all.addAll(extra);
		return new SearchResults(monster, startingHitpoints, byType, Collections.unmodifiableList(all), prayers, potions,
			assumedLevels, locks, markOfDarkness, rememberedLevels, warnings, thrall, raidScaling, baseDefence, prayerLevel,
			setupPotions, prepotted, protectionContext, boostTiming);
	}

	/** The same results with extra warnings shown beside them, e.g. a raid style choice that couldn't be used. */
	public SearchResults withWarnings(List<String> extra)
	{
		if (extra.isEmpty()) { return this; }
		List<String> all = new ArrayList<>(warnings);
		extra.stream().filter(w -> !all.contains(w)).forEach(all::add);
		return new SearchResults(monster, startingHitpoints, byType, notes, prayers, potions, assumedLevels, locks,
			markOfDarkness, rememberedLevels, Collections.unmodifiableList(all), thrall, raidScaling, baseDefence, prayerLevel,
			setupPotions, prepotted, protectionContext, boostTiming);
	}

	/** The overhead to use against this target with a setup, which fights in melee range or from distance. */
	public ProtectionPrayers.Advice protectionFor(SetupResult setup)
	{
		return monster == null ? null : ProtectionPrayers.recommend(monster, setup, prayerLevel, protectionContext);
	}

	/** Presence-only supplies outside the prepotted first room; complete raid quantities use RaidBoostEstimator. */
	public List<Potion> carriedPotions(SetupResult setup)
	{
		return prepotted ? Collections.emptyList() : potionsForSetup(setup);
	}

	/** Boosts required by a setup, including Strength for atlatls and hunter's spears. */
	public List<Potion> potionsForSetup(SetupResult setup)
	{
		List<Potion> merged = setupPotions.get(setup.getDps());
		if (merged != null) { return merged; }
		List<Potion> candidates = new ArrayList<>(potions.getOrDefault(setup.getCombatClass(), Collections.emptyList()));
		if (setup.getCombatClass() == CombatClass.RANGED && WeaponRules.scalesWithStrength(setup.getLoadout().getWeapon()))
		{
			for (Potion potion : potions.getOrDefault(CombatClass.MELEE, Collections.emptyList()))
			{
				if ("strength".equalsIgnoreCase(potion.getSkill())) { candidates.add(potion); }
			}
		}
		List<Potion> used = new ArrayList<>();
		for (Potion potion : candidates)
		{
			if (used.stream().noneMatch(other -> other.getId() == potion.getId())) { used.add(potion); }
		}
		return used;
	}
}
