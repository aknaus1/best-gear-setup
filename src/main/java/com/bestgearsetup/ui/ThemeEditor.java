package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.Options;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ColorJButton;

/**
 * The Custom theme's editor in Settings: start from any theme, choose painters and colours, and export or import
 * the theme as text through the clipboard. Until Custom is first saved the editor shows the panel's theme, so the
 * first change starts Custom from it; after that it shows and edits Custom. A preview stands in for the parts
 * Settings hides, Undo steps back through the session's changes, and text that is hard to read is flagged.
 */
class ThemeEditor extends JPanel
{
	/** The colours worth listing for each painter, each option's label and the text to check; see theme-editor.json. */
	private static final JsonObject GROUPS = Theme.EDITOR;
	private static final Random RANDOM = new Random();
	private static final int UNDO_STEPS = 30;
	/** Text with less contrast than this against the middle of its fill is flagged; no built-in theme is. */
	private static final double READABLE = 3;

	private final BestGearSetupPlugin plugin;
	private final Options config;
	private final JComboBox<Theme> base = new JComboBox<>();
	private final Map<String, JComboBox<String>> painters = new LinkedHashMap<>();
	private final JCheckBox everyUse = new JCheckBox("Change every use of a colour");
	private final JPanel colours = new JPanel();
	private final Map<String, ColorJButton> swatches = new LinkedHashMap<>();
	/** The colour groups shown open; all start closed. */
	private final Set<String> open = new HashSet<>();
	private final JLabel contrast = new JLabel();
	private final JLabel status = new JLabel(" ");
	private final JButton undo = new JButton("Undo");
	/** The theme and Custom as they were before each change, newest first. */
	private final Deque<String[]> history = new ArrayDeque<>();
	/** The theme and Custom the editor last saved; any others, such as another profile's, start the history again. */
	private String written;
	/** The panel's theme at the last refresh. */
	private Theme shownTheme;
	private String shownKeys = "";
	private boolean syncing;

	ThemeEditor(BestGearSetupPlugin plugin, Options config)
	{
		this.plugin = plugin;
		this.config = config;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		JLabel intro = Controls.caption("<html><div style='width:170px'>Edits the Custom theme. Export copies it as "
			+ "text to share; Import reads one from the clipboard.</div></html>");
		add(spaced(intro));

		for (Theme theme : Theme.values())
		{
			if (theme != Theme.CUSTOM)
			{
				base.addItem(theme);
			}
		}
		Controls.field(base);
		JButton start = new JButton("Start from");
		Controls.secondary(start);
		start.setToolTipText("Replace Custom with a copy of this theme");
		start.addActionListener(e -> save(Theme.export((Theme) base.getSelectedItem()), "Started from " + base.getSelectedItem()));
		JPanel startRow = new JPanel(new BorderLayout(4, 0));
		startRow.setOpaque(false);
		startRow.add(base, BorderLayout.CENTER);
		startRow.add(start, BorderLayout.EAST);
		add(spaced(startRow));

		for (Map.Entry<String, JsonElement> option : Theme.CHOICES.entrySet())
		{
			String key = option.getKey();
			List<String> options = new ArrayList<>();
			option.getValue().getAsJsonArray().forEach(value -> options.add(value.getAsString()));
			JComboBox<String> choice = new JComboBox<>(options.toArray(new String[0]));
			Controls.field(choice);
			choice.setRenderer(new DefaultListCellRenderer()
			{
				@Override
				public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
					boolean focused)
				{
					return super.getListCellRendererComponent(list, label(String.valueOf(value)), index, selected, focused);
				}
			});
			choice.addActionListener(e ->
			{
				if (!syncing)
				{
					set(Collections.singletonList(key), (String) choice.getSelectedItem());
				}
			});
			painters.put(key, choice);
			JPanel row = new JPanel(new BorderLayout(4, 2));
			row.setOpaque(false);
			row.add(Controls.caption(GROUPS.getAsJsonObject("names").get(key).getAsString()), BorderLayout.NORTH);
			row.add(choice, BorderLayout.CENTER);
			add(spaced(row));
		}

		add(spaced(Controls.caption("Preview")));
		add(spaced(samples()));
		Controls.checkBox(everyUse);
		everyUse.setOpaque(false);
		everyUse.setForeground(Color.WHITE);
		everyUse.setFont(FontManager.getRunescapeSmallFont());
		everyUse.setFocusable(false);
		everyUse.setToolTipText("Also recolour every part that has the picked colour");
		add(spaced(everyUse));
		colours.setLayout(new BoxLayout(colours, BoxLayout.Y_AXIS));
		colours.setOpaque(false);
		add(spaced(colours));
		add(spaced(contrast));

		JPanel actions = new JPanel(new GridLayout(2, 2, 4, 4));
		actions.setOpaque(false);
		JButton export = new JButton("Export");
		JButton paste = new JButton("Import");
		JButton reset = new JButton("Reset");
		JButton surprise = new JButton("Surprise me");
		export.setToolTipText("Copy the Custom theme to the clipboard");
		paste.setToolTipText("Use a theme copied to the clipboard");
		reset.setToolTipText("Make Custom the same as Classic");
		surprise.setToolTipText("A random theme in new colours");
		surprise.addActionListener(e -> surprise());
		for (JButton action : new JButton[]{export, paste, surprise, reset})
		{
			Controls.secondary(action);
			action.setMargin(new Insets(3, 0, 3, 0));
			actions.add(action);
		}
		export.addActionListener(e -> exportTheme());
		paste.addActionListener(e -> importTheme());
		reset.addActionListener(e -> save("", "Custom is Classic again"));
		add(spaced(actions));
		JPanel statusRow = new JPanel(new BorderLayout(4, 0));
		statusRow.setOpaque(false);
		Sidebar.setSmall(status, ColorScheme.LIGHT_GRAY_COLOR);
		statusRow.add(status, BorderLayout.CENTER);
		Controls.ghost(undo);
		undo.setToolTipText("Go back to the theme before the last change");
		undo.addActionListener(e -> undo());
		statusRow.add(undo, BorderLayout.EAST);
		add(spaced(statusRow));
		refresh();
	}

	/** Shows the theme being edited, rebuilding the colour list only when its painters or open groups change. */
	void refresh()
	{
		syncing = true;
		try
		{
			Theme from = source();
			if (config.theme() != shownTheme)
			{
				// Start from follows the panel's theme, but a theme picked in it stays picked while the panel is on Custom
				shownTheme = config.theme();
				if (shownTheme != Theme.CUSTOM)
				{
					base.setSelectedItem(shownTheme);
				}
				if (from == Theme.CUSTOM && shownTheme != Theme.CUSTOM)
				{
					say("The panel shows " + shownTheme + "; a change here switches it to Custom");
				}
			}
			for (Map.Entry<String, JComboBox<String>> painter : painters.entrySet())
			{
				painter.getValue().setSelectedItem(from.get(painter.getKey()));
			}
			// Each colour listed once, under the first group that uses it
			Map<String, List<String>> keys = new LinkedHashMap<>();
			Set<String> listed = new HashSet<>();
			for (String group : new String[]{"panel", "cards", "results", "details", from.get("primary"),
				from.get("secondary"), from.get("wiki")})
			{
				GROUPS.getAsJsonArray(group).forEach(key ->
				{
					if (listed.add(key.getAsString()))
					{
						keys.computeIfAbsent(group, g -> new ArrayList<>()).add(key.getAsString());
					}
				});
			}
			if (!(keys + "" + open).equals(shownKeys))
			{
				shownKeys = keys + "" + open;
				rebuildColours(keys);
			}
			swatches.forEach((key, swatch) -> show(swatch, from.get(key)));
			String hard = hardToRead(from, keys.keySet());
			Sidebar.setSmall(contrast, Theme.color("warning"));
			contrast.setText("<html><div style='width:170px'>Hard to read: " + hard + "</div></html>");
			contrast.setVisible(hard != null);
			if (written != null && !written.equals(state()))
			{
				history.clear();
				written = null;
			}
			undo.setVisible(!history.isEmpty());
		}
		finally
		{
			syncing = false;
		}
	}

	/** The panel's theme and Custom, to tell the editor's own changes from another profile's. */
	private String state()
	{
		return config.theme() + config.customTheme();
	}

	/** Custom, or the panel's theme until Custom is first saved: what the editor shows and changes start from. */
	private Theme source()
	{
		return config.theme() != Theme.CUSTOM && config.customTheme().isEmpty() ? config.theme() : Theme.CUSTOM;
	}

	private void rebuildColours(Map<String, List<String>> groups)
	{
		colours.removeAll();
		swatches.clear();
		JsonObject names = GROUPS.getAsJsonObject("names");
		groups.forEach((group, keys) ->
		{
			boolean shown = open.contains(group);
			JButton heading = new JButton(names.has(group) ? names.get(group).getAsString() : label(group) + " painter");
			Controls.ghost(heading);
			heading.setIcon(new Controls.Chevron(shown ? Controls.Chevron.DOWN : Controls.Chevron.RIGHT, (Color) null));
			heading.setIconTextGap(6);
			heading.setToolTipText((shown ? "Hide" : "Show") + " these colours");
			heading.addActionListener(e ->
			{
				if (!open.remove(group))
				{
					open.add(group);
				}
				refresh();
			});
			colours.add(spaced(heading));
			if (shown)
			{
				keys.forEach(this::addColour);
			}
		});
		Controls.restyle(colours);
		colours.revalidate();
	}

	private void addColour(String key)
	{
		String label = label(key);
		ColorJButton swatch = new ColorJButton("", Color.BLACK);
		swatch.setFocusable(false);
		swatch.setFont(FontManager.getRunescapeSmallFont());
		swatch.setPreferredSize(new Dimension(84, 18));
		swatch.setToolTipText("Pick the " + label.toLowerCase(Locale.ROOT));
		swatch.addActionListener(e ->
		{
			List<String> keys = everyUse.isSelected() ? uses(key) : Collections.singletonList(key);
			plugin.pickColor(swatch, swatch.getColor(), label, color -> preview(swatch, keys, color),
				color -> picked(keys, hex(color)));
		});
		swatches.put(key, swatch);
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		JLabel name = new JLabel(label);
		Sidebar.setSmall(name, Color.WHITE);
		row.add(name, BorderLayout.CENTER);
		row.add(swatch, BorderLayout.EAST);
		colours.add(spaced(row));
	}

	/** Every key with the same colour as {@code key}, found as the picker opens so the picked colour can't add more. */
	private List<String> uses(String key)
	{
		Theme from = source();
		List<String> keys = new ArrayList<>();
		for (String other : Theme.CLASSIC.data().keySet())
		{
			if (from.get(other).equalsIgnoreCase(from.get(key)))
			{
				keys.add(other);
			}
		}
		return keys;
	}

	/** Shows a colour being picked on the panel in Custom before it is saved, without rebuilding the results. */
	private void preview(ColorJButton swatch, List<String> keys, Color color)
	{
		swatch.setColor(color);
		Theme.setCustom(with(keys, hex(color)).toString());
		Theme.use(Theme.CUSTOM);
		repaintPanel();
	}

	/** Saves the colour the picker closed on; closing it on the colour it opened with changes nothing. */
	private void picked(List<String> keys, String value)
	{
		// Changes are measured from the saved theme, which a preview replaced
		Theme.setCustom(config.customTheme());
		if (keys.stream().anyMatch(key -> !source().get(key).equalsIgnoreCase(value)))
		{
			set(keys, value);
			return;
		}
		Theme.use(config.theme());
		repaintPanel();
		refresh();
	}

	private void repaintPanel()
	{
		Container sidebar = SwingUtilities.getAncestorOfClass(Sidebar.class, this);
		Container root = sidebar == null ? this : sidebar;
		Controls.restyle(root);
		root.repaint();
	}

	private void set(List<String> keys, String value)
	{
		save(with(keys, value).toString(), keys.size() > 1 ? "Changed " + keys.size() + " colours" : null);
	}

	/** The theme being edited with {@code keys} set to {@code value}, as Custom. */
	private JsonObject with(List<String> keys, String value)
	{
		Theme from = source();
		JsonObject next = new JsonObject();
		for (Map.Entry<String, JsonElement> entry : Theme.CLASSIC.data().entrySet())
		{
			String key = entry.getKey();
			String current = keys.contains(key) ? value : from.get(key);
			if (!key.equals("_comment") && !current.equals(entry.getValue().getAsString()))
			{
				next.addProperty(key, current);
			}
		}
		return next;
	}

	/** Saves Custom and shows it, keeping what it replaces for Undo; the configuration change repaints the panel. */
	private void save(String json, String message)
	{
		if (!json.equals(config.customTheme()) || config.theme() != Theme.CUSTOM)
		{
			history.push(new String[]{config.theme().name(), config.customTheme()});
			if (history.size() > UNDO_STEPS)
			{
				history.removeLast();
			}
		}
		Theme.setCustom(json);
		plugin.setConfig(Options.CUSTOM_THEME_KEY, json);
		if (config.theme() != Theme.CUSTOM)
		{
			plugin.setConfig(Options.THEME_KEY, Theme.CUSTOM);
		}
		written = state();
		say(message);
		refresh();
	}

	private void undo()
	{
		String[] last = history.pop();
		Theme.setCustom(last[1]);
		plugin.setConfig(Options.CUSTOM_THEME_KEY, last[1]);
		plugin.setConfig(Options.THEME_KEY, Theme.valueOf(last[0]));
		written = state();
		refresh();
		say("Undone");
	}

	/** What Export copies: the theme the editor shows. */
	String exported()
	{
		return Theme.export(source());
	}

	private void exportTheme()
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(exported()), null);
		say("Copied " + source());
	}

	private void importTheme()
	{
		String text;
		try
		{
			text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
		}
		catch (Exception e)
		{
			say("The clipboard has no text to import");
			return;
		}
		String problem = Theme.check(text);
		if (problem != null)
		{
			say(problem);
			return;
		}
		Theme.setCustom(text);
		save(Theme.export(Theme.CUSTOM), "Imported into Custom");
	}

	/**
	 * Custom as a random built-in theme with its colours turned around the colour wheel. Result colours and the Wiki
	 * banner keep theirs, so blue still means DPS, green owned and the banner the game's.
	 */
	private void surprise()
	{
		Theme from = Theme.values()[RANDOM.nextInt(Theme.values().length - 1)];
		float turn = .15f + RANDOM.nextFloat() * .7f;
		Set<String> kept = new HashSet<>();
		for (String group : new String[]{"results", "ribbon"})
		{
			GROUPS.getAsJsonArray(group).forEach(key -> kept.add(key.getAsString()));
		}
		JsonObject next = new JsonObject();
		for (Map.Entry<String, JsonElement> entry : Theme.CLASSIC.data().entrySet())
		{
			String key = entry.getKey();
			String value = from.get(key);
			if (value.startsWith("#") && !kept.contains(key))
			{
				float[] hsb = Color.RGBtoHSB(Integer.parseInt(value.substring(1, 3), 16),
					Integer.parseInt(value.substring(3, 5), 16), Integer.parseInt(value.substring(5), 16), null);
				value = hex(Color.getHSBColor((hsb[0] + turn) % 1, hsb[1], hsb[2]));
			}
			if (!key.equals("_comment") && !value.equals(entry.getValue().getAsString()))
			{
				next.addProperty(key, value);
			}
		}
		save(next.toString(), "Surprise: " + from + ", recoloured");
	}

	/**
	 * The least readable text in the listed groups, as "Metal text (2.1:1)", or null when all of it is readable. Text
	 * is measured against the middle of its fill, which for painted buttons is a gradient between two colours.
	 */
	static String hardToRead(Theme theme, Collection<String> groups)
	{
		String worst = null;
		double least = READABLE;
		JsonObject checks = GROUPS.getAsJsonObject("contrast");
		for (String group : groups)
		{
			for (JsonElement check : checks.has(group) ? checks.getAsJsonArray(group) : new JsonArray())
			{
				JsonArray keys = check.getAsJsonArray();
				int[] fill = new int[3];
				for (int i = 1; i < keys.size(); i++)
				{
					Color part = Color.decode(theme.get(keys.get(i).getAsString()));
					fill[0] += part.getRed();
					fill[1] += part.getGreen();
					fill[2] += part.getBlue();
				}
				int parts = keys.size() - 1;
				double text = luminance(Color.decode(theme.get(keys.get(0).getAsString())));
				double back = luminance(new Color(fill[0] / parts, fill[1] / parts, fill[2] / parts));
				double ratio = Math.max(text, back) / Math.min(text, back);
				if (ratio < least)
				{
					least = ratio;
					worst = label(keys.get(0).getAsString()) + String.format(" (%.1f:1)", ratio);
				}
			}
		}
		return worst;
	}

	/** Relative luminance plus the 0.05 flare of the WCAG contrast ratio. */
	private static double luminance(Color color)
	{
		float[] rgb = color.getRGBColorComponents(null);
		double[] weights = {.2126, .7152, .0722};
		double sum = .05;
		for (int i = 0; i < 3; i++)
		{
			sum += weights[i] * (rgb[i] <= .03928 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4));
		}
		return sum;
	}

	/** The main, ordinary and Wiki buttons and the result boxes, which Settings hides, in the panel's theme. */
	private static JPanel samples()
	{
		JPanel samples = new JPanel(new BorderLayout(0, 4));
		samples.setOpaque(false);
		JButton main = new JButton("Main button");
		Controls.primary(main);
		samples.add(main, BorderLayout.NORTH);
		JPanel boxes = new JPanel(new GridLayout(1, 0, 3, 0));
		boxes.setOpaque(false);
		boxes.add(box("DPS", "dpsEdge", "text"));
		boxes.add(box("Spec", "specEdge", "text"));
		boxes.add(box("Owned", "costEdge", "owned"));
		boxes.add(box("Style", "styleEdge", "text"));
		samples.add(boxes, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new BorderLayout(4, 0));
		buttons.setOpaque(false);
		JButton button = new JButton("Button");
		Controls.secondary(button);
		buttons.add(button, BorderLayout.CENTER);
		JButton wiki = new JButton("Wiki");
		Controls.wiki(wiki);
		buttons.add(wiki, BorderLayout.EAST);
		samples.add(buttons, BorderLayout.SOUTH);
		return samples;
	}

	/** A result box in the theme's frame and box fill, with its edge and text colours read at each paint. */
	private static JLabel box(String text, String edge, String colour)
	{
		JLabel box = new JLabel(text, SwingConstants.CENTER)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Controls.paintFrame(g, getWidth(), getHeight(), Theme.color("boxFill"), Theme.color(edge));
				super.paintComponent(g);
			}

			@Override
			public Color getForeground()
			{
				return Theme.color(colour);
			}
		};
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setBorder(new EmptyBorder(4, 0, 4, 0));
		return box;
	}

	/** "cardEdge" as "Card edge". */
	static String label(String key)
	{
		String words = key.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	/** Shows a message under the buttons, wrapped beside Undo. */
	private void say(String message)
	{
		status.setText(message == null ? " " : "<html><div style='width:130px'>" + message + "</div></html>");
	}

	private static void show(ColorJButton swatch, String hex)
	{
		swatch.setColor(Color.decode(hex));
		swatch.setText(hex.toUpperCase(Locale.ROOT));
	}

	private static String hex(Color color)
	{
		return String.format("#%06X", color.getRGB() & 0xFFFFFF);
	}

	private static JPanel spaced(JComponent row)
	{
		JPanel spaced = Sidebar.left(row);
		spaced.setOpaque(false);
		spaced.setBorder(new EmptyBorder(0, 0, 6, 0));
		return spaced;
	}
}
