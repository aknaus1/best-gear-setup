package com.bestgearsetup;

import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.NameLists;
import com.bestgearsetup.data.OffensivePrayer;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import lombok.Value;
import net.runelite.api.gameval.ItemID;

/**
 * What a trip to a target outside the raids carries besides its gear, for the Kills per trip setting: each boost's
 * bottles and the prayer restores its kills need. A kill lasts the target's hitpoints over the setup's DPS, as the TTK
 * line shows. Prayer is assumed to be the setup's offensive prayer and one protection prayer, kept on throughout.
 */
public final class TripSupplies
{
	static final String SUPER_RESTORE = "Super restore";
	/** Drain effect of a protection prayer, assumed on beside the offensive prayer (Wiki: Prayer). */
	static final int PROTECTION_DRAIN = 12;
	/** Ticks a prayer point lasts per unit of drain effect before any prayer bonus. */
	private static final int BASE_RESISTANCE = 60;
	private static final Map<String, Integer> DRAIN_EFFECTS = NameLists.exact("prayer drain");

	private TripSupplies()
	{
	}

	/** One kind of carried supply: its bank ids (its own first, then alternatives) and how many to bring. */
	@Value
	public static class Supply
	{
		String name;
		List<Integer> ids;
		int count;
	}

	/** Seconds one kill takes: the target's hitpoints over the setup's DPS; infinite when it deals no damage. */
	public static double killSeconds(SetupResult setup, SearchResults results)
	{
		double dps = setup.getDps() == null ? 0 : setup.getDps().getPrimaryDps();
		return dps > 0 ? results.getStartingHitpoints() / dps : Double.POSITIVE_INFINITY;
	}

	/**
	 * Prayer points one kill drains. Every tick the active prayers' drain effects add up toward a resistance of
	 * {@code 2 × prayer bonus + 60}, and each time it is reached a point is lost (Wiki: Prayer); 0 when the kill
	 * never ends.
	 */
	public static double prayerPerKill(SetupResult setup, SearchResults results)
	{
		OffensivePrayer offensive = results.getPrayers() == null ? null : results.getPrayers().get(setup.getCombatClass());
		int drain = PROTECTION_DRAIN
			+ (offensive == null ? 0 : DRAIN_EFFECTS.getOrDefault(offensive.getName().toLowerCase(Locale.ROOT), 0));
		int bonus = 0;
		for (Slot slot : Slot.values())
			bonus += setup.getLoadout().get(slot) == null ? 0 : setup.getLoadout().get(slot).getPrayerBonus();
		double seconds = killSeconds(setup, results);
		return Double.isFinite(seconds) ? seconds * drain / ((2.0 * bonus + BASE_RESISTANCE) * 0.6) : 0;
	}

	/**
	 * The supplies {@code kills} kills need: each boost's bottles, then prayer restores. Empty for raid targets, whose
	 * supplies come from the raid.
	 */
	public static List<Supply> plan(SetupResult setup, SearchResults results, List<Potion> potions, int kills,
		IntPredicate owns)
	{
		List<Supply> out = new ArrayList<>();
		if (setup == null || results.getMonster() == null || RaidScaling.raid(results.getMonster()) != RaidScaling.Raid.NONE)
			return out;
		PlayerLevels levels = results.getLevels() == null ? PlayerLevels.maxed() : results.getLevels();
		double seconds = killSeconds(setup, results) * kills;
		for (Potion potion : potions)
			if (!potion.isRaidSupply())
				out.add(new Supply(GameData.titleCase(potion.getName()), new ArrayList<>(BoostAccess.itemIds(potion)),
					boostBottles(potion, level(levels, potion.getSkill()), seconds)));
		addRestores(out, prayerPerKill(setup, results) * kills, levels.getPrayer(), owns);
		return out;
	}

	/**
	 * Bottles of a boost for {@code seconds} of combat. A divine dose holds its boost for five minutes; an ordinary one
	 * is drunk again once half its boost has drained, a level a minute, the first after half a minute on average. A
	 * heart is carried once.
	 */
	static int boostBottles(Potion potion, int level, double seconds)
	{
		String name = potion.getName().toLowerCase(Locale.ROOT);
		int boost = potion.boost(level) - level;
		if (name.contains("heart") || boost <= 0 || !Double.isFinite(seconds))
			return 1;
		double doseSeconds = name.startsWith("divine") ? 300 : (boost / 2 + 0.5) * 60;
		int doses = Math.max(1, (int) Math.ceil(seconds / doseSeconds));
		return (doses + 3) / 4;
	}

	private static int level(PlayerLevels levels, String skill)
	{
		switch (skill == null ? "" : skill.toLowerCase(Locale.ROOT))
		{
			case "attack":
				return levels.getAttack();
			case "strength":
				return levels.getStrength();
			case "defence":
				return levels.getDefence();
			case "ranged":
				return levels.getRanged();
			default:
				return levels.getMagic();
		}
	}

	/**
	 * Restore bottles for the {@code points} beyond the full {@code level} the trip starts with: super restores
	 * ({@code 8 + level / 4} a dose) when the player owns them or Sanfew serums, otherwise prayer potions
	 * ({@code 7 + level / 4}). The other restores follow as bank alternatives.
	 */
	static void addRestores(List<Supply> out, double points, int level, IntPredicate owns)
	{
		List<Integer> restores = BoostAccess.doseIds(ItemID._4DOSE2RESTORE);
		List<Integer> sanfews = BoostAccess.doseIds(ItemID.SANFEW_SALVE_4_DOSE);
		List<Integer> prayers = BoostAccess.doseIds(ItemID._4DOSEPRAYERRESTORE);
		boolean superRestore = restores.stream().anyMatch(owns::test) || sanfews.stream().anyMatch(owns::test);
		int doses = (int) Math.ceil((points - level) / ((superRestore ? 8 : 7) + level / 4));
		if (doses <= 0)
			return;
		List<Integer> ids = new ArrayList<>(superRestore ? restores : prayers);
		ids.addAll(sanfews);
		ids.addAll(superRestore ? prayers : restores);
		out.add(new Supply(superRestore ? SUPER_RESTORE : "Prayer potion", ids, (doses + 3) / 4));
	}

	/** The supplies by count and name, e.g. "2 Super combat potion, 3 Super restore". */
	public static String summary(List<Supply> supplies)
	{
		return supplies.stream().map(s -> s.getCount() + " " + s.getName()).collect(Collectors.joining(", "));
	}
}
