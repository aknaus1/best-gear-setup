package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class DragonfireProtectionTest
{
	private static Monster target(String name)
	{
		Monster m = monster(1, 0);
		m.setName(name);
		return m;
	}

	private static Outfit gear(String shield)
	{
		Outfit l = new Outfit();
		l.set(Slot.WEAPON, weapon(4151, "abyssal whip", "whip", 4, "flick,slash,accurate"));
		l.setStyle(Style.parse("flick,slash,accurate"));
		l.set(Slot.SHIELD, shield == null ? null : item(1540, shield, Slot.SHIELD));
		return l;
	}

	private static boolean allowed(String name, String shield, Antifire potion, boolean magic)
	{
		return allowed(target(name), gear(shield),
			OptimizerSettings.builder().antifire(potion).protectMagic(magic).build(), PlayerLevels.maxed());
	}

	/** Meets normal protection requirements, as the search checks them. */
	private static boolean allowed(Monster monster, Outfit loadout, OptimizerSettings settings, PlayerLevels levels)
	{
		return !DragonfireProtection.applies(monster)
			|| DragonfireProtection.protects(monster, loadout, settings, levels);
	}

	@Test
	public void chromaticAndBrutalDragonsAcceptAppropriatePotionPrayerAndShieldCombinations()
	{
		for (String dragon : new String[]{"black dragon", "brutal black dragon", "lava dragon", "frost dragon"})
		{
			assertFalse(allowed(dragon, null, Antifire.NONE, false));
			assertFalse(allowed(dragon, null, Antifire.REGULAR, false));
			assertTrue(allowed(dragon, null, Antifire.REGULAR, true));
			assertTrue(allowed(dragon, "anti-dragon shield", Antifire.REGULAR, false));
			assertTrue(allowed(dragon, null, Antifire.SUPER, false));
			assertFalse(allowed(dragon, "mind shield", Antifire.REGULAR, false));
		}
		assertTrue(allowed("baby black dragon", null, Antifire.NONE, false));
		assertTrue(allowed("wyrm", null, Antifire.NONE, false));
	}

	@Test
	public void metalDragonfireIgnoresOverheadAndWyvernsIgnorePotions()
	{
		assertFalse(allowed("rune dragon", null, Antifire.REGULAR, true));
		assertTrue(allowed("rune dragon", "dragonfire shield", Antifire.REGULAR, false));
		assertTrue(allowed("iron dragon", null, Antifire.SUPER, false));
		for (String wyvern : new String[]{"skeletal wyvern", "ancient wyvern", "spitting wyvern"})
		{
			assertFalse(allowed(wyvern, "anti-dragon shield", Antifire.SUPER, true));
			assertFalse(allowed(wyvern, null, Antifire.SUPER, true));
			assertTrue(allowed(wyvern, "mind shield", Antifire.NONE, false));
			assertTrue(allowed(wyvern, "ancient wyvern shield", Antifire.NONE, false));
		}
	}

	@Test
	public void bossProtectionIsNotAssumedEquivalentToRegularDragons()
	{
		assertFalse(allowed("vorkath (post-quest)", null, Antifire.SUPER, false));
		assertFalse(allowed("vorkath (post-quest)", null, Antifire.REGULAR, true));
		assertTrue(allowed("vorkath (post-quest)", null, Antifire.SUPER, true));
		assertTrue(allowed("vorkath (post-quest)", "dragonfire ward", Antifire.REGULAR, false));
		assertFalse(allowed("king black dragon", null, Antifire.SUPER, false));
		assertTrue(allowed("king black dragon", null, Antifire.SUPER, true));
		assertTrue(allowed("king black dragon", "anti-dragon shield", Antifire.REGULAR, false));
		assertFalse(allowed("galvek", null, Antifire.SUPER, true));
		assertTrue(allowed("galvek", "anti-dragon shield", Antifire.REGULAR, false));
	}

	@Test
	public void lowPrayerAndTwoHandedWeaponsCannotUseMissingProtection()
	{
		PlayerLevels lowPrayer = new PlayerLevels(99, 99, 99, 99, 99, 1, 99, 99);
		OptimizerSettings settings = OptimizerSettings.builder().antifire(Antifire.REGULAR).protectMagic(true).build();
		assertFalse(allowed(target("black dragon"), gear(null), settings, lowPrayer));
		Outfit twoHanded = gear("anti-dragon shield");
		twoHanded.getWeapon().setTwoHanded(true);
		assertFalse(allowed(target("iron dragon"), twoHanded, settings, PlayerLevels.maxed()));
	}

	@Test
	public void optimizerRetainsProtectiveShieldsAndHonoursLocksAndFilling()
	{
		Gear whip = gear(null).getWeapon();
		whip.setMeleeStr(82);
		Gear fire = item(1540, "anti-dragon shield", Slot.SHIELD);
		fire.setPrice(100);
		Gear defender = item(12954, "dragon defender", Slot.SHIELD);
		defender.setMeleeStr(6);
		defender.setSlashBonus(25);
		defender.setSlashDef(100);
		defender.setPrice(0);
		Monster dragon = target("iron dragon");
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED)
			.spellbooks(Collections.emptySet()).antifire(Antifire.REGULAR).protectMagic(true)
			.fillMode(FillMode.DEFENCE).fillMarginPercent(100).build();
		CombatContext ctx = TestFactories.combatContext(dragon, PlayerLevels.maxed(), false, true, TestData.piety());
		for (boolean locked : new boolean[]{false, true})
		{
			OptimizerSettings s = locked ? settings.toBuilder()
				.locks(Collections.singletonMap(Slot.SHIELD, SlotLock.item(defender.getId()))).build() : settings;
			Optimizer optimizer = com.bestgearsetup.calc.Optimizers.of(TestData.gameData(Arrays.asList(whip, fire, defender), Collections.emptyList()),
				ctx, s, id -> true, Gear::getPrice);
			List<Setup> setups = optimizer.optimize(Combat.MELEE, () -> false, done -> { });
			// Locking another shield rules protection out, so it is lifted rather than emptying the search.
			assertEquals(!locked, optimizer.isFireProtectionRequired());
			if (locked)
			{
				assertEquals(defender, setups.get(0).getLoadout().get(Slot.SHIELD));
			}
			else
			{
				assertFalse(setups.isEmpty());
				assertEquals(fire, setups.get(0).getLoadout().get(Slot.SHIELD));
			}
		}
	}

	@Test
	public void protectionIsRequiredOnlyWhenSomethingUsableGivesIt()
	{
		Gear whip = gear(null).getWeapon();
		Gear fire = item(1540, "anti-dragon shield", Slot.SHIELD);
		Gear maul = weapon(13576, "dragon warhammer", "hammer", 6, "pound,crush,accurate");
		maul.setTwoHanded(true);
		OptimizerSettings base = OptimizerSettings.builder().mode(SearchMode.UNLIMITED)
			.spellbooks(Collections.emptySet()).antifire(Antifire.REGULAR).protectMagic(true).build();
		assertTrue("A usable shield is required", required("iron dragon", base, whip, fire, maul));
		assertFalse("Excluding every protective shield lifts it",
			required("iron dragon", base.toBuilder().excluded(Collections.singleton(fire.getId())).build(), whip, fire, maul));
		assertFalse("A shield slot locked empty lifts it", required("iron dragon", base.toBuilder()
			.locks(Collections.singletonMap(Slot.SHIELD, SlotLock.empty())).build(), whip, fire, maul));
		assertFalse("A two-handed weapon lock lifts it", required("iron dragon", base.toBuilder()
			.locks(Collections.singletonMap(Slot.WEAPON, SlotLock.item(maul.getId()))).build(), whip, fire, maul));
		assertFalse("An anti-dragon shield doesn't stop icy breath", required("skeletal wyvern", base, whip, fire, maul));
		assertTrue("Super antifire alone still protects", required("iron dragon", base.toBuilder()
			.antifire(Antifire.SUPER).excluded(Collections.singleton(fire.getId())).build(), whip, fire, maul));
		assertTrue("Antifire with Protect from Magic still protects from chromatics", required("black dragon",
			base.toBuilder().excluded(Collections.singleton(fire.getId())).build(), whip, fire, maul));
		assertFalse("Nothing to block", required("goblin", base, whip, fire, maul));
	}

	@Test
	public void withoutAntifireAFireShieldIsStillRequired()
	{
		Gear whip = gear(null).getWeapon();
		whip.setMeleeStr(82);
		Gear fire = item(1540, "anti-dragon shield", Slot.SHIELD);
		Gear defender = item(12954, "dragon defender", Slot.SHIELD);
		defender.setMeleeStr(6);
		defender.setSlashBonus(25);
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED)
			.spellbooks(Collections.emptySet()).antifire(Antifire.NONE).protectMagic(true).build();
		for (String name : new String[]{"green dragon", "iron dragon"})
		{
			CombatContext ctx = TestFactories.combatContext(target(name), PlayerLevels.maxed(), false, true, TestData.piety());
			Optimizer optimizer = com.bestgearsetup.calc.Optimizers.of(TestData.gameData(Arrays.asList(whip, fire, defender),
				Collections.emptyList()), ctx, settings, id -> true, Gear::getPrice);
			List<Setup> setups = optimizer.optimize(Combat.MELEE, () -> false, done -> { });
			assertFalse(optimizer.isFireProtectionRequired());
			assertTrue(name, optimizer.isFireShieldRequired());
			assertEquals(name, fire, setups.get(0).getLoadout().get(Slot.SHIELD));
		}
		CombatContext wyvern = TestFactories.combatContext(target("skeletal wyvern"), PlayerLevels.maxed(), false, true,
			TestData.piety());
		assertFalse("An anti-dragon shield doesn't cut icy breath", com.bestgearsetup.calc.Optimizers.of(
			TestData.gameData(Arrays.asList(whip, fire, defender), Collections.emptyList()), wyvern, settings, id -> true,
			Gear::getPrice).isFireShieldRequired());
	}

	private static boolean required(String name, OptimizerSettings settings, Gear... items)
	{
		Monster monster = target(name);
		CombatContext ctx = TestFactories.combatContext(monster, PlayerLevels.maxed(), false, true, TestData.piety());
		return com.bestgearsetup.calc.Optimizers.of(TestData.gameData(Arrays.asList(items), Collections.emptyList()),
			ctx, settings, id -> true, Gear::getPrice).isFireProtectionRequired();
	}
}
