package com.bestgearsetup.calc;

import com.bestgearsetup.data.NameLists;
import java.util.Locale;

/**
 * Monsters that only appear in the Wilderness, so a fight against them is always a Wilderness fight.
 * PvM Arena copies of the Wilderness bosses are separate records and stay outside the list in name-lists.json.
 */
public final class WildernessTargets
{
	private static final NameLists.NameList NAMES = NameLists.list("wilderness-only monsters");

	private WildernessTargets()
	{
	}

	/** Whether every version of this monster is found only in the Wilderness. */
	public static boolean only(String name)
	{
		return name != null && NAMES.matches(name.toLowerCase(Locale.ROOT));
	}
}
