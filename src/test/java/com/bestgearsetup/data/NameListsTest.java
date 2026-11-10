package com.bestgearsetup.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

public class NameListsTest
{
	private static NameLists.NameList list(String... patterns)
	{
		JsonArray array = new JsonArray();
		for (String pattern : patterns)
		{
			array.add(pattern);
		}
		return NameLists.NameList.of(array);
	}

	@Test
	public void patternsMatchExactPrefixSuffixContainsAndVersions()
	{
		NameLists.NameList names = list("sunspear", "dragon dagger*", "*sickle", "*emerald*", "magic shortbow*(i)*",
			"kraken (*)");
		assertTrue(names.matches("sunspear"));
		assertFalse(names.matches("sunspear (or)"));
		assertTrue(names.matches("dragon dagger(p++)"));
		assertFalse(names.matches("dragon"));
		assertTrue(names.matches("silver sickle"));
		assertTrue(names.matches("an emerald thing"));
		assertTrue(names.matches("magic shortbow (i)"));
		assertFalse(names.matches("magic shortbow"));
		assertTrue(names.matches("kraken"));
		assertTrue(names.matches("kraken (cave)"));
		assertFalse(names.matches("krakenling"));
		// A prefix and suffix may not share characters.
		assertFalse(list("ab*ba").matches("aba"));
		assertTrue(list("ab*ba").matches("abba"));
	}

	@Test
	public void tablesUseTheFirstMatchingPatternAndSkipNotes()
	{
		NameLists.NameTable reach = NameLists.tables("attack reach").get("bow");
		assertEquals(10, reach.get("magic longbow", 0));
		assertEquals(7, reach.get("magic shortbow", 0));
		assertEquals(5, reach.get("comp ogre bow", 0));
		assertEquals(-1, reach.get("not a bow", -1));
		assertEquals(61, NameLists.table("guardian pickaxe bonus").get("dragon pickaxe", 61));
		assertFalse(NameLists.exact("pvp repair fees").containsKey("_about"));
	}

	@Test
	public void bundledListsAreLowerCaseWithoutDuplicates() throws IOException
	{
		JsonObject root = read("src/main/resources/com/bestgearsetup/name-lists.json");
		check(root, "");
		for (JsonElement row : root.getAsJsonArray("slayer equipment"))
		{
			check(row.getAsJsonObject(), "slayer equipment.");
		}
		JsonObject specials = read("src/main/resources/com/bestgearsetup/special-attacks.json");
		for (String group : new String[]{"melee", "ranged", "magic"})
		{
			for (JsonElement row : specials.getAsJsonArray(group))
			{
				JsonObject spec = row.getAsJsonObject();
				assertNotNull(spec.get("name"));
				assertNotNull(spec.get("energy"));
				lowerCaseUnique(group + " " + spec.get("name").getAsString(), spec.getAsJsonArray("match"));
			}
		}
	}

	private static void check(JsonObject object, String path)
	{
		for (Map.Entry<String, JsonElement> entry : object.entrySet())
		{
			String key = path + entry.getKey();
			if (entry.getKey().startsWith("_") || entry.getKey().equals("slayer equipment"))
			{
				continue;
			}
			JsonElement value = entry.getValue();
			if (value.isJsonArray() && value.getAsJsonArray().size() > 0 && value.getAsJsonArray().get(0).isJsonPrimitive())
			{
				lowerCaseUnique(key, value.getAsJsonArray());
			}
			else if (value.isJsonObject())
			{
				for (Map.Entry<String, JsonElement> pattern : value.getAsJsonObject().entrySet())
				{
					if (pattern.getValue().isJsonObject())
					{
						check(value.getAsJsonObject(), key + ".");
						break;
					}
					assertEquals(key + " " + pattern.getKey(), pattern.getKey().toLowerCase(Locale.ROOT), pattern.getKey());
				}
			}
		}
	}

	private static void lowerCaseUnique(String key, JsonArray patterns)
	{
		Set<String> seen = new HashSet<>();
		for (JsonElement pattern : patterns)
		{
			String text = pattern.getAsString();
			assertEquals(key + ": names are compared in lower case", text.toLowerCase(Locale.ROOT), text);
			assertTrue(key + ": duplicate " + text, seen.add(text));
		}
	}

	/** A list name the code asks for but the JSON lacks would only fail when that class first loads. */
	@Test
	public void everyNameTheCodeAsksForIsBundled() throws IOException
	{
		JsonObject root = read("src/main/resources/com/bestgearsetup/name-lists.json");
		Pattern call = Pattern.compile("NameLists\\.(list|table|tables|exact|rows)\\(\"([^\"]+)\"\\)");
		int found = 0;
		try (Stream<Path> sources = Files.walk(Paths.get("src/main/java")))
		{
			for (Path source : (Iterable<Path>) sources.filter(p -> p.toString().endsWith(".java"))::iterator)
			{
				Matcher m = call.matcher(new String(Files.readAllBytes(source), StandardCharsets.UTF_8));
				while (m.find())
				{
					assertTrue(source + " asks for " + m.group(2), root.has(m.group(2)));
					found++;
				}
			}
		}
		assertTrue(found > 20);
	}

	private static JsonObject read(String path) throws IOException
	{
		return new JsonParser().parse(new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8))
			.getAsJsonObject();
	}
}
