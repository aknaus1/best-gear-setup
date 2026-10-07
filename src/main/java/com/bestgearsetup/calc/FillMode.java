package com.bestgearsetup.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What to put in slots that add no DPS.
 */
@Getter
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
