package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.bestgearsetup.calc.SearchMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.Test;

public class StoredItemsBridgeTest
{
	private final AtomicReference<String> profile = new AtomicReference<>("account-one");
	private OwnedItems owned;
	private StoredItemsBridge bridge;

	@Before
	public void setUp()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
		ConfigManager config = mock(ConfigManager.class);
		when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
		ItemManager items = mock(ItemManager.class);
		when(items.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0));
		owned = new OwnedItems(config, items);
		bridge = new StoredItemsBridge(config, new EventBus(), owned);
	}

	static Map<String, Object> storage(String category, String name, Object... items)
	{
		return Map.of("category", category, "name", name, "lastUpdated", 1234L, "items", Arrays.asList(items));
	}

	static Map<String, Object> item(int id, long quantity)
	{
		return Map.of("id", id, "quantity", quantity);
	}

	static PluginMessage reply(Object storages)
	{
		return new PluginMessage("dudewheresmystuff", "storages-response", Map.of(
			"source", "Dude, Where's My Stuff?", "target", "Best Gear Setup", "version", 1, "storages", storages));
	}

	private void refresh(Object... storages)
	{
		assertTrue(bridge.request(true));
		bridge.onPluginMessage(reply(Arrays.asList(storages)));
		bridge.complete();
		owned.settle(); owned.settle();
	}

	@Test
	public void storageGearAndQuantitiesJoinOwnershipButNeverCarriedOnly()
	{
		owned.bank = Map.of(4151, 1L);
		owned.inventory = Map.of(9242, 40L);
		owned.worn = Map.of(11840, 1L);
		owned.rebuild(true);
		refresh(storage("stash", "Warriors' Guild bank (master)", item(11865, 1)),
			storage("poh", "Magic Wardrobe", item(21791, 1)),
			storage("death", "Vorkath", item(12926, 1)),
			storage("carryable", "Bolt Pouch", item(9242, 60)),
			storage("world", "Group Storage", item(11802, 1)));

		assertTrue(owned.isStorageConnected());
		assertTrue(owned.owns(21791));
		assertTrue(owned.owns(12926));
		assertEquals(Long.valueOf(100), owned.quantitySnapshot().get(9242));
		assertEquals(7, owned.count());
		assertEquals(5, owned.getStoredLocations().size());
		assertFalse(owned.snapshot(SearchMode.INVENTORY_ONLY).contains(12926));
		assertEquals(Map.of(9242, 40L, 11840, 1L), owned.quantitySnapshot(SearchMode.INVENTORY_ONLY));
		assertEquals(Collections.singleton("POH: Magic Wardrobe"), owned.locations(21791, Collections.emptyList()));
		assertEquals(Arrays.asList("Inventory", "Bolt Pouch"),
			List.copyOf(owned.locations(9242, Collections.emptyList())));
	}

	@Test
	public void excludesDuplicateContainersAndNonEquipmentDisplays()
	{
		owned.bank = Map.of(4151, 1L);
		owned.inventory = Map.of(9242, 40L);
		owned.worn = Map.of(11840, 1L);
		owned.rebuild(true);
		refresh(storage("world", "Bank", item(4151, 1)),
			storage("carryable", "Inventory", item(9242, 40)),
			storage("carryable", "Equipment", item(11840, 1)),
			storage("poh", "Cape Hanger", item(21791, 1)),
			storage("poh", "Menagerie", item(11865, 1)),
			storage("poh", "Boss Lair Display", item(11802, 1)),
			storage("world", "Log Storage", item(1511, 100)),
			storage("coins", "Inventory", item(995, 100)),
			storage("minigames", "Nightmare Zone", item(12926, 1)));
		assertEquals(Map.of(4151, 1L, 9242, 40L, 11840, 1L), owned.quantitySnapshot());
		assertTrue(owned.getStoredLocations().isEmpty());
	}

	@Test
	public void everyResponseReplacesStorageIncludingEmptyAndDisconnected()
	{
		refresh(storage("death", "Vorkath", item(4151, 1)));
		assertTrue(owned.owns(4151));
		refresh(); // Provider filters reclaimed or lost deathbanks out of the full snapshot.
		assertFalse(owned.owns(4151));
		assertTrue(owned.isStorageConnected());
		refresh(storage("poh", "Cape Rack", item(21791, 1)));
		bridge.request(true);
		bridge.complete(); // Disabled, missing, or older provider does not reply.
		owned.settle(); owned.settle();
		assertFalse(owned.isStorageConnected());
		assertFalse(owned.owns(21791));
		assertFalse(bridge.complete());
	}

	@Test
	public void wrongVersionRecipientNamespaceAndUnsolicitedMessagesAreIgnored()
	{
		PluginMessage valid = reply(List.of(storage("stash", "Test", item(4151, 1))));
		bridge.onPluginMessage(valid);
		assertFalse(bridge.complete());
		for (PluginMessage bad : List.of(
			new PluginMessage("other", "storages-response", valid.getData()),
			new PluginMessage("dudewheresmystuff", "other", valid.getData()),
			new PluginMessage("dudewheresmystuff", "storages-response", Map.of(
				"source", "Dude, Where's My Stuff?", "target", "Other Plugin", "version", 1, "storages", valid.getData().get("storages"))),
			new PluginMessage("dudewheresmystuff", "storages-response", Map.of(
				"source", "Dude, Where's My Stuff?", "target", "Best Gear Setup", "version", 2, "storages", valid.getData().get("storages")))))
		{
			bridge.request(true);
			bridge.onPluginMessage(bad);
			bridge.complete();
			assertFalse(owned.owns(4151));
			assertFalse(owned.isStorageConnected());
		}
	}

	@Test
	public void accountSwitchRejectsPendingReplyAndClearsPriorLocations()
	{
		refresh(storage("poh", "Armour Case", item(4151, 1)));
		bridge.request(true);
		profile.set("account-two");
		owned.load();
		bridge.onPluginMessage(reply(List.of(storage("death", "Vorkath", item(11802, 1)))));
		assertFalse(bridge.complete());
		assertFalse(owned.owns(4151));
		assertFalse(owned.owns(11802));
		assertTrue(owned.getStoredLocations().isEmpty());
		bridge.reset();
		refresh(storage("stash", "Other account", item(11840, 1)));
		assertTrue(owned.owns(11840));
	}

	@Test
	public void unchangedSnapshotDoesNotRebuildAndTransfersBypassPollingInterval()
	{
		List<Object> payload = List.of(storage("death", "Vorkath", item(4151, 1)));
		bridge.request(false);
		bridge.onPluginMessage(reply(payload));
		assertTrue(bridge.complete());
		owned.settle(); owned.settle();
		Map<Integer, Long> previous = owned.quantitySnapshot();
		for (int i = 0; i < StoredItemsBridge.POLL_TICKS; i++) { assertFalse(bridge.request(false)); }
		assertTrue(bridge.request(false));
		bridge.onPluginMessage(reply(payload));
		assertFalse(bridge.complete());
		assertTrue(previous == owned.quantitySnapshot());
		assertTrue(bridge.request(true));
		assertFalse(bridge.request(true)); // One outstanding request only.
	}

	@Test
	public void invalidStacksAreIgnoredAndMalformedMessagesNeverPartiallyImport()
	{
		refresh(storage("stash", "Test", item(-1, 1), item(11840, 0), item(11865, -1), item(4151, 1)));
		assertEquals(Map.of(4151, 1L), owned.quantitySnapshot());
		assertNull(StoredItemsBridge.parse("unsupported"));
		assertNull(StoredItemsBridge.parse(List.of(storage("stash", "Test", item(4151, 1)), "invalid")));
		assertNull(StoredItemsBridge.parse(List.of(storage("stash", "Test", Map.of("id", 4151, "quantity", "unknown")))));
	}

	@Test
	public void rememberedVariantsDoNotCreatePaidUpgradesAndManualQuantityStaysUnlimited()
	{
		refresh(storage("stash", "Test", item(4081, 1), item(9242, Long.MAX_VALUE), item(9242, 1)));
		assertFalse(owned.owns(12018)); // Ordinary salve does not grant salve (ei).
		assertEquals(Long.valueOf(Long.MAX_VALUE), owned.quantitySnapshot().get(9242));
		owned.setManual(9242, true);
		refresh();
		assertEquals(Long.valueOf(Long.MAX_VALUE), owned.quantitySnapshot().get(9242));
	}
}
