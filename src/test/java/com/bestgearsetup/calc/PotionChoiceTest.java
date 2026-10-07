package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Prayer;
import com.bestgearsetup.data.Slot;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class PotionChoiceTest
{
	private static Potion potion(String name, int base, int pct, String... attributes)
	{
		Potion p = new Potion();
		p.setName(name);
		p.setBaseIncrease(base);
		p.setPercentageIncrease(pct);
		p.setAttributes(Arrays.asList(attributes));
		return p;
	}

	private static GameData data()
	{
		Map<String, List<Potion>> potions = new HashMap<>();
		potions.put("ranged", Arrays.asList(
			potion("ranging potion", 4, 10),
			potion("overload (+)", 6, 16, "xerician"),
			potion("smelling salts", 11, 16, "tombs of amascut")));
		potions.put("strength", Collections.singletonList(potion("dragon battleaxe", 10, 0)));
		return new GameData(Collections.<MonsterSummary>emptyList(), Collections.<String, List<GearItem>>emptyMap(),
			Collections.<GearItem>emptyList(), Collections.emptyList(), Collections.<String, List<Prayer>>emptyMap(), potions);
	}

	@Test
	public void bestSkipsRaidOnlyPotions()
	{
		// 99 + 4 + 9 = 112 (overloads and smelling salts are raid-only)
		assertEquals(112, PotionChoice.boostedLevel(data(), "ranged", 99, PotionChoice.BEST));
		assertEquals("Ranging Potion", PotionChoice.describe(data(), "ranged", 99, PotionChoice.BEST));
	}

	@Test
	public void bestUsesRaidSuppliesInsideTheirRaid()
	{
		Monster vorkath = TestData.monster(214, 26);
		Monster zebak = TestData.monster(70, 0, "tombs of amascut");
		Monster olm = TestData.monster(150, 0, "xerician");
		// Owning nothing: raid supplies are handed out in the raid, so ownership doesn't apply to them.
		java.util.function.Predicate<Potion> none = p -> p.isRaidSupply();
		// 99 + 11 + 15 = 125
		assertEquals(125, PotionChoice.boostedLevel(data(), "ranged", 99, PotionChoice.BEST, none, zebak));
		// 99 + 6 + 15 = 120; smelling salts don't work in the Chambers of Xeric
		assertEquals(120, PotionChoice.boostedLevel(data(), "ranged", 99, PotionChoice.BEST, none, olm));
		assertEquals(112, PotionChoice.boostedLevel(data(), "ranged", 99, PotionChoice.BEST, p -> true, vorkath));
		assertNull(PotionChoice.resolve(data(), "ranged", 99, PotionChoice.BEST, none, vorkath));
	}

	@Test
	public void namedAndNoneChoices()
	{
		// 99 + 11 + 15 = 125
		assertEquals(125, PotionChoice.boostedLevel(data(), "ranged", 99, "smelling salts"));
		assertEquals(99, PotionChoice.boostedLevel(data(), "ranged", 99, PotionChoice.NONE));
		assertTrue(PotionChoice.namesFor(data(), CombatClass.RANGED).contains("overload (+)"));
	}

	@Test
	public void dragonBattleaxeIsNotOffered()
	{
		assertTrue(PotionChoice.namesFor(data(), CombatClass.MELEE).isEmpty());
	}

	@Test
	public void slotLocksRoundTrip()
	{
		Map<Slot, SlotLock> locks = SlotLock.parse("WEAPON:4151,SHIELD:empty,BODY:fill,bogus,HEAD:x");
		assertEquals(SlotLock.item(4151), locks.get(Slot.WEAPON));
		assertEquals(SlotLock.empty(), locks.get(Slot.SHIELD));
		assertEquals(SlotLock.fill(), locks.get(Slot.BODY));
		assertEquals(3, locks.size());
		assertEquals(locks, SlotLock.parse(SlotLock.format(locks)));
	}

	@Test
	public void namedRaidPotionsReportWhereTheyApply()
	{
		Monster vorkath = TestData.monster(214, 26);
		vorkath.setName("vorkath (post-quest)");
		Monster zebak = TestData.monster(70, 0, "tombs of amascut");
		zebak.setName("zebak");
		Monster olm = TestData.monster(150, 0, "xerician");
		Potion salts = potion("smelling salts", 11, 16, "tombs of amascut");
		Potion overload = potion("overload (+)", 6, 16, "xerician");
		assertEquals("only usable in the Tombs of Amascut", PotionChoice.restriction(salts, vorkath));
		assertNull(PotionChoice.restriction(salts, zebak));
		assertEquals("only usable in the Chambers of Xeric", PotionChoice.restriction(overload, vorkath));
		assertNull(PotionChoice.restriction(overload, olm));
		assertNull(PotionChoice.restriction(potion("ranging potion", 4, 10), vorkath));
		assertEquals("only usable in Nightmare Zone", PotionChoice.restriction(potion("super ranging", 5, 15, "nmz"), olm));
		assertTrue(PotionChoice.isExplicit("smelling salts"));
		assertFalse(PotionChoice.isExplicit(PotionChoice.BEST));
		assertFalse(PotionChoice.isExplicit(PotionChoice.NONE));
		assertFalse(PotionChoice.isExplicit(null));
	}
}
