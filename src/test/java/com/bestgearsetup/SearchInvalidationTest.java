package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
