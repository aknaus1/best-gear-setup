package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.StatusImmunities;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * Damage over time from poison, venom and burns, integrated over the kill (OSRS Wiki Poison, Venom and
 * Burn pages). Poison and venom hit every 30 ticks on the target's own timer (random phase); poison
 * deals floor((severity + 4) / 5) and loses one severity per hit, and each landed hit may reset it;
 * venom starts at 6 and rises by 2 per hit to 20 and never resets. Burns deal 1 per stack every 4 ticks
 * for 10 hits, up to 5 stacks, and are mitigated as ranged damage. The kill time T solves
 * direct damage x T + expected damage over time by T = HP, so short kills gain little.
 */
public final class StatusEffects
{
	static final int POISON_INTERVAL = 30;
	static final int BURN_INTERVAL = 4;
	static final int BURN_HITS = 10;
	static final int BURN_STACKS = 5;
	private static final double SECONDS_PER_TICK = 0.6;
	private static final int VENOM_AS_POISON = 26;
	private static final Pattern POISONABLE_MELEE = Pattern.compile(
		"(bronze|iron|steel|black|white|mithril|adamant|rune|dragon) (dagger|spear|hasta)|abyssal dagger|bone dagger");
	private static final Pattern POISONABLE_AMMO = Pattern.compile(
		"(bronze|iron|steel|black|mithril|adamant|rune|amethyst|dragon) (arrows?|dart|knife|javelin)"
			+ "|(bronze|iron|steel|mithril|adamant|runite|dragon|silver|blurite|bone) bolts");

	private StatusEffects()
	{
	}

	/** Chances per attack (already multiplied by accuracy), severities, and the burn mitigation factor. */
	@Value
	static class Sources
	{
		double poisonChance;
		int poisonSeverity;
		double venomChance;
		double burnChance;
		/** Fraction of burn damage left after the target's ranged mitigation. */
		double burnFactor;
		List<String> notes;

		boolean any()
		{
			return poisonChance > 0 && poisonSeverity > 0 || venomChance > 0 || burnChance > 0 && burnFactor > 0;
		}
	}

	static boolean serpentineHelm(Loadout l)
	{
		String head = EncounterDamage.lower(l.get(Slot.HEAD));
		return (head.startsWith("serpentine helm") || head.startsWith("tanzanite helm") || head.startsWith("magma helm"))
			&& !head.contains("uncharged");
	}

	/** Whether the weapon option poisons this weapon or its ammunition. */
	static boolean poisonable(Loadout l)
	{
		GearItem weapon = l.getWeapon();
		if (weapon == null)
		{
			return false;
		}
		String w = EncounterDamage.lower(weapon);
		if (l.getStyle().isMelee())
		{
			return POISONABLE_MELEE.matcher(w).matches();
		}
		if (l.getStyle().getType() != AttackStyle.Type.RANGED)
		{
			return false;
		}
		if (WeaponRules.firesAmmoSlot(weapon))
		{
			return POISONABLE_AMMO.matcher(EncounterDamage.lower(l.get(Slot.AMMO))).matches();
		}
		return weapon.getSubcategory().equals("thrown") && POISONABLE_AMMO.matcher(w).matches();
	}

	static Sources sources(Loadout l, CombatContext ctx, double accuracy)
	{
		Monster m = ctx.getMonster();
		List<String> notes = new ArrayList<>();
		GearItem weapon = l.getWeapon();
		String w = EncounterDamage.lower(weapon);
		CombatClass cls = WeaponRules.classOf(l.getStyle());
		boolean serpentine = serpentineHelm(l);
		WeaponPoison option = ctx.getModifiers().getWeaponPoison();
		double poisonMiss = 1;
		int severity = 0;
		double venom = 0;
		boolean poisoned = option != WeaponPoison.NONE && poisonable(l);
		boolean toxic = false;
		switch (cls)
		{
			case MELEE:
				if (poisoned)
				{
					poisonMiss *= 1 - accuracy / 4;
					severity = Math.max(severity, option.getSeverity());
				}
				if (w.startsWith("abyssal tentacle"))
				{
					poisonMiss *= 1 - accuracy / 4;
					severity = Math.max(severity, 20);
				}
				if (w.equals("swamp lizard"))
				{
					poisonMiss *= 1 - accuracy / 4;
					severity = Math.max(severity, 30);
				}
				if (w.startsWith("toxic staff of the dead"))
				{
					venom = serpentine ? 1 : 0.25;
					toxic = true;
				}
				else if (w.startsWith("noxious halberd"))
				{
					venom = serpentine ? 0.5 : 1 / 3.0;
					toxic = true;
				}
				break;
			case RANGED:
				if (poisoned)
				{
					poisonMiss *= 1 - accuracy / 8;
					severity = Math.max(severity, option.getSeverity() - 14);
				}
				String ammo = EncounterDamage.lower(l.get(Slot.AMMO));
				if (weapon.getSubcategory().equals("crossbow") && WeaponRules.firesAmmoSlot(weapon)
					&& (ammo.equals("emerald bolts (e)") || ammo.equals("emerald dragon bolts (e)")))
				{
					// Magical Poison: 55% of successful hits, not reduced like other ranged poison.
					double chance = Math.min(1, 0.55 * (ctx.getModifiers().isKandarinDiary() ? 1.1 : 1));
					poisonMiss *= 1 - accuracy * chance;
					severity = Math.max(severity, w.startsWith("zaryte crossbow") ? 27 : 25);
				}
				if (w.startsWith("toxic blowpipe"))
				{
					venom = serpentine ? 1 : 0.25;
					toxic = true;
				}
				break;
			default:
			{
				String spell = EncounterDamage.spell(l);
				if (spell.startsWith("smoke "))
				{
					int base = spell.endsWith("rush") || spell.endsWith("burst") ? 10 : 20;
					// Every ancient sceptre raises Ancient Magicks effects by 10%.
					poisonMiss *= 1 - accuracy / 8;
					severity = Math.max(severity, w.contains("ancient sceptre") ? base * 11 / 10 : base);
				}
				if (w.startsWith("trident of the swamp") || w.startsWith("toxic staff of the dead") && l.getSpell() != null)
				{
					venom = serpentine ? 1 : 0.25;
					toxic = true;
				}
				break;
			}
		}
		if (serpentine && !toxic)
		{
			// Wiki: 1/6 with unpoisoned melee weapons, 1/2 with poisoned weapons; emerald bolts do not count.
			venom = poisoned ? 0.5 : cls == CombatClass.MELEE ? 1 / 6.0 : 0;
		}
		double venomChance = accuracy * venom;
		double poisonChance = severity > 0 ? 1 - poisonMiss : 0;

		StatusImmunities.Immunity immunity = StatusImmunities.of(m);
		if (venomChance > 0)
		{
			if (!immunity.isVenomImmune())
			{
				// Venom replaces poison on the shared timer; earlier poison is ignored.
				poisonChance = 0;
				notes.add("venom");
			}
			else if (immunity.isVenomBecomesPoison())
			{
				poisonChance = 1 - (1 - poisonChance) * (1 - venomChance);
				severity = Math.max(severity, VENOM_AS_POISON);
				venomChance = 0;
				notes.add("venom becomes poison");
			}
			else
			{
				venomChance = 0;
			}
		}
		if (poisonChance > 0)
		{
			if (immunity.isPoisonImmune())
			{
				poisonChance = 0;
			}
			else if (!notes.contains("venom becomes poison"))
			{
				notes.add("poison");
			}
		}

		double burnChance = 0;
		double burnFactor = 0;
		if (WeaponRules.isAtlatl(weapon) && cls == CombatClass.RANGED && EquipmentEffects.named(l, Slot.HEAD, "eclipse moon helm")
			&& EquipmentEffects.named(l, Slot.BODY, "eclipse moon chestplate")
			&& EquipmentEffects.named(l, Slot.LEGS, "eclipse moon tassets"))
		{
			// Eclipse set: 20% of successful atlatl attacks inflict a strong burn.
			burnFactor = burnFactor(l, ctx, StatusImmunities.BURN_STRONG);
			if (burnFactor > 0)
			{
				burnChance = 0.2 * accuracy;
				notes.add("burn");
			}
		}
		return new Sources(poisonChance, severity, venomChance, burnChance, burnFactor, notes);
	}

	/**
	 * Fraction of burn damage a target takes: zero when it ignores the burn's severity or ranged damage,
	 * otherwise its ranged mitigation applied to a 10-damage burn.
	 */
	static double burnFactor(Loadout l, CombatContext ctx, int severity)
	{
		Monster m = ctx.getMonster();
		if (CombatRules.burnImmune(m) || StatusImmunities.of(m).getBurnImmunity() >= severity
			|| !EncounterImmunities.classAllowed(m, CombatClass.RANGED))
		{
			return 0;
		}
		return EncounterDamage.rule(m, l, CombatClass.RANGED).mean(BURN_HITS) / BURN_HITS;
	}

	/** Add expected damage over time to a sustained-attack result. */
	static DpsResult apply(Loadout l, CombatContext ctx, DpsResult r)
	{
		Monster m = ctx.getMonster();
		if (ctx.getModifiers().isSpecialAttack() || r.getPrimaryDps() <= 0 || CombatRules.oneHit(m))
		{
			return r;
		}
		Sources s = sources(l, ctx, r.getAccuracy());
		if (!s.any())
		{
			return r;
		}
		double speed = Math.max(1, r.getExpectedSpeedTicks());
		double direct = r.getPrimaryDps() * SECONDS_PER_TICK;
		Fight fight = new Fight(s, speed);
		boolean infinite = m.getId() == 14779 || EncounterDamage.named(m, "gemstone crab");
		int hp = ctx.getModifiers().targetHp(m);
		double extra;
		String when;
		if (infinite || hp <= 0)
		{
			extra = fight.steadyPerTick();
			when = "sustained";
		}
		else
		{
			double ticks = fight.killTime(direct, hp);
			extra = hp / ticks - direct;
			when = String.format(Locale.ROOT, "over a %.0fs kill", ticks * SECONDS_PER_TICK);
		}
		if (extra <= 1e-9)
		{
			return r;
		}
		double extraDps = extra / SECONDS_PER_TICK;
		String note = String.format(Locale.ROOT, "%s +%.2f DPS %s", String.join(" + ", s.getNotes()), extraDps, when);
		return r.withExtraDps(extraDps).withDetail(r.getMaxHitDetail() == null ? note : r.getMaxHitDetail() + "; " + note);
	}

	/** Expected cumulative damage over time for one fight. */
	static final class Fight
	{
		private final Sources s;
		private final double speed;
		private final double refresh;
		private final List<Double> poisonCumulative = new ArrayList<>();
		private double refreshPrefix;
		private double noRefresh = 1;

		Fight(Sources s, double speed)
		{
			this.s = s;
			this.speed = speed;
			// Chance that at least one attack between two poison hits lands a fresh application.
			this.refresh = 1 - Math.pow(1 - s.getPoisonChance(), POISON_INTERVAL / speed);
			poisonCumulative.add(0.0);
		}

		/** Ticks to kill with direct damage per tick plus damage over time. */
		double killTime(double direct, int hp)
		{
			double low = 0;
			double high = hp / direct;
			for (int i = 0; i < 40 && high - low > 1e-3; i++)
			{
				double mid = (low + high) / 2;
				if (direct * mid + damage(mid) >= hp)
				{
					high = mid;
				}
				else
				{
					low = mid;
				}
			}
			return high;
		}

		/** Expected damage-over-time damage dealt by tick t (attacks at 0, speed, 2 speed, ...). */
		double damage(double t)
		{
			double total = 0;
			double venomWeight = s.getVenomChance();
			double poisonWeight = s.getPoisonChance();
			for (int i = 0; ; i++)
			{
				double elapsed = t - i * speed;
				if (elapsed < 0)
				{
					break;
				}
				if (venomWeight > 1e-9)
				{
					total += venomWeight * phaseAverage(elapsed, POISON_INTERVAL, Fight::venomCumulative);
					venomWeight *= 1 - s.getVenomChance();
				}
				if (poisonWeight > 1e-9 && s.getPoisonSeverity() > 0)
				{
					total += poisonWeight * phaseAverage(elapsed, POISON_INTERVAL, this::poisonCumulative);
					poisonWeight *= 1 - s.getPoisonChance();
				}
				if (s.getBurnChance() > 0)
				{
					total += s.getBurnChance() * s.getBurnFactor() * burnCap()
						* phaseAverage(elapsed, BURN_INTERVAL, n -> Math.min(BURN_HITS, n));
				}
			}
			return total;
		}

		/** Long-run damage per tick, for targets whose HP never falls. */
		double steadyPerTick()
		{
			double venom = s.getVenomChance() > 0 ? 20.0 / POISON_INTERVAL : 0;
			double poison = 0;
			if (s.getPoisonChance() > 0)
			{
				// Each poison hit's expected damage once refreshes have reached their stationary pattern.
				double hit = 0;
				for (int k = 0; k <= s.getPoisonSeverity(); k++)
				{
					hit += refresh * Math.pow(1 - refresh, k) * poisonHit(s.getPoisonSeverity() - k);
				}
				poison = hit / POISON_INTERVAL;
			}
			double burn = s.getBurnChance() / speed * BURN_HITS * s.getBurnFactor() * burnCap();
			return venom + poison + burn;
		}

		/** Share of burn damage left after the five-stack limit discards new burns. */
		private double burnCap()
		{
			double mean = s.getBurnChance() / speed * BURN_INTERVAL * BURN_HITS;
			if (mean <= 0)
			{
				return 1;
			}
			double p = Math.exp(-mean);
			double capped = 0;
			double below = 0;
			for (int n = 0; n < BURN_STACKS; n++)
			{
				capped += n * p;
				below += p;
				p *= mean / (n + 1);
			}
			capped += BURN_STACKS * (1 - below);
			return capped / mean;
		}

		/**
		 * Average over the target's timer phase of a per-hit cumulative series: hits come at phase,
		 * phase + interval, ... with the phase uniform over one interval.
		 */
		private static double phaseAverage(double elapsed, int interval, java.util.function.IntToDoubleFunction cumulative)
		{
			int e = (int) elapsed;
			int whole = e / interval;
			int rest = e % interval;
			return (rest * cumulative.applyAsDouble(whole + 1) + (interval - rest) * cumulative.applyAsDouble(whole))
				/ interval;
		}

		/** Venom dealt by its first n hits: 6, 8, ... capped at 20. */
		static double venomCumulative(int n)
		{
			int ramp = Math.min(n, 8);
			double total = ramp * 6 + ramp * (ramp - 1);
			return total + Math.max(0, n - 8) * 20.0;
		}

		/**
		 * Poison dealt by its first n hits after the first application. Before hit j a fresh application
		 * landed with chance r in each interval, resetting the severity.
		 */
		double poisonCumulative(int n)
		{
			int s0 = s.getPoisonSeverity();
			while (poisonCumulative.size() <= n)
			{
				int j = poisonCumulative.size();
				// Hit j: severity s0 - m when the last reset was m intervals ago.
				double expected = refreshPrefix + noRefresh * poisonHit(s0 - (j - 1));
				poisonCumulative.add(poisonCumulative.get(j - 1) + expected);
				refreshPrefix += refresh * noRefresh * poisonHit(s0 - (j - 1));
				noRefresh *= 1 - refresh;
			}
			return poisonCumulative.get(n);
		}

		static int poisonHit(int severity)
		{
			return severity > 0 ? (severity + 4) / 5 : 0;
		}
	}
}
