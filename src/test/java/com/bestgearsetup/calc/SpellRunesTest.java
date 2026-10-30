package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import com.bestgearsetup.data.WikiGameData;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

public class SpellRunesTest
{
	@Test
	public void costPerCastMatchesTheWiki()
	{
		assertCost("wind strike", ItemID.AIRRUNE, 1, ItemID.MINDRUNE, 1);
		assertCost("smoke rush", ItemID.AIRRUNE, 1, ItemID.FIRERUNE, 1, ItemID.CHAOSRUNE, 2, ItemID.DEATHRUNE, 2);
		assertCost("water strike", ItemID.AIRRUNE, 1, ItemID.WATERRUNE, 1, ItemID.MINDRUNE, 1);
		assertCost("shadow rush", ItemID.AIRRUNE, 1, ItemID.CHAOSRUNE, 2, ItemID.DEATHRUNE, 2, ItemID.SOULRUNE, 1);
		assertCost("earth strike", ItemID.AIRRUNE, 1, ItemID.EARTHRUNE, 2, ItemID.MINDRUNE, 1);
		assertCost("blood rush", ItemID.CHAOSRUNE, 2, ItemID.DEATHRUNE, 2, ItemID.BLOODRUNE, 1);
		assertCost("fire strike", ItemID.AIRRUNE, 2, ItemID.FIRERUNE, 3, ItemID.MINDRUNE, 1);
		assertCost("ice rush", ItemID.WATERRUNE, 2, ItemID.CHAOSRUNE, 2, ItemID.DEATHRUNE, 2);
		assertCost("wind bolt", ItemID.AIRRUNE, 2, ItemID.CHAOSRUNE, 1);
		assertCost("smoke burst", ItemID.AIRRUNE, 2, ItemID.FIRERUNE, 2, ItemID.CHAOSRUNE, 4, ItemID.DEATHRUNE, 2);
		assertCost("water bolt", ItemID.AIRRUNE, 2, ItemID.WATERRUNE, 2, ItemID.CHAOSRUNE, 1);
		assertCost("shadow burst", ItemID.AIRRUNE, 1, ItemID.CHAOSRUNE, 4, ItemID.DEATHRUNE, 2, ItemID.SOULRUNE, 2);
		assertCost("earth bolt", ItemID.AIRRUNE, 2, ItemID.EARTHRUNE, 3, ItemID.CHAOSRUNE, 1);
		assertCost("blood burst", ItemID.CHAOSRUNE, 4, ItemID.DEATHRUNE, 2, ItemID.BLOODRUNE, 2);
		assertCost("fire bolt", ItemID.AIRRUNE, 3, ItemID.FIRERUNE, 4, ItemID.CHAOSRUNE, 1);
		assertCost("ice burst", ItemID.WATERRUNE, 4, ItemID.CHAOSRUNE, 4, ItemID.DEATHRUNE, 2);
		assertCost("wind blast", ItemID.AIRRUNE, 3, ItemID.DEATHRUNE, 1);
		assertCost("smoke blitz", ItemID.AIRRUNE, 2, ItemID.FIRERUNE, 2, ItemID.DEATHRUNE, 2, ItemID.BLOODRUNE, 2);
		assertCost("water blast", ItemID.AIRRUNE, 3, ItemID.WATERRUNE, 3, ItemID.DEATHRUNE, 1);
		assertCost("shadow blitz", ItemID.AIRRUNE, 2, ItemID.DEATHRUNE, 2, ItemID.BLOODRUNE, 2, ItemID.SOULRUNE, 2);
		assertCost("earth blast", ItemID.AIRRUNE, 3, ItemID.EARTHRUNE, 4, ItemID.DEATHRUNE, 1);
		assertCost("blood blitz", ItemID.DEATHRUNE, 2, ItemID.BLOODRUNE, 4);
		assertCost("fire blast", ItemID.AIRRUNE, 4, ItemID.FIRERUNE, 5, ItemID.DEATHRUNE, 1);
		assertCost("ice blitz", ItemID.WATERRUNE, 3, ItemID.DEATHRUNE, 2, ItemID.BLOODRUNE, 2);
		assertCost("wind wave", ItemID.AIRRUNE, 5, ItemID.BLOODRUNE, 1);
		assertCost("smoke barrage", ItemID.AIRRUNE, 4, ItemID.FIRERUNE, 4, ItemID.DEATHRUNE, 4, ItemID.BLOODRUNE, 2);
		assertCost("water wave", ItemID.AIRRUNE, 5, ItemID.WATERRUNE, 7, ItemID.BLOODRUNE, 1);
		assertCost("shadow barrage", ItemID.AIRRUNE, 4, ItemID.DEATHRUNE, 4, ItemID.BLOODRUNE, 2, ItemID.SOULRUNE, 3);
		assertCost("earth wave", ItemID.AIRRUNE, 5, ItemID.EARTHRUNE, 7, ItemID.BLOODRUNE, 1);
		assertCost("blood barrage", ItemID.DEATHRUNE, 4, ItemID.BLOODRUNE, 4, ItemID.SOULRUNE, 1);
		assertCost("fire wave", ItemID.AIRRUNE, 5, ItemID.FIRERUNE, 7, ItemID.BLOODRUNE, 1);
		assertCost("ice barrage", ItemID.WATERRUNE, 6, ItemID.DEATHRUNE, 4, ItemID.BLOODRUNE, 2);
		assertCost("wind surge", ItemID.AIRRUNE, 7, ItemID.WRATHRUNE, 1);
		assertCost("ghostly grasp", ItemID.AIRRUNE, 4, ItemID.CHAOSRUNE, 1);
		assertCost("water surge", ItemID.AIRRUNE, 7, ItemID.WATERRUNE, 10, ItemID.WRATHRUNE, 1);
		assertCost("skeletal grasp", ItemID.EARTHRUNE, 8, ItemID.DEATHRUNE, 1);
		assertCost("earth surge", ItemID.AIRRUNE, 7, ItemID.EARTHRUNE, 10, ItemID.WRATHRUNE, 1);
		assertCost("undead grasp", ItemID.FIRERUNE, 12, ItemID.BLOODRUNE, 1);
		assertCost("fire surge", ItemID.AIRRUNE, 7, ItemID.FIRERUNE, 10, ItemID.WRATHRUNE, 1);
		assertCost("inferior demonbane", ItemID.FIRERUNE, 3, ItemID.CHAOSRUNE, 1);
		assertCost("crumble undead", ItemID.AIRRUNE, 2, ItemID.EARTHRUNE, 2, ItemID.CHAOSRUNE, 1);
		assertCost("superior demonbane", ItemID.FIRERUNE, 5, ItemID.SOULRUNE, 1);
		assertCost("iban blast", ItemID.FIRERUNE, 5, ItemID.DEATHRUNE, 1);
		assertCost("dark demonbane", ItemID.FIRERUNE, 7, ItemID.SOULRUNE, 2);
		assertCost("magic dart", ItemID.MINDRUNE, 4, ItemID.DEATHRUNE, 1);
		assertCost("saradomin strike", ItemID.AIRRUNE, 4, ItemID.FIRERUNE, 2, ItemID.BLOODRUNE, 2);
		assertCost("claws of guthix", ItemID.AIRRUNE, 4, ItemID.FIRERUNE, 1, ItemID.BLOODRUNE, 2);
		assertCost("flames of zamorak", ItemID.AIRRUNE, 1, ItemID.FIRERUNE, 4, ItemID.BLOODRUNE, 2);
	}

	private static void assertCost(String name, int... pairs)
	{
		Map<Integer, Integer> expected = cost(pairs);
		Map<Integer, Integer> actual = SpellRunes.perCast(name);
		assertEquals(name, expected, actual);
		assertEquals(name + " rune order", new ArrayList<>(expected.entrySet()), new ArrayList<>(actual.entrySet()));
	}

	private static Map<Integer, Integer> cost(int... pairs)
	{
		Map<Integer, Integer> result = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			result.put(pairs[i], pairs[i + 1]);
		}
		return result;
	}

	@Test
	public void everyBundledCombatSpellHasACost() throws Exception
	{
		Gson gson = RuneLiteAPI.GSON;
		for (Spell spell : WikiGameData.get(gson).gameData(gson).getSpells())
		{
			if (spell.getMaxHit() > 0)
			{
				assertFalse(spell.getName(), SpellRunes.perCast(spell.getName()).isEmpty());
			}
		}
	}

	@Test
	public void supportSpellCostsMatchTheWiki()
	{
		assertEquals(cost(ItemID.COSMICRUNE, 1, ItemID.SOULRUNE, 1),
			SpellRunes.supportPerCast(SupportSpell.MARK_OF_DARKNESS, null));
		assertEquals(cost(ItemID.AIRRUNE, 3, ItemID.FIRERUNE, 3, ItemID.BLOODRUNE, 3),
			SpellRunes.supportPerCast(SupportSpell.CHARGE, null));
		assertEquals(cost(ItemID.AIRRUNE, 10, ItemID.MINDRUNE, 5, ItemID.COSMICRUNE, 1),
			SpellRunes.supportPerCast(SupportSpell.THRALL, Thrall.LESSER));
		assertEquals(cost(ItemID.EARTHRUNE, 10, ItemID.DEATHRUNE, 5, ItemID.COSMICRUNE, 1),
			SpellRunes.supportPerCast(SupportSpell.THRALL, Thrall.SUPERIOR));
		Map<Integer, Integer> greater = cost(ItemID.FIRERUNE, 10, ItemID.BLOODRUNE, 5, ItemID.COSMICRUNE, 1);
		assertEquals(greater, SpellRunes.supportPerCast(SupportSpell.THRALL, Thrall.GREATER));
		assertEquals(greater, SpellRunes.supportPerCast(SupportSpell.THRALL, null));
	}

	@Test
	public void supportDurations()
	{
		assertEquals(178.2, SpellRunes.supportDurationSeconds(SupportSpell.MARK_OF_DARKNESS,
			loadout(null, "magic staff", null), 99, 99), 1e-9);
		assertEquals(891.0, SpellRunes.supportDurationSeconds(SupportSpell.MARK_OF_DARKNESS,
			loadout(null, "purging staff", null), 99, 99), 1e-9);
		assertEquals(420.0, SpellRunes.supportDurationSeconds(SupportSpell.CHARGE,
			loadout(null, "magic staff", null), 99, 99), 1e-9);
		assertEquals(59.4, SpellRunes.supportDurationSeconds(SupportSpell.THRALL,
			loadout(null, "magic staff", null), 99, 99), 1e-9);
		assertEquals(30.0, SpellRunes.supportDurationSeconds(SupportSpell.THRALL,
			loadout(null, "magic staff", null), 50, 50), 1e-9);
	}

	@Test
	public void runeSavingWeapons()
	{
		assertEquals(0.15, SpellRunes.runeSaveChance(loadout(null, "kodai wand", null)), 1e-9);
		for (String name : Arrays.asList("staff of the dead", "toxic staff of the dead", "staff of light",
			"staff of balance"))
		{
			assertEquals(name, 1.0 / 7, SpellRunes.runeSaveChance(loadout(null, name, null)), 1e-9);
		}
		assertEquals(0.0, SpellRunes.runeSaveChance(loadout(null, "toxic staff (uncharged)", null)), 1e-9);
		assertEquals(0.0, SpellRunes.runeSaveChance(loadout(null, "ancient staff", null)), 1e-9);
		assertEquals(0.0, SpellRunes.runeSaveChance(null), 1e-9);
	}

	@Test
	public void runeSavingNamesExistInTheGameData() throws Exception
	{
		Gson gson = RuneLiteAPI.GSON;
		List<GearItem> weapons = WikiGameData.get(gson).gameData(gson).getItems(Slot.WEAPON);
		for (String name : Arrays.asList("kodai wand", "staff of the dead", "toxic staff of the dead",
			"staff of light", "staff of balance"))
		{
			assertTrue(name, weapons.stream().anyMatch(item -> name.equals(item.getName().toLowerCase(Locale.ROOT))));
		}
	}

	@Test
	public void thrallRunesFollowTheTier()
	{
		Loadout melee = loadout(null, "abyssal whip", null);
		Set<SupportSpell> thrall = EnumSet.of(SupportSpell.THRALL);
		assertEquals(Arrays.asList(ItemID.AIRRUNE, ItemID.MINDRUNE, ItemID.COSMICRUNE, ItemID.BOOK_OF_THE_DEAD),
			SpellRunes.required(melee, thrall, Thrall.LESSER));
		assertEquals(Arrays.asList(ItemID.EARTHRUNE, ItemID.DEATHRUNE, ItemID.COSMICRUNE, ItemID.BOOK_OF_THE_DEAD),
			SpellRunes.required(melee, thrall, Thrall.SUPERIOR));
		List<Integer> greater = Arrays.asList(ItemID.FIRERUNE, ItemID.BLOODRUNE, ItemID.COSMICRUNE,
			ItemID.BOOK_OF_THE_DEAD);
		assertEquals(greater, SpellRunes.required(melee, thrall, Thrall.GREATER));
		assertEquals(greater, SpellRunes.required(melee, thrall, null));
		SetupResult setup = new SetupResult(CombatClass.MELEE, melee, null, 0, 0, Collections.emptySet(),
			thrall, null, Thrall.LESSER);
		assertEquals(SpellRunes.required(melee, thrall, Thrall.LESSER), SpellRunes.required(setup));
	}

	@Test
	public void runeNames()
	{
		assertEquals("death", SpellRunes.runeName(ItemID.DEATHRUNE));
		assertEquals("cosmic", SpellRunes.runeName(ItemID.COSMICRUNE));
		assertEquals("rune", SpellRunes.runeName(-1));
	}

	@Test
	public void everyBundledSpellHasItsRunes() throws Exception
	{
		Gson gson = RuneLiteAPI.GSON;
		for (Spell spell : WikiGameData.get(gson).gameData(gson).getSpells())
		{
			Loadout loadout = new Loadout();
			loadout.setSpell(spell);
			assertFalse(spell.getName(), required(loadout).isEmpty());
		}
	}

	@Test
	public void runesTheStaffOrTomeSuppliesAreLeftOut()
	{
		assertEquals(Arrays.asList(ItemID.AIRRUNE, ItemID.FIRERUNE, ItemID.WRATHRUNE),
			required(loadout("fire surge", "kodai wand", null)));
		assertEquals(Collections.singletonList(ItemID.WRATHRUNE),
			required(loadout("fire surge", "smoke battlestaff", null)));
		assertEquals(Collections.singletonList(ItemID.WRATHRUNE),
			required(loadout("fire surge", "staff of air", "tome of fire")));
		assertEquals(Arrays.asList(ItemID.FIRERUNE, ItemID.WRATHRUNE),
			required(loadout("fire surge", "mystic air staff", "tome of fire (empty)")));
		assertEquals(Arrays.asList(ItemID.DEATHRUNE, ItemID.BLOODRUNE),
			required(loadout("ice barrage", "kodai wand", null)));
		assertEquals(Arrays.asList(ItemID.WATERRUNE, ItemID.DEATHRUNE, ItemID.BLOODRUNE),
			required(loadout("ice barrage", "smoke ancient sceptre", null)));
		assertTrue(required(loadout(null, "trident of the swamp", null)).isEmpty());
		assertTrue(SpellRunes.substitutes(ItemID.FIRERUNE).contains(ItemID.SUNFIRERUNE));
	}

	@Test
	public void supportSpellsAddTheirRunesOnceAndThrallsAddTheBook()
	{
		// Dark Demonbane (fire, soul) + Mark of Darkness (cosmic, soul) + thrall (fire, blood, cosmic, book).
		assertEquals(Arrays.asList(ItemID.FIRERUNE, ItemID.SOULRUNE, ItemID.COSMICRUNE, ItemID.BLOODRUNE,
			ItemID.BOOK_OF_THE_DEAD), required(loadout("dark demonbane", "magic staff", null),
			EnumSet.of(SupportSpell.MARK_OF_DARKNESS, SupportSpell.THRALL)));
		// A melee setup with a thrall still brings the thrall's runes; a fire staff covers its fire.
		assertEquals(Arrays.asList(ItemID.BLOODRUNE, ItemID.COSMICRUNE, ItemID.BOOK_OF_THE_DEAD),
			required(loadout(null, "staff of fire", null), EnumSet.of(SupportSpell.THRALL)));
		assertEquals(Arrays.asList(ItemID.AIRRUNE, ItemID.FIRERUNE, ItemID.BLOODRUNE),
			required(loadout("flames of zamorak", "zamorak staff", null), EnumSet.of(SupportSpell.CHARGE)));
	}

	private static List<Integer> required(Loadout loadout)
	{
		return required(loadout, EnumSet.noneOf(SupportSpell.class));
	}

	private static List<Integer> required(Loadout loadout, Set<SupportSpell> support)
	{
		return SpellRunes.required(loadout, support);
	}

	private static Loadout loadout(String spell, String weapon, String shield)
	{
		Loadout loadout = new Loadout();
		if (spell != null)
		{
			Spell s = new Spell();
			s.setName(spell);
			loadout.setSpell(s);
		}
		loadout.set(Slot.WEAPON, gear(1, weapon));
		if (shield != null)
		{
			loadout.set(Slot.SHIELD, gear(2, shield));
		}
		return loadout;
	}

	private static GearItem gear(int id, String name)
	{
		GearItem item = new GearItem();
		item.setId(id);
		item.setName(name);
		return item;
	}
}
