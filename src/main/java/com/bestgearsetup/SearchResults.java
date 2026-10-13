package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.ProtectionPrayers;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.Thrall;
import com.bestgearsetup.calc.WeaponRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.OffensivePrayer;
import com.bestgearsetup.data.Potion;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
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
	/** Boost entries per combat class and skill; combined potions can have multiple entries with the same item ID. */
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
	/** The raid scaling applied, such as "ToB party: 3 (live raid)", or null outside raids. */
	String raidScaling;
	/** The target's Defence level before pre-fight drains. */
	int baseDefence;
	/** The player's Prayer level, which decides the protection prayers available. */
	int prayerLevel;

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

	SearchResults withCandidates(Map<AttackStyle.Type, List<SetupResult>> candidates)
	{
		return new SearchResults(monster, startingHitpoints, candidates, notes, prayers, potions,
			assumedLevels, locks, markOfDarkness, rememberedLevels, warnings, thrall, raidScaling, baseDefence, prayerLevel);
	}

	/** The overhead to use against this target with a setup, which fights in melee range or from distance. */
	public ProtectionPrayers.Advice protectionFor(SetupResult setup)
	{
		return monster == null ? null : ProtectionPrayers.recommend(monster, setup.getCombatClass(), prayerLevel);
	}

	/** Boosts required by a setup, including Strength for atlatls and hunter's spears. */
	public List<Potion> potionsForSetup(SetupResult setup)
	{
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
