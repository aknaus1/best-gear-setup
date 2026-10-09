package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import com.bestgearsetup.calc.CombatContext;
import com.bestgearsetup.calc.Optimizer;
import com.bestgearsetup.calc.OptimizerSettings;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

/** Ordinary glories must not turn into trimmed recommendations through ownership expansion. */
public class GloryOwnershipTest
{
	@Test
	public void ownedOnlyRecommendsOrdinaryGloryWhenOnlyOrdinaryGloryIsHeld() throws Exception
	{
		Gson gson = RuneLiteAPI.GSON;
		GameData data = WikiGameData.get(gson).gameData(gson);
		Monster monster = WikiMonsters.get(gson).monster(data.matchNpc(265, null, 0).getName());
		CombatContext context = new CombatContext(monster, PlayerLevels.maxed(), false, false, null);
		Set<Integer> owned = OwnedItems.expand(new HashSet<>(Arrays.asList(
			ItemID.DRAGON_SCIMITAR, ItemID.AMULET_OF_GLORY_4)));
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY)
			.spellbooks(Collections.emptySet()).build();
		List<SetupResult> results = new Optimizer(data, context, settings, owned::contains, GearItem::getPrice)
			.optimize(CombatClass.MELEE, () -> false);
		assertFalse(results.isEmpty());
		for (SetupResult result : results)
		{
			assertEquals(ItemID.AMULET_OF_GLORY, result.getLoadout().get(Slot.NECK).getId());
			assertEquals(0, result.getBuyCost());
		}
	}

	@Test
	public void trimmedGloryUsesTrimmedAcquisitionPrice()
	{
		GearItem trimmed = new GearItem();
		trimmed.setId(ItemID.TRAIL_AMULET_OF_GLORY_4);
		trimmed.setTradeable(true);
		assertEquals(50_000, ItemCosts.price(trimmed,
			id -> id == ItemID.TRAIL_AMULET_OF_GLORY ? 50_000 : 10_000, id -> true));
		assertEquals(ItemCosts.UNKNOWN, ItemCosts.price(trimmed,
			id -> id == ItemID.AMULET_OF_GLORY ? 10_000 : 0, id -> true));
	}
}
