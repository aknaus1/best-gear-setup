package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.EncounterPhase;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class LiveRaidTest
{
	@Test public void wardensP2FormsChooseTheDamageStyleAndCoreForBothBranches()
	{
		int[][] ids = {
			{NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE, NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE,
				NpcID.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED, NpcID.TOA_WARDEN_ELIDINIS_CORE},
			{NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE, NpcID.TOA_WARDEN_TUMEKEN_PHASE2_RANGE,
				NpcID.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED, NpcID.TOA_WARDEN_TUMEKEN_CORE}};
		for (int branch = 0; branch < ids.length; branch++)
		{
			String name = branch == 0 ? "Elidinis" : "Tumeken";
			NPC boss = akkhaNpc(ids[branch][0]), core = akkhaNpc(ids[branch][3]);
			assertEquals(name + " P2: Magic", LiveRaid.wardenTarget(Arrays.asList(core, boss)).getLabel());
			when(boss.getId()).thenReturn(ids[branch][1]);
			assertEquals(name + " P2: Ranged", LiveRaid.wardenTarget(Arrays.asList(boss, core)).getLabel());
			when(boss.getId()).thenReturn(ids[branch][2]); when(boss.getHealthRatio()).thenReturn(0);
			LiveRaid.WardenTarget exposed = LiveRaid.wardenTarget(Arrays.asList(boss, core));
			assertEquals(name + " Core", exposed.getLabel()); assertSame(core, exposed.getNpc());
			when(core.isDead()).thenReturn(true);
			assertNull("Core gear is shown while the core is still being ejected", LiveRaid.wardenTarget(Arrays.asList(boss, core)).getNpc());
			when(boss.isDead()).thenReturn(true);
			assertNull(LiveRaid.wardenTarget(Arrays.asList(boss, core)));
		}
		assertNull(LiveRaid.wardenTarget(Collections.singletonList(akkhaNpc(NpcID.TOA_WARDEN_ELIDINIS_PHASE1))));
		assertNull(LiveRaid.wardenTarget(Collections.singletonList(akkhaNpc(NpcID.TOA_WARDEN_ELIDINIS_PHASE3))));
	}

	@Test public void akkhaUsesLivePrayerIconsAndExplicitFinalStandForms()
	{
		NPC boss = akkhaNpc(NpcID.AKKHA_MELEE);
		when(boss.getOverheadArchiveIds()).thenReturn(new int[]{-1, 123, SpriteID.HEADICONS_PRAYER});
		when(boss.getOverheadSpriteIds()).thenReturn(new short[]{-1, 0, 2});
		assertEquals(EncounterPhase.AKKHA_IMMUNE_MAGIC, LiveRaid.akkhaPhase(boss));
		when(boss.getOverheadArchiveIds()).thenReturn(new int[]{SpriteID.HEADICONS_PRAYER, SpriteID.HEADICONS_PRAYER});
		when(boss.getOverheadSpriteIds()).thenReturn(new short[]{1, 2});
		assertEquals(EncounterPhase.AKKHA_ENRAGE, LiveRaid.akkhaPhase(boss));
		when(boss.getOverheadSpriteIds()).thenReturn(null);
		assertNull(LiveRaid.akkhaPhase(boss));
		for (int id : new int[]{NpcID.AKKHA_ENRAGE_INITIAL, NpcID.AKKHA_ENRAGE})
		{
			when(boss.getId()).thenReturn(id);
			assertEquals(EncounterPhase.AKKHA_ENRAGE, LiveRaid.akkhaPhase(boss));
		}
	}

	@Test public void akkhaShadowsRequireVisibleVulnerabilityAndNeverOverrideFinalStand()
	{
		NPC boss = akkhaNpc(NpcID.AKKHA_MELEE), shadow = akkhaNpc(NpcID.AKKHA_SHADOW);
		Model model = mock(Model.class);
		when(shadow.getModel()).thenReturn(model);
		when(model.getOverrideAmount()).thenReturn((byte) 127);
		assertSame(boss, LiveRaid.akkhaTarget(Arrays.asList(shadow, boss)));
		when(model.getOverrideAmount()).thenReturn((byte) 0);
		assertSame(shadow, LiveRaid.akkhaTarget(Arrays.asList(boss, shadow)));
		when(shadow.isDead()).thenReturn(true);
		assertSame(boss, LiveRaid.akkhaTarget(Arrays.asList(boss, shadow)));
		when(shadow.isDead()).thenReturn(false); when(shadow.getHealthRatio()).thenReturn(0);
		assertSame(boss, LiveRaid.akkhaTarget(Arrays.asList(boss, shadow)));
		when(shadow.getHealthRatio()).thenReturn(-1); when(shadow.getModel()).thenReturn(null);
		assertNull("An unloaded model must not be treated as an active or inactive shadow",
			LiveRaid.akkhaTarget(Arrays.asList(boss, shadow)));
		when(boss.getId()).thenReturn(NpcID.AKKHA_ENRAGE);
		assertSame(boss, LiveRaid.akkhaTarget(Arrays.asList(shadow, boss)));
		when(shadow.getModel()).thenReturn(model);
		assertSame(boss, LiveRaid.akkhaTarget(Arrays.asList(boss, shadow)));
	}

	@Test public void akkhaIgnoresSpawnFormsAndFinalStandDummies()
	{
		for (int id : new int[]{NpcID.AKKHA_SPAWN, NpcID.AKKHA_ENRAGE_SPAWN, NpcID.AKKHA_ENRAGE_DUMMY,
			NpcID.AKKHA_SHADOW_ENRAGE, NpcID.AKKHA_SHADOW_ENRAGE_DUMMY, NpcID.TOA_WARDENS_AKKHA})
		{
			assertNull(LiveRaid.akkhaTarget(Collections.singletonList(akkhaNpc(id))));
		}
		assertNull(LiveRaid.akkhaTarget(Collections.emptyList()));
	}

	private NPC akkhaNpc(int id)
	{
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(id); when(npc.getName()).thenReturn("Akkha");
		when(npc.getHealthRatio()).thenReturn(-1);
		when(npc.getOverheadArchiveIds()).thenReturn(new int[]{SpriteID.HEADICONS_PRAYER});
		when(npc.getOverheadSpriteIds()).thenReturn(new short[]{0});
		return npc;
	}

	@Test public void progressNeedsAStartedRaidInsteadOfLobbyPartyMembership()
	{
		Map<Integer, Integer> bits = new HashMap<>(); Client client = client(bits);
		bits.put(VarbitID.TOB_CLIENT_PARTYSTATUS, 1);
		assertFalse(LiveRaid.inProgress(client, RaidPlanType.TOB, -1));
		bits.put(VarbitID.TOB_CLIENT_PARTYSTATUS, 2);
		assertTrue(LiveRaid.inProgress(client, RaidPlanType.TOB, -1));
		bits.put(VarbitID.TOB_CLIENT_PARTYSTATUS, 3);
		assertTrue("Death while spectating the party does not reset completed rooms", LiveRaid.inProgress(client, RaidPlanType.TOB, -1));
		bits.put(VarbitID.RAIDS_CLIENT_INDUNGEON, 1); bits.put(VarbitID.RAIDS_CHALLENGE_MODE, 1);
		assertFalse(LiveRaid.inProgress(client, RaidPlanType.COX_CM, -1));
		bits.put(VarbitID.RAIDS_CLIENT_PROGRESS, 1);
		assertTrue(LiveRaid.inProgress(client, RaidPlanType.COX_CM, -1));
		bits.put(VarbitID.RAIDS_CHALLENGE_MODE, 0);
		assertFalse("A normal CoX run cannot advance the CM plan", LiveRaid.inProgress(client, RaidPlanType.COX_CM, -1));
	}

	@Test public void toaProgressUsesTheActualInstanceRoom()
	{
		Client client = client(new HashMap<>());
		assertFalse(LiveRaid.inProgress(client, RaidPlanType.TOA, 13454));
		assertTrue(LiveRaid.inProgress(client, RaidPlanType.TOA, 14160));
		assertTrue(LiveRaid.inProgress(client, RaidPlanType.TOA, 15700));
		assertEquals("crondis", LiveRaid.toaRoom(15700));
		assertFalse(LiveRaid.toaPuzzle(15700));
		assertTrue(LiveRaid.toaPuzzle(15698));
	}

	private Client client(Map<Integer, Integer> bits)
	{
		Client client = mock(Client.class);
		when(client.getVarbitValue(anyInt())).thenAnswer(i -> bits.getOrDefault(i.<Integer>getArgument(0), 0));
		return client;
	}

	@Test public void nonRaidTargetsReadNothing()
	{
		MonsterSummary target = new MonsterSummary(); target.setName("turoth");
		LiveRaid live = LiveRaid.read(client(new HashMap<>()), target);
		assertFalse(live.raidDetected);
		assertTrue(live.values.isEmpty());
	}

	@Test public void toaInputsRequireActivePartyAndFollowSelectedBossPath()
	{
		MonsterSummary target = new MonsterSummary(); target.setName("kephri");
		target.setAttributes(java.util.Collections.singletonList("tombs of amascut"));
		Map<Integer,Integer> bits = new HashMap<>();
		bits.put(VarbitID.TOA_CLIENT_PARTYSTATUS,2); bits.put(VarbitID.TOA_CLIENT_P0,1); bits.put(VarbitID.TOA_CLIENT_P1,2);
		bits.put(VarbitID.TOA_CLIENT_RAID_LEVEL,350); bits.put(VarbitID.TOA_CLIENT_SCABARAS_LEVEL,3);
		LiveRaid live = LiveRaid.read(client(bits), target);
		assertTrue(live.raidDetected); assertEquals(2,live.values.get("raidPartySize"));
		assertEquals(350,live.values.get("toaRaidLevel")); assertEquals(3,live.values.get("toaPathLevel"));
		bits.clear(); live = LiveRaid.read(client(bits), target);
		assertFalse(live.raidDetected); assertTrue(live.values.isEmpty());
	}
}
