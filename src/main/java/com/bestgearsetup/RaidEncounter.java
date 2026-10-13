package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.EncounterImmunities;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.MonsterGroup;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.EncounterPhase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;

/** One combat target or required attack style in a complete raid. */
@Value
public class RaidEncounter
{
	String label;
	MonsterSummary target;
	/** Attack types the encounter is planned with: the usable types narrowed by the style choice; empty for any. */
	Set<AttackStyle.Type> styles;
	/** Number of times this target must be defeated, used only for the full raid's combat-time objective. */
	int repetitions;
	EncounterPhase phase;
	/** Attack types the encounter is restricted to by the raid itself; empty for any. */
	Set<AttackStyle.Type> requiredStyles;
	/** The required types the target, in this phase, can be damaged by (e.g. not ranged for Tekton). */
	Set<AttackStyle.Type> usableStyles;
	/** What Auto means here, e.g. ranged or magic for bosses that are not fought in melee range. */
	RaidStyle preferredStyle;
	/** The player's saved choice for this boss. */
	RaidStyle styleChoice;
	/** The saved choice can't damage this phase (e.g. melee against Akkha immune to melee), so Auto is used. */
	boolean styleFallback;
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
		this.usableStyles = usable(target, phase, required);
		this.preferredStyle = preferred;
		this.styleChoice = choice;
		this.raidSupplies = raidSupplies;
		this.styleFallback = choice != RaidStyle.AUTO && within(choice).isEmpty();
		this.styles = Collections.unmodifiableSet(stylesFor(choice));
	}

	private static Set<AttackStyle.Type> required(AttackStyle.Type... styles)
	{
		EnumSet<AttackStyle.Type> allowed = EnumSet.noneOf(AttackStyle.Type.class);
		Collections.addAll(allowed, styles);
		return Collections.unmodifiableSet(allowed);
	}

	/** The required types (every type when none is required) that the phase-applied target isn't immune to. */
	private static Set<AttackStyle.Type> usable(MonsterSummary summary, EncounterPhase phase, Set<AttackStyle.Type> required)
	{
		Monster target = new Monster();
		target.setId(summary.getId());
		target.setName(summary.getName());
		target.setAttributes(summary.getAttributes().stream().map(name ->
		{
			Monster.Attribute attribute = new Monster.Attribute();
			attribute.setName(name);
			return attribute;
		}).collect(Collectors.toList()));
		target.setPhase(phase);
		EnumSet<AttackStyle.Type> candidates = required.isEmpty() ? EnumSet.allOf(AttackStyle.Type.class) : EnumSet.copyOf(required);
		EnumSet<AttackStyle.Type> out = EnumSet.noneOf(AttackStyle.Type.class);
		for (AttackStyle.Type type : candidates)
		{
			if (EncounterImmunities.classAllowed(target, classOf(type))) { out.add(type); }
		}
		return Collections.unmodifiableSet(out.isEmpty() ? candidates : out);
	}

	private static CombatClass classOf(AttackStyle.Type type)
	{
		return type == AttackStyle.Type.MAGIC ? CombatClass.MAGIC
			: type == AttackStyle.Type.RANGED || type == AttackStyle.Type.ATLATL ? CombatClass.RANGED : CombatClass.MELEE;
	}

	/** The usable types a style allows; empty when it allows none of them. */
	private Set<AttackStyle.Type> within(RaidStyle style)
	{
		Set<AttackStyle.Type> out = EnumSet.noneOf(AttackStyle.Type.class);
		out.addAll(usableStyles);
		out.retainAll(style.types());
		return out;
	}

	/** The types a choice plans with; a choice that leaves nothing usable falls back to Auto. */
	private Set<AttackStyle.Type> stylesFor(RaidStyle choice)
	{
		Set<AttackStyle.Type> chosen = choice == RaidStyle.AUTO ? Collections.emptySet() : within(choice);
		if (!chosen.isEmpty()) { return chosen; }
		Set<AttackStyle.Type> preferred = preferredStyle == RaidStyle.AUTO ? Collections.emptySet() : within(preferredStyle);
		return preferred.isEmpty() ? requiredStyles : preferred;
	}

	/** The types Auto plans with, used when a saved choice can't be searched. */
	public Set<AttackStyle.Type> getAutoStyles()
	{
		return Collections.unmodifiableSet(stylesFor(RaidStyle.AUTO));
	}

	/** A type set with "any" spelled out, so choices that plan the same types compare equal. */
	private Set<AttackStyle.Type> spelled(Set<AttackStyle.Type> types)
	{
		return types.isEmpty() ? usableStyles : types;
	}

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

	/**
	 * Choices that plan this phase differently, Auto first: styles the target can't be damaged by, and choices
	 * planning the same types as an earlier one, are left out. One choice means the style is fixed.
	 */
	public List<RaidStyle> getStyleChoices()
	{
		List<RaidStyle> choices = new ArrayList<>();
		choices.add(RaidStyle.AUTO);
		List<Set<AttackStyle.Type>> seen = new ArrayList<>();
		seen.add(spelled(stylesFor(RaidStyle.AUTO)));
		for (RaidStyle style : RaidStyle.values())
		{
			Set<AttackStyle.Type> types = within(style);
			if (style != RaidStyle.AUTO && !types.isEmpty() && !seen.contains(types)) { choices.add(style); seen.add(types); }
		}
		return choices;
	}

	/**
	 * The listed choice showing the saved one: itself, or the entry planning the same types when it was left out
	 * as a duplicate (e.g. Magic when Auto already means magic) or can't damage this phase (shown as Auto).
	 */
	public RaidStyle getDisplayedStyleChoice()
	{
		List<RaidStyle> choices = getStyleChoices();
		if (choices.contains(styleChoice)) { return styleChoice; }
		for (RaidStyle choice : choices)
		{
			if (spelled(stylesFor(choice)).equals(spelled(styles))) { return choice; }
		}
		return RaidStyle.AUTO;
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
