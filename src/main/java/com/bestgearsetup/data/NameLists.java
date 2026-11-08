package com.bestgearsetup.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Item and monster name lists and name-keyed values bundled in name-lists.json, matched against lower-case names.
 * A pattern is an exact name, or uses {@code *} for any text ({@code "dragon dagger*"}, {@code "*sickle*"});
 * {@code "kraken (*)"} matches "kraken" alone or with a version, as in "kraken (cave)".
 */
public final class NameLists
{
	/** Parsed on first use without Gson: classes read their lists at class load, before the client's Gson exists. */
	private static final class Lists
	{
		static final JsonObject DATA = load("/com/bestgearsetup/name-lists.json");
	}

	private NameLists()
	{
	}

	/** Bundled JSON resource, parsed once. Missing or malformed data fails loudly at class load. */
	public static JsonObject load(String resource)
	{
		InputStream in = NameLists.class.getResourceAsStream(resource);
		if (in == null)
		{
			throw new IllegalStateException("Bundled data is missing: " + resource);
		}
		try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			return new JsonParser().parse(reader).getAsJsonObject();
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Cannot read bundled data " + resource, e);
		}
	}

	/** The named list; an unknown name fails at once, so a typo can't silently match nothing. */
	public static NameList list(String key)
	{
		JsonElement list = Lists.DATA.get(key);
		if (list == null || !list.isJsonArray())
		{
			throw new IllegalArgumentException("Unknown name list " + key);
		}
		return NameList.of(list);
	}

	/** Structured rows bundled under this name, such as "slayer equipment". */
	public static JsonArray rows(String key)
	{
		JsonElement rows = Lists.DATA.get(key);
		if (rows == null || !rows.isJsonArray())
		{
			throw new IllegalArgumentException("Unknown rows " + key);
		}
		return rows.getAsJsonArray();
	}

	/** Whole numbers keyed by exact name, for lookups too frequent for a pattern table. */
	public static Map<String, Integer> exact(String key)
	{
		Map<String, Integer> values = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : object(key).entrySet())
		{
			if (!entry.getKey().startsWith("_"))
			{
				values.put(entry.getKey(), entry.getValue().getAsInt());
			}
		}
		return Collections.unmodifiableMap(values);
	}

	/** The named table of pattern to whole number, tried in order. */
	public static NameTable table(String key)
	{
		return table(object(key), key);
	}

	/** A group of tables under one name, keyed like "attack reach" by weapon subcategory. */
	public static Map<String, NameTable> tables(String key)
	{
		Map<String, NameTable> tables = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : object(key).entrySet())
		{
			if (entry.getValue().isJsonObject())
			{
				tables.put(entry.getKey(), table(entry.getValue().getAsJsonObject(), key + "." + entry.getKey()));
			}
		}
		return tables;
	}

	private static JsonObject object(String key)
	{
		JsonElement table = Lists.DATA.get(key);
		if (table == null || !table.isJsonObject())
		{
			throw new IllegalArgumentException("Unknown name table " + key);
		}
		return table.getAsJsonObject();
	}

	private static NameTable table(JsonObject table, String key)
	{
		List<NameList> patterns = new ArrayList<>();
		List<Integer> values = new ArrayList<>();
		for (Map.Entry<String, JsonElement> entry : table.entrySet())
		{
			if (!entry.getKey().startsWith("_"))
			{
				patterns.add(NameList.of(entry.getKey()));
				values.add(entry.getValue().getAsInt());
			}
		}
		return new NameTable(patterns, values);
	}

	/** Names matched by exact name or by pattern. */
	public static final class NameList
	{
		private final Set<String> exact = new HashSet<>();
		private final List<String[]> globs = new ArrayList<>();

		public static NameList of(JsonElement patterns)
		{
			NameList list = new NameList();
			for (JsonElement pattern : patterns.getAsJsonArray())
			{
				list.add(pattern.getAsString());
			}
			return list;
		}

		static NameList of(String pattern)
		{
			NameList list = new NameList();
			list.add(pattern);
			return list;
		}

		private void add(String pattern)
		{
			if (pattern.endsWith(" (*)"))
			{
				String base = pattern.substring(0, pattern.length() - 4);
				exact.add(base);
				globs.add((base + " (*").split("\\*", -1));
			}
			else if (pattern.indexOf('*') < 0)
			{
				exact.add(pattern);
			}
			else
			{
				globs.add(pattern.split("\\*", -1));
			}
		}

		public boolean matches(String name)
		{
			if (exact.contains(name))
			{
				return true;
			}
			for (String[] parts : globs)
			{
				if (glob(parts, name))
				{
					return true;
				}
			}
			return false;
		}

		/** Parts between the stars, in order: the first anchors the start and the last the end. */
		private static boolean glob(String[] parts, String name)
		{
			int last = parts.length - 1;
			if (!name.startsWith(parts[0]) || name.length() < parts[0].length() + parts[last].length()
				|| !name.endsWith(parts[last]))
			{
				return false;
			}
			int at = parts[0].length();
			int end = name.length() - parts[last].length();
			for (int i = 1; i < last; i++)
			{
				int found = name.indexOf(parts[i], at);
				if (found < 0 || found + parts[i].length() > end)
				{
					return false;
				}
				at = found + parts[i].length();
			}
			return true;
		}
	}

	/** Whole numbers keyed by name pattern; the first matching pattern gives the value. */
	public static final class NameTable
	{
		private final List<NameList> patterns;
		private final List<Integer> values;

		private NameTable(List<NameList> patterns, List<Integer> values)
		{
			this.patterns = patterns;
			this.values = values;
		}

		public int get(String name, int fallback)
		{
			for (int i = 0; i < patterns.size(); i++)
			{
				if (patterns.get(i).matches(name))
				{
					return values.get(i);
				}
			}
			return fallback;
		}
	}
}
