package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Monster;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

/** Checks that an optimized setup carries the thrall tier used in its damage estimate. */
public class SetupResultThrallTest
{
	private static Setup search(int magic, boolean thrallsEnabled, boolean immuneThrall)
	{
		Gear sword = TestData.weapon(100, "sword", "slash sword", 4, "chop,slash,aggressive");
		sword.setSlashBonus(80);
		sword.setMeleeStr(80);
		GameData data = TestData.gameData(Collections.singletonList(sword), Collections.emptyList());
		Monster monster = TestData.monster(100, 20);
		monster.setHitpoints(100);
		monster.setImmuneThrall(immuneThrall);
		PlayerLevels levels = new PlayerLevels(99, 99, 99, 99, magic, 99, 99, 99);
		CombatContext ctx = new CombatContext(monster, levels, false, 99, 99, 99, magic, null, thrallsEnabled);
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED)
			.styles(EnumSet.of(Style.Type.SLASH)).build();
		List<Setup> results = com.bestgearsetup.calc.Optimizers.of(data, ctx, settings, id -> true, item -> 0)
			.optimize(Combat.MELEE, () -> false, done -> { });
		assertFalse(results.isEmpty());
		return results.get(0);
	}

	@Test
	public void resultCarriesTheAssumedThrallTier()
	{
		int[] magicLevels = {38, 57, 76};
		Thrall[] tiers = {Thrall.LESSER, Thrall.SUPERIOR, Thrall.GREATER};
		for (int i = 0; i < magicLevels.length; i++)
		{
			Setup result = search(magicLevels[i], true, false);
			assertTrue(result.getSupportSpells().contains(SupportSpell.THRALL));
			assertEquals(tiers[i], result.getThrall());
		}
	}

	@Test
	public void resultHasNoTierWhenThrallIsNotAssumed()
	{
		for (Setup result : new Setup[]{search(76, false, false), search(37, true, false),
			search(76, true, true)})
		{
			assertFalse(result.getSupportSpells().contains(SupportSpell.THRALL));
			assertNull(result.getThrall());
		}
	}
}
