package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.IntPredicate;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

/** Special gear whose effect can't apply with a weapon is left out of that weapon's search without changing its answer. */
public class GearPruningTest
{
	private static final Gson GSON = RuneLiteAPI.GSON;
	// Yama, Zulrah, Kraken, an abyssal demon, General Graardor, a fire giant and Kree'arra, then targets with their
	// own gear effects: Vorkath (undead), a Vyrewatch Sentinel (vampyre) and a revenant dragon.
	private static final int[] NPCS = {14176, 2042, 494, 415, 2215, 2075, 3162, 8061, 9756, 7940};
	private static GameData data;

	@BeforeClass
	public static void loadSnapshot() throws Exception
	{
		data = WikiGameData.get(GSON).gameData(GSON);
	}

	@After
	public void restore()
	{
		Optimizer.pruneGear = true;
	}

	private static List<SetupResult> search(SearchDepth depth, boolean ownedOnly, boolean onTask, boolean prune) throws Exception
	{
		Optimizer.pruneGear = prune;
		// Owned-only searches hold a fixed third of the catalogue.
		IntPredicate owned = id -> ownedOnly && Math.floorMod(Integer.hashCode(id * 0x9E3779B9), 3) == 0;
		List<SetupResult> out = new ArrayList<>();
		for (int npc : NPCS)
		{
			MonsterSummary summary = data.matchNpc(npc, null, 0);
			assertNotNull("npc " + npc, summary);
			Monster monster = WikiMonsters.get(GSON).monster(summary.getName());
			CombatContext ctx = new CombatContext(monster, PlayerLevels.maxed(), onTask, false, TestData.piety());
			OptimizerSettings settings = OptimizerSettings.builder()
				.mode(ownedOnly ? SearchMode.OWNED_ONLY : SearchMode.UNLIMITED).depth(depth)
				.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient", "arceuus"))).build();
			for (CombatClass cls : CombatClass.values())
			{
				out.addAll(new Optimizer(data, ctx, settings, owned, GearItem::getPrice).optimize(cls, () -> false));
			}
		}
		return out;
	}

	private static void assertSameSetups(SearchDepth depth, boolean ownedOnly, boolean onTask) throws Exception
	{
		List<SetupResult> full = search(depth, ownedOnly, onTask, false);
		List<SetupResult> pruned = search(depth, ownedOnly, onTask, true);
		assertEquals(full.size(), pruned.size());
		for (int i = 0; i < full.size(); i++)
		{
			String label = full.get(i).getLoadout().getWeapon().getName() + " (setup " + i + ")";
			assertEquals(label, full.get(i).getDps().getDps(), pruned.get(i).getDps().getDps(), 1e-9);
			assertEquals(label, full.get(i).getLoadout(), pruned.get(i).getLoadout());
		}
	}

	@Test
	public void theSetupsAreTheOnesFoundWhenEverySpecialItemIsTried() throws Exception
	{
		assertSameSetups(SearchDepth.NORMAL, false, false);
	}

	@Test
	public void theSetupsAreTheSameOnASlayerTask() throws Exception
	{
		assertSameSetups(SearchDepth.NORMAL, false, true);
	}

	@Test
	public void theSetupsAreTheSameAtBestDepthFromOwnedGear() throws Exception
	{
		assertSameSetups(SearchDepth.BEST, true, false);
	}
}
