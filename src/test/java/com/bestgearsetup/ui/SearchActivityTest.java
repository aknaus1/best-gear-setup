package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.ConfigItemsForTests;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SearchActivityTest
{
	@Test public void animationAndElapsedTimeContinueWithoutPercentageUpdates() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			AtomicLong clock = new AtomicLong();
			SearchActivity activity = new SearchActivity(clock::get);
			activity.start();
			activity.stage("Comparing equipment");
			BufferedImage before = icon(activity);
			clock.set(12_000_000_000L);
			activity.tick();
			assertTrue(activity.isRunning());
			assertTrue(activity.getText().contains("Comparing equipment"));
			assertTrue(activity.getText().contains("12s"));
			BufferedImage after = icon(activity);
			boolean changed = false;
			for (int y = 0; y < before.getHeight(); y++)
			{
				for (int x = 0; x < before.getWidth(); x++)
				{ changed |= before.getRGB(x, y) != after.getRGB(x, y); }
			}
			assertTrue("Spinner must animate while the percent is unchanged", changed);
			clock.set(75_000_000_000L);
			activity.stage("Packing raid kit");
			assertTrue(activity.getText().contains("1m 15s"));
			activity.stop();
			String stopped = activity.getText();
			activity.stage("Stale update"); activity.tick();
			assertEquals(stopped, activity.getText());
			assertFalse(activity.isRunning()); assertFalse(activity.isVisible());
			activity.start();
			assertTrue(activity.getText().contains("Preparing search"));
			assertTrue(activity.getText().contains("0s"));
			activity.stop();
		});
	}

	@Test public void panelStopsActivityOnFailureClearingAndResultsAndIgnoresLateUpdates() throws Exception
	{
		BestGearSetupPlugin plugin = mock(BestGearSetupPlugin.class);
		net.runelite.client.config.ConfigDescriptor descriptor = ConfigItemsForTests.descriptor();
		when(plugin.getConfigDescriptor()).thenReturn(descriptor);
		com.bestgearsetup.OwnedItems owned = ConfigItemsForTests.emptyOwnedItems();
		when(plugin.getOwnedItems()).thenReturn(owned);
		SwingUtilities.invokeAndWait(() ->
		{
			BestGearSetupPanel panel = new BestGearSetupPanel(plugin, new BestGearSetupConfig() {}, null, null, null);
			JProgressBar bar = (JProgressBar) named(panel, "searchProgress");
			SearchActivity activity = (SearchActivity) named(panel, "searchActivity");
			MonsterSummary target = new MonsterSummary(); target.setName("zebak");
			panel.showSearching(target);
			panel.showProgress(42); panel.showSearchActivity("Comparing equipment");
			panel.showEncounterProgress("Planning ToA: Zebak");
			assertTrue(activity.isRunning()); assertTrue(activity.isVisible());
			assertEquals(4200, bar.getValue());
			assertTrue(panel.statusLabel.getText().contains("Planning ToA: Zebak"));
			panel.showProgress(42.17); assertEquals(4217, bar.getValue());
			assertEquals("42.17%", bar.getString());
			panel.showProgress(20); assertEquals(4217, bar.getValue());
			panel.showProgress(100); assertEquals(9999, bar.getValue());
			panel.showError("Search failed");
			assertFalse(activity.isRunning()); assertFalse(bar.getParent().isVisible());
			panel.showSearchActivity("Stale update"); panel.showProgress(65);
			assertFalse(activity.getText().contains("Stale update"));
			assertTrue(panel.statusLabel.getText().contains("Search failed"));
			panel.showSearching(target); assertEquals(0, bar.getValue());
			panel.clearSearch(); assertFalse(activity.isRunning());
			panel.showSearching(target);
			Monster monster = new Monster(); monster.setName("zebak");
			panel.showResults(new SearchResults(monster, 100, Collections.emptyMap(), Collections.emptyList(),
				Collections.emptyMap(), Collections.emptyMap(), false));
			assertFalse(activity.isRunning()); assertFalse(activity.isVisible());
			panel.showSearching(target); panel.stopSearchActivity();
			assertFalse(activity.isRunning());
		});
	}

	@Test public void newSearchKeepsTheBankLayoutUntilItsResultsReplaceIt() throws Exception
	{
		BestGearSetupPlugin plugin = mock(BestGearSetupPlugin.class);
		net.runelite.client.config.ConfigDescriptor descriptor = ConfigItemsForTests.descriptor();
		when(plugin.getConfigDescriptor()).thenReturn(descriptor);
		com.bestgearsetup.OwnedItems owned = ConfigItemsForTests.emptyOwnedItems();
		when(plugin.getOwnedItems()).thenReturn(owned);
		SwingUtilities.invokeAndWait(() ->
		{
			BestGearSetupPanel panel = new BestGearSetupPanel(plugin, new BestGearSetupConfig() {}, null, null, null);
			MonsterSummary target = new MonsterSummary(); target.setName("zebak");
			javax.swing.JLabel previous = new javax.swing.JLabel("Vorkath results");
			panel.resultsPanel.add(previous);
			clearInvocations(plugin);
			panel.showSearching(target);
			verify(plugin, never()).setBankHighlightedSetup(null);
			assertTrue("Previous results stay up while searching", previous.getParent() == panel.resultsPanel);
			// With no setup found there is nothing to replace the old layout with.
			Monster monster = new Monster(); monster.setName("zebak");
			panel.showResults(new SearchResults(monster, 100, Collections.emptyMap(), Collections.emptyList(),
				Collections.emptyMap(), Collections.emptyMap(), false));
			verify(plugin).setBankHighlightedSetup(null);
			clearInvocations(plugin);
			panel.clearResults("Account changed");
			verify(plugin).setBankHighlightedSetup(null);
		});
	}

	private static BufferedImage icon(SearchActivity activity)
	{
		BufferedImage image = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		activity.getIcon().paintIcon(activity, graphics, 0, 0);
		graphics.dispose();
		return image;
	}

	private static Component named(Container parent, String name)
	{
		for (Component child : parent.getComponents())
		{
			if (name.equals(child.getName())) { return child; }
			if (child instanceof Container)
			{
				Component match = named((Container) child, name);
				if (match != null) { return match; }
			}
		}
		return null;
	}
}
