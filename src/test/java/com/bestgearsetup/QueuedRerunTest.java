package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import com.bestgearsetup.data.MonsterSummary;
import java.util.ArrayList;
import java.util.List;
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
	public void setUp()
	{
		plugin = new BestGearSetupPlugin()
		{
			@Override
			public void findBestSetup(MonsterSummary summary)
			{
				started.add(summary);
			}
		};
		plugin.lastSearched = target;
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
			// What onRuneScapeProfileChanged does before the queued callback runs.
			plugin.searchGeneration.incrementAndGet();
			plugin.lastSearched = null;
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
			plugin.lifecycle.incrementAndGet();
		});
		flushSwing();
		assertEquals(0, started.size());

		SwingUtilities.invokeAndWait(() ->
		{
			plugin.rerun();
			plugin.searchGeneration.incrementAndGet();
		});
		flushSwing();
		assertEquals(0, started.size());
	}
}
