package com.bestgearsetup;

import com.bestgearsetup.calc.AttackReach;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.CombatContext;
import com.bestgearsetup.calc.CombatModifiers;
import com.bestgearsetup.calc.CosmeticVariants;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.DrainSpecs;
import com.bestgearsetup.calc.DragonfireProtection;
import com.bestgearsetup.calc.EncounterPhases;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.MonsterStates;
import com.bestgearsetup.calc.Optimizer;
import com.bestgearsetup.calc.OptimizerSettings;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.PotionChoice;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.CalcMode;
import com.bestgearsetup.calc.FreezeDuty;
import com.bestgearsetup.calc.RaidLoadout;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SlayerEquipment;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.calc.SpecEnergy;
import com.bestgearsetup.calc.SpecialAttacks;
import com.bestgearsetup.calc.SpecialPlan;
import com.bestgearsetup.calc.SpecialRotation;
import com.bestgearsetup.calc.SupportSpell;
import com.bestgearsetup.calc.WeaponRules;
import com.bestgearsetup.calc.WildernessTargets;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.EquipmentRequirements;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.OffensivePrayer;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.StatusImmunities;
import com.bestgearsetup.ui.BestGearSetupPanel;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ChatMessageType;
import net.runelite.api.WorldView;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Best Gear Setup",
	description = "DPS calculator and gear optimizer for monsters, bosses and Slayer. Find best in slot gear using owned items or a budget.",
	tags = {"gear", "setup", "dps", "best", "bis", "calculator", "bank", "budget", "boss", "slayer", "optimizer",
		"equipment", "loadout", "pvm", "combat", "melee", "ranged", "magic", "accuracy", "maxhit", "ttk", "weapon",
		"armour", "armor"}
)
public class BestGearSetupPlugin extends Plugin
{
	private static final String MENU_OPTION = "Best setup";
	/** Ticks without combat before an encounter ends, about ten seconds: longer than any attack or phase gap. */
	private static final int ENGAGEMENT_TICKS = 16;
	/** RuneScape-profile key remembering whether the Elite Kourend & Kebos Diary is complete. */
	private static final String KOUREND_ELITE_KEY = "kourendEliteDiary";
	/** RuneScape-profile key remembering whether A Kingdom Divided (needed for thralls) is complete. */
	private static final String KINGDOM_DIVIDED_KEY = "kingdomDivided";
	/** How a raid plan values this player's defence reducers where the whole party opens with theirs. */
	static final String TEAM_OPENERS_NOTE = "Teammates also spec: at Olm's melee hand phases, Tekton, Maiden and Xarpus, "
		+ "each other player (up to seven) is assumed to open with one dragon warhammer special before yours; at Sotetseg "
		+ "the party shares two reducer attempts a third, at most one each. Their hammers use a modest reference accuracy "
		+ "(99 Attack, super combat, Piety, +120 crush) whatever you wear, so every class gets their drains and your own "
		+ "reducers are valued after theirs. You take your fair share too: if you can use an elder maul, dragon "
		+ "warhammer or Bandos godsword, every kit opens with one of them at each of these bosses (Sotetseg's share "
		+ "below), switching to it for the spec when the kit is ranged or magic, as far as your energy allows, even "
		+ "where a damage spec alone would be faster for you.";

	/** Your fair share of the party's Sotetseg reducers ({@link SpecialRotation#sotetsegShare}). */
	static String sotetsegShareNote(int party)
	{
		int perThird = SpecialRotation.sotetsegTeamPerThird(party);
		int share = SpecialRotation.sotetsegShare(party);
		return "Sotetseg fair share: of the party's " + perThird * SpecialRotation.SOTETSEG_THIRDS + " reducer attempts ("
			+ perThird + " a third), you are expected to take " + share + " with an elder maul, dragon warhammer or Bandos "
			+ "godsword, one in each of the first " + share + " thirds, as far as your energy allows; teammates don't cover "
			+ "a share you can't fund. Without one of those weapons, teammates make all of them. Your share is valued at "
			+ "your own reducer's accuracy against the reference teammate's, so a less accurate hammer than theirs can "
			+ "cost the party a little time.";
	}

	@Inject
	private Client client;

	@Inject
	ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;


	@Inject
	BestGearSetupConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private GameDataLoader api;

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
	StoredItemsBridge storedItemsBridge;

	@Inject
	private EventBus eventBus;

	@Inject
	private ColorPickerManager colorPickerManager;

	/** Immutable selection snapshot of the full kit for the bank renderer. */
	@Getter
	private volatile Set<Integer> bankHighlightIds = Collections.emptySet();
	/** Base items whose owned decorated alternatives should receive the bank outline instead. */
	private volatile Map<Integer, Set<Integer>> bankPreferredCosmetics = Collections.emptyMap();
	/** Only the equipment slots of the displayed setup, excluding carried switches and supplies. */
	@Getter
	private volatile Set<Integer> inventoryHighlightIds = Collections.emptySet();
	/** CM gear colors; null selects ordinary encounter highlighting. Previews never reach the overlay. */
	private volatile Map<Integer, java.awt.Color> coxChestHighlights;

	@Getter
	@Inject
	OwnedItems ownedItems;

	BestGearSetupPanel panel;
	private NavigationButton navButton;
	ExecutorService executor;
	/**
	 * Fixed helpers for raid consolidation, whose trials are searched at once (RaidConsolidator.Search#helpers).
	 * A fixed count, as for other Plugin Hub plugins: never sized from the machine's processors.
	 */
	volatile ExecutorService searchHelpers;
	/** Helper threads raid consolidation may use beside the search thread. */
	static final int SEARCH_HELPERS = 2;
	volatile GameData gameData;
	volatile Future<?> search;
	final AtomicInteger searchGeneration = new AtomicInteger();
	/** Bumped on startup and shutdown so loading work queued by an earlier run cannot publish into this one. */
	final AtomicInteger lifecycle = new AtomicInteger();
	private volatile Map<Integer, Long> prices = Collections.emptyMap();
	volatile MonsterSummary lastSearched;
	/** Akkha's live protection prayer the latest search used, or null when it read none. */
	volatile com.bestgearsetup.data.EncounterPhase searchedPhase;
	/** The latest search used specs landed on one NPC instance, so another instance needs its own search. */
	volatile boolean searchedLandedSpecs;
	/** The latest search failed, so engaging its target again should retry it. */
	volatile boolean searchFailed;
	/**
	 * The latest search read a live NPC (engaged or right-clicked): live raid scaling, the Slayer task's location
	 * and Karuulm. One planned from the panel alone didn't, so the first live engagement still searches.
	 */
	volatile boolean searchedLive;
	private volatile RaidPlan raidPlan;
	final RaidProgress raidProgress = new RaidProgress();
	private final Map<NPC, MonsterSummary> raidNpcTargets = new IdentityHashMap<>();
	private long publishedRaidProgress = -1;
	private int raidRegion = -1;
	private NPC raidLiveTarget;
	private volatile SearchInputs desiredRaidInputs;
	/** Only a published result can suppress another refresh with the same inputs. */
	private volatile SearchInputs publishedRaidInputs;
	private SearchInputs requestedRaidInputs;
	private Future<?> raidLiveSearch;
	private int raidLiveTick = -1000;
	/** Successful published models are read-only; keep a bounded set for repeated searches in this session. */
	final SearchCache<SearchInputs, RaidPlan> raidCache = new SearchCache<>(6);
	final SearchCache<SearchInputs, SearchResults> encounterCache = new SearchCache<>(192);
	/** The encounter searches inside raid searches; memory only, and never ahead of single-target results. */
	final SearchCache<SearchInputs, SearchResults> raidEncounterCache = new SearchCache<>(192);
	/** Equipment ids and their variants: the only ownership changes that can alter a result. */
	volatile Set<Integer> gearIds = Collections.emptySet();
	/** Last levels written to the profile; client thread only. */
	private String lastRememberedLevels;
	/** Ownership the latest search was computed with; null when no results depend on it. */
	volatile Set<Integer> searchOwned;
	/** The latest search's mode: Inventory + equipped only compares carried items alone. */
	volatile SearchMode searchMode;
	/** Stack sizes and ammo quantity the latest search was computed with. */
	volatile Map<Integer, Long> searchQuantities;
	volatile int searchAmmoCount;
	/** Client thread only: compare ownership after all container events for this game tick have arrived. */
	private boolean containersChanged;
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
	/** Read and written on the client thread only. */
	private NPC lookupTarget;
	private NPC activeTarget;
	/** The NPC you are in combat with and the tick combat with it was last seen. */
	private NPC engagedNpc;
	private int engagedTick;
	private int activeTargetId = -1;
	private com.bestgearsetup.data.EncounterPhase activeTargetPhase;
	private volatile int activeSequence;
	/** Swing thread only: whether this run has started loading the bundled data. */
	private boolean dataRequested;

	@Provides
	BestGearSetupConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BestGearSetupConfig.class);
	}

	@Override
	protected void startUp()
	{
		// Before any event handler can reach ownership rules (bank and inventory changes).
		OwnershipRules.init(gson);
		int life = lifecycle.incrementAndGet();
		clearSearchCaches();
		raidProgress.reset();
		// "Yama tank on Magic" (+60 Magic defence) became the default phase; drop the retired value.
		if ("YAMA_MAGIC_TANK".equals(configManager.getConfiguration(BestGearSetupConfig.GROUP,
			BestGearSetupConfig.PHASE_KEY)))
		{
			configManager.unsetConfiguration(BestGearSetupConfig.GROUP, BestGearSetupConfig.PHASE_KEY);
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
		searchHelpers = Executors.newFixedThreadPool(SEARCH_HELPERS, r ->
		{
			Thread t = new Thread(r, "best-gear-setup-helper");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			return t;
		});
		migratePreferences();
		panel = new BestGearSetupPanel(this, config, itemManager, spriteManager, skillIconManager);
		BufferedImage icon = BestGearSetupPanel.createNavIcon();
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
		// Detach the profile stores rather than attaching new ones: a stopped plugin must not write the profile.
		raidCache.profile(null);
		encounterCache.profile(null);
		raidEncounterCache.clear();
		raidProgress.reset();
		eventBus.unregister(storedItemsBridge);
		storedItemsBridge.reset();
		storedItemsBridge = null;
		overlayManager.remove(bankOverlay);
		overlayManager.remove(inventoryOverlay);
		eventBus.unregister(bankView);
		eventBus.unregister(bankButton);
		bankButton.stop();
		bankButton = null;
		eventBus.unregister(liveSpecs);
		liveSpecs = null;
		if (partySpecs != null)
		{
			eventBus.unregister(partySpecs);
			partySpecs = null;
		}
		setBankHighlightedSetup(null);
		// The lifecycle guard skips the usual refresh: release the layout and restore dragging unconditionally.
		clientThread.invokeLater(bankView::clear);
		clientToolbar.removeNavigation(navButton);
		BestGearSetupPanel stoppingPanel = panel;
		if (stoppingPanel != null) { SwingUtilities.invokeLater(stoppingPanel::stopSearchActivity); }
		// Running work stops itself on the lifecycle and search generations bumped above.
		executor.shutdown();
		executor = null;
		searchHelpers.shutdown();
		searchHelpers = null;
		panel = null;
		navButton = null;
		gameData = null;
		gearIds = Collections.emptySet();
		consumableIds = Collections.emptySet();
		searchOwned = null;
		searchQuantities = null;
		observedQuantities = null;
		lastSearched = null;
		raidPlan = null;
		lookupTarget = null;
		activeTarget = null;
		engagedNpc = null;
		activeSequence++;
		if (supplyRefresh != null)
		{
			supplyRefresh.stop();
		}
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
		BestGearSetupPanel target = panel;
		executor.submit(() -> loadGameData(life, target));
	}

	/**
	 * Load the bundled data for one plugin run. Results are published on the Swing thread, where startup and
	 * shutdown also run, and only while that run is current: a later run never receives an earlier run's data.
	 */
	private void loadGameData(int life, BestGearSetupPanel target)
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
		Set<Integer> ids = new HashSet<>();
		for (Slot slot : Slot.values())
		{
			for (GearItem item : data.getItems(slot))
			{
				ids.add(item.getId());
				ids.addAll(item.getVariants());
			}
		}
		return Collections.unmodifiableSet(ids);
	}

	static Set<Integer> consumableIds(GameData data)
	{
		Set<Integer> ids = new HashSet<>();
		for (Slot slot : Slot.values())
		{
			for (GearItem item : data.getItems(slot))
			{
				if (WeaponRules.consumedPerAttack(item))
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
					for (GearItem item : prepared.getItems(slot))
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
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			// A hop keeps the account, so its stored items stay owned; logging out may switch accounts.
			if (storedItemsBridge != null && event.getGameState() == GameState.LOGIN_SCREEN) { storedItemsBridge.reset(); }
			raidProgress.reset(); raidNpcTargets.clear(); raidRegion = -1;
			publishRaidProgress();
		}
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			clearLiveRaidInputs();
			activeTarget = null;
			engagedNpc = null;
			activeTargetId = -1;
			activeSequence++;
		}
		if (event.getGameState().getState() >= GameState.LOGIN_SCREEN.getState())
		{
			SwingUtilities.invokeLater(this::requestGameData);
		}
		if (event.getGameState().getState() < GameState.LOGGING_IN.getState())
		{
			ownedItems.clearEquipped();
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.updateOwnedStatus();
				}
			});
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
		ownedItems.rebuild(true);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.updateOwnedStatus();
			}
		});
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		ownedItems.onContainerChanged(event.getContainerId(), event.getItemContainer());
		int id = event.getContainerId();
		if (id == InventoryID.BANK || id == InventoryID.INV || id == InventoryID.WORN)
		{
			containersChanged = true;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.updateOwnedStatus();
			}
		});
	}

	/**
	 * Called after combined ownership settles: a withdrawal, deposit or equip moves items between containers
	 * without changing ownership. Individual events or ticks can see a temporary loss or double-counted stack.
	 * Results are valid only for the items owned when they were computed: if equipment was gained or lost since,
	 * discard them and search again with the new ownership. A changed ammunition or thrown-weapon stack that
	 * affects the requested ammo quantity searches again once the stack stops changing, so firing doesn't restart
	 * the search every attack. Food, potions and other non-gear changes are ignored.
	 */
	void invalidateIfOwnershipChanged()
	{
		// A live raid follows its packed kit; firing ammunition or picking up loot must not re-plan the raid.
		if (raidProgress.isActive()) { observedQuantities = null; return; }
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
		if (raidProgress.isActive()) { observedQuantities = null; return; }
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
		if (before == null || ammoCount <= 0)
		{
			return false;
		}
		for (int id : consumables)
		{
			if (Math.min(before.getOrDefault(id, 0L), ammoCount) != Math.min(now.getOrDefault(id, 0L), ammoCount))
			{
				return true;
			}
		}
		return false;
	}

	static boolean gearOwnershipDiffers(Set<Integer> before, Set<Integer> now, Set<Integer> gear)
	{
		for (int id : gear)
		{
			if (before.contains(id) != now.contains(id))
			{
				return true;
			}
		}
		return false;
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		activeTarget = null;
		engagedNpc = null;
		activeTargetId = -1;
		activeSequence++;
		int generation = searchGeneration.incrementAndGet();
		clearSearchCaches();
		raidProgress.reset(); raidNpcTargets.clear(); raidRegion = -1;
		setBankHighlightedSetup(null);
		if (search != null)
		{
			search.cancel(false);
		}
		lastSearched = null;
		raidPlan = null;
		searchOwned = null;
		searchQuantities = null;
		observedQuantities = null;
		lookupTarget = null;
		lastRememberedLevels = null;
		ownedItems.load();
		if (storedItemsBridge != null) { storedItemsBridge.reset(); }
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				if (generation == searchGeneration.get())
				{
					panel.clearResults("Account changed. Find best setup again to use this account's levels and items.");
					panel.showRaidProgress(RaidPlanType.SINGLE, null, "");
				}
				panel.syncFromConfig();
				panel.updateOwnedStatus();
			}
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (BestGearSetupConfig.GROUP.equals(event.getGroup()) && BestGearSetupConfig.BOSSES_ONLY_KEY.equals(event.getKey()) && panel != null)
		{
			SwingUtilities.invokeLater(() -> { if (panel != null) { panel.refreshSearchFilter(); } });
			return;
		}
		// Per-account keys (owned items) change with every bank update; they must not trigger a search. The
		// boss filter only changes search suggestions, and seen Fight options only the Fight button.
		if (!BestGearSetupConfig.GROUP.equals(event.getGroup()) || panel == null || event.getProfile() != null
			|| "showMenuOption".equals(event.getKey()) || BestGearSetupConfig.BOSSES_ONLY_KEY.equals(event.getKey())
			|| BestGearSetupConfig.SEEN_FIGHT_OPTIONS_KEY.equals(event.getKey())
			|| BestGearSetupConfig.HIGHLIGHT_BANK_GEAR_KEY.equals(event.getKey())
			|| BestGearSetupConfig.BANK_HIGHLIGHT_COLOR_KEY.equals(event.getKey())
			|| BestGearSetupConfig.HIGHLIGHT_INVENTORY_GEAR_KEY.equals(event.getKey())
			|| BestGearSetupConfig.INVENTORY_HIGHLIGHT_COLOR_KEY.equals(event.getKey()))
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.syncFromConfig();
			}
		});
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
		if (comp == null || comp.getActions() == null)
		{
			return false;
		}
		for (String action : comp.getActions())
		{
			if (action != null && "attack".equalsIgnoreCase(Text.removeTags(action)))
			{
				return true;
			}
		}
		return false;
	}

	/** Client thread: resolve the clicked NPC and open the panel on it. */
	private void lookupNpc(NPC npc)
	{
		lookupTarget = npc;
		int id = npc.getId();
		String name = npc.getName();
		int level = npc.getCombatLevel();
		com.bestgearsetup.data.EncounterPhase phase = LiveRaid.akkhaPhase(npc);
		SwingUtilities.invokeLater(() ->
		{
			if (panel == null)
			{
				return;
			}
			clientToolbar.openPanel(navButton);
			GameData data = gameData;
			if (data == null)
			{
				panel.showStatus("Game data is still loading, try again in a moment.");
				return;
			}
			MonsterSummary match = data.matchNpc(id, name, level);
			if (match == null)
			{
				panel.showSearch(name == null ? "" : Text.removeTags(name));
				panel.showError("No monster data for " + name + " (id " + id + ").");
				return;
			}
			if (!panel.selectRaidMonster(match, phase)) { panel.selectMonster(match, true); }
		});
	}

	// ------------------------------------------------------------ search

	/** Follow combat once per tick; phase changes and new same-name spawns each get fresh stats. */
	@Subscribe(priority = -1)
	public void onGameTick(GameTick event)
	{
		boolean trackingRaid = updateRaidProgress();
		// RuneLite replays all deferred container events before GameTick, so transfers are complete here.
		StoredItemsBridge bridge = storedItemsBridge;
		if (bridge != null && client.getGameState() == GameState.LOGGED_IN && bridge.request(containersChanged))
		{
			containersChanged = false;
			int life = lifecycle.get();
			// DWMS queues its reply on the client thread. Queue behind it before checking ownership.
			clientThread.invokeLater(() ->
			{
				if (life != lifecycle.get() || bridge != storedItemsBridge) { return; }
				settleOwnedItems(bridge.complete());
			});
		}
		else
		{
			containersChanged = false;
			settleOwnedItems(false);
		}
		if (trackingRaid) { updateLiveRaidEncounter(); return; }
		clearLiveRaidInputs();
		Player player = client.getLocalPlayer();
		boolean following = config.activeEncounter() && client.getGameState() == GameState.LOGGED_IN && player != null;
		// Targeting alone (clicking or walking up to an NPC) is not combat; it is once the NPC attacks you back.
		if (following && player.getInteracting() instanceof NPC && player.getInteracting().getInteracting() == player)
		{
			engage((NPC) player.getInteracting());
		}
		NPC npc = following ? engaged() : null;
		if (npc == null || !isAttackable(npc))
		{
			if (activeTarget != null) { activeTarget = null; activeTargetId = -1; activeSequence++; }
			return;
		}
		GameData data = gameData;
		com.bestgearsetup.data.EncounterPhase targetPhase = LiveRaid.akkhaPhase(npc);
		if (data == null || activeTarget == npc && activeTargetId == npc.getId() && activeTargetPhase == targetPhase) { return; }
		MonsterSummary match = data.matchNpc(npc.getId(), npc.getName(), npc.getCombatLevel());
		if (match == null) { return; }
		activeTarget = npc;
		activeTargetId = npc.getId();
		activeTargetPhase = targetPhase;
		lookupTarget = npc;
		if (liveSpecs != null) { liveSpecs.trackTarget(npc); }
		int sequence = ++activeSequence;
		int life = lifecycle.get();
		boolean shown = showsSameType(match, targetPhase);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null && config.activeEncounter() && sequence == activeSequence && life == lifecycle.get())
			{
				if (shown) { panel.showActiveMonster(match, targetPhase); }
				else { panel.selectActiveMonster(match); }
			}
		});
	}

	/** Publish one combined owned category only after its totals agree across consecutive ticks. */
	private void settleOwnedItems(boolean locationsChanged)
	{
		boolean changed = ownedItems.settle();
		if (changed) { invalidateIfOwnershipChanged(); }
		if (changed || locationsChanged)
		{
			SwingUtilities.invokeLater(() -> { if (panel != null) { panel.onManualOwnedChanged(); } });
		}
	}

	/**
	 * Whether the results already shown (or being searched) are for this type of monster: the same variant, the
	 * same live Akkha phase, found for a live NPC (so live raid scaling and the task location were read), and
	 * nothing specific to that NPC instance, such as specs landed on it. Another NPC of the same type, e.g. the
	 * next abyssal demon or wave nylocas, then needs no new search.
	 */
	boolean showsSameType(MonsterSummary match, com.bestgearsetup.data.EncounterPhase phase)
	{
		MonsterSummary last = lastSearched;
		return last != null && last.getId() == match.getId() && last.getName().equals(match.getName())
			&& searchedPhase == phase && searchedLive && !searchedLandedSpecs && !searchFailed;
	}

	/** Your own hits, blocked or not, mark the NPC you are fighting. */
	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (event.getActor() instanceof NPC && event.getHitsplat().isMine())
		{
			engage((NPC) event.getActor());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (event.getNpc() == raidLiveTarget)
		{ raidLiveTarget = null; clearLiveRaidInputs(); }
		if (event.getNpc() == engagedNpc) { engagedNpc = null; }
		raidNpcTargets.remove(event.getNpc());
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!raidProgress.isActive() || !(event.getActor() instanceof NPC) || gameData == null) { return; }
		NPC npc = (NPC) event.getActor();
		MonsterSummary target = raidNpcTargets.get(npc);
		if (target == null) { target = gameData.matchNpc(npc.getId(), npc.getName(), npc.getCombatLevel()); }
		if (target != null) { raidProgress.died(npc, target); }
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			raidProgress.message(Text.removeTags(event.getMessage()));
			publishRaidProgress();
		}
	}

	/** Client thread: progress uses the saved plan even when ordinary automatic encounter searches are disabled. */
	private boolean updateRaidProgress()
	{
		raidLiveTarget = null;
		if (panel == null || gameData == null) { return false; }
		RaidPlanType type = panel.getRaidType();
		if (type == null || type == RaidPlanType.SINGLE)
		{
			if (raidProgress.current().getType() != RaidPlanType.SINGLE)
			{ raidProgress.reset(); publishRaidProgress(); }
			raidNpcTargets.clear(); raidRegion = -1;
			return false;
		}
		if (client.getGameState() != GameState.LOGGED_IN) { return raidProgress.isActive(); }
		int region = LiveRaid.region(client);
		boolean inside = LiveRaid.inProgress(client, type, region);
		if (raidProgress.current().getType() != type) { raidNpcTargets.clear(); raidRegion = -1; }
		raidProgress.tick(type, raidPlan, inside);
		if (inside && raidProgress.isActive())
		{
			if (type == RaidPlanType.TOA && region != raidRegion)
			{
				String room = LiveRaid.toaRoom(region);
				if (room != null) { raidProgress.enteredRoom(room, LiveRaid.toaPuzzle(region)); }
			}
			raidRegion = region;
			Set<String> livingRooms = new HashSet<>();
			NPC vasa = null;
			WorldView world = client.getTopLevelWorldView();
			for (NPC npc : world == null ? Collections.<NPC>emptyList() : world.npcs())
			{
				if (npc == null || RaidProgress.room(type, npc.getName()) == null || npc.isDead() || npc.getHealthRatio() == 0) { continue; }
				if (type == RaidPlanType.COX_CM && (npc.getId() == NpcID.RAIDS_VASANISTIRIO_WALKING
					|| npc.getId() == NpcID.RAIDS_VASANISTIRIO_HEALING)) { vasa = npc; }
				MonsterSummary target = gameData.matchNpc(npc.getId(), npc.getName(), npc.getCombatLevel());
				if (target == null) { continue; }
				raidNpcTargets.put(npc, target);
				raidProgress.alive(npc, target);
				if (RaidProgress.coxCompletionTarget(target.getName())) { livingRooms.add(RaidProgress.room(type, target.getName())); }
			}
			Player player = client.getLocalPlayer();
			// A form change (the Nylocas Vasilias) drops your attack, so the NPC you're fighting leads until you reclick.
			NPC target = player != null && player.getInteracting() instanceof NPC ? (NPC) player.getInteracting() : engaged();
			// Live vulnerability leads over a stale click on Akkha or a shadow. Only inspect the actual boss room.
			if (type == RaidPlanType.TOA && region == 14676 && world != null)
			{
				NPC akkhaTarget = LiveRaid.akkhaTarget(world.npcs());
				if (akkhaTarget != null) { target = akkhaTarget; }
			}
			// Vasa's healing form opens crystal gear before a click, and his walking form restores Vasa gear.
			boolean vasaPhase = vasa != null && raidProgress.vasaPhase(vasa.getId() == NpcID.RAIDS_VASANISTIRIO_HEALING);
			LiveRaid.WardenTarget warden = type == RaidPlanType.TOA && region == 15184 && world != null
				? LiveRaid.wardenTarget(world.npcs()) : null;
			boolean wardenPhase = warden != null && raidProgress.wardenPhase(warden.getLabel());
			if (wardenPhase)
			{
				target = warden.getNpc();
				RaidPlan plan = raidPlan;
				if (target != null && plan != null)
				{
					NPC actual = target;
					plan.getEncounters().stream().filter(fight -> fight.getLabel().equals(warden.getLabel())).findFirst()
						.ifPresent(fight -> raidNpcTargets.put(actual, fight.getTarget()));
				}
			}
			if (!vasaPhase && !wardenPhase && target != null && !target.isDead() && target.getHealthRatio() != 0 && isAttackable(target))
			{
				MonsterSummary match = gameData.matchNpc(target.getId(), target.getName(), target.getCombatLevel());
				if (match != null) { raidProgress.target(match, LiveRaid.akkhaPhase(target)); }
			}
			raidProgress.settleDeaths(livingRooms);
			// Resolve the encounter chosen by room/form detection, including forms that drop the player's target.
			RaidPlan plan = raidPlan;
			RaidEncounter current = plan == null ? null : plan.getEncounters().stream()
				.filter(fight -> fight.getLabel().equals(raidProgress.current().getEncounter())).findFirst().orElse(null);
			if (current != null && !raidProgress.isFinished() && (!wardenPhase || target != null))
			{
				for (NPC npc : world == null ? Collections.<NPC>emptyList() : world.npcs())
				{
					MonsterSummary match = raidNpcTargets.get(npc);
					if (match != null && match.getName().equals(current.getTarget().getName())
						&& !npc.isDead() && npc.getHealthRatio() != 0 && isAttackable(npc))
					{
						raidLiveTarget = npc;
						if (npc == target) { break; }
					}
				}
			}
		}
		if (!raidProgress.isActive()) { raidNpcTargets.clear(); raidRegion = -1; }
		publishRaidProgress();
		return raidProgress.isActive();
	}

	/** Recalculate only the current fight from settled carried items; never rebuild or persist the packing plan. */
	void updateLiveRaidEncounter()
	{
		RaidPlan plan = raidPlan;
		NPC npc = raidLiveTarget;
		if (executor == null || plan == null || npc == null || raidProgress.isFinished()
			|| !raidProgress.isActive() || client.getGameState() != GameState.LOGGED_IN
			|| client.getItemContainer(InventoryID.INV) == null || client.getItemContainer(InventoryID.WORN) == null)
		{ clearLiveRaidInputs(); return; }
		// A transfer invalidates in-flight work, but may settle to the same normalized gear and spell supplies.
		if (!ownedItems.carriedSettled()) { desiredRaidInputs = null; return; }
		RaidEncounter encounter = plan.getEncounters().stream()
			.filter(fight -> fight.getLabel().equals(raidProgress.current().getEncounter())).findFirst().orElse(null);
		RememberedLevels levels = readLevels();
		if (encounter == null || levels == null || !levels.complete()) { desiredRaidInputs = null; return; }
		if (liveSpecs != null) { liveSpecs.trackTarget(npc); }
		LiveRaid live = LiveRaid.encounter(client, encounter.getTarget(), npc);
		live.encounterLabel = encounter.getLabel();
		if (live.combat.subList(0, 5).stream().anyMatch(level -> level <= 0)) { desiredRaidInputs = null; return; }
		OwnedItems.Snapshot carried = ownedItems.capture(SearchMode.INVENTORY_ONLY);
		live.spellSupplies = SpellRunes.carriedSupplies(plan, carried.getIds()::contains);
		Map<String, Object> overrides = new HashMap<>(live.values);
		List<String> notes = new ArrayList<>();
		detectThralls(overrides, notes);
		overrides.put("mode", SearchMode.INVENTORY_ONLY);
		overrides.put("ammoCount", 1);
		overrides.put("encounterPhase", encounter.getPhase());
		overrides.put("potionMelee", PotionChoice.NONE);
		overrides.put("potionRanged", PotionChoice.NONE);
		overrides.put("potionMagic", PotionChoice.NONE);
		if (liveSpecs != null) { overrides.putAll(liveSpecs.overrides(npc)); }
		BestGearSetupConfig assumptions = SearchAssumptions.capture(config, overrides);
		SearchInputs inputs = SearchInputs.capture(gameData, api, encounter.getTarget(), levels.getLevels(), false,
			Collections.emptySet(), null, false, levels.getMining(), prices, carried.getIds(), carried.getQuantities(),
			assumptions, live, Collections.emptyList(), true).with(System.identityHashCode(plan), npc.getIndex(), npc.getId(), encounter.getLabel());
		desiredRaidInputs = inputs;
		if (inputs.equals(publishedRaidInputs) || raidLiveSearch != null && !raidLiveSearch.isDone()
			|| search != null && !search.isDone()) { return; }
		if (requestedRaidInputs != null && client.getTickCount() - raidLiveTick < 3) { return; }
		requestedRaidInputs = inputs; raidLiveTick = client.getTickCount(); live.inputs = inputs;
		int generation = searchGeneration.incrementAndGet();
		Set<String> prayers = readPrayerUnlocks();
		Boolean diary = readKourendEliteDiary();
		GameData data = gameData;
		Map<Integer, Long> quotes = prices;
		notes.add("Live raid: best setup from your inventory and worn equipment. The pre-raid packing plan is unchanged.");
		notes.add("Uses current boosted levels, tracked landed defence reductions and " + live.combat.get(5) + "% special energy."
				+ (live.combat.get(6) == 0 ? " Target HP is unavailable; maximum HP is assumed."
					: " Target HP is estimated from its health bar, rounded up to the next 10%."));
		raidLiveSearch = executor.submit(() ->
		{
			try
			{
				refreshRaidEncounter(plan, data, encounter.getTarget(), levels.getLevels(), false, prayers, diary,
					levels.getMining(), quotes, carried.getIds(), carried.getQuantities(), generation, assumptions, live, notes, percent -> { });
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Live raid encounter refresh failed", e);
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null && generation == searchGeneration.get() && inputs.equals(desiredRaidInputs))
					{ panel.showStatus("Live encounter update failed; the displayed setup may be outdated."); }
				});
			}
		});
	}

	private void clearLiveRaidInputs()
	{
		desiredRaidInputs = null;
		requestedRaidInputs = null;
		publishedRaidInputs = null;
	}

	private void publishRaidProgress()
	{
		RaidProgress.Update update = raidProgress.current();
		if (publishedRaidProgress == update.getRevision()) { return; }
		publishedRaidProgress = update.getRevision();
		int life = lifecycle.get();
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null && life == lifecycle.get() && raidProgress.current().getRevision() == update.getRevision())
			{ panel.showRaidProgress(update.getType(), update.getEncounter(), update.getStatus()); }
		});
	}

	/** Client thread: record combat with an NPC. With several in combat (AoE, chinning) the one you target leads. */
	private void engage(NPC npc)
	{
		Player player = client.getLocalPlayer();
		if (npc != engagedNpc && engaged() != null && (player == null || player.getInteracting() != npc)) { return; }
		engagedNpc = npc;
		engagedTick = client.getTickCount();
	}

	/** Client thread: the NPC you are in combat with, or null once it has died or combat with it has lapsed. */
	private NPC engaged()
	{
		NPC npc = engagedNpc;
		if (npc != null && (npc.getHealthRatio() == 0 || npc.isDead() || client.getTickCount() - engagedTick > ENGAGEMENT_TICKS))
		{
			engagedNpc = null;
			return null;
		}
		return npc;
	}

	private boolean matchesTarget(NPC npc, MonsterSummary selected)
	{
		GameData data = gameData;
		if (npc == null || data == null || selected == null || npc.getWorldView() == null
			|| npc.getWorldView().npcs().byIndex(npc.getIndex()) != npc)
		{
			return false;
		}
		MonsterSummary match = data.matchNpc(npc.getId(), npc.getName(), npc.getCombatLevel());
		return match != null && match.getId() == selected.getId();
	}

	/**
	 * Start a best-setup search for the monster. Runs off the client thread; results are
	 * delivered to the panel on the Swing thread. A new search cancels the previous one.
	 */
	public void findBestSetup(MonsterSummary summary)
	{
		findBestSetup(summary, panel == null ? RaidPlanType.SINGLE : panel.getRaidType(), null);
	}

	public void findActiveSetup(MonsterSummary summary)
	{
		RaidPlanType type = panel == null ? RaidPlanType.SINGLE : panel.getRaidType();
		RaidPlan cached = raidPlan;
		findBestSetup(summary, type, cached != null && cached.getType() == type ? cached : null);
	}

	private void findBestSetup(MonsterSummary summary, RaidPlanType raidType, RaidPlan cached)
	{
		GameData data = gameData;
		if (data == null || executor == null)
		{
			return;
		}
		lastSearched = summary;
		searchFailed = false;
		searchedLandedSpecs = false;
		searchedLive = false;
		if (cached == null) { raidPlan = null; }
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
				configManager.getRSProfileConfiguration(BestGearSetupConfig.GROUP, RememberedLevels.CONFIG_KEY));
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
			detectThralls(detected, detectedNotes);
			NPC npc = matchingLiveTarget(summary);
			searchedLive = npc != null;
			com.bestgearsetup.data.EncounterPhase livePhase = LiveRaid.akkhaPhase(npc);
			if (livePhase != null) { detected.put("encounterPhase", livePhase); }
			searchedPhase = livePhase;
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
			String taskNote = config.taskMode() == AutoState.AUTO
				? "Slayer task: " + (matched == null ? "unknown; task bonus not assumed. Use Fight to override." : onTask ? "matched to this target." : "this target is off task.")
				: "Slayer task: " + config.taskMode() + " (planning override).";
			taskNote += " " + AccountFacts.describe(assignment);
			if (matched == null && assignment != null && assignment.locationRestricted)
			{ taskNote += " The assigned location could not be verified for this search."; }
			if (npc != null && assignment != null && assignment.locationRestricted)
			{ taskNote += " Checked the target's region " + taskLocation.getRegionID() + ", floor " + taskLocation.getPlane() + "."; }
			else if (Boolean.TRUE.equals(matched) && assignment.locationRestricted)
			{ taskNote += " Assumes you fight it at the assigned location; right-click the monster there to check."; }
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
			Map<String, Object> landed = liveSpecs == null ? Collections.emptyMap() : liveSpecs.overrides(npc);
			detected.putAll(landed);
			searchedLandedSpecs = !landed.isEmpty();
			if (!landed.isEmpty())
			{ detectedNotes.add("Pre-fight preparation: specs landed on this target so far, in place of the Fight settings."); }
			detected.put("potionMelee", getPotionChoice(CombatClass.MELEE));
			detected.put("potionRanged", getPotionChoice(CombatClass.RANGED));
			detected.put("potionMagic", getPotionChoice(CombatClass.MAGIC));
			BestGearSetupConfig assumptions = SearchAssumptions.capture(config, detected);
			String assumptionDetail = taskNote;
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null && generation == searchGeneration.get())
				{ panel.showAssumptions("Task: " + (matched == null && assumptions.taskMode() == AutoState.AUTO ? "unknown" : onTask ? "on" : "off")
					+ (assumptions.taskMode() == AutoState.AUTO ? " (Auto)" : " (override)"), assumptionDetail); }
			});
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
			search = executor.submit(() -> runSearch(data, summary, levels, rememberedLevels, prayerUnlocks,
				eliteDiary, karuulmDungeon, mining, quotes, owned, quantities, generation, assumptions, liveRaid, detectedNotes, raidType, cached));
		});
	}

	/** Specs landed on a live target: refresh the open result if it is that target. */
	private void liveSpecsChanged(String target)
	{
		// The next GameTick refreshes the raid from a complete NPC/container snapshot and coalesces spec hits.
		if (raidProgress.isActive()) { desiredRaidInputs = null; return; }
		MonsterSummary last = lastSearched;
		if (last != null && LiveSpecs.sameTarget(target, last.getName()))
		{
			if (config.activeEncounter())
			{
				int generation = searchGeneration.get();
				int life = lifecycle.get();
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null && life == lifecycle.get() && generation == searchGeneration.get() && last == lastSearched)
					{ findActiveSetup(last); }
				});
			}
			else { rerun(); }
		}
	}

	/** The logged-in account's base levels, or null when logged out. Client thread only. */
	private RememberedLevels readLevels()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		return new RememberedLevels(new PlayerLevels(
			client.getRealSkillLevel(Skill.ATTACK),
			client.getRealSkillLevel(Skill.STRENGTH),
			client.getRealSkillLevel(Skill.DEFENCE),
			client.getRealSkillLevel(Skill.RANGED),
			client.getRealSkillLevel(Skill.MAGIC),
			client.getRealSkillLevel(Skill.PRAYER),
			client.getRealSkillLevel(Skill.HITPOINTS),
			client.getRealSkillLevel(Skill.SLAYER)),
			client.getRealSkillLevel(Skill.MINING));
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
			configManager.setRSProfileConfiguration(BestGearSetupConfig.GROUP, RememberedLevels.CONFIG_KEY, value);
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
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			boolean complete = client.getVarbitValue(VarbitID.KOUREND_DIARY_ELITE_COMPLETE) == 1;
			configManager.setRSProfileConfiguration(BestGearSetupConfig.GROUP, KOUREND_ELITE_KEY, complete);
			return complete;
		}
		String remembered = configManager.getRSProfileConfiguration(BestGearSetupConfig.GROUP, KOUREND_ELITE_KEY);
		return remembered == null ? null : Boolean.valueOf(remembered);
	}

	/**
	 * Whether A Kingdom Divided is complete (thralls need it): read while logged in and remembered for the
	 * profile, so logged-out searches use the last known state. Null if never seen. Client thread only.
	 */
	private Boolean readKingdomDivided()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			boolean complete = Quest.A_KINGDOM_DIVIDED.getState(client) == QuestState.FINISHED;
			configManager.setRSProfileConfiguration(BestGearSetupConfig.GROUP, KINGDOM_DIVIDED_KEY, complete);
			return complete;
		}
		String remembered = configManager.getRSProfileConfiguration(BestGearSetupConfig.GROUP, KINGDOM_DIVIDED_KEY);
		return remembered == null ? null : Boolean.valueOf(remembered);
	}

	/** Apply the same account and spellbook eligibility to planned searches and live encounter refreshes. */
	private void detectThralls(Map<String, Object> detected, List<String> notes)
	{
		if (!config.thrall()) { return; }
		Boolean kingdomDivided = readKingdomDivided();
		detected.put("thrall", config.arceuusSpellbook() && !Boolean.FALSE.equals(kingdomDivided));
		if (!config.arceuusSpellbook())
		{
			notes.add("Thralls: not added, because the Arceuus spellbook isn't allowed under Spellbooks.");
		}
		else if (kingdomDivided == null)
		{
			notes.add("Thralls: A Kingdom Divided is unknown until you log in; thralls are assumed available.");
		}
		else if (!kingdomDivided)
		{
			notes.add("Thralls: not added, because A Kingdom Divided isn't complete.");
		}
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
			configManager.setRSProfileConfiguration(BestGearSetupConfig.GROUP, PrayerUnlocks.CONFIG_KEY,
				PrayerUnlocks.serialize(unlocked));
			return unlocked;
		}
		return PrayerUnlocks.parse(configManager.getRSProfileConfiguration(BestGearSetupConfig.GROUP,
			PrayerUnlocks.CONFIG_KEY));
	}

	private void runSearch(GameData data, MonsterSummary summary, PlayerLevels knownLevels, boolean rememberedLevels,
		Set<String> prayerUnlocks, Boolean kourendElite, boolean karuulmDungeon, int mining, Map<Integer, Long> quotes, Set<Integer> owned,
		Map<Integer, Long> quantities, int generation, BestGearSetupConfig config, LiveRaid liveRaid, List<String> detectedNotes, RaidPlanType raidType, RaidPlan cached)
	{
		try
		{
			if (raidType != RaidPlanType.SINGLE)
			{
				if (cached != null && refreshRaidEncounter(cached, data, summary, knownLevels, rememberedLevels, prayerUnlocks, kourendElite, mining, quotes, owned, quantities, generation, config, liveRaid, detectedNotes, percent -> publishSearchProgress(generation, percent * 90 / 100))) { return; }
				runRaidSearch(data, summary, knownLevels, rememberedLevels, prayerUnlocks, kourendElite, mining, quotes, owned, quantities, generation, config, liveRaid, detectedNotes, raidType, percent -> publishSearchProgress(generation, cached == null ? percent : 90 + percent * 9 / 100));
				return;
			}
			SearchResults found = computeSearch(data, summary, knownLevels, rememberedLevels, prayerUnlocks, kourendElite, karuulmDungeon, mining, quotes, owned, quantities, generation, config, liveRaid, detectedNotes, Collections.emptySet(), Collections.emptySet(), null, RaidLoadout.ANY, percent -> publishSearchProgress(generation, percent));
			SwingUtilities.invokeLater(() ->
			{
				if (found != null && panel != null && generation == searchGeneration.get()) { panel.showProgress(100); panel.showResults(found); }
			});
		}
		catch (IOException | RuntimeException | Error e)
		{
			// Errors (e.g. assertion failures) would otherwise vanish inside the executor's Future.
			log.warn("Best setup search failed", e);
			if (generation == searchGeneration.get()) { searchFailed = true; }
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null && generation == searchGeneration.get())
				{
					// The previous results and layout were kept for this search to replace; nothing will now.
					panel.clearResults("");
					panel.showError("Search failed: " + e.getMessage());
				}
			});
		}
	}

	/** Only successful result publication may display 100%; stale searches cannot update the bar. */
	private void publishSearchProgress(int generation, double percent)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null && generation == searchGeneration.get()) { panel.showProgress(Math.min(99.99, percent)); }
		});
	}

	/** Stage updates have the same generation guard as percentages, so a cancelled search cannot replace them. */
	private void publishSearchActivity(int generation, String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null && generation == searchGeneration.get()) { panel.showSearchActivity(message); }
		});
	}

	private SearchResults computeSearch(GameData data, MonsterSummary summary, PlayerLevels knownLevels, boolean rememberedLevels,
		Set<String> prayerUnlocks, Boolean kourendElite, boolean karuulmDungeon, int mining, Map<Integer, Long> quotes, Set<Integer> owned,
		Map<Integer, Long> quantities, int generation, BestGearSetupConfig config, LiveRaid liveRaid, List<String> detectedNotes, Set<AttackStyle.Type> allowedStyles, Set<Integer> extraExcluded, RaidSpellbook spellbook, RaidLoadout raidLoadout, DoubleConsumer publishProgress) throws IOException
	{
		return computeSearch(data, summary, knownLevels, rememberedLevels, prayerUnlocks, kourendElite, karuulmDungeon, mining, quotes,
			owned, quantities, generation, config, liveRaid, detectedNotes, allowedStyles, extraExcluded, spellbook, raidLoadout,
			publishProgress, null, null);
	}

	/**
	 * @param sharedTabs one encounter's Optimizer results from other spellbooks and path orders of the same raid search, keyed by
	 *                   everything else that can differ between them; null when not shared
	 * @param switchFrom the worn setup a raid quick switch keeps all but the weapon and a few other pieces of (the
	 *                   quick switch limit); null for none
	 */
	private SearchResults computeSearch(GameData data, MonsterSummary summary, PlayerLevels knownLevels, boolean rememberedLevels,
		Set<String> prayerUnlocks, Boolean kourendElite, boolean karuulmDungeon, int mining, Map<Integer, Long> quotes, Set<Integer> owned,
		Map<Integer, Long> quantities, int generation, BestGearSetupConfig config, LiveRaid liveRaid, List<String> detectedNotes, Set<AttackStyle.Type> allowedStyles, Set<Integer> extraExcluded, RaidSpellbook spellbook, RaidLoadout raidLoadout, DoubleConsumer publishProgress,
		Map<List<Object>, List<SetupResult>> sharedTabs, Loadout switchFrom) throws IOException
	{
		if (generation != searchGeneration.get()) { return null; }
		// Raid searches run thousands of these, so they get their own memory-only cache: saving each one to the
		// profile cost more than the search itself, and they would push the player's single-target results out.
		SearchCache<SearchInputs, SearchResults> cache = spellbook == null ? encounterCache : raidEncounterCache;
		long epoch = cache.epoch();
		SearchInputs inputs = SearchInputs.capture(data, api, summary, knownLevels, rememberedLevels, prayerUnlocks,
			kourendElite, karuulmDungeon, mining, quotes, owned, quantities, config, liveRaid, detectedNotes, true)
			.with(allowedStyles, extraExcluded, spellbook, raidLoadout, sharedTabs != null, SearchInputs.loadout(switchFrom));
		BoostTiming timing = boostTiming(config, knownLevels);
		SearchResults reused = cache.get(inputs, epoch);
		if (reused != null)
		{
			publishProgress.accept(100);
			return reused.withBoostTiming(timing);
		}
		SearchResults found = searchEncounter(data, summary, knownLevels, rememberedLevels, prayerUnlocks, kourendElite,
			karuulmDungeon, mining, quotes, owned, quantities, generation, config, liveRaid, detectedNotes, allowedStyles,
			extraExcluded, spellbook, raidLoadout, publishProgress, sharedTabs, switchFrom);
		if (found != null) { found = found.withBoostTiming(timing); }
		if (generation == searchGeneration.get()) { cache.put(inputs, found, epoch); }
		return found;
	}

	/** Raid boost dose timing: a kit setting, so it is applied after the encounter search cache. */
	private static BoostTiming boostTiming(BestGearSetupConfig config, PlayerLevels knownLevels)
	{
		return new BoostTiming(config.divinePotions(), config.repotBelow(),
			knownLevels == null ? PlayerLevels.maxed() : knownLevels);
	}

	private SearchResults searchEncounter(GameData data, MonsterSummary summary, PlayerLevels knownLevels, boolean rememberedLevels,
		Set<String> prayerUnlocks, Boolean kourendElite, boolean karuulmDungeon, int mining, Map<Integer, Long> quotes, Set<Integer> owned,
		Map<Integer, Long> quantities, int generation, BestGearSetupConfig config, LiveRaid liveRaid, List<String> detectedNotes,
		Set<AttackStyle.Type> allowedStyles, Set<Integer> extraExcluded, RaidSpellbook spellbook, RaidLoadout raidLoadout,
		DoubleConsumer publishProgress, Map<List<Object>, List<SetupResult>> sharedTabs, Loadout switchFrom) throws IOException
	{
		boolean assumedLevels = knownLevels == null;
		PlayerLevels levels = assumedLevels ? PlayerLevels.maxed() : knownLevels;
		publishSearchActivity(generation, "Loading target stats");
		Monster baseMonster = api.getMonster(summary);
		SpecialAttacks specials = specials(config);
		// Wiki order: raid scaling, health-dependent stats, then pre-fight defence reductions.
		RaidScaling.Settings raid = raidSettings(levels, mining, config);
		raid = ToaPathLevels.resolve(summary, raid, config, liveRaid, spellbook != null);
		if (liveRaid.maxCombat > 0 && liveRaid.maxCombat <= 126) { raid = raid.toBuilder().partyMaxCombat(liveRaid.maxCombat).build(); }
		Monster scaled = RaidScaling.apply(baseMonster, raid);
		scaled.setPhase(config.encounterPhase());
		EncounterPhases.applyStats(scaled);
		Monster monster = specials.apply(MonsterStates.atHealth(scaled, 0));

		boolean eliteDiary = Boolean.TRUE.equals(kourendElite);
		OptimizerSettings settings = buildSettings(config).toBuilder().kourendEliteDiary(eliteDiary)
			.teamOpeners(teamOpeners(config, monster, specials, sharedTabs != null))
			.karuulmDungeon(karuulmDungeon).raidLoadout(raidLoadout).switchFrom(switchFrom)
			.switchLimit(Math.max(1, Math.min(RaidEncounter.MAX_SWITCH_LIMIT, config.quickSwitchLimit()))).build();
		if (liveRaid.adaptive())
		{
			settings = settings.toBuilder().teamOpeners(false).raidOpeningEnergy(liveRaid.combat.get(5)).build();
		}
		if (raidLoadout == RaidLoadout.ICE_BARRAGE)
		{
			// A freeze is judged by how often each cast lands, switching as few items as reach the target.
			settings = settings.toBuilder().calcMode(CalcMode.ACCURACY).freezeAccuracy(FreezeDuty.GUARANTEED)
				.switchLimit(RaidEncounter.FREEZE_SWITCH_LIMIT).build();
		}
		if (!allowedStyles.isEmpty())
		{
			Set<AttackStyle.Type> styles = EnumSet.noneOf(AttackStyle.Type.class);
			styles.addAll(settings.getStyles());
			styles.retainAll(allowedStyles);
			settings = settings.toBuilder().styles(styles).build();
		}
		Set<Integer> excludedSupplies = new HashSet<>(settings.getExcluded());
		Set<Integer> excluded = new HashSet<>(settings.getExcluded());
		excluded.addAll(extraExcluded);
		settings = settings.toBuilder().excluded(excluded).build();
		if (spellbook != null)
		{
			Set<String> books = new HashSet<>(settings.getSpellbooks());
			books.retainAll(Collections.singleton(spellbook.key()));
			settings = settings.toBuilder().spellbooks(books).build();
		}
		Map<CombatClass, OffensivePrayer> prayers = new EnumMap<>(CombatClass.class);
		if (config.usePrayers())
		{
			Predicate<String> available = PrayerUnlocks.available(prayerUnlocks);
			for (CombatClass cls : CombatClass.values())
			{
				OffensivePrayer p = PrayerUnlocks.best(data.getPrayers(cls), levels, available);
				if (p != null)
				{
					prayers.put(cls, p);
				}
			}
		}
		Predicate<Potion> boosts = BoostAccess.allowed(settings.getMode(), owned, config.raidPotions())
			.and(potion -> !excludedSupplies.contains(potion.getId()));
		CombatContext ctx = new CombatContext(monster, levels, config.onSlayerTask(),
			PotionChoice.boostedLevel(data, "attack", levels.getAttack(), potionChoice(config, CombatClass.MELEE), boosts, monster),
			PotionChoice.boostedLevel(data, "strength", levels.getStrength(), potionChoice(config, CombatClass.MELEE), boosts, monster),
			PotionChoice.boostedLevel(data, "ranged", levels.getRanged(), potionChoice(config, CombatClass.RANGED), boosts, monster),
			PotionChoice.boostedLevel(data, "magic", levels.getMagic(), potionChoice(config, CombatClass.MAGIC), boosts, monster),
			prayers, config.thrall() && (spellbook == null || spellbook == RaidSpellbook.ARCEUUS)
				&& SpellRunes.suppliesAllowed(new Loadout(), EnumSet.of(SupportSpell.THRALL), excludedSupplies))
			.withFightOptions(config.aoe() ? config.aoeTargets() : 1, 0)
			.withModifiers(CombatModifiers.builder().currentHitpoints(config.currentHitpoints())
				.wilderness(config.wilderness())
				.forinthrySurge(config.forinthrySurge())
				.weaponPoison(config.weaponPoison()).charge(config.charge()
					&& SpellRunes.suppliesAllowed(new Loadout(), EnumSet.of(SupportSpell.CHARGE), excludedSupplies))
				.markOfDarkness(config.markOfDarkness()
					&& SpellRunes.suppliesAllowed(new Loadout(), EnumSet.of(SupportSpell.MARK_OF_DARKNESS), excludedSupplies))
				.sunfireRunes(config.sunfireRunes())
				.kandarinDiary(config.kandarinDiary()).soulreaperStacks(config.soulreaperStacks())
				.miningLevel(config.miningLevel() == 0 ? mining : config.miningLevel()).build())
			// HP-dependent target stats are re-derived in the same order for whole-fight averages.
			.withHealthStates(hp -> specials.apply(MonsterStates.atHealth(scaled, hp)));
		if (liveRaid.adaptive())
		{
			int hp = liveRaid.combat.get(6) == 0 ? scaled.getHitpoints()
				: Math.max(1, (scaled.getHitpoints() * liveRaid.combat.get(6) + 9) / 10);
			Monster current = specials.apply(MonsterStates.atHealth(scaled, hp));
			ctx = new CombatContext(current, levels, config.onSlayerTask(), liveRaid.combat.get(0), liveRaid.combat.get(1),
				liveRaid.combat.get(2), liveRaid.combat.get(3), prayers, ctx.isThrall())
				.withFightOptions(config.aoe() ? config.aoeTargets() : 1, 0)
				.withModifiers(ctx.getModifiers().toBuilder().currentHitpoints(liveRaid.combat.get(4)).monsterHitpoints(hp).build())
				.withHealthStates(health -> specials.apply(MonsterStates.atHealth(scaled, health)));
			monster = current;
		}
		if (settings.isTeamOpeners())
		{
			// Only the player's own exclusions count: a reducer the kit planner leaves out of this search still owes its
			// Sotetseg share, while one the player can't equip or use doesn't.
			Set<Integer> kitOnly = new HashSet<>(extraExcluded);
			kitOnly.removeAll(excludedSupplies);
			settings = settings.toBuilder().sharedReducerUsable(new Optimizer(data, ctx, settings, owned::contains,
				id -> quantities.getOrDefault(id, 0L), item -> price(quotes, item)).sharedReducerUsable(kitOnly)).build();
		}
		// One tab per attack type: melee is optimised separately for stab, slash and crush.
		List<AttackStyle.Type> types = new ArrayList<>();
		for (AttackStyle.Type type : AttackStyle.Type.values())
		{
			if (settings.getStyles().contains(type))
			{
				types.add(type);
			}
		}
		// A newer search, a cleared search or shutdown bumps the generation.
		BooleanSupplier cancelled = () -> generation != searchGeneration.get();
		SearchProgress progress = new SearchProgress(types.size(), publishProgress);
		List<List<SetupResult>> tabs = new ArrayList<>();
		publishSearchActivity(generation, "Comparing equipment");
		for (int i = 0; i < types.size() && !cancelled.getAsBoolean(); i++)
		{
			int tab = i;
			AttackStyle.Type type = types.get(i);
			OptimizerSettings typeSettings = settings.toBuilder().styles(EnumSet.of(type)).build();
			// Spellbooks only decide which spells the magic tab may cast. The other tabs repeat across a raid's
			// spellbooks unless thralls (Arceuus), boosted levels (a quick switch searched without its boost) or
			// another fight modifier differ, which the key includes. Consolidation's trial searches each exclude a
			// different item from nearly all gear, so no other search repeats them: keeping them would only hold
			// thousands of results (and their exclusions) until the raid search ends.
			List<Object> shared = sharedTabs == null || !extraExcluded.isEmpty() || classOf(type) == CombatClass.MAGIC ? null
				: Arrays.asList(typeSettings.toBuilder().spellbooks(Collections.emptySet()).build(), ctx.isThrall(), ctx.getModifiers(),
						ctx.getAttack(), ctx.getStrength(), ctx.getRanged(), ctx.getMagic());
			List<SetupResult> setups = shared == null ? null : sharedTabs.get(shared);
			if (setups == null)
			{
				setups = new Optimizer(data, ctx, typeSettings, owned::contains, id -> quantities.getOrDefault(id, 0L),
					item -> price(quotes, item)).optimize(classOf(type), cancelled, done -> progress.update(tab, done));
				if (shared != null && !cancelled.getAsBoolean()) { sharedTabs.put(shared, setups); }
			}
			else { progress.update(tab, 1); }
			tabs.add(setups);
		}
		if (cancelled.getAsBoolean())
		{
			return null;
		}
		Map<AttackStyle.Type, List<SetupResult>> byType = new LinkedHashMap<>();
		for (int i = 0; i < types.size(); i++)
		{
			if (!tabs.get(i).isEmpty())
			{
				byType.put(types.get(i), tabs.get(i));
			}
		}
		if (generation != searchGeneration.get())
		{
			return null;
		}
		List<String> notes = describeSearch(data, levels, baseMonster, scaled, monster, specials, ctx, settings, raid, config);
		notes.addAll(detectedNotes);
		if (config.usePrayers() && prayerUnlocks == null)
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
		publishSearchActivity(generation, "Checking gear rules");
		if (settings.isTeamOpeners())
		{
			notes.add(TEAM_OPENERS_NOTE + " Each attempt takes a full attack interval, hits and misses are weighted, "
				+ "and their energy is separate from yours. Live refreshes use detected or manual drains instead.");
			if (monster.getLowerName().startsWith("sotetseg")) { notes.add(sotetsegShareNote(settings.getRaidKillShare())); }
		}
		List<LockStatus> locks = LockStatus.check(data,
			new Optimizer(data, ctx, settings, owned::contains, id -> quantities.getOrDefault(id, 0L),
				item -> price(quotes, item)), settings.getLocks());
		SearchResults found = new SearchResults(monster, ctx.getTargetHitpoints(), byType, notes, ctx.getPrayers(),
			potionsUsed(data, levels, monster, boosts, config), assumedLevels, locks, config.markOfDarkness(),
			rememberedLevels, assumptionWarnings(data, levels, monster, settings.getMode(), boosts, config),
			monster.isImmuneThrall() ? null : ctx.getThrall(), raidScaling(baseMonster, raid, liveRaid.raidDetected),
			scaled.getDefenceLevel(), levels.getPrayer()).withProtectionContext(
				// Prayer advice reads only the fight settings; a raid consolidation search excludes thousands of items,
				// which every kept result would otherwise hold on to.
				new com.bestgearsetup.calc.ProtectionPrayers.Context(scaled.copy(), levels,
					settings.toBuilder().excluded(Collections.emptySet()).build()));
		if (cancelled.getAsBoolean()) { return null; }
		progress.complete();
		Map<AttackStyle.Type, List<SetupResult>> supplied = new LinkedHashMap<>();
		byType.forEach((type, setups) ->
		{
			List<SetupResult> usable = setups.stream().filter(setup -> SpellRunes.suppliesAllowed(setup.getLoadout(), setup.getSupportSpells(), excludedSupplies))
				.filter(setup -> found.potionsForSetup(setup).stream().noneMatch(potion -> excludedSupplies.contains(potion.getId())))
				.collect(Collectors.toList());
			if (!usable.isEmpty()) { supplied.put(type, usable); }
		});
		return found.withCandidates(supplied);
	}


	/** Refresh one fight using only the raid kit already selected, without re-planning every boss. */
	private boolean refreshRaidEncounter(RaidPlan plan, GameData data, MonsterSummary summary, PlayerLevels levels,
		boolean remembered, Set<String> prayerUnlocks, Boolean diary, int mining, Map<Integer, Long> quotes,
		Set<Integer> owned, Map<Integer, Long> quantities, int generation, BestGearSetupConfig config,
		LiveRaid liveRaid, List<String> notes, DoubleConsumer publishProgress) throws IOException
	{
		RaidEncounter encounter = liveRaid.encounterLabel == null ? plan.encounterFor(summary.getName(), config.encounterPhase())
			: plan.getEncounters().stream().filter(fight -> fight.getLabel().equals(liveRaid.encounterLabel)).findFirst().orElse(null);
		if (encounter == null) { return false; }
		Set<Integer> excluded = liveRaid.adaptive() ? Collections.emptySet() : outsideKit(plan, data);
		Set<Integer> encounterOwned = new HashSet<>(owned);
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("coxChallengeMode", plan.getType() == RaidPlanType.COX_CM);
		overrides.put("encounterPhase", encounter.getPhase());
		overrides.put("onSlayerTask", false);
		overrides.put("aoe", false);
		overrides.put("wilderness", false);
		if (!encounter.isRaidSupplies()) { overrides.put("raidPotions", false); }
		if (!liveRaid.adaptive())
		{
			if (encounter.isUnboosted()) { unboostAll(overrides, config); }
			freezeHeart(overrides, encounter, config, data, owned);
		}
		// Path levels follow the order the plan was made in, which Auto chose.
		if (plan.getType() == RaidPlanType.TOA) { overrides.put("toaPathOrder", ToaPathLevels.orderOf(plan.getEncounters())); }
		// A quick switch stays a switch from the plan's full setup, within the plan's limit and with the boost it gave it.
		overrides.put("quickSwitchLimit", encounter.getSwitchLimit());
		// Spec energy follows the plan's rooms: a setup opens on a full bar only where the plan spent one.
		overrides.put("specEnergy", SpecEnergy.FULL_BAR);
		overrides.put("raidKillShare", liveRaid.adaptive() && encounter.isShared()
			? liveRaid.values.getOrDefault("raidPartySize", encounter.getKillShare()) : encounter.getKillShare());
		overrides.put("raidFinalBlowPercent", (int) Math.round(encounter.getFinalBlow() * 100));
		int index = plan.getEncounters().indexOf(encounter);
		boolean fullBar = plan.getSetups().get(index).usesFullBar();
		boolean unknownHandPhase = summary.getName().startsWith("great olm") && summary.getName().contains("hand")
			&& plan.getEncounters().stream().filter(e -> e.getTarget().getName().equals(summary.getName())).count() > 1;
		if (unknownHandPhase) { fullBar = false; }
		// The switch group holding this fight (groups don't overlap) and the plan's full setup in it, when it has one.
		List<int[]> groups = RaidEncounter.switchGroups(plan.getEncounters());
		int[] full = RaidPlanner.fullSetupChoice(plan.getEncounters(), plan.getResults(), plan.getSetups());
		int[] switchGroup = null;
		int fullSetup = -1;
		for (int group = 0; full != null && switchGroup == null && group < groups.size(); group++)
		{
			if (Arrays.stream(groups.get(group)).anyMatch(member -> member == index))
			{
				switchGroup = groups.get(group);
				fullSetup = full[group];
			}
		}
		Loadout switchFrom = null;
		if (switchGroup != null && fullSetup != index)
		{
			switchFrom = plan.getSetups().get(fullSetup).getLoadout();
			if (!liveRaid.adaptive() && plan.getResults().get(index).potionsForSetup(plan.getSetups().get(index)).isEmpty())
			{ unboost(overrides, config, encounter.getFixedClass()); }
		}
		List<String> styleWarnings = new ArrayList<>();
		if (unknownHandPhase && !liveRaid.adaptive())
		{
			styleWarnings.add("Live Olm hand phase is unknown: this refresh shows ordinary attacks and regenerated damage specs only. "
				+ "Use the planned phase rows for entry-funded defence reducers.");
		}
		Set<AttackStyle.Type> styles = raidStyles(encounter, config, styleWarnings);
		BestGearSetupConfig assumptions = SearchAssumptions.capture(config, overrides);
		SearchResults searched = computeSearch(data, summary, levels, remembered, prayerUnlocks, diary, false, mining,
			quotes, encounterOwned, quantities, generation, assumptions, liveRaid, notes,
			styles, excluded, plan.getSpellbook(), encounter.getLoadoutRequirement(), publishProgress, null, switchFrom);
		if (searched == null) { return true; }
		if (searched.hasBlockingLock() && !liveRaid.adaptive()) { return false; }
		if (encounter.isPrepotted()) { searched = searched.withPrepotted(); }
		searched = compatibleSwitches(plan, liveRaid.adaptive() ? searched : searched.withRegeneratedChoices(), index, switchGroup, fullSetup);
		// The freeze keeps its own goal in the kit: the fewest items reaching the target, not the most damage.
		java.util.Comparator<SetupResult> preference = encounter.isFreeze()
			? FreezeDuty.preference(FreezeDuty.GUARANTEED).reversed() : PRIMARY_DPS;
		SetupResult best = liveRaid.adaptive() ? bestCarried(searched, liveRaid.spellSupplies, preference)
			: bestInKit(plan, searched, owned::contains, fullBar, preference);
		if (best == null && !styles.equals(encounter.getRequiredStyles()))
		{
			// The plan may have widened this encounter when its chosen style found nothing; do the same within the kit.
			styleWarnings.add(widenedWarning(encounter, styles));
			searched = computeSearch(data, summary, levels, remembered, prayerUnlocks, diary, false, mining,
				quotes, encounterOwned, quantities, generation, assumptions, liveRaid, notes,
				encounter.getRequiredStyles(), excluded, plan.getSpellbook(), encounter.getLoadoutRequirement(), publishProgress,
				null, switchFrom);
			if (searched == null) { return true; }
			if (searched.hasBlockingLock() && !liveRaid.adaptive()) { return false; }
			if (encounter.isPrepotted()) { searched = searched.withPrepotted(); }
			searched = compatibleSwitches(plan, liveRaid.adaptive() ? searched : searched.withRegeneratedChoices(), index, switchGroup, fullSetup);
			best = liveRaid.adaptive() ? bestCarried(searched, liveRaid.spellSupplies, preference)
				: bestInKit(plan, searched, owned::contains, fullBar, preference);
		}
		if (best == null && !liveRaid.adaptive()) { return false; }
		SearchResults found = best == null ? searched.withCandidates(Collections.emptyMap()).withWarnings(styleWarnings)
			: searched.withWarnings(styleWarnings).withSetup(best);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null && generation == searchGeneration.get()
				&& (!liveRaid.adaptive() || raidPlan == plan && raidProgress.isActive() && !raidProgress.isFinished()
					&& liveRaid.inputs != null && liveRaid.inputs.equals(desiredRaidInputs)
					&& encounter.getLabel().equals(raidProgress.current().getEncounter())))
			{
				if (!liveRaid.adaptive()) { panel.showProgress(100); }
				if (liveRaid.adaptive())
				{
					panel.showLiveRaidResults(plan, encounter, found);
					publishedRaidInputs = liveRaid.inputs;
				}
				else { panel.showRaidResults(plan, summary, found); }
			}
		});
		return true;
	}

	/** Live choices use only carried gear and the original kit's spell supplies, without spending future rooms' energy. */
	private static SetupResult bestCarried(SearchResults found, Set<Integer> supplies,
		java.util.Comparator<SetupResult> preference)
	{
		if (found.hasBlockingLock()) { return null; }
		return found.getByType().values().stream().flatMap(Collection::stream)
			.filter(setup -> supplies.containsAll(SpellRunes.required(setup)))
			.max(preference).orElse(null);
	}

	/**
	 * Live recalculations must remain compatible with the other forms' selected switch pattern and main setup: the
	 * fight's switch {@code group} must still choose the plan's full setup {@code base}. Unchanged when the fight has
	 * no group or the plan no full-setup choice (null).
	 */
	private static SearchResults compatibleSwitches(RaidPlan plan, SearchResults found, int index, int[] group, int base)
	{
		if (group == null) { return found; }
		Map<AttackStyle.Type, List<SetupResult>> candidates = new EnumMap<>(AttackStyle.Type.class);
		found.getByType().forEach((type, setups) ->
		{
			List<SetupResult> kept = setups.stream().filter(setup ->
			{
				List<SetupResult> trial = new ArrayList<>(plan.getSetups());
				trial.set(index, plan.fillAmmoFor(setup, found));
				return RaidEncounter.fullSetups(group, trial, plan.getEncounters()).contains(base);
			}).collect(Collectors.toList());
			if (!kept.isEmpty()) { candidates.put(type, kept); }
		});
		return found.withCandidates(candidates);
	}

	/** Every item the kit doesn't carry, so a search can only use the kit's equipment and loaded ammunition. */
	private static Set<Integer> outsideKit(RaidPlan plan, GameData data)
	{
		Set<Integer> allowed = new HashSet<>();
		plan.getEquipment().forEach(item -> allowed.add(item.getId()));
		for (SetupResult setup : plan.getSetups())
		{
			if (setup.getLoadout().getLoadedAmmo() != null) { allowed.add(setup.getLoadout().getLoadedAmmo().getId()); }
			setup.getSpecials().stream().filter(special -> special.getLoadedAmmo() != null)
				.forEach(special -> allowed.add(special.getLoadedAmmo().getId()));
		}
		Set<Integer> excluded = new HashSet<>();
		for (Slot slot : Slot.values())
		{
			for (GearItem item : data.getItems(slot))
			{
				// The kit never carries raid-provided items (the Dawnbringer, picked up at Verzik P1 for its special),
				// so they aren't excluded for being missing from it.
				if (!RaidPlan.providedInRaid(item) && !allowed.contains(item.getId())) { excluded.add(item.getId()); }
			}
		}
		return excluded;
	}

	/** Setups by the damage they deal to the fought target. */
	private static final java.util.Comparator<SetupResult> PRIMARY_DPS = java.util.Comparator.comparingDouble(
		setup -> setup.getDps().getPrimaryDps());

	/**
	 * The {@code preference}'s best of a search's setups that the selected kit's ammunition and supplies support,
	 * opening on a full spec bar or not as asked, or null.
	 */
	private static SetupResult bestInKit(RaidPlan plan, SearchResults found, IntPredicate owned, boolean fullBar,
		java.util.Comparator<SetupResult> preference)
	{
		RaidEncounter refreshed = plan.encounterFor(found.getMonster().getName(), found.getMonster().getPhase());
		double openingLimit = refreshed == null || !plan.getSetups().get(plan.getEncounters().indexOf(refreshed)).usesFullBar()
			? 0 : plan.getSetups().get(plan.getEncounters().indexOf(refreshed)).getSpecial().getOpeningEnergy();
		Set<Integer> allowedSpecs = plan.getSetups().stream().flatMap(setup -> setup.getSpecials().stream())
			.map(special -> special.getWeapon().getId()).collect(Collectors.toSet());
		return found.getByType().values().stream().flatMap(Collection::stream)
			.filter(setup -> setup.usesFullBar() == fullBar)
			.filter(setup -> !fullBar || setup.getSpecial().getOpeningEnergy() <= openingLimit + 1e-9)
			.filter(setup -> setup.getSpecials().stream().allMatch(special -> RaidPlan.providedInRaid(special.getWeapon())
				|| allowedSpecs.contains(special.getWeapon().getId())))
			.map(setup -> plan.fillAmmoFor(setup, found))
			.filter(setup -> plan.supportsSupplies(setup, found, owned))
			.max(preference).orElse(null);
	}

	/**
	 * Put the display-only encounters back in raid order, each with its fastest setup from the finished kit. One the
	 * kit can't fight is left out with a warning. Returns null when cancelled.
	 */
	private static RaidPlan withDisplayOnly(RaidPlan kit, GameData data, List<RaidEncounter> encounters,
		RaidConsolidator.Search search, IntPredicate owns) throws IOException
	{
		if (kit.getEncounters().size() == encounters.size()) { return kit; }
		Set<Integer> excluded = outsideKit(kit, data);
		List<RaidEncounter> shown = new ArrayList<>();
		List<SearchResults> results = new ArrayList<>();
		List<SetupResult> setups = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		int planned = 0;
		for (int index = 0; index < encounters.size(); index++)
		{
			RaidEncounter encounter = encounters.get(index);
			if (!encounter.isDisplayOnly())
			{
				shown.add(encounter);
				results.add(kit.getResults().get(planned));
				setups.add(kit.getSetups().get(planned++));
				continue;
			}
			SearchResults found = search.run(index, excluded, 0);
			if (found == null) { return null; }
			// Display-only encounters spend none of the raid's full spec bars.
			SetupResult best = found.hasBlockingLock() ? null : bestInKit(kit, found, owns, false, PRIMARY_DPS);
			if (best == null) { missing.add(encounter.getLabel()); continue; }
			shown.add(encounter);
			results.add(found.withNotes(Collections.singletonList(encounter.getLabel()
				+ " doesn't shape the raid kit: this is the fastest setup from the kit's gear.")));
			setups.add(best);
		}
		RaidPlan plan = kit.withEncounters(Collections.unmodifiableList(shown), Collections.unmodifiableList(results),
			Collections.unmodifiableList(setups));
		return missing.isEmpty() ? plan : plan.withRaidNotes(Collections.emptyList(), Collections.singletonList(
			"No setup from the raid kit can fight " + String.join(" or ", missing) + ", so "
				+ (missing.size() == 1 ? "it isn't" : "they aren't") + " shown."));
	}

	/** Why an encounter was searched with every style it allows: the styles actually tried found no setup. */
	static String widenedWarning(RaidEncounter encounter, Set<AttackStyle.Type> tried)
	{
		List<String> classes = new ArrayList<>();
		if (tried.contains(AttackStyle.Type.STAB) || tried.contains(AttackStyle.Type.SLASH) || tried.contains(AttackStyle.Type.CRUSH))
		{ classes.add("melee"); }
		if (tried.contains(AttackStyle.Type.RANGED) || tried.contains(AttackStyle.Type.ATLATL)) { classes.add("ranged"); }
		if (tried.contains(AttackStyle.Type.MAGIC)) { classes.add("magic"); }
		return encounter.getLabel() + ": no usable " + String.join(" or ", classes)
			+ " setup, so every attack style it allows was searched.";
	}

	/**
	 * The attack types an encounter is searched with. A saved choice whose types are all turned off under Attack
	 * styles falls back to Auto, with a warning; when Auto's default (e.g. ranged or magic for Zebak) is turned
	 * off too, the encounter uses every type the raid allows. A choice never leaves an encounter unsearchable.
	 */
	private Set<AttackStyle.Type> raidStyles(RaidEncounter encounter, BestGearSetupConfig config, List<String> warnings)
	{
		Set<AttackStyle.Type> enabled = buildSettings(config).getStyles();
		Set<AttackStyle.Type> styles = encounter.getStyles();
		if (!styles.isEmpty() && Collections.disjoint(styles, enabled) && encounter.getStyleChoice() != RaidStyle.AUTO
			&& !encounter.isStyleFallback())
		{
			warnings.add(encounter.getLabel() + ": the chosen " + encounter.getStyleChoice() + " attack style is turned off "
				+ "under Attack styles, so Auto was used.");
			styles = encounter.getAutoStyles();
		}
		return !styles.isEmpty() && Collections.disjoint(styles, enabled) ? encounter.getRequiredStyles() : styles;
	}

	/**
	 * Compare complete feasible kits on each allowed spellbook, and for ToA's Auto path order in each path order,
	 * before publishing one raid plan.
	 */
	private void runRaidSearch(GameData data, MonsterSummary active, PlayerLevels levels, boolean remembered,
		Set<String> prayerUnlocks, Boolean diary, int mining, Map<Integer, Long> quotes, Set<Integer> owned,
		Map<Integer, Long> quantities, int generation, BestGearSetupConfig config, LiveRaid liveRaid,
		List<String> detectedNotes, RaidPlanType type, DoubleConsumer publishProgress) throws IOException
	{
		if (generation != searchGeneration.get()) { return; }
		long epoch = raidCache.epoch();
		SearchInputs inputs = SearchInputs.capture(data, api, active, levels, remembered, prayerUnlocks, diary,
			false, mining, quotes, owned, quantities, config, liveRaid, detectedNotes, false).with(type);
		RaidPlan reused = raidCache.get(inputs, epoch);
		if (reused != null)
		{
			publishProgress.accept(100);
			publishRaidPlan(reused, active, generation);
			return;
		}
		List<RaidSpellbook> books = RaidSpellbook.candidates(type, config);
		List<RaidOptions.ToaPathOrder> orders = type == RaidPlanType.TOA ? config.toaPathOrder().candidates()
			: Collections.singletonList(config.toaPathOrder());
		SearchProgress progress = new SearchProgress(orders.size() * books.size(), publishProgress);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null && generation == searchGeneration.get()) { panel.showEncounterProgress("Optimizing raid setups..."); }
		});
		RaidPlan selected = null;
		RaidOptions.ToaPathOrder selectedOrder = null;
		List<String> unavailable = new ArrayList<>();
		// Each order's spellbook times, and its fastest plan for the path order note.
		Map<RaidOptions.ToaPathOrder, List<String>> compared = new EnumMap<>(RaidOptions.ToaPathOrder.class);
		Map<RaidOptions.ToaPathOrder, RaidPlan> fastestByOrder = new EnumMap<>(RaidOptions.ToaPathOrder.class);
		// Optimizer results by encounter, shared between the spellbooks and path orders compared here.
		Map<List<Object>, Map<List<Object>, List<SetupResult>>> sharedTabs = new java.util.concurrent.ConcurrentHashMap<>();
		// Per order, the fastest plan a book stopped at without beating it (RaidKitSlower), in estimated seconds.
		Map<RaidOptions.ToaPathOrder, Double> noFasterThan = new EnumMap<>(RaidOptions.ToaPathOrder.class);
		for (int orderIndex = 0; orderIndex < orders.size(); orderIndex++)
		{
			RaidOptions.ToaPathOrder order = orders.get(orderIndex);
			BestGearSetupConfig orderConfig = orders.size() == 1 ? config
				: SearchAssumptions.capture(config, Collections.singletonMap("toaPathOrder", order));
			String prefix = orders.size() == 1 ? "" : order + ", ";
			for (int index = 0; index < books.size(); index++)
			{
				if (generation != searchGeneration.get()) { return; }
				int step = orderIndex * books.size() + index;
				RaidSpellbook book = books.get(index);
				// Later books and orders only need a faster kit than the best so far, so it bounds their consolidation.
				double best = selected == null ? Double.POSITIVE_INFINITY : RaidSpellbook.seconds(selected);
				// Consolidation counts every player's combat; the estimate is per party member.
				double beat = selected == null ? Double.POSITIVE_INFINITY : best * selected.getPartySize();
				try
				{
					RaidPlan candidate = planRaidSpellbook(data, active, levels, remembered, prayerUnlocks, diary, mining,
						quotes, owned, quantities, generation, orderConfig, liveRaid, detectedNotes, type, book,
						percent -> progress.update(step, percent / 100.0), sharedTabs, beat);
					if (candidate == null) { return; }
					compared.computeIfAbsent(order, o -> new ArrayList<>())
						.add(book + ": " + Math.round(RaidSpellbook.seconds(candidate)) + "s estimated combat");
					if (RaidSpellbook.faster(candidate, fastestByOrder.get(order))) { fastestByOrder.put(order, candidate); }
					if (RaidSpellbook.faster(candidate, selected)) { selected = candidate; selectedOrder = order; }
				}
				catch (RaidKitUnavailable e) { unavailable.add(prefix + book + ": " + e.getMessage()); }
				catch (RaidKitSlower e)
				{
					compared.computeIfAbsent(order, o -> new ArrayList<>()).add(book + ": no kit under " + Math.round(best) + "s");
					noFasterThan.merge(order, best, Math::min);
				}
				progress.update(step, 1);
			}
		}
		if (generation != searchGeneration.get()) { return; }
		if (selected == null) { throw new IllegalStateException(String.join(" ", unavailable)); }
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null && generation == searchGeneration.get()) { panel.showEncounterProgress("Finalizing raid kit..."); }
		});
		List<String> bookNotes = new ArrayList<>();
		if (orders.size() > 1)
		{
			List<String> orderTimes = new ArrayList<>();
			for (RaidOptions.ToaPathOrder order : orders)
			{
				// An order whose books stopped at a faster plan is only known to be no faster than it.
				RaidPlan plan = fastestByOrder.get(order);
				Double bound = noFasterThan.get(order);
				if (plan != null && (bound == null || RaidSpellbook.seconds(plan) <= bound))
				{ orderTimes.add(order + ": " + Math.round(RaidSpellbook.seconds(plan)) + "s"); }
				else if (bound != null) { orderTimes.add(order + ": no kit under " + Math.round(bound) + "s"); }
			}
			bookNotes.add("Auto path order selected " + selectedOrder + " by estimated raid combat time. "
				+ String.join("; ", orderTimes) + ".");
		}
		if (type == RaidPlanType.TOB && config.tobRole().freezes())
		{ bookNotes.add("Ancient locked for the ToB freeze role: Ice Barrage is required."); }
		else if (config.raidSpellbook() == RaidSpellbook.AUTO)
		{
			bookNotes.add("Auto selected " + selected.getSpellbook() + " by estimated raid combat time. "
				+ String.join("; ", compared.get(selectedOrder)) + ".");
			if (!unavailable.isEmpty()) { bookNotes.add("Spellbooks without a feasible complete kit: " + String.join("; ", unavailable)); }
		}
		// Only where an encounter's own search modelled the teammates' openers.
		if (selected.getResults().stream().anyMatch(result -> result.getNotes().stream().anyMatch(note -> note.startsWith(TEAM_OPENERS_NOTE))))
		{ bookNotes.add(TEAM_OPENERS_NOTE); }
		selected.getResults().stream().flatMap(result -> result.getNotes().stream())
			.filter(note -> note.startsWith("Sotetseg fair share:")).findFirst().ifPresent(bookNotes::add);
		RaidPlan plan = selected.withRaidNotes(bookNotes, Collections.emptyList());
		progress.complete();
		if (generation == searchGeneration.get()) { raidCache.put(inputs, plan, epoch); }
		publishRaidPlan(plan, active, generation);
	}

	private void publishRaidPlan(RaidPlan plan, MonsterSummary active, int generation)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null && generation == searchGeneration.get())
			{
				raidPlan = plan; panel.showProgress(100); panel.showRaidResults(plan, active);
				RaidProgress.Update progress = raidProgress.current();
				if (progress.isActive() && progress.getType() == plan.getType())
				{ panel.showRaidProgress(progress.getType(), progress.getEncounter(), progress.getStatus()); }
			}
		});
	}

	private void clearSearchCaches()
	{
		raidCache.clear();
		encounterCache.clear();
		raidEncounterCache.clear();
		if (configManager != null && gson != null && clientThread != null)
		{
			raidCache.profile(ProfileSearchCache.raids(configManager, gson, clientThread::invokeLater));
			encounterCache.profile(ProfileSearchCache.encounters(configManager, gson, clientThread::invokeLater));
		}
	}

	/**
	 * One raid encounter's search, optionally as a quick switch from a worn setup, without its class's boost, or
	 * wearing items the kit planner locked in.
	 */
	@FunctionalInterface
	private interface SwitchSearch
	{
		SearchResults run(int index, Set<Integer> excluded, int pass, Loadout switchFrom, boolean unboosted,
			Map<Slot, Integer> worn) throws IOException;
	}

	/** How many of another switch group member's fastest full setups each member quick-switches from. */
	private static final int SWITCH_BASES = 3;

	/**
	 * A switch group member's candidates: its own full setups when it is an allowed base, plus quick switches from
	 * the fastest few full setups of the other allowed bases in {@code group}, both with and (when {@code boostable})
	 * without its style's boost. Each candidate keeps its boosts; the kit planner keeps a boosted quick switch only
	 * when that boost is brought for a full setup anyway. A freeze ({@code freeze}) only counts from the gear it was
	 * searched from, so it also switches from the fastest full setup of each attack type: a mage Maiden setup may need
	 * no freeze switch at all. Null when cancelled.
	 */
	private static SearchResults switchCandidates(int index, int[] group, Set<Integer> excluded, int pass, SwitchSearch search,
		Map<List<Object>, SearchResults> fullSearches, boolean boostable, boolean freeze) throws IOException
	{
		SearchResults own = fullSearch(index, excluded, pass, search, fullSearches);
		if (own == null) { return null; }
		Map<AttackStyle.Type, List<SetupResult>> candidates = new LinkedHashMap<>();
		Map<DpsResult, List<Potion>> boosts = new IdentityHashMap<>();
		if (Arrays.stream(group).anyMatch(base -> base == index)) { addCandidates(candidates, boosts, own); }
		for (int other : group)
		{
			if (other == index) { continue; }
			SearchResults worn = fullSearch(other, excluded, pass, search, fullSearches);
			if (worn == null) { return null; }
			if (worn.hasBlockingLock()) { continue; }
			java.util.Comparator<SetupResult> fastest = java.util.Comparator.comparingDouble(
				(SetupResult setup) -> -setup.getDps().getPrimaryDps());
			List<Loadout> bases = worn.getByType().values().stream().flatMap(List::stream).sorted(fastest)
				.map(SetupResult::getLoadout).distinct().limit(SWITCH_BASES).collect(Collectors.toCollection(ArrayList::new));
			if (freeze)
			{
				for (List<SetupResult> type : worn.getByType().values())
				{
					type.stream().filter(setup -> setup.getSwitchFrom() == null).min(fastest).map(SetupResult::getLoadout)
						.filter(base -> !bases.contains(base)).ifPresent(bases::add);
				}
			}
			for (Loadout base : bases)
			{
				for (boolean unboosted : boostable ? new boolean[]{false, true} : new boolean[]{false})
				{
					SearchResults quick = search.run(index, excluded, pass, base, unboosted, Collections.emptyMap());
					if (quick == null) { return null; }
					addCandidates(candidates, boosts, quick);
				}
			}
		}
		candidates.values().forEach(list -> list.sort(java.util.Comparator.comparingDouble(
			(SetupResult setup) -> -setup.getDps().getPrimaryDps())));
		return own.withCandidates(candidates, boosts);
	}

	private static SearchResults fullSearch(int index, Set<Integer> excluded, int pass, SwitchSearch search,
		Map<List<Object>, SearchResults> fullSearches) throws IOException
	{
		List<Object> key = Arrays.asList(index, excluded);
		SearchResults found = fullSearches.get(key);
		if (found == null)
		{
			found = search.run(index, excluded, pass, null, false, Collections.emptyMap());
			if (found != null) { fullSearches.put(key, found); }
		}
		return found;
	}

	/**
	 * Add a search's setups with their boosts, skipping one already listed with the same equipment and boosts. Boosts
	 * are keyed by the setup's DPS result instance, so a setup instance another search already returned (with the same
	 * levels, hence the same DPS) is listed once, keeping the fewer boosts.
	 */
	private static void addCandidates(Map<AttackStyle.Type, List<SetupResult>> candidates, Map<DpsResult, List<Potion>> boosts,
		SearchResults found)
	{
		found.getByType().forEach((type, setups) -> setups.forEach(setup ->
		{
			List<Potion> potions = found.potionsForSetup(setup);
			List<Potion> listed = boosts.get(setup.getDps());
			if (listed != null)
			{
				if (potions.size() < listed.size()) { boosts.put(setup.getDps(), potions); }
				return;
			}
			List<SetupResult> list = candidates.computeIfAbsent(type, t -> new ArrayList<>());
			if (list.stream().noneMatch(other -> other.getLoadout().equals(setup.getLoadout())
				&& potionIds(boosts.get(other.getDps())).equals(potionIds(potions))))
			{
				list.add(setup);
				boosts.put(setup.getDps(), potions);
			}
		}));
	}

	private static Set<Integer> potionIds(List<Potion> potions)
	{
		return potions.stream().map(Potion::getId).collect(Collectors.toSet());
	}

	/**
	 * Whether a raid search of this target assumes every teammate opens with a reducer ({@link #TEAM_OPENERS_NOTE}):
	 * only while planning (a live refresh uses detected drains), in a party, with drains credited and none set by hand.
	 */
	static boolean teamOpeners(BestGearSetupConfig config, Monster monster, SpecialAttacks specials, boolean planned)
	{
		return planned && config.killSpecials() && config.drainSpecs() != DrainSpecs.DAMAGE_ONLY && config.raidKillShare() > 1
			&& !specials.isAny() && SpecialRotation.teamReducerTarget(monster.getLowerName());
	}

	/** Whether a class's boost is the Best available choice, which a quick switch may go without. */
	private static boolean boostable(BestGearSetupConfig config, CombatClass cls)
	{
		return cls != null && PotionChoice.BEST.equalsIgnoreCase(potionChoice(config, cls).trim());
	}

	/** Search without the Best available boost for a class; a potion chosen by name is the player's own choice. */
	private static void unboost(Map<String, Object> overrides, BestGearSetupConfig config, CombatClass cls)
	{
		if (!boostable(config, cls)) { return; }
		overrides.put(cls == CombatClass.MELEE ? "potionMelee" : cls == CombatClass.RANGED ? "potionRanged" : "potionMagic",
			PotionChoice.NONE);
	}

	/** Whether a freeze switch guarantees the freeze, and with how many items. */
	static String freezeNote(RaidEncounter freeze, RaidEncounter worn, SetupResult setup)
	{
		int items = FreezeDuty.items(setup);
		String switched = items + (items == 1 ? " item" : " items");
		if (items == 0 && FreezeDuty.reaches(setup, FreezeDuty.GUARANTEED) && !FreezeDuty.manualCast(setup))
		{
			return freeze.getLabel() + " needs no gear switch: your " + worn.getLabel() + " setup's weapon autocasts Ice Barrage"
				+ " with a magic attack roll of at least (base Magic + 9) x 204, so freezes never splash.";
		}
		String casting = FreezeDuty.manualCast(setup) ? " Ice Barrage is cast by hand: no weapon you can use that autocasts it does as well."
			: " The weapon autocasts Ice Barrage.";
		return (FreezeDuty.reaches(setup, FreezeDuty.GUARANTEED)
			? freeze.getLabel() + " never splashes: the fewest items from your " + worn.getLabel() + " setup that reach a magic"
				+ " attack roll of (base Magic + 9) x 204, " + switched + "."
			: "No switch from your " + worn.getLabel() + " setup reaches a magic attack roll of (base Magic + 9) x 204, so"
				+ " freezes can splash: the most accurate, " + switched + ", lands " + Math.round(setup.getDps().getAccuracy() * 1000)
				/ 10.0 + "%. More magic attack, Augury or a heart raises the roll.") + casting;
	}

	/** Keep the prayer and Magic boost behind a freeze's accuracy visible beside its gear. */
	static String freezeAssumptions(SearchResults result, SetupResult setup)
	{
		List<String> assumptions = new ArrayList<>();
		OffensivePrayer prayer = result.getPrayers().get(CombatClass.MAGIC);
		assumptions.add(prayer == null ? "no offensive Magic prayer" : GameData.titleCase(prayer.getName()));
		List<String> boosts = result.potionsForSetup(setup).stream().filter(potion -> "magic".equalsIgnoreCase(potion.getSkill()))
			.map(potion -> GameData.titleCase(potion.getName())).distinct().collect(Collectors.toList());
		assumptions.add(boosts.isEmpty() ? "unboosted Magic" : listed(boosts));
		return "Freeze accuracy assumes " + listed(assumptions) + ".";
	}

	/**
	 * A South freezer hearts before Maiden: the freeze assumes the best heart the search may use (a saturated heart,
	 * else an imbued heart), whatever the Magic boost choice. Without one the choice stands.
	 */
	private void freezeHeart(Map<String, Object> overrides, RaidEncounter encounter, BestGearSetupConfig config,
		GameData data, Set<Integer> owned)
	{
		if (!encounter.isFreeze() || config.tobRole() != RaidOptions.TobRole.SOUTH_FREEZE) { return; }
		OptimizerSettings settings = buildSettings(config);
		String heart = southFreezeHeart(data, settings.getMode(), owned, settings.getExcluded());
		if (heart != null) { overrides.put("potionMagic", heart); }
	}

	/** The heart a South freezer uses, or null when the search may use neither. */
	static String southFreezeHeart(GameData data, SearchMode mode, Set<Integer> owned, Set<Integer> excluded)
	{
		Predicate<Potion> usable = BoostAccess.allowed(mode, owned, false).and(potion -> !excluded.contains(potion.getId()));
		for (String name : new String[]{"saturated heart", "imbued heart"})
		{
			for (Potion potion : data.getPotions("magic"))
			{
				if (potion.getName().equalsIgnoreCase(name) && usable.test(potion)) { return potion.getName(); }
			}
		}
		return null;
	}

	/** Search an encounter not worth a potion: no Best available boost for any class, and no raid supplies. */
	private static void unboostAll(Map<String, Object> overrides, BestGearSetupConfig config)
	{
		for (CombatClass cls : CombatClass.values()) { unboost(overrides, config, cls); }
		overrides.put("raidPotions", false);
	}

	/**
	 * Notes on a finished kit: which switch group member wears the full setup and which are quick switches (and
	 * whether they go without a boost), and that the first room's boosts are drunk before entering.
	 */
	static RaidPlan withKitNotes(RaidPlan plan, BestGearSetupConfig config)
	{
		List<RaidEncounter> encounters = plan.getEncounters();
		List<SearchResults> results = new ArrayList<>(plan.getResults());
		List<int[]> groups = RaidEncounter.switchGroups(encounters);
		int[] full = RaidPlanner.fullSetupChoice(encounters, results, plan.getSetups());
		for (int group = 0; full != null && group < groups.size(); group++)
		{
			int worn = full[group];
			List<String> quick = new ArrayList<>();
			Set<Slot> extras = EnumSet.noneOf(Slot.class);
			for (int member : groups.get(group))
			{
				if (member != worn)
				{ extras.addAll(plan.getSetups().get(member).getLoadout().switchSlotsFrom(plan.getSetups().get(worn).getLoadout())); }
			}
			extras.remove(Slot.WEAPON);
			List<String> slots = extras.stream().map(slot -> slot.getDisplayName().toLowerCase(Locale.ROOT)).collect(Collectors.toList());
			String pattern = extras.isEmpty() ? "Quick switches use the weapon only."
				: "Quick switches share the weapon and " + listed(slots) + (slots.size() == 1 ? " slot." : " slots.");
			int others = encounters.get(worn).getSwitchLimit() - 1;
			String allowed = others <= 0 ? "the weapon only."
				: "the weapon and at most " + (others == 1 ? "one other piece." : others + " other pieces.");
			for (int member : groups.get(group))
			{
				if (member == worn) { continue; }
				RaidEncounter encounter = encounters.get(member);
				quick.add(encounter.getLabel());
				List<String> notes = new ArrayList<>();
				if (encounter.isFreeze())
				{
					notes.add(freezeNote(encounter, encounters.get(worn), plan.getSetups().get(member)));
					notes.add(freezeAssumptions(results.get(member), plan.getSetups().get(member)));
				}
				else
				{
					notes.add(encounter.getLabel() + " is a quick switch from your " + encounters.get(worn).getLabel()
						+ " setup: " + allowed);
					notes.add(pattern);
				}
				CombatClass cls = encounter.getFixedClass();
				if (boostable(config, cls) && results.get(member).potionsForSetup(plan.getSetups().get(member)).isEmpty())
				{
					notes.add("Planned without a " + cls.name().toLowerCase(Locale.ROOT) + " boost: the kit only brings one"
						+ " for a full setup after the first room.");
				}
				results.set(member, results.get(member).withNotes(notes));
			}
			results.set(worn, results.get(worn).withNotes(Collections.singletonList(encounters.get(worn).getLabel()
				+ " wears your full setup; " + listed(quick) + (quick.size() == 1 ? " is a quick switch" : " are quick switches") + " from it.")));
		}
		for (int index = 0; index < encounters.size(); index++)
		{
			if (encounters.get(index).isPrepotted() && !results.get(index).potionsForSetup(plan.getSetups().get(index)).isEmpty())
			{
				results.set(index, results.get(index).withNotes(Collections.singletonList("Boosts for the first room are drunk"
					+ " before entering; the kit only carries those a later room needs.")));
			}
			if (encounters.get(index).isUnboosted())
			{
				results.set(index, results.get(index).withNotes(Collections.singletonList("Fought without boosts: not worth"
					+ " a potion.")));
			}
		}
		return plan.withEncounters(encounters, Collections.unmodifiableList(results), plan.getSetups());
	}

	/** Names joined as a list: "gloves, boots and ring". */
	private static String listed(List<String> names)
	{
		return names.size() == 1 ? names.get(0)
			: String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
	}

	/** A book may have no feasible kit while another book still works; data/loading errors are not swallowed. */
	private static final class RaidKitUnavailable extends IllegalStateException
	{
		RaidKitUnavailable(String message) { super(message); }
	}

	/** A book or path order stopped at the faster plan already found: its own best is only known to be no faster. */
	private static final class RaidKitSlower extends IllegalStateException
	{
		RaidKitSlower() { super("No faster kit"); }
	}

	/**
	 * Search every target on one book, then consolidate switches when independent setups exceed the limit.
	 * Consolidation only looks for kits faster than {@code beat} combat seconds, the whole party's (infinity for
	 * none), and throws {@link RaidKitSlower} when that cut it short without one.
	 */
	private RaidPlan planRaidSpellbook(GameData data, MonsterSummary active, PlayerLevels levels, boolean remembered,
		Set<String> prayerUnlocks, Boolean diary, int mining, Map<Integer, Long> quotes, Set<Integer> owned,
		Map<Integer, Long> quantities, int generation, BestGearSetupConfig config, LiveRaid liveRaid,
		List<String> detectedNotes, RaidPlanType type, RaidSpellbook spellbook, DoubleConsumer publishProgress,
		Map<List<Object>, Map<List<Object>, List<SetupResult>>> sharedTabs, double beat) throws IOException
	{
		List<RaidEncounter> encounters = type.encounters(data, raidSettings(levels == null ? PlayerLevels.maxed() : levels, mining, config),
			RaidOptions.of(config));
		RaidSearchProgress progress = new RaidSearchProgress((int) encounters.stream().filter(encounter -> !encounter.isDisplayOnly()).count(), publishProgress);
		BooleanSupplier cancelled = () -> generation != searchGeneration.get();
		// Encounters whose chosen style found no setup in the first pass; later passes keep the wider search.
		String[] widened = new String[encounters.size()];
		SwitchSearch searchFrom = (index, excluded, pass, switchFrom, unboosted, worn) ->
		{
			RaidEncounter encounter = encounters.get(index);
			if (cancelled.getAsBoolean()) { return null; }
			Map<String, Object> overrides = new HashMap<>();
			overrides.put("coxChallengeMode", type == RaidPlanType.COX_CM);
			overrides.put("encounterPhase", encounter.getPhase());
			overrides.put("onSlayerTask", false);
			overrides.put("wilderness", false);
			overrides.put("aoe", false);
			// Each setup is planned opening on a full spec bar, with its regenerated-energy alternative; the kit
			// planner follows the raid's energy (RaidEncounter#energyAfter) instead of the Spec energy setting.
			overrides.put("specEnergy", SpecEnergy.FULL_BAR);
			// Specs are planned over one player's share of each kill, with the kill's final blows.
			overrides.put("raidKillShare", encounter.getKillShare());
			overrides.put("raidFinalBlowPercent", (int) Math.round(encounter.getFinalBlow() * 100));
			// CoX overloads come after Tekton and ToA smelling salts after the first two paths; ToB hands out none.
			if (!encounter.isRaidSupplies()) { overrides.put("raidPotions", false); }
			if (unboosted) { unboost(overrides, config, encounter.getFixedClass()); }
			if (encounter.isUnboosted()) { unboostAll(overrides, config); }
			freezeHeart(overrides, encounter, config, data, owned);
			boolean same = encounter.getTarget().getId() == active.getId();
			boolean freshHand = encounter.getTarget().getName().startsWith("great olm")
				&& encounter.getTarget().getName().contains("hand");
			if (!same || freshHand)
			{
				for (String key : new String[]{"specElderMaul", "specDwh", "specEmberlight", "specArclight", "specTonalztics",
					"specBgsDamage", "specSeercullDamage", "specAyakDamage"}) { overrides.put(key, 0); }
				overrides.put("specVulnerability", false);
				overrides.put("specTomeOfWater", false);
			}
			// The Dawnbringer is passed around at Verzik P1: the optimizer offers it to everyone there as a spec-only
			// weapon, and the kit never carries it (RaidPlan.providedInRaid).
			List<String> styleWarnings = new ArrayList<>();
			Set<AttackStyle.Type> chosen = raidStyles(encounter, config, styleWarnings);
			Set<AttackStyle.Type> styles = widened[index] != null ? encounter.getRequiredStyles() : chosen;
			if (!worn.isEmpty())
			{
				// Items the kit planner chose to wear here instead of carrying, on top of the player's own locks.
				Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
				locks.putAll(SlotLock.parse(config.locks()));
				worn.forEach((slot, id) -> locks.putIfAbsent(slot, SlotLock.item(id)));
				overrides.put("locks", SlotLock.format(locks));
			}
			BestGearSetupConfig assumptions = SearchAssumptions.capture(config, overrides);
			List<String> notes = same ? detectedNotes : Collections.emptyList();
			// Keyed by position and label: a ToA path order moves the path fights, whose scaling and smelling salts
			// depend on their position, while the Obelisk and Wardens stay put and are shared between orders.
			// Consolidation searches trials on helper threads too, so these maps are shared between threads.
			Map<List<Object>, List<SetupResult>> encounterTabs = sharedTabs.computeIfAbsent(
				Arrays.asList(index, encounter.getLabel()), key -> new java.util.concurrent.ConcurrentHashMap<>());
			DoubleConsumer taskProgress = progress.search();
			SearchResults result = computeSearch(data, encounter.getTarget(), levels, remembered, prayerUnlocks, diary,
				false, mining, quotes, owned, quantities, generation, assumptions,
				liveRaid, notes, styles, excluded, spellbook, encounter.getLoadoutRequirement(), percent -> taskProgress.accept(percent * 0.5), encounterTabs,
				switchFrom);
			if (result != null && result.isEmpty() && pass == 0 && !styles.equals(encounter.getRequiredStyles()))
			{
				// A chosen (or default distance) style without a usable setup, e.g. no ranged gear owned, must not
				// fail the whole raid: search every style the raid allows here and say so.
				widened[index] = widenedWarning(encounter, styles);
				result = computeSearch(data, encounter.getTarget(), levels, remembered, prayerUnlocks, diary,
					false, mining, quotes, owned, quantities, generation, assumptions,
					liveRaid, notes, encounter.getRequiredStyles(), excluded, spellbook, encounter.getLoadoutRequirement(), percent -> taskProgress.accept(50 + percent * 0.5),
					encounterTabs, switchFrom);
			}
			if (widened[index] != null) { styleWarnings.add(widened[index]); }
			if (result == null) { return null; }
			taskProgress.accept(100);
			result = result.withWarnings(styleWarnings);
			if (!worn.isEmpty() && !result.hasBlockingLock()) { result = result.wearing(worn.keySet()); }
			// The kit planner chooses where to spend the raid's full spec bars, so each setup is also offered without one.
			result = result.withRegeneratedChoices();
			return encounter.isPrepotted() ? result.withPrepotted() : result;
		};
		int[][] groupOf = new int[encounters.size()][];
		RaidEncounter.switchGroups(encounters).forEach(group ->
		{
			int[] bases = Arrays.stream(group).filter(index -> encounters.get(index).isFullSwitchSetup()).toArray();
			Arrays.stream(group).forEach(member -> groupOf[member] = bases);
		});
		// Each switch group member's full-setup search, shared by the other members' quick switches.
		Map<List<Object>, SearchResults> fullSearches = new java.util.concurrent.ConcurrentHashMap<>();
		RaidConsolidator.Search search = new RaidConsolidator.Search()
		{
			@Override
			public SearchResults run(int index, Set<Integer> excluded, int pass) throws IOException
			{
				int bases = SWITCH_BASES + (encounters.get(index).isFreeze() ? AttackStyle.Type.values().length : 0);
				int calls = groupOf[index] == null ? 1 : 1 + (int) Arrays.stream(groupOf[index]).filter(other -> other != index).count()
					* (1 + bases * (boostable(config, encounters.get(index).getFixedClass()) ? 2 : 1));
				progress.expectSearches(calls);
				SearchResults found = groupOf[index] == null ? searchFrom.run(index, excluded, pass, null, false, Collections.emptyMap())
					: switchCandidates(index, groupOf[index], excluded, pass, searchFrom, fullSearches,
						boostable(config, encounters.get(index).getFixedClass()), encounters.get(index).isFreeze());
				return found != null && encounters.get(index).isFreeze() ? found.forFreeze(FreezeDuty.GUARANTEED) : found;
			}

			@Override
			public SearchResults wearing(int index, Set<Integer> excluded, Map<Slot, Integer> worn, int pass) throws IOException
			{
				progress.expectSearches(1);
				return groupOf[index] == null ? searchFrom.run(index, excluded, pass, null, false, worn) : null;
			}
		};
		// Only these encounters choose the kit; display-only ones are added afterwards from the kit's own gear.
		List<Integer> planned = new ArrayList<>();
		for (int index = 0; index < encounters.size(); index++)
		{
			if (!encounters.get(index).isDisplayOnly()) { planned.add(index); }
		}
		List<RaidEncounter> kitEncounters = planned.stream().map(encounters::get).collect(Collectors.toList());
		ExecutorService helpers = searchHelpers;
		RaidConsolidator.Search kitSearch = new RaidConsolidator.Search()
		{
			@Override public void searching(DoubleConsumer work) { progress.searching(work); }

			@Override public java.util.concurrent.Executor helpers() { return helpers; }

			@Override public int helperCount() { return helpers == null ? 0 : SEARCH_HELPERS; }

			@Override
			public SearchResults run(int index, Set<Integer> excluded, int pass) throws IOException
			{
				return search.run(planned.get(index), excluded, pass);
			}

			@Override
			public SearchResults wearing(int index, Set<Integer> excluded, Map<Slot, Integer> worn, int pass) throws IOException
			{
				return search.wearing(planned.get(index), excluded, worn, pass);
			}
		};
		List<SearchResults> results = new ArrayList<>();
		for (int position = 0; position < planned.size(); position++)
		{
			int index = planned.get(position);
			progress.beginEncounter(position);
			SearchResults result = search.run(index, Collections.emptySet(), 0);
			if (result == null) { return null; }
			if (result.isEmpty() || result.hasBlockingLock())
			{
				RaidEncounter encounter = encounters.get(index);
				String duty = encounter.getLoadoutRequirement() == RaidLoadout.ICE_BARRAGE
					? " Ice Barrage needs 94 Magic after boosts, Ancient spells and usable magic gear; manual casting is supported. Check rune exclusions and locks."
					: " Check owned gear, locks and allowed styles.";
				throw new RaidKitUnavailable("No usable setup for " + encounter.getLabel() + "." + duty);
			}
			results.add(result);
			progress.endEncounter(position);
		}
		// The Wardens' shield breaks and Vasa's crystals depend on how fast the party damages the core and Vasa.
		List<RaidEncounter> weighted = RaidPlanType.withCycles(kitEncounters, results, config.raidPartySize());
		List<RaidEncounter> shown = new ArrayList<>(encounters);
		for (int index = 0; index < planned.size(); index++) { shown.set(planned.get(index), weighted.get(index)); }
		int limit = Math.max(0, Math.min(28, config.raidEquipmentLimit()));
		long budget = config.mode() == SearchMode.BUDGET ? Budget.parse(config.budget()) : Long.MAX_VALUE;
		Set<Integer> excludedSupplies = buildSettings(config).getExcluded();
		IntPredicate kitOwns = id -> owned.contains(id) && !excludedSupplies.contains(id);
		publishSearchActivity(generation, "Packing raid kit");
		RaidPlan selected = RaidPlanner.select(type, spellbook, weighted, results, limit, budget, kitOwns,
			item -> price(quotes, item), config.ammoCount(), id -> quantities.getOrDefault(id, 0L), cancelled, progress.selection());
		// A feasible beam result can sacrifice a whole combat class to save starting inventory slots.
		// Compare shared-armour alternatives whenever the independently best setups do not fit.
		List<SetupResult> bestSetups = RaidConsolidator.best(results, weighted);
		RaidPlan independent = RaidPlanner.select(type, spellbook, weighted,
			java.util.stream.IntStream.range(0, results.size()).mapToObj(i -> results.get(i).withSetup(bestSetups.get(i)))
				.collect(Collectors.toList()), limit, budget, kitOwns, item -> price(quotes, item), config.ammoCount(),
			id -> quantities.getOrDefault(id, 0L), cancelled, progress.independentSelection());
		boolean cutShort = false;
		if (independent == null && !cancelled.getAsBoolean())
		{
			publishSearchActivity(generation, "Comparing shared gear");
			Set<Integer> locked = buildSettings(config).getLocks().values().stream().map(SlotLock::getItemId).collect(Collectors.toSet());
			// Only kits faster than the beam's own result can replace it, so let consolidation prune against it; and
			// only kits faster than another book's or path order's plan can be chosen, so against that too.
			double own = selected == null ? Double.POSITIVE_INFINITY
				: RaidConsolidator.seconds(selected.getSetups(), selected.getResults(), weighted);
			double bound = Math.min(own, beat);
			RaidPlan consolidated = RaidConsolidator.select(data, type, spellbook, weighted, results, locked, limit, budget,
				kitOwns, item -> price(quotes, item), config.ammoCount(), id -> quantities.getOrDefault(id, 0L), cancelled, bound, kitSearch, progress::consolidation);
			if (consolidated != null && (selected == null
				|| RaidConsolidator.seconds(consolidated.getSetups(), consolidated.getResults(), weighted)
					< RaidConsolidator.seconds(selected.getSetups(), selected.getResults(), weighted)))
			{ selected = consolidated; }
			cutShort = beat < own && (selected == null
				|| RaidConsolidator.seconds(selected.getSetups(), selected.getResults(), weighted) >= beat);
		}
		if (cancelled.getAsBoolean()) { return null; }
		if (cutShort) { throw new RaidKitSlower(); }
		if (selected == null)
		{
			// The equipment limit is a maximum, so only suggest more slots when there are more to allow.
			String raise = limit < 28 ? (budget == Long.MAX_VALUE ? "Allow more equipment slots" : "Allow more equipment slots or budget")
				: budget == Long.MAX_VALUE ? null : "Raise the budget";
			throw new RaidKitUnavailable("No complete raid kit found within " + limit
				+ " equipment slots (switches, rune pouch, loose runes and book of the dead), 28 inventory slots and the shared budget. "
				+ (raise == null ? "Adjust" : raise + ", or adjust") + " gear rules, locks or allowed styles.");
		}
		progress.finalization();
		selected = withDisplayOnly(withKitNotes(selected, config), data, shown, search, kitOwns);
		if (selected == null || cancelled.getAsBoolean()) { return null; }
		progress.complete();
		return selected.withPartySize(config.raidPartySize());
	}

	/** Clear the bank highlights, or publish a setup without its boosts. */
	public void setBankHighlightedSetup(SetupResult setup)
	{
		setBankHighlightedSetup(setup, Collections.emptyList());
	}

	/**
	 * Publish the displayed setup's equipment, loaded ammunition, spell runes (with an owned rune pouch) and
	 * boosts to the bank.
	 */
	public void setBankHighlightedSetup(SetupResult setup, List<Potion> potions)
	{
		setInventoryHighlightedSetup(setup);
		Set<Integer> ids = new HashSet<>();
		if (setup != null)
		{
			for (Slot slot : Slot.values())
			{
				addBankHighlightItem(ids, setup.getLoadout().get(slot));
			}
			addBankHighlightItem(ids, setup.getLoadout().getLoadedAmmo());
			for (SpecialPlan special : setup.getSpecials())
			{
				// A raid-provided spec weapon (the Dawnbringer) is picked up in the room, not withdrawn.
				if (!RaidPlan.providedInRaid(special.getWeapon())) { addBankHighlightItem(ids, special.getWeapon()); }
				addBankHighlightItem(ids, special.getOffhand());
				addBankHighlightItem(ids, special.getAmmo());
				addBankHighlightItem(ids, special.getLoadedAmmo());
			}
			for (List<Integer> spellItem : SpellRunes.bankItems(setup, this::ownsItem))
			{
				ids.addAll(spellItem);
			}
			for (Potion potion : potions)
			{
				// Raid supplies are handed out inside the raid, never withdrawn.
				if (!potion.isRaidSupply())
				{
					ids.addAll(BoostAccess.itemIds(potion));
				}
			}
		}
		publishBankHighlights(ids);
		if (bankView != null)
		{
			bankView.setSelection(setup, setup == null ? Collections.emptyList() : potions, this::bankItemIds,
				this::ownsItem);
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

	/** Publish the starting worn gear and the complete shared raid inventory side by side. */
	public void setBankHighlightedRaid(RaidPlan plan)
	{
		setInventoryHighlightedSetup(plan.getStartingSetup());
		Set<Integer> ids = new HashSet<>();
		plan.getEquipment().forEach(item -> addBankHighlightItem(ids, item));
		plan.getInventory().forEach(item ->
		{
			ids.addAll(item.getIds());
			item.getContents().forEach(rune -> ids.addAll(rune.getIds()));
		});
		plan.prepotItems().forEach(item -> ids.addAll(item.getIds()));
		for (SetupResult setup : plan.getSetups())
		{
			addBankHighlightItem(ids, setup.getLoadout().getLoadedAmmo());
			setup.getSpecials().forEach(special -> addBankHighlightItem(ids, special.getLoadedAmmo()));
		}
		publishBankHighlights(ids);
		if (bankView != null)
		{
			bankView.setRaidSelection(plan, this::bankItemIds);
			int life = lifecycle.get();
			clientThread.invokeLater(() -> { if (life == lifecycle.get()) { bankView.refreshSelection(); } });
		}
	}

	/** Inventory outlines mark the gear to equip for the displayed setup, rather than the whole carried kit. */
	public void setInventoryHighlightedSetup(SetupResult setup)
	{
		coxChestHighlights = null;
		Set<Integer> ids = new HashSet<>();
		if (setup != null)
		{
			for (Slot slot : Slot.values()) { addBankHighlightItem(ids, setup.getLoadout().get(slot)); }
		}
		inventoryHighlightIds = Collections.unmodifiableSet(ids);
	}

	/** Publish the sidebar's private-storage advice without changing the complete bank kit. */
	public void setCoxChestHighlights(RaidPlan plan, CoxChestPlan chest)
	{
		if (plan.getType() != RaidPlanType.COX_CM) { coxChestHighlights = null; return; }
		Map<Integer, java.awt.Color> colors = new HashMap<>();
		for (GearItem item : plan.getEquipment())
		{
			java.awt.Color color = chest.colorFor(item);
			if (color == null) { continue; }
			Set<Integer> ids = new HashSet<>();
			addBankHighlightItem(ids, item);
			ids.forEach(id -> colors.put(id, color));
		}
		coxChestHighlights = Collections.unmodifiableMap(colors);
	}

	/** Full CM kits add floor guidance alongside the selected encounter's equip outlines. */
	boolean isCoxChestHighlightMode() { return coxChestHighlights != null; }

	/** Storage outlines apply only while the active CM raid's private chest is open. */
	java.awt.Color coxChestHighlightColor(int itemId)
	{
		Map<Integer, java.awt.Color> colors = coxChestHighlights;
		if (colors == null || !raidProgress.isActive() || raidProgress.current().getType() != RaidPlanType.COX_CM || client == null)
		{ return null; }
		net.runelite.api.widgets.Widget chest = client.getWidget(net.runelite.api.gameval.InterfaceID.RaidsStoragePrivate.UNIVERSE);
		if (chest == null || chest.isHidden()) { return null; }
		java.awt.Color color = colors.get(itemId);
		return color != null && raidProgress.isFinished() ? CoxChestPlan.FINISHED_COLOR : color;
	}

	/** Swing thread: cancel current and queued work and release the selected setup and target. */
	public void clearGearSearch()
	{
		cancelGearSearch();
		setBankHighlightedSetup(null);
		if (panel != null)
		{
			panel.clearSearch();
		}
	}

	/** Swing thread: cancel current and queued work, keeping the shown results and bank layout for the next search to replace. */
	public void cancelGearSearch()
	{
		raidProgress.reset();
		activeSequence++;
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
		raidPlan = null;
		lookupTarget = null;
		searchOwned = null;
		searchQuantities = null;
		observedQuantities = null;
	}

	private boolean ownsItem(int itemId)
	{
		return ownedItems.owns(itemId) && !getExcluded().contains(itemId);
	}

	private void addBankHighlightItem(Set<Integer> ids, GearItem item)
	{
		if (item != null)
		{
			ids.addAll(bankItemIds(item));
		}
	}

	/**
	 * Same-stats kitted copies take the equipment cell before base copies. Real bank stacks always take
	 * precedence over placeholders, so the base still fills the cell when no decorated copy is available.
	 */
	List<Integer> bankItemIds(GearItem item)
	{
		List<GearItem> copies = new ArrayList<>();
		copies.add(item);
		copies.addAll(CosmeticVariants.others(gameData, item));
		copies.sort(java.util.Comparator.comparing(copy -> !CosmeticVariants.isDecorated(copy)));
		Set<Integer> ids = new LinkedHashSet<>();
		for (GearItem copy : copies)
		{
			ids.addAll(BestGearSetupBankView.itemIds(copy));
			for (int state : OwnershipRules.reviewedStates(copy.getId()))
			{
				if (ItemCosts.equivalent(copy.getId(), state))
				{
					ids.add(state);
				}
			}
		}
		return new ArrayList<>(ids);
	}

	/** Keep all alternatives available to the layout, but outline decorated bank stacks ahead of base copies. */
	private void publishBankHighlights(Set<Integer> ids)
	{
		Map<Integer, Set<Integer>> preferred = new HashMap<>();
		GameData data = gameData;
		if (data != null)
		{
			for (int id : ids)
			{
				GearItem item = data.findItem(id);
				if (item == null || CosmeticVariants.isDecorated(item)) { continue; }
				Set<Integer> decorated = new HashSet<>();
				for (GearItem copy : CosmeticVariants.others(data, item))
				{
					if (CosmeticVariants.isDecorated(copy)) { decorated.addAll(BestGearSetupBankView.itemIds(copy)); }
				}
				decorated.retainAll(ids);
				if (!decorated.isEmpty())
				{
					Set<Integer> choices = Collections.unmodifiableSet(decorated);
					for (int base : BestGearSetupBankView.itemIds(item)) { preferred.put(base, choices); }
				}
			}
		}
		bankPreferredCosmetics = Collections.unmodifiableMap(preferred);
		bankHighlightIds = Collections.unmodifiableSet(ids);
	}

	/** Only real bank holdings suppress a base outline; equipped items and placeholders don't. */
	boolean shouldHighlightBankItem(int id)
	{
		if (!bankHighlightIds.contains(id)) { return false; }
		Set<Integer> decorated = bankPreferredCosmetics.get(id);
		if (decorated != null && ownedItems != null)
		{
			Map<Integer, Long> bank = ownedItems.bank;
			for (int copy : decorated)
			{
				if (bank.getOrDefault(copy, 0L) > 0) { return false; }
			}
		}
		return true;
	}

	private static CombatClass classOf(AttackStyle.Type type)
	{
		switch (type)
		{
			case RANGED:
			case ATLATL:
				return CombatClass.RANGED;
			case MAGIC:
				return CombatClass.MAGIC;
			default:
				return CombatClass.MELEE;
		}
	}

	/**
	 * Assumptions shown beside the results rather than in the collapsed details: potions picked by name (used
	 * even where they can't be, or aren't owned) and the special-attack-only mode.
	 */
	private List<String> assumptionWarnings(GameData data, PlayerLevels levels, Monster target, SearchMode mode,
		Predicate<Potion> boosts, BestGearSetupConfig config)
	{
		List<String> warnings = new ArrayList<>();
		String[] skills = {"attack", "strength", "ranged", "magic"};
		CombatClass[] classes = {CombatClass.MELEE, CombatClass.MELEE, CombatClass.RANGED, CombatClass.MAGIC};
		int[] base = {levels.getAttack(), levels.getStrength(), levels.getRanged(), levels.getMagic()};
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < skills.length; i++)
		{
			String choice = potionChoice(config, classes[i]);
			Potion p = PotionChoice.isExplicit(choice) ? PotionChoice.resolve(data, skills[i], base[i], choice) : null;
			if (p == null || !seen.add(classes[i] + "/" + p.getName()))
			{
				continue;
			}
			List<String> problems = new ArrayList<>();
			String restriction = PotionChoice.restriction(p, target);
			if (restriction != null)
			{
				problems.add(restriction);
			}
			if (mode != SearchMode.UNLIMITED && !boosts.test(p))
			{
				problems.add(mode == SearchMode.INVENTORY_ONLY ? "not in your inventory" : mode == SearchMode.OWNED_ONLY ? "not owned"
					: "not owned and can't be bought");
			}
			warnings.add(GameData.titleCase(classes[i].name().toLowerCase(java.util.Locale.ROOT)) + " potion picked by name: "
				+ GameData.titleCase(p.getName()) + (problems.isEmpty() ? "."
				: " - " + String.join(", ", problems) + ". The estimates assume it anyway; choose Best available for a practical setup."));
		}
		return warnings;
	}

	Map<CombatClass, List<Potion>> potionsUsed(GameData data, PlayerLevels levels, Monster target, Predicate<Potion> boosts,
		BestGearSetupConfig config)
	{
		Map<CombatClass, List<Potion>> out = new EnumMap<>(CombatClass.class);
		String[] skills = {"attack", "strength", "ranged", "magic"};
		CombatClass[] classes = {CombatClass.MELEE, CombatClass.MELEE, CombatClass.RANGED, CombatClass.MAGIC};
		int[] base = {levels.getAttack(), levels.getStrength(), levels.getRanged(), levels.getMagic()};
		for (int i = 0; i < skills.length; i++)
		{
			Potion p = PotionChoice.resolve(data, skills[i], base[i], potionChoice(config, classes[i]), boosts, target);
			if (p == null)
			{
				continue;
			}
			List<Potion> list = out.computeIfAbsent(classes[i], k -> new ArrayList<>());
			// Keep each skill's entry: combined potions must retain their Strength boost for atlatl results.
			// The view deduplicates item IDs after selecting the skills the setup uses.
			list.add(p);
		}
		return out;
	}

	OptimizerSettings buildSettings(BestGearSetupConfig config)
	{
		Set<String> spellbooks = new HashSet<>();
		spellbooks.add("standard");
		if (config.ancientMagicks())
		{
			spellbooks.add("ancient");
		}
		if (config.arceuusSpellbook())
		{
			spellbooks.add("arceuus");
		}
		Set<AttackStyle.Type> styles = EnumSet.noneOf(AttackStyle.Type.class);
		if (config.styleStab())
		{
			styles.add(AttackStyle.Type.STAB);
		}
		if (config.styleSlash())
		{
			styles.add(AttackStyle.Type.SLASH);
		}
		if (config.styleCrush())
		{
			styles.add(AttackStyle.Type.CRUSH);
		}
		if (config.styleRanged())
		{
			styles.add(AttackStyle.Type.RANGED);
		}
		if (config.styleMagic())
		{
			styles.add(AttackStyle.Type.MAGIC);
		}
		if (config.styleAtlatl())
		{
			styles.add(AttackStyle.Type.ATLATL);
		}
		return OptimizerSettings.builder()
			.mode(config.mode())
			.budget(Math.max(0, Budget.parse(config.budget())))
			.wildernessRiskLimited(config.wilderness() && config.limitWildernessRisk())
			.maxExpensiveItems(Math.max(0, Math.min(11, config.maxExpensiveItems())))
			.expensiveItemThreshold(Math.max(0, Budget.parse(config.expensiveItemThreshold())))
			.spellbooks(spellbooks)
			.styles(styles)
			.weaponHands(config.weaponHands())
			.calcMode(config.calcMode())
			.depth(config.searchDepth())
			.attackXp(config.attackXp())
			.strengthXp(config.strengthXp())
			.defenceXp(config.defenceXp())
			.membersItems(config.membersItems())
			.dmmItems(config.dmmItems())
			.betaItems(config.betaItems())
			.bountyHunterItems(config.bountyHunterItems())
			.requireAtlatlAmmoRecovery(config.requireAtlatlAmmoRecovery())
			.fillMode(config.fillMode())
			.defenceFocus(config.defenceFocus())
			.fillMarginPercent(config.fillMargin())
			.ammoCount(config.ammoCount())
			.killSpecials(config.killSpecials()).specEnergy(config.specEnergy())
			.drainSpecs(config.drainSpecs())
			.raidKillShare(Math.max(0, config.raidKillShare()))
			.finalBlows(Math.max(0, Math.min(100, config.raidFinalBlowPercent())) / 100.0)
			.requireFireProtection(config.requireFireProtection())
			.antifire(config.antifire())
			.protectMagic(config.protectMagic())
			.locks(SlotLock.parse(config.locks()))
			.excluded(getExcluded())
			.excludedSupplies(getExcluded().stream().filter(SpellRunes::isSpellSupply).collect(Collectors.toSet()))
			.build();
	}

	private RaidScaling.Settings raidSettings(PlayerLevels levels, int mining, BestGearSetupConfig config)
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

	private SpecialAttacks specials(BestGearSetupConfig config)
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
				if (RaidScaling.toaPath(target))
				{
					scaling += raid.isToaWalkThePath() ? ", starting path level " + raid.getToaPathLevel()
						+ ", Walk the Path: expected HP after " + raid.getToaCompletedPaths() + " paths"
						: ", path level " + raid.getToaPathLevel();
				}
				break;
			default:
				return null;
		}
		return scaling + (live ? " (live)" : " (Fight settings)");
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

	private List<String> describeSearch(GameData data, PlayerLevels levels, Monster unscaled, Monster base, Monster drained,
		SpecialAttacks specials, CombatContext ctx, OptimizerSettings settings, RaidScaling.Settings raid, BestGearSetupConfig config)
	{
		// Only what changed this result or needs the player's attention; the header shows party scaling and Defence.
		List<String> notes = new ArrayList<>();
		if (specials.isAny() && config.killSpecials())
		{
			notes.add("Pre-fight preparation is applied before the specs planned into each kill; clear it if it describes the same specs.");
		}
		if (settings.isWildernessRiskLimited())
		{
			notes.add("Wilderness limit: at most " + settings.getMaxExpensiveItems() + " equipped items worth "
				+ Budget.format(settings.getExpensiveItemThreshold()) + " GP or more each.");
			if (Budget.parse(config.expensiveItemThreshold()) < 0)
			{
				notes.add(0, "Invalid expensive-item threshold: using 0 GP, so every equipped item counts as expensive. Enter an amount such as 500k or 1m in Fight.");
			}
		}
		if (settings.isRequireAtlatlAmmoRecovery())
		{
			notes.add("Ranged attacks firing ammo an Ava's device recovers require an Ava's device, an assembler cape or Dizana's quiver; special-attack switches are exempt.");
		}
		String statusNote = statusNote(base);
		if (statusNote != null)
		{
			notes.add(statusNote);
		}
		String raidNote = RaidScaling.describe(unscaled, base, raid);
		if (raidNote != null)
		{
			notes.add(raidNote);
		}
		String phaseNote = EncounterPhases.describe(base);
		if (phaseNote != null)
		{
			notes.add(phaseNote);
		}
		if (base.getName().toLowerCase(java.util.Locale.ROOT).startsWith("vardorvis"))
		{
			notes.add("Vardorvis: Defence and Strength follow his HP; DPS is averaged over the fight.");
		}
		if (base.getWeakness() > 0)
		{
			notes.add("Elemental weakness: " + base.getWeaknessType() + " +" + base.getWeakness() + "% base accuracy and damage.");
		}
		if (base.hasAttribute("demon") && config.arceuusSpellbook())
		{
			notes.add(config.markOfDarkness() ? "Demonbane spells assume Mark of Darkness is active."
				: "Mark of Darkness is off, so demonbane spells get only their base demon accuracy bonus.");
		}
		if (ctx.getAoeTargets() > 1)
		{
			notes.add("AoE: " + ctx.getAoeTargets() + " grouped enemies; ranked by total DPS.");
		}
		String fireNote = DragonfireProtection.note(base, settings);
		if (fireNote != null)
		{
			notes.add(fireNote);
		}
		notes.addAll(SlayerEquipment.notes(base, settings));
		int distance = AttackReach.distance(base, settings.getTargetDistance());
		if (distance > 1)
		{
			notes.add("Fighting distance: " + distance + " tiles; attacks that can't reach are excluded.");
		}
		String reachNote = AttackReach.restrictionNote(base);
		if (reachNote != null)
		{
			notes.add(reachNote);
		}
		if (base.isImmuneThrall() && ctx.getThrall() != null)
		{
			notes.add("Thralls: this target is immune, so none are added.");
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
		int locks = getLocks().size();
		int excluded = getExcluded().size();
		if (locks > 0 || excluded > 0)
		{
			notes.add(locks + " slot lock(s), " + excluded + " excluded item(s)");
		}
		return notes;
	}

	// ------------------------------------------------------------ locks, exclusions, potions (panel API)

	public GameData getGameData()
	{
		return gameData;
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
		setConfig(BestGearSetupConfig.LOCKS_KEY, SlotLock.format(locks));
	}

	/** Lock an item's own slot to it; an excluded item is restored first so the lock can be used. */
	public void lockItem(GearItem item)
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
			setConfig(BestGearSetupConfig.EXCLUDED_KEY, ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
		}
	}

	public void clearLocks()
	{
		setConfig(BestGearSetupConfig.LOCKS_KEY, "");
	}

	public void clearExclusions()
	{
		setConfig(BestGearSetupConfig.EXCLUDED_KEY, "");
	}

	public void setManualOwned(int itemId, boolean owned)
	{
		ownedItems.setManual(itemId, owned);
		// Owned items are per-account settings, which onConfigChanged ignores, so refresh the lists here.
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.onManualOwnedChanged();
			}
		});
		rerun();
	}

	public String getPotionChoice(CombatClass cls)
	{
		// The plugin settings are canonical; legacy free-text choices were migrated at startup.
		return potionValue(cls == CombatClass.MELEE ? config.meleePotion() : cls == CombatClass.RANGED ? config.rangedPotion() : config.magicPotion());
	}

	/**
	 * Re-run the last search, e.g. after a setting changed. The queued rerun is dropped if, by the time it runs,
	 * the plugin restarted, the account changed or any newer search started (which also coalesces repeated
	 * requests into one search).
	 */
	public void rerun()
	{
		MonsterSummary last = lastSearched;
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
			for (GearItem item : data.getItems(slot))
			{
				if (!map.containsKey(item.getId()))
				{
					map.put(item.getId(), ItemCosts.price(item, itemManager::getItemPrice,
						id -> itemManager.getItemComposition(id).isTradeable()));
				}
			}
		}
		return map;
	}

	/** Owned directly or through a reviewed equivalent charge state. */
	public boolean owns(GearItem item)
	{
		if (ownedItems.owns(item.getId()))
		{
			return true;
		}
		for (int variant : item.getOwnershipVariants())
		{
			if (ownedItems.owns(variant))
			{
				return true;
			}
		}
		return false;
	}

	/** Worn directly or as a reviewed equivalent charge state. */
	public boolean wears(GearItem item)
	{
		if (ownedItems.isWorn(item.getId()))
		{
			return true;
		}
		for (int variant : item.getOwnershipVariants())
		{
			if (ownedItems.isWorn(variant))
			{
				return true;
			}
		}
		return false;
	}

	/** Tracked locations for the exact item and its reviewed ownership variants. */
	public Set<String> itemLocations(GearItem item)
	{
		return ownedItems.locations(item.getId(), item.getOwnershipVariants());
	}

	/** Units held of the item and its catalogued variants; {@link OwnedItems#UNLIMITED} if marked owned by hand. */
	public long heldQuantity(GearItem item)
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
	public long price(GearItem item)
	{
		return price(prices, item);
	}

	private static long price(Map<Integer, Long> quotes, GearItem item)
	{
		Long price = quotes.get(item.getId());
		return price != null ? price : ItemCosts.UNKNOWN;
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
			for (GearItem item : data.getItems(slot))
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


	private static String potionChoice(BestGearSetupConfig config, CombatClass cls)
	{
		return cls == CombatClass.MELEE ? config.potionMelee() : cls == CombatClass.RANGED ? config.potionRanged() : config.potionMagic();
	}

	private static String potionValue(Object selected)
	{
		if (selected instanceof PotionOptions.Melee) { return ((PotionOptions.Melee) selected).value(); }
		if (selected instanceof PotionOptions.Ranged) { return ((PotionOptions.Ranged) selected).value(); }
		return ((PotionOptions.Magic) selected).value();
	}

	static Object potionOption(CombatClass cls, String name)
	{
		Object[] choices = cls == CombatClass.MELEE ? PotionOptions.Melee.values() : cls == CombatClass.RANGED ? PotionOptions.Ranged.values() : PotionOptions.Magic.values();
		for (Object choice : choices) { if (potionValue(choice).equalsIgnoreCase(name)) { return choice; } }
		return null;
	}

	private void migratePreferences()
	{
		Map<String, String> stored = new HashMap<>();
		for (String key : new String[]{PreferenceMigration.MARKER, "potionMELEE", "potionRANGED", "potionMAGIC",
			"meleePotionChoice", "rangedPotionChoice", "magicPotionChoice", BestGearSetupConfig.ON_TASK_KEY, "taskMode",
			"raidPartySize", "toaRaidLevel", "toaPathLevel", "coxChallengeMode"})
		{ stored.put(key, configManager.getConfiguration(BestGearSetupConfig.GROUP, key)); }
		PreferenceMigration.plan(stored).forEach((key, value) -> configManager.setConfiguration(BestGearSetupConfig.GROUP, key, value));
	}

	/** Client thread: the interacting or looked-up NPC if it is a live, nearby instance of this monster. */
	private NPC matchingLiveTarget(MonsterSummary summary)
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
		configManager.setConfiguration(BestGearSetupConfig.GROUP, key, value);
	}
}
