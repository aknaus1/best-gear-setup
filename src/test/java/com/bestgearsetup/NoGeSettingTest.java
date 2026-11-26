package com.bestgearsetup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import com.bestgearsetup.calc.SearchMode;
import org.junit.Test;

/** No GE is off by default, and only searches that buy (budget, best in slot) use the shop stock. */
public class NoGeSettingTest
{
	private static Options config(SearchMode mode, boolean noGe)
	{
		return new Options()
		{
			@Override public SearchMode mode() { return mode; }
			@Override public boolean noGe() { return noGe; }
		};
	}

	@Test
	public void offByDefault()
	{
		assertFalse(new Options() {}.noGe());
		assertNull(BestGearSetupPlugin.shopStock(new Options() {}));
	}

	@Test
	public void onlySearchesThatBuyUseShopStock()
	{
		assertNotNull(BestGearSetupPlugin.shopStock(config(SearchMode.BUDGET, true)));
		assertNotNull(BestGearSetupPlugin.shopStock(config(SearchMode.UNLIMITED, true)));
		assertNull(BestGearSetupPlugin.shopStock(config(SearchMode.OWNED_ONLY, true)));
		assertNull(BestGearSetupPlugin.shopStock(config(SearchMode.INVENTORY_ONLY, true)));
		assertNull(BestGearSetupPlugin.shopStock(config(SearchMode.BUDGET, false)));
	}
}
