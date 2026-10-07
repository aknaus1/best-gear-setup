package com.bestgearsetup.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Special attack energy available for each kill when specs are used during the fight. */
@Getter
@RequiredArgsConstructor
public enum SpecEnergy
{
	/** Steady state over a trip: only the energy regenerated during the kill. */
	REGENERATION("Regenerating"),
	/** Each kill starts with a full bar, then regenerates. */
	FULL_BAR("Full bar");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
