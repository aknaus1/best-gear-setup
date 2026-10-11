package com.bestgearsetup.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;

/**
 * Shared look for the sidebar's inputs and buttons. RuneLite's look and feel is FlatLaf, so the
 * styling is passed as FlatLaf client properties; other look and feels ignore them.
 */
final class Controls
{
	/** Inputs sit in a recessed well with a visible outline; buttons are raised above the panel. */
	static final Color WELL = ColorScheme.DARKER_GRAY_COLOR;
	static final Color OUTLINE = new Color(0x3E3E3E);
	static final Color OUTLINE_HOVER = new Color(0x5C5C5C);
	static final Color RAISED = new Color(0x363636);
	static final Color RAISED_HOVER = new Color(0x424242);
	static final Color RAISED_PRESSED = new Color(0x2C2C2C);
	static final Color RAISED_BORDER = new Color(0x4A4A4A);
	static final Color PRIMARY_HOVER = new Color(0xF09C14);
	static final Color PRIMARY_PRESSED = new Color(0xB87300);
	static final Color PRIMARY_TEXT = new Color(0x1A1A1A);
	static final Color DISABLED_TEXT = new Color(0x7A7A7A);
	static final Color ROW_HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;
	/** Collapsible sections are cards raised above the panel; their inputs stay recessed wells. */
	static final Color CARD = new Color(0x323232);
	static final Color CARD_OUTLINE = new Color(0x424242);
	static final Color CARD_HOVER = new Color(0x3B3B3B);

	/** FlatLaf applies a minimum height only to buttons that keep their default margins; others size by margin. */
	static final int PRIMARY_HEIGHT = 30;

	private static final String STYLE = "FlatLaf.style";
	private static final String MINIMUM_HEIGHT = "JComponent.minimumHeight";
	private static final String PLACEHOLDER = "JTextField.placeholderText";
	private static final String PADDING = "JTextField.padding";

	private Controls()
	{
	}

	/**
	 * RuneLite's fonts are UI resources, which a look and feel replaces whenever it reinstalls a
	 * component's UI; FlatLaf does that when a style is first set. A derived copy is kept.
	 */
	static Font plain(Font font)
	{
		return font.deriveFont(font.getStyle());
	}

	/** The one main action in view: filled brand orange. */
	static void primary(AbstractButton button)
	{
		button.putClientProperty(MINIMUM_HEIGHT, PRIMARY_HEIGHT);
		button.putClientProperty(STYLE, "borderWidth: 0; focusWidth: 0; arc: 0"
			+ "; hoverBackground: " + hex(PRIMARY_HOVER)
			+ "; pressedBackground: " + hex(PRIMARY_PRESSED)
			+ "; disabledBackground: " + hex(RAISED_PRESSED)
			+ "; disabledText: " + hex(DISABLED_TEXT));
		button.setBackground(ColorScheme.BRAND_ORANGE);
		button.setForeground(PRIMARY_TEXT);
		button.setFont(plain(FontManager.getRunescapeFont()));
	}

	/** Ordinary buttons: a raised grey chip that lightens on hover. */
	static void secondary(AbstractButton button)
	{
		button.putClientProperty(STYLE, "focusWidth: 0; arc: 0"
			+ "; borderColor: " + hex(RAISED_BORDER)
			+ "; hoverBorderColor: " + hex(OUTLINE_HOVER)
			+ "; hoverBackground: " + hex(RAISED_HOVER)
			+ "; pressedBackground: " + hex(RAISED_PRESSED)
			+ "; disabledBackground: " + hex(ColorScheme.DARK_GRAY_COLOR)
			+ "; disabledBorderColor: " + hex(OUTLINE)
			+ "; disabledText: " + hex(DISABLED_TEXT));
		button.setBackground(RAISED);
		button.setForeground(Color.WHITE);
		button.setFont(plain(FontManager.getRunescapeSmallFont()));
		button.setMargin(new Insets(3, 6, 3, 6));
	}

	/** A small status tag, e.g. the active Bosses filter: orange outline and text. */
	static void badge(AbstractButton button)
	{
		button.putClientProperty(STYLE, "focusWidth: 0; arc: 0"
			+ "; borderColor: " + hex(PRIMARY_PRESSED)
			+ "; hoverBorderColor: " + hex(ColorScheme.BRAND_ORANGE)
			+ "; hoverForeground: " + hex(PRIMARY_HOVER)
			+ "; hoverBackground: " + hex(RAISED_PRESSED)
			+ "; pressedBackground: " + hex(WELL));
		button.setBackground(ColorScheme.DARK_GRAY_COLOR);
		button.setForeground(ColorScheme.BRAND_ORANGE);
		button.setFont(plain(FontManager.getRunescapeSmallFont()));
		button.setMargin(new Insets(0, 4, 0, 4));
	}

	/** A text-only button such as Back or a disclosure toggle: no chrome until hovered. */
	static void ghost(AbstractButton button)
	{
		button.putClientProperty(STYLE, "buttonType: borderless; focusWidth: 0; arc: 0"
			+ "; hoverForeground: #FFFFFF"
			+ "; hoverBackground: " + hex(RAISED)
			+ "; pressedBackground: " + hex(RAISED_PRESSED));
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setFont(plain(FontManager.getRunescapeSmallFont()));
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setMargin(new Insets(3, 4, 3, 4));
	}

	/** The header of a collapsible card: the whole bar is the click target and lightens on hover. */
	static void cardHeader(AbstractButton button)
	{
		button.putClientProperty(STYLE, "buttonType: borderless; focusWidth: 0; arc: 0"
			+ "; hoverBackground: " + hex(CARD_HOVER)
			+ "; pressedBackground: " + hex(RAISED_PRESSED));
		button.setForeground(Color.WHITE);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setMargin(new Insets(5, 6, 5, 6));
		button.setIconTextGap(7);
		button.setFocusable(false);
	}

	/** A clickable row in a suggestion list, matching the monster search rows; it keeps its own padding border. */
	static void listRow(AbstractButton button)
	{
		button.putClientProperty(STYLE, "hoverBackground: " + hex(ROW_HOVER)
			+ "; pressedBackground: " + hex(RAISED_PRESSED));
		button.setBackground(WELL);
		button.setHorizontalAlignment(SwingConstants.LEFT);
	}

	/** Text fields, combo boxes and spinners: a dark well with an outline that turns orange on focus. */
	static void field(JComponent field)
	{
		field.putClientProperty(STYLE, "focusWidth: 0; arc: 0"
			+ "; borderColor: " + hex(OUTLINE)
			+ "; focusedBorderColor: " + hex(ColorScheme.BRAND_ORANGE)
			+ "; disabledBorderColor: " + hex(ColorScheme.DARK_GRAY_HOVER_COLOR));
		field.setBackground(WELL);
		if (field instanceof JTextField)
		{
			field.putClientProperty(PADDING, new Insets(0, 4, 0, 4));
		}
	}

	/** Outline the check box's square like the other fields. Call before setting its font. */
	static void checkBox(AbstractButton box)
	{
		box.putClientProperty(STYLE, "icon.borderColor: " + hex(OUTLINE)
			+ "; icon.selectedBorderColor: " + hex(OUTLINE)
			+ "; icon.hoverBorderColor: " + hex(OUTLINE_HOVER)
			+ "; icon.hoverSelectedBorderColor: " + hex(OUTLINE_HOVER)
			+ "; icon.background: " + hex(WELL)
			+ "; icon.selectedBackground: " + hex(WELL));
	}

	static void placeholder(JTextField field, String text)
	{
		field.putClientProperty(PLACEHOLDER, text);
	}

	/**
	 * RuneLite's search box: outline it like the other fields, turn the outline orange on focus,
	 * and show a placeholder while it is empty.
	 */
	static void search(IconTextField search, String placeholder)
	{
		search.setBackground(WELL);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		search.setBorder(BorderFactory.createLineBorder(OUTLINE));
		JTextField input = textFieldIn(search);
		if (input == null)
		{
			return;
		}
		placeholder(input, placeholder);
		input.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				search.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				search.setBorder(BorderFactory.createLineBorder(OUTLINE));
			}
		});
	}

	private static JTextField textFieldIn(Container container)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JTextField)
			{
				return (JTextField) child;
			}
			if (child instanceof Container)
			{
				JTextField found = textFieldIn((Container) child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Join a suggestion list to the search box above it; an empty list draws nothing. */
	static void outlineList(JPanel list)
	{
		list.setBorder(list.getComponentCount() == 0 ? null
			: BorderFactory.createMatteBorder(0, 1, 1, 1, OUTLINE));
	}

	/** A caption above a control. */
	static JLabel caption(String text)
	{
		JLabel label = new JLabel(text);
		BestGearSetupPanel.setSmall(label, ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	private static String hex(Color color)
	{
		return String.format("#%06X", color.getRGB() & 0xFFFFFF);
	}

	/** A small chevron, drawn because the RuneScape font has no arrow glyphs. */
	static final class Chevron implements Icon
	{
		static final int LEFT = 0;
		static final int RIGHT = 1;
		static final int DOWN = 2;

		private final int direction;
		/** Null draws in the component's foreground, so the icon follows hover colours. */
		private final Color color;

		Chevron(int direction, Color color)
		{
			this.direction = direction;
			this.color = color;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.setColor(color == null ? c.getForeground() : color);
			g2.translate(x, y);
			switch (direction)
			{
				case LEFT:
					g2.drawPolyline(new int[]{6, 2, 6}, new int[]{1, 5, 9}, 3);
					break;
				case RIGHT:
					g2.drawPolyline(new int[]{3, 7, 3}, new int[]{1, 5, 9}, 3);
					break;
				default:
					g2.drawPolyline(new int[]{1, 5, 9}, new int[]{3, 7, 3}, 3);
					break;
			}
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 10;
		}

		@Override
		public int getIconHeight()
		{
			return 10;
		}
	}
}
