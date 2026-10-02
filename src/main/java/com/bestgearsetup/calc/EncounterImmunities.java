package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;

/** Combat-class and equipment requirements shared by search filtering and direct DPS scoring. */
final class EncounterImmunities
{
	private EncounterImmunities()
	{
	}

	static boolean classAllowed(Monster monster, CombatClass cls)
	{
		int id = monster.getId();
		if (CombatRules.eclipseClone(monster))
		{
			return cls == CombatClass.MELEE;
		}
		boolean dusk = id == 7851 || id == 7854 || id == 7855 || id == 7882 || id == 7883
			|| id >= 7886 && id <= 7889 || EncounterDamage.named(monster, "dusk");
		if (dusk || EncounterDamage.guardian(monster))
		{
			return cls == CombatClass.MELEE;
		}
		if (id == 2463 || id == 2464 || id == 2465 || id == 2466 || id == 2467 || id == 2468
			|| id >= 2137 && id <= 2142)
		{
			return cls == CombatClass.MELEE;
		}
		if (EncounterDamage.tekton(monster) || EncounterDamage.crystal(monster))
		{
			// Crystals can take reduced Magic damage even where a broader "melee only" tag says otherwise.
			return cls != CombatClass.RANGED;
		}
		if (cls == CombatClass.MELEE && (AttackReach.isMeleeBlocked(monster)
			|| id >= 7530 && id <= 7533 || EncounterDamage.named(monster, "vespula")
			|| id == 12214 || id == 12215 || id == 12219 || EncounterDamage.named(monster, "the leviathan")))
		{
			return false;
		}
		if (cls == CombatClass.MELEE && monster.hasAttribute("melee immune")
			|| cls == CombatClass.RANGED && monster.hasAttribute("ranged immune")
			|| cls == CombatClass.MAGIC && monster.hasAttribute("magic immune"))
		{
			return false;
		}
		return (!monster.hasAttribute("melee only") || cls == CombatClass.MELEE)
			&& (!monster.hasAttribute("ranged only") || cls == CombatClass.RANGED)
			&& (!monster.hasAttribute("magic only") || cls == CombatClass.MAGIC);
	}

	static boolean attackAllowed(Monster monster, Loadout loadout)
	{
		CombatClass cls = WeaponRules.classOf(loadout.getStyle());
		if (!classAllowed(monster, cls)
			|| loadout.getStyle().isMelee() && !AttackReach.meleeAllowed(monster, loadout.getWeapon()))
		{
			return false;
		}
		String weapon = EncounterDamage.lower(loadout.getWeapon());
		String ammo = EncounterDamage.lower(loadout.get(Slot.AMMO));
		for (Slot slot : Slot.values())
		{
			if (loadout.get(slot) != null && !CombatRules.equipmentAllowed(monster, loadout.get(slot)))
			{
				return false;
			}
		}
		if (cls == CombatClass.RANGED && (weapon.equals("holy water") && !monster.hasAttribute("demon")
			|| weapon.contains("(u)") || weapon.contains("uncharged")))
		{
			return false;
		}
		if (EquipmentEffects.ratWeapon(loadout.getWeapon()) && !monster.hasAttribute("rat"))
		{
			return false;
		}
		if (CombatRules.phase(monster, EncounterPhase.MOKHAIOTL_SHIELDED) && !EncounterDamage.demonbane(loadout))
		{
			// The shield blocks everything except demonbane attacks.
			return false;
		}
		if (EncounterDamage.named(monster, "fire warrior of lesarkus")
			&& (cls != CombatClass.RANGED || !ammo.equals("ice arrows")))
		{
			return false;
		}
		if (EncounterDamage.named(monster, "fareed") && (cls == CombatClass.MAGIC
			&& !SpellRules.element(loadout.getSpell()).equals("water") || cls == CombatClass.RANGED && !ammo.contains("arrow")))
		{
			return false;
		}
		if (EncounterDamage.guardian(monster)
			&& !loadout.getWeapon().getSubcategory().equals("pickaxe"))
		{
			return false;
		}
		if (monster.hasAttribute("leafy") || EncounterDamage.named(monster, "kurask")
			|| EncounterDamage.named(monster, "turoth"))
		{
			boolean leaf = cls == CombatClass.MELEE && weapon.startsWith("leaf-bladed ")
				|| cls == CombatClass.MAGIC && EncounterDamage.spell(loadout).equals("magic dart")
				|| cls == CombatClass.RANGED && WeaponRules.firesAmmoSlot(loadout.getWeapon())
				&& (ammo.startsWith("broad ") || ammo.startsWith("amethyst broad ") || ammo.startsWith("seeking broad "));
			if (!leaf)
			{
				return false;
			}
		}
		if (monster.hasAttribute("vampyre (t3)") && !vampyrebane(loadout, true))
		{
			return false;
		}
		if (monster.hasAttribute("vampyre (t2)") && !vampyrebane(loadout, false)
			&& !EncounterDamage.lower(loadout.get(Slot.RING)).equals("efaritay's aid") && !silver(loadout))
		{
			return false;
		}
		return true;
	}

	static boolean vampyrebane(Loadout loadout, boolean tierThree)
	{
		String weapon = EncounterDamage.lower(loadout.getWeapon());
		if (loadout.getStyle().getType() == AttackStyle.Type.RANGED && weapon.startsWith("blisterwood stake"))
		{
			return true;
		}
		return (!tierThree || loadout.getStyle().isMelee()) && (weapon.startsWith("ivandis flail")
			|| weapon.startsWith("blisterwood sickle") || weapon.startsWith("blisterwood flail")
			|| weapon.startsWith("hallowed flail") || weapon.equals("sunspear")
			|| !tierThree && weapon.startsWith("rod of ivandis"));
	}

	static boolean silver(Loadout loadout)
	{
		if (loadout.getStyle().getType() == AttackStyle.Type.RANGED)
		{
			return EncounterDamage.lower(loadout.getWeapon()).startsWith("blisterwood stake")
				|| WeaponRules.firesAmmoSlot(loadout.getWeapon())
				&& EncounterDamage.lower(loadout.get(Slot.AMMO)).startsWith("silver bolts");
		}
		String weapon = EncounterDamage.lower(loadout.getWeapon());
		return loadout.getStyle().isMelee() && (weapon.startsWith("silver sickle") || weapon.contains("emerald sickle")
			|| weapon.contains("ruby sickle") || weapon.equals("blessed axe") || weapon.equals("wolfbane")
			|| weapon.startsWith("silverlight") || weapon.startsWith("darklight") || weapon.startsWith("arclight")
			|| weapon.startsWith("ivandis flail") || weapon.startsWith("rod of ivandis")
			|| weapon.startsWith("blisterwood") || weapon.startsWith("hallowed flail"));
	}
}
