package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;

/**
 * Weapon classification helpers shared by the calculator and the optimiser.
 */
public final class WeaponRules
{
	private WeaponRules()
	{
	}

	/** Weapons with external ammunition fire what is in the ammo slot. */
	public static boolean firesAmmoSlot(GearItem weapon)
	{
		if (weapon == null || weapon.getAmmunition().isEmpty())
		{
			return false;
		}
		return !loadsAmmo(weapon);
	}

	/** Built-in magic damage of a salamander; tar is required separately by the optimizer. */
	public static int salamanderMaxHit(GearItem weapon, int magic)
	{
		String name = EncounterDamage.lower(weapon);
		int bonus = name.equals("swamp lizard") ? 56 : name.equals("orange salamander") ? 59
			: name.equals("red salamander") ? 77 : name.equals("black salamander") ? 92
			: name.equals("tecu salamander") ? 104 : -1;
		return bonus < 0 ? -1 : (magic * (bonus + 64) + 320) / 640;
	}

	/** Blowpipes hold darts internally; the ammo slot stays free. */
	public static boolean loadsAmmo(GearItem weapon)
	{
		return weapon != null && EncounterDamage.lower(weapon).contains("blowpipe") && !weapon.getAmmunition().isEmpty();
	}

	/** Consumable ammunition includes zero-bonus atlatl darts and salamander tar. */
	public static boolean isAmmunition(GearItem item)
	{
		if (item == null)
		{
			return false;
		}
		String name = EncounterDamage.lower(item);
		return item.getRangedStr() > 0 || item.getRangedBonus() > 0 || name.equals("atlatl dart")
			|| name.endsWith(" tar");
	}

	/**
	 * Used up as it is fired, so priced and owned by the configured ammo quantity: ammo-slot ammunition (including
	 * blowpipe darts) and stackable thrown weapons such as knives, darts, thrownaxes and chinchompas. Blowpipes,
	 * Tonalztics of Ralos and the Hunter's spear are thrown-style weapons that are not consumed.
	 */
	public static boolean consumedPerAttack(GearItem item)
	{
		if (item == null)
		{
			return false;
		}
		if (item.getSlot() == Slot.AMMO)
		{
			return isAmmunition(item);
		}
		if (item.getSlot() != Slot.WEAPON)
		{
			return false;
		}
		String name = EncounterDamage.lower(item);
		return item.getSubcategory().equals("chinchompa") || item.getSubcategory().equals("thrown")
			&& !name.contains("blowpipe") && !name.startsWith("tonalztics of ralos") && !name.equals("hunter's spear");
	}

	public static boolean isPoweredStaff(GearItem weapon)
	{
		return weapon != null && weapon.getSubcategory().equals("powered staff");
	}

	/**
	 * Base max hit of a powered staff's built-in attack at the given (boosted) Magic level,
	 * or -1 when the staff is not supported (wilderness-only or unknown formula).
	 * Formulas from the OSRS Wiki.
	 */
	public static int poweredStaffMaxHit(GearItem weapon, int magic)
	{
		String name = EncounterDamage.lower(weapon);
		int third = magic / 3;
		if (name.contains("uncharged"))
		{
			return -1;
		}
		if (name.startsWith("thammaron's sceptre"))
		{
			return Math.max(1, third - 8);
		}
		if (name.startsWith("accursed sceptre"))
		{
			return Math.max(1, third - 6);
		}
		if (name.equals("starter staff"))
		{
			return 8;
		}
		if (name.startsWith("crystal staff") || name.startsWith("corrupted staff"))
		{
			return name.contains("perfected") ? 39 : name.contains("attuned") ? 31 : name.contains("basic") ? 23 : -1;
		}
		if (name.startsWith("tumeken's shadow"))
		{
			return third + 1;
		}
		if (name.startsWith("sanguinesti staff") || name.startsWith("holy sanguinesti staff"))
		{
			return third;
		}
		if (name.startsWith("trident of the swamp") || name.equals("dawnbringer"))
		{
			return Math.max(1, third - 2);
		}
		if (name.startsWith("trident of the seas"))
		{
			return Math.max(1, third - 5);
		}
		if (name.startsWith("eye of ayak"))
		{
			return Math.max(1, third - 6);
		}
		if (name.startsWith("warped sceptre"))
		{
			return (8 * magic + 96) / 37;
		}
		if (name.startsWith("bone staff"))
		{
			return Math.max(1, third - 5) + 10;
		}
		return -1;
	}

	/** The eclipse atlatl: a ranged weapon that scales with Strength and melee strength bonus. */
	public static boolean isAtlatl(GearItem weapon)
	{
		return weapon != null && weapon.getLowerCombatName().startsWith("eclipse atlatl");
	}

	/** Ava's devices and their assembler cape variants, plus charged or uncharged Dizana's quivers. */
	public static boolean isAmmoRecoveryCape(GearItem item)
	{
		if (item == null || item.getSlot() != Slot.CAPE)
		{
			return false;
		}
		String name = item.getLowerCombatName();
		return name.startsWith("ava's attractor") || name.startsWith("ava's accumulator")
			|| name.startsWith("ava's assembler") || name.startsWith("assembler max cape")
			|| name.startsWith("masori assembler") || name.startsWith("dizana's quiver")
			|| name.startsWith("blessed dizana's quiver");
	}

	/**
	 * Ranged weapons whose damage uses visible Strength, melee strength bonus and melee slayer/salve
	 * damage branches: the eclipse atlatl and Hunter's spear. Accuracy remains ordinary ranged accuracy.
	 */
	public static boolean scalesWithStrength(GearItem weapon)
	{
		return isAtlatl(weapon) || weapon != null && weapon.getLowerCombatName().startsWith("hunter's spear");
	}

	/**
	 * The result tab a style belongs to: the atlatl gets its own tab even though its
	 * styles are ranged styles.
	 */
	public static AttackStyle.Type tabType(GearItem weapon, AttackStyle style)
	{
		return style.getType() == AttackStyle.Type.RANGED && isAtlatl(weapon) ? AttackStyle.Type.ATLATL : style.getType();
	}

	public static boolean isShadow(GearItem weapon)
	{
		return weapon != null && EncounterDamage.lower(weapon).startsWith("tumeken's shadow");
	}

	/** Which class a weapon style belongs to, from the style list. */
	public static boolean supports(GearItem weapon, CombatClass combatClass)
	{
		for (String raw : weapon.getStyles())
		{
			AttackStyle s = AttackStyle.parse(raw);
			if (s != null && classOf(s) == combatClass)
			{
				return true;
			}
		}
		return false;
	}

	public static CombatClass classOf(AttackStyle style)
	{
		switch (style.getType())
		{
			case RANGED:
			case ATLATL:
				return CombatClass.RANGED;
			case MAGIC:
				return CombatClass.MAGIC;
			default:
				return CombatClass.MELEE;
		}
	}
}
