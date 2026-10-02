package com.bestgearsetup.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Which defence bonus to maximise when filling slots.
 */
@Getter
@RequiredArgsConstructor
public enum DefenceFocus
{
	TARGET("Target's attack styles"),
	STAB("Stab"),
	SLASH("Slash"),
	CRUSH("Crush"),
	MELEE("All melee"),
	MAGIC("Magic"),
	RANGED("Ranged"),
	TOTAL("Total");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
