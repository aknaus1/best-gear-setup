package com.bestgearsetup;

import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.Style;
import com.bestgearsetup.data.GameData;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Value key of everything one style tab's optimiser reads, so a repeated search can reuse that tab's setups. Each
 * part is an immutable copy compared by value; the game data is not a part, as the cache is cleared (and its epoch
 * moved on) whenever new data is published.
 *
 * <p>Ownership is narrowed to what the optimiser can ask: {@code Optimizer.isOwned} tests an item's id and its
 * ownership variants, all of which {@link BestGearSetupPlugin#gearIds} holds, and {@code heldQuantity} is read only
 * for ammunition shortfalls, where a stack counts up to the requested ammo quantity (as in
 * {@link BestGearSetupPlugin#supplyDiffers}). Potions owned reach a tab only through the boosted levels, which are
 * a part instead. Food, coins and loot therefore never change the key. Prices are always a part: held-only searches
 * still price a locked item they don't own, and the optimiser compares items' expensive status (a price threshold)
 * outside the Wilderness limit too.
 */
final class SearchInputs
{
	/**
	 * Settings no style tab reads: menu and highlight presentation, trip supplies, the target list's boss filter,
	 * and the style toggles, which only choose which tabs run (each tab searches its own {@link Style.Type}).
	 */
	static final Set<String> UNSEARCHED = new HashSet<>(Arrays.asList("showMenuOption", "highlightBankGear",
		"bankHighlightColor", "highlightInventoryGear", "inventoryHighlightColor", "theme", "customTheme", "fillFood", "killsPerTrip",
		"bossesOnly", "styleStab", "styleSlash", "styleCrush", "styleRanged", "styleMagic", "styleAtlatl"));
	/**
	 * One copy of each large part (prices, owned ids) by content, shared by every key built with it: keys then
	 * compare by reference and cost no more memory than a pointer each. A few recent contents are enough, as prices
	 * change only with Grand Exchange refreshes and owned gear only when equipment is gained or lost.
	 */
	static final SearchCache<Object, Object> SHARED = new SearchCache<>(8);

	private final List<Object> values;
	private final int hash;

	private SearchInputs(Object... values)
	{
		this.values = Arrays.asList(values);
		hash = this.values.hashCode();
	}

	/**
	 * The shared key of one search; {@link #tab} adds the style. {@code boosted} holds the potion-boosted levels
	 * the combat context uses, {@code unlocks} stays null when unknown (the context then assumes every gated
	 * prayer, which an empty set would not).
	 */
	static SearchInputs of(GameData data, String target, PlayerLevels levels, int[] boosted, Set<String> unlocks,
		boolean eliteDiary, boolean karuulm, int mining, int maxCombat, Map<Integer, Long> quotes, Set<Integer> owned,
		Map<Integer, Long> quantities, Options config)
	{
		Set<Integer> gear = BestGearSetupPlugin.gearIds(data);
		Set<Integer> consumables = BestGearSetupPlugin.consumableIds(data);
		int count = config.ammoCount();
		Map<Integer, Long> ammo = new HashMap<>();
		quantities.forEach((id, held) ->
		{
			if (count > 0 && held > 0 && consumables.contains(id))
			{
				ammo.put(id, Math.min(held, count));
			}
		});
		Object[] settings = ConfigSettings.capture(config);
		for (String name : UNSEARCHED)
		{
			settings[ConfigSettings.index(name)] = null;
		}
		return new SearchInputs(target, levels, Arrays.stream(boosted).boxed().collect(Collectors.toList()),
			unlocks == null ? null : new HashSet<>(unlocks), eliteDiary, karuulm, mining, maxCombat,
			shared(Collections.unmodifiableMap(quotes)),
			shared(Collections.unmodifiableSet(owned.stream().filter(gear::contains).collect(Collectors.toSet()))),
			shared(Collections.unmodifiableMap(ammo)), Arrays.asList(settings));
	}

	/** The key of one style tab of this search. */
	SearchInputs tab(Style.Type type)
	{
		return new SearchInputs(this, type);
	}

	@SuppressWarnings("unchecked")
	private static <T> T shared(T value)
	{
		long epoch = SHARED.epoch();
		Object known = SHARED.get(value, epoch);
		if (known != null)
		{
			return (T) known;
		}
		SHARED.put(value, value, epoch);
		return value;
	}

	@Override
	public int hashCode()
	{
		return hash;
	}

	@Override
	public boolean equals(Object other)
	{
		// Different hashes settle most comparisons; shared parts then compare by reference.
		return other instanceof SearchInputs && hash == ((SearchInputs) other).hash
			&& values.equals(((SearchInputs) other).values);
	}
}
