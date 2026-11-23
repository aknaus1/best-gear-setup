package com.bestgearsetup;

import com.bestgearsetup.calc.*;
import com.bestgearsetup.data.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.*;
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
 * throughout; a target without advice needs none.
 */
public final class TripSupplies
{
	static final String SUPER_RESTORE = "Super restore";
	static final String SANFEW = "Sanfew serum";
	/** Drain effect of a protection prayer, kept on beside the offensive prayer (Wiki: Prayer). */
	static final int PROTECTION_DRAIN = 12;
	/** Ticks a prayer point lasts per unit of drain effect before any prayer bonus. */
	private static final int BASE_RESISTANCE = 60;
	private static final Map<String, Integer> DRAIN_EFFECTS = Names.exact("prayer drain");
	/** Doses per item of boosts that aren't four-dose bottles. */
	private static final Map<String, Integer> BOOST_DOSES = Names.exact("boost doses");
	/** One-bite foods as [name, item id], best heal first. */
	private static final JsonArray FOODS = Names.rows("trip foods");
	/** Wave and challenge fights (Fight Caves, Inferno, Colosseum, Gauntlet), where one run is the trip. */
	private static final Names.NameList ONE_RUN = Names.list("one-run fights");
	public static final int INVENTORY_SLOTS = 28;
	/** Fewest free slots that get a mix of foods; fewer hold the main food alone. */
	static final int MIX_SLOTS = 4;
	/** Karambwans and brews each take at most one in this many free slots, so most of the mix stays one-bite food. */
	static final int MIX_SHARE = 4;
	/** A max hit from which karambwans are mixed in for combo eating: one hit can outdo a shark's 20 healing. */
	static final int COMBO_MAX_HIT = 20;
	/** Food packed in case of a mistake when protection blocks every attack, instead of filling the inventory. */
	static final int EMERGENCY_FOOD = 3;
	/** Attack styles a protection from melee blocks, magical and ranged melee included (Wiki). */
	private static final Set<String> MELEE_STYLES = new HashSet<>(Arrays.asList("crush", "slash", "stab", "melee",
		"magical melee", "ranged melee"));
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

	/** Whether {@code monster} is fought in a wave or challenge run, which Kills per trip doesn't repeat. */
	public static boolean oneRun(Monster monster)
	{
		return monster != null && ONE_RUN.matches(monster.getLowerName());
	}

	/** The Kills per trip setting for {@code monster}: 1 for a {@link #oneRun} fight. */
	public static int kills(Monster monster, int setting)
	{
		return oneRun(monster) ? 1 : setting;
	}

	/** Seconds one kill takes: the target's hitpoints over the setup's DPS; infinite when it deals no damage. */
	public static double killSeconds(Setup setup, SearchResults results)
	{
		double dps = setup.getDps() == null ? 0 : setup.getDps().getPrimaryDps();
		return dps > 0 ? results.getStartingHitpoints() / dps : Double.POSITIVE_INFINITY;
	}

	/**
	 * Prayer points one kill drains. Every tick the active prayers' drain effects add up toward a resistance of
	 * {@code 2 × prayer bonus + 60}, and each time it is reached a point is lost (Wiki: Prayer); 0 when the kill
	 * never ends.
	 */
	public static double prayerPerKill(Setup setup, SearchResults results)
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
	 * Whether a protection prayer drains during the kill: only when the target's advice names one the Prayer level
	 * allows. A target that needs none, or that praying doesn't help against or has no advice, drains none. A switching
	 * fight keeps one on at a time.
	 */
	static boolean needsOverhead(Setup setup, SearchResults results)
	{
		Overheads.Advice advice = Overheads.recommend(results.getMonster(), setup.getCombatClass());
		if (advice == null)
			return false;
		PlayerLevels levels = results.getLevels() == null ? PlayerLevels.maxed() : results.getLevels();
		return advice.getPrayer() != null && advice.getPrayer().getLevel() <= levels.getPrayer();
	}

	/** Whether a trip to {@code monster} withdraws this boost: never raid supplies, NMZ potions only in Nightmare Zone. */
	public static boolean takes(Potion potion, Monster monster)
	{
		return !potion.isRaidSupply() && (!potion.getAttributes().contains("nmz")
			|| monster.getLowerName().contains("(nightmare zone"));
	}

	/**
	 * The supplies {@code kills} kills need: each boost's bottles, the {@code antifire} a dragon's breath needs
	 * ({@link Antifire#NONE} when fire protection isn't required), prayer restores, a bottle of cure against a target
	 * that poisons, then with {@code food} food in the free slots. Empty for raid targets, whose supplies come from
	 * the raid.
	 */
	public static List<Supply> plan(Setup setup, SearchResults results, List<Potion> potions, int kills,
		boolean food, IntPredicate owns, Antifire antifire)
	{
		return plan(setup == null ? null : Kit.of(setup), results, potions, kills, food, owns, antifire);
	}

	/** The supplies for the kit's setup, with food only in the slots the kit leaves. */
	public static List<Supply> plan(Kit kit, SearchResults results, List<Potion> potions, int kills,
		boolean food, IntPredicate owns, Antifire antifire)
	{
		Setup setup = kit == null ? null : kit.setup;
		List<Supply> out = new ArrayList<>();
		if (setup == null || results.getMonster() == null || RaidScaling.raid(results.getMonster()) != RaidScaling.Raid.NONE)
			return out;
		PlayerLevels levels = results.getLevels() == null ? PlayerLevels.maxed() : results.getLevels();
		kills = kills(results.getMonster(), kills);
		double seconds = killSeconds(setup, results) * kills;
		for (Potion potion : potions)
			if (takes(potion, results.getMonster()))
				out.add(new Supply(GameData.titleCase(potion.getName()), new ArrayList<>(BoostAccess.itemIds(potion)),
					boostBottles(potion, level(levels, potion.getSkill()), seconds)));
		// A dose of antifire lasts 6 minutes, of super antifire 3 (Wiki); the extended potion is its alternative.
		if (antifire != Antifire.NONE && DragonfireProtection.takesPotion(results.getMonster()))
		{
			boolean superPotion = antifire == Antifire.SUPER;
			int doses = Double.isFinite(seconds) ? Math.max(1, (int) Math.ceil(seconds / (superPotion ? 180 : 360))) : 1;
			out.add(new Supply(antifire.toString(), superPotion ? doses(ItemID._4DOSE3ANTIDRAGON, ItemID._4DOSE4ANTIDRAGON)
				: doses(ItemID._4DOSE1ANTIDRAGON, ItemID._4DOSE2ANTIDRAGON), (doses + 3) / 4));
		}
		addRestores(out, prayerPerKill(setup, results) * kills, levels.getPrayer(), owns);
		// Anti-venom+ also cures poison; a superantipoison is the usual bottle against poison alone. A serpentine helm
		// makes the wearer immune to both.
		String poisons = StatusEffects.serpentineHelm(setup.getLoadout()) ? null : results.getMonster().getPoisons();
		if ("venom".equals(poisons))
			out.add(new Supply("Anti-venom+", doses(ItemID.ANTIVENOM_4, ItemID.ANTIVENOM4), 1));
		else if ("poison".equals(poisons))
			out.add(new Supply("Superantipoison", doses(ItemID._4DOSE2ANTIPOISON, ItemID.ANTIDOTE__4, ItemID.ANTIVENOM_4), 1));
		if (food)
			addFood(out, foodSlots(results.getMonster(), setup, antifire, levels, INVENTORY_SLOTS - usedSlots(kit, out)),
				owns, maxHit(results.getMonster()) >= COMBO_MAX_HIT);
		return out;
	}

	/** The highest max hit of the target's attacks besides dragonfire, which fire protection blocks; -1 without data. */
	static int maxHit(Monster monster)
	{
		Map<String, Monster.MonsterAttack> attacks = monster.getAttackStyles();
		return attacks == null ? -1 : attacks.entrySet().stream().filter(a -> !a.getKey().equals("dragonfire"))
			.mapToInt(a -> a.getValue().getMaxHit()).max().orElse(-1);
	}

	/**
	 * Food slots of the {@code free} ones: none against a target that can't hurt you (its advice says it doesn't attack,
	 * or every attack hits 0), {@link #EMERGENCY_FOOD} when your protection blocks it: an ordinary target (not a boss)
	 * each of whose attacks hits 0 or is blocked: a
	 * melee, magic or ranged attack by the one overhead its advice keeps up (it blocks all of an ordinary monster's
	 * damage, Wiki: Protect from Melee; magical and ranged melee count as melee, magical ranged as ranged), dragonfire by
	 * full fire protection ({@link DragonfireProtection#blocksBreath}), and a metal dragon's melee by standing at range.
	 * A boss, a typeless or unknown attack, a switching fight or missing data fills them all.
	 */
	static int foodSlots(Monster monster, Setup setup, Antifire antifire, PlayerLevels levels, int free)
	{
		Overheads.Advice advice = Overheads.recommend(monster, setup.getCombatClass());
		if (advice != null && advice.getNote().startsWith("It doesn't attack"))
			return 0;
		Map<String, Monster.MonsterAttack> attacks = monster.getAttackStyles();
		if (monster.isBoss() || attacks == null || attacks.isEmpty())
			return free;
		Overheads.Overhead prayer = advice == null || advice.isSwitching() || advice.getPrayer() == null
			|| advice.getPrayer().getLevel() > levels.getPrayer() ? null : advice.getPrayer();
		boolean distant = setup.getCombatClass() != Combat.MELEE && DragonfireProtection.keepsDistance(monster);
		boolean hits = false;
		for (Map.Entry<String, Monster.MonsterAttack> attack : attacks.entrySet())
		{
			String style = attack.getKey();
			Overheads.Overhead blocker = MELEE_STYLES.contains(style) ? Overheads.Overhead.MELEE
				: style.equals("magic") ? Overheads.Overhead.MAGIC
				: style.equals("ranged") || style.equals("magical ranged") ? Overheads.Overhead.MISSILES : null;
			hits |= attack.getValue().getMaxHit() > 0;
			boolean blocked = attack.getValue().getMaxHit() <= 0 || !attack.getValue().isTypeless()
				&& (style.equals("dragonfire") ? DragonfireProtection.blocksBreath(monster, setup.getLoadout(), antifire,
					prayer == Overheads.Overhead.MAGIC) : blocker == Overheads.Overhead.MELEE && distant || blocker != null && blocker == prayer);
			if (!blocked)
				return free;
		}
		return hits ? Math.min(free, EMERGENCY_FOOD) : 0;
	}

	/**
	 * Items of a boost for {@code seconds} of combat. A dose is drunk again once half its boost has drained, a level a
	 * minute, the first after half a minute on average; a divine dose counts as an ordinary one. Each item holds four
	 * doses unless {@code name-lists.json} ("boost doses") says otherwise: two for a wild pie, one for a butterfly jar. A heart is
	 * carried once.
	 */
	static int boostBottles(Potion potion, int level, double seconds)
	{
		String name = potion.getName().toLowerCase(Locale.ROOT);
		int boost = potion.boost(level) - level;
		if (name.contains("heart") || boost <= 0 || !Double.isFinite(seconds))
			return 1;
		int doses = Math.max(1, (int) Math.ceil(seconds / ((boost / 2 + 0.5) * 60)));
		int perItem = BOOST_DOSES.getOrDefault(name, 4);
		return (doses + perItem - 1) / perItem;
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
	 * ({@code 8 + level / 4} a dose) when the player owns them, else Sanfew serums ({@code 4 + level × 3 / 10}) when
	 * owned, otherwise prayer potions ({@code 7 + level / 4}) (Wiki). The other restores follow as bank alternatives.
	 */
	static void addRestores(List<Supply> out, double points, int level, IntPredicate owns)
	{
		List<Integer> restores = BoostAccess.doseIds(ItemID._4DOSE2RESTORE);
		List<Integer> sanfews = BoostAccess.doseIds(ItemID.SANFEW_SALVE_4_DOSE);
		List<Integer> prayers = BoostAccess.doseIds(ItemID._4DOSEPRAYERRESTORE);
		boolean superRestore = restores.stream().anyMatch(owns::test);
		boolean sanfew = !superRestore && sanfews.stream().anyMatch(owns::test);
		int perDose = superRestore ? 8 + level / 4 : sanfew ? 4 + level * 3 / 10 : 7 + level / 4;
		int doses = (int) Math.ceil((points - level) / perDose);
		if (doses <= 0)
			return;
		List<Integer> ids = new ArrayList<>(superRestore ? restores : sanfew ? sanfews : prayers);
		ids.addAll(superRestore ? sanfews : restores);
		ids.addAll(superRestore || sanfew ? prayers : sanfews);
		out.add(new Supply(superRestore ? SUPER_RESTORE : sanfew ? SANFEW : "Prayer potion", ids, (doses + 3) / 4));
	}

	private static List<Integer> doses(int... fourDoses)
	{
		List<Integer> ids = new ArrayList<>();
		for (int fourDose : fourDoses)
			ids.addAll(BoostAccess.doseIds(fourDose));
		return ids;
	}

	/** Slots taken before food: a spec switch's items and a shield a two-handed spec weapon takes off, a rune pouch and spell items beyond its three, and each bottle. */
	public static int usedSlots(Setup setup, List<Supply> supplies)
	{
		return usedSlots(Kit.of(setup), supplies);
	}

	public static int usedSlots(Kit kit, List<Supply> supplies)
	{
		int spellItems = kit.spellItems.size();
		return supplies.stream().mapToInt(Supply::getCount).sum() + kit.carried.size() + kit.room
			+ (spellItems > 0 ? 1 + Math.max(0, spellItems - 3) : 0);
	}

	/**
	 * What the inventory and bank layout hold besides supplies: the worn setup, the switch items carried, free slots for
	 * the items they take off, ammunition loaded into carried weapons (no slot of its own) and the spell items.
	 */
	@Value
	public static class Kit
	{
		Setup setup;
		List<Gear> carried;
		int room;
		List<Gear> loaded;
		List<Integer> spellItems;

		/** One setup's: its spec weapon switch and its spells. */
		public static Kit of(Setup setup)
		{
			SpecialPlan special = setup.getSpecial();
			Outfit worn = setup.getLoadout();
			boolean switches = special != null && special.isSwitch(worn);
			return new Kit(setup, !switches ? List.of() : Stream.of(special.getWeapon() == worn.getWeapon() ? null
				: special.getWeapon(), special.getOffhand(), special.getAmmo()).filter(Objects::nonNull).collect(Collectors.toList()),
				takesShieldOff(setup) ? 1 : 0, switches && special.getLoadedAmmo() != null ? List.of(special.getLoadedAmmo())
				: List.of(), SpellRunes.required(setup));
		}

		/** A whole fight's: the plan's switches and spec weapons, and the items for every spell a phase casts. */
		public static Kit of(FightPlan.Plan plan, Setup worn)
		{
			Set<Integer> spells = new LinkedHashSet<>();
			Set<Gear> loaded = new LinkedHashSet<>();
			// The plan casts from one spellbook; the worn setup's support spells of another book are left out.
			String book = plan.outfits().stream().map(Outfit::getSpell).filter(Objects::nonNull).map(Spell::getSpellbook)
				.findFirst().orElse(null);
			Set<SupportSpell> support = worn.getSupportSpells().stream().filter(spell -> book == null
				|| book.equalsIgnoreCase(spell == SupportSpell.CHARGE ? "standard" : "arceuus"))
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(SupportSpell.class)));
			for (Outfit outfit : plan.outfits())
			{
				spells.addAll(SpellRunes.required(outfit, support, worn.getThrall()));
				if (outfit.getLoadedAmmo() != null && !outfit.getLoadedAmmo().equals(plan.getWorn().getLoadedAmmo()))
					loaded.add(outfit.getLoadedAmmo());
			}
			return new Kit(worn, plan.getCarried(), plan.getSlots() - plan.getCarried().size(), new ArrayList<>(loaded),
				new ArrayList<>(spells));
		}
	}

	/** Whether a two-handed spec weapon switch takes the worn shield off into the inventory. */
	static boolean takesShieldOff(Setup setup)
	{
		SpecialPlan special = setup.getSpecial();
		Gear weapon = special == null ? null : special.getWeapon();
		return weapon != null && special.isSwitch(setup.getLoadout()) && weapon.isTwoHanded() && special.getOffhand() == null
			&& setup.getLoadout().get(Slot.SHIELD) != null && weapon != setup.getLoadout().getWeapon();
	}

	/**
	 * Food for {@code free} slots: the best one-bite food the player owns (sharks if none), with up to
	 * {@link #ALTERNATIVES} other owned foods the bank may show in its place. With {@link #MIX_SLOTS} or more free,
	 * up to one in {@link #MIX_SHARE} slots each of owned cooked karambwans, eaten in the same tick ({@code combo}: only
	 * against a target hitting {@link #COMBO_MAX_HIT} or more), and owned Saradomin brews, at most one per super restore
	 * bottle, which restores the stats a brew drains.
	 */
	static void addFood(List<Supply> out, int free, IntPredicate owns, boolean combo)
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
		int karambwans = mix && combo && owns.test(ItemID.TBWT_COOKED_KARAMBWAN) ? free / MIX_SHARE : 0;
		int restores = out.stream().filter(s -> s.getName().equals(SUPER_RESTORE) || s.getName().equals(SANFEW)).mapToInt(Supply::getCount).sum();
		List<Integer> brewIds = BoostAccess.doseIds(ItemID._4DOSEPOTIONOFSARADOMIN);
		int brews = mix && brewIds.stream().anyMatch(owns::test) ? Math.min(free / MIX_SHARE, restores) : 0;
		out.add(new Supply(main, ids, free - karambwans - brews));
		if (karambwans > 0)
			out.add(new Supply("Cooked karambwan", Collections.singletonList(ItemID.TBWT_COOKED_KARAMBWAN), karambwans));
		if (brews > 0)
			out.add(new Supply("Saradomin brew", brewIds, brews));
	}

	/** Whether {@code id} is one of the trip's one-bite foods. */
	public static boolean isFood(int id)
	{
		for (JsonElement row : FOODS)
			if (row.getAsJsonArray().get(1).getAsInt() == id)
				return true;
		return false;
	}

	/** The supplies by count and name, e.g. "2 Super combat potion, 3 Super restore". */
	public static String summary(List<Supply> supplies)
	{
		return supplies.stream().map(s -> s.getCount() + " " + s.getName()).collect(Collectors.joining(", "));
	}
}
