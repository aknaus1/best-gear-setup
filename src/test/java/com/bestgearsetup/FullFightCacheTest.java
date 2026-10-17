package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.SearchDepth;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.ui.BestGearSetupPanel;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleConsumer;
import javax.swing.SwingUtilities;
import org.junit.Before;
import org.junit.Test;

/** A full fight's plan is cached like a raid's, and the phase it is opened from doesn't fragment the cache. */
public class FullFightCacheTest
{
	private static final class Loader extends GameDataLoader
	{
		int reads;

		Loader() { super(new Gson()); }

		@Override public Monster getMonster(MonsterSummary summary) throws IOException
		{
			reads++;
			return super.getMonster(summary);
		}
	}

	private final Loader loader = new Loader();
	private final BestGearSetupPlugin plugin = new BestGearSetupPlugin();
	private final Set<Integer> owned = new HashSet<>();
	private final Map<Integer, Long> quantities = new HashMap<>();
	private final BestGearSetupConfig base = new BestGearSetupConfig()
	{
		@Override public SearchMode mode() { return SearchMode.OWNED_ONLY; }
		@Override public SearchDepth searchDepth() { return SearchDepth.FAST; }
		@Override public boolean killSpecials() { return false; }
		@Override public boolean usePrayers() { return false; }
		@Override public boolean thrall() { return false; }
		@Override public int ammoCount() { return 100; }
	};
	private GameData data;
	private List<RaidEncounter> encounters;
	private RaidPlan shown;
	private MonsterSummary shownFrom;

	@Before
	public void setUp() throws Exception
	{
		CacheIdentity.init(net.runelite.http.api.RuneLiteAPI.GSON);
		OwnershipRules.init(net.runelite.http.api.RuneLiteAPI.GSON);
		GameData full = loader.loadGameData();
		Set<String> names = Set.of("toxic blowpipe", "trident of the swamp", "magic shortbow", "rune arrow",
			"black d'hide body", "ava's accumulator");
		Map<String, List<GearItem>> equipment = new HashMap<>();
		List<GearItem> weapons = new ArrayList<>();
		for (Slot slot : Slot.values())
		{
			List<GearItem> selected = new ArrayList<>();
			for (GearItem item : full.getItems(slot))
			{
				if (names.contains(item.getName())) { selected.add(item); owned.add(item.getId()); quantities.put(item.getId(), 1000L); }
			}
			if (slot == Slot.WEAPON) { weapons.addAll(selected); }
			else { equipment.put(slot.getApiKey(), selected); }
		}
		data = new GameData(full.getMonsters(), equipment, weapons, full.getSpells(), Collections.emptyMap(), Collections.emptyMap());
		encounters = RaidPlanType.ZULRAH.encounters(data);
		plugin.config = base;
		plugin.ownedItems = mock(OwnedItems.class);
		plugin.panel = mock(BestGearSetupPanel.class);
		plugin.api = loader;
		doAnswer(call -> { shown = call.getArgument(0); shownFrom = call.getArgument(1); return null; })
			.when(plugin.panel).showRaidResults(any(RaidPlan.class), any());
	}

	private MonsterSummary phase(int index)
	{
		return encounters.get(index).getTarget();
	}

	private RaidPlan fight(MonsterSummary from, BestGearSetupConfig config, List<String> notes) throws Exception
	{
		shown = null;
		plugin.runRaidSearch(data, from, PlayerLevels.maxed(), false, Collections.emptySet(), true, 99,
			Collections.emptyMap(), owned, quantities, plugin.searchGeneration.incrementAndGet(), config, new LiveRaid(),
			notes, RaidPlanType.ZULRAH, (DoubleConsumer) percent -> { });
		SwingUtilities.invokeAndWait(() -> { });
		assertNotNull(shown);
		return shown;
	}

	private RaidPlan fight(MonsterSummary from, BestGearSetupConfig config) throws Exception
	{
		return fight(from, config, Collections.emptyList());
	}

	@Test
	public void reopeningTheFightFromAnotherPhaseReusesThePlan() throws Exception
	{
		assertNotEquals("Zulrah's forms are separate targets", phase(0).getId(), phase(1).getId());
		RaidPlan first = fight(phase(0), base);
		int reads = loader.reads;
		assertTrue(reads > 1);
		assertSame(first, fight(phase(0), base));
		assertSame("Another phase opens the same planned fight", first, fight(phase(1), base));
		assertSame("The plan opens on the phase it was reopened from", phase(1), shownFrom);
		assertSame(first, fight(phase(2), base));
		assertEquals("Cache hits must skip every encounter search and the kit planner", reads, loader.reads);
	}

	@Test
	public void presentationAndBossStateChangesStillHit() throws Exception
	{
		RaidPlan first = fight(phase(0), base);
		int reads = loader.reads;
		Map<String, Object> presentation = new HashMap<>();
		presentation.put("highlightBankGear", false);
		presentation.put("highlightInventoryGear", false);
		presentation.put("bossesOnly", true);
		presentation.put("showMenuOption", false);
		presentation.put("activeEncounter", false);
		presentation.put("seenFightOptions", "everything");
		// Each phase sets its own boss state, so the picker's value never reaches the plan.
		presentation.put("encounterPhase", EncounterPhase.AKKHA_ENRAGE);
		assertSame(first, fight(phase(1), SearchAssumptions.capture(base, presentation)));
		assertEquals(reads, loader.reads);
	}

	@Test
	public void changedInputsPlanTheFightAgain() throws Exception
	{
		RaidPlan first = fight(phase(0), base);
		RaidPlan fewerSwitches = fight(phase(0), SearchAssumptions.capture(base, Map.of("quickSwitchLimit", 1)));
		assertNotSame("A different quick switch limit must re-plan the kit", first, fewerSwitches);
		RaidPlan fewerSlots = fight(phase(0), SearchAssumptions.capture(base, Map.of("raidEquipmentLimit", 12)));
		assertNotSame("A different kit limit must re-plan the kit", first, fewerSlots);
		int blowpipe = data.getItems(Slot.WEAPON).stream().filter(item -> item.getName().equals("toxic blowpipe"))
			.findFirst().get().getId();
		owned.remove(blowpipe);
		assertNotSame("Ownership changes must re-plan the kit", first, fight(phase(0), base));
		owned.add(blowpipe);
		assertNotSame("Detected notes change the plan", first, fight(phase(0), base, List.of("Thralls: detected")));
		assertSame("Earlier inputs remain cached", first, fight(phase(1), base));
	}

	@Test
	public void landedSpecsKeepThePhaseTheyLandedOn() throws Exception
	{
		BestGearSetupConfig specs = SearchAssumptions.capture(base, Map.of("specDwh", 1));
		RaidPlan serpentine = fight(phase(0), specs);
		RaidPlan magma = fight(phase(1), specs);
		assertNotSame("Landed specs reach only the searched phase, so it is part of the key", serpentine, magma);
		int reads = loader.reads;
		assertSame(serpentine, fight(phase(0), specs));
		assertSame(magma, fight(phase(1), specs));
		assertEquals(reads, loader.reads);
	}
}
