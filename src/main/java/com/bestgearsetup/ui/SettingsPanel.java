package com.bestgearsetup.ui;

import static com.bestgearsetup.ui.SettingRows.setting;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.ui.SettingRows.Setting;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	/** Every setting RuneLite's configuration page shows, with its getter. */
	private static final List<Setting> SETTINGS = Arrays.asList(
		setting("calcMode", BestGearSetupConfig::calcMode),
		setting("searchDepth", BestGearSetupConfig::searchDepth),
		setting("weaponHands", BestGearSetupConfig::weaponHands),
		setting(BestGearSetupConfig.BOSSES_ONLY_KEY, BestGearSetupConfig::bossesOnly),
		setting("ammoCount", BestGearSetupConfig::ammoCount),
		setting("styleStab", BestGearSetupConfig::styleStab),
		setting("styleSlash", BestGearSetupConfig::styleSlash),
		setting("styleCrush", BestGearSetupConfig::styleCrush),
		setting("styleRanged", BestGearSetupConfig::styleRanged),
		setting("styleAtlatl", BestGearSetupConfig::styleAtlatl),
		setting("styleMagic", BestGearSetupConfig::styleMagic),
		setting(BestGearSetupConfig.MEMBERS_ITEMS_KEY, BestGearSetupConfig::membersItems),
		setting(BestGearSetupConfig.DMM_ITEMS_KEY, BestGearSetupConfig::dmmItems),
		setting(BestGearSetupConfig.BOUNTY_HUNTER_ITEMS_KEY, BestGearSetupConfig::bountyHunterItems),
		setting(BestGearSetupConfig.BETA_ITEMS_KEY, BestGearSetupConfig::betaItems),
		setting("requireAtlatlAmmoRecovery", BestGearSetupConfig::requireAtlatlAmmoRecovery),
		setting("ancientMagicks", BestGearSetupConfig::ancientMagicks),
		setting("arceuusSpellbook", BestGearSetupConfig::arceuusSpellbook),
		setting("usePrayers", BestGearSetupConfig::usePrayers),
		setting("meleePotionChoice", BestGearSetupConfig::meleePotion),
		setting("rangedPotionChoice", BestGearSetupConfig::rangedPotion),
		setting("magicPotionChoice", BestGearSetupConfig::magicPotion),
		setting("raidPotions", BestGearSetupConfig::raidPotions),
		setting("divinePotions", BestGearSetupConfig::divinePotions),
		setting("repotBelow", BestGearSetupConfig::repotBelow),
		setting("weaponPoison", BestGearSetupConfig::weaponPoison),
		setting("charge", BestGearSetupConfig::charge),
		setting("markOfDarkness", BestGearSetupConfig::markOfDarkness),
		setting("sunfireRunes", BestGearSetupConfig::sunfireRunes),
		setting("requireFireProtection", BestGearSetupConfig::requireFireProtection),
		setting("antifire", BestGearSetupConfig::antifire),
		setting("protectMagic", BestGearSetupConfig::protectMagic),
		setting("attackXp", BestGearSetupConfig::attackXp),
		setting("strengthXp", BestGearSetupConfig::strengthXp),
		setting("defenceXp", BestGearSetupConfig::defenceXp),
		setting("fillMode", BestGearSetupConfig::fillMode),
		setting("defenceFocus", BestGearSetupConfig::defenceFocus),
		setting("fillMargin", BestGearSetupConfig::fillMargin),
		setting("showMenuOption", BestGearSetupConfig::showMenuOption),
		setting(BestGearSetupConfig.HIGHLIGHT_BANK_GEAR_KEY, BestGearSetupConfig::highlightBankGear),
		setting(BestGearSetupConfig.BANK_HIGHLIGHT_COLOR_KEY, BestGearSetupConfig::bankHighlightColor),
		setting(BestGearSetupConfig.HIGHLIGHT_INVENTORY_GEAR_KEY, BestGearSetupConfig::highlightInventoryGear),
		setting(BestGearSetupConfig.INVENTORY_HIGHLIGHT_COLOR_KEY, BestGearSetupConfig::inventoryHighlightColor));
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
		Map<String, Setting> settings = new HashMap<>();
		for (Setting entry : SETTINGS)
		{
			settings.put(entry.getKey(), entry);
		}
		List<ConfigItemDescriptor> shown = descriptor.getItems().stream()
			.filter(item -> !item.getItem().hidden())
			.sorted(Comparator.comparingInt(ConfigItemDescriptor::position))
			.collect(Collectors.toList());

		for (ConfigItemDescriptor item : shown)
		{
			if (item.getItem().section().isEmpty())
			{
				add(settingRows.add(settingFor(settings, item)));
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
					group.getContent().add(settingRows.add(settingFor(settings, item)));
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

	private static Setting settingFor(Map<String, Setting> settings, ConfigItemDescriptor item)
	{
		Setting setting = settings.get(item.key());
		if (setting == null)
		{
			throw new IllegalStateException("Setting " + item.key() + " has no getter in SettingsPanel");
		}
		return setting;
	}

	/** Show the saved values; called when configuration changes anywhere. */
	void refresh()
	{
		settingRows.refresh();
	}
}
