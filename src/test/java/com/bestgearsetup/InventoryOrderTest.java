package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class InventoryOrderTest
{
	@Test public void pairedRowsUseTheUsersExactFillOrder()
	{
		assertEquals(Arrays.asList(1, 5, 2, 6, 3, 7, 4, 8, 9, 13, 10, 14, 11, 15, 12, 16),
			java.util.stream.IntStream.range(0, 16).map(i -> RaidPlan.rowSwitchCell(i) + 1).boxed().collect(Collectors.toList()));
	}

	@Test public void pairedColumnsUseTheUsersExactFillOrder()
	{
		assertEquals(Arrays.asList(1, 2, 5, 6, 9, 10, 13, 14, 17, 18),
			java.util.stream.IntStream.range(0, 10).map(i -> RaidPlan.columnSwitchCell(i, 0) + 1).boxed().collect(Collectors.toList()));
		assertEquals(Arrays.asList(3, 4, 7, 8, 11, 12, 15, 16, 19, 20),
			java.util.stream.IntStream.range(0, 10).map(i -> RaidPlan.columnSwitchCell(i, 1) + 1).boxed().collect(Collectors.toList()));
	}

	@Test public void frequentlyUsedPiecesLeadAlternativesEvenWhenTheRareSetHasMorePieces()
	{
		List<RaidPlan.InventoryItem> inventory = switchSet(10, 6, RaidPlan.InventoryGroup.MELEE_GEAR, bits(1));
		inventory.add(gear(30, "Frequent weapon", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.WEAPON, 8, bits(2, 3, 4), null));
		inventory.add(gear(31, "Frequent body", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.BODY, 7, bits(2, 3), null));
		List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
		assertEquals(Arrays.asList(30, 31, 10, 11, 12, 13, 14, 15), rowIds(display, 0, 8));
		Collections.reverse(inventory);
		assertEquals(display, RaidPlan.inventoryLayout(inventory));
	}

	@Test public void uniqueRareCapeAndHighDamageBootsAreExtrasRatherThanCoreGear()
	{
		List<RaidPlan.InventoryItem> inventory = new ArrayList<>(Arrays.asList(
			gear(10, "Main", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.WEAPON, 8),
			gear(11, "Head", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.HEAD, 8),
			gear(12, "Body", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.BODY, 8),
			gear(13, "Offhand", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.SHIELD, 8),
			gear(20, "Extra weapon", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.WEAPON, 3),
			gear(21, "Rare cape", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.CAPE, 1),
			new RaidPlan.InventoryItem("Rare boots", Collections.singletonList(22), true, Collections.emptyList(),
				RaidPlan.InventoryGroup.MAGIC_GEAR, 0, 0, false, Slot.FEET, 1, 0, null, 200, 200)));
		List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
		assertEquals(Arrays.asList(10, 11, 12, 13, 20, 21, 22), rowIds(display, 0, 7));
		Collections.shuffle(inventory, new java.util.Random(17));
		assertEquals(display, RaidPlan.inventoryLayout(inventory));
	}

	@Test public void mainWeaponStillLeadsAccessoriesSharedAcrossMoreEncounters()
	{
		List<RaidPlan.InventoryItem> inventory = new ArrayList<>(Arrays.asList(
			gear(10, "Main bow", RaidPlan.InventoryGroup.RANGED_GEAR, Slot.WEAPON, 2),
			gear(11, "Shared back", RaidPlan.InventoryGroup.RANGED_GEAR, Slot.CAPE, 8),
			gear(12, "Shared body", RaidPlan.InventoryGroup.RANGED_GEAR, Slot.BODY, 8),
			gear(20, "Alternate bow", RaidPlan.InventoryGroup.RANGED_GEAR, Slot.WEAPON, 1)));
		assertEquals(Arrays.asList(10, 11, 12, 20), rowIds(RaidPlan.inventoryLayout(inventory), 0, 4));
	}

	@Test public void dominantWornStyleOwnsSharedArmourAndCountsOnlyItsOwnEncounters()
	{
		GearItem shared = item(11, Slot.BODY, "Shared ranged body");
		SetupResult start = setup(CombatClass.MELEE, item(1, Slot.WEAPON, "Starting sword"));
		SetupResult melee = setup(CombatClass.MELEE, item(2, Slot.WEAPON, "Melee sword"), shared);
		SetupResult ranged = setup(CombatClass.RANGED, item(3, Slot.WEAPON, "Bow"), shared);
		SetupResult inherited = new SetupResult(CombatClass.MAGIC, ranged.getLoadout(), null, 0, 0,
			Collections.emptySet(), Collections.emptySet(), null, ranged.getLoadout());
		SetupResult filled = setup(CombatClass.MAGIC, item(4, Slot.WEAPON, "Staff"), shared)
			.withFilledSlots(Collections.singleton(Slot.BODY));
		List<SetupResult> setups = Arrays.asList(start, melee, ranged, ranged, ranged,
			inherited, inherited, inherited, inherited, filled, filled, filled, filled);
		List<SearchResults> results = setups.stream().map(s -> new SearchResults(new Monster(), 100,
			Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false))
			.collect(Collectors.toList());
		List<RaidPlan.InventoryItem> inventory = RaidPlan.filledInventory(setups, results, id -> false, new RaidPlan.Memo());
		RaidPlan.InventoryItem body = inventory.stream().filter(i -> i.getIds().get(0) == 11).findFirst().get();
		assertEquals(RaidPlan.InventoryGroup.RANGED_GEAR, body.getGroup());
		assertEquals(3, body.getUses());
		assertEquals(bits(2, 3, 4), body.getWornIn());
		List<SetupResult> reversed = new ArrayList<>(setups.subList(1, setups.size()));
		Collections.reverse(reversed); reversed.add(0, start);
		RaidPlan.InventoryItem rebuilt = RaidPlan.filledInventory(reversed, results, id -> false, new RaidPlan.Memo())
			.stream().filter(i -> i.getIds().get(0) == 11).findFirst().get();
		assertEquals(body.getGroup(), rebuilt.getGroup());
		assertEquals(body.getUses(), rebuilt.getUses());
	}

	@Test public void spareCellsKeepExtraWeaponsBeforeExtraGearAndOffhandBeforeBoth()
	{
		List<RaidPlan.InventoryItem> inventory = new ArrayList<>(Arrays.asList(
			gear(10, "Main", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.WEAPON, 8),
			gear(11, "Back", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.CAPE, 8),
			gear(12, "Body", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.BODY, 8),
			gear(13, "Offhand", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.SHIELD, 8),
			gear(20, "Extra weapon", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.WEAPON, 1),
			gear(21, "Extra cape", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.CAPE, 1),
			gear(22, "Extra body", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.BODY, 1),
			gear(23, "Extra offhand", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.SHIELD, 1)));
		assertEquals(Arrays.asList(10, 11, 12, 13, 20, 21, 22, 23), rowIds(RaidPlan.inventoryLayout(inventory), 0, 8));
	}

	@Test public void largestSwitchLeadsRegardlessOfCombatStyle()
	{
		List<RaidPlan.InventoryItem> inventory = switchSet(10, 4, RaidPlan.InventoryGroup.MELEE_GEAR, bits(1));
		inventory.addAll(switchSet(30, 4, RaidPlan.InventoryGroup.RANGED_GEAR, bits(2)));
		inventory.addAll(switchSet(50, 8, RaidPlan.InventoryGroup.MAGIC_GEAR, bits(3)));
		List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
		assertEquals(Integer.valueOf(50), display.get(0).getIds().get(0));
		assertTrue(ids(display.stream().filter(i -> i != null).collect(Collectors.toList())).indexOf(10)
			< ids(display.stream().filter(i -> i != null).collect(Collectors.toList())).indexOf(30));
		assertSingleBlock(display, RaidPlan.InventoryGroup.MELEE_GEAR);
		assertSingleBlock(display, RaidPlan.InventoryGroup.RANGED_GEAR);
		assertSingleBlock(display, RaidPlan.InventoryGroup.MAGIC_GEAR);
		assertTrue(display.indexOf(inventory.get(8)) < display.indexOf(inventory.get(0)));
	}

	@Test public void frequentlyUsedSpecialWeaponsStayExtrasInsideTheirOwnCombatGroup()
	{
		List<RaidPlan.InventoryItem> inventory = switchSet(10, 4, RaidPlan.InventoryGroup.MELEE_GEAR, bits(1));
		inventory.add(gear(20, "Claws", RaidPlan.InventoryGroup.SPECIAL, Slot.WEAPON, 10, bits(2, 3), CombatClass.MELEE));
		inventory.add(gear(22, "Special offhand", RaidPlan.InventoryGroup.SPECIAL, Slot.SHIELD, 10, bits(2, 3), CombatClass.MELEE));
		inventory.add(gear(21, "Special bow", RaidPlan.InventoryGroup.SPECIAL, Slot.WEAPON, 10, bits(2, 3), CombatClass.RANGED));
		inventory.addAll(switchSet(30, 3, RaidPlan.InventoryGroup.RANGED_GEAR, bits(4)));
		List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
		assertEquals(Integer.valueOf(10), display.get(0).getIds().get(0));
		assertEquals(Integer.valueOf(20), display.get(2).getIds().get(0));
		assertEquals(Integer.valueOf(22), display.get(6).getIds().get(0));
		assertEquals(Integer.valueOf(30), display.get(8).getIds().get(0));
		assertEquals(Integer.valueOf(21), display.get(13).getIds().get(0));
	}

	@Test public void equalHeightLayoutsPreferPairedRows()
	{
		List<RaidPlan.InventoryItem> inventory = switchSet(10, 7, RaidPlan.InventoryGroup.MELEE_GEAR, bits(1));
		inventory.addAll(switchSet(30, 7, RaidPlan.InventoryGroup.RANGED_GEAR, bits(2)));
		List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
		assertEquals(Arrays.asList(10, 11, 12, 13, 14, 15, 16), rowIds(display, 0, 7));
		assertEquals(Arrays.asList(30, 31, 32, 33, 34, 35, 36), rowIds(display, 8, 7));
	}

	@Test public void coherentRowsWinEvenWhenColumnsCouldSaveARow()
	{
		for (int first = 5; first <= 6; first++)
		{
			for (int second = 5; second <= 6; second++)
			{
				List<RaidPlan.InventoryItem> inventory = switchSet(10, first, RaidPlan.InventoryGroup.MELEE_GEAR, bits(1));
				inventory.addAll(switchSet(30, second, RaidPlan.InventoryGroup.MAGIC_GEAR, bits(2)));
				List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
				int meleeBase = first >= second ? 0 : 8;
				int magicBase = first >= second ? 8 : 0;
				for (int i = 0; i < first; i++) { assertEquals(10 + i, (int) display.get(meleeBase + RaidPlan.rowSwitchCell(i)).getIds().get(0)); }
				for (int i = 0; i < second; i++) { assertEquals(30 + i, (int) display.get(magicBase + RaidPlan.rowSwitchCell(i)).getIds().get(0)); }
			}
		}
	}

	@Test public void ninePieceGroupUsesOneColumnPairRatherThanSplittingAcrossRowPairs()
	{
		List<RaidPlan.InventoryItem> inventory = switchSet(10, 9, RaidPlan.InventoryGroup.MELEE_GEAR, bits(1));
		inventory.addAll(switchSet(30, 6, RaidPlan.InventoryGroup.RANGED_GEAR, bits(2)));
		List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
		assertSingleBlock(display, RaidPlan.InventoryGroup.MELEE_GEAR);
		assertSingleBlock(display, RaidPlan.InventoryGroup.RANGED_GEAR);
		for (int i = 0; i < 9; i++) { assertEquals(10 + i, (int) display.get(RaidPlan.columnSwitchCell(i, 0)).getIds().get(0)); }
	}

	@Test public void threeStylesNeverInterleaveRowsAndColumnsToSaveSpace()
	{
		List<RaidPlan.InventoryItem> inventory = switchSet(10, 2, RaidPlan.InventoryGroup.MELEE_GEAR, bits(1));
		inventory.addAll(switchSet(30, 5, RaidPlan.InventoryGroup.RANGED_GEAR, bits(2)));
		inventory.addAll(switchSet(50, 6, RaidPlan.InventoryGroup.MAGIC_GEAR, bits(3)));
		List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
		assertEquals(Arrays.asList(50, 51, 52, 53, 54, 55), rowIds(display, 0, 6));
		assertEquals(Arrays.asList(30, 31, 32, 33, 34), rowIds(display, 8, 5));
		assertEquals(Arrays.asList(10, 11), rowIds(display, 16, 2));
	}

	@Test public void largerCoreForcesColumnsForEveryStyleRatherThanAMixedLayout()
	{
		List<RaidPlan.InventoryItem> inventory = switchSet(10, 9, RaidPlan.InventoryGroup.MELEE_GEAR, bits(1));
		inventory.addAll(switchSet(30, 3, RaidPlan.InventoryGroup.RANGED_GEAR, bits(2)));
		inventory.addAll(switchSet(50, 5, RaidPlan.InventoryGroup.MAGIC_GEAR, bits(3)));
		List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
		for (int i = 0; i < 9; i++) { assertEquals(Integer.valueOf(10 + i), display.get(RaidPlan.columnSwitchCell(i, 0)).getIds().get(0)); }
		for (int i = 0; i < 5; i++) { assertEquals(Integer.valueOf(50 + i), display.get(RaidPlan.columnSwitchCell(i, 1)).getIds().get(0)); }
		for (int i = 0; i < 3; i++) { assertEquals(Integer.valueOf(30 + i), display.get(12 + RaidPlan.columnSwitchCell(i, 1)).getIds().get(0)); }
	}

	@Test public void compatibleGearFromDifferentEncountersStaysInOneCombatBlock()
	{
		List<RaidPlan.InventoryItem> inventory = switchSet(10, 5, RaidPlan.InventoryGroup.RANGED_GEAR, bits(1));
		inventory.add(gear(20, "Extra weapon", RaidPlan.InventoryGroup.RANGED_GEAR, Slot.WEAPON, 1, bits(2), null));
		inventory.add(gear(21, "Extra back", RaidPlan.InventoryGroup.RANGED_GEAR, Slot.CAPE, 1, bits(3), null));
		inventory.add(gear(22, "Extra body", RaidPlan.InventoryGroup.RANGED_GEAR, Slot.BODY, 1, bits(4), null));
		List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
		assertEquals(Arrays.asList(10, 11, 12, 13, 14, 20, 21, 22), rowIds(display, 0, 8));
	}

	@Test public void suppliesFillSpareCellsWithoutMovingCombatPieces()
	{
		List<RaidPlan.InventoryItem> inventory = switchSet(10, 6, RaidPlan.InventoryGroup.MAGIC_GEAR, bits(1));
		List<RaidPlan.InventoryItem> before = RaidPlan.inventoryLayout(inventory);
		inventory.add(entry(SpellRunesIds.POUCH, "Rune pouch", RaidPlan.InventoryGroup.RUNES));
		inventory.add(entry(70, "Pickaxe", RaidPlan.InventoryGroup.OTHER));
		List<RaidPlan.InventoryItem> after = RaidPlan.inventoryLayout(inventory);
		for (int i = 0; i < before.size(); i++) { if (before.get(i) != null) { assertEquals(before.get(i), after.get(i)); } }
		assertEquals(8, after.size());
		assertTrue(after.stream().noneMatch(i -> i == null));
	}

	@Test public void screenshotReferenceUsesTwoSwitchBlocksAndFinishesWithPotions()
	{
		List<RaidPlan.InventoryItem> inventory = new ArrayList<>(Arrays.asList(
			rankedGear(10, Slot.WEAPON, RaidPlan.InventoryGroup.MAGIC_GEAR, 4, 0, 15),
			rankedGear(11, Slot.CAPE, RaidPlan.InventoryGroup.MAGIC_GEAR, 3, 2, 15),
			rankedGear(12, Slot.NECK, RaidPlan.InventoryGroup.MAGIC_GEAR, 3, 5, 12),
			rankedGear(13, Slot.BODY, RaidPlan.InventoryGroup.MAGIC_GEAR, 3, 1, 30),
			rankedGear(14, Slot.LEGS, RaidPlan.InventoryGroup.MAGIC_GEAR, 3, 1, 22),
			rankedGear(15, Slot.SHIELD, RaidPlan.InventoryGroup.MAGIC_GEAR, 3, 0, 6),
			rankedGear(20, Slot.WEAPON, RaidPlan.InventoryGroup.RANGED_GEAR, 7, 40, 87),
			rankedGear(21, Slot.CAPE, RaidPlan.InventoryGroup.RANGED_GEAR, 9, 0, 8),
			rankedGear(22, Slot.BODY, RaidPlan.InventoryGroup.RANGED_GEAR, 9, 3, 31),
			rankedGear(23, Slot.HEAD, RaidPlan.InventoryGroup.RANGED_GEAR, 9, 3, 8),
			rankedGear(24, Slot.WEAPON, RaidPlan.InventoryGroup.RANGED_GEAR, 2, 0, 30),
			rankedGear(30, Slot.WEAPON, RaidPlan.InventoryGroup.MELEE_GEAR, 2, 86, 90),
			rankedGear(31, Slot.WEAPON, RaidPlan.InventoryGroup.MELEE_GEAR, 1, 40, 40),
			entry(90, "Super attack", RaidPlan.InventoryGroup.BOOSTS),
			entry(91, "Super strength", RaidPlan.InventoryGroup.BOOSTS),
			entry(SpellRunesIds.POUCH, "Rune pouch", RaidPlan.InventoryGroup.RUNES)));
		assertEquals(Arrays.asList(10, 12, 14, SpellRunesIds.POUCH, 11, 13, 15, 30,
			20, 22, 24, 31, 21, 23, 90, 91), ids(RaidPlan.inventoryLayout(inventory)));
		assertPackingPreservesItems(inventory, false);
	}

	@Test public void changingWornStyleReordersTheOtherTwoSwitches()
	{
		SetupResult melee = setup(CombatClass.MELEE, item(10, Slot.WEAPON, "Sword"), item(11, Slot.CAPE, "Melee cape"),
			item(12, Slot.HEAD, "Melee helm"), item(13, Slot.BODY, "Melee body"), item(14, Slot.LEGS, "Melee legs"));
		SetupResult ranged = setup(CombatClass.RANGED, item(30, Slot.WEAPON, "Bow"), item(31, Slot.CAPE, "Ranged cape"),
			item(32, Slot.HEAD, "Ranged helm"), item(33, Slot.BODY, "Ranged body"));
		SetupResult magic = setup(CombatClass.MAGIC, item(50, Slot.WEAPON, "Staff"), item(51, Slot.CAPE, "Mage cape"),
			item(52, Slot.HEAD, "Mage hat"), item(53, Slot.BODY, "Mage body"), item(54, Slot.LEGS, "Mage legs"),
			item(55, Slot.NECK, "Mage neck"));
		List<SetupResult> setups = Arrays.asList(melee, ranged, magic);
		List<SearchResults> results = setups.stream().map(s -> new SearchResults(new Monster(), 100,
			Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false))
			.collect(Collectors.toList());
		List<RaidPlan.InventoryItem> inventory = RaidPlan.filledInventory(setups, results, id -> false, new RaidPlan.Memo());
		List<GearItem> equipment = new ArrayList<>(); setups.forEach(s -> equipment.addAll(RaidPlan.gear(s).values()));
		RaidPlan plan = new RaidPlan(RaidPlanType.TOA, RaidSpellbook.ARCEUUS, Collections.emptyList(), results,
			setups, equipment, inventory, 28, 0);
		assertEquals(Integer.valueOf(50), RaidPlan.inventoryLayout(plan.inventoryFor(melee)).get(0).getIds().get(0));
		assertEquals(Integer.valueOf(30), RaidPlan.inventoryLayout(plan.inventoryFor(melee)).get(8).getIds().get(0));
		assertEquals(Integer.valueOf(10), RaidPlan.inventoryLayout(plan.inventoryFor(magic)).get(0).getIds().get(0));
		assertEquals(Integer.valueOf(30), RaidPlan.inventoryLayout(plan.inventoryFor(magic)).get(8).getIds().get(0));
	}

	private static RaidPlan.InventoryItem rankedGear(int id, Slot slot, RaidPlan.InventoryGroup group,
		int uses, double damage, int accuracy)
	{
		return new RaidPlan.InventoryItem("Gear " + id, Collections.singletonList(id), true, Collections.emptyList(),
			group, 0, 0, false, slot, uses, 0, null, damage, accuracy);
	}

	@Test public void fullInventoriesPreserveEveryItemAndDuplicateBottleForEverySwitchSize()
	{
		for (int first = 1; first <= 11; first++)
		{
			for (int second = 1; second <= 11; second++)
			{
				List<RaidPlan.InventoryItem> inventory = switchSet(10, first, RaidPlan.InventoryGroup.MELEE_GEAR, bits(1));
				inventory.addAll(switchSet(30, second, RaidPlan.InventoryGroup.MAGIC_GEAR, bits(2)));
				inventory.add(entry(SpellRunesIds.POUCH, "Rune pouch", RaidPlan.InventoryGroup.RUNES));
				inventory.add(entry(70, "Pickaxe", RaidPlan.InventoryGroup.OTHER));
				RaidPlan.InventoryItem potion = entry(90, "Super restore", RaidPlan.InventoryGroup.BOOSTS);
				while (inventory.size() < 28) { inventory.add(potion); }
				assertPackingPreservesItems(inventory, true);
			}
		}
	}

	@Test public void threeStyleKitsStayWithinCapacityEvenWhenSeparateRectanglesAreImpossible()
	{
		for (int[] sizes : new int[][]{{9, 9, 9}, {11, 11, 6}, {7, 5, 6}, {5, 5, 11}, {3, 5, 6}, {11, 3, 5}})
		{
			List<RaidPlan.InventoryItem> inventory = switchSet(10, sizes[0], RaidPlan.InventoryGroup.MELEE_GEAR, bits(1));
			inventory.addAll(switchSet(30, sizes[1], RaidPlan.InventoryGroup.RANGED_GEAR, bits(2)));
			inventory.addAll(switchSet(50, sizes[2], RaidPlan.InventoryGroup.MAGIC_GEAR, bits(3)));
			while (inventory.size() < 28) { inventory.add(entry(90, "Restore", RaidPlan.InventoryGroup.BOOSTS)); }
			assertPackingPreservesItems(inventory, true);
		}
	}

	@Test public void spareWeaponsCannotSplitThreeFrequentlyUsedEightPieceSwitches()
	{
		List<RaidPlan.InventoryItem> inventory = new ArrayList<>();
		RaidPlan.InventoryGroup[] groups = {RaidPlan.InventoryGroup.MELEE_GEAR, RaidPlan.InventoryGroup.RANGED_GEAR,
			RaidPlan.InventoryGroup.MAGIC_GEAR};
		for (int g = 0; g < groups.length; g++)
		{
			inventory.addAll(switchSet(10 + g * 20, 8, groups[g], bits(g)));
			inventory.add(gear(100 + g, "Spare weapon", groups[g], Slot.WEAPON, 1));
		}
		List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
		for (int g = 0; g < groups.length; g++)
		{
			final int firstId = 10 + g * 20;
			assertEquals(java.util.stream.IntStream.range(firstId, firstId + 8).boxed().collect(Collectors.toList()),
				rowIds(display, g * 8, 8));
			assertEquals(Integer.valueOf(100 + g), display.get(24 + g).getIds().get(0));
		}
		inventory.add(entry(90, "Restore", RaidPlan.InventoryGroup.BOOSTS));
		assertPackingPreservesItems(inventory, true);
	}

	@Test public void bankAndEncounterInventoryUseTheSamePhysicalCellsRegardlessOfTraversal()
	{
		SetupResult start = setup(CombatClass.MELEE, item(1, Slot.WEAPON, "Starting weapon"));
		List<SetupResult> switches = Arrays.asList(
			setup(CombatClass.MELEE, item(10, Slot.WEAPON, "Sword"), item(11, Slot.BODY, "Melee body")),
			setup(CombatClass.RANGED, item(20, Slot.WEAPON, "Bow"), item(21, Slot.BODY, "Ranged body")),
			setup(CombatClass.MAGIC, item(30, Slot.WEAPON, "Staff"), item(31, Slot.BODY, "Mage body")));
		List<RaidPlan.InventoryItem> expected = null;
		for (int rotation = 0; rotation < 3; rotation++)
		{
			List<SetupResult> setups = new ArrayList<>(switches);
			Collections.rotate(setups, rotation); setups.add(0, start);
			List<SearchResults> results = setups.stream().map(s -> new SearchResults(new Monster(), 100,
				Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false))
				.collect(Collectors.toList());
			List<RaidPlan.InventoryItem> inventory = RaidPlan.filledInventory(setups, results, id -> false, new RaidPlan.Memo());
			if (expected != null) { assertEquals(ids(expected), ids(inventory)); }
			expected = inventory;
			List<GearItem> equipment = new ArrayList<>(); setups.forEach(s -> equipment.addAll(RaidPlan.gear(s).values()));
			RaidPlan plan = new RaidPlan(RaidPlanType.TOB, RaidSpellbook.ANCIENT, Collections.emptyList(), results,
				setups, equipment, inventory, 28, 0);
			assertEquals(inventory, plan.inventoryFor(start));
			List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
			for (int i = 0; i < display.size(); i++)
			{
				if (display.get(i) != null) { assertEquals(Integer.valueOf(BestGearSetupBankView.inventoryCell(i)),
					BestGearSetupBankView.raidCellsFor(plan).get(display.get(i).getIds().get(0))); }
			}
		}
	}

	private static void assertPackingPreservesItems(List<RaidPlan.InventoryItem> inventory, boolean full)
	{
		List<Integer> expected = ids(inventory); Collections.sort(expected);
		List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
		assertTrue(display.size() <= 28);
		List<Integer> actual = ids(display.stream().filter(i -> i != null).collect(Collectors.toList())); Collections.sort(actual);
		assertEquals(expected, actual);
		if (full) { assertEquals(28, display.size()); assertTrue(display.stream().noneMatch(i -> i == null)); }
		RaidPlan.sortInventory(inventory);
		List<RaidPlan.InventoryItem> reversed = new ArrayList<>(inventory); Collections.reverse(reversed);
		RaidPlan.sortInventory(reversed);
		assertEquals(inventory, reversed);
		assertEquals(display, RaidPlan.inventoryLayout(inventory));
	}

	private static void assertSingleBlock(List<RaidPlan.InventoryItem> display, RaidPlan.InventoryGroup group)
	{
		List<Integer> cells = java.util.stream.IntStream.range(0, display.size())
			.filter(i -> display.get(i) != null && display.get(i).getGroup() == group).boxed().collect(Collectors.toList());
		assertTrue(cells.stream().allMatch(i -> i / 8 == cells.get(0) / 8)
			|| cells.stream().allMatch(i -> i % 4 / 2 == cells.get(0) % 4 / 2));
	}

	private static List<Integer> rowIds(List<RaidPlan.InventoryItem> display, int base, int count)
	{
		return java.util.stream.IntStream.range(0, count).mapToObj(i -> display.get(base + RaidPlan.rowSwitchCell(i)).getIds().get(0))
			.collect(Collectors.toList());
	}

	private static List<RaidPlan.InventoryItem> switchSet(int id, int size, RaidPlan.InventoryGroup group, long wornIn)
	{
		Slot[] slots = {Slot.WEAPON, Slot.CAPE, Slot.HEAD, Slot.BODY, Slot.LEGS, Slot.NECK,
			Slot.HANDS, Slot.FEET, Slot.RING, Slot.AMMO, Slot.SHIELD};
		List<RaidPlan.InventoryItem> set = new ArrayList<>();
		for (int i = 0; i < size; i++) { set.add(gear(id + i, "Switch " + (id + i), group, slots[i], 2, wornIn, null)); }
		return set;
	}

	@Test public void quickWeaponSwitchesDoNotMixTorvaAndMasoriInTheMeleeGroup() throws Exception
	{
		OwnershipRules.init(net.runelite.http.api.RuneLiteAPI.GSON);
		GameData data = new GameDataLoader(net.runelite.http.api.RuneLiteAPI.GSON).loadGameData();
		SetupResult melee = namedSetup(data, CombatClass.MELEE, "scythe of vitur", "torva full helm", "torva platebody",
			"torva platelegs", "amulet of torture", "infernal cape", "primordial boots");
		SetupResult ranged = namedSetup(data, CombatClass.RANGED, "twisted bow", "masori mask", "masori body",
			"masori chaps", "necklace of anguish", "ava's assembler", "pegasian boots");
		SetupResult magic = namedSetup(data, CombatClass.MAGIC, "sanguinesti staff", "ancestral hat",
			"ancestral robe top", "ancestral robe bottom", "occult necklace", "eternal boots");
		Loadout quickMelee = ranged.getLoadout().copy(); quickMelee.set(Slot.WEAPON, melee.getLoadout().getWeapon());
		Loadout quickMagic = ranged.getLoadout().copy(); quickMagic.set(Slot.WEAPON, magic.getLoadout().getWeapon());
		List<SetupResult> setups = Arrays.asList(magic,
			new SetupResult(CombatClass.MELEE, quickMelee, null, 0, 0, Collections.emptySet(), Collections.emptySet(), null, ranged.getLoadout()),
			new SetupResult(CombatClass.MAGIC, quickMagic, null, 0, 0, Collections.emptySet(), Collections.emptySet(), null, ranged.getLoadout()),
			ranged, melee);
		java.util.Map<Integer, GearItem> equipment = new java.util.LinkedHashMap<>();
		setups.forEach(s -> equipment.putAll(RaidPlan.gear(s)));
		List<SearchResults> results = setups.stream().map(s -> new SearchResults(new Monster(), 100,
			Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false))
			.collect(Collectors.toList());
		List<RaidPlan.InventoryItem> inventory = RaidPlan.filledInventory(setups, results, id -> false, new RaidPlan.Memo());
		RaidPlan plan = new RaidPlan(RaidPlanType.TOB, RaidSpellbook.ANCIENT, Collections.emptyList(), results,
			setups, new ArrayList<>(equipment.values()), inventory, 28, 0);
		assertEquals(inventory, plan.inventoryFor(magic));
		assertEquals(14, inventory.size());
		for (int i = 0; i < inventory.size(); i++)
		{
			assertEquals(i < 7 ? RaidPlan.InventoryGroup.MELEE_GEAR : RaidPlan.InventoryGroup.RANGED_GEAR, inventory.get(i).getGroup());
			assertEquals(Integer.valueOf(BestGearSetupBankView.inventoryCell(RaidPlan.inventoryLayout(inventory).indexOf(inventory.get(i)))),
				BestGearSetupBankView.raidCellsFor(plan).get(inventory.get(i).getIds().get(0)));
		}
	}

	@Test public void realMageCapeWornApartStillGroupsWithMageGearAndUsesCatalogueBonuses() throws Exception
	{
		OwnershipRules.init(net.runelite.http.api.RuneLiteAPI.GSON);
		GameData data = new GameDataLoader(net.runelite.http.api.RuneLiteAPI.GSON).loadGameData();
		SetupResult start = setup(CombatClass.MELEE, item(1, Slot.WEAPON, "Starting weapon"));
		SetupResult mage = namedSetup(data, CombatClass.MAGIC, "eye of ayak", "blue moon chestplate", "blue moon tassets",
			"occult necklace", "book of darkness");
		SetupResult capeOnly = namedSetup(data, CombatClass.MAGIC, "imbued saradomin cape");
		List<SetupResult> setups = Arrays.asList(start, mage, capeOnly);
		List<SearchResults> results = setups.stream().map(s -> new SearchResults(new Monster(), 100,
			Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false))
			.collect(Collectors.toList());
		List<RaidPlan.InventoryItem> inventory = RaidPlan.filledInventory(setups, results, id -> false, new RaidPlan.Memo());
		assertEquals(Arrays.asList("eye of ayak", "imbued saradomin cape", "occult necklace", "blue moon chestplate",
			"blue moon tassets", "book of darkness"), java.util.stream.IntStream.range(0, 6)
				.mapToObj(i -> RaidPlan.inventoryLayout(inventory).get(RaidPlan.rowSwitchCell(i)).getName()).collect(Collectors.toList()));
		org.junit.Assert.assertTrue(inventory.stream().allMatch(i -> i.getGroup() == RaidPlan.InventoryGroup.MAGIC_GEAR));
	}

	@Test public void eachCombatStyleOrdersWeaponBackDamageAccuracyThenOffhand()
	{
		for (CombatClass combat : CombatClass.values())
		{
			GearItem weapon = item(10, Slot.WEAPON, "Weapon");
			GearItem cape = rankedItem(11, Slot.CAPE, 0, 0);
			GearItem head = rankedItem(12, Slot.HEAD, 5, 10);
			GearItem body = rankedItem(13, Slot.BODY, 5, 20);
			GearItem neck = rankedItem(14, Slot.NECK, 10, 0);
			GearItem hands = rankedItem(15, Slot.HANDS, 0, 100);
			GearItem shield = rankedItem(16, Slot.SHIELD, 50, 200);
			SetupResult start = setup(combat == CombatClass.MELEE ? CombatClass.MAGIC : CombatClass.MELEE,
				item(1, Slot.WEAPON, "Starting weapon"));
			SetupResult switched = setup(combat, shield, head, weapon, hands, neck, cape, body);
			List<SetupResult> setups = Arrays.asList(start, switched);
			List<SearchResults> results = setups.stream().map(s -> new SearchResults(new Monster(), 100,
				Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false))
				.collect(Collectors.toList());
			List<RaidPlan.InventoryItem> inventory = RaidPlan.filledInventory(setups, results, id -> false, new RaidPlan.Memo());
			assertEquals(combat.name(), Arrays.asList(10, 11, 14, 13, 12, 15, 16), java.util.stream.IntStream.range(0, 7)
				.mapToObj(i -> RaidPlan.inventoryLayout(inventory).get(RaidPlan.rowSwitchCell(i)).getIds().get(0)).collect(Collectors.toList()));
		}
	}

	@Test public void toaMoonArmourAndOccasionalMageBootsFollowTheirFrequentSwitches() throws Exception
	{
		OwnershipRules.init(net.runelite.http.api.RuneLiteAPI.GSON);
		GameData data = new GameDataLoader(net.runelite.http.api.RuneLiteAPI.GSON).loadGameData();
		SetupResult start = namedSetup(data, CombatClass.MELEE, "osmumten's fang", "mixed hide top");
		SetupResult melee = namedSetup(data, CombatClass.MELEE, "abyssal tentacle", "eclipse moon chestplate");
		SetupResult ranged = namedSetup(data, CombatClass.RANGED, "eclipse atlatl", "eclipse moon helm",
			"eclipse moon chestplate", "masori assembler");
		SetupResult magic = namedSetup(data, CombatClass.MAGIC, "trident of the seas (full)", "occult necklace",
			"imbued saradomin cape", "blue moon chestplate", "blue moon tassets", "book of the dead");
		SetupResult occasional = namedSetup(data, CombatClass.MAGIC, "trident of the seas (full)", "eternal boots");
		List<SetupResult> setups = Arrays.asList(start, melee, melee, ranged, ranged, ranged, ranged, ranged,
			ranged, ranged, magic, magic, magic, occasional);
		List<SearchResults> results = setups.stream().map(s -> new SearchResults(new Monster(), 100,
			Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false))
			.collect(Collectors.toList());
		List<RaidPlan.InventoryItem> inventory = RaidPlan.filledInventory(setups, results, id -> false, new RaidPlan.Memo());
		RaidPlan.InventoryItem body = inventory.stream().filter(i -> i.getName().equals("eclipse moon chestplate")).findFirst().get();
		assertEquals(RaidPlan.InventoryGroup.RANGED_GEAR, body.getGroup());
		assertEquals(7, body.getUses());
		List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
		assertEquals(Arrays.asList("eclipse atlatl", "masori assembler", "eclipse moon chestplate", "eclipse moon helm"),
			java.util.stream.IntStream.range(0, 4).mapToObj(i -> display.get(8 + RaidPlan.rowSwitchCell(i)).getName())
				.collect(Collectors.toList()));
		assertEquals(Arrays.asList("trident of the seas (full)", "imbued saradomin cape", "occult necklace",
			"blue moon chestplate", "blue moon tassets", "book of the dead", "eternal boots"),
			java.util.stream.IntStream.range(0, 7).mapToObj(i -> display.get(RaidPlan.rowSwitchCell(i)).getName())
				.collect(Collectors.toList()));
	}

	@Test public void hybridStartingOutfitKeepsItsCarriedMagicArmourTogether() throws Exception
	{
		OwnershipRules.init(net.runelite.http.api.RuneLiteAPI.GSON);
		GameData data = new GameDataLoader(net.runelite.http.api.RuneLiteAPI.GSON).loadGameData();
		SetupResult start = namedSetup(data, CombatClass.MAGIC, "trident of the seas (full)", "eclipse moon helm",
			"eclipse moon chestplate", "eclipse moon tassets", "masori assembler", "book of the dead", "boots of brimstone");
		SetupResult melee = namedSetup(data, CombatClass.MELEE, "osmumten's fang", "blood moon helm",
			"fire cape", "mixed hide top", "dragon defender", "primordial boots");
		SetupResult ranged = namedSetup(data, CombatClass.RANGED, "eclipse atlatl", "eclipse moon helm",
			"eclipse moon chestplate", "eclipse moon tassets", "masori assembler", "primordial boots");
		SetupResult mage = namedSetup(data, CombatClass.MAGIC, "trident of the seas (full)", "blue moon helm",
			"imbued saradomin cape", "occult necklace", "blue moon chestplate", "blue moon tassets", "book of the dead");
		List<SetupResult> setups = Arrays.asList(start, melee, melee, melee, melee, melee, melee,
			ranged, ranged, ranged, ranged, mage, mage, mage);
		List<SearchResults> results = setups.stream().map(s -> new SearchResults(new Monster(), 100,
			Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false))
			.collect(Collectors.toList());
		List<RaidPlan.InventoryItem> inventory = RaidPlan.filledInventory(setups, results, id -> false, new RaidPlan.Memo());
		List<RaidPlan.InventoryItem> cells = RaidPlan.inventoryLayout(inventory);
		assertEquals(12, inventory.size());
		assertEquals(Arrays.asList("eclipse atlatl", "imbued saradomin cape", "occult necklace", "blue moon chestplate",
			"blue moon tassets", "blue moon helm"), java.util.stream.IntStream.range(0, 6)
				.mapToObj(i -> cells.get(8 + RaidPlan.rowSwitchCell(i)).getName()).collect(Collectors.toList()));
		assertEquals("osmumten's fang", cells.get(0).getName());
		assertEquals("fire cape", cells.get(4).getName());
		org.junit.Assert.assertFalse(inventory.stream().anyMatch(i -> i.getName().equals("trident of the seas (full)")));
		// Attack class alone must not change the layout of an identical worn outfit.
		List<SetupResult> relabelled = new ArrayList<>(setups);
		relabelled.set(0, new SetupResult(CombatClass.RANGED, start.getLoadout(), null, 0));
		assertEquals(ids(inventory), ids(RaidPlan.filledInventory(relabelled, results, id -> false, new RaidPlan.Memo())));
	}

	@Test public void everyHybridPairUsesTheWornArmoursWeaponAsAStandIn() throws Exception
	{
		for (CombatClass weaponStyle : CombatClass.values())
		{
			for (CombatClass armourStyle : CombatClass.values())
			{
				if (weaponStyle == armourStyle) { continue; }
				GearItem equipped = item(10, Slot.WEAPON, "Equipped weapon");
				GearItem standIn = item(20, Slot.WEAPON, "Armour style main weapon");
				GearItem alternate = item(21, Slot.WEAPON, "Armour style spare weapon");
				GearItem wornBody = item(30, Slot.BODY, "Worn body");
				GearItem wornLegs = item(31, Slot.LEGS, "Worn legs");
				SetupResult start = setup(weaponStyle, equipped, wornBody, wornLegs);
				SetupResult armour = setup(armourStyle, standIn, wornBody, wornLegs);
				SetupResult spare = setup(armourStyle, alternate, wornBody, wornLegs);
				SetupResult target = setup(weaponStyle, equipped, item(40, Slot.CAPE, "Switch cape"),
					item(41, Slot.BODY, "Switch body"), item(42, Slot.LEGS, "Switch legs"));
				List<SetupResult> setups = Arrays.asList(start, armour, armour, armour, armour, spare, target, target);
				List<SearchResults> results = setups.stream().map(s -> new SearchResults(new Monster(), 100,
					Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false))
					.collect(Collectors.toList());
				List<RaidPlan.InventoryItem> carried = RaidPlan.filledInventory(setups, results, id -> false, new RaidPlan.Memo());
				List<RaidPlan.InventoryItem> cells = RaidPlan.inventoryLayout(carried);
				String pairing = weaponStyle + " weapon / " + armourStyle + " armour";
				assertEquals(pairing, Arrays.asList(20, 40, 41, 42), java.util.stream.IntStream.range(0, 4)
					.mapToObj(i -> cells.get(RaidPlan.rowSwitchCell(i)).getIds().get(0)).collect(Collectors.toList()));
				assertEquals(pairing, 5, carried.size());
				assertEquals(pairing, 5, ids(carried).stream().distinct().count());
				assertEquals(pairing, combatInventoryGroup(armourStyle), cells.get(0).getGroup());
				assertEquals(pairing, combatInventoryGroup(weaponStyle), cells.get(0).getWeaponStandInFor());
				SearchResultCodec codec = new SearchResultCodec(net.runelite.http.api.RuneLiteAPI.GSON);
				assertEquals(pairing, cells.get(0), codec.decode(codec.encode(cells.get(0)), RaidPlan.InventoryItem.class));
				RaidPlan.sortInventory(carried);
				assertEquals(pairing, cells, RaidPlan.inventoryLayout(carried));
				java.util.Map<Integer, GearItem> equipment = new java.util.LinkedHashMap<>();
				setups.forEach(s -> equipment.putAll(RaidPlan.gear(s)));
				RaidPlan plan = new RaidPlan(RaidPlanType.TOA, RaidSpellbook.ARCEUUS, Collections.emptyList(), results,
					setups, new ArrayList<>(equipment.values()), carried, 16, 0);
				assertEquals(pairing, carried, plan.inventoryFor(start));
				assertEquals(pairing, Integer.valueOf(BestGearSetupBankView.inventoryCell(0)),
					BestGearSetupBankView.raidCellsFor(plan).get(20));
				// A complete matching outfit needs no borrowed weapon position.
				assertTrue(plan.inventoryFor(target).stream().allMatch(i -> i.getWeaponStandInFor() == null));
				List<SetupResult> withoutWeapon = Arrays.asList(start, target);
				assertTrue(RaidPlan.filledInventory(withoutWeapon, results.subList(0, 2), id -> false, new RaidPlan.Memo())
					.stream().allMatch(i -> i.getWeaponStandInFor() == null));
			}
		}
	}

	private static RaidPlan.InventoryGroup combatInventoryGroup(CombatClass combat)
	{
		return combat == CombatClass.MELEE ? RaidPlan.InventoryGroup.MELEE_GEAR
			: combat == CombatClass.RANGED ? RaidPlan.InventoryGroup.RANGED_GEAR : RaidPlan.InventoryGroup.MAGIC_GEAR;
	}

	private static GearItem rankedItem(int id, Slot slot, int damage, int accuracy)
	{
		GearItem item = item(id, slot, "Gear " + id);
		item.setMeleeStr(damage); item.setRangedStr(damage); item.setMagicStr(damage);
		item.setStabBonus(accuracy); item.setSlashBonus(accuracy); item.setCrushBonus(accuracy);
		item.setRangedBonus(accuracy); item.setMagicBonus(accuracy);
		return item;
	}

	private static long bits(int... encounters)
	{
		long bits = 0;
		for (int encounter : encounters) { bits |= 1L << encounter; }
		return bits;
	}

	private static RaidPlan.InventoryItem gear(int id, String name, RaidPlan.InventoryGroup group, Slot slot, int uses)
	{
		return gear(id, name, group, slot, uses, 0, null);
	}

	private static RaidPlan.InventoryItem gear(int id, String name, RaidPlan.InventoryGroup group, Slot slot, int uses,
		long wornIn, CombatClass specStyle)
	{
		return new RaidPlan.InventoryItem(name, Collections.singletonList(id), true, Collections.emptyList(), group, 0, 0,
			false, slot, uses, wornIn, specStyle);
	}

	/** A rune pouch's item id. */
	private static final class SpellRunesIds
	{
		static final int POUCH = net.runelite.api.gameval.ItemID.BH_RUNE_POUCH;
	}

	@Test public void suppliesKeepDuplicateBottlesTogetherAfterGearAndTools()
	{
		RaidPlan.InventoryItem rune = entry(554, "Fire rune", RaidPlan.InventoryGroup.RUNES);
		RaidPlan.InventoryItem restore = entry(3024, "Super restore", RaidPlan.InventoryGroup.BOOSTS);
		RaidPlan.InventoryItem combat = new RaidPlan.InventoryItem("Super combat", Collections.singletonList(12695),
			false, Collections.emptyList(), RaidPlan.InventoryGroup.BOOSTS, 5, 2, false);
		RaidPlan.InventoryItem tool = entry(600, "Pickaxe", RaidPlan.InventoryGroup.OTHER);
		RaidPlan.InventoryItem special = entry(500, "Special weapon", RaidPlan.InventoryGroup.SPECIAL);
		List<RaidPlan.InventoryItem> inventory = new ArrayList<>(Arrays.asList(rune, restore, combat, tool, combat, special));
		RaidPlan.sortInventory(inventory);
		// Without switches there is no row to fill, so the rune stack goes last.
		assertEquals(Arrays.asList(500, 600, 12695, 12695, 3024, 554), ids(inventory));
		assertEquals(5, inventory.get(2).getEstimatedDoses());
		assertEquals(2, inventory.get(3).getBottles());
	}

	private static RaidPlan.InventoryItem entry(int id, String name, RaidPlan.InventoryGroup group)
	{
		return new RaidPlan.InventoryItem(name, Collections.singletonList(id), false, Collections.emptyList(), group);
	}

	private static List<Integer> ids(List<RaidPlan.InventoryItem> inventory)
	{
		return inventory.stream().map(i -> i.getIds().get(0)).collect(Collectors.toList());
	}

	private static SetupResult setup(CombatClass combat, GearItem... items)
	{
		Loadout loadout = new Loadout();
		for (GearItem item : items) { loadout.set(item.getSlot(), item); }
		return new SetupResult(combat, loadout, null, 0);
	}

	private static SetupResult namedSetup(GameData data, CombatClass combat, String... names)
	{
		List<GearItem> gear = new ArrayList<>();
		for (String name : names)
		{
			gear.add(Arrays.stream(Slot.values()).flatMap(slot -> data.getItems(slot).stream())
				.filter(item -> item.getName().equals(name)).findFirst().orElseThrow(() -> new AssertionError("Missing " + name)));
		}
		return setup(combat, gear.toArray(new GearItem[0]));
	}

	private static GearItem item(int id, Slot slot, String name)
	{
		GearItem item = new GearItem();
		item.setId(id); item.setSlot(slot); item.setName(name);
		return item;
	}
}
