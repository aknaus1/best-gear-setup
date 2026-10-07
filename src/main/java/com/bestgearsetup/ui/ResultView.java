package com.bestgearsetup.ui;

import com.bestgearsetup.Budget;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.ItemCosts;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.calc.AttackReach;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SpecialPlan;
import com.bestgearsetup.calc.SupportSpell;
import com.bestgearsetup.calc.Thrall;
import com.bestgearsetup.calc.WeaponRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.OffensivePrayer;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
	private static final Color TAB_SELECTED = new Color(0xF7, 0x8B, 0x00);
	private static final Color BOX_BORDER = new Color(0x2E, 0x8B, 0x2E);
	private static final Color BOX_BG = new Color(0x1E, 0x1E, 0x1E);
	private static final Color STYLE_BG = new Color(0x8B, 0x1A, 0x1A);
	private static final Color STYLE_BORDER = new Color(0xC0, 0x39, 0x2B);
	private static final Color YELLOW = new Color(0xFF, 0xD2, 0x4D);

	private static final Map<String, Integer> PRAYER_SPRITES = new HashMap<>();
	private static final Map<Thrall, Integer> THRALL_SPRITES = new EnumMap<>(Thrall.class);

	static
	{
		THRALL_SPRITES.put(Thrall.LESSER, SpriteID.MagicNecroOn.RESURRECT_LESSER_GHOST);
		THRALL_SPRITES.put(Thrall.SUPERIOR, SpriteID.MagicNecroOn.RESURRECT_SUPERIOR_SKELETON);
		THRALL_SPRITES.put(Thrall.GREATER, SpriteID.MagicNecroOn.RESURRECT_GREATER_ZOMBIE);
		PRAYER_SPRITES.put("piety", SpriteID.Prayeron.PIETY);
		PRAYER_SPRITES.put("chivalry", SpriteID.Prayeron.CHIVALRY);
		PRAYER_SPRITES.put("ultimate strength", SpriteID.Prayeron.ULTIMATE_STRENGTH);
		PRAYER_SPRITES.put("incredible reflexes", SpriteID.Prayeron.INCREDIBLE_REFLEXES);
		PRAYER_SPRITES.put("superhuman strength", SpriteID.Prayeron.SUPERHUMAN_STRENGTH);
		PRAYER_SPRITES.put("improved reflexes", SpriteID.Prayeron.IMPROVED_REFLEXES);
		PRAYER_SPRITES.put("burst of strength", SpriteID.Prayeron.BURST_OF_STRENGTH);
		PRAYER_SPRITES.put("clarity of thought", SpriteID.Prayeron.CLARITY_OF_THOUGHT);
		PRAYER_SPRITES.put("rigour", SpriteID.Prayeron.RIGOUR);
		PRAYER_SPRITES.put("deadeye", SpriteID.Prayeron.DEADEYE);
		PRAYER_SPRITES.put("eagle eye", SpriteID.Prayeron.EAGLE_EYE);
		PRAYER_SPRITES.put("hawk eye", SpriteID.Prayeron.HAWK_EYE);
		PRAYER_SPRITES.put("sharp eye", SpriteID.Prayeron.SHARP_EYE);
		PRAYER_SPRITES.put("augury", SpriteID.Prayeron.AUGURY);
		PRAYER_SPRITES.put("mystic vigour", SpriteID.Prayeron.MYSTIC_VIGOUR);
		PRAYER_SPRITES.put("mystic might", SpriteID.Prayeron.MYSTIC_MIGHT);
		PRAYER_SPRITES.put("mystic lore", SpriteID.Prayeron.MYSTIC_LORE);
		PRAYER_SPRITES.put("mystic will", SpriteID.Prayeron.MYSTIC_WILL);
	}

	/** Item icons for the melee styles and the atlatl (white weapons, eclipse atlatl). */
	private static final Map<AttackStyle.Type, Integer> TAB_ITEM_ICONS = new EnumMap<>(AttackStyle.Type.class);

	static
	{
		TAB_ITEM_ICONS.put(AttackStyle.Type.STAB, ItemID.WHITE_DAGGER);
		TAB_ITEM_ICONS.put(AttackStyle.Type.SLASH, ItemID.WHITE_SCIMITAR);
		TAB_ITEM_ICONS.put(AttackStyle.Type.CRUSH, ItemID.WHITE_WARHAMMER);
		TAB_ITEM_ICONS.put(AttackStyle.Type.ATLATL, ItemID.ECLIPSE_ATLATL);
	}

	private static final int TAB_SIZE = 32;

	private final BestGearSetupPlugin plugin;
	private final ItemManager itemManager;
	private final SpriteCache sprites;
	private final SkillIconManager skillIcons;
	private final int ammoCount;
	private final SearchResults results;
	/** Result tabs, highest DPS first. */
	private final List<AttackStyle.Type> tabOrder = new ArrayList<>();
	private final JPanel tabs = new JPanel(new GridLayout(0, 5, 2, 2));
	private final JPanel body = new JPanel();

	private AttackStyle.Type selectedType;
	private SetupResult selected;

	ResultView(SearchResults results, BestGearSetupPlugin plugin, ItemManager itemManager, SpriteCache sprites,
		SkillIconManager skillIcons, int ammoCount)
	{
		this.results = results;
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.sprites = sprites;
		this.skillIcons = skillIcons;
		this.ammoCount = ammoCount;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JPanel tabRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		tabRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabRow.add(tabs);
		add(BestGearSetupPanel.left(tabRow));
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(BestGearSetupPanel.left(body));

		tabOrder.addAll(results.getByType().keySet());
		tabOrder.sort(Comparator.comparingDouble((AttackStyle.Type t) -> -results.getByType().get(t).get(0).getDps().getDps()));

		// Every recalculation opens on the best (first) style.
		if (!tabOrder.isEmpty())
		{
			AttackStyle.Type initial = tabOrder.get(0);
			select(initial, results.getByType().get(initial).get(0));
		}
	}

	private void select(AttackStyle.Type type, SetupResult setup)
	{
		selectedType = type;
		selected = setup;
		plugin.setBankHighlightedSetup(setup, potionsFor(setup));
		rebuildTabs();
		rebuildBody();
	}

	// ------------------------------------------------------------ tabs

	private void rebuildTabs()
	{
		tabs.removeAll();
		for (AttackStyle.Type type : tabOrder)
		{
			tabs.add(new StyleTab(type, results.getByType().get(type).get(0), type == selectedType));
		}
		tabs.revalidate();
		tabs.repaint();
	}

	private static String typeName(AttackStyle.Type type)
	{
		return GameData.titleCase(type.name().toLowerCase(Locale.ROOT));
	}

	/** Attack-style tab: the style's icon on a dark square, orange when selected. */
	private final class StyleTab extends JComponent
	{
		private final BufferedImage icon;
		private final boolean active;

		StyleTab(AttackStyle.Type type, SetupResult best, boolean active)
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
				icon = skillIcons.getSkillImage(type == AttackStyle.Type.MAGIC ? Skill.MAGIC : Skill.RANGED);
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
					select(type, best);
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(active ? TAB_SELECTED : ColorScheme.DARKER_GRAY_COLOR);
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
		Loadout l = selected.getLoadout();
		DpsResult d = selected.getDps();

		body.add(spacer(6));
		JPanel gridRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		gridRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		gridRow.add(new EquipmentGrid(selected, locksBySlot(), plugin, itemManager, sprites, ammoCount));
		body.add(BestGearSetupPanel.left(gridRow));
		body.add(spacer(6));

		body.add(BestGearSetupPanel.left(statsBox(d, thrallFor(selected))));
		if (selected.getSpecial() != null)
		{
			body.add(spacer(4));
			body.add(BestGearSetupPanel.left(specBox(selected.getSpecial(), l)));
		}
		body.add(spacer(4));
		body.add(BestGearSetupPanel.left(costBox(l, selected.getSpecial())));
		body.add(spacer(6));
		body.add(BestGearSetupPanel.left(styleRow(l)));

		List<SetupResult> others = results.getByType().getOrDefault(selectedType, Collections.emptyList());
		if (others.size() > 1)
		{
			JLabel header = new JLabel("Other " + typeName(selectedType) + " setups");
			header.setFont(FontManager.getRunescapeBoldFont());
			header.setForeground(ColorScheme.BRAND_ORANGE);
			header.setBorder(new EmptyBorder(8, 0, 2, 0));
			body.add(BestGearSetupPanel.left(header));
			for (SetupResult r : others)
			{
				body.add(BestGearSetupPanel.left(alternativeRow(r, r == selected)));
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

	private JPanel statsBox(DpsResult d, Thrall thrall)
	{
		JPanel box = new JPanel(new GridLayout(0, 1, 0, 2));
		box.setBackground(BOX_BG);
		box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BOX_BORDER),
			new EmptyBorder(4, 6, 4, 6)));
		String max = d.getMaxHit() + (d.getMaxHitDetail() == null ? "" : " (" + d.getMaxHitDetail() + ")");
		double ttk = d.getPrimaryDps() > 0 ? results.getStartingHitpoints() / d.getPrimaryDps() : 0;
		boolean area = d.getTargetsHit() > 1;
		box.add(stat("<b>" + (area ? "Total DPS: " : "DPS: ") + fmt(d.getDps(), 3) + "</b>"));
		if (thrall != null)
		{
			JLabel thrallLabel = stat("Incl. thrall: +" + fmt(thrall.getDps(), 3));
			thrallLabel.setName("thrallDps");
			thrallLabel.setToolTipText(thrall + " (max hit " + thrall.getMaxHit() + ", always hits, every 2.4s)");
			box.add(thrallLabel);
		}
		box.add(stat((area ? "Primary max: " : "Max hit: ") + max));
		box.add(stat("Accuracy: " + fmt(d.getAccuracy() * 100, 1) + "%"));
		box.add(stat((area ? "Avg attack: " : "Avg hit: ") + fmt(d.getAverageHit(), 2)));
		box.add(stat("Speed: " + fmt(d.getExpectedSpeedTicks() * 0.6, 2) + "s"));
		JLabel ttkLabel = stat("TTK: ~" + fmt(ttk, 1) + "s");
		ttkLabel.setToolTipText("Approximate: selected enemy hitpoints / damage to that enemy (no overkill)");
		box.add(ttkLabel);
		if (area)
		{
			box.add(stat("Primary DPS: " + fmt(d.getPrimaryDps(), 3)));
			box.add(stat("Enemies hit: " + d.getTargetsHit()));
		}
		double kph = results.getMonster().getHitpoints() > 0 ? d.getDps() * 3600 / results.getMonster().getHitpoints() : 0;
		JLabel kills = stat("Est. KPH: " + fmt(kph, 1));
		kills.setToolTipText("Damage-based estimate: total DPS / enemy HP. Assumes continuous combat; excludes overkill, "
			+ "respawns, travel, banking, eating and boss phases.");
		box.add(kills);
		return box;
	}

	/** The special attack mixed into the kill: its weapon, energy, uses per kill and one spec's stats. */
	private JPanel specBox(SpecialPlan plan, Loadout worn)
	{
		JPanel box = new JPanel(new BorderLayout(6, 0));
		box.setBackground(BOX_BG);
		box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BOX_BORDER),
			new EmptyBorder(3, 6, 3, 6)));
		boolean newWeapon = plan.getWeapon() != worn.getWeapon();
		JPanel icons = new JPanel(new GridLayout(1, 0, 2, 0));
		icons.setOpaque(false);
		for (GearItem item : new GearItem[]{newWeapon ? plan.getWeapon() : null, plan.getOffhand(), plan.getAmmo()})
		{
			if (item != null)
			{
				JLabel icon = new JLabel();
				icon.setPreferredSize(new Dimension(36, 32));
				icon.setToolTipText(GameData.titleCase(item.getName()));
				itemManager.getImage(item.getId()).addTo(icon);
				icons.add(icon);
			}
		}
		if (icons.getComponentCount() > 0)
		{
			box.add(icons, BorderLayout.WEST);
		}
		GearItem ring = worn.get(Slot.RING);
		boolean lightbearer = ring != null && ring.getId() == ItemID.LIGHTBEARER;
		String uses = newWeapon ? "switch" : plan.getOffhand() != null ? "add off-hand" : "same weapon";
		DpsResult drained = plan.getDrained();
		DpsResult spec = plan.getSpec();
		// The spec's detail opens with its own description, already shown by name and energy.
		String detail = spec.getMaxHitDetail() == null ? "" : spec.getMaxHitDetail();
		String described = plan.getSpecial().describe();
		detail = detail.startsWith(described) ? detail.substring(described.length()).replaceFirst("^; ", "") : detail;
		JLabel label = new JLabel("<html><font color='#FF9F40'>" + BestGearSetupPanel.escape(
			GameData.titleCase(plan.getSpecial().getName())) + "</font> spec (" + plan.getSpecial().getEnergy() + "%)"
			+ "<br>~" + fmt(plan.getSpecsPerKill(), 1) + " per kill, " + uses
			+ "<br>Max " + spec.getMaxHit() + ", " + fmt(spec.getAccuracy() * 100, 1) + "% acc</html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.WHITE);
		box.setToolTipText("<html>Special attacks are used whenever energy allows and are counted in DPS, TTK and KPH."
			+ "<br>Without specs: " + fmt(plan.getOrdinary().getDps(), 3) + " DPS. One spec averages "
			+ fmt(spec.getAverageHit(), 2) + " damage every " + fmt(spec.getExpectedSpeedTicks() * 0.6, 1) + "s."
			+ (detail.isEmpty() ? "" : "<br>Max hit: " + BestGearSetupPanel.escape(detail))
			+ (drained == null ? "" : "<br>Opens each kill for its drain: one landed spec raises ordinary DPS from "
			+ fmt(plan.getOrdinary().getDps(), 3) + " to " + fmt(drained.getDps(), 3) + ".")
			+ (newWeapon ? "<br>Switch from " + BestGearSetupPanel.escape(GameData.titleCase(worn.getWeapon().getName()))
			+ " to " + BestGearSetupPanel.escape(GameData.titleCase(plan.getWeapon().getName())) : "")
			+ (plan.getOffhand() == null ? "" : (newWeapon ? " with " : "<br>Equip ")
			+ BestGearSetupPanel.escape(GameData.titleCase(plan.getOffhand().getName())) + " in the off-hand")
			+ (plan.getAmmo() == null ? "" : (newWeapon || plan.getOffhand() != null ? ", firing " : "<br>Equip ")
			+ BestGearSetupPanel.escape(GameData.titleCase(plan.getAmmo().getName())))
			+ (plan.isSwitch(worn) ? "; the rest of the setup stays on." : "")
			+ (lightbearer ? "<br>Lightbearer doubles energy regeneration." : "") + "</html>");
		box.add(label, BorderLayout.CENTER);
		return box;
	}

	private JPanel costBox(Loadout l, SpecialPlan special)
	{
		long total = 0;
		long ownedValue = 0;
		int unpriced = 0;
		for (Slot slot : Slot.values())
		{
			long[] v = value(l.get(slot));
			total += v[0];
			ownedValue += v[1];
			unpriced += (int) v[2];
		}
		List<GearItem> extras = new ArrayList<>();
		extras.add(l.getLoadedAmmo());
		if (special != null && special.getWeapon() != l.getWeapon())
		{
			// The spec weapon (and a spec blowpipe's darts) are carried in the inventory.
			extras.add(special.getWeapon());
			extras.add(special.getLoadedAmmo());
		}
		if (special != null)
		{
			extras.add(special.getOffhand());
			extras.add(special.getAmmo());
		}
		for (GearItem extra : extras)
		{
			long[] v = value(extra);
			total += v[0];
			ownedValue += v[1];
			unpriced += (int) v[2];
		}
		long buy = total - ownedValue;
		String unknown = unpriced == 0 ? "" : " + <font color='#FF6B6B'>" + unpriced + " unpriced</font>";

		JPanel box = new JPanel(new BorderLayout());
		box.setBackground(BOX_BG);
		box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BOX_BORDER),
			new EmptyBorder(3, 6, 3, 6)));
		JLabel label = new JLabel("<html>Cost: <font color='#6BD56B'>" + Budget.format(total) + "</font>" + unknown
			+ " - <font color='#6BD56B'>" + Budget.format(ownedValue) + "</font><br>To buy: <font color='"
			+ (buy == 0 && unpriced == 0 ? "#FFFFFF" : "#FFD24D") + "'>" + Budget.format(buy) + "</font>" + unknown
			+ "</html>", JLabel.CENTER);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.WHITE);
		label.setToolTipText("Acquisition cost including tradable components - items you own = GP needed"
			+ " (weapon charges and ongoing upkeep excluded)"
			+ (ammoCount > 0 ? " (ammo x" + ammoCount + ")" : " (ammo not counted)")
			+ (unpriced == 0 ? "" : ". Unpriced items have no current Grand Exchange price; totals exclude them"));
		box.add(label, BorderLayout.CENTER);
		return box;
	}

	/**
	 * {total, owned, unpriced to buy} for one item; ammunition and thrown weapons are priced x ammo count and
	 * owned up to the stack held.
	 */
	private long[] value(GearItem item)
	{
		if (item == null)
		{
			return new long[]{0, 0, 0};
		}
		boolean ammo = WeaponRules.consumedPerAttack(item);
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

	private JPanel styleRow(Loadout l)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel styleBox = new JPanel(new BorderLayout(0, 0));
		styleBox.setBackground(STYLE_BG);
		styleBox.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(STYLE_BORDER, 2),
			new EmptyBorder(2, 6, 2, 6)));
		AttackStyle style = l.getStyle();
		String name = style == null ? "" : GameData.titleCase(style.getName());
		String sub = style == null ? "" : GameData.titleCase(style.getType().name().toLowerCase(Locale.ROOT))
			+ ", " + GameData.titleCase(style.getStance());
		if (l.getSpell() != null)
		{
			name = l.getSpell().toString();
			sub = "Autocast";
		}
		else if (style != null && style.getType() == AttackStyle.Type.MAGIC && WeaponRules.isPoweredStaff(l.getWeapon()))
		{
			name = "Built-in spell";
		}
		JLabel styleLabel = new JLabel("<html><center><font color='#FF9F40'>" + BestGearSetupPanel.escape(name)
			+ "</font><br>" + BestGearSetupPanel.escape(sub) + "</center></html>", JLabel.CENTER);
		styleLabel.setFont(FontManager.getRunescapeSmallFont());
		styleLabel.setForeground(Color.WHITE);
		if (style != null)
		{
			int range = AttackReach.range(l.getWeapon(), style);
			styleLabel.setToolTipText(range == 0 ? "Attack range unknown" : "Attack range: " + range + " tiles");
		}
		styleBox.add(styleLabel, BorderLayout.CENTER);
		row.add(styleBox, BorderLayout.NORTH);

		JPanel boosts = new JPanel(new GridLayout(0, 5, 2, 0));
		boosts.setBackground(ColorScheme.DARK_GRAY_COLOR);
		CombatClass cls = selected.getCombatClass();
		OffensivePrayer prayer = results.getPrayers().get(cls);
		if (prayer != null)
		{
			Integer spriteId = PRAYER_SPRITES.get(prayer.getName().toLowerCase(Locale.ROOT));
			boosts.add(spriteId == null
				? textChip(GameData.titleCase(prayer.getName()))
				: new SpriteIcon(spriteId, GameData.titleCase(prayer.getName())));
		}
		if (usesMarkOfDarkness(l))
		{
			boosts.add(new SpriteIcon(SpriteID.MagicNecroOn.MARK_OF_DARKNESS,
				"Mark of Darkness (boosts demonbane spells against demons)"));
		}
		Thrall thrall = thrallFor(selected);
		if (thrall != null)
		{
			SpriteIcon thrallIcon = new SpriteIcon(THRALL_SPRITES.get(thrall),
				thrall + " (+" + fmt(thrall.getDps(), 3) + " DPS included)");
			thrallIcon.setName("thrallIcon");
			boosts.add(thrallIcon);
		}
		for (Potion p : potionsFor(selected))
		{
			JLabel icon = new JLabel();
			icon.setPreferredSize(new Dimension(34, 32));
			itemManager.getImage(p.getId()).addTo(icon);
			icon.setToolTipText(GameData.titleCase(p.getName()));
			boosts.add(icon);
		}
		row.add(boosts, BorderLayout.CENTER);
		return row;
	}

	/** Potions and hearts a setup in the selected tab assumes; the atlatl also uses the melee boosts. */
	private List<Potion> potionsFor(SetupResult setup)
	{
		List<Potion> potions = new ArrayList<>(results.getPotions().getOrDefault(setup.getCombatClass(),
			Collections.emptyList()));
		if (selectedType == AttackStyle.Type.ATLATL)
		{
			for (Potion p : results.getPotions().getOrDefault(CombatClass.MELEE, Collections.emptyList()))
			{
				if (potions.stream().noneMatch(x -> x.getId() == p.getId()))
				{
					potions.add(p);
				}
			}
		}
		return potions;
	}

	/** Mark of Darkness only affects demonbane spells cast at demons. */
	/** The thrall this setup's DPS includes, or null. */
	private Thrall thrallFor(SetupResult setup)
	{
		return setup.getSupportSpells() != null && setup.getSupportSpells().contains(SupportSpell.THRALL)
			? results.getThrall() : null;
	}

	private boolean usesMarkOfDarkness(Loadout l)
	{
		return results.isMarkOfDarkness() && l.getSpell() != null
			&& l.getSpell().getName().toLowerCase(Locale.ROOT).contains("demonbane")
			&& results.getMonster().hasAttribute("demon");
	}

	private JPanel alternativeRow(SetupResult r, boolean current)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		Color bg = current ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR;
		row.setBackground(bg);
		row.setBorder(new EmptyBorder(1, 2, 1, 4));
		GearItem weapon = r.getLoadout().getWeapon();
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		itemManager.getImage(weapon.getId()).addTo(icon);
		row.add(icon, BorderLayout.WEST);
		JLabel name = new JLabel(GameData.titleCase(weapon.getName()));
		BestGearSetupPanel.setSmall(name, Color.WHITE);
		row.add(name, BorderLayout.CENTER);
		JLabel right = new JLabel("<html><div style='text-align:right'>" + fmt(r.getDps().getDps(), 2) + " dps<br>"
			+ (r.getBuyCost() == 0 && r.getUnpricedItems() == 0 ? "<font color='#6BD56B'>owned</font>"
			: "<font color='#FFD24D'>" + Budget.format(r.getBuyCost()) + (r.getUnpricedItems() > 0 ? "+?" : "") + "</font>")
			+ "</div></html>");
		BestGearSetupPanel.setSmall(right, ColorScheme.LIGHT_GRAY_COLOR);
		row.add(right, BorderLayout.EAST);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		SpecialPlan plan = r.getSpecial();
		row.setToolTipText(plan == null ? "Show this setup" : "Show this setup (specs with "
			+ GameData.titleCase(plan.getWeapon().getName()) + ")");
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
		JPanel wrap = BestGearSetupPanel.left(row);
		wrap.setBorder(new EmptyBorder(1, 0, 1, 0));
		return wrap;
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

	private static JLabel textChip(String text)
	{
		JLabel l = new JLabel(text);
		BestGearSetupPanel.setSmall(l, YELLOW);
		return l;
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
