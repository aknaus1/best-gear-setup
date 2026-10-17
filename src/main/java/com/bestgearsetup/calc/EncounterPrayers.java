package com.bestgearsetup.calc;

import com.bestgearsetup.calc.ProtectionPrayers.Advice;
import com.bestgearsetup.calc.ProtectionPrayers.Overhead;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Encounter mechanics that cannot be inferred from an infobox's attack styles or maximum hit. */
final class EncounterPrayers
{
	private static final JsonArray RULES = load("encounter-prayers.json");

	private EncounterPrayers() { }

	static Advice equipped(Monster monster, SetupResult setup, boolean meleeRange, ProtectionPrayers.Context context)
	{
		String n = monster.getLowerName();
		if (specialMode(n)) { return null; }
		Loadout loadout = setup.getLoadout();
		if (named(n, "king black dragon"))
		{
			GearItem weapon = loadout.getWeapon();
			GearItem shield = weapon == null || weapon.isTwoHanded() ? null : loadout.get(Slot.SHIELD);
			boolean close = meleeRange || context != null && context.getSettings().getTargetDistance() == 0;
			boolean useMelee = close && DragonfireProtection.isFireShield(shield)
				&& (context == null || context.getLevels().getPrayer() >= Overhead.MELEE.getLevel()
					|| context.getLevels().getPrayer() < Overhead.MAGIC.getLevel());
			Overhead prayer = useMelee ? Overhead.MELEE : Overhead.MAGIC;
			String note = useMelee ? "With this fire shield, fight KBD adjacent and keep Melee, even with a crossbow. "
				+ "This reduces the frequency of special breaths. "
				: "Keep Magic with a shieldless / two-handed setup or when fighting away from KBD. "
					+ "A fire shield and adjacent position allow Melee once unlocked. ";
			note += "Use antifire; special breaths can still deal chip damage. Normal breath timing doesn't allow reactive style switches.";
			if (context != null && !DragonfireProtection.allowed(monster, loadout, context.getSettings().toBuilder()
				.requireFireProtection(true).protectMagic(prayer == Overhead.MAGIC).build(), context.getLevels()))
			{
				note += " The selected shield / potion combination is insufficient for the search's fire requirement.";
			}
			return rule("King_Black_Dragon/Strategies", note, prayer);
		}
		if (named(n, "araxxor") && context != null && !meleeRange)
		{
			long magic = IncomingDamage.defenceRoll(loadout, context.getLevels(), Overhead.MAGIC, "magic");
			long ranged = IncomingDamage.defenceRoll(loadout, context.getLevels(), Overhead.MISSILES, "ranged");
			return rule("Araxxor", "Outside melee range Araxxor attacks your weaker ranged / magic defence. At the "
				+ "search's base levels, this armour calls for " + (magic < ranged ? "Magic" : "Missiles")
				+ "; switch to Melee when he approaches. Prefer fighting adjacent with Melee. Dodge acid and cleaves; "
				+ "defensive boosts can change the distant style he chooses.", magic < ranged ? Overhead.MAGIC : Overhead.MISSILES, Overhead.MELEE);
		}
		GearItem boots = loadout.get(Slot.FEET);
		if (named(n, "sarachnis") && meleeRange && boots != null && boots.getLowerCombatName().startsWith("aranea boots"))
		{
			return rule("Sarachnis/Strategies", "Keep Melee while adjacent. Your aranea boots prevent the web, so her "
				+ "usual web-driven ranged prayer swap isn't required if you remain in melee reach. Magic spawns are separate attackers.", Overhead.MELEE);
		}
		return null;
	}

	static Advice recommend(Monster monster, CombatClass setup, boolean meleeRange)
	{
		String n = monster.getLowerName();
		// Special modes have their own attack rules; don't silently apply normal-game advice to them.
		if (specialMode(n))
		{
			if (named(n, "cerberus") || named(n, "kalphite queen") || named(n, "tztok-jad")
				|| named(n, "phantom muspah") || named(n, "sol heredit") || named(n, "kree'arra")
				|| named(n, "general graardor") || named(n, "commander zilyana") || named(n, "k'ril tsutsaroth")
				|| named(n, "king black dragon"))
			{
				return new Advice(null, Collections.emptyList(), "Special-mode attack timing and protection mechanics "
					+ "haven't been verified for this variant. Check its Wiki strategy; normal-game encounter advice "
					+ "must not be assumed.", true);
			}
			return null;
		}
		return match(RULES, n, setup, meleeRange);
	}

	/**
	 * The first rule listing this target ("prefix:" names match a name start), then its first case whose conditions
	 * hold; null when no rule lists the target.
	 */
	static Advice match(JsonArray rules, String n, CombatClass setup, boolean meleeRange)
	{
		for (JsonElement element : rules)
		{
			JsonObject rule = element.getAsJsonObject();
			boolean listed = false;
			for (JsonElement name : rule.getAsJsonArray("names"))
			{
				String base = name.getAsString();
				listed |= base.startsWith("prefix:") ? n.startsWith(base.substring(7)) : named(n, base);
			}
			if (!listed || !holds(rule.get("when"), n, setup, meleeRange)) { continue; }
			for (JsonElement option : rule.getAsJsonArray("cases"))
			{
				JsonObject chosen = option.getAsJsonObject();
				if (!holds(chosen.get("when"), n, setup, meleeRange)) { continue; }
				List<Overhead> prayers = new ArrayList<>();
				if (chosen.has("prayers")) { chosen.getAsJsonArray("prayers").forEach(p -> prayers.add(Overhead.valueOf(p.getAsString()))); }
				return rule(rule.get("page").getAsString(), chosen.get("note").getAsString(), prayers.toArray(new Overhead[0]));
			}
		}
		return null;
	}

	/** Every condition holds: "melee" (in melee range), "has:text" (in the name) or "class:MAGIC", each negated by "!". */
	private static boolean holds(JsonElement conditions, String name, CombatClass setup, boolean meleeRange)
	{
		if (conditions == null) { return true; }
		for (JsonElement element : conditions.getAsJsonArray())
		{
			String condition = element.getAsString();
			boolean negated = condition.startsWith("!");
			String test = negated ? condition.substring(1) : condition;
			boolean holds = test.equals("melee") ? meleeRange : test.startsWith("has:") ? name.contains(test.substring(4))
				: setup != null && setup.name().equals(test.substring(6));
			if (holds == negated) { return false; }
		}
		return true;
	}

	/** Verified strategy rules, in priority order. Parsed without Gson, which isn't available at class load. */
	static JsonArray load(String resource)
	{
		try (Reader reader = new InputStreamReader(EncounterPrayers.class.getResourceAsStream("/com/bestgearsetup/" + resource),
			StandardCharsets.UTF_8))
		{
			return new JsonParser().parse(reader).getAsJsonArray();
		}
		catch (IOException | RuntimeException e)
		{
			throw new IllegalStateException("Cannot load encounter prayer advice", e);
		}
	}

	private static boolean named(String name, String base)
	{
		return name.equals(base) || name.startsWith(base + " (");
	}

	private static boolean specialMode(String name)
	{
		return name.contains("deadman") || name.contains("echo") || name.contains("pvm arena") || name.contains("nightmare zone");
	}

	private static Advice rule(String page, String note, Overhead... prayers)
	{
		return new Advice(prayers.length == 0 ? null : prayers[0],
			Collections.unmodifiableList(Arrays.asList(prayers)), note).withSource("https://oldschool.runescape.wiki/w/" + page);
	}
}
