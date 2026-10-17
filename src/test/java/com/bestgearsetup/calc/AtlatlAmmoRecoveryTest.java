package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.ConfigItemsForTests;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
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
		assertTrue(new BestGearSetupConfig() { }.requireAtlatlAmmoRecovery());
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
			"masori assembler", "masori assembler max cape", "dizana's quiver", "blessed dizana's quiver", "dizana's max cape"};
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
	public void recoveredAmmoWeaponsRequireRecoveryAcrossDepthsAndFilling()
	{
		fireCape.setRangedStr(30);
		darts.setRangedStr(40);
		for (String[] kind : new String[][] {{"bow", "bow"}, {"crossbow", "crossbow"},
			{"toxic blowpipe", "thrown"}, {"rune knife", "thrown"}})
		{
			GearItem ranged = rangedWeapon(kind[0], kind[1]);
			if (!kind[0].equals("rune knife"))
			{ ranged.setAmmunition(Collections.singletonList(darts.getId())); }
			data = TestData.gameData(Arrays.asList(ranged, darts, fireCape, accumulator), Collections.emptyList());
			OptimizerSettings.OptimizerSettingsBuilder rangedSettings = settings().styles(EnumSet.of(AttackStyle.Type.RANGED));
			assertEquals(kind[0], fireCape, run(rangedSettings.requireAtlatlAmmoRecovery(false)).get(0).getLoadout().get(Slot.CAPE));
			for (SearchDepth depth : SearchDepth.values())
			{
				for (FillMode fill : FillMode.values())
				{
					List<SetupResult> results = run(rangedSettings.requireAtlatlAmmoRecovery(true).depth(depth)
						.fillMode(fill).fillMarginPercent(100));
					assertFalse(kind[0], results.isEmpty());
					assertTrue(kind[0], results.stream().allMatch(s -> s.getLoadout().get(Slot.CAPE) == accumulator));
				}
			}
			Optimizer excluded = optimizer(rangedSettings.excluded(Collections.singleton(accumulator.getId())).build(), id -> true);
			assertTrue(kind[0], excluded.optimize(CombatClass.RANGED, () -> false).isEmpty());
			assertTrue(kind[0], excluded.weaponLockReason(ranged).contains("Ava's"));
		}
	}

	@Test
	public void unrecoveredRangedWeaponsIgnoreTheRequirement()
	{
		fireCape.setRangedStr(30);
		GearItem boltRack = item(4740, "bolt rack", Slot.AMMO);
		boltRack.setRangedStr(55);
		for (String[] kind : new String[][] {{"red chinchompa", "chinchompa"}, {"crystal bow", "bow"},
			{"bow of faerdhinen", "bow"}, {"tonalztics of ralos", "thrown"}, {"hunter's spear", "thrown"},
			{"swamp lizard", "salamander"}, {"karil's crossbow", "crossbow"}})
		{
			GearItem ranged = rangedWeapon(kind[0], kind[1]);
			if (kind[0].equals("karil's crossbow"))
			{ ranged.setAmmunition(Collections.singletonList(boltRack.getId())); }
			data = TestData.gameData(Arrays.asList(ranged, boltRack, fireCape, accumulator), Collections.emptyList());
			OptimizerSettings.OptimizerSettingsBuilder rangedSettings = settings().styles(EnumSet.of(AttackStyle.Type.RANGED));
			assertEquals(kind[0], fireCape, run(rangedSettings).get(0).getLoadout().get(Slot.CAPE));
			for (SlotLock lock : Arrays.asList(SlotLock.empty(), SlotLock.item(fireCape.getId())))
			{
				Optimizer locked = optimizer(rangedSettings.locks(Collections.singletonMap(Slot.CAPE, lock)).build(), id -> true);
				assertFalse(kind[0], locked.optimize(CombatClass.RANGED, () -> false).isEmpty());
				assertNull(kind[0], locked.weaponLockReason(ranged));
			}
		}
	}

	@Test
	public void recoverableAmmoFollowsTheWiki()
	{
		GearItem tar = item(29002, "swamp tar", Slot.AMMO);
		GearItem salamander = weapon(900, "swamp lizard", "salamander", 4,
			"scorch,slash,aggressive", "flare,ranged,accurate", "blaze,magic,magic");
		salamander.setAmmunition(Collections.singletonList(tar.getId()));
		assertFalse(WeaponRules.firesRecoverableAmmo(salamander, CombatClass.RANGED));
		assertTrue(WeaponRules.firesRecoverableAmmo(atlatl, CombatClass.RANGED));
		assertFalse(WeaponRules.firesRecoverableAmmo(atlatl, CombatClass.MELEE));
		GearItem ballista = weapon(901, "heavy ballista", "crossbow", 7, "accurate,ranged,accurate");
		ballista.setAmmunition(Collections.singletonList(902));
		assertTrue(WeaponRules.firesRecoverableAmmo(ballista, CombatClass.RANGED));
		GearItem karils = weapon(906, "karil's crossbow", "crossbow", 4, "rapid,ranged,rapid");
		karils.setAmmunition(Collections.singletonList(4740));
		assertFalse("bolt rack", WeaponRules.firesRecoverableAmmo(karils, CombatClass.RANGED));
		karils.setAmmunition(Arrays.asList(4740, 902));
		assertTrue("bolt rack and bolts", WeaponRules.firesRecoverableAmmo(karils, CombatClass.RANGED));
		for (String thrown : new String[] {"dragon dart", "dragon knife", "dragon thrownaxe", "toktz-xil-ul", "blisterwood stake"})
		{
			assertTrue(thrown, WeaponRules.firesRecoverableAmmo(weapon(903, thrown, "thrown", 3, "rapid,ranged,rapid"), CombatClass.RANGED));
		}
		for (String thrown : new String[] {"holy water", "mud pie", "morrigan's javelin (bh)", "tonalztics of ralos", "hunter's spear"})
		{
			assertFalse(thrown, WeaponRules.firesRecoverableAmmo(weapon(903, thrown, "thrown", 3, "rapid,ranged,rapid"), CombatClass.RANGED));
		}
		assertFalse(WeaponRules.firesRecoverableAmmo(weapon(904, "black chinchompa", "chinchompa", 4, "short fuse,ranged,accurate"),
			CombatClass.RANGED));
		assertFalse(WeaponRules.firesRecoverableAmmo(weapon(905, "crystal bow", "bow", 5, "rapid,ranged,rapid"), CombatClass.RANGED));
	}

	private GearItem rangedWeapon(String name, String subcategory)
	{
		GearItem ranged = weapon(900, name, subcategory, 4, "rapid,ranged,rapid");
		ranged.setRangedBonus(60);
		ranged.setRangedStr(40);
		ranged.setMeleeStr(40);
		return ranged;
	}

	@Test
	public void raidQuickSwitchMustSpendItsSecondChangeOnRecoveryCape()
	{
		GearItem blowpipe = weapon(900, "toxic blowpipe", "thrown", 3, "rapid,ranged,rapid");
		blowpipe.setAmmunition(Collections.singletonList(darts.getId()));
		darts.setRangedStr(40);
		data = TestData.gameData(Arrays.asList(blowpipe, darts, fireCape, accumulator), Collections.emptyList());
		Loadout melee = new Loadout();
		melee.set(Slot.WEAPON, weapon(901, "sword", "sword", 4, "slash,slash,aggressive"));
		melee.set(Slot.CAPE, fireCape);
		List<SetupResult> results = run(settings().styles(EnumSet.of(AttackStyle.Type.RANGED)).switchFrom(melee));
		assertFalse(results.isEmpty());
		for (SetupResult result : results)
		{
			assertEquals(accumulator, result.getLoadout().get(Slot.CAPE));
			assertEquals(2, result.getLoadout().switchesFrom(melee));
		}
		assertTrue(run(settings().styles(EnumSet.of(AttackStyle.Type.RANGED)).switchFrom(melee)
			.excluded(Collections.singleton(accumulator.getId()))).isEmpty());
	}

	@Test
	public void specialAttackSwitchesAreExemptButPrimaryWeaponsAreNot()
	{
		GearItem bowfa = rangedWeapon("bow of faerdhinen", "bow");
		bowfa.setTwoHanded(true);
		bowfa.setRangedBonus(100);
		bowfa.setRangedStr(60);
		GearItem crossbow = weapon(26374, "zaryte crossbow", "crossbow", 5, "rapid,ranged,rapid");
		crossbow.setRangedBonus(110);
		GearItem bolts = item(21905, "dragon bolts", Slot.AMMO);
		bolts.setRangedStr(122);
		crossbow.setAmmunition(Collections.singletonList(bolts.getId()));
		data = TestData.gameData(Arrays.asList(bowfa, crossbow, bolts, fireCape, accumulator), Collections.emptyList());
		Monster target = monster(250, 20);
		target.setHitpoints(600);
		CombatContext rangedCtx = new CombatContext(target, PlayerLevels.maxed(), false, true, null);
		OptimizerSettings.OptimizerSettingsBuilder specs = settings().styles(EnumSet.of(AttackStyle.Type.RANGED))
			.spellbooks(Collections.singleton("standard")).killSpecials(true).specEnergy(SpecEnergy.FULL_BAR);

		// The worn fire cape stays on for the crossbow's spec switch, which the requirement doesn't cover.
		List<SetupResult> locked = new Optimizer(data, rangedCtx, specs.locks(Collections.singletonMap(Slot.CAPE,
			SlotLock.item(fireCape.getId()))).build(), id -> true, GearItem::getPrice).optimize(CombatClass.RANGED, () -> false);
		SetupResult worn = locked.stream().filter(s -> s.getLoadout().getWeapon() == bowfa).findFirst().orElse(null);
		assertNotNull(worn);
		assertNotNull("spec switch dropped", worn.getSpecial());
		assertEquals(fireCape, worn.getLoadout().get(Slot.CAPE));
		assertEquals(crossbow, worn.getSpecial().getWeapon());
		assertEquals(bolts, worn.getSpecial().getAmmo());
		// The crossbow as the primary weapon still needs the recovery cape the lock rules out.
		assertTrue(locked.stream().noneMatch(s -> s.getLoadout().getWeapon() == crossbow));

		List<SetupResult> free = new Optimizer(data, rangedCtx, specs.locks(Collections.emptyMap()).build(), id -> true,
			GearItem::getPrice).optimize(CombatClass.RANGED, () -> false);
		assertTrue(free.stream().anyMatch(s -> s.getLoadout().getWeapon() == crossbow));
		assertTrue(free.stream().filter(s -> s.getLoadout().getWeapon() == crossbow)
			.allMatch(s -> s.getLoadout().get(Slot.CAPE) == accumulator));
	}

	@Test
	public void settingUsesTheNewLabelAndOriginalSavedKey()
	{
		assertTrue(ConfigItemsForTests.items().stream().anyMatch(item ->
			item.getItem().keyName().equals("requireAtlatlAmmoRecovery")
				&& item.getItem().name().equals("Require Ava's / quiver")));
	}
}
