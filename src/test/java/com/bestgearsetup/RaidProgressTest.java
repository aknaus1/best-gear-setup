package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

public class RaidProgressTest
{
	private static GameData data;
	private final RaidProgress progress = new RaidProgress();

	@BeforeClass public static void load() throws Exception
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
		data = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
	}

	static RaidPlan plan(GameData data, RaidPlanType type)
	{
		List<RaidEncounter> encounters = type.encounters(data, RaidScaling.Settings.SOLO, RaidOptions.DEFAULT);
		Loadout loadout = new Loadout(); loadout.set(Slot.WEAPON, data.findItem(4151));
		loadout.setStyle(AttackStyle.parse("flick,slash,accurate"));
		SetupResult setup = new SetupResult(CombatClass.MELEE, loadout, new DpsResult(5, 10, 1, 4, 5, null), 0);
		List<SearchResults> results = new ArrayList<>();
		for (RaidEncounter encounter : encounters)
		{
			Monster monster = new Monster(); monster.setName(encounter.getTarget().getName()); monster.setPhase(encounter.getPhase());
			results.add(new SearchResults(monster, 100, Collections.singletonMap(AttackStyle.Type.SLASH, Collections.singletonList(setup)),
				Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false));
		}
		return new RaidPlan(type, RaidSpellbook.ARCEUUS, encounters, results, Collections.nCopies(encounters.size(), setup),
			Collections.singletonList(loadout.getWeapon()), Collections.emptyList(), 20, 0);
	}

	private RaidPlan enter(RaidPlanType type)
	{
		RaidPlan plan = plan(data, type);
		progress.tick(type, plan, true);
		return plan;
	}

	private static RaidEncounter fight(RaidPlan plan, String label)
	{
		return plan.getEncounters().stream().filter(encounter -> encounter.getLabel().equalsIgnoreCase(label)).findFirst().get();
	}

	@Test public void theatreStartsInEntryGearAndAdvancesByRoomInsteadOfAddsAndPhases()
	{
		RaidPlan plan = enter(RaidPlanType.TOB);
		assertEquals(plan.getStartingEncounter().getLabel(), progress.current().getEncounter());
		progress.message("Wave 'The Maiden of Sugadinti' (Normal Mode) complete!");
		assertEquals("Bloat", progress.current().getEncounter());
		assertTrue(progress.current().getStatus().contains("1 / 6"));
		long advanced = progress.current().getRevision();
		progress.message("Wave 'The Maiden of Sugadinti' completed! Duration: 2:00. Total: 2:00.");
		assertEquals(advanced, progress.current().getRevision());
		progress.target(plan.getEncounters().get(0).getTarget(), null);
		assertEquals("Bloat", progress.current().getEncounter());
		progress.message("Wave 'The Pestilent Bloat' completed! Duration: 1:30. Total: 3:30.");
		assertTrue(progress.current().getEncounter().startsWith("Nylocas Waves"));
		progress.message("Wave 'The Nylocas' completed! Duration: 4:00. Total: 7:30.");
		assertEquals("Sotetseg", progress.current().getEncounter());
		progress.message("Wave 'Sotetseg' completed! Duration: 2:00. Total: 9:30.");
		assertEquals("Xarpus", progress.current().getEncounter());
		progress.message("Wave 'Xarpus' completed! Duration: 2:00. Total: 11:30.");
		assertEquals("Verzik P1", progress.current().getEncounter());
		progress.message("Wave 'The Final Challenge' completed! Duration: 5:00. Theatre of Blood completion time: 16:30.");
		assertEquals("Raid complete", progress.current().getStatus());
	}

	@Test public void toaPuzzlesOpenTheBossAndBossCompletionSkipsAdds()
	{
		RaidPlan plan = enter(RaidPlanType.TOA);
		assertTrue(progress.current().getEncounter().startsWith("Baboon"));
		progress.message("Challenge complete: Path of Apmeken. Duration: 1:30. Total: 1:30.");
		assertEquals("Ba-Ba", progress.current().getEncounter());
		assertTrue(progress.current().getStatus().contains("0 / 5"));
		progress.message("Challenge complete: Ba-Ba. Duration: 2:00. Total: 3:30.");
		assertEquals("Kephri Shield", progress.current().getEncounter());
		progress.message("Challenge complete: Kephri. Duration: 2:00. Total: 5:30.");
		assertFalse(progress.current().getEncounter().contains("scarab"));
		RaidEncounter akkha = plan.getEncounters().stream().filter(e -> e.getPhase() == EncounterPhase.AKKHA_ENRAGE).findFirst().get();
		progress.target(akkha.getTarget(), EncounterPhase.AKKHA_ENRAGE);
		assertEquals(akkha.getLabel(), progress.current().getEncounter());
		progress.target(akkha.getTarget(), null);
		assertEquals("Missing overhead icons must not reset the selected phase", akkha.getLabel(), progress.current().getEncounter());
		progress.message("Challenge complete: Akkha. Duration: 3:00. Total: 8:30.");
		assertEquals("Zebak", progress.current().getEncounter());
		progress.message("Challenge complete: Zebak. Duration: 2:00. Total: 10:30.");
		assertEquals("Obelisk", progress.current().getEncounter());
		progress.message("Challenge complete: The Wardens. Duration: 5:00. Tombs of Amascut challenge completion time: 15:30.");
		assertEquals("Raid complete", progress.current().getStatus());
	}

	@Test public void toaActualPathOrderOverridesThePlannedOrderWithoutReplanning()
	{
		RaidPlan plan = enter(RaidPlanType.TOA);
		progress.enteredRoom("crondis", true);
		assertEquals("Zebak", progress.current().getEncounter());
		progress.message("Challenge complete: Zebak. Duration: 2:00. Total: 2:00.");
		assertTrue(progress.current().getEncounter().startsWith("Baboon"));
		progress.enteredRoom("het", false);
		assertEquals(plan.getEncounters().stream().filter(e -> e.getPhase() == EncounterPhase.AKKHA_IMMUNE_MELEE)
			.findFirst().get().getLabel(), progress.current().getEncounter());
	}

	@Test public void coxDeathsSkipRepeatedPhasesAndOptionalScavengers()
	{
		RaidPlan plan = enter(RaidPlanType.COX_CM);
		progress.died(new Object(), fight(plan, "Tekton enraged").getTarget());
		progress.settleDeaths(Set.of("tekton"));
		assertEquals("Tekton", progress.current().getEncounter());
		progress.settleDeaths(Collections.emptySet());
		assertEquals("Ice Demon", progress.current().getEncounter());
		progress.died(new Object(), fight(plan, "Ice demon").getTarget());
		progress.settleDeaths(Collections.emptySet());
		assertEquals("Shamans", progress.current().getEncounter());
	}

	@Test public void coxMultiTargetRoomsRequireEveryDeathAndRejectResurrectedActors()
	{
		RaidPlan plan = enter(RaidPlanType.COX_CM);
		RaidEncounter guardians = fight(plan, "Guardians");
		progress.target(guardians.getTarget(), null);
		Object one = new Object(); Object two = new Object();
		progress.died(one, guardians.getTarget());
		progress.settleDeaths(Collections.emptySet());
		assertEquals("Guardians", progress.current().getEncounter());
		progress.died(one, guardians.getTarget()); // duplicate event isn't a second kill
		progress.settleDeaths(Collections.emptySet());
		assertEquals("Guardians", progress.current().getEncounter());
		progress.died(two, guardians.getTarget());
		progress.alive(one, guardians.getTarget());
		progress.settleDeaths(Collections.emptySet());
		assertEquals("Guardians", progress.current().getEncounter());
		progress.died(one, guardians.getTarget());
		progress.settleDeaths(Collections.emptySet());
		assertEquals("Vasa", progress.current().getEncounter());
		progress.died(new Object(), fight(plan, "Vasa crystal").getTarget());
		progress.settleDeaths(Collections.emptySet());
		assertEquals("A crystal isn't the boss", "Vasa", progress.current().getEncounter());
	}

	@Test public void replanKeepsProgressExitResetsAndLoadingGapsDoNot()
	{
		RaidPlan plan = enter(RaidPlanType.TOB);
		progress.message("Wave 'The Maiden of Sugadinti' completed! Duration: 2:00.");
		progress.tick(RaidPlanType.TOB, null, true);
		assertEquals("Bloat", progress.current().getEncounter());
		progress.tick(RaidPlanType.TOB, plan(data, RaidPlanType.TOB), true);
		assertEquals("Bloat", progress.current().getEncounter());
		progress.tick(RaidPlanType.TOB, plan, false); progress.tick(RaidPlanType.TOB, plan, false);
		assertTrue(progress.isActive());
		progress.tick(RaidPlanType.TOB, plan, true);
		assertEquals("Bloat", progress.current().getEncounter());
		for (int i = 0; i < 3; i++) { progress.tick(RaidPlanType.TOB, plan, false); }
		assertFalse(progress.isActive());
		progress.tick(RaidPlanType.TOB, plan, true);
		assertEquals(plan.getStartingEncounter().getLabel(), progress.current().getEncounter());
		progress.reset();
		assertFalse(progress.isActive());
	}

	@Test public void completionsDuringTheInitialSearchAreAppliedWhenItsPlanArrives()
	{
		progress.tick(RaidPlanType.TOB, null, true);
		progress.message("Wave 'The Maiden of Sugadinti' completed! Duration: 2:00.");
		progress.tick(RaidPlanType.TOB, plan(data, RaidPlanType.TOB), true);
		assertEquals("Bloat", progress.current().getEncounter());
	}

	@Test public void toaRoomAndPuzzleSignalsDuringPlanningStillOpenTheCurrentBoss()
	{
		progress.tick(RaidPlanType.TOA, null, true);
		progress.enteredRoom("apmeken", true);
		progress.message("Challenge complete: Path of Apmeken. Duration: 1:30. Total: 1:30.");
		progress.tick(RaidPlanType.TOA, plan(data, RaidPlanType.TOA), true);
		assertEquals("Ba-Ba", progress.current().getEncounter());
	}
}
