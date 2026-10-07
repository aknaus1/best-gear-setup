package com.bestgearsetup.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Arceuus thrall tiers. Thralls always land a successful hit, rolling 0 to their max hit every 4 ticks, so the
 * average damage is half the max hit per 2.4 seconds.
 */
@Getter
@RequiredArgsConstructor
public enum Thrall
{
	LESSER("Lesser thrall", 38, 1),
	SUPERIOR("Superior thrall", 57, 2),
	GREATER("Greater thrall", 76, 3);

	private static final double ATTACK_SECONDS = 2.4;

	private final String displayName;
	private final int magicLevel;
	private final int maxHit;

	/** Flat DPS added to a setup. */
	public double getDps()
	{
		return maxHit / 2.0 / ATTACK_SECONDS;
	}

	/** The strongest thrall this base Magic level can cast, or null below the lesser thrall's level. */
	public static Thrall forLevel(int magic)
	{
		Thrall best = null;
		for (Thrall thrall : values())
		{
			if (magic >= thrall.magicLevel)
			{
				best = thrall;
			}
		}
		return best;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
