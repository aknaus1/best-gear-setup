package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

public class ThrallTest
{
	@Test
	public void ghostlyThrallIsAssumedUnlessTheTargetIsImmuneToMagic()
	{
		assertEquals(Thrall.Type.GHOSTLY, Thrall.eligibleType(monster("Goblin")));
		assertEquals(Thrall.Type.GHOSTLY, Thrall.eligibleType(monster("Kraken", "melee immune")));
		assertEquals(Thrall.Type.GHOSTLY, Thrall.eligibleType(monster("Tekton")));
		assertEquals(Thrall.Type.GHOSTLY, Thrall.eligibleType(monster("Nylocas Hagios", "magic only")));
		assertEquals(Thrall.Type.SKELETAL, Thrall.eligibleType(monster("Nylocas Toxobolos", "ranged only")));
		assertEquals(Thrall.Type.ZOMBIFIED, Thrall.eligibleType(monster("Nylocas Ischyros", "melee only")));
		assertNull("No thrall's style can damage it", Thrall.eligibleType(monster("Test", "melee only", "melee immune")));
	}

	@Test
	public void immuneTargetsAndRestrictedFightsGetNoThrall()
	{
		Monster immune = monster("Vorkath");
		immune.setImmuneThrall(true);
		assertNull(Thrall.eligibleType(immune));
		assertTrue(Thrall.immune(immune));
		for (String name : new String[]{"Galvek (fire)", "Robert the Strong", "Fragment of Seren",
			"Elvarg (Nightmare Zone) (hard mode)"})
		{
			Monster restricted = monster(name);
			assertNull(name, Thrall.eligibleType(restricted));
			assertTrue(name, Thrall.immune(restricted));
			assertEquals(name, EnumSet.noneOf(SupportSpell.class), SupportSpell.assumed(new Loadout(), context(restricted)));
		}
		assertFalse(Thrall.immune(monster("Elvarg")));
	}

	@Test
	public void eligibleThrallAddsItsDamageAndAnIneligibleOneAddsNone()
	{
		Monster rangedOnly = monster("Nylocas Toxobolos", "ranged only");
		assertEquals(EnumSet.of(SupportSpell.THRALL), SupportSpell.assumed(new Loadout(), context(rangedOnly)));
		Monster none = monster("Test", "melee only", "melee immune");
		assertEquals(EnumSet.noneOf(SupportSpell.class), SupportSpell.assumed(new Loadout(), context(none)));
	}

	@Test
	public void typedNamesKeepTheTier()
	{
		assertEquals("Greater ghostly thrall", Thrall.GREATER.displayName(Thrall.Type.GHOSTLY));
		assertEquals("Lesser zombified thrall", Thrall.LESSER.displayName(Thrall.Type.ZOMBIFIED));
		assertEquals("Superior thrall", Thrall.SUPERIOR.displayName(null));
	}

	@Test
	public void bundledDataMarksTheWikiImmuneBosses() throws Exception
	{
		Gson gson = RuneLiteAPI.GSON;
		GameData data = WikiGameData.get(gson).gameData(gson);
		WikiMonsters monsters = WikiMonsters.get(gson);
		// Vorkath and the Abyssal Sire are immune per their infoboxes; the Kraken boss is not.
		assertTrue(Thrall.immune(monsters.monster(data.matchNpc(8059, null, 0).getName())));
		assertTrue(Thrall.immune(monsters.monster(data.matchNpc(5886, null, 0).getName())));
		Monster kraken = monsters.monster(data.matchNpc(494, null, 0).getName());
		assertFalse(Thrall.immune(kraken));
		assertEquals(Thrall.Type.GHOSTLY, Thrall.eligibleType(kraken));
		// Galvek isn't marked in the data; the fight's restriction is.
		assertTrue(Thrall.immune(monsters.monster(data.matchNpc(8094, null, 0).getName())));
	}

	private static CombatContext context(Monster target)
	{
		PlayerLevels levels = new PlayerLevels(99, 99, 99, 99, 99, 99, 99, 99);
		return new CombatContext(target, levels, false, 99, 99, 99, 99, null, true);
	}

	private static Monster monster(String name, String... attributes)
	{
		Monster m = new Monster();
		m.setName(name);
		List<Monster.Attribute> list = new ArrayList<>();
		for (String a : attributes)
		{
			Monster.Attribute attribute = new Monster.Attribute();
			attribute.setName(a);
			list.add(attribute);
		}
		m.setAttributes(list);
		return m;
	}
}
