package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class BudgetTest
{
	@Test
	public void parses()
	{
		assertEquals(750_000, Budget.parse("750k"));
		assertEquals(50_000_000, Budget.parse("50m"));
		assertEquals(1_200_000_000, Budget.parse("1.2b"));
		assertEquals(12_345, Budget.parse("12,345"));
		assertEquals(-1, Budget.parse("lots"));
		assertEquals(-1, Budget.parse(""));
	}

	@Test
	public void formats()
	{
		assertEquals("10m", Budget.format(10_000_000));
		assertEquals("1.25b", Budget.format(1_250_000_000));
		assertEquals("750k", Budget.format(750_000));
		assertEquals("9999", Budget.format(9_999));
	}
}
