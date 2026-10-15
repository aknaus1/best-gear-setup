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
