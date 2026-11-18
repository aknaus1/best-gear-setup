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
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;
import net.runelite.api.gameval.ItemID;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

public class OptimizerTest
{
	/** Main code gets the client's Gson in startUp; tests use the same instance. */
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
	}

	private final Gear sword = weapon(100, "sword", "slash sword", 4, "chop,slash,aggressive", "slash,slash,accurate");
	private final Gear cheapHelm = item(200, "cheap helm", Slot.HEAD);
	private final Gear pricyHelm = item(201, "pricy helm", Slot.HEAD);
	private final Gear uselessRing = item(300, "useless ring", Slot.RING);
	private final Gear voidHelm = item(11665, "void melee helm", Slot.HEAD);
	private final Gear voidTop = item(8839, "void knight top", Slot.BODY);
	private final Gear voidRobe = item(8840, "void knight robe", Slot.LEGS);
	private final Gear voidGloves = item(8842, "void knight gloves", Slot.HANDS);
	private final Gear body = item(400, "strong body", Slot.BODY);

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
		for (Gear v : Arrays.asList(voidHelm, voidTop, voidRobe, voidGloves))
		{
			v.setTradeable(false);
		}
	}

	private List<Setup> run(SearchMode mode, long budget, Set<Integer> owned, Gear... items)
	{
		return run(mode, budget, owned, Gear::getPrice, items);
	}

	private List<Setup> run(SearchMode mode, long budget, Set<Integer> owned,
		ToLongFunction<Gear> prices, Gear... items)
	{
		OptimizerSettings settings = OptimizerSettings.builder()
			.mode(mode)
			.budget(budget)
			.spellbooks(new HashSet<>(Collections.singletonList("standard")))
			.build();
		return run(settings, owned, prices, items);
	}

	private List<Setup> run(OptimizerSettings settings, Set<Integer> owned, ToLongFunction<Gear> prices,
		Gear... items)
	{
		GameData data = TestData.gameData(Arrays.asList(items), Collections.emptyList());
		CombatContext ctx = TestFactories.combatContext(monster(100, 20), PlayerLevels.maxed(), false, true, piety());
		return com.bestgearsetup.calc.Optimizers.of(data, ctx, settings, owned::contains, prices).optimize(Combat.MELEE, () -> false, done -> { });
	}

	@Test
	public void unpricedPurchasesAreExcludedFromBudgetSearches()
	{
		ToLongFunction<Gear> noQuoteForPricyHelm = i -> i == pricyHelm ? ItemCosts.UNKNOWN : i.getPrice();
		Set<Integer> owned = new HashSet<>(Collections.singletonList(100));
		Setup budget = run(SearchMode.BUDGET, 2_000_000_000L, owned, noQuoteForPricyHelm,
			sword, cheapHelm, pricyHelm).get(0);
		assertEquals(cheapHelm, budget.getLoadout().get(Slot.HEAD));
		assertEquals(1_000, budget.getBuyCost());
		assertEquals(0, budget.getUnpricedItems());

		Setup unlimited = run(SearchMode.UNLIMITED, 0, owned, noQuoteForPricyHelm,
			sword, cheapHelm, pricyHelm).get(0);
		assertEquals(pricyHelm, unlimited.getLoadout().get(Slot.HEAD));
		assertEquals(0, unlimited.getBuyCost());
		assertEquals(1, unlimited.getUnpricedItems());

		owned.add(pricyHelm.getId());
		Setup alreadyOwned = run(SearchMode.BUDGET, 0, owned, noQuoteForPricyHelm,
			sword, cheapHelm, pricyHelm).get(0);
		assertEquals(pricyHelm, alreadyOwned.getLoadout().get(Slot.HEAD));
		assertEquals(0, alreadyOwned.getUnpricedItems());
	}

	@Test
	public void unpricedGearWithRangedBonusesIsNotTreatedAsUncountedAmmo()
	{
		// Only ammo-slot ammunition may stay unpriced; a helm with a ranged bonus is still bought once.
		pricyHelm.setRangedBonus(5);
		ToLongFunction<Gear> noQuoteForPricyHelm = i -> i == pricyHelm ? ItemCosts.UNKNOWN : i.getPrice();
		Set<Integer> owned = new HashSet<>(Collections.singletonList(100));
		Setup budget = run(SearchMode.BUDGET, 2_000_000_000L, owned, noQuoteForPricyHelm,
			sword, cheapHelm, pricyHelm).get(0);
		assertEquals(cheapHelm, budget.getLoadout().get(Slot.HEAD));
		assertEquals(0, budget.getUnpricedItems());
	}

	@Test
	public void untradeablesWithTradableComponentsCanBeBought()
	{
		Gear chargedHelm = item(ItemID.SERPENTINE_HELM_CHARGED, "charged helm", Slot.HEAD);
		chargedHelm.setTradeable(false);
		chargedHelm.setMeleeStr(10);
		chargedHelm.setPrice(5_000);
		Set<Integer> owned = new HashSet<>(Collections.singletonList(100));
		Setup r = run(SearchMode.BUDGET, 10_000, owned, sword, cheapHelm, chargedHelm).get(0);
		assertEquals(chargedHelm, r.getLoadout().get(Slot.HEAD));
		assertEquals(5_000, r.getBuyCost());

		Setup tooDear = run(SearchMode.BUDGET, 4_000, owned, sword, cheapHelm, chargedHelm).get(0);
		assertEquals(cheapHelm, tooDear.getLoadout().get(Slot.HEAD));
	}

	@Test
	public void budgetLimitsPurchases()
	{
		Set<Integer> owned = new HashSet<>(Collections.singletonList(100));
		Setup r = run(SearchMode.BUDGET, 10_000, owned, sword, cheapHelm, pricyHelm).get(0);
		assertEquals(cheapHelm, r.getLoadout().get(Slot.HEAD));
		assertEquals(1_000, r.getBuyCost());

		Setup unlimited = run(SearchMode.UNLIMITED, 0, owned, sword, cheapHelm, pricyHelm).get(0);
		assertEquals(pricyHelm, unlimited.getLoadout().get(Slot.HEAD));
	}

	@Test
	public void ownedOnlyUsesNothingElse()
	{
		assertTrue(run(SearchMode.OWNED_ONLY, 0, Collections.emptySet(), sword, cheapHelm).isEmpty());

		Set<Integer> owned = new HashSet<>(Arrays.asList(100, 200));
		Setup r = run(SearchMode.OWNED_ONLY, 0, owned, sword, cheapHelm, pricyHelm).get(0);
		assertEquals(cheapHelm, r.getLoadout().get(Slot.HEAD));
		assertEquals(0, r.getBuyCost());
	}

	@Test
	public void itemsThatAddNoDpsAreLeftOff()
	{
		Set<Integer> owned = new HashSet<>(Collections.singletonList(100));
		Setup r = run(SearchMode.UNLIMITED, 0, owned, sword, uselessRing).get(0);
		assertNull(r.getLoadout().get(Slot.RING));
	}

	@Test
	public void piecesThatChangeNoRateAreTakenOff()
	{
		// At 79 strength, +1 falls short of the next max hit in every stance, with or without void, so the cape is
		// worn for nothing; +1 slash moves the attack roll, and the void pieces' own zero bonuses hide a set effect.
		sword.setMeleeStr(79);
		Gear plateauCape = item(500, "plateau cape", Slot.CAPE);
		plateauCape.setMeleeStr(1);
		Gear accurateRing = item(501, "accurate ring", Slot.RING);
		accurateRing.setSlashBonus(1);
		Set<Integer> owned = new HashSet<>(Arrays.asList(100, 500, 501, 11665, 8839, 8840, 8842));
		Setup r = run(SearchMode.OWNED_ONLY, 0, owned,
			sword, plateauCape, accurateRing, voidHelm, voidTop, voidRobe, voidGloves).get(0);
		assertNull(r.getLoadout().get(Slot.CAPE));
		assertEquals(accurateRing, r.getLoadout().get(Slot.RING));
		assertEquals(voidHelm, r.getLoadout().get(Slot.HEAD));
		assertEquals(voidTop, r.getLoadout().get(Slot.BODY));
		assertEquals(voidRobe, r.getLoadout().get(Slot.LEGS));
		assertEquals(voidGloves, r.getLoadout().get(Slot.HANDS));
	}

	@Test
	public void fillModesKeepPiecesWithTheBonusTheyAskFor()
	{
		sword.setMeleeStr(79);
		Gear plateauCape = item(500, "plateau cape", Slot.CAPE);
		plateauCape.setMeleeStr(1);
		plateauCape.setStabDef(5);
		Set<Integer> owned = new HashSet<>(Arrays.asList(100, 500));
		OptimizerSettings.OptimizerSettingsBuilder settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY)
			.spellbooks(new HashSet<>(Collections.singletonList("standard")));
		assertNull(run(settings.fillMode(FillMode.NONE).build(), owned, Gear::getPrice, sword, plateauCape)
			.get(0).getLoadout().get(Slot.CAPE));
		assertEquals(plateauCape, run(settings.fillMode(FillMode.DEFENCE).defenceFocus(DefenceFocus.STAB).build(), owned,
			Gear::getPrice, sword, plateauCape).get(0).getLoadout().get(Slot.CAPE));
	}

	@Test
	public void findsVoidSetBonusThatGreedySearchWouldMiss()
	{
		Set<Integer> owned = new HashSet<>(Arrays.asList(100, 11665, 8839, 8840, 8842, 400));
		Setup r = run(SearchMode.OWNED_ONLY, 0, owned,
			sword, voidHelm, voidTop, voidRobe, voidGloves, body).get(0);
		assertEquals(voidHelm, r.getLoadout().get(Slot.HEAD));
		assertEquals(voidTop, r.getLoadout().get(Slot.BODY));
		assertEquals(voidGloves, r.getLoadout().get(Slot.HANDS));
	}

	@Test
	public void unownedUntradeablesAreOnlyUsedInBestInSlot()
	{
		Set<Integer> owned = new HashSet<>(Arrays.asList(100, 400));
		Setup budget = run(SearchMode.BUDGET, Long.MAX_VALUE, owned,
			sword, voidHelm, voidTop, voidRobe, voidGloves, body).get(0);
		assertEquals(body, budget.getLoadout().get(Slot.BODY));

		Setup bis = run(SearchMode.UNLIMITED, 0, owned,
			sword, voidHelm, voidTop, voidRobe, voidGloves, body).get(0);
		assertEquals(voidTop, bis.getLoadout().get(Slot.BODY));
	}

	@Test
	public void untradeableComponentsRespectTheBudgetAndOwnership()
	{
		// A charged serpentine helm: untradeable, bought as its tradable uncharged base.
		pricyHelm.setId(ItemID.SERPENTINE_HELM_CHARGED);
		pricyHelm.setTradeable(false);
		Set<Integer> owned = new HashSet<>(Collections.singletonList(100));
		Setup cheap = run(SearchMode.BUDGET, 10_000, owned, sword, cheapHelm, pricyHelm).get(0);
		assertEquals(cheapHelm, cheap.getLoadout().get(Slot.HEAD));
		assertEquals(1_000, cheap.getBuyCost());

		Setup enough = run(SearchMode.BUDGET, 50_000_000, owned, sword, cheapHelm, pricyHelm).get(0);
		assertEquals(pricyHelm, enough.getLoadout().get(Slot.HEAD));
		assertEquals(50_000_000, enough.getBuyCost());

		owned.add(pricyHelm.getId());
		Setup alreadyOwned = run(SearchMode.BUDGET, 0, owned, sword, cheapHelm, pricyHelm).get(0);
		assertEquals(pricyHelm, alreadyOwned.getLoadout().get(Slot.HEAD));
		assertEquals(0, alreadyOwned.getBuyCost());
	}

	@Test
	public void untradeableComponentsCountTowardTheCombinedBudget()
	{
		cheapHelm.setId(ItemID.SERPENTINE_HELM_CHARGED);
		cheapHelm.setTradeable(false);
		body.setId(ItemID.SANGUINESTI_STAFF);
		body.setTradeable(false);
		Set<Integer> owned = new HashSet<>(Collections.singletonList(100));
		Setup result = run(SearchMode.BUDGET, 10_000, owned, sword, cheapHelm, body).get(0);
		assertTrue(result.getBuyCost() <= 10_000);
		assertFalse(result.getLoadout().get(Slot.HEAD) != null && result.getLoadout().get(Slot.BODY) != null);
	}

	@Test
	public void respectsMonsterImmunity()
	{
		GameData data = TestData.gameData(Collections.singletonList(sword), Collections.emptyList());
		CombatContext ctx = TestFactories.combatContext(monster(1, 0, "melee immune"), PlayerLevels.maxed(), false, true, piety());
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED)
			.spellbooks(Collections.singleton("standard")).build();
		assertTrue(com.bestgearsetup.calc.Optimizers.of(data, ctx, settings, id -> true, Gear::getPrice)
			.optimize(Combat.MELEE, () -> false, done -> { }).isEmpty());
	}
}
