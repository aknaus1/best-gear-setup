package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.EncounterImmunities;
import com.bestgearsetup.calc.FreezeDuty;
import com.bestgearsetup.calc.KillTime;
import com.bestgearsetup.calc.RaidLoadout;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SpecialRotation;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.MonsterGroup;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.Builder;
import lombok.AccessLevel;
import net.runelite.api.gameval.ItemID;

/** One combat target or required attack style in a complete raid. */
@Value
public class RaidEncounter
{
	String label;
	MonsterSummary target;
	/** Attack types the encounter is planned with: the usable types narrowed by the style choice; empty for any. */
	Set<AttackStyle.Type> styles;
	/**
	 * Expected number of times this target (or phase) is fought, used only for the full raid's combat-time objective:
	 * fractional for an expectation, such as a Warden branch fought in half of raids.
	 */
	double repetitions;
	/**
	 * Share of the target's hitpoints dealt each time it is fought, for phases of one HP pool (Akkha's immune phases
	 * split her health, the Vasilias forms share one pool). Like {@link #repetitions} it only weighs the objective.
	 */
	double share;
	EncounterPhase phase;
	/** Attack types the encounter is restricted to by the raid itself; empty for any. */
	Set<AttackStyle.Type> requiredStyles;
	/** The required types the target, in this phase, can be damaged by (e.g. not ranged for Tekton). */
	Set<AttackStyle.Type> usableStyles;
	/** What Auto means here, e.g. ranged or magic for bosses that are not fought in melee range. */
	RaidStyle preferredStyle;
	/**
	 * The key a style choice is saved under: the label, or one shared by the phases of a single fight (Tekton and
	 * enraged Tekton, the Akkha phases...). Separate targets such as the Olm parts never share one.
	 */
	String styleKey;
	/** The player's saved choice for {@link #styleKey}. */
	RaidStyle styleChoice;
	/** The saved choice can't damage this phase (e.g. melee against Akkha immune to melee), so Auto is used. */
	boolean styleFallback;
	/** Raid-handed boosts (CoX overloads, ToA smelling salts) are available by this point of the raid. */
	boolean raidSupplies;
	/** Primary attack required by the player's raid duty. */
	RaidLoadout loadoutRequirement;
	/**
	 * Shown with the fastest setup the finished kit allows, but never weighs on the kit: how often it's fought varies
	 * (scavengers are killed until the potion secondaries drop) or it usually isn't attacked at all (Vespula).
	 */
	boolean displayOnly;
	/**
	 * Encounters sharing a group (the Nylocas Vasilias forms, the ToA baboons) are fought back to back: one wears a
	 * full setup and the others are quick switches from it, within {@link #switchLimit} swaps. Null for none.
	 */
	String switchGroup;
	/** May wear the group's full setup; false for explicitly restricted forms. */
	boolean fullSwitchSetup;
	/**
	 * Most swaps a quick switch in this encounter's group may make: the weapon and at most one fewer other pieces,
	 * drawn from slots every quick switch of the group shares, so switching between any two forms stays within it.
	 * A limit, not a target: a piece is only swapped when it makes the switch faster.
	 */
	int switchLimit;
	/** In the raid's first room (ToA's first path): its boosts are drunk before entering, so the kit doesn't carry them. */
	boolean prepotted;
	/**
	 * In the raid's first room (ToA's first path), so the kit may be worn in for this encounter: the starting setup
	 * is whichever such encounter leaves the fewest switches to carry.
	 */
	boolean startable;
	/** Not worth a potion (Kephri's scarabs): fought without the Best available boosts or raid supplies. */
	boolean unboosted;
	/**
	 * Seconds without combat before this encounter (puzzles, preparation, walking), while spec energy regenerates.
	 * The raid starts on a full bar and energy carries from fight to fight ({@link #energyAfter}).
	 */
	double restBefore;
	/** The bar is full again when this encounter starts: each Theatre room refills it. */
	boolean refill;
	/** Players in the party: the fight lasts its estimated time over this, while spec energy regenerates. */
	int partySize;
	/**
	 * Whether the whole party fights each kill together (a boss), rather than each player killing their own (the
	 * waves' nylocas, baboons, scarabs): specs are planned over one player's share of a shared kill.
	 */
	boolean shared;
	/**
	 * The share of this encounter's kills followed by a pause, when each player's last attack doesn't wait out its
	 * attack interval: 1 for a boss or phase that ends the fight, a phase's share of a pool that may end in any of
	 * them, and 0 for adds killed back to back and phases that end at a threshold.
	 */
	double finalBlow;
	/** Damaged only while it stops between walks (the Pestilent Bloat), or null for a target fought throughout. */
	Downs downs;

	/**
	 * A target the party damages only while it is down: it first walks for {@code firstWalk} ticks, then stays down for
	 * {@code down} ticks at a time with {@code walk} ticks of walking between, until the party's damage while it is down
	 * kills it. Fewer downs save a whole walk each, so the fight's time steps with the party's DPS.
	 */
	@Value
	public static class Downs
	{
		int firstWalk;
		int down;
		int walk;
		int partySize;

		/** The party's time scaled by its size (as for other encounters), at this DPS per player. */
		double seconds(double dps, int hitpoints)
		{
			if (!(dps > 0)) { return Double.POSITIVE_INFINITY; }
			int players = Math.max(1, partySize);
			double partyDps = players * dps;
			double perDown = partyDps * down * 0.6;
			double downs = Math.max(1, Math.ceil(hitpoints / perDown));
			double last = hitpoints - (downs - 1) * perDown;
			return players * (firstWalk * 0.6 + (downs - 1) * (down + walk) * 0.6 + last / partyDps);
		}
	}

	/** Swaps a quick switch may make unless the player chooses otherwise: the weapon and one other piece. */
	public static final int DEFAULT_SWITCH_LIMIT = 2;
	/** The highest swap limit offered; larger switches search slowly and are rarely made mid-fight. */
	public static final int MAX_SWITCH_LIMIT = 6;
	/** A freeze switch may swap every slot: it swaps as few as reach the freeze accuracy ({@link FreezeDuty}). */
	static final int FREEZE_SWITCH_LIMIT = Slot.values().length;
	/**
	 * A freeze adds no combat time; each item its switch swaps weighs this much in the kit's objective (casting by hand
	 * more than any switch, {@link FreezeDuty#burden}), so autocasting and fewer items win between otherwise equal
	 * kits without showing in the raid's estimated time.
	 */
	static final double FREEZE_ITEM_SECONDS = 1e-3;

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
		this(label, target, required(styles), repetitions, 1, phase, RaidStyle.AUTO, label, RaidStyle.AUTO,
			true, RaidLoadout.ANY, false, null, true, DEFAULT_SWITCH_LIMIT, false, false, false, 0, false, 1, true, 1, null);
	}

	@Builder(toBuilder = true, access = AccessLevel.PRIVATE)
	private RaidEncounter(String label, MonsterSummary target, Set<AttackStyle.Type> requiredStyles, double repetitions,
		double share, EncounterPhase phase, RaidStyle preferredStyle, String styleKey, RaidStyle styleChoice,
		boolean raidSupplies, RaidLoadout loadoutRequirement, boolean displayOnly, String switchGroup,
		boolean fullSwitchSetup, int switchLimit, boolean prepotted, boolean startable, boolean unboosted,
		double restBefore, boolean refill, int partySize, boolean shared, double finalBlow, Downs downs)
	{
		// Zero is allowed: a fight the party may not reach (e.g. no Vasa crystal before he dies) still shapes the kit.
		if (!(repetitions >= 0)) { throw new IllegalArgumentException("Encounter repetitions must not be negative"); }
		if (!(share > 0 && share <= 1)) { throw new IllegalArgumentException("Encounter share must be in (0, 1]"); }
		this.label = label;
		this.target = target;
		this.repetitions = repetitions;
		this.share = share;
		this.phase = phase;
		this.requiredStyles = requiredStyles;
		this.usableStyles = usable(target, phase, requiredStyles);
		this.preferredStyle = preferredStyle;
		this.styleKey = styleKey;
		this.styleChoice = styleChoice;
		this.raidSupplies = raidSupplies;
		this.loadoutRequirement = loadoutRequirement;
		this.displayOnly = displayOnly;
		this.switchGroup = switchGroup;
		this.prepotted = prepotted;
		this.startable = startable;
		this.unboosted = unboosted;
		this.restBefore = restBefore;
		this.refill = refill;
		this.partySize = partySize;
		this.shared = shared;
		this.finalBlow = finalBlow;
		this.downs = downs;
		this.fullSwitchSetup = fullSwitchSetup;
		this.switchLimit = switchLimit;
		this.styleFallback = styleChoice != RaidStyle.AUTO && within(styleChoice).isEmpty();
		this.styles = Collections.unmodifiableSet(stylesFor(styleChoice));
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

	/** The one combat class the encounter can be fought with, or null when it allows several. */
	public CombatClass getFixedClass()
	{
		Set<CombatClass> classes = usableStyles.stream().map(RaidEncounter::classOf).collect(Collectors.toSet());
		return classes.size() == 1 ? classes.iterator().next() : null;
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
		return toBuilder().preferredStyle(preferred).build();
	}

	RaidEncounter withStyleChoice(RaidStyle choice)
	{
		return toBuilder().styleChoice(choice).build();
	}

	/** Share a style choice with the other phases of the same fight. */
	RaidEncounter withStyleKey(String key)
	{
		return toBuilder().styleKey(key).build();
	}

	RaidEncounter withRaidSupplies(boolean available)
	{
		return toBuilder().raidSupplies(available).build();
	}

	RaidEncounter withLoadoutRequirement(RaidLoadout requirement)
	{
		return toBuilder().loadoutRequirement(requirement).build();
	}

	/** Fought this many times (an expectation when fractional) in the combat-time objective. */
	RaidEncounter withRepetitions(double times)
	{
		return toBuilder().repetitions(times).build();
	}

	/** Each time, only this share of the target's hitpoints is dealt (a phase of one HP pool). */
	RaidEncounter withShare(double fraction)
	{
		return toBuilder().share(fraction).build();
	}

	/**
	 * Spec energy regenerates for {@code restBefore} seconds before this fight, or the bar is full again when it
	 * {@code refill}s; the fight itself lasts its time over the {@code party}'s size.
	 */
	RaidEncounter withEnergy(double restBefore, boolean refill, int party)
	{
		return toBuilder().restBefore(restBefore).refill(refill).partySize(Math.max(1, party)).build();
	}

	/**
	 * Spec energy (percent) left after this fight, from {@code before} left after the previous one: it regenerates
	 * over the rest before this fight (or the bar refills), then a setup opening on a full bar spends its opening specs
	 * and every setup retains regeneration it does not spend. NaN when a draining opener has no funded entry bar,
	 * the entry bar cannot pay the reserved opening, or total special spending exceeds available energy.
	 */
	double energyAfter(double before, SearchResults result, SetupResult setup)
	{
		return energyAfter(before, setup, () -> estimatedSeconds(result, setup));
	}

	/** As above, with the fight's {@link #estimatedSeconds} supplied. */
	double energyAfter(double before, SetupResult setup, DoubleSupplier seconds)
	{
		double energy = refill ? SpecialRotation.FULL_ENERGY
			: Math.min(SpecialRotation.FULL_ENERGY, before + SpecialRotation.REGEN_PER_SECOND * restBefore);
		com.bestgearsetup.calc.SpecialPlan plan = setup.getSpecial();
		if (plan != null && !setup.usesFullBar() && setup.getSpecials().stream().anyMatch(special -> special.getDrained() != null))
		{
			// A regenerated-only damage plan may wait for energy. A phase-entry drain may never borrow future energy.
			return Double.NaN;
		}
		if (setup.usesFullBar())
		{
			if (energy + 1e-9 < plan.getOpeningEnergy()) { return Double.NaN; }
		}
		GearItem ring = setup.getLoadout().get(Slot.RING);
		double regen = SpecialRotation.REGEN_PER_SECOND * (ring != null && ring.getId() == ItemID.LIGHTBEARER ? 2 : 1);
		double gained = regen * seconds.getAsDouble() / Math.max(1, partySize);
		double spent = 0;
		if (plan != null)
		{
			if (setup.getSpecials().stream().anyMatch(special -> special.getSpecial() == null))
			{
				// Legacy synthetic plans without a weapon's energy data assumed all fight regeneration was spent.
				spent = gained + (setup.usesFullBar() ? barsSpent() * plan.getOpeningEnergy() : 0);
			}
			else
			{
				spent = specEnergy(plan) * (setup.usesFullBar() ? barsSpent() : repetitions);
				if (setup.usesFullBar() && repetitions > 1 && plan.getRegeneratedSpecsPerKill() > 0)
				{ spent += (repetitions - 1) * specEnergy(plan.withRegeneratedEnergy()); }
			}
		}
		double left = energy + gained - spent;
		return left < -1e-6 ? Double.NaN : Math.min(SpecialRotation.FULL_ENERGY, Math.max(0, left));
	}

	private static double specEnergy(com.bestgearsetup.calc.SpecialPlan plan)
	{
		return plan.getAll().stream().mapToDouble(special -> special.getSpecsPerKill() * special.getSpecial().getEnergy()).sum();
	}

	/** Damaged only while down between walks, in these ticks ({@link Downs}). */
	RaidEncounter withDowns(Downs cycle)
	{
		return toBuilder().downs(cycle).build();
	}

	/** Each player kills their own (adds), and none of the kills is followed by a pause. */
	RaidEncounter withKillsEach()
	{
		return toBuilder().shared(false).finalBlow(0).build();
	}

	/** This share of the encounter's kills is followed by a pause ({@link #finalBlow}). */
	RaidEncounter withFinalBlow(double share)
	{
		return toBuilder().finalBlow(share).build();
	}

	/** Players sharing each kill: the party for a boss, 1 for adds each player kills. */
	public int getKillShare()
	{
		return shared ? Math.max(1, partySize) : 1;
	}

	/** The opening bars a full-bar setup spends here: one fight's worth, or its expectation when fought in some raids. */
	double barsSpent()
	{
		return Math.min(1, repetitions);
	}

	RaidEncounter withDisplayOnly()
	{
		return toBuilder().displayOnly(true).build();
	}

	RaidEncounter withSwitchGroup(String group)
	{
		return withSwitchGroup(group, true);
	}

	RaidEncounter withSwitchGroup(String group, boolean fullSetup)
	{
		return toBuilder().switchGroup(group).fullSwitchSetup(fullSetup).build();
	}

	RaidEncounter withSwitchLimit(int limit)
	{
		return toBuilder().switchLimit(Math.max(1, Math.min(MAX_SWITCH_LIMIT, limit))).build();
	}

	/** In a freeze's switch group, whose switch may swap every slot ({@link #FREEZE_SWITCH_LIMIT}). */
	RaidEncounter withFreezeSwitches()
	{
		return toBuilder().switchLimit(FREEZE_SWITCH_LIMIT).build();
	}

	/** A freeze duty: Ice Barrage is cast to freeze, and each cast should land ({@link FreezeDuty}). */
	public boolean isFreeze()
	{
		return loadoutRequirement == RaidLoadout.ICE_BARRAGE;
	}

	/**
	 * Whether this group member's setup may be a quick switch from {@code base}. A freeze may swap every slot, so the
	 * slot count says nothing: it must have been searched from the gear {@code base} wears, or its items (and whether
	 * it lands) were counted from different gear. {@code base} may also wear pieces in slots that gear left empty, as
	 * the finished kit does ({@link RaidPlan#fill}), when they don't lower magic attack.
	 */
	boolean switchableFrom(SetupResult setup, Loadout base)
	{
		if (!isFreeze()) { return true; }
		Loadout from = setup.getSwitchFrom();
		if (from == null) { return false; }
		if (!FreezeDuty.keepsWornGear(setup.getLoadout(), from)) { return false; }
		for (Slot slot : Slot.values())
		{
			GearItem searched = from.get(slot);
			GearItem worn = base.get(slot);
			if (searched == null ? worn != null && worn.getMagicBonus() < 0 : worn == null || worn.getId() != searched.getId())
			{
				return false;
			}
		}
		return true;
	}

	RaidEncounter withPrepotted()
	{
		return toBuilder().prepotted(true).build();
	}

	RaidEncounter withStartable()
	{
		return toBuilder().startable(true).build();
	}

	RaidEncounter withUnboosted()
	{
		return toBuilder().unboosted(true).build();
	}

	/** Indices of the encounters the kit may start in, in list order; the first encounter when none is marked. */
	static int[] starts(List<RaidEncounter> encounters)
	{
		int[] starts = java.util.stream.IntStream.range(0, encounters.size()).filter(i -> encounters.get(i).isStartable()).toArray();
		return starts.length == 0 && !encounters.isEmpty() ? new int[]{0} : starts;
	}

	/** Indices of each switch group's encounters, in list order; groups of one aren't listed. */
	static List<int[]> switchGroups(List<RaidEncounter> encounters)
	{
		java.util.Map<String, List<Integer>> groups = new java.util.LinkedHashMap<>();
		for (int index = 0; index < encounters.size(); index++)
		{
			String group = encounters.get(index).getSwitchGroup();
			if (group != null) { groups.computeIfAbsent(group, g -> new ArrayList<>()).add(index); }
		}
		List<int[]> out = new ArrayList<>();
		groups.values().stream().filter(group -> group.size() > 1)
			.forEach(group -> out.add(group.stream().mapToInt(Integer::intValue).toArray()));
		return out;
	}

	/**
	 * The extra slots a group's quick switches may share under {@code limit} swaps: every largest choice of the
	 * {@code used} slots (those the candidates change besides the weapon) that fits beside the weapon. A smaller
	 * choice allows nothing a larger one doesn't.
	 */
	static List<Set<Slot>> extraSlotChoices(Set<Slot> used, int limit)
	{
		List<Slot> slots = new ArrayList<>(used);
		slots.remove(Slot.WEAPON);
		List<Set<Slot>> out = new ArrayList<>();
		choose(slots, 0, Math.max(0, Math.min(limit - 1, slots.size())), EnumSet.noneOf(Slot.class), out);
		return out;
	}

	private static void choose(List<Slot> slots, int from, int size, EnumSet<Slot> chosen, List<Set<Slot>> out)
	{
		if (chosen.size() == size) { out.add(EnumSet.copyOf(chosen)); return; }
		for (int index = from; index <= slots.size() - (size - chosen.size()); index++)
		{
			chosen.add(slots.get(index));
			choose(slots, index + 1, size, chosen, out);
			chosen.remove(slots.get(index));
		}
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

	/**
	 * Keep phase HP and DPS intact; repeat the estimated fight time instead. Each fight deals this encounter's share
	 * of the target's hitpoints in whole attacks ({@link KillTime}), so overkill on small targets counts. Display-only
	 * encounters add none, and a freeze only its switch's burden ({@link #FREEZE_ITEM_SECONDS}).
	 */
	double estimatedSeconds(SearchResults result, SetupResult setup)
	{
		if (displayOnly) { return 0; }
		if (isFreeze()) { return FREEZE_ITEM_SECONDS * FreezeDuty.burden(setup); }
		int hitpoints = hitpoints(result);
		double once = fightSeconds(setup.getDps(), hitpoints);
		if (setup.usesFullBar() && repetitions > 1)
		{
			// Only the first of repeated fights opens on the full bar; the rest spend regenerated energy.
			return once + (repetitions - 1) * fightSeconds(setup.getSpecial().getRegenerated(), hitpoints);
		}
		return repetitions * once;
	}

	/** One fight's time: whole attacks to kill, or the target's downs when it is only damaged while down. */
	private double fightSeconds(DpsResult dps, int hitpoints)
	{
		return downs == null ? KillTime.seconds(dps, hitpoints) : downs.seconds(dps.getPrimaryDps(), hitpoints);
	}

	/** Hitpoints dealt each time this encounter is fought: its share of the target's, at least 1. */
	int hitpoints(SearchResults result)
	{
		return Math.max(1, (int) Math.round(share * result.getStartingHitpoints()));
	}

	@Override public String toString() { return label; }
}
