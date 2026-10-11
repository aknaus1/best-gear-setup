package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.ConfigItemsForTests;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

public class GearRulesTest
{
	private static final GameData DATA = load();

	private final Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
	private final Set<Integer> excluded = new LinkedHashSet<>();
	private final List<String> cleared = new ArrayList<>();
	private final BestGearSetupPlugin plugin = new BestGearSetupPlugin()
	{
		@Override
		public GameData getGameData()
		{
			return DATA;
		}

		@Override
		public Map<Slot, SlotLock> getLocks()
		{
			return new EnumMap<>(locks);
		}

		@Override
		public Set<Integer> getExcluded()
		{
			return new LinkedHashSet<>(excluded);
		}

		@Override
		public void setExcluded(int itemId, boolean exclude)
		{
			if (exclude)
			{
				excluded.add(itemId);
			}
			else
			{
				excluded.remove(itemId);
			}
		}

		@Override
		public void clearLocks()
		{
			cleared.add("locks");
		}

		@Override
		public void clearExclusions()
		{
			cleared.add("exclusions");
		}

		@Override
		public OwnedItems getOwnedItems()
		{
			return ConfigItemsForTests.emptyOwnedItems();
		}

		@Override
		public boolean owns(GearItem item)
		{
			return false;
		}
	};

	private static GameData load()
	{
		try
		{
			return WikiGameData.get(RuneLiteAPI.GSON).gameData(RuneLiteAPI.GSON);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	@Test
	public void locksAndExclusionsAreListedAndClearedSeparately() throws Exception
	{
		GearItem lightbearer = DATA.searchItems("lightbearer", 1, Slot.RING).get(0);
		GearItem torva = DATA.searchItems("torva full helm", 1, Slot.HEAD).get(0);
		locks.put(Slot.RING, SlotLock.item(lightbearer.getId()));
		locks.put(Slot.SHIELD, SlotLock.empty());
		excluded.add(torva.getId());
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			panel.refresh();
			Container lockCard = (Container) named(panel, "locksContent");
			Container excludedCard = (Container) named(panel, "exclusionsContent");
			String helm = GameData.titleCase(torva.getName());
			assertNotNull(label(lockCard, GameData.titleCase(lightbearer.getName())));
			assertNotNull(label(lockCard, "Keep empty"));
			assertNull("Exclusions are not listed with locks", label(lockCard, helm));
			assertNotNull(label(excludedCard, helm));
			assertEquals("Locks (2)", ((JLabel) ((Container) named(panel, "locksSection")).getComponent(1)).getText());

			button(lockCard, "Clear locks").doClick();
			assertEquals("Clearing locks keeps exclusions", List.of("locks"), cleared);
			assertNull("One exclusion needs no Clear all", button(excludedCard, "Clear exclusions"));
		});
	}

	@Test
	public void anyItemCanBeExcludedFromTheGearView() throws Exception
	{
		GearItem lightbearer = DATA.searchItems("lightbearer", 1, Slot.RING).get(0);
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			panel.refresh();
			IconTextField search = (IconTextField) named(panel, "excludeSearch");
			Container suggestions = (Container) named(panel, "excludeSuggestions");
			search.setText("lightbear");
			JButton suggestion = button(suggestions, "Lightbearer");
			assertEquals("Lightbearer (Ring)", suggestion.getText());
			suggestion.doClick();
			assertTrue(excluded.contains(lightbearer.getId()));
			assertEquals("", search.getText());

			search.setText("lightbear");
			assertFalse("Excluded items aren't offered again", button(suggestions, "Lightbearer") != null);
		});
	}

	private static Component named(Container root, String name)
	{
		for (Component c : root.getComponents())
		{
			if (name.equals(c.getName()))
			{
				return c;
			}
			if (c instanceof Container)
			{
				Component found = named((Container) c, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static JLabel label(Container root, String text)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JLabel && text.equals(((JLabel) c).getText()))
			{
				return (JLabel) c;
			}
			if (c instanceof Container)
			{
				JLabel found = label((Container) c, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static JButton button(Container root, String prefix)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JButton && ((JButton) c).getText().startsWith(prefix))
			{
				return (JButton) c;
			}
			if (c instanceof Container)
			{
				JButton found = button((Container) c, prefix);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
