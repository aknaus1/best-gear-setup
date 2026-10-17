package com.bestgearsetup;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.ui.BestGearSetupPanel;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;

public class ActiveEncounterTest
{
	@Test public void tracksAkkhaFormChanges() throws Exception
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		plugin.ownedItems = new OwnedItems(null, null);
		Client client = mock(Client.class); Player player = mock(Player.class); NPC npc = mock(NPC.class);
		NPCComposition composition = mock(NPCComposition.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
		when(player.getInteracting()).thenReturn(npc); when(npc.getInteracting()).thenReturn(player); when(npc.getId()).thenReturn(11789); when(npc.getName()).thenReturn("Akkha");
		when(npc.getHealthRatio()).thenReturn(100); when(npc.getTransformedComposition()).thenReturn(composition);
		when(composition.getActions()).thenReturn(new String[]{"Attack"});
		MonsterSummary akkha = new MonsterSummary(); akkha.setId(11789); akkha.setName("akkha");
		plugin.gameData = new GameData(Collections.singletonList(akkha), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		plugin.config = new BestGearSetupConfig() { @Override public boolean activeEncounter() { return true; } };
		plugin.panel = mock(BestGearSetupPanel.class);
		plugin.client = client;
		int[] forms = {NpcID.AKKHA_MELEE, NpcID.AKKHA_RANGE, NpcID.AKKHA_MAGE, NpcID.AKKHA_ENRAGE};
		EncounterPhase[] phases = {EncounterPhase.AKKHA_IMMUNE_MELEE, EncounterPhase.AKKHA_IMMUNE_RANGED,
			EncounterPhase.AKKHA_IMMUNE_MAGIC, EncounterPhase.AKKHA_ENRAGE};
		for (int i = 0; i < forms.length; i++)
		{
			when(npc.getId()).thenReturn(forms[i]);
			org.junit.Assert.assertEquals(phases[i], LiveRaid.akkhaPhase(npc));
			plugin.onGameTick(new GameTick()); SwingUtilities.invokeAndWait(() -> { });
			plugin.onGameTick(new GameTick()); SwingUtilities.invokeAndWait(() -> { });
			verify(plugin.panel, times(i + 1)).selectActiveMonster(akkha);
		}
		when(npc.getId()).thenReturn(NpcID.AKKHA_SPAWN);
		org.junit.Assert.assertNull(LiveRaid.akkhaPhase(npc));
	}

	@Test public void followsNewNpcAndTransformedPhaseOnceEachAndHonoursDisable() throws Exception
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		plugin.ownedItems = new OwnedItems(null, null);
		Client client = mock(Client.class);
		Player player = mock(Player.class);
		NPC npc = mock(NPC.class);
		NPCComposition composition = mock(NPCComposition.class);
		BestGearSetupConfig config = mock(BestGearSetupConfig.class);
		when(config.activeEncounter()).thenReturn(true);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getInteracting()).thenReturn(npc);
		when(npc.getInteracting()).thenReturn(player);
		when(npc.getId()).thenReturn(200);
		when(npc.getName()).thenReturn("Boss");
		when(npc.getHealthRatio()).thenReturn(100);
		when(npc.getTransformedComposition()).thenReturn(composition);
		when(composition.getActions()).thenReturn(new String[]{"Attack"});
		MonsterSummary first = new MonsterSummary(); first.setId(200); first.setName("boss (phase 1)");
		MonsterSummary second = new MonsterSummary(); second.setId(201); second.setName("boss (phase 2)");
		plugin.gameData = new GameData(Arrays.asList(first, second), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		plugin.config = config;
		plugin.panel = mock(BestGearSetupPanel.class);
		plugin.client = client;
		plugin.onGameTick(new GameTick());
		SwingUtilities.invokeAndWait(() -> { });
		plugin.onGameTick(new GameTick());
		SwingUtilities.invokeAndWait(() -> { });
		verify(plugin.panel, times(1)).selectActiveMonster(first);
		when(npc.getId()).thenReturn(201);
		plugin.onGameTick(new GameTick());
		SwingUtilities.invokeAndWait(() -> { });
		verify(plugin.panel).selectActiveMonster(second);
		when(config.activeEncounter()).thenReturn(false);
		when(npc.getId()).thenReturn(200);
		plugin.onGameTick(new GameTick());
		SwingUtilities.invokeAndWait(() -> { });
		verify(plugin.panel, times(1)).selectActiveMonster(first);
	}

	@Test public void targetingOrStandingNearAnNpcIsNotCombat() throws Exception
	{
		Fixture f = new Fixture();
		NPC boss = f.npc(200, "Boss");
		when(f.player.getInteracting()).thenReturn(boss);
		f.tick();
		verify(f.plugin.panel, never()).selectActiveMonster(org.mockito.ArgumentMatchers.any());
		f.plugin.onHitsplatApplied(f.hit(boss, false));
		f.tick();
		verify(f.plugin.panel, never()).selectActiveMonster(org.mockito.ArgumentMatchers.any());
		f.plugin.onHitsplatApplied(f.hit(boss, true));
		f.tick();
		verify(f.plugin.panel).selectActiveMonster(f.boss);
	}

	@Test public void ownHitsLeadAndTheTargetedNpcWinsAoeHits() throws Exception
	{
		Fixture f = new Fixture();
		NPC boss = f.npc(200, "Boss");
		NPC add = f.npc(201, "Add");
		f.plugin.onHitsplatApplied(f.hit(boss, true));
		f.plugin.onHitsplatApplied(f.hit(add, true));
		f.tick();
		verify(f.plugin.panel).selectActiveMonster(f.boss);
		verify(f.plugin.panel, never()).selectActiveMonster(f.add);
		when(f.player.getInteracting()).thenReturn(add);
		f.plugin.onHitsplatApplied(f.hit(add, true));
		f.tick();
		verify(f.plugin.panel).selectActiveMonster(f.add);
	}

	@Test public void combatLapsesAfterIdleTicksAndOnDespawn() throws Exception
	{
		Fixture f = new Fixture();
		NPC boss = f.npc(200, "Boss");
		f.plugin.onHitsplatApplied(f.hit(boss, true));
		f.tick();
		when(f.client.getTickCount()).thenReturn(16);
		f.tick();
		verify(f.plugin.panel, times(1)).selectActiveMonster(f.boss);
		when(f.client.getTickCount()).thenReturn(17);
		f.tick();
		f.plugin.onHitsplatApplied(f.hit(boss, true));
		f.tick();
		verify(f.plugin.panel, times(2)).selectActiveMonster(f.boss);
		f.plugin.onNpcDespawned(new NpcDespawned(boss));
		f.tick();
		f.plugin.onHitsplatApplied(f.hit(boss, true));
		f.tick();
		verify(f.plugin.panel, times(3)).selectActiveMonster(f.boss);
	}

	@Test public void anotherNpcOfTheShownTypeKeepsTheResults() throws Exception
	{
		Fixture f = new Fixture();
		// The boss's results are already shown, from a search of a live NPC.
		f.plugin.lastSearched = f.boss;
		f.plugin.searchedLive = true;
		NPC first = f.npc(200, "Boss");
		when(f.player.getInteracting()).thenReturn(first);
		f.plugin.onHitsplatApplied(f.hit(first, true));
		f.tick();
		verify(f.plugin.panel).showActiveMonster(f.boss, null);
		// A second NPC with the same id, e.g. the next one after a kill, doesn't search again either.
		NPC second = f.npc(200, "Boss");
		when(f.player.getInteracting()).thenReturn(second);
		f.plugin.onHitsplatApplied(f.hit(second, true));
		f.tick();
		verify(f.plugin.panel, times(2)).showActiveMonster(f.boss, null);
		verify(f.plugin.panel, never()).selectActiveMonster(org.mockito.ArgumentMatchers.any());
		// A different monster does.
		NPC add = f.npc(201, "Add");
		when(f.player.getInteracting()).thenReturn(add);
		f.plugin.onHitsplatApplied(f.hit(add, true));
		f.tick();
		verify(f.plugin.panel).selectActiveMonster(f.add);
	}

	@Test public void instanceSpecificOrFailedResultsAreSearchedAgain() throws Exception
	{
		Fixture f = new Fixture();
		f.plugin.lastSearched = f.boss;
		f.plugin.searchedLive = true;
		// Specs landed on the last NPC don't apply to a new one.
		f.plugin.searchedLandedSpecs = true;
		NPC first = f.npc(200, "Boss");
		when(f.player.getInteracting()).thenReturn(first);
		f.plugin.onHitsplatApplied(f.hit(first, true));
		f.tick();
		verify(f.plugin.panel).selectActiveMonster(f.boss);
		f.plugin.searchedLandedSpecs = false;
		f.plugin.searchFailed = true;
		NPC second = f.npc(200, "Boss");
		when(f.player.getInteracting()).thenReturn(second);
		f.plugin.onHitsplatApplied(f.hit(second, true));
		f.tick();
		verify(f.plugin.panel, times(2)).selectActiveMonster(f.boss);
		// A different live Akkha phase maps to different stats.
		f.plugin.searchFailed = false;
		org.junit.Assert.assertTrue(f.plugin.showsSameType(f.boss, null));
		org.junit.Assert.assertFalse(f.plugin.showsSameType(f.boss, EncounterPhase.AKKHA_IMMUNE_MAGIC));
		org.junit.Assert.assertFalse(f.plugin.showsSameType(f.add, null));
		f.plugin.lastSearched = null;
		org.junit.Assert.assertFalse(f.plugin.showsSameType(f.boss, null));
	}

	@Test public void resultsPlannedWithoutALiveNpcAreSearchedOnTheFirstEngagement() throws Exception
	{
		Fixture f = new Fixture();
		// Picked from the panel (e.g. a raid's first room): no live raid scaling or task location was read.
		f.plugin.lastSearched = f.boss;
		f.plugin.searchedLive = false;
		NPC first = f.npc(200, "Boss");
		when(f.player.getInteracting()).thenReturn(first);
		f.plugin.onHitsplatApplied(f.hit(first, true));
		f.tick();
		verify(f.plugin.panel).selectActiveMonster(f.boss);
		verify(f.plugin.panel, never()).showActiveMonster(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		org.junit.Assert.assertFalse(f.plugin.showsSameType(f.boss, null));
		// Once that live search has run, the next NPC of the type keeps its results.
		f.plugin.searchedLive = true;
		NPC second = f.npc(200, "Boss");
		when(f.player.getInteracting()).thenReturn(second);
		f.plugin.onHitsplatApplied(f.hit(second, true));
		f.tick();
		verify(f.plugin.panel).showActiveMonster(f.boss, null);
	}

	/** An active-encounter plugin with two known monsters, ids 200 and 201. */
	private static final class Fixture
	{
		final BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		final Client client = mock(Client.class);
		final Player player = mock(Player.class);
		final MonsterSummary boss = new MonsterSummary();
		final MonsterSummary add = new MonsterSummary();

		Fixture() throws Exception
		{
			plugin.ownedItems = new OwnedItems(null, null);
			when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
			when(client.getLocalPlayer()).thenReturn(player);
			boss.setId(200); boss.setName("boss");
			add.setId(201); add.setName("add");
			plugin.gameData = new GameData(Arrays.asList(boss, add), Collections.emptyMap(), Collections.emptyList(),
				Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
			plugin.config = new BestGearSetupConfig() { @Override public boolean activeEncounter() { return true; } };
			plugin.panel = mock(BestGearSetupPanel.class);
			plugin.client = client;
		}

		NPC npc(int id, String name)
		{
			NPC npc = mock(NPC.class);
			NPCComposition composition = mock(NPCComposition.class);
			when(npc.getId()).thenReturn(id);
			when(npc.getName()).thenReturn(name);
			when(npc.getHealthRatio()).thenReturn(100);
			when(npc.getTransformedComposition()).thenReturn(composition);
			when(composition.getActions()).thenReturn(new String[]{"Attack"});
			return npc;
		}

		HitsplatApplied hit(NPC npc, boolean mine)
		{
			Hitsplat hitsplat = mock(Hitsplat.class);
			when(hitsplat.isMine()).thenReturn(mine);
			HitsplatApplied event = new HitsplatApplied();
			event.setActor(npc);
			event.setHitsplat(hitsplat);
			return event;
		}

		void tick() throws Exception
		{
			plugin.onGameTick(new GameTick());
			SwingUtilities.invokeAndWait(() -> { });
		}
	}
}
