package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.calc.PotionChoice;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.components.IconTextField;
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
				try
				{
					Constructor<OwnedItems> constructor = OwnedItems.class.getDeclaredConstructor(ConfigManager.class, ItemManager.class);
					constructor.setAccessible(true);
					return constructor.newInstance(null, null);
				}
				catch (ReflectiveOperationException e)
				{
					throw new AssertionError(e);
				}
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
			JTextField budget = field(panel, "budgetField", JTextField.class);
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
		GameData data = WikiGameData.get(new Gson()).gameData(new Gson());
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
			IconTextField search = field(panel, "searchField", IconTextField.class);
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

	private static <T> T field(Object owner, String name, Class<T> type)
	{
		try
		{
			Field f = BestGearSetupPanel.class.getDeclaredField(name);
			f.setAccessible(true);
			return type.cast(f.get(owner));
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
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
