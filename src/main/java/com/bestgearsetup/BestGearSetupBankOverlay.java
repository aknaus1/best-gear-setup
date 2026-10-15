package com.bestgearsetup;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/** Outlines the selected setup's equipment and spell runes in the bank, and tags when each raid boost is drunk. */
class BestGearSetupBankOverlay extends WidgetItemOverlay
{
	private final BestGearSetupPlugin plugin;
	private final BestGearSetupConfig config;
	private final ItemManager itemManager;
	@Inject private Client client;
	@Inject private BestGearSetupBankView bankView;
	@Inject private TooltipManager tooltipManager;

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
		if (client != null && bankView != null && tooltipManager != null && itemId > 0
			&& widgetItem.getQuantity() > 0 && widgetItem.getCanvasBounds().contains(client.getMouseCanvasPosition().getX(),
				client.getMouseCanvasPosition().getY()))
		{
			String guidance = bankView.boostGuidance(itemId);
			if (guidance != null) { tooltipManager.add(new Tooltip(guidance)); }
		}
		RaidPlan.PotionUse use = bankView == null || itemId <= 0 ? null : bankView.potionUse(itemId);
		if (use != null && use.getTag() != null) { drawTag(graphics, use.getTag(), widgetItem.getCanvasBounds()); }
		Set<Integer> ids = plugin.getBankHighlightIds();
		if (!config.highlightBankGear() || itemId <= 0 || widgetItem.getQuantity() <= 0
			|| ids.isEmpty())
		{
			return;
		}
		if (!plugin.shouldHighlightBankItem(itemManager.canonicalize(itemId)))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.drawImage(itemManager.getItemOutline(itemId, widgetItem.getQuantity(), config.bankHighlightColor()),
			bounds.x, bounds.y, null);
	}

	/** The tag in the stack's bottom-left corner, clear of the game's quantity, shadowed like it. */
	private static void drawTag(Graphics2D graphics, String tag, Rectangle bounds)
	{
		graphics.setFont(FontManager.getRunescapeSmallFont());
		int baseline = bounds.y + bounds.height - 1;
		graphics.setColor(Color.BLACK);
		graphics.drawString(tag, bounds.x + 1, baseline + 1);
		graphics.setColor(Color.CYAN);
		graphics.drawString(tag, bounds.x, baseline);
	}
}
