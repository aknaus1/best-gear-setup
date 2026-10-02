package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.calc.PotionChoice;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.List;
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
 * Collapsible section of the side panel: potion choice per combat style, slot locks and
 * excluded items, and items marked as owned by hand.
 */
class ConstraintsPanel extends JPanel
{
	private static final String BEST_LABEL = "Best available";
	private static final String NONE_LABEL = "None";

	private final BestGearSetupPlugin plugin;
	private final JButton toggle = new JButton();
	private final JPanel body = new JPanel();
	private final Map<CombatClass, JComboBox<String>> potionBoxes = new EnumMap<>(CombatClass.class);
	private final JPanel locksList = new JPanel();
	private final JPanel ownedList = new JPanel();
	private final IconTextField ownedSearch = new IconTextField();
	private final JPanel ownedSuggestions = new JPanel();
	private boolean syncing;

	ConstraintsPanel(BestGearSetupPlugin plugin)
	{
		this.plugin = plugin;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		toggle.setFocusable(false);
		toggle.setToolTipText("Potion choices, slot locks, excluded items and manually owned items");
		toggle.addActionListener(e -> setExpanded(!body.isVisible()));
		add(toggle, BorderLayout.NORTH);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.setBorder(new EmptyBorder(4, 0, 0, 0));
		add(body, BorderLayout.CENTER);

		body.add(header("Potions"));
		JPanel potions = new JPanel(new GridLayout(0, 1, 0, 2));
		potions.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (CombatClass cls : CombatClass.values())
		{
			JComboBox<String> box = new JComboBox<>(new String[]{BEST_LABEL, NONE_LABEL});
			box.setFocusable(false);
			box.setToolTipText(cls.getDisplayName() + " potion. 'Best available' skips raid, NMZ and Deadman potions");
			box.addActionListener(e ->
			{
				if (!syncing)
				{
					plugin.setPotionChoice(cls, toChoice((String) box.getSelectedItem()));
				}
			});
			potionBoxes.put(cls, box);
			JPanel row = new JPanel(new BorderLayout(4, 0));
			row.setBackground(ColorScheme.DARK_GRAY_COLOR);
			JLabel label = new JLabel(cls.getDisplayName());
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setPreferredSize(new java.awt.Dimension(44, 20));
			row.add(label, BorderLayout.WEST);
			row.add(box, BorderLayout.CENTER);
			potions.add(row);
		}
		body.add(BestGearSetupPanel.left(potions));

		body.add(header("Locks & exclusions"));
		JLabel lockHint = new JLabel(BestGearSetupPanel.html("Right-click an item in a result to lock its slot, exclude it, or mark it owned."));
		BestGearSetupPanel.setSmall(lockHint, ColorScheme.LIGHT_GRAY_COLOR);
		body.add(BestGearSetupPanel.left(lockHint));
		locksList.setLayout(new BoxLayout(locksList, BoxLayout.Y_AXIS));
		locksList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.add(BestGearSetupPanel.left(locksList));

		body.add(header("Marked as owned"));
		JLabel ownedHint = new JLabel(BestGearSetupPanel.html("For items the bank scan can't see, e.g. the POH costume room."));
		BestGearSetupPanel.setSmall(ownedHint, ColorScheme.LIGHT_GRAY_COLOR);
		body.add(BestGearSetupPanel.left(ownedHint));
		ownedSearch.setIcon(IconTextField.Icon.SEARCH);
		ownedSearch.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		ownedSearch.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		ownedSearch.setToolTipText("Search an item to mark as owned");
		ownedSearch.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateOwnedSuggestions();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateOwnedSuggestions();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateOwnedSuggestions();
			}
		});
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
		body.add(BestGearSetupPanel.left(more));

		setExpanded(false);
	}

	private void setExpanded(boolean expanded)
	{
		body.setVisible(expanded);
		toggle.setText((expanded ? "- " : "+ ") + "Potions, locks & items" + summary());
		revalidate();
	}

	private String summary()
	{
		int locks = plugin.getLocks().size();
		int excluded = plugin.getExcluded().size();
		return locks + excluded == 0 ? "" : " (" + (locks + excluded) + ")";
	}

	/** Rebuild from the plugin's current state (after data load or config change). */
	void refresh()
	{
		GameData data = plugin.getGameData();
		syncing = true;
		try
		{
			for (Map.Entry<CombatClass, JComboBox<String>> e : potionBoxes.entrySet())
			{
				JComboBox<String> box = e.getValue();
				if (data != null && box.getItemCount() <= 2)
				{
					for (String name : PotionChoice.namesFor(data, e.getKey()))
					{
						box.addItem(GameData.titleCase(name));
					}
				}
				box.setSelectedItem(toLabel(plugin.getPotionChoice(e.getKey())));
			}
		}
		finally
		{
			syncing = false;
		}
		rebuildLocks(data);
		rebuildOwned(data);
		setExpanded(body.isVisible());
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
			clear.setFocusable(false);
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
				JLabel row = new JLabel(GameData.titleCase(item.getName()));
				BestGearSetupPanel.setSmall(row, plugin.owns(item) ? new Color(0x6BD56B) : Color.WHITE);
				row.setBorder(new EmptyBorder(3, 6, 3, 6));
				row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				row.setToolTipText("Mark as owned");
				row.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e)
					{
						plugin.setManualOwned(item.getId(), true);
						ownedSearch.setText("");
					}
				});
				ownedSuggestions.add(BestGearSetupPanel.left(row));
			}
		}
		ownedSuggestions.revalidate();
		ownedSuggestions.repaint();
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
		JLabel remove = new JLabel("x");
		BestGearSetupPanel.setSmall(remove, new Color(0xFF6B6B));
		remove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		remove.setToolTipText("Remove");
		remove.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onRemove.run();
			}
		});
		row.add(remove, BorderLayout.EAST);
		JPanel wrap = BestGearSetupPanel.left(row);
		wrap.setBorder(new EmptyBorder(1, 0, 1, 0));
		return wrap;
	}

	private static JPanel header(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(new EmptyBorder(6, 0, 2, 0));
		return BestGearSetupPanel.left(label);
	}

	private static String toChoice(String label)
	{
		if (label == null || BEST_LABEL.equals(label))
		{
			return PotionChoice.BEST;
		}
		return NONE_LABEL.equals(label) ? PotionChoice.NONE : label.toLowerCase(java.util.Locale.ROOT);
	}

	private static String toLabel(String choice)
	{
		if (PotionChoice.BEST.equalsIgnoreCase(choice))
		{
			return BEST_LABEL;
		}
		return PotionChoice.NONE.equalsIgnoreCase(choice) ? NONE_LABEL : GameData.titleCase(choice);
	}
}
