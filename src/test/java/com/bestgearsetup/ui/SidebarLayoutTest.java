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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
				BestGearSetupPlugin plugin = new BestGearSetupPlugin()
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
				};
				BestGearSetupPanel panel = new BestGearSetupPanel(plugin, new BestGearSetupConfig() {}, null, null, null);
				JButton options = button(panel, "+ Fight options");
				options.doClick();
				EquipmentGrid grid = new EquipmentGrid(new SetupResult(CombatClass.MELEE, new Loadout(), null, 0),
					java.util.Collections.emptyMap(), plugin, null, null, 0);
				JPanel gridRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
				gridRow.add(grid);
				try
				{
					Field field = BestGearSetupPanel.class.getDeclaredField("resultsPanel");
					field.setAccessible(true);
					((JPanel) field.get(panel)).add(BestGearSetupPanel.left(gridRow));
				}
				catch (ReflectiveOperationException e)
				{
					throw new AssertionError(e);
				}
				panel.setSize(width, 420);
				layout(panel);
				layout(panel);
				JScrollPane scroll = (JScrollPane) panel.getComponent(0);
				assertTrue("Expanded options should scroll vertically", scroll.getVerticalScrollBar().isVisible());
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
				try
				{
					Field field = BestGearSetupPanel.class.getDeclaredField("statusLabel");
					field.setAccessible(true);
					assertFalse(((JLabel) field.get(panel)).getText().contains("provenance explanation"));
				}
				catch (ReflectiveOperationException e)
				{
					throw new AssertionError(e);
				}
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
