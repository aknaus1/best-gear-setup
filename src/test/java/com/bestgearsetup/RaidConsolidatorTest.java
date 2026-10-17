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

	/** No max hit leaves the hit spread unknown, so each fight takes exactly HP over DPS (no overkill). */
	private SetupResult candidate(GearItem body, double dps)
	{
		Loadout loadout = new Loadout(); loadout.set(Slot.WEAPON, meleeWeapon); loadout.set(Slot.BODY, body);
		loadout.setStyle(AttackStyle.parse("pound,crush,aggressive"));
		return new SetupResult(CombatClass.MELEE, loadout, new DpsResult(dps, 0, 1, 4, dps * 2.4, null), 0);
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
		return new SetupResult(CombatClass.MAGIC, loadout, new DpsResult(dps, 0, 1, 4, dps * 2.4, null), 0);
	}

	@Test public void enforcesSharedBudgetAndRejectsImpossibleOrCancelledPlans() throws Exception
	{
		assertNotNull(consolidate(Collections.emptySet(), 2, 40, new AtomicBoolean()));
		assertNull(consolidate(new java.util.HashSet<>(Arrays.asList(mage.getId(), shared.getId())), 2, 39, new AtomicBoolean()));
		assertNull(consolidate(Collections.emptySet(), 0, Long.MAX_VALUE, new AtomicBoolean()));
		assertNull(consolidate(Collections.emptySet(), 2, Long.MAX_VALUE, new AtomicBoolean(true)));
	}

	/**
	 * Two fights whose fastest setups each spec with a different damage weapon (claws, dagger) on a shared worn sword:
	 * both fit the limit from 2 up, but the kit shares one damage spec weapon, so no kit comes from them alone. A
	 * restricted search offers the plain sword without the spec.
	 */
	private RaidPlan specConflict(int limit) throws Exception
	{
		GearItem sword = item(120, Slot.WEAPON);
		GearItem claws = item(121, Slot.WEAPON);
		GearItem dagger = item(122, Slot.WEAPON);
		GameData weapons = new GameData(Collections.emptyList(), Collections.emptyMap(), Arrays.asList(sword, claws, dagger),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		SetupResult plain = candidate(null, 5);
		plain.getLoadout().set(Slot.WEAPON, sword);
		SetupResult clawSpec = withSpec(plain, claws);
		SetupResult daggerSpec = withSpec(plain, dagger);
		List<RaidEncounter> fights = encounters.subList(0, 2);
		List<SearchResults> original = Arrays.asList(results(clawSpec), results(daggerSpec));
		assertNull(RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, fights, original, 28, Long.MAX_VALUE,
			id -> false, item -> 0, 0, id -> 0, () -> false));
		return RaidConsolidator.select(weapons, RaidPlanType.TOB, RaidSpellbook.ARCEUUS, fights, original,
			Collections.emptySet(), limit, Long.MAX_VALUE, id -> false, item -> 0, 0, id -> 0, () -> false,
			(index, excluded, pass) -> results(Arrays.asList(index == 0 ? clawSpec : daggerSpec, plain).stream()
				.filter(setup -> RaidPlan.gear(setup).keySet().stream().noneMatch(excluded::contains)).toArray(SetupResult[]::new)));
	}

	@Test public void aFittingBaselineWhoseKitIsNotFoundStillTriesRemovals() throws Exception
	{
		RaidPlan plan = specConflict(28);
		assertNotNull(plan);
		assertTrue(RaidPlanner.specialWeaponsAllowed(plan.getSetups()));
		assertEquals(1, plan.getSetups().stream().filter(setup -> setup.getSpecial() != null).count());
	}

	@Test public void raisingTheEquipmentLimitNeverSlowsOrLosesTheConsolidatedKit() throws Exception
	{
		// Below 2 the baseline is over the limit and consolidation drops the specs; from 2 it fits, yet must still
		// find a kit at least as fast as a lower limit's.
		double previous = Double.POSITIVE_INFINITY;
		for (int limit = 0; limit <= 4; limit++)
		{
			RaidPlan plan = specConflict(limit);
			assertNotNull("limit " + limit, plan);
			double seconds = RaidConsolidator.seconds(plan.getSetups(), plan.getResults(), encounters.subList(0, 2));
			assertTrue("limit " + limit, seconds <= previous);
			assertTrue("limit " + limit, RaidPlan.limitedSlots(plan.getInventory()) <= limit);
			previous = seconds;
		}
		previous = Double.POSITIVE_INFINITY;
		boolean found = false;
		for (int limit = 0; limit <= 6; limit++)
		{
			RaidPlan plan = consolidate(Collections.emptySet(), limit, Long.MAX_VALUE, new AtomicBoolean());
			if (plan == null) { assertTrue("limit " + limit, !found); continue; }
			found = true;
			double seconds = RaidConsolidator.seconds(plan.getSetups(), plan.getResults(), encounters);
			assertTrue("limit " + limit, seconds <= previous);
			assertTrue("limit " + limit, RaidPlan.limitedSlots(plan.getInventory()) <= limit);
			previous = seconds;
		}
		assertTrue(found);
	}

	@Test public void aFittingBaselineWithAnUnplannableKitStopsAfterTwoDryPasses() throws Exception
	{
		// Every setup opens on a 150% bar, so no kit is ever plannable, whatever consolidation removes.
		GearItem hammer = item(13576, Slot.WEAPON); hammer.setName("dragon warhammer");
		List<GearItem> bodies = new ArrayList<>();
		for (int i = 0; i < encounters.size(); i++) { bodies.add(item(130 + i, Slot.BODY)); }
		GameData gear = new GameData(Collections.emptyList(), Collections.singletonMap(Slot.BODY.getApiKey(), bodies),
			Collections.singletonList(hammer), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		java.util.function.IntFunction<SearchResults> wearing = body ->
		{
			Loadout loadout = new Loadout(); loadout.set(Slot.WEAPON, hammer); loadout.set(Slot.BODY, bodies.get(body));
			loadout.setStyle(AttackStyle.parse("pound,crush,aggressive"));
			DpsResult dps = new DpsResult(5, 0, 1, 4, 12, null);
			com.bestgearsetup.calc.SpecialAttack special = com.bestgearsetup.calc.SpecialAttack.of(loadout,
				new com.bestgearsetup.calc.CombatContext(new Monster(), com.bestgearsetup.calc.PlayerLevels.maxed(), false, true, null));
			com.bestgearsetup.calc.SpecialPlan plan = new com.bestgearsetup.calc.SpecialPlan(hammer, null, null, loadout.getStyle(),
				null, null, special, dps, dps, dps, 3, 0, 0, dps, 0, Collections.emptyList(), 150, null);
			return results(new SetupResult(CombatClass.MELEE, loadout, dps, 0, 0, Collections.emptySet(), Collections.emptySet(), plan));
		};
		List<SearchResults> original = new ArrayList<>();
		for (int i = 0; i < encounters.size(); i++) { original.add(wearing.apply(i)); }
		int[] passes = {0};
		RaidPlan plan = RaidConsolidator.select(gear, RaidPlanType.TOB, RaidSpellbook.ARCEUUS, encounters, original,
			Collections.emptySet(), 28, Long.MAX_VALUE, id -> false, item -> 0, 0, id -> 0, () -> false,
			Double.POSITIVE_INFINITY, (index, excluded, pass) ->
				excluded.contains(bodies.get(index).getId()) ? (excluded.contains(bodies.get(0).getId()) ? results() : wearing.apply(0))
					: wearing.apply(index),
			pass -> { passes[0] = Math.max(passes[0], pass); return percent -> { }; });
		assertNull(plan);
		// Each pass could still drop a carried body armour, but two passes without any kit end the search.
		assertEquals(2, passes[0]);
	}

	private static SetupResult withSpec(SetupResult base, GearItem weapon)
	{
		com.bestgearsetup.calc.SpecialPlan special = new com.bestgearsetup.calc.SpecialPlan(weapon, null, null,
			base.getLoadout().getStyle(), null, null, null, base.getDps(), base.getDps(), null, 1, 0, 0);
		return new SetupResult(base.getCombatClass(), base.getLoadout(), new DpsResult(10, 0, 1, 4, 24, null), 0, 0,
			Collections.emptySet(), Collections.emptySet(), special);
	}

	@Test public void startingSetupWearsAnotherSetupsItemWhenThatCostsLessThanDroppingIt() throws Exception
	{
		GearItem staff = item(110, Slot.WEAPON);
		GearItem whip = item(111, Slot.WEAPON);
		GearItem mageBoots = item(112, Slot.FEET); mageBoots.setMagicBonus(5);
		GearItem meleeBoots = item(113, Slot.FEET); meleeBoots.setMagicBonus(-4); meleeBoots.setMeleeStr(4);
		GameData kit = new GameData(Collections.emptyList(), Collections.singletonMap(Slot.FEET.getApiKey(),
			Arrays.asList(mageBoots, meleeBoots)), Arrays.asList(staff, whip), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap());
		List<RaidEncounter> fights = Arrays.asList(new RaidEncounter("Magic start", new MonsterSummary()),
			new RaidEncounter("Melee", new MonsterSummary()));
		RaidConsolidator.Search search = new RaidConsolidator.Search()
		{
			@Override public SearchResults run(int index, Set<Integer> excluded, int pass)
			{
				return index == 0 ? results(setup(staff, excluded.contains(mageBoots.getId()) ? null : mageBoots, 10, 9.9))
					: results(setup(whip, excluded.contains(meleeBoots.getId()) ? null : meleeBoots, 10, 8));
			}

			@Override public SearchResults wearing(int index, Set<Integer> excluded, java.util.Map<Slot, Integer> worn, int pass)
			{
				assertEquals(Collections.singletonMap(Slot.FEET, meleeBoots.getId()), worn);
				return results(setup(staff, meleeBoots, 9.8, 9.8));
			}
		};
		List<SearchResults> original = Arrays.asList(search.run(0, Collections.emptySet(), 0), search.run(1, Collections.emptySet(), 0));
		// Mage boots worn into the raid are free, but the melee boots must be carried: one switch over the limit.
		RaidPlan plan = RaidConsolidator.select(kit, RaidPlanType.TOA, RaidSpellbook.ARCEUUS, fights, original,
			Collections.emptySet(), 1, Long.MAX_VALUE, id -> false, item -> 10, 0, id -> 0, () -> false, search);
		assertNotNull(plan);
		assertEquals(meleeBoots, plan.getStartingSetup().getLoadout().get(Slot.FEET));
		assertEquals(meleeBoots, plan.getSetups().get(1).getLoadout().get(Slot.FEET));
		assertEquals(1, plan.getInventory().stream().filter(RaidPlan.InventoryItem::isEquipment).count());

		// Without that option the melee setup goes without its boots, a larger loss.
		RaidPlan dropped = RaidConsolidator.select(kit, RaidPlanType.TOA, RaidSpellbook.ARCEUUS, fights, original,
			Collections.emptySet(), 1, Long.MAX_VALUE, id -> false, item -> 10, 0, id -> 0, () -> false, search::run);
		assertNotNull(dropped);
		assertNull(dropped.getSetups().get(1).getLoadout().get(Slot.FEET));
		assertTrue(RaidConsolidator.seconds(plan.getSetups(), plan.getResults(), fights)
			< RaidConsolidator.seconds(dropped.getSetups(), dropped.getResults(), fights));
	}

	/** A setup with these boots at {@code dps}, or without boots at {@code bare}. */
	private static SetupResult[] setup(GearItem weapon, GearItem boots, double dps, double bare)
	{
		List<SetupResult> setups = new ArrayList<>();
		for (GearItem feet : boots == null ? new GearItem[]{null} : new GearItem[]{boots, null})
		{
			Loadout loadout = new Loadout(); loadout.set(Slot.WEAPON, weapon); loadout.set(Slot.FEET, feet);
			loadout.setStyle(AttackStyle.parse("pound,crush,aggressive"));
			double value = feet == null ? bare : dps;
			setups.add(new SetupResult(CombatClass.MELEE, loadout, new DpsResult(value, 0, 1, 4, value * 2.4, null), 0));
		}
		return setups.toArray(new SetupResult[0]);
	}

	@Test public void reportsDistinctTrialWorkDuringConsolidation() throws Exception
	{
		List<SearchResults> original = new ArrayList<>();
		for (int index = 0; index < encounters.size(); index++) { original.add(search(index, Collections.emptySet())); }
		java.util.Map<Integer, List<Double>> seen = new java.util.LinkedHashMap<>();
		List<Double> insideSearch = new ArrayList<>();
		RaidConsolidator.Search observed = new RaidConsolidator.Search()
		{
			private java.util.function.DoubleConsumer work;
			@Override public void searching(java.util.function.DoubleConsumer progress) { work = progress; }
			@Override public SearchResults run(int index, Set<Integer> excluded, int pass)
			{
				work.accept(25); insideSearch.add(seen.get(pass).get(seen.get(pass).size() - 1));
				work.accept(75); insideSearch.add(seen.get(pass).get(seen.get(pass).size() - 1));
				return search(index, excluded);
			}
		};
		RaidPlan plan = RaidConsolidator.select(data, RaidPlanType.COX_CM, RaidSpellbook.ARCEUUS, encounters, original,
			Collections.emptySet(), 2, Long.MAX_VALUE, id -> false, item -> 10, 0, id -> 0, () -> false,
			Double.POSITIVE_INFINITY, observed, pass -> percent -> seen.computeIfAbsent(pass, key -> new ArrayList<>()).add(percent));
		assertNotNull(plan);
		assertTrue("Separate expensive searches need separate progress ranges", insideSearch.stream().distinct().count() > 4);
		for (List<Double> pass : seen.values())
		{
			for (int index = 1; index < pass.size(); index++) { assertTrue(pass.get(index) >= pass.get(index - 1)); }
		}
	}

	@Test public void trialsSearchedOnHelperThreadsChooseTheSameKitAsOneByOne() throws Exception
	{
		List<SearchResults> original = new ArrayList<>();
		for (int index = 0; index < encounters.size(); index++) { original.add(search(index, Collections.emptySet())); }
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
		try
		{
			Set<String> threads = java.util.concurrent.ConcurrentHashMap.newKeySet();
			RaidConsolidator.Search together = new RaidConsolidator.Search()
			{
				@Override public SearchResults run(int index, Set<Integer> excluded, int pass) throws java.io.IOException
				{
					threads.add(Thread.currentThread().getName());
					// Slow enough that the helpers take trials before the calling thread has finished them all.
					try { Thread.sleep(10); }
					catch (InterruptedException e) { throw new java.io.InterruptedIOException(); }
					return search(index, excluded);
				}
				@Override public java.util.concurrent.Executor helpers() { return pool; }
				@Override public int helperCount() { return 2; }
			};
			RaidPlan alone = RaidConsolidator.select(data, RaidPlanType.COX_CM, RaidSpellbook.ARCEUUS, encounters, original,
				Collections.emptySet(), 2, Long.MAX_VALUE, id -> false, item -> 10, 0, id -> 0, () -> false,
				(index, excluded, pass) -> search(index, excluded));
			RaidPlan parallel = RaidConsolidator.select(data, RaidPlanType.COX_CM, RaidSpellbook.ARCEUUS, encounters, original,
				Collections.emptySet(), 2, Long.MAX_VALUE, id -> false, item -> 10, 0, id -> 0, () -> false, together);
			assertNotNull(alone);
			assertNotNull(parallel);
			assertTrue("Some trials must be searched on a helper thread", threads.size() > 1);
			assertEquals(RaidConsolidator.seconds(alone.getSetups(), alone.getResults(), encounters),
				RaidConsolidator.seconds(parallel.getSetups(), parallel.getResults(), encounters), 0);
			for (int index = 0; index < encounters.size(); index++)
			{
				assertEquals(RaidPlan.gear(alone.getSetups().get(index)).keySet(), RaidPlan.gear(parallel.getSetups().get(index)).keySet());
			}
		}
		finally { pool.shutdown(); }
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
				candidates.add(new SetupResult(CombatClass.MELEE, loadout, new DpsResult(dps, 0, 1, 4, dps * 2.4, null), 0));
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
