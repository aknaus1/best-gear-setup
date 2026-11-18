package com.bestgearsetup.calc;

import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.OffensivePrayer;
import java.util.Map;

/** Short forms of production constructors and calls that only tests need. */
public final class TestFactories
{
	private TestFactories()
	{
	}

	/** With potions, assumes super combat, ranging potion and saturated heart. */
	public static CombatContext combatContext(Monster monster, PlayerLevels levels, boolean onTask, boolean usePotions,
		Map<Combat, OffensivePrayer> prayers)
	{
		return new CombatContext(monster, levels, onTask,
			usePotions ? levels.getAttack() + 5 + levels.getAttack() * 15 / 100 : levels.getAttack(),
			usePotions ? levels.getStrength() + 5 + levels.getStrength() * 15 / 100 : levels.getStrength(),
			usePotions ? levels.getRanged() + 4 + levels.getRanged() / 10 : levels.getRanged(),
			usePotions ? levels.getMagic() + 4 + levels.getMagic() / 10 : levels.getMagic(),
			prayers, false);
	}

	/** Stab / slash / crush / ranged / magic / atlatl: the result tab a setup belongs to. */
	public static Style.Type attackType(Setup setup)
	{
		Outfit loadout = setup.getLoadout();
		return loadout.getStyle() == null ? null : Weapons.tabType(loadout.getWeapon(), loadout.getStyle());
	}
}
