package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.data.OffensivePrayer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

public class PrayerUnlocksTest
{
	private static final List<OffensivePrayer> RANGED = Arrays.asList(
		new OffensivePrayer("eagle eye", 15, 15, 44, 1),
		new OffensivePrayer("deadeye", 18, 18, 62, 1),
		new OffensivePrayer("rigour", 20, 23, 74, 70));
	private static final List<OffensivePrayer> MELEE = Arrays.asList(
		new OffensivePrayer("ultimate strength", 0, 15, 31, 1),
		new OffensivePrayer("chivalry", 15, 18, 60, 65),
		new OffensivePrayer("piety", 20, 23, 70, 70));

	private static String best(List<OffensivePrayer> prayers, Set<String> unlocked)
	{
		OffensivePrayer p = PrayerUnlocks.best(prayers, PlayerLevels.maxed(), PrayerUnlocks.available(unlocked));
		return p == null ? null : p.getName();
	}

	@Test
	public void lockedPrayersFallBackToTheBestUnlockedOne()
	{
		Set<String> none = Collections.emptySet();
		assertEquals("eagle eye", best(RANGED, none));
		assertEquals("ultimate strength", best(MELEE, none));
		assertEquals("deadeye", best(RANGED, new TreeSet<>(Collections.singleton("deadeye"))));
		assertEquals("piety", best(MELEE, new TreeSet<>(Arrays.asList("chivalry", "piety"))));
	}

	@Test
	public void unknownUnlocksAreAssumedLikeTheAssumed99s()
	{
		assertEquals("rigour", best(RANGED, null));
		assertEquals("piety", best(MELEE, null));
	}

	@Test
	public void profileValueRoundTrips()
	{
		Set<String> unlocked = new TreeSet<>(Arrays.asList("rigour", "mystic vigour", "piety"));
		assertEquals(unlocked, PrayerUnlocks.parse(PrayerUnlocks.serialize(unlocked)));
		assertEquals(Collections.emptySet(), PrayerUnlocks.parse(""));
		assertEquals(Collections.singleton("augury"), PrayerUnlocks.parse("Augury, not a prayer"));
		assertNull(PrayerUnlocks.parse(null));
	}
}
