package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;
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

	@Test public void automaticUnknownAndExplicitOverridesAreDifferent()
	{
		assertFalse(AutoState.AUTO.resolve(null, false));
		assertTrue(AutoState.AUTO.resolve(true, false));
		assertFalse(AutoState.AUTO.resolve(false, true));
		assertTrue(AutoState.ON.resolve(false, false));
		assertFalse(AutoState.OFF.resolve(true, true));
	}
}
