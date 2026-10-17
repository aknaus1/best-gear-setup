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
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;
import org.junit.BeforeClass;

public class SpellRunesTest
{
	@BeforeClass public static void ownershipRules() { OwnershipRules.init(RuneLiteAPI.GSON); }

	@Test public void carriedSuppliesNormalizeEverySupportedSubstitute()
	{
		RaidPlan plan = org.mockito.Mockito.mock(RaidPlan.class);
		org.mockito.Mockito.when(plan.getInventory()).thenReturn(Collections.emptyList());
		Map<Integer, Set<Integer>> replacements = Map.ofEntries(
			Map.entry(ItemID.MISTRUNE, Set.of(ItemID.AIRRUNE, ItemID.WATERRUNE)),
			Map.entry(ItemID.DUSTRUNE, Set.of(ItemID.AIRRUNE, ItemID.EARTHRUNE)),
			Map.entry(ItemID.SMOKERUNE, Set.of(ItemID.AIRRUNE, ItemID.FIRERUNE)),
			Map.entry(ItemID.MUDRUNE, Set.of(ItemID.WATERRUNE, ItemID.EARTHRUNE)),
			Map.entry(ItemID.STEAMRUNE, Set.of(ItemID.WATERRUNE, ItemID.FIRERUNE)),
			Map.entry(ItemID.LAVARUNE, Set.of(ItemID.EARTHRUNE, ItemID.FIRERUNE)),
			Map.entry(ItemID.SUNFIRERUNE, Set.of(ItemID.FIRERUNE)),
			Map.entry(ItemID.AETHERRUNE, Set.of(ItemID.SOULRUNE, ItemID.COSMICRUNE)));
		replacements.forEach((substitute, canonical) ->
		{
			assertEquals(canonical, SpellRunes.carriedSupplies(plan, id -> id == substitute));
			assertEquals(canonical, SpellRunes.carriedSupplies(plan, canonical::contains));
		});
		assertEquals(Set.of(ItemID.BOOK_OF_THE_DEAD),
			SpellRunes.carriedSupplies(plan, id -> id == ItemID.BOOK_OF_THE_DEAD));
	}

	@Test public void onlyCarriedPouchesContributeTheirPlannedContents()
	{
		RaidPlan plan = org.mockito.Mockito.mock(RaidPlan.class);
		RaidPlan.InventoryItem runes = new RaidPlan.InventoryItem("Fire runes",
			Arrays.asList(ItemID.FIRERUNE, ItemID.SUNFIRERUNE, ItemID.SMOKERUNE), false);
		RaidPlan.InventoryItem pouch = new RaidPlan.InventoryItem("Rune pouch",
			Arrays.asList(ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER), false, Collections.singletonList(runes));
		org.mockito.Mockito.when(plan.getInventory()).thenReturn(Collections.singletonList(pouch));
		assertTrue(SpellRunes.carriedSupplies(plan, id -> false).isEmpty());
		assertEquals(SpellRunes.carriedSupplies(plan, id -> id == ItemID.BH_RUNE_POUCH),
			SpellRunes.carriedSupplies(plan, id -> id == ItemID.BH_RUNE_POUCH_TROUVER));
		assertEquals("A possible smoke substitute is not evidence that air runes were packed", Set.of(ItemID.FIRERUNE),
			SpellRunes.carriedSupplies(plan, id -> id == ItemID.BH_RUNE_POUCH));
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

	@Test
	public void bankLayoutWrapsSpellItemsOntoTheSecondRow()
	{
		Loadout loadout = loadout("dark demonbane", "magic staff", null);
		Map<Integer, Integer> cells = BestGearSetupBankView.cellsFor(new SetupResult(CombatClass.MAGIC, loadout, null,
			0, 0, Collections.emptySet(), EnumSet.of(SupportSpell.MARK_OF_DARKNESS, SupportSpell.THRALL)));
		assertEquals(Integer.valueOf(12), cells.get(ItemID.BLOODRUNE));
		assertEquals(Integer.valueOf(4), cells.get(ItemID.BOOK_OF_THE_DEAD));
		// Aether replaces soul and cosmic; it sits with the first of those (soul).
		assertEquals(Integer.valueOf(6), cells.get(ItemID.AETHERRUNE));
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
	public void bankLayoutPlacesBoostsInTheInventoryWithEveryDose()
	{
		Potion combat = new Potion();
		combat.setId(ItemID._4DOSE2COMBAT);
		Potion heart = new Potion();
		heart.setId(ItemID.SATURATED_HEART);
		Map<Integer, Integer> cells = BestGearSetupBankView.cellsFor(new SetupResult(CombatClass.MELEE,
			loadout(null, "abyssal whip", null), null, 0), Arrays.asList(combat, heart));
		assertEquals(Integer.valueOf(4), cells.get(ItemID._4DOSE2COMBAT));
		assertEquals(Integer.valueOf(4), cells.get(ItemID._2DOSE2COMBAT));
		assertEquals(Integer.valueOf(4), cells.get(ItemID._3DOSEDIVINECOMBAT));
		assertEquals(Integer.valueOf(5), cells.get(ItemID.SATURATED_HEART));
		Potion attack = new Potion();
		attack.setId(ItemID._4DOSE2ATTACK);
		Potion strength = new Potion();
		strength.setId(ItemID._4DOSE2STRENGTH);
		Map<Integer, Integer> split = BestGearSetupBankView.cellsFor(new SetupResult(CombatClass.MELEE,
			loadout(null, "abyssal whip", null), null, 0), Arrays.asList(attack, strength));
		// A super combat covers both and sits with the first; each keeps its own cell.
		assertEquals(Integer.valueOf(4), split.get(ItemID._4DOSE2ATTACK));
		assertEquals(Integer.valueOf(4), split.get(ItemID._3DOSE2COMBAT));
		assertEquals(Integer.valueOf(5), split.get(ItemID._4DOSE2STRENGTH));
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		plugin.setBankHighlightedSetup(new SetupResult(CombatClass.MELEE, loadout(null, "abyssal whip", null), null, 0),
			Collections.singletonList(combat));
		assertTrue(plugin.getBankHighlightIds().containsAll(Arrays.asList(ItemID._1DOSE2COMBAT,
			ItemID._1DOSEDIVINECOMBAT)));
	}

	@Test
	public void bankLayoutPutsAnOwnedRunePouchBeforeTheRunes()
	{
		SetupResult barrage = new SetupResult(CombatClass.MAGIC, loadout("blood barrage", "kodai wand", null), null, 0);
		Map<Integer, Integer> none = BestGearSetupBankView.cellsFor(barrage, Collections.emptyList(), id -> false);
		assertFalse(none.containsKey(ItemID.BH_RUNE_POUCH));
		assertEquals(Integer.valueOf(4), none.get(ItemID.DEATHRUNE));
		// Keep the bank consistent with the packed inventory: choose the best owned pouch capacity.
		Map<Integer, Integer> cells = BestGearSetupBankView.cellsFor(barrage, Collections.emptyList(),
			id -> id == ItemID.DIVINE_RUNE_POUCH || id == ItemID.BH_RUNE_POUCH_TROUVER);
		assertEquals(Integer.valueOf(4), cells.get(ItemID.DIVINE_RUNE_POUCH));
		assertFalse(cells.containsKey(ItemID.BH_RUNE_POUCH_TROUVER));
		assertFalse(cells.containsKey(ItemID.BH_RUNE_POUCH));
		// Packed runes load from the rows below the inventory, as in a raid kit.
		assertEquals(Integer.valueOf(56), cells.get(ItemID.DEATHRUNE));
		// No runes needed, no pouch.
		assertFalse(BestGearSetupBankView.cellsFor(new SetupResult(CombatClass.MAGIC,
			loadout(null, "trident of the swamp", null), null, 0), Collections.emptyList(), id -> true)
			.containsKey(ItemID.DIVINE_RUNE_POUCH));
	}

	@Test
	public void bankHighlightsIncludeAnOwnedRunePouchWhenRunesAreNeeded()
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		plugin.config = new BestGearSetupConfig() {};
		plugin.ownedItems = new OwnedItems(null, null);
		plugin.ownedItems.bank = Collections.singletonMap(ItemID.BH_RUNE_POUCH, 1L);
		plugin.ownedItems.rebuild(true);
		plugin.setBankHighlightedSetup(new SetupResult(CombatClass.MAGIC,
			loadout("ice barrage", "kodai wand", null), null, 0));
		assertTrue(plugin.getBankHighlightIds().contains(ItemID.BH_RUNE_POUCH));
		assertFalse(plugin.getBankHighlightIds().contains(ItemID.DIVINE_RUNE_POUCH));
		plugin.setBankHighlightedSetup(new SetupResult(CombatClass.MELEE, loadout(null, "abyssal whip", null), null,
			0));
		assertFalse(plugin.getBankHighlightIds().contains(ItemID.BH_RUNE_POUCH));
	}

	@Test public void excludedDivinePouchFallsBackToOwnedRegularPouch()
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		plugin.config = new BestGearSetupConfig() { @Override public String excluded() { return Integer.toString(ItemID.DIVINE_RUNE_POUCH); } };
		plugin.ownedItems = new OwnedItems(null, null);
		java.util.Map<Integer, Long> bank = new java.util.HashMap<>();
		bank.put(ItemID.DIVINE_RUNE_POUCH, 1L); bank.put(ItemID.BH_RUNE_POUCH, 1L);
		plugin.ownedItems.bank = bank; plugin.ownedItems.rebuild(true);
		plugin.setBankHighlightedSetup(new SetupResult(CombatClass.MAGIC, loadout("ice barrage", "kodai wand", null), null, 0));
		assertFalse(plugin.getBankHighlightIds().contains(ItemID.DIVINE_RUNE_POUCH));
		assertTrue(plugin.getBankHighlightIds().contains(ItemID.BH_RUNE_POUCH));
	}

	@Test
	public void bankHighlightsIncludeSpellRunes()
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		plugin.ownedItems = new OwnedItems(null, null);
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
