package com.bestgearsetup;

import static org.junit.Assert.*;

import com.bestgearsetup.calc.Outfit;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Slot;
import java.util.*;
import java.util.function.Predicate;
import org.junit.Test;

/** The fight planner on small made-up setups whose DPS is the sum of each item's per-phase value. */
public class FightPlanTest
{
	private static final Set<Slot> NO_LOCKS = EnumSet.noneOf(Slot.class);
	private static final Predicate<Collection<Gear>> ANY = items -> true;

	private final Map<Integer, double[]> power = new HashMap<>();

	private Gear gear(Slot slot, double... perPhase)
	{
		Gear gear = new Gear();
		gear.setId(power.size() + 1);
		gear.setName("item " + gear.getId());
		gear.setSlot(slot);
		power.put(gear.getId(), perPhase);
		return gear;
	}

	private static Outfit outfit(Gear... items)
	{
		Outfit outfit = new Outfit();
		for (Gear item : items)
		{
			outfit.set(item.getSlot(), item);
		}
		return outfit;
	}

	private double dps(Outfit outfit, int phase)
	{
		double sum = 0;
		for (Slot slot : Slot.values())
		{
			sum += outfit.get(slot) == null ? 0 : power.get(outfit.get(slot).getId())[phase];
		}
		return sum;
	}

	private FightPlan.Plan plan(int slots, Set<Slot> locked, Predicate<Collection<Gear>> allowed, double[] work,
		Outfit... candidates)
	{
		return FightPlans.plan(Arrays.asList(candidates), work, this::dps, slots, locked, allowed, () -> false);
	}

	@Test
	public void greedyCarriesTheFastestSwitch()
	{
		Outfit melee = outfit(gear(Slot.WEAPON, 10, 1));
		Outfit slowRanged = outfit(gear(Slot.WEAPON, 1, 5));
		Gear bow = gear(Slot.WEAPON, 1, 10);
		FightPlan.Plan plan = plan(1, NO_LOCKS, ANY, new double[]{100, 100}, melee, slowRanged, outfit(bow));

		assertEquals(melee, plan.getWorn());
		assertEquals(Collections.singletonList(bow), plan.getCarried());
		assertEquals(1, plan.getSlots());
		assertEquals(Arrays.asList(melee, outfit(bow)), plan.getUsed());
		assertEquals(Collections.singletonList(bow), plan.switches(1));
		assertEquals(20, plan.getTotal(), 1e-9);
	}

	@Test
	public void slotsZeroGivesTheBestSingleSetup()
	{
		Outfit melee = outfit(gear(Slot.WEAPON, 10, 1));
		Outfit ranged = outfit(gear(Slot.WEAPON, 1, 10));
		Outfit hybrid = outfit(gear(Slot.WEAPON, 4, 4));
		FightPlan.Plan plan = plan(0, NO_LOCKS, ANY, new double[]{100, 100}, melee, ranged, hybrid);

		assertEquals(hybrid, plan.getWorn());
		assertTrue(plan.getCarried().isEmpty());
		assertEquals(0, plan.getSlots());
		assertEquals(Arrays.asList(hybrid, hybrid), plan.getUsed());
		assertEquals(50, plan.getTotal(), 1e-9);
	}

	@Test
	public void twoHanderClearsTheShieldAndNeedsRoomForIt()
	{
		Gear sword = gear(Slot.WEAPON, 10, 0);
		Gear shield = gear(Slot.SHIELD, 2, 0);
		Gear bow = gear(Slot.WEAPON, 0, 5);
		bow.setTwoHanded(true);
		Outfit swordAndShield = outfit(sword, shield);
		double[] work = {120, 10};

		// The bow comes out of the inventory, but the sword and shield both go in: two slots either way round.
		FightPlan.Plan tight = plan(1, NO_LOCKS, ANY, work, swordAndShield, outfit(bow));
		assertTrue(tight.getCarried().isEmpty());
		assertEquals(outfit(bow), tight.getWorn());
		assertEquals(Double.POSITIVE_INFINITY, tight.getSeconds()[0], 0);

		// Wearing either and carrying the other ties; both need two slots.
		FightPlan.Plan room = plan(2, NO_LOCKS, ANY, work, swordAndShield, outfit(bow));
		assertEquals(outfit(bow), room.getWorn());
		assertEquals(Arrays.asList(sword, shield), room.getCarried());
		assertEquals(2, room.getSlots());
		assertEquals(swordAndShield, room.getUsed().get(0));
		assertEquals(12, room.getTotal(), 1e-9);
	}

	@Test
	public void lockedShieldStopsATwoHanderSwitch()
	{
		Outfit swordAndShield = outfit(gear(Slot.WEAPON, 10, 0), gear(Slot.SHIELD, 1, 0));
		Gear bow = gear(Slot.WEAPON, 0, 10);
		bow.setTwoHanded(true);
		Set<Slot> shieldLocked = EnumSet.of(Slot.SHIELD);

		assertNull(FightPlan.switchTo(swordAndShield, outfit(bow), false, shieldLocked));
		assertNull(FightPlan.switchTo(swordAndShield, outfit(bow), true, shieldLocked));
		assertEquals(outfit(bow), FightPlan.switchTo(swordAndShield, outfit(bow), true, NO_LOCKS));
		FightPlan.Plan plan = plan(5, shieldLocked, ANY, new double[]{10, 10}, swordAndShield);
		assertEquals(Double.POSITIVE_INFINITY, plan.getSeconds()[1], 0);
		assertEquals("only the damaged phase counts", 10 / 11.0, plan.getTotal(), 1e-9);
	}

	@Test
	public void loadedAmmoFollowsTheWeaponHeld()
	{
		Outfit sword = outfit(gear(Slot.WEAPON, 1));
		Gear blowpipe = gear(Slot.WEAPON, 1);
		Outfit loaded = outfit(blowpipe);
		loaded.setLoadedAmmo(gear(Slot.AMMO, 0));

		assertEquals(loaded, FightPlan.switchTo(sword, loaded, true, NO_LOCKS));
		assertNull(FightPlan.switchTo(sword, loaded, true, EnumSet.of(Slot.WEAPON)).getLoadedAmmo());
		Outfit worn = sword.copy();
		worn.setLoadedAmmo(gear(Slot.AMMO, 0));
		assertEquals(worn.getLoadedAmmo(), FightPlan.switchTo(worn, loaded, false, EnumSet.of(Slot.WEAPON))
			.getLoadedAmmo());
	}

	@Test
	public void allowedSeesWornAndCarriedItems()
	{
		Gear melee = gear(Slot.WEAPON, 10, 1, 1);
		Gear bow = gear(Slot.WEAPON, 1, 10, 1);
		Gear staff = gear(Slot.WEAPON, 1, 1, 8);
		List<Collection<Gear>> seen = new ArrayList<>();
		Predicate<Collection<Gear>> twoItems = items ->
		{
			seen.add(new ArrayList<>(items));
			return items.size() <= 2;
		};
		FightPlan.Plan plan = plan(5, NO_LOCKS, twoItems, new double[]{100, 100, 100}, outfit(melee), outfit(bow),
			outfit(staff));

		assertEquals(1, plan.getCarried().size());
		assertTrue(seen.contains(Arrays.asList(bow, melee)));
		assertTrue(seen.contains(Collections.singletonList(melee)));
	}

	@Test
	public void disallowedItemsAreNeitherWornNorCarried()
	{
		Gear best = gear(Slot.WEAPON, 20, 20);
		Outfit melee = outfit(gear(Slot.WEAPON, 10, 1));
		Gear bow = gear(Slot.WEAPON, 1, 10);
		FightPlan.Plan plan = plan(5, NO_LOCKS, items -> !items.contains(best), new double[]{100, 100}, outfit(best),
			melee, outfit(bow));

		assertEquals(melee, plan.getWorn());
		assertEquals(Collections.singletonList(bow), plan.getCarried());
		assertNull(FightPlans.plan(Collections.singletonList(outfit(best)), new double[]{1}, this::dps, 5, NO_LOCKS,
			items -> !items.contains(best), () -> false));
	}

	@Test
	public void equalTimesPreferFewerSlots()
	{
		Outfit weak = outfit(gear(Slot.WEAPON, 10, 5));
		Outfit even = outfit(gear(Slot.WEAPON, 10, 10));
		FightPlan.Plan plan = plan(5, NO_LOCKS, ANY, new double[]{100, 100}, weak, even);

		assertEquals(even, plan.getWorn());
		assertEquals(0, plan.getSlots());
		assertEquals(20, plan.getTotal(), 1e-9);
	}

	@Test
	public void deadPhaseTakesTheFasterFixEvenWhenListedLater()
	{
		Outfit melee = outfit(gear(Slot.WEAPON, 10, 0));
		Gear slow = gear(Slot.WEAPON, 0, 2);
		Gear fast = gear(Slot.WEAPON, 0, 10);
		double[] work = {100, 100};

		FightPlan.Plan single = plan(0, NO_LOCKS, ANY, work, melee, outfit(slow), outfit(fast));
		assertEquals(melee, single.getWorn());
		assertEquals(Double.POSITIVE_INFINITY, single.getSeconds()[1], 0);
		assertEquals("the damaged phase only", 10, single.getTotal(), 1e-9);

		FightPlan.Plan plan = plan(1, NO_LOCKS, ANY, work, melee, outfit(slow), outfit(fast));
		assertEquals(20, plan.getTotal(), 1e-9);
		assertEquals(outfit(fast), plan.getUsed().get(1));
	}

	@Test
	public void weaponSwitchKeepsTheWornArmour()
	{
		Gear helm = gear(Slot.HEAD, 3, 3);
		Gear sword = gear(Slot.WEAPON, 10, 1);
		Gear bow = gear(Slot.WEAPON, 1, 10);
		Outfit ranged = outfit(bow, gear(Slot.HEAD, 0, 0));
		FightPlan.Plan plan = plan(1, NO_LOCKS, ANY, new double[]{130, 130}, outfit(sword, helm), ranged);

		assertEquals(Collections.singletonList(bow), plan.getCarried());
		assertEquals(outfit(bow, helm), plan.getUsed().get(1));
	}

	@Test
	public void dpsIsAskedOncePerSetupAndPhase()
	{
		List<Outfit> candidates = new ArrayList<>();
		for (int i = 0; i < 6; i++)
		{
			candidates.add(outfit(gear(Slot.WEAPON, i, 6 - i, 3), gear(Slot.BODY, 1, i % 2, 0)));
		}
		candidates.add(candidates.get(2).copy());
		Map<Outfit, Integer> perOutfit = new HashMap<>();
		FightPlans.plan(candidates, new double[]{50, 50, 50}, (outfit, phase) ->
		{
			perOutfit.merge(outfit, 1, Integer::sum);
			return dps(outfit, phase);
		}, 4, NO_LOCKS, ANY, () -> false);

		assertFalse(perOutfit.isEmpty());
		for (int count : perOutfit.values())
		{
			assertEquals(3, count);
		}
	}

	@Test
	public void plansFourPhasesOfTwentyCandidatesQuickly()
	{
		Random random = new Random(103);
		List<Outfit> candidates = new ArrayList<>();
		for (int i = 0; i < 20; i++)
		{
			Outfit outfit = new Outfit();
			for (Slot slot : new Slot[]{Slot.WEAPON, Slot.HEAD, Slot.BODY, Slot.LEGS, Slot.SHIELD})
			{
				outfit.set(slot, gear(slot, random.nextDouble() * 5, random.nextDouble() * 5, random.nextDouble() * 5,
					random.nextDouble() * 5));
			}
			candidates.add(outfit);
		}
		double[] work = {400, 300, 200, 100};
		long start = System.nanoTime();
		FightPlan.Plan plan = FightPlans.plan(candidates, work, this::dps, 8, NO_LOCKS, ANY, () -> false);
		long millis = (System.nanoTime() - start) / 1_000_000;

		assertTrue(plan.getSlots() <= 8);
		assertEquals(plan, FightPlans.plan(candidates, work, this::dps, 8, NO_LOCKS, ANY, () -> false));
		assertTrue("took " + millis + " ms", millis < 200);
	}

	@Test
	public void supersededSwitchIsDroppedAndFreesItsSlot()
	{
		// Phase 1 takes the wand first (best per slot), then the mage set replaces it, which leaves room for the axe.
		Gear wand = gear(Slot.WEAPON, 0, 8, 0);
		Outfit mage = outfit(gear(Slot.WEAPON, 0, 5, 0), gear(Slot.BODY, 0, 5, 0));
		Gear axe = gear(Slot.WEAPON, 12, 0, 0);
		Outfit worn = outfit(gear(Slot.WEAPON, 10, 1, 1), gear(Slot.SHIELD, 0, 0, 0));
		Map<Outfit, double[]> table = new HashMap<>();
		table.put(worn, new double[]{10, 1, 1});
		table.put(outfit(wand, worn.get(Slot.SHIELD)), new double[]{0, 8, 0});
		table.put(mage, new double[]{0, 10, 0});
		table.put(outfit(mage.getWeapon(), mage.get(Slot.BODY), worn.get(Slot.SHIELD)), new double[]{0, 10, 0});
		table.put(outfit(axe, worn.get(Slot.SHIELD)), new double[]{10.5, 0, 0});
		FightPlan.Plan plan = FightPlans.plan(Arrays.asList(worn, outfit(wand), mage, outfit(axe)), new double[]{150, 120, 1},
			(outfit, phase) -> table.getOrDefault(outfit, new double[3])[phase], 3, NO_LOCKS, ANY, () -> false);

		assertEquals(worn, plan.getWorn());
		assertFalse(plan.getCarried().contains(wand));
		assertTrue(plan.getCarried().contains(axe));
		assertEquals(3, plan.getSlots());
		assertEquals(150 / 10.5 + 12 + 1, plan.getTotal(), 1e-9);
	}

	@Test
	public void mostSecondsCanBeatMostSecondsPerSlot()
	{
		// From the worn setup: the dagger saves 10 s for one slot, the armour 30 s for five; only one fits.
		Gear weapon = gear(Slot.WEAPON);
		Outfit worn = outfit(weapon);
		Outfit dagger = outfit(gear(Slot.WEAPON));
		Outfit armour = outfit(gear(Slot.HEAD), gear(Slot.BODY), gear(Slot.LEGS), gear(Slot.HANDS), gear(Slot.FEET));
		Outfit armoured = armour.copy();
		armoured.set(Slot.WEAPON, weapon);
		Map<Outfit, double[]> table = new HashMap<>();
		table.put(worn, new double[]{10, 10});
		table.put(dagger, new double[]{20, 0});
		table.put(armoured, new double[]{0, 40});
		FightPlan.Plan plan = FightPlans.plan(Arrays.asList(worn, dagger, armour), new double[]{200, 400},
			(outfit, phase) -> table.getOrDefault(outfit, new double[2])[phase], 5, NO_LOCKS, ANY, () -> false);

		assertEquals(worn, plan.getWorn());
		assertEquals(Arrays.asList(worn, armoured), plan.getUsed());
		assertEquals(5, plan.getSlots());
		assertEquals(20 + 10, plan.getTotal(), 1e-9);
	}

	@Test
	public void spellsFromAnotherSpellbookAreNotAFreeSwitch()
	{
		Gear staff = gear(Slot.WEAPON, 1);
		Outfit standard = outfit(staff);
		standard.setSpell(spell("fire surge", "standard"));
		Outfit ancient = outfit(staff);
		ancient.setSpell(spell("ice barrage", "ancient"));
		Outfit sameBook = outfit(staff);
		sameBook.setSpell(spell("water surge", "standard"));

		assertEquals(sameBook, FightPlan.switchTo(standard, sameBook, true, NO_LOCKS));
		// The ancient spell is far better in the second phase, but the worn spell fixes the spellbook.
		Map<Outfit, double[]> table = new HashMap<>();
		table.put(standard, new double[]{10, 1});
		table.put(ancient, new double[]{1, 100});
		table.put(sameBook, new double[]{1, 5});
		FightPlan.Plan plan = FightPlans.plan(Arrays.asList(standard, ancient, sameBook), new double[]{100, 100},
			(outfit, phase) -> table.getOrDefault(outfit, new double[2])[phase], 8, NO_LOCKS, ANY, () -> false);
		assertEquals(Arrays.asList(standard, sameBook), plan.getUsed());
	}

	@Test
	public void theFirstSpellSwitchFixesTheSpellbook()
	{
		Gear sword = gear(Slot.WEAPON, 1);
		Gear staff = gear(Slot.WEAPON, 1);
		Outfit melee = outfit(sword);
		Outfit standard = outfit(staff);
		standard.setSpell(spell("fire surge", "standard"));
		Outfit ancient = outfit(staff);
		ancient.setSpell(spell("ice barrage", "ancient"));
		Map<Outfit, double[]> table = new HashMap<>();
		table.put(melee, new double[]{10, 1, 1});
		table.put(standard, new double[]{1, 20, 1});
		table.put(ancient, new double[]{1, 1, 50});
		FightPlan.Plan plan = FightPlans.plan(Arrays.asList(melee, standard, ancient), new double[]{100, 100, 100},
			(outfit, phase) -> table.getOrDefault(outfit, new double[3])[phase], 8, NO_LOCKS, ANY, () -> false);
		// The ancient spell saves the most, so the standard spell can't be cast in the same fight.
		assertFalse(plan.getUsed().contains(standard));
		assertTrue(plan.getUsed().contains(ancient));
		assertEquals(10 + 100 + 2, plan.getTotal(), 1e-9);
	}

	@Test
	public void cancellingDuringTheSwitchesGivesNoPlan()
	{
		Outfit melee = outfit(gear(Slot.WEAPON, 10, 1));
		Outfit ranged = outfit(gear(Slot.WEAPON, 1, 10));
		int[] asked = {0};
		// Not cancelled when each worn setup is started, only once its switches are being chosen.
		assertNull(FightPlans.plan(Arrays.asList(melee, ranged), new double[]{100, 100}, this::dps, 8, NO_LOCKS, ANY,
			() -> asked[0]++ >= 2));
	}

	@Test
	public void loadedAmmoCountsTowardTheLimits()
	{
		Gear darts = gear(Slot.AMMO, 0, 0);
		Outfit blowpipe = outfit(gear(Slot.WEAPON, 1, 10));
		blowpipe.setLoadedAmmo(darts);
		Outfit sword = outfit(gear(Slot.WEAPON, 10, 1));

		FightPlan.Plan plan = plan(1, NO_LOCKS, items -> !items.contains(darts), new double[]{100, 100}, sword, blowpipe);
		assertEquals(sword, plan.getWorn());
		assertTrue(plan.getCarried().isEmpty());
	}

	@Test
	public void cancelledPlanningGivesNoPlan()
	{
		assertNull(FightPlans.plan(Collections.singletonList(outfit(gear(Slot.WEAPON, 1))), new double[]{1}, this::dps,
			0, NO_LOCKS, ANY, () -> true));
	}

	private static com.bestgearsetup.data.Spell spell(String name, String spellbook)
	{
		com.bestgearsetup.data.Spell spell = new com.bestgearsetup.data.Spell();
		spell.setName(name);
		spell.setSpellbook(spellbook);
		return spell;
	}

	@Test
	public void teamWorkFollowsThePartySize()
	{
		// Shield 2,000 up to five players, then +200 each, capped at 19,600; three per kill, shared by the party.
		FightPlan.Phase shield = new FightPlan.Phase("Shield", "the nightmare", 3, com.bestgearsetup.data.EncounterPhase.STANDARD,
			EnumSet.noneOf(com.bestgearsetup.data.Combat.class), "shield", new double[]{2000, 200, 5, 19600}, true, 3);
		assertEquals(3 * 2000, shield.work(1, 999), 1e-9);
		assertEquals(3 * 2000 / 5.0, shield.work(5, 999), 1e-9);
		assertEquals(3 * 2200 / 6.0, shield.work(6, 999), 1e-9);
		assertEquals(3 * 19600 / 100.0, shield.work(100, 999), 1e-9);
		assertEquals("below 1 counts as solo", shield.work(1, 999), shield.work(0, 999), 1e-9);
		FightPlan.Phase own = new FightPlan.Phase("Husk", "husk (magic)", 2, com.bestgearsetup.data.EncounterPhase.STANDARD,
			EnumSet.noneOf(com.bestgearsetup.data.Combat.class), null, null, true, 2);
		assertEquals("the target's hitpoints, shared", 2 * 70 / 4.0, own.work(4, 70), 1e-9);
		assertTrue(FightPlan.partySized("the nightmare"));
		assertFalse(FightPlan.partySized("zulrah"));
	}
}
