package com.bestgearsetup.calc;

import com.bestgearsetup.*;
import com.bestgearsetup.data.*;
import com.google.gson.*;
import java.util.*;
import java.util.function.*;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import net.runelite.api.gameval.ItemID;
import static com.bestgearsetup.calc.Weapons.*;
import static com.bestgearsetup.calc.Encounter.*;
import static com.bestgearsetup.calc.Effects.*;

/**
 * Searches for the best setup per combat class under the owned-items / budget rules and the
 * user's constraints (styles, experience, locks, exclusions...).
 *
 * <p>Each weapon is solved exactly ({@link #exactWeapon}): plain gear changes the score only through a few bonus
 * totals and never lowers it as one rises, so the slots are combined keeping only loadouts no other matches in every
 * total at no more cost, around each choice of effect gear and whole set, and the survivors are scored by the
 * calculator with each allowed stance or spell. The kill's specials are added afterwards; the strongest weapons also
 * finish their next-best choices ({@link #ALTERNATIVES}).
 *
 * <p>Per-slot candidates are pruned to items no other item dominates in every damage bonus, price
 * and expensive-item status, plus items with special effects. Once the best setups are chosen, slots
 * that add nothing can be filled with prayer or defence items (optionally trading a small DPS margin).
 */
public class Optimizer
{
	private static final double EPS = 1e-9;
	/** Slot.values() copies the array on every call; the per-setup loops share this one. */
	private static final Slot[] SLOTS = Slot.values();
	/**
	 * The search maximises the ordinary attacks, but the kill's specials can favour another option or effect gear:
	 * the strongest few weapons also finish this many of their next-best choices (each option group's and each effect
	 * gear's best) within this share of their best score, keeping whichever is fastest with its specials.
	 */
	private static final int ALTERNATIVES = 3;
	private static final int ALTERNATIVE_WEAPONS = 10;
	private static final double ALTERNATIVE_SHARE = 0.03;
	/**
	 * How far below the best score an option's bound must be to skip it. Far above {@link #EPS} times the number of
	 * options, so an option skipped this way can't have changed which of the others wins a near tie.
	 */
	private static final double BOUND_MARGIN = 1e-6;
	/** Weight that makes the calc-mode metric dominate DPS, which only breaks ties. */
	private static final double PRIMARY_WEIGHT = 1e6;
	/**
	 * Relative change in a damage rate or the calc mode's figure below which a piece of equipment makes no meaningful
	 * difference to a setup, so it isn't worth wearing or buying ({@link #prune}).
	 */
	private static final double MEANINGFUL_BENEFIT = 1e-6;

	/** Gear whose effects the calculator reads by name, so it stays a candidate whatever its bonuses. */
	private static final Names.NameList SPECIAL_GEAR = Names.list("special gear");
	private static final Names.NameList MAGIC_ONLY_GEAR = Names.list("magic-only gear");
	/** Spells that need one of their staves wielded to be cast at all, so no other weapon casts them by hand. */
	private static final Names.NameList STAFF_BOUND_SPELLS = Names.list("staff-bound spells");
	/** The plain god staves cast their god spell, but can't autocast it (each spell's OSRS Wiki page). */
	private static final Names.NameList HAND_CAST_STAVES = Names.list("hand-cast god staves");
	private static final Names.NameList VENOM_HELMS = Names.list("venom helms");
	/** Sets whose effect needs every piece, worn whole by the search ({@link #wholeSets}). */
	private static final JsonArray SETS = Names.rows("optimizer sets");
	/** Bonuses a total can read; {@link #ABOVE} plus ten times one attack bonus plus another is how far it leads. */
	private static final int STAB = 1;
	private static final int SLASH = 2;
	private static final int CRUSH = 3;
	private static final int RANGED = 4;
	private static final int MAGIC = 5;
	private static final int MELEE_STR = 6;
	private static final int RANGED_STR = 7;
	private static final int MAGIC_STR = 8;
	private static final int DEFENCE = 9;
	private static final int ABOVE = 100;

	private final GameData data;
	private final CombatContext ctx;
	/** The fight with a special attack being thrown. */
	private final CombatContext specCtx;
	private final OptimizerSettings settings;
	private final IntPredicate owned;
	private final IntToLongFunction ownedQuantity;
	private final ToLongFunction<Gear> priceFn;
	private final Map<Integer, Boolean> expensiveItems = new HashMap<>();
	// Ownership is fixed for one search but queried in the innermost cost loops; Gear's Lombok
	// equals/hashCode cover every stat, so these caches key on identity.
	private final Map<Gear, List<Integer>> ownershipVariants = new IdentityHashMap<>();
	private final Map<Gear, Boolean> ownedItems = new IdentityHashMap<>();
	private final Map<Combat, List<Gear>> specWeapons = new EnumMap<>(Combat.class);
	/**
	 * Special attacks scored by {@link #strongest}, for the whole search: setups found for different weapons often
	 * share their armour, and so hold the same spec loadouts.
	 */
	private final Map<ScoreKey, Dps> specResults = new HashMap<>();
	private final ScoreKey probe = new ScoreKey();
	/** Per weapon, the best setups for each option and effect gear worth finishing ({@link #exactWeapon}). */
	private final Map<Gear, List<Found>> alternatives = new IdentityHashMap<>();
	/** The search behind each setup found, to search again around its effect gear. */
	private final Map<Outfit, Exact> origins = new IdentityHashMap<>();
	private final Map<Gear, Boolean> setPieces = new IdentityHashMap<>();
	/** Per weapon and {@link #optionKey}, whether each item is effect gear ({@link #effect}). */
	private final Map<Gear, Map<String, Map<Gear, Boolean>>> effectItems = new IdentityHashMap<>();
	private Combat boundClass;
	/** The running search's cancellation, polled inside each weapon's search too. */
	private BooleanSupplier cancelled = () -> false;
	/** The searched class's candidate shields, offered as an off-hand for one-handed spec weapons. */
	private List<Gear> specShields = Collections.emptyList();
	private final PlayerLevels levels;
	private final Monster monster;
	/** Spells that pass the search-wide gates, before each weapon's autocast restrictions. */
	private final List<Spell> autocastableSpells;
	/** Targets rewarding an attack bonus above the others ({@link #totals}). */
	private final boolean araxyte;
	private final boolean hueyTail;
	// Whether the target demands protective equipment is fixed for the search; only the setup varies.
	private final boolean fireProtectionRequired;
	private final boolean slayerEquipmentRequired;
	// An ammunition recovery cape is required only when the setting is on and one could be worn here, cape lock included.
	// Decided on first use, so a search that never asks doesn't scan the capes; null until then.
	private Boolean recoveryRequired;
	// Whether chaos gauntlets could be worn, which keeps bolt spells in the search; null until first asked.
	private Boolean chaosGauntlets;
	/** Tests turn this off to compare the search against one that scores every spell. */
	static boolean pruneSpells = true;

	/**
	 * @param owned         whether the player owns an item id (bank, inventory, worn or marked owned)
	 * @param ownedQuantity units of an item id held; ammunition beyond this is bought for the ammo quantity
	 * @param priceFn       acquisition cost of an item, including tradable components of untradeables
	 */
	public Optimizer(GameData data, CombatContext ctx, OptimizerSettings settings, IntPredicate owned,
		IntToLongFunction ownedQuantity, ToLongFunction<Gear> priceFn)
	{
		this.data = data;
		this.ctx = ctx.withFightOptions(ctx.getAoeTargets(), settings.getTargetDistance());
		specCtx = this.ctx.withModifiers(this.ctx.getModifiers().toBuilder().specialAttack(true).build());
		this.settings = settings;
		this.owned = owned;
		this.ownedQuantity = ownedQuantity;
		this.priceFn = priceFn;
		this.levels = ctx.getLevels();
		this.monster = ctx.getMonster();
		this.autocastableSpells = settings.getSpellbooks() == null ? Collections.emptyList()
			: autocastSpells(data, this.ctx.getMagic(), monster, settings);
		this.fireProtectionRequired = DragonfireProtection.required(monster, settings);
		this.slayerEquipmentRequired = SlayerEquipment.applies(monster, settings);
		this.araxyte = Encounter.named(monster, "acidic araxyte") || Encounter.named(monster, "mirrorback araxyte");
		this.hueyTail = hueyTail(monster);
	}

	/**
	 * Best setups for one combat class, best first, one per weapon, reporting the fraction of work done (0 to 1,
	 * never decreasing) on the calling thread. Each weapon solved or finished counts as one step.
	 */
	public List<Setup> optimize(Combat cls, BooleanSupplier cancelled, DoubleConsumer progress)
	{
		if (!classAllowed(monster, cls))
		{
			progress.accept(1);
			return Collections.emptyList();
		}
		Map<Slot, List<Gear>> candidates = candidates(cls);
		this.cancelled = cancelled;
		boundClass = cls;
		alternatives.clear();
		origins.clear();
		specShields = candidates.getOrDefault(Slot.SHIELD, Collections.emptyList());
		List<Gear> weapons = weapons(cls);
		Steps steps = new Steps(progress, weapons.size() * 2);
		List<Found> found = new ArrayList<>();
		for (Gear weapon : weapons)
		{
			if (cancelled.getAsBoolean())
				return Collections.emptyList();
			Found f = exactWeapon(weapon, cls, candidates);
			if (f != null)
				found.add(f);
			steps.step();
		}
		found.sort(Comparator.comparingDouble((Found f) -> -f.getScore()).thenComparingLong(Found::getCost));
		steps.total(weapons.size() + found.size());
		for (int i = ALTERNATIVE_WEAPONS; i < found.size(); i++)
			alternatives.remove(found.get(i).getLoadout().getWeapon());

		List<Setup> results = new ArrayList<>();
		Map<Setup, Exact> originOf = new IdentityHashMap<>();
		for (Found f : found)
		{
			if (cancelled.getAsBoolean())
				return Collections.emptyList();
			Gear weapon = f.getLoadout().getWeapon();
			// The kill's specials can favour another option or effect gear than the ordinary attacks do.
			Setup kept = null;
			Exact origin = null;
			for (Found tried : alternatives.getOrDefault(weapon, Collections.singletonList(f)))
			{
				Exact from = origins.get(tried.getLoadout());
				Setup r = finish(tried.getLoadout(), cls);
				if (faster(r, kept))
				{
					kept = r;
					origin = from;
				}
			}
			// Its special can favour other gear around the same effect gear, e.g. a crush bonus for the elder maul.
			if (kept != null && kept.getSpecial() != null && origin != null && alternatives.containsKey(weapon))
			{
				for (Outfit trade : specialTrades(origin, kept.getSpecial()))
				{
					Setup r = finish(trade, cls);
					if (faster(r, kept))
						kept = r;
				}
			}
			if (kept != null)
			{
				results.add(kept);
				originOf.put(kept, origin);
			}
			steps.step();
		}
		// Filling can lower different setups by different amounts; rank the equipment actually returned.
		Comparator<Setup> rank = Comparator.comparingDouble((Setup r) -> -metric(r.getDps()))
			.thenComparingLong(Setup::getBuyCost);
		results.sort(rank);
		// Only the setups returned (and any tied with the last) are finished further: pruning changes no rate, only cost.
		int returned = Math.min(Math.max(0, settings.getResultsPerClass()), results.size());
		int finished = returned;
		while (finished > 0 && finished < results.size()
			&& Math.abs(metric(results.get(finished).getDps()) - metric(results.get(returned - 1).getDps())) <= EPS)
			finished++;
		for (int i = 0; i < finished; i++)
		{
			if (cancelled.getAsBoolean())
				return Collections.emptyList();
			// They wear the cheapest gear as good as the one found ({@link #cheapest}).
			Exact origin = originOf.get(results.get(i));
			Setup kept = results.get(i);
			for (Exact e : origin == null ? Collections.<Exact>emptyList() : origin.ties)
			{
				cheapest(e);
				Setup r = finish(e.loadout.copy(), cls);
				kept = faster(r, kept) ? r : kept;
			}
			Setup r = origin == null || !cheapest(origin) ? null : finish(origin.loadout.copy(), cls);
			results.set(i, pruned(faster(r, kept) ? r : kept, cls));
		}
		results = results.subList(0, finished);
		results.sort(rank);
		return new ArrayList<>(results.subList(0, Math.min(Math.max(0, settings.getResultsPerClass()), results.size())));
	}

	/** Whether {@code r} has a higher figure than {@code kept} (or none), or the same for less GP. */
	private boolean faster(Setup r, Setup kept)
	{
		return r != null && (kept == null || better(metric(r.getDps()), r.getBuyCost(), metric(kept.getDps()), kept.getBuyCost()));
	}

	/** The found equipment filled, with the kill's specials; null when it deals no damage. */
	private Setup finish(Outfit l, Combat cls)
	{
		Set<Slot> filled = fill(l);
		Dps dps = calculate(l);
		return dps.getDps() <= 0 || !withinWildernessRisk(l) ? null : withSpecial(l, cls, filled, dps);
	}

	private Setup withSpecial(Outfit l, Combat cls, Set<Slot> filled, Dps dps)
	{
		Specced specced = special(l, cls, dps, cost(l));
		if (specced != null && specced.getWorn() != l)
		{
			// Lightbearer replaced the ring.
			l = specced.getWorn();
			filled = EnumSet.copyOf(filled.isEmpty() ? EnumSet.noneOf(Slot.class) : filled);
			filled.remove(Slot.RING);
		}
		return result(cls, l, specced == null ? dps : specced.getResult(), filled, specced == null ? null : specced.getPlan());
	}

	private Setup result(Combat cls, Outfit l, Dps dps, Set<Slot> filled, SpecialPlan plan)
	{
		Set<SupportSpell> support = SupportSpell.assumed(l, ctx);
		return new Setup(cls, l, dps, cost(l) + (plan == null ? 0 : plan.getExtraCost()),
			unpricedItems(l) + (plan == null ? 0 : plan.getUnpricedItems()), filled,
			support, plan, support.contains(SupportSpell.THRALL) ? ctx.getThrall() : null);
	}

	/**
	 * The setup without what changes no rate: pruned for the ordinary attacks, its spec chosen again with that lighter
	 * setup's cost and Wilderness risk (a piece doing nothing can leave no room for a spec weapon), then pruned for the
	 * spec kept.
	 */
	private Setup pruned(Setup found, Combat cls)
	{
		Outfit l = found.getLoadout().copy();
		prune(l, found.getFilledSlots(), null);
		Setup again = withSpecial(l, cls, found.getFilledSlots(), calculate(l));
		Setup kept = faster(again, found) ? again : found;
		Outfit worn = kept.getLoadout().copy();
		prune(worn, kept.getFilledSlots(), kept.getSpecial());
		return result(cls, worn, kept.getDps(), kept.getFilledSlots(), kept.getSpecial());
	}

	/** Counts finished steps against a total that may only shrink, so the reported fraction never falls. */
	private static final class Steps
	{
		private final DoubleConsumer progress;
		private int done;
		private int total;

		Steps(DoubleConsumer progress, int total)
		{
			this.progress = progress;
			this.total = total;
			report();
		}

		void step()
		{
			done++;
			report();
		}

		void total(int total)
		{
			this.total = Math.max(done, Math.min(this.total, total));
			report();
		}

		private void report()
		{
			progress.accept(total <= 0 ? 1 : Math.min(1, (double) done / total));
		}
	}

	// ------------------------------------------------------------ exact search

	/** A higher score, or the same score for less GP. */
	private static boolean better(double score, long cost, double bestScore, long bestCost)
	{
		return score > bestScore + EPS || Math.abs(score - bestScore) <= EPS && cost < bestCost;
	}

	private int overCap(Outfit l)
	{
		return settings.isWildernessRiskLimited()
			? Math.max(0, expensiveItemCount(l) - Math.max(0, settings.getMaxExpensiveItems())) : 0;
	}

	/**
	 * The weapon's best setup, found exactly. For each ammunition and choice of effect gear (every whole set, and every
	 * item whose effect can apply, in its slot), the other slots hold plain items, which change the score only through
	 * the few bonus totals an option reads ({@link #totals}) and never lower it as one rises. So, slot by slot, only
	 * loadouts no other matches in every total at no more cost and expensive items are kept, and the survivors are
	 * scored with each option. Ties keep the cheapest. The best of each option group and effect gear within
	 * {@link #ALTERNATIVE_SHARE} are kept as {@link #alternatives}.
	 */
	private Found exactWeapon(Gear weapon, Combat cls, Map<Slot, List<Gear>> candidates)
	{
		List<Option> options = options(weapon, cls);
		boolean fires = firesAmmoSlot(weapon);
		boolean loads = loadsAmmo(weapon);
		List<Gear> ammo = fires || loads ? compatibleAmmo(weapon, fires) : Collections.singletonList(null);
		if (options.isEmpty() || ammo.isEmpty())
			return null;
		boolean recovery = recoveryRequired() && firesRecoverableAmmo(weapon, cls);
		List<Slot> free = new ArrayList<>();
		Map<Slot, List<Gear>> choices = new EnumMap<>(Slot.class);
		for (Slot slot : SLOTS)
		{
			SlotLock lock = settings.getLocks().get(slot);
			// A fill-locked cape is still searched among the recovery capes the weapon needs.
			boolean open = lock == null || recovery && slot == Slot.CAPE && lock.getKind() == SlotLock.Kind.FILL;
			if (slot == Slot.WEAPON || slot == Slot.AMMO || slot == Slot.SHIELD && weapon.isTwoHanded() || !open)
				continue;
			free.add(slot);
			List<Gear> items = new ArrayList<>();
			items.add(null);
			items.addAll(candidates.get(slot));
			choices.put(slot, items);
		}
		Exact best = new Exact();
		for (Gear a : ammo)
		{
			Outfit base = new Outfit();
			base.set(Slot.WEAPON, weapon);
			applyLocks(base, weapon, fires);
			if (fires)
				base.set(Slot.AMMO, a);
			else
				base.setLoadedAmmo(a);
			solveAround(base, free, choices, options, best);
		}
		if (best.loadout == null || calculate(best.loadout).getDps() <= 0)
			return null;
		List<Found> kept = new ArrayList<>();
		kept.add(new Found(best.loadout, best.score, best.cost));
		best.tried.sort(Comparator.comparingDouble((Exact e) -> -e.score));
		Exact winner = best.tried.stream().filter(e -> e.loadout == best.loadout).findFirst().orElse(null);
		for (Exact e : best.tried)
		{
			// Without a budget each branch kept only its strongest totals, so branches tied with the best are tried
			// again once they wear their cheapest gear: effect gear adding nothing mustn't win on a dearer plain fill.
			if (winner != null && e != winner && Math.abs(e.score - best.score) <= EPS)
				winner.ties.add(e);
			if (e.loadout == best.loadout)
				origins.put(e.loadout, e);
			else if (kept.size() <= ALTERNATIVES && e.score > 0 && e.score >= best.score * (1 - ALTERNATIVE_SHARE))
			{
				kept.add(new Found(e.loadout, e.score, e.cost));
				origins.put(e.loadout, e);
			}
		}
		// The best gear's other options (a stance the special favours, say) are finished too.
		Exact origin = origins.get(best.loadout);
		List<Found> others = new ArrayList<>();
		for (Option o : origin == null ? Collections.<Option>emptyList() : origin.options)
		{
			if (o.getStyle().equals(best.loadout.getStyle()) && o.getSpell() == best.loadout.getSpell())
				continue;
			Outfit other = best.loadout.copy();
			o.apply(other);
			double s = metric(calculate(other));
			if (s > 0 && s >= best.score * (1 - ALTERNATIVE_SHARE))
				others.add(new Found(other, s, best.cost));
		}
		others.sort(Comparator.comparingDouble((Found f) -> -f.getScore()));
		for (Found f : others.subList(0, Math.min(ALTERNATIVES, others.size())))
		{
			kept.add(f);
			origins.put(f.getLoadout(), origin);
		}
		alternatives.put(weapon, kept);
		return kept.get(0);
	}

	/**
	 * The best loadouts of {@code base} with each free slot holding one of its choices (the empty slot among them), for
	 * each of the options, into {@code best}.
	 */
	private void solveAround(Outfit base, List<Slot> free, Map<Slot, List<Gear>> choices, List<Option> options, Exact best)
	{
		// Options reading the same totals with the same effect gear share one search.
		Map<List<Object>, List<Option>> groups = new LinkedHashMap<>();
		for (Option o : options)
		{
			o.apply(base);
			List<Object> key = new ArrayList<>(totals(base));
			key.add(optionKey(base));
			groups.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
		}
		List<Map<Slot, List<Gear>>> sets = wholeSets(base, free, choices);
		for (List<Option> group : groups.values())
		{
			group.get(0).apply(base);
			int[] totals = totals(base).stream().mapToInt(Integer::intValue).toArray();
			// Effect items that differ only in their bonuses are searched together; the rest are plain.
			Map<Slot, List<Gear>> pools = new EnumMap<>(Slot.class);
			Map<Slot, Collection<List<Gear>>> effects = new EnumMap<>(Slot.class);
			for (Slot slot : free)
			{
				List<Gear> pool = new ArrayList<>();
				Map<String, List<Gear>> alike = new LinkedHashMap<>();
				for (Gear item : choices.get(slot))
				{
					if (item != null && effect(item, base))
						alike.computeIfAbsent(effectKey(base, item), k -> new ArrayList<>()).add(item);
					else
						pool.add(item);
				}
				pools.put(slot, pool);
				effects.put(slot, alike.values());
			}
			// Slots offering several kinds of effect gear are fixed first, so the rest can be bounded early.
			List<Slot> order = new ArrayList<>(free);
			order.sort(Comparator.comparingInt((Slot slot) -> -effects.get(slot).size()));
			GroupSearch search = new GroupSearch(base, order, pools, effects, totals, group);
			for (Map<Slot, List<Gear>> set : sets)
				search.fixEffects(0, new EnumMap<>(set), best);
		}
	}

	/** What decides which items have an effect besides the weapon ({@link #effectApplies}): the option and ammunition. */
	private static String optionKey(Outfit trial)
	{
		Spell spell = trial.getSpell();
		return trial.getStyle().getType() + "/" + (spell == null ? "-" : spell.getSpellbook() + "/" + SpellRules.element(spell)
			+ "/" + spell(trial).endsWith(" bolt")) + "/" + Effects.named(trial, Slot.AMMO, "crystal blessing");
	}

	/** The best exact result so far, and the search that found it. */
	private static final class Exact
	{
		Outfit loadout;
		double score = -1;
		long cost = Long.MAX_VALUE;
		double spare;
		/** The fixed gear, its plain slots and their items, its totals and options. */
		Outfit frame;
		List<Slot> plain;
		Map<Slot, List<Gear>> pools;
		int[] totals;
		List<Option> options;
		/** Each option group's and effect gear's own best. */
		final List<Exact> tried = new ArrayList<>();
		/** Whether {@link #cheapest} has searched it. */
		boolean cheapened;
		/** Other branches scoring the same, finished beside it. */
		final List<Exact> ties = new ArrayList<>();
	}

	/**
	 * The bonus totals the worn option's score reads: its attack bonus and strength, with the bulwark's defences. Where
	 * the target compares the attack bonuses with each other, the leads over the others count too.
	 */
	private List<Integer> totals(Outfit trial)
	{
		Style style = trial.getStyle();
		Gear weapon = trial.getWeapon();
		Combat cls = classOf(style);
		int attack = cls == Combat.MAGIC ? MAGIC : cls == Combat.RANGED ? RANGED
			: style.getType() == Style.Type.STAB ? STAB : style.getType() == Style.Type.SLASH ? SLASH : CRUSH;
		List<Integer> out = new ArrayList<>();
		out.add(attack);
		out.add(cls == Combat.MAGIC ? MAGIC_STR
			: cls == Combat.RANGED && !scalesWithStrength(weapon) ? RANGED_STR : MELEE_STR);
		if (lower(weapon).contains("bulwark"))
			out.add(DEFENCE);
		// Huey's tail and the araxytes reward an attack bonus above the others (Encounter, CombatRules.araxyteMax).
		boolean lead = araxyte && (attack == CRUSH || attack == RANGED && weapon.getSubcategory().equals("crossbow"));
		if (lead || hueyTail && attack == CRUSH)
		{
			for (int other = STAB; other <= MAGIC; other++)
			{
				if (other != attack && (lead || other < CRUSH))
					out.add(ABOVE + attack * 10 + other);
			}
		}
		return out;
	}

	/** The item's attack and strength bonuses of the searched class, whichever the option reads. */
	private double spare(Gear item)
	{
		if (item == null)
			return 0;
		switch (boundClass)
		{
			case MELEE:
				return item.getStabBonus() + item.getSlashBonus() + item.getCrushBonus() + item.getMeleeStr();
			case RANGED:
				return item.getRangedBonus() + item.getRangedStr() + item.getMeleeStr();
			default:
				return item.getMagicBonus() + item.getMagicStr();
		}
	}

	private static double stat(int code, Gear item)
	{
		if (item == null)
			return 0;
		if (code > ABOVE)
			return stat((code - ABOVE) / 10, item) - stat((code - ABOVE) % 10, item);
		switch (code)
		{
			case STAB:
				return item.getStabBonus();
			case SLASH:
				return item.getSlashBonus();
			case CRUSH:
				return item.getCrushBonus();
			case RANGED:
				return item.getRangedBonus();
			case MAGIC:
				return item.getMagicBonus();
			case MELEE_STR:
				return item.getMeleeStr();
			case RANGED_STR:
				return item.getRangedStr();
			case MAGIC_STR:
				return item.getMagicStr();
			default:
				return item.getStabDef() + item.getSlashDef() + item.getCrushDef() + item.getRangedDef();
		}
	}

	/** Sets a bonus on an imaginary item. */
	private static void setStat(int code, Gear item, double v)
	{
		switch (code)
		{
			case STAB:
				item.setStabBonus((int) v);
				break;
			case SLASH:
				item.setSlashBonus((int) v);
				break;
			case CRUSH:
				item.setCrushBonus((int) v);
				break;
			case RANGED:
				item.setRangedBonus((int) v);
				break;
			case MAGIC:
				item.setMagicBonus((int) v);
				break;
			case MELEE_STR:
				item.setMeleeStr((int) v);
				break;
			case RANGED_STR:
				item.setRangedStr((int) v);
				break;
			case MAGIC_STR:
				item.setMagicStr(v);
				break;
			default:
				item.setStabDef((int) v);
				break;
		}
	}

	/**
	 * Whether the item is searched as effect gear with this weapon: it isn't {@link #plain}, nor a whole set's piece
	 * (sets are worn whole, {@link #wholeSets}).
	 */
	private boolean effect(Gear item, Outfit trial)
	{
		Map<Gear, Boolean> known = effectItems.computeIfAbsent(trial.getWeapon(), w -> new HashMap<>())
			.computeIfAbsent(optionKey(trial), k -> new IdentityHashMap<>());
		Boolean effect = known.get(item);
		if (effect == null)
		{
			effect = !setPiece(item) && !plain(item, trial);
			known.put(item, effect);
		}
		return effect;
	}

	/**
	 * What decides an effect item's effect besides its bonuses: its name, the venom shared by the venom helms, or for
	 * a protective shield with no named effect, whether it protects.
	 */
	private String effectKey(Outfit trial, Gear item)
	{
		String name = lower(item);
		if (DragonfireProtection.isProtectiveShield(item) && !specialName(name))
		{
			Outfit l = trial.copy();
			l.set(Slot.SHIELD, item);
			return "protects " + DragonfireProtection.protects(monster, l, settings, levels)
				+ (SlayerEquipment.isRequired(monster, settings, item) ? " " + name : "");
		}
		return VENOM_HELMS.matches(name) && !name.contains("uncharged") ? "venom helm"
			: DpsCalculator.Bonuses.QUIVERS.contains(name) ? "quiver" : name;
	}

	private boolean setPiece(Gear item)
	{
		Boolean known = setPieces.get(item);
		if (known == null)
		{
			known = false;
			for (JsonElement set : SETS)
			{
				for (JsonElement piece : set.getAsJsonObject().getAsJsonArray("pieces"))
				{
					JsonArray names = piece.getAsJsonArray();
					for (int i = 1; i < names.size(); i++)
						known |= namedWeapon(item, names.get(i).getAsString());
				}
			}
			setPieces.put(item, known);
		}
		return known;
	}

	/** No set, then every way to wear each whole set that this class and weapon can use around the locks. */
	private List<Map<Slot, List<Gear>>> wholeSets(Outfit trial, List<Slot> free, Map<Slot, List<Gear>> choices)
	{
		List<Map<Slot, List<Gear>>> out = new ArrayList<>();
		out.add(new EnumMap<>(Slot.class));
		set:
		for (JsonElement element : SETS)
		{
			JsonObject set = element.getAsJsonObject();
			if (boundClass != Combat.valueOf(set.get("class").getAsString()) || set.has("weapon")
				&& !Names.NameList.of(set.get("weapon")).matches(lower(trial.getWeapon())))
				continue;
			// Each slot's pieces are searched together: a way to wear the set with a stronger effect (elite void) is
			// listed as a set of its own, and only adds to the score here.
			Map<Slot, List<Gear>> pieces = new EnumMap<>(Slot.class);
			for (JsonElement piece : set.getAsJsonArray("pieces"))
			{
				JsonArray names = piece.getAsJsonArray();
				Slot slot = Slot.valueOf(names.get(0).getAsString());
				List<Gear> named = new ArrayList<>();
				// A locked slot already holds its piece, or the set can't be worn.
				for (Gear item : free.contains(slot) ? choices.get(slot) : Collections.singletonList(trial.get(slot)))
				{
					for (int i = 1; i < names.size(); i++)
					{
						if (namedWeapon(item, names.get(i).getAsString()))
						{
							named.add(item);
							break;
						}
					}
				}
				if (named.isEmpty())
					continue set;
				if (free.contains(slot))
					pieces.put(slot, named);
			}
			out.add(pieces);
		}
		return out;
	}

	/** One option group's search around {@code base}: its free slots, their plain items and effect gear, and the totals. */
	@RequiredArgsConstructor
	private final class GroupSearch
	{
		private final Outfit base;
		private final List<Slot> free;
		private final Map<Slot, List<Gear>> plainPools;
		private final Map<Slot, Collection<List<Gear>>> effects;
		private final int[] totals;
		private final List<Option> options;

		/** Fixes each free slot from {@code index} on to one kind of its effect items, or leaves it to the plain items. */
		void fixEffects(int index, Map<Slot, List<Gear>> fixed, Exact best)
		{
			if (cancelled.getAsBoolean())
				return;
			if (index == free.size())
			{
				Exact own = new Exact();
				solveFixed(fixed, own, best.score * (1 - ALTERNATIVE_SHARE));
				if (own.loadout == null)
					return;
				if (better(own.score, own.cost, best.score, best.cost))
				{
					best.loadout = own.loadout;
					best.score = own.score;
					best.cost = own.cost;
					best.spare = own.spare;
					best.tried.removeIf(e -> e.score < best.score * (1 - ALTERNATIVE_SHARE));
				}
				// Only those that can become alternatives are kept.
				if (own.score >= best.score * (1 - ALTERNATIVE_SHARE))
					best.tried.add(own);
				return;
			}
			Slot slot = free.get(index);
			if (!fixed.containsKey(slot) && !effects.get(slot).isEmpty()
				&& !openBound(index, fixed, best.score * (1 - ALTERNATIVE_SHARE)))
				return;
			fixEffects(index + 1, fixed, best);
			if (fixed.containsKey(slot))
				return;
			for (List<Gear> alike : effects.get(slot))
			{
				fixed.put(slot, alike);
				fixEffects(index + 1, fixed, best);
			}
			fixed.remove(slot);
		}

		/**
		 * Whether the choices still open from {@code index} on can reach {@code floor}, once every open slot has at most one
		 * kind of effect gear: each holds an imaginary item with the highest of each total among its plain and effect items,
		 * named as that effect gear when it has one. An effect never lowers the score below the same bonuses without it,
		 * so this bounds the plain items too. True (no bound) while a slot still offers several kinds.
		 */
		boolean openBound(int index, Map<Slot, List<Gear>> fixed, double floor)
		{
			Outfit top = base.copy();
			for (int i = 0; i < free.size(); i++)
			{
				Slot slot = free.get(i);
				List<Gear> items = new ArrayList<>(fixed.getOrDefault(slot, plainPools.get(slot)));
				Gear like = fixed.containsKey(slot) ? items.get(0) : null;
				if (i >= index && !fixed.containsKey(slot))
				{
					if (effects.get(slot).size() > 1)
						return true;
					for (List<Gear> alike : effects.get(slot))
					{
						like = alike.get(0);
						items.addAll(alike);
					}
				}
				top.set(slot, items.size() == 1 ? items.get(0) : boundItem(slot, like, items, totals));
			}
			for (Option o : options)
			{
				o.apply(top);
				if (!protectedSetup(top))
					continue;
				double bound = metric(DpsCalculator.calculate(top, ctx));
				if (bound <= 0 || bound >= floor - EPS - BOUND_MARGIN)
					return true;
			}
			return false;
		}

		/** The best plain items around the fixed effect gear, for each option of the group. */
		void solveFixed(Map<Slot, List<Gear>> fixed, Exact best, double floor)
		{
			Outfit l = base.copy();
			List<Slot> plainSlots = new ArrayList<>();
			Map<Slot, List<Gear>> pools = new EnumMap<>(Slot.class);
			for (Slot slot : free)
			{
				List<Gear> items = fixed.getOrDefault(slot, plainPools.get(slot));
				l.set(slot, items.size() == 1 ? items.get(0) : null);
				if (items.size() == 1)
					continue;
				plainSlots.add(slot);
				pools.put(slot, items);
			}
			if (!withinBudget(cost(l)) || overCap(l) > 0)
				return;
			// Each option's bound: every searched slot holding the highest of each total among its items (as one of its
			// effect items, when they are).
			Outfit top = l.copy();
			for (Slot slot : plainSlots)
				top.set(slot, boundItem(slot, fixed.containsKey(slot) ? pools.get(slot).get(0) : null, pools.get(slot), totals));
			List<Option> open = new ArrayList<>();
			Map<Option, Double> bounds = new IdentityHashMap<>();
			for (Option o : options)
			{
				o.apply(top);
				double bound = protectedSetup(top) ? metric(DpsCalculator.calculate(top, ctx)) : 0;
				// Gear rules can reject the imaginary items (the Gauntlet): then there is no bound.
				if (bound <= 0 && protectedSetup(top))
					bound = Double.POSITIVE_INFINITY;
				if (bound > 0 && bound >= floor - EPS - BOUND_MARGIN)
				{
					open.add(o);
					bounds.put(o, bound);
				}
			}
			if (open.isEmpty())
				return;
			open.sort(Comparator.comparingDouble(o -> -bounds.get(o)));

			best.frame = l.copy();
			best.plain = plainSlots;
			best.pools = pools;
			best.totals = totals;
			best.options = options;
			List<Partial> partials = loadouts(l, plainSlots, pools, totals, Double.NaN);
			List<Partial> strongest = undominated(partials, false);
			partials.sort(Comparator.comparingLong((Partial p) -> -p.cost));
			for (Option o : open)
			{
				if (bounds.get(o) < Math.max(floor, best.score) - EPS - BOUND_MARGIN)
					continue;
				o.apply(l);
				// The highest score is on a loadout no other beats in every total; then look for a cheaper one as good.
				for (Partial p : strongest)
					consider(l, plainSlots, p, best);
				List<Partial> lower = new ArrayList<>();
				for (Partial p : partials)
				{
					if (p.cost >= best.cost || lower.stream().anyMatch(s -> s.covers(p, false)))
						continue;
					if (!consider(l, plainSlots, p, best))
						lower.add(p);
				}
			}
		}
	}

	/** An imaginary item with the highest of each total among the items (a lead's other bonus at its lowest). */
	private static Gear boundItem(Slot slot, Gear like, List<Gear> items, int[] totals)
	{
		Gear bound = new Gear();
		bound.setName(like == null ? "" : like.getName());
		bound.setCombatName(like == null ? null : like.getCombatName());
		bound.setId(like == null ? -1 : like.getId());
		bound.setSlot(slot);
		for (int code : totals)
		{
			int raw = code > ABOVE ? (code - ABOVE) % 10 : code;
			double most = code > ABOVE ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
			for (Gear item : items)
				most = code > ABOVE ? Math.min(most, stat(raw, item)) : Math.max(most, stat(raw, item));
			setStat(raw, bound, most);
		}
		return bound;
	}

	/** A partial loadout over the plain slots: its totals, cost and expensive items, and the item it last added. */
	@RequiredArgsConstructor
	private static final class Partial
	{
		final double[] totals;
		final long cost;
		final int expensive;
		/** The class's other bonuses ({@link #spare}), breaking ties towards gear the specials may use. */
		final double spare;
		final Slot slot;
		final Gear item;
		final Partial previous;

		void wear(Outfit l)
		{
			for (Partial p = this; p.slot != null; p = p.previous)
				l.set(p.slot, p.item);
		}

		/** At least these totals, at no more expensive items (and no more cost when costed). */
		boolean covers(Partial other, boolean costed)
		{
			if (costed && cost > other.cost || expensive > other.expensive)
				return false;
			for (int k = 0; k < totals.length; k++)
			{
				if (totals[k] < other.totals[k] - EPS)
					return false;
			}
			return true;
		}
	}

	/**
	 * Without a budget the search keeps only the strongest totals, which can drop a cheaper loadout scoring the same
	 * (strength short of the next max hit, say). This searches the found setup's plain slots again keeping every
	 * cheaper trade, but only loadouts whose best completion still reaches its score, and wears the cheapest. True when
	 * it found one; each search runs once.
	 */
	private boolean cheapest(Exact e)
	{
		if (settings.getMode() == SearchMode.BUDGET || e.plain == null || e.plain.isEmpty() || e.cheapened)
			return false;
		e.cheapened = true;
		Outfit found = e.loadout;
		Outfit l = e.frame.copy();
		l.setStyle(found.getStyle());
		l.setSpell(found.getSpell());
		for (Partial p : loadouts(l, e.plain, e.pools, e.totals, e.score))
			consider(l, e.plain, p, e);
		return e.loadout != found;
	}

	/**
	 * The plain loadouts around {@code l}'s fixed gear within the budget and expensive cap that no other matches in
	 * every total at no more expensive items (and, under a budget, no more cost). Given a {@code floor}, cost counts
	 * too, but only loadouts that can still reach it with {@code l}'s option are kept.
	 */
	private List<Partial> loadouts(Outfit l, List<Slot> plainSlots, Map<Slot, List<Gear>> pools, int[] totals,
		double floor)
	{
		boolean bounded = !Double.isNaN(floor);
		// The slots still to fill hold their best bonuses, keeping the effect of a slot's effect gear: its items are
		// one kind of effect gear, where a plain slot can also be left empty.
		Outfit top = l.copy();
		for (Slot slot : plainSlots)
		{
			List<Gear> pool = pools.get(slot);
			top.set(slot, boundItem(slot, pool.contains(null) ? null : pool.get(0), pool, totals));
		}
		// A lead starts from the weapon's and fixed gear's bonuses, so its sign is the worn total's.
		double[] start = new double[totals.length];
		DpsCalculator.Bonuses worn = DpsCalculator.Bonuses.of(l);
		int[] bonus = {0, worn.stab, worn.slash, worn.crush, worn.ranged, worn.magic};
		for (int k = 0; k < totals.length; k++)
		{
			if (totals[k] > ABOVE)
				start[k] = bonus[(totals[k] - ABOVE) / 10] - bonus[(totals[k] - ABOVE) % 10];
		}
		List<Partial> partials = Collections.singletonList(new Partial(start, cost(l),
			settings.isWildernessRiskLimited() ? expensiveItemCount(l) : 0, 0, null, null, null));
		// The least and most the slots still to fill can add to each lead: one sure to end above or below zero is
		// clamped, so loadouts differing only in a settled lead compare equal.
		double[][] low = new double[plainSlots.size() + 1][totals.length];
		double[][] high = new double[plainSlots.size() + 1][totals.length];
		for (int i = plainSlots.size() - 1; i >= 0; i--)
		{
			for (int k = 0; k < totals.length; k++)
			{
				double least = Double.POSITIVE_INFINITY;
				double most = Double.NEGATIVE_INFINITY;
				for (Gear item : pools.get(plainSlots.get(i)))
				{
					least = Math.min(least, stat(totals[k], item));
					most = Math.max(most, stat(totals[k], item));
				}
				low[i][k] = low[i + 1][k] + least;
				high[i][k] = high[i + 1][k] + most;
			}
		}
		int filled = 0;
		for (Slot slot : plainSlots)
		{
			filled++;
			List<Partial> grown = new ArrayList<>();
			for (Partial p : partials)
			{
				for (Gear item : pools.get(slot))
				{
					long c = p.cost + itemCost(item);
					int e = p.expensive + (settings.isWildernessRiskLimited() && expensive(item) ? 1 : 0);
					if (!withinBudget(c) || settings.isWildernessRiskLimited() && e > Math.max(0, settings.getMaxExpensiveItems()))
						continue;
					double[] t = p.totals.clone();
					for (int k = 0; k < totals.length; k++)
					{
						t[k] += stat(totals[k], item);
						if (totals[k] > ABOVE && t[k] + low[filled][k] >= 1)
							t[k] = 1 - low[filled][k];
						else if (totals[k] > ABOVE && t[k] + high[filled][k] <= -1)
							t[k] = -1 - high[filled][k];
					}
					grown.add(new Partial(t, c, e, p.spare + spare(item), slot, item, p));
				}
			}
			if (bounded)
			{
				grown.removeIf(p ->
				{
					p.wear(top);
					double bound = metric(DpsCalculator.calculate(top, ctx));
					return bound > 0 && bound < floor - EPS - BOUND_MARGIN;
				});
			}
			partials = undominated(grown, bounded || settings.getMode() == SearchMode.BUDGET);
		}
		return partials;
	}

	/**
	 * Around a found setup's effect gear, the loadouts trading ordinary damage for a harder hit of the plan's special:
	 * each keeps its totals and the special's, and only those no other beats in both scores are returned.
	 */
	private List<Outfit> specialTrades(Exact found, SpecialPlan plan)
	{
		Outfit frame = found.frame.copy();
		Set<Integer> codes = new LinkedHashSet<>();
		for (int code : found.totals)
			codes.add(code);
		codes.addAll(totals(heldFor(frame, plan)));
		int[] totals = codes.stream().mapToInt(Integer::intValue).toArray();
		List<double[]> scores = new ArrayList<>();
		List<Outfit> loadouts = new ArrayList<>();
		for (Partial p : undominated(loadouts(frame, found.plain, found.pools, totals, Double.NaN), false))
		{
			for (Slot slot : found.plain)
				frame.set(slot, null);
			p.wear(frame);
			double ordinary = bestOption(frame, found.options);
			if (ordinary < found.score * (1 - ALTERNATIVE_SHARE))
				continue;
			double spec = DpsCalculator.calculate(heldFor(frame, plan), specCtx).getPrimaryDps();
			scores.add(new double[]{ordinary, spec});
			loadouts.add(frame.copy());
		}
		List<Outfit> out = new ArrayList<>();
		for (int i = 0; i < loadouts.size(); i++)
		{
			boolean beaten = false;
			for (int j = 0; j < loadouts.size() && !beaten; j++)
			{
				double[] a = scores.get(j);
				double[] b = scores.get(i);
				beaten = j != i && a[0] >= b[0] - EPS && a[1] >= b[1] - EPS && (a[0] > b[0] + EPS || a[1] > b[1] + EPS || j < i);
			}
			if (!beaten)
				out.add(loadouts.get(i));
		}
		return out;
	}

	/** The loadout's score with its best option, which it is left holding (the first on ties). */
	private double bestOption(Outfit l, List<Option> options)
	{
		Option kept = null;
		double best = Double.NEGATIVE_INFINITY;
		for (Option o : options)
		{
			o.apply(l);
			double s = metric(calculate(l));
			if (s > best + EPS)
			{
				best = s;
				kept = o;
			}
		}
		if (kept != null)
		{
			kept.apply(l);
		}
		return best;
	}

	/** {@link #bestOption} over the weapon's allowed options; package-private to test option choice alone. */
	double scoreWithBestOption(Outfit l, Combat cls)
	{
		return bestOption(l, options(l.getWeapon(), cls));
	}

	/** Scores the loadout; true when it became the best (or ties it). */
	private boolean consider(Outfit l, List<Slot> plainSlots, Partial p, Exact best)
	{
		for (Slot slot : plainSlots)
			l.set(slot, null);
		p.wear(l);
		double s = metric(calculate(l));
		if (better(s, p.cost, best.score, best.cost)
			|| Math.abs(s - best.score) <= EPS && p.cost == best.cost && p.spare > best.spare + EPS)
		{
			best.loadout = l.copy();
			best.score = s;
			best.cost = p.cost;
			best.spare = p.spare;
			return true;
		}
		return s >= best.score - EPS;
	}

	/** The partials no other covers (cheapest first among equals). */
	private static List<Partial> undominated(List<Partial> partials, boolean costed)
	{
		List<Partial> sorted = new ArrayList<>(partials);
		sorted.sort(Comparator.comparingLong((Partial p) -> costed ? p.cost : 0).thenComparingInt(p -> costed ? p.expensive : 0)
			.thenComparing((a, b) ->
			{
				for (int k = 0; k < a.totals.length; k++)
				{
					int c = Double.compare(b.totals[k], a.totals[k]);
					if (c != 0)
						return c;
				}
				int c = Long.compare(a.cost, b.cost);
				return c != 0 ? c : Double.compare(b.spare, a.spare);
			}));
		List<Partial> kept = new ArrayList<>();
		for (Partial p : sorted)
		{
			boolean covered = false;
			for (int i = 0; i < kept.size() && !covered; i++)
				covered = kept.get(i).covers(p, costed);
			if (!covered)
				kept.add(p);
		}
		return kept;
	}

	/** Put locked items / empties in place. Locked slots are then left out of the search. */
	private void applyLocks(Outfit l, Gear weapon, boolean firesAmmo)
	{
		for (Map.Entry<Slot, SlotLock> e : settings.getLocks().entrySet())
		{
			Slot slot = e.getKey();
			SlotLock lock = e.getValue();
			if (slot == Slot.WEAPON || (slot == Slot.SHIELD && weapon.isTwoHanded()))
			{
				continue;
			}
			if (slot == Slot.AMMO && firesAmmo)
			{
				// Handled by compatibleAmmo(): the ammo list is restricted to the locked ammo.
				continue;
			}
			switch (lock.getKind())
			{
				case ITEM:
					Gear locked = data.getItem(slot, lock.getItemId());
					l.set(slot, locked != null && usable(locked) ? locked : null);
					break;
				default:
					// EMPTY stays empty; FILL is filled after the search.
					l.set(slot, null);
					break;
			}
		}
	}

	private boolean isLocked(Slot slot)
	{
		return settings.getLocks().containsKey(slot);
	}

	private Dps calculate(Outfit l)
	{
		return protectedSetup(l) && withinWildernessRisk(l) ? DpsCalculator.calculate(l, ctx) : Dps.ZERO;
	}

	/** Mandatory protection and, when required, an ammunition recovery cape are enforced throughout the search. */
	private boolean protectedSetup(Outfit l)
	{
		return protectedEquipment(l)
			&& (!recoveryRequired() || l.getStyle() == null
				|| !firesRecoverableAmmo(l.getWeapon(), classOf(l.getStyle()))
				|| isAmmoRecoveryCape(l.get(Slot.CAPE)));
	}

	/** Special switches must meet the equipment protection rules, but keep the worn cape: no recovery cape is required. */
	private boolean protectedEquipment(Outfit l)
	{
		return (!fireProtectionRequired || DragonfireProtection.protects(monster, l, settings, levels))
			&& (!slayerEquipmentRequired || SlayerEquipment.allowed(monster, settings, l));
	}

	private double metric(Dps r)
	{
		switch (settings.getCalcMode())
		{
			case ACCURACY:
				return r.getDps() <= 0 ? 0 : r.getAccuracy() * PRIMARY_WEIGHT + r.getDps();
			case MAX_HIT:
				return r.getMaxHit() * PRIMARY_WEIGHT + r.getDps();
			case AVERAGE_HIT:
				return r.getAverageHit() * PRIMARY_WEIGHT + r.getDps();
			default:
				return r.getDps();
		}
	}

	private double primary(Dps r)
	{
		switch (settings.getCalcMode())
		{
			case ACCURACY:
				return r.getAccuracy();
			case MAX_HIT:
				return r.getMaxHit();
			case AVERAGE_HIT:
				return r.getAverageHit();
			default:
				return r.getDps();
		}
	}

	// ------------------------------------------------------------ special attacks during the kill

	/**
	 * The best special attack to mix into the worn setup's kill, or null when none helps. Also tries the setup
	 * wearing lightbearer, which doubles energy regeneration at the cost of its ring.
	 */
	private Specced special(Outfit worn, Combat cls, Dps ordinary, long wornCost)
	{
		if (!settings.isKillSpecials() || ctx.getModifiers().isSpecialAttack())
		{
			return null;
		}
		double regen = SpecialRotation.REGEN_PER_SECOND * (lightbearer(worn) ? 2 : 1);
		Specced best = special(worn, cls, ordinary, wornCost, regen);
		Outfit lit = withLightbearer(worn);
		if (lit != null)
		{
			Dps litOrdinary = calculate(lit);
			long litCost = cost(lit);
			if (litOrdinary.getDps() > 0 && withinBudget(litCost))
			{
				Specced s = special(lit, cls, litOrdinary, litCost, 2 * SpecialRotation.REGEN_PER_SECOND);
				if (s != null && (best == null || metric(s.getResult()) > metric(best.getResult()) + EPS))
				{
					best = s;
				}
			}
		}
		return best;
	}

	private static boolean lightbearer(Outfit l)
	{
		Gear ring = l.get(Slot.RING);
		return ring != null && ring.getId() == ItemID.LIGHTBEARER;
	}

	/** The worn setup with lightbearer in place of its ring, or null when it can't or already does. */
	private Outfit withLightbearer(Outfit worn)
	{
		Gear ring = data.getItem(Slot.RING, ItemID.LIGHTBEARER);
		if (ring == null || lightbearer(worn) || settings.getLocks().containsKey(Slot.RING) || !usable(ring))
		{
			return null;
		}
		Outfit lit = worn.copy();
		lit.set(Slot.RING, ring);
		return protectedSetup(lit) && withinWildernessRisk(lit) ? lit : null;
	}

	/**
	 * Candidates are the worn weapon and every usable weapon of the same combat class with a damaging spec,
	 * held with the worn armour, plus an off-hand when a one-handed spec weapon leaves the shield slot empty.
	 * They are bought within what the budget has left.
	 */
	private Specced special(Outfit worn, Combat cls, Dps ordinary, long wornCost, double regen)
	{
		int hp = ctx.getTargetHitpoints();
		// Ordinary attacks after each set of landed drains, shared by every draining candidate.
		Map<SpecialAttacks, Dps> drainedOrdinary = new HashMap<>();
		drainedOrdinary.put(SpecialAttacks.NONE, ordinary);
		Specced best = null;
		double bestScore = metric(ordinary);
		for (Gear weapon : specWeapons(cls))
		{
			Outfit base = specLoadout(worn, weapon);
			if (base == null)
			{
				continue;
			}
			boolean same = weapon == worn.getWeapon();
			long weaponCost = same ? 0 : itemCost(weapon) + ammoCost(base.getLoadedAmmo());
			for (Option o : options(weapon, cls, true))
			{
				o.apply(base);
				SpecialAttack spec = SpecialAttack.of(base, specCtx);
				if (spec == null || spec.getEnergy() <= 0)
				{
					continue;
				}
				// The ammunition, then the off-hand, that make this spec hit hardest (they hardly interact, and
				// trying every pair would multiply the candidates).
				Held ammoChoice = strongest(worn, base, Slot.AMMO, specAmmo(worn, weapon), weaponCost, wornCost);
				Held choice = ammoChoice == null ? null
					: strongest(worn, ammoChoice.getLoadout(), Slot.SHIELD, offhands(worn, weapon), ammoChoice.getCost(), wornCost);
				if (choice == null)
				{
					continue;
				}
				Outfit held = choice.getLoadout();
				Dps opening = choice.getResult();
				long extra = choice.getCost();
				SpecialRotation.Blend blend;
				Dps drained = null;
				if (spec.getDrain() == SpecialAttack.Drain.NONE || settings.getDrainSpecs() == DrainSpecs.DAMAGE_ONLY)
				{
					blend = SpecialRotation.blend(ordinary, opening, spec.getEnergy(), hp, settings.getSpecEnergy(), regen);
				}
				else
				{
					SpecialRotation.Fight fight = drainingFight(worn, held, opening, drainedOrdinary);
					blend = SpecialRotation.draining(fight, spec.getDrain(), spec.getEnergy(), hp,
						settings.getSpecEnergy(), regen, settings.getDrainSpecs() == DrainSpecs.ALL_MISS);
					// Shown as the ordinary attacks after one landed spec of average damage.
					int damage = (int) Math.round(opening.getAccuracy() > 0
						? opening.getPrimaryDps() * opening.getExpectedSpeedTicks() * 0.6 / opening.getAccuracy() : 0);
					drained = fight.ordinary(SpecialAttacks.NONE.landed(spec.getDrain(), damage));
				}
				if (blend == null)
				{
					continue;
				}
				double score = metric(blend.getResult());
				if (score > bestScore + EPS || best != null && Math.abs(score - bestScore) <= EPS
					&& extra < best.getPlan().getExtraCost())
				{
					Gear offhand = held.get(Slot.SHIELD) == worn.get(Slot.SHIELD) ? null : held.get(Slot.SHIELD);
					Gear specAmmo = held.get(Slot.AMMO) == worn.get(Slot.AMMO) ? null : held.get(Slot.AMMO);
					int unpriced = same ? 0 : (unpriced(weapon) ? 1 : 0) + (unpriced(held.getLoadedAmmo()) ? 1 : 0);
					unpriced += (unpriced(offhand) ? 1 : 0) + (unpriced(specAmmo) ? 1 : 0);
					best = new Specced(new SpecialPlan(weapon, offhand, specAmmo, o.getStyle(), o.getSpell(),
						held.getLoadedAmmo(), spec, opening, ordinary, drained, blend.getSpecsPerKill(), extra, unpriced),
						blend.getResult(), worn);
					bestScore = score;
				}
			}
		}
		return best;
	}

	/** Ordinary and spec attacks once drains land; each state is calculated once. */
	private SpecialRotation.Fight drainingFight(Outfit worn, Outfit held, Dps opening,
		Map<SpecialAttacks, Dps> drainedOrdinary)
	{
		Map<SpecialAttacks, Dps> specs = new HashMap<>();
		specs.put(SpecialAttacks.NONE, opening);
		return new SpecialRotation.Fight()
		{
			@Override
			public Dps ordinary(SpecialAttacks drains)
			{
				return drainedOrdinary.computeIfAbsent(drains, d -> DpsCalculator.calculate(worn, ctx.withDrains(d)));
			}

			@Override
			public Dps spec(SpecialAttacks drains)
			{
				return specs.computeIfAbsent(drains, d -> DpsCalculator.calculate(held, specCtx.withDrains(d)));
			}
		};
	}

	/**
	 * Off-hands to hold with the spec weapon: none, or when a one-handed spec weapon leaves the worn shield
	 * slot empty (after a two-handed main weapon), the class's candidate shields too.
	 */
	private List<Gear> offhands(Outfit worn, Gear weapon)
	{
		if (weapon.isTwoHanded() || worn.get(Slot.SHIELD) != null || settings.getLocks().containsKey(Slot.SHIELD)
			|| specShields.isEmpty())
		{
			return Collections.singletonList(null);
		}
		List<Gear> out = new ArrayList<>();
		out.add(null);
		out.addAll(specShields);
		return out;
	}

	/**
	 * The spec loadout with the item from {@code items} (null keeps the slot) that makes the spec hit hardest,
	 * cheapest on ties, or null when none is protected, affordable and within the Wilderness risk limit together
	 * with the {@code worn} setup it is carried alongside.
	 */
	private Held strongest(Outfit worn, Outfit base, Slot slot, List<Gear> items, long baseCost, long wornCost)
	{
		Held best = null;
		for (Gear item : items)
		{
			Outfit l = base.copy();
			if (item != null)
			{
				l.set(slot, item);
			}
			long c = baseCost + (item == null ? 0 : itemCost(item));
			if (!protectedEquipment(l) || !withinBudget(wornCost + c) || !withinWildernessRisk(worn, l))
			{
				continue;
			}
			probe.fill(l);
			Dps r = specResults.get(probe);
			if (r == null)
			{
				r = DpsCalculator.calculate(l, specCtx);
				specResults.put(probe.copy(), r);
			}
			if (best == null || r.getPrimaryDps() > best.getResult().getPrimaryDps() + EPS
				|| Math.abs(r.getPrimaryDps() - best.getResult().getPrimaryDps()) <= EPS && c < best.getCost())
			{
				best = new Held(l, r, c);
			}
		}
		return best;
	}

	/**
	 * Ammunition to equip with the spec weapon: none to change (null), or for an ammo-slot spec weapon each
	 * compatible ammunition, with the worn ammunition kept as null when it fits. Empty when none can be used.
	 */
	private List<Gear> specAmmo(Outfit worn, Gear weapon)
	{
		if (weapon == worn.getWeapon() || !firesAmmoSlot(weapon))
		{
			return Collections.singletonList(null);
		}
		List<Gear> out = new ArrayList<>();
		for (Gear a : compatibleAmmo(weapon, true))
		{
			out.add(a == worn.get(Slot.AMMO) ? null : a);
		}
		return out;
	}

	/**
	 * The worn setup holding the spec weapon instead, or null when it cannot fire: a blowpipe brings its own
	 * darts (ammo-slot ammunition is chosen separately).
	 */
	private Outfit specLoadout(Outfit worn, Gear weapon)
	{
		Outfit l = worn.copy();
		if (weapon == worn.getWeapon())
		{
			return l;
		}
		l.set(Slot.WEAPON, weapon);
		l.setSpell(null);
		l.setLoadedAmmo(null);
		if (weapon.isTwoHanded())
		{
			l.set(Slot.SHIELD, null);
		}
		if (loadsAmmo(weapon))
		{
			List<Gear> darts = compatibleAmmo(weapon, false);
			if (darts.isEmpty())
			{
				return null;
			}
			l.setLoadedAmmo(darts.get(0));
		}
		return l;
	}

	/** Usable weapons of the class with a damaging special attack; weapon locks and hand limits apply to the worn weapon only. */
	private List<Gear> specWeapons(Combat cls)
	{
		return specWeapons.computeIfAbsent(cls, c ->
		{
			List<Gear> out = new ArrayList<>();
			for (Gear w : data.getItems(Slot.WEAPON))
			{
				if (usable(w) && supports(w, c) && hasSpecialAttack(w, c))
				{
					out.add(w);
				}
			}
			return withoutCosmeticCopies(out);
		});
	}

	// ------------------------------------------------------------ fill phase

	/**
	 * Fill slots that add nothing with prayer or defence items, allowing up to the configured DPS
	 * margin to be lost. Slots locked to FILL are always filled. Returns the filled slots.
	 */
	private Set<Slot> fill(Outfit l)
	{
		boolean anyForced = settings.getLocks().values().stream().anyMatch(k -> k.getKind() == SlotLock.Kind.FILL);
		if (settings.getFillMode() == FillMode.NONE && !anyForced)
		{
			return Collections.emptySet();
		}
		Gear weapon = l.getWeapon();
		boolean firesAmmo = firesAmmoSlot(weapon);
		Dps base = DpsCalculator.calculate(l, ctx);
		double minDps = base.getDps() * (1 - Math.max(0, settings.getFillMarginPercent()) / 100) - EPS;
		double basePrimary = primary(base);
		ToDoubleFunction<Gear> metric = fillMetric(settings.getFillMode() == FillMode.NONE
			? FillMode.DEFENCE : settings.getFillMode());

		Set<Slot> filled = EnumSet.noneOf(Slot.class);
		long curCost = cost(l);
		for (Slot slot : SLOTS)
		{
			SlotLock lock = settings.getLocks().get(slot);
			boolean forced = lock != null && lock.getKind() == SlotLock.Kind.FILL;
			if (slot == Slot.WEAPON || (slot == Slot.AMMO && firesAmmo) || (slot == Slot.SHIELD && weapon.isTwoHanded())
				|| (lock != null && !forced) || (!forced && settings.getFillMode() == FillMode.NONE))
			{
				continue;
			}
			Gear orig = l.get(slot);
			Gear bestItem = orig;
			double bestMetric = orig == null ? 0 : metric.applyAsDouble(orig);
			long bestCost = curCost;
			long baseCost = curCost - itemCost(orig);
			for (Gear cand : data.getItems(slot))
			{
				if (cand == orig || !usable(cand))
				{
					continue;
				}
				double m = metric.applyAsDouble(cand);
				long c = baseCost + itemCost(cand);
				if (m < bestMetric - EPS || (Math.abs(m - bestMetric) <= EPS && c >= bestCost) || !withinBudget(c))
				{
					continue;
				}
				l.set(slot, cand);
				Dps r = DpsCalculator.calculate(l, ctx);
				boolean acceptable = forced || (r.getDps() >= minDps
					&& (settings.getCalcMode() == CalcMode.DPS || primary(r) >= basePrimary - EPS));
				if (acceptable && protectedSetup(l) && withinWildernessRisk(l))
				{
					bestItem = cand;
					bestMetric = m;
					bestCost = c;
				}
			}
			l.set(slot, bestItem);
			if (bestItem != orig)
			{
				filled.add(slot);
				curCost = bestCost;
			}
		}
		return filled;
	}

	// ------------------------------------------------------------ pruning

	/**
	 * Take off every piece that makes no meaningful difference to the setup: its ordinary attack's rates and calc-mode
	 * figure, and the hit of the spec weapon the kill switches to ({@code spec}, null without one), are the same (within {@link #MEANINGFUL_BENEFIT})
	 * with the slot empty. Such a piece was worn for a bonus that never paid off, e.g. strength short of the next max
	 * hit, and would otherwise be carried or bought for nothing. Set pieces, salve and slayer
	 * effects, protection the target demands and anything moving accuracy, max hit or speed all change a rate, so they
	 * stay. The weapon, the ammunition it fires, locked slots, slots filled for prayer or defence, pieces with the fill
	 * mode's bonus (the mode asked for them) and lightbearer behind a spec plan (it pays in energy, not rates) also stay.
	 */
	private void prune(Outfit l, Set<Slot> filled, SpecialPlan spec)
	{
		boolean firesAmmo = firesAmmoSlot(l.getWeapon());
		ToDoubleFunction<Gear> fillBonus = settings.getFillMode() == FillMode.NONE ? null : fillMetric(settings.getFillMode());
		Dps worn = calculate(l);
		for (Slot slot : SLOTS)
		{
			Gear item = l.get(slot);
			if (item == null || slot == Slot.WEAPON || (slot == Slot.AMMO && firesAmmo) || isLocked(slot) || filled.contains(slot)
				|| (fillBonus != null && fillBonus.applyAsDouble(item) > 0)
				|| (slot == Slot.RING && spec != null && lightbearer(l)))
			{
				continue;
			}
			l.set(slot, null);
			if (differs(worn, calculate(l)) || spec != null && specDiffers(l, slot, item, spec))
			{
				l.set(slot, item);
			}
		}
	}

	/**
	 * Whether the spec's hit changes when {@code item} comes off {@code slot} of the worn setup ({@code worn} already
	 * has it off). An item the spec replaces with its own off-hand or ammunition, or a shield a two-handed spec weapon
	 * takes off, isn't held during the spec.
	 */
	private boolean specDiffers(Outfit worn, Slot slot, Gear item, SpecialPlan plan)
	{
		Outfit without = heldFor(worn, plan);
		if (without.get(slot) != null || slot == Slot.SHIELD && plan.getWeapon().isTwoHanded())
			return false;
		Outfit with = without.copy();
		with.set(slot, item);
		return differs(DpsCalculator.calculate(with, specCtx), DpsCalculator.calculate(without, specCtx));
	}

	/** The worn setup holding a spec plan's weapon, stance, spell, darts, off-hand and ammunition. */
	private static Outfit heldFor(Outfit worn, SpecialPlan plan)
	{
		Outfit held = worn.copy();
		held.set(Slot.WEAPON, plan.getWeapon());
		held.setStyle(plan.getStyle());
		held.setSpell(plan.getSpell());
		held.setLoadedAmmo(plan.getLoadedAmmo());
		if (plan.getWeapon().isTwoHanded())
		{
			held.set(Slot.SHIELD, null);
		}
		if (plan.getOffhand() != null)
		{
			held.set(Slot.SHIELD, plan.getOffhand());
		}
		if (plan.getAmmo() != null)
		{
			held.set(Slot.AMMO, plan.getAmmo());
		}
		return held;
	}

	/** Whether two results differ meaningfully in a damage rate or in the calc mode's figure. */
	private boolean differs(Dps a, Dps b)
	{
		return differs(a.getDps(), b.getDps()) || differs(a.getPrimaryDps(), b.getPrimaryDps())
			|| differs(primary(a), primary(b));
	}

	private static boolean differs(double a, double b)
	{
		return Math.abs(a - b) > MEANINGFUL_BENEFIT * Math.max(Math.abs(a), Math.abs(b));
	}

	// ------------------------------------------------------------ fill metrics

	private ToDoubleFunction<Gear> fillMetric(FillMode mode)
	{
		if (mode == FillMode.PRAYER)
		{
			return Gear::getPrayerBonus;
		}
		switch (settings.getDefenceFocus())
		{
			case STAB:
				return Gear::getStabDef;
			case SLASH:
				return Gear::getSlashDef;
			case CRUSH:
				return Gear::getCrushDef;
			case MELEE:
				return i -> i.getStabDef() + i.getSlashDef() + i.getCrushDef();
			case MAGIC:
				return Gear::getMagicDef;
			case RANGED:
				return Gear::getRangedDef;
			case TOTAL:
				return i -> i.getStabDef() + i.getSlashDef() + i.getCrushDef() + i.getMagicDef() + i.getRangedDef();
			default:
				return targetDefenceMetric();
		}
	}

	/** Weighted by how often the monster uses each attack type (typeless attacks ignored). */
	private ToDoubleFunction<Gear> targetDefenceMetric()
	{
		double[] w = new double[5];
		if (monster.getAttackStyles() != null)
		{
			for (Map.Entry<String, Monster.MonsterAttack> e : monster.getAttackStyles().entrySet())
			{
				Monster.MonsterAttack a = e.getValue();
				if (a == null || a.isTypeless())
				{
					continue;
				}
				String k = e.getKey().toLowerCase(Locale.ROOT);
				double weight = a.getWeighting() > 0 ? a.getWeighting() : 1;
				if (k.contains("stab"))
				{
					w[0] += weight;
				}
				else if (k.contains("slash"))
				{
					w[1] += weight;
				}
				else if (k.contains("crush") || k.equals("melee"))
				{
					w[2] += weight;
				}
				else if (k.contains("magic") || k.contains("mage"))
				{
					w[3] += weight;
				}
				else if (k.contains("ranged") || k.contains("range"))
				{
					w[4] += weight;
				}
			}
		}
		if (w[0] + w[1] + w[2] + w[3] + w[4] <= 0)
		{
			return i -> i.getStabDef() + i.getSlashDef() + i.getCrushDef() + i.getMagicDef() + i.getRangedDef();
		}
		return i -> w[0] * i.getStabDef() + w[1] * i.getSlashDef() + w[2] * i.getCrushDef()
			+ w[3] * i.getMagicDef() + w[4] * i.getRangedDef();
	}

	// ------------------------------------------------------------ candidates

	/**
	 * Per-slot armour candidates: the usable items no other usable item dominates, plus items with special effects
	 * and required protection. Package-private so tests can audit the pruning.
	 */
	Map<Slot, List<Gear>> candidates(Combat cls)
	{
		List<ToDoubleFunction<Gear>> stats = damageStats(cls);
		Map<Slot, List<Gear>> out = new EnumMap<>(Slot.class);
		for (Slot slot : SLOTS)
		{
			if (slot == Slot.WEAPON || slot == Slot.AMMO)
			{
				continue;
			}
			List<Gear> pool = new ArrayList<>();
			for (Gear item : data.getItems(slot))
			{
				if (usable(item))
				{
					pool.add(item);
				}
			}
			Set<Gear> keep = new LinkedHashSet<>(nondominated(pool, stats));
			for (Gear item : pool)
			{
				if (isSpecial(item) || DragonfireProtection.isProtectiveShield(item)
					|| SlayerEquipment.isRequired(monster, settings, item)
					|| recoveryRequired() && isAmmoRecoveryCape(item) && !outranked(item, pool))
				{
					keep.add(item);
				}
			}
			out.put(slot, new ArrayList<>(keep));
		}
		return out;
	}

	/** The item bonuses the class's damage reads; damage never falls as one of them rises. */
	private List<ToDoubleFunction<Gear>> damageStats(Combat cls)
	{
		List<ToDoubleFunction<Gear>> m = new ArrayList<>();
		switch (cls)
		{
			case MELEE:
				m.add(Gear::getStabBonus);
				m.add(Gear::getSlashBonus);
				m.add(Gear::getCrushBonus);
				m.add(Gear::getMeleeStr);
				// Defence-derived strength reads this sum.
				m.add(i -> i.getStabDef() + i.getSlashDef() + i.getCrushDef() + i.getRangedDef());
				break;
			case RANGED:
				m.add(Gear::getRangedBonus);
				m.add(Gear::getRangedStr);
				if (settings.getStyles().contains(Style.Type.ATLATL) || strengthRangedWeaponUsable())
				{
					// The atlatl and Hunter's spear hit with melee strength but aim with ranged attack.
					m.add(Gear::getMeleeStr);
				}
				break;
			default:
				m.add(Gear::getMagicBonus);
				m.add(Gear::getMagicStr);
				break;
		}
		if (araxyte || hueyTail)
		{
			// These targets reward an attack bonus above the others, so a lower other bonus can be worth more.
			m.add(i -> -i.getStabBonus());
			m.add(i -> -i.getSlashBonus());
			m.add(i -> -i.getCrushBonus());
			m.add(i -> -i.getRangedBonus());
			m.add(i -> -i.getMagicBonus());
		}
		return m;
	}

	private boolean strengthRangedWeaponUsable()
	{
		for (Gear weapon : data.getItems(Slot.WEAPON))
		{
			if (scalesWithStrength(weapon) && !isAtlatl(weapon) && usable(weapon))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Items that neither another item nor the empty slot dominates: at least as high in every damage stat, no
	 * dearer, and not expensive under the cap where the other isn't. Swapping a dominated item for its dominator
	 * never lowers the score, raises the cost or breaks the cap, so no optimum is lost. A union of single-stat
	 * fronts is not enough: +1 attack and +1 strength can beat both +3 attack and +3 strength when +1 strength
	 * already reaches the same max hit.
	 */
	private List<Gear> nondominated(List<Gear> pool, List<ToDoubleFunction<Gear>> stats)
	{
		int n = pool.size();
		double[][] values = new double[n][stats.size()];
		long[] costs = new long[n];
		boolean[] risky = new boolean[n];
		for (int i = 0; i < n; i++)
		{
			Gear item = pool.get(i);
			for (int k = 0; k < stats.size(); k++)
			{
				values[i][k] = stats.get(k).applyAsDouble(item);
			}
			costs[i] = itemCost(item);
			risky[i] = settings.isWildernessRiskLimited() && expensive(item);
		}
		List<Gear> out = new ArrayList<>();
		for (int i = 0; i < n; i++)
		{
			boolean dominated = true;
			for (double v : values[i])
			{
				// The empty slot dominates an item with no positive damage stat.
				dominated &= v <= EPS;
			}
			for (int j = 0; j < n && !dominated; j++)
			{
				dominated = j != i && dominates(pool, values, costs, risky, j, i);
			}
			if (!dominated)
			{
				out.add(pool.get(i));
			}
		}
		return out;
	}

	/**
	 * Equipment that changes this weapon's score only through its bonuses, the score never falling as one rises: no
	 * named effect the calculator checks can apply with this weapon, option, ammunition and target ({@code trial}), and
	 * it isn't protection or ammunition recovery the setup may need. Effects not recognised here count as applying.
	 */
	private boolean plain(Gear item, Outfit trial)
	{
		Gear weapon = trial.getWeapon();
		if (DragonfireProtection.isProtectiveShield(item) || SlayerEquipment.isRequired(monster, settings, item)
			|| isAmmoRecoveryCape(item) && recoveryRequired()
				&& firesRecoverableAmmo(weapon, boundClass))
		{
			return false;
		}
		// Effects match the calculator's name, which drops the "corrupted " of Deadman items.
		String name = lower(item);
		// Coloured slayer helmets ("hydra slayer helmet") have the effect without the special name.
		if (!specialName(item.getLowerCombatName()) && !specialName(name) && !name.contains("max cape")
			&& !DpsCalculator.isSlayerHelm(item))
		{
			return true;
		}
		return !ctx.getModifiers().isSpecialAttack() && !effectApplies(item, name, trial);
	}

	/** Whether a named effect of the item could change this weapon's score against this target; true if unsure. */
	private boolean effectApplies(Gear item, String name, Outfit trial)
	{
		Combat cls = boundClass;
		Gear weapon = trial.getWeapon();
		String w = lower(weapon);
		// Target stats can follow its remaining HP; keep target-specific effects whenever they might.
		boolean fixedTarget = ctx.getHealthStates() == null;
		// The pieces of whole sets (Barrows, moon, obsidian, void) never get here: effect() takes them first.
		if (name.startsWith("berserker necklace"))
		{
			return cls == Combat.MELEE && (w.startsWith("toktz-") || w.startsWith("tzhaar-ket-"));
		}
		if (name.startsWith("inquisitor's "))
		{
			return cls == Combat.MELEE && trial.getStyle().getType() == Style.Type.CRUSH;
		}
		if (name.startsWith("crystal helm") || name.startsWith("crystal body") || name.startsWith("crystal legs"))
		{
			return cls == Combat.MELEE && Effects.named(trial, Slot.AMMO, "crystal blessing")
				|| cls == Combat.RANGED && (w.startsWith("bow of faerdhinen") || w.startsWith("crystal bow"));
		}
		if (MAGIC_ONLY_GEAR.matches(name))
		{
			// Each needs its spellbook, spell or element (DpsCalculator's magic rules).
			Spell spell = trial.getSpell();
			String book = spell == null || spell.getSpellbook() == null ? "" : spell.getSpellbook().toLowerCase(Locale.ROOT);
			String element = SpellRules.element(spell);
			return cls == Combat.MAGIC && (name.startsWith("virtus ") ? book.equals("ancient")
				: name.startsWith("chaos gauntlets") ? book.equals("standard") && spell(trial).endsWith(" bolt")
				: name.startsWith("confliction gauntlets") ? !weapon.isTwoHanded()
				: name.startsWith("brimstone ring") || !element.isEmpty() && !name.contains("empty")
					&& (name.startsWith("elemental amulet") || name.contains(element)));
		}
		if ((name.contains("saradomin") || name.contains("guthix") || name.contains("zamorak")) && name.contains("cape"))
		{
			// Only Charge reads the god capes.
			return cls == Combat.MAGIC && ctx.getModifiers().isCharge();
		}
		if (name.startsWith("dizana's") || name.startsWith("blessed dizana's"))
		{
			return cls == Combat.RANGED;
		}
		if (name.startsWith("salve amulet"))
		{
			// Ranged and Magic need it imbued, except the atlatl's strength-scaled max hit.
			return (!fixedTarget || monster.hasAttribute("undead"))
				&& (cls == Combat.MELEE || name.contains("i)") || isAtlatl(weapon));
		}
		if (name.startsWith("amulet of avarice"))
		{
			return !fixedTarget || monster.getLowerName().startsWith("revenant");
		}
		if (name.startsWith("efaritay's aid"))
		{
			return !fixedTarget || monster.hasAttribute("vampyre (t1)") || monster.hasAttribute("vampyre (t2)")
				|| monster.hasAttribute("vampyre (t3)");
		}
		if (DpsCalculator.isSlayerHelm(item))
		{
			return ctx.isOnTask();
		}
		// Other max capes read by name are the god, Dizana's and recovery capes above.
		return !name.contains("max cape");
	}

	private boolean dominates(List<Gear> pool, double[][] values, long[] costs, boolean[] risky, int j, int i)
	{
		if (costs[j] > costs[i] || risky[j] && !risky[i])
		{
			return false;
		}
		boolean strict = costs[j] < costs[i] || risky[i] && !risky[j];
		for (int k = 0; k < values[i].length; k++)
		{
			if (values[j][k] < values[i][k] - EPS)
			{
				return false;
			}
			strict |= values[j][k] > values[i][k] + EPS;
		}
		if (strict)
		{
			return true;
		}
		// Equal in everything the search scores: keep other bonuses the fill phase may use (Fury vs Glory), and
		// only one of exact duplicates.
		Gear a = pool.get(j);
		Gear b = pool.get(i);
		return betterBonuses(a, b) || noWorseBonuses(a, b) && noWorseBonuses(b, a) && preferredDuplicate(a, b);
	}

	/** Whether another item in the pool is an exact duplicate of this one that is preferred to it. */
	private boolean outranked(Gear item, List<Gear> pool)
	{
		for (Gear other : pool)
		{
			if (other != item && noWorseBonuses(other, item) && noWorseBonuses(item, other)
				&& itemCost(other) == itemCost(item) && expensive(other) == expensive(item)
				&& isSpecial(other) == isSpecial(item) && preferredDuplicate(other, item))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Of two exact duplicates: the one held, then a catalogue item over a cosmetic copy, then the newer catalogue
	 * item, so kitted and upgraded versions (Masori assembler over Ava's assembler) win over the base.
	 */
	private boolean preferredDuplicate(Gear a, Gear b)
	{
		if (isOwned(a) != isOwned(b))
		{
			return isOwned(a);
		}
		boolean catalogueA = a.getId() == a.getCombatId();
		if (catalogueA != (b.getId() == b.getCombatId()))
		{
			return catalogueA;
		}
		return catalogueA ? a.getId() > b.getId() : a.getId() < b.getId();
	}

	/** Prefer a strict stat upgrade when score and acquisition cost tie; avoid arbitrary swap cycles. */
	private static boolean betterBonuses(Gear candidate, Gear current)
	{
		return noWorseBonuses(candidate, current) && !noWorseBonuses(current, candidate);
	}

	/** Package-private: {@link CosmeticVariants#equivalent} compares the same bonuses. */
	static boolean noWorseBonuses(Gear candidate, Gear current)
	{
		return candidate != null && current != null
			&& candidate.getStabBonus() >= current.getStabBonus()
			&& candidate.getSlashBonus() >= current.getSlashBonus()
			&& candidate.getCrushBonus() >= current.getCrushBonus()
			&& candidate.getRangedBonus() >= current.getRangedBonus()
			&& candidate.getMagicBonus() >= current.getMagicBonus()
			&& candidate.getMeleeStr() >= current.getMeleeStr()
			&& candidate.getRangedStr() >= current.getRangedStr()
			&& candidate.getMagicStr() >= current.getMagicStr()
			&& candidate.getStabDef() >= current.getStabDef()
			&& candidate.getSlashDef() >= current.getSlashDef()
			&& candidate.getCrushDef() >= current.getCrushDef()
			&& candidate.getRangedDef() >= current.getRangedDef()
			&& candidate.getMagicDef() >= current.getMagicDef()
			&& candidate.getPrayerBonus() >= current.getPrayerBonus();
	}

	private static boolean isSpecial(Gear item)
	{
		return specialName(item.getLowerCombatName());
	}

	private static boolean specialName(String n)
	{
		return SPECIAL_GEAR.matches(n);
	}

	private List<Gear> weapons(Combat cls)
	{
		SlotLock weaponLock = settings.getLocks().get(Slot.WEAPON);
		SlotLock shieldLock = settings.getLocks().get(Slot.SHIELD);
		boolean shieldTaken = shieldLock != null && shieldLock.getKind() != SlotLock.Kind.EMPTY;
		List<Gear> out = new ArrayList<>();
		List<Gear> byHand = new ArrayList<>();
		for (Gear w : data.getItems(Slot.WEAPON))
		{
			if (!usable(w))
			{
				continue;
			}
			if (settings.isWildernessRiskLimited() && settings.getMaxExpensiveItems() <= 0 && expensive(w))
			{
				continue;
			}
			if (weaponLock != null)
			{
				// Locks restrict the candidates but never bypass wear or availability rules.
				if (weaponLock.getKind() != SlotLock.Kind.ITEM || w.getId() != weaponLock.getItemId()
					|| settings.getExcluded().contains(w.getId()))
				{
					continue;
				}
			}
			if (settings.getWeaponHands() == WeaponHands.ONE_HANDED && w.isTwoHanded()
				|| settings.getWeaponHands() == WeaponHands.TWO_HANDED && !w.isTwoHanded()
				|| shieldTaken && w.isTwoHanded())
			{
				continue;
			}
			if (!supports(w, cls))
			{
				if (cls == Combat.MAGIC && !ctx.getModifiers().isSpecialAttack() && !settings.isPreventManualCast())
				{
					byHand.add(w);
				}
				continue;
			}
			if (ctx.getModifiers().isSpecialAttack() && !hasSpecialAttack(w, cls))
			{
				continue;
			}
			out.add(w);
		}
		// With no Magic weapon left (none held, or the weapon locked to another kind), spells are cast by hand.
		return withoutCosmeticCopies(out.isEmpty() ? byHand : out);
	}

	/**
	 * One weapon per set of cosmetic variants: the owned or cheapest priced one, then the snapshot item. Each copy
	 * would repeat the same ascent and crowd distinct weapons out of the capped preselection and refinement sweeps.
	 * A cheaper copy over the Wilderness value limit doesn't replace a dearer one under it, so both stay.
	 */
	private List<Gear> withoutCosmeticCopies(List<Gear> weapons)
	{
		Map<Integer, List<Gear>> byCombatId = new HashMap<>();
		for (Gear w : weapons)
		{
			byCombatId.computeIfAbsent(w.getCombatId(), k -> new ArrayList<>()).add(w);
		}
		List<Gear> out = new ArrayList<>(weapons.size());
		for (Gear w : weapons)
		{
			boolean replaced = false;
			for (Gear other : byCombatId.get(w.getCombatId()))
			{
				if (other != w && preferredCopy(other, w) && CosmeticVariants.equivalent(other, w))
				{
					replaced = true;
					break;
				}
			}
			if (!replaced)
			{
				out.add(w);
			}
		}
		return out;
	}

	/** Whether {@code a} serves wherever {@code b} does, at no greater cost or Wilderness risk. */
	private boolean preferredCopy(Gear a, Gear b)
	{
		// An unknown price is never assumed free: a priced copy wins over an unpriced one.
		long costA = unpriced(a) ? Long.MAX_VALUE : itemCost(a);
		long costB = unpriced(b) ? Long.MAX_VALUE : itemCost(b);
		boolean riskyA = settings.isWildernessRiskLimited() && expensive(a);
		boolean riskyB = settings.isWildernessRiskLimited() && expensive(b);
		if (costA > costB || riskyA && !riskyB)
		{
			return false;
		}
		if (costA < costB || riskyB && !riskyA)
		{
			return true;
		}
		// Free and untradeable copies tie on cost: the one held is the one to show and highlight.
		if (isOwned(a) != isOwned(b))
		{
			return isOwned(a);
		}
		boolean snapshotA = a.getId() == a.getCombatId();
		boolean snapshotB = b.getId() == b.getCombatId();
		return snapshotA != snapshotB ? snapshotA : a.getId() < b.getId();
	}

	private boolean recoveryRequired()
	{
		if (recoveryRequired == null)
		{
			// A cape locked to anything else, or left empty, lifts the rule rather than blocking ranged.
			SlotLock lock = settings.getLocks().get(Slot.CAPE);
			recoveryRequired = false;
			for (Gear cape : data.getItems(Slot.CAPE))
			{
				recoveryRequired |= settings.isRequireAtlatlAmmoRecovery() && isAmmoRecoveryCape(cape) && usable(cape)
					&& (lock == null || lock.getKind() == SlotLock.Kind.FILL
						|| lock.getKind() == SlotLock.Kind.ITEM && lock.getItemId() == cape.getId());
			}
		}
		return recoveryRequired;
	}

	/** Whether any of the weapon's styles in this class has a damaging special attack. */
	private boolean hasSpecialAttack(Gear weapon, Combat cls)
	{
		Outfit l = new Outfit();
		l.set(Slot.WEAPON, weapon);
		for (String raw : weapon.getStyles() == null ? java.util.Collections.<String>emptyList() : weapon.getStyles())
		{
			Style style = Style.parse(raw);
			if (style != null && classOf(style) == cls)
			{
				l.setStyle(style);
				if (SpecialAttack.of(l, ctx) != null)
				{
					return true;
				}
			}
		}
		return false;
	}

	private List<Option> options(Gear weapon, Combat cls)
	{
		return options(weapon, cls, false);
	}

	/** @param anyType allow every attack type of the class, not just the searched tab's (spec weapons) */
	private List<Option> options(Gear weapon, Combat cls, boolean anyType)
	{
		List<Option> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		boolean byHand = cls == Combat.MAGIC && !anyType && !ctx.getModifiers().isSpecialAttack()
			&& !settings.isPreventManualCast() && styleAllowed(weapon, Style.MANUAL_CAST, false);
		// Any weapon can cast a spell by hand, including one it can't autocast, unless the spell needs its own staff.
		List<Spell> spells = cls == Combat.MAGIC ? strongest(byHand ? autocastableSpells : spellsFor(weapon), weapon)
			: Collections.emptyList();
		boolean autocasts = false;
		boolean handOnly = HAND_CAST_STAVES.matches(weapon.getLowerCombatName());
		for (String raw : weapon.getStyles())
		{
			Style s = Style.parse(raw);
			if (s == null || classOf(s) != cls || !seen.add(s.getType() + "/" + s.getStance())
				|| !styleAllowed(weapon, s, anyType))
			{
				continue;
			}
			if (cls == Combat.MAGIC)
			{
				if (weapon.getSubcategory().equals("salamander") && salamanderMaxHit(weapon, ctx.getMagic()) > 0)
				{
					out.add(new Option(s, null));
				}
				else if (s.isAutocast())
				{
					autocasts = true;
					for (Spell spell : spells)
					{
						if (spell.castableWith(weapon.getCombatId()) && !(handOnly && staffBound(spell)))
						{
							out.add(new Option(s, spell));
						}
					}
				}
				else if (isPoweredStaff(weapon) && poweredStaffMaxHit(weapon, ctx.getMagic()) > 0)
				{
					out.add(new Option(s, null));
				}
			}
			else
			{
				out.add(new Option(s, null));
			}
		}
		if (byHand)
		{
			for (Spell spell : spells)
			{
				boolean castable = spell.castableWith(weapon.getCombatId());
				boolean bound = staffBound(spell);
				if ((!autocasts || !castable || handOnly && bound) && (castable || !bound))
				{
					out.add(new Option(Style.MANUAL_CAST, spell));
				}
			}
		}
		return out;
	}

	/** Reach, attack type and experience filter. */
	private boolean styleAllowed(Gear weapon, Style s, boolean anyType)
	{
		if (!AttackReach.canReach(monster, weapon, s, settings.getTargetDistance())
			|| !anyType && !settings.getStyles().contains(tabType(weapon, s)))
		{
			return false;
		}
		String stance = s.getStance();
		boolean melee = s.isMelee();
		boolean controlled = melee && stance.equals("controlled");
		boolean grantsAttack = melee && (stance.equals("accurate") || controlled);
		boolean grantsStrength = melee && (stance.equals("aggressive") || controlled);
		boolean grantsDefence = (melee && (stance.equals("defensive") || controlled)) || stance.equals("longrange");
		return (settings.isAttackXp() || !grantsAttack) && (settings.isStrengthXp() || !grantsStrength)
			&& (settings.isDefenceXp() || !grantsDefence);
	}

	/**
	 * The spells worth scoring with this weapon: a spell is dropped when a stronger tier of the same element or
	 * ancient school is also castable, since nothing the setup wears can make the weaker one win.
	 */
	private List<Spell> strongest(List<Spell> spells, Gear weapon)
	{
		if (chaosGauntlets == null)
		{
			chaosGauntlets = false;
			for (Gear hands : data.getItems(Slot.HANDS))
			{
				chaosGauntlets |= hands.getLowerCombatName().startsWith("chaos gauntlets") && usable(hands);
			}
		}
		boolean twinflame = weapon.getLowerCombatName().startsWith("twinflame staff");
		List<Spell> out = new ArrayList<>();
		for (Spell weak : spells)
		{
			boolean beaten = false;
			for (Spell strong : spells)
			{
				beaten |= SpellRules.outclasses(strong, weak, ctx.getMagic(), twinflame, chaosGauntlets, ctx.getAoeTargets() > 1);
			}
			if (!beaten || !pruneSpells)
			{
				out.add(weak);
			}
		}
		return out;
	}

	/** Whether the spell needs one of its own staves wielded to be cast at all. */
	public static boolean staffBound(Spell spell)
	{
		return STAFF_BOUND_SPELLS.matches(spell.getLowerName());
	}

	private List<Spell> spellsFor(Gear weapon)
	{
		List<Spell> out = new ArrayList<>();
		for (Spell spell : autocastableSpells)
		{
			if (spell.castableWith(weapon.getCombatId()))
			{
				out.add(spell);
			}
		}
		return out;
	}

	/** Spells that pass every non-weapon gate and the spell filter, in data order. */
	public static List<Spell> autocastSpells(GameData data, int magic, Monster monster, OptimizerSettings settings)
	{
		List<Spell> out = new ArrayList<>();
		for (Spell spell : data.getSpells())
		{
			String name = spell.getName().toLowerCase(Locale.ROOT);
			String book = spell.getSpellbook() == null ? "" : spell.getSpellbook().toLowerCase(Locale.ROOT);
			if (spell.getMaxHit() <= 0 || spell.getLevel() > magic
				|| !settings.getSpellbooks().contains(book))
			{
				continue;
			}
			if (name.contains("demonbane") && !monster.hasAttribute("demon"))
			{
				continue;
			}
			if (name.equals("crumble undead") && !monster.hasAttribute("undead"))
			{
				continue;
			}
			if (!settings.getSpellFilter().allows(spell))
			{
				continue;
			}
			out.add(spell);
		}
		return out;
	}

	private List<Gear> compatibleAmmo(Gear weapon, boolean firesAmmoSlot)
	{
		SlotLock ammoLock = firesAmmoSlot ? settings.getLocks().get(Slot.AMMO) : null;
		List<Gear> listed = new ArrayList<>();
		for (int id : weapon.getAmmunition())
		{
			Gear a = data.getItem(Slot.AMMO, id);
			if (a != null)
			{
				listed.add(a);
			}
		}
		// Cosmetic copies such as poisoned arrows have ids the Wiki's ammunition list doesn't name; they follow
		// their original, after it so equal-strength ties keep the listed order.
		for (Gear a : data.getItems(Slot.AMMO))
		{
			if (a.getCombatId() != a.getId() && weapon.getAmmunition().contains(a.getCombatId()))
			{
				listed.add(a);
			}
		}
		List<Gear> out = new ArrayList<>();
		for (Gear a : listed)
		{
			if (!usable(a))
			{
				continue;
			}
			if (ammoLock == null || ammoLock.getKind() == SlotLock.Kind.ITEM && ammoLock.getItemId() == a.getId())
			{
				out.add(a);
			}
		}
		out.sort(Comparator.comparingInt((Gear a) -> -a.getRangedStr()));
		return out;
	}

	// ------------------------------------------------------------ availability and cost

	private boolean usable(Gear item)
	{
		return equipmentAllowed(item)
			&& meetsRequirements(item)
			&& available(item);
	}

	private boolean equipmentAllowed(Gear item)
	{
		return !settings.getExcluded().contains(item.getId())
			&& CombatRules.equipmentAllowed(monster, item)
			&& (settings.isMembersItems() || !item.isMembers())
			&& (settings.isDmmItems() || !item.isDmmEquipment())
			&& (settings.isBetaItems() || !item.isBetaEquipment())
			&& (settings.isBountyHunterItems() || !item.isBountyHunterEquipment());
	}

	/** Ownership requires the exact item or a reviewed equivalent charge state. */
	private boolean isOwned(Gear item)
	{
		// get-then-put: computeIfAbsent would allocate a capturing lambda on every call in the cost loops.
		Boolean known = ownedItems.get(item);
		if (known == null)
		{
			known = owned.test(item.getId());
			for (int variant : ownershipVariants(item))
			{
				known = known || owned.test(variant);
			}
			ownedItems.put(item, known);
		}
		return known;
	}

	private List<Integer> ownershipVariants(Gear item)
	{
		return ownershipVariants.computeIfAbsent(item, Gear::getOwnershipVariants);
	}

	boolean available(Gear item)
	{
		// Best in slot has no limit: untradeables and every diary tier are assumed obtainable.
		if (settings.getMode() == SearchMode.UNLIMITED || isOwned(item) && ammoShortfall(item) == 0)
		{
			return true;
		}
		if (settings.getMode().isHeldOnly())
		{
			// The player chose to wear a locked item, so an owned-only search uses it even when it isn't held.
			// It still costs what buying it would (itemCost), so the result shows the price to pay.
			return locked(item);
		}
		if (DiaryRewards.isReward(item.getId()))
		{
			return false;
		}
		return purchasable(item) && withinBudget(item);
	}

	/** Whether the item is the one locked into its slot. */
	private boolean locked(Gear item)
	{
		SlotLock lock = item.getSlot() == null ? null : settings.getLocks().get(item.getSlot());
		return lock != null && lock.getKind() == SlotLock.Kind.ITEM && data.getItem(item.getSlot(), lock.getItemId()) == item;
	}

	/**
	 * Tradeable, or bought through tradable components (a charged staff, a demonic weapon's synapse, an
	 * imbued ring). Other untradeables (fire capes, quest gear) count in owned modes only when owned.
	 */
	private boolean purchasable(Gear item)
	{
		return !OwnershipRules.requiresOwnership(item.getId())
			&& (item.isTradeable() || ItemCosts.hasTradableComponents(item.getId()));
	}

	private boolean meetsRequirements(Gear i)
	{
		return levels.canEquip(i);
	}

	// ------------------------------------------------------------ lock diagnostics

	/** Why this item cannot be worn in this search, in plain words, or null if it can. */
	public String unusableReason(Gear item)
	{
		if (settings.getExcluded().contains(item.getId()))
		{
			return "you excluded it";
		}
		if (!CombatRules.equipmentAllowed(monster, item))
		{
			return "it can't be used against " + monster.getDisplayName();
		}
		if (!settings.isMembersItems() && item.isMembers())
		{
			return "members equipment is turned off";
		}
		if (!settings.isDmmItems() && item.isDmmEquipment())
		{
			return "DMM equipment is turned off";
		}
		if (!settings.isBetaItems() && item.isBetaEquipment())
		{
			return "beta equipment is turned off";
		}
		if (!settings.isBountyHunterItems() && item.isBountyHunterEquipment())
		{
			return "BH equipment is turned off";
		}
		String missing = levels.missingRequirements(item);
		if (missing != null)
		{
			return "it needs " + missing;
		}
		if (available(item))
		{
			return null;
		}
		if (DiaryRewards.isReward(item.getId()))
		{
			return settings.getMode() == SearchMode.INVENTORY_ONLY ? "this diary tier isn't in your inventory or equipment"
				: "you don't own this diary tier";
		}
		if (settings.getMode().isHeldOnly())
		{
			boolean carried = settings.getMode() == SearchMode.INVENTORY_ONLY;
			return isOwned(item) ? "you have " + heldQuantity(item) + " of the " + settings.getAmmoCount()
				+ " requested (Ammo quantity) and the search uses " + (carried ? "carried" : "owned") + " items only"
				: carried ? "it isn't in your inventory or equipment and the search uses carried items only"
				: "you don't own it and the search uses owned items only";
		}
		if (!purchasable(item))
		{
			return "it's untradeable and you don't own it";
		}
		long price = price(item);
		return !ItemCosts.isKnown(price) ? "it has no current Grand Exchange price"
			: "it costs " + Budget.format(price) + ", over your " + Budget.format(settings.getBudget()) + " budget";
	}

	/** Why a locked weapon gives no setup at all in this search, in plain words, or null if it can be searched. */
	public String weaponLockReason(Gear weapon)
	{
		String reason = unusableReason(weapon);
		if (reason != null)
		{
			return reason;
		}
		SlotLock shieldLock = settings.getLocks().get(Slot.SHIELD);
		if (weapon.isTwoHanded() && shieldLock != null && shieldLock.getKind() != SlotLock.Kind.EMPTY)
		{
			return "it's two-handed and the shield slot is locked";
		}
		if (settings.getWeaponHands() == WeaponHands.ONE_HANDED && weapon.isTwoHanded())
		{
			return "it's two-handed and the search only allows one-handed weapons";
		}
		if (settings.getWeaponHands() == WeaponHands.TWO_HANDED && !weapon.isTwoHanded())
		{
			return "it's one-handed and the search only allows two-handed weapons";
		}
		boolean anyStyle = false;
		for (Combat cls : Combat.values())
		{
			if (!supports(weapon, cls) || !classAllowed(monster, cls) || options(weapon, cls).isEmpty())
			{
				continue;
			}
			anyStyle = true;
			if (ctx.getModifiers().isSpecialAttack() && !hasSpecialAttack(weapon, cls))
			{
				continue;
			}
			boolean fires = firesAmmoSlot(weapon);
			if ((fires || loadsAmmo(weapon)) && compatibleAmmo(weapon, fires).isEmpty())
			{
				return "none of its ammunition can be used" + (fires && settings.getLocks().containsKey(Slot.AMMO)
					? " with the ammo slot lock" : "");
			}
			return null;
		}
		return anyStyle ? "it has no damaging special attack (Only special attacks is on)"
			: "none of its attack styles are allowed by your style, experience or distance settings";
	}

	/**
	 * Why a lock leaves the slot without mandatory protection, or null if it doesn't.
	 *
	 * @param item the locked item, or null for an empty / fill lock
	 */
	public String protectionLockReason(Slot slot, Gear item)
	{
		return SlayerEquipment.lockConflict(monster, settings, slot, item);
	}

	/** Acquisition price, or {@link ItemCosts#UNKNOWN} if a tradable component has no current quote. */
	private long price(Gear item)
	{
		long price = priceFn.applyAsLong(item);
		return ItemCosts.isKnown(price) ? price : ItemCosts.UNKNOWN;
	}

	/**
	 * An unpriced purchase cannot be shown to fit a budget; uncounted ammunition needs no price. Only items
	 * used up per attack (ammo-slot ammunition, blowpipe darts, thrown weapons) are ammunition here: armour and
	 * reusable weapons with ranged bonuses are still bought once and need a price.
	 */
	private boolean withinBudget(Gear item)
	{
		long price = price(item);
		if (!ItemCosts.isKnown(price))
		{
			return settings.getAmmoCount() <= 0 && consumedPerAttack(item);
		}
		return price <= settings.getBudget();
	}

	private boolean unpriced(Gear item)
	{
		if (item == null || ItemCosts.isKnown(price(item)))
		{
			return false;
		}
		if (consumedPerAttack(item))
		{
			// Only the units still to buy need a price; none are needed when the quantity isn't counted.
			return ammoShortfall(item) > 0;
		}
		return !isOwned(item);
	}

	/** Items in the setup that must be bought but have no current price, so its cost is incomplete. */
	public int unpricedItems(Outfit l)
	{
		int count = 0;
		for (Slot s : SLOTS)
		{
			count += unpriced(l.get(s)) ? 1 : 0;
		}
		return count + (unpriced(l.getLoadedAmmo()) ? 1 : 0);
	}

	/**
	 * GP to acquire the item. Ammunition and thrown weapons cost price x the configured ammo count, less the
	 * stack held; blessings and reusable weapons are bought once.
	 */
	private long itemCost(Gear item)
	{
		if (consumedPerAttack(item))
		{
			return ammoCost(item);
		}
		return plainCost(item);
	}

	private long plainCost(Gear item)
	{
		if (item == null || isOwned(item))
		{
			return 0;
		}
		// Unpriced purchases are unavailable in budget mode; elsewhere cost only breaks ties and is reported as incomplete.
		return Math.max(0, price(item));
	}

	/** Price of the ammunition still to buy: the configured quantity minus the stack already held. */
	private long ammoCost(Gear item)
	{
		long shortfall = ammoShortfall(item);
		return shortfall == 0 ? 0 : Math.max(0, price(item)) * shortfall;
	}

	/** Units of the configured ammo quantity not covered by what the player holds; 0 if not counted. */
	private long ammoShortfall(Gear item)
	{
		long count = settings.getAmmoCount();
		if (count <= 0 || !consumedPerAttack(item))
		{
			return 0;
		}
		return Math.max(0, count - heldQuantity(item));
	}

	/** Units held of the item and its catalogued variants. */
	private long heldQuantity(Gear item)
	{
		long held = Math.max(0, ownedQuantity.applyAsLong(item.getId()));
		for (int variant : ownershipVariants(item))
		{
			if (variant != item.getId())
			{
				long more = Math.max(0, ownedQuantity.applyAsLong(variant));
				held = held > Long.MAX_VALUE - more ? Long.MAX_VALUE : held + more;
			}
		}
		return held;
	}

	/** Total GP for everything in the setup the player does not own. */
	public long cost(Outfit l)
	{
		long total = 0;
		for (Slot s : SLOTS)
		{
			total += itemCost(l.get(s));
		}
		total += ammoCost(l.getLoadedAmmo());
		return total;
	}

	private boolean withinBudget(long cost)
	{
		return settings.getMode() != SearchMode.BUDGET || cost <= settings.getBudget();
	}

	/** Count equipped items by acquisition value plus PvP repair fee, independently of ownership and budget. */
	public int expensiveItemCount(Outfit loadout)
	{
		int count = 0;
		for (Slot slot : SLOTS)
		{
			count += expensive(loadout.get(slot)) ? 1 : 0;
		}
		return count;
	}

	private boolean expensive(Gear item)
	{
		if (item == null)
		{
			return false;
		}
		Boolean known = expensiveItems.get(item.getId());
		if (known == null)
		{
			long value = price(item);
			// A broken untradeable costs its repair fee to use again, on top of anything it drops to the killer.
			known = !ItemCosts.isKnown(value)
				|| value + ItemCosts.pvpRepairCost(item) >= Math.max(0, settings.getExpensiveItemThreshold());
			expensiveItems.put(item.getId(), known);
		}
		return known;
	}

	private boolean withinWildernessRisk(Outfit loadout)
	{
		return !settings.isWildernessRiskLimited()
			|| expensiveItemCount(loadout) <= Math.max(0, settings.getMaxExpensiveItems());
	}

	/**
	 * A spec loadout is carried into the Wilderness with the worn setup, so their expensive items count together:
	 * the spec weapon, and an off-hand or ammunition held only for the spec, are risked like the worn gear.
	 */
	private boolean withinWildernessRisk(Outfit worn, Outfit held)
	{
		if (!settings.isWildernessRiskLimited())
		{
			return true;
		}
		Set<Integer> risked = new HashSet<>();
		for (Slot slot : SLOTS)
		{
			for (Gear item : new Gear[]{worn.get(slot), held.get(slot)})
			{
				if (expensive(item))
					risked.add(item.getId());
			}
		}
		return risked.size() <= Math.max(0, settings.getMaxExpensiveItems());
	}

	static boolean classAllowed(Monster m, Combat c)
	{
		return EncounterImmunities.classAllowed(m, c);
	}

	@Value
	private static class Option
	{
		Style style;
		Spell spell;

		/** Leaves the loadout attacking this way. */
		void apply(Outfit l)
		{
			l.setStyle(style);
			l.setSpell(spell);
		}
	}

	/** A spec loadout, one special attack with it, and the GP it adds to the worn setup. */
	@Value
	private static class Held
	{
		Outfit loadout;
		Dps result;
		long cost;
	}

	@Value
	private static class Specced
	{
		SpecialPlan plan;
		/** The worn setup's whole-kill rates with the specs mixed in. */
		Dps result;
		/** The worn setup the specs are mixed into (with lightbearer, when it pays). */
		Outfit worn;
	}

	@Value
	private static class Found
	{
		Outfit loadout;
		double score;
		long cost;
	}

	/**
	 * A loadout's exact contents by identity, for the score memo. Gear's Lombok equals/hashCode compare every
	 * stat, which would cost more than the lookup saves; a different but equal instance only misses the memo.
	 */
	private static final class ScoreKey
	{
		private final Object[] parts;
		private int hash;

		ScoreKey()
		{
			parts = new Object[SLOTS.length + 3];
		}

		private ScoreKey(Object[] parts, int hash)
		{
			this.parts = parts;
			this.hash = hash;
		}

		/** Refill this (probe) key in place, so a memo hit allocates nothing. */
		void fill(Outfit l)
		{
			int n = SLOTS.length;
			for (Slot slot : SLOTS)
			{
				parts[slot.ordinal()] = l.get(slot);
			}
			parts[n] = l.getStyle();
			parts[n + 1] = l.getSpell();
			parts[n + 2] = l.getLoadedAmmo();
			int h = 1;
			for (Object part : parts)
			{
				h = 31 * h + System.identityHashCode(part);
			}
			hash = h;
		}

		/** An independent copy to store, since the probe is refilled. */
		ScoreKey copy()
		{
			return new ScoreKey(parts.clone(), hash);
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof ScoreKey) || ((ScoreKey) o).hash != hash)
			{
				return false;
			}
			Object[] other = ((ScoreKey) o).parts;
			for (int i = 0; i < parts.length; i++)
			{
				if (parts[i] != other[i])
				{
					return false;
				}
			}
			return true;
		}

		@Override
		public int hashCode()
		{
			return hash;
		}
	}
}
