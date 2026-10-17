package com.bestgearsetup.ui;

import com.bestgearsetup.RaidPlan;
import com.bestgearsetup.CoxChestPlan;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.GameData;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Function;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Inventory below worn equipment, laid out like the game's inventory tab: 28 slots of 36x32 on the side-panel stone
 * background, 4 across and 7 down, with pouch tooltips. Every result shows it, with or without equipment switches. It starts minimized; the state is kept on a component that
 * outlives the results (the side panel's boss selector), so it carries over to new searches and raid encounters.
 */
class InventoryView extends JPanel
{
	private static final String EXPANDED = "inventoryExpanded";
	/** The game's inventory slot size. */
	private static final int SLOT_WIDTH = 36;
	private static final int SLOT_HEIGHT = 32;
	private final Backpack grid;
	private final JPanel gridRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
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
		grid = new Backpack(sprites);
		grid.setName(name);
		gridRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		gridRow.add(grid);
		toggle.setName(name + "Toggle");
		Controls.ghost(toggle);
		toggle.addActionListener(e ->
		{
			stateOwner.putClientProperty(EXPANDED, !expanded());
			applyExpanded();
		});
		add(BestGearSetupPanel.left(toggle));
		add(BestGearSetupPanel.left(gridRow));
		applyExpanded();
	}

	void showItems(List<RaidPlan.InventoryItem> carried)
	{
		showItems(carried, item -> null);
	}

	/** @param potionUse how the raid kit gets each carried boost: brought, and maybe prepot too */
	void showItems(List<RaidPlan.InventoryItem> carried, Function<RaidPlan.InventoryItem, RaidPlan.PotionUse> potionUse)
	{
		showItems(carried, potionUse, null);
	}

	/** Chest guidance stays in hover text so inventory cells retain their original appearance. */
	void showItems(List<RaidPlan.InventoryItem> carried, Function<RaidPlan.InventoryItem, RaidPlan.PotionUse> potionUse,
		CoxChestPlan chest)
	{
		grid.removeAll();
		List<RaidPlan.InventoryItem> layout = RaidPlan.inventoryLayout(carried);
		for (int i = 0; i < 28; i++)
		{
			RaidPlan.InventoryItem item = i < layout.size() ? layout.get(i) : null;
			GameData data = plugin == null ? null : plugin.getGameData();
			GearItem gear = item == null || data == null ? null : data.findItem(item.getIds().get(0));
			JComponent cell = gear == null ? new Cell(item, item == null ? null : potionUse.apply(item), items, plugin)
				: EquipmentGrid.inventoryCell(gear, item.getContents(), plugin, items, sprites, ammoCount, false);
			cell.setPreferredSize(new Dimension(SLOT_WIDTH, SLOT_HEIGHT));
			if (chest != null && item != null && item.isEquipment())
			{
				chest.getDeposits().stream().filter(deposit -> item.getIds().contains(deposit.getItem().getId()))
					.findFirst().ifPresent(deposit ->
					{
						String tip = cell.getToolTipText();
						if (tip == null) { tip = ""; }
						if (!tip.startsWith("<html>")) { tip = "<html>" + BestGearSetupPanel.escape(tip) + "</html>"; }
						cell.setToolTipText(tip.replace("</html>", "<br>Private chest: "
							+ BestGearSetupPanel.escape(deposit.getReason()) + "</html>"));
					});
			}
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
		toggle.getAccessibleContext().setAccessibleDescription(open ? "Expanded" : "Collapsed");
		revalidate();
		repaint();
	}

	static String tooltip(RaidPlan.InventoryItem item)
	{
		if (item == null) { return "Free slot for supplies"; }
		if (item.getBottles() > 0)
		{
			return "<html><b>" + BestGearSetupPanel.escape(item.getName()) + "</b><br>"
				+ (item.isReusable() ? "Bring 1 reusable item" : "~" + item.getEstimatedDoses()
					+ " doses · " + item.getBottles() + " bottles")
				+ "</html>";
		}
		if (item.getContents().isEmpty()) { return item.getName(); }
		StringBuilder tip = new StringBuilder("<html><b>").append(BestGearSetupPanel.escape(item.getName())).append("</b>");
		for (RaidPlan.InventoryItem rune : item.getContents()) { tip.append("<br>").append(BestGearSetupPanel.escape(rune.getName())); }
		return tip.append("</html>").toString();
	}

	/** The inventory tab's stone backing, sized like the game's fixed-mode side panel (190x261). */
	private static final class Backpack extends JPanel
	{
		private static final Color STONE = new Color(0x3E, 0x35, 0x29);
		private static final Color STONE_EDGE = new Color(0x2B, 0x24, 0x1B);
		private final SpriteCache sprites;

		Backpack(SpriteCache sprites)
		{
			super(new GridLayout(7, 4, 6, 4));
			this.sprites = sprites;
			setOpaque(true);
			setBorder(new EmptyBorder(6, 14, 7, 14));
		}

		@Override protected void paintComponent(Graphics g)
		{
			BufferedImage stone = sprites == null ? null : sprites.get(SpriteID.SIDE_BACKGROUND, this::repaint);
			if (stone != null)
			{
				g.drawImage(stone, 0, 0, getWidth(), getHeight(), null);
				return;
			}
			g.setColor(STONE);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(STONE_EDGE);
			g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
		}
	}

	/** A supply in a slot; drawn straight on the stone like the game, with no tile of its own. */
	private static class Cell extends JComponent
	{
		private final AsyncBufferedImage icon;
		/** Whether a boost is also prepot, tagged in the corner; null for other supplies. */
		private final String tag;
		Cell(RaidPlan.InventoryItem item, RaidPlan.PotionUse use, ItemManager items, BestGearSetupPlugin plugin)
		{
			setPreferredSize(new Dimension(SLOT_WIDTH, SLOT_HEIGHT));
			tag = use == null ? null : use.getTag();
			if (use != null) { setName("potion:" + use.name()); }
			icon = item == null || items == null ? null : items.getImage(item.getIds().get(0));
			if (icon != null) { icon.onLoaded(this::repaint); }
			String tip = tooltip(item);
			if (item != null && plugin != null)
			{
				if (!tip.startsWith("<html>")) { tip = "<html><b>" + BestGearSetupPanel.escape(GameData.titleCase(item.getName())) + "</b></html>"; }
				boolean owned = item.getIds().stream().anyMatch(plugin.getOwnedItems()::owns);
				String drunk = use == null ? "" : "<br>" + BestGearSetupPanel.escape(use.getDescription());
				tip = tip.replace("</html>", drunk + "<br>" + (owned ? "Owned" : "Not owned") + "<br><i>Right-click for options</i></html>");
			}
			setToolTipText(tip);
			if (item != null && plugin != null) { setComponentPopupMenu(ItemMenus.supply(item, plugin)); }
		}
		@Override protected void paintComponent(Graphics g)
		{
			if (icon != null) { g.drawImage(icon, (getWidth() - icon.getWidth()) / 2, (getHeight() - icon.getHeight()) / 2, null); }
			ResultView.paintTag(g, tag);
		}
	}
}
