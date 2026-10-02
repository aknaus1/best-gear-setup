package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.MonsterSummary;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class SearchInvalidationTest
{
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
	private OwnedItems owned;
	private BestGearSetupPlugin plugin;
	private Method invalidate;

	/** A search captured with 159 Ruby bolts (e) held and 100,000 requested. */
	private void searchedWithBolts(int settleMillis) throws Exception
	{
		owned = new OwnedItems(null, null);
		set(owned, OwnedItems.class, "bank", Collections.singletonMap(9242, 159L));
		rebuild(owned, true);
		plugin = new BestGearSetupPlugin()
		{
			@Override
			public void rerun()
			{
				reruns.incrementAndGet();
			}
		};
		plugin.supplySettleMillis = settleMillis;
		set(plugin, BestGearSetupPlugin.class, "ownedItems", owned);
		set(plugin, BestGearSetupPlugin.class, "searchOwned", owned.snapshot());
		set(plugin, BestGearSetupPlugin.class, "searchQuantities", owned.quantitySnapshot());
		set(plugin, BestGearSetupPlugin.class, "searchAmmoCount", 100_000);
		set(plugin, BestGearSetupPlugin.class, "lastSearched", new MonsterSummary());
		set(plugin, BestGearSetupPlugin.class, "gearIds", ids(9242));
		set(plugin, BestGearSetupPlugin.class, "consumableIds", ids(9242));
		invalidate = BestGearSetupPlugin.class.getDeclaredMethod("invalidateIfOwnershipChanged");
		invalidate.setAccessible(true);
	}

	/** A container event: the bank holds this many bolts and the inventory this many lobsters. */
	private void containers(long bolts, long lobsters) throws Exception
	{
		Map<Integer, Long> bank = new HashMap<>();
		bank.put(9242, bolts);
		set(owned, OwnedItems.class, "bank", bank);
		set(owned, OwnedItems.class, "inventory", Collections.singletonMap(379, lobsters));
		rebuild(owned, false);
		invalidate.invoke(plugin);
	}

	private static void waitFor(long millis) throws Exception
	{
		long end = System.currentTimeMillis() + millis;
		while (System.currentTimeMillis() < end)
		{
			Thread.sleep(10);
			SwingUtilities.invokeAndWait(() ->
			{
			});
		}
	}

	private void stopTimer() throws Exception
	{
		Field field = BestGearSetupPlugin.class.getDeclaredField("supplyRefresh");
		field.setAccessible(true);
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				javax.swing.Timer timer = (javax.swing.Timer) field.get(plugin);
				if (timer != null)
				{
					timer.stop();
				}
			}
			catch (IllegalAccessException e)
			{
				throw new AssertionError(e);
			}
		});
	}

	/** The evaluator's scenario: one stack-only change to a quantity-dependent search reruns it once settled. */
	@Test
	public void changingOnlyStackQuantityRefreshesSearchOnceSettled() throws Exception
	{
		searchedWithBolts(50);
		// Three shots in quick succession coalesce into one refresh.
		for (long left : new long[]{158, 157, 156})
		{
			containers(left, 0);
		}
		assertEquals("No rerun before the stack settles", 0, reruns.get());
		waitFor(300);
		assertEquals(1, reruns.get());
		// The refresh records the stack it searched with, so the same stack doesn't rerun again.
		containers(156, 0);
		waitFor(150);
		assertEquals(1, reruns.get());
		stopTimer();
	}

	/** Food and potion updates after the ammunition has settled must not keep postponing the refresh. */
	@Test
	public void unrelatedContainerEventsDoNotPostponeASettledSupplyRefresh() throws Exception
	{
		searchedWithBolts(200);
		containers(158, 0);
		// Lobster stack changes every 50ms for 800ms: four whole settle intervals with no ammunition change.
		for (long lobsters = 1; lobsters <= 16; lobsters++)
		{
			waitFor(50);
			containers(158, lobsters);
		}
		assertEquals(1, reruns.get());
		stopTimer();
	}

	/** A stack that returns to the searched size before settling needs no refresh. */
	@Test
	public void aStackRestoredBeforeSettlingDoesNotRerun() throws Exception
	{
		searchedWithBolts(100);
		containers(158, 0);
		containers(159, 0);
		waitFor(300);
		assertEquals(0, reruns.get());
		stopTimer();
	}

	private static void set(Object target, Class<?> type, String name, Object value) throws Exception
	{
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static void rebuild(OwnedItems owned, boolean itemsChanged) throws Exception
	{
		Method method = OwnedItems.class.getDeclaredMethod("rebuild", boolean.class);
		method.setAccessible(true);
		method.invoke(owned, itemsChanged);
	}
}
