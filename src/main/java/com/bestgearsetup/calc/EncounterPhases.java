package com.bestgearsetup.calc;

import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.Monster;

/** Describes how the selected temporary boss phase changes the calculation. */
public final class EncounterPhases
{
	private EncounterPhases()
	{
	}

	public static boolean applies(Monster monster, EncounterPhase phase)
	{
		return CombatRules.phaseApplies(monster, phase);
	}

	/**
	 * Apply phase-dependent target stats. Yama's Magic defence is 60 while the tank attacks with Magic and
	 * -30 otherwise (Wiki calculator phases), except in his enraged phase.
	 */
	public static void applyStats(Monster monster)
	{
		if (CombatRules.yamaTankDependent(monster))
		{
			monster.setDefMagic(monster.getPhase() == EncounterPhase.YAMA_MAGIC_TANK ? 60 : -30);
		}
	}

	/** A summary for the search notes, or null when the standard state is selected. */
	public static String describe(Monster monster)
	{
		EncounterPhase phase = monster.getPhase();
		if (phase == EncounterPhase.STANDARD)
		{
			return null;
		}
		if (!applies(monster, phase))
		{
			return "Boss phase \"" + phase + "\" does not apply to this target and is ignored.";
		}
		switch (phase)
		{
			case HUEYCOATL_PILLAR:
				return "Hueycoatl pillar: each hit deals 30% more damage after the tail caps.";
			case ROYAL_TITANS_OUT_OF_MELEE:
				return "Royal Titans out of melee range: ranged attack rolls are multiplied by 6.";
			case ABYSSAL_SIRE_TRANSITION:
				return "Abyssal Sire transition: each hit is halved.";
			case MOKHAIOTL_SHIELDED:
				return "Mokhaiotl shielded: only demonbane attacks deal damage; they always hit.";
			case MOKHAIOTL_BURROWING:
				return "Mokhaiotl burrowing: every attack hits.";
			case TD_UNSHIELDED:
				return "Tormented Demon unshielded: always hit, no fire-shield reduction, heavy-hitting attacks gain damage.";
			case TD_DEFENCELESS:
				return "Tormented Demon defenceless: attacks always hit but the fire shield still reduces damage.";
			case YAMA_MAGIC_TANK:
				return "Yama: Magic defence +60 while the tank uses Magic (-30 otherwise).";
			default:
				return null;
		}
	}
}
