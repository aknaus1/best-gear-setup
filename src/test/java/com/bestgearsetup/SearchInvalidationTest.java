package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.data.MonsterSummary;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

public class SearchInvalidationTest
{
	/** Main code gets the client's Gson in startUp; tests use the same instance. */
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
	}

	private static Set<Integer> ids(Integer... ids)
	{
		return new HashSet<>(Arrays.asList(ids));
	}

	@Test
	public void gainingOrLosingEquipmentInvalidatesResults()
	{
		Set<Integer> gear = ids(100, 200, 201);
		assertTrue(BestGearSetupPlugin.gearOwnershipDiffers(ids(100, 200), ids(100), gear));
		assertTrue(BestGearSetupPlugin.gearOwnershipDiffers(ids(100), ids(100, 201), gear));
	}

	@Test
	public void consumablesAndUnchangedGearDoNotInvalidateResults()
	{
		Set<Integer> gear = ids(100, 200);
		// e.g. a potion dose changing id, or food running out.
		assertFalse(BestGearSetupPlugin.gearOwnershipDiffers(ids(100, 2434), ids(100, 139), gear));
		assertFalse(BestGearSetupPlugin.gearOwnershipDiffers(ids(100, 200), ids(200, 100), gear));
	}

	@Test
	public void onlyStackChangesWithinTheRequestedQuantityMatter()
	{
		Set<Integer> bolts = ids(9242);
		Map<Integer, Long> held = Collections.singletonMap(9242, 159L);
		assertTrue(BestGearSetupPlugin.supplyDiffers(held, Collections.singletonMap(9242, 158L), bolts, 100_000));
		assertTrue(BestGearSetupPlugin.supplyDiffers(held, Collections.emptyMap(), bolts, 100_000));
		// Firing from a stack that still covers the requested quantity changes nothing.
		assertFalse(BestGearSetupPlugin.supplyDiffers(held, Collections.singletonMap(9242, 150L), bolts, 100));
		assertTrue(BestGearSetupPlugin.supplyDiffers(held, Collections.singletonMap(9242, 99L), bolts, 100));
		// Uncounted ammunition and non-supply items never trigger a refresh.
		assertFalse(BestGearSetupPlugin.supplyDiffers(held, Collections.singletonMap(9242, 1L), bolts, 0));
		assertFalse(BestGearSetupPlugin.supplyDiffers(held, Collections.singletonMap(9242, 1L), ids(868), 100_000));
	}

	private final AtomicInteger reruns = new AtomicInteger();
	/** Times the settle timer was (re)started; each restart pushes the refresh back. */
	private final AtomicInteger schedules = new AtomicInteger();
	private OwnedItems owned;
	private BestGearSetupPlugin plugin;

	/** A search captured with 159 Ruby bolts (e) held and 100,000 requested. */
	private void searchedWithBolts()
	{
		owned = new OwnedItems(null, null);
		owned.bank = Collections.singletonMap(9242, 159L);
		owned.rebuild(true);
		plugin = new BestGearSetupPlugin()
		{
			@Override
			public void rerun()
			{
				reruns.incrementAndGet();
			}

			@Override
			void scheduleSupplyRefresh()
			{
				schedules.incrementAndGet();
			}
		};
		plugin.ownedItems = owned;
		plugin.searchOwned = owned.snapshot();
		plugin.searchQuantities = owned.quantitySnapshot();
		plugin.searchAmmoCount = 100_000;
		plugin.lastSearched = new MonsterSummary();
		plugin.gearIds = ids(9242);
		plugin.consumableIds = ids(9242);
	}

	/** A container event: the bank holds this many bolts and the inventory this many lobsters. */
	private void containers(long bolts, long lobsters) throws Exception
	{
		Map<Integer, Long> bank = new HashMap<>();
		bank.put(9242, bolts);
		owned.bank = bank;
		owned.inventory = Collections.singletonMap(379, lobsters);
		owned.rebuild(false);
		plugin.invalidateIfOwnershipChanged();
		// Scheduling is queued on the Swing thread.
		SwingUtilities.invokeAndWait(() ->
		{
		});
	}

	/** The settle timer fires. */
	private void settle() throws Exception
	{
		SwingUtilities.invokeAndWait(plugin::refreshSupply);
	}

	/** The evaluator's scenario: one stack-only change to a quantity-dependent search reruns it once settled. */
	@Test
	public void changingOnlyStackQuantityRefreshesSearchOnceSettled() throws Exception
	{
		searchedWithBolts();
		// Three shots in quick succession restart one settle timer rather than starting three refreshes.
		for (long left : new long[]{158, 157, 156})
		{
			containers(left, 0);
		}
		assertEquals(3, schedules.get());
		assertEquals("No rerun before the stack settles", 0, reruns.get());
		settle();
		assertEquals(1, reruns.get());
		// The refresh records the stack it searched with, so the same stack doesn't schedule again.
		containers(156, 0);
		assertEquals(3, schedules.get());
		assertEquals(1, reruns.get());
	}

	/** Food and potion updates after the ammunition has settled must not keep postponing the refresh. */
	@Test
	public void unrelatedContainerEventsDoNotPostponeASettledSupplyRefresh() throws Exception
	{
		searchedWithBolts();
		containers(158, 0);
		for (long lobsters = 1; lobsters <= 16; lobsters++)
		{
			containers(158, lobsters);
		}
		assertEquals("Lobster changes must not restart the settle timer", 1, schedules.get());
		settle();
		assertEquals(1, reruns.get());
	}

	/** An Inventory + equipped only search ignores the bank, but withdrawing gear changes its result. */
	@Test
	public void inventoryOnlySearchesIgnoreBankChanges()
	{
		owned = new OwnedItems(null, null);
		owned.bank = Collections.singletonMap(4151, 1L);
		owned.rebuild(true);
		plugin = new BestGearSetupPlugin()
		{
			@Override
			public void rerun()
			{
				reruns.incrementAndGet();
			}
		};
		plugin.ownedItems = owned;
		plugin.searchMode = SearchMode.INVENTORY_ONLY;
		plugin.searchOwned = owned.snapshot(SearchMode.INVENTORY_ONLY);
		plugin.searchQuantities = owned.quantitySnapshot(SearchMode.INVENTORY_ONLY);
		plugin.lastSearched = new MonsterSummary();
		plugin.gearIds = ids(4151, 11840);

		owned.bank = Collections.singletonMap(11840, 1L);
		owned.rebuild(true);
		plugin.invalidateIfOwnershipChanged();
		assertEquals("Bank changes don't affect carried gear", 0, reruns.get());

		owned.inventory = Collections.singletonMap(4151, 1L);
		owned.rebuild(true);
		plugin.invalidateIfOwnershipChanged();
		assertEquals(1, reruns.get());
	}

	private static ItemContainer holding(Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		return container;
	}

	/** A search captured with an Abyssal whip carried, fed by real container events and game ticks. */
	private void searchedWithWhipCarried()
	{
		ItemManager itemManager = mock(ItemManager.class);
		when(itemManager.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0));
		ItemComposition notPlaceholder = mock(ItemComposition.class);
		when(notPlaceholder.getPlaceholderTemplateId()).thenReturn(-1);
		when(itemManager.getItemComposition(anyInt())).thenReturn(notPlaceholder);
		owned = new OwnedItems(mock(ConfigManager.class), itemManager);
		owned.onContainerChanged(InventoryID.BANK, holding());
		owned.onContainerChanged(InventoryID.INV, holding(new Item(4151, 1)));
		owned.rebuild(true);
		plugin = new BestGearSetupPlugin()
		{
			@Override
			public void rerun()
			{
				reruns.incrementAndGet();
			}
		};
		plugin.ownedItems = owned;
		plugin.searchOwned = owned.snapshot();
		plugin.searchQuantities = owned.quantitySnapshot();
		plugin.lastSearched = new MonsterSummary();
		plugin.gearIds = ids(4151);
	}

	/** Depositing empties the inventory before the bank fills; between the two events the whip isn't owned. */
	@Test
	public void depositingGearDoesNotRerunTheSearch()
	{
		searchedWithWhipCarried();
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, holding()));
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.BANK, holding(new Item(4151, 1))));
		plugin.onGameTick(new GameTick());
		assertEquals("The whip only moved to the bank", 0, reruns.get());
	}

	@Test
	public void losingGearRerunsTheSearchOnceItHasSettled()
	{
		searchedWithWhipCarried();
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, holding()));
		assertEquals("Compared only once the tick's events have landed", 0, reruns.get());
		plugin.onGameTick(new GameTick());
		assertEquals("The bank's half of a transfer may still be on its way", 0, reruns.get());
		plugin.onGameTick(new GameTick());
		assertEquals(1, reruns.get());
		plugin.onGameTick(new GameTick());
		assertEquals("Unchanged ownership compares nothing new", 1, reruns.get());
	}

	/** A deposit whose two container updates land on different ticks, in either order, moved nothing owned. */
	@Test
	public void depositSplitAcrossTicksDoesNotRerunTheSearch()
	{
		for (boolean inventoryFirst : new boolean[]{true, false})
		{
			searchedWithWhipCarried();
			if (inventoryFirst)
			{
				plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, holding()));
			}
			else
			{
				plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.BANK, holding(new Item(4151, 1))));
			}
			plugin.onGameTick(new GameTick());
			if (inventoryFirst)
			{
				plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.BANK, holding(new Item(4151, 1))));
			}
			else
			{
				plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, holding()));
			}
			plugin.onGameTick(new GameTick());
			plugin.onGameTick(new GameTick());
			plugin.onGameTick(new GameTick());
			assertEquals("inventory first: " + inventoryFirst, 0, reruns.get());
			assertTrue(owned.owns(4151));
		}
	}

	/** Equipping across a tick boundary: the whip leaves the inventory, then reaches the worn equipment. */
	@Test
	public void equipSplitAcrossTicksDoesNotRerunTheSearch()
	{
		searchedWithWhipCarried();
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, holding()));
		plugin.onGameTick(new GameTick());
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.WORN, holding(new Item(4151, 1))));
		plugin.onGameTick(new GameTick());
		plugin.onGameTick(new GameTick());
		assertEquals(0, reruns.get());
		assertTrue(owned.owns(4151));
		assertTrue(owned.isWorn(4151));
	}

	/** Gaining gear is published, and searched again, once it has held for two ticks. */
	@Test
	public void gainingGearRerunsTheSearchOnceItHasSettled()
	{
		searchedWithWhipCarried();
		plugin.gearIds = ids(4151, 11840);
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, holding(new Item(4151, 1), new Item(11840, 1))));
		plugin.onGameTick(new GameTick());
		assertFalse(owned.owns(11840));
		plugin.onGameTick(new GameTick());
		assertTrue(owned.owns(11840));
		assertEquals(1, reruns.get());
	}

	/** A stack that returns to the searched size before settling needs no refresh. */
	@Test
	public void aStackRestoredBeforeSettlingDoesNotRerun() throws Exception
	{
		searchedWithBolts();
		containers(158, 0);
		containers(159, 0);
		settle();
		assertEquals(0, reruns.get());
	}

}
