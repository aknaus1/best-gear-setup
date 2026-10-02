package com.bestgearsetup.calc;

import com.bestgearsetup.Budget;
import com.bestgearsetup.DiaryRewards;
import com.bestgearsetup.ItemCosts;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import lombok.Value;

/**
 * Searches for the best setup per combat class under the owned-items / budget rules and the
 * user's constraints (styles, experience, locks, exclusions...).
 *
 * <p>For every candidate weapon it runs a coordinate ascent: starting from a seed loadout it
 * repeatedly tries every candidate in each slot (and every style / spell / ammo option) and keeps
 * any change that raises the score, or keeps the score while lowering cost, until nothing improves.
 * Set bonuses that a greedy per-slot search can never discover (void, obsidian, inquisitor, crystal)
 * are handled by also starting from seeds that already wear the set.
 *
 * <p>Per-slot candidates are pruned to the price/stat Pareto front for the relevant bonuses, plus
 * items with special effects. Once the best setups are chosen, slots that add nothing can be
 * filled with prayer or defence items (optionally trading a small DPS margin).
 */
public class Optimizer
{
	private static final double EPS = 1e-9;
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
	private final ToLongFunction<GearItem> priceFn;
	private final PlayerLevels levels;
	private final Monster monster;
	private final int maxPasses;
	private final int maxWeapons;

	/**
	 * @param owned   whether the player owns an item id (bank, inventory, worn or marked owned)
	 * @param priceFn acquisition cost of an item, including tradable components of untradeables
	 */
	public Optimizer(GameData data, CombatContext ctx, OptimizerSettings settings, IntPredicate owned,
		ToLongFunction<GearItem> priceFn)
	{
		this.data = data;
		this.ctx = ctx.withFightOptions(ctx.getAoeTargets(), settings.getTargetDistance());
		this.settings = settings;
		this.owned = owned;
		this.priceFn = priceFn;
		this.levels = ctx.getLevels();
		this.monster = ctx.getMonster();
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
		if (!classAllowed(monster, cls))
		{
			return Collections.emptyList();
		}
		Map<Slot, List<GearItem>> candidates = candidates(cls);
		List<GearItem> weapons = weapons(cls);
		if (weapons.size() > maxWeapons)
		{
			weapons = preselectWeapons(weapons, cls);
		}
		List<Seed> seeds = seeds(cls);

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
		}
		found.sort(Comparator.comparingDouble((Found f) -> -f.getScore()).thenComparingLong(Found::getCost));

		if (settings.getDepth() == SearchDepth.BEST && !found.isEmpty())
		{
			// Second sweep: start every strong weapon from the best armour found for any weapon.
			Loadout bestArmour = found.get(0).getLoadout().copy();
			List<Seed> extra = new ArrayList<>(seeds);
			extra.add(new Seed(bestArmour, w -> true));
			List<Found> refined = new ArrayList<>();
			for (Found f : found.subList(0, Math.min(15, found.size())))
			{
				if (cancelled.getAsBoolean())
				{
					return Collections.emptyList();
				}
				Found r = optimizeWeapon(f.getLoadout().getWeapon(), cls, candidates, extra);
				refined.add(r != null && r.getScore() > f.getScore() + EPS ? r : f);
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
			if (dps.getDps() > 0)
			{
				results.add(new SetupResult(cls, l, dps, cost(l), unpricedItems(l), filled));
			}
		}
		// Filling can lower different setups by different amounts; rank the equipment actually returned.
		results.sort(Comparator.comparingDouble((SetupResult r) -> -metric(r.getDps()))
			.thenComparingLong(SetupResult::getBuyCost));
		return new ArrayList<>(results.subList(0, Math.min(Math.max(0, settings.getResultsPerClass()), results.size())));
	}

	// ------------------------------------------------------------ search

	private Found optimizeWeapon(GearItem weapon, CombatClass cls, Map<Slot, List<GearItem>> candidates,
		List<Seed> seeds)
	{
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
			// Start from the strongest ammo that fits the budget (ammo can be priced per 1000 arrows).
			boolean fits = false;
			for (GearItem a : firesAmmo || loadsAmmo ? ammo : Collections.<GearItem>singletonList(null))
			{
				l.set(Slot.AMMO, firesAmmo ? a : l.get(Slot.AMMO));
				l.setLoadedAmmo(loadsAmmo ? a : null);
				if (withinBudget(cost(l)))
				{
					fits = true;
					break;
				}
			}
			if (!fits)
			{
				continue;
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

	private void ascend(Loadout l, GearItem weapon, List<Option> options, Map<Slot, List<GearItem>> candidates,
		List<GearItem> ammo, boolean firesAmmo, boolean loadsAmmo)
	{
		double cur = score(l);
		long curCost = cost(l);
		for (int pass = 0; pass < maxPasses; pass++)
		{
			boolean improved = false;

			// Attack style / spell
			AttackStyle origStyle = l.getStyle();
			Spell origSpell = l.getSpell();
			AttackStyle bestStyle = origStyle;
			Spell bestSpell = origSpell;
			for (Option o : options)
			{
				l.setStyle(o.getStyle());
				l.setSpell(o.getSpell());
				double d = score(l);
				if (d > cur + EPS)
				{
					cur = d;
					bestStyle = o.getStyle();
					bestSpell = o.getSpell();
				}
			}
			l.setStyle(bestStyle);
			l.setSpell(bestSpell);
			improved |= !bestStyle.equals(origStyle) || bestSpell != origSpell;

			// Darts in a blowpipe
			if (loadsAmmo)
			{
				GearItem orig = l.getLoadedAmmo();
				GearItem bestAmmo = orig;
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
					double d = score(l);
					if (d > bestD + EPS || (Math.abs(d - bestD) <= EPS && c < bestC))
					{
						bestAmmo = a;
						bestD = d;
						bestC = c;
					}
				}
				l.setLoadedAmmo(bestAmmo);
				if (bestAmmo != orig)
				{
					improved = true;
					cur = bestD;
					curCost = bestC;
				}
			}

			for (Slot slot : Slot.values())
			{
				if (slot == Slot.WEAPON || (slot == Slot.SHIELD && weapon.isTwoHanded()))
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
				GearItem bestItem = orig;
				double bestD = cur;
				long bestC = curCost;
				long base = curCost - itemCost(slot, orig);
				for (int i = -1; i < list.size(); i++)
				{
					GearItem cand = i < 0 ? null : list.get(i);
					if (cand == orig || (cand == null && slot == Slot.AMMO))
					{
						continue;
					}
					long c = base + itemCost(slot, cand);
					if (!withinBudget(c))
					{
						continue;
					}
					l.set(slot, cand);
					double d = score(l);
					if (d > bestD + EPS || (Math.abs(d - bestD) <= EPS && c < bestC))
					{
						bestItem = cand;
						bestD = d;
						bestC = c;
					}
				}
				l.set(slot, bestItem);
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
		return metric(calculate(l));
	}

	private DpsResult calculate(Loadout l)
	{
		return DragonfireProtection.allowed(monster, l, settings, levels) ? DpsCalculator.calculate(l, ctx) : DpsResult.ZERO;
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
		for (Slot slot : Slot.values())
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
			long baseCost = curCost - itemCost(slot, orig);
			for (GearItem cand : data.getItems(slot))
			{
				if (cand == orig || !usable(cand))
				{
					continue;
				}
				double m = metric.applyAsDouble(cand);
				long c = baseCost + itemCost(slot, cand);
				if (m < bestMetric - EPS || (Math.abs(m - bestMetric) <= EPS && c >= bestCost) || !withinBudget(c))
				{
					continue;
				}
				l.set(slot, cand);
				DpsResult r = DpsCalculator.calculate(l, ctx);
				boolean acceptable = forced || (r.getDps() >= minDps
					&& (settings.getCalcMode() == CalcMode.DPS || primary(r) >= basePrimary - EPS));
				if (acceptable && DragonfireProtection.allowed(monster, l, settings, levels))
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

	private Map<Slot, List<GearItem>> candidates(CombatClass cls)
	{
		List<ToDoubleFunction<GearItem>> metrics = metrics(cls);
		Map<Slot, List<GearItem>> out = new EnumMap<>(Slot.class);
		for (Slot slot : Slot.values())
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
			Set<GearItem> keep = new LinkedHashSet<>();
			for (ToDoubleFunction<GearItem> metric : metrics)
			{
				keep.addAll(paretoFront(pool, metric));
			}
			for (GearItem item : pool)
			{
				if (isSpecial(item) || DragonfireProtection.isProtectiveShield(item))
				{
					keep.add(item);
				}
			}
			out.put(slot, new ArrayList<>(keep));
		}
		return out;
	}

	private List<ToDoubleFunction<GearItem>> metrics(CombatClass cls)
	{
		List<ToDoubleFunction<GearItem>> m = new ArrayList<>();
		switch (cls)
		{
			case MELEE:
				m.add(GearItem::getStabBonus);
				m.add(GearItem::getSlashBonus);
				m.add(GearItem::getCrushBonus);
				m.add(GearItem::getMeleeStr);
				m.add(i -> i.getStabBonus() + i.getMeleeStr());
				m.add(i -> i.getSlashBonus() + i.getMeleeStr());
				m.add(i -> i.getCrushBonus() + i.getMeleeStr());
				m.add(i -> i.getStabDef() + i.getSlashDef() + i.getCrushDef() + i.getRangedDef());
				break;
			case RANGED:
				m.add(GearItem::getRangedBonus);
				m.add(GearItem::getRangedStr);
				m.add(i -> i.getRangedBonus() + 2 * i.getRangedStr());
				if (settings.getStyles().contains(AttackStyle.Type.ATLATL) || strengthRangedWeaponUsable())
				{
					// The atlatl and Hunter's spear hit with melee strength but aim with ranged attack.
					m.add(GearItem::getMeleeStr);
					m.add(i -> i.getRangedBonus() + 2 * i.getMeleeStr());
				}
				break;
			default:
				m.add(GearItem::getMagicBonus);
				m.add(GearItem::getMagicStr);
				m.add(i -> i.getMagicBonus() + 3 * i.getMagicStr());
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

	/** Items not dominated by a cheaper item on this metric. */
	private List<GearItem> paretoFront(List<GearItem> pool, ToDoubleFunction<GearItem> metric)
	{
		List<GearItem> sorted = new ArrayList<>();
		for (GearItem i : pool)
		{
			if (metric.applyAsDouble(i) > 0)
			{
				sorted.add(i);
			}
		}
		sorted.sort(Comparator.comparingLong((GearItem i) -> itemCost(i.getSlot(), i))
			.thenComparingDouble(i -> -metric.applyAsDouble(i)));
		List<GearItem> front = new ArrayList<>();
		double best = 0;
		for (GearItem i : sorted)
		{
			double v = metric.applyAsDouble(i);
			if (v > best + EPS)
			{
				front.add(i);
				best = v;
			}
		}
		return front;
	}

	private static boolean isSpecial(GearItem item)
	{
		String n = item.getName().toLowerCase(Locale.ROOT);
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
		// Bare-weapon ranking cannot measure a full set, spell interaction or defence-derived strength.
		for (Scored s : scored)
		{
			String n = s.getItem().getName().toLowerCase(Locale.ROOT);
			if ((n.startsWith("dharok's") || n.startsWith("verac's") || n.startsWith("ahrim's")
				|| n.startsWith("karil's") || n.equals("dual macuahuitl") || n.equals("eclipse atlatl")
				|| n.contains("bulwark") || n.startsWith("twinflame staff")) && !out.contains(s.getItem()))
			{
				out.add(s.getItem());
			}
		}
		return out;
	}

	private List<Option> options(GearItem weapon, CombatClass cls)
	{
		List<Option> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		List<Spell> spells = null;
		for (String raw : weapon.getStyles())
		{
			AttackStyle s = AttackStyle.parse(raw);
			if (s == null || WeaponRules.classOf(s) != cls || !seen.add(s.getType() + "/" + s.getStance())
				|| !styleAllowed(weapon, s))
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

	/** Reach, attack type, experience filter and melee XP preference. */
	boolean styleAllowed(GearItem weapon, AttackStyle s)
	{
		if (!AttackReach.canReach(monster, weapon, s, settings.getTargetDistance())
			|| !settings.getStyles().contains(WeaponRules.tabType(weapon, s)))
		{
			return false;
		}
		String stance = s.getStance();
		boolean melee = s.isMelee();
		boolean controlled = melee && stance.equals("controlled");
		boolean grantsAttack = melee && (stance.equals("accurate") || controlled);
		boolean grantsStrength = melee && (stance.equals("aggressive") || controlled);
		boolean grantsDefence = (melee && (stance.equals("defensive") || controlled)) || stance.equals("longrange");
		if (!settings.isAttackXp() && grantsAttack || !settings.isStrengthXp() && grantsStrength
			|| !settings.isDefenceXp() && grantsDefence)
		{
			return false;
		}
		if (!melee)
		{
			return true;
		}
		switch (settings.getXpPreference())
		{
			case ATTACK:
				return grantsAttack;
			case STRENGTH:
				return grantsStrength;
			case DEFENCE:
				return grantsDefence;
			case CONTROLLED:
				return controlled;
			default:
				return true;
		}
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
				seeds.add(new Seed(set(voidHelm, gloves, eliteTop, eliteRobe), w -> true));
			}
			if (top != null && robe != null)
			{
				seeds.add(new Seed(set(voidHelm, gloves, top, robe), w -> true));
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
				seeds.add(new Seed(set(crystalHead, crystalBody, crystalLegs, blessing), w -> true));
			}
			GearItem oHelm = find(Slot.HEAD, "obsidian helmet");
			GearItem oBody = find(Slot.BODY, "obsidian platebody");
			GearItem oLegs = find(Slot.LEGS, "obsidian platelegs");
			if (oHelm != null && oBody != null && oLegs != null)
			{
				seeds.add(new Seed(set(oHelm, oBody, oLegs), w ->
				{
					String n = w.getName().toLowerCase(Locale.ROOT);
					return n.startsWith("toktz-") || n.startsWith("tzhaar-ket-");
				}));
			}
			GearItem iHelm = find(Slot.HEAD, "inquisitor's great helm");
			GearItem iBody = find(Slot.BODY, "inquisitor's hauberk");
			GearItem iLegs = find(Slot.LEGS, "inquisitor's plateskirt");
			if (iHelm != null && iBody != null && iLegs != null)
			{
				seeds.add(new Seed(set(iHelm, iBody, iLegs), w -> w.getStyles().stream().anyMatch(s -> s.contains(",crush,"))));
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
				seeds.add(new Seed(set(cHelm, cBody, cLegs), w ->
				{
					String n = w.getName().toLowerCase(Locale.ROOT);
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
			seeds.add(new Seed(seed, w -> EquipmentEffects.namedWeapon(w, weapon)));
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

	/** Owning any catalogued variant (ornament, charges, recolour) counts as owning the item. */
	private boolean isOwned(GearItem item)
	{
		if (owned.test(item.getId()))
		{
			return true;
		}
		for (int variant : item.getVariants())
		{
			if (owned.test(variant))
			{
				return true;
			}
		}
		return false;
	}

	boolean available(GearItem item)
	{
		if (isOwned(item))
		{
			return true;
		}
		if (DiaryRewards.isReward(item.getId()))
		{
			return false;
		}
		switch (settings.getMode())
		{
			case OWNED_ONLY:
				return false;
			case BUDGET:
				return (item.isTradeable() || settings.isAllowUntradeables()) && withinBudget(item);
			default:
				return item.isTradeable() || settings.isAllowUntradeables();
		}
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
		boolean untradeable = !item.isTradeable() && !settings.isAllowUntradeables();
		switch (settings.getMode())
		{
			case OWNED_ONLY:
				return "you don't own it and the search uses owned items only";
			case BUDGET:
				if (untradeable)
				{
					return "it's untradeable and you don't own it";
				}
				long price = price(item);
				return !ItemCosts.isKnown(price) ? "it has no current Grand Exchange price"
					: "it costs " + Budget.format(price) + ", over your " + Budget.format(settings.getBudget()) + " budget";
			default:
				return "it's untradeable and you don't own it";
		}
	}

	/** Why a locked weapon gives no setup at all in this search, in plain words, or null if it can be searched. */
	public String weaponLockReason(GearItem weapon)
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

	/** Acquisition price, or {@link ItemCosts#UNKNOWN} if a tradable component has no current quote. */
	private long price(GearItem item)
	{
		long price = priceFn.applyAsLong(item);
		return ItemCosts.isKnown(price) ? price : ItemCosts.UNKNOWN;
	}

	/** An unpriced purchase cannot be shown to fit a budget; uncounted ammunition needs no price. */
	private boolean withinBudget(GearItem item)
	{
		long price = price(item);
		if (!ItemCosts.isKnown(price))
		{
			return settings.getAmmoCount() <= 0 && WeaponRules.isAmmunition(item);
		}
		return price <= settings.getBudget();
	}

	/** @param slot the equipment slot, or null for darts loaded in a blowpipe */
	private boolean unpriced(Slot slot, GearItem item)
	{
		if (item == null || isOwned(item) || ItemCosts.isKnown(price(item)))
		{
			return false;
		}
		boolean uncountedAmmo = (slot == null || slot == Slot.AMMO) && WeaponRules.isAmmunition(item)
			&& settings.getAmmoCount() <= 0;
		return !uncountedAmmo;
	}

	/** Items in the setup that must be bought but have no current price, so its cost is incomplete. */
	public int unpricedItems(Loadout l)
	{
		int count = 0;
		for (Slot s : Slot.values())
		{
			count += unpriced(s, l.get(s)) ? 1 : 0;
		}
		return count + (unpriced(null, l.getLoadedAmmo()) ? 1 : 0);
	}

	/**
	 * GP to acquire the item. Consumable ammunition costs
	 * price x configured ammo count; blessings and similar are bought once.
	 */
	private long itemCost(Slot slot, GearItem item)
	{
		if (slot == Slot.AMMO && WeaponRules.isAmmunition(item))
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

	private long ammoCost(GearItem item)
	{
		return plainCost(item) * Math.max(0, settings.getAmmoCount());
	}

	/** Total GP for everything in the setup the player does not own. */
	public long cost(Loadout l)
	{
		long total = 0;
		for (Slot s : Slot.values())
		{
			total += itemCost(s, l.get(s));
		}
		total += ammoCost(l.getLoadedAmmo());
		return total;
	}

	private boolean withinBudget(long cost)
	{
		return settings.getMode() != SearchMode.BUDGET || cost <= settings.getBudget();
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

	@Value
	private static class Found
	{
		Loadout loadout;
		double score;
		long cost;
	}
}
