package com.bestgearsetup;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/** Diary tiers share RuneLite variation families, but each tier needs its own unlock. */
public final class DiaryRewards
{
	private static final Set<Integer> FAMILIES = new HashSet<>();

	static
	{
		for (int id : Arrays.asList(ItemID.ZEAH_BLESSING_ELITE, ItemID.ATJUN_GLOVES_ELITE,
			ItemID.VARROCK_ARMOUR_ELITE, ItemID.WILDERNESS_SWORD_ELITE, ItemID.MORYTANIA_LEGS_ELITE,
			ItemID.FALADOR_SHIELD_ELITE, ItemID.ARDY_CAPE_ELITE, ItemID.LUMBRIDGE_RING_ELITE,
			ItemID.FREMENNIK_BOOTS_ELITE, ItemID.DESERT_AMULET_ELITE, ItemID.SEERS_HEADBAND_ELITE,
			ItemID.WESTERN_BANNER_ELITE))
		{
			FAMILIES.add(ItemVariationMapping.map(id));
		}
	}

	private DiaryRewards()
	{
	}

	/** Whether ownership of this exact tier is required as evidence of the account's unlock. */
	public static boolean isReward(int id)
	{
		return FAMILIES.contains(ItemVariationMapping.map(id));
	}
}
