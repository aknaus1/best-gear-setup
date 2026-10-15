package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.RaidPlan;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class InventoryBoostTest
{
	@Test public void suppliesAloneHideTheWholeInventorySectionAndSwitchesRestoreIt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			InventoryView view = new InventoryView(null, new JPanel(), "inventory", null, null, 0);
			RaidPlan.InventoryItem runes = new RaidPlan.InventoryItem("Runes", Collections.singletonList(565), false);
			view.showItems(Collections.singletonList(runes));
			assertTrue(!view.isVisible());
			view.showItems(Arrays.asList(runes, new RaidPlan.InventoryItem("Weapon", Collections.singletonList(1), true)));
			assertTrue(view.isVisible());
			view.showItems(Collections.emptyList());
			assertTrue(!view.isVisible());
		});
	}

	@Test public void smallCombatSwitchesStayConsecutiveAndFreeSupplySlotsStayAtTheEnd() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			RaidPlan.InventoryItem melee = new RaidPlan.InventoryItem("Sword", Collections.singletonList(1), true,
				Collections.emptyList(), RaidPlan.InventoryGroup.MELEE_GEAR);
			RaidPlan.InventoryItem ranged = new RaidPlan.InventoryItem("Bow", Collections.singletonList(2), true,
				Collections.emptyList(), RaidPlan.InventoryGroup.RANGED_GEAR);
			RaidPlan.InventoryItem magic = new RaidPlan.InventoryItem("Staff", Collections.singletonList(3), true,
				Collections.emptyList(), RaidPlan.InventoryGroup.MAGIC_GEAR);
			RaidPlan.InventoryItem potion = new RaidPlan.InventoryItem("Potion", Collections.singletonList(4), false,
				Collections.emptyList(), RaidPlan.InventoryGroup.BOOSTS);
			InventoryView view = new InventoryView(null, new JPanel(), "inventory", null, null, 0);
			view.showItems(Arrays.asList(melee, ranged, magic, potion));
			JPanel grid = findGrid(view);
			assertEquals(28, grid.getComponentCount());
			assertEquals("Sword", ((JComponent) grid.getComponent(0)).getToolTipText());
			assertEquals("Bow", ((JComponent) grid.getComponent(1)).getToolTipText());
			assertEquals("Staff", ((JComponent) grid.getComponent(2)).getToolTipText());
			assertEquals("Potion", ((JComponent) grid.getComponent(3)).getToolTipText());
			assertEquals("Free slot for supplies", ((JComponent) grid.getComponent(4)).getToolTipText());
			assertTrue(Arrays.stream(view.getComponents()).filter(c -> c instanceof JPanel)
				.flatMap(c -> Arrays.stream(((JPanel) c).getComponents())).anyMatch(c -> c instanceof javax.swing.JButton
					&& "Inventory (4 / 28)".equals(((javax.swing.JButton) c).getText())));
		});
	}

	@Test public void estimatedBottlesRenderAsSeparateCellsWithDoseGuidance() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			RaidPlan.InventoryItem bottle = new RaidPlan.InventoryItem("Super combat potion", Collections.singletonList(12695),
				false, Collections.emptyList(), RaidPlan.InventoryGroup.BOOSTS, 5, 2, false);
			InventoryView view = new InventoryView(null, new JPanel(), "inventory", null, null, 0);
			view.showItems(Arrays.asList(bottle, bottle), item -> RaidPlan.PotionUse.BROUGHT);
			JPanel grid = findGrid(view);
			assertEquals(28, grid.getComponentCount());
			assertTrue(((JComponent) grid.getComponent(0)).getToolTipText().contains("Estimated 5 doses; bring 2"));
			assertEquals(((JComponent) grid.getComponent(0)).getToolTipText(), ((JComponent) grid.getComponent(1)).getToolTipText());
			assertEquals("Free slot for supplies", ((JComponent) grid.getComponent(2)).getToolTipText());
		});
	}

	private static JPanel findGrid(JPanel panel)
	{
		for (java.awt.Component component : panel.getComponents())
		{
			if (component instanceof JPanel)
			{
				JPanel child = (JPanel) component;
				if ("inventory".equals(child.getName())) { return child; }
				JPanel found = findGrid(child); if (found != null) { return found; }
			}
		}
		return null;
	}
}
