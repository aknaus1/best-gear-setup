package com.bestgearsetup.calc;

import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.NameLists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A weapon's damaging special attack. Formulas follow the OSRS Wiki calculator (PlayerVsNPCCalc,
 * dists/claws and dists/bolts at the audited revision); specials it does not implement follow each
 * weapon's Wiki page. The weapons and their numbers are bundled in special-attacks.json.
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

	/** Stat drain a landed special leaves on the target, matching the pre-fight drain options. */
	public enum Drain
	{
		NONE,
		/** -30% of current Defence (also Statius's warhammer). */
		DRAGON_WARHAMMER,
		/** -35% of current Defence. */
		ELDER_MAUL,
		ARCLIGHT,
		EMBERLIGHT,
		/** Defence lowered by an eighth of the target's Magic level; counted once per landed special. */
		TONALZTICS,
		/** Drains by the damage dealt: Defence, then Strength, Attack and Magic. */
		BANDOS_GODSWORD,
		/** Drains Magic by the damage dealt. */
		SEERCULL,
		/** Drains magic defence by the damage dealt. */
		EYE_OF_AYAK,
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
	private final Drain drain;

	private static final JsonObject TABLE = NameLists.load("/com/bestgearsetup/special-attacks.json");
	private static final List<Entry> MELEE = entries(TABLE.getAsJsonArray("melee"));
	private static final List<Entry> RANGED = entries(TABLE.getAsJsonArray("ranged"));
	private static final List<Entry> MAGIC = entries(TABLE.getAsJsonArray("magic"));

	private static SpecialAttack spec(String name, int energy, Kind kind, int hits, int accNum, int accDen,
		String defence, int... damage)
	{
		return new SpecialAttack(name, energy, kind, hits, accNum, accDen, damage, defence, false, 0, 0, Drain.NONE);
	}

	/** The minimum hit for a maximum before this special's damage multipliers. */
	long minimum(long unscaledMax)
	{
		return unscaledMax * minimumPercent / 100;
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
		if (melee && n.startsWith("soulreaper axe"))
		{
			int stacks = Math.max(0, Math.min(5, ctx.getModifiers().getSoulreaperStacks()));
			// Uses the current stacks; with none it is not a damaging spec.
			return stacks == 0 ? null : spec("Soulreaper axe", 0, Kind.SOULREAPER, 1, 100 + 12 * stacks, 100,
				null);
		}
		for (Entry entry : melee ? MELEE : ranged ? RANGED : MAGIC)
		{
			if (entry.match.matches(n) && (!entry.bloodMoon || EquipmentEffects.bloodMoon(l)))
			{
				return entry.spec;
			}
		}
		return null;
	}

	/** One special-attacks.json row: the weapons it applies to and their special. */
	@AllArgsConstructor
	private static final class Entry
	{
		final NameLists.NameList match;
		final boolean bloodMoon;
		final SpecialAttack spec;
	}

	private static List<Entry> entries(JsonArray rows)
	{
		List<Entry> entries = new ArrayList<>();
		for (JsonElement element : rows)
		{
			JsonObject row = element.getAsJsonObject();
			int[] accuracy = ints(row, "accuracy", 1, 1);
			SpecialAttack spec = new SpecialAttack(row.get("name").getAsString(), row.get("energy").getAsInt(),
				Kind.valueOf(string(row, "kind", "SINGLE")), integer(row, "hits", 1), accuracy[0], accuracy[1],
				ints(row, "damage"), string(row, "defence", null), flag(row, "alwaysHits"), integer(row, "minimum", 0),
				integer(row, "delay", 0), Drain.valueOf(string(row, "drain", "NONE")));
			entries.add(new Entry(NameLists.NameList.of(row.get("match")), flag(row, "bloodMoon"), spec));
		}
		return Collections.unmodifiableList(entries);
	}

	private static String string(JsonObject row, String key, String fallback)
	{
		return row.has(key) ? row.get(key).getAsString() : fallback;
	}

	private static int integer(JsonObject row, String key, int fallback)
	{
		return row.has(key) ? row.get(key).getAsInt() : fallback;
	}

	private static boolean flag(JsonObject row, String key)
	{
		return row.has(key) && row.get(key).getAsBoolean();
	}

	private static int[] ints(JsonObject row, String key, int... fallback)
	{
		if (!row.has(key))
		{
			return fallback;
		}
		JsonArray values = row.getAsJsonArray(key);
		int[] out = new int[values.size()];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = values.get(i).getAsInt();
		}
		return out;
	}

	/** Spec energy needed per attack; zero for Soulreaper, which spends stacks instead. */
	public String describe()
	{
		return name + " special" + (energy > 0 ? " (" + energy + "% energy)" : " (spends stacks)");
	}
}
