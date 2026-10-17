package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigSectionDescriptor;
import net.runelite.client.ui.ColorScheme;

/**
 * The plugin's persistent preferences, grouped into the sections RuneLite's configuration page uses and
 * saved to the same keys, so a change in either place shows in both.
 */
class SettingsPanel extends JPanel
{
	/** Wide enough for the largest maximum shown here (100,000 ammo). */
	private static final int SPINNER_COLUMNS = 6;

	private final SettingRows settingRows;

	SettingsPanel(BestGearSetupPlugin plugin, BestGearSetupConfig config, ConfigDescriptor descriptor)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		settingRows = new SettingRows(plugin, config, descriptor.getItems(), SPINNER_COLUMNS, () ->
		{
		});
		List<ConfigItemDescriptor> shown = descriptor.getItems().stream()
			.filter(item -> !item.getItem().hidden())
			.sorted(Comparator.comparingInt(ConfigItemDescriptor::position))
			.collect(Collectors.toList());

		for (ConfigItemDescriptor item : shown)
		{
			if (item.getItem().section().isEmpty())
			{
				add(settingRows.add(item.key()));
			}
		}
		// One section open at a time, as in the Fight editor, keeps the list short enough to scan.
		SidebarSection.Accordion accordion = new SidebarSection.Accordion();
		List<ConfigSectionDescriptor> sections = descriptor.getSections().stream()
			.sorted(Comparator.comparingInt(ConfigSectionDescriptor::position))
			.collect(Collectors.toList());
		SidebarSection first = null;
		for (ConfigSectionDescriptor section : sections)
		{
			SidebarSection group = accordion.create(section.key(), section.name());
			group.setToolTip(section.getSection().description());
			for (ConfigItemDescriptor item : shown)
			{
				if (item.getItem().section().equals(section.key()))
				{
					group.getContent().add(settingRows.add(item.key()));
				}
			}
			if (group.getContent().getComponentCount() > 0)
			{
				add(BestGearSetupPanel.left(group));
				first = first == null ? group : first;
			}
		}
		if (first != null)
		{
			first.setExpanded(true);
		}

		JButton runeLite = new JButton("Open in RuneLite settings");
		runeLite.setName("openRuneLiteSettings");
		Controls.ghost(runeLite);
		runeLite.setToolTipText("The same settings on RuneLite's configuration page, with reset to defaults");
		runeLite.addActionListener(e -> plugin.openConfiguration());
		JPanel link = BestGearSetupPanel.left(runeLite);
		link.setBorder(new EmptyBorder(6, 0, 0, 0));
		add(link);
		settingRows.refresh();
	}

	/** Show the saved values; called when configuration changes anywhere. */
	void refresh()
	{
		settingRows.refresh();
	}
}
