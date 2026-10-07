package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.WikiGameData;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

public class OptimizerProgressTest
{
	private static GameData data;
	private static CombatContext context;

	@BeforeClass
	public static void load() throws Exception
	{
		data = WikiGameData.get(new Gson()).gameData(new Gson());
		context = new CombatContext(TestData.monster(100, 20), PlayerLevels.maxed(), false, true, null);
	}

	private static List<Double> progress(SearchDepth depth)
	{
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED).depth(depth)
			.spellbooks(Collections.emptySet()).build();
		List<Double> seen = new ArrayList<>();
		List<SetupResult> results = new Optimizer(data, context, settings, id -> false, GearItem::getPrice)
			.optimize(CombatClass.MELEE, () -> false, seen::add);
		assertFalse(results.isEmpty());
		return seen;
	}

	@Test
	public void progressRisesToCompletionAtEveryDepth()
	{
		for (SearchDepth depth : SearchDepth.values())
		{
			List<Double> seen = progress(depth);
			assertTrue(depth + ": reports more than start and end", seen.size() > 2);
			assertEquals(depth + ": starts at zero", 0, seen.get(0), 0);
			for (int i = 1; i < seen.size(); i++)
			{
				assertTrue(depth + ": never falls", seen.get(i) >= seen.get(i - 1));
			}
			assertEquals(depth + ": ends complete", 1, seen.get(seen.size() - 1), 1e-9);
		}
	}

	@Test
	public void aClassTheTargetIgnoresIsCompleteAtOnce()
	{
		List<Double> seen = new ArrayList<>();
		// Dusk takes melee damage only.
		Monster dusk = TestData.monster(100, 20);
		dusk.setId(7851);
		CombatContext meleeOnly = new CombatContext(dusk, PlayerLevels.maxed(), false, true, null);
		assertTrue(new Optimizer(data, meleeOnly, OptimizerSettings.builder().build(), id -> false, GearItem::getPrice)
			.optimize(CombatClass.RANGED, () -> false, seen::add).isEmpty());
		assertEquals(Collections.singletonList(1.0), seen);
	}
}
