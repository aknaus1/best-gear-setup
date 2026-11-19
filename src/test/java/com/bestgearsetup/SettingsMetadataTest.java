package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.PotionNames;
import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.http.api.RuneLiteAPI;
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

	@Test public void dropdownsRetainEverySelectableBundledPotionName() throws Exception
	{
		GameData data = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
		for (Combat cls : Combat.values())
		{
			Set<String> names = new HashSet<>();
			if (cls == Combat.MELEE) { for (PotionOptions.Melee option : PotionOptions.Melee.values()) { names.add(PotionOptions.value(option)); } }
			if (cls == Combat.RANGED) { for (PotionOptions.Ranged option : PotionOptions.Ranged.values()) { names.add(PotionOptions.value(option)); } }
			if (cls == Combat.MAGIC) { for (PotionOptions.Magic option : PotionOptions.Magic.values()) { names.add(PotionOptions.value(option)); } }
			assertTrue(names.contains("best")); assertTrue(names.contains("none"));
			for (String name : PotionNames.namesFor(data,cls)) { assertTrue(cls + ": " + name,names.contains(name)); }
			assertEquals(PotionNames.namesFor(data,cls).size() + 2,names.size());
		}
	}
}
