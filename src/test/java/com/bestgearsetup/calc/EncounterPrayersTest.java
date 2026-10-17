package com.bestgearsetup.calc;

import com.bestgearsetup.calc.ProtectionPrayers.Advice;
import com.bestgearsetup.calc.ProtectionPrayers.Overhead;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncounterPrayersTest
{
	private static Monster monster(String name)
	{
		Monster m = new Monster();
		m.setName(name);
		m.setMagicLevel(100);
		m.setRangedLevel(100);
		m.setAttackLevel(100);
		return m;
	}

	private static SetupResult setup(CombatClass cls, String shieldName)
	{
		Loadout loadout = new Loadout();
		GearItem weapon = new GearItem();
		weapon.setName("one-handed weapon");
		loadout.set(Slot.WEAPON, weapon);
		if (shieldName != null)
		{
			GearItem shield = new GearItem();
			shield.setName(shieldName);
			loadout.set(Slot.SHIELD, shield);
		}
		return new SetupResult(cls, loadout, null, 0);
	}

	private static Advice advice(String name, SetupResult setup, int distance)
	{
		Monster m = monster(name);
		return ProtectionPrayers.recommend(m, setup, 99, new ProtectionPrayers.Context(m, PlayerLevels.maxed(),
			OptimizerSettings.builder().targetDistance(distance).build()));
	}

	@Test
	public void delayedBossAttacksKeepRequiredPrayersAtDistance()
	{
		SetupResult ranged = setup(CombatClass.RANGED, null);
		for (String name : new String[]{"the leviathan (post-quest)", "doom of mokhaiotl (delve 8)", "manticore", "the hueycoatl (tail)"})
		{
			Advice a = advice(name, ranged, 2);
			assertTrue(name, a.isSwitching());
			assertTrue("Melee projectiles must not be dropped at distance: " + name, a.getSwitches().contains(Overhead.MELEE));
			assertNotNull(a.getSource());
		}
		assertFalse(advice("tztok-jad", ranged, 2).getSwitches().contains(Overhead.MELEE));
		assertEquals(Overhead.MELEE, advice("tztok-jad", ranged, 1).getPrayer());
	}

	@Test
	public void launchAndImpactTimingAreNotInterchangeable()
	{
		SetupResult ranged = setup(CombatClass.RANGED, null);
		assertTrue(advice("manticore", ranged, 2).getNote().contains("BEFORE"));
		assertTrue(advice("doom of mokhaiotl (delve 8)", ranged, 2).getNote().contains("before impact"));
		assertTrue(advice("cerberus", ranged, 2).getNote().contains("not reactive"));
		assertTrue(advice("alchemical hydra (electric)", ranged, 2).getNote().contains("Count three"));
		assertTrue(advice("corrupted hunllef", ranged, 2).getNote().contains("four boss"));
		assertTrue(advice("maggot king (far)", ranged, 2).getNote().contains("OFF overheads"));
	}

	@Test
	public void muspahUsesMissilesAsBaselineAndMeleeByBossForm()
	{
		SetupResult ranged = setup(CombatClass.RANGED, null);
		Advice a = advice("phantom muspah (ranged)", ranged, 2);
		assertEquals(Overhead.MISSILES, a.getPrayer());
		assertTrue(a.getNote().contains("BEFORE launch"));
		assertEquals(Overhead.MELEE, advice("phantom muspah (melee)", ranged, 2).getPrayer());
		assertNull(advice("phantom muspah (teleporting)", ranged, 2).getPrayer());
		assertEquals(Overhead.MISSILES, advice("strange creature (ranged)", ranged, 2).getPrayer());
		assertNull("Unrelated NPC with the same display-name family", EncounterPrayers.recommend(
			monster("strange creature (shadows of custodia)"), CombatClass.RANGED, false));
	}

	@Test
	public void araxxorUsesGearToChooseTheDistantPrayer()
	{
		SetupResult ranged = setup(CombatClass.RANGED, "shield");
		GearItem shield = ranged.getLoadout().get(Slot.SHIELD);
		shield.setRangedDef(300);
		shield.setMagicDef(-20);
		assertEquals(Overhead.MAGIC, advice("araxxor (in combat)", ranged, 2).getPrayer());
		shield.setRangedDef(0);
		shield.setMagicDef(300);
		assertEquals(Overhead.MISSILES, advice("araxxor (in combat)", ranged, 2).getPrayer());
		assertEquals(Overhead.MELEE, advice("araxxor (enraged)", ranged, 1).getPrayer());
	}

	@Test
	public void kbdShieldAndTwoHandedWeaponChangeTheOverhead()
	{
		SetupResult ranged = setup(CombatClass.RANGED, "dragonfire ward");
		assertEquals(Overhead.MELEE, advice("king black dragon", ranged, 0).getPrayer());
		assertEquals(Overhead.MAGIC, advice("king black dragon", ranged, 3).getPrayer());
		ranged.getLoadout().getWeapon().setTwoHanded(true);
		assertEquals(Overhead.MAGIC, advice("king black dragon", ranged, 0).getPrayer());
	}

	@Test
	public void sarachnisAraneaBootsRemoveTheWebPrayerSwap()
	{
		SetupResult melee = setup(CombatClass.MELEE, null);
		assertTrue(advice("sarachnis", melee, 0).isSwitching());
		GearItem boots = new GearItem();
		boots.setName("aranea boots");
		melee.getLoadout().set(Slot.FEET, boots);
		assertFalse(advice("sarachnis", melee, 0).isSwitching());
		assertEquals(Overhead.MELEE, advice("sarachnis", melee, 0).getPrayer());
	}

	@Test
	public void raidAdviceMatchesTheActualIncomingAttacker()
	{
		SetupResult ranged = setup(CombatClass.RANGED, null);
		assertNull(advice("obelisk (tombs of amascut)", ranged, 2).getPrayer());
		assertNull(advice("kephri (shielded)", ranged, 2).getPrayer());
		assertEquals(Overhead.MAGIC, advice("vanguard (magic)", ranged, 2).getPrayer());
		assertEquals(Overhead.MISSILES, advice("vanguard (ranged)", ranged, 2).getPrayer());
		assertTrue(advice("akkha's shadow", ranged, 2).getNote().contains("Akkha keeps attacking"));
		assertTrue(advice("akkha", ranged, 2).getSwitches().contains(Overhead.MELEE));
	}

	@Test
	public void restrictionsAndSpecialModesDoNotBorrowNormalAdvice()
	{
		SetupResult ranged = setup(CombatClass.RANGED, null);
		assertNull(advice("penance queen", ranged, 2).getPrayer());
		assertNull(advice("earth warrior champion", ranged, 2).getPrayer());
		assertTrue(advice("cerberus (echo)", ranged, 2).isUnknown());
		assertTrue(advice("sol heredit (deadman) (permanent)", ranged, 2).isUnknown());
		assertNull(EncounterPrayers.recommend(monster("tztok-jad-rek"), CombatClass.RANGED, false));
	}

	@Test
	public void galvekAndZilyanaDependOnPhaseAndPlayerStyle()
	{
		SetupResult ranged = setup(CombatClass.RANGED, "anti-dragon shield");
		assertEquals(Overhead.MAGIC, advice("galvek (fire)", ranged, 0).getPrayer());
		assertEquals(Overhead.MISSILES, advice("galvek (air)", ranged, 0).getPrayer());
		assertEquals(Overhead.MAGIC, advice("galvek (air)", setup(CombatClass.MELEE, "dragonfire shield"), 0).getPrayer());
		assertEquals(Overhead.MAGIC, advice("commander zilyana", ranged, 0).getPrayer());
		assertEquals(Overhead.MISSILES, advice("commander zilyana", setup(CombatClass.MAGIC, null), 0).getPrayer());
	}

	@Test
	public void mixedTypeAttacksSeparatePrayerFromAccuracy()
	{
		assertEquals(java.util.Collections.singleton(Overhead.MAGIC), ProtectionPrayers.protectedBy("ranged magic"));
		assertEquals(java.util.Collections.singleton(Overhead.MELEE), ProtectionPrayers.protectedBy("ranged melee"));
		Monster m = monster("mixed attacker");
		Monster.MonsterAttack attack = new Monster.MonsterAttack();
		attack.setMaxHit(20);
		Map<String, Monster.MonsterAttack> attacks = new LinkedHashMap<>();
		attacks.put("ranged magic", attack);
		m.setAttackStyles(attacks);
		SetupResult ranged = setup(CombatClass.RANGED, "shield");
		GearItem shield = ranged.getLoadout().get(Slot.SHIELD);
		shield.setMagicDef(300);
		double bare = IncomingDamage.byPrayer(m, ranged.getLoadout(), PlayerLevels.maxed(), false).get(Overhead.MAGIC);
		assertEquals(HitChance.single(109L * 64, 107L * 64) * 10, bare, 1e-9);
		shield.setRangedDef(300);
		assertTrue(IncomingDamage.byPrayer(m, ranged.getLoadout(), PlayerLevels.maxed(), false).get(Overhead.MAGIC) < bare);
	}

	@Test
	public void partialPenetrationCountsOnlyTheDamageActuallyBlocked()
	{
		Monster m = monster("mixed attacker");
		Monster.MonsterAttack magic = new Monster.MonsterAttack();
		magic.setMaxHit(40);
		Monster.MonsterAttack ranged = new Monster.MonsterAttack();
		ranged.setMaxHit(30);
		Map<String, Monster.MonsterAttack> attacks = new LinkedHashMap<>();
		attacks.put("magic (50% prayer penetration)", magic);
		attacks.put("ranged", ranged);
		m.setAttackStyles(attacks);
		SetupResult setup = setup(CombatClass.RANGED, null);
		Advice a = ProtectionPrayers.recommend(m, setup, 99, new ProtectionPrayers.Context(m, PlayerLevels.maxed(),
			OptimizerSettings.builder().build()));
		assertEquals(Overhead.MISSILES, a.getPrayer());
		assertTrue(ProtectionPrayers.protectedBy("magic (100% prayer penetration)").isEmpty());
	}

	@Test
	public void aFireShieldAndPotionDoNotInventAnOrdinaryMagicAttack()
	{
		Monster m = monster("blue dragon");
		Monster.MonsterAttack fire = new Monster.MonsterAttack();
		fire.setMaxHit(50);
		m.setAttackStyles(java.util.Collections.singletonMap("dragonfire", fire));
		Advice a = ProtectionPrayers.recommend(m, setup(CombatClass.RANGED, "anti-dragon shield"), 99,
			new ProtectionPrayers.Context(m, PlayerLevels.maxed(), OptimizerSettings.builder().build()));
		assertNull(a.getPrayer());
		assertFalse(a.isUnknown());
	}

	@Test
	public void coxShamansDoNotRequireSurfaceShayzienArmour()
	{
		Advice a = advice("lizardman shaman (chambers of xeric) (normal)", setup(CombatClass.RANGED, null), 2);
		assertEquals(Overhead.MISSILES, a.getPrayer());
		assertFalse(a.getNote().contains("Shayzien"));
		assertTrue(a.getSource().contains("Chambers_of_Xeric"));
	}
}
