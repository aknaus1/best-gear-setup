package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.OffensivePrayer;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.StatusImmunities;

/**
 * Expected-DPS calculator following the OSRS Wiki combat formulas. Covers the common gear
 * effects (void, slayer helmet, salve, obsidian, inquisitor, crystal, dragon hunter, arclight,
 * keris, twisted bow, scythe, fang, Tumeken's shadow) and supported encounter hit caps and
 * immunities. Thralls are added separately. "Only special attacks" scores each weapon's special attack
 * instead (see {@link SpecialAttack}); specs mixed into a kill are blended by {@link SpecialRotation}.
 * Overkill is not modelled.
 */
public final class DpsCalculator
{
	private static final double SECONDS_PER_TICK = 0.6;
	/** Slot.values() copies the array on every call, and bonuses are summed for every setup scored. */
	private static final Slot[] SLOTS = Slot.values();

	private DpsCalculator()
	{
	}

	/** Gauss-Legendre nodes and weights on [0, 1]; exact for cubic 1/DPS between breakpoints. */
	private static final double[] NODES = {0.5 - Math.sqrt(0.15), 0.5, 0.5 + Math.sqrt(0.15)};
	private static final double[] WEIGHTS = {5 / 18.0, 8 / 18.0, 5 / 18.0};

	/**
	 * Expected DPS for the fight. When the attack depends on the target's remaining HP (ruby bolts,
	 * the Sun keris in ToA, Vardorvis's Defence), kill time is integrated from the selected HP down to
	 * zero, and DPS is that HP divided by the time. Accuracy and max hit describe the starting state.
	 */
	public static DpsResult calculate(Loadout loadout, CombatContext ctx)
	{
		DpsResult start = snapshot(loadout, ctx);
		if (ctx.getModifiers().isSpecialAttack())
		{
			// A special attack is one hit at the selected HP, not a whole fight.
			return start;
		}
		int startHp = ctx.getModifiers().targetHp(ctx.getMonster());
		int[] breaks = hpBreakpoints(loadout, ctx);
		if (breaks == null || start.getPrimaryDps() <= 0 || startHp <= 1)
		{
			return StatusEffects.apply(loadout, ctx, start);
		}
		double seconds = 0;
		int upper = startHp;
		for (int b = 0; b <= breaks.length; b++)
		{
			int lower = b < breaks.length ? Math.max(0, Math.min(upper, breaks[b])) : 0;
			if (upper <= lower)
			{
				continue;
			}
			for (int i = 0; i < NODES.length; i++)
			{
				// Remaining HP is at least 1 while the target is alive.
				int hp = Math.max(1, (int) Math.ceil(lower + (upper - lower) * NODES[i]));
				DpsResult at = hp == startHp ? start : snapshot(loadout, ctx.atTargetHp(hp));
				if (at.getPrimaryDps() <= 0)
				{
					return StatusEffects.apply(loadout, ctx, start);
				}
				seconds += WEIGHTS[i] * (upper - lower) / at.getPrimaryDps();
			}
			upper = lower;
		}
		return StatusEffects.apply(loadout, ctx, start.withWholeFight(startHp / seconds / start.getPrimaryDps(), startHp));
	}

	/**
	 * HP values below which the attack changes, ordered downwards, or null when it does not depend on
	 * the target's HP. An empty array still integrates smoothly varying stats.
	 */
	static int[] hpBreakpoints(Loadout l, CombatContext ctx)
	{
		Monster m = ctx.getMonster();
		if (CombatRules.oneHit(m) || m.getId() == 14779 || EncounterDamage.named(m, "gemstone crab"))
		{
			return null;
		}
		String weapon = lower(l.getWeapon());
		String ammo = lower(l.get(Slot.AMMO));
		boolean ruby = WeaponRules.classOf(l.getStyle()) == CombatClass.RANGED
			&& l.getWeapon() != null && l.getWeapon().getSubcategory().equals("crossbow")
			&& WeaponRules.firesAmmoSlot(l.getWeapon()) && ammo.startsWith("ruby ");
		boolean sun = weapon.equals("keris partisan of the sun") && l.getStyle().isMelee() && CombatRules.toa(m);
		boolean vardorvis = ctx.getHealthStates() != null && EncounterDamage.named(m, "vardorvis");
		if (!ruby && !sun && !vardorvis)
		{
			return null;
		}
		if (ruby && sun)
		{
			return new int[]{500, m.getHitpoints() / 4};
		}
		// Ruby damage stops at its cap from 500 HP; the Sun keris gains accuracy below a quarter of max HP.
		return ruby ? new int[]{500} : sun ? new int[]{m.getHitpoints() / 4} : new int[0];
	}

	static DpsResult snapshot(Loadout loadout, CombatContext ctx)
	{
		GearItem weapon = loadout.getWeapon();
		AttackStyle style = loadout.getStyle();
		if (weapon == null || style == null || !EncounterImmunities.attackAllowed(ctx.getMonster(), loadout)
			|| !AttackReach.canReach(ctx.getMonster(), weapon, style, ctx.getTargetDistance()))
		{
			return DpsResult.ZERO;
		}
		Bonuses b = Bonuses.of(loadout);
		// Special-attack mode scores only the spec, so weapons without a damaging spec deal nothing.
		SpecialAttack spec = ctx.getModifiers().isSpecialAttack() ? SpecialAttack.of(loadout, ctx) : null;
		if (ctx.getModifiers().isSpecialAttack() && spec == null)
		{
			return DpsResult.ZERO;
		}
		DpsResult r;
		switch (WeaponRules.classOf(style))
		{
			case RANGED:
				r = ranged(loadout, weapon, style, b, ctx, spec);
				break;
			case MAGIC:
				r = magic(loadout, weapon, style, b, ctx, spec);
				break;
			default:
				r = melee(loadout, weapon, style, b, ctx, spec);
				break;
		}
		if (CombatRules.oneHit(ctx.getMonster()) && r.getMaxHit() > 0)
		{
			int hp = ctx.getModifiers().targetHp(ctx.getMonster());
			int speed = EquipmentEffects.ratWeapon(weapon) ? 1 : r.getAttackSpeedTicks();
			r = result(hp, hp, 1, speed).withDetail("Target dies in one successful attack");
		}
		else if (EncounterDamage.named(ctx.getMonster(), "respiratory system") && EncounterDamage.demonbane(loadout))
		{
			int hp = ctx.getMonster().getHitpoints();
			r = result(hp * r.getAccuracy(), hp, r.getAccuracy(), r.getAttackSpeedTicks()).withDetail("Demonbane one-hit kill");
		}
		else if (CombatRules.titanElemental(ctx.getMonster()) && WeaponRules.classOf(style) == CombatClass.MAGIC)
		{
			// Royal Titans elementals: accuracy from the Magic attack bonus alone; successful hits are maximum.
			double accuracy = Math.min(1.0, Math.max(0, b.magic) / 100.0 + 0.3);
			if (hasVoid(loadout, "void mage helm"))
			{
				accuracy = Math.min(1.0, accuracy * 1.45);
			}
			r = result(r.getMaxHit() * accuracy, r.getMaxHit(), accuracy, r.getAttackSpeedTicks())
				.withDetail("Bonus-based accuracy; guaranteed maximum damage");
		}
		else if (CombatRules.alwaysMax(ctx.getMonster(), WeaponRules.classOf(style))
			|| CombatRules.araxyteMax(ctx.getMonster(), loadout))
		{
			r = result(r.getMaxHit(), r.getMaxHit(), 1, r.getAttackSpeedTicks()).withDetail("Guaranteed maximum damage");
		}
		if (CombatRules.tormentedBonus(ctx.getMonster(), loadout))
		{
			r = r.withExpectedSpeed(Math.max(1, r.getExpectedSpeedTicks() - 1));
		}
		if (SupportSpell.thrall(loadout, ctx) && r.getDps() > 0)
		{
			r = r.withExtraDps(ctx.getThrall().getDps());
		}
		return r;
	}

	// ---------------------------------------------------------------- melee

	private static DpsResult melee(Loadout l, GearItem weapon, AttackStyle style, Bonuses b, CombatContext ctx,
		SpecialAttack spec)
	{
		Monster m = ctx.getMonster();
		String stance = style.getStance();
		int attStance = stance.equals("accurate") ? 3 : stance.equals("controlled") ? 1 : 0;
		int strStance = stance.equals("aggressive") ? 3 : stance.equals("controlled") ? 1 : 0;

		OffensivePrayer prayer = ctx.getPrayer(CombatClass.MELEE);
		double pAtt = prayer == null ? 1 : 1 + prayer.getAccuracyPercent() / 100;
		double pStr = prayer == null ? 1 : 1 + prayer.getDamagePercent() / 100;

		int effAtt = (int) Math.floor(ctx.getAttack() * pAtt) + attStance + 8;
		int effStr = (int) Math.floor(ctx.getStrength() * pStr) + strStance + 8;
		String wName = lower(weapon);
		if (wName.startsWith("soulreaper axe") && spec == null)
		{
			effStr += ctx.getStrength() * Math.max(0, Math.min(5, ctx.getModifiers().getSoulreaperStacks())) * 6 / 100;
		}
		if (hasVoid(l, "void melee helm"))
		{
			effAtt = effAtt * 11 / 10;
			effStr = effStr * 11 / 10;
		}

		int attBonus;
		int defBonus;
		switch (style.getType())
		{
			case STAB:
				attBonus = b.stab;
				defBonus = m.getDefStab();
				break;
			case SLASH:
				attBonus = b.slash;
				defBonus = m.getDefSlash();
				break;
			default:
				attBonus = b.crush;
				defBonus = m.getDefCrush();
				break;
		}
		int defLevel = m.getDefenceLevel();
		if (spec != null && spec.getDefence() != null)
		{
			// Many specs roll against a fixed defence style regardless of the selected stance.
			defBonus = specDefenceBonus(m, spec.getDefence());
			if (spec.getDefence().equals("magic") && !CombatRules.defenceBasedMagic(m))
			{
				defLevel = m.getMagicLevel();
			}
		}

		long attRoll = (long) effAtt * (attBonus + 64);
		long maxHit = (effStr * (long) (b.meleeStr + 64) + 320) / 640;
		long baseMax = maxHit;
		if (named(l, Slot.AMMO, "crystal blessing"))
		{
			int pieces = (named(l, Slot.HEAD, "crystal helm") ? 1 : 0)
				+ (named(l, Slot.BODY, "crystal body") ? 3 : 0) + (named(l, Slot.LEGS, "crystal legs") ? 2 : 0);
			maxHit = frac(maxHit, 40 + pieces, 40);
		}

		// Salve and slayer helmet do not stack; salve takes priority.
		int salve = salveTier(l.get(Slot.NECK));
		if (named(l, Slot.NECK, "amulet of avarice") && m.getLowerName().startsWith("revenant"))
		{
			int avarice = ctx.getModifiers().isForinthrySurge() ? 27 : 24;
			attRoll = frac(attRoll, avarice, 20);
			maxHit = frac(maxHit, avarice, 20);
		}
		else if (m.hasAttribute("undead") && salve != SALVE_NONE)
		{
			boolean enchanted = salve == SALVE_E || salve == SALVE_EI;
			attRoll = frac(attRoll, enchanted ? 6 : 7, enchanted ? 5 : 6);
			maxHit = frac(maxHit, enchanted ? 6 : 7, enchanted ? 5 : 6);
		}
		else if (ctx.isOnTask() && isSlayerHelm(l.get(Slot.HEAD)))
		{
			attRoll = frac(attRoll, 7, 6);
			maxHit = frac(maxHit, 7, 6);
		}

		boolean tzhaar = wName.startsWith("toktz-") || wName.startsWith("tzhaar-ket-");
		if (tzhaar && named(l, Slot.HEAD, "obsidian helmet") && named(l, Slot.BODY, "obsidian platebody")
			&& named(l, Slot.LEGS, "obsidian platelegs"))
		{
			attRoll = frac(attRoll, 11, 10);
			maxHit += baseMax / 10;
		}
		if (wName.startsWith("dragon hunter lance") && m.hasAttribute("dragon"))
		{
			attRoll = frac(attRoll, 6, 5);
			maxHit = frac(maxHit, 6, 5);
		}
		if (m.hasAttribute("demon"))
		{
			int bonus = wName.startsWith("arclight") || wName.startsWith("emberlight") ? 70
				: wName.startsWith("silverlight") || wName.startsWith("darklight") ? 60
				: wName.startsWith("burning claws") || wName.startsWith("bone claws") ? 5 : 0;
			bonus = CombatRules.demonBonus(m, bonus);
			attRoll = frac(attRoll, 100 + bonus, 100);
			maxHit = frac(maxHit, 100 + bonus, 100);
		}
		if (wName.startsWith("keris") && m.hasAttribute("kalphite"))
		{
			if (wName.contains("breaching"))
			{
				attRoll = frac(attRoll, 133, 100);
			}
			maxHit = frac(maxHit, wName.contains("amascut") ? 115 : 133, 100);
		}
		if (wName.equals("keris partisan of the sun") && CombatRules.toa(m)
			&& ctx.getModifiers().targetHp(m) < m.getHitpoints() / 4)
		{
			attRoll = frac(attRoll, 5, 4);
		}
		if (EquipmentEffects.vampyre(ctx))
		{
			if (wName.startsWith("blisterwood flail") || wName.startsWith("blisterwood sickle"))
			{
				attRoll = frac(attRoll, 105, 100);
			}
			else if (wName.startsWith("hallowed flail") || wName.equals("sunspear"))
			{
				attRoll = frac(attRoll, 125, 100);
			}
			if (named(l, Slot.RING, "efaritay's aid") && EncounterImmunities.silver(l))
			{
				attRoll = frac(attRoll, 23, 20);
			}
		}
		if (wName.startsWith("dragon hunter wand") && m.hasAttribute("dragon"))
		{
			attRoll = frac(attRoll, 7, 4);
			maxHit = frac(maxHit, 7, 5);
		}
		if (EquipmentEffects.wildernessWeapon(l, ctx))
		{
			attRoll = frac(attRoll, 3, 2);
			maxHit = frac(maxHit, 3, 2);
		}
		if (wName.equals("leaf-bladed battleaxe") && (m.hasAttribute("leafy")
			|| EncounterDamage.named(m, "kurask") || EncounterDamage.named(m, "turoth")))
		{
			maxHit = frac(maxHit, 47, 40);
		}
		if (m.hasAttribute("golem") && wName.equals("barronite mace"))
		{
			maxHit = frac(maxHit, 23, 20);
		}
		if (m.hasAttribute("golem") && wName.equals("granite hammer"))
		{
			attRoll = frac(attRoll, 13, 10);
			maxHit = frac(maxHit, 13, 10);
		}
		if (wName.equals("colossal blade"))
		{
			maxHit += Math.min(10, Math.max(1, m.getSize()) * 2);
		}
		if (EquipmentEffects.ratWeapon(weapon) && m.hasAttribute("rat"))
		{
			maxHit += 10;
		}
		if (style.getType() == AttackStyle.Type.CRUSH)
		{
			int pieces = (named(l, Slot.HEAD, "inquisitor's great helm") ? 1 : 0)
				+ (named(l, Slot.BODY, "inquisitor's hauberk") ? 2 : 0)
				+ (named(l, Slot.LEGS, "inquisitor's plateskirt") ? 2 : 0);
			if (pieces > 0)
			{
				int perMille = pieces * 5;
				attRoll = frac(attRoll, 1000 + perMille, 1000);
				maxHit = frac(maxHit, 1000 + perMille, 1000);
			}
		}
		long specMin = 0;
		if (spec != null)
		{
			attRoll = spec.scaleAccuracy(attRoll);
			specMin = spec.minimum(maxHit);
			maxHit = spec.scaleMax(maxHit);
			switch (spec.getKind())
			{
				case VOIDWAKER:
					specMin = maxHit / 2;
					maxHit += specMin;
					break;
				case BLOOD_MOON:
					specMin = maxHit / 4;
					maxHit += specMin;
					break;
				case SOULREAPER:
				{
					int stacks = Math.max(0, Math.min(5, ctx.getModifiers().getSoulreaperStacks()));
					specMin = maxHit * 6 * stacks / 100;
					maxHit = maxHit * (100 + 6 * stacks) / 100;
					break;
				}
				case FANG:
					specMin = maxHit * 3 / 20;
					break;
				default:
					break;
			}
		}

		if (maxHit <= 0)
		{
			return DpsResult.ZERO;
		}
		long defRoll = CombatRules.defenceRoll(m, defLevel, defBonus);
		boolean fang = wName.startsWith("osmumten's fang");
		double accuracy = HitChance.single(attRoll, defRoll);
		if (fang && style.getType() == AttackStyle.Type.STAB)
		{
			accuracy = CombatRules.toa(m) ? 1 - (1 - accuracy) * (1 - accuracy) : HitChance.twoAttackRolls(attRoll, defRoll);
		}
		accuracy = CombatRules.accuracy(m, l, accuracy);
		int speed = weapon.getAttackSpeed();
		EncounterDamage.Rule rule = EncounterDamage.rule(m, l);
		long minimum = EncounterDamage.named(m, "respiratory system") ? maxHit / 2 : 0;
		if (spec != null)
		{
			return SpecialDamage.melee(spec, l, ctx, rule, spec.isGuaranteed() ? 1 : accuracy, minimum + specMin, maxHit,
				attRoll, defRoll, speed);
		}

		double avgDamage;
		int displayMax = (int) maxHit;
		String detail = null;
		boolean scythe = wName.contains("scythe of vitur");
		if (scythe)
		{
			// Up to three hits at 100%, 50% and 25% damage depending on target size.
			int hits = m.getSize() >= 3 ? 3 : m.getSize() == 2 ? 2 : 1;
			avgDamage = 0;
			displayMax = 0;
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < hits; i++)
			{
				avgDamage += accuracy * EquipmentEffects.average(rule, l, ctx, i == 0 ? minimum : 0, maxHit >> i)
					+ (1 - accuracy) * rule.missDamage();
				int hitMax = EquipmentEffects.maximum(rule, l, ctx, maxHit >> i);
				displayMax += hitMax;
				sb.append(i == 0 ? "" : " + ").append(hitMax);
			}
			detail = hits > 1 ? sb.toString() : null;
		}
		else
		{
			avgDamage = accuracy * EquipmentEffects.average(rule, l, ctx, minimum, maxHit) + (1 - accuracy) * rule.missDamage();
			displayMax = EquipmentEffects.maximum(rule, l, ctx, maxHit);
			if (fang)
			{
				// Rolls between 15% and 85% of the max hit: same mean, lower max.
				displayMax = (int) (maxHit - maxHit * 15 / 100);
				detail = (maxHit * 15 / 100) + "-" + displayMax;
				long fangMin = maxHit * 15 / 100 + (minimum > 0 ? displayMax / 2 : 0);
				avgDamage = accuracy * EquipmentEffects.average(rule, l, ctx, fangMin, displayMax);
				displayMax = EquipmentEffects.maximum(rule, l, ctx, displayMax);
			}
		}
		if (EquipmentEffects.twoHit(weapon) || wName.equals("dual macuahuitl"))
		{
			long first = maxHit / 2;
			long second = maxHit - first;
			double secondAccuracy = wName.equals("dual macuahuitl") ? accuracy * accuracy : accuracy;
			avgDamage = accuracy * EquipmentEffects.average(rule, l, ctx, 0, first)
				+ (1 - accuracy) * rule.missDamage() + secondAccuracy * EquipmentEffects.average(rule, l, ctx, 0, second)
				+ (1 - secondAccuracy) * rule.missDamage();
			displayMax = EquipmentEffects.maximum(rule, l, ctx, first) + EquipmentEffects.maximum(rule, l, ctx, second);
			detail = EquipmentEffects.maximum(rule, l, ctx, first) + " + " + EquipmentEffects.maximum(rule, l, ctx, second);
		}
		if (wName.startsWith("keris") && m.hasAttribute("kalphite"))
		{
			avgDamage = (50 * avgDamage + accuracy * HitDamage.average(rule, 0, maxHit,
				h -> EquipmentEffects.damage(l, ctx, h * 3))) / 51;
			displayMax = EquipmentEffects.maximum(rule, l, ctx, maxHit * 3);
			detail = "1/51 triple-damage roll";
		}
		if (wName.equals("gadderhammer") && m.hasAttribute("shade"))
		{
			avgDamage = accuracy * (0.95 * HitDamage.average(rule, 0, maxHit,
				h -> EquipmentEffects.damage(l, ctx, h * 5 / 4)) + 0.05 * HitDamage.average(rule, 0, maxHit,
				h -> EquipmentEffects.damage(l, ctx, h * 2)));
			displayMax = EquipmentEffects.maximum(rule, l, ctx, maxHit * 2);
		}
		if (EquipmentEffects.verac(l))
		{
			avgDamage = 0.75 * avgDamage + 0.25 * EquipmentEffects.average(rule, l, ctx, 1, maxHit + 1);
			displayMax = EquipmentEffects.maximum(rule, l, ctx, maxHit + 1);
			accuracy = 0.75 * accuracy + 0.25;
			detail = "25% defence-bypassing roll";
		}
		if (rule.detail != null)
		{
			detail = (detail == null ? "" : detail + "; ") + rule.detail;
		}
		DpsResult r = result(avgDamage, displayMax, accuracy, speed).withDetail(detail);
		if (EquipmentEffects.bloodMoon(l))
		{
			// Bloodrager rolls 0..99 and triggers at <=32: exactly 33%, not one third.
			// The second hit requires the first to land; either proc saves only one tick.
			double procChance = 0.33 * accuracy + 0.33 * 0.67 * accuracy * accuracy;
			r = r.withExpectedSpeed(speed - procChance);
		}
		if (wName.equals("hallowfell") && ctx.getAoeTargets() > 1 && r.getDps() > 0)
		{
			// Two distinct secondary enemies, each at half max; no repeated hits on large enemies.
			int targets = Math.min(3, ctx.getAoeTargets());
			double cleave = accuracy * EquipmentEffects.average(rule, l, ctx, 0, maxHit / 2);
			r = r.withAreaDamage(cleave * (targets - 1), 0, targets);
		}
		else if (scythe && ctx.getAoeTargets() > 1 && r.getDps() > 0)
		{
			int primaryHits = Math.max(1, Math.min(3, m.getSize()));
			int extraTargets = Math.min(ctx.getAoeTargets() - 1, 3 - primaryHits);
			double extra = 0;
			for (int i = primaryHits; i < primaryHits + extraTargets; i++)
			{
				extra += accuracy * EquipmentEffects.average(rule, l, ctx, 0, maxHit >> i);
			}
			if (extraTargets > 0)
			{
				r = r.withAreaDamage(extra, 0, 1 + extraTargets);
			}
		}
		return r;
	}

	// ---------------------------------------------------------------- ranged

	private static DpsResult ranged(Loadout l, GearItem weapon, AttackStyle style, Bonuses b, CombatContext ctx,
		SpecialAttack spec)
	{
		Monster m = ctx.getMonster();
		String stance = style.getStance();
		int stanceBonus = stance.equals("accurate") ? 3 : 0;

		OffensivePrayer prayer = ctx.getPrayer(CombatClass.RANGED);
		double pAcc = prayer == null ? 1 : 1 + prayer.getAccuracyPercent() / 100;
		double pDmg = prayer == null ? 1 : 1 + prayer.getDamagePercent() / 100;

		// The eclipse atlatl and Hunter's spear hit with the (visible, potion-boosted) Strength level and melee strength bonus;
		// ranged prayers and the void ranger helm still boost it. Accuracy is ordinary ranged accuracy.
		boolean atlatl = WeaponRules.scalesWithStrength(weapon);
		int effAcc = (int) Math.floor(ctx.getRanged() * pAcc) + stanceBonus + 8;
		int effStr = (int) Math.floor((atlatl ? ctx.getStrength() : ctx.getRanged()) * pDmg) + stanceBonus + 8;
		if (hasVoid(l, "void ranger helm"))
		{
			effAcc = effAcc * 11 / 10;
			effStr = isEliteVoid(l) ? effStr * 1125 / 1000 : effStr * 11 / 10;
		}

		long attRoll = (long) effAcc * (b.ranged + 64);
		long maxHit = (effStr * (long) ((atlatl ? b.meleeStr : b.rangedStr) + 64) + 320) / 640;
		String wName = lower(weapon);
		boolean crystal = wName.startsWith("bow of faerdhinen") || wName.startsWith("crystal bow")
			&& !wName.contains("basic") && !wName.contains("attuned") && !wName.contains("perfected");
		if (crystal)
		{
			int pieces = (named(l, Slot.HEAD, "crystal helm") ? 1 : 0)
				+ (named(l, Slot.BODY, "crystal body") ? 3 : 0) + (named(l, Slot.LEGS, "crystal legs") ? 2 : 0);
			attRoll = frac(attRoll, 100 + pieces * 5, 100);
			maxHit = frac(maxHit, 1000 + pieces * 25, 1000);
		}
		boolean taskDamage = false;

		int salve = salveTier(l.get(Slot.NECK));
		boolean avarice = named(l, Slot.NECK, "amulet of avarice")
			&& m.getLowerName().startsWith("revenant");
		int avariceFactor = ctx.getModifiers().isForinthrySurge() ? 27 : 24;
		if (avarice)
		{
			attRoll = frac(attRoll, avariceFactor, 20);
		}
		else if (m.hasAttribute("undead") && (salve == SALVE_I || salve == SALVE_EI))
		{
			attRoll = frac(attRoll, salve == SALVE_EI ? 6 : 7, salve == SALVE_EI ? 5 : 6);
		}
		else if (ctx.isOnTask() && isImbuedSlayerHelm(l.get(Slot.HEAD)))
		{
			attRoll = frac(attRoll, 23, 20);
		}
		if (avarice)
		{
			maxHit = frac(maxHit, avariceFactor, 20);
		}
		else if (m.hasAttribute("undead") && (salve == SALVE_I || salve == SALVE_EI || atlatl && salve != SALVE_NONE))
		{
			boolean enchanted = salve == SALVE_E || salve == SALVE_EI;
			maxHit = frac(maxHit, enchanted ? 6 : 7, enchanted ? 5 : 6);
		}
		else if (ctx.isOnTask() && (atlatl ? isSlayerHelm(l.get(Slot.HEAD)) : isImbuedSlayerHelm(l.get(Slot.HEAD))))
		{
			if (atlatl)
			{
				maxHit = frac(maxHit, 7, 6);
			}
			else
			{
				taskDamage = true;
			}
		}
		if (wName.startsWith("twisted bow"))
		{
			if (taskDamage)
			{
				maxHit = frac(maxHit, 23, 20);
				taskDamage = false;
			}
			int magic = Math.min(m.hasAttribute("xerician") ? 350 : 250,
				Math.max(m.getMagicLevel(), m.getMagicAttackBonus()));
			int t = 3 * magic / 10;
			int accPct = Math.max(0, Math.min(140, 140 + (3 * magic - 10) / 100 - (t - 100) * (t - 100) / 100));
			int dmgPct = Math.max(0, Math.min(250, 250 + (3 * magic - 14) / 100 - (t - 140) * (t - 140) / 100));
			attRoll = frac(attRoll, accPct, 100);
			if (CombatRules.wardenP2(m))
			{
				attRoll = frac(attRoll, accPct, 100);
			}
			maxHit = frac(maxHit, dmgPct, 100);
		}
		int extraDamage = taskDamage ? 15 : 0;
		if (wName.startsWith("dragon hunter crossbow") && m.hasAttribute("dragon"))
		{
			attRoll = frac(attRoll, 13, 10);
			extraDamage += 25;
		}
		if (wName.startsWith("scorching bow") && m.hasAttribute("demon"))
		{
			int bonus = CombatRules.demonBonus(m, 30);
			attRoll = frac(attRoll, 100 + bonus, 100);
			extraDamage += bonus;
		}
		if (EquipmentEffects.wildernessWeapon(l, ctx))
		{
			attRoll = frac(attRoll, 3, 2);
			extraDamage += 50;
		}
		maxHit = frac(maxHit, 100 + extraDamage, 100);
		if (wName.equals("ogre bow") || wName.equals("comp ogre bow"))
		{
			GearItem ammo = l.get(Slot.AMMO);
			maxHit = ((ctx.getRanged() + 10L) * (64 + (ammo == null ? 0 : ammo.getRangedStr())) + 320) / 640;
		}
		if (wName.equals("holy water"))
		{
			maxHit = ((ctx.getRanged() + 10L) * (64 + weapon.getRangedStr()) + 320) / 640;
			maxHit = frac(maxHit, 100 + CombatRules.demonBonus(m, 60), 100);
			if (EncounterDamage.named(m, "nezikchened"))
			{
				maxHit += 5;
			}
		}
		if (EquipmentEffects.ratWeapon(weapon) && m.hasAttribute("rat"))
		{
			maxHit += 10;
		}
		if (wName.startsWith("tonalztics of ralos"))
		{
			maxHit = frac(maxHit, 3, 4);
		}
		long specMin = 0;
		if (spec != null)
		{
			switch (spec.getKind())
			{
				case ARROW_FORMULA:
					// The bows' specials ignore every other damage bonus.
					maxHit = SpecialDamage.arrowFormula(l, ctx);
					break;
				case WEBWEAVER:
					maxHit -= maxHit * 6 / 10;
					break;
				case DARK_BOW:
				{
					boolean dragonArrows = lower(l.get(Slot.AMMO)).startsWith("dragon arrow");
					specMin = dragonArrows ? 8 : 5;
					maxHit = maxHit * (dragonArrows ? 15 : 13) / 10;
					break;
				}
				default:
					specMin = spec.minimum(maxHit);
					maxHit = spec.scaleMax(maxHit);
					break;
			}
		}

		int defBonus;
		String sub = weapon.getSubcategory();
		if (sub.equals("salamander"))
		{
			defBonus = (m.getDefLightOrStandard() + m.getDefRanged() + m.getDefHeavyOrStandard()) / 3;
		}
		else if (sub.equals("crossbow") && !wName.startsWith("karil's crossbow"))
		{
			defBonus = m.getDefHeavyOrStandard();
		}
		else if (sub.equals("chinchompa"))
		{
			defBonus = m.getDefHeavyOrStandard();
			int distance = AttackReach.distance(m, ctx.getTargetDistance());
			int band = distance <= 3 ? 0 : distance <= 6 ? 1 : 2;
			int preferred = stance.equals("accurate") ? 0 : stance.equals("rapid") ? 1 : 2;
			int percent = preferred == band ? 100 : preferred == 1 || band == 1 ? 75 : 50;
			attRoll = frac(attRoll, percent, 100);
		}
		else if (sub.equals("thrown"))
		{
			defBonus = m.getDefLightOrStandard();
		}
		else
		{
			defBonus = m.getDefRanged();
		}
		if (spec != null)
		{
			attRoll = spec.scaleAccuracy(attRoll);
		}
		if (maxHit <= 0)
		{
			return DpsResult.ZERO;
		}
		if (CombatRules.phase(m, EncounterPhase.ROYAL_TITANS_OUT_OF_MELEE))
		{
			// Ranged attacks are six times as accurate while the titan is out of melee range.
			attRoll *= 6;
		}
		long defRoll = CombatRules.defenceRoll(m, m.getDefenceLevel(), defBonus);
		double accuracy = HitChance.single(attRoll, defRoll);
		accuracy = CombatRules.accuracy(m, l, accuracy);
		int speed = weapon.getAttackSpeed() - (stance.equals("rapid") ? 1 : 0);
		long[] bounds = CombatRules.bounds(m, attRoll, defRoll, maxHit);
		long minimum = bounds[0];
		maxHit = bounds[1];
		EncounterDamage.Rule rule = EncounterDamage.rule(m, l);
		if (spec != null)
		{
			return SpecialDamage.ranged(spec, l, ctx, rule, spec.isGuaranteed() ? 1 : accuracy,
				Math.max(minimum, specMin), maxHit, attRoll, defBonus, speed);
		}
		double avgDamage = accuracy * EquipmentEffects.average(rule, l, ctx, minimum, maxHit);
		long rawMax = maxHit;
		maxHit = EquipmentEffects.maximum(rule, l, ctx, maxHit);
		String detail = rule.detail;
		if (wName.startsWith("dark bow") || wName.startsWith("tonalztics of ralos") && !wName.contains("uncharged"))
		{
			avgDamage *= 2;
			detail = maxHit + " + " + maxHit + (detail == null ? "" : "; " + detail);
			maxHit *= 2;
		}
		if (EquipmentEffects.karil(l))
		{
			avgDamage += accuracy * 0.25 * HitDamage.average(rule, 0, rawMax, h -> h / 2);
			maxHit += rule.maximum(rawMax / 2);
			detail = "25% second hitsplat at half the first damage";
		}
		if (sub.equals("crossbow") && WeaponRules.firesAmmoSlot(weapon))
		{
			EnchantedBolts.Result bolts = EnchantedBolts.calculate(l, ctx, rule, accuracy, minimum, rawMax);
			if (bolts != null)
			{
				avgDamage = bolts.getAverage();
				maxHit = bolts.getMaximum();
				accuracy = bolts.getAccuracy();
				detail = bolts.getDetail() + (detail == null ? "" : "; " + detail);
			}
		}
		DpsResult r = result(avgDamage, (int) maxHit, accuracy, speed).withDetail(detail);
		if (ctx.getAoeTargets() > 1 && r.getDps() > 0)
		{
			if (sub.equals("chinchompa"))
			{
				int targets = Math.min(wName.startsWith("black chinchompa") ? 12 : 11, ctx.getAoeTargets());
				r = r.withAreaDamage(avgDamage * (targets - 1), 0, targets);
			}
			else if (wName.startsWith("venator bow") && !wName.contains("uncharged"))
			{
				double bounce = accuracy * EquipmentEffects.average(rule, l, ctx, 0, rawMax * 2 / 3);
				int targets = Math.min(3, ctx.getAoeTargets());
				r = r.withAreaDamage(2 * bounce, targets == 2 ? bounce : 0, targets,
					targets == 2 ? rule.maximum(rawMax * 2 / 3) : 0);
			}
		}
		return r;
	}

	// ---------------------------------------------------------------- magic

	private static DpsResult magic(Loadout l, GearItem weapon, AttackStyle style, Bonuses b, CombatContext ctx,
		SpecialAttack spec)
	{
		Monster m = ctx.getMonster();
		if (spec != null && style.isAutocast() && SpecialDamage.builtInSpell(weapon))
		{
			// These staves' specials cast their own built-in spell, as a powered staff on its accurate stance.
			l = l.copy();
			l.setSpell(null);
			l.setStyle(AttackStyle.parse("accurate,magic,accurate"));
			style = l.getStyle();
		}
		int baseMax;
		int speed;
		int stanceBonus;
		if (weapon.getSubcategory().equals("salamander"))
		{
			baseMax = WeaponRules.salamanderMaxHit(weapon, ctx.getMagic());
			speed = weapon.getAttackSpeed();
			stanceBonus = 0;
		}
		else if (style.isAutocast())
		{
			if (l.getSpell() == null)
			{
				return DpsResult.ZERO;
			}
			baseMax = SpellRules.maxHit(l, ctx);
			speed = l.getSpell().getAttackSpeed();
			if (lower(weapon).startsWith("harmonised nightmare staff")
				&& "standard".equalsIgnoreCase(l.getSpell().getSpellbook()))
			{
				speed = 4;
			}
			if (lower(weapon).startsWith("twinflame staff"))
			{
				speed = 6;
			}
			stanceBonus = 0;
		}
		else
		{
			baseMax = spec != null ? SpecialDamage.builtInMax(weapon, ctx.getMagic())
				: WeaponRules.poweredStaffMaxHit(weapon, ctx.getMagic());
			if (baseMax <= 0)
			{
				return DpsResult.ZERO;
			}
			speed = weapon.getAttackSpeed();
			stanceBonus = style.getStance().equals("accurate") ? 2 : 0;
		}
		String wName = lower(weapon);
		if (spec != null && spec.getName().equals("Eye of Ayak"))
		{
			// Eye of Ayak's special raises the base damage and attacks every five ticks.
			baseMax = (int) spec.scaleMax(baseMax);
			speed = 5;
		}
		String spell = EncounterDamage.spell(l);
		String element = SpellRules.element(l.getSpell());
		boolean standard = l.getSpell() != null && "standard".equalsIgnoreCase(l.getSpell().getSpellbook());
		boolean ancient = l.getSpell() != null && "ancient".equalsIgnoreCase(l.getSpell().getSpellbook());
		if (standard && spell.endsWith(" bolt") && named(l, Slot.HANDS, "chaos gauntlets"))
		{
			baseMax += 3;
		}
		if (!element.isEmpty() && (named(l, Slot.NECK, "elemental amulet") || named(l, Slot.NECK, "amulet of " + element)))
		{
			baseMax += 2;
		}
		if (SupportSpell.charge(l, ctx))
		{
			baseMax += 10;
		}

		OffensivePrayer prayer = ctx.getPrayer(CombatClass.MAGIC);
		double pAcc = prayer == null ? 1 : 1 + prayer.getAccuracyPercent() / 100;
		double dmgPct = prayer == null ? 0 : prayer.getDamagePercent();

		int effMagic = (int) Math.floor(ctx.getMagic() * pAcc) + stanceBonus + 9;
		boolean voidMage = hasVoid(l, "void mage helm");
		if (voidMage)
		{
			effMagic = effMagic * 29 / 20;
			if (isEliteVoid(l))
			{
				dmgPct += 5;
			}
		}

		int magicBonus = b.magic;
		double gearDmg = b.magicStr;
		if (ancient)
		{
			gearDmg += (named(l, Slot.HEAD, "virtus mask") ? 3 : 0)
				+ (named(l, Slot.BODY, "virtus robe top") ? 3 : 0) + (named(l, Slot.LEGS, "virtus robe bottom") ? 3 : 0);
		}
		if (WeaponRules.isShadow(weapon) && !style.isAutocast())
		{
			int factor = CombatRules.toa(m) ? 4 : 3;
			magicBonus *= factor;
			gearDmg = Math.min(100, gearDmg * factor);
		}
		dmgPct += gearDmg;

		long attRoll = (long) effMagic * (magicBonus + 64);
		long baseRoll = attRoll;
		int additiveAccuracy = 0;
		boolean smoke = standard && (wName.startsWith("smoke battlestaff") || wName.startsWith("mystic smoke staff"));
		if (smoke)
		{
			dmgPct += 10;
			additiveAccuracy += 10;
		}
		boolean task = false;

		int salve = salveTier(l.get(Slot.NECK));
		if (m.hasAttribute("undead") && (salve == SALVE_I || salve == SALVE_EI))
		{
			int bonus = salve == SALVE_EI ? 20 : 15;
			additiveAccuracy += bonus;
			dmgPct += bonus;
		}
		else if (named(l, Slot.NECK, "amulet of avarice") && m.getLowerName().startsWith("revenant"))
		{
			int bonus = ctx.getModifiers().isForinthrySurge() ? 35 : 20;
			additiveAccuracy += bonus;
			dmgPct += bonus;
		}
		else if (ctx.isOnTask() && isImbuedSlayerHelm(l.get(Slot.HEAD)))
		{
			task = true;
		}
		if (EquipmentEffects.vampyre(ctx) && named(l, Slot.RING, "efaritay's aid") && EncounterImmunities.silver(l))
		{
			additiveAccuracy += 15;
		}
		attRoll = frac(attRoll, 100 + additiveAccuracy, 100);
		long maxHit = (long) Math.floor(baseMax * (1 + dmgPct / 100));
		if (task)
		{
			attRoll = frac(attRoll, 23, 20);
			maxHit = frac(maxHit, 23, 20);
		}
		if (m.hasAttribute("dragon") && wName.startsWith("dragon hunter wand"))
		{
			attRoll = frac(attRoll, 7, 4);
			maxHit = frac(maxHit, 7, 5);
		}
		boolean demonbane = SupportSpell.demonbane(l, ctx);
		if (demonbane)
		{
			int bonus = ctx.getModifiers().isMarkOfDarkness() ? 40 : 20;
			if (wName.startsWith("purging staff"))
			{
				bonus *= 2;
			}
			attRoll = frac(attRoll, 100 + CombatRules.demonBonus(m, bonus), 100);
		}
		if (EquipmentEffects.wildernessWeapon(l, ctx))
		{
			attRoll = frac(attRoll, 3, 2);
			maxHit = frac(maxHit, 3, 2);
		}
		if (standard && named(l, Slot.SHIELD, "tome of water")
			&& !lower(l.get(Slot.SHIELD)).contains("empty") && element.equals("water"))
		{
			attRoll = frac(attRoll, 6, 5);
		}
		if (spec != null)
		{
			attRoll = spec.scaleAccuracy(attRoll);
			if (!spec.getName().equals("Eye of Ayak"))
			{
				maxHit = spec.scaleMax(maxHit);
			}
		}
		if (!element.isEmpty() && element.equalsIgnoreCase(m.getWeaknessType()))
		{
			attRoll += frac(baseRoll, Math.max(0, m.getWeakness()), 100);
			maxHit += frac(baseMax, Math.max(0, m.getWeakness()), 100);
		}
		long minimum = ctx.getModifiers().isSunfireRunes() && element.equals("fire") ? maxHit / 10 : 0;
		String shield = lower(l.get(Slot.SHIELD));
		if (standard && !shield.contains("empty") && !shield.contains("uncharged")
			&& (shield.startsWith("tome of fire") && element.equals("fire")
			|| shield.startsWith("tome of water") && element.equals("water")
			|| shield.startsWith("tome of earth") && element.equals("earth")))
		{
			maxHit = frac(maxHit, 11, 10);
			minimum = frac(minimum, 11, 10);
		}
		if (wName.equals("dawnbringer"))
		{
			maxHit = Math.max(2, maxHit / 2);
		}
		int defenceLevel = CombatRules.defenceBasedMagic(m) ? m.getDefenceLevel() : m.getMagicLevel();
		if (maxHit <= 0)
		{
			return DpsResult.ZERO;
		}
		long defRoll = CombatRules.defenceRoll(m, defenceLevel, m.getDefMagic());
		boolean confliction = named(l, Slot.HANDS, "confliction gauntlets") && !weapon.isTwoHanded();
		double accuracy = magicAccuracy(attRoll, defRoll, confliction);
		if (named(l, Slot.RING, "brimstone ring"))
		{
			accuracy = 0.75 * accuracy + 0.25 * magicAccuracy(attRoll, defRoll * 9 / 10, confliction);
		}
		if (wName.startsWith("ice ancient sceptre") && spell.startsWith("ice "))
		{
			accuracy = iceSceptreAccuracy(m, l, attRoll, defRoll, confliction, accuracy, spell, speed);
		}
		accuracy = CombatRules.accuracy(m, l, accuracy);
		long[] bounds = CombatRules.bounds(m, attRoll, defRoll, maxHit);
		if (CombatRules.wardenP2(m) || EncounterDamage.named(m, "respiratory system"))
		{
			minimum = bounds[0];
			maxHit = bounds[1];
		}
		EncounterDamage.Rule rule = EncounterDamage.rule(m, l);
		if (spec != null)
		{
			boolean dawnbringer = spec.getKind() == SpecialAttack.Kind.DAWNBRINGER;
			// Dawnbringer's special always hits for 75-150, ignoring bonuses.
			long low = dawnbringer ? 75 : minimum;
			long high = dawnbringer ? 150 : maxHit;
			double hit = spec.isGuaranteed() ? 1 : accuracy;
			return result(hit * EquipmentEffects.average(rule, l, ctx, low, high) + (1 - hit) * rule.missDamage(),
				EquipmentEffects.maximum(rule, l, ctx, high), hit, speed)
				.withDetail(spec.describe() + (rule.detail == null ? "" : "; " + rule.detail));
		}
		double average = accuracy * EquipmentEffects.average(rule, l, ctx, minimum, maxHit);
		int displayMax = EquipmentEffects.maximum(rule, l, ctx, maxHit);
		if (wName.startsWith("sanguinesti staff") || wName.startsWith("holy sanguinesti staff"))
		{
			average = 0.8 * average + 0.2 * accuracy * HitDamage.average(rule, minimum, maxHit, h -> h + 8);
			displayMax = rule.maximum(maxHit + 8);
		}
		if (EquipmentEffects.ahrim(l))
		{
			average = 0.75 * average + 0.25 * accuracy * HitDamage.average(rule, minimum, maxHit, h -> h * 13 / 10);
			displayMax = rule.maximum(maxHit * 13 / 10);
		}
		if (SupportSpell.markOfDarkness(l, ctx))
		{
			int percent = wName.startsWith("purging staff") ? 50 : 25;
			average = accuracy * HitDamage.average(rule, minimum, maxHit,
				h -> h + h * percent / 100 * CombatRules.demonVulnerability(m) / 100);
			displayMax = rule.maximum(maxHit + maxHit * percent / 100 * CombatRules.demonVulnerability(m) / 100);
		}
		if (wName.startsWith("twinflame staff") && standard
			&& (spell.endsWith(" bolt") || spell.endsWith(" blast") || spell.endsWith(" wave")))
		{
			average += accuracy * HitDamage.averageTransformed(rule, minimum, maxHit, h -> Math.max(1, h) * 4 / 10);
			displayMax += rule.maximum(maxHit * 4 / 10);
		}
		DpsResult r = result(average, displayMax, accuracy, speed).withDetail(rule.detail);
		if (style.isAutocast() && l.getSpell() != null && "ancient".equalsIgnoreCase(l.getSpell().getSpellbook())
			&& (spell.endsWith(" burst") || spell.endsWith(" barrage")) && ctx.getAoeTargets() > 1 && r.getDps() > 0)
		{
			int targets = Math.min(9, ctx.getAoeTargets());
			r = r.withAreaDamage(average * (targets - 1), 0, targets);
		}
		return r;
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * Ice ancient sceptre: ice spells gain 10% accuracy against freezable targets that are not frozen.
	 * A landed cast freezes for the spell's duration (+10% from the sceptre, less any freeze resistance),
	 * then five ticks of immunity; casts in that window get no bonus, and every cast after it does until
	 * one lands. Returns the mean accuracy over that cycle.
	 */
	private static double iceSceptreAccuracy(Monster m, Loadout l, long attRoll, long defRoll, boolean confliction,
		double accuracy, String spell, int speed)
	{
		StatusImmunities.Immunity immunity = StatusImmunities.of(m);
		int resistance = immunity.getFreezeResistance();
		boolean freezable = resistance >= 0 ? resistance < 100 : !m.hasAttribute("boss");
		if (!freezable)
		{
			return accuracy;
		}
		double bonus = magicAccuracy(attRoll * 11 / 10, defRoll, confliction);
		if (named(l, Slot.RING, "brimstone ring"))
		{
			bonus = 0.75 * bonus + 0.25 * magicAccuracy(attRoll * 11 / 10, defRoll * 9 / 10, confliction);
		}
		if (bonus <= 0)
		{
			return accuracy;
		}
		int freeze = spell.endsWith("rush") ? 8 : spell.endsWith("burst") ? 17 : spell.endsWith("blitz") ? 26 : 35;
		freeze = freeze * (100 - Math.max(0, resistance)) / 100;
		int unbonused = Math.max(0, (int) Math.ceil((freeze + 5.0) / speed) - 1);
		double bonusCasts = 1 / bonus;
		double share = bonusCasts / (bonusCasts + unbonused);
		return share * bonus + (1 - share) * accuracy;
	}

	private static int specDefenceBonus(Monster m, String defence)
	{
		switch (defence)
		{
			case "stab":
				return m.getDefStab();
			case "slash":
				return m.getDefSlash();
			case "crush":
				return m.getDefCrush();
			case "ranged":
				return m.getDefRanged();
			default:
				return m.getDefMagic();
		}
	}

	/** Magic accuracy; with one-handed confliction gauntlets, the attack after a miss rolls accuracy twice. */
	private static double magicAccuracy(long attack, long defence, boolean confliction)
	{
		double single = HitChance.single(attack, defence);
		return confliction ? HitChance.withRetryAfterMiss(single, HitChance.twoAttackRolls(attack, defence)) : single;
	}

	static DpsResult result(double avgDamage, int maxHit, double accuracy, int speed)
	{
		if (speed <= 0 || maxHit <= 0)
		{
			return new DpsResult(0, Math.max(0, maxHit), accuracy, speed, 0, null);
		}
		return new DpsResult(avgDamage / (speed * SECONDS_PER_TICK), maxHit, accuracy, speed, avgDamage, null);
	}

	private static long frac(long value, long num, long den)
	{
		return value * num / den;
	}

	private static final int SALVE_NONE = 0;
	private static final int SALVE = 1;
	private static final int SALVE_I = 2;
	private static final int SALVE_E = 3;
	private static final int SALVE_EI = 4;

	private static int salveTier(GearItem neck)
	{
		String n = lower(neck);
		if (!n.startsWith("salve amulet"))
		{
			return SALVE_NONE;
		}
		String rest = n.substring("salve amulet".length()).replace(" ", "");
		switch (rest)
		{
			case "(ei)":
				return SALVE_EI;
			case "(e)":
				return SALVE_E;
			case "(i)":
				return SALVE_I;
			default:
				return SALVE;
		}
	}

	static boolean isSlayerHelm(GearItem head)
	{
		String n = lower(head);
		return n.startsWith("slayer helmet") || n.startsWith("black mask")
			|| (n.endsWith("slayer helmet") || n.endsWith("slayer helmet (i)"));
	}

	static boolean isImbuedSlayerHelm(GearItem head)
	{
		return isSlayerHelm(head) && lower(head).contains("(i)");
	}

	private static boolean hasVoid(Loadout l, String helm)
	{
		String body = lower(l.get(Slot.BODY));
		String legs = lower(l.get(Slot.LEGS));
		return named(l, Slot.HEAD, helm)
			&& lower(l.get(Slot.HANDS)).startsWith("void knight gloves")
			&& (body.startsWith("void knight top") || body.startsWith("elite void top"))
			&& (legs.startsWith("void knight robe") || legs.startsWith("elite void robe"));
	}

	private static boolean isEliteVoid(Loadout l)
	{
		return lower(l.get(Slot.BODY)).startsWith("elite void top") && lower(l.get(Slot.LEGS)).startsWith("elite void robe");
	}

	private static boolean named(Loadout l, Slot slot, String prefix)
	{
		return EquipmentEffects.named(l, slot, prefix);
	}

	private static String lower(GearItem item)
	{
		return EncounterDamage.lower(item);
	}

	/**
	 * Summed equipment bonuses. Ranged stats of the ammo slot only count when the weapon fires it.
	 */
	static final class Bonuses
	{
		private static final java.util.Set<String> QUIVERS = new java.util.HashSet<>(java.util.Arrays.asList(
			"dizana's quiver", "blessed dizana's quiver", "dizana's max cape"));

		int stab;
		int slash;
		int crush;
		int ranged;
		int magic;
		int meleeStr;
		int rangedStr;
		double magicStr;

		static Bonuses of(Loadout l)
		{
			Bonuses b = new Bonuses();
			GearItem weapon = l.getWeapon();
			boolean firesAmmo = WeaponRules.firesAmmoSlot(weapon);
			for (Slot slot : SLOTS)
			{
				GearItem item = l.get(slot);
				if (item == null)
				{
					continue;
				}
				b.stab += item.getStabBonus();
				b.slash += item.getSlashBonus();
				b.crush += item.getCrushBonus();
				b.magic += item.getMagicBonus();
				b.meleeStr += item.getMeleeStr();
				b.magicStr += item.getMagicStr();
				if (slot != Slot.AMMO || firesAmmo)
				{
					b.ranged += item.getRangedBonus();
					b.rangedStr += item.getRangedStr();
				}
			}
			GearItem loaded = l.getLoadedAmmo();
			if (loaded != null && WeaponRules.loadsAmmo(weapon))
			{
				b.rangedStr += loaded.getRangedStr();
			}
			// A charged Dizana's quiver adds +10 ranged accuracy and +1 ranged strength to fired arrows and bolts.
			GearItem ammo = l.get(Slot.AMMO);
			if (firesAmmo && ammo != null && weapon.getAmmunition().contains(ammo.getCombatId())
				&& QUIVERS.contains(lower(l.get(Slot.CAPE))))
			{
				b.ranged += 10;
				b.rangedStr += 1;
			}
			if (lower(weapon).contains("bulwark"))
			{
				int defence = 0;
				for (Slot slot : SLOTS)
				{
					GearItem item = l.get(slot);
					if (item != null)
					{
						defence += item.getStabDef() + item.getSlashDef() + item.getCrushDef() + item.getRangedDef();
					}
				}
				b.meleeStr += Math.max(0, (defence - 800) / 12 - 38);
			}
			return b;
		}
	}
}
