package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
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
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class FightOptionsPanelTest
{
	@Test
	public void encounterControlsFollowTheTargetAndPreserveSavedValues() throws Exception
	{
		Map<String, Object> writes = new HashMap<>();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override
			public java.util.Collection<net.runelite.client.config.ConfigItemDescriptor> getConfigItems()
			{
				return com.bestgearsetup.ConfigItemsForTests.items();
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
			for (String key : Arrays.asList("toaRaidLevel", "toaPathLevel", "raidPartySize", "coxChallengeMode",
				"miningLevel", "requireFireProtection", "antifire", "protectMagic", "aoeTargets", "forinthrySurge"))
			{
				assertFalse(key, control(panel, key + "Row").isVisible());
			}
			panel.setTarget(target("kephri", "tombs of amascut"));
			assertTrue(control(panel, "toaRaidLevelRow").isVisible());
			assertTrue(control(panel, "toaPathLevelRow").isVisible());
			assertTrue(control(panel, "raidPartySizeRow").isVisible());
			assertFalse(control(panel, "coxChallengeModeRow").isVisible());
			panel.setTarget(target("elidinis' warden", "tombs of amascut"));
			assertTrue(control(panel, "toaRaidLevelRow").isVisible());
			assertFalse(control(panel, "toaPathLevelRow").isVisible());
			panel.setTarget(target("guardian", "xerician"));
			assertTrue(control(panel, "miningLevelRow").isVisible());
			assertTrue(control(panel, "coxChallengeModeRow").isVisible());
			assertFalse(control(panel, "toaRaidLevelRow").isVisible());
			panel.setTarget(target("vorkath", "dragon"));
			assertTrue(control(panel, "antifireRow").isVisible());
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
	public void restoresSavedFightAssumptionsWithoutWritingOrTriggeringSearches() throws Exception
	{
		Map<String, Object> saved = new HashMap<>();
		saved.put("specDwh", 3);
		saved.put("ammoCount", 1000);
		BestGearSetupConfig config = new BestGearSetupConfig()
		{
			@Override
			public int specDwh()
			{
				return (int) saved.get("specDwh");
			}

			@Override
			public int ammoCount()
			{
				return (int) saved.get("ammoCount");
			}

			@Override
			public boolean specVulnerability()
			{
				return true;
			}
		};
		Map<String, Object> writes = new HashMap<>();
		int[] measurements = {0};
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override
			public java.util.Collection<net.runelite.client.config.ConfigItemDescriptor> getConfigItems()
			{
				return com.bestgearsetup.ConfigItemsForTests.items();
			}

			@Override
			public void useCurrentDistance()
			{
				measurements[0]++;
			}

			@Override
			public void setConfig(String key, Object value)
			{
				writes.put(key, value);
			}
		};
		SwingUtilities.invokeAndWait(() ->
		{
			FightOptionsPanel panel = new FightOptionsPanel(plugin, config, com.bestgearsetup.ConfigItemsForTests.items());
			JSpinner hits = (JSpinner) control(panel, "specDwh");
			JSpinner ammo = (JSpinner) control(panel, "ammoCount");
			JSpinner distance = (JSpinner) control(panel, "targetDistance");
			JCheckBox vulnerability = (JCheckBox) control(panel, "specVulnerability");
			assertEquals(3, hits.getValue());
			assertEquals(1000, ammo.getValue());
			assertEquals(0, distance.getValue());
			assertTrue(vulnerability.isSelected());
			assertTrue(writes.isEmpty());
			JCheckBox aoe = (JCheckBox) control(panel, "aoe");
			JSpinner targets = (JSpinner) control(panel, "aoeTargets");
			JComboBox<?> antifire = (JComboBox<?>) control(panel, "antifire");
			assertEquals(false, aoe.isSelected());
			assertEquals(9, targets.getValue());
			assertEquals(Antifire.SUPER, antifire.getSelectedItem());

			saved.put("specDwh", 7);
			panel.refresh();
			assertEquals(7, hits.getValue());
			assertTrue(writes.isEmpty());

			hits.setValue(8);
			assertEquals(8, writes.get("specDwh"));
			assertEquals(1, writes.size());
			distance.setValue(6);
			assertEquals(6, writes.get(BestGearSetupConfig.DISTANCE_KEY));
			assertEquals(2, writes.size());
			((JButton) control(panel, "currentDistance")).doClick();
			assertEquals(1, measurements[0]);
			aoe.doClick();
			targets.setValue(3);
			antifire.setSelectedItem(Antifire.REGULAR);
			assertEquals(true, writes.get("aoe"));
			assertEquals(3, writes.get("aoeTargets"));
			assertEquals(Antifire.REGULAR, writes.get("antifire"));
		});
	}
}
