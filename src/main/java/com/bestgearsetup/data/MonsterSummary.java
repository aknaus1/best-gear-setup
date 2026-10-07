package com.bestgearsetup.data;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import lombok.Data;

/**
 * Entry in the bundled monster list. The id is an in-game NPC id.
 */
@Data
public class MonsterSummary
{
	private int id;
	private String name;
	@SerializedName("level_cb")
	private int combatLevel;
	private boolean boss;
	private List<String> attributes;
	/** Every NPC id of this variant; a single-id entry may list only {@link #id}. */
	private List<Integer> ids;

	public List<Integer> getIds()
	{
		return ids == null || ids.isEmpty() ? Collections.singletonList(id) : ids;
	}

	public List<String> getAttributes()
	{
		return attributes == null ? Collections.emptyList() : attributes;
	}

	public String getName()
	{
		return name == null ? "" : name;
	}

	/** Name with each word capitalised, for display. */
	public String getDisplayName()
	{
		return GameData.titleCase(getName());
	}

	@Override
	public String toString()
	{
		return getDisplayName() + " (lvl " + combatLevel + ")";
	}
}
