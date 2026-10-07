package com.bestgearsetup;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/** The config item descriptors RuneLite's ConfigManager builds, for tests that run without a ConfigManager. */
public final class ConfigItemsForTests
{
	private ConfigItemsForTests()
	{
	}

	public static List<ConfigItemDescriptor> items()
	{
		List<ConfigItemDescriptor> items = new ArrayList<>();
		for (Method m : BestGearSetupConfig.class.getMethods())
		{
			if (m.getParameterCount() == 0 && m.isAnnotationPresent(ConfigItem.class))
			{
				items.add(new ConfigItemDescriptor(m.getDeclaredAnnotation(ConfigItem.class), m.getGenericReturnType(),
					m.getDeclaredAnnotation(Range.class), m.getDeclaredAnnotation(Alpha.class),
					m.getDeclaredAnnotation(Units.class)));
			}
		}
		return items;
	}
}
