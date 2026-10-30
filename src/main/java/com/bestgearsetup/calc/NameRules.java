package com.bestgearsetup.calc;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/** Immutable blocked and forced lower-case names, in their stored order. */
public final class NameRules
{
	public static final NameRules NONE = new NameRules(new LinkedHashSet<>(), new LinkedHashSet<>());

	private final Set<String> blocked;
	private final Set<String> forced;

	private NameRules(Set<String> blocked, Set<String> forced)
	{
		this.blocked = Collections.unmodifiableSet(new LinkedHashSet<>(blocked));
		this.forced = Collections.unmodifiableSet(new LinkedHashSet<>(forced));
	}

	/** Normalises both collections; a name that is both blocked and forced stays blocked. */
	public static NameRules of(Collection<String> blocked, Collection<String> forced)
	{
		Set<String> blockedNames = names(blocked);
		Set<String> forcedNames = names(forced);
		forcedNames.removeAll(blockedNames);
		return blockedNames.isEmpty() && forcedNames.isEmpty() ? NONE : new NameRules(blockedNames, forcedNames);
	}

	public static NameRules parse(String blockedCsv, String forcedCsv)
	{
		return of(split(blockedCsv), split(forcedCsv));
	}

	public static String format(Collection<String> names)
	{
		StringJoiner joined = new StringJoiner(",");
		if (names != null)
		{
			for (String name : names)
			{
				joined.add(name);
			}
		}
		return joined.toString();
	}

	public Set<String> getBlocked()
	{
		return blocked;
	}

	public Set<String> getForced()
	{
		return forced;
	}

	public boolean isEmpty()
	{
		return blocked.isEmpty() && forced.isEmpty();
	}

	public SpellRule ruleFor(String name)
	{
		String normalized = normalize(name);
		if (blocked.contains(normalized))
		{
			return SpellRule.BLOCK;
		}
		return forced.contains(normalized) ? SpellRule.FORCE : SpellRule.ALLOW;
	}

	/** Returns these rules with the name set to the rule, or this instance when nothing changes. */
	public NameRules withRule(String name, SpellRule rule)
	{
		String normalized = normalize(name);
		if (normalized.isEmpty())
		{
			return this;
		}
		Set<String> nextBlocked = new LinkedHashSet<>(blocked);
		Set<String> nextForced = new LinkedHashSet<>(forced);
		switch (Objects.requireNonNull(rule, "rule"))
		{
			case BLOCK:
				nextBlocked.add(normalized);
				nextForced.remove(normalized);
				break;
			case FORCE:
				nextForced.add(normalized);
				nextBlocked.remove(normalized);
				break;
			case ALLOW:
				nextBlocked.remove(normalized);
				nextForced.remove(normalized);
				break;
			default:
				throw new IllegalStateException("Unknown spell rule: " + rule);
		}
		return nextBlocked.equals(blocked) && nextForced.equals(forced)
			? this : of(nextBlocked, nextForced);
	}

	/** Whether the name passes: not blocked, and in the forced set when one exists. */
	boolean allows(String lowerName)
	{
		return !blocked.contains(lowerName) && (forced.isEmpty() || forced.contains(lowerName));
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof NameRules))
		{
			return false;
		}
		NameRules that = (NameRules) other;
		return blocked.equals(that.blocked) && forced.equals(that.forced);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(blocked, forced);
	}

	@Override
	public String toString()
	{
		return "NameRules{blocked=" + blocked + ", forced=" + forced + '}';
	}

	private static Set<String> names(Collection<String> source)
	{
		Set<String> normalized = new LinkedHashSet<>();
		if (source != null)
		{
			for (String name : source)
			{
				String value = normalize(name);
				if (!value.isEmpty())
				{
					normalized.add(value);
				}
			}
		}
		return normalized;
	}

	private static Collection<String> split(String csv)
	{
		return csv == null ? Collections.emptyList() : Arrays.asList(csv.split(","));
	}

	private static String normalize(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}
}
