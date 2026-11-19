package com.bestgearsetup;

import java.util.Map;

/**
 * Immutable configuration values for one search, including transient detected overrides. Overrides are keyed by
 * the config key. Each config default reads its captured value through {@link #value}, so this class needs
 * no override per setting; AssumptionsTest checks that every config item is copied and can be overridden.
 */
final class Assumptions implements Options
{
	private final Object[] values;

	private Assumptions(Object[] values)
	{
		this.values = values;
	}

	/** Copy every setting of {@code source}, then apply the overrides; an unknown or mistyped override is rejected. */
	static Options capture(Options source, Map<String, Object> overrides)
	{
		Object[] values = ConfigSettings.capture(source);
		overrides.forEach((name, value) -> ConfigSettings.set(values, name, value));
		return new Assumptions(values);
	}

	/**
	 * A plain config or RuneLite's own config proxy uses its declared default; a search snapshot reads its captured
	 * value instead. A static call, so no unannotated method reaches RuneLite's configuration handler.
	 */
	@SuppressWarnings("unchecked")
	static <T> T value(Options source, String name, T defaultValue)
	{
		return source instanceof Assumptions
			? (T) ((Assumptions) source).values[ConfigSettings.index(name)] : defaultValue;
	}

	@Override
	public String toString()
	{
		return "Search assumptions";
	}
}
