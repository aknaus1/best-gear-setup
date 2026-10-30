package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.RuneFilter;
import com.bestgearsetup.calc.Thrall;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class RuneRulesNoteTest
{
	private static RuneFilter blocked(String... names)
	{
		return RuneFilter.of(Arrays.asList(names), Collections.emptyList());
	}

	private static List<String> notes(RuneFilter filter, Thrall thrall, boolean charge, boolean mark)
	{
		return BestGearSetupPlugin.runeSupportNotes(filter, thrall, charge, mark);
	}

	@Test
	public void noWarningForNoneOrWhenMagicHasResults()
	{
		assertNull(BestGearSetupPlugin.runeFilterNote(RuneFilter.NONE, false));
		assertNull(BestGearSetupPlugin.runeFilterNote(blocked("blood"), true));
	}

	@Test
	public void warningListsBlockedRunesInRuneOrder()
	{
		assertEquals("Runes: no Magic setup is left; your rune rules may block every spell you could autocast here "
			+ "(blocked: Death, Blood).", BestGearSetupPlugin.runeFilterNote(blocked("blood", "death"), false));
	}

	@Test
	public void warningListsOnlyRunes()
	{
		assertEquals("Runes: no Magic setup is left; your rune rules may block every spell you could autocast here "
			+ "(only: Air, Mind).", BestGearSetupPlugin.runeFilterNote(
			RuneFilter.of(Collections.emptyList(), Arrays.asList("mind", "air")), false));
	}

	@Test
	public void warningListsBothParts()
	{
		assertEquals("Runes: no Magic setup is left; your rune rules may block every spell you could autocast here "
			+ "(blocked: Blood; only: Air, Mind).", BestGearSetupPlugin.runeFilterNote(
			RuneFilter.of(Collections.singletonList("blood"), Arrays.asList("mind", "air")), false));
	}

	@Test
	public void supportNotesEmptyForNone()
	{
		assertTrue(notes(RuneFilter.NONE, Thrall.GREATER, true, true).isEmpty());
	}

	@Test
	public void bloodBlockedDowngradesGreaterThrall()
	{
		assertEquals(Collections.singletonList("Thralls: Superior thralls at most, because your rune rules block "
			+ "blood runes."), notes(blocked("blood"), Thrall.GREATER, false, false));
	}

	@Test
	public void bloodAndDeathBlockedLeavesLesser()
	{
		assertEquals(Collections.singletonList("Thralls: Lesser thralls at most, because your rune rules block "
			+ "death and blood runes."), notes(blocked("blood", "death"), Thrall.GREATER, false, false));
	}

	@Test
	public void everyBlockingRuneIsNamedWhenNoThrallIsLeft()
	{
		assertEquals(Collections.singletonList("Thralls: none added, because your rune rules block mind, death "
			+ "and blood runes."), notes(blocked("blood", "death", "mind"), Thrall.GREATER, false, false));
	}

	@Test
	public void superiorRequestedFallsBackToLesser()
	{
		assertEquals(Collections.singletonList("Thralls: Lesser thralls at most, because your rune rules block "
			+ "death runes."), notes(blocked("death"), Thrall.SUPERIOR, false, false));
	}

	@Test
	public void cosmicBlockedRemovesThralls()
	{
		assertEquals(Collections.singletonList("Thralls: none added, because your rune rules block cosmic runes."),
			notes(blocked("cosmic"), Thrall.GREATER, false, false));
	}

	@Test
	public void elementalRunesCountAsSupplied()
	{
		assertTrue(notes(blocked("fire"), Thrall.GREATER, false, false).isEmpty());
		assertTrue(notes(blocked("air"), null, true, false).isEmpty());
	}

	@Test
	public void chargeNoteNamesBlockedRune()
	{
		assertEquals(Collections.singletonList("Charge: not assumed, because your rune rules block blood runes."),
			notes(blocked("blood"), null, true, false));
	}

	@Test
	public void markOfDarknessNotes()
	{
		assertEquals(Collections.singletonList("Mark of Darkness: not assumed, because your rune rules block "
			+ "soul runes."), notes(blocked("soul"), null, false, true));
		assertEquals(Collections.singletonList("Mark of Darkness: not assumed, because your rune rules block "
			+ "cosmic and soul runes."), notes(blocked("cosmic", "soul"), null, false, true));
	}

	@Test
	public void falseFlagsGiveNoNotes()
	{
		assertTrue(notes(blocked("blood", "soul", "cosmic"), null, false, false).isEmpty());
	}
}
