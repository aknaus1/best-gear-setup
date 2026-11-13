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

	@Test
	public void enragedWardensUseThePhaseThreeAdvice()
	{
		for (String warden : new String[]{"elidinis' warden", "tumeken's warden"})
			assertEquals(Overheads.recommend(named(warden + " (phase 3)"), CombatClass.MAGIC),
				Overheads.recommend(named(warden + " (phase 3 enraged)"), CombatClass.MAGIC));
	}

	@Test
	public void everyRuleParses() throws Exception
	{
		com.google.gson.JsonArray rules = com.bestgearsetup.data.NameLists.load("/com/bestgearsetup/encounter-prayers.json")
			.getAsJsonArray("rules");
		for (com.google.gson.JsonElement element : rules)
		{
			com.google.gson.JsonObject rule = element.getAsJsonObject();
			assertTrue(rule.toString(), rule.getAsJsonArray("names").size() > 0);
			for (String part : new String[]{"", "melee"})
			{
				com.google.gson.JsonObject advice = part.isEmpty() ? rule : rule.getAsJsonObject(part);
				if (advice == null)
					continue;
				assertTrue(rule.toString(), !part.equals("melee") || advice.has("note"));
				if (advice.has("prayers"))
					advice.getAsJsonArray("prayers").forEach(p -> Overhead.valueOf(p.getAsString()));
			}
		}
	}
}
