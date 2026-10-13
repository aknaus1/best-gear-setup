package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.ConfigItemsForTests;
import com.bestgearsetup.RaidEncounter;
import com.bestgearsetup.RaidPlan;
import com.bestgearsetup.RaidPlanType;
import com.bestgearsetup.RaidSpellbook;
import com.bestgearsetup.RaidStyle;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;

public class RaidPanelTest
{
	@Test public void selectingRaidStartsAtFirstBossAndManualSelectionReturnsToSingle() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		BestGearSetupPlugin plugin = mock(BestGearSetupPlugin.class);
		net.runelite.client.config.ConfigDescriptor descriptor = ConfigItemsForTests.descriptor();
		when(plugin.getConfigDescriptor()).thenReturn(descriptor);
		when(plugin.getOwnedItems()).thenReturn(ConfigItemsForTests.emptyOwnedItems());
		BestGearSetupPanel[] holder = {null};
		doAnswer(call -> { holder[0].clearSearch(); return null; }).when(plugin).clearGearSearch();
		SwingUtilities.invokeAndWait(() ->
		{
			holder[0] = new BestGearSetupPanel(plugin, new BestGearSetupConfig() {}, null, null, null);
			holder[0].onDataLoaded(data);
			List<Component> flow = components(holder[0]);
			Component target = flow.stream().filter(c -> "targetChoice".equals(c.getName())).findFirst().get();
			assertTrue(flow.stream().noneMatch(c -> "raidPlanType".equals(c.getName())));
			assertTrue(flow.indexOf(holder[0].searchField) < flow.indexOf(target));
			assertTrue(flow.indexOf(target) < flow.indexOf(holder[0].versionRow));
			assertTrue(flow.indexOf(holder[0].versionRow) < flow.indexOf(holder[0].modeBox));
			assertTrue(flow.indexOf(holder[0].modeBox) < flow.indexOf(holder[0].findButton));
			javax.swing.JProgressBar bar = (javax.swing.JProgressBar) components(holder[0]).stream()
				.filter(c -> "searchProgress".equals(c.getName())).findFirst().get();
			holder[0].showSearching(RaidPlanType.COX_CM.encounters(data).get(0).getTarget());
			holder[0].showProgress(45);
			holder[0].showEncounterProgress("Next encounter");
			assertEquals(45, bar.getValue());
			holder[0].showProgress(20);
			assertEquals(45, bar.getValue());
			holder[0].showSearching(RaidPlanType.TOA.encounters(data).get(0).getTarget());
			assertEquals(0, bar.getValue());
			holder[0].searchField.setText("CM CoX");
			((javax.swing.JTextField) components(holder[0].searchField).stream().filter(c -> c instanceof javax.swing.JTextField).findFirst().get()).postActionEvent();
			assertEquals(RaidPlanType.COX_CM, holder[0].getRaidType());
			assertEquals("tekton (normal)", holder[0].selected.getName());
			verify(plugin).findBestSetup(holder[0].selected);
			holder[0].searchField.setText("Tombs of Amascut");
			((javax.swing.JTextField) components(holder[0].searchField).stream().filter(c -> c instanceof javax.swing.JTextField).findFirst().get()).postActionEvent();
			assertEquals(RaidPlanType.TOA, holder[0].getRaidType());
			assertEquals("baboon brawler (level 56)", holder[0].selected.getName());
			holder[0].searchField.setText("Theatre of Blood");
			((javax.swing.JTextField) components(holder[0].searchField).stream().filter(c -> c instanceof javax.swing.JTextField).findFirst().get()).postActionEvent();
			assertEquals(RaidPlanType.TOB, holder[0].getRaidType());
			assertEquals("the maiden of sugadinti (normal)", holder[0].selected.getName());
			holder[0].selectMonster(RaidPlanType.TOB.encounters(data).get(0).getTarget(), false);
			assertEquals(RaidPlanType.SINGLE, holder[0].getRaidType());
		});
	}

	@Test public void encounterSwitchKeepsCompleteBankKitAndShowsTwentyEightInventoryCells() throws Exception
	{
		BestGearSetupPlugin plugin = mock(BestGearSetupPlugin.class);
		when(plugin.getOwnedItems()).thenReturn(ConfigItemsForTests.emptyOwnedItems());
		ItemManager images = mock(ItemManager.class);
		when(images.getImage(anyInt())).thenReturn(mock(AsyncBufferedImage.class));
		MonsterSummary one = new MonsterSummary(); one.setName("first boss"); one.setId(1);
		MonsterSummary two = new MonsterSummary(); two.setName("second boss"); two.setId(2);
		GearItem first = new GearItem(); first.setId(100); first.setName("first weapon"); first.setSlot(Slot.WEAPON);
		GearItem second = new GearItem(); second.setId(101); second.setName("second weapon"); second.setSlot(Slot.WEAPON);
		SetupResult a = setup(first); SetupResult b = setup(second);
		SearchResults resultA = result(a); SearchResults resultB = result(b);
		RaidPlan plan = new RaidPlan(RaidPlanType.TOB, RaidSpellbook.ARCEUUS,
			Arrays.asList(new RaidEncounter("First", one, new AttackStyle.Type[0]), new RaidEncounter("Second", two, new AttackStyle.Type[0])),
			Arrays.asList(resultA, resultB), Arrays.asList(a, b), Arrays.asList(first, second),
			Collections.singletonList(new RaidPlan.InventoryItem("second weapon", Collections.singletonList(101), true)), 10, 0);
		SwingUtilities.invokeAndWait(() ->
		{
			JComboBox<RaidEncounter> selector = new JComboBox<>();
			RaidResultView view = new RaidResultView(plan, one, null, plugin, images, mock(SpriteCache.class), null, 0, selector, new JComboBox<>(), encounter -> { });
			List<Component> components = components(view);
			Component gear = components.stream().filter(c -> c instanceof EquipmentGrid).findFirst().get();
			JPanel inventory = (JPanel) components.stream().filter(c -> "raidInventory".equals(c.getName())).findFirst().get();
			assertEquals(28, inventory.getComponentCount());
			assertTrue(components.indexOf(gear) < components.indexOf(inventory));
			assertTrue(((javax.swing.JComponent) inventory.getComponent(0)).getToolTipText().contains("Second Weapon"));
			javax.swing.JButton toggle = (javax.swing.JButton) components.stream().filter(c -> "raidInventoryToggle".equals(c.getName())).findFirst().get();
			assertTrue("The inventory starts minimized", !inventory.isVisible());
			toggle.doClick();
			assertTrue(inventory.isVisible());
			selector.setSelectedIndex(1);
			assertTrue("Switching encounters keeps it expanded", inventory.isVisible());
			toggle.doClick();
			assertTrue(!inventory.isVisible());
			selector.setSelectedIndex(0);
			assertTrue(!inventory.isVisible());
			selector.setSelectedIndex(1);
			toggle.doClick();
			assertTrue(inventory.isVisible());
			assertTrue(components(view).contains(inventory));
			assertTrue(((javax.swing.JComponent) inventory.getComponent(0)).getToolTipText().contains("First Weapon"));
			// A re-planned raid keeps the expanded inventory, and then the minimized one.
			RaidResultView expanded = new RaidResultView(plan, two, null, plugin, images, mock(SpriteCache.class), null, 0, selector, new JComboBox<>(), encounter -> { });
			assertTrue(((JPanel) components(expanded).stream().filter(c -> "raidInventory".equals(c.getName())).findFirst().get()).isVisible());
			toggle.doClick();
			RaidResultView refreshed = new RaidResultView(plan, two, null, plugin, images, mock(SpriteCache.class), null, 0, selector, new JComboBox<>(), encounter -> { });
			JPanel refreshedInventory = (JPanel) components(refreshed).stream().filter(c -> "raidInventory".equals(c.getName())).findFirst().get();
			assertTrue(!refreshedInventory.isVisible());
			verify(plugin, org.mockito.Mockito.times(3)).setBankHighlightedRaid(plan);
			verify(plugin, never()).setBankHighlightedSetup(any(), any());
		});
	}

	@Test public void hoveringPouchListsItsPackedRunes()
	{
		RaidPlan.InventoryItem fire = new RaidPlan.InventoryItem("Fire runes", Collections.singletonList(554), false);
		RaidPlan.InventoryItem blood = new RaidPlan.InventoryItem("Blood runes", Collections.singletonList(565), false);
		RaidPlan.InventoryItem pouch = new RaidPlan.InventoryItem("Rune pouch", Collections.singletonList(12791), false, Arrays.asList(fire, blood));
		String tooltip = RaidResultView.inventoryTooltip(pouch);
		assertTrue(tooltip.contains("Fire runes"));
		assertTrue(tooltip.contains("Blood runes"));
	}

	@Test public void currentBossAboveVersionTracksTheDisplayedRaidEquipment() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		BestGearSetupPlugin plugin = mock(BestGearSetupPlugin.class);
		net.runelite.client.config.ConfigDescriptor descriptor = ConfigItemsForTests.descriptor();
		when(plugin.getConfigDescriptor()).thenReturn(descriptor);
		when(plugin.getOwnedItems()).thenReturn(ConfigItemsForTests.emptyOwnedItems());
		ItemManager images = mock(ItemManager.class);
		when(images.getImage(anyInt())).thenReturn(mock(AsyncBufferedImage.class));
		List<RaidEncounter> encounters = RaidPlanType.TOB.encounters(data);
		RaidEncounter maiden = encounters.get(0);
		RaidEncounter bloat = encounters.stream().filter(e -> e.getLabel().equals("Bloat")).findFirst().get();
		GearItem first = new GearItem(); first.setId(100); first.setName("first weapon"); first.setSlot(Slot.WEAPON);
		GearItem second = new GearItem(); second.setId(101); second.setName("second weapon"); second.setSlot(Slot.WEAPON);
		SetupResult a = setup(first); SetupResult b = setup(second);
		RaidPlan plan = new RaidPlan(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, Arrays.asList(maiden, bloat),
			Arrays.asList(result(a), result(b)), Arrays.asList(a, b), Arrays.asList(first, second),
			Collections.singletonList(new RaidPlan.InventoryItem("second weapon", Collections.singletonList(101), true)), 10, 0);
		SwingUtilities.invokeAndWait(() ->
		{
			BestGearSetupPanel panel = new BestGearSetupPanel(plugin, new BestGearSetupConfig() {}, images, null, null);
			panel.onDataLoaded(data);
			panel.selectRaid(RaidPlanType.TOB);
			panel.showRaidResults(plan, maiden.getTarget());
			assertTrue(panel.encounterBox.isVisible());
			panel.encounterBox.setSelectedIndex(1);
			assertEquals(bloat.getTarget(), panel.selected);
			assertEquals(bloat, panel.raidVersionBox.getSelectedItem());
			assertEquals("Theatre of Blood", panel.searchField.getText());
			List<Component> flow = components(panel);
			Component gear = flow.stream().filter(c -> c instanceof EquipmentGrid).findFirst().get();
			Component inventory = flow.stream().filter(c -> "raidInventory".equals(c.getName())).findFirst().get();
			assertTrue(flow.indexOf(panel.encounterBox) < flow.indexOf(panel.versionRow));
			assertTrue(flow.indexOf(panel.findButton) < flow.indexOf(gear));
			assertTrue(flow.indexOf(gear) < flow.indexOf(inventory));
			panel.clearResults("New search");
			panel.selectMonster(maiden.getTarget(), false);
			panel.encounterBox.setSelectedItem(bloat);
			assertEquals(maiden.getTarget(), panel.selected);
		});
	}

	@Test public void raidBossesAreGroupedAndTheirVersionsStayInsideTheRaid() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		BestGearSetupPlugin plugin = mock(BestGearSetupPlugin.class);
		net.runelite.client.config.ConfigDescriptor descriptor = ConfigItemsForTests.descriptor();
		when(plugin.getConfigDescriptor()).thenReturn(descriptor);
		when(plugin.getOwnedItems()).thenReturn(ConfigItemsForTests.emptyOwnedItems());
		ItemManager images = mock(ItemManager.class);
		when(images.getImage(anyInt())).thenReturn(mock(AsyncBufferedImage.class));
		List<RaidEncounter> encounters = RaidPlanType.TOA.encounters(data);
		GearItem weapon = new GearItem(); weapon.setId(100); weapon.setName("weapon"); weapon.setSlot(Slot.WEAPON);
		SetupResult setup = setup(weapon);
		List<SearchResults> results = new ArrayList<>();
		for (RaidEncounter encounter : encounters)
		{
			SearchResults result = result(setup); result.getMonster().setPhase(encounter.getPhase()); results.add(result);
		}
		RaidPlan plan = new RaidPlan(RaidPlanType.TOA, RaidSpellbook.ARCEUUS, encounters, results,
			Collections.nCopies(encounters.size(), setup), Collections.singletonList(weapon), Collections.emptyList(), 15, 0);
		SwingUtilities.invokeAndWait(() ->
		{
			BestGearSetupPanel panel = new BestGearSetupPanel(plugin, new BestGearSetupConfig() {}, images, null, null);
			panel.onDataLoaded(data); panel.selectRaid(RaidPlanType.TOA);
			panel.showRaidResults(plan, encounters.get(0).getTarget());
			assertEquals(encounters.stream().map(RaidEncounter::getBossName).distinct().count(), panel.encounterBox.getItemCount());
			assertTrue(!panel.phaseRow.isVisible());
			RaidEncounter akkha = encounters.stream().filter(e -> e.getBossName().equals("Akkha")).findFirst().get();
			panel.encounterBox.setSelectedItem(akkha);
			assertEquals(4, panel.raidVersionBox.getItemCount());
			assertTrue(panel.versionRow.isVisible());
			assertEquals("Immune To Melee", panel.raidVersionBox.getItemAt(0).getVersionLabel());
			panel.raidVersionBox.setSelectedIndex(2);
			assertEquals(EncounterPhase.AKKHA_IMMUNE_MAGIC, ((RaidEncounter) panel.raidVersionBox.getSelectedItem()).getPhase());
			assertEquals(RaidPlanType.TOA, panel.getRaidType());
			assertEquals(akkha.getTarget(), panel.selected);
			verify(plugin, org.mockito.Mockito.times(1)).findBestSetup(any());
			panel.encounterBox.setSelectedIndex(0);
			assertEquals(1, panel.raidVersionBox.getItemCount());
			assertTrue(!panel.versionRow.isVisible());
			panel.selectMonster(RaidPlanType.TOB.encounters(data).get(0).getTarget(), false);
			assertEquals(RaidPlanType.SINGLE, panel.getRaidType());
			assertEquals(panel.versionRow, panel.versionBox.getParent());
		});
	}

	@Test public void attackStyleSelectorSavesTheDisplayedEncountersChoice() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		BestGearSetupPlugin plugin = mock(BestGearSetupPlugin.class);
		net.runelite.client.config.ConfigDescriptor descriptor = ConfigItemsForTests.descriptor();
		when(plugin.getConfigDescriptor()).thenReturn(descriptor);
		when(plugin.getOwnedItems()).thenReturn(ConfigItemsForTests.emptyOwnedItems());
		ItemManager images = mock(ItemManager.class);
		when(images.getImage(anyInt())).thenReturn(mock(AsyncBufferedImage.class));
		List<RaidEncounter> encounters = RaidPlanType.TOA.encounters(data);
		GearItem weapon = new GearItem(); weapon.setId(100); weapon.setName("weapon"); weapon.setSlot(Slot.WEAPON);
		SetupResult setup = setup(weapon);
		List<SearchResults> results = new ArrayList<>();
		for (RaidEncounter encounter : encounters)
		{
			SearchResults result = result(setup); result.getMonster().setPhase(encounter.getPhase()); results.add(result);
		}
		RaidPlan plan = new RaidPlan(RaidPlanType.TOA, RaidSpellbook.ARCEUUS, encounters, results,
			Collections.nCopies(encounters.size(), setup), Collections.singletonList(weapon), Collections.emptyList(), 15, 0);
		SwingUtilities.invokeAndWait(() ->
		{
			BestGearSetupPanel panel = new BestGearSetupPanel(plugin, new BestGearSetupConfig() {}, images, null, null);
			panel.onDataLoaded(data); panel.selectRaid(RaidPlanType.TOA);
			panel.showRaidResults(plan, encounters.get(0).getTarget());
			RaidEncounter zebak = encounters.stream().filter(e -> e.getLabel().equals("Zebak")).findFirst().get();
			RaidEncounter enraged = encounters.stream().filter(e -> e.getLabel().equals("Zebak Enraged")).findFirst().get();
			panel.encounterBox.setSelectedItem(zebak);
			panel.raidVersionBox.setSelectedItem(enraged);
			assertEquals(enraged.getTarget(), panel.selected);
			assertTrue(panel.raidStyleRow.isVisible());
			assertEquals(RaidStyle.AUTO, panel.raidStyleBox.getSelectedItem());
			verify(plugin, never()).setConfig(org.mockito.ArgumentMatchers.eq(BestGearSetupConfig.RAID_STYLES_KEY), any());
			// The choice is saved for the boss, so it covers both Zebak phases.
			panel.raidStyleBox.setSelectedItem(RaidStyle.MELEE);
			verify(plugin).setConfig(BestGearSetupConfig.RAID_STYLES_KEY, "TOA|Zebak=MELEE");
			// The re-planned raid reopens the encounter that was being edited, not the first room.
			panel.showSearching(encounters.get(0).getTarget());
			panel.showRaidResults(plan, encounters.get(0).getTarget());
			assertEquals(enraged, panel.raidVersionBox.getSelectedItem());
			assertEquals(enraged.getTarget(), panel.selected);
			// A later re-plan follows the plan again.
			panel.showRaidResults(plan, encounters.get(0).getTarget());
			assertEquals(encounters.get(0), panel.raidVersionBox.getSelectedItem());
			// The Wardens' P2 ranged phase can only be damaged by ranged, so there is nothing to choose.
			RaidEncounter warden = encounters.stream().filter(e -> e.getLabel().equals("Elidinis P2: Ranged")).findFirst().get();
			panel.encounterBox.setSelectedItem(warden);
			assertTrue(!panel.raidStyleRow.isVisible());
			panel.clearResults("New search");
			assertTrue(!panel.raidStyleRow.isVisible());
		});
	}

	@Test public void engagingTheShownTypeAgainSelectsItsPlannedEncounterWithoutSearching() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		BestGearSetupPlugin plugin = mock(BestGearSetupPlugin.class);
		net.runelite.client.config.ConfigDescriptor descriptor = ConfigItemsForTests.descriptor();
		when(plugin.getConfigDescriptor()).thenReturn(descriptor);
		when(plugin.getOwnedItems()).thenReturn(ConfigItemsForTests.emptyOwnedItems());
		ItemManager images = mock(ItemManager.class);
		AsyncBufferedImage image = mock(AsyncBufferedImage.class);
		when(images.getImage(anyInt())).thenReturn(image);
		List<RaidEncounter> encounters = RaidPlanType.TOA.encounters(data);
		GearItem weapon = new GearItem(); weapon.setId(100); weapon.setName("weapon"); weapon.setSlot(Slot.WEAPON);
		SetupResult setup = setup(weapon);
		List<SearchResults> results = new ArrayList<>();
		for (RaidEncounter encounter : encounters)
		{
			SearchResults result = result(setup); result.getMonster().setPhase(encounter.getPhase()); results.add(result);
		}
		RaidPlan plan = new RaidPlan(RaidPlanType.TOA, RaidSpellbook.ARCEUUS, encounters, results,
			Collections.nCopies(encounters.size(), setup), Collections.singletonList(weapon), Collections.emptyList(), 15, 0);
		RaidEncounter magic = encounters.stream().filter(e -> e.getPhase() == EncounterPhase.AKKHA_IMMUNE_MAGIC).findFirst().get();
		RaidEncounter zebak = encounters.stream().filter(e -> e.getLabel().equals("Zebak")).findFirst().get();
		SwingUtilities.invokeAndWait(() ->
		{
			BestGearSetupPanel panel = new BestGearSetupPanel(plugin, new BestGearSetupConfig() {}, images, null, null);
			panel.onDataLoaded(data); panel.selectRaid(RaidPlanType.TOA);
			panel.showRaidResults(plan, encounters.get(0).getTarget());
			panel.showActiveMonster(magic.getTarget(), EncounterPhase.AKKHA_IMMUNE_MAGIC);
			assertEquals(magic, panel.raidVersionBox.getSelectedItem());
			assertEquals(magic.getTarget(), panel.selected);
			panel.showActiveMonster(zebak.getTarget(), null);
			assertEquals(zebak, panel.raidVersionBox.getSelectedItem());
			// A monster outside the plan leaves the shown encounter alone.
			MonsterSummary stranger = new MonsterSummary(); stranger.setId(-5); stranger.setName("stranger");
			panel.showActiveMonster(stranger, null);
			assertEquals(zebak, panel.raidVersionBox.getSelectedItem());
			verify(plugin, never()).findActiveSetup(any());
			// Outside raid results it does nothing.
			panel.clearResults("New search");
			panel.showActiveMonster(zebak.getTarget(), null);
			assertTrue(!panel.encounterBox.isVisible());
		});
	}

	@Test public void inventoryStartsMinimizedAndKeepsItsStateAcrossSearches() throws Exception
	{
		BestGearSetupPlugin plugin = mock(BestGearSetupPlugin.class);
		net.runelite.client.config.ConfigDescriptor descriptor = ConfigItemsForTests.descriptor();
		when(plugin.getConfigDescriptor()).thenReturn(descriptor);
		when(plugin.getOwnedItems()).thenReturn(ConfigItemsForTests.emptyOwnedItems());
		ItemManager images = mock(ItemManager.class);
		AsyncBufferedImage image = mock(AsyncBufferedImage.class);
		when(images.getImage(anyInt())).thenReturn(image);
		GearItem weapon = new GearItem(); weapon.setId(100); weapon.setName("weapon"); weapon.setSlot(Slot.WEAPON);
		SetupResult setup = setup(weapon);
		MonsterSummary boss = new MonsterSummary(); boss.setId(1); boss.setName("boss");
		RaidPlan plan = new RaidPlan(RaidPlanType.TOB, RaidSpellbook.ARCEUUS,
			Collections.singletonList(new RaidEncounter("Boss", boss, new AttackStyle.Type[0])), Collections.singletonList(result(setup)),
			Collections.singletonList(setup), Collections.singletonList(weapon), Collections.emptyList(), 10, 0);
		SwingUtilities.invokeAndWait(() ->
		{
			BestGearSetupPanel panel = new BestGearSetupPanel(plugin, new BestGearSetupConfig() {}, images, null, null);
			panel.showResults(result(setup));
			assertTrue("Minimized by default", !named(panel, "setupInventory").isVisible());
			((javax.swing.JButton) named(panel, "setupInventoryToggle")).doClick();
			assertTrue(named(panel, "setupInventory").isVisible());
			// A new search keeps it expanded, and so does a raid plan.
			panel.showResults(result(setup));
			assertTrue(named(panel, "setupInventory").isVisible());
			panel.showRaidResults(plan, boss);
			assertTrue(named(panel, "raidInventory").isVisible());
			((javax.swing.JButton) named(panel, "raidInventoryToggle")).doClick();
			panel.showResults(result(setup));
			assertTrue("Minimizing carries over too", !named(panel, "setupInventory").isVisible());
		});
	}

	private static Component named(Container root, String name)
	{
		return components(root).stream().filter(c -> name.equals(c.getName())).findFirst().get();
	}

	@Test public void failedRaidPlanOffersToResetSavedAttackStyles() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		BestGearSetupPlugin plugin = mock(BestGearSetupPlugin.class);
		net.runelite.client.config.ConfigDescriptor descriptor = ConfigItemsForTests.descriptor();
		when(plugin.getConfigDescriptor()).thenReturn(descriptor);
		when(plugin.getOwnedItems()).thenReturn(ConfigItemsForTests.emptyOwnedItems());
		BestGearSetupConfig config = new BestGearSetupConfig()
		{
			@Override public String raidStyles() { return "COX_CM|Tekton=MAGIC;TOA|Zebak=MELEE"; }
		};
		SwingUtilities.invokeAndWait(() ->
		{
			BestGearSetupPanel panel = new BestGearSetupPanel(plugin, config, null, null, null);
			panel.onDataLoaded(data);
			panel.showError("Search failed");
			assertTrue("Single searches have no raid styles", components(panel).stream().noneMatch(c -> "resetRaidStyles".equals(c.getName())));
			panel.selectRaid(RaidPlanType.TOA);
			panel.showError("Search failed: No usable setup for Zebak.");
			javax.swing.JButton reset = (javax.swing.JButton) components(panel).stream()
				.filter(c -> "resetRaidStyles".equals(c.getName())).findFirst().get();
			reset.doClick();
			verify(plugin).setConfig(BestGearSetupConfig.RAID_STYLES_KEY, "COX_CM|Tekton=MAGIC");
		});
	}

	private static List<Component> components(Container root)
	{
		List<Component> out = new ArrayList<>();
		for (Component component : root.getComponents())
		{
			out.add(component);
			if (component instanceof Container) { out.addAll(components((Container) component)); }
		}
		return out;
	}

	private static SetupResult setup(GearItem weapon)
	{
		Loadout loadout = new Loadout(); loadout.set(Slot.WEAPON, weapon);
		loadout.setStyle(AttackStyle.parse("pound,crush,aggressive"));
		return new SetupResult(CombatClass.MELEE, loadout, new DpsResult(5, 20, 1, 4, 12, null), 0);
	}
	private static SearchResults result(SetupResult setup)
	{
		Monster monster = new Monster(); monster.setName("boss");
		return new SearchResults(monster, 100, Collections.singletonMap(AttackStyle.Type.CRUSH, Collections.singletonList(setup)),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false);
	}
}

