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
		assertEquals("Spells: none of the allowed spells (Ice Barrage, Fire Bolt) can be cast here; "
			+ "check your Magic level, spellbooks and the target.",
			BestGearSetupPlugin.spellFilterNote(forced, Collections.emptyList()));
	}

	@Test
	public void staffBoundSpellsExplainTheMissingStaff()
	{
		Spell iban = new Spell();
		iban.setName("iban blast");
		Spell bolt = new Spell();
		bolt.setName("fire bolt");
		SpellFilter forced = SpellFilter.of(Collections.emptySet(), Collections.singleton("iban blast"));
		assertEquals("Spells: Iban Blast can only be cast with the right staff wielded, and none is available to this search.",
			BestGearSetupPlugin.staffBoundNote(forced, Collections.singletonList(iban), Collections.emptyList()));
		assertNull(BestGearSetupPlugin.staffBoundNote(forced, Arrays.asList(iban, bolt), Collections.emptyList()));
		assertNull(BestGearSetupPlugin.staffBoundNote(SpellFilter.NONE, Collections.singletonList(iban), Collections.emptyList()));
	}

	@Test
	public void blockedSpellsExplainWhyNoneCanBeAutocast()
	{
		SpellFilter blocked = SpellFilter.of(Collections.singleton("fire bolt"), Collections.emptySet());
		assertEquals("Spells: every spell you could cast here is excluded.",
			BestGearSetupPlugin.spellFilterNote(blocked, Collections.emptyList()));
	}
}
