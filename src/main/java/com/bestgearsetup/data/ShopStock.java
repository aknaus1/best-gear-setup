package com.bestgearsetup.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;

/**
 * Equipment a shop stocks, from the bundled Wiki snapshot (shop-stock.json, made by tools/wiki_shops.py): the
 * cheapest coin price, or the other currency it is sold for when no coin shop stocks it. Stocked means a default
 * stock of at least 1, or infinite. An item counts as stocked when its own id or a catalogued variant is.
 */
public final class ShopStock
{
	/** Parsed on first use, like {@link Names}. */
	private static final class Bundled
	{
		static final ShopStock STOCK = parse(Names.load("/com/bestgearsetup/shop-stock.json"));
	}

	private final Map<Integer, Long> coins;
	/** "375 Tokkul" by item id, for items no coin shop stocks. */
	private final Map<Integer, String> other;

	private ShopStock(Map<Integer, Long> coins, Map<Integer, String> other)
	{
		this.coins = coins;
		this.other = other;
	}

	public static ShopStock bundled()
	{
		return Bundled.STOCK;
	}

	/** {"coins": {"id": price}, "other": {"id": ["Currency", price]}}. */
	public static ShopStock parse(JsonObject root)
	{
		Map<Integer, Long> coins = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("coins").entrySet())
		{
			coins.put(Integer.parseInt(e.getKey()), e.getValue().getAsLong());
		}
		Map<Integer, String> other = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("other").entrySet())
		{
			other.put(Integer.parseInt(e.getKey()), String.format(Locale.ROOT, "%,d %s",
				e.getValue().getAsJsonArray().get(1).getAsLong(), e.getValue().getAsJsonArray().get(0).getAsString()));
		}
		return new ShopStock(coins, other);
	}

	public boolean stocks(Gear item)
	{
		return price(item) >= 0;
	}

	/** Cheapest coin price; 0 when only other currencies buy it, -1 when no shop stocks it. */
	public long price(Gear item)
	{
		long best = -1;
		for (int id : ids(item))
		{
			Long coin = coins.get(id);
			if (coin != null && (best < 0 || coin < best))
			{
				best = coin;
			}
		}
		return best >= 0 ? best : otherPrice(item) != null ? 0 : -1;
	}

	/** What a stocked item no coin shop sells costs instead, e.g. "375 Tokkul"; null otherwise. */
	public String otherPrice(Gear item)
	{
		String found = null;
		for (int id : ids(item))
		{
			if (coins.containsKey(id))
			{
				return null;
			}
			found = found != null ? found : other.get(id);
		}
		return found;
	}

	private static Set<Integer> ids(Gear item)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		ids.add(item.getId());
		ids.addAll(item.getVariants());
		return ids;
	}
}
