package com.bestgearsetup;

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
	static final String ASSUMPTIONS = "Boost doses are estimates over planned combat, including known rest gaps. "
		+ "Divine boosts hold for 5 minutes; ordinary boosts drain a level a minute and are drunk again once below the "
		+ "repot threshold, so peak stats are not maintained between doses. "
		+ "Walking, puzzles and unknown downtime can require extra doses. Hearts are reusable. Boost supply prices are excluded from the gear budget.";
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
			SearchResults result = results.get(i);
			for (Potion potion : result.potionsForSetup(setups.get(i)))
			{
				register(needs, potion, result.getBoostTiming());
				// The setup's list keeps one entry per item; the potion table has its other skills.
				result.getPotions().values().forEach(entries -> entries.stream()
					.filter(other -> other.getId() == potion.getId())
					.forEach(other -> register(needs, other, result.getBoostTiming())));
			}
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
				if (need == null || !used.add(potion.getId()) || seconds <= 0) { continue; }
				if (need.expiry <= clock) { need.doses++; need.expiry = clock + need.seconds; }
				if (need.expiry < clock + seconds)
				{
					int refreshes = (int) Math.ceil((clock + seconds - need.expiry) / need.seconds);
					need.doses += refreshes;
					need.expiry += refreshes * need.seconds;
				}
			}
			clock += seconds;
		}
		List<RaidPlan.InventoryItem> out = new ArrayList<>();
		for (Need need : needs.values())
		{
			if (need.doses == 0) { continue; }
			boolean reusable = need.potion.getName().toLowerCase(java.util.Locale.ROOT).contains("heart");
			int bottles = reusable ? 1 : (need.doses + 3) / 4;
			RaidPlan.InventoryItem item = new RaidPlan.InventoryItem((need.divine ? "divine " : "") + need.potion.getName(),
				Collections.unmodifiableList(new ArrayList<>(BoostAccess.itemIds(need.potion, need.divine))), false,
				Collections.emptyList(), RaidPlan.InventoryGroup.BOOSTS, reusable ? 0 : need.doses, bottles, reusable);
			for (int bottle = 0; bottle < bottles; bottle++) { out.add(item); }
		}
		return out;
	}

	static List<RaidPlan.InventoryItem> replace(List<RaidPlan.InventoryItem> inventory, List<RaidEncounter> encounters,
		List<SearchResults> results, List<SetupResult> setups)
	{
		List<RaidPlan.InventoryItem> out = new ArrayList<>();
		inventory.stream().filter(item -> item.getGroup() != RaidPlan.InventoryGroup.BOOSTS).forEach(out::add);
		out.addAll(inventory(encounters, results, setups));
		RaidPlan.sortInventory(out);
		return out;
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
	}
}
