package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.PotionChoice;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.google.gson.Gson;
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

	@Test public void dropdownsRetainEverySelectableBundledPotionName() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		for (CombatClass cls : CombatClass.values())
		{
			Set<String> names = new HashSet<>();
			if (cls == CombatClass.MELEE) { for (PotionOptions.Melee option : PotionOptions.Melee.values()) { names.add(option.value()); } }
			if (cls == CombatClass.RANGED) { for (PotionOptions.Ranged option : PotionOptions.Ranged.values()) { names.add(option.value()); } }
			if (cls == CombatClass.MAGIC) { for (PotionOptions.Magic option : PotionOptions.Magic.values()) { names.add(option.value()); } }
			assertTrue(names.contains("best")); assertTrue(names.contains("none"));
			for (String name : PotionChoice.namesFor(data,cls)) { assertTrue(cls + ": " + name,names.contains(name)); }
			assertEquals(PotionChoice.namesFor(data,cls).size() + 2,names.size());
		}
	}
}
