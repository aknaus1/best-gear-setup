package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.ItemCosts;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;
import org.junit.Test;

public class OptimizerTest
{
	private final GearItem sword = weapon(100, "sword", "slash sword", 4, "chop,slash,aggressive", "slash,slash,accurate");
	private final GearItem cheapHelm = item(200, "cheap helm", Slot.HEAD);
	private final GearItem pricyHelm = item(201, "pricy helm", Slot.HEAD);
	private final GearItem uselessRing = item(300, "useless ring", Slot.RING);
	private final GearItem voidHelm = item(11665, "void melee helm", Slot.HEAD);
	private final GearItem voidTop = item(8839, "void knight top", Slot.BODY);
	private final GearItem voidRobe = item(8840, "void knight robe", Slot.LEGS);
	private final GearItem voidGloves = item(8842, "void knight gloves", Slot.HANDS);
	private final GearItem body = item(400, "strong body", Slot.BODY);

	public OptimizerTest()
	{
		sword.setMeleeStr(80);
		sword.setSlashBonus(80);
		sword.setPrice(1_000_000);
		cheapHelm.setMeleeStr(2);
		cheapHelm.setPrice(1_000);
		pricyHelm.setMeleeStr(10);
		pricyHelm.setSlashBonus(10);
		pricyHelm.setPrice(50_000_000);
		uselessRing.setPrice(5_000);
		body.setMeleeStr(1);
		body.setSlashBonus(1);
		body.setPrice(10_000);
		for (GearItem v : Arrays.asList(voidHelm, voidTop, voidRobe, voidGloves))
		{
			v.setTradeable(false);
		}
	}

	private List<SetupResult> run(SearchMode mode, long budget, boolean untradeables, Set<Integer> owned, GearItem... items)
	{
		return run(mode, budget, untradeables, owned, GearItem::getPrice, items);
	}

	private List<SetupResult> run(SearchMode mode, long budget, boolean untradeables, Set<Integer> owned,
		ToLongFunction<GearItem> prices, GearItem... items)
	{
		GameData data = TestData.gameData(Arrays.asList(items), Collections.emptyList());
		CombatContext ctx = new CombatContext(monster(100, 20), PlayerLevels.maxed(), false, true, piety());
		OptimizerSettings settings = OptimizerSettings.builder()
			.mode(mode)
			.budget(budget)
			.allowUntradeables(untradeables)
			.spellbooks(new HashSet<>(Collections.singletonList("standard")))
			.build();
		return new Optimizer(data, ctx, settings, owned::contains, prices).optimize(CombatClass.MELEE, () -> false);
	}

	@Test
	public void unpricedPurchasesAreExcludedFromBudgetSearches()
	{
		ToLongFunction<GearItem> noQuoteForPricyHelm = i -> i == pricyHelm ? ItemCosts.UNKNOWN : i.getPrice();
		Set<Integer> owned = new HashSet<>(Collections.singletonList(100));
		SetupResult budget = run(SearchMode.BUDGET, 2_000_000_000L, false, owned, noQuoteForPricyHelm,
			sword, cheapHelm, pricyHelm).get(0);
		assertEquals(cheapHelm, budget.getLoadout().get(Slot.HEAD));
		assertEquals(1_000, budget.getBuyCost());
		assertEquals(0, budget.getUnpricedItems());

		SetupResult unlimited = run(SearchMode.UNLIMITED, 0, false, owned, noQuoteForPricyHelm,
			sword, cheapHelm, pricyHelm).get(0);
		assertEquals(pricyHelm, unlimited.getLoadout().get(Slot.HEAD));
		assertEquals(0, unlimited.getBuyCost());
		assertEquals(1, unlimited.getUnpricedItems());

		owned.add(pricyHelm.getId());
		SetupResult alreadyOwned = run(SearchMode.BUDGET, 0, false, owned, noQuoteForPricyHelm,
			sword, cheapHelm, pricyHelm).get(0);
		assertEquals(pricyHelm, alreadyOwned.getLoadout().get(Slot.HEAD));
		assertEquals(0, alreadyOwned.getUnpricedItems());
	}

	@Test
	public void budgetLimitsPurchases()
	{
		Set<Integer> owned = new HashSet<>(Collections.singletonList(100));
		SetupResult r = run(SearchMode.BUDGET, 10_000, false, owned, sword, cheapHelm, pricyHelm).get(0);
		assertEquals(cheapHelm, r.getLoadout().get(Slot.HEAD));
		assertEquals(1_000, r.getBuyCost());

		SetupResult unlimited = run(SearchMode.UNLIMITED, 0, false, owned, sword, cheapHelm, pricyHelm).get(0);
		assertEquals(pricyHelm, unlimited.getLoadout().get(Slot.HEAD));
	}

	@Test
	public void ownedOnlyUsesNothingElse()
	{
		assertTrue(run(SearchMode.OWNED_ONLY, 0, false, Collections.emptySet(), sword, cheapHelm).isEmpty());

		Set<Integer> owned = new HashSet<>(Arrays.asList(100, 200));
		SetupResult r = run(SearchMode.OWNED_ONLY, 0, false, owned, sword, cheapHelm, pricyHelm).get(0);
		assertEquals(cheapHelm, r.getLoadout().get(Slot.HEAD));
		assertEquals(0, r.getBuyCost());
	}

	@Test
	public void itemsThatAddNoDpsAreLeftOff()
	{
		Set<Integer> owned = new HashSet<>(Collections.singletonList(100));
		SetupResult r = run(SearchMode.UNLIMITED, 0, false, owned, sword, uselessRing).get(0);
		assertNull(r.getLoadout().get(Slot.RING));
	}

	@Test
	public void findsVoidSetBonusThatGreedySearchWouldMiss()
	{
		Set<Integer> owned = new HashSet<>(Arrays.asList(100, 11665, 8839, 8840, 8842, 400));
		SetupResult r = run(SearchMode.OWNED_ONLY, 0, false, owned,
			sword, voidHelm, voidTop, voidRobe, voidGloves, body).get(0);
		assertEquals(voidHelm, r.getLoadout().get(Slot.HEAD));
		assertEquals(voidTop, r.getLoadout().get(Slot.BODY));
		assertEquals(voidGloves, r.getLoadout().get(Slot.HANDS));
	}

	@Test
	public void unownedUntradeablesNeedOptIn()
	{
		Set<Integer> owned = new HashSet<>(Arrays.asList(100, 400));
		SetupResult without = run(SearchMode.BUDGET, 1_000_000, false, owned,
			sword, voidHelm, voidTop, voidRobe, voidGloves, body).get(0);
		assertEquals(body, without.getLoadout().get(Slot.BODY));

		SetupResult with = run(SearchMode.BUDGET, 1_000_000, true, owned,
			sword, voidHelm, voidTop, voidRobe, voidGloves, body).get(0);
		assertEquals(voidTop, with.getLoadout().get(Slot.BODY));
	}

	@Test
	public void untradeableComponentsRespectTheBudgetAndOwnership()
	{
		pricyHelm.setTradeable(false);
		Set<Integer> owned = new HashSet<>(Collections.singletonList(100));
		SetupResult cheap = run(SearchMode.BUDGET, 10_000, true, owned, sword, cheapHelm, pricyHelm).get(0);
		assertEquals(cheapHelm, cheap.getLoadout().get(Slot.HEAD));
		assertEquals(1_000, cheap.getBuyCost());

		SetupResult enough = run(SearchMode.BUDGET, 50_000_000, true, owned, sword, cheapHelm, pricyHelm).get(0);
		assertEquals(pricyHelm, enough.getLoadout().get(Slot.HEAD));
		assertEquals(50_000_000, enough.getBuyCost());

		owned.add(pricyHelm.getId());
		SetupResult alreadyOwned = run(SearchMode.BUDGET, 0, false, owned, sword, cheapHelm, pricyHelm).get(0);
		assertEquals(pricyHelm, alreadyOwned.getLoadout().get(Slot.HEAD));
		assertEquals(0, alreadyOwned.getBuyCost());
	}

	@Test
	public void untradeableComponentsCountTowardTheCombinedBudget()
	{
		cheapHelm.setTradeable(false);
		body.setTradeable(false);
		Set<Integer> owned = new HashSet<>(Collections.singletonList(100));
		SetupResult result = run(SearchMode.BUDGET, 10_000, true, owned, sword, cheapHelm, body).get(0);
		assertTrue(result.getBuyCost() <= 10_000);
		assertFalse(result.getLoadout().get(Slot.HEAD) != null && result.getLoadout().get(Slot.BODY) != null);
	}

	@Test
	public void respectsMonsterImmunity()
	{
		GameData data = TestData.gameData(Collections.singletonList(sword), Collections.emptyList());
		CombatContext ctx = new CombatContext(monster(1, 0, "melee immune"), PlayerLevels.maxed(), false, true, piety());
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED)
			.spellbooks(Collections.singleton("standard")).build();
		assertTrue(new Optimizer(data, ctx, settings, id -> true, GearItem::getPrice)
			.optimize(CombatClass.MELEE, () -> false).isEmpty());
	}
}
