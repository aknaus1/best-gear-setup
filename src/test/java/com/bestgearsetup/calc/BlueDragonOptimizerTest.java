package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/** Owned blue-dragon setups whose stat upgrades cross a max-hit breakpoint together. */
public class BlueDragonOptimizerTest
{
	private static GameData data;
	private static CombatContext ctx;
	private static final OptimizerSettings SETTINGS = OptimizerSettings.builder()
		.mode(SearchMode.OWNED_ONLY)
		.depth(SearchDepth.BEST)
		.antifire(Antifire.REGULAR)
		.protectMagic(false)
		.spellbooks(Collections.singleton("standard"))
		.build();

	@BeforeClass
	public static void load() throws Exception
	{
		Gson gson = RuneLiteAPI.GSON;
		data = WikiGameData.get(gson).gameData(gson);
		Monster monster = WikiMonsters.get(gson).monster(data.matchNpc(265, null, 0).getName());
		ctx = new CombatContext(monster, new PlayerLevels(82, 87, 80, 81, 76, 76, 87, 67), true, false, null);
	}

	private static Set<Integer> meleeOwned()
	{
		return new HashSet<>(Arrays.asList(11864, 13121, 10354, 6585, 6570, 26219, 29280, 1540,
			29283, 7462, 13239, 11773));
	}

	private static SetupResult best(Set<Integer> owned, CombatClass cls)
	{
		List<SetupResult> results = new Optimizer(data, ctx, SETTINGS, owned::contains, item -> 1_000)
			.optimize(cls, () -> false);
		return results.get(0);
	}

	@Test
	public void furyAndFireCapeTogetherEscapeTheStabPlateau()
	{
		Set<Integer> owned = meleeOwned();
		owned.remove(6585);
		owned.remove(6570);
		Loadout baseline = best(owned, CombatClass.MELEE).getLoadout();
		DpsResult before = DpsCalculator.calculate(baseline, ctx);
		Loadout furyOnly = baseline.copy();
		furyOnly.set(Slot.NECK, data.getItem(Slot.NECK, 6585));
		assertEquals(before.getDps(), DpsCalculator.calculate(furyOnly, ctx).getDps(), 1e-9);
		Loadout capeOnly = baseline.copy();
		capeOnly.set(Slot.CAPE, data.getItem(Slot.CAPE, 6570));
		assertTrue(DpsCalculator.calculate(capeOnly, ctx).getDps() < before.getDps());

		SetupResult result = best(meleeOwned(), CombatClass.MELEE);
		assertEquals(6585, result.getLoadout().get(Slot.NECK).getId());
		assertEquals(6570, result.getLoadout().get(Slot.CAPE).getId());
		assertEquals(32, result.getDps().getMaxHit());
		assertEquals(5.96212946154077, result.getDps().getDps(), 1e-9);
		assertEquals(0, result.getBuyCost());
	}

	@Test
	public void bloodMoonArmourTogetherEscapesTheEmptyCrushSlots()
	{
		Set<Integer> owned = new HashSet<>(Arrays.asList(11865, 6570, 6585, 28810, 1540, 7462, 13239, 11773));
		OptimizerSettings crush = SETTINGS.toBuilder().styles(Collections.singleton(AttackStyle.Type.CRUSH)).build();
		Loadout baseline = new Optimizer(data, ctx, crush, owned::contains, item -> 1_000)
			.optimize(CombatClass.MELEE, () -> false).get(0).getLoadout();
		double before = DpsCalculator.calculate(baseline, ctx).getDps();
		for (Slot slot : Arrays.asList(Slot.BODY, Slot.LEGS))
		{
			Loadout single = baseline.copy();
			single.set(slot, data.getItem(slot, slot == Slot.BODY ? 29022 : 29025));
			assertEquals(before, DpsCalculator.calculate(single, ctx).getDps(), 1e-9);
		}
		owned.add(29022);
		owned.add(29025);
		SetupResult result = new Optimizer(data, ctx, crush, owned::contains, item -> 1_000)
			.optimize(CombatClass.MELEE, () -> false).get(0);
		assertEquals(29022, result.getLoadout().get(Slot.BODY).getId());
		assertEquals(29025, result.getLoadout().get(Slot.LEGS).getId());
		assertEquals(38, result.getDps().getMaxHit());
		assertEquals(3.990498978687955, result.getDps().getDps(), 1e-9);
		assertEquals(0, result.getBuyCost());
	}

	@Test
	public void rangedAccuracyTieRetainsFurysDefenceAndPrayer()
	{
		Set<Integer> owned = new HashSet<>(Arrays.asList(11865, 27374, 10354, 6585, 9243, 9185,
			29004, 1540, 2497, 7462, 22951));
		SetupResult result = best(owned, CombatClass.RANGED);
		assertEquals(6585, result.getLoadout().get(Slot.NECK).getId());
		Loadout glory = result.getLoadout().copy();
		GearItem amulet = data.getItem(Slot.NECK, 10354);
		glory.set(Slot.NECK, amulet);
		assertEquals(result.getDps().getDps(), DpsCalculator.calculate(glory, ctx).getDps(), 1e-9);
		assertEquals(0, result.getBuyCost());
	}

	@Test
	public void superAntifireStabFindsTwoIndividuallyWorseStrengthTrades()
	{
		Set<Integer> owned = meleeOwned();
		owned.addAll(Arrays.asList(11865, 12954, 29025));
		OptimizerSettings settings = SETTINGS.toBuilder().antifire(Antifire.SUPER)
			.styles(Collections.singleton(AttackStyle.Type.STAB)).build();
		Loadout baseline = new Optimizer(data, ctx, settings.toBuilder().depth(SearchDepth.NORMAL).build(),
			owned::contains, item -> 1_000).optimize(CombatClass.MELEE, () -> false).get(0).getLoadout();
		double before = DpsCalculator.calculate(baseline, ctx).getDps();
		for (Slot slot : Arrays.asList(Slot.CAPE, Slot.LEGS))
		{
			Loadout single = baseline.copy();
			single.set(slot, data.getItem(slot, slot == Slot.CAPE ? 6570 : 29025));
			assertTrue(DpsCalculator.calculate(single, ctx).getDps() < before);
		}
		SetupResult result = new Optimizer(data, ctx, settings, owned::contains, item -> 1_000)
			.optimize(CombatClass.MELEE, () -> false).get(0);
		assertEquals(6570, result.getLoadout().get(Slot.CAPE).getId());
		assertEquals(29025, result.getLoadout().get(Slot.LEGS).getId());
		assertEquals(33, result.getDps().getMaxHit());
		assertEquals(6.162669837023042, result.getDps().getDps(), 1e-9);
		assertEquals(0, result.getBuyCost());

		OptimizerSettings locked = settings.toBuilder().locks(SlotLock.parse("CAPE:13121,LEGS:29283")).build();
		SetupResult constrained = new Optimizer(data, ctx, locked, owned::contains, item -> 1_000)
			.optimize(CombatClass.MELEE, () -> false).get(0);
		assertEquals(13121, constrained.getLoadout().get(Slot.CAPE).getId());
		assertEquals(29283, constrained.getLoadout().get(Slot.LEGS).getId());
		assertTrue(constrained.getDps().getDps() < result.getDps().getDps());

		owned.removeAll(Arrays.asList(6570, 29025));
		OptimizerSettings noSpend = settings.toBuilder().mode(SearchMode.BUDGET).budget(0).build();
		SetupResult affordable = new Optimizer(data, ctx, noSpend, owned::contains, item -> 1_000)
			.optimize(CombatClass.MELEE, () -> false).get(0);
		assertEquals(0, affordable.getBuyCost());
		assertTrue(affordable.getDps().getDps() < result.getDps().getDps());
	}

	@Test
	public void diamondBeatsRubyAcrossTheFightDespiteRubysHigherStartingDps()
	{
		Set<Integer> owned = new HashSet<>(Arrays.asList(11865, 27374, 6585, 9242, 9243, 9185,
			29004, 1540, 2497, 7462, 22951));
		SetupResult result = best(owned, CombatClass.RANGED);
		assertEquals(9243, result.getLoadout().get(Slot.AMMO).getId());
		Loadout ruby = result.getLoadout().copy();
		ruby.set(Slot.AMMO, data.getItem(Slot.AMMO, 9242));
		assertTrue(DpsCalculator.snapshot(ruby, ctx).getDps() > result.getDps().getDps());
		assertEquals(3.996653853236797, DpsCalculator.snapshot(ruby, ctx).getDps(), 1e-9);
		double seconds = 0;
		for (int hp = ctx.getMonster().getHitpoints(); hp >= 1; hp--)
		{
			seconds += 1 / DpsCalculator.snapshot(ruby, ctx.atTargetHp(hp)).getPrimaryDps();
		}
		double averaged = ctx.getMonster().getHitpoints() / seconds;
		assertTrue(averaged < result.getDps().getDps());
		assertEquals(averaged, DpsCalculator.calculate(ruby, ctx).getDps(), averaged * 0.002);
		assertEquals(3.942164150262182, result.getDps().getDps(), 1e-9);
	}

	@Test
	public void shortAtlatlFightFavoursSlayerHelmetOverTheFullBurnSet()
	{
		GearItem eclipseHelm = data.getItems(Slot.HEAD).stream()
			.filter(i -> i.getName().equals("eclipse moon helm")).findFirst().get();
		GearItem mixedBoots = data.getItems(Slot.FEET).stream()
			.filter(i -> i.getName().equals("mixed hide boots")).findFirst().get();
		Set<Integer> owned = new HashSet<>(Arrays.asList(11865, 6570, 6585, 29000, 28991, 29004,
			29007, 7462, 13239, 11773, eclipseHelm.getId(), mixedBoots.getId()));
		OptimizerSettings settings = SETTINGS.toBuilder().antifire(Antifire.SUPER)
			.styles(Collections.singleton(AttackStyle.Type.ATLATL)).build();
		SetupResult result = new Optimizer(data, ctx, settings, owned::contains, item -> 1_000)
			.optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(11865, result.getLoadout().get(Slot.HEAD).getId());
		assertEquals(13239, result.getLoadout().get(Slot.FEET).getId());
		assertEquals(5.16442935381305, result.getDps().getDps(), 1e-9);

		Loadout fullSet = result.getLoadout().copy();
		fullSet.set(Slot.HEAD, eclipseHelm);
		fullSet.set(Slot.FEET, mixedBoots);
		DpsResult direct = DpsCalculator.snapshot(fullSet, ctx);
		// Crediting all ten future burn hits per proc reproduces GearScape's sustained result.
		double allBurnHits = direct.getDps() + direct.getAccuracy() * 0.2 * 10 / 1.8;
		assertEquals(5.19227, allBurnHits, 1e-5);
		assertTrue(allBurnHits > result.getDps().getDps());
		assertEquals(4.773570288705978, DpsCalculator.calculate(fullSet, ctx).getDps(), 1e-9);
		assertTrue(DpsCalculator.calculate(fullSet, ctx).getDps() < result.getDps().getDps());
	}

	@Test
	public void superAntifireRangedFavoursKnivesOverWholeFightRubyDamage()
	{
		Set<Integer> owned = new HashSet<>(Arrays.asList(11865, 27374, 6585, 868, 9242, 9243, 9185,
			29004, 22275, 2497, 7462, 22951));
		OptimizerSettings settings = SETTINGS.toBuilder().antifire(Antifire.SUPER)
			.styles(Collections.singleton(AttackStyle.Type.RANGED)).build();
		SetupResult result = new Optimizer(data, ctx, settings, owned::contains, item -> 1_000)
			.optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(868, result.getLoadout().getWeapon().getId());
		assertEquals(3.9831401730861367, result.getDps().getDps(), 1e-9);
		Loadout crossbow = result.getLoadout().copy();
		crossbow.set(Slot.WEAPON, data.getItem(Slot.WEAPON, 9185));
		crossbow.set(Slot.AMMO, data.getItem(Slot.AMMO, 9242));
		assertEquals(4.00754, DpsCalculator.snapshot(crossbow, ctx).getDps(), 1e-5);
		assertTrue(DpsCalculator.snapshot(crossbow, ctx).getDps() > result.getDps().getDps());
		assertTrue(DpsCalculator.calculate(crossbow, ctx).getDps() < result.getDps().getDps());
		crossbow.set(Slot.AMMO, data.getItem(Slot.AMMO, 9243));
		assertEquals(3.9525841756379023, DpsCalculator.calculate(crossbow, ctx).getDps(), 1e-9);
	}

	@Test
	public void ownedOnlyDoesNotSubstituteUnownedDragonDarts()
	{
		Set<Integer> owned = new HashSet<>(Arrays.asList(11865, 27374, 6585, 9243, 9185, 29004,
			1540, 2497, 7462, 22951));
		assertEquals(9185, best(owned, CombatClass.RANGED).getLoadout().getWeapon().getId());
		owned.add(11230);
		SetupResult darts = best(owned, CombatClass.RANGED);
		assertEquals(11230, darts.getLoadout().getWeapon().getId());
		assertEquals(4.12223197101416, darts.getDps().getDps(), 1e-9);
	}
}
