package com.bestgearsetup.calc;

import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import java.util.Locale;

/** Tile reach and encounter restrictions, separate from damage and accuracy. */
public final class AttackReach
{
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
		String name = monster.getName().toLowerCase(Locale.ROOT);
		boolean zulrah = monster.getId() >= 2042 && monster.getId() <= 2044 || name.startsWith("zulrah");
		return Math.max(zulrah || isOlmHead(monster) ? 2 : 1, requested);
	}

	/** Olm's head is a separate target; restrictions must not apply to either hand. */
	public static boolean isOlmHead(Monster monster)
	{
		String name = monster.getName().toLowerCase(Locale.ROOT);
		return monster.getId() == 7551 || monster.getId() == 7554
			|| name.startsWith("great olm") && name.contains("head");
	}

	/** The Inferno's final boss, excluding the wave's other NPCs. */
	public static boolean isZuk(Monster monster)
	{
		return monster.getId() == 7706 || named(monster, "tzkal-zuk");
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
			|| id == 15623 || id == 15635 || named(monster, "kree'arra")
			|| named(monster, "wingman skree") || named(monster, "flockleader geerin")
			|| named(monster, "flight kilisa") || named(monster, "aviansie")
			|| named(monster, "reanimated aviansie") || named(monster, "dawn");
	}

	private static boolean named(Monster monster, String target)
	{
		String name = monster.getName().toLowerCase(Locale.ROOT).replace('\u2019', '\'');
		return name.equals(target) || name.startsWith(target + " (");
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

	private static int baseRange(GearItem weapon)
	{
		String name = weapon.getName().toLowerCase(Locale.ROOT).replace("corrupted ", "");
		switch (weapon.getSubcategory())
		{
			case "powered staff":
				if (name.startsWith("tumeken's shadow"))
				{
					return 10;
				}
				if (name.startsWith("eye of ayak"))
				{
					return 6;
				}
				if (name.equals("dawnbringer") || name.startsWith("trident of") || name.startsWith("sanguinesti staff")
					|| name.startsWith("holy sanguinesti staff") || name.startsWith("warped sceptre")
					|| name.startsWith("bone staff") || name.startsWith("thammaron's sceptre")
					|| name.startsWith("accursed sceptre"))
				{
					return 7;
				}
				return 0;
			case "bow":
				if (name.contains("longbow") || name.contains(" comp bow") || name.startsWith("dark bow")
					|| name.startsWith("twisted bow") || name.startsWith("crystal bow")
					|| name.startsWith("bow of faerdhinen") || name.equals("ogre bow"))
				{
					return 10;
				}
				if (name.startsWith("3rd age bow") || name.startsWith("craw's bow") || name.startsWith("webweaver bow"))
				{
					return 9;
				}
				if (name.startsWith("venator bow") || name.startsWith("eclipse atlatl"))
				{
					return 6;
				}
				if (name.contains("shortbow") || name.equals("seercull") || name.startsWith("scorching bow"))
				{
					return 7;
				}
				if (name.equals("comp ogre bow"))
				{
					return 5;
				}
				return 0;
			case "crossbow":
				if (name.equals("crossbow"))
				{
					return 10;
				}
				if (name.startsWith("phoenix crossbow"))
				{
					return 5;
				}
				if (name.startsWith("dorgeshuun crossbow"))
				{
					return 6;
				}
				if (name.startsWith("karil's crossbow") || name.startsWith("armadyl crossbow")
					|| name.startsWith("zaryte crossbow") || name.startsWith("hunters'"))
				{
					return 8;
				}
				if (name.equals("light ballista") || name.equals("heavy ballista"))
				{
					return 9;
				}
				if (name.startsWith("dragon hunter crossbow"))
				{
					return 7;
				}
				if (name.matches("(bronze|blurite|iron|steel|mithril|adamant|rune|dragon) crossbow.*"))
				{
					return 7;
				}
				return 0;
			case "chinchompa":
				return 9;
			case "thrown":
				if (name.startsWith("toxic blowpipe") || name.startsWith("camphor blowpipe")
					|| name.startsWith("ironwood blowpipe") || name.startsWith("rosewood blowpipe"))
				{
					return 5;
				}
				if (name.startsWith("tonalztics of ralos"))
				{
					return weapon.getId() == 28919 || name.contains("uncharged") ? 6 : 7;
				}
				if (name.contains(" dart"))
				{
					return 3;
				}
				if (name.contains(" knife") || name.contains("thrownaxe") || name.contains("throwing axe"))
				{
					return 4;
				}
				return 0;
			default:
				return 0;
		}
	}
}
