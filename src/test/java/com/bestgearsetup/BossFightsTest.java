package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.Names;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/** The full-fight catalog: every phase names a real monster and uses only the documented fields. */
public class BossFightsTest
{
	private static final Set<String> FIGHT_FIELDS = new HashSet<>(Arrays.asList("phases", "note"));
	private static final Set<String> PHASE_FIELDS = new HashSet<>(Arrays.asList("target", "label", "share", "count", "phase", "styles", "hp", "split", "bar"));
	private static JsonObject fights;
	private static WikiMonsters monsters;

	@BeforeClass
	public static void load() throws Exception
	{
		fights = Names.load("/com/bestgearsetup/boss-fights.json");
		monsters = WikiMonsters.get(RuneLiteAPI.GSON);
	}

	private static double share(JsonElement value)
	{
		String text = value.getAsString();
		int slash = text.indexOf('/');
		if (slash < 0)
		{
			return Double.parseDouble(text);
		}
		return Double.parseDouble(text.substring(0, slash)) / Double.parseDouble(text.substring(slash + 1));
	}

	@Test
	public void everyPhaseIsWellFormed()
	{
		for (Map.Entry<String, JsonElement> fight : fights.entrySet())
		{
			if (fight.getKey().startsWith("_"))
			{
				continue;
			}
			String key = fight.getKey();
			assertEquals("Fight keys are lower-case: " + key, key.toLowerCase(), key);
			JsonObject body = fight.getValue().getAsJsonObject();
			assertTrue(key + " has an unknown field", FIGHT_FIELDS.containsAll(body.keySet()));
			JsonArray phases = body.getAsJsonArray("phases");
			assertNotNull(key + " has no phases", phases);
			assertTrue(key + " needs at least two phases", phases.size() >= 2);
			for (JsonElement element : phases)
			{
				JsonObject phase = element.getAsJsonObject();
				assertTrue(key + " phase has an unknown field: " + phase, PHASE_FIELDS.containsAll(phase.keySet()));
				String target = phase.get("target").getAsString();
				assertNotNull(key + ": no bundled monster " + target, monsters.monster(target));
				if (phase.has("phase"))
				{
					EncounterPhase.valueOf(phase.get("phase").getAsString());
				}
				if (phase.has("share"))
				{
					double share = share(phase.get("share"));
					assertTrue(key + " share out of (0, 1]: " + phase, share > 0 && share <= 1);
				}
				if (phase.has("count"))
				{
					assertTrue(key + " count below 1: " + phase, phase.get("count").getAsInt() >= 1);
				}
				if (phase.has("hp") && phase.get("hp").isJsonPrimitive())
				{
					assertTrue(key + " hp not positive: " + phase, phase.get("hp").getAsDouble() > 0);
				}
				else if (phase.has("hp"))
				{
					JsonObject hp = phase.getAsJsonObject("hp");
					assertTrue(key + " hp formula has an unknown field: " + hp,
						new HashSet<>(Arrays.asList("base", "per", "from", "cap")).containsAll(hp.keySet()));
					double base = hp.get("base").getAsDouble();
					assertTrue(key + " hp base not positive", base > 0);
					assertTrue(key + " hp per below 0", !hp.has("per") || hp.get("per").getAsDouble() >= 0);
					assertTrue(key + " hp from below 1", !hp.has("from") || hp.get("from").getAsDouble() >= 1);
					assertTrue(key + " hp cap below base", !hp.has("cap") || hp.get("cap").getAsDouble() >= base);
				}
				if (phase.has("split"))
				{
					assertTrue(key + " split isn't a boolean", phase.get("split").getAsJsonPrimitive().isBoolean());
				}
				if (phase.has("styles"))
				{
					for (com.google.gson.JsonElement style : phase.getAsJsonArray("styles"))
					{
						com.bestgearsetup.data.Combat.valueOf(style.getAsString().toUpperCase());
					}
				}
			}
		}
	}

	@Test
	public void eachTargetBelongsToOneFight()
	{
		Map<String, String> owner = new HashMap<>();
		for (Map.Entry<String, JsonElement> fight : fights.entrySet())
		{
			if (fight.getKey().startsWith("_"))
			{
				continue;
			}
			for (JsonElement phase : fight.getValue().getAsJsonObject().getAsJsonArray("phases"))
			{
				String target = phase.getAsJsonObject().get("target").getAsString();
				String previous = owner.put(target, fight.getKey());
				assertTrue(target + " is in both " + previous + " and " + fight.getKey(),
					previous == null || previous.equals(fight.getKey()));
			}
		}
	}

	/** The planner reads the catalog as written: every target leads back to its fight, with its phases in order. */
	@Test
	public void plannerReadsEveryFight()
	{
		for (Map.Entry<String, JsonElement> fight : fights.entrySet())
		{
			if (fight.getKey().startsWith("_"))
			{
				continue;
			}
			JsonArray rows = fight.getValue().getAsJsonObject().getAsJsonArray("phases");
			List<FightPlan.Phase> phases = FightPlan.phases(fight.getKey());
			assertEquals(fight.getKey(), rows.size(), phases.size());
			for (int i = 0; i < rows.size(); i++)
			{
				JsonObject row = rows.get(i).getAsJsonObject();
				FightPlan.Phase phase = phases.get(i);
				assertEquals(row.get("target").getAsString(), phase.getTarget());
				assertEquals(phase.getTarget(), fight.getKey(), FightPlan.fight(phase.getTarget()));
				assertTrue(phase.getTarget() + " has no work", phase.getWork() > 0);
				assertEquals(phase.getTarget(), row.has("styles") ? row.getAsJsonArray("styles").size() : 0,
					phase.getStyles().size());
			}
		}
	}

	/** Forms sharing a health bar share bar 0, several targets get no drain, and a separate target has its own bar. */
	@Test
	public void healthBarsFollowShareAndCount()
	{
		FightPlan.phases("zulrah").forEach(phase ->
		{
			assertEquals(phase.getLabel(), "zulrah", phase.getBar());
			assertTrue(phase.isShared());
		});
		List<FightPlan.Phase> vetion = FightPlan.phases("vet'ion");
		assertEquals(null, vetion.get(1).getBar());
		assertEquals(2, vetion.get(1).getCount(), 0);
		assertNotNull(vetion.get(0).getBar());
		assertTrue("he is reborn undrained", !vetion.get(0).getBar().equals(vetion.get(2).getBar()));
		assertTrue(!vetion.get(0).isShared());
		assertEquals(1, vetion.get(0).getCount(), 0);
		// A shared bar is named by its fight, so another fight's shared bar is never the same one.
		assertTrue(!FightPlan.phases("alchemical hydra").get(0).getBar().equals(FightPlan.phases("zulrah").get(0).getBar()));
	}

	/** Each phase target is matched to the slayer task on its own: the Royal Titans are two different giants. */
	@Test
	public void eachPhaseTargetIsMatchedToTheTask()
	{
		AccountFacts.TaskRecord fireGiants = new AccountFacts.TaskRecord("fire giants", 10, false, null);
		Map<String, Boolean> tasks = BestGearSetupPlugin.phaseTasks("eldric the ice king", AutoState.AUTO,
			name -> AccountFacts.matchesAssignment(fireGiants, name));
		assertEquals(Boolean.TRUE, tasks.get("branda the fire queen"));
		assertEquals(Boolean.FALSE, tasks.get("eldric the ice king"));
		assertEquals("the Fight override applies to every phase", Boolean.TRUE, BestGearSetupPlugin.phaseTasks(
			"eldric the ice king", AutoState.ON, name -> false).get("eldric the ice king"));
		assertTrue(BestGearSetupPlugin.phaseTasks("abyssal demon", AutoState.AUTO, name -> true).isEmpty());
	}
}
