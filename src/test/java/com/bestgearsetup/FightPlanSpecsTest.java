package com.bestgearsetup;

import static org.junit.Assert.*;

import com.bestgearsetup.calc.Outfit;
import com.bestgearsetup.calc.SpecialAttack;
import com.bestgearsetup.calc.SpecialAttacks;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Slot;
import java.util.*;
import java.util.function.Predicate;
import org.junit.Test;

/**
 * Special attacks in the fight planner, on a made-up fight: a setup's DPS is the sum of its items' per-phase values,
 * half as much again per landed hammer drain. A spec takes 3 seconds and always hits unless said otherwise.
 */
public class FightPlanSpecsTest
{
	private static final Set<Slot> NO_LOCKS = EnumSet.noneOf(Slot.class);
	private static final Predicate<Collection<Gear>> ANY = items -> true;

	private final Map<Integer, double[]> power = new HashMap<>();
	/** Spec weapon id to {energy, damage, accuracy, drains (1 or 0)}. */
	private final Map<Integer, double[]> specs = new LinkedHashMap<>();
	private final Map<Integer, Gear> weapons = new HashMap<>();
	/** Spec weapon id to the ammunition loaded into it. */
	private final Map<Integer, Gear> loaded = new HashMap<>();
	private double regen = 1.0 / 3;
	private int specCalls;

	private Gear gear(Slot slot, double... perPhase)
	{
		Gear gear = new Gear();
		gear.setId(power.size() + 1);
		gear.setName("item " + gear.getId());
		gear.setSlot(slot);
		power.put(gear.getId(), perPhase);
		return gear;
	}

	private Gear specWeapon(boolean twoHanded, double energy, double damage, double accuracy, boolean drains)
	{
		Gear gear = gear(Slot.WEAPON, 0, 0, 0, 0);
		gear.setTwoHanded(twoHanded);
		specs.put(gear.getId(), new double[]{energy, damage, accuracy, drains ? 1 : 0});
		weapons.put(gear.getId(), gear);
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

	private static Outfit holding(Outfit worn, Gear weapon)
	{
		Outfit held = worn.copy();
		held.set(Slot.WEAPON, weapon);
		if (weapon.isTwoHanded())
		{
			held.set(Slot.SHIELD, null);
		}
		return held;
	}

	private final FightPlan.Fight fight = new FightPlan.Fight()
	{
		@Override
		public double dps(Outfit outfit, int phase, SpecialAttacks drains)
		{
			double sum = 0;
			for (Slot slot : Slot.values())
			{
				sum += outfit.get(slot) == null ? 0 : power.get(outfit.get(slot).getId())[phase];
			}
			return sum * (1 + 0.5 * drains.getDragonWarhammer());
		}

		@Override
		public double[] spec(Outfit held, int phase, SpecialAttacks drains)
		{
			specCalls++;
			double[] spec = specs.get(held.getWeapon().getId());
			return new double[]{3, spec[1] * spec[2], spec[2]};
		}

		@Override
		public List<Outfit> specs(Outfit worn)
		{
			List<Outfit> out = new ArrayList<>();
			specs.keySet().forEach(id ->
			{
				Outfit held = holding(worn, weapons.get(id));
				held.setLoadedAmmo(loaded.get(id));
				out.add(held);
			});
			return out;
		}

		@Override
		public int energy(Outfit held)
		{
			return (int) specs.get(held.getWeapon().getId())[0];
		}

		@Override
		public SpecialAttacks landed(Outfit held, SpecialAttacks drains, double damage)
		{
			return specs.get(held.getWeapon().getId())[3] > 0
				? drains.landed(SpecialAttack.Drain.DRAGON_WARHAMMER, (int) damage) : drains;
		}

		@Override
		public double regen(Outfit outfit)
		{
			return bearer != null && bearer.equals(outfit.get(Slot.RING)) ? 2 * regen : regen;
		}
	};

	/** The ring that doubles regeneration. */
	private Gear bearer;

	/** @param bar 0 for the fight's shared health bar, -1 for several targets, else the phase's own */
	private FightPlan.Plan plan(int slots, boolean fullBar, Predicate<Collection<Gear>> allowed, double[] work, int[] bar,
		double[] cap, Outfit... candidates)
	{
		String[] bars = new String[bar.length];
		for (int i = 0; i < bar.length; i++)
		{
			bars[i] = bar[i] < 0 ? null : bar[i] == 0 ? "shared" : "own " + bar[i];
		}
		return FightPlan.plan(Arrays.asList(candidates), work, bars, cap, fight, fullBar, slots, NO_LOCKS, allowed, () -> false);
	}

	@Test
	public void aDrainCarriesOverTheSharedHealthBarOnly()
	{
		Outfit worn = outfit(gear(Slot.WEAPON, 10, 10, 10));
		Gear hammer = specWeapon(false, 50, 20, 1, true);
		double[] work = {100, 100, 10};
		FightPlan.Plan plan = plan(8, true, ANY, work, new int[]{0, 0, 3}, work, worn);

		Outfit held = holding(worn, hammer);
		assertEquals(Collections.singletonList(new FightPlan.Spec(held, 0, 2, true)), plan.getSpecs());
		assertEquals(Collections.singletonList(hammer), plan.getCarried());
		assertEquals(1, plan.getSlots());
		// Two specs, then 60 left at double DPS; the next form is drained too, the other target isn't.
		assertArrayEquals(new double[]{6 + 60 / 20.0, 100 / 20.0, 1}, plan.getSeconds(), 1e-9);
		assertEquals(100, plan.getEnergy(), 0);
		assertEquals(Arrays.asList(worn, worn, worn), plan.getUsed());
	}

	@Test
	public void aMissLeavesTheTargetUndrained()
	{
		Outfit worn = outfit(gear(Slot.WEAPON, 10, 10));
		specWeapon(false, 100, 20, 0.5, true);
		double[] work = {100, 200};
		FightPlan.Plan plan = plan(8, true, ANY, work, new int[]{0, 0}, work, worn);

		assertEquals(1, plan.getSpecs().get(0).getUses());
		// Expected damage 10; half the kills go on drained, half don't.
		assertEquals(3 + 0.5 * 90 / 15 + 0.5 * 90 / 10, plan.getSeconds()[0], 1e-9);
		assertEquals(0.5 * 200 / 15 + 0.5 * 200 / 10, plan.getSeconds()[1], 1e-9);
	}

	@Test
	public void severalTargetsGetNoDrain()
	{
		Outfit worn = outfit(gear(Slot.WEAPON, 10));
		specWeapon(false, 50, 20, 1, true);
		double[] work = {100};
		// 3 s for 20 damage is slower than attacking, unless the drain counts.
		assertTrue(plan(8, true, ANY, work, new int[]{-1}, work, worn).getSpecs().isEmpty());
		FightPlan.Plan own = plan(8, true, ANY, work, new int[]{1}, work, worn);
		assertEquals(1, own.getSpecs().size());
		assertTrue(own.getSpecs().get(0).isDrain());
	}

	@Test
	public void energyIsPaidInPhaseOrderAndRegenerates()
	{
		Outfit worn = outfit(gear(Slot.WEAPON, 10, 10));
		Gear claws = specWeapon(false, 50, 50, 1, false);
		Outfit held = holding(worn, claws);
		double[] work = {200, 200};
		int[] bar = {1, 2};
		// Each use saves 2 s. A full bar pays two; the first phase's 16 s with them regenerate under 6%.
		assertEquals(Collections.singletonList(new FightPlan.Spec(held, 0, 2, false)),
			plan(8, true, ANY, work, bar, work, worn).getSpecs());
		// 16 s at 3% a second is 48%, still short of a third use: the phase's time with its specials counts.
		regen = 3;
		assertEquals(Collections.singletonList(new FightPlan.Spec(held, 0, 2, false)),
			plan(8, true, ANY, work, bar, work, worn).getSpecs());
		// At 4% a second the first phase pays a third use in the second.
		regen = 4;
		FightPlan.Plan plan = plan(8, true, ANY, work, bar, work, worn);
		assertEquals(Arrays.asList(new FightPlan.Spec(held, 0, 2, false), new FightPlan.Spec(held, 1, 1, false)),
			plan.getSpecs());
		assertEquals(40 - 3 * 2, plan.getTotal(), 1e-9);
	}

	@Test
	public void regenerationHasOnlyWhatOneFightRegenerates()
	{
		Outfit worn = outfit(gear(Slot.WEAPON, 10, 10));
		Gear claws = specWeapon(false, 50, 50, 1, false);
		double[] work = {300, 300};
		int[] bar = {1, 2};
		// 60 s at a third of a percent: 20%, never enough for a 50% special.
		FightPlan.Plan plan = plan(8, false, ANY, work, bar, work, worn);
		assertTrue(plan.getSpecs().isEmpty());
		assertEquals(20, plan.getEnergy(), 1e-9);
		// 40% is still short: the energy isn't regenerated a second time while the phases pass.
		regen = 2.0 / 3;
		plan = plan(8, false, ANY, work, bar, work, worn);
		assertEquals(40, plan.getEnergy(), 1e-9);
		assertTrue(plan.getSpecs().isEmpty());
		// 90% pays one use and no more, wherever it is used.
		regen = 1.5;
		plan = plan(8, false, ANY, work, bar, work, worn);
		assertEquals(90, plan.getEnergy(), 1e-9);
		assertEquals(Collections.singletonList(new FightPlan.Spec(holding(worn, claws), 0, 1, false)), plan.getSpecs());
	}

	@Test
	public void aPhaseNothingDamagesRegeneratesNoEnergy()
	{
		// Nothing damages the second phase: its placeholder time is no fight time.
		Outfit worn = outfit(gear(Slot.WEAPON, 10, 0, 10));
		specWeapon(false, 50, 50, 1, false);
		double[] work = {300, 300, 300};
		int[] bar = {1, 2, 3};
		FightPlan.Plan plan = plan(8, false, ANY, work, bar, work, worn);
		assertEquals(20, plan.getEnergy(), 1e-9);
		assertTrue(plan.getSpecs().isEmpty());
		assertTrue(Double.isInfinite(plan.getSeconds()[1]));
		// From a full bar, two uses and no more after the phase that never ends.
		assertEquals(2, plan(8, true, ANY, work, bar, work, worn).getSpecs().stream().mapToInt(FightPlan.Spec::getUses).sum());
	}

	@Test
	public void eachPhaseRegeneratesAtItsOwnSetupsRate()
	{
		// The second phase's switch wears the ring that doubles regeneration: 20 s at 1% and 20 s at 2%.
		Outfit melee = outfit(gear(Slot.WEAPON, 10, 1), gear(Slot.RING, 5, 0));
		bearer = gear(Slot.RING, 0, 5);
		Outfit ringed = outfit(gear(Slot.WEAPON, 1, 10), bearer);
		regen = 1;
		double[] work = {300, 300};
		FightPlan.Plan plan = plan(8, false, ANY, work, new int[]{1, 2}, work, melee, ringed);
		assertEquals(melee, plan.getWorn());
		assertEquals(Arrays.asList(melee, ringed), plan.getUsed());
		assertEquals(60, plan.getEnergy(), 1e-9);
	}

	@Test
	public void aSpecWeaponCompetesWithSwitchesForSlots()
	{
		Outfit melee = outfit(gear(Slot.WEAPON, 10, 1));
		Gear bow = gear(Slot.WEAPON, 1, 10);
		Gear claws = specWeapon(false, 50, 50, 1, false);
		double[] work = {200, 200};
		int[] bar = {1, 2};
		FightPlan.Plan one = plan(1, true, ANY, work, bar, work, melee, outfit(bow));
		assertEquals("the switch saves far more", Collections.singletonList(bow), one.getCarried());
		assertTrue(one.getSpecs().isEmpty());

		FightPlan.Plan two = plan(2, true, ANY, work, bar, work, melee, outfit(bow));
		assertEquals(new HashSet<>(Arrays.asList(bow, claws)), new HashSet<>(two.getCarried()));
		assertEquals(2, two.getSlots());
		assertEquals(2, two.getSpecs().stream().mapToInt(FightPlan.Spec::getUses).sum());
		assertEquals(40 - 2 * 2, two.getTotal(), 1e-9);
	}

	@Test
	public void theWornWeaponsSpecNeedsNoSlotAndATwoHanderNeedsTwo()
	{
		Gear own = specWeapon(false, 50, 50, 1, false);
		power.put(own.getId(), new double[]{10});
		Gear shield = gear(Slot.SHIELD, 0);
		Outfit worn = outfit(own, shield);
		double[] work = {200};
		FightPlan.Plan plan = plan(0, true, ANY, work, new int[]{1}, work, worn);
		assertEquals(Collections.singletonList(new FightPlan.Spec(worn, 0, 2, false)), plan.getSpecs());
		assertTrue(plan.getCarried().isEmpty());
		assertEquals(0, plan.getSlots());

		specs.clear();
		Gear maul = specWeapon(true, 50, 50, 1, false);
		assertTrue("the shield needs room too", plan(1, true, ANY, work, new int[]{1}, work, worn).getSpecs().isEmpty());
		FightPlan.Plan carried = plan(2, true, ANY, work, new int[]{1}, work, worn);
		assertEquals(Collections.singletonList(maul), carried.getCarried());
		assertEquals(2, carried.getSlots());
		assertNull(carried.getSpecs().get(0).getHeld().get(Slot.SHIELD));
	}

	@Test
	public void loadedAmmunitionIsLimitedButNeedsNoSlot()
	{
		Outfit worn = outfit(gear(Slot.WEAPON, 10));
		Gear blowpipe = specWeapon(false, 50, 50, 1, false);
		Gear darts = gear(Slot.AMMO, 0);
		loaded.put(blowpipe.getId(), darts);
		double[] work = {200};
		FightPlan.Plan plan = plan(1, true, ANY, work, new int[]{1}, work, worn);
		assertEquals(Collections.singletonList(blowpipe), plan.getCarried());
		assertEquals(1, plan.getSlots());
		assertEquals(darts, plan.getSpecs().get(0).getHeld().getLoadedAmmo());
		assertTrue(plan(1, true, items -> !items.contains(darts), work, new int[]{1}, work, worn).getSpecs().isEmpty());
	}

	@Test
	public void aSpecWeaponThatMayNotBeTakenIsLeftOut()
	{
		Outfit worn = outfit(gear(Slot.WEAPON, 10));
		Gear claws = specWeapon(false, 50, 50, 1, false);
		double[] work = {200};
		FightPlan.Plan plan = plan(8, true, items -> !items.contains(claws), work, new int[]{1}, work, worn);
		assertTrue(plan.getSpecs().isEmpty());
		assertTrue(plan.getCarried().isEmpty());
		assertEquals(20, plan.getTotal(), 1e-9);
	}

	@Test
	public void aSpecDealsNoMoreThanOneTargetsWork()
	{
		Outfit worn = outfit(gear(Slot.WEAPON, 10));
		specWeapon(false, 50, 50, 1, false);
		double[] work = {100};
		// Ten targets of 10: a 50-damage special kills one, 3 s for 1 s of attacks.
		assertTrue(plan(8, true, ANY, work, new int[]{-1}, new double[]{10}, worn).getSpecs().isEmpty());
		assertEquals(2 * 3, plan(8, true, ANY, work, new int[]{-1}, work, worn).getTotal(), 1e-9);
	}

	@Test
	public void atMostTwoSpecWeaponsAreCarried()
	{
		Outfit worn = outfit(gear(Slot.WEAPON, 10, 10, 10));
		// Each is worth one use: a hammer on the long first form, and two that hit hard.
		Gear hammer = specWeapon(false, 50, 0, 1, true);
		Gear claws = specWeapon(false, 25, 60, 1, false);
		specWeapon(false, 25, 59, 1, false);
		double[] work = {2000, 100, 100};
		FightPlan.Plan plan = plan(8, true, ANY, work, new int[]{0, 2, 3}, work, worn);
		assertEquals(new HashSet<>(Arrays.asList(hammer, claws)), new HashSet<>(plan.getCarried()));
		assertEquals(2, plan.getSpecs().stream().map(FightPlan.Spec::getHeld).distinct().count());
	}

	@Test
	public void plansAreRepeatableAndEachNumberIsAskedOnce()
	{
		Outfit worn = outfit(gear(Slot.WEAPON, 10, 10, 10));
		specWeapon(false, 50, 20, 0.7, true);
		specWeapon(false, 25, 40, 1, false);
		double[] work = {300, 200, 100};
		int[] bar = {0, 0, 0};
		FightPlan.Plan plan = plan(8, true, ANY, work, bar, work, worn);
		int calls = specCalls;
		assertFalse(plan.getSpecs().isEmpty());
		assertEquals(plan, plan(8, true, ANY, work, bar, work, worn));
		// Two weapons, three phases and at most three sets of landed drains.
		assertTrue("spec numbers asked " + calls + " times", calls <= 2 * 3 * 3);
	}

	@Test
	public void withoutSpecWeaponsThePlanIsTheSwitchPlan()
	{
		Outfit melee = outfit(gear(Slot.WEAPON, 10, 1));
		Gear bow = gear(Slot.WEAPON, 1, 10);
		FightPlan.Plan plan = plan(1, true, ANY, new double[]{100, 100}, new int[]{1, 2}, new double[]{100, 100}, melee,
			outfit(bow));
		assertTrue(plan.getSpecs().isEmpty());
		assertEquals(Collections.singletonList(bow), plan.getCarried());
		assertEquals(20, plan.getTotal(), 1e-9);
	}
}
