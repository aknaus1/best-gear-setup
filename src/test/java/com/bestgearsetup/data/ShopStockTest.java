package com.bestgearsetup.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.JsonParser;
import java.util.Arrays;
import org.junit.Test;

/** No GE's shop stock: coin prices, other currencies and catalogued variants. */
public class ShopStockTest
{
	private static final ShopStock STOCK = ShopStock.parse(new JsonParser().parse(
		"{\"coins\": {\"1\": 500, \"2\": 300, \"5\": 40}, \"other\": {\"3\": [\"Tokkul\", 78000], \"4\": [\"Castle wars ticket\", 6],"
			+ " \"5\": [\"Tokkul\", 1]}}").getAsJsonObject());

	private static Gear gear(int id, Integer... variants)
	{
		Gear item = new Gear();
		item.setId(id);
		item.setVariants(Arrays.asList(variants));
		return item;
	}

	@Test
	public void coinItemsCostTheirCheapestShopIncludingVariants()
	{
		assertEquals(500, STOCK.price(gear(1)));
		assertEquals(300, STOCK.price(gear(1, 1, 2)));
		assertTrue(STOCK.stocks(gear(9, 9, 2)));
		assertNull(STOCK.otherPrice(gear(1)));
	}

	@Test
	public void otherCurrencyItemsCostNoCoinsAndNameTheirCurrency()
	{
		assertTrue(STOCK.stocks(gear(3)));
		assertEquals(0, STOCK.price(gear(3)));
		assertEquals("78,000 Tokkul", STOCK.otherPrice(gear(3)));
		// A coin shop for any variant wins over another currency.
		assertEquals(300, STOCK.price(gear(4, 4, 2)));
		assertNull(STOCK.otherPrice(gear(4, 4, 2)));
		assertNull(STOCK.otherPrice(gear(5)));
	}

	@Test
	public void unstockedItemsAreNotSold()
	{
		assertFalse(STOCK.stocks(gear(7)));
		assertEquals(-1, STOCK.price(gear(7)));
		assertNull(STOCK.otherPrice(gear(7)));
	}

	@Test
	public void theBundledSnapshotPricesRealShopItems()
	{
		ShopStock bundled = ShopStock.bundled();
		assertTrue(bundled.price(gear(1127)) > 0);
		assertEquals("78,000 Tokkul", bundled.otherPrice(gear(6568)));
		assertTrue(bundled.stocks(gear(8839)));
		assertFalse(bundled.stocks(gear(4151)));
	}
}
