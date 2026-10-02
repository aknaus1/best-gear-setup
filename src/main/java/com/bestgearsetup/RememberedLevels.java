package com.bestgearsetup;

import com.bestgearsetup.calc.PlayerLevels;
import lombok.Value;

/**
 * The account's base combat levels (and Mining, for raid scaling), remembered per RuneScape profile so
 * logged-out searches match the logged-in ones instead of assuming 99s.
 */
@Value
class RememberedLevels
{
	/** RuneScape-profile key. */
	static final String CONFIG_KEY = "levels";

	PlayerLevels levels;
	int mining;

	/** Whether every level is loaded; real levels read as 0 until the client has received them. */
	boolean complete()
	{
		return levels.getAttack() > 0 && levels.getStrength() > 0 && levels.getDefence() > 0
			&& levels.getRanged() > 0 && levels.getMagic() > 0 && levels.getPrayer() > 0
			&& levels.getHitpoints() > 0 && levels.getSlayer() > 0 && mining > 0;
	}

	String serialize()
	{
		return levels.getAttack() + "," + levels.getStrength() + "," + levels.getDefence() + ","
			+ levels.getRanged() + "," + levels.getMagic() + "," + levels.getPrayer() + ","
			+ levels.getHitpoints() + "," + levels.getSlayer() + "," + mining;
	}

	/** Parsed profile value, or null when nothing complete was recorded. */
	static RememberedLevels parse(String value)
	{
		if (value == null)
		{
			return null;
		}
		String[] parts = value.split(",");
		if (parts.length != 9)
		{
			return null;
		}
		int[] v = new int[parts.length];
		try
		{
			for (int i = 0; i < parts.length; i++)
			{
				v[i] = Integer.parseInt(parts[i].trim());
			}
		}
		catch (NumberFormatException e)
		{
			return null;
		}
		RememberedLevels remembered = new RememberedLevels(
			new PlayerLevels(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]), v[8]);
		return remembered.complete() ? remembered : null;
	}
}
