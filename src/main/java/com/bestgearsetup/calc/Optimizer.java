package com.bestgearsetup.calc;

import com.bestgearsetup.Budget;
import com.bestgearsetup.DiaryRewards;
import com.bestgearsetup.ItemCosts;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
		this.ctx = ctx.withFightOptions(ctx.getAoeTargets(), settings.getTargetDistance());
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
	 * decreasing) on the calling thread. Each weapon swept, refined or finalised counts as one step.
	 */
	public List<SetupResult> optimize(CombatClass cls, BooleanSupplier cancelled, DoubleConsumer progress)
	{
		if (!classAllowed(monster, cls))
		{
			progress.accept(1);
			return Collections.emptyList();
		}
		Map<Slot, List<GearItem>> candidates = candidates(cls);
		specShields = candidates.getOrDefault(Slot.SHIELD, Collections.emptyList());
		List<GearItem> weapons = weapons(cls);
		if (weapons.size() > maxWeapons)
		{
			weapons = preselectWeapons(weapons, cls);
		}
		List<Seed> seeds = seeds(cls);
		boolean secondSweep = settings.getDepth() == SearchDepth.BEST || settings.isWildernessRiskLimited();
		// Until the first sweep ends, assume every weapon survives it; the total only shrinks afterwards.
		Steps steps = new Steps(progress, weapons.size() * 2 + (secondSweep ? Math.min(15, weapons.size()) : 0));

		List<Found> found = new ArrayList<>();
		for (GearItem weapon : weapons)
		{
			if (cancelled.getAsBoolean())
			{
				return Collections.emptyList();
			}
			Found f = optimizeWeapon(weapon, cls, candidates, seeds);
			if (f != null)
			{
				found.add(f);
			}
			steps.step();
		}
		found.sort(Comparator.comparingDouble((Found f) -> -f.getScore()).thenComparingLong(Found::getCost));
		steps.total(weapons.size() + found.size() + (secondSweep ? Math.min(15, found.size()) : 0));

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
				Found r = optimizeWeapon(f.getLoadout().getWeapon(), cls, candidates, extra);
				if (r == null || !r.beats(f))
				{
					r = f;
				}
				// Below Best depth, pairs only move the Wilderness expensive-item allowance between slots.
				if (refined.size() < PAIR_REFINED_WEAPONS)
				{
					r = refinePairs(r, cls, candidates, settings.getDepth() != SearchDepth.BEST, cancelled);
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
				Specced specced = special(l, cls, dps, cost);
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
				results.add(new SetupResult(cls, l, specced == null ? dps : specced.getResult(),
					cost + (plan == null ? 0 : plan.getExtraCost()),
					unpricedItems(l) + (plan == null ? 0 : plan.getUnpricedItems()), filled,
					SupportSpell.assumed(l, ctx), plan));
			}
			steps.step();
		}
		// Filling can lower different setups by different amounts; rank the equipment actually returned.
		results.sort(Comparator.comparingDouble((SetupResult r) -> -metric(r.getDps()))
			.thenComparingLong(SetupResult::getBuyCost));
		return new ArrayList<>(results.subList(0, Math.min(Math.max(0, settings.getResultsPerClass()), results.size())));
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
		List<Seed> seeds)
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
		if (settings.isRequireAtlatlAmmoRecovery() && WeaponRules.isAtlatl(weapon))
		{
			// Seed every usable recovery cape, including zero-damage devices that ascent would otherwise drop.
			List<Seed> recoverySeeds = new ArrayList<>();
			for (GearItem cape : atlatlRecoveryCapes())
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
		for (Seed seed : seeds)
		{
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
			if (settings.isRequireAtlatlAmmoRecovery() && WeaponRules.isAtlatl(weapon)
				&& capeLock != null && capeLock.getKind() == SlotLock.Kind.FILL)
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
				ascend(l, weapon, options, candidates, ammo, firesAmmo, loadsAmmo, pieces);
			}
			ascend(l, weapon, options, candidates, ammo, firesAmmo, loadsAmmo);

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
		boolean allowanceOnly, BooleanSupplier cancelled)
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
			if (improvePair(l, pairs, options, allowanceOnly, cancelled))
			{
				ascend(l, weapon, options, candidates, ammo, firesAmmo, loadsAmmo);
				continue;
			}
			Loadout kicked = allowanceOnly ? null
				: kick(l, weapon, options, candidates, pairs, ammo, firesAmmo, loadsAmmo, cancelled);
			if (kicked == null)
			{
				break;
			}
			l = kicked;
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
		BooleanSupplier cancelled)
	{
		double bestScore = score(l);
		long bestCost = cost(l);
		Loadout best = null;
		for (Map.Entry<Slot, List<GearItem>> e : moves.entrySet())
		{
			Slot slot = e.getKey();
			for (GearItem cand : e.getValue())
			{
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
				ascend(trial, weapon, options, candidates, ammo, firesAmmo, loadsAmmo, hold);
				ascend(trial, weapon, options, candidates, ammo, firesAmmo, loadsAmmo);
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
		boolean allowanceOnly, BooleanSupplier cancelled)
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
		for (int a = 0; a < slots.size(); a++)
		{
			Slot left = slots.get(a);
			GearItem originalLeft = l.get(left);
			for (int b = a + 1; b < slots.size(); b++)
			{
				Slot right = slots.get(b);
				GearItem originalRight = l.get(right);
				long base = originalCost - itemCost(originalLeft) - itemCost(originalRight);
				for (GearItem first : candidates.get(left))
				{
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
					for (GearItem second : candidates.get(right))
					{
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
						// Pairs are almost never repeated, so they skip the memo.
						l.setStyle(originalStyle);
						l.setSpell(originalSpell);
						double s = scoreBestOption(l, options, false);
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
		climb(l, weapon, options, candidates, ammo, firesAmmo, loadsAmmo, held, false);
		climb(l, weapon, options, candidates, ammo, firesAmmo, loadsAmmo, held, options.size() > 1);
	}

	/** @param joint score each trial with its best option rather than the current one */
	private void climb(Loadout l, GearItem weapon, List<Option> options, Map<Slot, List<GearItem>> candidates,
		List<GearItem> ammo, boolean firesAmmo, boolean loadsAmmo, Set<Slot> held, boolean joint)
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

			for (Slot slot : SLOTS)
			{
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
					l.setStyle(keptStyle);
					l.setSpell(keptSpell);
					double d = joint ? scoreBestOption(l, options, true) : score(l);
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

	/** Mandatory protection and the optional atlatl cape requirement are enforced throughout the search. */
	private boolean protectedSetup(Loadout l)
	{
		return (!fireProtectionRequired || DragonfireProtection.protects(monster, l, settings, levels))
			&& (!slayerEquipmentRequired || SlayerEquipment.allowed(monster, settings, l))
			&& (!settings.isRequireAtlatlAmmoRecovery() || !WeaponRules.isAtlatl(l.getWeapon())
				|| WeaponRules.isAmmoRecoveryCape(l.get(Slot.CAPE)));
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
	private Specced special(Loadout worn, CombatClass cls, DpsResult ordinary, long wornCost)
	{
		if (!settings.isKillSpecials() || ctx.getModifiers().isSpecialAttack())
		{
			return null;
		}
		double regen = SpecialRotation.REGEN_PER_SECOND * (lightbearer(worn) ? 2 : 1);
		Specced best = special(worn, cls, ordinary, wornCost, regen);
		Loadout lit = withLightbearer(worn);
		if (lit != null)
		{
			DpsResult litOrdinary = calculate(lit);
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

	private static boolean lightbearer(Loadout l)
	{
		GearItem ring = l.get(Slot.RING);
		return ring != null && ring.getId() == ItemID.LIGHTBEARER;
	}

	/** The worn setup with lightbearer in place of its ring, or null when it can't or already does. */
	private Loadout withLightbearer(Loadout worn)
	{
		GearItem ring = data.getItem(Slot.RING, ItemID.LIGHTBEARER);
		if (ring == null || lightbearer(worn) || settings.getLocks().containsKey(Slot.RING) || !usable(ring))
		{
			return null;
		}
		Loadout lit = worn.copy();
		lit.set(Slot.RING, ring);
		return protectedSetup(lit) && withinWildernessRisk(lit) ? lit : null;
	}

	/**
	 * Candidates are the worn weapon and every usable weapon of the same combat class with a damaging spec,
	 * held with the worn armour, plus an off-hand when a one-handed spec weapon leaves the shield slot empty.
	 * They are bought within what the budget has left.
	 */
	private Specced special(Loadout worn, CombatClass cls, DpsResult ordinary, long wornCost, double regen)
	{
		CombatContext specCtx = ctx.withModifiers(ctx.getModifiers().toBuilder().specialAttack(true).build());
		int hp = ctx.getTargetHitpoints();
		// Ordinary attacks after each set of landed drains, shared by every draining candidate.
		Map<SpecialAttacks, DpsResult> drainedOrdinary = new HashMap<>();
		drainedOrdinary.put(SpecialAttacks.NONE, ordinary);
		Specced best = null;
		double bestScore = metric(ordinary);
		for (GearItem weapon : specWeapons(cls))
		{
			Loadout base = specLoadout(worn, weapon);
			if (base == null)
			{
				continue;
			}
			boolean same = weapon == worn.getWeapon();
			long weaponCost = same ? 0 : itemCost(weapon) + ammoCost(base.getLoadedAmmo());
			for (Option o : options(weapon, cls, true))
			{
				base.setStyle(o.getStyle());
				base.setSpell(o.getSpell());
				SpecialAttack spec = SpecialAttack.of(base, specCtx);
				if (spec == null || spec.getEnergy() <= 0)
				{
					continue;
				}
				// The ammunition, then the off-hand, that make this spec hit hardest (they hardly interact, and
				// trying every pair would multiply the candidates).
				Held ammoChoice = strongest(base, Slot.AMMO, specAmmo(worn, weapon), weaponCost, wornCost, specCtx);
				Held choice = ammoChoice == null ? null
					: strongest(ammoChoice.getLoadout(), Slot.SHIELD, offhands(worn, weapon), ammoChoice.getCost(), wornCost,
					specCtx);
				if (choice == null)
				{
					continue;
				}
				Loadout held = choice.getLoadout();
				DpsResult opening = choice.getResult();
				long extra = choice.getCost();
				SpecialRotation.Blend blend;
				DpsResult drained = null;
				if (spec.getDrain() == SpecialAttack.Drain.NONE || settings.getDrainSpecs() == DrainSpecs.DAMAGE_ONLY)
				{
					blend = SpecialRotation.blend(ordinary, opening, spec.getEnergy(), hp, settings.getSpecEnergy(), regen);
				}
				else
				{
					SpecialRotation.Fight fight = drainingFight(worn, held, opening, specCtx, drainedOrdinary);
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
					GearItem offhand = held.get(Slot.SHIELD) == worn.get(Slot.SHIELD) ? null : held.get(Slot.SHIELD);
					GearItem specAmmo = held.get(Slot.AMMO) == worn.get(Slot.AMMO) ? null : held.get(Slot.AMMO);
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
			if (!protectedSetup(l) || !withinBudget(wornCost + c))
			{
				continue;
			}
			DpsResult r = DpsCalculator.calculate(l, specCtx);
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

	/** Usable weapons of the class with a damaging special attack; weapon locks and hand limits apply to the worn weapon only. */
	private List<GearItem> specWeapons(CombatClass cls)
	{
		return specWeapons.computeIfAbsent(cls, c ->
		{
			List<GearItem> out = new ArrayList<>();
			for (GearItem w : data.getItems(Slot.WEAPON))
			{
				if (usable(w) && WeaponRules.supports(w, c) && hasSpecialAttack(w, c))
				{
					out.add(w);
				}
			}
			return out;
		});
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
		String n = item.getLowerCombatName();
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
		return out;
	}

	/** Recovery capes that satisfy availability and the cape lock, before combined setup costs are checked. */
	private List<GearItem> atlatlRecoveryCapes()
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
		List<Spell> out = new ArrayList<>();
		for (Spell spell : data.getSpells())
		{
			String name = spell.getName().toLowerCase(Locale.ROOT);
			String book = spell.getSpellbook() == null ? "" : spell.getSpellbook().toLowerCase(Locale.ROOT);
			if (spell.getMaxHit() <= 0 || spell.getLevel() > ctx.getMagic()
				|| !settings.getSpellbooks().contains(book) || !spell.castableWith(weapon.getId()))
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
		List<GearItem> out = new ArrayList<>();
		for (int id : weapon.getAmmunition())
		{
			GearItem a = data.getItem(Slot.AMMO, id);
			if (a == null || !usable(a))
			{
				continue;
			}
			if (ammoLock != null)
			{
				if (ammoLock.getKind() == SlotLock.Kind.ITEM && ammoLock.getItemId() == id)
				{
					out.add(a);
				}
			}
			else if (usable(a))
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
		return !settings.getExcluded().contains(item.getId())
			&& CombatRules.equipmentAllowed(monster, item)
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
		// Best in slot has no limit: untradeables and every diary tier are assumed obtainable.
		if (settings.getMode() == SearchMode.UNLIMITED || isOwned(item) && ammoShortfall(item) == 0)
		{
			return true;
		}
		if (DiaryRewards.isReward(item.getId()) || settings.getMode() == SearchMode.OWNED_ONLY)
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
			return "you don't own this diary tier";
		}
		if (settings.getMode() == SearchMode.OWNED_ONLY)
		{
			return isOwned(item) ? "you have " + heldQuantity(item) + " of the " + settings.getAmmoCount()
				+ " requested (Ammo quantity) and the search uses owned items only"
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
		if (settings.isRequireAtlatlAmmoRecovery() && WeaponRules.isAtlatl(weapon) && atlatlRecoveryCapes().isEmpty())
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
		if (item == null || ItemCosts.isKnown(price(item)))
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
		if (item == null || isOwned(item))
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

	/** A spec loadout, one special attack with it, and the GP it adds to the worn setup. */
	@Value
	private static class Held
	{
		Loadout loadout;
		DpsResult result;
		long cost;
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
