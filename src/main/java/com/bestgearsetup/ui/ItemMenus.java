package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * Right-click menu for an item or slot in a result.
 */
final class ItemMenus
{
	private ItemMenus()
	{
	}

	/**
	 * @param item the item shown (null for an empty slot)
	 * @param slot the equipment slot, or null for blowpipe darts
	 */
	static JPopupMenu create(GearItem item, Slot slot, boolean locked, BestGearSetupPlugin plugin)
	{
		JPopupMenu menu = new JPopupMenu();
		String slotName = slot == null ? "" : slot.getDisplayName().toLowerCase(Locale.ROOT);
		if (slot != null)
		{
			if (locked)
			{
				add(menu, "Unlock " + slotName + " slot", () -> plugin.setLock(slot, null));
			}
			else if (item != null)
			{
				add(menu, "Lock " + slotName + " to " + GameData.titleCase(item.getName()),
					() -> plugin.setLock(slot, SlotLock.item(item.getId())));
			}
			add(menu, "Lock " + slotName + " to another item...", () -> plugin.chooseLockItem(slot));
			if (slot != Slot.WEAPON && !locked)
			{
				add(menu, "Keep " + slotName + " slot empty", () -> plugin.setLock(slot, SlotLock.empty()));
				add(menu, "Always fill " + slotName + " for defence / prayer", () -> plugin.setLock(slot, SlotLock.fill()));
			}
		}
		if (item != null)
		{
			String name = GameData.titleCase(item.getName());
			GameData data = plugin.getGameData();
			List<Integer> family = data == null ? Collections.emptyList() : data.variantFamily(item).stream()
				.map(GearItem::getId).collect(Collectors.toList());
			if (family.size() > 1)
			{
				// Variants are separate entries ("(b)", "(e)"); offer the whole family so it can't simply reappear.
				add(menu, "Exclude all " + GameData.titleCase(GameData.familyName(item.getName())) + " variants ("
					+ family.size() + ")", () -> plugin.setExcluded(family, true));
				add(menu, "Exclude only " + name, () -> plugin.setExcluded(item.getId(), true));
			}
			else
			{
				add(menu, "Exclude " + name, () -> plugin.setExcluded(item.getId(), true));
			}
			if (plugin.getOwnedItems().isManual(item.getId()))
			{
				add(menu, "Unmark as owned", () -> plugin.setManualOwned(item.getId(), false));
			}
			else if (!plugin.owns(item))
			{
				add(menu, "Mark as owned", () -> plugin.setManualOwned(item.getId(), true));
			}
		}
		return menu;
	}

	private static void add(JPopupMenu menu, String text, Runnable action)
	{
		JMenuItem mi = new JMenuItem(text);
		mi.addActionListener(e -> action.run());
		menu.add(mi);
	}
}
