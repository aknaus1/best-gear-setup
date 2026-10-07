package com.bestgearsetup;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Outlines the selected setup's equipment and spell runes in the bank. */
class BestGearSetupBankOverlay extends WidgetItemOverlay
{
	private final BestGearSetupPlugin plugin;
	private final BestGearSetupConfig config;
	private final ItemManager itemManager;

	@Inject
	BestGearSetupBankOverlay(BestGearSetupPlugin plugin, BestGearSetupConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		Set<Integer> ids = plugin.getBankHighlightIds();
		if (!config.highlightBankGear() || itemId <= 0 || widgetItem.getQuantity() <= 0
			|| ids.isEmpty())
		{
			return;
		}
		if (!ids.contains(itemManager.canonicalize(itemId)))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.drawImage(itemManager.getItemOutline(itemId, widgetItem.getQuantity(), config.bankHighlightColor()),
			bounds.x, bounds.y, null);
	}
}
