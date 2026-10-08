package com.bestgearsetup.calc;

import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Protective equipment some Slayer monsters require, on or off task: without it they drain stats and hit
 * constantly, or can't be damaged at all, so no unprotected setup is usable. Each rule follows the monster's
 * OSRS Wiki page; superior variants share their base monster's rule. Requirements that are only recommended
 * (insulated boots, slayer gloves, witchwood icon) or only needed to start a fight (wall beasts) are notes.
 */
public final class SlayerEquipment
{
	private static final Predicate<String> SLAYER_HELMET = n -> n.contains("slayer helmet");
	private static final Predicate<String> KARUULM_ONLY = names(
		"hydra", "colossal hydra", "alchemical hydra", "drake", "guardian drake", "sulphur lizard");
	private static final Predicate<String> WYRMS = names("wyrm", "shadow wyrm");
	private static final List<Rule> RULES = Collections.unmodifiableList(Arrays.asList(
		new Rule(Slot.HEAD, "a facemask or slayer helmet", false,
			names("dust devil", "choke devil", "smoke devil", "nuclear smoke devil", "thermonuclear smoke devil"),
			SLAYER_HELMET.or(n -> n.equals("facemask"))),
		new Rule(Slot.HEAD, "earmuffs or a slayer helmet", false,
			names("banshee", "twisted banshee", "screaming banshee", "screaming twisted banshee"),
			SLAYER_HELMET.or(n -> n.equals("earmuffs"))),
		new Rule(Slot.HEAD, "a nose peg or slayer helmet", false,
			names("aberrant spectre", "deviant spectre", "abhorrent spectre", "repugnant spectre"),
			SLAYER_HELMET.or(n -> n.equals("nose peg"))),
		new Rule(Slot.HEAD, "reinforced goggles or a slayer helmet", false, n -> n.equals("sourhog"),
			SLAYER_HELMET.or(n -> n.equals("reinforced goggles"))),
		// During A Porcine of Interest Spria hands over the goggles first; slayer helmets only gain their
		// protection when the quest is complete, so the quest's sourhog needs the goggles themselves.
		new Rule(Slot.HEAD, "reinforced goggles", false, n -> n.equals("sourhog (a porcine of interest)"),
			n -> n.equals("reinforced goggles")),
		new Rule(Slot.SHIELD, "a mirror shield or V's shield", false,
			names("basilisk", "basilisk knight", "basilisk sentinel", "monstrous basilisk", "cockatrice", "cockathrice"),
			n -> n.equals("mirror shield") || n.equals("v's shield")),
		// The Wiki names only the mirror shield for younglings.
		new Rule(Slot.SHIELD, "a mirror shield", false, names("basilisk youngling"), n -> n.equals("mirror shield")),
		new Rule(Slot.SHIELD, "a lit bug lantern", false, names("harpie bug swarm"), n -> n.equals("lit bug lantern")),
		// Wyrms share this rule only when the search location is Karuulm.
		new Rule(Slot.FEET, "boots of stone, boots of brimstone or granite boots", true,
			KARUULM_ONLY.or(WYRMS),
			n -> n.equals("boots of stone") || n.equals("boots of brimstone") || n.equals("granite boots"))));

	private SlayerEquipment()
	{
	}

	/** Names equal to one of the bases or a base followed by a parenthesised version. */
	private static Predicate<String> names(String... bases)
	{
		return n ->
		{
			for (String base : bases)
			{
				if (EncounterDamage.versionOf(n, base))
				{
					return true;
				}
			}
			return false;
		};
	}

	/** The rules this target imposes under these settings. */
	private static List<Rule> rules(Monster monster, OptimizerSettings settings)
	{
		String name = monster.getLowerName();
		List<Rule> out = new ArrayList<>();
		for (Rule rule : RULES)
		{
			if (rule.monster.test(name)
				&& !(rule.karuulm && (settings.isKourendEliteDiary() || !inKaruulm(monster, settings))))
			{
				out.add(rule);
			}
		}
		return out;
	}

	/** Whether the target lives only inside the Karuulm Slayer Dungeon, where heat-protection boots apply. */
	public static boolean inKaruulm(Monster monster)
	{
		String name = monster.getLowerName();
		return KARUULM_ONLY.test(name);
	}

	/** Whether heat-protection boots apply at this search's target location, before the diary exemption. */
	public static boolean inKaruulm(Monster monster, OptimizerSettings settings)
	{
		return inKaruulm(monster) || settings.isKaruulmDungeon() && WYRMS.test(monster.getLowerName());
	}

	/** Whether the target requires protective equipment in any slot. */
	public static boolean applies(Monster monster, OptimizerSettings settings)
	{
		return !rules(monster, settings).isEmpty();
	}

	/** Whether the item satisfies one of this target's requirements, so it must stay a search candidate. */
	static boolean isRequired(Monster monster, OptimizerSettings settings, GearItem item)
	{
		for (Rule rule : rules(monster, settings))
		{
			if (rule.satisfiedBy(item))
			{
				return true;
			}
		}
		return false;
	}

	static boolean allowed(Monster monster, OptimizerSettings settings, Loadout loadout)
	{
		for (Rule rule : rules(monster, settings))
		{
			GearItem worn = loadout.get(rule.slot);
			if (rule.slot == Slot.SHIELD && loadout.getWeapon() != null && loadout.getWeapon().isTwoHanded())
			{
				worn = null;
			}
			if (!rule.satisfiedBy(worn))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Why a slot lock leaves out required equipment, or null if it doesn't.
	 *
	 * @param item the locked item, or null for an empty / fill lock
	 */
	static String lockConflict(Monster monster, OptimizerSettings settings, Slot slot, GearItem item)
	{
		for (Rule rule : rules(monster, settings))
		{
			if (rule.slot == slot && !rule.satisfiedBy(item))
			{
				return monster.getDisplayName() + " requires " + rule.items + " in the "
					+ slot.getDisplayName().toLowerCase(Locale.ROOT) + " slot";
			}
		}
		return null;
	}

	/** Summaries for the search details: enforced requirements plus related mechanics that are not enforced. */
	public static List<String> notes(Monster monster, OptimizerSettings settings)
	{
		String name = monster.getLowerName();
		List<String> notes = new ArrayList<>();
		for (Rule rule : rules(monster, settings))
		{
			notes.add("Required equipment: every setup wears " + rule.items + " ("
				+ rule.slot.getDisplayName().toLowerCase(Locale.ROOT) + "), on or off task"
				+ (rule.slot == Slot.SHIELD ? ", so two-handed weapons are excluded." : ".")
				+ (rule.karuulm ? " The Elite Kourend & Kebos Diary removes the boots requirement." : ""));
		}
		if (inKaruulm(monster, settings) && settings.isKourendEliteDiary())
		{
			notes.add("Karuulm Slayer Dungeon: no heat-protection boots needed (Elite Kourend & Kebos Diary complete).");
		}
		if (WYRMS.test(name) && !inKaruulm(monster, settings))
		{
			notes.add("Wyrms in the Karuulm Slayer Dungeon need boots of stone, boots of brimstone or granite boots"
				+ " (unless the Elite Kourend & Kebos Diary is complete); Wyrmscraig wyrms don't, so boots are not enforced.");
		}
		if (names("wall beast").test(name))
		{
			notes.add("Wall beasts: a spiny helmet or slayer helmet is needed only to start the fight, so the head slot"
				+ " is not restricted.");
		}
		return notes;
	}

	private static final class Rule
	{
		private final Slot slot;
		private final String items;
		/** Waived by the Elite Kourend & Kebos Diary. */
		private final boolean karuulm;
		private final Predicate<String> monster;
		private final Predicate<String> item;

		private Rule(Slot slot, String items, boolean karuulm, Predicate<String> monster, Predicate<String> item)
		{
			this.slot = slot;
			this.items = items;
			this.karuulm = karuulm;
			this.monster = monster;
			this.item = item;
		}

		private boolean satisfiedBy(GearItem worn)
		{
			return worn != null && (worn.getSlot() == null || worn.getSlot() == slot) && item.test(worn.getLowerCombatName());
		}
	}
}
