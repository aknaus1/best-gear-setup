package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.Antifire;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.PotionChoice;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.WikiGameData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
		Predicate<Potion> boosts = BoostAccess.allowed(mode, expanded);
		Potion p = PotionChoice.resolve(data, skill, 99, boosts, null);
		return p == null ? null : p.getName();
	}

	@Test
	public void aCombatPotionIsKeptAsItsStrengthBoost()
	{
		Predicate<Potion> boosts = BoostAccess.allowed(SearchMode.OWNED_ONLY,
			OwnedItems.expand(new HashSet<>(Collections.singletonList(ItemID._4DOSECOMBAT))));
		List<Potion> melee = BestGearSetupPlugin.potionsUsed(data, PlayerLevels.builder().attack(99).strength(99).build(),
			null, boosts).get(Combat.MELEE);
		// One bottle boosts both, listed as the Strength boost the atlatl takes.
		assertEquals(1, melee.size());
		assertEquals("strength", melee.get(0).getSkill());
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
	public void inventoryOnlyUsesBoostsLikeOwnedOnly()
	{
		assertNull(best("ranged", SearchMode.INVENTORY_ONLY));
		assertEquals("ranging potion", best("ranged", SearchMode.INVENTORY_ONLY, ItemID._1DOSERANGERSPOTION));
		assertEquals("saturated heart", best("magic", SearchMode.INVENTORY_ONLY, ItemID.IMBUED_HEART, ItemID.SATURATED_HEART));
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
		Predicate<Potion> none = BoostAccess.allowed(SearchMode.OWNED_ONLY, Collections.emptySet());
		Monster olm = raidMonster(7554, "xerician");
		Monster zebak = raidMonster(11730, "tombs of amascut");
		assertEquals("overload (+)", PotionChoice.resolve(data, "ranged", 99, none, olm).getName());
		assertEquals("overload (+)", PotionChoice.resolve(data, "strength", 99, none, olm).getName());
		assertEquals("smelling salts", PotionChoice.resolve(data, "magic", 99, none, zebak).getName());
		assertEquals("smelling salts", PotionChoice.resolve(data, "attack", 99, none, zebak).getName());
		assertNull(PotionChoice.resolve(data, "ranged", 99, none, raidMonster(8061, "dragon")));
	}

	@Test
	public void excludedRaidSuppliesAreLeftOut()
	{
		Monster olm = raidMonster(7554, "xerician");
		for (SearchMode mode : SearchMode.values())
		{
			Predicate<Potion> noOverload = BoostAccess.allowed(mode, Collections.emptySet()).and(p -> !p.isRaidSupply());
			Potion ranged = PotionChoice.resolve(data, "ranged", 99, noOverload, olm);
			assertTrue(mode + " must not assume an overload", ranged == null || !ranged.isRaidSupply());
		}
		assertEquals("ranging potion", PotionChoice.resolve(data, "ranged", 99,
			BoostAccess.allowed(SearchMode.BUDGET, Collections.emptySet()).and(p -> !p.isRaidSupply()), olm).getName());
	}

	@Test
	public void theStrongestAntifireIsAssumedUnlessExcludedOrUnowned()
	{
		Set<Integer> none = Collections.emptySet();
		assertEquals(Antifire.SUPER, BoostAccess.antifire(SearchMode.UNLIMITED, none, none));
		assertEquals(Antifire.SUPER, BoostAccess.antifire(SearchMode.BUDGET, none, none));
		Set<Integer> superExcluded = Collections.singleton(Antifire.SUPER.getItemId());
		assertEquals(Antifire.REGULAR, BoostAccess.antifire(SearchMode.UNLIMITED, none, superExcluded));
		Set<Integer> both = new HashSet<>(Arrays.asList(Antifire.SUPER.getItemId(), Antifire.REGULAR.getItemId()));
		assertEquals(Antifire.NONE, BoostAccess.antifire(SearchMode.UNLIMITED, none, both));
		// Owned only: an extended super antifire counts; owning neither still assumes the ordinary potion.
		Set<Integer> extendedSuper = OwnedItems.expand(new HashSet<>(Collections.singletonList(ItemID._2DOSE4ANTIDRAGON)));
		assertEquals(Antifire.SUPER, BoostAccess.antifire(SearchMode.OWNED_ONLY, extendedSuper, none));
		assertEquals(Antifire.REGULAR, BoostAccess.antifire(SearchMode.OWNED_ONLY, none, none));
		assertEquals(Antifire.NONE, BoostAccess.antifire(SearchMode.OWNED_ONLY, none, both));
	}
}
