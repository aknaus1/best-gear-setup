package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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
import java.util.Locale;
import java.util.Set;
import java.util.function.ToLongFunction;
import org.junit.BeforeClass;
import org.junit.Test;

/** Slayer monsters with mandatory protective equipment: every returned setup must wear it, on or off task. */
public class SlayerEquipmentTest
{
	private static final Gson GSON = new Gson();
	private static final ToLongFunction<GearItem> PRICE = item -> 100_000L;
	private static final OptimizerSettings DEFAULTS = OptimizerSettings.builder().build();
	private static GameData data;
	private static WikiMonsters monsters;

	@BeforeClass
	public static void load() throws Exception
	{
		data = WikiGameData.get(GSON).gameData(GSON);
		monsters = WikiMonsters.get(GSON);
	}

	private static Monster bundled(String name)
	{
		Monster m = monsters.monster(name);
		assertNotNull(name, m);
		return m;
	}

	private static Monster named(String name)
	{
		Monster m = TestData.monster(1, 0);
		m.setName(name);
		return m;
	}

	private static GearItem item(Slot slot, String name)
	{
		for (GearItem item : data.getItems(slot))
		{
			if (item.getName().equalsIgnoreCase(name))
			{
				return item;
			}
		}
		throw new AssertionError(name + " is not in the bundled data");
	}

	private static Loadout wearing(GearItem... items)
	{
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, item(Slot.WEAPON, "abyssal whip"));
		for (GearItem i : items)
		{
			l.set(i.getSlot(), i);
		}
		return l;
	}

	@Test
	public void eachTargetAcceptsOnlyItsProtection()
	{
		GearItem slayerHelm = item(Slot.HEAD, "slayer helmet (i)");
		Object[][] cases = {
			{"dust devil (smoke dungeon)", item(Slot.HEAD, "facemask")},
			{"choke devil", item(Slot.HEAD, "facemask")},
			{"thermonuclear smoke devil", item(Slot.HEAD, "facemask")},
			{"banshee", item(Slot.HEAD, "earmuffs")},
			{"twisted banshee", item(Slot.HEAD, "earmuffs")},
			{"screaming banshee", item(Slot.HEAD, "earmuffs")},
			{"aberrant spectre", item(Slot.HEAD, "nose peg")},
			{"repugnant spectre", item(Slot.HEAD, "nose peg")},
			{"sourhog", item(Slot.HEAD, "reinforced goggles")},
			{"basilisk knight", item(Slot.SHIELD, "mirror shield")},
			{"cockathrice", item(Slot.SHIELD, "v's shield")},
			{"basilisk youngling", item(Slot.SHIELD, "mirror shield")},
			{"harpie bug swarm", item(Slot.SHIELD, "lit bug lantern")},
			{"hydra", item(Slot.FEET, "boots of stone")},
			{"alchemical hydra (fire)", item(Slot.FEET, "granite boots")},
			{"drake", item(Slot.FEET, "boots of brimstone")},
		};
		for (Object[] c : cases)
		{
			Monster target = bundled((String) c[0]);
			GearItem protection = (GearItem) c[1];
			assertTrue(target.getName(), SlayerEquipment.applies(target, DEFAULTS));
			assertFalse(target.getName() + " unprotected", SlayerEquipment.allowed(target, DEFAULTS, wearing()));
			assertTrue(target.getName() + " with " + protection.getName(),
				SlayerEquipment.allowed(target, DEFAULTS, wearing(protection)));
			if (protection.getSlot() == Slot.HEAD)
			{
				assertTrue(target.getName() + " with a slayer helmet", SlayerEquipment.allowed(target, DEFAULTS,
					wearing(slayerHelm)));
			}
		}
		// The quest's sourhog is fought before slayer helmets gain the goggles' protection.
		Monster questHog = bundled("sourhog (a porcine of interest)");
		assertFalse(SlayerEquipment.allowed(questHog, DEFAULTS, wearing()));
		assertFalse(SlayerEquipment.allowed(questHog, DEFAULTS, wearing(slayerHelm)));
		assertTrue(SlayerEquipment.allowed(questHog, DEFAULTS, wearing(item(Slot.HEAD, "reinforced goggles"))));
		// Each head item covers only its own monsters: earmuffs don't stop dust devils.
		assertFalse(SlayerEquipment.allowed(bundled("choke devil"), DEFAULTS, wearing(item(Slot.HEAD, "earmuffs"))));
		// The Wiki names only the mirror shield for younglings.
		assertFalse(SlayerEquipment.allowed(bundled("basilisk youngling"), DEFAULTS, wearing(item(Slot.SHIELD, "v's shield"))));
	}

	@Test
	public void unrelatedVariantsAndRecommendationsAreNotEnforced()
	{
		for (String name : new String[]{"moonlight cockatrice", "wall beast (beast)",
			"wyrm (attacking)", "killerwatt", "fever spider", "cave horror", "vorkath (post-quest)", "zulrah (serpentine)"})
		{
			assertFalse(name, SlayerEquipment.applies(named(name), DEFAULTS));
		}
		assertTrue(SlayerEquipment.notes(named("wall beast (beast)"), DEFAULTS).get(0).contains("start the fight"));
		assertTrue(SlayerEquipment.notes(named("wyrm (attacking)"), DEFAULTS).get(0).contains("Wyrmscraig"));
	}

	@Test
	public void eliteKourendDiaryWaivesOnlyTheKaruulmBoots()
	{
		OptimizerSettings diary = DEFAULTS.toBuilder().kourendEliteDiary(true).build();
		Monster hydra = bundled("hydra");
		assertTrue(SlayerEquipment.inKaruulm(hydra));
		assertFalse(SlayerEquipment.applies(hydra, diary));
		assertTrue(SlayerEquipment.allowed(hydra, diary, wearing()));
		assertTrue(SlayerEquipment.notes(hydra, diary).get(0).contains("Diary complete"));
		assertTrue(SlayerEquipment.applies(bundled("banshee"), diary));
	}

	@Test
	public void twoHandedWeaponsCannotHoldARequiredShield()
	{
		Monster knight = bundled("basilisk knight");
		Loadout l = wearing(item(Slot.SHIELD, "mirror shield"));
		assertTrue(SlayerEquipment.allowed(knight, DEFAULTS, l));
		GearItem twoHanded = TestData.weapon(1, "test greatsword", "2h sword", 6, "chop,slash,accurate");
		twoHanded.setTwoHanded(true);
		l.set(Slot.WEAPON, twoHanded);
		assertFalse(SlayerEquipment.allowed(knight, DEFAULTS, l));
	}

	@Test
	public void everySearchModeStyleAndAoeResultWearsProtectionOffTask()
	{
		Set<Integer> owned = new HashSet<>(Arrays.asList(4587, 1704, 4164));
		Monster dustDevil = bundled("dust devil (smoke dungeon)");
		for (SearchMode mode : SearchMode.values())
		{
			for (int targets : new int[]{1, 9})
			{
				OptimizerSettings settings = OptimizerSettings.builder().mode(mode).budget(10_000_000L)
					.depth(SearchDepth.FAST).resultsPerClass(3)
					.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient"))).build();
				CombatContext ctx = new CombatContext(dustDevil, PlayerLevels.maxed(), false, true, TestData.piety())
					.withFightOptions(targets, 0);
				Optimizer optimizer = new Optimizer(data, ctx, settings, owned::contains, PRICE);
				int found = 0;
				for (CombatClass cls : CombatClass.values())
				{
					for (SetupResult r : optimizer.optimize(cls, () -> false))
					{
						found++;
						assertTrue(mode + " " + cls + " x" + targets, SlayerEquipment.allowed(dustDevil, settings, r.getLoadout()));
					}
				}
				assertTrue(mode + " x" + targets + " found no setup", found > 0);
			}
		}
	}

	@Test
	public void searchesAgainstEachSlotRuleWearTheRequiredItem()
	{
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED).depth(SearchDepth.FAST)
			.resultsPerClass(5).spellbooks(Collections.singleton("standard")).build();
		for (String name : new String[]{"banshee", "aberrant spectre", "sourhog", "sourhog (a porcine of interest)",
			"basilisk knight", "harpie bug swarm", "hydra"})
		{
			Monster target = bundled(name);
			CombatContext ctx = new CombatContext(target, PlayerLevels.maxed(), false, true, TestData.piety());
			Optimizer optimizer = new Optimizer(data, ctx, settings, id -> false, PRICE);
			int found = 0;
			for (CombatClass cls : CombatClass.values())
			{
				for (SetupResult r : optimizer.optimize(cls, () -> false))
				{
					found++;
					assertTrue(name + " " + cls + " " + r.getLoadout().getWeapon().getName(),
						SlayerEquipment.allowed(target, settings, r.getLoadout()));
					assertFalse(name.equals("basilisk knight") && r.getLoadout().getWeapon().isTwoHanded());
				}
			}
			assertTrue(name + " found no setup", found > 0);
		}
	}

	@Test
	public void ownedOnlyWithoutProtectionFindsNothingAndOnTaskUsesASlayerHelmet()
	{
		Monster dustDevil = bundled("dust devil (smoke dungeon)");
		OptimizerSettings owned = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY).depth(SearchDepth.FAST)
			.spellbooks(Collections.singleton("standard")).build();
		CombatContext offTask = new CombatContext(dustDevil, PlayerLevels.maxed(), false, true, TestData.piety());
		Set<Integer> noMask = new HashSet<>(Arrays.asList(4587, 1704));
		assertTrue(new Optimizer(data, offTask, owned, noMask::contains, PRICE).optimize(CombatClass.MELEE, () -> false)
			.isEmpty());

		CombatContext onTask = new CombatContext(dustDevil, PlayerLevels.maxed(), true, true, TestData.piety());
		OptimizerSettings unlimited = owned.toBuilder().mode(SearchMode.UNLIMITED).build();
		GearItem head = new Optimizer(data, onTask, unlimited, id -> false, PRICE).optimize(CombatClass.MELEE, () -> false)
			.get(0).getLoadout().get(Slot.HEAD);
		assertTrue(head.getName().toLowerCase(Locale.ROOT).contains("slayer helmet"));
	}

	@Test
	public void locksWithoutTheRequiredItemAreReportedAsBlocking()
	{
		Object[][] cases = {
			{"dust devil (smoke dungeon)", Slot.HEAD, "eclipse moon helm", "facemask"},
			{"basilisk knight", Slot.SHIELD, "dragon defender", "mirror shield"},
			{"hydra", Slot.FEET, "primordial boots", "boots of stone"},
		};
		for (Object[] c : cases)
		{
			Monster target = bundled((String) c[0]);
			Slot slot = (Slot) c[1];
			GearItem wrong = item(slot, (String) c[2]);
			GearItem right = item(slot, (String) c[3]);
			CombatContext ctx = new CombatContext(target, PlayerLevels.maxed(), false, true, TestData.piety());
			for (SlotLock lock : new SlotLock[]{SlotLock.item(wrong.getId()), SlotLock.empty(), SlotLock.fill()})
			{
				OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED).depth(SearchDepth.FAST)
					.spellbooks(Collections.singleton("standard")).locks(Collections.singletonMap(slot, lock)).build();
				Optimizer optimizer = new Optimizer(data, ctx, settings, id -> false, PRICE);
				assertTrue(target.getName() + " " + lock, optimizer.optimize(CombatClass.RANGED, () -> false).isEmpty());
				List<LockStatus> status = LockStatus.check(data, optimizer, settings.getLocks());
				assertTrue(status.get(0).isBlocking());
				assertTrue(status.get(0).getProblem().contains("requires"));
			}
			OptimizerSettings good = OptimizerSettings.builder().mode(SearchMode.UNLIMITED).depth(SearchDepth.FAST)
				.spellbooks(Collections.singleton("standard"))
				.locks(Collections.singletonMap(slot, SlotLock.item(right.getId()))).build();
			Optimizer optimizer = new Optimizer(data, ctx, good, id -> false, PRICE);
			assertFalse(LockStatus.check(data, optimizer, good.getLocks()).get(0).isBlocking());
			assertEquals(right, optimizer.optimize(CombatClass.RANGED, () -> false).get(0).getLoadout().get(slot));
		}
	}
}
