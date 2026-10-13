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
import java.lang.reflect.Field;
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
import net.runelite.api.gameval.SpriteID;
import org.junit.Test;

public class ActiveEncounterTest
{
	@Test public void tracksAkkhaOverheadChangesWithoutAnNpcIdChange() throws Exception
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		Client client = mock(Client.class); Player player = mock(Player.class); NPC npc = mock(NPC.class);
		NPCComposition composition = mock(NPCComposition.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
		when(player.getInteracting()).thenReturn(npc); when(npc.getInteracting()).thenReturn(player); when(npc.getId()).thenReturn(11789); when(npc.getName()).thenReturn("Akkha");
		when(npc.getHealthRatio()).thenReturn(100); when(npc.getTransformedComposition()).thenReturn(composition);
		when(composition.getActions()).thenReturn(new String[]{"Attack"});
		when(npc.getOverheadArchiveIds()).thenReturn(new int[]{SpriteID.HEADICONS_PRAYER});
		MonsterSummary akkha = new MonsterSummary(); akkha.setId(11789); akkha.setName("akkha");
		plugin.gameData = new GameData(Collections.singletonList(akkha), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		plugin.config = new BestGearSetupConfig() { @Override public boolean activeEncounter() { return true; } };
		plugin.panel = mock(BestGearSetupPanel.class);
		Field field = BestGearSetupPlugin.class.getDeclaredField("client"); field.setAccessible(true); field.set(plugin, client);
		short[] icons = {0, 1, 2, 6};
		EncounterPhase[] phases = {EncounterPhase.AKKHA_IMMUNE_MELEE, EncounterPhase.AKKHA_IMMUNE_RANGED,
			EncounterPhase.AKKHA_IMMUNE_MAGIC, EncounterPhase.AKKHA_ENRAGE};
		for (int i = 0; i < icons.length; i++)
		{
			when(npc.getOverheadSpriteIds()).thenReturn(new short[]{icons[i]});
			org.junit.Assert.assertEquals(phases[i], LiveRaid.akkhaPhase(npc));
			plugin.onGameTick(new GameTick()); SwingUtilities.invokeAndWait(() -> { });
			plugin.onGameTick(new GameTick()); SwingUtilities.invokeAndWait(() -> { });
			verify(plugin.panel, times(i + 1)).selectActiveMonster(akkha);
		}
		when(npc.getName()).thenReturn("Ba-Ba");
		org.junit.Assert.assertNull(LiveRaid.akkhaPhase(npc));
	}

	@Test public void followsNewNpcAndTransformedPhaseOnceEachAndHonoursDisable() throws Exception
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
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
		Field field = BestGearSetupPlugin.class.getDeclaredField("client"); field.setAccessible(true); field.set(plugin, client);
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
			when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
			when(client.getLocalPlayer()).thenReturn(player);
			boss.setId(200); boss.setName("boss");
			add.setId(201); add.setName("add");
			plugin.gameData = new GameData(Arrays.asList(boss, add), Collections.emptyMap(), Collections.emptyList(),
				Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
			plugin.config = new BestGearSetupConfig() { @Override public boolean activeEncounter() { return true; } };
			plugin.panel = mock(BestGearSetupPanel.class);
			Field field = BestGearSetupPlugin.class.getDeclaredField("client"); field.setAccessible(true); field.set(plugin, client);
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
