package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-hit encounter mitigation, applied after player damage bonuses and before thralls. */
final class EncounterDamage
{
	private static final Rule NONE = new Rule(1, 1, false, -1, 0, false, null);
	private static final Rule THIRD = new Rule(1, 3, false, -1, 0, false, "Damage / 3, rounded per hit");
	private static final Rule HALF = new Rule(1, 2, false, -1, 0, false, "Damage / 2, rounded per hit");
	private static final Rule QUARTER = new Rule(1, 4, false, -1, 0, false, "Damage / 4, rounded per hit");
	private static final Rule KRAKEN = new Rule(1, 7, true, -1, 0, false, "Ranged damage / 7; positive hits stay at least 1");
	private static final Rule TEKTON = new Rule(1, 5, true, -1, 0, false, "Magic damage / 5; positive hits stay at least 1");
	private static final Rule TD_SHIELD = new Rule(4, 5, true, -1, 0, false, "Fire shield: damage reduced by 20%");
	private static final Rule ZULRAH = new Rule(1, 1, false, 50, 45, true, "Hits above 50 rerolled to 45-50");
	private static final Rule SEREN = new Rule(1, 1, false, 2, 22, false, "Each hit limited by a random 22-24 cap");
	private static final Rule VERZIK_MELEE = new Rule(1, 1, false, 10, 0, false, "P1: each hit limited by a random 0-10 cap");
	private static final Rule VERZIK_OTHER = new Rule(1, 1, false, 3, 0, false, "P1: each hit limited by a random 0-3 cap");
	private static final Rule SILVER_CAP = new Rule(1, 1, false, 0, 10, false, "Silver weapon damage capped at 10");
	private static final Rule TOTEM = new Rule(2, 1, false, -1, 0, false, "Nightmare totem: double Magic damage");
	private static final Rule SIRE_TRANSITION = new Rule(1, 2, false, -1, 0, false, "Transition: damage / 2 per hit");
	private static final Rule HUEY_OTHER = new Rule(1, 1, false, 4, 0, false, "Tail: random 0-4 damage cap");
	private static final Rule HUEY_EARTH = new Rule(1, 1, false, 9, 0, false, "Tail: random 0-9 earth spell cap");
	private static final Rule HUEY_CRUSH = new Rule(1, 1, false, 9, 0, false,
		"Tail: random 0-9 crush cap, minimum 1 including misses", 0, true);

	private EncounterDamage()
	{
	}

	static CombatClass olmPreferredClass(Monster monster)
	{
		if (AttackReach.isOlmHead(monster))
		{
			return CombatClass.RANGED;
		}
		int id = monster.getId();
		String name = monster.getLowerName();
		boolean olm = name.startsWith("great olm");
		if (id == 7550 || id == 7553 || olm && name.contains("mage hand"))
		{
			return CombatClass.MAGIC;
		}
		if (id == 7552 || id == 7555 || olm && name.contains("melee hand"))
		{
			return CombatClass.MELEE;
		}
		return null;
	}

	static int divisor(Monster monster, CombatClass combatClass)
	{
		CombatClass preferred = olmPreferredClass(monster);
		if (preferred != null && preferred != combatClass)
		{
			return 3;
		}
		return combatClass == CombatClass.RANGED && AttackReach.isKraken(monster) ? 7 : 1;
	}

	static boolean named(Monster monster, String target)
	{
		return versionOf(monster.getLowerName(), target);
	}

	/** The name equals the base or is the base followed by a parenthesised version, without building strings. */
	static boolean versionOf(String name, String base)
	{
		return name.startsWith(base) && (name.length() == base.length() || name.startsWith(" (", base.length()));
	}

	static boolean tekton(Monster monster)
	{
		int id = monster.getId();
		return id == 7540 || id >= 7543 && id <= 7545 || named(monster, "tekton");
	}

	static boolean crystal(Monster monster)
	{
		return monster.getId() == 7568 || named(monster, "glowing crystal");
	}

	static boolean guardian(Monster monster)
	{
		return monster.getId() >= 7569 && monster.getId() <= 7572;
	}

	static boolean verzikP1(Monster monster)
	{
		int id = monster.getId();
		return id >= 8369 && id <= 8371 || id >= 10830 && id <= 10832 || id >= 10847 && id <= 10849
			|| named(monster, "verzik vitur") && monster.getLowerName().contains("(phase 1)");
	}

	static boolean tormented(Monster monster)
	{
		return monster.getId() >= 13599 && monster.getId() <= 13606 || named(monster, "tormented demon");
	}

	/** An "(unshielded)" variant, or the Boss phase option on the spawn variants the Wiki lists. */
	static boolean unshielded(Monster monster)
	{
		return tormented(monster) && (monster.getLowerName().contains("(unshielded)")
			|| CombatRules.phase(monster, EncounterPhase.TD_UNSHIELDED));
	}

	static boolean demonbane(Loadout loadout)
	{
		String weapon = lower(loadout.getWeapon());
		if (loadout.getStyle().getType() == AttackStyle.Type.MAGIC)
		{
			return spell(loadout).contains("demonbane");
		}
		if (loadout.getStyle().getType() == AttackStyle.Type.RANGED)
		{
			return weapon.startsWith("scorching bow");
		}
		return weapon.startsWith("silverlight") || weapon.startsWith("darklight") || weapon.startsWith("arclight")
			|| weapon.startsWith("emberlight") || weapon.startsWith("bone claws") || weapon.startsWith("burning claws");
	}

	static boolean abyssal(Loadout loadout)
	{
		String weapon = lower(loadout.getWeapon());
		return loadout.getStyle().isMelee() && (weapon.startsWith("abyssal whip") || weapon.startsWith("abyssal tentacle")
			|| weapon.startsWith("abyssal dagger") || weapon.startsWith("abyssal bludgeon"));
	}

	static String lower(GearItem item)
	{
		if (item == null)
		{
			return "";
		}
		String name = item.getLowerCombatName();
		return item.isDmmEquipment() && name.startsWith("corrupted ") ? name.substring(10) : name;
	}

	static String spell(Loadout loadout)
	{
		return loadout.getSpell() == null ? "" : loadout.getSpell().getName().toLowerCase(Locale.ROOT);
	}

	static Rule rule(Monster monster, Loadout loadout)
	{
		return rule(monster, loadout, WeaponRules.classOf(loadout.getStyle()));
	}

	/** Mitigation for a hit of the given class, such as Voidwaker's magical special attack. */
	static Rule rule(Monster monster, Loadout loadout, CombatClass cls)
	{
		Rule base = baseRule(monster, loadout, cls);
		int armour = monster.getFlatArmour() == null ? monster.getId() == 13011 ? -2
			: monster.getId() == 13013 ? -5 : monster.getId() == 13012
			? monster.getLowerName().contains("clone") ? 4 : 6 : CombatRules.eclipseClone(monster)
			? 4 : 0 : monster.getFlatArmour();
		if (cls == CombatClass.MAGIC)
		{
			armour = 0;
		}
		boolean pillar = CombatRules.phase(monster, EncounterPhase.HUEYCOATL_PILLAR);
		if (armour == 0 && !pillar)
		{
			return base;
		}
		int flat = armour;
		// Distinct keys for each armour value with and without the pillar buff.
		return base.derived.computeIfAbsent(flat * 2 + (pillar ? 1 : 0), key ->
		{
			String detail = base.detail;
			if (pillar)
			{
				detail = (detail == null ? "" : detail + "; ") + "Pillar: damage x1.3 per hit";
			}
			if (flat != 0)
			{
				detail = (detail == null ? "" : detail + "; ") + "Flat armour: " + flat + " per hit";
			}
			return new Rule(base.numerator, base.divisor, base.positiveMinimum, base.limit, base.offset,
				base.reroll, detail, flat, base.outputMinimum, pillar ? 13 : 1, pillar ? 10 : 1);
		});
	}

	private static Rule baseRule(Monster monster, Loadout loadout, CombatClass cls)
	{
		String weapon = lower(loadout.getWeapon());
		int id = monster.getId();
		if (id == 14014 || named(monster, "the hueycoatl") && monster.getLowerName().contains("tail"))
		{
			DpsCalculator.Bonuses bonuses = DpsCalculator.Bonuses.of(loadout);
			boolean crush = loadout.getStyle().getType() == AttackStyle.Type.CRUSH
				&& bonuses.crush > bonuses.stab && bonuses.crush > bonuses.slash;
			return crush ? HUEY_CRUSH : SpellRules.element(loadout.getSpell()).equals("earth") ? HUEY_EARTH : HUEY_OTHER;
		}
		if (cls == CombatClass.MAGIC && (id == 9434 || id == 9435 || id == 9437 || id == 9438
			|| id == 9440 || id == 9441 || id == 9443 || id == 9444 || named(monster, "nightmare totem")
			|| named(monster, "phosani's nightmare totem")))
		{
			return TOTEM;
		}
		if (id >= 2042 && id <= 2044 || named(monster, "zulrah"))
		{
			return ZULRAH;
		}
		if (id >= 8917 && id <= 8920 || named(monster, "fragment of seren"))
		{
			return SEREN;
		}
		if (verzikP1(monster) && !weapon.equals("dawnbringer"))
		{
			return cls == CombatClass.MELEE ? VERZIK_MELEE : VERZIK_OTHER;
		}
		if (tekton(monster) && cls == CombatClass.MAGIC)
		{
			return TEKTON;
		}
		if (crystal(monster) && cls == CombatClass.MAGIC)
		{
			return THIRD;
		}
		if ((id == 7584 || id == 7585 || named(monster, "ice demon"))
			&& !(cls == CombatClass.MAGIC && spell(loadout).startsWith("fire ")) && !demonbane(loadout))
		{
			return THIRD;
		}
		if (tormented(monster) && !unshielded(monster) && !demonbane(loadout) && !abyssal(loadout))
		{
			return TD_SHIELD;
		}
		if (CombatRules.phase(monster, EncounterPhase.ABYSSAL_SIRE_TRANSITION))
		{
			return SIRE_TRANSITION;
		}
		if (id == 319 || named(monster, "corporeal beast"))
		{
			boolean stab = loadout.getStyle().getType() == AttackStyle.Type.STAB;
			boolean suitable = cls == CombatClass.MAGIC || stab && (weapon.startsWith("osmumten's fang")
				|| loadout.getWeapon().getSubcategory().equals("polearm")
				|| weapon.contains("spear") && !weapon.startsWith("blue moon spear"));
			return suitable ? NONE : HALF;
		}
		if (named(monster, "slagilith") && !loadout.getWeapon().getSubcategory().equals("pickaxe"))
		{
			return THIRD;
		}
		if (named(monster, "zogre") || named(monster, "skogre") || named(monster, "slash bash"))
		{
			if (spell(loadout).equals("crumble undead"))
			{
				return HALF;
			}
			return cls == CombatClass.RANGED && weapon.equals("comp ogre bow")
				&& lower(loadout.get(Slot.AMMO)).contains(" brutal") ? NONE : QUARTER;
		}
		if (monster.hasAttribute("vampyre (t2)") && !EncounterImmunities.vampyrebane(loadout, false))
		{
			return lower(loadout.get(Slot.RING)).equals("efaritay's aid") ? HALF : SILVER_CAP;
		}
		int divisor = divisor(monster, cls);
		return divisor == 7 ? KRAKEN : divisor == 3 ? THIRD : NONE;
	}

	static Rule unmitigated()
	{
		return NONE;
	}

	static String encounterNote(Monster monster)
	{
		int id = monster.getId();
		if (id >= 2042 && id <= 2044 || named(monster, "zulrah"))
		{
			return "Zulrah: hits above 50 reroll to 45-50; the reroll is included in expected DPS.";
		}
		if (id >= 8917 && id <= 8920 || named(monster, "fragment of seren"))
		{
			return "Seren: each hit is limited by a random 22-24 cap.";
		}
		if (verzikP1(monster))
		{
			return "Verzik P1: random per-hit caps of 0-10 for melee and 0-3 otherwise; Dawnbringer bypasses them.";
		}
		if (tekton(monster))
		{
			return "Tekton: ranged immune; Magic damage divided by 5 with positive hits kept at 1.";
		}
		if (crystal(monster))
		{
			return "Vasa crystal: ranged immune; Magic damage divided by 3.";
		}
		if (id == 7584 || id == 7585 || named(monster, "ice demon"))
		{
			return "Ice Demon: damage divided by 3 except fire spells and demonbane attacks.";
		}
		if (tormented(monster))
		{
			return unshielded(monster) ? "Tormented Demon: unshielded target; no fire-shield reduction."
				: "Tormented Demon: fire shield reduces damage by 20%; demonbane and abyssal attacks bypass it.";
		}
		if (id == 319 || named(monster, "corporeal beast"))
		{
			return "Corp: half damage except Magic and qualifying stab weapons.";
		}
		if (guardian(monster))
		{
			return "CoX guardian: only melee attacks with a pickaxe can deal damage.";
		}
		if (monster.hasAttribute("leafy"))
		{
			return "Requires leaf-bladed melee, broad ammunition, or Magic Dart.";
		}
		if (monster.hasAttribute("vampyre (t2)") || monster.hasAttribute("vampyre (t3)"))
		{
			return "Vampyre weapon requirements apply; tier 2 silver hits cap at 10, or half damage with Efaritay's aid.";
		}
		return null;
	}

	/**
	 * A uniform raw hit is transformed before its mean is calculated: division, then a random cap or
	 * reroll, then a phase multiplier, then flat armour. Prefix sums keep searches fast.
	 */
	static final class Rule
	{
		private final int numerator;
		private final int divisor;
		private final boolean positiveMinimum;
		private final int limit;
		private final int offset;
		private final boolean reroll;
		private final int flatArmour;
		private final boolean outputMinimum;
		private final int postNumerator;
		private final int postDivisor;
		private final Map<Integer, Rule> derived = new ConcurrentHashMap<>();
		final String detail;
		private final double[] sums = new double[513];

		private Rule(int numerator, int divisor, boolean positiveMinimum, int limit, int offset,
			boolean reroll, String detail)
		{
			this(numerator, divisor, positiveMinimum, limit, offset, reroll, detail, 0, false, 1, 1);
		}

		private Rule(int numerator, int divisor, boolean positiveMinimum, int limit, int offset,
			boolean reroll, String detail, int flatArmour, boolean outputMinimum)
		{
			this(numerator, divisor, positiveMinimum, limit, offset, reroll, detail, flatArmour, outputMinimum, 1, 1);
		}

		private Rule(int numerator, int divisor, boolean positiveMinimum, int limit, int offset,
			boolean reroll, String detail, int flatArmour, boolean outputMinimum, int postNumerator, int postDivisor)
		{
			this.numerator = numerator;
			this.divisor = divisor;
			this.positiveMinimum = positiveMinimum;
			this.limit = limit;
			this.offset = offset;
			this.reroll = reroll;
			this.flatArmour = flatArmour;
			this.outputMinimum = outputMinimum;
			this.postNumerator = postNumerator;
			this.postDivisor = postDivisor;
			this.detail = detail;
			for (int hit = 0; hit < sums.length - 1; hit++)
			{
				sums[hit + 1] = sums[hit] + mean(hit);
			}
		}

		private long scaled(long hit)
		{
			return positiveMinimum && hit > 0 ? Math.max(1, hit * numerator / divisor) : hit * numerator / divisor;
		}

		/** Phase multiplier and armour for one capped outcome. Flat armour applies per outcome, not to a mean. */
		private double finish(long hit, boolean armour)
		{
			long value = hit * postNumerator / postDivisor;
			return outputMinimum ? Math.max(1, value) : Math.max(0, value - (armour ? flatArmour : 0));
		}

		private double finish(long hit)
		{
			return finish(hit, true);
		}

		/** Mean for an inaccurate hitsplat that still deals damage: flat armour only reduces accurate hits. */
		double meanInaccurate(long raw)
		{
			if (flatArmour == 0)
			{
				return mean(raw);
			}
			long hit = scaled(raw);
			if (limit < 0 || reroll && hit <= limit)
			{
				return finish(hit, false);
			}
			double sum = 0;
			if (reroll)
			{
				for (long rerolled = offset; rerolled <= limit; rerolled++)
				{
					sum += finish(rerolled, false);
				}
				return sum / (limit - offset + 1.0);
			}
			for (long cap = offset; cap <= offset + limit; cap++)
			{
				sum += finish(Math.min(hit, cap), false);
			}
			return sum / (limit + 1.0);
		}

		double mean(long raw)
		{
			long hit = scaled(raw);
			if (limit < 0 || reroll && hit <= limit)
			{
				return finish(hit);
			}
			if (reroll)
			{
				double sum = 0;
				for (long rerolled = offset; rerolled <= limit; rerolled++)
				{
					sum += finish(rerolled);
				}
				return sum / (limit - offset + 1.0);
			}
			if (!outputMinimum && flatArmour == 0 && postNumerator == postDivisor)
			{
				return cappedMean(hit);
			}
			double sum = 0;
			for (long cap = offset; cap <= offset + limit; cap++)
			{
				sum += finish(Math.min(hit, cap));
			}
			return sum / (limit + 1.0);
		}

		/** Closed form of min(hit, cap) averaged over a uniform cap in [offset, offset + limit]. */
		private double cappedMean(long hit)
		{
			long upper = offset + limit;
			if (hit >= upper)
			{
				return (offset + upper) / 2.0;
			}
			if (hit <= offset)
			{
				return hit;
			}
			long smaller = hit - offset;
			return (smaller * (offset + hit - 1) / 2.0 + (upper - hit + 1) * hit) / (limit + 1.0);
		}

		int maximum(long raw)
		{
			long hit = scaled(raw);
			long capped = limit < 0 ? hit : Math.min(hit, reroll ? limit : offset + limit);
			return (int) finish(capped);
		}

		double missDamage()
		{
			return outputMinimum ? finish(1) : 0;
		}

		double average(long minimum, long maximum)
		{
			if (this == NONE)
			{
				return (minimum + maximum) / 2.0;
			}
			if (minimum >= 0 && maximum < sums.length - 1)
			{
				return (sums[(int) maximum + 1] - sums[(int) minimum]) / (maximum - minimum + 1.0);
			}
			double sum = 0;
			for (long hit = minimum; hit <= maximum; hit++)
			{
				sum += mean(hit);
			}
			return sum / (maximum - minimum + 1.0);
		}
	}

}
