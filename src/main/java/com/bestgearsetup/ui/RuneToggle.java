package com.bestgearsetup.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JToggleButton;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * One rune in a rune card's grid: its icon on a small tile, framed when it is in the card's list and dimmed
 * when it is not usable. Selected means "in this card's list".
 */
class RuneToggle extends JToggleButton
{
	static final Dimension CELL_SIZE = new Dimension(40, 36);
	private static final float DIM_ALPHA = 0.38f;

	/** Null in tests, which show the rune's name instead. */
	private final BufferedImage icon;
	private final String label;
	private Color frame = Color.WHITE;
	private boolean dimmed;

	RuneToggle(BufferedImage icon, String label)
	{
		this.icon = icon;
		this.label = label;
		if (icon instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) icon).onLoaded(this::repaint);
		}
		setPreferredSize(CELL_SIZE);
		setMinimumSize(new Dimension(1, CELL_SIZE.height));
		setFocusable(true);
		setFocusPainted(false);
		setContentAreaFilled(false);
		setBorderPainted(false);
		setOpaque(false);
		setRolloverEnabled(true);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	/** Set how the rune looks: the frame colour used while selected, and whether it is dimmed. */
	void setLook(Color frame, boolean dimmed)
	{
		this.frame = frame;
		this.dimmed = dimmed;
		repaint();
	}

	/** Whether the rune is drawn faded because it is blocked or not in a non-empty only-list. */
	boolean isDimmed()
	{
		return dimmed;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			boolean hover = getModel().isRollover();
			g2.setColor(hover ? Controls.RAISED_HOVER : Controls.RAISED);
			g2.fillRect(0, 0, w, h);

			Graphics2D content = (Graphics2D) g2.create();
			if (dimmed)
			{
				content.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DIM_ALPHA));
			}
			if (icon != null)
			{
				content.drawImage(icon, (w - icon.getWidth()) / 2, (h - icon.getHeight()) / 2, null);
			}
			else
			{
				Font font = Controls.plain(FontManager.getRunescapeSmallFont());
				content.setFont(font);
				content.setColor(Color.WHITE);
				FontMetrics fm = content.getFontMetrics();
				content.drawString(label, Math.max(0, (w - fm.stringWidth(label)) / 2),
					(h - fm.getHeight()) / 2 + fm.getAscent());
			}
			content.dispose();

			if (isSelected())
			{
				g2.setColor(frame);
				g2.setStroke(new java.awt.BasicStroke(2));
				g2.drawRect(1, 1, w - 3, h - 3);
			}
			else
			{
				g2.setColor(hover ? Controls.OUTLINE_HOVER : Controls.OUTLINE);
				g2.drawRect(0, 0, w - 1, h - 1);
			}
			if (hover && isSelected())
			{
				g2.setColor(Controls.OUTLINE_HOVER);
				g2.drawRect(0, 0, w - 1, h - 1);
			}
			if (hasFocus())
			{
				g2.setColor(Color.WHITE);
				g2.setStroke(new java.awt.BasicStroke(1f, java.awt.BasicStroke.CAP_BUTT,
					java.awt.BasicStroke.JOIN_MITER, 10f, new float[]{2f, 2f}, 0f));
				g2.drawRect(3, 3, w - 7, h - 7);
			}
		}
		finally
		{
			g2.dispose();
		}
	}
}
