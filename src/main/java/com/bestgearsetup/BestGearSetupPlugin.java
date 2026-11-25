package com.bestgearsetup;

import com.bestgearsetup.calc.*;
import com.bestgearsetup.data.*;
import com.bestgearsetup.ui.Sidebar;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.*;
import net.runelite.client.game.*;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Best Gear Setup",
	description = "Find the best gear setup for any monster, using your owned items or a budget",
	tags = {"gear", "setup", "dps", "bis", "best", "calculator", "bank", "budget", "boss", "slayer", "optimizer"}
)
public class BestGearSetupPlugin extends Plugin
{
	private static final String MENU_OPTION = "Best setup";
	/** The skills a potion can boost, each with the combat class whose potion setting applies to it. */
	private static final String[] BOOST_SKILLS = {"attack", "strength", "ranged", "magic"};
	private static final Combat[] BOOST_CLASSES = {Combat.MELEE, Combat.MELEE, Combat.RANGED, Combat.MAGIC};
	/** Settings that change neither a search nor the sidebar. */
	private static final List<String> NO_SEARCH_KEYS = Arrays.asList("showMenuOption",
		Options.HIGHLIGHT_BANK_GEAR_KEY, Options.BANK_HIGHLIGHT_COLOR_KEY,
		Options.HIGHLIGHT_INVENTORY_GEAR_KEY, Options.INVENTORY_HIGHLIGHT_COLOR_KEY);

	@Inject
	Client client;

	@Inject
	ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;


	@Inject
	Options config;

	@Inject
	private ConfigManager configManager;

	@Inject
	GameDataLoader api;

	/** The client's Gson; Plugin Hub builds reject plugins that create their own. */
	@Inject
	private Gson gson;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BestGearSetupBankOverlay bankOverlay;

	@Inject
	private BestGearSetupInventoryOverlay inventoryOverlay;

	@Inject
	private BestGearSetupBankView bankView;

	private BestGearSetupBankButton bankButton;

	@Inject
	private PartyService partyService;

	private LiveSpecs liveSpecs;
	/** A {@link PartySpecs}, held as Object so this class never loads the Special Attack Counter's classes. */
	private Object partySpecs;

	@Inject
	private EventBus eventBus;

	@Inject
	private ColorPickerManager colorPickerManager;

	/** Immutable selection snapshot shared between the sidebar and the bank and inventory renderers. */
	@Getter
	private volatile Set<Integer> bankHighlightIds = Collections.emptySet();

	@Getter
	@Inject
	OwnedItems ownedItems;

	Sidebar panel;
	private NavigationButton navButton;
	ExecutorService executor;
	@Getter
	volatile GameData gameData;
	volatile Future<?> search;
	final AtomicInteger searchGeneration = new AtomicInteger();
	/** Bumped on startup and shutdown so loading work queued by an earlier run cannot publish into this one. */
	final AtomicInteger lifecycle = new AtomicInteger();
	private volatile Map<Integer, Long> prices = Collections.emptyMap();
	volatile Summary lastSearched;
	/** Equipment ids and their variants: the only ownership changes that can alter a result. */
	volatile Set<Integer> gearIds = Collections.emptySet();
	/**
	 * Each style tab's setups by {@link SearchInputs}: repeating a search, toggling a style or rerunning after a
	 * presentation change reuses the tabs whose inputs are unchanged. An entry is its key's small parts and at most
	 * a few setups of catalogue items, a few KB; the large parts (prices, owned gear) are one copy per content (see
	 * {@link SearchInputs#SHARED}). 64 tabs is about ten searches of every style: normally under a megabyte, and
	 * only if every entry were searched under different Grand Exchange prices would each pin its own price map.
	 */
	final SearchCache<SearchInputs, List<Setup>> tabCache = new SearchCache<>(64);
	/** Last levels written to the profile; client thread only. */
	private String lastRememberedLevels;
	/** Ownership the latest search was computed with; null when no results depend on it. */
	volatile Set<Integer> searchOwned;
	/** The latest search's mode: Inventory + equipped only compares carried items alone. */
	volatile SearchMode searchMode;
	/** Stack sizes and ammo quantity the latest search was computed with. */
	volatile Map<Integer, Long> searchQuantities;
	volatile int searchAmmoCount;
	/**
	 * Stack sizes at the last relevant supply change since the search, or null if none; only a further change
	 * restarts the settle timer.
	 */
	private volatile Map<Integer, Long> observedQuantities;
	/** Ammunition and thrown weapons (and their variants): the stacks whose size can alter a result. */
	volatile Set<Integer> consumableIds = Collections.emptySet();
	/** Swing thread only: waits for a changing stack to settle (e.g. while firing) before searching again. */
	javax.swing.Timer supplyRefresh;
	/** How long a stack must stay unchanged before a quantity-only change reruns the search. */
	int supplySettleMillis = 5000;
	/** The bank, inventory or worn equipment changed since the last tick. Client thread only. */
	private boolean containersChanged;
	/** Optional Dude, Where's My Stuff? storage; null while the plugin is stopped. */
	StoredItemsBridge storedItemsBridge;
	/** Read and written on the client thread only. */
	private NPC lookupTarget;
	/** Swing thread only: whether this run has started loading the bundled data. */
	private boolean dataRequested;

	@Provides
	Options provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(Options.class);
	}

	@Override
	protected void startUp()
	{
		// Before any event handler can reach ownership rules (bank and inventory changes).
		OwnershipRules.init(gson);
		int life = lifecycle.incrementAndGet();
		// "Yama tank on Magic" (+60 Magic defence) became the default phase; drop the retired value.
		if ("YAMA_MAGIC_TANK".equals(configManager.getConfiguration(Options.GROUP,
			Options.PHASE_KEY)))
		{
			configManager.unsetConfiguration(Options.GROUP, Options.PHASE_KEY);
		}
		executor = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "best-gear-setup");
			t.setDaemon(true);
			// On machines with few cores the game's own threads must win: searching takes longer instead of
			// the client stuttering.
			t.setPriority(Thread.MIN_PRIORITY);
			return t;
		});
		migratePreferences();
		panel = new Sidebar(this, config, itemManager, spriteManager, skillIconManager);
		BufferedImage icon = Sidebar.createNavIcon();
		navButton = NavigationButton.builder()
			.tooltip("Best Gear Setup")
			.icon(icon)
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(bankOverlay);
		overlayManager.add(inventoryOverlay);
		eventBus.register(bankView);
		bankButton = new BestGearSetupBankButton(client, clientThread, bankView);
		eventBus.register(bankButton);
		bankButton.start();
		liveSpecs = new LiveSpecs(client, clientThread, config, this::liveSpecsChanged);
		eventBus.register(liveSpecs);
		try
		{
			Object bridge = new PartySpecs(client, clientThread, partyService, liveSpecs);
			eventBus.register(bridge);
			partySpecs = bridge;
		}
		catch (LinkageError e)
		{
			// The Special Attack Counter's classes are missing or changed: only own specials are tracked.
			log.warn("Party specs unavailable; tracking own specs only", e);
		}

		ownedItems.load();
		storedItemsBridge = new StoredItemsBridge(configManager, eventBus, ownedItems);
		eventBus.register(storedItemsBridge);
		panel.updateOwnedStatus();
		// Enabled while logged in: no container event fires until something changes, so read them now.
		clientThread.invokeLater(this::readLiveContainers);
		// Loading waits for the game cache (login screen), so live wear levels can be read; see onGameStateChanged.
		dataRequested = false;
		if (cacheReady())
		{
			requestGameData();
		}
	}

	@Override
	protected void shutDown()
	{
		lifecycle.incrementAndGet();
		searchGeneration.incrementAndGet();
		// Release the setups (and through them the catalogue) and stop running searches from storing more.
		tabCache.clear();
		SearchInputs.SHARED.clear();
		overlayManager.remove(bankOverlay);
		overlayManager.remove(inventoryOverlay);
		eventBus.unregister(bankView);
		eventBus.unregister(bankButton);
		bankButton.stop();
		bankButton = null;
		eventBus.unregister(liveSpecs);
		liveSpecs = null;
		StoredItemsBridge bridge = storedItemsBridge;
		storedItemsBridge = null;
		eventBus.unregister(bridge);
		// The bridge and settling ownership belong to the client thread.
		clientThread.invoke(bridge::reset);
		if (partySpecs != null)
		{
			eventBus.unregister(partySpecs);
			partySpecs = null;
		}
		setBankHighlightedSetup(null);
		// The lifecycle guard skips the usual refresh: release the layout and restore dragging unconditionally.
		clientThread.invokeLater(bankView::clear);
		clientToolbar.removeNavigation(navButton);
		// Running work stops itself on the lifecycle and search generations bumped above.
		executor.shutdown();
		executor = null;
		panel = null;
		navButton = null;
		gameData = null;
		gearIds = Collections.emptySet();
		consumableIds = Collections.emptySet();
		searchOwned = null;
		searchQuantities = null;
		observedQuantities = null;
		lastSearched = null;
		lookupTarget = null;
		if (supplyRefresh != null)
		{
			supplyRefresh.stop();
		}
	}

	/** Swing thread, later: act on the panel unless the plugin has stopped by then. */
	private void onPanel(Consumer<Sidebar> action)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				action.accept(panel);
			}
		});
	}

	/** As {@link #onPanel(Consumer)}, and only while the search of this generation is still the current one. */
	private void onPanel(int generation, Consumer<Sidebar> action)
	{
		onPanel(p ->
		{
			if (generation == searchGeneration.get())
			{
				action.accept(p);
			}
		});
	}

	private boolean cacheReady()
	{
		return client.getGameState().getState() >= GameState.LOGIN_SCREEN.getState();
	}

	/**
	 * Swing thread: load the bundled data once per plugin run. A plugin enabled at client launch starts before
	 * the game cache has loaded, so loading waits until the client reaches the login screen.
	 */
	private void requestGameData()
	{
		if (executor == null || dataRequested)
		{
			return;
		}
		dataRequested = true;
		int life = lifecycle.get();
		Sidebar target = panel;
		executor.submit(() -> loadGameData(life, target));
	}

	/**
	 * Load the bundled data for one plugin run. Results are published on the Swing thread, where startup and
	 * shutdown also run, and only while that run is current: a later run never receives an earlier run's data.
	 */
	private void loadGameData(int life, Sidebar target)
	{
		try
		{
			GameData bundled = api.loadGameData();
			// Build ownership identities here, from the catalogue just read, off the Swing and client threads.
			OwnershipRules.load();
			GameData data = prepareGameData(bundled);
			if (life != lifecycle.get() || data == null)
			{
				return;
			}
			// Without live cache identities, folded cosmetics are neither listed nor owned: retry on the next state change.
			boolean incomplete = data == bundled;
			Set<Integer> gear = gearIds(data);
			Set<Integer> consumables = consumableIds(data);
			SwingUtilities.invokeLater(() ->
			{
				if (life == lifecycle.get())
				{
					// Searches of the earlier catalogue can no longer read or store tabs (see findBestSetup).
					tabCache.clear();
					gameData = data;
					gearIds = gear;
					consumableIds = consumables;
					target.onDataLoaded(data);
					if (incomplete)
					{
						dataRequested = false;
					}
				}
			});
		}
		catch (IOException | RuntimeException e)
		{
			if (life != lifecycle.get())
			{
				return;
			}
			log.warn("Unable to load game data", e);
			SwingUtilities.invokeLater(() ->
			{
				if (life == lifecycle.get())
				{
					target.showLoadError("Could not read the bundled game data: " + e.getMessage());
				}
			});
		}
	}

	static Set<Integer> gearIds(GameData data)
	{
		return idsWithVariants(data, item -> true);
	}

	static Set<Integer> consumableIds(GameData data)
	{
		return idsWithVariants(data, Weapons::consumedPerAttack);
	}

	private static Set<Integer> idsWithVariants(GameData data, Predicate<Gear> wanted)
	{
		Set<Integer> ids = new HashSet<>();
		for (Slot slot : Slot.values())
		{
			for (Gear item : data.getItems(slot))
			{
				if (wanted.test(item))
				{
					ids.add(item.getId());
					ids.addAll(item.getVariants());
				}
			}
		}
		return Collections.unmodifiableSet(ids);
	}

	/**
	 * Raise bundled wear levels to any stricter level in the live game cache, so a game update that changes an
	 * item's parameters takes effect without a new snapshot. Runs before the data is published; the cache is
	 * read on the client thread and skipped if the client is not ready.
	 *
	 * @return prepared catalogue, or null if waiting for the client thread was interrupted
	 */
	private GameData prepareGameData(GameData data)
	{
		if (!cacheReady())
		{
			log.debug("Game cache not loaded; using the bundled wear levels");
			return data;
		}
		CompletableFuture<GameData> read = new CompletableFuture<>();
		clientThread.invoke(() ->
		{
			try
			{
				// Prepare a separate snapshot: a timed-out or stale callback cannot mutate published data.
				GameData prepared = data.withMonsters(gson, data.getMonsters());
				prepared.addVariantItems(gson, id -> client.getItemDefinition(id).getName(),
					id -> client.getItemDefinition(id).isTradeable());
				int raised = 0;
				for (Slot slot : Slot.values())
				{
					for (Gear item : prepared.getItems(slot))
					{
						raised += EquipmentRequirements.get().resolve(item, client.getItemDefinition(item.getId()))
							.raise(item) ? 1 : 0;
					}
				}
				log.debug("Live cache raised wear levels on {} items", raised);
				read.complete(prepared);
			}
			catch (RuntimeException e)
			{
				read.completeExceptionally(e);
			}
		});
		try
		{
			return read.get(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			return null;
		}
		catch (ExecutionException | TimeoutException e)
		{
			log.warn("Live cache identities unavailable; using the bundled catalogue until the next retry", e);
			return data;
		}
	}

	/** Retry after the bundled data failed to load. */
	public void reloadGameData()
	{
		dataRequested = false;
		requestGameData();
	}

	// ------------------------------------------------------------ events

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Logging out may switch accounts, so recorded storage is dropped; a world hop keeps the account and its storage.
		if (event.getGameState() == GameState.LOGIN_SCREEN && storedItemsBridge != null)
		{
			storedItemsBridge.reset();
		}
		if (event.getGameState().getState() >= GameState.LOGIN_SCREEN.getState())
		{
			SwingUtilities.invokeLater(this::requestGameData);
		}
		if (event.getGameState().getState() < GameState.LOGGING_IN.getState())
		{
			ownedItems.clearEquipped();
			onPanel(Sidebar::updateOwnedStatus);
		}
	}

	/** Client thread: take the current inventory and equipment, as if their containers had just changed. */
	private void readLiveContainers()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		for (int containerId : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container != null)
			{
				ownedItems.onContainerChanged(containerId, container);
			}
		}
		// Nothing is moving: publish what was read rather than waiting for it to settle.
		ownedItems.rebuild();
		onPanel(Sidebar::updateOwnedStatus);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		// Storing or retrieving items moves them through the inventory: ask DWMS for its storage again. Firing
		// ammunition only shrinks a stack, so it asks only once the last of it is gone.
		if (ownedItems.onContainerChanged(event.getContainerId(), event.getItemContainer()))
		{
			containersChanged = true;
		}
		onPanel(Sidebar::updateOwnedStatus);
	}

	/**
	 * Client thread: RuneLite delivers the tick's container events before GameTick. Ownership is published once
	 * its totals agree on two consecutive ticks, after Dude, Where's My Stuff? has answered when it was asked.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		StoredItemsBridge bridge = storedItemsBridge;
		if (bridge != null && client.getGameState() == GameState.LOGGED_IN && bridge.request(containersChanged))
		{
			containersChanged = false;
			int life = lifecycle.get();
			// DWMS queues its reply on the client thread; queue behind it before checking ownership.
			clientThread.invokeLater(() ->
			{
				if (life == lifecycle.get() && bridge == storedItemsBridge)
				{
					settleOwnedItems(bridge.complete());
				}
			});
		}
		else
		{
			containersChanged = false;
			settleOwnedItems(false);
		}
	}

	/** Client thread: publish settled ownership, then search again if gear was really gained or lost. */
	private void settleOwnedItems(boolean locationsChanged)
	{
		Set<Integer> idsBefore = ownedItems.snapshot();
		boolean changed = ownedItems.settle();
		if (changed)
		{
			invalidateIfOwnershipChanged();
		}
		// Firing ammunition only changes stack sizes: repaint the owned markers, but rebuild the Gear lists only
		// when items come or go, or recorded storage changes.
		boolean listsChanged = locationsChanged || ownedItems.snapshot() != idsBefore;
		if (changed || listsChanged)
		{
			onPanel(listsChanged ? Sidebar::onManualOwnedChanged : Sidebar::updateOwnedStatus);
		}
	}

	/**
	 * Results are valid only for the items owned when they were computed: if equipment was gained or lost since,
	 * discard them and search again with the new ownership. A changed ammunition or thrown-weapon stack that
	 * affects the requested ammo quantity searches again once the stack stops changing, so firing doesn't restart
	 * the search every attack. Food, potions and other non-gear changes are ignored.
	 */
	void invalidateIfOwnershipChanged()
	{
		Set<Integer> before = searchOwned;
		if (before == null || lastSearched == null)
		{
			return;
		}
		SearchMode mode = searchMode;
		Set<Integer> now = ownedItems.snapshot(mode);
		if (before != now && gearOwnershipDiffers(before, now, gearIds))
		{
			searchOwned = now;
			rerun();
			return;
		}
		// Compare with the last observed supply, not the search's: unrelated container events (food, potions)
		// must not keep pushing back the refresh of a stack that has already settled.
		Map<Integer, Long> quantities = ownedItems.quantitySnapshot(mode);
		Map<Integer, Long> observed = observedQuantities;
		if (supplyDiffers(observed != null ? observed : searchQuantities, quantities, consumableIds, searchAmmoCount))
		{
			observedQuantities = quantities;
			SwingUtilities.invokeLater(this::scheduleSupplyRefresh);
		}
	}

	/** Swing thread: (re)start the settle timer; each further relevant stack change pushes the refresh back. */
	void scheduleSupplyRefresh()
	{
		if (supplyRefresh == null)
		{
			supplyRefresh = new javax.swing.Timer(supplySettleMillis, e -> refreshSupply());
			supplyRefresh.setRepeats(false);
		}
		supplyRefresh.setInitialDelay(supplySettleMillis);
		supplyRefresh.restart();
	}

	/** Swing thread: the stack has settled; search again if it still differs from the one the results used. */
	void refreshSupply()
	{
		Map<Integer, Long> now = ownedItems.quantitySnapshot(searchMode);
		if (searchOwned != null && supplyDiffers(searchQuantities, now, consumableIds, searchAmmoCount))
		{
			searchQuantities = now;
			observedQuantities = null;
			rerun();
		}
	}

	/**
	 * Whether a held stack changed in a way the search could see: only the part of each stack up to the requested
	 * ammo quantity matters, so a large stack shrinking above that quantity changes nothing.
	 */
	static boolean supplyDiffers(Map<Integer, Long> before, Map<Integer, Long> now, Set<Integer> consumables,
		long ammoCount)
	{
		return before != null && ammoCount > 0 && consumables.stream().anyMatch(id ->
			Math.min(before.getOrDefault(id, 0L), ammoCount) != Math.min(now.getOrDefault(id, 0L), ammoCount));
	}

	static boolean gearOwnershipDiffers(Set<Integer> before, Set<Integer> now, Set<Integer> gear)
	{
		return gear.stream().anyMatch(id -> before.contains(id) != now.contains(id));
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		int generation = searchGeneration.incrementAndGet();
		tabCache.clear();
		setBankHighlightedSetup(null);
		if (search != null)
		{
			search.cancel(false);
		}
		lastSearched = null;
		searchOwned = null;
		searchQuantities = null;
		observedQuantities = null;
		lookupTarget = null;
		lastRememberedLevels = null;
		ownedItems.load();
		StoredItemsBridge bridge = storedItemsBridge;
		if (bridge != null)
		{
			clientThread.invoke(bridge::reset);
		}
		onPanel(p ->
		{
			if (generation == searchGeneration.get())
			{
				p.clearResults("Account changed. Find best setup again to use this account's levels and items.");
			}
			p.syncFromConfig();
			p.updateOwnedStatus();
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		String key = event.getKey();
		if (!Options.GROUP.equals(event.getGroup()) || panel == null)
		{
			return;
		}
		// The boss filter only changes search suggestions.
		if (Options.BOSSES_ONLY_KEY.equals(key))
		{
			onPanel(Sidebar::refreshSearchFilter);
			return;
		}
		// Per-account keys (owned items) change with every bank update; they must not trigger a search.
		if (event.getProfile() != null || NO_SEARCH_KEYS.contains(key))
		{
			return;
		}
		// Kills per trip and food filling change only the trip's supplies, not the gear.
		boolean suppliesOnly = "killsPerTrip".equals(key) || "fillFood".equals(key);
		onPanel(p ->
		{
			p.syncFromConfig();
			if (suppliesOnly)
				p.refreshSupplies();
		});
		if (!suppliesOnly)
			rerun();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.showMenuOption() || event.getMenuEntry().getType() != MenuAction.EXAMINE_NPC)
		{
			return;
		}
		MenuEntry entry = event.getMenuEntry();
		NPC npc = entry.getNpc();
		if (npc == null || !isAttackable(npc))
		{
			return;
		}
		// Added alongside Examine (index -1 places it just above), so it is never the left-click option.
		client.getMenu().createMenuEntry(-1)
			.setOption(MENU_OPTION)
			.setTarget(event.getTarget())
			.setIdentifier(event.getIdentifier())
			.setType(MenuAction.RUNELITE)
			.onClick(e -> lookupNpc(npc));
	}

	private static boolean isAttackable(NPC npc)
	{
		NPCComposition comp = npc.getTransformedComposition();
		if (comp == null)
		{
			comp = npc.getComposition();
		}
		return comp != null && comp.getActions() != null && Arrays.stream(comp.getActions())
			.anyMatch(action -> action != null && "attack".equalsIgnoreCase(Text.removeTags(action)));
	}

	/** Client thread: resolve the clicked NPC and open the panel on it. */
	private void lookupNpc(NPC npc)
	{
		lookupTarget = npc;
		int id = npc.getId();
		String name = npc.getName();
		int level = npc.getCombatLevel();
		onPanel(p ->
		{
			clientToolbar.openPanel(navButton);
			GameData data = gameData;
			if (data == null)
			{
				p.showStatus("Game data is still loading, try again in a moment.");
				return;
			}
			Summary match = data.matchNpc(id, name, level);
			if (match == null)
			{
				p.showSearch(name == null ? "" : Text.removeTags(name));
				p.showError("No monster data for " + name + " (id " + id + ").");
				return;
			}
			p.selectMonster(match, true);
		});
	}

	// ------------------------------------------------------------ search

	private boolean matchesTarget(NPC npc, Summary selected)
	{
		GameData data = gameData;
		if (npc == null || data == null || selected == null || npc.getWorldView() == null
			|| npc.getWorldView().npcs().byIndex(npc.getIndex()) != npc)
		{
			return false;
		}
		Summary match = data.matchNpc(npc.getId(), npc.getName(), npc.getCombatLevel());
		return match != null && match.getId() == selected.getId();
	}

	/**
	 * Start a best-setup search for the monster. Runs off the client thread; results are
	 * delivered to the panel on the Swing thread. A new search cancels the previous one.
	 */
	public void findBestSetup(Summary summary)
	{
		GameData data = gameData;
		// Read with the data, on the Swing thread that publishes new data: a search of an earlier catalogue can
		// then never store under a newer one.
		long epoch = tabCache.epoch();
		if (data == null || executor == null)
		{
			return;
		}
		lastSearched = summary;
		int generation = searchGeneration.incrementAndGet();
		if (search != null)
		{
			search.cancel(false);
		}
		panel.showSearching(summary);

		clientThread.invokeLater(() ->
		{
			// A profile change or shutdown can invalidate a search before its client-thread snapshot runs.
			if (generation != searchGeneration.get() || executor == null)
			{
				return;
			}
			// Logged out, use the levels last seen on this profile so results match the logged-in ones.
			RememberedLevels live = readLevels();
			RememberedLevels known = live != null ? live : RememberedLevels.parse(
				configManager.getRSProfileConfiguration(Options.GROUP, RememberedLevels.CONFIG_KEY));
			if (live != null)
			{
				rememberLevels(live);
			}
			boolean rememberedLevels = live == null && known != null;
			PlayerLevels levels = known == null ? null : known.getLevels();
			int mining = known == null ? 99 : known.getMining();
			Set<String> prayerUnlocks = readPrayerUnlocks();
			Boolean kourendElite = readKourendEliteDiary();
			Map<String, Object> detected = new HashMap<>();
			List<String> detectedNotes = new ArrayList<>();
			if (config.thrall())
			{
				Boolean kingdomDivided = readKingdomDivided();
				detected.put("thrall", !Boolean.FALSE.equals(kingdomDivided));
				if (kingdomDivided == null)
				{
					detectedNotes.add("Thralls: A Kingdom Divided is unknown until you log in; thralls are assumed available.");
				}
				else if (!kingdomDivided)
				{
					detectedNotes.add("Thralls: not added, because A Kingdom Divided isn't complete.");
				}
			}
			NPC npc = matchingLiveTarget(summary);
			AccountFacts.TaskRecord assignment = AccountFacts.task(client, configManager, gson);
			WorldPoint taskLocation = npc != null ? npc.getWorldLocation()
				: AccountFacts.ready(client) && client.getLocalPlayer() != null ? client.getLocalPlayer().getWorldLocation() : null;
			// A live target is checked where it stands; otherwise the search plans the assigned fight.
			Boolean matched = npc != null ? AccountFacts.matches(assignment, summary.getName(), taskLocation)
				: AccountFacts.matchesAssignment(assignment, summary.getName());
			boolean karuulmDungeon = AccountFacts.karuulmSearch(assignment, summary.getName(),
				npc == null ? null : npc.getWorldLocation(), taskLocation);
			boolean onTask = config.taskMode().resolve(matched, false);
			detected.put("onSlayerTask", onTask);
			// The task summary's hover text: one short line.
			String taskNote = config.taskMode() != AutoState.AUTO ? "Slayer task " + config.taskMode() + " (Fight override)."
				: matched == null ? "Slayer task unknown; task bonus not assumed."
				: !onTask ? "This target is off task."
				: npc == null && assignment.locationRestricted ? "On task, assuming the assigned location."
				: "Slayer task matched to this target.";
			if (WildernessTargets.only(summary.getName()))
			{
				detected.put("wilderness", true);
				detectedNotes.add("Wilderness: on, because this target only appears in the Wilderness.");
			}
			Boolean diary = AccountFacts.diary(client, configManager);
			detected.put("kandarinDiary", Boolean.TRUE.equals(diary));
			if (diary == null) { detectedNotes.add("Kandarin hard diary: unknown, so the enchanted bolt bonus isn't assumed."); }
			LiveRaid liveRaid = npc == null || !config.autoRaid() ? new LiveRaid() : LiveRaid.read(client, summary);
			detected.putAll(liveRaid.values);
			Map<String, Object> landed = liveSpecs == null ? Collections.emptyMap() : liveSpecs.overrides(summary.getName());
			detected.putAll(landed);
			if (!landed.isEmpty())
			{ detectedNotes.add("Pre-fight preparation: specs landed on this target so far, in place of the Fight settings."); }
			Options assumptions = Assumptions.capture(config, detected);
			String assumptionDetail = taskNote;
			onPanel(generation, p -> p.showAssumptions("Task: "
				+ (matched == null && assumptions.taskMode() == AutoState.AUTO ? "unknown" : onTask ? "on" : "off")
				+ (assumptions.taskMode() == AutoState.AUTO ? " (Auto)" : " (override)"), assumptionDetail));
			// ItemManager prices must be read on the client thread; snapshot them for the search and UI.
			Map<Integer, Long> quotes = snapshotPrices(data);
			prices = quotes;
			// Ownership is fixed for the whole search; later gear changes invalidate the results instead.
			SearchMode mode = assumptions.mode();
			OwnedItems.Snapshot ownership = ownedItems.capture(mode);
			Set<Integer> owned = ownership.getIds();
			Map<Integer, Long> quantities = ownership.getQuantities();
			searchMode = mode;
			searchOwned = owned;
			searchQuantities = quantities;
			observedQuantities = null;
			searchAmmoCount = config.ammoCount();
			// Rada's blessing 4 is an Elite diary reward, so owning it anywhere proves completion before the varbit
			// is seen, even when the search only uses carried items.
			Boolean eliteDiary = kourendElite == null && ownedItems.snapshot().contains(ItemID.ZEAH_BLESSING_ELITE)
				? Boolean.TRUE : kourendElite;
			// The antifire follows ownership, so it is settled with it, for the search and its tab cache key.
			Antifire antifire = BoostAccess.antifire(mode, owned, excluded(assumptions));
			if (antifire == Antifire.REGULAR && !owned.contains(antifire.getItemId()) && !owned.contains(antifire.getExtendedId())
				&& (mode == SearchMode.OWNED_ONLY || mode == SearchMode.INVENTORY_ONLY))
			{ detectedNotes.add("Antifire: none owned, so an antifire potion is assumed against dragonfire."); }
			Options settled = Assumptions.capture(assumptions, Collections.singletonMap("antifire", antifire));
			search = executor.submit(() -> runSearch(data, summary, levels, rememberedLevels, prayerUnlocks,
				eliteDiary, karuulmDungeon, mining, quotes, owned, quantities, generation, settled, liveRaid, detectedNotes, epoch));
		});
	}

	/** Specs landed on a live target: refresh the open result if it is that target. */
	private void liveSpecsChanged(String target)
	{
		Summary last = lastSearched;
		if (last != null && LiveSpecs.sameTarget(target, last.getName()))
		{
			rerun();
		}
	}

	/** The logged-in account's base levels, or null when logged out. Client thread only. */
	private RememberedLevels readLevels()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		return RememberedLevels.of(Arrays.stream(RememberedLevels.SKILLS).mapToInt(client::getRealSkillLevel).toArray());
	}

	/** Persist the account's levels for logged-out searches, once they have all loaded and only when changed. */
	private void rememberLevels(RememberedLevels levels)
	{
		if (!levels.complete())
		{
			return;
		}
		String value = levels.serialize();
		if (!value.equals(lastRememberedLevels))
		{
			lastRememberedLevels = value;
			configManager.setRSProfileConfiguration(Options.GROUP, RememberedLevels.CONFIG_KEY, value);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.KANDARIN_DIARY_HARD_COMPLETE)
		{
			AccountFacts.diary(client, configManager);
		}
		// The count drops on every task kill; only its completion changes the remembered assignment. Searches
		// read the live record anyway, so per-kill counts need not be persisted.
		if (event.getVarpId() == VarPlayerID.SLAYER_COUNT && event.getValue() == 0
			|| event.getVarpId() == VarPlayerID.SLAYER_TARGET || event.getVarpId() == VarPlayerID.SLAYER_AREA
			|| event.getVarbitId() == VarbitID.SLAYER_TARGET_BOSSID)
		{
			AccountFacts.task(client, configManager, gson);
		}
		if (PrayerUnlocks.isUnlockVarbit(event.getVarbitId()))
		{
			readPrayerUnlocks();
		}
		if (event.getVarbitId() == VarbitID.KOUREND_DIARY_ELITE_COMPLETE)
		{
			readKourendEliteDiary();
		}
	}

	/**
	 * Whether the Elite Kourend & Kebos Diary is complete (it waives the Karuulm heat-protection boots): read while
	 * logged in and remembered for the profile. Null if never seen. Client thread only.
	 */
	private Boolean readKourendEliteDiary()
	{
		return readRemembered("kourendEliteDiary", () -> client.getVarbitValue(VarbitID.KOUREND_DIARY_ELITE_COMPLETE) == 1);
	}

	/**
	 * Whether A Kingdom Divided is complete (thralls need it): read while logged in and remembered for the
	 * profile, so logged-out searches use the last known state. Null if never seen. Client thread only.
	 */
	private Boolean readKingdomDivided()
	{
		return readRemembered("kingdomDivided", () -> Quest.A_KINGDOM_DIVIDED.getState(client) == QuestState.FINISHED);
	}

	/** Client thread: the live answer while logged in, saved under the profile key; otherwise the saved one. */
	private Boolean readRemembered(String key, BooleanSupplier live)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			boolean complete = live.getAsBoolean();
			configManager.setRSProfileConfiguration(Options.GROUP, key, complete);
			return complete;
		}
		String remembered = configManager.getRSProfileConfiguration(Options.GROUP, key);
		return remembered == null ? null : Boolean.valueOf(remembered);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		RememberedLevels live = readLevels();
		if (live != null)
		{
			rememberLevels(live);
		}
	}

	/**
	 * Gated prayers the account has unlocked: read from the game while logged in and remembered for the
	 * profile, so logged-out searches use the last known unlocks. Null if never seen. Client thread only.
	 */
	private Set<String> readPrayerUnlocks()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			Set<String> unlocked = PrayerUnlocks.read(client);
			configManager.setRSProfileConfiguration(Options.GROUP, PrayerUnlocks.CONFIG_KEY,
				PrayerUnlocks.serialize(unlocked));
			return unlocked;
		}
		return PrayerUnlocks.parse(configManager.getRSProfileConfiguration(Options.GROUP,
			PrayerUnlocks.CONFIG_KEY));
	}

	void runSearch(GameData data, Summary summary, PlayerLevels knownLevels, boolean rememberedLevels,
		Set<String> prayerUnlocks, Boolean kourendElite, boolean karuulmDungeon, int mining, Map<Integer, Long> quotes, Set<Integer> owned,
		Map<Integer, Long> quantities, int generation, Options config, LiveRaid liveRaid, List<String> detectedNotes, long epoch)
	{
		try
		{
			boolean assumedLevels = knownLevels == null;
			PlayerLevels levels = assumedLevels ? PlayerLevels.maxed() : knownLevels;
			Monster baseMonster = api.getMonster(summary);
			SpecialAttacks specials = specials(config);
			// Wiki order: raid scaling, health-dependent stats, then pre-fight defence reductions.
			RaidScaling.Settings raid = raidSettings(levels, mining, config);
			if (liveRaid.maxCombat > 0 && liveRaid.maxCombat <= 126) { raid = raid.toBuilder().partyMaxCombat(liveRaid.maxCombat).build(); }
			Monster scaled = RaidScaling.apply(baseMonster, raid);
			scaled.setPhase(config.encounterPhase());
			EncounterPhases.applyStats(scaled);
			Monster monster = specials.apply(MonsterStates.atHealth(scaled, 0));

			boolean eliteDiary = Boolean.TRUE.equals(kourendElite);
			OptimizerSettings settings = buildSettings(config).toBuilder().kourendEliteDiary(eliteDiary)
				.karuulmDungeon(karuulmDungeon).build();
			Map<Combat, OffensivePrayer> prayers = new EnumMap<>(Combat.class);
			Predicate<String> available = PrayerUnlocks.available(prayerUnlocks, config.excludedPrayers());
			for (Combat cls : Combat.values())
			{
				OffensivePrayer p = PrayerUnlocks.best(data.getPrayers(cls), levels, available);
				if (p != null)
				{
					prayers.put(cls, p);
				}
			}
			Predicate<Potion> boosts = BoostAccess.allowed(settings.getMode(), owned)
				.and(p -> !settings.getExcluded().contains(p.getId()));
			int[] boosted = boostBases(levels);
			for (int i = 0; i < boosted.length; i++)
			{
				boosted[i] = PotionChoice.boostedLevel(data, BOOST_SKILLS[i], boosted[i], boosts, monster);
			}
			CombatContext ctx = new CombatContext(monster, levels, config.onSlayerTask(),
				boosted[0], boosted[1], boosted[2], boosted[3], prayers, config.thrall()).withFightOptions(config.aoe() ? config.aoeTargets() : 1, 0)
				.withModifiers(CombatModifiers.builder().currentHitpoints(config.currentHitpoints())
					.wilderness(config.wilderness())
					.forinthrySurge(config.forinthrySurge())
					.weaponPoison(config.weaponPoison()).charge(config.charge())
					.markOfDarkness(markOfDarkness(settings)).sunfireRunes(config.sunfireRunes())
					.unlocks(prayerUnlocks == null ? PrayerUnlocks.GATED : prayerUnlocks)
					.kandarinDiary(config.kandarinDiary()).soulreaperStacks(config.soulreaperStacks())
					.miningLevel(config.miningLevel() == 0 ? mining : config.miningLevel()).build())
				// HP-dependent target stats are re-derived in the same order for whole-fight averages.
				.withHealthStates(hp -> specials.apply(MonsterStates.atHealth(scaled, hp)));
			// One tab per attack type: melee is optimised separately for stab, slash and crush.
			// An EnumSet, so already in declaration order.
			List<Style.Type> types = new ArrayList<>(settings.getStyles());
			// A newer search, a cleared search or shutdown bumps the generation.
			BooleanSupplier cancelled = () -> generation != searchGeneration.get();
			SearchProgress progress = new SearchProgress(types.size(),
				percent -> onPanel(generation, p -> p.showProgress(percent)));
			// Built here, off the client thread; everything after the tabs (notes, locks, warnings) runs every time.
			SearchInputs inputs = SearchInputs.of(data, summary.getName(), levels, boosted, prayerUnlocks, eliteDiary,
				karuulmDungeon, mining, liveRaid.maxCombat, quotes, owned, quantities, config);
			List<List<Setup>> tabs = new ArrayList<>();
			for (int i = 0; i < types.size() && !cancelled.getAsBoolean(); i++)
			{
				int tab = i;
				Style.Type type = types.get(i);
				OptimizerSettings typeSettings = settings.toBuilder().styles(EnumSet.of(type)).build();
				DoubleConsumer done = fraction -> progress.update(tab, fraction);
				tabs.add(searchTab(inputs.tab(type), epoch, cancelled, done, () -> new Optimizer(data, ctx, typeSettings,
					owned::contains, id -> quantities.getOrDefault(id, 0L), item -> price(quotes, item))
					.optimize(classOf(type), cancelled, done)));
			}
			if (cancelled.getAsBoolean())
			{
				return;
			}
			progress.complete();
			Map<Style.Type, List<Setup>> byType = new LinkedHashMap<>();
			for (int i = 0; i < types.size(); i++)
			{
				if (!tabs.get(i).isEmpty())
				{
					byType.put(types.get(i), tabs.get(i));
				}
			}
			if (generation != searchGeneration.get())
			{
				return;
			}
			// Also asked whether fire protection could be met, so the note, warning and antifire agree with the search.
			Optimizer checker = new Optimizer(data, ctx, settings, owned::contains, id -> quantities.getOrDefault(id, 0L),
				item -> price(quotes, item));
			boolean fireProtected = checker.isFireProtectionRequired();
			List<String> notes = describeSearch(baseMonster, scaled, monster, specials, ctx, settings, raid, config,
				fireProtected);
			notes.addAll(detectedNotes);
			if (prayerUnlocks == null)
			{
				notes.add("Prayer unlocks are unknown until you log in; Piety, Rigour, Augury and similar are assumed unlocked.");
			}
			if (kourendElite == null && !eliteDiary && SlayerEquipment.inKaruulm(baseMonster, settings))
			{
				notes.add("Elite Kourend & Kebos Diary status is unknown until you log in, so Karuulm boots are required.");
			}
			String unpricedNote = unpricedNote(data, quotes, settings);
			if (unpricedNote != null)
			{
				notes.add(0, unpricedNote);
			}
			List<LockStatus> locks = LockStatus.check(data, checker, settings.getLocks());
			List<String> warnings = new ArrayList<>();
			if (!fireProtected && DragonfireProtection.applies(monster))
			{
				warnings.add("No usable fire protection (excluded or not owned), so its breath isn't blocked.");
			}
			if (settings.getStyles().contains(Style.Type.MAGIC))
			{
				List<Spell> castable = Optimizer.autocastSpells(data, ctx.getMagic(), monster, settings);
				String spellNote = spellFilterNote(settings.getSpellFilter(), castable);
				addNote(warnings, spellNote != null ? spellNote : staffBoundNote(settings.getSpellFilter(), castable,
					byType.getOrDefault(Style.Type.MAGIC, Collections.emptyList())));
			}
			SearchResults found = new SearchResults(monster, ctx.getTargetHitpoints(), byType, notes, ctx.getPrayers(),
				potionsUsed(data, levels, monster, boosts), assumedLevels, locks, markOfDarkness(settings),
				rememberedLevels, warnings,
				Thrall.eligibleType(monster) == null ? null : ctx.getThrall(), raidScaling(baseMonster, raid, liveRaid.raidDetected),
				scaled.getDefenceLevel(), levels.getMagic(), ctx.getMagic(), levels, PrayerUnlocks.excluded(config.excludedPrayers()),
				fireProtected && DragonfireProtection.takesPotion(monster) ? settings.getAntifire() : Antifire.NONE);
			// Loads the overhead notes here rather than on the Swing thread when the first result is shown.
			Overheads.recommend(monster, Combat.MELEE);
			onPanel(generation, p -> p.showResults(found));
		}
		catch (IOException | RuntimeException | Error e)
		{
			// Errors (e.g. assertion failures) would otherwise vanish inside the executor's Future.
			log.warn("Best setup search failed", e);
			onPanel(generation, p ->
			{
				// The kept results belong to an earlier search; don't leave them beside the failure.
				p.clearResults("");
				p.showError("Search failed: " + e.getMessage());
			});
		}
	}

	/**
	 * One style tab's setups: those remembered for these inputs, else a new search's. A search is remembered only if
	 * it was never cancelled (the generation only moves on, so not cancelled now means not cancelled while it ran):
	 * a cancelled optimiser can return a partial list. Shared lists are read-only, and nothing changes a returned
	 * setup or needs the optimiser that found it.
	 */
	List<Setup> searchTab(SearchInputs key, long epoch, BooleanSupplier cancelled, DoubleConsumer progress,
		Supplier<List<Setup>> search)
	{
		List<Setup> found = tabCache.get(key, epoch);
		if (found != null)
		{
			progress.accept(1);
			return found;
		}
		found = Collections.unmodifiableList(search.get());
		if (!cancelled.getAsBoolean())
		{
			tabCache.put(key, found, epoch);
		}
		return found;
	}

	/** Clear the bank highlights, or publish a setup without its boosts. */
	public void setBankHighlightedSetup(Setup setup)
	{
		setBankHighlightedSetup(setup, Collections.emptyList(), Collections.emptyList());
	}

	/** Publish the displayed setup's equipment, loaded ammunition, spell runes, boosts and trip supplies to the bank. */
	public void setBankHighlightedSetup(Setup setup, List<Potion> potions, List<TripSupplies.Supply> supplies)
	{
		Set<Integer> ids = new HashSet<>();
		if (setup != null)
		{
			for (Slot slot : Slot.values())
			{
				addBankHighlightItem(ids, setup.getLoadout().get(slot));
			}
			addBankHighlightItem(ids, setup.getLoadout().getLoadedAmmo());
			if (setup.getSpecial() != null)
			{
				addBankHighlightItem(ids, setup.getSpecial().getWeapon());
				addBankHighlightItem(ids, setup.getSpecial().getOffhand());
				addBankHighlightItem(ids, setup.getSpecial().getAmmo());
				addBankHighlightItem(ids, setup.getSpecial().getLoadedAmmo());
			}
			List<Integer> runes = SpellRunes.required(setup);
			if (!runes.isEmpty())
			{
				ids.addAll(SpellRunes.POUCHES);
			}
			for (int rune : runes)
			{
				ids.add(rune);
				ids.addAll(SpellRunes.substitutes(rune));
			}
			for (Potion potion : potions)
			{
				// Raid supplies are handed out inside the raid, never withdrawn.
				if (!potion.isRaidSupply())
				{
					ids.addAll(BoostAccess.itemIds(potion));
				}
			}
			supplies.forEach(supply -> ids.addAll(supply.getIds()));
		}
		bankHighlightIds = Collections.unmodifiableSet(ids);
		if (bankView != null)
		{
			bankView.setSelection(setup, setup == null ? Collections.emptyList() : potions, this::bankItemIds, supplies);
			int life = lifecycle.get();
			clientThread.invokeLater(() ->
			{
				if (life == lifecycle.get())
				{
					bankView.refreshSelection();
				}
			});
		}
	}

	/** Swing thread: cancel current and queued work and release the selected setup and target. */
	public void clearGearSearch()
	{
		searchGeneration.incrementAndGet();
		if (search != null)
		{
			search.cancel(false);
			search = null;
		}
		if (supplyRefresh != null)
		{
			supplyRefresh.stop();
		}
		lastSearched = null;
		lookupTarget = null;
		searchOwned = null;
		searchQuantities = null;
		observedQuantities = null;
		setBankHighlightedSetup(null);
		if (panel != null)
		{
			panel.clearSearch();
		}
	}

	private void addBankHighlightItem(Set<Integer> ids, Gear item)
	{
		if (item != null)
		{
			ids.addAll(bankItemIds(item));
		}
	}

	/**
	 * The item and its charge states, then its same-stats cosmetic variants, which can be worn in its place: the
	 * bank shows whichever is there, and a recoloured or ornamented copy is highlighted too.
	 */
	private List<Integer> bankItemIds(Gear item)
	{
		List<Integer> ids = BestGearSetupBankView.itemIds(item);
		for (Gear other : CosmeticVariants.others(gameData, item))
		{
			ids.addAll(BestGearSetupBankView.itemIds(other));
		}
		return ids;
	}

	private static Combat classOf(Style.Type type)
	{
		switch (type)
		{
			case RANGED:
			case ATLATL:
				return Combat.RANGED;
			case MAGIC:
				return Combat.MAGIC;
			default:
				return Combat.MELEE;
		}
	}

	static Map<Combat, List<Potion>> potionsUsed(GameData data, PlayerLevels levels, Monster target, Predicate<Potion> boosts)
	{
		Map<Combat, List<Potion>> out = new EnumMap<>(Combat.class);
		int[] base = boostBases(levels);
		for (int i = 0; i < base.length; i++)
		{
			Potion p = PotionChoice.resolve(data, BOOST_SKILLS[i], base[i], boosts, target);
			if (p == null)
			{
				continue;
			}
			List<Potion> list = out.computeIfAbsent(BOOST_CLASSES[i], k -> new ArrayList<>());
			int same = list.stream().map(Potion::getId).collect(Collectors.toList()).indexOf(p.getId());
			if (same < 0)
			{
				list.add(p);
			}
			else if (BOOST_SKILLS[i].equals("strength"))
			{
				// Keep a combat potion or overload as its Strength boost, the one the atlatl uses.
				list.set(same, p);
			}
		}
		return out;
	}

	/** Base levels of the boosted skills, in the order of {@link #BOOST_SKILLS}. */
	private static int[] boostBases(PlayerLevels levels)
	{
		return new int[]{levels.getAttack(), levels.getStrength(), levels.getRanged(), levels.getMagic()};
	}

	OptimizerSettings buildSettings(Options config)
	{
		Set<Style.Type> styles = EnumSet.noneOf(Style.Type.class);
		if (config.styleStab())
		{
			styles.add(Style.Type.STAB);
		}
		if (config.styleSlash())
		{
			styles.add(Style.Type.SLASH);
		}
		if (config.styleCrush())
		{
			styles.add(Style.Type.CRUSH);
		}
		if (config.styleRanged())
		{
			styles.add(Style.Type.RANGED);
		}
		if (config.styleMagic())
		{
			styles.add(Style.Type.MAGIC);
		}
		if (config.styleAtlatl())
		{
			styles.add(Style.Type.ATLATL);
		}
		return OptimizerSettings.builder()
			.mode(config.mode())
			.budget(Math.max(0, Budget.parse(config.budget())))
			.wildernessRiskLimited(config.wilderness() && config.limitWildernessRisk())
			.maxExpensiveItems(Math.max(0, Math.min(11, config.maxExpensiveItems())))
			.expensiveItemThreshold(Math.max(0, Budget.parse(config.expensiveItemThreshold())))
			.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient", "arceuus")))
			.spellFilter(SpellFilter.parse(config.blockedSpells(), config.forcedSpells()))
			.styles(styles)
			.weaponHands(config.weaponHands())
			.calcMode(config.calcMode())
			.attackXp(config.attackXp())
			.strengthXp(config.strengthXp())
			.defenceXp(config.defenceXp())
			.membersItems(config.membersItems())
			.dmmItems(config.dmmItems())
			.betaItems(config.betaItems())
			.bountyHunterItems(config.bountyHunterItems())
			.requireAtlatlAmmoRecovery(config.requireAtlatlAmmoRecovery())
			.preventManualCast(config.preventManualCast())
			.fillMode(config.fillMode())
			.defenceFocus(config.defenceFocus())
			.fillMarginPercent(config.fillMargin())
			.ammoCount(config.ammoCount())
			.killSpecials(config.killSpecials()).specEnergy(config.specEnergy())
			.drainSpecs(config.drainSpecs())
			.antifire(config.antifire())
			.protectMagic(!PrayerUnlocks.excluded(config.excludedPrayers()).contains("protect from magic"))
			// The search's own snapshot, not the live settings, which can change while it waits to run.
			.locks(SlotLock.parse(config.locks()))
			.excluded(excluded(config))
			.build();
	}

	private RaidScaling.Settings raidSettings(PlayerLevels levels, int mining, Options config)
	{
		return RaidScaling.Settings.builder()
			.partySize(config.raidPartySize())
			.toaRaidLevel(config.toaRaidLevel())
			.toaPathLevel(config.toaPathLevel())
			.coxChallengeMode(config.coxChallengeMode())
			.partyMaxCombat(levels.combatLevel())
			.partyMaxHitpoints(levels.getHitpoints())
			.partyAverageMining(config.miningLevel() == 0 ? mining : config.miningLevel())
			.build();
	}

	private SpecialAttacks specials(Options config)
	{
		return SpecialAttacks.builder()
			.vulnerability(config.specVulnerability() || config.specTomeOfWater())
			.tomeOfWater(config.specTomeOfWater())
			.elderMaul(config.specElderMaul())
			.dragonWarhammer(config.specDwh())
			.emberlight(config.specEmberlight())
			.arclight(config.specArclight())
			.tonalztics(config.specTonalztics())
			.bandosGodswordDamage(config.specBgsDamage())
			.seercullDamage(config.specSeercullDamage())
			.ayakDamage(config.specAyakDamage())
			.build();
	}

	/** The party scaling a raid target was searched with, such as "ToB party: 3 (live)"; null outside raids. */
	static String raidScaling(Monster target, RaidScaling.Settings raid, boolean live)
	{
		String scaling;
		switch (RaidScaling.raid(target))
		{
			case COX:
				scaling = "CoX party: " + raid.getPartySize() + (raid.isCoxChallengeMode() ? ", Challenge Mode" : "");
				break;
			case TOB:
				scaling = "ToB party: " + raid.getPartySize();
				break;
			case TOB_ENTRY:
				scaling = "ToB entry party: " + raid.getPartySize();
				break;
			case TOA:
				scaling = "ToA party: " + raid.getPartySize() + ", raid level " + raid.getToaRaidLevel();
				break;
			case TFA:
				scaling = "TFA party: " + raid.getPartySize() + (raid.getPartySize() < 2 ? " (scaled as 2)" : "");
				break;
			default:
				return null;
		}
		return scaling + (live ? " (live)" : " (Fight settings)");
	}

	/** Adds a note that applies; null means it doesn't. */
	private static void addNote(List<String> notes, String note)
	{
		if (note != null)
		{
			notes.add(note);
		}
	}

	/** The damage-over-time effects this target resists, or null when it takes them all. */
	private static String statusNote(Monster target)
	{
		StatusImmunities.Immunity im = StatusImmunities.of(target);
		List<String> resisted = new ArrayList<>();
		if (im.isPoisonImmune())
		{
			resisted.add("poison immune");
		}
		if (im.isVenomBecomesPoison() || im.isVenomImmune())
		{
			resisted.add(im.isVenomBecomesPoison() ? "venom becomes poison" : "venom immune");
		}
		if (im.getBurnImmunity() >= StatusImmunities.BURN_NORMAL)
		{
			resisted.add(im.getBurnImmunity() >= StatusImmunities.BURN_STRONG ? "burn immune" : "strong burns only");
		}
		return resisted.isEmpty() ? null : "Damage over time: " + String.join(", ", resisted) + ".";
	}

	private List<String> describeSearch(Monster unscaled, Monster base, Monster drained,
		SpecialAttacks specials, CombatContext ctx, OptimizerSettings settings, RaidScaling.Settings raid, Options config,
		boolean fireProtected)
	{
		// Only what changed this result or needs the player's attention; the header shows party scaling and Defence.
		List<String> notes = new ArrayList<>();
		if (specials.isAny() && config.killSpecials())
		{
			notes.add("Pre-fight preparation is applied before the specs planned into each kill; clear it if it describes the same specs.");
		}
		if (settings.isWildernessRiskLimited())
		{
			notes.add("Wilderness limit: at most " + settings.getMaxExpensiveItems() + " equipped or spec items worth "
				+ Budget.format(settings.getExpensiveItemThreshold()) + " GP or more each.");
			if (Budget.parse(config.expensiveItemThreshold()) < 0)
			{
				notes.add(0, "Invalid expensive-item threshold: using 0 GP, so every equipped item counts as expensive. Enter an amount such as 500k or 1m in Fight.");
			}
		}
		if (settings.isRequireAtlatlAmmoRecovery() && (settings.getStyles().contains(Style.Type.RANGED)
			|| settings.getStyles().contains(Style.Type.ATLATL)))
		{
			notes.add("Ranged attacks firing ammo an Ava's device recovers require an Ava's device, an assembler cape or "
				+ "Dizana's quiver when one is available to this search; special-attack switches are exempt.");
		}
		addNote(notes, statusNote(base));
		addNote(notes, RaidScaling.describe(unscaled, base, raid));
		addNote(notes, EncounterPhases.describe(base));
		if (base.getName().toLowerCase(Locale.ROOT).startsWith("vardorvis"))
		{
			notes.add("Vardorvis: Defence and Strength follow his HP; DPS is averaged over the fight.");
		}
		if (base.getWeakness() > 0)
		{
			notes.add("Elemental weakness: " + base.getWeaknessType() + " +" + base.getWeakness() + "% base accuracy and damage.");
		}
		if (base.hasAttribute("demon"))
		{
			notes.add(markOfDarkness(settings) ? "Demonbane spells assume Mark of Darkness is active."
				: "Mark of Darkness is excluded, so demonbane spells get only their base demon accuracy bonus.");
		}
		if (ctx.getAoeTargets() > 1)
		{
			notes.add("AoE: " + ctx.getAoeTargets() + " grouped enemies; ranked by total DPS.");
		}
		if (fireProtected)
		{
			addNote(notes, DragonfireProtection.note(base, settings));
		}
		notes.addAll(SlayerEquipment.notes(base, settings));
		int distance = AttackReach.distance(base, settings.getTargetDistance());
		if (distance > 1)
		{
			notes.add("Fighting distance: " + distance + " tiles; attacks that can't reach are excluded.");
		}
		addNote(notes, AttackReach.restrictionNote(base));
		Thrall.Type thrallType = Thrall.eligibleType(base);
		if (Thrall.immune(base) && ctx.getThrall() != null)
		{
			notes.add(base.isImmuneThrall() ? "Thralls: this target is immune, so none are added."
				: "Thralls: none added, because they can't be resurrected in this fight.");
		}
		else if (thrallType == null && ctx.getThrall() != null)
		{
			notes.add("Thralls: none added, because no thrall's combat style can damage this target.");
		}
		else if (thrallType != null && thrallType != Thrall.Type.GHOSTLY && ctx.getThrall() != null)
		{
			notes.add("Thralls: a " + thrallType.getLabel() + " thrall is assumed, because this target is immune to "
				+ (thrallType == Thrall.Type.SKELETAL ? "magic." : "magic and ranged."));
		}
		else if (ctx.getThrall() == null && ctx.isThrall())
		{
			notes.add("Thralls: none added, because they need 38 Magic.");
		}
		if (drained.getMagicLevel() != base.getMagicLevel() || drained.getDefMagic() != base.getDefMagic())
		{
			notes.add(String.format("After specials: Magic %d -> %d, magic defence %d -> %d.",
				base.getMagicLevel(), drained.getMagicLevel(), base.getDefMagic(), drained.getDefMagic()));
		}
		// The search's own snapshot, matching the setups (see buildSettings).
		int locks = settings.getLocks().size();
		int excluded = settings.getExcluded().size();
		if (locks > 0 || excluded > 0)
		{
			notes.add(locks + " slot lock(s), " + excluded + " excluded item(s)");
		}
		return notes;
	}

	// ------------------------------------------------------------ locks, exclusions, potions (panel API)

	/** Returns the current blocked and forced spell rules. */
	public SpellFilter getSpellFilter()
	{
		return config == null ? SpellFilter.NONE : SpellFilter.parse(config.blockedSpells(), config.forcedSpells());
	}

	/** Whether the selected spell setup uses sunfire runes. */
	public boolean isSunfireRunes()
	{
		return config != null && config.sunfireRunes();
	}

	public int getKillsPerTrip()
	{
		return config.killsPerTrip();
	}

	/** A setup's trip supplies for the Kills per trip setting, from the items owned now. */
	public List<TripSupplies.Supply> tripSupplies(Setup setup, SearchResults results, List<Potion> potions)
	{
		return TripSupplies.plan(setup, results, potions, config.killsPerTrip(), config.fillFood(), ownedItems::owns,
			results.getAntifire());
	}

	/** Updates one spell rule, moving it between the blocked and forced lists. */
	public void setSpellRule(String name, SpellRule rule)
	{
		SpellFilter current = getSpellFilter();
		SpellFilter changed = current.withRule(name, rule);
		String blocked = SpellFilter.format(changed.getBlocked());
		String forced = SpellFilter.format(changed.getForced());
		String storedBlocked = SpellFilter.format(SpellFilter.parse(config.blockedSpells(), "").getBlocked());
		String storedForced = SpellFilter.format(SpellFilter.parse("", config.forcedSpells()).getForced());
		if (!blocked.equals(storedBlocked))
		{
			setConfig(Options.BLOCKED_SPELLS_KEY, blocked);
		}
		if (!forced.equals(storedForced))
		{
			setConfig(Options.FORCED_SPELLS_KEY, forced);
		}
	}

	/** Clears all blocked spell rules. */
	public void clearBlockedSpells()
	{
		setConfig(Options.BLOCKED_SPELLS_KEY, "");
	}

	/** Clears all forced spell rules. */
	public void clearForcedSpells()
	{
		setConfig(Options.FORCED_SPELLS_KEY, "");
	}

	/** Returns combat spells ordered by spellbook, level, and name. */
	public List<Spell> getCombatSpells()
	{
		GameData data = gameData;
		if (data == null)
		{
			return Collections.emptyList();
		}
		return data.getSpells().stream().filter(spell -> spell.getMaxHit() > 0)
			.sorted(Comparator.comparingInt((Spell spell) -> spellbookOrder(spell.getSpellbook()))
				.thenComparingInt(Spell::getLevel).thenComparing(spell -> spell.getName().toLowerCase(Locale.ROOT)))
			.collect(Collectors.toList());
	}

	/** The spellbook icon sprite for a combat spell, or -1 when unknown. */
	public static int spellSprite(String spellName)
	{
		return SpellRunes.sprite(spellName);
	}

	/** Orders the supported spellbooks before other spellbooks. */
	private static int spellbookOrder(String spellbook)
	{
		if ("standard".equalsIgnoreCase(spellbook))
		{
			return 0;
		}
		if ("ancient".equalsIgnoreCase(spellbook))
		{
			return 1;
		}
		if ("arceuus".equalsIgnoreCase(spellbook))
		{
			return 2;
		}
		return 3;
	}

	/** Explains when spell rules leave no spell to cast; most allowed spells can at least be cast by hand. */
	/** Mark of Darkness is assumed before demonbane spells unless it is an excluded spell. */
	private static boolean markOfDarkness(OptimizerSettings settings)
	{
		return !settings.getSpellFilter().getBlocked().contains("mark of darkness");
	}

	static String spellFilterNote(SpellFilter filter, List<Spell> castable)
	{
		if (filter.isEmpty() || !castable.isEmpty())
		{
			return null;
		}
		if (!filter.getForced().isEmpty())
		{
			return "Spells: none of the allowed spells ("
				+ filter.getForced().stream().map(GameData::titleCase).collect(Collectors.joining(", "))
				+ ") can be cast here; check your Magic level and the target.";
		}
		return "Spells: every spell you could cast here is excluded.";
	}

	/** Explains when the only allowed spells need their own staff and no setup found wields one. */
	static String staffBoundNote(SpellFilter filter, List<Spell> castable, List<Setup> magic)
	{
		if (filter.getForced().isEmpty() || castable.isEmpty() || !castable.stream().allMatch(Optimizer::staffBound)
			|| magic.stream().anyMatch(r -> r.getLoadout().getSpell() != null))
		{
			return null;
		}
		return "Spells: " + castable.stream().map(Spell::toString).collect(Collectors.joining(", "))
			+ " can only be cast with the right staff wielded, and none is available to this search.";
	}

	public Map<Slot, SlotLock> getLocks()
	{
		return SlotLock.parse(config.locks());
	}

	public void setLock(Slot slot, SlotLock lock)
	{
		Map<Slot, SlotLock> locks = getLocks();
		if (lock == null)
		{
			locks.remove(slot);
		}
		else
		{
			locks.put(slot, lock);
		}
		setConfig(Options.LOCKS_KEY, SlotLock.format(locks));
	}

	/** Lock an item's own slot to it; an excluded item is restored first so the lock can be used. */
	public void lockItem(Gear item)
	{
		setExcluded(item.getId(), false);
		setLock(item.getSlot(), SlotLock.item(item.getId()));
	}

	/** Open the Gear view's item search for a lock, limited to this slot (null for any). */
	public void chooseLockItem(Slot slot)
	{
		if (panel != null)
		{
			panel.showLockSearch(slot);
		}
	}

	public Set<Integer> getExcluded()
	{
		return excluded(config);
	}

	private static Set<Integer> excluded(Options config)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		for (String part : config.excluded().split(","))
		{
			try
			{
				if (!part.trim().isEmpty())
				{
					ids.add(Integer.parseInt(part.trim()));
				}
			}
			catch (NumberFormatException ignored)
			{
				// skip malformed entries
			}
		}
		return ids;
	}

	public void setExcluded(int itemId, boolean excluded)
	{
		setExcluded(Collections.singleton(itemId), excluded);
	}

	/** Exclude or restore several items at once, e.g. every variant of a weapon. */
	public void setExcluded(Collection<Integer> itemIds, boolean excluded)
	{
		Set<Integer> ids = getExcluded();
		if (excluded ? ids.addAll(itemIds) : ids.removeAll(itemIds))
		{
			setConfig(Options.EXCLUDED_KEY, ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
		}
	}

	/** Lower-case names of the prayers excluded in Rules. */
	public Set<String> getExcludedPrayers()
	{
		return PrayerUnlocks.excluded(config == null ? "" : config.excludedPrayers());
	}

	public void setPrayerExcluded(String name, boolean excluded)
	{
		Set<String> names = getExcludedPrayers();
		if (excluded ? names.add(name.toLowerCase(Locale.ROOT)) : names.remove(name.toLowerCase(Locale.ROOT)))
			setConfig(Options.EXCLUDED_PRAYERS_KEY, String.join(",", names));
	}

	public void clearLocks()
	{
		setConfig(Options.LOCKS_KEY, "");
	}

	public void clearExclusions()
	{
		setConfig(Options.EXCLUDED_KEY, "");
	}

	public void setManualOwned(int itemId, boolean owned)
	{
		// Publish on the client thread, which settles ownership each tick from the same pending totals.
		clientThread.invoke(() ->
		{
			ownedItems.setManual(itemId, owned);
			// Owned items are per-account settings, which onConfigChanged ignores, so refresh the lists here.
			onPanel(Sidebar::onManualOwnedChanged);
			rerun();
		});
	}

	/**
	 * Re-run the last search, e.g. after a setting changed. The queued rerun is dropped if, by the time it runs,
	 * the plugin restarted, the account changed or any newer search started (which also coalesces repeated
	 * requests into one search).
	 */
	public void rerun()
	{
		Summary last = lastSearched;
		if (last == null)
		{
			return;
		}
		int life = lifecycle.get();
		int generation = searchGeneration.get();
		SwingUtilities.invokeLater(() ->
		{
			if (life == lifecycle.get() && generation == searchGeneration.get() && last == lastSearched)
			{
				findBestSetup(last);
			}
		});
	}

	/** Client thread only. */
	private Map<Integer, Long> snapshotPrices(GameData data)
	{
		Map<Integer, Long> map = new HashMap<>();
		for (Slot slot : Slot.values())
		{
			for (Gear item : data.getItems(slot))
			{
				map.computeIfAbsent(item.getId(), key -> ItemCosts.price(item, itemManager::getItemPrice,
					id -> itemManager.getItemComposition(id).isTradeable()));
			}
		}
		return map;
	}

	/** Owned directly or through a reviewed equivalent charge state. */
	public boolean owns(Gear item)
	{
		return ownedItems.owns(item.getId()) || item.getOwnershipVariants().stream().anyMatch(ownedItems::owns);
	}

	/** Worn directly or as a reviewed equivalent charge state. */
	public boolean wears(Gear item)
	{
		return ownedItems.isWorn(item.getId()) || item.getOwnershipVariants().stream().anyMatch(ownedItems::isWorn);
	}

	/** Where the item and its reviewed ownership variants are: "Bank", "Inventory", "POH: Toy Box"... */
	public Set<String> itemLocations(Gear item)
	{
		return ownedItems.locations(item.getId(), item.getOwnershipVariants());
	}

	/** Units held of the item and its catalogued variants; {@link OwnedItems#UNLIMITED} if marked owned by hand. */
	public long heldQuantity(Gear item)
	{
		Map<Integer, Long> quantities = ownedItems.quantitySnapshot();
		long held = quantities.getOrDefault(item.getId(), 0L);
		for (int variant : item.getOwnershipVariants())
		{
			if (variant != item.getId())
			{
				long more = quantities.getOrDefault(variant, 0L);
				held = held > OwnedItems.UNLIMITED - more ? OwnedItems.UNLIMITED : held + more;
			}
		}
		return held;
	}

	/**
	 * Acquisition cost of the item or its tradable components, snapshotted at search time, or
	 * {@link ItemCosts#UNKNOWN} if a tradable component has no current price.
	 */
	public long price(Gear item)
	{
		return price(prices, item);
	}

	private static long price(Map<Integer, Long> quotes, Gear item)
	{
		return quotes.getOrDefault(item.getId(), ItemCosts.UNKNOWN);
	}

	/** Budget searches cannot buy what has no price; say how much of the catalogue that removed. */
	static String unpricedNote(GameData data, Map<Integer, Long> quotes, OptimizerSettings settings)
	{
		if (settings.getMode() != SearchMode.BUDGET)
		{
			return null;
		}
		Set<Integer> unpriced = new HashSet<>();
		for (Slot slot : Slot.values())
		{
			for (Gear item : data.getItems(slot))
			{
				if (!ItemCosts.isKnown(price(quotes, item)))
				{
					unpriced.add(item.getId());
				}
			}
		}
		return unpriced.isEmpty() ? null : unpriced.size() + " item(s) have no current Grand Exchange price, so the"
			+ " budget search only uses them if owned. Prices load after login; search again if this is unexpected.";
	}

	private void migratePreferences()
	{
		Map<String, String> stored = new HashMap<>();
		for (String key : new String[]{PreferenceMigration.MARKER, Options.ON_TASK_KEY, "taskMode",
			"raidPartySize", "toaRaidLevel", "toaPathLevel", "coxChallengeMode"})
		{ stored.put(key, configManager.getConfiguration(Options.GROUP, key)); }
		PreferenceMigration.plan(stored).forEach((key, value) -> configManager.setConfiguration(Options.GROUP, key, value));
	}

	/** Client thread: the interacting or looked-up NPC if it is a live, nearby instance of this monster. */
	private NPC matchingLiveTarget(Summary summary)
	{
		if (!AccountFacts.ready(client)) { return null; }
		Player player = client.getLocalPlayer();
		if (player == null) { return null; }
		NPC npc = player.getInteracting() instanceof NPC ? (NPC) player.getInteracting() : null;
		if (!matchesTarget(npc, summary)) { npc = lookupTarget; }
		return matchesTarget(npc, summary) && player.getWorldView() == npc.getWorldView()
			&& npc.getHealthRatio() != 0 && player.getWorldArea().distanceTo(npc.getWorldArea()) <= 128 ? npc : null;
	}

	/** Sections, names, descriptions and ranges of the config items, for the panels that edit them. */
	public ConfigDescriptor getConfigDescriptor()
	{
		return configManager.getConfigDescriptor(config);
	}

	/**
	 * Open RuneLite's color picker by the given component. {@code onChange} previews each change and
	 * {@code onClose} receives the color chosen when the picker is closed.
	 */
	public void pickColor(Component owner, Color current, String title, Consumer<Color> onChange, Consumer<Color> onClose)
	{
		RuneliteColorPicker picker = colorPickerManager.create(SwingUtilities.windowForComponent(owner), current, title, true);
		picker.setLocationRelativeTo(owner);
		picker.setOnColorChange(onChange);
		picker.setOnClose(onClose);
		picker.setVisible(true);
	}

	/** Open this plugin's page in RuneLite's configuration panel. */
	public void openConfiguration()
	{
		// The configuration plugin opens an overlay owner's settings for this menu action; it is the
		// supported way for a plugin to show its own configuration page.
		Overlay owner = new Overlay(this)
		{
			@Override
			public Dimension render(Graphics2D graphics)
			{
				return null;
			}
		};
		eventBus.post(new OverlayMenuClicked(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, "", ""), owner));
	}

	/** Persist a setting changed from the panel. */
	public void setConfig(String key, Object value)
	{
		configManager.setConfiguration(Options.GROUP, key, value);
	}
}
