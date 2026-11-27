package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import java.awt.Color;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;

public class AssumptionsTest
{
	/** Config defaults read their captured value through Assumptions.value; RuneLite's proxy must still serve them. */
	@Test public void runeliteConfigurationDefaultsAndSnapshotsShareTheSameValues()
	{
		// Exercise RuneLite's actual config proxy: it rejects unannotated instance methods.
		ConfigManager manager = mock(ConfigManager.class, withSettings().useConstructor(constructorArguments()));
		doCallRealMethod().when(manager).getConfig(Options.class);
		Options live = manager.getConfig(Options.class);
		Options defaults = new Options() { };
		Options snapshot = Assumptions.capture(live, Collections.emptyMap());
		assertEquals("Search snapshots are plain immutable objects", Assumptions.class, snapshot.getClass());
		for (Map.Entry<String, Function<Options, Object>> entry : GETTERS.entrySet())
		{
			assertEquals(entry.getKey(), entry.getValue().apply(defaults), entry.getValue().apply(live));
			assertEquals(entry.getKey(), entry.getValue().apply(defaults), entry.getValue().apply(snapshot));
		}
	}

	/**
	 * Arguments for ConfigManager's injected constructor, matched by type so a RuneLite release that adds, removes
	 * or reorders its parameters doesn't break the test: the executor is mocked, everything else is null or zero.
	 */
	private static Object[] constructorArguments()
	{
		Constructor<?> constructor = Arrays.stream(ConfigManager.class.getDeclaredConstructors())
			.max(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow(IllegalStateException::new);
		Class<?>[] types = constructor.getParameterTypes();
		Object[] arguments = new Object[types.length];
		for (int i = 0; i < types.length; i++)
		{
			if (types[i] == ScheduledExecutorService.class)
			{
				arguments[i] = mock(ScheduledExecutorService.class);
			}
			else if (types[i] == boolean.class)
			{
				arguments[i] = false;
			}
			else if (types[i].isPrimitive())
			{
				arguments[i] = 0;
			}
		}
		return arguments;
	}

	/** Both settings editors look getters up by stored key in ConfigSettings. */
	@Test public void sharedEditorBindingsMatchEveryPersistedConfigKey()
	{
		Options defaults = new Options() { };
		for (ConfigItemDescriptor item : ConfigItemsForTests.items())
		{
			String key = item.key();
			assertEquals(key, GETTERS.get(METHOD_BY_KEY.getOrDefault(key, key)).apply(defaults),
				ConfigSettings.getter(key).apply(defaults));
		}
	}

	@Test(expected = IllegalArgumentException.class) public void mistypedOverrideIsRejected()
	{
		Assumptions.capture(new Options() { }, Collections.singletonMap("ammoCount", "1000"));
	}

	@Test public void snapshotKeepsValuesWhenPreferencesAndOverrideMapChange()
	{
		int[] stacks = {4};
		Options planning = new Options() { @Override public int soulreaperStacks() { return stacks[0]; } };
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("currentHitpoints", 21);
		Options snapshot = Assumptions.capture(planning, overrides);
		stacks[0] = 2; overrides.put("currentHitpoints", 80);
		assertEquals(4, snapshot.soulreaperStacks()); assertEquals(21, snapshot.currentHitpoints());
		assertEquals(0, planning.currentHitpoints()); assertEquals(2, planning.soulreaperStacks());
		assertEquals(planning.fillMargin(), snapshot.fillMargin(), 0);
		assertEquals(planning.antifire(), snapshot.antifire());
	}

	/** Every key the plugin puts in a search's detected overrides names a setting, so capturing never throws. */
	@Test public void pluginOverrideKeysAreConfigKeys() throws Exception
	{
		String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
			"src/main/java/com/bestgearsetup/BestGearSetupPlugin.java")), java.nio.charset.StandardCharsets.UTF_8);
		java.util.regex.Matcher put = java.util.regex.Pattern.compile(
			"detected\\.put\\((?:\"(\\w+)\"|(\\S+?)),").matcher(source);
		int keys = 0;
		while (put.find())
		{
			assertTrue("override key is not a literal: " + put.group(2), put.group(2) == null);
			ConfigSettings.index(put.group(1));
			keys++;
		}
		assertTrue("found the plugin's detected overrides", keys >= 4);
	}

	/** Every config item's getter, by key; the first test below fails until a new item is added here. */
	private static final Map<String, Function<Options, Object>> GETTERS = new LinkedHashMap<>();

	static
	{
		GETTERS.put("showMenuOption", Options::showMenuOption);
		GETTERS.put("highlightBankGear", Options::highlightBankGear);
		GETTERS.put("bankHighlightColor", Options::bankHighlightColor);
		GETTERS.put("highlightInventoryGear", Options::highlightInventoryGear);
		GETTERS.put("inventoryHighlightColor", Options::inventoryHighlightColor);
		GETTERS.put("theme", Options::theme);
		GETTERS.put("customTheme", Options::customTheme);
		GETTERS.put("fillFood", Options::fillFood);
		GETTERS.put("mode", Options::mode);
		GETTERS.put("budget", Options::budget);
		GETTERS.put("noGe", Options::noGe);
		GETTERS.put("bossesOnly", Options::bossesOnly);
		GETTERS.put("onSlayerTask", Options::onSlayerTask);
		GETTERS.put("membersItems", Options::membersItems);
		GETTERS.put("ammoCount", Options::ammoCount);
		GETTERS.put("killsPerTrip", Options::killsPerTrip);
		GETTERS.put("currentHitpoints", Options::currentHitpoints);
		GETTERS.put("wilderness", Options::wilderness);
		GETTERS.put("limitWildernessRisk", Options::limitWildernessRisk);
		GETTERS.put("maxExpensiveItems", Options::maxExpensiveItems);
		GETTERS.put("expensiveItemThreshold", Options::expensiveItemThreshold);
		GETTERS.put("charge", Options::charge);
		GETTERS.put("sunfireRunes", Options::sunfireRunes);
		GETTERS.put("kandarinDiary", Options::kandarinDiary);
		GETTERS.put("killSpecials", Options::killSpecials);
		GETTERS.put("specEnergy", Options::specEnergy);
		GETTERS.put("drainSpecs", Options::drainSpecs);
		GETTERS.put("weaponPoison", Options::weaponPoison);
		GETTERS.put("forinthrySurge", Options::forinthrySurge);
		GETTERS.put("soulreaperStacks", Options::soulreaperStacks);
		GETTERS.put("miningLevel", Options::miningLevel);
		GETTERS.put("raidPartySize", Options::raidPartySize);
		GETTERS.put("toaRaidLevel", Options::toaRaidLevel);
		GETTERS.put("toaPathLevel", Options::toaPathLevel);
		GETTERS.put("coxChallengeMode", Options::coxChallengeMode);
		GETTERS.put("encounterPhase", Options::encounterPhase);
		GETTERS.put("aoe", Options::aoe);
		GETTERS.put("aoeTargets", Options::aoeTargets);
		GETTERS.put("antifire", Options::antifire);
		GETTERS.put("dmmItems", Options::dmmItems);
		GETTERS.put("betaItems", Options::betaItems);
		GETTERS.put("bountyHunterItems", Options::bountyHunterItems);
		GETTERS.put("requireAtlatlAmmoRecovery", Options::requireAtlatlAmmoRecovery);
		GETTERS.put("preventManualCast", Options::preventManualCast);
		GETTERS.put("styleStab", Options::styleStab);
		GETTERS.put("styleSlash", Options::styleSlash);
		GETTERS.put("styleCrush", Options::styleCrush);
		GETTERS.put("styleRanged", Options::styleRanged);
		GETTERS.put("styleMagic", Options::styleMagic);
		GETTERS.put("styleAtlatl", Options::styleAtlatl);
		GETTERS.put("weaponHands", Options::weaponHands);
		GETTERS.put("calcMode", Options::calcMode);
		GETTERS.put("attackXp", Options::attackXp);
		GETTERS.put("strengthXp", Options::strengthXp);
		GETTERS.put("defenceXp", Options::defenceXp);
		GETTERS.put("fillMode", Options::fillMode);
		GETTERS.put("defenceFocus", Options::defenceFocus);
		GETTERS.put("fillMargin", Options::fillMargin);
		GETTERS.put("specVulnerability", Options::specVulnerability);
		GETTERS.put("specTomeOfWater", Options::specTomeOfWater);
		GETTERS.put("specElderMaul", Options::specElderMaul);
		GETTERS.put("specDwh", Options::specDwh);
		GETTERS.put("specEmberlight", Options::specEmberlight);
		GETTERS.put("specArclight", Options::specArclight);
		GETTERS.put("specTonalztics", Options::specTonalztics);
		GETTERS.put("specBgsDamage", Options::specBgsDamage);
		GETTERS.put("specSeercullDamage", Options::specSeercullDamage);
		GETTERS.put("specAyakDamage", Options::specAyakDamage);
		GETTERS.put("excludedPrayers", Options::excludedPrayers);
		GETTERS.put("thrall", Options::thrall);
		GETTERS.put("activeEncounter", Options::activeEncounter);
		GETTERS.put("taskMode", Options::taskMode);
		GETTERS.put("autoRaid", Options::autoRaid);
		GETTERS.put("autoSpecs", Options::autoSpecs);
		GETTERS.put("locks", Options::locks);
		GETTERS.put("excluded", Options::excluded);
		GETTERS.put("blockedSpells", Options::blockedSpells);
		GETTERS.put("forcedSpells", Options::forcedSpells);
	}

	/** Config keys that differ from their method name, which is what overrides are keyed by. */
	private static final Map<String, String> METHOD_BY_KEY = new HashMap<>();

	static
	{
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
		Options defaults = new Options() { };
		Map<String, Object> changed = new LinkedHashMap<>();
		for (Map.Entry<String, Function<Options, Object>> e : GETTERS.entrySet())
		{
			changed.put(e.getKey(), different(e.getValue().apply(defaults)));
		}
		// Overriding every key at once gives a source whose every value differs from the default.
		Options source = Assumptions.capture(defaults, changed);
		Options copied = Assumptions.capture(source, Collections.emptyMap());
		for (Map.Entry<String, Object> e : changed.entrySet())
		{
			Function<Options, Object> getter = GETTERS.get(e.getKey());
			assertEquals(e.getKey(), e.getValue(), getter.apply(source));
			assertEquals(e.getKey(), e.getValue(), getter.apply(copied));
			Options overridden = Assumptions.capture(defaults, Collections.singletonMap(e.getKey(), e.getValue()));
			assertEquals(e.getKey(), e.getValue(), getter.apply(overridden));
		}
	}

	@Test(expected = IllegalArgumentException.class) public void unknownOverrideIsRejected()
	{
		Assumptions.capture(new Options() { }, Collections.singletonMap("noSuchSetting", true));
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
