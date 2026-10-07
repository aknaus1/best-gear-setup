package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.CombatContext;
import com.bestgearsetup.calc.Optimizer;
import com.bestgearsetup.calc.OptimizerSettings;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.SearchDepth;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;

/**
 * Wall-clock time of whole searches through {@link SearchPool}, sequential versus the spare cores of a 4-thread
 * and of this machine, with every returned setup compared. Skipped unless BGS_BENCH is set.
 */
public class SearchPoolBenchmark
{
	private static final List<AttackStyle.Type> TYPES = Arrays.asList(AttackStyle.Type.STAB, AttackStyle.Type.SLASH,
		AttackStyle.Type.CRUSH, AttackStyle.Type.RANGED, AttackStyle.Type.MAGIC);

	@Test
	public void bench() throws Exception
	{
		Assume.assumeTrue(System.getenv("BGS_BENCH") != null);
		Gson gson = new Gson();
		OwnershipRules.init(gson);
		GameData data = WikiGameData.get(gson).gameData(gson);
		int cores = Runtime.getRuntime().availableProcessors();
		for (int npc : new int[]{265, 415, 8061})
		{
			Monster monster = WikiMonsters.get(gson).monster(data.matchNpc(npc, null, 0).getName());
			CombatContext ctx = new CombatContext(monster, PlayerLevels.maxed(), true, true, null);
			for (SearchDepth depth : new SearchDepth[]{SearchDepth.NORMAL, SearchDepth.BEST})
			{
				OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED).depth(depth)
					.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient", "arceuus"))).build();
				StringBuilder line = new StringBuilder(String.format("npc %-5d %-15s", npc, depth));
				String reference = null;
				for (int processors : new int[]{1, 4, cores})
				{
					SearchPool pool = new SearchPool(processors, TYPES.size());
					try
					{
						// Warm-up run, then the best of three.
						search(pool, data, ctx, settings);
						long best = Long.MAX_VALUE;
						String fingerprint = null;
						for (int i = 0; i < 3; i++)
						{
							long t0 = System.nanoTime();
							fingerprint = search(pool, data, ctx, settings);
							best = Math.min(best, (System.nanoTime() - t0) / 1_000_000);
						}
						if (reference == null)
						{
							reference = fingerprint;
						}
						assertEquals("results differ with " + processors + " processors", reference, fingerprint);
						line.append(String.format("  %2d cpu (%d threads)=%5dms", processors, pool.helperCount() + 1, best));
					}
					finally
					{
						pool.shutdown();
					}
				}
				System.out.println(line);
			}
		}
	}

	private static String search(SearchPool pool, GameData data, CombatContext ctx, OptimizerSettings settings)
		throws InterruptedException
	{
		List<List<SetupResult>> tabs = pool.run(TYPES, type ->
		{
			CombatClass cls = type == AttackStyle.Type.RANGED ? CombatClass.RANGED
				: type == AttackStyle.Type.MAGIC ? CombatClass.MAGIC : CombatClass.MELEE;
			return new Optimizer(data, ctx, settings.toBuilder().styles(EnumSet.of(type)).build(), id -> false,
				item -> item.getPrice()).optimize(cls, () -> false);
		}, () -> false, () -> false);
		List<String> out = new ArrayList<>();
		for (List<SetupResult> tab : tabs)
		{
			for (SetupResult r : tab)
			{
				StringBuilder s = new StringBuilder(String.format("%.9f", r.getDps().getDps()));
				for (Slot slot : Slot.values())
				{
					s.append(',').append(r.getLoadout().get(slot) == null ? 0 : r.getLoadout().get(slot).getId());
				}
				out.add(s.toString());
			}
		}
		return String.join("|", out);
	}
}
