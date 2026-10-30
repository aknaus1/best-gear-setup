package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** Value semantics of the rune allow and block rules. */
public class RuneFilterTest
{
	private static final int[] ALL_RUNES = {ItemID.AIRRUNE, ItemID.WATERRUNE, ItemID.EARTHRUNE, ItemID.FIRERUNE,
		ItemID.MINDRUNE, ItemID.CHAOSRUNE, ItemID.DEATHRUNE, ItemID.BLOODRUNE, ItemID.SOULRUNE, ItemID.WRATHRUNE,
		ItemID.COSMICRUNE};

	@Test
	public void parseNormalisesBlockedNamesAndKeepsOrder()
	{
		RuneFilter filter = RuneFilter.parse(" Blood,death,blood ,", "");
		assertEquals(Arrays.asList("blood", "death"), new ArrayList<>(filter.getBlocked()));
		assertTrue(filter.getOnly().isEmpty());
		assertFalse(filter.isEmpty());
	}

	@Test
	public void blockedWinsOverTheOnlyList()
	{
		RuneFilter filter = RuneFilter.parse("blood", "blood,air");
		assertEquals(Collections.singletonList("air"), new ArrayList<>(filter.getOnly()));
		assertEquals(Collections.singletonList("blood"), new ArrayList<>(filter.getBlocked()));
		RuneFilter viaOf = RuneFilter.of(Collections.singletonList("blood"), Arrays.asList("blood", "air"));
		assertEquals(filter, viaOf);
	}

	@Test
	public void onlyListPermitsOnlyItsRunes()
	{
		RuneFilter filter = RuneFilter.parse("", "air,mind");
		assertTrue(filter.permits(ItemID.AIRRUNE));
		assertTrue(filter.permits(ItemID.MINDRUNE));
		assertFalse(filter.permits(ItemID.CHAOSRUNE));
		assertFalse(filter.permits(ItemID.WRATHRUNE));
	}

	@Test
	public void blockedRuneIsNotPermittedAndOthersAre()
	{
		RuneFilter filter = RuneFilter.parse("blood", "");
		assertFalse(filter.permits(ItemID.BLOODRUNE));
		for (int rune : ALL_RUNES)
		{
			assertEquals(rune != ItemID.BLOODRUNE, filter.permits(rune));
		}
	}

	@Test
	public void emptyRulesPermitEveryRune()
	{
		for (RuneFilter filter : new RuneFilter[]{RuneFilter.NONE, RuneFilter.parse("", ""),
			RuneFilter.parse(null, null), RuneFilter.of(Collections.emptyList(), Collections.emptyList())})
		{
			assertTrue(filter.isEmpty());
			for (int rune : ALL_RUNES)
			{
				assertTrue(filter.permits(rune));
			}
		}
		assertTrue(RuneFilter.NONE.isEmpty());
		assertTrue(RuneFilter.NONE.getBlocked().isEmpty());
		assertTrue(RuneFilter.NONE.getOnly().isEmpty());
	}

	@Test
	public void unknownRuneIdsAreOnlyPermittedWithoutAnOnlyList()
	{
		int combination = ItemID.LAVARUNE;
		assertTrue(RuneFilter.parse("blood", "").permits(combination));
		assertFalse(RuneFilter.parse("", "air").permits(combination));
	}

	@Test
	public void unknownNamesAreKeptButMatchNothing()
	{
		RuneFilter filter = RuneFilter.parse("bogus", "");
		assertEquals(Collections.singletonList("bogus"), new ArrayList<>(filter.getBlocked()));
		for (int rune : ALL_RUNES)
		{
			assertTrue(filter.permits(rune));
		}
	}

	@Test
	public void formatRoundTrips()
	{
		RuneFilter filter = RuneFilter.parse("Blood,death", "air, mind");
		assertEquals("blood,death", RuneFilter.format(filter.getBlocked()));
		assertEquals("air,mind", RuneFilter.format(filter.getOnly()));
		assertEquals(filter, RuneFilter.parse(RuneFilter.format(filter.getBlocked()), RuneFilter.format(filter.getOnly())));
		assertEquals("", RuneFilter.format(Collections.emptyList()));
		assertEquals(RuneFilter.NONE, RuneFilter.parse("", ""));
	}

	@Test
	public void withRuleAllowRemovesARule()
	{
		RuneFilter filter = RuneFilter.parse("blood,death", "");
		assertEquals(Collections.singletonList("death"),
			new ArrayList<>(filter.withRule("BLOOD", SpellRule.ALLOW).getBlocked()));
		RuneFilter only = RuneFilter.parse("", "air,mind");
		assertEquals(Collections.singletonList("mind"),
			new ArrayList<>(only.withRule("Air", SpellRule.ALLOW).getOnly()));
	}

	@Test
	public void withRuleReturnsTheSameInstanceWhenNothingChanges()
	{
		RuneFilter filter = RuneFilter.parse("blood", "air");
		assertSame(filter, filter.withRule("blood", SpellRule.BLOCK));
		assertSame(filter, filter.withRule("AIR", SpellRule.FORCE));
		assertSame(filter, filter.withRule("fire", SpellRule.ALLOW));
		assertSame(RuneFilter.NONE, RuneFilter.NONE.withRule("fire", SpellRule.ALLOW));
	}

	@Test
	public void emptiedRulesBecomeNone()
	{
		assertSame(RuneFilter.NONE, RuneFilter.parse("blood", "").withRule("blood", SpellRule.ALLOW));
		assertSame(RuneFilter.NONE, RuneFilter.parse("", "air").withRule("air", SpellRule.ALLOW));
	}

	@Test
	public void withRuleMovesARuneBetweenTheLists()
	{
		RuneFilter blocked = RuneFilter.NONE.withRule("blood", SpellRule.BLOCK);
		assertEquals(SpellRule.BLOCK, blocked.ruleFor("blood"));
		RuneFilter only = blocked.withRule("blood", SpellRule.FORCE);
		assertEquals(SpellRule.FORCE, only.ruleFor("blood"));
		assertTrue(only.getBlocked().isEmpty());
		RuneFilter back = only.withRule("blood", SpellRule.BLOCK);
		assertEquals(blocked, back);
		assertTrue(back.getOnly().isEmpty());
	}

	@Test
	public void ruleForReportsBlockForceAndAllow()
	{
		RuneFilter filter = RuneFilter.parse("blood", "air");
		assertEquals(SpellRule.BLOCK, filter.ruleFor("blood"));
		assertEquals(SpellRule.BLOCK, filter.ruleFor("BLOOD"));
		assertEquals(SpellRule.FORCE, filter.ruleFor("air"));
		assertEquals(SpellRule.ALLOW, filter.ruleFor("fire"));
		assertEquals(SpellRule.ALLOW, RuneFilter.NONE.ruleFor("blood"));
	}

	@Test
	public void equalityIsByValue()
	{
		RuneFilter a = RuneFilter.parse("blood,death", "air");
		RuneFilter b = RuneFilter.parse(" DEATH ,blood", "Air");
		assertEquals(RuneFilter.parse("blood,death", "air"), a);
		assertEquals(a, RuneFilter.of(Arrays.asList("blood", "death"), Collections.singletonList("air")));
		assertEquals(a.hashCode(), RuneFilter.parse("blood,death", "air").hashCode());
		assertNotEquals(a, RuneFilter.parse("blood", "air"));
		assertNotEquals(a, RuneFilter.parse("blood,death", "mind"));
		assertNotEquals(a, RuneFilter.NONE);
		// Same sets in another order are still the same rules.
		assertEquals(b.getBlocked(), a.getBlocked());
	}
}
