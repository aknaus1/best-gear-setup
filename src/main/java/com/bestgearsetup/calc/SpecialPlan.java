package com.bestgearsetup.calc;

import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Spell;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/** The special attack a setup uses during the kill: its weapon, the spec itself and how often it is used. */
@Value
public class SpecialPlan
{
	/** The spec weapon; the setup's own weapon when no switch is needed. */
	GearItem weapon;
	/** An off-hand switched in with a one-handed spec weapon when the worn shield slot is empty; else null. */
	GearItem offhand;
	/** Ammunition equipped with an ammo-slot spec weapon when it differs from the worn ammunition; else null. */
	GearItem ammo;
	AttackStyle style;
	Spell spell;
	/** Darts loaded into a blowpipe spec weapon. */
	GearItem loadedAmmo;
	SpecialAttack special;
	/** One special attack at the start of the kill: its max hit, accuracy, average and interval. */
	DpsResult spec;
	/** The setup's ordinary attacks alone. */
	DpsResult ordinary;
	/** Ordinary attacks after one landed draining spec of average damage; null for specs that don't drain. */
	DpsResult drained;
	/** Expected specs per kill of the selected target HP. */
	double specsPerKill;
	/** GP to buy the spec weapon (with its off-hand, ammunition and darts) on top of the worn setup. */
	long extraCost;
	/** Items to buy for the spec that have no current price. */
	int unpricedItems;
	/**
	 * When this plan opens the kill with a full bar, the setup's whole-kill rates with the same spec on energy
	 * regenerated during the fight alone (the ordinary attacks when that never pays); null when the plan doesn't
	 * open on a full bar. A raid's bar only refills as it regenerates ({@code RaidEncounter#energyAfter}).
	 */
	DpsResult regenerated;
	/** Expected specs per kill on regenerated energy alone, with {@link #regenerated}. */
	double regeneratedSpecsPerKill;
	/**
	 * The other spec weapons used in the same kill (a defence reducer and one damage weapon),
	 * each with its own items and specs per kill; empty for one weapon. This plan's rates are the whole rotation's.
	 */
	List<SpecialPlan> partners;
	/** Energy the rotation spends at the start of the kill; negative for every whole spec a full bar holds. */
	double openingEnergy;
	/** The whole rotation on regenerated energy alone, when it uses other weapons than this one; else null. */
	SpecialPlan regeneratedPlan;

	public SpecialPlan(GearItem weapon, GearItem offhand, GearItem ammo, AttackStyle style, Spell spell, GearItem loadedAmmo,
		SpecialAttack special, DpsResult spec, DpsResult ordinary, DpsResult drained, double specsPerKill, long extraCost,
		int unpricedItems)
	{
		this(weapon, offhand, ammo, style, spell, loadedAmmo, special, spec, ordinary, drained, specsPerKill, extraCost,
			unpricedItems, null, 0);
	}

	/** A plan with its rates on regenerated energy alone (null when it doesn't open on a full bar). */
	public SpecialPlan(GearItem weapon, GearItem offhand, GearItem ammo, AttackStyle style, Spell spell, GearItem loadedAmmo,
		SpecialAttack special, DpsResult spec, DpsResult ordinary, DpsResult drained, double specsPerKill, long extraCost,
		int unpricedItems, DpsResult regenerated, double regeneratedSpecsPerKill)
	{
		this(weapon, offhand, ammo, style, spell, loadedAmmo, special, spec, ordinary, drained, specsPerKill, extraCost,
			unpricedItems, regenerated, regeneratedSpecsPerKill, Collections.emptyList(), -1, null);
	}

	/** A plan with every field, including a rotation's partner weapon. */
	public SpecialPlan(GearItem weapon, GearItem offhand, GearItem ammo, AttackStyle style, Spell spell, GearItem loadedAmmo,
		SpecialAttack special, DpsResult spec, DpsResult ordinary, DpsResult drained, double specsPerKill, long extraCost,
		int unpricedItems, DpsResult regenerated, double regeneratedSpecsPerKill, List<SpecialPlan> partners,
		double openingEnergy, SpecialPlan regeneratedPlan)
	{
		this.weapon = weapon;
		this.offhand = offhand;
		this.ammo = ammo;
		this.style = style;
		this.spell = spell;
		this.loadedAmmo = loadedAmmo;
		this.special = special;
		this.spec = spec;
		this.ordinary = ordinary;
		this.drained = drained;
		this.specsPerKill = specsPerKill;
		this.extraCost = extraCost;
		this.unpricedItems = unpricedItems;
		this.regenerated = regenerated;
		this.regeneratedSpecsPerKill = regeneratedSpecsPerKill;
		this.partners = Collections.unmodifiableList(new ArrayList<>(partners));
		this.openingEnergy = openingEnergy;
		this.regeneratedPlan = regeneratedPlan;
	}

	/** Whether the spec needs a weapon, off-hand or ammunition switch from the worn setup. */
	public boolean isSwitch(Loadout worn)
	{
		return weapon != worn.getWeapon() || offhand != null || ammo != null;
	}

	/** This plan's weapon then its partners': every spec weapon the kill uses. */
	public List<SpecialPlan> getAll()
	{
		List<SpecialPlan> all = new ArrayList<>();
		all.add(this);
		all.addAll(partners);
		return all;
	}

	/** GP to buy every spec weapon of the rotation with its items. */
	public long getTotalExtraCost()
	{
		return getAll().stream().mapToLong(SpecialPlan::getExtraCost).sum();
	}

	/** Unpriced items of every spec weapon of the rotation. */
	public int getTotalUnpricedItems()
	{
		return getAll().stream().mapToInt(SpecialPlan::getUnpricedItems).sum();
	}

	/** Energy the opening specs spend: the rotation's, or every whole spec a full bar holds (the whole bar if unknown). */
	public double getOpeningEnergy()
	{
		if (openingEnergy >= 0)
		{
			return openingEnergy;
		}
		int cost = special == null ? 0 : special.getEnergy();
		return cost <= 0 ? SpecialRotation.FULL_ENERGY : SpecialRotation.FULL_ENERGY / cost * cost;
	}

	/** Whether this plan opens the kill with a full bar that it could do without. */
	public boolean isFullBar()
	{
		return regenerated != null;
	}

	/** The same rotation on regenerated energy alone; this plan when it doesn't open on a full bar. */
	public SpecialPlan withRegeneratedEnergy()
	{
		if (regenerated == null)
		{
			return this;
		}
		return regeneratedPlan != null ? regeneratedPlan : new SpecialPlan(weapon, offhand, ammo, style, spell, loadedAmmo,
			special, spec, ordinary, drained, regeneratedSpecsPerKill, extraCost, unpricedItems);
	}
}
