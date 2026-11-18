package com.bestgearsetup.ui;

import com.bestgearsetup.Options;
import com.bestgearsetup.BestGearSetupPlugin;
import java.awt.Cursor;
import java.awt.font.TextAttribute;
import java.util.*;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.*;
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

	SettingsPanel(BestGearSetupPlugin plugin, Options config, ConfigDescriptor descriptor)
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
		SidebarSection.Accordion accordion = new SidebarSection.Accordion(false);
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
				add(Sidebar.left(group));
				first = first == null ? group : first;
			}
		}
		if (first != null)
		{
			first.setExpanded(true);
		}

		// Underlined with a hand cursor, so the quiet text reads as a link before it is hovered.
		JButton runeLite = new JButton("Open in RuneLite settings");
		runeLite.setName("openRuneLiteSettings");
		Controls.ghost(runeLite);
		runeLite.setFont(runeLite.getFont().deriveFont(Collections.singletonMap(TextAttribute.UNDERLINE,
			TextAttribute.UNDERLINE_ON)));
		runeLite.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		runeLite.setToolTipText("Same settings on RuneLite's config page, with reset");
		runeLite.addActionListener(e -> plugin.openConfiguration());
		JPanel link = Sidebar.left(runeLite);
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
