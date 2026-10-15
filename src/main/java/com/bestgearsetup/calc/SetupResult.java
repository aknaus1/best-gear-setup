package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Slot;
import java.util.Collections;
import java.util.Set;
import lombok.Value;

@Value
public class SetupResult
{
	CombatClass combatClass;
	Loadout loadout;
	DpsResult dps;
	/** GP needed to buy the items in this setup that the player does not own. */
	long buyCost;
	/** Items to buy that have no current price; when positive, {@link #buyCost} is a lower bound. */
	int unpricedItems;
	/** Slots filled for prayer / defence rather than DPS. */
	Set<Slot> filledSlots;
	/** Thrall, Mark of Darkness or Charge spells this setup's DPS assumes are cast. */
	Set<SupportSpell> supportSpells;
	/** Special attack used during the kill and already counted in {@link #dps}; null when none helps. */
	SpecialPlan special;
	/** Full setup this raid quick switch was searched from; null for an independently searched full setup. */
	Loadout switchFrom;

	/** Stab / slash / crush / ranged / magic / atlatl: the result tab this setup belongs to. */
	public AttackStyle.Type getAttackType()
	{
		return loadout.getStyle() == null ? null : WeaponRules.tabType(loadout.getWeapon(), loadout.getStyle());
	}

	/** The same setup with these slots also marked as filled; the DPS result instance is kept. */
	public SetupResult withFilledSlots(Set<Slot> slots)
	{
		Set<Slot> filled = java.util.EnumSet.noneOf(Slot.class);
		filled.addAll(filledSlots);
		filled.addAll(slots);
		return new SetupResult(combatClass, loadout, dps, buyCost, unpricedItems, Collections.unmodifiableSet(filled),
			supportSpells, special, switchFrom);
	}

	/** Whether this setup's spec plan opens the kill with a full bar it could do without. */
	public boolean usesFullBar()
	{
		return special != null && special.isFullBar();
	}

	/**
	 * This setup without its spec plan's full opening bar: the spec on energy regenerated during the fight alone, or
	 * no spec (and no spec weapon to carry or buy) when that never pays. This setup when it doesn't use a full bar.
	 */
	public SetupResult withRegeneratedEnergy()
	{
		if (!usesFullBar())
		{
			return this;
		}
		SpecialPlan regenerated = special.getRegeneratedSpecsPerKill() > 0 ? special.withRegeneratedEnergy() : null;
		return new SetupResult(combatClass, loadout, special.getRegenerated(),
			buyCost - special.getTotalExtraCost() + (regenerated == null ? 0 : regenerated.getTotalExtraCost()),
			unpricedItems - special.getTotalUnpricedItems() + (regenerated == null ? 0 : regenerated.getTotalUnpricedItems()),
			filledSlots, supportSpells, regenerated, switchFrom);
	}

	/** Every spec weapon's plan the kill uses, the main one first; empty without specs. */
	public java.util.List<SpecialPlan> getSpecials()
	{
		return special == null ? Collections.emptyList() : special.getAll();
	}

	public SetupResult(CombatClass combatClass, Loadout loadout, DpsResult dps, long buyCost)
	{
		this(combatClass, loadout, dps, buyCost, 0, Collections.emptySet());
	}

	public SetupResult(CombatClass combatClass, Loadout loadout, DpsResult dps, long buyCost, int unpricedItems,
		Set<Slot> filledSlots)
	{
		this(combatClass, loadout, dps, buyCost, unpricedItems, filledSlots, Collections.emptySet());
	}

	public SetupResult(CombatClass combatClass, Loadout loadout, DpsResult dps, long buyCost, int unpricedItems,
		Set<Slot> filledSlots, Set<SupportSpell> supportSpells)
	{
		this(combatClass, loadout, dps, buyCost, unpricedItems, filledSlots, supportSpells, null);
	}

	public SetupResult(CombatClass combatClass, Loadout loadout, DpsResult dps, long buyCost, int unpricedItems,
		Set<Slot> filledSlots, Set<SupportSpell> supportSpells, SpecialPlan special)
	{
		this(combatClass, loadout, dps, buyCost, unpricedItems, filledSlots, supportSpells, special, null);
	}

	/** A setup with every field, including the full setup a raid quick switch was searched from (null for none). */
	public SetupResult(CombatClass combatClass, Loadout loadout, DpsResult dps, long buyCost, int unpricedItems,
		Set<Slot> filledSlots, Set<SupportSpell> supportSpells, SpecialPlan special, Loadout switchFrom)
	{
		this.combatClass = combatClass;
		this.loadout = loadout;
		this.dps = dps;
		this.buyCost = buyCost;
		this.unpricedItems = unpricedItems;
		this.filledSlots = filledSlots;
		this.supportSpells = supportSpells;
		this.special = special;
		this.switchFrom = switchFrom;
	}
}
