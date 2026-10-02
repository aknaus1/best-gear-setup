package com.bestgearsetup;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
 * Tracks which items the player owns across bank, inventory and worn equipment. Each container
 * is persisted per RuneScape profile so the bank is remembered after it has been opened once.
 */
@Singleton
public class OwnedItems
{
	private static final String BANK_KEY = "ownedBank";
	private static final String INVENTORY_KEY = "ownedInventory";
	private static final String WORN_KEY = "ownedWorn";
	private static final String MANUAL_KEY = "ownedManual";

	private final ConfigManager configManager;
	private final ItemManager itemManager;

	private volatile Set<Integer> bank = Collections.emptySet();
	private volatile Set<Integer> inventory = Collections.emptySet();
	private volatile Set<Integer> worn = Collections.emptySet();
	/** Items the player marked as owned by hand (e.g. stored in the POH costume room). */
	private volatile Set<Integer> manual = Collections.emptySet();
	/** Owned ids, equivalent variants and the bases contained in decorated equipment. */
	private volatile Set<Integer> expanded = Collections.emptySet();

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
		bank = read(BANK_KEY);
		inventory = read(INVENTORY_KEY);
		worn = read(WORN_KEY);
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

		Set<Integer> ids = new HashSet<>();
		for (Item item : container.getItems())
		{
			// Quantity 0 is a bank placeholder: not owned.
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				ids.add(itemManager.canonicalize(item.getId()));
			}
		}

		Set<Integer> previous = containerId == InventoryID.BANK ? bank : containerId == InventoryID.INV ? inventory : worn;
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
		configManager.setRSProfileConfiguration(BestGearSetupConfig.GROUP, key,
			ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
		rebuild();
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

	public int count()
	{
		Set<Integer> all = new HashSet<>(bank);
		all.addAll(inventory);
		all.addAll(worn);
		return all.size();
	}

	private void rebuild()
	{
		Set<Integer> actual = new HashSet<>();
		for (Set<Integer> s : java.util.Arrays.asList(bank, inventory, worn, manual))
		{
			actual.addAll(s);
		}
		expanded = expand(actual);
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
