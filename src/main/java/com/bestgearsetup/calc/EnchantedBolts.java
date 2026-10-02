package com.bestgearsetup.calc;

import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import lombok.Value;

/** Sustained damaging enchanted-bolt effects, including miss procs and per-hit encounter caps. */
final class EnchantedBolts
{
	private EnchantedBolts()
	{
	}

	static Result calculate(Loadout l, CombatContext ctx, EncounterDamage.Rule rule, double accuracy, long min, long max)
	{
		String ammo = EncounterDamage.lower(l.get(Slot.AMMO));
		if (!ammo.endsWith("(e)"))
		{
			return null;
		}
		Monster monster = ctx.getMonster();
		boolean zaryte = EncounterDamage.lower(l.getWeapon()).startsWith("zaryte crossbow");
		// The Zaryte crossbow special guarantees the effect on every accurate hit; misses keep the usual chance.
		boolean guaranteed = zaryte && ctx.getModifiers().isSpecialAttack();
		double diary = ctx.getModifiers().isKandarinDiary() ? 1.1 : 1;
		double normal = EquipmentEffects.average(rule, l, ctx, min, max);
		int normalMax = EquipmentEffects.maximum(rule, l, ctx, max);
		double chance;
		double effectHit;
		// Expected effect damage when it procs on a miss; negative when misses cannot proc.
		double effectMiss;
		boolean missBecomesAccurate = false;
		int effectMax;
		if (ammo.startsWith("ruby ") && ctx.getModifiers().playerHp(ctx.getLevels()) >= 10
			&& ctx.getModifiers().targetHp(monster) > 0)
		{
			chance = 0.06 * diary;
			boolean infinite = monster.getId() == 14779 || EncounterDamage.named(monster, "gemstone crab");
			int cap = infinite ? zaryte ? 66 : 60 : zaryte ? 110 : 100;
			int damage = Math.min(cap, ctx.getModifiers().targetHp(monster) * (zaryte ? 22 : 20) / 100);
			// Corp's reduction precedes ruby replacement, so ruby effect damage bypasses it.
			EncounterDamage.Rule rubyRule = EncounterDamage.named(monster, "corporeal beast")
				|| monster.getId() == 319 ? EncounterDamage.unmitigated() : rule;
			effectHit = rubyRule.mean(Math.max(1, damage));
			effectMiss = effectHit;
			missBecomesAccurate = true;
			effectMax = rubyRule.maximum(Math.max(1, damage));
		}
		else if (ammo.startsWith("diamond "))
		{
			chance = 0.10 * diary;
			long effectRoll = max * (zaryte ? 126 : 115) / 100;
			effectHit = HitDamage.average(rule, 0, effectRoll);
			effectMiss = effectHit;
			missBecomesAccurate = true;
			effectMax = rule.maximum(effectRoll);
		}
		else if (ammo.startsWith("onyx ") && !monster.hasAttribute("undead"))
		{
			chance = 0.11 * diary;
			long effectRoll = max * (zaryte ? 132 : 120) / 100;
			effectHit = HitDamage.average(rule, 0, effectRoll);
			effectMiss = -1;
			effectMax = rule.maximum(effectRoll);
		}
		else
		{
			int bonus;
			boolean missProc;
			if (ammo.startsWith("opal "))
			{
				chance = 0.05 * diary;
				bonus = ctx.getRanged() / (zaryte ? 9 : 10);
				missProc = true;
			}
			else if (ammo.startsWith("pearl "))
			{
				chance = 0.06 * diary;
				int divisor = monster.hasAttribute("fiery") ? 15 : 20;
				bonus = ctx.getRanged() / (zaryte ? divisor - 2 : divisor);
				missProc = true;
			}
			else if (ammo.startsWith("dragonstone ") && !monster.hasAttribute("dragon")
				&& !monster.hasAttribute("fiery") && !monster.hasAttribute("dragonfire immune"))
			{
				chance = 0.06 * diary;
				bonus = ctx.getRanged() * 2 / (zaryte ? 9 : 10);
				missProc = false;
			}
			else
			{
				return null;
			}
			effectHit = HitDamage.average(rule, min, max, h -> EquipmentEffects.damage(l, ctx, h) + bonus);
			// A miss that procs stays inaccurate, so flat armour does not reduce it.
			effectMiss = missProc ? rule.meanInaccurate(bonus) : -1;
			effectMax = rule.maximum(EquipmentEffects.damage(l, ctx, max) + bonus);
		}
		double onHit = guaranteed ? 1 : chance;
		double average = accuracy * (onHit * effectHit + (1 - onHit) * normal)
			+ (effectMiss >= 0 ? (1 - accuracy) * chance * effectMiss : 0);
		double accurate = missBecomesAccurate ? accuracy + (1 - accuracy) * chance : accuracy;
		return new Result(average, Math.max(normalMax, effectMax), accurate,
			String.format(java.util.Locale.ROOT, "%s: %s proc; target HP %d", ammo,
				guaranteed ? "guaranteed on hit" : String.format(java.util.Locale.ROOT, "%.1f%%", chance * 100),
				ctx.getModifiers().targetHp(monster)));
	}

	@Value
	static class Result
	{
		double average;
		int maximum;
		double accuracy;
		String detail;
	}
}
