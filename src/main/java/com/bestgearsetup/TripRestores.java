package com.bestgearsetup;

import com.bestgearsetup.calc.PrayerDrain;
import com.bestgearsetup.calc.SetupResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;
import net.runelite.api.gameval.ItemID;

/**
 * Prayer restore bottles for a trip to a non-raid boss: the kills' prayer drain ({@link SearchResults#prayerDrainFor})
 * beyond the full points the player arrives with, as four-dose bottles. Super restores lead when any dose is owned,
 * then Sanfew serums, otherwise prayer potions; the other two families follow as alternatives, so the bank cell shows
 * whichever the player has. They are listed with the trip's boosts, so the inventory and bank show them the same way.
 */
final class TripRestores
{
	static final String PRAYER_POTION = "prayer potion";
	static final String SUPER_RESTORE = "super restore";
	static final String SANFEW_SERUM = "sanfew serum";

	private TripRestores()
	{
	}

	/** One entry per bottle; empty without a trip, when the setup prays nothing that drains, or when the starting points cover it. */
	static List<RaidPlan.InventoryItem> trip(SearchResults result, SetupResult setup, int kills, IntPredicate owns)
	{
		PrayerDrain.Estimate estimate = kills > 0 ? result.prayerDrainFor(setup) : null;
		if (estimate == null) { return Collections.emptyList(); }
		List<Integer> restores = BoostAccess.doseIds(ItemID._4DOSE2RESTORE);
		List<Integer> sanfews = BoostAccess.doseIds(ItemID.SANFEW_SALVE_4_DOSE);
		List<Integer> prayers = BoostAccess.doseIds(ItemID._4DOSEPRAYERRESTORE);
		boolean superRestore = restores.stream().anyMatch(owns::test);
		boolean sanfew = !superRestore && sanfews.stream().anyMatch(owns::test);
		int level = result.getPrayerLevel();
		int perDose = superRestore || sanfew ? PrayerDrain.superRestoreDose(level) : PrayerDrain.prayerPotionDose(level);
		int doses = estimate.dosesFor(kills, perDose, level);
		if (doses == 0) { return Collections.emptyList(); }
		int bottles = (doses + 3) / 4;
		// The cell's own family first; the bank fills the cell with an alternative family only in its absence.
		List<Integer> ids = new ArrayList<>();
		if (superRestore) { ids.addAll(restores); ids.addAll(sanfews); ids.addAll(prayers); }
		else if (sanfew) { ids.addAll(sanfews); ids.addAll(restores); ids.addAll(prayers); }
		else { ids.addAll(prayers); ids.addAll(restores); ids.addAll(sanfews); }
		RaidPlan.InventoryItem item = new RaidPlan.InventoryItem(superRestore ? SUPER_RESTORE : sanfew ? SANFEW_SERUM : PRAYER_POTION,
			Collections.unmodifiableList(ids), false, Collections.emptyList(), RaidPlan.InventoryGroup.BOOSTS, doses, bottles, false);
		List<RaidPlan.InventoryItem> out = new ArrayList<>();
		for (int bottle = 0; bottle < bottles; bottle++) { out.add(item); }
		return out;
	}
}
