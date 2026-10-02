package com.bestgearsetup.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Melee only: require a style that grants this experience (controlled grants all three).
 */
@Getter
@RequiredArgsConstructor
public enum XpPreference
{
	ANY("Any"),
	ATTACK("Attack"),
	STRENGTH("Strength"),
	DEFENCE("Defence"),
	CONTROLLED("Controlled (shared)");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
