package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.Options;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Summary;
import com.bestgearsetup.data.Slot;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.components.IconTextField;
import org.junit.Test;

public class MonsterSearchTest
{
	@org.junit.Before public void trackTips() { HoverTextLengthTest.trackTips(); }
	@org.junit.After public void tipsAreShort() throws Exception { HoverTextLengthTest.assertTrackedTipsShort(); }

	private final List<Summary> searched = new ArrayList<>();
	private final Map<String, Object> saved = new HashMap<>();

	private final BestGearSetupPlugin plugin = new BestGearSetupPlugin()
	{
		@Override
		public net.runelite.client.config.ConfigDescriptor getConfigDescriptor()
		{
			return com.bestgearsetup.ConfigItemsForTests.descriptor();
		}

		@Override
		public OwnedItems getOwnedItems()
		{
			return com.bestgearsetup.ConfigItemsForTests.emptyOwnedItems();
		}

		@Override
		public Map<Slot, SlotLock> getLocks()
		{
			return Collections.emptyMap();
		}

		@Override
		public Set<Integer> getExcluded()
		{
			return Collections.emptySet();
		}

		@Override
		public void findBestSetup(Summary summary)
		{
			searched.add(summary);
		}

		@Override
		public void setConfig(String key, Object value)
		{
			saved.put(key, value);
		}
	};

	private static Summary summary(String name, int level, boolean boss)
	{
		Summary m = new Summary();
		m.setName(name);
		m.setCombatLevel(level);
		m.setBoss(boss);
		return m;
	}

	private static void click(java.awt.Component row)
	{
		row.dispatchEvent(new MouseEvent(row, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false));
	}

	@Test
	public void clearingSearchResetsTargetResultsAndAllowsANewSearch() throws Exception
	{
		Summary target = summary("abyssal sire", 350, true);
		GameData data = new GameData(Collections.singletonList(target), Collections.emptyMap(),
			Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		SwingUtilities.invokeAndWait(() ->
		{
			Sidebar panel = new Sidebar(plugin, new Options() {}, null, null, null);
			panel.onDataLoaded(data);
			panel.selectMonster(target, false);
			JPanel results = panel.resultsPanel;
			results.add(new javax.swing.JLabel("Previous setup"));
			panel.clearSearch();
			assertEquals("", ((IconTextField) panel.searchField).getText());
			assertEquals(0, results.getComponentCount());
			assertEquals(0, ((JPanel) panel.suggestions).getComponentCount());
			assertEquals(null, panel.selected);
			assertFalse(((javax.swing.JButton) panel.findButton).isEnabled());
			assertFalse(((javax.swing.JButton) panel.wikiButton).isEnabled());
			panel.selectMonster(target, true);
			assertTrue(((javax.swing.JButton) panel.findButton).isEnabled());
			assertEquals(Collections.singletonList(target), searched);
		});
	}

	@Test
	public void phasesCollapseIntoOneSuggestionWithAVersionPicker() throws Exception
	{
		Summary phase1 = summary("abyssal sire (phase 1)", 350, true);
		Summary phase2 = summary("abyssal sire (phase 2)", 350, true);
		Summary goblin = summary("goblin", 2, false);
		Summary wizard = summary("dark wizard", 7, false);
		GameData data = new GameData(Arrays.asList(phase1, phase2, goblin, wizard), Collections.emptyMap(),
			Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		SwingUtilities.invokeAndWait(() ->
		{
			Sidebar panel = new Sidebar(plugin, new Options() {}, null, null, null);
			panel.onDataLoaded(data);
			IconTextField search = panel.searchField;
			JPanel suggestions = panel.suggestions;
			JPanel versionRow = panel.versionRow;
			JComboBox<Summary> versions = panel.versionBox;
			JToggleButton bosses = panel.bossesButton;
			JToggleButton all = panel.allButton;
			assertTrue("All monsters is the default", all.isSelected());

			search.setText("abyssal sire");
			assertEquals(1, suggestions.getComponentCount());
			click(suggestions.getComponent(0));
			assertEquals(Collections.singletonList(phase1), searched);
			assertEquals("Abyssal Sire", search.getText());
			assertTrue(versionRow.isVisible());
			assertEquals(2, versions.getItemCount());
			assertEquals(phase1, versions.getSelectedItem());

			versions.setSelectedItem(phase2);
			assertEquals(Arrays.asList(phase1, phase2), searched);
			assertEquals("Abyssal Sire", search.getText());

			// Single-version monsters have nothing to pick.
			search.setText("goblin");
			click(suggestions.getComponent(0));
			assertFalse(versionRow.isVisible());
			assertEquals("Goblin", search.getText());

			search.setText("r");
			assertEquals(2, suggestions.getComponentCount());
			bosses.doClick();
			assertEquals(true, saved.get(Options.BOSSES_ONLY_KEY));
			assertEquals("Only the boss matches", 1, suggestions.getComponentCount());
			all.doClick();
			assertEquals(false, saved.get(Options.BOSSES_ONLY_KEY));
			assertEquals(2, suggestions.getComponentCount());
		});
	}

	@Test
	public void rightClickedVariantShowsItsSiblings() throws Exception
	{
		Summary phase1 = summary("abyssal sire (phase 1)", 350, true);
		Summary phase2 = summary("abyssal sire (phase 2)", 350, true);
		GameData data = new GameData(Arrays.asList(phase1, phase2), Collections.emptyMap(),
			Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		SwingUtilities.invokeAndWait(() ->
		{
			Sidebar panel = new Sidebar(plugin, new Options()
			{
				@Override
				public boolean bossesOnly()
				{
					return true;
				}
			}, null, null, null);
			panel.onDataLoaded(data);
			JToggleButton bosses = panel.bossesButton;
			assertTrue(bosses.isSelected());
			IconTextField search = panel.searchField;
			search.setText("previous target");
			panel.selectMonster(phase2, false);
			JComboBox<Summary> versions = panel.versionBox;
			assertTrue(((JPanel) panel.versionRow).isVisible());
			assertEquals(phase2, versions.getSelectedItem());
			assertEquals("Abyssal Sire", search.getText());
			assertEquals(0, ((JPanel) panel.suggestions).getComponentCount());
			assertTrue("Selecting without running must not search", searched.isEmpty());

			search.setText("another target");
			panel.selectMonster(phase1, true);
			assertEquals("Abyssal Sire", search.getText());
			assertEquals(phase1, versions.getSelectedItem());
			assertEquals(Collections.singletonList(phase1), searched);
		});
	}

	@Test
	public void bossPhasesFollowTheSelectedVersion() throws Exception
	{
		Summary yama = summary("yama (normal)", 1238, true);
		Summary enraged = summary("yama (phase 3)", 1238, true);
		Summary wolf = summary("wolf", 25, false);
		yama.setAttributes(Collections.singletonList("demon"));
		GameData data = new GameData(Arrays.asList(yama, enraged, wolf), Collections.emptyMap(),
			Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		Options config = new Options()
		{
			@Override
			public EncounterPhase encounterPhase()
			{
				return EncounterPhase.YAMA_MELEE_TANK;
			}
		};
		SwingUtilities.invokeAndWait(() ->
		{
			Sidebar panel = new Sidebar(plugin, config, null, null, null);
			panel.onDataLoaded(data);
			JPanel phaseRow = panel.phaseRow;
			JComboBox<EncounterPhase> phases = panel.phaseBox;
			assertFalse(phaseRow.isVisible());

			panel.selectMonster(yama, false);
			assertTrue(phaseRow.isVisible());
			assertEquals(2, phases.getItemCount());
			assertEquals(EncounterPhase.YAMA_MELEE_TANK, phases.getSelectedItem());

			// Yama's enraged phase ignores the tank's style, so there is nothing to pick.
			JComboBox<Summary> versions = panel.versionBox;
			versions.setSelectedItem(enraged);
			assertFalse(phaseRow.isVisible());

			panel.selectMonster(wolf, false);
			assertFalse(phaseRow.isVisible());
			assertEquals(EncounterPhase.STANDARD, phases.getSelectedItem());
			assertFalse("Showing another target keeps the saved phase", saved.containsKey(Options.PHASE_KEY));

			panel.selectMonster(yama, false);
			phases.setSelectedItem(EncounterPhase.STANDARD);
			assertEquals(EncounterPhase.STANDARD, saved.get(Options.PHASE_KEY));
		});
	}
}
