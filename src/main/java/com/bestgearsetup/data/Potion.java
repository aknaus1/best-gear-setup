package com.bestgearsetup.data;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import lombok.Data;

/**
 * A stat boost for one skill: level + base + floor(level * percent / 100).
 */
@Data
public class Potion
{
	private int id;
	private String name;
	@SerializedName("base_increase")
	private int baseIncrease;
	@SerializedName("percentage_increase")
	private int percentageIncrease;
	private String skill;
	/** e.g. "xerician" (Chambers of Xeric), "tombs of amascut", "nmz", "deadman". */
	private List<String> attributes;

	public List<String> getAttributes()
	{
		return attributes == null ? Collections.emptyList() : attributes;
	}

	public String getName()
	{
		return name == null ? "" : name;
	}

	public int boost(int level)
	{
		return Math.max(0, level + baseIncrease + level * percentageIncrease / 100);
	}

	/**
	 * Eligible for "best available": usable anywhere (not raid, Nightmare Zone or Deadman only), and not a
	 * Zamorak / Saradomin brew, which boost on paper but drain other stats and are rarely a combat choice.
	 */
	public boolean isUnrestricted()
	{
		String n = getName().toLowerCase(java.util.Locale.ROOT);
		return getAttributes().isEmpty() && !n.equals("zamorak brew") && !n.equals("saradomin brew");
	}
}
