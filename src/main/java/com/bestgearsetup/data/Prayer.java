package com.bestgearsetup.data;

import lombok.Data;

/**
 * An offensive prayer entry from the bundled data. {@code boost} is the accuracy
 * (or level) boost in percent; {@code strengthBoost} is the damage boost for ranged and magic.
 */
@Data
public class Prayer
{
	private String name;
	private String prayerbook;
	private double boost;
	private double strengthBoost;
	private int prayerLevel = 1;
	private int defenceLevel = 1;

	@Override
	public String toString()
	{
		return GameData.titleCase(name == null ? "" : name);
	}
}
