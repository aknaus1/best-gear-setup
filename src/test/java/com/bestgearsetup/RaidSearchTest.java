package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.SearchDepth;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.ui.BestGearSetupPanel;
import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** Exercise the real raid search pipeline with a small, real owned kit instead of synthetic DPS. */
public class RaidSearchTest
{
	@Test public void allThreeRaidsProduceCompleteKitsUsingBundledCombatData() throws Exception
	{
		GameDataLoader loader = new GameDataLoader(new Gson());
		GameData full = loader.loadGameData();
		Set<String> names = new HashSet<>(Arrays.asList("dragon pickaxe", "abyssal whip", "dragon hunter lance",
			"magic shortbow", "trident of the swamp", "rune arrow", "rune platebody", "black d'hide body", "mystic robe top", "dawnbringer"));
		Map<String, List<GearItem>> equipment = new HashMap<>();
		List<GearItem> weapons = new ArrayList<>();
		Set<Integer> owned = new HashSet<>();
		Map<Integer, Long> quantities = new HashMap<>();
		for (Slot slot : Slot.values())
		{
			List<GearItem> selected = new ArrayList<>();
			for (GearItem item : full.getItems(slot))
			{
				if (names.contains(item.getName()))
				{
					selected.add(item);
					if (!RaidPlan.providedInRaid(item)) { owned.add(item.getId()); quantities.put(item.getId(), 1000L); }
				}
			}
			if (slot == Slot.WEAPON) { weapons.addAll(selected); }
			else { equipment.put(slot.getApiKey(), selected); }
		}
		assertTrue(weapons.stream().anyMatch(w -> w.getName().equals("dragon pickaxe")));
		GameData data = new GameData(full.getMonsters(), equipment, weapons, full.getSpells(), Collections.emptyMap(), Collections.emptyMap());
		BestGearSetupConfig config = new BestGearSetupConfig()
		{
			@Override public SearchMode mode() { return SearchMode.OWNED_ONLY; }
			@Override public SearchDepth searchDepth() { return SearchDepth.FAST; }
			@Override public boolean killSpecials() { return false; }
			@Override public boolean thrall() { return false; }
			@Override public boolean usePrayers() { return false; }
			@Override public boolean requireFireProtection() { return false; }
			@Override public int raidPartySize() { return 9; }
		};
		BestGearSetupPlugin plugin = new BestGearSetupPlugin(); plugin.config = config;
		plugin.panel = mock(BestGearSetupPanel.class);
		Field api = BestGearSetupPlugin.class.getDeclaredField("api"); api.setAccessible(true); api.set(plugin, loader);
		Method search = Arrays.stream(BestGearSetupPlugin.class.getDeclaredMethods()).filter(m -> m.getName().equals("runRaidSearch")).findFirst().get();
		search.setAccessible(true);
		RaidPlan[] result = {null};
		doAnswer(call -> { result[0] = call.getArgument(0); return null; }).when(plugin.panel).showRaidResults(any(RaidPlan.class), any());
		for (RaidPlanType type : Arrays.asList(RaidPlanType.COX_CM, RaidPlanType.TOA, RaidPlanType.TOB))
		{
			result[0] = null;
			List<Integer> progress = new ArrayList<>();
			search.invoke(plugin, data, type.encounters(data).get(0).getTarget(), PlayerLevels.maxed(), false,
				Collections.emptySet(), true, 99, Collections.emptyMap(), owned, quantities, 0, config, new LiveRaid(), Collections.emptyList(), type, (java.util.function.IntConsumer) progress::add);
			SwingUtilities.invokeAndWait(() -> { });
			assertNotNull(type.toString(), result[0]);
			assertEquals(Integer.valueOf(100), progress.get(progress.size() - 1));
			assertEquals(1, progress.stream().filter(p -> p == 100).count());
			for (int i = 1; i < progress.size(); i++) { assertTrue(progress.get(i) > progress.get(i - 1)); }
			assertEquals(type.encounters(data).size(), result[0].getSetups().size());
			assertTrue(RaidPlan.maximumInventory(result[0].getSetups(), result[0].getResults(), owned::contains) <= 28);
			assertTrue(result[0].getSetups().stream().allMatch(s -> s.getDps().getPrimaryDps() > 0));
			assertTrue(result[0].getSetups().stream().allMatch(s -> s.getLoadout().getSpell() == null
				|| s.getLoadout().getSpell().getSpellbook().equals(config.raidSpellbook().key())));
			assertEquals(0, result[0].getBuyCost());
			if (type == RaidPlanType.COX_CM)
			{
				for (RaidEncounter encounter : result[0].getEncounters())
				{
					boolean hand = encounter.getTarget().getName().startsWith("great olm") && encounter.getTarget().getName().contains("hand");
					assertEquals(hand ? 4 : 1, encounter.getRepetitions());
				}
				RaidPlan original = result[0];
				SearchResults[] active = {null};
				doAnswer(call -> { active[0] = call.getArgument(2); return null; }).when(plugin.panel)
					.showRaidResults(any(RaidPlan.class), any(), any(SearchResults.class));
				Method refresh = Arrays.stream(BestGearSetupPlugin.class.getDeclaredMethods()).filter(m -> m.getName().equals("refreshRaidEncounter")).findFirst().get();
				refresh.setAccessible(true);
				BestGearSetupConfig drained = SearchAssumptions.capture(config, Collections.singletonMap("specDwh", 1));
				assertEquals(true, refresh.invoke(plugin, original, data, original.getEncounters().get(0).getTarget(), PlayerLevels.maxed(),
					false, Collections.emptySet(), true, 99, Collections.emptyMap(), owned, quantities, 0, drained, new LiveRaid(), Collections.emptyList(), (java.util.function.IntConsumer) percent -> { }));
				SwingUtilities.invokeAndWait(() -> { });
				assertNotNull(active[0]);
				assertTrue(active[0].getMonster().getDefenceLevel() < active[0].getBaseDefence());
				Set<Integer> kit = new HashSet<>(); original.getEquipment().forEach(item -> kit.add(item.getId()));
				active[0].getByType().values().forEach(list -> list.forEach(setup -> assertTrue(kit.containsAll(RaidPlan.gear(setup).keySet()))));
			}
		}
	}
}
