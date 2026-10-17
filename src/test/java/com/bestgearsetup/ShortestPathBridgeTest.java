package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class ShortestPathBridgeTest
{
	private static final WorldPoint VARROCK = new WorldPoint(3213, 3424, 0);
	private final EventBus eventBus = mock(EventBus.class);
	private final AtomicInteger answers = new AtomicInteger();
	private final ShortestPathBridge bridge = new ShortestPathBridge(eventBus, answers::incrementAndGet);

	@Test public void asksOnceForTheRouteFromThePlayersPosition()
	{
		bridge.update(TravelTeleports.ZULRAH, VARROCK);
		bridge.update(TravelTeleports.ZULRAH, VARROCK);
		PluginMessage query = lastMessage(1);
		assertEquals("shortestpath", query.getNamespace());
		assertEquals("query", query.getName());
		assertEquals(VARROCK, query.getData().get("start"));
		assertEquals(TravelTeleports.ZULRAH.getTarget(), query.getData().get("target"));
		assertTrue(((String) query.getData().get("id")).startsWith("bestgearsetup:"));
	}

	@Test public void readsTheAnswerToItsOwnQueryOnly()
	{
		bridge.update(TravelTeleports.ZULRAH, VARROCK);
		Object id = lastMessage(1).getData().get("id");
		bridge.onPluginMessage(new PluginMessage("shortestpath", "result", result("someone-else")));
		bridge.onPluginMessage(new PluginMessage("other", "result", result(id)));
		assertNull(bridge.getRoute());
		bridge.onPluginMessage(new PluginMessage("shortestpath", "result", result(id)));
		assertEquals(1, answers.get());
		ShortestPathBridge.Route route = bridge.getRoute();
		assertEquals(Collections.singletonList("Zul-andra teleport: Zul-Andra"), route.getSteps());
		assertEquals(42, route.getCost());
		assertTrue(route.isReached());
		assertEquals(route.getSteps(), bridge.stepsFor(TravelTeleports.ZULRAH));
		assertTrue(bridge.stepsFor(TravelTeleports.VORKATH).isEmpty());
	}

	@Test public void asksAgainOnlyAfterMovingWellAway()
	{
		bridge.update(TravelTeleports.ZULRAH, VARROCK);
		bridge.onPluginMessage(new PluginMessage("shortestpath", "result", result(lastMessage(1).getData().get("id"))));
		bridge.update(TravelTeleports.ZULRAH, VARROCK.dx(ShortestPathBridge.REQUERY_TILES - 1));
		verify(eventBus, times(1)).post(any());
		bridge.update(TravelTeleports.ZULRAH, VARROCK.dx(ShortestPathBridge.REQUERY_TILES));
		verify(eventBus, times(2)).post(any());
	}

	@Test public void anUnansweredQueryTimesOutWithoutRepeatingFromTheSameSpot()
	{
		bridge.update(TravelTeleports.ZULRAH, VARROCK);
		for (int i = 0; i < ShortestPathBridge.QUERY_TIMEOUT_TICKS + 5; i++) { bridge.update(TravelTeleports.ZULRAH, VARROCK); }
		verify(eventBus, times(1)).post(any());
		bridge.update(TravelTeleports.ZULRAH, VARROCK.dx(1));
		verify(eventBus, times(2)).post(any());
	}

	@Test public void showsThePathToTheDestination()
	{
		bridge.showPath(TravelTeleports.VORKATH);
		PluginMessage path = lastMessage(1);
		assertEquals("path", path.getName());
		assertEquals(TravelTeleports.VORKATH.getTarget(), path.getData().get("target"));
	}

	@Test public void parseToleratesMissingAndOddValues()
	{
		Map<String, Object> data = new HashMap<>();
		data.put("transports", Arrays.asList("not a map", Collections.singletonMap("displayInfo", " "),
			Collections.singletonMap("displayInfo", 7)));
		ShortestPathBridge.Route route = ShortestPathBridge.parse(data, TravelTeleports.ZULRAH, VARROCK);
		assertTrue(route.getSteps().isEmpty());
		assertEquals(-1, route.getCost());
		assertFalse(route.isReached());
	}

	private PluginMessage lastMessage(int expected)
	{
		ArgumentCaptor<PluginMessage> captor = ArgumentCaptor.forClass(PluginMessage.class);
		verify(eventBus, times(expected)).post(captor.capture());
		List<PluginMessage> all = captor.getAllValues();
		return all.get(all.size() - 1);
	}

	private static Map<String, Object> result(Object id)
	{
		Map<String, Object> transport = new HashMap<>();
		transport.put("origin", VARROCK);
		transport.put("destination", new WorldPoint(2193, 3060, 0));
		transport.put("objectInfo", null);
		transport.put("displayInfo", "Zul-andra teleport: Zul-Andra");
		Map<String, Object> data = new HashMap<>();
		data.put("id", id);
		data.put("reached", true);
		data.put("cost", 42);
		data.put("transports", Collections.singletonList(transport));
		return data;
	}
}
