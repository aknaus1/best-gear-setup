package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;

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
					|| EncounterDamage.named(m, "abyssal sire") && !m.getName().toLowerCase(java.util.Locale.ROOT).contains("stage 2");
			case MOKHAIOTL_SHIELDED:
			case MOKHAIOTL_BURROWING:
				return EncounterDamage.named(m, "doom of mokhaiotl") || m.getId() == 14707;
			case TD_UNSHIELDED:
			case TD_DEFENCELESS:
				// Variants whose names already fix the shield state ignore the phase option.
				return EncounterDamage.tormented(m) && !m.getName().toLowerCase(java.util.Locale.ROOT).contains("shielded)")
					&& !m.getName().toLowerCase(java.util.Locale.ROOT).contains("100% accuracy");
			case YAMA_MAGIC_TANK:
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
		String name = m.getName().toLowerCase(java.util.Locale.ROOT);
		return EncounterDamage.named(m, "yama") && !name.contains("enraged") && !name.contains("phase 3")
			&& !name.contains("mage tank") && !name.contains("deadman");
	}

	/** Head and tail receive the pillar damage buff; the body cannot. */
	static boolean hueycoatlPillarTarget(Monster m)
	{
		String name = m.getName().toLowerCase(java.util.Locale.ROOT);
		int id = m.getId();
		return EncounterDamage.named(m, "the hueycoatl") ? name.contains("(head)") || name.contains("(tail)")
			: id == 14009 || id == 14010 || id == 14013 || id == 14014;
	}

	static boolean titanBoss(Monster m)
	{
		return m.getId() == 12596 || m.getId() == 14147 || EncounterDamage.named(m, "branda the fire queen")
			|| EncounterDamage.named(m, "eldric the ice king");
	}

	static boolean titanElemental(Monster m)
	{
		String name = m.getName().toLowerCase(java.util.Locale.ROOT);
		return m.getId() == 14150 || m.getId() == 14151
			|| name.endsWith("elemental (royal titans)");
	}

	/** Targets immune to burns in the reference: Tekton, Dusk, Vasa's crystal and the Warriors' Guild cyclopes. */
	static boolean burnImmune(Monster m)
	{
		int id = m.getId();
		return EncounterDamage.tekton(m) || EncounterDamage.crystal(m) || EncounterDamage.named(m, "dusk")
			|| id == 7851 || id == 7854 || id == 7855 || id == 7882 || id == 7883 || id >= 7886 && id <= 7889
			|| id >= 2463 && id <= 2468 || id >= 2137 && id <= 2142;
	}

	static boolean gauntlet(Monster m)
	{
		return m.hasAttribute("crystalline") || m.hasAttribute("corrupted")
			|| m.getId() >= 9021 && m.getId() <= 9048;
	}

	static boolean equipmentAllowed(Monster m, GearItem item)
	{
		String name = EncounterDamage.lower(item);
		boolean activity = (name.startsWith("crystal ") || name.startsWith("corrupted "))
			&& (name.contains("(basic)") || name.contains("(attuned)") || name.contains("(perfected)")
			|| name.contains("(gauntlet)") || name.endsWith("sceptre") || name.equals("corrupted axe")
			|| name.equals("corrupted harpoon") || name.equals("corrupted pickaxe"));
		if (gauntlet(m))
		{
			return activity && name.startsWith(m.hasAttribute("corrupted") || m.getId() >= 9035 ? "corrupted " : "crystal ");
		}
		return !activity && (!name.startsWith("dawnbringer") || EncounterDamage.verzikP1(m))
			&& (!name.contains("(inside toa)") || toa(m)) && (!name.contains("(outside toa)") || !toa(m));
	}

	static int defenceFloor(Monster m)
	{
		if (EncounterDamage.named(m, "vardorvis") || EncounterDamage.named(m, "verzik vitur"))
		{
			return m.getDefenceLevel();
		}
		String name = m.getName().toLowerCase(java.util.Locale.ROOT);
		int floor = name.startsWith("sotetseg") ? 100 : name.contains("nightmare") && !name.contains("totem") ? 120
			: name.startsWith("akkha") && !name.contains("shadow") ? 70 : name.startsWith("ba-ba") ? 60
			: name.startsWith("kephri") || name.startsWith("obelisk") && toa(m) ? 60
			: name.startsWith("zebak") ? 50 : name.contains("warden") && name.contains("phase 3") ? 120
			: EncounterDamage.named(m, "nex") ? 250 : EncounterDamage.named(m, "araxxor") ? 90
			: EncounterDamage.named(m, "the hueycoatl") ? 120 : EncounterDamage.named(m, "yama") ? 145
			: EncounterDamage.named(m, "doom of mokhaiotl") ? 60 : 0;
		return Math.min(m.getDefenceLevel(), Math.max(floor, m.getDefenceFloor()));
	}

	static boolean wardenCore(Monster m)
	{
		String name = m.getName().toLowerCase(java.util.Locale.ROOT);
		if (name.contains("warden"))
		{
			// A named variant takes priority over the NPC id.
			return name.contains("core");
		}
		return m.getId() == 11755 || m.getId() == 11758;
	}

	static boolean wardenP2(Monster m)
	{
		String name = m.getName().toLowerCase(java.util.Locale.ROOT);
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
			return new long[]{EncounterDamage.named(m, "respiratory system") ? max / 2 : 0, max};
		}
		double modifier = Math.max(15, Math.min(40, 15 + Math.max(0, attack - defence / 3) * 25.0 / 42000));
		return new long[]{(long) (max * modifier / 100), (long) (max * (modifier + 20) / 100)};
	}

	static boolean defenceBasedMagic(Monster m)
	{
		int id = m.getId();
		return id == 7584 || id == 7585 || id >= 8917 && id <= 8920
			|| id >= 8369 && id <= 8374 || id >= 10830 && id <= 10835 || id >= 10847 && id <= 10852
			|| id == 11709 || id == 11712 || id == 9118
			|| EncounterDamage.named(m, "ice demon") || EncounterDamage.named(m, "fragment of seren")
			|| EncounterDamage.named(m, "verzik vitur");
	}

	static int demonVulnerability(Monster m)
	{
		if (m.getDemonbaneVulnerability() != null)
		{
			return Math.max(0, m.getDemonbaneVulnerability());
		}
		if (EncounterDamage.named(m, "duke sucellus"))
		{
			return 70;
		}
		if (EncounterDamage.named(m, "yama"))
		{
			return 120;
		}
		if (m.getId() == 14179)
		{
			return 200;
		}
		return m.getId() == 7584 || m.getId() == 7585 || EncounterDamage.named(m, "ice demon") ? 115 : 100;
	}

	static int demonBonus(Monster m, int percent)
	{
		return percent * demonVulnerability(m) / 100;
	}

	static boolean alwaysMax(Monster m, CombatClass cls)
	{
		int id = m.getId();
		if (id == 14179 || id == 14180)
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

	static double accuracy(Monster m, Loadout l, double normal)
	{
		return alwaysMax(m, WeaponRules.classOf(l.getStyle())) || oneHit(m) || m.getId() == 5916 || wardenP2(m)
			|| m.getName().toLowerCase(java.util.Locale.ROOT).contains("(100% accuracy)")
			|| phase(m, EncounterPhase.MOKHAIOTL_SHIELDED) || phase(m, EncounterPhase.MOKHAIOTL_BURROWING)
			|| phase(m, EncounterPhase.TD_DEFENCELESS)
			|| eclipseClone(m) && l.getStyle().isMelee()
			|| EncounterDamage.unshielded(m) || EncounterDamage.verzikP1(m)
			&& EncounterDamage.lower(l.getWeapon()).equals("dawnbringer") ? 1 : normal;
	}

	static boolean eclipseClone(Monster m)
	{
		// By name: the clone shares no reliable NPC id across data sources.
		return EncounterDamage.named(m, "eclipse moon") && m.getName().toLowerCase(java.util.Locale.ROOT).contains("clone");
	}

	static boolean araxyteMax(Monster m, Loadout l)
	{
		if (!EncounterDamage.named(m, "acidic araxyte") && !EncounterDamage.named(m, "mirrorback araxyte"))
		{
			return false;
		}
		if (EncounterDamage.lower(l.getWeapon()).equals("noxious halberd"))
		{
			return true;
		}
		DpsCalculator.Bonuses b = DpsCalculator.Bonuses.of(l);
		return l.getStyle().getType() == AttackStyle.Type.CRUSH && b.crush > b.stab && b.crush > b.slash
			&& b.crush >= b.magic && b.crush >= b.ranged
			|| l.getStyle().getType() == AttackStyle.Type.RANGED && l.getWeapon().getSubcategory().equals("crossbow")
			&& b.ranged > b.stab && b.ranged > b.slash && b.ranged > b.crush && b.ranged > b.magic;
	}

	static boolean tormentedBonus(Monster m, Loadout l)
	{
		if (!EncounterDamage.unshielded(m))
		{
			return false;
		}
		String category = l.getWeapon().getSubcategory();
		return l.getStyle().getType() == AttackStyle.Type.CRUSH
			|| l.getStyle().getType() == AttackStyle.Type.MAGIC && l.getSpell() != null
			|| l.getStyle().getType() == AttackStyle.Type.RANGED && (category.equals("chinchompa")
			|| category.equals("crossbow") && !EncounterDamage.lower(l.getWeapon()).startsWith("karil's crossbow"));
	}
}
