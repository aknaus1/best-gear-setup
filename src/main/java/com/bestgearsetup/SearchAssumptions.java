package com.bestgearsetup;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigItem;

/** Immutable configuration values for one search, including transient detected overrides. */
final class SearchAssumptions
{
	private SearchAssumptions() {}

	static BestGearSetupConfig capture(BestGearSetupConfig source, Map<String, Object> overrides)
	{
		Map<String, Object> values = new HashMap<>();
		for (Method method : BestGearSetupConfig.class.getMethods())
		{
			if (method.isAnnotationPresent(ConfigItem.class))
			{
				try { values.put(method.getName(), method.invoke(source)); }
				catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot snapshot " + method.getName(), e); }
			}
		}
		values.putAll(overrides);
		return (BestGearSetupConfig) Proxy.newProxyInstance(BestGearSetupConfig.class.getClassLoader(),
			new Class<?>[]{BestGearSetupConfig.class}, (proxy, method, args) ->
			{
				if (method.getName().equals("toString")) { return "Search assumptions"; }
				if (method.getName().equals("hashCode")) { return System.identityHashCode(proxy); }
				if (method.getName().equals("equals")) { return proxy == args[0]; }
				return values.get(method.getName());
			});
	}
}
