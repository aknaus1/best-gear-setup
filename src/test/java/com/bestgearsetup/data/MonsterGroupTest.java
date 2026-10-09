package com.bestgearsetup.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

public class MonsterGroupTest
{
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

	@Test
	public void baseNameDropsTrailingVersions()
	{
		assertEquals("doom of mokhaiotl", MonsterGroup.baseName("doom of mokhaiotl (delve 1) (npc 14708)"));
		assertEquals("abyssal sire", MonsterGroup.baseName("abyssal sire (phase 3 (stage 1))"));
		assertEquals("nex", MonsterGroup.baseName("nex"));
		assertEquals("k'ril tsutsaroth", MonsterGroup.baseName("k'ril tsutsaroth"));
	}

	@Test
	public void variantsAreGroupedAndLabelledInNaturalOrder()
	{
		// Bundle order: default versions first.
		GameData data = data(summary("doom of mokhaiotl (delve 1)", 558, true),
			summary("doom of mokhaiotl (delve 1) (npc 14708)", 558, true),
			summary("doom of mokhaiotl (delve 10)", 1008, true),
			summary("doom of mokhaiotl (delve 2)", 608, true),
			summary("goblin", 2, false));
		List<MonsterGroup> found = data.searchMonsterGroups("mokh", 10, false);
		assertEquals(1, found.size());
		MonsterGroup doom = found.get(0);
		assertEquals("Doom Of Mokhaiotl", doom.getDisplayName());
		assertEquals("doom of mokhaiotl (delve 1)", doom.getPrimary().getName());
		assertEquals(Arrays.asList("Delve 1", "Delve 1, NPC 14708", "Delve 2", "Delve 10"),
			doom.getVariants().stream().map(doom::versionLabel).collect(Collectors.toList()));
		assertEquals(558, doom.getMinCombatLevel());
		assertEquals(1008, doom.getMaxCombatLevel());
		MonsterGroup goblin = data.searchMonsterGroups("goblin", 10, false).get(0);
		assertEquals("Standard", goblin.versionLabel(goblin.getPrimary()));
	}

	@Test
	public void bossFilterKeepsOnlyBossVariants()
	{
		MonsterSummary deadman = summary("yama (deadman) (permanent)", 524, false);
		MonsterSummary normal = summary("yama (normal)", 1238, true);
		MonsterSummary spirit = summary("tree spirit (level 14)", 14, false);
		MonsterSummary lostCity = summary("tree spirit (lost city)", 101, true);
		GameData data = data(deadman, normal, spirit, lostCity, summary("goblin", 2, false));

		MonsterGroup yama = data.searchMonsterGroups("yama", 10, false).get(0);
		assertEquals(2, yama.getVariants().size());
		// The boss version is preselected even when a non-boss default is listed first.
		assertEquals(normal, yama.getPrimary());

		assertTrue(data.searchMonsterGroups("goblin", 10, true).isEmpty());
		MonsterGroup bossSpirit = data.searchMonsterGroups("tree spirit", 10, true).get(0);
		assertEquals(Collections.singletonList(lostCity), bossSpirit.getVariants());
		assertEquals(2, data.searchMonsterGroups("tree spirit", 10, false).get(0).getVariants().size());

		// A right-clicked non-boss keeps its full group even while searching bosses only.
		assertEquals(2, data.groupOf(spirit, true).getVariants().size());
		assertEquals(1, data.groupOf(lostCity, true).getVariants().size());
		MonsterSummary unknown = summary("test target", 1, false);
		assertEquals(Collections.singletonList(unknown), data.groupOf(unknown, false).getVariants());
	}

	@Test
	public void searchRanksExactThenPrefixThenSubstringAndMatchesVariantNames()
	{
		GameData data = data(summary("black demon (level 172)", 172, true), summary("demon", 1, false),
			summary("demonic gorilla", 275, false), summary("lesser demon (level 82)", 82, false));
		List<String> names = data.searchMonsterGroups("demon", 10, false).stream().map(MonsterGroup::getName)
			.collect(Collectors.toList());
		assertEquals(Arrays.asList("demon", "demonic gorilla", "black demon", "lesser demon"), names);
		assertEquals("lesser demon", data.searchMonsterGroups("lesser demon (level 82)", 10, false).get(0).getName());
	}

	@Test
	public void bundledBossesCollapseIntoOneEntryEach() throws Exception
	{
		GameData data = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
		List<MonsterGroup> sire = data.searchMonsterGroups("abyssal sire", 10, true);
		assertEquals(1, sire.size());
		assertEquals(4, sire.get(0).getVariants().size());
		assertEquals("Phase 1", sire.get(0).versionLabel(sire.get(0).getPrimary()));
		for (MonsterGroup group : data.searchMonsterGroups("a", Integer.MAX_VALUE, true))
		{
			assertTrue(group.getName(), group.getVariants().stream().allMatch(MonsterSummary::isBoss));
		}
		assertFalse(data.searchMonsterGroups("guard", 10, false).isEmpty());
		assertTrue(data.searchMonsterGroups("hill giant", 10, true).isEmpty());
	}
}
