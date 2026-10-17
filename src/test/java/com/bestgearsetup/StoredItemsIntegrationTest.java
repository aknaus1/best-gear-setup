package com.bestgearsetup;

import static com.bestgearsetup.StoredItemsBridgeTest.item;
import static com.bestgearsetup.StoredItemsBridgeTest.reply;
import static com.bestgearsetup.StoredItemsBridgeTest.storage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.data.MonsterSummary;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.Test;

/** Exercise the public plugin-message contract and client-thread ordering, including storage transfers. */
public class StoredItemsIntegrationTest
{
	private final AtomicInteger searches = new AtomicInteger();
	private final Queue<Runnable> callbacks = new ArrayDeque<>();
	private final EventBus events = new EventBus();
	private final BestGearSetupPlugin plugin = new BestGearSetupPlugin()
	{
		@Override public void rerun() { searches.incrementAndGet(); }
		@Override void scheduleSupplyRefresh() { searches.incrementAndGet(); }
	};
	private List<Map<String, Object>> storageData = Collections.emptyList();
	private boolean providerEnabled = true;

	public final class Provider
	{
		@Subscribe public void onPluginMessage(PluginMessage message)
		{
			if (providerEnabled && "storages-request".equals(message.getName()))
			{
				assertEquals("dudewheresmystuff", message.getNamespace());
				assertEquals("Best Gear Setup", message.getData().get("source"));
				callbacks.add(() -> events.post(reply(storageData)));
			}
		}
	}

	@Before public void setUp() throws Exception
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
		ConfigManager config = mock(ConfigManager.class);
		when(config.getRSProfileKey()).thenReturn("account-one");
		ItemManager items = mock(ItemManager.class);
		when(items.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0));
		plugin.ownedItems = new OwnedItems(config, items);
		plugin.config = new BestGearSetupConfig() { };
		Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		set("client", client);
		ClientThread clientThread = mock(ClientThread.class);
		doAnswer(call -> { callbacks.add(call.getArgument(0)); return null; }).when(clientThread).invokeLater(any(Runnable.class));
		set("clientThread", clientThread);
		plugin.storedItemsBridge = new StoredItemsBridge(config, events, plugin.ownedItems);
		events.register(plugin.storedItemsBridge);
		events.register(new Provider());
		plugin.gearIds = Set.of(4151, 11840, 9242);
		plugin.consumableIds = Set.of(9242);
		plugin.searchAmmoCount = 1000;
	}

	private void set(String name, Object value) throws Exception
	{
		Field field = BestGearSetupPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}

	private void tick() throws Exception
	{
		plugin.onGameTick(new GameTick());
		while (!callbacks.isEmpty()) { callbacks.remove().run(); }
		SwingUtilities.invokeAndWait(() -> { });
	}

	private void inventory(Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, container));
	}

	private void searched(SearchMode mode)
	{
		plugin.ownedItems.rebuild(true);
		plugin.searchMode = mode;
		plugin.searchOwned = plugin.ownedItems.snapshot(mode);
		plugin.searchQuantities = plugin.ownedItems.quantitySnapshot(mode);
		plugin.lastSearched = new MonsterSummary();
	}

	@Test public void withdrawingAndDepositingStorageGearAndAmmoNeverRestartsSearch() throws Exception
	{
		storageData = List.of(storage("death", "Vorkath", item(4151, 1), item(9242, 1000)));
		tick();
		searched(SearchMode.OWNED_ONLY);
		storageData = Collections.emptyList();
		inventory(new Item(4151, 1), new Item(9242, 1000));
		plugin.onGameTick(new GameTick());
		assertEquals("Wait for DWMS before comparing ownership", 0, searches.get());
		while (!callbacks.isEmpty()) { callbacks.remove().run(); }
		assertEquals(0, searches.get());
		assertTrue(plugin.ownedItems.getStoredLocations().isEmpty());
		storageData = List.of(storage("poh", "Armour Case", item(4151, 1), item(9242, 1000)));
		inventory();
		tick();
		assertEquals(0, searches.get());
		assertEquals(Long.valueOf(1000), plugin.ownedItems.quantitySnapshot().get(9242));
	}

	@Test public void lostStorageGearRefreshesOnceButIdenticalPollingDoesNot() throws Exception
	{
		storageData = List.of(storage("death", "Vorkath", item(4151, 1)));
		tick();
		searched(SearchMode.OWNED_ONLY);
		for (int i = 0; i < 10; i++) { tick(); }
		assertEquals(0, searches.get());
		storageData = Collections.emptyList();
		// No container changes: the loss arrives with the next unprompted refresh, then settles over two ticks.
		for (int i = 0; i < StoredItemsBridge.POLL_TICKS + 3; i++) { tick(); }
		assertEquals(1, searches.get());
		assertFalse(plugin.ownedItems.owns(4151));
	}

	@Test public void disconnectedProviderDropsStorageAndKeepsNativeOwnershipChecks() throws Exception
	{
		storageData = List.of(storage("stash", "Test", item(4151, 1)));
		tick();
		searched(SearchMode.OWNED_ONLY);
		providerEnabled = false;
		inventory(new Item(4151, 1));
		tick();
		assertFalse(plugin.ownedItems.isStorageConnected());
		assertEquals(0, searches.get());
		inventory();
		tick();
		tick();
		assertEquals(1, searches.get());
	}

	@Test public void storedGearNeverChangesInventoryOnlyResults() throws Exception
	{
		inventory(new Item(11840, 1));
		tick();
		searched(SearchMode.INVENTORY_ONLY);
		storageData = List.of(storage("stash", "Test", item(4151, 1), item(9242, 1000)));
		for (int i = 0; i < 5; i++) { tick(); }
		assertEquals(0, searches.get());
		assertFalse(plugin.ownedItems.snapshot(SearchMode.INVENTORY_ONLY).contains(4151));
	}
}
