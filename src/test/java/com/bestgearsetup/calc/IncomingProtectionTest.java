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

public class IncomingProtectionTest
{
	private static Monster mixed(String name)
	{
		Monster monster = new Monster();
		monster.setName(name);
		monster.setAttackLevel(100);
		monster.setMagicLevel(100);
		monster.setRangedLevel(100);
		monster.setMeleeAttackBonus(0);
		monster.setRangedAttackBonus(0);
		Map<String, Monster.MonsterAttack> attacks = new LinkedHashMap<>();
		attacks.put("magic", attack(20, 1));
		attacks.put("ranged", attack(30, 1));
		monster.setAttackStyles(attacks);
		return monster;
	}

	private static Monster.MonsterAttack attack(int max, double weight)
	{
		Monster.MonsterAttack attack = new Monster.MonsterAttack();
		attack.setMaxHit(max);
		attack.setWeighting(weight);
		return attack;
	}

	private static SetupResult setup(CombatClass cls, String shieldName)
	{
		Loadout loadout = new Loadout();
		GearItem weapon = new GearItem();
		weapon.setName(cls == CombatClass.MELEE ? "dragon hunter lance" : "dragon hunter crossbow");
		loadout.set(Slot.WEAPON, weapon);
		loadout.setStyle(AttackStyle.parse(cls == CombatClass.MELEE ? "lunge,stab,aggressive" : "rapid,ranged,rapid"));
		if (shieldName != null)
		{
			GearItem shield = new GearItem();
			shield.setName(shieldName);
			loadout.set(Slot.SHIELD, shield);
		}
		return new SetupResult(cls, loadout, null, 0);
	}

	private static Advice recommend(Monster monster, SetupResult setup, OptimizerSettings settings)
	{
		return ProtectionPrayers.recommend(monster, setup, 99,
			new ProtectionPrayers.Context(monster, PlayerLevels.maxed(), settings));
	}

	@Test
	public void vorkathUsesTheActualFireShieldAndCombatClass()
	{
		for (String version : new String[]{"vorkath (post-quest)", "vorkath (dragon slayer ii)"})
		{
			Monster monster = mixed(version);
			for (String shield : new String[]{"anti-dragon shield", "dragonfire ward", "dragonfire shield", "ancient wyvern shield"})
			{
				Advice ranged = recommend(monster, setup(CombatClass.RANGED, shield), OptimizerSettings.builder().build());
				assertEquals(shield, Overhead.MISSILES, ranged.getPrayer());
				assertFalse(ranged.isSwitching());
				assertTrue(ranged.getNote().contains("active before"));
				assertEquals(Overhead.MAGIC, recommend(monster, setup(CombatClass.MELEE, shield),
					OptimizerSettings.builder().build()).getPrayer());
			}
			for (String shield : new String[]{null, "twisted buckler", "book of law", "elemental shield", "mind shield"})
			{
				assertEquals(Overhead.MAGIC, recommend(monster, setup(CombatClass.RANGED, shield),
					OptimizerSettings.builder().build()).getPrayer());
			}
			SetupResult bow = setup(CombatClass.RANGED, "dragonfire ward");
			bow.getLoadout().getWeapon().setTwoHanded(true);
			assertEquals(Overhead.MAGIC, recommend(monster, bow, OptimizerSettings.builder().build()).getPrayer());
		}
	}

	@Test
	public void vorkathReportsInsufficientAntifireAndLockedPrayers()
	{
		Monster monster = mixed("vorkath (post-quest)");
		Advice bad = recommend(monster, setup(CombatClass.RANGED, "twisted buckler"),
			OptimizerSettings.builder().antifire(Antifire.REGULAR).requireFireProtection(false).build());
		assertTrue(bad.getNote().contains("does not provide sufficient"));
		assertEquals(Overhead.MAGIC, bad.getPrayer());
		assertFalse(recommend(monster, setup(CombatClass.RANGED, "anti-dragon shield"),
			OptimizerSettings.builder().antifire(Antifire.REGULAR).build()).getNote().contains("does not provide sufficient"));
		PlayerLevels low = new PlayerLevels(99, 99, 99, 99, 99, 38, 99, 99);
		Advice usable = ProtectionPrayers.recommend(monster, setup(CombatClass.RANGED, "dragonfire ward"), 38,
			new ProtectionPrayers.Context(monster, low, OptimizerSettings.builder().build()));
		assertEquals(Overhead.MAGIC, usable.getPrayer());
		assertFalse(usable.isLocked());
		assertTrue(usable.getNote().contains("needs 40"));
		Advice locked = ProtectionPrayers.recommend(monster, setup(CombatClass.MELEE, null), 30, null);
		assertEquals("Protect from Magic (needs 37 Prayer)", locked.summary());
	}

	@Test
	public void armourCanMakeTheLowerMaxHitTheBetterPrayer()
	{
		Monster monster = mixed("mixed attacker");
		SetupResult setup = setup(CombatClass.RANGED, "shield");
		assertEquals(Overhead.MISSILES, recommend(monster, setup, OptimizerSettings.builder().build()).getPrayer());
		setup.getLoadout().get(Slot.SHIELD).setRangedDef(1000);
		Advice advice = recommend(monster, setup, OptimizerSettings.builder().build());
		assertEquals(Overhead.MAGIC, advice.getPrayer());
		assertFalse(advice.isSwitching());
		assertTrue(advice.getNote().contains("estimated incoming damage"));
		setup.getLoadout().getWeapon().setTwoHanded(true);
		assertEquals("A shield can't defend a two-handed setup", Overhead.MISSILES,
			recommend(monster, setup, OptimizerSettings.builder().build()).getPrayer());
	}

	@Test
	public void frequencyAndAccuracyAffectTheChoice()
	{
		Monster monster = mixed("mixed attacker");
		SetupResult setup = setup(CombatClass.RANGED, null);
		monster.getAttackStyles().get("magic").setWeighting(4);
		assertEquals(Overhead.MAGIC, recommend(monster, setup, OptimizerSettings.builder().build()).getPrayer());
		monster.getAttackStyles().get("magic").setWeighting(1);
		monster.setMagicLevel(500);
		monster.setRangedLevel(1);
		assertEquals(Overhead.MAGIC, recommend(monster, setup, OptimizerSettings.builder().build()).getPrayer());
		monster.setMagicLevel(100);
		monster.setRangedLevel(100);
		monster.setRangedAttackBonus(-60);
		assertEquals(Overhead.MAGIC, recommend(monster, setup, OptimizerSettings.builder().build()).getPrayer());
		assertEquals(monster.getRangedAttackBonus(), monster.copy().getRangedAttackBonus());
	}

	@Test
	public void incompleteStatsFallBackWithoutInventingSwitches()
	{
		Monster monster = mixed("mixed attacker");
		monster.setRangedLevel(0);
		Advice advice = recommend(monster, setup(CombatClass.RANGED, null), OptimizerSettings.builder().build());
		assertEquals(Overhead.MISSILES, advice.getPrayer());
		assertFalse(advice.isSwitching());
		assertTrue(advice.getNote().contains("fallback"));
		monster.setRangedLevel(100);
		monster.setRangedAttackBonus(null);
		assertTrue(recommend(monster, setup(CombatClass.RANGED, null), OptimizerSettings.builder().build())
			.getNote().contains("estimated as zero"));
	}

	@Test
	public void explicitDistanceOverridesCombatClassForMeleeExposure()
	{
		Monster monster = mixed("mixed attacker");
		monster.getAttackStyles().put("slash", attack(100, 1));
		SetupResult ranged = setup(CombatClass.RANGED, null);
		assertEquals(Overhead.MISSILES, recommend(monster, ranged, OptimizerSettings.builder().build()).getPrayer());
		assertEquals(Overhead.MELEE, recommend(monster, ranged,
			OptimizerSettings.builder().targetDistance(1).build()).getPrayer());
		assertEquals(Overhead.MISSILES, recommend(monster, setup(CombatClass.MELEE, null),
			OptimizerSettings.builder().targetDistance(2).build()).getPrayer());
	}

	@Test
	public void magicRequiredForFireProtectionCannotBeReplacedByMelee()
	{
		Monster monster = mixed("blue dragon");
		monster.getAttackStyles().clear();
		monster.getAttackStyles().put("slash", attack(30, 1));
		SetupResult setup = setup(CombatClass.MELEE, null);
		Advice regular = recommend(monster, setup, OptimizerSettings.builder().antifire(Antifire.REGULAR).build());
		assertEquals(Overhead.MAGIC, regular.getPrayer());
		assertTrue(regular.getNote().contains("fire protection"));
		assertEquals(Overhead.MELEE, recommend(monster, setup, OptimizerSettings.builder().antifire(Antifire.SUPER).build()).getPrayer());
	}
}
