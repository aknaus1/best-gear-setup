package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import com.google.gson.Gson;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
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
import org.junit.BeforeClass;
import org.junit.Test;

public class BankViewTest
{
	/** Main code gets the client's Gson in startUp; tests supply their own. */
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(new Gson());
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
		oldClick.run(proxy(ScriptEvent.class, (p, m, a) -> m.getName().equals("getOp") ? 1 : null));
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
		assertEquals(180, bank.items[1].value("OriginalY"));
	}

	@Test
	public void cellsOwnItemClaimsItAheadOfAnAlternativeEarlierInTheBank() throws Exception
	{
		Fixture bank = new Fixture();
		// 12926 sits first in the bank but is only an alternative (like a combination rune) for 12924.
		bank.view.setSelection(setup(Slot.WEAPON, item(12924, 12926)));
		assertTrue(bank.view.show());
		assertEquals(72, bank.items[1].value("OriginalY"));
		assertEquals(180, bank.items[0].value("OriginalY"));
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
		// Own cells: weapon, air and mind runes right of the head, super attack below them.
		assertEquals(72, bank.items[0].value("OriginalY"));
		assertEquals(0, bank.items[4].value("OriginalY"));
		assertEquals(243, bank.items[4].value("OriginalX"));
		assertEquals(72, bank.items[7].value("OriginalY"));
		assertEquals(243, bank.items[7].value("OriginalX"));
		// Extras: the gear variant, then the mist rune and the super combat each on a row of their own.
		assertEquals(180, bank.items[1].value("OriginalY"));
		assertEquals(216, bank.items[5].value("OriginalY"));
		assertEquals(51, bank.items[5].value("OriginalX"));
		assertEquals(252, bank.items[8].value("OriginalY"));
		assertEquals(51, bank.items[8].value("OriginalX"));
		assertEquals(288, bank.container.value("ScrollHeight"));
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
		assertEquals(72, bank.items[4].value("OriginalY"));
		assertEquals(243, bank.items[4].value("OriginalX"));
		// Gear extra, then the remaining super attack, then both super combats together on the next row.
		assertEquals(180, bank.items[1].value("OriginalY"));
		assertEquals(216, bank.items[6].value("OriginalY"));
		assertEquals(51, bank.items[6].value("OriginalX"));
		assertEquals(252, bank.items[5].value("OriginalY"));
		assertEquals(51, bank.items[5].value("OriginalX"));
		assertEquals(252, bank.items[7].value("OriginalY"));
		assertEquals(99, bank.items[7].value("OriginalX"));
	}

	@Test
	public void filterIncludesVariantsAmmoAndPlaceholdersButSkipsUnrelatedItems() throws Exception
	{
		Fixture bank = new Fixture();
		bank.view.setSelection(setup(Slot.WEAPON, item(12926, 12924)));
		assertTrue(bank.view.show());
		assertTrue(bank.heading.widget.isSelfHidden());
		assertEquals(72, bank.items[0].value("OriginalY"));
		assertEquals(180, bank.items[1].value("OriginalY"));
		assertEquals(216, bank.container.value("ScrollHeight"));
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
		assertEquals(0, bank.filter(11231));
		assertEquals(1, bank.filter(12926));
	}

	@Test
	public void withdrawnItemLeavesItsPlaceholderInTheCell() throws Exception
	{
		Fixture bank = new Fixture();
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		assertTrue(bank.view.show());
		assertEquals(72, bank.items[0].value("OriginalY"));
		assertTrue(bank.items[3].widget.isSelfHidden());
		// Withdrawing the weapon leaves only its placeholder, which takes the empty weapon cell.
		bank.items[0].values.put("ItemId", -1);
		bank.rebuild();
		assertTrue(bank.items[0].widget.isSelfHidden());
		assertFalse(bank.items[3].widget.isSelfHidden());
		assertEquals(72, bank.items[3].value("OriginalY"));
		assertEquals(51, bank.items[3].value("OriginalX"));
	}

	@Test
	public void layoutPreservesWithdrawalIdentitySeparatesVariantsAndRestoresDragging() throws Exception
	{
		Fixture bank = new Fixture();
		bank.view.setSelection(setup(Slot.WEAPON, item(12926, 12924)));
		assertTrue(bank.view.show());
		assertEquals(51, bank.items[0].value("OriginalX"));
		assertEquals(72, bank.items[0].value("OriginalY"));
		assertEquals(180, bank.items[1].value("OriginalY"));
		assertEquals(0, bank.items[0].value("Index"));
		assertEquals(12926, bank.items[0].value("ItemId"));
		assertEquals(Integer.MAX_VALUE, bank.items[0].value("DragDeadZone"));
		assertEquals(Integer.MAX_VALUE, bank.items[0].value("DragDeadTime"));
		assertEquals(216, bank.container.value("ScrollHeight"));
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
		bank.view.onMenuOptionClicked(new MenuOptionClicked(proxy(MenuEntry.class,
			(p, m, a) -> m.getName().equals("getOption") ? "View tag tab" : null)));
		assertFalse(bank.view.isActive());
		assertEquals(5, bank.items[0].value("DragDeadTime"));
		assertEquals(0, bank.callback("bankBuildTab"));
		assertEquals(0, bank.callback("getSearchingTagTab"));
		bank.rebuild();
		assertFalse(bank.items[2].widget.isSelfHidden());
	}

	@Test
	public void showReleasesOtherPluginTabsAndThePotionStore() throws Exception
	{
		Fixture bank = new Fixture();
		bank.view.setSelection(setup(Slot.WEAPON, item(12926)));
		bank.tab = 15;
		bank.container.values.put("Hidden", true);
		assertTrue(bank.view.show());
		assertTrue(bank.view.isActive());
		assertEquals(Arrays.asList("Potion store", "View all items"), bank.menuActions);
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
		assertTrue(hidden.menuActions.isEmpty());
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

	private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}

	/** Minimal game-script fixture: native tab/search mode, filtering, and rebuilt bank widgets. */
	private static class Fixture
	{
		private final WidgetState[] items;
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
		private final List<String> menuActions = new ArrayList<>();
		private final List<Object> posted = new ArrayList<>();
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
			Widget[] children = new Widget[items.length + 1];
			for (int i = 0; i < items.length; i++)
			{
				children[i] = items[i].widget;
			}
			children[items.length] = heading.widget;
			container.values.put("Width", 460);
			container.values.put("Height", 140);
			container.values.put("ScrollHeight", 36);
			container.values.put("Children", children);
			container.values.put("OnInvTransmitListener", new Object[]{ScriptID.BANKMAIN_BUILD});
			client = proxy(Client.class, (p, method, args) ->
			{
				switch (method.getName())
				{
					case "getWidget": return !open ? null
						: (int) args[0] == InterfaceID.Bankmain.UNIVERSE ? universe.widget : container.widget;
					case "getVarcIntValue": return (int) args[0] == VarClientID.MESLAYERMODE ? mode : 0;
					case "getVarcStrValue": return search;
					case "getVarbitValue": return tab;
					case "setVarbit": tab = (int) args[1]; return null;
					case "menuAction": menuActions.add((String) args[5]); return null;
					case "setVarcIntValue":
						if ((int) args[0] == VarClientID.MESLAYERMODE) { mode = (int) args[1]; }
						return null;
					case "setVarcStrValue": search = (String) args[1]; return null;
					case "getIntStack": return ints;
					case "getObjectStack": return objects;
					case "getIntStackSize": return stackSize;
					case "getObjectStackSize": return 1;
					case "getItemDefinition": return composition((int) args[0]);
					case "runScript":
						Object[] script = (Object[]) args[0];
						if ((int) script[0] == ScriptID.MESSAGE_LAYER_CLOSE)
						{
							mode = InputType.NONE.getType(); search = "";
						}
						else if ((int) script[0] == ScriptID.BANKMAIN_SEARCH_TOGGLE)
						{
							mode = InputType.SEARCH.getType();
						}
						rebuild();
						return null;
					default: return null;
				}
			});
			thread = new ClientThread()
			{
				@Override
				public void invoke(Runnable action) { action.run(); }
				@Override
				public void invokeLater(Runnable action) { action.run(); }
			};
			Constructor<BankSearch> constructor = BankSearch.class.getDeclaredConstructor(Client.class, ClientThread.class);
			constructor.setAccessible(true);
			BankSearch bankSearch = constructor.newInstance(client, thread);
			// ItemManager only reads item definitions in these tests; its background tasks stay dormant.
			Constructor<?> managerConstructor = ItemManager.class.getDeclaredConstructors()[0];
			managerConstructor.setAccessible(true);
			ScheduledExecutorService executor = proxy(ScheduledExecutorService.class, (p, m, a) -> null);
			ItemManager manager = (ItemManager) managerConstructor.newInstance(client, executor, thread, new EventBus(), null, null);
			EventBus eventBus = new EventBus()
			{
				@Override
				public void post(Object event) { posted.add(event); }
			};
			view = new BestGearSetupBankView(client, manager, bankSearch, eventBus);
		}

		private ItemComposition composition(int id)
		{
			return proxy(ItemComposition.class, (p, method, args) ->
			{
				switch (method.getName())
				{
					case "getNote": return -1;
					case "getPlaceholderTemplateId": return id == 4153 ? 14401 : -1;
					case "getPlaceholderId": return id == 4153 ? 12926 : -1;
					default: return null;
				}
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
			action.run(proxy(ScriptEvent.class, (p, m, a) -> m.getName().equals("getOp") ? op : null));
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
		private final Widget widget = proxy(Widget.class, (p, method, args) ->
		{
			String name = method.getName();
			if (name.equals("createChild"))
			{
				WidgetState child = new WidgetState(-1, 0, children.size());
				children.add(child);
				values.put("Children", children.stream().map(c -> c.widget).toArray(Widget[]::new));
				return child.widget;
			}
			if (name.equals("setAction"))
			{
				values.put("Action" + args[0], args[1]);
				return null;
			}
			if (name.equals("clearActions"))
			{
				values.keySet().removeIf(key -> key.startsWith("Action"));
				return null;
			}
			if (name.startsWith("set"))
			{
				values.put(name.equals("setHidden") ? "SelfHidden" : name.substring(3), args[0]);
				return method.getReturnType() == Widget.class ? p : null;
			}
			if (name.startsWith("get") || name.startsWith("is"))
			{
				Object value = values.get(name.substring(name.startsWith("get") ? 3 : 2));
				return value != null ? value : method.getReturnType() == boolean.class ? false
					: method.getReturnType() == int.class ? 0 : null;
			}
			return null;
		});

		WidgetState(int id, int quantity, int index)
		{
			values.put("ItemId", id);
			values.put("ItemQuantity", quantity);
			values.put("Index", index);
			values.put("DragDeadZone", 5);
			values.put("DragDeadTime", 5);
		}

		int value(String name) { return (int) values.get(name); }
	}
}
