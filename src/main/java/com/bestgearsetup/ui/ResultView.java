package com.bestgearsetup.ui;

import com.bestgearsetup.*;
import com.bestgearsetup.calc.*;
import com.bestgearsetup.data.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Search result: one tab per attack type (showing that style's best weapon), the worn
 * equipment grid, a stats box, the special attack used during the kill, a cost box ("total - owned = to buy"), the attack style with the
 * prayers and potions used, and the other setups found for the selected tab.
 */
class ResultView extends JPanel
{
	/** Each result box has its own edge: blue statistics, orange special attack, green cost. */
	/** Whether the statistics box shows more than its DPS line; closed until opened, then kept for the session. */
	static boolean statsOpen;
	private static final Color YELLOW = new Color(0xFF, 0xD2, 0x4D);

	/** One named spell sprite per thrall type; the tooltip names the tier. */
	private static final Map<Thrall.Type, Integer> THRALL_SPRITES = new EnumMap<>(Thrall.Type.class);

	static
	{
		THRALL_SPRITES.put(Thrall.Type.GHOSTLY, SpriteID.MagicNecroOn.RESURRECT_LESSER_GHOST);
		THRALL_SPRITES.put(Thrall.Type.SKELETAL, SpriteID.MagicNecroOn.RESURRECT_SUPERIOR_SKELETON);
		THRALL_SPRITES.put(Thrall.Type.ZOMBIFIED, SpriteID.MagicNecroOn.RESURRECT_GREATER_ZOMBIE);
	}

	/** Item icons for the melee styles and the atlatl (white weapons, eclipse atlatl). */
	private static final Map<Style.Type, Integer> TAB_ITEM_ICONS = new EnumMap<>(Style.Type.class);

	static
	{
		TAB_ITEM_ICONS.put(Style.Type.STAB, ItemID.WHITE_DAGGER);
		TAB_ITEM_ICONS.put(Style.Type.SLASH, ItemID.WHITE_SCIMITAR);
		TAB_ITEM_ICONS.put(Style.Type.CRUSH, ItemID.WHITE_WARHAMMER);
		TAB_ITEM_ICONS.put(Style.Type.ATLATL, ItemID.ECLIPSE_ATLATL);
	}

	private static final int TAB_SIZE = 32;
	/** CSS width of the wrapped max-hit line; Swing scales CSS px by 1.3, so this fills the stats box. */
	private static final int STAT_WRAP_WIDTH = 155;
	/** The overhead icon's hover text floats over the game, so it can be wider than the panel. */
	private static final int TIP_WRAP_WIDTH = 220;

	private final BestGearSetupPlugin plugin;
	private final ItemManager itemManager;
	private final SpriteCache sprites;
	private final SkillIconManager skillIcons;
	private final int ammoCount;
	private final SearchResults results;
	/** Result tabs, highest DPS first. */
	private final List<Style.Type> tabOrder = new ArrayList<>();
	private final JPanel tabs = new JPanel(new GridLayout(0, 5, 2, 2));
	private final JPanel body = new JPanel();

	private Style.Type selectedType;
	private Setup selected;
	/** Told the style tab the player clicks, so the panel can reopen it on the next recalculation. */
	private final Consumer<Style.Type> onPick;
	private List<TripSupplies.Supply> supplies = Collections.emptyList();

	/** @param kept the style tab to open on when these results have one; otherwise the best style */
	ResultView(SearchResults results, BestGearSetupPlugin plugin, ItemManager itemManager, SpriteCache sprites,
		SkillIconManager skillIcons, int ammoCount, Style.Type kept, Consumer<Style.Type> onPick)
	{
		this.onPick = onPick;
		this.results = results;
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.sprites = sprites;
		this.skillIcons = skillIcons;
		this.ammoCount = ammoCount;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(centred(tabs));
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(Sidebar.left(body));

		tabOrder.addAll(results.getByType().keySet());
		tabOrder.sort(Comparator.comparingDouble((Style.Type t) -> -results.getByType().get(t).get(0).getDps().getDps()));

		// A recalculation keeps the picked style; new results open on the best (first) one.
		if (!tabOrder.isEmpty())
		{
			Style.Type initial = tabOrder.contains(kept) ? kept : tabOrder.get(0);
			select(initial, results.getByType().get(initial).get(0));
		}
	}

	/** Recomputes the shown setup's trip supplies after a supply-only setting changed; the setups stay. */
	void refreshSupplies()
	{
		if (selected != null)
			select(selectedType, selected);
	}

	private void select(Style.Type type, Setup setup)
	{
		selectedType = type;
		selected = setup;
		List<Potion> potions = potionsFor(setup);
		supplies = plugin.tripSupplies(setup, results, potions);
		// The bank shows only the boosts the trip withdraws, as the Supplies line lists them.
		if (results.getMonster() != null)
			potions.removeIf(p -> !TripSupplies.takes(p, results.getMonster()));
		plugin.setBankHighlightedSetup(setup, potions, supplies);
		rebuildTabs();
		rebuildBody();
		Controls.restyle(this);
	}

	// ------------------------------------------------------------ tabs

	private void rebuildTabs()
	{
		tabs.removeAll();
		for (Style.Type type : tabOrder)
		{
			tabs.add(new StyleTab(type, results.getByType().get(type).get(0), type == selectedType));
		}
		tabs.revalidate();
		tabs.repaint();
	}

	private static String typeName(Style.Type type)
	{
		return GameData.titleCase(type.name().toLowerCase(Locale.ROOT));
	}

	/** Attack-style tab: the style's icon on a dark square, orange when selected. */
	private final class StyleTab extends JComponent
	{
		private final BufferedImage icon;
		private final boolean active;

		StyleTab(Style.Type type, Setup best, boolean active)
		{
			this.active = active;
			Integer itemId = TAB_ITEM_ICONS.get(type);
			if (itemId != null)
			{
				AsyncBufferedImage img = itemManager.getImage(itemId);
				img.onLoaded(this::repaint);
				icon = img;
			}
			else
			{
				icon = skillIcons.getSkillImage(type == Style.Type.MAGIC ? Skill.MAGIC : Skill.RANGED);
			}
			setPreferredSize(new Dimension(TAB_SIZE, TAB_SIZE));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText(String.format(Locale.ROOT, "%s: %.2f DPS with %s", typeName(type), best.getDps().getDps(),
				GameData.titleCase(best.getLoadout().getWeapon().getName())));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					onPick.accept(type);
					select(type, best);
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(active ? Theme.color("tabSelected") : ColorScheme.DARKER_GRAY_COLOR);
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
			if (icon != null && icon.getWidth() > 0)
			{
				// Fit inside the tab, keeping the aspect ratio (item icons are 36x32).
				double scale = Math.min(1.0, Math.min((getWidth() - 4) / (double) icon.getWidth(),
					(getHeight() - 4) / (double) icon.getHeight()));
				int w = (int) Math.round(icon.getWidth() * scale);
				int h = (int) Math.round(icon.getHeight() * scale);
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g2.drawImage(icon, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
			}
			g2.dispose();
		}
	}

	// ------------------------------------------------------------ body

	private void rebuildBody()
	{
		body.removeAll();
		if (selected == null)
		{
			body.revalidate();
			return;
		}
		Outfit l = selected.getLoadout();
		Dps d = selected.getDps();

		body.add(spacer(6));
		body.add(centred(new EquipmentGrid(selected, locksBySlot(), plugin, itemManager, sprites, ammoCount)));
		body.add(spacer(6));
		body.add(Sidebar.left(styleRow(l)));
		body.add(spacer(6));

		RuneUse runes = RuneUse.of(selected, results.getMonster().getHitpoints(),
			results.getBaseMagic(), results.getBoostedMagic());
		boolean poweredStaff = selected.getCombatClass() == Combat.MAGIC && l.getSpell() == null;
		JPanel stats = statsBox(d, thrallFor(selected), runes, plugin != null && plugin.isSunfireRunes(), poweredStaff);
		if (plugin != null)
			addTripLines(stats, selected, TripSupplies.kills(results.getMonster(), plugin.getKillsPerTrip()), supplies);
		collapsible(stats);
		body.add(Sidebar.left(stats));
		if (selected.getSpecial() != null)
		{
			body.add(spacer(4));
			body.add(Sidebar.left(specBox(selected.getSpecial(), l)));
		}
		body.add(spacer(4));
		body.add(Sidebar.left(costBox(l, selected.getSpecial())));

		List<Setup> others = results.getByType().getOrDefault(selectedType, Collections.emptyList());
		if (others.size() > 1)
		{
			JLabel header = new JLabel(typeName(selectedType) + " setups");
			header.setFont(FontManager.getRunescapeBoldFont());
			header.setForeground(Theme.color("accent"));
			header.setBorder(new EmptyBorder(8, 0, 2, 0));
			body.add(Sidebar.left(header));
			for (Setup r : others)
			{
				body.add(Sidebar.left(alternativeRow(r, r == selected)));
			}
		}
		body.revalidate();
		body.repaint();
	}

	private Map<Slot, LockStatus> locksBySlot()
	{
		Map<Slot, LockStatus> locks = new EnumMap<>(Slot.class);
		for (LockStatus lock : results.getLocks())
		{
			locks.put(lock.getSlot(), lock);
		}
		return locks;
	}

	/** Statistics, followed by expected attack and support rune use per kill. */
	JPanel statsBox(Dps d, Thrall thrall, RuneUse runes, boolean sunfire, boolean poweredStaff)
	{
		// One column, each row only as tall as its own text, so a wrapped max-hit note doesn't stretch every row
		JPanel box = box(new GridBagLayout(), 2, 4, Theme.color("dpsEdge"));
		String max = d.getMaxHit() + (d.getMaxHitDetail() == null ? "" : " (" + d.getMaxHitDetail() + ")");
		double ttk = d.getPrimaryDps() > 0 ? results.getStartingHitpoints() / d.getPrimaryDps() : 0;
		boolean area = d.getTargetsHit() > 1;
		line(box, "<b>" + (area ? "Total DPS: " : "DPS: ") + fmt(d.getDps(), 2) + "</b>", null, null);
		if (thrall != null)
		{
			line(box, "Incl. thrall: +" + fmt(thrall.getDps(), 2), "thrallDps",
				thrall.displayName(thrallType()) + ": max " + thrall.getMaxHit() + ", always hits");
		}
		String maxText = (area ? "Primary max: " : "Max hit: ") + max;
		JLabel maxHit = line(box, maxText, null, null);
		if (d.getMaxHitDetail() != null)
		{
			// A fixed width gives the long note its wrapped height; an unsized label only reports one line
			maxHit.setText(Sidebar.html(maxText, STAT_WRAP_WIDTH));
		}
		line(box, "Accuracy: " + fmt(d.getAccuracy() * 100, 1) + "%", null, null);
		line(box, (area ? "Avg attack: " : "Avg hit: ") + fmt(d.getAverageHit(), 2), null, null);
		line(box, "Speed: " + fmt(d.getExpectedSpeedTicks() * 0.6, 2) + "s", null, null);
		line(box, "TTK: ~" + fmt(ttk, ttk < 9.95 ? 1 : 0) + "s", null, "Enemy hitpoints / DPS, ignoring overkill");
		if (area)
		{
			line(box, "Primary DPS: " + fmt(d.getPrimaryDps(), 2), null, null);
			line(box, "Enemies hit: " + d.getTargetsHit(), null, null);
		}
		double kph = results.getMonster().getHitpoints() > 0 ? d.getDps() * 3600 / results.getMonster().getHitpoints() : 0;
		line(box, "Est. KPH: " + fmt(kph, 1), null, "DPS / enemy HP; continuous combat, no downtime");
		if (runes != null && !runes.getAttack().isEmpty())
		{
			line(box, "Runes/kill: " + runeEntries(runes.getAttack(), sunfire), "runesPerKill",
				"Expected per kill, minus staff and tome supply");
		}
		else if (poweredStaff)
		{
			line(box, "Runes/kill: uses charges, not counted", "runesPerKill", null);
		}
		if (runes != null && !runes.getSupport().isEmpty())
		{
			line(box, "Support/kill: " + runeEntries(runes.getSupport(), sunfire), "supportRunesPerKill",
				"Averaged over duration, without the Master Combat Achievements reward");
		}
		return box;
	}

	/** A trip's combat time, prayer use per kill and supplies (Kills per trip), then the overhead advice, below the setup's stats. */
	void addTripLines(JPanel box, Setup setup, int kills, List<TripSupplies.Supply> supplies)
	{
		int first = box.getComponentCount();
		// Raid targets have no trip: the raid hands out its supplies. Their overhead advice still shows.
		boolean trip = results.getMonster() == null || RaidScaling.raid(results.getMonster()) == RaidScaling.Raid.NONE;
		double seconds = TripSupplies.killSeconds(setup, results) * kills;
		if (trip && kills > 1 && Double.isFinite(seconds))
		{
			line(box, "Trip combat: ~" + duration(seconds), "tripCombatTime",
				Sidebar.tip("Kills per trip times TTK; no banking"));
		}
		double prayer = trip ? TripSupplies.prayerPerKill(setup, results) : 0;
		if (prayer > 0)
		{
			line(box, "Prayer/kill: ~" + fmt(prayer, 1) + " points", "prayerPerKill",
				Sidebar.tip("Offensive prayer and the overhead below"));
		}
		if (supplies != null && !supplies.isEmpty())
		{
			line(box, "", "tripSupplies", Sidebar.tip("Bottles and food for one trip"))
				.setText(Sidebar.html("Supplies: " + TripSupplies.summary(supplies), STAT_WRAP_WIDTH));
			if (TripSupplies.usedSlots(setup, supplies) > TripSupplies.INVENTORY_SLOTS)
			{
				line(box, "Doesn't fit one inventory: fewer kills per trip", "tripOverflow", null)
					.setForeground(Theme.color("warning"));
			}
		}
		divide(box, first);
		Overheads.Advice advice = results.overhead(setup.getCombatClass());
		if (advice != null)
		{
			int at = box.getComponentCount();
			line(box, "", "protectionPrayer", Sidebar.tip("Static advice from the Wiki")).setText(Sidebar.html(
				overheadText(advice), STAT_WRAP_WIDTH));
			divide(box, at);
		}
	}

	/**
	 * The overhead advice and the Wiki's note: "Overhead: Protect from Magic: ..." (a note that opens with the summary,
	 * "None needed at range: ...", continues it), then any advised overhead excluded in Rules.
	 */
	private static String overheadText(Overheads.Advice advice)
	{
		String note = advice.getNote().startsWith(advice.summary()) ? advice.getNote().substring(advice.summary().length())
			: ": " + advice.getNote();
		String excluded = advice.getExcluded().stream().map(Overheads.Overhead::toString).collect(Collectors.joining(" and "));
		return "<b>Overhead: " + Sidebar.escape(advice.summary()) + "</b>" + Sidebar.escape(note)
			+ (excluded.isEmpty() ? "" : "<br>" + Sidebar.escape(excluded) + " excluded in Rules.");
	}

	/** Shows only the box's first (DPS) line until it is clicked; another click hides the rest again. */
	static void collapsible(JPanel box)
	{
		JLabel head = (JLabel) box.getComponent(0);
		head.setName("statsToggle");
		head.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		head.setToolTipText("Show or hide details and overhead advice");
		head.setHorizontalTextPosition(SwingConstants.LEFT);
		head.setIconTextGap(6);
		Runnable show = () ->
		{
			head.setIcon(new Controls.Chevron(statsOpen ? Controls.Chevron.DOWN : Controls.Chevron.RIGHT, (Color) null));
			for (int i = 1; i < box.getComponentCount(); i++)
				box.getComponent(i).setVisible(statsOpen);
			box.revalidate();
			box.repaint();
		};
		show.run();
		MouseAdapter toggle = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				statsOpen = !statsOpen;
				show.run();
			}
		};
		head.addMouseListener(toggle);
	}

	/** A thin rule above the component at {@code index}, starting a new group of lines; nothing when none was added. */
	private static void divide(JPanel box, int index)
	{
		if (index < box.getComponentCount())
			((JComponent) box.getComponent(index)).setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(2, 0, 0, 0),
				BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
					new EmptyBorder(3, 0, 0, 0))));
	}

	/** Seconds as "3m 05s", or "1h 02m" from an hour. */
	static String duration(double seconds)
	{
		long total = Math.round(seconds);
		return total < 3600 ? total / 60 + "m " + String.format("%02d", total % 60) + "s"
			: total / 3600 + "h " + String.format("%02d", total / 60 % 60) + "m";
	}

	private static String runeEntries(Map<Integer, Double> counts, boolean sunfire)
	{
		List<String> entries = new ArrayList<>();
		for (Map.Entry<Integer, Double> entry : counts.entrySet())
		{
			String amount = entry.getValue() < 0.05 ? "<0.1" : fmt(entry.getValue(), 1);
			String name = sunfire && entry.getKey() == ItemID.FIRERUNE ? "fire/sunfire"
				: RuneUse.runeName(entry.getKey());
			entries.add(amount + " " + name);
		}
		return String.join(", ", entries);
	}

	/** The special attack mixed into the kill: its weapon, energy, uses per kill and one spec's stats. */
	private JPanel specBox(SpecialPlan plan, Outfit worn)
	{
		JPanel box = box(new BorderLayout(6, 0), 3, 3, Theme.color("specEdge"));
		boolean newWeapon = plan.getWeapon() != worn.getWeapon();
		JPanel icons = new JPanel(new GridLayout(1, 0, 2, 0));
		icons.setOpaque(false);
		for (Gear item : new Gear[]{plan.getWeapon(), plan.getOffhand(), plan.getAmmo()})
		{
			if (item != null)
			{
				JLabel icon = itemIcon(item.getId(), 36, GameData.titleCase(item.getName()));
				// No slot: locks belong to the worn gear, so the spec items only offer Exclude and Mark as owned.
				icon.setComponentPopupMenu(ItemMenus.create(item, null, false, plugin));
				icons.add(icon);
			}
		}
		if (icons.getComponentCount() > 0)
		{
			box.add(icons, BorderLayout.WEST);
		}
		String uses = newWeapon ? "switch" : plan.getOffhand() != null ? "add off-hand" : "same weapon";
		Dps spec = plan.getSpec();
		JLabel label = stat("<font color='#FF9F40'>" + Sidebar.escape(
			GameData.titleCase(plan.getSpecial().getName())) + "</font> spec (" + plan.getSpecial().getEnergy() + "%)"
			+ "<br>~" + fmt(plan.getSpecsPerKill(), 1) + " per kill, " + uses
			+ "<br>Max " + spec.getMaxHit() + ", " + fmt(spec.getAccuracy() * 100, 1) + "% acc");
		// The label already shows energy, specs per kill, max hit and accuracy; the icons show the spec weapon.
		box.setToolTipText("Without specs: " + fmt(plan.getOrdinary().getDps(), 2) + " DPS"
			+ (newWeapon ? "; switch weapons for it" : ""));
		box.add(label, BorderLayout.CENTER);
		return box;
	}

	/** The setup's priced items: worn, loaded ammo, and the spec items carried for it (nulls for empty slots). */
	private static List<Gear> pricedItems(Outfit l, SpecialPlan special)
	{
		List<Gear> items = new ArrayList<>();
		for (Slot slot : Slot.values())
		{
			items.add(l.get(slot));
		}
		items.add(l.getLoadedAmmo());
		if (special != null && special.getWeapon() != l.getWeapon())
		{
			// The spec weapon (and a spec blowpipe's darts) are carried in the inventory.
			items.add(special.getWeapon());
			items.add(special.getLoadedAmmo());
		}
		if (special != null)
		{
			items.add(special.getOffhand());
			items.add(special.getAmmo());
		}
		return items;
	}

	/** No GE: what unowned items cost in shop currencies other than coins, e.g. "78,000 Tokkul". */
	private List<String> otherCurrencyCosts(Outfit l, SpecialPlan special)
	{
		List<String> costs = new ArrayList<>();
		for (Gear item : pricedItems(l, special))
		{
			String currency = item == null ? null : plugin.shopCurrency(item);
			if (currency != null && !plugin.owns(item))
			{
				costs.add(currency);
			}
		}
		return costs;
	}

	JPanel costBox(Outfit l, SpecialPlan special)
	{
		long total = 0;
		long ownedValue = 0;
		int unpriced = 0;
		for (Gear item : pricedItems(l, special))
		{
			long[] v = value(item);
			total += v[0];
			ownedValue += v[1];
			unpriced += (int) v[2];
		}
		long buy = total - ownedValue;
		String unknown = unpriced == 0 ? "" : " + <font color='#FF6B6B'>" + unpriced + " unpriced</font>";
		List<String> other = otherCurrencyCosts(l, special);
		String elsewhere = other.isEmpty() ? "" : " + " + Sidebar.escape(String.join(" + ", other));

		JPanel box = box(new BorderLayout(), 3, 3, Theme.color("costEdge"));
		String owned = Controls.hex(Theme.color("owned"));
		JLabel label = stat("Cost: <font color='" + owned + "'>" + Budget.format(total) + "</font>" + unknown
			+ " (own <font color='" + owned + "'>" + Budget.format(ownedValue) + "</font>)<br>To buy: <font color='"
			+ (buy == 0 && unpriced == 0 && other.isEmpty() ? "#FFFFFF" : "#FFD24D") + "'>" + Budget.format(buy)
			+ elsewhere + "</font>" + unknown);
		label.setHorizontalAlignment(JLabel.CENTER);
		label.setToolTipText("Cost minus owned = to buy; " + (unpriced > 0 ? "unpriced left out"
			: ammoCount > 0 ? "ammo x" + ammoCount + " included" : "ammo not counted"));
		box.add(label, BorderLayout.CENTER);
		return box;
	}

	/**
	 * {total, owned, unpriced to buy} for one item; ammunition and thrown weapons are priced x ammo count and
	 * owned up to the stack held.
	 */
	private long[] value(Gear item)
	{
		if (item == null)
		{
			return new long[]{0, 0, 0};
		}
		boolean ammo = Weapons.consumedPerAttack(item);
		long price = plugin.price(item);
		boolean owned = plugin.owns(item);
		long units = ammo ? Math.max(0, ammoCount) : 1;
		long ownedUnits = !owned ? 0 : ammo ? Math.min(units, plugin.heldQuantity(item)) : 1;
		if (!ItemCosts.isKnown(price))
		{
			return new long[]{0, 0, ownedUnits < units ? 1 : 0};
		}
		return new long[]{price * units, price * ownedUnits, 0};
	}

	private JPanel styleRow(Outfit l)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel styleBox = new JPanel(new BorderLayout(0, 0));
		// A red edge like the game's selected combat style, on the same dark box as the stats.
		styleBox.setBackground(Theme.color("boxFill"));
		Controls.ownColours(styleBox);
		styleBox.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.color("styleEdge")),
			new EmptyBorder(2, 6, 2, 6)));
		Style style = l.getStyle();
		String name = style == null ? "" : GameData.titleCase(style.getName());
		String sub = style == null ? "" : GameData.titleCase(style.getType().name().toLowerCase(Locale.ROOT))
			+ ", " + GameData.titleCase(style.getStance());
		if (l.getSpell() != null)
		{
			name = l.getSpell().toString();
			sub = style != null && style.isManualCast() ? "Manual cast" : "Autocast";
		}
		else if (style != null && style.getType() == Style.Type.MAGIC && Weapons.isPoweredStaff(l.getWeapon()))
		{
			name = "Built-in spell";
		}
		JLabel styleLabel = stat("<center><font color='#FF9F40'>" + Sidebar.escape(name)
			+ "</font><br>" + Sidebar.escape(sub) + "</center>");
		styleLabel.setHorizontalAlignment(JLabel.CENTER);
		if (style != null)
		{
			int range = AttackReach.range(l.getWeapon(), style);
			styleLabel.setToolTipText(range == 0 ? "Attack range unknown" : "Attack range: " + range + " tiles");
		}
		styleBox.add(styleLabel, BorderLayout.CENTER);
		if (l.getSpell() != null)
		{
			String spell = GameData.titleCase(l.getSpell().getName());
			JPopupMenu menu = ItemMenus.of("Exclude " + spell, () -> plugin.setSpellRule(l.getSpell().getLowerName(), SpellRule.BLOCK));
			styleBox.setComponentPopupMenu(menu);
			styleLabel.setComponentPopupMenu(menu);
		}
		int spellSprite = l.getSpell() == null ? -1 : BestGearSetupPlugin.spellSprite(l.getSpell().getName());
		if (sprites != null && spellSprite >= 0)
		{
			// The spell's spellbook icon on the left, balanced by an empty slot so the text stays centred
			SpriteIcon spellIcon = new SpriteIcon(spellSprite, null);
			spellIcon.setComponentPopupMenu(styleBox.getComponentPopupMenu());
			styleBox.add(spellIcon, BorderLayout.WEST);
			JPanel balance = new JPanel();
			balance.setOpaque(false);
			balance.setPreferredSize(new Dimension(30, 32));
			styleBox.add(balance, BorderLayout.EAST);
		}
		row.add(styleBox, BorderLayout.NORTH);

		JPanel boosts = new JPanel(new GridLayout(0, 5, 2, 0));
		boosts.setBackground(ColorScheme.DARK_GRAY_COLOR);
		Combat cls = selected.getCombatClass();
		Overheads.Advice advice = results.overhead(cls);
		if (advice != null)
		{
			boosts.add(overheadIcon(advice));
		}
		OffensivePrayer prayer = results.getPrayers().get(cls);
		if (prayer != null)
		{
			String prayerName = GameData.titleCase(prayer.getName());
			Integer spriteId = PrayerBook.SPRITES.get(prayer.getName().toLowerCase(Locale.ROOT));
			JComponent icon = spriteId == null ? textChip(prayerName) : new SpriteIcon(spriteId, prayerName);
			icon.setComponentPopupMenu(ItemMenus.of("Exclude " + prayerName, () -> plugin.setPrayerExcluded(prayer.getName(), true)));
			boosts.add(icon);
		}
		if (usesMarkOfDarkness(l))
		{
			SpriteIcon mark = new SpriteIcon(SpriteID.MagicNecroOn.MARK_OF_DARKNESS,
				"Mark of Darkness (boosts demonbane spells against demons)");
			mark.setComponentPopupMenu(ItemMenus.of("Exclude Mark of Darkness",
				() -> plugin.setSpellRule("mark of darkness", SpellRule.BLOCK)));
			boosts.add(mark);
		}
		Thrall thrall = thrallFor(selected);
		if (thrall != null)
		{
			Thrall.Type type = thrallType();
			SpriteIcon thrallIcon = new SpriteIcon(THRALL_SPRITES.get(type),
				thrall.displayName(type) + " (+" + fmt(thrall.getDps(), 2) + " DPS included)");
			thrallIcon.setName("thrallIcon");
			thrallIcon.setComponentPopupMenu(ItemMenus.of("Turn off thralls", () -> plugin.setConfig("thrall", false)));
			boosts.add(thrallIcon);
		}
		for (Potion p : potionsFor(selected))
		{
			JLabel icon = itemIcon(p.getId(), 34, GameData.titleCase(p.getName()));
			icon.setComponentPopupMenu(ItemMenus.withFieldGuide(ItemMenus.of("Exclude " + GameData.titleCase(p.getName()),
				() -> plugin.setExcluded(p.getId(), true)), p.getId(), plugin));
			boosts.add(icon);
		}
		Antifire antifire = results.getAntifire();
		if (antifire != null && antifire != Antifire.NONE)
		{
			JLabel icon = itemIcon(antifire.getItemId(), 34, antifire + " (assumed against dragonfire)");
			icon.setName("antifireIcon");
			icon.setComponentPopupMenu(ItemMenus.withFieldGuide(ItemMenus.of("Exclude " + antifire,
				() -> plugin.setExcluded(antifire.getItemId(), true)), antifire.getItemId(), plugin));
			boosts.add(icon);
		}
		TripSupplies.Supply cure = TripSupplies.cure(selected, results.getMonster());
		if (cure != null)
		{
			JLabel icon = itemIcon(cure.getIds().get(0), 34, cure.getName() + " (the target "
				+ ("venom".equals(results.getMonster().getPoisons()) ? "envenoms" : "poisons") + " you)");
			icon.setName("cureIcon");
			boosts.add(icon);
		}
		row.add(boosts, BorderLayout.CENTER);
		return row;
	}

	/**
	 * The overhead to pray against the target, or the Prayer icon when none is needed or every advised one is
	 * excluded. Its hover text is the Wiki advice; right-click excludes an advised overhead or allows an excluded one.
	 */
	JComponent overheadIcon(Overheads.Advice advice)
	{
		Integer sprite = advice.getPrayer() == null ? null : PrayerBook.SPRITES.get(advice.getPrayer().toString().toLowerCase(Locale.ROOT));
		SpriteIcon icon = new SpriteIcon(sprite == null ? SpriteID.Staticons.PRAYER : sprite, null);
		icon.setName("overheadIcon");
		icon.setToolTipText(Sidebar.html(overheadText(advice), TIP_WRAP_WIDTH));
		JPopupMenu menu = new JPopupMenu();
		for (Overheads.Overhead o : advice.getSwitches())
			ItemMenus.add(menu, "Exclude " + o, () -> plugin.setPrayerExcluded(o.toString(), true));
		for (Overheads.Overhead o : advice.getExcluded())
			ItemMenus.add(menu, "Allow " + o, () -> plugin.setPrayerExcluded(o.toString(), false));
		icon.setComponentPopupMenu(menu.getComponentCount() == 0 ? null : menu);
		return icon;
	}

	/** Potions and hearts a setup in the selected tab assumes; the atlatl also uses the Strength boost. */
	private List<Potion> potionsFor(Setup setup)
	{
		return potionsFor(results, selectedType, setup.getCombatClass());
	}

	static List<Potion> potionsFor(SearchResults results, Style.Type type, Combat cls)
	{
		List<Potion> potions = new ArrayList<>(results.getPotions().getOrDefault(cls, Collections.emptyList()));
		if (type == Style.Type.ATLATL)
		{
			for (Potion p : results.getPotions().getOrDefault(Combat.MELEE, Collections.emptyList()))
			{
				if ("strength".equalsIgnoreCase(p.getSkill()) && potions.stream().noneMatch(x -> x.getId() == p.getId()))
				{
					potions.add(p);
				}
			}
		}
		return potions;
	}

	/** The thrall this setup's DPS includes, or null. */
	private Thrall thrallFor(Setup setup)
	{
		return setup.getSupportSpells() != null && setup.getSupportSpells().contains(SupportSpell.THRALL)
			? results.getThrall() : null;
	}

	/** The thrall type that can damage this target, magic first; ghostly when none is known. */
	private Thrall.Type thrallType()
	{
		Thrall.Type type = Thrall.eligibleType(results.getMonster());
		return type == null ? Thrall.Type.GHOSTLY : type;
	}

	/** Mark of Darkness only affects demonbane spells cast at demons. */
	private boolean usesMarkOfDarkness(Outfit l)
	{
		return results.isMarkOfDarkness() && l.getSpell() != null
			&& l.getSpell().getName().toLowerCase(Locale.ROOT).contains("demonbane")
			&& results.getMonster().hasAttribute("demon");
	}

	private JPanel alternativeRow(Setup r, boolean current)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		Color bg = Theme.color(current ? "rowCurrent" : "rowFill");
		row.setBackground(bg);
		Controls.ownColours(row);
		row.setBorder(current ? BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 2, 0, 0, Theme.color("accent")), new EmptyBorder(1, 0, 1, 4))
			: new EmptyBorder(1, 2, 1, 4));
		Gear weapon = r.getLoadout().getWeapon();
		row.add(itemIcon(weapon.getId(), 36, null), BorderLayout.WEST);
		JLabel name = new JLabel(GameData.titleCase(weapon.getName()));
		Sidebar.setSmall(name, Color.WHITE);
		row.add(name, BorderLayout.CENTER);
		// No GE: items bought with other currencies cost 0 coins, so the row isn't "owned" while it buys them.
		boolean elsewhere = !otherCurrencyCosts(r.getLoadout(), r.getSpecial()).isEmpty();
		JLabel right = new JLabel("<html><div style='text-align:right'>" + fmt(r.getDps().getDps(), 2) + " dps<br>"
			+ (r.getBuyCost() == 0 && r.getUnpricedItems() == 0 && !elsewhere
			? "<font color='" + Controls.hex(Theme.color("owned")) + "'>owned</font>"
			: "<font color='#FFD24D'>" + Budget.format(r.getBuyCost()) + (r.getUnpricedItems() > 0 ? "+?" : "")
			+ (elsewhere ? " + shop" : "") + "</font>")
			+ "</div></html>");
		Sidebar.setSmall(right, ColorScheme.LIGHT_GRAY_COLOR);
		row.add(right, BorderLayout.EAST);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		SpecialPlan plan = r.getSpecial();
		row.setToolTipText(plan == null ? "Show this setup" : "Show setup; specs with "
			+ GameData.titleCase(plan.getWeapon().getName()));
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				select(selectedType, r);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(bg);
			}
		});
		JPanel wrap = Sidebar.left(row);
		wrap.setBorder(new EmptyBorder(1, 0, 1, 0));
		return wrap;
	}

	/** An item's icon, {@code width} wide, with hover text when not null. */
	private JLabel itemIcon(int itemId, int width, String tip)
	{
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(width, 32));
		icon.setToolTipText(tip);
		itemManager.getImage(itemId).addTo(icon);
		return icon;
	}

	private final class SpriteIcon extends JComponent
	{
		private final int spriteId;

		SpriteIcon(int spriteId, String tooltip)
		{
			this.spriteId = spriteId;
			setPreferredSize(new Dimension(30, 32));
			setToolTipText(tooltip);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			BufferedImage img = sprites.get(spriteId, this::repaint);
			if (img != null)
			{
				g.drawImage(img, (getWidth() - img.getWidth()) / 2, (getHeight() - img.getHeight()) / 2, null);
			}
		}
	}

	/** A dark box with a coloured edge, padded 6px at the sides. */
	private static JPanel box(LayoutManager layout, int top, int bottom, Color edge)
	{
		return Controls.framed(layout, top, bottom, edge);
	}

	/** A full-width row with {@code c} centred at its preferred size. */
	private static JPanel centred(Component c)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.add(c);
		return Sidebar.left(row);
	}

	private static JLabel textChip(String text)
	{
		JLabel l = new JLabel(text);
		Sidebar.setSmall(l, YELLOW);
		return l;
	}

	/** Adds a statistics line under the last, with its test name and hover text when not null. */
	private static JLabel line(JPanel box, String html, String name, String tip)
	{
		JLabel label = stat(html);
		label.setName(name);
		label.setToolTipText(tip);
		GridBagConstraints row = new GridBagConstraints();
		row.gridx = 0;
		row.weightx = 1;
		row.fill = GridBagConstraints.HORIZONTAL;
		row.insets = new Insets(2, 0, 0, 0);
		box.add(label, row);
		return label;
	}

	private static JLabel stat(String html)
	{
		JLabel l = new JLabel("<html>" + html + "</html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(Color.WHITE);
		return l;
	}

	private static String fmt(double v, int decimals)
	{
		return String.format(Locale.ROOT, "%." + decimals + "f", v);
	}

	private static Component spacer(int h)
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(1, h));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}
}
