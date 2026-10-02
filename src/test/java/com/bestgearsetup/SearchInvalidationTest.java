package com.bestgearsetup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class SearchInvalidationTest
{
	private static Set<Integer> ids(Integer... ids)
	{
		return new HashSet<>(Arrays.asList(ids));
	}

	@Test
	public void gainingOrLosingEquipmentInvalidatesResults()
	{
		Set<Integer> gear = ids(100, 200, 201);
		assertTrue(BestGearSetupPlugin.gearOwnershipDiffers(ids(100, 200), ids(100), gear));
		assertTrue(BestGearSetupPlugin.gearOwnershipDiffers(ids(100), ids(100, 201), gear));
	}

	@Test
	public void consumablesAndUnchangedGearDoNotInvalidateResults()
	{
		Set<Integer> gear = ids(100, 200);
		// e.g. a potion dose changing id, or food running out.
		assertFalse(BestGearSetupPlugin.gearOwnershipDiffers(ids(100, 2434), ids(100, 139), gear));
		assertFalse(BestGearSetupPlugin.gearOwnershipDiffers(ids(100, 200), ids(200, 100), gear));
	}
}
