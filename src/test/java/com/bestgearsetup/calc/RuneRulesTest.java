package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** Whether a loadout's autocast spell needs a rune the rules forbid, once staff and tome supply are counted. */
public class RuneRulesTest
{
	private static Loadout loadout(String spellName, String weapon, String shield)
	{
		Loadout l = new Loadout();
		if (spellName != null)
		{
			Spell spell = new Spell();
			spell.setName(spellName);
			spell.setSpellbook("standard");
			l.setSpell(spell);
		}
		if (weapon != null)
		{
			l.set(Slot.WEAPON, TestData.item(1, weapon, Slot.WEAPON));
		}
		if (shield != null)
		{
			l.set(Slot.SHIELD, TestData.item(2, shield, Slot.SHIELD));
		}
		return l;
	}

	@Test
	public void fireSurgeWithFireBlockedNeedsAFireSupplier()
	{
		RuneFilter noFire = RuneFilter.parse("fire", "");
		assertTrue(RuneRules.allows(loadout("fire surge", "fire battlestaff", null), noFire));
		assertFalse(RuneRules.allows(loadout("fire surge", "staff of the dead", null), noFire));
		assertTrue(RuneRules.allows(loadout("fire surge", "staff of the dead", "tome of fire"), noFire));
		assertFalse(RuneRules.allows(loadout("fire surge", "staff of the dead", "tome of fire (empty)"), noFire));
		assertFalse(RuneRules.allows(loadout("fire surge", null, null), noFire));
		assertFalse(RuneRules.allows(loadout("fire surge", "staff of air", "tome of water"), noFire));
	}

	@Test
	public void kodaiSuppliesWaterButNothingElse()
	{
		RuneFilter noWater = RuneFilter.parse("water", "");
		assertTrue(RuneRules.allows(loadout("ice barrage", "kodai wand", null), noWater));
		assertFalse(RuneRules.allows(loadout("ice barrage", "staff of fire", null), noWater));
		assertFalse(RuneRules.allows(loadout("ice barrage", null, null), noWater));
		assertTrue(RuneRules.allows(loadout("ice barrage", "staff of the dead", "tome of water"), noWater));
	}

	@Test
	public void twinflameSuppliesWaterAndFire()
	{
		RuneFilter filter = RuneFilter.parse("water,fire", "");
		assertTrue(RuneRules.allows(loadout("ice barrage", "twinflame staff", null), filter));
		// Surge still needs air and wrath, which twinflame does not supply.
		assertFalse(RuneRules.allows(loadout("fire surge", "twinflame staff", null), RuneFilter.parse("wrath", "")));
		assertTrue(RuneRules.allows(loadout("fire surge", "twinflame staff", null), RuneFilter.parse("fire", "")));
	}

	@Test
	public void iceBarrageWithDeathBlockedIsRejectedOnAnyWeapon()
	{
		RuneFilter noDeath = RuneFilter.parse("death", "");
		for (String weapon : new String[]{"kodai wand", "staff of water", "staff of the dead", "twinflame staff",
			"fire battlestaff", null})
		{
			assertFalse(weapon, RuneRules.allows(loadout("ice barrage", weapon, null), noDeath));
		}
	}

	@Test
	public void noSpellIsAlwaysAllowed()
	{
		RuneFilter everything = RuneFilter.parse("air,water,earth,fire,mind,chaos,death,blood,soul,wrath,cosmic", "");
		assertTrue(RuneRules.allows(loadout(null, "abyssal whip", null), everything));
		assertTrue(RuneRules.allows(loadout(null, null, null), everything));
		assertTrue(RuneRules.allows(loadout(null, "trident of the swamp", null), everything));
	}

	@Test
	public void emptyFilterAllowsEverySpell()
	{
		assertTrue(RuneRules.allows(loadout("ice barrage", null, null), RuneFilter.NONE));
		assertTrue(RuneRules.allows(loadout("fire surge", "staff of the dead", null), RuneFilter.NONE));
	}

	@Test
	public void onlyListLimitsSpellsToItsRunes()
	{
		RuneFilter airMind = RuneFilter.parse("", "air,mind");
		assertTrue(RuneRules.allows(loadout("wind strike", null, null), airMind));
		assertFalse(RuneRules.allows(loadout("wind bolt", null, null), airMind));
		assertFalse(RuneRules.allows(loadout("wind bolt", "staff of air", null), airMind));
		assertTrue(RuneRules.allows(loadout("fire strike", "fire battlestaff", null), airMind));
		assertFalse(RuneRules.allows(loadout("fire strike", "staff of air", null), airMind));
		assertFalse(RuneRules.allows(loadout("fire strike", null, null), airMind));
	}

	@Test
	public void blockedWinsOverTheOnlyListForSpells()
	{
		RuneFilter filter = RuneFilter.parse("mind", "air,mind");
		assertFalse(RuneRules.allows(loadout("wind strike", null, null), filter));
		assertTrue(RuneRules.allows(loadout("wind strike", null, null), RuneFilter.parse("", "air,mind")));
	}

	@Test
	public void disallowedListsBlockedRunesInCostOrder()
	{
		Map<Integer, Integer> cost = SpellRunes.perCast("fire surge");
		RuneFilter filter = RuneFilter.parse("wrath,air,fire", "");
		assertEquals(Arrays.asList(ItemID.AIRRUNE, ItemID.FIRERUNE, ItemID.WRATHRUNE),
			RuneRules.disallowed(cost, Collections.emptySet(), filter));
		assertEquals(Arrays.asList(ItemID.AIRRUNE, ItemID.WRATHRUNE),
			RuneRules.disallowed(cost, Collections.singleton(ItemID.FIRERUNE), filter));
		Set<Integer> everything = new HashSet<>(cost.keySet());
		assertTrue(RuneRules.disallowed(cost, everything, filter).isEmpty());
		assertTrue(RuneRules.disallowed(cost, Collections.emptySet(), RuneFilter.NONE).isEmpty());
	}

	@Test
	public void disallowedUsesTheOnlyListToo()
	{
		Map<Integer, Integer> cost = SpellRunes.perCast("wind bolt");
		List<Integer> missing = RuneRules.disallowed(cost, Collections.emptySet(), RuneFilter.parse("", "air,mind"));
		assertEquals(Collections.singletonList(ItemID.CHAOSRUNE), missing);
		assertTrue(RuneRules.disallowed(Collections.emptyMap(), Collections.emptySet(),
			RuneFilter.parse("air", "")).isEmpty());
	}
}
