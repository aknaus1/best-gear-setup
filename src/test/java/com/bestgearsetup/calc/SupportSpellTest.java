package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.util.Collections;
import java.util.EnumSet;
import org.junit.Test;

public class SupportSpellTest
{
	private static final CombatModifiers ALL = CombatModifiers.builder().markOfDarkness(true).charge(true).build();

	@Test
	public void thrallIsAssumedUnlessANonArceuusSpellIsAutocastOrTheTargetIsImmune()
	{
		Monster target = monster(false, false);
		assertEquals(EnumSet.of(SupportSpell.THRALL), SupportSpell.assumed(new Loadout(), context(target, true)));
		assertEquals(EnumSet.noneOf(SupportSpell.class),
			SupportSpell.assumed(loadout("ice barrage", "ancient", null), context(target, true)));
		assertEquals(EnumSet.of(SupportSpell.THRALL),
			SupportSpell.assumed(loadout("undead grasp", "arceuus", null), context(target, true)));
		assertEquals(EnumSet.noneOf(SupportSpell.class), SupportSpell.assumed(new Loadout(), context(target, false)));
		assertEquals(EnumSet.noneOf(SupportSpell.class),
			SupportSpell.assumed(new Loadout(), context(monster(false, true), true)));
	}

	@Test
	public void thrallTierFollowsBaseMagicLevel()
	{
		Monster target = monster(false, false);
		assertEquals(EnumSet.noneOf(SupportSpell.class), SupportSpell.assumed(new Loadout(), context(target, true, 37)));
		assertEquals(Thrall.LESSER, context(target, true, 38).getThrall());
		assertEquals(Thrall.SUPERIOR, context(target, true, 75).getThrall());
		assertEquals(Thrall.GREATER, context(target, true, 76).getThrall());
		assertEquals(EnumSet.of(SupportSpell.THRALL), SupportSpell.assumed(new Loadout(), context(target, true, 38)));
		assertNull(context(target, false, 99).getThrall());
		assertEquals(0.625, Thrall.GREATER.getDps(), 1e-9);
		assertEquals(2 / 2.0 / 2.4, Thrall.SUPERIOR.getDps(), 1e-9);
		assertEquals(1 / 2.0 / 2.4, Thrall.LESSER.getDps(), 1e-9);
	}

	@Test
	public void markOfDarknessNeedsADemonbaneSpellAgainstADemon()
	{
		Loadout demonbane = loadout("dark demonbane", "arceuus", null);
		assertEquals(EnumSet.of(SupportSpell.MARK_OF_DARKNESS),
			SupportSpell.assumed(demonbane, context(monster(true, false), false)));
		assertEquals(EnumSet.noneOf(SupportSpell.class),
			SupportSpell.assumed(demonbane, context(monster(false, false), false)));
		assertEquals(EnumSet.noneOf(SupportSpell.class), SupportSpell.assumed(demonbane,
			context(monster(true, false), false).withModifiers(CombatModifiers.NONE)));
	}

	@Test
	public void chargeNeedsAGodSpellWithItsGodCape()
	{
		Monster target = monster(false, false);
		assertEquals(EnumSet.of(SupportSpell.CHARGE),
			SupportSpell.assumed(loadout("flames of zamorak", "standard", "imbued zamorak cape"), context(target, false)));
		assertEquals(EnumSet.noneOf(SupportSpell.class),
			SupportSpell.assumed(loadout("flames of zamorak", "standard", "saradomin cape"), context(target, false)));
		assertEquals(EnumSet.noneOf(SupportSpell.class),
			SupportSpell.assumed(loadout("fire surge", "standard", "zamorak cape"), context(target, false)));
	}

	private static CombatContext context(Monster target, boolean thrall)
	{
		return context(target, thrall, 99);
	}

	private static CombatContext context(Monster target, boolean thrall, int magic)
	{
		PlayerLevels levels = new PlayerLevels(99, 99, 99, 99, magic, 99, 99, 99);
		return new CombatContext(target, levels, false, 99, 99, 99, magic, null, thrall).withModifiers(ALL);
	}

	private static Monster monster(boolean demon, boolean immuneThrall)
	{
		Monster m = new Monster();
		Monster.Attribute attribute = new Monster.Attribute();
		attribute.setName("demon");
		m.setAttributes(demon ? Collections.singletonList(attribute) : Collections.emptyList());
		m.setImmuneThrall(immuneThrall);
		return m;
	}

	private static Loadout loadout(String spell, String spellbook, String cape)
	{
		Loadout l = new Loadout();
		Spell s = new Spell();
		s.setName(spell);
		s.setSpellbook(spellbook);
		l.setSpell(s);
		if (cape != null)
		{
			GearItem item = new GearItem();
			item.setName(cape);
			l.set(Slot.CAPE, item);
		}
		return l;
	}
}
