package com.bestgearsetup.ui;

import com.bestgearsetup.RaidPlan;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.GameData;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Inventory below worn equipment, with pouch tooltips. It starts minimized; the state is kept on a component that
 * outlives the results (the side panel's boss selector), so it carries over to new searches and raid encounters.
 */
class InventoryView extends JPanel
{
	private static final String EXPANDED = "inventoryExpanded";
	private final JPanel grid = new JPanel(new GridLayout(7, 4, 3, 3));
	private final JButton toggle = new JButton("Inventory");
	private final ItemManager items;
	private final JComponent stateOwner;
	private final BestGearSetupPlugin plugin;
	private final SpriteCache sprites;
	private final int ammoCount;

	InventoryView(ItemManager items, JComponent stateOwner, String name, BestGearSetupPlugin plugin, SpriteCache sprites, int ammoCount)
	{
		this.items = items;
		this.stateOwner = stateOwner;
		this.plugin = plugin;
		this.sprites = sprites;
		this.ammoCount = ammoCount;
		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		grid.setOpaque(false);
		grid.setName(name);
		toggle.setName(name + "Toggle");
		Controls.ghost(toggle);
		toggle.addActionListener(e ->
		{
			stateOwner.putClientProperty(EXPANDED, !expanded());
			applyExpanded();
		});
		add(BestGearSetupPanel.left(toggle));
		add(BestGearSetupPanel.left(grid));
		applyExpanded();
	}

	void showItems(List<RaidPlan.InventoryItem> carried)
	{
		grid.removeAll();
		for (int i = 0; i < 28; i++)
		{
			RaidPlan.InventoryItem item = i < carried.size() ? carried.get(i) : null;
			GameData data = plugin == null ? null : plugin.getGameData();
			GearItem gear = item == null || data == null ? null : data.findItem(item.getIds().get(0));
			JComponent cell = gear == null ? new Cell(item, items, plugin)
				: EquipmentGrid.inventoryCell(gear, item.getContents(), plugin, items, sprites, ammoCount);
			cell.setPreferredSize(new Dimension(40, 36));
			grid.add(cell);
		}
		toggle.setText("Inventory (" + carried.size() + " / 28)");
		applyExpanded();
	}

	/** Minimized until the player expands it; the state owner keeps that choice for later results. */
	private boolean expanded() { return Boolean.TRUE.equals(stateOwner.getClientProperty(EXPANDED)); }

	private void applyExpanded()
	{
		boolean open = expanded();
		grid.setVisible(open);
		toggle.setIcon(new Controls.Chevron(open ? Controls.Chevron.DOWN : Controls.Chevron.RIGHT, null));
		toggle.setToolTipText(open ? "Minimize inventory" : "Expand inventory");
		toggle.getAccessibleContext().setAccessibleDescription(open ? "Expanded" : "Collapsed");
		revalidate();
		repaint();
	}

	static String tooltip(RaidPlan.InventoryItem item)
	{
		if (item == null) { return "Free slot for supplies"; }
		if (item.getContents().isEmpty()) { return item.getName(); }
		StringBuilder tip = new StringBuilder("<html><b>").append(BestGearSetupPanel.escape(item.getName())).append("</b>");
		for (RaidPlan.InventoryItem rune : item.getContents()) { tip.append("<br>").append(BestGearSetupPanel.escape(rune.getName())); }
		return tip.append("</html>").toString();
	}

	private static class Cell extends JComponent
	{
		private final AsyncBufferedImage icon;
		Cell(RaidPlan.InventoryItem item, ItemManager items, BestGearSetupPlugin plugin)
		{
			setPreferredSize(new Dimension(40, 36));
			icon = item == null || items == null ? null : items.getImage(item.getIds().get(0));
			if (icon != null) { icon.onLoaded(this::repaint); }
			String tip = tooltip(item);
			if (item != null && plugin != null)
			{
				if (!tip.startsWith("<html>")) { tip = "<html><b>" + BestGearSetupPanel.escape(GameData.titleCase(item.getName())) + "</b></html>"; }
				boolean owned = item.getIds().stream().anyMatch(plugin.getOwnedItems()::owns);
				tip = tip.replace("</html>", "<br>" + (owned ? "Owned" : "Not owned") + "<br><i>Right-click for options</i></html>");
			}
			setToolTipText(tip);
			if (item != null && plugin != null) { setComponentPopupMenu(ItemMenus.supply(item, plugin)); }
		}
		@Override protected void paintComponent(Graphics g)
		{
			g.setColor(new Color(0x3E3529));
			g.fillRect(0, 0, getWidth(), getHeight());
			if (icon != null) { g.drawImage(icon, (getWidth() - icon.getWidth()) / 2, (getHeight() - icon.getHeight()) / 2, null); }
		}
	}
}
