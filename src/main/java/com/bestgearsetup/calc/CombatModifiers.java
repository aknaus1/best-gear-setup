package com.bestgearsetup.calc;

import lombok.Builder;
import lombok.Value;

/** Explicit combat snapshots; defaults do not assume optional buffs or missing health. */
@Value
@Builder(toBuilder = true)
public class CombatModifiers
{
	public static final CombatModifiers NONE = CombatModifiers.builder().build();
	/** Zero selects the player's base maximum HP. */
	int currentHitpoints;
	/** Zero selects the monster's supplied maximum HP. */
	int monsterHitpoints;
	boolean wilderness;
	boolean charge;
	boolean markOfDarkness;
	boolean sunfireRunes;
	boolean kandarinDiary;
	/** Forinthry Surge raises the amulet of avarice bonus against revenants. */
	boolean forinthrySurge;
	/** Poison assumed on poisonable weapons and ammunition. */
	@Builder.Default
	WeaponPoison weaponPoison = WeaponPoison.NONE;
	/** Score each weapon's special attack instead of its ordinary attack. */
	boolean specialAttack;
	int soulreaperStacks;
	@Builder.Default
	int miningLevel = 99;

	int playerHp(PlayerLevels levels)
	{
		return currentHitpoints <= 0 ? levels.getHitpoints() : Math.min(currentHitpoints, levels.getHitpoints());
	}

	int targetHp(com.bestgearsetup.data.Monster monster)
	{
		return monsterHitpoints <= 0 ? monster.getHitpoints() : Math.min(monsterHitpoints, monster.getHitpoints());
	}
}
