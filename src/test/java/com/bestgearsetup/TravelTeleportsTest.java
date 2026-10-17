package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SpecialPlan;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class TravelTeleportsTest
{
	private static final WorldPoint VARROCK = new WorldPoint(3213, 3424, 0);

	@Test public void monsterVersionsShareTheirBossDestination()
	{
		assertSame(TravelTeleports.ZULRAH, TravelTeleports.forMonster("zulrah (serpentine)"));
		assertSame(TravelTeleports.VORKATH, TravelTeleports.forMonster("Vorkath (post-quest)"));
		assertSame(TravelTeleports.KRAKEN, TravelTeleports.forMonster("kraken (kraken)"));
		assertSame(TravelTeleports.GOD_WARS, TravelTeleports.forMonster("kree'arra (normal)"));
		assertSame(TravelTeleports.BARROWS, TravelTeleports.forMonster("dharok the wretched"));
		assertSame(TravelTeleports.SLAYER_TOWER, TravelTeleports.forMonster("dusk (second form)"));
	}

	@Test public void otherMonstersAndElsewhereVariantsGetNoDestination()
	{
		assertNull(TravelTeleports.forMonster(null));
		assertNull(TravelTeleports.forMonster("abyssal demon"));
		assertNull(TravelTeleports.forMonster("cave kraken (whirlpool)"));
		assertNull(TravelTeleports.forMonster("general graardor (deadman) (permanent)"));
		assertNull(TravelTeleports.forMonster("general graardor (pvm arena)"));
		assertNull(TravelTeleports.forMonster("cerberus (echo)"));
		assertNull(TravelTeleports.forMonster("giant mole (lanscape) (bandos)"));
	}

	@Test public void raidsTravelToTheirLobby()
	{
		assertSame(TravelTeleports.CHAMBERS_OF_XERIC, TravelTeleports.forRaid(RaidPlanType.COX_CM));
		assertSame(TravelTeleports.THEATRE_OF_BLOOD, TravelTeleports.forRaid(RaidPlanType.TOB));
		assertNull(TravelTeleports.forRaid(RaidPlanType.TOA));
		assertNull(TravelTeleports.forRaid(null));
	}

	@Test public void nothingIsSuggestedNearTheBossOrInItsInstance()
	{
		assertNull(TravelTeleports.advise(TravelTeleports.VORKATH, new WorldPoint(2645, 3700, 0), id -> false,
			Collections.emptyList()));
		assertNull(TravelTeleports.advise(TravelTeleports.VORKATH, new WorldPoint(2272, 4060, 0), id -> false,
			Collections.emptyList()));
		assertNull(TravelTeleports.advise(TravelTeleports.VORKATH, null, id -> false, Collections.emptyList()));
		assertNull(TravelTeleports.advise(null, VARROCK, id -> false, Collections.emptyList()));
		assertTrue(TravelTeleports.isNear(TravelTeleports.ZULRAH, new WorldPoint(2193 + 64, 3060, 0)));
		assertFalse(TravelTeleports.isNear(TravelTeleports.ZULRAH, new WorldPoint(2268 + 65, 3060, 0)));
	}

	@Test public void farAwayPrefersAnOwnedTeleportElseTheFirstListed()
	{
		TravelTeleports.Advice none = TravelTeleports.advise(TravelTeleports.VORKATH, VARROCK, id -> false,
			Collections.emptyList());
		assertEquals("Rellekka teleport", none.getTeleport().getName());
		assertFalse(none.isOwned());
		assertFalse(none.isRouted());

		TravelTeleports.Advice lyre = TravelTeleports.advise(TravelTeleports.VORKATH, VARROCK,
			id -> id == ItemID.MAGIC_STRUNG_LYRE_3, Collections.emptyList());
		assertEquals("Enchanted lyre", lyre.getTeleport().getName());
		assertTrue(lyre.isOwned());
	}

	@Test public void shortestPathsTeleportComesFirst()
	{
		TravelTeleports.Advice routed = TravelTeleports.advise(TravelTeleports.VORKATH, VARROCK,
			id -> id == ItemID.MAGIC_STRUNG_LYRE_3, Arrays.asList("Varrock: Gate", "Rellekka Teleport: Rellekka"));
		assertEquals("Rellekka teleport", routed.getTeleport().getName());
		assertFalse(routed.isOwned());
		assertTrue(routed.isRouted());
	}

	@Test public void everyDestinationListsUsableTeleportItems()
	{
		for (TravelTeleports.Destination destination : Arrays.asList(TravelTeleports.ZULRAH, TravelTeleports.VORKATH,
			TravelTeleports.BARROWS, TravelTeleports.CERBERUS, TravelTeleports.GIANT_MOLE, TravelTeleports.CORPOREAL_BEAST,
			TravelTeleports.KRAKEN, TravelTeleports.GOD_WARS, TravelTeleports.SLAYER_TOWER,
			TravelTeleports.THEATRE_OF_BLOOD, TravelTeleports.CHAMBERS_OF_XERIC))
		{
			assertFalse(destination.getName(), destination.getTeleports().isEmpty());
			assertFalse(destination.getName(), destination.getNear().isEmpty());
			// The route's end counts as being there.
			assertTrue(destination.getName(), TravelTeleports.isNear(destination, destination.getTarget()));
			for (TravelTeleports.Teleport teleport : destination.getTeleports())
			{
				assertFalse(teleport.getName(), teleport.getItemIds().isEmpty());
				assertTrue(teleport.getName(), teleport.getItemIds().stream().allMatch(id -> id > 0));
				assertEquals(teleport.getKeyword(), teleport.getKeyword().toLowerCase());
			}
		}
	}

	@Test public void describesTheTeleportAndTheRoute()
	{
		TravelTeleports.Advice advice = TravelTeleports.advise(TravelTeleports.ZULRAH, VARROCK, id -> false,
			Collections.emptyList());
		assertEquals(Collections.singletonList("Teleport to Zulrah: Zul-andra teleport (not owned)"),
			TravelTeleports.describe(advice, Collections.emptyList(), -1));
		List<String> lines = TravelTeleports.describe(advice, Arrays.asList("a", "b", "c", "d", "e"), 100);
		assertEquals("Shortest Path: a > b > c > d > ... (~60s)", lines.get(1));
		assertTrue(TravelTeleports.describe(null, Collections.emptyList(), -1).isEmpty());
		assertEquals("~2 min", TravelTeleports.duration(200));
	}

	@Test public void bankLayoutPutsTheTeleportInAFreeCellWithoutMovingGear()
	{
		GearItem whip = item(4151);
		SetupResult setup = setup(whip);
		List<Integer> scroll = Collections.singletonList(ItemID.TELEPORTSCROLL_ZULANDRA);
		Map<Integer, Integer> plain = BestGearSetupBankView.cellsFor(setup, Collections.emptyList(), Collections.emptyList());
		Map<Integer, Integer> cells = BestGearSetupBankView.cellsFor(setup, Collections.emptyList(), scroll);
		assertEquals(Integer.valueOf(BestGearSetupBankView.TRAVEL_CELL), cells.get(ItemID.TELEPORTSCROLL_ZULANDRA));
		assertEquals(plain.get(4151), cells.get(4151));
		// Already part of the setup: no second cell.
		assertEquals(plain, BestGearSetupBankView.cellsFor(setup, Collections.emptyList(), Collections.singletonList(4151)));
		assertTrue(BestGearSetupBankView.cellsFor(null, Collections.emptyList(), scroll).isEmpty());
	}

	@Test public void inventoryLayoutTakesTheLastFreeInventoryCell()
	{
		GearItem whip = item(4151);
		SpecialPlan special = new SpecialPlan(item(4153), null, null, null, null, null, null, null, null, null, 1, 0, 0);
		SetupResult setup = new SetupResult(CombatClass.MELEE, setup(whip).getLoadout(), null, 0, 0,
			Collections.emptySet(), Collections.emptySet(), special);
		Map<Integer, Integer> cells = BestGearSetupBankView.cellsFor(setup, Collections.emptyList(),
			Collections.singletonList(ItemID.TELEPORTSCROLL_ZULANDRA));
		// The granite maul switch puts the layout in inventory mode; the last inventory cell is free.
		assertEquals(Integer.valueOf(4), cells.get(4153));
		assertEquals(Integer.valueOf(BestGearSetupBankView.inventoryCell(27)), cells.get(ItemID.TELEPORTSCROLL_ZULANDRA));
	}

	private static SetupResult setup(GearItem weapon)
	{
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, weapon);
		return new SetupResult(CombatClass.MELEE, loadout, null, 0);
	}

	private static GearItem item(int id)
	{
		GearItem item = new GearItem();
		item.setId(id);
		item.setVariants(Collections.emptyList());
		return item;
	}
}
