package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/** Salve damage and ownership rules for Strength-scaling ranged attacks against undead. */
public class AtlatlSalveTest
{
	private static GameData data;
	private static final CombatContext CONTEXT = new CombatContext(TestData.monster(30, 0, "undead"),
		new PlayerLevels(82, 87, 80, 81, 76, 76, 87, 68), true, false, null);
	private static final OptimizerSettings SETTINGS = OptimizerSettings.builder()
		.mode(SearchMode.OWNED_ONLY)
		.depth(SearchDepth.BEST)
		.styles(Collections.singleton(AttackStyle.Type.ATLATL))
		.spellbooks(Collections.singleton("standard"))
		.build();

	@BeforeClass
	public static void load() throws Exception
	{
		Gson gson = RuneLiteAPI.GSON;
		data = WikiGameData.get(gson).gameData(gson);
	}

	@Test
	public void enchantedSalvesShareAtlatlDamageButOnlyImbuedGrantsAccuracy()
	{
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, data.getItem(Slot.WEAPON, 29000));
		loadout.set(Slot.AMMO, data.getItem(Slot.AMMO, 28991));
		loadout.setStyle(AttackStyle.parse("rapid,ranged,rapid"));
		DpsResult baseline = DpsCalculator.calculate(loadout, CONTEXT);
		loadout.set(Slot.NECK, data.getItem(Slot.NECK, 10588));
		DpsResult regular = DpsCalculator.calculate(loadout, CONTEXT);
		loadout.set(Slot.NECK, data.getItem(Slot.NECK, 12018));
		DpsResult imbued = DpsCalculator.calculate(loadout, CONTEXT);

		assertEquals(baseline.getMaxHit() * 6 / 5, regular.getMaxHit());
		assertEquals(baseline.getAccuracy(), regular.getAccuracy(), 1e-12);
		assertEquals(regular.getMaxHit(), imbued.getMaxHit());
		assertTrue(imbued.getAccuracy() > regular.getAccuracy());
		assertTrue(imbued.getDps() > regular.getDps());
	}

	@Test
	public void ownedOnlySelectsImbuedWhenAvailableAndRegularOtherwise()
	{
		Set<Integer> owned = new HashSet<>(Arrays.asList(29000, 28991, 10588));
		SetupResult regular = new Optimizer(data, CONTEXT, SETTINGS, owned::contains, item -> 0)
			.optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(10588, regular.getLoadout().get(Slot.NECK).getId());

		owned.add(12018);
		SetupResult imbued = new Optimizer(data, CONTEXT, SETTINGS, owned::contains, item -> 0)
			.optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(12018, imbued.getLoadout().get(Slot.NECK).getId());
		assertTrue(imbued.getDps().getDps() > regular.getDps().getDps());
	}
}
