package com.bestgearsetup.calc;

import com.bestgearsetup.data.Spell;

/** Elemental spell scaling and equipment compatibility, independent of API max-hit snapshots. */
final class SpellRules
{
	private static final String[] ELEMENTS = {"water", "earth", "fire"};
	private static final String[] STANDARD_TIERS = {"strike", "bolt", "blast", "wave", "surge"};
	private static final String[] ANCIENT_TIERS = {"rush", "burst", "blitz", "barrage"};

	private SpellRules()
	{
	}

	static String element(Spell spell)
	{
		String name = spell == null ? "" : spell.getLowerName();
		if (name.startsWith("wind "))
		{
			return "air";
		}
		for (String element : ELEMENTS)
		{
			if (name.startsWith(element) && name.startsWith(" ", element.length()))
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
		return baseMax(spell, magic);
	}

	/** A spell's maximum before equipment: elemental spells scale with the Magic level, others are fixed. */
	static int baseMax(Spell spell, int magic)
	{
		String name = spell.getLowerName();
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

	/**
	 * Whether {@code strong} makes {@code weak} pointless for this search: the same element or ancient school one
	 * tier up, so every weakness, tome, amulet and sceptre effect applies to both and only the maximum differs.
	 * Tier-only effects keep a weaker spell: chaos gauntlets (bolts), the twinflame staff's second hit (bolt, blast
	 * and wave, never surge) and, with several targets, the multi-target burst over the single-target blitz.
	 */
	static boolean outclasses(Spell strong, Spell weak, int magic, boolean twinflame, boolean chaosGauntlets, boolean area)
	{
		String a = strong.getLowerName();
		String b = weak.getLowerName();
		int cutA = a.lastIndexOf(' ');
		int cutB = b.lastIndexOf(' ');
		if (cutA < 0 || cutA != cutB || !a.regionMatches(0, b, 0, cutA) || strong.getSpellbook() == null
			|| !strong.getSpellbook().equalsIgnoreCase(weak.getSpellbook()) || strong.getAttackSpeed() > weak.getAttackSpeed())
		{
			return false;
		}
		String[] tiers = element(strong).isEmpty() ? ANCIENT_TIERS : STANDARD_TIERS;
		int tierA = tier(tiers, a, cutA);
		int tierB = tier(tiers, b, cutB);
		if (tierB < 0 || tierA <= tierB)
		{
			return false;
		}
		if (tiers == ANCIENT_TIERS)
		{
			// Burst and barrage hit every grouped target.
			return strong.getMaxHit() >= weak.getMaxHit() && (!area || tierA % 2 == 1 || tierB % 2 == 0);
		}
		return baseMax(strong, magic) >= baseMax(weak, magic) + (tierB == 1 && chaosGauntlets ? 3 : 0)
			&& !(twinflame && tierA == 4 && tierB > 0);
	}

	private static int tier(String[] tiers, String name, int cut)
	{
		for (int i = 0; i < tiers.length; i++)
		{
			if (name.startsWith(tiers[i], cut + 1) && name.length() == cut + 1 + tiers[i].length())
			{
				return i;
			}
		}
		return -1;
	}
}
