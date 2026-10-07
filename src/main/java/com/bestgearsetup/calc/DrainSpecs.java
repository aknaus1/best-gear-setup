package com.bestgearsetup.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** How the stat drains of specs used during a kill (dragon warhammer, elder maul...) count towards ranking. */
@Getter
@RequiredArgsConstructor
public enum DrainSpecs
{
	/** Every hit/miss sequence weighted by its chance: the average kill. */
	EXPECTED("Expected"),
	/** Drains ignored; the spec is scored by its damage alone. */
	DAMAGE_ONLY("Ignore drain"),
	/** Worst case: every opening drain spec misses, costing its attack and leaving the target undrained. */
	ALL_MISS("Worst case");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
