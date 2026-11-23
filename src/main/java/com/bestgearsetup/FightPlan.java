package com.bestgearsetup;

import com.bestgearsetup.calc.Outfit;
import com.bestgearsetup.calc.SpecialAttacks;
import com.bestgearsetup.data.*;
import com.google.gson.*;
import java.util.*;
import java.util.function.*;
import lombok.*;

/**
 * Whole-fight plans for bosses with several phases or targets (boss-fights.json): one worn setup and the inventory
 * switches that minimise the summed time to deal each phase's damage, with the special attacks one fight's energy
 * pays for. A static pre-fight plan.
 */
public final class FightPlan
{
	/** Seconds standing for "nothing damages this phase"; finite so the greedy can still rank the fixes. */
	private static final double NEVER = 1e9;
	private static final Map<SpecialAttacks, Double> FRESH = Collections.singletonMap(SpecialAttacks.NONE, 1.0);
	/** Worn setups tried, the fastest alone over the whole fight, so long fights stay bounded. */
	private static final int WORN_TRIED = 40;
	/** Worn setups, the fastest without specials, that specials are planned for. */
	private static final int SPEC_WORN = 2;

	private FightPlan()
	{
	}

	/** One phase or target of a fight; work = count × share, the times its hitpoints (or hp) are dealt per kill. */
	@Value
	public static class Phase
	{
		String label;
		String target;
		double work;
		EncounterPhase phase;
		/** Combat classes that may attack it; empty for any. */
		Set<Combat> styles;
		/**
		 * Its health bar: phases with the same one are one target, so drains carry over. The catalog's, else the fight for
		 * a share below 1, null for several targets (count above 1), which no drain is planned for, else its own.
		 */
		String bar;
		/** Damage per count in place of the target's hitpoints: base, per player, from, cap; null for its hitpoints. */
		double[] hp;
		/** The party shares the work. */
		boolean split;
		/** Times the target is fought per kill. */
		double count;

		/** Your damage to deal in this phase per kill, for this party size. */
		public double work(int party, double hitpoints)
		{
			int players = Math.max(1, party);
			double each = hp == null ? hitpoints : Math.min(hp[3], hp[0] + hp[1] * Math.max(0, players - hp[2]));
			return work * each / (split ? players : 1);
		}

		/** A share below 1: its health bar is shared with other phases, so pre-fight drains carry over. */
		public boolean isShared()
		{
			return work < count;
		}

		public boolean allows(Combat style)
		{
			return styles.isEmpty() || styles.contains(style);
		}
	}

	/** A planned fight: the setup to wear, the switch items to carry and the setup used in each phase. */
	@Value
	public static class Plan
	{
		Outfit worn;
		/** Switch items, in the order they were chosen. */
		List<Gear> carried;
		/** Inventory slots used: carried items plus room for items taken off. */
		int slots;
		/** Per phase: the setup used, the worn one when it needs no switch. */
		List<Outfit> used;
		/** Per phase: seconds to deal its work, infinite when nothing damages it. */
		double[] seconds;
		/** Special attacks used, by weapon and phase. */
		List<Spec> specs;
		/** Percent of special attack energy the fight starts with. */
		@With
		double energy;

		/** Seconds over the phases something damages. */
		public double getTotal()
		{
			return Arrays.stream(seconds).filter(Double::isFinite).sum();
		}

		/** Every setup the fight uses: each phase's, then each special attack's. */
		public List<Outfit> outfits()
		{
			List<Outfit> outfits = new ArrayList<>(used);
			specs.forEach(spec -> outfits.add(spec.held));
			return outfits;
		}

		/** The items switched in for this phase. */
		public List<Gear> switches(int phase)
		{
			return changed(worn, used.get(phase));
		}
	}

	/** A special attack used in a phase: the worn setup holding the spec weapon, and how often. */
	@Value
	public static class Spec
	{
		Outfit held;
		int phase;
		int uses;
		/** Its stat drain is counted: on this phase and the later ones on the same health bar. */
		boolean drain;
	}

	/** The fight's damage numbers, each called once per distinct input. */
	public interface Fight
	{
		/** A setup's DPS in a phase after these drains landed; 0 when it may not attack it. */
		double dps(Outfit outfit, int phase, SpecialAttacks drains);

		/** One special attack: its seconds, expected damage and accuracy; zeros when it can't be used on the phase. */
		double[] spec(Outfit held, int phase, SpecialAttacks drains);

		/** The spec weapons to consider, each held with the worn setup's armour. */
		List<Outfit> specs(Outfit worn);

		/** Percent of energy one special attack costs. */
		int energy(Outfit held);

		/** The drains after one more landed special that dealt this damage. */
		SpecialAttacks landed(Outfit held, SpecialAttacks drains, double damage);

		/** Percent of energy regenerated per second in this setup. */
		double regen(Outfit outfit);
	}

	/** A setup reachable from the worn one: the items it takes from the inventory, the free slots it needs and its seconds. */
	@Value
	private static class Option
	{
		Outfit outfit;
		List<Gear> in;
		int room;
		double[] seconds;
	}

	/** Read on first use, so planning never needs the catalog. */
	private static final class Catalog
	{
		static final Map<String, List<Phase>> PHASES = new HashMap<>();
		static final Map<String, String> FIGHTS = new HashMap<>();

		static
		{
			for (Map.Entry<String, JsonElement> fight : Names.load("/com/bestgearsetup/boss-fights.json").entrySet())
			{
				if (fight.getKey().startsWith("_"))
				{
					continue;
				}
				List<Phase> phases = new ArrayList<>();
				for (JsonElement element : fight.getValue().getAsJsonObject().getAsJsonArray("phases"))
				{
					JsonObject row = element.getAsJsonObject();
					String target = row.get("target").getAsString();
					Set<Combat> styles = EnumSet.noneOf(Combat.class);
					if (row.has("styles"))
					{
						row.getAsJsonArray("styles").forEach(style -> styles.add(Combat.valueOf(style.getAsString().toUpperCase(Locale.ROOT))));
					}
					phases.add(new Phase(row.has("label") ? row.get("label").getAsString() : GameData.titleCase(target),
						target, fraction(row.get("count")) * fraction(row.get("share")),
						row.has("phase") ? EncounterPhase.valueOf(row.get("phase").getAsString()) : EncounterPhase.STANDARD, styles,
						row.has("bar") ? row.get("bar").getAsString() : fraction(row.get("share")) < 1 ? fight.getKey()
							: fraction(row.get("count")) > 1 ? null : target + phases.size(),
						hp(row.get("hp")), row.has("split") && row.get("split").getAsBoolean(), fraction(row.get("count"))));
					String other = FIGHTS.put(target, fight.getKey());
					if (other != null && !other.equals(fight.getKey()))
					{
						throw new IllegalStateException(target + " is in both " + other + " and " + fight.getKey());
					}
				}
				PHASES.put(fight.getKey(), Collections.unmodifiableList(phases));
			}
		}
	}

	/** The fight with this monster as a phase target, or null. */
	public static String fight(String monster)
	{
		return monster == null ? null : Catalog.FIGHTS.get(monster.toLowerCase(Locale.ROOT));
	}

	/** Whether the fight's work depends on the party size. */
	public static boolean partySized(String fight)
	{
		return phases(fight).stream().anyMatch(phase -> phase.split || phase.hp != null && phase.hp[1] > 0);
	}

	/** A number, or a team formula {base, per, from, cap}; null when absent. */
	private static double[] hp(JsonElement value)
	{
		if (value == null || value.isJsonPrimitive())
		{
			return value == null ? null : new double[]{value.getAsDouble(), 0, 1, Double.POSITIVE_INFINITY};
		}
		JsonObject formula = value.getAsJsonObject();
		return new double[]{formula.get("base").getAsDouble(), formula.has("per") ? formula.get("per").getAsDouble() : 0,
			formula.has("from") ? formula.get("from").getAsDouble() : 1,
			formula.has("cap") ? formula.get("cap").getAsDouble() : Double.POSITIVE_INFINITY};
	}

	/** The fight's phases in order; empty for an unknown fight. */
	public static List<Phase> phases(String fight)
	{
		return Catalog.PHASES.getOrDefault(fight, Collections.emptyList());
	}

	/** A number or "a/b"; 1 when absent. */
	static double fraction(JsonElement value)
	{
		if (value == null)
		{
			return 1;
		}
		String[] parts = value.getAsString().split("/");
		return Double.parseDouble(parts[0]) / (parts.length > 1 ? Double.parseDouble(parts[1]) : 1);
	}

	/**
	 * The fastest plan from an allowed candidate worn setup, damaging the most phases first; null if none is allowed,
	 * nothing damages any phase or it was cancelled. From each, greedily carry the switch (all of another candidate,
	 * or only its weapon) saving the most seconds per inventory slot, and again the most seconds, until none fits or
	 * helps. Ties keep the faster worn setup, after fewer slots. The fastest of those then carry up to two spec
	 * weapons, each in the slots it needs and only when it saves a second: uses are added where they save the most
	 * while the energy lasts, walking the phases in their listed order.
	 *
	 * @param work hitpoints to deal in each phase
	 * @param bar each phase's health bar, see {@link Phase#getBar()}
	 * @param cap the most one special attack can deal in each phase: one target's work
	 * @param fullBar the fight starts with full energy and regenerates more, rather than having only what one fight
	 *     regenerates
	 * @param slots free inventory slots; 0 plans one set of items (attack style or spell changes stay free)
	 * @param locked slots never switched
	 * @param allowed whether these distinct items, worn, carried and loaded, may be taken together
	 */
	public static Plan plan(List<Outfit> candidates, double[] work, String[] bar, double[] cap, Fight fight, boolean fullBar,
		int slots, Set<Slot> locked, Predicate<Collection<Gear>> allowed, BooleanSupplier cancelled)
	{
		Specs specs = new Specs(work, bar, cap, fight, fullBar);
		Map<Outfit, double[]> memo = new HashMap<>();
		Function<Outfit, double[]> time = outfit -> memo.computeIfAbsent(outfit,
			o -> specs.seconds(Collections.nCopies(work.length, o), Collections.emptyList()));
		List<Outfit> pool = new ArrayList<>(new LinkedHashSet<>(candidates));
		List<Outfit> worn = new ArrayList<>(pool);
		worn.sort(Comparator.comparingDouble(outfit -> Arrays.stream(time.apply(outfit)).sum()));
		List<Plan> plans = new ArrayList<>();
		for (Outfit outfit : worn.subList(0, Math.min(WORN_TRIED, worn.size())))
		{
			if (cancelled.getAsBoolean())
			{
				return null;
			}
			if (allowed.test(items(outfit)))
			{
				plans.add(faster(planFrom(outfit, pool, time, slots, locked, allowed, true, cancelled),
					planFrom(outfit, pool, time, slots, locked, allowed, false, cancelled)));
			}
		}
		plans.sort(Comparator.comparingDouble(Plan::getTotal).thenComparingInt(Plan::getSlots));
		Plan best = null;
		for (Plan plain : plans.subList(0, Math.min(SPEC_WORN, plans.size())))
		{
			// Only the spec weapons that help the plan as it is.
			List<Outfit> weapons = new ArrayList<>(new LinkedHashSet<>(fight.specs(plain.worn)));
			weapons.removeIf(held -> specs.allocate(plain, Collections.singletonList(held)).isEmpty());
			Plan current = plain.withEnergy(specs.energy(plain));
			List<Outfit> chosen = new ArrayList<>();
			for (int round = 0; round < 2; round++)
			{
				Plan found = null;
				Outfit add = null;
				for (Outfit weapon : weapons)
				{
					List<Outfit> held = new ArrayList<>(chosen);
					held.add(weapon);
					Set<Gear> extra = new LinkedHashSet<>();
					Set<Gear> taken = new LinkedHashSet<>();
					int room = 0;
					for (Outfit outfit : held)
					{
						extra.addAll(changed(plain.worn, outfit));
						taken.addAll(items(outfit));
						room = Math.max(room, changed(outfit, plain.worn).size() - changed(plain.worn, outfit).size());
					}
					Predicate<Collection<Gear>> both = items ->
					{
						Set<Gear> all = new LinkedHashSet<>(items);
						all.addAll(taken);
						return allowed.test(all);
					};
					int left = slots - extra.size() - room;
					if (chosen.contains(weapon) || left < 0 || !both.test(items(plain.worn)) || cancelled.getAsBoolean())
					{
						continue;
					}
					Plan gear = faster(planFrom(plain.worn, pool, time, left, locked, both, true, cancelled),
						planFrom(plain.worn, pool, time, left, locked, both, false, cancelled));
					List<Spec> uses = specs.allocate(gear, held);
					extra.addAll(gear.carried);
					Plan plan = new Plan(gear.worn, new ArrayList<>(extra), gear.slots - gear.carried.size() + extra.size() + room,
						gear.used, specs.seconds(gear.used, uses), uses, specs.energy(gear));
					if (!uses.isEmpty() && (found == null || faster(found, plan) == plan))
					{
						found = plan;
						add = weapon;
					}
				}
				if (found == null || found.getTotal() > current.getTotal() - 1)
				{
					break;
				}
				current = found;
				chosen.add(add);
			}
			best = best == null ? current : faster(best, current);
		}
		if (best == null || cancelled.getAsBoolean() || Arrays.stream(best.seconds).allMatch(seconds -> real(seconds) == 0))
		{
			return null;
		}
		for (int i = 0; i < work.length; i++)
		{
			best.seconds[i] = real(best.seconds[i]) > 0 ? best.seconds[i] : Double.POSITIVE_INFINITY;
		}
		return best;
	}

	/** The plan with the lower total, the first on ties unless the other needs fewer slots. */
	private static Plan faster(Plan first, Plan other)
	{
		return other.getTotal() < first.getTotal() || other.getTotal() == first.getTotal() && other.slots < first.slots
			? other : first;
	}

	/** The seconds, or 0 for the placeholder of a phase nothing damages. */
	private static double real(double seconds)
	{
		return seconds < NEVER / 2 ? seconds : 0;
	}

	/** Phase times with special attacks, on memoised damage numbers. */
	@RequiredArgsConstructor
	private static final class Specs
	{
		private final double[] work;
		private final String[] bar;
		private final double[] cap;
		private final Fight fight;
		private final boolean fullBar;
		private final Map<List<Object>, Double> rates = new HashMap<>();
		private final Map<List<Object>, double[]> thrown = new HashMap<>();

		/**
		 * Seconds per phase. Each health bar keeps the chance of every set of landed drains: a special attack splits each
		 * by its accuracy, and the rest of the phase's work is dealt at the DPS against each.
		 */
		double[] seconds(List<Outfit> used, List<Spec> specs)
		{
			double[] seconds = new double[work.length];
			Map<String, Map<SpecialAttacks, Double>> bars = new HashMap<>();
			for (int i = 0; i < work.length; i++)
			{
				int phase = i;
				String key = bar[i] == null ? "" + i : bar[i];
				double damage = 0;
				for (Spec spec : specs)
				{
					for (int use = 0; use < (spec.phase == i ? spec.uses : 0); use++)
					{
						Map<SpecialAttacks, Double> next = new LinkedHashMap<>();
						for (Map.Entry<SpecialAttacks, Double> state : bars.getOrDefault(key, FRESH).entrySet())
						{
							double[] hit = thrown.computeIfAbsent(Arrays.asList(spec.held, i, state.getKey()),
								k -> fight.spec(spec.held, phase, state.getKey()));
							double chance = state.getValue();
							seconds[i] += chance * hit[0];
							damage += chance * Math.min(hit[1], cap[i]);
							next.merge(spec.drain && hit[2] > 0 ? fight.landed(spec.held, state.getKey(), hit[1] / hit[2])
								: state.getKey(), chance * hit[2], Double::sum);
							next.merge(state.getKey(), chance * (1 - hit[2]), Double::sum);
						}
						bars.put(key, next);
					}
				}
				double left = work[i] - damage;
				for (Map.Entry<SpecialAttacks, Double> state : bars.getOrDefault(key, FRESH).entrySet())
				{
					double rate = rates.computeIfAbsent(Arrays.asList(used.get(i), i, state.getKey()),
						k -> fight.dps(used.get(phase), phase, state.getKey()));
					seconds[i] += left <= 0 ? 0 : state.getValue() * (rate > 0 ? Math.min(NEVER, left / rate) : NEVER);
				}
			}
			return seconds;
		}

		/** Percent of energy the plan starts with: a full bar, or only what regenerates over its phases' setups. */
		double energy(Plan plan)
		{
			double energy = fullBar ? 100 : 0;
			for (int i = 0; i < work.length; i++)
			{
				energy += fight.regen(plan.used.get(i)) * real(plan.seconds[i]);
			}
			return Math.min(100, energy);
		}

		/**
		 * The uses of these spec weapons that save the plan the most seconds, added one at a time while one helps and the
		 * energy lasts: each phase's uses are paid on entering it and, from a full bar, its seconds with those specials
		 * then regenerate energy up to a full bar.
		 */
		List<Spec> allocate(Plan plan, List<Outfit> held)
		{
			int[][] uses = new int[held.size()][work.length];
			List<Spec> specs = Collections.emptyList();
			double total = plan.getTotal();
			while (true)
			{
				int[] pick = null;
				List<Spec> picked = null;
				for (int weapon = 0; weapon < held.size(); weapon++)
				{
					for (int i = 0; i < work.length; i++)
					{
						uses[weapon][i]++;
						List<Spec> tried = new ArrayList<>();
						for (int phase = 0; phase < work.length; phase++)
						{
							for (int w = 0; w < held.size(); w++)
							{
								if (uses[w][phase] > 0)
								{
									tried.add(new Spec(held.get(w), phase, uses[w][phase], bar[phase] != null
										&& fight.landed(held.get(w), SpecialAttacks.NONE, 1).isAny()));
								}
							}
						}
						double[] seconds = seconds(plan.used, tried);
						double left = energy(plan);
						for (int phase = 0; left >= 0 && phase < work.length; phase++)
						{
							for (int w = 0; w < held.size(); w++)
							{
								left -= uses[w][phase] * fight.energy(held.get(w));
							}
							if (left >= 0 && fullBar)
							{
								left = Math.min(100, left + fight.regen(plan.used.get(phase)) * real(seconds[phase]));
							}
						}
						uses[weapon][i]--;
						double sum = Arrays.stream(seconds).sum();
						if (left >= 0 && sum < total - 1e-9)
						{
							total = sum;
							pick = new int[]{weapon, i};
							picked = tried;
						}
					}
				}
				if (pick == null)
				{
					return specs;
				}
				uses[pick[0]][pick[1]]++;
				specs = picked;
			}
		}
	}

	private static Plan planFrom(Outfit worn, List<Outfit> pool, Function<Outfit, double[]> time, int slots,
		Set<Slot> locked, Predicate<Collection<Gear>> allowed, boolean perSlot, BooleanSupplier cancelled)
	{
		List<Option> options = new ArrayList<>();
		for (Outfit to : pool)
		{
			for (boolean weaponOnly : new boolean[]{false, true})
			{
				Outfit outfit = switchTo(worn, to, weaponOnly, locked);
				if (outfit != null)
				{
					List<Gear> in = changed(worn, outfit);
					options.add(new Option(outfit, in, Math.max(0, changed(outfit, worn).size() - in.size()),
						time.apply(outfit)));
				}
			}
		}
		double[] now = time.apply(worn).clone();
		Outfit[] used = new Outfit[now.length];
		Arrays.fill(used, worn);
		Set<Gear> carried = new LinkedHashSet<>();
		Set<Gear> taken = new LinkedHashSet<>(items(worn));
		int room = 0;
		// One spellbook per plan: the worn spell's, else the first switch's.
		Spell cast = worn.getSpell();
		while (true)
		{
			Option pick = null;
			double bestScore = 0;
			for (Option option : options)
			{
				List<Gear> added = new ArrayList<>(option.in);
				added.removeAll(carried);
				int cost = added.size() + Math.max(0, option.room - room);
				double gain = 0;
				for (int i = 0; i < now.length; i++)
				{
					gain += Math.max(0, now[i] - option.seconds[i]);
				}
				double score = perSlot ? gain / Math.max(1, cost) : gain;
				Spell spell = option.outfit.getSpell();
				if (carried.size() + room + cost <= slots && score > bestScore && (cast == null || spell == null
					|| Objects.equals(cast.getSpellbook(), spell.getSpellbook())))
				{
					Set<Gear> all = new LinkedHashSet<>(added);
					all.addAll(taken);
					all.addAll(items(option.outfit));
					if (allowed.test(all))
					{
						pick = option;
						bestScore = score;
					}
				}
			}
			if (pick == null || cancelled.getAsBoolean())
			{
				return new Plan(worn, new ArrayList<>(carried), carried.size() + room, Arrays.asList(used), now,
					Collections.emptyList(), 100);
			}
			carried.addAll(pick.in);
			for (int i = 0; i < now.length; i++)
			{
				if (pick.seconds[i] < now[i])
				{
					now[i] = pick.seconds[i];
					used[i] = pick.outfit;
				}
			}
			// Carry only what a phase uses: a later switch can replace an earlier one and free its slots.
			Set<Gear> kept = new HashSet<>();
			taken = new LinkedHashSet<>(items(worn));
			room = 0;
			cast = worn.getSpell();
			for (Outfit outfit : used)
			{
				cast = cast == null ? outfit.getSpell() : cast;
				List<Gear> in = changed(worn, outfit);
				kept.addAll(in);
				taken.addAll(items(outfit));
				room = Math.max(room, changed(outfit, worn).size() - in.size());
			}
			carried.retainAll(kept);
		}
	}

	/**
	 * Worn with to's items switched in, all or only the weapon, shield and ammo, with to's style when its weapon is
	 * held. Locked slots keep the worn item; null when a two-hander would have to remove a locked shield.
	 */
	static Outfit switchTo(Outfit worn, Outfit to, boolean weaponOnly, Set<Slot> locked)
	{
		Outfit outfit = worn.copy();
		for (Slot slot : Slot.values())
		{
			if (to.get(slot) != null && !locked.contains(slot)
				&& (!weaponOnly || slot == Slot.WEAPON || slot == Slot.SHIELD || slot == Slot.AMMO))
			{
				outfit.set(slot, to.get(slot));
			}
		}
		if (outfit.getWeapon() != null && outfit.getWeapon().isTwoHanded())
		{
			if (locked.contains(Slot.SHIELD) && outfit.get(Slot.SHIELD) != null)
			{
				return null;
			}
			outfit.set(Slot.SHIELD, null);
		}
		if (Objects.equals(outfit.getWeapon(), to.getWeapon()))
		{
			outfit.setStyle(to.getStyle());
			outfit.setSpell(to.getSpell());
			outfit.setLoadedAmmo(to.getLoadedAmmo());
		}
		return outfit;
	}

	/** The outfit's items and its loaded ammunition, which the caller's limits count too. */
	private static List<Gear> items(Outfit outfit)
	{
		List<Gear> items = changed(new Outfit(), outfit);
		if (outfit.getLoadedAmmo() != null)
		{
			items.add(outfit.getLoadedAmmo());
		}
		return items;
	}

	/** Items of to that from doesn't wear in the same slot; from an empty outfit, all of to's items. */
	static List<Gear> changed(Outfit from, Outfit to)
	{
		List<Gear> items = new ArrayList<>();
		for (Slot slot : Slot.values())
		{
			if (to.get(slot) != null && !to.get(slot).equals(from.get(slot)))
			{
				items.add(to.get(slot));
			}
		}
		return items;
	}
}
