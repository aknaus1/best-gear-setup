package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.awt.Color;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.config.ConfigItem;
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

	/** A new config item that the snapshot forgets would silently fall back to its default during searches. */
	@Test public void everyConfigItemIsCopiedAndCanBeOverridden() throws Exception
	{
		BestGearSetupConfig defaults = new BestGearSetupConfig() { };
		Map<String, Object> changed = new LinkedHashMap<>();
		Map<String, Method> methods = new HashMap<>();
		for (Method m : BestGearSetupConfig.class.getMethods())
		{
			if (m.getParameterCount() == 0 && m.isAnnotationPresent(ConfigItem.class))
			{
				changed.put(m.getName(), different(m.getReturnType(), m.invoke(defaults)));
				methods.put(m.getName(), m);
			}
		}
		BestGearSetupConfig source = (BestGearSetupConfig) Proxy.newProxyInstance(BestGearSetupConfig.class.getClassLoader(),
			new Class<?>[]{BestGearSetupConfig.class}, (proxy, method, args) -> changed.get(method.getName()));

		BestGearSetupConfig copied = SearchAssumptions.capture(source, Collections.emptyMap());
		for (Map.Entry<String, Object> e : changed.entrySet())
		{
			assertEquals(e.getKey(), e.getValue(), methods.get(e.getKey()).invoke(copied));
			BestGearSetupConfig overridden = SearchAssumptions.capture(defaults, Collections.singletonMap(e.getKey(), e.getValue()));
			assertEquals(e.getKey(), e.getValue(), methods.get(e.getKey()).invoke(overridden));
		}
	}

	@Test(expected = IllegalArgumentException.class) public void unknownOverrideIsRejected()
	{
		SearchAssumptions.capture(new BestGearSetupConfig() { }, Collections.singletonMap("noSuchSetting", true));
	}

	private static Object different(Class<?> type, Object value)
	{
		if (type == boolean.class) { return !(Boolean) value; }
		if (type == int.class) { return (Integer) value + 1; }
		if (type == double.class) { return (Double) value + 0.5; }
		if (type == String.class) { return value + "-changed"; }
		if (type == Color.class) { return new Color(1, 2, 3); }
		if (type.isEnum())
		{
			Object[] constants = type.getEnumConstants();
			return constants[(((Enum<?>) value).ordinal() + 1) % constants.length];
		}
		throw new AssertionError("Add a changed value for " + type);
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
