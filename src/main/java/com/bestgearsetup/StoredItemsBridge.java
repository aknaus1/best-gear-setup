package com.bestgearsetup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

/** Optional integration with DWMS's documented version 1 storage messages; no plugin dependency. */
final class StoredItemsBridge
{
	static final String NAMESPACE = "dudewheresmystuff";
	static final String SOURCE = "Best Gear Setup";
	/**
	 * Ticks between unprompted refreshes (about 30 seconds). Storing or retrieving items moves them through the
	 * inventory, which requests at once; this only catches changes DWMS learns of without a container change.
	 */
	static final int POLL_TICKS = 49;
	private final ConfigManager profiles;
	private final EventBus eventBus;
	private final OwnedItems owned;
	private boolean pending;
	private String profile;
	private List<StoredLocation> response;
	private int ticks;

	StoredItemsBridge(ConfigManager profiles, EventBus eventBus, OwnedItems owned)
	{
		this.profiles = profiles;
		this.eventBus = eventBus;
		this.owned = owned;
	}

	/** Client thread, after DWMS has handled this tick. Transfers always request a fresh snapshot. */
	boolean request(boolean containersChanged)
	{
		if (pending || !containersChanged && ticks-- > 0) { return false; }
		profile = profiles.getRSProfileKey();
		if (profile == null) { return false; }
		ticks = POLL_TICKS;
		pending = true;
		response = null;
		eventBus.post(new PluginMessage(NAMESPACE, "storages-request", Collections.singletonMap("source", SOURCE)));
		return true;
	}

	@Subscribe
	public void onPluginMessage(PluginMessage message)
	{
		Map<String, Object> data = message.getData();
		if (!pending || !Objects.equals(profile, profiles.getRSProfileKey())
			|| !NAMESPACE.equals(message.getNamespace()) || !"storages-response".equals(message.getName())
			|| data == null || !SOURCE.equals(data.get("target"))
			|| !"Dude, Where's My Stuff?".equals(data.get("source"))
			|| !Integer.valueOf(1).equals(data.get("version"))) { return; }
		List<StoredLocation> parsed = parse(data.get("storages"));
		if (parsed != null) { response = parsed; }
	}

	/** Queued after the provider's reply; never compare a half-completed storage transfer. */
	boolean complete()
	{
		if (!pending) { return false; }
		pending = false;
		if (!Objects.equals(profile, profiles.getRSProfileKey())) { return false; }
		return owned.setStoredLocations(response == null ? Collections.emptyList() : response, response != null);
	}

	void reset()
	{
		pending = false;
		response = null;
		profile = null;
		ticks = 0;
		owned.setStoredLocations(Collections.emptyList(), false);
		owned.rebuild(true);
	}

	/** Null denotes an unsupported payload; an empty list is an authoritative empty snapshot. */
	static List<StoredLocation> parse(Object payload)
	{
		if (!(payload instanceof List)) { return null; }
		List<StoredLocation> locations = new ArrayList<>();
		for (Object entry : (List<?>) payload)
		{
			if (!(entry instanceof Map)) { return null; }
			Map<?, ?> storage = (Map<?, ?>) entry;
			if (!(storage.get("category") instanceof String) || !(storage.get("name") instanceof String)
				|| !(storage.get("items") instanceof List)) { return null; }
			String category = (String) storage.get("category");
			String name = (String) storage.get("name");
			if (!include(category, name)) { continue; }
			Map<Integer, Long> items = new HashMap<>();
			for (Object item : (List<?>) storage.get("items"))
			{
				if (!(item instanceof Map)) { return null; }
				Map<?, ?> stack = (Map<?, ?>) item;
				if (!(stack.get("id") instanceof Integer) || !(stack.get("quantity") instanceof Long)) { return null; }
				int id = (Integer) stack.get("id");
				long quantity = (Long) stack.get("quantity");
				if (id > 0 && quantity > 0) { items.merge(id, quantity, OwnedItems::saturatedAdd); }
			}
			if (!items.isEmpty())
			{
				Object updated = storage.get("lastUpdated");
				locations.add(new StoredLocation(category, name, updated instanceof Long ? (Long) updated : -1, items));
			}
		}
		return Collections.unmodifiableList(locations);
	}

	private static boolean include(String category, String name)
	{
		if ("carryable".equals(category)) { return !"Inventory".equals(name) && !"Equipment".equals(name); }
		if ("world".equals(category)) { return !"Bank".equals(name) && !"Log Storage".equals(name); }
		if ("poh".equals(category))
		{
			// The message also includes pets, mounted displays and capes from potentially someone else's house.
			return name.startsWith("Treasure Chest (") || "Armour Case".equals(name)
				|| "Magic Wardrobe".equals(name) || "Fancy Dress Box".equals(name) || "Cape Rack".equals(name)
				|| "Toy Box".equals(name) || "Spice Rack".equals(name) || "Uncategorised".equals(name);
		}
		// DWMS excludes expired deathpiles and lost deathbanks from this response.
		return "stash".equals(category) || "death".equals(category) || "sailing".equals(category);
	}
}
