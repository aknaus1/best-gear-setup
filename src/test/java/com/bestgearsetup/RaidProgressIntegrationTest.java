package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.ui.BestGearSetupPanel;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.gameval.VarbitID;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.Test;

/** Real plugin event handlers must navigate without scheduling the optimizer. */
public class RaidProgressIntegrationTest
{
	private final AtomicInteger searches = new AtomicInteger();
	private final BestGearSetupPlugin plugin = new BestGearSetupPlugin()
	{
		@Override public void findBestSetup(MonsterSummary target) { searches.incrementAndGet(); }
		@Override public void findActiveSetup(MonsterSummary target) { searches.incrementAndGet(); }
		@Override public void rerun() { searches.incrementAndGet(); }
	};
	private final Client client = mock(Client.class);
	private final Player player = mock(Player.class);
	private final Map<Integer, Integer> bits = new HashMap<>();
	private RaidPlan plan;

	@Before public void setUp() throws Exception
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
		GameData data = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
		plugin.gameData = data;
		plugin.config = new BestGearSetupConfig() { }; // ordinary Active encounter is off
		plugin.panel = mock(BestGearSetupPanel.class);
		when(plugin.panel.getRaidType()).thenReturn(RaidPlanType.TOB);
		plugin.ownedItems = new OwnedItems(null, null);
		plugin.search = new FutureTask<>(() -> null);
		plugin.searchGeneration.set(42);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(player);
		when(client.getVarbitValue(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(call -> bits.getOrDefault(call.getArgument(0), 0));
		Field clientField = BestGearSetupPlugin.class.getDeclaredField("client"); clientField.setAccessible(true); clientField.set(plugin, client);
		setPlan(RaidPlanType.TOB);
	}

	private void setPlan(RaidPlanType type) throws Exception
	{
		plan = RaidProgressTest.plan(plugin.gameData, type);
		Field planField = BestGearSetupPlugin.class.getDeclaredField("raidPlan"); planField.setAccessible(true); planField.set(plugin, plan);
		when(plugin.panel.getRaidType()).thenReturn(type);
	}

	private void tick() throws Exception
	{
		plugin.onGameTick(new GameTick()); SwingUtilities.invokeAndWait(() -> { });
	}

	private void message(ChatMessageType type, String text) throws Exception
	{
		plugin.onChatMessage(new ChatMessage(null, type, "", text, null, 0));
		SwingUtilities.invokeAndWait(() -> { });
	}

	@Test public void startingAndCompletingARoomNavigatesWithAutomaticSearchesDisabled() throws Exception
	{
		bits.put(VarbitID.TOB_CLIENT_PARTYSTATUS, 1); tick();
		assertFalse("Being in the party lobby isn't a raid start", plugin.raidProgress.isActive());
		bits.put(VarbitID.TOB_CLIENT_PARTYSTATUS, 2); tick();
		verify(plugin.panel).showRaidProgress(eq(RaidPlanType.TOB), eq(plan.getStartingEncounter().getLabel()), anyString());
		message(ChatMessageType.GAMEMESSAGE, "Wave '<col=ff0000>The Maiden of Sugadinti</col>' completed! Duration: 2:00.");
		verify(plugin.panel).showRaidProgress(eq(RaidPlanType.TOB), eq("Bloat"), anyString());
		assertEquals(0, searches.get());
		assertEquals(42, plugin.searchGeneration.get());
		assertFalse(plugin.search.isCancelled());
	}

	@Test public void playerChatAndNpcDisappearanceCannotCompleteARoom() throws Exception
	{
		bits.put(VarbitID.TOB_CLIENT_PARTYSTATUS, 2); tick();
		message(ChatMessageType.PUBLICCHAT, "Wave 'The Maiden of Sugadinti' completed! Duration: 2:00.");
		NPC boss = mock(NPC.class);
		plugin.onNpcDespawned(new NpcDespawned(boss)); tick();
		assertEquals(plan.getStartingEncounter().getLabel(), plugin.raidProgress.current().getEncounter());
		verify(plugin.panel, never()).showRaidProgress(eq(RaidPlanType.TOB), eq("Bloat"), anyString());
	}

	@Test public void combatTargetsSelectTheSavedSetupWithoutActiveEncounterSearches() throws Exception
	{
		bits.put(VarbitID.TOB_CLIENT_PARTYSTATUS, 2); tick();
		RaidEncounter bloat = plan.getEncounters().stream().filter(fight -> fight.getLabel().equals("Bloat")).findFirst().get();
		NPC npc = mock(NPC.class); NPCComposition composition = mock(NPCComposition.class);
		when(npc.getId()).thenReturn(bloat.getTarget().getId()); when(npc.getName()).thenReturn("Pestilent Bloat");
		when(npc.getHealthRatio()).thenReturn(100); when(npc.getTransformedComposition()).thenReturn(composition);
		when(composition.getActions()).thenReturn(new String[]{"Attack"}); when(player.getInteracting()).thenReturn(npc);
		tick();
		assertEquals("Bloat", plugin.raidProgress.current().getEncounter());
		verify(plugin.panel, never()).selectActiveMonster(any());
		assertEquals(0, searches.get());
	}

	@Test public void aVasiliasFormChangeSelectsTheNewFormBeforeTheReclick() throws Exception
	{
		bits.put(VarbitID.TOB_CLIENT_PARTYSTATUS, 2); tick();
		int melee = encounter("Nylocas: Melee").getTarget().getId(), ranged = encounter("Nylocas: Ranged").getTarget().getId();
		NPC vasilias = mock(NPC.class); NPCComposition composition = mock(NPCComposition.class);
		when(vasilias.getId()).thenReturn(melee); when(vasilias.getName()).thenReturn("Nylocas Vasilias");
		when(vasilias.getHealthRatio()).thenReturn(100); when(vasilias.getTransformedComposition()).thenReturn(composition);
		when(composition.getActions()).thenReturn(new String[]{"Attack"}); when(player.getInteracting()).thenReturn(vasilias);
		Hitsplat hit = mock(Hitsplat.class); when(hit.isMine()).thenReturn(true);
		HitsplatApplied applied = new HitsplatApplied(); applied.setActor(vasilias); applied.setHitsplat(hit);
		plugin.onHitsplatApplied(applied); tick();
		assertEquals("Nylocas: Melee", plugin.raidProgress.current().getEncounter());
		// The same NPC changes form and your attack stops: no target until you reclick.
		when(vasilias.getId()).thenReturn(ranged); when(player.getInteracting()).thenReturn(null);
		tick();
		assertEquals("Nylocas: Ranged", plugin.raidProgress.current().getEncounter());
		assertEquals(0, searches.get());
	}

	private RaidEncounter encounter(String label)
	{
		return plan.getEncounters().stream().filter(fight -> fight.getLabel().equals(label)).findFirst().get();
	}

	@Test public void coxDeathsAdvanceOnceAndLandedSpecsOrSuppliesDoNotReplanTheKit() throws Exception
	{
		setPlan(RaidPlanType.COX_CM);
		bits.put(VarbitID.RAIDS_CLIENT_INDUNGEON, 1); bits.put(VarbitID.RAIDS_CLIENT_PROGRESS, 1);
		bits.put(VarbitID.RAIDS_CHALLENGE_MODE, 1); tick();
		NPC tekton = mock(NPC.class);
		when(tekton.getId()).thenReturn(plan.getEncounters().get(0).getTarget().getId()); when(tekton.getName()).thenReturn("Tekton");
		plugin.onActorDeath(new ActorDeath(tekton)); tick();
		assertEquals("Ice Demon", plugin.raidProgress.current().getEncounter());
		plugin.onActorDeath(new ActorDeath(tekton)); tick();
		assertEquals("Ice Demon", plugin.raidProgress.current().getEncounter());
		plugin.lastSearched = plan.getEncounters().get(0).getTarget();
		plugin.searchOwned = Collections.singleton(4151); plugin.gearIds = Collections.singleton(4151);
		plugin.searchQuantities = Collections.singletonMap(892, 100L); plugin.consumableIds = Collections.singleton(892); plugin.searchAmmoCount = 100;
		plugin.invalidateIfOwnershipChanged(); SwingUtilities.invokeAndWait(plugin::refreshSupply);
		assertEquals("Consumption and loot keep the packed kit", 0, searches.get());
		verify(plugin.panel).showRaidProgress(eq(RaidPlanType.COX_CM), eq("Ice Demon"), anyString());
	}

	@Test public void queuedProgressCannotSurviveAReset() throws Exception
	{
		bits.put(VarbitID.TOB_CLIENT_PARTYSTATUS, 2); tick();
		SwingUtilities.invokeAndWait(() ->
		{
			plugin.onChatMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "",
				"Wave 'The Maiden of Sugadinti' completed! Duration: 2:00.", null, 0));
			plugin.raidProgress.reset();
		});
		SwingUtilities.invokeAndWait(() -> { });
		verify(plugin.panel, never()).showRaidProgress(eq(RaidPlanType.TOB), eq("Bloat"), anyString());
	}
}
