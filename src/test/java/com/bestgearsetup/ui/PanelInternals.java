package com.bestgearsetup.ui;

import javax.swing.JLabel;
import javax.swing.JPanel;

/** Panel parts that tests outside this package inspect. */
public final class PanelInternals
{
	private PanelInternals()
	{
	}

	public static JPanel resultsPanel(BestGearSetupPanel panel)
	{
		return panel.resultsPanel;
	}

	public static JLabel statusLabel(BestGearSetupPanel panel)
	{
		return panel.statusLabel;
	}
}
