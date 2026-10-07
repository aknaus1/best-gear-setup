package com.bestgearsetup.data;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class PrayerDataTest
{
	private static Prayer prayer(String name, double boost)
	{
		Prayer p = new Prayer();
		p.setName(name);
		p.setPrayerbook("standard");
		p.setBoost(boost);
		p.setPrayerLevel(70);
		p.setDefenceLevel(70);
		return p;
	}

	@Test
	public void sourceOnlyPrayerIsExcludedWithoutChangingPietyBoosts()
	{
		Map<String, List<Prayer>> prayers = new HashMap<>();
		prayers.put("attack", Arrays.asList(prayer("piety", 20), prayer("zeal", 25)));
		prayers.put("strength", Arrays.asList(prayer("piety", 23), prayer("zeal", 28)));
		GameData data = new GameData(Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), prayers, null);
		List<OffensivePrayer> melee = data.getPrayers(CombatClass.MELEE);
		assertEquals(1, melee.size());
		assertEquals("piety", melee.get(0).getName());
		assertEquals(20, melee.get(0).getAccuracyPercent(), 0);
		assertEquals(23, melee.get(0).getDamagePercent(), 0);
	}
}
