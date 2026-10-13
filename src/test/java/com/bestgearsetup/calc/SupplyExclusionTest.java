package com.bestgearsetup.calc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.SpellRunes;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

public class SupplyExclusionTest
{
	@Test public void excludedSpellRunesCauseTheOptimizerToChooseAnotherSpell() throws Exception
	{
		GameData full = new GameDataLoader(new Gson()).loadGameData();
		GearItem staff = full.getItem(Slot.WEAPON, 1381);
		GameData data = new GameData(Collections.emptyList(), Collections.emptyMap(), Collections.singletonList(staff),
			full.getSpells(), Collections.emptyMap(), Collections.emptyMap());
		CombatContext context = new CombatContext(TestData.monster(100, 20), PlayerLevels.maxed(), false, false, null);
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY).spellbooks(Collections.singleton("standard")).build();
		SetupResult original = new Optimizer(data, context, settings, id -> id == 1381, item -> 0)
			.optimize(CombatClass.MAGIC, () -> false).get(0);
		assertFalse(SpellRunes.suppliesAllowed(original.getLoadout(), original.getSupportSpells(), Collections.singleton(21880)));
		List<SetupResult> allowed = new Optimizer(data, context, settings.toBuilder().excludedSupplies(Collections.singleton(21880)).build(),
			id -> id == 1381, item -> 0).optimize(CombatClass.MAGIC, () -> false);
		assertTrue(!allowed.isEmpty());
		assertTrue(allowed.stream().allMatch(setup -> SpellRunes.suppliesAllowed(setup.getLoadout(), setup.getSupportSpells(), Collections.singleton(21880))));
	}

	@Test public void excludingLoadedDartsChangesTheBlowpipeSearch() throws Exception
	{
		GameData full = new GameDataLoader(new Gson()).loadGameData();
		GearItem pipe = full.getItem(Slot.WEAPON, 12926);
		GearItem amethyst = full.getItem(Slot.AMMO, 21326);
		GearItem rune = full.getItem(Slot.AMMO, 811);
		GameData data = new GameData(Collections.emptyList(), Collections.singletonMap(Slot.AMMO.getApiKey(), Arrays.asList(amethyst, rune)),
			Collections.singletonList(pipe), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		CombatContext context = new CombatContext(TestData.monster(100, 20), PlayerLevels.maxed(), false, false, null);
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY).spellbooks(Collections.emptySet())
			.excluded(Collections.singleton(21326)).build();
		List<SetupResult> setups = new Optimizer(data, context, settings, id -> id == 12926 || id == 21326 || id == rune.getId(), item -> 0)
			.optimize(CombatClass.RANGED, () -> false);
		assertTrue(!setups.isEmpty());
		assertTrue(setups.stream().allMatch(setup -> setup.getLoadout().getLoadedAmmo().getId() == rune.getId()));
	}

	@Test public void supportSpellSuppliesCanBeExcludedWithoutBlockingUnrelatedSpells()
	{
		Loadout loadout = new Loadout();
		assertFalse(SpellRunes.suppliesAllowed(loadout, EnumSet.of(SupportSpell.THRALL), Collections.singleton(565)));
		assertTrue(SpellRunes.suppliesAllowed(loadout, EnumSet.of(SupportSpell.MARK_OF_DARKNESS), Collections.singleton(565)));
	}

}
