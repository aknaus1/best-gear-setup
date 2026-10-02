package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.bestgearsetup.calc.PotionChoice;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.WikiGameData;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.runelite.api.gameval.ItemID;
import org.junit.BeforeClass;
import org.junit.Test;

public class BoostAccessTest
{
	private static GameData data;

	@BeforeClass
	public static void loadSnapshot() throws Exception
	{
		data = WikiGameData.get(new Gson()).gameData(new Gson());
	}

	private static String best(String skill, SearchMode mode, Integer... owned)
	{
		Set<Integer> expanded = OwnedItems.expand(new HashSet<>(Arrays.asList(owned)));
		Predicate<Potion> boosts = BoostAccess.allowed(mode, expanded);
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
	public void budgetBuysPotionsButNotHearts()
	{
		assertEquals("ranging potion", best("ranged", SearchMode.BUDGET));
		assertEquals("forgotten brew", best("magic", SearchMode.BUDGET));
		assertEquals("saturated heart", best("magic", SearchMode.BUDGET, ItemID.SATURATED_HEART));
		assertEquals("saturated heart", best("magic", SearchMode.UNLIMITED));
	}

	@Test
	public void aPotionChosenByNameIsAlwaysUsed()
	{
		Predicate<Potion> none = BoostAccess.allowed(SearchMode.OWNED_ONLY, Collections.emptySet());
		assertEquals("super combat potion",
			PotionChoice.resolve(data, "attack", 99, "super combat potion", none).getName());
	}
}
