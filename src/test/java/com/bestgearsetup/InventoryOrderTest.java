package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
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
	@Test public void encounterAndEquipmentTraversalCannotShuffleCombatSwitches()
	{
		SetupResult start = setup(CombatClass.MELEE, item(1, Slot.WEAPON, "Starting weapon"));
		SetupResult melee = setup(CombatClass.MELEE, item(102, Slot.WEAPON, "Zamorak weapon"),
			item(103, Slot.SHIELD, "Offhand"), item(104, Slot.HEAD, "Head"), item(105, Slot.BODY, "Body"),
			item(106, Slot.LEGS, "Legs"), item(107, Slot.CAPE, "Cape"), item(108, Slot.NECK, "Neck"),
			item(109, Slot.HANDS, "Gloves"), item(110, Slot.FEET, "Boots"), item(111, Slot.RING, "Ring"),
			item(112, Slot.AMMO, "Ammo"));
		SetupResult otherMelee = setup(CombatClass.MELEE, item(101, Slot.WEAPON, "Armadyl weapon"));
		SetupResult ranged = setup(CombatClass.RANGED, item(201, Slot.WEAPON, "Bow"), item(202, Slot.BODY, "Hide"));
		SetupResult magic = setup(CombatClass.MAGIC, item(301, Slot.WEAPON, "Staff"), item(302, Slot.BODY, "Robe"));
		List<SetupResult> setups = Arrays.asList(start, magic, melee, ranged, otherMelee);
		for (int rotation = 0; rotation < 4; rotation++)
		{
			List<SetupResult> reordered = new ArrayList<>(setups.subList(1, setups.size()));
			Collections.rotate(reordered, rotation);
			reordered.add(0, start);
			// Two-row switch blocks filled down each column: the eleven-piece melee set fills the first block and
			// two columns of the next with the other melee weapon after it, then magic and ranged take a column each,
			// in prayer-book order whatever the encounter order.
			List<Integer> expected = Arrays.asList(102, 104, 106, 108, 103, 105, 107, 109, 110, 112, 301, 201,
				111, 101, 302, 202);
			List<SearchResults> results = reordered.stream().map(s -> new SearchResults(new Monster(), 100,
				Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false))
				.collect(Collectors.toList());
			List<RaidPlan.InventoryItem> inventory = RaidPlan.filledInventory(reordered, results, id -> false, new RaidPlan.Memo());
			assertEquals(expected, ids(inventory));
			List<GearItem> equipment = new ArrayList<>();
			reordered.forEach(s -> equipment.addAll(RaidPlan.gear(s).values()));
			Collections.reverse(equipment);
			RaidPlan plan = new RaidPlan(RaidPlanType.TOB, RaidSpellbook.ANCIENT, Collections.emptyList(), results,
				reordered, equipment, inventory, 28, 0);
			assertEquals(inventory, plan.inventoryFor(start));
			for (int i = 0; i < expected.size(); i++)
			{
				assertEquals(Integer.valueOf(BestGearSetupBankView.inventoryCell(i)),
					BestGearSetupBankView.raidCellsFor(plan).get(expected.get(i)));
			}
			List<RaidPlan.InventoryItem> switched = plan.inventoryFor(magic);
			List<RaidPlan.InventoryItem> rebuilt = new ArrayList<>(switched);
			Collections.reverse(rebuilt);
			RaidPlan.sortInventory(rebuilt);
			assertEquals(switched, rebuilt);
		}
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
			assertEquals(Integer.valueOf(BestGearSetupBankView.inventoryCell(i)),
				BestGearSetupBankView.raidCellsFor(plan).get(inventory.get(i).getIds().get(0)));
		}
	}

	@Test public void mainSwitchLeadsThePouchFillsItsRowAndSpecsThenOneOffsGoAtTheBottom()
	{
		List<RaidPlan.InventoryItem> inventory = new ArrayList<>(Arrays.asList(
			entry(90, "Super combat", RaidPlan.InventoryGroup.BOOSTS),
			gear(1, "Salve amulet", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.NECK, 1),
			entry(80, "Dragon claws", RaidPlan.InventoryGroup.SPECIAL),
			entry(81, "Dragon warhammer", RaidPlan.InventoryGroup.SPECIAL),
			entry(70, "Pickaxe", RaidPlan.InventoryGroup.OTHER),
			entry(SpellRunesIds.POUCH, "Rune pouch", RaidPlan.InventoryGroup.RUNES),
			gear(10, "Bow", RaidPlan.InventoryGroup.RANGED_GEAR, Slot.WEAPON, 2),
			gear(11, "Ranged body", RaidPlan.InventoryGroup.RANGED_GEAR, Slot.BODY, 2),
			gear(20, "Staff", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.WEAPON, 3),
			gear(21, "Ward", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.SHIELD, 3),
			gear(22, "Hat", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.HEAD, 3),
			gear(23, "Robe top", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.BODY, 3),
			gear(24, "Robe bottom", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.LEGS, 2)));
		RaidPlan.sortInventory(inventory);
		// Magic is the main switch, down the first columns of a two-row block; ranged starts the next column, a
		// vertical pair, and the pouch takes the cell magic left free. The specs and then the pickaxe start the third
		// row; then the salve only one encounter wears, and the potion at the bottom.
		assertEquals(Arrays.asList(20, 22, 24, 10, 21, 23, SpellRunesIds.POUCH, 11, 80, 81, 70, 1, 90), ids(inventory));
		// With no cell left, the pouch completes the specs' row rather than pushing them off it.
		inventory.add(gear(25, "Boots", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.FEET, 3));
		RaidPlan.sortInventory(inventory);
		assertEquals(Arrays.asList(20, 22, 24, 10, 21, 23, 25, 11, 80, 81, SpellRunesIds.POUCH, 70, 1, 90), ids(inventory));
		// A second melee piece makes melee a switch of its own rather than a one-off, and the pouch fills a cell again.
		inventory.add(gear(2, "Melee ring", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.RING, 1));
		RaidPlan.sortInventory(inventory);
		// Ranged comes before melee in prayer-book order.
		assertEquals(Arrays.asList(20, 22, 24, 10, 21, 23, 25, 11, 1, SpellRunesIds.POUCH, 80, 81, 2, 70, 90), ids(inventory));
	}

	@Test public void fourPieceSetFormsASquareBesideAPairWithSpellSuppliesCompletingTheRows()
	{
		// A ToB mage kit worn in: a Blood Moon melee switch, an Eye of Ayak and gloves for magic, and a lone salve.
		List<RaidPlan.InventoryItem> inventory = new ArrayList<>(Arrays.asList(
			entry(90, "Saradomin brew", RaidPlan.InventoryGroup.BOOSTS),
			entry(91, "Super restore", RaidPlan.InventoryGroup.BOOSTS),
			gear(1, "Salve amulet(ei)", RaidPlan.InventoryGroup.RANGED_GEAR, Slot.NECK, 3),
			entry(80, "Dragon claws", RaidPlan.InventoryGroup.SPECIAL),
			entry(81, "Elder maul", RaidPlan.InventoryGroup.SPECIAL),
			entry(82, "Voidwaker", RaidPlan.InventoryGroup.SPECIAL),
			entry(83, "Zaryte crossbow", RaidPlan.InventoryGroup.SPECIAL),
			entry(SpellRunesIds.POUCH, "Rune pouch", RaidPlan.InventoryGroup.RUNES),
			entry(net.runelite.api.gameval.ItemID.BOOK_OF_THE_DEAD, "Book of the dead", RaidPlan.InventoryGroup.OTHER),
			gear(20, "Eye of ayak", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.WEAPON, 2),
			gear(21, "Confliction gauntlets", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.HANDS, 2),
			gear(10, "Dual macuahuitl", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.WEAPON, 3),
			gear(11, "Blood moon helm", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.HEAD, 3),
			gear(12, "Blood moon chestguard", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.BODY, 3),
			gear(13, "Blood moon tassets", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.LEGS, 3)));
		RaidPlan.sortInventory(inventory);
		// Blood Moon in slots 1, 2, 5 and 6; the magic switch in 3 and 7; the pouch and book end the top rows, so all
		// four specs share the third row, with the salve and then the potions below.
		assertEquals(Arrays.asList(10, 12, 20, SpellRunesIds.POUCH, 11, 13, 21, net.runelite.api.gameval.ItemID.BOOK_OF_THE_DEAD,
			80, 81, 82, 83, 1, 90, 91), ids(inventory));
	}

	@Test public void tobMeleeDpsKitMatchesThePlayersLayout()
	{
		// The planned ToB melee DPS kit for a saved profile. Encounters: Maiden 0, Maiden's nylocas 1, Bloat 2,
		// Nylocas melee 3, magic 4 and ranged 5, Verzik 8 and 9.
		int book = net.runelite.api.gameval.ItemID.BOOK_OF_THE_DEAD;
		List<RaidPlan.InventoryItem> inventory = new ArrayList<>(Arrays.asList(
			gear(10, "Dual macuahuitl", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.WEAPON, 2, bits(3, 8), null),
			gear(12, "Blood moon chestplate", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.BODY, 2, bits(3, 8), null),
			// Swapped into the worn gear at Bloat, which is undead, never alongside the Blood Moon set.
			gear(1, "Salve amulet (e)", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.NECK, 1, bits(2), null),
			gear(30, "Toxic blowpipe", RaidPlan.InventoryGroup.RANGED_GEAR, Slot.WEAPON, 2, bits(1, 9), null),
			gear(11, "Blood moon helm", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.HEAD, 2, bits(3, 8), null),
			gear(13, "Blood moon tassets", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.LEGS, 2, bits(3, 8), null),
			entry(SpellRunesIds.POUCH, "Divine rune pouch", RaidPlan.InventoryGroup.RUNES),
			gear(31, "Masori body (f)", RaidPlan.InventoryGroup.RANGED_GEAR, Slot.BODY, 2, bits(1, 9), null),
			gear(20, "Eye of ayak", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.WEAPON, 1, bits(4), null),
			entry(book, "Book of the dead", RaidPlan.InventoryGroup.OTHER),
			gear(80, "Dragon claws", RaidPlan.InventoryGroup.SPECIAL, Slot.WEAPON, 6, bits(0, 2, 3, 6, 7, 8), CombatClass.MELEE),
			gear(81, "Zaryte crossbow", RaidPlan.InventoryGroup.SPECIAL, Slot.WEAPON, 1, bits(5), CombatClass.RANGED),
			gear(21, "Confliction gauntlets", RaidPlan.InventoryGroup.MAGIC_GEAR, Slot.HANDS, 1, bits(4), null),
			entry(90, "Super combat potion", RaidPlan.InventoryGroup.BOOSTS)));
		RaidPlan.sortInventory(inventory);
		// Blood Moon as a square, then the magic and ranged pairs in prayer-book order; the ranged spec before the
		// melee one, then the book and the pouch at the row's end; the salve and the potion below.
		assertEquals(Arrays.asList(10, 12, 20, 30, 11, 13, 21, 31, 81, 80, book, SpellRunesIds.POUCH, 1, 90), ids(inventory));
	}

	@Test public void twoSetsOfOneStyleStayApartInTheirOwnColumns()
	{
		// A solo kit carrying Blood Moon for the nylocas waves and Oathplate for the bosses, sharing gloves and a cape.
		List<RaidPlan.InventoryItem> inventory = new ArrayList<>(Arrays.asList(
			gear(10, "Dual macuahuitl", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.WEAPON, 2, bits(3, 4), null),
			gear(11, "Blood moon helm", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.HEAD, 2, bits(3, 4), null),
			gear(12, "Blood moon chestplate", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.BODY, 2, bits(3, 4), null),
			gear(13, "Blood moon tassets", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.LEGS, 2, bits(3, 4), null),
			gear(20, "Oathplate helm", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.HEAD, 3, bits(0, 2, 6), null),
			gear(21, "Oathplate chest", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.BODY, 3, bits(0, 2, 6), null),
			gear(22, "Oathplate legs", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.LEGS, 3, bits(0, 2, 6), null),
			gear(30, "Infernal cape", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.CAPE, 5, bits(0, 2, 3, 4, 6), null),
			gear(31, "Ferocious gloves", RaidPlan.InventoryGroup.MELEE_GEAR, Slot.HANDS, 5, bits(0, 2, 3, 4, 6), null),
			entry(SpellRunesIds.POUCH, "Rune pouch", RaidPlan.InventoryGroup.RUNES)));
		RaidPlan.sortInventory(inventory);
		// Blood Moon as a square, Oathplate down the next column and a half, the pouch in the cell Oathplate leaves
		// free, and the shared cape and gloves on the next row, with nothing else to fill that block's columns.
		assertEquals(Arrays.asList(10, 12, 20, 22, 11, 13, 21, SpellRunesIds.POUCH, 30, 31), ids(inventory));
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

	@Test public void gearFillingAnotherStylesEmptySlotKeepsItsCombatGroup()
	{
		GearItem hide = item(11, Slot.BODY, "Ranged body");
		SetupResult melee = setup(CombatClass.MELEE, item(1, Slot.WEAPON, "Sword"), hide)
			.withFilledSlots(Collections.singleton(Slot.BODY));
		SetupResult ranged = setup(CombatClass.RANGED, item(10, Slot.WEAPON, "Bow"), hide);
		SetupResult magic = setup(CombatClass.MAGIC, item(20, Slot.WEAPON, "Staff"), item(21, Slot.BODY, "Robe"));
		List<SetupResult> setups = Arrays.asList(melee, ranged, magic);
		List<GearItem> equipment = Arrays.asList(melee.getLoadout().getWeapon(), hide, ranged.getLoadout().getWeapon(),
			magic.getLoadout().getWeapon(), magic.getLoadout().get(Slot.BODY));
		RaidPlan plan = new RaidPlan(RaidPlanType.TOB, RaidSpellbook.ANCIENT, Collections.emptyList(), Collections.emptyList(),
			setups, equipment, Collections.emptyList(), 28, 0);
		List<RaidPlan.InventoryItem> carried = plan.inventoryFor(magic);
		// Ranged is the main switch; the lone sword only one encounter uses is a one-off below it.
		assertEquals(Arrays.asList(10, 11, 1), ids(carried));
		assertEquals(RaidPlan.InventoryGroup.RANGED_GEAR, carried.get(1).getGroup());
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
