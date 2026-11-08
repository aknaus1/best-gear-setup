package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/** Temporary: each name rule moved into name-lists.json must answer exactly as its hand-written original did. */
public class NameRulesParityTest
{
	private static List<GearItem> items;
	private static List<Monster> monsters;

	@BeforeClass
	public static void load() throws Exception
	{
		GameData data = WikiGameData.get(RuneLiteAPI.GSON).gameData(RuneLiteAPI.GSON);
		items = new ArrayList<>();
		for (Slot slot : Slot.values())
		{
			items.addAll(data.getItems(slot));
		}
		WikiMonsters wiki = WikiMonsters.get(RuneLiteAPI.GSON);
		monsters = new ArrayList<>();
		for (MonsterSummary summary : wiki.getSummaries())
		{
			Monster m = wiki.monster(summary.getName());
			if (m != null)
			{
				monsters.add(m);
			}
		}
	}

	static Object call(Class<?> owner, String name, Class<?>[] types, Object... args) throws Exception
	{
		Method method = owner.getDeclaredMethod(name, types);
		method.setAccessible(true);
		return method.invoke(null, args);
	}

	private static <T> void same(String rule, List<T> inputs, Function<T, Object> was, Function<T, Object> now)
	{
		for (T input : inputs)
		{
			assertEquals(rule + ": " + input, was.apply(input), now.apply(input));
		}
		System.out.println("PARITY " + rule + ": " + inputs.size() + " compared");
	}

	private static Object unchecked(Callable call)
	{
		try
		{
			return call.call();
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}

	interface Callable
	{
		Object call() throws Exception;
	}

	@Test
	public void attackReach()
	{
		same("AttackReach.baseRange", items, LegacyNameRules::baseRange, AttackReach::baseRange);
		same("AttackReach.flying", monsters, LegacyNameRules::flying,
			m -> unchecked(() -> call(AttackReach.class, "flying", new Class<?>[]{Monster.class}, m)));
	}

	private static List<String> names()
	{
		List<String> names = new ArrayList<>();
		for (GearItem item : items)
		{
			names.add(item.getLowerCombatName());
			names.add(EncounterDamage.lower(item));
		}
		for (Monster m : monsters)
		{
			names.add(m.getLowerName());
		}
		// Prefix and suffix edge cases around every listed pattern.
		for (String n : new ArrayList<>(names))
		{
			names.add(n + " (or)");
		}
		names.add("");
		return names;
	}

	private static boolean in(String list, String name)
	{
		return com.bestgearsetup.data.NameLists.list(list).matches(name);
	}

	private static int table(String table, String name, int fallback)
	{
		return com.bestgearsetup.data.NameLists.table(table).get(name, fallback);
	}

	@Test
	public void nameListsAndTables()
	{
		List<String> names = names();
		same("protective shields", items, LegacyNameRules::isProtectiveShield, DragonfireProtection::isProtectiveShield);
		same("demonbane melee", names, LegacyNameRules::demonbaneMelee, n -> in("demonbane melee weapons", n));
		same("abyssal", names, LegacyNameRules::abyssalWeapon, n -> in("abyssal weapons", n));
		same("vampyrebane t3", names, n -> LegacyNameRules.vampyrebaneMelee(n, true), n -> in("vampyrebane melee weapons", n));
		same("vampyrebane t2", names, n -> LegacyNameRules.vampyrebaneMelee(n, false),
			n -> in("vampyrebane melee weapons", n) || n.startsWith("rod of ivandis"));
		same("silver melee", names, LegacyNameRules::silverMelee, n -> in("silver melee weapons", n));
		same("broad ammunition", names, LegacyNameRules::broadAmmo, n -> in("broad ammunition", n));
		same("vampyre percent", names, LegacyNameRules::vampyrePercent, n -> table("vampyrebane damage percent", n, -1));
		same("pickaxe bonus", names, LegacyNameRules::pickaxeBonus, n -> table("guardian pickaxe bonus", n, 61));
		same("two-hit", names, LegacyNameRules::twoHit, n -> in("two-hit weapons", n));
		same("rat weapons", names, LegacyNameRules::ratWeapon, n -> in("rat weapons", n));
		same("wilderness melee", names, n -> LegacyNameRules.wildernessWeapon(n, CombatClass.MELEE), n -> in("wilderness melee weapons", n));
		same("wilderness ranged", names, n -> LegacyNameRules.wildernessWeapon(n, CombatClass.RANGED), n -> in("wilderness ranged weapons", n));
		same("wilderness magic", names, n -> LegacyNameRules.wildernessWeapon(n, CombatClass.MAGIC), n -> in("wilderness magic weapons", n));
		same("salamander bonus", names, LegacyNameRules::salamanderBonus, n -> table("salamander magic bonus", n, -1));
		same("recovery capes", names, LegacyNameRules::recoveryCapeName, n -> in("ammo recovery capes", n));
		same("venom helms", names, LegacyNameRules::venomHelm, n -> in("venom helms", n));
		List<String> mixedCase = new ArrayList<>(names);
		for (Monster m : monsters)
		{
			mixedCase.add(m.getName());
		}
		mixedCase.add(null);
		same("wilderness-only", mixedCase, LegacyNameRules::wildernessOnly, WildernessTargets::only);
	}

	@Test
	public void slayerEquipment()
	{
		int checked = 0;
		for (boolean diary : new boolean[]{false, true})
		{
			for (boolean karuulm : new boolean[]{false, true})
			{
				OptimizerSettings settings = OptimizerSettings.builder().kourendEliteDiary(diary).karuulmDungeon(karuulm).build();
				for (Monster m : monsters)
				{
					String where = m.getName() + " diary " + diary + " karuulm " + karuulm;
					assertEquals(where, LegacySlayerEquipment.applies(m, settings), SlayerEquipment.applies(m, settings));
					assertEquals(where, LegacySlayerEquipment.notes(m, settings), SlayerEquipment.notes(m, settings));
					assertEquals(where, LegacySlayerEquipment.inKaruulm(m), SlayerEquipment.inKaruulm(m));
					assertEquals(where, LegacySlayerEquipment.inKaruulm(m, settings), SlayerEquipment.inKaruulm(m, settings));
					if (!LegacySlayerEquipment.applies(m, settings))
					{
						continue;
					}
					for (GearItem item : items)
					{
						assertEquals(where + " " + item.getName(), LegacySlayerEquipment.isRequired(m, settings, item),
							SlayerEquipment.isRequired(m, settings, item));
						assertEquals(where + " " + item.getName(), LegacySlayerEquipment.lockConflict(m, settings, item.getSlot(), item),
							SlayerEquipment.lockConflict(m, settings, item.getSlot(), item));
						checked++;
					}
				}
			}
		}
		System.out.println("PARITY slayer equipment: " + checked + " monster-item pairs compared");
	}

	@Test
	public void optimizerCostsAndLeagues()
	{
		List<String> names = names();
		same("special gear", names, LegacyNameRules::specialName, n -> in("special gear", n));
		same("magic-only gear", names, LegacyNameRules::magicOnly, n -> in("magic-only gear", n));
		List<String> raw = new ArrayList<>(names);
		for (String n : LegacyNameRules.REPAIR_COSTS.keySet())
		{
			raw.add(n);
			raw.add("decorative armour " + n);
		}
		same("pvp repair fee", items, i -> LegacyNameRules.repairFee(i.getName() == null ? "" : i.getName().toLowerCase(java.util.Locale.ROOT)),
			com.bestgearsetup.ItemCosts::pvpRepairCost);
		java.util.Map<String, Integer> fees = com.bestgearsetup.data.NameLists.exact("pvp repair fees");
		assertEquals(LegacyNameRules.REPAIR_COSTS.size(), fees.size());
		LegacyNameRules.REPAIR_COSTS.forEach((n, fee) -> assertEquals(n, fee.longValue(), (long) fees.get(n)));
		same("league equipment", raw, LegacyNameRules::leagueName, n -> in("league equipment", n));
		same("GearItem.isLeagueEquipment", items, i -> LegacyNameRules.leagueName(i.getName().toLowerCase(java.util.Locale.ROOT))
			&& !i.isDmmEquipment(), GearItem::isLeagueEquipment);
	}
}
