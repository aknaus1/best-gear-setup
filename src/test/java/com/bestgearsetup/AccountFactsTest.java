package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class AccountFactsTest
{
	private static Boolean match(String task, String npc)
	{ return AccountFacts.matches(new AccountFacts.TaskRecord(task, 35, false), npc); }

	@Test public void taskMatchesAliasesAndVariantsInsteadOfAllTargets()
	{
		assertEquals(true, match("Blue dragons", "Vorkath"));
		assertEquals(false, match("Blue dragons", "Turoth"));
		assertEquals(true, match("Turoths", "Turoth (Child)"));
		assertEquals(true, match("Aviansies", "Kree'arra"));
		assertEquals(true, match("Black demons", "Demonic gorilla"));
		assertEquals(true, match("Dagannoth Kings", "Dagannoth Rex"));
		assertEquals(true, match("The Alchemical Hydra", "Alchemical Hydra"));
	}

	@Test public void missingLocationUnknownAssignmentAndFinishedTaskDoNotGrantBonus()
	{
		assertNull(match("Unrecognised new assignment", "Turoth"));
		assertNull(AccountFacts.matches(null, "Turoth"));
		assertNull(AccountFacts.matches(new AccountFacts.TaskRecord("Blue dragons", 8, true), "Vorkath"));
		assertEquals(false, AccountFacts.matches(new AccountFacts.TaskRecord("Blue dragons", 0, false), "Vorkath"));
		assertEquals(false, match("Turoths", "Notaturoth"));
	}

	@Test public void restrictedTurothTaskUsesTheAssignedDungeonAndRejectsOtherLocations()
	{
		AccountFacts.TaskRecord task = new AccountFacts.TaskRecord("Turoth", 5, true, "Fremennik Slayer Dungeon");
		for (int region : new int[]{10907, 10908, 11164})
		{
			WorldPoint inside = new WorldPoint((region >> 8) * 64 + 20, (region & 255) * 64 + 20, 0);
			assertEquals(true, AccountFacts.matches(task, "Turoth (Mum)", inside));
			assertEquals(false, AccountFacts.matches(task, "Turoth (Mum)", new WorldPoint(inside.getX(), inside.getY(), 1)));
			assertEquals(false, AccountFacts.matches(task, "Vorkath", inside));
		}
		assertEquals(false, AccountFacts.matches(task, "Turoth", new WorldPoint(3200, 3200, 0)));
		assertNull(AccountFacts.matches(task, "Turoth", null));
		assertNull(AccountFacts.matches(new AccountFacts.TaskRecord("Turoth", 5, true, "Unmapped dungeon"), "Turoth", new WorldPoint(2800, 9998, 0)));
	}
}
