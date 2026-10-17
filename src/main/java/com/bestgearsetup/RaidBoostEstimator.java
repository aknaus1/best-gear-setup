package com.bestgearsetup;

import com.bestgearsetup.calc.KillTime;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.Potion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Estimated boost doses on the selected combat timeline, packed as four-dose bottles. Each dose lasts
 * {@link BoostTiming#doseSeconds}: a divine potion's five minutes, or an ordinary boost's drain to the repot threshold.
 */
final class RaidBoostEstimator
{
	static final String ASSUMPTIONS = "Boost quantities cover estimated combat; allow extra for downtime. "
		+ "Boost supplies are excluded from the gear budget.";
	static final String OWNERSHIP_WARNING = "Required boost dose quantities have not been validated against owned stacks. "
		+ "Check the estimated doses and withdraw enough bottles; a partial-dose bottle may not cover its planned four-dose slot.";

	private RaidBoostEstimator() { }

	static List<RaidPlan.InventoryItem> inventory(List<RaidEncounter> encounters, List<SearchResults> results,
		List<SetupResult> setups)
	{
		Map<Integer, Need> needs = new LinkedHashMap<>();
		// Settle every potion's dose duration over all of its skills before counting doses.
		for (int i = 0; i < setups.size(); i++)
		{
			if (encounters.get(i).isDisplayOnly()) { continue; }
			registerAll(needs, results.get(i), results.get(i).potionsForSetup(setups.get(i)));
		}
		// Every first-room prepot is drunk before entry, once per physical potion, even when represented by several skills.
		for (int i = 0; i < setups.size(); i++)
		{
			if (results.get(i).isPrepotted() && !encounters.get(i).isDisplayOnly())
			{
				for (Potion potion : results.get(i).potionsForSetup(setups.get(i)))
				{
					Need need = needs.get(potion.getId());
					if (need != null) { need.expiry = need.seconds; }
				}
			}
		}
		double clock = 0;
		for (int i = 0; i < setups.size(); i++)
		{
			RaidEncounter encounter = encounters.get(i);
			if (encounter.isDisplayOnly()) { continue; }
			clock += Math.max(0, encounter.getRestBefore());
			double seconds = encounter.estimatedSeconds(results.get(i), setups.get(i)) / Math.max(1, encounter.getPartySize());
			if (!Double.isFinite(seconds)) { continue; }
			Set<Integer> used = new HashSet<>();
			for (Potion potion : results.get(i).potionsForSetup(setups.get(i)))
			{
				Need need = needs.get(potion.getId());
				if (need != null && used.add(potion.getId())) { need.drink(clock, seconds); }
			}
			clock += seconds;
		}
		return items(needs);
	}

	/**
	 * Boost bottles for {@code kills} back-to-back kills of one non-raid target, the first dose drunk as the first
	 * kill starts; empty when the setup can't kill it.
	 */
	static List<RaidPlan.InventoryItem> trip(SearchResults result, SetupResult setup, int kills)
	{
		Map<Integer, Need> needs = new LinkedHashMap<>();
		List<Potion> potions = result.carriedPotions(setup);
		registerAll(needs, result, potions);
		double seconds = kills * KillTime.seconds(setup.getDps(), result.getStartingHitpoints());
		if (!Double.isFinite(seconds)) { return Collections.emptyList(); }
		Set<Integer> used = new HashSet<>();
		for (Potion potion : potions)
		{
			Need need = needs.get(potion.getId());
			if (need != null && used.add(potion.getId())) { need.drink(0, seconds); }
		}
		return items(needs);
	}

	/** Every bottle of the needed doses, one entry per bottle. */
	private static List<RaidPlan.InventoryItem> items(Map<Integer, Need> needs)
	{
		List<RaidPlan.InventoryItem> out = new ArrayList<>();
		for (Need need : needs.values())
		{
			if (need.doses == 0) { continue; }
			boolean reusable = need.potion.getName().toLowerCase(java.util.Locale.ROOT).contains("heart");
			int bottles = reusable ? 1 : (need.doses + 3) / 4;
			RaidPlan.InventoryItem item = new RaidPlan.InventoryItem((need.divine ? "divine " : "") + need.potion.getName(),
				Collections.unmodifiableList(new ArrayList<>(BoostAccess.inventoryItemIds(need.potion))), false,
				Collections.emptyList(), RaidPlan.InventoryGroup.BOOSTS, reusable ? 0 : need.doses, bottles, reusable);
			for (int bottle = 0; bottle < bottles; bottle++) { out.add(item); }
		}
		return out;
	}

	static List<RaidPlan.InventoryItem> replace(List<RaidPlan.InventoryItem> inventory, List<RaidEncounter> encounters,
		List<SearchResults> results, List<SetupResult> setups)
	{
		return withBoosts(inventory, inventory(encounters, results, setups));
	}

	/** The inventory with its boosts replaced by {@code boosts}. */
	static List<RaidPlan.InventoryItem> withBoosts(List<RaidPlan.InventoryItem> inventory, List<RaidPlan.InventoryItem> boosts)
	{
		List<RaidPlan.InventoryItem> out = new ArrayList<>();
		inventory.stream().filter(item -> item.getGroup() != RaidPlan.InventoryGroup.BOOSTS).forEach(out::add);
		out.addAll(boosts);
		RaidPlan.sortInventory(out);
		return out;
	}

	/** Registers each carried potion, with the other skills it boosts. */
	private static void registerAll(Map<Integer, Need> needs, SearchResults result, List<Potion> potions)
	{
		for (Potion potion : potions)
		{
			register(needs, potion, result.getBoostTiming());
			// The setup's list keeps one entry per item; the potion table has its other skills.
			result.getPotions().values().forEach(entries -> entries.stream()
				.filter(other -> other.getId() == potion.getId())
				.forEach(other -> register(needs, other, result.getBoostTiming())));
		}
	}

	/**
	 * Records the need for a physical potion carried into the raid. A potion boosting several skills (a super combat)
	 * is drunk again when its first skill falls below the threshold.
	 */
	private static void register(Map<Integer, Need> needs, Potion potion, BoostTiming timing)
	{
		if (potion.isRaidSupply() || BoostAccess.itemIds(potion).isEmpty()) { return; }
		Need need = needs.computeIfAbsent(potion.getId(), id -> new Need(potion, timing.isDivine(potion)));
		need.seconds = Math.min(need.seconds, timing.doseSeconds(potion));
	}

	private static final class Need
	{
		private final Potion potion;
		private final boolean divine;
		private int doses;
		private double expiry;
		private double seconds = Double.POSITIVE_INFINITY;
		private Need(Potion potion, boolean divine) { this.potion = potion; this.divine = divine; }

		/** Keeps the boost up over {@code seconds} of combat from {@code clock}. */
		private void drink(double clock, double seconds)
		{
			if (seconds <= 0) { return; }
			if (expiry <= clock) { doses++; expiry = clock + this.seconds; }
			if (expiry < clock + seconds)
			{
				int refreshes = (int) Math.ceil((clock + seconds - expiry) / this.seconds);
				doses += refreshes;
				expiry += refreshes * this.seconds;
			}
		}
	}
}
