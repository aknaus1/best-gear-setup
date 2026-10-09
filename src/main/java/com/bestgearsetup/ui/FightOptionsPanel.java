package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.WildernessTargets;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.Range;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Boss lookup assumptions, edited here while configuration stores their existing keys. */
class FightOptionsPanel extends JPanel
{
	/**
	 * The settings shown here and their getters. Names, descriptions, types and ranges come from the
	 * {@link ConfigItem} annotations through RuneLite's config descriptor, so they are declared only once.
	 */
	private static final List<Setting> SETTINGS = Arrays.asList(
		new Setting("taskMode", BestGearSetupConfig::taskMode),
		new Setting("autoRaid", BestGearSetupConfig::autoRaid),
		new Setting("currentHitpoints", BestGearSetupConfig::currentHitpoints),
		new Setting("aoe", BestGearSetupConfig::aoe),
		new Setting("aoeTargets", BestGearSetupConfig::aoeTargets),
		new Setting("wilderness", BestGearSetupConfig::wilderness),
		new Setting("limitWildernessRisk", BestGearSetupConfig::limitWildernessRisk),
		new Setting("maxExpensiveItems", BestGearSetupConfig::maxExpensiveItems),
		new Setting("expensiveItemThreshold", BestGearSetupConfig::expensiveItemThreshold),
		new Setting("forinthrySurge", BestGearSetupConfig::forinthrySurge),
		new Setting("soulreaperStacks", BestGearSetupConfig::soulreaperStacks),
		new Setting("miningLevel", BestGearSetupConfig::miningLevel),
		new Setting("raidPartySize", BestGearSetupConfig::raidPartySize),
		new Setting("toaRaidLevel", BestGearSetupConfig::toaRaidLevel),
		new Setting("toaPathLevel", BestGearSetupConfig::toaPathLevel),
		new Setting("coxChallengeMode", BestGearSetupConfig::coxChallengeMode),
		new Setting("autoSpecs", BestGearSetupConfig::autoSpecs),
		new Setting("specVulnerability", BestGearSetupConfig::specVulnerability),
		new Setting("specTomeOfWater", BestGearSetupConfig::specTomeOfWater),
		new Setting("specElderMaul", BestGearSetupConfig::specElderMaul),
		new Setting("specDwh", BestGearSetupConfig::specDwh),
		new Setting("specEmberlight", BestGearSetupConfig::specEmberlight),
		new Setting("specArclight", BestGearSetupConfig::specArclight),
		new Setting("specTonalztics", BestGearSetupConfig::specTonalztics),
		new Setting("specBgsDamage", BestGearSetupConfig::specBgsDamage),
		new Setting("specSeercullDamage", BestGearSetupConfig::specSeercullDamage),
		new Setting("specAyakDamage", BestGearSetupConfig::specAyakDamage));
	/** Pre-fight drains, counted in the collapsed section's heading when any is set. */
	private static final String[] PREPARATION = {"specVulnerability", "specTomeOfWater", "specElderMaul", "specDwh",
		"specEmberlight", "specArclight", "specTonalztics", "specBgsDamage", "specSeercullDamage", "specAyakDamage"};
	/** Wide enough for the largest maximum shown here (2000 damage). */
	private static final int SPINNER_COLUMNS = 4;
	private final BestGearSetupConfig config;
	private final Map<String, JComponent> controls = new LinkedHashMap<>();
	private final Map<String, JPanel> rows = new LinkedHashMap<>();
	private final SidebarSection raidSection;
	private final SidebarSection preparationSection;
	private final String wildernessTip;
	private Monster target = new Monster();
	private boolean syncing;

	/** @param items the config's item descriptors, from {@link net.runelite.client.config.ConfigManager#getConfigDescriptor} */
	FightOptionsPanel(BestGearSetupPlugin plugin, BestGearSetupConfig config, Collection<ConfigItemDescriptor> items)
	{
		this(plugin, config, items, new SidebarSection.Accordion());
	}

	FightOptionsPanel(BestGearSetupPlugin plugin, BestGearSetupConfig config, Collection<ConfigItemDescriptor> items,
		SidebarSection.Accordion accordion)
	{
		this.config = config;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Build the controls first, then place them in one-level sections by purpose.
		JPanel body = new JPanel();
		Map<String, ConfigItemDescriptor> descriptors = new HashMap<>();
		for (ConfigItemDescriptor descriptor : items)
		{
			descriptors.put(descriptor.key(), descriptor);
		}
		for (Setting entry : SETTINGS)
		{
			String setting = entry.key;
			ConfigItemDescriptor descriptor = descriptors.get(setting);
			if (descriptor == null)
			{
				throw new IllegalStateException("Missing fight setting " + setting);
			}
			ConfigItem item = descriptor.getItem();
			Object type = descriptor.getType();
			if (type == boolean.class)
			{
				JCheckBox box = new JCheckBox(item.name());
				box.setName(item.keyName());
				box.setBackground(ColorScheme.DARK_GRAY_COLOR);
				box.setForeground(Color.WHITE);
				box.setFont(FontManager.getRunescapeSmallFont());
				box.setToolTipText(item.description());
				box.addActionListener(e ->
				{
					if (!syncing)
					{
						plugin.setConfig(item.keyName(), box.isSelected());
						updateVisibility();
					}
				});
				controls.put(setting, box);
				addRow(body, setting, BestGearSetupPanel.left(box));
			}
			else if (type instanceof Class && ((Class<?>) type).isEnum())
			{
				JComboBox<Object> choice = new JComboBox<>(((Class<?>) type).getEnumConstants());
				choice.setName(item.keyName());
				choice.setToolTipText(item.description());
				choice.addActionListener(e ->
				{
					if (!syncing)
					{
						plugin.setConfig(item.keyName(), choice.getSelectedItem());
					}
				});
				controls.put(setting, choice);
				JPanel row = new JPanel(new BorderLayout(4, 0));
				row.setBackground(ColorScheme.DARK_GRAY_COLOR);
				JLabel label = new JLabel(item.name());
				label.setLabelFor(choice);
				BestGearSetupPanel.setSmall(label, Color.WHITE);
				row.add(label, BorderLayout.NORTH);
				row.add(choice, BorderLayout.CENTER);
				addRow(body, setting, BestGearSetupPanel.left(row));
			}
			else if (type == String.class)
			{
				JTextField field = new JTextField();
				field.setName(item.keyName());
				field.setToolTipText(item.description());
				Runnable save = () ->
				{
					if (!syncing && !field.getText().equals(entry.getter.apply(config)))
					{
						plugin.setConfig(item.keyName(), field.getText());
					}
				};
				field.addActionListener(e -> save.run());
				field.addFocusListener(new FocusAdapter()
				{
					@Override
					public void focusLost(FocusEvent e)
					{
						save.run();
					}
				});
				controls.put(setting, field);
				JPanel row = new JPanel(new BorderLayout(4, 0));
				row.setBackground(ColorScheme.DARK_GRAY_COLOR);
				JLabel label = new JLabel(item.name());
				label.setLabelFor(field);
				BestGearSetupPanel.setSmall(label, Color.WHITE);
				row.add(label, BorderLayout.NORTH);
				row.add(field, BorderLayout.CENTER);
				addRow(body, setting, BestGearSetupPanel.left(row));
			}
			else
			{
				Range range = descriptor.getRange();
				JSpinner spinner = new JSpinner(new SpinnerNumberModel(range.min(), range.min(), range.max(), 1));
				spinner.setName(item.keyName());
				spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
				// One width for every spinner, so the column lines up whatever each one's maximum is.
				((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(SPINNER_COLUMNS);
				spinner.setToolTipText(item.description());
				spinner.addChangeListener(e ->
				{
					if (!syncing)
					{
						plugin.setConfig(item.keyName(), ((Number) spinner.getValue()).intValue());
						updateVisibility();
					}
				});
				controls.put(setting, spinner);
				JPanel row = new JPanel(new BorderLayout(4, 0));
				row.setBackground(ColorScheme.DARK_GRAY_COLOR);
				JLabel label = new JLabel(item.name());
				label.setLabelFor(spinner);
				BestGearSetupPanel.setSmall(label, Color.WHITE);
				label.setToolTipText(item.description());
				row.add(label, BorderLayout.CENTER);
				row.add(spinner, BorderLayout.EAST);
				addRow(body, setting, BestGearSetupPanel.left(row));
			}
		}
		wildernessTip = control("wilderness").getToolTipText();
		SidebarSection conditions = accordion.create("fightConditions", "Fight conditions");
		addRows(conditions, "taskMode", "currentHitpoints", "aoe", "aoeTargets", "wilderness",
			"limitWildernessRisk", "maxExpensiveItems", "expensiveItemThreshold", "forinthrySurge", "soulreaperStacks");
		conditions.showAsEditor();
		add(BestGearSetupPanel.left(conditions));
		raidSection = accordion.create("raidScaling", "Raid scaling");
		raidSection.showAsEditor();
		addRows(raidSection, "autoRaid", "raidPartySize", "toaRaidLevel", "toaPathLevel", "coxChallengeMode", "miningLevel");
		add(BestGearSetupPanel.left(raidSection));
		// Collapsed by default: most searches start from the target's full stats.
		preparationSection = accordion.create("preparation", "Pre-fight preparation");
		preparationSection.setToolTipText("Successful specials and defence reductions landed before the fight starts");
		addRows(preparationSection, "autoSpecs");
		addRows(preparationSection, PREPARATION);
		add(BestGearSetupPanel.left(preparationSection));
		refresh();
	}

	private void addRow(JPanel body, String key, JPanel row)
	{
		row.setName(key + "Row");
		rows.put(key, row);
		body.add(row);
	}

	private void addRows(SidebarSection section, String... keys)
	{
		for (String key : keys)
		{
			section.getContent().add(rows.get(key));
		}
	}

	/** Match the encounter rules used by the calculation, without changing saved assumptions. */
	void setTarget(MonsterSummary summary)
	{
		target = summary == null ? new Monster() : targetOf(summary);
		refresh();
	}

	/** The id, name and tags the encounter rules read, without loading the variant's full stats. */
	static Monster targetOf(MonsterSummary summary)
	{
		Monster target = new Monster();
		target.setId(summary.getId());
		target.setName(summary.getName());
		target.setAttributes(summary.getAttributes().stream().map(name ->
		{
			Monster.Attribute attribute = new Monster.Attribute();
			attribute.setName(name);
			return attribute;
		}).collect(Collectors.toList()));
		return target;
	}

	private void updateVisibility()
	{
		RaidScaling.Raid raid = RaidScaling.raid(target);
		raidSection.getParent().setVisible(raid != RaidScaling.Raid.NONE);
		rows.get("raidPartySize").setVisible(raid != RaidScaling.Raid.NONE);
		rows.get("toaRaidLevel").setVisible(raid == RaidScaling.Raid.TOA);
		rows.get("toaPathLevel").setVisible(raid == RaidScaling.Raid.TOA && RaidScaling.toaPath(target));
		rows.get("coxChallengeMode").setVisible(raid == RaidScaling.Raid.COX);
		rows.get("miningLevel").setVisible(raid == RaidScaling.Raid.COX && target.getName().contains("guardian"));
		rows.get("aoeTargets").setVisible(selected("aoe"));
		rows.get("limitWildernessRisk").setVisible(selected("wilderness"));
		boolean riskLimited = selected("wilderness") && selected("limitWildernessRisk");
		rows.get("maxExpensiveItems").setVisible(riskLimited);
		rows.get("expensiveItemThreshold").setVisible(riskLimited);
		rows.get("forinthrySurge").setVisible(target.getName().contains("revenant") && selected("wilderness"));
		rows.get("specTomeOfWater").setVisible(selected("specVulnerability"));
		int prepared = 0;
		for (String key : PREPARATION)
		{
			JComponent control = control(key);
			boolean set = control instanceof JCheckBox ? ((JCheckBox) control).isSelected()
				: ((Number) ((JSpinner) control).getValue()).intValue() > 0;
			// The tome only modifies Vulnerability, so it isn't a separate preparation.
			prepared += set && !key.equals("specTomeOfWater") ? 1 : 0;
		}
		preparationSection.setSummary(prepared);
		revalidate();
	}

	private JComponent control(String key)
	{
		JComponent control = controls.get(key);
		if (control == null)
		{
			throw new IllegalStateException("Missing fight setting " + key);
		}
		return control;
	}

	private boolean selected(String key)
	{
		return ((JCheckBox) control(key)).isSelected();
	}

	/** Synchronise saved values without triggering another search. */
	void refresh()
	{
		syncing = true;
		try
		{
			for (Setting entry : SETTINGS)
			{
				Object value = entry.getter.apply(config);
				JComponent control = control(entry.key);
				if (control instanceof JCheckBox)
				{
					((JCheckBox) control).setSelected((boolean) value);
				}
				else if (control instanceof JComboBox)
				{
					((JComboBox<?>) control).setSelectedItem(value);
				}
				else if (control instanceof JTextField)
				{
					((JTextField) control).setText((String) value);
				}
				else
				{
					((JSpinner) control).setValue(value);
				}
			}
			// The search turns Wilderness on for these targets whatever is saved, so show that instead.
			boolean wildernessOnly = WildernessTargets.only(target.getName());
			JCheckBox wilderness = (JCheckBox) control("wilderness");
			wilderness.setEnabled(!wildernessOnly);
			wilderness.setToolTipText(wildernessOnly ? "Always on: this target only appears in the Wilderness." : wildernessTip);
			if (wildernessOnly)
			{
				wilderness.setSelected(true);
			}
		}
		finally
		{
			syncing = false;
		}
		updateVisibility();
	}

	private static final class Setting
	{
		private final String key;
		private final Function<BestGearSetupConfig, Object> getter;

		private Setting(String key, Function<BestGearSetupConfig, Object> getter)
		{
			this.key = key;
			this.getter = getter;
		}
	}
}
