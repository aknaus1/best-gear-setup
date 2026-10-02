package com.bestgearsetup.data;

import com.google.gson.Gson;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Game data from the bundled OSRS Wiki snapshots. Nothing is downloaded; the first call parses the
 * bundles, so call it off the client thread.
 */
@Singleton
public class GameDataLoader
{
	private final Gson gson;

	@Inject
	public GameDataLoader(Gson gson)
	{
		this.gson = gson;
	}

	/** Monsters, equipment, spells, prayers and potions. */
	public GameData loadGameData() throws IOException
	{
		return WikiGameData.get(gson).gameData(gson);
	}

	/** Full stats for the selected variant, as a copy the caller may adjust. */
	public Monster getMonster(MonsterSummary summary) throws IOException
	{
		Monster monster = WikiMonsters.get(gson).monster(summary.getName());
		if (monster == null)
		{
			throw new IOException("No bundled Wiki data for " + summary.getName());
		}
		return monster;
	}

	/** Where the data comes from, for the search notes. */
	public String describeSource()
	{
		try
		{
			return "Game data: bundled OSRS Wiki snapshots (equipment " + WikiGameData.get(gson).getRetrieved()
				+ ", monsters " + WikiMonsters.get(gson).getRetrieved() + ").";
		}
		catch (IOException e)
		{
			return "Game data: bundled OSRS Wiki snapshots.";
		}
	}
}
