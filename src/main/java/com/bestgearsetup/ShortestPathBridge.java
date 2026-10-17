package com.bestgearsetup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

/**
 * Optional integration with the Shortest Path plugin's documented cross-plugin messages (namespace
 * "shortestpath": "query" answered by "result", and "path" to show a route). There is no plugin dependency: with
 * Shortest Path missing or disabled, queries simply go unanswered. Never writes its config.
 */
final class ShortestPathBridge
{
	static final String NAMESPACE = "shortestpath";
	private static final String ID_PREFIX = "bestgearsetup:";
	/** Ticks before an unanswered query is given up and may be asked again. */
	static final int QUERY_TIMEOUT_TICKS = 50;
	/** Tiles the player must move from a route's start before the route is asked for again. */
	static final int REQUERY_TILES = 32;

	/** Shortest Path's answer for one destination. */
	@Value
	static class Route
	{
		TravelTeleports.Destination destination;
		WorldPoint start;
		/** Descriptions of the route's teleports and other transports, in order; may be empty. */
		List<String> steps;
		/** Ticks, or -1 when there's no path. */
		int cost;
		boolean reached;
	}

	private final EventBus eventBus;
	private final Runnable onRoute;
	private int sequence;
	private String pendingId;
	private TravelTeleports.Destination pendingDestination;
	private WorldPoint pendingStart;
	private int pendingTicks;
	private volatile Route route;

	ShortestPathBridge(EventBus eventBus, Runnable onRoute)
	{
		this.eventBus = eventBus;
		this.onRoute = onRoute;
	}

	/** The last answered route, for any destination; null when none. */
	Route getRoute()
	{
		return route;
	}

	/** The last route's steps when it is for this destination, else an empty list. */
	List<String> stepsFor(TravelTeleports.Destination destination)
	{
		Route last = route;
		return last != null && last.getDestination() == destination ? last.getSteps() : Collections.emptyList();
	}

	/**
	 * Client thread, once per tick: ask for a route when the destination is new, or the player has moved well away
	 * from where the last route started (e.g. after teleporting). At most one query is outstanding.
	 */
	void update(TravelTeleports.Destination destination, WorldPoint player)
	{
		if (pendingId != null && --pendingTicks > 0) { return; }
		pendingId = null;
		if (destination == null || player == null) { return; }
		Route last = route;
		if (last != null && last.getDestination() == destination && last.getStart() != null
			&& last.getStart().getPlane() == player.getPlane()
			&& Math.max(Math.abs(last.getStart().getX() - player.getX()),
				Math.abs(last.getStart().getY() - player.getY())) < REQUERY_TILES)
		{
			return;
		}
		if (pendingDestination == destination && player.equals(pendingStart)) { return; }
		pendingId = ID_PREFIX + (++sequence);
		pendingDestination = destination;
		pendingStart = player;
		pendingTicks = QUERY_TIMEOUT_TICKS;
		eventBus.post(new PluginMessage(NAMESPACE, "query", queryData(pendingId, player, destination)));
	}

	/** Ask Shortest Path to show the route to the destination; it draws and clears it with its own controls. */
	void showPath(TravelTeleports.Destination destination)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("target", destination.getTarget());
		eventBus.post(new PluginMessage(NAMESPACE, "path", data));
	}

	void reset()
	{
		pendingId = null;
		pendingDestination = null;
		pendingStart = null;
		route = null;
	}

	@Subscribe
	public void onPluginMessage(PluginMessage message)
	{
		if (!NAMESPACE.equals(message.getNamespace()) || !"result".equals(message.getName())) { return; }
		Map<String, Object> data = message.getData();
		String id = pendingId;
		if (id == null || data == null || !id.equals(data.get("id"))) { return; }
		pendingId = null;
		route = parse(data, pendingDestination, pendingStart);
		onRoute.run();
	}

	static Map<String, Object> queryData(String id, WorldPoint start, TravelTeleports.Destination destination)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("id", id);
		data.put("start", start);
		data.put("target", destination.getTarget());
		return data;
	}

	/** Reads only the documented keys, tolerating missing or unexpected values. */
	static Route parse(Map<String, Object> data, TravelTeleports.Destination destination, WorldPoint start)
	{
		List<String> steps = new ArrayList<>();
		Object transports = data.get("transports");
		if (transports instanceof List)
		{
			for (Object transport : (List<?>) transports)
			{
				if (!(transport instanceof Map)) { continue; }
				Object display = ((Map<?, ?>) transport).get("displayInfo");
				if (display instanceof String && !((String) display).trim().isEmpty()) { steps.add(((String) display).trim()); }
			}
		}
		Object cost = data.get("cost");
		return new Route(destination, start, Collections.unmodifiableList(steps),
			cost instanceof Number ? ((Number) cost).intValue() : -1, Boolean.TRUE.equals(data.get("reached")));
	}
}
