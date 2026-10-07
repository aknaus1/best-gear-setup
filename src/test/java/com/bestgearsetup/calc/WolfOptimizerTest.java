package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** The exact Bloodrager chance reverses a close wolf crush equipment ranking. */
public class WolfOptimizerTest
{
	@Test
	public void slayerHelmetAndFuryBeatFullBloodMoonAndStrengthAmulet() throws Exception
	{
		Gson gson = new Gson();
		GameData data = WikiGameData.get(gson).gameData(gson);
		Monster wolf = WikiMonsters.get(gson).monster(data.matchNpc(106, null, 0).getName());
		CombatContext ctx = new CombatContext(wolf, new PlayerLevels(82, 87, 80, 81, 76, 76, 87, 67),
			true, false, null);
		Set<Integer> owned = new HashSet<>(Arrays.asList(29028, 11865, 6570, 1725, 6585, 28997,
			29022, 29025, 7462, 13239, 11773));
		OptimizerSettings settings = OptimizerSettings.builder()
			.mode(SearchMode.OWNED_ONLY)
			.depth(SearchDepth.BEST)
			.styles(Collections.singleton(AttackStyle.Type.CRUSH))
			.spellbooks(Collections.singleton("standard"))
			.build();
		SetupResult best = new Optimizer(data, ctx, settings, owned::contains, item -> 1_000)
			.optimize(CombatClass.MELEE, () -> false).get(0);
		assertEquals(11865, best.getLoadout().get(Slot.HEAD).getId());
		assertEquals(6585, best.getLoadout().get(Slot.NECK).getId());
		assertEquals(33, best.getDps().getMaxHit());
		assertEquals(6.021772704, best.getDps().getDps(), 1e-9);

		Loadout bloodMoon = best.getLoadout().copy();
		bloodMoon.set(Slot.HEAD, data.getItem(Slot.HEAD, 29028));
		bloodMoon.set(Slot.NECK, data.getItem(Slot.NECK, 1725));
		DpsResult bloodMoonDps = DpsCalculator.calculate(bloodMoon, ctx);
		assertEquals(30, bloodMoonDps.getMaxHit());
		assertEquals(6.020908603581, bloodMoonDps.getDps(), 1e-9);
		assertTrue(best.getDps().getDps() > bloodMoonDps.getDps());
	}
}
