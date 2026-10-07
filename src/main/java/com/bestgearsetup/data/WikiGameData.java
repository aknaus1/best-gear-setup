package com.bestgearsetup.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Equipment, weapons, spells, prayers and potions from the bundled OSRS Wiki snapshot
 * (tools/wiki_equipment.py), in the plugin's JSON schema. Nothing is downloaded at runtime. Wear
 * requirements are already combined from cache parameters, Wiki text and the reviewed rule table.
 */
public final class WikiGameData
{
	private static final String RESOURCE = "/com/bestgearsetup/wiki-gamedata.json.gz";
	private static volatile WikiGameData instance;

	private final GameData equipment;
	private final String retrieved;

	private WikiGameData(GameData equipment, String retrieved)
	{
		this.equipment = equipment;
		this.retrieved = retrieved;
	}

	/** The catalogue if something has already loaded it, otherwise null; never loads. */
	public static WikiGameData loaded()
	{
		return instance;
	}

	public static WikiGameData get(Gson gson) throws IOException
	{
		WikiGameData loaded = instance;
		if (loaded == null)
		{
			synchronized (WikiGameData.class)
			{
				loaded = instance;
				if (loaded == null)
				{
					loaded = load(gson);
					instance = loaded;
				}
			}
		}
		return loaded;
	}

	private static WikiGameData load(Gson gson) throws IOException
	{
		InputStream in = WikiGameData.class.getResourceAsStream(RESOURCE);
		if (in == null)
		{
			throw new IOException("Bundled Wiki equipment data is missing");
		}
		try (Reader reader = new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8))
		{
			JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
			String stamp = root.has("retrievedUtc") ? root.get("retrievedUtc").getAsString() : "";
			JsonObject monsters = new JsonObject();
			monsters.add("monsters", new JsonArray());
			GameData data = GameDataJson.parse(gson, monsters, root, root, root, root, root);
			return new WikiGameData(data, stamp.length() >= 10 ? stamp.substring(0, 10) : "unknown date");
		}
		catch (RuntimeException e)
		{
			throw new IOException("Bundled Wiki equipment data is unreadable", e);
		}
	}

	/** The bundled equipment with the bundled Wiki monster list. */
	public GameData gameData(Gson gson) throws IOException
	{
		return equipment.withMonsters(gson, WikiMonsters.get(gson).getSummaries());
	}

	/** The shared bundled equipment catalogue, without monsters; callers must not modify it. */
	public GameData getEquipment()
	{
		return equipment;
	}

	/** Snapshot date (yyyy-mm-dd). */
	public String getRetrieved()
	{
		return retrieved;
	}
}
