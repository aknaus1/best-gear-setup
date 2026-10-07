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
	/** How landed stat drains from those specs count. */
	@Builder.Default
	DrainSpecs drainSpecs = DrainSpecs.EXPECTED;

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

	@Builder.Default
	Map<Slot, SlotLock> locks = Collections.emptyMap();
	@Builder.Default
	Set<Integer> excluded = Collections.emptySet();
}
