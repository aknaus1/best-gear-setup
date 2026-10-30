package com.bestgearsetup;

import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SpecialPlan;
import com.bestgearsetup.calc.SupportSpell;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/** Expected base runes consumed per kill by attack and support spells. */
public final class RuneUse
{
	private static final List<Integer> RUNE_ORDER = List.of(ItemID.AIRRUNE, ItemID.WATERRUNE,
		ItemID.EARTHRUNE, ItemID.FIRERUNE, ItemID.MINDRUNE, ItemID.CHAOSRUNE, ItemID.DEATHRUNE,
		ItemID.BLOODRUNE, ItemID.SOULRUNE, ItemID.WRATHRUNE, ItemID.COSMICRUNE);
	private static final RuneUse EMPTY = new RuneUse(Collections.emptyMap(), Collections.emptyMap(), 0, 0);

	private final Map<Integer, Double> attack;
	private final Map<Integer, Double> support;
	private final double castsPerKill;
	private final double secondsPerKill;

	private RuneUse(Map<Integer, Double> attack, Map<Integer, Double> support, double castsPerKill,
		double secondsPerKill)
	{
		this.attack = sorted(attack);
		this.support = sorted(support);
		this.castsPerKill = castsPerKill;
		this.secondsPerKill = secondsPerKill;
	}

	/** Estimate rune use from total DPS and full monster hitpoints. */
	public static RuneUse of(SetupResult setup, int monsterHitpoints, int baseMagic, int boostedMagic)
	{
		if (setup == null || setup.getLoadout() == null || setup.getDps() == null
			|| setup.getDps().getDps() <= 0 || monsterHitpoints <= 0)
		{
			return EMPTY;
		}
		double seconds = monsterHitpoints / setup.getDps().getDps();
		if (!Double.isFinite(seconds))
		{
			return EMPTY;
		}
		Loadout loadout = setup.getLoadout();
		Set<Integer> supplied = SpellRunes.supplied(loadout);
		SpecialPlan special = setup.getSpecial();
		DpsResult ordinary = special == null ? setup.getDps() : special.getOrdinary();
		double interval = ordinary.getExpectedSpeedTicks() * 0.6;
		double specSeconds = special == null ? 0
			: special.getSpecsPerKill() * special.getSpec().getExpectedSpeedTicks() * 0.6;
		double casts = loadout.getSpell() == null || interval <= 0 ? 0
			: Math.max(0, seconds - specSeconds) / interval;
		Map<Integer, Double> attack = new HashMap<>();
		if (casts > 0)
		{
			double usedFraction = 1 - SpellRunes.runeSaveChance(loadout);
			SpellRunes.perCast(loadout.getSpell().getName()).forEach((rune, count) ->
			{
				if (!supplied.contains(rune))
				{
					attack.put(rune, casts * count * usedFraction);
				}
			});
		}
		Map<Integer, Double> support = new HashMap<>();
		if (setup.getSupportSpells() != null)
		{
			for (SupportSpell spell : setup.getSupportSpells())
			{
				double duration = SpellRunes.supportDurationSeconds(spell, loadout, baseMagic, boostedMagic);
				if (duration <= 0)
				{
					continue;
				}
				SpellRunes.supportPerCast(spell, setup.getThrall()).forEach((rune, count) ->
				{
					if (!supplied.contains(rune))
					{
						support.merge(rune, count * seconds / duration, Double::sum);
					}
				});
			}
		}
		return new RuneUse(attack, support, casts, seconds);
	}

	private static Map<Integer, Double> sorted(Map<Integer, Double> counts)
	{
		List<Map.Entry<Integer, Double>> entries = new ArrayList<>(counts.entrySet());
		entries.removeIf(entry -> entry.getValue() <= 0 || !Double.isFinite(entry.getValue()));
		entries.sort(Comparator.<Map.Entry<Integer, Double>>comparingDouble(Map.Entry::getValue).reversed()
			.thenComparingInt(entry -> RUNE_ORDER.indexOf(entry.getKey())));
		Map<Integer, Double> ordered = new LinkedHashMap<>();
		for (Map.Entry<Integer, Double> entry : entries)
		{
			ordered.put(entry.getKey(), entry.getValue());
		}
		return Collections.unmodifiableMap(ordered);
	}

	public Map<Integer, Double> getAttack()
	{
		return attack;
	}

	public Map<Integer, Double> getSupport()
	{
		return support;
	}

	public double getCastsPerKill()
	{
		return castsPerKill;
	}

	public double getSecondsPerKill()
	{
		return secondsPerKill;
	}

	public boolean isEmpty()
	{
		return attack.isEmpty() && support.isEmpty();
	}

	/** Short name for a base rune id in the results view. */
	public static String runeName(int runeId)
	{
		return SpellRunes.runeName(runeId);
	}
}
