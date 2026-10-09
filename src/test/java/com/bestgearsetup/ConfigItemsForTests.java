package com.bestgearsetup;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;

/** The config item descriptors RuneLite's ConfigManager builds, for tests that run without a ConfigManager. */
public final class ConfigItemsForTests
{
	private ConfigItemsForTests()
	{
	}

	public static List<ConfigItemDescriptor> items()
	{
		// getConfigDescriptor reads only the config interface, so the manager's own state is never needed.
		ConfigManager manager = mock(ConfigManager.class, CALLS_REAL_METHODS);
		return new ArrayList<>(manager.getConfigDescriptor(new BestGearSetupConfig()
		{
		}).getItems());
	}

	/** Owned items with no account data, as the plugin has before login. */
	public static OwnedItems emptyOwnedItems()
	{
		return new OwnedItems(null, null);
	}
}
