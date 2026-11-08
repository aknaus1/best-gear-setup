package com.bestgearsetup.calc;

import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.NameLists;
import java.util.Map;

/** Tile reach and encounter restrictions, separate from damage and accuracy. */
public final class AttackReach
{
	/** Base range by weapon subcategory and name. */
	private static final Map<String, NameLists.NameTable> REACH = NameLists.tables("attack reach");
	private static final NameLists.NameList FLYING = NameLists.list("flying monsters");

	private AttackReach()
	{
	}

	/** Effective fighting distance. Zuk Auto covers the shield's corner positions. */
	public static int distance(Monster monster, int requested)
	{
		if (isZuk(monster))
		{
			// Five-tile attacks cannot reach Zuk. Auto uses nine tiles for shield corner coverage.
			return requested <= 0 ? 9 : Math.max(6, requested);
		}
		if (isAbyssalPortal(monster))
		{
			// The portal sits deeper in the room: only seven-tile attacks reach it.
			return Math.max(7, requested);
		}
		if (isVespula(monster) && requested <= 0)
		{
			// Vespula flies over the far side of the room; Auto fights from the portal's reach.
			return 7;
		}
		String name = monster.getLowerName();
		boolean zulrah = monster.getId() >= 2042 && monster.getId() <= 2044 || name.startsWith("zulrah");
		return Math.max(zulrah || isOlmHead(monster) ? 2 : 1, requested);
	}

	/** Olm's head is a separate target; restrictions must not apply to either hand. */
	public static boolean isOlmHead(Monster monster)
	{
		String name = monster.getLowerName();
		return monster.getId() == 7551 || monster.getId() == 7554
			|| name.startsWith("great olm") && name.contains("head");
	}

	/** The Inferno's final boss, excluding the wave's other NPCs. */
	public static boolean isZuk(Monster monster)
	{
		return monster.getId() == 7706 || named(monster, "tzkal-zuk");
	}

	/** Chambers of Xeric's Vespula, excluding the abyssal portal that ends the encounter. */
	public static boolean isVespula(Monster monster)
	{
		return monster.getId() >= 7530 && monster.getId() <= 7532 || named(monster, "vespula");
	}

	/** Vespula's abyssal portal in the Chambers of Xeric. */
	public static boolean isAbyssalPortal(Monster monster)
	{
		return monster.getId() == 7533 || named(monster, "abyssal portal");
	}

	/** Slayer krakens only; Sailing krakens and the boss's tentacles have different damage rules. */
	public static boolean isKraken(Monster monster)
	{
		return monster.getId() == 492 || monster.getId() == 494
			|| named(monster, "kraken") || named(monster, "cave kraken");
	}

	/** Encounters where even a halberd cannot attack with melee. */
	public static boolean isMeleeBlocked(Monster monster)
	{
		return isZuk(monster) || isKraken(monster)
			|| monster.getId() == 5535 || named(monster, "enormous tentacle");
	}

	private static boolean flying(Monster monster)
	{
		int id = monster.getId();
		return monster.hasAttribute("flying") || id >= 3162 && id <= 3165
			|| id >= 3169 && id <= 3183 || id == 7037 || id == 12443 || id == 6492 || id == 15697
			|| id == 7850 || id == 7852 || id == 7853 || id == 7884 || id == 7885
			|| id == 15623 || id == 15635 || FLYING.matches(monster.getLowerName().replace('\u2019', '\''));
	}

	private static boolean named(Monster monster, String target)
	{
		return EncounterDamage.versionOf(monster.getLowerName().replace('\u2019', '\''), target);
	}

	/** Melee legality independent of the selected tile distance or missing monster tags. */
	public static boolean meleeAllowed(Monster monster, GearItem weapon)
	{
		boolean salamander = weapon.getSubcategory().equals("salamander");
		int id = monster.getId();
		if (id >= 3169 && id <= 3183 || id == 7037 || id == 12443 || id == 6492 || id == 15697
			|| named(monster, "aviansie") || named(monster, "reanimated aviansie"))
		{
			return salamander;
		}
		return !isMeleeBlocked(monster)
			&& (!isOlmHead(monster) || weapon.getSubcategory().equals("polearm"))
			&& (!flying(monster) || weapon.getSubcategory().equals("polearm") || salamander);
	}

	/** Explain the encounter rule alongside the calculated results. */
	public static String restrictionNote(Monster monster)
	{
		if (isOlmHead(monster))
		{
			return "Olm head: melee requires a halberd; non-ranged damage is divided by 3. Assumes the final phase.";
		}
		if (EncounterDamage.olmPreferredClass(monster) != null)
		{
			return "Olm hand: off-style damage is divided by 3; hand immunity and healing phases are not simulated.";
		}
		if (isZuk(monster))
		{
			return "Zuk: melee excluded; Auto uses 9 tiles for shield corners. Line of sight is not checked.";
		}
		if (isAbyssalPortal(monster))
		{
			return "Abyssal portal: melee excluded; only weapons that reach 7 tiles can attack it.";
		}
		if (isVespula(monster))
		{
			return "Vespula: melee excluded; Auto assumes 7 tiles. Measure or set Distance if you fight closer.";
		}
		if (isKraken(monster))
		{
			return "Kraken: melee excluded; ranged hits are divided by 7 (minimum 1 for a positive hit).";
		}
		if (isMeleeBlocked(monster))
		{
			return "This target cannot be attacked with melee, including halberds.";
		}
		if (flying(monster))
		{
			return "Flying target: melee reach and equipment restrictions apply; aviansies require salamanders for melee.";
		}
		if (named(monster, "zebak") || monster.getId() == 11730)
		{
			return "Zebak: ordinary melee at 1 tile; halberds at 2 tiles; melee excluded farther away.";
		}
		return EncounterDamage.encounterNote(monster);
	}

	/** Whether this weapon style can attack from the selected distance in this encounter. */
	public static boolean canReach(Monster monster, GearItem weapon, AttackStyle style, int requested)
	{
		if (style.isMelee() && !meleeAllowed(monster, weapon))
		{
			return false;
		}
		int distance = distance(monster, requested);
		int range = range(weapon, style);
		// Unknown equipment can still be used adjacent to ordinary monsters. Never assume it can safespot.
		return range >= distance || range == 0 && distance == 1;
	}

	/**
	 * Maximum tile distance for a normal attack with this style; 0 means unknown.
	 * Longrange adds two tiles, capped at ten. Ranges follow the OSRS Wiki weapon combat tables.
	 */
	public static int range(GearItem weapon, AttackStyle style)
	{
		if (weapon.getSubcategory().equals("salamander"))
		{
			return 1;
		}
		if (style.isMelee())
		{
			return weapon.getSubcategory().equals("polearm") ? 2 : 1;
		}
		if (style.isAutocast())
		{
			return 10;
		}
		int base = baseRange(weapon);
		return base == 0 ? 0 : Math.min(10, base + (style.getStance().equals("longrange") ? 2 : 0));
	}

	static int baseRange(GearItem weapon)
	{
		String name = weapon.getLowerCombatName().replace("corrupted ", "");
		String subcategory = weapon.getSubcategory();
		if (subcategory.equals("thrown") && weapon.getId() == 28919 && name.startsWith("tonalztics of ralos"))
		{
			// The uncharged Tonalztics shares the charged item's name.
			return 6;
		}
		NameLists.NameTable ranges = REACH.get(subcategory);
		return ranges == null ? 0 : ranges.get(name, 0);
	}
}
