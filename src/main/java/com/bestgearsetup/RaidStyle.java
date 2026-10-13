package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import java.util.EnumSet;
import java.util.Set;

/** The attack styles a raid encounter is planned with; Auto keeps the encounter's own default. */
public enum RaidStyle
{
	AUTO("Auto"), MELEE("Melee"), RANGED("Ranged"), MAGIC("Magic"), DISTANCE("Ranged or magic");

	private final String label;

	RaidStyle(String label) { this.label = label; }

	/** Attack types this choice allows; empty for Auto, which adds no restriction of its own. */
	public Set<AttackStyle.Type> types()
	{
		switch (this)
		{
			case MELEE: return EnumSet.of(AttackStyle.Type.STAB, AttackStyle.Type.SLASH, AttackStyle.Type.CRUSH);
			case RANGED: return EnumSet.of(AttackStyle.Type.RANGED, AttackStyle.Type.ATLATL);
			case MAGIC: return EnumSet.of(AttackStyle.Type.MAGIC);
			case DISTANCE: return EnumSet.of(AttackStyle.Type.RANGED, AttackStyle.Type.ATLATL, AttackStyle.Type.MAGIC);
			default: return EnumSet.noneOf(AttackStyle.Type.class);
		}
	}

	@Override public String toString() { return label; }
}
