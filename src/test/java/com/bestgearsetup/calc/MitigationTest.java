package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class MitigationTest
{
	private static Dps calculate(Gear weapon, String style, Monster monster, boolean thrall)
	{
		Outfit loadout = new Outfit();
		loadout.set(Slot.WEAPON, weapon);
		loadout.setStyle(Style.parse(style));
		CombatContext ctx = TestFactories.combatContext(monster, PlayerLevels.maxed(), false, true, piety());
		if (thrall)
		{
			ctx = new CombatContext(monster, PlayerLevels.maxed(), false, ctx.getAttack(), ctx.getStrength(),
				ctx.getRanged(), ctx.getMagic(), piety(), true);
		}
		return DpsCalculator.calculate(loadout, ctx);
	}

	@Test
	public void allOlmPartsApplyOffStyleReductionInNormalAndChallengeMode()
	{
		String[] styles = {"swipe,slash,aggressive", "rapid,ranged,rapid", "accurate,magic,accurate"};
		Combat[] classes = {Combat.MELEE, Combat.RANGED, Combat.MAGIC};
		Gear[] weapons = {
			weapon(29796, "noxious halberd", "polearm", 5),
			weapon(861, "magic shortbow", "bow", 4),
			weapon(11907, "trident of the seas", "powered staff", 4)
		};
		weapons[0].setMeleeStr(130);
		weapons[0].setSlashBonus(100);
		weapons[1].setRangedStr(130);
		weapons[1].setRangedBonus(100);
		weapons[2].setMagicBonus(100);
		int[] ids = {7550, 7551, 7552, 7553, 7554, 7555};
		Combat[] preferred = {Combat.MAGIC, Combat.RANGED, Combat.MELEE,
			Combat.MAGIC, Combat.RANGED, Combat.MELEE};
		for (int part = 0; part < ids.length; part++)
		{
			for (int style = 0; style < styles.length; style++)
			{
				Monster target = monster(100, 20);
				Dps base = calculate(weapons[style], styles[style], target, false);
				target.setId(ids[part]);
				Dps reduced = calculate(weapons[style], styles[style], target, false);
				int divisor = classes[style] == preferred[part] ? 1 : 3;
				long sum = 0;
				for (int hit = 0; hit <= base.getMaxHit(); hit++)
				{
					sum += Math.max(1, hit) / divisor;
				}
				assertEquals(base.getMaxHit() / divisor, reduced.getMaxHit());
				assertEquals(base.getAccuracy(), reduced.getAccuracy(), 1e-12);
				double mean = base.getAccuracy() * sum / (base.getMaxHit() + 1.0);
				assertEquals(mean, reduced.getAverageHit(), 1e-12);
				assertEquals(mean / (base.getAttackSpeedTicks() * 0.6), reduced.getDps(), 1e-12);
				assertEquals(reduced.getDps() + Thrall.GREATER.getDps(),
					calculate(weapons[style], styles[style], target, true).getDps(), 1e-12);
			}
		}
	}

	@Test
	public void namesDistinguishOlmPartsWhenIdsAreUnknown()
	{
		String[] names = {"Great Olm (head)", "great olm (mage hand)", "great olm (melee hand)"};
		Combat[] preferred = {Combat.RANGED, Combat.MAGIC, Combat.MELEE};
		for (int i = 0; i < names.length; i++)
		{
			Monster target = monster(100, 20);
			target.setName(names[i]);
			assertEquals(preferred[i], Mitigation.olmPreferredClass(target));
		}
	}

	@Test
	public void scytheDividesEachHitBeforeSummingItsMaximumAndMean()
	{
		Monster target = monster(100, 20);
		target.setSize(3);
		Gear scythe = weapon(22325, "scythe of vitur", "scythe", 5);
		scythe.setMeleeStr(130);
		Dps base = calculate(scythe, "reap,slash,aggressive", target, false);
		target.setId(7550);
		Dps reduced = calculate(scythe, "reap,slash,aggressive", target, false);
		int maximum = 0;
		double mean = 0;
		for (String component : base.getMaxHitDetail().split(" \\+ "))
		{
			int raw = Integer.parseInt(component);
			maximum += raw / 3;
			int sum = 0;
			for (int hit = 0; hit <= raw; hit++)
			{
				sum += hit / 3;
			}
			mean += base.getAccuracy() * sum / (raw + 1.0);
		}
		assertEquals(maximum, reduced.getMaxHit());
		assertEquals(mean, reduced.getAverageHit(), 1e-12);
	}

	@Test
	public void fangReducesItsBoundedDamageRollRatherThanAnOrdinaryZeroToMaxRoll()
	{
		Monster target = monster(100, 20);
		Gear fang = weapon(26219, "osmumten's fang", "stab sword", 5);
		fang.setMeleeStr(130);
		Dps base = calculate(fang, "stab,stab,aggressive", target, false);
		int minimum = Integer.parseInt(base.getMaxHitDetail().split("-")[0]);
		int sum = 0;
		for (int hit = minimum; hit <= base.getMaxHit(); hit++)
		{
			sum += hit / 3;
		}
		target.setId(7550);
		Dps reduced = calculate(fang, "stab,stab,aggressive", target, false);
		assertEquals(base.getMaxHit() / 3, reduced.getMaxHit());
		assertEquals(base.getAccuracy() * sum / (base.getMaxHit() - minimum + 1.0), reduced.getAverageHit(), 1e-12);
	}

	@Test
	public void zulrahRerollsOnlyHitsAboveFiftyAndChangesTheExpectedDamage()
	{
		Monster target = monster(100, 20);
		target.setId(2042);
		Outfit loadout = loadout("noxious halberd", "polearm", "swipe,slash,aggressive");
		Mitigation.Rule rule = Mitigation.rule(target, loadout);
		assertEquals(50, rule.mean(50), 0);
		assertEquals(47.5, rule.mean(51), 0);
		assertEquals(25, rule.average(0, 50), 1e-12);
		assertEquals((1275 + 47.5) / 52, rule.average(0, 51), 1e-12);
		assertEquals((1275 + 30 * 47.5) / 81, rule.average(0, 80), 1e-12);
		assertEquals(50, rule.maximum(80));
		loadout.getWeapon().setMeleeStr(600);
		Monster ordinary = monster(100, 20);
		Dps base = calculate(loadout.getWeapon(), "swipe,slash,aggressive", ordinary, false);
		Dps capped = calculate(loadout.getWeapon(), "swipe,slash,aggressive", target, false);
		assertTrue(base.getMaxHit() > 50);
		assertEquals(50, capped.getMaxHit());
		assertEquals(base.getAccuracy() * (rule.average(0, base.getMaxHit()) + 1.0 / (base.getMaxHit() + 1)),
			capped.getAverageHit(), 1e-12);
		assertEquals(capped.getDps() + Thrall.GREATER.getDps(),
			calculate(loadout.getWeapon(), "swipe,slash,aggressive", target, true).getDps(), 1e-12);
	}

	@Test
	public void randomCapsMatchEnumeratingEveryRawRollAndCapIncludingLowHits()
	{
		Outfit melee = loadout("test sword", "slash sword", "chop,slash,aggressive");
		Outfit ranged = loadout("magic shortbow", "bow", "rapid,ranged,rapid");
		for (int id : new int[]{8917, 8370, 10831, 10848})
		{
			Monster target = monster(100, 20);
			target.setId(id);
			for (Outfit loadout : new Outfit[]{melee, ranged})
			{
				int low = id == 8917 ? 22 : 0;
				int high = id == 8917 ? 24 : loadout.getStyle().isMelee() ? 10 : 3;
				Mitigation.Rule rule = Mitigation.rule(target, loadout);
				for (int max : new int[]{0, 1, 3, 10, 22, 23, 24, 80, 520})
				{
					assertEquals(randomCapMean(0, max, low, high), rule.average(0, max), 1e-10);
					assertEquals(Math.min(max, high), rule.maximum(max));
				}
				assertEquals(randomCapMean(7, 40, low, high), rule.average(7, 40), 1e-12);
			}
		}
	}

	private static double randomCapMean(int min, int max, int low, int high)
	{
		long total = 0;
		for (int hit = min; hit <= max; hit++)
		{
			for (int cap = low; cap <= high; cap++)
			{
				total += Math.min(hit, cap);
			}
		}
		return total / ((max - min + 1.0) * (high - low + 1));
	}

	private static Outfit loadout(String name, String subcategory, String style)
	{
		Outfit loadout = new Outfit();
		loadout.set(Slot.WEAPON, weapon(40000, name, subcategory, 5, style));
		loadout.setStyle(Style.parse(style));
		return loadout;
	}

	@Test
	public void verzikCapAppliesToEachScytheHitAndOnlyPhaseOne()
	{
		Monster target = monster(100, 20);
		target.setSize(3);
		Gear scythe = weapon(22325, "scythe of vitur", "scythe", 5);
		scythe.setMeleeStr(130);
		Dps ordinary = calculate(scythe, "reap,slash,aggressive", target, false);
		target.setId(8370);
		Dps capped = calculate(scythe, "reap,slash,aggressive", target, false);
		assertEquals(30, capped.getMaxHit());
		double mean = 0;
		for (String component : ordinary.getMaxHitDetail().split(" \\+ "))
		{
			int bound = Integer.parseInt(component);
			// A raised successful zero survives ten of the eleven possible caps.
			mean += ordinary.getAccuracy() * (randomCapMean(0, bound, 0, 10) + 10.0 / 11 / (bound + 1));
		}
		assertEquals(mean, capped.getAverageHit(), 1e-12);
		target.setId(8372);
		assertEquals(ordinary.getDps(), calculate(scythe, "reap,slash,aggressive", target, false).getDps(), 1e-12);
		Gear dawn = weapon(22516, "dawnbringer", "powered staff", 4);
		target.setId(8370);
		Dps dawnDps = calculate(dawn, "accurate,magic,accurate", target, false);
		assertTrue(dawnDps.getMaxHit() > 3);
		// 112 boosted Magic: swamp base 35, Augury +4% gives 36, then Dawnbringer halves it to 18.
		assertEquals(18, dawnDps.getMaxHit());
	}

	@Test
	public void raidReductionsAllowFireAndDemonbaneExceptionsAndCrystalMagic()
	{
		Outfit magic = loadout("test staff", "staff", "spell,magic,magic");
		Spell spell = new Spell();
		spell.setName("Fire surge");
		magic.setSpell(spell);
		Monster ice = monster(100, 20);
		ice.setId(7584);
		assertEquals(30, Mitigation.rule(ice, magic).maximum(30));
		spell.setName("Ice barrage");
		assertEquals(10, Mitigation.rule(ice, magic).maximum(30));
		spell.setName("Dark demonbane");
		assertEquals(30, Mitigation.rule(ice, magic).maximum(30));
		Outfit arclight = loadout("arclight", "slash sword", "chop,slash,aggressive");
		assertEquals(30, Mitigation.rule(ice, arclight).maximum(30));
		Monster tekton = monster(100, 20);
		tekton.setId(7544);
		assertFalse(Optimizer.classAllowed(tekton, Combat.RANGED));
		assertTrue(Optimizer.classAllowed(tekton, Combat.MAGIC));
		assertEquals(1, Mitigation.rule(tekton, magic).maximum(4));
		assertEquals(6, Mitigation.rule(tekton, magic).maximum(30));
		Monster crystal = monster(100, 20, "melee only");
		crystal.setId(7568);
		assertTrue(Optimizer.classAllowed(crystal, Combat.MAGIC));
		assertFalse(Optimizer.classAllowed(crystal, Combat.RANGED));
		assertEquals(10, Mitigation.rule(crystal, magic).maximum(30));
	}

	@Test
	public void tormentedShieldRecognizesAttackTypeAndAbyssalAndDemonbaneBypasses()
	{
		Monster demon = monster(100, 20);
		demon.setId(13599);
		Outfit sword = loadout("test sword", "slash sword", "chop,slash,aggressive");
		assertEquals(24, Mitigation.rule(demon, sword).maximum(30));
		assertEquals(1, Mitigation.rule(demon, sword).maximum(1));
		for (String name : new String[]{"abyssal whip", "abyssal dagger (p++)", "abyssal tentacle", "arclight", "burning claws"})
		{
			Outfit bypass = loadout(name, "slash sword", "chop,slash,aggressive");
			assertEquals(name, 30, Mitigation.rule(demon, bypass).maximum(30));
		}
		Outfit scorching = loadout("scorching bow", "bow", "rapid,ranged,rapid");
		assertEquals(30, Mitigation.rule(demon, scorching).maximum(30));
		scorching.setStyle(Style.parse("bash,crush,aggressive"));
		assertEquals(24, Mitigation.rule(demon, scorching).maximum(30));
		// The Wiki's 13600 is a spawn variant; the unshielded state comes from the Boss phase input.
		demon.setId(13600);
		assertEquals(24, Mitigation.rule(demon, sword).maximum(30));
		demon.setPhase(com.bestgearsetup.data.EncounterPhase.TD_UNSHIELDED);
		assertEquals(30, Mitigation.rule(demon, sword).maximum(30));
		demon.setPhase(com.bestgearsetup.data.EncounterPhase.STANDARD);
		demon.setName("tormented demon (unshielded)");
		assertEquals(30, Mitigation.rule(demon, sword).maximum(30));
		demon.setId(1);
		demon.setName("tormented demon (shielded)");
		assertEquals(24, Mitigation.rule(demon, sword).maximum(30));
	}

	@Test
	public void corpRequiresQualifyingStabWeaponsAndAllowsFullMagicDamage()
	{
		Monster corp = monster(100, 20);
		corp.setId(319);
		assertEquals(15, Mitigation.rule(corp, loadout("ghrazi rapier", "stab sword", "stab,stab,accurate")).maximum(30));
		assertEquals(30, Mitigation.rule(corp, loadout("osmumten's fang", "stab sword", "stab,stab,accurate")).maximum(30));
		assertEquals(15, Mitigation.rule(corp, loadout("osmumten's fang", "stab sword", "slash,slash,aggressive")).maximum(30));
		assertEquals(30, Mitigation.rule(corp, loadout("noxious halberd", "polearm", "jab,stab,controlled")).maximum(30));
		assertEquals(15, Mitigation.rule(corp, loadout("blue moon spear", "spear", "jab,stab,controlled")).maximum(30));
		assertEquals(30, Mitigation.rule(corp, loadout("trident of the seas", "powered staff", "accurate,magic,accurate")).maximum(30));
	}

	@Test
	public void equipmentImmunitiesApplyToDirectScoringAndLockedOptimizerWeapons()
	{
		Monster guardian = monster(100, 20);
		guardian.setId(7569);
		Outfit sword = loadout("abyssal whip", "whip", "lash,slash,aggressive");
		Outfit pickaxe = loadout("dragon pickaxe", "pickaxe", "smash,crush,aggressive");
		assertFalse(EncounterImmunities.attackAllowed(guardian, sword));
		assertTrue(EncounterImmunities.attackAllowed(guardian, pickaxe));
		assertEquals(0, calculate(sword.getWeapon(), "lash,slash,aggressive", guardian, true).getDps(), 0);
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.UNLIMITED)
			.locks(Collections.singletonMap(Slot.WEAPON, SlotLock.item(sword.getWeapon().getId()))).build();
		assertTrue(com.bestgearsetup.calc.Optimizers.of(TestData.gameData(Arrays.asList(sword.getWeapon()), Collections.emptyList()),
			TestFactories.combatContext(guardian, PlayerLevels.maxed(), false, true, piety()), settings, id -> true,
			Gear::getPrice).optimize(Combat.MELEE, () -> false, done -> { }).isEmpty());
		Monster leafy = monster(100, 20, "leafy");
		assertFalse(EncounterImmunities.attackAllowed(leafy, sword));
		assertTrue(EncounterImmunities.attackAllowed(leafy, loadout("leaf-bladed sword", "stab sword", "stab,stab,accurate")));
		Outfit crossbow = loadout("rune crossbow", "crossbow", "rapid,ranged,rapid");
		crossbow.getWeapon().setAmmunition(Collections.singletonList(13280));
		crossbow.set(Slot.AMMO, item(13280, "amethyst broad bolts", Slot.AMMO));
		assertTrue(EncounterImmunities.attackAllowed(leafy, crossbow));
		Outfit trident = loadout("trident of the seas", "powered staff", "accurate,magic,accurate");
		trident.set(Slot.AMMO, item(13280, "amethyst broad bolts", Slot.AMMO));
		assertFalse(EncounterImmunities.attackAllowed(leafy, trident));
		assertFalse(Optimizer.classAllowed(monster(100, 20, "magic only", "magic immune"), Combat.MAGIC));
	}

	@Test
	public void tierTwoAndThreeVampyresHaveDifferentWeaponRequirementsAndCaps()
	{
		Monster tierTwo = monster(100, 20, "vampyre (t2)");
		Outfit sword = loadout("abyssal whip", "whip", "lash,slash,aggressive");
		assertFalse(EncounterImmunities.attackAllowed(tierTwo, sword));
		sword.set(Slot.RING, item(21140, "efaritay's aid", Slot.RING));
		assertTrue(EncounterImmunities.attackAllowed(tierTwo, sword));
		assertEquals(15, Mitigation.rule(tierTwo, sword).maximum(30));
		Outfit silver = loadout("silverlight", "slash sword", "chop,slash,aggressive");
		assertTrue(EncounterImmunities.attackAllowed(tierTwo, silver));
		assertEquals(10, Mitigation.rule(tierTwo, silver).maximum(30));
		assertEquals(5, Mitigation.rule(tierTwo, silver).maximum(5));
		Monster tierThree = monster(100, 20, "vampyre (t3)");
		assertFalse(EncounterImmunities.attackAllowed(tierThree, sword));
		assertFalse(EncounterImmunities.attackAllowed(tierThree, silver));
		assertTrue(EncounterImmunities.attackAllowed(tierThree, loadout("blisterwood flail", "flail", "reap,crush,aggressive")));
	}

	@Test
	public void immunityTagsExcludeMagicRatherThanTreatingDefenceAsImmunity()
	{
		assertFalse(Optimizer.classAllowed(monster(1, 0, "magic immune"), Combat.MAGIC));
		assertTrue(Optimizer.classAllowed(monster(999, 999), Combat.MAGIC));
		assertTrue(Optimizer.classAllowed(monster(1, 0, "magic immune"), Combat.MELEE));
	}
}
