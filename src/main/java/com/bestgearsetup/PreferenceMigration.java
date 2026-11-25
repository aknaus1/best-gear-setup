package com.bestgearsetup;

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
		// Legacy false was the default, not evidence that the user intended a permanent override.
		if ("true".equals(old.get(Options.ON_TASK_KEY)) && (old.get("taskMode") == null || "AUTO".equals(old.get("taskMode"))))
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
