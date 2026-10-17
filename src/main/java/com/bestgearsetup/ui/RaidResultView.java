package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.Budget;
import com.bestgearsetup.CoxChestPlan;
import com.bestgearsetup.RaidEncounter;
import com.bestgearsetup.RaidPlan;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.data.MonsterSummary;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import java.awt.event.ActionListener;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;

/** Raid inventory plus an encounter selector; every displayed setup belongs to the published kit. */
class RaidResultView extends JPanel
{
	RaidResultView(RaidPlan plan, MonsterSummary active, SearchResults activeResult, BestGearSetupPlugin plugin, ItemManager items,
		SpriteCache sprites, SkillIconManager skills, int ammoCount, JComboBox<RaidEncounter> encounter,
		JComboBox<RaidEncounter> version, Consumer<RaidEncounter> selected)
	{
		this(plan, active, activeResult, plugin, items, sprites, skills, ammoCount, encounter, version, selected, null);
	}

	RaidResultView(RaidPlan plan, MonsterSummary active, SearchResults activeResult, BestGearSetupPlugin plugin, ItemManager items,
		SpriteCache sprites, SkillIconManager skills, int ammoCount, JComboBox<RaidEncounter> encounter,
		JComboBox<RaidEncounter> version, Consumer<RaidEncounter> selected, RaidEncounter exact)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		InventoryView inventorySection = new InventoryView(items, encounter, "raidInventory", plugin, sprites, ammoCount);
		detachSelector(encounter);
		detachSelector(version);
		Map<String, RaidEncounter> bosses = new LinkedHashMap<>();
		plan.getEncounters().forEach(e -> bosses.putIfAbsent(e.getBossName(), e));
		encounter.setModel(new DefaultComboBoxModel<>(bosses.values().toArray(new RaidEncounter[0])));

		JPanel detail = new JPanel();
		detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
		detail.setOpaque(false);
		Runnable show = () ->
		{
			RaidEncounter current = (RaidEncounter) version.getSelectedItem();
			if (current == null) { return; }
			int index = plan.getEncounters().indexOf(current);
			detail.removeAll();
			SearchResults result = plan.getResults().get(index).withSetup(plan.getSetups().get(index));
			// Only the encounter the live target refreshed shows the refreshed result.
			if (activeResult != null && active != null
				&& current.equals(exact == null ? plan.encounterFor(active.getName(), activeResult.getMonster().getPhase()) : exact))
			{ result = activeResult; }
			if (result.getByType().isEmpty())
			{
				detail.add(new JLabel(BestGearSetupPanel.html("No usable setup with your carried items and current restrictions.")));
				inventorySection.showItems(java.util.Collections.emptyList(), plan::potionUse, null);
				inventorySection.setVisible(false);
				selected.accept(current);
				detail.revalidate(); detail.repaint();
				return;
			}
			java.util.List<RaidPlan.InventoryItem> carried = plan.inventoryFor(result.getByType().values().iterator().next().get(0));
			CoxChestPlan chest = CoxChestPlan.forEncounter(plan, current, result.getByType().values().iterator().next().get(0));
			inventorySection.showItems(carried, plan::potionUse, chest);
			inventorySection.setVisible(true);
			selected.accept(plan.getEncounters().get(index));
			JLabel stats = new JLabel(BestGearSetupPanel.html("Defence: " + result.getBaseDefence() + " → "
				+ result.getMonster().getDefenceLevel() + (result.getRaidScaling() == null ? "" : "<br>" + BestGearSetupPanel.escape(result.getRaidScaling()))));
			stats.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			detail.add(new ResultView(result, plugin, items, sprites, skills, ammoCount, false, inventorySection, null,
				plan::potionUse));
			plugin.setCoxChestHighlights(plan, chest);
			detail.add(BestGearSetupPanel.left(stats));
			detail.revalidate();
			detail.repaint();
		};
		RaidEncounter initial = exact != null ? exact : active == null ? null
			: plan.encounterFor(active.getName(), activeResult == null ? null : activeResult.getMonster().getPhase());
		// A search from the raid's default (first) target opens on the setup the kit is worn in for.
		if (initial == null || exact == null && initial.equals(plan.getEncounters().get(0))) { initial = plan.getStartingEncounter(); }
		encounter.setSelectedItem(bosses.get(initial.getBossName()));
		Runnable versions = () ->
		{
			RaidEncounter boss = (RaidEncounter) encounter.getSelectedItem();
			version.setModel(new DefaultComboBoxModel<>(plan.getEncounters().stream()
				.filter(e -> e.getBossName().equals(boss.getBossName())).toArray(RaidEncounter[]::new)));
		};
		versions.run();
		version.setSelectedItem(initial);
		ActionListener versionListener = e -> show.run();
		version.addActionListener(versionListener);
		version.putClientProperty("raidSelectionListener", versionListener);
		ActionListener listener = e -> { versions.run(); show.run(); };
		encounter.addActionListener(listener);
		encounter.putClientProperty("raidSelectionListener", listener);
		add(BestGearSetupPanel.left(detail));
		plugin.setBankHighlightedRaid(plan);
		show.run();
	}

	static JLabel searchSummary(RaidPlan plan, boolean live)
	{
		// Switches, the rune pouch, loose runes and a carried book of the dead count against the limit, a maximum.
		int equipment = RaidPlan.limitedSlots(plan.getInventory());
		JLabel summary = new JLabel(BestGearSetupPanel.html((!live ? "Start wearing <b>" : "Pre-raid starting setup: <b>") + plan.getStartingEncounter().getLabel()
			+ "</b> gear.<br>Equipment slots: " + equipment + " / " + plan.getEquipmentLimit() + " max"
			+ "<br>" + (!live ? "Estimated combat time: " : "Pre-raid combat estimate: ") + duration(plan.getEstimatedSeconds())
			+ "<br>Spellbook: " + plan.getSpellbook()
			+ "<br>Inventory: " + plan.getInventory().size() + " / 28."
			+ boostSummary(plan)
			+ "<br>Shared kit to buy: " + (plan.getBuyCost() < 0 ? "unpriced items" : Budget.format(plan.getBuyCost()))));
		summary.setName("raidSearchSummary");
		summary.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		summary.setToolTipText(combatTimeTooltip(plan));
		return summary;
	}


	/** One total per boost type, even though every planned bottle has its own inventory cell. */
	static String boostSummary(RaidPlan plan)
	{
		Map<Integer, RaidPlan.InventoryItem> boosts = new LinkedHashMap<>();
		plan.getInventory().stream().filter(item -> item.getBottles() > 0)
			.forEach(item -> boosts.putIfAbsent(item.getIds().get(0), item));
		StringBuilder out = new StringBuilder();
		boosts.values().forEach(item -> out.append("<br>").append(BestGearSetupPanel.escape(item.getName())).append(": ")
			.append(item.isReusable() ? "1 reusable item" : item.getEstimatedDoses() + " estimated doses / "
				+ item.getBottles() + " four-dose bottles"));
		return out.toString();
	}

	/** Each boss's share of the raid's estimated combat time, in room order, and what the estimate leaves out. */
	static String combatTimeTooltip(RaidPlan plan)
	{
		Map<String, Double> bosses = new LinkedHashMap<>();
		for (int i = 0; i < plan.getEncounters().size(); i++)
		{
			// A freeze is a duty, not combat time.
			double seconds = plan.getEncounters().get(i).isFreeze() ? 0 : plan.estimatedSeconds(i);
			if (seconds > 0) { bosses.merge(plan.getEncounters().get(i).getBossName(), seconds, Double::sum); }
		}
		StringBuilder out = new StringBuilder("<html>Estimated combat time with the planned setups"
			+ (plan.getPartySize() > 1 ? ", party of " + plan.getPartySize() : "") + ":");
		bosses.forEach((boss, seconds) -> out.append("<br>").append(BestGearSetupPanel.escape(boss)).append(": ").append(duration(seconds)));
		if (plan.getPartySize() > 1) { out.append("<br>Assumes each teammate matches your DPS."); }
		return out.append("<br><br>Puzzles, walking and downtime between rooms are excluded.</html>").toString();
	}

	/** Seconds as "45s", "3m 05s" or "1h 02m"; "unknown" when a setup can't deal damage. */
	static String duration(double seconds)
	{
		if (Double.isNaN(seconds) || Double.isInfinite(seconds)) { return "unknown"; }
		long total = Math.round(seconds);
		if (total < 60) { return total + "s"; }
		if (total < 3600) { return total / 60 + "m " + String.format("%02d", total % 60) + "s"; }
		long minutes = Math.round(seconds / 60);
		return minutes / 60 + "h " + String.format("%02d", minutes % 60) + "m";
	}

	static void detachSelector(JComboBox<RaidEncounter> encounter)
	{
		Object old = encounter.getClientProperty("raidSelectionListener");
		if (old instanceof ActionListener) { encounter.removeActionListener((ActionListener) old); }
		encounter.putClientProperty("raidSelectionListener", null);
	}
}
