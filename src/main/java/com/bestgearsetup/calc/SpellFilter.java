package com.bestgearsetup.calc;

import com.bestgearsetup.data.Spell;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/** Immutable blocked and forced autocast spell names, in their stored order. */
public final class SpellFilter
{
	public static final SpellFilter NONE = new SpellFilter(new LinkedHashSet<>(), new LinkedHashSet<>());

	private final Set<String> blocked;
	private final Set<String> forced;

	private SpellFilter(Set<String> blocked, Set<String> forced)
	{
		this.blocked = Collections.unmodifiableSet(new LinkedHashSet<>(blocked));
		this.forced = Collections.unmodifiableSet(new LinkedHashSet<>(forced));
	}

	public static SpellFilter of(Collection<String> blocked, Collection<String> forced)
	{
		Set<String> blockedNames = names(blocked);
		Set<String> forcedNames = names(forced);
		forcedNames.removeAll(blockedNames);
		return blockedNames.isEmpty() && forcedNames.isEmpty() ? NONE : new SpellFilter(blockedNames, forcedNames);
	}

	public static SpellFilter parse(String blockedCsv, String forcedCsv)
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

	public boolean allows(Spell spell)
	{
		if (spell == null)
		{
			return true;
		}
		String name = spell.getLowerName();
		return !blocked.contains(name) && (forced.isEmpty() || forced.contains(name));
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

	public SpellFilter withRule(String name, SpellRule rule)
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

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof SpellFilter))
		{
			return false;
		}
		SpellFilter that = (SpellFilter) other;
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
		return "SpellFilter{blocked=" + blocked + ", forced=" + forced + '}';
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
		return csv == null ? Collections.emptyList() : java.util.Arrays.asList(csv.split(","));
	}

	private static String normalize(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}
}
