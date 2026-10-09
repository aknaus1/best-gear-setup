package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.PotionChoice;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

/** Rejected input and empty searches explain themselves; estimate-changing assumptions sit beside the results. */
public class PanelFeedbackTest
{
	private static BestGearSetupPlugin plugin()
	{
		return new BestGearSetupPlugin()
		{
			@Override
			public java.util.Collection<net.runelite.client.config.ConfigItemDescriptor> getConfigItems()
			{
				return com.bestgearsetup.ConfigItemsForTests.items();
			}

			@Override
			public OwnedItems getOwnedItems()
			{
				return com.bestgearsetup.ConfigItemsForTests.emptyOwnedItems();
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

			@Override
			public void setConfig(String key, Object value)
			{
			}
		};
	}

	@Test
	public void invalidBudgetIsExplainedAndTheSavedBudgetKept() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			BestGearSetupConfig config = new BestGearSetupConfig()
			{
			};
			BestGearSetupPanel panel = new BestGearSetupPanel(plugin(), config, null, null, null);
			JTextField budget = panel.budgetField;
			JLabel error = (JLabel) named(panel, "budgetError");
			assertFalse(error.isVisible());
			budget.setText("nonsense");
			budget.postActionEvent();
			assertEquals(config.budget(), budget.getText());
			assertTrue(error.isVisible());
			assertTrue(error.getText().contains("nonsense"));
			assertTrue(error.getText().contains("750k"));
			budget.setText(config.budget());
			budget.postActionEvent();
			assertFalse(error.isVisible());
		});
	}

	@Test
	public void emptySearchesSayWhyAndWhichTargetIsKept() throws Exception
	{
		GameData data = WikiGameData.get(RuneLiteAPI.GSON).gameData(RuneLiteAPI.GSON);
		SwingUtilities.invokeAndWait(() ->
		{
			BestGearSetupPanel panel = new BestGearSetupPanel(plugin(), new BestGearSetupConfig()
			{
				@Override
				public boolean bossesOnly()
				{
					return true;
				}
			}, null, null, null);
			panel.onDataLoaded(data);
			IconTextField search = panel.searchField;
			JLabel hint = (JLabel) named(panel, "noMatches");
			search.setText("zzzz-no-monster");
			assertTrue(hint.isVisible());
			assertTrue(hint.getText().contains("No matching monsters"));
			search.setText("dust devil");
			assertTrue("Bosses filter hides dust devils", hint.isVisible());
			assertTrue(hint.getText().contains("Search all monsters"));
			search.setText("vorkath");
			assertFalse(hint.isVisible());
		});
		assertTrue(BestGearSetupPanel.noMatchesMessage(false, "Vorkath").contains("Still targeting <b>Vorkath</b>"));
	}

	@Test
	public void assumptionWarningsAreShownWithTheResults() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			BestGearSetupPanel panel = new BestGearSetupPanel(plugin(), new BestGearSetupConfig()
			{
			}, null, null, null);
			panel.showResults(new SearchResults(new Monster(), 100, Collections.emptyMap(), Collections.emptyList(),
				Collections.emptyMap(), Collections.emptyMap(), false, Collections.emptyList(), false, false,
				Collections.singletonList("Ranged potion picked by name: Smelling Salts - only usable in the Tombs of Amascut.")));
			JLabel warnings = (JLabel) named(panel, "assumptionWarnings");
			assertNotNull(warnings);
			assertTrue(warnings.isVisible());
			assertTrue(warnings.getText().contains("Smelling Salts"));
		});
	}

	@Test
	public void locksAreNotListedAboveResultsButABlockedLockIsExplained() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			BestGearSetupPanel panel = new BestGearSetupPanel(plugin(), new BestGearSetupConfig()
			{
			}, null, null, null);
			LockStatus used = new LockStatus(Slot.FEET, SlotLock.item(1), null, null, null);
			LockStatus blocked = new LockStatus(Slot.HEAD, SlotLock.item(2), null, "its requirements are too high", null);
			panel.showResults(new SearchResults(new Monster(), 100, Collections.emptyMap(), Collections.emptyList(),
				Collections.emptyMap(), Collections.emptyMap(), false, Arrays.asList(used, blocked), false, false,
				Collections.emptyList()));
			assertTrue("No lock rows above the results", named(panel, "lockFEET") == null && named(panel, "lockHEAD") == null);
			String status = panel.statusLabel.getText();
			assertTrue(status, status.contains("Head lock can't be used: its requirements are too high. Remove it under Gear."));
			assertFalse(status, status.contains("Feet lock"));
		});
	}


	private static Component named(Container container, String name)
	{
		for (Component child : container.getComponents())
		{
			if (name.equals(child.getName()))
			{
				return child;
			}
			if (child instanceof Container)
			{
				Component found = named((Container) child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
