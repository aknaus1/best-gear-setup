package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import com.bestgearsetup.data.StatusImmunities;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** Poison, venom and burn mechanics from the OSRS Wiki status-effect pages. */
public class StatusEffectsTest
{
	private static final double EPS = 1e-9;

	private static StatusEffects.Sources sources(double poison, int severity, double venom, double burn)
	{
		return new StatusEffects.Sources(poison, severity, venom, burn, 1, Collections.emptyList());
	}

	private static Monster target(int id, String name, int hp)
	{
		Monster m = monster(100, 50);
		m.setId(id);
		m.setName(name);
		m.setHitpoints(hp);
		return m;
	}

	private static Loadout loadout(GearItem weapon, String style)
	{
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, weapon);
		l.setStyle(AttackStyle.parse(style));
		return l;
	}

	private static CombatContext context(Monster m, CombatModifiers modifiers)
	{
		return new CombatContext(m, PlayerLevels.maxed(), false, false, null).withModifiers(modifiers);
	}

	@Test
	public void venomMatchesTheWikiDamageTable()
	{
		double[] totals = {0, 6, 14, 24, 36, 50, 66, 84, 104, 124};
		for (int n = 0; n < totals.length; n++)
		{
			assertEquals(totals[n], StatusEffects.Fight.venomCumulative(n), EPS);
		}
	}

	@Test
	public void unrefreshedPoisonTotalsMatchTheWiki()
	{
		// Severity 30 (p++ melee) 105, ranged (p) severity 6 deals 7, emerald bolts 75 or 87 with the Zaryte.
		int[][] cases = {{30, 105}, {20, 50}, {6, 7}, {25, 75}, {27, 87}, {26, 81}};
		for (int[] c : cases)
		{
			StatusEffects.Fight fight = new StatusEffects.Fight(sources(1e-15, c[0], 0, 0), 4);
			assertEquals("severity " + c[0], c[1], fight.poisonCumulative(200), 1e-6);
		}
		assertEquals(6, StatusEffects.Fight.poisonHit(30));
		assertEquals(5, StatusEffects.Fight.poisonHit(22));
	}

	@Test
	public void frequentReapplicationKeepsPoisonAtItsStartingDamage()
	{
		StatusEffects.Fight fight = new StatusEffects.Fight(sources(1, 30, 0, 0), 4);
		assertEquals(6.0 / 30, fight.steadyPerTick(), EPS);
		assertEquals(60, fight.poisonCumulative(10), EPS);
	}

	@Test
	public void killTimeIncludesDamageOverTimeButShortKillsGainLittle()
	{
		StatusEffects.Fight fight = new StatusEffects.Fight(sources(0, 0, 1, 0), 4);
		double longKill = fight.killTime(0.5, 20_000);
		double rate = 20_000 / longKill;
		// Long fights approach the 20-per-30-ticks venom cap.
		assertTrue(rate > 0.5 + 20.0 / 30 - 0.02);
		// 10 HP at 0.5 per tick takes 20 ticks; the first 6-damage venom hit lands within 30 ticks.
		double shortKill = fight.killTime(0.5, 10);
		assertTrue(shortKill > 13 && shortKill < 20);
		assertTrue(fight.damage(0) == 0);
	}

	@Test
	public void bundledImmunitiesOverrideMissingMonsterFlags()
	{
		StatusImmunities.Immunity vorkath = StatusImmunities.of(target(8061, "vorkath", 750));
		assertTrue(vorkath.isPoisonImmune());
		assertTrue(vorkath.isVenomImmune());
		StatusImmunities.Immunity rex = StatusImmunities.of(target(2267, "dagannoth rex", 255));
		assertFalse(rex.isPoisonImmune());
		assertTrue(rex.isVenomBecomesPoison());
		StatusImmunities.Immunity demon = StatusImmunities.of(target(415, "abyssal demon (standard)", 150));
		assertFalse(demon.isVenomImmune());
		assertEquals(StatusImmunities.BURN_NORMAL, StatusImmunities.of(target(5862, "cerberus", 600)).getBurnImmunity());
		assertEquals(StatusImmunities.BURN_STRONG, StatusImmunities.of(target(7706, "tzkal-zuk", 1200)).getBurnImmunity());
		// Unknown ids fall back to the variant's base name.
		assertTrue(StatusImmunities.of(target(999_999, "vorkath (test variant)", 750)).isVenomImmune());
	}

	@Test
	public void venomChancesFollowTheSerpentineHelmRules()
	{
		Monster demon = target(415, "abyssal demon (standard)", 150);
		GearItem whip = weapon(4151, "abyssal whip", "whip", 4, "lash,slash,controlled");
		Loadout l = loadout(whip, "lash,slash,controlled");
		CombatContext ctx = context(demon, CombatModifiers.NONE);
		assertFalse(StatusEffects.sources(l, ctx, 0.8).any());
		l.set(Slot.HEAD, item(12931, "serpentine helm", Slot.HEAD));
		assertEquals(0.8 / 6, StatusEffects.sources(l, ctx, 0.8).getVenomChance(), EPS);
		Loadout dagger = loadout(weapon(1215, "dragon dagger", "stab sword", 4, "lunge,stab,aggressive"), "lunge,stab,aggressive");
		dagger.set(Slot.HEAD, item(12931, "serpentine helm", Slot.HEAD));
		CombatContext poisoned = context(demon, CombatModifiers.builder().weaponPoison(WeaponPoison.POISON_PLUS_PLUS).build());
		assertEquals(0.4, StatusEffects.sources(dagger, poisoned, 0.8).getVenomChance(), EPS);
		GearItem pipe = weapon(12926, "toxic blowpipe", "thrown", 3, "rapid,ranged,rapid");
		Loadout blowpipe = loadout(pipe, "rapid,ranged,rapid");
		assertEquals(0.25 * 0.7, StatusEffects.sources(blowpipe, ctx, 0.7).getVenomChance(), EPS);
		blowpipe.set(Slot.HEAD, item(12931, "serpentine helm", Slot.HEAD));
		assertEquals(0.7, StatusEffects.sources(blowpipe, ctx, 0.7).getVenomChance(), EPS);
	}

	@Test
	public void poisonSourcesUseTheirChanceAndSeverity()
	{
		Monster demon = target(415, "abyssal demon (standard)", 150);
		CombatContext ctx = context(demon, CombatModifiers.builder().weaponPoison(WeaponPoison.POISON_PLUS_PLUS).build());
		StatusEffects.Sources dagger = StatusEffects.sources(
			loadout(weapon(1215, "dragon dagger", "stab sword", 4, "lunge,stab,aggressive"), "lunge,stab,aggressive"), ctx, 0.8);
		assertEquals(0.2, dagger.getPoisonChance(), EPS);
		assertEquals(30, dagger.getPoisonSeverity());
		// Poisoned ranged ammunition: 1/8 per successful hit, severity lowered by 14.
		GearItem bow = weapon(861, "magic shortbow", "bow", 4, "rapid,ranged,rapid");
		bow.setAmmunition(Arrays.asList(892));
		Loadout ranged = loadout(bow, "rapid,ranged,rapid");
		ranged.set(Slot.AMMO, item(892, "rune arrow", Slot.AMMO));
		StatusEffects.Sources arrows = StatusEffects.sources(ranged, ctx, 0.8);
		assertEquals(0.1, arrows.getPoisonChance(), EPS);
		assertEquals(16, arrows.getPoisonSeverity());
		// Emerald bolts: 55% of successful hits, severity 25 (27 with the Zaryte crossbow), never lowered.
		GearItem zcb = weapon(26374, "zaryte crossbow", "crossbow", 5, "rapid,ranged,rapid");
		zcb.setAmmunition(Arrays.asList(9241));
		Loadout bolts = loadout(zcb, "rapid,ranged,rapid");
		bolts.set(Slot.AMMO, item(9241, "emerald bolts (e)", Slot.AMMO));
		StatusEffects.Sources emerald = StatusEffects.sources(bolts, context(demon, CombatModifiers.NONE), 0.5);
		assertEquals(0.275, emerald.getPoisonChance(), EPS);
		assertEquals(27, emerald.getPoisonSeverity());
		// Smoke Barrage with an ancient sceptre: 1/8, severity 22.
		GearItem sceptre = weapon(28264, "smoke ancient sceptre", "staff", 4, "spell,magic,magic");
		Loadout smoke = loadout(sceptre, "spell,magic,magic");
		Spell barrage = new Spell();
		barrage.setName("smoke barrage");
		barrage.setSpellbook("ancient");
		smoke.setSpell(barrage);
		StatusEffects.Sources magic = StatusEffects.sources(smoke, context(demon, CombatModifiers.NONE), 0.8);
		assertEquals(0.1, magic.getPoisonChance(), EPS);
		assertEquals(22, magic.getPoisonSeverity());
	}

	@Test
	public void immuneTargetsTakeNoPoisonOrVenomAndVenomBecomesPoisonWhereListed()
	{
		GearItem pipe = weapon(12926, "toxic blowpipe", "thrown", 3, "rapid,ranged,rapid");
		Loadout blowpipe = loadout(pipe, "rapid,ranged,rapid");
		assertFalse(StatusEffects.sources(blowpipe, context(target(8061, "vorkath", 750), CombatModifiers.NONE), 0.7).any());
		StatusEffects.Sources rex = StatusEffects.sources(blowpipe,
			context(target(2267, "dagannoth rex", 255), CombatModifiers.NONE), 0.8);
		assertEquals(0, rex.getVenomChance(), EPS);
		assertEquals(0.2, rex.getPoisonChance(), EPS);
		assertEquals(26, rex.getPoisonSeverity());
	}

	@Test
	public void burnsRespectSeverityImmunityAndRangedImmunity()
	{
		Loadout atlatl = loadout(weapon(29000, "eclipse atlatl", "bow", 4, "rapid,ranged,rapid"), "rapid,ranged,rapid");
		atlatl.set(Slot.HEAD, item(29010, "eclipse moon helm", Slot.HEAD));
		atlatl.set(Slot.BODY, item(29004, "eclipse moon chestplate", Slot.BODY));
		atlatl.set(Slot.LEGS, item(29007, "eclipse moon tassets", Slot.LEGS));
		// Cerberus resists normal burns only; the Eclipse burn is strong.
		assertTrue(StatusEffects.sources(atlatl, context(target(5862, "cerberus", 600), CombatModifiers.NONE), 0.8).any());
		assertFalse(StatusEffects.sources(atlatl, context(target(7706, "tzkal-zuk", 1200), CombatModifiers.NONE), 0.8).any());
		Monster tekton = target(7540, "tekton (normal)", 300);
		assertEquals(0, StatusEffects.burnFactor(atlatl, context(tekton, CombatModifiers.NONE), StatusImmunities.BURN_STRONG), EPS);
		// Burning claws' special burn is normal severity, so Cerberus takes none of it.
		assertEquals(0, StatusEffects.burnFactor(atlatl, context(target(5862, "cerberus", 600), CombatModifiers.NONE),
			StatusImmunities.BURN_NORMAL), EPS);
	}

	@Test
	public void venomRaisesWholeFightDpsOnSusceptibleTargetsOnly()
	{
		GearItem pipe = weapon(12926, "toxic blowpipe", "thrown", 3, "rapid,ranged,rapid");
		pipe.setRangedBonus(30);
		pipe.setRangedStr(40);
		Loadout l = loadout(pipe, "rapid,ranged,rapid");
		l.set(Slot.HEAD, item(12931, "serpentine helm", Slot.HEAD));
		Monster demon = target(415, "abyssal demon (standard)", 150);
		DpsResult withVenom = DpsCalculator.calculate(l, context(demon, CombatModifiers.NONE));
		DpsResult direct = DpsCalculator.snapshot(l, context(demon, CombatModifiers.NONE));
		assertTrue(withVenom.getDps() > direct.getDps());
		assertTrue(withVenom.getMaxHitDetail().contains("venom"));
		Monster vorkath = target(8061, "vorkath", 750);
		assertEquals(DpsCalculator.snapshot(l, context(vorkath, CombatModifiers.NONE)).getDps(),
			DpsCalculator.calculate(l, context(vorkath, CombatModifiers.NONE)).getDps(), EPS);
	}

	@Test
	public void iceAncientSceptreGainsAccuracyOnlyAgainstFreezableTargets()
	{
		GearItem sceptre = weapon(28262, "ice ancient sceptre", "staff", 4, "spell,magic,magic");
		sceptre.setMagicBonus(20);
		Loadout l = loadout(sceptre, "spell,magic,magic");
		Spell barrage = new Spell();
		barrage.setName("ice barrage");
		barrage.setSpellbook("ancient");
		barrage.setMaxHit(30);
		barrage.setLevel(94);
		l.setSpell(barrage);
		Monster ordinary = target(415, "abyssal demon (standard)", 150);
		double plain = DpsCalculator.snapshot(l.copy(), context(ordinary, CombatModifiers.NONE)).getAccuracy();
		GearItem other = weapon(27624, "ancient sceptre", "staff", 4, "spell,magic,magic");
		other.setMagicBonus(20);
		Loadout baseline = l.copy();
		baseline.set(Slot.WEAPON, other);
		assertTrue(plain > DpsCalculator.snapshot(baseline, context(ordinary, CombatModifiers.NONE)).getAccuracy());
		Monster boss = target(8061, "vorkath", 750);
		boss.setAttributes(Collections.singletonList(attribute("boss")));
		assertEquals(DpsCalculator.snapshot(baseline, context(boss, CombatModifiers.NONE)).getAccuracy(),
			DpsCalculator.snapshot(l, context(boss, CombatModifiers.NONE)).getAccuracy(), EPS);
	}

	private static Monster.Attribute attribute(String name)
	{
		Monster.Attribute a = new Monster.Attribute();
		a.setName(name);
		return a;
	}
}
