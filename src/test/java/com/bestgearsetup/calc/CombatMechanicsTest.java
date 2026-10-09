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
import com.bestgearsetup.data.Spell;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.IntUnaryOperator;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

/** Regression vectors and enumerated damage rolls independent of the calculator's averaging code. */
public class CombatMechanicsTest
{
	private static final double EPS = 1e-10;

	private static Loadout loadout(String name, String category, String style)
	{
		GearItem w = weapon(40000, name, category, 5, style);
		w.setMeleeStr(100);
		w.setRangedStr(100);
		w.setStabBonus(100);
		w.setSlashBonus(100);
		w.setCrushBonus(100);
		w.setRangedBonus(100);
		w.setMagicBonus(100);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, w);
		l.setStyle(AttackStyle.parse(style));
		return l;
	}

	private static CombatContext context(Monster target)
	{
		return new CombatContext(target, PlayerLevels.maxed(), false, false, null);
	}

	private static void spell(Loadout l, String name, String book, int max)
	{
		Spell s = new Spell();
		s.setName(name);
		s.setSpellbook(book);
		s.setMaxHit(max);
		l.setSpell(s);
	}

	private static double mean(int min, int max, IntUnaryOperator transform)
	{
		long sum = 0;
		for (int roll = min; roll <= max; roll++)
		{
			sum += transform.applyAsInt(roll);
		}
		return sum / (max - min + 1.0);
	}

	private static void set(Loadout l, String head, String body, String legs)
	{
		l.set(Slot.HEAD, item(40001, head, Slot.HEAD));
		l.set(Slot.BODY, item(40002, body, Slot.BODY));
		l.set(Slot.LEGS, item(40003, legs, Slot.LEGS));
	}

	@Test
	public void fangOnlyRerollsStabAndUsesToaAccuracyRules()
	{
		Loadout fang = loadout("osmumten's fang", "stab sword", "slash,slash,aggressive");
		Monster m = monster(200, 100);
		DpsResult slash = DpsCalculator.calculate(fang, context(m));
		Loadout sword = loadout("ordinary sword", "slash sword", "slash,slash,aggressive");
		assertEquals(DpsCalculator.calculate(sword, context(m)).getAccuracy(), slash.getAccuracy(), EPS);
		assertEquals(24, slash.getMaxHit());
		assertEquals(slash.getAccuracy() * 14, slash.getAverageHit(), EPS);
		fang.setStyle(AttackStyle.parse("stab,stab,aggressive"));
		DpsResult outside = DpsCalculator.calculate(fang, context(m));
		assertTrue(outside.getAccuracy() > slash.getAccuracy());
		m.setId(11719);
		assertEquals(1 - Math.pow(1 - slash.getAccuracy(), 2), DpsCalculator.calculate(fang, context(m)).getAccuracy(), EPS);
	}

	@Test
	public void kerisCriticalRollIsTransformedBeforeBossCaps()
	{
		Monster m = monster(100, 20, "kalphite");
		Loadout l = loadout("keris", "dagger", "stab,stab,aggressive");
		DpsResult normal = DpsCalculator.calculate(l, context(m));
		assertEquals(111, normal.getMaxHit());
		double expected = (50 * mean(0, 37, h -> Math.max(1, h))
			+ mean(0, 37, h -> Math.max(1, h * 3))) / 51;
		assertEquals(normal.getAccuracy() * expected, normal.getAverageHit(), EPS);
		m.setId(2042);
		double capped = (50 * mean(0, 37, h -> Math.max(1, h))
			+ mean(0, 37, h -> h * 3 > 50 ? 0 : Math.max(1, h * 3))) / 51;
		int rerolls = 37 - 16;
		capped += rerolls * 47.5 / 38 / 51;
		// Use a reachable halberd category to isolate the damage transform.
		l.getWeapon().setSubcategory("polearm");
		assertEquals(normal.getAccuracy() * capped, DpsCalculator.calculate(l, context(m)).getAverageHit(), EPS);
	}

	@Test
	public void conditionalAndIndependentSecondHitsDifferAtLowAccuracy()
	{
		Monster m = monster(300, 100);
		Loadout dual = loadout("dual macuahuitl", "blunt", "pummel,crush,aggressive");
		DpsResult conditional = DpsCalculator.calculate(dual, context(m));
		Loadout sulphur = loadout("sulphur blades", "slash sword", "slash,slash,aggressive");
		DpsResult independent = DpsCalculator.calculate(sulphur, context(m));
		double hit = mean(0, 14, h -> Math.max(1, h));
		double a = conditional.getAccuracy();
		assertEquals((a + a * a) * hit, conditional.getAverageHit(), EPS);
		assertEquals(2 * a * hit, independent.getAverageHit(), EPS);
		assertEquals(28, conditional.getMaxHit());
		assertTrue(independent.getDps() > conditional.getDps());
	}

	@Test
	public void bloodMoonUsesMeanSpeedAndThrallsStayIndependent()
	{
		Loadout l = loadout("dual macuahuitl", "blunt", "pummel,crush,aggressive");
		set(l, "blood moon helm", "blood moon chestplate", "blood moon tassets");
		CombatContext ctx = context(monster(100, 20));
		DpsResult r = DpsCalculator.calculate(l, ctx);
		double a = r.getAccuracy();
		// Each roll is 0..99; either roll at 32 or below triggers one tick of reduction.
		int successfulPairs = 0;
		for (int first = 0; first < 100; first++)
		{
			for (int second = 0; second < 100; second++)
			{
				if (Math.min(first, second) <= 32)
				{
					successfulPairs++;
				}
			}
		}
		assertEquals(5511, successfulPairs);
		double firstOnly = a * (1 - a);
		double bothHits = a * a;
		double procChance = firstOnly * 33 / 100 + bothHits * successfulPairs / 10000;
		assertEquals(5 - procChance, r.getExpectedSpeedTicks(), EPS);
		assertEquals(r.getAverageHit() / (r.getExpectedSpeedTicks() * 0.6), r.getDps(), EPS);
		CombatContext thrall = new CombatContext(ctx.getMonster(), ctx.getLevels(), false, 99, 99, 99, 99, null, true);
		assertEquals(r.getDps() + Thrall.GREATER.getDps(), DpsCalculator.calculate(l, thrall).getDps(), EPS);
		// Base Magic 60 casts a superior thrall; the attack itself uses the boosted levels passed in.
		CombatContext superior = new CombatContext(ctx.getMonster(), new PlayerLevels(99, 99, 99, 99, 60, 99, 99, 99),
			false, 99, 99, 99, 99, null, true);
		assertEquals(r.getDps() + Thrall.SUPERIOR.getDps(), DpsCalculator.calculate(l, superior).getDps(), EPS);
	}

	@Test
	public void veracProcBypassesDefenceButStillHasEncounterMitigation()
	{
		Monster m = monster(300, 100);
		m.setId(7550);
		Loadout l = loadout("verac's flail", "flail", "pound,crush,aggressive");
		DpsResult ordinary = DpsCalculator.calculate(l, context(m));
		set(l, "verac's helm", "verac's brassard", "verac's plateskirt");
		DpsResult proc = DpsCalculator.calculate(l, context(m));
		assertEquals(0.75 * ordinary.getAccuracy() + 0.25, proc.getAccuracy(), EPS);
		assertEquals(0.75 * ordinary.getAverageHit() + 0.25 * mean(1, 29, h -> h / 3), proc.getAverageHit(), EPS);
	}

	@Test
	public void dharokScalesRolledHitsAtSelectedHpBeforeMitigation()
	{
		Loadout l = loadout("dharok's greataxe", "axe", "hack,slash,aggressive");
		set(l, "dharok's helm", "dharok's platebody", "dharok's platelegs");
		CombatContext ctx = context(monster(100, 20));
		DpsResult full = DpsCalculator.calculate(l, ctx);
		DpsResult low = DpsCalculator.calculate(l, ctx.withModifiers(CombatModifiers.builder().currentHitpoints(1).build()));
		assertEquals(55, low.getMaxHit());
		assertEquals(low.getAccuracy() * mean(0, 28, h -> Math.max(1, h * 19702 / 10000)), low.getAverageHit(), EPS);
		assertTrue(low.getDps() > full.getDps() * 1.9);
	}

	@Test
	public void barrowsDamnedProcsRequireCompleteSetsAndNecklace()
	{
		Loadout karil = loadout("karil's crossbow", "crossbow", "rapid,ranged,rapid");
		Monster m = monster(100, 20);
		m.setDefHeavy(500);
		DpsResult base = DpsCalculator.calculate(karil, context(m));
		set(karil, "karil's coif", "karil's leathertop", "karil's leatherskirt");
		assertEquals(base.getDps(), DpsCalculator.calculate(karil, context(m)).getDps(), EPS);
		karil.set(Slot.NECK, item(40005, "amulet of the damned", Slot.NECK));
		DpsResult proc = DpsCalculator.calculate(karil, context(m));
		assertEquals(base.getAverageHit() + 0.25 * base.getAccuracy() * mean(0, 27, h -> Math.max(1, h / 2)),
			proc.getAverageHit(), EPS);
		assertEquals(40, proc.getMaxHit());
		Loadout ahrim = loadout("ahrim's staff", "staff", "spell,magic,magic");
		spell(ahrim, "fire surge", "standard", 24);
		set(ahrim, "ahrim's hood", "ahrim's robetop", "ahrim's robeskirt");
		ahrim.set(Slot.NECK, karil.get(Slot.NECK));
		DpsResult magic = DpsCalculator.calculate(ahrim, context(m));
		assertEquals(31, magic.getMaxHit());
		assertEquals(magic.getAccuracy() * (0.75 * mean(0, 24, h -> Math.max(1, h))
			+ 0.25 * mean(0, 24, h -> Math.max(1, h * 13 / 10))), magic.getAverageHit(), EPS);
	}

	@Test
	public void shadowCapsGearDamageAndUsesFourTimesStatsOnlyInsideToa()
	{
		Loadout l = loadout("tumeken's shadow", "powered staff", "accurate,magic,accurate");
		l.getWeapon().setTwoHanded(true);
		l.getWeapon().setMagicStr(20);
		Monster m = monster(100, 20);
		DpsResult outside = DpsCalculator.calculate(l, context(m));
		assertEquals(54, outside.getMaxHit());
		m.setId(11719);
		DpsResult inside = DpsCalculator.calculate(l, context(m));
		assertEquals(61, inside.getMaxHit());
		assertTrue(inside.getAccuracy() > outside.getAccuracy());
		l.getWeapon().setMagicStr(80);
		assertEquals(68, DpsCalculator.calculate(l, context(m)).getMaxHit());
	}

	@Test
	public void elementalWeaknessIsAdditiveToBaseRollAndMagicSalveDamage()
	{
		Loadout l = loadout("smoke battlestaff", "staff", "spell,magic,magic");
		l.getWeapon().setMagicStr(100);
		spell(l, "fire surge", "standard", 24);
		l.set(Slot.NECK, item(40006, "salve amulet(ei)", Slot.NECK));
		Monster m = monster(100, 20, "undead");
		m.setWeaknessType("fire");
		m.setWeakness(50);
		DpsResult r = DpsCalculator.calculate(l, context(m));
		assertEquals(67, r.getMaxHit()); // floor(24 * 2.30) + floor(24 * .50).
		l.set(Slot.SHIELD, item(40007, "tome of fire", Slot.SHIELD));
		assertEquals(73, DpsCalculator.calculate(l, context(m)).getMaxHit());
		spell(l, "water surge", "standard", 22);
		assertEquals(55, DpsCalculator.calculate(l, context(m)).getMaxHit()); // scales to fire-tier base 24.
	}

	@Test
	public void dynamicSpellsChaosGauntletsAndSunfireMinimumApplyInOrder()
	{
		Loadout l = loadout("ordinary staff", "staff", "spell,magic,magic");
		spell(l, "wind bolt", "standard", 9);
		l.set(Slot.HANDS, item(40006, "chaos gauntlets", Slot.HANDS));
		assertEquals(15, DpsCalculator.calculate(l, context(monster(100, 20))).getMaxHit());
		spell(l, "fire surge", "standard", 24);
		l.set(Slot.HANDS, null);
		l.set(Slot.SHIELD, item(40007, "tome of fire", Slot.SHIELD));
		DpsResult r = DpsCalculator.calculate(l, context(monster(100, 20))
			.withModifiers(CombatModifiers.builder().sunfireRunes(true).build()));
		assertEquals(26, r.getMaxHit());
		assertEquals(r.getAccuracy() * 14, r.getAverageHit(), EPS); // uniform 2..26.
	}

	@Test
	public void brimstoneAndMagicDefenceExceptionsChangeAccuracy()
	{
		Loadout l = loadout("trident of the seas", "powered staff", "accurate,magic,accurate");
		Monster m = monster(200, 20);
		m.setMagicLevel(1);
		m.setName("fragment of seren");
		DpsResult defenceBased = DpsCalculator.calculate(l, context(m));
		m.setName("ordinary monster");
		DpsResult magicBased = DpsCalculator.calculate(l, context(m));
		assertTrue(magicBased.getAccuracy() > defenceBased.getAccuracy());
		l.set(Slot.RING, item(40008, "brimstone ring", Slot.RING));
		assertTrue(DpsCalculator.calculate(l, context(m)).getAccuracy() > magicBased.getAccuracy());
	}

	@Test
	public void demonbaneHonoursVulnerabilityAndScorchingBow()
	{
		Monster m = monster(100, 20, "demon");
		m.setName("duke sucellus");
		Loadout l = loadout("emberlight", "slash sword", "slash,slash,aggressive");
		assertEquals(41, DpsCalculator.calculate(l, context(m)).getMaxHit()); // floor(28 * 1.49).
		l = loadout("scorching bow", "bow", "rapid,ranged,rapid");
		assertEquals(32, DpsCalculator.calculate(l, context(m)).getMaxHit()); // floor(27 * 1.21).
	}

	@Test
	public void wildernessBuffIsExplicitAndSoulreaperAddsToPrayerStrength()
	{
		Monster m = monster(100, 20);
		Loadout l = loadout("ursine chainmace", "blunt", "pummel,crush,aggressive");
		CombatContext ctx = context(m);
		assertEquals(28, DpsCalculator.calculate(l, ctx).getMaxHit());
		assertEquals(42, DpsCalculator.calculate(l, ctx.withModifiers(CombatModifiers.builder().wilderness(true).build())).getMaxHit());
		l.getWeapon().setName("soulreaper axe");
		assertEquals(36, DpsCalculator.calculate(l,
			ctx.withModifiers(CombatModifiers.builder().soulreaperStacks(5).build())).getMaxHit());
	}

	@Test
	public void ratWeaponsCannotDamageOtherMonstersAndScurriusRatsTakeOneTickAttacks()
	{
		Loadout l = loadout("bone mace", "blunt", "pummel,crush,aggressive");
		assertEquals(0, DpsCalculator.calculate(l, context(monster(100, 20))).getDps(), 0);
		Monster rat = monster(100, 20, "rat");
		rat.setId(7223);
		rat.setHitpoints(15);
		DpsResult r = DpsCalculator.calculate(l, context(rat));
		assertEquals(15, r.getMaxHit());
		assertEquals(25, r.getDps(), EPS);
		assertEquals(1, r.getAttackSpeedTicks());
	}

	@Test
	public void moonArmourAppliesPerHitAndDoesNotModifyMagic()
	{
		Monster m = monster(100, 20);
		m.setId(13012);
		Loadout l = loadout("sulphur blades", "slash sword", "slash,slash,aggressive");
		DpsResult eclipse = DpsCalculator.calculate(l, context(m));
		assertEquals(16, eclipse.getMaxHit());
		assertEquals(2 * eclipse.getAccuracy() * mean(0, 14, h -> Math.max(0, Math.max(1, h) - 6)), eclipse.getAverageHit(), EPS);
		m.setId(13013);
		DpsResult blue = DpsCalculator.calculate(l, context(m));
		assertEquals(38, blue.getMaxHit());
		assertEquals(2 * blue.getAccuracy() * mean(0, 14, h -> Math.max(1, h) + 5), blue.getAverageHit(), EPS);
		Loadout staff = loadout("trident of the seas", "powered staff", "accurate,magic,accurate");
		assertEquals(28, DpsCalculator.calculate(staff, context(m)).getMaxHit());
	}

	@Test
	public void wardenVariantNamesOverrideConflictingApiIds()
	{
		Monster m = monster(100, 20);
		m.setId(11755);
		m.setName("elidinis' warden (phase 3)");
		assertFalse(CombatRules.wardenCore(m));
		assertFalse(CombatRules.alwaysMax(m, CombatClass.MELEE));
		m.setId(11748);
		m.setName("tumeken's warden (phase 2)");
		assertTrue(CombatRules.wardenP2(m));
		Loadout l = loadout("magic shortbow", "bow", "rapid,ranged,rapid");
		DpsResult r = DpsCalculator.calculate(l, context(m));
		assertEquals(1, r.getAccuracy(), 0);
		assertEquals(11, r.getMaxHit());
		assertEquals(8.5, r.getAverageHit(), EPS); // attack 17548, defence 9156 -> range 6..11.
		m.setId(11759);
		m.setName("elidinis' warden (core-ejected)");
		assertTrue(CombatRules.wardenCore(m));
	}

	@Test
	public void optimizerFindsCompleteVeracSetDespiteItsZeroOffensiveStats()
	{
		Loadout l = loadout("verac's flail", "flail", "pound,crush,aggressive");
		set(l, "verac's helm", "verac's brassard", "verac's plateskirt");
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED).build();
		SetupResult r = new Optimizer(TestData.gameData(Arrays.asList(l.getWeapon(), l.get(Slot.HEAD), l.get(Slot.BODY),
			l.get(Slot.LEGS)), Collections.emptyList()), context(monster(300, 100)), settings, id -> false, GearItem::getPrice)
			.optimize(CombatClass.MELEE, () -> false).get(0);
		assertEquals(l.get(Slot.HEAD), r.getLoadout().get(Slot.HEAD));
		assertEquals(l.get(Slot.BODY), r.getLoadout().get(Slot.BODY));
		assertEquals(l.get(Slot.LEGS), r.getLoadout().get(Slot.LEGS));
	}

	@Test
	public void copiedMonsterRetainsWeaknessAndReplacedAttributesInvalidateCache()
	{
		Monster m = RuneLiteAPI.GSON.fromJson("{\"name\":\"target\",\"weakness_type\":\"air\",\"weakness\":50,"
			+ "\"flat_armour\":6,\"attributes\":[{\"name\":\"undead\"}]}", Monster.class);
		assertTrue(m.hasAttribute("undead"));
		Monster copy = m.copy();
		assertEquals("air", copy.getWeaknessType());
		assertEquals(50, copy.getWeakness());
		assertEquals(Integer.valueOf(6), copy.getFlatArmour());
		m.setAttributes(Collections.emptyList());
		assertFalse(m.hasAttribute("undead"));
		assertTrue(copy.hasAttribute("undead"));
	}
}
