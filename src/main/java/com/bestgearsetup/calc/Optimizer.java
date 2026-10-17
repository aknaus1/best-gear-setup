package com.bestgearsetup.calc;

import com.bestgearsetup.Budget;
import com.bestgearsetup.DiaryRewards;
import com.bestgearsetup.ItemCosts;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.SpellRunes;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import lombok.Value;
import net.runelite.api.gameval.ItemID;

/**
 * Searches for the best setup per combat class under the owned-items / budget rules and the
 * user's constraints (styles, experience, locks, exclusions...).
 *
 * <p>For every candidate weapon it runs a coordinate ascent: starting from a seed loadout it
 * repeatedly tries every candidate in each slot (and the loaded ammo), each scored with its best
 * allowed style / spell, and keeps any change that raises the score, or keeps the score while
 * lowering cost or improving bonuses, until nothing improves. Judging gear and option together
 * matters: a stance or spell can lose with the current gear and win with one changed item.
 * Equal-score stat upgrades can unlock another slot's next max-hit breakpoint.
 * Set bonuses that a greedy per-slot search can never discover (void, obsidian, inquisitor, crystal)
 * are handled by also starting from seeds that already wear the set, first filling the other slots
 * around the held set.
 * Best-depth refinement also tries two slots together (with their best option) for the strongest
 * weapon candidates, since two individually weaker accuracy/strength trades can jointly reach a higher
 * max hit, and then holds each candidate in each slot while the rest re-optimise around it (repairing
 * the budget or expensive-item cap first), which reaches some optima needing three or more changes.
 * Damage-bonus seeds let several strength trades or a different stance reach that breakpoint together.
 * This is a local search: it guarantees no single-slot (and, for refined weapons, no two-slot or
 * held-slot) change improves the result, not a global optimum.
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
	/** Two-slot refinement is quadratic in candidates per slot pair: apply it to the strongest few weapons only. */
	private static final int PAIR_REFINED_WEAPONS = 5;
	/**
	 * How far below the best score an option's bound must be to skip it. Far above {@link #EPS} times the number of
	 * options, so an option skipped this way can't have changed which of the others wins a near tie.
	 */
	private static final double BOUND_MARGIN = 1e-6;
	/** Switches of each size a larger raid switch is grown from, one more slot at a time. */
	private static final int GROWN_SWITCHES = 4;
	/** Weight that makes the calc-mode metric dominate DPS, which only breaks ties. */
	private static final double PRIMARY_WEIGHT = 1e6;

	private static final String[] SPECIAL_PREFIXES = {
		"void ", "elite void", "slayer helmet", "black mask", "salve amulet", "obsidian helmet",
		"obsidian platebody", "obsidian platelegs", "berserker necklace", "inquisitor's", "crystal helm",
		"crystal body", "crystal legs", "eclipse moon",
		"blood moon", "dharok's", "verac's", "ahrim's", "karil's", "amulet of the damned", "crystal blessing",
		"virtus", "tome of", "chaos gauntlets", "brimstone ring", "confliction gauntlets", "amulet of avarice",
		"efaritay's aid", "elemental amulet", "amulet of air", "amulet of water", "amulet of earth", "amulet of fire",
		"saradomin cape", "guthix cape", "zamorak cape", "imbued saradomin", "imbued guthix", "imbued zamorak",
		"serpentine helm", "tanzanite helm", "magma helm", "dizana's", "blessed dizana's",
	};

	private final GameData data;
	private final CombatContext ctx;
	private final OptimizerSettings settings;
	private final IntPredicate owned;
	private final IntToLongFunction ownedQuantity;
	private final ToLongFunction<GearItem> priceFn;
	private final Map<Integer, Boolean> expensiveItems = new HashMap<>();
	// Ownership is fixed for one search but queried in the innermost cost loops; GearItem's Lombok
	// equals/hashCode cover every stat, so these caches key on identity.
	private final Map<GearItem, List<Integer>> ownershipVariants = new IdentityHashMap<>();
	private final Map<GearItem, Boolean> ownedItems = new IdentityHashMap<>();
	private final Map<CombatClass, List<GearItem>> specWeapons = new EnumMap<>(CombatClass.class);
	/**
	 * Scores of the current weapon's loadouts. About half of an ascent's evaluations repeat one: seeds converge on
	 * the same setup, and each pass re-tries swaps the previous pass already scored. Cleared per weapon to stay small.
	 */
	private final Map<ScoreKey, Double> scores = new HashMap<>();
	private final ScoreKey probe = new ScoreKey();
	/**
	 * Special attacks scored by {@link #strongest}, for the whole search: setups found for different weapons often
	 * share their armour, and so hold the same spec loadouts.
	 */
	private final Map<ScoreKey, DpsResult> specResults = new HashMap<>();
	/**
	 * Per slot, an imaginary plain item with the highest of every bonus among the slot's plain candidates (see
	 * {@link #plain}), and which list positions are plain. No plain candidate can score more with an option than this
	 * item does, so options it can't make competitive are skipped for all of them.
	 */
	private final Map<Slot, GearItem> boundItems = new EnumMap<>(Slot.class);
	private final Map<Slot, boolean[]> plainCandidates = new EnumMap<>(Slot.class);
	/** The candidates, class and weapon {@link #boundItems} and {@link #plainCandidates} were built for. */
	private Map<Slot, List<GearItem>> boundCandidates;
	private CombatClass boundClass;
	private GearItem boundWeapon;
	/** The searched class's candidate shields, offered as an off-hand for one-handed spec weapons. */
	private List<GearItem> specShields = Collections.emptyList();
	private final PlayerLevels levels;
	private final Monster monster;
	private final int maxPasses;
	private final int maxWeapons;
	// Whether the target demands protective equipment is fixed for the search; only the setup varies.
	private final boolean fireProtectionRequired;
	private final boolean slayerEquipmentRequired;

	/** As the full constructor, with every owned item held in any quantity. */
	public Optimizer(GameData data, CombatContext ctx, OptimizerSettings settings, IntPredicate owned,
		ToLongFunction<GearItem> priceFn)
	{
		this(data, ctx, settings, owned, id -> owned.test(id) ? Long.MAX_VALUE : 0, priceFn);
	}

	/**
	 * @param owned         whether the player owns an item id (bank, inventory, worn or marked owned)
	 * @param ownedQuantity units of an item id held; ammunition beyond this is bought for the ammo quantity
	 * @param priceFn       acquisition cost of an item, including tradable components of untradeables
	 */
	public Optimizer(GameData data, CombatContext ctx, OptimizerSettings settings, IntPredicate owned,
		IntToLongFunction ownedQuantity, ToLongFunction<GearItem> priceFn)
	{
		this.data = data;
		CombatContext fight = ctx.withFightOptions(ctx.getAoeTargets(), settings.getTargetDistance());
		// A freeze duty only casts on targets that aren't frozen yet.
		this.ctx = settings.getRaidLoadout() == RaidLoadout.ICE_BARRAGE
			? fight.withModifiers(fight.getModifiers().toBuilder().freezeCasts(true).build()) : fight;
		this.settings = settings;
		this.owned = owned;
		this.ownedQuantity = ownedQuantity;
		this.priceFn = priceFn;
		this.levels = ctx.getLevels();
		this.monster = ctx.getMonster();
		this.fireProtectionRequired = DragonfireProtection.required(monster, settings);
		this.slayerEquipmentRequired = SlayerEquipment.applies(monster, settings);
		switch (settings.getDepth())
		{
			case FAST:
				maxPasses = 2;
				maxWeapons = 20;
				break;
			case BEST:
				maxPasses = 8;
				maxWeapons = Integer.MAX_VALUE;
				break;
			default:
				maxPasses = 4;
				maxWeapons = 60;
				break;
		}
	}

	/**
	 * Best setups for one combat class, best first, one per weapon.
	 */
	public List<SetupResult> optimize(CombatClass cls, BooleanSupplier cancelled)
	{
		return optimize(cls, cancelled, done -> { });
	}

	/**
	 * As {@link #optimize(CombatClass, BooleanSupplier)}, reporting the fraction of work done (0 to 1, never
	 * decreasing) on the calling thread. Weapons count as steps, with fractional updates for seed ascents and pair refinement inside each step.
	 */
	public List<SetupResult> optimize(CombatClass cls, BooleanSupplier cancelled, DoubleConsumer progress)
	{
		if (!classAllowed(monster, cls))
		{
			progress.accept(1);
			return Collections.emptyList();
		}
		if (settings.getSwitchFrom() != null)
		{
			return optimizeSwitch(cls, settings.getSwitchFrom(), cancelled, progress);
		}
		Map<Slot, List<GearItem>> candidates = candidates(cls);
		boundCandidates = candidates;
		boundClass = cls;
		boundWeapon = null;
		specShields = candidates.getOrDefault(Slot.SHIELD, Collections.emptyList());
		List<GearItem> weapons = weapons(cls);
		if (weapons.size() > maxWeapons)
		{
			weapons = preselectWeapons(weapons, cls);
		}
		List<Seed> seeds = seeds(cls);
		boolean secondSweep = settings.getDepth() == SearchDepth.BEST || settings.isWildernessRiskLimited();
		// Until the first sweep ends, assume every weapon survives it; the total only shrinks afterwards.
		Steps steps = new Steps(progress, weapons.size() * 2 + (secondSweep ? Math.min(15, weapons.size()) : 0) + 1);

		List<Found> found = new ArrayList<>();
		for (GearItem weapon : weapons)
		{
			if (cancelled.getAsBoolean())
			{
				return Collections.emptyList();
			}
			Found f = optimizeWeapon(weapon, cls, candidates, seeds, steps.part(0, 1));
			if (f != null)
			{
				found.add(f);
			}
			steps.step();
		}
		found.sort(Comparator.comparingDouble((Found f) -> -f.getScore()).thenComparingLong(Found::getCost));
		steps.total(weapons.size() + found.size() + (secondSweep ? Math.min(15, found.size()) : 0) + 1);

		if (secondSweep && !found.isEmpty())
		{
			// Second sweep: start every strong weapon from the best armour found for any weapon. Only the new
			// seeds run: the first sweep's seeds would ascend to the same setup again.
			Loadout bestArmour = found.get(0).getLoadout().copy();
			List<Seed> extra = new ArrayList<>();
			extra.add(new Seed(bestArmour, w -> true));
			if (cls == CombatClass.MELEE)
			{
				extra.add(new Seed(damageSeed(candidates, GearItem::getMeleeStr), w -> true, GearItem::getMeleeStr));
			}
			else if (cls == CombatClass.RANGED)
			{
				extra.add(new Seed(damageSeed(candidates, GearItem::getRangedStr), w -> !WeaponRules.scalesWithStrength(w), GearItem::getRangedStr));
				extra.add(new Seed(damageSeed(candidates, GearItem::getMeleeStr), WeaponRules::scalesWithStrength, GearItem::getMeleeStr));
			}
			else
			{
				extra.add(new Seed(damageSeed(candidates, GearItem::getMagicStr), w -> true, GearItem::getMagicStr));
			}
			List<Found> refined = new ArrayList<>();
			for (Found f : found.subList(0, Math.min(15, found.size())))
			{
				if (cancelled.getAsBoolean())
				{
					return Collections.emptyList();
				}
				Found r = optimizeWeapon(f.getLoadout().getWeapon(), cls, candidates, extra, steps.part(0, 0.25));
				if (r == null || !r.beats(f))
				{
					r = f;
				}
				// Below Best depth, pairs only move the Wilderness expensive-item allowance between slots.
				if (refined.size() < PAIR_REFINED_WEAPONS)
				{
					r = refinePairs(r, cls, candidates, settings.getDepth() != SearchDepth.BEST, cancelled, steps.part(0.25, 0.75));
				}
				refined.add(r.beats(f) ? r : f);
				steps.step();
			}
			refined.addAll(found.subList(Math.min(15, found.size()), found.size()));
			found = refined;
			found.sort(Comparator.comparingDouble((Found f) -> -f.getScore()).thenComparingLong(Found::getCost));
		}

		List<SetupResult> results = new ArrayList<>();
		for (Found f : found)
		{
			if (cancelled.getAsBoolean())
			{
				return Collections.emptyList();
			}
			Loadout l = f.getLoadout();
			Set<Slot> filled = fill(l);
			DpsResult dps = calculate(l);
			if (dps.getDps() > 0 && withinWildernessRisk(l))
			{
				long cost = cost(l);
				Specced specced = special(l, cls, dps, cost, steps.part(0, 1));
				SpecialPlan plan = specced == null ? null : specced.getPlan();
				if (specced != null && specced.getWorn() != l)
				{
					// Lightbearer replaced the ring.
					l = specced.getWorn();
					cost = cost(l);
					Set<Slot> kept = EnumSet.noneOf(Slot.class);
					kept.addAll(filled);
					kept.remove(Slot.RING);
					filled = kept;
				}
				results.add(new SetupResult(cls, l, specced == null ? raidOrdinary(l, dps) : specced.getResult(),
					cost + (plan == null ? 0 : plan.getTotalExtraCost()),
					unpricedItems(l) + (plan == null ? 0 : plan.getTotalUnpricedItems()), filled,
					SupportSpell.assumed(l, ctx), plan));
			}
			steps.step();
		}
		// Filling can lower different setups by different amounts; rank the equipment actually returned.
		results.sort(Comparator.comparingDouble((SetupResult r) -> -metric(r.getDps()))
			.thenComparingLong(SetupResult::getBuyCost));
		List<SetupResult> selected = new ArrayList<>(results.subList(0,
			Math.min(Math.max(0, settings.getResultsPerClass()), results.size())));
		if (settings.getRaidKillShare() > 0)
		{
			List<SetupResult> alternatives = new ArrayList<>();
			for (SetupResult setup : selected)
			{
				long ordinaryCost = cost(setup.getLoadout());
				for (Specced variant : raidSpecVariants.getOrDefault(setup.getLoadout(), Collections.emptyList()))
				{
					SpecialPlan plan = variant.getPlan();
					alternatives.add(new SetupResult(cls, setup.getLoadout(), variant.getResult(),
						ordinaryCost + plan.getTotalExtraCost(), unpricedItems(setup.getLoadout()) + plan.getTotalUnpricedItems(),
						setup.getFilledSlots(), setup.getSupportSpells(), plan));
				}
				if (setup.getSpecial() != null)
				{
					alternatives.add(new SetupResult(cls, setup.getLoadout(), raidOrdinary(setup.getLoadout(), calculate(setup.getLoadout())),
						ordinaryCost, unpricedItems(setup.getLoadout()), setup.getFilledSlots(), setup.getSupportSpells(), null));
				}
			}
			selected.addAll(alternatives);
		}
		steps.step();
		return selected;
	}

	/**
	 * Raid switches into the class from {@code worn}: each weapon, with at most {@code switchLimit - 1} other slots
	 * changed (a shield removed by a two-handed weapon doesn't count). Ammunition the weapon fires is a slot like any
	 * other, so a bow whose ammunition isn't already worn takes a change; darts loaded into a blowpipe aren't worn and
	 * are free. Every single extra change is scored exactly; larger switches grow the best few switches of each size
	 * one slot at a time. A switch is only kept when it beats every smaller switch of its weapon that it contains, so
	 * the limit is a most, never a target. Empty slots stay empty unless changed, and special attacks aren't planned:
	 * either would be another switch. A freeze duty ({@link OptimizerSettings#freezeAccuracy}) stops growing a weapon's
	 * switches once one lands often enough, and ranks them by {@link FreezeDuty#preference}.
	 */
	private List<SetupResult> optimizeSwitch(CombatClass cls, Loadout worn, BooleanSupplier cancelled, DoubleConsumer progress)
	{
		int limit = Math.max(1, settings.getSwitchLimit());
		Map<Slot, List<GearItem>> candidates = candidates(cls);
		List<GearItem> weapons = weapons(cls);
		if (weapons.size() > maxWeapons)
		{
			weapons = preselectWeapons(weapons, cls);
		}
		Steps steps = new Steps(progress, weapons.size() + 1);
		List<Found> found = new ArrayList<>();
		for (GearItem weapon : weapons)
		{
			if (cancelled.getAsBoolean())
			{
				return Collections.emptyList();
			}
			List<Option> options = options(weapon, cls);
			// Insertion order keeps equal switches in a stable order between runs.
			Map<Set<Slot>, Found> best = new LinkedHashMap<>();
			List<Loadout> starts = options.isEmpty() ? Collections.emptyList() : switchStarts(worn, weapon);
			int startIndex = 0;
			for (Loadout start : starts)
			{
				keepSwitchTrial(start, options, worn, limit, best);
				// Keeping the worn weapon (it already attacks with this class) leaves one more change free.
				if (start.switchesFrom(worn) < limit)
				{
					changeOneSlot(start, weapon, candidates, options, worn, limit, best,
					range(steps.part(0, 0.25), (double) startIndex / starts.size(), 1.0 / starts.size()));
				}
				startIndex++;
			}
			steps.part(0, 1).accept(0.25);
			for (int size = 2; size < limit; size++)
			{
				// Fewer items are the freeze's goal: once a smaller switch lands often enough, larger ones can't help.
				if (reachesFreezeTarget(best.values())) { break; }
				int smaller = size - 1;
				List<Found> parents = new ArrayList<>();
				best.forEach((pattern, f) -> { if (pattern.size() == smaller) { parents.add(f); } });
				parents.sort(Comparator.comparingDouble((Found f) -> -f.getScore()).thenComparingLong(Found::getCost));
				int parentIndex = 0;
				int parentCount = Math.min(GROWN_SWITCHES, parents.size());
				for (Found parent : parents.subList(0, parentCount))
				{
					changeOneSlot(parent.getLoadout(), weapon, candidates, options, worn, limit, best,
						range(steps.part(0.25, 0.7), ((size - 2) + (double) parentIndex++ / parentCount) / Math.max(1, limit - 2),
							1.0 / parentCount / Math.max(1, limit - 2)));
				}
			}
			found.addAll(worthSwapping(best));
			steps.step();
		}
		List<SetupResult> scored = new ArrayList<>();
		int scoredIndex = 0;
		for (Found f : found)
		{
			steps.part(0, 0.8).accept((double) scoredIndex++ / Math.max(1, found.size()));
			Loadout l = f.getLoadout();
			DpsResult dps = calculate(l);
			if (dps.getDps() > 0)
			{
				// The same raid final blows as a full setup without specs, so the two compare in one fight.
				scored.add(new SetupResult(cls, l, withFinalBlows(dps), f.getCost(), unpricedItems(l),
					EnumSet.noneOf(Slot.class), SupportSpell.assumed(l, ctx), null, worn));
			}
		}
		// Final blows favour hard hits over plain DPS; rank the rates actually returned, as full setups are.
		double freezeTarget = settings.getFreezeAccuracy();
		scored.sort(freezeTarget > 0 ? FreezeDuty.preference(freezeTarget).thenComparingLong(SetupResult::getBuyCost)
			: Comparator.comparingDouble((SetupResult r) -> -metric(r.getDps()))
			.thenComparingInt((SetupResult r) -> r.getLoadout().switchesFrom(worn)).thenComparingLong(SetupResult::getBuyCost));
		List<SetupResult> results = new ArrayList<>();
		steps.part(0, 1).accept(0.8);
		Map<Set<Slot>, Integer> returned = new HashMap<>();
		for (SetupResult r : scored)
		{
			Set<Slot> pattern = switchPattern(r.getLoadout(), worn);
			if (returned.getOrDefault(pattern, 0) < settings.getResultsPerClass())
			{
				returned.merge(pattern, 1, Integer::sum);
				results.add(r);
			}
		}
		steps.step();
		return results;
	}

	/** Whether a freeze duty's switches include one landing each cast often enough. */
	private boolean reachesFreezeTarget(Collection<Found> switches)
	{
		double target = settings.getFreezeAccuracy();
		return target > 0 && switches.stream().anyMatch(f -> calculate(f.getLoadout()).getAccuracy() >= target - EPS);
	}

	/** Each switch {@code from} with one more slot changed (or one of its changes made differently), kept by pattern. */
	private void changeOneSlot(Loadout from, GearItem weapon, Map<Slot, List<GearItem>> candidates, List<Option> options,
		Loadout worn, int limit, Map<Set<Slot>, Found> best, DoubleConsumer progress)
	{
		int totalItems = candidates.values().stream().mapToInt(list -> list.size() + 1).sum();
		int compared = 0;
		for (Map.Entry<Slot, List<GearItem>> slot : candidates.entrySet())
		{
			if (isLocked(slot.getKey()) || slot.getKey() == Slot.SHIELD && weapon.isTwoHanded())
			{
				continue;
			}
			List<GearItem> items = new ArrayList<>(slot.getValue());
			if (settings.getFreezeAccuracy() <= 0 || worn.get(slot.getKey()) == null) { items.add(null); }
			for (GearItem item : items)
			{
				progress.accept((double) ++compared / Math.max(1, totalItems));
				if (item == from.get(slot.getKey()))
				{
					continue;
				}
				Loadout l = from.copy();
				l.set(slot.getKey(), item);
				keepSwitchTrial(l, options, worn, limit, best);
			}
		}
	}

	/** Keep the best switch for every set of extra slots, so a group's other forms can share the same slots. Switches changing more than the limit allows are dropped. */
	private void keepSwitchTrial(Loadout l, List<Option> options, Loadout worn, int limit, Map<Set<Slot>, Found> best)
	{
		if (settings.getFreezeAccuracy() > 0 && !FreezeDuty.keepsWornGear(l, worn)) { return; }
		Set<Slot> pattern = switchPattern(l, worn);
		if (pattern.size() > limit - 1) { return; }
		Found trial = switchTrial(l, options, best.get(pattern));
		if (trial != null) { best.put(pattern, trial); }
	}

	/** The slots a switch changes besides the weapon; empty for a weapon-only switch. */
	private static Set<Slot> switchPattern(Loadout l, Loadout worn)
	{
		Set<Slot> changed = l.switchSlotsFrom(worn);
		changed.remove(Slot.WEAPON);
		return changed;
	}

	/**
	 * The switches worth their swaps: those scoring above every smaller switch of the same weapon whose slots they
	 * contain. The smaller one is allowed wherever the larger is, so a larger one that doesn't beat it never helps.
	 * Scores are the plain metric, like the full-setup search; final blows only re-rank the reported switches.
	 */
	private static List<Found> worthSwapping(Map<Set<Slot>, Found> best)
	{
		List<Found> out = new ArrayList<>();
		for (Map.Entry<Set<Slot>, Found> larger : best.entrySet())
		{
			boolean helps = true;
			for (Map.Entry<Set<Slot>, Found> smaller : best.entrySet())
			{
				if (smaller.getKey().size() < larger.getKey().size() && larger.getKey().containsAll(smaller.getKey())
					&& larger.getValue().getScore() <= smaller.getValue().getScore() + EPS)
				{
					helps = false;
					break;
				}
			}
			if (helps) { out.add(larger.getValue()); }
		}
		return out;
	}

	/** The worn setup holding {@code weapon}: once with each ammunition it fires or loads, or as worn (ammunition slot included) when it uses none. */
	private List<Loadout> switchStarts(Loadout worn, GearItem weapon)
	{
		Loadout start = worn.copy();
		// A live switch may use a saved base whose gear has since been banked or lost.
		if (settings.getMode().isHeldOnly())
		{
			for (Slot slot : Slot.values())
			{
				GearItem item = start.get(slot);
				if (item != null && !available(item)) { start.set(slot, null); }
			}
		}
		start.set(Slot.WEAPON, weapon);
		start.setLoadedAmmo(null);
		if (weapon.isTwoHanded())
		{
			start.set(Slot.SHIELD, null);
		}
		boolean firesAmmo = WeaponRules.firesAmmoSlot(weapon);
		if (!firesAmmo && !WeaponRules.loadsAmmo(weapon))
		{
			return Collections.singletonList(start);
		}
		List<Loadout> starts = new ArrayList<>();
		for (GearItem ammo : compatibleAmmo(weapon, firesAmmo))
		{
			Loadout l = start.copy();
			if (firesAmmo)
			{
				l.set(Slot.AMMO, ammo);
			}
			else
			{
				l.setLoadedAmmo(ammo);
			}
			starts.add(l);
		}
		return starts;
	}

	/** The better of {@code best} and {@code l} with its best option, when {@code l} deals damage within the budget. */
	private Found switchTrial(Loadout l, List<Option> options, Found best)
	{
		long cost = cost(l);
		if (!withinBudget(cost))
		{
			return best;
		}
		double score = scoreBestOption(l, options, false);
		if (score <= 0)
		{
			return best;
		}
		Found trial = new Found(l.copy(), score, cost);
		return best == null || trial.beats(best) ? trial : best;
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

		DoubleConsumer part(double start, double span)
		{
			int before = done;
			return fraction -> progress.accept(total <= 0 ? 1 : Math.min(1, (before + start + span * fraction) / total));
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

	private static DoubleConsumer range(DoubleConsumer progress, double start, double span)
	{
		return fraction -> progress.accept(start + span * fraction);
	}

	// ------------------------------------------------------------ search

	/** Start from damage bonuses together, rather than rejecting each trade on an accuracy plateau. */
	private Loadout damageSeed(Map<Slot, List<GearItem>> candidates, ToDoubleFunction<GearItem> damage)
	{
		Loadout seed = new Loadout();
		for (Map.Entry<Slot, List<GearItem>> entry : candidates.entrySet())
		{
			GearItem best = null;
			for (GearItem item : entry.getValue())
			{
				double bonus = damage.applyAsDouble(item);
				if (bonus > 0 && (best == null || bonus > damage.applyAsDouble(best)
					|| bonus == damage.applyAsDouble(best) && (itemCost(item) < itemCost(best)
						|| itemCost(item) == itemCost(best) && betterBonuses(item, best))))
				{
					best = item;
				}
			}
			seed.set(entry.getKey(), best);
		}
		return seed;
	}

	/** Keep a damage seed usable under the cap, giving up the least damage bonus per expensive slot. */
	private void fitDamageSeed(Loadout seed, Map<Slot, List<GearItem>> candidates,
		ToDoubleFunction<GearItem> damage)
	{
		while (!withinWildernessRisk(seed))
		{
			Slot replace = null;
			GearItem replacement = null;
			double smallestLoss = Double.POSITIVE_INFINITY;
			for (Map.Entry<Slot, List<GearItem>> entry : candidates.entrySet())
			{
				Slot slot = entry.getKey();
				GearItem original = seed.get(slot);
				if (isLocked(slot) || !expensive(original))
				{
					continue;
				}
				GearItem cheap = null;
				for (GearItem item : entry.getValue())
				{
					if (expensive(item) || damage.applyAsDouble(item) <= 0)
					{
						continue;
					}
					if (cheap == null || damage.applyAsDouble(item) > damage.applyAsDouble(cheap)
						|| damage.applyAsDouble(item) == damage.applyAsDouble(cheap)
							&& (itemCost(item) < itemCost(cheap)
								|| itemCost(item) == itemCost(cheap) && betterBonuses(item, cheap)))
					{
						cheap = item;
					}
				}
				double loss = damage.applyAsDouble(original) - (cheap == null ? 0 : damage.applyAsDouble(cheap));
				if (loss < smallestLoss)
				{
					smallestLoss = loss;
					replace = slot;
					replacement = cheap;
				}
			}
			if (replace == null)
			{
				return;
			}
			seed.set(replace, replacement);
		}
	}

	private Found optimizeWeapon(GearItem weapon, CombatClass cls, Map<Slot, List<GearItem>> candidates,
		List<Seed> seeds, DoubleConsumer progress)
	{
		scores.clear();
		List<Option> options = options(weapon, cls);
		if (options.isEmpty())
		{
			return null;
		}
		boolean firesAmmo = WeaponRules.firesAmmoSlot(weapon);
		boolean loadsAmmo = WeaponRules.loadsAmmo(weapon);
		List<GearItem> ammo = firesAmmo || loadsAmmo ? compatibleAmmo(weapon, firesAmmo) : Collections.emptyList();
		if ((firesAmmo || loadsAmmo) && ammo.isEmpty())
		{
			return null;
		}
		boolean recoveryCape = settings.isRequireAtlatlAmmoRecovery() && WeaponRules.firesRecoverableAmmo(weapon, cls);
		if (recoveryCape)
		{
			// Seed every usable recovery cape, including zero-damage devices that ascent would otherwise drop.
			List<Seed> recoverySeeds = new ArrayList<>();
			for (GearItem cape : ammoRecoveryCapes())
			{
				for (Seed seed : seeds)
				{
					Loadout loadout = seed.getLoadout().copy();
					loadout.set(Slot.CAPE, cape);
					recoverySeeds.add(new Seed(loadout, seed.getFilter(), seed.getDamage(), seed.isSet()));
				}
			}
			seeds = recoverySeeds;
		}

		Loadout best = null;
		double bestScore = -1;
		long bestCost = Long.MAX_VALUE;
		int seedIndex = 0;
		for (Seed seed : seeds)
		{
			int index = seedIndex++;
			progress.accept((double) index / seeds.size());
			DoubleConsumer seedProgress = range(progress, (double) index / seeds.size(), 1.0 / seeds.size());
			if (!seed.appliesTo(weapon))
			{
				continue;
			}
			Loadout l = seed.getLoadout().copy();
			l.set(Slot.WEAPON, weapon);
			if (weapon.isTwoHanded())
			{
				l.set(Slot.SHIELD, null);
			}
			l.setStyle(options.get(0).getStyle());
			l.setSpell(options.get(0).getSpell());
			l.set(Slot.AMMO, null);
			applyLocks(l, weapon, firesAmmo);
			SlotLock capeLock = settings.getLocks().get(Slot.CAPE);
			if (recoveryCape && capeLock != null && capeLock.getKind() == SlotLock.Kind.FILL)
			{
				// A fill lock may choose a different recovery cape later, but must start with usable equipment.
				l.set(Slot.CAPE, seed.getLoadout().get(Slot.CAPE));
			}
			// Start from the strongest ammo that fits the budget (ammo can be priced per 1000 arrows).
			boolean fits = false;
			for (GearItem a : firesAmmo || loadsAmmo ? ammo : Collections.<GearItem>singletonList(null))
			{
				l.set(Slot.AMMO, firesAmmo ? a : l.get(Slot.AMMO));
				l.setLoadedAmmo(loadsAmmo ? a : null);
				if (seed.getDamage() != null && settings.isWildernessRiskLimited())
				{
					fitDamageSeed(l, candidates, seed.getDamage());
				}
				if (withinBudget(cost(l)) && withinWildernessRisk(l))
				{
					fits = true;
					break;
				}
			}
			if (!fits)
			{
				continue;
			}

			if (seed.isSet())
			{
				// Greedy single-slot moves can break a set before the other slots are filled, after which
				// no single move restores it: fill the other slots around the set first.
				Set<Slot> pieces = EnumSet.noneOf(Slot.class);
				for (Slot slot : SLOTS)
				{
					GearItem piece = seed.getLoadout().get(slot);
					if (piece != null && slot != Slot.WEAPON && slot != Slot.AMMO && l.get(slot) == piece)
					{
						pieces.add(slot);
					}
				}
				ascend(l, weapon, options, candidates, ammo, firesAmmo, loadsAmmo, pieces, range(seedProgress, 0, 0.5));
			}
			ascend(l, weapon, options, candidates, ammo, firesAmmo, loadsAmmo, Collections.emptySet(),
				range(seedProgress, seed.isSet() ? 0.5 : 0, seed.isSet() ? 0.5 : 1));
			seedProgress.accept(1);

			double s = score(l);
			long c = cost(l);
			if (s > bestScore + EPS || (Math.abs(s - bestScore) <= EPS && c < bestCost))
			{
				best = l;
				bestScore = s;
				bestCost = c;
			}
		}
		if (best == null || calculate(best).getDps() <= 0)
		{
			return null;
		}
		return new Found(best, bestScore, bestCost);
	}

	/**
	 * Escape a single-slot local optimum without requiring either half of a pair to improve alone.
	 *
	 * @param allowanceOnly only try pairs that move the expensive-item allowance between the two slots
	 */
	private Found refinePairs(Found found, CombatClass cls, Map<Slot, List<GearItem>> candidates,
		boolean allowanceOnly, BooleanSupplier cancelled, DoubleConsumer progress)
	{
		if (allowanceOnly && !settings.isWildernessRiskLimited())
		{
			return found;
		}
		Loadout l = found.getLoadout().copy();
		GearItem weapon = l.getWeapon();
		boolean firesAmmo = WeaponRules.firesAmmoSlot(weapon);
		boolean loadsAmmo = WeaponRules.loadsAmmo(weapon);
		List<GearItem> ammo = firesAmmo || loadsAmmo ? compatibleAmmo(weapon, firesAmmo) : Collections.emptyList();
		Map<Slot, List<GearItem>> pairs = new EnumMap<>(Slot.class);
		for (Slot slot : SLOTS)
		{
			if (slot == Slot.WEAPON || isLocked(slot) || slot == Slot.SHIELD && weapon.isTwoHanded()
				|| slot == Slot.AMMO && !firesAmmo)
			{
				continue;
			}
			List<GearItem> list = new ArrayList<>(slot == Slot.AMMO ? ammo : candidates.get(slot));
			if (slot != Slot.AMMO)
			{
				list.add(null);
			}
			pairs.put(slot, list);
		}
		List<Option> options = options(weapon, cls);
		for (int pass = 0; pass < maxPasses && !cancelled.getAsBoolean(); pass++)
		{
			DoubleConsumer passProgress = range(progress, (double) pass / maxPasses, 1.0 / maxPasses);
			if (improvePair(l, pairs, options, allowanceOnly, cancelled, range(passProgress, 0, 0.5)))
			{
				ascend(l, weapon, options, candidates, ammo, firesAmmo, loadsAmmo, Collections.emptySet(), range(passProgress, 0.5, 0.5));
				passProgress.accept(1);
				continue;
			}
			Loadout kicked = allowanceOnly ? null
				: kick(l, weapon, options, candidates, pairs, ammo, firesAmmo, loadsAmmo, cancelled, range(passProgress, 0.5, 0.5));
			if (kicked == null)
			{
				break;
			}
			l = kicked;
			passProgress.accept(1);
		}
		return new Found(l, score(l), cost(l));
	}

	/**
	 * Iterated local search: hold each candidate in each slot, even past the expensive-item cap, re-optimise the
	 * other slots around it, then release it. This reaches optima that need three or more simultaneous changes, such
	 * as completing a set the ascent broke early or moving expensive allowances between several slots.
	 *
	 * @return the best strictly better loadout found (higher score, or the same score for less GP), or null
	 */
	private Loadout kick(Loadout l, GearItem weapon, List<Option> options, Map<Slot, List<GearItem>> candidates,
		Map<Slot, List<GearItem>> moves, List<GearItem> ammo, boolean firesAmmo, boolean loadsAmmo,
		BooleanSupplier cancelled, DoubleConsumer progress)
	{
		double bestScore = score(l);
		long bestCost = cost(l);
		Loadout best = null;
		int totalMoves = moves.values().stream().mapToInt(List::size).sum();
		int move = 0;
		for (Map.Entry<Slot, List<GearItem>> e : moves.entrySet())
		{
			Slot slot = e.getKey();
			for (GearItem cand : e.getValue())
			{
				progress.accept((double) move / Math.max(1, totalMoves));
				DoubleConsumer moveProgress = range(progress, (double) move++ / Math.max(1, totalMoves), 1.0 / Math.max(1, totalMoves));
				if (cancelled.getAsBoolean())
				{
					return null;
				}
				if (cand == l.get(slot))
				{
					continue;
				}
				Loadout trial = l.copy();
				trial.set(slot, cand);
				Set<Slot> hold = EnumSet.of(slot);
				if (!repair(trial, moves, hold))
				{
					continue;
				}
				ascend(trial, weapon, options, candidates, ammo, firesAmmo, loadsAmmo, hold, range(moveProgress, 0, 0.5));
				ascend(trial, weapon, options, candidates, ammo, firesAmmo, loadsAmmo, Collections.emptySet(), range(moveProgress, 0.5, 0.5));
				double s = score(trial);
				long c = cost(trial);
				if (s > bestScore + EPS || Math.abs(s - bestScore) <= EPS && c < bestCost)
				{
					best = trial;
					bestScore = s;
					bestCost = c;
				}
			}
		}
		return best;
	}

	/**
	 * Bring a loadout back within the budget and expensive-item cap, one move at a time: each move must reduce one
	 * excess without raising the other, and the one keeping the highest score is taken. The ascent alone can't do
	 * this, since it only accepts moves that raise the score, and in slot order rather than where it costs least.
	 *
	 * @return false when no move reduces the excess
	 */
	private boolean repair(Loadout l, Map<Slot, List<GearItem>> moves, Set<Slot> held)
	{
		long overCost = overBudget(l);
		int overCap = overCap(l);
		while (overCost > 0 || overCap > 0)
		{
			Slot bestSlot = null;
			GearItem bestItem = null;
			double bestScore = Double.NEGATIVE_INFINITY;
			long bestOverCost = overCost;
			int bestOverCap = overCap;
			for (Map.Entry<Slot, List<GearItem>> e : moves.entrySet())
			{
				Slot slot = e.getKey();
				if (held.contains(slot))
				{
					continue;
				}
				GearItem orig = l.get(slot);
				for (GearItem cand : e.getValue())
				{
					if (cand == orig)
					{
						continue;
					}
					l.set(slot, cand);
					long c = overBudget(l);
					int k = overCap(l);
					if (c <= overCost && k <= overCap && (c < overCost || k < overCap))
					{
						double s = protectedSetup(l) ? metric(DpsCalculator.calculate(l, ctx)) : 0;
						if (s > bestScore + EPS || Math.abs(s - bestScore) <= EPS && c + k < bestOverCost + bestOverCap)
						{
							bestSlot = slot;
							bestItem = cand;
							bestScore = s;
							bestOverCost = c;
							bestOverCap = k;
						}
					}
				}
				l.set(slot, orig);
			}
			if (bestSlot == null)
			{
				return false;
			}
			l.set(bestSlot, bestItem);
			overCost = bestOverCost;
			overCap = bestOverCap;
		}
		return true;
	}

	private long overBudget(Loadout l)
	{
		return settings.getMode() == SearchMode.BUDGET ? Math.max(0, cost(l) - settings.getBudget()) : 0;
	}

	private int overCap(Loadout l)
	{
		return settings.isWildernessRiskLimited()
			? Math.max(0, expensiveItemCount(l) - Math.max(0, settings.getMaxExpensiveItems())) : 0;
	}

	/** Change two slots together, each pair with its best allowed stance / spell. */
	private boolean improvePair(Loadout l, Map<Slot, List<GearItem>> candidates, List<Option> options,
		boolean allowanceOnly, BooleanSupplier cancelled, DoubleConsumer progress)
	{
		double bestScore = score(l);
		long originalCost = cost(l);
		long bestCost = originalCost;
		AttackStyle originalStyle = l.getStyle();
		Spell originalSpell = l.getSpell();
		Slot bestLeft = null;
		Slot bestRight = null;
		GearItem bestFirst = null;
		GearItem bestSecond = null;
		AttackStyle bestStyle = originalStyle;
		Spell bestSpell = originalSpell;
		List<Slot> slots = new ArrayList<>(candidates.keySet());
		Map<Slot, Set<GearItem>> plain = pairPlainItems(l.getWeapon(), slots);
		int pairs = slots.size() * (slots.size() - 1) / 2;
		int pair = 0;
		for (int a = 0; a < slots.size(); a++)
		{
			Slot left = slots.get(a);
			GearItem originalLeft = l.get(left);
			for (int b = a + 1; b < slots.size(); b++)
			{
				DoubleConsumer pairProgress = range(progress, (double) pair++ / Math.max(1, pairs), 1.0 / Math.max(1, pairs));
				pairProgress.accept(0);
				Slot right = slots.get(b);
				GearItem originalRight = l.get(right);
				long base = originalCost - itemCost(originalLeft) - itemCost(originalRight);
				Set<GearItem> plainLeft = plain.get(left);
				Set<GearItem> plainRight = plain.get(right);
				List<GearItem> seconds = candidates.get(right);
				// Option bounds with plain items replaced by the slot's imaginary item: both slots, or one of them for
				// each item in the other (by the right-hand item's position).
				double[] bothBounds = null;
				double[][] rightHeld = null;
				int row = 0;
				for (GearItem first : candidates.get(left))
				{
					pairProgress.accept((double) row++ / Math.max(1, candidates.get(left).size()));
					if (cancelled.getAsBoolean())
					{
						l.set(left, originalLeft);
						l.set(right, originalRight);
						l.setStyle(originalStyle);
						l.setSpell(originalSpell);
						return false;
					}
					if (first == originalLeft || allowanceOnly && expensive(first) == expensive(originalLeft))
					{
						continue;
					}
					l.set(left, first);
					boolean firstPlain = plainLeft != null && plainLeft.contains(first);
					double[] leftHeld = null;
					for (int k = 0; k < seconds.size(); k++)
					{
						GearItem second = seconds.get(k);
						long c = base + itemCost(first) + itemCost(second);
						if (second == originalRight || !withinBudget(c)
							|| allowanceOnly && expensive(second) == expensive(originalRight))
						{
							continue;
						}
						l.set(right, second);
						if (!withinWildernessRisk(l))
						{
							continue;
						}
						boolean secondPlain = plainRight != null && plainRight.contains(second);
						List<Option> tried = options;
						if (firstPlain || secondPlain)
						{
							double[] bounds;
							if (firstPlain && secondPlain)
							{
								if (bothBounds == null)
								{
									bothBounds = pairBounds(l, left, boundItems.get(left), right, boundItems.get(right), options);
								}
								bounds = bothBounds;
							}
							else if (firstPlain)
							{
								if (rightHeld == null)
								{
									rightHeld = new double[seconds.size()][];
								}
								if (rightHeld[k] == null)
								{
									rightHeld[k] = pairBounds(l, left, boundItems.get(left), right, second, options);
								}
								bounds = rightHeld[k];
							}
							else
							{
								if (leftHeld == null)
								{
									leftHeld = pairBounds(l, left, first, right, boundItems.get(right), options);
								}
								bounds = leftHeld;
							}
							l.set(left, first);
							l.set(right, second);
							tried = optionsWithin(options, bounds, bestScore - EPS - BOUND_MARGIN);
							if (tried.isEmpty())
							{
								continue;
							}
						}
						// Pairs are almost never repeated, so they skip the memo.
						l.setStyle(originalStyle);
						l.setSpell(originalSpell);
						double s = scoreBestOption(l, tried, false);
						if (s > bestScore + EPS || Math.abs(s - bestScore) <= EPS && c < bestCost)
						{
							bestScore = s;
							bestCost = c;
							bestLeft = left;
							bestRight = right;
							bestFirst = first;
							bestSecond = second;
							bestStyle = l.getStyle();
							bestSpell = l.getSpell();
						}
					}
				}
				l.set(left, originalLeft);
				l.set(right, originalRight);
			}
		}
		l.setStyle(originalStyle);
		l.setSpell(originalSpell);
		if (bestLeft == null)
		{
			return false;
		}
		l.set(bestLeft, bestFirst);
		l.set(bestRight, bestSecond);
		l.setStyle(bestStyle);
		l.setSpell(bestSpell);
		return true;
	}

	/** Put locked items / empties in place. Locked slots are then skipped by the ascent. */
	private void applyLocks(Loadout l, GearItem weapon, boolean firesAmmo)
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
					GearItem locked = data.getItem(slot, lock.getItemId());
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

	/**
	 * Score the loadout's equipment with its best allowed stance / spell, and leave that option set: the loadout then
	 * holds the complete candidate. Gear and option are judged together, since a stance or spell that loses with the
	 * current gear can win with one changed item (and vice versa). The option already set is kept on ties, then the
	 * earliest in {@code options}. Cost, budget and the expensive-item cap don't depend on the option, while
	 * protection and every combat modifier are applied by {@link #calculate}.
	 *
	 * @param memo false for one-off trials (pairs) that would only grow the memo
	 */
	private double scoreBestOption(Loadout l, List<Option> options, boolean memo)
	{
		AttackStyle current = l.getStyle();
		Spell currentSpell = l.getSpell();
		AttackStyle bestStyle = current;
		Spell bestSpell = currentSpell;
		double best = Double.NEGATIVE_INFINITY;
		for (Option o : options)
		{
			l.setStyle(o.getStyle());
			l.setSpell(o.getSpell());
			double s = memo ? score(l) : metric(calculate(l));
			boolean kept = o.getSpell() == currentSpell && o.getStyle().equals(current);
			if (s > best + EPS || kept && s >= best - EPS)
			{
				best = s;
				bestStyle = o.getStyle();
				bestSpell = o.getSpell();
			}
		}
		l.setStyle(bestStyle);
		l.setSpell(bestSpell);
		return best;
	}

	/** {@link #scoreBestOption} over the weapon's allowed options; package-private to test trial evaluation alone. */
	double scoreWithBestOption(Loadout l, CombatClass cls)
	{
		return scoreBestOption(l, options(l.getWeapon(), cls), false);
	}

	private void ascend(Loadout l, GearItem weapon, List<Option> options, Map<Slot, List<GearItem>> candidates,
		List<GearItem> ammo, boolean firesAmmo, boolean loadsAmmo)
	{
		ascend(l, weapon, options, candidates, ammo, firesAmmo, loadsAmmo, Collections.emptySet());
	}

	/**
	 * Climb until no single change of one slot (or the loaded ammunition) together with any allowed stance / spell
	 * raises the score. Moves at the current option are far cheaper to score, so those are exhausted first.
	 *
	 * @param held slots to keep as they are
	 */
	private void ascend(Loadout l, GearItem weapon, List<Option> options, Map<Slot, List<GearItem>> candidates,
		List<GearItem> ammo, boolean firesAmmo, boolean loadsAmmo, Set<Slot> held)
	{
		ascend(l, weapon, options, candidates, ammo, firesAmmo, loadsAmmo, held, fraction -> { });
	}

	private void ascend(Loadout l, GearItem weapon, List<Option> options, Map<Slot, List<GearItem>> candidates,
		List<GearItem> ammo, boolean firesAmmo, boolean loadsAmmo, Set<Slot> held, DoubleConsumer progress)
	{
		climb(l, weapon, options, candidates, ammo, firesAmmo, loadsAmmo, held, false, range(progress, 0, 0.5));
		progress.accept(0.5);
		climb(l, weapon, options, candidates, ammo, firesAmmo, loadsAmmo, held, options.size() > 1, range(progress, 0.5, 0.5));
		progress.accept(1);
	}

	/** @param joint score each trial with its best option rather than the current one */
	private void climb(Loadout l, GearItem weapon, List<Option> options, Map<Slot, List<GearItem>> candidates,
		List<GearItem> ammo, boolean firesAmmo, boolean loadsAmmo, Set<Slot> held, boolean joint, DoubleConsumer progress)
	{
		double cur = score(l);
		long curCost = cost(l);
		for (int pass = 0; pass < maxPasses; pass++)
		{
			boolean improved = false;

			// Attack style / spell with the current equipment
			AttackStyle origStyle = l.getStyle();
			Spell origSpell = l.getSpell();
			double d0 = scoreBestOption(l, options, true);
			if (d0 > cur + EPS)
			{
				cur = d0;
				improved = true;
			}
			else
			{
				l.setStyle(origStyle);
				l.setSpell(origSpell);
			}

			// Darts in a blowpipe, each with its best option
			if (loadsAmmo)
			{
				GearItem orig = l.getLoadedAmmo();
				AttackStyle keptStyle = l.getStyle();
				Spell keptSpell = l.getSpell();
				GearItem bestAmmo = orig;
				AttackStyle bestStyle = keptStyle;
				Spell bestSpell = keptSpell;
				double bestD = cur;
				long bestC = curCost;
				long base = curCost - ammoCost(orig);
				for (GearItem a : ammo)
				{
					long c = base + ammoCost(a);
					if (a == orig || !withinBudget(c))
					{
						continue;
					}
					l.setLoadedAmmo(a);
					l.setStyle(keptStyle);
					l.setSpell(keptSpell);
					double d = joint ? scoreBestOption(l, options, true) : score(l);
					if (d > bestD + EPS || (Math.abs(d - bestD) <= EPS
						&& (c < bestC || c == bestC && betterBonuses(a, bestAmmo))))
					{
						bestAmmo = a;
						bestStyle = l.getStyle();
						bestSpell = l.getSpell();
						bestD = d;
						bestC = c;
					}
				}
				l.setLoadedAmmo(bestAmmo);
				l.setStyle(bestStyle);
				l.setSpell(bestSpell);
				if (bestAmmo != orig)
				{
					improved = true;
					cur = bestD;
					curCost = bestC;
				}
			}

			int slotIndex = 0;
			for (Slot slot : SLOTS)
			{
				progress.accept((pass + (double) slotIndex++ / SLOTS.length) / maxPasses);
				if (slot == Slot.WEAPON || held.contains(slot) || (slot == Slot.SHIELD && weapon.isTwoHanded()))
				{
					continue;
				}
				List<GearItem> list;
				if (slot == Slot.AMMO)
				{
					if (!firesAmmo)
					{
						continue;
					}
					list = ammo;
				}
				else
				{
					if (isLocked(slot))
					{
						continue;
					}
					list = candidates.get(slot);
				}

				GearItem orig = l.get(slot);
				AttackStyle keptStyle = l.getStyle();
				Spell keptSpell = l.getSpell();
				GearItem bestItem = orig;
				AttackStyle bestStyle = keptStyle;
				Spell bestSpell = keptSpell;
				double bestD = cur;
				long bestC = curCost;
				long base = curCost - itemCost(orig);
				// Bound the options for plain items when scoring every option, and there are several items to skip.
				boolean[] plain = !joint || slot == Slot.AMMO || candidates != boundCandidates ? null
					: plainCandidates(weapon, slot);
				if (plain != null && plainOthers(plain, list, orig) < 2)
				{
					plain = null;
				}
				double[] bounds = null;
				List<Option> open = options;
				double openFor = Double.NaN;
				for (int i = -1; i < list.size(); i++)
				{
					GearItem cand = i < 0 ? null : list.get(i);
					if (cand == orig || (cand == null && slot == Slot.AMMO))
					{
						continue;
					}
					long c = base + itemCost(cand);
					if (!withinBudget(c))
					{
						continue;
					}
					l.set(slot, cand);
					if (!withinWildernessRisk(l))
					{
						continue;
					}
					List<Option> tried = options;
					if (plain != null && i >= 0 && plain[i])
					{
						if (bounds == null)
						{
							bounds = optionBounds(l, slot, options);
							l.set(slot, cand);
						}
						if (openFor != bestD)
						{
							open = optionsWithin(options, bounds, bestD - EPS - BOUND_MARGIN);
							openFor = bestD;
						}
						if (open.isEmpty())
						{
							// No option lets any plain item in this slot reach the best so far.
							continue;
						}
						tried = open;
					}
					l.setStyle(keptStyle);
					l.setSpell(keptSpell);
					double d = joint ? scoreBestOption(l, tried, true) : score(l);
					if (d > bestD + EPS || (Math.abs(d - bestD) <= EPS
						&& (c < bestC || c == bestC && (betterBonuses(cand, bestItem)
							|| bestItem == null && d > 0 && usefulBonuses(cand, l)))))
					{
						bestItem = cand;
						bestStyle = l.getStyle();
						bestSpell = l.getSpell();
						bestD = d;
						bestC = c;
					}
				}
				l.set(slot, bestItem);
				l.setStyle(bestStyle);
				l.setSpell(bestSpell);
				if (bestItem != orig)
				{
					improved = true;
					cur = bestD;
					curCost = bestC;
				}
			}

			if (!improved)
			{
				break;
			}
		}
	}

	/**
	 * The most each option can score with any plain item in the slot, the rest of the loadout unchanged: its score
	 * wearing {@link #boundItems}' item there. Leaves the slot holding that item and the option changed.
	 */
	private double[] optionBounds(Loadout l, Slot slot, List<Option> options)
	{
		l.set(slot, boundItems.get(slot));
		return optionBounds(l, options);
	}

	/** As {@link #optionBounds(Loadout, Slot, List)} for a pair of slots, each holding the item given. */
	private double[] pairBounds(Loadout l, Slot left, GearItem first, Slot right, GearItem second, List<Option> options)
	{
		l.set(left, first);
		l.set(right, second);
		return optionBounds(l, options);
	}

	/**
	 * Per slot, the pair candidates {@link #boundItems} bounds for this weapon: its plain candidates and the empty
	 * slot. Slots without bounds (ammunition) are left out.
	 */
	private Map<Slot, Set<GearItem>> pairPlainItems(GearItem weapon, List<Slot> slots)
	{
		Map<Slot, Set<GearItem>> out = new EnumMap<>(Slot.class);
		for (Slot slot : slots)
		{
			List<GearItem> list = boundCandidates == null ? null : boundCandidates.get(slot);
			if (slot == Slot.AMMO || list == null)
			{
				continue;
			}
			boolean[] plain = plainCandidates(weapon, slot);
			Set<GearItem> items = Collections.newSetFromMap(new IdentityHashMap<>());
			items.add(null);
			for (int i = 0; i < plain.length; i++)
			{
				if (plain[i])
				{
					items.add(list.get(i));
				}
			}
			out.put(slot, items);
		}
		return out;
	}

	/** Each option's score for the loadout as it is, without the expensive-item cap; leaves the last option set. */
	private double[] optionBounds(Loadout l, List<Option> options)
	{
		double[] bounds = new double[options.size()];
		for (int k = 0; k < bounds.length; k++)
		{
			l.setStyle(options.get(k).getStyle());
			l.setSpell(options.get(k).getSpell());
			// Shares the weapon's memo, since no real setup holds the imaginary item. Leaving out the expensive-item cap
			// (which would price that item) only raises the bound.
			probe.fill(l);
			Double known = scores.get(probe);
			if (known == null)
			{
				known = protectedSetup(l) ? metric(DpsCalculator.calculate(l, ctx)) : 0;
				scores.put(probe.copy(), known);
			}
			bounds[k] = known;
		}
		return bounds;
	}

	private static int plainOthers(boolean[] plain, List<GearItem> list, GearItem orig)
	{
		int count = 0;
		for (int i = 0; i < plain.length; i++)
		{
			count += plain[i] && list.get(i) != orig ? 1 : 0;
		}
		return count;
	}

	/** The options, in order, whose bound reaches the threshold; all of them when there are too many to rule out near ties. */
	private static List<Option> optionsWithin(List<Option> options, double[] bounds, double threshold)
	{
		if (options.size() * EPS * 4 >= BOUND_MARGIN)
		{
			return options;
		}
		List<Option> out = new ArrayList<>(options.size());
		for (int k = 0; k < bounds.length; k++)
		{
			if (bounds[k] >= threshold)
			{
				out.add(options.get(k));
			}
		}
		return out.size() == options.size() ? options : out;
	}

	/** The value being maximised: the calc-mode metric, with DPS as tie-breaker. */
	private double score(Loadout l)
	{
		probe.fill(l);
		Double known = scores.get(probe);
		if (known != null)
		{
			return known;
		}
		double s = metric(calculate(l));
		scores.put(probe.copy(), s);
		return s;
	}

	private DpsResult calculate(Loadout l)
	{
		return protectedSetup(l) && withinWildernessRisk(l) ? DpsCalculator.calculate(l, ctx) : DpsResult.ZERO;
	}

	/** Mandatory protection, primary raid duties and, when required, an ammunition recovery cape are enforced throughout the search. */
	private boolean protectedSetup(Loadout l)
	{
		return settings.getRaidLoadout().allows(l) && protectedEquipment(l)
			&& (!settings.isRequireAtlatlAmmoRecovery() || l.getStyle() == null
				|| !WeaponRules.firesRecoverableAmmo(l.getWeapon(), WeaponRules.classOf(l.getStyle()))
				|| WeaponRules.isAmmoRecoveryCape(l.get(Slot.CAPE)));
	}

	/** Special switches must meet equipment protection rules; their attack needn't fulfil the primary raid duty or wear an ammunition recovery cape. */
	private boolean protectedEquipment(Loadout l)
	{
		return SpellRunes.suppliesAllowed(l, Collections.emptySet(), settings.getExcludedSupplies())
			&& (!fireProtectionRequired || DragonfireProtection.protects(monster, l, settings, levels))
			&& (!slayerEquipmentRequired || SlayerEquipment.allowed(monster, settings, l));
	}

	private double metric(DpsResult r)
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

	private double primary(DpsResult r)
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
	private Specced special(Loadout worn, CombatClass cls, DpsResult ordinary, long wornCost, DoubleConsumer progress)
	{
		if (!settings.isKillSpecials() || ctx.getModifiers().isSpecialAttack()
			|| settings.getRaidKillShare() > 0 && !CombatRules.raidSpecialsAllowed(ctx.getMonster()))
		{
			return null;
		}
		double regen = SpecialRotation.REGEN_PER_SECOND * (lightbearer(worn) ? 2 : 1);
		Specced best = special(worn, cls, ordinary, wornCost, regen, range(progress, 0, 0.5));
		progress.accept(0.5);
		Loadout lit = withLightbearer(worn);
		if (lit != null)
		{
			DpsResult litOrdinary = calculate(lit);
			long litCost = cost(lit);
			if (litOrdinary.getDps() > 0 && withinBudget(litCost))
			{
				Specced s = special(lit, cls, litOrdinary, litCost, 2 * SpecialRotation.REGEN_PER_SECOND, range(progress, 0.5, 0.5));
				if (s != null && metric(s.getResult()) > metric(best == null ? raidOrdinary(worn, ordinary) : best.getResult()) + EPS)
				{
					best = s;
				}
			}
		}
		return best;
	}

	private static boolean lightbearer(Loadout l)
	{
		GearItem ring = l.get(Slot.RING);
		return ring != null && ring.getId() == ItemID.LIGHTBEARER;
	}

	/**
	 * The worn setup with lightbearer in place of its ring, or null when it can't or already does. The Theatre of
	 * Blood never swaps it in: a ring switch there isn't worth the extra regeneration (lock it to wear it anyway).
	 */
	private Loadout withLightbearer(Loadout worn)
	{
		GearItem ring = data.getItem(Slot.RING, ItemID.LIGHTBEARER);
		if (ring == null || lightbearer(worn) || settings.getLocks().containsKey(Slot.RING) || !usable(ring)
			|| theatre(ctx.getMonster()))
		{
			return null;
		}
		Loadout lit = worn.copy();
		lit.set(Slot.RING, ring);
		return protectedSetup(lit) && withinWildernessRisk(lit) ? lit : null;
	}

	private static boolean theatre(Monster m)
	{
		RaidScaling.Raid raid = RaidScaling.raid(m);
		return raid == RaidScaling.Raid.TOB || raid == RaidScaling.Raid.TOB_ENTRY;
	}

	/**
	 * Candidates are the worn weapon and every usable weapon of the same combat class with a damaging spec,
	 * held with the worn armour, plus an off-hand when a one-handed spec weapon leaves the shield slot empty.
	 * They are bought within what the budget has left.
	 */
	private Specced special(Loadout worn, CombatClass cls, DpsResult ordinary, long wornCost, double regen, DoubleConsumer progress)
	{
		if (settings.getRaidKillShare() > 0)
		{
			return raidSpecial(worn, cls, ordinary, wornCost, regen, progress);
		}
		CombatContext specCtx = ctx.withModifiers(ctx.getModifiers().toBuilder().specialAttack(true).build());
		int hp = ctx.getTargetHitpoints();
		// Ordinary attacks after each set of landed drains, shared by every draining candidate.
		Map<SpecialAttacks, DpsResult> drainedOrdinary = new HashMap<>();
		drainedOrdinary.put(SpecialAttacks.NONE, ordinary);
		// A drain only pays while it lasts: not at all on targets that restore their Defence straight away.
		boolean drainsLast = settings.getDrainSpecs() != DrainSpecs.DAMAGE_ONLY && CombatRules.drainPersistence(ctx.getMonster()) > 0;
		Specced[] best = {null};
		double[] bestScore = {metric(ordinary)};
		forEachSpec(worn, cls, wornCost, specCtx, progress, (weapon, o, spec, choice) ->
		{
			Loadout held = choice.getLoadout();
			DpsResult opening = choice.getResult();
			long extra = choice.getCost();
			boolean draining = drainsLast && spec.getDrain() != SpecialAttack.Drain.NONE;
			SpecialRotation.Fight fight = draining ? drainingFight(worn, held, opening, specCtx, drainedOrdinary) : null;
			SpecialRotation.Blend blend = rotation(fight, spec, ordinary, opening, hp, regen, settings.getSpecEnergy());
			double score = blend == null ? Double.NEGATIVE_INFINITY : metric(blend.getResult());
			if (blend == null || !(score > bestScore[0] + EPS || best[0] != null && Math.abs(score - bestScore[0]) <= EPS
				&& extra < best[0].getPlan().getExtraCost()))
			{
				return;
			}
			DpsResult drained = null;
			if (draining)
			{
				// Shown as the ordinary attacks after one landed spec of average damage.
				int damage = (int) Math.round(opening.getAccuracy() > 0
					? opening.getPrimaryDps() * opening.getExpectedSpeedTicks() * 0.6 / opening.getAccuracy() : 0);
				drained = fight.ordinary(SpecialAttacks.NONE.landed(spec.getDrain(), damage));
			}
			SpecItems items = specItems(worn, weapon, held);
			// A raid may not have a full bar for this fight: the same spec on regenerated energy alone.
			boolean fullBar = settings.getSpecEnergy() == SpecEnergy.FULL_BAR;
			SpecialRotation.Blend regenerated = fullBar
				? rotation(fight, spec, ordinary, opening, hp, regen, SpecEnergy.REGENERATION) : null;
			best[0] = new Specced(new SpecialPlan(weapon, items.getOffhand(), items.getAmmo(), o.getStyle(), o.getSpell(),
				held.getLoadedAmmo(), spec, opening, ordinary, drained, blend.getSpecsPerKill(), extra, items.getUnpriced(),
				!fullBar ? null : regenerated == null ? ordinary : regenerated.getResult(),
				regenerated == null ? 0 : regenerated.getSpecsPerKill()), blend.getResult(), worn);
			bestScore[0] = score;
		});
		return best[0];
	}

	/** A usable spec weapon and style, held with its strongest affordable equipment. */
	private interface SpecCandidate
	{
		void accept(GearItem weapon, Option option, SpecialAttack spec, Held held);
	}

	/**
	 * Every usable weapon of the combat class with a spec, in each style, held with the worn armour and bought within
	 * what the budget has left. The held loadout is reused between styles: a candidate that is kept must copy it.
	 */
	private void forEachSpec(Loadout worn, CombatClass cls, long wornCost, CombatContext specCtx, DoubleConsumer progress,
		SpecCandidate candidate)
	{
		List<GearItem> specialWeapons = specWeapons(cls);
		int weaponIndex = 0;
		for (GearItem weapon : specialWeapons)
		{
			progress.accept(0.7 * weaponIndex++ / Math.max(1, specialWeapons.size()));
			Loadout base = specLoadout(worn, weapon);
			if (base == null)
			{
				continue;
			}
			long weaponCost = weapon == worn.getWeapon() ? 0 : itemCost(weapon) + ammoCost(base.getLoadedAmmo());
			for (Option o : options(weapon, specClass(weapon, cls), true))
			{
				base.setStyle(o.getStyle());
				base.setSpell(o.getSpell());
				SpecialAttack spec = SpecialAttack.of(base, specCtx);
				Held held = spec == null || spec.getEnergy() <= 0 ? null
					: strongestHeld(worn, weapon, base, weaponCost, wornCost, specCtx);
				if (held != null)
				{
					candidate.accept(weapon, o, spec, held);
				}
			}
		}
	}

	/** One player's share of each kill in a raid fight, which their own spec energy is spent over. */
	private int raidShareHp()
	{
		return Math.max(1, (int) Math.ceil(ctx.getTargetHitpoints() / (double) Math.max(1, settings.getRaidKillShare())));
	}

	/** The ordinary attacks with the raid fight's final blows: each kill's last attack doesn't wait out its interval. */
	private DpsResult withFinalBlows(DpsResult ordinary)
	{
		if (settings.getRaidKillShare() <= 0 || settings.getFinalBlows() <= 0 || ordinary.getPrimaryDps() <= 0)
		{
			return ordinary;
		}
		int hp = raidShareHp();
		SpecialRotation.Rotation plain = SpecialRotation.rotation(ordinary, hp / ordinary.getPrimaryDps(), hp, 0, 0,
			Collections.emptyList(), settings.getFinalBlows());
		return plain == null ? ordinary : plain.getResult();
	}

	/**
	 * A raid fight's spec rotation over one player's share of each kill ({@link OptimizerSettings#getRaidKillShare}): a
	 * draining opener where its drains last, then at most one damage weapon; at most {@link #RAID_SPEC_WEAPONS} spec weapons, bought within what the budget has left. It's planned
	 * on a full bar, with the best rotation on energy regenerated during the fight alone as its alternative. Null when
	 * no rotation beats the ordinary attacks with their own final blows.
	 */
	private Specced raidSpecial(Loadout worn, CombatClass cls, DpsResult ordinary, long wornCost, double regen, DoubleConsumer progress)
	{
		CombatContext specCtx = ctx.withModifiers(ctx.getModifiers().toBuilder().specialAttack(true).build());
		int hp = raidShareHp();
		double rate = ordinary.getPrimaryDps();
		if (rate <= 0)
		{
			return null;
		}
		boolean drainsLast = settings.getDrainSpecs() != DrainSpecs.DAMAGE_ONLY && CombatRules.drainPersistence(ctx.getMonster()) > 0;
		Map<SpecialAttacks, DpsResult> drainedOrdinary = new HashMap<>();
		drainedOrdinary.put(SpecialAttacks.NONE, ordinary);
		// Each weapon's strongest style, as a damage spec or a draining opener.
		Map<GearItem, SpecChoice> damage = new LinkedHashMap<>();
		Map<GearItem, SpecChoice> drains = new LinkedHashMap<>();
		forEachSpec(worn, cls, wornCost, specCtx, progress, (weapon, o, spec, held) ->
		{
			SpecItems items = specItems(worn, weapon, held.getLoadout());
			boolean draining = drainsLast && spec.getDrain() != SpecialAttack.Drain.NONE;
			SpecChoice choice = new SpecChoice(weapon, o, held.getLoadout().copy(), held.getResult(), spec, items.getOffhand(),
				items.getAmmo(), held.getCost(), items.getUnpriced(), draining
				? drainingFight(worn, held.getLoadout().copy(), held.getResult(), specCtx, drainedOrdinary) : null);
			Map<GearItem, SpecChoice> role = draining ? drains : damage;
			SpecChoice kept = role.get(weapon);
			if (kept == null || choice.gainPerEnergy(rate) > kept.gainPerEnergy(rate))
			{
				role.put(weapon, choice);
			}
		});
		// The few damage specs gaining the most per energy, and the draining openers that alone finish the kill soonest.
		List<SpecChoice> damageChoices = new ArrayList<>(damage.values());
		damageChoices.sort(Comparator.comparingDouble((SpecChoice c) -> -c.gainPerEnergy(rate)));
		damageChoices = damageChoices.subList(0, Math.min(RAID_DAMAGE_CHOICES, damageChoices.size()));
		List<SpecChoice> drainChoices = new ArrayList<>(drains.values());
		// Where teammates open with reducers, a player who can use an elder maul, warhammer or godsword takes their fair
		// share of the party's reducers with one of them, whatever the kit's class (a ranged or magic kit switches to it
		// for the spec); teammates no longer cover that share, whatever this player actually spends.
		List<SpecChoice> sharing = drainChoices.stream().filter(c -> SpecialRotation.sharedReducer(c.getSpec().getDrain())
			&& withinBudget(wornCost + c.getExtra())).collect(Collectors.toList());
		// Every kit owes the share whenever the player owns one, even with it left out here (then it can't pay it).
		boolean owes = !sharing.isEmpty() || settings.isSharedReducerUsable();
		share = owes ? reducerShare() : 0;
		if (share > 0) { drainChoices = sharing; }
		kitShares.put(worn, share);
		teamOrdinary.remove(worn);
		drainChoices.sort(Comparator.comparingDouble((SpecChoice c) -> drainedSeconds(c, Math.min(maxSpecs(c), ownReducerLimit()), hp, rate)[0]));
		drainChoices = drainChoices.subList(0, Math.min(RAID_DRAIN_CHOICES, drainChoices.size()));
		// Every choice of this worn kit, including no personal specs, follows the same teammates' openers.
		plainSeconds = plainSeconds(worn, ordinary, hp, share);
		List<RaidRotation> variants = new ArrayList<>();
		RaidRotation full = bestRotation(ordinary, hp, rate, regen, Math.max(0, Math.min(100, settings.getRaidOpeningEnergy())), damageChoices, drainChoices,
			wornCost, variants, range(progress, 0.7, 0.2));
		progress.accept(0.9);
		DpsResult plain = raidOrdinary(worn, ordinary);
		if (full == null || metric(full.rotation.getResult()) <= metric(plain) + EPS)
		{
			return null;
		}
		Map<List<Integer>, RaidRotation> distinct = new LinkedHashMap<>();
		for (RaidRotation candidate : variants)
		{
			if (metric(candidate.rotation.getResult()) <= metric(plain) + EPS) { continue; }
			List<Integer> key = new ArrayList<>();
			key.add(candidate.openerSpecs);
			key.add((int) Math.round(candidate.openingEnergy));
			if (candidate.opener != null) { key.add(candidate.opener.getWeapon().getId()); }
			for (int j = 0; j < candidate.damage.size(); j++)
			{ if (candidate.rotation.getSpecs()[j] > 0) { key.add(candidate.damage.get(j).getWeapon().getId()); } }
			RaidRotation old = distinct.get(key);
			if (old == null || metric(candidate.rotation.getResult()) > metric(old.rotation.getResult())) { distinct.put(key, candidate); }
		}
		List<Specced> alternatives = new ArrayList<>();
		int alternativeIndex = 0;
		for (RaidRotation candidate : distinct.values())
		{
			alternatives.add(raidPlan(candidate, ordinary, plain, hp, rate, regen, wornCost, worn));
			progress.accept(0.9 + 0.1 * ++alternativeIndex / distinct.size());
		}
		raidSpecVariants.put(worn, alternatives);
		return raidPlan(full, ordinary, plain, hp, rate, regen, wornCost, worn);
	}

	/** Rates without the opening bar retain the same carried spec weapons. */
	private Specced raidPlan(RaidRotation full, DpsResult ordinary, DpsResult plain, int hp, double rate, double regen,
		long wornCost, Loadout worn)
	{
		RaidRotation regenerated = bestRotation(ordinary, hp, rate, regen, 0, full.damage, Collections.emptyList(), wornCost, null);
		boolean regenerates = regenerated != null && metric(regenerated.rotation.getResult()) > metric(plain) + EPS;
		SpecialPlan regeneratedPlan = regenerates ? regenerated.plan(ordinary, null, 0, null) : null;
		DpsResult regeneratedRates = regenerates ? regenerated.rotation.getResult() : plain;
		boolean fullBar = settings.getSpecEnergy() == SpecEnergy.FULL_BAR;
		SpecialPlan plan = full.plan(ordinary, fullBar ? regeneratedRates : null,
			regenerates ? regenerated.totalSpecs() : 0, regeneratedPlan);
		return new Specced(plan, full.rotation.getResult(), worn);
	}

	/** Alternate spec choices for the final worn candidates, so the raid can share one weapon of each role. */
	private final Map<Loadout, List<Specced>> raidSpecVariants = new IdentityHashMap<>();
	private final Map<Loadout, DpsResult> teamOrdinary = new IdentityHashMap<>();
	/** The teammates' reference hammers where the whole party opens with reducers; see {@link #teamFight()}. */
	private SpecialRotation.Fight teamFight;
	/** The current worn kit's kill time without personal specs; set before each rotation search. */
	private double plainSeconds;
	/** The current worn kit's assigned share of the party's reducers ({@link #reducerShare}); set before each rotation search. */
	private int share;
	private final Map<Loadout, Integer> kitShares = new IdentityHashMap<>();

	/** This player's fair share of the party's reducers where teammates' openers apply, else 0. */
	private int reducerShare()
	{
		return teamFight() == null ? 0
			: SpecialRotation.reducerShare(ctx.getMonster().getLowerName(), settings.getRaidKillShare());
	}

	/**
	 * The teammates' reference dragon warhammers ({@link SpecialRotation#teammateHammer}) where every other party
	 * member opens with one ({@link OptimizerSettings#isTeamOpeners}), or null. The same for every kit and class.
	 */
	private SpecialRotation.Fight teamFight()
	{
		if (!settings.isTeamOpeners() || settings.getRaidKillShare() <= 1)
		{
			return null;
		}
		if (teamFight == null)
		{
			teamFight = SpecialRotation.teammateHammer(ctx.getMonster());
		}
		return teamFight;
	}

	/** One player's share of each kill without personal specs, after any teammates' openers. */
	private double plainSeconds(Loadout worn, DpsResult ordinary, int hp, int assigned)
	{
		double rate = ordinary.getPrimaryDps();
		if (teamFight() == null || rate <= 0)
		{
			return hp / rate;
		}
		Map<SpecialAttacks, DpsResult> states = new HashMap<>();
		states.put(SpecialAttacks.NONE, ordinary);
		SpecialRotation.Fight own = new SpecialRotation.Fight()
		{
			@Override
			public DpsResult ordinary(SpecialAttacks drains)
			{
				return states.computeIfAbsent(drains, d -> DpsCalculator.calculate(worn, ctx.withDrains(d)));
			}

			@Override
			public DpsResult spec(SpecialAttacks drains)
			{
				return ordinary(drains);
			}
		};
		return drainedSeconds(own, SpecialAttack.Drain.NONE, 0, 0, hp, rate, assigned)[0];
	}

	/** The worn kit's ordinary attacks with final blows, after teammates' openers where the party opens with them. */
	private DpsResult raidOrdinary(Loadout worn, DpsResult ordinary)
	{
		if (teamFight() == null || settings.getRaidKillShare() <= 0 || ordinary.getPrimaryDps() <= 0)
		{
			return withFinalBlows(ordinary);
		}
		return teamOrdinary.computeIfAbsent(worn, w ->
		{
			int hp = raidShareHp();
			SpecialRotation.Rotation baseline = SpecialRotation.rotation(ordinary, plainSeconds(w, ordinary, hp,
				kitShares.getOrDefault(w, 0)), hp, 0, 0,
				Collections.emptyList(), settings.getFinalBlows());
			return baseline == null ? withFinalBlows(ordinary) : baseline.getResult();
		});
	}

	/** Opening specs a full bar holds of this spec. */
	private static int maxSpecs(SpecChoice choice)
	{
		return SpecialRotation.FULL_ENERGY / choice.getSpec().getEnergy();
	}

	/** A kill opened by this many of a draining spec, lasting the target's {@link CombatRules#drainPersistence}, then finished with ordinary attacks: {seconds, specs used}. */
	private double[] drainedSeconds(SpecChoice choice, int specs, int hp, double rate)
	{
		return drainedSeconds(choice.getFight(), choice.getSpec().getDrain(), choice.getSpec().getEnergy(), specs, hp, rate,
			share);
	}

	/** As above, for a kit assigned this many of the party's reducers ({@link #reducerShare}). */
	private double[] drainedSeconds(SpecialRotation.Fight fight, SpecialAttack.Drain drain, int energy, int specs, int hp,
		double rate, int assigned)
	{
		SpecialRotation.Fight teammate = teamFight();
		if (settings.getRaidKillShare() > 0)
		{
			int party = Math.max(1, settings.getRaidKillShare());
			int phases = drainPhases();
			int own = Math.min(specs, ownReducerLimit());
			double seconds = 0;
			double used = 0;
			for (int phase = 0; phase < phases; phase++)
			{
				int phaseHp = hp / phases + (phase < hp % phases ? 1 : 0);
				int phaseSpecs = phaseSpecs(own, phases, phase);
				int window = phases > 1 ? phaseHp : drainWindow(phaseHp, rate);
				boolean allMiss = settings.getDrainSpecs() == DrainSpecs.ALL_MISS;
				// Each Sotetseg third: the party's two reducers, this player's assigned or own one among them (the
				// assigned ones in the first thirds); teammates' first.
				int teammates = phases > 1
					? SpecialRotation.sotetsegTeammates(party, drain, phaseSpecs, phaseSpecs(assigned, phases, phase))
					: Math.min(SpecialRotation.MAX_TEAM_OPENERS, party - 1);
				double[] opened = teammate == null
					? SpecialRotation.drainOpenedShared(fight, drain, energy, phaseSpecs, window * party, allMiss, party)
					: SpecialRotation.drainOpenedTeam(fight, drain, energy, phaseSpecs, window * party, allMiss, party,
						teammates, teammate, SpecialAttack.Drain.DRAGON_WARHAMMER);
				seconds += opened[0] + (phaseHp - window) / rate;
				used += opened[1];
			}
			return new double[]{seconds, used};
		}
		int window = drainWindow(hp, rate);
		double[] opened = SpecialRotation.drainOpened(fight, drain, energy, specs, window,
			settings.getDrainSpecs() == DrainSpecs.ALL_MISS);
		return new double[]{opened[0] + (hp - window) / rate, opened[1]};
	}

	/**
	 * This player's opening specs in one of a fight's drain phases (Sotetseg's thirds): spread evenly, the extras in
	 * the earliest phases. Every third starts at the same restored Defence with the same teammates, so which thirds
	 * take a party player's one reducer each doesn't change their value.
	 */
	static int phaseSpecs(int specs, int phases, int phase)
	{
		return specs / phases + (phase < specs % phases ? 1 : 0);
	}

	/** Defence windows of this fight's HP: Sotetseg restores its Defence after each maze. */
	private int drainPhases()
	{
		return ctx.getMonster().getLowerName().startsWith("sotetseg") ? 3 : 1;
	}

	/** This player's reducer specs per kill: in a party at Sotetseg, at most one in each third. */
	private int ownReducerLimit()
	{
		return ownReducerLimit(settings.getRaidKillShare(), drainPhases());
	}

	/** This player's reducer specs per kill: one in each drain phase in a party, else unlimited. */
	static int ownReducerLimit(int party, int phases)
	{
		return party > 1 && phases > 1 ? phases : Integer.MAX_VALUE;
	}

	/**
	 * The fastest rotation from these choices on {@code energy} at the start of the kill: no opener or one draining
	 * opener (with each number of its specs the energy holds), and at most one damage spec, within
	 * {@link #RAID_SPEC_WEAPONS} weapons and the budget. Null when none can be dealt.
	 */
	private RaidRotation bestRotation(DpsResult ordinary, int hp, double rate, double regen, int energy,
		List<SpecChoice> damage, List<SpecChoice> drains, long wornCost, List<RaidRotation> variants)
	{
		return bestRotation(ordinary, hp, rate, regen, energy, damage, drains, wornCost, variants, fraction -> { });
	}

	private RaidRotation bestRotation(DpsResult ordinary, int hp, double rate, double regen, int energy,
		List<SpecChoice> damage, List<SpecChoice> drains, long wornCost, List<RaidRotation> variants, DoubleConsumer progress)
	{
		List<List<SpecChoice>> damageSets = new ArrayList<>();
		damageSets.add(Collections.emptyList());
		for (int a = 0; a < damage.size(); a++)
		{
			damageSets.add(Collections.singletonList(damage.get(a)));
		}
		List<SpecChoice> openers = new ArrayList<>();
		// An assigned share is made whenever the energy funds a reducer: as many as it funds, up to the share. Sotetseg's
		// one a third is exactly the share; in a single defence window more may follow.
		boolean assigned = share > 0 && drains.stream().anyMatch(c -> energy >= c.getSpec().getEnergy());
		if (!assigned) { openers.add(null); }
		openers.addAll(drains);
		RaidRotation best = null;
		int openerIndex = 0;
		for (SpecChoice opener : openers)
		{
			progress.accept((double) openerIndex++ / openers.size());
			int most = opener == null ? 0 : Math.min(Math.min(maxSpecs(opener), ownReducerLimit()),
				energy / opener.getSpec().getEnergy());
			if (opener != null && most <= 0)
			{
				continue;
			}
			int fewest = opener == null ? 0 : share > 0 ? Math.min(share, most) : 1;
			for (int specs = fewest; specs <= (opener == null || share == 0 || drainPhases() < 2 ? most : fewest); specs++)
			{
				double[] base = opener == null ? new double[]{plainSeconds, 0} : drainedSeconds(opener, specs, hp, rate);
				double left = energy - specs * (opener == null ? 0 : opener.getSpec().getEnergy());
				for (List<SpecChoice> set : damageSets)
				{
					if (set.size() + (opener == null ? 0 : 1) > RAID_SPEC_WEAPONS
						|| opener != null && set.stream().anyMatch(c -> c.getWeapon() == opener.getWeapon()))
					{
						continue;
					}
					long extra = (opener == null ? 0 : opener.getExtra()) + set.stream().mapToLong(SpecChoice::getExtra).sum();
					if (!withinBudget(wornCost + extra))
					{
						continue;
					}
					List<SpecialRotation.Damage> specials = new ArrayList<>();
					set.forEach(c -> specials.add(new SpecialRotation.Damage(c.getOpening(), c.getSpec().getEnergy())));
					SpecialRotation.Rotation rotation = SpecialRotation.rotation(ordinary, base[0], hp, left, regen, specials,
						settings.getFinalBlows());
					if (rotation == null)
					{
						continue;
					}
					RaidRotation candidate = new RaidRotation(rotation, opener, specs, base[1], set,
						specs * (opener == null ? 0 : opener.getSpec().getEnergy()) + rotation.getOpeningEnergy());
					if (variants != null) { variants.add(candidate); }
					if (best == null || metric(rotation.getResult()) > metric(best.rotation.getResult()) + EPS
						|| Math.abs(metric(rotation.getResult()) - metric(best.rotation.getResult())) <= EPS
						&& candidate.extra() < best.extra())
					{
						best = candidate;
					}
				}
			}
		}
		return best;
	}

	/**
	 * Whole-kill rates with this spec mixed in on the given energy, or null when it never helps. A draining spec
	 * ({@code fight} not null) opens the kill, and its drains last the target's {@link CombatRules#drainPersistence}.
	 */
	private SpecialRotation.Blend rotation(SpecialRotation.Fight fight, SpecialAttack spec, DpsResult ordinary,
		DpsResult opening, int hp, double regen, SpecEnergy energy)
	{
		if (fight == null)
		{
			return SpecialRotation.blend(ordinary, opening, spec.getEnergy(), hp, energy, regen);
		}
		int window = drainWindow(hp, ordinary.getPrimaryDps());
		SpecialRotation.Blend blend = SpecialRotation.draining(fight, spec.getDrain(), spec.getEnergy(), window, energy,
			regen, settings.getDrainSpecs() == DrainSpecs.ALL_MISS);
		return blend == null || window >= hp ? blend
			: SpecialRotation.resetAfter(blend, window, ordinary, opening, spec.getEnergy(), hp, regen);
	}

	/** Hitpoints of a kill dealt while drains last: the target's {@link CombatRules#drainPersistence} of them, at least one. */
	private int drainWindow(int hp, double rate)
	{
		if (ctx.getMonster().getLowerName().startsWith("pestilent bloat"))
		{
			// A run-by BGS can help one down; conservatively discard its remaining drain after that down.
			return Math.max(1, Math.min(hp, (int) Math.round(rate * 18)));
		}
		double persistence = CombatRules.drainPersistence(ctx.getMonster());
		return persistence >= 1 ? hp : Math.max(1, (int) Math.round(hp * persistence));
	}

	/** Ordinary and spec attacks once drains land; each state is calculated once. */
	private SpecialRotation.Fight drainingFight(Loadout worn, Loadout held, DpsResult opening, CombatContext specCtx,
		Map<SpecialAttacks, DpsResult> drainedOrdinary)
	{
		Map<SpecialAttacks, DpsResult> specs = new HashMap<>();
		specs.put(SpecialAttacks.NONE, opening);
		return new SpecialRotation.Fight()
		{
			@Override
			public DpsResult ordinary(SpecialAttacks drains)
			{
				return drainedOrdinary.computeIfAbsent(drains, d -> DpsCalculator.calculate(worn, ctx.withDrains(d)));
			}

			@Override
			public DpsResult spec(SpecialAttacks drains)
			{
				return specs.computeIfAbsent(drains, d -> DpsCalculator.calculate(held, specCtx.withDrains(d)));
			}
		};
	}

	/**
	 * Off-hands to hold with the spec weapon: none, or when a one-handed spec weapon leaves the worn shield
	 * slot empty (after a two-handed main weapon), the class's candidate shields too.
	 */
	private List<GearItem> offhands(Loadout worn, GearItem weapon)
	{
		if (weapon.isTwoHanded() || worn.get(Slot.SHIELD) != null || settings.getLocks().containsKey(Slot.SHIELD)
			|| specShields.isEmpty())
		{
			return Collections.singletonList(null);
		}
		List<GearItem> out = new ArrayList<>();
		out.add(null);
		out.addAll(specShields);
		return out;
	}

	/**
	 * The spec loadout with the ammunition, then the off-hand, that make its spec hit hardest (they hardly interact,
	 * and trying every pair would multiply the candidates), or null when none is protected and affordable.
	 */
	private Held strongestHeld(Loadout worn, GearItem weapon, Loadout base, long weaponCost, long wornCost,
		CombatContext specCtx)
	{
		Held ammoChoice = strongest(base, Slot.AMMO, specAmmo(worn, weapon), weaponCost, wornCost, specCtx);
		return ammoChoice == null ? null
			: strongest(ammoChoice.getLoadout(), Slot.SHIELD, offhands(worn, weapon), ammoChoice.getCost(), wornCost, specCtx);
	}

	/** The off-hand and ammunition a spec loadout holds beyond the worn setup, and how many bought items have no price. */
	private SpecItems specItems(Loadout worn, GearItem weapon, Loadout held)
	{
		GearItem offhand = held.get(Slot.SHIELD) == worn.get(Slot.SHIELD) ? null : held.get(Slot.SHIELD);
		GearItem ammo = held.get(Slot.AMMO) == worn.get(Slot.AMMO) ? null : held.get(Slot.AMMO);
		int unpriced = weapon == worn.getWeapon() ? 0 : (unpriced(weapon) ? 1 : 0) + (unpriced(held.getLoadedAmmo()) ? 1 : 0);
		unpriced += (unpriced(offhand) ? 1 : 0) + (unpriced(ammo) ? 1 : 0);
		return new SpecItems(offhand, ammo, unpriced);
	}

	/**
	 * The spec loadout with the item from {@code items} (null keeps the slot) that makes the spec hit hardest,
	 * cheapest on ties, or null when none is protected and affordable.
	 */
	private Held strongest(Loadout base, Slot slot, List<GearItem> items, long baseCost, long wornCost,
		CombatContext specCtx)
	{
		Held best = null;
		for (GearItem item : items)
		{
			Loadout l = base.copy();
			if (item != null)
			{
				l.set(slot, item);
			}
			long c = baseCost + (item == null ? 0 : itemCost(item));
			if (!protectedEquipment(l) || !withinBudget(wornCost + c))
			{
				continue;
			}
			probe.fill(l);
			DpsResult r = specResults.get(probe);
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
	private List<GearItem> specAmmo(Loadout worn, GearItem weapon)
	{
		if (weapon == worn.getWeapon() || !WeaponRules.firesAmmoSlot(weapon))
		{
			return Collections.singletonList(null);
		}
		List<GearItem> out = new ArrayList<>();
		for (GearItem a : compatibleAmmo(weapon, true))
		{
			out.add(a == worn.get(Slot.AMMO) ? null : a);
		}
		return out;
	}

	/**
	 * The worn setup holding the spec weapon instead, or null when it cannot fire: a blowpipe brings its own
	 * darts (ammo-slot ammunition is chosen separately).
	 */
	private Loadout specLoadout(Loadout worn, GearItem weapon)
	{
		Loadout l = worn.copy();
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
		if (WeaponRules.loadsAmmo(weapon))
		{
			List<GearItem> darts = compatibleAmmo(weapon, false);
			if (darts.isEmpty())
			{
				return null;
			}
			l.setLoadedAmmo(darts.get(0));
		}
		return l;
	}

	/**
	 * Usable weapons of the class with a damaging special attack; weapon locks and hand limits apply to the worn
	 * weapon only. Spec-only weapons (the Dawnbringer at Verzik P1) join every class with their own special, and so
	 * do the elder maul, dragon warhammer and Bandos godsword where the player owes a share of the party's reducers.
	 */
	private List<GearItem> specWeapons(CombatClass cls)
	{
		return specWeapons.computeIfAbsent(cls, c ->
		{
			List<GearItem> out = new ArrayList<>();
			for (GearItem w : data.getItems(Slot.WEAPON))
			{
				CombatClass own = specClass(w, c);
				if (usable(w) && WeaponRules.supports(w, own) && hasSpecialAttack(w, own))
				{
					out.add(w);
				}
			}
			return withoutCosmeticCopies(out);
		});
	}

	/**
	 * The class a spec weapon attacks with: the setup's, a spec-only weapon's own (magic for the Dawnbringer), or melee
	 * for a shared reducer switched to by a ranged or magic kit that owes the party its share ({@link #reducerShare}).
	 */
	private CombatClass specClass(GearItem weapon, CombatClass worn)
	{
		if (worn != CombatClass.MELEE && SpecialRotation.sharedReducerName(weapon.getName()) && reducerShare() > 0)
		{
			return CombatClass.MELEE;
		}
		if (WeaponRules.specOnly(weapon))
		{
			for (String raw : weapon.getStyles() == null ? java.util.Collections.<String>emptyList() : weapon.getStyles())
			{
				AttackStyle style = AttackStyle.parse(raw);
				if (style != null)
				{
					return WeaponRules.classOf(style);
				}
			}
		}
		return worn;
	}

	// ------------------------------------------------------------ fill phase

	/**
	 * Fill slots that add nothing with prayer or defence items, allowing up to the configured DPS
	 * margin to be lost. Slots locked to FILL are always filled. Returns the filled slots.
	 */
	private Set<Slot> fill(Loadout l)
	{
		boolean anyForced = settings.getLocks().values().stream().anyMatch(k -> k.getKind() == SlotLock.Kind.FILL);
		if (settings.getFillMode() == FillMode.NONE && !anyForced)
		{
			return Collections.emptySet();
		}
		GearItem weapon = l.getWeapon();
		boolean firesAmmo = WeaponRules.firesAmmoSlot(weapon);
		DpsResult base = DpsCalculator.calculate(l, ctx);
		double minDps = base.getDps() * (1 - Math.max(0, settings.getFillMarginPercent()) / 100) - EPS;
		double basePrimary = primary(base);
		ToDoubleFunction<GearItem> metric = fillMetric(settings.getFillMode() == FillMode.NONE
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
			GearItem orig = l.get(slot);
			GearItem bestItem = orig;
			double bestMetric = orig == null ? 0 : metric.applyAsDouble(orig);
			long bestCost = curCost;
			long baseCost = curCost - itemCost(orig);
			for (GearItem cand : data.getItems(slot))
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
				DpsResult r = DpsCalculator.calculate(l, ctx);
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

	private ToDoubleFunction<GearItem> fillMetric(FillMode mode)
	{
		if (mode == FillMode.PRAYER)
		{
			return GearItem::getPrayerBonus;
		}
		switch (settings.getDefenceFocus())
		{
			case STAB:
				return GearItem::getStabDef;
			case SLASH:
				return GearItem::getSlashDef;
			case CRUSH:
				return GearItem::getCrushDef;
			case MELEE:
				return i -> i.getStabDef() + i.getSlashDef() + i.getCrushDef();
			case MAGIC:
				return GearItem::getMagicDef;
			case RANGED:
				return GearItem::getRangedDef;
			case TOTAL:
				return i -> i.getStabDef() + i.getSlashDef() + i.getCrushDef() + i.getMagicDef() + i.getRangedDef();
			default:
				return targetDefenceMetric();
		}
	}

	/** Weighted by how often the monster uses each attack type (typeless attacks ignored). */
	private ToDoubleFunction<GearItem> targetDefenceMetric()
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
	Map<Slot, List<GearItem>> candidates(CombatClass cls)
	{
		List<ToDoubleFunction<GearItem>> stats = damageStats(cls);
		Map<Slot, List<GearItem>> out = new EnumMap<>(Slot.class);
		for (Slot slot : SLOTS)
		{
			if (slot == Slot.WEAPON || slot == Slot.AMMO)
			{
				continue;
			}
			List<GearItem> pool = new ArrayList<>();
			for (GearItem item : data.getItems(slot))
			{
				if (usable(item))
				{
					pool.add(item);
				}
			}
			Set<GearItem> keep = new LinkedHashSet<>(nondominated(pool, stats));
			for (GearItem item : pool)
			{
				if (isSpecial(item) || DragonfireProtection.isProtectiveShield(item)
					|| SlayerEquipment.isRequired(monster, settings, item)
					|| settings.isRequireAtlatlAmmoRecovery() && WeaponRules.isAmmoRecoveryCape(item))
				{
					keep.add(item);
				}
			}
			out.put(slot, new ArrayList<>(keep));
		}
		return out;
	}

	/** The item bonuses the class's damage reads; damage never falls as one of them rises. */
	private List<ToDoubleFunction<GearItem>> damageStats(CombatClass cls)
	{
		List<ToDoubleFunction<GearItem>> m = new ArrayList<>();
		switch (cls)
		{
			case MELEE:
				m.add(GearItem::getStabBonus);
				m.add(GearItem::getSlashBonus);
				m.add(GearItem::getCrushBonus);
				m.add(GearItem::getMeleeStr);
				// Defence-derived strength reads this sum.
				m.add(i -> i.getStabDef() + i.getSlashDef() + i.getCrushDef() + i.getRangedDef());
				break;
			case RANGED:
				m.add(GearItem::getRangedBonus);
				m.add(GearItem::getRangedStr);
				if (settings.getStyles().contains(AttackStyle.Type.ATLATL) || strengthRangedWeaponUsable())
				{
					// The atlatl and Hunter's spear hit with melee strength but aim with ranged attack.
					m.add(GearItem::getMeleeStr);
				}
				break;
			default:
				m.add(GearItem::getMagicBonus);
				m.add(GearItem::getMagicStr);
				break;
		}
		return m;
	}

	private boolean strengthRangedWeaponUsable()
	{
		for (GearItem weapon : data.getItems(Slot.WEAPON))
		{
			if (WeaponRules.scalesWithStrength(weapon) && !WeaponRules.isAtlatl(weapon) && usable(weapon))
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
	private List<GearItem> nondominated(List<GearItem> pool, List<ToDoubleFunction<GearItem>> stats)
	{
		int n = pool.size();
		double[][] values = new double[n][stats.size()];
		long[] costs = new long[n];
		boolean[] risky = new boolean[n];
		for (int i = 0; i < n; i++)
		{
			GearItem item = pool.get(i);
			for (int k = 0; k < stats.size(); k++)
			{
				values[i][k] = stats.get(k).applyAsDouble(item);
			}
			costs[i] = itemCost(item);
			risky[i] = settings.isWildernessRiskLimited() && expensive(item);
		}
		List<GearItem> out = new ArrayList<>();
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

	/** Which of the slot's candidates are plain for this weapon's ascent; rebuilds the bound items when the weapon changes. */
	private boolean[] plainCandidates(GearItem weapon, Slot slot)
	{
		if (weapon != boundWeapon)
		{
			boundWeapon = weapon;
			boundItems.clear();
			plainCandidates.clear();
			for (Map.Entry<Slot, List<GearItem>> e : boundCandidates.entrySet())
			{
				List<GearItem> list = e.getValue();
				boolean[] plain = new boolean[list.size()];
				// Starting from zero keeps every bonus at least the empty slot's.
				GearItem top = new GearItem();
				top.setName("");
				top.setId(-1);
				top.setSlot(e.getKey());
				for (int i = 0; i < plain.length; i++)
				{
					GearItem item = list.get(i);
					plain[i] = plain(item, weapon);
					if (plain[i])
					{
						top.setStabBonus(Math.max(top.getStabBonus(), item.getStabBonus()));
						top.setSlashBonus(Math.max(top.getSlashBonus(), item.getSlashBonus()));
						top.setCrushBonus(Math.max(top.getCrushBonus(), item.getCrushBonus()));
						top.setRangedBonus(Math.max(top.getRangedBonus(), item.getRangedBonus()));
						top.setMagicBonus(Math.max(top.getMagicBonus(), item.getMagicBonus()));
						top.setMeleeStr(Math.max(top.getMeleeStr(), item.getMeleeStr()));
						top.setRangedStr(Math.max(top.getRangedStr(), item.getRangedStr()));
						top.setMagicStr(Math.max(top.getMagicStr(), item.getMagicStr()));
						top.setPrayerBonus(Math.max(top.getPrayerBonus(), item.getPrayerBonus()));
						top.setStabDef(Math.max(top.getStabDef(), item.getStabDef()));
						top.setSlashDef(Math.max(top.getSlashDef(), item.getSlashDef()));
						top.setCrushDef(Math.max(top.getCrushDef(), item.getCrushDef()));
						top.setRangedDef(Math.max(top.getRangedDef(), item.getRangedDef()));
						top.setMagicDef(Math.max(top.getMagicDef(), item.getMagicDef()));
					}
				}
				boundItems.put(e.getKey(), top);
				plainCandidates.put(e.getKey(), plain);
			}
		}
		return plainCandidates.get(slot);
	}

	/**
	 * Equipment that changes this weapon's score only through its bonuses, the score never falling as one rises: no
	 * named effect the calculator checks can apply with this weapon and target, and it isn't protection or ammunition
	 * recovery the setup may need. Effects not recognised here count as applying.
	 */
	private boolean plain(GearItem item, GearItem weapon)
	{
		if (DragonfireProtection.isProtectiveShield(item) || SlayerEquipment.isRequired(monster, settings, item)
			|| WeaponRules.isAmmoRecoveryCape(item) && settings.isRequireAtlatlAmmoRecovery()
				&& WeaponRules.firesRecoverableAmmo(weapon, boundClass))
		{
			return false;
		}
		// Effects match the calculator's name, which drops the "corrupted " of Deadman items.
		String name = EncounterDamage.lower(item);
		// Coloured slayer helmets ("hydra slayer helmet") have the effect without the special name.
		if (!specialName(item.getLowerCombatName()) && !specialName(name) && !name.contains("max cape")
			&& !DpsCalculator.isSlayerHelm(item))
		{
			return true;
		}
		return !ctx.getModifiers().isSpecialAttack() && !effectApplies(item, name, weapon);
	}

	/** Whether a named effect of the item could change this weapon's score against this target; true if unsure. */
	private boolean effectApplies(GearItem item, String name, GearItem weapon)
	{
		CombatClass cls = boundClass;
		String w = EncounterDamage.lower(weapon);
		boolean ahrim = cls == CombatClass.MAGIC && EquipmentEffects.namedWeapon(weapon, "ahrim's staff");
		boolean karil = cls == CombatClass.RANGED && EquipmentEffects.namedWeapon(weapon, "karil's crossbow");
		// Target stats can follow its remaining HP; keep target-specific effects whenever they might.
		boolean fixedTarget = ctx.getHealthStates() == null;
		if (name.startsWith("dharok's "))
		{
			return cls == CombatClass.MELEE && EquipmentEffects.namedWeapon(weapon, "dharok's greataxe");
		}
		if (name.startsWith("verac's "))
		{
			return cls == CombatClass.MELEE && EquipmentEffects.namedWeapon(weapon, "verac's flail");
		}
		if (name.startsWith("blood moon "))
		{
			return cls == CombatClass.MELEE && EquipmentEffects.namedWeapon(weapon, "dual macuahuitl");
		}
		if (name.startsWith("ahrim's "))
		{
			return ahrim;
		}
		if (name.startsWith("karil's "))
		{
			return karil;
		}
		if (name.startsWith("amulet of the damned"))
		{
			return ahrim || karil;
		}
		if (name.startsWith("eclipse moon "))
		{
			return cls == CombatClass.RANGED && WeaponRules.isAtlatl(weapon);
		}
		if (name.startsWith("obsidian ") || name.startsWith("berserker necklace"))
		{
			return cls == CombatClass.MELEE && (w.startsWith("toktz-") || w.startsWith("tzhaar-ket-"));
		}
		if (name.startsWith("inquisitor's "))
		{
			return cls == CombatClass.MELEE;
		}
		if (name.startsWith("crystal helm") || name.startsWith("crystal body") || name.startsWith("crystal legs"))
		{
			return cls == CombatClass.MELEE
				|| cls == CombatClass.RANGED && (w.startsWith("bow of faerdhinen") || w.startsWith("crystal bow"));
		}
		if (name.startsWith("void melee helm"))
		{
			return cls == CombatClass.MELEE;
		}
		if (name.startsWith("void ranger helm"))
		{
			return cls == CombatClass.RANGED;
		}
		if (name.startsWith("void mage helm"))
		{
			return cls == CombatClass.MAGIC;
		}
		if (name.startsWith("virtus ") || name.startsWith("tome of ") || name.startsWith("elemental amulet")
			|| name.startsWith("amulet of air") || name.startsWith("amulet of water") || name.startsWith("amulet of earth")
			|| name.startsWith("amulet of fire") || name.startsWith("chaos gauntlets")
			|| name.startsWith("confliction gauntlets") || name.startsWith("brimstone ring"))
		{
			return cls == CombatClass.MAGIC;
		}
		if ((name.contains("saradomin") || name.contains("guthix") || name.contains("zamorak")) && name.contains("cape"))
		{
			// Only Charge reads the god capes.
			return cls == CombatClass.MAGIC && ctx.getModifiers().isCharge();
		}
		if (name.startsWith("dizana's") || name.startsWith("blessed dizana's"))
		{
			return cls == CombatClass.RANGED;
		}
		if (name.startsWith("salve amulet"))
		{
			return !fixedTarget || monster.hasAttribute("undead");
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
		return true;
	}

	private static boolean dominates(List<GearItem> pool, double[][] values, long[] costs, boolean[] risky, int j, int i)
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
		// only the first of exact duplicates.
		GearItem a = pool.get(j);
		GearItem b = pool.get(i);
		return betterBonuses(a, b) || j < i && noWorseBonuses(a, b) && noWorseBonuses(b, a);
	}

	/** Prefer a strict stat upgrade when score and acquisition cost tie; avoid arbitrary swap cycles. */
	private static boolean betterBonuses(GearItem candidate, GearItem current)
	{
		return noWorseBonuses(candidate, current) && !noWorseBonuses(current, candidate);
	}

	/** Equip useful offensive stats on a plateau so another empty slot can reach the next max hit. */
	private static boolean usefulBonuses(GearItem candidate, Loadout loadout)
	{
		if (candidate == null)
		{
			return false;
		}
		switch (WeaponRules.classOf(loadout.getStyle()))
		{
			case MELEE:
				int attack = loadout.getStyle().getType() == AttackStyle.Type.STAB ? candidate.getStabBonus()
					: loadout.getStyle().getType() == AttackStyle.Type.SLASH ? candidate.getSlashBonus()
					: candidate.getCrushBonus();
				return attack >= 0 && candidate.getMeleeStr() >= 0 && (attack > 0 || candidate.getMeleeStr() > 0);
			case RANGED:
				int strength = WeaponRules.scalesWithStrength(loadout.getWeapon())
					? candidate.getMeleeStr() : candidate.getRangedStr();
				return candidate.getRangedBonus() >= 0 && strength >= 0
					&& (candidate.getRangedBonus() > 0 || strength > 0);
			default:
				return candidate.getMagicBonus() >= 0 && candidate.getMagicStr() >= 0
					&& (candidate.getMagicBonus() > 0 || candidate.getMagicStr() > 0);
		}
	}

	private static boolean noWorseBonuses(GearItem candidate, GearItem current)
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

	private static boolean isSpecial(GearItem item)
	{
		return specialName(item.getLowerCombatName());
	}

	private static boolean specialName(String n)
	{
		for (String p : SPECIAL_PREFIXES)
		{
			if (n.startsWith(p))
			{
				return true;
			}
		}
		return false;
	}

	private List<GearItem> weapons(CombatClass cls)
	{
		SlotLock weaponLock = settings.getLocks().get(Slot.WEAPON);
		SlotLock shieldLock = settings.getLocks().get(Slot.SHIELD);
		boolean shieldTaken = shieldLock != null && shieldLock.getKind() != SlotLock.Kind.EMPTY;
		List<GearItem> out = new ArrayList<>();
		for (GearItem w : data.getItems(Slot.WEAPON))
		{
			if (!usable(w) || WeaponRules.specOnly(w) || !settings.getRaidLoadout().allowsWeapon(w))
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
			if (!WeaponRules.supports(w, cls) || ctx.getModifiers().isSpecialAttack() && !hasSpecialAttack(w, cls))
			{
				continue;
			}
			if (settings.getWeaponHands() == WeaponHands.ONE_HANDED && w.isTwoHanded()
				|| settings.getWeaponHands() == WeaponHands.TWO_HANDED && !w.isTwoHanded()
				|| shieldTaken && w.isTwoHanded())
			{
				continue;
			}
			out.add(w);
		}
		return withoutCosmeticCopies(out);
	}

	/**
	 * One weapon per set of cosmetic variants: the owned or cheapest priced one, then the snapshot item. Each copy
	 * would repeat the same ascent and crowd distinct weapons out of the capped preselection and refinement sweeps.
	 * A cheaper copy over the Wilderness value limit doesn't replace a dearer one under it, so both stay.
	 */
	private List<GearItem> withoutCosmeticCopies(List<GearItem> weapons)
	{
		Map<Integer, List<GearItem>> byCombatId = new HashMap<>();
		for (GearItem w : weapons)
		{
			byCombatId.computeIfAbsent(w.getCombatId(), k -> new ArrayList<>()).add(w);
		}
		List<GearItem> out = new ArrayList<>(weapons.size());
		for (GearItem w : weapons)
		{
			boolean replaced = false;
			for (GearItem other : byCombatId.get(w.getCombatId()))
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
	private boolean preferredCopy(GearItem a, GearItem b)
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

	/** Recovery capes that satisfy availability and the cape lock, before combined setup costs are checked. */
	private List<GearItem> ammoRecoveryCapes()
	{
		SlotLock lock = settings.getLocks().get(Slot.CAPE);
		List<GearItem> capes = new ArrayList<>();
		for (GearItem cape : data.getItems(Slot.CAPE))
		{
			if (WeaponRules.isAmmoRecoveryCape(cape) && usable(cape)
				&& (lock == null || lock.getKind() == SlotLock.Kind.FILL
					|| lock.getKind() == SlotLock.Kind.ITEM && lock.getItemId() == cape.getId()))
			{
				capes.add(cape);
			}
		}
		return capes;
	}

	/** Whether any of the weapon's styles in this class has a damaging special attack. */
	private boolean hasSpecialAttack(GearItem weapon, CombatClass cls)
	{
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, weapon);
		for (String raw : weapon.getStyles() == null ? java.util.Collections.<String>emptyList() : weapon.getStyles())
		{
			AttackStyle style = AttackStyle.parse(raw);
			if (style != null && WeaponRules.classOf(style) == cls)
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

	/** Rank weapons by score with no other gear and keep the strongest. */
	private List<GearItem> preselectWeapons(List<GearItem> weapons, CombatClass cls)
	{
		List<Scored> scored = new ArrayList<>();
		for (GearItem w : weapons)
		{
			List<Option> options = options(w, cls);
			if (options.isEmpty())
			{
				continue;
			}
			boolean fires = WeaponRules.firesAmmoSlot(w);
			boolean loads = WeaponRules.loadsAmmo(w);
			List<GearItem> ammo = fires || loads ? compatibleAmmo(w, fires) : null;
			if (ammo != null && ammo.isEmpty())
			{
				continue;
			}
			Loadout l = new Loadout();
			l.set(Slot.WEAPON, w);
			double best = 0;
			for (Option o : options)
			{
				l.setStyle(o.getStyle());
				l.setSpell(o.getSpell());
				if (ammo != null)
				{
					for (GearItem a : ammo)
					{
						l.set(Slot.AMMO, fires ? a : null);
						l.setLoadedAmmo(loads ? a : null);
						best = Math.max(best, metric(DpsCalculator.calculate(l, ctx)));
					}
				}
				else
				{
					best = Math.max(best, metric(DpsCalculator.calculate(l, ctx)));
				}
			}
			scored.add(new Scored(w, best));
		}
		scored.sort(Comparator.comparingDouble((Scored s) -> -s.getScore()));
		List<GearItem> out = new ArrayList<>();
		for (int i = 0; i < Math.min(maxWeapons, scored.size()); i++)
		{
			out.add(scored.get(i).getItem());
		}
		if (settings.isWildernessRiskLimited())
		{
			// Cheap weapons leave another expensive-item allowance for armour.
			int cheap = 0;
			for (Scored s : scored)
			{
				if (!expensive(s.getItem()) && cheap++ < maxWeapons && !out.contains(s.getItem()))
				{
					out.add(s.getItem());
				}
			}
		}
		// Bare-weapon ranking cannot measure a full set, spell interaction, defence-derived strength or
		// Tumeken's shadow tripling the gear's magic bonuses.
		for (Scored s : scored)
		{
			String n = s.getItem().getLowerCombatName();
			if ((n.startsWith("dharok's") || n.startsWith("verac's") || n.startsWith("ahrim's")
				|| n.startsWith("karil's") || n.equals("dual macuahuitl") || n.equals("eclipse atlatl")
				|| n.contains("bulwark") || n.startsWith("twinflame staff") || n.contains("tumeken's shadow"))
				&& !out.contains(s.getItem()))
			{
				out.add(s.getItem());
			}
		}
		return out;
	}

	private List<Option> options(GearItem weapon, CombatClass cls)
	{
		return options(weapon, cls, false);
	}

	/** @param anyType allow every attack type of the class, not just the searched tab's (spec weapons) */
	private List<Option> options(GearItem weapon, CombatClass cls, boolean anyType)
	{
		List<Option> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		List<Spell> spells = null;
		for (String raw : weapon.getStyles())
		{
			AttackStyle s = AttackStyle.parse(raw);
			if (s == null || WeaponRules.classOf(s) != cls || !seen.add(s.getType() + "/" + s.getStance())
				|| !styleAllowed(weapon, s, anyType))
			{
				continue;
			}
			if (cls == CombatClass.MAGIC)
			{
				if (weapon.getSubcategory().equals("salamander") && WeaponRules.salamanderMaxHit(weapon, ctx.getMagic()) > 0)
				{
					out.add(new Option(s, null));
				}
				else if (s.isAutocast())
				{
					if (spells == null)
					{
						spells = spellsFor(weapon);
					}
					for (Spell spell : spells)
					{
						out.add(new Option(s, spell));
					}
				}
				else if (WeaponRules.isPoweredStaff(weapon) && WeaponRules.poweredStaffMaxHit(weapon, ctx.getMagic()) > 0)
				{
					out.add(new Option(s, null));
				}
			}
			else
			{
				out.add(new Option(s, null));
			}
		}
		if (!anyType) { out.removeIf(option -> !settings.getRaidLoadout().allowsSpell(option.getSpell())); }
		// Maiden freezes are manually cast after switches. Owning an Ancient autocast staff isn't required:
		// keep the owned magic weapon's bonuses, but use Ice Barrage's damage and five-tick cast interval.
		if (!anyType && cls == CombatClass.MAGIC && settings.getRaidLoadout() == RaidLoadout.ICE_BARRAGE)
		{
			AttackStyle manual = AttackStyle.parse("manual cast,magic,manual");
			if (styleAllowed(weapon, manual))
			{
				for (Spell spell : spellsFor(weapon, true))
				{
					if (out.stream().noneMatch(option -> option.getSpell() == spell)) { out.add(new Option(manual, spell)); }
				}
			}
		}
		return out;
	}

	/** Reach, attack type and experience filter. */
	boolean styleAllowed(GearItem weapon, AttackStyle s)
	{
		return styleAllowed(weapon, s, false);
	}

	private boolean styleAllowed(GearItem weapon, AttackStyle s, boolean anyType)
	{
		if (!AttackReach.canReach(monster, weapon, s, settings.getTargetDistance())
			|| !anyType && !settings.getStyles().contains(WeaponRules.tabType(weapon, s)))
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

	private List<Spell> spellsFor(GearItem weapon)
	{
		return spellsFor(weapon, false);
	}

	/** Manual Ice Barrage uses the same spellbook and level gates without the weapon's autocast restriction. */
	private List<Spell> spellsFor(GearItem weapon, boolean manual)
	{
		List<Spell> out = new ArrayList<>();
		for (Spell spell : data.getSpells())
		{
			String name = spell.getName().toLowerCase(Locale.ROOT);
			String book = spell.getSpellbook() == null ? "" : spell.getSpellbook().toLowerCase(Locale.ROOT);
			if (spell.getMaxHit() <= 0 || spell.getLevel() > ctx.getMagic()
				|| !settings.getRaidLoadout().allowsSpell(spell)
				|| !settings.getSpellbooks().contains(book) || !manual && !spell.castableWith(weapon.getCombatId()))
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
			out.add(spell);
		}
		return out;
	}

	private List<GearItem> compatibleAmmo(GearItem weapon, boolean firesAmmoSlot)
	{
		SlotLock ammoLock = firesAmmoSlot ? settings.getLocks().get(Slot.AMMO) : null;
		List<GearItem> listed = new ArrayList<>();
		for (int id : weapon.getAmmunition())
		{
			GearItem a = data.getItem(Slot.AMMO, id);
			if (a != null)
			{
				listed.add(a);
			}
		}
		// Cosmetic copies such as poisoned arrows have ids the Wiki's ammunition list doesn't name; they follow
		// their original, after it so equal-strength ties keep the listed order.
		for (GearItem a : data.getItems(Slot.AMMO))
		{
			if (a.getCombatId() != a.getId() && weapon.getAmmunition().contains(a.getCombatId()))
			{
				listed.add(a);
			}
		}
		List<GearItem> out = new ArrayList<>();
		for (GearItem a : listed)
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
		out.sort(Comparator.comparingInt((GearItem a) -> -a.getRangedStr()));
		return out;
	}

	private List<Seed> seeds(CombatClass cls)
	{
		List<Seed> seeds = new ArrayList<>();
		seeds.add(new Seed(new Loadout(), w -> true));

		String helm = cls == CombatClass.MELEE ? "void melee helm" : cls == CombatClass.RANGED ? "void ranger helm" : "void mage helm";
		GearItem voidHelm = find(Slot.HEAD, helm);
		GearItem gloves = find(Slot.HANDS, "void knight gloves");
		GearItem eliteTop = find(Slot.BODY, "elite void top");
		GearItem eliteRobe = find(Slot.LEGS, "elite void robe");
		GearItem top = find(Slot.BODY, "void knight top");
		GearItem robe = find(Slot.LEGS, "void knight robe");
		if (voidHelm != null && gloves != null)
		{
			if (eliteTop != null && eliteRobe != null)
			{
				seeds.add(Seed.wearing(set(voidHelm, gloves, eliteTop, eliteRobe), w -> true));
			}
			if (top != null && robe != null)
			{
				seeds.add(Seed.wearing(set(voidHelm, gloves, top, robe), w -> true));
			}
		}

		if (cls == CombatClass.MELEE)
		{
			addSetSeed(seeds, "dharok's helm", "dharok's platebody", "dharok's platelegs", "dharok's greataxe", false);
			addSetSeed(seeds, "verac's helm", "verac's brassard", "verac's plateskirt", "verac's flail", false);
			addSetSeed(seeds, "blood moon helm", "blood moon chestplate", "blood moon tassets", "dual macuahuitl", false);
			GearItem crystalHead = find(Slot.HEAD, "crystal helm");
			GearItem crystalBody = find(Slot.BODY, "crystal body");
			GearItem crystalLegs = find(Slot.LEGS, "crystal legs");
			GearItem blessing = find(Slot.AMMO, "crystal blessing");
			if (crystalHead != null && crystalBody != null && crystalLegs != null && blessing != null)
			{
				seeds.add(Seed.wearing(set(crystalHead, crystalBody, crystalLegs, blessing), w -> true));
			}
			GearItem oHelm = find(Slot.HEAD, "obsidian helmet");
			GearItem oBody = find(Slot.BODY, "obsidian platebody");
			GearItem oLegs = find(Slot.LEGS, "obsidian platelegs");
			if (oHelm != null && oBody != null && oLegs != null)
			{
				seeds.add(Seed.wearing(set(oHelm, oBody, oLegs), w ->
				{
					String n = w.getLowerCombatName();
					return n.startsWith("toktz-") || n.startsWith("tzhaar-ket-");
				}));
			}
			GearItem iHelm = find(Slot.HEAD, "inquisitor's great helm");
			GearItem iBody = find(Slot.BODY, "inquisitor's hauberk");
			GearItem iLegs = find(Slot.LEGS, "inquisitor's plateskirt");
			if (iHelm != null && iBody != null && iLegs != null)
			{
				seeds.add(Seed.wearing(set(iHelm, iBody, iLegs), w -> w.getStyles().stream().anyMatch(s -> s.contains(",crush,"))));
			}
		}
		else if (cls == CombatClass.RANGED)
		{
			addSetSeed(seeds, "karil's coif", "karil's leathertop", "karil's leatherskirt", "karil's crossbow", true);
			addSetSeed(seeds, "eclipse moon helm", "eclipse moon chestplate", "eclipse moon tassets", "eclipse atlatl", false);
			GearItem cHelm = find(Slot.HEAD, "crystal helm");
			GearItem cBody = find(Slot.BODY, "crystal body");
			GearItem cLegs = find(Slot.LEGS, "crystal legs");
			if (cHelm != null && cBody != null && cLegs != null)
			{
				seeds.add(Seed.wearing(set(cHelm, cBody, cLegs), w ->
				{
					String n = w.getLowerCombatName();
					return n.startsWith("bow of faerdhinen") || n.startsWith("crystal bow");
				}));
			}
		}
		else
		{
			addSetSeed(seeds, "ahrim's hood", "ahrim's robetop", "ahrim's robeskirt", "ahrim's staff", true);
		}
		return seeds;
	}

	private void addSetSeed(List<Seed> seeds, String head, String body, String legs, String weapon, boolean damned)
	{
		GearItem h = find(Slot.HEAD, head);
		GearItem b = find(Slot.BODY, body);
		GearItem g = find(Slot.LEGS, legs);
		GearItem neck = damned ? find(Slot.NECK, "amulet of the damned") : null;
		if (h != null && b != null && g != null && (!damned || neck != null))
		{
			Loadout seed = set(h, b, g);
			if (damned)
			{
				seed.set(Slot.NECK, neck);
			}
			seeds.add(Seed.wearing(seed, w -> EquipmentEffects.namedWeapon(w, weapon)));
		}
	}

	private static Loadout set(GearItem... items)
	{
		Loadout l = new Loadout();
		for (GearItem i : items)
		{
			l.set(i.getSlot(), i);
		}
		return l;
	}

	private GearItem find(Slot slot, String name)
	{
		for (GearItem i : data.getItems(slot))
		{
			if (EquipmentEffects.namedWeapon(i, name) && usable(i))
			{
				return i;
			}
		}
		return null;
	}

	// ------------------------------------------------------------ availability and cost

	private boolean usable(GearItem item)
	{
		return equipmentAllowed(item)
			&& meetsRequirements(item)
			&& available(item);
	}

	private boolean equipmentAllowed(GearItem item)
	{
		return !settings.getExcluded().contains(item.getId()) && equipmentRulesAllow(item);
	}

	/**
	 * Whether a melee kit could switch to an elder maul, dragon warhammer or Bandos godsword for its special, by the
	 * search's own rules (levels, equipment rules, ownership or budget), ignoring these exclusions (the kit planner's).
	 */
	public boolean sharedReducerUsable(Set<Integer> ignoredExclusions)
	{
		for (GearItem weapon : data.getItems(Slot.WEAPON))
		{
			if (SpecialRotation.sharedReducerName(weapon.getName())
				&& (!settings.getExcluded().contains(weapon.getId()) || ignoredExclusions.contains(weapon.getId()))
				&& equipmentRulesAllow(weapon) && meetsRequirements(weapon) && available(weapon)
				&& WeaponRules.supports(weapon, CombatClass.MELEE) && hasSpecialAttack(weapon, CombatClass.MELEE))
			{
				return true;
			}
		}
		return false;
	}

	private boolean equipmentRulesAllow(GearItem item)
	{
		return CombatRules.equipmentAllowed(monster, item)
			&& (settings.isMembersItems() || !item.isMembers())
			&& (settings.isDmmItems() || !item.isDmmEquipment())
			&& (settings.isBetaItems() || !item.isBetaEquipment())
			&& (settings.isBountyHunterItems() || !item.isBountyHunterEquipment());
	}

	/** Ownership requires the exact item or a reviewed equivalent charge state. */
	private boolean isOwned(GearItem item)
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

	private List<Integer> ownershipVariants(GearItem item)
	{
		return ownershipVariants.computeIfAbsent(item, GearItem::getOwnershipVariants);
	}

	boolean available(GearItem item)
	{
		// Best in slot has no limit: untradeables and every diary tier are assumed obtainable. Spec-only weapons are
		// picked up where they can be used.
		if (settings.getMode() == SearchMode.UNLIMITED || WeaponRules.specOnly(item)
			|| isOwned(item) && ammoShortfall(item) == 0)
		{
			return true;
		}
		if (DiaryRewards.isReward(item.getId()) || settings.getMode().isHeldOnly())
		{
			return false;
		}
		return purchasable(item) && withinBudget(item);
	}

	/**
	 * Tradeable, or bought through tradable components (a charged staff, a demonic weapon's synapse, an
	 * imbued ring). Other untradeables (fire capes, quest gear) count in owned modes only when owned.
	 */
	private boolean purchasable(GearItem item)
	{
		return !OwnershipRules.requiresOwnership(item.getId())
			&& (item.isTradeable() || ItemCosts.hasTradableComponents(item.getId()));
	}

	private boolean meetsRequirements(GearItem i)
	{
		return levels.canEquip(i);
	}

	// ------------------------------------------------------------ lock diagnostics

	/** Why this item cannot be worn in this search, in plain words, or null if it can. */
	public String unusableReason(GearItem item)
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
	public String weaponLockReason(GearItem weapon)
	{
		String reason = unusableReason(weapon);
		if (reason != null)
		{
			return reason;
		}
		if (settings.isRequireAtlatlAmmoRecovery() && WeaponRules.firesRecoverableAmmo(weapon, CombatClass.RANGED)
			&& !options(weapon, CombatClass.RANGED).isEmpty() && ammoRecoveryCapes().isEmpty())
		{
			return "it requires an Ava's device or Dizana's quiver, but none is usable with your cape lock, "
				+ "exclusions and availability settings";
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
		for (CombatClass cls : CombatClass.values())
		{
			if (!WeaponRules.supports(weapon, cls) || !classAllowed(monster, cls) || options(weapon, cls).isEmpty())
			{
				continue;
			}
			anyStyle = true;
			if (ctx.getModifiers().isSpecialAttack() && !hasSpecialAttack(weapon, cls))
			{
				continue;
			}
			boolean fires = WeaponRules.firesAmmoSlot(weapon);
			if ((fires || WeaponRules.loadsAmmo(weapon)) && compatibleAmmo(weapon, fires).isEmpty())
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
	public String protectionLockReason(Slot slot, GearItem item)
	{
		return SlayerEquipment.lockConflict(monster, settings, slot, item);
	}

	/** Acquisition price, or {@link ItemCosts#UNKNOWN} if a tradable component has no current quote. */
	private long price(GearItem item)
	{
		long price = priceFn.applyAsLong(item);
		return ItemCosts.isKnown(price) ? price : ItemCosts.UNKNOWN;
	}

	/**
	 * An unpriced purchase cannot be shown to fit a budget; uncounted ammunition needs no price. Only items
	 * used up per attack (ammo-slot ammunition, blowpipe darts, thrown weapons) are ammunition here: armour and
	 * reusable weapons with ranged bonuses are still bought once and need a price.
	 */
	private boolean withinBudget(GearItem item)
	{
		long price = price(item);
		if (!ItemCosts.isKnown(price))
		{
			return settings.getAmmoCount() <= 0 && WeaponRules.consumedPerAttack(item);
		}
		return price <= settings.getBudget();
	}

	private boolean unpriced(GearItem item)
	{
		if (item == null || WeaponRules.specOnly(item) || ItemCosts.isKnown(price(item)))
		{
			return false;
		}
		if (WeaponRules.consumedPerAttack(item))
		{
			// Only the units still to buy need a price; none are needed when the quantity isn't counted.
			return ammoShortfall(item) > 0;
		}
		return !isOwned(item);
	}

	/** Items in the setup that must be bought but have no current price, so its cost is incomplete. */
	public int unpricedItems(Loadout l)
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
	private long itemCost(GearItem item)
	{
		if (WeaponRules.consumedPerAttack(item))
		{
			return ammoCost(item);
		}
		return plainCost(item);
	}

	private long plainCost(GearItem item)
	{
		if (item == null || isOwned(item) || WeaponRules.specOnly(item))
		{
			return 0;
		}
		// Unpriced purchases are unavailable in budget mode; elsewhere cost only breaks ties and is reported as incomplete.
		return Math.max(0, price(item));
	}

	/** Price of the ammunition still to buy: the configured quantity minus the stack already held. */
	private long ammoCost(GearItem item)
	{
		long shortfall = ammoShortfall(item);
		return shortfall == 0 ? 0 : Math.max(0, price(item)) * shortfall;
	}

	/** Units of the configured ammo quantity not covered by what the player holds; 0 if not counted. */
	private long ammoShortfall(GearItem item)
	{
		long count = settings.getAmmoCount();
		if (count <= 0 || !WeaponRules.consumedPerAttack(item))
		{
			return 0;
		}
		return Math.max(0, count - heldQuantity(item));
	}

	/** Units held of the item and its catalogued variants. */
	private long heldQuantity(GearItem item)
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
	public long cost(Loadout l)
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
	public int expensiveItemCount(Loadout loadout)
	{
		int count = 0;
		for (Slot slot : SLOTS)
		{
			count += expensive(loadout.get(slot)) ? 1 : 0;
		}
		return count;
	}

	private boolean expensive(GearItem item)
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

	private boolean withinWildernessRisk(Loadout loadout)
	{
		return !settings.isWildernessRiskLimited()
			|| expensiveItemCount(loadout) <= Math.max(0, settings.getMaxExpensiveItems());
	}

	static boolean classAllowed(Monster m, CombatClass c)
	{
		return EncounterImmunities.classAllowed(m, c);
	}

	@Value
	private static class Option
	{
		AttackStyle style;
		Spell spell;
	}

	@Value
	private static class Seed
	{
		Loadout loadout;
		Predicate<GearItem> filter;
		ToDoubleFunction<GearItem> damage;
		/** A set whose bonus needs every piece: first searched around the held set, then released. */
		boolean set;

		Seed(Loadout loadout, Predicate<GearItem> filter)
		{
			this(loadout, filter, null, false);
		}

		Seed(Loadout loadout, Predicate<GearItem> filter, ToDoubleFunction<GearItem> damage)
		{
			this(loadout, filter, damage, false);
		}

		private Seed(Loadout loadout, Predicate<GearItem> filter, ToDoubleFunction<GearItem> damage, boolean set)
		{
			this.loadout = loadout;
			this.filter = filter;
			this.damage = damage;
			this.set = set;
		}

		static Seed wearing(Loadout set, Predicate<GearItem> filter)
		{
			return new Seed(set, filter, null, true);
		}

		boolean appliesTo(GearItem weapon)
		{
			return filter.test(weapon);
		}
	}

	@Value
	private static class Scored
	{
		GearItem item;
		double score;
	}

	/** Spec weapons combined in one raid rotation at most: a draining opener and one damage spec. */
	private static final int RAID_SPEC_WEAPONS = 2;
	/** Damage specs considered for a raid rotation: those gaining the most damage per energy. */
	private static final int RAID_DAMAGE_CHOICES = 4;
	/** Draining openers considered for a raid rotation: those that alone finish the kill soonest. */
	private static final int RAID_DRAIN_CHOICES = 2;

	/** One spec weapon held for a raid rotation: its strongest style, items and one spec at the opening stats. */
	@Value
	private static class SpecChoice
	{
		GearItem weapon;
		Option option;
		Loadout held;
		DpsResult opening;
		SpecialAttack spec;
		GearItem offhand;
		GearItem ammo;
		long extra;
		int unpriced;
		/** Ordinary and spec attacks once its drains land; null for a damage spec. */
		SpecialRotation.Fight fight;

		/** Damage gained over the ordinary attacks it replaces, per energy. */
		double gainPerEnergy(double rate)
		{
			double seconds = opening.getExpectedSpeedTicks() * 0.6;
			return (opening.getPrimaryDps() - rate) * seconds / Math.max(1, spec.getEnergy());
		}
	}

	/** A raid rotation and the spec weapons it uses. */
	@Value
	private static class RaidRotation
	{
		SpecialRotation.Rotation rotation;
		/** The draining opener, or null. */
		SpecChoice opener;
		/** Opening specs of the draining opener. */
		int openerSpecs;
		/** Expected specs of the draining opener used per kill, misses included. */
		double openerUsed;
		List<SpecChoice> damage;
		/** Energy spent from the bar at the start of the kill. */
		double openingEnergy;

		/** GP for the weapons the rotation actually uses. */
		long extra()
		{
			long total = opener == null ? 0 : opener.getExtra();
			for (int j = 0; j < damage.size(); j++) { if (rotation.getSpecs()[j] > 0) { total += damage.get(j).getExtra(); } }
			return total;
		}

		double totalSpecs()
		{
			double total = opener == null ? 0 : openerUsed;
			for (double specs : rotation.getSpecs()) { total += specs; }
			return total;
		}

		/** The rotation as spec plans, the opener (or the most-used damage spec) first with the rotation's rates; its alternative on regenerated energy alone when given. */
		SpecialPlan plan(DpsResult ordinary, DpsResult regenerated, double regeneratedSpecs, SpecialPlan regeneratedPlan)
		{
			List<SpecialPlan> plans = new ArrayList<>();
			if (opener != null)
			{
				int damageDealt = (int) Math.round(opener.getOpening().getAccuracy() > 0
					? opener.getOpening().getPrimaryDps() * opener.getOpening().getExpectedSpeedTicks() * 0.6
					/ opener.getOpening().getAccuracy() : 0);
				DpsResult drained = opener.getFight().ordinary(SpecialAttacks.NONE.landed(opener.getSpec().getDrain(), damageDealt));
				plans.add(single(opener, ordinary, drained, openerUsed));
			}
			int most = -1;
			for (int j = 0; j < damage.size(); j++)
			{
				if (rotation.getSpecs()[j] <= 0) { continue; }
				if (most < 0 || rotation.getSpecs()[j] > rotation.getSpecs()[most]) { most = j; }
			}
			List<SpecialPlan> used = new ArrayList<>();
			for (int j = 0; j < damage.size(); j++)
			{
				if (rotation.getSpecs()[j] > 0)
				{
					SpecialPlan one = single(damage.get(j), ordinary, null, rotation.getSpecs()[j]);
					if (opener == null && j == most) { plans.add(0, one); } else { used.add(one); }
				}
			}
			plans.addAll(used);
			SpecialPlan first = plans.get(0);
			return new SpecialPlan(first.getWeapon(), first.getOffhand(), first.getAmmo(), first.getStyle(), first.getSpell(),
				first.getLoadedAmmo(), first.getSpecial(), first.getSpec(), ordinary, first.getDrained(), first.getSpecsPerKill(),
				first.getExtraCost(), first.getUnpricedItems(), regenerated, regeneratedSpecs, plans.subList(1, plans.size()),
				openingEnergy, regeneratedPlan);
		}

		private static SpecialPlan single(SpecChoice choice, DpsResult ordinary, DpsResult drained, double specs)
		{
			return new SpecialPlan(choice.getWeapon(), choice.getOffhand(), choice.getAmmo(), choice.getOption().getStyle(),
				choice.getOption().getSpell(), choice.getHeld().getLoadedAmmo(), choice.getSpec(), choice.getOpening(), ordinary,
				drained, specs, choice.getExtra(), choice.getUnpriced(), null, 0, Collections.emptyList(), -1, null);
		}
	}

	/** A spec loadout, one special attack with it, and the GP it adds to the worn setup. */
	@Value
	private static class Held
	{
		Loadout loadout;
		DpsResult result;
		long cost;
	}

	/** A spec loadout's off-hand and ammunition beyond the worn setup (null when kept), and its unpriced purchases. */
	@Value
	private static class SpecItems
	{
		GearItem offhand;
		GearItem ammo;
		int unpriced;
	}

	@Value
	private static class Specced
	{
		SpecialPlan plan;
		/** The worn setup's whole-kill rates with the specs mixed in. */
		DpsResult result;
		/** The worn setup the specs are mixed into (with lightbearer, when it pays). */
		Loadout worn;
	}

	@Value
	private static class Found
	{
		Loadout loadout;
		double score;
		long cost;

		/** A higher score, or the same score for less GP. */
		boolean beats(Found other)
		{
			return score > other.score + EPS || Math.abs(score - other.score) <= EPS && cost < other.cost;
		}
	}

	/**
	 * A loadout's exact contents by identity, for the score memo. GearItem's Lombok equals/hashCode compare every
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
		void fill(Loadout l)
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
