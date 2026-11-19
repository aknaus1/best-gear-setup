package com.bestgearsetup.calc;

import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Potion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Test helper: the potion names a combat class can pick, used to check the settings dropdowns cover the data. */
public final class PotionNames
{
	private PotionNames()
	{
	}

	/** Skills each combat class boosts. */
	public static List<String> skillsFor(Combat cls)
	{
		switch (cls)
		{
			case RANGED:
				return Arrays.asList("ranged");
			case MAGIC:
				return Arrays.asList("magic");
			default:
				return Arrays.asList("attack", "strength");
		}
	}

	/** Potion names selectable for a combat class, in data order. */
	public static List<String> namesFor(GameData data, Combat cls)
	{
		Set<String> names = new LinkedHashSet<>();
		for (String skill : skillsFor(cls))
		{
			for (Potion p : data.getPotions(skill))
			{
				if (p.getPercentageIncrease() >= 0 && p.getBaseIncrease() >= 0)
				{
					names.add(p.getName());
				}
			}
		}
		return new ArrayList<>(names);
	}
}
