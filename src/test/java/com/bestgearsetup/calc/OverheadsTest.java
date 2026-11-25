package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.Overheads.Advice;
import com.bestgearsetup.calc.Overheads.Overhead;
import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.Names;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.http.api.RuneLiteAPI;
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
		Advice vorkath = Overheads.recommend(named("vorkath (post-quest)"), Combat.RANGED);
		assertEquals(Overhead.MAGIC, vorkath.getPrayer());
		assertFalse(vorkath.isSwitching());
		assertEquals("Protect from Magic", vorkath.summary());
		assertEquals(Overhead.MELEE, Overheads.recommend(named("phantom muspah (melee)"), Combat.RANGED).getPrayer());
		assertNull(Overheads.recommend(named("no such monster"), Combat.MELEE));
	}

	@Test
	public void metalDragonsNeedNoOverheadAtRangeButMeleeUpClose()
	{
		// Metal dragons stay out of melee range and breathe from a distance; Protect from Magic doesn't block it.
		for (Combat cls : new Combat[]{Combat.RANGED, Combat.MAGIC})
		{
			Advice ranged = Overheads.recommend(named("steel dragon (level 246)"), cls);
			assertNull(ranged.getPrayer());
			assertEquals("None needed", ranged.summary());
		}
		assertEquals(Overhead.MELEE, Overheads.recommend(named("steel dragon (level 246)"), Combat.MELEE).getPrayer());
		assertNull(Overheads.recommend(named("bronze dragon (catacombs of kourend)"), Combat.MAGIC).getPrayer());
	}

	@Test
	public void targetsWithoutAnOverheadAndSwitchingFights()
	{
		Advice none = Overheads.recommend(named("zalcano"), Combat.MELEE);
		assertNull(none.getPrayer());
		assertEquals("None needed", none.summary());
		Advice zebak = Overheads.recommend(named("zebak"), Combat.RANGED);
		assertEquals(Arrays.asList(Overhead.MISSILES, Overhead.MAGIC), zebak.getSwitches());
		assertTrue(zebak.isSwitching());
		assertEquals("Switch: Missiles / Magic", zebak.summary());
	}

	@Test
	public void excludedOverheadsLeaveTheAdvice()
	{
		Advice zebak = Overheads.recommend(named("zebak"), Combat.RANGED);
		assertSame(zebak, zebak.without(java.util.Collections.singleton("protect from melee")));
		Advice magicOnly = zebak.without(java.util.Collections.singleton("protect from missiles"));
		assertEquals(Overhead.MAGIC, magicOnly.getPrayer());
		assertFalse(magicOnly.isSwitching());
		assertEquals(Arrays.asList(Overhead.MISSILES), magicOnly.getExcluded());
		Advice vorkath = Overheads.recommend(named("vorkath (post-quest)"), Combat.RANGED)
			.without(java.util.Collections.singleton("protect from magic"));
		assertNull(vorkath.getPrayer());
		assertEquals("No overhead", vorkath.summary());
	}

	@Test
	public void enragedWardensUseThePhaseThreeAdvice()
	{
		for (String warden : new String[]{"elidinis' warden", "tumeken's warden"})
			assertEquals(Overheads.recommend(named(warden + " (phase 3)"), Combat.MAGIC),
				Overheads.recommend(named(warden + " (phase 3 enraged)"), Combat.MAGIC));
	}

	@Test
	public void everyRuleParses() throws Exception
	{
		com.google.gson.JsonArray rules = com.bestgearsetup.data.Names.load("/com/bestgearsetup/encounter-prayers.json")
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

	@Test
	public void everyRuleNamesABundledMonster() throws java.io.IOException
	{
		Set<String> monsters = new GameDataLoader(RuneLiteAPI.GSON).loadGameData().getMonsters().stream()
			.map(m -> m.getName().toLowerCase(java.util.Locale.ROOT)).collect(Collectors.toSet());
		List<String> stale = new ArrayList<>();
		for (JsonElement rule : Names.load("/com/bestgearsetup/encounter-prayers.json").getAsJsonArray("rules"))
			for (JsonElement element : rule.getAsJsonObject().getAsJsonArray("names"))
			{
				String name = element.getAsString();
				String start = name.startsWith("prefix:") ? name.substring(7) : null;
				if (monsters.stream().noneMatch(m -> start != null ? m.startsWith(start) : m.equals(name) || m.startsWith(name + " (")))
					stale.add(name);
			}
		assertTrue("Rule names matching no monster: " + stale, stale.isEmpty());
	}
}
