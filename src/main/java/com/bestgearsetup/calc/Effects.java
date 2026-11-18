package com.bestgearsetup.calc;

import com.bestgearsetup.data.*;
import static com.bestgearsetup.calc.Encounter.*;

/** Equipment predicates shared by calculation and optimizer set seeds. */
final class Effects
{
	private static final Names.NameList TWO_HIT = Names.list("two-hit weapons");
	private static final Names.NameList RAT_WEAPONS = Names.list("rat weapons");
	private static final Names.NameList WILDERNESS_MELEE = Names.list("wilderness melee weapons");
	private static final Names.NameList WILDERNESS_RANGED = Names.list("wilderness ranged weapons");
	private static final Names.NameList WILDERNESS_MAGIC = Names.list("wilderness magic weapons");
	private static final Names.NameTable PICKAXE_BONUS = Names.table("guardian pickaxe bonus");
	private static final Names.NameTable VAMPYREBANE_DAMAGE = Names.table("vampyrebane damage percent");

	private Effects()
	{
	}

	static boolean named(Outfit l, Slot slot, String name)
	{
		return namedWeapon(l.get(slot), name);
	}

	static boolean namedWeapon(Gear item, String name)
	{
		String actual = lower(item);
		// "name (" is a case of "name ": test the prefix first, without building strings.
		return actual.startsWith(name) && (actual.length() == name.length() || actual.charAt(name.length()) == ' ')
			&& !actual.contains("uncharged") && !actual.contains("inactive") && !actual.contains("broken");
	}

	static boolean set(Outfit l, String head, String body, String legs, String weapon)
	{
		return named(l, Slot.HEAD, head) && named(l, Slot.BODY, body) && named(l, Slot.LEGS, legs)
			&& named(l, Slot.WEAPON, weapon);
	}

	static boolean damned(Outfit l)
	{
		return named(l, Slot.NECK, "amulet of the damned");
	}

	static boolean verac(Outfit l)
	{
		return set(l, "verac's helm", "verac's brassard", "verac's plateskirt", "verac's flail");
	}

	static boolean dharok(Outfit l)
	{
		return set(l, "dharok's helm", "dharok's platebody", "dharok's platelegs", "dharok's greataxe");
	}

	static boolean ahrim(Outfit l)
	{
		return damned(l) && set(l, "ahrim's hood", "ahrim's robetop", "ahrim's robeskirt", "ahrim's staff");
	}

	static boolean karil(Outfit l)
	{
		return damned(l) && set(l, "karil's coif", "karil's leathertop", "karil's leatherskirt", "karil's crossbow");
	}

	static boolean bloodMoon(Outfit l)
	{
		return set(l, "blood moon helm", "blood moon chestplate", "blood moon tassets", "dual macuahuitl");
	}

	static boolean twoHit(Gear w)
	{
		String name = lower(w);
		return TWO_HIT.matches(name);
	}

	static boolean ratWeapon(Gear w)
	{
		String name = lower(w);
		return RAT_WEAPONS.matches(name);
	}

	static boolean wildernessWeapon(Outfit l, CombatContext ctx)
	{
		String name = lower(l.getWeapon());
		if (!ctx.getModifiers().isWilderness() || name.contains("uncharged") || name.endsWith("(u)"))
		{
			return false;
		}
		Combat cls = Weapons.classOf(l.getStyle());
		return (cls == Combat.MELEE ? WILDERNESS_MELEE : cls == Combat.RANGED ? WILDERNESS_RANGED
			: WILDERNESS_MAGIC).matches(name);
	}

	static boolean vampyre(CombatContext ctx)
	{
		return ctx.getMonster().hasAttribute("vampyre (t1)") || ctx.getMonster().hasAttribute("vampyre (t2)")
			|| ctx.getMonster().hasAttribute("vampyre (t3)");
	}

	/** Effects that scale each rolled hit, rather than increasing the roll's maximum. */
	static long damage(Outfit l, CombatContext ctx, long hit)
	{
		String weapon = lower(l.getWeapon());
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
			if (guardian(ctx.getMonster()))
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
			int speed = l.getSpell() != null ? l.getSpell().getAttackSpeed() : Weapons.attackSpeed(l.getWeapon(), l.getStyle());
			if (l.getSpell() != null && weapon.startsWith("harmonised nightmare staff") && !l.getStyle().isManualCast()
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

	static boolean seeking(Outfit l)
	{
		return l.getStyle().getType() == Style.Type.RANGED && Weapons.firesAmmoSlot(l.getWeapon())
			&& lower(l.get(Slot.AMMO)).startsWith("seeking broad");
	}

	static boolean rolledEffect(Outfit l, CombatContext ctx)
	{
		String weapon = lower(l.getWeapon());
		return vampyre(ctx) || CombatRules.tormentedBonus(ctx.getMonster(), l) || seeking(l)
			|| l.getStyle().isMelee() && (dharok(l) || guardian(ctx.getMonster())
			|| (weapon.startsWith("toktz-") || weapon.startsWith("tzhaar-ket-"))
			&& named(l, Slot.NECK, "berserker necklace"));
	}

	static double average(Encounter.Rule rule, Outfit l, CombatContext ctx, long min, long max)
	{
		return rolledEffect(l, ctx) ? HitDamage.average(rule, min, max, h -> damage(l, ctx, h))
			: HitDamage.average(rule, min, max);
	}

	static int maximum(Encounter.Rule rule, Outfit l, CombatContext ctx, long max)
	{
		return rule.maximum(Math.max(1, damage(l, ctx, max)));
	}
}
