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
import net.runelite.api.gameval.ItemID;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/** Search-level checks that rune rules never leave a Magic setup relying on a blocked rune. */
public class OptimizerRuneFilterTest
{
	private static final String ALL_RUNES = "air,water,earth,fire,mind,chaos,death,blood,soul,wrath,cosmic";
	private static GameData data;
	private static WikiMonsters monsters;
	private static Monster graardor;

	@BeforeClass
	public static void loadSnapshot() throws Exception
	{
		data = WikiGameData.get(RuneLiteAPI.GSON).gameData(RuneLiteAPI.GSON);
		monsters = WikiMonsters.get(RuneLiteAPI.GSON);
		graardor = monster("general graardor");
	}

	private static Monster monster(String name)
	{
		Monster m = monsters.monster(name);
		assertNotNull(name, m);
		return m;
	}

	private static OptimizerSettings.OptimizerSettingsBuilder settings()
	{
		return OptimizerSettings.builder().mode(SearchMode.UNLIMITED).depth(SearchDepth.NORMAL)
			.resultsPerClass(40).styles(EnumSet.of(AttackStyle.Type.MAGIC))
			.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient", "arceuus")));
	}

	private static CombatContext context(Monster target, boolean thralls)
	{
		return new CombatContext(target, PlayerLevels.maxed(), false, 99, 99, 99, 99, TestData.piety(), thralls);
	}

	private static List<SetupResult> search(CombatContext ctx, CombatClass combatClass,
		OptimizerSettings settings)
	{
		return new Optimizer(data, ctx, settings, id -> false, item -> 1_000_000L).optimize(combatClass, () -> false);
	}

	private static List<SetupResult> magic(CombatContext ctx)
	{
		return search(ctx, CombatClass.MAGIC, settings().build());
	}

	private static boolean hasSpell(List<SetupResult> results)
	{
		return results.stream().anyMatch(r -> r.getLoadout().getSpell() != null);
	}

	private static boolean isSurge(Spell spell)
	{
		return spell != null && spell.getLowerName().endsWith(" surge");
	}

	private static void assertAllAllowed(List<SetupResult> results, RuneFilter filter, CombatContext ctx)
	{
		for (SetupResult result : results)
		{
			assertTrue(describe(result, filter), RuneRules.allows(result.getLoadout(), filter));
			Spell spell = result.getLoadout().getSpell();
			if (spell != null)
			{
				for (int rune : SpellRunes.perCast(spell.getName()).keySet())
				{
					assertTrue(describe(result, filter),
						filter.permits(rune) || SpellRunes.supplied(result.getLoadout()).contains(rune));
				}
			}
			if (result.getSupportSpells().contains(SupportSpell.THRALL))
			{
				assertEquals(SupportSpell.thrallFor(result.getLoadout(), ctx), result.getThrall());
			}
		}
	}

	private static String describe(SetupResult result, RuneFilter filter)
	{
		Spell spell = result.getLoadout().getSpell();
		return (spell == null ? "no spell" : spell.getName()) + " on " + result.getLoadout().getWeapon().getName()
			+ " with " + filter.getBlocked() + "/" + filter.getOnly();
	}

	private static void assertSameResults(List<SetupResult> expected, List<SetupResult> actual)
	{
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++)
		{
			SetupResult a = expected.get(i);
			SetupResult b = actual.get(i);
			assertEquals(a.getLoadout(), b.getLoadout());
			assertEquals(a.getLoadout().getWeapon().getName(), b.getLoadout().getWeapon().getName());
			assertEquals(spellName(a), spellName(b));
			assertEquals(a.getDps().getDps(), b.getDps().getDps(), 1e-9);
			assertEquals(a.getSupportSpells(), b.getSupportSpells());
			assertEquals(a.getThrall(), b.getThrall());
		}
	}

	private static String spellName(SetupResult result)
	{
		Spell spell = result.getLoadout().getSpell();
		return spell == null ? null : spell.getLowerName();
	}

	@Test
	public void blockingWrathRemovesEverySurge()
	{
		CombatContext open = context(graardor, false);
		assertTrue("unfiltered search should use a surge", magic(open).stream().anyMatch(r -> isSurge(r.getLoadout().getSpell())));
		CombatContext ctx = open.withRuneFilter(RuneFilter.parse("wrath", ""));
		List<SetupResult> results = magic(ctx);
		assertFalse(results.isEmpty());
		assertTrue(hasSpell(results));
		for (SetupResult result : results)
		{
			assertFalse(describe(result, ctx.getRuneFilter()), isSurge(result.getLoadout().getSpell()));
			if (result.getSpecial() != null)
			{
				assertFalse(isSurge(result.getSpecial().getSpell()));
			}
		}
		assertAllAllowed(results, ctx.getRuneFilter(), ctx);
	}

	@Test
	public void everyResultSatisfiesTheRuneRules()
	{
		List<SetupResult> unfiltered = magic(context(graardor, false));
		assertTrue(hasSpell(unfiltered));
		RuneFilter[] filters = {RuneFilter.parse("death", ""), RuneFilter.parse("blood", ""),
			RuneFilter.parse("fire", ""), RuneFilter.parse("water,earth", ""), RuneFilter.parse("", "air,mind,chaos"),
			RuneFilter.parse("chaos", "air,mind,chaos,death")};
		for (RuneFilter filter : filters)
		{
			CombatContext ctx = context(graardor, false).withRuneFilter(filter);
			List<SetupResult> results = magic(ctx);
			assertFalse(filter.getBlocked() + "/" + filter.getOnly(), results.isEmpty());
			assertAllAllowed(results, filter, ctx);
		}
	}

	@Test
	public void onlyAirMindChaosStillFindsSpellsThatFitIt()
	{
		RuneFilter filter = RuneFilter.parse("", "air,mind,chaos");
		CombatContext ctx = context(graardor, false).withRuneFilter(filter);
		List<SetupResult> results = magic(ctx);
		assertTrue(hasSpell(results));
		for (SetupResult result : results)
		{
			Spell spell = result.getLoadout().getSpell();
			if (spell != null)
			{
				assertTrue(describe(result, filter), RuneRules.disallowed(SpellRunes.perCast(spell.getName()),
					SpellRunes.supplied(result.getLoadout()), filter).isEmpty());
			}
		}
	}

	@Test
	public void forcedFireSurgeWithFireBlockedNeedsAFireSupplier()
	{
		SpellFilter forced = SpellFilter.of(Collections.emptySet(), Collections.singleton("fire surge"));
		OptimizerSettings settings = settings().spellFilter(forced).build();
		CombatContext open = context(graardor, false);
		List<SetupResult> unfiltered = search(open, CombatClass.MAGIC, settings);
		assertTrue("forced fire surge should be searchable",
			unfiltered.stream().anyMatch(r -> r.getLoadout().getSpell() != null));
		RuneFilter noFire = RuneFilter.parse("fire", "");
		CombatContext ctx = open.withRuneFilter(noFire);
		List<SetupResult> results = search(ctx, CombatClass.MAGIC, settings);
		for (SetupResult result : results)
		{
			if (result.getLoadout().getSpell() != null)
			{
				assertEquals("fire surge", result.getLoadout().getSpell().getLowerName());
				assertTrue(describe(result, noFire), SpellRunes.supplied(result.getLoadout()).contains(ItemID.FIRERUNE));
			}
		}
		assertAllAllowed(results, noFire, ctx);
	}

	@Test
	public void emptyFilterChangesNothing()
	{
		for (String name : new String[]{"general graardor", "k'ril tsutsaroth"})
		{
			Monster target = monster(name);
			for (boolean thralls : new boolean[]{false, true})
			{
				List<SetupResult> defaults = magic(context(target, thralls));
				assertTrue(name, hasSpell(defaults));
				List<SetupResult> explicit = magic(context(target, thralls).withRuneFilter(RuneFilter.NONE));
				assertSameResults(defaults, explicit);
				List<SetupResult> parsed = magic(context(target, thralls).withRuneFilter(RuneFilter.parse("", "")));
				assertSameResults(defaults, parsed);
			}
		}
	}

	@Test
	public void blockingEveryRuneLeavesMeleeAndRangedUnchanged()
	{
		RuneFilter everything = RuneFilter.parse(ALL_RUNES, "");
		assertEquals(11, everything.getBlocked().size());
		OptimizerSettings melee = settings().styles(EnumSet.of(AttackStyle.Type.STAB, AttackStyle.Type.SLASH,
			AttackStyle.Type.CRUSH)).build();
		OptimizerSettings ranged = settings().styles(EnumSet.of(AttackStyle.Type.RANGED)).build();
		// Thralls are off: a thrall needs runes, so its DPS is the one thing rune rules may change here.
		CombatContext open = context(graardor, false);
		CombatContext closed = open.withRuneFilter(everything);
		List<SetupResult> meleeOpen = search(open, CombatClass.MELEE, melee);
		List<SetupResult> rangedOpen = search(open, CombatClass.RANGED, ranged);
		assertFalse(meleeOpen.isEmpty());
		assertFalse(rangedOpen.isEmpty());
		assertSameResults(meleeOpen, search(closed, CombatClass.MELEE, melee));
		assertSameResults(rangedOpen, search(closed, CombatClass.RANGED, ranged));
	}

	@Test
	public void blockingEveryRuneLeavesNoAutocastSpell()
	{
		assertTrue(hasSpell(magic(context(graardor, false))));
		RuneFilter everything = RuneFilter.parse(ALL_RUNES, "");
		CombatContext ctx = context(graardor, false).withRuneFilter(everything);
		List<SetupResult> results = magic(ctx);
		for (SetupResult result : results)
		{
			assertEquals(describe(result, everything), null, result.getLoadout().getSpell());
			assertTrue(RuneRules.allows(result.getLoadout(), everything));
		}
	}

	@Test
	public void blockedBloodGivesEveryThrallSetupASuperiorThrall()
	{
		RuneFilter noBlood = RuneFilter.parse("blood", "");
		CombatContext open = context(graardor, true);
		boolean greaterSeen = false;
		for (CombatClass combatClass : new CombatClass[]{CombatClass.MELEE, CombatClass.MAGIC})
		{
			OptimizerSettings settings = combatClass == CombatClass.MAGIC ? settings().build()
				: settings().styles(EnumSet.of(AttackStyle.Type.STAB, AttackStyle.Type.SLASH,
					AttackStyle.Type.CRUSH)).build();
			for (SetupResult result : search(open, combatClass, settings))
			{
				greaterSeen |= result.getThrall() == Thrall.GREATER;
			}
			CombatContext ctx = open.withRuneFilter(noBlood);
			boolean thrallSeen = false;
			List<SetupResult> results = search(ctx, combatClass, settings);
			assertFalse(results.isEmpty());
			for (SetupResult result : results)
			{
				if (result.getSupportSpells().contains(SupportSpell.THRALL))
				{
					thrallSeen = true;
					assertEquals(SupportSpell.thrallFor(result.getLoadout(), ctx), result.getThrall());
					assertEquals(Thrall.SUPERIOR, result.getThrall());
				}
				else
				{
					assertEquals(null, result.getThrall());
				}
			}
			assertTrue(combatClass + " results should assume a thrall", thrallSeen);
			assertAllAllowed(results, noBlood, ctx);
		}
		assertTrue("unfiltered search should assume Greater thralls", greaterSeen);
	}
}
