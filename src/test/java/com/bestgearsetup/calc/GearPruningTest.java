package com.bestgearsetup.calc;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.NameLists;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Special gear whose effect can't apply with a weapon and option is searched as plain gear without changing the answer:
 * the search is exact, so no special item worn in place of a found setup's piece, with the weapon's best option, scores
 * higher. (OptimizerOracleTest checks the whole search against an exhaustive one on small banks.)
 */
public class GearPruningTest
{
	private static final Gson GSON = RuneLiteAPI.GSON;
	// Yama, Zulrah, Kraken, an abyssal demon, General Graardor, a fire giant and Kree'arra, then targets with their
	// own gear effects: Vorkath (undead), a Vyrewatch Sentinel (vampyre) and a revenant dragon.
	private static final int[] NPCS = {14176, 2042, 494, 415, 2215, 2075, 3162, 8061, 9756, 7940};
	private static final NameLists.NameList SPECIAL_GEAR = NameLists.list("special gear");
	private static GameData data;

	@BeforeClass
	public static void loadSnapshot() throws Exception
	{
		data = WikiGameData.get(GSON).gameData(GSON);
	}

	private static void assertNoSpecialItemBeatsTheSetups(boolean ownedOnly, boolean onTask) throws Exception
	{
		// Owned-only searches hold a fixed third of the catalogue.
		IntPredicate owned = id -> ownedOnly && Math.floorMod(Integer.hashCode(id * 0x9E3779B9), 3) == 0;
		int checked = 0;
		for (int npc : NPCS)
		{
			MonsterSummary summary = data.matchNpc(npc, null, 0);
			assertNotNull("npc " + npc, summary);
			Monster monster = WikiMonsters.get(GSON).monster(summary.getName());
			CombatContext ctx = new CombatContext(monster, PlayerLevels.maxed(), onTask, false, TestData.piety());
			OptimizerSettings settings = OptimizerSettings.builder()
				.mode(ownedOnly ? SearchMode.OWNED_ONLY : SearchMode.UNLIMITED).resultsPerClass(1)
				.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient", "arceuus"))).build();
			for (CombatClass cls : CombatClass.values())
			{
				Optimizer optimizer = new Optimizer(data, ctx, settings, owned, GearItem::getPrice);
				for (SetupResult result : optimizer.optimize(cls, () -> false))
				{
					Loadout found = result.getLoadout();
					double score = optimizer.scoreWithBestOption(found.copy(), cls);
					for (Map.Entry<Slot, List<GearItem>> e : optimizer.candidates(cls).entrySet())
					{
						if (e.getKey() == Slot.SHIELD && found.getWeapon().isTwoHanded())
							continue;
						for (GearItem item : e.getValue())
						{
							if (!SPECIAL_GEAR.matches(item.getLowerCombatName()))
								continue;
							Loadout swapped = found.copy();
							swapped.set(e.getKey(), item);
							checked++;
							assertTrue(monster.getName() + " " + cls + ": " + item.getName() + " beats " + found,
								optimizer.scoreWithBestOption(swapped, cls) <= score + 1e-9 * Math.max(1, score));
						}
					}
				}
			}
		}
		assertTrue(checked > 100);
	}

	@Test
	public void noSpecialItemBeatsTheSetupsFound() throws Exception
	{
		assertNoSpecialItemBeatsTheSetups(false, false);
	}

	@Test
	public void noSpecialItemBeatsTheSetupsOnASlayerTask() throws Exception
	{
		assertNoSpecialItemBeatsTheSetups(false, true);
	}

	@Test
	public void noSpecialItemBeatsTheSetupsFromOwnedGear() throws Exception
	{
		assertNoSpecialItemBeatsTheSetups(true, false);
	}
}
