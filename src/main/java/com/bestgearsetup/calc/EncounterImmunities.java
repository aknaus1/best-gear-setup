package com.bestgearsetup.calc;

import com.bestgearsetup.data.*;
import static com.bestgearsetup.calc.Encounter.*;

/** Combat-class and equipment requirements shared by search filtering and direct DPS scoring. */
final class EncounterImmunities
{
	private static final Names.NameList BROAD_AMMUNITION = Names.list("broad ammunition");
	private static final Names.NameList VAMPYREBANE_MELEE = Names.list("vampyrebane melee weapons");
	private static final Names.NameList SILVER_MELEE = Names.list("silver melee weapons");

	/** Slot.values() copies the array on every call, and every scored setup is checked. */
	private static final Slot[] SLOTS = Slot.values();

	private EncounterImmunities()
	{
	}

	static boolean classAllowed(Monster monster, Combat cls)
	{
		int id = monster.getId();
		boolean dusk = id == 7851 || id == 7854 || id == 7855 || id == 7882 || id == 7883
			|| id >= 7886 && id <= 7889 || named(monster, "dusk");
		if (CombatRules.eclipseClone(monster) || dusk || guardian(monster)
			|| id >= 2463 && id <= 2468 || id >= 2137 && id <= 2142)
		{
			return cls == Combat.MELEE;
		}
		if (tekton(monster) || crystal(monster))
		{
			// Crystals can take reduced Magic damage even where a broader "melee only" tag says otherwise.
			return cls != Combat.RANGED;
		}
		if (cls == Combat.MELEE && (AttackReach.isMeleeBlocked(monster)
			|| AttackReach.isVespula(monster) || AttackReach.isAbyssalPortal(monster)
			|| id == 12214 || id == 12215 || id == 12219 || named(monster, "the leviathan")))
		{
			return false;
		}
		if (cls == Combat.MELEE && monster.hasAttribute("melee immune")
			|| cls == Combat.RANGED && monster.hasAttribute("ranged immune")
			|| cls == Combat.MAGIC && monster.hasAttribute("magic immune"))
		{
			return false;
		}
		return (!monster.hasAttribute("melee only") || cls == Combat.MELEE)
			&& (!monster.hasAttribute("ranged only") || cls == Combat.RANGED)
			&& (!monster.hasAttribute("magic only") || cls == Combat.MAGIC);
	}

	static boolean attackAllowed(Monster monster, Outfit loadout)
	{
		Combat cls = Weapons.classOf(loadout.getStyle());
		if (!classAllowed(monster, cls)
			|| loadout.getStyle().isMelee() && !AttackReach.meleeAllowed(monster, loadout.getWeapon()))
		{
			return false;
		}
		String weapon = lower(loadout.getWeapon());
		String ammo = lower(loadout.get(Slot.AMMO));
		boolean gauntlet = CombatRules.gauntlet(monster);
		for (Slot slot : SLOTS)
		{
			if (loadout.get(slot) != null && !CombatRules.equipmentAllowed(monster, gauntlet, loadout.get(slot)))
			{
				return false;
			}
		}
		if (cls == Combat.RANGED && (weapon.equals("holy water") && !monster.hasAttribute("demon")
			|| weapon.contains("(u)") || weapon.contains("uncharged")))
		{
			return false;
		}
		if (Effects.ratWeapon(loadout.getWeapon()) && !monster.hasAttribute("rat"))
		{
			return false;
		}
		if (CombatRules.phase(monster, EncounterPhase.MOKHAIOTL_SHIELDED) && !demonbane(loadout))
		{
			// The shield blocks everything except demonbane attacks.
			return false;
		}
		if (named(monster, "fire warrior of lesarkus")
			&& (cls != Combat.RANGED || !ammo.equals("ice arrows")))
		{
			return false;
		}
		if (named(monster, "fareed") && (cls == Combat.MAGIC
			&& !SpellRules.element(loadout.getSpell()).equals("water") || cls == Combat.RANGED && !ammo.contains("arrow")))
		{
			return false;
		}
		if (guardian(monster)
			&& !loadout.getWeapon().getSubcategory().equals("pickaxe"))
		{
			return false;
		}
		if (leafy(monster))
		{
			boolean leaf = cls == Combat.MELEE && weapon.startsWith("leaf-bladed ")
				|| cls == Combat.MAGIC && spell(loadout).equals("magic dart")
				|| cls == Combat.RANGED && Weapons.firesAmmoSlot(loadout.getWeapon())
				&& BROAD_AMMUNITION.matches(ammo);
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
			&& !lower(loadout.get(Slot.RING)).equals("efaritay's aid") && !silver(loadout))
		{
			return false;
		}
		return true;
	}

	static boolean vampyrebane(Outfit loadout, boolean tierThree)
	{
		String weapon = lower(loadout.getWeapon());
		if (loadout.getStyle().getType() == Style.Type.RANGED && weapon.startsWith("blisterwood stake"))
		{
			return true;
		}
		return (!tierThree || loadout.getStyle().isMelee()) && (VAMPYREBANE_MELEE.matches(weapon)
			|| !tierThree && weapon.startsWith("rod of ivandis"));
	}

	static boolean silver(Outfit loadout)
	{
		if (loadout.getStyle().getType() == Style.Type.RANGED)
		{
			return lower(loadout.getWeapon()).startsWith("blisterwood stake")
				|| Weapons.firesAmmoSlot(loadout.getWeapon())
				&& lower(loadout.get(Slot.AMMO)).startsWith("silver bolts");
		}
		String weapon = lower(loadout.getWeapon());
		return loadout.getStyle().isMelee() && SILVER_MELEE.matches(weapon);
	}
}
