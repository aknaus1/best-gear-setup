package com.bestgearsetup.ui;

import com.bestgearsetup.Budget;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.calc.EncounterPhases;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.RaidPlan;
import com.bestgearsetup.RaidEncounter;
import com.bestgearsetup.RaidOptions;
import com.bestgearsetup.RaidPlanType;
import com.bestgearsetup.RaidSpellbook;
import com.bestgearsetup.RaidStyle;
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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
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
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.config.ConfigDescriptor;
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
	private volatile RaidPlanType raidType = RaidPlanType.SINGLE;
	final JComboBox<RaidEncounter> encounterBox = new JComboBox<>();
	private boolean selectingRaid;
	final JToggleButton bossesButton = new JToggleButton("Bosses");
	final JToggleButton allButton = new JToggleButton("All monsters");
	final JPanel suggestions = new JPanel();
	final JComboBox<MonsterSummary> versionBox = new JComboBox<>();
	final JComboBox<RaidEncounter> raidVersionBox = new JComboBox<>();
	final JPanel versionRow = new JPanel(new BorderLayout(0, 2));
	final JComboBox<EncounterPhase> phaseBox = new JComboBox<>();
	/** Attack style of the displayed raid encounter; a change is saved and re-plans the raid. */
	final JComboBox<RaidStyle> raidStyleBox = new JComboBox<>();
	final JPanel raidStyleRow = new JPanel(new BorderLayout(0, 2));
	/** The raid encounter the style selector edits. */
	private RaidEncounter styledEncounter;
	/** The raid plan on screen, or null when none is; active-encounter switches select its encounters. */
	private RaidPlan shownPlan;
	/** Label of the encounter whose style was just changed, shown again once the raid is re-planned. */
	private String pendingRaidEncounter;
	final JPanel phaseRow = new JPanel(new BorderLayout(0, 2));
	private final JLabel monsterLabel = new JLabel();
	final JButton wikiButton = new JButton("Open Wiki");
	final JComboBox<SearchMode> modeBox = new JComboBox<>(SearchMode.values());
	final JTextField budgetField = new JTextField();
	final JPanel budgetRow = new JPanel(new BorderLayout(4, 0));
	private final CardLayout views = new CardLayout();
	private final JButton gearButton = new JButton("Gear rules");
	final JButton fightButton = new JButton("Fight");
	/** Target-specific Fight options already shown to the player, so the Fight button stops flagging them. */
	private final Set<String> seenFightOptions = new LinkedHashSet<>();
	private boolean fightFlagged;
	private String view = "results";
	private final JLabel assumptionsLabel = new JLabel();
	private final JButton filterBadge = new JButton("Bosses");
	private JButton editorOrigin;
	private final Map<String, JButton> editorBackButtons = new HashMap<>();
	private final JLabel budgetError = new JLabel();
	private final JLabel noMatches = new JLabel();
	final JButton findButton = new JButton("Find best setup");
	private final JButton clearSearchButton = new JButton("Clear search");
	final JLabel statusLabel = new JLabel();
	private final JLabel raidProgressLabel = new JLabel();
	private final JProgressBar progressBar = new JProgressBar(0, 10_000);
	private final SearchActivity searchActivity = new SearchActivity();
	/** Holds the bar with its top gap; hidden whenever no search is running. */
	private final JPanel progressRow = left(progressBar);
	private final JLabel ownedLabel = new JLabel();
	final JPanel resultsPanel = new JPanel();
	private final ConstraintsPanel constraints;
	private final FightOptionsPanel fightOptions;
	private final SettingsPanel settingsPanel;
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
		ConfigDescriptor descriptor = plugin.getConfigDescriptor();
		this.fightOptions = new FightOptionsPanel(plugin, config, descriptor.getItems(), options);
		this.settingsPanel = new SettingsPanel(plugin, config, descriptor);
		this.itemManager = itemManager;
		this.sprites = new SpriteCache(spriteManager);
		this.skillIcons = skillIconManager;
		this.constraints = new ConstraintsPanel(plugin, options, itemManager, spriteManager == null ? null : sprites);

		setLayout(views);
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		Controls.badge(filterBadge);
		filterBadge.setToolTipText("Bosses-only filter is active. Click to search all monsters.");
		filterBadge.addActionListener(e -> allButton.doClick());

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
		Controls.search(searchField, "Search monsters or raids...");
		searchField.setName("targetSearch");
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
				List<RaidPlanType> raids = RaidPlanType.search(searchField.getText());
				if (!raids.isEmpty()) { selectRaid(raids.get(0)); return; }
				List<MonsterGroup> matches = data.searchMonsterGroups(searchField.getText(), 1, bossesButton.isSelected());
				if (!matches.isEmpty())
				{
					select(matches.get(0), matches.get(0).getPrimary(), true);
				}
			}
		});
		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setOpaque(false);
		searchRow.add(searchField, BorderLayout.CENTER);
		searchRow.add(filterBadge, BorderLayout.EAST);
		content.add(left(searchRow));

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

		monsterLabel.setForeground(Color.WHITE);
		monsterLabel.setFont(FontManager.getRunescapeSmallFont());
		JPanel targetRow = new JPanel(new BorderLayout(2, 0));
		targetRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		monsterLabel.setName("selectedTarget");
		JPanel targetChoice = new JPanel(new BorderLayout());
		targetChoice.setOpaque(false);
		targetChoice.setName("targetChoice");
		targetChoice.add(monsterLabel, BorderLayout.NORTH);
		Controls.field(encounterBox);
		encounterBox.setName("raidEncounter");
		encounterBox.setToolTipText("Boss or monster whose equipment is shown below");
		encounterBox.getAccessibleContext().setAccessibleName("Current boss or monster");
		encounterBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof RaidEncounter) { setText(((RaidEncounter) value).getBossName()); }
				return this;
			}
		});
		encounterBox.setVisible(false);
		targetChoice.add(encounterBox, BorderLayout.CENTER);
		targetRow.add(targetChoice, BorderLayout.CENTER);
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
		Controls.secondary(wikiButton);
		wikiButton.setMargin(new java.awt.Insets(1, 6, 1, 6));
		targetRow.add(wikiButton, BorderLayout.EAST);
		content.add(left(targetRow));
		content.add(spacer(6));

		versionRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JLabel versionLabel = Controls.caption("Version");
		versionLabel.setLabelFor(versionBox);
		versionBox.setFocusable(false);
		Controls.field(versionBox);
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
		Controls.field(raidVersionBox);
		raidVersionBox.setName("raidVersion");
		raidVersionBox.setToolTipText("Phase of the selected boss in this raid kit");
		raidVersionBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof RaidEncounter) { setText(((RaidEncounter) value).getVersionLabel()); }
				return this;
			}
		});
		versionRow.setVisible(false);
		content.add(left(versionRow));

		phaseRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		phaseRow.setBorder(new EmptyBorder(3, 0, 0, 0));
		JLabel phaseLabel = Controls.caption("Boss phase");
		phaseLabel.setLabelFor(phaseBox);
		phaseBox.setFocusable(false);
		Controls.field(phaseBox);
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

		raidStyleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		raidStyleRow.setBorder(new EmptyBorder(3, 0, 0, 0));
		JLabel raidStyleLabel = Controls.caption("Attack style");
		raidStyleLabel.setLabelFor(raidStyleBox);
		raidStyleBox.setFocusable(false);
		Controls.field(raidStyleBox);
		raidStyleBox.setName("raidStyle");
		raidStyleBox.setToolTipText("Attack style planned for this encounter and the other phases of the same fight. Auto "
			+ "uses ranged or magic where the boss is fought from distance; phases the style can't damage keep Auto. "
			+ "Changing it re-plans the raid.");
		raidStyleBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				RaidEncounter encounter = styledEncounter;
				if (value == RaidStyle.AUTO && encounter != null && encounter.getPreferredStyle() != RaidStyle.AUTO)
				{ setText("Auto (" + encounter.getPreferredStyle().toString().toLowerCase(java.util.Locale.ROOT) + ")"); }
				return this;
			}
		});
		raidStyleBox.addActionListener(e ->
		{
			RaidEncounter encounter = styledEncounter;
			RaidStyle style = (RaidStyle) raidStyleBox.getSelectedItem();
			if (!syncing && encounter != null && style != null && style != encounter.getDisplayedStyleChoice())
			{
				// Phases of one fight share the choice; the re-planned raid reopens this encounter.
				pendingRaidEncounter = encounter.getLabel();
				plugin.setConfig(BestGearSetupConfig.RAID_STYLES_KEY,
					RaidOptions.withStyle(config.raidStyles(), raidType, encounter.getStyleKey(), style));
			}
		});
		raidStyleRow.add(raidStyleLabel, BorderLayout.NORTH);
		raidStyleRow.add(raidStyleBox, BorderLayout.CENTER);
		raidStyleRow.setVisible(false);
		content.add(left(raidStyleRow));
		content.add(spacer(6));


		JPanel settings = new JPanel();
		settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
		settings.setBackground(ColorScheme.DARK_GRAY_COLOR);
		modeBox.setFocusable(false);
		Controls.field(modeBox);
		JLabel modeLabel = Controls.caption("Gear source");
		modeLabel.setLabelFor(modeBox);
		modeLabel.setBorder(new EmptyBorder(0, 0, 2, 0));
		settings.add(left(modeLabel));
		modeBox.setAlignmentX(LEFT_ALIGNMENT);
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
		budgetRow.setBorder(new EmptyBorder(4, 0, 0, 0));
		budgetRow.setAlignmentX(LEFT_ALIGNMENT);
		JLabel budgetLabel = Controls.caption("Budget");
		budgetLabel.setLabelFor(budgetField);
		budgetRow.add(budgetLabel, BorderLayout.WEST);
		budgetField.setToolTipText("e.g. 750k, 50m, 1.2b");
		Controls.field(budgetField);
		Controls.placeholder(budgetField, "e.g. 50m");
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
		Controls.primary(findButton);
		findButton.addActionListener(e ->
		{
			if (modeBox.getSelectedItem() == SearchMode.BUDGET) { commitBudget(); }
			if (selected != null)
			{
				plugin.findBestSetup(selected);
			}
		});
		clearSearchButton.setName("clearSearch");
		Controls.secondary(clearSearchButton);
		clearSearchButton.setFocusable(false);
		clearSearchButton.setToolTipText("Clear the target, results, highlights and bank gear layout");
		clearSearchButton.addActionListener(e -> plugin.clearGearSearch());
		JPanel searchActions = new JPanel(new BorderLayout(4, 0));
		searchActions.setOpaque(false);
		searchActions.add(findButton, BorderLayout.CENTER);
		searchActions.add(clearSearchButton, BorderLayout.EAST);
		content.add(left(searchActions));
		content.add(spacer(4));
		JPanel actions = new JPanel(new GridLayout(1, 3, 4, 0));
		actions.setBackground(ColorScheme.DARK_GRAY_COLOR);
		fightButton.setName("fightEditorButton");
		fightButton.setHorizontalTextPosition(SwingConstants.LEADING);
		fightButton.addActionListener(e -> { editorOrigin = fightButton; openEditor("fight"); });
		gearButton.setName("gearEditorButton");
		gearButton.addActionListener(e -> { editorOrigin = gearButton; openEditor("gear"); });
		JButton settingsButton = new JButton("Settings");
		settingsButton.setName("settingsButton");
		settingsButton.setToolTipText("Search preferences, attack styles, equipment, boosts and highlights");
		settingsButton.addActionListener(e -> { editorOrigin = settingsButton; openEditor("settings"); });
		for (JButton action : new JButton[]{fightButton, gearButton, settingsButton})
		{
			Controls.secondary(action);
			action.setMargin(new java.awt.Insets(3, 0, 3, 0));
			actions.add(action);
		}
		updateFightButton();
		fightOptions.onTargetOptionsChanged(this::updateFightButton);
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
		raidProgressLabel.setName("raidProgress");
		raidProgressLabel.setFont(FontManager.getRunescapeSmallFont());
		raidProgressLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		raidProgressLabel.setVisible(false);
		content.add(left(raidProgressLabel));
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
		content.add(left(searchActivity));
		content.add(spacer(6));

		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.add(left(resultsPanel));
		content.add(spacer(12));
		JButton coffeeButton = new JButton("Support development");
		Controls.ghost(coffeeButton);
		coffeeButton.setHorizontalAlignment(SwingConstants.CENTER);
		coffeeButton.setName("buyMeACoffee");
		coffeeButton.setToolTipText("Support Best Gear Setup on Buy Me a Coffee");
		coffeeButton.getAccessibleContext().setAccessibleName("Buy Me a Coffee");
		coffeeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		coffeeButton.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
		coffeeButton.setContentAreaFilled(false);
		coffeeButton.addActionListener(e -> LinkBrowser.browse("https://www.buymeacoffee.com/aknaus"));
		content.add(left(coffeeButton));

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
		addEditor("Settings", settingsPanel, "settings");

		syncFromConfig();
	}

	private void addEditor(String title, JPanel body, String key)
	{
		JPanel editor = new JPanel(new BorderLayout(0, 6));
		editor.setName(key + "Editor");
		editor.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton back = new JButton("Back to results");
		back.setName(key + "Back");
		Controls.ghost(back);
		back.setIcon(new Controls.Chevron(Controls.Chevron.LEFT, null));
		back.setIconTextGap(6);
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
		heading.setFont(FontManager.getRunescapeBoldFont());
		heading.setForeground(Color.WHITE);
		heading.setBorder(new EmptyBorder(4, 0, 0, 0));
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
		view = key;
		views.show(this, key);
		updateFightButton();
		JButton back = editorBackButtons.get(key);
		if (back != null) { back.requestFocusInWindow(); }
		revalidate();
		repaint();
	}

	/**
	 * Flag the Fight button while it holds target-specific options, such as a raid's party size or ToB role,
	 * that the player has not seen there yet: nothing else tells new players these options exist.
	 */
	private void updateFightButton()
	{
		Map<String, String> shown = fightOptions.targetOptions();
		seenFightOptions.addAll(Arrays.asList(config.seenFightOptions().split(",")));
		if ("fight".equals(view) && !seenFightOptions.containsAll(shown.keySet()))
		{
			seenFightOptions.addAll(shown.keySet());
			seenFightOptions.remove("");
			plugin.setConfig(BestGearSetupConfig.SEEN_FIGHT_OPTIONS_KEY, String.join(",", seenFightOptions));
		}
		List<String> unseen = shown.entrySet().stream().filter(e -> !seenFightOptions.contains(e.getKey()))
			.map(Map.Entry::getValue).collect(Collectors.toList());
		boolean flag = !unseen.isEmpty();
		if (flag != fightFlagged)
		{
			if (flag) { Controls.badge(fightButton); }
			else { Controls.secondary(fightButton); }
			fightButton.setMargin(new java.awt.Insets(3, 0, 3, 0));
			fightButton.setIcon(flag ? new Controls.Dot() : null);
			fightButton.setIconTextGap(4);
		}
		fightButton.setToolTipText(flag
			? html("New options for this fight: <b>" + escape(String.join(", ", unseen)) + "</b>")
			: "Fight conditions, special attacks and raid scaling");
		fightFlagged = flag;
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
		assumptionsLabel.setVisible(raidType == RaidPlanType.SINGLE);
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
		stopSearchActivity();
		statusLabel.setForeground(new Color(0xFF6B6B));
		statusLabel.setText(html(escape(message)));
		findButton.setEnabled(selected != null && data != null);
		pendingRaidEncounter = null;
		// Saved attack styles are hidden settings, so a raid that can't be planned offers to clear them.
		if (raidType != RaidPlanType.SINGLE && RaidOptions.hasStyles(config.raidStyles(), raidType))
		{
			RaidPlanType raid = raidType;
			JButton reset = new JButton("Reset raid attack styles to Auto");
			reset.setName("resetRaidStyles");
			Controls.secondary(reset);
			reset.addActionListener(e -> plugin.setConfig(BestGearSetupConfig.RAID_STYLES_KEY,
				RaidOptions.withoutStyles(config.raidStyles(), raid)));
			resultsPanel.removeAll();
			resultsPanel.add(left(reset));
			resultsPanel.revalidate();
			resultsPanel.repaint();
		}
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
			Controls.secondary(retry);
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
		raidProgressLabel.setVisible(false);
		raidType = RaidPlanType.SINGLE;
		pendingRaidEncounter = null;
		fightOptions.setRaidPlan(raidType);
		selected = null;
		showVersions(null, null);
		searchField.setText("");
		suggestions.removeAll();
		Controls.outlineList(suggestions);
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

	public RaidPlanType getRaidType() { return raidType; }

	/** Select a full raid from the ordinary search results. */
	public void selectRaid(RaidPlanType raid)
	{
		if (data == null || raid == RaidPlanType.SINGLE) { return; }
		// Unlike Clear, the shown results and bank layout stay up until the raid plan replaces them.
		plugin.cancelGearSearch();
		raidProgressLabel.setVisible(false);
		pendingRaidEncounter = null;
		raidType = raid;
		selectingRaid = true;
		try { selectMonster(raidEncounters(raid).get(0).getTarget(), true); }
		finally { selectingRaid = false; }
	}

	/** The raid's encounters in the saved path order and roles, so the first is the planned starting fight. */
	private List<RaidEncounter> raidEncounters(RaidPlanType raid)
	{
		return raid.encounters(data, RaidScaling.Settings.builder().partySize(config.raidPartySize()).build(), RaidOptions.of(config));
	}

	private void showDisplayedEncounter(RaidEncounter encounter)
	{
		selected = encounter.getTarget();
		monsterLabel.setText("<html><div style='width:120px'><b>" + escape(selected.getDisplayName())
			+ "</b> (lvl " + selected.getCombatLevel() + ")</div></html>");
		versionRow.setVisible(raidVersionBox.getItemCount() > 1);
		phaseRow.setVisible(false);
		showRaidStyles(encounter);
		fightOptions.setTarget(selected);
		wikiButton.setToolTipText("Open the OSRS Wiki page for " + selected.getDisplayName());
	}

	/**
	 * The player engaged another NPC of the type already shown, so its results stand: in a raid, show that
	 * encounter of the plan again (the player may have browsed to another) without searching or re-planning.
	 */
	public void showActiveMonster(MonsterSummary monster, EncounterPhase phase)
	{
		if (shownPlan != null && encounterBox.isVisible()) { selectRaidMonster(monster, phase); }
	}

	/** Right-click lookup within the selected raid browses its existing kit without starting another search. */
	public boolean selectRaidMonster(MonsterSummary monster, EncounterPhase phase)
	{
		if (raidType == RaidPlanType.SINGLE || data == null) { return false; }
		RaidPlan plan = shownPlan != null && shownPlan.getType() == raidType ? shownPlan : null;
		List<RaidEncounter> encounters = plan == null ? raidEncounters(raidType) : plan.getEncounters();
		RaidEncounter shown = (RaidEncounter) raidVersionBox.getSelectedItem();
		if (plan != null && encounterBox.isVisible() && shown != null && shown.getTarget().getId() == monster.getId()
			&& (phase == null || shown.getPhase() == phase)) { return true; }
		RaidEncounter match = null;
		for (RaidEncounter encounter : encounters)
		{
			if ((encounter.getTarget().getId() == monster.getId() || encounter.getTarget().getName().equals(monster.getName()))
				&& (match == null || phase != null && encounter.getPhase() == phase && match.getPhase() != phase))
			{ match = encounter; }
		}
		if (match == null) { return false; }
		if (plan != null && encounterBox.isVisible())
		{
			if (!match.equals(shown)) { showEncounter(match); }
		}
		else
		{
			// Keep the running raid search; open this encounter when the completed plan is published.
			pendingRaidEncounter = match.getLabel();
		}
		return true;
	}

	/** Show tracked progress and its exact saved encounter, including repeated phases, without searching. */
	public void showRaidProgress(RaidPlanType type, String label, String status)
	{
		if (raidType == RaidPlanType.SINGLE || type == RaidPlanType.SINGLE)
		{ raidProgressLabel.setVisible(false); return; }
		if (raidType != type) { return; }
		raidProgressLabel.setText(html(escape(status)));
		raidProgressLabel.setVisible(!status.isEmpty());
		if (label == null) { return; }
		RaidEncounter match = shownPlan == null ? null : shownPlan.getEncounters().stream()
			.filter(fight -> fight.getLabel().equals(label)).findFirst().orElse(null);
		if (match != null && encounterBox.isVisible()) { showEncounter(match); }
		else { pendingRaidEncounter = label; }
	}

	/** Select an encounter of the shown raid plan through the boss and version selectors. */
	private void showEncounter(RaidEncounter encounter)
	{
		for (int i = 0; i < encounterBox.getItemCount(); i++)
		{
			if (encounterBox.getItemAt(i).getBossName().equals(encounter.getBossName())) { encounterBox.setSelectedIndex(i); break; }
		}
		raidVersionBox.setSelectedItem(encounter);
	}

	/** Automatic target changes keep a matching full raid selected. */
	public void selectActiveMonster(MonsterSummary monster)
	{
		selectingRaid = getRaidType() != RaidPlanType.SINGLE && data != null && raidEncounters(getRaidType()).stream()
			.anyMatch(e -> e.getTarget().getId() == monster.getId());
		try { selectMonster(monster, false); plugin.findActiveSetup(monster); }
		finally { selectingRaid = false; }
	}

	/** Select one variant of a searched group; the group's other variants are offered as versions. */
	private void select(MonsterGroup group, MonsterSummary monster, boolean run)
	{
		if (!selectingRaid)
		{
			raidProgressLabel.setVisible(false);
			raidType = RaidPlanType.SINGLE;
			encounterBox.setVisible(false);
			monsterLabel.setVisible(true);
			versionRow.remove(raidVersionBox);
			versionRow.add(versionBox, BorderLayout.CENTER);
			showRaidStyles(null);
			pendingRaidEncounter = null;
		}
		fightOptions.setRaidPlan(raidType);
		searchField.setText(raidType == RaidPlanType.SINGLE ? group == null ? monster.getDisplayName() : group.getDisplayName() : raidType.toString());
		showVersions(group, monster);
		selected = monster;
		wikiButton.setEnabled(true);
		wikiButton.setToolTipText("Open the OSRS Wiki page for " + monster.getDisplayName());
		fightOptions.setTarget(monster);
		suggestions.removeAll();
		Controls.outlineList(suggestions);
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

	/** After planning, reopen the encounter selected by a style edit or an in-raid right-click lookup. */
	private void reopenPendingEncounter(RaidPlan plan)
	{
		String label = pendingRaidEncounter;
		pendingRaidEncounter = null;
		RaidEncounter edited = label == null ? null
			: plan.getEncounters().stream().filter(e -> e.getLabel().equals(label)).findFirst().orElse(null);
		if (edited != null) { showEncounter(edited); }
	}

	/** Offer the displayed raid encounter's attack styles; hidden outside raids and where the style is fixed. */
	private void showRaidStyles(RaidEncounter encounter)
	{
		boolean previous = syncing;
		syncing = true;
		try
		{
			styledEncounter = encounter;
			List<RaidStyle> choices = encounter == null ? java.util.Collections.emptyList() : encounter.getStyleChoices();
			raidStyleBox.setModel(new DefaultComboBoxModel<>(choices.toArray(new RaidStyle[0])));
			if (encounter != null) { raidStyleBox.setSelectedItem(encounter.getDisplayedStyleChoice()); }
			raidStyleRow.setVisible(choices.size() > 1);
		}
		finally
		{
			syncing = previous;
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
		// The previous results and bank layout stay up until the new results replace them in place.
		releaseResults("Searching setups for " + monster.getDisplayName() + "...");
		progressBar.setValue(0);
		progressBar.setString("0.00%");
		progressRow.setVisible(true);
		searchActivity.start();
	}

	public void showEncounterProgress(String message)
	{
		showStatus(message);
	}

	/** Advance the running search's bar; updates from different search threads may arrive out of order. */
	public void showProgress(double percent)
	{
		if (searchActivity.isRunning())
		{
			// The worker can finish its progress accounting before final checks and rendering are done.
			progressBar.setValue(Math.max(progressBar.getValue(), (int) Math.min(9999, Math.floor(percent * 100))));
			progressBar.setString(String.format(java.util.Locale.ROOT, "%.2f%%", progressBar.getValue() / 100.0));
		}
	}

	/** Describe the actual search stage while the activity animation continues independently of percentages. */
	public void showSearchActivity(String message)
	{
		searchActivity.stage(message);
	}

	/** Stop animation on completion, failure, cancellation or plugin shutdown. Called on the Swing thread. */
	public void stopSearchActivity()
	{
		searchActivity.stop();
		progressRow.setVisible(false);
	}

	/** Clear recommendations when the search or account they belong to is no longer current. */
	public void clearResults(String message)
	{
		releaseResults(message);
		resultsPanel.removeAll();
		resultsPanel.revalidate();
		resultsPanel.repaint();
		plugin.setBankHighlightedSetup(null);
	}

	/** Detach the shown results from the raid selectors and status, leaving them visible until replaced. */
	private void releaseResults(String message)
	{
		RaidResultView.detachSelector(encounterBox);
		RaidResultView.detachSelector(raidVersionBox);
		encounterBox.setModel(new DefaultComboBoxModel<>());
		encounterBox.setVisible(false);
		shownPlan = null;
		showRaidStyles(null);
		monsterLabel.setVisible(true);
		stopSearchActivity();
		assumptionsLabel.setText("");
		assumptionsLabel.setToolTipText(null);
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setText(html(escape(message)));
	}

	/** One line per lock the search couldn't apply; locked slots are otherwise marked in the equipment grid. */
	private static String blockedLocks(SearchResults found)
	{
		return found.getLocks().stream().filter(LockStatus::isBlocking)
			.map(lock -> escape(lock.getSlot().getDisplayName()) + " lock can't be used: " + escape(lock.getProblem())
				+ ". Remove it under Gear.")
			.collect(Collectors.joining("<br>"));
	}

	public void showRaidResults(RaidPlan plan, MonsterSummary active)
	{
		showRaidResults(plan, active, null);
	}

	public void showRaidResults(RaidPlan plan, MonsterSummary active, SearchResults activeResult)
	{
		stopSearchActivity();
		findButton.setEnabled(true);
		resultsPanel.removeAll();
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		SearchResults first = plan.getResults().get(0);
		assumptionsLabel.setVisible(false);
		versionRow.remove(versionBox);
		versionRow.add(raidVersionBox, BorderLayout.CENTER);
		statusLabel.setText(html(escape(plan.getType().toString()) + ": " + plan.getEncounters().size() + " encounter setups"
			+ (first.isAssumedLevels() ? "<br>Assuming 99 combat stats." : first.isRememberedLevels() ? "<br>Using remembered account levels." : "")));
		shownPlan = plan;
		if (plan.getType() == RaidPlanType.TOA)
		{
			String order = plan.getEncounters().stream().map(RaidEncounter::getBossName)
				.filter(label -> Arrays.asList("Ba-Ba", "Kephri", "Akkha", "Zebak").contains(label))
				.distinct().map(BestGearSetupPanel::escape).collect(Collectors.joining(" → "));
			JLabel layout = new JLabel(html("Path order: " + order));
			layout.setName("raidPathOrder");
			setSmall(layout, ColorScheme.LIGHT_GRAY_COLOR);
			resultsPanel.add(left(layout));
		}
		resultsPanel.add(left(new RaidResultView(plan, active, activeResult, plugin, itemManager, sprites, skillIcons,
			config.ammoCount(), encounterBox, raidVersionBox, this::showDisplayedEncounter)));
		reopenPendingEncounter(plan);
		monsterLabel.setVisible(false);
		encounterBox.setVisible(true);
		List<String> warnings = new ArrayList<>(plan.getWarnings());
		plan.getResults().stream().flatMap(r -> r.getWarnings().stream()).forEach(warnings::add);
		if (activeResult != null) { warnings.addAll(activeResult.getWarnings()); }
		if (plan.getType() == RaidPlanType.TOB && config.tobRole().freezes()
			&& plan.getSpellbook() != RaidSpellbook.ANCIENT)
		{ warnings.add("Freezing the Maiden nylocas needs Ice Barrage: set Raid spellbook to Ancient."); }
		if (!warnings.isEmpty())
		{
			JLabel warningLabel = new JLabel(html(warnings.stream().distinct().map(BestGearSetupPanel::escape).collect(Collectors.joining("<br>"))));
			warningLabel.setName("assumptionWarnings");
			setSmall(warningLabel, WARNING_COLOR);
			resultsPanel.add(left(warningLabel));
		}
		List<String> detailLines = new ArrayList<>();
		if (plugin.getOwnedItems().isBankKnown() || plugin.getOwnedItems().count() > 0) { detailLines.add(ownedSummary()); }
		detailLines.addAll(plan.getNotes());
		detailLines.add("Raid kits use a bounded search over valid setups; lower equipment limits share more armour.");
		detailLines.add("Runes are packed into your pouch; hover it to see its contents. Extra rune types stay in free inventory slots. Food, healing and utility supplies are not planned.");
		detailLines.add("In-raid boosts are omitted from the bank: CoX overloads are assumed after Tekton and ToA smelling salts after the first two paths. The Dawnbringer is picked up at Verzik P1 and only used for its special. Loaded darts must be loaded before entry.");
		addSearchDetails(detailLines);
		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	public void showResults(SearchResults found)
	{
		stopSearchActivity();
		// A found setup replaces the bank layout in place (ResultView publishes it); with none there is nothing to show.
		if (found.isEmpty()) { plugin.setBankHighlightedSetup(null); }
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
			// The boss selector outlives every result, so it keeps the inventory's minimized state for raids too.
			ResultView resultView = new ResultView(found, plugin, itemManager, sprites, skillIcons, config.ammoCount(),
				true, null, encounterBox);
			resultsPanel.add(left(resultView));
		}
		List<String> detailLines = new ArrayList<>();
		if (plugin.getOwnedItems().isBankKnown() || plugin.getOwnedItems().count() > 0)
		{
			detailLines.add(ownedSummary());
		}
		detailLines.addAll(found.getNotes());
		addSearchDetails(detailLines);
		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	private void addSearchDetails(List<String> detailLines)
	{
		if (!detailLines.isEmpty())
		{
			JPanel details = new JPanel(new BorderLayout());
			details.setBackground(ColorScheme.DARK_GRAY_COLOR);
			JButton toggle = new JButton("Search details");
			toggle.setName("searchDetailsToggle");
			toggle.setFocusable(false);
			Controls.ghost(toggle);
			toggle.setIcon(new Controls.Chevron(Controls.Chevron.RIGHT, null));
			toggle.setIconTextGap(6);
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
				toggle.setIcon(new Controls.Chevron(notes.isVisible() ? Controls.Chevron.DOWN : Controls.Chevron.RIGHT, null));
				resultsPanel.revalidate();
			});
			resultsPanel.add(left(details));
		}
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
		int locations = plugin.getOwnedItems().getStoredLocations().size();
		return "Owned items: " + plugin.getOwnedItems().count() + " tracked from bank, inventory and equipment"
			+ (locations == 0 ? "" : " and " + locations + " other storage locations")
			+ (manual == 0 ? "" : " + " + manual + " marked by hand") + ". "
			+ (plugin.getOwnedItems().isStorageConnected() ? "Other storage supplied by Dude, Where's My Stuff?"
				: "Enable or update Dude, Where's My Stuff? to include its tracked storage.");
	}

	/** Owned items or their recorded storage locations changed. */
	public void onManualOwnedChanged()
	{
		updateOwnedStatus();
		constraints.refreshOwned();
	}

	public void updateOwnedStatus()
	{
		// The tracked count is listed under Search details; only the missing bank needs a standing prompt.
		boolean known = plugin.getOwnedItems().isBankKnown();
		ownedLabel.setText(known ? "" : html(plugin.getOwnedItems().isStorageConnected()
			? "Other storage connected. Open your bank once to include bank items."
			: "Open your bank once to use your owned items."));
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
			settingsPanel.refresh();
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
			List<RaidPlanType> raids = RaidPlanType.search(query);
			for (RaidPlanType raid : raids) { suggestions.add(raidSuggestionRow(raid)); }
			List<MonsterGroup> matches = data.searchMonsterGroups(query, MAX_SUGGESTIONS - raids.size(), bossesButton.isSelected());
			for (MonsterGroup group : matches)
			{
				suggestions.add(suggestionRow(group));
			}
			none = matches.isEmpty() && raids.isEmpty() && !query.trim().isEmpty();
		}
		if (none)
		{
			boolean hiddenByFilter = bossesButton.isSelected() && !data.searchMonsterGroups(query, 1, false).isEmpty();
			noMatches.setText(html(noMatchesMessage(hiddenByFilter, selected == null ? null : selected.getDisplayName())));
			noMatches.setToolTipText(hiddenByFilter ? "Click to search all monsters" : null);
		}
		noMatches.setVisible(none);
		Controls.outlineList(suggestions);
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

	private JPanel raidSuggestionRow(RaidPlanType raid)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setName("raidSearch:" + raid.name());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 6, 3, 6));
		JLabel name = new JLabel(html(escape(raid.toString())));
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(ColorScheme.BRAND_ORANGE);
		row.add(name, BorderLayout.CENTER);
		row.setToolTipText("Full raid with a shared equipment kit and inventory");
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseEntered(MouseEvent e) { row.setBackground(Controls.ROW_HOVER); }
			@Override public void mouseExited(MouseEvent e) { row.setBackground(ColorScheme.DARKER_GRAY_COLOR); }
			@Override public void mouseClicked(MouseEvent e) { selectRaid(raid); }
		});
		return row;
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
				row.setBackground(Controls.ROW_HOVER);
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
