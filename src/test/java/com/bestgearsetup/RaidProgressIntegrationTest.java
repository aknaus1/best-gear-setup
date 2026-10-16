package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.ui.BestGearSetupPanel;
import java.lang.reflect.Field;
import java.util.Arrays;
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
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpriteID;
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

	@Test public void akkhaVulnerabilityAndImmunitySwitchSavedGearBeforeClicking() throws Exception
	{
		setPlan(RaidPlanType.TOA);
		NPC boss = toaNpc(NpcID.AKKHA_MELEE, "Akkha"), shadow = toaNpc(NpcID.AKKHA_SHADOW, "Akkha's Shadow");
		Model model = mock(Model.class); when(shadow.getModel()).thenReturn(model);
		when(model.getOverrideAmount()).thenReturn((byte) 127);
		when(boss.getOverheadArchiveIds()).thenReturn(new int[]{SpriteID.HEADICONS_PRAYER});
		when(boss.getOverheadSpriteIds()).thenReturn(new short[]{2});
		toaWorld(14676, shadow, boss);
		tick(); assertEquals("Akkha: Immune To Magic", plugin.raidProgress.current().getEncounter());
		long revision = plugin.raidProgress.current().getRevision();
		tick(); assertEquals(revision, plugin.raidProgress.current().getRevision());
		for (int cycle = 0; cycle < 2; cycle++)
		{
			// The player still targets Akkha when his shadows become vulnerable.
			when(player.getInteracting()).thenReturn(boss); when(model.getOverrideAmount()).thenReturn((byte) 0);
			tick(); assertEquals("Akkha Shadows", plugin.raidProgress.current().getEncounter());
			revision = plugin.raidProgress.current().getRevision();
			tick(); assertEquals(revision, plugin.raidProgress.current().getRevision());
			// The shadow stays alive and targeted after it turns inactive; resume the boss's current form.
			when(player.getInteracting()).thenReturn(shadow); when(model.getOverrideAmount()).thenReturn((byte) 127);
			when(boss.getOverheadSpriteIds()).thenReturn(new short[]{1});
			tick(); assertEquals("Akkha: Immune To Ranged", plugin.raidProgress.current().getEncounter());
			when(player.getInteracting()).thenReturn(null); when(boss.getOverheadSpriteIds()).thenReturn(new short[]{0});
			tick(); assertEquals("Akkha: Immune To Melee", plugin.raidProgress.current().getEncounter());
		}
		// Enrage takes priority over any lingering ordinary shadows, even before its overhead arrives.
		when(boss.getId()).thenReturn(NpcID.AKKHA_ENRAGE); when(boss.getOverheadSpriteIds()).thenReturn(null);
		when(model.getOverrideAmount()).thenReturn((byte) 0); when(player.getInteracting()).thenReturn(shadow);
		tick(); assertEquals("Akkha: Final Stand (Melee Only)", plugin.raidProgress.current().getEncounter());
		message(ChatMessageType.GAMEMESSAGE, "Challenge complete: Akkha. Duration: 3:00.");
		String next = plugin.raidProgress.current().getEncounter();
		assertFalse(next.startsWith("Akkha"));
		tick(); assertEquals(next, plugin.raidProgress.current().getEncounter());
		assertEquals(0, searches.get()); assertEquals(42, plugin.searchGeneration.get());
		assertFalse(plugin.search.isCancelled());
	}

	@Test public void akkhaDetectionRequiresTheBossRoomAndDoesNotGuessFromUnloadedShadows() throws Exception
	{
		setPlan(RaidPlanType.TOA);
		NPC boss = toaNpc(NpcID.AKKHA_MELEE, "Akkha"), shadow = toaNpc(NpcID.AKKHA_SHADOW, "Akkha's Shadow");
		Model model = mock(Model.class); when(shadow.getModel()).thenReturn(model);
		when(model.getOverrideAmount()).thenReturn((byte) 127);
		when(boss.getOverheadArchiveIds()).thenReturn(new int[]{SpriteID.HEADICONS_PRAYER});
		when(boss.getOverheadSpriteIds()).thenReturn(new short[]{2});
		toaWorld(14160, boss, shadow); tick();
		assertEquals(plan.getStartingEncounter().getLabel(), plugin.raidProgress.current().getEncounter());
		toaWorld(14676, boss, shadow); tick();
		assertEquals("Akkha: Immune To Magic", plugin.raidProgress.current().getEncounter());
		when(shadow.getModel()).thenReturn(null); when(boss.getOverheadSpriteIds()).thenReturn(new short[]{1});
		long revision = plugin.raidProgress.current().getRevision();
		tick(); assertEquals(revision, plugin.raidProgress.current().getRevision());
		when(shadow.getModel()).thenReturn(model); when(model.getOverrideAmount()).thenReturn((byte) 0);
		tick(); assertEquals("Akkha Shadows", plugin.raidProgress.current().getEncounter());
		when(shadow.isDead()).thenReturn(true);
		tick(); assertEquals("Akkha: Immune To Ranged", plugin.raidProgress.current().getEncounter());
		assertEquals(0, searches.get());
	}

	private NPC toaNpc(int id, String name)
	{
		NPC npc = mock(NPC.class); NPCComposition composition = mock(NPCComposition.class);
		when(npc.getId()).thenReturn(id); when(npc.getName()).thenReturn(name); when(npc.getHealthRatio()).thenReturn(-1);
		when(npc.getTransformedComposition()).thenReturn(composition);
		when(composition.getActions()).thenReturn(new String[]{"Attack"});
		return npc;
	}

	@Test public void bothWardensSwitchP2GearBeforeAttackingAndIgnoreStaleCoreTargets() throws Exception
	{
		int[][] ids = {
			{NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE, NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE,
				NpcID.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED, NpcID.TOA_WARDEN_ELIDINIS_CORE},
			{NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE, NpcID.TOA_WARDEN_TUMEKEN_PHASE2_RANGE,
				NpcID.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED, NpcID.TOA_WARDEN_TUMEKEN_CORE}};
		for (int branch = 0; branch < ids.length; branch++)
		{
			plugin.raidProgress.reset(); setPlan(RaidPlanType.TOA);
			String name = branch == 0 ? "Elidinis" : "Tumeken";
			NPC boss = toaNpc(ids[branch][0], branch == 0 ? "Elidinis' Warden" : "Tumeken's Warden");
			NPC core = toaNpc(ids[branch][3], "<col=00ffff>Core</col>"); when(core.isDead()).thenReturn(true);
			when(player.getInteracting()).thenReturn(null);
			toaWorld(14160, boss, core); tick();
			assertEquals("Loading unrelated NPCs outside the boss room must not select P2", plan.getStartingEncounter().getLabel(),
				plugin.raidProgress.current().getEncounter());
			toaWorld(15184, boss, core); tick();
			assertEquals(name + " P2: Magic", plugin.raidProgress.current().getEncounter());
			for (int cycle = 0; cycle < 2; cycle++)
			{
				when(boss.getId()).thenReturn(ids[branch][1]); tick();
				assertEquals(name + " P2: Ranged", plugin.raidProgress.current().getEncounter());
				long revision = plugin.raidProgress.current().getRevision();
				tick(); assertEquals(revision, plugin.raidProgress.current().getRevision());
				// Shield HP is zero before the core has landed; still open core gear immediately.
				when(boss.getId()).thenReturn(ids[branch][2]); when(boss.getHealthRatio()).thenReturn(0);
				when(player.getInteracting()).thenReturn(boss); tick();
				assertEquals(name + " Core", plugin.raidProgress.current().getEncounter());
				when(core.isDead()).thenReturn(false); tick();
				assertEquals(name + " Core", plugin.raidProgress.current().getEncounter());
				// The core can linger while the shield reforms, and remains the player's stale target.
				when(player.getInteracting()).thenReturn(core);
				when(boss.getHealthRatio()).thenReturn(-1); when(boss.getId()).thenReturn(ids[branch][0]); tick();
				assertEquals(name + " P2: Magic", plugin.raidProgress.current().getEncounter());
				when(core.isDead()).thenReturn(true);
			}
		}
		assertEquals(0, searches.get()); assertEquals(42, plugin.searchGeneration.get());
		assertFalse(plugin.search.isCancelled());
	}

	@SuppressWarnings("unchecked")
	private void toaWorld(int region, NPC... actors)
	{
		IndexedObjectSet<NPC> npcs = mock(IndexedObjectSet.class);
		when(npcs.iterator()).thenAnswer(call -> Arrays.asList(actors).iterator());
		WorldView world = mock(WorldView.class); when(world.npcs()).thenReturn((IndexedObjectSet) npcs);
		when(client.getTopLevelWorldView()).thenReturn(world); when(client.getWorldView(WorldView.TOPLEVEL)).thenReturn(world);
		// The allocated instance is elsewhere; detection must read its source template region.
		when(world.isInstance()).thenReturn(true);
		int template = ((region >> 8) * 8 << 14) | ((region & 255) * 8 << 3);
		when(world.getInstanceTemplateChunks()).thenReturn(new int[][][]{{{template}}});
		when(player.getLocalLocation()).thenReturn(new LocalPoint(64, 64, WorldView.TOPLEVEL));
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

	@Test public void coxVanguardsAdvanceToPortalGearAndPortalDeathOpensTightrope() throws Exception
	{
		setPlan(RaidPlanType.COX_CM);
		bits.put(VarbitID.RAIDS_CLIENT_INDUNGEON, 1); bits.put(VarbitID.RAIDS_CLIENT_PROGRESS, 1);
		bits.put(VarbitID.RAIDS_CHALLENGE_MODE, 1); tick();
		for (String label : new String[]{"Vanguard: Melee", "Vanguard: Ranged", "Vanguard: Magic"})
		{
			NPC vanguard = mock(NPC.class);
			when(vanguard.getId()).thenReturn(encounter(label).getTarget().getId());
			when(vanguard.getName()).thenReturn("Vanguard");
			plugin.onActorDeath(new ActorDeath(vanguard));
		}
		tick();
		assertEquals("Vespula Portal", plugin.raidProgress.current().getEncounter());
		verify(plugin.panel).showRaidProgress(eq(RaidPlanType.COX_CM), eq("Vespula Portal"), anyString());
		verify(plugin.panel, never()).showRaidProgress(eq(RaidPlanType.COX_CM), eq("Vespula"), anyString());
		NPC portal = mock(NPC.class);
		when(portal.getId()).thenReturn(encounter("Vespula Portal").getTarget().getId());
		when(portal.getName()).thenReturn("Abyssal portal");
		plugin.onActorDeath(new ActorDeath(portal)); tick();
		assertEquals("Tightrope Mage", plugin.raidProgress.current().getEncounter());
		assertEquals(0, searches.get());
		assertEquals(42, plugin.searchGeneration.get());
		assertFalse(plugin.search.isCancelled());
	}

	@SuppressWarnings("unchecked")
	@Test public void bothGuardianDeathsOpenVasaGearWithoutTargetingVasa() throws Exception
	{
		setPlan(RaidPlanType.COX_CM);
		bits.put(VarbitID.RAIDS_CLIENT_INDUNGEON, 1); bits.put(VarbitID.RAIDS_CLIENT_PROGRESS, 1);
		bits.put(VarbitID.RAIDS_CHALLENGE_MODE, 1); tick();
		NPC left = mock(NPC.class), right = mock(NPC.class);
		when(left.getId()).thenReturn(NpcID.RAIDS_STONEGUARDIANS_LEFT);
		when(right.getId()).thenReturn(NpcID.RAIDS_STONEGUARDIANS_RIGHT);
		NPCComposition composition = mock(NPCComposition.class);
		when(composition.getActions()).thenReturn(new String[]{"Attack"});
		for (NPC guardian : Arrays.asList(left, right))
		{
			when(guardian.getName()).thenReturn("Guardian"); when(guardian.getHealthRatio()).thenReturn(100);
			when(guardian.getTransformedComposition()).thenReturn(composition);
		}
		IndexedObjectSet<NPC> npcs = mock(IndexedObjectSet.class);
		when(npcs.iterator()).thenAnswer(call -> Arrays.asList(left, right).iterator());
		WorldView world = mock(WorldView.class); when(world.npcs()).thenReturn((IndexedObjectSet) npcs);
		when(client.getTopLevelWorldView()).thenReturn(world);
		when(player.getInteracting()).thenReturn(left); tick();
		assertEquals("Guardians", plugin.raidProgress.current().getEncounter());
		plugin.onActorDeath(new ActorDeath(left)); when(left.isDead()).thenReturn(true); tick();
		assertEquals("One Guardian remains", "Guardians", plugin.raidProgress.current().getEncounter());
		plugin.onActorDeath(new ActorDeath(left)); tick();
		assertEquals("A duplicate death must not advance", "Guardians", plugin.raidProgress.current().getEncounter());
		plugin.onActorDeath(new ActorDeath(right)); when(right.isDead()).thenReturn(true); tick();
		assertEquals("Vasa", plugin.raidProgress.current().getEncounter());
		verify(plugin.panel).showRaidProgress(eq(RaidPlanType.COX_CM), eq("Vasa"), anyString());
		long revision = plugin.raidProgress.current().getRevision();
		tick(); assertEquals(revision, plugin.raidProgress.current().getRevision());
		assertEquals(0, searches.get()); assertEquals(42, plugin.searchGeneration.get());
		assertFalse(plugin.search.isCancelled());
	}

	@SuppressWarnings("unchecked")
	@Test public void vasaPhasesSwitchGearBeforeReclickingAndIgnoreStaleTargets() throws Exception
	{
		setPlan(RaidPlanType.COX_CM);
		bits.put(VarbitID.RAIDS_CLIENT_INDUNGEON, 1); bits.put(VarbitID.RAIDS_CLIENT_PROGRESS, 1);
		bits.put(VarbitID.RAIDS_CHALLENGE_MODE, 1); tick();
		NPC vasa = mock(NPC.class), crystal = mock(NPC.class);
		when(vasa.getId()).thenReturn(NpcID.RAIDS_VASANISTIRIO_WALKING);
		when(vasa.getName()).thenReturn("Vasa Nistirio"); when(vasa.getHealthRatio()).thenReturn(-1);
		when(crystal.getId()).thenReturn(NpcID.RAIDS_VASANISTIRIO_CRYSTAL);
		when(crystal.getName()).thenReturn("Glowing crystal"); when(crystal.getHealthRatio()).thenReturn(-1);
		NPCComposition composition = mock(NPCComposition.class);
		when(composition.getActions()).thenReturn(new String[]{"Attack"});
		when(vasa.getTransformedComposition()).thenReturn(composition);
		when(crystal.getTransformedComposition()).thenReturn(composition);
		IndexedObjectSet<NPC> npcs = mock(IndexedObjectSet.class);
		when(npcs.iterator()).thenAnswer(call -> Arrays.asList(crystal, vasa).iterator());
		WorldView world = mock(WorldView.class); when(world.npcs()).thenReturn((IndexedObjectSet) npcs);
		when(client.getTopLevelWorldView()).thenReturn(world);
		// Merely loading the NPCs must not pull gear away from the current room.
		tick(); assertEquals("Tekton", plugin.raidProgress.current().getEncounter());
		when(player.getInteracting()).thenReturn(vasa); tick();
		assertEquals("Vasa", plugin.raidProgress.current().getEncounter());
		for (int cycle = 0; cycle < 2; cycle++)
		{
			// Vasa starts healing while the player still has Vasa targeted.
			when(vasa.getId()).thenReturn(NpcID.RAIDS_VASANISTIRIO_HEALING);
			when(player.getInteracting()).thenReturn(vasa); tick();
			assertEquals("Vasa Crystal", plugin.raidProgress.current().getEncounter());
			long revision = plugin.raidProgress.current().getRevision();
			tick(); assertEquals("An unchanged phase must not refresh gear", revision, plugin.raidProgress.current().getRevision());
			when(player.getInteracting()).thenReturn(null); tick();
			assertEquals("Vasa Crystal", plugin.raidProgress.current().getEncounter());
			// The crystal need not die: Vasa can resume while it is still targeted and alive.
			when(vasa.getId()).thenReturn(NpcID.RAIDS_VASANISTIRIO_WALKING);
			when(player.getInteracting()).thenReturn(crystal); tick();
			assertEquals("Vasa", plugin.raidProgress.current().getEncounter());
			revision = plugin.raidProgress.current().getRevision();
			tick(); assertEquals(revision, plugin.raidProgress.current().getRevision());
		}
		// Crystal death does not complete Vasa's room; Vasa's own death does.
		when(vasa.getId()).thenReturn(NpcID.RAIDS_VASANISTIRIO_HEALING);
		tick(); plugin.onActorDeath(new ActorDeath(crystal)); when(crystal.isDead()).thenReturn(true);
		when(vasa.getId()).thenReturn(NpcID.RAIDS_VASANISTIRIO_WALKING); tick();
		assertEquals("Vasa", plugin.raidProgress.current().getEncounter());
		plugin.onActorDeath(new ActorDeath(vasa)); when(vasa.isDead()).thenReturn(true); tick();
		assertEquals("Mystics", plugin.raidProgress.current().getEncounter());
		verify(plugin.panel, atLeastOnce()).showRaidProgress(eq(RaidPlanType.COX_CM), eq("Vasa Crystal"), anyString());
		assertEquals(0, searches.get()); assertEquals(42, plugin.searchGeneration.get());
		assertFalse(plugin.search.isCancelled());
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
