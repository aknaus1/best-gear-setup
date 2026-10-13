package com.bestgearsetup.ui;

import static com.bestgearsetup.ui.SettingRows.setting;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.RaidPlanType;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.WildernessTargets;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.ui.SettingRows.Setting;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
	/** The settings shown here and their getters; {@link SettingRows} builds their editors. */
	private static final List<Setting> SETTINGS = Arrays.asList(
		setting("activeEncounter", BestGearSetupConfig::activeEncounter),
		setting("taskMode", BestGearSetupConfig::taskMode),
		setting("autoRaid", BestGearSetupConfig::autoRaid),
		setting("currentHitpoints", BestGearSetupConfig::currentHitpoints),
		setting("aoe", BestGearSetupConfig::aoe),
		setting("aoeTargets", BestGearSetupConfig::aoeTargets),
		setting("wilderness", BestGearSetupConfig::wilderness),
		setting("limitWildernessRisk", BestGearSetupConfig::limitWildernessRisk),
		setting("maxExpensiveItems", BestGearSetupConfig::maxExpensiveItems),
		setting("expensiveItemThreshold", BestGearSetupConfig::expensiveItemThreshold),
		setting("forinthrySurge", BestGearSetupConfig::forinthrySurge),
		setting("soulreaperStacks", BestGearSetupConfig::soulreaperStacks),
		setting("thrall", BestGearSetupConfig::thrall),
		setting("killSpecials", BestGearSetupConfig::killSpecials),
		setting("specEnergy", BestGearSetupConfig::specEnergy),
		setting("drainSpecs", BestGearSetupConfig::drainSpecs),
		setting("miningLevel", BestGearSetupConfig::miningLevel),
		setting("raidPartySize", BestGearSetupConfig::raidPartySize),
		setting("toaRaidLevel", BestGearSetupConfig::toaRaidLevel),
		setting("toaPathLevel", BestGearSetupConfig::toaPathLevel),
		setting("coxChallengeMode", BestGearSetupConfig::coxChallengeMode),
		setting("raidEquipmentLimit", BestGearSetupConfig::raidEquipmentLimit),
		setting("raidSpellbook", BestGearSetupConfig::raidSpellbook),
		setting("toaPathOrder", BestGearSetupConfig::toaPathOrder),
		setting("tobMaidenRole", BestGearSetupConfig::tobMaidenRole),
		setting("tobNylocasRole", BestGearSetupConfig::tobNylocasRole),
		setting("tobDawnbringer", BestGearSetupConfig::tobDawnbringer),
		setting("autoSpecs", BestGearSetupConfig::autoSpecs),
		setting("specVulnerability", BestGearSetupConfig::specVulnerability),
		setting("specTomeOfWater", BestGearSetupConfig::specTomeOfWater),
		setting("specElderMaul", BestGearSetupConfig::specElderMaul),
		setting("specDwh", BestGearSetupConfig::specDwh),
		setting("specEmberlight", BestGearSetupConfig::specEmberlight),
		setting("specArclight", BestGearSetupConfig::specArclight),
		setting("specTonalztics", BestGearSetupConfig::specTonalztics),
		setting("specBgsDamage", BestGearSetupConfig::specBgsDamage),
		setting("specSeercullDamage", BestGearSetupConfig::specSeercullDamage),
		setting("specAyakDamage", BestGearSetupConfig::specAyakDamage));
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
	/** The complete raid being planned; path order and roles only change complete raid plans. */
	private RaidPlanType raidPlan = RaidPlanType.SINGLE;

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
		// Build the controls first, then place them in one-level sections by purpose.
		settingRows = new SettingRows(plugin, config, items, SPINNER_COLUMNS, this::updateVisibility);
		for (Setting entry : SETTINGS)
		{
			settingRows.add(entry);
		}
		wildernessTip = control("wilderness").getToolTipText();
		SidebarSection conditions = accordion.create("fightConditions", "Fight conditions");
		addRows(conditions, "activeEncounter", "taskMode", "currentHitpoints", "aoe", "aoeTargets", "wilderness",
			"limitWildernessRisk", "maxExpensiveItems", "expensiveItemThreshold", "forinthrySurge", "soulreaperStacks", "thrall");
		conditions.showAsEditor();
		add(BestGearSetupPanel.left(conditions));
		SidebarSection specials = accordion.create("specialAttacks", "Special attacks");
		specials.showAsEditor();
		addRows(specials, "killSpecials", "specEnergy", "drainSpecs");
		add(BestGearSetupPanel.left(specials));
		raidSection = accordion.create("raidScaling", "Raid");
		raidSection.showAsEditor();
		addRows(raidSection, "autoRaid", "raidPartySize", "toaRaidLevel", "toaPathLevel", "coxChallengeMode", "miningLevel",
			"raidEquipmentLimit", "raidSpellbook", "toaPathOrder", "tobMaidenRole", "tobNylocasRole",
			"tobDawnbringer");
		add(BestGearSetupPanel.left(raidSection));
		// Collapsed by default: most searches start from the target's full stats.
		preparationSection = accordion.create("preparation", "Pre-fight preparation");
		preparationSection.setToolTipText("Successful specials and defence reductions landed before the fight starts");
		addRows(preparationSection, "autoSpecs");
		addRows(preparationSection, PREPARATION);
		add(BestGearSetupPanel.left(preparationSection));
		refresh();
	}

	private void addRows(SidebarSection section, String... keys)
	{
		for (String key : keys)
		{
			section.getContent().add(settingRows.row(key));
		}
	}

	/** Match the encounter rules used by the calculation, without changing saved assumptions. */
	void setTarget(MonsterSummary summary)
	{
		target = summary == null ? new Monster() : targetOf(summary);
		refresh();
	}

	/** Show the path order or role choices that the complete raid being planned uses. */
	void setRaidPlan(RaidPlanType type)
	{
		raidPlan = type == null ? RaidPlanType.SINGLE : type;
		updateVisibility();
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
		// A complete raid keeps its settings visible even on unscaled puzzle targets such as the ToA baboons.
		boolean planned = raidPlan != RaidPlanType.SINGLE;
		raidSection.getParent().setVisible(raid != RaidScaling.Raid.NONE || planned);
		settingRows.row("raidPartySize").setVisible(raid != RaidScaling.Raid.NONE || planned);
		settingRows.row("toaRaidLevel").setVisible(raid == RaidScaling.Raid.TOA || raidPlan == RaidPlanType.TOA);
		settingRows.row("toaPathLevel").setVisible(raid == RaidScaling.Raid.TOA && RaidScaling.toaPath(target));
		settingRows.row("coxChallengeMode").setVisible(raid == RaidScaling.Raid.COX);
		settingRows.row("miningLevel").setVisible(raid == RaidScaling.Raid.COX && target.getName().contains("guardian"));
		settingRows.row("toaPathOrder").setVisible(raidPlan == RaidPlanType.TOA);
		settingRows.row("tobMaidenRole").setVisible(raidPlan == RaidPlanType.TOB);
		settingRows.row("tobNylocasRole").setVisible(raidPlan == RaidPlanType.TOB);
		settingRows.row("tobDawnbringer").setVisible(raidPlan == RaidPlanType.TOB);
		settingRows.row("aoeTargets").setVisible(selected("aoe"));
		settingRows.row("specEnergy").setVisible(selected("killSpecials"));
		settingRows.row("drainSpecs").setVisible(selected("killSpecials"));
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
		wilderness.setToolTipText(wildernessOnly ? "Always on: this target only appears in the Wilderness." : wildernessTip);
		if (wildernessOnly)
		{
			wilderness.setSelected(true);
		}
		updateVisibility();
	}
}
