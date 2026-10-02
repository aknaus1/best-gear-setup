package com.bestgearsetup.ui;

import com.bestgearsetup.Budget;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.ItemCosts;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.WeaponRules;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The worn-equipment layout from the game:
 * <pre>
 *        head
 *  cape  neck  ammo
 *  weapon body shield
 *        legs
 *  hands feet  ring
 * </pre>
 * Slots filled for defence / prayer are highlighted blue, locked slots get an orange frame and padlock
 * (red when the lock could not be applied, with the locked item shown faded), and a corner marker shows
 * whether the item is owned (green), to buy (yellow) or untradeable (orange).
 */
class EquipmentGrid extends JPanel
{
	static final int SLOT_SIZE = 44;
	private static final Color TILE = new Color(0x3E, 0x35, 0x29);
	private static final Color TILE_EDGE = new Color(0x5A, 0x4D, 0x3B);
	private static final Color FILLED = new Color(0x3D, 0x6F, 0xC4);
	private static final Color LOCKED = new Color(0xFF, 0x98, 0x1F);
	private static final Color BLOCKED = new Color(0xFF, 0x4D, 0x4D);
	private static final Color OWNED = new Color(0x6B, 0xD5, 0x6B);
	private static final Color BUY = new Color(0xFF, 0xD2, 0x4D);
	private static final Color UNTRADEABLE = new Color(0xFF, 0x9F, 0x40);

	private static final Slot[] LAYOUT = {
		null, Slot.HEAD, null,
		Slot.CAPE, Slot.NECK, Slot.AMMO,
		Slot.WEAPON, Slot.BODY, Slot.SHIELD,
		null, Slot.LEGS, null,
		Slot.HANDS, Slot.FEET, Slot.RING,
	};

	/** @param locks how each locked slot affected the search that found this setup */
	EquipmentGrid(SetupResult result, Map<Slot, LockStatus> locks, BestGearSetupPlugin plugin, ItemManager itemManager,
		SpriteCache sprites, int ammoCount)
	{
		super(new GridLayout(5, 3, 6, 4));
		setOpaque(false);
		Loadout l = result.getLoadout();
		Set<Slot> filled = result.getFilledSlots();
		for (Slot slot : LAYOUT)
		{
			if (slot == null)
			{
				JPanel empty = new JPanel();
				empty.setOpaque(false);
				add(empty);
				continue;
			}
			GearItem item = l.get(slot);
			boolean darts = false;
			if (slot == Slot.AMMO && item == null && l.getLoadedAmmo() != null)
			{
				// Blowpipe darts are shown in the ammo slot.
				item = l.getLoadedAmmo();
				darts = true;
			}
			add(new SlotBox(slot, item, darts, filled.contains(slot), locks.get(slot), plugin, itemManager, sprites,
				ammoCount));
		}
		setMaximumSize(getPreferredSize());
	}

	private static int placeholderSprite(Slot slot)
	{
		switch (slot)
		{
			case HEAD:
				return SpriteID.Wornicons.HEAD;
			case CAPE:
				return SpriteID.Wornicons.CAPE;
			case NECK:
				return SpriteID.Wornicons.NECK;
			case AMMO:
				return SpriteID.Wornicons.AMMUNITION;
			case WEAPON:
				return SpriteID.Wornicons.WEAPON;
			case BODY:
				return SpriteID.Wornicons.TORSO;
			case SHIELD:
				return SpriteID.Wornicons.SHIELD;
			case LEGS:
				return SpriteID.Wornicons.LEGS;
			case HANDS:
				return SpriteID.Wornicons.HANDS;
			case FEET:
				return SpriteID.Wornicons.FEET;
			default:
				return SpriteID.Wornicons.RING;
		}
	}

	private static final class SlotBox extends JComponent
	{
		private final Slot slot;
		private final GearItem item;
		private final boolean filled;
		private final LockStatus lock;
		private final SpriteCache sprites;
		private final AsyncBufferedImage icon;
		/** The locked item that could not be used, drawn faded in its empty slot. */
		private final AsyncBufferedImage ghost;
		private final Color marker;

		SlotBox(Slot slot, GearItem item, boolean darts, boolean filled, LockStatus lock, BestGearSetupPlugin plugin,
			ItemManager itemManager, SpriteCache sprites, int ammoCount)
		{
			this.slot = slot;
			this.item = item;
			this.filled = filled;
			this.lock = lock;
			this.sprites = sprites;
			setPreferredSize(new Dimension(SLOT_SIZE, SLOT_SIZE));
			boolean locked = lock != null;
			GearItem lockedItem = locked ? lock.getItem() : null;

			if (item == null && lockedItem != null && lock.isBlocking())
			{
				icon = null;
				marker = null;
				ghost = itemManager.getImage(lockedItem.getId());
				ghost.onLoaded(this::repaint);
				setToolTipText("<html><b>Locked to " + BestGearSetupPanel.escape(GameData.titleCase(lockedItem.getName()))
					+ "</b><br>Can't be used: " + BestGearSetupPanel.escape(lock.getProblem()) + ".<br>"
					+ BestGearSetupPanel.escape(lock.getEffect()) + "<br><i>Right-click to unlock</i></html>");
				setComponentPopupMenu(ItemMenus.create(lockedItem, slot, true, plugin));
				return;
			}
			ghost = null;
			if (item == null)
			{
				icon = null;
				marker = null;
				setToolTipText(slot.getDisplayName() + (lockDescription(lock).isEmpty() ? " (empty)" : " - " + lockDescription(lock))
					+ (locked && lock.isBlocking() ? ". Can't be used: " + lock.getProblem() : "")
					+ " - right-click for options");
			}
			else
			{
				icon = itemManager.getImage(item.getId());
				icon.onLoaded(this::repaint);
				boolean owned = plugin.owns(item);
				marker = owned ? OWNED : !item.isTradeable() ? UNTRADEABLE : BUY;
				StringBuilder tip = new StringBuilder("<html><b>").append(BestGearSetupPanel.escape(GameData.titleCase(item.getName())))
					.append("</b>");
				if (darts)
				{
					tip.append(" (loaded in blowpipe)");
				}
				tip.append("<br>");
				if (owned)
				{
					tip.append("Owned");
				}
				else if (!item.isTradeable())
				{
					tip.append("Untradeable");
					long cost = plugin.price(item);
					if (!ItemCosts.isKnown(cost))
					{
						tip.append("<br>Components: no current price");
					}
					else if (cost > 0)
					{
						tip.append("<br>Components: ").append(Budget.format(cost)).append(" gp");
					}
				}
				else
				{
					boolean ammo = slot == Slot.AMMO && WeaponRules.isAmmunition(item);
					long cost = plugin.price(item);
					tip.append("Buy: ").append(ItemCosts.isKnown(cost) ? Budget.format(cost) + " gp" : "no current price");
					if (ammo && ItemCosts.isKnown(cost))
					{
						tip.append(" each").append(ammoCount > 0 ? " (x" + ammoCount + ")" : "");
					}
				}
				if (filled)
				{
					tip.append("<br>Filled for defence / prayer (no DPS)");
				}
				if (locked)
				{
					tip.append("<br>").append(BestGearSetupPanel.escape(lockDescription(lock)));
				}
				tip.append("<br><i>Right-click for options</i></html>");
				setToolTipText(tip.toString());
			}
			setComponentPopupMenu(ItemMenus.create(item, darts ? null : slot, locked, plugin));
		}

		private static String lockDescription(LockStatus lock)
		{
			if (lock == null)
			{
				return "";
			}
			switch (lock.getLock().getKind())
			{
				case EMPTY:
					return "Locked empty";
				case FILL:
					return "Locked: always filled for defence / prayer";
				default:
					return lock.getItem() == null ? "Locked to item " + lock.getLock().getItemId()
						: "Locked to " + GameData.titleCase(lock.getItem().getName());
			}
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();

			BufferedImage tile = sprites.get(SpriteID.Miscgraphics.EQUIPMENT_SLOT_TILE, this::repaint);
			if (tile != null)
			{
				g2.drawImage(tile, 0, 0, w, h, null);
			}
			else
			{
				g2.setColor(TILE);
				g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
				g2.setColor(TILE_EDGE);
				g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
			}
			if (filled)
			{
				g2.setColor(new Color(FILLED.getRed(), FILLED.getGreen(), FILLED.getBlue(), 150));
				g2.fillRoundRect(2, 2, w - 4, h - 4, 8, 8);
			}

			if (icon != null)
			{
				g2.drawImage(icon, (w - icon.getWidth()) / 2, (h - icon.getHeight()) / 2, null);
			}
			else if (ghost != null)
			{
				Graphics2D faded = (Graphics2D) g2.create();
				faded.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
				faded.drawImage(ghost, (w - ghost.getWidth()) / 2, (h - ghost.getHeight()) / 2, null);
				faded.dispose();
				g2.setColor(BLOCKED);
				g2.setStroke(new BasicStroke(2f));
				g2.drawLine(8, h - 8, w - 8, 8);
			}
			else
			{
				BufferedImage ph = sprites.get(placeholderSprite(slot), this::repaint);
				if (ph != null)
				{
					g2.drawImage(ph, (w - ph.getWidth()) / 2, (h - ph.getHeight()) / 2, null);
				}
			}

			if (marker != null)
			{
				g2.setColor(marker);
				g2.fillPolygon(new int[]{w - 9, w - 2, w - 2}, new int[]{h - 2, h - 2, h - 9}, 3);
			}
			if (lock != null)
			{
				Color frame = lock.isBlocking() ? BLOCKED : LOCKED;
				g2.setColor(frame);
				g2.setStroke(new BasicStroke(2f));
				g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
				// Padlock in the top-left corner.
				g2.setStroke(new BasicStroke(1.5f));
				g2.drawArc(5, 3, 6, 7, 0, 180);
				g2.fillRect(4, 7, 8, 6);
			}
			g2.dispose();
		}
	}
}
