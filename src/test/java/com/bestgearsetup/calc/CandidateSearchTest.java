package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.BeforeClass;
import org.junit.Test;

/** Candidate pruning and trial evaluation on synthetic gear. */
public class CandidateSearchTest
{
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(new Gson());
	}

	private static GearItem stabSword()
	{
		GearItem sword = weapon(100, "sword", "stab sword", 4, "stab,stab,accurate", "lunge,stab,aggressive");
		sword.setStabBonus(60);
		sword.setMeleeStr(40);
		return sword;
	}

	private static OptimizerSettings.OptimizerSettingsBuilder settings()
	{
		return OptimizerSettings.builder().mode(SearchMode.UNLIMITED).depth(SearchDepth.BEST)
			.spellbooks(new HashSet<>());
	}

	private static double dps(GearItem sword, GearItem head, CombatContext ctx, String style)
	{
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, sword);
		l.set(Slot.HEAD, head);
		l.setStyle(AttackStyle.parse(style));
		return DpsCalculator.calculate(l, ctx).getDps();
	}

	private static double best(GearItem sword, GearItem head, CombatContext ctx)
	{
		return Math.max(dps(sword, head, ctx, "stab,stab,accurate"), dps(sword, head, ctx, "lunge,stab,aggressive"));
	}

	/**
	 * +1 attack and +1 strength reach the same max hit as +3 strength while keeping some accuracy, so they beat
	 * both +3 attack and +3 strength, though each single-stat ranking puts them last.
	 */
	@Test
	public void balancedItemSurvivesPruning()
	{
		GearItem sword = stabSword();
		GearItem attack = item(201, "attack helm", Slot.HEAD);
		attack.setStabBonus(3);
		GearItem strength = item(202, "strength helm", Slot.HEAD);
		strength.setMeleeStr(3);
		GearItem balanced = item(203, "balanced helm", Slot.HEAD);
		balanced.setStabBonus(1);
		balanced.setMeleeStr(1);
		CombatContext ctx = new CombatContext(monster(100, 60), PlayerLevels.maxed(), false, false, null);
		double best = best(sword, balanced, ctx);
		assertTrue(best > best(sword, strength, ctx));
		assertTrue(best > best(sword, attack, ctx));

		Optimizer optimizer = new Optimizer(TestData.gameData(Arrays.asList(sword, attack, strength, balanced),
			Collections.emptyList()), ctx, settings().build(), id -> true, i -> 0);
		assertTrue(optimizer.candidates(CombatClass.MELEE).get(Slot.HEAD).contains(balanced));
		SetupResult result = optimizer.optimize(CombatClass.MELEE, () -> false).get(0);
		assertEquals(balanced, result.getLoadout().get(Slot.HEAD));
		assertEquals(best, result.getDps().getDps(), 1e-12);
	}

	/** A dominated item (no better stat, no cheaper, no less risky) is pruned; a cheaper equal one is not. */
	@Test
	public void dominatedItemsArePruned()
	{
		GearItem sword = stabSword();
		GearItem good = item(201, "good helm", Slot.HEAD);
		good.setStabBonus(5);
		good.setMeleeStr(2);
		good.setPrice(1_000);
		GearItem worse = item(202, "worse helm", Slot.HEAD);
		worse.setStabBonus(4);
		worse.setMeleeStr(2);
		worse.setPrice(1_000);
		GearItem cheaper = item(203, "cheaper helm", Slot.HEAD);
		cheaper.setStabBonus(4);
		cheaper.setMeleeStr(2);
		cheaper.setPrice(10);
		CombatContext ctx = new CombatContext(monster(100, 60), PlayerLevels.maxed(), false, false, null);
		Optimizer optimizer = new Optimizer(TestData.gameData(Arrays.asList(sword, good, worse, cheaper),
			Collections.emptyList()), ctx, settings().mode(SearchMode.BUDGET).budget(10_000).build(), id -> false,
			GearItem::getPrice);
		assertEquals(Arrays.asList(good, cheaper), optimizer.candidates(CombatClass.MELEE).get(Slot.HEAD));
	}

	/** The trial evaluation only ever chooses a stance the experience filter allows, even when another scores more. */
	@Test
	public void trialOptionsFollowTheExperienceFilter()
	{
		GearItem sword = stabSword();
		CombatContext ctx = new CombatContext(monster(300, 300), PlayerLevels.maxed(), false, false, null);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, sword);
		double accurate = dps(sword, null, ctx, "stab,stab,accurate");
		double aggressive = dps(sword, null, ctx, "lunge,stab,aggressive");
		boolean accurateWins = accurate > aggressive;
		assertTrue(accurate != aggressive);

		Optimizer free = new Optimizer(TestData.gameData(Collections.singletonList(sword), Collections.emptyList()), ctx,
			settings().build(), id -> true, i -> 0);
		l.setStyle(AttackStyle.parse(accurateWins ? "lunge,stab,aggressive" : "stab,stab,accurate"));
		assertEquals(Math.max(accurate, aggressive), free.scoreWithBestOption(l, CombatClass.MELEE), 1e-12);
		assertEquals(accurateWins ? "accurate" : "aggressive", l.getStyle().getStance());

		// Forbid the winning stance's experience: the other one is chosen although it scores less.
		Optimizer filtered = new Optimizer(TestData.gameData(Collections.singletonList(sword), Collections.emptyList()),
			ctx, settings().attackXp(!accurateWins).strengthXp(accurateWins).build(), id -> true, i -> 0);
		assertEquals(Math.min(accurate, aggressive), filtered.scoreWithBestOption(l, CombatClass.MELEE), 1e-12);
		assertEquals(accurateWins ? "aggressive" : "accurate", l.getStyle().getStance());
	}

	/** A trial over the expensive-item cap is worth nothing, whatever its option. */
	@Test
	public void trialOverTheCapScoresNothing()
	{
		GearItem sword = stabSword();
		sword.setPrice(5_000_000);
		CombatContext ctx = new CombatContext(monster(100, 60), PlayerLevels.maxed(), false, false, null);
		Optimizer optimizer = new Optimizer(TestData.gameData(Collections.singletonList(sword), Collections.emptyList()),
			ctx, settings().wildernessRiskLimited(true).maxExpensiveItems(0).build(), id -> true, GearItem::getPrice);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, sword);
		l.setStyle(AttackStyle.parse("lunge,stab,aggressive"));
		assertEquals(0, optimizer.scoreWithBestOption(l, CombatClass.MELEE), 0);
	}
}
