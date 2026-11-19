package com.bestgearsetup.data;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
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

	/** The snapshot as written by the tool: equipment by slot, weapons, spells, prayers and potions. */
	private static final class Snapshot
	{
		private String retrievedUtc;
		private Map<String, List<GearItem>> equipment;
		private List<GearItem> weapons;
		private List<Spell> spells;
		private Map<String, List<Prayer>> prayers;
		private Map<String, List<Potion>> potions;
	}

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
		return read(gson, RESOURCE, Snapshot.class, "equipment", root -> new WikiGameData(new GameData(
			Collections.emptyList(), root.equipment, root.weapons, root.spells, root.prayers, root.potions),
			date(root.retrievedUtc)));
	}

	/**
	 * Builds from a bundled gzipped snapshot read with the client's Gson; {@code what} names the snapshot in errors. A
	 * missing snapshot, or one that can't be read or built from, is an IOException.
	 */
	static <S, T> T read(Gson gson, String resource, Class<S> type, String what, Function<S, T> build) throws IOException
	{
		InputStream in = WikiGameData.class.getResourceAsStream(resource);
		if (in == null)
		{
			throw new IOException("Bundled Wiki " + what + " data is missing");
		}
		try (Reader reader = new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8))
		{
			return build.apply(gson.fromJson(reader, type));
		}
		catch (RuntimeException e)
		{
			throw new IOException("Bundled Wiki " + what + " data is unreadable", e);
		}
	}

	/** The snapshot date (yyyy-mm-dd) of a retrieval time. */
	static String date(String retrievedUtc)
	{
		return retrievedUtc != null && retrievedUtc.length() >= 10 ? retrievedUtc.substring(0, 10) : "unknown date";
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
