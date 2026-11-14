package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

/** Without a budget, gear tied on score is the cheapest, with the benchmark's fixed prices. */
public class CheapestTieTest
{
	private static SetupResult best(String monster, CombatClass cls) throws Exception
	{
		GameData data = WikiGameData.get(RuneLiteAPI.GSON).gameData(RuneLiteAPI.GSON);
		Monster m = WikiMonsters.get(RuneLiteAPI.GSON).monster(monster);
		CombatContext ctx = new CombatContext(m, PlayerLevels.maxed(), false, true, null);
		OptimizerSettings s = OptimizerSettings.builder().mode(SearchMode.UNLIMITED).killSpecials(true)
			.spellbooks(new HashSet<>(Arrays.asList("standard", "arceuus", "ancient"))).build();
		List<SetupResult> list = new Optimizer(data, ctx, s, id -> false, SingleSearchBench::price).optimize(cls, () -> false);
		return list.get(0);
	}

	@Test
	public void effectGearInAPlainSlotStillBoundsTheCheaperSearch() throws Exception
	{
		// The serpentine helm's venom kept every cheaper loadout below the bound when the bound dropped it; the
		// treads (max) add ranged strength short of the next max hit, so the cheaper (pr)(et) scores the same.
		SetupResult r = best("slagilith", CombatClass.RANGED);
		assertEquals("serpentine helm", r.getLoadout().get(Slot.HEAD).getName().toLowerCase());
		assertEquals("avernic treads (pr)(et)", r.getLoadout().get(Slot.FEET).getName().toLowerCase());
	}

	@Test
	public void effectGearAddingNothingDoesNotWinOnADearerFill() throws Exception
	{
		// Inquisitor's hauberk ties the plain body on score; its branch's dearer plain fill mustn't win.
		SetupResult r = best("eclipse moon (clone)", CombatClass.MELEE);
		assertTrue(r.getLoadout().get(Slot.BODY).getName(), !r.getLoadout().get(Slot.BODY).getName().toLowerCase().startsWith("inquisitor"));
		assertTrue(String.valueOf(r.getBuyCost()), r.getBuyCost() <= 85_199_437L);
	}
}
