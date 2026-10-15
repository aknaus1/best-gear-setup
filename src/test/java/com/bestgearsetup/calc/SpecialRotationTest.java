package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SpecialRotationTest
{
	private static final double EPS = 1e-9;
	// 5 DPS ordinary attacks, and a 10 DPS spec on the same 4-tick (2.4s) weapon.
	private static final DpsResult ORDINARY = new DpsResult(5, 30, 0.8, 4, 12, null);
	private static final DpsResult SPEC = new DpsResult(10, 60, 0.9, 4, 24, null);

	// 10 DPS ordinary attacks averaging 24 a hit, a 30% spec dealing 60 every 7 ticks (4.2s) and a 50% spec dealing 120
	// every 4 ticks (2.4s).
	private static final DpsResult STEADY = new DpsResult(10, 48, 1, 4, 24, null);
	private static final SpecialRotation.Damage HALBERD =
		new SpecialRotation.Damage(new DpsResult(60 / 4.2, 60, 1, 7, 60, null), 30);
	private static final SpecialRotation.Damage CLAWS =
		new SpecialRotation.Damage(new DpsResult(50, 120, 1, 4, 120, null), 50);

	@Test
	public void everyDamageSpecialPaysItsFullInterval()
	{
		SpecialRotation.Rotation r = SpecialRotation.rotation(STEADY, 50, 500, 100, 0,
			java.util.Collections.singletonList(HALBERD), 1);
		assertEquals(3, r.getSpecs()[0], EPS);
		assertEquals(90, r.getOpeningEnergy(), EPS);
		assertEquals(500 / (50 - (3 * 18 + 24) / 10.0), r.getResult().getPrimaryDps(), EPS);
	}

	@Test
	public void aSlowSpecLosingDamageNeverBecomesAFinalAttackException()
	{
		SpecialRotation.Damage slow = new SpecialRotation.Damage(new DpsResult(8, 60, 1, 7, 33.6, null), 30);
		SpecialRotation.Rotation r = SpecialRotation.rotation(STEADY, 50, 500, 100, 1,
			java.util.Collections.singletonList(slow), 1);
		assertEquals(0, r.getSpecs()[0], EPS);
		assertEquals(500 / (50 - 24 / 10.0), r.getResult().getPrimaryDps(), EPS);
	}

	@Test
	public void regenerationUsesDamageGainedAfterTheEntireInterval()
	{
		SpecialRotation.Rotation r = SpecialRotation.rotation(STEADY, 50, 500, 0, 1,
			java.util.Collections.singletonList(HALBERD), 0);
		double time = 50 / (1 + 18 / 300.0);
		assertEquals(time / 30, r.getSpecs()[0], EPS);
		assertEquals(500 / time, r.getResult().getPrimaryDps(), EPS);
	}

	@Test
	public void regeneratedEnergySpecsForItsShareOfTheFight()
	{
		SpecialRotation.Blend b = SpecialRotation.blend(ORDINARY, SPEC, 50, 500, SpecEnergy.REGENERATION);
		// 1/3% per second for 2.4s per spec at 50% each: 1.6% of the fight is spent speccing.
		double share = 2.4 / 3 / 50;
		double rate = share * 10 + (1 - share) * 5;
		assertEquals(rate, b.getResult().getPrimaryDps(), EPS);
		assertEquals(rate, b.getResult().getDps(), EPS);
		assertEquals(share * (500 / rate) / 2.4, b.getSpecsPerKill(), EPS);
		// The ordinary attack's own stats are kept.
		assertEquals(30, b.getResult().getMaxHit());
		assertEquals(12, b.getResult().getAverageHit(), EPS);
	}

	@Test
	public void fullBarOpensWithEveryWholeSpecItHolds()
	{
		SpecialRotation.Blend b = SpecialRotation.blend(ORDINARY, SPEC, 40, 500, SpecEnergy.FULL_BAR);
		// Two 40% specs (24 damage each, 4.8s); the 20% remainder is not carried over.
		double share = 2.4 / 3 / 40;
		double rate = share * 10 + (1 - share) * 5;
		double seconds = 4.8 + (500 - 48) / rate;
		assertEquals(500 / seconds, b.getResult().getPrimaryDps(), EPS);
		assertEquals(2 + share * (seconds - 4.8) / 2.4, b.getSpecsPerKill(), EPS);
	}

	@Test
	public void openingSpecsAloneCanKill()
	{
		SpecialRotation.Blend b = SpecialRotation.blend(ORDINARY, SPEC, 25, 30, SpecEnergy.FULL_BAR);
		assertEquals(10, b.getResult().getPrimaryDps(), EPS);
		assertEquals(30 / 24.0, b.getSpecsPerKill(), EPS);
	}

	@Test
	public void specsThatDoNotOutDamageOrdinaryAttacksAreNotUsed()
	{
		assertNull(SpecialRotation.blend(SPEC, ORDINARY, 50, 500, SpecEnergy.FULL_BAR));
		assertNull(SpecialRotation.blend(ORDINARY, SPEC, 0, 500, SpecEnergy.FULL_BAR));
		assertNull(SpecialRotation.blend(ORDINARY, DpsResult.ZERO, 50, 500, SpecEnergy.FULL_BAR));
	}

	@Test
	public void areaDamageBlendsTotalAndPrimaryRatesSeparately()
	{
		DpsResult area = new DpsResult(15, 30, 0.8, 4, 12, null, 5, 3);
		SpecialRotation.Blend b = SpecialRotation.blend(area, SPEC, 50, 500, SpecEnergy.REGENERATION);
		double share = 2.4 / 3 / 50;
		assertEquals(share * 10 + (1 - share) * 15, b.getResult().getDps(), EPS);
		assertEquals(3, b.getResult().getTargetsHit());
	}

	/** Ordinary attacks rise with each landed warhammer; the 50%-accurate spec itself averages 12 a hit. */
	private static final SpecialRotation.Fight WARHAMMER = new SpecialRotation.Fight()
	{
		@Override
		public DpsResult ordinary(SpecialAttacks drains)
		{
			int dps = new int[]{5, 8, 10}[Math.min(2, drains.getDragonWarhammer())];
			return new DpsResult(dps, 30, 0.8, 4, dps * 2.4, null);
		}

		@Override
		public DpsResult spec(SpecialAttacks drains)
		{
			return new DpsResult(5, 50, 0.5, 4, 12, null);
		}
	};

	@Test
	public void sharedDrainKeepsTeammatesAttackingAndOnlyThePlayersSpecDrains()
	{
		// Four equal teammates: during the 2.4s opening three deal 36 total. A hit adds 24 from the
		// player's hammer, then all four attack at 8 DPS each; a miss leaves all four at 5 DPS each.
		double[] shared = SpecialRotation.drainOpenedShared(WARHAMMER, SpecialAttack.Drain.DRAGON_WARHAMMER,
			50, 1, 2000, false, 4);
		assertEquals(2.4 + 0.5 * (2000 - 36 - 24) / 32 + 0.5 * (2000 - 36) / 20, shared[0], EPS);
		assertEquals(1, shared[1], EPS);
		double[] solo = SpecialRotation.drainOpened(WARHAMMER, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, 1, 500, false);
		assertTrue(shared[0] < solo[0]);
	}

	@Test
	public void teammatesCanEndTheTargetDuringAMissedSpecWithoutSpendingAnother()
	{
		double[] shared = SpecialRotation.drainOpenedShared(WARHAMMER, SpecialAttack.Drain.DRAGON_WARHAMMER,
			50, 2, 20, true, 4);
		assertEquals(20 / 15.0, shared[0], EPS);
		assertEquals(1, shared[1], EPS);
	}

	@Test
	public void teamOpenerWithSoloPartyIsTheUnchangedSoloSimulation()
	{
		double[] solo = SpecialRotation.drainOpened(WARHAMMER, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, 1, 500, false);
		double[] team = SpecialRotation.drainOpenedTeam(WARHAMMER, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, 1,
			500, false, 1, WARHAMMER, SpecialAttack.Drain.DRAGON_WARHAMMER);
		assertEquals(solo[0], team[0], EPS);
		assertEquals(solo[1], team[1], EPS);
	}

	@Test
	public void teammatesMissesCostTheirOwnAttackIntervalsButNoPersonalEnergy()
	{
		double[] team = SpecialRotation.drainOpenedTeam(WARHAMMER, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, 0,
			2000, true, 4, WARHAMMER, SpecialAttack.Drain.DRAGON_WARHAMMER);
		assertEquals(3 * 2.4 + (2000 - 3 * 36) / 20.0, team[0], EPS);
		assertEquals(0, team[1], EPS);
	}

	@Test
	public void teamHitAndMissBranchesShareTheNoPersonalSpecBaseline()
	{
		// One teammate: 50% hits, 24 conditional damage, the other player keeps ordinary attacks going.
		double[] team = SpecialRotation.drainOpenedTeam(WARHAMMER, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, 0,
			2000, false, 2, WARHAMMER, SpecialAttack.Drain.DRAGON_WARHAMMER);
		assertEquals(2.4 + .5 * (2000 - 12 - 24) / 16 + .5 * (2000 - 12) / 10, team[0], EPS);
		assertEquals(0, team[1], EPS);
	}

	@Test
	public void successfulTeammateDrainsReduceTheMarginalValueOfSpendingAnotherPersonalSpec()
	{
		SpecialRotation.Fight certain = new SpecialRotation.Fight()
		{
			@Override public DpsResult ordinary(SpecialAttacks drains) { return WARHAMMER.ordinary(drains); }
			@Override public DpsResult spec(SpecialAttacks drains) { return new DpsResult(10, 24, 1, 4, 24, null); }
		};
		double[] zero = SpecialRotation.drainOpenedShared(certain, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, 0, 2000, false, 4);
		double[] one = SpecialRotation.drainOpenedShared(certain, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, 1, 2000, false, 4);
		double[] teamZero = SpecialRotation.drainOpenedTeam(certain, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, 0, 2000, false, 4, certain, SpecialAttack.Drain.DRAGON_WARHAMMER);
		double[] teamOne = SpecialRotation.drainOpenedTeam(certain, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, 1, 2000, false, 4, certain, SpecialAttack.Drain.DRAGON_WARHAMMER);
		assertTrue(zero[0] - one[0] > teamZero[0] - teamOne[0]);
		assertEquals(1, teamOne[1], EPS);
	}

	/**
	 * An Olm-hand-like target: Defence 260 against a 30000 attack roll, so each landed hammer (30% of current Defence)
	 * raises the accuracy of every attack. 40 max hit every 2.4s; the hammer hits 60 every 3.6s.
	 */
	private static final SpecialRotation.Fight DEFENSIVE = new SpecialRotation.Fight()
	{
		@Override public DpsResult ordinary(SpecialAttacks drains) { return attack(drains, 40, 4); }
		@Override public DpsResult spec(SpecialAttacks drains) { return attack(drains, 60, 6); }

		private DpsResult attack(SpecialAttacks drains, int max, int ticks)
		{
			int defence = 260;
			for (int i = 0; i < drains.getDragonWarhammer(); i++) { defence -= defence * 30 / 100; }
			double attackRoll = 30000;
			double defenceRoll = (defence + 9) * 124.0;
			double accuracy = attackRoll > defenceRoll ? 1 - (defenceRoll + 2) / (2 * (attackRoll + 1))
				: attackRoll / (2 * (defenceRoll + 1));
			return new DpsResult(accuracy * max / 2.0 / (ticks * 0.6), max, accuracy, ticks, accuracy * max / 2.0, null);
		}
	};

	@Test
	public void theTeammatesHammerRollsAgainstTheDrainedDefence()
	{
		com.bestgearsetup.data.Monster target = new com.bestgearsetup.data.Monster();
		target.setName("test boss");
		target.setDefenceLevel(200);
		target.setDefCrush(50);
		SpecialRotation.Fight hammer = SpecialRotation.teammateHammer(target);
		SpecialAttacks once = SpecialAttacks.builder().dragonWarhammer(1).build();
		assertEquals(HitChance.single(SpecialRotation.TEAMMATE_ATTACK_ROLL, 209L * 114), hammer.spec(SpecialAttacks.NONE).getAccuracy(), EPS);
		// 200 Defence less 30% is 140.
		assertEquals(HitChance.single(SpecialRotation.TEAMMATE_ATTACK_ROLL, 149L * 114), hammer.spec(once).getAccuracy(), EPS);
		assertTrue(hammer.spec(once).getAccuracy() > hammer.spec(SpecialAttacks.NONE).getAccuracy());
	}

	@Test
	public void inAPartyOfFourTeammatesAttemptsShrinkWhatEachOwnHammerSaves()
	{
		double[] seconds = new double[3];
		double[] shared = new double[3];
		for (int own = 0; own <= 2; own++)
		{
			seconds[own] = SpecialRotation.drainOpenedTeam(DEFENSIVE, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, own, 2400,
				false, 4, DEFENSIVE, SpecialAttack.Drain.DRAGON_WARHAMMER)[0];
			shared[own] = SpecialRotation.drainOpenedShared(DEFENSIVE, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, own, 2400,
				false, 4)[0];
		}
		double first = seconds[0] - seconds[1];
		double second = seconds[1] - seconds[2];
		double sharedFirst = shared[0] - shared[1];
		double sharedSecond = shared[1] - shared[2];
		String values = "team " + first + "/" + second + ", shared " + sharedFirst + "/" + sharedSecond;
		assertTrue(values, second > 0 && second < first);
		// Without teammates' attempts the player's own hammers were credited with the whole drain benefit.
		assertTrue(values, first < sharedFirst / 1.5 && second < sharedSecond / 1.5);
		// The team baseline already includes the other three attempts, whether or not this player specs.
		assertTrue(seconds[0] < shared[0]);
	}

	@Test
	public void fullBarDrainsOpenTheKillAndEachOutcomeFinishesAtItsStats()
	{
		SpecialRotation.Blend b = SpecialRotation.draining(WARHAMMER, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, 500,
			SpecEnergy.FULL_BAR, SpecialRotation.REGEN_PER_SECOND);
		// A landed spec deals 24. Two hits: 4.8s + 452/10; one: 4.8s + 476/8 (either order); none: 4.8s + 500/5.
		double seconds = 4.8 + 0.25 * 45.2 + 0.5 * 59.5 + 0.25 * 100;
		assertEquals(500 / seconds, b.getResult().getPrimaryDps(), EPS);
		assertEquals(2, b.getSpecsPerKill(), EPS);
		// The undrained attack's stats are kept.
		assertEquals(30, b.getResult().getMaxHit());
	}

	@Test
	public void worstCaseDrainsAllMissAndOnlyCostTheirAttacks()
	{
		SpecialRotation.Blend b = SpecialRotation.draining(WARHAMMER, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, 500,
			SpecEnergy.FULL_BAR, SpecialRotation.REGEN_PER_SECOND, true);
		// Two missed specs (4.8s), then the whole 500 HP at the undrained 5 DPS.
		assertEquals(500 / 104.8, b.getResult().getPrimaryDps(), EPS);
		assertEquals(2, b.getSpecsPerKill(), EPS);
	}

	@Test
	public void worstCaseWithRegeneratedEnergyOpensWithOneKillsEnergy()
	{
		SpecialRotation.Blend b = SpecialRotation.draining(WARHAMMER, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, 500,
			SpecEnergy.REGENERATION, SpecialRotation.REGEN_PER_SECOND, true);
		double seconds = 500 / b.getResult().getPrimaryDps();
		double m = b.getSpecsPerKill();
		// Fewer specs than a full bar's two, each a 2.4s miss before the undrained 100s.
		assertTrue(m > 0 && m < 1);
		assertEquals(SpecialRotation.REGEN_PER_SECOND * seconds / 50, m, 1e-4);
		assertEquals(100 + m * 2.4, seconds, 1e-6);
	}

	@Test
	public void longKillsSpendRegenerationBeyondAFullBarDuringTheKill()
	{
		// A spec (12 DPS) that still beats the twice-drained ordinary attacks (10 DPS).
		SpecialRotation.Fight strong = new SpecialRotation.Fight()
		{
			@Override
			public DpsResult ordinary(SpecialAttacks drains)
			{
				return WARHAMMER.ordinary(drains);
			}

			@Override
			public DpsResult spec(SpecialAttacks drains)
			{
				return new DpsResult(12, 50, 0.5, 4, 28.8, null);
			}
		};
		SpecialRotation.Blend b = SpecialRotation.draining(strong, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, 5000,
			SpecEnergy.REGENERATION, SpecialRotation.REGEN_PER_SECOND);
		double seconds = 5000 / b.getResult().getPrimaryDps();
		// Over 300s regenerates more than a full bar: two opening specs, then the rest during the kill, so all of
		// one kill's regenerated energy is spent.
		assertTrue(b.getSpecsPerKill() > 2);
		assertEquals(SpecialRotation.REGEN_PER_SECOND * seconds / 50, b.getSpecsPerKill(), 1e-3);
	}

	@Test
	public void regeneratedDrainsOpenEachKillWithTheEnergyOfOneKill()
	{
		SpecialRotation.Blend b = SpecialRotation.draining(WARHAMMER, SpecialAttack.Drain.DRAGON_WARHAMMER, 50, 500,
			SpecEnergy.REGENERATION, SpecialRotation.REGEN_PER_SECOND);
		double seconds = 500 / b.getResult().getPrimaryDps();
		// Self-consistent: the specs opening a kill are what one kill regenerates.
		assertEquals(SpecialRotation.REGEN_PER_SECOND * seconds / 50, b.getSpecsPerKill(), 1e-4);
		// Between no spec (100s) and one spec every kill (82.15s).
		double m = b.getSpecsPerKill();
		assertTrue(m > 0 && m < 1);
		assertEquals(100 + m * (82.15 - 100), seconds, 1e-3);
	}

	@Test
	public void lightbearerDoublesRegeneratedSpecs()
	{
		SpecialRotation.Blend plain = SpecialRotation.blend(ORDINARY, SPEC, 50, 500, SpecEnergy.REGENERATION);
		SpecialRotation.Blend lit = SpecialRotation.blend(ORDINARY, SPEC, 50, 500, SpecEnergy.REGENERATION,
			2 * SpecialRotation.REGEN_PER_SECOND);
		double share = 2 * 2.4 / 3 / 50;
		assertEquals(share * 10 + (1 - share) * 5, lit.getResult().getPrimaryDps(), EPS);
		assertTrue(lit.getSpecsPerKill() > 1.9 * plain.getSpecsPerKill());
	}
}
