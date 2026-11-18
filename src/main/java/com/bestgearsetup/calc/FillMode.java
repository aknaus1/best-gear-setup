package com.bestgearsetup.calc;

import lombok.RequiredArgsConstructor;

/**
 * What to put in slots that add no DPS.
 */
@RequiredArgsConstructor
public enum FillMode
{
	NONE("Leave empty"),
	PRAYER("Prayer bonus"),
	DEFENCE("Defence");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
