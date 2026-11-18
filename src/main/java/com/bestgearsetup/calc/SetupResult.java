package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Slot;
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
	/** Thrall tier whose runes this setup requires; null when no thrall is assumed. */
	Thrall thrall;

	/** Stab / slash / crush / ranged / magic / atlatl: the result tab this setup belongs to. */
	public AttackStyle.Type getAttackType()
	{
		return loadout.getStyle() == null ? null : WeaponRules.tabType(loadout.getWeapon(), loadout.getStyle());
	}
}
