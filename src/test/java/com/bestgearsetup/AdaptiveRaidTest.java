package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.SearchDepth;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.ui.BestGearSetupPanel;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.Test;

/** Real event scheduling and real encounter calculations, with only the game API and UI mocked. */
public class AdaptiveRaidTest
{
	@org.junit.Before public void initializeCacheIdentity()
	{
		CacheIdentity.init(net.runelite.http.api.RuneLiteAPI.GSON);
	}

	private final BestGearSetupPlugin plugin = new BestGearSetupPlugin();
	private final Client client = mock(Client.class);
	private final Player player = mock(Player.class);
	private final WorldView world = mock(WorldView.class);
	private final NPC boss = mock(NPC.class);
	private final Map<Integer, Integer> bits = new HashMap<>();
	private final AtomicInteger ticks = new AtomicInteger();
	private final Queue<FutureTask<Void>> jobs = new ArrayDeque<>();
	private final List<SearchResults> published = new ArrayList<>();
	private final List<String> publishedEncounters = new ArrayList<>();
	private RaidPlan plan;
	private LiveSpecs specs;
	private boolean specials;

	@SuppressWarnings("unchecked")
	@Before public void setUp() throws Exception
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
		GameDataLoader loader = new GameDataLoader(RuneLiteAPI.GSON);
		GameData full = loader.loadGameData();
		List<GearItem> weapons = new ArrayList<>();
		for (int id : new int[]{4151, 4587, 11804, 12899, 11791}) { weapons.add(full.findItem(id)); }
		GameData data = new GameData(full.getMonsters(), Collections.emptyMap(), weapons, full.getSpells(),
			Collections.emptyMap(), Collections.emptyMap());
		plugin.gameData = data;
		plugin.config = new BestGearSetupConfig()
		{
			@Override public SearchMode mode() { return SearchMode.UNLIMITED; }
			@Override public SearchDepth searchDepth() { return SearchDepth.FAST; }
			@Override public boolean killSpecials() { return specials; }
			@Override public boolean usePrayers() { return false; }
			@Override public boolean thrall() { return false; }
			@Override public int specBgsDamage() { return 100; }
			@Override public boolean toaWalkThePath() { return true; }
			@Override public RaidOptions.ToaPathOrder toaPathOrder() { return RaidOptions.ToaPathOrder.ZEBAK_FIRST; }
		};
		plugin.panel = mock(BestGearSetupPanel.class);
		when(plugin.panel.getRaidType()).thenReturn(RaidPlanType.TOA);
		plugin.ownedItems = new OwnedItems(null, null);
		plugin.ownedItems.inventory = Map.of(4587, 1L, 11804, 1L);
		plugin.ownedItems.bank = Map.of(4151, 1L);
		plugin.ownedItems.rebuild(false);
		set("client", client); set("api", loader); set("configManager", mock(ConfigManager.class));
		plan = RaidProgressTest.plan(data, RaidPlanType.TOA);
		List<RaidEncounter> encounters = RaidPlanType.TOA.encounters(data, RaidScaling.Settings.SOLO, RaidOptions.of(plugin.config));
		List<SearchResults> orderedResults = new ArrayList<>();
		List<com.bestgearsetup.calc.SetupResult> orderedSetups = new ArrayList<>();
		for (RaidEncounter encounter : encounters)
		{
			int index = plan.getEncounters().indexOf(plan.getEncounters().stream()
				.filter(e -> e.getLabel().equals(encounter.getLabel())).findFirst().get());
			orderedResults.add(plan.getResults().get(index)); orderedSetups.add(plan.getSetups().get(index));
		}
		plan = plan.withEncounters(encounters, orderedResults, orderedSetups);
		set("raidPlan", plan);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(player); when(client.getTopLevelWorldView()).thenReturn(world);
		when(client.getWorldView(WorldView.TOPLEVEL)).thenReturn(world);
		when(client.getRealSkillLevel(any())).thenReturn(99); when(client.getBoostedSkillLevel(any())).thenReturn(99);
		when(client.getTickCount()).thenAnswer(call -> ticks.get());
		when(client.getVarbitValue(anyInt())).thenAnswer(call -> bits.getOrDefault(call.getArgument(0), 0));
		bits.put(VarbitID.TOA_CLIENT_PARTYSTATUS, 2); bits.put(VarbitID.TOA_CLIENT_P0, 1);
		bits.put(VarbitID.TOA_CLIENT_RAID_LEVEL, 350); bits.put(VarbitID.TOA_CLIENT_APMEKEN_LEVEL, 0);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(mock(ItemContainer.class));
		when(client.getItemContainer(InventoryID.WORN)).thenReturn(mock(ItemContainer.class));
		MonsterSummary target = encounters.stream().filter(e -> e.getLabel().equals("Ba-Ba")).findFirst().get().getTarget();
		when(boss.getId()).thenReturn(target.getId()); when(boss.getName()).thenReturn("Ba-Ba");
		when(boss.getIndex()).thenReturn(7); when(boss.getHealthRatio()).thenReturn(-1);
		NPCComposition composition = mock(NPCComposition.class); when(boss.getTransformedComposition()).thenReturn(composition);
		when(composition.getActions()).thenReturn(new String[]{"Attack"});
		IndexedObjectSet<NPC> npcs = mock(IndexedObjectSet.class);
		when(npcs.iterator()).thenAnswer(call -> Collections.singletonList(boss).iterator());
		when(npcs.byIndex(7)).thenReturn(boss); when(world.npcs()).thenReturn((IndexedObjectSet) npcs);
		region(15188);
		specs = new LiveSpecs(client, mock(ClientThread.class), plugin.config, ignored -> { }); set("liveSpecs", specs);
		ExecutorService executor = mock(ExecutorService.class); plugin.executor = executor;
		doAnswer(call ->
		{
			FutureTask<Void> task = new FutureTask<>(call.getArgument(0), null); jobs.add(task); return task;
		}).when(executor).submit(any(Runnable.class));
		doAnswer(call -> { published.add(call.getArgument(2)); publishedEncounters.add(call.<RaidEncounter>getArgument(1).getLabel()); return null; }).when(plugin.panel)
			.showLiveRaidResults(any(RaidPlan.class), any(), any(SearchResults.class));
	}

	private void set(String name, Object value) throws Exception
	{
		Field field = BestGearSetupPlugin.class.getDeclaredField(name); field.setAccessible(true); field.set(plugin, value);
	}

	private void region(int region)
	{
		when(world.isInstance()).thenReturn(true);
		int template = ((region >> 8) * 8 << 14) | ((region & 255) * 8 << 3);
		when(world.getInstanceTemplateChunks()).thenReturn(new int[][][]{{{template}}});
		when(player.getLocalLocation()).thenReturn(new LocalPoint(64, 64, WorldView.TOPLEVEL));
	}

	private void tick() throws Exception
	{
		ticks.addAndGet(3); plugin.onGameTick(new GameTick()); SwingUtilities.invokeAndWait(() -> { });
	}

	private SearchResults work() throws Exception
	{
		assertFalse("An adaptive encounter job must be scheduled", jobs.isEmpty());
		FutureTask<Void> task = jobs.remove(); task.run(); task.get(); SwingUtilities.invokeAndWait(() -> { });
		assertFalse("The real calculation must publish a result", published.isEmpty());
		return published.get(published.size() - 1);
	}

	@Test public void alternateRouteUsesActualScalingAndCarriedItemsWithoutChangingThePackingPlan() throws Exception
	{
		assertEquals(RaidOptions.ToaPathOrder.ZEBAK_FIRST, ToaPathLevels.orderOf(plan.getEncounters()));
		tick(); SearchResults first = work();
		assertEquals("Ba-Ba", plugin.raidProgress.current().getEncounter());
		assertFalse(first.isEmpty());
		assertTrue(first.getRaidScaling(), first.getRaidScaling().contains("path level 0"));
		assertEquals("Predicted BGS damage must not be used before any spec lands", first.getBaseDefence(), first.getMonster().getDefenceLevel());
		assertTrue(first.getByType().values().stream().flatMap(List::stream)
			.flatMap(s -> java.util.Arrays.stream(Slot.values()).map(s.getLoadout()::get))
			.filter(java.util.Objects::nonNull).noneMatch(i -> i.getId() == 4151));
		assertEquals(0, first.getByType().values().iterator().next().get(0).getBuyCost());
		Field saved = BestGearSetupPlugin.class.getDeclaredField("raidPlan"); saved.setAccessible(true);
		assertTrue("The original packing plan stays intact", saved.get(plugin) == plan);
		tick(); assertTrue("Unchanged circumstances do not schedule another calculation", jobs.isEmpty());
		bits.put(VarbitID.TOA_CLIENT_APMEKEN_LEVEL, 4); tick();
		assertTrue(work().getRaidScaling().contains("path level 4"));
	}

	@Test public void landedSpecsAndMissesRefreshActualDefenceAndRemainingEnergy() throws Exception
	{
		tick(); SearchResults untouched = work();
		specs.partySpec(7, "BANDOS_GODSWORD", 10); tick(); SearchResults hit = work();
		assertTrue(hit.getMonster().getDefenceLevel() < untouched.getMonster().getDefenceLevel());
		specs.partySpec(7, "BANDOS_GODSWORD", 10); tick(); SearchResults extra = work();
		assertTrue(extra.getMonster().getDefenceLevel() < hit.getMonster().getDefenceLevel());
		when(client.getVarpValue(anyInt())).thenReturn(500);
		specs.partySpec(7, "BANDOS_GODSWORD", 0); tick(); SearchResults miss = work();
		assertEquals(extra.getMonster().getDefenceLevel(), miss.getMonster().getDefenceLevel());
		assertTrue(miss.getNotes().stream().anyMatch(note -> note.contains("50% special energy")));
	}

	@Test public void gearTransfersSettleBeforeRefreshAndStaleResultsCannotPublishAfterLeaving() throws Exception
	{
		tick(); work();
		plugin.ownedItems.inventory = Map.of(4151, 1L); tick();
		assertTrue("First transfer tick must not calculate from remembered carried items", jobs.isEmpty());
		tick(); SearchResults changed = work();
		assertTrue(changed.getByType().values().stream().flatMap(List::stream).allMatch(s -> s.getLoadout().getWeapon().getId() == 4151));
		bits.put(VarbitID.TOA_CLIENT_APMEKEN_LEVEL, 2); tick();
		assertEquals(1, jobs.size()); int count = published.size();
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		GameStateChanged logout = new GameStateChanged(); logout.setGameState(GameState.LOGIN_SCREEN);
		plugin.onGameStateChanged(logout);
		FutureTask<Void> stale = jobs.remove(); stale.run(); stale.get(); SwingUtilities.invokeAndWait(() -> { });
		assertEquals(count, published.size());
	}

	@Test public void noCarriedWeaponShowsUnavailableInsteadOfBorrowingBankGear() throws Exception
	{
		plugin.ownedItems.inventory = Collections.emptyMap(); plugin.ownedItems.rebuild(false);
		tick(); assertTrue(work().isEmpty());
	}

	@Test public void changingCircumstancesCoalesceAndOnlyTheNewestResultCanPublish() throws Exception
	{
		tick(); assertEquals(1, jobs.size());
		bits.put(VarbitID.TOA_CLIENT_APMEKEN_LEVEL, 3); tick();
		assertEquals("A running refresh must not queue duplicate calculations", 1, jobs.size());
		FutureTask<Void> stale = jobs.remove(); stale.run(); stale.get(); SwingUtilities.invokeAndWait(() -> { });
		assertTrue("A result for obsolete path levels must not replace the live view", published.isEmpty());
		tick(); assertTrue(work().getRaidScaling().contains("path level 3"));
	}

	@Test public void discardedInputsCanBeRequestedAgainWhenTheLiveStateReturns() throws Exception
	{
		tick();
		when(client.getBoostedSkillLevel(net.runelite.api.Skill.STRENGTH)).thenReturn(100);
		tick();
		FutureTask<Void> stale = jobs.remove(); stale.run(); stale.get(); SwingUtilities.invokeAndWait(() -> { });
		assertTrue(published.isEmpty());
		when(client.getBoostedSkillLevel(net.runelite.api.Skill.STRENGTH)).thenReturn(99);
		tick(); assertFalse(work().isEmpty());
		tick(); assertTrue("A successfully published result still prevents duplicate work", jobs.isEmpty());
	}

	@Test public void looseRuneChangesRefreshButEquivalentSubstitutesAndRedundantStacksDoNot() throws Exception
	{
		when(boss.getId()).thenReturn(NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE);
		when(boss.getName()).thenReturn("Elidinis' Warden"); region(15184);
		plugin.ownedItems.inventory = Map.of(11791, 1L, ItemID.FIRERUNE, 100L); plugin.ownedItems.rebuild(false);
		tick(); assertTrue("No runes for a usable damage spell", work().isEmpty());
		plugin.ownedItems.inventory = Map.of(11791, 1L, ItemID.FIRERUNE, 100L, ItemID.BLOODRUNE, 100L);
		tick(); tick(); assertFalse("Blood runes enable Undead Grasp", work().isEmpty());
		plugin.ownedItems.inventory = Map.of(11791, 1L, ItemID.SUNFIRERUNE, 100L, ItemID.BLOODRUNE, 100L);
		tick(); tick(); assertTrue("Sunfire replaces fire without changing spell availability", jobs.isEmpty());
		plugin.ownedItems.inventory = Map.of(11791, 1L, ItemID.SUNFIRERUNE, 1L, ItemID.FIRERUNE, 50L, ItemID.BLOODRUNE, 20L);
		tick(); tick(); assertTrue("Redundant runes and positive stack sizes do not change availability", jobs.isEmpty());
		plugin.ownedItems.inventory = Map.of(11791, 1L, ItemID.BLOODRUNE, 20L);
		tick(); tick(); assertTrue("Losing every fire substitute invalidates the spell", work().isEmpty());
	}

	@Test public void missingThrallSuppliesKeepOrdinaryAttacksAndRecoverWhenRestored() throws Exception
	{
		plugin.config = SearchAssumptions.capture(plugin.config, Map.of("thrall", true));
		when(client.getIntStack()).thenReturn(new int[]{2});
		Map<Integer, Long> supplies = Map.of(4587, 1L, ItemID.BOOK_OF_THE_DEAD, 1L,
			ItemID.FIRERUNE, 100L, ItemID.BLOODRUNE, 100L, ItemID.COSMICRUNE, 100L);
		plugin.ownedItems.inventory = supplies; plugin.ownedItems.rebuild(false);
		tick(); SearchResults complete = work();
		assertFalse(complete.isEmpty());
		assertTrue(complete.getByType().values().stream().flatMap(List::stream)
			.allMatch(s -> s.getSupportSpells().contains(com.bestgearsetup.calc.SupportSpell.THRALL)));
		for (int missing : new int[]{ItemID.COSMICRUNE, ItemID.BOOK_OF_THE_DEAD})
		{
			Map<Integer, Long> reduced = new HashMap<>(supplies); reduced.remove(missing);
			plugin.ownedItems.inventory = reduced;
			tick(); tick(); SearchResults ordinary = work();
			assertFalse("Missing optional supplies must not remove combat gear", ordinary.isEmpty());
			assertTrue(ordinary.getByType().values().stream().flatMap(List::stream)
				.allMatch(s -> s.getSupportSpells().isEmpty()));
			plugin.ownedItems.inventory = supplies;
			tick(); tick(); SearchResults restored = work();
			assertFalse(restored.isEmpty());
			assertTrue(restored.getByType().values().stream().flatMap(List::stream)
				.allMatch(s -> s.getSupportSpells().contains(com.bestgearsetup.calc.SupportSpell.THRALL)));
		}
	}

	@Test public void liveThrallsFollowQuestAndSpellbookEligibility() throws Exception
	{
		plugin.config = SearchAssumptions.capture(plugin.config, Map.of("thrall", true));
		when(client.getIntStack()).thenReturn(new int[]{1});
		tick(); SearchResults locked = work();
		assertFalse("Quest-locked thralls must not prevent ordinary melee gear", locked.isEmpty());
		assertTrue(locked.getByType().values().stream().flatMap(List::stream).allMatch(s -> s.getSupportSpells().isEmpty()));
		assertTrue(locked.getNotes().stream().anyMatch(note -> note.contains("A Kingdom Divided isn't complete")));
		plugin.ownedItems.inventory = Map.of(4587, 1L, ItemID.BOOK_OF_THE_DEAD, 1L, ItemID.FIRERUNE, 100L,
			ItemID.BLOODRUNE, 100L, ItemID.COSMICRUNE, 100L);
		when(client.getIntStack()).thenReturn(new int[]{2});
		tick(); tick(); SearchResults unlocked = work();
		assertFalse(unlocked.isEmpty());
		assertTrue(unlocked.getByType().values().stream().flatMap(List::stream)
			.allMatch(s -> s.getSupportSpells().contains(com.bestgearsetup.calc.SupportSpell.THRALL)));
		plugin.config = SearchAssumptions.capture(plugin.config, Map.of("arceuusSpellbook", false));
		tick(); SearchResults disabled = work();
		assertFalse(disabled.isEmpty());
		assertTrue(disabled.getByType().values().stream().flatMap(List::stream).allMatch(s -> s.getSupportSpells().isEmpty()));
	}

	@SuppressWarnings("unchecked")
	@Test public void wardenStyleRefreshKeepsTheExactRowAndUsesThePhysicalCoreForLiveCalculations() throws Exception
	{
		plugin.ownedItems.inventory = Map.of(12899, 1L, 4587, 1L); plugin.ownedItems.rebuild(false);
		when(boss.getId()).thenReturn(NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE);
		when(boss.getName()).thenReturn("Elidinis' Warden"); region(15184);
		tick(); SearchResults magic = work();
		assertEquals("Elidinis P2: Magic", publishedEncounters.get(publishedEncounters.size() - 1));
		assertFalse(magic.isEmpty());
		assertTrue(magic.getByType().values().stream().flatMap(List::stream)
			.allMatch(s -> s.getCombatClass() == com.bestgearsetup.data.CombatClass.MAGIC));
		when(boss.getId()).thenReturn(NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE);
		tick(); assertTrue("No ranged gear is carried; the magic setup must not be reused", work().isEmpty());
		assertEquals("Elidinis P2: Ranged", publishedEncounters.get(publishedEncounters.size() - 1));
		when(boss.getId()).thenReturn(NpcID.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED);
		when(boss.getHealthRatio()).thenReturn(0); tick();
		assertEquals("Elidinis Core", plugin.raidProgress.current().getEncounter());
		assertTrue("No physical core yet: show the saved setup without calculating against the fallen body", jobs.isEmpty());
		NPC core = mock(NPC.class);
		when(core.getId()).thenReturn(NpcID.TOA_WARDEN_ELIDINIS_CORE); when(core.getName()).thenReturn("<col=00ffff>Core</col>");
		when(core.getIndex()).thenReturn(8); when(core.getHealthRatio()).thenReturn(-1);
		NPCComposition coreComposition = boss.getTransformedComposition();
		when(core.getTransformedComposition()).thenReturn(coreComposition);
		IndexedObjectSet<NPC> npcs = (IndexedObjectSet<NPC>) (IndexedObjectSet) world.npcs();
		when(npcs.iterator()).thenAnswer(call -> java.util.Arrays.asList(boss, core).iterator());
		tick(); SearchResults coreResult = work();
		assertEquals("Elidinis Core", publishedEncounters.get(publishedEncounters.size() - 1));
		assertFalse(coreResult.isEmpty());
		assertTrue(coreResult.getByType().values().stream().flatMap(List::stream)
			.allMatch(s -> s.getCombatClass() == com.bestgearsetup.data.CombatClass.MELEE));
	}

	@Test public void boostedLevelsHealthAndAvailableEnergyAreReadFromTheLiveFight() throws Exception
	{
		tick(); SearchResults base = work();
		when(client.getBoostedSkillLevel(net.runelite.api.Skill.STRENGTH)).thenReturn(125);
		tick(); SearchResults stronger = work();
		assertTrue(stronger.getByType().values().stream().flatMap(List::stream).mapToDouble(s -> s.getDps().getPrimaryDps()).max().getAsDouble()
			> base.getByType().values().stream().flatMap(List::stream).mapToDouble(s -> s.getDps().getPrimaryDps()).max().getAsDouble());
		when(boss.getHealthRatio()).thenReturn(4); when(boss.getHealthScale()).thenReturn(10);
		when(client.getVarpValue(anyInt())).thenReturn(250);
		specials = true; tick(); SearchResults boosted = work();
		assertEquals((base.getMonster().getHitpoints() * 4 + 9) / 10, boosted.getStartingHitpoints());
		assertTrue(boosted.getByType().values().stream().flatMap(List::stream).allMatch(setup -> setup.getSpecial() == null
			|| setup.getSpecial().getOpeningEnergy() <= 25));
		assertTrue(boosted.getNotes().stream().anyMatch(note -> note.contains("25% special energy")));
		assertTrue(boosted.getPotions().values().stream().allMatch(List::isEmpty));
	}

	@Test public void liveRotationsRespectEachAvailableEnergyBudgetEvenIfThePlanSpentNoOpeningBar() throws Exception
	{
		specials = true;
		for (int energy : new int[]{0, 25, 50, 100})
		{
			when(client.getVarpValue(anyInt())).thenReturn(energy * 10);
			tick(); SearchResults result = work();
			assertFalse(result.isEmpty());
			assertTrue(result.getByType().values().stream().flatMap(List::stream).allMatch(setup -> setup.getSpecial() == null
				|| setup.getSpecial().getOpeningEnergy() <= energy));
			if (energy == 100)
			{ assertTrue(result.getByType().values().stream().flatMap(List::stream).anyMatch(s -> s.getSpecial() != null && s.getSpecial().getOpeningEnergy() > 0)); }
		}
	}
}
