package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.PotionChoice;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Monster;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Test;

public class SidebarLayoutTest
{
	@Test
	public void expandedOptionsAndEquipmentStayInsideNarrowSidebar() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			for (int width : new int[]{225, 242})
			{
				int[] configurationOpened = {0};
				BestGearSetupPlugin plugin = new BestGearSetupPlugin()
				{
					@Override
					public void setConfig(String key, Object value)
					{
					}
					@Override
					public void openConfiguration()
					{
						configurationOpened[0]++;
					}
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
				};
				BestGearSetupPanel panel = new BestGearSetupPanel(plugin, new BestGearSetupConfig() {}, null, null, null);
				panel.modeBox.setSelectedItem(com.bestgearsetup.calc.SearchMode.OWNED_ONLY);
				assertFalse(panel.budgetRow.isVisible());
				panel.modeBox.setSelectedItem(com.bestgearsetup.calc.SearchMode.BUDGET);
				assertTrue(panel.budgetRow.isVisible());
				JButton options = button(panel, "Fight");
				options.doClick();
				assertTrue(named(panel, "fightEditor").isVisible());
				assertFalse(((JScrollPane) panel.getComponent(0)).isVisible());
				button(panel, "Back to results").doClick();
				assertTrue(((JScrollPane) panel.getComponent(0)).isVisible());
				panel.setSize(width, 420);
				button(panel, "Settings").doClick();
				assertEquals("Settings opens RuneLite's configuration page", 1, configurationOpened[0]);
				assertTrue("Settings must not replace the results view", ((JScrollPane) panel.getComponent(0)).isVisible());
				for (String[] editor : new String[][]{{"Fight", "fight"}, {"Gear", "gear"}})
				{
					button(panel, editor[0]).doClick();
					for (int pass = 0; pass < 3; pass++) { layout(panel); }
					JPanel card = (JPanel) named(panel, editor[1] + "Editor");
					JButton back = (JButton) named(panel, editor[1] + "Back");
					assertTrue("Back must remain visible in a short editor", back.getHeight() > 0);
					Rectangle backBounds = SwingUtilities.convertRectangle(back.getParent(), back.getBounds(), card);
					JScrollPane editorScroll = (JScrollPane) card.getComponent(1);
					assertTrue("Scrolling content must stay below Back", editorScroll.getY() >= backBounds.y + backBounds.height);
					assertEquals(editorScroll.getViewport().getWidth(), editorScroll.getViewport().getView().getWidth());
					back.doClick();
				}
				EquipmentGrid grid = new EquipmentGrid(new SetupResult(CombatClass.MELEE, new Loadout(), null, 0),
					java.util.Collections.emptyMap(), plugin, null, null, 0);
				JPanel gridRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
				gridRow.add(grid);
				panel.resultsPanel.add(BestGearSetupPanel.left(gridRow));
				panel.setSize(width, 420);
				layout(panel);
				layout(panel);
				JScrollPane scroll = (JScrollPane) panel.getComponent(0);
				assertTrue("Results should scroll vertically when needed", scroll.getVerticalScrollBar().isVisible());
				assertEquals("Wide option values must not enlarge the scroll view", scroll.getViewport().getWidth(),
					scroll.getViewport().getView().getWidth());
				assertFits(scroll, options);
				assertFits(scroll, button(panel, "Find best setup"));
				for (Component slot : grid.getComponents())
				{
					assertFits(scroll, slot);
				}
				panel.showResults(new SearchResults(new Monster(), 100, Collections.emptyMap(),
					Collections.singletonList("Long calculation and provenance explanation"),
					Collections.emptyMap(), Collections.emptyMap(), false));
				JButton details = button(panel, "+ Search details");
				assertTrue("Search notes should be collapsed", details != null);
				JLabel notes = (JLabel) named(panel, "searchDetails");
				assertFalse(notes.isVisible());
				assertFalse(panel.statusLabel.getText().contains("provenance explanation"));
				details.doClick();
				assertTrue(notes.isVisible());
				assertTrue(notes.getText().contains("provenance explanation"));
				panel.clearResults("Account changed");
				assertTrue(named(panel, "searchDetails") == null);
			}
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

	private static void assertFits(JScrollPane scroll, Component component)
	{
		Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(),
			scroll.getViewport().getView());
		assertTrue("Component extends past the sidebar: " + bounds,
			bounds.x >= 0 && bounds.x + bounds.width <= scroll.getViewport().getWidth());
	}

	private static void layout(Container container)
	{
		container.doLayout();
		for (Component child : container.getComponents())
		{
			if (child instanceof Container && child.isVisible())
			{
				layout((Container) child);
			}
		}
	}

	private static JButton button(Container container, String text)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JButton && text.equals(((JButton) child).getText()))
			{
				return (JButton) child;
			}
			if (child instanceof Container)
			{
				JButton found = button((Container) child, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
