package com.bestgearsetup;

import static org.junit.Assert.assertTrue;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.config.ConfigItemDescriptor;
import org.junit.Test;

public class SettingsMetadataTest
{
	@Test public void visibleSettingsHaveDistinctPositionsWithinEachCategory()
	{
		Set<String> positions = new HashSet<>();
		for (ConfigItemDescriptor descriptor : ConfigItemsForTests.items())
		{
			if (!descriptor.getItem().hidden())
			{
				assertTrue(descriptor.key(), positions.add(descriptor.getItem().section() + ":" + descriptor.getItem().position()));
			}
		}
	}
}
