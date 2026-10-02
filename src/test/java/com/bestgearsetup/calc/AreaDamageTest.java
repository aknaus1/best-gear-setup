package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class AreaDamageTest
{
	private static Loadout loadout(GearItem weapon, String style)
	{
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, weapon);
		l.setStyle(AttackStyle.parse(style));
		return l;
	}

	private static CombatContext context(Monster target, int targets)
	{
		return new CombatContext(target, PlayerLevels.maxed(), false, true, piety()).withFightOptions(targets, 0);
	}

	@Test
	public void allScytheVariantsUseMonsterSizeAndOnlyUnusedHitsSplash()
	{
		for (String name : new String[]{"scythe of vitur", "holy scythe of vitur", "sanguine scythe of vitur"})
		{
			GearItem w = weapon(22325, name, "scythe", 5, "chop,slash,aggressive");
			w.setMeleeStr(130);
			Loadout l = loadout(w, "chop,slash,aggressive");
			for (int size = 1; size <= 4; size++)
			{
				Monster m = monster(1, 0);
				m.setSize(size);
				DpsResult single = DpsCalculator.calculate(l, context(m, 1));
				int hits = Math.min(3, size);
				int max = 0;
				for (int hit = 0; hit < hits; hit++)
				{
					max += 47 >> hit;
				}
				assertEquals(max, single.getMaxHit());
				double mean = 0;
				for (int hit = 0; hit < hits; hit++)
				{
					int bound = 47 >> hit;
					mean += bound / 2.0 + 1.0 / (bound + 1);
				}
				assertEquals(single.getAccuracy() * mean, single.getAverageHit(), 1e-10);
				DpsResult group = DpsCalculator.calculate(l, context(m, 9));
				assertEquals(single.getPrimaryDps(), group.getPrimaryDps(), 1e-10);
				assertEquals(4 - hits, group.getTargetsHit());
				assertEquals(group.getAccuracy() * (81 / 2.0 + 1.0 / 48 + 1.0 / 24 + 1.0 / 12) / 3,
					group.getDps(), 1e-10);
			}
		}
	}

	@Test
	public void hallowfellCleavesTwoUniqueTargetsAtHalfMaxRegardlessOfMonsterSize()
	{
		GearItem sword = weapon(34027, "hallowfell", "2h sword", 6, "slash,slash,aggressive", "smash,crush,aggressive");
		sword.setMeleeStr(130);
		sword.setTwoHanded(true);
		for (String style : sword.getStyles())
		{
			Loadout l = loadout(sword, style);
			for (int size : new int[]{1, 2, 3, 5})
			{
				Monster m = monster(100, 0);
				m.setSize(size);
				DpsResult single = DpsCalculator.calculate(l, context(m, 1));
				assertEquals(47, single.getMaxHit());
				assertEquals(1, single.getTargetsHit());
				assertEquals(single.getDps(), single.getPrimaryDps(), 1e-10);
				for (int targets : new int[]{2, 3, 9})
				{
					DpsResult group = DpsCalculator.calculate(l, context(m, targets));
					int hits = Math.min(3, targets);
					double secondary = single.getAccuracy() * (23 / 2.0 + 1.0 / 24);
					assertEquals(hits, group.getTargetsHit());
					assertEquals(single.getAverageHit() + secondary * (hits - 1), group.getAverageHit(), 1e-10);
					assertEquals(group.getAverageHit() / 3.6, group.getDps(), 1e-10);
					assertEquals(single.getPrimaryDps(), group.getPrimaryDps(), 1e-10);
					assertEquals(single.getMaxHit(), group.getMaxHit());
				}
			}
		}
	}

	@Test
	public void hallowfellAppliesEncounterCapsToEachCleaveAfterHalvingRawMax()
	{
		GearItem sword = weapon(34027, "hallowfell", "2h sword", 6, "slash,slash,aggressive");
		sword.setMeleeStr(130);
		Loadout l = loadout(sword, "slash,slash,aggressive");
		Monster regular = monster(1, 0);
		int raw = DpsCalculator.calculate(l, context(regular, 1)).getMaxHit();
		Monster verzik = regular.copy();
		verzik.setId(8369);
		verzik.setName("verzik vitur (phase 1)");
		EncounterDamage.Rule rule = EncounterDamage.rule(verzik, l);
		DpsResult area = DpsCalculator.calculate(l, context(verzik, 3));
		assertEquals(area.getAccuracy() * (rule.average(0, raw) + rule.mean(1) / (raw + 1.0)
			+ 2 * (rule.average(0, raw / 2) + rule.mean(1) / (raw / 2 + 1.0))),
			area.getAverageHit(), 1e-10);
		assertEquals(rule.maximum(raw), area.getMaxHit());
		assertEquals(DpsCalculator.calculate(l, context(verzik, 1)).getDps(), area.getPrimaryDps(), 1e-10);
	}

	@Test
	public void hallowfellThrallIsNotMultipliedByCleaveTargets()
	{
		GearItem sword = weapon(34027, "hallowfell", "2h sword", 6, "slash,slash,aggressive");
		sword.setMeleeStr(110);
		Loadout l = loadout(sword, "slash,slash,aggressive");
		Monster m = monster(1, 0);
		CombatContext base = context(m, 3);
		CombatContext thrall = new CombatContext(m, PlayerLevels.maxed(), false, base.getAttack(), base.getStrength(),
			base.getRanged(), base.getMagic(), piety(), true).withFightOptions(3, 0);
		DpsResult before = DpsCalculator.calculate(l, base);
		DpsResult after = DpsCalculator.calculate(l, thrall);
		assertEquals(CombatContext.THRALL_DPS, after.getDps() - before.getDps(), 1e-10);
		assertEquals(CombatContext.THRALL_DPS, after.getPrimaryDps() - before.getPrimaryDps(), 1e-10);
	}

	@Test
	public void optimizerChoosesHallowfellForCleaveButFasterSwordWithAoeOff()
	{
		GearItem hallowfell = weapon(34027, "hallowfell", "2h sword", 6, "slash,slash,aggressive");
		hallowfell.setMeleeStr(110);
		hallowfell.setSlashBonus(117);
		hallowfell.setTwoHanded(true);
		GearItem fast = weapon(1, "fast sword", "slash sword", 4, "slash,slash,aggressive");
		fast.setMeleeStr(110);
		fast.setSlashBonus(117);
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED)
			.spellbooks(Collections.emptySet()).build();
		for (int targets : new int[]{1, 3})
		{
			Optimizer optimizer = new Optimizer(TestData.gameData(Arrays.asList(hallowfell, fast), Collections.emptyList()),
				context(monster(100, 0), targets), settings, id -> false, GearItem::getPrice);
			SetupResult best = optimizer.optimize(CombatClass.MELEE, () -> false).get(0);
			assertEquals(targets == 1 ? fast : hallowfell, best.getLoadout().getWeapon());
		}
	}

	@Test
	public void venatorUsesTwoReducedBouncesAndReturnsLastBounceWithTwoEnemies()
	{
		GearItem bow = weapon(27655, "venator bow", "bow", 5, "rapid,ranged,rapid");
		bow.setRangedStr(130);
		Loadout l = loadout(bow, "rapid,ranged,rapid");
		Monster m = monster(1, 0);
		DpsResult single = DpsCalculator.calculate(l, context(m, 1));
		int bounceMax = single.getMaxHit() * 2 / 3;
		double bounce = single.getAccuracy() * (bounceMax / 2.0 + 1.0 / (bounceMax + 1)) / 2.4;
		for (int targets : new int[]{2, 3, 9})
		{
			DpsResult area = DpsCalculator.calculate(l, context(m, targets));
			assertEquals(single.getDps() + bounce * 2, area.getDps(), 1e-10);
			assertEquals(single.getDps() + (targets == 2 ? bounce : 0), area.getPrimaryDps(), 1e-10);
			assertEquals(Math.min(3, targets), area.getTargetsHit());
			assertEquals(single.getMaxHit() + (targets == 2 ? single.getMaxHit() * 2 / 3 : 0), area.getMaxHit());
		}
	}

	@Test
	public void venatorBouncesApplyCapsAfterScalingRawHit()
	{
		GearItem bow = weapon(27655, "venator bow", "bow", 5, "rapid,ranged,rapid");
		bow.setRangedStr(650);
		Loadout l = loadout(bow, "rapid,ranged,rapid");
		Monster regular = monster(1, 0);
		int raw = DpsCalculator.calculate(l, context(regular, 1)).getMaxHit();
		Monster zulrah = regular.copy();
		zulrah.setId(2042);
		EncounterDamage.Rule rule = EncounterDamage.rule(zulrah, l);
		DpsResult area = DpsCalculator.calculate(l, context(zulrah, 3));
		double expected = area.getAccuracy() * (rule.average(0, raw) + rule.mean(1) / (raw + 1.0)
			+ 2 * (rule.average(0, raw * 2 / 3) + rule.mean(1) / (raw * 2 / 3 + 1.0)));
		assertEquals(expected, area.getAverageHit(), 1e-10);
	}

	@Test
	public void chinchompasUseHeavyDefenceDistanceAndTheirOwnTargetLimit()
	{
		GearItem chin = weapon(10034, "red chinchompa", "chinchompa", 4, "medium fuse,ranged,rapid");
		chin.setRangedStr(15);
		chin.setRangedBonus(70);
		Loadout l = loadout(chin, "medium fuse,ranged,rapid");
		Monster m = monster(100, 0);
		m.setDefHeavy(200);
		m.setDefLight(0);
		CombatContext ctx = context(m, 1);
		DpsResult near = DpsCalculator.calculate(l, ctx.withFightOptions(1, 1));
		DpsResult middle = DpsCalculator.calculate(l, ctx.withFightOptions(1, 5));
		DpsResult far = DpsCalculator.calculate(l, ctx.withFightOptions(1, 8));
		assertTrue(middle.getAccuracy() > near.getAccuracy());
		assertEquals(near.getAccuracy(), far.getAccuracy(), 1e-10);
		DpsResult area = DpsCalculator.calculate(l, ctx.withFightOptions(12, 5));
		assertEquals(11, area.getTargetsHit());
		assertEquals(middle.getDps() * 11, area.getDps(), 1e-10);
		assertEquals(middle.getDps(), area.getPrimaryDps(), 1e-10);
		chin.setName("black chinchompa");
		assertEquals(12, DpsCalculator.calculate(l, ctx.withFightOptions(12, 5)).getTargetsHit());
		m.setDefHeavy(0);
		assertTrue(DpsCalculator.calculate(l, ctx.withFightOptions(1, 5)).getAccuracy() > middle.getAccuracy());
	}

	@Test
	public void ancientBurstAndBarrageSplashButBlitzDoesNot()
	{
		GearItem staff = weapon(4675, "ancient staff", "staff", 5, "spell,magic,magic");
		Loadout l = loadout(staff, "spell,magic,magic");
		Monster m = monster(1, 0);
		for (String name : new String[]{"ice burst", "ice barrage", "blood barrage", "ice blitz"})
		{
			Spell spell = new Spell();
			spell.setName(name);
			spell.setSpellbook("ancient");
			spell.setMaxHit(30);
			l.setSpell(spell);
			DpsResult single = DpsCalculator.calculate(l, context(m, 1));
			DpsResult area = DpsCalculator.calculate(l, context(m, 12));
			int hits = name.endsWith("blitz") ? 1 : 9;
			assertEquals(hits, area.getTargetsHit());
			assertEquals(single.getDps() * hits, area.getDps(), 1e-10);
			assertEquals(single.getDps(), area.getPrimaryDps(), 1e-10);
		}
	}

	@Test
	public void thrallIsAddedOnceAndOrdinaryWeaponsNeverGainAreaDamage()
	{
		Monster m = monster(1, 0);
		GearItem bow = weapon(27655, "venator bow", "bow", 5, "rapid,ranged,rapid");
		bow.setRangedStr(80);
		Loadout l = loadout(bow, "rapid,ranged,rapid");
		CombatContext base = context(m, 3);
		CombatContext thrall = new CombatContext(m, PlayerLevels.maxed(), false, base.getAttack(), base.getStrength(),
			base.getRanged(), base.getMagic(), piety(), true).withFightOptions(3, 0);
		DpsResult before = DpsCalculator.calculate(l, base);
		DpsResult after = DpsCalculator.calculate(l, thrall);
		assertEquals(CombatContext.THRALL_DPS, after.getDps() - before.getDps(), 1e-10);
		assertEquals(CombatContext.THRALL_DPS, after.getPrimaryDps() - before.getPrimaryDps(), 1e-10);
		bow.setName("magic shortbow");
		assertEquals(DpsCalculator.calculate(l, context(m, 1)).getDps(), DpsCalculator.calculate(l, base).getDps(), 1e-10);
		assertEquals(1, DpsCalculator.calculate(l, base).getTargetsHit());
	}

	@Test
	public void optimizerCanChooseLowerSingleTargetSpellForGreaterTotalDamage()
	{
		GearItem staff = weapon(4675, "ancient staff", "staff", 5, "spell,magic,magic");
		Spell burst = new Spell();
		burst.setName("ice burst");
		burst.setSpellbook("ancient");
		burst.setMaxHit(22);
		burst.setSpellWeapons(Collections.singletonList("4675"));
		Spell blitz = new Spell();
		blitz.setName("ice blitz");
		blitz.setSpellbook("ancient");
		blitz.setMaxHit(26);
		blitz.setSpellWeapons(Collections.singletonList("4675"));
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED)
			.spellbooks(Collections.singleton("ancient")).build();
		for (int targets : new int[]{1, 9})
		{
			Optimizer optimizer = new Optimizer(TestData.gameData(Collections.singletonList(staff), Arrays.asList(burst, blitz)),
				context(monster(1, 0), targets), settings, id -> false, GearItem::getPrice);
			SetupResult best = optimizer.optimize(CombatClass.MAGIC, () -> false).get(0);
			assertEquals(targets == 1 ? blitz : burst, best.getLoadout().getSpell());
		}
	}
}
