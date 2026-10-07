package com.bestgearsetup.calc;

import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.util.Arrays;
import lombok.Getter;
import lombok.Setter;

/**
 * A candidate setup: one item per slot, the chosen attack style, and optionally a spell
 * (autocast) or darts loaded into a blowpipe. Mutable for the optimiser's hot loop; use
 * {@link #copy()} before storing.
 */
public class Loadout
{
	private final GearItem[] items = new GearItem[Slot.values().length];

	@Getter
	@Setter
	private AttackStyle style;

	@Getter
	@Setter
	private Spell spell;

	/** Darts loaded into a blowpipe-type weapon (they do not occupy the ammo slot). */
	@Getter
	@Setter
	private GearItem loadedAmmo;

	public GearItem get(Slot slot)
	{
		return items[slot.ordinal()];
	}

	public void set(Slot slot, GearItem item)
	{
		items[slot.ordinal()] = item;
	}

	public GearItem getWeapon()
	{
		return items[Slot.WEAPON.ordinal()];
	}

	public Loadout copy()
	{
		Loadout l = new Loadout();
		System.arraycopy(items, 0, l.items, 0, items.length);
		l.style = style;
		l.spell = spell;
		l.loadedAmmo = loadedAmmo;
		return l;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Loadout))
		{
			return false;
		}
		Loadout other = (Loadout) o;
		return Arrays.equals(items, other.items) && java.util.Objects.equals(style, other.style)
			&& spell == other.spell && loadedAmmo == other.loadedAmmo;
	}

	@Override
	public int hashCode()
	{
		return Arrays.hashCode(items);
	}
}
