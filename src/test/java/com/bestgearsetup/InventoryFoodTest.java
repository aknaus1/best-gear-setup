package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class InventoryFoodTest
{
	@Test public void picksTheBestHealingOwnedFoodElseSharks()
	{
		Set<Integer> owned = Set.of(ItemID.LOBSTER, ItemID.MANTARAY, ItemID.SHARK);
		assertEquals(ItemID.MANTARAY, InventoryFood.choose(owned::contains).getId());
		assertEquals(ItemID.TBWT_COOKED_KARAMBWAN, InventoryFood.choose(id -> id == ItemID.TBWT_COOKED_KARAMBWAN).getId());
		assertEquals("Nothing owned, or ownership not yet known", ItemID.SHARK, InventoryFood.choose(id -> false).getId());
		for (int i = 1; i < InventoryFood.FOODS.size(); i++)
		{
			assertTrue(InventoryFood.FOODS.get(i - 1).getHeals() >= InventoryFood.FOODS.get(i).getHeals());
		}
	}

	@Test public void fillsOnlyTheSlotsTheRestOfTheInventoryLeaves()
	{
		List<RaidPlan.InventoryItem> carried = new ArrayList<>();
		for (int i = 0; i < 5; i++) { carried.add(supply(1000 + i)); }
		// A pouch's runes take no slot of their own.
		carried.add(new RaidPlan.InventoryItem("Rune pouch", Collections.singletonList(ItemID.BH_RUNE_POUCH), false,
			Collections.singletonList(supply(ItemID.FIRERUNE))));
		List<RaidPlan.InventoryItem> filled = InventoryFood.fill(carried, id -> id == ItemID.ANGLERFISH);
		assertEquals(28, filled.size());
		assertEquals(carried, filled.subList(0, carried.size()));
		assertTrue(filled.subList(carried.size(), 28).stream().allMatch(item -> item.getGroup() == RaidPlan.InventoryGroup.FOOD
			&& item.getIds().equals(Collections.singletonList(ItemID.ANGLERFISH))));
		assertEquals("The caller's list is left alone", 6, carried.size());

		List<RaidPlan.InventoryItem> full = new ArrayList<>(Collections.nCopies(28, supply(1000)));
		assertSame(full, InventoryFood.fill(full, id -> true));
		List<RaidPlan.InventoryItem> over = new ArrayList<>(Collections.nCopies(30, supply(1000)));
		assertSame(over, InventoryFood.fill(over, id -> true));
	}

	@Test public void foodTakesTheCellsLeftAfterGearSuppliesAndBoosts()
	{
		List<RaidPlan.InventoryItem> carried = new ArrayList<>();
		for (Slot slot : Arrays.asList(Slot.WEAPON, Slot.BODY, Slot.LEGS))
		{
			carried.add(new RaidPlan.InventoryItem("Melee " + slot, Collections.singletonList(2000 + slot.ordinal()), true,
				Collections.emptyList(), RaidPlan.InventoryGroup.MELEE_GEAR, 0, 0, false, slot, 1));
		}
		carried.add(new RaidPlan.InventoryItem("Super combat potion", Collections.singletonList(ItemID._4DOSE2COMBAT), false,
			Collections.emptyList(), RaidPlan.InventoryGroup.BOOSTS));
		carried.add(new RaidPlan.InventoryItem("Rune pouch", Collections.singletonList(ItemID.BH_RUNE_POUCH), false,
			Collections.singletonList(supply(ItemID.FIRERUNE))));
		List<RaidPlan.InventoryItem> before = RaidPlan.inventoryLayout(carried);
		List<RaidPlan.InventoryItem> after = RaidPlan.inventoryLayout(InventoryFood.fill(carried, id -> false));
		assertEquals(28, after.size());
		for (int cell = 0; cell < 28; cell++)
		{
			RaidPlan.InventoryItem item = cell < before.size() ? before.get(cell) : null;
			if (item == null) { assertEquals(RaidPlan.InventoryGroup.FOOD, after.get(cell).getGroup()); }
			else { assertSame("Food never moves another item", item, after.get(cell)); }
		}

		// Supplies alone (no switch block) list food after them.
		List<RaidPlan.InventoryItem> supplies = carried.subList(3, 5);
		List<RaidPlan.InventoryItem> packed = RaidPlan.inventoryLayout(InventoryFood.fill(supplies, id -> false));
		assertEquals(28, packed.size());
		assertEquals(RaidPlan.inventoryLayout(supplies), packed.subList(0, 2));
		assertEquals(ItemID.SHARK, (int) packed.get(27).getIds().get(0));
	}

	@Test public void bankLayoutDrawsFoodInEveryFreeInventoryCellAndOutlinesIt()
	{
		Loadout loadout = new Loadout();
		GearItem weapon = new GearItem(); weapon.setId(ItemID.ABYSSAL_WHIP);
		loadout.set(Slot.WEAPON, weapon);
		SetupResult setup = new SetupResult(CombatClass.MELEE, loadout, null, 0);
		Potion potion = new Potion(); potion.setId(ItemID._4DOSE2COMBAT);
		List<Potion> potions = Collections.singletonList(potion);
		BestGearSetupBankView.Layout plain = BestGearSetupBankView.layoutFor(setup, potions, BestGearSetupBankView::itemIds,
			id -> id == ItemID.DARK_CRAB);
		assertTrue("Off by default for callers that don't ask", BestGearSetupBankView.allCells(plain, ItemID.DARK_CRAB).isEmpty());

		BestGearSetupBankView.Layout layout = BestGearSetupBankView.layoutFor(setup, potions, BestGearSetupBankView::itemIds,
			id -> id == ItemID.DARK_CRAB, true);
		List<RaidPlan.InventoryItem> grid = RaidPlan.inventoryLayout(InventoryFood.fill(
			RaidPlan.inventoryForSetup(setup, potions, id -> id == ItemID.DARK_CRAB), id -> id == ItemID.DARK_CRAB));
		List<Integer> expected = new ArrayList<>();
		for (int i = 0; i < grid.size(); i++)
		{
			if (grid.get(i).getGroup() == RaidPlan.InventoryGroup.FOOD) { expected.add(BestGearSetupBankView.inventoryCell(i)); }
		}
		assertEquals(27, expected.size());
		assertEquals(expected, BestGearSetupBankView.allCells(layout, ItemID.DARK_CRAB));
		// The potion keeps its cell beside the food.
		assertEquals(Collections.singletonList(BestGearSetupBankView.inventoryCell(0)),
			BestGearSetupBankView.allCells(layout, ItemID._4DOSE2COMBAT));

		BestGearSetupBankView view = new BestGearSetupBankView(null, null, null, null, null);
		view.setSelection(setup, potions, BestGearSetupBankView::itemIds, id -> false, Collections.emptyList(), true);
		assertEquals(Set.of(ItemID.SHARK), view.selectedFoodIds());
		view.setSelection(setup, potions, BestGearSetupBankView::itemIds, id -> false, Collections.emptyList(), false);
		assertTrue(view.selectedFoodIds().isEmpty());
		view.setSelection(null, potions, BestGearSetupBankView::itemIds, id -> false, Collections.emptyList(), true);
		assertTrue(view.selectedFoodIds().isEmpty());
	}

	@Test public void onlyIndividualNonRaidSearchesFillFoodAndTheSettingTurnsItOff()
	{
		boolean[] enabled = {true};
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		plugin.config = new BestGearSetupConfig() { @Override public boolean fillFood() { return enabled[0]; } };
		Monster boss = new Monster(); boss.setName("Vorkath");
		SearchResults vorkath = mock(SearchResults.class);
		when(vorkath.getMonster()).thenReturn(boss);
		assertTrue(new BestGearSetupConfig() { }.fillFood());
		assertTrue(plugin.fillsFood(vorkath));
		// Raid encounters keep the raid's own supply rules.
		Monster olm = mock(Monster.class);
		when(olm.hasAttribute("xerician")).thenReturn(true);
		SearchResults raid = mock(SearchResults.class);
		when(raid.getMonster()).thenReturn(olm);
		assertFalse(plugin.fillsFood(raid));
		assertFalse(plugin.fillsFood(null));
		enabled[0] = false;
		assertFalse(plugin.fillsFood(vorkath));
	}

	private static RaidPlan.InventoryItem supply(int id)
	{
		return new RaidPlan.InventoryItem("Supply " + id, Collections.singletonList(id), false);
	}
}
