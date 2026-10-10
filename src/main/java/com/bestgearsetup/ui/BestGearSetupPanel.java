package com.bestgearsetup.ui;

import com.bestgearsetup.Budget;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.calc.EncounterPhases;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterGroup;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
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
import okhttp3.HttpUrl;

public class BestGearSetupPanel extends PluginPanel
{
	private static final int MAX_SUGGESTIONS = 10;
	/** CSS width for wrapped labels; the RuneScape font renders wider than Swing's CSS px estimate. */
	private static final int HTML_WIDTH = 160;
	private static final String WIKI_URL = "https://oldschool.runescape.wiki/";
	private static final Color ERROR_COLOR = new Color(0xFF6B6B);
	private static final Color WARNING_COLOR = new Color(0xFFD24D);

	private final BestGearSetupPlugin plugin;
	private final BestGearSetupConfig config;
	private final ItemManager itemManager;

	final IconTextField searchField = new IconTextField();
	final JToggleButton bossesButton = new JToggleButton("Bosses");
	final JToggleButton allButton = new JToggleButton("All monsters");
	final JPanel suggestions = new JPanel();
	final JComboBox<MonsterSummary> versionBox = new JComboBox<>();
	final JPanel versionRow = new JPanel(new BorderLayout(0, 2));
	final JComboBox<EncounterPhase> phaseBox = new JComboBox<>();
	final JPanel phaseRow = new JPanel(new BorderLayout(0, 2));
	private final JLabel monsterLabel = new JLabel();
	final JButton wikiButton = new JButton("Open Wiki");
	final JComboBox<SearchMode> modeBox = new JComboBox<>(SearchMode.values());
	final JTextField budgetField = new JTextField();
	final JPanel budgetRow = new JPanel(new BorderLayout(4, 0));
	private final CardLayout views = new CardLayout();
	private final JButton gearButton = new JButton("Gear rules");
	private final JLabel assumptionsLabel = new JLabel();
	private final JButton filterBadge = new JButton("Bosses");
	private JButton editorOrigin;
	private final Map<String, JButton> editorBackButtons = new HashMap<>();
	private final JLabel budgetError = new JLabel();
	private final JLabel noMatches = new JLabel();
	final JButton findButton = new JButton("Find best setup");
	private final JButton clearSearchButton = new JButton("Clear search");
	final JLabel statusLabel = new JLabel();
	private final JProgressBar progressBar = new JProgressBar(0, 100);
	/** Holds the bar with its top gap; hidden whenever no search is running. */
	private final JPanel progressRow = left(progressBar);
	private final JLabel ownedLabel = new JLabel();
	final JPanel resultsPanel = new JPanel();
	private final ConstraintsPanel constraints;
	private final FightOptionsPanel fightOptions;
	private final SpriteCache sprites;
	private final SkillIconManager skillIcons;

	private GameData data;
	MonsterSummary selected;
	/** The searched group of {@link #selected}; its variants fill the version list. */
	private MonsterGroup selectedGroup;
	private boolean syncing;

	public BestGearSetupPanel(BestGearSetupPlugin plugin, BestGearSetupConfig config, ItemManager itemManager,
		SpriteManager spriteManager, SkillIconManager skillIconManager)
	{
		super(false);
		this.plugin = plugin;
		this.config = config;
		SidebarSection.Accordion options = new SidebarSection.Accordion();
		this.fightOptions = new FightOptionsPanel(plugin, config, plugin.getConfigItems(), options);
		this.itemManager = itemManager;
		this.sprites = new SpriteCache(spriteManager);
		this.skillIcons = skillIconManager;
		this.constraints = new ConstraintsPanel(plugin, options);

		setLayout(views);
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Best Gear Setup");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		titleRow.add(title, BorderLayout.CENTER);
		filterBadge.setFont(FontManager.getRunescapeSmallFont());
		filterBadge.setMargin(new java.awt.Insets(0, 2, 0, 2));
		filterBadge.setToolTipText("Bosses-only filter is active. Click to search all monsters.");
		filterBadge.addActionListener(e -> allButton.doClick());
		titleRow.add(filterBadge, BorderLayout.EAST);
		content.add(left(titleRow));
		content.add(spacer(6));

		JPanel filter = new JPanel(new GridLayout(1, 2, 2, 0));
		filter.setBackground(ColorScheme.DARK_GRAY_COLOR);
		ButtonGroup filterGroup = new ButtonGroup();
		bossesButton.setToolTipText("Search bosses only");
		allButton.setToolTipText("Search bosses and other monsters");
		for (JToggleButton button : new JToggleButton[]{bossesButton, allButton})
		{
			button.setFont(FontManager.getRunescapeSmallFont());
			button.setFocusable(false);
			button.addActionListener(e ->
			{
				if (!syncing)
				{
					plugin.setConfig(BestGearSetupConfig.BOSSES_ONLY_KEY, bossesButton.isSelected());
				}
				updateSuggestions();
			});
			filterGroup.add(button);
			filter.add(button);
		}
		// The search filter is a persistent plugin preference; the recovery action still uses allButton.

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
				List<MonsterGroup> matches = data.searchMonsterGroups(searchField.getText(), 1, bossesButton.isSelected());
				if (!matches.isEmpty())
				{
					select(matches.get(0), matches.get(0).getPrimary(), true);
				}
			}
		});
		content.add(left(searchField));

		suggestions.setLayout(new BoxLayout(suggestions, BoxLayout.Y_AXIS));
		suggestions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		content.add(left(suggestions));
		noMatches.setName("noMatches");
		setSmall(noMatches, ERROR_COLOR);
		noMatches.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		noMatches.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				// The hint offers All monsters when only the Bosses filter hides the matches.
				if (bossesButton.isSelected() && data != null
					&& !data.searchMonsterGroups(searchField.getText(), 1, false).isEmpty())
				{
					allButton.doClick();
				}
			}
		});
		noMatches.setVisible(false);
		content.add(left(noMatches));
		content.add(spacer(6));

		versionRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JLabel versionLabel = new JLabel("Version");
		setSmall(versionLabel, Color.WHITE);
		versionLabel.setLabelFor(versionBox);
		versionBox.setFocusable(false);
		versionBox.setToolTipText("Boss phase or monster variant to search against");
		versionBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof MonsterSummary && selectedGroup != null)
				{
					MonsterSummary variant = (MonsterSummary) value;
					setText(selectedGroup.versionLabel(variant) + " (lvl " + variant.getCombatLevel() + ")");
				}
				return this;
			}
		});
		versionBox.addActionListener(e ->
		{
			MonsterSummary variant = (MonsterSummary) versionBox.getSelectedItem();
			if (!syncing && variant != null && !variant.equals(selected))
			{
				select(selectedGroup, variant, true);
			}
		});
		versionRow.add(versionLabel, BorderLayout.NORTH);
		versionRow.add(versionBox, BorderLayout.CENTER);
		versionRow.setVisible(false);
		content.add(left(versionRow));

		phaseRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		phaseRow.setBorder(new EmptyBorder(3, 0, 0, 0));
		JLabel phaseLabel = new JLabel("Boss phase");
		setSmall(phaseLabel, Color.WHITE);
		phaseLabel.setLabelFor(phaseBox);
		phaseBox.setFocusable(false);
		phaseBox.setToolTipText("Select a temporary boss state. Only the named boss is affected.");
		phaseBox.addActionListener(e ->
		{
			if (!syncing && phaseBox.getSelectedItem() != null)
			{
				plugin.setConfig(BestGearSetupConfig.PHASE_KEY, phaseBox.getSelectedItem());
			}
		});
		phaseRow.add(phaseLabel, BorderLayout.NORTH);
		phaseRow.add(phaseBox, BorderLayout.CENTER);
		phaseRow.setVisible(false);
		content.add(left(phaseRow));
		content.add(spacer(6));

		monsterLabel.setForeground(Color.WHITE);
		monsterLabel.setFont(FontManager.getRunescapeSmallFont());
		JPanel targetRow = new JPanel(new BorderLayout(2, 0));
		targetRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		targetRow.add(monsterLabel, BorderLayout.CENTER);
		wikiButton.setEnabled(false);
		wikiButton.setFocusable(false);
		wikiButton.setToolTipText("Select a monster to open its OSRS Wiki page");
		wikiButton.addActionListener(e ->
		{
			if (selected != null)
			{
				LinkBrowser.browse(HttpUrl.get(WIKI_URL).newBuilder()
					.addPathSegments("w/Special:Lookup")
					.addQueryParameter("type", "npc")
					.addQueryParameter("id", String.valueOf(selected.getId()))
					.addQueryParameter("name", selectedGroup == null ? selected.getDisplayName() : selectedGroup.getDisplayName())
					.addQueryParameter("utm_source", "runelite")
					.build().toString());
			}
		});
		wikiButton.setText("Wiki");
		wikiButton.setFont(FontManager.getRunescapeSmallFont());
		wikiButton.setMargin(new java.awt.Insets(0, 3, 0, 3));
		targetRow.add(wikiButton, BorderLayout.EAST);
		content.add(left(targetRow));
		content.add(spacer(6));

		JPanel settings = new JPanel();
		settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
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

		content.add(left(settings));
		budgetError.setName("budgetError");
		setSmall(budgetError, ERROR_COLOR);
		budgetError.setVisible(false);
		content.add(left(budgetError));
		content.add(spacer(6));

		findButton.setEnabled(false);
		findButton.setFocusable(false);
		findButton.addActionListener(e ->
		{
			if (modeBox.getSelectedItem() == SearchMode.BUDGET) { commitBudget(); }
			if (selected != null)
			{
				plugin.findBestSetup(selected);
			}
		});
		clearSearchButton.setName("clearSearch");
		clearSearchButton.setFont(FontManager.getRunescapeSmallFont());
		clearSearchButton.setMargin(new java.awt.Insets(2, 3, 2, 3));
		clearSearchButton.setFocusable(false);
		clearSearchButton.setToolTipText("Clear the target, results, highlights and bank gear layout");
		clearSearchButton.addActionListener(e -> plugin.clearGearSearch());
		JPanel searchActions = new JPanel(new BorderLayout(4, 0));
		searchActions.setOpaque(false);
		searchActions.add(findButton, BorderLayout.CENTER);
		searchActions.add(clearSearchButton, BorderLayout.EAST);
		content.add(left(searchActions));
		content.add(spacer(4));
		JPanel actions = new JPanel(new GridLayout(1, 3, 2, 0));
		actions.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton fightButton = new JButton("Fight");
		fightButton.setName("fightEditorButton");
		fightButton.addActionListener(e -> { editorOrigin = fightButton; openEditor("fight"); });
		gearButton.setName("gearEditorButton");
		gearButton.addActionListener(e -> { editorOrigin = gearButton; openEditor("gear"); });
		JButton settingsButton = new JButton("Settings");
		settingsButton.setName("settingsButton");
		settingsButton.setToolTipText("Open this plugin's settings in RuneLite's configuration panel");
		settingsButton.addActionListener(e -> plugin.openConfiguration());
		for (JButton action : new JButton[]{fightButton, gearButton, settingsButton})
		{
			action.setFont(FontManager.getRunescapeSmallFont());
			action.setMargin(new java.awt.Insets(1, 0, 1, 0));
			actions.add(action);
		}
		content.add(left(actions));
		content.add(spacer(4));
		assumptionsLabel.setName("assumptionsSummary");
		setSmall(assumptionsLabel, ColorScheme.LIGHT_GRAY_COLOR);
		content.add(left(assumptionsLabel));

		ownedLabel.setFont(FontManager.getRunescapeSmallFont());
		ownedLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		content.add(left(ownedLabel));

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setText(html("Loading bundled Wiki data..."));
		content.add(left(statusLabel));
		progressBar.setName("searchProgress");
		progressBar.setStringPainted(true);
		progressBar.setFont(FontManager.getRunescapeSmallFont());
		progressBar.setForeground(ColorScheme.BRAND_ORANGE);
		progressBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		progressBar.setBorder(new EmptyBorder(0, 0, 0, 0));
		progressBar.setPreferredSize(new Dimension(0, 14));
		progressRow.setVisible(false);
		progressRow.setBorder(new EmptyBorder(4, 0, 0, 0));
		content.add(progressRow);
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
		add(scroll, "results");
		addEditor("Fight assumptions", fightOptions, "fight");
		addEditor("Gear rules", constraints, "gear");

		syncFromConfig();
	}

	private void addEditor(String title, JPanel body, String key)
	{
		JPanel editor = new JPanel(new BorderLayout(0, 6));
		editor.setName(key + "Editor");
		editor.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton back = new JButton("Back to results");
		back.setName(key + "Back");
		editorBackButtons.put(key, back);
		back.addActionListener(e ->
		{
			openEditor("results");
			if (editorOrigin != null) { editorOrigin.requestFocusInWindow(); }
		});
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.add(back, BorderLayout.NORTH);
		JLabel heading = new JLabel(title);
		setSmall(heading, Color.WHITE);
		header.add(heading, BorderLayout.SOUTH);
		editor.add(header, BorderLayout.NORTH);
		JPanel wrapped = new SidebarContent();
		wrapped.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapped.add(body, BorderLayout.NORTH);
		javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(wrapped);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		editor.add(scroll, BorderLayout.CENTER);
		add(editor, key);
	}

	private void openEditor(String key)
	{
		views.show(this, key);
		JButton back = editorBackButtons.get(key);
		if (back != null) { back.requestFocusInWindow(); }
		revalidate();
		repaint();
	}

	/** Open the Gear view at its lock search, limited to this slot (null for any). */
	public void showLockSearch(Slot slot)
	{
		editorOrigin = gearButton;
		openEditor("gear");
		constraints.startLockSearch(slot);
	}

	/** Show the effective search assumptions without placing another editor above results. */
	public void showAssumptions(String summary, String detail)
	{
		assumptionsLabel.setText(summary);
		assumptionsLabel.setToolTipText(detail);
	}

	// ------------------------------------------------------------ state updates (Swing thread)

	public void onDataLoaded(GameData data)
	{
		boolean first = this.data == null;
		this.data = data;
		searchField.setEnabled(true);
		constraints.refresh();
		if (first)
		{
			statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			statusLabel.setText(html("Search for a monster, or right-click one in game and pick <b>Best setup</b>."));
		}
	}

	public void showError(String message)
	{
		progressRow.setVisible(false);
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

	/** Reset the current target and recommendations after the plugin cancels outstanding work. */
	public void clearSearch()
	{
		selected = null;
		showVersions(null, null);
		searchField.setText("");
		suggestions.removeAll();
		noMatches.setVisible(false);
		monsterLabel.setText("");
		monsterLabel.setToolTipText(null);
		wikiButton.setEnabled(false);
		findButton.setEnabled(false);
		fightOptions.setTarget(null);
		clearResults("Search for a monster, or right-click one in game and pick Best setup.");
		revalidate();
		repaint();
	}

	public void selectMonster(MonsterSummary monster, boolean run)
	{
		select(data == null ? null : data.groupOf(monster, bossesButton.isSelected()), monster, run);
	}

	/** Select one variant of a searched group; the group's other variants are offered as versions. */
	private void select(MonsterGroup group, MonsterSummary monster, boolean run)
	{
		searchField.setText(group == null ? monster.getDisplayName() : group.getDisplayName());
		showVersions(group, monster);
		selected = monster;
		wikiButton.setEnabled(true);
		wikiButton.setToolTipText("Open the OSRS Wiki page for " + monster.getDisplayName());
		fightOptions.setTarget(monster);
		suggestions.removeAll();
		suggestions.revalidate();
		noMatches.setVisible(false);
		monsterLabel.setText("<html><div style='width:120px'><b>" + escape(monster.getDisplayName())
			+ "</b> (lvl " + monster.getCombatLevel() + ")</div></html>");
		monsterLabel.setToolTipText(String.join(", ", monster.getAttributes()));
		showAssumptions("Task: " + config.taskMode(), "Auto matches the selected monster to your Slayer assignment. Overrides are in Fight.");
		findButton.setEnabled(true);
		if (run)
		{
			plugin.findBestSetup(monster);
		}
	}

	private void showVersions(MonsterGroup group, MonsterSummary monster)
	{
		boolean previous = syncing;
		syncing = true;
		try
		{
			if (group != selectedGroup)
			{
				selectedGroup = group;
				versionBox.setModel(group == null ? new DefaultComboBoxModel<>()
					: new DefaultComboBoxModel<>(group.getVariants().toArray(new MonsterSummary[0])));
			}
			versionBox.setSelectedItem(monster);
			versionRow.setVisible(group != null && group.getVariants().size() > 1);
		}
		finally
		{
			syncing = previous;
		}
		showPhases(monster);
	}

	/**
	 * Offer the temporary boss states that apply to the target. The saved phase is shown when it applies;
	 * otherwise Standard is shown and the saved choice is kept for the boss it names.
	 */
	private void showPhases(MonsterSummary monster)
	{
		boolean previous = syncing;
		syncing = true;
		try
		{
			DefaultComboBoxModel<EncounterPhase> phases = new DefaultComboBoxModel<>();
			phases.addElement(EncounterPhase.STANDARD);
			if (monster != null)
			{
				Monster target = FightOptionsPanel.targetOf(monster);
				for (EncounterPhase phase : EncounterPhase.values())
				{
					if (phase != EncounterPhase.STANDARD && EncounterPhases.applies(target, phase))
					{
						phases.addElement(phase);
					}
				}
			}
			phaseBox.setModel(phases);
			EncounterPhase saved = config.encounterPhase();
			phaseBox.setSelectedItem(phases.getIndexOf(saved) >= 0 ? saved : EncounterPhase.STANDARD);
			phaseRow.setVisible(phases.getSize() > 1);
		}
		finally
		{
			syncing = previous;
		}
	}

	public void showSearching(MonsterSummary monster)
	{
		clearResults("Searching setups for " + monster.getDisplayName() + "...");
		progressBar.setValue(0);
		progressRow.setVisible(true);
	}

	/** Advance the running search's bar; updates from different search threads may arrive out of order. */
	public void showProgress(int percent)
	{
		progressBar.setValue(Math.max(progressBar.getValue(), percent));
	}

	/** Clear recommendations when the search or account they belong to is no longer current. */
	public void clearResults(String message)
	{
		progressRow.setVisible(false);
		assumptionsLabel.setText("");
		assumptionsLabel.setToolTipText(null);
		plugin.setBankHighlightedSetup(null);
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setText(html(escape(message)));
		resultsPanel.removeAll();
		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	/** One line per lock the search couldn't apply; locked slots are otherwise marked in the equipment grid. */
	private static String blockedLocks(SearchResults found)
	{
		return found.getLocks().stream().filter(LockStatus::isBlocking)
			.map(lock -> escape(lock.getSlot().getDisplayName()) + " lock can't be used: " + escape(lock.getProblem())
				+ ". Remove it under Gear.")
			.collect(Collectors.joining("<br>"));
	}

	public void showResults(SearchResults found)
	{
		progressRow.setVisible(false);
		plugin.setBankHighlightedSetup(null);
		resultsPanel.removeAll();
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		StringBuilder status = new StringBuilder();
		if (found.isEmpty())
		{
			status.append("No usable setup found.");
			if (found.hasBlockingLock())
			{
				status.append("<br>").append(blockedLocks(found));
			}
			else if (config.mode() == SearchMode.OWNED_ONLY && !plugin.getOwnedItems().isBankKnown())
			{
				status.append(" Open your bank once so the plugin can see what you own.");
			}
			else if (config.mode() == SearchMode.INVENTORY_ONLY)
			{
				status.append(" Only your inventory and worn equipment are used; withdraw gear or change the mode.");
			}
			else
			{
				status.append(" Check locks, excluded items and style / experience filters.");
			}
		}
		else
		{
			status.append(targetSummary(found));
			if (found.hasBlockingLock())
			{
				status.append("<br><font color='#FFD24D'>").append(blockedLocks(found)).append("</font>");
			}
		}
		if (found.isAssumedLevels())
		{
			status.append("<br>Not logged in: assuming 99 in all combat stats.");
		}
		else if (found.isRememberedLevels())
		{
			status.append("<br>Not logged in: using the levels last seen on this account.");
		}
		statusLabel.setText(html(status.toString()));

		if (!found.getWarnings().isEmpty())
		{
			JLabel warnings = new JLabel(html(found.getWarnings().stream().map(BestGearSetupPanel::escape)
				.collect(Collectors.joining("<br>"))));
			warnings.setName("assumptionWarnings");
			setSmall(warnings, WARNING_COLOR);
			resultsPanel.add(left(warnings));
			resultsPanel.add(spacer(4));
		}
		if (!found.isEmpty())
		{
			ResultView resultView = new ResultView(found, plugin, itemManager, sprites, skillIcons, config.ammoCount());
			resultsPanel.add(left(resultView));
		}
		List<String> detailLines = new ArrayList<>();
		if (plugin.getOwnedItems().isBankKnown())
		{
			detailLines.add(ownedSummary());
		}
		detailLines.addAll(found.getNotes());
		if (!detailLines.isEmpty())
		{
			JPanel details = new JPanel(new BorderLayout());
			details.setBackground(ColorScheme.DARK_GRAY_COLOR);
			JButton toggle = new JButton("+ Search details");
			toggle.setFocusable(false);
			JLabel notes = new JLabel(html(detailLines.stream().map(BestGearSetupPanel::escape)
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

	/** Raid party scaling, when the target has any, and the Defence the setups were ranked against. */
	private static String targetSummary(SearchResults found)
	{
		int defence = found.getMonster().getDefenceLevel();
		String line = "Defence: <b>" + defence + "</b>"
			+ (defence == found.getBaseDefence() ? "" : " (" + found.getBaseDefence() + " before drains)");
		return found.getRaidScaling() == null ? line : escape(found.getRaidScaling()) + "<br>" + line;
	}

	private String ownedSummary()
	{
		int manual = plugin.getOwnedItems().getManual().size();
		return "Owned items: " + plugin.getOwnedItems().count() + " tracked from bank, inventory and equipment"
			+ (manual == 0 ? "" : " + " + manual + " marked by hand") + ".";
	}

	/** An item was marked or unmarked as owned by hand. */
	public void onManualOwnedChanged()
	{
		updateOwnedStatus();
		constraints.refreshOwned();
	}

	public void updateOwnedStatus()
	{
		// The tracked count is listed under Search details; only the missing bank needs a standing prompt.
		boolean known = plugin.getOwnedItems().isBankKnown();
		ownedLabel.setText(known ? "" : html("Open your bank once to use your owned items."));
		ownedLabel.setVisible(!known);
		// Equipped markers are read when painted, so equipping an item updates the shown setup.
		resultsPanel.repaint();
	}

	public void syncFromConfig()
	{
		syncing = true;
		try
		{
			modeBox.setSelectedItem(config.mode());
			(config.bossesOnly() ? bossesButton : allButton).setSelected(true);
			filterBadge.setVisible(config.bossesOnly());
			showPhases(selected);
			budgetField.setText(config.budget());
			fightOptions.refresh();
			updateBudgetEnabled();
			constraints.refresh();
			int rules = plugin.getLocks().size() + plugin.getExcluded().size();
			gearButton.setText("Gear" + (rules == 0 ? "" : " (" + rules + ")"));
			gearButton.setToolTipText("Gear rules: " + rules + " locks or exclusions; also manage extra owned items");
			searchField.setToolTipText(config.bossesOnly() ? "Searching bosses only. Change the filter in Settings." : "Search all monsters");
		}
		finally
		{
			syncing = false;
		}
	}

	private void updateBudgetEnabled()
	{
		budgetField.setEnabled(modeBox.getSelectedItem() == SearchMode.BUDGET);
		budgetRow.setVisible(modeBox.getSelectedItem() == SearchMode.BUDGET);
	}

	/** Apply a changed persistent search filter without rerunning the equipment calculation. */
	public void refreshSearchFilter()
	{
		syncFromConfig();
		updateSuggestions();
	}

	private void commitBudget()
	{
		String text = budgetField.getText();
		long gp = Budget.parse(text);
		if (gp < 0)
		{
			budgetField.setText(config.budget());
			budgetError.setText(html("\"" + escape(text.trim()) + "\" isn't an amount. Use gp, or k / m / b such as"
				+ " 750k, 50m or 1.2b. Kept " + escape(config.budget()) + "."));
			budgetError.setVisible(true);
			revalidate();
			return;
		}
		if (budgetError.isVisible())
		{
			budgetError.setVisible(false);
			revalidate();
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
		String query = searchField.getText();
		boolean none = false;
		if (data != null)
		{
			List<MonsterGroup> matches = data.searchMonsterGroups(query, MAX_SUGGESTIONS, bossesButton.isSelected());
			for (MonsterGroup group : matches)
			{
				suggestions.add(suggestionRow(group));
			}
			none = matches.isEmpty() && !query.trim().isEmpty();
		}
		if (none)
		{
			boolean hiddenByFilter = bossesButton.isSelected() && !data.searchMonsterGroups(query, 1, false).isEmpty();
			noMatches.setText(html(noMatchesMessage(hiddenByFilter, selected == null ? null : selected.getDisplayName())));
			noMatches.setToolTipText(hiddenByFilter ? "Click to search all monsters" : null);
		}
		noMatches.setVisible(none);
		suggestions.revalidate();
		suggestions.repaint();
		revalidate();
	}

	static String noMatchesMessage(boolean hiddenByFilter, String target)
	{
		String message = hiddenByFilter ? "No bosses match. <u>Search all monsters</u> instead."
			: "No matching monsters.";
		return target == null ? message : message + " Still targeting <b>" + escape(target) + "</b>.";
	}

	private JPanel suggestionRow(MonsterGroup group)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 6, 3, 6));
		JLabel name = new JLabel(html(escape(group.getDisplayName())));
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(group.isBoss() ? ColorScheme.BRAND_ORANGE : Color.WHITE);
		int min = group.getMinCombatLevel();
		int max = group.getMaxCombatLevel();
		JLabel level = new JLabel(min == max ? String.valueOf(min) : min + "-" + max);
		int versions = group.getVariants().size();
		if (versions > 1)
		{
			row.setToolTipText(versions + " versions; pick one below the search after selecting");
		}
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
				select(group, group.getPrimary(), true);
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
