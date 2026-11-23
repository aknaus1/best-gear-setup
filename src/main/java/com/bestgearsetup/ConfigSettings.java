package com.bestgearsetup;

import java.util.*;
import java.util.function.Function;

/**
 * Every setting's getter, in one table shared by search snapshots ({@link Assumptions}) and both settings
 * editors. Bindings are method references, so a renamed or removed getter fails to compile; AssumptionsTest
 * fails until a new config item is added here.
 */
public final class ConfigSettings
{
	private static final Map<String, Integer> INDICES = new LinkedHashMap<>();
	private static final List<Function<Options, Object>> GETTERS = new ArrayList<>();
	/** Each setting's value type, taken from its declared default, so overrides are checked when captured. */
	private static final List<Class<?>> TYPES = new ArrayList<>();

	static
	{
		add("showMenuOption", Options::showMenuOption);
		add("highlightBankGear", Options::highlightBankGear);
		add("bankHighlightColor", Options::bankHighlightColor);
		add("highlightInventoryGear", Options::highlightInventoryGear);
		add("inventoryHighlightColor", Options::inventoryHighlightColor);
		add("fillFood", Options::fillFood);
		add("mode", Options::mode);
		add("budget", Options::budget);
		add("bossesOnly", Options::bossesOnly);
		add("onSlayerTask", Options::onSlayerTask);
		add("ancientMagicks", Options::ancientMagicks);
		add("arceuusSpellbook", Options::arceuusSpellbook);
		add("preventManualCast", Options::preventManualCast);
		add("membersItems", Options::membersItems);
		add("ammoCount", Options::ammoCount);
		add("killsPerTrip", Options::killsPerTrip);
		add("fightSwitchSlots", Options::fightSwitchSlots);
		add("currentHitpoints", Options::currentHitpoints);
		add("wilderness", Options::wilderness);
		add("limitWildernessRisk", Options::limitWildernessRisk);
		add("maxExpensiveItems", Options::maxExpensiveItems);
		add("expensiveItemThreshold", Options::expensiveItemThreshold);
		add("charge", Options::charge);
		add("markOfDarkness", Options::markOfDarkness);
		add("sunfireRunes", Options::sunfireRunes);
		add("kandarinDiary", Options::kandarinDiary);
		add("killSpecials", Options::killSpecials);
		add("specEnergy", Options::specEnergy);
		add("drainSpecs", Options::drainSpecs);
		add("weaponPoison", Options::weaponPoison);
		add("forinthrySurge", Options::forinthrySurge);
		add("soulreaperStacks", Options::soulreaperStacks);
		add("miningLevel", Options::miningLevel);
		add("raidPartySize", Options::raidPartySize);
		add("toaRaidLevel", Options::toaRaidLevel);
		add("toaPathLevel", Options::toaPathLevel);
		add("coxChallengeMode", Options::coxChallengeMode);
		add("encounterPhase", Options::encounterPhase);
		add("aoe", Options::aoe);
		add("aoeTargets", Options::aoeTargets);
		add("requireFireProtection", Options::requireFireProtection);
		add("antifire", Options::antifire);
		add("protectMagic", Options::protectMagic);
		add("dmmItems", Options::dmmItems);
		add("betaItems", Options::betaItems);
		add("bountyHunterItems", Options::bountyHunterItems);
		add("requireAtlatlAmmoRecovery", Options::requireAtlatlAmmoRecovery);
		add("styleStab", Options::styleStab);
		add("styleSlash", Options::styleSlash);
		add("styleCrush", Options::styleCrush);
		add("styleRanged", Options::styleRanged);
		add("styleMagic", Options::styleMagic);
		add("styleAtlatl", Options::styleAtlatl);
		add("weaponHands", Options::weaponHands);
		add("calcMode", Options::calcMode);
		add("attackXp", Options::attackXp);
		add("strengthXp", Options::strengthXp);
		add("defenceXp", Options::defenceXp);
		add("fillMode", Options::fillMode);
		add("defenceFocus", Options::defenceFocus);
		add("fillMargin", Options::fillMargin);
		add("specVulnerability", Options::specVulnerability);
		add("specTomeOfWater", Options::specTomeOfWater);
		add("specElderMaul", Options::specElderMaul);
		add("specDwh", Options::specDwh);
		add("specEmberlight", Options::specEmberlight);
		add("specArclight", Options::specArclight);
		add("specTonalztics", Options::specTonalztics);
		add("specBgsDamage", Options::specBgsDamage);
		add("specSeercullDamage", Options::specSeercullDamage);
		add("specAyakDamage", Options::specAyakDamage);
		add("usePrayers", Options::usePrayers);
		add("thrall", Options::thrall);
		add("raidPotions", Options::raidPotions);
		add("taskMode", Options::taskMode);
		add("autoRaid", Options::autoRaid);
		add("autoSpecs", Options::autoSpecs);
		add("meleePotionChoice", Options::meleePotion);
		add("rangedPotionChoice", Options::rangedPotion);
		add("magicPotionChoice", Options::magicPotion);
		add(Options.POTION_KEY_PREFIX + "MELEE", Options::potionMelee);
		add(Options.POTION_KEY_PREFIX + "RANGED", Options::potionRanged);
		add(Options.POTION_KEY_PREFIX + "MAGIC", Options::potionMagic);
		add("locks", Options::locks);
		add("excluded", Options::excluded);
		add("blockedSpells", Options::blockedSpells);
		add("forcedSpells", Options::forcedSpells);
		add("excludedPrayers", Options::excludedPrayers);
		Options defaults = new Options() { };
		for (Function<Options, Object> getter : GETTERS)
		{
			Object value = getter.apply(defaults);
			TYPES.add(value instanceof Enum ? ((Enum<?>) value).getDeclaringClass() : value.getClass());
		}
	}

	private ConfigSettings()
	{
	}

	private static void add(String name, Function<Options, Object> getter)
	{
		INDICES.put(name, GETTERS.size());
		GETTERS.add(getter);
	}

	/** Position of a setting, by config key, in captured values. */
	static int index(String name)
	{
		Integer index = INDICES.get(name);
		if (index == null)
		{
			throw new IllegalArgumentException("Unknown setting " + name);
		}
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

	/** Getter for a stored config key. A key without a binding fails before an editor is half built. */
	public static Function<Options, Object> getter(String key)
	{
		return GETTERS.get(index(key));
	}

	/** Every setting's current value, in table order; each getter is called once. */
	static Object[] capture(Options config)
	{
		Object[] values = new Object[GETTERS.size()];
		for (int i = 0; i < values.length; i++)
		{
			values[i] = GETTERS.get(i).apply(config);
		}
		return values;
	}
}
