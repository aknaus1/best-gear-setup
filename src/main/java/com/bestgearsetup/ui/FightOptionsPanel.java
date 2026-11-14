package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.WildernessTargets;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.ui.ColorScheme;

/** Boss lookup assumptions, edited here while configuration stores their existing keys. */
class FightOptionsPanel extends JPanel
{
	/** Pre-fight drains, counted in the collapsed section's heading when any is set. */
	private static final String[] PREPARATION = {"specVulnerability", "specTomeOfWater", "specElderMaul", "specDwh",
		"specEmberlight", "specArclight", "specTonalztics", "specBgsDamage", "specSeercullDamage", "specAyakDamage"};
	/** Wide enough for the largest maximum shown here (2000 damage). */
	private static final int SPINNER_COLUMNS = 4;
	private final SettingRows settingRows;
	private final SidebarSection raidSection;
	private final SidebarSection preparationSection;
	private final String wildernessTip;
	private Monster target = new Monster();

	/** @param items the config's item descriptors, from {@link net.runelite.client.config.ConfigManager#getConfigDescriptor} */
	FightOptionsPanel(BestGearSetupPlugin plugin, BestGearSetupConfig config, Collection<ConfigItemDescriptor> items)
	{
		this(plugin, config, items, new SidebarSection.Accordion());
	}

	FightOptionsPanel(BestGearSetupPlugin plugin, BestGearSetupConfig config, Collection<ConfigItemDescriptor> items,
		SidebarSection.Accordion accordion)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Each row's control is built as its one-level section is filled.
		settingRows = new SettingRows(plugin, config, items, SPINNER_COLUMNS, this::updateVisibility);
		SidebarSection conditions = accordion.create("fightConditions", "Fight conditions");
		addRows(conditions, "taskMode", "currentHitpoints", "aoe", "aoeTargets", "wilderness",
			"limitWildernessRisk", "maxExpensiveItems", "expensiveItemThreshold", "forinthrySurge", "soulreaperStacks");
		wildernessTip = control("wilderness").getToolTipText();
		conditions.showAsEditor();
		add(BestGearSetupPanel.left(conditions));
		raidSection = accordion.create("raidScaling", "Raid scaling");
		raidSection.showAsEditor();
		addRows(raidSection, "autoRaid", "raidPartySize", "toaRaidLevel", "toaPathLevel", "coxChallengeMode", "miningLevel");
		add(BestGearSetupPanel.left(raidSection));
		// Collapsed by default: most searches start from the target's full stats.
		preparationSection = accordion.create("preparation", "Pre-fight preparation");
		preparationSection.setToolTipText("Specs and defence drains landed before the fight");
		addRows(preparationSection, "autoSpecs");
		addRows(preparationSection, PREPARATION);
		add(BestGearSetupPanel.left(preparationSection));
		refresh();
	}

	private void addRows(SidebarSection section, String... keys)
	{
		for (String key : keys)
		{
			section.getContent().add(settingRows.add(key));
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
		settingRows.row("raidPartySize").setVisible(raid != RaidScaling.Raid.NONE);
		settingRows.row("toaRaidLevel").setVisible(raid == RaidScaling.Raid.TOA);
		settingRows.row("toaPathLevel").setVisible(raid == RaidScaling.Raid.TOA && RaidScaling.toaPath(target));
		settingRows.row("coxChallengeMode").setVisible(raid == RaidScaling.Raid.COX);
		settingRows.row("miningLevel").setVisible(raid == RaidScaling.Raid.COX && target.getName().contains("guardian"));
		settingRows.row("aoeTargets").setVisible(selected("aoe"));
		settingRows.row("limitWildernessRisk").setVisible(selected("wilderness"));
		boolean riskLimited = selected("wilderness") && selected("limitWildernessRisk");
		settingRows.row("maxExpensiveItems").setVisible(riskLimited);
		settingRows.row("expensiveItemThreshold").setVisible(riskLimited);
		settingRows.row("forinthrySurge").setVisible(target.getName().contains("revenant") && selected("wilderness"));
		settingRows.row("specTomeOfWater").setVisible(selected("specVulnerability"));
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
		return settingRows.control(key);
	}

	private boolean selected(String key)
	{
		return settingRows.selected(key);
	}

	/** Synchronise saved values without triggering another search. */
	void refresh()
	{
		settingRows.refresh();
		// The search turns Wilderness on for these targets whatever is saved, so show that instead.
		boolean wildernessOnly = WildernessTargets.only(target.getName());
		JCheckBox wilderness = (JCheckBox) control("wilderness");
		wilderness.setEnabled(!wildernessOnly);
		wilderness.setToolTipText(wildernessOnly ? "Always on: this target is only in the Wilderness." : wildernessTip);
		if (wildernessOnly)
		{
			wilderness.setSelected(true);
		}
		updateVisibility();
	}
}
