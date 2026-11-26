package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Slot;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;

/** Panel parts that tests outside this package inspect. */
public final class PanelInternals
{
	private PanelInternals()
	{
	}

	public static JPanel resultsPanel(Sidebar panel)
	{
		return panel.resultsPanel;
	}

	public static JLabel statusLabel(Sidebar panel)
	{
		return panel.statusLabel;
	}

	public static JTextField budgetField(Sidebar panel)
	{
		return panel.budgetField;
	}

	public static JButton useGpButton(Sidebar panel)
	{
		return panel.useGpButton;
	}

	/** The right-click menu of an unlocked result item in a slot. */
	public static JPopupMenu itemMenu(Gear item, Slot slot, BestGearSetupPlugin plugin)
	{
		return ItemMenus.create(item, slot, false, plugin);
	}
}
