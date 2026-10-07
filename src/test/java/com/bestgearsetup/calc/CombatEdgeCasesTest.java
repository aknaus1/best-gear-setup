package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import com.google.gson.Gson;
import org.junit.Test;

/** Boundary cases that must survive changes to modifier order and catalog identity. */
public class CombatEdgeCasesTest
{
	private static final double EPS = 1e-10;

	private static Loadout loadout(String name, String category, String style)
	{
		GearItem w = weapon(50000, name, category, 5, style);
		w.setMeleeStr(100);
		w.setRangedStr(100);
		w.setStabBonus(100);
		w.setSlashBonus(100);
		w.setCrushBonus(100);
		w.setRangedBonus(100);
		w.setMagicBonus(100);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, w);
		l.setStyle(AttackStyle.parse(style));
		return l;
	}

	private static CombatContext context(Monster m)
	{
		return new CombatContext(m, PlayerLevels.maxed(), false, false, null);
	}

	private static Loadout crossbow(String ammo)
	{
		Loadout l = loadout("rune crossbow", "crossbow", "rapid,ranged,rapid");
		l.set(Slot.AMMO, item(50001, ammo, Slot.AMMO));
		return l;
	}

	private static double ordinary(int max)
	{
		return max / 2.0 + 1.0 / (max + 1);
	}

	@Test
	public void diamondProcsOnMissesAndDiaryScalesProcProbability()
	{
		Loadout l = crossbow("diamond bolts (e)");
		CombatContext ctx = context(monster(100, 50));
		EnchantedBolts.Result result = EnchantedBolts.calculate(l, ctx, EncounterDamage.unmitigated(), 0.25, 0, 20);
		assertEquals(0.9 * 0.25 * ordinary(20) + 0.1 * ordinary(23), result.getAverage(), EPS);
		assertEquals(0.325, result.getAccuracy(), EPS);
		assertEquals(23, result.getMaximum());
		ctx = ctx.withModifiers(CombatModifiers.builder().kandarinDiary(true).build());
		result = EnchantedBolts.calculate(l, ctx, EncounterDamage.unmitigated(), 0.25, 0, 20);
		assertEquals(0.89 * 0.25 * ordinary(20) + 0.11 * ordinary(23), result.getAverage(), EPS);
	}

	@Test
	public void opalAddsDamageEvenOnMissesWithoutTurningMissesAccurate()
	{
		Loadout l = crossbow("opal bolts (e)");
		EnchantedBolts.Result result = EnchantedBolts.calculate(l, context(monster(100, 50)),
			EncounterDamage.unmitigated(), 0.25, 0, 20);
		assertEquals(0.95 * 0.25 * ordinary(20) + 0.05 * (0.25 * 19 + 0.75 * 9), result.getAverage(), EPS);
		assertEquals(0.25, result.getAccuracy(), EPS);
		assertEquals(29, result.getMaximum());
	}

	@Test
	public void opalRetainsBoundedWardenDamageRolls()
	{
		EnchantedBolts.Result result = EnchantedBolts.calculate(crossbow("opal bolts (e)"), context(monster(100, 50)),
			EncounterDamage.unmitigated(), 1, 6, 11);
		assertEquals(0.95 * 8.5 + 0.05 * 17.5, result.getAverage(), EPS);
	}

	@Test
	public void rubyUsesHealthSnapshotAndRequiresAtLeastTenPlayerHp()
	{
		Monster m = monster(100, 50);
		m.setHitpoints(1000);
		Loadout l = crossbow("ruby bolts (e)");
		CombatContext ctx = context(m);
		EnchantedBolts.Result result = EnchantedBolts.calculate(l, ctx, EncounterDamage.unmitigated(), 0, 0, 20);
		assertEquals(6, result.getAverage(), EPS);
		assertEquals(0.06, result.getAccuracy(), EPS);
		assertEquals(100, result.getMaximum());
		ctx = ctx.withModifiers(CombatModifiers.builder().monsterHitpoints(100).currentHitpoints(10).build());
		result = EnchantedBolts.calculate(l, ctx, EncounterDamage.unmitigated(), 0, 0, 20);
		assertEquals(1.2, result.getAverage(), EPS);
		assertEquals(20, result.getMaximum());
		assertNull(EnchantedBolts.calculate(l, ctx.withModifiers(CombatModifiers.builder().currentHitpoints(9).build()),
			EncounterDamage.unmitigated(), 0.25, 0, 20));
	}

	@Test
	public void zaryteRaisesRubyCapAndOtherBoltStrength()
	{
		Monster m = monster(100, 50);
		m.setHitpoints(1000);
		Loadout l = crossbow("ruby dragon bolts (e)");
		l.getWeapon().setName("zaryte crossbow");
		assertEquals(110, EnchantedBolts.calculate(l, context(m), EncounterDamage.unmitigated(), 0, 0, 20).getMaximum());
		l.get(Slot.AMMO).setName("diamond dragon bolts (e)");
		assertEquals(25, EnchantedBolts.calculate(l, context(m), EncounterDamage.unmitigated(), 0, 0, 20).getMaximum());
	}

	@Test
	public void rubyBypassesCorpHalvingButRespectsZulrahReroll()
	{
		Monster m = monster(100, 50);
		m.setHitpoints(1000);
		m.setName("corporeal beast");
		Loadout l = crossbow("ruby bolts (e)");
		EnchantedBolts.Result result = EnchantedBolts.calculate(l, context(m), EncounterDamage.rule(m, l), 0.25, 0, 20);
		double halfMean = 0;
		for (int roll = 0; roll <= 20; roll++)
		{
			halfMean += Math.max(1, roll) / 2;
		}
		assertEquals(0.94 * 0.25 * halfMean / 21 + 6, result.getAverage(), EPS);
		m.setName("zulrah");
		result = EnchantedBolts.calculate(l, context(m), EncounterDamage.rule(m, l), 0, 0, 20);
		assertEquals(0.06 * 47.5, result.getAverage(), EPS);
		assertEquals(50, result.getMaximum());
	}

	@Test
	public void boltImmunitiesDoNotAddBlockedProcDamage()
	{
		assertNull(EnchantedBolts.calculate(crossbow("onyx bolts (e)"), context(monster(100, 50, "undead")),
			EncounterDamage.unmitigated(), 0.25, 0, 20));
		for (String tag : new String[]{"dragon", "fiery", "dragonfire immune"})
		{
			assertNull(EnchantedBolts.calculate(crossbow("dragonstone bolts (e)"), context(monster(100, 50, tag)),
				EncounterDamage.unmitigated(), 0.25, 0, 20));
		}
		assertNull(EnchantedBolts.calculate(crossbow("emerald bolts (e)"), context(monster(100, 50)),
			EncounterDamage.unmitigated(), 0.25, 0, 20));
	}

	@Test
	public void tormentedUnshieldedRewardsCrushHeavyAndCastSpellsOnly()
	{
		Monster m = monster(100, 50, "demon");
		m.setName("tormented demon (unshielded)");
		Loadout l = loadout("rune warhammer", "blunt", "pummel,crush,aggressive");
		DpsResult result = DpsCalculator.calculate(l, context(m));
		assertEquals(1, result.getAccuracy(), EPS);
		assertEquals(23, result.getAverageHit(), EPS);
		assertEquals(37, result.getMaxHit());
		assertEquals(4, result.getExpectedSpeedTicks(), EPS);
		l.setStyle(AttackStyle.parse("slash,slash,aggressive"));
		assertEquals(5, DpsCalculator.calculate(l, context(m)).getExpectedSpeedTicks(), EPS);
		l = crossbow("rune bolts");
		l.getWeapon().setAttackSpeed(6);
		result = DpsCalculator.calculate(l, context(m));
		assertEquals(22.5, result.getAverageHit(), EPS);
		assertEquals(4, result.getExpectedSpeedTicks(), EPS);
		l = loadout("trident of the seas", "powered staff", "accurate,magic,accurate");
		assertEquals(5, DpsCalculator.calculate(l, context(m)).getExpectedSpeedTicks(), EPS);
		l = loadout("staff of fire", "staff", "spell,magic,magic");
		Spell spell = new Spell();
		spell.setName("fire wave");
		spell.setSpellbook("standard");
		spell.setMaxHit(20);
		l.setSpell(spell);
		result = DpsCalculator.calculate(l, context(m));
		assertEquals(19, result.getAverageHit(), EPS);
		assertEquals(4, result.getExpectedSpeedTicks(), EPS);
	}

	@Test
	public void salamandersUseBuiltInMagicAndCannotSafespot()
	{
		Loadout l = loadout("black salamander", "salamander", "blaze,magic,magic");
		assertEquals(24, DpsCalculator.calculate(l, context(monster(100, 50))).getMaxHit());
		assertEquals(0, DpsCalculator.calculate(l, context(monster(100, 50)).withFightOptions(1, 2)).getDps(), EPS);
		Monster aviansie = monster(100, 50, "flying");
		aviansie.setName("aviansie");
		l.setStyle(AttackStyle.parse("scorch,slash,aggressive"));
		assertTrue(DpsCalculator.calculate(l, context(aviansie)).getDps() > 0);
	}

	@Test
	public void hueyCrushMissesDealOneAndBothConditionalSplatsAreCapped()
	{
		Monster m = monster(100, 50);
		m.setId(14014);
		Loadout l = loadout("dual macuahuitl", "blunt", "pummel,crush,aggressive");
		l.getWeapon().setCrushBonus(101);
		DpsResult result = DpsCalculator.calculate(l, context(m));
		double a = result.getAccuracy();
		double mean = 0;
		for (int roll = 0; roll <= 14; roll++)
		{
			for (int cap = 0; cap <= 9; cap++)
			{
				mean += Math.max(1, Math.min(Math.max(1, roll), cap));
			}
		}
		mean /= 150;
		assertEquals(a * mean + (1 - a) + a * a * mean + (1 - a * a), result.getAverageHit(), EPS);
		assertEquals(18, result.getMaxHit());
	}

	@Test
	public void gauntletAndToaVariantsStayInTheirActivity()
	{
		Monster ordinary = monster(100, 50);
		Monster hunllef = monster(100, 50, "crystalline");
		hunllef.setId(9021);
		GearItem activity = weapon(50002, "crystal staff (perfected)", "powered staff", 4, "accurate,magic,accurate");
		assertFalse(CombatRules.equipmentAllowed(ordinary, activity));
		assertTrue(CombatRules.equipmentAllowed(hunllef, activity));
		assertFalse(CombatRules.equipmentAllowed(hunllef, weapon(50003, "tumeken's shadow", "powered staff", 5)));
		GearItem keris = weapon(50004, "keris partisan of amascut (inside toa)", "stab sword", 4);
		assertFalse(CombatRules.equipmentAllowed(ordinary, keris));
		assertTrue(CombatRules.equipmentAllowed(monster(100, 50, "tombs of amascut"), keris));
		assertEquals(0, DpsCalculator.calculate(crossbow("rune bolts"), context(hunllef)).getDps(), EPS);
		Loadout uncharged = loadout("craw's bow (u)", "bow", "rapid,ranged,rapid");
		assertEquals(0, DpsCalculator.calculate(uncharged, context(ordinary)).getDps(), EPS);
	}

	@Test
	public void realApiFlatFieldIsRetainedInCopies()
	{
		Monster m = new Gson().fromJson("{\"id\":13012,\"name\":\"eclipse moon\",\"flat\":6}", Monster.class);
		assertEquals(Integer.valueOf(6), m.getFlatArmour());
		assertEquals(Integer.valueOf(6), m.copy().getFlatArmour());
	}

	@Test
	public void vardorvisHealthScalingIsAppliedBeforeDrainImmunity()
	{
		Monster m = monster(215, 50);
		m.setName("vardorvis (post-quest)");
		m.setHitpoints(700);
		m.setStrengthLevel(270);
		Monster half = MonsterStates.atHealth(m, 350);
		assertEquals(180, half.getDefenceLevel());
		assertEquals(315, half.getStrengthLevel());
		assertEquals(180, SpecialAttacks.builder().dragonWarhammer(10).bandosGodswordDamage(1000).build()
			.apply(half).getDefenceLevel());
		assertEquals(215, m.getDefenceLevel());
		assertEquals(145, MonsterStates.atHealth(m, 1).getDefenceLevel());
	}

	@Test
	public void absorptionAndDefenceFloorsDoNotPropagateGodswordDrain()
	{
		Monster m = monster(200, 50, "absorption");
		m.setStrengthLevel(200);
		assertEquals(200, SpecialAttacks.builder().dragonWarhammer(10).ayakDamage(100).build().apply(m).getDefenceLevel());
		m = monster(200, 50);
		m.setStrengthLevel(200);
		m.setDefenceFloor(150);
		Monster result = SpecialAttacks.builder().bandosGodswordDamage(1000).build().apply(m);
		assertEquals(150, result.getDefenceLevel());
		assertEquals(200, result.getStrengthLevel());
		m.setDefMagic(50);
		assertEquals(0, SpecialAttacks.builder().ayakDamage(100).build().apply(m).getDefMagic());
		m.setDefMagic(-20);
		assertEquals(-20, SpecialAttacks.builder().ayakDamage(100).build().apply(m).getDefMagic());
	}

	@Test
	public void akkhaCanStillBeDrainedToItsDocumentedFloorDespiteBroadApiTag()
	{
		Monster m = monster(80, 50, "absorption", "tombs of amascut");
		m.setName("akkha");
		assertEquals(70, SpecialAttacks.builder().dragonWarhammer(10).build().apply(m).getDefenceLevel());
	}

	@Test
	public void actualEclipseCloneVariantIsMeleeOnlyAccurateAndHasFourArmour()
	{
		Monster m = monster(100, 50);
		m.setName("eclipse moon (clone)");
		m.setId(13014);
		Loadout l = loadout("rune sword", "stab sword", "slash,slash,aggressive");
		DpsResult result = DpsCalculator.calculate(l, context(m));
		assertEquals(1, result.getAccuracy(), EPS);
		assertEquals(24, result.getMaxHit());
		assertEquals(300.0 / 29, result.getAverageHit(), EPS);
		assertEquals(0, DpsCalculator.calculate(crossbow("rune bolts"), context(m)).getDps(), EPS);
	}

	@Test
	public void twistedBowAppliesTaskDamageBeforeMagicScaling()
	{
		Loadout l = loadout("twisted bow", "bow", "rapid,ranged,rapid");
		l.getWeapon().setRangedStr(38);
		l.set(Slot.HEAD, item(50005, "slayer helmet (i)", Slot.HEAD));
		Monster m = monster(100, 50);
		m.setMagicLevel(100);
		CombatContext ctx = new CombatContext(m, PlayerLevels.maxed(), true, false, null);
		// Base 17 -> task 19 -> 131% Twisted bow multiplier = 24. Reversing order incorrectly gives 25.
		assertEquals(24, DpsCalculator.calculate(l, ctx).getMaxHit());
	}

	@Test
	public void emptyWaterTomeDoesNotSupplyChargedAccuracyOrDamage()
	{
		Loadout l = loadout("ordinary staff", "staff", "spell,magic,magic");
		Spell spell = new Spell();
		spell.setName("water surge");
		spell.setSpellbook("standard");
		spell.setMaxHit(24);
		l.setSpell(spell);
		Monster m = monster(100, 50);
		m.setMagicLevel(100);
		DpsResult baseline = DpsCalculator.calculate(l, context(m));
		l.set(Slot.SHIELD, item(50006, "tome of water (empty)", Slot.SHIELD));
		DpsResult empty = DpsCalculator.calculate(l, context(m));
		assertEquals(baseline.getDps(), empty.getDps(), EPS);
		l.get(Slot.SHIELD).setName("tome of water");
		assertTrue(DpsCalculator.calculate(l, context(m)).getDps() > empty.getDps());
	}

	@Test
	public void brimstoneAndConflictionCombineTheirAccuracyDistributions()
	{
		Loadout l = loadout("trident of the seas", "powered staff", "accurate,magic,accurate");
		l.set(Slot.HANDS, item(50007, "confliction gauntlets", Slot.HANDS));
		l.set(Slot.RING, item(50008, "brimstone ring", Slot.RING));
		Monster m = monster(100, 50);
		m.setMagicLevel(100);
		long attack = 110 * 164;
		double expected = 0;
		for (int defence : new int[]{12426, 11183})
		{
			double single = 1 - (defence + 2.0) / (2 * (attack + 1));
			double twice = 1 - (defence + 2.0) * (2.0 * defence + 3) / (6 * (attack + 1) * (attack + 1));
			expected += (defence == 12426 ? 0.75 : 0.25) * twice / (1 + twice - single);
		}
		assertEquals(expected, DpsCalculator.calculate(l, context(m)).getAccuracy(), EPS);
	}

	@Test
	public void inquisitorBodyAndLegsCountTwiceTheHelmBonus()
	{
		Loadout l = loadout("rune warhammer", "blunt", "pummel,crush,aggressive");
		Monster m = monster(100, 50);
		l.set(Slot.BODY, item(50009, "inquisitor's hauberk", Slot.BODY));
		double expected = 1 - 12428.0 / (2 * (17723 + 1));
		assertEquals(expected, DpsCalculator.calculate(l, context(m)).getAccuracy(), EPS);
	}
}
