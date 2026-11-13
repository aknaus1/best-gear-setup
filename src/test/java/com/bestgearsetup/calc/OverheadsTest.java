package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.Overheads.Advice;
import com.bestgearsetup.calc.Overheads.Overhead;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import java.util.Arrays;
import org.junit.Test;

public class OverheadsTest
{
	private static Monster named(String name)
	{
		Monster m = new Monster();
		m.setName(name);
		return m;
	}

	@Test
	public void lookupKeysBossesByNameAndVersion()
	{
		Advice vorkath = Overheads.recommend(named("vorkath (post-quest)"), CombatClass.RANGED);
		assertEquals(Overhead.MAGIC, vorkath.getPrayer());
		assertFalse(vorkath.isSwitching());
		assertEquals("Protect from Magic", vorkath.summary());
		assertEquals(Overhead.MELEE, Overheads.recommend(named("phantom muspah (melee)"), CombatClass.RANGED).getPrayer());
		assertNull(Overheads.recommend(named("no such monster"), CombatClass.MELEE));
	}

	@Test
	public void targetsWithoutAnOverheadAndSwitchingFights()
	{
		Advice none = Overheads.recommend(named("zalcano"), CombatClass.MELEE);
		assertNull(none.getPrayer());
		assertEquals("None needed", none.summary());
		Advice zebak = Overheads.recommend(named("zebak"), CombatClass.RANGED);
		assertEquals(Arrays.asList(Overhead.MISSILES, Overhead.MAGIC), zebak.getSwitches());
		assertTrue(zebak.isSwitching());
		assertEquals("Switch: Missiles / Magic", zebak.summary());
	}
}
