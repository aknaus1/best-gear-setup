package com.bestgearsetup.ui;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.RaidPlan;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.Test;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

public class ItemInteractionTest
{
	private final GearItem pipe = gear(12926, "toxic blowpipe", Slot.WEAPON);
	private final GearItem darts = gear(21326, "amethyst dart", Slot.AMMO);
	private final GearItem body = gear(29013, "blue moon chestplate", Slot.BODY);
	private final GameData data = new GameData(Collections.emptyList(),
		Collections.singletonMap(Slot.BODY.getApiKey(), Collections.singletonList(body)), Collections.singletonList(pipe),
		Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());

	@Test public void wornBlowpipeHoverAndMenuShowDartsInsideRatherThanInAmmoSlot() throws Exception
	{
		BestGearSetupPlugin plugin = plugin(); ItemManager images = images();
		Loadout loadout = new Loadout(); loadout.set(Slot.WEAPON, pipe); loadout.setLoadedAmmo(darts);
		loadout.setStyle(AttackStyle.parse("rapid,ranged,rapid"));
		SwingUtilities.invokeAndWait(() ->
		{
			EquipmentGrid grid = new EquipmentGrid(new SetupResult(CombatClass.RANGED, loadout, null, 0),
				Collections.emptyMap(), plugin, images, null, 1000);
			JComponent weapon = (JComponent) grid.getComponent(6);
			JComponent ammo = (JComponent) grid.getComponent(5);
			assertTrue(weapon.getToolTipText(hover(weapon)).contains("Loaded darts: Amethyst Dart"));
			assertTrue(ammo.getToolTipText().contains("empty"));
			option(weapon.getComponentPopupMenu(), "Exclude Amethyst Dart").doClick();
			verify(plugin).setExcluded(21326, true);
		});
	}

	@Test public void inventoryGearUsesEquipmentTooltipAndActionsIncludingLoadedDarts() throws Exception
	{
		BestGearSetupPlugin plugin = plugin(); ItemManager images = images();
		SwingUtilities.invokeAndWait(() ->
		{
			InventoryView inventory = new InventoryView(images, new JPanel(), "testInventory", plugin, null, 1000);
			RaidPlan.InventoryItem loaded = new RaidPlan.InventoryItem("amethyst dart", Collections.singletonList(21326), false);
			inventory.showItems(Arrays.asList(new RaidPlan.InventoryItem("blue moon chestplate", Collections.singletonList(29013), true),
				new RaidPlan.InventoryItem("toxic blowpipe", Collections.singletonList(12926), true, Collections.singletonList(loaded))));
			JPanel grid = findGrid(inventory);
			JComponent armour = (JComponent) grid.getComponent(0);
			assertTrue(armour.getToolTipText(hover(armour)).contains("Blue Moon Chestplate"));
			assertTrue(armour.getToolTipText(hover(armour)).contains("Owned"));
			assertNotNull(option(armour.getComponentPopupMenu(), "Lock body to Blue Moon Chestplate"));
			option(armour.getComponentPopupMenu(), "Exclude Blue Moon Chestplate").doClick();
			verify(plugin).setExcluded(29013, true);
			JComponent weapon = (JComponent) grid.getComponent(1);
			assertTrue(weapon.getToolTipText(hover(weapon)).contains("Loaded darts: Amethyst Dart"));
			option(weapon.getComponentPopupMenu(), "Exclude Amethyst Dart").doClick();
			verify(plugin).setExcluded(21326, true);
		});
	}

	@Test public void inventoryPouchRetainsContentsAndHasSupplyActions() throws Exception
	{
		BestGearSetupPlugin plugin = plugin();
		SwingUtilities.invokeAndWait(() ->
		{
			InventoryView inventory = new InventoryView(images(), new JPanel(), "testInventory", plugin, null, 0);
			RaidPlan.InventoryItem rune = new RaidPlan.InventoryItem("Blood runes", Collections.singletonList(565), false);
			inventory.showItems(Collections.singletonList(new RaidPlan.InventoryItem("Rune pouch", Collections.singletonList(12791),
				false, Collections.singletonList(rune))));
			JComponent pouch = (JComponent) findGrid(inventory).getComponent(0);
			assertTrue(pouch.getToolTipText().contains("Blood runes"));
			assertTrue(pouch.getToolTipText().contains("Right-click"));
			option(pouch.getComponentPopupMenu(), "Exclude Blood Runes").doClick();
			verify(plugin).setExcluded(565, true);
			option(pouch.getComponentPopupMenu(), "Mark as owned").doClick();
			verify(plugin).setManualOwned(12791, true);
			assertTrue(((JComponent) findGrid(inventory).getComponent(1)).getComponentPopupMenu() == null);
		});
	}

	private BestGearSetupPlugin plugin()
	{
		BestGearSetupPlugin plugin = mock(BestGearSetupPlugin.class);
		when(plugin.getGameData()).thenReturn(data); when(plugin.getOwnedItems()).thenReturn(mock(OwnedItems.class));
		when(plugin.getLocks()).thenReturn(Collections.emptyMap()); when(plugin.getExcluded()).thenReturn(Collections.emptySet());
		when(plugin.owns(body)).thenReturn(true);
		return plugin;
	}

	private static ItemManager images()
	{
		ItemManager images = mock(ItemManager.class); when(images.getImage(anyInt())).thenReturn(mock(AsyncBufferedImage.class)); return images;
	}
	private static GearItem gear(int id, String name, Slot slot)
	{
		GearItem item = new GearItem(); item.setId(id); item.setName(name); item.setSlot(slot); return item;
	}
	private static MouseEvent hover(JComponent cell) { return new MouseEvent(cell, MouseEvent.MOUSE_MOVED, 0, 0, 1, 1, 0, false); }
	private static JMenuItem option(JPopupMenu menu, String name)
	{
		for (Component item : menu.getComponents())
		{ if (item instanceof JMenuItem && name.equals(((JMenuItem) item).getText())) { return (JMenuItem) item; } }
		throw new AssertionError("Missing menu action: " + name);
	}
	private static JPanel findGrid(Container root)
	{
		for (Component item : root.getComponents())
		{
			if (item instanceof JPanel && "testInventory".equals(item.getName())) { return (JPanel) item; }
			if (item instanceof Container) { JPanel found = findGrid((Container) item); if (found != null) { return found; } }
		}
		return null;
	}
}
