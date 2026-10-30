package com.bestgearsetup.calc;

import java.util.Collection;
import java.util.Set;

/** Immutable blocked runes and the optional "only these runes" allow-list, by rune name in stored order. */
public final class RuneFilter
{
	public static final RuneFilter NONE = new RuneFilter(NameRules.NONE);

	private final NameRules rules;

	private RuneFilter(NameRules rules)
	{
		this.rules = rules;
	}

	private static RuneFilter wrap(NameRules rules)
	{
		return rules.isEmpty() ? NONE : new RuneFilter(rules);
	}

	/** Normalised filter; an only-name that is also blocked stays blocked, and an empty result is NONE. */
	public static RuneFilter of(Collection<String> blocked, Collection<String> only)
	{
		return wrap(NameRules.of(blocked, only));
	}

	public static RuneFilter parse(String blockedCsv, String onlyCsv)
	{
		return wrap(NameRules.parse(blockedCsv, onlyCsv));
	}

	public static String format(Collection<String> names)
	{
		return NameRules.format(names);
	}

	public Set<String> getBlocked()
	{
		return rules.getBlocked();
	}

	/** The allow-list; when non-empty only these runes are permitted. */
	public Set<String> getOnly()
	{
		return rules.getForced();
	}

	public boolean isEmpty()
	{
		return rules.isEmpty();
	}

	/** BLOCK, FORCE (in the only-list) or ALLOW. */
	public SpellRule ruleFor(String name)
	{
		return rules.ruleFor(name);
	}

	/** This filter when nothing changes; NONE when the result is empty. */
	public RuneFilter withRule(String name, SpellRule rule)
	{
		NameRules next = rules.withRule(name, rule);
		return next == rules ? this : wrap(next);
	}

	/** Whether the rune may be used; runes SpellRunes cannot name are permitted only with no allow-list. */
	public boolean permits(int runeId)
	{
		if (rules.isEmpty())
		{
			return true;
		}
		String name = SpellRunes.runeName(runeId);
		if ("rune".equals(name))
		{
			// Unnamed rune ids never match a stored name.
			return rules.getForced().isEmpty();
		}
		return rules.allows(name);
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof RuneFilter))
		{
			return false;
		}
		return rules.equals(((RuneFilter) other).rules);
	}

	@Override
	public int hashCode()
	{
		return rules.hashCode();
	}

	@Override
	public String toString()
	{
		return "RuneFilter{blocked=" + rules.getBlocked() + ", only=" + rules.getForced() + '}';
	}
}
