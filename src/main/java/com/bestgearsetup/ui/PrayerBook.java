package com.bestgearsetup.ui;

import com.bestgearsetup.data.Names;
import com.google.gson.JsonArray;
import java.util.*;

/** Every standard prayer in prayer book order, with its icon sprite and Prayer level, from prayer-book.json. */
final class PrayerBook
{
	/** Lower-case name to its icon sprite, in prayer book order. */
	static final Map<String, Integer> SPRITES = new LinkedHashMap<>();
	static final Map<String, Integer> LEVELS = new HashMap<>();

	static
	{
		Names.load("/com/bestgearsetup/prayer-book.json").getAsJsonObject("prayers").entrySet().forEach(e ->
		{
			JsonArray values = e.getValue().getAsJsonArray();
			SPRITES.put(e.getKey(), values.get(0).getAsInt());
			LEVELS.put(e.getKey(), values.get(1).getAsInt());
		});
	}

	private PrayerBook()
	{
	}
}
