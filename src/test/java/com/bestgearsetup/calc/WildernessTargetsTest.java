package com.bestgearsetup.calc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WildernessTargetsTest
{
	@Test
	public void wildernessOnlyMonstersMatch()
	{
		assertTrue(WildernessTargets.only("mammoth"));
		assertTrue(WildernessTargets.only("Revenant dragon"));
		assertTrue(WildernessTargets.only("vet'ion (enraged)"));
		assertTrue(WildernessTargets.only("skeleton hellhound (calvar'ion)"));
		assertTrue(WildernessTargets.only("callisto"));
	}

	@Test
	public void monstersFoundElsewhereDoNotMatch()
	{
		assertFalse(WildernessTargets.only("callisto (pvm arena)"));
		assertFalse(WildernessTargets.only("king black dragon"));
		assertFalse(WildernessTargets.only("green dragon"));
		assertFalse(WildernessTargets.only(""));
		assertFalse(WildernessTargets.only(null));
	}
}
