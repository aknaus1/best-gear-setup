package com.bestgearsetup;

import com.bestgearsetup.calc.*;
import com.bestgearsetup.data.*;
import com.google.gson.Gson;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;
import java.util.function.ToLongFunction;
import javax.swing.SwingUtilities;
import org.junit.Test;
import net.runelite.client.config.ConfigItem;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * Opt-in local diagnostic: reads only plugin fields, never exports profile contents. Enable with
 * -Dbgs.planker.diagnostic=true. Optional overrides: -Dbgs.planker.type=COX_CM|TOB, -Dbgs.planker.limit=N,
 * -Dbgs.planker.tobRole=MELEE_DPS|..., -Dbgs.planker.party=N, -Dbgs.planker.probe=14,16,18 (re-runs the captured
 * kit planner input at other equipment limits), -Dbgs.planker.label=name (output file build/diagnostics/name.txt),
 * -Dbgs.planker.profile=RS_PROFILE_KEY and -Dbgs.planker.settings=SETTINGS_FILENAME for another saved account.
 */
public class PlankerCoxDiagnosticTest
{
	private static final Map<String, String> OVERRIDES = new HashMap<>();
	/** Inputs of each top-level RaidPlanner.select call: book, encounters, results, owns, price, ammo, quantities. */
	private static final List<Object[]> CAPTURED = new ArrayList<>();

	@Test public void savedProfile() throws Exception
	{
		org.junit.Assume.assumeTrue(Boolean.getBoolean("bgs.planker.diagnostic"));
		RaidPlanType raidType = RaidPlanType.valueOf(System.getProperty("bgs.planker.type", "COX_CM"));
		OVERRIDES.clear(); CAPTURED.clear();
		if (System.getProperty("bgs.planker.limit") != null) { OVERRIDES.put("raidEquipmentLimit", System.getProperty("bgs.planker.limit")); }
		if (System.getProperty("bgs.planker.tobRole") != null) { OVERRIDES.put("tobRole", System.getProperty("bgs.planker.tobRole")); }
		if (System.getProperty("bgs.planker.party") != null) { OVERRIDES.put("raidPartySize", System.getProperty("bgs.planker.party")); }
		if (System.getProperty("bgs.planker.excluded") != null) { OVERRIDES.put("excluded", System.getProperty("bgs.planker.excluded")); }
		OwnershipRules.init(net.runelite.http.api.RuneLiteAPI.GSON);
		Properties character = pluginProperties(Paths.get(System.getProperty("user.home"), ".runelite", "profiles2", "$rsprofile--1.properties"));
		Properties saved = pluginProperties(Paths.get(System.getProperty("user.home"), ".runelite", "profiles2",
			System.getProperty("bgs.planker.settings", "Planker-454709730071000.properties")));
		BestGearSetupConfig defaults = new BestGearSetupConfig() {};
		BestGearSetupConfig config = (BestGearSetupConfig) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{BestGearSetupConfig.class}, (proxy, method, args) -> {
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			String value = item == null ? null : OVERRIDES.containsKey(item.keyName()) ? OVERRIDES.get(item.keyName())
				: saved.getProperty("bestgearsetup." + item.keyName());
			if (value == null) { return method.invoke(defaults, args); }
			Class<?> type = method.getReturnType();
			if (type == boolean.class) { return Boolean.parseBoolean(value); }
			if (type == int.class) { return Integer.parseInt(value); }
			if (type == double.class) { return Double.parseDouble(value); }
			if (type.isEnum()) { return Enum.valueOf((Class) type, value); }
			if (type == String.class) { return value; }
			return method.invoke(defaults, args);
		});
		String prefix = "bestgearsetup.rsprofile." + System.getProperty("bgs.planker.profile", "av_BWTSC") + ".";
		Map<Integer, Long> quantities = OwnedItems.combine(Arrays.asList(OwnedItems.parseQuantities(character.getProperty(prefix + "ownedBank")),
			OwnedItems.parseQuantities(character.getProperty(prefix + "ownedInventory")), OwnedItems.parseQuantities(character.getProperty(prefix + "ownedWorn"))),
			OwnedItems.parseQuantities(character.getProperty(prefix + "ownedManual")).keySet());
		Set<Integer> owned = new HashSet<>(OwnedItems.expand(quantities.keySet()));
		Gson gson = new Gson(); GameDataLoader loader = new GameDataLoader(gson); GameData data = loader.loadGameData();
		// Folded cosmetic items share stats; expose cache-name-independent copies for owned exact identities.
		Map<Integer, String> names = new HashMap<>(); Map<Integer, Boolean> tradeable = new HashMap<>();
		for (Slot slot : Slot.values()) { for (GearItem item : data.getItems(slot)) { for (int id : item.getVariants()) { names.putIfAbsent(id, item.getName()); tradeable.putIfAbsent(id, item.isTradeable()); } } }
		data.addVariantItems(gson, names::get, id -> tradeable.getOrDefault(id, false));
		String[] levelValues = character.getProperty(prefix + "levels", "83,88,80,82,77,76,87,70").split(",");
		int[] lv = Arrays.stream(levelValues).mapToInt(Integer::parseInt).toArray();
		PlayerLevels levels = new PlayerLevels(lv[0], lv[1], lv[2], lv[3], lv[4], lv[5], lv[6], lv[7]);
		StringBuilder report = new StringBuilder("Saved profile levels=" + levels + "; type=" + raidType + " party=" + config.raidPartySize()
			+ " limit=" + config.raidEquipmentLimit() + " tobRole=" + config.tobRole() + " spellbook=" + config.raidSpellbook()
			+ " CM=" + config.coxChallengeMode() + " mode=" + config.mode() + "\n");
		for (GearItem weapon : data.getItems(Slot.WEAPON))
		{
			if (weapon.getName().equals("elder maul") || weapon.getName().equals("dragon claws") || weapon.getName().equals("crystal halberd"))
			{
				report.append("Spec candidate ").append(weapon.getName()).append(" id=").append(weapon.getId())
					.append(" owned=").append(owned.contains(weapon.getId()) || weapon.getOwnershipVariants().stream().anyMatch(owned::contains)).append('\n');
			}
		}
		for (GearItem cape : data.getItems(Slot.CAPE))
		{
			if (cape.getName().equals("fire cape") || cape.getName().equals("mythical cape") || cape.getName().equals("infernal cape"))
			{
				report.append(cape.getName()).append(" id=").append(cape.getId()).append(" owned=").append(owned.contains(cape.getId()))
					.append(" familyOwned=").append(cape.getOwnershipVariants().stream().anyMatch(owned::contains))
					.append(" atk(st/sl/cr/rng/mag)=").append(cape.getStabBonus()).append('/').append(cape.getSlashBonus()).append('/').append(cape.getCrushBonus())
					.append('/').append(cape.getRangedBonus()).append('/').append(cape.getMagicBonus())
					.append(" str=").append(cape.getMeleeStr()).append(" rstr=").append(cape.getRangedStr()).append(" mdmg=").append(cape.getMagicStr())
					.append(" pray=").append(cape.getPrayerBonus()).append(" atkReq=").append(cape.getAttackReq()).append('\n');
			}
		}
		BestGearSetupPlugin plugin = new BestGearSetupPlugin(); plugin.config = config; plugin.panel = mock(com.bestgearsetup.ui.BestGearSetupPanel.class);
		Field api = BestGearSetupPlugin.class.getDeclaredField("api"); api.setAccessible(true); api.set(plugin, loader);
		RaidPlan[] found = {null}; doAnswer(call -> { found[0] = call.getArgument(0); return null; }).when(plugin.panel).showRaidResults(any(RaidPlan.class), any());
		Method search = Arrays.stream(BestGearSetupPlugin.class.getDeclaredMethods()).filter(m -> m.getName().equals("runRaidSearch")).findFirst().get(); search.setAccessible(true);
		List<String> stages = Collections.synchronizedList(new ArrayList<>());
		long started = System.nanoTime();
		// Search activity messages (e.g. "Packing raid kit", "Comparing shared gear") with their time since start.
		doAnswer(call -> { stages.add("activity +" + (System.nanoTime() - started) / 1_000_000 + "ms: " + call.getArgument(0)); return null; })
			.when(plugin.panel).showSearchActivity(anyString());
		Throwable failure = null;
		// Capture mode records the first RaidPlanner.select input (for -Dbgs.planker.probe) and then removes the static
		// mock at once: while active, Mockito's static dispatch walks the stack on every RaidPlanner call.
		org.mockito.MockedStatic<RaidPlanner>[] planner = new org.mockito.MockedStatic[1];
		if (Boolean.getBoolean("bgs.planker.capture"))
		{
			planner[0] = mockStatic(RaidPlanner.class, CALLS_REAL_METHODS);
			planner[0].when(() -> RaidPlanner.select(any(), any(), anyList(), anyList(), anyInt(), anyLong(), any(), any(), anyInt(), any(), any(),
				any(java.util.function.DoubleConsumer.class))).thenAnswer(call ->
				{
					planner[0].close(); planner[0] = null;
					CAPTURED.add(new Object[]{call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(6), call.getArgument(7),
						call.getArgument(8), call.getArgument(9)});
					long t = System.nanoTime();
					RaidPlan r = RaidPlanner.select(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4),
						call.getArgument(5), call.getArgument(6), call.getArgument(7), call.getArgument(8), call.getArgument(9), call.getArgument(10),
						call.getArgument(11));
					List<RaidEncounter> encs = call.getArgument(2);
					stages.add("first RaidPlanner.select book=" + call.getArgument(1) + " limit=" + call.getArgument(4) + " candidates/enc="
						+ candidateCounts(call.getArgument(3)) + " -> " + (r == null ? "null" : Math.round(RaidConsolidator.seconds(r.getSetups(), r.getResults(), encs)) + "s")
						+ " in " + (System.nanoTime() - t) / 1_000_000 + "ms");
					return r;
				});
		}
		try
		{
			search.invoke(plugin, data, raidType.encounters(data).get(0).getTarget(), levels, true,
				new HashSet<>(Arrays.asList(character.getProperty(prefix + "prayerUnlocks", "chivalry,piety,rigour").split(","))),
				Boolean.parseBoolean(character.getProperty(prefix + "detectedKandarinHard", "false")),
				levelValues.length > 8 ? lv[8] : 70, Collections.emptyMap(), owned, quantities, 0,
				config, new LiveRaid(), Collections.emptyList(), raidType, (java.util.function.DoubleConsumer) percent -> {});
		}
		catch (Throwable e) { failure = e; }
		finally { if (planner[0] != null) { planner[0].close(); } }
		SwingUtilities.invokeAndWait(() -> {});
		long peak = 0;
		for (java.lang.management.MemoryPoolMXBean pool : java.lang.management.ManagementFactory.getMemoryPoolMXBeans())
		{
			if (pool.getType() == java.lang.management.MemoryType.HEAP) { peak += pool.getPeakUsage().getUsed(); }
		}
		report.append("Elapsed ms=").append((System.nanoTime() - started) / 1_000_000).append(" maxHeapMB=").append(Runtime.getRuntime().maxMemory() >> 20)
			.append(" peakHeapPoolsMB~").append(peak >> 20).append('\n');
		stages.forEach(stage -> report.append("  stage: ").append(stage).append('\n'));

		if (failure != null)
		{
			report.append("FAILED\n");
			for (Throwable cause = failure; cause != null; cause = cause.getCause())
			{
				report.append("  cause: ").append(cause.getClass().getName()).append(": ").append(cause.getMessage()).append('\n');
				if (cause.getCause() == null)
				{
					StackTraceElement[] trace = cause.getStackTrace();
					for (int k = 0; k < Math.min(25, trace.length); k++) { report.append("    at ").append(trace[k]).append('\n'); }
				}
			}
		}
		else if (found[0] == null) { report.append("NO RESULT: runRaidSearch returned without publishing a plan\n"); }
		else
		{
			describe(found[0], data, levels, owned, report, config.raidPartySize());
			Path graph = Paths.get("build", "diagnostics", System.getProperty("bgs.planker.label", "planker-cox") + ".json");
			Files.createDirectories(graph.getParent());
			Files.writeString(graph, new SearchResultCodec(gson).encode(found[0]).toString());
		}
		limitProbe(raidType, report);
		Path output = Paths.get("build", "diagnostics", System.getProperty("bgs.planker.label", "planker-cox") + ".txt");
		Files.createDirectories(output.getParent());
		Files.write(output, report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		System.out.println(report);
		if (failure != null) { throw new AssertionError("Saved profile search failed; see " + output, failure); }
		org.junit.Assert.assertNotNull("Saved profile search must publish a plan", found[0]);
	}

	private static String candidateCounts(List<SearchResults> results)
	{
		StringBuilder out = new StringBuilder();
		for (SearchResults result : results) { out.append(result.getByType().values().stream().mapToInt(List::size).sum()).append(','); }
		return out.toString();
	}

	/** Re-run the captured planner inputs (first capture per book) at other equipment limits. */
	@SuppressWarnings("unchecked")
	private static void limitProbe(RaidPlanType type, StringBuilder report)
	{
		String probe = System.getProperty("bgs.planker.probe");
		if (probe == null || probe.isEmpty()) { return; }
		Set<Object> books = new HashSet<>();
		for (Object[] input : CAPTURED)
		{
			if (!books.add(input[0])) { continue; }
			for (String limitText : probe.split(","))
			{
				int limit = Integer.parseInt(limitText.trim());
				long t = System.nanoTime();
				List<RaidEncounter> encs = (List<RaidEncounter>) input[1];
				RaidPlan plan = RaidPlanner.select(type, (RaidSpellbook) input[0], encs, (List<SearchResults>) input[2], limit, Long.MAX_VALUE,
					(IntPredicate) input[3], (ToLongFunction<GearItem>) input[4], (Integer) input[5], (IntToLongFunction) input[6], () -> false);
				report.append("  probe book=").append(input[0]).append(" limit=").append(limit).append(" -> ");
				if (plan == null) { report.append("null"); }
				else
				{
					report.append(Math.round(RaidConsolidator.seconds(plan.getSetups(), plan.getResults(), encs))).append("s carried=")
						.append(RaidPlan.carriedEquipment(plan.getEquipment().stream().map(GearItem::getId).collect(java.util.stream.Collectors.toSet()),
							plan.getStartingSetup()))
						.append(" limitSlots=").append(RaidPlan.limitedSlots(plan.getInventory())).append(" inventory=").append(inventorySlots(plan));
				}
				report.append(" in ").append((System.nanoTime() - t) / 1_000_000).append("ms\n");
			}
		}
	}

	private static int inventorySlots(RaidPlan plan)
	{
		// Each estimated boost bottle is its own entry; pouch contents take no slot.
		return plan.getInventory().size();
	}

	private static void describe(RaidPlan plan, GameData data, PlayerLevels levels, Set<Integer> owned, StringBuilder report, int party)
	{
		Set<Integer> gearIds = new HashSet<>();
		plan.getEquipment().forEach(item -> gearIds.add(item.getId()));
		report.append("Spellbook=").append(plan.getSpellbook()).append(" estimated raid seconds=").append(plan.getEstimatedSeconds())
			.append(" kitEquipment=").append(plan.getEquipment().size())
			.append(" carriedEquipment(excl. starting worn)=").append(RaidPlan.carriedEquipment(gearIds, plan.getStartingSetup()))
			.append(" limitSlots=").append(RaidPlan.limitedSlots(plan.getInventory())).append('/').append(plan.getEquipmentLimit())
			.append(" start=").append(plan.getStartingEncounter().getLabel()).append('\n');
		report.append("Maximum inventory slots=").append(RaidPlan.maximumInventory(plan.getSetups(), plan.getResults(), owned::contains))
			.append(" plan inventory entries/slots=").append(plan.getInventory().size()).append('/').append(inventorySlots(plan)).append('\n');
		report.append("Equipment: ");
		for (GearItem item : plan.getEquipment()) { report.append(item.getName()).append(", "); }
		report.append('\n');
		Map<RaidPlan.InventoryGroup, List<String>> groups = new EnumMap<>(RaidPlan.InventoryGroup.class);
		for (RaidPlan.InventoryItem item : plan.getInventory())
		{
			String text = item.getName() + (item.isEquipment() ? "[eq]" : "") + (item.getBottles() > 0 ? "x" + item.getBottles() : "")
				+ (item.getContents().isEmpty() ? "" : "{" + item.getContents().size() + " inside}");
			groups.computeIfAbsent(item.getGroup(), g -> new ArrayList<>()).add(text);
		}
		groups.forEach((group, items) -> report.append("  inv ").append(group).append(": ").append(String.join(", ", items)).append('\n'));
		report.append("Inventory in display order:\n");
		for (int i = 0; i < plan.getInventory().size(); i++)
		{
			RaidPlan.InventoryItem item = plan.getInventory().get(i);
			report.append("  slot ").append(i + 1).append(": ").append(item.getName()).append(" group=").append(item.getGroup())
				.append(" slot=").append(item.getSlot()).append(" uses=").append(item.getUses()).append(" spec=").append(item.getSpecStyle())
				.append(" wornIn=");
			for (int e = 0; e < plan.getEncounters().size(); e++)
			{
				if ((item.getWornIn() & 1L << e) != 0) { report.append(plan.getEncounters().get(e).getLabel()).append("; "); }
			}
			report.append('\n');
		}
		report.append("Notes: ").append(plan.getNotes()).append(" warnings=").append(plan.getWarnings()).append('\n');
		double energy = 100;
		for (int i = 0; i < plan.getEncounters().size(); i++)
		{
			RaidEncounter encounter = plan.getEncounters().get(i); SetupResult setup = plan.getSetups().get(i); SearchResults result = plan.getResults().get(i);
			double after = encounter.energyAfter(energy, result, setup);
			report.append(encounter.getLabel()).append(" phase=").append(encounter.getPhase()).append(encounter.isDisplayOnly() ? " (display-only)" : "")
				.append(" weapon=").append(setup.getLoadout().get(Slot.WEAPON)).append(" cape=").append(setup.getLoadout().get(Slot.CAPE))
				.append(" energyBefore=").append(Math.round(energy * 10) / 10.0).append(" after=").append(Math.round(after * 10) / 10.0)
				.append(" dps=").append(Math.round(setup.getDps().getPrimaryDps() * 100) / 100.0)
				.append(" fullBar=").append(setup.usesFullBar()).append('\n');
			if (encounter.isFreeze())
			{
				report.append("    freeze accuracy=").append(setup.getDps().getAccuracy()).append(" style=")
					.append(setup.getLoadout().getStyle()).append(" spell=").append(setup.getLoadout().getSpell().getName())
					.append(" potions=").append(result.potionsForSetup(setup).stream().map(Potion::getName).collect(java.util.stream.Collectors.toList()))
					.append(" prayers=").append(result.getPrayers()).append('\n');
				int magicAttack = 0;
				for (Slot slot : Slot.values())
				{
					GearItem item = setup.getLoadout().get(slot);
					if (item != null)
					{
						magicAttack += item.getMagicBonus();
						report.append("      ").append(slot).append(": ").append(item.getName()).append(" magic=").append(item.getMagicBonus()).append('\n');
					}
				}
				report.append("    total magic attack=").append(magicAttack).append('\n');
				List<Potion> boosts = result.potionsForSetup(setup);
				CombatContext freezeContext = new CombatContext(result.getMonster(), levels, false,
					levels.getAttack(), levels.getStrength(), levels.getRanged(), boost(boosts, "magic", levels.getMagic()), result.getPrayers(), false)
					.withModifiers(CombatModifiers.builder().freezeCasts(true).build());
				double recalculated = DpsCalculator.calculate(setup.getLoadout(), freezeContext).getAccuracy();
				report.append("    recalculated freeze accuracy=").append(recalculated).append('\n');
				org.junit.Assert.assertEquals("Published freeze accuracy must match its actual equipped gear", setup.getDps().getAccuracy(), recalculated, 1e-9);
				org.junit.Assert.assertNotNull("A freeze must be searched from its worn Maiden gear", setup.getSwitchFrom());
				org.junit.Assert.assertTrue("A freeze cannot depend on manually taking off worn gear",
					FreezeDuty.keepsWornGear(setup.getLoadout(), setup.getSwitchFrom()));
				if (Boolean.getBoolean("bgs.planker.requireFreeze"))
				{ org.junit.Assert.assertEquals("The selected freeze must never splash", 1, recalculated, 1e-9); }
			}
			for (SpecialPlan special : setup.getSpecials())
			{
				report.append("    spec ").append(special.getWeapon().getName()).append(" specs/kill=").append(special.getSpecsPerKill())
					.append(" opening=").append(setup.getSpecial().getOpeningEnergy()).append('\n');
			}
			if (encounter.getLabel().toLowerCase(Locale.ROOT).startsWith("olm melee hand")) { capeComparison(data, levels, result, setup, report, party); }
			if (encounter.getLabel().equals("Maiden") && !Boolean.getBoolean("bgs.planker.noComparisons"))
			{ maidenComparison(data, levels, plan, result, setup, report, party); }
			if (!encounter.isDisplayOnly() && !Double.isNaN(after)) { energy = after; }
		}
	}

	private static int firstIndex(RaidPlan plan, String label)
	{
		for (int i = 0; i < plan.getEncounters().size(); i++) { if (plan.getEncounters().get(i).getLabel().equals(label)) { return i; } }
		return -1;
	}

	private static void capeComparison(GameData data, PlayerLevels levels, SearchResults result, SetupResult setup, StringBuilder report, int party)
	{
		List<Potion> potions = result.potionsForSetup(setup);
		CombatContext ctx = new CombatContext(result.getMonster(), levels, false,
			boost(potions, "attack", levels.getAttack()), boost(potions, "strength", levels.getStrength()),
			boost(potions, "ranged", levels.getRanged()), boost(potions, "magic", levels.getMagic()), result.getPrayers(), false);
		SpecialPlan reducer = setup.getSpecials().stream().filter(p -> p.getDrained() != null).findFirst().orElse(null);
		for (String name : Arrays.asList("fire cape", "mythical cape"))
		{
			GearItem cape = data.getItems(Slot.CAPE).stream().filter(c -> c.getName().equals(name)).findFirst().get();
			Loadout worn = setup.getLoadout().copy(); worn.set(Slot.CAPE, cape);
			DpsResult normal = DpsCalculator.calculate(worn, ctx);
			report.append("  Cape swap ").append(name).append(" ordinaryDps=").append(normal.getPrimaryDps()).append(" max=").append(normal.getMaxHit()).append(" accuracy=").append(normal.getAccuracy());
			if (reducer != null)
			{
				Loadout spec = worn.copy(); spec.set(Slot.WEAPON, reducer.getWeapon()); spec.setStyle(reducer.getStyle());
				CombatContext special = ctx.withModifiers(ctx.getModifiers().toBuilder().specialAttack(true).build());
				SpecialRotation.Fight fight = new SpecialRotation.Fight()
				{
					@Override public DpsResult ordinary(SpecialAttacks drains) { return DpsCalculator.calculate(worn, drained(ctx, drains)); }
					@Override public DpsResult spec(SpecialAttacks drains) { return DpsCalculator.calculate(spec, drained(special, drains)); }
				};
				report.append(" reducerAccuracy=").append(fight.spec(SpecialAttacks.NONE).getAccuracy());
				for (int count = 0; count <= 2; count++)
				{
					report.append(" own").append(count).append("Seconds=").append(SpecialRotation.drainOpenedShared(fight, reducer.getSpecial().getDrain(),
						reducer.getSpecial().getEnergy(), count, result.getStartingHitpoints(), false, party)[0]);
				}
				for (int count = 0; count <= 2; count++)
				{
					report.append(" team").append(count).append("Seconds=").append(SpecialRotation.drainOpenedTeam(fight, reducer.getSpecial().getDrain(),
						reducer.getSpecial().getEnergy(), count, result.getStartingHitpoints(), false, party, fight, reducer.getSpecial().getDrain())[0]);
				}
			}
			report.append('\n');
		}
	}

	/** Maiden's chosen setup against fang/macuahuitl and fire/mythical cape swaps, with team openers and own hammers. */
	private static void maidenComparison(GameData data, PlayerLevels levels, RaidPlan plan, SearchResults result, SetupResult setup,
		StringBuilder report, int party)
	{
		List<Potion> potions = result.potionsForSetup(setup);
		CombatContext ctx = new CombatContext(result.getMonster(), levels, false,
			boost(potions, "attack", levels.getAttack()), boost(potions, "strength", levels.getStrength()),
			boost(potions, "ranged", levels.getRanged()), boost(potions, "magic", levels.getMagic()), result.getPrayers(), false);
		CombatContext special = ctx.withModifiers(ctx.getModifiers().toBuilder().specialAttack(true).build());
		SpecialPlan reducer = plan.getSetups().stream().flatMap(s -> s.getSpecials().stream()).filter(p -> p.getDrained() != null).findFirst().orElse(null);
		SetupResult fangSetup = plan.getSetups().stream().filter(s -> s.getLoadout().getWeapon() != null
			&& s.getLoadout().getWeapon().getName().equals("osmumten's fang")).findFirst().orElse(null);
		GearItem chosenWeapon = setup.getLoadout().getWeapon();
		report.append("  Maiden chosen: ").append(setup.getLoadout()).append(" style=").append(setup.getLoadout().getStyle())
			.append(" planDps=").append(setup.getDps().getPrimaryDps()).append(" hp=").append(result.getStartingHitpoints())
			.append(" def=").append(result.getMonster().getDefenceLevel()).append(" defCrush=").append(result.getMonster().getDefCrush())
			.append(" defSlash=").append(result.getMonster().getDefSlash()).append(" defStab=").append(result.getMonster().getDefStab()).append('\n');
		SpecialRotation.Fight teammate = SpecialRotation.teammateHammer(result.getMonster());
		report.append("  teammate hammer accuracy=").append(teammate.spec(SpecialAttacks.NONE).getAccuracy()).append('\n');
		for (GearItem weapon : Arrays.asList(chosenWeapon, fangSetup == null ? null : fangSetup.getLoadout().getWeapon()))
		{
			if (weapon == null) { continue; }
			for (String name : Arrays.asList("fire cape", "mythical cape"))
			{
				GearItem cape = data.getItems(Slot.CAPE).stream().filter(c -> c.getName().equals(name)).findFirst().get();
				Loadout base = setup.getLoadout().copy();
				base.set(Slot.WEAPON, weapon);
				base.set(Slot.CAPE, cape);
				if (weapon != chosenWeapon && fangSetup != null) { base.set(Slot.SHIELD, fangSetup.getLoadout().get(Slot.SHIELD)); }
				if (weapon.isTwoHanded()) { base.set(Slot.SHIELD, null); }
				Loadout worn = null;
				DpsResult normal = null;
				for (String raw : weapon.getStyles())
				{
					Loadout candidate = base.copy();
					candidate.setStyle(AttackStyle.parse(raw));
					DpsResult dps = DpsCalculator.calculate(candidate, ctx);
					if (normal == null || dps.getPrimaryDps() > normal.getPrimaryDps()) { normal = dps; worn = candidate; }
				}
				Loadout chosenWorn = worn;
				report.append("  Maiden ").append(weapon.getName()).append(" + ").append(name).append(" style=").append(worn.getStyle())
					.append(" ordinaryDps=").append(normal.getPrimaryDps()).append(" max=").append(normal.getMaxHit())
					.append(" accuracy=").append(normal.getAccuracy());
				if (reducer != null)
				{
					Loadout spec = worn.copy(); spec.set(Slot.WEAPON, reducer.getWeapon()); spec.setStyle(reducer.getStyle());
					if (spec.get(Slot.SHIELD) == null && fangSetup != null) { spec.set(Slot.SHIELD, fangSetup.getLoadout().get(Slot.SHIELD)); }
					SpecialRotation.Fight fight = new SpecialRotation.Fight()
					{
						@Override public DpsResult ordinary(SpecialAttacks drains) { return DpsCalculator.calculate(chosenWorn, drained(ctx, drains)); }
						@Override public DpsResult spec(SpecialAttacks drains) { return DpsCalculator.calculate(spec, drained(special, drains)); }
					};
					report.append(" ownHammerAccuracy=").append(fight.spec(SpecialAttacks.NONE).getAccuracy());
					for (int count = 0; count <= 2; count++)
					{
						report.append(" team+own").append(count).append("Seconds=").append(SpecialRotation.drainOpenedTeam(fight,
							reducer.getSpecial().getDrain(), reducer.getSpecial().getEnergy(), count, result.getStartingHitpoints(), false, party,
							teammate, SpecialAttack.Drain.DRAGON_WARHAMMER)[0]);
					}
					report.append(" teamDrainedDps=").append(fight.ordinary(SpecialAttacks.builder().dragonWarhammer(3).build()).getPrimaryDps());
				}
				report.append('\n');
			}
		}
	}

	/** CombatContext#withDrains is package-private in calc; reach it reflectively. */
	private static CombatContext drained(CombatContext ctx, SpecialAttacks drains)
	{
		try
		{
			Method with = CombatContext.class.getDeclaredMethod("withDrains", SpecialAttacks.class);
			with.setAccessible(true);
			return (CombatContext) with.invoke(ctx, drains);
		}
		catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
	}

	private static int boost(List<Potion> potions, String skill, int base)
	{
		return potions.stream().filter(p -> skill.equals(p.getSkill())).mapToInt(p -> p.boost(base)).max().orElse(base);
	}

	private static Properties pluginProperties(Path path) throws IOException
	{
		StringBuilder filtered = new StringBuilder();
		try (BufferedReader reader = Files.newBufferedReader(path)) { String line; while ((line = reader.readLine()) != null) { if (line.startsWith("bestgearsetup.")) { filtered.append(line).append('\n'); } } }
		Properties values = new Properties(); values.load(new StringReader(filtered.toString())); return values;
	}
}
