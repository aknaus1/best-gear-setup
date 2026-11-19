package com.bestgearsetup;

import com.bestgearsetup.calc.*;
import com.bestgearsetup.data.EncounterPhase;
import java.awt.Color;
import net.runelite.client.config.*;

@ConfigGroup(Options.GROUP)
public interface Options extends Config
{
	String GROUP = "bestgearsetup";
	String MODE_KEY = "mode";
	String BUDGET_KEY = "budget";
	String ON_TASK_KEY = "onSlayerTask";
	String BOSSES_ONLY_KEY = "bossesOnly";
	String PHASE_KEY = "encounterPhase";
	String POTION_KEY_PREFIX = "potion";
	String LOCKS_KEY = "locks";
	String EXCLUDED_KEY = "excluded";
	String BLOCKED_SPELLS_KEY = "blockedSpells";
	String FORCED_SPELLS_KEY = "forcedSpells";
	String HIGHLIGHT_BANK_GEAR_KEY = "highlightBankGear";
	String BANK_HIGHLIGHT_COLOR_KEY = "bankHighlightColor";
	String HIGHLIGHT_INVENTORY_GEAR_KEY = "highlightInventoryGear";
	String INVENTORY_HIGHLIGHT_COLOR_KEY = "inventoryHighlightColor";

	/**
	 * A search snapshot returns its captured value; a plain config or RuneLite's own config proxy uses the declared
	 * default. Private, so it is neither a config item nor routed through RuneLite's configuration handler.
	 */
	private <T> T value(String name, T defaultValue)
	{
		return Assumptions.value(this, name, defaultValue);
	}

	@ConfigSection(
		name = "Search options",
		description = "What to optimise for, search thoroughness and weapon restrictions",
		position = 0
	)
	String searchSection = "search";

	@ConfigSection(
		name = "Attack styles",
		description = "Attack types to include in setup results",
		position = 1
	)
	String optionsSection = "options";

	@ConfigSection(name = "Equipment", description = "Equipment allowed by world type and game mode",
		position = 2, closedByDefault = true)
	String equipmentSection = "equipment";

	@ConfigSection(name = "Spellbooks", description = "Spellbooks available for casting",
		position = 3, closedByDefault = true)
	String spellbooksSection = "spellbooks";

	@ConfigSection(
		name = "Experience filters",
		description = "Limit attack styles by the experience they give",
		position = 6,
		closedByDefault = true
	)
	String experienceSection = "experience";

	@ConfigSection(
		name = "Fill empty slots",
		description = "What to wear in slots that add no damage",
		position = 7,
		closedByDefault = true
	)
	String fillSection = "fill";

	@ConfigSection(name = "Dragonfire", description = "Protection required and assumed against dragonfire and wyvern breath",
		position = 5, closedByDefault = true)
	String dragonfireSection = "dragonfire";

	@ConfigSection(
		name = "Combat boosts",
		description = "Potions, prayers, thralls and other reusable combat assumptions",
		position = 4,
		closedByDefault = true
	)
	String boostSection = "boosts";

	@ConfigSection(name = "Display & interaction",
		description = "NPC right-click lookup and highlights for the selected setup",
		position = 8, closedByDefault = true)
	String displaySection = "display";

	@ConfigItem(
		keyName = "showMenuOption",
		name = "Right-click option",
		description = "Add a 'Best setup' option when right-clicking attackable NPCs",
		section = displaySection,
		position = 0
	)
	default boolean showMenuOption()
	{
		return value("showMenuOption", true);
	}

	@ConfigItem(keyName = HIGHLIGHT_BANK_GEAR_KEY, name = "Highlight bank gear",
		description = "Outline the selected setup's gear in your bank",
		section = displaySection, position = 1)
	default boolean highlightBankGear()
	{
		return value("highlightBankGear", true);
	}

	@ConfigItem(keyName = BANK_HIGHLIGHT_COLOR_KEY, name = "Bank color",
		description = "Outline color for the selected setup's bank items", section = displaySection, position = 2)
	default Color bankHighlightColor()
	{
		return value("bankHighlightColor", new Color(0x00, 0xFF, 0x80));
	}

	@ConfigItem(keyName = HIGHLIGHT_INVENTORY_GEAR_KEY, name = "Highlight inventory gear",
		description = "Outline the selected setup's gear in your inventory",
		section = displaySection, position = 3)
	default boolean highlightInventoryGear()
	{
		return value("highlightInventoryGear", true);
	}

	@ConfigItem(keyName = INVENTORY_HIGHLIGHT_COLOR_KEY, name = "Inventory color",
		description = "Outline color for the selected setup's inventory items", section = displaySection, position = 4)
	default Color inventoryHighlightColor()
	{
		return value("inventoryHighlightColor", new Color(0x00, 0xFF, 0x80));
	}

	@ConfigItem(keyName = "fillFood", name = "Fill inventory with food",
		description = "Fill free inventory slots with your best food", section = displaySection, position = 5)
	default boolean fillFood()
	{
		return value("fillFood", true);
	}

	// ------------------------------------------------------------ search

	/** Mode: Owned only, carried only, owned plus budget, or unlimited. Edited by its own sidebar control, so it needs no label. */
	@ConfigItem(keyName = MODE_KEY, name = "", description = "", hidden = true)
	default SearchMode mode()
	{
		return value("mode", SearchMode.BUDGET);
	}

	/** Budget: GP to spend on unowned items, e.g. 50m. Edited by its own sidebar control, so it needs no label. */
	@ConfigItem(keyName = BUDGET_KEY, name = "", description = "", hidden = true)
	default String budget()
	{
		return value("budget", "10m");
	}

	@ConfigItem(
		keyName = BOSSES_ONLY_KEY,
		name = "Search bosses only",

		description = "Monster search lists only bosses",
		section = searchSection,
		position = 3
	)
	default boolean bossesOnly()
	{
		return value("bossesOnly", false);
	}

	/** On slayer task: Apply slayer helmet / black mask bonuses. Detected per search from the Slayer task setting, so it needs no label. */
	@ConfigItem(keyName = ON_TASK_KEY, name = "", description = "", hidden = true)
	default boolean onSlayerTask()
	{
		return value("onSlayerTask", false);
	}

	@ConfigItem(
		keyName = "ancientMagicks",
		name = "Ancient Magicks",
		description = "Allow casting Ancient spells",
		section = spellbooksSection,
		position = 0
	)
	default boolean ancientMagicks()
	{
		return value("ancientMagicks", true);
	}

	@ConfigItem(
		keyName = "arceuusSpellbook",
		name = "Arceuus spellbook",
		description = "Allow casting Arceuus spells",
		section = spellbooksSection,
		position = 1
	)
	default boolean arceuusSpellbook()
	{
		return value("arceuusSpellbook", true);
	}

	@ConfigItem(
		keyName = "preventManualCast",
		name = "Prevent manual casting",
		description = "Only autocast spells; off also allows casting by hand",
		section = spellbooksSection,
		position = 2
	)
	default boolean preventManualCast()
	{
		return value("preventManualCast", false);
	}

	@ConfigItem(
		keyName = "membersItems",
		name = "Members items",
		description = "Turn off for free-to-play worlds",
		section = equipmentSection,
		position = 0
	)
	default boolean membersItems()
	{
		return value("membersItems", true);
	}

	@ConfigItem(
		keyName = "ammoCount",
		name = "Ammo to price",

		description = "Ammo to price in the budget; 0 ignores it",
		section = searchSection,
		position = 4
	)
	@Range(max = 100_000)
	default int ammoCount()
	{
		return value("ammoCount", 0);
	}

	@ConfigItem(keyName = "killsPerTrip", name = "Kills per trip", hidden = true,
		description = "Kills one trip's supplies cover")
	@Range(min = 1, max = 1000)
	default int killsPerTrip()
	{
		return value("killsPerTrip", 1);
	}

	@ConfigItem(keyName = "currentHitpoints", name = "Your HP (0 = full)", hidden = true,
		description = "HP for Dharok and ruby bolts; 0 is full")
	@Range(max = 99)
	default int currentHitpoints()
	{
		return value("currentHitpoints", 0);
	}

	@ConfigItem(keyName = "wilderness", name = "In Wilderness", hidden = true,
		description = "Plan a Wilderness fight, with revenant weapon bonuses")
	default boolean wilderness()
	{
		return value("wilderness", false);
	}

	@ConfigItem(keyName = "limitWildernessRisk", name = "Limit expensive items", hidden = true,
		description = "Cap expensive items in Wilderness setups, specs included")
	default boolean limitWildernessRisk()
	{
		return value("limitWildernessRisk", false);
	}

	@ConfigItem(keyName = "maxExpensiveItems", name = "Expensive items allowed", hidden = true,
		description = "Expensive items allowed in a Wilderness setup")
	@Range(max = 11)
	default int maxExpensiveItems()
	{
		return value("maxExpensiveItems", 3);
	}

	@ConfigItem(keyName = "expensiveItemThreshold", name = "Expensive at (GP)", hidden = true,
		description = "Price at which an item counts as expensive")
	default String expensiveItemThreshold()
	{
		return value("expensiveItemThreshold", "1m");
	}

	@ConfigItem(keyName = "charge", name = "Assume Charge",
		description = "Boost god spells when wearing the matching god cape.", section = boostSection, position = 8)
	default boolean charge()
	{
		return value("charge", false);
	}

	@ConfigItem(keyName = "markOfDarkness", name = "Mark of Darkness",
		description = "Assume Mark of Darkness before demonbane spells",
		section = boostSection, position = 9)
	default boolean markOfDarkness()
	{
		return value("markOfDarkness", true);
	}

	@ConfigItem(keyName = "sunfireRunes", name = "Use sunfire runes",
		description = "Raise fire spell minimum hits to 10% of max", section = boostSection, position = 10)
	default boolean sunfireRunes()
	{
		return value("sunfireRunes", false);
	}

	/** Kandarin hard diary: Increase enchanted bolt proc chances by 10%. Detected per search from the account's diaries, so it needs no label. */
	@ConfigItem(keyName = "kandarinDiary", name = "", description = "", hidden = true)
	default boolean kandarinDiary()
	{
		return value("kandarinDiary", false);
	}

	@ConfigItem(keyName = "killSpecials", name = "Use special attacks",
		description = "Mix the best special attacks into each kill", section = searchSection, position = 6)
	default boolean killSpecials()
	{
		return value("killSpecials", true);
	}

	@ConfigItem(keyName = "specEnergy", name = "Spec energy",
		description = "Regenerating for repeated kills; Full bar for single kills", section = searchSection, position = 7)
	default SpecEnergy specEnergy()
	{
		return value("specEnergy", SpecEnergy.REGENERATION);
	}

	@ConfigItem(keyName = "drainSpecs", name = "Drain specs",
		description = "How defence-draining specs are ranked against misses",
		section = searchSection, position = 8)
	default DrainSpecs drainSpecs()
	{
		return value("drainSpecs", DrainSpecs.EXPECTED);
	}

	@ConfigItem(keyName = "weaponPoison", name = "Weapon poison",
		description = "Poison assumed on poisonable weapons and ammo", section = boostSection, position = 7)
	default WeaponPoison weaponPoison()
	{
		return value("weaponPoison", WeaponPoison.NONE);
	}

	@ConfigItem(keyName = "forinthrySurge", name = "Forinthry Surge", hidden = true,
		description = "Amulet of avarice gives 35% against revenants")
	default boolean forinthrySurge()
	{
		return value("forinthrySurge", false);
	}

	@ConfigItem(keyName = "soulreaperStacks", name = "Soulreaper stacks", hidden = true,
		description = "Current soulreaper stacks (0-5); adds Strength separately from prayer.")
	@Range(max = 5)
	default int soulreaperStacks()
	{
		return value("soulreaperStacks", 0);
	}

	@ConfigItem(keyName = "miningLevel", name = "Mining (0 = yours)", hidden = true,
		description = "Mining level for CoX guardians; 0 uses yours")
	@Range(max = 99)
	default int miningLevel()
	{
		return value("miningLevel", 0);
	}

	@ConfigItem(keyName = "raidPartySize", name = "Raid party size", hidden = true,
		description = "Players in the raid; scales boss HP and levels")
	@Range(min = 1, max = 100)
	default int raidPartySize()
	{
		return value("raidPartySize", 1);
	}

	@ConfigItem(keyName = "toaRaidLevel", name = "ToA raid level", hidden = true,
		description = "ToA raid level; scales boss HP and defence")
	@Range(max = 600)
	default int toaRaidLevel()
	{
		return value("toaRaidLevel", 0);
	}

	@ConfigItem(keyName = "toaPathLevel", name = "ToA path level", hidden = true,
		description = "Path level of the selected ToA boss (0-6)")
	@Range(max = 6)
	default int toaPathLevel()
	{
		return value("toaPathLevel", 0);
	}

	@ConfigItem(keyName = "coxChallengeMode", name = "CoX Challenge Mode", hidden = true,
		description = "Apply Challenge Mode scaling to Chambers of Xeric targets.")
	default boolean coxChallengeMode()
	{
		return value("coxChallengeMode", false);
	}

	/** Boss phase: Temporary boss state; only the named boss changes. Edited by its own sidebar control, so it needs no label. */
	@ConfigItem(keyName = PHASE_KEY, name = "", description = "", hidden = true)
	default EncounterPhase encounterPhase()
	{
		return value("encounterPhase", EncounterPhase.STANDARD);
	}

	@ConfigItem(keyName = "aoe", name = "Enable AoE", hidden = true,
		description = "Compare total damage to grouped identical enemies in multicombat")
	default boolean aoe()
	{
		return value("aoe", false);
	}

	@ConfigItem(keyName = "aoeTargets", name = "Grouped enemies", hidden = true,
		description = "Total enemies, including the selected target")
	@Range(min = 1, max = 12)
	default int aoeTargets()
	{
		return value("aoeTargets", 9);
	}

	@ConfigItem(keyName = "requireFireProtection", name = "Require fire protection",
		description = "Exclude gear without dragonfire or wyvern protection",
		section = dragonfireSection, position = 0)
	default boolean requireFireProtection()
	{
		return value("requireFireProtection", true);
	}

	@ConfigItem(keyName = "antifire", name = "Antifire potion",
		description = "Active antifire potion (extended versions give the same protection)", section = dragonfireSection, position = 1)
	default Antifire antifire()
	{
		return value("antifire", Antifire.REGULAR);
	}

	@ConfigItem(keyName = "protectMagic", name = "Protect from Magic",
		description = "Assume this overhead prayer is active",
		section = dragonfireSection, position = 2)
	default boolean protectMagic()
	{
		return value("protectMagic", true);
	}

	@ConfigItem(keyName = "dmmItems", name = "DMM equipment", description = "Include Deadman-only equipment",
		section = equipmentSection, position = 1)
	default boolean dmmItems()
	{
		return value("dmmItems", false);
	}

	@ConfigItem(keyName = "betaItems", name = "Beta equipment", description = "Include beta equipment when present in the bundled data",
		section = equipmentSection, position = 3)
	default boolean betaItems()
	{
		return value("betaItems", false);
	}

	@ConfigItem(keyName = "bountyHunterItems", name = "BH equipment",
		description = "Include Bounty Hunter-only equipment, including Vesta's blighted longsword",
		section = equipmentSection, position = 2)
	default boolean bountyHunterItems()
	{
		return value("bountyHunterItems", false);
	}

	// Retain the original key so existing saved preferences continue to apply.
	@ConfigItem(keyName = "requireAtlatlAmmoRecovery", name = "Require Ava's / quiver",
		description = "Ammo-firing ranged setups wear an ammo-saving cape when available",
		section = equipmentSection, position = 4)
	default boolean requireAtlatlAmmoRecovery()
	{
		return value("requireAtlatlAmmoRecovery", true);
	}

	// ------------------------------------------------------------ options

	@ConfigItem(keyName = "styleStab", name = "Stab", description = "Allow stab attacks", section = optionsSection, position = 0)
	default boolean styleStab()
	{
		return value("styleStab", true);
	}

	@ConfigItem(keyName = "styleSlash", name = "Slash", description = "Allow slash attacks", section = optionsSection, position = 1)
	default boolean styleSlash()
	{
		return value("styleSlash", true);
	}

	@ConfigItem(keyName = "styleCrush", name = "Crush", description = "Allow crush attacks", section = optionsSection, position = 2)
	default boolean styleCrush()
	{
		return value("styleCrush", true);
	}

	@ConfigItem(keyName = "styleRanged", name = "Ranged", description = "Allow ranged attacks", section = optionsSection, position = 3)
	default boolean styleRanged()
	{
		return value("styleRanged", true);
	}

	@ConfigItem(keyName = "styleMagic", name = "Magic", description = "Allow magic attacks", section = optionsSection, position = 5)
	default boolean styleMagic()
	{
		return value("styleMagic", true);
	}

	@ConfigItem(keyName = "styleAtlatl", name = "Atlatl",
		description = "Allow the eclipse atlatl as its own style",
		section = optionsSection, position = 4)
	default boolean styleAtlatl()
	{
		return value("styleAtlatl", true);
	}

	@ConfigItem(
		keyName = "weaponHands",
		name = "Weapon hands",
		description = "Restrict to one-handed or two-handed weapons",
		section = searchSection,
		position = 2
	)
	default WeaponHands weaponHands()
	{
		return value("weaponHands", WeaponHands.ANY);
	}

	@ConfigItem(
		keyName = "calcMode",
		name = "Optimise for",
		description = "What to maximise. DPS is used to break ties",
		section = searchSection,
		position = 0
	)
	default CalcMode calcMode()
	{
		return value("calcMode", CalcMode.DPS);
	}

	// ------------------------------------------------------------ experience

	@ConfigItem(keyName = "attackXp", name = "Attack XP", description = "Allow styles that give Attack experience",
		section = experienceSection, position = 20)
	default boolean attackXp()
	{
		return value("attackXp", true);
	}

	@ConfigItem(keyName = "strengthXp", name = "Strength XP", description = "Allow styles that give Strength experience",
		section = experienceSection, position = 21)
	default boolean strengthXp()
	{
		return value("strengthXp", true);
	}

	@ConfigItem(keyName = "defenceXp", name = "Defence XP",
		description = "Allow styles that give Defence experience; off for pures",
		section = experienceSection, position = 22)
	default boolean defenceXp()
	{
		return value("defenceXp", true);
	}

	// ------------------------------------------------------------ fill

	@ConfigItem(
		keyName = "fillMode",
		name = "Fill with",
		description = "What to wear in slots that add no damage",
		section = fillSection,
		position = 30
	)
	default FillMode fillMode()
	{
		return value("fillMode", FillMode.NONE);
	}

	@ConfigItem(
		keyName = "defenceFocus",
		name = "Defence",
		description = "Which defence to maximise when filling with defence",
		section = fillSection,
		position = 31
	)
	default DefenceFocus defenceFocus()
	{
		return value("defenceFocus", DefenceFocus.TARGET);
	}

	@ConfigItem(
		keyName = "fillMargin",
		name = "Max DPS loss",
		description = "DPS percent you'll give up for fill items",
		section = fillSection,
		position = 32
	)
	@Units(Units.PERCENT)
	default double fillMargin()
	{
		return value("fillMargin", 0.0);
	}

	// ------------------------------------------------------------ specials

	@ConfigItem(keyName = "autoSpecs", name = "Use active defence",
		description = "Read Defence drains from specs landed on your target",
		hidden = true)
	default boolean autoSpecs()
	{
		return value("autoSpecs", true);
	}

	@ConfigItem(keyName = "specVulnerability", name = "Vulnerability", description = "Target is under Vulnerability (-10% Defence)",
		hidden = true)
	default boolean specVulnerability()
	{
		return value("specVulnerability", false);
	}

	@ConfigItem(keyName = "specTomeOfWater", name = "...with tome of water", description = "Vulnerability cast with the tome of water (-15% Defence)",
		hidden = true)
	default boolean specTomeOfWater()
	{
		return value("specTomeOfWater", false);
	}

	@ConfigItem(keyName = "specElderMaul", name = "Elder maul hits", description = "Successful elder maul specials (-35% Defence each)",
		hidden = true)
	@Range(max = 10)
	default int specElderMaul()
	{
		return value("specElderMaul", 0);
	}

	@ConfigItem(keyName = "specDwh", name = "DWH hits", description = "Successful dragon warhammer specials (-30% Defence each)",
		hidden = true)
	@Range(max = 10)
	default int specDwh()
	{
		return value("specDwh", 0);
	}

	@ConfigItem(keyName = "specEmberlight", name = "Emberlight hits", description = "Successful emberlight specials",
		hidden = true)
	@Range(max = 20)
	default int specEmberlight()
	{
		return value("specEmberlight", 0);
	}

	@ConfigItem(keyName = "specArclight", name = "Arclight hits", description = "Successful arclight specials (-5% Attack/Strength/Defence, 10% on demons)",
		hidden = true)
	@Range(max = 20)
	default int specArclight()
	{
		return value("specArclight", 0);
	}

	@ConfigItem(keyName = "specTonalztics", name = "Tonalztics hits", description = "Successful Tonalztics of Ralos special hits",
		hidden = true)
	@Range(max = 20)
	default int specTonalztics()
	{
		return value("specTonalztics", 0);
	}

	@ConfigItem(keyName = "specBgsDamage", name = "BGS damage", description = "Total damage dealt with Bandos godsword specials",
		hidden = true)
	@Range(max = 2000)
	default int specBgsDamage()
	{
		return value("specBgsDamage", 0);
	}

	@ConfigItem(keyName = "specSeercullDamage", name = "Seercull damage", description = "Total damage dealt with seercull specials (drains Magic level)",
		hidden = true)
	@Range(max = 2000)
	default int specSeercullDamage()
	{
		return value("specSeercullDamage", 0);
	}

	@ConfigItem(keyName = "specAyakDamage", name = "Eye of Ayak damage", description = "Total damage dealt with Eye of Ayak specials",
		hidden = true)
	@Range(max = 2000)
	default int specAyakDamage()
	{
		return value("specAyakDamage", 0);
	}

	// ------------------------------------------------------------ boosts

	@ConfigItem(
		keyName = "usePrayers",
		name = "Offensive prayers",
		description = "Assume the best offensive prayer you can use",
		section = boostSection,
		position = 0
	)
	default boolean usePrayers()
	{
		return value("usePrayers", true);
	}

	@ConfigItem(keyName = "excludedPrayers", name = "Excluded prayers",
		description = "Offensive prayers never assumed, comma-separated (e.g. Piety, Rigour)", section = boostSection, position = 1)
	default String excludedPrayers()
	{
		return value("excludedPrayers", "");
	}

	@ConfigItem(
		keyName = "thrall",
		name = "Thralls",
		description = "Add your best thrall (Arceuus, A Kingdom Divided)",
		section = boostSection,
		position = 6
	)
	default boolean thrall()
	{
		return value("thrall", true);
	}

	@ConfigItem(
		keyName = "raidPotions",
		name = "Include raid potions",
		description = "Let Best available use overloads and smelling salts",
		section = boostSection,
		position = 2
	)
	default boolean raidPotions()
	{
		return value("raidPotions", true);
	}


	@ConfigItem(keyName = "taskMode", name = "Slayer task", description = "Auto matches your slayer task; On and Off override", hidden = true)
	default AutoState taskMode() { return value("taskMode", AutoState.AUTO); }

	@ConfigItem(keyName = "autoRaid", name = "Use active raid", description = "Read scaling from a matching live raid target", hidden = true)
	default boolean autoRaid() { return value("autoRaid", true); }

	@ConfigItem(keyName = "meleePotionChoice", name = "Melee potion", description = "Best available follows ownership; a named boost overrides it", section = boostSection, position = 3)
	default PotionOptions.Melee meleePotion() { return value("meleePotionChoice", PotionOptions.Melee.BEST); }

	@ConfigItem(keyName = "rangedPotionChoice", name = "Ranged potion", description = "Best available follows ownership; a named boost overrides it", section = boostSection, position = 4)
	default PotionOptions.Ranged rangedPotion() { return value("rangedPotionChoice", PotionOptions.Ranged.BEST); }

	@ConfigItem(keyName = "magicPotionChoice", name = "Magic potion", description = "Best available follows ownership; a named boost overrides it", section = boostSection, position = 5)
	default PotionOptions.Magic magicPotion() { return value("magicPotionChoice", PotionOptions.Magic.BEST); }

	// ------------------------------------------------------------ managed from the side panel

	@ConfigItem(keyName = POTION_KEY_PREFIX + "MELEE", name = "", description = "", hidden = true)
	default String potionMelee()
	{
		return value(POTION_KEY_PREFIX + "MELEE", "best");
	}

	@ConfigItem(keyName = POTION_KEY_PREFIX + "RANGED", name = "", description = "", hidden = true)
	default String potionRanged()
	{
		return value(POTION_KEY_PREFIX + "RANGED", "best");
	}

	@ConfigItem(keyName = POTION_KEY_PREFIX + "MAGIC", name = "", description = "", hidden = true)
	default String potionMagic()
	{
		return value(POTION_KEY_PREFIX + "MAGIC", "best");
	}

	@ConfigItem(keyName = LOCKS_KEY, name = "", description = "", hidden = true)
	default String locks()
	{
		return value("locks", "");
	}

	@ConfigItem(keyName = EXCLUDED_KEY, name = "", description = "", hidden = true)
	default String excluded()
	{
		return value("excluded", "");
	}

	@ConfigItem(keyName = BLOCKED_SPELLS_KEY, name = "", description = "", hidden = true)
	default String blockedSpells()
	{
		return value("blockedSpells", "");
	}

	@ConfigItem(keyName = FORCED_SPELLS_KEY, name = "", description = "", hidden = true)
	default String forcedSpells()
	{
		return value("forcedSpells", "");
	}
}
