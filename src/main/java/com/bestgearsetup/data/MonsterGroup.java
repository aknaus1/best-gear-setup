package com.bestgearsetup.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.Getter;

/**
 * The bundled variants of one monster ("abyssal sire (phase 1)", "abyssal sire (phase 2)"...), searched as
 * a single entry. Variants share the name left after removing their trailing parenthesised versions.
 */
public final class MonsterGroup
{
	/** Lower-case base name shared by every variant. */
	@Getter
	private final String name;
	/** Variants in natural version order ("delve 2" before "delve 10"). */
	@Getter
	private final List<MonsterSummary> variants;
	/** The variant selected when the group is picked: the first listed default version, a boss when possible. */
	@Getter
	private final MonsterSummary primary;

	MonsterGroup(String name, List<MonsterSummary> bundleOrder)
	{
		this.name = name;
		// The bundle lists Wiki default versions first, so the first boss (or first entry) is a default version.
		this.primary = bundleOrder.stream().filter(MonsterSummary::isBoss).findFirst().orElse(bundleOrder.get(0));
		List<MonsterSummary> sorted = new ArrayList<>(bundleOrder);
		sorted.sort(Comparator.comparing(this::sortKey, MonsterGroup::compareNatural));
		this.variants = Collections.unmodifiableList(sorted);
	}

	/** A group of one, for a target that is not in the bundled list. */
	static MonsterGroup single(MonsterSummary monster)
	{
		return new MonsterGroup(baseName(monster.getName()), Collections.singletonList(monster));
	}

	public String getDisplayName()
	{
		return GameData.titleCase(name);
	}

	public boolean isBoss()
	{
		return variants.stream().anyMatch(MonsterSummary::isBoss);
	}

	public boolean contains(MonsterSummary monster)
	{
		return variants.contains(monster);
	}

	public int getMinCombatLevel()
	{
		return variants.stream().mapToInt(MonsterSummary::getCombatLevel).min().orElse(0);
	}

	public int getMaxCombatLevel()
	{
		return variants.stream().mapToInt(MonsterSummary::getCombatLevel).max().orElse(0);
	}

	/** "Phase 3 (stage 1)", "Delve 1, NPC 14708"; "Standard" for the variant without a version. */
	public String versionLabel(MonsterSummary variant)
	{
		String label = sortKey(variant);
		return label.isEmpty() ? "Standard" : label;
	}

	private String sortKey(MonsterSummary variant)
	{
		String full = variant.getName();
		String rest = full.startsWith(name) ? full.substring(name.length()).trim() : full;
		List<String> parts = new ArrayList<>();
		for (String part : versions(rest))
		{
			parts.add(part.startsWith("npc ") ? "NPC" + part.substring(3)
				: Character.toUpperCase(part.charAt(0)) + part.substring(1));
		}
		return String.join(", ", parts);
	}

	/**
	 * The name without its trailing parenthesised versions: "doom of mokhaiotl (delve 1) (npc 14708)" and
	 * "abyssal sire (phase 3 (stage 1))" become "doom of mokhaiotl" and "abyssal sire".
	 */
	static String baseName(String name)
	{
		String base = name.trim();
		while (base.endsWith(")"))
		{
			int open = matchingOpen(base);
			if (open <= 0)
			{
				break;
			}
			base = base.substring(0, open).trim();
		}
		return base.isEmpty() ? name.trim() : base;
	}

	/** Top-level parenthesised groups, without their outer parentheses. */
	private static List<String> versions(String suffix)
	{
		List<String> out = new ArrayList<>();
		int depth = 0;
		int start = -1;
		for (int i = 0; i < suffix.length(); i++)
		{
			char c = suffix.charAt(i);
			if (c == '(' && depth++ == 0)
			{
				start = i + 1;
			}
			else if (c == ')' && depth > 0 && --depth == 0 && i > start)
			{
				out.add(suffix.substring(start, i).trim());
			}
		}
		out.removeIf(String::isEmpty);
		return out;
	}

	private static int matchingOpen(String s)
	{
		int depth = 0;
		for (int i = s.length() - 1; i >= 0; i--)
		{
			char c = s.charAt(i);
			if (c == ')')
			{
				depth++;
			}
			else if (c == '(' && --depth == 0)
			{
				return i;
			}
		}
		return -1;
	}

	/** Case-insensitive order that compares digit runs by value. */
	static int compareNatural(String a, String b)
	{
		String x = a.toLowerCase(Locale.ROOT);
		String y = b.toLowerCase(Locale.ROOT);
		int i = 0;
		int j = 0;
		while (i < x.length() && j < y.length())
		{
			char cx = x.charAt(i);
			char cy = y.charAt(j);
			if (Character.isDigit(cx) && Character.isDigit(cy))
			{
				int ei = i;
				int ej = j;
				while (ei < x.length() && Character.isDigit(x.charAt(ei)))
				{
					ei++;
				}
				while (ej < y.length() && Character.isDigit(y.charAt(ej)))
				{
					ej++;
				}
				String nx = x.substring(i, ei).replaceFirst("^0+(?=.)", "");
				String ny = y.substring(j, ej).replaceFirst("^0+(?=.)", "");
				int cmp = nx.length() != ny.length() ? Integer.compare(nx.length(), ny.length()) : nx.compareTo(ny);
				if (cmp != 0)
				{
					return cmp;
				}
				i = ei;
				j = ej;
			}
			else
			{
				if (cx != cy)
				{
					return Character.compare(cx, cy);
				}
				i++;
				j++;
			}
		}
		return Integer.compare(x.length() - i, y.length() - j);
	}

	@Override
	public String toString()
	{
		return getDisplayName();
	}
}
