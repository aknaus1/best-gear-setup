package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.RaidLoadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class RaidSwitchesTest
{
	private static final Slot[] EXTRAS = {Slot.HEAD, Slot.NECK, Slot.CAPE, Slot.RING};

	@Test public void beamRetainsFreezeOriginsUntilTheFullSetupHasBeenChosen()
	{
		Loadout first = new Loadout(); first.set(Slot.WEAPON, item(80, Slot.WEAPON));
		Loadout shared = new Loadout(); shared.set(Slot.WEAPON, item(81, Slot.WEAPON));
		Loadout casting = new Loadout(); casting.set(Slot.WEAPON, item(82, Slot.WEAPON));
		SetupResult firstBase = setup(first, null);
		SetupResult sharedBase = setup(shared, null);
		SetupResult fromFirst = setup(casting, first);
		SetupResult fromShared = setup(casting, shared);
		List<RaidEncounter> encounters = Arrays.asList(encounter("Freeze", false).withLoadoutRequirement(RaidLoadout.ICE_BARRAGE),
			new RaidEncounter("Start", new MonsterSummary()).withStartable(), encounter("Full", true));
		RaidPlan plan = RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ANCIENT, encounters,
			Arrays.asList(results(fromFirst, fromShared), results(sharedBase), results(firstBase, sharedBase)),
			1, Long.MAX_VALUE, id -> true, item -> 0, 0, id -> 0, () -> false);
		assertTrue("Only the freeze searched from the shared weapon can finish within one carried switch", plan != null);
		assertEquals(shared, plan.getSetups().get(0).getSwitchFrom());
	}

	@Test public void compiledGroupsMatchEverySharedSlotPatternAcrossNonadjacentEncounters()
	{
		SetupResult full = setup(new Loadout(), null);
		List<SetupResult> quicks = new ArrayList<>();
		for (int mask = 0; mask < 16; mask++)
		{
			Loadout loadout = full.getLoadout().copy();
			loadout.set(Slot.WEAPON, item(2, Slot.WEAPON));
			for (int slot = 0; slot < EXTRAS.length; slot++)
			{
				if ((mask & 1 << slot) != 0) { loadout.set(EXTRAS[slot], item(10 + slot, EXTRAS[slot])); }
			}
			quicks.add(setup(loadout, full.getLoadout()));
		}
		List<SearchResults> results = Arrays.asList(results(full), results(full), results(quicks), results(quicks));
		for (int limit = 1; limit <= 6; limit++)
		{
			List<RaidEncounter> encounters = Arrays.asList(encounter("Base", true).withSwitchLimit(limit),
				new RaidEncounter("Other room", new MonsterSummary()), encounter("A", false), encounter("B", false));
			RaidSwitches compiled = new RaidSwitches(encounters, results);
			for (int a = 0; a < 16; a++)
			{
				for (int b = 0; b < 16; b++)
				{
					List<SetupResult> selected = Arrays.asList(full, full, quicks.get(a), quicks.get(b));
					// The generated weapons always differ: there is room for limit - 1 distinct extra slots.
					boolean fits = Integer.bitCount(a | b) < limit;
					assertEquals(fits, compiled.completable(3, selected));
					assertEquals(fits, compiled.fullSetupChoice(selected) != null);
					if (fits) { assertTrue(compiled.completable(2, selected.subList(0, 3))); }
				}
			}
		}
	}

	@Test public void identicalFreezeEquipmentRetainsItsDistinctSearchProvenance()
	{
		Loadout worn = new Loadout();
		worn.set(Slot.HEAD, item(20, Slot.HEAD));
		SetupResult full = setup(worn, null);
		Loadout other = worn.copy();
		other.set(Slot.HEAD, item(21, Slot.HEAD));
		Loadout casting = worn.copy();
		casting.set(Slot.HEAD, item(22, Slot.HEAD));
		SetupResult compatible = setup(casting, worn);
		SetupResult incompatible = setup(casting, other);
		List<RaidEncounter> encounters = Arrays.asList(encounter("Base", true),
			encounter("Freeze", false).withLoadoutRequirement(RaidLoadout.ICE_BARRAGE), encounter("Other", false));
		List<SearchResults> results = Arrays.asList(results(full), results(compatible, incompatible), results(full));
		RaidSwitches compiled = new RaidSwitches(encounters, results);
		assertTrue(compiled.completable(1, Arrays.asList(full, compatible)));
		assertFalse(compiled.completable(1, Arrays.asList(full, incompatible)));
		assertTrue(compiled.completable(1, Arrays.asList(full, compatible)));
	}

	private static RaidEncounter encounter(String name, boolean base)
	{
		return new RaidEncounter(name, new MonsterSummary()).withSwitchGroup("forms", base);
	}

	private static SetupResult setup(Loadout loadout, Loadout from)
	{
		return new SetupResult(CombatClass.MELEE, loadout, new DpsResult(10, 24, 1, 4, 24, null),
			0, 0, Collections.emptySet(), Collections.emptySet(), null, from);
	}

	private static GearItem item(int id, Slot slot)
	{
		GearItem item = new GearItem();
		item.setId(id); item.setName("Item " + id); item.setSlot(slot);
		return item;
	}

	private static SearchResults results(SetupResult... setups) { return results(Arrays.asList(setups)); }
	private static SearchResults results(List<SetupResult> setups)
	{
		return new SearchResults(new Monster(), 100, Collections.singletonMap(AttackStyle.Type.SLASH, setups),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false);
	}
}
