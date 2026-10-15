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
		PREPOT("Pre", "Prepot: drink before entering the raid; not carried in"),
		BROUGHT("Bring", "Brought into the raid in the inventory"),
		BOTH("Both", "Prepot before entering, and brought into the raid for later rooms"),
		/** Handed out inside the raid (overloads, smelling salts): no tag, since it is neither withdrawn nor carried in. */
		SUPPLIED(null, "Handed out inside the raid");

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
		 * Pieces worn in the same encounters form a switch.
		 */
		long wornIn;
		/** The combat style a special weapon specs with; null for other items. */
		CombatClass specStyle;

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

	/** Top-to-bottom inventory order; shared gear belongs to the first combat class that uses it. */
	public enum InventoryGroup
	{
		MELEE_GEAR, RANGED_GEAR, MAGIC_GEAR, SPECIAL, OTHER, BOOSTS, RUNES
	}

	/** Fixed switch order within each combat group, independent of encounter and candidate traversal. */
	private static final List<Slot> INVENTORY_SLOTS = java.util.Arrays.asList(Slot.WEAPON, Slot.SHIELD, Slot.HEAD,
		Slot.BODY, Slot.LEGS, Slot.CAPE, Slot.NECK, Slot.HANDS, Slot.FEET, Slot.RING, Slot.AMMO);

	private static final int INVENTORY_COLUMNS = 4;
	/** Switches fill blocks two rows tall, column by column. */
	private static final int SWITCH_BLOCK = 2 * INVENTORY_COLUMNS;
	private static final int AUXILIARY = 1;
	private static final int FILLER = 2;
	private static final int SPECIALS = 3;
	private static final int TOOLS = 4;
	private static final int ONE_OFF = 5;
	private static final int BOOSTS = 6;

	/**
	 * Display order, packed with no gaps, the way players lay out a raid inventory: the main switch (the combat
	 * style with the most carried gear) on top, then the other styles' switches in prayer-book order (magic, ranged,
	 * melee), in two-row blocks (see {@link #switchBlocks}). Spell supplies fill those blocks' free cells; special
	 * weapons, in the same style order, start a new row below, and spell supplies the blocks can't hold complete
	 * it, the rune pouch at the row's end. Then tools, then one-off gear, then boosts. One-off gear is a situational
	 * piece never worn alongside its style's switch (a salve amulet worn only at an undead boss), or a style's only
	 * carried piece other than a weapon it switches to repeatedly.
	 */
	static void sortInventory(List<InventoryItem> items)
	{
		Map<InventoryGroup, Long> anchors = switchEncounters(items);
		Map<InventoryGroup, Integer> sizes = new java.util.EnumMap<>(InventoryGroup.class);
		for (InventoryItem item : items) { if (isCoreGear(item, anchors)) { sizes.merge(item.getGroup(), 1, Integer::sum); } }
		// The largest style's gear; on a tie, the first style.
		InventoryGroup main = sizes.entrySet().stream()
			.max(Map.Entry.<InventoryGroup, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
			.map(Map.Entry::getKey).orElse(null);
		Map<InventoryItem, Integer> sections = new IdentityHashMap<>();
		for (InventoryItem item : items) { sections.put(item, section(item, main, sizes, anchors)); }
		// Within a style, pieces worn in exactly the same encounters are one set: the largest set first.
		Map<InventoryGroup, Map<Long, Integer>> sets = new java.util.EnumMap<>(InventoryGroup.class);
		for (InventoryItem item : items)
		{
			if (sections.get(item) <= AUXILIARY)
			{ sets.computeIfAbsent(item.getGroup(), group -> new HashMap<>()).merge(item.getWornIn(), 1, Integer::sum); }
		}
		Map<InventoryItem, Integer> setSizes = new IdentityHashMap<>();
		for (InventoryItem item : items)
		{ setSizes.put(item, sections.get(item) <= AUXILIARY ? sets.get(item.getGroup()).get(item.getWornIn()) : 0); }
		items.sort(Comparator.comparingInt((InventoryItem item) -> sections.get(item))
			.thenComparingInt(item -> prayerOrder(sections.get(item) == SPECIALS ? item.getSpecStyle() : style(item.getGroup())))
			.thenComparingInt(item -> -setSizes.get(item))
			.thenComparingLong(item -> sections.get(item) <= AUXILIARY ? item.getWornIn() : 0)
			.thenComparingInt(item -> SpellRunes.pouchCapacity(item.getIds().get(0)) > 0 ? 0 : 1)
			.thenComparing(InventoryItem::getGroup)
			.thenComparingInt(item -> item.getSlot() == null ? INVENTORY_SLOTS.size() : INVENTORY_SLOTS.indexOf(item.getSlot()))
			// Rune packing puts the pouch first and keeps loose stacks in spell order.
			.thenComparing(item -> item.getGroup() == InventoryGroup.RUNES ? "" : item.getName(), String.CASE_INSENSITIVE_ORDER)
			.thenComparingInt(item -> item.getGroup() == InventoryGroup.RUNES ? 0 : item.getIds().get(0)));
		List<InventoryItem> blocks = switchBlocks(items, sections, setSizes, false);
		if (blocks == null) { blocks = switchBlocks(items, sections, setSizes, true); }
		if (blocks != null)
		{
			items.clear();
			items.addAll(blocks);
			return;
		}
		int switches = (int) items.stream().filter(item -> sections.get(item) <= AUXILIARY).count();
		int gap = (INVENTORY_COLUMNS - switches % INVENTORY_COLUMNS) % INVENTORY_COLUMNS;
		List<InventoryItem> spare = new ArrayList<>();
		for (int i = switches + gap; i < items.size(); i++)
		{
			if (sections.get(items.get(i)) == FILLER) { spare.add(items.get(i)); }
		}
		items.removeAll(spare);
		items.addAll(spare);
	}

	/**
	 * Switches in blocks two rows tall, filled column by column, each style and each set of two or more pieces
	 * starting a new column: a four-piece set forms a square and a two-piece switch a vertical pair, quicker to click
	 * through than a row (1, 5, 2, 6 rather than 1, 2, 3, 4). Spell supplies take the blocks' free cells, and any
	 * left over follow the specials; then the rest fill any cells still free and continue row by row. With
	 * {@code lastInRows}, the last block's switches run along its rows instead, for a kit with too little else to
	 * fill that block's columns. Null without switches, or when nothing is left to fill a free cell between items,
	 * which would shift every later item and break the columns.
	 */
	private static List<InventoryItem> switchBlocks(List<InventoryItem> sorted, Map<InventoryItem, Integer> sections,
		Map<InventoryItem, Integer> setSizes, boolean lastInRows)
	{
		List<InventoryItem> switches = new ArrayList<>();
		List<InventoryItem> fillers = new ArrayList<>();
		List<InventoryItem> specials = new ArrayList<>();
		List<InventoryItem> later = new ArrayList<>();
		for (InventoryItem item : sorted)
		{
			int section = sections.get(item);
			(section <= AUXILIARY ? switches : section == FILLER ? fillers : section == SPECIALS ? specials : later).add(item);
		}
		if (switches.isEmpty()) { return null; }
		java.util.TreeMap<Integer, InventoryItem> cells = new java.util.TreeMap<>();
		int next = 0;
		InventoryItem previous = null;
		List<Integer> places = new ArrayList<>();
		for (InventoryItem item : switches)
		{
			boolean newStyle = previous != null && item.getGroup() != previous.getGroup();
			boolean newSet = previous != null && item.getWornIn() != previous.getWornIn() && setSizes.get(item) > 1;
			if ((newStyle || newSet) && next % 2 == 1) { next++; }
			previous = item;
			places.add(next++);
		}
		int last = (next - 1) / SWITCH_BLOCK * SWITCH_BLOCK;
		int end = last + SWITCH_BLOCK;
		java.util.function.IntUnaryOperator cellAt = index -> lastInRows && index >= last ? index : blockCell(index);
		int row = last;
		for (int i = 0; i < switches.size(); i++)
		{
			cells.put(lastInRows && places.get(i) >= last ? row++ : blockCell(places.get(i)), switches.get(i));
		}
		java.util.Iterator<InventoryItem> filler = fillers.iterator();
		for (int i = 0; i < end && filler.hasNext(); i++)
		{
			if (!cells.containsKey(cellAt.applyAsInt(i))) { cells.put(cellAt.applyAsInt(i), filler.next()); }
		}
		// Spell supplies the blocks can't hold complete the specials' row rather than pushing them off it.
		List<InventoryItem> rest = new ArrayList<>(specials);
		filler.forEachRemaining(rest::add);
		rest.addAll(later);
		int cell = 0;
		for (InventoryItem item : rest)
		{
			while (cells.containsKey(cell)) { cell++; }
			cells.put(cell, item);
		}
		if (cells.lastKey() != cells.size() - 1) { return null; }
		// The rune pouch, rarely touched in the raid, takes the outermost spell supply cell: the rightmost, then the top.
		List<Integer> supplies = new ArrayList<>();
		cells.forEach((at, item) -> { if (sections.get(item) == FILLER) { supplies.add(at); } });
		Integer pouch = supplies.stream().filter(at -> SpellRunes.pouchCapacity(cells.get(at).getIds().get(0)) > 0).findFirst().orElse(null);
		if (pouch != null)
		{
			int corner = supplies.stream().min(Comparator.comparingInt((Integer at) -> -(at % INVENTORY_COLUMNS))
				.thenComparingInt(at -> at)).get();
			cells.put(pouch, cells.put(corner, cells.get(pouch)));
		}
		return new ArrayList<>(cells.values());
	}

	/** The inventory slot of a switch block's {@code index}th cell, counted down each column. */
	private static int blockCell(int index)
	{
		int column = index % SWITCH_BLOCK / 2;
		return index / SWITCH_BLOCK * SWITCH_BLOCK + index % 2 * INVENTORY_COLUMNS + column;
	}

	/** {@link #sortInventory} order: main switch, other switches, fillers, specials, tools, one-off gear, boosts. */
	private static int section(InventoryItem item, InventoryGroup main, Map<InventoryGroup, Integer> sizes,
		Map<InventoryGroup, Long> anchors)
	{
		if (isCombatGear(item))
		{
			if (!isCoreGear(item, anchors)) { return ONE_OFF; }
			if (item.getGroup() == main) { return 0; }
			// A style's lone piece is an occasional swap, unless it's a weapon several encounters switch to.
			boolean lone = sizes.get(item.getGroup()) == 1 && (item.getUses() == 1 || item.getSlot() != Slot.WEAPON);
			return lone ? ONE_OFF : AUXILIARY;
		}
		int id = item.getIds().get(0);
		if (item.getGroup() == InventoryGroup.RUNES || SpellRunes.isSpellSupply(id) || SpellRunes.pouchCapacity(id) > 0)
		{ return FILLER; }
		switch (item.getGroup())
		{
			case SPECIAL: return SPECIALS;
			case BOOSTS: return BOOSTS;
			default: return TOOLS;
		}
	}

	private static boolean isCombatGear(InventoryItem item)
	{
		return item.isEquipment() && item.getGroup().compareTo(InventoryGroup.SPECIAL) < 0;
	}

	/**
	 * Each style's switches, as the encounters their pieces are worn in: pieces worn in a shared encounter belong
	 * together. Every set of two or more pieces counts, and so does the largest set (most pieces, then most uses,
	 * then the earliest encounters) when every piece is worn apart; a lone piece worn apart from the rest doesn't.
	 */
	private static Map<InventoryGroup, Long> switchEncounters(List<InventoryItem> items)
	{
		// Per style, sets of pieces worn together: {encounters, pieces, uses}.
		Map<InventoryGroup, List<long[]>> together = new java.util.EnumMap<>(InventoryGroup.class);
		for (InventoryItem item : items)
		{
			if (!isCombatGear(item) || item.getWornIn() == 0) { continue; }
			List<long[]> sets = together.computeIfAbsent(item.getGroup(), group -> new ArrayList<>());
			long[] merged = {item.getWornIn(), 1, item.getUses()};
			for (boolean joined = true; joined; )
			{
				joined = false;
				for (java.util.Iterator<long[]> it = sets.iterator(); it.hasNext(); )
				{
					long[] set = it.next();
					if ((set[0] & merged[0]) == 0) { continue; }
					merged[0] |= set[0]; merged[1] += set[1]; merged[2] += set[2];
					it.remove();
					joined = true;
				}
			}
			sets.add(merged);
		}
		Map<InventoryGroup, Long> switches = new java.util.EnumMap<>(InventoryGroup.class);
		together.forEach((group, sets) ->
		{
			long worn = sets.stream().max(Comparator.comparingLong((long[] set) -> set[1])
				.thenComparingLong(set -> set[2]).thenComparingLong(set -> -Long.lowestOneBit(set[0]))).get()[0];
			for (long[] set : sets) { if (set[1] > 1) { worn |= set[0]; } }
			switches.put(group, worn);
		});
		return switches;
	}

	/**
	 * Combat gear that belongs in its style's switch ({@link #switchEncounters}). A salve amulet swapped into the worn
	 * gear only at an undead boss, where the rest of its style's switch isn't worn, is a situational piece instead.
	 * Weapons and gear without recorded encounters always belong.
	 */
	private static boolean isCoreGear(InventoryItem item, Map<InventoryGroup, Long> switches)
	{
		if (!isCombatGear(item)) { return false; }
		Long worn = switches.get(item.getGroup());
		return worn == null || item.getWornIn() == 0 || item.getSlot() == Slot.WEAPON || (item.getWornIn() & worn) != 0;
	}

	/** Prayer-book order: magic, ranged, melee, then anything without a style. */
	private static int prayerOrder(CombatClass style)
	{
		return style == CombatClass.MAGIC ? 0 : style == CombatClass.RANGED ? 1 : style == CombatClass.MELEE ? 2 : 3;
	}

	private static CombatClass style(InventoryGroup group)
	{
		switch (group)
		{
			case MELEE_GEAR: return CombatClass.MELEE;
			case RANGED_GEAR: return CombatClass.RANGED;
			case MAGIC_GEAR: return CombatClass.MAGIC;
			default: return null;
		}
	}

	/**
	 * Boosts drunk before entering for the prepotted first room and not carried in ({@link PotionUse#PREPOT}): withdrawn,
	 * drunk and banked again. A boost also carried in ({@link PotionUse#BOTH}) is already an inventory entry.
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
				List<Integer> ids = new ArrayList<>(BoostAccess.itemIds(potion, divine));
				if (ids.isEmpty() || prepots.containsKey(ids.get(0)) || potionUse(potion) != PotionUse.PREPOT) { continue; }
				prepots.put(ids.get(0), new InventoryItem((divine ? "divine " : "") + potion.getName(),
					Collections.unmodifiableList(ids), false, Collections.emptyList(), InventoryGroup.BOOSTS));
			}
		}
		return new ArrayList<>(prepots.values());
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
		if (display) { sortInventory(items); }
		return items;
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
		return new InventoryItem(item.getName(), BestGearSetupBankView.itemIds(item), true,
			Collections.unmodifiableList(new ArrayList<>(contents.values())), equipmentGroup(item, setups), 0, 0, false, item.getSlot(),
			uses(item, setups), wornIn(item, setups), specStyle(item, setups));
	}

	/**
	 * Setups that wear this item for its own sake or spec with it; gear only worn to fill an empty slot, or inherited
	 * unchanged by a quick weapon switch, counts only when nothing else uses it.
	 */
	private static int uses(GearItem item, List<SetupResult> setups)
	{
		int own = 0;
		int any = 0;
		for (SetupResult setup : setups)
		{
			int use = use(item, setup);
			if (use == OWN_USE) { own++; }
			if (use != NO_USE) { any++; }
		}
		return own > 0 ? own : any;
	}

	/** The setups counted by {@link #uses}, as {@link InventoryItem#getWornIn} bits; setups past the 64th aren't recorded. */
	private static long wornIn(GearItem item, List<SetupResult> setups)
	{
		long own = 0;
		long any = 0;
		for (int i = 0; i < Math.min(setups.size(), Long.SIZE); i++)
		{
			int use = use(item, setups.get(i));
			if (use == OWN_USE) { own |= 1L << i; }
			if (use != NO_USE) { any |= 1L << i; }
		}
		return own != 0 ? own : any;
	}

	/** The style a special weapon specs with, from its first planned special; null when no setup specs with it. */
	private static CombatClass specStyle(GearItem item, List<SetupResult> setups)
	{
		for (SetupResult setup : setups)
		{
			for (SpecialPlan plan : setup.getSpecials())
			{
				if (plan.getWeapon().getId() != item.getId() || plan.getStyle() == null) { continue; }
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

	/** Whether the setup wears this item for its own sake or specs with it, only wears it otherwise, or neither. */
	private static int use(GearItem item, SetupResult setup)
	{
		int use = NO_USE;
		for (Slot slot : Slot.values())
		{
			GearItem worn = setup.getLoadout().get(slot);
			if (worn == null || worn.getId() != item.getId()) { continue; }
			GearItem inherited = setup.getSwitchFrom() == null ? null : setup.getSwitchFrom().get(slot);
			if (!setup.getFilledSlots().contains(slot) && (inherited == null || inherited.getId() != item.getId())) { return OWN_USE; }
			use = ANY_USE;
		}
		for (SpecialPlan plan : setup.getSpecials())
		{
			for (GearItem special : new GearItem[]{plan.getWeapon(), plan.getOffhand(), plan.getAmmo()})
			{
				if (special != null && special.getId() == item.getId()) { return OWN_USE; }
			}
		}
		return use;
	}

	private static InventoryGroup equipmentGroup(GearItem item, List<SetupResult> setups)
	{
		if (item.getSubcategory().equalsIgnoreCase("pickaxe") || item.getLowerCombatName().contains("pickaxe"))
		{ return InventoryGroup.OTHER; }
		InventoryGroup primary = equipmentGroup(item, setups, false);
		return primary == InventoryGroup.OTHER ? equipmentGroup(item, setups, true) : primary;
	}

	/** Prefer combat use over filled slots and unchanged gear inherited by a quick weapon switch. */
	private static InventoryGroup equipmentGroup(GearItem item, List<SetupResult> setups, boolean includeFilled)
	{
		InventoryGroup group = InventoryGroup.OTHER;
		for (SetupResult setup : setups)
		{
			for (Slot slot : Slot.values())
			{
				GearItem worn = setup.getLoadout().get(slot);
				GearItem inherited = setup.getSwitchFrom() == null ? null : setup.getSwitchFrom().get(slot);
				if (worn != null && worn.getId() == item.getId() && (includeFilled || !setup.getFilledSlots().contains(slot)
					&& (inherited == null || inherited.getId() != item.getId())))
				{
					InventoryGroup combat = setup.getCombatClass() == CombatClass.MELEE ? InventoryGroup.MELEE_GEAR
						: setup.getCombatClass() == CombatClass.RANGED ? InventoryGroup.RANGED_GEAR : InventoryGroup.MAGIC_GEAR;
					if (combat.compareTo(group) < 0) { group = combat; }
				}
			}
			for (SpecialPlan plan : setup.getSpecials())
			{
				for (GearItem special : new GearItem[]{plan.getWeapon(), plan.getOffhand(), plan.getAmmo()})
				{
					if (special != null && special.getId() == item.getId() && InventoryGroup.SPECIAL.compareTo(group) < 0)
					{ group = InventoryGroup.SPECIAL; }
				}
			}
		}
		return group;
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
				List<Integer> ids = new ArrayList<>(BoostAccess.itemIds(potion));
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
