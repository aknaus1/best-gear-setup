package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.util.Collections;
import org.junit.Test;

public class DpsCalculatorTest
{
	private static final PlayerLevels MAXED = PlayerLevels.maxed();

	private static Loadout meleeLoadout(GearItem weapon, String style)
	{
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, weapon);
		l.setStyle(AttackStyle.parse(style));
		return l;
	}

	@Test
	public void meleeMaxHitMatchesWikiFormula()
	{
		// 99 Str + super combat = 118; piety x1.23 = 145; +3 aggressive +8 = 156.
		// max = floor((156 * (130 + 64) + 320) / 640) = 47
		GearItem w = weapon(1, "test sword", "slash sword", 4, "chop,slash,aggressive");
		w.setMeleeStr(130);
		CombatContext ctx = new CombatContext(monster(1, 0), MAXED, false, true, piety());
		DpsResult r = DpsCalculator.calculate(meleeLoadout(w, "chop,slash,aggressive"), ctx);
		assertEquals(47, r.getMaxHit());
		assertEquals(4, r.getAttackSpeedTicks());
	}

	@Test
	public void krakenRangedReductionRoundsEachHitAndKeepsPositiveHitsAtOne()
	{
		GearItem bow = weapon(861, "magic shortbow", "bow", 4, "rapid,ranged,rapid");
		bow.setRangedBonus(70);
		Loadout l = meleeLoadout(bow, "rapid,ranged,rapid");
		for (int strength : new int[]{-50, 0, 30, 100, 170})
		{
			bow.setRangedStr(strength);
			Monster ordinary = monster(100, 20);
			DpsResult normal = DpsCalculator.calculate(l, new CombatContext(ordinary, MAXED, false, true, piety()));
			long total = 1;
			for (int hit = 1; hit <= normal.getMaxHit(); hit++)
			{
				total += Math.max(1, hit / 7);
			}
			for (int id : new int[]{492, 494})
			{
				Monster kraken = ordinary.copy();
				kraken.setId(id);
				DpsResult reduced = DpsCalculator.calculate(l, new CombatContext(kraken, MAXED, false, true, piety()));
				assertEquals(normal.getAccuracy(), reduced.getAccuracy(), 1e-12);
				assertEquals(Math.max(1, normal.getMaxHit() / 7), reduced.getMaxHit());
				double expected = normal.getAccuracy() * total / (normal.getMaxHit() + 1.0);
				assertEquals(expected, reduced.getAverageHit(), 1e-12);
				assertEquals(expected / (normal.getAttackSpeedTicks() * 0.6), reduced.getDps(), 1e-12);
			}
		}
	}

	@Test
	public void krakenReductionDoesNotReduceThrallsMagicOrTentacleRangedDamage()
	{
		GearItem bow = weapon(861, "magic shortbow", "bow", 4, "rapid,ranged,rapid");
		bow.setRangedStr(100);
		Loadout l = meleeLoadout(bow, "rapid,ranged,rapid");
		Monster target = monster(100, 20);
		DpsResult ordinary = DpsCalculator.calculate(l, new CombatContext(target, MAXED, false, true, piety()));
		target.setId(5535);
		assertEquals(ordinary.getDps(), DpsCalculator.calculate(l,
			new CombatContext(target, MAXED, false, true, piety())).getDps(), 1e-12);
		target.setId(494);
		CombatContext ctx = new CombatContext(target, MAXED, false, true, piety());
		DpsResult withoutThrall = DpsCalculator.calculate(l, ctx);
		CombatContext thrall = new CombatContext(target, MAXED, false, ctx.getAttack(), ctx.getStrength(),
			ctx.getRanged(), ctx.getMagic(), piety(), true);
		assertEquals(withoutThrall.getDps() + CombatContext.THRALL_DPS,
			DpsCalculator.calculate(l, thrall).getDps(), 1e-12);
		GearItem trident = weapon(11907, "trident of the seas", "powered staff", 4, "accurate,magic,accurate");
		l = meleeLoadout(trident, "accurate,magic,accurate");
		double magicDps = DpsCalculator.calculate(l, ctx).getDps();
		target.setId(1);
		assertEquals(magicDps, DpsCalculator.calculate(l, ctx).getDps(), 1e-12);
		assertTrue(magicDps > 0);
	}

	@Test
	public void directCalculatorCannotScoreIllegalMeleeOnKnownBosses()
	{
		GearItem halberd = weapon(29796, "noxious halberd", "polearm", 5, "swipe,slash,aggressive");
		halberd.setMeleeStr(100);
		for (int id : new int[]{492, 494, 7706})
		{
			Monster target = monster(100, 20);
			target.setId(id);
			assertEquals(0, DpsCalculator.calculate(meleeLoadout(halberd, "swipe,slash,aggressive"),
				new CombatContext(target, MAXED, false, true, piety())).getDps(), 0);
		}
	}

	@Test
	public void dpsIsAverageHitOverAttackInterval()
	{
		GearItem w = weapon(1, "test sword", "slash sword", 4, "chop,slash,aggressive");
		w.setMeleeStr(130);
		w.setSlashBonus(100);
		CombatContext ctx = new CombatContext(monster(100, 50), MAXED, false, true, piety());
		DpsResult r = DpsCalculator.calculate(meleeLoadout(w, "chop,slash,aggressive"), ctx);
		double expected = r.getAccuracy() * (r.getMaxHit() / 2.0 + 1.0 / (r.getMaxHit() + 1)) / (4 * 0.6);
		assertEquals(expected, r.getDps(), 1e-9);
	}

	@Test
	public void hitChanceFormula()
	{
		// a > d: 1 - (d + 2) / (2(a + 1))
		assertEquals(1 - 1002.0 / (2 * 2001), HitChance.single(2000, 1000), 1e-12);
		// a <= d: a / (2(d + 1))
		assertEquals(1000.0 / (2 * 2001), HitChance.single(1000, 2000), 1e-12);
		// fang rolls twice, so it is always at least as accurate
		assertTrue(HitChance.twoAttackRolls(1000, 2000) > HitChance.single(1000, 2000));
		assertTrue(HitChance.twoAttackRolls(3000, 2000) > HitChance.single(3000, 2000));
	}

	@Test
	public void twistedBowScalesWithTargetMagic()
	{
		GearItem tbow = weapon(20997, "twisted bow", "bow", 6, "rapid,ranged,rapid");
		GearItem plainBow = weapon(2, "plain bow", "bow", 6, "rapid,ranged,rapid");
		for (GearItem b : new GearItem[]{tbow, plainBow})
		{
			b.setRangedBonus(70);
			b.setRangedStr(20);
			b.setAmmunition(Collections.singletonList(11212));
		}
		GearItem arrows = item(11212, "dragon arrow", Slot.AMMO);
		arrows.setRangedStr(60);

		Monster highMagic = monster(100, 0);
		highMagic.setMagicLevel(250);
		CombatContext ctx = new CombatContext(highMagic, MAXED, false, true, piety());

		Loadout l = new Loadout();
		l.set(Slot.AMMO, arrows);
		l.setStyle(AttackStyle.parse("rapid,ranged,rapid"));
		l.set(Slot.WEAPON, plainBow);
		int baseMax = DpsCalculator.calculate(l, ctx).getMaxHit();
		l.set(Slot.WEAPON, tbow);
		DpsResult r = DpsCalculator.calculate(l, ctx);

		// At 250 magic the twisted bow deals 215% damage (OSRS Wiki).
		assertEquals(baseMax * 215 / 100, r.getMaxHit());
		assertEquals(5, r.getAttackSpeedTicks());
	}

	@Test
	public void ammoSlotRangedStrengthIgnoredWhenWeaponDoesNotFireIt()
	{
		GearItem bofa = weapon(25865, "bow of faerdhinen", "bow", 4, "rapid,ranged,rapid");
		bofa.setRangedStr(106);
		GearItem arrows = item(11212, "dragon arrow", Slot.AMMO);
		arrows.setRangedStr(60);
		CombatContext ctx = new CombatContext(monster(1, 0), MAXED, false, true, piety());

		Loadout l = new Loadout();
		l.set(Slot.WEAPON, bofa);
		l.setStyle(AttackStyle.parse("rapid,ranged,rapid"));
		int without = DpsCalculator.calculate(l, ctx).getMaxHit();
		l.set(Slot.AMMO, arrows);
		assertEquals(without, DpsCalculator.calculate(l, ctx).getMaxHit());
	}

	@Test
	public void salveOverridesSlayerHelmOnUndead()
	{
		GearItem w = weapon(1, "test sword", "slash sword", 4, "chop,slash,aggressive");
		w.setMeleeStr(100);
		Loadout l = meleeLoadout(w, "chop,slash,aggressive");
		l.set(Slot.HEAD, item(11864, "slayer helmet", Slot.HEAD));
		l.set(Slot.NECK, item(10588, "salve amulet (e)", Slot.NECK));

		CombatContext plain = new CombatContext(monster(1, 0), MAXED, false, true, piety());
		CombatContext undeadOnTask = new CombatContext(monster(1, 0, "undead"), MAXED, true, true, piety());

		Loadout bare = meleeLoadout(w, "chop,slash,aggressive");
		int base = DpsCalculator.calculate(bare, plain).getMaxHit();
		// Salve (e) is x1.2 and does not stack with the slayer helmet's 7/6.
		assertEquals(base * 6 / 5, DpsCalculator.calculate(l, undeadOnTask).getMaxHit());
	}

	@Test
	public void voidMeleeBoostsEffectiveLevels()
	{
		GearItem w = weapon(1, "test sword", "slash sword", 4, "chop,slash,aggressive");
		w.setMeleeStr(100);
		CombatContext ctx = new CombatContext(monster(1, 0), MAXED, false, false, null);
		Loadout l = meleeLoadout(w, "chop,slash,aggressive");
		int base = DpsCalculator.calculate(l, ctx).getMaxHit();

		l.set(Slot.HEAD, item(11665, "void melee helm", Slot.HEAD));
		l.set(Slot.BODY, item(8839, "void knight top", Slot.BODY));
		l.set(Slot.LEGS, item(8840, "void knight robe", Slot.LEGS));
		l.set(Slot.HANDS, item(8842, "void knight gloves", Slot.HANDS));
		// 99 + 3 + 8 = 110 -> x1.1 = 121; floor((121*164+320)/640) = 31 vs floor((110*164+320)/640) = 28
		assertEquals(28, base);
		assertEquals(31, DpsCalculator.calculate(l, ctx).getMaxHit());
	}

	@Test
	public void scytheHitsThreeTimesOnLargeTargets()
	{
		GearItem scythe = weapon(22325, "scythe of vitur", "scythe", 5, "chop,slash,aggressive");
		scythe.setMeleeStr(75);
		Monster small = monster(1, 0);
		Monster large = monster(1, 0);
		large.setSize(3);
		Loadout l = meleeLoadout(scythe, "chop,slash,aggressive");
		double one = DpsCalculator.calculate(l, new CombatContext(small, MAXED, false, true, piety())).getDps();
		double three = DpsCalculator.calculate(l, new CombatContext(large, MAXED, false, true, piety())).getDps();
		assertTrue(three > one * 1.7);
	}

	@Test
	public void poweredStaffAndAutocast()
	{
		GearItem sang = weapon(22323, "sanguinesti staff", "powered staff", 4, "accurate,magic,accurate");
		CombatContext ctx = new CombatContext(monster(1, 0), MAXED, false, false, null);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, sang);
		l.setStyle(AttackStyle.parse("accurate,magic,accurate"));
		// Base 33; the 20% +8 damage roll can reach 41.
		assertEquals(41, DpsCalculator.calculate(l, ctx).getMaxHit());

		GearItem staff = weapon(1381, "staff of fire", "staff", 5, "spell,magic,magic");
		staff.setMagicStr(10);
		Spell surge = new Spell();
		surge.setName("fire surge");
		surge.setSpellbook("standard");
		surge.setMaxHit(24);
		Loadout cast = new Loadout();
		cast.set(Slot.WEAPON, staff);
		cast.setStyle(AttackStyle.parse("spell,magic,magic"));
		cast.setSpell(surge);
		// 24 * 1.10 = 26.4 -> 26
		DpsResult r = DpsCalculator.calculate(cast, ctx);
		assertEquals(26, r.getMaxHit());
		assertEquals(5, r.getAttackSpeedTicks());
	}

	@Test
	public void atlatlUsesStrengthAndMeleeStrengthBonus()
	{
		GearItem atlatl = weapon(29000, "eclipse atlatl", "bow", 4, "rapid,ranged,rapid");
		atlatl.setRangedBonus(87);
		atlatl.setMeleeStr(40);
		GearItem strengthAmulet = item(1725, "amulet of strength", Slot.NECK);
		strengthAmulet.setMeleeStr(100);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, atlatl);
		l.set(Slot.NECK, strengthAmulet);
		l.setStyle(AttackStyle.parse("rapid,ranged,rapid"));
		CombatContext ctx = new CombatContext(monster(1, 0), MAXED, false, true, piety());
		// Strength 99 + super combat = 118; x1.23 (rigour damage) = 145; + 0 (rapid) + 8 = 153
		// max = floor((153 * (140 + 64) + 320) / 640) = 49
		DpsResult r = DpsCalculator.calculate(l, ctx);
		assertEquals(49, r.getMaxHit());
		assertEquals(3, r.getAttackSpeedTicks());
		assertEquals(AttackStyle.Type.ATLATL, WeaponRules.tabType(atlatl, l.getStyle()));
	}

	@Test
	public void eclipseMoonSetAddsBurnDamage()
	{
		GearItem atlatl = weapon(29000, "eclipse atlatl", "bow", 4, "rapid,ranged,rapid");
		atlatl.setMeleeStr(40);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, atlatl);
		l.setStyle(AttackStyle.parse("rapid,ranged,rapid"));
		CombatContext ctx = new CombatContext(monster(50, 0), MAXED, false, true, piety());
		DpsResult without = DpsCalculator.calculate(l, ctx);
		l.set(Slot.HEAD, item(29010, "eclipse moon helm", Slot.HEAD));
		l.set(Slot.BODY, item(29004, "eclipse moon chestplate", Slot.BODY));
		l.set(Slot.LEGS, item(29007, "eclipse moon tassets", Slot.LEGS));
		DpsResult with = DpsCalculator.calculate(l, ctx);
		// Burns are damage over time, not part of the hit: 20% of successful attacks add 10 damage over
		// 40 ticks, limited to five stacks. The target's HP is unknown here, so the long-run rate applies.
		assertEquals(without.getAverageHit(), with.getAverageHit(), 1e-9);
		double acc = without.getAccuracy();
		double stacks = 0.2 * acc / 3 * 40;
		double p = Math.exp(-stacks);
		double capped = 0;
		double below = 0;
		for (int n = 0; n < 5; n++)
		{
			capped += n * p;
			below += p;
			p *= stacks / (n + 1);
		}
		capped += 5 * (1 - below);
		double perTick = 0.2 * acc / 3 * 10 * capped / stacks;
		assertEquals(without.getDps() + perTick / 0.6, with.getDps(), 1e-9);
		assertTrue(with.getMaxHitDetail().contains("burn"));
	}
}
