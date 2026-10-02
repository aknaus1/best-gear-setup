package com.bestgearsetup.calc;

import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A weapon's damaging special attack. Formulas follow the OSRS Wiki calculator (PlayerVsNPCCalc,
 * dists/claws and dists/bolts at the audited revision); specials it does not implement follow each
 * weapon's Wiki page.
 * Specs without a damage effect return null.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class SpecialAttack
{
	/** How the spec's hits are built from the accuracy and min/max roll. */
	public enum Kind
	{
		/** One ordinary roll. */
		SINGLE,
		/** Several independent ordinary rolls. */
		MULTI,
		/** Second hit always lands when the first does. */
		ABYSSAL_DAGGER,
		DRAGON_CLAWS,
		BURNING_CLAWS,
		/** Four accuracy rolls; successes raise a single hit's range. */
		CRIMSON_KISTEN,
		/** Second hit on larger targets at three-quarters attack roll. */
		HALBERD,
		/** Extra 1-16 magic hit when the melee hit lands. */
		SARADOMIN_SWORD,
		/** +5 damage, including on misses. */
		GRANITE_HAMMER,
		/** One hit of 70% of the maximum. */
		SUNSPEAR,
		/** Two arrows with minimum damage on misses, each capped at 48. */
		DARK_BOW,
		/** Built-in ammunition-only max hit. */
		ARROW_FORMULA,
		/** Second hit against the Defence lowered by the first. */
		TONALZTICS,
		/** Two conditional hits with a raised minimum, and an attack-speed proc. */
		BLOOD_MOON,
		/** Damage range fixed at 75-150. */
		DAWNBRINGER,
		/** 50-150% of the maximum, always accurate, mitigated as Magic. */
		VOIDWAKER,
		/** Four hits at 40% of the maximum. */
		WEBWEAVER,
		/** Minimum and maximum raised by 6% per stack. */
		SOULREAPER,
		/** Fang's minimum without its reduced maximum; accuracy still rolls twice. */
		FANG,
	}

	private final String name;
	private final int energy;
	private final Kind kind;
	private final int hits;
	private final int accuracyNumerator;
	private final int accuracyDenominator;
	/** Maximum-hit multipliers applied in order, each truncated: numerator, denominator pairs. */
	private final int[] damage;
	/** Defence rolled against: stab, slash, crush, ranged or magic; null keeps the active style. */
	private final String defence;
	/** Always accurate. */
	private final boolean guaranteed;
	/** Minimum hit as a percentage of the unscaled maximum (Statius's warhammer: 25-125%). */
	private final int minimumPercent;
	/** Ticks the special adds before the next attack (rune claws). */
	private final int extraTicks;

	private static SpecialAttack spec(String name, int energy, Kind kind, int hits, int accNum, int accDen,
		String defence, int... damage)
	{
		return new SpecialAttack(name, energy, kind, hits, accNum, accDen, damage, defence, false, 0, 0);
	}

	private SpecialAttack withMinimum(int percent)
	{
		return new SpecialAttack(name, energy, kind, hits, accuracyNumerator, accuracyDenominator, damage,
			defence, guaranteed, percent, extraTicks);
	}

	private SpecialAttack withDelay(int ticks)
	{
		return new SpecialAttack(name, energy, kind, hits, accuracyNumerator, accuracyDenominator, damage,
			defence, guaranteed, minimumPercent, ticks);
	}

	/** The minimum hit for a maximum before this special's damage multipliers. */
	long minimum(long unscaledMax)
	{
		return unscaledMax * minimumPercent / 100;
	}

	private static SpecialAttack single(String name, int energy, int accNum, int accDen, String defence, int... damage)
	{
		return spec(name, energy, Kind.SINGLE, 1, accNum, accDen, defence, damage);
	}

	private SpecialAttack alwaysHits()
	{
		return new SpecialAttack(name, energy, kind, hits, accuracyNumerator, accuracyDenominator, damage,
			defence, true, minimumPercent, extraTicks);
	}

	/** Apply the maximum-hit multipliers in order. */
	long scaleMax(long max)
	{
		for (int i = 0; i + 1 < damage.length; i += 2)
		{
			max = max * damage[i] / damage[i + 1];
		}
		return max;
	}

	long scaleAccuracy(long roll)
	{
		return roll * accuracyNumerator / accuracyDenominator;
	}

	/** Spec for the loadout's weapon and attack class, or null when it has no damaging special attack. */
	public static SpecialAttack of(Loadout l, CombatContext ctx)
	{
		GearItem weapon = l.getWeapon();
		if (weapon == null || l.getStyle() == null)
		{
			return null;
		}
		String n = EncounterDamage.lower(weapon);
		AttackStyle.Type type = l.getStyle().getType();
		boolean melee = l.getStyle().isMelee();
		boolean ranged = type == AttackStyle.Type.RANGED;
		if (ranged && n.startsWith("scorching bow") && !ctx.getMonster().hasAttribute("demon"))
		{
			// Scorching shackles fail against non-demons.
			return null;
		}
		return melee ? melee(n, l, ctx) : ranged ? ranged(n, weapon) : magic(n);
	}

	private static SpecialAttack melee(String n, Loadout l, CombatContext ctx)
	{
		if (n.startsWith("abyssal dagger"))
		{
			return spec("Abyssal dagger", 25, Kind.ABYSSAL_DAGGER, 2, 5, 4, "slash", 17, 20);
		}
		if (n.startsWith("dragon dagger"))
		{
			return spec("Dragon dagger", 25, Kind.MULTI, 2, 23, 20, "slash", 23, 20);
		}
		if (n.startsWith("dragon longsword"))
		{
			return single("Dragon longsword", 25, 1, 1, "slash", 5, 4);
		}
		if (n.startsWith("dragon mace"))
		{
			return single("Dragon mace", 25, 5, 4, "crush", 3, 2);
		}
		if (n.startsWith("osmumten's fang"))
		{
			// The ordinary 15% minimum remains but the maximum is not reduced; accuracy still rolls twice.
			return spec("Osmumten's fang", 25, Kind.FANG, 1, 3, 2, null);
		}
		if (n.equals("dual macuahuitl") && EquipmentEffects.bloodMoon(l))
		{
			return spec("Dual macuahuitl", 25, Kind.BLOOD_MOON, 2, 1, 1, null, 1, 1);
		}
		if (n.startsWith("dragon halberd") || n.startsWith("crystal halberd"))
		{
			return spec(n.startsWith("dragon") ? "Dragon halberd" : "Crystal halberd", 30, Kind.HALBERD, 2, 1, 1,
				"slash", 11, 10);
		}
		if (n.startsWith("burning claws") || n.startsWith("bone claws"))
		{
			return spec("Burning claws", 35, Kind.BURNING_CLAWS, 3, 1, 1, "slash", 1, 1);
		}
		if (n.startsWith("arkan blade"))
		{
			return single("Arkan blade", 30, 3, 2, "slash", 3, 2);
		}
		if (n.startsWith("dragon sword"))
		{
			return single("Dragon sword", 40, 5, 4, "stab", 5, 4);
		}
		if (n.startsWith("elder maul"))
		{
			return single("Elder maul", 50, 5, 4, null, 1, 1);
		}
		if (n.startsWith("dragon warhammer"))
		{
			return single("Dragon warhammer", 50, 1, 1, null, 3, 2);
		}
		if (n.startsWith("bandos godsword"))
		{
			return single("Bandos godsword", 50, 2, 1, "slash", 11, 10, 11, 10);
		}
		if (n.startsWith("armadyl godsword"))
		{
			return single("Armadyl godsword", 50, 2, 1, "slash", 11, 10, 5, 4);
		}
		if (n.startsWith("saradomin godsword") || n.startsWith("zamorak godsword") || n.startsWith("ancient godsword"))
		{
			// Ancient godsword's delayed blood sacrifice damage is not included, as in the reference.
			return single(n.startsWith("saradomin") ? "Saradomin godsword" : n.startsWith("zamorak")
				? "Zamorak godsword" : "Ancient godsword", 50, 2, 1, "slash", 11, 10);
		}
		if (n.startsWith("arclight") || n.startsWith("emberlight"))
		{
			return single(n.startsWith("arclight") ? "Arclight" : "Emberlight", 50, 1, 1, "stab", 1, 1);
		}
		if (n.startsWith("dragon claws"))
		{
			return spec("Dragon claws", 50, Kind.DRAGON_CLAWS, 4, 1, 1, "slash", 1, 1);
		}
		if (n.startsWith("voidwaker"))
		{
			// 50-150% of the maximum; the hit is magical and always accurate.
			return spec("Voidwaker", 50, Kind.VOIDWAKER, 1, 1, 1, "magic").alwaysHits();
		}
		if (n.startsWith("abyssal bludgeon"))
		{
			// Damage rises with missing Prayer points, which are not tracked: full Prayer is assumed.
			return single("Abyssal bludgeon", 50, 1, 1, null, 1, 1);
		}
		if (n.startsWith("abyssal whip"))
		{
			return single("Abyssal whip", 50, 5, 4, null, 1, 1);
		}
		if (n.startsWith("barrelchest anchor"))
		{
			return single("Barrelchest anchor", 50, 2, 1, null, 110, 100);
		}
		if (n.startsWith("crimson kisten"))
		{
			return spec("Crimson kisten", 50, Kind.CRIMSON_KISTEN, 1, 1, 1, "crush", 1, 1);
		}
		if (n.equals("sunspear"))
		{
			return spec("Sunspear", 50, Kind.SUNSPEAR, 1, 1, 1, null, 7, 10);
		}
		if (n.startsWith("dragon scimitar"))
		{
			return single("Dragon scimitar", 55, 5, 4, "slash", 1, 1);
		}
		if (n.startsWith("granite hammer"))
		{
			return spec("Granite hammer", 60, Kind.GRANITE_HAMMER, 1, 3, 2, null, 1, 1);
		}
		if (n.startsWith("saradomin's blessed sword") || n.startsWith("sara's blessed sword"))
		{
			return single("Saradomin's blessed sword", 65, 1, 1, "magic", 5, 4);
		}
		if (n.startsWith("saradomin sword"))
		{
			return spec("Saradomin sword", 100, Kind.SARADOMIN_SWORD, 2, 1, 1, "slash", 11, 10);
		}
		if (n.startsWith("abyssal tentacle"))
		{
			// Binding Tentacle: an ordinary hit that also binds and may poison (not scored).
			return single("Abyssal tentacle", 50, 1, 1, null, 1, 1);
		}
		if (n.startsWith("ursine chainmace"))
		{
			// Bear Down: doubled accuracy against slash; the 20-damage bleed on a hit is not scored.
			return single("Ursine chainmace", 50, 2, 1, "slash", 1, 1);
		}
		if (n.startsWith("rune claws"))
		{
			// Impale: +10% damage, and the next attack comes one tick later.
			return single("Rune claws", 25, 1, 1, null, 11, 10).withDelay(1);
		}
		if (n.startsWith("dinh's bulwark") || n.startsWith("dinh's blazing bulwark"))
		{
			// Shield Bash: +20% accuracy against crush; extra targets in multicombat are not scored.
			return single("Dinh's bulwark", 50, 6, 5, "crush", 1, 1);
		}
		if (n.startsWith("statius's warhammer"))
		{
			// Smash: 25-125% of the maximum; the Defence drain on a hit is not scored.
			return single("Statius's warhammer", 35, 1, 1, null, 125, 100).withMinimum(25);
		}
		if (n.startsWith("vesta's longsword"))
		{
			// Feint: 20-120% of the maximum. Its accuracy against a quarter of the target's Defence is not modelled.
			return single("Vesta's longsword", 25, 1, 1, "stab", 120, 100).withMinimum(20);
		}
		if (n.startsWith("soulreaper axe"))
		{
			int stacks = Math.max(0, Math.min(5, ctx.getModifiers().getSoulreaperStacks()));
			// Uses the current stacks; with none it is not a damaging spec.
			return stacks == 0 ? null : spec("Soulreaper axe", 0, Kind.SOULREAPER, 1, 100 + 12 * stacks, 100,
				null);
		}
		return null;
	}

	private static SpecialAttack ranged(String n, GearItem weapon)
	{
		if (n.startsWith("armadyl crossbow"))
		{
			// Armadyl Eye doubles accuracy; its doubled enchanted-bolt chance is not modelled.
			return single("Armadyl crossbow", 50, 2, 1, null, 1, 1);
		}
		if (n.startsWith("morrigan's throwing axe"))
		{
			// Hamstring: 150% accuracy and 50-150% of the maximum.
			return single("Morrigan's throwing axe", 50, 3, 2, null, 150, 100).withMinimum(50);
		}
		if (n.startsWith("morrigan's javelin"))
		{
			// Phantom Strike: 150% accuracy; its bleed affects players only.
			return single("Morrigan's javelin", 50, 3, 2, null, 1, 1);
		}
		if (n.startsWith("zaryte crossbow"))
		{
			return single("Zaryte crossbow", 75, 2, 1, null, 1, 1);
		}
		if (n.startsWith("webweaver bow"))
		{
			return spec("Webweaver bow", 50, Kind.WEBWEAVER, 4, 2, 1, null);
		}
		if (n.startsWith("toxic blowpipe") || n.startsWith("blazing blowpipe"))
		{
			return single(n.startsWith("toxic") ? "Toxic blowpipe" : "Blazing blowpipe", 50, 2, 1, null, 3, 2);
		}
		if (n.startsWith("magic shortbow"))
		{
			return spec("Magic shortbow", n.contains("(i)") ? 50 : 55, Kind.ARROW_FORMULA, 2, 10, 7, null);
		}
		if (n.startsWith("magic longbow") || n.startsWith("magic comp bow") || n.equals("seercull"))
		{
			return spec(n.equals("seercull") ? "Seercull" : n.startsWith("magic comp") ? "Magic comp bow"
				: "Magic longbow", n.equals("seercull") ? 100 : 35, Kind.ARROW_FORMULA, 1, 1, 1, null).alwaysHits();
		}
		if (n.startsWith("heavy ballista") || n.startsWith("light ballista"))
		{
			return single(n.startsWith("heavy") ? "Heavy ballista" : "Light ballista", 65, 5, 4, null, 5, 4);
		}
		if (n.startsWith("dark bow"))
		{
			return spec("Dark bow", 55, Kind.DARK_BOW, 2, 1, 1, null, 1, 1);
		}
		if (n.startsWith("dragon knife") || n.startsWith("rosewood blowpipe"))
		{
			return spec(n.startsWith("dragon") ? "Dragon knife" : "Rosewood blowpipe", 25, Kind.MULTI, 2, 1, 1,
				null);
		}
		if (n.startsWith("scorching bow"))
		{
			return single("Scorching bow", 25, 1, 1, null, 1, 1);
		}
		if (n.startsWith("tonalztics of ralos"))
		{
			boolean charged = !n.contains("uncharged");
			return spec("Tonalztics of Ralos", 50, charged ? Kind.TONALZTICS : Kind.SINGLE, charged ? 2 : 1, 3, 2,
				null);
		}
		return null;
	}

	private static SpecialAttack magic(String n)
	{
		if (n.startsWith("accursed sceptre"))
		{
			return single("Accursed sceptre", 50, 3, 2, null, 3, 2);
		}
		if (n.startsWith("volatile nightmare staff"))
		{
			return single("Volatile nightmare staff", 55, 3, 2, null, 1, 1);
		}
		if (n.startsWith("eldritch nightmare staff"))
		{
			return single("Eldritch nightmare staff", 55, 1, 1, null, 1, 1);
		}
		if (n.startsWith("eye of ayak"))
		{
			return single("Eye of Ayak", 50, 2, 1, null, 13, 10);
		}
		if (n.equals("dawnbringer"))
		{
			return spec("Dawnbringer", 30, Kind.DAWNBRINGER, 1, 1, 1, null, 1, 1).alwaysHits();
		}
		return null;
	}

	/** Spec energy needed per attack; zero for Soulreaper, which spends stacks instead. */
	public String describe()
	{
		return name + " special" + (energy > 0 ? " (" + energy + "% energy)" : " (spends stacks)");
	}
}
