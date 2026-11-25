package com.bestgearsetup;

import com.bestgearsetup.calc.Antifire;
import com.bestgearsetup.calc.Style;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.Setup;
import com.bestgearsetup.data.Combat;
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

	public static SearchResults of(Monster monster, int startingHitpoints, Map<Style.Type, List<Setup>> byType,
		List<String> notes, Map<Combat, OffensivePrayer> prayers, Map<Combat, List<Potion>> potions,
		boolean assumedLevels)
	{
		return of(monster, startingHitpoints, byType, notes, prayers, potions, assumedLevels, Collections.emptyList(), false,
			false, Collections.emptyList());
	}

	public static SearchResults of(Monster monster, int startingHitpoints, Map<Style.Type, List<Setup>> byType,
		List<String> notes, Map<Combat, OffensivePrayer> prayers, Map<Combat, List<Potion>> potions,
		boolean assumedLevels, List<LockStatus> locks, boolean markOfDarkness, boolean rememberedLevels, List<String> warnings)
	{
		return new SearchResults(monster, startingHitpoints, byType, notes, prayers, potions, assumedLevels, locks,
			markOfDarkness, rememberedLevels, warnings, null, null, monster == null ? 0 : monster.getDefenceLevel(), 99, 99,
			null, Collections.emptySet(), Antifire.NONE);
	}
}
