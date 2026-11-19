package com.bestgearsetup.calc;

import com.bestgearsetup.data.*;
import static com.bestgearsetup.calc.Mitigation.*;

/** Static encounter identity rules from the OSRS Wiki calculator; phases remain separate targets. */
final class CombatRules
{
	private CombatRules()
	{
	}

	static boolean toa(Monster m)
	{
		int id = m.getId();
		return id >= 11789 && id <= 11799 || id >= 11778 && id <= 11780
			|| id == 11719 || id == 11721 || id >= 11724 && id <= 11726
			|| id == 11730 || id == 11732 || id == 11733 || id >= 11750 && id <= 11758
			|| id >= 11761 && id <= 11764 || m.hasAttribute("toa") || m.hasAttribute("tombs of amascut");
	}

	/** NPC defence roll, including the Tombs of Amascut raid-level multiplier stored on scaled targets. */
	static long defenceRoll(Monster m, int level, int bonus)
	{
		long roll = (long) (level + 9) * (bonus + 64);
		int raidLevel = m.getToaRaidLevel();
		return raidLevel > 0 ? roll * (250 + raidLevel) / 250 : roll;
	}

	/** Whether the selected temporary phase names this target. */
	static boolean phaseApplies(Monster m, EncounterPhase phase)
	{
		switch (phase)
		{
			case HUEYCOATL_PILLAR:
				return hueycoatlPillarTarget(m);
			case ROYAL_TITANS_OUT_OF_MELEE:
				return titanBoss(m);
			case ABYSSAL_SIRE_TRANSITION:
				return m.getId() == 5886 || m.getId() == 5889 || m.getId() == 5891
					|| named(m, "abyssal sire") && !m.getLowerName().contains("stage 2");
			case MOKHAIOTL_SHIELDED:
			case MOKHAIOTL_BURROWING:
				return named(m, "doom of mokhaiotl") || m.getId() == 14707;
			case TD_UNSHIELDED:
			case TD_DEFENCELESS:
				// Variants whose names already fix the shield state ignore the phase option.
				return tormented(m) && !m.getLowerName().contains("shielded)")
					&& !m.getLowerName().contains("100% accuracy");
			case YAMA_MELEE_TANK:
				return yamaTankDependent(m);
			default:
				return false;
		}
	}

	static boolean phase(Monster m, EncounterPhase phase)
	{
		return m.getPhase() == phase && phaseApplies(m, phase);
	}

	/** Yama's Magic defence depends on the tank's style except in his enraged final phase. */
	static boolean yamaTankDependent(Monster m)
	{
		String name = m.getLowerName();
		return named(m, "yama") && !name.contains("enraged") && !name.contains("phase 3")
			&& !name.contains("mage tank") && !name.contains("deadman");
	}

	/** Head and tail receive the pillar damage buff; the body cannot. */
	static boolean hueycoatlPillarTarget(Monster m)
	{
		String name = m.getLowerName();
		int id = m.getId();
		return named(m, "the hueycoatl") ? name.contains("(head)") || name.contains("(tail)")
			: id == 14009 || id == 14010 || id == 14013 || id == 14014;
	}

	static boolean titanBoss(Monster m)
	{
		return m.getId() == 12596 || m.getId() == 14147 || named(m, "branda the fire queen")
			|| named(m, "eldric the ice king");
	}

	static boolean titanElemental(Monster m)
	{
		String name = m.getLowerName();
		return m.getId() == 14150 || m.getId() == 14151
			|| name.endsWith("elemental (royal titans)");
	}

	/** Targets immune to burns in the reference: Tekton, Dusk, Vasa's crystal and the Warriors' Guild cyclopes. */
	static boolean burnImmune(Monster m)
	{
		int id = m.getId();
		return tekton(m) || crystal(m) || named(m, "dusk")
			|| id == 7851 || id == 7854 || id == 7855 || id == 7882 || id == 7883 || id >= 7886 && id <= 7889
			|| id >= 2463 && id <= 2468 || id >= 2137 && id <= 2142;
	}

	static boolean gauntlet(Monster m)
	{
		return m.hasAttribute("crystalline") || m.hasAttribute("corrupted")
			|| m.getId() >= 9021 && m.getId() <= 9048;
	}

	static boolean equipmentAllowed(Monster m, Gear item)
	{
		return equipmentAllowed(m, gauntlet(m), item);
	}

	/** @param gauntlet {@link #gauntlet(Monster)}, worked out once when checking every slot of a setup */
	static boolean equipmentAllowed(Monster m, boolean gauntlet, Gear item)
	{
		String name = lower(item);
		boolean activity = (name.startsWith("crystal ") || name.startsWith("corrupted "))
			&& (name.contains("(basic)") || name.contains("(attuned)") || name.contains("(perfected)")
			|| name.contains("(gauntlet)") || name.endsWith("sceptre") || name.equals("corrupted axe")
			|| name.equals("corrupted harpoon") || name.equals("corrupted pickaxe"));
		if (gauntlet)
		{
			return activity && name.startsWith(m.hasAttribute("corrupted") || m.getId() >= 9035 ? "corrupted " : "crystal ");
		}
		return !activity && (!name.startsWith("dawnbringer") || verzikP1(m))
			&& (!name.contains("(inside toa)") || toa(m)) && (!name.contains("(outside toa)") || !toa(m));
	}

	static int defenceFloor(Monster m)
	{
		if (named(m, "vardorvis") || named(m, "verzik vitur"))
		{
			return m.getDefenceLevel();
		}
		String name = m.getLowerName();
		int floor = name.startsWith("sotetseg") ? 100 : name.contains("nightmare") && !name.contains("totem") ? 120
			: name.startsWith("akkha") && !name.contains("shadow") ? 70 : name.startsWith("ba-ba") ? 60
			: name.startsWith("kephri") || name.startsWith("obelisk") && toa(m) ? 60
			: name.startsWith("zebak") ? 50 : name.contains("warden") && name.contains("phase 3") ? 120
			: named(m, "nex") ? 250 : named(m, "araxxor") ? 90
			: named(m, "the hueycoatl") ? 120 : named(m, "yama") ? 145
			: named(m, "doom of mokhaiotl") ? 60
			: tfaDrainCapped(m) ? m.getDefenceLevel() * 3 / 5 : 0;
		return Math.min(m.getDefenceLevel(), Math.max(floor, m.getDefenceFloor()));
	}

	/**
	 * The Fractured Archive's bosses whose Defence can only be drained to 60% of its original value ("The Fractured
	 * Archive - Out Today", 2026-10-21 hotfix): not the Dagon'hai, Dreadnoth, the Smorks or the adds, nor Ocellus's
	 * second phase (the Blade), whose Defence can be drained to 0 (Wiki "Ocellus").
	 */
	static boolean tfaDrainCapped(Monster m)
	{
		if (!m.hasAttribute("the fractured archive"))
		{
			return false;
		}
		return named(m, "archive guardian") || named(m, "bork")
			|| named(m, "ezekiel") || named(m, "moia")
			|| named(m, "xarth")
			|| named(m, "ocellus") && RaidScaling.tfaScaled(m);
	}

	static boolean wardenCore(Monster m)
	{
		String name = m.getLowerName();
		if (name.contains("warden"))
		{
			// A named variant takes priority over the NPC id.
			return name.contains("core");
		}
		return m.getId() == 11755 || m.getId() == 11758;
	}

	static boolean wardenP2(Monster m)
	{
		String name = m.getLowerName();
		if (name.contains("warden"))
		{
			return name.contains("phase 2");
		}
		return m.getId() == 11753 || m.getId() == 11754 || m.getId() == 11756 || m.getId() == 11757;
	}

	static long[] bounds(Monster m, long attack, long defence, long max)
	{
		if (!wardenP2(m))
		{
			return new long[]{named(m, "respiratory system") ? max / 2 : 0, max};
		}
		double modifier = Math.max(15, Math.min(40, 15 + Math.max(0, attack - defence / 3) * 25.0 / 42000));
		return new long[]{(long) (max * modifier / 100), (long) (max * (modifier + 20) / 100)};
	}

	static boolean defenceBasedMagic(Monster m)
	{
		int id = m.getId();
		return iceDemon(m) || seren(m)
			|| id >= 8369 && id <= 8374 || id >= 10830 && id <= 10835 || id >= 10847 && id <= 10852
			|| id == 11709 || id == 11712 || id == 9118 || named(m, "verzik vitur");
	}

	static int demonVulnerability(Monster m)
	{
		if (m.getDemonbaneVulnerability() != null)
		{
			return Math.max(0, m.getDemonbaneVulnerability());
		}
		if (named(m, "duke sucellus"))
		{
			return 70;
		}
		if (named(m, "yama"))
		{
			return 120;
		}
		if (m.getId() == 14179)
		{
			return 200;
		}
		return iceDemon(m) ? 115 : 100;
	}

	/** Yama's void flares (ids shared by the fire and shadow flares, matched by name for older data). */
	static boolean voidFlare(Monster m)
	{
		return m.getId() == 14179 || named(m, "void flare");
	}

	/**
	 * Void flares always take a maximum hit from magic and from standard ranged weapons (Wiki); melee and light or
	 * heavy ranged attacks roll normally.
	 */
	static boolean flareMax(Monster m, Outfit loadout)
	{
		if (!voidFlare(m))
		{
			return false;
		}
		Combat cls = Weapons.classOf(loadout.getStyle());
		return cls == Combat.MAGIC || cls == Combat.RANGED && standardRanged(loadout.getWeapon());
	}

	/**
	 * Standard ranged weapons: bows (the eclipse atlatl is one), and salamanders' ranged attack (Wiki: Ranged). Crossbows, including
	 * Karil's, and chinchompas are heavy; thrown weapons are light.
	 */
	static boolean standardRanged(Gear weapon)
	{
		String sub = weapon.getSubcategory();
		return sub.equals("bow") || sub.equals("salamander");
	}

	static int demonBonus(Monster m, int percent)
	{
		return percent * demonVulnerability(m) / 100;
	}

	static boolean alwaysMax(Monster m, Combat cls)
	{
		int id = m.getId();
		if (id == 14180)
		{
			return true;
		}
		switch (cls)
		{
			case MELEE:
				return id == 11710 || id == 11713 || id == 12814 || wardenCore(m);
			case RANGED:
				return id == 11711 || id == 11714 || id == 12815 || id == 11717 || id == 11715;
			default:
				return id == 11709 || id == 11712 || id == 12816 || id == 14150 || id == 14151;
		}
	}

	static boolean oneHit(Monster m)
	{
		return m.getId() == 7223 || m.getId() == 8584 || m.getId() == 11193;
	}

	static double accuracy(Monster m, Outfit l, double normal)
	{
		return alwaysMax(m, Weapons.classOf(l.getStyle())) || oneHit(m) || m.getId() == 5916 || wardenP2(m)
			|| m.getLowerName().contains("(100% accuracy)")
			|| phase(m, EncounterPhase.MOKHAIOTL_SHIELDED) || phase(m, EncounterPhase.MOKHAIOTL_BURROWING)
			|| phase(m, EncounterPhase.TD_DEFENCELESS)
			|| eclipseClone(m) && l.getStyle().isMelee()
			|| unshielded(m) || verzikP1(m)
			&& lower(l.getWeapon()).equals("dawnbringer") ? 1 : normal;
	}

	static boolean eclipseClone(Monster m)
	{
		// By name: the clone shares no reliable NPC id across data sources.
		return named(m, "eclipse moon") && m.getLowerName().contains("clone");
	}

	static boolean araxyteMax(Monster m, Outfit l)
	{
		if (!named(m, "acidic araxyte") && !named(m, "mirrorback araxyte"))
		{
			return false;
		}
		if (lower(l.getWeapon()).equals("noxious halberd"))
		{
			return true;
		}
		DpsCalculator.Bonuses b = DpsCalculator.Bonuses.of(l);
		return l.getStyle().getType() == Style.Type.CRUSH && b.crush > b.stab && b.crush > b.slash
			&& b.crush >= b.magic && b.crush >= b.ranged
			|| l.getStyle().getType() == Style.Type.RANGED && l.getWeapon().getSubcategory().equals("crossbow")
			&& b.ranged > b.stab && b.ranged > b.slash && b.ranged > b.crush && b.ranged > b.magic;
	}

	static boolean tormentedBonus(Monster m, Outfit l)
	{
		if (!unshielded(m))
		{
			return false;
		}
		String category = l.getWeapon().getSubcategory();
		return l.getStyle().getType() == Style.Type.CRUSH
			|| l.getStyle().getType() == Style.Type.MAGIC && l.getSpell() != null
			|| l.getStyle().getType() == Style.Type.RANGED && (category.equals("chinchompa")
			|| category.equals("crossbow"));
	}
}
