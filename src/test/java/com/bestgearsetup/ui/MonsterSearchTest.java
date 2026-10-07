package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.calc.PotionChoice;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import java.awt.event.MouseEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.components.IconTextField;
import org.junit.Test;

public class MonsterSearchTest
{
	private final List<MonsterSummary> searched = new ArrayList<>();
	private final Map<String, Object> saved = new HashMap<>();

	private final BestGearSetupPlugin plugin = new BestGearSetupPlugin()
	{
		@Override
		public java.util.Collection<net.runelite.client.config.ConfigItemDescriptor> getConfigItems()
		{
			return com.bestgearsetup.ConfigItemsForTests.items();
		}

		@Override
		public OwnedItems getOwnedItems()
		{
			try
			{
				Constructor<OwnedItems> constructor = OwnedItems.class.getDeclaredConstructor(ConfigManager.class, ItemManager.class);
				constructor.setAccessible(true);
				return constructor.newInstance(null, null);
			}
			catch (ReflectiveOperationException e)
			{
				throw new AssertionError(e);
			}
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
		public String getPotionChoice(CombatClass cls)
		{
			return PotionChoice.BEST;
		}

		@Override
		public void findBestSetup(MonsterSummary summary)
		{
			searched.add(summary);
		}

		@Override
		public void setConfig(String key, Object value)
		{
			saved.put(key, value);
		}
	};

	private static MonsterSummary summary(String name, int level, boolean boss)
	{
		MonsterSummary m = new MonsterSummary();
		m.setName(name);
		m.setCombatLevel(level);
		m.setBoss(boss);
		return m;
	}

	@SuppressWarnings("unchecked")
	private static <T> T field(BestGearSetupPanel panel, String name) throws ReflectiveOperationException
	{
		Field field = BestGearSetupPanel.class.getDeclaredField(name);
		field.setAccessible(true);
		return (T) field.get(panel);
	}

	private static void click(java.awt.Component row)
	{
		row.dispatchEvent(new MouseEvent(row, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false));
	}

	@Test
	public void clearingSearchResetsTargetResultsAndAllowsANewSearch() throws Exception
	{
		MonsterSummary target = summary("abyssal sire", 350, true);
		GameData data = new GameData(Collections.singletonList(target), Collections.emptyMap(),
			Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				BestGearSetupPanel panel = new BestGearSetupPanel(plugin, new BestGearSetupConfig() {}, null, null, null);
				panel.onDataLoaded(data);
				panel.selectMonster(target, false);
				JPanel results = field(panel, "resultsPanel");
				results.add(new javax.swing.JLabel("Previous setup"));
				panel.clearSearch();
				assertEquals("", ((IconTextField) field(panel, "searchField")).getText());
				assertEquals(0, results.getComponentCount());
				assertEquals(0, ((JPanel) field(panel, "suggestions")).getComponentCount());
				assertEquals(null, field(panel, "selected"));
				assertFalse(((javax.swing.JButton) field(panel, "findButton")).isEnabled());
				assertFalse(((javax.swing.JButton) field(panel, "wikiButton")).isEnabled());
				panel.selectMonster(target, true);
				assertTrue(((javax.swing.JButton) field(panel, "findButton")).isEnabled());
				assertEquals(Collections.singletonList(target), searched);
			}
			catch (ReflectiveOperationException e)
			{
				throw new AssertionError(e);
			}
		});
	}

	@Test
	public void phasesCollapseIntoOneSuggestionWithAVersionPicker() throws Exception
	{
		MonsterSummary phase1 = summary("abyssal sire (phase 1)", 350, true);
		MonsterSummary phase2 = summary("abyssal sire (phase 2)", 350, true);
		MonsterSummary goblin = summary("goblin", 2, false);
		MonsterSummary wizard = summary("dark wizard", 7, false);
		GameData data = new GameData(Arrays.asList(phase1, phase2, goblin, wizard), Collections.emptyMap(),
			Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				BestGearSetupPanel panel = new BestGearSetupPanel(plugin, new BestGearSetupConfig() {}, null, null, null);
				panel.onDataLoaded(data);
				IconTextField search = field(panel, "searchField");
				JPanel suggestions = field(panel, "suggestions");
				JPanel versionRow = field(panel, "versionRow");
				JComboBox<MonsterSummary> versions = field(panel, "versionBox");
				JToggleButton bosses = field(panel, "bossesButton");
				JToggleButton all = field(panel, "allButton");
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
				assertEquals(true, saved.get(BestGearSetupConfig.BOSSES_ONLY_KEY));
				assertEquals("Only the boss matches", 1, suggestions.getComponentCount());
				all.doClick();
				assertEquals(false, saved.get(BestGearSetupConfig.BOSSES_ONLY_KEY));
				assertEquals(2, suggestions.getComponentCount());
			}
			catch (ReflectiveOperationException e)
			{
				throw new AssertionError(e);
			}
		});
	}

	@Test
	public void rightClickedVariantShowsItsSiblings() throws Exception
	{
		MonsterSummary phase1 = summary("abyssal sire (phase 1)", 350, true);
		MonsterSummary phase2 = summary("abyssal sire (phase 2)", 350, true);
		GameData data = new GameData(Arrays.asList(phase1, phase2), Collections.emptyMap(),
			Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				BestGearSetupPanel panel = new BestGearSetupPanel(plugin, new BestGearSetupConfig()
				{
					@Override
					public boolean bossesOnly()
					{
						return true;
					}
				}, null, null, null);
				panel.onDataLoaded(data);
				JToggleButton bosses = field(panel, "bossesButton");
				assertTrue(bosses.isSelected());
				IconTextField search = field(panel, "searchField");
				search.setText("previous target");
				panel.selectMonster(phase2, false);
				JComboBox<MonsterSummary> versions = field(panel, "versionBox");
				assertTrue(((JPanel) field(panel, "versionRow")).isVisible());
				assertEquals(phase2, versions.getSelectedItem());
				assertEquals("Abyssal Sire", search.getText());
				assertEquals(0, ((JPanel) field(panel, "suggestions")).getComponentCount());
				assertTrue("Selecting without running must not search", searched.isEmpty());

				search.setText("another target");
				panel.selectMonster(phase1, true);
				assertEquals("Abyssal Sire", search.getText());
				assertEquals(phase1, versions.getSelectedItem());
				assertEquals(Collections.singletonList(phase1), searched);
			}
			catch (ReflectiveOperationException e)
			{
				throw new AssertionError(e);
			}
		});
	}

	@Test
	public void bossPhasesFollowTheSelectedVersion() throws Exception
	{
		MonsterSummary yama = summary("yama (normal)", 1238, true);
		MonsterSummary enraged = summary("yama (phase 3)", 1238, true);
		MonsterSummary wolf = summary("wolf", 25, false);
		yama.setAttributes(Collections.singletonList("demon"));
		GameData data = new GameData(Arrays.asList(yama, enraged, wolf), Collections.emptyMap(),
			Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		BestGearSetupConfig config = new BestGearSetupConfig()
		{
			@Override
			public EncounterPhase encounterPhase()
			{
				return EncounterPhase.YAMA_MELEE_TANK;
			}
		};
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				BestGearSetupPanel panel = new BestGearSetupPanel(plugin, config, null, null, null);
				panel.onDataLoaded(data);
				JPanel phaseRow = field(panel, "phaseRow");
				JComboBox<EncounterPhase> phases = field(panel, "phaseBox");
				assertFalse(phaseRow.isVisible());

				panel.selectMonster(yama, false);
				assertTrue(phaseRow.isVisible());
				assertEquals(2, phases.getItemCount());
				assertEquals(EncounterPhase.YAMA_MELEE_TANK, phases.getSelectedItem());

				// Yama's enraged phase ignores the tank's style, so there is nothing to pick.
				JComboBox<MonsterSummary> versions = field(panel, "versionBox");
				versions.setSelectedItem(enraged);
				assertFalse(phaseRow.isVisible());

				panel.selectMonster(wolf, false);
				assertFalse(phaseRow.isVisible());
				assertEquals(EncounterPhase.STANDARD, phases.getSelectedItem());
				assertFalse("Showing another target keeps the saved phase", saved.containsKey(BestGearSetupConfig.PHASE_KEY));

				panel.selectMonster(yama, false);
				phases.setSelectedItem(EncounterPhase.STANDARD);
				assertEquals(EncounterPhase.STANDARD, saved.get(BestGearSetupConfig.PHASE_KEY));
			}
			catch (ReflectiveOperationException e)
			{
				throw new AssertionError(e);
			}
		});
	}
}
