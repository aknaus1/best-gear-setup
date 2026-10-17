package com.bestgearsetup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.DrainSpecs;
import com.bestgearsetup.calc.SpecialAttacks;
import com.bestgearsetup.data.Monster;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** When a raid search assumes every teammate opens the boss with a defence reducer. */
public class TeamOpenersTest
{
	private static BestGearSetupConfig config(int killShare, boolean killSpecials, DrainSpecs drains)
	{
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("raidKillShare", killShare);
		overrides.put("killSpecials", killSpecials);
		overrides.put("drainSpecs", drains);
		return SearchAssumptions.capture(new BestGearSetupConfig() { }, overrides);
	}

	private static Monster named(String name)
	{
		Monster monster = new Monster();
		monster.setName(name);
		return monster;
	}

	@Test public void onlyPlannedPartyFightsOfTeamOpenedBossesCreditTeammates()
	{
		BestGearSetupConfig party = config(4, true, DrainSpecs.EXPECTED);
		SpecialAttacks none = SpecialAttacks.NONE;
		for (String boss : new String[]{"great olm (melee hand)", "tekton (normal)", "the maiden of sugadinti (normal)",
			"sotetseg (normal)", "xarpus"})
		{
			assertTrue(boss, BestGearSetupPlugin.teamOpeners(party, named(boss), none, true));
		}
		for (String other : new String[]{"great olm (mage hand)", "tekton (enraged)", "nylocas matomenos (normal mode maiden)",
			"pestilent bloat (normal)", "verzik vitur (phase 2)", "ba-ba"})
		{
			assertFalse(other, BestGearSetupPlugin.teamOpeners(party, named(other), none, true));
		}
		Monster xarpus = named("xarpus");
		// Solo, without credited drains, without kill specials, with drains set by hand, or in a live refresh.
		assertFalse(BestGearSetupPlugin.teamOpeners(config(1, true, DrainSpecs.EXPECTED), xarpus, none, true));
		assertFalse(BestGearSetupPlugin.teamOpeners(config(4, true, DrainSpecs.DAMAGE_ONLY), xarpus, none, true));
		assertFalse(BestGearSetupPlugin.teamOpeners(config(4, false, DrainSpecs.EXPECTED), xarpus, none, true));
		assertFalse(BestGearSetupPlugin.teamOpeners(party, xarpus, SpecialAttacks.builder().dragonWarhammer(1).build(), true));
		assertFalse(BestGearSetupPlugin.teamOpeners(party, xarpus, none, false));
		assertTrue(BestGearSetupPlugin.teamOpeners(config(4, true, DrainSpecs.ALL_MISS), xarpus, none, true));
	}
}
