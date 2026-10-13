package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class RaidConsolidatorTest
{
	private final GearItem mage = item(100, Slot.BODY);
	private final GearItem shared = item(101, Slot.BODY);
	private final GearItem melee = item(102, Slot.BODY);
	private final GearItem inferior = item(103, Slot.BODY);
	private final GearItem meleeWeapon = item(104, Slot.WEAPON);
	private final GearItem magicWeapon = item(105, Slot.WEAPON);
	private final List<RaidEncounter> encounters = Arrays.asList(new RaidEncounter("Start", new MonsterSummary()),
		new RaidEncounter("Melee", new MonsterSummary()), new RaidEncounter("Other melee", new MonsterSummary()),
		new RaidEncounter("Magic", new MonsterSummary()));
	private final GameData data = new GameData(Collections.emptyList(), Collections.singletonMap(Slot.BODY.getApiKey(),
		Arrays.asList(mage, shared, melee, inferior)), Arrays.asList(meleeWeapon, magicWeapon), Collections.emptyList(),
		Collections.emptyMap(), Collections.emptyMap());

	@Test public void keepsRareHighValueSwitchAndRemovesMoreFrequentlyUsedLowValueSwitch() throws Exception
	{
		RaidPlan plan = consolidate(Collections.emptySet(), 2, Long.MAX_VALUE, new AtomicBoolean());
		assertNotNull(plan);
		assertEquals(mage, plan.getSetups().get(3).getLoadout().get(Slot.BODY));
		for (int i = 0; i < 3; i++) { assertEquals(shared, plan.getSetups().get(i).getLoadout().get(Slot.BODY)); }
		assertTrue(plan.getEquipment().stream().noneMatch(item -> item.getId() == inferior.getId()));
		assertEquals(2, plan.getInventory().stream().filter(RaidPlan.InventoryItem::isEquipment).count());
	}

	@Test public void repeatedOlmFightChangesWhichItemConsolidationRemoves() throws Exception
	{
		SetupResult start = candidate(shared, 10);
		SetupResult early = candidate(melee, 10);
		SetupResult earlyShared = candidate(shared, 8);
		SetupResult olm = candidate(mage, 10);
		SetupResult olmShared = candidate(shared, 9);
		List<SearchResults> original = Arrays.asList(results(start), results(early), results(olm));
		for (int repetitions : new int[]{1, 3})
		{
			List<RaidEncounter> fights = Arrays.asList(new RaidEncounter("Start", new MonsterSummary()),
				new RaidEncounter("Early boss", new MonsterSummary()), new RaidEncounter("Olm hand", new MonsterSummary(), repetitions));
			RaidPlan plan = RaidConsolidator.select(data, RaidPlanType.COX_CM, RaidSpellbook.ARCEUUS, fights, original,
				Collections.emptySet(), 1, Long.MAX_VALUE, id -> false, item -> 0, 0, id -> 0, () -> false,
				(index, excluded, pass) ->
				{
					List<SetupResult> options = index == 0 ? Collections.singletonList(start)
						: index == 1 ? Arrays.asList(early, earlyShared) : Arrays.asList(olm, olmShared);
					return results(options.stream().filter(setup -> RaidPlan.gear(setup).keySet().stream().noneMatch(excluded::contains))
						.toArray(SetupResult[]::new));
				});
			assertNotNull(plan);
			assertEquals(repetitions == 1 ? early : earlyShared, plan.getSetups().get(1));
			assertEquals(repetitions == 1 ? olmShared : olm, plan.getSetups().get(2));
			assertEquals(1, plan.getInventory().size());
		}
	}

	private SetupResult candidate(GearItem body, double dps)
	{
		Loadout loadout = new Loadout(); loadout.set(Slot.WEAPON, meleeWeapon); loadout.set(Slot.BODY, body);
		loadout.setStyle(AttackStyle.parse("pound,crush,aggressive"));
		return new SetupResult(CombatClass.MELEE, loadout, new DpsResult(dps, 20, 1, 4, dps * 2.4, null), 0);
	}

	private SearchResults results(SetupResult... setups)
	{
		Monster monster = new Monster(); monster.setName("test boss");
		return new SearchResults(monster, 100, setups.length == 0 ? Collections.emptyMap()
			: Collections.singletonMap(AttackStyle.Type.CRUSH, Arrays.asList(setups)), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), false);
	}

	@Test public void honoursLocksWhileComparingSwitches() throws Exception
	{
		RaidPlan plan = consolidate(Collections.singleton(melee.getId()), 2, Long.MAX_VALUE, new AtomicBoolean());
		assertNotNull(plan);
		assertEquals(mage, plan.getSetups().get(3).getLoadout().get(Slot.BODY));
		assertTrue(plan.getEquipment().contains(melee));
		assertTrue(!plan.getEquipment().contains(shared));
	}

	@Test public void feasibleMageStartingGearDoesNotStopArmourSharingComparison() throws Exception
	{
		SetupResult fang = candidate(melee, 10);
		SetupResult mageStart = magicalCandidate(mage, 1);
		SetupResult mageBoss = magicalCandidate(mage, 10);
		List<SearchResults> original = Arrays.asList(results(fang, mageStart), results(mageBoss));
		List<RaidEncounter> fights = encounters.subList(0, 2);
		RaidPlan poor = RaidPlanner.select(RaidPlanType.TOA, RaidSpellbook.ARCEUUS, fights, original, 1,
			Long.MAX_VALUE, id -> false, item -> 0, 0, id -> 0, () -> false);
		assertNotNull(poor);
		assertEquals(magicWeapon, poor.getSetups().get(0).getLoadout().getWeapon());
		RaidPlan improved = RaidConsolidator.select(data, RaidPlanType.TOA, RaidSpellbook.ARCEUUS, fights, original,
			Collections.emptySet(), 1, Long.MAX_VALUE, id -> false, item -> 0, 0, id -> 0, () -> false,
			(index, excluded, pass) ->
			{
				List<SetupResult> options = new ArrayList<>();
				if (index == 0 && !excluded.contains(meleeWeapon.getId()))
				{ options.add(candidate(excluded.contains(melee.getId()) ? null : melee, 10)); }
				if (!excluded.contains(magicWeapon.getId()))
				{ options.add(magicalCandidate(excluded.contains(mage.getId()) ? null : mage, index == 0 ? 1 : 9.9)); }
				return results(options.toArray(new SetupResult[0]));
			});
		assertNotNull(improved);
		assertEquals(meleeWeapon, improved.getSetups().get(0).getLoadout().getWeapon());
		assertEquals(magicWeapon, improved.getSetups().get(1).getLoadout().getWeapon());
		assertTrue(RaidConsolidator.seconds(improved.getSetups(), improved.getResults(), fights)
			< RaidConsolidator.seconds(poor.getSetups(), poor.getResults(), fights));
		assertEquals(1, improved.getInventory().size());
	}

	private SetupResult magicalCandidate(GearItem body, double dps)
	{
		Loadout loadout = new Loadout(); loadout.set(Slot.WEAPON, magicWeapon); loadout.set(Slot.BODY, body);
		loadout.setStyle(AttackStyle.parse("accurate,magic,accurate"));
		return new SetupResult(CombatClass.MAGIC, loadout, new DpsResult(dps, 20, 1, 4, dps * 2.4, null), 0);
	}

	@Test public void enforcesSharedBudgetAndRejectsImpossibleOrCancelledPlans() throws Exception
	{
		assertNotNull(consolidate(Collections.emptySet(), 2, 40, new AtomicBoolean()));
		assertNull(consolidate(new java.util.HashSet<>(Arrays.asList(mage.getId(), shared.getId())), 2, 39, new AtomicBoolean()));
		assertNull(consolidate(Collections.emptySet(), 0, Long.MAX_VALUE, new AtomicBoolean()));
		assertNull(consolidate(Collections.emptySet(), 2, Long.MAX_VALUE, new AtomicBoolean(true)));
	}

	private RaidPlan consolidate(Set<Integer> locked, int limit, long budget, AtomicBoolean cancelled) throws Exception
	{
		List<SearchResults> original = new ArrayList<>();
		for (int index = 0; index < encounters.size(); index++) { original.add(search(index, Collections.emptySet())); }
		return RaidConsolidator.select(data, RaidPlanType.COX_CM, RaidSpellbook.ANCIENT, encounters, original, locked,
			limit, budget, id -> false, item -> 10, 0, id -> 0, cancelled::get, (index, excluded, pass) ->
			{
				// A removed item must be replaced by another useful piece, never an unrelated weaker item.
				assertTrue(excluded.contains(inferior.getId()));
				assertTrue(Collections.disjoint(locked, excluded));
				return search(index, excluded);
			});
	}

	private SearchResults search(int index, Set<Integer> excluded)
	{
		GearItem weapon = index == 3 ? magicWeapon : meleeWeapon;
		List<SetupResult> candidates = new ArrayList<>();
		if (!excluded.contains(weapon.getId()))
		{
			for (GearItem body : Arrays.asList(mage, shared, melee))
			{
				if (excluded.contains(body.getId())) { continue; }
				double dps = index == 3 ? (body == mage ? 10 : 1)
					: index == 2 ? (body == shared ? 10 : body == melee ? 7 : 1)
					: body == melee ? 10 : body == shared ? 9.9 : 1;
				Loadout loadout = new Loadout(); loadout.set(Slot.WEAPON, weapon); loadout.set(Slot.BODY, body);
				loadout.setStyle(AttackStyle.parse("pound,crush,aggressive"));
				candidates.add(new SetupResult(CombatClass.MELEE, loadout, new DpsResult(dps, 20, 1, 4, dps * 2.4, null), 0));
			}
		}
		candidates.sort(java.util.Comparator.comparingDouble(setup -> -setup.getDps().getPrimaryDps()));
		// Only the best result is known initially; replacement candidates require a restricted search.
		if (excluded.isEmpty()) { candidates = candidates.subList(0, 1); }
		Monster monster = new Monster(); monster.setName("test boss");
		return new SearchResults(monster, 100, candidates.isEmpty() ? Collections.emptyMap()
			: Collections.singletonMap(AttackStyle.Type.CRUSH, candidates), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), false);
	}

	private static GearItem item(int id, Slot slot)
	{
		GearItem item = new GearItem(); item.setId(id); item.setName("item " + id); item.setSlot(slot); return item;
	}
}
