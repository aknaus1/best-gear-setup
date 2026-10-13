package com.bestgearsetup;

import static org.mockito.Mockito.mock;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.SearchDepth;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.ui.BestGearSetupPanel;
import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Test;

/** Temporary benchmark: -Dbgs.bench=TOA (or COX_CM/TOB) -Dbgs.benchMode=OWNED_ONLY|UNLIMITED -Dbgs.benchDepth=NORMAL. */
public class RaidBenchTmp
{
	static final String[] BANK = {"abyssal whip", "abyssal tentacle", "dragon hunter lance", "ghrazi rapier", "inquisitor's mace",
		"scythe of vitur", "osmumten's fang", "dragon warhammer", "bandos godsword", "elder maul",
		"twisted bow", "toxic blowpipe", "bow of faerdhinen (c)", "dragon crossbow", "armadyl crossbow", "zaryte crossbow",
		"trident of the swamp", "sanguinesti staff", "tumeken's shadow", "harmonised nightmare staff", "kodai wand",
		"dragon arrow", "amethyst arrow", "ruby dragon bolts (e)", "diamond dragon bolts (e)", "dragon bolts",
		"neitiznot faceguard", "serpentine helm", "torva full helm", "masori mask (f)", "ancestral hat", "virtus mask",
		"bandos chestplate", "torva platebody", "masori body (f)", "ancestral robe top", "armadyl chestplate",
		"bandos tassets", "torva platelegs", "masori chaps (f)", "ancestral robe bottom", "armadyl chainskirt",
		"ferocious gloves", "barrows gloves", "zaryte vambraces", "tormented bracelet",
		"primordial boots", "pegasian boots", "eternal boots",
		"amulet of torture", "necklace of anguish", "occult necklace", "amulet of rancour", "amulet of fury",
		"infernal cape", "fire cape", "ava's assembler", "imbued saradomin cape", "dizana's quiver",
		"avernic defender", "dragon defender", "twisted buckler", "elidinis' ward (f)", "arcane spirit shield", "book of the dead",
		"ultor ring", "berserker ring (i)", "venator ring", "magus ring", "lightbearer", "ring of suffering (i)",
		"rune pouch", "divine rune pouch", "dawnbringer", "dragon pickaxe"};

	@Test public void bench() throws Exception
	{
		String which = System.getProperty("bgs.bench");
		Assume.assumeTrue(which != null);
		RaidPlanType type = RaidPlanType.valueOf(which);
		SearchMode mode = SearchMode.valueOf(System.getProperty("bgs.benchMode", "OWNED_ONLY"));
		SearchDepth depth = SearchDepth.valueOf(System.getProperty("bgs.benchDepth", "NORMAL"));
		int limitOverride = Integer.getInteger("bgs.benchLimit", -1);
		GameDataLoader loader = new GameDataLoader(new Gson());
		GameData data = loader.loadGameData();
		Set<String> names = new HashSet<>(Arrays.asList(BANK));
		Set<Integer> owned = new HashSet<>();
		Map<Integer, Long> quantities = new HashMap<>();
		Set<String> found = new HashSet<>();
		for (Slot slot : Slot.values())
		{
			for (GearItem item : data.getItems(slot))
			{
				if (names.contains(item.getName())) { owned.add(item.getId()); quantities.put(item.getId(), 1000L); found.add(item.getName()); }
			}
		}
		Set<String> missing = new TreeSet<>(names); missing.removeAll(found);
		System.out.println("BENCH missing names: " + missing + " owned ids=" + owned.size());
		BestGearSetupConfig config = new BestGearSetupConfig()
		{
			@Override public SearchMode mode() { return mode; }
			@Override public SearchDepth searchDepth() { return depth; }
		};
		BestGearSetupPlugin plugin = new BestGearSetupPlugin(); plugin.config = config;
		plugin.panel = mock(BestGearSetupPanel.class);
		Field api = BestGearSetupPlugin.class.getDeclaredField("api"); api.setAccessible(true); api.set(plugin, loader);
		Method compute = Arrays.stream(BestGearSetupPlugin.class.getDeclaredMethods()).filter(m -> m.getName().equals("computeSearch")).findFirst().get();
		compute.setAccessible(true);
		Method raidSettings = Arrays.stream(BestGearSetupPlugin.class.getDeclaredMethods()).filter(m -> m.getName().equals("raidSettings")).findFirst().get();
		raidSettings.setAccessible(true);
		List<RaidEncounter> encounters = type.encounters(data, (com.bestgearsetup.calc.RaidScaling.Settings) raidSettings.invoke(plugin, PlayerLevels.maxed(), 99, config));
		AtomicInteger searches = new AtomicInteger();
		AtomicLong searchNanos = new AtomicLong();
		Map<Integer, Integer> perPass = new TreeMap<>();
		RaidConsolidator.Search search = (index, excluded, pass) ->
		{
			RaidEncounter encounter = encounters.get(index);
			Map<String, Object> overrides = new HashMap<>();
			overrides.put("coxChallengeMode", type == RaidPlanType.COX_CM);
			overrides.put("encounterPhase", encounter.getPhase());
			overrides.put("onSlayerTask", false);
			overrides.put("wilderness", false);
			overrides.put("aoe", false);
			if (encounter == encounters.get(0)) { overrides.put("raidPotions", false); }
			for (String key : new String[]{"specElderMaul", "specDwh", "specEmberlight", "specArclight", "specTonalztics",
				"specBgsDamage", "specSeercullDamage", "specAyakDamage"}) { overrides.put(key, 0); }
			overrides.put("specVulnerability", false);
			overrides.put("specTomeOfWater", false);
			Set<Integer> encounterOwned = new HashSet<>(owned);
			long t = System.nanoTime();
			try
			{
				return (SearchResults) compute.invoke(plugin, data, encounter.getTarget(), PlayerLevels.maxed(), false, Collections.emptySet(), true,
					false, 99, Collections.emptyMap(), encounterOwned, quantities, 0, SearchAssumptions.capture(config, overrides),
					new LiveRaid(), Collections.emptyList(), encounter.getStyles(), excluded, config.raidSpellbook(), (java.util.function.IntConsumer) p -> { });
			}
			catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
			finally { searchNanos.addAndGet(System.nanoTime() - t); searches.incrementAndGet(); perPass.merge(pass, 1, Integer::sum); }
		};
		long t0 = System.nanoTime();
		List<SearchResults> results = new ArrayList<>();
		for (int i = 0; i < encounters.size(); i++)
		{
			long t = System.nanoTime();
			SearchResults r = search.run(i, Collections.emptySet(), 0);
			if (r.isEmpty() || r.hasBlockingLock()) { System.out.println("BENCH EMPTY " + encounters.get(i).getLabel()); return; }
			int cands = r.getByType().values().stream().mapToInt(List::size).sum();
			System.out.printf("BENCH enc %-28s %6d ms  candidates=%d%n", encounters.get(i).getLabel(), (System.nanoTime() - t) / 1_000_000, cands);
			results.add(r);
		}
		long t1 = System.nanoTime();
		System.out.printf("BENCH %s mode=%s depth=%s encounters=%d initial searches=%d ms%n", type, mode, depth, encounters.size(), (t1 - t0) / 1_000_000);
		int limit = limitOverride >= 0 ? limitOverride : config.raidEquipmentLimit();
		long budget = mode == SearchMode.BUDGET ? Budget.parse(config.budget()) : Long.MAX_VALUE;
		java.util.function.IntPredicate kitOwns = owned::contains;
		java.util.function.IntToLongFunction qty = id -> quantities.getOrDefault(id, 0L);
		List<SetupResult> bestSetups = RaidConsolidator.best(results);
		List<SearchResults> independentResults = java.util.stream.IntStream.range(0, results.size())
			.mapToObj(i -> results.get(i).withSetup(bestSetups.get(i))).collect(Collectors.toList());
		long bestOld = Long.MAX_VALUE, bestNew = Long.MAX_VALUE;
		String oldPlan = null, newPlan = null;
		int oldSearches = 0, newSearches = 0;
		for (int rep = 0; rep < Integer.getInteger("bgs.benchRepeat", 3); rep++)
		{
			for (boolean optimised : new boolean[]{false, true})
			{
				int before = searches.get();
				long start = System.nanoTime();
				RaidPlan selected, consolidated = null;
				if (optimised)
				{
					selected = RaidPlanner.select(type, config.raidSpellbook(), encounters, results, limit, budget, kitOwns, item -> -1L, config.ammoCount(), qty, () -> false);
					RaidPlan independent = RaidPlanner.select(type, config.raidSpellbook(), encounters, independentResults, limit, budget, kitOwns, item -> -1L, config.ammoCount(), qty, () -> false);
					if (independent == null)
					{
						consolidated = RaidConsolidator.select(data, type, config.raidSpellbook(), encounters, results, Collections.emptySet(), limit, budget,
							kitOwns, item -> -1L, config.ammoCount(), qty, () -> false,
							selected == null ? Double.POSITIVE_INFINITY : RaidConsolidator.seconds(selected.getSetups(), selected.getResults(), encounters), search);
					}
				}
				else
				{
					selected = RaidOrigTmp.plannerSelect(type, config.raidSpellbook(), encounters, results, limit, budget, kitOwns, item -> -1L, config.ammoCount(), qty, () -> false);
					RaidPlan independent = RaidOrigTmp.plannerSelect(type, config.raidSpellbook(), encounters, independentResults, limit, budget, kitOwns, item -> -1L, config.ammoCount(), qty, () -> false);
					if (independent == null)
					{
						consolidated = RaidOrigTmp.consolidate(data, type, config.raidSpellbook(), encounters, results, Collections.emptySet(), limit, budget,
							kitOwns, item -> -1L, config.ammoCount(), qty, () -> false, search);
					}
				}
				long elapsed = System.nanoTime() - start;
				RaidPlan finalPlan = consolidated != null && (selected == null || RaidConsolidator.seconds(consolidated.getSetups(), consolidated.getResults(), encounters)
					< RaidConsolidator.seconds(selected.getSetups(), selected.getResults(), encounters)) ? consolidated : selected;
				StringBuilder describe = new StringBuilder(finalPlan == null ? "none" : String.format("%.6fs", RaidConsolidator.seconds(finalPlan.getSetups(), finalPlan.getResults(), encounters)));
				if (finalPlan != null)
				{
					for (SetupResult setup : finalPlan.getSetups())
					{ describe.append(" | ").append(RaidPlan.gear(setup).values().stream().map(GearItem::getName).collect(Collectors.joining(","))); }
					describe.append(" || inv ").append(finalPlan.getInventory().stream().map(RaidPlan.InventoryItem::getName).collect(Collectors.joining(",")));
				}
				if (optimised) { bestNew = Math.min(bestNew, elapsed); newPlan = describe.toString(); newSearches = searches.get() - before; }
				else { bestOld = Math.min(bestOld, elapsed); oldPlan = describe.toString(); oldSearches = searches.get() - before; }
				System.out.printf("BENCH rep %d %s: %d ms%n", rep, optimised ? "new" : "old", elapsed / 1_000_000);
			}
		}
		System.out.printf("BENCH RESULT %s %s: old %d ms (%d searches) -> new %d ms (%d searches); same plan=%s; %s%n", type, mode,
			bestOld / 1_000_000, oldSearches, bestNew / 1_000_000, newSearches, oldPlan.equals(newPlan), newPlan.substring(0, Math.min(14, newPlan.length())));
		if (!oldPlan.equals(newPlan)) { System.out.println("BENCH OLD " + oldPlan); System.out.println("BENCH NEW " + newPlan); }
	}
}
