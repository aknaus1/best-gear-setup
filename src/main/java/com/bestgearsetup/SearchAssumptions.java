package com.bestgearsetup;

import com.bestgearsetup.calc.Antifire;
import com.bestgearsetup.calc.CalcMode;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable configuration values for one search, including transient detected overrides. Overrides are keyed by
 * the config method name; SearchAssumptionsTest checks that every config item is copied and can be overridden.
 */
final class SearchAssumptions implements BestGearSetupConfig
{
	private final boolean showMenuOption;
	private final boolean highlightBankGear;
	private final Color bankHighlightColor;
	private final boolean highlightInventoryGear;
	private final Color inventoryHighlightColor;
	private final SearchMode mode;
	private final String budget;
	private final boolean bossesOnly;
	private final boolean onSlayerTask;
	private final boolean ancientMagicks;
	private final boolean arceuusSpellbook;
	private final boolean membersItems;
	private final int ammoCount;
	private final int currentHitpoints;
	private final boolean wilderness;
	private final boolean limitWildernessRisk;
	private final int maxExpensiveItems;
	private final String expensiveItemThreshold;
	private final boolean charge;
	private final boolean markOfDarkness;
	private final boolean sunfireRunes;
	private final boolean kandarinDiary;
	private final boolean killSpecials;
	private final SpecEnergy specEnergy;
	private final DrainSpecs drainSpecs;
	private final WeaponPoison weaponPoison;
	private final boolean forinthrySurge;
	private final int soulreaperStacks;
	private final int miningLevel;
	private final int raidPartySize;
	private final int toaRaidLevel;
	private final int toaPathLevel;
	private final boolean coxChallengeMode;
	private final EncounterPhase encounterPhase;
	private final boolean aoe;
	private final int aoeTargets;
	private final boolean requireFireProtection;
	private final Antifire antifire;
	private final boolean protectMagic;
	private final boolean dmmItems;
	private final boolean betaItems;
	private final boolean bountyHunterItems;
	private final boolean requireAtlatlAmmoRecovery;
	private final boolean styleStab;
	private final boolean styleSlash;
	private final boolean styleCrush;
	private final boolean styleRanged;
	private final boolean styleMagic;
	private final boolean styleAtlatl;
	private final WeaponHands weaponHands;
	private final CalcMode calcMode;
	private final SearchDepth searchDepth;
	private final boolean attackXp;
	private final boolean strengthXp;
	private final boolean defenceXp;
	private final FillMode fillMode;
	private final DefenceFocus defenceFocus;
	private final double fillMargin;
	private final boolean specVulnerability;
	private final boolean specTomeOfWater;
	private final int specElderMaul;
	private final int specDwh;
	private final int specEmberlight;
	private final int specArclight;
	private final int specTonalztics;
	private final int specBgsDamage;
	private final int specSeercullDamage;
	private final int specAyakDamage;
	private final boolean usePrayers;
	private final boolean thrall;
	private final boolean raidPotions;
	private final AutoState taskMode;
	private final boolean autoRaid;
	private final PotionOptions.Melee meleePotion;
	private final PotionOptions.Ranged rangedPotion;
	private final PotionOptions.Magic magicPotion;
	private final String potionMelee;
	private final String potionRanged;
	private final String potionMagic;
	private final String locks;
	private final String excluded;

	private SearchAssumptions(BestGearSetupConfig source, Overrides o)
	{
		showMenuOption = o.get("showMenuOption", source.showMenuOption());
		highlightBankGear = o.get("highlightBankGear", source.highlightBankGear());
		bankHighlightColor = o.get("bankHighlightColor", source.bankHighlightColor());
		highlightInventoryGear = o.get("highlightInventoryGear", source.highlightInventoryGear());
		inventoryHighlightColor = o.get("inventoryHighlightColor", source.inventoryHighlightColor());
		mode = o.get("mode", source.mode());
		budget = o.get("budget", source.budget());
		bossesOnly = o.get("bossesOnly", source.bossesOnly());
		onSlayerTask = o.get("onSlayerTask", source.onSlayerTask());
		ancientMagicks = o.get("ancientMagicks", source.ancientMagicks());
		arceuusSpellbook = o.get("arceuusSpellbook", source.arceuusSpellbook());
		membersItems = o.get("membersItems", source.membersItems());
		ammoCount = o.get("ammoCount", source.ammoCount());
		currentHitpoints = o.get("currentHitpoints", source.currentHitpoints());
		wilderness = o.get("wilderness", source.wilderness());
		limitWildernessRisk = o.get("limitWildernessRisk", source.limitWildernessRisk());
		maxExpensiveItems = o.get("maxExpensiveItems", source.maxExpensiveItems());
		expensiveItemThreshold = o.get("expensiveItemThreshold", source.expensiveItemThreshold());
		charge = o.get("charge", source.charge());
		markOfDarkness = o.get("markOfDarkness", source.markOfDarkness());
		sunfireRunes = o.get("sunfireRunes", source.sunfireRunes());
		kandarinDiary = o.get("kandarinDiary", source.kandarinDiary());
		killSpecials = o.get("killSpecials", source.killSpecials());
		specEnergy = o.get("specEnergy", source.specEnergy());
		drainSpecs = o.get("drainSpecs", source.drainSpecs());
		weaponPoison = o.get("weaponPoison", source.weaponPoison());
		forinthrySurge = o.get("forinthrySurge", source.forinthrySurge());
		soulreaperStacks = o.get("soulreaperStacks", source.soulreaperStacks());
		miningLevel = o.get("miningLevel", source.miningLevel());
		raidPartySize = o.get("raidPartySize", source.raidPartySize());
		toaRaidLevel = o.get("toaRaidLevel", source.toaRaidLevel());
		toaPathLevel = o.get("toaPathLevel", source.toaPathLevel());
		coxChallengeMode = o.get("coxChallengeMode", source.coxChallengeMode());
		encounterPhase = o.get("encounterPhase", source.encounterPhase());
		aoe = o.get("aoe", source.aoe());
		aoeTargets = o.get("aoeTargets", source.aoeTargets());
		requireFireProtection = o.get("requireFireProtection", source.requireFireProtection());
		antifire = o.get("antifire", source.antifire());
		protectMagic = o.get("protectMagic", source.protectMagic());
		dmmItems = o.get("dmmItems", source.dmmItems());
		betaItems = o.get("betaItems", source.betaItems());
		bountyHunterItems = o.get("bountyHunterItems", source.bountyHunterItems());
		requireAtlatlAmmoRecovery = o.get("requireAtlatlAmmoRecovery", source.requireAtlatlAmmoRecovery());
		styleStab = o.get("styleStab", source.styleStab());
		styleSlash = o.get("styleSlash", source.styleSlash());
		styleCrush = o.get("styleCrush", source.styleCrush());
		styleRanged = o.get("styleRanged", source.styleRanged());
		styleMagic = o.get("styleMagic", source.styleMagic());
		styleAtlatl = o.get("styleAtlatl", source.styleAtlatl());
		weaponHands = o.get("weaponHands", source.weaponHands());
		calcMode = o.get("calcMode", source.calcMode());
		searchDepth = o.get("searchDepth", source.searchDepth());
		attackXp = o.get("attackXp", source.attackXp());
		strengthXp = o.get("strengthXp", source.strengthXp());
		defenceXp = o.get("defenceXp", source.defenceXp());
		fillMode = o.get("fillMode", source.fillMode());
		defenceFocus = o.get("defenceFocus", source.defenceFocus());
		fillMargin = o.get("fillMargin", source.fillMargin());
		specVulnerability = o.get("specVulnerability", source.specVulnerability());
		specTomeOfWater = o.get("specTomeOfWater", source.specTomeOfWater());
		specElderMaul = o.get("specElderMaul", source.specElderMaul());
		specDwh = o.get("specDwh", source.specDwh());
		specEmberlight = o.get("specEmberlight", source.specEmberlight());
		specArclight = o.get("specArclight", source.specArclight());
		specTonalztics = o.get("specTonalztics", source.specTonalztics());
		specBgsDamage = o.get("specBgsDamage", source.specBgsDamage());
		specSeercullDamage = o.get("specSeercullDamage", source.specSeercullDamage());
		specAyakDamage = o.get("specAyakDamage", source.specAyakDamage());
		usePrayers = o.get("usePrayers", source.usePrayers());
		thrall = o.get("thrall", source.thrall());
		raidPotions = o.get("raidPotions", source.raidPotions());
		taskMode = o.get("taskMode", source.taskMode());
		autoRaid = o.get("autoRaid", source.autoRaid());
		meleePotion = o.get("meleePotion", source.meleePotion());
		rangedPotion = o.get("rangedPotion", source.rangedPotion());
		magicPotion = o.get("magicPotion", source.magicPotion());
		potionMelee = o.get("potionMelee", source.potionMelee());
		potionRanged = o.get("potionRanged", source.potionRanged());
		potionMagic = o.get("potionMagic", source.potionMagic());
		locks = o.get("locks", source.locks());
		excluded = o.get("excluded", source.excluded());
	}

	static BestGearSetupConfig capture(BestGearSetupConfig source, Map<String, Object> overrides)
	{
		Overrides o = new Overrides(overrides);
		SearchAssumptions snapshot = new SearchAssumptions(source, o);
		o.requireAllUsed();
		return snapshot;
	}

	@Override public boolean showMenuOption() { return showMenuOption; }
	@Override public boolean highlightBankGear() { return highlightBankGear; }
	@Override public Color bankHighlightColor() { return bankHighlightColor; }
	@Override public boolean highlightInventoryGear() { return highlightInventoryGear; }
	@Override public Color inventoryHighlightColor() { return inventoryHighlightColor; }
	@Override public SearchMode mode() { return mode; }
	@Override public String budget() { return budget; }
	@Override public boolean bossesOnly() { return bossesOnly; }
	@Override public boolean onSlayerTask() { return onSlayerTask; }
	@Override public boolean ancientMagicks() { return ancientMagicks; }
	@Override public boolean arceuusSpellbook() { return arceuusSpellbook; }
	@Override public boolean membersItems() { return membersItems; }
	@Override public int ammoCount() { return ammoCount; }
	@Override public int currentHitpoints() { return currentHitpoints; }
	@Override public boolean wilderness() { return wilderness; }
	@Override public boolean limitWildernessRisk() { return limitWildernessRisk; }
	@Override public int maxExpensiveItems() { return maxExpensiveItems; }
	@Override public String expensiveItemThreshold() { return expensiveItemThreshold; }
	@Override public boolean charge() { return charge; }
	@Override public boolean markOfDarkness() { return markOfDarkness; }
	@Override public boolean sunfireRunes() { return sunfireRunes; }
	@Override public boolean kandarinDiary() { return kandarinDiary; }
	@Override public boolean killSpecials() { return killSpecials; }
	@Override public SpecEnergy specEnergy() { return specEnergy; }
	@Override public DrainSpecs drainSpecs() { return drainSpecs; }
	@Override public WeaponPoison weaponPoison() { return weaponPoison; }
	@Override public boolean forinthrySurge() { return forinthrySurge; }
	@Override public int soulreaperStacks() { return soulreaperStacks; }
	@Override public int miningLevel() { return miningLevel; }
	@Override public int raidPartySize() { return raidPartySize; }
	@Override public int toaRaidLevel() { return toaRaidLevel; }
	@Override public int toaPathLevel() { return toaPathLevel; }
	@Override public boolean coxChallengeMode() { return coxChallengeMode; }
	@Override public EncounterPhase encounterPhase() { return encounterPhase; }
	@Override public boolean aoe() { return aoe; }
	@Override public int aoeTargets() { return aoeTargets; }
	@Override public boolean requireFireProtection() { return requireFireProtection; }
	@Override public Antifire antifire() { return antifire; }
	@Override public boolean protectMagic() { return protectMagic; }
	@Override public boolean dmmItems() { return dmmItems; }
	@Override public boolean betaItems() { return betaItems; }
	@Override public boolean bountyHunterItems() { return bountyHunterItems; }
	@Override public boolean requireAtlatlAmmoRecovery() { return requireAtlatlAmmoRecovery; }
	@Override public boolean styleStab() { return styleStab; }
	@Override public boolean styleSlash() { return styleSlash; }
	@Override public boolean styleCrush() { return styleCrush; }
	@Override public boolean styleRanged() { return styleRanged; }
	@Override public boolean styleMagic() { return styleMagic; }
	@Override public boolean styleAtlatl() { return styleAtlatl; }
	@Override public WeaponHands weaponHands() { return weaponHands; }
	@Override public CalcMode calcMode() { return calcMode; }
	@Override public SearchDepth searchDepth() { return searchDepth; }
	@Override public boolean attackXp() { return attackXp; }
	@Override public boolean strengthXp() { return strengthXp; }
	@Override public boolean defenceXp() { return defenceXp; }
	@Override public FillMode fillMode() { return fillMode; }
	@Override public DefenceFocus defenceFocus() { return defenceFocus; }
	@Override public double fillMargin() { return fillMargin; }
	@Override public boolean specVulnerability() { return specVulnerability; }
	@Override public boolean specTomeOfWater() { return specTomeOfWater; }
	@Override public int specElderMaul() { return specElderMaul; }
	@Override public int specDwh() { return specDwh; }
	@Override public int specEmberlight() { return specEmberlight; }
	@Override public int specArclight() { return specArclight; }
	@Override public int specTonalztics() { return specTonalztics; }
	@Override public int specBgsDamage() { return specBgsDamage; }
	@Override public int specSeercullDamage() { return specSeercullDamage; }
	@Override public int specAyakDamage() { return specAyakDamage; }
	@Override public boolean usePrayers() { return usePrayers; }
	@Override public boolean thrall() { return thrall; }
	@Override public boolean raidPotions() { return raidPotions; }
	@Override public AutoState taskMode() { return taskMode; }
	@Override public boolean autoRaid() { return autoRaid; }
	@Override public PotionOptions.Melee meleePotion() { return meleePotion; }
	@Override public PotionOptions.Ranged rangedPotion() { return rangedPotion; }
	@Override public PotionOptions.Magic magicPotion() { return magicPotion; }
	@Override public String potionMelee() { return potionMelee; }
	@Override public String potionRanged() { return potionRanged; }
	@Override public String potionMagic() { return potionMagic; }
	@Override public String locks() { return locks; }
	@Override public String excluded() { return excluded; }

	@Override
	public String toString()
	{
		return "Search assumptions";
	}

	/** Detected values that replace the saved settings for this search. */
	private static final class Overrides
	{
		private final Map<String, Object> values;
		private final Set<String> unused;

		Overrides(Map<String, Object> values)
		{
			this.values = values;
			unused = new HashSet<>(values.keySet());
		}

		@SuppressWarnings("unchecked")
		<T> T get(String name, T saved)
		{
			unused.remove(name);
			return values.containsKey(name) ? (T) values.get(name) : saved;
		}

		void requireAllUsed()
		{
			if (!unused.isEmpty())
			{
				throw new IllegalArgumentException("Unknown search overrides: " + unused);
			}
		}
	}
}
