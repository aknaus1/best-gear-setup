package com.bestgearsetup;

import java.util.*;
import lombok.Value;

/** A snapshot supplied by Dude, Where's My Stuff?, kept separate from live carried containers. */
@Value
public class StoredLocation
{
	String category;
	String name;
	Map<Integer, Long> items;

	StoredLocation(String category, String name, Map<Integer, Long> items)
	{
		this.category = category;
		this.name = name;
		this.items = Collections.unmodifiableMap(new HashMap<>(items));
	}

	public String displayName()
	{
		return ("poh".equals(category) ? "POH: " : "stash".equals(category) ? "STASH: " : "") + name;
	}
}
