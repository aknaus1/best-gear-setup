package com.bestgearsetup.calc;

import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.NameLists;
import com.bestgearsetup.data.Slot;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Protective equipment some Slayer monsters require, on or off task: without it they drain stats and hit
 * constantly, or can't be damaged at all, so no unprotected setup is usable. The rules are bundled in
 * name-lists.json; superior variants share their base monster's rule. Requirements that are only recommended
 * (insulated boots, slayer gloves, witchwood icon) or only needed to start a fight (wall beasts) are notes.
 */
public final class SlayerEquipment
{
	private static final NameLists.NameList KARUULM_ONLY = NameLists.list("karuulm monsters");
	private static final NameLists.NameList WYRMS = NameLists.list("wyrms");
	/** One rule per row of name-lists.json's "slayer equipment", each from the monster's OSRS Wiki page. */
	private static final List<Rule> RULES = rules();

	private SlayerEquipment()
	{
	}

	private static List<Rule> rules()
	{
		List<Rule> rules = new ArrayList<>();
		for (JsonElement element : NameLists.rows("slayer equipment"))
		{
			JsonObject row = element.getAsJsonObject();
			rules.add(new Rule(Slot.valueOf(row.get("slot").getAsString()), row.get("items").getAsString(),
				row.has("karuulm") && row.get("karuulm").getAsBoolean(), NameLists.NameList.of(row.get("monsters")),
				NameLists.NameList.of(row.get("gear"))));
		}
		return Collections.unmodifiableList(rules);
	}

	/** The rules this target imposes under these settings. */
	private static List<Rule> rules(Monster monster, OptimizerSettings settings)
	{
		String name = monster.getLowerName();
		List<Rule> out = new ArrayList<>();
		for (Rule rule : RULES)
		{
			if (rule.monster.matches(name)
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
		return KARUULM_ONLY.matches(name);
	}

	/** Whether heat-protection boots apply at this search's target location, before the diary exemption. */
	public static boolean inKaruulm(Monster monster, OptimizerSettings settings)
	{
		return inKaruulm(monster) || settings.isKaruulmDungeon() && WYRMS.matches(monster.getLowerName());
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
		if (WYRMS.matches(name) && !inKaruulm(monster, settings))
		{
			notes.add("Wyrms in the Karuulm Slayer Dungeon need boots of stone, boots of brimstone or granite boots"
				+ " (unless the Elite Kourend & Kebos Diary is complete); Wyrmscraig wyrms don't, so boots are not enforced.");
		}
		if (EncounterDamage.versionOf(name, "wall beast"))
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
		private final NameLists.NameList monster;
		private final NameLists.NameList item;

		private Rule(Slot slot, String items, boolean karuulm, NameLists.NameList monster, NameLists.NameList item)
		{
			this.slot = slot;
			this.items = items;
			this.karuulm = karuulm;
			this.monster = monster;
			this.item = item;
		}

		private boolean satisfiedBy(GearItem worn)
		{
			return worn != null && (worn.getSlot() == null || worn.getSlot() == slot) && item.matches(worn.getLowerCombatName());
		}
	}
}
