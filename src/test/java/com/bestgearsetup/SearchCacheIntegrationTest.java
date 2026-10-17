package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.RaidLoadout;
import com.bestgearsetup.calc.SearchDepth;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.ui.BestGearSetupPanel;
import com.google.gson.Gson;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleConsumer;
import javax.swing.SwingUtilities;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.RuneScapeProfileChanged;
import org.junit.Before;
import org.junit.Test;

/** The actual optimizer and kit planner run once; cache hits must bypass them, not just paint faster. */
public class SearchCacheIntegrationTest
{
	private static final class Loader extends GameDataLoader
	{
		int reads;
		boolean fail;
		Runnable afterRead = () -> { };

		Loader() { super(new Gson()); }

		@Override public Monster getMonster(MonsterSummary summary) throws IOException
		{
			reads++;
			if (fail) { throw new IOException("test failure"); }
			Monster monster = super.getMonster(summary);
			afterRead.run();
			return monster;
		}
	}

	private final Loader loader = new Loader();
	private final BestGearSetupPlugin plugin = new BestGearSetupPlugin();
	/** Records every ownership query so the cache key can be checked against what searches actually read. */
	private static final class Owned extends HashSet<Integer>
	{
		final Set<Object> queried = new HashSet<>();

		@Override public boolean contains(Object id)
		{
			queried.add(id);
			return super.contains(id);
		}
	}

	private final Owned owned = new Owned();
	private final Map<Integer, Long> quantities = new HashMap<>();
	private final BestGearSetupConfig base = new BestGearSetupConfig()
	{
		@Override public SearchMode mode() { return SearchMode.OWNED_ONLY; }
		@Override public SearchDepth searchDepth() { return SearchDepth.FAST; }
		@Override public boolean killSpecials() { return false; }
		@Override public boolean usePrayers() { return false; }
		@Override public boolean thrall() { return false; }
		@Override public boolean requireFireProtection() { return false; }
		@Override public RaidSpellbook raidSpellbook() { return RaidSpellbook.ARCEUUS; }
		@Override public int raidEquipmentLimit() { return 28; }
		@Override public int ammoCount() { return 100; }
	};
	private GameData data;
	private MonsterSummary target;
	private RaidPlan shown;
	private final List<Double> progress = new ArrayList<>();
	private Method raidSearch;
	private Method encounterSearch;

	@Before
	public void setUp() throws Exception
	{
		OwnershipRules.init(net.runelite.http.api.RuneLiteAPI.GSON);
		GameData full = loader.loadGameData();
		Set<String> names = Set.of("abyssal whip", "magic shortbow", "trident of the swamp", "rune arrow", "ava's accumulator");
		Map<String, List<GearItem>> equipment = new HashMap<>();
		List<GearItem> weapons = new ArrayList<>();
		for (Slot slot : Slot.values())
		{
			List<GearItem> selected = new ArrayList<>();
			for (GearItem item : full.getItems(slot))
			{
				if (names.contains(item.getName()))
				{
					selected.add(item); owned.add(item.getId()); quantities.put(item.getId(), 1000L);
				}
			}
			if (slot == Slot.WEAPON) { weapons.addAll(selected); }
			else { equipment.put(slot.getApiKey(), selected); }
		}
		data = new GameData(full.getMonsters(), equipment, weapons, full.getSpells(), Collections.emptyMap(), Collections.emptyMap());
		target = RaidPlanType.TOB.encounters(data).get(0).getTarget();
		plugin.config = base;
		plugin.ownedItems = mock(OwnedItems.class);
		plugin.panel = mock(BestGearSetupPanel.class);
		Field api = BestGearSetupPlugin.class.getDeclaredField("api"); api.setAccessible(true); api.set(plugin, loader);
		raidSearch = Arrays.stream(BestGearSetupPlugin.class.getDeclaredMethods())
			.filter(method -> method.getName().equals("runRaidSearch")).findFirst().get();
		raidSearch.setAccessible(true);
		encounterSearch = Arrays.stream(BestGearSetupPlugin.class.getDeclaredMethods())
			.filter(method -> method.getName().equals("computeSearch"))
			.max(java.util.Comparator.comparingInt(Method::getParameterCount)).get();
		encounterSearch.setAccessible(true);
		doAnswer(call -> { shown = call.getArgument(0); return null; }).when(plugin.panel).showRaidResults(any(RaidPlan.class), any());
	}

	private void raid(BestGearSetupConfig config) throws Exception
	{
		shown = null; progress.clear();
		raidSearch.invoke(plugin, data, target, PlayerLevels.maxed(), false, Collections.emptySet(), true, 99,
			Collections.emptyMap(), owned, quantities, plugin.searchGeneration.incrementAndGet(), config, new LiveRaid(),
			Collections.emptyList(), RaidPlanType.TOB, (DoubleConsumer) progress::add);
	}

	private SearchResults encounter(PlayerLevels levels, Map<Integer, Long> prices) throws Exception
	{
		return encounter(base, levels, prices);
	}

	private SearchResults encounter(BestGearSetupConfig config, PlayerLevels levels, Map<Integer, Long> prices) throws Exception
	{
		return (SearchResults) encounterSearch.invoke(plugin, data, target, levels, false, Collections.emptySet(), true,
			false, 99, prices, owned, quantities, plugin.searchGeneration.get(), config, new LiveRaid(), Collections.emptyList(),
			Collections.emptySet(), Collections.emptySet(), null, RaidLoadout.ANY, (DoubleConsumer) percent -> { }, null, null);
	}

	private static void flush() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> { });
	}

	@Test
	public void repeatedRaidSkipsCalculationsAndSlotLimitChangeReusesEncounters() throws Exception
	{
		long firstStart = System.nanoTime(); raid(base); flush(); long firstNanos = System.nanoTime() - firstStart;
		RaidPlan first = shown;
		assertNotNull(first);
		int reads = loader.reads;
		assertTrue(reads > 1);
		long repeatStart = System.nanoTime(); raid(base); flush(); long repeatNanos = System.nanoTime() - repeatStart;
		assertSame(first, shown);
		assertEquals("An exact repeat must skip both encounter calculations and kit planning", reads, loader.reads);
		assertEquals(List.of(100.0), progress);
		BestGearSetupConfig fewerSlots = SearchAssumptions.capture(base, Map.of("raidEquipmentLimit", 27));
		raid(fewerSlots); flush();
		assertNotNull(shown);
		assertNotSame("A different kit limit must re-plan the kit", first, shown);
		assertEquals(27, shown.getEquipmentLimit());
		assertEquals("Unchanged encounters must be reused while re-planning", reads, loader.reads);
		raid(base); flush(); assertSame("Previous settings remain reusable", first, shown);
		System.out.printf(Locale.ROOT, "Small real ToB kit: first %.1f ms; repeated %.1f ms; %d monster calculations skipped%n",
			firstNanos / 1e6, repeatNanos / 1e6, reads);
	}

	@Test
	public void raidCacheSurvivesMovingTheSameOwnedKitBetweenContainers() throws Exception
	{
		OwnedItems tracker = new OwnedItems(null, null);
		tracker.bank = new HashMap<>(quantities);
		tracker.rebuild(true);
		owned.clear(); owned.addAll(tracker.snapshot());
		raid(base); flush();
		RaidPlan first = shown;
		assertNotNull(first);
		int reads = loader.reads;
		tracker.inventory = tracker.bank;
		tracker.bank = Collections.emptyMap();
		tracker.settle(); tracker.settle();
		owned.clear(); owned.addAll(tracker.snapshot());
		quantities.clear(); quantities.putAll(tracker.quantitySnapshot());
		raid(base); flush();
		assertSame(first, shown);
		tracker.worn = tracker.inventory;
		tracker.inventory = Collections.emptyMap();
		tracker.settle(); tracker.settle();
		owned.clear(); owned.addAll(tracker.snapshot());
		quantities.clear(); quantities.putAll(tracker.quantitySnapshot());
		raid(base); flush();
		assertSame(first, shown);
		assertEquals("Bank withdrawal and equip must skip both optimizer and raid planner", reads, loader.reads);
	}

	@Test
	public void persistedRaidRestoresAfterMemoryCacheIsRecreated() throws Exception
	{
		ProfileSearchCacheTest profile = new ProfileSearchCacheTest(); profile.profile();
		plugin.raidCache.profile(ProfileSearchCache.raids(profile.config, new Gson(), Runnable::run));
		plugin.encounterCache.profile(ProfileSearchCache.encounters(profile.config, new Gson(), Runnable::run));
		raid(base); flush();
		RaidPlan first = shown;
		assertNotNull(first);
		new SearchResultCodec(new Gson()).encode(first);
		assertTrue("The completed real raid must be persisted", profile.records.size() > 1);
		assertTrue("A raid's own encounter searches stay in memory, not the profile", profile.records.keySet().stream()
			.noneMatch(name -> name.matches("one:searchCache\\.encounter\\.[0-9a-f]{64}")));
		int reads = loader.reads;
		// A restart: both memory layers are new and only the profile remains.
		plugin.raidEncounterCache.clear();
		plugin.raidCache.profile(ProfileSearchCache.raids(profile.config, new Gson(), Runnable::run));
		plugin.encounterCache.profile(ProfileSearchCache.encounters(profile.config, new Gson(), Runnable::run));
		raid(base); flush();
		assertNotNull(shown);
		assertNotSame(first, shown);
		assertEquals("A restart cache hit must bypass all encounter calculations", reads, loader.reads);
		assertEquals(first.getEquipmentLimit(), shown.getEquipmentLimit());
		assertEquals(first.getEstimatedSeconds(), shown.getEstimatedSeconds(), 0.000001);
		assertEquals(first.getInventory().size(), shown.getInventory().size());
		assertSame(shown.getEncounters().get(shown.getStartIndex()), shown.getStartingEncounter());
		for (SetupResult setup : shown.getSetups())
		{
			assertNotNull(setup.getLoadout().getWeapon().getSlot());
			GearItem weapon = setup.getLoadout().getWeapon();
			assertSame(data.getItem(Slot.WEAPON, weapon.getId()), weapon);
		}
	}

	@Test
	public void changedPricesAndLevelsMissAndReturningToPriorInputsHits() throws Exception
	{
		BestGearSetupConfig budget = SearchAssumptions.capture(base, Map.of("mode", SearchMode.BUDGET));
		SearchResults first = encounter(budget, PlayerLevels.maxed(), Map.of(892, 10L));
		int reads = loader.reads;
		assertSame(first, encounter(budget, PlayerLevels.maxed(), Map.of(892, 10L)));
		assertEquals(reads, loader.reads);
		assertNotSame(first, encounter(budget, PlayerLevels.maxed(), Map.of(892, 11L)));
		assertEquals(reads + 1, loader.reads);
		assertNotSame(first, encounter(budget, new PlayerLevels(90, 90, 90, 90, 90, 90, 90, 90), Map.of(892, 10L)));
		assertEquals(reads + 2, loader.reads);
		assertSame(first, encounter(budget, PlayerLevels.maxed(), Map.of(892, 10L)));
		assertEquals(reads + 2, loader.reads);
	}

	@Test
	public void heldOnlySearchSurvivesAPriceRefreshUnlessTheWildernessCapPricesOwnedGear() throws Exception
	{
		SearchResults first = encounter(PlayerLevels.maxed(), Map.of(892, 10L));
		int reads = loader.reads;
		assertSame("Owned-only searches buy nothing, so a GE price refresh must not re-search",
			first, encounter(PlayerLevels.maxed(), Map.of(892, 11L)));
		assertEquals(reads, loader.reads);
		BestGearSetupConfig capped = SearchAssumptions.capture(base, Map.of("wilderness", true, "limitWildernessRisk", true));
		SearchResults wilderness = encounter(capped, PlayerLevels.maxed(), Map.of(892, 10L));
		assertNotSame(wilderness, encounter(capped, PlayerLevels.maxed(), Map.of(892, 11L)));
		assertEquals(reads + 2, loader.reads);
	}

	/**
	 * Held-only keys ignore prices, so their results must not store any: the views price owned gear at display time
	 * from the snapshot each search takes, which keeps a cached result's costs current.
	 */
	@Test
	public void heldOnlyResultsStoreNoPricesForTheViewsToRepriceOnLoad() throws Exception
	{
		Map<Integer, Long> prices = new HashMap<>();
		owned.forEach(id -> prices.put(id, 1_000_000L));
		SearchResults found = encounter(PlayerLevels.maxed(), prices);
		List<SetupResult> setups = new ArrayList<>();
		found.getByType().values().forEach(setups::addAll);
		raid(base); flush();
		assertNotNull(shown);
		setups.addAll(shown.getSetups());
		assertTrue(setups.size() > 1);
		for (SetupResult setup : setups)
		{
			assertEquals("Owned gear must not be costed into a cached result", 0, setup.getBuyCost());
			assertEquals(0, setup.getUnpricedItems());
		}
		assertEquals(0, shown.getBuyCost());
	}

	@Test
	public void lootAndCoinsHitButNewGearPotionsOrPouchesMiss() throws Exception
	{
		SearchResults first = encounter(PlayerLevels.maxed(), Collections.emptyMap());
		int reads = loader.reads;
		owned.add(ItemID.COINS); owned.add(ItemID.BONES); owned.add(ItemID.SHARK);
		assertSame("Items no search reads must not change the key", first, encounter(PlayerLevels.maxed(), Collections.emptyMap()));
		assertEquals(reads, loader.reads);
		Set<Integer> relevant = SearchInputs.ownershipIds(data);
		assertTrue(relevant.contains(ItemID.DIVINE_RUNE_POUCH));
		GameData full = loader.loadGameData();
		assertTrue("Boosts and their divine versions are keyed", SearchInputs.ownershipIds(full).containsAll(
			List.of(ItemID._4DOSE2COMBAT, ItemID._4DOSEDIVINECOMBAT, ItemID._4DOSERANGERSPOTION)));
		owned.add(ItemID.DIVINE_RUNE_POUCH);
		assertNotSame(first, encounter(PlayerLevels.maxed(), Collections.emptyMap()));
		assertEquals(reads + 1, loader.reads);
	}

	@Test
	public void everyOwnershipQueryIsPartOfTheKey() throws Exception
	{
		owned.add(ItemID.COINS);
		raid(base); flush();
		assertNotNull(shown);
		encounter(SearchAssumptions.capture(base, Map.of("mode", SearchMode.BUDGET)), PlayerLevels.maxed(), Collections.emptyMap());
		assertTrue(owned.queried.size() > 1);
		Set<Object> unkeyed = new HashSet<>(owned.queried);
		unkeyed.removeAll(SearchInputs.ownershipIds(data));
		assertEquals("A search read ownership the cache key ignores, so a cached result could go stale", Set.of(), unkeyed);
	}

	@Test
	public void failedAndCancelledEncountersAreNotCached() throws Exception
	{
		loader.fail = true;
		try
		{
			encounter(PlayerLevels.maxed(), Collections.emptyMap());
			org.junit.Assert.fail("The loader should fail");
		}
		catch (InvocationTargetException e) { assertTrue(e.getCause() instanceof IOException); }
		loader.fail = false;
		loader.afterRead = plugin.searchGeneration::incrementAndGet;
		assertNull(encounter(PlayerLevels.maxed(), Collections.emptyMap()));
		assertEquals(2, loader.reads);
		loader.afterRead = () -> { };
		assertNotNull(encounter(PlayerLevels.maxed(), Collections.emptyMap()));
		assertEquals("A completed retry must calculate again", 3, loader.reads);
	}

	@Test
	public void realRaidSearchLeavesEveryProfileWriteToTheClientThread() throws Exception
	{
		ProfileSearchCacheTest profile = new ProfileSearchCacheTest(); profile.profile();
		List<Runnable> clientThread = new ArrayList<>();
		plugin.clientThread = mock(ClientThread.class);
		doAnswer(call -> { clientThread.add(call.getArgument(0)); return null; }).when(plugin.clientThread).invokeLater(any(Runnable.class));
		for (Map.Entry<String, Object> injected : Map.<String, Object>of("configManager", profile.config, "gson", new Gson()).entrySet())
		{
			Field field = BestGearSetupPlugin.class.getDeclaredField(injected.getKey());
			field.setAccessible(true); field.set(plugin, injected.getValue());
		}
		Method clear = BestGearSetupPlugin.class.getDeclaredMethod("clearSearchCaches");
		clear.setAccessible(true); clear.invoke(plugin);
		raid(base); flush();
		assertNotNull(shown);
		assertTrue("The search itself must not write the profile, so no ConfigChanged runs on its thread", profile.records.isEmpty());
		assertTrue("Prunes and the raid save wait for the client thread", clientThread.size() > 2);
		clientThread.forEach(Runnable::run);
		assertEquals(1, profile.records.keySet().stream().filter(name -> name.matches("one:searchCache\\.raid\\.[0-9a-f]{64}")).count());
		long characters = profile.records.entrySet().stream().filter(entry -> entry.getKey().startsWith("one:searchCache."))
			.mapToLong(entry -> entry.getValue().length()).sum();
		assertTrue(characters + " characters", characters <= ProfileSearchCache.RAID_BYTES + ProfileSearchCache.ENCOUNTER_BYTES + 64 * 1024);
	}

	@Test
	public void accountChangeClearsBothLayersAndStaleCachedPublicationIsIgnored() throws Exception
	{
		raid(base); flush(); assertNotNull(shown);
		SwingUtilities.invokeAndWait(() ->
		{
			try { raid(base); } catch (Exception e) { throw new AssertionError(e); }
			plugin.searchGeneration.incrementAndGet();
		});
		flush(); assertNull("A cached callback must not replace a newer search", shown);
		int reads = loader.reads;
		plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged("old", "new"));
		flush();
		raid(base); flush();
		assertNotNull(shown);
		assertTrue("Both plan and encounter caches must be empty after an account change", loader.reads > reads);
	}
}
