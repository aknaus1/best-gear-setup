package com.bestgearsetup;

import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.data.OffensivePrayer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/**
 * Account unlocks a search needs: offensive prayers on top of their level requirement, Piety and Chivalry (King's
 * Ransom and the Knight Waves training), Rigour and Augury (Chambers of Xeric scrolls), Deadeye and Mystic Vigour
 * (Varlamore scrolls) and Zeal (the Horn of Bran), and the Fractured Archive's elemental fragments broken, by element.
 */
final class PrayerUnlocks
{
	/** RuneScape-profile key remembering the account's unlocks for logged-out searches. */
	static final String CONFIG_KEY = "prayerUnlocks";

	/** Unlocks recorded as a varbit set to 1. */
	private static final Map<Integer, String> FLAGS = Map.of(VarbitID.PRAYER_RIGOUR_UNLOCKED, "rigour",
		VarbitID.PRAYER_AUGURY_UNLOCKED, "augury", VarbitID.PRAYER_DEADEYE_UNLOCKED, "deadeye",
		VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED, "mystic vigour", VarbitID.PRAYER_ZEAL_UNLOCKED, "zeal",
		VarbitID.TFA_MOTE_AIR_UNLOCKED, "air", VarbitID.TFA_MOTE_WATER_UNLOCKED, "water",
		VarbitID.TFA_MOTE_EARTH_UNLOCKED, "earth", VarbitID.TFA_MOTE_FIRE_UNLOCKED, "fire");
	/** Every unlock, which an account seen before its first login is assumed to have. */
	static final Set<String> GATED = gated();
	/** Knight Waves training ground state once all eight knights are defeated. */
	private static final int KNIGHT_WAVES_COMPLETE = 8;

	private PrayerUnlocks()
	{
	}

	/** Whether a varbit records one of the unlocks. */
	static boolean isUnlockVarbit(int varbitId)
	{
		return varbitId == VarbitID.KR_KNIGHTWAVES_STATE || FLAGS.containsKey(varbitId);
	}

	private static Set<String> gated()
	{
		Set<String> gated = new TreeSet<>(FLAGS.values());
		gated.addAll(Arrays.asList("piety", "chivalry"));
		return Collections.unmodifiableSet(gated);
	}

	/** The unlocks the logged-in account has. Client thread only. */
	static Set<String> read(Client client)
	{
		Set<String> unlocked = new TreeSet<>();
		if (client.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE) >= KNIGHT_WAVES_COMPLETE)
		{
			unlocked.add("chivalry");
			unlocked.add("piety");
		}
		FLAGS.forEach((varbit, name) ->
		{
			if (client.getVarbitValue(varbit) == 1)
				unlocked.add(name);
		});
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
