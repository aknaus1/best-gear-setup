package com.bestgearsetup;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Outlines the selected setup's worn equipment and CM chest guidance in inventory and private storage. */
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
		showOnInterfaces(InterfaceID.RAIDS_STORAGE_SIDE, InterfaceID.RAIDS_STORAGE_PRIVATE);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		Set<Integer> ids = plugin.getInventoryHighlightIds();
		if (!config.highlightInventoryGear() || itemId <= 0 || widgetItem.getQuantity() <= 0)
		{
			return;
		}
		int canonical = itemManager.canonicalize(itemId);
		boolean stored = widgetItem.getWidget() != null
			&& (widgetItem.getWidget().getId() >>> 16) == InterfaceID.RAIDS_STORAGE_PRIVATE;
		Color color;
		if (plugin.isCoxChestHighlightMode())
		{
			color = plugin.coxChestHighlightColor(canonical);
			// Equipping the selected encounter's gear remains useful with storage closed too.
			if (!stored && ids.contains(canonical) && (color == null || CoxChestPlan.NEEDED_COLOR.equals(color)))
			{ color = config.inventoryHighlightColor(); }
			else if (color == null || stored && CoxChestPlan.FINISHED_COLOR.equals(color)
				|| !stored && CoxChestPlan.NEEDED_COLOR.equals(color)) { return; }
		}
		else
		{
			if (stored || !ids.contains(canonical)) { return; }
			color = config.inventoryHighlightColor();
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.drawImage(itemManager.getItemOutline(itemId, widgetItem.getQuantity(), color),
			bounds.x, bounds.y, null);
	}
}
