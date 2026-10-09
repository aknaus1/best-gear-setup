package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.IntPredicate;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class AtlatlAmmoRecoveryTest
{
	private GearItem atlatl;
	private GearItem darts;
	private GearItem fireCape;
	private GearItem accumulator;
	private GameData data;
	private final CombatContext ctx = new CombatContext(monster(100, 20), PlayerLevels.maxed(), false, true, piety());

	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
	}

	@Before
	public void setUp()
	{
		atlatl = weapon(29000, "eclipse atlatl", "bow", 4, "rapid,ranged,rapid");
		atlatl.setRangedBonus(87);
		atlatl.setMeleeStr(40);
		atlatl.setTwoHanded(true);
		atlatl.setAmmunition(Collections.singletonList(29001));
		darts = item(29001, "atlatl dart", Slot.AMMO);
		fireCape = item(6570, "fire cape", Slot.CAPE);
		fireCape.setMeleeStr(30);
		fireCape.setRangedBonus(30);
		fireCape.setPrayerBonus(10);
		accumulator = item(10499, "ava's accumulator", Slot.CAPE);
		accumulator.setTradeable(false);
		data = TestData.gameData(Arrays.asList(atlatl, darts, fireCape, accumulator), Collections.emptyList());
	}

	private OptimizerSettings.OptimizerSettingsBuilder settings()
	{
		return OptimizerSettings.builder().mode(SearchMode.UNLIMITED).requireAtlatlAmmoRecovery(true)
			.spellbooks(Collections.emptySet()).styles(EnumSet.of(AttackStyle.Type.ATLATL));
	}

	private Optimizer optimizer(OptimizerSettings settings, IntPredicate owned)
	{
		return new Optimizer(data, ctx, settings, owned, GearItem::getPrice);
	}

	private List<SetupResult> run(OptimizerSettings.OptimizerSettingsBuilder settings)
	{
		return optimizer(settings.build(), id -> true).optimize(CombatClass.RANGED, () -> false);
	}

	@Test
	public void optionalSettingPreservesUnrestrictedAtlatlResults()
	{
		assertFalse(new BestGearSetupConfig() { }.requireAtlatlAmmoRecovery());
		assertFalse(OptimizerSettings.builder().build().isRequireAtlatlAmmoRecovery());
		assertEquals(fireCape, run(settings().requireAtlatlAmmoRecovery(false)).get(0).getLoadout().get(Slot.CAPE));
	}

	@Test
	public void zeroDamageDeviceSurvivesPruningAndEverySearchDepth()
	{
		assertTrue(optimizer(settings().build(), id -> true).candidates(CombatClass.RANGED).get(Slot.CAPE).contains(accumulator));
		for (SearchDepth depth : SearchDepth.values())
		{
			List<SetupResult> results = run(settings().depth(depth));
			assertEquals(depth.toString(), 1, results.size());
			assertEquals(accumulator, results.get(0).getLoadout().get(Slot.CAPE));
			assertTrue(results.get(0).getDps().getDps() > 0);
		}
	}

	@Test
	public void supportsDevicesAssemblerVariantsAndQuivers()
	{
		String[] names = {"ava's attractor", "ava's accumulator", "ava's assembler", "assembler max cape",
			"masori assembler", "masori assembler max cape", "dizana's quiver", "blessed dizana's quiver"};
		for (String name : names)
		{
			GearItem cape = item(900, name, Slot.CAPE);
			data = TestData.gameData(Arrays.asList(atlatl, darts, fireCape, cape), Collections.emptyList());
			assertEquals(name, cape, run(settings()).get(0).getLoadout().get(Slot.CAPE));
		}
	}

	@Test
	public void ownershipAndExclusionsCanRemoveAllAtlatlResults()
	{
		for (SearchMode mode : Arrays.asList(SearchMode.OWNED_ONLY, SearchMode.BUDGET))
		{
			assertTrue(optimizer(settings().mode(mode).budget(1_000_000).build(), id -> id != accumulator.getId())
				.optimize(CombatClass.RANGED, () -> false).isEmpty());
		}
		assertTrue(run(settings().excluded(Collections.singleton(accumulator.getId()))).isEmpty());
		accumulator.setRangedReq(100);
		assertTrue(run(settings()).isEmpty());
	}

	@Test
	public void cheaperDeviceFitsWhenQuiverExceedsCombinedBudget()
	{
		accumulator.setTradeable(true);
		accumulator.setPrice(100);
		atlatl.setPrice(200);
		GearItem quiver = item(28951, "dizana's quiver", Slot.CAPE);
		quiver.setPrice(250);
		quiver.setRangedBonus(20);
		data = TestData.gameData(Arrays.asList(atlatl, darts, accumulator, quiver), Collections.emptyList());
		OptimizerSettings budget = settings().mode(SearchMode.BUDGET).budget(300).build();
		List<SetupResult> results = optimizer(budget, id -> false).optimize(CombatClass.RANGED, () -> false);
		assertEquals(accumulator, results.get(0).getLoadout().get(Slot.CAPE));
		assertEquals(300, results.get(0).getBuyCost());
		assertTrue(optimizer(budget.toBuilder().budget(299).build(), id -> false)
			.optimize(CombatClass.RANGED, () -> false).isEmpty());
	}

	@Test
	public void capeLocksRespectTheRequirementAndExplainWeaponConflicts()
	{
		for (SlotLock lock : Arrays.asList(SlotLock.empty(), SlotLock.item(fireCape.getId())))
		{
			OptimizerSettings locked = settings().locks(Collections.singletonMap(Slot.CAPE, lock)).build();
			Optimizer optimizer = optimizer(locked, id -> true);
			assertTrue(optimizer.optimize(CombatClass.RANGED, () -> false).isEmpty());
			assertTrue(optimizer.weaponLockReason(atlatl).contains("Ava's"));
		}
		for (SlotLock lock : Arrays.asList(SlotLock.fill(), SlotLock.item(accumulator.getId())))
		{
			assertEquals(accumulator, run(settings().locks(Collections.singletonMap(Slot.CAPE, lock)))
				.get(0).getLoadout().get(Slot.CAPE));
		}
	}

	@Test
	public void slotFillingCannotReplaceTheRecoveryCape()
	{
		for (FillMode fill : FillMode.values())
		{
			assertEquals(accumulator, run(settings().fillMode(fill).fillMarginPercent(100))
				.get(0).getLoadout().get(Slot.CAPE));
		}
	}

	@Test
	public void wildernessRiskStillCountsRequiredCapes()
	{
		accumulator.setPrice(1_000_000);
		assertTrue(run(settings().wildernessRiskLimited(true).maxExpensiveItems(0)).isEmpty());
		assertEquals(accumulator, run(settings().wildernessRiskLimited(true).maxExpensiveItems(1))
			.get(0).getLoadout().get(Slot.CAPE));
	}

	@Test
	public void otherRangedWeaponsKeepTheirUnrestrictedCapes()
	{
		GearItem bow = weapon(900, "bow", "bow", 4, "rapid,ranged,rapid");
		bow.setRangedBonus(60);
		fireCape.setRangedStr(30);
		data = TestData.gameData(Arrays.asList(bow, fireCape), Collections.emptyList());
		assertEquals(fireCape, run(settings().styles(EnumSet.of(AttackStyle.Type.RANGED)))
			.get(0).getLoadout().get(Slot.CAPE));
	}
}
