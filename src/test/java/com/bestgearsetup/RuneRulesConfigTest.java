package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.RuneFilter;
import com.bestgearsetup.calc.SpellRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class RuneRulesConfigTest
{
	private String blocked;
	private String only;
	private List<String> writes;
	private BestGearSetupPlugin plugin;

	@Before
	public void setUp()
	{
		blocked = "";
		only = "";
		writes = new ArrayList<>();
		plugin = new BestGearSetupPlugin()
		{
			@Override
			public void setConfig(String key, Object value)
			{
				writes.add(key + "=" + value);
				if (BestGearSetupConfig.BLOCKED_RUNES_KEY.equals(key))
				{
					blocked = (String) value;
				}
				else if (BestGearSetupConfig.ONLY_RUNES_KEY.equals(key))
				{
					only = (String) value;
				}
			}
		};
		plugin.config = new BestGearSetupConfig()
		{
			@Override
			public String blockedRunes()
			{
				return blocked;
			}

			@Override
			public String onlyRunes()
			{
				return only;
			}
		};
	}

	@Test
	public void noConfigGivesNoFilter()
	{
		assertEquals(RuneFilter.NONE, new BestGearSetupPlugin().getRuneFilter());
	}

	@Test
	public void getRuneFilterParsesConfig()
	{
		blocked = "blood,death";
		only = "air";
		assertEquals(RuneFilter.parse("blood,death", "air"), plugin.getRuneFilter());
	}

	@Test
	public void blockWritesOnlyBlockedKey()
	{
		plugin.setRuneRule("blood", SpellRule.BLOCK);
		assertEquals(1, writes.size());
		assertEquals(BestGearSetupConfig.BLOCKED_RUNES_KEY + "=blood", writes.get(0));
	}

	@Test
	public void forceMovesRuneFromBlockedToOnly()
	{
		blocked = "blood";
		plugin.setRuneRule("blood", SpellRule.FORCE);
		assertEquals("", blocked);
		assertEquals("blood", only);
		assertEquals(2, writes.size());
	}

	@Test
	public void blockMovesRuneFromOnlyToBlocked()
	{
		only = "air";
		plugin.setRuneRule("air", SpellRule.BLOCK);
		assertEquals("air", blocked);
		assertEquals("", only);
	}

	@Test
	public void allowRemovesFromBoth()
	{
		blocked = "blood";
		only = "air";
		plugin.setRuneRule("blood", SpellRule.ALLOW);
		assertEquals("", blocked);
		assertEquals("air", only);
		plugin.setRuneRule("air", SpellRule.ALLOW);
		assertEquals("", only);
	}

	@Test
	public void unchangedRuleWritesNothing()
	{
		blocked = "blood";
		plugin.setRuneRule("blood", SpellRule.BLOCK);
		plugin.setRuneRule("death", SpellRule.ALLOW);
		assertTrue(writes.isEmpty());
	}

	@Test
	public void clearEmptiesEachKey()
	{
		blocked = "blood";
		only = "air";
		plugin.clearBlockedRunes();
		assertEquals("", blocked);
		assertEquals("air", only);
		plugin.clearOnlyRunes();
		assertEquals("", only);
	}
}
