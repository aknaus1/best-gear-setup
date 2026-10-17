package com.bestgearsetup;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Value;

/** A snapshot supplied by Dude, Where's My Stuff?, kept separate from live carried containers. */
@Value
public class StoredLocation
{
	String category;
	String name;
	long lastUpdated;
	Map<Integer, Long> items;

	StoredLocation(String category, String name, long lastUpdated, Map<Integer, Long> items)
	{
		this.category = category;
		this.name = name;
		this.lastUpdated = lastUpdated;
		this.items = Collections.unmodifiableMap(new HashMap<>(items));
	}

	public String displayName()
	{
		return ("poh".equals(category) ? "POH: " : "stash".equals(category) ? "STASH: " : "") + name;
	}
}
