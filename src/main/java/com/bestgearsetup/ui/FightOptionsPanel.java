package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.RaidPlanType;
import com.bestgearsetup.RaidOptions;
import com.bestgearsetup.RaidSpellbook;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.WildernessTargets;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import java.awt.Component;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JComboBox;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.ui.ColorScheme;

/** Boss lookup assumptions, edited here while configuration stores their existing keys. */
class FightOptionsPanel extends JPanel
{
	/** Pre-fight drains, counted in the collapsed section's heading when any is set. */
	private static final String[] PREPARATION = {"specVulnerability", "specTomeOfWater", "specElderMaul", "specDwh",
		"specEmberlight", "specArclight", "specTonalztics", "specBgsDamage", "specSeercullDamage", "specAyakDamage"};
	/**
	 * Options that only appear for some targets or complete raids. Players who don't open Fight would never
	 * learn they exist, so the Fight button flags them until they have been shown.
	 */
	private static final String[] TARGET_OPTIONS = {"autoRaid", "raidPartySize", "toaRaidLevel", "toaPathLevel",
		"toaStartingPathLevel", "toaWalkThePath", "toaMoreOverlords", "coxChallengeMode", "miningLevel", "toaPathOrder",
		"tobRole", "raidEquipmentLimit", "raidSpellbook", "quickSwitchLimit", "forinthrySurge"};
	/** Wide enough for the largest maximum shown here (2000 damage). */
	private static final int SPINNER_COLUMNS = 4;
	private final SettingRows settingRows;
	private final BestGearSetupConfig config;
	private final SidebarSection raidSection;
	/** What a raid or boss fight plan may pack: equipment slots, spellbook and quick-switch swaps. */
	private final SidebarSection kitSection;
	private final SidebarSection preparationSection;
	private final String wildernessTip;
	private final String spellbookTip;
	private boolean freezeSpellbookLocked;
	private Monster target = new Monster();
	/** The complete raid being planned; path order and roles only change complete raid plans. */
	private RaidPlanType raidPlan = RaidPlanType.SINGLE;
	private Runnable targetOptionsListener;

	/** @param items the config's item descriptors, from {@link net.runelite.client.config.ConfigManager#getConfigDescriptor} */
	FightOptionsPanel(BestGearSetupPlugin plugin, BestGearSetupConfig config, Collection<ConfigItemDescriptor> items)
	{
		this(plugin, config, items, new SidebarSection.Accordion());
	}

	FightOptionsPanel(BestGearSetupPlugin plugin, BestGearSetupConfig config, Collection<ConfigItemDescriptor> items,
		SidebarSection.Accordion accordion)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		this.config = config;
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Build controls as their one-level sections are populated.
		settingRows = new SettingRows(plugin, config, items, SPINNER_COLUMNS, this::updateVisibility);
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
		addRows(raidSection, "autoRaid", "raidPartySize", "toaRaidLevel", "toaPathLevel", "toaStartingPathLevel", "toaWalkThePath",
			"toaMoreOverlords", "coxChallengeMode", "miningLevel", "toaPathOrder", "tobRole");
		add(BestGearSetupPanel.left(raidSection));
		kitSection = accordion.create("kit", "Kit");
		kitSection.showAsEditor();
		addRows(kitSection, "raidEquipmentLimit", "raidSpellbook", "quickSwitchLimit");
		add(BestGearSetupPanel.left(kitSection));
		// Collapsed by default: most searches start from the target's full stats.
		preparationSection = accordion.create("preparation", "Pre-fight preparation");
		preparationSection.setToolTipText("Successful specials and defence reductions landed before the fight starts");
		addRows(preparationSection, "autoSpecs");
		addRows(preparationSection, PREPARATION);
		add(BestGearSetupPanel.left(preparationSection));
		wildernessTip = control("wilderness").getToolTipText();
		spellbookTip = control("raidSpellbook").getToolTipText();
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
		// A boss fight is planned solo, outside any raid, so only its kit settings apply.
		boolean fight = raidPlan.isBossFight();
		raidSection.getParent().setVisible(raid != RaidScaling.Raid.NONE || planned && !fight);
		kitSection.getParent().setVisible(planned);
		settingRows.row("raidPartySize").setVisible(raid != RaidScaling.Raid.NONE || planned);
		settingRows.row("toaRaidLevel").setVisible(raid == RaidScaling.Raid.TOA || raidPlan == RaidPlanType.TOA);
		settingRows.row("toaPathLevel").setVisible(raidPlan == RaidPlanType.SINGLE && raid == RaidScaling.Raid.TOA && RaidScaling.toaPath(target));
		settingRows.row("toaStartingPathLevel").setVisible(raidPlan == RaidPlanType.TOA);
		settingRows.row("toaWalkThePath").setVisible(raidPlan == RaidPlanType.TOA);
		settingRows.row("toaMoreOverlords").setVisible(raidPlan == RaidPlanType.TOA);
		settingRows.row("coxChallengeMode").setVisible(raidPlan == RaidPlanType.SINGLE && raid == RaidScaling.Raid.COX);
		settingRows.row("miningLevel").setVisible(raidPlan == RaidPlanType.COX_CM
			|| (raid == RaidScaling.Raid.COX && target.getName().contains("guardian")));
		settingRows.row("toaPathOrder").setVisible(raidPlan == RaidPlanType.TOA);
		settingRows.row("tobRole").setVisible(raidPlan == RaidPlanType.TOB);
		// Only these plans have quick-switch groups: the ToB Vasilias forms, the ToA baboons and a boss fight's phases.
		settingRows.row("quickSwitchLimit").setVisible(raidPlan == RaidPlanType.TOB || raidPlan == RaidPlanType.TOA || fight);
		boolean freeze = raidPlan == RaidPlanType.TOB
			&& ((RaidOptions.TobRole) ((JComboBox<?>) control("tobRole")).getSelectedItem()).freezes();
		if (freeze) { settingRows.showChoice("raidSpellbook", RaidSpellbook.ANCIENT); }
		else if (freezeSpellbookLocked) { settingRows.showChoice("raidSpellbook", config.raidSpellbook()); }
		freezeSpellbookLocked = freeze;
		control("raidSpellbook").setEnabled(!freeze);
		control("raidSpellbook").setToolTipText(freeze ? "Ancient is required for this role's Ice Barrage freezes." : spellbookTip);
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
		if (targetOptionsListener != null) { targetOptionsListener.run(); }
	}

	/** Run whenever the shown options may have changed, e.g. to flag {@link #targetOptions()} elsewhere. */
	void onTargetOptionsChanged(Runnable listener)
	{
		targetOptionsListener = listener;
	}

	/** The target-specific options shown for the current target or raid plan: display names by setting key. */
	Map<String, String> targetOptions()
	{
		Map<String, String> shown = new LinkedHashMap<>();
		for (String key : TARGET_OPTIONS)
		{
			if (shown(settingRows.row(key)))
			{
				shown.put(key, settingRows.name(key));
			}
		}
		return shown;
	}

	/** Visible within this panel; isShowing() would also need the sidebar on screen. */
	private boolean shown(Component row)
	{
		for (Component c = row; c != null && c != this; c = c.getParent())
		{
			if (!c.isVisible())
			{
				return false;
			}
		}
		return true;
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
