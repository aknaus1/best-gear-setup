package com.bestgearsetup.ui;

import com.bestgearsetup.Budget;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.MonsterSummary;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.LinkBrowser;

public class BestGearSetupPanel extends PluginPanel
{
	private static final int MAX_SUGGESTIONS = 10;
	/** CSS width for wrapped labels; the RuneScape font renders wider than Swing's CSS px estimate. */
	private static final int HTML_WIDTH = 160;
	private static final String WIKI_URL = "https://oldschool.runescape.wiki/";

	private final BestGearSetupPlugin plugin;
	private final BestGearSetupConfig config;
	private final ItemManager itemManager;

	private final IconTextField searchField = new IconTextField();
	private final JPanel suggestions = new JPanel();
	private final JLabel monsterLabel = new JLabel();
	private final JComboBox<SearchMode> modeBox = new JComboBox<>(SearchMode.values());
	private final JTextField budgetField = new JTextField();
	private final JCheckBox onTaskBox = new JCheckBox("On slayer task");
	private final JButton findButton = new JButton("Find best setup");
	private final JLabel statusLabel = new JLabel();
	private final JLabel ownedLabel = new JLabel();
	private final JPanel resultsPanel = new JPanel();
	private final ConstraintsPanel constraints;
	private final FightOptionsPanel fightOptions;
	private final SpriteCache sprites;
	private final SkillIconManager skillIcons;

	private GameData data;
	private MonsterSummary selected;
	private boolean syncing;

	public BestGearSetupPanel(BestGearSetupPlugin plugin, BestGearSetupConfig config, ItemManager itemManager,
		SpriteManager spriteManager, SkillIconManager skillIconManager)
	{
		super(false);
		this.plugin = plugin;
		this.config = config;
		this.fightOptions = new FightOptionsPanel(plugin, config, plugin.getConfigItems());
		this.itemManager = itemManager;
		this.sprites = new SpriteCache(spriteManager);
		this.skillIcons = skillIconManager;
		this.constraints = new ConstraintsPanel(plugin);

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Best Gear Setup");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		content.add(left(title));
		content.add(spacer(6));

		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setPreferredSize(new Dimension(PANEL_WIDTH - 16, 30));
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchField.setEnabled(false);
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateSuggestions();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateSuggestions();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateSuggestions();
			}
		});
		searchField.addActionListener(e ->
		{
			if (data != null)
			{
				List<MonsterSummary> matches = data.searchMonsters(searchField.getText(), 1);
				if (!matches.isEmpty())
				{
					selectMonster(matches.get(0), true);
				}
			}
		});
		content.add(left(searchField));

		suggestions.setLayout(new BoxLayout(suggestions, BoxLayout.Y_AXIS));
		suggestions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		content.add(left(suggestions));
		content.add(spacer(6));

		monsterLabel.setForeground(Color.WHITE);
		monsterLabel.setFont(FontManager.getRunescapeSmallFont());
		content.add(left(monsterLabel));
		content.add(spacer(6));

		JPanel settings = new JPanel(new GridLayout(0, 1, 0, 3));
		settings.setBackground(ColorScheme.DARK_GRAY_COLOR);
		modeBox.setFocusable(false);
		modeBox.addActionListener(e ->
		{
			updateBudgetEnabled();
			if (!syncing)
			{
				plugin.setConfig(BestGearSetupConfig.MODE_KEY, modeBox.getSelectedItem());
			}
		});
		settings.add(modeBox);

		JPanel budgetRow = new JPanel(new BorderLayout(4, 0));
		budgetRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JLabel budgetLabel = new JLabel("Budget");
		budgetLabel.setFont(FontManager.getRunescapeSmallFont());
		budgetRow.add(budgetLabel, BorderLayout.WEST);
		budgetField.setToolTipText("e.g. 750k, 50m, 1.2b");
		budgetField.addActionListener(e -> commitBudget());
		budgetField.addFocusListener(new java.awt.event.FocusAdapter()
		{
			@Override
			public void focusLost(java.awt.event.FocusEvent e)
			{
				commitBudget();
			}
		});
		budgetRow.add(budgetField, BorderLayout.CENTER);
		settings.add(budgetRow);

		onTaskBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
		onTaskBox.setFocusable(false);
		onTaskBox.addActionListener(e ->
		{
			if (!syncing)
			{
				plugin.setConfig(BestGearSetupConfig.ON_TASK_KEY, onTaskBox.isSelected());
			}
		});
		settings.add(onTaskBox);
		content.add(left(settings));
		content.add(spacer(6));

		findButton.setEnabled(false);
		findButton.setFocusable(false);
		findButton.addActionListener(e ->
		{
			commitBudget();
			if (selected != null)
			{
				plugin.findBestSetup(selected);
			}
		});
		content.add(left(findButton));
		content.add(spacer(4));
		content.add(left(fightOptions));
		content.add(spacer(4));
		content.add(left(constraints));
		content.add(spacer(4));

		ownedLabel.setFont(FontManager.getRunescapeSmallFont());
		ownedLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		content.add(left(ownedLabel));

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setText(html("Loading bundled Wiki data..."));
		content.add(left(statusLabel));
		content.add(spacer(6));

		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.add(left(resultsPanel));

		JLabel credit = new JLabel(html("Data: <u>Old School RuneScape Wiki contributors</u>. DPS is an estimate; encounter and fight assumptions apply."));
		credit.setFont(FontManager.getRunescapeSmallFont());
		credit.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		credit.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		credit.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(WIKI_URL);
			}
		});
		content.add(spacer(8));
		content.add(left(credit));

		JPanel north = new SidebarContent();
		north.setBackground(ColorScheme.DARK_GRAY_COLOR);
		north.add(content, BorderLayout.NORTH);
		javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(north);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		add(scroll, BorderLayout.CENTER);

		syncFromConfig();
	}

	// ------------------------------------------------------------ state updates (Swing thread)

	public void onDataLoaded(GameData data)
	{
		this.data = data;
		searchField.setEnabled(true);
		constraints.refresh();
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setText(html("Search for a monster, or right-click one in game and pick <b>Best setup</b>."));
	}

	public void showError(String message)
	{
		statusLabel.setForeground(new Color(0xFF6B6B));
		statusLabel.setText(html(escape(message)));
		findButton.setEnabled(selected != null && data != null);
	}

	/** A neutral status message, e.g. while the data is still loading. */
	public void showStatus(String message)
	{
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setText(html(escape(message)));
	}

	/** The bundled data could not be read: show the error with a Retry button. */
	public void showLoadError(String message)
	{
		showError(message);
		if (data == null)
		{
			JButton retry = new JButton("Retry");
			retry.addActionListener(e ->
			{
				resultsPanel.removeAll();
				statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				statusLabel.setText(html("Loading bundled Wiki data..."));
				plugin.reloadGameData();
			});
			resultsPanel.removeAll();
			resultsPanel.add(left(retry));
			resultsPanel.revalidate();
		}
	}

	public void showSearch(String text)
	{
		searchField.setText(text);
	}

	public void selectMonster(MonsterSummary monster, boolean run)
	{
		selected = monster;
		fightOptions.setTarget(monster);
		suggestions.removeAll();
		suggestions.revalidate();
		monsterLabel.setText(html("<b>" + escape(monster.getDisplayName()) + "</b> (level " + monster.getCombatLevel() + ")"
			+ (monster.getAttributes().isEmpty() ? "" : "<br>" + escape(String.join(", ", monster.getAttributes())))));
		findButton.setEnabled(true);
		if (run)
		{
			plugin.findBestSetup(monster);
		}
	}

	public void showSearching(MonsterSummary monster)
	{
		clearResults("Searching setups for " + monster.getDisplayName() + "...");
	}

	/** Clear recommendations when the search or account they belong to is no longer current. */
	public void clearResults(String message)
	{
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setText(html(escape(message)));
		resultsPanel.removeAll();
		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	public void showResults(SearchResults found)
	{
		resultsPanel.removeAll();
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		StringBuilder status = new StringBuilder();
		if (found.isEmpty())
		{
			status.append("No usable setup found.");
			if (found.hasBlockingLock())
			{
				status.append(" A slot lock can't be used; see <b>Locks</b> below.");
			}
			else if (config.mode() == SearchMode.OWNED_ONLY && !plugin.getOwnedItems().isBankKnown())
			{
				status.append(" Open your bank once so the plugin can see what you own.");
			}
			else
			{
				status.append(" Check distance, locks, excluded items and style / experience filters.");
			}
		}
		else
		{
			status.append("Best setups vs <b>").append(escape(found.getMonster().getDisplayName())).append("</b>");
			if (found.hasBlockingLock())
			{
				status.append("<br><font color='#FFD24D'>A slot lock couldn't be applied; see Locks below.</font>");
			}
		}
		if (found.isAssumedLevels())
		{
			status.append("<br>Not logged in: assuming 99 in all combat stats.");
		}
		statusLabel.setText(html(status.toString()));

		if (!found.getLocks().isEmpty())
		{
			resultsPanel.add(left(new LocksView(found.getLocks(), plugin, itemManager)));
			resultsPanel.add(spacer(4));
		}
		if (!found.isEmpty())
		{
			ResultView resultView = new ResultView(found, plugin, itemManager, sprites, skillIcons, config.ammoCount());
			resultsPanel.add(left(resultView));
		}
		if (!found.getNotes().isEmpty())
		{
			JPanel details = new JPanel(new BorderLayout());
			details.setBackground(ColorScheme.DARK_GRAY_COLOR);
			JButton toggle = new JButton("+ Search details");
			toggle.setFocusable(false);
			JLabel notes = new JLabel(html(found.getNotes().stream().map(BestGearSetupPanel::escape)
				.collect(Collectors.joining("<br>"))));
			notes.setName("searchDetails");
			setSmall(notes, ColorScheme.LIGHT_GRAY_COLOR);
			notes.setVisible(false);
			details.add(toggle, BorderLayout.NORTH);
			details.add(notes, BorderLayout.CENTER);
			toggle.addActionListener(e ->
			{
				notes.setVisible(!notes.isVisible());
				toggle.setText((notes.isVisible() ? "- " : "+ ") + "Search details");
				resultsPanel.revalidate();
			});
			resultsPanel.add(left(details));
		}
		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	/** An item was marked or unmarked as owned by hand. */
	public void onManualOwnedChanged()
	{
		updateOwnedStatus();
		constraints.refreshOwned();
	}

	public void updateOwnedStatus()
	{
		if (!plugin.getOwnedItems().isBankKnown())
		{
			ownedLabel.setText(html("Open your bank once to use your owned items."));
		}
		else
		{
			ownedLabel.setText(html(plugin.getOwnedItems().count() + " owned items tracked."));
		}
	}

	public void syncFromConfig()
	{
		syncing = true;
		try
		{
			modeBox.setSelectedItem(config.mode());
			budgetField.setText(config.budget());
			onTaskBox.setSelected(config.onSlayerTask());
			fightOptions.refresh();
			updateBudgetEnabled();
			constraints.refresh();
		}
		finally
		{
			syncing = false;
		}
	}

	private void updateBudgetEnabled()
	{
		budgetField.setEnabled(modeBox.getSelectedItem() == SearchMode.BUDGET);
	}

	private void commitBudget()
	{
		String text = budgetField.getText();
		long gp = Budget.parse(text);
		if (gp < 0)
		{
			budgetField.setText(config.budget());
			return;
		}
		String normalised = Budget.format(gp);
		budgetField.setText(normalised);
		if (!normalised.equals(config.budget()))
		{
			plugin.setConfig(BestGearSetupConfig.BUDGET_KEY, normalised);
		}
	}

	private void updateSuggestions()
	{
		suggestions.removeAll();
		if (data != null)
		{
			for (MonsterSummary m : data.searchMonsters(searchField.getText(), MAX_SUGGESTIONS))
			{
				suggestions.add(suggestionRow(m));
			}
		}
		suggestions.revalidate();
		suggestions.repaint();
	}

	private JPanel suggestionRow(MonsterSummary m)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 6, 3, 6));
		JLabel name = new JLabel(html(escape(m.getDisplayName())));
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(m.isBoss() ? ColorScheme.BRAND_ORANGE : Color.WHITE);
		JLabel level = new JLabel(String.valueOf(m.getCombatLevel()));
		level.setFont(FontManager.getRunescapeSmallFont());
		level.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(name, BorderLayout.CENTER);
		row.add(level, BorderLayout.EAST);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				selectMonster(m, true);
			}
		});
		return row;
	}

	// ------------------------------------------------------------ helpers

	/** Keep wide controls from expanding the view beyond the sidebar's visible width. */
	private static final class SidebarContent extends JPanel implements Scrollable
	{
		private SidebarContent()
		{
			super(new BorderLayout());
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return Math.max(16, visibleRect.height - 16);
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	static JPanel left(java.awt.Component c)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);
		p.add(c, BorderLayout.CENTER);
		p.setMinimumSize(new Dimension(0, 0));
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	private static JPanel spacer(int height)
	{
		JPanel p = new JPanel();
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);
		p.setPreferredSize(new Dimension(1, height));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	static String html(String body)
	{
		return html(body, HTML_WIDTH);
	}

	static String html(String body, int width)
	{
		return "<html><body style='width:" + width + "px'>" + body + "</body></html>";
	}

	static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** A small crossed-swords icon for the sidebar. */
	public static BufferedImage createNavIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0xF7, 0x8B, 0x00));
		g.drawLine(3, 3, 12, 12);
		g.drawLine(12, 3, 3, 12);
		g.setColor(new Color(0xD8, 0xD8, 0xD8));
		g.drawLine(2, 10, 5, 13);
		g.drawLine(13, 10, 10, 13);
		g.dispose();
		return img;
	}

	static void setSmall(JLabel label, Color color)
	{
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setBorder(BorderFactory.createEmptyBorder());
	}
}
