package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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
import net.runelite.api.widgets.WidgetItem;
import org.junit.BeforeClass;
import org.junit.Test;

public class BankHighlightTest
{
	/** Main code gets the client's Gson in startUp; tests supply their own. */
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(new Gson());
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

		Loadout melee = new Loadout();
		melee.set(Slot.WEAPON, item(4151));
		plugin.setBankHighlightedSetup(new SetupResult(CombatClass.MELEE, melee, null, 0));
		assertEquals(new HashSet<>(Arrays.asList(4151)), plugin.getBankHighlightIds());
		// Rendering can still hold an older snapshot while the sidebar changes its selection.
		assertEquals(4, first.size());
		plugin.setBankHighlightedSetup(null);
		assertTrue(plugin.getBankHighlightIds().isEmpty());
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
