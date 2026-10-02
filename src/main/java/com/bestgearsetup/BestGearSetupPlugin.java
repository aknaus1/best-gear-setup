package com.bestgearsetup;

import com.bestgearsetup.calc.AttackReach;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.CombatContext;
import com.bestgearsetup.calc.CombatModifiers;
import com.bestgearsetup.calc.DragonfireProtection;
import com.bestgearsetup.calc.EncounterPhases;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.MonsterStates;
import com.bestgearsetup.calc.Optimizer;
import com.bestgearsetup.calc.OptimizerSettings;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.PotionChoice;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SlayerEquipment;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.calc.SpecialAttacks;
import com.bestgearsetup.calc.WeaponPoison;
import com.bestgearsetup.calc.WeaponRules;
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
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
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
	/** RuneScape-profile key remembering whether the Elite Kourend & Kebos Diary is complete. */
	private static final String KOUREND_ELITE_KEY = "kourendEliteDiary";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private BestGearSetupConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private GameDataLoader api;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private SkillIconManager skillIconManager;

	@Getter
	@Inject
	private OwnedItems ownedItems;

	private BestGearSetupPanel panel;
	private NavigationButton navButton;
	private ExecutorService executor;
	private volatile GameData gameData;
	private volatile Future<?> search;
	private final AtomicInteger searchGeneration = new AtomicInteger();
	/** Bumped on startup and shutdown so loading work queued by an earlier run cannot publish into this one. */
	private final AtomicInteger lifecycle = new AtomicInteger();
	private volatile Map<Integer, Long> prices = Collections.emptyMap();
	private volatile MonsterSummary lastSearched;
	/** Equipment ids and their variants: the only ownership changes that can alter a result. */
	private volatile Set<Integer> gearIds = Collections.emptySet();
	/** Last levels written to the profile; client thread only. */
	private String lastRememberedLevels;
	/** Ownership the latest search was computed with; null when no results depend on it. */
	private volatile Set<Integer> searchOwned;
	/** Stack sizes and ammo quantity the latest search was computed with. */
	private volatile Map<Integer, Long> searchQuantities;
	private volatile int searchAmmoCount;
	/**
	 * Stack sizes at the last relevant supply change since the search, or null if none; only a further change
	 * restarts the settle timer.
	 */
	private volatile Map<Integer, Long> observedQuantities;
	/** Ammunition and thrown weapons (and their variants): the stacks whose size can alter a result. */
	private volatile Set<Integer> consumableIds = Collections.emptySet();
	/** Swing thread only: waits for a changing stack to settle (e.g. while firing) before searching again. */
	private javax.swing.Timer supplyRefresh;
	/** How long a stack must stay unchanged before a quantity-only change reruns the search. */
	int supplySettleMillis = 5000;
	/** Read and written on the client thread only. */
	private NPC lookupTarget;
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
		int life = lifecycle.incrementAndGet();
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
			return t;
		});
		panel = new BestGearSetupPanel(this, config, itemManager, spriteManager, skillIconManager);
		BufferedImage icon = BestGearSetupPanel.createNavIcon();
		navButton = NavigationButton.builder()
			.tooltip("Best Gear Setup")
			.icon(icon)
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		ownedItems.load();
		panel.updateOwnedStatus();
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
		clientToolbar.removeNavigation(navButton);
		executor.shutdownNow();
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
			GameData data = api.loadGameData();
			if (life != lifecycle.get() || !raiseRequirementsFromCache(data))
			{
				return;
			}
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
				}
			});
		}
		catch (IOException | RuntimeException e)
		{
			if (life != lifecycle.get() || Thread.currentThread().isInterrupted())
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
	 * @return false if loading was interrupted (the plugin stopped), so the data must not be published
	 */
	private boolean raiseRequirementsFromCache(GameData data)
	{
		if (!cacheReady())
		{
			log.debug("Game cache not loaded; using the bundled wear levels");
			return !Thread.currentThread().isInterrupted();
		}
		List<GearItem> items = new ArrayList<>();
		for (Slot slot : Slot.values())
		{
			items.addAll(data.getItems(slot));
		}
		CompletableFuture<List<EquipmentRequirements.Resolution>> read = new CompletableFuture<>();
		clientThread.invoke(() ->
		{
			try
			{
				List<EquipmentRequirements.Resolution> out = new ArrayList<>(items.size());
				for (GearItem item : items)
				{
					out.add(EquipmentRequirements.get().resolve(item, client.getItemDefinition(item.getId())));
				}
				read.complete(out);
			}
			catch (RuntimeException e)
			{
				read.completeExceptionally(e);
			}
		});
		try
		{
			List<EquipmentRequirements.Resolution> resolutions = read.get(10, TimeUnit.SECONDS);
			int raised = 0;
			for (int i = 0; i < items.size(); i++)
			{
				raised += resolutions.get(i).raise(items.get(i)) ? 1 : 0;
			}
			log.debug("Live cache raised wear levels on {} of {} items", raised, items.size());
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return false;
		}
		catch (ExecutionException | TimeoutException e)
		{
			log.debug("Live cache requirements unavailable; using the bundled levels", e);
		}
		return !Thread.currentThread().isInterrupted();
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
		if (event.getGameState().getState() >= GameState.LOGIN_SCREEN.getState())
		{
			SwingUtilities.invokeLater(this::requestGameData);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		ownedItems.onContainerChanged(event.getContainerId(), event.getItemContainer());
		invalidateIfOwnershipChanged();
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.updateOwnedStatus();
			}
		});
	}

	/**
	 * Results are valid only for the items owned when they were computed: if equipment was gained or lost since,
	 * discard them and search again with the new ownership. A changed ammunition or thrown-weapon stack that
	 * affects the requested ammo quantity searches again once the stack stops changing, so firing doesn't restart
	 * the search every attack. Food, potions and other non-gear changes are ignored.
	 */
	private void invalidateIfOwnershipChanged()
	{
		Set<Integer> before = searchOwned;
		if (before == null || lastSearched == null)
		{
			return;
		}
		Set<Integer> now = ownedItems.snapshot();
		if (before != now && gearOwnershipDiffers(before, now, gearIds))
		{
			searchOwned = now;
			rerun();
			return;
		}
		// Compare with the last observed supply, not the search's: unrelated container events (food, potions)
		// must not keep pushing back the refresh of a stack that has already settled.
		Map<Integer, Long> quantities = ownedItems.quantitySnapshot();
		Map<Integer, Long> observed = observedQuantities;
		if (supplyDiffers(observed != null ? observed : searchQuantities, quantities, consumableIds, searchAmmoCount))
		{
			observedQuantities = quantities;
			SwingUtilities.invokeLater(this::scheduleSupplyRefresh);
		}
	}

	/** Swing thread: (re)start the settle timer; each further relevant stack change pushes the refresh back. */
	private void scheduleSupplyRefresh()
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
	private void refreshSupply()
	{
		Map<Integer, Long> now = ownedItems.quantitySnapshot();
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
		int generation = searchGeneration.incrementAndGet();
		if (search != null)
		{
			search.cancel(true);
		}
		lastSearched = null;
		searchOwned = null;
		searchQuantities = null;
		observedQuantities = null;
		lookupTarget = null;
		lastRememberedLevels = null;
		ownedItems.load();
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				if (generation == searchGeneration.get())
				{
					panel.clearResults("Account changed. Find best setup again to use this account's levels and items.");
				}
				panel.syncFromConfig();
				panel.updateOwnedStatus();
			}
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// Per-account keys (owned items) change with every bank update; they must not trigger a search. The
		// boss filter only changes search suggestions.
		if (!BestGearSetupConfig.GROUP.equals(event.getGroup()) || panel == null || event.getProfile() != null
			|| "showMenuOption".equals(event.getKey()) || BestGearSetupConfig.BOSSES_ONLY_KEY.equals(event.getKey()))
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
			panel.selectMonster(match, true);
		});
	}

	// ------------------------------------------------------------ search

	/** Measure the selected live NPC's nearest tile, then rerun at that fighting distance. */
	public void useCurrentDistance()
	{
		clientThread.invokeLater(() ->
		{
			MonsterSummary selected = lastSearched;
			Player player = client.getLocalPlayer();
			NPC target = player != null && player.getInteracting() instanceof NPC
				? (NPC) player.getInteracting() : null;
			if (!matchesDistanceTarget(target))
			{
				target = lookupTarget;
			}
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				showDistanceError("Not logged in: log in to measure your distance, or set Distance by hand.");
				return;
			}
			if (player == null || !matchesDistanceTarget(target) || player.getWorldView() != target.getWorldView())
			{
				showDistanceError("Select a nearby NPC with Best setup, or attack the selected monster first.");
				return;
			}
			int distance = player.getWorldArea().distanceTo(target.getWorldArea());
			if (distance > 128)
			{
				showDistanceError("The selected NPC is too far away or on another floor.");
				return;
			}
			int measured = Math.max(1, distance);
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null && selected == lastSearched)
				{
					setConfig(BestGearSetupConfig.DISTANCE_KEY, measured);
				}
			});
		});
	}

	private void showDistanceError(String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.showError(message);
			}
		});
	}

	private boolean matchesDistanceTarget(NPC npc)
	{
		GameData data = gameData;
		MonsterSummary selected = lastSearched;
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
		GameData data = gameData;
		if (data == null || executor == null)
		{
			return;
		}
		lastSearched = summary;
		int generation = searchGeneration.incrementAndGet();
		if (search != null)
		{
			search.cancel(true);
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
			// ItemManager prices must be read on the client thread; snapshot them for the search and UI.
			Map<Integer, Long> quotes = snapshotPrices(data);
			prices = quotes;
			// Ownership is fixed for the whole search; later gear changes invalidate the results instead.
			Set<Integer> owned = ownedItems.snapshot();
			Map<Integer, Long> quantities = ownedItems.quantitySnapshot();
			searchOwned = owned;
			searchQuantities = quantities;
			observedQuantities = null;
			searchAmmoCount = config.ammoCount();
			search = executor.submit(() -> runSearch(data, summary, levels, rememberedLevels, prayerUnlocks,
				kourendElite, mining, quotes, owned, quantities, generation));
		});
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
		Set<String> prayerUnlocks, Boolean kourendElite, int mining, Map<Integer, Long> quotes, Set<Integer> owned,
		Map<Integer, Long> quantities, int generation)
	{
		try
		{
			boolean assumedLevels = knownLevels == null;
			PlayerLevels levels = assumedLevels ? PlayerLevels.maxed() : knownLevels;
			Monster baseMonster = api.getMonster(summary);
			SpecialAttacks specials = specials();
			// Wiki order: raid scaling, health-dependent stats, then pre-fight defence reductions.
			RaidScaling.Settings raid = raidSettings(levels, mining);
			Monster scaled = RaidScaling.apply(baseMonster, raid);
			scaled.setPhase(config.encounterPhase());
			EncounterPhases.applyStats(scaled);
			Monster monster = specials.apply(MonsterStates.atHealth(scaled, config.monsterHitpoints()));

			// Rada's blessing 4 is an Elite diary reward, so owning it proves completion before the varbit is seen.
			boolean eliteDiary = kourendElite != null ? kourendElite : owned.contains(ItemID.ZEAH_BLESSING_ELITE);
			OptimizerSettings settings = buildSettings().toBuilder().kourendEliteDiary(eliteDiary).build();
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
			Predicate<Potion> boosts = BoostAccess.allowed(settings.getMode(), owned);
			CombatContext ctx = new CombatContext(monster, levels, config.onSlayerTask(),
				PotionChoice.boostedLevel(data, "attack", levels.getAttack(), getPotionChoice(CombatClass.MELEE), boosts),
				PotionChoice.boostedLevel(data, "strength", levels.getStrength(), getPotionChoice(CombatClass.MELEE), boosts),
				PotionChoice.boostedLevel(data, "ranged", levels.getRanged(), getPotionChoice(CombatClass.RANGED), boosts),
				PotionChoice.boostedLevel(data, "magic", levels.getMagic(), getPotionChoice(CombatClass.MAGIC), boosts),
				prayers, config.thrall()).withFightOptions(config.aoe() ? config.aoeTargets() : 1, config.targetDistance())
				.withModifiers(CombatModifiers.builder().currentHitpoints(config.currentHitpoints())
					.monsterHitpoints(config.monsterHitpoints()).wilderness(config.wilderness())
					.forinthrySurge(config.forinthrySurge()).specialAttack(config.specialAttacks())
					.weaponPoison(config.weaponPoison()).charge(config.charge())
					.markOfDarkness(config.markOfDarkness()).sunfireRunes(config.sunfireRunes())
					.kandarinDiary(config.kandarinDiary()).soulreaperStacks(config.soulreaperStacks())
					.miningLevel(config.miningLevel() == 0 ? mining : config.miningLevel()).build())
				// HP-dependent target stats are re-derived in the same order for whole-fight averages.
				.withHealthStates(hp -> specials.apply(MonsterStates.atHealth(scaled, hp)));
			// One tab per attack type: melee is optimised separately for stab, slash and crush.
			Map<AttackStyle.Type, List<SetupResult>> byType = new LinkedHashMap<>();
			for (AttackStyle.Type type : AttackStyle.Type.values())
			{
				if (Thread.currentThread().isInterrupted() || generation != searchGeneration.get())
				{
					return;
				}
				if (!settings.getStyles().contains(type))
				{
					continue;
				}
				OptimizerSettings typeSettings = settings.toBuilder().styles(EnumSet.of(type)).build();
				Optimizer optimizer = new Optimizer(data, ctx, typeSettings, owned::contains,
					id -> quantities.getOrDefault(id, 0L), item -> price(quotes, item));
				List<SetupResult> results = optimizer.optimize(classOf(type),
					() -> Thread.currentThread().isInterrupted() || generation != searchGeneration.get());
				if (!results.isEmpty())
				{
					byType.put(type, results);
				}
			}
			if (generation != searchGeneration.get())
			{
				return;
			}
			List<String> notes = describeSearch(data, levels, baseMonster, scaled, monster, specials, ctx, settings, raid);
			if (config.usePrayers() && prayerUnlocks == null)
			{
				notes.add("Prayer unlocks are unknown until you log in; Piety, Rigour, Augury and similar are assumed unlocked.");
			}
			if (kourendElite == null && !eliteDiary && SlayerEquipment.inKaruulm(baseMonster))
			{
				notes.add("Elite Kourend & Kebos Diary status is unknown until you log in, so Karuulm boots are required.");
			}
			if (settings.getMode() == SearchMode.OWNED_ONLY && usesBestPotion())
			{
				notes.add("Boosts: \"Best\" potions use only potions and hearts you own (any dose; divine counts).");
			}
			else if (settings.getMode() == SearchMode.BUDGET && usesBestPotion())
			{
				notes.add("Boosts: \"Best\" potions may be bought (not counted in the budget); hearts must be owned.");
			}
			String unpricedNote = unpricedNote(data, quotes, settings);
			if (unpricedNote != null)
			{
				notes.add(0, unpricedNote);
			}
			List<LockStatus> locks = LockStatus.check(data,
				new Optimizer(data, ctx, settings, owned::contains, id -> quantities.getOrDefault(id, 0L),
					item -> price(quotes, item)), settings.getLocks());
			SearchResults found = new SearchResults(monster, ctx.getTargetHitpoints(), byType, notes, ctx.getPrayers(),
				potionsUsed(data, levels, boosts), assumedLevels, locks, config.markOfDarkness(),
				rememberedLevels, assumptionWarnings(data, levels, monster, settings.getMode(), boosts));
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null && generation == searchGeneration.get())
				{
					panel.showResults(found);
				}
			});
		}
		catch (IOException | RuntimeException | Error e)
		{
			// Errors (e.g. assertion failures) would otherwise vanish inside the executor's Future.
			log.warn("Best setup search failed", e);
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null && generation == searchGeneration.get())
				{
					panel.showError("Search failed: " + e.getMessage());
				}
			});
		}
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
		Predicate<Potion> boosts)
	{
		List<String> warnings = new ArrayList<>();
		String[] skills = {"attack", "strength", "ranged", "magic"};
		CombatClass[] classes = {CombatClass.MELEE, CombatClass.MELEE, CombatClass.RANGED, CombatClass.MAGIC};
		int[] base = {levels.getAttack(), levels.getStrength(), levels.getRanged(), levels.getMagic()};
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < skills.length; i++)
		{
			String choice = getPotionChoice(classes[i]);
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
				problems.add(mode == SearchMode.OWNED_ONLY ? "not owned" : "not owned and can't be bought");
			}
			warnings.add(GameData.titleCase(classes[i].name().toLowerCase(java.util.Locale.ROOT)) + " potion picked by name: "
				+ GameData.titleCase(p.getName()) + (problems.isEmpty() ? "."
				: " - " + String.join(", ", problems) + ". The estimates assume it anyway; choose Best available for a practical setup."));
		}
		if (config.specialAttacks())
		{
			warnings.add("Special attacks only: DPS, TTK and kills/hour assume back-to-back specials with no energy"
				+ " regeneration limit.");
		}
		return warnings;
	}

	private Map<CombatClass, List<Potion>> potionsUsed(GameData data, PlayerLevels levels, Predicate<Potion> boosts)
	{
		Map<CombatClass, List<Potion>> out = new EnumMap<>(CombatClass.class);
		String[] skills = {"attack", "strength", "ranged", "magic"};
		CombatClass[] classes = {CombatClass.MELEE, CombatClass.MELEE, CombatClass.RANGED, CombatClass.MAGIC};
		int[] base = {levels.getAttack(), levels.getStrength(), levels.getRanged(), levels.getMagic()};
		for (int i = 0; i < skills.length; i++)
		{
			Potion p = PotionChoice.resolve(data, skills[i], base[i], getPotionChoice(classes[i]), boosts);
			if (p == null)
			{
				continue;
			}
			List<Potion> list = out.computeIfAbsent(classes[i], k -> new ArrayList<>());
			if (list.stream().noneMatch(x -> x.getId() == p.getId()))
			{
				list.add(p);
			}
		}
		return out;
	}

	private OptimizerSettings buildSettings()
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
			.spellbooks(spellbooks)
			.styles(styles)
			.weaponHands(config.weaponHands())
			.calcMode(config.calcMode())
			.depth(config.searchDepth())
			.attackXp(config.attackXp())
			.strengthXp(config.strengthXp())
			.defenceXp(config.defenceXp())
			.xpPreference(config.xpPreference())
			.membersItems(config.membersItems())
			.dmmItems(config.dmmItems())
			.betaItems(config.betaItems())
			.bountyHunterItems(config.bountyHunterItems())
			.fillMode(config.fillMode())
			.defenceFocus(config.defenceFocus())
			.fillMarginPercent(config.fillMargin())
			.ammoCount(config.ammoCount())
			.targetDistance(config.targetDistance())
			.requireFireProtection(config.requireFireProtection())
			.antifire(config.antifire())
			.protectMagic(config.protectMagic())
			.locks(getLocks())
			.excluded(getExcluded())
			.build();
	}

	private RaidScaling.Settings raidSettings(PlayerLevels levels, int mining)
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

	private SpecialAttacks specials()
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

	/** Which damage-over-time effects the target takes, and the assumed weapon poison. */
	private String statusNote(Monster target)
	{
		StatusImmunities.Immunity im = StatusImmunities.of(target);
		String venom = im.isVenomBecomesPoison() ? "venom becomes poison" : im.isVenomImmune() ? "venom immune" : "venom";
		String poison = im.isPoisonImmune() ? "poison immune" : "poison";
		String burn = im.getBurnImmunity() >= StatusImmunities.BURN_STRONG ? "burn immune"
			: im.getBurnImmunity() == StatusImmunities.BURN_NORMAL ? "strong burns only" : "burns";
		return "Damage over time: " + poison + ", " + venom + ", " + burn
			+ (im.isKnown() ? "" : " (target flags; no Wiki immunity data)")
			+ (config.weaponPoison() == WeaponPoison.NONE ? "" : "; " + config.weaponPoison() + " on poisonable weapons")
			+ ". Poison, venom and burns are averaged over the kill.";
	}

	/** Human-readable summary of the assumptions behind the results. */
	private List<String> describeSearch(GameData data, PlayerLevels levels, Monster unscaled, Monster base, Monster drained,
		SpecialAttacks specials, CombatContext ctx, OptimizerSettings settings, RaidScaling.Settings raid)
	{
		List<String> notes = new ArrayList<>();
		notes.add("Combat snapshot: your HP " + (config.currentHitpoints() == 0 ? levels.getHitpoints()
			: Math.min(levels.getHitpoints(), config.currentHitpoints()))
			+ ", target HP " + (config.monsterHitpoints() == 0 ? base.getHitpoints()
			: Math.min(base.getHitpoints(), config.monsterHitpoints()))
			+ ". Ruby bolts, the Sun keris in ToA and Vardorvis are averaged from this HP to the kill.");
		if (config.specialAttacks())
		{
			notes.add("Only special attacks: each result is one special attack repeated at its weapon speed "
				+ "(energy cost shown per setup; regeneration is not limited). Weapons without a damaging special are excluded.");
		}
		notes.add(api.describeSource());
		notes.add("Wear requirements: bundled Wiki text, cache parameters and reviewed local rules; live cache levels can only raise them.");
		notes.add(statusNote(base));
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
		if (config.wilderness() || config.forinthrySurge() || config.charge()
			|| config.sunfireRunes() || config.kandarinDiary() || config.soulreaperStacks() > 0)
		{
			notes.add("Optional combat buffs selected in Fight options; bonuses apply only to compatible attacks.");
		}
		if (base.hasAttribute("demon") && config.arceuusSpellbook())
		{
			notes.add(config.markOfDarkness() ? "Demonbane spells assume Mark of Darkness is active (Fight options)."
				: "Mark of Darkness is off, so demonbane spells get only their base demon accuracy bonus.");
		}
		if (ctx.getAoeTargets() > 1)
		{
			notes.add("AoE: " + ctx.getAoeTargets() + " identical grouped enemies in multicombat; attack limits apply. "
				+ "Ranking uses total DPS. Requires valid splash/bounce positions and identical task/gear bonuses.");
		}
		String fireNote = DragonfireProtection.note(base, settings);
		if (fireNote != null)
		{
			notes.add(fireNote);
		}
		notes.addAll(SlayerEquipment.notes(base, settings));
		int distance = AttackReach.distance(base, settings.getTargetDistance());
		notes.add("Fighting distance: " + distance + " tile(s)"
			+ (settings.getTargetDistance() == 0 ? " (Auto)" : " (selected; encounter limits apply)"));
		if (distance > 1)
		{
			notes.add("Unreachable attacks and weapons with unknown range are excluded.");
		}
		String reachNote = AttackReach.restrictionNote(base);
		if (reachNote != null)
		{
			notes.add(reachNote);
		}
		// Prayers and potions are shown as icons next to each setup; only list what has no icon.
		List<String> extras = new ArrayList<>();
		if (ctx.isOnTask())
		{
			extras.add("on slayer task");
		}
		if (ctx.isThrall())
		{
			extras.add("thrall included");
		}
		if (!extras.isEmpty())
		{
			notes.add(GameData.titleCase(String.join(", ", extras)));
		}
		if (specials.isAny())
		{
			notes.add(String.format("After specials: Defence %d -> %d, Magic %d -> %d, magic def %d -> %d",
				base.getDefenceLevel(), drained.getDefenceLevel(), base.getMagicLevel(), drained.getMagicLevel(),
				base.getDefMagic(), drained.getDefMagic()));
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

	public void clearLocksAndExclusions()
	{
		setConfig(BestGearSetupConfig.LOCKS_KEY, "");
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

	private boolean usesBestPotion()
	{
		for (CombatClass cls : CombatClass.values())
		{
			if (PotionChoice.BEST.equalsIgnoreCase(getPotionChoice(cls)))
			{
				return true;
			}
		}
		return false;
	}

	public String getPotionChoice(CombatClass cls)
	{
		String v = configManager.getConfiguration(BestGearSetupConfig.GROUP, BestGearSetupConfig.POTION_KEY_PREFIX + cls.name());
		return v == null || v.isEmpty() ? PotionChoice.BEST : v;
	}

	public void setPotionChoice(CombatClass cls, String choice)
	{
		setConfig(BestGearSetupConfig.POTION_KEY_PREFIX + cls.name(), choice);
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

	/** Owned, directly or through any catalogued variant (ornament kit, charges, recolour). */
	public boolean owns(GearItem item)
	{
		if (ownedItems.owns(item.getId()))
		{
			return true;
		}
		for (int variant : item.getVariants())
		{
			if (ownedItems.owns(variant))
			{
				return true;
			}
		}
		return false;
	}

	/** Units held of the item and its catalogued variants; {@link OwnedItems#UNLIMITED} if marked owned by hand. */
	public long heldQuantity(GearItem item)
	{
		Map<Integer, Long> quantities = ownedItems.quantitySnapshot();
		long held = quantities.getOrDefault(item.getId(), 0L);
		for (int variant : item.getVariants())
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

	/** Names, descriptions and ranges of the config items, for panels that edit hidden settings. */
	public Collection<ConfigItemDescriptor> getConfigItems()
	{
		return configManager.getConfigDescriptor(config).getItems();
	}

	/** Persist a setting changed from the panel. */
	public void setConfig(String key, Object value)
	{
		configManager.setConfiguration(BestGearSetupConfig.GROUP, key, value);
	}
}
