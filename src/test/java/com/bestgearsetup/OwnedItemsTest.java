package com.bestgearsetup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class OwnedItemsTest
{
	@Test
	public void diaryTiersRequireTheirOwnOwnershipEvidence()
	{
		int[][] tiers = {
			{ItemID.ZEAH_BLESSING_MEDIUM, ItemID.ZEAH_BLESSING_ELITE},
			{ItemID.ATJUN_GLOVES_HARD, ItemID.ATJUN_GLOVES_ELITE},
			{ItemID.VARROCK_ARMOUR_HARD, ItemID.VARROCK_ARMOUR_ELITE},
			{ItemID.ARDY_CAPE_HARD, ItemID.ARDY_CAPE_ELITE},
			{ItemID.LUMBRIDGE_RING_HARD, ItemID.LUMBRIDGE_RING_ELITE},
			{ItemID.FALADOR_SHIELD_HARD, ItemID.FALADOR_SHIELD_ELITE},
			{ItemID.MORYTANIA_LEGS_HARD, ItemID.MORYTANIA_LEGS_ELITE},
			{ItemID.WILDERNESS_SWORD_HARD, ItemID.WILDERNESS_SWORD_ELITE},
			{ItemID.FREMENNIK_BOOTS_HARD, ItemID.FREMENNIK_BOOTS_ELITE},
			{ItemID.DESERT_AMULET_HARD, ItemID.DESERT_AMULET_ELITE},
			{ItemID.SEERS_HEADBAND_HARD, ItemID.SEERS_HEADBAND_ELITE},
			{ItemID.WESTERN_BANNER_HARD, ItemID.WESTERN_BANNER_ELITE}
		};
		for (int[] family : tiers)
		{
			assertTrue(OwnedItems.expand(Collections.singleton(family[0])).contains(family[0]));
			assertFalse("Lower diary tier grants " + family[1],
				OwnedItems.expand(Collections.singleton(family[0])).contains(family[1]));
			assertTrue(OwnedItems.expand(Collections.singleton(family[1])).contains(family[1]));
		}
	}

	@Test
	public void twistedAncestralCountsAsTheCorrespondingAncestralPieces()
	{
		int[][] pieces = {
			{ItemID.ANCESTRAL_HAT_TWISTED, ItemID.ANCESTRAL_HAT},
			{ItemID.ANCESTRAL_ROBE_TOP_TWISTED, ItemID.ANCESTRAL_ROBE_TOP},
			{ItemID.ANCESTRAL_ROBE_BOTTOM_TWISTED, ItemID.ANCESTRAL_ROBE_BOTTOM}
		};
		for (int[] piece : pieces)
		{
			Set<Integer> owned = OwnedItems.expand(Collections.singleton(piece[0]));
			assertTrue(owned.contains(piece[0]));
			assertTrue(owned.contains(piece[1]));
		}
	}

	@Test
	public void decoratedEquipmentLinksBothItsBaseAndItsRemovableKit()
	{
		Set<Integer> owned = OwnedItems.expand(Collections.singleton(ItemID.DRAGON_SCIMITAR_ORNAMENT));
		assertTrue(owned.contains(ItemID.DRAGON_SCIMITAR));
		assertTrue(owned.contains(ItemID.DRAGON_SCIMITAR_ORNAMENT_KIT));
		assertFalse(OwnedItems.expand(Collections.singleton(ItemID.DRAGON_SCIMITAR))
			.contains(ItemID.DRAGON_SCIMITAR_ORNAMENT));
		assertFalse(OwnedItems.expand(Collections.singleton(ItemID.DRAGON_SCIMITAR_ORNAMENT_KIT))
			.contains(ItemID.DRAGON_SCIMITAR));
	}

	@Test
	public void paidUpgradesStillRequireTheirExtraComponents()
	{
		Set<Integer> base = OwnedItems.expand(new HashSet<>(Arrays.asList(ItemID.AVERNIC_TREADS, ItemID.ELIDINIS_WARD)));
		assertFalse(base.contains(ItemID.AVERNIC_TREADS_MAX));
		assertFalse(base.contains(ItemID.ELIDINIS_WARD_FORTIFIED));
		assertTrue(OwnedItems.expand(Collections.singleton(ItemID.AVERNIC_TREADS_MAX)).contains(ItemID.AVERNIC_TREADS));
	}

	@Test
	public void materialOwnershipDoesNotGrantFinishedEquipment()
	{
		Set<Integer> owned = OwnedItems.expand(Collections.singleton(ItemID.PRIF_ARMOUR_SEED));
		assertFalse(owned.contains(ItemID.CRYSTAL_HELMET));
		assertFalse(owned.contains(ItemID.CRYSTAL_CHESTPLATE));
		assertFalse(owned.contains(ItemID.CRYSTAL_PLATELEGS));
	}

	@Test
	public void removingTheDecoratedItemRemovesItsInferredBase()
	{
		assertTrue(OwnedItems.expand(Collections.singleton(ItemID.ANCESTRAL_HAT_TWISTED)).contains(ItemID.ANCESTRAL_HAT));
		assertFalse(OwnedItems.expand(Collections.emptySet()).contains(ItemID.ANCESTRAL_HAT));
	}
}
