package com.bestgearsetup.calc;

import com.bestgearsetup.data.Monster;
import java.util.Locale;

/** Apply supported health-dependent target stats before pre-fight stat drains. */
public final class MonsterStates
{
	private MonsterStates()
	{
	}

	/** Vardorvis's selected health snapshot; unknown modes keep the supplied reference stats. */
	public static Monster atHealth(Monster base, int currentHp)
	{
		Monster result = base.copy();
		if (!EncounterDamage.named(base, "vardorvis"))
		{
			return result;
		}
		String name = base.getName().toLowerCase(Locale.ROOT);
		boolean awakened = name.contains("awakened");
		boolean quest = name.contains("(quest)");
		int maximum = awakened ? 1400 : quest ? 500 : 700;
		if (base.getHitpoints() != maximum || name.contains("echo"))
		{
			return result;
		}
		int hp = currentHp <= 0 ? maximum : Math.min(maximum, currentHp);
		int startDef = awakened ? 268 : quest ? 180 : 215;
		int endDef = awakened ? 181 : quest ? 130 : 145;
		int startStr = awakened ? 391 : quest ? 210 : 270;
		int endStr = awakened ? 522 : quest ? 280 : 360;
		result.setDefenceLevel((int) (startDef - (maximum - hp) * (double) (startDef - endDef) / maximum));
		result.setStrengthLevel((int) (startStr + (maximum - hp) * (double) (endStr - startStr) / maximum));
		result.setDefenceFloor(result.getDefenceLevel());
		return result;
	}
}
