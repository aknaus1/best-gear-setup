package com.bestgearsetup.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import org.junit.Test;

public class GameDataLoaderTest
{
	@Test
	public void everythingComesFromTheBundledSnapshots() throws Exception
	{
		GameDataLoader loader = new GameDataLoader(new Gson());
		GameData data = loader.loadGameData();
		assertFalse(data.getMonsters().isEmpty());
		assertTrue(data.getItems(Slot.WEAPON).size() > 600);
		assertTrue(data.getSpells().size() > 40);
		assertFalse(data.getPrayers(CombatClass.MAGIC).isEmpty());
		assertFalse(data.getPotions("strength").isEmpty());
		MonsterSummary vorkath = data.matchNpc(8061, "Vorkath", 732);
		assertNotNull(vorkath);
		Monster first = loader.getMonster(vorkath);
		assertEquals(vorkath.getName(), first.getName());
		// Each lookup is a copy, so search-time adjustments never leak into the bundle.
		assertNotSame(first, loader.getMonster(vorkath));
		assertTrue(loader.describeSource().startsWith("Game data: bundled OSRS Wiki snapshots (equipment 20"));
	}
}
