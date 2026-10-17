package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Point;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.stubbing.Answer;

public class BankViewTest
{
	@Test public void specialSwitchesAndSuppliesShareTheInventoryCells()
	{
		GearItem weapon = item(12926);
		SetupResult base = setup(Slot.WEAPON, weapon);
		Potion potion = new Potion(); potion.setId(ItemID._4DOSE2COMBAT);
		for (GearItem specialWeapon : Arrays.asList(weapon, item(4153)))
		{
			com.bestgearsetup.calc.SpecialPlan special = new com.bestgearsetup.calc.SpecialPlan(specialWeapon,
				null, null, null, null, null, null, null, null, null, 1, 0, 0);
			SetupResult selected = new SetupResult(CombatClass.MELEE, base.getLoadout(), null, 0, 0,
				Collections.emptySet(), Collections.emptySet(), special);
			Map<Integer, Integer> cells = BestGearSetupBankView.cellsFor(selected, Collections.singletonList(potion));
			// Without a switch the potion takes the first inventory cell; a spec weapon switch comes first.
			assertEquals(Integer.valueOf(specialWeapon == weapon ? 4 : 5), cells.get(ItemID._4DOSE2COMBAT));
			if (specialWeapon != weapon) { assertEquals(Integer.valueOf(4), cells.get(4153)); }
		}
		// Loaded ammunition joins the loading rows below the inventory, as in a raid kit.
		base.getLoadout().setLoadedAmmo(item(11230));
		assertEquals(Integer.valueOf(56), BestGearSetupBankView.cellsFor(base).get(11230));
	}

	@Test public void searchWithoutSwitchesMirrorsThePanelsInventoryCells()
	{
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, item(12926));
		Spell spell = new Spell();
		spell.setName("wind strike");
		loadout.setSpell(spell);
		SetupResult setup = new SetupResult(CombatClass.MAGIC, loadout, null, 0);
		Potion attack = new Potion();
		attack.setId(ItemID._4DOSE2ATTACK);
		List<Potion> potions = Collections.singletonList(attack);
		List<RaidPlan.InventoryItem> carried = RaidPlan.inventoryForSetup(setup, potions, id -> false);
		assertFalse(carried.isEmpty());
		assertTrue(carried.stream().noneMatch(RaidPlan.InventoryItem::isEquipment));
		// Every carried supply takes the bank cell of its slot in the panel's inventory grid.
		Map<Integer, Integer> cells = BestGearSetupBankView.cellsFor(setup, potions);
		List<RaidPlan.InventoryItem> grid = RaidPlan.inventoryLayout(carried);
		for (int i = 0; i < grid.size(); i++)
		{
			assertEquals(Integer.valueOf(BestGearSetupBankView.inventoryCell(i)), cells.get(grid.get(i).getIds().get(0)));
		}
	}

	@Test public void raidBoostCountGuidanceReservesBottleSlotsAndPreservesRealStack() throws Exception
	{
		Fixture bank = new Fixture(ItemID._4DOSE2COMBAT, 200);
		SetupResult setup = setup(Slot.WEAPON, item(12926));
		RaidPlan plan = mock(RaidPlan.class);
		when(plan.getStartingSetup()).thenReturn(setup);
		when(plan.getSetups()).thenReturn(Collections.singletonList(setup));
		when(plan.getEquipment()).thenReturn(Collections.singletonList(item(12926)));
		RaidPlan.InventoryItem bottle = new RaidPlan.InventoryItem("Super combat potion", Collections.singletonList(ItemID._4DOSE2COMBAT),
			false, Collections.emptyList(), RaidPlan.InventoryGroup.BOOSTS, 5, 2, false);
		when(plan.getInventory()).thenReturn(Arrays.asList(bottle, bottle,
			new RaidPlan.InventoryItem("Rune", Collections.singletonList(200), false)));
		bank.items[4].widget.setName("Original potion");
		bank.items[4].widget.setAction(0, "Withdraw-1");
		bank.items[4].widget.setAction(1, "Examine");
		bank.view.setRaidSelection(plan, BestGearSetupBankView::itemIds);
		assertTrue(bank.view.show());
		assertTrue(bank.view.boostGuidance(ItemID._4DOSE2COMBAT).contains("Bring 2 bottles (~5 doses)"));
		// Supplies alone still take inventory cells: the rune first, then the potion bottles.
		assertEquals(291, bank.items[4].value("OriginalX"));
		assertEquals(243, bank.items[5].value("OriginalX"));
		assertEquals(0, bank.items[4].value("OriginalY"));
		assertEquals(0, bank.items[5].value("OriginalY"));
		assertEquals("Original potion", bank.items[4].widget.getName());
		assertEquals("Withdraw-1", bank.items[4].values.get("Action0"));
		assertEquals(1, bank.items[4].widget.getItemQuantity()); assertEquals(4, bank.items[4].value("Index"));
		// The repeated potion entry is drawn in the bank's first spare item slot; no widgets are created.
		assertEquals(bank.items.length + Fixture.SPARE_SLOTS + 1, bank.container.widget.getChildren().length);
		Widget duplicate = bank.spares[0].widget;
		int spareSlot = bank.spares[0].value("Index");
		assertEquals(ItemID._4DOSE2COMBAT, duplicate.getItemId());
		assertFalse(duplicate.isSelfHidden());
		assertEquals(Arrays.asList("Withdraw-1", "Examine"), Arrays.asList(duplicate.getActions()));
		assertTrue(duplicate.getOriginalX() != bank.items[4].value("OriginalX")
			|| duplicate.getOriginalY() != bank.items[4].value("OriginalY"));
		// The game builds the spare slot's menu; a click is redirected to the real stack's bank slot.
		MenuEntry withdraw = mock(MenuEntry.class);
		when(withdraw.getOption()).thenReturn("Withdraw-1");
		when(withdraw.getParam0()).thenReturn(spareSlot);
		when(withdraw.getParam1()).thenReturn(InterfaceID.Bankmain.ITEMS);
		bank.view.onMenuOptionClicked(new MenuOptionClicked(withdraw));
		verify(withdraw).setParam0(4);
		bank.view.clear();
		assertEquals(null, bank.view.boostGuidance(ItemID._4DOSE2COMBAT));
		assertEquals("Original potion", bank.items[4].widget.getName());
		assertTrue(duplicate.isSelfHidden());
		assertEquals(-1, duplicate.getItemId());
		assertEquals(0, duplicate.getActions().length);
		// A cleared view leaves clicks on that slot alone.
		MenuEntry later = mock(MenuEntry.class);
		when(later.getOption()).thenReturn("Withdraw-1");
		when(later.getParam0()).thenReturn(spareSlot);
		when(later.getParam1()).thenReturn(InterfaceID.Bankmain.ITEMS);
		bank.view.onMenuOptionClicked(new MenuOptionClicked(later));
		verify(later, never()).setParam0(anyInt());
		bank.view.setSelection(setup); bank.view.show();
		assertEquals(bank.items.length + Fixture.SPARE_SLOTS + 1, bank.container.widget.getChildren().length);
		assertEquals(null, bank.view.boostGuidance(ItemID._4DOSE2COMBAT));
	}
	/** Main code gets the client's Gson in startUp; tests use the same instance. */
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
	}

	@Test
	public void nativeBankButtonAppearsOnlyWithSelectionAndTogglesLayout() throws Exception
	{
		Fixture bank = new Fixture();
		BestGearSetupBankButton button = new BestGearSetupBankButton(bank.client, bank.thread, bank.view);
		button.start();
		assertTrue(bank.universe.children.isEmpty());
		assertEquals("", bank.search);
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		button.onClientTick(new ClientTick());
		WidgetState background = bank.universe.children.get(0);
		WidgetState icon = bank.universe.children.get(1);
		assertEquals(408, background.value("OriginalX"));
		assertEquals(5, background.value("OriginalY"));
		assertEquals(25, background.value("OriginalWidth"));
		assertEquals(25, background.value("OriginalHeight"));
		assertEquals(411, icon.value("OriginalX"));
		assertEquals(8, icon.value("OriginalY"));
		assertEquals(19, icon.value("OriginalWidth"));
		assertEquals("Gear layout", background.values.get("Action0"));
		assertFalse(background.values.containsKey("Action1"));
		bank.click(background, 1);
		assertEquals("", bank.search);
		assertEquals(InputType.NONE.getType(), bank.mode);
		assertTrue(bank.view.isActive());
		assertEquals(72, bank.items[0].value("OriginalY"));
		assertEquals(Integer.MAX_VALUE, bank.items[0].value("DragDeadTime"));
		assertEquals("Show all items", background.values.get("Action0"));
		assertEquals(SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED, background.value("SpriteId"));
		bank.click(background, 1);
		assertEquals("", bank.search);
		assertFalse(bank.items[2].widget.isSelfHidden());
		assertEquals(5, bank.items[0].value("DragDeadTime"));
		bank.click(background, 1);
		bank.view.setSelection(null);
		bank.view.refreshSelection();
		button.onClientTick(new ClientTick());
		assertTrue(background.widget.isSelfHidden());
		assertTrue(bank.universe.children.get(1).widget.isSelfHidden());
		assertFalse(background.values.containsKey("Action0"));
		bank.click(background, 1);
		assertEquals("", bank.search);
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		button.onClientTick(new ClientTick());
		assertEquals(2, bank.universe.children.size());
		assertFalse(background.widget.isSelfHidden());
		assertEquals("Gear layout", background.values.get("Action0"));
	}

	@Test
	public void nativeBankButtonSitsRightOfQuestHelpersButtonWhileItIsShown() throws Exception
	{
		Fixture bank = new Fixture();
		Widget questHelper = bank.universe.widget.createChild(-1, WidgetType.GRAPHIC);
		questHelper.setName("quest-helper");
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		BestGearSetupBankButton button = new BestGearSetupBankButton(bank.client, bank.thread, bank.view);
		button.start();
		WidgetState background = bank.universe.children.get(1);
		WidgetState icon = bank.universe.children.get(2);
		assertEquals(434, background.value("OriginalX"));
		assertEquals(437, icon.value("OriginalX"));
		questHelper.setHidden(true);
		button.onClientTick(new ClientTick());
		assertEquals(408, background.value("OriginalX"));
		assertEquals(411, icon.value("OriginalX"));
		assertEquals(3, bank.universe.children.size());
	}

	@Test
	public void nativeBankButtonDoesNotDuplicateOnRebuildAndReleasesWidgetsOnStop() throws Exception
	{
		Fixture bank = new Fixture();
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		BestGearSetupBankButton button = new BestGearSetupBankButton(bank.client, bank.thread, bank.view);
		button.start();
		button.onClientTick(new ClientTick());
		button.onClientTick(new ClientTick());
		assertEquals(2, bank.universe.children.size());
		WidgetState background = bank.universe.children.get(0);
		JavaScriptCallback oldClick = (JavaScriptCallback) ((Object[]) background.values.get("OnOpListener"))[0];
		button.onWidgetClosed(new WidgetClosed(InterfaceID.BANKMAIN, 0, true));
		assertTrue(background.widget.isSelfHidden());
		bank.universe = new WidgetState(-1, 0, -1);
		button.onClientTick(new ClientTick());
		assertEquals(2, bank.universe.children.size());
		button.stop();
		assertTrue(bank.universe.children.get(0).widget.isSelfHidden());
		assertTrue(bank.universe.children.get(1).widget.isSelfHidden());
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		oldClick.run(scriptEvent(1));
		assertEquals("", bank.search);
	}

	@Test
	public void wideScrollWidgetDoesNotPushFilteredItemsOutsideTheBankViewport() throws Exception
	{
		Fixture bank = new Fixture();
		bank.container.values.put("Width", 1000);
		bank.view.setSelection(setup(Slot.WEAPON, item(12926, 12924)));
		assertTrue(bank.view.show());
		assertEquals(51, bank.items[0].value("OriginalX"));
		assertEquals(51, bank.items[1].value("OriginalX"));
		assertEquals(252, bank.items[1].value("OriginalY"));
	}

	@Test
	public void kittedAssemblerTakesTheCapeCellAndBaseBeatsAKitPlaceholder() throws Exception
	{
		GameData data = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin(); plugin.gameData = data;
		for (int kitQuantity : new int[]{1, 0})
		{
			Fixture bank = new Fixture(ItemID.AVAS_ASSEMBLER, ItemID.AVAS_ASSEMBLER_MASORI);
			bank.items[5].values.put("ItemQuantity", kitQuantity);
			bank.view.setSelection(setup(Slot.CAPE, data.findItem(ItemID.AVAS_ASSEMBLER)), Collections.emptyList(),
				plugin::bankItemIds, id -> false);
			assertTrue(bank.view.show());
			assertEquals(36, bank.items[kitQuantity > 0 ? 5 : 4].value("OriginalY"));
			if (kitQuantity > 0) { assertEquals(252, bank.items[4].value("OriginalY")); }
			else { assertTrue(bank.items[5].widget.isSelfHidden()); }
		}
	}

	@Test
	public void cellsOwnItemClaimsItAheadOfAnAlternativeEarlierInTheBank() throws Exception
	{
		Fixture bank = new Fixture();
		// 12926 sits first in the bank but is only an alternative (like a combination rune) for 12924.
		bank.view.setSelection(setup(Slot.WEAPON, item(12924, 12926)));
		assertTrue(bank.view.show());
		assertEquals(72, bank.items[1].value("OriginalY"));
		assertEquals(252, bank.items[0].value("OriginalY"));
		assertFalse(bank.items[0].widget.isSelfHidden());
	}

	@Test
	public void extraGearRunesAndBoostsEachStartTheirOwnRowBelowTheLayout() throws Exception
	{
		Fixture bank = new Fixture(ItemID.AIRRUNE, ItemID.MISTRUNE, ItemID.MINDRUNE, ItemID._4DOSE2ATTACK,
			ItemID._4DOSE2COMBAT);
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, item(12926, 12924));
		Spell spell = new Spell();
		spell.setName("wind strike");
		loadout.setSpell(spell);
		Potion attack = new Potion();
		attack.setId(ItemID._4DOSE2ATTACK);
		bank.view.setSelection(new SetupResult(CombatClass.MAGIC, loadout, null, 0), Collections.singletonList(attack));
		assertTrue(bank.view.show());
		// Own cells: weapon in the silhouette; the boost then the runes in the inventory's first row.
		assertEquals(72, bank.items[0].value("OriginalY"));
		assertEquals(0, bank.items[7].value("OriginalY"));
		assertEquals(243, bank.items[7].value("OriginalX"));
		assertEquals(0, bank.items[4].value("OriginalY"));
		assertEquals(291, bank.items[4].value("OriginalX"));
		assertEquals(0, bank.items[6].value("OriginalY"));
		assertEquals(339, bank.items[6].value("OriginalX"));
		// Extras below the inventory: the gear variant, then super combat, then the mist rune on separate rows.
		assertEquals(252, bank.items[1].value("OriginalY"));
		assertEquals(288, bank.items[8].value("OriginalY"));
		assertEquals(51, bank.items[8].value("OriginalX"));
		assertEquals(324, bank.items[5].value("OriginalY"));
		assertEquals(51, bank.items[5].value("OriginalX"));
		assertEquals(360, bank.container.value("ScrollHeight"));
	}

	@Test
	public void extraPotionsAreGroupedByTypeWithEachTypeOnItsOwnRow() throws Exception
	{
		// Bank order mixes the types; there is no 4-dose super attack.
		Fixture bank = new Fixture(ItemID._3DOSE2ATTACK, ItemID._4DOSE2COMBAT, ItemID._2DOSE2ATTACK,
			ItemID._2DOSE2COMBAT);
		Potion attack = new Potion();
		attack.setId(ItemID._4DOSE2ATTACK);
		bank.view.setSelection(setup(Slot.WEAPON, item(12926, 12924)), Collections.singletonList(attack));
		assertTrue(bank.view.show());
		// The highest dose of the potion itself fills its cell ahead of the stand-in super combat.
		assertEquals(0, bank.items[4].value("OriginalY"));
		assertEquals(243, bank.items[4].value("OriginalX"));
		// Gear extra, then the remaining super attack, then both super combats together on the next row.
		assertEquals(252, bank.items[1].value("OriginalY"));
		assertEquals(288, bank.items[6].value("OriginalY"));
		assertEquals(51, bank.items[6].value("OriginalX"));
		assertEquals(324, bank.items[5].value("OriginalY"));
		assertEquals(51, bank.items[5].value("OriginalX"));
		assertEquals(324, bank.items[7].value("OriginalY"));
		assertEquals(99, bank.items[7].value("OriginalX"));
	}

	@Test
	public void extraPrepotDosesAreArrangedHighToLow() throws Exception
	{
		Fixture bank = new Fixture(ItemID._1DOSE2ATTACK, ItemID._2DOSE2ATTACK, ItemID._3DOSE2ATTACK,
			ItemID._4DOSE2ATTACK);
		SetupResult setup = setup(Slot.WEAPON, item(12926));
		RaidPlan plan = mock(RaidPlan.class);
		when(plan.getStartingSetup()).thenReturn(setup);
		when(plan.getEquipment()).thenReturn(Collections.emptyList());
		when(plan.getInventory()).thenReturn(Collections.emptyList());
		when(plan.getSetups()).thenReturn(Collections.emptyList());
		when(plan.prepotItems()).thenReturn(Collections.singletonList(new RaidPlan.InventoryItem("Super attack",
			Arrays.asList(ItemID._1DOSE2ATTACK, ItemID._2DOSE2ATTACK, ItemID._3DOSE2ATTACK, ItemID._4DOSE2ATTACK),
			false, Collections.emptyList(), RaidPlan.InventoryGroup.BOOSTS)));

		bank.view.setRaidSelection(plan, BestGearSetupBankView::itemIds);
		assertTrue(bank.view.show());
		// The one-dose prepot fills its cell below the silhouette; its extras remain grouped high-dose-first.
		assertEquals(180, bank.items[4].value("OriginalY"));
		assertEquals(51, bank.items[4].value("OriginalX"));
		assertEquals(252, bank.items[7].value("OriginalY"));
		assertEquals(51, bank.items[7].value("OriginalX"));
		assertEquals(252, bank.items[6].value("OriginalY"));
		assertEquals(99, bank.items[6].value("OriginalX"));
		assertEquals(252, bank.items[5].value("OriginalY"));
		assertEquals(147, bank.items[5].value("OriginalX"));
		Widget extraPrepot = bank.spares[0].widget;
		assertEquals(ItemID._1DOSE2ATTACK, extraPrepot.getItemId());
		assertEquals(252, extraPrepot.getOriginalY());
		assertEquals(195, extraPrepot.getOriginalX());
		assertFalse(extraPrepot.isSelfHidden());
	}

	@Test
	public void filterIncludesVariantsAmmoAndPlaceholdersButSkipsUnrelatedItems() throws Exception
	{
		Fixture bank = new Fixture();
		bank.view.setSelection(setup(Slot.WEAPON, item(12926, 12924)));
		assertTrue(bank.view.show());
		assertTrue(bank.heading.widget.isSelfHidden());
		assertEquals(72, bank.items[0].value("OriginalY"));
		assertEquals(252, bank.items[1].value("OriginalY"));
		assertEquals(288, bank.container.value("ScrollHeight"));
		assertFalse(bank.items[0].widget.isSelfHidden());
		assertFalse(bank.items[1].widget.isSelfHidden());
		assertTrue(bank.items[2].widget.isSelfHidden());
		// The weapon's placeholder passes the filter, but a real item already holds its cell.
		assertTrue(bank.items[3].widget.isSelfHidden());
		assertEquals(1, bank.filter(12926));
		assertEquals(0, bank.filter(-1));
		assertEquals(1, bank.filter(4153));
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, item(12926, 12924));
		loadout.setLoadedAmmo(item(11230, 11231));
		bank.view.setSelection(new SetupResult(CombatClass.RANGED, loadout, null, 0));
		bank.view.refreshSelection();
		// A new selection updates the open layout in place; the player doesn't have to reopen it.
		assertTrue(bank.view.isActive());
		assertEquals(0, bank.filter(11231));
		assertEquals(1, bank.filter(12926));
	}

	@Test
	public void partialRaidInventoryReservesSevenRowsBeforeLoadingItemsAndVariants() throws Exception
	{
		Fixture bank = new Fixture(200);
		GearItem weapon = item(12926, 12924);
		SetupResult setup = setup(Slot.WEAPON, weapon);
		RaidPlan plan = mock(RaidPlan.class);
		when(plan.getStartingSetup()).thenReturn(setup);
		when(plan.getSetups()).thenReturn(Collections.singletonList(setup));
		when(plan.getEquipment()).thenReturn(Collections.singletonList(weapon));
		RaidPlan.InventoryItem rune = new RaidPlan.InventoryItem("Rune", Collections.singletonList(200), false);
		when(plan.getInventory()).thenReturn(Arrays.asList(new RaidPlan.InventoryItem("Pouch",
			Collections.singletonList(100), false, Collections.singletonList(rune)),
			new RaidPlan.InventoryItem("Switch", Collections.singletonList(300), true)));
		bank.view.setRaidSelection(plan, BestGearSetupBankView::itemIds);
		assertTrue(bank.view.show());
		assertEquals(243, bank.items[2].value("OriginalX"));
		assertEquals(0, bank.items[2].value("OriginalY"));
		assertEquals(252, bank.items[4].value("OriginalY"));
		assertEquals(288, bank.items[1].value("OriginalY"));
		assertEquals(324, bank.container.value("ScrollHeight"));
	}

	@Test
	public void depositedTridentChargeStateReplacesFullPlaceholderWithoutReselecting() throws Exception
	{
		GameData data = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin(); plugin.gameData = data;
		for (int[] states : new int[][]{{ItemID.TOTS, ItemID.TOTS_CHARGED}})
		{
			Fixture bank = new Fixture(states[0], states[1], ItemID.TOTS_I_CHARGED, ItemID.TOXIC_TOTS_CHARGED);
			bank.items[4].values.put("ItemQuantity", 0);
			bank.items[5].values.put("ItemId", -1); // The deposited item is still in inventory.
			bank.view.setSelection(setup(Slot.WEAPON, data.findItem(states[0])), Collections.emptyList(),
				plugin::bankItemIds, id -> false);
			assertTrue(bank.view.show());
			assertFalse("The selected trident's placeholder initially fills the weapon cell", bank.items[4].widget.isSelfHidden());
			bank.items[5].values.put("ItemId", states[1]); // Deposit a different charge state.
			bank.items[5].widget.setAction(0, "Withdraw-1");
			bank.rebuild();
			assertEquals("Deposited charge state must pass the existing bank filter", 1, bank.filter(states[1]));
			assertFalse(bank.items[5].widget.isSelfHidden());
			assertTrue("Real deposited gear takes precedence over the full placeholder", bank.items[4].widget.isSelfHidden());
			assertEquals(51, bank.items[5].value("OriginalX"));
			assertEquals(72, bank.items[5].value("OriginalY"));
			assertEquals(states[1], bank.items[5].widget.getItemId());
			assertEquals("Withdraw-1", bank.items[5].values.get("Action0"));
			assertEquals("Enhanced tridents remain separate", 0, bank.filter(ItemID.TOTS_I_CHARGED));
			assertEquals("Swamp tridents remain separate", 0, bank.filter(ItemID.TOXIC_TOTS_CHARGED));
			bank.items[5].values.put("ItemId", -1); // Withdraw it again: restore the placeholder.
			bank.rebuild();
			assertFalse(bank.items[4].widget.isSelfHidden());
			assertTrue(bank.view.isActive());
		}
	}

	@Test
	public void withdrawnItemLeavesItsPlaceholderInTheCell() throws Exception
	{
		Fixture bank = new Fixture();
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		assertTrue(bank.view.show());
		assertEquals(72, bank.items[0].value("OriginalY"));
		assertEquals(252, bank.container.value("ScrollHeight"));
		assertTrue(bank.items[3].widget.isSelfHidden());
		// Withdrawing the weapon leaves only its placeholder, which takes the empty weapon cell.
		bank.items[0].values.put("ItemId", -1);
		bank.rebuild();
		assertTrue(bank.items[0].widget.isSelfHidden());
		assertFalse(bank.items[3].widget.isSelfHidden());
		assertEquals(72, bank.items[3].value("OriginalY"));
		assertEquals(51, bank.items[3].value("OriginalX"));
		assertEquals(252, bank.container.value("ScrollHeight"));
	}

	@Test
	public void layoutPreservesWithdrawalIdentitySeparatesVariantsAndRestoresDragging() throws Exception
	{
		Fixture bank = new Fixture();
		bank.view.setSelection(setup(Slot.WEAPON, item(12926, 12924)));
		assertTrue(bank.view.show());
		assertEquals(51, bank.items[0].value("OriginalX"));
		assertEquals(72, bank.items[0].value("OriginalY"));
		assertEquals(252, bank.items[1].value("OriginalY"));
		assertEquals(0, bank.items[0].value("Index"));
		assertEquals(12926, bank.items[0].value("ItemId"));
		assertEquals(Integer.MAX_VALUE, bank.items[0].value("DragDeadZone"));
		assertEquals(Integer.MAX_VALUE, bank.items[0].value("DragDeadTime"));
		assertEquals(288, bank.container.value("ScrollHeight"));
		bank.view.clear();
		for (WidgetState item : bank.items)
		{
			assertFalse(item.widget.isSelfHidden());
			assertEquals(5, item.value("DragDeadZone"));
			assertEquals(5, item.value("DragDeadTime"));
			assertEquals(0, item.value("OriginalY"));
		}
		assertEquals(InputType.NONE.getType(), bank.mode);
		assertEquals("", bank.search);
	}

	@Test
	public void anotherSearchTakesOverAndClearDoesNotEraseIt() throws Exception
	{
		Fixture bank = new Fixture();
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		bank.view.show();
		bank.view.onScriptPreFired(new ScriptPreFired(ScriptID.BANKMAIN_SEARCH_TOGGLE));
		assertFalse(bank.view.isActive());
		bank.mode = InputType.SEARCH.getType();
		bank.search = "rune";
		bank.rebuild();
		assertEquals(-1, bank.filter(12926));
		assertEquals(5, bank.items[0].value("DragDeadZone"));
		bank.view.clear();
		assertEquals("rune", bank.search);
		assertEquals(InputType.SEARCH.getType(), bank.mode);
	}

	@Test
	public void gearTabUsesNativeTabCallbacksWithoutOpeningSearchAndReleasesOnOtherTabs() throws Exception
	{
		Fixture bank = new Fixture();
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		bank.mode = InputType.SEARCH.getType();
		bank.search = "rune";
		assertTrue(bank.view.show());
		assertEquals(InputType.NONE.getType(), bank.mode);
		assertEquals("", bank.search);
		assertEquals(1, bank.callback("getSearchingTagTab"));
		assertEquals(1, bank.callback("bankBuildTab"));
		assertEquals(1, bank.filter(12926));
		assertEquals(0, bank.filter(100));
		// A server inventory rebuild must keep the view active without a message-layer marker.
		bank.rebuild();
		assertTrue(bank.view.isActive());
		assertEquals(72, bank.items[0].value("OriginalY"));
		MenuEntry viewTagTab = mock(MenuEntry.class);
		when(viewTagTab.getOption()).thenReturn("View tag tab");
		bank.view.onMenuOptionClicked(new MenuOptionClicked(viewTagTab));
		assertFalse(bank.view.isActive());
		assertEquals(5, bank.items[0].value("DragDeadTime"));
		assertEquals(0, bank.callback("bankBuildTab"));
		assertEquals(0, bank.callback("getSearchingTagTab"));
		bank.rebuild();
		assertFalse(bank.items[2].widget.isSelfHidden());
	}

	@Test
	public void showReleasesOtherPluginTabsButNotThePotionStore() throws Exception
	{
		Fixture store = new Fixture();
		store.view.setSelection(setup(Slot.WEAPON, item(12926)));
		store.tab = 15;
		store.container.values.put("Hidden", true);
		assertTrue(store.view.isPotionStoreOpen());
		assertFalse(store.view.show());
		assertFalse(store.view.isActive());
		assertEquals(15, store.tab);
		assertTrue(store.posted.isEmpty());

		Fixture bank = new Fixture();
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		bank.tab = 3;
		assertTrue(bank.view.show());
		assertTrue(bank.view.isActive());
		assertEquals(0, bank.tab);
		// Bank Tags and Quest Helper close their own tabs on a search toggle.
		assertEquals(1, bank.posted.size());
		assertEquals(ScriptID.BANKMAIN_SEARCH_TOGGLE, ((ScriptPreFired) bank.posted.get(0)).getScriptId());
		assertEquals(InputType.NONE.getType(), bank.mode);
		assertEquals(1, bank.filter(12926));
		assertEquals(0, bank.filter(100));

		Fixture hidden = new Fixture();
		hidden.view.setSelection(setup(Slot.WEAPON, item(12926)));
		hidden.container.values.put("Hidden", true);
		assertFalse(hidden.view.show());
		assertTrue(hidden.posted.isEmpty());
	}

	@Test
	public void clearingSelectionAndClosingBankReleaseOnlyOurSearch() throws Exception
	{
		Fixture bank = new Fixture();
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		bank.view.show();
		bank.view.setSelection(null);
		bank.view.refreshSelection();
		assertEquals("", bank.search);
		assertFalse(bank.items[2].widget.isSelfHidden());
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		bank.view.show();
		bank.view.onWidgetClosed(new WidgetClosed(InterfaceID.BANKMAIN, 0, true));
		assertEquals(InputType.NONE.getType(), bank.mode);
		assertEquals("", bank.search);
		assertEquals(5, bank.items[0].value("DragDeadTime"));
		assertEquals(-1, bank.filter(12926));
	}

	@Test
	public void reopeningBankRestoresOnlyALayoutThatWasShowing() throws Exception
	{
		Fixture bank = new Fixture();
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		bank.view.show();
		bank.view.onWidgetClosed(new WidgetClosed(InterfaceID.BANKMAIN, 0, true));
		assertFalse(bank.view.isActive());
		bank.tab = 3;
		bank.rebuild();
		assertTrue(bank.view.isActive());
		assertEquals(0, bank.tab);
		assertTrue(bank.items[2].widget.isSelfHidden());

		bank.view.clear();
		bank.view.onWidgetClosed(new WidgetClosed(InterfaceID.BANKMAIN, 0, true));
		bank.rebuild();
		assertFalse(bank.view.isActive());
		assertFalse(bank.items[2].widget.isSelfHidden());
	}

	@Test
	public void closedBankOrEmptySelectionDoesNotStartASearch() throws Exception
	{
		Fixture bank = new Fixture();
		assertFalse(bank.view.show());
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		bank.open = false;
		assertFalse(bank.view.show());
		assertEquals("", bank.search);
		assertEquals(InputType.NONE.getType(), bank.mode);
	}

	private static SetupResult setup(Slot slot, GearItem item)
	{
		Loadout loadout = new Loadout();
		loadout.set(slot, item);
		return new SetupResult(CombatClass.MELEE, loadout, null, 0);
	}

	private static GearItem item(int id, Integer... variants)
	{
		GearItem item = new GearItem();
		item.setId(id);
		item.setVariants(Arrays.asList(variants));
		return item;
	}

	private static ScriptEvent scriptEvent(int op)
	{
		ScriptEvent event = mock(ScriptEvent.class);
		when(event.getOp()).thenReturn(op);
		return event;
	}

	/** Minimal game-script fixture: native tab/search mode, filtering, and rebuilt bank widgets. */
	private static class Fixture
	{
		/** Unused item slots the game builds after the real stacks, before the tab headings. */
		static final int SPARE_SLOTS = 3;
		private final WidgetState[] items;
		private final WidgetState[] spares = new WidgetState[SPARE_SLOTS];
		private final WidgetState container = new WidgetState(-1, 0, -1);
		private WidgetState universe = new WidgetState(-1, 0, -1);
		private final WidgetState heading = new WidgetState(-1, 0, 1410);
		private final int[] ints = new int[24];
		private int stackSize = 2;
		private final Object[] objects = new Object[1];
		private boolean open = true;
		private int mode;
		private String search = "";
		private int tab;
		private final List<Object> posted = new ArrayList<>();
		private final Map<Integer, ItemComposition> compositions = new HashMap<>();
		private final BestGearSetupBankView view;
		private final Client client;
		private final ClientThread thread;

		/** The bank holds 12926, 12924, 100 and an empty placeholder, then one of each extra item. */
		Fixture(int... extraItems) throws Exception
		{
			items = new WidgetState[4 + extraItems.length];
			items[0] = new WidgetState(12926, 1, 0);
			items[1] = new WidgetState(12924, 1, 1);
			items[2] = new WidgetState(100, 1, 2);
			items[3] = new WidgetState(4153, 0, 3);
			for (int i = 0; i < extraItems.length; i++)
			{
				items[4 + i] = new WidgetState(extraItems[i], 1, 4 + i);
			}
			Widget[] children = new Widget[items.length + SPARE_SLOTS + 1];
			for (int i = 0; i < items.length; i++)
			{
				children[i] = items[i].widget;
			}
			for (int i = 0; i < SPARE_SLOTS; i++)
			{
				spares[i] = new WidgetState(-1, 0, items.length + i);
				spares[i].values.put("SelfHidden", true);
				children[items.length + i] = spares[i].widget;
			}
			children[children.length - 1] = heading.widget;
			heading.values.put("OriginalHeight", 20);
			container.values.put("Width", 460);
			container.values.put("Height", 140);
			container.values.put("ScrollHeight", 36);
			container.values.put("Children", children);
			container.values.put("OnInvTransmitListener", new Object[]{ScriptID.BANKMAIN_BUILD});
			client = mock(Client.class);
			ItemContainer bankItems = mock(ItemContainer.class);
			when(bankItems.size()).thenReturn(items.length);
			when(client.getItemContainer(InventoryID.BANK)).thenReturn(bankItems);
			when(client.getWidget(anyInt())).thenAnswer(i -> !open ? null
				: i.<Integer>getArgument(0) == InterfaceID.Bankmain.UNIVERSE ? universe.widget : container.widget);
			when(client.getVarcIntValue(anyInt())).thenAnswer(i ->
				i.<Integer>getArgument(0) == VarClientID.MESLAYERMODE ? mode : 0);
			when(client.getVarcStrValue(anyInt())).thenAnswer(i -> search);
			when(client.getVarbitValue(anyInt())).thenAnswer(i -> tab);
			doAnswer(i -> tab = i.getArgument(1)).when(client).setVarbit(anyInt(), anyInt());
			doThrow(new AssertionError("Plugin Hub plugins may not call menuAction")).when(client)
				.menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), any(), any());
			doAnswer(i ->
			{
				if (i.<Integer>getArgument(0) == VarClientID.MESLAYERMODE)
				{
					mode = i.getArgument(1);
				}
				return null;
			}).when(client).setVarcIntValue(anyInt(), anyInt());
			doAnswer(i -> search = i.getArgument(1)).when(client).setVarcStrValue(anyInt(), any());
			when(client.getIntStack()).thenReturn(ints);
			when(client.getObjectStack()).thenReturn(objects);
			when(client.getIntStackSize()).thenAnswer(i -> stackSize);
			when(client.getObjectStackSize()).thenReturn(1);
			when(client.getItemDefinition(anyInt())).thenAnswer(i -> composition(i.getArgument(0)));
			doAnswer(i ->
			{
				Object[] script = (Object[]) i.getRawArguments()[0];
				if ((int) script[0] == ScriptID.MESSAGE_LAYER_CLOSE)
				{
					mode = InputType.NONE.getType();
					search = "";
				}
				else if ((int) script[0] == ScriptID.BANKMAIN_SEARCH_TOGGLE)
				{
					mode = InputType.SEARCH.getType();
				}
				rebuild();
				return null;
			}).when(client).runScript(any(Object[].class));
			thread = new ClientThread()
			{
				@Override
				public void invoke(Runnable action) { action.run(); }
				@Override
				public void invokeLater(Runnable action) { action.run(); }
			};
			// The real BankSearch and ItemManager, built through their injected constructors.
			BankSearch bankSearch = mock(BankSearch.class, withSettings().useConstructor(client, thread)
				.defaultAnswer(CALLS_REAL_METHODS));
			// ItemManager only reads item definitions in these tests; its background tasks stay dormant.
			ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
			ItemManager manager = mock(ItemManager.class, withSettings()
				.useConstructor(client, executor, thread, new EventBus(), null, null).defaultAnswer(CALLS_REAL_METHODS));
			EventBus eventBus = new EventBus()
			{
				@Override
				public void post(Object event) { posted.add(event); }
			};
			view = new BestGearSetupBankView(client, thread, manager, bankSearch, eventBus);
		}

		private ItemComposition composition(int id)
		{
			return compositions.computeIfAbsent(id, key ->
			{
				ItemComposition composition = mock(ItemComposition.class);
				when(composition.getNote()).thenReturn(-1);
				when(composition.getPlaceholderTemplateId()).thenReturn(key == 4153 ? 14401 : -1);
				when(composition.getPlaceholderId()).thenReturn(key == 4153 ? 12926 : -1);
				return composition;
			});
		}

		int filter(int id)
		{
			ints[0] = -1;
			ints[1] = id;
			objects[0] = search;
			ScriptCallbackEvent event = new ScriptCallbackEvent();
			event.setEventName("bankSearchFilter");
			view.onScriptCallbackEvent(event);
			return ints[0];
		}

		int callback(String name)
		{
			ints[stackSize - 1] = 0;
			ScriptCallbackEvent event = new ScriptCallbackEvent();
			event.setEventName(name);
			view.onScriptCallbackEvent(event);
			return ints[stackSize - 1];
		}

		void click(WidgetState widget, int op)
		{
			JavaScriptCallback action = (JavaScriptCallback) ((Object[]) widget.values.get("OnOpListener"))[0];
			action.run(scriptEvent(op));
		}

		void rebuild()
		{
			view.onScriptPreFired(new ScriptPreFired(ScriptID.BANKMAIN_BUILD));
			boolean singleTab = callback("bankBuildTab") == 1;
			for (WidgetState item : items)
			{
				int result = mode == InputType.SEARCH.getType() || singleTab ? filter(item.value("ItemId")) : 1;
				item.values.put("SelfHidden", result == 0);
				item.values.put("OriginalY", 0);
				item.values.put("OriginalX", item.value("Index") * 48);
			}
			heading.values.put("SelfHidden", false);
			stackSize = 22;
			ints[stackSize - 9] = container.value("ScrollHeight");
			view.onScriptPreFired(new ScriptPreFired(ScriptID.BANKMAIN_FINISHBUILDING));
			assertEquals(container.value("ScrollHeight"), ints[stackSize - 9]);
			stackSize = 2;
			view.onScriptPostFired(new ScriptPostFired(ScriptID.BANKMAIN_FINISHBUILDING));
		}
	}

	private static class WidgetState
	{
		private final Map<String, Object> values = new HashMap<>();
		private final List<WidgetState> children = new ArrayList<>();
		private final Widget widget = mock(Widget.class);

		WidgetState(int id, int quantity, int index)
		{
			values.put("ItemId", id);
			values.put("ItemQuantity", quantity);
			values.put("Index", index);
			values.put("OriginalWidth", 36);
			values.put("OriginalHeight", 32);
			values.put("DragDeadZone", 5);
			values.put("DragDeadTime", 5);
			// A property bag: setters store under the property name, getters read it back (setHidden is SelfHidden).
			when(widget.getChildren()).thenAnswer(i -> values.get("Children"));
			when(widget.getName()).thenAnswer(i -> values.get("Name"));
			when(widget.getText()).thenAnswer(i -> values.get("Text"));
			when(widget.getOnInvTransmitListener()).thenAnswer(i -> values.get("OnInvTransmitListener"));
			when(widget.getOnOpListener()).thenAnswer(i -> values.get("OnOpListener"));
			when(widget.getActions()).thenAnswer(i -> actions());
			when(widget.hasListener()).thenAnswer(i -> flag("HasListener"));
			when(widget.getItemId()).thenAnswer(i -> intValue("ItemId"));
			when(widget.getItemQuantity()).thenAnswer(i -> intValue("ItemQuantity"));
			when(widget.getIndex()).thenAnswer(i -> intValue("Index"));
			when(widget.getItemQuantityMode()).thenAnswer(i -> intValue("ItemQuantityMode"));
			when(widget.getOriginalWidth()).thenAnswer(i -> intValue("OriginalWidth"));
			when(widget.getOriginalHeight()).thenAnswer(i -> intValue("OriginalHeight"));
			when(widget.getOpacity()).thenAnswer(i -> intValue("Opacity"));
			when(widget.getCanvasLocation()).thenAnswer(i ->
				new Point(intValue("OriginalX"), intValue("OriginalY")));
			when(widget.getDragDeadTime()).thenAnswer(i -> intValue("DragDeadTime"));
			when(widget.getDragDeadZone()).thenAnswer(i -> intValue("DragDeadZone"));
			when(widget.getHeight()).thenAnswer(i -> values.containsKey("Height")
				? intValue("Height") : intValue("OriginalHeight"));
			when(widget.getWidth()).thenAnswer(i -> values.containsKey("Width")
				? intValue("Width") : intValue("OriginalWidth"));
			when(widget.getOriginalX()).thenAnswer(i -> intValue("OriginalX"));
			when(widget.getOriginalY()).thenAnswer(i -> intValue("OriginalY"));
			when(widget.getScrollHeight()).thenAnswer(i -> intValue("ScrollHeight"));
			when(widget.getScrollY()).thenAnswer(i -> intValue("ScrollY"));
			when(widget.getSpriteId()).thenAnswer(i -> intValue("SpriteId"));
			when(widget.isHidden()).thenAnswer(i -> flag("Hidden"));
			when(widget.isSelfHidden()).thenAnswer(i -> flag("SelfHidden"));
			when(widget.setHidden(anyBoolean())).thenAnswer(store("SelfHidden"));
			when(widget.setHasListener(anyBoolean())).thenAnswer(store("HasListener"));
			when(widget.setName(any())).thenAnswer(store("Name"));
			when(widget.setText(any())).thenAnswer(store("Text"));
			when(widget.setOriginalX(anyInt())).thenAnswer(store("OriginalX"));
			when(widget.setOriginalY(anyInt())).thenAnswer(store("OriginalY"));
			when(widget.setOriginalWidth(anyInt())).thenAnswer(store("OriginalWidth"));
			when(widget.setOriginalHeight(anyInt())).thenAnswer(store("OriginalHeight"));
			when(widget.setItemId(anyInt())).thenAnswer(store("ItemId"));
			when(widget.setItemQuantity(anyInt())).thenAnswer(store("ItemQuantity"));
			when(widget.setItemQuantityMode(anyInt())).thenAnswer(store("ItemQuantityMode"));
			when(widget.setOpacity(anyInt())).thenAnswer(store("Opacity"));
			when(widget.setScrollHeight(anyInt())).thenAnswer(store("ScrollHeight"));
			when(widget.setScrollY(anyInt())).thenAnswer(store("ScrollY"));
			when(widget.setSpriteId(anyInt())).thenAnswer(store("SpriteId"));
			doAnswer(store("DragDeadTime")).when(widget).setDragDeadTime(anyInt());
			doAnswer(store("DragDeadZone")).when(widget).setDragDeadZone(anyInt());
			doAnswer(i -> values.put("OnOpListener", i.getRawArguments()[0])).when(widget)
				.setOnOpListener(any(Object[].class));
			doAnswer(i -> values.put("Action" + i.getArgument(0), i.getArgument(1))).when(widget)
				.setAction(anyInt(), any());
			doAnswer(i -> values.keySet().removeIf(key -> key.startsWith("Action"))).when(widget).clearActions();
			when(widget.createChild(anyInt(), anyInt())).thenAnswer(i ->
			{
				Widget[] current = (Widget[]) values.get("Children");
				int childIndex = current == null ? 0 : current.length;
				WidgetState child = new WidgetState(-1, 0, childIndex);
				children.add(child);
				Widget[] expanded = current == null ? new Widget[1] : Arrays.copyOf(current, current.length + 1);
				expanded[childIndex] = child.widget;
				values.put("Children", expanded);
				return child.widget;
			});
		}

		private Answer<Widget> store(String property)
		{
			return i ->
			{
				values.put(property, i.getArgument(0));
				return widget;
			};
		}

		private int intValue(String property)
		{
			Object value = values.get(property);
			return value == null ? 0 : (int) value;
		}

		private String[] actions()
		{
			int count = values.keySet().stream().filter(key -> key.startsWith("Action"))
				.mapToInt(key -> Integer.parseInt(key.substring("Action".length()))).max().orElse(-1) + 1;
			String[] actions = new String[count];
			for (int i = 0; i < count; i++) { actions[i] = (String) values.get("Action" + i); }
			return actions;
		}

		private boolean flag(String property)
		{
			return Boolean.TRUE.equals(values.get(property));
		}

		int value(String name) { return (int) values.get(name); }
	}
}
