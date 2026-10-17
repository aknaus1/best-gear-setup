package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.RaidLoadout;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.SearchDepth;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.WeaponRules;
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
	@org.junit.Before public void initializeCacheIdentity()
	{
		CacheIdentity.init(net.runelite.http.api.RuneLiteAPI.GSON);
	}

	@Test public void everyTobRoleProducesItsRequiredPrimaryAttacksAndRefreshPreservesThem() throws Exception
	{
		GameDataLoader loader = new GameDataLoader(new Gson());
		GameData full = loader.loadGameData();
		Set<String> names = new HashSet<>(Arrays.asList("abyssal whip", "magic shortbow", "rune arrow",
			"trident of the swamp", "ancient staff", "red chinchompa", "toxic blowpipe", "adamant dart", "ava's accumulator"));
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
				{ selected.add(item); owned.add(item.getId()); quantities.put(item.getId(), 1000L); }
			}
			if (slot == Slot.WEAPON) { weapons.addAll(selected); }
			else { equipment.put(slot.getApiKey(), selected); }
		}
		assertTrue(weapons.stream().anyMatch(w -> w.getName().equals("ancient staff")));
		assertTrue(weapons.stream().anyMatch(w -> w.getName().equals("red chinchompa")));
		GameData data = new GameData(full.getMonsters(), equipment, weapons, full.getSpells(), Collections.emptyMap(), Collections.emptyMap());
		BestGearSetupConfig base = new BestGearSetupConfig()
		{
			@Override public SearchMode mode() { return SearchMode.OWNED_ONLY; }
			@Override public SearchDepth searchDepth() { return SearchDepth.FAST; }
			@Override public boolean killSpecials() { return false; }
			@Override public boolean thrall() { return false; }
			@Override public boolean usePrayers() { return false; }
			@Override public RaidSpellbook raidSpellbook() { return RaidSpellbook.ARCEUUS; }
			@Override public int raidPartySize() { return 4; }
			@Override public int raidEquipmentLimit() { return 15; }
			@Override public boolean requireAtlatlAmmoRecovery() { return true; }
		};
		BestGearSetupPlugin plugin = new BestGearSetupPlugin(); plugin.config = base;
		plugin.panel = mock(BestGearSetupPanel.class);
		Field api = BestGearSetupPlugin.class.getDeclaredField("api"); api.setAccessible(true); api.set(plugin, loader);
		Method search = Arrays.stream(BestGearSetupPlugin.class.getDeclaredMethods()).filter(m -> m.getName().equals("runRaidSearch")).findFirst().get();
		search.setAccessible(true);
		Method refresh = Arrays.stream(BestGearSetupPlugin.class.getDeclaredMethods()).filter(m -> m.getName().equals("refreshRaidEncounter")).findFirst().get();
		refresh.setAccessible(true);
		RaidPlan[] result = {null};
		SearchResults[] active = {null};
		doAnswer(call -> { result[0] = call.getArgument(0); return null; }).when(plugin.panel).showRaidResults(any(RaidPlan.class), any());
		doAnswer(call -> { active[0] = call.getArgument(2); return null; }).when(plugin.panel)
			.showRaidResults(any(RaidPlan.class), any(), any(SearchResults.class));
		for (RaidOptions.TobRole role : RaidOptions.TobRole.values())
		{
			BestGearSetupConfig config = SearchAssumptions.capture(base, Collections.singletonMap("tobRole", role));
			Set<Integer> roleOwned = new HashSet<>(owned);
			if (role.freezes())
			{ weapons.stream().filter(w -> w.getName().equals("ancient staff")).forEach(w -> roleOwned.remove(w.getId())); }
			result[0] = null;
			search.invoke(plugin, data, RaidPlanType.TOB.encounters(data).get(0).getTarget(), PlayerLevels.maxed(), false,
				Collections.emptySet(), true, 99, Collections.emptyMap(), roleOwned, quantities, 0, config, new LiveRaid(), Collections.emptyList(),
				RaidPlanType.TOB, (java.util.function.DoubleConsumer) percent -> { });
			SwingUtilities.invokeAndWait(() -> { });
			assertNotNull(role.toString(), result[0]);
			RaidPlan plan = result[0];
			assertTrue(plan.getSetups().stream().filter(RaidSearchTest::firesRecoverableAmmo)
				.allMatch(s -> WeaponRules.isAmmoRecoveryCape(s.getLoadout().get(Slot.CAPE))));
			assertEquals(role.freezes() ? RaidSpellbook.ANCIENT : RaidSpellbook.ARCEUUS, plan.getSpellbook());
			assertTrue(RaidPlan.maximumInventory(plan.getSetups(), plan.getResults(), owned::contains) <= 28);
			int[] fullChoice = RaidPlanner.fullSetupChoice(plan.getEncounters(), plan.getResults(), plan.getSetups());
			List<int[]> groups = RaidEncounter.switchGroups(plan.getEncounters());
			for (int g = 0; g < groups.size(); g++)
			{
				int[] group = groups.get(g);
				int fullIndex = fullChoice[g];
				assertTrue(role.toString(), plan.getEncounters().get(fullIndex).isFullSwitchSetup());
				if (Arrays.stream(group).anyMatch(member -> plan.getEncounters().get(member).isFreeze()))
				{
					// The freeze is a switch from the Maiden setup, however many items it takes to land.
					assertEquals("Maiden", plan.getEncounters().get(fullIndex).getLabel());
					continue;
				}
				Set<Slot> extras = java.util.EnumSet.noneOf(Slot.class);
				for (int member : group)
				{
					if (member == fullIndex) { continue; }
					assertTrue(role.toString(), plan.getSetups().get(member).getLoadout()
						.switchesFrom(plan.getSetups().get(fullIndex).getLoadout()) <= RaidEncounter.DEFAULT_SWITCH_LIMIT);
					extras.addAll(plan.getSetups().get(member).getLoadout().switchSlotsFrom(plan.getSetups().get(fullIndex).getLoadout()));
				}
				extras.remove(Slot.WEAPON);
				assertTrue(role + " must share its extra slot", extras.size() <= 1);
			}
			for (int index = 0; index < plan.getEncounters().size(); index++)
			{
				RaidEncounter encounter = plan.getEncounters().get(index);
				if (encounter.getLoadoutRequirement() == RaidLoadout.ANY) { continue; }
				SetupResult setup = plan.getSetups().get(index);
				if (role.freezes())
				{
					assertEquals("ice barrage", setup.getLoadout().getSpell().getLowerName());
					assertTrue(setup.getLoadout().getStyle().isManualCast());
					assertTrue(roleOwned.contains(setup.getLoadout().getWeapon().getId()));
				}
				else { assertEquals("chinchompa", setup.getLoadout().getWeapon().getSubcategory()); }
				// A live nylocas refreshes the first Maiden nylocas encounter; Range DPS's chins come after helping.
				if (!encounter.equals(plan.encounterFor(encounter.getTarget().getName(), encounter.getPhase())))
				{
					assertEquals(RaidOptions.TobRole.RANGED_DPS, role);
					continue;
				}
				active[0] = null;
				assertEquals(true, refresh.invoke(plugin, plan, data, encounter.getTarget(), PlayerLevels.maxed(), false,
					Collections.emptySet(), true, 99, Collections.emptyMap(), roleOwned, quantities, 0, config, new LiveRaid(), Collections.emptyList(),
					(java.util.function.DoubleConsumer) percent -> { }));
				SwingUtilities.invokeAndWait(() -> { });
				assertNotNull(active[0]);
				assertTrue(active[0].getByType().values().stream().flatMap(List::stream)
					.allMatch(s -> encounter.getLoadoutRequirement().allows(s.getLoadout())));
				assertTrue(active[0].getByType().values().stream().flatMap(List::stream)
					.filter(RaidSearchTest::firesRecoverableAmmo)
					.allMatch(s -> WeaponRules.isAmmoRecoveryCape(s.getLoadout().get(Slot.CAPE))));
			}
			for (int g = 0; g < groups.size(); g++)
			{
				int[] group = groups.get(g);
				int fullIndex = fullChoice[g];
				for (int member : group)
				{
					RaidEncounter encounter = plan.getEncounters().get(member);
					active[0] = null;
					assertEquals(true, refresh.invoke(plugin, plan, data, encounter.getTarget(), PlayerLevels.maxed(), false,
						Collections.emptySet(), true, 99, Collections.emptyMap(), roleOwned, quantities, 0, config, new LiveRaid(), Collections.emptyList(),
						(java.util.function.DoubleConsumer) percent -> { }));
					SwingUtilities.invokeAndWait(() -> { });
					assertNotNull(role + " " + encounter, active[0]);
					for (SetupResult setup : active[0].getByType().values().stream().flatMap(List::stream).collect(java.util.stream.Collectors.toList()))
					{
						List<SetupResult> trial = new ArrayList<>(plan.getSetups());
						trial.set(member, setup);
						assertTrue("Live refresh preserves the chosen base and extra slot",
							RaidEncounter.fullSetups(group, trial, plan.getEncounters()).contains(fullIndex));
					}
				}
			}
		}
	}

	@Test public void allThreeRaidsProduceCompleteKitsUsingBundledCombatData() throws Exception
	{
		GameDataLoader loader = new GameDataLoader(new Gson());
		GameData full = loader.loadGameData();
		Set<String> names = new HashSet<>(Arrays.asList("dragon pickaxe", "abyssal whip", "dragon hunter lance",
			"magic shortbow", "trident of the swamp", "smoke battlestaff", "rune arrow", "rune platebody", "black d'hide body", "mystic robe top", "dawnbringer", "ava's accumulator"));
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
			List<Double> progress = new ArrayList<>();
			search.invoke(plugin, data, type.encounters(data).get(0).getTarget(), PlayerLevels.maxed(), false,
				Collections.emptySet(), true, 99, Collections.emptyMap(), owned, quantities, 0, config, new LiveRaid(), Collections.emptyList(), type, (java.util.function.DoubleConsumer) progress::add);
			SwingUtilities.invokeAndWait(() -> { });
			assertNotNull(type.toString(), result[0]);
			assertEquals(Double.valueOf(100), progress.get(progress.size() - 1));
			assertEquals(1, progress.stream().filter(p -> p == 100).count());
			for (int i = 1; i < progress.size(); i++) { assertTrue(progress.get(i) > progress.get(i - 1)); }
			RaidScaling.Settings scaling = RaidScaling.Settings.builder().partySize(config.raidPartySize()).build();
			assertEquals(type.encounters(data, scaling, RaidOptions.of(config)).size(), result[0].getSetups().size());
			assertTrue(RaidPlan.maximumInventory(result[0].getSetups(), result[0].getResults(), owned::contains) <= 28);
			assertTrue(result[0].getSetups().stream().allMatch(s -> s.getDps().getPrimaryDps() > 0));
			assertTrue(result[0].getSetups().stream().allMatch(s -> s.getLoadout().getSpell() == null
				|| s.getLoadout().getSpell().getSpellbook().equals(result[0].getSpellbook().key())));
			assertEquals(0, result[0].getBuyCost());
			RaidPlan auto = result[0];
			assertEquals(RaidSpellbook.ARCEUUS, auto.getSpellbook());
			for (RaidSpellbook book : Arrays.asList(RaidSpellbook.STANDARD, RaidSpellbook.ANCIENT, RaidSpellbook.ARCEUUS))
			{
				BestGearSetupConfig manual = SearchAssumptions.capture(config, Collections.singletonMap("raidSpellbook", book));
				result[0] = null;
				search.invoke(plugin, data, type.encounters(data).get(0).getTarget(), PlayerLevels.maxed(), false,
					Collections.emptySet(), true, 99, Collections.emptyMap(), owned, quantities, 0, manual, new LiveRaid(),
					Collections.emptyList(), type, (java.util.function.DoubleConsumer) percent -> { });
				SwingUtilities.invokeAndWait(() -> { });
				assertNotNull(book.toString(), result[0]);
				assertTrue("Auto must beat every feasible manual book", RaidSpellbook.seconds(auto) <= RaidSpellbook.seconds(result[0]) + 1e-6);
				assertEquals(book, result[0].getSpellbook());
				if (type == RaidPlanType.COX_CM && book == RaidSpellbook.STANDARD)
				{
					RaidPlan fire = result[0];
					int ice = java.util.stream.IntStream.range(0, fire.getEncounters().size())
						.filter(i -> fire.getEncounters().get(i).getLabel().equals("Ice Demon")).findFirst().getAsInt();
					assertEquals("fire surge", fire.getSetups().get(ice).getLoadout().getSpell().getLowerName());
					assertTrue(fire.getEncounters().get(ice).estimatedSeconds(fire.getResults().get(ice), fire.getSetups().get(ice))
						< auto.getEncounters().get(ice).estimatedSeconds(auto.getResults().get(ice), auto.getSetups().get(ice)));
				}
			}
			if (type == RaidPlanType.COX_CM)
			{
				BestGearSetupConfig noThralls = SearchAssumptions.capture(config, Collections.singletonMap("thrall", false));
				result[0] = null;
				search.invoke(plugin, data, type.encounters(data).get(0).getTarget(), PlayerLevels.maxed(), false,
					Collections.emptySet(), true, 99, Collections.emptyMap(), owned, quantities, 0, noThralls, new LiveRaid(),
					Collections.emptyList(), type, (java.util.function.DoubleConsumer) percent -> { });
				SwingUtilities.invokeAndWait(() -> { });
				assertEquals(RaidSpellbook.STANDARD, result[0].getSpellbook());
			}
			if (type == RaidPlanType.TOA)
			{
				// With only Standard spells and no powered staff, the other books cannot cover required magic rooms.
				GameData standardOnly = new GameData(data.getMonsters(), equipment,
					weapons.stream().filter(w -> !w.getName().equals("trident of the swamp")).collect(java.util.stream.Collectors.toList()),
					full.getSpells().stream().filter(spell -> "standard".equals(spell.getSpellbook())).collect(java.util.stream.Collectors.toList()),
					Collections.emptyMap(), Collections.emptyMap());
				result[0] = null;
				search.invoke(plugin, standardOnly, type.encounters(data).get(0).getTarget(), PlayerLevels.maxed(), false,
					Collections.emptySet(), true, 99, Collections.emptyMap(), owned, quantities, 0, config, new LiveRaid(),
					Collections.emptyList(), type, (java.util.function.DoubleConsumer) percent -> { });
				SwingUtilities.invokeAndWait(() -> { });
				assertNotNull(result[0]);
				assertEquals(RaidSpellbook.STANDARD, result[0].getSpellbook());
				assertTrue(result[0].getNotes().stream().anyMatch(note -> note.contains("without a feasible complete kit")));
			}
			result[0] = auto;
			if (type == RaidPlanType.TOB)
			{
				// The Dawnbringer is never a worn weapon, and the kit never carries it: it's picked up in the room.
				RaidPlan plan = result[0];
				plan.getSetups().forEach(setup -> assertTrue(!RaidPlan.providedInRaid(setup.getLoadout().getWeapon())));
				assertTrue(plan.getEquipment().stream().noneMatch(RaidPlan::providedInRaid));
				assertTrue(plan.getInventory().stream().noneMatch(item -> item.getIds().stream()
					.anyMatch(id -> data.findItem(id) != null && RaidPlan.providedInRaid(data.findItem(id)))));
				// One Vasilias form wears the full setup; the other two are quick switches from it.
				int[] group = RaidEncounter.switchGroups(plan.getEncounters()).get(0);
				int[] fullSetup = RaidPlanner.fullSetupChoice(plan.getEncounters(), plan.getResults(), plan.getSetups());
				assertNotNull(fullSetup);
				for (int member : group)
				{
					assertTrue(plan.getSetups().get(member).getLoadout().switchesFrom(plan.getSetups().get(fullSetup[0]).getLoadout())
						<= RaidEncounter.DEFAULT_SWITCH_LIMIT);
					assertTrue(plan.getResults().get(member).getNotes().stream().anyMatch(note -> note.contains("quick switch")));
				}
				// With specials on, P1 keeps the player's own weapon and specs with the Dawnbringer.
				RaidEncounter p1 = plan.getEncounters().stream().filter(e -> e.getLabel().equals("Verzik P1")).findFirst().get();
				SearchResults[] active = {null};
				doAnswer(call -> { active[0] = call.getArgument(2); return null; }).when(plugin.panel)
					.showRaidResults(any(RaidPlan.class), any(), any(SearchResults.class));
				Method refresh = Arrays.stream(BestGearSetupPlugin.class.getDeclaredMethods()).filter(m -> m.getName().equals("refreshRaidEncounter")).findFirst().get();
				refresh.setAccessible(true);
				BestGearSetupConfig specs = SearchAssumptions.capture(config, Collections.singletonMap("killSpecials", true));
				assertEquals(true, refresh.invoke(plugin, plan, data, p1.getTarget(), PlayerLevels.maxed(),
					false, Collections.emptySet(), true, 99, Collections.emptyMap(), owned, quantities, 0, specs, new LiveRaid(), Collections.emptyList(), (java.util.function.DoubleConsumer) percent -> { }));
				SwingUtilities.invokeAndWait(() -> { });
				assertNotNull(active[0]);
				SetupResult shown = active[0].getByType().values().iterator().next().get(0);
				assertTrue(!RaidPlan.providedInRaid(shown.getLoadout().getWeapon()));
				assertNotNull(shown.getSpecial());
				assertTrue(RaidPlan.providedInRaid(shown.getSpecial().getWeapon()));
				assertEquals(35, shown.getSpecial().getSpecial().getEnergy());
			}
			if (type == RaidPlanType.COX_CM)
			{
				List<RaidEncounter> hands = new ArrayList<>();
				for (RaidEncounter encounter : result[0].getEncounters())
				{
					boolean hand = encounter.getTarget().getName().startsWith("great olm") && encounter.getTarget().getName().contains("hand");
					if (hand) { hands.add(encounter); assertEquals(1, encounter.getRepetitions(), 0); }
				}
				assertEquals(2 * RaidScaling.olmHandPhases(scaling), hands.size());
				for (int phase = 0; phase < hands.size() / 2; phase++)
				{
					assertEquals("great olm (melee hand)", hands.get(2 * phase).getTarget().getName());
					assertEquals("great olm (mage hand)", hands.get(2 * phase + 1).getTarget().getName());
				}
				// Scavengers and Vespula are shown with setups built only from the kit the other encounters chose.
				Set<Integer> chosenKit = new HashSet<>();
				result[0].getEquipment().forEach(item -> chosenKit.add(item.getId()));
				List<String> shownOnly = new ArrayList<>();
				for (int i = 0; i < result[0].getEncounters().size(); i++)
				{
					if (!result[0].getEncounters().get(i).isDisplayOnly()) { continue; }
					shownOnly.add(result[0].getEncounters().get(i).getLabel());
					assertTrue(chosenKit.containsAll(RaidPlan.gear(result[0].getSetups().get(i)).keySet()));
					assertTrue(result[0].getResults().get(i).getNotes().stream().anyMatch(note -> note.contains("doesn't shape the raid kit")));
				}
				assertEquals(Arrays.asList("Scavengers", "Vespula"), shownOnly);
				RaidPlan original = result[0];
				SearchResults[] active = {null};
				doAnswer(call -> { active[0] = call.getArgument(2); return null; }).when(plugin.panel)
					.showRaidResults(any(RaidPlan.class), any(), any(SearchResults.class));
				Method refresh = Arrays.stream(BestGearSetupPlugin.class.getDeclaredMethods()).filter(m -> m.getName().equals("refreshRaidEncounter")).findFirst().get();
				refresh.setAccessible(true);
				BestGearSetupConfig drained = SearchAssumptions.capture(config, Collections.singletonMap("specDwh", 1));
				assertEquals(true, refresh.invoke(plugin, original, data, original.getEncounters().get(0).getTarget(), PlayerLevels.maxed(),
					false, Collections.emptySet(), true, 99, Collections.emptyMap(), owned, quantities, 0, drained, new LiveRaid(), Collections.emptyList(), (java.util.function.DoubleConsumer) percent -> { }));
				SwingUtilities.invokeAndWait(() -> { });
				assertNotNull(active[0]);
				assertTrue(active[0].getMonster().getDefenceLevel() < active[0].getBaseDefence());
				Set<Integer> kit = new HashSet<>(); original.getEquipment().forEach(item -> kit.add(item.getId()));
				active[0].getByType().values().forEach(list -> list.forEach(setup -> assertTrue(kit.containsAll(RaidPlan.gear(setup).keySet()))));
			}
		}
	}

	@Test public void spellbooksShareOnlyNonMagicSearchesAndPlanTheSameKits() throws Exception
	{
		GameDataLoader loader = new GameDataLoader(new Gson());
		GameData data = loader.loadGameData();
		Set<String> names = new HashSet<>(Arrays.asList("abyssal whip", "magic shortbow", "rune arrow", "ancient staff",
			"trident of the swamp", "dragon defender", "rune platebody", "ava's accumulator"));
		Set<Integer> owned = new HashSet<>();
		Map<Integer, Long> quantities = new HashMap<>();
		for (Slot slot : Slot.values())
		{
			for (GearItem item : data.getItems(slot))
			{
				if (names.contains(item.getName())) { owned.add(item.getId()); quantities.put(item.getId(), 1000L); }
			}
		}
		BestGearSetupConfig config = new BestGearSetupConfig()
		{
			@Override public SearchMode mode() { return SearchMode.OWNED_ONLY; }
			@Override public SearchDepth searchDepth() { return SearchDepth.FAST; }
			@Override public boolean killSpecials() { return false; }
			@Override public boolean thrall() { return false; }
		};
		BestGearSetupPlugin plugin = new BestGearSetupPlugin(); plugin.config = config;
		plugin.panel = mock(BestGearSetupPanel.class);
		Field api = BestGearSetupPlugin.class.getDeclaredField("api"); api.setAccessible(true); api.set(plugin, loader);
		Method plan = Arrays.stream(BestGearSetupPlugin.class.getDeclaredMethods()).filter(m -> m.getName().equals("planRaidSpellbook")).findFirst().get();
		plan.setAccessible(true);
		Map<Integer, Map<List<Object>, List<SetupResult>>> shared = new HashMap<>();
		for (RaidSpellbook book : RaidSpellbook.candidates(RaidPlanType.TOB, config))
		{
			RaidPlan[] plans = new RaidPlan[2];
			for (int run = 0; run < 2; run++)
			{
				plans[run] = (RaidPlan) plan.invoke(plugin, data, RaidPlanType.TOB.encounters(data).get(0).getTarget(), PlayerLevels.maxed(),
					false, null, true, 99, Collections.emptyMap(), owned, quantities, 0, config, new LiveRaid(), Collections.emptyList(),
					RaidPlanType.TOB, book, (java.util.function.DoubleConsumer) percent -> { }, run == 0 ? shared : new HashMap<>(),
					Double.POSITIVE_INFINITY);
			}
			assertNotNull(book.toString(), plans[0]);
			assertEquals(book.toString(), RaidSpellbook.seconds(plans[1]), RaidSpellbook.seconds(plans[0]), 0);
			for (int index = 0; index < plans[0].getSetups().size(); index++)
			{
				SetupResult sharedSetup = plans[0].getSetups().get(index);
				SetupResult separate = plans[1].getSetups().get(index);
				assertEquals(RaidPlan.gear(separate).keySet(), RaidPlan.gear(sharedSetup).keySet());
				assertEquals(separate.getLoadout().getSpell(), sharedSetup.getLoadout().getSpell());
				assertEquals(separate.getDps(), sharedSetup.getDps());
			}
		}
		assertTrue(shared.values().stream().mapToInt(Map::size).sum() > 0);
		for (Map<List<Object>, List<SetupResult>> tabs : shared.values())
		{
			for (List<Object> key : tabs.keySet())
			{ assertTrue(!((com.bestgearsetup.calc.OptimizerSettings) key.get(0)).getStyles().contains(com.bestgearsetup.calc.AttackStyle.Type.MAGIC)); }
		}
	}

	/** Ranged setups whose ammunition an Ava's device recovers, the only ones the cape requirement applies to. */
	private static boolean firesRecoverableAmmo(SetupResult setup)
	{
		AttackStyle style = setup.getLoadout().getStyle();
		return style != null && WeaponRules.firesRecoverableAmmo(setup.getLoadout().getWeapon(), WeaponRules.classOf(style));
	}
}
