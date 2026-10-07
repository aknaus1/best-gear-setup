package com.bestgearsetup;

import com.bestgearsetup.data.GearItem;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemMapping;
import net.runelite.client.game.ItemVariationMapping;

/** Tradable components needed to acquire equipment, rather than its resale value. */
public final class ItemCosts
{
	/**
	 * Untradeables bought for coins from a shop, at their full price: the Culinaromancer's Chest gloves after all
	 * Recipe for Disaster subquests, without the Lumbridge diary discount.
	 */
	private static final Map<Integer, Long> SHOP_PRICES = shopPrices();

	/**
	 * Perdu's fee to repair untradeables that break on an unprotected PvP death, by item name, from the OSRS Wiki's
	 * (broken) page. Ancient sceptres also charge the ancient staff's current price, which their acquisition
	 * price already covers.
	 */
	private static final Map<String, Long> REPAIR_COSTS = repairCosts();

	private ItemCosts()
	{
	}

	private static Map<Integer, Long> shopPrices()
	{
		Map<Integer, Long> costs = new HashMap<>();
		costs.put(ItemID.HUNDRED_GAUNTLETS_LEVEL_1, 65L);
		costs.put(ItemID.HUNDRED_GAUNTLETS_LEVEL_2, 130L);
		costs.put(ItemID.HUNDRED_GAUNTLETS_LEVEL_3, 325L);
		costs.put(ItemID.HUNDRED_GAUNTLETS_LEVEL_4, 650L);
		costs.put(ItemID.HUNDRED_GAUNTLETS_LEVEL_5, 1_300L);
		costs.put(ItemID.HUNDRED_GAUNTLETS_LEVEL_6, 1_950L);
		costs.put(ItemID.HUNDRED_GAUNTLETS_LEVEL_7, 3_250L);
		costs.put(ItemID.HUNDRED_GAUNTLETS_LEVEL_8, 6_500L);
		costs.put(ItemID.HUNDRED_GAUNTLETS_LEVEL_9, 130_000L);
		costs.put(ItemID.HUNDRED_GAUNTLETS_LEVEL_10, 130_000L);
		return Collections.unmodifiableMap(costs);
	}

	private static Map<String, Long> repairCosts()
	{
		Map<String, Long> costs = new HashMap<>();
		repair(costs, 150_000, "fire cape", "fire max cape", "fighter torso");
		repair(costs, 225_000, "infernal cape", "infernal max cape");
		repair(costs, 240_000, "ava's assembler", "assembler max cape", "masori assembler",
			"masori assembler max cape", "dragon defender");
		repair(costs, 96_000, "imbued guthix cape", "imbued saradomin cape", "imbued zamorak cape");
		repair(costs, 99_000, "imbued guthix max cape", "imbued saradomin max cape", "imbued zamorak max cape");
		repair(costs, 1_000, "bronze defender");
		repair(costs, 2_000, "iron defender");
		repair(costs, 2_500, "steel defender");
		repair(costs, 5_000, "black defender");
		repair(costs, 15_000, "mithril defender");
		repair(costs, 25_000, "adamant defender");
		repair(costs, 35_000, "rune defender");
		repair(costs, 600_000, "avernic defender");
		repair(costs, 160_000, "void melee helm", "void mage helm", "void ranger helm");
		repair(costs, 180_000, "void knight top", "void knight robe");
		repair(costs, 250_000, "elite void top", "elite void robe");
		repair(costs, 120_000, "void knight gloves");
		repair(costs, 20_000, "void knight mace", "penance skirt");
		repair(costs, 25_000, "guthix halo", "saradomin halo", "zamorak halo", "armadyl halo", "bandos halo",
			"seren halo", "ancient halo", "brassica halo");
		repair(costs, 45_000, "fighter hat", "ranger hat", "healer hat");
		repair(costs, 40_005, "runner hat");
		repair(costs, 10_500, "barronite mace");
		repair(costs, 200_000, "blood ancient sceptre", "smoke ancient sceptre", "ice ancient sceptre",
			"shadow ancient sceptre");
		repair(costs, 270_000, "dizana's quiver");
		repair(costs, 400_000, "blessed dizana's quiver", "dizana's max cape");
		return Collections.unmodifiableMap(costs);
	}

	private static void repair(Map<String, Long> costs, long fee, String... names)
	{
		for (String name : names)
		{
			costs.put(name, fee);
		}
	}

	/**
	 * Coins needed to repair the item after an unprotected PvP death, or 0 if it doesn't break. Variants share
	 * their base item's catalogue name, so a trimmed dragon defender pays the fee as well as losing its kit.
	 */
	public static long pvpRepairCost(GearItem item)
	{
		String name = item.getName() == null ? "" : item.getName().toLowerCase(Locale.ROOT);
		if (name.startsWith("decorative armour"))
		{
			return 5_000;
		}
		return REPAIR_COSTS.getOrDefault(name, 0L);
	}

	/** Component quantities, including consumed upgrades absent from RuneLite's resale mappings. */
	public static Map<Integer, Long> components(int itemId)
	{
		Map<Integer, Long> components = new HashMap<>();
		int base = acquisitionBase(itemId);
		int family = ItemVariationMapping.map(itemId);
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

		if (family == ItemVariationMapping.map(ItemID.BLOOD_AMULET))
		{
			components.put(ItemID.ENCHANTED_ONYX_AMULET, 1L);
			components.put(ItemID.BLOOD_SHARD, 1L);
		}
		else if (family == ItemVariationMapping.map(ItemID.INFERNAL_DEFENDER)
			|| family == ItemVariationMapping.map(ItemID.INFERNAL_DEFENDER_GHOMMAL_5)
			|| family == ItemVariationMapping.map(ItemID.INFERNAL_DEFENDER_GHOMMAL_6))
		{
			components.remove(base);
			components.put(ItemID.INFERNAL_DEFENDER_HILT, 1L);
		}
		else if (family == ItemVariationMapping.map(ItemID.ABYSSAL_TENTACLE))
		{
			components.put(ItemID.ABYSSAL_WHIP, 1L);
		}
		else if (family == ItemVariationMapping.map(ItemID.ECHO_BOOTS))
		{
			components.put(ItemID.ECHO_CRYSTAL, 1L);
		}
		return Collections.unmodifiableMap(components);
	}

	/** Cosmetic variants sold separately can share a RuneLite family without sharing an acquisition base. */
	private static int acquisitionBase(int itemId)
	{
		return OwnershipRules.identity(itemId);
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
	 * traded; otherwise it costs its coin shop price, or nothing when it is earned rather than bought.
	 *
	 * @param tradeable whether the game cache marks an item id as tradeable
	 */
	public static long price(GearItem item, IntToLongFunction quote, IntPredicate tradeable)
	{
		int base = acquisitionBase(item.getId());
		boolean unmapped = mappings(item.getId(), base) == null;
		long total = SHOP_PRICES.getOrDefault(base, 0L);
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

	/**
	 * Whether an item is acquired by buying tradable components: RuneLite maps it to a tradable base
	 * (charged Sanguinesti staff, Tumeken's shadow, trident of the swamp; a tormented synapse for the
	 * demonic weapons; imbued rings) or it consumes a tradable upgrade. Earned-only untradeables
	 * such as fire capes have neither.
	 */
	public static boolean hasTradableComponents(int itemId)
	{
		int base = acquisitionBase(itemId);
		boolean mapped = mappings(itemId, base) != null;
		for (int id : components(itemId).keySet())
		{
			if (mapped || id != base)
			{
				return true;
			}
		}
		return false;
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
