package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.OffensivePrayer;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntFunction;
import lombok.Getter;

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
	private final Map<CombatClass, OffensivePrayer> prayers;
	private final boolean thrall;
	private final int aoeTargets;
	private final int targetDistance;
	private final CombatModifiers modifiers;
	/** Target stats at a given HP (after pre-fight drains), or null when they do not depend on HP. */
	private final IntFunction<Monster> healthStates;

	public CombatContext(Monster monster, PlayerLevels levels, boolean onTask, int attack, int strength, int ranged,
		int magic, Map<CombatClass, OffensivePrayer> prayers, boolean thrall)
	{
		this(monster, levels, onTask, attack, strength, ranged, magic, prayers, thrall, 1, 0);
	}

	private CombatContext(Monster monster, PlayerLevels levels, boolean onTask, int attack, int strength, int ranged,
		int magic, Map<CombatClass, OffensivePrayer> prayers, boolean thrall, int aoeTargets, int targetDistance)
	{
		this(monster, levels, onTask, attack, strength, ranged, magic, prayers, thrall, aoeTargets, targetDistance,
			CombatModifiers.NONE);
	}

	private CombatContext(Monster monster, PlayerLevels levels, boolean onTask, int attack, int strength, int ranged,
		int magic, Map<CombatClass, OffensivePrayer> prayers, boolean thrall, int aoeTargets, int targetDistance,
		CombatModifiers modifiers)
	{
		this(monster, levels, onTask, attack, strength, ranged, magic, prayers, thrall, aoeTargets, targetDistance,
			modifiers, null);
	}

	private CombatContext(Monster monster, PlayerLevels levels, boolean onTask, int attack, int strength, int ranged,
		int magic, Map<CombatClass, OffensivePrayer> prayers, boolean thrall, int aoeTargets, int targetDistance,
		CombatModifiers modifiers, IntFunction<Monster> healthStates)
	{
		this.monster = monster;
		this.levels = levels;
		this.onTask = onTask;
		this.attack = attack;
		this.strength = strength;
		this.ranged = ranged;
		this.magic = magic;
		this.prayers = prayers == null ? new EnumMap<>(CombatClass.class) : new EnumMap<>(prayers);
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

	/** Grouped identical targets in multicombat; one target disables area damage. */
	public CombatContext withFightOptions(int targets, int distance)
	{
		return new CombatContext(monster, levels, onTask, attack, strength, ranged, magic, prayers, thrall,
			targets, distance, modifiers, healthStates);
	}

	/** Change optional buffs without losing distance, group size or boosted levels. */
	public CombatContext withModifiers(CombatModifiers buffs)
	{
		return new CombatContext(monster, levels, onTask, attack, strength, ranged, magic, prayers, thrall,
			aoeTargets, targetDistance, buffs == null ? CombatModifiers.NONE : buffs, healthStates);
	}

	/** Supply target stats that change with its HP, such as Vardorvis's Defence. */
	public CombatContext withHealthStates(IntFunction<Monster> states)
	{
		return new CombatContext(monster, levels, onTask, attack, strength, ranged, magic, prayers, thrall,
			aoeTargets, targetDistance, modifiers, states);
	}

	/** The same fight after further drains land on the target, at every HP state too. */
	CombatContext withDrains(SpecialAttacks drains)
	{
		if (!drains.isAny())
		{
			return this;
		}
		IntFunction<Monster> states = healthStates == null ? null : hp -> drains.apply(healthStates.apply(hp));
		return new CombatContext(drains.apply(monster), levels, onTask, attack, strength, ranged, magic, prayers,
			thrall, aoeTargets, targetDistance, modifiers, states);
	}

	/** The same fight with the target at the given HP. */
	CombatContext atTargetHp(int hp)
	{
		Monster target = healthStates == null ? monster : healthStates.apply(hp);
		return new CombatContext(target, levels, onTask, attack, strength, ranged, magic, prayers, thrall,
			aoeTargets, targetDistance, modifiers.toBuilder().monsterHitpoints(hp).build(), healthStates);
	}

	/**
	 * Convenience constructor: with potions, assumes super combat, ranging potion and saturated heart.
	 */
	public CombatContext(Monster monster, PlayerLevels levels, boolean onTask, boolean usePotions,
		Map<CombatClass, OffensivePrayer> prayers)
	{
		this(monster, levels, onTask,
			usePotions ? levels.getAttack() + 5 + levels.getAttack() * 15 / 100 : levels.getAttack(),
			usePotions ? levels.getStrength() + 5 + levels.getStrength() * 15 / 100 : levels.getStrength(),
			usePotions ? levels.getRanged() + 4 + levels.getRanged() / 10 : levels.getRanged(),
			usePotions ? levels.getMagic() + 4 + levels.getMagic() / 10 : levels.getMagic(),
			prayers, false);
	}

	public OffensivePrayer getPrayer(CombatClass combatClass)
	{
		return prayers.get(combatClass);
	}

	/** Effective target HP at the start of this fight, clamped to its scaled maximum. */
	public int getTargetHitpoints()
	{
		return modifiers.targetHp(monster);
	}
}
