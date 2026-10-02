package com.bestgearsetup.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Weapon poison assumed on poisonable daggers, spears, hastae and metal ammunition. The bundled data lists
 * only unpoisoned items, so this option stands in for the (p), (p+) and (p++) variants.
 */
@Getter
@RequiredArgsConstructor
public enum WeaponPoison
{
	NONE("None", 0),
	POISON("Weapon poison (p)", 20),
	POISON_PLUS("Weapon poison (p+)", 25),
	POISON_PLUS_PLUS("Weapon poison (p++)", 30);

	private final String displayName;
	/** Poison severity; ranged weapons apply it 14 lower. */
	private final int severity;

	@Override
	public String toString()
	{
		return displayName;
	}
}
