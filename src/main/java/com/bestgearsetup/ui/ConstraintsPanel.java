package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.calc.SpellFilter;
import com.bestgearsetup.calc.SpellRule;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;

/**
 * Editor for gear rules: locks, excluded items, and items marked as owned by hand. Each is a card
 * listing the current rules above a search for adding another.
 */
class ConstraintsPanel extends JPanel
{
	private static final int MAX_SUGGESTIONS = 8;
	private static final Color OWNED_COLOR = new Color(0x6BD56B);
	private static final Color REMOVE_COLOR = new Color(0xFF6B6B);
	/** Wrap width inside a card, whose padding leaves less room than the sidebar's full width. */
	private static final int CARD_HTML_WIDTH = 140;
	/** An inventory item icon's size. */
	private static final Dimension ICON_SIZE = new Dimension(36, 32);

	private final BestGearSetupPlugin plugin;
	/** Null in tests, which show the rules without icons. */
	private final ItemManager itemManager;
	private final SpriteCache sprites;
	private final SidebarSection locksSection;
	private final SidebarSection excludedSection;
	private final SidebarSection blockedSpellsSection;
	private final SidebarSection forcedSpellsSection;
	private final SidebarSection ownedSection;
	private final JPanel locksList = ruleList();
	private final JPanel excludedList = ruleList();
	private final JPanel blockedSpellsList = ruleList();
	private final JPanel forcedSpellsList = ruleList();
	private final JPanel ownedList = ruleList();
	private final JComboBox<String> lockSlot = new JComboBox<>();
	private final IconTextField lockSearch = new IconTextField();
	private final JPanel lockSuggestions = new JPanel();
	private final IconTextField excludeSearch = new IconTextField();
	private final JPanel excludeSuggestions = new JPanel();
	private final IconTextField blockedSpellsSearch = new IconTextField();
	private final JPanel blockedSpellsSuggestions = new JPanel();
	private final IconTextField forcedSpellsSearch = new IconTextField();
	private final JPanel forcedSpellsSuggestions = new JPanel();
	private final IconTextField ownedSearch = new IconTextField();
	private final JPanel ownedSuggestions = new JPanel();

	ConstraintsPanel(BestGearSetupPlugin plugin)
	{
		this(plugin, new SidebarSection.Accordion(), null, null);
	}

	ConstraintsPanel(BestGearSetupPlugin plugin, SidebarSection.Accordion accordion, ItemManager itemManager, SpriteCache sprites)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.sprites = sprites;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		locksSection = card(accordion, "locks", "Locks");
		JPanel body = locksSection.getContent();
		body.add(hint("Always use an item, or keep a slot empty or filled. Right-click an item in a result for more."));
		body.add(transparent(locksList));
		body.add(caption("Add a lock"));
		lockSlot.setName("lockSlot");
		lockSlot.setToolTipText("Slot to search");
		lockSlot.addItem("Any slot");
		for (Slot slot : Slot.values())
		{
			lockSlot.addItem(slot.getDisplayName());
		}
		lockSlot.addActionListener(e -> updateLockSuggestions());
		Controls.field(lockSlot);
		JPanel slotRow = transparent(lockSlot);
		slotRow.setBorder(new EmptyBorder(0, 0, 4, 0));
		body.add(slotRow);
		lockSearch.setName("lockSearch");
		addSearch(body, lockSearch, lockSuggestions, "lockSuggestions", "Search items to lock...",
			"Search an item to lock its slot to", this::updateLockSuggestions);

		excludedSection = card(accordion, "exclusions", "Excluded items");
		body = excludedSection.getContent();
		body.add(hint("Never used in a setup. Right-click an item in a result to exclude it or all its variants."));
		body.add(transparent(excludedList));
		body.add(caption("Exclude an item"));
		excludeSearch.setName("excludeSearch");
		addSearch(body, excludeSearch, excludeSuggestions, "excludeSuggestions", "Search items to exclude...",
			"Search an item the search should never use", this::updateExcludeSuggestions);

		blockedSpellsSection = card(accordion, "blockedSpells", "Blocked spells");
		body = blockedSpellsSection.getContent();
		body.add(hint("Never autocast these spells."));
		body.add(transparent(blockedSpellsList));
		body.add(caption("Block a spell"));
		blockedSpellsSearch.setName("blockedSpellsSearch");
		addSearch(body, blockedSpellsSearch, blockedSpellsSuggestions, "blockedSpellsSuggestions",
			"Search spells to block...", "Search a spell to block", this::updateBlockedSpellSuggestions);

		forcedSpellsSection = card(accordion, "forcedSpells", "Forced spells");
		forcedSpellsSection.setToolTip("Only these spells are autocast. Powered staves and salamanders are still used.");
		body = forcedSpellsSection.getContent();
		body.add(hint("Only these spells are autocast. Powered staves and salamanders are still used."));
		body.add(transparent(forcedSpellsList));
		body.add(caption("Force a spell"));
		forcedSpellsSearch.setName("forcedSpellsSearch");
		addSearch(body, forcedSpellsSearch, forcedSpellsSuggestions, "forcedSpellsSuggestions",
			"Search spells to force...", "Search a spell to force", this::updateForcedSpellSuggestions);

		ownedSection = card(accordion, "extraOwnedItems", "Extra owned items");
		body = ownedSection.getContent();
		body.add(hint("For items the bank scan can't see, e.g. the POH costume room."));
		body.add(transparent(ownedList));
		body.add(caption("Mark an item as owned"));
		ownedSearch.setName("ownedSearch");
		addSearch(body, ownedSearch, ownedSuggestions, "ownedSuggestions", "Search items you own...",
			"Search an item to mark as owned", this::updateOwnedSuggestions);

		JLabel more = new JLabel(BestGearSetupPanel.html("Persistent preferences (equipment, styles, experience and fill slots) are in "
			+ "<b>Settings</b>."));
		BestGearSetupPanel.setSmall(more, ColorScheme.LIGHT_GRAY_COLOR);
		more.setBorder(new EmptyBorder(2, 2, 0, 0));
		add(BestGearSetupPanel.left(more));
	}

	private SidebarSection card(SidebarSection.Accordion accordion, String key, String title)
	{
		SidebarSection section = accordion.create(key, title);
		section.showAsEditor();
		add(BestGearSetupPanel.left(section));
		return section;
	}

	/** Limit the lock search to a slot (null for any) and put the cursor in it. */
	void startLockSearch(Slot slot)
	{
		lockSlot.setSelectedIndex(slot == null ? 0 : slot.ordinal() + 1);
		lockSearch.setText("");
		lockSearch.requestFocusInWindow();
	}

	private Slot selectedLockSlot()
	{
		int index = lockSlot.getSelectedIndex();
		return index <= 0 ? null : Slot.values()[index - 1];
	}

	/** Rebuild from the plugin's current state (after data load or config change). */
	void refresh()
	{
		GameData data = plugin.getGameData();
		rebuildLocks(data);
		rebuildExcluded(data);
		rebuildSpells(plugin.getSpellFilter());
		rebuildOwned(data);
	}

	/** Rebuild the owned list after the manual list changed. */
	void refreshOwned()
	{
		rebuildOwned(plugin.getGameData());
	}

	private void rebuildLocks(GameData data)
	{
		locksList.removeAll();
		Map<Slot, SlotLock> locks = plugin.getLocks();
		for (Map.Entry<Slot, SlotLock> e : locks.entrySet())
		{
			Slot slot = e.getKey();
			SlotLock lock = e.getValue();
			String slotName = slot.getDisplayName();
			Runnable unlock = () -> plugin.setLock(slot, null);
			switch (lock.getKind())
			{
				case EMPTY:
					locksList.add(ruleRow(slotIcon(slot), "Keep empty", slotName, "Unlock " + slotName, unlock));
					break;
				case FILL:
					locksList.add(ruleRow(slotIcon(slot), "Always fill", slotName, "Unlock " + slotName, unlock));
					break;
				default:
					GearItem item = data == null ? null : data.getItem(slot, lock.getItemId());
					String name = item == null ? "Item " + lock.getItemId() : GameData.titleCase(item.getName());
					locksList.add(ruleRow(itemIcon(lock.getItemId()), name, slotName, "Unlock " + slotName, unlock));
					break;
			}
		}
		finishList(locksList, locks.size(), "No locks.", "Clear locks", plugin::clearLocks);
		locksSection.setSummary(locks.size());
	}

	private void rebuildExcluded(GameData data)
	{
		excludedList.removeAll();
		Set<Integer> excluded = plugin.getExcluded();
		for (int id : excluded)
		{
			GearItem item = data == null ? null : data.findItem(id);
			String name = item == null ? "Item " + id : GameData.titleCase(item.getName());
			excludedList.add(ruleRow(itemIcon(id), name, item == null ? null : item.getSlot().getDisplayName(),
				"Use " + name + " again", () -> plugin.setExcluded(id, false)));
		}
		finishList(excludedList, excluded.size(), "No excluded items.", "Clear exclusions", plugin::clearExclusions);
		excludedSection.setSummary(excluded.size());
	}

	private void rebuildOwned(GameData data)
	{
		ownedList.removeAll();
		Set<Integer> manual = plugin.getOwnedItems().getManual();
		for (int id : manual)
		{
			GearItem item = data == null ? null : data.findItem(id);
			String name = item == null ? "Item " + id : GameData.titleCase(item.getName());
			ownedList.add(ruleRow(itemIcon(id), name, item == null ? null : item.getSlot().getDisplayName(),
				"No longer mark " + name + " as owned", () -> plugin.setManualOwned(id, false)));
		}
		finishList(ownedList, manual.size(), "None marked.", null, null);
		ownedSection.setSummary(manual.size());
	}

	/** Rebuild the blocked and forced cards from the current spell rules. */
	private void rebuildSpells(SpellFilter filter)
	{
		Map<String, Spell> known = plugin.getCombatSpells().stream()
			.collect(Collectors.toMap(Spell::getLowerName, spell -> spell, (first, ignored) -> first));
		blockedSpellsList.removeAll();
		for (String name : filter.getBlocked())
		{
			blockedSpellsList.add(spellRow(name, known.get(name)));
		}
		finishList(blockedSpellsList, filter.getBlocked().size(), "No blocked spells",
			"Clear blocked spells", plugin::clearBlockedSpells);
		blockedSpellsSection.setSummary(filter.getBlocked().size());

		forcedSpellsList.removeAll();
		for (String name : filter.getForced())
		{
			forcedSpellsList.add(spellRow(name, known.get(name)));
		}
		finishList(forcedSpellsList, filter.getForced().size(), "No forced spells",
			"Clear forced spells", plugin::clearForcedSpells);
		forcedSpellsSection.setSummary(filter.getForced().size());
		updateBlockedSpellSuggestions();
		updateForcedSpellSuggestions();
	}

	/** A spell rule shows its spellbook, or flags a stale name. */
	private JPanel spellRow(String name, Spell spell)
	{
		String title = GameData.titleCase(name);
		String detail = spell == null ? "not in game data" : spellbookLabel(spell);
		return ruleRow(iconLabel(), title, detail, "Allow " + title,
			() -> plugin.setSpellRule(name, SpellRule.ALLOW));
	}

	/** Show a usable label when a spell has no spellbook in game data. */
	private static String spellbookLabel(Spell spell)
	{
		String spellbook = spell.getSpellbook();
		return spellbook == null ? "unknown spellbook" : GameData.titleCase(spellbook);
	}

	/** An empty list says so; a list of several rules ends with a button that clears them all. */
	private static void finishList(JPanel list, int count, String empty, String clearText, Runnable clear)
	{
		if (count == 0)
		{
			JLabel none = new JLabel(empty);
			BestGearSetupPanel.setSmall(none, Controls.DISABLED_TEXT);
			none.setBorder(new EmptyBorder(0, 0, 4, 0));
			list.add(transparent(none));
		}
		else if (clear != null && count > 1)
		{
			JButton button = new JButton(clearText);
			Controls.ghost(button);
			button.setHorizontalAlignment(SwingConstants.RIGHT);
			button.addActionListener(e -> clear.run());
			list.add(transparent(button));
		}
		list.revalidate();
		list.repaint();
	}

	private void updateLockSuggestions()
	{
		lockSuggestions.removeAll();
		GameData data = plugin.getGameData();
		if (data != null)
		{
			Slot only = selectedLockSlot();
			for (GearItem item : data.searchItems(lockSearch.getText(), MAX_SUGGESTIONS, only))
			{
				String name = GameData.titleCase(item.getName());
				String slotName = item.getSlot().getDisplayName();
				lockSuggestions.add(itemButton(item, only == null ? name + " (" + slotName + ")" : name,
					"Lock " + slotName.toLowerCase(Locale.ROOT) + " slot to " + name, () ->
					{
						plugin.lockItem(item);
						lockSearch.setText("");
					}));
			}
		}
		showSuggestions(lockSuggestions);
	}

	private void updateExcludeSuggestions()
	{
		excludeSuggestions.removeAll();
		GameData data = plugin.getGameData();
		if (data != null)
		{
			Set<Integer> excluded = plugin.getExcluded();
			List<GearItem> matches = data.searchItems(excludeSearch.getText(), MAX_SUGGESTIONS + excluded.size()).stream()
				.filter(item -> !excluded.contains(item.getId()))
				.limit(MAX_SUGGESTIONS)
				.collect(Collectors.toList());
			for (GearItem item : matches)
			{
				String name = GameData.titleCase(item.getName());
				excludeSuggestions.add(itemButton(item, name + " (" + item.getSlot().getDisplayName() + ")", "Exclude " + name, () ->
				{
					plugin.setExcluded(item.getId(), true);
					excludeSearch.setText("");
				}));
			}
		}
		showSuggestions(excludeSuggestions);
	}

	private void updateOwnedSuggestions()
	{
		ownedSuggestions.removeAll();
		GameData data = plugin.getGameData();
		if (data != null)
		{
			for (GearItem item : data.searchItems(ownedSearch.getText(), MAX_SUGGESTIONS))
			{
				ownedSuggestions.add(itemButton(item, GameData.titleCase(item.getName()), "Mark as owned", () ->
				{
					plugin.setManualOwned(item.getId(), true);
					ownedSearch.setText("");
				}));
			}
		}
		showSuggestions(ownedSuggestions);
	}

	/** Suggest spells that are not already in the selected rule list. */
	private void updateBlockedSpellSuggestions()
	{
		updateSpellSuggestions(blockedSpellsSearch, blockedSpellsSuggestions, plugin.getSpellFilter().getBlocked(), SpellRule.BLOCK);
	}

	private void updateForcedSpellSuggestions()
	{
		updateSpellSuggestions(forcedSpellsSearch, forcedSpellsSuggestions, plugin.getSpellFilter().getForced(), SpellRule.FORCE);
	}

	private void updateSpellSuggestions(IconTextField search, JPanel suggestions, Set<String> existing, SpellRule rule)
	{
		suggestions.removeAll();
		String query = search.getText().trim().toLowerCase(Locale.ROOT);
		plugin.getCombatSpells().stream()
			.filter(spell -> !existing.contains(spell.getLowerName()) && spell.getLowerName().contains(query))
			.limit(MAX_SUGGESTIONS)
			.forEach(spell ->
			{
				String name = GameData.titleCase(spell.getName());
				JButton button = new JButton(name + " (" + spellbookLabel(spell) + ")");
				Controls.listRow(button);
				button.setToolTipText((rule == SpellRule.BLOCK ? "Block " : "Force ") + name);
				button.addActionListener(e ->
				{
					plugin.setSpellRule(spell.getLowerName(), rule);
					search.setText("");
				});
				suggestions.add(BestGearSetupPanel.left(button));
			});
		showSuggestions(suggestions);
	}

	private static void showSuggestions(JPanel suggestions)
	{
		Controls.outlineList(suggestions);
		suggestions.revalidate();
		suggestions.repaint();
	}

	/** A search suggestion with the item's icon; items you own are green. */
	private JPanel itemButton(GearItem item, String text, String tooltip, Runnable onClick)
	{
		JButton row = new JButton(text);
		Controls.listRow(row);
		row.setFont(Controls.plain(FontManager.getRunescapeSmallFont()));
		row.setForeground(plugin.owns(item) ? OWNED_COLOR : Color.WHITE);
		row.setBorder(new EmptyBorder(1, 4, 1, 6));
		row.setIconTextGap(6);
		if (itemManager != null)
		{
			itemManager.getImage(item.getId()).addTo(row);
		}
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setToolTipText(tooltip);
		row.addActionListener(e -> onClick.run());
		return BestGearSetupPanel.left(row);
	}

	/** One rule: its icon, name and slot in a recessed tile, with a button that removes it. */
	private static JPanel ruleRow(JLabel icon, String name, String detail, String removeTip, Runnable onRemove)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(Controls.WELL);
		row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Controls.OUTLINE),
			new EmptyBorder(1, 2, 1, 2)));
		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel(new GridLayout(0, 1));
		text.setOpaque(false);
		JLabel title = new JLabel(name);
		BestGearSetupPanel.setSmall(title, Color.WHITE);
		text.add(title);
		if (detail != null)
		{
			JLabel slot = new JLabel(detail);
			BestGearSetupPanel.setSmall(slot, ColorScheme.LIGHT_GRAY_COLOR);
			text.add(slot);
		}
		text.setToolTipText(detail == null ? name : name + " (" + detail + ")");
		row.add(text, BorderLayout.CENTER);

		JButton remove = new JButton("×");
		Controls.ghost(remove);
		remove.setForeground(REMOVE_COLOR);
		remove.setHorizontalAlignment(SwingConstants.CENTER);
		remove.setMargin(new Insets(0, 6, 0, 6));
		remove.getAccessibleContext().setAccessibleName(removeTip);
		remove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		remove.setToolTipText(removeTip);
		remove.addActionListener(e -> onRemove.run());
		row.add(remove, BorderLayout.EAST);

		JPanel wrap = transparent(row);
		wrap.setBorder(new EmptyBorder(0, 0, 4, 0));
		return wrap;
	}

	private JLabel itemIcon(int itemId)
	{
		JLabel label = iconLabel();
		if (itemManager != null)
		{
			itemManager.getImage(itemId).addTo(label);
		}
		return label;
	}

	/** The equipment screen's empty-slot icon, for locks that keep a slot empty or filled. */
	private JLabel slotIcon(Slot slot)
	{
		JLabel label = iconLabel();
		if (sprites != null)
		{
			showSprite(label, EquipmentGrid.placeholderSprite(slot));
		}
		return label;
	}

	/** Show the sprite now if it is loaded, or once it has loaded. */
	private void showSprite(JLabel label, int sprite)
	{
		BufferedImage image = sprites.get(sprite, () -> showSprite(label, sprite));
		if (image != null)
		{
			label.setIcon(new ImageIcon(image));
		}
	}

	private static JLabel iconLabel()
	{
		JLabel label = new JLabel();
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setPreferredSize(ICON_SIZE);
		return label;
	}

	private static JLabel hint(String text)
	{
		JLabel label = new JLabel(BestGearSetupPanel.html(text, CARD_HTML_WIDTH));
		BestGearSetupPanel.setSmall(label, ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(0, 0, 6, 0));
		return label;
	}

	private static JPanel caption(String text)
	{
		JLabel label = Controls.caption(text);
		label.setBorder(new EmptyBorder(4, 0, 2, 0));
		return transparent(label);
	}

	private static void addSearch(JPanel body, IconTextField field, JPanel suggestions, String suggestionsName,
		String placeholder, String tooltip, Runnable onChange)
	{
		field.setIcon(IconTextField.Icon.SEARCH);
		field.setPreferredSize(new Dimension(0, 30));
		Controls.search(field, placeholder);
		field.setToolTipText(tooltip);
		onTextChange(field, onChange);
		body.add(transparent(field));
		suggestions.setName(suggestionsName);
		suggestions.setLayout(new BoxLayout(suggestions, BoxLayout.Y_AXIS));
		suggestions.setBackground(Controls.WELL);
		body.add(transparent(suggestions));
	}

	private static JPanel ruleList()
	{
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		return list;
	}

	/** Lay a component out full width without painting over the card behind it. */
	private static JPanel transparent(Component c)
	{
		JPanel p = BestGearSetupPanel.left(c);
		p.setOpaque(false);
		return p;
	}

	private static void onTextChange(IconTextField field, Runnable action)
	{
		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				action.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				action.run();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				action.run();
			}
		});
	}
}
