package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.data.MonsterSummary;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.Test;

/** Transfers emit separate container events, but don't change what the player owns. */
public class ContainerTransferTest
{
	private final AtomicInteger reruns = new AtomicInteger();
	private final AtomicInteger supplySchedules = new AtomicInteger();
	private BestGearSetupPlugin plugin;

	@Before
	public void setUp() throws Exception
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
		ItemManager items = mock(ItemManager.class);
		when(items.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0));
		plugin = new BestGearSetupPlugin()
		{
			@Override public void rerun() { reruns.incrementAndGet(); }
			@Override void scheduleSupplyRefresh() { supplySchedules.incrementAndGet(); }
		};
		plugin.ownedItems = new OwnedItems(mock(ConfigManager.class), items);
		plugin.config = new BestGearSetupConfig() { @Override public boolean activeEncounter() { return false; } };
		Field client = BestGearSetupPlugin.class.getDeclaredField("client");
		client.setAccessible(true);
		client.set(plugin, mock(Client.class));
		plugin.gearIds = new HashSet<>(Arrays.asList(4151, 11840, 9242));
		plugin.consumableIds = new HashSet<>(Arrays.asList(9242));
		plugin.searchAmmoCount = 100_000;
	}

	private void container(int id, Item... items) throws Exception
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		plugin.onItemContainerChanged(new ItemContainerChanged(id, container));
		flushSwing();
	}

	private static void flushSwing() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> { });
	}

	private void searched(SearchMode mode)
	{
		plugin.ownedItems.rebuild(true);
		plugin.searchMode = mode;
		plugin.searchOwned = plugin.ownedItems.snapshot(mode);
		plugin.searchQuantities = plugin.ownedItems.quantitySnapshot(mode);
		plugin.lastSearched = new MonsterSummary();
		plugin.lastSearched.setName("Chambers of Xeric");
	}

	private void tick() throws Exception
	{
		plugin.onGameTick(new GameTick());
		flushSwing();
	}

	private void assertNoRefresh() throws Exception
	{
		assertEquals("A partial container update must not restart the raid search", 0, reruns.get());
		tick();
		tick();
		SwingUtilities.invokeAndWait(plugin::refreshSupply);
		assertEquals("Moving owned items must preserve the raid search", 0, reruns.get());
		assertEquals("Moving ammunition must not start a supply refresh", 0, supplySchedules.get());
	}

	@Test
	public void withdrawingLastGearAndAmmoDoesNotRefreshInEitherEventOrder() throws Exception
	{
		for (boolean bankFirst : new boolean[]{true, false})
		{
			container(InventoryID.BANK, new Item(4151, 1), new Item(9242, 159));
			searched(SearchMode.OWNED_ONLY);
			if (bankFirst) { container(InventoryID.BANK); }
			container(InventoryID.INV, new Item(4151, 1), new Item(9242, 159));
			if (!bankFirst) { container(InventoryID.BANK); }
			assertNoRefresh();
			plugin.lastSearched = null;
			container(InventoryID.INV);
		}
	}

	@Test
	public void depositingGearAndAmmoDoesNotRefreshInEitherEventOrder() throws Exception
	{
		for (boolean inventoryFirst : new boolean[]{true, false})
		{
			container(InventoryID.INV, new Item(4151, 1), new Item(9242, 159));
			searched(SearchMode.BUDGET);
			if (inventoryFirst) { container(InventoryID.INV); }
			container(InventoryID.BANK, new Item(4151, 1), new Item(9242, 159));
			if (!inventoryFirst) { container(InventoryID.INV); }
			assertNoRefresh();
			plugin.lastSearched = null;
			container(InventoryID.BANK);
		}
	}

	@Test
	public void equippingAndUnequippingDoesNotRefreshIncludingCarriedOnlyMode() throws Exception
	{
		for (SearchMode mode : SearchMode.values())
		{
			for (boolean inventoryFirst : new boolean[]{true, false})
			{
				container(InventoryID.INV, new Item(4151, 1), new Item(9242, 159));
				searched(mode);
				if (inventoryFirst) { container(InventoryID.INV); }
				container(InventoryID.WORN, new Item(4151, 1), new Item(9242, 159));
				if (!inventoryFirst) { container(InventoryID.INV); }
				assertNoRefresh();
				if (inventoryFirst) { container(InventoryID.INV, new Item(4151, 1), new Item(9242, 159)); }
				container(InventoryID.WORN);
				if (!inventoryFirst) { container(InventoryID.INV, new Item(4151, 1), new Item(9242, 159)); }
				assertNoRefresh();
				plugin.lastSearched = null;
				container(InventoryID.INV);
			}
		}
	}

	@Test
	public void partialAmmoWithdrawalsDoNotScheduleRefreshInEitherEventOrder() throws Exception
	{
		for (boolean bankFirst : new boolean[]{true, false})
		{
			container(InventoryID.BANK, new Item(9242, 159));
			searched(SearchMode.OWNED_ONLY);
			if (bankFirst) { container(InventoryID.BANK, new Item(9242, 100)); }
			container(InventoryID.INV, new Item(9242, 59));
			if (!bankFirst) { container(InventoryID.BANK, new Item(9242, 100)); }
			assertNoRefresh();
			plugin.lastSearched = null;
			container(InventoryID.INV);
		}
	}

	@Test
	public void genuineGearLossAndGainStillRefreshOnceSettled() throws Exception
	{
		container(InventoryID.INV, new Item(4151, 1));
		searched(SearchMode.OWNED_ONLY);
		container(InventoryID.INV);
		container(InventoryID.BANK, new Item(11840, 1));
		assertEquals(0, reruns.get());
		tick();
		tick();
		assertEquals(1, reruns.get());
		tick();
		tick();
		assertEquals(1, reruns.get());
	}

	@Test
	public void carriedOnlyWithdrawalStillRefreshesForNewlyAvailableGear() throws Exception
	{
		container(InventoryID.BANK, new Item(4151, 1));
		searched(SearchMode.INVENTORY_ONLY);
		container(InventoryID.BANK);
		container(InventoryID.INV, new Item(4151, 1));
		assertEquals(0, reruns.get());
		tick();
		tick();
		assertEquals(1, reruns.get());
	}

	@Test
	public void actualAmmoConsumptionStillSchedulesSettledRefresh() throws Exception
	{
		container(InventoryID.INV, new Item(9242, 159));
		searched(SearchMode.OWNED_ONLY);
		container(InventoryID.INV, new Item(9242, 158));
		tick();
		tick();
		assertEquals(0, reruns.get());
		assertEquals(1, supplySchedules.get());
		SwingUtilities.invokeAndWait(plugin::refreshSupply);
		assertEquals(1, reruns.get());
	}

	@Test
	public void splitTickWithdrawalsAndEquipDoNotRefreshInEitherEventOrder() throws Exception
	{
		for (boolean sourceFirst : new boolean[]{true, false})
		{
			plugin.lastSearched = null;
			container(InventoryID.INV); container(InventoryID.WORN);
			container(InventoryID.BANK, new Item(4151, 1), new Item(9242, 60));
			searched(SearchMode.OWNED_ONLY);
			if (sourceFirst) { container(InventoryID.BANK); }
			else { container(InventoryID.INV, new Item(4151, 1), new Item(9242, 60)); }
			tick(); // The paired packet has not arrived yet.
			assertEquals(0, reruns.get());
			if (sourceFirst) { container(InventoryID.INV, new Item(4151, 1), new Item(9242, 60)); }
			else { container(InventoryID.BANK); }
			assertNoRefresh();
			if (sourceFirst) { container(InventoryID.INV); }
			else { container(InventoryID.WORN, new Item(4151, 1), new Item(9242, 60)); }
			tick();
			if (sourceFirst) { container(InventoryID.WORN, new Item(4151, 1), new Item(9242, 60)); }
			else { container(InventoryID.INV); }
			assertNoRefresh();
		}
	}
}
