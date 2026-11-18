package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.OffensivePrayer;
import com.bestgearsetup.data.Potion;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Search results with the defaults tests rarely care about. */
public final class TestResults
{
	private TestResults()
	{
	}

	public static SearchResults of(Monster monster, int startingHitpoints, Map<AttackStyle.Type, List<SetupResult>> byType,
		List<String> notes, Map<CombatClass, OffensivePrayer> prayers, Map<CombatClass, List<Potion>> potions,
		boolean assumedLevels)
	{
		return of(monster, startingHitpoints, byType, notes, prayers, potions, assumedLevels, Collections.emptyList(), false,
			false, Collections.emptyList());
	}

	public static SearchResults of(Monster monster, int startingHitpoints, Map<AttackStyle.Type, List<SetupResult>> byType,
		List<String> notes, Map<CombatClass, OffensivePrayer> prayers, Map<CombatClass, List<Potion>> potions,
		boolean assumedLevels, List<LockStatus> locks, boolean markOfDarkness, boolean rememberedLevels, List<String> warnings)
	{
		return new SearchResults(monster, startingHitpoints, byType, notes, prayers, potions, assumedLevels, locks,
			markOfDarkness, rememberedLevels, warnings, null, null, monster == null ? 0 : monster.getDefenceLevel(), 99, 99,
			null);
	}
}
