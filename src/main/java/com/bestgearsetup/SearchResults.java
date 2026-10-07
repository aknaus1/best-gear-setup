package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.Thrall;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.OffensivePrayer;
import com.bestgearsetup.data.Potion;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Everything the results view shows for one search.
 */
@Value
@AllArgsConstructor
public class SearchResults
{
	/** The target after any pre-fight stat drains. */
	Monster monster;
	/** Effective starting HP used by the calculator, which may be below the target's maximum. */
	int startingHitpoints;
	/** Best setups per attack type (stab, slash, crush, ranged, magic), best first. */
	Map<AttackStyle.Type, List<SetupResult>> byType;
	List<String> notes;
	Map<CombatClass, OffensivePrayer> prayers;
	/** Potions used per combat class (melee may use one for Attack and another for Strength). */
	Map<CombatClass, List<Potion>> potions;
	boolean assumedLevels;
	/** How each slot lock affected the search; empty when nothing is locked. */
	List<LockStatus> locks;
	/** Mark of Darkness is assumed for demonbane spells. */
	boolean markOfDarkness;
	/** Logged out, using the levels last seen on this account. */
	boolean rememberedLevels;
	/** Assumptions that change the headline estimates and must stay visible beside them (potion overrides...). */
	List<String> warnings;
	/** The thrall added to setups that can cast one, or null when none is. */
	Thrall thrall;

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
			rememberedLevels, warnings, null);
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
}
