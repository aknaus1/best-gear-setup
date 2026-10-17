package com.bestgearsetup;

import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SpecialPlan;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.calc.WeaponRules;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.AllArgsConstructor;

/** A shared kit and one valid setup for every encounter, worn in for the starting encounter's setup. */
@Value
public class RaidPlan
{
	RaidPlanType type;
	RaidSpellbook spellbook;
	List<RaidEncounter> encounters;
	List<SearchResults> results;
	List<SetupResult> setups;
	List<GearItem> equipment;
	List<InventoryItem> inventory;
	int equipmentLimit;
	long buyCost;
	/** The encounter whose setup the kit is worn in for, one of the raid's first room; null for the first encounter. */
	RaidEncounter start;
	/** Notes on the whole raid's choices (spellbook, path order), apart from any one encounter's. */
	List<String> notes;
	/** Warnings on the whole raid, e.g. display-only encounters the kit can't fight. */
	List<String> warnings;
	/** Players in the raid, each assumed to deal the planned setups' DPS for {@link #getEstimatedSeconds}. */
	int partySize;

	/** A kit worn in for its first encounter's setup. */
	public RaidPlan(RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters, List<SearchResults> results,
		List<SetupResult> setups, List<GearItem> equipment, List<InventoryItem> inventory, int equipmentLimit, long buyCost)
	{
		this(type, spellbook, encounters, results, setups, equipment, inventory, equipmentLimit, buyCost, null);
	}

	/** A kit worn in for the setup of {@code start}, an encounter of the raid's first room. */
	public RaidPlan(RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters, List<SearchResults> results,
		List<SetupResult> setups, List<GearItem> equipment, List<InventoryItem> inventory, int equipmentLimit, long buyCost,
		RaidEncounter start)
	{
		this(type, spellbook, encounters, results, setups, equipment, inventory, equipmentLimit, buyCost, start,
			Collections.emptyList(), Collections.emptyList());
	}

	/** As above, with raid-wide notes and warnings. */
	public RaidPlan(RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters, List<SearchResults> results,
		List<SetupResult> setups, List<GearItem> equipment, List<InventoryItem> inventory, int equipmentLimit, long buyCost,
		RaidEncounter start, List<String> notes, List<String> warnings)
	{
		this(type, spellbook, encounters, results, setups, equipment, inventory, equipmentLimit, buyCost, start, notes, warnings, 1);
	}

	private RaidPlan(RaidPlanType type, RaidSpellbook spellbook, List<RaidEncounter> encounters, List<SearchResults> results,
		List<SetupResult> setups, List<GearItem> equipment, List<InventoryItem> inventory, int equipmentLimit, long buyCost,
		RaidEncounter start, List<String> notes, List<String> warnings, int partySize)
	{
		this.type = type;
		this.spellbook = spellbook;
		this.encounters = encounters;
		this.results = results;
		this.setups = setups;
		this.equipment = equipment;
		this.inventory = inventory;
		this.equipmentLimit = equipmentLimit;
		this.buyCost = buyCost;
		this.start = start;
		this.notes = notes;
		this.warnings = warnings;
		this.partySize = Math.max(1, partySize);
	}

	/** The same kit with these encounters, results and setups, keeping its raid-wide notes and warnings. */
	RaidPlan withEncounters(List<RaidEncounter> encounters, List<SearchResults> results, List<SetupResult> setups)
	{
		return new RaidPlan(type, spellbook, encounters, results, setups, equipment, inventory, equipmentLimit, buyCost, start,
			notes, warnings, partySize);
	}

	/**
	 * The plan as saved to the profile. Once a plan is published, its views, bank layout, boost doses and live
	 * refreshes read only each encounter's chosen setup, so the other candidates the planner compared are dropped.
	 */
	RaidPlan compacted()
	{
		List<SearchResults> kept = new ArrayList<>();
		for (int index = 0; index < results.size(); index++) { kept.add(results.get(index).onlySetup(setups.get(index))); }
		return withEncounters(encounters, Collections.unmodifiableList(kept), setups);
	}

	/** The same kit planned for a party of this size. */
	public RaidPlan withPartySize(int players)
	{
		return new RaidPlan(type, spellbook, encounters, results, setups, equipment, inventory, equipmentLimit, buyCost, start,
			notes, warnings, players);
	}

	/** The same kit with more raid-wide notes and warnings. */
	RaidPlan withRaidNotes(List<String> extraNotes, List<String> extraWarnings)
	{
		List<String> allNotes = new ArrayList<>(notes);
		allNotes.addAll(extraNotes);
		List<String> allWarnings = new ArrayList<>(warnings);
		allWarnings.addAll(extraWarnings);
		return new RaidPlan(type, spellbook, encounters, results, setups, equipment, inventory, equipmentLimit, buyCost, start,
			Collections.unmodifiableList(allNotes), Collections.unmodifiableList(allWarnings), partySize);
	}

	/** Index of the starting encounter's setup. */
	public int getStartIndex()
	{
		int index = start == null ? -1 : encounters.indexOf(start);
		return Math.max(0, index);
	}

	/** The encounter the kit is worn in for. */
	public RaidEncounter getStartingEncounter() { return encounters.get(getStartIndex()); }

	/** The setup the kit is worn in for. */
	public SetupResult getStartingSetup() { return setups.get(getStartIndex()); }

	/**
	 * Estimated combat time for the whole raid: each encounter's share of its party-scaled hitpoints, dealt in whole
	 * attacks ({@link RaidEncounter#estimatedSeconds}) by the party, times its repetitions, assuming every player
	 * deals the planned setup's DPS. Kits are compared by the same objective (the
	 * party size is the same for all of them). Puzzles, walking and downtime between rooms aren't counted.
	 */
	public double getEstimatedSeconds() { return RaidConsolidator.seconds(setups, results, encounters) / partySize; }

	/** One encounter's share of {@link #getEstimatedSeconds}; 0 for display-only encounters. */
	public double estimatedSeconds(int index)
	{
		return encounters.get(index).estimatedSeconds(results.get(index), setups.get(index)) / partySize;
	}

	/** Active searches use the same carried ammunition as the complete kit. */
	SetupResult fillAmmoFor(SetupResult setup, SearchResults result)
	{
		List<SetupResult> choices = new ArrayList<>(setups); choices.add(setup);
		List<SearchResults> context = new ArrayList<>(results); context.add(result);
		return fill(choices, context, new Memo(), getStartIndex()).get(choices.size() - 1);
	}

	/** Whether the carried items include equipment to switch into, rather than only supplies. */
	public static boolean hasEquipmentSwitches(List<InventoryItem> inventory)
	{
		return inventory.stream().anyMatch(InventoryItem::isEquipment);
	}

	/** Supplies and special-attack switches for an individual encounter. */
	public static List<InventoryItem> inventoryForSetup(SetupResult setup, SearchResults results, IntPredicate owns)
	{
		return inventory(Collections.singletonList(setup), Collections.singletonList(results), owns);
	}

	/** The same inventory ordering for the bank's selected encounter and boost choices. */
	static List<InventoryItem> inventoryForSetup(SetupResult setup, List<Potion> potions, IntPredicate owns)
	{
		List<SetupResult> setups = Collections.singletonList(setup);
		Set<Integer> worn = wornIds(setup.getLoadout());
		List<InventoryItem> items = new ArrayList<>();
		for (GearItem item : gear(setup).values())
		{
			if (!worn.contains(item.getId())) { items.add(equipmentItem(item, setups)); }
		}
		Supplies supplies = new Supplies(setup, potions, owns);
		for (InventoryItem spell : supplies.spells)
		{
			if (!worn.contains(spell.getIds().get(0))) { addSupply(items, spell); }
		}
		supplies.boosts.forEach(boost -> addSupply(items, boost));
		packRunes(items, owns);
		weaponStandIn(items, setup, setups);
		sortInventory(items);
		return items;
	}

	/**
	 * The encounter a live target refreshes: the first of that monster in the phase, else its first in any phase; null
	 * when the plan doesn't fight it. Encounters sharing a target and phase (e.g. freezing and helping with Maiden's
	 * nylocas) keep their planned setups.
	 */
	public RaidEncounter encounterFor(String targetName, EncounterPhase phase)
	{
		RaidEncounter any = null;
		for (RaidEncounter encounter : encounters)
		{
			if (!encounter.getTarget().getName().equals(targetName)) { continue; }
			if (phase == null || encounter.getPhase() == phase) { return encounter; }
			if (any == null) { any = encounter; }
		}
		return any;
	}

	/** The same shared kit after changing into an encounter's worn equipment. */
	public List<InventoryItem> inventoryFor(SetupResult setup)
	{
		Set<Integer> worn = wornIds(setup.getLoadout());
		Set<Integer> listed = new java.util.HashSet<>();
		List<InventoryItem> carried = new ArrayList<>();
		for (GearItem item : equipment)
		{
			listed.add(item.getId());
			if (!worn.contains(item.getId())) { carried.add(equipmentItem(item, setups)); }
		}
		for (InventoryItem item : inventory)
		{
			if (!item.isEquipment() && !worn.contains(item.getIds().get(0)) && !listed.contains(item.getIds().get(0)))
			{ carried.add(item); }
		}
		weaponStandIn(carried, setup, setups);
		sortInventory(carried);
		return carried;
	}

	/** Active recalculations may only use supplies already listed in this kit. */
	boolean supportsSupplies(SetupResult setup, SearchResults result, IntPredicate owns)
	{
		RaidEncounter refreshed = encounterFor(result.getMonster().getName(), result.getMonster().getPhase());
		if (refreshed != null)
		{
			List<SetupResult> trial = new ArrayList<>(setups);
			List<SearchResults> context = new ArrayList<>(results);
			int index = encounters.indexOf(refreshed);
			trial.set(index, setup); context.set(index, result);
			Map<Integer, Integer> bottles = new HashMap<>();
			inventory.stream().filter(item -> item.getGroup() == InventoryGroup.BOOSTS)
				.forEach(item -> bottles.merge(item.getIds().get(0), 1, Integer::sum));
			for (InventoryItem required : RaidBoostEstimator.inventory(encounters, context, trial))
			{
				if (bottles.getOrDefault(required.getIds().get(0), 0) < required.getBottles()) { return false; }
			}
		}
		Set<Integer> total = new java.util.HashSet<>();
		equipment.forEach(item -> total.add(item.getId()));
		int supplies = 0;
		for (InventoryItem item : inventory) { if (!item.isEquipment() && !total.contains(item.getIds().get(0))) { supplies++; } }
		for (Loadout loadout : wornStates(setup))
		{
			Set<Integer> carried = new java.util.HashSet<>(total);
			carried.removeAll(wornIds(loadout));
			if (carried.size() + supplies > 28) { return false; }
		}
		Set<Integer> packed = new java.util.HashSet<>();
		inventory.forEach(item ->
		{
			packed.addAll(item.getIds());
			item.getContents().forEach(rune -> packed.addAll(rune.getIds()));
		});
		equipment.forEach(item -> packed.add(item.getId()));
		for (List<Integer> ids : SpellRunes.bankItems(setup, owns))
		{
			if (ids.stream().noneMatch(packed::contains)) { return false; }
		}
		// The chronological estimate already checks dose needs, including later rooms still covered by a prepot.
		if (refreshed != null) { return true; }
		for (Potion potion : result.carriedPotions(setup))
		{
			if (!potion.isRaidSupply() && BoostAccess.itemIds(potion).stream().noneMatch(packed::contains)) { return false; }
		}
		return true;
	}

	/**
	 * How the kit gets a boost one of its setups assumes: drunk before entering for the prepotted first room, carried in
	 * the inventory for a later room, or both; null when the kit neither prepots nor carries it.
	 */
	public PotionUse potionUse(Potion potion)
	{
		if (potion.isRaidSupply()) { return PotionUse.SUPPLIED; }
		boolean prepot = false;
		for (int i = 0; i < encounters.size() && !prepot; i++)
		{
			prepot = results.get(i).isPrepotted()
				&& results.get(i).potionsForSetup(setups.get(i)).stream().anyMatch(p -> p.getId() == potion.getId());
		}
		// A carried bottle may be the divine version, listed under its own id.
		boolean brought = inventory.stream().anyMatch(item -> item.getGroup() == InventoryGroup.BOOSTS
			&& BoostAccess.isBottleOf(potion, item.getIds().get(0)));
		if (prepot && brought) { return PotionUse.BOTH; }
		return prepot ? PotionUse.PREPOT : brought ? PotionUse.BROUGHT : null;
	}

	/** {@link #potionUse} of a carried boost: brought, and also prepot when the first room drinks it too. */
	public PotionUse potionUse(InventoryItem item)
	{
		if (item.getGroup() != InventoryGroup.BOOSTS) { return null; }
		for (int i = 0; i < encounters.size(); i++)
		{
			for (Potion potion : results.get(i).potionsForSetup(setups.get(i)))
			{
				if (BoostAccess.isBottleOf(potion, item.getIds().get(0))) { return potionUse(potion); }
			}
		}
		return null;
	}

	/** When a raid boost is drunk, shown as a tag on its icon and explained in its tooltip. */
	public enum PotionUse
	{
		PREPOT("Pre", "Drink before raid"),
		BROUGHT("Bring", "Carry into raid"),
		BOTH("Both", "Drink before raid; carry in"),
		/** Handed out inside the raid (overloads, smelling salts): no tag, since it is neither withdrawn nor carried in. */
		SUPPLIED(null, "Provided in raid");

		private final String tag;
		private final String description;

		PotionUse(String tag, String description)
		{
			this.tag = tag;
			this.description = description;
		}

		/** A short label for the icon's corner; null for none. */
		public String getTag() { return tag; }

		public String getDescription() { return description; }
	}

	@Value
	@AllArgsConstructor
	public static class InventoryItem
	{
		String name;
		List<Integer> ids;
		boolean equipment;
		/** Rune stacks in a pouch or darts loaded into a blowpipe; no additional inventory slots. */
		List<InventoryItem> contents;
		InventoryGroup group;
		/** Total carried doses and four-dose bottles of this boost across the complete plan (zero for other items). */
		int estimatedDoses;
		int bottles;
		boolean reusable;
		/** Worn slot for ordering switches consistently; null for supplies. */
		Slot slot;
		/** Encounter setups wearing or speccing with this equipment; 0 for supplies. A lone one-use piece sorts low. */
		int uses;
		/**
		 * Bit {@code i} set when encounter setup {@code i} counts toward {@link #uses}; 0 for supplies or when unknown.
		 * Encounter provenance; display priority uses the total usage count.
		 */
		long wornIn;
		/** The combat style special-attack equipment is used with; null for other items. */
		CombatClass specStyle;
		/** Offensive bonuses for this item's combat group, used only to order carried switches. */
		double damageBonus;
		int accuracyBonus;
		/** Display group when this carried weapon stands in for the weapon currently equipped. */
		InventoryGroup weaponStandInFor;

		public InventoryItem(String name, List<Integer> ids, boolean equipment, List<InventoryItem> contents,
			InventoryGroup group, int estimatedDoses, int bottles, boolean reusable, Slot slot, int uses,
			long wornIn, CombatClass specStyle, double damageBonus, int accuracyBonus)
		{
			this(name, ids, equipment, contents, group, estimatedDoses, bottles, reusable, slot, uses, wornIn,
				specStyle, damageBonus, accuracyBonus, null);
		}

		public InventoryItem(String name, List<Integer> ids, boolean equipment, List<InventoryItem> contents,
			InventoryGroup group, int estimatedDoses, int bottles, boolean reusable, Slot slot, int uses,
			long wornIn, CombatClass specStyle)
		{
			this(name, ids, equipment, contents, group, estimatedDoses, bottles, reusable, slot, uses, wornIn, specStyle, 0, 0);
		}

		public InventoryItem(String name, List<Integer> ids, boolean equipment, List<InventoryItem> contents,
			InventoryGroup group, int estimatedDoses, int bottles, boolean reusable, Slot slot, int uses)
		{
			this(name, ids, equipment, contents, group, estimatedDoses, bottles, reusable, slot, uses, 0, null);
		}

		public InventoryItem(String name, List<Integer> ids, boolean equipment, List<InventoryItem> contents,
			InventoryGroup group, int estimatedDoses, int bottles, boolean reusable)
		{
			this(name, ids, equipment, contents, group, estimatedDoses, bottles, reusable, null, 0);
		}

		public InventoryItem(String name, List<Integer> ids, boolean equipment, List<InventoryItem> contents, InventoryGroup group)
		{
			this(name, ids, equipment, contents, group, 0, 0, false);
		}

		/** An item grouped as runes (a rune or rune pouch) or other supplies. */
		public InventoryItem(String name, List<Integer> ids, boolean equipment, List<InventoryItem> contents)
		{
			this(name, ids, equipment, contents, SpellRunes.isRune(ids.get(0)) || SpellRunes.pouchCapacity(ids.get(0)) > 0
				? InventoryGroup.RUNES : InventoryGroup.OTHER);
		}

		public InventoryItem(String name, List<Integer> ids, boolean equipment)
		{
			this(name, ids, equipment, Collections.emptyList());
		}
	}

	/** Combat classification; shared gear follows its most frequent deliberate combat use. */
	public enum InventoryGroup
	{
		MELEE_GEAR, RANGED_GEAR, MAGIC_GEAR, SPECIAL, OTHER, BOOSTS, RUNES
	}

	/** Fixed switch order within each combat group, independent of encounter and candidate traversal. */
	private static final List<Slot> INVENTORY_SLOTS = java.util.Arrays.asList(Slot.WEAPON, Slot.CAPE, Slot.HEAD,
		Slot.BODY, Slot.LEGS, Slot.NECK, Slot.HANDS, Slot.FEET, Slot.RING, Slot.AMMO, Slot.SHIELD);
	/** Weapon, back slot, offensive bonuses (damage then accuracy), and finally the offhand. */
	private static final Comparator<InventoryItem> SWITCH_ORDER = Comparator.comparingInt((InventoryItem item) ->
		item.getSlot() == Slot.WEAPON ? 0 : item.getSlot() == Slot.CAPE ? 1 : item.getSlot() == Slot.SHIELD ? 3 : 2)
		.thenComparing(Comparator.comparingDouble(InventoryItem::getDamageBonus).reversed())
		.thenComparing(Comparator.comparingInt(InventoryItem::getAccuracyBonus).reversed())
		.thenComparingInt(item -> item.getSlot() == null ? INVENTORY_SLOTS.size() : INVENTORY_SLOTS.indexOf(item.getSlot()));

	private static final int INVENTORY_COLUMNS = 4;
	/** Stable tie breakers keep repeated rendering and encounter traversal from rearranging a switch. */
	private static final Comparator<InventoryItem> GEAR_ORDER = SWITCH_ORDER
		.thenComparing(InventoryItem::getName, String.CASE_INSENSITIVE_ORDER)
		.thenComparingInt(item -> item.getIds().get(0));

	/** Sort carried entries by their physical layout; empty display cells never count as carried items. */
	static void sortInventory(List<InventoryItem> items)
	{
		List<InventoryItem> packed = inventoryLayout(items).stream().filter(item -> item != null).collect(Collectors.toList());
		items.clear();
		items.addAll(packed);
	}

	/**
	 * Already-worn items have been removed. Group the remaining multi-piece switches, largest first;
	 * weapon-only swaps and isolated pieces fill spare cells. An attack label never excludes a whole style:
	 * a trident worn with ranged armour still needs its carried magic armour kept together.
	 * Prefer a consistent paired-row layout for the whole inventory;
	 * use paired columns when rows cannot preserve the core switches within the inventory's capacity.
	 * Supplies fill spare cells without moving a combat item off its switch path.
	 */
	public static List<InventoryItem> inventoryLayout(List<InventoryItem> items)
	{
		List<List<InventoryItem>> groups = new ArrayList<>();
		List<List<InventoryItem>> primaryGroups = new ArrayList<>();
		List<InventoryItem> looseGear = new ArrayList<>();
		for (InventoryGroup group : new InventoryGroup[]{InventoryGroup.MELEE_GEAR,
			InventoryGroup.RANGED_GEAR, InventoryGroup.MAGIC_GEAR})
		{
			List<InventoryItem> gear = items.stream().filter(item -> layoutGroup(item) == group)
				.sorted(Comparator.comparingInt(InventoryItem::getUses).reversed().thenComparing(GEAR_ORDER))
				.collect(Collectors.toList());
			Map<Slot, InventoryItem> primary = new LinkedHashMap<>();
			List<InventoryItem> extras = new ArrayList<>();
			int frequentUses = gear.stream().filter(item -> item.getGroup() != InventoryGroup.SPECIAL
				&& item.getWeaponStandInFor() == null)
				.mapToInt(InventoryItem::getUses).max().orElse(0);
			for (InventoryItem item : gear)
			{
				// A unique slot is not automatically core gear. It must be used at least half as often as
				// the style's most-used carried piece. Its main weapon still leads shared accessories.
				boolean mainWeapon = item.getSlot() == Slot.WEAPON && !primary.containsKey(Slot.WEAPON);
				if (item.getGroup() == InventoryGroup.SPECIAL || (!mainWeapon && item.getUses() * 2 < frequentUses)
					|| primary.putIfAbsent(item.getSlot(), item) != null)
				{ extras.add(item); }
			}
			List<InventoryItem> ordered = new ArrayList<>(primary.values());
			ordered.sort(GEAR_ORDER);
			extras.sort(Comparator.comparingInt((InventoryItem item) -> item.getSlot() == Slot.WEAPON ? 0 : 1)
				.thenComparing(Comparator.comparingInt(InventoryItem::getUses).reversed()).thenComparing(GEAR_ORDER));
			ordered.addAll(extras);
			if (!ordered.isEmpty())
			{
				if (primary.size() < 2) { looseGear.addAll(ordered); }
				else
				{
					groups.add(ordered);
					primaryGroups.add(ordered.subList(0, primary.size()));
				}
			}
		}
		List<Integer> order = java.util.stream.IntStream.range(0, groups.size()).boxed()
			.sorted(Comparator.comparingInt((Integer i) -> -groups.get(i).size())
				.thenComparingInt(i -> -primaryGroups.get(i).size()).thenComparingInt(i -> i)).collect(Collectors.toList());
		List<List<InventoryItem>> orderedGroups = order.stream().map(groups::get).collect(Collectors.toList());
		List<List<InventoryItem>> orderedPrimary = order.stream().map(primaryGroups::get).collect(Collectors.toList());
		groups.clear(); groups.addAll(orderedGroups);
		primaryGroups.clear(); primaryGroups.addAll(orderedPrimary);
		looseGear.sort(Comparator.comparingInt((InventoryItem item) -> item.getSlot() == Slot.WEAPON ? 0 : 1)
			.thenComparing(Comparator.comparingInt(InventoryItem::getUses).reversed())
			.thenComparing(RaidPlan::combatGroup).thenComparing(GEAR_ORDER));
		List<InventoryItem> supplies = items.stream().filter(item -> !isCombatGear(item))
			.sorted(Comparator.comparingInt(RaidPlan::supplyOrder)
				.thenComparing(item -> isSpellSupply(item) ? "" : item.getName(), String.CASE_INSENSITIVE_ORDER)
				.thenComparingInt(item -> isSpellSupply(item) ? 0 : item.getIds().get(0)))
			.collect(Collectors.toList());
		if (groups.isEmpty()) { looseGear.addAll(supplies); return looseGear; }

		SwitchLayout best = switchLayout(groups, primaryGroups);
		List<InventoryItem> cells = new ArrayList<>(Collections.nCopies(28, null));
		if (best.paths != null)
		{
			for (int g = 0; g < groups.size(); g++)
			{
				List<InventoryItem> gear = groups.get(g);
				List<Integer> path = best.paths.get(g);
				for (int i = 0; i < Math.min(gear.size(), path.size()); i++) { cells.set(path.get(i), gear.get(i)); }
				if (gear.size() > path.size()) { looseGear.addAll(gear.subList(path.size(), gear.size())); }
			}
		}
		else
		{
			// Some kits cannot have disjoint rectangles (e.g. three nine-piece switches in 28 cells).
			// Preserve every item and its click priority rather than hiding gear outside the inventory.
			List<InventoryItem> gear = groups.stream().flatMap(List::stream).collect(Collectors.toList());
			if (gear.size() > 28) { gear.addAll(looseGear); gear.addAll(supplies); return gear; }
			for (int i = 0; i < gear.size(); i++) { cells.set(rowSwitchCell(i), gear.get(i)); }
		}
		// Passive supplies can occupy an outer edge (the pouch in the reference layout). Spare gear
		// follows the complete switches; potions finish the lowest occupied row rather than filling top holes.
		fillInventorySpace(cells, supplies.stream().filter(item -> item.getGroup() != InventoryGroup.BOOSTS).collect(Collectors.toList()));
		fillInventorySpace(cells, looseGear);
		List<InventoryItem> boosts = supplies.stream().filter(item -> item.getGroup() == InventoryGroup.BOOSTS).collect(Collectors.toList());
		int last = cells.size() - 1;
		while (last >= 0 && cells.get(last) == null) { last--; }
		int rowEnd = (last / 4 + 1) * 4;
		int boostStart = rowEnd - last - 1 >= boosts.size() ? rowEnd - boosts.size() : rowEnd;
		if (boostStart + boosts.size() <= 28)
		{
			for (int i = 0; i < boosts.size(); i++) { cells.set(boostStart + i, boosts.get(i)); }
		}
		else
		{
			// A full inventory may need earlier gaps. Fill the lowest available cells first, preserving bottles.
			List<Integer> free = java.util.stream.IntStream.range(0, cells.size()).filter(i -> cells.get(i) == null)
				.boxed().collect(Collectors.toList());
			int start = Math.max(0, free.size() - boosts.size());
			int used = 0;
			for (int i = start; i < free.size() && used < boosts.size(); i++) { cells.set(free.get(i), boosts.get(used++)); }
			cells.addAll(boosts.subList(used, boosts.size()));
		}
		while (!cells.isEmpty() && cells.get(cells.size() - 1) == null) { cells.remove(cells.size() - 1); }
		return cells;
	}

	private static void fillInventorySpace(List<InventoryItem> cells, List<InventoryItem> items)
	{
		java.util.Iterator<InventoryItem> next = items.iterator();
		for (int cell = 0; cell < cells.size() && next.hasNext(); cell++)
		{ if (cells.get(cell) == null) { cells.set(cell, next.next()); } }
		next.forEachRemaining(cells::add);
	}

	/** Prefer rows for both full groups and core switches before considering a uniform column layout. */
	private static SwitchLayout switchLayout(List<List<InventoryItem>> groups, List<List<InventoryItem>> primaryGroups)
	{
		SwitchLayout best = new SwitchLayout();
		for (boolean columns : new boolean[]{false, true})
		{
			for (List<List<InventoryItem>> switches : java.util.Arrays.asList(groups, primaryGroups))
			{
				findSwitchLayout(switches, groups, 0, 0, -1, columns, new ArrayList<>(), best);
				if (best.paths != null) { return best; }
			}
		}
		return best;
	}

	/** All style blocks in a candidate use one orientation; compactness only breaks ties within it. */
	private static void findSwitchLayout(List<List<InventoryItem>> groups, List<List<InventoryItem>> carried,
		int group, int occupied, int previousStart,
		boolean columns, List<List<Integer>> paths, SwitchLayout best)
	{
		if (group == groups.size())
		{
			int required = 0;
			for (int g = 0; g < groups.size(); g++) { required += Math.max(carried.get(g).size(), paths.get(g).size()); }
			if (required > 28) { return; }
			int rows = (32 - Integer.numberOfLeadingZeros(occupied) + 3) / 4;
			int score = rows * 100 + Integer.bitCount(occupied);
			if (score < best.score)
			{
				best.score = score;
				best.paths = new ArrayList<>(paths);
			}
			return;
		}
		int size = groups.get(group).size();
		if (size > (columns ? 14 : 8)) { return; }
		for (int start = 0; start < 28; start++)
		{
			if (start <= previousStart || (columns ? start % 2 != 0 : start % 8 != 0)) { continue; }
			int capacity = columns ? (size + 1) / 2 * 2 : 8;
			// The seventh row has only four cells; it can still hold a small final group.
			if (!columns && start == 24) { capacity = 4; }
			List<Integer> path = new ArrayList<>();
			int mask = 0;
			for (int i = 0; i < capacity; i++)
			{
				int cell = start + (columns ? columnSwitchCell(i, 0) : rowSwitchCell(i));
				if (cell >= 28) { break; }
				path.add(cell);
				mask |= 1 << cell;
			}
			if (path.size() != capacity || path.size() < size || (mask & occupied) != 0) { continue; }
			paths.add(path);
			findSwitchLayout(groups, carried, group + 1, occupied | mask, start, columns, paths, best);
			paths.remove(paths.size() - 1);
		}
	}

	private static final class SwitchLayout
	{
		private int score = Integer.MAX_VALUE;
		private List<List<Integer>> paths;
	}

	/** Zero-based paired-row click path: 1,5,2,6,3,7,4,8; then 9,13,... */
	static int rowSwitchCell(int index)
	{
		if (index >= 24 && index < 28) { return index; }
		return index / 8 * 8 + index % 2 * INVENTORY_COLUMNS + index % 8 / 2;
	}

	/** Zero-based paired-column click path: 1,2,5,6,... or 3,4,7,8,... */
	static int columnSwitchCell(int index, int side)
	{
		return index / 2 * INVENTORY_COLUMNS + index % 2 + side * 2;
	}

	private static boolean isCombatGear(InventoryItem item)
	{
		return combatGroup(item) != null;
	}

	/** Known special-attack styles join the extra weapons of that combat group. */
	private static InventoryGroup combatGroup(InventoryItem item)
	{
		if (!item.isEquipment()) { return null; }
		if (item.getGroup().compareTo(InventoryGroup.SPECIAL) < 0) { return item.getGroup(); }
		if (item.getGroup() != InventoryGroup.SPECIAL || item.getSpecStyle() == null) { return null; }
		switch (item.getSpecStyle())
		{
			case MELEE: return InventoryGroup.MELEE_GEAR;
			case RANGED: return InventoryGroup.RANGED_GEAR;
			case MAGIC: return InventoryGroup.MAGIC_GEAR;
			default: return null;
		}
	}

	/** Retain specials/tools/boosts/runes order and pouch-first spell packing. */
	private static int supplyOrder(InventoryItem item)
	{
		if (item.getGroup() == InventoryGroup.SPECIAL) { return 0; }
		if (item.getGroup() == InventoryGroup.BOOSTS) { return 2; }
		if (isSpellSupply(item))
		{
			if (SpellRunes.pouchCapacity(item.getIds().get(0)) > 0) { return 3; }
			return item.getGroup() == InventoryGroup.RUNES ? 5 : 4;
		}
		return 1;
	}

	private static boolean isSpellSupply(InventoryItem item)
	{
		int id = item.getIds().get(0);
		return item.getGroup() == InventoryGroup.RUNES || SpellRunes.isSpellSupply(id) || SpellRunes.pouchCapacity(id) > 0;
	}

	/**
	 * Boosts drunk before entering for the prepotted first room. A boost also carried into the raid is listed here and
	 * again in the inventory.
	 */
	public List<InventoryItem> prepotItems()
	{
		Map<Integer, InventoryItem> prepots = new LinkedHashMap<>();
		for (int i = 0; i < encounters.size(); i++)
		{
			if (!results.get(i).isPrepotted()) { continue; }
			for (Potion potion : results.get(i).potionsForSetup(setups.get(i)))
			{
				boolean divine = results.get(i).getBoostTiming().isDivine(potion);
				List<Integer> ids = new ArrayList<>(BoostAccess.prepotItemIds(potion));
				if (ids.isEmpty() || prepots.containsKey(ids.get(0))) { continue; }
				prepots.put(ids.get(0), new InventoryItem((divine ? "divine " : "") + potion.getName(),
					Collections.unmodifiableList(ids), false, Collections.emptyList(), InventoryGroup.BOOSTS));
			}
		}
		List<InventoryItem> ordered = new ArrayList<>(prepots.values());
		ordered.sort(Comparator.comparing((InventoryItem item) -> !item.getName().startsWith("divine "))
			.thenComparing(InventoryItem::getName, String.CASE_INSENSITIVE_ORDER));
		return ordered;
	}

	static Map<Integer, GearItem> gear(SetupResult setup)
	{
		Map<Integer, GearItem> out = new LinkedHashMap<>();
		for (Slot slot : Slot.values()) { put(out, setup.getLoadout().get(slot)); }
		for (SpecialPlan special : setup.getSpecials())
		{
			put(out, special.getWeapon());
			put(out, special.getOffhand());
			put(out, special.getAmmo());
		}
		// Loaded blowpipe darts are supplies, not an inventory equipment switch.
		return out;
	}

	static void put(Map<Integer, GearItem> out, GearItem item)
	{
		if (item != null && !providedInRaid(item)) { out.putIfAbsent(item.getId(), item); }
	}

	/** Picked up inside the raid (the Dawnbringer, passed around at Verzik P1 for its special): never banked or carried in. */
	/** Uses the cached lower-case name: the kit planner asks this for every spec weapon of every kit it grows. */
	public static boolean providedInRaid(GearItem item) { return "dawnbringer".equals(item.getLowerCombatName()); }

	static List<InventoryItem> inventory(List<SetupResult> setups, List<SearchResults> results, IntPredicate owns)
	{
		return inventory(setups, results, owns, 0);
	}

	/** The kit's inventory when worn in for the setup at {@code start}. */
	static List<InventoryItem> inventory(List<SetupResult> setups, List<SearchResults> results, IntPredicate owns, int start)
	{
		Memo memo = new Memo();
		return filledInventory(fill(setups, results, memo, start), results, owns, memo, start);
	}

	/** {@link #inventory} for setups that already went through {@link #fill}. */
	static List<InventoryItem> filledInventory(List<SetupResult> setups, List<SearchResults> results, IntPredicate owns, Memo memo)
	{
		return filledInventory(setups, results, owns, memo, 0);
	}

	static List<InventoryItem> filledInventory(List<SetupResult> setups, List<SearchResults> results, IntPredicate owns, Memo memo,
		int start)
	{
		return filledInventory(setups, results, owns, memo, kitGear(setups, memo), true, start);
	}

	/**
	 * {@link #filledInventory} as the kit search checks it: the same entries with the same first ids, so slot counts
	 * and pouch packing match, but equipment skips its bank ids, loaded darts and display order.
	 */
	static List<InventoryItem> packedInventory(List<SetupResult> setups, List<SearchResults> results, IntPredicate owns, Memo memo)
	{
		return packedInventory(setups, results, owns, memo, kitGear(setups, memo), 0);
	}

	/** As above, worn in for the setup at {@code start}. */
	static List<InventoryItem> packedInventory(List<SetupResult> setups, List<SearchResults> results, IntPredicate owns, Memo memo,
		int start)
	{
		return packedInventory(setups, results, owns, memo, kitGear(setups, memo), start);
	}

	/**
	 * As above, with the kit's gear already collected, worn in for the setup at {@code start}. Filling only wears
	 * ammunition or gear some setup already holds, so the unfilled setups' gear is the same set.
	 */
	static List<InventoryItem> packedInventory(List<SetupResult> setups, List<SearchResults> results, IntPredicate owns, Memo memo,
		Map<Integer, GearItem> gear, int start)
	{
		return filledInventory(setups, results, owns, memo, gear, false, start);
	}

	/** Every setup's {@link Memo#gear}, in setup order. */
	private static Map<Integer, GearItem> kitGear(List<SetupResult> setups, Memo memo)
	{
		Map<Integer, GearItem> gear = new LinkedHashMap<>();
		setups.forEach(s -> gear.putAll(memo.gear(s)));
		return gear;
	}

	/** Equipment switches carried into the raid: the kit's gear less what the (filled) starting setup wears. */
	static int carriedEquipment(Set<Integer> gear, SetupResult start)
	{
		Set<Integer> worn = slotIds(start.getLoadout());
		int carried = 0;
		for (int id : gear) { if (!worn.contains(id)) { carried++; } }
		return carried;
	}

	/**
	 * Inventory slots counted against the raid equipment limit, a maximum: equipment switches, the rune pouch, runes
	 * left outside it and a carried book of the dead. Runes inside the pouch take no slot, and boosts and other
	 * supplies only need to fit in 28 slots. Worn items take no inventory slot, and a book some setup wears is
	 * already an equipment switch.
	 */
	public static int limitedSlots(List<InventoryItem> inventory)
	{
		int slots = 0;
		for (InventoryItem item : inventory) { if (countsTowardEquipmentLimit(item)) { slots++; } }
		return slots;
	}

	/** Whether this inventory entry uses one of the raid equipment limit's slots ({@link #limitedSlots}). */
	static boolean countsTowardEquipmentLimit(InventoryItem item)
	{
		int id = item.getIds().get(0);
		return item.isEquipment() || SpellRunes.isSpellSupply(id) || SpellRunes.pouchCapacity(id) > 0;
	}

	private static List<InventoryItem> filledInventory(List<SetupResult> setups, List<SearchResults> results, IntPredicate owns,
		Memo memo, Map<Integer, GearItem> gear, boolean display, int start)
	{
		Set<Integer> wornIds = slotIds(setups.get(start).getLoadout());
		List<InventoryItem> items = new ArrayList<>();
		// First ids already listed, so each supply is added once.
		Set<Integer> listed = new java.util.HashSet<>();
		for (GearItem item : gear.values())
		{
			if (!wornIds.contains(item.getId()))
			{
				items.add(display ? equipmentItem(item, setups) : memo.packed(item));
				listed.add(item.getId());
			}
		}
		for (int i = 0; i < setups.size(); i++)
		{
			Supplies supplies = memo.supplies(setups.get(i), results.get(i), owns);
			for (InventoryItem spell : supplies.spells)
			{
				if (!wornIds.contains(spell.getIds().get(0)) && listed.add(spell.getIds().get(0))) { items.add(spell); }
			}
			for (InventoryItem boost : supplies.boosts)
			{
				if (listed.add(boost.getIds().get(0))) { items.add(boost); }
			}
		}
		packRunes(items, owns);
		if (display)
		{
			weaponStandIn(items, setups.get(start), setups);
			sortInventory(items);
		}
		return items;
	}

	/** Keep a real carried weapon in the equipped weapon's switch position for hybrid outfits. */
	private static void weaponStandIn(List<InventoryItem> items, SetupResult current, List<SetupResult> setups)
	{
		GearItem weapon = current.getLoadout().getWeapon();
		if (weapon == null) { return; }
		InventoryGroup target = equipmentGroup(weapon, setups);
		if (target.ordinal() > InventoryGroup.MAGIC_GEAR.ordinal()
			|| items.stream().anyMatch(item -> item.getGroup() == target && item.getSlot() == Slot.WEAPON)
			|| items.stream().filter(item -> item.getGroup() == target && item.getSlot() != Slot.WEAPON)
				.map(InventoryItem::getSlot).distinct().count() < 2) { return; }
		int[] armour = new int[3];
		for (Slot slot : new Slot[]{Slot.BODY, Slot.LEGS, Slot.HEAD, Slot.CAPE})
		{
			GearItem worn = current.getLoadout().get(slot);
			if (worn == null) { continue; }
			InventoryGroup style = equipmentGroup(worn, setups);
			if (style.ordinal() < armour.length)
			{ armour[style.ordinal()] += slot == Slot.BODY || slot == Slot.LEGS ? 2 : 1; }
		}
		int best = -1;
		for (int i = 0; i < armour.length; i++)
		{ if (armour[i] > 0 && (best < 0 || armour[i] > armour[best])) { best = i; } }
		if (best < 0 || best == target.ordinal()) { return; }
		for (int i = 0; i < armour.length; i++)
		{ if (i != best && armour[i] == armour[best]) { return; } }
		InventoryGroup source = InventoryGroup.values()[best];
		InventoryItem standIn = items.stream().filter(item -> item.getGroup() == source && item.getSlot() == Slot.WEAPON)
			.sorted(Comparator.comparingInt(InventoryItem::getUses).reversed().thenComparing(GEAR_ORDER)).findFirst().orElse(null);
		if (standIn == null) { return; }
		items.set(items.indexOf(standIn), new InventoryItem(standIn.getName(), standIn.getIds(), standIn.isEquipment(),
			standIn.getContents(), standIn.getGroup(), standIn.getEstimatedDoses(), standIn.getBottles(), standIn.isReusable(),
			standIn.getSlot(), standIn.getUses(), standIn.getWornIn(), standIn.getSpecStyle(), standIn.getDamageBonus(),
			standIn.getAccuracyBonus(), target));
	}

	private static InventoryGroup layoutGroup(InventoryItem item)
	{
		return item.getWeaponStandInFor() == null ? combatGroup(item) : item.getWeaponStandInFor();
	}

	/** Every worn slot's item id, unlike {@link #wornIds} including items provided in the raid. */
	private static Set<Integer> slotIds(Loadout loadout)
	{
		Set<Integer> ids = new java.util.HashSet<>();
		for (Slot slot : Slot.values())
		{
			GearItem item = loadout.get(slot);
			if (item != null) { ids.add(item.getId()); }
		}
		return ids;
	}

	/**
	 * Keep required ammunition worn during fights that leave the slot free (loaded darts remain inside the pipe), and
	 * wear switches the kit carries anyway in the first setup's empty slots.
	 */
	static List<SetupResult> fill(List<SetupResult> setups, List<SearchResults> results)
	{
		return fill(setups, results, new Memo(), 0);
	}

	static List<SetupResult> fill(List<SetupResult> setups, List<SearchResults> results, Memo memo)
	{
		return fill(setups, results, memo, 0);
	}

	/** As above, for a kit worn in for the setup at {@code start}. */
	static List<SetupResult> fill(List<SetupResult> setups, List<SearchResults> results, Memo memo, int start)
	{
		List<SetupResult> filled = fillAmmo(setups, results, memo);
		SetupResult worn = wearCarried(filled, start, results.get(start), memo);
		if (worn == filled.get(start)) { return filled; }
		filled = new ArrayList<>(filled);
		filled.set(start, worn);
		return filled;
	}

	/**
	 * Of the candidate starting setups (indices into {@code setups}; those beyond it are skipped), the one leaving
	 * the fewest equipment switches to carry once filled, the earliest on a tie.
	 */
	static int bestStart(List<SetupResult> setups, List<SearchResults> results, int[] starts, Memo memo)
	{
		Set<Integer> gear = kitGear(setups, memo).keySet();
		int best = 0;
		int fewest = Integer.MAX_VALUE;
		for (int start : starts)
		{
			if (start >= setups.size()) { continue; }
			int carried = carriedEquipment(gear, fill(setups, results, memo, start).get(start));
			if (carried < fewest) { fewest = carried; best = start; }
		}
		return best;
	}

	/**
	 * The starting setup wearing, in each empty slot, an item another setup already carries: it then needs no
	 * inventory slot, e.g. the melee ring worn while maging. Only items with no negative offensive bonus are worn,
	 * so the setup's damage cannot drop; locked slots, and a shield slot a two-handed or special weapon uses, stay empty.
	 */
	private static SetupResult wearCarried(List<SetupResult> setups, int index, SearchResults result, Memo memo)
	{
		SetupResult start = setups.get(index);
		Loadout loadout = start.getLoadout();
		Map<Slot, GearItem> worn = new LinkedHashMap<>();
		for (Slot slot : Slot.values())
		{
			boolean shieldInUse = slot == Slot.SHIELD
				&& (loadout.getWeapon() == null || loadout.getWeapon().isTwoHanded() || start.getSpecial() != null);
			if (slot == Slot.WEAPON || slot == Slot.AMMO || loadout.get(slot) != null || shieldInUse
				|| result.getLocks().stream().anyMatch(lock -> lock.getSlot() == slot))
			{ continue; }
			for (int i = 0; i < setups.size() && !worn.containsKey(slot); i++)
			{
				GearItem item = i == index ? null : setups.get(i).getLoadout().get(slot);
				if (item != null && !providedInRaid(item) && neverLowersDamage(item)) { worn.put(slot, item); }
			}
		}
		return worn.isEmpty() ? start : memo.wearing(start, worn);
	}

	private static boolean neverLowersDamage(GearItem item)
	{
		return item.getStabBonus() >= 0 && item.getSlashBonus() >= 0 && item.getCrushBonus() >= 0 && item.getRangedBonus() >= 0
			&& item.getMagicBonus() >= 0 && item.getMeleeStr() >= 0 && item.getRangedStr() >= 0 && item.getMagicStr() >= 0;
	}

	private static SetupResult withWorn(SetupResult setup, Map<Slot, GearItem> worn)
	{
		Loadout loadout = setup.getLoadout().copy();
		worn.forEach(loadout::set);
		Set<Slot> filledSlots = java.util.EnumSet.noneOf(Slot.class);
		filledSlots.addAll(setup.getFilledSlots());
		filledSlots.addAll(worn.keySet());
		return new SetupResult(setup.getCombatClass(), loadout, setup.getDps(), setup.getBuyCost(), setup.getUnpricedItems(),
			Collections.unmodifiableSet(filledSlots), setup.getSupportSpells(), setup.getSpecial(), setup.getSwitchFrom());
	}

	private static List<SetupResult> fillAmmo(List<SetupResult> setups, List<SearchResults> results, Memo memo)
	{
		Map<Integer, GearItem> ammo = new LinkedHashMap<>();
		Map<Integer, Integer> uses = new LinkedHashMap<>();
		for (SetupResult setup : setups)
		{
			List<GearItem> fired = new ArrayList<>();
			fired.add(setup.getLoadout().get(Slot.AMMO));
			setup.getSpecials().forEach(special -> fired.add(special.getAmmo()));
			for (GearItem item : fired)
			{
				if (WeaponRules.consumedPerAttack(item) && item.getSlot() == Slot.AMMO)
				{ ammo.putIfAbsent(item.getId(), item); uses.merge(item.getId(), 1, Integer::sum); }
			}
		}
		if (ammo.isEmpty()) { return setups; }
		GearItem preferred = ammo.values().stream().max(java.util.Comparator.comparingInt(item -> uses.get(item.getId()))).get();
		List<SetupResult> filled = new ArrayList<>();
		for (int i = 0; i < setups.size(); i++)
		{
			SetupResult setup = setups.get(i);
			boolean locked = results.get(i).getLocks().stream().anyMatch(lock -> lock.getSlot() == Slot.AMMO
				&& (lock.getLock().getKind() != SlotLock.Kind.ITEM || lock.isBlocking() || lock.getLock().getItemId() != preferred.getId()));
			if (setup.getLoadout().get(Slot.AMMO) != null || WeaponRules.firesAmmoSlot(setup.getLoadout().getWeapon()) || locked)
			{ filled.add(setup); continue; }
			filled.add(memo.filled(setup, preferred));
		}
		return filled;
	}

	private static SetupResult withAmmo(SetupResult setup, GearItem ammo)
	{
		Loadout loadout = setup.getLoadout().copy(); loadout.set(Slot.AMMO, ammo);
		return new SetupResult(setup.getCombatClass(), loadout, setup.getDps(), setup.getBuyCost(), setup.getUnpricedItems(),
			setup.getFilledSlots(), setup.getSupportSpells(), setup.getSpecial(), setup.getSwitchFrom());
	}

	private static InventoryItem equipmentItem(GearItem item, List<SetupResult> setups)
	{
		Map<Integer, InventoryItem> contents = new LinkedHashMap<>();
		for (SetupResult setup : setups)
		{
			if (setup.getLoadout().getWeapon() != null && setup.getLoadout().getWeapon().getId() == item.getId())
			{ loaded(contents, setup.getLoadout().getLoadedAmmo()); }
			for (SpecialPlan special : setup.getSpecials())
			{
				if (special.getWeapon().getId() == item.getId()) { loaded(contents, special.getLoadedAmmo()); }
			}
		}
		InventoryGroup group = equipmentGroup(item, setups);
		return new InventoryItem(item.getName(), BestGearSetupBankView.itemIds(item), true,
			Collections.unmodifiableList(new ArrayList<>(contents.values())), group, 0, 0, false, item.getSlot(),
			uses(item, setups, group), wornIn(item, setups, group), specStyle(item, setups), inventoryDamage(item, group, setups),
			inventoryAccuracy(item, group, setups));
	}

	private static double inventoryDamage(GearItem item, InventoryGroup group, List<SetupResult> setups)
	{
		if (group == InventoryGroup.MAGIC_GEAR) { return item.getMagicStr(); }
		if (group == InventoryGroup.MELEE_GEAR) { return item.getMeleeStr(); }
		if (group != InventoryGroup.RANGED_GEAR) { return 0; }
		boolean atlatl = setups.stream().anyMatch(setup -> setup.getCombatClass() == CombatClass.RANGED
			&& use(item, setup) == OWN_USE && WeaponRules.scalesWithStrength(setup.getLoadout().getWeapon()));
		return atlatl ? item.getMeleeStr() : item.getRangedStr();
	}

	private static int inventoryAccuracy(GearItem item, InventoryGroup group, List<SetupResult> setups)
	{
		if (group == InventoryGroup.MAGIC_GEAR) { return item.getMagicBonus(); }
		if (group == InventoryGroup.RANGED_GEAR) { return item.getRangedBonus(); }
		if (group != InventoryGroup.MELEE_GEAR) { return 0; }
		return setups.stream().filter(setup -> setup.getCombatClass() == CombatClass.MELEE && use(item, setup) == OWN_USE)
			.mapToInt(setup ->
			{
				com.bestgearsetup.calc.AttackStyle attack = setup.getLoadout().getStyle();
				if (attack == null) { return Math.max(item.getStabBonus(), Math.max(item.getSlashBonus(), item.getCrushBonus())); }
				switch (attack.getType())
				{
					case STAB: return item.getStabBonus();
					case SLASH: return item.getSlashBonus();
					case CRUSH: return item.getCrushBonus();
					default: return 0;
				}
			}).max().orElse(0);
	}

	/**
	 * Setups that wear this item for its own sake or spec with it; gear only worn to fill an empty slot, or inherited
	 * unchanged by a quick weapon switch, counts only when nothing else uses it.
	 */
	private static int uses(GearItem item, List<SetupResult> setups, InventoryGroup group)
	{
		int own = 0;
		int any = 0;
		for (SetupResult setup : setups)
		{
			int use = inventoryUse(item, setup, group);
			if (use == OWN_USE) { own++; }
			if (use != NO_USE) { any++; }
		}
		return own > 0 ? own : any;
	}

	/** The setups counted by {@link #uses}, as {@link InventoryItem#getWornIn} bits; setups past the 64th aren't recorded. */
	private static long wornIn(GearItem item, List<SetupResult> setups, InventoryGroup group)
	{
		long own = 0;
		long any = 0;
		for (int i = 0; i < Math.min(setups.size(), Long.SIZE); i++)
		{
			int use = inventoryUse(item, setups.get(i), group);
			if (use == OWN_USE) { own |= 1L << i; }
			if (use != NO_USE) { any |= 1L << i; }
		}
		return own != 0 ? own : any;
	}

	/** The first planned special using this weapon, offhand or ammunition determines its style. */
	private static CombatClass specStyle(GearItem item, List<SetupResult> setups)
	{
		for (SetupResult setup : setups)
		{
			for (SpecialPlan plan : setup.getSpecials())
			{
				boolean used = plan.getWeapon().getId() == item.getId()
					|| (plan.getOffhand() != null && plan.getOffhand().getId() == item.getId())
					|| (plan.getAmmo() != null && plan.getAmmo().getId() == item.getId());
				if (!used || plan.getStyle() == null) { continue; }
				switch (plan.getStyle().getType())
				{
					case MAGIC: return CombatClass.MAGIC;
					case RANGED: case ATLATL: return CombatClass.RANGED;
					default: return CombatClass.MELEE;
				}
			}
		}
		return null;
	}

	private static final int NO_USE = 0;
	private static final int ANY_USE = 1;
	private static final int OWN_USE = 2;

	/** A shared piece's display frequency comes from its selected combat style, not unrelated encounters or specs. */
	private static int inventoryUse(GearItem item, SetupResult setup, InventoryGroup group)
	{
		if (group.compareTo(InventoryGroup.SPECIAL) >= 0) { return use(item, setup); }
		return combatGroup(setup.getCombatClass()) == group ? wornUse(item, setup) : NO_USE;
	}

	/** Whether the setup wears this item for its own sake or specs with it, only wears it otherwise, or neither. */
	private static int use(GearItem item, SetupResult setup)
	{
		int use = wornUse(item, setup);
		if (use == OWN_USE) { return use; }
		for (SpecialPlan plan : setup.getSpecials())
		{
			for (GearItem special : new GearItem[]{plan.getWeapon(), plan.getOffhand(), plan.getAmmo()})
			{
				if (special != null && special.getId() == item.getId()) { return OWN_USE; }
			}
		}
		return use;
	}

	/** Filled and unchanged inherited equipment is incidental wear, not a vote for another combat style. */
	private static int wornUse(GearItem item, SetupResult setup)
	{
		for (Slot slot : Slot.values())
		{
			GearItem worn = setup.getLoadout().get(slot);
			if (worn == null || worn.getId() != item.getId()) { continue; }
			GearItem inherited = setup.getSwitchFrom() == null ? null : setup.getSwitchFrom().get(slot);
			if (!setup.getFilledSlots().contains(slot) && (inherited == null || inherited.getId() != item.getId())) { return OWN_USE; }
			return ANY_USE;
		}
		return NO_USE;
	}

	private static InventoryGroup equipmentGroup(GearItem item, List<SetupResult> setups)
	{
		if (item.getSubcategory().equalsIgnoreCase("pickaxe") || item.getLowerCombatName().contains("pickaxe"))
		{ return InventoryGroup.OTHER; }
		InventoryGroup primary = equipmentGroup(item, setups, false);
		return primary == InventoryGroup.OTHER ? equipmentGroup(item, setups, true) : primary;
	}

	/** The style with the most deliberate worn uses owns shared gear; ties use the fixed melee/ranged/magic order. */
	private static InventoryGroup equipmentGroup(GearItem item, List<SetupResult> setups, boolean includeFilled)
	{
		int[] uses = new int[3];
		boolean specialUse = false;
		for (SetupResult setup : setups)
		{
			int worn = wornUse(item, setup);
			if (worn == OWN_USE || (includeFilled && worn == ANY_USE))
			{
				uses[combatGroup(setup.getCombatClass()).ordinal()]++;
			}
			for (SpecialPlan plan : setup.getSpecials())
			{
				for (GearItem special : new GearItem[]{plan.getWeapon(), plan.getOffhand(), plan.getAmmo()})
				{
					if (special != null && special.getId() == item.getId()) { specialUse = true; }
				}
			}
		}
		int most = -1;
		for (int i = 0; i < uses.length; i++)
		{ if (uses[i] > 0 && (most < 0 || uses[i] > uses[most])) { most = i; } }
		return most >= 0 ? InventoryGroup.values()[most] : specialUse ? InventoryGroup.SPECIAL : InventoryGroup.OTHER;
	}

	private static InventoryGroup combatGroup(CombatClass style)
	{
		return style == CombatClass.MELEE ? InventoryGroup.MELEE_GEAR
			: style == CombatClass.RANGED ? InventoryGroup.RANGED_GEAR : InventoryGroup.MAGIC_GEAR;
	}

	private static void loaded(Map<Integer, InventoryItem> contents, GearItem darts)
	{
		if (darts != null) { contents.putIfAbsent(darts.getId(), new InventoryItem(darts.getName(), BestGearSetupBankView.itemIds(darts), false)); }
	}

	private static void packRunes(List<InventoryItem> items, IntPredicate owns)
	{
		List<Integer> pouches = SpellRunes.bestOwnedPouches(owns);
		if (pouches.isEmpty()) { return; }
		List<InventoryItem> runes = new ArrayList<>();
		for (InventoryItem item : items)
		{
			if (SpellRunes.isRune(item.getIds().get(0)) && runes.size() < SpellRunes.pouchCapacity(pouches.get(0)))
			{ runes.add(item); }
		}
		if (runes.isEmpty()) { return; }
		items.removeAll(runes);
		int pouchIndex = -1;
		for (int i = items.size() - 1; i >= 0; i--)
		{
			if (SpellRunes.pouchCapacity(items.get(i).getIds().get(0)) > 0)
			{
				pouchIndex = i;
				items.remove(i);
			}
		}
		InventoryItem pouch = new InventoryItem(SpellRunes.supplyName(pouches.get(0)),
			Collections.unmodifiableList(pouches), false, Collections.unmodifiableList(runes));
		items.add(pouchIndex < 0 ? 0 : pouchIndex, pouch);
	}

	/** Inventory capacity must hold after switches too, including a two-handed weapon freeing the shield slot. */
	static int maximumInventory(List<SetupResult> setups, List<SearchResults> results, IntPredicate owns)
	{
		return maximumInventory(setups, results, owns, 0);
	}

	/** As above, for a kit worn in for the setup at {@code start}. */
	static int maximumInventory(List<SetupResult> setups, List<SearchResults> results, IntPredicate owns, int start)
	{
		Memo memo = new Memo();
		setups = fill(setups, results, memo, start);
		return filledMaximumInventory(setups, packedInventory(setups, results, owns, memo, kitGear(setups, memo), start), memo);
	}

	/** {@link #maximumInventory} for filled setups and their {@link #filledInventory}. */
	static int filledMaximumInventory(List<SetupResult> setups, List<InventoryItem> inventory, Memo memo)
	{
		return filledMaximumInventory(setups, kitGear(setups, memo).keySet(), inventory, memo);
	}

	/** As above, with the kit's gear already collected (see {@link #packedInventory}). */
	static int filledMaximumInventory(List<SetupResult> setups, Set<Integer> gear, List<InventoryItem> inventory, Memo memo)
	{
		Set<Integer> carried = new java.util.HashSet<>(gear);
		int supplies = 0;
		for (InventoryItem item : inventory) { if (!item.isEquipment() && !gear.contains(item.getIds().get(0))) { supplies++; } }
		int maximum = inventory.size();
		for (SetupResult setup : setups)
		{
			for (Set<Integer> worn : memo.worn(setup))
			{
				int stillWorn = 0;
				for (int id : worn) { if (carried.contains(id)) { stillWorn++; } }
				maximum = Math.max(maximum, carried.size() - stillWorn + supplies);
			}
		}
		return maximum;
	}

	private static Set<Integer> wornIds(Loadout loadout)
	{
		Set<Integer> worn = new java.util.HashSet<>();
		for (Slot slot : Slot.values())
		{
			GearItem item = loadout.get(slot);
			if (item != null && !providedInRaid(item)) { worn.add(item.getId()); }
		}
		return worn;
	}

	private static List<Loadout> wornStates(SetupResult setup)
	{
		List<Loadout> states = new ArrayList<>();
		states.add(setup.getLoadout());
		for (SpecialPlan plan : setup.getSpecials())
		{
			Loadout special = setup.getLoadout().copy();
			special.set(Slot.WEAPON, plan.getWeapon());
			if (plan.getWeapon().isTwoHanded()) { special.set(Slot.SHIELD, null); }
			else if (plan.getOffhand() != null) { special.set(Slot.SHIELD, plan.getOffhand()); }
			if (plan.getAmmo() != null) { special.set(Slot.AMMO, plan.getAmmo()); }
			states.add(special);
		}
		return states;
	}

	private static void addSupply(List<InventoryItem> items, InventoryItem supply)
	{
		Integer id = supply.getIds().get(0);
		for (InventoryItem item : items) { if (item.getIds().get(0).equals(id)) { return; } }
		items.add(supply);
	}

	private static InventoryItem supply(String name, List<Integer> ids)
	{
		return new InventoryItem(name, Collections.unmodifiableList(new ArrayList<>(ids)), false);
	}

	/** A setup's spell items (rune pouch, runes, tome) and carried boosts; the ammunition slot does not affect them. */
	private static final class Supplies
	{
		final List<InventoryItem> spells = new ArrayList<>();
		final List<InventoryItem> boosts = new ArrayList<>();

		Supplies(SetupResult setup, SearchResults result, IntPredicate owns)
		{
			this(setup, result.carriedPotions(setup), owns);
		}

		Supplies(SetupResult setup, List<Potion> potions, IntPredicate owns)
		{
			for (List<Integer> ids : SpellRunes.bankItems(setup, owns)) { spells.add(supply(SpellRunes.supplyName(ids.get(0)), ids)); }
			for (Potion potion : potions)
			{
				List<Integer> ids = new ArrayList<>(BoostAccess.inventoryItemIds(potion));
				if (!potion.isRaidSupply() && !ids.isEmpty())
				{
					boosts.add(new InventoryItem(potion.getName(), Collections.unmodifiableList(ids), false,
						Collections.emptyList(), InventoryGroup.BOOSTS));
				}
			}
		}
	}

	/**
	 * Derived items per setup, shared across one kit search. The planner re-checks every partial kit, so the same
	 * setups' gear, supplies and ammunition-filled copies would otherwise be rebuilt many thousands of times.
	 * Keyed by identity: every setup belongs to a single encounter's results, and owned items are fixed per search.
	 */
	static final class Memo
	{
		private final Map<SetupResult, Map<Integer, GearItem>> gear = new IdentityHashMap<>();
		private final Map<SetupResult, Supplies> supplies = new IdentityHashMap<>();
		private final Map<SetupResult, Map<Integer, SetupResult>> filled = new IdentityHashMap<>();
		private final Map<SetupResult, Map<List<Integer>, SetupResult>> wearing = new IdentityHashMap<>();
		private final Map<SetupResult, List<Set<Integer>>> worn = new IdentityHashMap<>();
		private final Map<GearItem, InventoryItem> packed = new IdentityHashMap<>();

		Map<Integer, GearItem> gear(SetupResult setup)
		{
			return gear.computeIfAbsent(setup, s -> Collections.unmodifiableMap(RaidPlan.gear(s)));
		}

		private Supplies supplies(SetupResult setup, SearchResults result, IntPredicate owns)
		{
			return supplies.computeIfAbsent(setup, s -> new Supplies(s, result, owns));
		}

		private SetupResult filled(SetupResult setup, GearItem ammo)
		{
			return filled.computeIfAbsent(setup, s -> new HashMap<>()).computeIfAbsent(ammo.getId(), id -> withAmmo(setup, ammo));
		}

		private SetupResult wearing(SetupResult setup, Map<Slot, GearItem> worn)
		{
			List<Integer> key = worn.values().stream().map(GearItem::getId).collect(Collectors.toList());
			return wearing.computeIfAbsent(setup, s -> new HashMap<>()).computeIfAbsent(key, k -> withWorn(setup, worn));
		}

		private List<Set<Integer>> worn(SetupResult setup)
		{
			return worn.computeIfAbsent(setup, s -> wornStates(s).stream().map(RaidPlan::wornIds).collect(Collectors.toList()));
		}

		private InventoryItem packed(GearItem item)
		{
			return packed.computeIfAbsent(item, i -> new InventoryItem(i.getName(), Collections.singletonList(i.getId()), true,
				Collections.emptyList(), InventoryGroup.OTHER));
		}
	}
}
