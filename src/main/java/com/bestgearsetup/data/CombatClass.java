package com.bestgearsetup.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CombatClass
{
	MELEE("Melee"),
	RANGED("Ranged"),
	MAGIC("Magic");

	private final String displayName;
}
