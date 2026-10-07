package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SupportSpell;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import com.bestgearsetup.data.WikiGameData;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class SpellRunesTest
{
	@Test
	public void everyBundledSpellHasItsRunes() throws Exception
	{
		Gson gson = new Gson();
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

	@Test
	public void bankLayoutWrapsSpellItemsOntoTheSecondRow()
	{
		Loadout loadout = loadout("dark demonbane", "magic staff", null);
		Map<Integer, Integer> cells = BestGearSetupBankView.cellsFor(new SetupResult(CombatClass.MAGIC, loadout, null,
			0, 0, Collections.emptySet(), EnumSet.of(SupportSpell.MARK_OF_DARKNESS, SupportSpell.THRALL)));
		assertEquals(Integer.valueOf(7), cells.get(ItemID.BLOODRUNE));
		assertEquals(Integer.valueOf(12), cells.get(ItemID.BOOK_OF_THE_DEAD));
		// Aether replaces soul and cosmic; it sits with the first of those (soul).
		assertEquals(Integer.valueOf(5), cells.get(ItemID.AETHERRUNE));
		Map<Integer, Integer> thrall = BestGearSetupBankView.cellsFor(new SetupResult(CombatClass.MELEE,
			loadout(null, "abyssal whip", null), null, 0, 0, Collections.emptySet(), EnumSet.of(SupportSpell.THRALL)));
		assertEquals(thrall.get(ItemID.COSMICRUNE), thrall.get(ItemID.AETHERRUNE));
	}

	@Test
	public void bankLayoutPlacesRunesBesideTheHeadSlotAfterEquipment()
	{
		Loadout loadout = loadout("fire surge", "staff of earth", null);
		loadout.set(Slot.HEAD, gear(ItemID.WRATHRUNE, "test helm"));
		Map<Integer, Integer> cells = BestGearSetupBankView.cellsFor(
			new SetupResult(CombatClass.MAGIC, loadout, null, 0));
		// Equipment keeps its silhouette cell; runes follow on the top row in spellbook order.
		assertEquals(Integer.valueOf(1), cells.get(ItemID.WRATHRUNE));
		assertEquals(Integer.valueOf(4), cells.get(ItemID.AIRRUNE));
		// A combination rune sits with the first rune it replaces.
		assertEquals(Integer.valueOf(4), cells.get(ItemID.SMOKERUNE));
		assertEquals(Integer.valueOf(5), cells.get(ItemID.FIRERUNE));
		assertEquals(Integer.valueOf(5), cells.get(ItemID.SUNFIRERUNE));
		assertEquals(Integer.valueOf(5), cells.get(ItemID.LAVARUNE));
		assertFalse(cells.containsKey(ItemID.EARTHRUNE));
	}

	@Test
	public void bankLayoutPlacesBoostsBelowTheSpellRunesWithEveryDose()
	{
		Potion combat = new Potion();
		combat.setId(ItemID._4DOSE2COMBAT);
		Potion heart = new Potion();
		heart.setId(ItemID.SATURATED_HEART);
		Map<Integer, Integer> cells = BestGearSetupBankView.cellsFor(new SetupResult(CombatClass.MELEE,
			loadout(null, "abyssal whip", null), null, 0), Arrays.asList(combat, heart));
		assertEquals(Integer.valueOf(20), cells.get(ItemID._4DOSE2COMBAT));
		assertEquals(Integer.valueOf(20), cells.get(ItemID._2DOSE2COMBAT));
		assertEquals(Integer.valueOf(20), cells.get(ItemID._3DOSEDIVINECOMBAT));
		assertEquals(Integer.valueOf(21), cells.get(ItemID.SATURATED_HEART));
		Potion attack = new Potion();
		attack.setId(ItemID._4DOSE2ATTACK);
		Potion strength = new Potion();
		strength.setId(ItemID._4DOSE2STRENGTH);
		Map<Integer, Integer> split = BestGearSetupBankView.cellsFor(new SetupResult(CombatClass.MELEE,
			loadout(null, "abyssal whip", null), null, 0), Arrays.asList(attack, strength));
		// A super combat covers both and sits with the first; each keeps its own cell.
		assertEquals(Integer.valueOf(20), split.get(ItemID._4DOSE2ATTACK));
		assertEquals(Integer.valueOf(20), split.get(ItemID._3DOSE2COMBAT));
		assertEquals(Integer.valueOf(21), split.get(ItemID._4DOSE2STRENGTH));
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		plugin.setBankHighlightedSetup(new SetupResult(CombatClass.MELEE, loadout(null, "abyssal whip", null), null, 0),
			Collections.singletonList(combat));
		assertTrue(plugin.getBankHighlightIds().containsAll(Arrays.asList(ItemID._1DOSE2COMBAT,
			ItemID._1DOSEDIVINECOMBAT)));
	}

	@Test
	public void bankHighlightsIncludeSpellRunes()
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		plugin.setBankHighlightedSetup(new SetupResult(CombatClass.MAGIC,
			loadout("blood barrage", "kodai wand", null), null, 0));
		assertTrue(plugin.getBankHighlightIds().containsAll(Arrays.asList(ItemID.DEATHRUNE, ItemID.BLOODRUNE,
			ItemID.SOULRUNE, ItemID.AETHERRUNE)));
		plugin.setBankHighlightedSetup(new SetupResult(CombatClass.MELEE, loadout(null, "abyssal whip", null), null,
			0, 0, Collections.emptySet(), EnumSet.of(SupportSpell.THRALL)));
		assertTrue(plugin.getBankHighlightIds().containsAll(Arrays.asList(ItemID.FIRERUNE, ItemID.BLOODRUNE,
			ItemID.COSMICRUNE, ItemID.BOOK_OF_THE_DEAD)));
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
