package com.bestgearsetup.calc;

import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Spell;
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

	/** Whether the spec needs a weapon, off-hand or ammunition switch from the worn setup. */
	public boolean isSwitch(Loadout worn)
	{
		return weapon != worn.getWeapon() || offhand != null || ammo != null;
	}
}
