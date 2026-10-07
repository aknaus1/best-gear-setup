package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.WikiGameData;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import net.runelite.api.gameval.ItemID;
import org.junit.BeforeClass;
import org.junit.Test;

public class ItemCostsTest
{
	/** Worst case for the cache lookup: nothing is tradeable unless the Wiki flag or a mapping says so. */
	private static final IntPredicate NOT_TRADEABLE = id -> false;

	private static GameData bundled;

	@BeforeClass
	public static void loadBundledCatalogue() throws Exception
	{
		bundled = WikiGameData.get(new Gson()).gameData(new Gson());
	}

	private long price(int id, long fallback, int... components)
	{
		GearItem item = new GearItem();
		item.setId(id);
		item.setPrice(fallback);
		Map<Integer, Long> prices = new HashMap<>();
		for (int i = 0; i < components.length; i += 2)
		{
			prices.put(components[i], (long) components[i + 1]);
		}
		return ItemCosts.price(item, component -> prices.getOrDefault(component, 0L), NOT_TRADEABLE);
	}

	@Test
	public void crystalArmourUsesOneThreeAndTwoSeedsIncludingRecolours()
	{
		assertEquals(5_000_000, price(ItemID.CRYSTAL_HELMET, 0, ItemID.PRIF_ARMOUR_SEED, 5_000_000));
		assertEquals(15_000_000, price(ItemID.CRYSTAL_CHESTPLATE, 0, ItemID.PRIF_ARMOUR_SEED, 5_000_000));
		assertEquals(10_000_000, price(ItemID.CRYSTAL_PLATELEGS, 0, ItemID.PRIF_ARMOUR_SEED, 5_000_000));
		assertEquals(5_000_000, price(ItemID.CRYSTAL_HELMET_HEFIN, 0, ItemID.PRIF_ARMOUR_SEED, 5_000_000));
	}

	@Test
	public void enhancedCrystalWeaponsUseTradableInactiveWeapon()
	{
		assertEquals(100_000_000, price(ItemID.BOW_OF_FAERDHINEN, 0,
			ItemID.BOW_OF_FAERDHINEN_INACTIVE, 100_000_000));
		assertEquals(100_000_000, price(25867, 0, ItemID.BOW_OF_FAERDHINEN_INACTIVE, 100_000_000));
		assertEquals(100_000_000, price(ItemID.BLADE_OF_SAELDOR, 0,
			ItemID.BLADE_OF_SAELDOR_INACTIVE, 100_000_000));
		assertEquals(100_000_000, price(24551, 0, ItemID.BLADE_OF_SAELDOR_INACTIVE, 100_000_000));
	}

	@Test
	public void everyAvernicTreadsUpgradeIncludesItsBootComponents()
	{
		int[] variants = {
			ItemID.AVERNIC_TREADS, ItemID.AVERNIC_TREADS_MELEE, ItemID.AVERNIC_TREADS_RANGED,
			ItemID.AVERNIC_TREADS_MAGIC, ItemID.AVERNIC_TREADS_MELEE_RANGED,
			ItemID.AVERNIC_TREADS_MELEE_MAGIC, ItemID.AVERNIC_TREADS_RANGED_MAGIC, ItemID.AVERNIC_TREADS_MAX
		};
		long[] expected = {100, 120, 130, 140, 150, 160, 170, 190};
		for (int i = 0; i < variants.length; i++)
		{
			assertEquals(expected[i], price(variants[i], 0, ItemID.AVERNIC_TREADS, 100,
				ItemID.PRIMORDIAL_BOOTS, 20, ItemID.PEGASIAN_BOOTS, 30, ItemID.ETERNAL_BOOTS, 40));
		}
	}

	@Test
	public void imbuesKeepTheTradableBaseCost()
	{
		assertEquals(4_000_000, price(ItemID.NZONE_BERZERKER_RING, 0, ItemID.BERZERKER_RING, 4_000_000));
		assertEquals(1_000_000, price(ItemID.NZONE_RANGER_RING, 0, ItemID.RANGER_RING, 1_000_000));
		assertEquals(500_000, price(ItemID.NZONE_SEER_RING, 0, ItemID.SEER_RING, 500_000));
		assertEquals(100_000, price(ItemID.NZONE_WARRIOR_RING, 0, ItemID.WARRIOR_RING, 100_000));
		assertEquals(1_000_000, price(11784, 0, ItemID.HARMLESS_BLACK_MASK, 1_000_000));
		assertEquals(1_000_000, price(11865, 0, ItemID.HARMLESS_BLACK_MASK, 1_000_000));
	}

	@Test
	public void consumedComponentsCountAsAcquisitionCost()
	{
		assertEquals(12_000_000, price(ItemID.BLOOD_AMULET, 0,
			ItemID.ENCHANTED_ONYX_AMULET, 2_000_000, ItemID.BLOOD_SHARD, 10_000_000));
		assertEquals(30_000_000, price(ItemID.INFERNAL_DEFENDER, 0, ItemID.INFERNAL_DEFENDER_HILT, 30_000_000));
		assertEquals(30_000_000, price(ItemID.INFERNAL_DEFENDER_GHOMMAL_6, 0,
			ItemID.INFERNAL_DEFENDER_HILT, 30_000_000));
		assertEquals(3_000_000, price(ItemID.ABYSSAL_TENTACLE, 0,
			ItemID.ABYSSAL_WHIP, 2_000_000, ItemID.KRAKEN_TENTACLE, 1_000_000));
	}

	@Test
	public void chargedEquipmentUsesItsTradableWeaponOrArmour()
	{
		int[][] pairs = {
			{ItemID.SCYTHE_OF_VITUR, ItemID.SCYTHE_OF_VITUR_UNCHARGED},
			{ItemID.TUMEKENS_SHADOW, ItemID.TUMEKENS_SHADOW_UNCHARGED},
			{ItemID.SANGUINESTI_STAFF, ItemID.SANGUINESTI_STAFF_UNCHARGED},
			{ItemID.WILD_CAVE_BOW_CHARGED, ItemID.WILD_CAVE_BOW_UNCHARGED},
			{ItemID.WILD_CAVE_CHAINMACE_CHARGED, ItemID.WILD_CAVE_CHAINMACE_UNCHARGED},
			{ItemID.WILD_CAVE_SCEPTRE_CHARGED, ItemID.WILD_CAVE_SCEPTRE_UNCHARGED},
			{ItemID.WILD_CAVE_WEBWEAVER_CHARGED, ItemID.WILD_CAVE_WEBWEAVER_UNCHARGED},
			{ItemID.WILD_CAVE_URSINE_CHARGED, ItemID.WILD_CAVE_URSINE_UNCHARGED},
			{ItemID.WILD_CAVE_ACCURSED_CHARGED, ItemID.WILD_CAVE_ACCURSED_UNCHARGED},
			{ItemID.TOXIC_BLOWPIPE_LOADED, ItemID.TOXIC_BLOWPIPE},
			{ItemID.SERPENTINE_HELM_CHARGED, ItemID.SERPENTINE_HELM},
			{ItemID.TOXIC_TOTS_CHARGED, ItemID.TOXIC_TOTS_UNCHARGED}
		};
		for (int[] pair : pairs)
		{
			assertEquals(1_000_000, price(pair[0], 0, pair[1], 1_000_000));
		}
	}

	@Test
	public void rewardEquipmentHasNoInventedPurchaseCost()
	{
		assertEquals(0, price(11665, 0));
		assertEquals(0, price(6570, 0));
		assertEquals(0, price(ItemID.GRACEFUL_HOOD, 0, ItemID.GRACE, 100_000));
	}

	@Test
	public void missingComponentQuoteMakesThePriceUnknownDespiteBundledEstimate()
	{
		assertEquals(ItemCosts.UNKNOWN, price(ItemID.BLOOD_AMULET, 12_000_000,
			ItemID.ENCHANTED_ONYX_AMULET, 2_000_000));
		assertEquals(ItemCosts.UNKNOWN, price(ItemID.BLOOD_AMULET, 12_000_000, ItemID.BLOOD_SHARD, 15_000_000));
	}

	@Test
	public void tradeableItemWithoutQuoteIsUnknownRatherThanFree()
	{
		GearItem bow = new GearItem();
		bow.setId(ItemID.TWISTED_BOW);
		bow.setTradeable(true);
		assertEquals(ItemCosts.UNKNOWN, ItemCosts.price(bow, id -> 0, NOT_TRADEABLE));
		assertEquals(1_500_000_000L, ItemCosts.price(bow, id -> id == ItemID.TWISTED_BOW ? 1_500_000_000L : 0, NOT_TRADEABLE));
		assertFalse(ItemCosts.isKnown(ItemCosts.UNKNOWN));
	}

	@Test
	public void syntheticTradeableItemWithoutBundledPriceIsUnknown()
	{
		GearItem item = new GearItem();
		item.setId(99949);
		item.setTradeable(true);
		assertEquals(ItemCosts.UNKNOWN, ItemCosts.price(item, id -> 0, NOT_TRADEABLE));
		item.setTradeable(false);
		assertEquals(0, ItemCosts.price(item, id -> 0, NOT_TRADEABLE));
	}

	@Test
	public void expensiveComponentSumsUseLongs()
	{
		assertEquals(2_900_000_000L, price(ItemID.AVERNIC_TREADS_MAX, 0,
			ItemID.AVERNIC_TREADS, 2_000_000_000, ItemID.PRIMORDIAL_BOOTS, 300_000_000,
			ItemID.PEGASIAN_BOOTS, 300_000_000, ItemID.ETERNAL_BOOTS, 300_000_000));
	}

	@Test
	public void untradeableCatalogueItemsWithUnpricedMappedBasesAreUnknown()
	{
		// Bundled entries flagged untradeable whose purchasable base is a RuneLite mapping target.
		int[][] itemAndBase = {
			{ItemID.TOXIC_BLOWPIPE_LOADED, ItemID.TOXIC_BLOWPIPE},
			{ItemID.BOW_OF_FAERDHINEN, ItemID.BOW_OF_FAERDHINEN_INACTIVE},
			{25867, ItemID.BOW_OF_FAERDHINEN_INACTIVE},
			{ItemID.SERPENTINE_HELM_CHARGED, ItemID.SERPENTINE_HELM},
		};
		for (int[] pair : itemAndBase)
		{
			GearItem item = bundled.findItem(pair[0]);
			assertNotNull("bundled item " + pair[0], item);
			assertFalse(item.getName() + " is untradeable in the bundled catalogue", item.isTradeable());
			assertEquals(item.getName(), ItemCosts.UNKNOWN, ItemCosts.price(item, id -> 0, NOT_TRADEABLE));
			assertEquals(item.getName(), 7_000_000,
				ItemCosts.price(item, id -> id == pair[1] ? 7_000_000 : 0, NOT_TRADEABLE));
		}
	}

	@Test
	public void chargedAndDemonicWeaponsAreBoughtThroughTradableComponents()
	{
		for (int id : new int[]{ItemID.SANGUINESTI_STAFF, ItemID.TUMEKENS_SHADOW, ItemID.TOXIC_TOTS_CHARGED,
			ItemID.PURGING_STAFF, ItemID.EMBERLIGHT, ItemID.SCORCHING_BOW, ItemID.BLOOD_AMULET})
		{
			assertTrue(String.valueOf(id), ItemCosts.hasTradableComponents(id));
		}
		assertFalse(ItemCosts.hasTradableComponents(ItemID.TZHAAR_CAPE_FIRE));
		assertFalse(ItemCosts.hasTradableComponents(ItemID.HUNDRED_GAUNTLETS_LEVEL_10));
	}

	@Test
	public void culinaromancerGlovesCostTheirChestPrice()
	{
		GearItem barrows = bundled.findItem(ItemID.HUNDRED_GAUNTLETS_LEVEL_10);
		assertNotNull(barrows);
		assertEquals(130_000, ItemCosts.price(barrows, id -> 0, NOT_TRADEABLE));
		assertEquals(130_000, price(ItemID.HUNDRED_GAUNTLETS_LEVEL_9, 0));
		assertEquals(6_500, price(ItemID.HUNDRED_GAUNTLETS_LEVEL_8, 0));
		assertFalse("Still not buyable without the quest", ItemCosts.hasTradableComponents(ItemID.HUNDRED_GAUNTLETS_LEVEL_10));
	}

	@Test
	public void breakableUntradeablesCarryTheirPvpRepairFee()
	{
		GearItem defender = bundled.findItem(ItemID.DRAGON_PARRYINGDAGGER);
		GearItem fireCape = bundled.findItem(ItemID.TZHAAR_CAPE_FIRE);
		assertNotNull(defender);
		assertEquals("Earned, so acquiring it costs nothing", 0, ItemCosts.price(defender, id -> 0, NOT_TRADEABLE));
		assertEquals(240_000, ItemCosts.pvpRepairCost(defender));
		assertEquals(150_000, ItemCosts.pvpRepairCost(fireCape));
		assertEquals(0, ItemCosts.pvpRepairCost(bundled.findItem(ItemID.HUNDRED_GAUNTLETS_LEVEL_10)));
		assertFalse("Still earned, not bought", ItemCosts.hasTradableComponents(ItemID.DRAGON_PARRYINGDAGGER));
	}

	@Test
	public void unmappedItemsUseTheGameCacheTradeability()
	{
		GearItem fireCape = bundled.findItem(ItemID.TZHAAR_CAPE_FIRE);
		assertNotNull(fireCape);
		assertEquals(0, ItemCosts.price(fireCape, id -> 0, NOT_TRADEABLE));

		GearItem unmapped = new GearItem();
		unmapped.setId(ItemID.TZHAAR_CAPE_FIRE);
		assertEquals(ItemCosts.UNKNOWN, ItemCosts.price(unmapped, id -> 0, id -> id == ItemID.TZHAAR_CAPE_FIRE));
	}

	@Test
	public void decoratedEquipmentCostsItsBaseAndAnyTradableKit()
	{
		assertEquals(40_000_000, price(ItemID.ANCESTRAL_HAT_TWISTED, 0, ItemID.ANCESTRAL_HAT, 40_000_000));
		assertEquals(90_000_000, price(ItemID.ANCESTRAL_ROBE_TOP_TWISTED, 0, ItemID.ANCESTRAL_ROBE_TOP, 90_000_000));
		assertEquals(70_000_000, price(ItemID.ANCESTRAL_ROBE_BOTTOM_TWISTED, 0,
			ItemID.ANCESTRAL_ROBE_BOTTOM, 70_000_000));
		assertEquals(150_000, price(ItemID.DRAGON_SCIMITAR_ORNAMENT, 0,
			ItemID.DRAGON_SCIMITAR, 60_000, ItemID.DRAGON_SCIMITAR_ORNAMENT_KIT, 90_000));
	}

	@Test
	public void ownershipDoesNotGrantPaidUpgrades()
	{
		assertFalse(ItemCosts.equivalent(ItemID.AVERNIC_TREADS, ItemID.AVERNIC_TREADS_MAX));
		assertFalse(ItemCosts.equivalent(ItemID.AVERNIC_TREADS_MELEE, ItemID.AVERNIC_TREADS_RANGED));
		assertFalse(ItemCosts.equivalent(ItemID.ELIDINIS_WARD, ItemID.ELIDINIS_WARD_FORTIFIED));
		assertTrue(ItemCosts.equivalent(ItemID.NZONE_BERZERKER_RING, ItemID.BERZERKER_RING));
		assertTrue(ItemCosts.equivalent(ItemID.SCYTHE_OF_VITUR, ItemID.SCYTHE_OF_VITUR_UNCHARGED));
	}
}
