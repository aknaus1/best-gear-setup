package com.bestgearsetup;

import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.GearItem;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/** Private-storage advice for the fixed CM layout, including the selected room in the remaining fights. */
@Value
public class CoxChestPlan
{
	public static final Color FINISHED_COLOR = new Color(0xFF, 0x50, 0x50);
	public static final Color LATER_COLOR = new Color(0xFF, 0xD2, 0x4A);
	public static final Color NEEDED_COLOR = new Color(0x00, 0xFF, 0x80);
	int floor;
	List<Deposit> deposits;
	int previewFloor;
	List<Deposit> preview;

	/** Classify a member of the shared gear kit, including gear used by later rooms on this floor. */
	public Color colorFor(GearItem item)
	{
		if (floor == 0) { return null; }
		return deposits.stream().filter(deposit -> deposit.getItem().getId() == item.getId())
			.map(Deposit::getColor).findFirst().orElse(NEEDED_COLOR);
	}

	@Value
	public static class Deposit
	{
		GearItem item;
		/** Null means this item has no remaining planned use. */
		RaidEncounter nextUse;
		int nextFloor;

		public Color getColor() { return nextUse == null ? FINISHED_COLOR : LATER_COLOR; }
		public String getReason()
		{
			return nextUse == null ? "Finished for this raid"
				: "Store for this floor; retrieve before " + nextUse.getLabel() + " (floor " + nextFloor + ")";
		}
	}

	/** Floors are keyed by rooms, never by the number of phases or party-scaled Olm hands. */
	public static int floor(RaidEncounter encounter)
	{
		String room = RaidProgress.room(RaidPlanType.COX_CM, encounter.getTarget().getName());
		if (room == null) { return 0; }
		switch (room)
		{
			case "tekton": case "ice demon": case "shamans": return 1;
			case "vanguards": case "vespula": case "tightrope": return 2;
			case "guardians": case "vasa": case "mystics": case "muttadiles": return 3;
			case "olm": return 4;
			default: return 0;
		}
	}

	/** Gear only: consumables, pouch contents and unmodelled prep supplies are never declared spare. */
	public static CoxChestPlan forEncounter(RaidPlan plan, RaidEncounter selected)
	{
		return forEncounter(plan, selected, null);
	}

	/** A live refreshed setup may use another piece of the shared kit; retain that gear as well. */
	public static CoxChestPlan forEncounter(RaidPlan plan, RaidEncounter selected, SetupResult displayed)
	{
		int index = plan.getEncounters().indexOf(selected);
		int currentFloor = selected == null ? 0 : floor(selected);
		if (plan.getType() != RaidPlanType.COX_CM || index < 0 || currentFloor == 0)
		{ return new CoxChestPlan(0, Collections.emptyList(), 0, Collections.emptyList()); }
		// All targets/versions of the selected room remain needed, even when browsing its last phase.
		String room = RaidProgress.room(RaidPlanType.COX_CM, selected.getTarget().getName());
		while (index > 0 && room.equals(RaidProgress.room(RaidPlanType.COX_CM,
			plan.getEncounters().get(index - 1).getTarget().getName()))) { index--; }
		List<Deposit> deposits = deposits(plan, index, currentFloor);
		if (displayed != null)
		{
			List<Deposit> safe = new ArrayList<>(deposits);
			safe.removeIf(deposit -> uses(displayed, deposit.getItem()));
			deposits = Collections.unmodifiableList(safe);
		}
		int next = index;
		while (next < plan.getEncounters().size() && floor(plan.getEncounters().get(next)) <= currentFloor) { next++; }
		boolean preview = deposits.isEmpty() && next < plan.getEncounters().size();
		return new CoxChestPlan(currentFloor, deposits, preview ? floor(plan.getEncounters().get(next)) : 0,
			preview ? deposits(plan, next, floor(plan.getEncounters().get(next))) : Collections.emptyList());
	}

	private static List<Deposit> deposits(RaidPlan plan, int from, int currentFloor)
	{
		List<Deposit> out = new ArrayList<>();
		Map<Integer, GearItem> gear = new LinkedHashMap<>();
		plan.getEquipment().forEach(item -> gear.putIfAbsent(item.getId(), item));
		for (GearItem item : gear.values())
		{
			RaidEncounter next = null;
			boolean keep = false;
			for (int i = from; i < plan.getEncounters().size(); i++)
			{
				RaidEncounter fight = plan.getEncounters().get(i);
				// Scavengers appear on every room floor; their display-only setup is not a full usage history.
				if (fight.isDisplayOnly() && floor(fight) == 0) { continue; }
				int floor = floor(fight);
				if (floor == 0) { keep = true; break; }
				if (uses(plan.getSetups().get(i), item) || toolNeeded(item, fight))
				{
					next = fight;
					keep = floor == currentFloor;
					break;
				}
			}
			if (!keep) { out.add(new Deposit(item, next, next == null ? 0 : floor(next))); }
		}
		return Collections.unmodifiableList(out);
	}

	private static boolean uses(SetupResult setup, GearItem item)
	{
		// Filled/prayer/defence pieces are required too: preserve the published loadouts.
		if (RaidPlan.requiredItems(setup).containsKey(item.getId())) { return true; }
		return SpellRunes.bankItems(setup, id -> false).stream().anyMatch(ids -> ids.contains(item.getId()));
	}

	private static boolean toolNeeded(GearItem item, RaidEncounter fight)
	{
		String name = item.getName().toLowerCase(java.util.Locale.ROOT);
		String room = RaidProgress.room(RaidPlanType.COX_CM, fight.getTarget().getName());
		return (name.endsWith(" axe") || item.getSubcategory().equalsIgnoreCase("axe"))
			&& ("ice demon".equals(room) || "muttadiles".equals(room))
			|| name.contains("pickaxe") && "guardians".equals(room);
	}
}
