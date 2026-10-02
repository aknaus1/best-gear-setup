package com.bestgearsetup;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a RuneLite client with this plugin loaded, for local development.
 */
public class BestGearSetupPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BestGearSetupPlugin.class);
		RuneLite.main(args);
	}
}
