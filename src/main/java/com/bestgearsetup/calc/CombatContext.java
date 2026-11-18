package com.bestgearsetup.calc;

import com.bestgearsetup.data.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntFunction;
import lombok.*;

/**
 * Everything about the fight that does not depend on the gear: target (after any pre-fight
 * drains), boosted levels, the prayer used for each combat class, task and thrall.
 */
@Getter
public class CombatContext
{
	private final Monster monster;
	private final PlayerLevels levels;
	private final boolean onTask;
	private final int attack;
	private final int strength;
	private final int ranged;
	private final int magic;
	private final Map<Combat, OffensivePrayer> prayers;
	private final boolean thrall;
	private final int aoeTargets;
	private final int targetDistance;
	private final CombatModifiers modifiers;
	/** Target stats at a given HP (after pre-fight drains), or null when they do not depend on HP. */
	private final IntFunction<Monster> healthStates;
	/** Whether a thrall can attack the target, worked out on first use: the same for every setup. */
	@Getter(AccessLevel.NONE)
	private Boolean thrallTarget;

	public CombatContext(Monster monster, PlayerLevels levels, boolean onTask, int attack, int strength, int ranged,
		int magic, Map<Combat, OffensivePrayer> prayers, boolean thrall)
	{
		this(monster, levels, onTask, attack, strength, ranged, magic, prayers, thrall, 1, 0, CombatModifiers.NONE, null);
	}

	@Builder(toBuilder = true, access = AccessLevel.PRIVATE)
	private CombatContext(Monster monster, PlayerLevels levels, boolean onTask, int attack, int strength, int ranged,
		int magic, Map<Combat, OffensivePrayer> prayers, boolean thrall, int aoeTargets, int targetDistance,
		CombatModifiers modifiers, IntFunction<Monster> healthStates)
	{
		this.monster = monster;
		this.levels = levels;
		this.onTask = onTask;
		this.attack = attack;
		this.strength = strength;
		this.ranged = ranged;
		this.magic = magic;
		this.prayers = prayers == null ? new EnumMap<>(Combat.class) : new EnumMap<>(prayers);
		this.thrall = thrall;
		this.aoeTargets = Math.max(1, Math.min(12, aoeTargets));
		this.targetDistance = targetDistance;
		this.modifiers = modifiers;
		this.healthStates = healthStates;
	}

	/** Whether thralls were requested; Lombok skips this getter because {@link #getThrall()} shares its name. */
	public boolean isThrall()
	{
		return thrall;
	}

	/** The thrall added to setups that can cast one, or null when thralls are off or Magic is below 38. */
	public Thrall getThrall()
	{
		if (!thrall)
		{
			return null;
		}
		return levels == null ? Thrall.GREATER : Thrall.forLevel(levels.getMagic());
	}

	/** Whether thralls can be resurrected for this fight and some thrall type can damage the target. */
	boolean thrallTarget()
	{
		if (thrallTarget == null)
		{
			thrallTarget = Thrall.eligibleType(monster) != null;
		}
		return thrallTarget;
	}

	/** Grouped identical targets in multicombat; one target disables area damage. */
	public CombatContext withFightOptions(int targets, int distance)
	{
		return toBuilder().aoeTargets(targets).targetDistance(distance).build();
	}

	/** Change optional buffs without losing distance, group size or boosted levels. */
	public CombatContext withModifiers(CombatModifiers buffs)
	{
		return toBuilder().modifiers(buffs == null ? CombatModifiers.NONE : buffs).build();
	}

	/** Supply target stats that change with its HP, such as Vardorvis's Defence. */
	public CombatContext withHealthStates(IntFunction<Monster> states)
	{
		return toBuilder().healthStates(states).build();
	}

	/** The same fight after further drains land on the target, at every HP state too. */
	CombatContext withDrains(SpecialAttacks drains)
	{
		if (!drains.isAny())
		{
			return this;
		}
		IntFunction<Monster> states = healthStates == null ? null : hp -> drains.apply(healthStates.apply(hp));
		return toBuilder().monster(drains.apply(monster)).healthStates(states).build();
	}

	/** The same fight with the target at the given HP. */
	CombatContext atTargetHp(int hp)
	{
		Monster target = healthStates == null ? monster : healthStates.apply(hp);
		return toBuilder().monster(target).modifiers(modifiers.toBuilder().monsterHitpoints(hp).build()).build();
	}

	public OffensivePrayer getPrayer(Combat combatClass)
	{
		return prayers.get(combatClass);
	}

	/** Effective target HP at the start of this fight, clamped to its scaled maximum. */
	public int getTargetHitpoints()
	{
		return modifiers.targetHp(monster);
	}
}
