package com.bestgearsetup;

import com.bestgearsetup.calc.*;
import com.bestgearsetup.data.*;
import java.util.List;
import java.util.Map;
import lombok.Value;

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
	Map<Style.Type, List<Setup>> byType;
	List<String> notes;
	Map<Combat, OffensivePrayer> prayers;
	/** Potions used per combat class (melee may use one for Attack and another for Strength). */
	Map<Combat, List<Potion>> potions;
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
	/** The raid scaling applied, such as "ToB party: 3 (live raid)", or null outside raids. */
	String raidScaling;
	/** The target's Defence level before pre-fight drains. */
	int baseDefence;
	/** Base Magic level used to time Mark of Darkness. */
	int baseMagic;
	/** Boosted Magic level used to time thralls. */
	int boostedMagic;
	/** Base levels searched with, which time a trip's boost and prayer restore doses; null when not known. */
	PlayerLevels levels;

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
