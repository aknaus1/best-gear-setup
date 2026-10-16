package com.bestgearsetup.calc;

import com.bestgearsetup.data.Slot;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class OptimizerSettings
{
	SearchMode mode;
	/** Maximum GP to spend on items not already owned (BUDGET mode only). */
	long budget;
	/** Limit all equipped expensive items for a Wilderness search, including owned gear. */
	@Builder.Default
	boolean wildernessRiskLimited = false;
	@Builder.Default
	int maxExpensiveItems = 3;
	/** An item at or above this acquisition value plus PvP repair fee is expensive; unknown values count too. */
	@Builder.Default
	long expensiveItemThreshold = 1_000_000;
	/** Lower-case spellbook names the player may autocast from: standard, ancient, arceuus. */
	Set<String> spellbooks;
	/** How many setups to return per combat class. */
	@Builder.Default
	int resultsPerClass = 3;

	/** Attack types that may be used. */
	@Builder.Default
	Set<AttackStyle.Type> styles = EnumSet.allOf(AttackStyle.Type.class);
	/** Raid duties may require an actual freeze spell or chinchompas rather than any attack of that class. */
	@Builder.Default
	RaidLoadout raidLoadout = RaidLoadout.ANY;
	/**
	 * A raid switch: setups keep this worn setup except for the weapon and at most {@link #switchLimit} - 1 other
	 * slots, without fill items or special attacks. Null searches every slot freely.
	 */
	@Builder.Default
	Loadout switchFrom = null;
	/** Most slots a {@link #switchFrom} switch may change, weapon included. */
	@Builder.Default
	int switchLimit = 2;
	/**
	 * A freeze duty's target chance (0..1) to land each cast ({@link FreezeDuty}): a {@link #switchFrom} switch swaps
	 * the fewest slots that reach it. 0 for no freeze duty.
	 */
	@Builder.Default
	double freezeAccuracy = 0;
	@Builder.Default
	WeaponHands weaponHands = WeaponHands.ANY;
	@Builder.Default
	CalcMode calcMode = CalcMode.DPS;
	@Builder.Default
	SearchDepth depth = SearchDepth.NORMAL;

	/** Experience filter: styles granting a disallowed experience type are never used. */
	@Builder.Default
	boolean attackXp = true;
	@Builder.Default
	boolean strengthXp = true;
	@Builder.Default
	boolean defenceXp = true;

	/** False restricts to free-to-play items. */
	@Builder.Default
	boolean membersItems = true;
	/** Optional world-specific equipment is excluded unless explicitly enabled. */
	@Builder.Default
	boolean dmmItems = false;
	@Builder.Default
	boolean betaItems = false;
	@Builder.Default
	boolean bountyHunterItems = false;
	/**
	 * Ranged attacks firing ammunition an Ava's device recovers must wear an Ava's device or quiver; special-attack
	 * switches are exempt. Name retained for saved-setting compatibility.
	 */
	@Builder.Default
	boolean requireAtlatlAmmoRecovery = false;

	@Builder.Default
	FillMode fillMode = FillMode.NONE;
	@Builder.Default
	DefenceFocus defenceFocus = DefenceFocus.TARGET;
	/** How much DPS (percent) may be given up to fit fill items. */
	@Builder.Default
	double fillMarginPercent = 0;

	/** Use special attacks during the kill, switching to the best spec weapon in the same combat class. */
	@Builder.Default
	boolean killSpecials = false;
	@Builder.Default
	SpecEnergy specEnergy = SpecEnergy.REGENERATION;
	/** Percent available now for a live raid rotation; planning starts on a full bar. */
	@Builder.Default
	int raidOpeningEnergy = 100;
	/** How landed stat drains from those specs count. */
	@Builder.Default
	DrainSpecs drainSpecs = DrainSpecs.EXPECTED;
	/**
	 * In a raid plan, the players sharing each kill of this fight (the party for a boss, 1 for adds one player
	 * kills): specs are planned over one player's share of its HP, combining one defence reducer and one damage weapon. 0 outside a
	 * raid plan, where each kill is planned on its own as before.
	 */
	@Builder.Default
	int raidKillShare = 0;
	/**
	 * A raid boss the whole party opens with defence reducers ({@link SpecialRotation#teamReducerTarget}): staggered
	 * attempts with the reference teammate's dragon warhammer ({@link SpecialRotation#teammateHammer}) before this
	 * player's own, the same for every kit and class.
	 */
	@Builder.Default
	boolean teamOpeners = false;
	/**
	 * The player could use an elder maul, dragon warhammer or Bandos godsword as a spec switch here (levels, equipment
	 * rules, ownership or budget, and the player's own exclusions; {@link Optimizer#sharedReducerUsable}), so every kit,
	 * whatever its class, owes its fair share of the party's reducers ({@link SpecialRotation#reducerShare}) even where
	 * the kit planner leaves the weapon out.
	 */
	@Builder.Default
	boolean sharedReducerUsable = false;
	/**
	 * In a raid plan, the share of this fight's kills followed by a pause, so each player's last attack doesn't wait
	 * out its interval and may be a hard-hitting slow spec (a crystal halberd finishing the kill).
	 */
	@Builder.Default
	double finalBlows = 0;

	/** Number of arrows / bolts / darts to price in the budget; 0 ignores ammo cost. */
	@Builder.Default
	int ammoCount = 0;

	/** Distance from the nearest monster tile; 0 uses the encounter's minimum reach. */
	@Builder.Default
	int targetDistance = 0;

	@Builder.Default
	boolean requireFireProtection = true;
	@Builder.Default
	Antifire antifire = Antifire.SUPER;
	@Builder.Default
	boolean protectMagic = true;
	/** The Elite Kourend & Kebos Diary removes the Karuulm Slayer Dungeon's heat-protection boots requirement. */
	@Builder.Default
	boolean kourendEliteDiary = false;
	/** The selected target is in Karuulm, or the search plans a matching Karuulm assignment. */
	@Builder.Default
	boolean karuulmDungeon = false;

	@Builder.Default
	Map<Slot, SlotLock> locks = Collections.emptyMap();
	@Builder.Default
	Set<Integer> excluded = Collections.emptySet();
	/** User supply exclusions, separate from the extra equipment restrictions used during raid consolidation. */
	@Builder.Default
	Set<Integer> excludedSupplies = Collections.emptySet();
}
