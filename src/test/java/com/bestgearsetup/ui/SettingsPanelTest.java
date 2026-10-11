package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.ConfigItemsForTests;
import com.bestgearsetup.calc.SearchDepth;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.ui.components.ColorJButton;
import org.junit.Test;

public class SettingsPanelTest
{
	private final Map<String, Object> writes = new HashMap<>();
	private final BestGearSetupPlugin plugin = new BestGearSetupPlugin()
	{
		@Override
		public void setConfig(String key, Object value)
		{
			writes.put(key, value);
		}
	};

	@Test
	public void everyVisibleSettingIsEditableInItsSection() throws Exception
	{
		ConfigDescriptor descriptor = ConfigItemsForTests.descriptor();
		SwingUtilities.invokeAndWait(() ->
		{
			SettingsPanel panel = new SettingsPanel(plugin, new BestGearSetupConfig() {}, descriptor);
			for (ConfigItemDescriptor item : descriptor.getItems())
			{
				Component control = named(panel, item.getItem().keyName());
				if (item.getItem().hidden())
				{
					assertEquals("Hidden settings stay out of Settings: " + item.key(), null, control);
					continue;
				}
				assertNotNull("No editor for " + item.key(), control);
				Component section = named(panel, item.getItem().section() + "Content");
				assertTrue(item.key() + " belongs in section " + item.getItem().section(),
					SwingUtilities.isDescendingFrom(control, section));
			}
			assertTrue("Saved values are shown without being saved again", writes.isEmpty());
		});
	}

	@Test
	public void editsSaveToTheSameKeysAsRuneLitesConfigPage() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			SettingsPanel panel = new SettingsPanel(plugin, new BestGearSetupConfig() {}, ConfigItemsForTests.descriptor());
			JCheckBox bosses = (JCheckBox) named(panel, BestGearSetupConfig.BOSSES_ONLY_KEY);
			assertFalse(bosses.isSelected());
			bosses.doClick();
			assertEquals(true, writes.get(BestGearSetupConfig.BOSSES_ONLY_KEY));

			((JComboBox<?>) named(panel, "searchDepth")).setSelectedItem(SearchDepth.FAST);
			assertEquals(SearchDepth.FAST, writes.get("searchDepth"));

			((JSpinner) named(panel, "ammoCount")).setValue(500);
			assertEquals(500, writes.get("ammoCount"));
			((JSpinner) named(panel, "fillMargin")).setValue(0.5);
			assertEquals(0.5, writes.get("fillMargin"));
		});
	}

	@Test
	public void refreshShowsChangesMadeElsewhere() throws Exception
	{
		boolean[] bossesOnly = {false};
		BestGearSetupConfig config = new BestGearSetupConfig()
		{
			@Override
			public boolean bossesOnly()
			{
				return bossesOnly[0];
			}

			@Override
			public Color bankHighlightColor()
			{
				return new Color(0x12AB34);
			}
		};
		SwingUtilities.invokeAndWait(() ->
		{
			SettingsPanel panel = new SettingsPanel(plugin, config, ConfigItemsForTests.descriptor());
			ColorJButton swatch = (ColorJButton) named(panel, BestGearSetupConfig.BANK_HIGHLIGHT_COLOR_KEY);
			assertEquals(new Color(0x12AB34), swatch.getColor());
			assertEquals("#12AB34", swatch.getText());
			bossesOnly[0] = true;
			panel.refresh();
			assertTrue(((JCheckBox) named(panel, BestGearSetupConfig.BOSSES_ONLY_KEY)).isSelected());
			assertTrue(writes.isEmpty());
		});
	}

	@Test
	public void runeLitesConfigPageStaysReachable() throws Exception
	{
		int[] opened = {0};
		BestGearSetupPlugin opener = new BestGearSetupPlugin()
		{
			@Override
			public void openConfiguration()
			{
				opened[0]++;
			}
		};
		SwingUtilities.invokeAndWait(() ->
		{
			SettingsPanel panel = new SettingsPanel(opener, new BestGearSetupConfig() {}, ConfigItemsForTests.descriptor());
			((JButton) named(panel, "openRuneLiteSettings")).doClick();
			assertEquals(1, opened[0]);
		});
	}

	private static Component named(Container container, String name)
	{
		for (Component child : container.getComponents())
		{
			if (name.equals(child.getName()))
			{
				return child;
			}
			if (child instanceof Container)
			{
				Component found = named((Container) child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
