package com.bestgearsetup;

import com.bestgearsetup.calc.Overheads;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SpecialPlan;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.NameLists;
import com.bestgearsetup.data.OffensivePrayer;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;
import net.runelite.api.gameval.ItemID;

/**
 * What a trip to a target outside the raids carries besides its gear, for the Kills per trip setting: each boost's
 * bottles, the prayer restores its kills need, an antipoison or antivenom against a target that poisons or envenoms,
 * and (Fill inventory with food) food in the inventory slots left over. A kill lasts the target's hitpoints over the setup's DPS, as the TTK
 * line shows. Prayer is the setup's offensive prayer and the target's recommended overhead ({@link Overheads}), kept on
 * throughout; a target without advice is assumed to need one.
 */
public final class TripSupplies
{
	static final String SUPER_RESTORE = "Super restore";
	/** Drain effect of a protection prayer, kept on beside the offensive prayer (Wiki: Prayer). */
	static final int PROTECTION_DRAIN = 12;
	/** Ticks a prayer point lasts per unit of drain effect before any prayer bonus. */
	private static final int BASE_RESISTANCE = 60;
	private static final Map<String, Integer> DRAIN_EFFECTS = NameLists.exact("prayer drain");
	/** One-bite foods as [name, item id], best heal first. */
	private static final JsonArray FOODS = NameLists.rows("trip foods");
	static final int INVENTORY_SLOTS = 28;
	/** Fewest free slots that get a mix of foods; fewer hold the main food alone. */
	static final int MIX_SLOTS = 4;
	/** Karambwans and brews each take at most one in this many free slots, so most of the mix stays one-bite food. */
	static final int MIX_SHARE = 4;
	/** Most other owned foods the bank layout offers in the main food's place. */
	static final int ALTERNATIVES = 3;

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
		int drain = (needsOverhead(setup, results) ? PROTECTION_DRAIN : 0)
			+ (offensive == null ? 0 : DRAIN_EFFECTS.getOrDefault(offensive.getName().toLowerCase(Locale.ROOT), 0));
		int bonus = 0;
		for (Slot slot : Slot.values())
			bonus += setup.getLoadout().get(slot) == null ? 0 : setup.getLoadout().get(slot).getPrayerBonus();
		double seconds = killSeconds(setup, results);
		return Double.isFinite(seconds) ? seconds * drain / ((2.0 * bonus + BASE_RESISTANCE) * 0.6) : 0;
	}

	/**
	 * Whether a protection prayer drains during the kill: the target's advice names one the Prayer level allows, or the
	 * target has no advice. A switching fight keeps one on at a time.
	 */
	static boolean needsOverhead(SetupResult setup, SearchResults results)
	{
		Overheads.Advice advice = Overheads.recommend(results.getMonster(), setup.getCombatClass());
		if (advice == null)
			return true;
		PlayerLevels levels = results.getLevels() == null ? PlayerLevels.maxed() : results.getLevels();
		return advice.getPrayer() != null && advice.getPrayer().getLevel() <= levels.getPrayer();
	}

	/**
	 * The supplies {@code kills} kills need: each boost's bottles, prayer restores, a bottle of cure against a target
	 * that poisons, then with {@code food} food in the free slots. Empty for raid targets, whose supplies come from
	 * the raid.
	 */
	public static List<Supply> plan(SetupResult setup, SearchResults results, List<Potion> potions, int kills,
		boolean food, IntPredicate owns)
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
		// Anti-venom+ also cures poison; a superantipoison is the usual bottle against poison alone.
		String poisons = results.getMonster().getPoisons();
		if ("venom".equals(poisons))
			out.add(new Supply("Anti-venom+", doses(ItemID.ANTIVENOM_4, ItemID.ANTIVENOM4), 1));
		else if ("poison".equals(poisons))
			out.add(new Supply("Superantipoison", doses(ItemID._4DOSE2ANTIPOISON, ItemID.ANTIDOTE__4, ItemID.ANTIVENOM_4), 1));
		if (food)
			addFood(out, INVENTORY_SLOTS - usedSlots(setup, out), owns);
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

	private static List<Integer> doses(int... fourDoses)
	{
		List<Integer> ids = new ArrayList<>();
		for (int fourDose : fourDoses)
			ids.addAll(BoostAccess.doseIds(fourDose));
		return ids;
	}

	/** Slots taken before food: a spec switch's items, a rune pouch and spell items beyond its three, and each bottle. */
	static int usedSlots(SetupResult setup, List<Supply> supplies)
	{
		int used = supplies.stream().mapToInt(Supply::getCount).sum();
		int spellItems = SpellRunes.required(setup).size();
		if (spellItems > 0)
			used += 1 + Math.max(0, spellItems - 3);
		SpecialPlan special = setup.getSpecial();
		if (special != null && special.isSwitch(setup.getLoadout()))
			used += Stream.of(special.getWeapon(), special.getOffhand(), special.getAmmo()).filter(Objects::nonNull).count();
		return used;
	}

	/**
	 * Food for {@code free} slots: the best one-bite food the player owns (sharks if none), with up to
	 * {@link #ALTERNATIVES} other owned foods the bank may show in its place. With {@link #MIX_SLOTS} or more free,
	 * up to one in {@link #MIX_SHARE} slots each of owned cooked karambwans, eaten in the same tick, and owned
	 * Saradomin brews, at most one per super restore bottle, which restores the stats a brew drains.
	 */
	static void addFood(List<Supply> out, int free, IntPredicate owns)
	{
		if (free <= 0)
			return;
		String main = "Shark";
		List<Integer> ids = new ArrayList<>();
		for (JsonElement row : FOODS)
		{
			int id = row.getAsJsonArray().get(1).getAsInt();
			if (id == ItemID.TBWT_COOKED_KARAMBWAN || !owns.test(id) || ids.size() > ALTERNATIVES)
				continue;
			if (ids.isEmpty())
				main = row.getAsJsonArray().get(0).getAsString();
			ids.add(id);
		}
		if (ids.isEmpty())
			ids.add(ItemID.SHARK);
		boolean mix = free >= MIX_SLOTS;
		int karambwans = mix && owns.test(ItemID.TBWT_COOKED_KARAMBWAN) ? free / MIX_SHARE : 0;
		int restores = out.stream().filter(s -> s.getName().equals(SUPER_RESTORE)).mapToInt(Supply::getCount).sum();
		List<Integer> brewIds = BoostAccess.doseIds(ItemID._4DOSEPOTIONOFSARADOMIN);
		int brews = mix && brewIds.stream().anyMatch(owns::test) ? Math.min(free / MIX_SHARE, restores) : 0;
		out.add(new Supply(main, ids, free - karambwans - brews));
		if (karambwans > 0)
			out.add(new Supply("Cooked karambwan", Collections.singletonList(ItemID.TBWT_COOKED_KARAMBWAN), karambwans));
		if (brews > 0)
			out.add(new Supply("Saradomin brew", brewIds, brews));
	}

	/** The supplies by count and name, e.g. "2 Super combat potion, 3 Super restore". */
	public static String summary(List<Supply> supplies)
	{
		return supplies.stream().map(s -> s.getCount() + " " + s.getName()).collect(Collectors.joining(", "));
	}
}
