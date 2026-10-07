package com.bestgearsetup.calc;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Monsters that only appear in the Wilderness, so a fight against them is always a Wilderness fight.
 * PvM Arena copies of the Wilderness bosses are separate records and stay outside this list.
 */
public final class WildernessTargets
{
	private static final Set<String> NAMES = new HashSet<>(Arrays.asList(
		"mammoth", "lava dragon", "elder chaos druid",
		"callisto", "artio", "venenatis", "venenatis' spiderling (monster)", "spindel", "spindel's spiderling",
		"scorpia", "scorpia's guardian", "scorpia's offspring (monster)",
		"chaos elemental", "chaos fanatic", "crazy archaeologist"));
	private static final String[] PREFIXES = {"revenant ", "vet'ion", "calvar'ion"};
	private static final String[] MINION_TAGS = {"(vet'ion)", "(calvar'ion)"};

	private WildernessTargets()
	{
	}

	/** Whether every version of this monster is found only in the Wilderness. */
	public static boolean only(String name)
	{
		if (name == null)
		{
			return false;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		if (NAMES.contains(lower))
		{
			return true;
		}
		for (String prefix : PREFIXES)
		{
			if (lower.startsWith(prefix))
			{
				return true;
			}
		}
		for (String tag : MINION_TAGS)
		{
			if (lower.endsWith(tag))
			{
				return true;
			}
		}
		return false;
	}
}
