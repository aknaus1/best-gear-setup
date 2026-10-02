package com.bestgearsetup;

import java.util.Locale;

/**
 * Parses and formats GP amounts like "750k", "50m", "1.2b".
 */
public final class Budget
{
	private Budget()
	{
	}

	/** @return the amount in GP, or -1 if the text is not a valid amount */
	public static long parse(String text)
	{
		if (text == null)
		{
			return -1;
		}
		String s = text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace(" ", "");
		if (s.isEmpty())
		{
			return -1;
		}
		long multiplier = 1;
		char last = s.charAt(s.length() - 1);
		if (last == 'k' || last == 'm' || last == 'b')
		{
			multiplier = last == 'k' ? 1_000L : last == 'm' ? 1_000_000L : 1_000_000_000L;
			s = s.substring(0, s.length() - 1);
		}
		try
		{
			double value = Double.parseDouble(s);
			if (value < 0 || Double.isNaN(value) || Double.isInfinite(value))
			{
				return -1;
			}
			return (long) Math.floor(value * multiplier);
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	public static String format(long gp)
	{
		if (gp >= 1_000_000_000L)
		{
			return trim(gp / 1_000_000_000.0) + "b";
		}
		if (gp >= 1_000_000L)
		{
			return trim(gp / 1_000_000.0) + "m";
		}
		if (gp >= 10_000L)
		{
			return trim(gp / 1_000.0) + "k";
		}
		return String.valueOf(gp);
	}

	private static String trim(double v)
	{
		String s = String.format(Locale.ROOT, "%.2f", v);
		s = s.replaceAll("0+$", "");
		return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
	}
}
