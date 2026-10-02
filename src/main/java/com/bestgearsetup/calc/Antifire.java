package com.bestgearsetup.calc;

/** Active potion assumption; extended potions have identical protection. */
public enum Antifire
{
	NONE("None"),
	REGULAR("Antifire"),
	SUPER("Super antifire");

	private final String label;

	Antifire(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
