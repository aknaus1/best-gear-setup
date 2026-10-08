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

	private static WorldPoint region(int region, int plane)
	{
		return new WorldPoint((region >> 8) * 64 + 20, (region & 255) * 64 + 20, plane);
	}

	@Test public void konarWyrmsMatchKaruulmAcrossFloorsAndRejectOutsideTargets()
	{
		AccountFacts.TaskRecord task = new AccountFacts.TaskRecord("Wyrms", 35, true, "the Karuulm Slayer Dungeon");
		for (int id : new int[]{5280, 5279, 5023, 5535, 5022, 4766, 4510, 4511, 4767, 4768, 4512})
		{
			for (int plane : new int[]{0, 1, 2})
			{
				assertEquals(true, AccountFacts.matches(task, "Wyrm (Attacking)", region(id, plane)));
				assertEquals(true, AccountFacts.matches(task, "Shadow wyrm", region(id, plane)));
				assertEquals(false, AccountFacts.matches(task, "Drake", region(id, plane)));
			}
		}
		assertEquals(false, AccountFacts.matches(task, "Wyrm", region(5179, 0))); // Mount Karuulm surface
		assertEquals(false, AccountFacts.matches(task, "Wyrm", new WorldPoint(3200, 3200, 0)));
		assertNull(AccountFacts.matches(task, "Wyrm", null));
	}

	@Test public void bootsUseTheVisibleTargetOrMatchingAssignmentForPlanning()
	{
		AccountFacts.TaskRecord task = new AccountFacts.TaskRecord("Wyrms", 35, true, "Karuulm Slayer Dungeon");
		WorldPoint inside = region(5279, 0);
		WorldPoint outside = new WorldPoint(3200, 3200, 0);
		assertEquals(true, AccountFacts.karuulmSearch(task, "Wyrm (Attacking)", inside, outside));
		assertEquals(false, AccountFacts.karuulmSearch(task, "Wyrm (Attacking)", outside, inside));
		assertEquals(true, AccountFacts.karuulmSearch(task, "Wyrm (Attacking)", null, outside));
		assertEquals(true, AccountFacts.karuulmSearch(task, "Shadow wyrm", null, null));
		assertEquals(false, AccountFacts.karuulmSearch(task, "Dust devil", null, outside));
		assertEquals(false, AccountFacts.karuulmSearch(new AccountFacts.TaskRecord("Wyrms", 0, true,
			"Karuulm Slayer Dungeon"), "Wyrm", null, outside));
		assertEquals(true, AccountFacts.karuulmSearch(null, "Wyrm", inside, outside));
		assertEquals(true, AccountFacts.karuulmSearch(null, "Wyrm", null, inside));
		assertEquals(false, AccountFacts.karuulmSearch(null, "Wyrm", null, outside));
	}
}
