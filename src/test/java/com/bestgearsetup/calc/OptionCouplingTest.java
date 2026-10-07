package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Small banks (found by the exhaustive comparison in {@link OptimizerOracleTest}) where the best stance or spell
 * only wins together with an equipment change, and neither wins alone. Each expected score is that bank's
 * exhaustive optimum.
 */
public class OptionCouplingTest
{
	private static GameData catalogue;
	private static WikiMonsters monsters;

	@BeforeClass
	public static void load() throws Exception
	{
		Gson gson = new Gson();
		OwnershipRules.init(gson);
		catalogue = WikiGameData.get(gson).gameData(gson);
		monsters = WikiMonsters.get(gson);
	}

	/** One bank: {item id, acquisition price, owned (1) or not (0)} per row. */
	private static SetupResult best(String monster, PlayerLevels levels, boolean potions, boolean prayers,
		boolean wilderness, OptimizerSettings settings, CombatClass cls, long[][] bank)
	{
		List<GearItem> items = new ArrayList<>();
		Map<Integer, Long> prices = new HashMap<>();
		HashSet<Integer> owned = new HashSet<>();
		for (long[] row : bank)
		{
			int id = (int) row[0];
			GearItem item = null;
			for (Slot slot : Slot.values())
			{
				item = item != null ? item : catalogue.getItem(slot, id);
			}
			items.add(item);
			prices.put(id, row[1]);
			if (row[2] != 0)
			{
				owned.add(id);
			}
		}
		GameData data = TestData.gameData(items, catalogue.getSpells());
		CombatContext ctx = new CombatContext(monsters.monster(monster), levels, false, potions,
			prayers ? TestData.piety() : null).withModifiers(CombatModifiers.builder().wilderness(wilderness).build());
		Optimizer optimizer = new Optimizer(data, ctx, settings, owned::contains, item -> prices.get(item.getId()));
		SetupResult result = optimizer.optimize(cls, () -> false).get(0);
		assertTrue(optimizer.expensiveItemCount(result.getLoadout()) <= settings.getMaxExpensiveItems()
			|| !settings.isWildernessRiskLimited());
		assertTrue(settings.getMode() != SearchMode.BUDGET || result.getBuyCost() <= settings.getBudget());
		return result;
	}

	private static OptimizerSettings.OptimizerSettingsBuilder settings(AttackStyle.Type tab)
	{
		return OptimizerSettings.builder().depth(SearchDepth.BEST).styles(Collections.singleton(tab))
			.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient", "arceuus")));
	}

	@Test
	public void stanceChangesWithTwoSlots()
	{
		// Aggressive only beats accurate once the cape comes off and Glory replaces Strength.
		SetupResult r = best("fire giant (level 86)", new PlayerLevels(71, 77, 69, 72, 95, 97, 89, 92), true, false,
			false, settings(AttackStyle.Type.CRUSH).mode(SearchMode.BUDGET).budget(5_000_000).build(), CombatClass.MELEE,
			new long[][]{
				{6760, 100_001, 1}, {4153, 99_999, 1}, {4081, 100_000, 1}, {10588, 12_000, 1}, {1725, 100_001, 1},
				{1704, 99_999, 1}, {21298, 500, 1}, {6524, 900_000, 0}, {31081, 99_999, 0}, {24420, 900_000, 1},
				{11832, 900_000, 0}, {4131, 12_000, 1}, {11966, 500, 0}, {29289, 100_001, 1},
			});
		assertEquals(2.6369662078521534, r.getDps().getDps(), 1e-9);
		assertEquals("aggressive", r.getLoadout().getStyle().getStance());
		assertEquals(1704, r.getLoadout().get(Slot.NECK).getId());
	}

	@Test
	public void spellChangesWithTwoSlots()
	{
		// Fire Wave needs the Tome of fire, which takes the one expensive allowance and the legs' place.
		SetupResult r = best("chaos dwarf", new PlayerLevels(90, 64, 82, 74, 71, 94, 75, 60), true, true, true,
			settings(AttackStyle.Type.MAGIC).mode(SearchMode.BUDGET).budget(5_000_000).wildernessRiskLimited(true)
				.maxExpensiveItems(1).expensiveItemThreshold(100_000).excluded(Collections.singleton(4675)).build(),
			CombatClass.MAGIC, new long[][]{
				{30634, 12_000, 1}, {1405, 12_000, 0}, {4675, 100_001, 1}, {20714, 900_000, 0}, {1387, 12_000, 1},
				{12002, 500, 0}, {21791, 100_000, 0}, {10446, 500, 0}, {25576, -1, 1}, {22975, 0, 0},
				{23053, 100_000, 1},
			});
		assertEquals(4.236583326045129, r.getDps().getDps(), 1e-9);
		assertEquals("fire wave", r.getLoadout().getSpell().getName().toLowerCase(java.util.Locale.ROOT));
		assertEquals(20714, r.getLoadout().get(Slot.SHIELD).getId());
	}

	/** The trial evaluation alone: each equipment state gets the best of every allowed spell, and keeps it set. */
	@Test
	public void trialTakesItsBestSpell()
	{
		CombatContext ctx = new CombatContext(monsters.monster("chaos dwarf"), new PlayerLevels(69, 73, 81, 64, 60, 67, 88, 61),
			false, false, null).withModifiers(CombatModifiers.builder().wilderness(true).build());
		OptimizerSettings settings = settings(AttackStyle.Type.MAGIC).mode(SearchMode.UNLIMITED).build();
		GearItem staff = catalogue.getItem(Slot.WEAPON, 6563);
		Optimizer optimizer = new Optimizer(catalogue, ctx, settings, id -> true, item -> 0);
		Map<String, String> best = new HashMap<>();
		for (int shield : new int[]{20714, 11924})
		{
			Loadout l = new Loadout();
			l.set(Slot.WEAPON, staff);
			l.set(Slot.SHIELD, catalogue.getItem(Slot.SHIELD, shield));
			AttackStyle autocast = null;
			for (String raw : staff.getStyles())
			{
				AttackStyle style = AttackStyle.parse(raw);
				autocast = style != null && style.isAutocast() ? style : autocast;
			}
			// Independently: every spell this staff can autocast at this Magic level.
			double expected = 0;
			String expectedSpell = null;
			for (com.bestgearsetup.data.Spell spell : catalogue.getSpells())
			{
				String book = spell.getSpellbook() == null ? "" : spell.getSpellbook().toLowerCase(java.util.Locale.ROOT);
				if (spell.getMaxHit() > 0 && spell.getLevel() <= ctx.getMagic() && spell.castableWith(staff.getId())
					&& settings.getSpellbooks().contains(book))
				{
					l.setStyle(autocast);
					l.setSpell(spell);
					double dps = DpsCalculator.calculate(l, ctx).getDps();
					if (dps > expected)
					{
						expected = dps;
						expectedSpell = spell.getName();
					}
				}
			}
			l.setSpell(catalogue.getSpells().get(0));
			double score = optimizer.scoreWithBestOption(l, CombatClass.MAGIC);
			assertEquals(expected, score, 1e-12);
			assertEquals(expectedSpell, l.getSpell().getName());
			assertEquals(score, DpsCalculator.calculate(l, ctx).getDps(), 1e-12);
			best.put(shield == 20714 ? "tome" : "ward", expectedSpell);
		}
		// The best spell depends on the shield, so neither can be chosen before the other.
		assertEquals("fire blast", best.get("tome").toLowerCase(java.util.Locale.ROOT));
		assertTrue(!best.get("ward").equalsIgnoreCase("fire blast"));
	}

	@Test
	public void spellChangesWithOneSlot()
	{
		// Fire Blast loses with the Malediction ward and the Tome of fire loses with Wind Blast; together they win.
		Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
		locks.put(Slot.NECK, SlotLock.empty());
		SetupResult r = best("chaos dwarf", new PlayerLevels(69, 73, 81, 64, 60, 67, 88, 61), false, false, true,
			settings(AttackStyle.Type.MAGIC).mode(SearchMode.UNLIMITED).wildernessRiskLimited(true).maxExpensiveItems(2)
				.expensiveItemThreshold(100_000).attackXp(false).ammoCount(1_000).locks(locks).build(),
			CombatClass.MAGIC, new long[][]{
				{6563, 99_999, 1}, {6910, 99_999, 1}, {20714, 100_000, 1}, {1387, 900_000, 0}, {12002, 12_000, 0},
				{21791, 500, 1}, {12267, 0, 1}, {6141, 99_999, 1}, {1724, 100_000, 1}, {28313, -1, 1},
				{11924, 12_000, 1}, {30066, 900_000, 0}, {581, 500, 1},
			});
		assertEquals(2.7124713964392533, r.getDps().getDps(), 1e-9);
		assertEquals("fire blast", r.getLoadout().getSpell().getName().toLowerCase(java.util.Locale.ROOT));
		assertEquals(20714, r.getLoadout().get(Slot.SHIELD).getId());
	}
}
