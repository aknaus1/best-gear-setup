package com.bestgearsetup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.Properties;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class ProfileSearchCacheTest
{
	@org.junit.Before public void initializeCacheIdentity()
	{
		CacheIdentity.init(net.runelite.http.api.RuneLiteAPI.GSON);
	}

	private final Gson gson = new Gson();
	final ConfigManager config = mock(ConfigManager.class);
	final Map<String, String> records = new HashMap<>();
	final AtomicReference<String> account = new AtomicReference<>("one");
	@Rule public final TemporaryFolder temporary = new TemporaryFolder();

	@Before public void profile()
	{
		when(config.getRSProfileKey()).thenAnswer(call -> account.get());
		when(config.getRSProfileConfigurationKeys(eq(BestGearSetupConfig.GROUP), anyString(), anyString()))
			.thenAnswer(call -> records.keySet().stream().filter(key -> key.startsWith(call.getArgument(1) + ":" + call.getArgument(2)))
				.map(key -> key.substring(key.indexOf(':') + 1)).collect(java.util.stream.Collectors.toList()));
		when(config.getConfiguration(eq(BestGearSetupConfig.GROUP), anyString(), anyString(), eq(String.class)))
			.thenAnswer(call -> records.get(call.getArgument(1) + ":" + call.getArgument(2)));
		doAnswer(call -> { records.put(call.getArgument(1) + ":" + call.getArgument(2), call.getArgument(3)); return null; })
			.when(config).setConfiguration(eq(BestGearSetupConfig.GROUP), anyString(), anyString(), anyString());
		doAnswer(call -> { records.remove(call.getArgument(1) + ":" + call.getArgument(2)); return null; })
			.when(config).unsetConfiguration(eq(BestGearSetupConfig.GROUP), anyString(), anyString());
	}

	SearchInputs key(int id)
	{
		// Every call uses new catalog/source objects and independently built unordered collections.
		GameData data = new GameData(Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		MonsterSummary target = new MonsterSummary(); target.setId(id); target.setName("boss" + id);
		return SearchInputs.capture(data, new Object(), target, PlayerLevels.maxed(), false, Set.of("piety"),
			true, false, 99, Map.of(4151, 1L), new LinkedHashSet<>(List.of(4151, 11840)), Collections.emptyMap(),
			new BestGearSetupConfig() { }, new LiveRaid(), Collections.emptyList(), false).with(RaidPlanType.TOB);
	}

	SearchResults result()
	{
		Monster monster = new Monster(); monster.setName("boss"); monster.setHitpoints(100);
		return new SearchResults(monster, 100, Collections.emptyMap(), List.of("saved"),
			Collections.emptyMap(), Collections.emptyMap(), false);
	}

	private SearchResults productionSizedResult()
	{
		byte[] noise = new byte[400_000]; new java.util.Random(1234).nextBytes(noise);
		String note = java.util.Base64.getEncoder().encodeToString(noise);
		return new SearchResults(result().getMonster(), 100, Collections.emptyMap(), List.of(note),
			Collections.emptyMap(), Collections.emptyMap(), false);
	}

	/** A generous byte limit, so these tests exercise record counts; the footprint tests use the shipped bounds. */
	static final int BYTES = 4 * 1024 * 1024;

	ProfileSearchCache<SearchResults> store(int capacity)
	{
		return new ProfileSearchCache<>(config, gson, "encounter", SearchResults.class, capacity, BYTES, Runnable::run);
	}

	private SearchCache<SearchInputs, SearchResults> cache(int capacity)
	{
		SearchCache<SearchInputs, SearchResults> cache = new SearchCache<>(capacity);
		cache.profile(store(capacity));
		return cache;
	}

	@Test public void freshCacheAndFreshInputObjectsRestoreCompletedResults()
	{
		SearchInputs first = key(1);
		SearchResults result = result();
		SearchCache<SearchInputs, SearchResults> before = cache(2);
		before.put(first, result, before.epoch());
		assertTrue("The completed result must be saved in this profile", records.size() > 1);
		SearchCache<SearchInputs, SearchResults> restarted = cache(2);
		SearchInputs again = key(1);
		assertEquals(first, again); assertEquals(first.hashCode(), again.hashCode());
		SearchResults restored = restarted.get(again, restarted.epoch());
		assertNotNull(restored); assertNotSame(result, restored);
		assertEquals(result.getNotes(), restored.getNotes());
		assertEquals(100, restored.getMonster().getHitpoints());
		assertSame(restored, restarted.get(again, restarted.epoch()));
	}

	@Test public void accountsAreIsolatedAndOldJobsCannotPopulateAnotherAccount()
	{
		SearchCache<SearchInputs, SearchResults> first = cache(2);
		long oldEpoch = first.epoch(); first.put(key(1), result(), oldEpoch);
		account.set("two");
		first.profile(store(2));
		assertNull(first.get(key(1), first.epoch()));
		first.put(key(1), result(), oldEpoch);
		assertFalse(records.keySet().stream().anyMatch(name -> name.startsWith("two:")));
		account.set("one");
		assertNotNull(cache(2).get(key(1), cache(2).epoch()));
	}

	@Test public void corruptionAndChangedBuildAreMissesAndCapacitySurvivesRestart()
	{
		SearchCache<SearchInputs, SearchResults> first = cache(2);
		for (int id = 1; id <= 3; id++) { first.put(key(id), result(), first.epoch()); }
		SearchCache<SearchInputs, SearchResults> restarted = cache(2);
		assertNull(restarted.get(key(1), restarted.epoch()));
		assertNotNull(restarted.get(key(2), restarted.epoch()));
		ProfileSearchCache<SearchResults> updated = new ProfileSearchCache<>(config, gson, "encounter", SearchResults.class, 2, BYTES,
			Runnable::run, "different-build");
		assertNull(updated.read(key(2).persistentKey()));
		records.put("one:searchCache.encounter." + key(3).persistentKey(), "damaged");
		assertNull(restarted.get(key(3), restarted.epoch()));
		assertEquals(6, records.size()); // Two manifests, two chunks, the index and the build.
	}

	@Test public void codecPreservesIdentityPotionAssumptionsAndTransientCombatState() throws Exception
	{
		GearItem gear = new GearItem(); gear.setId(4151); gear.setName("whip"); gear.setSlot(Slot.WEAPON);
		Loadout loadout = new Loadout(); loadout.set(Slot.WEAPON, gear); loadout.setStyle(AttackStyle.parse("flick,slash,accurate"));
		DpsResult dpsOne = new DpsResult(1, 1, 1, 1, 1, null);
		DpsResult dpsTwo = new DpsResult(1, 1, 1, 1, 1, null);
		SetupResult one = new SetupResult(CombatClass.MELEE, loadout, dpsOne, 0);
		SetupResult two = new SetupResult(CombatClass.MELEE, loadout, dpsTwo, 0);
		Potion potionOne = new Potion(); potionOne.setName("first");
		Potion potionTwo = new Potion(); potionTwo.setName("second");
		IdentityHashMap<DpsResult, List<Potion>> boosts = new IdentityHashMap<>();
		boosts.put(dpsOne, List.of(potionOne)); boosts.put(dpsTwo, List.of(potionTwo));
		SearchResults result = new SearchResults(result().getMonster(), 100, Map.of(AttackStyle.Type.SLASH, List.of(one, two)),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false, Collections.emptyList(), false,
			false, Collections.emptyList(), null, null, 0, 99, boosts, false);
		SearchResultCodec codec = new SearchResultCodec(gson);
		SearchResults restored = codec.decode(codec.encode(result), SearchResults.class);
		List<SetupResult> setups = restored.getByType().get(AttackStyle.Type.SLASH);
		assertSame(setups.get(0).getLoadout(), setups.get(1).getLoadout());
		assertEquals(Slot.WEAPON, setups.get(0).getLoadout().getWeapon().getSlot());
		assertEquals("first", restored.getSetupPotions().get(setups.get(0).getDps()).get(0).getName());
		assertEquals("second", restored.getSetupPotions().get(setups.get(1).getDps()).get(0).getName());
		assertEquals(2, restored.getSetupPotions().size());
	}

	@Test public void lostIndexIsRebuiltWithoutAccumulatingOrphanedRecords()
	{
		SearchCache<SearchInputs, SearchResults> first = cache(2);
		first.put(key(1), result(), first.epoch());
		records.put("one:searchCache.encounter.index", "damaged");
		first.put(key(2), result(), first.epoch());
		assertEquals(4, records.size());
		SearchCache<SearchInputs, SearchResults> restarted = cache(2);
		assertNull(restarted.get(key(1), restarted.epoch()));
		assertNotNull(restarted.get(key(2), restarted.epoch()));
	}

	@Test public void largeResultsSurviveSyncThatRejectsOversizedIndividualSettings()
	{
		// The live RuneLite sync rejected half-megabyte raid values. Simulate an even smaller per-value ceiling.
		doAnswer(call ->
		{
			String value = call.getArgument(3);
			if (value.length() <= ProfileSearchCache.CHUNK_SIZE)
			{ records.put(call.getArgument(1) + ":" + call.getArgument(2), value); }
			return null;
		}).when(config).setConfiguration(eq(BestGearSetupConfig.GROUP), anyString(), anyString(), anyString());
		SearchResults large = productionSizedResult();
		SearchCache<SearchInputs, SearchResults> before = cache(2);
		before.put(key(1), large, before.epoch());
		assertTrue("A production-sized result must be split into many small profile values", records.size() > 20);
		assertTrue(records.values().stream().allMatch(value -> value.length() <= ProfileSearchCache.CHUNK_SIZE));
		SearchCache<SearchInputs, SearchResults> restarted = cache(2);
		SearchResults restored = restarted.get(key(1), restarted.epoch());
		assertNotNull(restored);
		assertEquals(large.getNotes(), restored.getNotes());
	}

	@Test public void incompleteOrCorruptChunksAreMissesAndEvictionRemovesAllParts()
	{
		SearchCache<SearchInputs, SearchResults> before = cache(1);
		before.put(key(1), result(), before.epoch());
		String chunk = records.keySet().stream().filter(name -> name.contains(".chunk.")).findFirst().get();
		String original = records.remove(chunk);
		SearchCache<SearchInputs, SearchResults> restarted = cache(1);
		assertNull(restarted.get(key(1), restarted.epoch()));
		records.put(chunk, (original.charAt(0) == 'A' ? "B" : "A") + original.substring(1));
		assertNull(restarted.get(key(1), restarted.epoch()));
		records.put(chunk, original);
		assertNotNull(restarted.get(key(1), restarted.epoch()));
		before.put(key(2), result(), before.epoch());
		assertTrue(records.keySet().stream().noneMatch(name -> name.contains(key(1).persistentKey())));
	}

	@Test public void survivingLegacySmallRecordsRemainReadable()
	{
		ProfileSearchCache<SearchResults> store = store(2);
		String record = store.prepare(key(1).persistentKey(), result()).getValue();
		records.put("one:searchCache.encounter." + key(1).persistentKey(), record);
		assertNotNull(store.read(key(1).persistentKey()));
	}

	private SearchResults sizedResult(int noiseBytes, int seed)
	{
		byte[] noise = new byte[noiseBytes]; new java.util.Random(seed).nextBytes(noise);
		return new SearchResults(result().getMonster(), 100, Collections.emptyMap(),
			List.of(java.util.Base64.getEncoder().encodeToString(noise)), Collections.emptyMap(), Collections.emptyMap(), false);
	}

	private long manifests(String kind)
	{
		String prefix = account.get() + ":searchCache." + kind + ".";
		return records.keySet().stream().filter(name -> name.startsWith(prefix)
			&& name.substring(prefix.length()).matches("[0-9a-f]{64}")).count();
	}

	private long chunkCharacters(String kind)
	{
		return records.entrySet().stream().filter(entry -> entry.getKey().startsWith(account.get() + ":searchCache." + kind + ".")
			&& entry.getKey().contains(".chunk.")).mapToLong(entry -> entry.getValue().length()).sum();
	}

	private static void drain(Queue<Runnable> queue)
	{
		while (!queue.isEmpty()) { queue.poll().run(); }
	}

	@Test public void searchWorkersOnlyQueueWritesAndEveryWriteRunsOnTheWriterThread() throws Exception
	{
		// ConfigManager posts ConfigChanged on the calling thread, so a write from any other thread reaches other plugins there.
		AtomicReference<Thread> clientThread = new AtomicReference<>();
		List<String> elsewhere = Collections.synchronizedList(new ArrayList<>());
		doAnswer(call ->
		{
			if (Thread.currentThread() != clientThread.get()) { elsewhere.add("set " + call.getArgument(2)); }
			records.put(call.getArgument(1) + ":" + call.getArgument(2), call.getArgument(3)); return null;
		}).when(config).setConfiguration(eq(BestGearSetupConfig.GROUP), anyString(), anyString(), anyString());
		doAnswer(call ->
		{
			if (Thread.currentThread() != clientThread.get()) { elsewhere.add("unset " + call.getArgument(2)); }
			records.remove(call.getArgument(1) + ":" + call.getArgument(2)); return null;
		}).when(config).unsetConfiguration(eq(BestGearSetupConfig.GROUP), anyString(), anyString());
		String orphan = "one:searchCache.encounter." + "0123456789abcdef".repeat(4);
		records.put(orphan, "saved by an older build");
		Queue<Runnable> queued = new ConcurrentLinkedQueue<>();
		SearchCache<SearchInputs, SearchResults> cache = new SearchCache<>(2);
		cache.profile(new ProfileSearchCache<>(config, gson, "encounter", SearchResults.class, 2, BYTES, queued::add));
		long epoch = cache.epoch();
		Thread worker = new Thread(() -> cache.put(key(1), productionSizedResult(), epoch), "search worker");
		worker.start(); worker.join();
		assertEquals("The worker must not touch the profile", 1, records.size());
		assertEquals("Attach pruning and the save are both queued", 2, queued.size());
		Thread client = new Thread(() -> drain(queued), "client thread");
		clientThread.set(client); client.start(); client.join();
		assertEquals(List.of(), elsewhere);
		assertEquals(1, manifests("encounter"));
		assertTrue(records.size() > 20);
		assertFalse("The older build's record is removed, on the client thread", records.containsKey(orphan));
		clientThread.set(Thread.currentThread());
		SearchCache<SearchInputs, SearchResults> restarted = cache(2);
		assertNotNull(restarted.get(key(1), restarted.epoch()));
	}

	@Test public void queuedWritesFromBeforeAnAccountChangeOrShutdownAreDropped()
	{
		Queue<Runnable> queued = new ArrayDeque<>();
		SearchCache<SearchInputs, SearchResults> cache = new SearchCache<>(2);
		cache.profile(new ProfileSearchCache<>(config, gson, "encounter", SearchResults.class, 2, BYTES, queued::add));
		cache.put(key(1), result(), cache.epoch());
		// Away and back before the client thread runs: the account matches again, but the work is from an old epoch.
		account.set("two");
		cache.profile(new ProfileSearchCache<>(config, gson, "encounter", SearchResults.class, 2, BYTES, queued::add));
		account.set("one");
		cache.profile(new ProfileSearchCache<>(config, gson, "encounter", SearchResults.class, 2, BYTES, queued::add));
		drain(queued);
		assertEquals(0, manifests("encounter"));
		assertFalse(records.keySet().stream().anyMatch(name -> name.startsWith("two:")));
		cache.put(key(2), result(), cache.epoch());
		drain(queued);
		assertEquals(1, manifests("encounter"));
		cache.put(key(3), result(), cache.epoch());
		cache.clear(); // Plugin shutdown.
		drain(queued);
		assertEquals(1, manifests("encounter"));
	}

	@Test public void shippedBoundsCapRecordCountsAndProfileCharacters()
	{
		SearchCache<SearchInputs, SearchResults> encounters = new SearchCache<>(64);
		encounters.profile(ProfileSearchCache.encounters(config, gson, Runnable::run));
		int newest = ProfileSearchCache.ENCOUNTER_RECORDS + 5;
		for (int id = 1; id <= newest; id++) { encounters.put(key(id), result(), encounters.epoch()); }
		assertEquals(ProfileSearchCache.ENCOUNTER_RECORDS, manifests("encounter"));
		SearchCache<SearchInputs, SearchResults> restarted = cache(2);
		assertNotNull(restarted.get(key(newest), restarted.epoch()));
		// Random noise stores at about 1.36 characters a byte. Records well under their share: the count binds first...
		int records = ProfileSearchCache.RAID_RECORDS;
		int small = ProfileSearchCache.RAID_BYTES / (records + 2) * 10 / 14;
		SearchCache<SearchInputs, SearchResults> raids = new SearchCache<>(records + 8);
		raids.profile(new ProfileSearchCache<>(config, gson, "raid", SearchResults.class,
			records, ProfileSearchCache.RAID_BYTES, Runnable::run));
		for (int id = 1; id <= records + 2; id++) { raids.put(key(id), sizedResult(small, id), raids.epoch()); }
		assertEquals(records, manifests("raid"));
		assertTrue(chunkCharacters("raid") <= ProfileSearchCache.RAID_BYTES);
		// ...and ones of about two fifths of the budget are held to the character budget.
		int large = ProfileSearchCache.RAID_BYTES * 2 / 5 * 10 / 14;
		for (int id = records + 3; id <= records + 5; id++) { raids.put(key(id), sizedResult(large, id), raids.epoch()); }
		assertTrue(manifests("raid") < records);
		assertTrue(chunkCharacters("raid") <= ProfileSearchCache.RAID_BYTES);
		assertTrue(this.records.keySet().stream().anyMatch(name -> name.endsWith("." + key(records + 5).persistentKey())));
		// A record bigger than the whole budget is not saved and does not evict what is there.
		long kept = manifests("raid");
		raids.put(key(records + 6), sizedResult(ProfileSearchCache.RAID_BYTES * 10 / 12, records + 6), raids.epoch());
		assertEquals(kept, manifests("raid"));
		assertFalse(this.records.keySet().stream().anyMatch(name -> name.contains(key(records + 6).persistentKey())));
	}

	@Test public void attachingTrimsToCurrentBoundsAndDropsOtherBuilds()
	{
		SearchCache<SearchInputs, SearchResults> earlier = new SearchCache<>(5);
		earlier.profile(new ProfileSearchCache<>(config, gson, "encounter", SearchResults.class, 5, BYTES, Runnable::run, "old"));
		for (int id = 1; id <= 5; id++) { earlier.put(key(id), result(), earlier.epoch()); }
		assertEquals(5, manifests("encounter"));
		SearchCache<SearchInputs, SearchResults> smaller = new SearchCache<>(2);
		smaller.profile(new ProfileSearchCache<>(config, gson, "encounter", SearchResults.class, 2, BYTES, Runnable::run, "old"));
		assertEquals(2, manifests("encounter"));
		assertNotNull(smaller.get(key(5), smaller.epoch()));
		assertNull(smaller.get(key(3), smaller.epoch()));
		cache(2);
		assertEquals("Records from another build can never be read, so none are kept",
			Map.of("one:searchCache.encounter.build", CacheIdentity.buildId()), records);
	}

	@Test public void codecWritesBareNumbersWhereTheDecoderKnowsTheirType() throws Exception
	{
		Set<Integer> ids = new LinkedHashSet<>(List.of(28672, 4097));
		List<Object> graph = Arrays.asList(new DpsResult(11.732854485908735, 52, 0.79, 4, 20.5, null), ids,
			Map.of(5, 7L), Double.NaN, 3L, new int[] {1, 2}, new double[] {0.5, Double.POSITIVE_INFINITY});
		SearchResultCodec codec = new SearchResultCodec(gson);
		JsonObject stored = codec.encode(graph);
		String json = stored.toString();
		// Stored positionally: the DPS node lists its values in its type's field order.
		com.google.gson.JsonArray types = stored.getAsJsonArray("types");
		int type = -1;
		for (int i = 0; i < types.size(); i++)
		{
			if (types.get(i).getAsJsonArray().get(0).getAsString().equals(DpsResult.class.getName())) { type = i; }
		}
		List<String> names = new ArrayList<>();
		types.get(type).getAsJsonArray().get(1).getAsJsonArray().forEach(name -> names.add(name.getAsString()));
		com.google.gson.JsonArray node = null;
		for (com.google.gson.JsonElement element : stored.getAsJsonArray("nodes"))
		{
			com.google.gson.JsonArray candidate = element.getAsJsonArray();
			if (candidate.get(0).getAsJsonPrimitive().isNumber() && candidate.get(0).getAsInt() == type) { node = candidate; }
		}
		assertTrue(json, node.get(names.indexOf("dps") + 1).getAsJsonPrimitive().isNumber()
			&& node.get(names.indexOf("dps") + 1).getAsDouble() == 11.732854485908735
			&& node.get(names.indexOf("maxHit") + 1).getAsJsonPrimitive().isNumber() && node.get(names.indexOf("maxHit") + 1).getAsInt() == 52);
		assertTrue(json, json.contains("[28672,4097]"));
		assertTrue("Untyped Longs and non-finite doubles stay tagged", json.contains("\"number\":\"Long\"") && json.contains("NaN"));
		List<?> restored = codec.decode(new JsonParser().parse(json).getAsJsonObject(), List.class);
		DpsResult dps = (DpsResult) restored.get(0);
		assertEquals(11.732854485908735, dps.getDps(), 0);
		assertEquals(52, dps.getMaxHit());
		assertEquals(ids, restored.get(1));
		assertEquals(Map.of(5, 7L), restored.get(2));
		assertTrue(((Double) restored.get(3)).isNaN());
		assertEquals(3L, restored.get(4));
		assertArrayEquals(new int[] {1, 2}, (int[]) restored.get(5));
		assertArrayEquals(new double[] {0.5, Double.POSITIVE_INFINITY}, (double[]) restored.get(6), 0);
	}

	@Test public void diskRecordsAndHashesSurviveTwoSeparateJavaProcesses() throws Exception
	{
		Path file = temporary.newFolder().toPath().resolve("profile.properties");
		String classpath = System.getProperty("bgs.testClasspath");
		String executable = new File(System.getProperty("java.home"), "bin/java").getAbsolutePath();
		for (String action : List.of("write", "read"))
		{
			Process process = new ProcessBuilder(executable, "-cp", classpath,
				RestartProcess.class.getName(), action, file.toString()).redirectErrorStream(true).start();
			if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AssertionError("Restart probe timed out"); }
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			assertEquals(output, 0, process.exitValue());
		}
	}

	/** A separate JVM recreates its own catalogue, keys, cache and profile store. */
	public static class RestartProcess
	{
		public static void main(String[] args) throws Exception
		{
			ProfileSearchCacheTest fixture = new ProfileSearchCacheTest();
			fixture.initializeCacheIdentity(); fixture.profile();
			Path file = java.nio.file.Paths.get(args[1]);
			Properties saved = new Properties();
			SearchInputs key = fixture.key(1);
			SearchCache<SearchInputs, SearchResults> cache = fixture.cache(2);
			if ("write".equals(args[0]))
			{
				cache.put(key, fixture.productionSizedResult(), cache.epoch());
				assertTrue(fixture.records.size() > 20);
				saved.putAll(fixture.records);
				saved.setProperty("inputKey", key.persistentKey());
				saved.setProperty("inputHash", Integer.toString(key.hashCode()));
				try (java.io.OutputStream output = Files.newOutputStream(file)) { saved.store(output, "test profile"); }
			}
			else
			{
				try (java.io.InputStream input = Files.newInputStream(file)) { saved.load(input); }
				for (String name : saved.stringPropertyNames()) { fixture.records.put(name, saved.getProperty(name)); }
				assertEquals(saved.getProperty("inputKey"), key.persistentKey());
				assertEquals(saved.getProperty("inputHash"), Integer.toString(key.hashCode()));
				SearchResults restored = cache.get(key, cache.epoch());
				assertNotNull("A fresh JVM must load the completed search without recalculating", restored);
				assertEquals(fixture.productionSizedResult().getNotes(), restored.getNotes());
			}
		}
	}
}
