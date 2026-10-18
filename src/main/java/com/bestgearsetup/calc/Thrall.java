package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.NameLists;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Arceuus thrall tiers. Thralls always land a successful hit, rolling 0 to their max hit every 4 ticks, so the
 * average damage is half the max hit per 2.4 seconds. Each tier comes as a ghostly, skeletal or zombified thrall
 * with the same max hit; {@link #eligibleType} picks the one that can damage a target.
 */
@Getter
@RequiredArgsConstructor
public enum Thrall
{
	LESSER("Lesser thrall", 38, 1),
	SUPERIOR("Superior thrall", 57, 2),
	GREATER("Greater thrall", 76, 3);

	private static final double ATTACK_SECONDS = 2.4;

	/** Fights where thralls can't be resurrected but whose infobox doesn't mark the target immune (Wiki Thrall page). */
	private static final NameLists.NameList RESTRICTED_FIGHTS = NameLists.list("thrall-restricted fights");

	private final String displayName;
	private final int magicLevel;
	private final int maxHit;

	/** The three thrall types, in the Wiki's order of preference: magic first, as it hits a tick or two sooner. */
	@Getter
	@RequiredArgsConstructor
	public enum Type
	{
		GHOSTLY("ghostly", CombatClass.MAGIC),
		SKELETAL("skeletal", CombatClass.RANGED),
		ZOMBIFIED("zombified", CombatClass.MELEE);

		private final String label;
		private final CombatClass combatClass;
	}

	/** Flat DPS added to a setup. */
	public double getDps()
	{
		return maxHit / 2.0 / ATTACK_SECONDS;
	}

	/** This tier's thrall of the given type, e.g. "Greater ghostly thrall". */
	public String displayName(Type type)
	{
		return type == null ? displayName : displayName.replace(" thrall", " " + type.getLabel() + " thrall");
	}

	/** The strongest thrall this base Magic level can cast, or null below the lesser thrall's level. */
	public static Thrall forLevel(int magic)
	{
		Thrall best = null;
		for (Thrall thrall : values())
		{
			if (magic >= thrall.magicLevel)
			{
				best = thrall;
			}
		}
		return best;
	}

	/**
	 * The thrall type assumed against this target: the first type, magic first, whose combat class can damage it in
	 * its selected phase. Null when the target is immune to thralls, thralls can't be resurrected for the fight, or
	 * no type's class can damage it.
	 */
	public static Type eligibleType(Monster monster)
	{
		if (immune(monster))
			return null;
		for (Type type : Type.values())
			if (EncounterImmunities.classAllowed(monster, type.getCombatClass()))
				return type;
		return null;
	}

	/** Whether thralls can't be resurrected for this fight, or the target is immune to them. */
	public static boolean immune(Monster monster)
	{
		return monster.isImmuneThrall() || RESTRICTED_FIGHTS.matches(monster.getLowerName());
	}
}
