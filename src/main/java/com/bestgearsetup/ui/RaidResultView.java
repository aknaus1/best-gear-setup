package com.bestgearsetup.ui;

import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.Budget;
import com.bestgearsetup.RaidEncounter;
import com.bestgearsetup.RaidPlan;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.data.MonsterSummary;
import java.awt.Color;
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
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		long equipment = plan.getInventory().stream().filter(RaidPlan.InventoryItem::isEquipment).count();
		JLabel summary = new JLabel(BestGearSetupPanel.html("Start wearing <b>" + plan.getEncounters().get(0).getLabel()
			+ "</b> gear.<br>Equipment switches: " + equipment + " / " + plan.getEquipmentLimit()
			+ "<br>Spellbook: " + plan.getSpellbook()
			+ "<br>Inventory: " + plan.getInventory().size() + " / 28. Fill empty slots with supplies."
			+ "<br>Shared kit to buy: " + (plan.getBuyCost() < 0 ? "unpriced items" : Budget.format(plan.getBuyCost()))));
		summary.setForeground(Color.WHITE);
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
			if (activeResult != null && active != null && current.getTarget().getName().equals(active.getName())
				&& current.getPhase() == activeResult.getMonster().getPhase())
			{ result = activeResult; }
			java.util.List<RaidPlan.InventoryItem> carried = plan.inventoryFor(result.getByType().values().iterator().next().get(0));
			inventorySection.showItems(carried);
			selected.accept(plan.getEncounters().get(index));
			JLabel stats = new JLabel(BestGearSetupPanel.html("Defence: " + result.getBaseDefence() + " → "
				+ result.getMonster().getDefenceLevel() + (result.getRaidScaling() == null ? "" : "<br>" + BestGearSetupPanel.escape(result.getRaidScaling()))));
			stats.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			detail.add(new ResultView(result, plugin, items, sprites, skills, ammoCount, false, inventorySection));
			detail.add(BestGearSetupPanel.left(stats));
			detail.revalidate();
			detail.repaint();
		};
		RaidEncounter initial = plan.getEncounters().get(0);
		if (active != null)
		{
			for (RaidEncounter e : plan.getEncounters())
			{
				if (e.getTarget().getName().equals(active.getName())
					&& (activeResult == null || e.getPhase() == activeResult.getMonster().getPhase()))
				{ initial = e; break; }
			}
		}
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
		add(BestGearSetupPanel.left(summary));
		show.run();
		plugin.setBankHighlightedRaid(plan);
	}

	static String inventoryTooltip(RaidPlan.InventoryItem item) { return InventoryView.tooltip(item); }

	static void detachSelector(JComboBox<RaidEncounter> encounter)
	{
		Object old = encounter.getClientProperty("raidSelectionListener");
		if (old instanceof ActionListener) { encounter.removeActionListener((ActionListener) old); }
		encounter.putClientProperty("raidSelectionListener", null);
	}
}
