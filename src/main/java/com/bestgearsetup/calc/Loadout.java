package com.bestgearsetup.calc;

import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * A candidate setup: one item per slot, the chosen attack style, and optionally a spell
 * (autocast or manual cast) or darts loaded into a blowpipe. Mutable for the optimiser's hot loop; use
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

	/**
	 * Equipment swaps to put this setup on while wearing {@code worn}: slots holding a different item, except a shield
	 * that comes off with a two-handed weapon.
	 */
	public int switchesFrom(Loadout worn)
	{
		return switchSlotsFrom(worn).size();
	}

	/** Slots manually changed from the worn setup, with the same two-handed shield exception as the count. */
	public Set<Slot> switchSlotsFrom(Loadout worn)
	{
		Set<Slot> slots = EnumSet.noneOf(Slot.class);
		for (Slot slot : Slot.values())
		{
			GearItem item = get(slot);
			GearItem old = worn.get(slot);
			if (item == null ? old == null : old != null && item.getId() == old.getId()) { continue; }
			if (slot == Slot.SHIELD && item == null && getWeapon() != null && getWeapon().isTwoHanded()) { continue; }
			slots.add(slot);
		}
		return slots;
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

	/**
	 * Equal items have equal ids, so hashing the ids agrees with {@link #equals}. Hashing the items themselves would
	 * hash every stat and name of each piece, and searches hash loadouts for every candidate they score.
	 */
	@Override
	public int hashCode()
	{
		int hash = 1;
		for (GearItem item : items)
		{
			hash = 31 * hash + (item == null ? 0 : item.getId());
		}
		return hash;
	}
}
