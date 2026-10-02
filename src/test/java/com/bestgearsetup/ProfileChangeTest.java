package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.PotionChoice;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.ui.BestGearSetupPanel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.RuneScapeProfileChanged;
import org.junit.Test;

public class ProfileChangeTest
{
	private static Object field(Object target, Class<?> type, String name) throws Exception
	{
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static void inject(BestGearSetupPlugin plugin, String name, Object value) throws Exception
	{
		Field field = BestGearSetupPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}

	@Test
	public void accountChangeCancelsActiveAndQueuedSearchesAndClearsResults() throws Exception
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override
			public java.util.Collection<net.runelite.client.config.ConfigItemDescriptor> getConfigItems()
			{
				return ConfigItemsForTests.items();
			}

			@Override
			public Map<Slot, SlotLock> getLocks()
			{
				return Collections.emptyMap();
			}

			@Override
			public Set<Integer> getExcluded()
			{
				return Collections.emptySet();
			}

			@Override
			public String getPotionChoice(CombatClass cls)
			{
				return PotionChoice.BEST;
			}
		};
		AtomicInteger generation = (AtomicInteger) field(plugin, BestGearSetupPlugin.class, "searchGeneration");
		AtomicInteger loads = new AtomicInteger();
		FutureTask<Void> active = new FutureTask<>(() -> null);
		OwnedItems owned = new OwnedItems(null, null)
		{
			@Override
			public void load()
			{
				// Invalidate old work before changing the ownership it observes.
				assertTrue(active.isCancelled());
				assertEquals(2, generation.get());
				loads.incrementAndGet();
			}
		};
		inject(plugin, "ownedItems", owned);
		List<Runnable> queued = new ArrayList<>();
		inject(plugin, "clientThread", new ClientThread()
		{
			@Override
			public void invokeLater(Runnable runnable)
			{
				queued.add(runnable);
			}
		});
		GameData data = new GameData(Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		inject(plugin, "gameData", data);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		inject(plugin, "executor", executor);
		try
		{
			BestGearSetupPanel[] panels = new BestGearSetupPanel[1];
			MonsterSummary summary = new MonsterSummary();
			summary.setName("test target");
			SwingUtilities.invokeAndWait(() ->
			{
				panels[0] = new BestGearSetupPanel(plugin, new BestGearSetupConfig() {}, null, null, null);
				panels[0].onDataLoaded(data);
			});
			BestGearSetupPanel panel = panels[0];
			inject(plugin, "panel", panel);
			SwingUtilities.invokeAndWait(() -> panel.selectMonster(summary, true));
			assertEquals(1, queued.size());
			assertEquals(1, generation.get());
			inject(plugin, "search", active);
			JPanel results = (JPanel) field(panel, BestGearSetupPanel.class, "resultsPanel");
			SwingUtilities.invokeAndWait(() -> results.add(new JLabel("Old account's recommendation")));
			plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged("old", "new"));
			// No Client or ItemManager is installed: stale callbacks must stop before reading either.
			queued.get(0).run();
			assertEquals(1, loads.get());
			assertNull(field(plugin, BestGearSetupPlugin.class, "lastSearched"));
			SwingUtilities.invokeAndWait(() -> assertEquals(0, results.getComponentCount()));
			JLabel status = (JLabel) field(panel, BestGearSetupPanel.class, "statusLabel");
			SwingUtilities.invokeAndWait(() -> assertTrue(status.getText().contains("Account changed")));
		}
		finally
		{
			executor.shutdownNow();
		}
	}
}
