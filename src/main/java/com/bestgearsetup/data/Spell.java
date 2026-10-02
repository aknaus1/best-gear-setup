package com.bestgearsetup.data;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;

/**
 * A combat spell. {@code spellWeapons} lists the weapon ids that can autocast it.
 */
@Data
public class Spell
{
	private String name;
	private String spellbook;
	@SerializedName("max")
	private int maxHit;
	private int level;
	@SerializedName("attack_speed")
	private int attackSpeed = 5;
	@SerializedName("spell_weapons")
	private List<String> spellWeapons;

	private transient Set<Integer> weaponIds;

	public boolean castableWith(int weaponId)
	{
		if (weaponIds == null)
		{
			Set<Integer> ids = new HashSet<>();
			for (String s : spellWeapons == null ? Collections.<String>emptyList() : spellWeapons)
			{
				try
				{
					ids.add(Integer.parseInt(s.trim()));
				}
				catch (NumberFormatException ignored)
				{
					// skip malformed ids
				}
			}
			weaponIds = ids;
		}
		return weaponIds.contains(weaponId);
	}

	public String getName()
	{
		return name == null ? "" : name;
	}

	@Override
	public String toString()
	{
		return GameData.titleCase(getName());
	}
}
