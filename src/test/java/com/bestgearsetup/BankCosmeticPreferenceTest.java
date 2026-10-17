package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.CosmeticVariants;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.Test;

/** Cosmetic preference affects bank presentation without granting ownership of another item. */
public class BankCosmeticPreferenceTest
{
	private BestGearSetupPlugin plugin;
	private GameData data;

	@Before
	public void setUp() throws Exception
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
		data = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
		data.addVariantItems(RuneLiteAPI.GSON, id -> id == 25886 ? "Bow of faerdhinen (c) (ithell)" : null, id -> false);
		plugin = new BestGearSetupPlugin();
		plugin.gameData = data;
		plugin.config = new BestGearSetupConfig() { };
		plugin.ownedItems = new OwnedItems(null, null);
	}

	private void select(int id)
	{
		GearItem item = data.findItem(id);
		Loadout loadout = new Loadout(); loadout.set(item.getSlot(), item);
		plugin.setBankHighlightedSetup(new SetupResult(CombatClass.RANGED, loadout, null, 0));
	}

	@Test
	public void masoriAssemblerWinsOverTheBaseAndFallsBackAsItsBankStackChanges()
	{
		select(ItemID.AVAS_ASSEMBLER);
		assertEquals(Integer.valueOf(ItemID.AVAS_ASSEMBLER_MASORI), plugin.bankItemIds(data.findItem(ItemID.AVAS_ASSEMBLER)).get(0));
		plugin.ownedItems.bank = Map.of(ItemID.AVAS_ASSEMBLER, 1L, ItemID.AVAS_ASSEMBLER_MASORI, 1L);
		assertTrue(plugin.shouldHighlightBankItem(ItemID.AVAS_ASSEMBLER_MASORI));
		assertFalse(plugin.shouldHighlightBankItem(ItemID.AVAS_ASSEMBLER));
		// Withdrawing it, equipping it or retaining only its placeholder leaves the base as the bank fallback.
		plugin.ownedItems.bank = Map.of(ItemID.AVAS_ASSEMBLER, 1L, ItemID.AVAS_ASSEMBLER_MASORI, 0L);
		plugin.ownedItems.inventory = Map.of(ItemID.AVAS_ASSEMBLER_MASORI, 1L);
		assertTrue(plugin.shouldHighlightBankItem(ItemID.AVAS_ASSEMBLER));
		plugin.ownedItems.bank = Map.of(ItemID.AVAS_ASSEMBLER, 1L, ItemID.AVAS_ASSEMBLER_MASORI, 1L);
		assertFalse("No search or selection change is needed after a deposit", plugin.shouldHighlightBankItem(ItemID.AVAS_ASSEMBLER));
		plugin.setBankHighlightedSetup(null);
		assertFalse(plugin.shouldHighlightBankItem(ItemID.AVAS_ASSEMBLER_MASORI));
	}

	@Test
	public void maxCapeKitsRemainSeparateAndCosmeticMatchesDoNotGrantBaseOwnership()
	{
		GearItem assembler = data.findItem(ItemID.AVAS_ASSEMBLER);
		GearItem masori = data.findItem(ItemID.AVAS_ASSEMBLER_MASORI);
		GearItem max = data.findItem(ItemID.SKILLCAPE_MAX_ASSEMBLER);
		GearItem masoriMax = data.findItem(ItemID.SKILLCAPE_MAX_ASSEMBLER_MASORI);
		assertTrue(CosmeticVariants.equivalent(assembler, masori));
		assertTrue(CosmeticVariants.equivalent(max, masoriMax));
		assertFalse(CosmeticVariants.equivalent(assembler, masoriMax));
		assertFalse(CosmeticVariants.equivalent(assembler, max));
		plugin.ownedItems.bank = Map.of(ItemID.AVAS_ASSEMBLER_MASORI, 1L);
		plugin.ownedItems.rebuild(true);
		assertTrue(plugin.ownedItems.owns(ItemID.AVAS_ASSEMBLER_MASORI));
		assertFalse(plugin.ownedItems.owns(ItemID.AVAS_ASSEMBLER));
		assertFalse(plugin.ownedItems.owns(ItemID.SKILLCAPE_MAX_ASSEMBLER_MASORI));
		select(ItemID.SKILLCAPE_MAX_ASSEMBLER);
		assertFalse(plugin.getBankHighlightIds().contains(ItemID.AVAS_ASSEMBLER_MASORI));
	}

	@Test
	public void foldedRecoloursAlsoTakePriorityWithoutPreferringUnownedVersions()
	{
		select(25867);
		assertEquals(Integer.valueOf(25886), plugin.bankItemIds(data.findItem(25867)).get(0));
		plugin.ownedItems.bank = Map.of(25867, 1L);
		assertTrue(plugin.shouldHighlightBankItem(25867));
		plugin.ownedItems.bank = Map.of(25867, 1L, 25886, 1L);
		assertTrue(plugin.shouldHighlightBankItem(25886));
		assertFalse(plugin.shouldHighlightBankItem(25867));
		select(25886);
		assertEquals(Integer.valueOf(25886), plugin.bankItemIds(data.findItem(25886)).get(0));
	}
}
