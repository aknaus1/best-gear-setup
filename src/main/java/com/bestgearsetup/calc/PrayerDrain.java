package com.bestgearsetup.calc;

import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Value;

/**
 * Prayer points a fight costs and the restore doses that pay for them. Each active prayer has a drain effect
 * (Wiki: Prayer, "Drain effect"); every tick their sum is taken from a counter, and each time the counter runs
 * past the drain resistance of {@code 2 × prayer bonus + 60} one point is lost. So the prayers together lose one
 * point every {@code (2 × bonus + 60) / total drain} ticks: Piety with Protect from Melee at +0 bonus costs a
 * point a second, and at +30 one every two seconds. A prayer potion dose restores {@code 7 + level / 4} points
 * and a super restore or Sanfew serum {@code 8 + level / 4}, at the player's base Prayer level.
 */
public final class PrayerDrain
{
	/** Ticks a prayer point lasts per unit of drain effect before any bonus. */
	private static final int BASE_RESISTANCE = 60;
	private static final Map<String, Integer> DRAIN_EFFECTS = new HashMap<>();

	static
	{
		// Offensive prayers in the bundled data and the overheads the advice recommends, lower-case Wiki names.
		drain(3, "burst of strength", "clarity of thought", "sharp eye", "mystic will");
		drain(6, "superhuman strength", "improved reflexes", "hawk eye", "mystic lore");
		drain(12, "ultimate strength", "incredible reflexes", "eagle eye", "mystic might",
			"protect from magic", "protect from missiles", "protect from melee");
		drain(24, "chivalry", "piety", "rigour", "deadeye", "augury", "mystic vigour");
	}

	private PrayerDrain()
	{
	}

	private static void drain(int effect, String... names)
	{
		for (String name : names) { DRAIN_EFFECTS.put(name, effect); }
	}

	/** The prayer's drain effect per tick, or 0 for a name the table doesn't know. */
	public static int drainEffect(String prayerName)
	{
		Integer effect = prayerName == null ? null : DRAIN_EFFECTS.get(prayerName.toLowerCase(Locale.ROOT));
		return effect == null ? 0 : effect;
	}

	/** The worn equipment's prayer bonus. */
	public static int prayerBonus(Loadout loadout)
	{
		int bonus = 0;
		for (Slot slot : Slot.values())
		{
			GearItem item = loadout.get(slot);
			if (item != null) { bonus += item.getPrayerBonus(); }
		}
		return bonus;
	}

	/** Seconds one prayer point lasts under this total drain effect at this prayer bonus; infinite without drain. */
	public static double secondsPerPoint(int totalDrain, int prayerBonus)
	{
		if (totalDrain <= 0) { return Double.POSITIVE_INFINITY; }
		return (2.0 * prayerBonus + BASE_RESISTANCE) / totalDrain * 0.6;
	}

	/** Points one prayer potion dose restores at this base Prayer level. */
	public static int prayerPotionDose(int prayerLevel)
	{
		return 7 + prayerLevel / 4;
	}

	/** Points one super restore (or Sanfew serum) dose restores at this base Prayer level. */
	public static int superRestoreDose(int prayerLevel)
	{
		return 8 + prayerLevel / 4;
	}

	/**
	 * The drain of these prayers kept on for {@code secondsPerKill} of fighting with {@code prayerBonus} worn, or
	 * null when none of them drains (unknown names count as nothing) or the kill never ends.
	 */
	public static Estimate estimate(List<String> prayerNames, int prayerBonus, double secondsPerKill)
	{
		List<String> draining = new ArrayList<>();
		int total = 0;
		for (String name : prayerNames)
		{
			int effect = drainEffect(name);
			if (effect > 0 && !draining.contains(name))
			{
				draining.add(name);
				total += effect;
			}
		}
		if (total == 0 || !(secondsPerKill > 0) || Double.isInfinite(secondsPerKill)) { return null; }
		return new Estimate(Collections.unmodifiableList(draining), total, prayerBonus, secondsPerKill);
	}

	/** Prayer points one kill costs with a set of prayers on throughout. */
	@Value
	public static class Estimate
	{
		/** The prayers counted, as named by the search. */
		List<String> prayers;
		/** Their drain effects added together. */
		int totalDrain;
		int prayerBonus;
		double secondsPerKill;

		public double getSecondsPerPoint()
		{
			return secondsPerPoint(totalDrain, prayerBonus);
		}

		public double getPointsPerMinute()
		{
			return 60 / getSecondsPerPoint();
		}

		public double getPointsPerKill()
		{
			return secondsPerKill / getSecondsPerPoint();
		}

		/** Doses restoring {@code pointsPerDose} each that one kill's drain uses up. */
		public double dosesPerKill(int pointsPerDose)
		{
			return pointsPerDose <= 0 ? Double.POSITIVE_INFINITY : getPointsPerKill() / pointsPerDose;
		}

		/** Whole doses restoring {@code pointsPerDose} each that {@code kills} kills need, after the points started with. */
		public int dosesFor(int kills, int pointsPerDose, int startingPoints)
		{
			double needed = getPointsPerKill() * kills - startingPoints;
			return needed <= 0 || pointsPerDose <= 0 ? 0 : (int) Math.ceil(needed / pointsPerDose);
		}
	}
}
