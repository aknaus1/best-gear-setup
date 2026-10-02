package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import com.bestgearsetup.calc.PlayerLevels;
import org.junit.Test;

public class RememberedLevelsTest
{
	@Test
	public void roundTripsThroughTheProfileValue()
	{
		RememberedLevels levels = new RememberedLevels(new PlayerLevels(90, 91, 80, 92, 94, 76, 87, 85), 70);
		assertEquals(levels, RememberedLevels.parse(levels.serialize()));
	}

	@Test
	public void incompleteOrCorruptValuesAreIgnored()
	{
		assertNull(RememberedLevels.parse(null));
		assertNull(RememberedLevels.parse(""));
		assertNull(RememberedLevels.parse("90,91,80"));
		assertNull(RememberedLevels.parse("90,91,80,92,x,76,87,85,70"));
		// Levels read before the client has loaded them are 0 and must never be remembered.
		RememberedLevels loading = new RememberedLevels(new PlayerLevels(90, 0, 80, 92, 94, 76, 87, 85), 70);
		assertFalse(loading.complete());
		assertNull(RememberedLevels.parse(loading.serialize()));
	}
}
