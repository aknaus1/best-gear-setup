package com.bestgearsetup;

import java.util.Map;

/** Immutable search values, read through the config's typed getters without reflective invocation or a proxy. */
final class SearchAssumptions implements BestGearSetupConfig
{
	private final Object[] values;

	private SearchAssumptions(Object[] values)
	{
		this.values = values;
	}

	static BestGearSetupConfig capture(BestGearSetupConfig source, Map<String, Object> overrides)
	{
		Object[] values = ConfigSettings.capture(source);
		overrides.forEach((name, value) -> ConfigSettings.set(values, name, value));
		return new SearchAssumptions(values);
	}

	Object[] copyValues() { return values.clone(); }

	/**
	 * A plain config or RuneLite's own config proxy uses its declared default; a frozen search reads its captured
	 * value instead. Static dispatch avoids sending an unannotated method to RuneLite's configuration handler.
	 */
	@SuppressWarnings("unchecked")
	static <T> T value(BestGearSetupConfig source, String name, T defaultValue)
	{
		return source instanceof SearchAssumptions ? (T) ((SearchAssumptions) source).values[ConfigSettings.index(name)] : defaultValue;
	}

	@Override public String toString() { return "Search assumptions"; }
}
