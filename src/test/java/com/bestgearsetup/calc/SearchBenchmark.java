package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assume;
import org.junit.Test;

/**
 * Timing and sampling-profile harness for the gear search. Skipped unless the BGS_BENCH environment variable is
 * set ("all", or one SearchDepth name); prints per-tab times, the top result's DPS, and the hottest methods.
 */
public class SearchBenchmark
{
	@Test
	public void bench() throws Exception
	{
		Assume.assumeTrue(System.getenv("BGS_BENCH") != null);
		Gson gson = new Gson();
		GameData data = WikiGameData.get(gson).gameData(gson);
		int[] npcs = {265, 415, 8061};
		SearchDepth[] depths = {SearchDepth.FAST, SearchDepth.NORMAL, SearchDepth.BEST};
		SearchMode[] modes = {SearchMode.UNLIMITED, SearchMode.BUDGET};
		String only = System.getenv("BGS_BENCH");
		Map<String, Integer> self = new HashMap<>();
		Map<String, Integer> incl = new HashMap<>();
		int[] total = {0};
		for (int npc : npcs)
		{
			Monster monster = WikiMonsters.get(gson).monster(data.matchNpc(npc, null, 0).getName());
			CombatContext ctx = new CombatContext(monster, PlayerLevels.maxed(), true, true, TestData.piety());
			for (SearchMode mode : modes)
			{
				for (SearchDepth depth : depths)
				{
					if (!only.equals("all") && !only.equals(depth.name()))
					{
						continue;
					}
					StringBuilder line = new StringBuilder(String.format("npc %-5d %-9s %-6s", npc, mode, depth));
					long all = 0;
					com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
					long allocated = mx.getThreadAllocatedBytes(Thread.currentThread().getId());
					for (AttackStyle.Type type : new AttackStyle.Type[]{AttackStyle.Type.STAB, AttackStyle.Type.SLASH,
						AttackStyle.Type.CRUSH, AttackStyle.Type.RANGED, AttackStyle.Type.MAGIC})
					{
						OptimizerSettings s = OptimizerSettings.builder().mode(mode).budget(20_000_000).depth(depth)
							.spellbooks(new HashSet<>(java.util.Arrays.asList("standard", "ancient", "arceuus")))
							.styles(EnumSet.of(type)).build();
						CombatClass cls = type == AttackStyle.Type.RANGED ? CombatClass.RANGED
							: type == AttackStyle.Type.MAGIC ? CombatClass.MAGIC : CombatClass.MELEE;
						Thread target = Thread.currentThread();
						java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
						Thread sampler = new Thread(() ->
						{
							while (!done.get())
							{
								StackTraceElement[] st = target.getStackTrace();
								Set<String> seen = new HashSet<>();
								String top = null;
								for (StackTraceElement e : st)
								{
									if (e.getClassName().startsWith("com.bestgearsetup") && !e.getClassName().contains("Benchmark"))
									{
										String k = e.getClassName().replace("com.bestgearsetup.", "") + "." + e.getMethodName();
										if (top == null)
										{
											top = k;
										}
										if (seen.add(k))
										{
											synchronized (incl)
											{
												incl.merge(k, 1, Integer::sum);
											}
										}
									}
								}
								if (top != null)
								{
									synchronized (incl)
									{
										self.merge(top, 1, Integer::sum);
										total[0]++;
									}
								}
								try
								{
									Thread.sleep(1);
								}
								catch (InterruptedException ex)
								{
									return;
								}
							}
						});
						sampler.setDaemon(true);
						sampler.start();
						long t0 = System.nanoTime();
						List<SetupResult> r = new Optimizer(data, ctx, s, id -> false, item -> item.getPrice())
							.optimize(cls, () -> false);
						long ms = (System.nanoTime() - t0) / 1_000_000;
						done.set(true);
						sampler.join();
						all += ms;
						line.append(String.format(" %s=%5dms(%.2f)", type, ms, r.isEmpty() ? 0 : r.get(0).getDps().getDps()));
					}
					line.append(String.format("  total=%dms alloc=%dMB", all,
						(mx.getThreadAllocatedBytes(Thread.currentThread().getId()) - allocated) >> 20));
					System.out.println(line);
				}
			}
		}
		System.out.println("samples " + total[0]);
		System.out.println("--- self");
		self.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(30)
			.forEach(e -> System.out.printf("%5.1f%% %s%n", 100.0 * e.getValue() / total[0], e.getKey()));
		System.out.println("--- inclusive");
		incl.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(40)
			.forEach(e -> System.out.printf("%5.1f%% %s%n", 100.0 * e.getValue() / total[0], e.getKey()));
	}
}
