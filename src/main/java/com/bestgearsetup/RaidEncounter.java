package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.MonsterGroup;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.EncounterPhase;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import lombok.Value;

/** One combat target or required attack style in a complete raid. */
@Value
public class RaidEncounter
{
	String label;
	MonsterSummary target;
	Set<AttackStyle.Type> styles;
	/** Number of times this target must be defeated, used only for the full raid's combat-time objective. */
	int repetitions;
	EncounterPhase phase;

	public RaidEncounter(String label, MonsterSummary target, AttackStyle.Type... styles)
	{
		this(label, target, 1, styles);
	}

	public RaidEncounter(String label, MonsterSummary target, int repetitions, AttackStyle.Type... styles)
	{
		this(label, target, repetitions, EncounterPhase.STANDARD, styles);
	}

	public RaidEncounter(String label, MonsterSummary target, int repetitions, EncounterPhase phase, AttackStyle.Type... styles)
	{
		if (repetitions < 1) { throw new IllegalArgumentException("Encounter repetitions must be positive"); }
		this.label = label;
		this.target = target;
		this.repetitions = repetitions;
		this.phase = phase;
		EnumSet<AttackStyle.Type> allowed = EnumSet.noneOf(AttackStyle.Type.class);
		Collections.addAll(allowed, styles);
		this.styles = Collections.unmodifiableSet(allowed);
	}

	/** Group phases of the same boss together in the raid selector. */
	public String getBossName() { return GameData.titleCase(MonsterGroup.baseName(target.getName())); }

	public String getVersionLabel()
	{
		String boss = getBossName();
		return label.equals(boss) ? "Standard" : label.startsWith(boss)
			? label.substring(boss.length()).replaceFirst("^[ :]+", "") : label;
	}

	/** Keep phase HP and DPS intact; repeat the estimated fight time instead. */
	double estimatedSeconds(SearchResults result, SetupResult setup)
	{
		return repetitions * (double) result.getStartingHitpoints() / setup.getDps().getPrimaryDps();
	}

	@Override public String toString() { return label; }
}
