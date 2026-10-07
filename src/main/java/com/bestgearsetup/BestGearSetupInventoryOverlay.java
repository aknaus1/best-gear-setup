package com.bestgearsetup;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Outlines the selected setup's equipment in the inventory, including while banking. */
class BestGearSetupInventoryOverlay extends WidgetItemOverlay
{
	private final BestGearSetupPlugin plugin;
	private final BestGearSetupConfig config;
	private final ItemManager itemManager;

	@Inject
	BestGearSetupInventoryOverlay(BestGearSetupPlugin plugin, BestGearSetupConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		Set<Integer> ids = plugin.getBankHighlightIds();
		if (!config.highlightInventoryGear() || itemId <= 0 || widgetItem.getQuantity() <= 0 || ids.isEmpty())
		{
			return;
		}
		if (!ids.contains(itemManager.canonicalize(itemId)))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.drawImage(itemManager.getItemOutline(itemId, widgetItem.getQuantity(), config.inventoryHighlightColor()),
			bounds.x, bounds.y, null);
	}
}
