package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.game.ItemManager;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

public class BankHighlightTest
{
	/** Main code gets the client's Gson in startUp; tests use the same instance. */
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
	}

	@Test
	public void selectedSetupIncludesVariantsAndLoadedAmmoAndReplacesPreviousSelection()
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		Loadout ranged = new Loadout();
		ranged.set(Slot.WEAPON, item(12926, 12924));
		ranged.set(Slot.HEAD, item(11826, 11827));
		ranged.setLoadedAmmo(item(11230, 11231));
		plugin.setBankHighlightedSetup(new SetupResult(CombatClass.RANGED, ranged, null, 0));
		Set<Integer> first = plugin.getBankHighlightIds();
		assertEquals(new HashSet<>(Arrays.asList(12926, 12924, 11826, 11230)), first);
		assertEquals("Loaded darts aren't worn in an equipment slot",
			new HashSet<>(Arrays.asList(12926, 12924, 11826)), plugin.getInventoryHighlightIds());

		Loadout melee = new Loadout();
		melee.set(Slot.WEAPON, item(4151));
		plugin.setBankHighlightedSetup(new SetupResult(CombatClass.MELEE, melee, null, 0));
		assertEquals(new HashSet<>(Arrays.asList(4151)), plugin.getBankHighlightIds());
		assertEquals(new HashSet<>(Arrays.asList(4151)), plugin.getInventoryHighlightIds());
		// Rendering can still hold an older snapshot while the sidebar changes its selection.
		assertEquals(4, first.size());
		plugin.setBankHighlightedSetup(null);
		assertTrue(plugin.getBankHighlightIds().isEmpty());
		assertTrue(plugin.getInventoryHighlightIds().isEmpty());
	}

	@Test
	public void inventoryOverlayOnlyOutlinesTheDisplayedWornGear()
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, item(12926));
		loadout.setLoadedAmmo(item(11230));
		plugin.setBankHighlightedSetup(new SetupResult(CombatClass.RANGED, loadout, null, 0));
		Loadout switchGear = new Loadout();
		switchGear.set(Slot.WEAPON, item(4151));
		plugin.setInventoryHighlightedSetup(new SetupResult(CombatClass.MELEE, switchGear, null, 0));
		assertTrue("The bank's selected kit is independent", plugin.getBankHighlightIds().contains(12926));
		BestGearSetupConfig enabled = new BestGearSetupConfig()
		{
			@Override public boolean highlightInventoryGear() { return true; }
		};
		ItemManager items = mock(ItemManager.class);
		when(items.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0));
		BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
		when(items.getItemOutline(4151, 1, enabled.inventoryHighlightColor())).thenReturn(image);
		BestGearSetupInventoryOverlay overlay = new BestGearSetupInventoryOverlay(plugin, enabled, items);
		Graphics2D graphics = image.createGraphics();
		try
		{
			for (int id : new int[]{4151, 12926, 11230})
			{
				overlay.renderItemOverlay(graphics, id, new WidgetItem(id, 1, new Rectangle(0, 0, 36, 32), null, null));
			}
			verify(items).getItemOutline(4151, 1, enabled.inventoryHighlightColor());
			verify(items, never()).getItemOutline(12926, 1, enabled.inventoryHighlightColor());
			verify(items, never()).getItemOutline(11230, 1, enabled.inventoryHighlightColor());
		}
		finally { graphics.dispose(); }
	}

	@Test
	public void chestAdviceDoesNotChangeBankKitOrLeakOutsideTheRaid() throws Exception
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		GearItem gear = item(4151);
		CoxChestPlan.Deposit deposit = new CoxChestPlan.Deposit(gear, null, 0);
		CoxChestPlan chest = new CoxChestPlan(3, java.util.Collections.singletonList(deposit), 0, java.util.Collections.emptyList());
		RaidPlan plan = chestPlan(gear);
		plugin.setCoxChestHighlights(plan, chest);
		assertEquals(null, plugin.coxChestHighlightColor(4151));
		plugin.raidProgress.tick(RaidPlanType.COX_CM, null, true);
		assertEquals("No deposit highlight with storage closed", null, plugin.coxChestHighlightColor(4151));
		net.runelite.api.widgets.Widget chestWidget = openCoxStorage(plugin);
		assertEquals(CoxChestPlan.FINISHED_COLOR, plugin.coxChestHighlightColor(4151));
		when(chestWidget.isHidden()).thenReturn(true);
		assertEquals("Closing storage clears the highlight immediately", null, plugin.coxChestHighlightColor(4151));
		when(chestWidget.isHidden()).thenReturn(false);
		assertTrue(plugin.getBankHighlightIds().isEmpty());
		plugin.setInventoryHighlightedSetup(null);
		assertEquals(null, plugin.coxChestHighlightColor(4151));
		// The complete ordinary bank selection replaces any raid chest guidance.
		plugin.setCoxChestHighlights(plan, chest);
		plugin.setBankHighlightedSetup(null);
		assertEquals(null, plugin.coxChestHighlightColor(4151));
		plugin.setCoxChestHighlights(plan, chest);
		plugin.raidProgress.reset();
		assertEquals(null, plugin.coxChestHighlightColor(4151));
	}

	@Test public void chestOverlayAppliesTheInventoryAndStorageColorRulesOnlyWhileOpen() throws Exception
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		com.bestgearsetup.data.MonsterSummary target = new com.bestgearsetup.data.MonsterSummary();
		target.setName("great olm (head)");
		RaidEncounter later = new RaidEncounter("Olm head", target);
		RaidPlan plan = chestPlan(item(4151), item(12926), item(11826));
		plugin.setCoxChestHighlights(plan, new CoxChestPlan(3, Arrays.asList(
			new CoxChestPlan.Deposit(plan.getEquipment().get(0), null, 0),
			new CoxChestPlan.Deposit(plan.getEquipment().get(1), later, 4)),
			0, java.util.Collections.emptyList()));
		BestGearSetupConfig enabled = new BestGearSetupConfig() { @Override public boolean highlightInventoryGear() { return true; } };
		ItemManager items = mock(ItemManager.class);
		when(items.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0));
		BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
		when(items.getItemOutline(4151, 1, CoxChestPlan.FINISHED_COLOR)).thenReturn(image);
		when(items.getItemOutline(12926, 1, CoxChestPlan.LATER_COLOR)).thenReturn(image);
		when(items.getItemOutline(11826, 1, CoxChestPlan.NEEDED_COLOR)).thenReturn(image);
		BestGearSetupInventoryOverlay overlay = new BestGearSetupInventoryOverlay(plugin, enabled, items);
		assertTrue("The inventory beside private storage needs an explicit overlay target", overlay.getDrawHooks().contains(
			(net.runelite.api.gameval.InterfaceID.RAIDS_STORAGE_SIDE << 16) | 0xffff));
		assertTrue("Stored items need their own overlay target", overlay.getDrawHooks().contains(
			(net.runelite.api.gameval.InterfaceID.RAIDS_STORAGE_PRIVATE << 16) | 0xffff));
		Graphics2D graphics = image.createGraphics();
		try
		{
			int[] ids = {4151, 12926, 11826, 11230};
			renderChestItems(overlay, graphics, net.runelite.api.gameval.InterfaceID.INVENTORY, ids);
			org.mockito.Mockito.verify(items, never()).getItemOutline(anyInt(), anyInt(), org.mockito.ArgumentMatchers.any());
			plugin.raidProgress.tick(RaidPlanType.COX_CM, null, true);
			renderChestItems(overlay, graphics, net.runelite.api.gameval.InterfaceID.INVENTORY, ids);
			org.mockito.Mockito.verify(items, never()).getItemOutline(anyInt(), anyInt(), org.mockito.ArgumentMatchers.any());
			net.runelite.api.widgets.Widget chestWidget = openCoxStorage(plugin);
			renderChestItems(overlay, graphics, net.runelite.api.gameval.InterfaceID.RAIDS_STORAGE_SIDE, ids);
			verify(items).getItemOutline(4151, 1, CoxChestPlan.FINISHED_COLOR);
			verify(items).getItemOutline(12926, 1, CoxChestPlan.LATER_COLOR);
			verify(items, never()).getItemOutline(11826, 1, CoxChestPlan.NEEDED_COLOR);
			renderChestItems(overlay, graphics, net.runelite.api.gameval.InterfaceID.RAIDS_STORAGE_PRIVATE, ids);
			verify(items, org.mockito.Mockito.times(1)).getItemOutline(4151, 1, CoxChestPlan.FINISHED_COLOR);
			verify(items, org.mockito.Mockito.times(2)).getItemOutline(12926, 1, CoxChestPlan.LATER_COLOR);
			verify(items).getItemOutline(11826, 1, CoxChestPlan.NEEDED_COLOR);
			verify(items, never()).getItemOutline(org.mockito.ArgumentMatchers.eq(11230), anyInt(), org.mockito.ArgumentMatchers.any());
			when(chestWidget.isHidden()).thenReturn(true);
			renderChestItems(overlay, graphics, net.runelite.api.gameval.InterfaceID.RAIDS_STORAGE_SIDE, ids);
			renderChestItems(overlay, graphics, net.runelite.api.gameval.InterfaceID.RAIDS_STORAGE_PRIVATE, ids);
			verify(items, org.mockito.Mockito.times(1)).getItemOutline(4151, 1, CoxChestPlan.FINISHED_COLOR);
			verify(items, org.mockito.Mockito.times(2)).getItemOutline(12926, 1, CoxChestPlan.LATER_COLOR);
			verify(items, org.mockito.Mockito.times(1)).getItemOutline(11826, 1, CoxChestPlan.NEEDED_COLOR);
			when(chestWidget.isHidden()).thenReturn(false);
			BestGearSetupInventoryOverlay disabled = new BestGearSetupInventoryOverlay(plugin,
				new BestGearSetupConfig() { @Override public boolean highlightInventoryGear() { return false; } }, items);
			renderChestItems(disabled, graphics, net.runelite.api.gameval.InterfaceID.RAIDS_STORAGE_PRIVATE, ids);
			verify(items, org.mockito.Mockito.times(1)).getItemOutline(11826, 1, CoxChestPlan.NEEDED_COLOR);
			plugin.raidProgress.message("Congratulations - your raid is complete!");
			renderChestItems(overlay, graphics, net.runelite.api.gameval.InterfaceID.RAIDS_STORAGE_PRIVATE, ids);
			verify(items, org.mockito.Mockito.times(2)).getItemOutline(12926, 1, CoxChestPlan.LATER_COLOR);
			verify(items, org.mockito.Mockito.times(1)).getItemOutline(11826, 1, CoxChestPlan.NEEDED_COLOR);
			renderChestItems(overlay, graphics, net.runelite.api.gameval.InterfaceID.RAIDS_STORAGE_SIDE, ids);
			verify(items).getItemOutline(11826, 1, CoxChestPlan.FINISHED_COLOR);
		}
		finally { graphics.dispose(); }
	}

	@Test public void coxEquipHighlightsRemainVisibleDuringCombatAndFollowEncounterSwitches() throws Exception
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		GearItem weapon = item(4151);
		GearItem laterThisFloor = item(11826);
		Loadout worn = new Loadout(); worn.set(Slot.WEAPON, weapon);
		plugin.setInventoryHighlightedSetup(new SetupResult(CombatClass.MELEE, worn, null, 0));
		RaidPlan plan = chestPlan(weapon, laterThisFloor);
		CoxChestPlan chest = new CoxChestPlan(2, java.util.Collections.emptyList(), 0, java.util.Collections.emptyList());
		plugin.setCoxChestHighlights(plan, chest);
		plugin.raidProgress.tick(RaidPlanType.COX_CM, null, true);
		net.runelite.api.widgets.Widget chestWidget = openCoxStorage(plugin);
		when(chestWidget.isHidden()).thenReturn(true);
		BestGearSetupConfig enabled = new BestGearSetupConfig()
		{
			@Override public boolean highlightInventoryGear() { return true; }
			@Override public java.awt.Color inventoryHighlightColor() { return java.awt.Color.CYAN; }
		};
		ItemManager items = mock(ItemManager.class);
		when(items.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0));
		BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
		when(items.getItemOutline(anyInt(), anyInt(), org.mockito.ArgumentMatchers.any())).thenReturn(image);
		BestGearSetupInventoryOverlay overlay = new BestGearSetupInventoryOverlay(plugin, enabled, items);
		Graphics2D graphics = image.createGraphics();
		try
		{
			int[] ids = {4151, 11826};
			renderChestItems(overlay, graphics, net.runelite.api.gameval.InterfaceID.INVENTORY, ids);
			verify(items).getItemOutline(4151, 1, java.awt.Color.CYAN);
			verify(items, never()).getItemOutline(org.mockito.ArgumentMatchers.eq(11826), anyInt(), org.mockito.ArgumentMatchers.any());
			when(chestWidget.isHidden()).thenReturn(false);
			renderChestItems(overlay, graphics, net.runelite.api.gameval.InterfaceID.RAIDS_STORAGE_SIDE, ids);
			verify(items, org.mockito.Mockito.times(2)).getItemOutline(4151, 1, java.awt.Color.CYAN);
			verify(items, never()).getItemOutline(11826, 1, CoxChestPlan.NEEDED_COLOR);
			renderChestItems(overlay, graphics, net.runelite.api.gameval.InterfaceID.RAIDS_STORAGE_PRIVATE, ids);
			verify(items).getItemOutline(4151, 1, CoxChestPlan.NEEDED_COLOR);
			verify(items).getItemOutline(11826, 1, CoxChestPlan.NEEDED_COLOR);
			when(chestWidget.isHidden()).thenReturn(true);
			Loadout next = new Loadout(); next.set(Slot.WEAPON, laterThisFloor);
			plugin.setInventoryHighlightedSetup(new SetupResult(CombatClass.MELEE, next, null, 0));
			plugin.setCoxChestHighlights(plan, chest);
			renderChestItems(overlay, graphics, net.runelite.api.gameval.InterfaceID.INVENTORY, ids);
			verify(items).getItemOutline(11826, 1, java.awt.Color.CYAN);
			verify(items, org.mockito.Mockito.times(2)).getItemOutline(4151, 1, java.awt.Color.CYAN);
		}
		finally { graphics.dispose(); }
	}

	private static void renderChestItems(BestGearSetupInventoryOverlay overlay, Graphics2D graphics, int group, int[] ids)
	{
		net.runelite.api.widgets.Widget widget = mock(net.runelite.api.widgets.Widget.class);
		when(widget.getId()).thenReturn(group << 16 | 1);
		for (int id : ids)
		{ overlay.renderItemOverlay(graphics, id, new WidgetItem(id, 1, new Rectangle(0, 0, 36, 32), widget, null)); }
	}

	private static RaidPlan chestPlan(GearItem... equipment)
	{
		return new RaidPlan(RaidPlanType.COX_CM, RaidSpellbook.ARCEUUS, java.util.Collections.emptyList(),
			java.util.Collections.emptyList(), java.util.Collections.emptyList(), Arrays.asList(equipment), java.util.Collections.emptyList(), 20, 0);
	}

	private static net.runelite.api.widgets.Widget openCoxStorage(BestGearSetupPlugin plugin) throws Exception
	{
		net.runelite.api.Client client = mock(net.runelite.api.Client.class);
		net.runelite.api.widgets.Widget chest = mock(net.runelite.api.widgets.Widget.class);
		when(client.getWidget(net.runelite.api.gameval.InterfaceID.RaidsStoragePrivate.UNIVERSE)).thenReturn(chest);
		plugin.client = client;
		return chest;
	}

	@Test(expected = UnsupportedOperationException.class)
	public void rendererCannotMutateTheSelection()
	{
		new BestGearSetupPlugin().getBankHighlightIds().add(4151);
	}

	@Test
	public void emptyPlaceholdersAndDisabledHighlightsDoNotReadItemSprites()
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, item(4151));
		plugin.setBankHighlightedSetup(new SetupResult(CombatClass.MELEE, loadout, null, 0));
		// No ItemManager is installed: these paths must stop before any cache or sprite lookup.
		BestGearSetupBankOverlay overlay = new BestGearSetupBankOverlay(plugin, new BestGearSetupConfig() {}, null);
		Graphics2D graphics = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			overlay.renderItemOverlay(graphics, 4151, new WidgetItem(4151, 0, new Rectangle(0, 0, 36, 32), null, null));
			BestGearSetupConfig disabled = new BestGearSetupConfig()
			{
				@Override
				public boolean highlightBankGear()
				{
					return false;
				}
			};
			new BestGearSetupBankOverlay(plugin, disabled, null).renderItemOverlay(graphics, 4151,
				new WidgetItem(4151, 1, new Rectangle(0, 0, 36, 32), null, null));
			plugin.setBankHighlightedSetup(null);
			overlay.renderItemOverlay(graphics, 4151, new WidgetItem(4151, 1, new Rectangle(0, 0, 36, 32), null, null));
		}
		finally
		{
			graphics.dispose();
		}
	}

	private static GearItem item(int id, Integer... variants)
	{
		GearItem item = new GearItem();
		item.setId(id);
		item.setVariants(Arrays.asList(variants));
		return item;
	}
}
