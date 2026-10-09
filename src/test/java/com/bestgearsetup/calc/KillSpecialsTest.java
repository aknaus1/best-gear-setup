package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/** Special attacks mixed into normal searches, with a switch to the best spec weapon. */
public class KillSpecialsTest
{
	/** Main code gets the client's Gson in startUp; tests use the same instance. */
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
	}

	private final GearItem sword = weapon(100, "sword", "slash sword", 4, "chop,slash,aggressive");
	private final GearItem claws = weapon(13652, "dragon claws", "claw", 4, "slash,slash,aggressive");
	private final GearItem helm = item(200, "helm", Slot.HEAD);
	private final Set<Integer> owned = new HashSet<>(Arrays.asList(100, 200));

	public KillSpecialsTest()
	{
		sword.setSlashBonus(90);
		sword.setMeleeStr(90);
		claws.setSlashBonus(40);
		claws.setMeleeStr(40);
		claws.setPrice(40_000_000);
		helm.setMeleeStr(5);
	}

	private List<SetupResult> run(OptimizerSettings.OptimizerSettingsBuilder settings, CombatModifiers modifiers)
	{
		Monster target = monster(100, 20);
		target.setHitpoints(300);
		GameData data = TestData.gameData(Arrays.asList(sword, claws, helm), Collections.emptyList());
		CombatContext ctx = new CombatContext(target, PlayerLevels.maxed(), false, true, piety()).withModifiers(modifiers);
		return new Optimizer(data, ctx, settings.spellbooks(Collections.singleton("standard")).build(),
			owned::contains, GearItem::getPrice).optimize(CombatClass.MELEE, () -> false);
	}

	private static SetupResult withWeapon(List<SetupResult> results, GearItem weapon)
	{
		for (SetupResult r : results)
		{
			if (r.getLoadout().getWeapon() == weapon)
			{
				return r;
			}
		}
		throw new AssertionError("no setup with " + weapon.getName());
	}

	private static OptimizerSettings.OptimizerSettingsBuilder unlimited()
	{
		return OptimizerSettings.builder().mode(SearchMode.UNLIMITED);
	}

	@Test
	public void specsAreOffUnlessEnabled()
	{
		SetupResult r = withWeapon(run(unlimited(), CombatModifiers.NONE), sword);
		assertNull(r.getSpecial());
	}

	@Test
	public void mainSetupSwitchesToTheSpecWeaponAndCountsItsDamage()
	{
		SetupResult plain = withWeapon(run(unlimited(), CombatModifiers.NONE), sword);
		SetupResult specced = withWeapon(run(unlimited().killSpecials(true), CombatModifiers.NONE), sword);
		SpecialPlan plan = specced.getSpecial();
		assertNotNull(plan);
		assertEquals(claws, plan.getWeapon());
		assertTrue(plan.isSwitch(specced.getLoadout()));
		assertEquals("Dragon claws", plan.getSpecial().getName());
		assertTrue(plan.getSpecsPerKill() > 0);
		assertEquals(plain.getDps().getDps(), plan.getOrdinary().getDps(), 1e-9);
		assertTrue(specced.getDps().getDps() > plain.getDps().getDps());
		// The worn setup and its stats are unchanged; only the damage rates include the specs.
		assertEquals(sword, specced.getLoadout().getWeapon());
		assertEquals(plain.getDps().getMaxHit(), specced.getDps().getMaxHit());
	}

	@Test
	public void specWeaponIsBoughtWithinTheBudget()
	{
		SetupResult affordable = withWeapon(run(OptimizerSettings.builder().mode(SearchMode.BUDGET)
			.budget(50_000_000).killSpecials(true), CombatModifiers.NONE), sword);
		assertEquals(claws, affordable.getSpecial().getWeapon());
		assertEquals(40_000_000, affordable.getBuyCost());
		assertEquals(40_000_000, affordable.getSpecial().getExtraCost());

		SetupResult tooDear = withWeapon(run(OptimizerSettings.builder().mode(SearchMode.BUDGET)
			.budget(10_000_000).killSpecials(true), CombatModifiers.NONE), sword);
		assertNull(tooDear.getSpecial());
		assertEquals(0, tooDear.getBuyCost());

		owned.add(claws.getId());
		SetupResult ownedClaws = withWeapon(run(OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY)
			.killSpecials(true), CombatModifiers.NONE), sword);
		assertEquals(claws, ownedClaws.getSpecial().getWeapon());
		assertEquals(0, ownedClaws.getBuyCost());
	}

	@Test
	public void aWeaponWithItsOwnSpecNeedsNoSwitch()
	{
		owned.add(claws.getId());
		SetupResult r = withWeapon(run(OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY).killSpecials(true),
			CombatModifiers.NONE), claws);
		assertEquals(claws, r.getSpecial().getWeapon());
		assertTrue(!r.getSpecial().isSwitch(r.getLoadout()));
		assertEquals(0, r.getSpecial().getExtraCost());
	}

	@Test
	public void aFullBarUsesMoreSpecsPerKillThanRegeneration()
	{
		SpecialPlan regen = withWeapon(run(unlimited().killSpecials(true), CombatModifiers.NONE), sword).getSpecial();
		SetupResult full = withWeapon(run(unlimited().killSpecials(true).specEnergy(SpecEnergy.FULL_BAR),
			CombatModifiers.NONE), sword);
		assertTrue(full.getSpecial().getSpecsPerKill() > regen.getSpecsPerKill() + 1);
		assertTrue(full.getDps().getDps() > regen.getOrdinary().getDps());
	}

	@Test
	public void onlySpecialAttacksModeIgnoresKillSpecials()
	{
		List<SetupResult> results = run(unlimited().killSpecials(true),
			CombatModifiers.builder().specialAttack(true).build());
		assertTrue(!results.isEmpty());
		for (SetupResult r : results)
		{
			assertNull(r.getSpecial());
		}
	}

	@Test
	public void twoHandedSpecWeaponCannotDropAMandatoryShield()
	{
		GearItem godsword = weapon(11802, "armadyl godsword", "2h sword", 6, "slash,slash,aggressive");
		godsword.setTwoHanded(true);
		godsword.setSlashBonus(132);
		godsword.setMeleeStr(132);
		GearItem shield = item(11283, "dragonfire shield", Slot.SHIELD);
		owned.addAll(Arrays.asList(godsword.getId(), shield.getId()));
		Monster wyvern = monster(100, 20);
		wyvern.setName("test wyvern");
		wyvern.setHitpoints(300);
		GameData data = TestData.gameData(Arrays.asList(sword, godsword, shield), Collections.emptyList());
		CombatContext ctx = new CombatContext(wyvern, PlayerLevels.maxed(), false, true, piety());
		OptimizerSettings.OptimizerSettingsBuilder settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY)
			.killSpecials(true).spellbooks(Collections.singleton("standard"));

		// Wyvern breath needs the shield, which the godsword would take off.
		SetupResult protectedSetup = withWeapon(new Optimizer(data, ctx, settings.build(), owned::contains,
			GearItem::getPrice).optimize(CombatClass.MELEE, () -> false), sword);
		assertEquals(shield, protectedSetup.getLoadout().get(Slot.SHIELD));
		assertNull(protectedSetup.getSpecial());

		SetupResult unprotected = withWeapon(new Optimizer(data, ctx, settings.requireFireProtection(false).build(),
			owned::contains, GearItem::getPrice).optimize(CombatClass.MELEE, () -> false), sword);
		assertEquals(godsword, unprotected.getSpecial().getWeapon());
	}

	private List<SetupResult> run(Monster target, SpecEnergy energy, GearItem... items)
	{
		return run(target, energy, DrainSpecs.EXPECTED, items);
	}

	private List<SetupResult> run(Monster target, SpecEnergy energy, DrainSpecs drains, GearItem... items)
	{
		GameData data = TestData.gameData(Arrays.asList(items), Collections.emptyList());
		CombatContext ctx = new CombatContext(target, PlayerLevels.maxed(), false, true, piety());
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY).killSpecials(true)
			.specEnergy(energy).drainSpecs(drains).spellbooks(Collections.singleton("standard")).build();
		return new Optimizer(data, ctx, settings, owned::contains, GearItem::getPrice).optimize(CombatClass.MELEE,
			() -> false);
	}

	private static Monster target(int defence, int hitpoints)
	{
		Monster m = monster(defence, 20);
		m.setHitpoints(hitpoints);
		return m;
	}

	@Test
	public void oneHandedSpecWeaponAfterATwoHandedMainTakesAnOffHand()
	{
		GearItem scythe = weapon(22325, "scythe of vitur", "2h sword", 5, "chop,slash,aggressive");
		scythe.setTwoHanded(true);
		scythe.setSlashBonus(110);
		scythe.setMeleeStr(75);
		GearItem defender = item(22322, "avernic defender", Slot.SHIELD);
		defender.setSlashBonus(29);
		defender.setMeleeStr(8);
		owned.addAll(Arrays.asList(scythe.getId(), claws.getId(), defender.getId()));
		SetupResult r = withWeapon(run(target(100, 300), SpecEnergy.FULL_BAR, scythe, claws, defender), scythe);
		assertNull(r.getLoadout().get(Slot.SHIELD));
		assertEquals(claws, r.getSpecial().getWeapon());
		assertEquals(defender, r.getSpecial().getOffhand());
		// The defender's bonuses are in the spec.
		Loadout held = r.getLoadout().copy();
		held.set(Slot.WEAPON, claws);
		held.setStyle(r.getSpecial().getStyle());
		CombatContext spec = new CombatContext(target(100, 300), PlayerLevels.maxed(), false, true, piety())
			.withModifiers(CombatModifiers.builder().specialAttack(true).build());
		assertTrue(r.getSpecial().getSpec().getMaxHit() > DpsCalculator.calculate(held, spec).getMaxHit());

		// A one-handed main keeps its own off-hand.
		SetupResult sword1h = withWeapon(run(target(100, 300), SpecEnergy.FULL_BAR, sword, claws, defender), sword);
		assertEquals(defender, sword1h.getLoadout().get(Slot.SHIELD));
		assertNull(sword1h.getSpecial().getOffhand());
	}

	@Test
	public void lightbearerReplacesAnEmptyRingWhenSpecsPay()
	{
		GearItem lightbearer = item(ItemID.LIGHTBEARER, "lightbearer", Slot.RING);
		owned.addAll(Arrays.asList(claws.getId(), lightbearer.getId()));
		SetupResult without = withWeapon(run(target(100, 3000), SpecEnergy.REGENERATION, sword, claws, helm), sword);
		SetupResult with = withWeapon(run(target(100, 3000), SpecEnergy.REGENERATION, sword, claws, helm, lightbearer),
			sword);
		assertNull(without.getLoadout().get(Slot.RING));
		assertEquals(lightbearer, with.getLoadout().get(Slot.RING));
		assertTrue(with.getSpecial().getSpecsPerKill() > 1.9 * without.getSpecial().getSpecsPerKill());
		assertTrue(with.getDps().getDps() > without.getDps().getDps());
	}

	@Test
	public void lightbearerIsNotWornAtTheCostOfAStrongerRing()
	{
		GearItem lightbearer = item(ItemID.LIGHTBEARER, "lightbearer", Slot.RING);
		GearItem ring = item(301, "strength ring", Slot.RING);
		ring.setMeleeStr(30);
		owned.addAll(Arrays.asList(claws.getId(), lightbearer.getId(), ring.getId()));
		SetupResult r = withWeapon(run(target(100, 3000), SpecEnergy.REGENERATION, sword, claws, lightbearer, ring),
			sword);
		assertEquals(ring, r.getLoadout().get(Slot.RING));
	}

	@Test
	public void drainingSpecOpensTheKillAndRaisesTheRestOfIt()
	{
		GearItem warhammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate");
		warhammer.setCrushBonus(95);
		warhammer.setMeleeStr(85);
		owned.add(warhammer.getId());
		Monster armoured = target(300, 2000);
		SetupResult r = withWeapon(run(armoured, SpecEnergy.FULL_BAR, sword, warhammer), sword);
		SpecialPlan plan = r.getSpecial();
		assertEquals(warhammer, plan.getWeapon());
		assertEquals(SpecialAttack.Drain.DRAGON_WARHAMMER, plan.getSpecial().getDrain());
		assertEquals(2, plan.getSpecsPerKill(), 1e-9);
		assertTrue(plan.getDrained().getDps() > plan.getOrdinary().getDps());
		assertTrue(r.getDps().getDps() > plan.getOrdinary().getDps());

		// With regenerated energy only, a long kill still opens with some warhammer specs.
		SpecialPlan regen = withWeapon(run(armoured, SpecEnergy.REGENERATION, sword, warhammer), sword).getSpecial();
		assertEquals(warhammer, regen.getWeapon());
		assertTrue(regen.getSpecsPerKill() > 0 && regen.getSpecsPerKill() <= 2);
	}

	@Test
	public void drainSettingChoosesBetweenTheAverageTheDamageAndTheWorstCase()
	{
		GearItem warhammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate");
		warhammer.setCrushBonus(95);
		warhammer.setMeleeStr(85);
		owned.addAll(Arrays.asList(warhammer.getId(), claws.getId()));
		Monster armoured = target(300, 2000);
		SetupResult expected = withWeapon(run(armoured, SpecEnergy.FULL_BAR, DrainSpecs.EXPECTED, sword, warhammer, claws),
			sword);
		SetupResult damage = withWeapon(run(armoured, SpecEnergy.FULL_BAR, DrainSpecs.DAMAGE_ONLY, sword, warhammer, claws),
			sword);
		SetupResult worst = withWeapon(run(armoured, SpecEnergy.FULL_BAR, DrainSpecs.ALL_MISS, sword, warhammer, claws),
			sword);
		assertEquals(warhammer, expected.getSpecial().getWeapon());
		// Without credit for its drain, the warhammer loses to the claws' damage.
		assertEquals(claws, damage.getSpecial().getWeapon());
		assertNull(damage.getSpecial().getDrained());
		assertEquals(claws, worst.getSpecial().getWeapon());
		assertEquals(damage.getDps().getDps(), worst.getDps().getDps(), 1e-9);
		assertTrue(expected.getDps().getDps() > worst.getDps().getDps());
	}

	@Test
	public void worstCaseNeverUsesADrainThatOnlyPaysWhenItLands()
	{
		GearItem warhammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate");
		warhammer.setCrushBonus(95);
		warhammer.setMeleeStr(85);
		owned.add(warhammer.getId());
		SetupResult r = withWeapon(run(target(300, 2000), SpecEnergy.FULL_BAR, DrainSpecs.ALL_MISS, sword, warhammer),
			sword);
		assertNull(r.getSpecial());
	}

	@Test
	public void everyDrainSettingHonoursTheSpecEnergyPerKill()
	{
		GearItem warhammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate");
		warhammer.setCrushBonus(95);
		warhammer.setMeleeStr(85);
		owned.addAll(Arrays.asList(warhammer.getId(), claws.getId()));
		// Short enough that one kill regenerates less than a full bar.
		Monster armoured = target(300, 600);
		for (DrainSpecs drains : DrainSpecs.values())
		{
			SpecialPlan full = withWeapon(run(armoured, SpecEnergy.FULL_BAR, drains, sword, warhammer, claws), sword)
				.getSpecial();
			SpecialPlan regen = withWeapon(run(armoured, SpecEnergy.REGENERATION, drains, sword, warhammer, claws), sword)
				.getSpecial();
			// A full bar opens with both 50% specs; regeneration alone gives fewer.
			assertTrue(drains + ": " + full.getSpecsPerKill(), full.getSpecsPerKill() >= 2);
			assertTrue(drains + ": " + regen.getSpecsPerKill(), regen.getSpecsPerKill() < full.getSpecsPerKill());
		}
	}

	@Test
	public void ammoSlotSpecWeaponBringsItsOwnAmmunition()
	{
		GearItem bow = weapon(25865, "test longbow", "bow", 5, "rapid,ranged,rapid");
		bow.setTwoHanded(true);
		bow.setRangedBonus(100);
		GearItem arrows = item(11212, "dragon arrow", Slot.AMMO);
		arrows.setRangedStr(60);
		bow.setAmmunition(Collections.singletonList(arrows.getId()));
		GearItem crossbow = weapon(26374, "zaryte crossbow", "crossbow", 5, "rapid,ranged,rapid");
		crossbow.setRangedBonus(110);
		GearItem bolts = item(21905, "dragon bolts", Slot.AMMO);
		bolts.setRangedStr(122);
		bolts.setPrice(2_000);
		crossbow.setAmmunition(Collections.singletonList(bolts.getId()));
		owned.addAll(Arrays.asList(bow.getId(), arrows.getId(), crossbow.getId()));
		GameData data = TestData.gameData(Arrays.asList(bow, arrows, crossbow, bolts), Collections.emptyList());
		CombatContext ctx = new CombatContext(target(250, 600), PlayerLevels.maxed(), false, true, null);
		OptimizerSettings.OptimizerSettingsBuilder settings = OptimizerSettings.builder().killSpecials(true)
			.specEnergy(SpecEnergy.FULL_BAR).spellbooks(Collections.singleton("standard"));

		// The worn arrows don't fit the crossbow; its bolts go on with it, and are bought for the ammo count.
		SetupResult bought = withWeapon(new Optimizer(data, ctx, settings.mode(SearchMode.BUDGET).budget(100_000_000)
			.ammoCount(100).build(), owned::contains, GearItem::getPrice).optimize(CombatClass.RANGED, () -> false), bow);
		assertEquals(arrows, bought.getLoadout().get(Slot.AMMO));
		SpecialPlan plan = bought.getSpecial();
		assertEquals(crossbow, plan.getWeapon());
		assertEquals(bolts, plan.getAmmo());
		assertTrue(plan.isSwitch(bought.getLoadout()));
		assertEquals(200_000, plan.getExtraCost());
		assertEquals(200_000, bought.getBuyCost());

		// Unaffordable spec ammunition rules the switch out.
		SetupResult tooDear = withWeapon(new Optimizer(data, ctx, settings.budget(100_000).build(), owned::contains,
			GearItem::getPrice).optimize(CombatClass.RANGED, () -> false), bow);
		assertNull(tooDear.getSpecial());
	}
}
