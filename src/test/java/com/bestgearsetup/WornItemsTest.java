package com.bestgearsetup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.GearItem;
import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

public class WornItemsTest
{
	/** Main code gets the client's Gson in startUp; tests supply their own. */
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(new Gson());
	}

	private static BestGearSetupPlugin wearing(int... ids) throws Exception
	{
		OwnedItems owned = new OwnedItems(null, null);
		Map<Integer, Long> worn = new HashMap<>();
		for (int id : ids)
		{
			worn.put(id, 1L);
		}
		set(owned, OwnedItems.class, "worn", worn);
		// Held in the bank only: owned but not worn.
		set(owned, OwnedItems.class, "bank", Collections.singletonMap(4151, 1L));
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		set(plugin, BestGearSetupPlugin.class, "ownedItems", owned);
		return plugin;
	}

	private static GearItem item(int id, Integer... variants)
	{
		GearItem item = new GearItem();
		item.setId(id);
		item.setVariants(Arrays.asList(variants));
		return item;
	}

	@Test
	public void wornItemsAndTheirChargeStatesCountAsEquipped() throws Exception
	{
		BestGearSetupPlugin plugin = wearing(1704);
		assertTrue(plugin.wears(item(1704)));
		// An amulet of glory (4) is equipped when the uncharged glory is worn.
		assertTrue(plugin.wears(item(1712, 1704, 1712)));
	}

	@Test
	public void itemsOnlyInTheBankAreNotEquipped() throws Exception
	{
		BestGearSetupPlugin plugin = wearing(1704);
		assertFalse(plugin.wears(item(4151)));
		assertFalse(wearing().wears(item(1704)));
	}

	private static void set(Object target, Class<?> type, String name, Object value) throws Exception
	{
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
