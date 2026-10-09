package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterGroup;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/** Variants catalogued as separate entries, such as "dragon hunter crossbow (b)", can be excluded together. */
public class ExclusionFamilyTest
{
	private static final Gson GSON = RuneLiteAPI.GSON;
	private static GameData data;
	private static GearItem crossbow;
	private static GearItem crossbowB;

	@BeforeClass
	public static void load() throws Exception
	{
		data = WikiGameData.get(GSON).gameData(GSON);
		crossbow = data.getItem(Slot.WEAPON, 21012);
		crossbowB = data.getItem(Slot.WEAPON, 25918);
		assertNotNull(crossbow);
		assertNotNull(crossbowB);
	}

	@Test
	public void familyNamesDropTrailingVariantSuffixes()
	{
		assertEquals("dragon hunter crossbow", GameData.familyName("Dragon hunter crossbow (b)"));
		assertEquals("dragon hunter crossbow", GameData.familyName("dragon hunter crossbow"));
		assertEquals("dharok's helm", GameData.familyName("dharok's helm (100) (or)"));
		assertEquals("osmumten's fang", GameData.familyName("osmumten's fang"));
	}

	@Test
	public void crossbowFamilyHoldsBothEntriesWhicheverIsExcluded()
	{
		Set<Integer> family = ids(data.variantFamily(crossbow));
		assertTrue(family.contains(crossbow.getId()));
		assertTrue(family.contains(crossbowB.getId()));
		assertEquals(family, ids(data.variantFamily(crossbowB)));
		for (int id : family)
		{
			assertEquals(Slot.WEAPON, data.getItem(Slot.WEAPON, id).getSlot());
		}
	}

	@Test
	public void excludingTheFamilyRemovesTheEquivalentCrossbowFromVorkathResults() throws Exception
	{
		List<MonsterGroup> groups = data.searchMonsterGroups("vorkath", 1, true);
		assertFalse(groups.isEmpty());
		Monster vorkath = WikiMonsters.get(GSON).monster(groups.get(0).getPrimary().getName());
		CombatContext ctx = new CombatContext(vorkath, PlayerLevels.maxed(), false, true, TestData.piety());
		OptimizerSettings base = OptimizerSettings.builder().mode(SearchMode.UNLIMITED).depth(SearchDepth.FAST)
			.resultsPerClass(40).antifire(Antifire.SUPER).protectMagic(false).spellbooks(Collections.emptySet()).build();

		Optimizer onlyBase = new Optimizer(data, ctx, base.toBuilder().excluded(Collections.singleton(crossbow.getId()))
			.build(), id -> false, item -> 1L);
		assertNull(onlyBase.unusableReason(crossbowB));

		Set<Integer> family = ids(data.variantFamily(crossbow));
		Optimizer whole = new Optimizer(data, ctx, base.toBuilder().excluded(family).build(), id -> false, item -> 1L);
		assertEquals("you excluded it", whole.unusableReason(crossbowB));
		for (SetupResult r : whole.optimize(CombatClass.RANGED, () -> false))
		{
			assertFalse(r.getLoadout().getWeapon().getName(), family.contains(r.getLoadout().getWeapon().getId()));
		}
	}

	private static Set<Integer> ids(List<GearItem> items)
	{
		return items.stream().map(GearItem::getId).collect(Collectors.toCollection(HashSet::new));
	}
}
