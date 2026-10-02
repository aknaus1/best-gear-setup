package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
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

	private static Loadout gear(String shield)
	{
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, weapon(4151, "abyssal whip", "whip", 4, "flick,slash,accurate"));
		l.setStyle(AttackStyle.parse("flick,slash,accurate"));
		l.set(Slot.SHIELD, shield == null ? null : item(1540, shield, Slot.SHIELD));
		return l;
	}

	private static boolean allowed(String name, String shield, Antifire potion, boolean magic)
	{
		return DragonfireProtection.allowed(target(name), gear(shield),
			OptimizerSettings.builder().antifire(potion).protectMagic(magic).build(), PlayerLevels.maxed());
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
		assertFalse(DragonfireProtection.allowed(target("black dragon"), gear(null), settings, lowPrayer));
		Loadout twoHanded = gear("anti-dragon shield");
		twoHanded.getWeapon().setTwoHanded(true);
		assertFalse(DragonfireProtection.allowed(target("iron dragon"), twoHanded, settings, PlayerLevels.maxed()));
		assertTrue(DragonfireProtection.allowed(target("iron dragon"), twoHanded,
			settings.toBuilder().requireFireProtection(false).build(), PlayerLevels.maxed()));
	}

	@Test
	public void optimizerRetainsProtectiveShieldsAndHonoursLocksAndFilling()
	{
		GearItem whip = gear(null).getWeapon();
		whip.setMeleeStr(82);
		GearItem fire = item(1540, "anti-dragon shield", Slot.SHIELD);
		fire.setPrice(100);
		GearItem defender = item(12954, "dragon defender", Slot.SHIELD);
		defender.setMeleeStr(6);
		defender.setSlashBonus(25);
		defender.setSlashDef(100);
		defender.setPrice(0);
		Monster dragon = target("iron dragon");
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED).allowUntradeables(true)
			.spellbooks(Collections.emptySet()).antifire(Antifire.REGULAR).protectMagic(true)
			.fillMode(FillMode.DEFENCE).fillMarginPercent(100).build();
		CombatContext ctx = new CombatContext(dragon, PlayerLevels.maxed(), false, true, TestData.piety());
		for (boolean locked : new boolean[]{false, true})
		{
			OptimizerSettings s = locked ? settings.toBuilder()
				.locks(Collections.singletonMap(Slot.SHIELD, SlotLock.item(defender.getId()))).build() : settings;
			Optimizer optimizer = new Optimizer(TestData.gameData(Arrays.asList(whip, fire, defender), Collections.emptyList()),
				ctx, s, id -> true, GearItem::getPrice);
			List<SetupResult> setups = optimizer.optimize(CombatClass.MELEE, () -> false);
			if (locked)
			{
				assertTrue(setups.isEmpty());
			}
			else
			{
				assertFalse(setups.isEmpty());
				assertEquals(fire, setups.get(0).getLoadout().get(Slot.SHIELD));
			}
		}
	}
}
