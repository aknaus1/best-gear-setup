package com.bestgearsetup.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What the optimiser maximises. DPS breaks ties for the other modes.
 */
@Getter
@RequiredArgsConstructor
public enum CalcMode
{
	DPS("DPS"),
	ACCURACY("Accuracy"),
	MAX_HIT("Max hit"),
	AVERAGE_HIT("Average hit");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
