package com.bestgearsetup.ui;

import com.bestgearsetup.data.Names;
import com.google.gson.*;
import java.awt.Color;
import java.util.*;
import lombok.RequiredArgsConstructor;

/**
 * The panel's look, chosen with the Theme setting. Each theme is a bundled JSON file,
 * {@code /com/bestgearsetup/themes/<name>.json}, naming a painter for each kind of button, a frame for cards and
 * boxes, and their colours; any key a theme leaves out is read from Classic. Custom is the player's own, saved in
 * the hidden Custom theme setting and edited, exported and imported in Settings.
 */
@RequiredArgsConstructor
public enum Theme
{
	CLASSIC("Classic"),
	FORGED("Forged"),
	EDGE("Edge"),
	RUNITE("Runite"),
	DRAGON("Dragon"),
	ANCIENT("Ancient"),
	MIDNIGHT("Midnight"),
	CRYSTAL("Crystal"),
	WOODCUT("Woodcut"),
	INFERNO("Inferno"),
	ARCANE("Arcane"),
	SARADOMIN("Saradomin"),
	ZAMORAK("Zamorak"),
	GUTHIX("Guthix"),
	ARMADYL("Armadyl"),
	BANDOS("Bandos"),
	BARROWS("Barrows"),
	RETRO("Retro"),
	DESERT("Desert"),
	VOID("Void"),
	FROST("Frost"),
	VENOM("Venom"),
	GILDED("Gilded"),
	CUSTOM("Custom");

	/** The Custom theme editor's data: each non-colour key's allowed values, labels and colour groups. */
	static final JsonObject EDITOR = Names.load("/com/bestgearsetup/theme-editor.json");
	static final JsonObject CHOICES = EDITOR.getAsJsonObject("choices");
	/** Longer than any real theme, short enough that a pasted mistake can't fill the config. */
	private static final int MAX_JSON = 20_000;

	private static Theme current = CLASSIC;

	private final String displayName;
	private final Map<String, Color> colors = new HashMap<>();
	private JsonObject data;

	static Theme current()
	{
		return current;
	}

	/** Switches the look; painted parts follow at their next repaint, styled ones through {@link Controls#restyle}. */
	public static void use(Theme theme)
	{
		current = theme == null ? CLASSIC : theme;
	}

	/** Loads the saved Custom theme, keeping only known keys with valid values so painting can't fail on it. */
	public static void setCustom(String json)
	{
		JsonObject clean = new JsonObject();
		JsonObject parsed = parse(json);
		if (parsed != null)
		{
			for (Map.Entry<String, JsonElement> entry : parsed.entrySet())
			{
				if (problem(entry.getKey(), entry.getValue()) == null && CLASSIC.data().has(entry.getKey()))
				{
					clean.add(entry.getKey(), entry.getValue());
				}
			}
		}
		CUSTOM.data = clean;
		CUSTOM.colors.clear();
	}

	/**
	 * Why {@code json} can't be imported as a theme, or null if it can. Keys this version doesn't know are ignored,
	 * but it must have one it knows, so other JSON can't pass for a theme.
	 */
	static String check(String json)
	{
		JsonObject parsed = parse(json);
		String problem = "That isn't a theme: paste the text Export copies.";
		for (Map.Entry<String, JsonElement> entry : (parsed == null ? new JsonObject() : parsed).entrySet())
		{
			if (CLASSIC.data().has(entry.getKey()))
			{
				String wrong = problem(entry.getKey(), entry.getValue());
				if (wrong != null)
				{
					return wrong;
				}
				problem = null;
			}
		}
		return problem;
	}

	/** The theme as JSON, listing only what differs from Classic, one key per line. */
	static String export(Theme theme)
	{
		StringBuilder out = new StringBuilder("{\n  \"_comment\": \"Best Gear Setup theme, based on Classic\"");
		for (Map.Entry<String, JsonElement> entry : CLASSIC.data().entrySet())
		{
			String key = entry.getKey();
			JsonElement value = theme.value(key);
			if (!key.equals("_comment") && (theme == CLASSIC || !value.equals(entry.getValue())))
			{
				out.append(",\n  \"").append(key).append("\": \"").append(value.getAsString()).append('"');
			}
		}
		return out.append("\n}\n").toString();
	}

	/** The painter or frame this theme names for {@code key}, e.g. "flat" for "primary". */
	static String style(String key)
	{
		return current.value(key).getAsString();
	}

	static Color color(String key)
	{
		return current.colors.computeIfAbsent(key,
			k -> new Color(Integer.parseInt(current.value(k).getAsString().substring(1), 16)));
	}

	JsonObject data()
	{
		if (data == null)
		{
			data = this == CUSTOM ? new JsonObject()
				: Names.load("/com/bestgearsetup/themes/" + name().toLowerCase(Locale.ROOT) + ".json");
		}
		return data;
	}

	/** This theme's value for {@code key}, or Classic's when it leaves the key out. */
	String get(String key)
	{
		return value(key).getAsString();
	}

	/** What's wrong with one known key's value, or null. */
	private static String problem(String key, JsonElement value)
	{
		if (key.equals("_comment"))
		{
			return null;
		}
		String text = value != null && value.isJsonPrimitive() ? value.getAsString() : "";
		String kind = CLASSIC.data().has(key) ? CLASSIC.data().get(key).getAsString() : "";
		if (kind.startsWith("#"))
		{
			return text.matches("#[0-9A-Fa-f]{6}") ? null : key + " must be a colour like #DC8A00";
		}
		JsonArray allowed = CHOICES.getAsJsonArray(key);
		if (allowed == null || allowed.contains(new JsonPrimitive(text)))
		{
			return null;
		}
		List<String> names = new ArrayList<>();
		allowed.forEach(choice -> names.add(choice.getAsString()));
		return key + " must be one of " + String.join(", ", names);
	}

	private static JsonObject parse(String json)
	{
		if (json == null || json.length() > MAX_JSON)
		{
			return null;
		}
		try
		{
			JsonElement parsed = new JsonParser().parse(json);
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
		}
		catch (JsonParseException | IllegalStateException e)
		{
			return null;
		}
	}

	private JsonElement value(String key)
	{
		JsonElement value = data().get(key);
		if (value != null)
		{
			return value;
		}
		if (this == CLASSIC)
		{
			throw new IllegalArgumentException("Unknown theme key " + key);
		}
		return CLASSIC.value(key);
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
