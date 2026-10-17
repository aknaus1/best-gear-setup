package com.bestgearsetup.calc;

import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Spell;

/** Required primary attack for a raid duty, enforced even when a saved style or weapon lock conflicts. */
public enum RaidLoadout
{
	ANY, ICE_BARRAGE, CHINCHOMPAS;

	/** Whether the duty can attack with this weapon: chinchompas for {@link #CHINCHOMPAS}, any otherwise. */
	public boolean allowsWeapon(GearItem weapon)
	{
		return this != CHINCHOMPAS || weapon != null && weapon.getSubcategory().equals("chinchompa");
	}

	/** Whether the duty can cast this spell (null for none): only Ice Barrage for {@link #ICE_BARRAGE}. */
	public boolean allowsSpell(Spell spell)
	{
		return this != ICE_BARRAGE || spell != null && spell.getLowerName().equals("ice barrage");
	}

	/** Whether the setup's weapon and spell both fulfil the duty. */
	public boolean allows(Loadout loadout)
	{
		return allowsWeapon(loadout.getWeapon()) && allowsSpell(loadout.getSpell());
	}
}
