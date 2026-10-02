package com.bestgearsetup.calc;

import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Value;

/** How one slot lock affected a search, in plain words, for the results view. */
@Value
public class LockStatus
{
	Slot slot;
	SlotLock lock;
	/** The locked item; null for empty and fill locks, or an id missing from the bundled data. */
	GearItem item;
	/** Why the lock could not be honoured, or null when it was applied. */
	String problem;
	/** What the lock does to the search, or null when it only fixes the slot. */
	String effect;

	public boolean isBlocking()
	{
		return problem != null;
	}

	/** Check every lock against the search the optimizer was built for. */
	public static List<LockStatus> check(GameData data, Optimizer optimizer, Map<Slot, SlotLock> locks)
	{
		List<LockStatus> out = new ArrayList<>();
		for (Map.Entry<Slot, SlotLock> e : locks.entrySet())
		{
			out.add(check(data, optimizer, e.getKey(), e.getValue()));
		}
		return out;
	}

	private static LockStatus check(GameData data, Optimizer optimizer, Slot slot, SlotLock lock)
	{
		String slotName = slot.getDisplayName().toLowerCase(Locale.ROOT);
		GearItem locked = lock.getKind() == SlotLock.Kind.ITEM ? data.getItem(slot, lock.getItemId()) : null;
		String unprotected = optimizer.protectionLockReason(slot, locked);
		if (unprotected != null)
		{
			return new LockStatus(slot, lock, locked, unprotected, "No setup can be found while this lock is set.");
		}
		switch (lock.getKind())
		{
			case EMPTY:
				return new LockStatus(slot, lock, null, null, slot == Slot.AMMO
					? "Weapons that fire arrows, bolts or other ammunition are excluded." : null);
			case FILL:
				return new LockStatus(slot, lock, null, null, "Always filled with the best defence or prayer item."
					+ (slot == Slot.SHIELD ? " Two-handed weapons are excluded." : ""));
			default:
				break;
		}
		GearItem item = locked;
		String blocked = slot == Slot.WEAPON ? "No setup can be found while this lock is set."
			: slot == Slot.AMMO ? "The ammo slot is left empty, so weapons that fire ammunition are excluded."
			: "The " + slotName + " slot is left empty.";
		if (item == null)
		{
			return new LockStatus(slot, lock, null, "this item is not in the bundled data", blocked);
		}
		String problem = slot == Slot.WEAPON ? optimizer.weaponLockReason(item) : optimizer.unusableReason(item);
		if (problem != null)
		{
			return new LockStatus(slot, lock, item, problem, blocked);
		}
		String name = GameData.titleCase(item.getName());
		switch (slot)
		{
			case WEAPON:
				return new LockStatus(slot, lock, item, null, "Only " + name + " is searched.");
			case SHIELD:
				return new LockStatus(slot, lock, item, null, "Two-handed weapons are excluded.");
			case AMMO:
				return new LockStatus(slot, lock, item, null,
					"Weapons that fire ammunition are searched only if they can fire " + name + ".");
			default:
				return new LockStatus(slot, lock, item, null, null);
		}
	}
}
