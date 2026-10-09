package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.PotionChoice;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.WikiGameData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.runelite.api.gameval.ItemID;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

public class BoostAccessTest
{
	private static GameData data;

	@BeforeClass
	public static void loadSnapshot() throws Exception
	{
		data = WikiGameData.get(RuneLiteAPI.GSON).gameData(RuneLiteAPI.GSON);
	}

	private static String best(String skill, SearchMode mode, Integer... owned)
	{
		Set<Integer> expanded = OwnedItems.expand(new HashSet<>(Arrays.asList(owned)));
		Predicate<Potion> boosts = BoostAccess.allowed(mode, expanded, true);
		Potion p = PotionChoice.resolve(data, skill, 99, PotionChoice.BEST, boosts);
		return p == null ? null : p.getName();
	}

	@Test
	public void ownedOnlyUsesTheStrongestOwnedBoost()
	{
		assertNull(best("ranged", SearchMode.OWNED_ONLY));
		// A single dose of a 4-dose potion counts.
		assertEquals("ranging potion", best("ranged", SearchMode.OWNED_ONLY, ItemID._1DOSERANGERSPOTION));
		assertEquals("imbued heart", best("magic", SearchMode.OWNED_ONLY, ItemID.IMBUED_HEART, ItemID._4DOSE1MAGIC));
		assertEquals("saturated heart", best("magic", SearchMode.OWNED_ONLY, ItemID.IMBUED_HEART, ItemID.SATURATED_HEART));
		assertEquals("super attack", best("attack", SearchMode.OWNED_ONLY, ItemID._4DOSE2ATTACK));
		assertNull(best("strength", SearchMode.OWNED_ONLY, ItemID._4DOSE2ATTACK));
	}

	@Test
	public void divinePotionsCountAsTheirOrdinaryVersion()
	{
		assertEquals("super combat potion", best("strength", SearchMode.OWNED_ONLY, ItemID._2DOSEDIVINECOMBAT));
		assertEquals("ranging potion", best("ranged", SearchMode.OWNED_ONLY, ItemID._4DOSEDIVINERANGE));
	}

	@Test
	public void bankIdsCoverEveryDoseAndTheDivineVersion()
	{
		Set<Integer> ids = BoostAccess.itemIds(potion(ItemID._4DOSE2COMBAT));
		assertEquals(Integer.valueOf(ItemID._4DOSE2COMBAT), ids.iterator().next());
		assertTrue(ids.containsAll(Arrays.asList(ItemID._1DOSE2COMBAT, ItemID._4DOSEDIVINECOMBAT,
			ItemID._1DOSEDIVINECOMBAT)));
		assertEquals(Collections.singleton(ItemID.IMBUED_HEART), BoostAccess.itemIds(potion(ItemID.IMBUED_HEART)));
	}

	@Test
	public void bankIdsListEachTypesDosesTogetherHighestFirst()
	{
		assertEquals(Arrays.asList(ItemID._4DOSE2ATTACK, ItemID._3DOSE2ATTACK, ItemID._2DOSE2ATTACK, ItemID._1DOSE2ATTACK,
			ItemID._4DOSEDIVINEATTACK, ItemID._3DOSEDIVINEATTACK, ItemID._2DOSEDIVINEATTACK, ItemID._1DOSEDIVINEATTACK,
			ItemID._4DOSE2COMBAT, ItemID._3DOSE2COMBAT),
			new ArrayList<>(BoostAccess.itemIds(potion(ItemID._4DOSE2ATTACK))).subList(0, 10));
	}

	@Test
	public void combatPotionsStandInForAttackAndStrengthPotionsAndTheReverse()
	{
		assertTrue(BoostAccess.itemIds(potion(ItemID._4DOSE2ATTACK)).containsAll(Arrays.asList(ItemID._2DOSE2COMBAT,
			ItemID._1DOSEDIVINECOMBAT)));
		assertTrue(BoostAccess.itemIds(potion(ItemID._4DOSE2STRENGTH)).contains(ItemID._4DOSE2COMBAT));
		assertTrue(BoostAccess.itemIds(potion(ItemID._4DOSE2COMBAT)).containsAll(Arrays.asList(ItemID._3DOSE2ATTACK,
			ItemID._4DOSEDIVINESTRENGTH)));
		assertTrue(BoostAccess.itemIds(potion(ItemID._4DOSE1ATTACK)).contains(ItemID._4DOSECOMBAT));
		assertTrue(BoostAccess.itemIds(potion(ItemID._4DOSECOMBAT)).contains(ItemID.STRENGTH4));
		// Unrelated boosts stay separate.
		assertFalse(BoostAccess.itemIds(potion(ItemID._4DOSE2ATTACK)).contains(ItemID._4DOSE2STRENGTH));
		assertFalse(BoostAccess.itemIds(potion(ItemID._4DOSE2COMBAT)).contains(ItemID._4DOSECOMBAT));
	}

	private static Potion potion(int id)
	{
		Potion p = new Potion();
		p.setId(id);
		return p;
	}

	@Test
	public void budgetBuysPotionsButNotHearts()
	{
		assertEquals("ranging potion", best("ranged", SearchMode.BUDGET));
		assertEquals("forgotten brew", best("magic", SearchMode.BUDGET));
		assertEquals("saturated heart", best("magic", SearchMode.BUDGET, ItemID.SATURATED_HEART));
		assertEquals("saturated heart", best("magic", SearchMode.UNLIMITED));
	}

	private static Monster raidMonster(int id, String attribute)
	{
		Monster m = new Monster();
		m.setId(id);
		Monster.Attribute a = new Monster.Attribute();
		a.setName(attribute);
		m.setAttributes(Collections.singletonList(a));
		return m;
	}

	@Test
	public void raidSuppliesNeedNotBeOwned()
	{
		Predicate<Potion> none = BoostAccess.allowed(SearchMode.OWNED_ONLY, Collections.emptySet(), true);
		Monster olm = raidMonster(7554, "xerician");
		Monster zebak = raidMonster(11730, "tombs of amascut");
		assertEquals("overload (+)", PotionChoice.resolve(data, "ranged", 99, PotionChoice.BEST, none, olm).getName());
		assertEquals("overload (+)", PotionChoice.resolve(data, "strength", 99, PotionChoice.BEST, none, olm).getName());
		assertEquals("smelling salts", PotionChoice.resolve(data, "magic", 99, PotionChoice.BEST, none, zebak).getName());
		assertEquals("smelling salts", PotionChoice.resolve(data, "attack", 99, PotionChoice.BEST, none, zebak).getName());
		assertNull(PotionChoice.resolve(data, "ranged", 99, PotionChoice.BEST, none, raidMonster(8061, "dragon")));
	}

	@Test
	public void raidSuppliesCanBeLeftOut()
	{
		Monster olm = raidMonster(7554, "xerician");
		Monster zebak = raidMonster(11730, "tombs of amascut");
		for (SearchMode mode : SearchMode.values())
		{
			Predicate<Potion> noRaid = BoostAccess.allowed(mode, Collections.emptySet(), false);
			Potion ranged = PotionChoice.resolve(data, "ranged", 99, PotionChoice.BEST, noRaid, olm);
			Potion magic = PotionChoice.resolve(data, "magic", 99, PotionChoice.BEST, noRaid, zebak);
			assertTrue(mode + " must not assume an overload", ranged == null || !ranged.isRaidSupply());
			assertTrue(mode + " must not assume smelling salts", magic == null || !magic.isRaidSupply());
		}
		// Ordinary potions are still picked when raid supplies are off.
		assertEquals("ranging potion", PotionChoice.resolve(data, "ranged", 99, PotionChoice.BEST,
			BoostAccess.allowed(SearchMode.BUDGET, Collections.emptySet(), false), olm).getName());
		// A raid potion chosen by name is still used.
		assertEquals("overload (+)", PotionChoice.resolve(data, "ranged", 99, "overload (+)",
			BoostAccess.allowed(SearchMode.OWNED_ONLY, Collections.emptySet(), false), olm).getName());
	}

	@Test
	public void aPotionChosenByNameIsAlwaysUsed()
	{
		Predicate<Potion> none = BoostAccess.allowed(SearchMode.OWNED_ONLY, Collections.emptySet(), true);
		assertEquals("super combat potion",
			PotionChoice.resolve(data, "attack", 99, "super combat potion", none).getName());
	}
}
