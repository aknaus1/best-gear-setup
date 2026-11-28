package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class PreferenceMigrationTest
{
	@Test public void migratesLegacyTaskAndRaidValues()
	{
		Map<String,String> stored = new HashMap<>();
		stored.put("onSlayerTask","true"); stored.put("taskMode","AUTO");
		stored.put("toaRaidLevel","350"); stored.put("autoRaid","true");
		Map<String,Object> changes = PreferenceMigration.plan(stored);
		assertEquals(AutoState.ON,changes.get("taskMode"));
		assertEquals(false,changes.get("autoRaid"));
		assertEquals(true,changes.get(PreferenceMigration.MARKER));
	}

	@Test public void defaultFalseAndDefaultRaidValuesAllowAutomaticDetection()
	{
		Map<String,String> defaults = new HashMap<>();
		defaults.put("onSlayerTask","false"); defaults.put("kandarinDiary","false");
		defaults.put("raidPartySize","1"); defaults.put("toaRaidLevel","0");
		defaults.put("toaPathLevel","0"); defaults.put("coxChallengeMode","false");
		Map<String,Object> changes = PreferenceMigration.plan(defaults);
		assertFalse(changes.containsKey("taskMode")); assertFalse(changes.containsKey("diaryMode"));
		assertFalse(changes.containsKey("autoRaid"));
	}

	@Test public void alreadyMigratedSettingsAreNeverOverwritten()
	{
		Map<String,String> stored = new HashMap<>(); stored.put("onSlayerTask","true");
		stored.put(PreferenceMigration.MARKER,"true"); stored.put(Options.GUIDE_KEY,"false");
		assertTrue(PreferenceMigration.plan(stored).isEmpty());
	}

	@Test public void onlyAFreshInstallOpensTheGuide()
	{
		// Fresh install: nothing stored yet, so the guide starts unseen and stays so across restarts until closed.
		assertEquals(false,PreferenceMigration.plan(new HashMap<>()).get(Options.GUIDE_KEY));
		// Updating from before the guide: settings were already migrated, so it is marked seen.
		Map<String,String> updating = new HashMap<>(); updating.put(PreferenceMigration.MARKER,"true");
		assertEquals(Map.of(Options.GUIDE_KEY,true),PreferenceMigration.plan(updating));
		Map<String,String> unseen = new HashMap<>(); unseen.put(Options.GUIDE_KEY,"false");
		assertFalse(PreferenceMigration.plan(unseen).containsKey(Options.GUIDE_KEY));
	}
}
