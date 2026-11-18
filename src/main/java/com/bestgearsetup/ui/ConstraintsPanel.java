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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
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
	private final JComboBox<String> lockSlot = new JComboBox<>();
	private final RuleCard locks;
	private final RuleCard exclusions;
	private final RuleCard forcedSpells;
	private final RuleCard blockedSpells;
	private final RuleCard owned;

	ConstraintsPanel(BestGearSetupPlugin plugin)
	{
		this(plugin, null, null);
	}

	ConstraintsPanel(BestGearSetupPlugin plugin, ItemManager itemManager, SpriteCache sprites)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.sprites = sprites;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		SidebarSection.Accordion accordion = new SidebarSection.Accordion(true);
		add(BestGearSetupPanel.left(accordion.expandCollapseAll("gear")));

		locks = new RuleCard(accordion, "locks", "Locked items",
			"Always use an item, or keep a slot empty or filled. Right-click an item in a result for more.", "Add a lock");
		lockSlot.setName("lockSlot");
		lockSlot.setToolTipText("Slot to search");
		lockSlot.addItem("Any slot");
		for (Slot slot : Slot.values())
		{
			lockSlot.addItem(slot.getDisplayName());
		}
		lockSlot.addActionListener(e -> locks.suggest());
		Controls.field(lockSlot);
		JPanel slotRow = transparent(lockSlot);
		slotRow.setBorder(new EmptyBorder(0, 0, 4, 0));
		locks.section.getContent().add(slotRow);
		locks.addSearch("lock", "Search items to lock...", "Find an item to lock its slot to", this::suggestLocks);

		exclusions = new RuleCard(accordion, "exclusions", "Excluded items",
			"Never used in a setup. Right-click an item in a result to exclude it or all its variants.", "Exclude an item");
		exclusions.addSearch("exclude", "Search items to exclude...", "Find an item the search should never use",
			this::suggestExclusions);

		forcedSpells = new RuleCard(accordion, "forcedSpells", "Allowed spells",
			"Only these spells are cast. Powered staves and salamanders are still used.", "Allow a spell");
		forcedSpells.section.setToolTip("Only these spells; powered staves and salamanders still work");
		forcedSpells.addSearch("forcedSpells", "Search spells to allow...", "Find a spell to always cast",
			() -> suggestSpells(forcedSpells, plugin.getSpellFilter().getForced(), SpellRule.FORCE));

		blockedSpells = new RuleCard(accordion, "blockedSpells", "Excluded spells", "Never cast these spells.",
			"Exclude a spell");
		blockedSpells.addSearch("blockedSpells", "Search spells to exclude...", "Find a spell the search should never cast",
			() -> suggestSpells(blockedSpells, plugin.getSpellFilter().getBlocked(), SpellRule.BLOCK));

		owned = new RuleCard(accordion, "extraOwnedItems", "Extra owned items",
			"For items the bank scan can't see, e.g. the POH costume room.", "Mark an item as owned");
		owned.addSearch("owned", "Search items you own...", "Find an item to mark as owned", this::suggestOwned);

		JLabel more = new JLabel(BestGearSetupPanel.html("Persistent preferences (equipment, styles, experience and fill slots) are in "
			+ "<b>Settings</b>."));
		BestGearSetupPanel.setSmall(more, ColorScheme.LIGHT_GRAY_COLOR);
		more.setBorder(new EmptyBorder(2, 2, 0, 0));
		add(BestGearSetupPanel.left(more));
	}

	/** Limit the lock search to a slot (null for any) and put the cursor in it. */
	void startLockSearch(Slot slot)
	{
		locks.section.setExpanded(true);
		lockSlot.setSelectedIndex(slot == null ? 0 : slot.ordinal() + 1);
		locks.search.setText("");
		locks.search.requestFocusInWindow();
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
		locks.suggest();
		exclusions.suggest();
		owned.suggest();
	}

	/** Rebuild the owned list after the manual list changed. */
	void refreshOwned()
	{
		rebuildOwned(plugin.getGameData());
		owned.suggest();
	}

	private void rebuildLocks(GameData data)
	{
		locks.list.removeAll();
		Map<Slot, SlotLock> current = plugin.getLocks();
		current.forEach((slot, lock) ->
		{
			String slotName = slot.getDisplayName();
			JLabel icon = lock.getKind() == SlotLock.Kind.ITEM ? itemIcon(lock.getItemId()) : slotIcon(slot);
			locks.list.add(ruleRow(icon, lockLabel(data, slot, lock), slotName, "Unlock " + slotName,
				() -> plugin.setLock(slot, null)));
		});
		locks.finish(current.size(), "No locks.", "Clear locks", plugin::clearLocks);
	}

	/** What the lock list calls a lock: "Keep empty", "Always fill", or the locked item's name. */
	private static String lockLabel(GameData data, Slot slot, SlotLock lock)
	{
		switch (lock.getKind())
		{
			case EMPTY:
				return "Keep empty";
			case FILL:
				return "Always fill";
			default:
				GearItem item = data == null ? null : data.getItem(slot, lock.getItemId());
				return item == null ? "Item " + lock.getItemId() : GameData.titleCase(item.getName());
		}
	}

	private void rebuildExcluded(GameData data)
	{
		exclusions.list.removeAll();
		Set<Integer> excluded = plugin.getExcluded();
		for (int id : excluded)
		{
			GearItem item = data == null ? null : data.findItem(id);
			String name = item == null ? "Item " + id : GameData.titleCase(item.getName());
			exclusions.list.add(ruleRow(itemIcon(id), name, item == null ? null : item.getSlot().getDisplayName(),
				"Use " + name + " again", () -> plugin.setExcluded(id, false)));
		}
		exclusions.finish(excluded.size(), "No excluded items.", "Clear exclusions", plugin::clearExclusions);
	}

	private void rebuildOwned(GameData data)
	{
		owned.list.removeAll();
		Set<Integer> manual = plugin.getOwnedItems().getManual();
		for (int id : manual)
		{
			GearItem item = data == null ? null : data.findItem(id);
			String name = item == null ? "Item " + id : GameData.titleCase(item.getName());
			owned.list.add(ruleRow(itemIcon(id), name, item == null ? null : item.getSlot().getDisplayName(),
				"Unmark " + name, () -> plugin.setManualOwned(id, false)));
		}
		owned.finish(manual.size(), "None marked.", null, null);
	}

	/** Rebuild the blocked and forced cards from the current spell rules. */
	private void rebuildSpells(SpellFilter filter)
	{
		Map<String, Spell> known = new HashMap<>();
		Map<String, Integer> order = new HashMap<>();
		int position = 0;
		for (Spell spell : plugin.getCombatSpells())
		{
			known.putIfAbsent(spell.getLowerName(), spell);
			order.putIfAbsent(spell.getLowerName(), position++);
		}
		listSpells(blockedSpells, filter.getBlocked(), known, order, "No excluded spells", "Clear excluded spells",
			plugin::clearBlockedSpells);
		listSpells(forcedSpells, filter.getForced(), known, order, "No allowed spells", "Clear allowed spells",
			plugin::clearForcedSpells);
		blockedSpells.suggest();
		forcedSpells.suggest();
	}

	/** List a card's spells in combat spell list order, leaving stale names in stored order. */
	private void listSpells(RuleCard card, Set<String> names, Map<String, Spell> known, Map<String, Integer> order,
		String empty, String clearText, Runnable clear)
	{
		card.list.removeAll();
		List<String> sorted = new ArrayList<>(names);
		sorted.sort(Comparator.comparingInt(name -> order.getOrDefault(name, Integer.MAX_VALUE)));
		for (String name : sorted)
		{
			card.list.add(spellRow(name, known.get(name)));
		}
		card.finish(names.size(), empty, clearText, clear);
	}

	/** A spell rule shows its level and spellbook, or flags a stale name. */
	private JPanel spellRow(String name, Spell spell)
	{
		String title = GameData.titleCase(name);
		String detail = spell == null ? "not in game data" : spellDetail(spell);
		JLabel icon = iconLabel();
		showSpellIcon(icon::setIcon, name);
		return ruleRow(icon, title, detail, "Remove " + title, () -> plugin.setSpellRule(name, SpellRule.ALLOW));
	}

	/** Show a spell's unlock level and spellbook, with fallbacks for incomplete game data. */
	private static String spellDetail(Spell spell)
	{
		String spellbook = spell.getSpellbook();
		String label = spellbook == null ? "unknown spellbook" : GameData.titleCase(spellbook);
		return spell.getLevel() > 0 ? "lvl " + spell.getLevel() + ", " + label : label;
	}

	private void suggestLocks()
	{
		GameData data = plugin.getGameData();
		if (data != null)
		{
			Slot only = selectedLockSlot();
			Map<Slot, SlotLock> current = plugin.getLocks();
			matches(data, locks, only, current.size(), item -> !isLockedTo(current.get(item.getSlot()), item)).forEach(item ->
			{
				String name = GameData.titleCase(item.getName());
				String slotName = item.getSlot().getDisplayName();
				SlotLock lock = current.get(item.getSlot());
				// The button names the item and slot; the tooltip names the lock it would replace.
				String tooltip = lock == null ? "Lock to " + name : "Replaces lock: " + lockLabel(data, item.getSlot(), lock);
				itemButton(locks, item, only == null ? name + " (" + slotName + ")" : name, tooltip, () -> plugin.lockItem(item));
			});
		}
	}

	private static boolean isLockedTo(SlotLock lock, GearItem item)
	{
		return lock != null && lock.getKind() == SlotLock.Kind.ITEM && lock.getItemId() == item.getId();
	}

	private void suggestExclusions()
	{
		GameData data = plugin.getGameData();
		if (data != null)
		{
			Set<Integer> excluded = plugin.getExcluded();
			matches(data, exclusions, null, excluded.size(), item -> !excluded.contains(item.getId())).forEach(item ->
			{
				String name = GameData.titleCase(item.getName());
				itemButton(exclusions, item, name + " (" + item.getSlot().getDisplayName() + ")", "Exclude " + name,
					() -> plugin.setExcluded(item.getId(), true));
			});
		}
	}

	private void suggestOwned()
	{
		GameData data = plugin.getGameData();
		if (data != null)
		{
			Set<Integer> manual = plugin.getOwnedItems().getManual();
			matches(data, owned, null, manual.size(), item -> !manual.contains(item.getId())).forEach(item ->
				itemButton(owned, item, GameData.titleCase(item.getName()), "Mark as owned",
					() -> plugin.setManualOwned(item.getId(), true)));
		}
	}

	/** Items matching the card's search that {@code isNew} keeps; {@code taken} more are searched to make up for those it drops. */
	private static Stream<GearItem> matches(GameData data, RuleCard card, Slot only, int taken, Predicate<GearItem> isNew)
	{
		return data.searchItems(card.search.getText(), MAX_SUGGESTIONS + taken, only).stream()
			.filter(isNew)
			.limit(MAX_SUGGESTIONS);
	}

	/** Like the item searches: nothing until the user types, then name-prefix matches before the rest. */
	private void suggestSpells(RuleCard card, Set<String> existing, SpellRule rule)
	{
		String query = card.search.getText().trim().toLowerCase(Locale.ROOT);
		if (!query.isEmpty())
		{
			plugin.getCombatSpells().stream()
				.filter(spell -> !existing.contains(spell.getLowerName()) && spell.getLowerName().contains(query))
				.sorted(Comparator.comparing((Spell spell) -> !spell.getLowerName().startsWith(query)))
				.limit(MAX_SUGGESTIONS)
				.forEach(spell ->
				{
					String name = GameData.titleCase(spell.getName());
					JButton row = suggestion(card, name + " (" + spellDetail(spell) + ")", Color.WHITE,
						(rule == SpellRule.BLOCK ? "Exclude " : "Allow ") + name, () -> plugin.setSpellRule(spell.getLowerName(), rule));
					showSpellIcon(row::setIcon, spell.getName());
				});
		}
	}

	/** A search suggestion with the item's icon; items you own are green. */
	private void itemButton(RuleCard card, GearItem item, String text, String tooltip, Runnable add)
	{
		JButton row = suggestion(card, text, plugin.owns(item) ? OWNED_COLOR : Color.WHITE, tooltip, add);
		if (itemManager != null)
		{
			itemManager.getImage(item.getId()).addTo(row);
		}
	}

	/** A row in the card's suggestions that adds a rule, then refreshes the suggestions and returns to the search. */
	private static JButton suggestion(RuleCard card, String text, Color color, String tooltip, Runnable add)
	{
		JButton row = new JButton(text);
		Controls.listRow(row);
		row.setFont(Controls.plain(FontManager.getRunescapeSmallFont()));
		row.setForeground(color);
		row.setBorder(new EmptyBorder(1, 4, 1, 6));
		row.setIconTextGap(6);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setToolTipText(tooltip);
		row.addActionListener(e ->
		{
			add.run();
			card.suggest();
			card.search.requestFocusInWindow();
		});
		card.suggestions.add(BestGearSetupPanel.left(row));
		return row;
	}

	/** The spell's spellbook icon; none for a stale name. */
	private void showSpellIcon(Consumer<Icon> setIcon, String spell)
	{
		int sprite = BestGearSetupPlugin.spellSprite(spell);
		if (sprites != null && sprite >= 0)
		{
			showSprite(setIcon, sprite);
		}
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
			showSprite(label::setIcon, EquipmentGrid.placeholderSprite(slot));
		}
		return label;
	}

	/** Show the sprite now if it is loaded, or once it has loaded. */
	private void showSprite(Consumer<Icon> setIcon, int sprite)
	{
		BufferedImage image = sprites.get(sprite, () -> showSprite(setIcon, sprite));
		if (image != null)
		{
			setIcon.accept(new ImageIcon(image));
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

	/** Lay a component out full width without painting over the card behind it. */
	private static JPanel transparent(Component c)
	{
		JPanel p = BestGearSetupPanel.left(c);
		p.setOpaque(false);
		return p;
	}

	/** A card listing the current rules of one kind above a search for adding another. */
	private final class RuleCard
	{
		private final SidebarSection section;
		private final JPanel list = new JPanel();
		private final IconTextField search = new IconTextField();
		private final JPanel suggestions = new JPanel();
		/** Adds a suggestion for each match of the search's text. */
		private Runnable suggester;

		private RuleCard(SidebarSection.Accordion accordion, String key, String title, String hintText, String captionText)
		{
			section = accordion.create(key, title);
			add(BestGearSetupPanel.left(section));
			list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
			list.setOpaque(false);
			JPanel body = section.getContent();
			body.add(hint(hintText));
			body.add(transparent(list));
			body.add(caption(captionText));
		}

		/** The search below the card's rules, named {@code name}Search; Escape clears it. */
		private void addSearch(String name, String placeholder, String tooltip, Runnable suggester)
		{
			this.suggester = suggester;
			search.setName(name + "Search");
			Controls.search(search, 0, placeholder, this::suggest);
			search.setToolTipText(tooltip);
			search.addKeyListener(new KeyAdapter()
			{
				@Override
				public void keyPressed(KeyEvent e)
				{
					if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
					{
						search.setText("");
					}
				}
			});
			JPanel body = section.getContent();
			body.add(transparent(search));
			suggestions.setName(name + "Suggestions");
			suggestions.setLayout(new BoxLayout(suggestions, BoxLayout.Y_AXIS));
			suggestions.setBackground(Controls.WELL);
			body.add(transparent(suggestions));
		}

		/** Show the suggestions for the search's text. */
		private void suggest()
		{
			suggestions.removeAll();
			suggester.run();
			Controls.outlineList(suggestions);
			suggestions.revalidate();
			suggestions.repaint();
		}

		/**
		 * Count the listed rules in the header. An empty list says so; a list of several rules ends with a button
		 * that clears them all.
		 */
		private void finish(int count, String empty, String clearText, Runnable clear)
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
			section.setSummary(count);
		}
	}
}
