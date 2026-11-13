package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Spell;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

/** A weaker tier of the same element or ancient school is left out of the search without changing its answer. */
public class SpellPruningTest
{
	private static final Gson GSON = RuneLiteAPI.GSON;
	private static GameData data;

	@BeforeClass
	public static void loadSnapshot() throws Exception
	{
		data = WikiGameData.get(GSON).gameData(GSON);
	}

	@After
	public void restore()
	{
		Optimizer.pruneSpells = true;
	}

	private static Spell spell(String name)
	{
		for (Spell spell : data.getSpells())
		{
			if (spell.getName().equalsIgnoreCase(name))
			{
				return spell;
			}
		}
		throw new AssertionError(name);
	}

	private static boolean outclasses(String strong, String weak, boolean twinflame, boolean gauntlets, boolean area)
	{
		return SpellRules.outclasses(spell(strong), spell(weak), 99, twinflame, gauntlets, area);
	}

	@Test
	public void aHigherTierOfTheSameElementOutclassesALowerOne()
	{
		assertTrue(outclasses("fire surge", "fire wave", false, false, false));
		assertTrue(outclasses("wind surge", "wind strike", false, false, false));
		assertTrue(outclasses("ice barrage", "ice blitz", false, false, false));
		assertFalse(outclasses("fire wave", "fire surge", false, false, false));
		// Another element may meet a weakness, tome or amulet the stronger spell misses.
		assertFalse(outclasses("fire surge", "water wave", false, false, false));
		assertFalse(outclasses("ice barrage", "blood blitz", false, false, false));
		// Spells outside the tiers are never compared, even when their names end like one.
		assertFalse(outclasses("fire surge", "iban blast", false, false, false));
		assertFalse(outclasses("fire surge", "saradomin strike", false, false, false));
	}

	@Test
	public void tierOnlyEffectsKeepTheWeakerSpell()
	{
		// The twinflame staff's second hit applies to bolt, blast and wave spells but not to surges.
		assertFalse(outclasses("fire surge", "fire wave", true, false, false));
		assertTrue(outclasses("fire wave", "fire blast", true, false, false));
		assertTrue(outclasses("fire surge", "fire strike", true, false, false));
		// Chaos gauntlets add 3 to bolts: 15 beats a blast's 16 only below 59 Magic, never a wave.
		assertTrue(SpellRules.outclasses(spell("fire blast"), spell("fire bolt"), 99, false, true, false));
		assertFalse(SpellRules.outclasses(spell("fire blast"), spell("fire bolt"), 50, false, true, false));
		assertTrue(SpellRules.outclasses(spell("fire blast"), spell("fire bolt"), 50, false, false, false));
		// With grouped targets a burst hits them all and a blitz does not.
		assertFalse(outclasses("ice blitz", "ice burst", false, false, true));
		assertTrue(outclasses("ice blitz", "ice burst", false, false, false));
		assertTrue(outclasses("ice barrage", "ice burst", false, false, true));
		assertTrue(outclasses("ice barrage", "ice blitz", false, false, true));
	}

	private static List<SetupResult> search(Monster monster, boolean preventManualCast, int aoeTargets, boolean prune)
	{
		Optimizer.pruneSpells = prune;
		CombatContext ctx = new CombatContext(monster, PlayerLevels.maxed(), false, false, TestData.piety())
			.withFightOptions(aoeTargets, 0);
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.BUDGET).budget(Long.MAX_VALUE)
			.preventManualCast(preventManualCast)
			.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient", "arceuus"))).build();
		return new Optimizer(data, ctx, settings, id -> false, GearItem::getPrice).optimize(CombatClass.MAGIC, () -> false);
	}

	@Test
	public void theBestSetupIsTheOneFoundWhenEverySpellIsScored() throws Exception
	{
		// Yama, Zulrah, Kraken, an abyssal demon, General Graardor, a fire giant and Kree'arra.
		for (int npc : new int[]{14176, 2042, 494, 415, 2215, 2075, 3162})
		{
			MonsterSummary summary = data.matchNpc(npc, null, 0);
			assertNotNull("npc " + npc, summary);
			Monster monster = WikiMonsters.get(GSON).monster(summary.getName());
			for (int variant = 0; variant < 3; variant++)
			{
				boolean prevent = variant == 1;
				int targets = variant == 2 ? 9 : 1;
				SetupResult full = search(monster, prevent, targets, false).get(0);
				SetupResult pruned = search(monster, prevent, targets, true).get(0);
				String label = summary.getName() + " variant " + variant;
				assertEquals(label, full.getDps().getDps(), pruned.getDps().getDps(), 1e-9);
				assertEquals(label, full.getLoadout().getWeapon(), pruned.getLoadout().getWeapon());
				assertEquals(label, full.getLoadout().getSpell(), pruned.getLoadout().getSpell());
			}
		}
	}
}
