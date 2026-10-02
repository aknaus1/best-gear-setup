package com.bestgearsetup;

import com.bestgearsetup.calc.CalcMode;
import com.bestgearsetup.calc.Antifire;
import com.bestgearsetup.calc.DefenceFocus;
import com.bestgearsetup.calc.FillMode;
import com.bestgearsetup.calc.SearchDepth;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.calc.WeaponHands;
import com.bestgearsetup.calc.WeaponPoison;
import com.bestgearsetup.calc.XpPreference;
import com.bestgearsetup.data.EncounterPhase;
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
	String DISTANCE_KEY = "targetDistance";
	String POTION_KEY_PREFIX = "potion";
	String LOCKS_KEY = "locks";
	String EXCLUDED_KEY = "excluded";
	String MEMBERS_ITEMS_KEY = "membersItems";
	String DMM_ITEMS_KEY = "dmmItems";
	String BETA_ITEMS_KEY = "betaItems";
	String BOUNTY_HUNTER_ITEMS_KEY = "bountyHunterItems";

	@ConfigSection(
		name = "Equipment & access",
		description = "Equipment categories, untradeables and unlocked spellbooks",
		position = 0
	)
	String searchSection = "search";

	@ConfigSection(
		name = "Search options",
		description = "Styles, weapons and what to optimise for",
		position = 1
	)
	String optionsSection = "options";

	@ConfigSection(
		name = "Experience",
		description = "Restrict attack styles by the experience they give (e.g. for pures)",
		position = 2,
		closedByDefault = true
	)
	String experienceSection = "experience";

	@ConfigSection(
		name = "Fill empty slots",
		description = "What to wear in slots that add no damage",
		position = 3,
		closedByDefault = true
	)
	String fillSection = "fill";

	// Fight-specific settings are managed by the side panel.
	String specialsSection = "specials";

	@ConfigSection(
		name = "Boosts",
		description = "Prayers and thralls assumed in the calculation (potions are chosen in the side panel)",
		position = 5
	)
	String boostSection = "boosts";

	@ConfigItem(
		keyName = "showMenuOption",
		name = "Right-click option",
		description = "Add a 'Best setup' option when right-clicking attackable NPCs",
		position = 0
	)
	default boolean showMenuOption()
	{
		return true;
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
		keyName = "allowUntradeables",
		name = "Unowned untradeables",
		description = "Allow untradeable items you do not own yet; tradable base components still count toward the budget",
		section = searchSection,
		position = 3
	)
	default boolean allowUntradeables()
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
		section = searchSection,
		position = 5
	)
	default boolean ancientMagicks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "arceuusSpellbook",
		name = "Arceuus spellbook",
		description = "Allow autocasting Arceuus spells",
		section = searchSection,
		position = 6
	)
	default boolean arceuusSpellbook()
	{
		return true;
	}

	@ConfigItem(
		keyName = MEMBERS_ITEMS_KEY,
		name = "Members items",
		description = "Turn off for free-to-play worlds",
		section = searchSection,
		position = 7
	)
	default boolean membersItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ammoCount",
		name = "Ammo to price",
		hidden = true,
		description = "Arrows / bolts / darts to include in the budget (e.g. 1000). 0 ignores ammo cost",
		section = searchSection,
		position = 8
	)
	@Range(max = 100_000)
	default int ammoCount()
	{
		return 0;
	}

	@ConfigItem(keyName = DISTANCE_KEY, name = "Distance (0 = Auto)", hidden = true,
		description = "Fighting distance in tiles from the monster's nearest tile. Auto uses encounter restrictions. "
			+ "Unreachable weapons and styles are excluded; boss restrictions still apply at shorter distances.",
		section = searchSection, position = 12)
	@Range(max = 128)
	default int targetDistance()
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

	@ConfigItem(keyName = "monsterHitpoints", name = "Target HP (0 = full)", hidden = true,
		description = "Target HP when the fight (or a gear switch) starts. HP-dependent effects are averaged from here to the kill.", section = searchSection, position = 19)
	@Range(max = 100_000)
	default int monsterHitpoints()
	{
		return 0;
	}

	@ConfigItem(keyName = "wilderness", name = "In Wilderness", hidden = true,
		description = "Enable charged revenant weapon bonuses in the Wilderness.", section = searchSection, position = 20)
	default boolean wilderness()
	{
		return false;
	}

	@ConfigItem(keyName = "charge", name = "Charge spell active", hidden = true,
		description = "Boost god spells when wearing the matching god cape.", section = searchSection, position = 21)
	default boolean charge()
	{
		return false;
	}

	@ConfigItem(keyName = "markOfDarkness", name = "Mark of Darkness", hidden = true,
		description = "Enable demonbane spell accuracy and per-hit damage bonuses.", section = searchSection, position = 22)
	default boolean markOfDarkness()
	{
		return false;
	}

	@ConfigItem(keyName = "sunfireRunes", name = "Use sunfire runes", hidden = true,
		description = "Raise fire spell minimum rolls to 10% of the maximum before tome effects.", section = searchSection, position = 23)
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

	@ConfigItem(keyName = "specialAttacks", name = "Only special attacks", hidden = true,
		description = "Score each weapon's special attack instead of its ordinary attack. "
			+ "Weapons without a damaging special are excluded.", section = searchSection, position = 33)
	default boolean specialAttacks()
	{
		return false;
	}

	@ConfigItem(keyName = "weaponPoison", name = "Weapon poison", hidden = true,
		description = "Poison assumed on poisonable daggers, spears, hastae, darts, knives, javelins, arrows and "
			+ "bolts (the bundled data lists only unpoisoned items).", section = searchSection, position = 34)
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

	@ConfigItem(keyName = "encounterPhase", name = "Boss phase", hidden = true,
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

	@ConfigItem(keyName = "requireFireProtection", name = "Require fire protection", hidden = true,
		description = "Exclude gear without suitable dragonfire or wyvern protection. Boss chip damage may remain.",
		section = searchSection, position = 15)
	default boolean requireFireProtection()
	{
		return true;
	}

	@ConfigItem(keyName = "antifire", name = "Antifire potion", hidden = true,
		description = "Active antifire potion (extended versions give the same protection)", section = searchSection, position = 16)
	default Antifire antifire()
	{
		return Antifire.SUPER;
	}

	@ConfigItem(keyName = "protectMagic", name = "Protect from Magic", hidden = true,
		description = "Assume this overhead is active; it does not protect against metal dragonfire or wyvern icy breath fully.",
		section = searchSection, position = 17)
	default boolean protectMagic()
	{
		return true;
	}

	@ConfigItem(keyName = DMM_ITEMS_KEY, name = "DMM equipment", description = "Include Deadman-only equipment",
		section = searchSection, position = 9)
	default boolean dmmItems()
	{
		return false;
	}

	@ConfigItem(keyName = BETA_ITEMS_KEY, name = "Beta equipment", description = "Include beta equipment when present in the bundled data",
		section = searchSection, position = 10)
	default boolean betaItems()
	{
		return false;
	}

	@ConfigItem(keyName = BOUNTY_HUNTER_ITEMS_KEY, name = "BH equipment",
		description = "Include Bounty Hunter-only equipment, including Vesta's blighted longsword",
		section = searchSection, position = 11)
	default boolean bountyHunterItems()
	{
		return false;
	}

	// ------------------------------------------------------------ options

	@ConfigItem(keyName = "styleStab", name = "Stab", description = "Allow stab attacks", section = optionsSection, position = 10)
	default boolean styleStab()
	{
		return true;
	}

	@ConfigItem(keyName = "styleSlash", name = "Slash", description = "Allow slash attacks", section = optionsSection, position = 11)
	default boolean styleSlash()
	{
		return true;
	}

	@ConfigItem(keyName = "styleCrush", name = "Crush", description = "Allow crush attacks", section = optionsSection, position = 12)
	default boolean styleCrush()
	{
		return true;
	}

	@ConfigItem(keyName = "styleRanged", name = "Ranged", description = "Allow ranged attacks", section = optionsSection, position = 13)
	default boolean styleRanged()
	{
		return true;
	}

	@ConfigItem(keyName = "styleMagic", name = "Magic", description = "Allow magic attacks", section = optionsSection, position = 14)
	default boolean styleMagic()
	{
		return true;
	}

	@ConfigItem(keyName = "styleAtlatl", name = "Atlatl",
		description = "Allow the eclipse atlatl (ranged accuracy, melee strength damage) as its own style",
		section = optionsSection, position = 14)
	default boolean styleAtlatl()
	{
		return true;
	}

	@ConfigItem(
		keyName = "weaponHands",
		name = "Weapons",
		description = "Restrict to one-handed or two-handed weapons",
		section = optionsSection,
		position = 15
	)
	default WeaponHands weaponHands()
	{
		return WeaponHands.ANY;
	}

	@ConfigItem(
		keyName = "calcMode",
		name = "Optimise for",
		description = "What to maximise. DPS is used to break ties",
		section = optionsSection,
		position = 16
	)
	default CalcMode calcMode()
	{
		return CalcMode.DPS;
	}

	@ConfigItem(
		keyName = "searchDepth",
		name = "Search depth",
		description = "Fast: quickest, may miss the absolute best. Best: slowest, recommended for low budgets",
		section = optionsSection,
		position = 17
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

	@ConfigItem(
		keyName = "xpPreference",
		name = "Melee XP preference",
		description = "Require a melee style that gives this experience (controlled gives all three)",
		section = experienceSection,
		position = 23
	)
	default XpPreference xpPreference()
	{
		return XpPreference.ANY;
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
		name = "Defence type",
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
		name = "DPS margin",
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
		section = specialsSection, position = 40, hidden = true)
	default boolean specVulnerability()
	{
		return false;
	}

	@ConfigItem(keyName = "specTomeOfWater", name = "...with tome of water", description = "Vulnerability cast with the tome of water (-15% Defence)",
		section = specialsSection, position = 41, hidden = true)
	default boolean specTomeOfWater()
	{
		return false;
	}

	@ConfigItem(keyName = "specElderMaul", name = "Elder maul hits", description = "Successful elder maul specials (-35% Defence each)",
		section = specialsSection, position = 42, hidden = true)
	@Range(max = 10)
	default int specElderMaul()
	{
		return 0;
	}

	@ConfigItem(keyName = "specDwh", name = "Dragon warhammer hits", description = "Successful dragon warhammer specials (-30% Defence each)",
		section = specialsSection, position = 43, hidden = true)
	@Range(max = 10)
	default int specDwh()
	{
		return 0;
	}

	@ConfigItem(keyName = "specEmberlight", name = "Emberlight hits", description = "Successful emberlight specials (-5% + 1 of Attack/Strength/Defence, 15% on demons)",
		section = specialsSection, position = 44, hidden = true)
	@Range(max = 20)
	default int specEmberlight()
	{
		return 0;
	}

	@ConfigItem(keyName = "specArclight", name = "Arclight hits", description = "Successful arclight specials (-5% Attack/Strength/Defence, 10% on demons)",
		section = specialsSection, position = 45, hidden = true)
	@Range(max = 20)
	default int specArclight()
	{
		return 0;
	}

	@ConfigItem(keyName = "specTonalztics", name = "Tonalztics hits", description = "Successful Tonalztics of Ralos special hits (Defence -1/8 of the target's Magic level each)",
		section = specialsSection, position = 46, hidden = true)
	@Range(max = 20)
	default int specTonalztics()
	{
		return 0;
	}

	@ConfigItem(keyName = "specBgsDamage", name = "Bandos godsword damage", description = "Total damage dealt with BGS specials (drains Defence, then Strength, Attack, Magic)",
		section = specialsSection, position = 47, hidden = true)
	@Range(max = 2000)
	default int specBgsDamage()
	{
		return 0;
	}

	@ConfigItem(keyName = "specSeercullDamage", name = "Seercull damage", description = "Total damage dealt with seercull specials (drains Magic level)",
		section = specialsSection, position = 48, hidden = true)
	@Range(max = 2000)
	default int specSeercullDamage()
	{
		return 0;
	}

	@ConfigItem(keyName = "specAyakDamage", name = "Eye of Ayak damage", description = "Total damage dealt with Eye of Ayak specials (drains magic defence)",
		section = specialsSection, position = 49, hidden = true)
	@Range(max = 2000)
	default int specAyakDamage()
	{
		return 0;
	}

	// ------------------------------------------------------------ boosts

	@ConfigItem(
		keyName = "usePrayers",
		name = "Prayers",
		description = "Assume the best offensive prayer your Prayer and Defence levels allow",
		section = boostSection,
		position = 50
	)
	default boolean usePrayers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "unlockedPrayers",
		name = "Unlocked prayers",
		description = "Assume prayers that need a scroll or unlock (Rigour, Augury, Deadeye, Mystic Vigour...) are unlocked",
		section = boostSection,
		position = 51
	)
	default boolean unlockedPrayers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "thrall",
		name = "Thrall",
		description = "Add greater thrall DPS (0.625), except when autocasting a non-Arceuus spell",
		section = boostSection,
		position = 52
	)
	default boolean thrall()
	{
		return false;
	}

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
