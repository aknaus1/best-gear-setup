package com.bestgearsetup;

import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.data.Potion;
import java.util.Locale;
import lombok.Value;

/**
 * How long one boost dose lasts on a raid's combat timeline. A divine potion holds its full boost for five minutes.
 * An ordinary boost drains one level a minute and is drunk again once it falls below {@link #repotPercent} of its
 * full amount at the player's level.
 */
@Value
class BoostTiming
{
	static final BoostTiming DEFAULT = new BoostTiming(false, 50, PlayerLevels.maxed());
	/** A divine dose holds the boost for 500 ticks. */
	static final double DIVINE_SECONDS = 300;
	/** Boosted stats restore toward the base level by one every 100 ticks. */
	static final double DRAIN_SECONDS = 60;

	/** Plan the divine version of a potion when there is one. */
	boolean divine;
	/** Drink again once the remaining boost is below this percentage of the full boost; 0 lets it run out. */
	int repotPercent;
	/** Base levels the boosts are drunk at. */
	PlayerLevels levels;

	boolean isDivine(Potion potion)
	{
		return divine && BoostAccess.divine(potion) != null;
	}

	/**
	 * Seconds one dose keeps the boost above the repot threshold. Restoration runs on a timer that is already going
	 * when the dose is drunk, so the first level drains after half a minute on average and each later one a minute
	 * after that.
	 */
	double doseSeconds(Potion potion)
	{
		if (isDivine(potion)) { return DIVINE_SECONDS; }
		int level = level(potion.getSkill());
		int boost = potion.boost(level) - level;
		// No known boost at this level (e.g. an entry without stats): assume a divine-length refresh.
		if (boost <= 0) { return DIVINE_SECONDS; }
		int percent = Math.max(0, Math.min(100, repotPercent));
		// The first remaining boost below the threshold: boost - drained < boost * percent / 100.
		int drained = Math.min(boost, boost * (100 - percent) / 100 + 1);
		return (drained - 0.5) * DRAIN_SECONDS;
	}

	private int level(String skill)
	{
		switch (skill == null ? "" : skill.toLowerCase(Locale.ROOT))
		{
			case "attack": return levels.getAttack();
			case "strength": return levels.getStrength();
			case "defence": return levels.getDefence();
			case "ranged": return levels.getRanged();
			case "magic": return levels.getMagic();
			default: return 99;
		}
	}
}
