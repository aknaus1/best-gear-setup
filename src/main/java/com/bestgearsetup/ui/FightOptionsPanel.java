package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.calc.DragonfireProtection;
import com.bestgearsetup.calc.EncounterPhases;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
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
		new Setting("specialAttacks", BestGearSetupConfig::specialAttacks),
		new Setting("targetDistance", BestGearSetupConfig::targetDistance),
		new Setting("aoe", BestGearSetupConfig::aoe),
		new Setting("aoeTargets", BestGearSetupConfig::aoeTargets),
		new Setting("requireFireProtection", BestGearSetupConfig::requireFireProtection),
		new Setting("antifire", BestGearSetupConfig::antifire),
		new Setting("protectMagic", BestGearSetupConfig::protectMagic),
		new Setting("ammoCount", BestGearSetupConfig::ammoCount),
		new Setting("currentHitpoints", BestGearSetupConfig::currentHitpoints),
		new Setting("monsterHitpoints", BestGearSetupConfig::monsterHitpoints),
		new Setting("wilderness", BestGearSetupConfig::wilderness),
		new Setting("weaponPoison", BestGearSetupConfig::weaponPoison),
		new Setting("forinthrySurge", BestGearSetupConfig::forinthrySurge),
		new Setting("charge", BestGearSetupConfig::charge),
		new Setting("markOfDarkness", BestGearSetupConfig::markOfDarkness),
		new Setting("sunfireRunes", BestGearSetupConfig::sunfireRunes),
		new Setting("kandarinDiary", BestGearSetupConfig::kandarinDiary),
		new Setting("soulreaperStacks", BestGearSetupConfig::soulreaperStacks),
		new Setting("miningLevel", BestGearSetupConfig::miningLevel),
		new Setting("raidPartySize", BestGearSetupConfig::raidPartySize),
		new Setting("toaRaidLevel", BestGearSetupConfig::toaRaidLevel),
		new Setting("toaPathLevel", BestGearSetupConfig::toaPathLevel),
		new Setting("coxChallengeMode", BestGearSetupConfig::coxChallengeMode),
		new Setting("encounterPhase", BestGearSetupConfig::encounterPhase),
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
	private final BestGearSetupConfig config;
	private final Map<String, JComponent> controls = new LinkedHashMap<>();
	private final Map<String, JPanel> rows = new LinkedHashMap<>();
	private Monster target = new Monster();
	private boolean syncing;

	/** @param items the config's item descriptors, from {@link net.runelite.client.config.ConfigManager#getConfigDescriptor} */
	FightOptionsPanel(BestGearSetupPlugin plugin, BestGearSetupConfig config, Collection<ConfigItemDescriptor> items)
	{
		this.config = config;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton toggle = new JButton("+ Fight options");
		toggle.setFocusable(false);
		add(toggle, BorderLayout.NORTH);
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.setBorder(new EmptyBorder(4, 0, 0, 0));
		body.setVisible(false);
		add(body, BorderLayout.CENTER);
		toggle.addActionListener(e ->
		{
			body.setVisible(!body.isVisible());
			toggle.setText((body.isVisible() ? "- " : "+ ") + "Fight options");
			revalidate();
		});
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
				box.setFocusable(false);
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
			else
			{
				Range range = descriptor.getRange();
				JSpinner spinner = new JSpinner(new SpinnerNumberModel(range.min(), range.min(), range.max(), 1));
				spinner.setName(item.keyName());
				spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
				spinner.setToolTipText(item.description());
				spinner.addChangeListener(e ->
				{
					if (!syncing)
					{
						plugin.setConfig(item.keyName(), ((Number) spinner.getValue()).intValue());
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
		JButton currentDistance = new JButton("Use current distance");
		currentDistance.setName("currentDistance");
		currentDistance.setFocusable(false);
		currentDistance.setToolTipText("Measure your distance to the NPC selected through Best setup, or your current combat target.");
		currentDistance.addActionListener(e -> plugin.useCurrentDistance());
		body.add(BestGearSetupPanel.left(currentDistance));
		JPanel effects = group(body, "Attack effects");
		JPanel specials = group(body, "Pre-fight specials");
		for (Setting entry : SETTINGS)
		{
			String setting = entry.key;
			if (setting.startsWith("spec") && !setting.equals("specialAttacks"))
			{
				specials.add(rows.get(setting));
			}
			else if (Arrays.asList("currentHitpoints", "wilderness", "weaponPoison", "forinthrySurge",
				"charge", "markOfDarkness", "sunfireRunes", "kandarinDiary", "soulreaperStacks").contains(setting))
			{
				effects.add(rows.get(setting));
			}
		}
		refresh();
	}

	private void addRow(JPanel body, String key, JPanel row)
	{
		row.setName(key + "Row");
		rows.put(key, row);
		body.add(row);
	}

	private static JPanel group(JPanel parent, String title)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton toggle = new JButton("+ " + title);
		toggle.setFocusable(false);
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setVisible(false);
		panel.add(toggle, BorderLayout.NORTH);
		panel.add(content, BorderLayout.CENTER);
		toggle.addActionListener(e ->
		{
			content.setVisible(!content.isVisible());
			toggle.setText((content.isVisible() ? "- " : "+ ") + title);
			parent.revalidate();
		});
		parent.add(BestGearSetupPanel.left(panel));
		return content;
	}

	/** Match the encounter rules used by the calculation, without changing saved assumptions. */
	void setTarget(MonsterSummary summary)
	{
		target = new Monster();
		target.setId(summary.getId());
		target.setName(summary.getName());
		target.setAttributes(summary.getAttributes().stream().map(name ->
		{
			Monster.Attribute attribute = new Monster.Attribute();
			attribute.setName(name);
			return attribute;
		}).collect(Collectors.toList()));
		refresh();
	}

	@SuppressWarnings("unchecked")
	private void updateVisibility()
	{
		RaidScaling.Raid raid = RaidScaling.raid(target);
		rows.get("raidPartySize").setVisible(raid != RaidScaling.Raid.NONE);
		rows.get("toaRaidLevel").setVisible(raid == RaidScaling.Raid.TOA);
		rows.get("toaPathLevel").setVisible(raid == RaidScaling.Raid.TOA && RaidScaling.toaPath(target));
		rows.get("coxChallengeMode").setVisible(raid == RaidScaling.Raid.COX);
		rows.get("miningLevel").setVisible(raid == RaidScaling.Raid.COX && target.getName().contains("guardian"));
		boolean fire = DragonfireProtection.applies(target);
		rows.get("requireFireProtection").setVisible(fire);
		rows.get("antifire").setVisible(fire);
		rows.get("protectMagic").setVisible(fire);
		rows.get("aoeTargets").setVisible(selected("aoe"));
		rows.get("forinthrySurge").setVisible(target.getName().contains("revenant") && selected("wilderness"));
		rows.get("markOfDarkness").setVisible(target.hasAttribute("demon"));
		boolean previous = syncing;
		syncing = true;
		try
		{
			DefaultComboBoxModel<Object> phases = new DefaultComboBoxModel<>();
			phases.addElement(EncounterPhase.STANDARD);
			for (EncounterPhase phase : EncounterPhase.values())
			{
				if (phase != EncounterPhase.STANDARD && EncounterPhases.applies(target, phase))
				{
					phases.addElement(phase);
				}
			}
			JComboBox<Object> choice = (JComboBox<Object>) control("encounterPhase");
			choice.setModel(phases);
			EncounterPhase saved = config.encounterPhase();
			choice.setSelectedItem(phases.getIndexOf(saved) >= 0 ? saved : EncounterPhase.STANDARD);
			rows.get("encounterPhase").setVisible(phases.getSize() > 1);
		}
		finally
		{
			syncing = previous;
		}
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
				else
				{
					((JSpinner) control).setValue(value);
				}
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
