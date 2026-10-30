package com.bestgearsetup.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

public class SpecialVersionTiersTest
{
	private static MonsterSummary summary(String name, int level, boolean boss, int id)
	{
		MonsterSummary monster = new MonsterSummary();
		monster.setName(name);
		monster.setCombatLevel(level);
		monster.setBoss(boss);
		monster.setId(id);
		return monster;
	}

	private static GameData data(MonsterSummary... monsters)
	{
		return new GameData(Arrays.asList(monsters), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
	}

	@Test
	public void specialVersionRecognisesOnlyTheFourParenthesisedTokens()
	{
		for (String name : Arrays.asList("black dragon (ECHO)", "splatter (Deadman) (level 50)",
			"witch's experiment (NIGHTMARE ZONE)", "kalphite queen (PvM Arena)"))
		{
			assertTrue(name, MonsterGroup.specialVersion(name));
		}
		for (String name : Arrays.asList("black dragon (level 227)", "echo dragon", "dragon (echoes)",
			"the nightmare zone guardian"))
		{
			assertFalse(name, MonsterGroup.specialVersion(name));
		}
	}

	@Test
	public void primaryPrefersNormalBossThenNormalVersionThenOriginalRule()
	{
		MonsterSummary echoBoss = summary("dragon (echo)", 100, true, 1);
		MonsterSummary normal = summary("dragon (level 227)", 227, false, 2);
		MonsterSummary normalBoss = summary("dragon (level 300)", 300, true, 3);
		assertEquals(normalBoss, data(echoBoss, normal, normalBoss).searchMonsterGroups("dragon", 1, false)
			.get(0).getPrimary());
		assertEquals(normal, data(echoBoss, normal).searchMonsterGroups("dragon", 1, false)
			.get(0).getPrimary());
		MonsterSummary deadman = summary("dragon (deadman)", 200, false, 4);
		assertEquals(echoBoss, data(deadman, echoBoss).searchMonsterGroups("dragon", 1, false)
			.get(0).getPrimary());
		assertEquals(deadman, data(deadman).searchMonsterGroups("dragon", 1, false)
			.get(0).getPrimary());
	}

	@Test
	public void matchNpcPrefersNormalAtAnyTierButPreservesExactId()
	{
		MonsterSummary echo = summary("dragon (echo)", 227, true, 10);
		MonsterSummary normal = summary("dragon (level 227)", 227, true, 11);
		MonsterSummary other = summary("dragon (level 100)", 100, true, 12);
		GameData data = data(echo, normal, other);
		assertEquals(echo, data.matchNpc(10, "dragon", 227));
		assertEquals(normal, data.matchNpc(-1, "Dragon", 227));
		assertEquals(other, data.matchNpc(-1, "dragon", 100));
		assertEquals(normal, data.matchNpc(-1, "dragon", 999));
		assertNull(data.matchNpc(-1, "unrelated", 227));
		assertNull(data.matchNpc(-1, null, 227));
	}

	@Test
	public void normalLowerTierStillBeatsSpecialHigherTier()
	{
		MonsterSummary special = summary("dragon (echo)", 227, false, 1);
		MonsterSummary normal = summary("dragon (level 100)", 100, false, 2);
		assertEquals(normal, data(special, normal).matchNpc(-1, "dragon", 227));
		assertEquals(special, data(special).matchNpc(-1, "dragon", 227));
	}

	@Test
	public void matchNpcKeepsNameAndLevelTierOrderWithinNormalVersions()
	{
		MonsterSummary exactName = summary("dragon", 100, false, 1);
		MonsterSummary prefixLevel = summary("dragon (level 227)", 227, false, 2);
		GameData data = data(exactName, prefixLevel);
		assertEquals(exactName, data.matchNpc(-1, "dragon", 100));
		assertEquals(prefixLevel, data.matchNpc(-1, "dragon", 227));
		assertEquals(exactName, data.matchNpc(-1, "dragon", 999));
	}

	@Test
	public void bundledSearchUsesNormalDefaults() throws Exception
	{
		GameData data = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
		for (String name : Arrays.asList("black dragon", "splatter", "nazastarool", "witch's experiment"))
		{
			MonsterGroup group = data.searchMonsterGroups(name, 1, false).get(0);
			assertEquals(name, group.getName());
			assertFalse(name, MonsterGroup.specialVersion(group.getPrimary().getName()));
		}
	}
}
