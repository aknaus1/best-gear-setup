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
import com.bestgearsetup.data.Gear;
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
	@org.junit.Before public void trackTips() { HoverTextLengthTest.trackTips(); }
	@org.junit.After public void tipsAreShort() throws Exception { HoverTextLengthTest.assertTrackedTipsShort(); }

	private static final GameData DATA = load();

	private final Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
	private final Set<Integer> excluded = new LinkedHashSet<>();
	private final List<String> cleared = new ArrayList<>();
	private final Set<String> excludedPrayers = new java.util.TreeSet<>();
	private final Map<String, Object> saved = new java.util.HashMap<>();
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
		public void setConfig(String key, Object value)
		{
			saved.put(key, value);
		}

		@Override
		public Set<String> getExcludedPrayers()
		{
			return new java.util.TreeSet<>(excludedPrayers);
		}

		@Override
		public void setPrayerExcluded(String name, boolean exclude)
		{
			if (exclude)
				excludedPrayers.add(name);
			else
				excludedPrayers.remove(name);
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
		public boolean owns(Gear item)
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
	public void everyPrayerInTheBookTogglesItsExclusion() throws Exception
	{
		excludedPrayers.add("old typo");
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin, null, null);
			panel.refresh();
			for (String prayer : new String[]{"thick skin", "protect item", "protect from melee", "smite", "piety", "zeal"})
				assertNotNull(prayer, named(panel, "prayer:" + prayer));
			JLabel piety = (JLabel) named(panel, "prayer:piety");
			assertTrue(piety.getToolTipText(), piety.getToolTipText().contains("click to exclude"));
			click(piety);
			assertTrue(excludedPrayers.contains("piety"));
			// The square changes on the press, before the panel is rebuilt from the saved setting.
			assertTrue(piety.getToolTipText().contains("excluded: click to allow"));
			panel.refresh();
			JLabel excluded = (JLabel) named(panel, "prayer:piety");
			assertTrue(excluded.getToolTipText().contains("excluded: click to allow"));
			click(excluded);
			assertFalse(excludedPrayers.contains("piety"));
			((JButton) named(panel, "excludeAllPrayers")).doClick();
			assertTrue(((String) saved.get("excludedPrayers")).startsWith("thick skin,burst of strength,"));
			assertTrue(((String) saved.get("excludedPrayers")).endsWith(",augury,zeal"));
			((JButton) named(panel, "allowAllPrayers")).doClick();
			assertEquals("", saved.get("excludedPrayers"));
			// A name the prayer book lacks is still listed, with a way to remove it.
			assertTrue(hasAccessibleName(panel, "Remove Old Typo"));
		});
	}

	private static boolean hasAccessibleName(Container root, String name)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof javax.swing.JComponent && name.equals(((javax.swing.JComponent) c).getAccessibleContext().getAccessibleName())
				|| c instanceof Container && hasAccessibleName((Container) c, name))
				return true;
		}
		return false;
	}

	private static void click(Component c)
	{
		for (java.awt.event.MouseListener l : c.getMouseListeners())
			l.mousePressed(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_PRESSED, 0,
				java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1));
	}

	@Test
	public void gearCardsStartCollapsedAndOpenOnTheirOwnOrAllAtOnce() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin, null, null);
			String[] cards = {"locks", "exclusions", "excludedPrayers", "forcedSpells", "blockedSpells", "extraOwnedItems"};
			for (String card : cards)
			{
				assertFalse(card + " starts collapsed", named(panel, card + "Content").isVisible());
			}
			((JButton) named(panel, "locksToggle")).doClick();
			((JButton) named(panel, "exclusionsToggle")).doClick();
			assertTrue("Opening a card leaves the others open", named(panel, "locksContent").isVisible());
			assertTrue(named(panel, "exclusionsContent").isVisible());
			((JButton) named(panel, "locksToggle")).doClick();
			assertFalse(named(panel, "locksContent").isVisible());
			assertTrue("Collapsing a card leaves the others open", named(panel, "exclusionsContent").isVisible());
			((JButton) named(panel, "gearExpandAll")).doClick();
			for (String card : cards)
			{
				assertTrue(card + " expanded", named(panel, card + "Content").isVisible());
			}
			((JButton) named(panel, "gearCollapseAll")).doClick();
			for (String card : cards)
			{
				assertFalse(card + " collapsed", named(panel, card + "Content").isVisible());
			}
		});
	}

	@Test
	public void locksAndExclusionsAreListedAndClearedSeparately() throws Exception
	{
		Gear lightbearer = DATA.searchItems("lightbearer", 1, Slot.RING).get(0);
		Gear torva = DATA.searchItems("torva full helm", 1, Slot.HEAD).get(0);
		locks.put(Slot.RING, SlotLock.item(lightbearer.getId()));
		locks.put(Slot.SHIELD, SlotLock.empty());
		excluded.add(torva.getId());
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin, null, null);
			panel.refresh();
			Container lockCard = (Container) named(panel, "locksContent");
			Container excludedCard = (Container) named(panel, "exclusionsContent");
			String helm = GameData.titleCase(torva.getName());
			assertNotNull(label(lockCard, GameData.titleCase(lightbearer.getName())));
			assertNotNull(label(lockCard, "Keep empty"));
			assertNull("Exclusions are not listed with locks", label(lockCard, helm));
			assertNotNull(label(excludedCard, helm));
			assertEquals("Locked items (2)", ((JButton) named(panel, "locksToggle")).getText());

			button(lockCard, "Clear locks").doClick();
			assertEquals("Clearing locks keeps exclusions", List.of("locks"), cleared);
			assertNull("One exclusion needs no Clear all", button(excludedCard, "Clear exclusions"));
		});
	}

	@Test
	public void anExcludedAntifireIsListedByName() throws Exception
	{
		excluded.add(com.bestgearsetup.calc.Antifire.SUPER.getItemId());
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin, null, null);
			panel.refresh();
			assertNotNull(label((Container) named(panel, "exclusionsContent"), "Super antifire"));
		});
	}

	@Test
	public void anyItemCanBeExcludedFromTheGearView() throws Exception
	{
		Gear lightbearer = DATA.searchItems("lightbearer", 1, Slot.RING).get(0);
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin, null, null);
			panel.refresh();
			IconTextField search = (IconTextField) named(panel, "excludeSearch");
			Container suggestions = (Container) named(panel, "excludeSuggestions");
			search.setText("lightbear");
			JButton suggestion = button(suggestions, "Lightbearer");
			assertEquals("Lightbearer (Ring)", suggestion.getText());
			suggestion.doClick();
			assertTrue(excluded.contains(lightbearer.getId()));
			assertEquals("The text stays for the next pick", "lightbear", search.getText());

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
