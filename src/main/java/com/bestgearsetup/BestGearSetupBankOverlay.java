package com.bestgearsetup;

import com.bestgearsetup.ui.Theme;
import java.awt.*;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the selected setup's equipment and spell runes in the bank. Not over the equipment layout, which shows only
 * those items.
 */
class BestGearSetupBankOverlay extends WidgetItemOverlay
{
	private final BestGearSetupPlugin plugin;
	private final Options config;
	private final ItemManager itemManager;
	private final BestGearSetupBankView bankView;

	@Inject
	BestGearSetupBankOverlay(BestGearSetupPlugin plugin, Options config, ItemManager itemManager,
		BestGearSetupBankView bankView)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.bankView = bankView;
		showOnBank();
	}

	/** Also outlines the bank button while the Getting started guide points at it. */
	@Override
	public Dimension render(Graphics2D graphics)
	{
		Dimension size = super.render(graphics);
		Rectangle button = plugin.guideBankButton();
		if (button != null)
		{
			graphics.setColor(Theme.color("accent"));
			graphics.setStroke(new BasicStroke(2));
			graphics.drawRect(button.x, button.y, button.width, button.height);
		}
		return size;
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		Set<Integer> ids = plugin.getBankHighlightIds();
		if (!config.highlightBankGear() || bankView.isActive() || itemId <= 0 || widgetItem.getQuantity() <= 0
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
