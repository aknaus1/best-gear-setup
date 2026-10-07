package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.MonsterSummary;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class LiveRaidTest
{
	private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler)
	{ return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler)); }

	private Client client(Map<Integer, Integer> bits)
	{
		return proxy(Client.class, (p,m,a) -> {
			switch (m.getName()) {
				case "getVarbitValue": return bits.getOrDefault((Integer) a[0],0);
				default: return null;
			}
		});
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
