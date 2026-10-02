package com.bestgearsetup;

import com.bestgearsetup.data.GearItem;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemMapping;
import net.runelite.client.game.ItemVariationMapping;

/** Tradable components needed to acquire equipment, rather than its resale value. */
public final class ItemCosts
{
	private ItemCosts()
	{
	}

	/** Component quantities, including consumed upgrades absent from RuneLite's resale mappings. */
	public static Map<Integer, Long> components(int itemId)
	{
		Map<Integer, Long> components = new HashMap<>();
		int base = ItemVariationMapping.map(itemId);
		Collection<ItemMapping> mappings = mappings(itemId, base);
		if (mappings == null)
		{
			components.put(base, 1L);
		}
		else
		{
			for (ItemMapping mapping : mappings)
			{
				// Reward currencies have a cash-out value, but cannot be bought to acquire gear.
				if (mapping.getTradeableItem() != ItemID.GRACE
					&& mapping.getTradeableItem() != ItemID.MOTHERLODE_NUGGET)
				{
					components.merge(mapping.getTradeableItem(), mapping.getQuantity(), Long::sum);
				}
			}
		}

		if (base == ItemID.BLOOD_AMULET)
		{
			components.put(ItemID.ENCHANTED_ONYX_AMULET, 1L);
			components.put(ItemID.BLOOD_SHARD, 1L);
		}
		else if (base == ItemVariationMapping.map(ItemID.INFERNAL_DEFENDER)
			|| base == ItemVariationMapping.map(ItemID.INFERNAL_DEFENDER_GHOMMAL_5)
			|| base == ItemVariationMapping.map(ItemID.INFERNAL_DEFENDER_GHOMMAL_6))
		{
			components.remove(base);
			components.put(ItemID.INFERNAL_DEFENDER_HILT, 1L);
		}
		else if (base == ItemVariationMapping.map(ItemID.ABYSSAL_TENTACLE))
		{
			components.put(ItemID.ABYSSAL_WHIP, 1L);
		}
		else if (base == ItemVariationMapping.map(ItemID.ECHO_BOOTS))
		{
			components.put(ItemID.ECHO_CRYSTAL, 1L);
		}
		return Collections.unmodifiableMap(components);
	}

	/** RuneLite's tradable-component mappings for the item or its variation base; null if it has none. */
	private static Collection<ItemMapping> mappings(int itemId, int base)
	{
		Collection<ItemMapping> mappings = ItemMapping.map(itemId);
		return mappings == null && base != itemId ? ItemMapping.map(base) : mappings;
	}

	/** Price of an item that cannot be bought without a component that has no current quote. */
	public static final long UNKNOWN = -1;

	/**
	 * Current component prices, or {@link #UNKNOWN} if any purchasable component has no quote. RuneLite's mapped
	 * bases (an empty blowpipe, an inactive bow, an uncharged helm) and consumed upgrades are always purchasable.
	 * Only an item RuneLite maps to nothing is bought as its own variation base, and only when that base can be
	 * traded; otherwise it is earned rather than bought, so it costs nothing.
	 *
	 * @param tradeable whether the game cache marks an item id as tradeable
	 */
	public static long price(GearItem item, IntToLongFunction quote, IntPredicate tradeable)
	{
		int base = ItemVariationMapping.map(item.getId());
		boolean unmapped = mappings(item.getId(), base) == null;
		long total = 0;
		for (Map.Entry<Integer, Long> component : components(item.getId()).entrySet())
		{
			int id = component.getKey();
			long price = quote.applyAsLong(id);
			if (price > 0)
			{
				total += price * component.getValue();
			}
			else if (!unmapped || id != base || item.isTradeable() || tradeable.test(id))
			{
				return UNKNOWN;
			}
		}
		return total;
	}

	public static boolean isKnown(long price)
	{
		return price >= 0;
	}

	/** Variants with additional paid components must not be granted merely by owning the base. */
	public static boolean equivalent(int first, int second)
	{
		return components(first).equals(components(second));
	}

	/** Whether an owned mapped item already contains everything needed for its base component. */
	public static boolean containsComponents(int ownedItem, int baseItem)
	{
		Map<Integer, Long> owned = components(ownedItem);
		Map<Integer, Long> required = components(baseItem);
		if (required.isEmpty())
		{
			return false;
		}
		for (Map.Entry<Integer, Long> component : required.entrySet())
		{
			if (owned.getOrDefault(component.getKey(), 0L) < component.getValue())
			{
				return false;
			}
		}
		return true;
	}
}
