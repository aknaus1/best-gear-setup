package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.bestgearsetup.calc.SpellFilter;
import com.bestgearsetup.data.Spell;
import java.util.Collections;
import org.junit.Test;

public class SpellFilterMagicTabNoteTest
{
	private static final String MISSING_MAGIC =
		"Spells: no Magic setup is left; your spell rules may block every spell your weapons can autocast here.";

	@Test
	public void missingMagicTabExplainsSpellRulesWhenCandidatesRemain()
	{
		SpellFilter blocked = SpellFilter.of(Collections.singleton("fire bolt"), Collections.emptySet());
		assertEquals(MISSING_MAGIC, BestGearSetupPlugin.spellFilterNote(blocked,
			Collections.singletonList(new Spell()), false));
	}

	@Test
	public void noNoteForAnEmptyFilterEvenWithoutMagicResults()
	{
		assertNull(BestGearSetupPlugin.spellFilterNote(SpellFilter.NONE,
			Collections.singletonList(new Spell()), false));
	}

	@Test
	public void noNoteWhenMagicResultsExist()
	{
		SpellFilter forced = SpellFilter.of(Collections.emptySet(), Collections.singleton("fire bolt"));
		assertNull(BestGearSetupPlugin.spellFilterNote(forced,
			Collections.singletonList(new Spell()), true));
	}

	@Test
	public void missingCandidatesKeepTheSpecificForcedSpellMessage()
	{
		SpellFilter forced = SpellFilter.of(Collections.emptySet(), Collections.singleton("fire bolt"));
		assertEquals("Spells: none of the forced spells (Fire Bolt) can be autocast here; "
			+ "check your Magic level, spellbooks and the target.",
			BestGearSetupPlugin.spellFilterNote(forced, Collections.emptyList(), false));
	}
}
