package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Assume;
import org.junit.Test;

/**
 * Every boss in the catalogue, each class, a 10m budget ({@link #price}) and best in slot, with the kill's specials: each search's
 * time, top figure and returned list. Off unless -Dbgs.bench.out names the CSV to write; -Dbgs.bench.shard=i/n runs
 * every n-th search from the i-th, -Dbgs.bench.specs=false leaves out the kill's specials and -Dbgs.bench.only=name;name limits the bosses.
 */
public class SingleSearchBench
{
	@Test
	public void bench() throws Exception
	{
		String path = System.getProperty("bgs.bench.out");
		Assume.assumeTrue(path != null);
		String only = System.getProperty("bgs.bench.only");
		String[] shard = System.getProperty("bgs.bench.shard", "0/1").split("/");
		int part = Integer.parseInt(shard[0]);
		int parts = Integer.parseInt(shard[1]);
		boolean specs = Boolean.parseBoolean(System.getProperty("bgs.bench.specs", "true"));
		GameData data = WikiGameData.get(RuneLiteAPI.GSON).gameData(RuneLiteAPI.GSON);
		WikiMonsters wiki = WikiMonsters.get(RuneLiteAPI.GSON);
		Set<String> seen = new HashSet<>();
		int index = 0;
		try (PrintWriter out = new PrintWriter(new FileWriter(path)))
		{
			out.println("monster,mode,class,ms,dps,ordinary,list");
			for (MonsterSummary sum : data.getMonsters())
			{
				if (!sum.isBoss() || !seen.add(sum.getName())
					|| only != null && Arrays.stream(only.split(";")).noneMatch(sum.getName()::equals))
					continue;
				Monster m = wiki.monster(sum.getName());
				if (m == null || m.getHitpoints() <= 0)
					continue;
				CombatContext ctx = new CombatContext(m, PlayerLevels.maxed(), false, true, null);
				for (SearchMode mode : new SearchMode[]{SearchMode.BUDGET, SearchMode.UNLIMITED})
				{
					for (CombatClass cls : CombatClass.values())
					{
						if (index++ % parts != part)
							continue;
						OptimizerSettings s = OptimizerSettings.builder().mode(mode).budget(10_000_000).killSpecials(specs)
							.spellbooks(new HashSet<>(Arrays.asList("standard", "arceuus", "ancient"))).build();
						long t0 = System.nanoTime();
						List<SetupResult> list = new Optimizer(data, ctx, s, id -> false, SingleSearchBench::price)
							.optimize(cls, () -> false);
						long ms = (System.nanoTime() - t0) / 1_000_000;
						out.printf(Locale.ROOT, "\"%s\",%s,%s,%d,%.12f,%.12f,\"%s\"%n", sum.getName(), mode, cls, ms,
							list.isEmpty() ? 0 : list.get(0).getDps().getDps(),
							list.isEmpty() ? 0 : DpsCalculator.calculate(list.get(0).getLoadout(), ctx).getDps(), describe(list));
						out.flush();
					}
				}
			}
		}
	}

	/** Offline prices are all zero: a fixed pseudo-random price up to 20m per item gives the budget something to trade. */
	static long price(GearItem item)
	{
		return (item.getId() * 2654435761L >>> 8) % 20_000_000;
	}

	private static String describe(List<SetupResult> list)
	{
		return list.stream().map(s -> s.getLoadout().getWeapon().getName() + "=" + String.format(Locale.ROOT, "%.6f",
			s.getDps().getDps()) + "/" + s.getBuyCost()).collect(Collectors.joining(";")).replace("\"", "'")
			+ (list.isEmpty() || !Boolean.getBoolean("bgs.bench.gear") ? "" : " " + list.get(0).getLoadout().getStyle() + " "
			+ list.get(0).getLoadout().getSpell() + " " + Arrays.stream(Slot.values()).map(slot -> list.get(0).getLoadout().get(slot))
			.filter(Objects::nonNull).map(GearItem::getName).collect(Collectors.joining(", ")).replace("\"", "'"));
	}
}
