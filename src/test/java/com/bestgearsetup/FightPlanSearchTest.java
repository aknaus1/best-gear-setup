package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.calc.Setup;
import com.bestgearsetup.calc.Style;
import com.bestgearsetup.calc.Weapons;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Summary;
import com.bestgearsetup.ui.Sidebar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/** A search of a phase target plans the whole fight from real data, after its own results and from cached tabs on a repeat. */
public class FightPlanSearchTest
{
	@Test
	public void plansZulrahAndHydraAfterTheResults() throws Exception
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
		GameData data = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
		Map<Integer, Long> prices = new HashMap<>();
		for (Slot slot : Slot.values())
		{
			for (Gear item : data.getItems(slot))
			{
				prices.put(item.getId(), 100_000L);
			}
		}
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		plugin.api = new GameDataLoader(RuneLiteAPI.GSON);
		plugin.config = new Options() { };
		plugin.panel = mock(Sidebar.class);
		Map<String, Object> settings = new HashMap<>();
		settings.put("mode", SearchMode.UNLIMITED);
		Options config = Assumptions.capture(new Options() { }, settings);
		settings.put("fightSwitchSlots", 0);
		Options noSwitches = Assumptions.capture(new Options() { }, settings);
		for (String fight : new String[]{"zulrah", "alchemical hydra"})
		{
			String target = FightPlan.phases(fight).get(0).getTarget();
			Summary summary = data.getMonsters().stream().filter(m -> m.getName().equals(target)).findFirst().orElse(null);
			assertNotNull(target, summary);
			for (String run : new String[]{"first run", "cached", "cached, no switches"})
			{
				long start = System.nanoTime();
				plugin.runSearch(data, summary, null, false, null, null, false, 99, prices, Collections.emptySet(),
					Collections.emptyMap(), plugin.searchGeneration.get(), run.endsWith("switches") ? noSwitches : config,
					new LiveRaid(), new ArrayList<>(), plugin.tabCache.epoch(), Collections.emptyMap(), Collections.emptyMap(), -1);
				System.out.printf("Full fight %s, %s: %d ms%n", fight, run, (System.nanoTime() - start) / 1_000_000);
			}
			SwingUtilities.invokeAndWait(() -> { });
			ArgumentCaptor<FightPlan.Plan> plans = ArgumentCaptor.forClass(FightPlan.Plan.class);
			verify(plugin.panel, times(2)).showFightPlan(eq(fight), plans.capture(), any(Setup.class), eq(8), eq(0), any(), any(), anyBoolean(), any());
			for (FightPlan.Plan plan : plans.getAllValues())
			{
				assertEquals(FightPlan.phases(fight).size(), plan.getUsed().size());
				assertTrue(fight + " total " + plan.getTotal(), plan.getTotal() > 0 && plan.getTotal() < 1000);
				assertTrue(plan.getSlots() <= 8);
				System.out.printf("  %s: %.1f s, %d slots, carry %s, from %.0f%% energy, specs %s%n", fight, plan.getTotal(),
					plan.getSlots(), plan.getCarried().stream().map(Gear::getName).collect(java.util.stream.Collectors.toList()),
					plan.getEnergy(), plan.getSpecs().stream().map(spec -> spec.getHeld().getWeapon().getName() + " x" + spec.getUses()
						+ " on " + FightPlan.phases(fight).get(spec.getPhase()).getLabel()).collect(java.util.stream.Collectors.toList()));
			}
			ArgumentCaptor<FightPlan.Plan> single = ArgumentCaptor.forClass(FightPlan.Plan.class);
			verify(plugin.panel).showFightPlan(eq(fight), single.capture(), any(Setup.class), eq(0), eq(0), any(), any(), anyBoolean(), any());
			assertTrue(single.getValue().getCarried().isEmpty());
			assertTrue("one setup is never faster", single.getValue().getTotal() >= plans.getValue().getTotal());
			System.out.printf("  %s, no switches: %.1f s%n", fight, single.getValue().getTotal());
			// Searching another phase target of the fight plans the same fight, from the same cached tabs where shared.
			String other = FightPlan.phases(fight).get(1).getTarget();
			plugin.runSearch(data, data.getMonsters().stream().filter(m -> m.getName().equals(other)).findFirst().get(), null,
				false, null, null, false, 99, prices, Collections.emptySet(), Collections.emptyMap(),
				plugin.searchGeneration.get(), config, new LiveRaid(), new ArrayList<>(), plugin.tabCache.epoch(),
				Collections.emptyMap(), Collections.emptyMap(), -1);
			SwingUtilities.invokeAndWait(() -> { });
			ArgumentCaptor<FightPlan.Plan> fromOther = ArgumentCaptor.forClass(FightPlan.Plan.class);
			verify(plugin.panel).showFightPlan(eq(fight), fromOther.capture(), any(Setup.class), eq(8), eq(1), any(), any(), anyBoolean(), any());
			assertEquals(plans.getValue(), fromOther.getValue());
		}
		verify(plugin.panel, times(8)).showResults(any());
		verify(plugin.panel, never()).showError(any());
	}

	/**
	 * A saved boss phase never changes the fight's rows: one that doesn't apply is still said in the notes, and one that
	 * does (Yama's partner tanking) keeps the searched target's row marked.
	 */
	@Test
	public void aSavedPhaseKeepsItsNoteAndTheSelectedRow() throws Exception
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
		GameData data = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		plugin.api = new GameDataLoader(RuneLiteAPI.GSON);
		plugin.config = new Options() { };
		plugin.panel = mock(Sidebar.class);
		Map<String, Object> settings = new HashMap<>();
		settings.put("mode", SearchMode.UNLIMITED);
		settings.put("fightSwitchSlots", 0);
		settings.put(Options.PHASE_KEY, com.bestgearsetup.data.EncounterPhase.YAMA_MELEE_TANK);
		Options config = Assumptions.capture(new Options() { }, settings);
		for (String target : new String[]{"kalphite queen (airborne)", "yama (normal)"})
		{
			plugin.runSearch(data, data.getMonsters().stream().filter(m -> m.getName().equals(target)).findFirst().get(), null,
				false, null, null, false, 99, Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(),
				plugin.searchGeneration.get(), config, new LiveRaid(), new ArrayList<>(), plugin.tabCache.epoch(),
				Collections.emptyMap(), Collections.emptyMap(), -1);
		}
		SwingUtilities.invokeAndWait(() -> { });
		ArgumentCaptor<SearchResults> results = ArgumentCaptor.forClass(SearchResults.class);
		verify(plugin.panel, times(2)).showResults(results.capture());
		assertTrue(results.getAllValues().get(0).getNotes().toString(), results.getAllValues().get(0).getNotes().stream()
			.anyMatch(note -> note.contains("does not apply to this target")));
		verify(plugin.panel).showFightPlan(eq("kalphite queen"), any(), any(Setup.class), eq(0), eq(1), any(), any(), anyBoolean(), any());
		verify(plugin.panel).showFightPlan(eq("yama"), any(), any(Setup.class), eq(0), eq(0), any(), any(), anyBoolean(), any());
		verify(plugin.panel, never()).showError(any());
	}

	/** With a full bar, the plan carries a spec weapon within its slots; planning the specials stays quick on cached tabs. */
	@Test
	public void plansSpecialAttacksWithinTheFightsEnergy() throws Exception
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
		GameData data = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
		Map<Integer, Long> prices = new HashMap<>();
		for (Slot slot : Slot.values())
		{
			for (Gear item : data.getItems(slot))
			{
				prices.put(item.getId(), 100_000L);
			}
		}
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		plugin.api = new GameDataLoader(RuneLiteAPI.GSON);
		plugin.config = new Options() { };
		plugin.panel = mock(Sidebar.class);
		Map<String, Object> settings = new HashMap<>();
		settings.put("mode", SearchMode.UNLIMITED);
		settings.put("specEnergy", com.bestgearsetup.calc.SpecEnergy.FULL_BAR);
		settings.put("fightSwitchSlots", 10);
		Options fullBar = Assumptions.capture(new Options() { }, settings);
		settings.put("killSpecials", false);
		Options none = Assumptions.capture(new Options() { }, settings);
		for (String fight : new String[]{"zulrah", "vorkath"})
		{
			String target = FightPlan.phases(fight).get(0).getTarget();
			Summary summary = data.getMonsters().stream().filter(m -> m.getName().equals(target)).findFirst().orElse(null);
			long[] cached = new long[2];
			for (int run = 0; run < 6; run++)
			{
				long start = System.nanoTime();
				plugin.runSearch(data, summary, null, false, null, null, false, 99, prices, Collections.emptySet(),
					Collections.emptyMap(), plugin.searchGeneration.get(), run % 2 == 0 ? fullBar : none,
					new LiveRaid(), new ArrayList<>(), plugin.tabCache.epoch(), Collections.emptyMap(), Collections.emptyMap(), -1);
				// The last of three runs each, on cached tabs and warmed code.
				cached[run % 2] = (System.nanoTime() - start) / 1_000_000;
			}
			System.out.printf("Full fight %s, cached: %d ms with specials, %d ms without%n", fight, cached[0], cached[1]);
			assertTrue("specials took " + (cached[0] - cached[1]) + " ms", cached[0] - cached[1] < 50);
			SwingUtilities.invokeAndWait(() -> { });
			ArgumentCaptor<FightPlan.Plan> plans = ArgumentCaptor.forClass(FightPlan.Plan.class);
			verify(plugin.panel, times(6)).showFightPlan(eq(fight), plans.capture(), any(Setup.class), eq(10), eq(0), any(), any(), anyBoolean(), any());
			FightPlan.Plan plan = plans.getAllValues().get(4);
			FightPlan.Plan plain = plans.getAllValues().get(5);
			assertTrue(plain.getSpecs().isEmpty());
			assertFalse(fight, plan.getSpecs().isEmpty());
			assertEquals(100, plan.getEnergy(), 0);
			assertTrue(plan.getSlots() <= 10);
			assertTrue("specials never slow the plan", plan.getTotal() <= plain.getTotal());
			for (FightPlan.Spec spec : plan.getSpecs())
			{
				Gear weapon = spec.getHeld().getWeapon();
				assertTrue(weapon.getName(), plan.getWorn().getWeapon().equals(weapon) || plan.getCarried().contains(weapon));
				System.out.printf("  %s: spec %s x%d on %s%s%n", fight, weapon.getName(), spec.getUses(),
					FightPlan.phases(fight).get(spec.getPhase()).getLabel(), spec.isDrain() ? " (drain)" : "");
			}
			System.out.printf("  %s: %.1f s with specials, %.1f s without, %d slots, wear %s, carry %s%n", fight, plan.getTotal(),
				plain.getTotal(), plan.getSlots(), plan.getWorn().getWeapon().getName(),
				plan.getCarried().stream().map(Gear::getName).collect(java.util.stream.Collectors.toList()));
			// Another phase target of the fight plans the same special attacks.
			String other = FightPlan.phases(fight).get(1).getTarget();
			plugin.runSearch(data, data.getMonsters().stream().filter(m -> m.getName().equals(other)).findFirst().get(), null,
				false, null, null, false, 99, prices, Collections.emptySet(), Collections.emptyMap(),
				plugin.searchGeneration.get(), fullBar, new LiveRaid(), new ArrayList<>(), plugin.tabCache.epoch(),
				Collections.emptyMap(), Collections.emptyMap(), -1);
			SwingUtilities.invokeAndWait(() -> { });
			ArgumentCaptor<FightPlan.Plan> fromOther = ArgumentCaptor.forClass(FightPlan.Plan.class);
			verify(plugin.panel).showFightPlan(eq(fight), fromOther.capture(), any(Setup.class), eq(10), eq(1), any(), any(), anyBoolean(), any());
			assertEquals(plan, fromOther.getValue());
		}
		verify(plugin.panel, never()).showError(any());
	}

	/**
	 * A style pick re-plans from the cached tabs, never searching one, and constrains its row. With no fight overrides each
	 * row's tabs are its target's own search (no tab searched again either), so the kit wears a top setup of a picked
	 * tab and the row never says its tab differs. Two picks that one set of items can't both serve (0 Fight
	 * switches): the one left without damage is dropped and its row says why.
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void picksReplanFromTheCachedTabsAndUnfitPicksSayWhy() throws Exception
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
		GameData data = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
		int[] searches = {0};
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override
			java.util.List<Setup> searchTab(SearchInputs key, long epoch, java.util.function.BooleanSupplier cancelled,
				java.util.function.DoubleConsumer progress, java.util.function.Supplier<java.util.List<Setup>> search)
			{
				return super.searchTab(key, epoch, cancelled, progress, () ->
				{
					searches[0]++;
					return search.get();
				});
			}
		};
		plugin.api = new GameDataLoader(RuneLiteAPI.GSON);
		plugin.config = new Options() { };
		plugin.panel = mock(Sidebar.class);
		Map<String, Object> settings = new HashMap<>();
		settings.put("mode", SearchMode.UNLIMITED);
		settings.put("fightSwitchSlots", 0);
		Options config = Assumptions.capture(new Options() { }, settings);
		Summary serpentine = data.getMonsters().stream().filter(m -> m.getName().equals("zulrah (serpentine)")).findFirst().get();
		Map<Integer, Style.Type> magic = Collections.singletonMap(0, Style.Type.MAGIC);
		Map<Integer, Style.Type> both = new HashMap<>(magic);
		both.put(1, Style.Type.RANGED);
		java.util.List<Map<Integer, Style.Type>> runs = java.util.Arrays.asList(Collections.emptyMap(), magic, both,
			Collections.emptyMap(), Collections.emptyMap());
		for (int run = 0; run < runs.size(); run++)
		{
			Map<Integer, Style.Type> picks = runs.get(run);
			String target = FightPlan.phases("zulrah").get(Math.max(0, run - 2)).getTarget();
			plugin.runSearch(data, data.getMonsters().stream().filter(m -> m.getName().equals(target)).findFirst().get(), null, false, null, null, false, 99, Collections.emptyMap(), Collections.emptySet(),
				Collections.emptyMap(), plugin.searchGeneration.get(), config, new LiveRaid(), new ArrayList<>(),
				plugin.tabCache.epoch(), Collections.emptyMap(), picks, -1);
			if (run == 0)
			{
				searches[0] = 0;
			}
		}
		assertEquals("picks and the other targets' own searches never search a tab", 0, searches[0]);
		SwingUtilities.invokeAndWait(() -> { });
		ArgumentCaptor<SearchResults> results = ArgumentCaptor.forClass(SearchResults.class);
		verify(plugin.panel, times(5)).showResults(results.capture());
		ArgumentCaptor<FightPlan.Plan> plans = ArgumentCaptor.forClass(FightPlan.Plan.class);
		ArgumentCaptor<Map<Integer, String>> unused = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<Boolean> differs = ArgumentCaptor.forClass(Boolean.class);
		verify(plugin.panel, times(5)).showFightPlan(eq("zulrah"), plans.capture(), any(Setup.class), eq(0), anyInt(), any(),
			unused.capture(), differs.capture(), any());
		FightPlan.Plan picked = plans.getAllValues().get(1);
		assertEquals(Style.Type.MAGIC, Weapons.tabType(picked.getUsed().get(0)));
		assertTrue(unused.getAllValues().get(1).isEmpty());
		assertFalse("no overrides: the tab is the plan's", differs.getAllValues().get(1));
		assertTrue("the kit wears a picked tab's setup", results.getAllValues().stream()
			.anyMatch(found -> found.getByType().get(Style.Type.MAGIC).get(0).getLoadout().equals(picked.getWorn())));
		assertFalse(differs.getAllValues().contains(true));
		FightPlan.Plan two = plans.getAllValues().get(2);
		Map<Integer, String> dropped = unused.getAllValues().get(2);
		assertEquals(dropped.toString(), 1, dropped.size());
		int kept = dropped.containsKey(0) ? 1 : 0;
		assertEquals("doesn't fit 0 Fight switches", dropped.get(1 - kept));
		assertEquals(both.get(kept), Weapons.tabType(two.getUsed().get(kept)));
		assertTrue(java.util.Arrays.stream(two.getSeconds()).allMatch(Double::isFinite));
		// A pick for a fight not being searched (a card still shown from the fight before) is ignored, and a row past the
		// fight's rows plans as if unpicked.
		plugin.pickFightStyle("zulrah", 0, Style.Type.MAGIC);
		assertTrue(plugin.fightPicks.isEmpty());
		plugin.runSearch(data, serpentine, null, false, null, null, false, 99, Collections.emptyMap(), Collections.emptySet(),
			Collections.emptyMap(), plugin.searchGeneration.get(), config, new LiveRaid(), new ArrayList<>(),
			plugin.tabCache.epoch(), Collections.emptyMap(), Collections.singletonMap(7, Style.Type.MAGIC), 9);
		SwingUtilities.invokeAndWait(() -> { });
		verify(plugin.panel, times(6)).showFightPlan(eq("zulrah"), any(), any(Setup.class), eq(0), anyInt(), any(), any(),
			anyBoolean(), any());
		verify(plugin.panel, never()).showFightStatus(any());
		verify(plugin.panel, never()).showError(any());
	}
}
