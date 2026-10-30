package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.Spell;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class SpellFilterTest
{
	@Test
	public void blankAndNullInputsEqualNone()
	{
		assertEquals(SpellFilter.NONE, SpellFilter.parse(null, null));
		assertEquals(SpellFilter.NONE, SpellFilter.parse("", ""));
		assertEquals(SpellFilter.NONE, SpellFilter.parse(" , ,", null));
		assertTrue(SpellFilter.NONE.isEmpty());
	}

	@Test
	public void namesAreTrimmedLowerCasedAndDeduplicated()
	{
		SpellFilter filter = SpellFilter.parse(" Fire Bolt ,fire bolt,ICE BARRAGE", "");
		assertEquals(Arrays.asList("fire bolt", "ice barrage"), Arrays.asList(filter.getBlocked().toArray()));
		assertTrue(filter.getForced().isEmpty());
		assertFalse(filter.isEmpty());
	}

	@Test
	public void blockedBeatsForced()
	{
		SpellFilter filter = SpellFilter.of(Collections.singletonList("fire bolt"),
			Arrays.asList("fire bolt", "ice barrage"));
		assertEquals(Collections.singletonList("fire bolt"), Arrays.asList(filter.getBlocked().toArray()));
		assertEquals(Collections.singletonList("ice barrage"), Arrays.asList(filter.getForced().toArray()));
	}

	@Test
	public void allowsFollowsBlockAndForce()
	{
		Spell fire = spell("Fire Bolt");
		Spell ice = spell("Ice Barrage");
		Spell wind = spell("Wind Strike");
		assertTrue(SpellFilter.NONE.allows(fire));
		assertTrue(SpellFilter.NONE.allows(ice));
		assertTrue(SpellFilter.NONE.allows(wind));

		SpellFilter blocked = SpellFilter.of(Collections.singleton("fire bolt"), Collections.emptySet());
		assertFalse(blocked.allows(fire));
		assertTrue(blocked.allows(ice));
		assertTrue(blocked.allows(wind));

		SpellFilter forced = SpellFilter.of(Collections.emptySet(), Collections.singleton("ice barrage"));
		assertFalse(forced.allows(fire));
		assertTrue(forced.allows(ice));
		assertFalse(forced.allows(wind));

		SpellFilter both = SpellFilter.of(Collections.singleton("wind strike"),
			Arrays.asList("ice barrage", "wind strike"));
		assertFalse(both.allows(fire));
		assertTrue(both.allows(ice));
		assertFalse(both.allows(wind));
	}

	@Test
	public void nullSpellIsAlwaysAllowed()
	{
		SpellFilter filter = SpellFilter.of(Collections.singleton("fire bolt"), Collections.singleton("ice barrage"));
		assertTrue(filter.allows(null));
	}

	@Test
	public void unknownForcedNameStillRestricts()
	{
		SpellFilter filter = SpellFilter.of(Collections.emptySet(), Collections.singleton("not a spell"));
		assertEquals(Collections.singleton("not a spell"), filter.getForced());
		assertFalse(filter.allows(spell("Fire Bolt")));
	}

	@Test
	public void withRuleMovesBetweenLists()
	{
		SpellFilter original = SpellFilter.NONE;
		SpellFilter blocked = original.withRule("Fire Bolt", SpellRule.BLOCK);
		assertEquals(Collections.singleton("fire bolt"), blocked.getBlocked());
		assertTrue(blocked.getForced().isEmpty());
		assertEquals(SpellRule.BLOCK, blocked.ruleFor(" FIRE BOLT "));

		SpellFilter forced = blocked.withRule("Fire Bolt", SpellRule.FORCE);
		assertTrue(forced.getBlocked().isEmpty());
		assertEquals(Collections.singleton("fire bolt"), forced.getForced());
		assertEquals(SpellRule.FORCE, forced.ruleFor("fire bolt"));

		SpellFilter allowed = forced.withRule(" Fire Bolt ", SpellRule.ALLOW);
		assertEquals(SpellFilter.NONE, allowed);
		assertEquals(SpellRule.ALLOW, allowed.ruleFor("fire bolt"));
		assertTrue(original.isEmpty());
		assertEquals(Collections.singleton("fire bolt"), blocked.getBlocked());
		assertEquals(Collections.singleton("fire bolt"), forced.getForced());
		assertSame(blocked, blocked.withRule(" ", SpellRule.FORCE));
	}

	@Test
	public void formatRoundTrips()
	{
		SpellFilter filter = SpellFilter.of(Arrays.asList("Fire Bolt", "Wind Strike"),
			Arrays.asList("Ice Barrage", "Blood Burst"));
		assertEquals(filter, SpellFilter.parse(SpellFilter.format(filter.getBlocked()),
			SpellFilter.format(filter.getForced())));
		assertEquals("fire bolt,wind strike", SpellFilter.format(filter.getBlocked()));
		assertEquals("", SpellFilter.format(Collections.emptyList()));
	}

	@Test(expected = UnsupportedOperationException.class)
	public void getSetsAreUnmodifiable()
	{
		SpellFilter.of(Collections.singleton("fire bolt"), Collections.emptySet()).getBlocked().add("x");
	}

	private static Spell spell(String name)
	{
		Spell spell = new Spell();
		spell.setName(name);
		return spell;
	}
}
