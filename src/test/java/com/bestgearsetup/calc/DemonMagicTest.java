package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.ToLongFunction;
import org.junit.BeforeClass;
import org.junit.Test;

/** Magic against Yama with the bundled catalogue: demonic weapons and Arceuus spells must be searched. */
public class DemonMagicTest
{
	private static final Gson GSON = new Gson();
	/** Offline stand-in for live prices: every item costs 1m. */
	private static final ToLongFunction<GearItem> PRICE = item -> 1_000_000L;
	private static GameData data;
	private static Monster yama;

	@BeforeClass
	public static void loadSnapshot() throws Exception
	{
		data = WikiGameData.get(GSON).gameData(GSON);
		MonsterSummary summary = data.matchNpc(14176, null, 0);
		assertNotNull(summary);
		yama = WikiMonsters.get(GSON).monster(summary.getName());
		// Standard: the player is Yama's target, so magic setups face his +60 Magic defence.
		yama.setPhase(EncounterPhase.STANDARD);
		EncounterPhases.applyStats(yama);
	}

	private static List<SetupResult> search(boolean markOfDarkness)
	{
		CombatContext ctx = new CombatContext(yama, PlayerLevels.maxed(), false, false, TestData.piety())
			.withModifiers(CombatModifiers.builder().markOfDarkness(markOfDarkness).build());
		// An unlimited budget: charged and demonic weapons are untradeable but bought through their
		// tradable components, so they must be candidates even when not owned.
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.BUDGET).budget(Long.MAX_VALUE)
			.depth(SearchDepth.NORMAL).resultsPerClass(40)
			.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient", "arceuus"))).build();
		return new Optimizer(data, ctx, settings, id -> false, PRICE).optimize(CombatClass.MAGIC, () -> false);
	}

	@Test
	public void chargedAndDemonicStavesAreCandidatesWithoutTheUntradeableOptIn()
	{
		List<SetupResult> results = search(true);
		assertTrue(results.size() > 3);
		HashSet<String> weapons = new HashSet<>();
		for (SetupResult r : results)
		{
			weapons.add(r.getLoadout().getWeapon().getName());
		}
		for (String expected : Arrays.asList("purging staff", "tumeken's shadow", "harmonised nightmare staff"))
		{
			assertTrue(expected + " missing from " + weapons, weapons.contains(expected));
		}
	}

	@Test
	public void purgingStaffWithDarkDemonbaneLeadsWithMarkOfDarkness()
	{
		SetupResult best = search(true).get(0);
		assertEquals("purging staff", best.getLoadout().getWeapon().getName());
		assertNotNull(best.getLoadout().getSpell());
		assertEquals("dark demonbane", best.getLoadout().getSpell().getName());
	}

	@Test
	public void magicDefenceFollowsWhoTanks()
	{
		assertEquals(60, yama.getDefMagic());
		Monster partnerMelee = yama.copy();
		partnerMelee.setPhase(EncounterPhase.YAMA_MELEE_TANK);
		EncounterPhases.applyStats(partnerMelee);
		assertEquals(-30, partnerMelee.getDefMagic());
	}

	@Test
	public void waterWeaknessOutweighsDemonbaneWithoutMarkOfDarkness()
	{
		// Yama's 50% water weakness beats the bare demonbane accuracy bonus; Mark of Darkness reverses that.
		SetupResult best = search(false).get(0);
		assertNotNull(best.getLoadout().getSpell());
		assertEquals("water surge", best.getLoadout().getSpell().getName());
	}
}
