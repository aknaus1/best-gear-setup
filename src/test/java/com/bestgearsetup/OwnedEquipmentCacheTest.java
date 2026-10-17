package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.Test;

/** Identical owned gear must retrieve the same cached result, including halfway through split-tick transfers. */
public class OwnedEquipmentCacheTest
{
	@org.junit.Before public void initializeCacheIdentity()
	{
		CacheIdentity.init(net.runelite.http.api.RuneLiteAPI.GSON);
	}

	private OwnedItems owned;
	private final BestGearSetupConfig config = new BestGearSetupConfig() { @Override public int ammoCount() { return 100; } };
	private GameData data;
	private final MonsterSummary target = new MonsterSummary();
	private final SearchCache<SearchInputs, Object> cache = new SearchCache<>(3);
	private final Object result = new Object();

	@Before public void setUp()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
		ItemManager items = mock(ItemManager.class);
		when(items.canonicalize(anyInt())).thenAnswer(call -> (int) call.getArgument(0) == 4152 ? 4151 : call.getArgument(0));
		owned = new OwnedItems(mock(ConfigManager.class), items);
		GearItem bolts = new GearItem(); bolts.setId(9242); bolts.setName("ruby bolts (e)");
		bolts.setSlot(Slot.AMMO); bolts.setSubcategory("bolt"); bolts.setRangedStr(103);
		GearItem whip = new GearItem(); whip.setId(4151); whip.setName("abyssal whip"); whip.setSlot(Slot.WEAPON);
		data = new GameData(Collections.emptyList(), Map.of(Slot.AMMO.getApiKey(), List.of(bolts)),
			List.of(whip), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		target.setId(1); target.setName("boss");
		container(InventoryID.BANK, new Item(4151, 1), new Item(9242, 60));
		owned.rebuild(true);
	}

	private void container(int id, Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		owned.onContainerChanged(id, container);
	}

	private SearchInputs key(SearchMode mode)
	{
		OwnedItems.Snapshot snapshot = owned.capture(mode);
		return SearchInputs.capture(data, data, target, PlayerLevels.maxed(), false, Collections.emptySet(), true,
			false, 99, Collections.emptyMap(), snapshot.getIds(), snapshot.getQuantities(), config,
			new LiveRaid(), Collections.emptyList(), false);
	}

	private void assertHit(SearchInputs original)
	{
		SearchInputs current = key(SearchMode.OWNED_ONLY);
		assertEquals("Owned input values must ignore locations", original, current);
		assertEquals("Transfers must keep exactly the same cache hash", original.hashCode(), current.hashCode());
		assertSame(result, cache.get(current, cache.epoch()));
	}

	@Test public void bankWithdrawDepositEquipAndUnequipKeepTheSameHashAcrossTicks()
	{
		SearchInputs original = key(SearchMode.OWNED_ONLY);
		cache.put(original, result, cache.epoch());
		OwnedItems.Snapshot originalSnapshot = owned.capture(SearchMode.OWNED_ONLY);
		container(InventoryID.BANK); owned.settle(); assertHit(original); // Bank packet arrives first.
		container(InventoryID.INV, new Item(4151, 1), new Item(9242, 60));
		owned.settle(); owned.settle(); assertHit(original);
		container(InventoryID.WORN, new Item(4151, 1), new Item(9242, 60));
		owned.settle(); assertHit(original); // Temporarily appears in both locations.
		container(InventoryID.INV); owned.settle(); owned.settle(); assertHit(original);
		container(InventoryID.WORN); owned.settle(); assertHit(original);
		container(InventoryID.INV, new Item(4151, 1), new Item(9242, 60));
		owned.settle(); owned.settle(); assertHit(original);
		container(InventoryID.BANK, new Item(4151, 1), new Item(9242, 60));
		owned.settle(); assertHit(original);
		container(InventoryID.INV); owned.settle(); owned.settle(); assertHit(original);
		assertSame("Location-only changes should reuse the immutable combined snapshot", originalSnapshot,
			owned.capture(SearchMode.OWNED_ONLY));
	}

	@Test public void partialStacksAndNotedWithdrawalsUseTheSameOwnedCategory()
	{
		SearchInputs original = key(SearchMode.OWNED_ONLY);
		cache.put(original, result, cache.epoch());
		container(InventoryID.BANK, new Item(9242, 20)); owned.settle(); assertHit(original);
		container(InventoryID.INV, new Item(4152, 1), new Item(9242, 40));
		owned.settle(); owned.settle(); assertHit(original);
		assertEquals(Long.valueOf(60), owned.capture(SearchMode.OWNED_ONLY).getQuantities().get(9242));
	}

	@Test public void tridentChargeStatesKeepCachedResultsAcrossBankAndEquipmentTransfers() throws Exception
	{
		data = WikiGameData.get(RuneLiteAPI.GSON).getEquipment();
		for (int[] states : new int[][]{
			{ItemID.TOTS, ItemID.TOTS_CHARGED, ItemID.TOTS_UNCHARGED},
			{ItemID.TOTS_ORN, ItemID.TOTS_CHARGED_ORN, ItemID.TOTS_UNCHARGED_ORN}})
		{
			container(InventoryID.BANK, new Item(states[0], 1));
			container(InventoryID.INV); container(InventoryID.WORN);
			owned.rebuild(true);
			SearchInputs original = key(SearchMode.OWNED_ONLY);
			cache.put(original, result, cache.epoch());
			container(InventoryID.BANK); owned.settle(); assertHit(original);
			container(InventoryID.INV, new Item(states[1], 1));
			owned.settle(); owned.settle(); assertHit(original);
			SearchInputs carried = key(SearchMode.INVENTORY_ONLY);
			container(InventoryID.WORN, new Item(states[2], 1));
			container(InventoryID.INV); owned.settle(); owned.settle(); assertHit(original);
			assertEquals(carried, key(SearchMode.INVENTORY_ONLY));
			container(InventoryID.WORN); container(InventoryID.BANK, new Item(states[0], 1));
			owned.settle(); owned.settle(); assertHit(original);
		}
	}

	@Test public void storedGearLocationAndRecordedTimestampDoNotEnterTheCacheHash()
	{
		SearchInputs original = key(SearchMode.OWNED_ONLY);
		cache.put(original, result, cache.epoch());
		container(InventoryID.BANK);
		owned.setStoredLocations(List.of(new StoredLocation("poh", "Armour Case", 1234,
			Map.of(4151, 1L, 9242, 60L))), true);
		owned.settle(); owned.settle(); assertHit(original);
		owned.setStoredLocations(List.of(new StoredLocation("death", "Vorkath", 5678,
			Map.of(4151, 1L, 9242, 60L))), true);
		owned.settle(); owned.settle(); assertHit(original);
	}

	@Test public void actualGearChangesAmmoShortfallsAndCarriedAvailabilityStillChangeKeys()
	{
		SearchInputs original = key(SearchMode.OWNED_ONLY);
		SearchInputs carried = key(SearchMode.INVENTORY_ONLY);
		container(InventoryID.BANK);
		container(InventoryID.INV, new Item(4151, 1), new Item(9242, 60));
		owned.settle(); owned.settle();
		assertEquals(original, key(SearchMode.OWNED_ONLY));
		assertNotEquals(carried, key(SearchMode.INVENTORY_ONLY));
		container(InventoryID.INV, new Item(4151, 1), new Item(9242, 59));
		owned.settle(); owned.settle();
		assertNotEquals(original, key(SearchMode.OWNED_ONLY));
		SearchInputs beforeLoss = key(SearchMode.OWNED_ONLY);
		container(InventoryID.INV, new Item(9242, 59)); owned.settle(); owned.settle();
		assertNotEquals(beforeLoss, key(SearchMode.OWNED_ONLY));
		assertTrue(owned.capture(SearchMode.OWNED_ONLY).getIds().contains(9242));
	}
}
