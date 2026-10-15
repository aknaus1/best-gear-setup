package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.bestgearsetup.data.MonsterSummary;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class LiveRaidTest
{
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
