package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.OwnershipRules;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/** A capped atlatl search must be able to leave the full Eclipse set for stronger mixed equipment. */
public class ChaosDwarfOptimizerTest
{
	private static GameData data;
	private static CombatContext context;
	private static final Set<Integer> OWNED = new HashSet<>(Arrays.asList(
		10828, 6570, 1725, 29280, 2497, 7462, 29286, 11773, 29000, 28991,
		29035, 29031, 29033, 29047, 29043, 29045, 776, 22114, 13239, 6585, 1704, 3105));

	@BeforeClass
	public static void load() throws Exception
	{
		Gson gson = new Gson();
		OwnershipRules.init(gson);
		data = WikiGameData.get(gson).gameData(gson);
		// The live client adds charged/degraded entries with their own identities and prices.
		data.addVariantItems(gson, id ->
		{
			switch (id)
			{
				case 29035: return "Eclipse moon helm";
				case 29031: return "Eclipse moon chestplate";
				case 29033: return "Eclipse moon tassets";
				case 29047: return "Blood moon helm";
				case 29043: return "Blood moon chestplate";
				case 29045: return "Blood moon tassets";
				default: return null;
			}
		}, id -> false);
		Monster monster = WikiMonsters.get(gson).monster(data.matchNpc(291, null, 0).getName());
		context = new CombatContext(monster, new PlayerLevels(82, 87, 80, 81, 76, 76, 87, 68),
			false, false, null).withModifiers(CombatModifiers.builder().wilderness(true).build());
	}

	private static long price(GearItem item)
	{
		String name = item.getName();
		if (name.equals("barrows gloves"))
		{
			return 130_000;
		}
		return name.startsWith("eclipse moon") || name.startsWith("blood moon")
			|| name.equals("eclipse atlatl") || name.equals("berserker ring (i)")
			|| name.equals("primordial boots") || name.equals("amulet of fury") ? 1_000_000 : 1_000;
	}

	private static OptimizerSettings settings()
	{
		return OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY).depth(SearchDepth.BEST)
			.styles(Collections.singleton(AttackStyle.Type.ATLATL)).spellbooks(Collections.emptySet())
			.wildernessRiskLimited(true).maxExpensiveItems(4).expensiveItemThreshold(100_000).build();
	}

	@Test
	public void mixedEquipmentBeatsTheEclipseSetWithinFourExpensiveItems()
	{
		Optimizer optimizer = new Optimizer(data, context, settings(), OWNED::contains, ChaosDwarfOptimizerTest::price);
		SetupResult result = optimizer.optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(5.542224536007131, result.getDps().getDps(), 1e-9);
		assertEquals(22, result.getDps().getMaxHit());
		assertEquals(0.9033389604375305, result.getDps().getAccuracy(), 1e-8);
		assertEquals(10828, result.getLoadout().get(Slot.HEAD).getId());
		assertEquals(29280, result.getLoadout().get(Slot.BODY).getId());
		assertEquals(2497, result.getLoadout().get(Slot.LEGS).getId());
		assertEquals(11773, result.getLoadout().get(Slot.RING).getId());
		assertEquals(4, optimizer.expensiveItemCount(result.getLoadout()));
		assertEquals(0, result.getBuyCost());
	}

	/** Owned melee gear with fixed acquisition values; repair fees come from the normal cost rules. */
	private static final Map<Integer, Long> CRUSH_BANK = new HashMap<>();

	static
	{
		long[][] bank = {
			{10828, 43_682}, {22114, 0}, {1704, 12_103}, {1725, 734}, {28997, 5_178_308}, {29280, 17_253},
			{29283, 26_090}, {29286, 16_306}, {8850, 0}, {12954, 0}, {7462, 130_000}, {13239, 18_895_384},
			{11773, 3_259_456}, {776, 0}, {3105, 754}, {29047, 926_239}, {29043, 3_786_296}, {29045, 8_700_432},
			{28810, 1_628_575}, {1434, 29_900}, {1495, 0}, {6570, 0},
		};
		for (long[] entry : bank)
		{
			CRUSH_BANK.put((int) entry[0], entry[1]);
		}
	}

	private static CombatContext crushContext()
	{
		return context.withModifiers(CombatModifiers.builder().wilderness(true).markOfDarkness(true).miningLevel(70).build());
	}

	private static OptimizerSettings crushSettings()
	{
		return settings().toBuilder().styles(Collections.singleton(AttackStyle.Type.CRUSH)).build();
	}

	/**
	 * With the owned degraded Blood Moon set, the Strength amulet and the aggressive stance are each worse alone
	 * than the Glory on accurate, but better together; the search must change them together.
	 */
	@Test
	public void stanceAndNecklaceChangeTogether()
	{
		Optimizer optimizer = new Optimizer(data, crushContext(), crushSettings(), CRUSH_BANK::containsKey,
			item -> CRUSH_BANK.getOrDefault(item.getId(), 1_000L));
		SetupResult result = optimizer.optimize(CombatClass.MELEE, () -> false).get(0);
		Loadout l = result.getLoadout();
		assertEquals(28997, l.getWeapon().getId());
		assertEquals(1725, l.get(Slot.NECK).getId());
		assertEquals("aggressive", l.getStyle().getStance());
		assertEquals(5.365411110567, result.getDps().getDps(), 1e-9);
		for (Slot slot : Slot.values())
		{
			assertTrue(l.get(slot) == null || CRUSH_BANK.containsKey(l.get(slot).getId()));
		}
		assertEquals(0, result.getBuyCost());
		assertEquals(4, optimizer.expensiveItemCount(l));

		// The trap itself: every single change from accurate + Glory loses DPS.
		double both = DpsCalculator.calculate(l, crushContext()).getDps();
		Loadout glory = l.copy();
		glory.set(Slot.NECK, data.getItem(Slot.NECK, 1704));
		Loadout accurate = l.copy();
		Loadout trapped = glory.copy();
		for (String raw : l.getWeapon().getStyles())
		{
			AttackStyle style = AttackStyle.parse(raw);
			if (style.getType() == AttackStyle.Type.CRUSH && style.getStance().equals("accurate"))
			{
				accurate.setStyle(style);
				trapped.setStyle(style);
			}
		}
		double trap = DpsCalculator.calculate(trapped, crushContext()).getDps();
		assertTrue(DpsCalculator.calculate(glory, crushContext()).getDps() < trap);
		assertTrue(DpsCalculator.calculate(accurate, crushContext()).getDps() < trap);
		assertTrue(both > trap);
	}

	@Test
	public void damageSeedHonoursLockedSlotsAndZeroPurchaseBudget()
	{
		Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
		locks.put(Slot.HEAD, SlotLock.item(29035));
		locks.put(Slot.CAPE, SlotLock.item(22114));
		OptimizerSettings locked = settings().toBuilder().mode(SearchMode.BUDGET).budget(0)
			.locks(locks).build();
		Optimizer optimizer = new Optimizer(data, context, locked, OWNED::contains, ChaosDwarfOptimizerTest::price);
		SetupResult result = optimizer.optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(29035, result.getLoadout().get(Slot.HEAD).getId());
		assertEquals(22114, result.getLoadout().get(Slot.CAPE).getId());
		assertEquals(0, result.getBuyCost());
		assertTrue(optimizer.expensiveItemCount(result.getLoadout()) <= 4);
	}
}
