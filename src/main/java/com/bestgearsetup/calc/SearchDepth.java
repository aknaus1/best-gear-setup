package com.bestgearsetup.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Speed / thoroughness trade-off for the optimiser.
 */
@Getter
@RequiredArgsConstructor
public enum SearchDepth
{
	FAST("Fast"),
	NORMAL("Normal"),
	BEST("Best (slowest)");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
