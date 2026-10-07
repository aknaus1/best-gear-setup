package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import java.util.Arrays;
import org.junit.Test;

/** Special attack scoring (used for specs within a kill): vectors derived from the Wiki calculator's spec formulas. */
public class SpecialAttackTest
{
	private static final double EPS = 1e-10;
	private static final CombatModifiers SPEC = CombatModifiers.builder().specialAttack(true).build();

	/** Mean of a uniform 0..max roll with a successful zero raised to one. */
	private static double ordinary(long max)
	{
		return max / 2.0 + 1.0 / (max + 1);
	}

	private static Loadout loadout(GearItem weapon, String style)
	{
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, weapon);
		l.setStyle(AttackStyle.parse(style));
		return l;
	}

	private static CombatContext context(Monster m, CombatModifiers modifiers)
	{
		return new CombatContext(m, PlayerLevels.maxed(), false, false, null).withModifiers(modifiers);
	}

	private static GearItem melee(String name, int bonus, int strength, int speed)
	{
		GearItem w = weapon(50000, name, "slash sword", speed, "slash,slash,aggressive", "lunge,stab,aggressive");
		w.setStabBonus(bonus);
		w.setSlashBonus(bonus);
		w.setCrushBonus(bonus);
		w.setMeleeStr(strength);
		return w;
	}

	@Test
	public void armadylGodswordAppliesGodswordThenOwnMultiplierWithDoubleAccuracy()
	{
		Loadout l = loadout(melee("armadyl godsword", 132, 132, 6), "slash,slash,aggressive");
		DpsResult r = DpsCalculator.calculate(l, context(monster(100, 50), SPEC));
		// Max (110 * 196 + 320) / 640 = 34; x 11/10 = 37; x 5/4 = 46. Attack roll 107 * 196, doubled.
		assertEquals(46, r.getMaxHit());
		double acc = HitChance.single(2L * 107 * 196, 109L * 114);
		assertEquals(acc, r.getAccuracy(), EPS);
		assertEquals(acc * ordinary(46), r.getAverageHit(), EPS);
		assertEquals(6, r.getAttackSpeedTicks());
		assertTrue(r.getMaxHitDetail().contains("50% energy"));
	}

	@Test
	public void specDefenceStyleOverridesTheSelectedStance()
	{
		Monster target = monster(100, 0);
		target.setDefStab(300);
		target.setDefSlash(-20);
		// Dragon dagger rolls against slash even from a stab stance, and hits twice.
		GearItem dds = melee("dragon dagger(p++)", 40, 40, 4);
		DpsResult r = DpsCalculator.calculate(loadout(dds, "lunge,stab,aggressive"), context(target, SPEC));
		long attack = 107L * 104 * 23 / 20;
		double acc = HitChance.single(attack, 109L * 44);
		long max = (110L * 104 + 320) / 640 * 23 / 20;
		assertEquals(acc, r.getAccuracy(), EPS);
		assertEquals(2 * acc * ordinary(max), r.getAverageHit(), EPS);
		assertEquals(2 * max, r.getMaxHit());
	}

	@Test
	public void dragonClawsFollowTheCascadeDistribution()
	{
		Loadout l = loadout(melee("dragon claws", 57, 56, 4), "slash,slash,aggressive");
		Monster target = monster(150, 80);
		DpsResult r = DpsCalculator.calculate(l, context(target, SPEC));
		long max = (110L * 120 + 320) / 640;
		double acc = HitChance.single(107L * 121, 159L * 144);
		double expected = 0;
		for (int k = 0; k < 4; k++)
		{
			long low = max * (4 - k) / 4;
			long high = max + low - 1;
			for (long d = low; d <= high; d++)
			{
				long sum = k == 0 ? d / 2 + d / 4 + d / 8 + d / 8 + 1 : k == 1 ? d / 2 + d / 4 + d / 4 + 1
					: k == 2 ? d / 2 + d / 2 + 1 : d + 1;
				expected += Math.pow(1 - acc, k) * acc / (high - low + 1) * sum;
			}
		}
		expected += Math.pow(1 - acc, 4) * 2.0 / 3 * 2;
		assertEquals(expected, r.getAverageHit(), 1e-9);
		assertEquals(1 - Math.pow(1 - acc, 4), r.getAccuracy(), EPS);
	}

	@Test
	public void voidwakerAlwaysHitsFor50To150PercentAsMagic()
	{
		GearItem voidwaker = melee("voidwaker", 70, 80, 4);
		Loadout l = loadout(voidwaker, "slash,slash,aggressive");
		Monster target = monster(300, 300);
		DpsResult r = DpsCalculator.calculate(l, context(target, SPEC));
		long max = (110L * 144 + 320) / 640;
		assertEquals(1.0, r.getAccuracy(), EPS);
		assertEquals(max + max / 2, r.getMaxHit());
		assertEquals((max / 2 + max + max / 2) / 2.0, r.getAverageHit(), EPS);
		// Mitigated as Magic: Tekton divides Magic by five, with positive hits kept at one.
		target.setId(7540);
		target.setName("tekton (normal)");
		target.setDefMagic(0);
		assertTrue(DpsCalculator.calculate(l, context(target, SPEC)).getAverageHit() < max * 0.31);
	}

	@Test
	public void darkBowMissesStillDealTheArrowMinimumAndHitsCapAt48()
	{
		GearItem bow = weapon(11235, "dark bow", "bow", 9, "rapid,ranged,rapid");
		bow.setAmmunition(Arrays.asList(11212, 882));
		bow.setRangedBonus(95);
		Loadout l = loadout(bow, "rapid,ranged,rapid");
		GearItem dragon = item(11212, "dragon arrow", Slot.AMMO);
		dragon.setRangedStr(60);
		l.set(Slot.AMMO, dragon);
		Monster target = monster(200, 200);
		DpsResult r = DpsCalculator.calculate(l, context(target, SPEC));
		long max = (107L * 124 + 320) / 640 * 15 / 10;
		double acc = HitChance.single(107L * 159, 209L * 264);
		double hit = 0;
		for (long h = 8; h <= max; h++)
		{
			hit += Math.max(8, Math.min(48, h));
		}
		hit /= max - 8 + 1;
		assertEquals(2 * (acc * hit + (1 - acc) * 8), r.getAverageHit(), 1e-9);
		assertEquals(2 * Math.min(48, max), r.getMaxHit());
	}

	@Test
	public void zarytCrossbowSpecGuaranteesBoltEffectsOnAccurateHits()
	{
		GearItem zcb = weapon(26374, "zaryte crossbow", "crossbow", 5, "rapid,ranged,rapid");
		zcb.setAmmunition(Arrays.asList(9243));
		zcb.setRangedBonus(110);
		GearItem diamond = item(9243, "diamond bolts (e)", Slot.AMMO);
		diamond.setRangedStr(105);
		Loadout l = loadout(zcb, "rapid,ranged,rapid");
		l.set(Slot.AMMO, diamond);
		Monster target = monster(150, 80);
		CombatContext ctx = context(target, SPEC);
		long max = (107L * 169 + 320) / 640;
		long effect = max * 126 / 100;
		double acc = HitChance.single(2L * 107 * 174, 159L * 144);
		DpsResult r = DpsCalculator.calculate(l, ctx);
		assertEquals(acc * ordinary(effect) + (1 - acc) * 0.1 * ordinary(effect), r.getAverageHit(), EPS);
		assertTrue(r.getMaxHitDetail().contains("guaranteed on hit"));
	}

	@Test
	public void accursedSceptreAutocastSpecUsesItsBuiltInSpell()
	{
		GearItem sceptre = weapon(27679, "accursed sceptre (a)", "staff", 5, "bash,crush,accurate", "spell,magic,magic");
		sceptre.setMagicBonus(22);
		Loadout l = loadout(sceptre, "spell,magic,magic");
		DpsResult r = DpsCalculator.calculate(l, context(monster(100, 50), SPEC));
		// Built-in max 99 / 3 - 6 = 27, x 3/2 = 40; accurate stance +2 and spec accuracy x 3/2.
		assertEquals(40, r.getMaxHit());
		assertEquals(HitChance.single((99L + 2 + 9) * 86 * 3 / 2, 109L * 114), r.getAccuracy(), EPS);
		// The staff's bash has no melee special.
		assertNull(SpecialAttack.of(loadout(sceptre, "bash,crush,accurate"), context(monster(100, 50), SPEC)));
	}

	@Test
	public void unsupportedWeaponsScoreNothing()
	{
		CombatContext ctx = context(monster(100, 50), SPEC);
		GearItem scythe = melee("scythe of vitur", 125, 75, 5);
		assertEquals(0, DpsCalculator.calculate(loadout(scythe, "slash,slash,aggressive"), ctx).getDps(), EPS);
		GearItem other = weapon(11785, "unlisted crossbow", "crossbow", 6, "rapid,ranged,rapid");
		assertNull(SpecialAttack.of(loadout(other, "rapid,ranged,rapid"), ctx));
		// Ordinary attacks are unchanged when the mode is off.
		assertFalse(CombatModifiers.NONE.isSpecialAttack());
	}

	@Test
	public void specialsFollowTheirWikiPages()
	{
		CombatContext ctx = context(monster(100, 50), SPEC);
		// Armadyl Eye doubles accuracy.
		SpecialAttack acb = SpecialAttack.of(loadout(weapon(11785, "armadyl crossbow", "crossbow", 6,
			"rapid,ranged,rapid"), "rapid,ranged,rapid"), ctx);
		assertEquals(400, acb.scaleAccuracy(200));
		// Binding Tentacle has no accuracy bonus.
		SpecialAttack tentacle = SpecialAttack.of(loadout(melee("abyssal tentacle", 82, 86, 4), "slash,slash,aggressive"), ctx);
		assertEquals(200, tentacle.scaleAccuracy(200));
		assertEquals(40, tentacle.scaleMax(40));
		// Smash: 25-125% of the maximum.
		SpecialAttack smash = SpecialAttack.of(loadout(melee("statius's warhammer (bh)", 0, 114, 6),
			"slash,slash,aggressive"), ctx);
		assertEquals(50, smash.scaleMax(40));
		assertEquals(10, smash.minimum(40));
		// Impale: +10% damage and one tick slower.
		SpecialAttack impale = SpecialAttack.of(loadout(melee("rune claws", 38, 39, 4), "slash,slash,aggressive"), ctx);
		assertEquals(44, impale.scaleMax(40));
		assertEquals(1, impale.getExtraTicks());
		assertEquals(5, DpsCalculator.calculate(loadout(melee("rune claws", 38, 39, 4), "slash,slash,aggressive"), ctx)
			.getExpectedSpeedTicks(), EPS);
	}

	@Test
	public void burningClawDotMatchesTheReferenceBurnTable()
	{
		// The roll that succeeds sets a 15/30/45% burn chance per splat; overlapping first two burns lose 1.
		assertEquals(expected(0.15), SpecialDamage.burningClawDot(1), EPS);
		assertEquals(0.5 * expected(0.15) + 0.25 * expected(0.30) + 0.125 * expected(0.45),
			SpecialDamage.burningClawDot(0.5), EPS);
		assertEquals(0, SpecialDamage.burningClawDot(0), EPS);
	}

	private static double expected(double chance)
	{
		double total = 0;
		for (int row = 0; row < 8; row++)
		{
			int burns = Integer.bitCount(row);
			total += Math.pow(chance, burns) * Math.pow(1 - chance, 3 - burns) * (10 * burns - ((row & 6) == 6 ? 1 : 0));
		}
		return total;
	}

	@Test
	public void seekingBroadArrowsRaiseSuccessfulHitsToThree()
	{
		GearItem bow = weapon(50010, "test bow", "bow", 4, "rapid,ranged,rapid");
		bow.setAmmunition(Arrays.asList(50011));
		bow.setRangedBonus(50);
		GearItem arrows = item(50011, "seeking broad arrows", Slot.AMMO);
		arrows.setRangedStr(-40);
		Loadout l = loadout(bow, "rapid,ranged,rapid");
		l.set(Slot.AMMO, arrows);
		DpsResult r = DpsCalculator.calculate(l, context(monster(1, 0), CombatModifiers.NONE));
		long max = (107L * 24 + 320) / 640;
		double acc = HitChance.single(107L * 114, 10L * 64);
		double mean = 0;
		for (long h = 0; h <= max; h++)
		{
			mean += Math.max(3, h);
		}
		assertEquals(acc * mean / (max + 1), r.getAverageHit(), EPS);
	}
}
