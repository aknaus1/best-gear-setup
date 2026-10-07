package com.bestgearsetup;

import com.bestgearsetup.data.CombatClass;
import java.util.LinkedHashMap;
import java.util.Map;

/** One-time migration also handles defaults RuneLite writes before plugin startup. */
final class PreferenceMigration
{
	static final String MARKER = "compactSettingsMigrated";
	private PreferenceMigration() {}

	static Map<String, Object> plan(Map<String, String> old)
	{
		Map<String, Object> changes = new LinkedHashMap<>();
		if ("true".equals(old.get(MARKER))) { return changes; }
		for (CombatClass cls : CombatClass.values())
		{
			String name = old.get(BestGearSetupConfig.POTION_KEY_PREFIX + cls.name());
			String key = cls.name().toLowerCase(java.util.Locale.ROOT) + "PotionChoice";
			Object selected = name == null ? null : BestGearSetupPlugin.potionOption(cls, name);
			if (selected != null && (old.get(key) == null || "BEST".equals(old.get(key))))
			{ changes.put(key, selected); }
		}
		// Legacy false was the default, not evidence that the user intended a permanent override.
		if ("true".equals(old.get(BestGearSetupConfig.ON_TASK_KEY)) && (old.get("taskMode") == null || "AUTO".equals(old.get("taskMode"))))
		{ changes.put("taskMode", AutoState.ON); }
		for (String[] input : new String[][]{{"raidPartySize","1"},{"toaRaidLevel","0"},{"toaPathLevel","0"},{"coxChallengeMode","false"}})
		{
			if (old.get(input[0]) != null && !input[1].equals(old.get(input[0])))
			{ changes.put("autoRaid", false); break; }
		}
		changes.put(MARKER, true);
		return changes;
	}
}
