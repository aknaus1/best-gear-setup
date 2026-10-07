package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.CombatContext;
import com.bestgearsetup.calc.Optimizer;
import com.bestgearsetup.calc.OptimizerSettings;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.calc.WeaponRules;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import org.junit.BeforeClass;
import org.junit.Test;

public class OwnershipRulesTest
{
	private static GameData data;
	private static CombatContext context;

	@BeforeClass
	public static void load() throws Exception
	{
		Gson gson = new Gson();
		data = WikiGameData.get(gson).gameData(gson);
		context = new CombatContext(WikiMonsters.get(gson).monster(data.matchNpc(265, null, 0).getName()),
			PlayerLevels.maxed(), true, false, null);
	}

	private Optimizer optimizer(int source, SearchMode mode)
	{
		Set<Integer> owned = OwnedItems.expand(Collections.singleton(source));
		return new Optimizer(data, context, OptimizerSettings.builder().mode(mode).budget(Long.MAX_VALUE)
			.spellbooks(Collections.emptySet()).build(), owned::contains, item -> 1);
	}

	@Test
	public void ordinaryGearDoesNotGrantCosmeticsOrImbuesEvenWithAnUnlimitedBudget()
	{
		int[][] pairs = {{1195, 2589}, {1201, 2621}, {4097, 4107}, {6737, 11773},
			{6733, 11771}, {6731, 11770}, {6735, 11772}, {12601, 13202}, {12603, 12691},
			{12605, 12692}, {19550, 19710}, {21739, 21752}, {8921, 11784}, {11864, 11865},
			{1704, 10354}, {4095, 4105}, {4089, 4099}, {4091, 4101}, {4093, 4103}};
		for (int[] pair : pairs)
		{
			assertFalse(pair[0] + " grants " + pair[1],
				OwnedItems.expand(Collections.singleton(pair[0])).contains(pair[1]));
			GearItem target = data.findItem(pair[1]);
			assertNotNull(optimizer(pair[0], SearchMode.OWNED_ONLY).unusableReason(target));
			if (OwnershipRules.requiresOwnership(pair[1]))
			{
				assertNotNull(optimizer(pair[0], SearchMode.BUDGET).unusableReason(target));
			}
			else
			{
				assertNull(optimizer(pair[0], SearchMode.BUDGET).unusableReason(target));
			}
			assertNull(optimizer(pair[1], SearchMode.OWNED_ONLY).unusableReason(target));
		}
	}

	@Test
	public void catalogueVariantsCannotBypassIdentityOrGrantAmmunitionQuantities()
	{
		GearItem item = new GearItem();
		item.setId(1704);
		item.setVariants(Arrays.asList(1704, 1712, 10354, 10362));
		assertEquals(Arrays.asList(1704, 1712), item.getOwnershipVariants());
		assertFalse(data.findItem(11865).getOwnershipVariants().contains(19641));
	}

	@Test
	public void chargeStatesAndProvidersOfTheSameImbueRemainEquivalent()
	{
		int[][] pairs = {{1704, 1712}, {10362, 10354}, {12924, 12926}, {27277, 27275},
			{11908, 11907}, {11773, 25264}, {11773, 26770}, {11784, 25276}, {12018, 26782}};
		for (int[] pair : pairs)
		{
			assertTrue(pair[0] + " should own " + pair[1],
				OwnedItems.expand(Collections.singleton(pair[0])).contains(pair[1]));
		}
	}

	@Test
	public void independentCosmeticsHaveIndependentPrices()
	{
		for (int id : new int[]{2589, 2621, 4107, 23059})
		{
			assertEquals(Collections.singletonMap(id, 1L), ItemCosts.components(id));
		}
	}

	@Test
	public void foldedCosmeticsBecomeExactCacheNamedCandidatesWithoutChangingTheSource()
	{
		GameData expanded = data.withMonsters(new Gson(), data.getMonsters());
		expanded.addVariantItems(new Gson(), id -> id == 12436 ? "Amulet of fury (or)"
			: id == 8714 ? "Rune kiteshield (Arrav)" : null, id -> id == 8714);
		assertNull(data.findItem(8714));
		assertEquals("rune kiteshield (arrav)", expanded.findItem(8714).getName());
		assertEquals("amulet of fury (or)", expanded.findItem(12436).getName());
		assertEquals(data.findItem(6585).getMeleeStr(), expanded.findItem(12436).getMeleeStr());
		assertEquals(Collections.singletonList(12436), expanded.findItem(12436).getVariants());
		assertFalse(expanded.findItem(12436).isTradeable());
		assertTrue(expanded.findItem(8714).isTradeable());
		assertEquals(data.findItem(6585).getName(), expanded.findItem(12436).getCombatName());
		Set<Integer> ordinary = OwnedItems.expand(Collections.singleton(1201));
		Optimizer search = new Optimizer(expanded, context,
			OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY).build(), ordinary::contains, item -> 1);
		assertNotNull(search.unusableReason(expanded.getItem(Slot.SHIELD, 8714)));
	}

	@Test
	public void everyBundledVariantIsRejectedForUnrelatedOwnershipIdentities()
	{
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY).build();
		for (Slot slot : Slot.values())
		{
			Set<Integer> sources = new HashSet<>();
			for (GearItem item : data.getItems(slot))
			{
				sources.add(item.getId());
				sources.addAll(item.getVariants());
			}
			for (int source : sources)
			{
				Set<Integer> owned = OwnedItems.expand(Collections.singleton(source));
				Optimizer search = new Optimizer(data, context, settings, owned::contains, item -> 1);
				for (GearItem target : data.getItems(slot))
				{
					if (!OwnershipRules.equivalent(source, target.getId()))
					{
						assertNotNull(source + " incorrectly grants " + target.getName(), search.unusableReason(target));
					}
				}
			}
		}
	}

	@Test
	public void cosmeticDisplayNamesPreserveCombatEffectsAndSourceSnapshotIsolation()
	{
		GameData expanded = data.withMonsters(new Gson(), data.getMonsters());
		expanded.addVariantItems(new Gson(), id -> id == 25731 ? "Holy sanguinesti staff" : null, id -> false);
		GearItem holy = expanded.findItem(25731);
		assertNotNull(holy);
		assertEquals("holy sanguinesti staff", holy.getName());
		assertEquals(WeaponRules.poweredStaffMaxHit(data.findItem(22323), 99),
			WeaponRules.poweredStaffMaxHit(holy, 99));
		expanded.findItem(22323).setMagicReq(1);
		assertEquals(82, data.findItem(22323).getMagicReq());
	}
}
