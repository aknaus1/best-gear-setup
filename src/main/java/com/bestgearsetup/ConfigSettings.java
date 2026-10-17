package com.bestgearsetup;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Direct, compile-time checked getter bindings shared by snapshots, cache keys and both settings editors. */
public final class ConfigSettings
{
	private static final Map<String, Integer> INDICES = new LinkedHashMap<>();
	private static final List<Function<BestGearSetupConfig, Object>> GETTERS = new ArrayList<>();
	/** Each setting's value type, taken from its declared default, so overrides are checked at capture. */
	private static final List<Class<?>> TYPES = new ArrayList<>();
	/** Stored config keys that differ from their getter/override names. */
	private static final Map<String, String> NAMES = Map.of(
		"meleePotionChoice", "meleePotion", "rangedPotionChoice", "rangedPotion", "magicPotionChoice", "magicPotion",
		BestGearSetupConfig.POTION_KEY_PREFIX + "MELEE", "potionMelee",
		BestGearSetupConfig.POTION_KEY_PREFIX + "RANGED", "potionRanged",
		BestGearSetupConfig.POTION_KEY_PREFIX + "MAGIC", "potionMagic");

	static
	{
		add("activeEncounter", BestGearSetupConfig::activeEncounter);
		add("raidEquipmentLimit", BestGearSetupConfig::raidEquipmentLimit);
		add("raidSpellbook", BestGearSetupConfig::raidSpellbook);
		add("toaPathOrder", BestGearSetupConfig::toaPathOrder);
		add("tobRole", BestGearSetupConfig::tobRole);
		add("quickSwitchLimit", BestGearSetupConfig::quickSwitchLimit);
		add("showMenuOption", BestGearSetupConfig::showMenuOption);
		add("highlightBankGear", BestGearSetupConfig::highlightBankGear);
		add("bankHighlightColor", BestGearSetupConfig::bankHighlightColor);
		add("highlightInventoryGear", BestGearSetupConfig::highlightInventoryGear);
		add("inventoryHighlightColor", BestGearSetupConfig::inventoryHighlightColor);
		add("fillFood", BestGearSetupConfig::fillFood);
		add("travelTeleport", BestGearSetupConfig::travelTeleport);
		add("shortestPathRoute", BestGearSetupConfig::shortestPathRoute);
		add("mode", BestGearSetupConfig::mode);
		add("budget", BestGearSetupConfig::budget);
		add("bossesOnly", BestGearSetupConfig::bossesOnly);
		add("onSlayerTask", BestGearSetupConfig::onSlayerTask);
		add("ancientMagicks", BestGearSetupConfig::ancientMagicks);
		add("arceuusSpellbook", BestGearSetupConfig::arceuusSpellbook);
		add("membersItems", BestGearSetupConfig::membersItems);
		add("ammoCount", BestGearSetupConfig::ammoCount);
		add("currentHitpoints", BestGearSetupConfig::currentHitpoints);
		add("wilderness", BestGearSetupConfig::wilderness);
		add("limitWildernessRisk", BestGearSetupConfig::limitWildernessRisk);
		add("maxExpensiveItems", BestGearSetupConfig::maxExpensiveItems);
		add("expensiveItemThreshold", BestGearSetupConfig::expensiveItemThreshold);
		add("charge", BestGearSetupConfig::charge);
		add("markOfDarkness", BestGearSetupConfig::markOfDarkness);
		add("sunfireRunes", BestGearSetupConfig::sunfireRunes);
		add("kandarinDiary", BestGearSetupConfig::kandarinDiary);
		add("thrall", BestGearSetupConfig::thrall);
		add("killSpecials", BestGearSetupConfig::killSpecials);
		add("specEnergy", BestGearSetupConfig::specEnergy);
		add("drainSpecs", BestGearSetupConfig::drainSpecs);
		add("weaponPoison", BestGearSetupConfig::weaponPoison);
		add("forinthrySurge", BestGearSetupConfig::forinthrySurge);
		add("soulreaperStacks", BestGearSetupConfig::soulreaperStacks);
		add("miningLevel", BestGearSetupConfig::miningLevel);
		add("raidPartySize", BestGearSetupConfig::raidPartySize);
		add("toaRaidLevel", BestGearSetupConfig::toaRaidLevel);
		add("toaPathLevel", BestGearSetupConfig::toaPathLevel);
		add("toaStartingPathLevel", BestGearSetupConfig::toaStartingPathLevel);
		add("toaWalkThePath", BestGearSetupConfig::toaWalkThePath);
		add("toaMoreOverlords", BestGearSetupConfig::toaMoreOverlords);
		add("raidKillShare", BestGearSetupConfig::raidKillShare);
		add("raidFinalBlowPercent", BestGearSetupConfig::raidFinalBlowPercent);
		add("coxChallengeMode", BestGearSetupConfig::coxChallengeMode);
		add("encounterPhase", BestGearSetupConfig::encounterPhase);
		add("aoe", BestGearSetupConfig::aoe);
		add("aoeTargets", BestGearSetupConfig::aoeTargets);
		add("requireFireProtection", BestGearSetupConfig::requireFireProtection);
		add("antifire", BestGearSetupConfig::antifire);
		add("protectMagic", BestGearSetupConfig::protectMagic);
		add("dmmItems", BestGearSetupConfig::dmmItems);
		add("betaItems", BestGearSetupConfig::betaItems);
		add("bountyHunterItems", BestGearSetupConfig::bountyHunterItems);
		add("requireAtlatlAmmoRecovery", BestGearSetupConfig::requireAtlatlAmmoRecovery);
		add("styleStab", BestGearSetupConfig::styleStab);
		add("styleSlash", BestGearSetupConfig::styleSlash);
		add("styleCrush", BestGearSetupConfig::styleCrush);
		add("styleRanged", BestGearSetupConfig::styleRanged);
		add("styleMagic", BestGearSetupConfig::styleMagic);
		add("styleAtlatl", BestGearSetupConfig::styleAtlatl);
		add("weaponHands", BestGearSetupConfig::weaponHands);
		add("calcMode", BestGearSetupConfig::calcMode);
		add("searchDepth", BestGearSetupConfig::searchDepth);
		add("attackXp", BestGearSetupConfig::attackXp);
		add("strengthXp", BestGearSetupConfig::strengthXp);
		add("defenceXp", BestGearSetupConfig::defenceXp);
		add("fillMode", BestGearSetupConfig::fillMode);
		add("defenceFocus", BestGearSetupConfig::defenceFocus);
		add("fillMargin", BestGearSetupConfig::fillMargin);
		add("autoSpecs", BestGearSetupConfig::autoSpecs);
		add("specVulnerability", BestGearSetupConfig::specVulnerability);
		add("specTomeOfWater", BestGearSetupConfig::specTomeOfWater);
		add("specElderMaul", BestGearSetupConfig::specElderMaul);
		add("specDwh", BestGearSetupConfig::specDwh);
		add("specEmberlight", BestGearSetupConfig::specEmberlight);
		add("specArclight", BestGearSetupConfig::specArclight);
		add("specTonalztics", BestGearSetupConfig::specTonalztics);
		add("specBgsDamage", BestGearSetupConfig::specBgsDamage);
		add("specSeercullDamage", BestGearSetupConfig::specSeercullDamage);
		add("specAyakDamage", BestGearSetupConfig::specAyakDamage);
		add("usePrayers", BestGearSetupConfig::usePrayers);
		add("raidPotions", BestGearSetupConfig::raidPotions);
		add("divinePotions", BestGearSetupConfig::divinePotions);
		add("repotBelow", BestGearSetupConfig::repotBelow);
		add("killsPerTrip", BestGearSetupConfig::killsPerTrip);
		add("taskMode", BestGearSetupConfig::taskMode);
		add("autoRaid", BestGearSetupConfig::autoRaid);
		add("meleePotion", BestGearSetupConfig::meleePotion);
		add("rangedPotion", BestGearSetupConfig::rangedPotion);
		add("magicPotion", BestGearSetupConfig::magicPotion);
		add("potionMelee", BestGearSetupConfig::potionMelee);
		add("potionRanged", BestGearSetupConfig::potionRanged);
		add("potionMagic", BestGearSetupConfig::potionMagic);
		add("locks", BestGearSetupConfig::locks);
		add("excluded", BestGearSetupConfig::excluded);
		add("raidStyles", BestGearSetupConfig::raidStyles);
		add("seenFightOptions", BestGearSetupConfig::seenFightOptions);
		BestGearSetupConfig defaults = new BestGearSetupConfig() { };
		for (Function<BestGearSetupConfig, Object> getter : GETTERS)
		{
			Object value = getter.apply(defaults);
			TYPES.add(value instanceof Enum ? ((Enum<?>) value).getDeclaringClass() : value.getClass());
		}
	}

	private ConfigSettings() { }

	private static void add(String name, Function<BestGearSetupConfig, Object> getter)
	{
		INDICES.put(name, GETTERS.size());
		GETTERS.add(getter);
	}

	static int index(String name)
	{
		Integer index = INDICES.get(name);
		if (index == null) { throw new IllegalArgumentException("Unknown setting " + name); }
		return index;
	}

	/** Replace one captured value, rejecting a value its getter could not return. */
	static void set(Object[] values, String name, Object value)
	{
		int index = index(name);
		Class<?> type = TYPES.get(index);
		// Boolean, int and double settings are primitive getters: null would fail when read.
		if (value == null ? type == Boolean.class || type == Integer.class || type == Double.class : !type.isInstance(value))
		{
			throw new IllegalArgumentException("Setting " + name + " cannot be " + value);
		}
		values[index] = value;
	}

	/** Getter for a persisted config key. Missing bindings fail before constructing a partial editor. */
	public static Function<BestGearSetupConfig, Object> getter(String key)
	{
		return GETTERS.get(index(NAMES.getOrDefault(key, key)));
	}

	/** Compact snapshots avoid allocating a map entry for every setting of every restricted raid search. */
	static Object[] capture(BestGearSetupConfig config)
	{
		if (config instanceof SearchAssumptions) { return ((SearchAssumptions) config).copyValues(); }
		Object[] values = new Object[GETTERS.size()];
		for (int i = 0; i < values.length; i++) { values[i] = GETTERS.get(i).apply(config); }
		return values;
	}

	/** Snapshot by getter/override name; each getter is evaluated exactly once. */
	static Map<String, Object> read(BestGearSetupConfig config)
	{
		Object[] captured = capture(config);
		Map<String, Object> values = new LinkedHashMap<>();
		INDICES.forEach((name, index) -> values.put(name, captured[index]));
		return values;
	}
}
