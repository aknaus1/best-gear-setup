package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.MonsterGroup;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.EncounterPhase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.Value;

/** One combat target or required attack style in a complete raid. */
@Value
public class RaidEncounter
{
	String label;
	MonsterSummary target;
	/** Attack types the encounter is planned with: the required types narrowed by the style choice; empty for any. */
	Set<AttackStyle.Type> styles;
	/** Number of times this target must be defeated, used only for the full raid's combat-time objective. */
	int repetitions;
	EncounterPhase phase;
	/** Attack types the target can be damaged by in this encounter; empty for any. */
	Set<AttackStyle.Type> requiredStyles;
	/** What Auto means here, e.g. ranged or magic for bosses fought from distance. */
	RaidStyle preferredStyle;
	/** The player's saved choice for this encounter. */
	RaidStyle styleChoice;
	/** Raid-handed boosts (CoX overloads, ToA smelling salts) are available by this point of the raid. */
	boolean raidSupplies;

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
		this(label, target, repetitions, phase, required(styles), RaidStyle.AUTO, RaidStyle.AUTO, true);
	}

	private RaidEncounter(String label, MonsterSummary target, int repetitions, EncounterPhase phase,
		Set<AttackStyle.Type> required, RaidStyle preferred, RaidStyle choice, boolean raidSupplies)
	{
		if (repetitions < 1) { throw new IllegalArgumentException("Encounter repetitions must be positive"); }
		this.label = label;
		this.target = target;
		this.repetitions = repetitions;
		this.phase = phase;
		this.requiredStyles = required;
		this.preferredStyle = preferred;
		this.styleChoice = choice;
		this.raidSupplies = raidSupplies;
		this.styles = Collections.unmodifiableSet(narrowed(required, choice == RaidStyle.AUTO ? preferred : choice));
	}

	private static Set<AttackStyle.Type> required(AttackStyle.Type... styles)
	{
		EnumSet<AttackStyle.Type> allowed = EnumSet.noneOf(AttackStyle.Type.class);
		Collections.addAll(allowed, styles);
		return Collections.unmodifiableSet(allowed);
	}

	/** The required types narrowed by a style; a style that leaves nothing usable keeps the required types. */
	private static Set<AttackStyle.Type> narrowed(Set<AttackStyle.Type> required, RaidStyle style)
	{
		if (style == RaidStyle.AUTO) { return required; }
		Set<AttackStyle.Type> out = EnumSet.copyOf(style.types());
		if (!required.isEmpty()) { out.retainAll(required); }
		return out.isEmpty() ? required : out;
	}

	/** Auto for this encounter, such as ranged or magic for a boss that is not fought in melee range. */
	RaidEncounter withPreferredStyle(RaidStyle preferred)
	{
		return new RaidEncounter(label, target, repetitions, phase, requiredStyles, preferred, styleChoice, raidSupplies);
	}

	RaidEncounter withStyleChoice(RaidStyle choice)
	{
		return new RaidEncounter(label, target, repetitions, phase, requiredStyles, preferredStyle, choice, raidSupplies);
	}

	RaidEncounter withRaidSupplies(boolean available)
	{
		return new RaidEncounter(label, target, repetitions, phase, requiredStyles, preferredStyle, styleChoice, available);
	}

	/** Choices that plan this encounter differently, Auto first; one choice means the style is fixed. */
	public List<RaidStyle> getStyleChoices()
	{
		List<RaidStyle> choices = new ArrayList<>();
		List<Set<AttackStyle.Type>> seen = new ArrayList<>();
		for (RaidStyle style : RaidStyle.values())
		{
			Set<AttackStyle.Type> types = narrowed(requiredStyles, style == RaidStyle.AUTO ? preferredStyle : style);
			// A choice the target can't be damaged by would silently fall back to the required types.
			boolean usable = style == RaidStyle.AUTO || requiredStyles.isEmpty()
				|| style.types().stream().anyMatch(requiredStyles::contains);
			if (usable && !seen.contains(types)) { choices.add(style); seen.add(types); }
		}
		return choices;
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
