package com.bestgearsetup;

import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/** Value keys of immutable search inputs; container locations and equipped markers aren't calculation inputs. */
final class SearchInputs
{
	/** Settings that only affect presentation or which search is selected. */
	static final Set<String> PRESENTATION = Set.of("activeEncounter", "showMenuOption", "highlightBankGear",
		"bankHighlightColor", "highlightInventoryGear", "inventoryHighlightColor", "fillFood", "bossesOnly", "seenFightOptions");
	/** Kit selection settings: computeSearch receives the resolved styles and spellbook separately, and applies the boost timing to cached and new results alike. */
	static final Set<String> KIT_ONLY = Set.of("raidEquipmentLimit", "raidStyles", "tobRole", "raidSpellbook",
		"divinePotions", "repotBelow", "killsPerTrip");
	private static final Map<GameData, Set<Integer>> RELEVANT = Collections.synchronizedMap(new WeakHashMap<>());

	/** Only the fingerprint is kept, so cached keys don't hold copies of the prices and ownership they cover. */
	private final int hash;
	private final String fingerprint;
	private final GameData catalogue;

	private SearchInputs(List<Object> values, GameData catalogue)
	{
		this.catalogue = catalogue;
		fingerprint = CacheIdentity.digest(CacheIdentity.value(values));
		hash = fingerprint.hashCode();
	}

	String persistentKey() { return fingerprint; }
	GameData catalogue() { return catalogue; }

	/** The fingerprint is taken at once, so the inputs needn't be copied. */
	static SearchInputs capture(GameData data, Object source, MonsterSummary target, PlayerLevels levels,
		boolean remembered, Set<String> prayers, Boolean diary, boolean karuulm, int mining,
		Map<Integer, Long> prices, Set<Integer> owned, Map<Integer, Long> quantities,
		BestGearSetupConfig config, LiveRaid live, List<String> notes, boolean encounter)
	{
		return new SearchInputs(Arrays.asList(CacheIdentity.data(data), source == null ? null : source.getClass().getName(),
			Arrays.asList(target.getId(), target.getName(), target.getCombatLevel(), target.isBoss(),
				target.getAttributes(), target.getIds()),
			levels, remembered, prayers, diary, karuulm, mining, pricesMatter(config) ? prices : null,
			Arrays.asList(relevantOwned(data, owned).stream().filter(id -> !live.adaptive() || !SpellRunes.isCarriedSupply(id))
				.collect(Collectors.toSet()), ammunition(data, quantities, config.ammoCount())), settings(config, encounter),
			live.values, live.pathLevels, live.maxCombat, live.raidDetected, live.combat, live.spellSupplies, notes), data);
	}

	SearchInputs with(Object... extra)
	{
		List<Object> extended = new ArrayList<>();
		extended.add(this);
		extended.addAll(Arrays.asList(extra));
		return new SearchInputs(extended, catalogue);
	}

	/** The shared binding table is checked against every annotated config item by SearchAssumptionsTest. */
	static Map<String, Object> settings(BestGearSetupConfig config, boolean encounter)
	{
		Map<String, Object> values = ConfigSettings.read(config);
		values.keySet().removeIf(name -> PRESENTATION.contains(name) || encounter && KIT_ONLY.contains(name));
		return Collections.unmodifiableMap(values);
	}

	/**
	 * Held-only searches never buy, so owned items cost nothing and prices can't change the result; the Wilderness
	 * expensive-item cap prices owned gear too. Leaving them out lets a held-only search survive GE price refreshes.
	 */
	static boolean pricesMatter(BestGearSetupConfig config)
	{
		return !config.mode().isHeldOnly() || config.wilderness() && config.limitWildernessRisk();
	}

	/** Owned ids a search can query: gear and its variants, boosts and rune pouches. Loot, coins and food don't change a result, so they mustn't change the key. */
	static Set<Integer> relevantOwned(GameData data, Set<Integer> owned)
	{
		Set<Integer> relevant = RELEVANT.computeIfAbsent(data, SearchInputs::ownershipIds);
		return owned.stream().filter(relevant::contains).collect(Collectors.toUnmodifiableSet());
	}

	static Set<Integer> ownershipIds(GameData data)
	{
		Set<Integer> ids = new HashSet<>(BestGearSetupPlugin.gearIds(data));
		ids.addAll(BoostAccess.ownershipIds(data));
		ids.addAll(SpellRunes.RUNE_POUCHES);
		return Collections.unmodifiableSet(ids);
	}

	/** Only ammunition shortfalls affect pricing; food counts and stacks above the requested count don't. */
	private static Map<Integer, Long> ammunition(GameData data, Map<Integer, Long> quantities, int count)
	{
		if (count <= 0) { return Collections.emptyMap(); }
		Map<Integer, Long> capped = new HashMap<>();
		for (int id : BestGearSetupPlugin.consumableIds(data))
		{
			long held = Math.min(count, Math.max(0, quantities.getOrDefault(id, 0L)));
			if (held > 0) { capped.put(id, held); }
		}
		return Collections.unmodifiableMap(capped);
	}

	/** Loadouts are mutable in the optimiser: key the chosen items and casting style by value. */
	static List<Object> loadout(Loadout loadout)
	{
		if (loadout == null) { return Collections.emptyList(); }
		List<Object> items = new ArrayList<>();
		for (Slot slot : Slot.values()) { items.add(loadout.get(slot) == null ? null : loadout.get(slot).getId()); }
		items.add(loadout.getStyle());
		items.add(loadout.getSpell() == null ? null : Arrays.asList(loadout.getSpell().getName(),
			loadout.getSpell().getSpellbook(), loadout.getSpell().getMaxHit(), loadout.getSpell().getLevel(),
			loadout.getSpell().getAttackSpeed()));
		items.add(loadout.getLoadedAmmo() == null ? null : loadout.getLoadedAmmo().getId());
		return Collections.unmodifiableList(items);
	}

	@Override
	public int hashCode()
	{
		return hash;
	}

	@Override
	public boolean equals(Object other)
	{
		return other instanceof SearchInputs && fingerprint.equals(((SearchInputs) other).fingerprint);
	}
}
