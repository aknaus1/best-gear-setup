package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.Setup;
import com.bestgearsetup.calc.Style;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Summary;
import com.bestgearsetup.ui.Sidebar;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/** Search cache keys, and the plugin reusing a style tab only while everything it reads is unchanged. */
public class SearchInputsTest
{
	private static final int SHARK = ItemID.SHARK;
	private static final int WHIP = ItemID.ABYSSAL_WHIP;
	private static final int ARROW = ItemID.DRAGON_ARROW;
	private static GameData data;
	private static Map<Integer, Long> prices;

	@BeforeClass
	public static void load() throws Exception
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
		data = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
		prices = new HashMap<>();
		for (Slot slot : Slot.values())
		{
			for (Gear item : data.getItems(slot))
			{
				prices.put(item.getId(), 100_000L);
			}
		}
		assertTrue(BestGearSetupPlugin.gearIds(data).contains(WHIP));
		assertFalse(BestGearSetupPlugin.gearIds(data).contains(SHARK));
		assertFalse(BestGearSetupPlugin.gearIds(data).contains(ItemID.COINS));
		assertTrue(BestGearSetupPlugin.consumableIds(data).contains(ARROW));
	}

	private static Options config(Map<String, Object> overrides)
	{
		return Assumptions.capture(new Options() { }, overrides);
	}

	private static Options config(String key, Object value)
	{
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("ammoCount", 100);
		overrides.put(key, value);
		return config(overrides);
	}

	private static SearchInputs key(Options config, Set<Integer> owned, Map<Integer, Long> quantities)
	{
		return SearchInputs.of(data, "vorkath", PlayerLevels.maxed(), new int[]{118, 118, 112, 112}, null, false, false,
			99, 0, new HashMap<>(prices), new HashSet<>(owned), new HashMap<>(quantities), config);
	}

	private static SearchInputs key(Options config)
	{
		return key(config, Collections.singleton(WHIP), Collections.emptyMap());
	}

	@Test
	public void sameInputsGiveEqualKeysPerStyle()
	{
		Options a = config("budget", "20m");
		SearchInputs first = key(a);
		SearchInputs again = key(config("budget", "20m"));
		assertEquals(first, again);
		assertEquals(first.hashCode(), again.hashCode());
		assertEquals(first.tab(Style.Type.SLASH), again.tab(Style.Type.SLASH));
		assertNotEquals(first.tab(Style.Type.SLASH), again.tab(Style.Type.CRUSH));
		// A to B and back to A finds A's key again.
		assertNotEquals(first, key(config("budget", "30m")));
		assertEquals(first, key(a));
	}

	@Test
	public void unknownPrayerUnlocksDifferFromNone()
	{
		Options config = config("budget", "10m");
		SearchInputs unknown = SearchInputs.of(data, "vorkath", PlayerLevels.maxed(), new int[4], null, false, false, 99,
			0, prices, Collections.emptySet(), Collections.emptyMap(), config);
		SearchInputs none = SearchInputs.of(data, "vorkath", PlayerLevels.maxed(), new int[4], Collections.emptySet(),
			false, false, 99, 0, prices, Collections.emptySet(), Collections.emptyMap(), config);
		assertNotEquals(unknown, none);
	}

	/** Each input outside the settings changes the key. */
	@Test
	public void everyOtherInputChangesTheKey()
	{
		Options config = config("budget", "10m");
		PlayerLevels maxed = PlayerLevels.maxed();
		PlayerLevels lower = PlayerLevels.builder().attack(98).strength(99).defence(99).ranged(99).magic(99)
			.hitpoints(99).prayer(99).slayer(99).build();
		int[] boosted = {118, 118, 112, 112};
		Set<Integer> owned = Collections.singleton(WHIP);
		SearchInputs base = SearchInputs.of(data, "vorkath", maxed, boosted, null, false, false, 99, 0, prices, owned,
			Collections.emptyMap(), config);
		List<SearchInputs> changed = Arrays.asList(
			SearchInputs.of(data, "zulrah", maxed, boosted, null, false, false, 99, 0, prices, owned, Collections.emptyMap(), config),
			SearchInputs.of(data, "vorkath", lower, boosted, null, false, false, 99, 0, prices, owned, Collections.emptyMap(), config),
			SearchInputs.of(data, "vorkath", maxed, new int[]{118, 118, 112, 113}, null, false, false, 99, 0, prices, owned,
				Collections.emptyMap(), config),
			SearchInputs.of(data, "vorkath", maxed, boosted, Collections.singleton("piety"), false, false, 99, 0, prices,
				owned, Collections.emptyMap(), config),
			SearchInputs.of(data, "vorkath", maxed, boosted, null, true, false, 99, 0, prices, owned, Collections.emptyMap(), config),
			SearchInputs.of(data, "vorkath", maxed, boosted, null, false, true, 99, 0, prices, owned, Collections.emptyMap(), config),
			SearchInputs.of(data, "vorkath", maxed, boosted, null, false, false, 98, 0, prices, owned, Collections.emptyMap(), config),
			SearchInputs.of(data, "vorkath", maxed, boosted, null, false, false, 99, 100, prices, owned, Collections.emptyMap(), config));
		for (int i = 0; i < changed.size(); i++)
		{
			assertNotEquals("input " + i, base, changed.get(i));
		}
	}

	/** Every setting changes the key, except the few no style tab reads. */
	@Test
	public void everySearchedSettingChangesTheKey()
	{
		Options defaults = new Options() { };
		SearchInputs base = key(config(Collections.emptyMap()));
		Set<String> seen = new HashSet<>();
		for (ConfigItemDescriptor item : ConfigItemsForTests.items())
		{
			String name = item.key();
			seen.add(name);
			Object changed = changed(ConfigSettings.getter(name).apply(defaults));
			SearchInputs other = key(config(Collections.singletonMap(name, changed)));
			if (SearchInputs.UNSEARCHED.contains(name))
			{
				assertEquals(name + " is not read by a style tab", base, other);
			}
			else
			{
				assertNotEquals(name + " must change the key", base, other);
			}
		}
		assertTrue("every excluded setting is a config item", seen.containsAll(SearchInputs.UNSEARCHED));
	}

	private static Object changed(Object value)
	{
		if (value instanceof Boolean)
		{
			return !(Boolean) value;
		}
		if (value instanceof Integer)
		{
			return (Integer) value + 1;
		}
		if (value instanceof Double)
		{
			return (Double) value + 1;
		}
		if (value instanceof String)
		{
			return value + "1";
		}
		if (value instanceof Color)
		{
			return new Color(((Color) value).getRGB() ^ 1);
		}
		if (value instanceof Enum)
		{
			Object[] constants = ((Enum<?>) value).getDeclaringClass().getEnumConstants();
			assertTrue(value + " has another choice", constants.length > 1);
			return constants[(((Enum<?>) value).ordinal() + 1) % constants.length];
		}
		throw new AssertionError("No change for a " + value.getClass());
	}

	@Test
	public void onlyOwnershipASearchCanSeeChangesTheKey()
	{
		Options config = config("budget", "10m");
		Map<Integer, Long> stacks = new HashMap<>();
		stacks.put(SHARK, 4L);
		stacks.put(ARROW, 500L);
		SearchInputs base = key(config, new HashSet<>(Arrays.asList(WHIP, SHARK, ARROW)), stacks);
		assertEquals("coins and food don't change a search", base,
			key(config, new HashSet<>(Arrays.asList(WHIP, SHARK, ARROW, ItemID.COINS)), stacks));
		Map<Integer, Long> eaten = new HashMap<>(stacks);
		eaten.put(SHARK, 1L);
		eaten.put(ItemID.COINS, 2_000_000L);
		assertEquals(base, key(config, new HashSet<>(Arrays.asList(WHIP, SHARK, ARROW)), eaten));
		Map<Integer, Long> fired = new HashMap<>(stacks);
		fired.put(ARROW, 400L);
		assertEquals("a stack above the ammo quantity counts as that quantity", base,
			key(config, new HashSet<>(Arrays.asList(WHIP, SHARK, ARROW)), fired));
		fired.put(ARROW, 99L);
		assertNotEquals("below it, the shortfall changes", base,
			key(config, new HashSet<>(Arrays.asList(WHIP, SHARK, ARROW)), fired));
		assertNotEquals("losing gear changes the key", base,
			key(config, new HashSet<>(Arrays.asList(SHARK, ARROW)), stacks));
		Map<Integer, Long> repriced = new HashMap<>(prices);
		repriced.put(WHIP, 1L);
		assertNotEquals(base, SearchInputs.of(data, "vorkath", PlayerLevels.maxed(), new int[]{118, 118, 112, 112}, null,
			false, false, 99, 0, repriced, new HashSet<>(Arrays.asList(WHIP, SHARK, ARROW)), stacks, config));
	}

	@Test
	public void ammoStacksDontMatterWhenTheQuantityIsNotCounted()
	{
		Options uncounted = config("ammoCount", 0);
		assertEquals(key(uncounted, Collections.singleton(ARROW), Collections.singletonMap(ARROW, 5L)),
			key(uncounted, Collections.singleton(ARROW), Collections.singletonMap(ARROW, 5000L)));
	}

	/** The plugin's search: repeated inputs return the same tab lists without searching; changed ones search again. */
	@Test
	public void repeatedSearchReusesTabsAndChangedInputsSearchAgain() throws Exception
	{
		List<List<Setup>> returned = new ArrayList<>();
		int[] searched = {0};
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override
			List<Setup> searchTab(SearchInputs key, long epoch, BooleanSupplier cancelled, DoubleConsumer progress,
				Supplier<List<Setup>> search)
			{
				List<Setup> found = super.searchTab(key, epoch, cancelled, progress, () ->
				{
					searched[0]++;
					return search.get();
				});
				returned.add(found);
				return found;
			}
		};
		plugin.api = new GameDataLoader(RuneLiteAPI.GSON);
		// The notes read the live locks; a finished search publishes its results.
		plugin.config = new Options() { };
		plugin.panel = mock(Sidebar.class);
		Summary target = data.matchNpc(8061, "Vorkath", 732);
		assertTrue(target.getName().startsWith("vorkath"));
		Map<String, Object> settings = new HashMap<>();
		settings.put("styleMagic", false);
		settings.put("styleAtlatl", false);
		search(plugin, target, settings);
		assertEquals("stab, slash, crush and ranged", 4, searched[0]);
		List<List<Setup>> firstTabs = new ArrayList<>(returned);
		assertFalse(firstTabs.get(3).isEmpty());
		returned.clear();
		search(plugin, target, settings);
		assertEquals("nothing searched again", 4, searched[0]);
		for (int i = 0; i < firstTabs.size(); i++)
		{
			assertSame(firstTabs.get(i), returned.get(i));
		}
		// Only ranged now: its tab is reused.
		settings.put("styleStab", false);
		settings.put("styleSlash", false);
		settings.put("styleCrush", false);
		returned.clear();
		search(plugin, target, settings);
		assertEquals(4, searched[0]);
		assertSame(firstTabs.get(3), returned.get(0));
		// A setting the optimiser reads searches again.
		settings.put("currentHitpoints", 50);
		search(plugin, target, settings);
		assertEquals(5, searched[0]);
		// Clearing (a data reload, account change or shutdown) drops every tab.
		plugin.tabCache.clear();
		settings.remove("currentHitpoints");
		search(plugin, target, settings);
		assertEquals(6, searched[0]);
		SwingUtilities.invokeAndWait(() -> { });
		verify(plugin.panel, times(5)).showResults(any());
		verify(plugin.panel, never()).showError(any());
	}

	/** A cancelled optimiser can return part of a list, so its tab is never remembered; a reused tab is done at once. */
	@Test
	public void cancelledTabIsNotRememberedAndReusedTabReportsDone()
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		SearchInputs key = key(config("budget", "10m")).tab(Style.Type.STAB);
		long epoch = plugin.tabCache.epoch();
		List<Setup> partial = new ArrayList<>();
		assertEquals(partial, plugin.searchTab(key, epoch, () -> true, done -> { }, () -> partial));
		double[] progress = {0};
		List<Setup> complete = plugin.searchTab(key, epoch, () -> false, done -> progress[0] = done, ArrayList::new);
		assertEquals("searched, not reused", 0, progress[0], 0);
		assertSame(complete, plugin.searchTab(key, epoch, () -> false, done -> progress[0] = done, () ->
		{
			throw new AssertionError("searched again");
		}));
		assertEquals(1, progress[0], 0);
	}

	private static void search(BestGearSetupPlugin plugin, Summary target, Map<String, Object> settings)
	{
		plugin.runSearch(data, target, null, false, null, null, false, 99, prices, Collections.singleton(WHIP),
			Collections.emptyMap(), plugin.searchGeneration.get(), config(settings), new LiveRaid(), new ArrayList<>(),
			plugin.tabCache.epoch());
	}
}
