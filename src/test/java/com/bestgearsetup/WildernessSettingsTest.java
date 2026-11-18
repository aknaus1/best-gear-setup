package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.OptimizerSettings;
import java.util.Collections;
import org.junit.Test;

public class WildernessSettingsTest
{
	private OptimizerSettings settings(Options config)
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		plugin.config = config;
		return plugin.buildSettings(config);
	}

	@Test
	public void savedLimitActivatesOnlyInWildernessAndAcceptsGpSuffixes() throws Exception
	{
		Options planning = new Options()
		{
			@Override public boolean limitWildernessRisk() { return true; }
			@Override public int maxExpensiveItems() { return 0; }
			@Override public String expensiveItemThreshold() { return "500k"; }
		};
		assertFalse(settings(planning).isWildernessRiskLimited());
		// Live fight detection overrides only the location; saved risk choices still apply.
		Options live = Assumptions.capture(planning, Collections.singletonMap("wilderness", true));
		OptimizerSettings result = settings(live);
		assertTrue(result.isWildernessRiskLimited());
		assertEquals(0, result.getMaxExpensiveItems());
		assertEquals(500_000, result.getExpensiveItemThreshold());
		assertFalse(planning.wilderness());
		assertFalse(settings(new Options() { @Override public boolean wilderness() { return true; } })
			.isWildernessRiskLimited());
	}

	@Test
	public void invalidThresholdNeverSilentlyRemovesTheLimit() throws Exception
	{
		OptimizerSettings result = settings(new Options()
		{
			@Override public boolean wilderness() { return true; }
			@Override public boolean limitWildernessRisk() { return true; }
			@Override public String expensiveItemThreshold() { return "bad value"; }
		});
		assertTrue(result.isWildernessRiskLimited());
		assertEquals(0, result.getExpensiveItemThreshold());
	}
}
