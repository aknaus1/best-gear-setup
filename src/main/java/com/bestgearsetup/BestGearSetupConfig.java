package com.bestgearsetup;

import com.bestgearsetup.calc.CalcMode;
import com.bestgearsetup.calc.Antifire;
import com.bestgearsetup.calc.DefenceFocus;
import com.bestgearsetup.calc.DrainSpecs;
import com.bestgearsetup.calc.FillMode;
import com.bestgearsetup.calc.SearchDepth;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.calc.SpecEnergy;
import com.bestgearsetup.calc.WeaponHands;
import com.bestgearsetup.calc.WeaponPoison;
import com.bestgearsetup.data.EncounterPhase;
import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(BestGearSetupConfig.GROUP)
public interface BestGearSetupConfig extends Config
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
	String MEMBERS_ITEMS_KEY = "membersItems";
	String DMM_ITEMS_KEY = "dmmItems";
	String BETA_ITEMS_KEY = "betaItems";
	String BOUNTY_HUNTER_ITEMS_KEY = "bountyHunterItems";
	String HIGHLIGHT_BANK_GEAR_KEY = "highlightBankGear";
	String BANK_HIGHLIGHT_COLOR_KEY = "bankHighlightColor";
	String HIGHLIGHT_INVENTORY_GEAR_KEY = "highlightInventoryGear";
	String INVENTORY_HIGHLIGHT_COLOR_KEY = "inventoryHighlightColor";

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

	@ConfigSection(name = "Spellbooks", description = "Spellbooks available for autocasting",
		position = 3, closedByDefault = true)
	String spellbooksSection = "spellbooks";

	@ConfigSection(
		name = "Experience filters",
		description = "Restrict attack styles by the experience they give (e.g. for pures)",
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
		return true;
	}

	@ConfigItem(keyName = HIGHLIGHT_BANK_GEAR_KEY, name = "Highlight bank gear",
		description = "Outline equipment and ammunition from the selected setup in your bank",
		section = displaySection, position = 1)
	default boolean highlightBankGear()
	{
		return true;
	}

	@ConfigItem(keyName = BANK_HIGHLIGHT_COLOR_KEY, name = "Bank color",
		description = "Outline color for the selected setup's bank items", section = displaySection, position = 2)
	default Color bankHighlightColor()
	{
		return new Color(0x00, 0xFF, 0x80);
	}

	@ConfigItem(keyName = HIGHLIGHT_INVENTORY_GEAR_KEY, name = "Highlight inventory gear",
		description = "Outline equipment and ammunition from the selected setup in your inventory",
		section = displaySection, position = 3)
	default boolean highlightInventoryGear()
	{
		return true;
	}

	@ConfigItem(keyName = INVENTORY_HIGHLIGHT_COLOR_KEY, name = "Inventory color",
		description = "Outline color for the selected setup's inventory items", section = displaySection, position = 4)
	default Color inventoryHighlightColor()
	{
		return new Color(0x00, 0xFF, 0x80);
	}

	// ------------------------------------------------------------ search

	@ConfigItem(
		keyName = MODE_KEY,
		name = "Mode",
		hidden = true,
		description = "Owned items only, owned items plus a budget, or unlimited best in slot",
		section = searchSection,
		position = 1
	)
	default SearchMode mode()
	{
		return SearchMode.BUDGET;
	}

	@ConfigItem(
		keyName = BUDGET_KEY,
		name = "Budget",
		hidden = true,
		description = "GP to spend on items you do not own, e.g. 50m, 750k, 1.2b",
		section = searchSection,
		position = 2
	)
	default String budget()
	{
		return "10m";
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
		return false;
	}

	@ConfigItem(
		keyName = ON_TASK_KEY,
		name = "On slayer task",
		hidden = true,
		description = "Apply slayer helmet / black mask bonuses",
		section = searchSection,
		position = 4
	)
	default boolean onSlayerTask()
	{
		return false;
	}

	@ConfigItem(
		keyName = "ancientMagicks",
		name = "Ancient Magicks",
		description = "Allow autocasting Ancient spells",
		section = spellbooksSection,
		position = 0
	)
	default boolean ancientMagicks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "arceuusSpellbook",
		name = "Arceuus spellbook",
		description = "Allow autocasting Arceuus spells",
		section = spellbooksSection,
		position = 1
	)
	default boolean arceuusSpellbook()
	{
		return true;
	}

	@ConfigItem(
		keyName = MEMBERS_ITEMS_KEY,
		name = "Members items",
		description = "Turn off for free-to-play worlds",
		section = equipmentSection,
		position = 0
	)
	default boolean membersItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ammoCount",
		name = "Ammo to price",

		description = "Arrows / bolts / darts to include in the budget (e.g. 1000). 0 ignores ammo cost",
		section = searchSection,
		position = 4
	)
	@Range(max = 100_000)
	default int ammoCount()
	{
		return 0;
	}

	@ConfigItem(keyName = "currentHitpoints", name = "Your HP (0 = full)", hidden = true,
		description = "HP snapshot for Dharok and ruby bolts. Zero uses your base maximum HP.", section = searchSection, position = 18)
	@Range(max = 99)
	default int currentHitpoints()
	{
		return 0;
	}

	@ConfigItem(keyName = "wilderness", name = "In Wilderness", hidden = true,
		description = "Plan a Wilderness fight: enable charged revenant weapon bonuses and any selected expensive-item limit.", section = searchSection, position = 20)
	default boolean wilderness()
	{
		return false;
	}

	@ConfigItem(keyName = "limitWildernessRisk", name = "Limit expensive items", hidden = true,
		description = "In Wilderness searches, cap all equipped expensive items, including owned gear. No protected items are subtracted.")
	default boolean limitWildernessRisk()
	{
		return false;
	}

	@ConfigItem(keyName = "maxExpensiveItems", name = "Expensive items allowed", hidden = true,
		description = "Maximum equipped expensive items in a Wilderness setup. Zero allows only items below the threshold.")
	@Range(min = 0, max = 11)
	default int maxExpensiveItems()
	{
		return 3;
	}

	@ConfigItem(keyName = "expensiveItemThreshold", name = "Expensive at (GP)", hidden = true,
		description = "Items worth this much or more count as expensive (e.g. 500k, 1m). Uses equipment acquisition prices, including owned items; unknown prices count as expensive. Ammo uses its per-item price.")
	default String expensiveItemThreshold()
	{
		return "1m";
	}

	@ConfigItem(keyName = "charge", name = "Assume Charge",
		description = "Boost god spells when wearing the matching god cape.", section = boostSection, position = 7)
	default boolean charge()
	{
		return false;
	}

	@ConfigItem(keyName = "markOfDarkness", name = "Mark of Darkness",
		description = "Assume Mark of Darkness is cast before demonbane spells (higher accuracy and damage against demons).",
		section = boostSection, position = 8)
	default boolean markOfDarkness()
	{
		return true;
	}

	@ConfigItem(keyName = "sunfireRunes", name = "Use sunfire runes",
		description = "Raise fire spell minimum rolls to 10% of the maximum before tome effects.", section = boostSection, position = 9)
	default boolean sunfireRunes()
	{
		return false;
	}

	@ConfigItem(keyName = "kandarinDiary", name = "Kandarin hard diary", hidden = true,
		description = "Increase enchanted bolt proc chances by 10%.", section = searchSection, position = 24)
	default boolean kandarinDiary()
	{
		return false;
	}

	@ConfigItem(keyName = "killSpecials", name = "Use special attacks",
		description = "Mix special attacks into each kill, switching to the best spec weapon of the same combat style "
			+ "(and an off-hand after a two-handed weapon; bought within the budget). Draining specs open the kill so "
			+ "the rest is fought at the drained stats; lightbearer is worn when its faster regeneration pays. Counted "
			+ "in DPS, TTK and kills/hour.", section = searchSection, position = 6)
	default boolean killSpecials()
	{
		return true;
	}

	@ConfigItem(keyName = "specEnergy", name = "Spec energy",
		description = "Regenerating: steady-state energy over a trip (10% every 30 seconds), for repeated kills. "
			+ "Full bar: start every kill at 100%, for single boss kills.", section = searchSection, position = 7)
	default SpecEnergy specEnergy()
	{
		return SpecEnergy.REGENERATION;
	}

	@ConfigItem(keyName = "drainSpecs", name = "Drain specs",
		description = "How defence-draining specs (dragon warhammer, elder maul, Bandos godsword...) are ranked. "
			+ "Expected: the average kill, weighting misses by their chance. Ignore drain: score the damage only. "
			+ "Worst case: assume every drain misses, so a drain spec is only used if it pays even when it never lands.",
		section = searchSection, position = 8)
	default DrainSpecs drainSpecs()
	{
		return DrainSpecs.EXPECTED;
	}

	@ConfigItem(keyName = "weaponPoison", name = "Weapon poison",
		description = "Poison assumed on poisonable daggers, spears, hastae, darts, knives, javelins, arrows and "
			+ "bolts (the bundled data lists only unpoisoned items).", section = boostSection, position = 6)
	default WeaponPoison weaponPoison()
	{
		return WeaponPoison.NONE;
	}

	@ConfigItem(keyName = "forinthrySurge", name = "Forinthry Surge", hidden = true,
		description = "Amulet of avarice gives 35% instead of 20% against revenants.", section = searchSection, position = 32)
	default boolean forinthrySurge()
	{
		return false;
	}

	@ConfigItem(keyName = "soulreaperStacks", name = "Soulreaper stacks", hidden = true,
		description = "Current soulreaper stacks (0-5); adds Strength separately from prayer.", section = searchSection, position = 25)
	@Range(max = 5)
	default int soulreaperStacks()
	{
		return 0;
	}

	@ConfigItem(keyName = "miningLevel", name = "Mining (0 = yours)", hidden = true,
		description = "Mining level for CoX guardian damage. Zero uses your base Mining level, or 99 when logged out.",
		section = searchSection, position = 26)
	@Range(max = 99)
	default int miningLevel()
	{
		return 0;
	}

	@ConfigItem(keyName = "raidPartySize", name = "Raid party size", hidden = true,
		description = "Players in the raid. Scales CoX levels and HP, ToB HP (normal/hard below 3 count as 3) and ToA HP.",
		section = searchSection, position = 27)
	@Range(min = 1, max = 100)
	default int raidPartySize()
	{
		return 1;
	}

	@ConfigItem(keyName = "toaRaidLevel", name = "ToA raid level", hidden = true,
		description = "Tombs of Amascut raid level (invocations). Scales boss HP and their defence rolls.",
		section = searchSection, position = 28)
	@Range(max = 600)
	default int toaRaidLevel()
	{
		return 0;
	}

	@ConfigItem(keyName = "toaPathLevel", name = "ToA path level", hidden = true,
		description = "Path level of the selected ToA boss (0-6). Scales path boss HP.",
		section = searchSection, position = 29)
	@Range(max = 6)
	default int toaPathLevel()
	{
		return 0;
	}

	@ConfigItem(keyName = "coxChallengeMode", name = "CoX Challenge Mode", hidden = true,
		description = "Apply Challenge Mode scaling to Chambers of Xeric targets.",
		section = searchSection, position = 30)
	default boolean coxChallengeMode()
	{
		return false;
	}

	@ConfigItem(keyName = PHASE_KEY, name = "Boss phase", hidden = true,
		description = "Select a temporary boss state. Only the named boss is affected.",
		section = searchSection, position = 31)
	default EncounterPhase encounterPhase()
	{
		return EncounterPhase.STANDARD;
	}

	@ConfigItem(keyName = "aoe", name = "Enable AoE", hidden = true,
		description = "Compare total damage to grouped identical enemies in multicombat", section = searchSection, position = 13)
	default boolean aoe()
	{
		return false;
	}

	@ConfigItem(keyName = "aoeTargets", name = "Grouped enemies", hidden = true,
		description = "Total enemies, including the selected target. Each attack's own target limit applies.",
		section = searchSection, position = 14)
	@Range(min = 1, max = 12)
	default int aoeTargets()
	{
		return 9;
	}

	@ConfigItem(keyName = "requireFireProtection", name = "Require fire protection",
		description = "Exclude gear without suitable dragonfire or wyvern protection. Boss chip damage may remain.",
		section = dragonfireSection, position = 0)
	default boolean requireFireProtection()
	{
		return true;
	}

	@ConfigItem(keyName = "antifire", name = "Antifire potion",
		description = "Active antifire potion (extended versions give the same protection)", section = dragonfireSection, position = 1)
	default Antifire antifire()
	{
		return Antifire.SUPER;
	}

	@ConfigItem(keyName = "protectMagic", name = "Protect from Magic",
		description = "Assume this overhead is active; it does not protect against metal dragonfire or wyvern icy breath fully.",
		section = dragonfireSection, position = 2)
	default boolean protectMagic()
	{
		return true;
	}

	@ConfigItem(keyName = DMM_ITEMS_KEY, name = "DMM equipment", description = "Include Deadman-only equipment",
		section = equipmentSection, position = 1)
	default boolean dmmItems()
	{
		return false;
	}

	@ConfigItem(keyName = BETA_ITEMS_KEY, name = "Beta equipment", description = "Include beta equipment when present in the bundled data",
		section = equipmentSection, position = 3)
	default boolean betaItems()
	{
		return false;
	}

	@ConfigItem(keyName = BOUNTY_HUNTER_ITEMS_KEY, name = "BH equipment",
		description = "Include Bounty Hunter-only equipment, including Vesta's blighted longsword",
		section = equipmentSection, position = 2)
	default boolean bountyHunterItems()
	{
		return false;
	}

	// ------------------------------------------------------------ options

	@ConfigItem(keyName = "styleStab", name = "Stab", description = "Allow stab attacks", section = optionsSection, position = 0)
	default boolean styleStab()
	{
		return true;
	}

	@ConfigItem(keyName = "styleSlash", name = "Slash", description = "Allow slash attacks", section = optionsSection, position = 1)
	default boolean styleSlash()
	{
		return true;
	}

	@ConfigItem(keyName = "styleCrush", name = "Crush", description = "Allow crush attacks", section = optionsSection, position = 2)
	default boolean styleCrush()
	{
		return true;
	}

	@ConfigItem(keyName = "styleRanged", name = "Ranged", description = "Allow ranged attacks", section = optionsSection, position = 3)
	default boolean styleRanged()
	{
		return true;
	}

	@ConfigItem(keyName = "styleMagic", name = "Magic", description = "Allow magic attacks", section = optionsSection, position = 5)
	default boolean styleMagic()
	{
		return true;
	}

	@ConfigItem(keyName = "styleAtlatl", name = "Atlatl",
		description = "Allow the eclipse atlatl (ranged accuracy, melee strength damage) as its own style",
		section = optionsSection, position = 4)
	default boolean styleAtlatl()
	{
		return true;
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
		return WeaponHands.ANY;
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
		return CalcMode.DPS;
	}

	@ConfigItem(
		keyName = "searchDepth",
		name = "Search depth",
		description = "Fast: quickest, may miss the absolute best. Best: slowest, recommended for low budgets",
		section = searchSection,
		position = 1
	)
	default SearchDepth searchDepth()
	{
		return SearchDepth.NORMAL;
	}

	// ------------------------------------------------------------ experience

	@ConfigItem(keyName = "attackXp", name = "Attack XP", description = "Allow styles that give Attack experience",
		section = experienceSection, position = 20)
	default boolean attackXp()
	{
		return true;
	}

	@ConfigItem(keyName = "strengthXp", name = "Strength XP", description = "Allow styles that give Strength experience",
		section = experienceSection, position = 21)
	default boolean strengthXp()
	{
		return true;
	}

	@ConfigItem(keyName = "defenceXp", name = "Defence XP",
		description = "Allow styles that give Defence experience (defensive, controlled, long range). Turn off for pures",
		section = experienceSection, position = 22)
	default boolean defenceXp()
	{
		return true;
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
		return FillMode.NONE;
	}

	@ConfigItem(
		keyName = "defenceFocus",
		name = "Defence",
		description = "Which defence to maximise when filling with defence. 'Target' weights by the monster's attacks",
		section = fillSection,
		position = 31
	)
	default DefenceFocus defenceFocus()
	{
		return DefenceFocus.TARGET;
	}

	@ConfigItem(
		keyName = "fillMargin",
		name = "Max DPS loss",
		description = "DPS you are willing to give up for fill items, in percent (e.g. 0.5)",
		section = fillSection,
		position = 32
	)
	@Units(Units.PERCENT)
	default double fillMargin()
	{
		return 0;
	}

	// ------------------------------------------------------------ specials

	@ConfigItem(keyName = "specVulnerability", name = "Vulnerability", description = "Target is under Vulnerability (-10% Defence)",
		hidden = true)
	default boolean specVulnerability()
	{
		return false;
	}

	@ConfigItem(keyName = "specTomeOfWater", name = "...with tome of water", description = "Vulnerability cast with the tome of water (-15% Defence)",
		hidden = true)
	default boolean specTomeOfWater()
	{
		return false;
	}

	@ConfigItem(keyName = "specElderMaul", name = "Elder maul hits", description = "Successful elder maul specials (-35% Defence each)",
		hidden = true)
	@Range(max = 10)
	default int specElderMaul()
	{
		return 0;
	}

	@ConfigItem(keyName = "specDwh", name = "DWH hits", description = "Successful dragon warhammer specials (-30% Defence each)",
		hidden = true)
	@Range(max = 10)
	default int specDwh()
	{
		return 0;
	}

	@ConfigItem(keyName = "specEmberlight", name = "Emberlight hits", description = "Successful emberlight specials (-5% + 1 of Attack/Strength/Defence, 15% on demons)",
		hidden = true)
	@Range(max = 20)
	default int specEmberlight()
	{
		return 0;
	}

	@ConfigItem(keyName = "specArclight", name = "Arclight hits", description = "Successful arclight specials (-5% Attack/Strength/Defence, 10% on demons)",
		hidden = true)
	@Range(max = 20)
	default int specArclight()
	{
		return 0;
	}

	@ConfigItem(keyName = "specTonalztics", name = "Tonalztics hits", description = "Successful Tonalztics of Ralos special hits (Defence -1/8 of the target's Magic level each)",
		hidden = true)
	@Range(max = 20)
	default int specTonalztics()
	{
		return 0;
	}

	@ConfigItem(keyName = "specBgsDamage", name = "BGS damage", description = "Total damage dealt with Bandos godsword specials (drains Defence, then Strength, Attack, Magic)",
		hidden = true)
	@Range(max = 2000)
	default int specBgsDamage()
	{
		return 0;
	}

	@ConfigItem(keyName = "specSeercullDamage", name = "Seercull damage", description = "Total damage dealt with seercull specials (drains Magic level)",
		hidden = true)
	@Range(max = 2000)
	default int specSeercullDamage()
	{
		return 0;
	}

	@ConfigItem(keyName = "specAyakDamage", name = "Eye of Ayak damage", description = "Total damage dealt with Eye of Ayak specials (drains magic defence)",
		hidden = true)
	@Range(max = 2000)
	default int specAyakDamage()
	{
		return 0;
	}

	// ------------------------------------------------------------ boosts

	@ConfigItem(
		keyName = "usePrayers",
		name = "Offensive prayers",
		description = "Assume the best offensive prayer your Prayer and Defence levels allow",
		section = boostSection,
		position = 0
	)
	default boolean usePrayers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "thrall",
		name = "Thralls",
		description = "Add the strongest thrall your Magic level casts (lesser 38, superior 57, greater 76). Needs the "
			+ "Arceuus spellbook allowed and A Kingdom Divided; not used while autocasting a non-Arceuus spell",
		section = boostSection,
		position = 5
	)
	default boolean thrall()
	{
		return true;
	}

	@ConfigItem(
		keyName = "raidPotions",
		name = "Include raid potions",
		description = "Let Best available potions use overloads (CoX) and smelling salts (ToA) against raid targets. "
			+ "Turn off for the start of a raid, before you have them. A potion chosen by name is still used",
		section = boostSection,
		position = 1
	)
	default boolean raidPotions()
	{
		return true;
	}


	@ConfigItem(keyName = "taskMode", name = "Slayer task", description = "Auto matches your assignment to this target; On and Off override it", hidden = true)
	default AutoState taskMode() { return AutoState.AUTO; }

	@ConfigItem(keyName = "autoRaid", name = "Use active raid", description = "Read scaling from a matching live raid target; otherwise use the values below", hidden = true)
	default boolean autoRaid() { return true; }

	@ConfigItem(keyName = "meleePotionChoice", name = "Melee potion", description = "Best available uses ownership rules, plus overloads / smelling salts inside their raid when raid potions are included; named boosts and None override that choice", section = boostSection, position = 2)
	default PotionOptions.Melee meleePotion() { return PotionOptions.Melee.BEST; }

	@ConfigItem(keyName = "rangedPotionChoice", name = "Ranged potion", description = "Best available uses ownership rules, plus overloads / smelling salts inside their raid when raid potions are included; named boosts and None override that choice", section = boostSection, position = 3)
	default PotionOptions.Ranged rangedPotion() { return PotionOptions.Ranged.BEST; }

	@ConfigItem(keyName = "magicPotionChoice", name = "Magic potion", description = "Best available uses ownership rules, plus overloads / smelling salts inside their raid when raid potions are included; named boosts and None override that choice", section = boostSection, position = 4)
	default PotionOptions.Magic magicPotion() { return PotionOptions.Magic.BEST; }

	// ------------------------------------------------------------ managed from the side panel

	@ConfigItem(keyName = POTION_KEY_PREFIX + "MELEE", name = "", description = "", hidden = true)
	default String potionMelee()
	{
		return "best";
	}

	@ConfigItem(keyName = POTION_KEY_PREFIX + "RANGED", name = "", description = "", hidden = true)
	default String potionRanged()
	{
		return "best";
	}

	@ConfigItem(keyName = POTION_KEY_PREFIX + "MAGIC", name = "", description = "", hidden = true)
	default String potionMagic()
	{
		return "best";
	}

	@ConfigItem(keyName = LOCKS_KEY, name = "", description = "", hidden = true)
	default String locks()
	{
		return "";
	}

	@ConfigItem(keyName = EXCLUDED_KEY, name = "", description = "", hidden = true)
	default String excluded()
	{
		return "";
	}
}
