package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

public class OwnedItemsTest
{
	/** Main code gets the client's Gson in startUp; tests use the same instance. */
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
	}

	@Test
	public void ordinaryAndTrimmedGloriesRequireSeparateOwnership()
	{
		int[] ordinary = {ItemID.AMULET_OF_GLORY, ItemID.AMULET_OF_GLORY_1, ItemID.AMULET_OF_GLORY_2,
			ItemID.AMULET_OF_GLORY_3, ItemID.AMULET_OF_GLORY_4, ItemID.AMULET_OF_GLORY_5, ItemID.AMULET_OF_GLORY_6};
		int[] trimmed = {ItemID.TRAIL_AMULET_OF_GLORY, ItemID.TRAIL_AMULET_OF_GLORY_1,
			ItemID.TRAIL_AMULET_OF_GLORY_2, ItemID.TRAIL_AMULET_OF_GLORY_3, ItemID.TRAIL_AMULET_OF_GLORY_4,
			ItemID.TRAIL_AMULET_OF_GLORY_5, ItemID.TRAIL_AMULET_OF_GLORY_6};
		for (int[] family : new int[][]{ordinary, trimmed})
		{
			for (int id : family)
			{
				Set<Integer> owned = OwnedItems.expand(Collections.singleton(id));
				for (int variant : family)
				{
					assertTrue("Glory " + id + " should grant charge variant " + variant, owned.contains(variant));
				}
				for (int variant : family == ordinary ? trimmed : ordinary)
				{
					assertFalse("Glory " + id + " must not grant " + variant, owned.contains(variant));
				}
			}
		}
	}

	@Test
	public void imbuedSalveVariantsCountAsImbuedButEnchantedSalveDoesNot()
	{
		int[][] tiers = {{4081}, {10588}, {12017, 25250, 26763}, {12018, 25278, 26782}};
		for (int[] tier : tiers)
		{
			for (int id : tier)
			{
				Set<Integer> owned = OwnedItems.expand(Collections.singleton(id));
				for (int[] other : tiers)
				{
					for (int variant : other)
					{
						assertEquals("Salve " + id + " grants " + variant, tier == other, owned.contains(variant));
					}
				}
			}
		}
	}

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
	public void twistedAncestralDoesNotPretendItsBaseIsSeparatelyHeld()
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
			assertFalse(owned.contains(piece[1]));
		}
	}

	@Test
	public void decoratedEquipmentDoesNotPretendItsComponentsAreSeparatelyHeld()
	{
		Set<Integer> owned = OwnedItems.expand(Collections.singleton(ItemID.DRAGON_SCIMITAR_ORNAMENT));
		assertFalse(owned.contains(ItemID.DRAGON_SCIMITAR));
		assertFalse(owned.contains(ItemID.DRAGON_SCIMITAR_ORNAMENT_KIT));
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
		assertFalse(OwnedItems.expand(Collections.singleton(ItemID.AVERNIC_TREADS_MAX)).contains(ItemID.AVERNIC_TREADS));
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
	public void removingTheDecoratedItemRemovesItsOwnership()
	{
		assertTrue(OwnedItems.expand(Collections.singleton(ItemID.ANCESTRAL_HAT_TWISTED)).contains(ItemID.ANCESTRAL_HAT_TWISTED));
		assertFalse(OwnedItems.expand(Collections.emptySet()).contains(ItemID.ANCESTRAL_HAT_TWISTED));
	}

	@Test
	public void stackSizesSurvivePersistenceAndOldEntriesCountAsOne()
	{
		Map<Integer, Long> bank = OwnedItems.parseQuantities("9242:159,11865:1");
		assertEquals(Long.valueOf(159), bank.get(9242));
		assertEquals(bank, OwnedItems.parseQuantities(OwnedItems.serialize(bank)));
		// Caches written before quantities were tracked hold bare ids: owned, but only one unit is assumed.
		assertEquals(Long.valueOf(1), OwnedItems.parseQuantities("9242,oops,4164").get(9242));
		assertEquals(2, OwnedItems.parseQuantities("9242,oops,4164").size());
		assertTrue(OwnedItems.parseQuantities("9242:0").isEmpty());
	}

	@Test
	public void containersAddUpAndManualEntriesCoverAnyQuantity()
	{
		Map<Integer, Long> total = OwnedItems.combine(Arrays.asList(
			Collections.singletonMap(9242, 159L), Collections.singletonMap(9242, 41L), Collections.<Integer, Long>emptyMap()),
			Collections.singleton(4164));
		assertEquals(Long.valueOf(200), total.get(9242));
		assertEquals(Long.valueOf(OwnedItems.UNLIMITED), total.get(4164));
	}
}
