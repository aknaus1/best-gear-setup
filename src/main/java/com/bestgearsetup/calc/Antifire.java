package com.bestgearsetup.calc;

import lombok.RequiredArgsConstructor;

/** Active potion assumption; extended potions have identical protection. */
@RequiredArgsConstructor
public enum Antifire
{
	NONE("None"),
	REGULAR("Antifire"),
	SUPER("Super antifire");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
