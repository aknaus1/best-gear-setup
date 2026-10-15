package com.bestgearsetup.calc;

import com.bestgearsetup.data.Slot;
import java.util.Comparator;

/**
 * A raid freezer's duty (ToB Maiden's nylocas): every Ice Barrage should land, since a splash misses the freeze. The
 * freeze is a switch from the setup worn for the fight, and the best one swaps the fewest items that land each cast
 * at least as often as the target: {@link #GUARANTEED}, which Maiden's nylocas allow with enough magic attack
 * ({@link DpsCalculator#guaranteedFreeze}). When no switch reaches the target, accuracy comes first.
 */
public final class FreezeDuty
{
	/** A freezer's target: every freeze lands. */
	public static final double GUARANTEED = 1;

	/** Slack for comparing accuracies, so a switch exactly at the target reaches it. */
	private static final double EPS = 1e-9;

	private FreezeDuty()
	{
	}

	/** Whether the setup lands each cast at least {@code target} (0..1) of the time. */
	public static boolean reaches(SetupResult setup, double target)
	{
		return setup.getDps().getAccuracy() >= target - EPS;
	}

	/** A freeze equips switches and keeps other worn gear; only a two-handed weapon removes its shield automatically. */
	public static boolean keepsWornGear(Loadout freeze, Loadout worn)
	{
		for (Slot slot : Slot.values())
		{
			if (worn.get(slot) != null && freeze.get(slot) == null
				&& !(slot == Slot.SHIELD && freeze.getWeapon() != null && freeze.getWeapon().isTwoHanded()))
			{ return false; }
		}
		return true;
	}

	/** Items the setup puts on: those a switch swaps from its worn setup (weapon included), or every worn item. */
	public static int items(SetupResult setup)
	{
		Loadout loadout = setup.getLoadout();
		if (setup.getSwitchFrom() != null) { return loadout.switchesFrom(setup.getSwitchFrom()); }
		int worn = 0;
		for (Slot slot : Slot.values()) { if (loadout.get(slot) != null) { worn++; } }
		return worn;
	}

	/** Whether each freeze is cast by hand, the weapon being unable to autocast the spell. */
	public static boolean manualCast(SetupResult setup)
	{
		AttackStyle style = setup.getLoadout().getStyle();
		return style != null && style.isManualCast();
	}

	/**
	 * How much a freeze setup asks of the player, fewer being better: its items, with casting each freeze by hand
	 * weighing more than any switch, so an autocast setup is preferred whenever one does the job.
	 */
	public static int burden(SetupResult setup)
	{
		return items(setup) + (manualCast(setup) ? Slot.values().length + 1 : 0);
	}

	/**
	 * Best first: setups reaching {@code target} before those that don't; among those reaching it, autocasting, then
	 * fewer items, then higher accuracy; below it, higher accuracy, then autocasting, then fewer items. Remaining ties
	 * keep the higher DPS.
	 */
	public static Comparator<SetupResult> preference(double target)
	{
		return (a, b) ->
		{
			boolean reachesA = reaches(a, target);
			boolean reachesB = reaches(b, target);
			if (reachesA != reachesB) { return reachesA ? -1 : 1; }
			int burden = Integer.compare(burden(a), burden(b));
			int accuracy = Double.compare(b.getDps().getAccuracy(), a.getDps().getAccuracy());
			int first = reachesA ? burden : accuracy;
			int second = reachesA ? accuracy : burden;
			if (first != 0) { return first; }
			if (second != 0) { return second; }
			return Double.compare(b.getDps().getPrimaryDps(), a.getDps().getPrimaryDps());
		};
	}
}
