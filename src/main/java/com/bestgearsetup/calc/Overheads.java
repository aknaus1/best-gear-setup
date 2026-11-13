package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.NameLists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * The overhead protection prayer against a monster, looked up by name in {@code encounter-prayers.json}: game-mode
 * versions (Deadman, PvM Arena, Echo) by their own Wiki attack styles, then checked Wiki strategies, then every other
 * monster's Wiki attack styles. A melee setup can have its own entry, as it stands in
 * melee range. Static reference per target; monsters whose attacks can't be read get no advice.
 */
public final class Overheads
{
	/** Rules in priority order; "prefix:" names match a name start, others the name or one of its versions. */
	private static final JsonArray RULES = NameLists.load("/com/bestgearsetup/encounter-prayers.json").getAsJsonArray("rules");

	private Overheads()
	{
	}

	/** Overhead protection prayers with their Prayer level requirements. */
	@Getter
	@RequiredArgsConstructor
	public enum Overhead
	{
		MISSILES("Missiles", 40), MAGIC("Magic", 37), MELEE("Melee", 43);

		private final String shortName;
		private final int level;

		@Override
		public String toString()
		{
			return "Protect from " + shortName;
		}
	}

	/** One recommendation: the prayer to start on (null for none), every prayer the fight switches between, and why. */
	@Value
	public static class Advice
	{
		Overhead prayer;
		/** Every overhead used in the fight in priority order, including {@link #prayer}; empty when none is. */
		List<Overhead> switches;
		String note;

		public boolean isSwitching()
		{
			return switches.size() > 1;
		}

		/** Short form for the result view, such as "Protect from Magic" or "Switch: Magic / Missiles". */
		public String summary()
		{
			if (isSwitching())
				return "Switch: " + switches.stream().map(Overhead::getShortName).collect(Collectors.joining(" / "));
			return prayer != null ? prayer.toString() : "None needed";
		}
	}

	/** The first rule naming this target, with a melee setup's own advice when it has one; null when none does. */
	public static Advice recommend(Monster monster, CombatClass cls)
	{
		String n = monster.getLowerName();
		for (JsonElement element : RULES)
		{
			JsonObject rule = element.getAsJsonObject();
			if (!names(rule, n))
				continue;
			// A rule without a note lists a form that has no advice.
			if (!rule.has("note"))
				return null;
			JsonObject chosen = cls == CombatClass.MELEE && rule.has("melee") ? rule.getAsJsonObject("melee") : rule;
			List<Overhead> prayers = new ArrayList<>();
			if (chosen.has("prayers"))
				chosen.getAsJsonArray("prayers").forEach(p -> prayers.add(Overhead.valueOf(p.getAsString())));
			return new Advice(prayers.isEmpty() ? null : prayers.get(0), Collections.unmodifiableList(prayers),
				chosen.get("note").getAsString());
		}
		return null;
	}

	private static boolean names(JsonObject rule, String name)
	{
		for (JsonElement element : rule.getAsJsonArray("names"))
		{
			String base = element.getAsString();
			if (base.startsWith("prefix:") ? name.startsWith(base.substring(7))
				: name.equals(base) || name.startsWith(base + " ("))
				return true;
		}
		return false;
	}
}
