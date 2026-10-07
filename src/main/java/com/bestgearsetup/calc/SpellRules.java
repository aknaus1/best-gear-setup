package com.bestgearsetup.calc;

import com.bestgearsetup.data.Spell;
import java.util.Locale;

/** Elemental spell scaling and equipment compatibility, independent of API max-hit snapshots. */
final class SpellRules
{
	private SpellRules()
	{
	}

	static String element(Spell spell)
	{
		String name = spell == null ? "" : spell.getName().toLowerCase(Locale.ROOT);
		if (name.startsWith("wind "))
		{
			return "air";
		}
		for (String element : new String[]{"water", "earth", "fire"})
		{
			if (name.startsWith(element + " "))
			{
				return element;
			}
		}
		return "";
	}

	static int maxHit(Loadout l, CombatContext ctx)
	{
		Spell spell = l.getSpell();
		String name = EncounterDamage.spell(l);
		int magic = ctx.getMagic();
		if (name.equals("magic dart"))
		{
			return EncounterDamage.lower(l.getWeapon()).startsWith("slayer's staff (e)") && ctx.isOnTask()
				? 13 + magic / 6 : 10 + magic / 10;
		}
		if (element(spell).isEmpty())
		{
			return spell.getMaxHit();
		}
		String tier = name.substring(name.indexOf(' ') + 1);
		int[] thresholds;
		int[] maxima;
		switch (tier)
		{
			case "strike":
				thresholds = new int[]{5, 9, 13};
				maxima = new int[]{2, 4, 6, 8};
				break;
			case "bolt":
				thresholds = new int[]{23, 29, 35};
				maxima = new int[]{9, 10, 11, 12};
				break;
			case "blast":
				thresholds = new int[]{47, 53, 59};
				maxima = new int[]{13, 14, 15, 16};
				break;
			case "wave":
				thresholds = new int[]{65, 70, 75};
				maxima = new int[]{17, 18, 19, 20};
				break;
			case "surge":
				thresholds = new int[]{85, 90, 95};
				maxima = new int[]{21, 22, 23, 24};
				break;
			default:
				return spell.getMaxHit();
		}
		int index = 0;
		while (index < thresholds.length && magic >= thresholds[index])
		{
			index++;
		}
		return maxima[index];
	}
}
