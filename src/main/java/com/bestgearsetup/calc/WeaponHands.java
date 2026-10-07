package com.bestgearsetup.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Restrict weapons to one-handed or two-handed.
 */
@Getter
@RequiredArgsConstructor
public enum WeaponHands
{
	ANY("Any"),
	ONE_HANDED("One-handed only"),
	TWO_HANDED("Two-handed only");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
