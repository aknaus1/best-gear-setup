package com.bestgearsetup.calc;

import com.bestgearsetup.data.GearItem;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

/**
 * The player's base (unboosted) combat levels.
 */
@Value
@Builder
@AllArgsConstructor
public class PlayerLevels
{
	int attack;
	int strength;
	int defence;
	int ranged;
	int magic;
	int prayer;
	int hitpoints;
	int slayer;

	public static PlayerLevels maxed()
	{
		return new PlayerLevels(99, 99, 99, 99, 99, 99, 99, 99);
	}

	/** Standard combat level from the base levels. */
	public int combatLevel()
	{
		double base = (defence + hitpoints + prayer / 2) / 4.0;
		double melee = 13 * (attack + strength) / 40.0;
		double range = 13 * (ranged * 3 / 2) / 40.0;
		double mage = 13 * (magic * 3 / 2) / 40.0;
		return (int) (base + Math.max(melee, Math.max(range, mage)));
	}

	/** Wear requirements use base levels, never potion boosts; ownership and locks do not bypass them. */
	public boolean canEquip(GearItem item)
	{
		return item.getAttackReq() <= attack && item.getStrengthReq() <= strength
			&& item.getDefenceReq() <= defence && item.getRangedReq() <= ranged
			&& item.getMagicReq() <= magic && item.getPrayerReq() <= prayer
			&& item.getHitpointsReq() <= hitpoints && item.getSlayerReq() <= slayer
			&& item.getCombatReq() <= combatLevel();
	}

	/** The wear requirements these levels miss, e.g. "75 Ranged (you have 70)", or null if none. */
	public String missingRequirements(GearItem item)
	{
		List<String> missing = new ArrayList<>();
		missing(missing, item.getAttackReq(), attack, "Attack");
		missing(missing, item.getStrengthReq(), strength, "Strength");
		missing(missing, item.getDefenceReq(), defence, "Defence");
		missing(missing, item.getRangedReq(), ranged, "Ranged");
		missing(missing, item.getMagicReq(), magic, "Magic");
		missing(missing, item.getPrayerReq(), prayer, "Prayer");
		missing(missing, item.getHitpointsReq(), hitpoints, "Hitpoints");
		missing(missing, item.getSlayerReq(), slayer, "Slayer");
		missing(missing, item.getCombatReq(), combatLevel(), "combat");
		return missing.isEmpty() ? null : String.join(", ", missing);
	}

	private static void missing(List<String> out, int required, int level, String skill)
	{
		if (required > level)
		{
			out.add(required + " " + skill + " (you have " + level + ")");
		}
	}
}
