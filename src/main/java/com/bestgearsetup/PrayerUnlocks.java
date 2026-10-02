package com.bestgearsetup;

import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.data.OffensivePrayer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/**
 * Offensive prayers that need an unlock on top of their level requirement: Piety and Chivalry
 * (King's Ransom and the Knight Waves training), Rigour and Augury (Chambers of Xeric scrolls),
 * Deadeye and Mystic Vigour (Varlamore scrolls).
 */
final class PrayerUnlocks
{
	/** RuneScape-profile key remembering the account's unlocks for logged-out searches. */
	static final String CONFIG_KEY = "prayerUnlocks";

	private static final Set<String> GATED = Collections.unmodifiableSet(new TreeSet<>(Arrays.asList(
		"piety", "chivalry", "rigour", "augury", "deadeye", "mystic vigour")));
	/** Knight Waves training ground state once all eight knights are defeated. */
	private static final int KNIGHT_WAVES_COMPLETE = 8;

	private PrayerUnlocks()
	{
	}

	static boolean isGated(String prayer)
	{
		return GATED.contains(prayer.toLowerCase(Locale.ROOT));
	}

	/** Whether a varbit records one of the unlocks. */
	static boolean isUnlockVarbit(int varbitId)
	{
		return varbitId == VarbitID.KR_KNIGHTWAVES_STATE || varbitId == VarbitID.PRAYER_RIGOUR_UNLOCKED
			|| varbitId == VarbitID.PRAYER_AUGURY_UNLOCKED || varbitId == VarbitID.PRAYER_DEADEYE_UNLOCKED
			|| varbitId == VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED;
	}

	/** The gated prayers the logged-in account has unlocked. Client thread only. */
	static Set<String> read(Client client)
	{
		Set<String> unlocked = new TreeSet<>();
		if (client.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE) >= KNIGHT_WAVES_COMPLETE)
		{
			unlocked.add("chivalry");
			unlocked.add("piety");
		}
		if (client.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED) == 1)
		{
			unlocked.add("rigour");
		}
		if (client.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED) == 1)
		{
			unlocked.add("augury");
		}
		if (client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED) == 1)
		{
			unlocked.add("deadeye");
		}
		if (client.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED) == 1)
		{
			unlocked.add("mystic vigour");
		}
		return unlocked;
	}

	static String serialize(Set<String> unlocked)
	{
		return String.join(",", new TreeSet<>(unlocked));
	}

	/** Parsed profile value; null when nothing has been recorded for the profile yet. */
	static Set<String> parse(String value)
	{
		if (value == null)
		{
			return null;
		}
		Set<String> unlocked = new TreeSet<>();
		for (String name : value.split(","))
		{
			String trimmed = name.trim().toLowerCase(Locale.ROOT);
			if (GATED.contains(trimmed))
			{
				unlocked.add(trimmed);
			}
		}
		return unlocked;
	}

	/**
	 * @param unlocked the account's gated unlocks, or null when unknown (before the account's first login,
	 *                 when levels are assumed 99 too), in which case every prayer is assumed unlocked
	 */
	static Predicate<String> available(Set<String> unlocked)
	{
		return name ->
		{
			String lower = name.toLowerCase(Locale.ROOT);
			if (!GATED.contains(lower))
			{
				return true;
			}
			return unlocked == null || unlocked.contains(lower);
		};
	}

	/** The strongest prayer the levels and unlocks allow, or null if none applies. */
	static OffensivePrayer best(List<OffensivePrayer> prayers, PlayerLevels levels, Predicate<String> available)
	{
		OffensivePrayer best = null;
		double bestScore = 1;
		for (OffensivePrayer p : prayers)
		{
			if (p.getPrayerLevel() > levels.getPrayer() || p.getDefenceLevel() > levels.getDefence()
				|| !available.test(p.getName()))
			{
				continue;
			}
			double score = (1 + p.getAccuracyPercent() / 100) * (1 + p.getDamagePercent() / 100);
			if (score > bestScore)
			{
				bestScore = score;
				best = p;
			}
		}
		return best;
	}
}
