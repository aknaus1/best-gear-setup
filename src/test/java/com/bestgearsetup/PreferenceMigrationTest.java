package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class PreferenceMigrationTest
{
	@Test public void preservesLegacyNamedChoicesDespiteNativeDefaultInitialization()
	{
		Map<String,String> stored = new HashMap<>();
		stored.put("potionMELEE","super combat potion"); stored.put("meleePotionChoice","BEST");
		stored.put("onSlayerTask","true"); stored.put("taskMode","AUTO");
		stored.put("toaRaidLevel","350"); stored.put("autoRaid","true");
		Map<String,Object> changes = PreferenceMigration.plan(stored);
		assertEquals(PotionOptions.Melee.SUPER_COMBAT_POTION,changes.get("meleePotionChoice"));
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

	@Test public void alreadyMigratedAndExplicitNewSelectionsAreNeverOverwritten()
	{
		Map<String,String> stored = new HashMap<>(); stored.put("potionMELEE","attack potion");
		stored.put("meleePotionChoice","NONE");
		assertFalse(PreferenceMigration.plan(stored).containsKey("meleePotionChoice"));
		stored.put(PreferenceMigration.MARKER,"true");
		assertTrue(PreferenceMigration.plan(stored).isEmpty());
	}
}
