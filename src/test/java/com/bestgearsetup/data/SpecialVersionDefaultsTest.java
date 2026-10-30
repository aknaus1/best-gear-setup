package com.bestgearsetup.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

public class SpecialVersionDefaultsTest
{
	private static GameData bundled;

	@BeforeClass
	public static void load() throws Exception
	{
		bundled = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
	}

	private static MonsterSummary summary(String name, int level, boolean boss)
	{
		MonsterSummary m = new MonsterSummary();
		m.setName(name);
		m.setCombatLevel(level);
		m.setBoss(boss);
		return m;
	}

	private static GameData data(MonsterSummary... monsters)
	{
		return new GameData(Arrays.asList(monsters), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
	}

	private static String primary(String query)
	{
		return bundled.searchMonsterGroups(query, 1, false).get(0).getPrimary().getName();
	}

	@Test
	public void specialVersionsAreRecognised()
	{
		assertTrue(MonsterGroup.specialVersion("black dragon (echo)"));
		assertTrue(MonsterGroup.specialVersion("kalphite queen (echo) (crawling)"));
		assertTrue(MonsterGroup.specialVersion("cerberus (deadman) (apocalypse, armageddon)"));
		assertTrue(MonsterGroup.specialVersion("nazastarool (nightmare zone) (zombie (normal mode))"));
		assertTrue(MonsterGroup.specialVersion("kalphite queen (pvm arena) (crawling)"));
		assertTrue(MonsterGroup.specialVersion("Black Dragon (Echo)"));
		assertFalse(MonsterGroup.specialVersion("black dragon (level 227)"));
		assertFalse(MonsterGroup.specialVersion("forgotten soul (soul wars) (level 46)"));
		assertFalse(MonsterGroup.specialVersion("vorkath"));
	}

	@Test
	public void groupsPreferANormalVersion()
	{
		MonsterSummary echo = summary("black dragon (echo)", 227, false);
		MonsterSummary normal = summary("black dragon (level 227)", 227, false);
		MonsterSummary deadmanBoss = summary("yama (deadman) (permanent)", 524, true);
		MonsterSummary yama = summary("yama (normal)", 1238, true);
		MonsterSummary onlyDeadman = summary("bee swarm (deadman) (permanent)", 545, false);
		GameData data = data(echo, normal, deadmanBoss, yama, onlyDeadman,
			summary("bee swarm (deadman) (annihilation)", 1420, false));
		assertEquals(normal, data.searchMonsterGroups("black dragon", 1, false).get(0).getPrimary());
		assertEquals(yama, data.searchMonsterGroups("yama", 1, false).get(0).getPrimary());
		assertEquals(yama, data.searchMonsterGroups("yama", 1, true).get(0).getPrimary());
		assertEquals(onlyDeadman, data.searchMonsterGroups("bee swarm", 1, false).get(0).getPrimary());
	}

	@Test
	public void bundledGroupsDefaultToNormalVersions()
	{
		assertEquals("black dragon (level 227)", primary("black dragon"));
		assertEquals("splatter (level 22)", primary("splatter"));
		assertEquals("nazastarool (zombie)", primary("nazastarool"));
		assertEquals("witch's experiment (skavid)", primary("witch's experiment"));
		assertEquals("cerberus", primary("cerberus"));
		assertEquals("bee swarm (deadman) (permanent)", primary("bee swarm"));
		for (MonsterSummary monster : bundled.getMonsters())
		{
			for (boolean bosses : new boolean[]{false, true})
			{
				if (bosses && !monster.isBoss())
				{
					continue;
				}
				MonsterGroup group = bundled.groupOf(monster, bosses);
				boolean hasNormal = group.getVariants().stream().anyMatch(v -> !MonsterGroup.specialVersion(v.getName()));
				if (hasNormal)
				{
					assertFalse(group.getPrimary().getName(), MonsterGroup.specialVersion(group.getPrimary().getName()));
				}
			}
		}
	}

	@Test
	public void unknownNpcIdsMatchNormalVersions()
	{
		assertEquals("black dragon (level 227)", bundled.matchNpc(-1, "Black dragon", 227).getName());
		assertEquals("kree'arra (normal)", bundled.matchNpc(-1, "Kree'arra", 580).getName());
		assertEquals("witch's experiment (spider)", bundled.matchNpc(-1, "Witch's experiment", 30).getName());
		assertEquals("bee swarm (deadman) (permanent)", bundled.matchNpc(-1, "Bee swarm", 545).getName());
		// A known NPC id still wins, even for a special version.
		assertEquals("black dragon (echo)", bundled.matchNpc(15610, "Black dragon", 227).getName());
		assertEquals("black dragon (level 227)", bundled.matchNpc(252, "Black dragon", 227).getName());
	}
}
