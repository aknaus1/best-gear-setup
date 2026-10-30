package com.bestgearsetup.calc;

import com.bestgearsetup.data.Spell;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/** Immutable blocked and forced autocast spell names, in their stored order. */
public final class SpellFilter
{
	public static final SpellFilter NONE = new SpellFilter(NameRules.NONE);

	private final NameRules rules;

	private SpellFilter(NameRules rules)
	{
		this.rules = rules;
	}

	private static SpellFilter wrap(NameRules rules)
	{
		return rules.isEmpty() ? NONE : new SpellFilter(rules);
	}

	public static SpellFilter of(Collection<String> blocked, Collection<String> forced)
	{
		return wrap(NameRules.of(blocked, forced));
	}

	public static SpellFilter parse(String blockedCsv, String forcedCsv)
	{
		return wrap(NameRules.parse(blockedCsv, forcedCsv));
	}

	public static String format(Collection<String> names)
	{
		return NameRules.format(names);
	}

	public Set<String> getBlocked()
	{
		return rules.getBlocked();
	}

	public Set<String> getForced()
	{
		return rules.getForced();
	}

	public boolean allows(Spell spell)
	{
		return spell == null || rules.allows(spell.getLowerName());
	}

	public boolean isEmpty()
	{
		return rules.isEmpty();
	}

	public SpellRule ruleFor(String name)
	{
		return rules.ruleFor(name);
	}

	public SpellFilter withRule(String name, SpellRule rule)
	{
		NameRules next = rules.withRule(name, rule);
		return next == rules ? this : wrap(next);
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
		return rules.equals(((SpellFilter) other).rules);
	}

	@Override
	public int hashCode()
	{
		return rules.hashCode();
	}

	@Override
	public String toString()
	{
		return "SpellFilter{blocked=" + rules.getBlocked() + ", forced=" + rules.getForced() + '}';
	}
}
