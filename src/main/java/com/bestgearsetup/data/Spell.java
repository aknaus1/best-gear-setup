package com.bestgearsetup.data;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

	/** Built on first use; volatile so searches on several threads see the whole set. */
	private transient volatile Set<Integer> weaponIds;
	/** Derived from the name; cleared by {@link #setName}. Transient keeps it out of Gson and equals. */
	private transient String lowerName;

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

	public void setName(String name)
	{
		this.name = name;
		lowerName = null;
	}

	/** {@link #getName()} in lower case; damage rules match it for every setup the search scores. */
	public String getLowerName()
	{
		String lower = lowerName;
		if (lower == null)
		{
			lower = getName().toLowerCase(Locale.ROOT);
			lowerName = lower;
		}
		return lower;
	}

	@Override
	public String toString()
	{
		return GameData.titleCase(getName());
	}
}
