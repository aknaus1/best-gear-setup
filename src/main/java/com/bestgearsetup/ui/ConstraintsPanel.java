package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;

/**
 * Flat editor for gear rules and items marked as owned by hand.
 */
class ConstraintsPanel extends JPanel
{
	private final BestGearSetupPlugin plugin;
	private final SidebarSection gearSection;
	private final SidebarSection ownedSection;
	private final JPanel locksList = new JPanel();
	private final JPanel ownedList = new JPanel();
	private final IconTextField ownedSearch = new IconTextField();
	private final JPanel ownedSuggestions = new JPanel();
	private final JComboBox<String> lockSlot = new JComboBox<>();
	private final IconTextField lockSearch = new IconTextField();
	private final JPanel lockSuggestions = new JPanel();

	ConstraintsPanel(BestGearSetupPlugin plugin)
	{
		this(plugin, new SidebarSection.Accordion());
	}

	ConstraintsPanel(BestGearSetupPlugin plugin, SidebarSection.Accordion accordion)
	{
		this.plugin = plugin;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		gearSection = accordion.create("gearRules", "Locks & exclusions");
		JPanel body = gearSection.getContent();
		gearSection.showAsEditor();
		add(BestGearSetupPanel.left(gearSection));
		JLabel lockHint = new JLabel(BestGearSetupPanel.html("Right-click an item in a result to lock its slot, exclude it, or mark it owned."));
		BestGearSetupPanel.setSmall(lockHint, ColorScheme.LIGHT_GRAY_COLOR);
		body.add(BestGearSetupPanel.left(lockHint));
		locksList.setLayout(new BoxLayout(locksList, BoxLayout.Y_AXIS));
		locksList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.add(BestGearSetupPanel.left(locksList));
		JLabel lockAny = new JLabel("Lock any item:");
		BestGearSetupPanel.setSmall(lockAny, Color.WHITE);
		lockAny.setBorder(new EmptyBorder(4, 0, 2, 0));
		body.add(BestGearSetupPanel.left(lockAny));
		lockSlot.setName("lockSlot");
		lockSlot.setToolTipText("Slot to search");
		lockSlot.addItem("Any slot");
		for (Slot slot : Slot.values())
		{
			lockSlot.addItem(slot.getDisplayName());
		}
		lockSlot.addActionListener(e -> updateLockSuggestions());
		body.add(BestGearSetupPanel.left(lockSlot));
		styleSearch(lockSearch, "Search an item to lock its slot to");
		lockSearch.setName("lockSearch");
		onTextChange(lockSearch, this::updateLockSuggestions);
		body.add(BestGearSetupPanel.left(lockSearch));
		lockSuggestions.setName("lockSuggestions");
		lockSuggestions.setLayout(new BoxLayout(lockSuggestions, BoxLayout.Y_AXIS));
		lockSuggestions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.add(BestGearSetupPanel.left(lockSuggestions));

		ownedSection = accordion.create("extraOwnedItems", "Extra owned items");
		body = ownedSection.getContent();
		ownedSection.showAsEditor();
		add(BestGearSetupPanel.left(ownedSection));
		JLabel ownedHint = new JLabel(BestGearSetupPanel.html("For items the bank scan can't see, e.g. the POH costume room."));
		BestGearSetupPanel.setSmall(ownedHint, ColorScheme.LIGHT_GRAY_COLOR);
		body.add(BestGearSetupPanel.left(ownedHint));
		styleSearch(ownedSearch, "Search an item to mark as owned");
		onTextChange(ownedSearch, this::updateOwnedSuggestions);
		body.add(BestGearSetupPanel.left(ownedSearch));
		ownedSuggestions.setLayout(new BoxLayout(ownedSuggestions, BoxLayout.Y_AXIS));
		ownedSuggestions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.add(BestGearSetupPanel.left(ownedSuggestions));
		ownedList.setLayout(new BoxLayout(ownedList, BoxLayout.Y_AXIS));
		ownedList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.add(BestGearSetupPanel.left(ownedList));

		JLabel more = new JLabel(BestGearSetupPanel.html("Persistent preferences (equipment, styles, experience and fill slots) are in "
			+ "RuneLite's settings under <b>Best Gear Setup</b>."));
		BestGearSetupPanel.setSmall(more, ColorScheme.LIGHT_GRAY_COLOR);
		more.setBorder(new EmptyBorder(6, 0, 0, 0));
		gearSection.getContent().add(BestGearSetupPanel.left(more));
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

	private void updateLockSuggestions()
	{
		lockSuggestions.removeAll();
		GameData data = plugin.getGameData();
		if (data != null)
		{
			Slot only = selectedLockSlot();
			for (GearItem item : data.searchItems(lockSearch.getText(), 8, only))
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
		lockSuggestions.revalidate();
		lockSuggestions.repaint();
	}

	/** Rebuild from the plugin's current state (after data load or config change). */
	void refresh()
	{
		GameData data = plugin.getGameData();
		rebuildLocks(data);
		rebuildOwned(data);
		gearSection.setSummary(plugin.getLocks().size() + plugin.getExcluded().size());
	}

	private void rebuildLocks(GameData data)
	{
		locksList.removeAll();
		Map<Slot, SlotLock> locks = plugin.getLocks();
		Set<Integer> excluded = plugin.getExcluded();
		for (Map.Entry<Slot, SlotLock> e : locks.entrySet())
		{
			Slot slot = e.getKey();
			SlotLock lock = e.getValue();
			String what;
			switch (lock.getKind())
			{
				case EMPTY:
					what = "empty";
					break;
				case FILL:
					what = "always fill";
					break;
				default:
					GearItem item = data == null ? null : data.getItem(slot, lock.getItemId());
					what = item == null ? "item " + lock.getItemId() : GameData.titleCase(item.getName());
					break;
			}
			locksList.add(removableRow("Lock " + slot.getDisplayName() + ": " + what, () -> plugin.setLock(slot, null)));
		}
		for (int id : excluded)
		{
			GearItem item = data == null ? null : data.findItem(id);
			String name = item == null ? "item " + id : GameData.titleCase(item.getName());
			locksList.add(removableRow("Excluded: " + name, () -> plugin.setExcluded(id, false)));
		}
		if (!locks.isEmpty() || !excluded.isEmpty())
		{
			JButton clear = new JButton("Clear all");
			clear.addActionListener(e -> plugin.clearLocksAndExclusions());
			locksList.add(BestGearSetupPanel.left(clear));
		}
		else
		{
			JLabel none = new JLabel("None");
			BestGearSetupPanel.setSmall(none, ColorScheme.LIGHT_GRAY_COLOR);
			locksList.add(BestGearSetupPanel.left(none));
		}
		locksList.revalidate();
		locksList.repaint();
	}

	/** Rebuild the "Marked as owned" list after the manual list changed. */
	void refreshOwned()
	{
		rebuildOwned(plugin.getGameData());
	}

	private void rebuildOwned(GameData data)
	{
		ownedSection.setSummary(plugin.getOwnedItems().getManual().size());
		ownedList.removeAll();
		for (int id : plugin.getOwnedItems().getManual())
		{
			GearItem item = data == null ? null : data.findItem(id);
			String name = item == null ? "item " + id : GameData.titleCase(item.getName());
			ownedList.add(removableRow(name, () -> plugin.setManualOwned(id, false)));
		}
		ownedList.revalidate();
		ownedList.repaint();
	}

	private void updateOwnedSuggestions()
	{
		ownedSuggestions.removeAll();
		GameData data = plugin.getGameData();
		if (data != null)
		{
			List<GearItem> matches = data.searchItems(ownedSearch.getText(), 8);
			for (GearItem item : matches)
			{
				ownedSuggestions.add(itemButton(item, GameData.titleCase(item.getName()), "Mark as owned", () ->
				{
					plugin.setManualOwned(item.getId(), true);
					ownedSearch.setText("");
				}));
			}
		}
		ownedSuggestions.revalidate();
		ownedSuggestions.repaint();
	}

	/** A search suggestion; items you own are green. */
	private JPanel itemButton(GearItem item, String text, String tooltip, Runnable onClick)
	{
		JButton row = new JButton(text);
		row.setFont(FontManager.getRunescapeSmallFont());
		row.setForeground(plugin.owns(item) ? new Color(0x6BD56B) : Color.WHITE);
		row.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
		row.setBorder(new EmptyBorder(3, 6, 3, 6));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setToolTipText(tooltip);
		row.addActionListener(e -> onClick.run());
		return BestGearSetupPanel.left(row);
	}

	private static void styleSearch(IconTextField field, String tooltip)
	{
		field.setIcon(IconTextField.Icon.SEARCH);
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		field.setToolTipText(tooltip);
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

	private static JPanel removableRow(String text, Runnable onRemove)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 6, 2, 4));
		JLabel label = new JLabel(text);
		BestGearSetupPanel.setSmall(label, Color.WHITE);
		label.setToolTipText(text);
		row.add(label, BorderLayout.CENTER);
		JButton remove = new JButton("x");
		remove.setFont(FontManager.getRunescapeSmallFont());
		remove.setForeground(new Color(0xFF6B6B));
		remove.setMargin(new java.awt.Insets(0, 3, 0, 3));
		remove.getAccessibleContext().setAccessibleName("Remove " + text);
		remove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		remove.setToolTipText("Remove");
		remove.addActionListener(e -> onRemove.run());
		row.add(remove, BorderLayout.EAST);
		JPanel wrap = BestGearSetupPanel.left(row);
		wrap.setBorder(new EmptyBorder(1, 0, 1, 0));
		return wrap;
	}

}
