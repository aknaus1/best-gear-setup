package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.RaidOptions;
import com.bestgearsetup.calc.Antifire;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.MonsterSummary;
import java.util.Arrays;
import java.awt.Component;
import java.awt.Container;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class FightOptionsPanelTest
{
	@Test public void toaPlanningControlsPersistAndRemainVisibleOnPuzzleTargets() throws Exception
	{
		Map<String, Object> writes = new HashMap<>();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override public void setConfig(String key, Object value) { writes.put(key, value); }
		};
		SwingUtilities.invokeAndWait(() ->
		{
			FightOptionsPanel panel = new FightOptionsPanel(plugin, new BestGearSetupConfig() {}, com.bestgearsetup.ConfigItemsForTests.items());
			panel.setTarget(target("baboon brawler (level 56)", "tombs of amascut"));
			panel.setRaidPlan(com.bestgearsetup.RaidPlanType.TOA);
			assertTrue(control(panel, "toaStartingPathLevelRow").isVisible());
			assertTrue(control(panel, "toaWalkThePathRow").isVisible());
			assertFalse(control(panel, "toaPathLevelRow").isVisible());
			assertTrue(writes.isEmpty());
			((JSpinner) control(panel, "toaStartingPathLevel")).setValue(3);
			((JCheckBox) control(panel, "toaWalkThePath")).doClick();
			assertEquals(3, writes.get("toaStartingPathLevel"));
			assertEquals(true, writes.get("toaWalkThePath"));
			panel.setTarget(target("kephri", "tombs of amascut"));
			assertFalse(control(panel, "toaPathLevelRow").isVisible());
			panel.setRaidPlan(com.bestgearsetup.RaidPlanType.SINGLE);
			assertTrue(control(panel, "toaPathLevelRow").isVisible());
			assertFalse(control(panel, "toaStartingPathLevelRow").isVisible());
			assertFalse(control(panel, "toaWalkThePathRow").isVisible());
		});
	}

	@Test public void tobSelectorOffersFourRolesAndSavesTheSelection() throws Exception
	{
		Map<String, Object> writes = new HashMap<>();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override public void setConfig(String key, Object value) { writes.put(key, value); }
		};
		SwingUtilities.invokeAndWait(() ->
		{
			FightOptionsPanel panel = new FightOptionsPanel(plugin, new BestGearSetupConfig() {}, com.bestgearsetup.ConfigItemsForTests.items());
			panel.setRaidPlan(com.bestgearsetup.RaidPlanType.TOB);
			JComboBox<?> roles = (JComboBox<?>) control(panel, "tobRole");
			assertEquals(4, roles.getItemCount());
			assertEquals(RaidOptions.TobRole.MELEE_DPS, roles.getSelectedItem());
			assertTrue(writes.isEmpty());
			for (RaidOptions.TobRole role : RaidOptions.TobRole.values())
			{
				roles.setSelectedItem(role);
				assertEquals(role, writes.get("tobRole"));
				JComboBox<?> book = (JComboBox<?>) control(panel, "raidSpellbook");
				assertEquals(!role.freezes(), book.isEnabled());
				assertEquals(role.freezes() ? com.bestgearsetup.RaidSpellbook.ANCIENT : com.bestgearsetup.RaidSpellbook.AUTO,
					book.getSelectedItem());
				assertFalse(writes.containsKey("raidSpellbook"));
			}
			assertTrue(control(panel, "tobMaidenRole") == null);
			assertTrue(control(panel, "tobNylocasRole") == null);
		});
	}

	@Test
	public void wildernessRiskControlsShowTogetherAndSaveTheirValues() throws Exception
	{
		Map<String, Object> writes = new HashMap<>();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override
			public void setConfig(String key, Object value)
			{
				writes.put(key, value);
			}
		};
		SwingUtilities.invokeAndWait(() ->
		{
			FightOptionsPanel panel = new FightOptionsPanel(plugin, new BestGearSetupConfig() {},
				com.bestgearsetup.ConfigItemsForTests.items());
			assertFalse(control(panel, "limitWildernessRiskRow").isVisible());
			assertFalse(control(panel, "maxExpensiveItemsRow").isVisible());
			assertFalse(control(panel, "expensiveItemThresholdRow").isVisible());
			assertTrue(writes.isEmpty());
			((JCheckBox) control(panel, "wilderness")).doClick();
			assertTrue(control(panel, "limitWildernessRiskRow").isVisible());
			assertFalse(control(panel, "maxExpensiveItemsRow").isVisible());
			((JCheckBox) control(panel, "limitWildernessRisk")).doClick();
			assertTrue(control(panel, "maxExpensiveItemsRow").isVisible());
			assertTrue(control(panel, "expensiveItemThresholdRow").isVisible());
			assertEquals(3, ((JSpinner) control(panel, "maxExpensiveItems")).getValue());
			JTextField threshold = (JTextField) control(panel, "expensiveItemThreshold");
			assertEquals("1m", threshold.getText());
			((JSpinner) control(panel, "maxExpensiveItems")).setValue(0);
			threshold.setText("500k");
			threshold.postActionEvent();
			assertEquals(0, writes.get("maxExpensiveItems"));
			assertEquals("500k", writes.get("expensiveItemThreshold"));
			((JCheckBox) control(panel, "wilderness")).doClick();
			assertFalse(control(panel, "limitWildernessRiskRow").isVisible());
			assertFalse(control(panel, "maxExpensiveItemsRow").isVisible());
			assertFalse(control(panel, "expensiveItemThresholdRow").isVisible());
			assertEquals("500k", threshold.getText());
		});
	}

	@Test
	public void encounterControlsFollowTheTargetAndPreserveSavedValues() throws Exception
	{
		Map<String, Object> writes = new HashMap<>();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override
			public net.runelite.client.config.ConfigDescriptor getConfigDescriptor()
			{
				return com.bestgearsetup.ConfigItemsForTests.descriptor();
			}

			@Override
			public void setConfig(String key, Object value)
			{
				writes.put(key, value);
			}
		};
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
			FightOptionsPanel panel = new FightOptionsPanel(plugin, config, com.bestgearsetup.ConfigItemsForTests.items());
			panel.setTarget(target("wolf"));
			assertFalse(control(panel, "raidScalingSection").getParent().isVisible());
			assertTrue(control(panel, "protectionSection") == null);
			for (String key : Arrays.asList("toaRaidLevel", "toaPathLevel", "raidPartySize", "coxChallengeMode",
				"miningLevel", "aoeTargets", "forinthrySurge"))
			{
				assertFalse(key, control(panel, key + "Row").isVisible());
			}
			panel.setTarget(target("kephri", "tombs of amascut"));
			assertTrue(control(panel, "raidScalingSection").getParent().isVisible());
			assertTrue(control(panel, "toaRaidLevelRow").isVisible());
			assertTrue(control(panel, "toaPathLevelRow").isVisible());
			assertTrue(control(panel, "raidPartySizeRow").isVisible());
			assertFalse(control(panel, "coxChallengeModeRow").isVisible());
			// Path order and roles only change complete raid plans.
			assertFalse(control(panel, "toaPathOrderRow").isVisible());
			panel.setRaidPlan(com.bestgearsetup.RaidPlanType.TOA);
			assertTrue(control(panel, "toaPathOrderRow").isVisible());
			assertFalse(control(panel, "tobRoleRow").isVisible());
			panel.setRaidPlan(com.bestgearsetup.RaidPlanType.TOB);
			assertFalse(control(panel, "toaPathOrderRow").isVisible());
			assertTrue(control(panel, "tobRoleRow").isVisible());
			panel.setRaidPlan(com.bestgearsetup.RaidPlanType.SINGLE);
			assertFalse(control(panel, "tobRoleRow").isVisible());
			// ToA plans start on the Apmeken baboons, which the raid doesn't scale: the Raid section stays open.
			panel.setTarget(target("baboon brawler (level 56)", "tombs of amascut"));
			assertFalse(control(panel, "raidScalingSection").getParent().isVisible());
			panel.setRaidPlan(com.bestgearsetup.RaidPlanType.TOA);
			assertTrue(control(panel, "raidScalingSection").getParent().isVisible());
			assertTrue(control(panel, "toaPathOrderRow").isVisible());
			assertTrue(control(panel, "raidPartySizeRow").isVisible());
			assertTrue(control(panel, "toaRaidLevelRow").isVisible());
			assertTrue(control(panel, "raidSpellbookRow").isVisible());
			panel.setRaidPlan(com.bestgearsetup.RaidPlanType.SINGLE);
			panel.setTarget(target("elidinis' warden", "tombs of amascut"));
			assertTrue(control(panel, "toaRaidLevelRow").isVisible());
			assertFalse(control(panel, "toaPathLevelRow").isVisible());
			panel.setTarget(target("guardian", "xerician"));
			assertTrue(control(panel, "miningLevelRow").isVisible());
			assertTrue(control(panel, "coxChallengeModeRow").isVisible());
			assertFalse(control(panel, "toaRaidLevelRow").isVisible());
			panel.setTarget(target("vorkath", "dragon"));
			assertTrue(control(panel, "protectionSection") == null);
			assertFalse(control(panel, "raidScalingSection").getParent().isVisible());
			assertTrue(control(panel, "antifireRow") == null);
			// The boss phase is picked below the monster search instead.
			assertTrue(control(panel, "encounterPhaseRow") == null);
			assertTrue(writes.isEmpty());
			((JCheckBox) control(panel, "aoe")).doClick();
			assertTrue(control(panel, "aoeTargetsRow").isVisible());
		});
	}

	private static MonsterSummary target(String name, String... attributes)
	{
		MonsterSummary target = new MonsterSummary();
		target.setName(name);
		target.setAttributes(Arrays.asList(attributes));
		return target;
	}

	private static Component control(Container parent, String name)
	{
		for (Component component : parent.getComponents())
		{
			if (name.equals(component.getName()))
			{
				return component;
			}
			if (component instanceof Container)
			{
				Component found = control((Container) component, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	@Test
	public void preFightPreparationIsCollapsedAndCountsWhatIsSet() throws Exception
	{
		Map<String, Object> writes = new HashMap<>();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin() {
			@Override public void setConfig(String key, Object value) { writes.put(key, value); }
		};
		BestGearSetupConfig config = new BestGearSetupConfig() { @Override public int specDwh() { return 2; } };
		SwingUtilities.invokeAndWait(() -> {
			FightOptionsPanel panel = new FightOptionsPanel(plugin, config, com.bestgearsetup.ConfigItemsForTests.items());
			assertFalse("Collapsed by default", control(panel, "preparationContent").isVisible());
			JButton toggle = (JButton) control(panel, "preparationToggle");
			assertEquals("Pre-fight preparation (1)", toggle.getText());
			assertEquals(2, ((JSpinner) control(panel, "specDwh")).getValue());
			assertFalse("Tome only applies with Vulnerability", control(panel, "specTomeOfWaterRow").isVisible());
			((JCheckBox) control(panel, "specVulnerability")).doClick();
			assertEquals(true, writes.get("specVulnerability"));
			assertTrue(control(panel, "specTomeOfWaterRow").isVisible());
			assertEquals("Pre-fight preparation (2)", toggle.getText());
			((JSpinner) control(panel, "specBgsDamage")).setValue(40);
			assertEquals(40, writes.get("specBgsDamage"));
			assertEquals("Pre-fight preparation (3)", toggle.getText());
			// Opening and closing it must leave the always-open sections showing.
			assertTrue(control(panel, "fightConditionsContent").isVisible());
			toggle.doClick();
			assertTrue(control(panel, "preparationContent").isVisible());
			assertTrue("Fight conditions stay open", control(panel, "fightConditionsContent").isVisible());
			assertTrue("Raid scaling stays open", control(panel, "raidScalingContent").isVisible());
			toggle.doClick();
			assertFalse(control(panel, "preparationContent").isVisible());
			assertTrue(control(panel, "fightConditionsContent").isVisible());
			assertEquals("Spinners share one width", ((JSpinner) control(panel, "specDwh")).getPreferredSize().width,
				((JSpinner) control(panel, "specBgsDamage")).getPreferredSize().width);
		});
	}

	@Test
	public void restoresPlanningValuesWithoutWritingAndMovedSettingsAreAbsent() throws Exception
	{
		Map<String, Object> writes = new HashMap<>();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin() {
			@Override public void setConfig(String key, Object value) { writes.put(key, value); }
		};
		BestGearSetupConfig config = new BestGearSetupConfig() { @Override public int soulreaperStacks() { return 3; } };
		SwingUtilities.invokeAndWait(() -> {
			FightOptionsPanel panel = new FightOptionsPanel(plugin, config, com.bestgearsetup.ConfigItemsForTests.items());
			assertEquals(3, ((JSpinner) control(panel, "soulreaperStacks")).getValue());
			assertTrue(writes.isEmpty());
			assertTrue(control(panel, "ammoCount") == null);
			assertTrue(control(panel, "antifire") == null);
			assertTrue(control(panel, "targetDistance") == null);
			assertTrue(control(panel, "currentDistance") == null);
			assertTrue(control(panel, "monsterHitpoints") == null);
			((JSpinner) control(panel, "soulreaperStacks")).setValue(4);
			assertEquals(4, writes.get("soulreaperStacks"));
			panel.refresh();
			assertEquals(3, ((JSpinner) control(panel, "soulreaperStacks")).getValue());
			assertEquals(1, writes.size());
		});
	}

	@Test
	public void fightSettingsMovedFromSettingsAreEditableHere() throws Exception
	{
		Map<String, Object> writes = new HashMap<>();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin() {
			@Override public void setConfig(String key, Object value) { writes.put(key, value); }
		};
		SwingUtilities.invokeAndWait(() -> {
			FightOptionsPanel panel = new FightOptionsPanel(plugin, new BestGearSetupConfig() {},
				com.bestgearsetup.ConfigItemsForTests.items());
			Component conditions = control(panel, "fightConditionsContent");
			for (String key : Arrays.asList("activeEncounter", "thrall"))
			{
				assertTrue(key, SwingUtilities.isDescendingFrom(control(panel, key), conditions));
			}
			Component specials = control(panel, "specialAttacksContent");
			for (String key : Arrays.asList("killSpecials", "specEnergy", "drainSpecs"))
			{
				assertTrue(key, SwingUtilities.isDescendingFrom(control(panel, key), specials));
			}
			// Kit settings apply to raids and boss fights alike, so they sit outside the Raid section.
			Component kit = control(panel, "kitContent");
			for (String key : Arrays.asList("raidEquipmentLimit", "raidSpellbook", "quickSwitchLimit"))
			{
				assertTrue(key, SwingUtilities.isDescendingFrom(control(panel, key), kit));
				assertFalse(key, SwingUtilities.isDescendingFrom(control(panel, key), control(panel, "raidScalingContent")));
			}
			assertFalse("Kit settings only apply to plans", control(panel, "kitSection").getParent().isVisible());
			panel.setTarget(target("zulrah (serpentine)"));
			panel.setRaidPlan(com.bestgearsetup.RaidPlanType.ZULRAH);
			assertTrue(control(panel, "kitSection").getParent().isVisible());
			assertTrue(control(panel, "quickSwitchLimitRow").isVisible());
			assertFalse("A boss fight has no raid scaling", control(panel, "raidScalingSection").getParent().isVisible());
			panel.setRaidPlan(com.bestgearsetup.RaidPlanType.TOB);
			assertTrue(control(panel, "kitSection").getParent().isVisible());
			assertTrue(control(panel, "raidScalingSection").getParent().isVisible());
			panel.setRaidPlan(com.bestgearsetup.RaidPlanType.SINGLE);
			assertTrue(control(panel, "specEnergyRow").isVisible());
			((JCheckBox) control(panel, "killSpecials")).doClick();
			assertEquals(false, writes.get("killSpecials"));
			assertFalse("Spec options only apply with specials on", control(panel, "specEnergyRow").isVisible());
			assertFalse(control(panel, "drainSpecsRow").isVisible());
		});
	}

	@Test
	public void wildernessOnlyTargetsShowWildernessOnWithoutSavingIt() throws Exception
	{
		Map<String, Object> writes = new HashMap<>();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin() {
			@Override public void setConfig(String key, Object value) { writes.put(key, value); }
		};
		SwingUtilities.invokeAndWait(() -> {
			FightOptionsPanel panel = new FightOptionsPanel(plugin, new BestGearSetupConfig() {},
				com.bestgearsetup.ConfigItemsForTests.items());
			JCheckBox wilderness = (JCheckBox) control(panel, "wilderness");
			panel.setTarget(target("mammoth"));
			assertTrue(wilderness.isSelected());
			assertFalse("The search forces it, so it can't be turned off", wilderness.isEnabled());
			assertTrue(control(panel, "limitWildernessRiskRow").isVisible());
			panel.setTarget(target("revenant knight"));
			assertTrue(control(panel, "forinthrySurgeRow").isVisible());
			panel.setTarget(target("wolf"));
			assertFalse("Back to the saved value", wilderness.isSelected());
			assertTrue(wilderness.isEnabled());
			assertFalse(control(panel, "limitWildernessRiskRow").isVisible());
			assertTrue(writes.isEmpty());
		});
	}
}
