package com.bestgearsetup;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import com.bestgearsetup.calc.SearchMode;
import lombok.Getter;
import lombok.Value;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Tracks bank, inventory, equipment and optional DWMS storage, with stack sizes for ammunition shortfalls.
 * Native containers are persisted per RuneScape profile; DWMS supplies its own remembered storage each session.
 * Inventory + equipped only searches see just the carried containers, excluding other storage and manual entries.
 */
@Singleton
public class OwnedItems
{
	private static final String BANK_KEY = "ownedBank";
	private static final String INVENTORY_KEY = "ownedInventory";
	private static final String WORN_KEY = "ownedWorn";
	private static final String MANUAL_KEY = "ownedManual";
	/** The quantity assumed for an item marked owned by hand: enough of any requested stack. */
	public static final long UNLIMITED = Long.MAX_VALUE;

	private final ConfigManager configManager;
	private final ItemManager itemManager;

	/** Quantity held per canonical item id, per container. */
	volatile Map<Integer, Long> bank = Collections.emptyMap();
	volatile Map<Integer, Long> inventory = Collections.emptyMap();
	volatile Map<Integer, Long> worn = Collections.emptyMap();
	/**
	 * Canonical ids equipped right now: set from the live worn container and cleared on logout. Unlike
	 * {@link #worn} it is never persisted, so remembered equipment doesn't show as equipped while logged out.
	 */
	volatile Set<Integer> equipped = Collections.emptySet();
	/** Items the player marked as owned by hand (e.g. an untracked storage); no quantity is known. */
	private volatile Set<Integer> manual = Collections.emptySet();
	/** One atomic, location-independent ownership category, shared by availability, pricing and cache keys. */
	@Value
	public static class Snapshot
	{
		Set<Integer> ids;
		Map<Integer, Long> quantities;
	}
	private static final Snapshot EMPTY = new Snapshot(Collections.emptySet(), Collections.emptyMap());
	private volatile Snapshot owned = EMPTY;
	private volatile Snapshot carried = EMPTY;
	/** Candidates must agree on two consecutive ticks; a bank transfer may straddle a tick boundary. */
	private Map<Integer, Long> pendingOwned;
	private Map<Integer, Long> pendingCarried;
	@Getter
	private volatile List<StoredLocation> storedLocations = Collections.emptyList();
	@Getter
	private volatile boolean storageConnected;

	@Getter
	private volatile boolean bankKnown;

	@Inject
	OwnedItems(ConfigManager configManager, ItemManager itemManager)
	{
		this.configManager = configManager;
		this.itemManager = itemManager;
	}

	/** Reload the persisted containers for the current profile. */
	public void load()
	{
		storedLocations = Collections.emptyList();
		storageConnected = false;
		bank = readQuantities(BANK_KEY);
		inventory = readQuantities(INVENTORY_KEY);
		worn = readQuantities(WORN_KEY);
		manual = read(MANUAL_KEY);
		bankKnown = configManager.getRSProfileConfiguration(BestGearSetupConfig.GROUP, BANK_KEY) != null;
		rebuild();
	}

	/** Must be called on the client thread (canonicalises item ids). */
	public void onContainerChanged(int containerId, ItemContainer container)
	{
		String key;
		if (containerId == InventoryID.BANK)
		{
			key = BANK_KEY;
		}
		else if (containerId == InventoryID.INV)
		{
			key = INVENTORY_KEY;
		}
		else if (containerId == InventoryID.WORN)
		{
			key = WORN_KEY;
		}
		else
		{
			return;
		}

		Map<Integer, Long> ids = new HashMap<>();
		for (Item item : container.getItems())
		{
			// Quantity 0 is a bank placeholder: not owned.
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				ids.merge(itemManager.canonicalize(item.getId()), (long) item.getQuantity(), Long::sum);
			}
		}

		if (containerId == InventoryID.WORN)
		{
			equipped = Collections.unmodifiableSet(new HashSet<>(ids.keySet()));
		}

		Map<Integer, Long> previous = containerId == InventoryID.BANK ? bank : containerId == InventoryID.INV ? inventory : worn;
		boolean changed = !ids.equals(previous) || (containerId == InventoryID.BANK && !bankKnown);
		if (!changed)
		{
			return;
		}
		if (containerId == InventoryID.BANK)
		{
			bank = ids;
			bankKnown = true;
		}
		else if (containerId == InventoryID.INV)
		{
			inventory = ids;
		}
		else
		{
			worn = ids;
		}
		configManager.setRSProfileConfiguration(BestGearSetupConfig.GROUP, key, serialize(ids));
		// Location and worn markers update immediately. Publish calculation ownership only after transfers settle.
	}

	public Set<Integer> getManual()
	{
		return Collections.unmodifiableSet(manual);
	}

	public boolean isManual(int itemId)
	{
		return manual.contains(itemId);
	}

	public void setManual(int itemId, boolean owned)
	{
		Set<Integer> next = new HashSet<>(manual);
		if (owned ? !next.add(itemId) : !next.remove(itemId))
		{
			return;
		}
		manual = next;
		configManager.setRSProfileConfiguration(BestGearSetupConfig.GROUP, MANUAL_KEY,
			next.stream().map(String::valueOf).collect(Collectors.joining(",")));
		rebuild();
	}

	public boolean owns(int itemId)
	{
		return owned.getIds().contains(itemId);
	}

	/** Whether the item (canonical id) is equipped right now; always false while logged out. */
	public boolean isWorn(int itemId)
	{
		return equipped.contains(itemId);
	}

	/** Logged out: nothing is equipped until the worn container is seen again. */
	public void clearEquipped()
	{
		equipped = Collections.emptySet();
	}

	/** Immutable view of the owned ids at this moment, unaffected by later container changes. */
	public Set<Integer> snapshot()
	{
		return owned.getIds();
	}

	/** Immutable quantities at this moment: held stacks per id, {@link #UNLIMITED} for manual entries. */
	public Map<Integer, Long> quantitySnapshot()
	{
		return owned.getQuantities();
	}

	/** {@link #snapshot()}, or only the carried items for an Inventory + equipped only search. */
	public Set<Integer> snapshot(SearchMode mode)
	{
		return capture(mode).getIds();
	}

	/** {@link #quantitySnapshot()}, or only the carried stacks for an Inventory + equipped only search. */
	public Map<Integer, Long> quantitySnapshot(SearchMode mode)
	{
		return capture(mode).getQuantities();
	}

	/** Read ids and stack totals from the same immutable combined snapshot. */
	public Snapshot capture(SearchMode mode)
	{
		return mode == SearchMode.INVENTORY_ONLY ? carried : owned;
	}

	/** Distinct tracked items, including connected storage, excluding manual entries. */
	public int count()
	{
		Set<Integer> all = new HashSet<>(bank.keySet());
		all.addAll(inventory.keySet());
		all.addAll(worn.keySet());
		for (StoredLocation location : storedLocations) { all.addAll(location.getItems().keySet()); }
		return all.size();
	}

	/** Replace the entire provider snapshot, including removals. It is not separately persisted. */
	boolean setStoredLocations(List<StoredLocation> locations, boolean connected)
	{
		boolean statusChanged = storageConnected != connected;
		storageConnected = connected;
		if (storedLocations.equals(locations)) { return statusChanged; }
		// A newer DWMS timestamp alone moves no items: keep it, but don't report a change.
		boolean itemsChanged = !contents(storedLocations).equals(contents(locations));
		storedLocations = Collections.unmodifiableList(new ArrayList<>(locations));
		return statusChanged || itemsChanged;
	}

	private static List<List<Object>> contents(List<StoredLocation> locations)
	{
		List<List<Object>> contents = new ArrayList<>();
		for (StoredLocation location : locations)
		{
			contents.add(java.util.Arrays.asList(location.getCategory(), location.getName(), location.getItems()));
		}
		return contents;
	}

	/** Locations matching the exact item or one of its reviewed ownership equivalents. */
	public Set<String> locations(int itemId, Collection<Integer> variants)
	{
		Set<Integer> ids = new HashSet<>(variants);
		ids.add(itemId);
		Set<String> names = new LinkedHashSet<>();
		if (!Collections.disjoint(ids, worn.keySet())) { names.add("Equipment"); }
		if (!Collections.disjoint(ids, inventory.keySet())) { names.add("Inventory"); }
		if (!Collections.disjoint(ids, bank.keySet())) { names.add("Bank"); }
		for (StoredLocation location : storedLocations)
		{
			if (!Collections.disjoint(ids, location.getItems().keySet())) { names.add(location.displayName()); }
		}
		if (!Collections.disjoint(ids, manual)) { names.add("Marked as owned by hand"); }
		return names;
	}

	private List<Map<Integer, Long>> allContainers()
	{
		List<Map<Integer, Long>> containers = new ArrayList<>(java.util.Arrays.asList(bank, inventory, worn));
		for (StoredLocation location : storedLocations) { containers.add(location.getItems()); }
		return containers;
	}

	private void rebuild()
	{
		rebuild(true);
	}

	/** Force a complete initial/manual snapshot; deltas are based on the combined category, never a source container. */
	void rebuild(boolean itemsChanged)
	{
		Map<Integer, Long> total = combine(allContainers(), manual);
		Map<Integer, Long> held = combine(java.util.Arrays.asList(inventory, worn), Collections.emptySet());
		owned = snapshot(owned, total);
		carried = snapshot(carried, held);
		pendingOwned = null;
		pendingCarried = null;
	}

	/** Client thread, once per completed tick (after the optional storage reply). */
	boolean settle()
	{
		Map<Integer, Long> total = combine(allContainers(), manual);
		Map<Integer, Long> held = combine(java.util.Arrays.asList(inventory, worn), Collections.emptySet());
		Snapshot before = owned;
		Snapshot carriedBefore = carried;
		if (total.equals(pendingOwned)) { owned = snapshot(owned, total); }
		if (held.equals(pendingCarried)) { carried = snapshot(carried, held); }
		pendingOwned = total;
		pendingCarried = held;
		return before != owned || carriedBefore != carried;
	}

	private static Snapshot snapshot(Snapshot previous, Map<Integer, Long> quantities)
	{
		if (previous.getQuantities().equals(quantities)) { return previous; }
		Set<Integer> ids = previous.getQuantities().keySet().equals(quantities.keySet())
			? previous.getIds() : expand(quantities.keySet());
		return new Snapshot(ids, Collections.unmodifiableMap(quantities));
	}

	/** Sum stacks across containers; a manual entry has no known quantity, so it covers any stack. */
	static Map<Integer, Long> combine(Collection<Map<Integer, Long>> containers, Set<Integer> manual)
	{
		Map<Integer, Long> total = new HashMap<>();
		for (Map<Integer, Long> container : containers)
		{
			container.forEach((id, qty) -> total.merge(id, qty, OwnedItems::saturatedAdd));
		}
		for (int id : manual)
		{
			total.put(id, UNLIMITED);
		}
		return total;
	}

	static long saturatedAdd(long a, long b)
	{
		long sum = a + b;
		return sum < 0 ? UNLIMITED : sum;
	}

	/** Exact holdings plus reviewed charge states; resale components are not separately held items. */
	static Set<Integer> expand(Set<Integer> actual)
	{
		Set<Integer> all = new HashSet<>(actual);
		for (int id : actual)
		{
			for (int variant : ItemVariationMapping.getVariations(ItemVariationMapping.map(id)))
			{
				if (OwnershipRules.equivalent(id, variant) && ItemCosts.equivalent(id, variant))
				{
					all.add(variant);
				}
			}
		}
		return Collections.unmodifiableSet(all);
	}

	static String serialize(Map<Integer, Long> quantities)
	{
		return quantities.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(","));
	}

	/** "id:quantity" entries; a bare "id" from older versions counts as one. */
	static Map<Integer, Long> parseQuantities(String value)
	{
		if (value == null || value.isEmpty())
		{
			return Collections.emptyMap();
		}
		Map<Integer, Long> out = new HashMap<>();
		for (String part : value.split(","))
		{
			String[] fields = part.trim().split(":", 2);
			try
			{
				long qty = fields.length > 1 ? Long.parseLong(fields[1].trim()) : 1;
				if (qty > 0)
				{
					out.merge(Integer.parseInt(fields[0].trim()), qty, OwnedItems::saturatedAdd);
				}
			}
			catch (NumberFormatException ignored)
			{
				// skip corrupt entries
			}
		}
		return out;
	}

	private Map<Integer, Long> readQuantities(String key)
	{
		return parseQuantities(configManager.getRSProfileConfiguration(BestGearSetupConfig.GROUP, key));
	}

	private Set<Integer> read(String key)
	{
		String value = configManager.getRSProfileConfiguration(BestGearSetupConfig.GROUP, key);
		if (value == null || value.isEmpty())
		{
			return Collections.emptySet();
		}
		Set<Integer> ids = new HashSet<>();
		for (String part : value.split(","))
		{
			try
			{
				ids.add(Integer.parseInt(part.trim()));
			}
			catch (NumberFormatException ignored)
			{
				// skip corrupt entries
			}
		}
		return ids;
	}
}
