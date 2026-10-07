package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.monster;
import static org.junit.Assert.assertEquals;
import com.bestgearsetup.data.Monster;
import org.junit.Test;

public class SpecialAttacksTest
{
	private static Monster target(int defence, int magic, String... attributes)
	{
		Monster m = monster(defence, 0, attributes);
		m.setMagicLevel(magic);
		m.setAttackLevel(200);
		m.setStrengthLevel(200);
		return m;
	}

	@Test
	public void warhammerAndMaulAreMultiplicative()
	{
		// 200 -> 140 -> 98
		assertEquals(98, SpecialAttacks.builder().dragonWarhammer(2).build().apply(target(200, 100)).getDefenceLevel());
		// 200 -> 130
		assertEquals(130, SpecialAttacks.builder().elderMaul(1).build().apply(target(200, 100)).getDefenceLevel());
		// vulnerability first: 200 -> 180 -> 126
		assertEquals(126, SpecialAttacks.builder().vulnerability(true).dragonWarhammer(1).build()
			.apply(target(200, 100)).getDefenceLevel());
	}

	@Test
	public void arclightIsAdditiveAndStrongerOnDemons()
	{
		// 5% of 200 + 1 = 11 per hit
		assertEquals(178, SpecialAttacks.builder().arclight(2).build().apply(target(200, 100)).getDefenceLevel());
		// 10% vs demons
		assertEquals(158, SpecialAttacks.builder().arclight(2).build().apply(target(200, 100, "demon")).getDefenceLevel());
		// emberlight: 5% + 1
		assertEquals(189, SpecialAttacks.builder().emberlight(1).build().apply(target(200, 100)).getDefenceLevel());
	}

	@Test
	public void tonalzticsUsesTargetMagic()
	{
		// 1/8 of 160 magic = 20 per hit
		assertEquals(160, SpecialAttacks.builder().tonalztics(2).build().apply(target(200, 160)).getDefenceLevel());
	}

	@Test
	public void godswordDrainsDefenceThenOtherStats()
	{
		Monster m = target(50, 100);
		Monster drained = SpecialAttacks.builder().bandosGodswordDamage(460).build().apply(m);
		assertEquals(0, drained.getDefenceLevel());
		assertEquals(0, drained.getStrengthLevel());
		assertEquals(0, drained.getAttackLevel());
		// 460 - 50 def - 200 str - 200 att = 10 left for magic
		assertEquals(90, drained.getMagicLevel());
	}

	@Test
	public void defenceFloorIsRespected()
	{
		Monster m = target(200, 100);
		m.setDefenceFloor(150);
		assertEquals(150, SpecialAttacks.builder().dragonWarhammer(3).build().apply(m).getDefenceLevel());
	}

	@Test
	public void magicDrains()
	{
		Monster m = target(200, 100);
		m.setDefMagic(50);
		Monster drained = SpecialAttacks.builder().seercullDamage(30).ayakDamage(20).build().apply(m);
		assertEquals(70, drained.getMagicLevel());
		assertEquals(30, drained.getDefMagic());
		// the cached monster is untouched
		assertEquals(100, m.getMagicLevel());
	}
}
