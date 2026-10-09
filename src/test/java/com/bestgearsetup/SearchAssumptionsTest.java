package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import net.runelite.client.config.ConfigItemDescriptor;
import org.junit.Test;

public class SearchAssumptionsTest
{
	@Test public void snapshotKeepsValuesWhenPreferencesAndOverrideMapChange()
	{
		int[] stacks = {4};
		BestGearSetupConfig planning = new BestGearSetupConfig() { @Override public int soulreaperStacks() { return stacks[0]; } };
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("currentHitpoints", 21);
		BestGearSetupConfig snapshot = SearchAssumptions.capture(planning, overrides);
		stacks[0] = 2; overrides.put("currentHitpoints", 80);
		assertEquals(4, snapshot.soulreaperStacks()); assertEquals(21, snapshot.currentHitpoints());
		assertEquals(0, planning.currentHitpoints()); assertEquals(2, planning.soulreaperStacks());
		assertEquals(planning.fillMargin(), snapshot.fillMargin(), 0);
		assertEquals(planning.meleePotion(), snapshot.meleePotion());
	}

	/** Every config item's getter, by key; the first test below fails until a new item is added here. */
	private static final Map<String, Function<BestGearSetupConfig, Object>> GETTERS = new LinkedHashMap<>();

	static
	{
		GETTERS.put("showMenuOption", BestGearSetupConfig::showMenuOption);
		GETTERS.put("highlightBankGear", BestGearSetupConfig::highlightBankGear);
		GETTERS.put("bankHighlightColor", BestGearSetupConfig::bankHighlightColor);
		GETTERS.put("highlightInventoryGear", BestGearSetupConfig::highlightInventoryGear);
		GETTERS.put("inventoryHighlightColor", BestGearSetupConfig::inventoryHighlightColor);
		GETTERS.put("mode", BestGearSetupConfig::mode);
		GETTERS.put("budget", BestGearSetupConfig::budget);
		GETTERS.put("bossesOnly", BestGearSetupConfig::bossesOnly);
		GETTERS.put("onSlayerTask", BestGearSetupConfig::onSlayerTask);
		GETTERS.put("ancientMagicks", BestGearSetupConfig::ancientMagicks);
		GETTERS.put("arceuusSpellbook", BestGearSetupConfig::arceuusSpellbook);
		GETTERS.put("membersItems", BestGearSetupConfig::membersItems);
		GETTERS.put("ammoCount", BestGearSetupConfig::ammoCount);
		GETTERS.put("currentHitpoints", BestGearSetupConfig::currentHitpoints);
		GETTERS.put("wilderness", BestGearSetupConfig::wilderness);
		GETTERS.put("limitWildernessRisk", BestGearSetupConfig::limitWildernessRisk);
		GETTERS.put("maxExpensiveItems", BestGearSetupConfig::maxExpensiveItems);
		GETTERS.put("expensiveItemThreshold", BestGearSetupConfig::expensiveItemThreshold);
		GETTERS.put("charge", BestGearSetupConfig::charge);
		GETTERS.put("markOfDarkness", BestGearSetupConfig::markOfDarkness);
		GETTERS.put("sunfireRunes", BestGearSetupConfig::sunfireRunes);
		GETTERS.put("kandarinDiary", BestGearSetupConfig::kandarinDiary);
		GETTERS.put("killSpecials", BestGearSetupConfig::killSpecials);
		GETTERS.put("specEnergy", BestGearSetupConfig::specEnergy);
		GETTERS.put("drainSpecs", BestGearSetupConfig::drainSpecs);
		GETTERS.put("weaponPoison", BestGearSetupConfig::weaponPoison);
		GETTERS.put("forinthrySurge", BestGearSetupConfig::forinthrySurge);
		GETTERS.put("soulreaperStacks", BestGearSetupConfig::soulreaperStacks);
		GETTERS.put("miningLevel", BestGearSetupConfig::miningLevel);
		GETTERS.put("raidPartySize", BestGearSetupConfig::raidPartySize);
		GETTERS.put("toaRaidLevel", BestGearSetupConfig::toaRaidLevel);
		GETTERS.put("toaPathLevel", BestGearSetupConfig::toaPathLevel);
		GETTERS.put("coxChallengeMode", BestGearSetupConfig::coxChallengeMode);
		GETTERS.put("encounterPhase", BestGearSetupConfig::encounterPhase);
		GETTERS.put("aoe", BestGearSetupConfig::aoe);
		GETTERS.put("aoeTargets", BestGearSetupConfig::aoeTargets);
		GETTERS.put("requireFireProtection", BestGearSetupConfig::requireFireProtection);
		GETTERS.put("antifire", BestGearSetupConfig::antifire);
		GETTERS.put("protectMagic", BestGearSetupConfig::protectMagic);
		GETTERS.put("dmmItems", BestGearSetupConfig::dmmItems);
		GETTERS.put("betaItems", BestGearSetupConfig::betaItems);
		GETTERS.put("bountyHunterItems", BestGearSetupConfig::bountyHunterItems);
		GETTERS.put("requireAtlatlAmmoRecovery", BestGearSetupConfig::requireAtlatlAmmoRecovery);
		GETTERS.put("styleStab", BestGearSetupConfig::styleStab);
		GETTERS.put("styleSlash", BestGearSetupConfig::styleSlash);
		GETTERS.put("styleCrush", BestGearSetupConfig::styleCrush);
		GETTERS.put("styleRanged", BestGearSetupConfig::styleRanged);
		GETTERS.put("styleMagic", BestGearSetupConfig::styleMagic);
		GETTERS.put("styleAtlatl", BestGearSetupConfig::styleAtlatl);
		GETTERS.put("weaponHands", BestGearSetupConfig::weaponHands);
		GETTERS.put("calcMode", BestGearSetupConfig::calcMode);
		GETTERS.put("searchDepth", BestGearSetupConfig::searchDepth);
		GETTERS.put("attackXp", BestGearSetupConfig::attackXp);
		GETTERS.put("strengthXp", BestGearSetupConfig::strengthXp);
		GETTERS.put("defenceXp", BestGearSetupConfig::defenceXp);
		GETTERS.put("fillMode", BestGearSetupConfig::fillMode);
		GETTERS.put("defenceFocus", BestGearSetupConfig::defenceFocus);
		GETTERS.put("fillMargin", BestGearSetupConfig::fillMargin);
		GETTERS.put("specVulnerability", BestGearSetupConfig::specVulnerability);
		GETTERS.put("specTomeOfWater", BestGearSetupConfig::specTomeOfWater);
		GETTERS.put("specElderMaul", BestGearSetupConfig::specElderMaul);
		GETTERS.put("specDwh", BestGearSetupConfig::specDwh);
		GETTERS.put("specEmberlight", BestGearSetupConfig::specEmberlight);
		GETTERS.put("specArclight", BestGearSetupConfig::specArclight);
		GETTERS.put("specTonalztics", BestGearSetupConfig::specTonalztics);
		GETTERS.put("specBgsDamage", BestGearSetupConfig::specBgsDamage);
		GETTERS.put("specSeercullDamage", BestGearSetupConfig::specSeercullDamage);
		GETTERS.put("specAyakDamage", BestGearSetupConfig::specAyakDamage);
		GETTERS.put("usePrayers", BestGearSetupConfig::usePrayers);
		GETTERS.put("thrall", BestGearSetupConfig::thrall);
		GETTERS.put("raidPotions", BestGearSetupConfig::raidPotions);
		GETTERS.put("taskMode", BestGearSetupConfig::taskMode);
		GETTERS.put("autoRaid", BestGearSetupConfig::autoRaid);
		GETTERS.put("meleePotion", BestGearSetupConfig::meleePotion);
		GETTERS.put("rangedPotion", BestGearSetupConfig::rangedPotion);
		GETTERS.put("magicPotion", BestGearSetupConfig::magicPotion);
		GETTERS.put("potionMelee", BestGearSetupConfig::potionMelee);
		GETTERS.put("potionRanged", BestGearSetupConfig::potionRanged);
		GETTERS.put("potionMagic", BestGearSetupConfig::potionMagic);
		GETTERS.put("locks", BestGearSetupConfig::locks);
		GETTERS.put("excluded", BestGearSetupConfig::excluded);
	}

	/** Config keys that differ from their method name, which is what overrides are keyed by. */
	private static final Map<String, String> METHOD_BY_KEY = new HashMap<>();

	static
	{
		METHOD_BY_KEY.put("meleePotionChoice", "meleePotion");
		METHOD_BY_KEY.put("rangedPotionChoice", "rangedPotion");
		METHOD_BY_KEY.put("magicPotionChoice", "magicPotion");
		METHOD_BY_KEY.put(BestGearSetupConfig.POTION_KEY_PREFIX + "MELEE", "potionMelee");
		METHOD_BY_KEY.put(BestGearSetupConfig.POTION_KEY_PREFIX + "RANGED", "potionRanged");
		METHOD_BY_KEY.put(BestGearSetupConfig.POTION_KEY_PREFIX + "MAGIC", "potionMagic");
	}

	@Test public void getterTableCoversEveryConfigItem()
	{
		Set<String> methods = new TreeSet<>();
		for (ConfigItemDescriptor item : ConfigItemsForTests.items())
		{
			String key = item.getItem().keyName();
			methods.add(METHOD_BY_KEY.getOrDefault(key, key));
		}
		assertEquals(methods, new TreeSet<>(GETTERS.keySet()));
	}

	/** A new config item that the snapshot forgets would silently fall back to its default during searches. */
	@Test public void everyConfigItemIsCopiedAndCanBeOverridden()
	{
		BestGearSetupConfig defaults = new BestGearSetupConfig() { };
		Map<String, Object> changed = new LinkedHashMap<>();
		for (Map.Entry<String, Function<BestGearSetupConfig, Object>> e : GETTERS.entrySet())
		{
			changed.put(e.getKey(), different(e.getValue().apply(defaults)));
		}
		// Overriding every key at once gives a source whose every value differs from the default.
		BestGearSetupConfig source = SearchAssumptions.capture(defaults, changed);
		BestGearSetupConfig copied = SearchAssumptions.capture(source, Collections.emptyMap());
		for (Map.Entry<String, Object> e : changed.entrySet())
		{
			Function<BestGearSetupConfig, Object> getter = GETTERS.get(e.getKey());
			assertEquals(e.getKey(), e.getValue(), getter.apply(source));
			assertEquals(e.getKey(), e.getValue(), getter.apply(copied));
			BestGearSetupConfig overridden = SearchAssumptions.capture(defaults, Collections.singletonMap(e.getKey(), e.getValue()));
			assertEquals(e.getKey(), e.getValue(), getter.apply(overridden));
		}
	}

	@Test(expected = IllegalArgumentException.class) public void unknownOverrideIsRejected()
	{
		SearchAssumptions.capture(new BestGearSetupConfig() { }, Collections.singletonMap("noSuchSetting", true));
	}

	private static Object different(Object value)
	{
		if (value instanceof Boolean) { return !(Boolean) value; }
		if (value instanceof Integer) { return (Integer) value + 1; }
		if (value instanceof Double) { return (Double) value + 0.5; }
		if (value instanceof String) { return value + "-changed"; }
		if (value instanceof Color) { return new Color(1, 2, 3); }
		if (value instanceof Enum)
		{
			return next((Enum<?>) value);
		}
		throw new AssertionError("Add a changed value for " + value);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object next(Enum<?> value)
	{
		List<Enum<?>> constants = new ArrayList<>(EnumSet.allOf((Class) value.getDeclaringClass()));
		return constants.get((value.ordinal() + 1) % constants.size());
	}

	@Test public void automaticUnknownAndExplicitOverridesAreDifferent()
	{
		assertFalse(AutoState.AUTO.resolve(null, false));
		assertTrue(AutoState.AUTO.resolve(true, false));
		assertFalse(AutoState.AUTO.resolve(false, true));
		assertTrue(AutoState.ON.resolve(false, false));
		assertFalse(AutoState.OFF.resolve(true, true));
	}
}
