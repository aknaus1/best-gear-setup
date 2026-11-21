package com.bestgearsetup;

import com.bestgearsetup.calc.*;
import com.bestgearsetup.data.*;
import java.util.*;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
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
	// Ammunition loaded into a weapon (a blowpipe's darts), then a spec weapon's, below the silhouette.
	private static final int LOADED_CELL = 40;
	// The carried inventory fills the 4×7 grid right of the silhouette; a rune pouch's runes sit under it.
	private static final int POUCH_ROW = 7 * 8 + 4;
	private static final int POUCH_RUNES = 3;
	// Extra matches go below the layout, each group starting its own row: gear, spell items, boosts, then food.
	private static final int EXTRA_ROWS_START = 8 * 8;
	/** A repeated entry's opacity, fainter than the stack it repeats. */
	private static final int COPY_OPACITY = 100;
	private static final int GEAR = 0;
	private static final int SPELL = 1;
	private static final int BOOST = 2;
	private static final int FOOD = 3;
	private static final int BANKTAB_POTIONSTORE = 15;
	private final Client client;
	private final ItemManager itemManager;
	private final BankSearch bankSearch;
	private final EventBus eventBus;
	private volatile Layout selection = Layout.EMPTY;
	private final Map<Widget, int[]> dragSettings = new IdentityHashMap<>();
	/** Spare bank slots showing a repeated entry, with the real stack each repeats. */
	private final Map<Widget, Widget> copies = new IdentityHashMap<>();
	private boolean active;

	@Inject
	BestGearSetupBankView(Client client, ItemManager itemManager, BankSearch bankSearch, EventBus eventBus)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.bankSearch = bankSearch;
		this.eventBus = eventBus;
	}

	/**
	 * Publish a selection from the sidebar; the bank reads one immutable snapshot per rebuild. Each item's ids are in
	 * the order they may take its cell; a trip's restores, cure and food follow the boosts, whose bottles already
	 * have their boost's cell.
	 */
	void setSelection(Setup setup, List<Potion> potions, Function<Gear, List<Integer>> itemIds,
		List<TripSupplies.Supply> supplies)
	{
		selection = layoutFor(setup, potions, itemIds, supplies);
	}

	static Map<Integer, Integer> cellsFor(Setup setup, List<Potion> potions, List<TripSupplies.Supply> supplies)
	{
		return Collections.unmodifiableMap(layoutFor(setup, potions, BestGearSetupBankView::itemIds, supplies).cells);
	}

	static Layout layoutFor(Setup setup, List<Potion> potions, Function<Gear, List<Integer>> itemIds,
		List<TripSupplies.Supply> supplies)
	{
		Layout layout = new Layout();
		if (setup != null)
		{
			Outfit loadout = setup.getLoadout();
			for (Slot slot : Slot.values())
			{
				addItem(layout, itemIds, loadout.get(slot), CELLS[slot.ordinal()]);
			}
			addItem(layout, itemIds, loadout.getLoadedAmmo(), loadout.get(Slot.AMMO) == null ? 10 : LOADED_CELL);
			// The inventory is packed in the slots TripSupplies.usedSlots counts: the spec switch, the rune pouch
			// and runes beyond its three, each bottle, then food.
			int slot = 0;
			SpecialPlan special = setup.getSpecial();
			if (special != null && special.isSwitch(loadout))
			{
				if (special.getWeapon() != loadout.getWeapon())
					slot = carry(layout, itemIds, special.getWeapon(), slot);
				slot = carry(layout, itemIds, special.getOffhand(), slot);
				slot = carry(layout, itemIds, special.getAmmo(), slot);
				addItem(layout, itemIds, special.getLoadedAmmo(), LOADED_CELL + 1);
				// The worn shield a two-handed spec weapon takes off needs a slot, left empty at the bank.
				if (TripSupplies.takesShieldOff(setup))
					slot++;
			}
			List<Integer> spellItems = SpellRunes.required(setup);
			if (!spellItems.isEmpty())
				slot = layout.carry(SpellRunes.POUCHES, slot, 1, SPELL);
			for (int i = 0; i < spellItems.size(); i++)
			{
				List<Integer> ids = new ArrayList<>();
				ids.add(spellItems.get(i));
				ids.addAll(SpellRunes.substitutes(spellItems.get(i)));
				if (i < POUCH_RUNES)
					layout.add(ids, POUCH_ROW + i, SPELL);
				else
					slot = layout.carry(ids, slot, 1, SPELL);
			}
			for (Potion potion : potions)
			{
				// Raid supplies are handed out inside the raid, never withdrawn.
				if (!potion.isRaidSupply())
				{
					// Each dose of the potion, then its divine version and potions giving the same boost; as many
					// bottles as the trip needs (one without a trip).
					List<Integer> ids = new ArrayList<>(BoostAccess.itemIds(potion));
					int count = supplies.stream().filter(s -> s.getIds().get(0).equals(ids.get(0)))
						.mapToInt(TripSupplies.Supply::getCount).findFirst().orElse(1);
					slot = layout.carry(ids, slot, count, BOOST);
				}
			}
			// A food's other owned foods only fill its cell when it's missing, otherwise they share one row under the
			// boost rows.
			for (TripSupplies.Supply supply : supplies)
				if (!layout.cells.containsKey(supply.getIds().get(0)))
					slot = layout.carry(supply.getIds(), slot, supply.getCount(),
						TripSupplies.isFood(supply.getIds().get(0)) ? FOOD : BOOST);
		}
		return layout;
	}

	/** The bank cell of inventory slot {@code slot} (4 columns right of the silhouette); past 28, the extra rows. */
	static int inventoryCell(int slot)
	{
		return slot < TripSupplies.INVENTORY_SLOTS ? slot / 4 * 8 + 4 + slot % 4
			: EXTRA_ROWS_START + slot - TripSupplies.INVENTORY_SLOTS;
	}

	private static int carry(Layout layout, Function<Gear, List<Integer>> itemIds, Gear item, int slot)
	{
		return item == null ? slot : layout.carry(itemIds.apply(item), slot, 1, GEAR);
	}

	private static void addItem(Layout layout, Function<Gear, List<Integer>> itemIds, Gear item, int cell)
	{
		if (item != null)
		{
			layout.add(itemIds.apply(item), cell, GEAR);
		}
	}

	/** The item, then its equivalent charge states. */
	static List<Integer> itemIds(Gear item)
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
		private final Set<Integer> primary = new HashSet<>();
		/** Inventory cells repeating another cell's item (a trip's second bottle, the rest of its food), in order. */
		private final Map<Integer, Integer> repeats = new LinkedHashMap<>();

		/** {@code count} of an item from inventory slot {@code slot}, its copies only within the 28; the next slot. */
		private int carry(List<Integer> ids, int slot, int count, int group)
		{
			int cell = inventoryCell(slot);
			add(ids, cell, group);
			for (int i = 1; i < count && slot + i < TripSupplies.INVENTORY_SLOTS; i++)
				repeats.put(inventoryCell(slot + i), cell);
			return slot + count;
		}

		/** The first id is the cell's own item; the rest (variants, doses, substitutes) only fill it in its absence. */
		private void add(Collection<Integer> ids, int cell, int group)
		{
			boolean first = true;
			for (int id : ids)
			{
				if (cells.putIfAbsent(id, cell) == null)
				{
					groups.put(id, group);
					ranks.put(id, ranks.size());
					if (first)
					{
						primary.add(id);
					}
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
		return layout.cells.containsKey(canonical) ? canonical : null;
	}

	@Subscribe(priority = -1)
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_SEARCH_TOGGLE)
		{
			// Let a normal search open on its first click, without rebuilding inside the script VM.
			active = false;
			restoreDragging();
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_BUILD)
		{
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
		if (event.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING || !isActive())
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
		for (Widget item : bank.getChildren())
		{
			if (item == null || item.isSelfHidden())
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
			}
		}
		// Each cell's own item claims it first; the best-ranked alternative fills it in its absence (the
		// highest dose, the first substitute) and the rest join the extra rows in rank order. Placeholders
		// (quantity 0) come after every real item and only fill a cell nothing withdrawable can, except a
		// spell rune's own placeholder: the bank can't see a rune pouch, so it counts as owning the rune
		// and keeps its cell ahead of combination runes.
		List<Widget> ordered = new ArrayList<>(matches.keySet());
		ordered.sort(Comparator.comparing((Widget w) -> isPlaceholder(w)
				&& !(layout.groups.get(matches.get(w)) == SPELL && layout.primary.contains(matches.get(w))))
			.thenComparing((Widget w) -> !layout.primary.contains(matches.get(w)))
			.thenComparingInt(w -> layout.ranks.get(matches.get(w)))
			.thenComparingInt(Widget::getOriginalY).thenComparingInt(Widget::getOriginalX));
		List<List<Widget>> extras = Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		Map<Integer, Widget> placed = new HashMap<>();
		int lastCell = 34;
		for (Widget item : ordered)
		{
			int key = matches.get(item);
			int cell = layout.cells.get(key);
			if (occupied.add(cell))
			{
				place(item, cell);
				placed.put(cell, item);
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
		// Extra rows start below every placed cell; gear, spell items, boosts and food each begin a new row,
		// and so does each potion type (every dose of one potion together, highest first). Spare foods share one row.
		int next = Math.max(EXTRA_ROWS_START, (lastCell / 8 + 1) * 8);
		for (int group = 0; group < extras.size(); group++)
		{
			Integer type = null;
			for (Widget item : extras.get(group))
			{
				int itemType = ItemVariationMapping.map(matches.get(item));
				if (group == BOOST && type != null && type != itemType)
				{
					next = (next + 7) / 8 * 8;
				}
				type = itemType;
				place(item, next);
				lastCell = next++;
			}
			next = (next + 7) / 8 * 8;
		}
		// Like Bank Tags layouts, an item carried more than once is repeated in the bank's unused slots after its
		// real stacks: the game builds their menus, and a click is pointed at the real stack. A full bank has none.
		Iterator<Widget> spares = spareSlots(bank).iterator();
		for (Map.Entry<Integer, Integer> repeat : layout.repeats.entrySet())
		{
			Widget source = placed.get(repeat.getValue());
			if (source != null && !isPlaceholder(source) && spares.hasNext())
			{
				copy(spares.next(), source, repeat.getKey());
				lastCell = Math.max(lastCell, repeat.getKey());
			}
		}
		bank.setScrollHeight(lastCell < 0 ? 0 : (lastCell / 8 + 1) * 36);
		bank.setScrollY(Math.max(0, Math.min(bank.getScrollY(), bank.getScrollHeight() - bank.getHeight())));
		client.setVarcIntValue(VarClientID.BANK_SCROLLPOS, bank.getScrollY());
		return bank.getScrollHeight();
	}

	/** The bank's unused item slots, after its real stacks and before the tab headings. */
	private List<Widget> spareSlots(Widget bank)
	{
		ItemContainer items = client.getItemContainer(InventoryID.BANK);
		Widget[] children = bank.getChildren();
		List<Widget> spares = new ArrayList<>();
		for (int i = items == null ? children.length : items.size(); i < children.length; i++)
		{
			if (children[i] == null || children[i].getOriginalHeight() != BankTagsPlugin.BANK_ITEM_HEIGHT)
				break;
			spares.add(children[i]);
		}
		return spares;
	}

	private void copy(Widget copy, Widget source, int cell)
	{
		copy.setOriginalWidth(source.getOriginalWidth());
		copy.setItemId(source.getItemId());
		copy.setItemQuantity(source.getItemQuantity());
		copy.setItemQuantityMode(source.getItemQuantityMode());
		copy.setName(source.getName());
		copy.setOpacity(COPY_OPACITY);
		copy.clearActions();
		String[] actions = source.getActions();
		for (int i = actions == null ? -1 : actions.length - 1; i >= 0; i--)
			if (actions[i] != null)
				copy.setAction(i, actions[i]);
		copies.put(copy, source);
		place(copy, cell);
		copy.setHidden(false);
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

	/** Restores dragging and empties the spare slots showing repeated entries. */
	private void restoreDragging()
	{
		for (Map.Entry<Widget, int[]> entry : dragSettings.entrySet())
		{
			entry.getKey().setDragDeadZone(entry.getValue()[0]);
			entry.getKey().setDragDeadTime(entry.getValue()[1]);
		}
		dragSettings.clear();
		for (Widget copy : copies.keySet())
		{
			copy.setHidden(true);
			copy.setItemId(-1);
			copy.setItemQuantity(0);
			copy.clearActions();
		}
		copies.clear();
	}

	@Subscribe(priority = 1)
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// A repeated entry's game-built op names its spare slot; point it at the real stack, as Bank Tags does.
		if (isActive() && event.getParam1() == InterfaceID.Bankmain.ITEMS)
		{
			for (Map.Entry<Widget, Widget> copy : copies.entrySet())
			{
				if (copy.getKey().getIndex() == event.getParam0())
				{
					event.getMenuEntry().setParam0(copy.getValue().getIndex());
					return;
				}
			}
		}
		String option = event.getMenuOption();
		if (option.startsWith("View tab") || option.startsWith("View tag tab")
			|| option.equals("View all items") || option.startsWith("Potion store"))
		{
			active = false;
			restoreDragging();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && event.isUnload())
		{
			active = false;
			restoreDragging();
		}
	}
}
