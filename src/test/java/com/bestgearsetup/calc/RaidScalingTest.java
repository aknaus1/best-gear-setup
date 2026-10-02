package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import org.junit.Test;

/**
 * Vectors computed by hand from the Wiki calculator's lib/scaling modules (revision 89c3e25),
 * using solo, unscaled raid records as the base stats.
 */
public class RaidScalingTest
{
	private static Monster raidMonster(int id, String name, String attribute, int hp, int def, int magic,
		int attack, int strength, int ranged)
	{
		Monster m = monster(def, 50, attribute);
		m.setId(id);
		m.setName(name);
		m.setHitpoints(hp);
		m.setMagicLevel(magic);
		m.setAttackLevel(attack);
		m.setStrengthLevel(strength);
		m.setRangedLevel(ranged);
		return m;
	}

	private static RaidScaling.Settings.SettingsBuilder party(int size)
	{
		return RaidScaling.Settings.builder().partySize(size);
	}

	@Test
	public void toaScalesPathBossHpWithRaidPathAndPartyThenRounds()
	{
		Monster akkha = raidMonster(11789, "akkha", "tombs of amascut", 400, 80, 100, 100, 140, 100);
		Monster scaled = RaidScaling.apply(akkha, party(4).toaRaidLevel(300).toaPathLevel(3).build());
		// 400 + 120% = 880; path 3: x118% = 1038; party 4: x34/10 = 3529; rounded to 10.
		assertEquals(3530, scaled.getHitpoints());
		assertEquals(300, scaled.getToaRaidLevel());
		assertEquals(400, akkha.getHitpoints());
		assertEquals(0, akkha.getToaRaidLevel());
	}

	@Test
	public void toaCoreUsesFixedHpAndReducedInvocationFactor()
	{
		Monster core = raidMonster(11758, "tumeken's warden (core-ejected)", "tombs of amascut", 4500, 100, 190, 1, 1, 1);
		assertEquals(5850, RaidScaling.apply(core, party(1).toaRaidLevel(300).build()).getHitpoints());
	}

	@Test
	public void toaSmallTargetsRoundToFiveAndOverlordsKeepUnscaledDefenceRolls()
	{
		Monster kephri = raidMonster(11721, "kephri", "tombs of amascut", 80, 80, 125, 1, 1, 1);
		// 80 * 19/10 = 152, rounded to the nearest 5.
		assertEquals(150, RaidScaling.apply(kephri, party(2).build()).getHitpoints());
		Monster scarab = raidMonster(11724, "soldier scarab", "tombs of amascut", 40, 80, 1, 1, 1, 1);
		assertEquals(0, RaidScaling.apply(scarab, party(1).toaRaidLevel(300).build()).getToaRaidLevel());
		Monster baboon = raidMonster(11709, "baboon brawler", "tombs of amascut", 10, 10, 1, 1, 1, 1);
		Monster unscaled = RaidScaling.apply(baboon, party(8).toaRaidLevel(500).build());
		assertEquals(10, unscaled.getHitpoints());
		assertEquals(0, unscaled.getToaRaidLevel());
	}

	@Test
	public void toaRaidLevelMultipliesTheDefenceRollBeforeAccuracy()
	{
		Monster target = monster(80, 120);
		target.setToaRaidLevel(300);
		Loadout l = new Loadout();
		l.set(Slot.WEAPON, weapon(50000, "test sword", "stab sword", 4, "lunge,stab,accurate"));
		l.getWeapon().setStabBonus(100);
		l.getWeapon().setMeleeStr(50);
		l.setStyle(AttackStyle.parse("lunge,stab,accurate"));
		DpsResult r = DpsCalculator.calculate(l, new CombatContext(target, PlayerLevels.maxed(), false, false, null));
		long defence = 89L * 184 * 550 / 250;
		assertEquals(36027, defence);
		assertEquals(HitChance.single(110L * 164, defence), r.getAccuracy(), 1e-12);
	}

	@Test
	public void tobNormalClampsSmallPartiesAndEntryModeUsesItsTable()
	{
		Monster maiden = raidMonster(8360, "the maiden of sugadinti (normal)", "theatre of blood", 3500, 200, 350, 350, 350, 1);
		assertEquals(3062, RaidScaling.apply(maiden, party(4).build()).getHitpoints());
		assertEquals(2625, RaidScaling.apply(maiden, party(1).build()).getHitpoints());
		assertEquals(3500, RaidScaling.apply(maiden, party(5).build()).getHitpoints());
		Monster entry = raidMonster(10814, "the maiden of sugadinti (entry mode)", "theatre of blood", 2000, 80, 140, 1, 1, 1);
		assertEquals(950, RaidScaling.apply(entry, party(2).build()).getHitpoints());
		assertEquals(500, RaidScaling.apply(entry, party(1).build()).getHitpoints());
		// The variant name decides the mode, whatever the NPC id.
		Monster named = raidMonster(10821, "blood spawn (normal)", "theatre of blood", 120, 0, 0, 1, 1, 1);
		assertEquals(RaidScaling.Raid.TOB, RaidScaling.raid(named));
	}

	@Test
	public void coxMultiScalingUsesPartySizeAndChallengeMode()
	{
		Monster tekton = raidMonster(7540, "tekton (normal)", "xerician", 300, 205, 205, 390, 390, 1);
		Monster normal = RaidScaling.apply(tekton, party(3).build());
		assertEquals(600, normal.getHitpoints());
		assertEquals(209, normal.getDefenceLevel());
		// Tekton's Magic is a defensive skill.
		assertEquals(209, normal.getMagicLevel());
		assertEquals(425, normal.getAttackLevel());
		assertEquals(1, normal.getRangedLevel());
		Monster cm = RaidScaling.apply(tekton, party(3).coxChallengeMode(true).build());
		assertEquals(900, cm.getHitpoints());
		assertEquals(250, cm.getDefenceLevel());
		assertEquals(637, cm.getStrengthLevel());
	}

	@Test
	public void coxSoloAtMaximumLevelsKeepsBaseStats()
	{
		Monster tekton = raidMonster(7540, "tekton (normal)", "xerician", 300, 205, 205, 390, 390, 1);
		Monster solo = RaidScaling.apply(tekton, RaidScaling.Settings.SOLO);
		assertEquals(300, solo.getHitpoints());
		assertEquals(205, solo.getDefenceLevel());
		assertEquals(390, solo.getAttackLevel());
	}

	@Test
	public void coxLowerPartyLevelsReduceStatsAndGuardianHpUsesMining()
	{
		Monster guardian = raidMonster(7569, "guardian", "xerician", 151, 100, 1, 140, 140, 1);
		Monster maxed = RaidScaling.apply(guardian, party(1).partyAverageMining(85).build());
		assertEquals(236, maxed.getHitpoints());
		Monster low = RaidScaling.apply(guardian, party(1).partyAverageMining(85).partyMaxCombat(100)
			.partyMaxHitpoints(80).build());
		assertEquals(187, low.getHitpoints());
		assertEquals(90, low.getDefenceLevel());
		assertEquals(127, low.getAttackLevel());
		assertEquals(1, low.getMagicLevel());
	}

	@Test
	public void olmReplacesHpByPartyPhasesAndHalvesMageHandMagic()
	{
		Monster head = raidMonster(7551, "great olm (head)", "xerician", 400, 150, 250, 250, 250, 250);
		Monster scaled = RaidScaling.apply(head, party(9).build());
		assertEquals(2800, scaled.getHitpoints());
		assertEquals(160, scaled.getDefenceLevel());
		assertEquals(305, scaled.getMagicLevel());
		// The base record already halves the mage hand's Magic; scaling uses the linked Defence and halves once.
		Monster mage = raidMonster(7550, "great olm (mage hand)", "xerician", 300, 175, 87, 250, 250, 250);
		Monster hand = RaidScaling.apply(mage, party(1).build());
		assertEquals(600, hand.getHitpoints());
		assertEquals(175, hand.getDefenceLevel());
		assertEquals(87, hand.getMagicLevel());
	}

	@Test
	public void coxSinglesScaleOnlyByHighestLevels()
	{
		Monster beast = raidMonster(7548, "scavenger beast (normal)", "xerician", 40, 50, 1, 75, 75, 1);
		RaidScaling.Settings levels = party(8).partyMaxCombat(100).partyMaxHitpoints(80).build();
		Monster scaled = RaidScaling.apply(beast, levels);
		assertEquals(31, scaled.getHitpoints());
		assertEquals(40, scaled.getDefenceLevel());
		assertEquals(60, scaled.getAttackLevel());
		Monster cm = RaidScaling.apply(beast, levels.toBuilder().coxChallengeMode(true).build());
		assertEquals(47, cm.getHitpoints());
		assertEquals(60, cm.getDefenceLevel());
		assertEquals(90, cm.getAttackLevel());
	}

	@Test
	public void ordinaryTargetsAreCopiedUnchanged()
	{
		Monster ordinary = monster(100, 50);
		ordinary.setHitpoints(250);
		Monster copy = RaidScaling.apply(ordinary, party(5).toaRaidLevel(500).build());
		assertEquals(250, copy.getHitpoints());
		assertEquals(RaidScaling.Raid.NONE, RaidScaling.raid(ordinary));
		assertNull(RaidScaling.describe(ordinary, copy, RaidScaling.Settings.SOLO));
		assertSame(ordinary.getAttributes(), copy.getAttributes());
	}

	@Test
	public void maxedCombatLevelIs126()
	{
		assertEquals(126, PlayerLevels.maxed().combatLevel());
		assertEquals(3, new PlayerLevels(1, 1, 1, 1, 1, 1, 10, 1).combatLevel());
	}
}
