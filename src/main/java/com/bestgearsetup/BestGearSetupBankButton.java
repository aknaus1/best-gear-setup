package com.bestgearsetup;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

/** Bank title-bar control for the selected setup, styled and placed like Quest Helper's title-bar button. */
class BestGearSetupBankButton
{
	// Quest Helper's button geometry: a 25px square at x=408, with its icon inset by 3px.
	private static final int QUEST_HELPER_X = 408;
	private static final int Y = 5;
	private static final int SIZE = 25;
	private static final int ICON_INSET = 3;
	// Keep the gap tight: the bank's Close button sits just right of Quest Helper's.
	private static final int GAP = 1;
	private final Client client;
	private final ClientThread clientThread;
	private final BestGearSetupBankView view;
	private Widget parent;
	private Widget background;
	private Widget icon;
	private boolean enabled;
	private boolean lastSelected;
	private boolean lastAvailable;

	BestGearSetupBankButton(Client client, ClientThread clientThread, BestGearSetupBankView view)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.view = view;
	}

	void start()
	{
		enabled = true;
		clientThread.invokeLater(this::update);
	}

	void stop()
	{
		enabled = false;
		clientThread.invokeLater(this::remove);
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		update();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			remove();
			clientThread.invokeLater(this::update);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && event.isUnload())
		{
			remove();
		}
	}

	private void update()
	{
		if (!enabled)
		{
			return;
		}
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (bank == null || bank.isHidden())
		{
			return;
		}
		boolean selected = view.isActive();
		boolean available = view.hasSelection();
		if (!available)
		{
			if (background != null)
			{
				background.setHidden(true);
				background.clearActions();
				icon.setHidden(true);
			}
			lastAvailable = false;
			return;
		}
		boolean created = parent != bank || background == null;
		if (created)
		{
			remove();
			parent = bank;
			background = graphic(SIZE, SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL);
			background.setName("<col=ff981f>Best Gear Setup</col>");
			background.setHasListener(true);
			background.setOnOpListener((JavaScriptCallback) event ->
			{
				int op = event.getOp();
				clientThread.invokeLater(() -> activate(op));
			});
			icon = graphic(SIZE - 2 * ICON_INSET, SpriteID.ICON_SWORDS);
		}
		// Take Quest Helper's slot by the Close button, or sit just right of its button while it's shown.
		place(questHelperShown(bank) ? QUEST_HELPER_X + SIZE + GAP : QUEST_HELPER_X);
		if (created || selected != lastSelected || available != lastAvailable)
		{
			background.setSpriteId(selected ? SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED
				: SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL);
			background.setHidden(false);
			icon.setHidden(false);
			background.clearActions();
			background.setAction(0, selected ? "Show all items" : "Gear layout");
			background.revalidate();
			lastSelected = selected;
			lastAvailable = available;
		}
	}

	private boolean questHelperShown(Widget bank)
	{
		Widget[] children = bank.getChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				if (child != null && "quest-helper".equals(child.getName()) && !child.isSelfHidden())
				{
					return true;
				}
			}
		}
		return false;
	}

	private void place(int x)
	{
		if (background.getOriginalX() == x)
		{
			return;
		}
		background.setOriginalX(x);
		background.setOriginalY(Y);
		background.revalidate();
		icon.setOriginalX(x + ICON_INSET);
		icon.setOriginalY(Y + ICON_INSET);
		icon.revalidate();
	}

	private Widget graphic(int size, int sprite)
	{
		Widget widget = parent.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(size);
		widget.setOriginalHeight(size);
		widget.setSpriteId(sprite);
		widget.revalidate();
		return widget;
	}

	private void activate(int op)
	{
		if (!enabled || background == null || !view.hasSelection())
		{
			return;
		}
		if (op == 1)
		{
			client.playSoundEffect(SoundEffectID.UI_BOOP);
			if (view.isActive())
			{
				view.clear();
			}
			else if (view.isPotionStoreOpen())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Close the potion store to view the gear setup.", null);
			}
			else
			{
				view.show();
			}
		}
		update();
	}

	private void remove()
	{
		if (background != null)
		{
			background.setHidden(true);
			background.clearActions();
			background.setOnOpListener((Object[]) null);
			icon.setHidden(true);
		}
		parent = background = icon = null;
	}
}
