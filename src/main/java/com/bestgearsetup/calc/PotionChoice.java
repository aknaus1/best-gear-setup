package com.bestgearsetup.calc;

import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Potion;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Resolves the user's potion choice for a combat class into boosted levels.
 * A choice is "best" (strongest potion usable anywhere, plus the raid's own supplies such as overloads and
 * smelling salts when the target is in that raid; never NMZ / Deadman only), "none", or a potion name from the
 * potion table. "Best" can be narrowed further, e.g. to owned
 * boosts; a potion chosen by name is always used.
 */
public final class PotionChoice
{
	public static final String BEST = "best";
	public static final String NONE = "none";

	private PotionChoice()
	{
	}

	/** Boosted level for one skill; "best" only considers potions {@code available} accepts, plus raid supplies
	 * usable against {@code target} (null for none). */
	public static int boostedLevel(GameData data, String skill, int level, String choice, Predicate<Potion> available,
		Monster target)
	{
		Potion p = resolve(data, skill, level, choice, available, target);
		return p == null ? level : p.boost(level);
	}

	/** The potion a choice resolves to for a skill, or null for none / no boost. "Best" only considers potions
	 * {@code available} accepts, plus raid supplies usable against {@code target} (null for none). */
	public static Potion resolve(GameData data, String skill, int level, String choice, Predicate<Potion> available,
		Monster target)
	{
		String c = choice == null ? BEST : choice.trim().toLowerCase(Locale.ROOT);
		if (c.equals(NONE))
		{
			return null;
		}
		Potion best = null;
		int bestLevel = level;
		for (Potion p : data.getPotions(skill))
		{
			boolean match = c.equals(BEST) ? bestCandidate(p, target) && available.test(p) : p.getName().equalsIgnoreCase(c);
			if (match && p.boost(level) > bestLevel)
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

	/** Whether the choice names a potion rather than "best" or "none". */
	public static boolean isExplicit(String choice)
	{
		String c = choice == null ? BEST : choice.trim().toLowerCase(Locale.ROOT);
		return !c.equals(BEST) && !c.equals(NONE);
	}

	/**
	 * Where a restricted potion can be used, if the target isn't there (e.g. smelling salts outside the Tombs of
	 * Amascut), or null when it applies to this fight.
	 */
	public static String restriction(Potion potion, Monster target)
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
