package com.bestgearsetup.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Slot
{
	HEAD("head", "Head"),
	CAPE("cape", "Cape"),
	NECK("neck", "Neck"),
	AMMO("ammunition", "Ammo"),
	WEAPON("weapon", "Weapon"),
	BODY("body", "Body"),
	SHIELD("shield", "Shield"),
	LEGS("legs", "Legs"),
	HANDS("hands", "Hands"),
	FEET("feet", "Feet"),
	RING("ring", "Ring");

	/** Slot key used in the bundled JSON schema. */
	private final String apiKey;
	private final String displayName;

	public static Slot fromApiKey(String key)
	{
		for (Slot s : values())
		{
			if (s.apiKey.equals(key))
			{
				return s;
			}
		}
		return null;
	}
}
