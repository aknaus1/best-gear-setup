package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import com.bestgearsetup.ui.Sidebar;
import com.bestgearsetup.ui.Theme;
import javax.swing.SwingUtilities;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import org.junit.After;
import org.junit.Test;

public class ThemeChangeTest
{
	private Theme chosen = Theme.CLASSIC;

	@After
	public void backToClassic()
	{
		Theme.use(Theme.CLASSIC);
	}

	@Test
	public void changingTheSettingOrTheProfileRepaintsThePanelInTheChosenTheme() throws Exception
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override
			public net.runelite.client.config.ConfigDescriptor getConfigDescriptor()
			{
				return ConfigItemsForTests.descriptor();
			}

			@Override
			public OwnedItems getOwnedItems()
			{
				return ConfigItemsForTests.emptyOwnedItems();
			}
		};
		plugin.config = new Options()
		{
			@Override
			public Theme theme()
			{
				return chosen;
			}
		};
		Sidebar[] panels = new Sidebar[1];
		SwingUtilities.invokeAndWait(() -> panels[0] = new Sidebar(plugin, plugin.config, null, null, null));
		plugin.panel = panels[0];

		chosen = Theme.FORGED;
		ConfigChanged changed = new ConfigChanged();
		changed.setGroup(Options.GROUP);
		changed.setKey(Options.THEME_KEY);
		plugin.onConfigChanged(changed);
		SwingUtilities.invokeAndWait(() -> { });
		assertEquals("Forged", painter(panels[0]));

		chosen = Theme.CLASSIC;
		plugin.onProfileChanged(new ProfileChanged());
		SwingUtilities.invokeAndWait(() -> { });
		assertEquals("a profile switch follows the new profile's theme", "MetalButtonUI", painter(panels[0]));
	}

	/** The look delegate of the Find best setup button. */
	private static String painter(java.awt.Container root)
	{
		for (java.awt.Component child : root.getComponents())
		{
			if (child instanceof javax.swing.JButton && "Find best setup".equals(((javax.swing.JButton) child).getText()))
			{
				return ((javax.swing.JButton) child).getUI().getClass().getSimpleName();
			}
			if (child instanceof java.awt.Container)
			{
				String found = painter((java.awt.Container) child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
