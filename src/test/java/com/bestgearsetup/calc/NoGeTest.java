package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.ShopStock;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/** No GE: a budget search buys only shop-stocked items, best in slot uses only them, owned gear is unchanged. */
public class NoGeTest
{
	private static final Gson GSON = RuneLiteAPI.GSON;
	private static GameData data;
	private static CombatContext ctx;
	private static Gear whip;
	private static Gear runeBody;
	private static Gear voidTop;

	@BeforeClass
	public static void load() throws Exception
	{
		data = WikiGameData.get(GSON).gameData(GSON);
		Monster target = WikiMonsters.get(GSON).monster(data.searchMonsterGroups("vorkath", 1, true).get(0).getPrimary().getName());
		ctx = TestFactories.combatContext(target, PlayerLevels.maxed(), false, true, TestData.piety());
		whip = data.getItem(Slot.WEAPON, 4151);
		runeBody = data.getItem(Slot.BODY, 1127);
		voidTop = data.getItem(Slot.BODY, 8839);
		assertNotNull(whip);
		assertNotNull(runeBody);
		assertNotNull(voidTop);
	}

	private static Optimizer optimizer(SearchMode mode, boolean noGe, long budget, Set<Integer> owned)
	{
		ShopStock shops = noGe ? ShopStock.bundled() : null;
		OptimizerSettings settings = OptimizerSettings.builder().mode(mode).budget(budget).shopStock(shops)
			.antifire(Antifire.SUPER).protectMagic(false).spellbooks(Collections.singleton("standard")).build();
		// Prices as the plugin snapshots them: shop prices for stocked items, else a Grand Exchange quote.
		return Optimizers.of(data, ctx, settings, owned::contains,
			item -> shops != null && shops.stocks(item) ? shops.price(item) : 1_000L);
	}

	@Test
	public void budgetSearchesBuyOnlyWhatAShopStocksAtItsShopPrice()
	{
		Optimizer ge = optimizer(SearchMode.BUDGET, false, 100_000, Collections.emptySet());
		assertTrue(ge.available(whip));
		assertFalse(ge.available(voidTop));

		Optimizer shops = optimizer(SearchMode.BUDGET, true, 100_000, Collections.emptySet());
		assertFalse(shops.available(whip));
		assertEquals("no shop stocks it (No GE)", shops.unusableReason(whip));
		assertTrue(shops.available(runeBody));
		// Bought for Void Knight commendation points: no coins against the budget, untradeable or not.
		assertTrue(optimizer(SearchMode.BUDGET, true, 0, Collections.emptySet()).available(voidTop));
		// The rune platebody's coin price is over a 1,000 gp budget.
		assertFalse(optimizer(SearchMode.BUDGET, true, 1_000, Collections.emptySet()).available(runeBody));
		// Owned gear works as without No GE.
		assertTrue(optimizer(SearchMode.BUDGET, true, 0, Collections.singleton(4151)).available(whip));
	}

	@Test
	public void searchesWearOnlyStockedOrOwnedItems()
	{
		ShopStock shops = ShopStock.bundled();
		Set<Integer> owned = Collections.singleton(4151);
		for (SearchMode mode : new SearchMode[]{SearchMode.UNLIMITED, SearchMode.BUDGET})
		{
			for (Combat cls : Combat.values())
			{
				List<Setup> results = optimizer(mode, true, 5_000_000, owned).optimize(cls, () -> false, done -> { });
				assertFalse(mode + " " + cls, results.isEmpty());
				for (Setup r : results)
				{
					for (Gear item : worn(r))
					{
						boolean ownedOk = mode == SearchMode.BUDGET && owned.contains(item.getId());
						assertTrue(mode + " " + cls + ": " + item.getName(), ownedOk || shops.stocks(item));
					}
				}
			}
		}
	}

	private static List<Gear> worn(Setup r)
	{
		List<Gear> items = new ArrayList<>();
		for (Slot slot : Slot.values())
		{
			items.add(r.getLoadout().get(slot));
		}
		items.add(r.getLoadout().getLoadedAmmo());
		SpecialPlan plan = r.getSpecial();
		if (plan != null)
		{
			items.addAll(Arrays.asList(plan.getWeapon(), plan.getOffhand(), plan.getAmmo(), plan.getLoadedAmmo()));
		}
		items.removeIf(Objects::isNull);
		return items;
	}

	@Test
	public void bestInSlotUsesOnlyShopItemsInAnyCurrency()
	{
		assertTrue(optimizer(SearchMode.UNLIMITED, false, 0, Collections.emptySet()).available(whip));
		Optimizer shops = optimizer(SearchMode.UNLIMITED, true, 0, Collections.singleton(4151));
		assertFalse(shops.available(whip));
		assertTrue(shops.available(runeBody));
		assertTrue(shops.available(voidTop));
		assertNull(shops.unusableReason(runeBody));
	}
}
