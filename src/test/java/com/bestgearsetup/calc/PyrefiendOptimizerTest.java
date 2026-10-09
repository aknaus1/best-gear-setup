package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/** Pyrefiend comparisons where several strength bonuses cross a max-hit breakpoint together. */
public class PyrefiendOptimizerTest
{
	private static GameData data;
	private static CombatContext ctx;
	private static final OptimizerSettings SETTINGS = OptimizerSettings.builder()
		.mode(SearchMode.OWNED_ONLY)
		.depth(SearchDepth.BEST)
		.spellbooks(Collections.singleton("standard"))
		.excluded(new HashSet<>(Arrays.asList(11230, 12006, 12926)))
		.build();

	@BeforeClass
	public static void load() throws Exception
	{
		Gson gson = RuneLiteAPI.GSON;
		data = WikiGameData.get(gson).gameData(gson);
		Monster monster = WikiMonsters.get(gson).monster(data.matchNpc(433, null, 0).getName());
		ctx = new CombatContext(monster, new PlayerLevels(82, 87, 80, 81, 76, 76, 87, 67), true, false, null);
	}

	private static Set<Integer> meleeOwned()
	{
		return new HashSet<>(Arrays.asList(11865, 29028, 6570, 13121, 6585, 1725, 29280, 29022,
			29283, 29025, 12954, 7462, 13239, 11773, 26219, 28997, 28810, 4587, 31248));
	}

	private static SetupResult best(AttackStyle.Type type, Set<Integer> owned, OptimizerSettings settings)
	{
		return new Optimizer(data, ctx, settings.toBuilder().styles(Collections.singleton(type)).build(),
			owned::contains, item -> 1_000).optimize(type == AttackStyle.Type.RANGED ? CombatClass.RANGED
				: CombatClass.MELEE, () -> false).get(0);
	}

	@Test
	public void threeStrengthTradesMakeDualMacuahuitlBeatFang()
	{
		SetupResult result = best(AttackStyle.Type.STAB, meleeOwned(), SETTINGS);
		assertEquals(28997, result.getLoadout().getWeapon().getId());
		assertEquals(1725, result.getLoadout().get(Slot.NECK).getId());
		assertEquals(29022, result.getLoadout().get(Slot.BODY).getId());
		assertEquals(29025, result.getLoadout().get(Slot.LEGS).getId());
		assertEquals(11865, result.getLoadout().get(Slot.HEAD).getId());
		assertEquals(33, result.getDps().getMaxHit());
		assertEquals(6.349199525945838, result.getDps().getDps(), 1e-9);
	}

	@Test
	public void strengthAmuletAndAggressiveStanceMakeScimitarBeatZombieAxe()
	{
		SetupResult result = best(AttackStyle.Type.SLASH, meleeOwned(), SETTINGS);
		assertEquals(4587, result.getLoadout().getWeapon().getId());
		assertEquals(1725, result.getLoadout().get(Slot.NECK).getId());
		assertEquals("aggressive", result.getLoadout().getStyle().getStance());
		assertEquals(32, result.getDps().getMaxHit());
		assertEquals(6.2497467815797325, result.getDps().getDps(), 1e-9);
	}

	@Test
	public void matchingExclusionsSelectRuneKnives()
	{
		Set<Integer> owned = new HashSet<>(Arrays.asList(11865, 27374, 6585, 868, 811, 810, 12926,
			11230, 29004, 22275, 2497, 7462, 22951));
		SetupResult result = best(AttackStyle.Type.RANGED, owned, SETTINGS);
		assertEquals(868, result.getLoadout().getWeapon().getId());
		assertEquals(14, result.getDps().getMaxHit());
		assertEquals(5.519889137541012, result.getDps().getDps(), 1e-9);
		owned.remove(11230);
		OptimizerSettings knivesExcluded = SETTINGS.toBuilder()
			.excluded(new HashSet<>(Arrays.asList(868, 12006, 12926))).build();
		assertEquals(811, best(AttackStyle.Type.RANGED, owned, knivesExcluded).getLoadout().getWeapon().getId());
	}

	@Test
	public void strengthSeedRespectsLocksOwnershipAndZeroBudget()
	{
		Set<Integer> owned = meleeOwned();
		OptimizerSettings locked = SETTINGS.toBuilder().locks(SlotLock.parse("NECK:6585")).build();
		assertEquals(6585, best(AttackStyle.Type.STAB, owned, locked).getLoadout().get(Slot.NECK).getId());
		owned.remove(1725);
		OptimizerSettings noSpend = SETTINGS.toBuilder().mode(SearchMode.BUDGET).budget(0).build();
		SetupResult affordable = best(AttackStyle.Type.STAB, owned, noSpend);
		assertEquals(0, affordable.getBuyCost());
		assertTrue(affordable.getLoadout().get(Slot.NECK).getId() != 1725);
		assertTrue(affordable.getDps().getDps() < 6.349199525945838);
	}
}
