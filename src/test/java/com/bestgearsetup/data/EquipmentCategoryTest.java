package com.bestgearsetup.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class EquipmentCategoryTest
{
	private GearItem item(int id, String name)
	{
		GearItem gear = new GearItem();
		gear.setId(id);
		gear.setName(name);
		return gear;
	}

	@Test
	public void identifiesUntaggedBountyHunterWeapon()
	{
		assertTrue(item(ItemID.BH_VESTAS_LONGSWORD, "vesta's blighted longsword").isBountyHunterEquipment());
		assertTrue(item(27861, "abyssal dagger (BH)").isBountyHunterEquipment());
		assertFalse(item(22613, "vesta's longsword (dmm)").isBountyHunterEquipment());
		assertFalse(item(5698, "dragon dagger(p++)").isBountyHunterEquipment());
	}

	@Test
	public void identifiesDeadmanCorruptedWeaponsWithoutFilteringNormalCorruptedArmour()
	{
		assertTrue(item(ItemID.DEADMAN_BLIGHTED_SCYTHE_OF_VITUR, "corrupted scythe of vitur").isDmmEquipment());
		assertTrue(item(22616, "vesta's chainbody (dmm)").isDmmEquipment());
		assertFalse(item(20838, "corrupted helm").isDmmEquipment());
		assertFalse(item(25867, "bow of faerdhinen (c)").isDmmEquipment());
		assertFalse(item(ItemID.DEADMAN_MA2_ZAMORAK_CAPE, "imbued zamorak cape (deadman)").isDmmEquipment());
		assertFalse(item(ItemID.DEADMAN_AGS, "armadyl godsword (deadman)").isDmmEquipment());
	}

	@Test
	public void optionalCategoriesRemainAvailableInTheCatalogue()
	{
		assertFalse(GameData.isExcluded(item(24617, "vesta's blighted longsword")));
		assertFalse(GameData.isExcluded(item(27861, "abyssal dagger (bh)")));
		assertFalse(GameData.isExcluded(item(22616, "vesta's chainbody (dmm)")));
		assertFalse(GameData.isExcluded(item(99949, "the dogsword (dmm)")));
		assertFalse(GameData.isExcluded(item(99901, "test helm (beta)")));
		assertTrue(item(99901, "test helm (beta)").isBetaEquipment());
	}

	@Test
	public void unsupportedMinigameAndLeagueEquipmentStaysExcluded()
	{
		assertTrue(GameData.isExcluded(item(99920, "the dogsword")));
		assertFalse(GameData.isExcluded(item(23840, "corrupted helm (basic)")));
		assertFalse(GameData.isExcluded(item(23821, "corrupted axe")));
		assertTrue(GameData.isExcluded(item(22486, "scythe of vitur (uncharged)")));
	}

	@Test
	public void realCacheIdsDoNotMakeHistoricLeagueGearAvailable()
	{
		for (String name : new String[]{"drygore blowpipe", "amulet of the monarchs", "emperor ring", "devil's element",
			"nature's reprisal", "gloves of the damned", "crystal blessing", "sunlight spear", "sunlit bracers"})
		{
			assertTrue(name, GameData.isExcluded(item(30375, name)));
		}
		assertFalse(GameData.isExcluded(item(99949, "the dogsword (dmm)")));
	}
}
