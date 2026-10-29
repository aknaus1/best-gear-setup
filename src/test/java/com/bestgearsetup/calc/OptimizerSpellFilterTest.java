package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Spell;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/** Search-level checks for blocked and forced autocast spells. */
public class OptimizerSpellFilterTest
{
	private static GameData data;
	private static Monster monster;

	@BeforeClass
	public static void loadSnapshot() throws Exception
	{
		data = WikiGameData.get(RuneLiteAPI.GSON).gameData(RuneLiteAPI.GSON);
		monster = WikiMonsters.get(RuneLiteAPI.GSON).monster("general graardor");
		assertNotNull(monster);
	}

	private static OptimizerSettings.OptimizerSettingsBuilder settings()
	{
		return OptimizerSettings.builder().mode(SearchMode.UNLIMITED).depth(SearchDepth.NORMAL)
			.resultsPerClass(40).styles(EnumSet.of(AttackStyle.Type.MAGIC))
			.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient", "arceuus")));
	}

	private static List<SetupResult> search(OptimizerSettings settings)
	{
		CombatContext ctx = new CombatContext(monster, PlayerLevels.maxed(), false, false, TestData.piety());
		return new Optimizer(data, ctx, settings, id -> false, item -> 1_000_000L)
			.optimize(CombatClass.MAGIC, () -> false);
	}

	private static String spellName(Spell spell)
	{
		return spell == null ? null : spell.getLowerName();
	}

	private static Spell firstSpell(List<SetupResult> results)
	{
		return results.stream().map(r -> r.getLoadout().getSpell()).filter(s -> s != null).findFirst().orElse(null);
	}

	private static Set<String> spelllessWeapons(List<SetupResult> results)
	{
		return results.stream().filter(r -> r.getLoadout().getSpell() == null)
			.map(r -> r.getLoadout().getWeapon().getName()).collect(Collectors.toSet());
	}

	@Test
	public void blockingTheBestSpellFallsBackToAnotherSpell()
	{
		Spell best = firstSpell(search(settings().build()));
		assertNotNull(best);
		String blocked = best.getLowerName();
		List<SetupResult> results = search(settings().spellFilter(
			SpellFilter.of(Collections.singleton(blocked), Collections.emptySet())).build());
		assertNotNull(firstSpell(results));
		for (SetupResult result : results)
		{
			assertFalse(blocked.equals(spellName(result.getLoadout().getSpell())));
			if (result.getSpecial() != null)
			{
				assertFalse(blocked.equals(spellName(result.getSpecial().getSpell())));
			}
		}
	}

	@Test
	public void forcingASpellUsesOnlyThatSpell()
	{
		List<SetupResult> unfiltered = search(settings().build());
		List<SetupResult> forced = search(settings().spellFilter(
			SpellFilter.of(Collections.emptySet(), Collections.singleton("fire bolt"))).build());
		assertNotNull(firstSpell(forced));
		for (SetupResult result : forced)
		{
			if (result.getLoadout().getSpell() != null)
			{
				assertEquals("fire bolt", result.getLoadout().getSpell().getLowerName());
			}
		}
		assertTrue(spelllessWeapons(forced).containsAll(spelllessWeapons(unfiltered)));
	}

	@Test
	public void forcedDemonbaneAgainstANonDemonLeavesNoAutocast()
	{
		OptimizerSettings filtered = settings().spellFilter(
			SpellFilter.of(Collections.emptySet(), Collections.singleton("dark demonbane"))).build();
		List<SetupResult> results = search(filtered);
		assertFalse(results.isEmpty());
		assertTrue(results.stream().allMatch(r -> r.getLoadout().getSpell() == null));
		assertTrue(Optimizer.autocastSpells(data, 99, monster, filtered).isEmpty());
	}

	@Test
	public void blockingEverySpellLeavesNoAutocast()
	{
		OptimizerSettings filtered = settings().spellFilter(SpellFilter.of(
			data.getSpells().stream().map(Spell::getName).collect(Collectors.toList()), Collections.emptySet())).build();
		assertTrue(search(filtered).stream().allMatch(r -> r.getLoadout().getSpell() == null));
		assertTrue(Optimizer.autocastSpells(data, 99, monster, filtered).isEmpty());
	}

	@Test
	public void bothListsBlockedWins()
	{
		Spell best = firstSpell(search(settings().build()));
		assertNotNull(best);
		SpellFilter filter = SpellFilter.of(Collections.singleton(best.getName()), Collections.singleton(best.getName()));
		assertTrue(filter.getForced().isEmpty());
		List<SetupResult> results = search(settings().spellFilter(filter).build());
		assertNotNull(firstSpell(results));
		assertTrue(results.stream().allMatch(r -> !best.getLowerName().equals(spellName(r.getLoadout().getSpell()))));
		assertTrue(Optimizer.autocastSpells(data, 99, monster, settings().spellFilter(filter).build())
			.stream().noneMatch(s -> s.getLowerName().equals(best.getLowerName())));
	}

	@Test
	public void noFilterChangesNothing()
	{
		List<SetupResult> defaults = search(settings().build());
		List<SetupResult> explicit = search(settings().spellFilter(SpellFilter.NONE).build());
		assertEquals(defaults.size(), explicit.size());
		for (int i = 0; i < defaults.size(); i++)
		{
			Loadout a = defaults.get(i).getLoadout();
			Loadout b = explicit.get(i).getLoadout();
			assertEquals(a.getWeapon().getId(), b.getWeapon().getId());
			assertEquals(a.getStyle(), b.getStyle());
			assertEquals(spellName(a.getSpell()), spellName(b.getSpell()));
			assertEquals(defaults.get(i).getDps().getDps(), explicit.get(i).getDps().getDps(), 1e-12);
		}
	}

	@Test
	public void autocastSpellsMatchesTheGates()
	{
		Monster target = TestData.monster(100, 0);
		OptimizerSettings unfiltered = settings().spellFilter(SpellFilter.NONE).build();
		List<Spell> at99 = Optimizer.autocastSpells(data, 99, target, unfiltered);
		assertTrue(at99.stream().noneMatch(s -> s.getLowerName().contains("demonbane")));
		assertTrue(at99.stream().noneMatch(s -> s.getLowerName().equals("crumble undead")));
		assertTrue(at99.stream().anyMatch(s -> s.getLowerName().equals("ice barrage")));
		List<Spell> at1 = Optimizer.autocastSpells(data, 1, target, unfiltered);
		assertFalse(at1.isEmpty());
		assertTrue(at1.stream().allMatch(s -> s.getLevel() <= 1));
	}
}
