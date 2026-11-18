package com.bestgearsetup;

import com.bestgearsetup.calc.PlayerLevels;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * The account's base combat levels (and Mining, for raid scaling), remembered per RuneScape profile so
 * logged-out searches match the logged-in ones instead of assuming 99s.
 */
@Value
class RememberedLevels
{
	/** RuneScape-profile key. */
	static final String CONFIG_KEY = "levels";

	/** The remembered skills, in the saved order. */
	static final Skill[] SKILLS = {Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC,
		Skill.PRAYER, Skill.HITPOINTS, Skill.SLAYER, Skill.MINING};

	PlayerLevels levels;
	int mining;

	/** From one level per entry of {@link #SKILLS}. */
	static RememberedLevels of(int[] v)
	{
		return new RememberedLevels(new PlayerLevels(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]), v[8]);
	}

	/** Whether every level is loaded; real levels read as 0 until the client has received them. */
	boolean complete()
	{
		return all().allMatch(level -> level > 0);
	}

	String serialize()
	{
		return all().mapToObj(String::valueOf).collect(Collectors.joining(","));
	}

	/** Every level, in the saved order. */
	private IntStream all()
	{
		return IntStream.of(levels.getAttack(), levels.getStrength(), levels.getDefence(), levels.getRanged(),
			levels.getMagic(), levels.getPrayer(), levels.getHitpoints(), levels.getSlayer(), mining);
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
		RememberedLevels remembered = of(v);
		return remembered.complete() ? remembered : null;
	}
}
