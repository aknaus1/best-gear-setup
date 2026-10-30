package com.bestgearsetup.calc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure checks of spell rune costs against a {@link RuneFilter}. */
public final class RuneRules
{
	private RuneRules()
	{
	}

	/** Rune ids of the cost, in its order, that are not supplied and not permitted. */
	public static List<Integer> disallowed(Map<Integer, Integer> cost, Set<Integer> supplied, RuneFilter filter)
	{
		if (cost == null || cost.isEmpty() || filter == null || filter.isEmpty())
		{
			return Collections.emptyList();
		}
		List<Integer> out = null;
		for (Integer id : cost.keySet())
		{
			if (!supplied.contains(id) && !filter.permits(id))
			{
				if (out == null)
				{
					out = new ArrayList<>();
				}
				out.add(id);
			}
		}
		return out == null ? Collections.emptyList() : out;
	}

	/** Whether the loadout's attack spell needs no disallowed rune; loadouts without a spell are always allowed. */
	public static boolean allows(Loadout loadout, RuneFilter filter)
	{
		if (filter == null || filter.isEmpty() || loadout.getSpell() == null)
		{
			return true;
		}
		return disallowed(SpellRunes.perCast(loadout.getSpell().getName()), SpellRunes.supplied(loadout), filter)
			.isEmpty();
	}
}
