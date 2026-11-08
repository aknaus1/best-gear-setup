package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.NameLists;
import com.bestgearsetup.data.Slot;

/** Equipment predicates shared by calculation and optimizer set seeds. */
final class EquipmentEffects
{
	private static final NameLists.NameList TWO_HIT = NameLists.list("two-hit weapons");
	private static final NameLists.NameList RAT_WEAPONS = NameLists.list("rat weapons");
	private static final NameLists.NameList WILDERNESS_MELEE = NameLists.list("wilderness melee weapons");
	private static final NameLists.NameList WILDERNESS_RANGED = NameLists.list("wilderness ranged weapons");
	private static final NameLists.NameList WILDERNESS_MAGIC = NameLists.list("wilderness magic weapons");
	private static final NameLists.NameTable PICKAXE_BONUS = NameLists.table("guardian pickaxe bonus");
	private static final NameLists.NameTable VAMPYREBANE_DAMAGE = NameLists.table("vampyrebane damage percent");

	private EquipmentEffects()
	{
	}

	static boolean named(Loadout l, Slot slot, String name)
	{
		return namedWeapon(l.get(slot), name);
	}

	static boolean namedWeapon(GearItem item, String name)
	{
		String actual = EncounterDamage.lower(item);
		// "name (" is a case of "name ": test the prefix first, without building strings.
		return actual.startsWith(name) && (actual.length() == name.length() || actual.charAt(name.length()) == ' ')
			&& !actual.contains("uncharged") && !actual.contains("inactive") && !actual.contains("broken");
	}

	static boolean set(Loadout l, String head, String body, String legs, String weapon)
	{
		return named(l, Slot.HEAD, head) && named(l, Slot.BODY, body) && named(l, Slot.LEGS, legs)
			&& named(l, Slot.WEAPON, weapon);
	}

	static boolean damned(Loadout l)
	{
		return named(l, Slot.NECK, "amulet of the damned");
	}

	static boolean verac(Loadout l)
	{
		return set(l, "verac's helm", "verac's brassard", "verac's plateskirt", "verac's flail");
	}

	static boolean dharok(Loadout l)
	{
		return set(l, "dharok's helm", "dharok's platebody", "dharok's platelegs", "dharok's greataxe");
	}

	static boolean ahrim(Loadout l)
	{
		return damned(l) && set(l, "ahrim's hood", "ahrim's robetop", "ahrim's robeskirt", "ahrim's staff");
	}

	static boolean karil(Loadout l)
	{
		return damned(l) && set(l, "karil's coif", "karil's leathertop", "karil's leatherskirt", "karil's crossbow");
	}

	static boolean bloodMoon(Loadout l)
	{
		return set(l, "blood moon helm", "blood moon chestplate", "blood moon tassets", "dual macuahuitl");
	}

	static boolean twoHit(GearItem w)
	{
		String name = EncounterDamage.lower(w);
		return TWO_HIT.matches(name);
	}

	static boolean ratWeapon(GearItem w)
	{
		String name = EncounterDamage.lower(w);
		return RAT_WEAPONS.matches(name);
	}

	static boolean wildernessWeapon(Loadout l, CombatContext ctx)
	{
		String name = EncounterDamage.lower(l.getWeapon());
		if (!ctx.getModifiers().isWilderness() || name.contains("uncharged") || name.endsWith("(u)"))
		{
			return false;
		}
		CombatClass cls = WeaponRules.classOf(l.getStyle());
		return (cls == CombatClass.MELEE ? WILDERNESS_MELEE : cls == CombatClass.RANGED ? WILDERNESS_RANGED
			: WILDERNESS_MAGIC).matches(name);
	}

	static boolean vampyre(CombatContext ctx)
	{
		return ctx.getMonster().hasAttribute("vampyre (t1)") || ctx.getMonster().hasAttribute("vampyre (t2)")
			|| ctx.getMonster().hasAttribute("vampyre (t3)");
	}

	/** Effects that scale each rolled hit, rather than increasing the roll's maximum. */
	static long damage(Loadout l, CombatContext ctx, long hit)
	{
		String weapon = EncounterDamage.lower(l.getWeapon());
		if (l.getStyle().isMelee())
		{
			if (dharok(l))
			{
				int hp = ctx.getLevels().getHitpoints();
				hit = hit * (10000L + (hp - ctx.getModifiers().playerHp(ctx.getLevels())) * hp) / 10000;
			}
			if ((weapon.startsWith("toktz-") || weapon.startsWith("tzhaar-ket-"))
				&& named(l, Slot.NECK, "berserker necklace"))
			{
				hit = hit * 6 / 5;
			}
			if (EncounterDamage.guardian(ctx.getMonster()))
			{
				int pick = PICKAXE_BONUS.get(weapon, 61);
				hit = hit * (50 + Math.max(1, Math.min(99, ctx.getModifiers().getMiningLevel())) + pick) / 150;
			}
		}
		if (vampyre(ctx))
		{
			int percent = VAMPYREBANE_DAMAGE.get(weapon,
				ctx.getMonster().hasAttribute("vampyre (t1)") && EncounterImmunities.silver(l) ? 110 : 100);
			if (percent != 100)
			{
				if (named(l, Slot.RING, "efaritay's aid"))
				{
					hit = hit * 11 / 10;
				}
				hit = hit * percent / 100;
			}
		}
		if (CombatRules.tormentedBonus(ctx.getMonster(), l))
		{
			int speed = l.getSpell() != null ? l.getSpell().getAttackSpeed() : WeaponRules.attackSpeed(l.getWeapon(), l.getStyle());
			if (l.getSpell() != null && weapon.startsWith("harmonised nightmare staff")
				&& "standard".equalsIgnoreCase(l.getSpell().getSpellbook()))
			{
				speed = 4;
			}
			else if (l.getSpell() != null && weapon.startsWith("twinflame staff"))
			{
				speed = 6;
			}
			hit += Math.max(0, speed * speed - 16);
		}
		if (seeking(l))
		{
			// Seeking broad arrows raise successful hits to at least 3.
			hit = Math.max(3, hit);
		}
		return hit;
	}

	static boolean seeking(Loadout l)
	{
		return l.getStyle().getType() == AttackStyle.Type.RANGED && WeaponRules.firesAmmoSlot(l.getWeapon())
			&& EncounterDamage.lower(l.get(Slot.AMMO)).startsWith("seeking broad");
	}

	static boolean rolledEffect(Loadout l, CombatContext ctx)
	{
		String weapon = EncounterDamage.lower(l.getWeapon());
		return vampyre(ctx) || CombatRules.tormentedBonus(ctx.getMonster(), l) || seeking(l)
			|| l.getStyle().isMelee() && (dharok(l) || EncounterDamage.guardian(ctx.getMonster())
			|| (weapon.startsWith("toktz-") || weapon.startsWith("tzhaar-ket-"))
			&& named(l, Slot.NECK, "berserker necklace"));
	}

	static double average(EncounterDamage.Rule rule, Loadout l, CombatContext ctx, long min, long max)
	{
		return rolledEffect(l, ctx) ? HitDamage.average(rule, min, max, h -> damage(l, ctx, h))
			: HitDamage.average(rule, min, max);
	}

	static int maximum(EncounterDamage.Rule rule, Loadout l, CombatContext ctx, long max)
	{
		return rule.maximum(Math.max(1, damage(l, ctx, max)));
	}
}
