package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.Arrays;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Yama's void flares: only magic and standard ranged hits are guaranteed maximum (Wiki: Void Flare).
 */
public class VoidFlareTest
{
	private static final Gson GSON = RuneLiteAPI.GSON;
	private static Monster flare;

	@BeforeClass
	public static void loadSnapshot() throws Exception
	{
		flare = WikiMonsters.get(GSON).monster("void flare (fire)");
		assertNotNull(flare);
		assertEquals(14179, flare.getId());
	}

	private static Loadout loadout(String name, String subcategory, String style)
	{
		GearItem w = weapon(40000, name, subcategory, 5, style);
		w.setMeleeStr(80);
		w.setRangedStr(80);
		w.setSlashBonus(80);
		w.setRangedBonus(80);
		w.setMagicBonus(20);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, w);
		l.setStyle(AttackStyle.parse(style));
		return l;
	}

	@Test
	public void onlyMagicAndStandardRangedHitsAreGuaranteedMaximum()
	{
		Monster target = monster(100, 0, "demon");
		target.setId(14179);
		target.setName("void flare (shadow)");
		CombatContext ctx = new CombatContext(target, PlayerLevels.maxed(), false, false, null);
		DpsResult bow = DpsCalculator.calculate(loadout("magic shortbow", "bow", "rapid,ranged,rapid"), ctx);
		DpsResult salamander = DpsCalculator.calculate(loadout("black salamander", "salamander", "flare,ranged,accurate"), ctx);
		DpsResult staff = DpsCalculator.calculate(loadout("trident of the seas", "powered staff", "accurate,magic,accurate"), ctx);
		DpsResult crossbow = DpsCalculator.calculate(loadout("rune crossbow", "crossbow", "rapid,ranged,rapid"), ctx);
		// Karil's crossbow is a heavy weapon on the Wiki (Attack type "Heavy"; bolt racks are bolts).
		DpsResult karil = DpsCalculator.calculate(loadout("karil's crossbow", "crossbow", "rapid,ranged,rapid"), ctx);
		DpsResult knife = DpsCalculator.calculate(loadout("rune knife", "thrown", "accurate,ranged,accurate"), ctx);
		DpsResult whip = DpsCalculator.calculate(loadout("abyssal whip", "whip", "lash,slash,aggressive"), ctx);
		for (DpsResult max : Arrays.asList(bow, salamander, staff))
		{
			assertEquals(1, max.getAccuracy(), 0);
			assertEquals(max.getMaxHit(), max.getAverageHit(), 1e-9);
		}
		for (DpsResult rolled : Arrays.asList(crossbow, karil, knife, whip))
		{
			assertTrue(rolled.getAccuracy() < 1);
			assertTrue(rolled.getAverageHit() < rolled.getMaxHit());
		}
		// The Judge keeps its guaranteed hits with every style, and other targets are unaffected.
		target.setId(14180);
		target.setName("judge of yama");
		assertEquals(1, DpsCalculator.calculate(loadout("abyssal whip", "whip", "lash,slash,aggressive"), ctx).getAccuracy(), 0);
		assertFalse(CombatRules.flareMax(monster(100, 0), loadout("magic shortbow", "bow", "rapid,ranged,rapid")));
	}
}
