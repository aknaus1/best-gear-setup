package com.bestgearsetup.calc;

import com.bestgearsetup.data.*;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * The strongest potion for a skill: usable anywhere, plus the raid's own supplies such as overloads and smelling
 * salts when the target is in that raid (never NMZ / Deadman only), narrowed to the boosts a search may assume.
 */
public final class PotionChoice
{
	private PotionChoice()
	{
	}

	/** Boosted level for one skill from the best potion {@code available} accepts, plus raid supplies usable against
	 * {@code target} (null for none). */
	public static int boostedLevel(GameData data, String skill, int level, Predicate<Potion> available, Monster target)
	{
		Potion p = resolve(data, skill, level, available, target);
		return p == null ? level : p.boost(level);
	}

	/** The best potion for a skill, or null for no boost; only potions {@code available} accepts, plus raid supplies
	 * usable against {@code target} (null for none). */
	public static Potion resolve(GameData data, String skill, int level, Predicate<Potion> available, Monster target)
	{
		Potion best = null;
		int bestLevel = level;
		for (Potion p : data.getPotions(skill))
		{
			if (bestCandidate(p, target) && available.test(p) && p.boost(level) > bestLevel)
			{
				best = p;
				bestLevel = p.boost(level);
			}
		}
		return best;
	}

	private static boolean bestCandidate(Potion p, Monster target)
	{
		return p.isUnrestricted() || target != null && p.isRaidSupply() && restriction(p, target) == null;
	}

	/**
	 * Where a restricted potion can be used, if the target isn't there (e.g. smelling salts outside the Tombs of
	 * Amascut), or null when it applies to this fight.
	 */
	static String restriction(Potion potion, Monster target)
	{
		for (String attribute : potion.getAttributes())
		{
			switch (attribute.toLowerCase(Locale.ROOT))
			{
				case "tombs of amascut":
					if (!CombatRules.toa(target))
					{
						return "only usable in the Tombs of Amascut";
					}
					break;
				case "xerician":
					if (!target.hasAttribute("xerician"))
					{
						return "only usable in the Chambers of Xeric";
					}
					break;
				case "nmz":
					return "only usable in Nightmare Zone";
				case "deadman":
					return "only usable in Deadman mode";
				default:
					return "restricted (" + attribute + ")";
			}
		}
		return null;
	}
}
