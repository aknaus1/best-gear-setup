package com.bestgearsetup.calc;

import com.bestgearsetup.data.Slot;
import java.util.EnumSet;
import java.util.Set;

/** Spells cast before or beside the attack whose effect a setup's DPS assumes. */
public enum SupportSpell
{
	/** Mark of Darkness, boosting demonbane spells against demons. */
	MARK_OF_DARKNESS,
	/** Charge, boosting a god spell cast with the matching god cape. */
	CHARGE,
	/** The strongest thrall the player can cast, added as flat DPS; last so its Book of the dead follows the runes. */
	THRALL;

	/** The support spells the calculator applies to this loadout in this context. */
	static Set<SupportSpell> assumed(Loadout loadout, CombatContext ctx)
	{
		Set<SupportSpell> spells = EnumSet.noneOf(SupportSpell.class);
		if (markOfDarkness(loadout, ctx))
		{
			spells.add(MARK_OF_DARKNESS);
		}
		if (charge(loadout, ctx))
		{
			spells.add(CHARGE);
		}
		if (thrall(loadout, ctx))
		{
			spells.add(THRALL);
		}
		return spells;
	}

	/** Thralls need the Magic level and can't be cast while autocasting a non-Arceuus spell. */
	static boolean thrall(Loadout loadout, CombatContext ctx)
	{
		boolean usable = loadout.getSpell() == null || "arceuus".equalsIgnoreCase(loadout.getSpell().getSpellbook());
		return ctx.getThrall() != null && usable && !ctx.getMonster().isImmuneThrall();
	}

	static boolean demonbane(Loadout loadout, CombatContext ctx)
	{
		return EncounterDamage.spell(loadout).contains("demonbane") && ctx.getMonster().hasAttribute("demon");
	}

	static boolean markOfDarkness(Loadout loadout, CombatContext ctx)
	{
		return ctx.getModifiers().isMarkOfDarkness() && demonbane(loadout, ctx);
	}

	static boolean charge(Loadout loadout, CombatContext ctx)
	{
		if (!ctx.getModifiers().isCharge())
		{
			return false;
		}
		String spell = EncounterDamage.spell(loadout);
		String god = spell.equals("saradomin strike") ? "saradomin" : spell.equals("claws of guthix") ? "guthix"
			: spell.equals("flames of zamorak") ? "zamorak" : "";
		String cape = EncounterDamage.lower(loadout.get(Slot.CAPE));
		return !god.isEmpty() && (cape.equals(god + " cape") || cape.equals("imbued " + god + " cape")
			|| cape.equals(god + " max cape") || cape.equals("imbued " + god + " max cape"));
	}
}
