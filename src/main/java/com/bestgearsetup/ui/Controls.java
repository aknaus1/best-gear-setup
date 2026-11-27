package com.bestgearsetup.ui;

import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ColorJButton;
import net.runelite.client.ui.components.IconTextField;

/**
 * Shared look for the sidebar's inputs and buttons. RuneLite's look and feel is FlatLaf, so most styling is
 * passed as FlatLaf client properties, which other look and feels ignore. Buttons, cards and boxes follow the
 * {@link Theme}: its flat and edge painters are FlatLaf styles; the others are painted by {@link Forged}.
 */
final class Controls
{
	/** Inputs sit in a recessed well with a visible outline; buttons are raised above the panel. */
	static final Color WELL = ColorScheme.DARKER_GRAY_COLOR;
	static final Color OUTLINE = new Color(0x3E3E3E);
	static final Color DISABLED_TEXT = new Color(0x7A7A7A);

	/** The main button is at least this tall whatever its margin. */
	static final int PRIMARY_HEIGHT = 30;

	private static final String STYLE = "FlatLaf.style";
	private static final String MINIMUM_HEIGHT = "JComponent.minimumHeight";
	/** Extra room above the banner's label for its band, which sits below its tails. */
	private static final int BANNER_PAD = 2;
	/** Which of primary, secondary, wiki or header a button is, so a theme change can repaint it. */
	private static final String KIND = "bestgearsetup.kind";
	private static final String ROLE = "bestgearsetup.role";
	/** The Swing colours the panel is built with, in the order of the theme colours that replace them. */
	private static final java.util.List<Color> BUILT = java.util.Arrays.asList(ColorScheme.DARK_GRAY_COLOR,
		ColorScheme.DARKER_GRAY_COLOR, Color.WHITE, ColorScheme.LIGHT_GRAY_COLOR);
	private static final String[] ROLES = {"panel", "well", "text", "muted"};
	private static final java.util.List<String> INPUTS = java.util.Arrays.asList("field", "search", "check");
	private static final java.util.List<String> MINOR = java.util.Arrays.asList("badge", "ghost", "row", "scroll",
		"progress");
	/** The font a button was built with, kept so a theme's "default" font can put it back. */
	private static final String BUILT_FONT = "bestgearsetup.font";
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

	/** The one main action in view. */
	static void primary(AbstractButton button)
	{
		button.putClientProperty(KIND, "primary");
		button.setFont(plain(FontManager.getRunescapeFont()));
		skin(button);
	}

	/** Ordinary buttons. */
	static void secondary(AbstractButton button)
	{
		button.putClientProperty(KIND, "secondary");
		button.setFont(plain(FontManager.getRunescapeSmallFont()));
		button.setMargin(new Insets(3, 6, 3, 6));
		skin(button);
	}

	/** The Wiki link: a small ordinary button, or the theme's ribbon. */
	static void wiki(AbstractButton button)
	{
		button.putClientProperty(KIND, "wiki");
		skin(button);
	}

	/**
	 * Re-applies the current theme to every themed button under {@code root}. Every container is invalidated too:
	 * Swing passes invalidation up only through valid parents, so a panel not laid out since it was built (such as a
	 * sidebar never opened) would otherwise keep its layouts' cached sizes from the old theme.
	 */
	static void restyle(Container root)
	{
		root.invalidate();
		if (root instanceof JComponent && ((JComponent) root).getClientProperty(KIND) == null)
		{
			recolour((JComponent) root);
		}
		for (Component child : root.getComponents())
		{
			Object kind = child instanceof JComponent ? ((JComponent) child).getClientProperty(KIND) : null;
			if (kind == null && child instanceof JComponent)
			{
				recolour((JComponent) child);
			}
			else if (INPUTS.contains(kind))
			{
				skinInput((JComponent) child);
				recolour((JComponent) child);
			}
			else if (MINOR.contains(kind))
			{
				skinMinor((JComponent) child);
				recolour((JComponent) child);
			}
			else if (kind != null)
			{
				skin((AbstractButton) child);
			}
			if (child instanceof Container)
			{
				restyle((Container) child);
			}
		}
	}

	/**
	 * Gives a component the theme's panel, well, text or muted colour in place of the Swing colour it was built
	 * with; the first restyle records which it was, so later themes recolour it again.
	 */
	private static void recolour(JComponent c)
	{
		int[] role = (int[]) c.getClientProperty(ROLE);
		// A colour swatch's text is black or white to contrast with its own colour
		if (role == null && c instanceof ColorJButton)
		{
			ownColours(c);
			return;
		}
		if (role == null)
		{
			role = new int[]{BUILT.indexOf(c.getBackground()), BUILT.indexOf(c.getForeground())};
			c.putClientProperty(ROLE, role);
		}
		if (role[0] == 0 || role[0] == 1)
		{
			c.setBackground(Theme.color(ROLES[role[0]]));
		}
		if (role[1] == 2 || role[1] == 3)
		{
			c.setForeground(Theme.color(ROLES[role[1]]));
		}
	}

	/** Leaves a component's colours to the code that sets them, which follows the theme itself. */
	static void ownColours(JComponent c)
	{
		c.putClientProperty(ROLE, new int[]{-1, -1});
	}

	private static void skin(AbstractButton button)
	{
		String kind = (String) button.getClientProperty(KIND);
		if (kind.equals("header"))
		{
			button.putClientProperty(STYLE, "buttonType: borderless; focusWidth: 0; arc: 0"
				+ "; hoverBackground: " + hex(Theme.color("headerHover"))
				+ "; pressedBackground: " + hex(Theme.color("headerPressed")));
			button.setForeground(Theme.color("headerText"));
			return;
		}
		String painter = Theme.style(kind);
		boolean wiki = kind.equals("wiki");
		if (!wiki)
		{
			if (button.getClientProperty(BUILT_FONT) == null)
			{
				button.putClientProperty(BUILT_FONT, button.getFont());
			}
			String font = Theme.style(kind.equals("primary") ? "primaryFont" : "buttonFont");
			button.setFont(font.equals("default") ? (Font) button.getClientProperty(BUILT_FONT)
				: plain(font.equals("bold") ? FontManager.getRunescapeBoldFont()
				: font.equals("small") ? FontManager.getRunescapeSmallFont() : FontManager.getRunescapeFont()));
		}
		if (wiki)
		{
			boolean ribbon = painter.equals("ribbon");
			button.setFont(plain(ribbon ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont()));
			button.setMargin(ribbon ? new Insets(BANNER_PAD, 10, 0, 10) : new Insets(1, 6, 1, 6));
		}
		if (!painter.equals("flat") && !painter.equals("edge"))
		{
			if (!(button.getUI() instanceof Forged) || !((Forged) button.getUI()).paints(kind.equals("primary"), painter))
			{
				button.setUI(new Forged(kind.equals("primary"), painter));
			}
			button.repaint();
			return;
		}
		boolean edge = painter.equals("edge");
		// Back from a painter of ours: FlatLaf puts its own UI and border back only when none is set.
		if (button.getUI() instanceof Forged || !edge && (button.getBorder() instanceof Edge || button.getBorder() instanceof Lip))
		{
			button.setBorder(null);
			button.updateUI();
		}
		if (kind.equals("primary"))
		{
			button.putClientProperty(MINIMUM_HEIGHT, PRIMARY_HEIGHT);
			// The edge painter's border is the plugin's own, which takes no FlatLaf border settings
			button.putClientProperty(STYLE, (edge ? "" : "borderWidth: 0; focusWidth: 0; arc: 0; ")
				+ "hoverBackground: " + hex(Theme.color("primaryHover"))
				+ "; pressedBackground: " + hex(Theme.color("primaryPressed"))
				+ "; disabledBackground: #2C2C2C"
				+ "; disabledText: " + hex(DISABLED_TEXT));
			button.setBackground(Theme.color("primaryFill"));
			button.setForeground(Theme.color("primaryText"));
			if (edge)
			{
				button.setBorder(new Lip());
			}
			return;
		}
		button.putClientProperty(STYLE, (edge ? "" : "focusWidth: 0; arc: 0"
			+ "; borderColor: " + hex(Theme.color("buttonBorder"))
			+ "; hoverBorderColor: " + hex(Theme.color("buttonHoverBorder"))
			+ "; disabledBorderColor: " + hex(OUTLINE) + "; ")
			+ "hoverBackground: " + hex(Theme.color("buttonHover"))
			+ "; pressedBackground: " + hex(Theme.color("buttonPressed"))
			+ "; disabledBackground: " + hex(ColorScheme.DARK_GRAY_COLOR)
			+ "; disabledText: " + hex(DISABLED_TEXT));
		button.setBackground(Theme.color("buttonFill"));
		button.setForeground(Theme.color("buttonText"));
		if (edge)
		{
			button.setBorder(new Edge(Theme.color(wiki ? "wikiAccent" : "accent")));
		}
	}

	/** A small status tag, e.g. the active Bosses filter: orange outline and text. */
	static void badge(AbstractButton button)
	{
		button.putClientProperty(KIND, "badge");
		skinMinor(button);
		button.setFont(plain(FontManager.getRunescapeSmallFont()));
		button.setMargin(new Insets(0, 4, 0, 4));
	}

	/** A text-only button such as Back or a disclosure toggle: no chrome until hovered. */
	static void ghost(AbstractButton button)
	{
		button.putClientProperty(KIND, "ghost");
		skinMinor(button);
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setFont(plain(FontManager.getRunescapeSmallFont()));
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setMargin(new Insets(3, 4, 3, 4));
	}

	/** The header of a collapsible card: the whole bar is the click target and lightens on hover. */
	static void cardHeader(AbstractButton button)
	{
		button.putClientProperty(KIND, "header");
		skin(button);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setMargin(new Insets(5, 6, 5, 6));
		button.setIconTextGap(7);
		button.setFocusable(false);
	}

	/** A clickable row in a suggestion list, matching the monster search rows; it keeps its own padding border. */
	static void listRow(AbstractButton button)
	{
		button.putClientProperty(KIND, "row");
		skinMinor(button);
		button.setHorizontalAlignment(SwingConstants.LEFT);
	}

	/** Text fields, combo boxes and spinners: a dark well with an outline that turns orange on focus. */
	static void field(JComponent field)
	{
		field.putClientProperty(KIND, "field");
		skinInput(field);
		if (field instanceof JTextField)
		{
			field.putClientProperty(PADDING, new Insets(0, 4, 0, 4));
		}
	}

	/** Outline the check box's square like the other fields. Call before setting its font. */
	static void checkBox(AbstractButton box)
	{
		box.putClientProperty(KIND, "check");
		skinInput(box);
	}

	/** Marks a scroll pane or progress bar for the theme's scrollbar or progress colours. */
	static void themed(JComponent c, String kind)
	{
		c.putClientProperty(KIND, kind);
		skinMinor(c);
	}

	/** Text-only buttons, list rows, the badge, scrollbars and progress bars in the theme's colours. */
	private static void skinMinor(JComponent c)
	{
		switch ((String) c.getClientProperty(KIND))
		{
			case "badge":
				c.putClientProperty(STYLE, "focusWidth: 0; arc: 0"
					+ "; borderColor: " + hex(Theme.color("badgeRim"))
					+ "; hoverBorderColor: " + hex(Theme.color("badge"))
					+ "; hoverForeground: " + hex(Theme.color("badgeHover"))
					+ "; hoverBackground: #2C2C2C"
					+ "; pressedBackground: " + hex(Theme.color("well")));
				c.setBackground(Theme.color("panel"));
				c.setForeground(Theme.color("badge"));
				break;
			case "ghost":
				c.putClientProperty(STYLE, "buttonType: borderless; focusWidth: 0; arc: 0"
					+ "; hoverForeground: " + hex(Theme.color("text"))
					+ "; hoverBackground: " + hex(Theme.color("ghostHover"))
					+ "; pressedBackground: #2C2C2C");
				break;
			case "row":
				c.putClientProperty(STYLE, "hoverBackground: " + hex(Theme.color("rowHover"))
					+ "; pressedBackground: #2C2C2C");
				c.setBackground(Theme.color("well"));
				break;
			case "scroll":
				((JScrollPane) c).getVerticalScrollBar().putClientProperty(STYLE, "track: " + hex(Theme.color("scrollTrack"))
					+ "; thumb: " + hex(Theme.color("scrollThumb"))
					+ "; hoverTrackColor: " + hex(Theme.color("scrollTrack"))
					+ "; hoverThumbColor: " + hex(Theme.color("scrollHover"))
					+ "; pressedThumbColor: " + hex(Theme.color("scrollHover")));
				break;
			default:
				c.setForeground(Theme.color("progress"));
				c.setBackground(Theme.color("well"));
		}
	}

	/** Fields, check boxes and the search box in the theme's well and outline colours. */
	private static void skinInput(JComponent c)
	{
		String kind = (String) c.getClientProperty(KIND);
		String well = hex(Theme.color("well"));
		String outline = hex(Theme.color("wellOutline"));
		if (kind.equals("check"))
		{
			c.putClientProperty(STYLE, "icon.borderColor: " + outline
				+ "; icon.selectedBorderColor: " + outline
				+ "; icon.hoverBorderColor: #5C5C5C"
				+ "; icon.hoverSelectedBorderColor: #5C5C5C"
				+ "; icon.background: " + well
				+ "; icon.selectedBackground: " + well);
			return;
		}
		c.setBackground(Theme.color("well"));
		if (kind.equals("search"))
		{
			c.setBorder(BorderFactory.createLineBorder(Theme.color("wellOutline")));
			return;
		}
		c.putClientProperty(STYLE, "focusWidth: 0; arc: 0"
			+ "; borderColor: " + outline
			+ "; focusedBorderColor: " + hex(Theme.color("focus"))
			+ "; disabledBorderColor: " + hex(ColorScheme.DARK_GRAY_HOVER_COLOR));
	}

	static void placeholder(JTextField field, String text)
	{
		field.putClientProperty(PLACEHOLDER, text);
	}

	/**
	 * RuneLite's search box: outline it like the other fields, turn the outline orange on focus,
	 * show a placeholder while it is empty, and run {@code onChange} whenever its text changes.
	 */
	static void search(IconTextField search, int width, String placeholder, Runnable onChange)
	{
		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(width, 30));
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onChange.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onChange.run();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onChange.run();
			}
		});
		search.putClientProperty(KIND, "search");
		skinInput(search);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
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
				search.setBorder(BorderFactory.createLineBorder(Theme.color("focus")));
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				search.setBorder(BorderFactory.createLineBorder(Theme.color("wellOutline")));
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
		Sidebar.setSmall(label, ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	static String hex(Color color)
	{
		return String.format("#%06X", color.getRGB() & 0xFFFFFF);
	}

	/**
	 * A card or box in the theme's frame: flat fills it and outlines it in {@code rim}; chamfer cuts its corners,
	 * shades it from the top and rims it dark, adding {@code rim} as a coloured outline when it isn't the card rim.
	 */
	static void paintFrame(Graphics graphics, int w, int h, Color fill, Color rim)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		String frame = Theme.style("frame");
		if (frame.equals("flat") || frame.equals("plank"))
		{
			g.setColor(fill);
			g.fillRect(0, 0, w, h);
			if (frame.equals("plank") && textured())
			{
				g.setPaint(new TexturePaint(woodGrain(), new Rectangle(0, 0, 96, 16)));
				g.fillRect(0, 0, w, h);
				g.setColor(new Color(255, 255, 255, 22));
				g.drawLine(1, 1, w - 2, 1);
			}
			g.setColor(rim);
			g.drawRect(0, 0, w - 1, h - 1);
		}
		else
		{
			Color dark = Theme.color("cardRim");
			g.setColor(dark);
			int k = corner();
			g.fill(Forged.chamfer(0, 0, w, h, k));
			g.setPaint(new GradientPaint(0, 0, fill.brighter(), 0, Math.min(h, 40), fill));
			g.fill(Forged.chamfer(1, 1, w - 2, h - 2, Math.max(0, k - 1)));
			g.setColor(new Color(255, 255, 255, 18));
			g.drawLine(3, 1, w - 4, 1);
			if (!rim.equals(dark))
			{
				g.setColor(rim);
				g.draw(Forged.chamfer(0, 0, w - 1, h - 1, k));
			}
		}
		g.dispose();
	}

	private static BufferedImage woodGrain;

	/** A tile of wood grain, dark streaks with paler rings, laid over any wood colour. */
	static BufferedImage woodGrain()
	{
		if (woodGrain == null)
		{
			woodGrain = new BufferedImage(96, 16, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 16; y++)
			{
				for (int x = 0; x < 96; x++)
				{
					double v = Math.sin((x + 5 * Math.sin(y * .7 + x * .065)) * .065 * Math.PI * 2 / 6 + y * 1.3);
					if (v > .55)
					{
						woodGrain.setRGB(x, y, (int) ((v - .55) / .45 * 70) << 24);
					}
					else if (v < -.88)
					{
						woodGrain.setRGB(x, y, 0x10FFFFFF);
					}
				}
			}
		}
		return woodGrain;
	}

	/** The theme's corner cut in pixels, for painted buttons and chamfered cards. */
	static int corner()
	{
		return Integer.parseInt(Theme.style("corner"));
	}

	/** Whether the theme keeps grain, stars and wood textures. */
	static boolean textured()
	{
		return Theme.style("texture").equals("on");
	}

	/** A box drawn by {@link #paintFrame} in the theme's box fill with a coloured rim, padded 6px inside it. */
	static JPanel framed(LayoutManager layout, int top, int bottom, Color rim)
	{
		JPanel box = new JPanel(layout)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				paintFrame(g, getWidth(), getHeight(), Theme.color("boxFill"), rim);
			}
		};
		box.setOpaque(false);
		box.setBorder(new javax.swing.border.EmptyBorder(top + 1, 7, bottom + 1, 7));
		return box;
	}

	/** The edge painter's outline, with a strip down the left: dim at rest, full colour on hover or press. */
	private static final class Edge implements Border
	{
		private final Color accent;
		private final Color dim;

		Edge(Color accent)
		{
			this.accent = accent;
			dim = new Color(accent.getRed() / 2, accent.getGreen() / 2, accent.getBlue() / 2);
		}

		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int w, int h)
		{
			ButtonModel model = ((AbstractButton) c).getModel();
			boolean lit = c.isEnabled() && (model.isRollover() || model.isPressed());
			g.setColor(lit ? Theme.color("buttonHoverBorder") : Theme.color("cardRim"));
			g.drawRect(x, y, w - 1, h - 1);
			g.setColor(!c.isEnabled() ? OUTLINE : lit ? accent : dim);
			g.fillRect(x, y, 2, h);
		}

		@Override
		public Insets getBorderInsets(Component c)
		{
			Insets m = ((AbstractButton) c).getMargin();
			return new Insets(m.top + 1, m.left + 3, m.bottom + 1, m.right + 1);
		}

		@Override
		public boolean isBorderOpaque()
		{
			return false;
		}
	}

	/** The edge painter's darker lip under the main button, gone while it is pressed so it looks pushed in. */
	private static final class Lip implements Border
	{
		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int w, int h)
		{
			if (c.isEnabled() && !((AbstractButton) c).getModel().isPressed())
			{
				g.setColor(Theme.color("primaryLip"));
				g.fillRect(x, y + h - 2, w, 2);
			}
		}

		/** FlatLaf's minimum height needs its own border, so this pads the button to that height itself. */
		@Override
		public Insets getBorderInsets(Component c)
		{
			int spare = Math.max(6, PRIMARY_HEIGHT - c.getFontMetrics(c.getFont()).getHeight());
			return new Insets(spare / 2 - 1, 14, spare - spare / 2 + 1, 14);
		}

		@Override
		public boolean isBorderOpaque()
		{
			return false;
		}
	}

	/**
	 * Paints {@link #primary}, {@link #secondary} and {@link #wiki} buttons: chamfered corners (notched ends for the
	 * ribbon), a gradient body and text with the game's drop shadow. Hover and press fade in and out over a few
	 * frames; the timer runs only while one moves.
	 */
	static final class Forged extends BasicButtonUI
	{
		private static final int FRAME_MS = 15;
		private final boolean gold;
		private final boolean ribbon;
		private final String painter;
		/** Molten and arcane keep moving while hovered; this is their clock in milliseconds. */
		private final boolean live;
		private float phase;
		private final Timer timer = new Timer(FRAME_MS, e -> step());
		private final ChangeListener onChange = e -> start();
		private AbstractButton button;
		private float hover;
		private float press;
		/** How far the light has swept across the gold bar, from 0 to 1; negative while it isn't sweeping. */
		private float sweep = -1;
		private boolean rollover;
		private BufferedImage grain;

		boolean paints(boolean gold, String painter)
		{
			return this.gold == gold && this.painter.equals(painter);
		}

		Forged(boolean gold, String painter)
		{
			this.gold = gold;
			this.painter = painter;
			ribbon = painter.equals("ribbon");
			live = painter.equals("molten") || painter.equals("arcane");
		}

		private Polygon outline(int x, int y, int w, int h, int k)
		{
			return ribbon ? new Polygon(new int[]{x, x + w, x + w - 4, x + w, x, x + 4},
				new int[]{y, y, y + h / 2, y + h, y + h, y + h / 2}, 6) : chamfer(x, y, w, h, k);
		}

		@Override
		protected void installDefaults(AbstractButton b)
		{
			super.installDefaults(b);
			button = b;
			// As UI defaults, not client settings, so the next theme's look and feel sets its own
			LookAndFeel.installProperty(b, "opaque", false);
			LookAndFeel.installProperty(b, "rolloverEnabled", true);
			b.setBorder(new Pad());
		}

		@Override
		protected void installListeners(AbstractButton b)
		{
			super.installListeners(b);
			b.addChangeListener(onChange);
		}

		@Override
		protected void uninstallListeners(AbstractButton b)
		{
			super.uninstallListeners(b);
			b.removeChangeListener(onChange);
			timer.stop();
		}

		@Override
		public Dimension getPreferredSize(JComponent c)
		{
			Dimension size = super.getPreferredSize(c);
			if (gold && size != null)
			{
				size.height = Math.max(size.height, PRIMARY_HEIGHT);
			}
			return size;
		}

		private void start()
		{
			boolean now = button.isEnabled() && button.getModel().isRollover();
			if ((gold && painter.equals("forged") || painter.equals("crystal")) && now && !rollover)
			{
				sweep = 0;
			}
			rollover = now;
			timer.start();
		}

		private void step()
		{
			// Hidden or removed under the mouse, a button never hears it leave, so it would animate unseen forever
			if (!button.isShowing())
			{
				hover = press = 0;
				sweep = -1;
				rollover = false;
				timer.stop();
				return;
			}
			ButtonModel model = button.getModel();
			boolean on = button.isEnabled();
			float hoverTo = on && model.isRollover() ? 1 : 0;
			float pressTo = on && model.isArmed() && model.isPressed() ? 1 : 0;
			boolean still = !Theme.style("motion").equals("on");
			hover = still ? hoverTo : approach(hover, hoverTo, FRAME_MS / 140f);
			press = still ? pressTo : approach(press, pressTo, FRAME_MS / 70f);
			sweep = still ? -1 : sweep;
			if (sweep >= 0)
			{
				sweep = sweep + FRAME_MS / 650f > 1 ? -1 : sweep + FRAME_MS / 650f;
			}
			boolean moving = live && hover > 0 && !still;
			if (moving)
			{
				phase += FRAME_MS;
			}
			if (hover == hoverTo && press == pressTo && sweep < 0 && !moving)
			{
				timer.stop();
			}
			button.repaint();
		}

		private static float approach(float value, float target, float step)
		{
			return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
		}

		@Override
		public void paint(Graphics graphics, JComponent c)
		{
			AbstractButton b = (AbstractButton) c;
			Graphics2D g = (Graphics2D) graphics.create();
			int w = c.getWidth();
			int h = c.getHeight();
			boolean on = b.isEnabled();
			float hv = on ? ease(hover) : 0;
			float pr = on ? ease(press) : 0;
			if (ribbon)
			{
				paintBanner(g, w, h, on, hv, pr);
				g.dispose();
				super.paint(graphics, c);
				return;
			}
			// The theme gives each material's resting shades; hover brightens them and a press turns them over.
			String m = material();
			Color top = Theme.color(shade() + "Top");
			Color bottom = Theme.color(shade() + "Bottom");
			Color rim = Theme.color(m + "Rim");
			g.setColor(scale(rim, .8f));
			int k = corner();
			g.fill(outline(0, 0, w, h, k));
			g.clip(outline(1, 1, w - 2, h - 2, Math.max(0, k - 1)));
			if (!on)
			{
				// Dormant: a greyed version of the material, so a disabled button still reads as one of these
				g.setPaint(new GradientPaint(0, 0, mix(bottom, new Color(0x303030), .7f), 0, h,
					mix(bottom, new Color(0x202020), .8f)));
				g.fillRect(0, 0, w, h);
				if (textured())
				{
					g.drawImage(grain(w, h), 0, 0, null);
				}
				g.setColor(alpha(0xFFFFFF, .06f));
				g.drawLine(2, 1, w - 3, 1);
			}
			else if (!painter.equals("forged") && !ribbon)
			{
				paintMaterial(g, w, h, top, bottom, hv, pr);
			}
			else if (gold)
			{
				Color mid = Theme.color("metal");
				g.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0, .55f, 1}, new Color[]{
					mix(mix(top, scale(top, 1.12f), hv), bottom, pr),
					mix(mix(mid, scale(mid, 1.12f), hv), scale(bottom, .91f), pr),
					mix(mix(bottom, scale(bottom, 1.12f), hv), mix(mid, bottom, .5f), pr)}));
				g.fillRect(0, 0, w, h);
				g.setPaint(new GradientPaint(0, 1, alpha(0xFFFFFF, .55f * (1 - pr)), 0, h * .45f, alpha(0xFFFFFF, 0)));
				g.fillRect(0, 0, w, h / 2);
				if (sweep >= 0)
				{
					float x = -40 + ease(sweep) * (w + 80);
					Color light = mix(top, Color.WHITE, .85f);
					g.setPaint(new LinearGradientPaint(x - 18, 0, x + 18, h * .6f, new float[]{0, .5f, 1},
						new Color[]{alpha(light, 0), alpha(light, .75f), alpha(light, 0)}));
					g.fillRect(0, 0, w, h);
				}
				g.setColor(alpha(mix(top, Color.WHITE, .6f), (1 - pr) * (.7f + .3f * hv)));
				g.drawLine(2, 1, w - 3, 1);
				g.setColor(alpha(scale(bottom, .58f), .9f));
				g.drawLine(2, h - 2, w - 3, h - 2);
			}
			else
			{
				g.setPaint(new GradientPaint(0, 0,
					mix(mix(top, scale(top, ribbon ? 1.25f : 1.12f), hv), scale(bottom, ribbon ? 1.07f : .8f), pr), 0, h,
					mix(mix(bottom, scale(bottom, ribbon ? 1.25f : 1.12f), hv), ribbon ? scale(top, .73f) : scale(bottom, 1.05f), pr)));
				g.fillRect(0, 0, w, h);
				if (textured())
				{
					g.drawImage(grain(w, h), 0, 0, null);
				}
				if (!ribbon && hv > 0)
				{
					Color ember = Theme.color("ember");
					Point2D.Float centre = new Point2D.Float(w / 2f, h + 4);
					g.setPaint(new RadialGradientPaint(centre, Math.max(w * .55f, 1), new float[]{0, .45f, 1},
						new Color[]{alpha(ember, .55f * hv), alpha(ember, .16f * hv), alpha(ember, 0)}));
					g.fillRect(0, 0, w, h);
				}
				if (pr > 0)
				{
					g.setPaint(new GradientPaint(0, 0, alpha(0, .55f * pr), 0, 5, alpha(0, 0)));
					g.fillRect(0, 0, w, 5);
				}
				int inset = ribbon ? 5 : 2;
				g.setColor(ribbon ? alpha(mix(top, Color.WHITE, .7f), .22f * (1 - pr)) : alpha(0xFFFFFF, .1f * (1 - pr)));
				g.drawLine(inset, 1, w - inset - 1, 1);
				g.setColor(ribbon ? alpha(0, .4f) : alpha(mix(Theme.color("ember"), Color.WHITE, .3f), .9f * hv));
				g.drawLine(inset, h - 2, w - inset - 1, h - 2);
			}
			g.setClip(null);
			g.setColor(!on ? scale(mix(bottom, new Color(0x202020), .8f), .75f)
				: b.isFocusPainted() && b.hasFocus() ? Theme.color("focus") : mix(rim, Theme.color(m + "HoverRim"), hv));
			g.draw(outline(0, 0, w - 1, h - 1, k));
			g.dispose();
			super.paint(graphics, c);
		}

		@Override
		protected void paintText(Graphics g, AbstractButton b, Rectangle r, String text)
		{
			int shift = b.isEnabled() && press > .5f ? 1 : 0;
			int x = r.x + shift;
			if (ribbon)
			{
				// The banner's capitals with a dark edge, centred on the band
				text = text.toUpperCase();
				FontMetrics fm = g.getFontMetrics();
				int bx = (b.getWidth() - fm.stringWidth(text)) / 2 + shift;
				int by = BANNER_TOP + (b.getHeight() - BANNER_TOP - 1 - fm.getHeight()) / 2 + fm.getAscent() + shift;
				g.setColor(Color.BLACK);
				for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1, 1}})
				{
					g.drawString(text, bx + d[0], by + d[1]);
				}
				g.setColor(!b.isEnabled() ? new Color(0x8A7C74) : mix(Theme.color("ribbonText"), Color.WHITE, ease(hover)));
				g.drawString(text, bx, by);
				return;
			}
			int y = r.y + g.getFontMetrics().getAscent() + shift;
			Color ink = Theme.color(painter.equals("forged") || ribbon ? (gold ? "metalText" : "stoneText") : shade() + "Text");
			boolean dark = ink.getRed() + ink.getGreen() + ink.getBlue() < 384;
			if (!b.isEnabled())
			{
				g.setColor(Color.BLACK);
				g.drawString(text, x + 1, y + 1);
				g.setColor(dark ? mix(Theme.color(shade() + "Top"), new Color(0x606060), .6f) : mix(ink, new Color(0x303030), .55f));
			}
			else if (dark)
			{
				g.setColor(alpha(mix(Theme.color(shade() + "Top"), Color.WHITE, .75f), .55f));
				g.drawString(text, x, y + 1);
				g.setColor(ink);
			}
			else
			{
				g.setColor(Color.BLACK);
				g.drawString(text, x + 1, y + 1);
				g.setColor(mix(ink, Color.WHITE, ease(hover)));
			}
			g.drawString(text, x, y);
		}

		/** Where the banner's band starts: its tails sit higher, as on the game's Wiki banner. */
		private static final int BANNER_TOP = 3;
		private static final int TAIL = 6;

		/**
		 * The game's Wiki banner: a dark red band with a notched tail at each end, set two pixels higher and tucked
		 * behind it, the whole shape outlined in a pale trim that turns gold on hover.
		 */
		private void paintBanner(Graphics2D g, int w, int h, boolean on, float hv, float pr)
		{
			int tailBottom = h - 4;
			int mid = (1 + tailBottom) / 2;
			Polygon left = new Polygon(new int[]{0, TAIL + 2, TAIL + 2, 0, 3}, new int[]{1, 1, tailBottom, tailBottom, mid}, 5);
			Polygon right = new Polygon(new int[]{w - 1, w - TAIL - 3, w - TAIL - 3, w - 1, w - 4},
				new int[]{1, 1, tailBottom, tailBottom, mid}, 5);
			Rectangle band = new Rectangle(TAIL, BANNER_TOP, w - 2 * TAIL - 1, h - BANNER_TOP - 1);
			Color top = Theme.color("ribbonTop");
			Color bottom = Theme.color("ribbonBottom");
			if (!on)
			{
				top = mix(top, new Color(0x3A3434), .7f);
				bottom = mix(bottom, new Color(0x262222), .7f);
			}
			float lift = 1 + .25f * hv - .2f * pr;
			// The tails, a shade darker, then the band over them
			g.setPaint(new GradientPaint(0, 1, scale(top, .7f * lift), 0, tailBottom, scale(bottom, .7f * lift)));
			g.fill(left);
			g.fill(right);
			g.setColor(alpha(0, .55f));
			g.drawLine(TAIL + 1, BANNER_TOP, TAIL + 1, tailBottom);
			g.drawLine(w - TAIL - 2, BANNER_TOP, w - TAIL - 2, tailBottom);
			g.setPaint(new GradientPaint(0, BANNER_TOP, scale(top, lift), 0, h, scale(bottom, lift)));
			g.fill(band);
			g.setColor(alpha(Color.WHITE.getRGB(), .1f * (1 - pr)));
			g.drawLine(band.x + 1, band.y + 1, band.x + band.width - 2, band.y + 1);
			java.awt.geom.Area shape = new java.awt.geom.Area(left);
			shape.add(new java.awt.geom.Area(right));
			shape.add(new java.awt.geom.Area(band));
			Color trim = Theme.color("ribbonTrim");
			g.setColor(!on ? new Color(0x5A5450) : mix(trim, Theme.color("ribbonHoverRim"), hv));
			g.draw(shape);
		}

		/** The material whose rim colours this painter uses: metal or stone for forged, else the painter itself. */
		private String material()
		{
			return painter.equals("forged") ? (gold ? "metal" : "stone") : painter;
		}

		/** The key prefix for this button's shades: new painters give the main button its own as {@code <painter>Main}. */
		private String shade()
		{
			return painter.equals("forged") || ribbon || !gold ? material() : painter + "Main";
		}

		/** Crystal, wood, molten and arcane bodies, over a top-to-bottom gradient of the material. */
		private void paintMaterial(Graphics2D g, int w, int h, Color top, Color bottom, float hv, float pr)
		{
			float lift = 1 + .15f * hv - .2f * pr;
			g.setPaint(new GradientPaint(0, 0, scale(top, lift), 0, h, scale(bottom, lift)));
			g.fillRect(0, 0, w, h);
			if (painter.equals("crystal"))
			{
				// Facets: a lit wedge on the left, a pale band, a shaded wedge on the right
				g.setColor(alpha(0xFFFFFF, .14f + .08f * hv));
				g.fillPolygon(new int[]{0, w * 35 / 100, w * 15 / 100, 0}, new int[]{0, 0, h, h}, 4);
				g.setColor(alpha(0xFFFFFF, .08f));
				g.fillPolygon(new int[]{w * 55 / 100, w * 68 / 100, w * 50 / 100, w * 37 / 100}, new int[]{0, 0, h, h}, 4);
				g.setColor(alpha(0, .14f));
				g.fillPolygon(new int[]{w * 80 / 100, w, w, w * 66 / 100}, new int[]{0, 0, h, h}, 4);
				g.setColor(alpha(0xFFFFFF, .5f * (1 - pr)));
				g.drawLine(2, 1, w - 3, 1);
				if (sweep >= 0)
				{
					// A glint running along the facets
					int x = (int) (ease(sweep) * (w + 20)) - 10;
					int y = h / 3;
					float a = 1 - Math.abs(2 * sweep - 1);
					g.setPaint(new RadialGradientPaint(new Point2D.Float(x, y), 7, new float[]{0, 1},
						new Color[]{alpha(0xFFFFFF, .7f * a), alpha(0xFFFFFF, 0)}));
					g.fillRect(x - 7, y - 7, 14, 14);
					g.setColor(alpha(0xFFFFFF, a));
					g.drawLine(x - 4, y, x + 4, y);
					g.drawLine(x, y - 3, x, y + 3);
				}
			}
			else if (painter.equals("wood"))
			{
				if (textured())
				{
					g.setPaint(new TexturePaint(woodGrain(), new Rectangle(0, 0, 96, 16)));
					g.fillRect(0, 0, w, h);
				}
				g.setColor(alpha(0xFFFFFF, .12f * (1 - pr)));
				g.drawLine(2, 1, w - 3, 1);
				if (h >= 18)
				{
					Color stud = Theme.color("woodStud");
					for (int[] at : new int[][]{{3, 3}, {w - 6, 3}, {3, h - 6}, {w - 6, h - 6}})
					{
						g.setColor(scale(stud, .55f));
						g.fillOval(at[0], at[1], 3, 3);
						g.setColor(mix(stud, Color.WHITE, .35f * hv));
						g.fillRect(at[0], at[1], 2, 2);
					}
				}
			}
			else if (painter.equals("molten"))
			{
				Color glow = Theme.color("moltenGlow");
				if (gold)
				{
					// Lava: bright bands flowing across, with dark crust along the cracks
					for (int i = 0; i < 3; i++)
					{
						float x = ((phase / 18 + i * w / 3f) % (w + 30)) - 15;
						g.setPaint(new GradientPaint(x - 14, 0, alpha(0xFFE08A, 0), x, h, alpha(0xFFE08A, .28f)));
						g.fillPolygon(new int[]{(int) x - 14, (int) x + 6, (int) x + 16, (int) x - 4}, new int[]{0, 0, h, h}, 4);
					}
				}
				g.setStroke(new BasicStroke(1f));
				for (Shape vein : veins(w, h))
				{
					if (!gold && hv > 0)
					{
						g.setStroke(new BasicStroke(3f));
						g.setColor(alpha(glow, .18f * hv));
						g.draw(vein);
						g.setStroke(new BasicStroke(1f));
					}
					g.setColor(gold ? alpha(0x2A0A00, .22f) : alpha(glow, .3f + .6f * hv));
					g.draw(vein);
				}
				// Embers rising while hovered
				for (int i = 0; hv > 0 && i < 7; i++)
				{
					float t = ((phase / 22 + i * 53) % (h + 8)) / (h + 8);
					int x = (int) ((i * 73 % 97) / 97f * (w - 4)) + 2 + (int) (2 * Math.sin(phase / 300 + i));
					g.setColor(alpha(Theme.color("moltenEmber"), hv * (1 - t)));
					g.fillRect(x, (int) (h - t * (h + 4)), i % 3 == 0 ? 2 : 1, i % 3 == 0 ? 2 : 1);
				}
				g.setColor(alpha(0xFFFFFF, .08f * (1 - pr)));
				g.drawLine(2, 1, w - 3, 1);
			}
			else
			{
				// Arcane: faint stars; hovered, the outline pulses and a spark orbits the rim
				if (textured())
				{
					g.drawImage(stars(w, h), 0, 0, null);
				}
				g.setColor(alpha(0xFFFFFF, .12f * (1 - pr)));
				g.drawLine(2, 1, w - 3, 1);
				if (hv > 0)
				{
					Color glow = Theme.color("arcaneGlow");
					g.setColor(alpha(glow, hv * (.45f + .25f * (float) Math.sin(phase / 180))));
					g.draw(chamfer(1, 1, w - 3, h - 3, 2));
					float around = (phase / 1400 % 1) * 2 * (w + h);
					float x = around < w ? around : around < w + h ? w - 2 : around < 2 * w + h ? 2 * w + h - around : 1;
					float y = around < w ? 1 : around < w + h ? around - w : around < 2 * w + h ? h - 2 : 2 * (w + h) - around;
					g.setPaint(new RadialGradientPaint(new Point2D.Float(x, y), 6, new float[]{0, 1},
						new Color[]{alpha(glow, .9f * hv), alpha(glow, 0)}));
					g.fillRect((int) x - 6, (int) y - 6, 12, 12);
					g.setColor(alpha(0xFFFFFF, hv));
					g.fillRect((int) x - 1, (int) y - 1, 2, 2);
				}
			}
		}

		private java.util.List<Shape> veinCache;
		private int veinSize;

		/** Molten cracks: a few random walks across the button, the same for every paint at one size. */
		private java.util.List<Shape> veins(int w, int h)
		{
			if (veinCache == null || veinSize != w * 1000 + h)
			{
				veinSize = w * 1000 + h;
				veinCache = new java.util.ArrayList<>();
				java.util.Random random = new java.util.Random(veinSize);
				for (int i = 0; i < Math.max(2, w / 28); i++)
				{
					java.awt.geom.Path2D.Float vein = new java.awt.geom.Path2D.Float();
					float x = random.nextFloat() * w;
					float y = random.nextBoolean() ? 1 : h - 2;
					vein.moveTo(x, y);
					for (int step = 0; step < 5; step++)
					{
						x += random.nextFloat() * 12 - 6;
						y += (y < h / 2f ? 1 : -1) * (random.nextFloat() * h / 4f);
						vein.lineTo(x, y);
					}
					veinCache.add(vein);
				}
			}
			return veinCache;
		}

		private BufferedImage stars;

		/** A sparse field of faint stars, made once per button size. */
		private BufferedImage stars(int w, int h)
		{
			if (stars == null || stars.getWidth() != w || stars.getHeight() != h)
			{
				stars = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				for (int y = 2; y < h - 2; y++)
				{
					for (int x = 2; x < w - 2; x++)
					{
						int n = (x * 73856093 ^ y * 83492791) * 0x5BD1E995;
						if ((n ^ n >>> 15) >>> 24 > 251)
						{
							stars.setRGB(x, y, 0x70FFFFFF);
						}
					}
				}
			}
			return stars;
		}

		/** Fixed speckles of stone grain, made once per button size. */
		private BufferedImage grain(int w, int h)
		{
			if (grain == null || grain.getWidth() != w || grain.getHeight() != h)
			{
				grain = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				for (int y = 1; y < h - 1; y++)
				{
					for (int x = 1; x < w - 1; x++)
					{
						int n = (x * 73856093 ^ y * 19349663) * 0x5BD1E995;
						int v = (n ^ n >>> 13) >>> 24;
						if (v > 236 || v < 18)
						{
							grain.setRGB(x, y, v > 236 ? 0x0DFFFFFF : 0x38000000);
						}
					}
				}
			}
			return grain;
		}

		private static float ease(float t)
		{
			return t * t * (3 - 2 * t);
		}

		private static Color mix(Color a, Color b, float t)
		{
			return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
				(int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t), (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
		}

		private static Color alpha(int rgb, float a)
		{
			return new Color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, (int) (Math.max(0, Math.min(1, a)) * 255));
		}

		private static Color alpha(Color c, float a)
		{
			return alpha(c.getRGB(), a);
		}

		/** Each channel times {@code f}, capped at 255. */
		private static Color scale(Color c, float f)
		{
			return new Color(Math.min(255, (int) (c.getRed() * f)), Math.min(255, (int) (c.getGreen() * f)),
				Math.min(255, (int) (c.getBlue() * f)));
		}

		static Polygon chamfer(int x, int y, int w, int h, int k)
		{
			return new Polygon(new int[]{x + k, x + w - k, x + w, x + w, x + w - k, x + k, x, x},
				new int[]{y, y, y + k, y + h - k, y + h, y + h, y + h - k, y + k}, 8);
		}
	}

	/** Pads a forged button by its margin plus the 2 px rim, so callers keep sizing buttons with setMargin. */
	private static final class Pad implements Border
	{
		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int w, int h)
		{
		}

		@Override
		public Insets getBorderInsets(Component c)
		{
			Insets m = ((AbstractButton) c).getMargin();
			return m == null ? new Insets(3, 3, 3, 3) : new Insets(m.top + 2, m.left + 2, m.bottom + 2, m.right + 2);
		}

		@Override
		public boolean isBorderOpaque()
		{
			return false;
		}
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
		/** A theme colour read at each paint instead, so the icon follows a theme change. */
		private final String themeKey;

		Chevron(int direction, Color color)
		{
			this.direction = direction;
			this.color = color;
			themeKey = null;
		}

		Chevron(int direction, String themeKey)
		{
			this.direction = direction;
			color = null;
			this.themeKey = themeKey;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.setColor(themeKey != null ? Theme.color(themeKey) : color == null ? c.getForeground() : color);
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
