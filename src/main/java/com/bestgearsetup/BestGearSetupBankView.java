package com.bestgearsetup;

import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SpecialPlan;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.ScriptID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.plugins.bank.BankSearch;

/** Temporary equipment tab; never changes the bank's item slots or opens a search input. */
@Singleton
class BestGearSetupBankView
{
	// Equipment silhouette on the bank's eight-column grid, matching the sidebar.
	private static final int[] CELLS = {1, 8, 9, 10, 16, 17, 18, 25, 32, 33, 34};
	// Right of the silhouette: spell runes (and a thrall's Book of the dead), then potions and hearts.
	private static final int[] SPELL_CELLS = {4, 5, 6, 7, 12, 13, 14, 15};
	private static final int[] BOOST_CELLS = {20, 21, 22, 23, 28, 29, 30, 31};
	// Below the runes and boosts: a spec weapon and off-hand to switch to, then its own ammunition or a spec
	// blowpipe's darts (never both).
	private static final int SPEC_WEAPON_CELL = 36;
	private static final int SPEC_OFFHAND_CELL = 37;
	private static final int SPEC_AMMO_CELL = 38;
	// Extra matches go below the layout, each group starting its own row: gear, then spell items, then boosts.
	private static final int EXTRA_ROWS_START = 40;
	private static final int GEAR = 0;
	private static final int SPELL = 1;
	private static final int BOOST = 2;
	private static final int BANKTAB_POTIONSTORE = 15;
	private final Client client;
	private final ItemManager itemManager;
	private final BankSearch bankSearch;
	private final EventBus eventBus;
	private volatile Layout selection = Layout.EMPTY;
	private final Map<Widget, int[]> dragSettings = new IdentityHashMap<>();
	private boolean active;

	@Inject
	BestGearSetupBankView(Client client, ItemManager itemManager, BankSearch bankSearch, EventBus eventBus)
	{
		this.client = client;
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
		selection = layoutFor(setup, potions);
	}

	static Map<Integer, Integer> cellsFor(SetupResult setup)
	{
		return cellsFor(setup, Collections.emptyList());
	}

	static Map<Integer, Integer> cellsFor(SetupResult setup, List<Potion> potions)
	{
		return Collections.unmodifiableMap(layoutFor(setup, potions).cells);
	}

	static Layout layoutFor(SetupResult setup, List<Potion> potions)
	{
		Layout layout = new Layout();
		if (setup != null)
		{
			Loadout loadout = setup.getLoadout();
			for (Slot slot : Slot.values())
			{
				addItem(layout, loadout.get(slot), CELLS[slot.ordinal()]);
			}
			addItem(layout, loadout.getLoadedAmmo(), loadout.get(Slot.AMMO) == null ? 10 : 40);
			SpecialPlan special = setup.getSpecial();
			if (special != null && special.isSwitch(loadout))
			{
				addItem(layout, special.getWeapon(), SPEC_WEAPON_CELL);
				addItem(layout, special.getOffhand(), SPEC_OFFHAND_CELL);
				addItem(layout, special.getAmmo(), SPEC_AMMO_CELL);
				addItem(layout, special.getLoadedAmmo(), SPEC_AMMO_CELL);
			}
			List<Integer> spellItems = SpellRunes.required(setup);
			for (int i = 0; i < spellItems.size(); i++)
			{
				List<Integer> ids = new ArrayList<>();
				ids.add(spellItems.get(i));
				ids.addAll(SpellRunes.substitutes(spellItems.get(i)));
				layout.add(ids, reservedCell(SPELL_CELLS, i), SPELL);
			}
			int cell = 0;
			for (Potion potion : potions)
			{
				// Raid supplies are handed out inside the raid, never withdrawn.
				if (!potion.isRaidSupply())
				{
					// Each dose of the potion, then its divine version and potions giving the same boost.
					layout.add(BoostAccess.itemIds(potion), reservedCell(BOOST_CELLS, cell++), BOOST);
				}
			}
		}
		return layout;
	}

	private static int reservedCell(int[] reserved, int index)
	{
		// Any beyond the reserved cells join the extra rows below the silhouette.
		return index < reserved.length ? reserved[index] : EXTRA_ROWS_START + index;
	}

	private static void addItem(Layout layout, GearItem item, int cell)
	{
		if (item != null)
		{
			List<Integer> ids = new ArrayList<>();
			ids.add(item.getId());
			ids.addAll(item.getOwnershipVariants());
			layout.add(ids, cell, GEAR);
		}
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

	/** Client thread: switch from any bank tab/search into a temporary equipment tab. */
	boolean show()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS);
		boolean potionStore = client.getVarbitValue(VarbitID.BANK_CURRENTTAB) == BANKTAB_POTIONSTORE;
		if (bank == null || (bank.isHidden() && !potionStore) || bank.getOnInvTransmitListener() == null
			|| selection.isEmpty())
		{
			return false;
		}
		clear();
		releaseOtherViews(potionStore);
		active = true;
		bank.setScrollY(0);
		client.setVarcIntValue(VarClientID.BANK_SCROLLPOS, 0);
		bankSearch.reset(true);
		return true;
	}

	/** Close the potion store and any Bank Tags or Quest Helper tab so they cannot override this layout. */
	private void releaseOtherViews(boolean potionStore)
	{
		if (potionStore)
		{
			// Leaving the store open behind the layout would stop deposits working (as in Bank Tags).
			client.menuAction(-1, InterfaceID.Bankmain.POTIONSTORE_BUTTON, MenuAction.CC_OP, 1, -1, "Potion store", "");
		}
		client.menuAction(0, InterfaceID.Bankmain.TABS, MenuAction.CC_OP, 1, -1, "View all items", "");
		client.setVarbit(VarbitID.BANK_CURRENTTAB, 0);
		// A scripted menu action doesn't reach the other views' click handlers. Bank Tags and Quest Helper
		// both release their tab when the bank search is toggled, so announce one without opening search.
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
		// (quantity 0) come after every real item and only fill a cell nothing withdrawable can.
		List<Widget> ordered = new ArrayList<>(matches.keySet());
		ordered.sort(Comparator.comparing(BestGearSetupBankView::isPlaceholder)
			.thenComparing((Widget w) -> !layout.primary.contains(matches.get(w)))
			.thenComparingInt(w -> layout.ranks.get(matches.get(w)))
			.thenComparingInt(Widget::getOriginalY).thenComparingInt(Widget::getOriginalX));
		List<List<Widget>> extras = Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		int lastCell = 34;
		for (Widget item : ordered)
		{
			int key = matches.get(item);
			int cell = layout.cells.get(key);
			if (occupied.add(cell))
			{
				place(item, cell);
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
		// Extra rows start below every placed cell; gear, spell items and boosts each begin a new row,
		// and so does each potion type (every dose of one potion together, highest first).
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
		bank.setScrollHeight(lastCell < 0 ? 0 : (lastCell / 8 + 1) * 36);
		bank.setScrollY(Math.max(0, Math.min(bank.getScrollY(), bank.getScrollHeight() - bank.getHeight())));
		client.setVarcIntValue(VarClientID.BANK_SCROLLPOS, bank.getScrollY());
		return bank.getScrollHeight();
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
