package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.StatusImmunities;
import java.util.function.LongFunction;

/**
 * Expected damage of one special attack, from its accuracy and min/max roll. Hit structures follow each
 * weapon's Wiki page; encounter mitigation is applied to each hitsplat.
 */
final class SpecialDamage
{
	private SpecialDamage()
	{
	}

	/** One ordinary roll: accurate damage with equipment effects, or a miss. */
	private static double single(EncounterDamage.Rule rule, Loadout l, CombatContext ctx, double acc, long min, long max)
	{
		return acc * EquipmentEffects.average(rule, l, ctx, min, max) + (1 - acc) * rule.missDamage();
	}

	static DpsResult melee(SpecialAttack s, Loadout l, CombatContext ctx, EncounterDamage.Rule rule, double acc,
		long min, long max, long attRoll, long defRoll, int speed)
	{
		Monster m = ctx.getMonster();
		double average;
		int shown = EquipmentEffects.maximum(rule, l, ctx, max);
		double shownAccuracy = acc;
		double interval = speed;
		switch (s.getKind())
		{
			case MULTI:
				average = s.getHits() * single(rule, l, ctx, acc, min, max);
				shown *= s.getHits();
				break;
			case ABYSSAL_DAGGER:
				// The second hit always lands when the first does.
				average = acc * 2 * EquipmentEffects.average(rule, l, ctx, min, max) + (1 - acc) * rule.missDamage();
				shown *= 2;
				break;
			case DRAGON_CLAWS:
			case BURNING_CLAWS:
			{
				boolean dragon = s.getKind() == SpecialAttack.Kind.DRAGON_CLAWS;
				double[] claws = claws(rule, acc, max, dragon);
				average = claws[0];
				shown = (int) claws[1];
				shownAccuracy = 1 - Math.pow(1 - acc, dragon ? 4 : 3);
				if (!dragon)
				{
					average += burningClawDot(acc) * StatusEffects.burnFactor(l, ctx, StatusImmunities.BURN_NORMAL);
				}
				break;
			}
			case CRIMSON_KISTEN:
				average = 0;
				for (int passes = 1; passes <= 4; passes++)
				{
					int i = passes - 1;
					average += binomial(acc, passes, 4) * EquipmentEffects.average(rule, l, ctx,
						max * (70 + 20 * i) / 100, max * (110 + 20 * i) / 100);
				}
				shown = EquipmentEffects.maximum(rule, l, ctx, max * 170 / 100);
				shownAccuracy = 1 - Math.pow(1 - acc, 4);
				break;
			case HALBERD:
				average = single(rule, l, ctx, acc, min, max);
				if (m.getSize() > 1)
				{
					double second = CombatRules.accuracy(m, l, HitChance.single(attRoll * 3 / 4, defRoll));
					average += single(rule, l, ctx, second, min, max);
					shown *= 2;
				}
				break;
			case SARADOMIN_SWORD:
			{
				average = single(rule, l, ctx, acc, min, max);
				if (EncounterImmunities.classAllowed(m, CombatClass.MAGIC))
				{
					// A guaranteed 1-16 magic hit follows a successful melee hit.
					double magic = 0;
					for (int hit = 1; hit <= 16; hit++)
					{
						magic += rule.mean(hit);
					}
					average += acc * magic / 16;
					shown += rule.maximum(16);
				}
				break;
			}
			case GRANITE_HAMMER:
				average = acc * HitDamage.averageTransformed(rule, min, max, h -> EquipmentEffects.damage(l, ctx, h) + 5)
					+ (1 - acc) * rule.meanInaccurate(5);
				shown = rule.maximum(EquipmentEffects.damage(l, ctx, max) + 5);
				break;
			case SUNSPEAR:
				average = acc * rule.mean(Math.max(1, EquipmentEffects.damage(l, ctx, max)));
				break;
			case VOIDWAKER:
			{
				EncounterDamage.Rule magic = EncounterDamage.rule(m, l, CombatClass.MAGIC);
				average = EquipmentEffects.average(magic, l, ctx, min, max);
				shown = EquipmentEffects.maximum(magic, l, ctx, max);
				shownAccuracy = 1;
				break;
			}
			case BLOOD_MOON:
			{
				// Dual macuahuitl: the second half lands only after the first; the set shortens the attack.
				long first = max / 2;
				long second = max - first;
				average = acc * EquipmentEffects.average(rule, l, ctx, min, Math.max(min, first))
					+ (1 - acc) * rule.missDamage() + acc * single(rule, l, ctx, acc, min, Math.max(min, second));
				shown = EquipmentEffects.maximum(rule, l, ctx, first) + EquipmentEffects.maximum(rule, l, ctx, second);
				interval = speed - (1 - (1 - acc) * (1 - acc));
				break;
			}
			default:
				average = single(rule, l, ctx, acc, min, max);
				break;
		}
		if (s.getName().equals("Arkan blade"))
		{
			average += 10 * acc * StatusEffects.burnFactor(l, ctx, StatusImmunities.BURN_STRONG);
		}
		return finish(s, average, shown, shownAccuracy, speed, interval, rule, null);
	}

	static DpsResult ranged(SpecialAttack s, Loadout l, CombatContext ctx, EncounterDamage.Rule rule, double acc,
		long min, long max, long attRoll, int defBonus, int speed)
	{
		Monster m = ctx.getMonster();
		double average;
		int shown;
		double shownAccuracy = acc;
		String extra = null;
		switch (s.getKind())
		{
			case DARK_BOW:
			{
				// Each arrow deals at least the minimum even on a miss, and at most 48.
				long low = min;
				double hit = acc * HitDamage.averageTransformed(rule, min, max,
					h -> Math.max(low, Math.min(48, EquipmentEffects.damage(l, ctx, h))))
					+ (1 - acc) * rule.meanInaccurate(min);
				average = 2 * hit;
				shown = 2 * rule.maximum(Math.max(min, Math.min(48, max)));
				break;
			}
			case TONALZTICS:
			{
				// The second throw uses the Defence lowered by a successful first throw.
				Monster lowered = SpecialAttacks.builder().tonalztics(1).build().apply(m);
				long defence = CombatRules.defenceRoll(lowered, lowered.getDefenceLevel(), defBonus);
				double afterHit = CombatRules.accuracy(m, l, HitChance.single(attRoll, defence));
				double first = single(rule, l, ctx, acc, min, max);
				average = first + acc * single(rule, l, ctx, afterHit, min, max) + (1 - acc) * first;
				shown = 2 * EquipmentEffects.maximum(rule, l, ctx, max);
				break;
			}
			default:
			{
				double hit = single(rule, l, ctx, acc, min, max);
				int hitMax = EquipmentEffects.maximum(rule, l, ctx, max);
				if (l.getWeapon().getSubcategory().equals("crossbow") && WeaponRules.firesAmmoSlot(l.getWeapon()))
				{
					EnchantedBolts.Result bolts = EnchantedBolts.calculate(l, ctx, rule, acc, min, max);
					if (bolts != null)
					{
						hit = bolts.getAverage();
						hitMax = bolts.getMaximum();
						shownAccuracy = bolts.getAccuracy();
						extra = bolts.getDetail();
					}
				}
				average = s.getHits() * hit;
				shown = s.getHits() * hitMax;
				break;
			}
		}
		if (s.getName().equals("Scorching bow"))
		{
			// Scorching shackles burn demons for 5 whether or not the arrow lands.
			average += 5 * StatusEffects.burnFactor(l, ctx, StatusImmunities.BURN_NORMAL);
		}
		return finish(s, average, shown, shownAccuracy, speed, speed, rule, extra);
	}

	private static DpsResult finish(SpecialAttack s, double average, int shown, double accuracy, int speed,
		double interval, EncounterDamage.Rule rule, String extra)
	{
		String detail = s.describe() + (extra == null ? "" : "; " + extra) + (rule.detail == null ? "" : "; " + rule.detail);
		DpsResult r = DpsCalculator.result(average, shown, accuracy, speed).withDetail(detail);
		double next = interval + s.getExtraTicks();
		return next == speed ? r : r.withExpectedSpeed(next);
	}

	/**
	 * What a claws special does when one of its accuracy rolls is the first to succeed: it rolls a total
	 * between {@code quarters / 4} of the max hit and that plus the max hit ({@code + extra}), then splits the
	 * total into hitsplats. Later rolls start lower, so a late success deals less.
	 */
	private static final class ClawRoll
	{
		private final int quarters;
		private final int extra;
		private final LongFunction<long[]> split;

		private ClawRoll(int quarters, int extra, LongFunction<long[]> split)
		{
			this.quarters = quarters;
			this.extra = extra;
			this.split = split;
		}
	}

	/**
	 * Dragon claws, Slice and Dice (Wiki "Dragon claws"): up to four rolls; the hits after the first success
	 * halve in turn ("4-2-1-1", "0-4-2-2", "0-0-3-3", "0-0-0-5"), and the last splat gains 1 damage.
	 */
	private static final ClawRoll[] DRAGON_CLAWS = {
		new ClawRoll(4, -1, t -> new long[]{t / 2, t / 4, t / 8, t / 8 + 1}),
		new ClawRoll(3, -1, t -> new long[]{t / 2, t / 4, t / 4 + 1}),
		new ClawRoll(2, -1, t -> new long[]{t / 2, t / 2 + 1}),
		new ClawRoll(1, -1, t -> new long[]{t + 1}),
	};

	/**
	 * Burning claws, Burning barrage (Wiki "Burning claws"): 75-175% split 25/25/50, then 50-150% split 50/50/0
	 * moving 1 from each of the first two splats to the third, then 25-125% split 0/0/100 moving 2 from the
	 * third splat to the first two.
	 */
	private static final ClawRoll[] BURNING_CLAWS = {
		new ClawRoll(3, 0, t -> new long[]{t / 4, t / 4, t / 2}),
		new ClawRoll(2, 0, t -> new long[]{t / 2 - 1, t / 2 - 1, 2}),
		new ClawRoll(1, 0, t -> new long[]{1, 1, t - 2}),
	};

	/** Expected damage and maximum of a claws special, with encounter rules per splat. Returns {mean, maximum}. */
	static double[] claws(EncounterDamage.Rule rule, double acc, long max, boolean dragon)
	{
		double total = 0;
		long best = 0;
		// Chance that every accuracy roll so far has missed.
		double allMissed = 1;
		for (ClawRoll roll : dragon ? DRAGON_CLAWS : BURNING_CLAWS)
		{
			double firstSuccess = allMissed * acc;
			allMissed *= 1 - acc;
			long low = max * roll.quarters / 4;
			long high = low + max + roll.extra;
			if (high < low)
			{
				continue;
			}
			double perTotal = firstSuccess / (high - low + 1);
			for (long t = low; t <= high; t++)
			{
				double mean = 0;
				long top = 0;
				for (long splat : roll.split.apply(t))
				{
					mean += rule.mean(Math.max(0, splat));
					top += rule.maximum(Math.max(0, splat));
				}
				total += perTotal * mean;
				best = Math.max(best, top);
			}
		}
		// Every roll missed. Dragon claws: about 2/3 of the time two 1-damage splats. Burning claws: 40% one
		// 1-damage splat, 40% two. These splats are inaccurate.
		double missSplats = dragon ? 2.0 / 3 * 2 : 0.4 + 0.4 * 2;
		total += allMissed * missSplats * rule.meanInaccurate(1);
		return new double[]{total, best};
	}

	/**
	 * Expected burn damage of Burning barrage: each of the three splats burns for 10 with a chance of 15%, 30% or
	 * 45%, by the roll that succeeded (Wiki "Burning claws"). By linearity that is 30 x the chance, less 1 damage
	 * when the first two burns overlap (both burn: the chance squared), as the Wiki DPS calculator's authors observed.
	 */
	static double burningClawDot(double acc)
	{
		double total = 0;
		double allMissed = 1;
		for (int roll = 1; roll <= 3; roll++)
		{
			double chance = 0.15 * roll;
			total += allMissed * acc * (30 * chance - chance * chance);
			allMissed *= 1 - acc;
		}
		return total;
	}

	private static double binomial(double p, int k, int n)
	{
		double ways = 1;
		for (int i = 1; i <= k; i++)
		{
			ways = ways * (n - i + 1) / i;
		}
		return ways * Math.pow(p, k) * Math.pow(1 - p, n - k);
	}

	/** Ammunition-only max hit of the magic shortbow, magic longbow and Seercull specials. */
	static long arrowFormula(Loadout l, CombatContext ctx)
	{
		int ammo = l.get(Slot.AMMO) == null ? 0 : l.get(Slot.AMMO).getRangedStr();
		return ((ctx.getRanged() + 10L) * (ammo + 64) + 320) / 640;
	}

	/** Staves whose specials cast a built-in spell even when the staff normally autocasts. */
	static boolean builtInSpell(com.bestgearsetup.data.GearItem weapon)
	{
		String n = EncounterDamage.lower(weapon);
		return n.startsWith("accursed sceptre") || n.startsWith("volatile nightmare staff")
			|| n.startsWith("eldritch nightmare staff");
	}

	/** Built-in spec max hits of the nightmare staves; other staves use their powered formula. */
	static int builtInMax(com.bestgearsetup.data.GearItem weapon, int magic)
	{
		String n = EncounterDamage.lower(weapon);
		if (n.startsWith("volatile nightmare staff"))
		{
			return Math.max(1, Math.min(58, (99 + 58 * magic) / 99));
		}
		if (n.startsWith("eldritch nightmare staff"))
		{
			return Math.max(1, Math.min(44, (99 + 44 * magic) / 99));
		}
		return WeaponRules.poweredStaffMaxHit(weapon, magic);
	}
}
