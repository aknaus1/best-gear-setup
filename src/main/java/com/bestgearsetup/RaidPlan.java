package com.bestgearsetup;

import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.calc.WeaponRules;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Collections;
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

/** A shared kit and one valid setup for every encounter, starting in the first target's gear. */
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

	public SetupResult getStartingSetup() { return setups.get(0); }

	/** Active searches use the same carried ammunition as the complete kit. */
	SetupResult fillAmmoFor(SetupResult setup, SearchResults result)
	{
		List<SetupResult> choices = new ArrayList<>(setups); choices.add(setup);
		List<SearchResults> context = new ArrayList<>(results); context.add(result);
		return fillAmmo(choices, context).get(choices.size() - 1);
	}

	/** Supplies and special-attack switches for an individual encounter. */
	public static List<InventoryItem> inventoryForSetup(SetupResult setup, SearchResults results, IntPredicate owns)
	{
		return inventory(Collections.singletonList(setup), Collections.singletonList(results), owns);
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
		return carried;
	}

	/** Active recalculations may only use supplies already listed in this kit. */
	boolean supportsSupplies(SetupResult setup, SearchResults result, IntPredicate owns)
	{
		Set<Integer> total = new java.util.HashSet<>();
		equipment.forEach(item -> total.add(item.getId()));
		inventory.forEach(item -> total.add(item.getIds().get(0)));
		for (Loadout loadout : wornStates(setup))
		{
			Set<Integer> carried = new java.util.HashSet<>(total);
			carried.removeAll(wornIds(loadout));
			if (carried.size() > 28) { return false; }
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
		for (Potion potion : result.potionsForSetup(setup))
		{
			if (!potion.isRaidSupply() && BoostAccess.itemIds(potion).stream().noneMatch(packed::contains)) { return false; }
		}
		return true;
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

		public InventoryItem(String name, List<Integer> ids, boolean equipment)
		{
			this(name, ids, equipment, Collections.emptyList());
		}
	}

	static Map<Integer, GearItem> gear(SetupResult setup)
	{
		Map<Integer, GearItem> out = new LinkedHashMap<>();
		for (Slot slot : Slot.values()) { put(out, setup.getLoadout().get(slot)); }
		if (setup.getSpecial() != null)
		{
			put(out, setup.getSpecial().getWeapon());
			put(out, setup.getSpecial().getOffhand());
			put(out, setup.getSpecial().getAmmo());
		}
		// Loaded blowpipe darts are supplies, not an inventory equipment switch.
		return out;
	}

	static void put(Map<Integer, GearItem> out, GearItem item)
	{
		if (item != null && !providedInRaid(item)) { out.putIfAbsent(item.getId(), item); }
	}

	static boolean providedInRaid(GearItem item) { return "dawnbringer".equalsIgnoreCase(item.getCombatName()); }

	static List<InventoryItem> inventory(List<SetupResult> setups, List<SearchResults> results, IntPredicate owns)
	{
		Memo memo = new Memo();
		return filledInventory(fillAmmo(setups, results, memo), results, owns, memo);
	}

	/** {@link #inventory} for setups that already went through {@link #fillAmmo}. */
	static List<InventoryItem> filledInventory(List<SetupResult> setups, List<SearchResults> results, IntPredicate owns, Memo memo)
	{
		Map<Integer, GearItem> gear = new LinkedHashMap<>();
		setups.forEach(s -> gear.putAll(memo.gear(s)));
		for (Slot slot : Slot.values())
		{
			GearItem worn = setups.get(0).getLoadout().get(slot);
			if (worn != null) { gear.remove(worn.getId()); }
		}
		List<InventoryItem> items = new ArrayList<>();
		Set<Integer> wornIds = new java.util.HashSet<>();
		for (Slot slot : Slot.values())
		{
			GearItem worn = setups.get(0).getLoadout().get(slot);
			if (worn != null) { wornIds.add(worn.getId()); }
		}
		for (GearItem item : gear.values()) { items.add(equipmentItem(item, setups)); }
		for (int i = 0; i < setups.size(); i++)
		{
			Supplies supplies = memo.supplies(setups.get(i), results.get(i), owns);
			for (InventoryItem spell : supplies.spells)
			{
				if (!wornIds.contains(spell.getIds().get(0))) { addSupply(items, spell); }
			}
			supplies.boosts.forEach(boost -> addSupply(items, boost));
		}
		packRunes(items, owns);
		return items;
	}

	/** Keep required ammunition worn during fights that leave the slot free; loaded darts remain inside the pipe. */
	static List<SetupResult> fillAmmo(List<SetupResult> setups, List<SearchResults> results)
	{
		return fillAmmo(setups, results, new Memo());
	}

	static List<SetupResult> fillAmmo(List<SetupResult> setups, List<SearchResults> results, Memo memo)
	{
		Map<Integer, GearItem> ammo = new LinkedHashMap<>();
		Map<Integer, Integer> uses = new LinkedHashMap<>();
		for (SetupResult setup : setups)
		{
			for (GearItem item : new GearItem[]{setup.getLoadout().get(Slot.AMMO), setup.getSpecial() == null ? null : setup.getSpecial().getAmmo()})
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
			setup.getFilledSlots(), setup.getSupportSpells(), setup.getSpecial());
	}

	private static InventoryItem equipmentItem(GearItem item, List<SetupResult> setups)
	{
		Map<Integer, InventoryItem> contents = new LinkedHashMap<>();
		for (SetupResult setup : setups)
		{
			if (setup.getLoadout().getWeapon() != null && setup.getLoadout().getWeapon().getId() == item.getId())
			{ loaded(contents, setup.getLoadout().getLoadedAmmo()); }
			if (setup.getSpecial() != null && setup.getSpecial().getWeapon().getId() == item.getId())
			{ loaded(contents, setup.getSpecial().getLoadedAmmo()); }
		}
		return new InventoryItem(item.getName(), BestGearSetupBankView.itemIds(item), true,
			Collections.unmodifiableList(new ArrayList<>(contents.values())));
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
		Memo memo = new Memo();
		setups = fillAmmo(setups, results, memo);
		return filledMaximumInventory(setups, filledInventory(setups, results, owns, memo), memo);
	}

	/** {@link #maximumInventory} for filled setups and their {@link #filledInventory}. */
	static int filledMaximumInventory(List<SetupResult> setups, List<InventoryItem> inventory, Memo memo)
	{
		Set<Integer> carried = new java.util.HashSet<>();
		setups.forEach(s -> carried.addAll(memo.gear(s).keySet()));
		inventory.forEach(item -> carried.add(item.getIds().get(0)));
		int maximum = inventory.size();
		for (SetupResult setup : setups)
		{
			for (Set<Integer> worn : memo.worn(setup))
			{
				int stillWorn = 0;
				for (int id : worn) { if (carried.contains(id)) { stillWorn++; } }
				maximum = Math.max(maximum, carried.size() - stillWorn);
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
		if (setup.getSpecial() != null)
		{
			Loadout special = setup.getLoadout().copy();
			special.set(Slot.WEAPON, setup.getSpecial().getWeapon());
			if (setup.getSpecial().getWeapon().isTwoHanded()) { special.set(Slot.SHIELD, null); }
			else if (setup.getSpecial().getOffhand() != null) { special.set(Slot.SHIELD, setup.getSpecial().getOffhand()); }
			if (setup.getSpecial().getAmmo() != null) { special.set(Slot.AMMO, setup.getSpecial().getAmmo()); }
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
			for (List<Integer> ids : SpellRunes.bankItems(setup, owns)) { spells.add(supply(SpellRunes.supplyName(ids.get(0)), ids)); }
			for (Potion potion : result.potionsForSetup(setup))
			{
				List<Integer> ids = new ArrayList<>(BoostAccess.itemIds(potion));
				if (!potion.isRaidSupply() && !ids.isEmpty()) { boosts.add(supply(potion.getName(), ids)); }
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
		private final Map<SetupResult, List<Set<Integer>>> worn = new IdentityHashMap<>();

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

		private List<Set<Integer>> worn(SetupResult setup)
		{
			return worn.computeIfAbsent(setup, s -> wornStates(s).stream().map(RaidPlan::wornIds).collect(Collectors.toList()));
		}
	}
}
