package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import com.bestgearsetup.data.MonsterSummary;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.Before;
import org.junit.Test;

/** A rerun is queued on the Swing thread; it must not resurrect a search that is no longer current. */
public class QueuedRerunTest
{
	private final List<MonsterSummary> started = new ArrayList<>();
	private final MonsterSummary target = new MonsterSummary();
	private BestGearSetupPlugin plugin;

	@Before
	public void setUp() throws Exception
	{
		plugin = new BestGearSetupPlugin()
		{
			@Override
			public void findBestSetup(MonsterSummary summary)
			{
				started.add(summary);
			}
		};
		set("lastSearched", target);
	}

	private Object field(String name) throws ReflectiveOperationException
	{
		Field field = BestGearSetupPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(plugin);
	}

	private void set(String name, Object value) throws ReflectiveOperationException
	{
		Field field = BestGearSetupPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}

	private void bump(String counter) throws ReflectiveOperationException
	{
		((AtomicInteger) field(counter)).incrementAndGet();
	}

	private static void flushSwing() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
		});
	}

	@Test
	public void currentRerunStartsTheSearch() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			plugin.rerun();
		});
		flushSwing();
		assertEquals(1, started.size());
	}

	@Test
	public void accountChangeDropsQueuedRerun() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			plugin.rerun();
			try
			{
				// What onRuneScapeProfileChanged does before the queued callback runs.
				bump("searchGeneration");
				set("lastSearched", null);
			}
			catch (ReflectiveOperationException e)
			{
				throw new AssertionError(e);
			}
		});
		flushSwing();
		assertEquals(0, started.size());
	}

	@Test
	public void shutdownOrNewerSearchDropsQueuedRerun() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			plugin.rerun();
			plugin.rerun();
			try
			{
				bump("lifecycle");
			}
			catch (ReflectiveOperationException e)
			{
				throw new AssertionError(e);
			}
		});
		flushSwing();
		assertEquals(0, started.size());

		SwingUtilities.invokeAndWait(() ->
		{
			plugin.rerun();
			try
			{
				bump("searchGeneration");
			}
			catch (ReflectiveOperationException e)
			{
				throw new AssertionError(e);
			}
		});
		flushSwing();
		assertEquals(0, started.size());
	}
}
