package com.bestgearsetup.data;

import lombok.Value;

/**
 * An offensive prayer for one combat class, with its accuracy and damage boosts in percent.
 * For melee both apply to levels; for ranged both apply to levels; for magic the accuracy
 * applies to the level and the damage is an additive magic damage bonus.
 */
@Value
public class OffensivePrayer
{
	String name;
	double accuracyPercent;
	double damagePercent;
	int prayerLevel;
	int defenceLevel;
}
