package com.bestgearsetup;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemMapping;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Tracks which items the player owns across bank, inventory and worn equipment, with stack sizes so
 * ammunition can be priced by the shortfall. Each container is persisted per RuneScape profile so the
 * bank is remembered after it has been opened once.
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
	private volatile Map<Integer, Long> bank = Collections.emptyMap();
	private volatile Map<Integer, Long> inventory = Collections.emptyMap();
	private volatile Map<Integer, Long> worn = Collections.emptyMap();
	/** Items the player marked as owned by hand (e.g. stored in the POH costume room); no quantity is known. */
	private volatile Set<Integer> manual = Collections.emptySet();
	/** Owned ids, equivalent variants and the bases contained in decorated equipment. */
	private volatile Set<Integer> expanded = Collections.emptySet();
	/** Total quantity per id across containers; {@link #UNLIMITED} for items marked owned by hand. */
	private volatile Map<Integer, Long> quantities = Collections.emptyMap();

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

		Map<Integer, Long> previous = containerId == InventoryID.BANK ? bank : containerId == InventoryID.INV ? inventory : worn;
		boolean changed = !ids.equals(previous) || (containerId == InventoryID.BANK && !bankKnown);
		if (!changed)
		{
			return;
		}
		// Firing ammunition only changes stack sizes; the owned set is re-expanded only when items come or go.
		boolean itemsChanged = !ids.keySet().equals(previous.keySet());

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
		rebuild(itemsChanged);
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
		return expanded.contains(itemId);
	}

	/** Immutable view of the owned ids at this moment, unaffected by later container changes. */
	public Set<Integer> snapshot()
	{
		return expanded;
	}

	/** Immutable quantities at this moment: held stacks per id, {@link #UNLIMITED} for manual entries. */
	public Map<Integer, Long> quantitySnapshot()
	{
		return quantities;
	}

	/** Distinct items seen in the bank, inventory and worn equipment, excluding manual entries. */
	public int count()
	{
		Set<Integer> all = new HashSet<>(bank.keySet());
		all.addAll(inventory.keySet());
		all.addAll(worn.keySet());
		return all.size();
	}

	private void rebuild()
	{
		rebuild(true);
	}

	private void rebuild(boolean itemsChanged)
	{
		Map<Integer, Long> total = combine(java.util.Arrays.asList(bank, inventory, worn), manual);
		if (itemsChanged)
		{
			expanded = expand(total.keySet());
		}
		quantities = Collections.unmodifiableMap(total);
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

	private static long saturatedAdd(long a, long b)
	{
		long sum = a + b;
		return sum < 0 ? UNLIMITED : sum;
	}

	/** Follow outgoing component links without treating a base item as an unowned paid upgrade. */
	static Set<Integer> expand(Set<Integer> actual)
	{
		Set<Integer> all = new HashSet<>();
		for (int id : actual)
		{
			addEquivalentVariants(all, id);
			Collection<ItemMapping> mappings = ItemMapping.map(id);
			if (mappings == null)
			{
				continue;
			}
			for (ItemMapping mapping : mappings)
			{
				int baseItem = mapping.getTradeableItem();
				if (ItemCosts.containsComponents(id, baseItem))
				{
					addEquivalentVariants(all, baseItem);
				}
			}
		}
		return Collections.unmodifiableSet(all);
	}

	private static void addEquivalentVariants(Set<Integer> all, int id)
	{
		all.add(id);
		if (DiaryRewards.isReward(id))
		{
			return;
		}
		int base = ItemVariationMapping.map(id);
		for (int variant : ItemVariationMapping.getVariations(base))
		{
			if (ItemCosts.equivalent(id, variant))
			{
				all.add(variant);
			}
		}
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
