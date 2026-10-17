package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertArrayEquals;
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
	public void theatreOfBloodNeverSwitchesToLightbearer()
	{
		GearItem lightbearer = item(ItemID.LIGHTBEARER, "lightbearer", Slot.RING);
		owned.addAll(Arrays.asList(claws.getId(), lightbearer.getId()));
		Monster boss = target(100, 3000);
		Monster.Attribute theatre = new Monster.Attribute();
		theatre.setName("theatre of blood");
		boss.setAttributes(Collections.singletonList(theatre));
		SetupResult r = withWeapon(run(boss, SpecEnergy.REGENERATION, sword, claws, helm, lightbearer), sword);
		assertNull(r.getLoadout().get(Slot.RING));
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
	public void targetsThatRestoreTheirDefenceScoreDrainsOnTheirDamageAlone()
	{
		GearItem warhammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate");
		warhammer.setCrushBonus(95);
		warhammer.setMeleeStr(85);
		owned.addAll(Arrays.asList(warhammer.getId(), claws.getId()));
		// Akkha regenerates Defence quickly. Bloat has a short useful BGS window instead.
		for (String name : new String[]{"akkha"})
		{
			Monster armoured = target(300, 2000);
			armoured.setName(name);
			SetupResult r = withWeapon(run(armoured, SpecEnergy.FULL_BAR, sword, warhammer, claws), sword);
			assertEquals(name, claws, r.getSpecial().getWeapon());
			assertNull(name, r.getSpecial().getDrained());
		}
	}

	@Test
	public void aDrainLostPartWayThroughTheKillOnlyPaysUntilThen()
	{
		GearItem warhammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate");
		warhammer.setCrushBonus(95);
		warhammer.setMeleeStr(85);
		owned.add(warhammer.getId());
		SetupResult lasting = withWeapon(run(target(300, 2000), SpecEnergy.FULL_BAR, sword, warhammer), sword);
		// Sotetseg's Defence resets each time players are sent to the shadow realm: a third of her health.
		Monster sotetseg = target(300, 2000);
		sotetseg.setName("sotetseg");
		SetupResult reset = withWeapon(run(sotetseg, SpecEnergy.FULL_BAR, sword, warhammer), sword);
		assertEquals(warhammer, reset.getSpecial().getWeapon());
		assertTrue(reset.getDps().getDps() < lasting.getDps().getDps());
		assertTrue(reset.getDps().getDps() > reset.getSpecial().getOrdinary().getDps());
	}

	@Test
	public void aFullBarPlanKeepsItsRegeneratedEnergyAlternative()
	{
		SetupResult full = withWeapon(run(unlimited().killSpecials(true).specEnergy(SpecEnergy.FULL_BAR),
			CombatModifiers.NONE), sword);
		SpecialPlan plan = full.getSpecial();
		assertTrue(full.usesFullBar());
		assertTrue(plan.getRegenerated().getDps() < full.getDps().getDps());
		assertTrue(plan.getRegenerated().getDps() >= plan.getOrdinary().getDps());
		assertTrue(plan.getRegeneratedSpecsPerKill() < plan.getSpecsPerKill());
		SetupResult regenerated = full.withRegeneratedEnergy();
		assertTrue(!regenerated.usesFullBar());
		assertEquals(plan.getRegenerated(), regenerated.getDps());
		assertEquals(full.getLoadout(), regenerated.getLoadout());
		if (regenerated.getSpecial() != null)
		{ assertEquals(plan.getRegeneratedSpecsPerKill(), regenerated.getSpecial().getSpecsPerKill(), 1e-12); }
		// Regeneration alone is the plan itself, with no bar to spend.
		SetupResult regen = withWeapon(run(unlimited().killSpecials(true), CombatModifiers.NONE), sword);
		assertTrue(!regen.usesFullBar());
		assertTrue(regen == regen.withRegeneratedEnergy());
	}

	/** Xarpus in a party of four, with or without the teammates' openers, searched for one class. */
	private List<SetupResult> xarpus(boolean team, CombatClass cls, GearItem... items)
	{
		return teamBoss("xarpus", team, cls, items);
	}

	/** A boss in a party of four, with or without the teammates' openers, searched for one class. */
	private List<SetupResult> teamBoss(String name, boolean team, CombatClass cls, GearItem... items)
	{
		return teamBoss(name, 4, team, cls, items);
	}

	private List<SetupResult> teamBoss(String name, int party, boolean team, CombatClass cls, GearItem... items)
	{
		return teamBoss(name, party, team, false, cls, items);
	}

	private List<SetupResult> teamBoss(String name, int party, boolean team, boolean reducerOwned, CombatClass cls,
		GearItem... items)
	{
		Monster target = target(200, 3000);
		target.setName(name);
		return teamBoss(target, party, team, reducerOwned, cls, items);
	}

	private List<SetupResult> teamBoss(Monster target, int party, boolean team, boolean reducerOwned, CombatClass cls,
		GearItem... items)
	{
		GameData data = TestData.gameData(Arrays.asList(items), Collections.emptyList());
		CombatContext ctx = new CombatContext(target, PlayerLevels.maxed(), false, true, piety());
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY).killSpecials(true)
			.specEnergy(SpecEnergy.FULL_BAR).raidKillShare(party).teamOpeners(team).sharedReducerUsable(reducerOwned)
			.spellbooks(Collections.singleton("standard")).build();
		return new Optimizer(data, ctx, settings, owned::contains, GearItem::getPrice).optimize(cls, () -> false);
	}

	/** The ordinary-only result with this weapon: the zero-own-spec baseline. */
	private static double plainDps(List<SetupResult> results, GearItem weapon)
	{
		return results.stream().filter(r -> r.getLoadout().getWeapon() == weapon && r.getSpecial() == null)
			.mapToDouble(r -> r.getDps().getPrimaryDps()).findFirst()
			.orElseThrow(() -> new AssertionError("no plain setup with " + weapon.getName()));
	}

	@Test
	public void teammatesOpenersCreditEveryKitAndClassAlike()
	{
		GearItem warhammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate");
		warhammer.setCrushBonus(95);
		warhammer.setMeleeStr(85);
		GearItem bow = weapon(25865, "bow of faerdhinen", "bow", 4, "accurate,ranged,accurate");
		bow.setRangedBonus(90);
		bow.setRangedStr(90);
		owned.add(bow.getId());
		double swordAlone = plainDps(xarpus(true, CombatClass.MELEE, sword, helm, warhammer), sword);
		double bowAlone = plainDps(xarpus(true, CombatClass.RANGED, bow, helm, warhammer), bow);
		owned.add(warhammer.getId());
		List<SetupResult> withHammer = xarpus(true, CombatClass.MELEE, sword, helm, warhammer);
		// Owning a hammer adds only this player's own specs on top; the no-spec baseline is the same.
		assertEquals(swordAlone, plainDps(withHammer, sword), 1e-9);
		assertEquals(bowAlone, plainDps(xarpus(true, CombatClass.RANGED, bow, helm, warhammer), bow), 1e-9);
		// Every class gets the teammates' drains, not only a kit that carries a reducer.
		assertTrue(swordAlone > plainDps(xarpus(false, CombatClass.MELEE, sword, helm), sword));
		assertTrue(bowAlone > plainDps(xarpus(false, CombatClass.RANGED, bow, helm), bow));
		SetupResult specced = withWeapon(withHammer, sword);
		if (specced.getSpecial() != null) { assertTrue(specced.getDps().getPrimaryDps() >= swordAlone); }
	}

	@Test
	public void sotetsegFairShareIsOneReducerAThirdAndTeammatesMakeUpTheRest()
	{
		// Two a third (three thirds): two of the six in a party of three to five, three in a party of two.
		assertEquals(0, SpecialRotation.sotetsegShare(1));
		assertEquals(3, SpecialRotation.sotetsegShare(2));
		assertEquals(2, SpecialRotation.sotetsegShare(3));
		assertEquals(2, SpecialRotation.sotetsegShare(4));
		assertEquals(2, SpecialRotation.sotetsegShare(5));
		assertEquals(1, SpecialRotation.sotetsegShare(8));
		// Party of four with a hammer: own reducers in the first two thirds, one teammate there, two in the last.
		int[] own = new int[3];
		int[] teammates = new int[3];
		for (int phase = 0; phase < 3; phase++)
		{
			own[phase] = Optimizer.phaseSpecs(2, 3, phase);
			teammates[phase] = SpecialRotation.sotetsegTeammates(4, own[phase]);
		}
		assertArrayEquals(new int[]{1, 1, 0}, own);
		assertArrayEquals(new int[]{1, 1, 2}, teammates);
		// Party of two: one own and one teammate's every third; without a shared reducer, the teammate alone.
		for (int phase = 0; phase < 3; phase++)
		{
			assertEquals(1, Optimizer.phaseSpecs(3, 3, phase));
			assertEquals(1, SpecialRotation.sotetsegTeammates(2, 1));
			assertEquals(1, SpecialRotation.sotetsegTeammates(2, 0));
			assertEquals(2, SpecialRotation.sotetsegTeammates(4, 0));
		}
		assertTrue(SpecialRotation.sharedReducer(SpecialAttack.Drain.DRAGON_WARHAMMER));
		assertTrue(SpecialRotation.sharedReducer(SpecialAttack.Drain.ELDER_MAUL));
		assertTrue(SpecialRotation.sharedReducer(SpecialAttack.Drain.BANDOS_GODSWORD));
		assertTrue(!SpecialRotation.sharedReducer(SpecialAttack.Drain.ARCLIGHT));
	}

	/** Reducer specs per kill in the fastest Sotetseg plan with this weapon. */
	private double sotetsegReducers(int party, GearItem reducer)
	{
		owned.add(reducer.getId());
		SetupResult best = withWeapon(teamBoss("sotetseg (normal)", party, true, CombatClass.MELEE, sword, helm, reducer), sword);
		return best.getSpecials().stream().filter(plan -> plan.getSpecial().getDrain() != SpecialAttack.Drain.NONE)
			.mapToDouble(SpecialPlan::getSpecsPerKill).sum();
	}

	@Test
	public void eachOfTheThreeHeavyReducersTakesTheFairShareAtSotetseg()
	{
		GearItem warhammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate");
		warhammer.setCrushBonus(95);
		warhammer.setMeleeStr(85);
		GearItem maul = weapon(21003, "elder maul", "blunt", 6, "pound,crush,accurate");
		maul.setTwoHanded(true);
		maul.setCrushBonus(135);
		maul.setMeleeStr(147);
		GearItem godsword = weapon(11804, "bandos godsword", "2h sword", 6, "chop,slash,accurate");
		godsword.setTwoHanded(true);
		godsword.setSlashBonus(132);
		godsword.setMeleeStr(132);
		assertEquals(2, sotetsegReducers(4, warhammer), 1e-9);
		// A party of two owes three, but a full bar funds only two 50% specials.
		assertEquals(2, sotetsegReducers(2, warhammer), 1e-9);
		owned.remove(warhammer.getId());
		assertEquals(2, sotetsegReducers(4, maul), 1e-9);
		owned.remove(maul.getId());
		assertEquals(2, sotetsegReducers(4, godsword), 1e-9);
	}

	@Test
	public void everyKitStillOwesItsSotetsegShareWhenTheKitLeavesItsHammerOut()
	{
		GearItem bow = weapon(25865, "bow of faerdhinen", "bow", 4, "accurate,ranged,accurate");
		bow.setRangedBonus(90);
		bow.setRangedStr(90);
		owned.add(bow.getId());
		double covered = plainDps(teamBoss("sotetseg (normal)", 4, true, false, CombatClass.MELEE, sword, helm), sword);
		// Owned but not searched: teammates don't make the two reducers this player owes, so the kit is slower ...
		assertTrue(plainDps(teamBoss("sotetseg (normal)", 4, true, true, CombatClass.MELEE, sword, helm), sword) < covered);
		// ... and so is a ranged kit: owning a reducer the player could switch to, it can't leech the teammates' either.
		assertTrue(plainDps(teamBoss("sotetseg (normal)", 4, true, true, CombatClass.RANGED, bow, helm), bow)
			< plainDps(teamBoss("sotetseg (normal)", 4, true, false, CombatClass.RANGED, bow, helm), bow));
	}

	@Test
	public void rangedAndMagicKitsSwitchToTheElderMaulForTheirShare()
	{
		GearItem bow = weapon(25865, "bow of faerdhinen", "bow", 4, "accurate,ranged,accurate");
		bow.setTwoHanded(true);
		bow.setRangedBonus(90);
		bow.setRangedStr(90);
		GearItem maul = weapon(21003, "elder maul", "blunt", 6, "pound,crush,accurate");
		maul.setTwoHanded(true);
		maul.setCrushBonus(135);
		maul.setMeleeStr(147);
		owned.addAll(Arrays.asList(bow.getId(), maul.getId()));
		for (String name : new String[]{"the maiden of sugadinti (normal)", "xarpus", "sotetseg (normal)"})
		{
			SetupResult best = withWeapon(teamBoss(name, 4, true, true, CombatClass.RANGED, bow, helm, maul), bow);
			assertTrue(name, best.getSpecials().stream().anyMatch(plan -> plan.getWeapon() == maul
				&& plan.getSpecsPerKill() >= 1 - 1e-9));
		}
		// No share outside a team-opened boss: a ranged kit doesn't carry a melee reducer there.
		SetupResult bloat = withWeapon(teamBoss("pestilent bloat (normal)", 4, true, true, CombatClass.RANGED, bow, helm,
			maul), bow);
		assertTrue(bloat.getSpecials().stream().noneMatch(plan -> plan.getWeapon() == maul));
	}

	@Test
	public void theShareIsAssignedEvenWhenAnInaccurateHammerLosesToDamageSpecs()
	{
		// Crush is all but useless here, so a free choice would spend the bar on claws instead.
		Monster sotetseg = target(200, 3000);
		sotetseg.setName("sotetseg (normal)");
		sotetseg.setDefCrush(400);
		GearItem warhammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate");
		owned.addAll(Arrays.asList(warhammer.getId(), claws.getId()));
		SetupResult best = withWeapon(teamBoss(sotetseg, 4, true, true, CombatClass.MELEE, sword, helm, warhammer, claws),
			sword);
		assertEquals(2, best.getSpecials().stream().filter(plan -> plan.getSpecial().getDrain() != SpecialAttack.Drain.NONE)
			.mapToDouble(SpecialPlan::getSpecsPerKill).sum(), 1e-9);
	}

	@Test
	public void everyTeamOpenedBossAssignsAFairShareSoNoKitLeechesTheTeamsReducers()
	{
		assertEquals(0, SpecialRotation.reducerShare("xarpus", 1));
		assertEquals(0, SpecialRotation.reducerShare("pestilent bloat (normal)", 4));
		assertEquals(2, SpecialRotation.reducerShare("sotetseg (normal)", 4));
		GearItem warhammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate");
		owned.addAll(Arrays.asList(warhammer.getId(), claws.getId()));
		for (String name : new String[]{"great olm (melee hand)", "tekton (normal)", "the maiden of sugadinti (normal)",
			"xarpus"})
		{
			assertEquals(name, 1, SpecialRotation.reducerShare(name, 4));
			// With every teammate's hammer already in, a free choice would spend the bar on claws instead.
			Monster boss = target(200, 3000);
			boss.setName(name);
			boss.setDefCrush(400);
			SetupResult best = withWeapon(teamBoss(boss, 4, true, true, CombatClass.MELEE, sword, helm, warhammer, claws),
				sword);
			assertTrue(name, best.getSpecials().stream().anyMatch(plan -> plan.getWeapon() == warhammer
				&& plan.getSpecsPerKill() >= 1 - 1e-9));
		}
	}

	@Test
	public void onlyAReducerThePlayerCouldUseOwesTheShare()
	{
		GearItem godsword = weapon(11804, "bandos godsword", "2h sword", 6, "chop,slash,accurate");
		godsword.setTwoHanded(true);
		godsword.setSlashBonus(132);
		godsword.setAttackReq(75);
		owned.add(godsword.getId());
		Monster sotetseg = target(200, 3000);
		sotetseg.setName("sotetseg (normal)");
		GameData data = TestData.gameData(Arrays.asList(sword, helm, godsword), Collections.emptyList());
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY).killSpecials(true)
			.raidKillShare(4).teamOpeners(true).excluded(Collections.singleton(godsword.getId()))
			.spellbooks(Collections.singleton("standard")).build();
		java.util.function.Function<PlayerLevels, Optimizer> optimizer = levels -> new Optimizer(data,
			new CombatContext(sotetseg, levels, false, true, piety()), settings, owned::contains, GearItem::getPrice);
		PlayerLevels low = new PlayerLevels(70, 99, 99, 99, 99, 99, 99, 99);
		Set<Integer> kitOnly = Collections.singleton(godsword.getId());
		// Left out by the kit planner it still owes the share; under its Attack requirement, or excluded by the player
		// (not ignored), it doesn't.
		assertTrue(optimizer.apply(PlayerLevels.maxed()).sharedReducerUsable(kitOnly));
		assertTrue(!optimizer.apply(low).sharedReducerUsable(kitOnly));
		assertTrue(!optimizer.apply(PlayerLevels.maxed()).sharedReducerUsable(Collections.emptySet()));
		owned.remove(godsword.getId());
		assertTrue(!optimizer.apply(PlayerLevels.maxed()).sharedReducerUsable(kitOnly));
	}

	@Test
	public void theOneReducerAThirdLimitAndWhichSpecsStandInForATeammate()
	{
		assertEquals(3, Optimizer.ownReducerLimit(4, 3));
		assertEquals(3, Optimizer.ownReducerLimit(2, 3));
		assertEquals(Integer.MAX_VALUE, Optimizer.ownReducerLimit(1, 3));
		assertEquals(Integer.MAX_VALUE, Optimizer.ownReducerLimit(4, 1));
		// An arclight spec is no teammate's hammer: two teammates still make the third's reducers.
		assertEquals(2, SpecialRotation.sotetsegTeammates(4, SpecialAttack.Drain.ARCLIGHT, 1, 0));
		assertEquals(1, SpecialRotation.sotetsegTeammates(4, SpecialAttack.Drain.ARCLIGHT, 1, 1));
		assertEquals(1, SpecialRotation.sotetsegTeammates(4, SpecialAttack.Drain.DRAGON_WARHAMMER, 1, 0));
		assertEquals(1, SpecialRotation.sotetsegTeammates(4, SpecialAttack.Drain.NONE, 0, 1));
	}

	@Test
	public void otherReducersLeaveSotetsegToTheTeamAndKeepTheBaselineSymmetric()
	{
		GearItem arclight = weapon(19675, "arclight", "slash sword", 4, "chop,slash,aggressive");
		arclight.setSlashBonus(70);
		arclight.setMeleeStr(72);
		owned.add(arclight.getId());
		List<SetupResult> results = teamBoss("sotetseg (normal)", true, CombatClass.MELEE, sword, helm, arclight);
		// With teammates making both reducers every third, the no-spec baseline matches a kit without any reducer.
		double alone = plainDps(teamBoss("sotetseg (normal)", true, CombatClass.MELEE, sword, helm), sword);
		assertEquals(alone, plainDps(results, sword), 1e-9);
		assertTrue(alone > plainDps(teamBoss("sotetseg (normal)", false, CombatClass.MELEE, sword, helm), sword));
		// An arclight spec takes no share and replaces no teammate's hammer: if used, it only adds to their two.
		SetupResult best = withWeapon(results, sword);
		assertTrue(best.getDps().getPrimaryDps() >= alone - 1e-9);
	}

	/** A raid search: specs over one player's share of each kill, with this share of kills followed by a pause. */
	private List<SetupResult> raid(Monster target, int share, double finalBlows, GearItem... items)
	{
		GameData data = TestData.gameData(Arrays.asList(items), Collections.emptyList());
		CombatContext ctx = new CombatContext(target, PlayerLevels.maxed(), false, true, piety());
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY).killSpecials(true)
			.specEnergy(SpecEnergy.FULL_BAR).raidKillShare(share).finalBlows(finalBlows)
			.spellbooks(Collections.singleton("standard")).build();
		return new Optimizer(data, ctx, settings, owned::contains, GearItem::getPrice).optimize(CombatClass.MELEE,
			() -> false);
	}

	private GearItem halberd(int strength)
	{
		GearItem halberd = weapon(23987, "crystal halberd", "polearm", 7, "jab,stab,controlled", "swipe,slash,aggressive");
		halberd.setTwoHanded(true);
		halberd.setStabBonus(110);
		halberd.setSlashBonus(110);
		halberd.setMeleeStr(strength);
		owned.add(halberd.getId());
		return halberd;
	}

	@Test
	public void raidSpecsAreWorthMoreOverOnePlayersShareOfTheKill()
	{
		owned.add(claws.getId());
		Monster boss = target(100, 2000);
		SetupResult alone = withWeapon(raid(boss, 1, 0, sword, claws), sword);
		SetupResult party = withWeapon(raid(boss, 4, 0, sword, claws), sword);
		// The same bar over a quarter of the HP is a larger part of the kill.
		assertTrue(party.getDps().getDps() > alone.getDps().getDps());
		assertTrue(party.getSpecial().getSpecsPerKill() < alone.getSpecial().getSpecsPerKill());
		assertTrue(alone.usesFullBar());
		assertTrue(alone.getSpecial().getRegenerated().getDps() < alone.getDps().getDps());
	}

	@Test
	public void theLastAttackOfAKillFollowedByAPauseIsFree()
	{
		Monster boss = target(100, 2000);
		SetupResult endless = withWeapon(raid(boss, 4, 0, sword, helm), sword);
		SetupResult paused = withWeapon(raid(boss, 4, 1, sword, helm), sword);
		assertTrue(paused.getDps().getDps() > endless.getDps().getDps());
	}

	@Test
	public void raidNeverCarriesTwoDamageSpecialsForOneEncounter()
	{
		Monster boss = target(100, 3000); boss.setSize(3);
		GearItem halberd = halberd(150); owned.add(claws.getId());
		for (SetupResult result : raid(boss, 4, 1, sword, claws, halberd))
		{
			assertTrue(result.getSpecials().size() <= 2);
			assertTrue(result.getSpecials().stream().filter(plan -> plan.getSpecial().getDrain() == SpecialAttack.Drain.NONE).count() <= 1);
		}
	}

	@Test
	public void raidReturnsAlternateSpecWeaponsAndAnOrdinaryVersionOfTheSameGear()
	{
		Monster boss = target(100, 3000); boss.setSize(3);
		GearItem halberd = halberd(150); owned.add(claws.getId());
		List<SetupResult> results = raid(boss, 4, 0, sword, claws, halberd);
		List<SetupResult> sameGear = results.stream().filter(r -> r.getLoadout().getWeapon() == sword)
			.collect(java.util.stream.Collectors.toList());
		assertTrue(sameGear.stream().anyMatch(r -> r.getSpecial() == null));
		assertTrue(sameGear.stream().flatMap(r -> r.getSpecials().stream()).map(p -> p.getWeapon().getId()).distinct().count() >= 2);
	}

	@Test
	public void maidenNylocasReserveAllSpecialEnergyForTheBoss()
	{
		Monster add = target(20, 175); add.setName("nylocas matomenos (normal mode maiden)");
		owned.add(claws.getId());
		for (SetupResult result : raid(add, 1, 0, sword, claws)) { assertNull(result.getSpecial()); }
	}

	@Test
	public void sharedBossDrainsBeatPureDamageForRepresentativeArmouredTargets()
	{
		GearItem hammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate");
		hammer.setCrushBonus(95); hammer.setMeleeStr(85); owned.add(hammer.getId()); owned.add(claws.getId());
		for (String name : new String[]{"great olm (melee hand)", "the maiden of sugadinti (normal)", "sotetseg (normal)"})
		{
			Monster boss = target(300, 3000); boss.setName(name);
			SetupResult best = withWeapon(raid(boss, 4, 0, sword, hammer, claws), sword);
			assertTrue(name, best.getSpecials().stream().anyMatch(plan -> plan.getSpecial().getDrain() != SpecialAttack.Drain.NONE));
			assertTrue(name, best.getSpecials().size() <= 2);
			assertTrue(name, best.getSpecial().getOpeningEnergy() <= 100);
			SetupResult damageOnly = withWeapon(raid(boss, 4, 0, sword, claws), sword);
			assertTrue(name, best.getDps().getDps() > damageOnly.getDps().getDps());
			System.out.println(name + ": ordinary=" + best.getSpecial().getOrdinary().getDps() + " rotation=" + best.getDps().getDps()
				+ " damageOnly=" + damageOnly.getDps().getDps()
				+ " specs=" + best.getSpecials().stream().map(plan -> plan.getWeapon().getName() + " x " + plan.getSpecsPerKill()).collect(java.util.stream.Collectors.joining(", ")));
		}
	}

	@Test
	public void raidRegeneratedEnergyNeverFundsAPhaseEntryDrain()
	{
		GearItem hammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate");
		hammer.setCrushBonus(95); hammer.setMeleeStr(85); owned.add(hammer.getId());
		Monster boss = target(300, 3000); boss.setName("great olm (melee hand)");
		List<SetupResult> candidates = raid(boss, 4, 0, sword, hammer);
		assertTrue(candidates.stream().filter(r -> r.getLoadout().getWeapon() == sword && r.getSpecial() != null)
			.anyMatch(r -> r.getSpecial().getOpeningEnergy() == 50));
		assertTrue(candidates.stream().filter(r -> r.getLoadout().getWeapon() == sword && r.getSpecial() != null)
			.anyMatch(r -> r.getSpecial().getOpeningEnergy() == 100));
		for (SetupResult setup : candidates) { assertNull(setup.withRegeneratedEnergy().getSpecial()); }
	}

	@Test
	public void bloatAllowsADrainButOnlyCreditsTheFirstDown()
	{
		GearItem hammer = weapon(13576, "dragon warhammer", "blunt", 6, "pound,crush,accurate");
		hammer.setCrushBonus(95); hammer.setMeleeStr(85); owned.add(hammer.getId());
		Monster bloat = target(300, 3000); bloat.setName("pestilent bloat (normal)");
		SetupResult shortWindow = withWeapon(raid(bloat, 4, 0, sword, hammer), sword);
		SetupResult lasting = withWeapon(raid(target(300, 3000), 4, 0, sword, hammer), sword);
		assertNotNull(shortWindow.getSpecial());
		assertNotNull(shortWindow.getSpecial().getDrained());
		assertTrue(shortWindow.getDps().getDps() < lasting.getDps().getDps());
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
