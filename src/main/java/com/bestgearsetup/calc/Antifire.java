package com.bestgearsetup.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

/** Antifire potion assumption; extended potions have identical protection. Ids are the 4-dose potions. */
@Getter
@RequiredArgsConstructor
public enum Antifire
{
	NONE("None", -1, -1),
	REGULAR("Antifire", ItemID._4DOSE1ANTIDRAGON, ItemID._4DOSE2ANTIDRAGON),
	SUPER("Super antifire", ItemID._4DOSE3ANTIDRAGON, ItemID._4DOSE4ANTIDRAGON);

	private final String label;
	private final int itemId;
	private final int extendedId;

	/** The potion with this 4-dose id, or null. */
	public static Antifire of(int itemId)
	{
		for (Antifire potion : values())
			if (potion.itemId == itemId && itemId >= 0)
				return potion;
		return null;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
