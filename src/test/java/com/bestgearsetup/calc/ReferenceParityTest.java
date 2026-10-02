package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import org.junit.Test;

/** Cases found by diffing every named special case in the Wiki calculator against this implementation. */
public class ReferenceParityTest
{
	private static final double EPS = 1e-10;

	private static CombatContext context(Monster m, CombatModifiers modifiers)
	{
		return new CombatContext(m, PlayerLevels.maxed(), false, false, null).withModifiers(modifiers);
	}

	@Test
	public void huntersSpearScalesWithStrengthAndMeleeStrengthBonus()
	{
		GearItem spear = weapon(29305, "hunter's spear", "thrown", 6, "accurate,ranged,accurate", "rapid,ranged,rapid");
		spear.setRangedBonus(73);
		spear.setMeleeStr(48);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, spear);
		l.setStyle(AttackStyle.parse("rapid,ranged,rapid"));
		CombatContext ctx = new CombatContext(monster(1, 0), PlayerLevels.maxed(), false, 99, 70, 99, 99, null, false);
		// Visible Strength 70 + 8, melee strength 48: (78 * 112 + 320) / 640 = 14.
		assertEquals(14, DpsCalculator.calculate(l, ctx).getMaxHit());
		// Melee slayer helmet damage applies on task, as for the atlatl.
		l.set(Slot.HEAD, item(11864, "slayer helmet", Slot.HEAD));
		ctx = new CombatContext(monster(1, 0), PlayerLevels.maxed(), true, 99, 70, 99, 99, null, false);
		assertEquals(16, DpsCalculator.calculate(l, ctx).getMaxHit());
		assertEquals(AttackStyle.Type.RANGED, WeaponRules.tabType(spear, l.getStyle()));
	}

	@Test
	public void forinthrySurgeRaisesAvariceAgainstRevenantsOnly()
	{
		GearItem sword = weapon(50000, "test sword", "slash sword", 4, "slash,slash,aggressive");
		sword.setMeleeStr(100);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, sword);
		l.set(Slot.NECK, item(22557, "amulet of avarice", Slot.NECK));
		l.setStyle(AttackStyle.parse("slash,slash,aggressive"));
		Monster revenant = monster(1, 0);
		revenant.setName("revenant knight");
		// Effective strength 110; base max (110 * 164 + 320) / 640 = 28.
		assertEquals(33, DpsCalculator.calculate(l, context(revenant, CombatModifiers.NONE)).getMaxHit());
		CombatModifiers surge = CombatModifiers.builder().forinthrySurge(true).build();
		assertEquals(37, DpsCalculator.calculate(l, context(revenant, surge)).getMaxHit());
		assertEquals(28, DpsCalculator.calculate(l, context(monster(1, 0), surge)).getMaxHit());
	}
}
