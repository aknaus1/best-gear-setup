package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.bestgearsetup.calc.SpellFilter;
import com.bestgearsetup.data.Spell;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class SpellFilterNoteTest
{
	@Test
	public void noNoteForEmptyFilter()
	{
		assertNull(BestGearSetupPlugin.spellFilterNote(SpellFilter.NONE, Collections.emptyList()));
	}

	@Test
	public void noNoteWhenForcedSpellIsAvailable()
	{
		SpellFilter forced = SpellFilter.of(Collections.emptySet(), Collections.singleton("fire bolt"));
		assertNull(BestGearSetupPlugin.spellFilterNote(forced, Collections.singletonList(new Spell())));
	}

	@Test
	public void forcedSpellsExplainWhyNoneCanBeAutocast()
	{
		SpellFilter forced = SpellFilter.of(Collections.emptySet(), Arrays.asList("ice barrage", "fire bolt"));
		assertEquals("Spells: none of the forced spells (Ice Barrage, Fire Bolt) can be autocast here; "
			+ "check your Magic level, spellbooks and the target.",
			BestGearSetupPlugin.spellFilterNote(forced, Collections.emptyList()));
	}

	@Test
	public void blockedSpellsExplainWhyNoneCanBeAutocast()
	{
		SpellFilter blocked = SpellFilter.of(Collections.singleton("fire bolt"), Collections.emptySet());
		assertEquals("Spells: every spell you could autocast here is blocked.",
			BestGearSetupPlugin.spellFilterNote(blocked, Collections.emptyList()));
	}
}
