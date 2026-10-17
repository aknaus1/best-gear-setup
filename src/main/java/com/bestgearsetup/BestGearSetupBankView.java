package com.bestgearsetup;

import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsPlugin;

/** Temporary equipment tab; never changes the bank's item slots or opens a search input. */
@Singleton
class BestGearSetupBankView
{
	// Equipment silhouette on the bank's eight-column grid, matching the sidebar.
	private static final int[] CELLS = {1, 8, 9, 10, 16, 17, 18, 25, 32, 33, 34};
	// Prepot-only boosts sit below the equipment silhouette, left of the inventory.
	private static final int[] PREPOT_CELLS = {40, 41, 42, 43, 48, 49, 50, 51};
	// Loading rows (pouch runes, loaded ammo, extra prepots) and extra stacks start below the 7-row inventory.
	private static final int EXTRA_ROWS_START = 7 * 8;
	private static final int GEAR = 0;
	private static final int BOOST = 1;
	private static final int SPELL = 2;
	private static final int BANKTAB_POTIONSTORE = 15;
	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final BankSearch bankSearch;
	private final EventBus eventBus;
	private volatile Layout selection = Layout.EMPTY;
	private final Map<Widget, int[]> dragSettings = new IdentityHashMap<>();
	private final List<Widget> duplicateWidgets = new ArrayList<>();
	private final Map<Integer, Widget> duplicateSources = new HashMap<>();
	private boolean active;
	/** The layout was showing when the bank closed; show it again when the bank reopens. */
	private boolean reopen;

	@Inject
	BestGearSetupBankView(Client client, ClientThread clientThread, ItemManager itemManager, BankSearch bankSearch,
		EventBus eventBus)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.bankSearch = bankSearch;
		this.eventBus = eventBus;
	}

	/** Publish a selection from the sidebar; the bank reads one immutable snapshot per rebuild. */
	void setSelection(SetupResult setup)
	{
		setSelection(setup, Collections.emptyList());
	}

	void setSelection(SetupResult setup, List<Potion> potions)
	{
		setSelection(setup, potions, BestGearSetupBankView::itemIds, id -> false);
	}

	/**
	 * As {@link #setSelection(SetupResult, List)}, with each item's ids in the order they may take its cell and
	 * the player's ownership, which decides whether a rune pouch joins the spell runes.
	 */
	void setSelection(SetupResult setup, List<Potion> potions, Function<GearItem, List<Integer>> itemIds,
		IntPredicate owns)
	{
		selection = layoutFor(setup, potions, itemIds, owns);
	}

	void setRaidSelection(RaidPlan plan, Function<GearItem, List<Integer>> itemIds)
	{
		selection = layoutForRaid(plan, itemIds);
	}

	static Layout layoutForRaid(RaidPlan plan, Function<GearItem, List<Integer>> itemIds)
	{
		Map<Integer, GearItem> equipment = new LinkedHashMap<>();
		plan.getEquipment().forEach(item -> equipment.putIfAbsent(item.getId(), item));
		return layoutFor(plan.getStartingSetup(), plan.getInventory(), equipment, plan.prepotItems(), plan.getSetups(), itemIds, plan);
	}

	/**
	 * One physical bank layout for every search, single encounters and complete raid kits alike: the worn
	 * silhouette on the left, the carried inventory in its game layout ({@link RaidPlan#inventoryLayout}) on the
	 * right, then loading rows for pouch runes and loaded ammunition.
	 */
	private static Layout layoutFor(SetupResult setup, List<RaidPlan.InventoryItem> inventory,
		Map<Integer, GearItem> equipment, List<RaidPlan.InventoryItem> prepots, List<SetupResult> setups,
		Function<GearItem, List<Integer>> itemIds, RaidPlan plan)
	{
		Layout layout = new Layout();
		List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
		Loadout worn = setup.getLoadout();
		for (Slot slot : Slot.values()) { addItem(layout, itemIds, worn.get(slot), CELLS[slot.ordinal()]); }
		// The bank grid mirrors the inventory's physical switch cells, including empty cells in partial blocks.
		int loadingCell = EXTRA_ROWS_START;
		int prepotCell = 0;
		for (RaidPlan.InventoryItem item : prepots)
		{
			int cell = prepotCell < PREPOT_CELLS.length ? PREPOT_CELLS[prepotCell++] : loadingCell++;
			layout.add(item.getIds(), cell, BOOST);
			layout.prepotCells.add(cell);
			rankBoostDosesHighToLow(layout, item.getIds());
		}
		for (int i = 0; i < display.size(); i++)
		{
			RaidPlan.InventoryItem item = display.get(i);
			if (item == null) { continue; }
			GearItem gear = equipment.get(item.getIds().get(0));
			layout.add(gear == null ? item.getIds() : itemIds.apply(gear), inventoryCell(i), group(item));
			RaidPlan.PotionUse use = plan == null ? null : plan.potionUse(item);
			String drunk = use == null || use.getTag() == null ? "" : use.getDescription();
			if (item.getBottles() > 0)
			{
				String guidance = (item.isReusable() ? "Bring 1 " + item.getName() + " (reusable)"
					: "Bring " + item.getBottles() + " bottles (~" + item.getEstimatedDoses() + " doses)")
					+ (use == RaidPlan.PotionUse.BOTH ? " " + drunk : "");
				item.getIds().forEach(id -> layout.boostGuidance.put(id, guidance));
			}
			if (!drunk.isEmpty())
			{
				item.getIds().forEach(id ->
				{
					if (use == RaidPlan.PotionUse.BOTH || !layout.tags.containsKey(id)) { layout.tags.put(id, use); }
				});
				if (item.getBottles() == 0)
				{
					item.getIds().forEach(id -> layout.boostGuidance.putIfAbsent(id, drunk));
				}
			}
		}
		// Darts already loaded into blowpipes need bank withdrawal but occupy no carried switch slot.
		// Boosts only drunk before entering are tagged; carried prepots keep the more specific "both" guidance.
		for (RaidPlan.InventoryItem item : prepots)
		{
			item.getIds().forEach(id -> layout.tags.putIfAbsent(id, RaidPlan.PotionUse.PREPOT));
			item.getIds().forEach(id -> layout.boostGuidance.putIfAbsent(id, RaidPlan.PotionUse.PREPOT.getDescription()));
		}
		// Pouch contents are shown below the kit for loading; they are not extra carried slots. A single
		// encounter lists them beside its carried items, in display order; a raid kit in its packing order.
		for (RaidPlan.InventoryItem item : plan == null ? display : inventory)
		{
			if (item != null)
			{
				for (RaidPlan.InventoryItem rune : item.getContents()) { layout.add(rune.getIds(), loadingCell++, SPELL); }
			}
		}
		for (SetupResult member : setups)
		{
			addItem(layout, itemIds, member.getLoadout().getLoadedAmmo(), loadingCell++);
			for (com.bestgearsetup.calc.SpecialPlan special : member.getSpecials()) { addItem(layout, itemIds, special.getLoadedAmmo(), loadingCell++); }
		}
		return layout;
	}

	static int inventoryCell(int index) { return (index / 4) * 8 + 4 + index % 4; }

	private static int group(RaidPlan.InventoryItem item)
	{
		return item.getGroup() == RaidPlan.InventoryGroup.BOOSTS ? BOOST
			: item.getGroup() == RaidPlan.InventoryGroup.RUNES ? SPELL : GEAR;
	}

	private static void rankBoostDosesHighToLow(Layout layout, List<Integer> ids)
	{
		Map<Integer, List<Integer>> families = new LinkedHashMap<>();
		for (int id : ids)
		{
			families.computeIfAbsent(ItemVariationMapping.map(id), ignored -> new ArrayList<>()).add(id);
		}
		for (List<Integer> family : families.values())
		{
			Collections.reverse(family);
			for (int id : family)
			{
				layout.boostDoseRanks.put(id, layout.boostDoseRanks.size());
			}
		}
	}

	static Map<Integer, Integer> raidCellsFor(RaidPlan plan)
	{
		return Collections.unmodifiableMap(layoutForRaid(plan, BestGearSetupBankView::itemIds).cells);
	}

	static List<Integer> raidCellsFor(RaidPlan plan, int itemId)
	{
		Layout layout = layoutForRaid(plan, BestGearSetupBankView::itemIds);
		List<Integer> cells = new ArrayList<>();
		Integer first = layout.cells.get(itemId);
		if (first != null) { cells.add(first); }
		cells.addAll(layout.duplicateCells.getOrDefault(itemId, Collections.emptyList()));
		return Collections.unmodifiableList(cells);
	}

	static Map<Integer, Integer> cellsFor(SetupResult setup)
	{
		return cellsFor(setup, Collections.emptyList());
	}

	static Map<Integer, Integer> cellsFor(SetupResult setup, List<Potion> potions)
	{
		return cellsFor(setup, potions, id -> false);
	}

	static Map<Integer, Integer> cellsFor(SetupResult setup, List<Potion> potions, IntPredicate owns)
	{
		return Collections.unmodifiableMap(layoutFor(setup, potions, BestGearSetupBankView::itemIds, owns).cells);
	}

	static Layout layoutFor(SetupResult setup, List<Potion> potions, Function<GearItem, List<Integer>> itemIds,
		IntPredicate owns)
	{
		return setup == null ? Layout.EMPTY : layoutFor(setup, RaidPlan.inventoryForSetup(setup, potions, owns),
			RaidPlan.gear(setup), Collections.emptyList(), Collections.singletonList(setup), itemIds, null);
	}

	private static void addItem(Layout layout, Function<GearItem, List<Integer>> itemIds, GearItem item, int cell)
	{
		if (item != null)
		{
			layout.add(itemIds.apply(item), cell, GEAR);
		}
	}

	/** The item, then its equivalent charge states. */
	static List<Integer> itemIds(GearItem item)
	{
		List<Integer> ids = new ArrayList<>();
		ids.add(item.getId());
		ids.addAll(item.getOwnershipVariants());
		return ids;
	}

	/**
	 * Each matching item's cell, extra-row group (gear, spell or boost) and rank, and the items that take their
	 * cell ahead of any alternative. Built once in {@link #layoutFor}, then only read.
	 */
	static final class Layout
	{
		static final Layout EMPTY = new Layout();
		private final Map<Integer, Integer> cells = new HashMap<>();
		private final Map<Integer, Integer> groups = new HashMap<>();
		/** Insertion order: a cell's own item, then its alternatives (doses by type, substitutes) in order. */
		private final Map<Integer, Integer> ranks = new HashMap<>();
		/** Dose order for extra boost stacks; distinct from cell priority, which may prefer low-dose prepots. */
		private final Map<Integer, Integer> boostDoseRanks = new HashMap<>();
		private final Set<Integer> primary = new HashSet<>();
		/** Repeated item IDs at additional cells; the ID order within a cell controls which available dose is shown. */
		private final Map<Integer, List<Integer>> duplicateCells = new LinkedHashMap<>();
		private final Map<Integer, List<Integer>> duplicateCandidates = new LinkedHashMap<>();
		private final Set<Integer> prepotCells = new HashSet<>();
		private final Map<Integer, String> boostGuidance = new HashMap<>();
		/** When a raid boost is drunk (prepot, brought or both), tagged on its bank stack. */
		private final Map<Integer, RaidPlan.PotionUse> tags = new HashMap<>();

		/** The first id is the cell's own item; the rest (variants, doses, substitutes) only fill it in its absence. */
		private void add(Collection<Integer> ids, int cell, int group)
		{
			boolean first = true;
			for (int id : ids)
			{
				Integer existing = cells.putIfAbsent(id, cell);
				if (existing == null)
				{
					groups.put(id, group);
					ranks.put(id, ranks.size());
					if (first)
					{
						primary.add(id);
					}
				}
				else if (existing != cell && group == BOOST && groups.get(id) == BOOST)
				{
					List<Integer> cells = duplicateCells.computeIfAbsent(id, ignored -> new ArrayList<>());
					if (!cells.contains(cell)) { cells.add(cell); }
					List<Integer> candidates = duplicateCandidates.computeIfAbsent(cell, ignored -> new ArrayList<>());
					if (!candidates.contains(id)) { candidates.add(id); }
				}
				first = false;
			}
		}

		boolean isEmpty()
		{
			return cells.isEmpty();
		}
	}

	/** Whether the potion store is open; it must be closed by the player before the layout can show. */
	boolean isPotionStoreOpen()
	{
		return client.getVarbitValue(VarbitID.BANK_CURRENTTAB) == BANKTAB_POTIONSTORE;
	}

	/** Client thread: switch from any bank tab/search into a temporary equipment tab. */
	boolean show()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS);
		// The store is closed server-side by its button; plugins may not click it, and leaving it open behind
		// the layout would stop deposits working (as in Bank Tags).
		if (bank == null || bank.isHidden() || isPotionStoreOpen() || bank.getOnInvTransmitListener() == null
			|| selection.isEmpty())
		{
			return false;
		}
		clear();
		releaseOtherViews();
		active = true;
		bank.setScrollY(0);
		client.setVarcIntValue(VarClientID.BANK_SCROLLPOS, 0);
		bankSearch.reset(true);
		return true;
	}

	/** Release any Bank Tags or Quest Helper tab so they cannot override this layout. */
	private void releaseOtherViews()
	{
		client.setVarbit(VarbitID.BANK_CURRENTTAB, 0);
		// Bank Tags and Quest Helper both release their tab when the bank search is toggled, so announce one
		// without opening search.
		eventBus.post(new ScriptPreFired(ScriptID.BANKMAIN_SEARCH_TOGGLE));
	}

	/** Client thread: update an existing setup search, without replacing the user's own search. */
	void refreshSelection()
	{
		if (selection.isEmpty())
		{
			clear();
		}
		else if (isActive())
		{
			bankSearch.layoutBank();
		}
	}

	/** Client thread: release our view and restore normal bank search and dragging. */
	void clear()
	{
		boolean reset = isActive();
		active = false;
		reopen = false;
		resetDuplicateWidgets();
		restoreDragging();
		if (reset)
		{
			bankSearch.layoutBank();
		}
	}

	boolean hasSelection()
	{
		return !selection.isEmpty();
	}

	boolean isActive()
	{
		return active;
	}

	/** Guidance over a real bank stack; its native name, quantity and withdrawal actions remain untouched. */
	String boostGuidance(int itemId)
	{
		if (!isActive()) { return null; }
		Integer id = key(selection, itemId);
		return id == null ? null : selection.boostGuidance.get(id);
	}

	/** How the selected raid kit gets this bank stack's boost, for its tag; null for other items or no raid selection. */
	RaidPlan.PotionUse potionUse(int itemId)
	{
		Layout layout = selection;
		if (layout.tags.isEmpty()) { return null; }
		Integer id = key(layout, itemId);
		return id == null ? null : layout.tags.get(id);
	}

	/** {@link #potionUse} for tests, without an item manager to canonicalize ids. */
	static Map<Integer, RaidPlan.PotionUse> raidTagsFor(RaidPlan plan)
	{
		return Collections.unmodifiableMap(layoutForRaid(plan, BestGearSetupBankView::itemIds).tags);
	}

	// Run after the other bank views so this temporary tab owns its results while selected.
	@Subscribe(priority = -1)
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!isActive() || client.getWidget(InterfaceID.Bankmain.ITEMS) == null)
		{
			return;
		}
		if ("bankSearchFilter".equals(event.getEventName()))
		{
			int[] ints = client.getIntStack();
			int size = client.getIntStackSize();
			int id = ints[size - 1];
			// Placeholders match through their canonical item, so a withdrawn item keeps its cell.
			ints[size - 2] = id > 0 && key(selection, id) != null ? 1 : 0;
		}
		else if ("bankBuildTab".equals(event.getEventName())
			|| "getSearchingTagTab".equals(event.getEventName()))
		{
			client.getIntStack()[client.getIntStackSize() - 1] = 1;
		}
	}

	/** The layout id this bank item matches, directly or through its canonical item; null when none. */
	private Integer key(Layout layout, int id)
	{
		if (layout.cells.containsKey(id))
		{
			return id;
		}
		int canonical = itemManager.canonicalize(id);
		if (layout.cells.containsKey(canonical))
		{
			return canonical;
		}
		return null;
	}

	@Subscribe(priority = -1)
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_SEARCH_TOGGLE)
		{
			// Let a normal search open on its first click, without rebuilding inside the script VM.
			active = false;
			resetDuplicateWidgets();
			restoreDragging();
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_BUILD)
		{
			resetDuplicateWidgets();
			restoreDragging();
			if (client.getVarbitValue(VarbitID.BANK_CURRENTTAB) != 0
				|| client.getVarcIntValue(VarClientID.MESLAYERMODE) == InputType.SEARCH.getType())
			{
				active = false;
			}
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING && isActive())
		{
			int height = arrangeItems();
			if (height >= 0)
			{
				// Finishbuilding receives the scroll height as int13 (nine from the end).
				// Update it before the game configures its scrollbar and scroll listeners.
				client.getIntStack()[client.getIntStackSize() - 9] = height;
			}
		}
	}

	@Subscribe(priority = -1)
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING)
		{
			return;
		}
		if (reopen)
		{
			// The reopened bank has built its own tab; switch back outside the script VM. A potion store
			// left open blocks this, as it does the button.
			reopen = false;
			clientThread.invokeLater(() -> { show(); });
		}
		if (!isActive())
		{
			return;
		}
		Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		if (title != null)
		{
			title.setText("Best Gear Setup - equipment layout");
		}
	}

	private int arrangeItems()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (bank == null || bank.getChildren() == null)
		{
			return -1;
		}
		Layout layout = selection;
		Set<Integer> occupied = new HashSet<>();
		Map<Widget, Integer> matches = new IdentityHashMap<>();
		Map<Integer, Widget> placedByCell = new HashMap<>();
		Map<Integer, Widget> sources = new HashMap<>();
		resetDuplicateWidgets();
		for (Widget item : bank.getChildren())
		{
			if (item == null || item.isSelfHidden() || duplicateWidgets.contains(item))
			{
				continue;
			}
			Integer key = item.getItemId() <= 0 ? null : key(layout, item.getItemId());
			if (key == null)
			{
				// This also hides the tab headings/separators in the bank's scroll container.
				item.setHidden(true);
			}
			else
			{
				matches.put(item, key);
				if (!isPlaceholder(item)) { sources.putIfAbsent(key, item); }
			}
		}
		// Each cell's own item claims it first; the best-ranked alternative fills it in its absence (the
		// highest dose, the first substitute) and the rest join the extra rows in rank order. Placeholders
		// (quantity 0) come after every real item and only fill a cell nothing withdrawable can.
		List<Widget> ordered = new ArrayList<>(matches.keySet());
		ordered.sort(Comparator.comparing(BestGearSetupBankView::isPlaceholder)
			.thenComparing((Widget w) -> !layout.primary.contains(matches.get(w)))
			.thenComparingInt(w -> layout.ranks.get(matches.get(w)))
			.thenComparingInt(Widget::getOriginalY).thenComparingInt(Widget::getOriginalX));
		List<List<Widget>> extras = Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		int lastCell = EXTRA_ROWS_START - 1;
		for (Widget item : ordered)
		{
			int key = matches.get(item);
			int cell = layout.cells.get(key);
			if (occupied.add(cell))
			{
				place(item, cell);
				placedByCell.put(cell, item);
				lastCell = Math.max(lastCell, cell);
			}
			else if (isPlaceholder(item))
			{
				// A spare placeholder (e.g. another dose) would only clutter the extra rows.
				item.setHidden(true);
			}
			else
			{
				extras.get(layout.groups.get(key)).add(item);
			}
		}
		Iterator<Widget> spares = spareSlots(bank).iterator();
		for (int cell : layout.prepotCells)
		{
			Widget source = placedByCell.get(cell);
			if (source == null || isPlaceholder(source)) { continue; }
			if (!spares.hasNext()) { break; }
			int id = matches.get(source);
			Widget duplicate = spares.next();
			configureDuplicateWidget(duplicate, source, cell);
			matches.put(duplicate, id);
			extras.get(BOOST).add(duplicate);
		}
		// Extra rows start below every placed cell; gear, spell items and boosts each begin a new row,
		// and so does each potion type (every dose of one potion together, highest first).
		int next = Math.max(EXTRA_ROWS_START, (lastCell / 8 + 1) * 8);
		Map<Integer, List<Widget>> boostsByType = new LinkedHashMap<>();
		for (Widget item : extras.get(BOOST))
		{
			int itemType = ItemVariationMapping.map(matches.get(item));
			boostsByType.computeIfAbsent(itemType, ignored -> new ArrayList<>()).add(item);
		}
		for (List<Widget> boosts : boostsByType.values())
		{
			// Candidate priority can be low-dose-first for prepots; bank extras always display high-dose-first.
			boosts.sort(Comparator.comparingInt(item -> layout.boostDoseRanks
				.getOrDefault(matches.get(item), layout.ranks.get(matches.get(item)))));
		}
		for (int group : new int[]{GEAR, BOOST, SPELL})
		{
			if (group == BOOST)
			{
				for (List<Widget> boosts : boostsByType.values())
				{
					for (Widget item : boosts)
					{
						place(item, next);
						lastCell = next++;
					}
					next = (next + 7) / 8 * 8;
				}
			}
			else
			{
				for (Widget item : extras.get(group))
				{
					place(item, next);
					lastCell = next++;
				}
				next = (next + 7) / 8 * 8;
			}
		}
		for (Map.Entry<Integer, List<Integer>> entry : layout.duplicateCandidates.entrySet())
		{
			int cell = entry.getKey();
			if (!occupied.add(cell)) { continue; }
			for (int id : entry.getValue())
			{
				Widget source = sources.get(id);
				if (source == null || !spares.hasNext()) { continue; }
				configureDuplicateWidget(spares.next(), source, cell);
				lastCell = Math.max(lastCell, cell);
				break;
			}
		}
		bank.setScrollHeight(lastCell < 0 ? 0 : (lastCell / 8 + 1) * 36);
		bank.setScrollY(Math.max(0, Math.min(bank.getScrollY(), bank.getScrollHeight() - bank.getHeight())));
		client.setVarcIntValue(VarClientID.BANK_SCROLLPOS, bank.getScrollY());
		return bank.getScrollHeight();
	}

	/**
	 * The bank's unused item slots after its real stacks. Like Bank Tags layouts, repeated entries are drawn in
	 * these game-built slots so the game builds their menus itself; plugin-created widgets have no server ops.
	 */
	private List<Widget> spareSlots(Widget bank)
	{
		ItemContainer items = client.getItemContainer(InventoryID.BANK);
		Widget[] children = bank.getChildren();
		List<Widget> spares = new ArrayList<>();
		if (items == null || children == null) { return spares; }
		for (int i = items.size(); i < children.length; i++)
		{
			Widget slot = children[i];
			// Tab headings and separators follow the item slots.
			if (slot == null || slot.getOriginalHeight() != BankTagsPlugin.BANK_ITEM_HEIGHT) { break; }
			spares.add(slot);
		}
		return spares;
	}

	private void configureDuplicateWidget(Widget duplicate, Widget source, int cell)
	{
		duplicate.setOriginalWidth(source.getOriginalWidth());
		duplicate.setItemId(source.getItemId());
		duplicate.setItemQuantity(source.getItemQuantity());
		duplicate.setItemQuantityMode(source.getItemQuantityMode());
		duplicate.setName(source.getName());
		duplicate.setOpacity(source.getOpacity());
		duplicate.clearActions();
		String[] actions = source.getActions();
		if (actions != null)
		{
			for (int i = actions.length - 1; i >= 0; i--)
			{
				if (actions[i] != null) { duplicate.setAction(i, actions[i]); }
			}
		}
		duplicateWidgets.add(duplicate);
		duplicateSources.put(duplicate.getIndex(), source);
		place(duplicate, cell);
		duplicate.setHidden(false);
	}

	private void resetDuplicateWidgets()
	{
		duplicateSources.clear();
		for (Widget duplicate : duplicateWidgets)
		{
			duplicate.setHidden(true);
			duplicate.setItemId(-1);
			duplicate.setItemQuantity(0);
			duplicate.clearActions();
		}
		duplicateWidgets.clear();
	}

	private static boolean isPlaceholder(Widget item)
	{
		return item.getItemQuantity() <= 0;
	}

	private void place(Widget item, int cell)
	{
		// Preserve each real widget's index and withdrawal actions; only change its display position.
		// The scroll widget can be wider than its clipped viewport (e.g. stretched mode).
		// Match RuneLite's bank layout spacing: 36px items with 12px horizontal padding.
		item.setOriginalX(51 + (cell % 8) * 48);
		item.setOriginalY((cell / 8) * 36);
		item.revalidate();
		dragSettings.putIfAbsent(item, new int[]{item.getDragDeadZone(), item.getDragDeadTime()});
		// This is a temporary display layout, so dragging must not rearrange actual bank slots.
		item.setDragDeadZone(Integer.MAX_VALUE);
		item.setDragDeadTime(Integer.MAX_VALUE);
	}

	private void restoreDragging()
	{
		for (Map.Entry<Widget, int[]> entry : dragSettings.entrySet())
		{
			entry.getKey().setDragDeadZone(entry.getValue()[0]);
			entry.getKey().setDragDeadTime(entry.getValue()[1]);
		}
		dragSettings.clear();
	}

	@Subscribe(priority = 1)
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// A repeated entry's game-built op names its spare slot; point it at the real stack, as Bank Tags does.
		Widget source = isActive() && event.getParam1() == InterfaceID.Bankmain.ITEMS
			? duplicateSources.get(event.getParam0()) : null;
		if (source != null)
		{
			event.getMenuEntry().setParam0(source.getIndex());
			return;
		}
		String option = event.getMenuOption();
		if (option.startsWith("View tab") || option.startsWith("View tag tab")
			|| option.equals("View all items") || option.startsWith("Potion store"))
		{
			active = false;
			resetDuplicateWidgets();
			restoreDragging();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && event.isUnload())
		{
			reopen = active;
			active = false;
			restoreDragging();
			duplicateWidgets.clear();
			duplicateSources.clear();
		}
	}
}
