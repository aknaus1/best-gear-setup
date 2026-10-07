package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import org.junit.Test;

/** Temporary boss states from the Wiki calculator that have no separate monster variant. */
public class EncounterPhaseTest
{
	private static final double EPS = 1e-10;

	private static Loadout loadout(String name, String subcategory, String style)
	{
		GearItem w = weapon(50000, name, subcategory, 4, style);
		w.setStabBonus(100);
		w.setSlashBonus(100);
		w.setCrushBonus(100);
		w.setRangedBonus(100);
		w.setMeleeStr(80);
		w.setRangedStr(80);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, w);
		l.setStyle(AttackStyle.parse(style));
		return l;
	}

	private static Monster target(int id, String name, EncounterPhase phase, String... attributes)
	{
		Monster m = monster(100, 50, attributes);
		m.setId(id);
		m.setName(name);
		m.setHitpoints(1000);
		m.setPhase(phase);
		return m;
	}

	private static DpsResult calculate(Loadout l, Monster m)
	{
		return DpsCalculator.calculate(l, new CombatContext(m, PlayerLevels.maxed(), false, false, null));
	}

	@Test
	public void hueycoatlPillarMultipliesEachCappedTailOutcome()
	{
		Loadout crush = loadout("test maul", "blunt", "pound,crush,aggressive");
		crush.getWeapon().setCrushBonus(101);
		Monster tail = target(14014, "the hueycoatl (tail)", EncounterPhase.HUEYCOATL_PILLAR, "dragon");
		EncounterDamage.Rule rule = EncounterDamage.rule(tail, crush);
		// Caps 0-9 give max(1, trunc(cap * 1.3)): 1,1,2,3,5,6,7,9,10,11.
		assertEquals(5.5, rule.mean(20), EPS);
		assertEquals(11, rule.maximum(20));
		assertEquals(1, rule.missDamage(), EPS);
		tail.setPhase(EncounterPhase.STANDARD);
		assertEquals(4.6, EncounterDamage.rule(tail, crush).mean(20), EPS);
	}

	@Test
	public void hueycoatlPillarAppliesToTheHeadButNotTheBody()
	{
		Loadout slash = loadout("test sword", "slash sword", "slash,slash,aggressive");
		Monster head = target(14009, "the hueycoatl (head)", EncounterPhase.HUEYCOATL_PILLAR, "dragon");
		assertEquals(13, EncounterDamage.rule(head, slash).mean(10), EPS);
		Monster body = target(14017, "the hueycoatl", EncounterPhase.HUEYCOATL_PILLAR, "dragon");
		assertEquals(10, EncounterDamage.rule(body, slash).mean(10), EPS);
		assertTrue(EncounterPhases.describe(body).contains("ignored"));
	}

	@Test
	public void sireTransitionHalvesEachHit()
	{
		Loadout slash = loadout("test sword", "slash sword", "slash,slash,aggressive");
		Monster sire = target(5886, "abyssal sire (phase 1)", EncounterPhase.ABYSSAL_SIRE_TRANSITION, "demon");
		assertEquals(5, EncounterDamage.rule(sire, slash).mean(11), EPS);
		DpsResult halved = calculate(slash, sire);
		sire.setPhase(EncounterPhase.STANDARD);
		DpsResult normal = calculate(slash, sire);
		assertTrue(halved.getAverageHit() < normal.getAverageHit() * 0.55);
	}

	@Test
	public void royalTitansOutOfMeleeMultiplyOnlyTheRangedAttackRoll()
	{
		Loadout bow = loadout("test bow", "bow", "rapid,ranged,rapid");
		Monster titan = target(12596, "branda the fire queen", EncounterPhase.ROYAL_TITANS_OUT_OF_MELEE, "fiery");
		long attack = 107L * 164;
		long defence = 109L * 114;
		assertEquals(HitChance.single(attack * 6, defence), calculate(bow, titan).getAccuracy(), EPS);
		titan.setPhase(EncounterPhase.STANDARD);
		assertEquals(HitChance.single(attack, defence), calculate(bow, titan).getAccuracy(), EPS);
		Loadout sword = loadout("test sword", "slash sword", "slash,slash,accurate");
		titan.setPhase(EncounterPhase.ROYAL_TITANS_OUT_OF_MELEE);
		assertEquals(HitChance.single(110L * 164, defence), calculate(sword, titan).getAccuracy(), EPS);
	}

	@Test
	public void titanElementalMagicAccuracyComesFromTheMagicBonus()
	{
		Monster elemental = target(14150, "fire elemental (royal titans)", EncounterPhase.STANDARD);
		GearItem staff = weapon(11907, "trident of the seas", "powered staff", 4, "accurate,magic,accurate");
		staff.setMagicBonus(20);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, staff);
		l.setStyle(AttackStyle.parse("accurate,magic,accurate"));
		DpsResult r = calculate(l, elemental);
		assertEquals(0.5, r.getAccuracy(), EPS);
		assertEquals(r.getMaxHit() * 0.5, r.getAverageHit(), EPS);
		staff.setMagicBonus(90);
		assertEquals(1.0, calculate(l, elemental).getAccuracy(), EPS);
	}

	@Test
	public void mokhaiotlShieldAllowsOnlyDemonbaneAndEveryNonNormalStateHits()
	{
		Monster doom = target(14707, "doom of mokhaiotl", EncounterPhase.MOKHAIOTL_SHIELDED, "demon");
		Loadout whip = loadout("abyssal whip", "whip", "lash,slash,controlled");
		Loadout arclight = loadout("arclight", "slash sword", "slash,slash,aggressive");
		assertFalse(EncounterImmunities.attackAllowed(doom, whip));
		assertEquals(0, calculate(whip, doom).getDps(), EPS);
		assertTrue(EncounterImmunities.attackAllowed(doom, arclight));
		assertEquals(1.0, calculate(arclight, doom).getAccuracy(), EPS);
		doom.setPhase(EncounterPhase.MOKHAIOTL_BURROWING);
		assertEquals(1.0, calculate(whip, doom).getAccuracy(), EPS);
		doom.setPhase(EncounterPhase.STANDARD);
		assertTrue(calculate(whip, doom).getAccuracy() < 1.0);
		assertNull(EncounterPhases.describe(doom));
	}

	@Test
	public void guaranteedAccuracyVariantsAlwaysHit()
	{
		Monster variant = target(14708, "doom of mokhaiotl (100% accuracy)", EncounterPhase.STANDARD, "demon");
		assertEquals(1.0, calculate(loadout("abyssal whip", "whip", "lash,slash,controlled"), variant).getAccuracy(), EPS);
	}

	@Test
	public void phasesNeverChangeOtherTargets()
	{
		Monster ordinary = target(1, "test monster", EncounterPhase.MOKHAIOTL_SHIELDED);
		Loadout whip = loadout("abyssal whip", "whip", "lash,slash,controlled");
		assertTrue(EncounterImmunities.attackAllowed(ordinary, whip));
		assertTrue(calculate(whip, ordinary).getAccuracy() < 1.0);
		for (EncounterPhase phase : EncounterPhase.values())
		{
			assertFalse(phase.name(), phase != EncounterPhase.STANDARD && EncounterPhases.applies(ordinary, phase));
		}
	}
}
