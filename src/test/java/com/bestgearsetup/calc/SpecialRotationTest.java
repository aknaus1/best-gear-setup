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
			int dps = new int[]{5, 8, 10}[drains.getDragonWarhammer()];
			return new DpsResult(dps, 30, 0.8, 4, dps * 2.4, null);
		}

		@Override
		public DpsResult spec(SpecialAttacks drains)
		{
			return new DpsResult(5, 50, 0.5, 4, 12, null);
		}
	};

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
