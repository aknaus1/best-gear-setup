package com.bestgearsetup.calc;

import com.bestgearsetup.data.Monster;
import lombok.Value;

/**
 * Special attacks spent during a kill. Energy regenerates 10% every 30 seconds (twice as fast with
 * lightbearer); a full bar adds the whole specs it holds at the start of the kill (its remainder is not
 * carried over). Weapon-switch timing and overkill are not modelled.
 *
 * <p>A damage special replaces one ordinary attack for the spec weapon's attack interval, and is used only
 * when it out-damages the ordinary attacks over that interval. A draining special (dragon warhammer,
 * Bandos godsword...) opens each kill instead, so its drain helps the rest of the fight: each opening spec
 * hits or misses at the target's current stats, and the kill finishes with ordinary attacks at the drained
 * stats. With regeneration only, the opening uses the energy regenerated over one kill.
 */
public final class SpecialRotation
{
	/** Percent of special attack energy regenerated per second. */
	public static final double REGEN_PER_SECOND = 10.0 / 30;
	public static final int FULL_ENERGY = 100;
	/** Bound probabilistic branching; larger parties assume at most seven assigned teammate openers. */
	public static final int MAX_TEAM_OPENERS = 7;
	private static final double SECONDS_PER_TICK = 0.6;
	/**
	 * A reference teammate's dragon warhammer, the same for every kit: 99 Attack with a super combat potion and Piety
	 * on the accurate style ((99 + 19) * 1.2 + 3 + 8 = 152) at +120 crush (the hammer's +95 and a defender and gloves),
	 * and 99 Strength likewise ((99 + 19) * 1.23 + 8 = 153) at +110 strength, a 42 max hit that the special raises to 63.
	 * No overload, salve or slayer bonus, so it under- rather than overstates a raid team's hammers.
	 */
	public static final long TEAMMATE_ATTACK_ROLL = 152L * (120 + 64);
	public static final int TEAMMATE_HAMMER_MAX = 63;
	private static final int HAMMER_TICKS = 6;
	/** Iterations of the regenerated-opening fixed point (specs per kill depend on the kill time). */
	private static final int REGEN_ITERATIONS = 8;

	private SpecialRotation()
	{
	}

	/** Whole-kill damage rates with specs, and the specs used per kill. */
	@Value
	public static class Blend
	{
		DpsResult result;
		double specsPerKill;
	}

	/** The fight's ordinary attacks and special attack once some drains have landed. */
	public interface Fight
	{
		DpsResult ordinary(SpecialAttacks drains);

		DpsResult spec(SpecialAttacks drains);
	}

	public static Blend blend(DpsResult ordinary, DpsResult spec, int energy, int targetHp, SpecEnergy mode)
	{
		return blend(ordinary, spec, energy, targetHp, mode, REGEN_PER_SECOND);
	}

	/**
	 * Blend ordinary attacks with damage specs over a kill of the target's HP, or null when the spec never helps.
	 *
	 * @param ordinary the setup's ordinary attacks over the whole fight
	 * @param spec     one special attack, repeated at its interval
	 * @param energy   energy each spec costs, in percent
	 * @param regen    energy regenerated per second, in percent
	 */
	public static Blend blend(DpsResult ordinary, DpsResult spec, int energy, int targetHp, SpecEnergy mode,
		double regen)
	{
		double ordinaryRate = ordinary.getPrimaryDps();
		double specRate = spec.getPrimaryDps();
		double specSeconds = spec.getExpectedSpeedTicks() * SECONDS_PER_TICK;
		if (energy <= 0 || targetHp <= 0 || specSeconds <= 0 || specRate <= ordinaryRate)
		{
			return null;
		}
		// Share of fight time spent speccing on regenerated energy alone.
		double regenShare = Math.min(1, regen * specSeconds / energy);
		double sustainedRate = regenShare * specRate + (1 - regenShare) * ordinaryRate;
		int opening = mode == SpecEnergy.FULL_BAR ? FULL_ENERGY / energy : 0;
		double specDamage = specRate * specSeconds;

		double seconds;
		double specs;
		if (opening * specDamage >= targetHp)
		{
			// The opening specs kill the target alone.
			seconds = targetHp / specRate;
			specs = targetHp / specDamage;
		}
		else
		{
			double openingSeconds = opening * specSeconds;
			seconds = openingSeconds + (targetHp - opening * specDamage) / sustainedRate;
			specs = opening + regenShare * (seconds - openingSeconds) / specSeconds;
		}
		double specSecondsTotal = specs * specSeconds;
		double dps = (specSecondsTotal * spec.getDps() + (seconds - specSecondsTotal) * ordinary.getDps()) / seconds;
		return new Blend(ordinary.withDamageRates(dps, targetHp / seconds), specs);
	}

	/** A damage special in a raid rotation: one attack of it, at the target's undrained stats, and its energy. */
	@Value
	public static class Damage
	{
		DpsResult spec;
		int energy;
	}

	/** A raid rotation's whole-kill rates, and how it uses each damage special. */
	@Value
	public static class Rotation
	{
		DpsResult result;
		/** Specs per kill of each damage special, in the order given. */
		double[] specs;
		/** Energy spent from the bar at the start of the kill, as whole opening specs. */
		double openingEnergy;
	}

	/**
	 * Evaluate damage specials over one player's HP share after a draining opener's whole-kill time.
	 * Every special pays its full attack interval. Only ordinary end-of-fight timing is adjusted by finalBlows.
	 */
	public static Rotation rotation(DpsResult ordinary, double baseSeconds, int hp, double energy, double regen,
		java.util.List<Damage> damage, double finalBlows)
	{
		if (!(baseSeconds > 0) || Double.isInfinite(baseSeconds) || hp <= 0 || ordinary.getPrimaryDps() <= 0)
		{
			return null;
		}
		double rate = hp / baseSeconds;
		int count = damage.size();
		double[] seconds = new double[count];
		double[] dealt = new double[count];
		double[] gain = new double[count];
		int[] cost = new int[count];
		// The special energy regenerated during the kill buys: the most damage gained per energy.
		int regenerated = -1;
		for (int j = 0; j < count; j++)
		{
			DpsResult spec = damage.get(j).getSpec();
			seconds[j] = spec.getExpectedSpeedTicks() * SECONDS_PER_TICK;
			dealt[j] = spec.getPrimaryDps() * seconds[j];
			gain[j] = dealt[j] - rate * seconds[j];
			cost[j] = damage.get(j).getEnergy();
			if (cost[j] > 0 && gain[j] > 0 && (regenerated < 0 || gain[j] / cost[j] > gain[regenerated] / cost[regenerated]))
			{
				regenerated = j;
			}
		}
		RotationSearch search = new RotationSearch(rate, baseSeconds, energy, regen, finalBlows, ordinary.getAverageHit(),
			seconds, dealt, gain, cost, regenerated);
		search.opening(new int[count], 0, 0);
		if (search.best == null)
		{
			return null;
		}
		double primary = hp / search.bestSeconds;
		double total = primary * ordinary.getDps() / ordinary.getPrimaryDps();
		return new Rotation(ordinary.withDamageRates(total, primary), search.best, search.bestOpening);
	}

	/** Every whole number of opening specs within the available energy. */
	private static final class RotationSearch
	{
		private final double rate;
		private final double baseSeconds;
		private final double energy;
		private final double regen;
		private final double finalBlows;
		private final double lastHit;
		private final double[] seconds;
		private final double[] dealt;
		private final double[] gain;
		private final int[] cost;
		private final int regenerated;
		double[] best;
		double bestOpening;
		double bestSeconds = Double.POSITIVE_INFINITY;

		RotationSearch(double rate, double baseSeconds, double energy, double regen, double finalBlows, double lastHit,
			double[] seconds, double[] dealt, double[] gain, int[] cost, int regenerated)
		{
			this.rate = rate;
			this.baseSeconds = baseSeconds;
			this.energy = energy;
			this.regen = regen;
			this.finalBlows = finalBlows;
			this.lastHit = lastHit;
			this.seconds = seconds;
			this.dealt = dealt;
			this.gain = gain;
			this.cost = cost;
			this.regenerated = regenerated;
		}

		void opening(int[] counts, int from, double spent)
		{
			if (from == counts.length)
			{
				evaluate(counts, spent);
				return;
			}
			for (int n = 0; cost[from] > 0 && spent + n * cost[from] <= energy + 1e-9 || n == 0; n++)
			{
				counts[from] = n;
				// A special that loses damage over its interval never pays.
				if (n > 0 && gain[from] <= 0) { break; }
				opening(counts, from + 1, spent + n * cost[from]);
				if (cost[from] <= 0) { break; }
			}
			counts[from] = 0;
		}

		private void evaluate(int[] counts, double spent)
		{
			double gained = 0;
			double specTime = 0;
			for (int j = 0; j < counts.length; j++)
			{
				gained += counts[j] * gain[j];
				specTime += counts[j] * seconds[j];
			}
			double last = finalBlows * lastHit;
			double time = baseSeconds - (gained + last) / rate;
			double extra = 0;
			if (regenerated >= 0 && regen > 0)
			{
				double perSecond = regen * gain[regenerated] / (cost[regenerated] * rate);
				double spentTime = time / (1 + perSecond);
				double specs = regen * spentTime / cost[regenerated];
				if (specs > 0) { time = spentTime; extra = specs; }
			}
			// The opening specs take their own intervals, however much they deal.
			time = Math.max(time, Math.max(specTime, SECONDS_PER_TICK));
			if (time < bestSeconds - 1e-12)
			{
				bestSeconds = time;
				best = new double[counts.length];
				for (int j = 0; j < counts.length; j++)
				{
					best[j] = counts[j] + (j == regenerated ? extra : 0);
				}
				bestOpening = spent;
			}
		}
	}

	/** A kill opened by {@code specs} draining specials and finished with ordinary attacks at the drained stats, with no specials in between: {seconds, specs used}. */
	public static double[] drainOpened(Fight fight, SpecialAttack.Drain drain, int energy, int specs, int hp, boolean allMiss)
	{
		return new DrainKill(fight, drain, energy, 0, allMiss).path(specs, SpecialAttacks.NONE, hp);
	}

	/**
	 * One player's draining opener in a party of equal ordinary-DPS teammates. Teammates keep attacking during
	 * this player's spec intervals and benefit from landed drains. Only this player's hits cause further drains.
	 */
	public static double[] drainOpenedShared(Fight fight, SpecialAttack.Drain drain, int energy, int specs, int hp,
		boolean allMiss, int party)
	{
		return new DrainKill(fight, drain, energy, 0, allMiss, party).path(specs, SpecialAttacks.NONE, hp);
	}

	/**
	 * Raid bosses a party conventionally opens with defence reducers, so teammates' attempts are assumed before this
	 * player's ({@link #drainOpenedTeam}): each Olm melee hand phase (a fresh hand), Tekton's first phase, Maiden,
	 * Sotetseg ({@link #sotetsegTeammates}) and Xarpus. Not Tekton enraged (the same HP pool, already opened), Maiden's
	 * nylocas, Bloat (one down per drain), Verzik (undrainable) or ToA (invocation-scaled floors and no fixed team
	 * opener).
	 */
	public static boolean teamReducerTarget(String name)
	{
		String lower = name.toLowerCase(java.util.Locale.ROOT);
		return lower.equals("great olm (melee hand)") || lower.equals("tekton (normal)") || lower.startsWith("sotetseg")
			|| lower.startsWith("xarpus") || lower.startsWith("the maiden of sugadinti");
	}

	/** Reducer attempts a party aims to land in each Sotetseg third, each player making at most one. */
	public static final int SOTETSEG_TEAM_REDUCERS = 2;
	public static final int SOTETSEG_THIRDS = 3;

	/** The party's reducer attempts in each Sotetseg third: two, or fewer in a smaller party (one each). */
	public static int sotetsegTeamPerThird(int party)
	{
		return Math.min(SOTETSEG_TEAM_REDUCERS, Math.max(0, party));
	}

	/**
	 * This player's fair share of the party's Sotetseg reducers per kill, at most one a third: the party's
	 * {@link #sotetsegTeamPerThird} in each of the three thirds divided by the party, rounded up (two in a party of three
	 * to five, three in a party of two).
	 */
	public static int sotetsegShare(int party)
	{
		if (party < 2)
		{
			return 0;
		}
		int team = sotetsegTeamPerThird(party) * SOTETSEG_THIRDS;
		return Math.min(SOTETSEG_THIRDS, (team + party - 1) / party);
	}

	/**
	 * This player's fair share of the party's reducers at a {@link #teamReducerTarget} so no kit leeches the teammates'
	 * openers: the {@link #sotetsegShare} at Sotetseg, else one, as each teammate is assumed to open with one. 0 for
	 * any other target or outside a party.
	 */
	public static int reducerShare(String name, int party)
	{
		if (party < 2 || !teamReducerTarget(name))
		{
			return 0;
		}
		return name.toLowerCase(java.util.Locale.ROOT).startsWith("sotetseg") ? sotetsegShare(party) : 1;
	}

	/**
	 * Teammates' reducer attempts in one Sotetseg third, each from a different teammate: the party's
	 * {@link #sotetsegTeamPerThird} less this player's reducer there, whether assigned or made ({@code own}, at most one).
	 * An assigned reducer this player can't fund is not made up by a teammate.
	 */
	public static int sotetsegTeammates(int party, int own)
	{
		return Math.min(Math.min(MAX_TEAM_OPENERS, Math.max(0, party - 1)), Math.max(0, sotetsegTeamPerThird(party) - own));
	}

	/**
	 * As {@link #sotetsegTeammates(int, int)} for a third where this player makes {@code own} specials that drain as
	 * {@code drain} and is assigned {@code assigned}: only an elder maul, warhammer or godsword stands in for a teammate.
	 */
	public static int sotetsegTeammates(int party, SpecialAttack.Drain drain, int own, int assigned)
	{
		return sotetsegTeammates(party, Math.max(sharedReducer(drain) ? own : 0, assigned));
	}

	/** Whether a weapon of this name is an elder maul, dragon warhammer or Bandos godsword (ornament kits included). */
	public static boolean sharedReducerName(String name)
	{
		String lower = name.toLowerCase(java.util.Locale.ROOT);
		return lower.startsWith("elder maul") || lower.startsWith("dragon warhammer") || lower.startsWith("bandos godsword");
	}

	/** Whether this special counts toward a Sotetseg fair share: the elder maul, dragon warhammer and Bandos godsword. */
	public static boolean sharedReducer(SpecialAttack.Drain drain)
	{
		return drain == SpecialAttack.Drain.ELDER_MAUL || drain == SpecialAttack.Drain.DRAGON_WARHAMMER
			|| drain == SpecialAttack.Drain.BANDOS_GODSWORD;
	}

	/**
	 * The reference teammate's dragon warhammer special ({@link #TEAMMATE_ATTACK_ROLL}) against this target as drains
	 * land, independent of the player's own kit. Only its special is used.
	 */
	public static Fight teammateHammer(Monster target)
	{
		return new Fight()
		{
			@Override
			public DpsResult ordinary(SpecialAttacks drains)
			{
				return spec(drains);
			}

			@Override
			public DpsResult spec(SpecialAttacks drains)
			{
				Monster drained = drains.apply(target);
				double accuracy = HitChance.single(TEAMMATE_ATTACK_ROLL,
					CombatRules.defenceRoll(drained, drained.getDefenceLevel(), drained.getDefCrush()));
				double average = accuracy * TEAMMATE_HAMMER_MAX / 2.0;
				return new DpsResult(average / (HAMMER_TICKS * SECONDS_PER_TICK), TEAMMATE_HAMMER_MAX, accuracy, HAMMER_TICKS,
					average, null);
			}
		};
	}

	/**
	 * One finite, staggered teammate attempt each before this player's opener. The teammate weapon and accuracy
	 * are an explicit estimate; hit and miss branches remain separate. Only this player's attempts count as specs
	 * used. The identical prefix also applies when {@code specs} is zero, so team benefit is never credited solely
	 * to the player's own opener. Other members keep attacking during each full attack interval.
	 */
	public static double[] drainOpenedTeam(Fight fight, SpecialAttack.Drain drain, int energy, int specs, int hp,
		boolean allMiss, int party, Fight teammate, SpecialAttack.Drain teammateDrain)
	{
		return drainOpenedTeam(fight, drain, energy, specs, hp, allMiss, party,
			Math.min(MAX_TEAM_OPENERS, Math.max(0, party - 1)), teammate, teammateDrain);
	}

	/** As above, with this many teammates' attempts (fewer than the other party members where a party shares them). */
	public static double[] drainOpenedTeam(Fight fight, SpecialAttack.Drain drain, int energy, int specs, int hp,
		boolean allMiss, int party, int teammates, Fight teammate, SpecialAttack.Drain teammateDrain)
	{
		DrainKill kill = new DrainKill(fight, drain, energy, 0, allMiss, party);
		kill.teammate = teammate;
		kill.teammateDrain = teammateDrain;
		return kill.path(specs, Math.min(teammates, Math.max(0, party - 1)), SpecialAttacks.NONE, hp);
	}

	/**
	 * A kill whose drains are lost once {@code windowHp} has been dealt ({@link CombatRules#drainPersistence}): the
	 * draining blend over that window, then the rest of the kill at the target's restored stats, speccing for damage
	 * on the energy regenerated meanwhile. Null when the rest can't be dealt.
	 */
	public static Blend resetAfter(Blend window, int windowHp, DpsResult ordinary, DpsResult spec, int energy, int hp,
		double regen)
	{
		int restHp = hp - windowHp;
		if (restHp <= 0)
		{
			return window;
		}
		Blend rest = blend(ordinary, spec, energy, restHp, SpecEnergy.REGENERATION, regen);
		DpsResult restRate = rest == null ? ordinary : rest.getResult();
		double seconds = windowHp / window.getResult().getPrimaryDps() + restHp / restRate.getPrimaryDps();
		if (!(seconds > 0) || Double.isInfinite(seconds))
		{
			return null;
		}
		double primary = hp / seconds;
		double total = primary * ordinary.getDps() / ordinary.getPrimaryDps();
		return new Blend(ordinary.withDamageRates(total, primary),
			window.getSpecsPerKill() + (rest == null ? 0 : rest.getSpecsPerKill()));
	}

	/**
	 * Open each kill with draining specs and finish at the drained stats, or null when the target can't be
	 * killed. The result keeps the undrained ordinary attack's max hit and accuracy.
	 */
	public static Blend draining(Fight fight, SpecialAttack.Drain drain, int energy, int targetHp, SpecEnergy mode,
		double regen)
	{
		return draining(fight, drain, energy, targetHp, mode, regen, false);
	}

	/**
	 * As {@link #draining(Fight, SpecialAttack.Drain, int, int, SpecEnergy, double)}.
	 *
	 * @param allMiss the worst case: every opening spec misses, costing its attack and draining nothing
	 */
	public static Blend draining(Fight fight, SpecialAttack.Drain drain, int energy, int targetHp, SpecEnergy mode,
		double regen, boolean allMiss)
	{
		DpsResult start = fight.ordinary(SpecialAttacks.NONE);
		if (energy <= 0 || targetHp <= 0 || start.getPrimaryDps() <= 0)
		{
			return null;
		}
		int barSpecs = FULL_ENERGY / energy;
		double[] r;
		if (mode == SpecEnergy.FULL_BAR)
		{
			r = new DrainKill(fight, drain, energy, regen, allMiss).path(barSpecs, SpecialAttacks.NONE, targetHp);
		}
		else
		{
			// The regenerated energy banked over one kill opens the next; a fraction is a spec on some kills. A
			// bar holds only so many specs, so energy regenerated beyond it is spent during the kill instead.
			double specSeconds = fight.spec(SpecialAttacks.NONE).getExpectedSpeedTicks() * SECONDS_PER_TICK;
			r = new double[]{targetHp / start.getPrimaryDps(), 0};
			for (int i = 0; i < REGEN_ITERATIONS; i++)
			{
				double regenerated = regen * r[0];
				double opening = Math.min(barSpecs, regenerated / energy);
				double excess = Math.max(0, regenerated - barSpecs * energy);
				double midFight = excess / Math.max(specSeconds, r[0] - barSpecs * specSeconds);
				DrainKill kill = new DrainKill(fight, drain, energy, midFight, allMiss);
				int whole = (int) opening;
				double fraction = opening - whole;
				r = kill.path(whole, SpecialAttacks.NONE, targetHp);
				if (fraction > 0)
				{
					double[] more = kill.path(whole + 1, SpecialAttacks.NONE, targetHp);
					r = new double[]{r[0] + fraction * (more[0] - r[0]), r[1] + fraction * (more[1] - r[1])};
				}
			}
		}
		double seconds = r[0];
		if (!(seconds > 0) || Double.isInfinite(seconds))
		{
			return null;
		}
		double primary = targetHp / seconds;
		double total = primary * start.getDps() / start.getPrimaryDps();
		return new Blend(start.withDamageRates(total, primary), r[1]);
	}

	/** Expected kill time and specs used from a point in the opening. */
	private static final class DrainKill
	{
		private final Fight fight;
		private final SpecialAttack.Drain drain;
		private final int energy;
		/** Energy per second that can be spent after the opening specs. */
		private final double midFightRegen;
		private final boolean allMiss;
		private final int party;
		private Fight teammate;
		private SpecialAttack.Drain teammateDrain;

		DrainKill(Fight fight, SpecialAttack.Drain drain, int energy, double midFightRegen, boolean allMiss)
		{
			this(fight, drain, energy, midFightRegen, allMiss, 1);
		}

		DrainKill(Fight fight, SpecialAttack.Drain drain, int energy, double midFightRegen, boolean allMiss, int party)
		{
			this.fight = fight;
			this.drain = drain;
			this.energy = energy;
			this.midFightRegen = midFightRegen;
			this.allMiss = allMiss;
			this.party = Math.max(1, party);
		}

		/** {seconds, specs} with the given opening specs still to throw and HP left. */
		double[] path(int left, SpecialAttacks drains, double hp)
		{
			return path(left, 0, drains, hp);
		}

		double[] path(int left, int teamLeft, SpecialAttacks drains, double hp)
		{
			if (hp <= 0) { return new double[]{0, 0}; }
			if (left <= 0 && teamLeft <= 0)
			{
				return remainder(drains, hp);
			}
			boolean teamAttack = teamLeft > 0;
			double used = teamAttack ? 0 : 1;
			int nextOwn = left - (teamAttack ? 0 : 1);
			int nextTeam = teamLeft - (teamAttack ? 1 : 0);
			DpsResult spec = (teamAttack ? teammate : fight).spec(drains);
			double seconds = spec.getExpectedSpeedTicks() * SECONDS_PER_TICK;
			double perSpec = spec.getPrimaryDps() * seconds;
			double accuracy = Math.max(0, Math.min(1, spec.getAccuracy()));
			double teammatesDamage = (party - 1) * fight.ordinary(drains).getPrimaryDps() * seconds;
			if (seconds <= 0)
			{
				return remainder(drains, hp);
			}
			if (allMiss || perSpec <= 0 || accuracy <= 0)
			{
				if (teammatesDamage >= hp) { return new double[]{seconds * hp / teammatesDamage, used}; }
				double[] after = path(nextOwn, nextTeam, drains, hp - teammatesDamage);
				return new double[]{seconds + after[0], used + after[1]};
			}
			// A landed spec deals its expected damage given a hit; a miss deals nothing.
			double hitDamage = perSpec / accuracy;
			double[] hit;
			if (hitDamage + teammatesDamage >= hp)
			{
				hit = new double[]{seconds * hp / (hitDamage + teammatesDamage), used};
			}
			else
			{
				double[] after = path(nextOwn, nextTeam, drains.landed(teamAttack ? teammateDrain : drain, (int) Math.round(hitDamage)), hp - hitDamage - teammatesDamage);
				hit = new double[]{seconds + after[0], used + after[1]};
			}
			if (accuracy >= 1)
			{
				return hit;
			}
			double[] missed;
			if (teammatesDamage >= hp)
			{
				missed = new double[]{seconds * hp / teammatesDamage, used};
			}
			else
			{
				double[] after = path(nextOwn, nextTeam, drains, hp - teammatesDamage);
				missed = new double[]{seconds + after[0], used + after[1]};
			}
			return new double[]{
				accuracy * hit[0] + (1 - accuracy) * missed[0],
				accuracy * hit[1] + (1 - accuracy) * missed[1],
			};
		}

		/**
		 * The rest of the kill at the drained stats, speccing for damage on any energy regenerated meanwhile
		 * (drains from those specs are not applied).
		 */
		private double[] remainder(SpecialAttacks drains, double hp)
		{
			double ordinary = party * fight.ordinary(drains).getPrimaryDps();
			if (midFightRegen > 0)
			{
				DpsResult spec = fight.spec(drains);
				double seconds = spec.getExpectedSpeedTicks() * SECONDS_PER_TICK;
				if (seconds > 0 && spec.getPrimaryDps() > ordinary)
				{
					double share = Math.min(1, midFightRegen * seconds / energy);
					double time = hp / (share * spec.getPrimaryDps() + (1 - share) * ordinary);
					return new double[]{time, share * time / seconds};
				}
			}
			return new double[]{ordinary > 0 ? Math.max(0, hp) / ordinary : Double.POSITIVE_INFINITY, 0};
		}
	}
}
