package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import com.bestgearsetup.Options;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.calc.Outfit;
import com.bestgearsetup.calc.Setup;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.Combat;
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
import org.junit.Test;

public class SidebarLayoutTest
{
	@org.junit.Before public void trackTips() { HoverTextLengthTest.trackTips(); }
	@org.junit.After public void tipsAreShort() throws Exception { HoverTextLengthTest.assertTrackedTipsShort(); }

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
					public net.runelite.client.config.ConfigDescriptor getConfigDescriptor()
					{
						return com.bestgearsetup.ConfigItemsForTests.descriptor();
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

				};
				Sidebar panel = new Sidebar(plugin, new Options() {}, null, null, null);
				panel.modeBox.setSelectedItem(com.bestgearsetup.calc.SearchMode.OWNED_ONLY);
				assertFalse(panel.budgetRow.isVisible());
				panel.modeBox.setSelectedItem(com.bestgearsetup.calc.SearchMode.BUDGET);
				assertTrue(panel.budgetRow.isVisible());
				JButton options = button(panel, "Assumptions");
				options.doClick();
				assertTrue(named(panel, "fightEditor").isVisible());
				assertFalse(((JScrollPane) named(panel, "resultsScroll")).isVisible());
				button(panel, "Back to results").doClick();
				assertTrue(((JScrollPane) named(panel, "resultsScroll")).isVisible());
				panel.setSize(width, 420);
				button(panel, "Settings").doClick();
				assertTrue("Settings opens in the sidebar", named(panel, "settingsEditor").isVisible());
				assertEquals(0, configurationOpened[0]);
				button(panel, "Open in RuneLite settings").doClick();
				assertEquals("RuneLite's configuration page stays one click away", 1, configurationOpened[0]);
				button(panel, "Back to results").doClick();
				for (String[] editor : new String[][]{{"Assumptions", "fight"}, {"Rules", "gear"}, {"Settings", "settings"}})
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
					if (editor[1].equals("fight"))
					{
						((JButton) named(panel, "fightExpandAll")).doClick();
						for (int pass = 0; pass < 3; pass++) { layout(panel); }
						for (String key : new String[]{"killsPerTrip", "specEnergy", "fillFood"})
						{
							assertFits(editorScroll, named(panel, key));
						}
						((JButton) named(panel, "fightCollapseAll")).doClick();
					}
					back.doClick();
				}
				EquipmentGrid grid = new EquipmentGrid(new Setup(Combat.MELEE, new Outfit(), null, 0, 0, java.util.Collections.emptySet(), java.util.Collections.emptySet(), null, null),
					java.util.Collections.emptyMap(), plugin, null, null, 0);
				JPanel gridRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
				gridRow.add(grid);
				panel.resultsPanel.add(Sidebar.left(gridRow));
				panel.setSize(width, 420);
				layout(panel);
				layout(panel);
				JScrollPane scroll = (JScrollPane) named(panel, "resultsScroll");
				assertTrue("Results should scroll vertically when needed", scroll.getVerticalScrollBar().isVisible());
				assertEquals("Wide option values must not enlarge the scroll view", scroll.getViewport().getWidth(),
					scroll.getViewport().getView().getWidth());
				assertFits(scroll, options);
				assertFits(scroll, button(panel, "Search again"));
				for (Component slot : grid.getComponents())
				{
					assertFits(scroll, slot);
				}
				panel.showResults(com.bestgearsetup.TestResults.of(new Monster(), 100, Collections.emptyMap(),
					Collections.singletonList("Long calculation and provenance explanation"),
					Collections.emptyMap(), Collections.emptyMap(), false));
				JButton details = button(panel, "Search details");
				assertTrue("Search notes should be collapsed", details != null);
				JLabel notes = (JLabel) named(panel, "searchDetails");
				assertFalse(notes.isVisible());
				assertFalse(panel.statusLabel.getText().contains("provenance explanation"));
				details.doClick();
				assertTrue(notes.isVisible());
				assertTrue(notes.getText().contains("provenance explanation"));
				panel.clearResults("Account changed");
				assertTrue(named(panel, "searchDetails") == null);
				// Each editor's change dot is painted inside its button, so marking all three leaves the labels room.
				for (String name : new String[]{"fightEditorButton", "gearEditorButton", "settingsButton"})
				{
					((Sidebar.EditorButton) named(panel, name)).mark(99, "changed");
				}
				layout(panel);
				layout(panel);
				for (String label : new String[]{"Assumptions", "Rules", "Settings"})
				{
					JButton action = button(panel, label);
					assertTrue(label + " must fit its button at " + width + "px",
						action.getWidth() >= action.getPreferredSize().width);
				}
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
