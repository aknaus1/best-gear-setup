package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * The overhead protection prayer to use against a target with a setup. Verified encounter rules decide when
 * switching is possible; a mixed Wiki attack list alone never implies a reaction window.
 * A melee setup stands in the target's melee range; ranged and magic setups are assumed to fight from distance,
 * while melee-only attackers still walk up to players who aren't safespotting.
 */
public final class ProtectionPrayers
{
	private ProtectionPrayers()
	{
	}

	/** Search-time inputs, retained with results rather than read from changing live settings. */
	@Value
	public static class Context
	{
		/** Incoming stats before offensive special-attack drains. */
		Monster monster;
		PlayerLevels levels;
		OptimizerSettings settings;
	}

	/** Overhead protection prayers with their Prayer level requirements (Wiki). */
	public enum Overhead
	{
		MAGIC("Protect from Magic", 37), MISSILES("Protect from Missiles", 40), MELEE("Protect from Melee", 43);

		private final String label;
		private final int level;

		Overhead(String label, int level)
		{
			this.label = label;
			this.level = level;
		}

		public int getLevel()
		{
			return level;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** One recommendation: the prayer to start on (null for none), every prayer the fight switches between, and why. */
	@Value
	public static class Advice
	{
		Overhead prayer;
		/** Every overhead used in the fight in priority order, including {@link #prayer}; empty when none is. */
		List<Overhead> switches;
		String note;
		/** The Wiki data couldn't be read, so no prayer is recommended either way. */
		boolean unknown;
		/** The Prayer level the recommendation needs when none of its overheads is unlocked yet; 0 otherwise. */
		int requiredLevel;
		/** Primary encounter source, when this advice was checked against a strategy rather than inferred. */
		String source;

		public Advice(Overhead prayer, List<Overhead> switches, String note)
		{
			this(prayer, switches, note, false, 0);
		}

		public Advice(Overhead prayer, List<Overhead> switches, String note, boolean unknown)
		{
			this(prayer, switches, note, unknown, 0);
		}

		public Advice(Overhead prayer, List<Overhead> switches, String note, boolean unknown, int requiredLevel)
		{
			this(prayer, switches, note, unknown, requiredLevel, null);
		}

		/** As above, with the encounter strategy page the advice was checked against (null when inferred). */
		public Advice(Overhead prayer, List<Overhead> switches, String note, boolean unknown, int requiredLevel, String source)
		{
			this.prayer = prayer;
			this.switches = switches;
			this.note = note;
			this.unknown = unknown;
			this.requiredLevel = requiredLevel;
			this.source = source;
		}

		Advice withSource(String source)
		{
			return new Advice(prayer, switches, note, unknown, requiredLevel, source);
		}

		/** Whether the player can't use any of the recommended overheads yet. */
		public boolean isLocked()
		{
			return requiredLevel > 0;
		}

		public boolean isSwitching()
		{
			return switches.size() > 1;
		}

		/** Short form for the result view, such as "Protect from Magic" or "Switch: Magic / Missiles". */
		public String summary()
		{
			String locked = isLocked() ? " (needs " + requiredLevel + " Prayer)" : "";
			if (isSwitching())
			{
				return "Switch: " + switches.stream().map(o -> o.toString().replace("Protect from ", ""))
					.collect(Collectors.joining(" / ")) + locked;
			}
			return prayer != null ? prayer + locked : unknown ? "Unknown \u2013 check the Wiki" : "None needed";
		}
	}

	private static final Advice UNKNOWN = new Advice(null, Collections.emptyList(),
		"The Wiki attack data for this target can't be read, so check its Wiki page.", true);

	/** Curated raid advice by name prefix, most specific first. */
	private static final JsonArray RAIDS = EncounterPrayers.load("raid-prayers.json");

	private static Advice advice(String note, Overhead... prayers)
	{
		List<Overhead> switches = new ArrayList<>();
		Collections.addAll(switches, prayers);
		return new Advice(switches.isEmpty() ? null : switches.get(0), Collections.unmodifiableList(switches), note);
	}

	/**
	 * @param prayerLevel the player's Prayer level; overheads above it are left out of the advice
	 */
	public static Advice recommend(Monster monster, CombatClass setup, int prayerLevel)
	{
		boolean meleeRange = setup == CombatClass.MELEE;
		if (monster.getLowerName().startsWith("vorkath"))
		{
			return unlocked(vorkath(null, setup, prayerLevel, null), prayerLevel);
		}
		Advice advice = EncounterPrayers.recommend(monster, setup, meleeRange);
		if (advice == null) { advice = curated(monster, meleeRange); }
		if (advice == null)
		{
			advice = fromAttackStyles(monster, meleeRange, prayerLevel);
		}
		return unlocked(advice, prayerLevel);
	}

	/** Recommendations for the actual gear, levels, position and fire-protection assumptions of this search. */
	public static Advice recommend(Monster monster, SetupResult setup, int prayerLevel, Context context)
	{
		Monster incoming = context == null ? monster : context.getMonster();
		Loadout loadout = setup.getLoadout();
		int distance = context == null ? 0 : context.getSettings().getTargetDistance();
		boolean meleeRange = distance > 0 ? AttackReach.distance(incoming, distance) <= 1
			: setup.getCombatClass() == CombatClass.MELEE;
		if (incoming.getLowerName().startsWith("vorkath"))
		{
			return unlocked(vorkath(loadout, setup.getCombatClass(), prayerLevel, context), prayerLevel);
		}
		Advice curated = EncounterPrayers.equipped(incoming, setup, meleeRange, context);
		if (curated == null) { curated = EncounterPrayers.recommend(incoming, setup.getCombatClass(), meleeRange); }
		if (curated == null) { curated = curated(incoming, meleeRange); }
		if (curated != null) { return unlocked(curated, prayerLevel); }
		Advice fallback = fromAttackStyles(incoming, meleeRange, prayerLevel);
		boolean fireRequired = context != null && DragonfireProtection.required(incoming, context.getSettings());
		if (fallback.getPrayer() == null && (!fireRequired || fallback.isUnknown())) { return fallback; }
		if (fallback.getPrayer() == null && DragonfireProtection.allowed(incoming, loadout,
			context.getSettings().toBuilder().protectMagic(false).build(), context.getLevels()))
		{
			return fallback;
		}
		Map<Overhead, Double> damage = context == null ? Collections.emptyMap()
			: IncomingDamage.byPrayer(incoming, loadout, context.getLevels(), meleeRange);
		List<Overhead> candidates = new ArrayList<>(damage.keySet());
		candidates.sort((a, b) -> Double.compare(damage.get(b), damage.get(a)) == 0
			? Integer.compare(tieRank(a), tieRank(b)) : Double.compare(damage.get(b), damage.get(a)));
		if (candidates.isEmpty())
		{
			if (fallback.getPrayer() != null) { candidates.add(fallback.getPrayer()); }
		}
		// A fire requirement can make Magic mandatory even though ordinary dragonfire isn't a magic attack.
		if (fireRequired)
		{
			candidates.removeIf(prayer -> !DragonfireProtection.allowed(incoming, loadout,
				context.getSettings().toBuilder().protectMagic(prayer == Overhead.MAGIC).build(), context.getLevels()));
			if (DragonfireProtection.allowed(incoming, loadout,
				context.getSettings().toBuilder().protectMagic(true).build(), context.getLevels())
				&& !candidates.contains(Overhead.MAGIC))
			{
				candidates.add(Overhead.MAGIC);
			}
			if (candidates.isEmpty())
			{
				return new Advice(null, Collections.emptyList(), "This setup does not meet the selected fire protection "
					+ "requirements with any overhead. Check the shield and antifire potion.", true);
			}
		}
		Overhead chosen = candidates.stream().filter(p -> p.getLevel() <= prayerLevel).findFirst().orElse(candidates.get(0));
		String note = damage.isEmpty() ? chosen == fallback.getPrayer() ? fallback.getNote()
			: "Keep " + chosen + " active before the attack to maintain this setup's fire protection."
			: "Keep " + chosen + " active before the attack. It covers the greatest estimated incoming damage among "
				+ "usable prayers compatible with your fire protection, for this "
				+ "armour and your base Defence / Magic levels. Attack frequencies use the snapshot's weights (equal "
				+ "when unspecified); missing melee / ranged accuracy bonuses are estimated as zero. Defensive boosts "
				+ "and special damage-reduction effects are not modelled. No reactive switch window is verified.";
		if (fireRequired && chosen == Overhead.MAGIC)
		{
			note += " Keep Magic active to maintain this setup's fire protection where the shield and potion need it.";
		}
		if (chosen != candidates.get(0))
		{
			note += " " + candidates.get(0) + " needs " + candidates.get(0).getLevel() + " Prayer; using an unlocked alternative.";
		}
		return unlocked(advice(note, chosen), prayerLevel);
	}

	private static Advice vorkath(Loadout loadout, CombatClass setup, int prayerLevel, Context context)
	{
		GearItem weapon = loadout == null ? null : loadout.getWeapon();
		GearItem shield = weapon == null || weapon.isTwoHanded() ? null : loadout.get(Slot.SHIELD);
		boolean fireShield = DragonfireProtection.isFireShield(shield);
		boolean missiles = setup == CombatClass.RANGED && fireShield;
		boolean lowPrayer = missiles && prayerLevel >= Overhead.MAGIC.getLevel() && prayerLevel < Overhead.MISSILES.getLevel();
		Overhead prayer = missiles && !lowPrayer ? Overhead.MISSILES : Overhead.MAGIC;
		String note = "Keep " + prayer + " active before Vorkath attacks; do not reactively switch between his "
			+ "ordinary projectiles. ";
		if (setup == CombatClass.MELEE)
		{
			note += "Melee setups use Magic and rely on armour's ranged defence. Stay in melee range between specials; "
				+ "stepping back increases the frequency of ranged attacks. ";
		}
		else if (lowPrayer)
		{
			note += "Missiles needs 40 Prayer; use the unlocked Magic overhead until then. ";
		}
		else if (fireShield && setup == CombatClass.RANGED)
		{
			note += "The equipped fire shield plus antifire lets you block ranged attacks. ";
		}
		else
		{
			note += "Without a ranged setup's fire shield, Magic also supplies part of your dragonfire protection. ";
		}
		note += "Use super antifire; regular antifire with a fire shield leaves more chip damage. Re-enable the "
			+ "overhead after the purple breath disables it, and dodge the fireball and acid specials.";
		if (context != null && !DragonfireProtection.allowed(context.getMonster(), loadout,
			context.getSettings().toBuilder().requireFireProtection(true).protectMagic(prayer == Overhead.MAGIC).build(),
			context.getLevels()))
		{
			note += " This shield / potion combination does not provide sufficient fire protection; change it before fighting.";
		}
		return advice(note, prayer).withSource("https://oldschool.runescape.wiki/w/Vorkath/Strategies");
	}

	/** Curated raid advice, or null when the target has none (falling back to its Wiki attack styles). */
	static Advice curated(Monster monster, boolean meleeRange)
	{
		return EncounterPrayers.match(RAIDS, monster.getLowerName(), null, meleeRange);
	}

	/**
	 * The Wiki attack styles: every protectable style, highest max hit first. Typeless, dragonfire, poison and
	 * fully prayer-penetrating attacks are left out, and "all" means all three styles. From distance, melee attacks
	 * are dropped unless the monster only has melee, since it walks up to you unless you safespot it. Data that
	 * can't be read (e.g. "none" with a max hit, or "various") gives no recommendation rather than "None needed".
	 */
	static Advice fromAttackStyles(Monster monster, boolean meleeRange)
	{
		return fromAttackStyles(monster, meleeRange, 99);
	}

	private static Advice fromAttackStyles(Monster monster, boolean meleeRange, int prayerLevel)
	{
		Map<String, Monster.MonsterAttack> attacks = monster.getAttackStyles();
		if (attacks == null || attacks.isEmpty())
		{
			return UNKNOWN;
		}
		Map<Overhead, Integer> maxHits = new EnumMap<>(Overhead.class);
		boolean unknown = false;
		boolean unprotectable = false;
		for (Map.Entry<String, Monster.MonsterAttack> entry : attacks.entrySet())
		{
			Monster.MonsterAttack attack = entry.getValue();
			if (attack == null)
			{
				unknown = true;
				continue;
			}
			String style = entry.getKey().toLowerCase(Locale.ROOT).trim();
			if (style.equals("none") || style.equals("n/a"))
			{
				// No attack, unless the Wiki still gives it a max hit.
				unknown |= attack.getMaxHit() > 0;
				continue;
			}
			if (attack.isTypeless())
			{
				unprotectable = true;
				continue;
			}
			// Several attacks can share one entry, e.g. "ranged <br/> typeless".
			for (String part : style.split("<br\\s*/?>"))
			{
				Set<Overhead> blocked = protectedBy(part);
				if (blocked.isEmpty())
				{
					boolean known = unprotectable(part);
					unprotectable |= known;
					unknown |= !known;
				}
				for (Overhead overhead : blocked)
				{
					maxHits.merge(overhead, attack.getMaxHit(), Math::max);
				}
			}
		}
		if (maxHits.isEmpty())
		{
			return unknown ? UNKNOWN : new Advice(null, Collections.emptyList(), unprotectable
				? "Its attacks aren't blocked by protection prayers." : "It doesn't attack.");
		}
		String partial = unknown ? " Some of its Wiki attack data can't be read." : "";
		if (!meleeRange && maxHits.keySet().equals(EnumSet.of(Overhead.MELEE)))
		{
			return new Advice(Overhead.MELEE, Collections.singletonList(Overhead.MELEE), "Melee only: it walks up to "
				+ "you, so pray Melee; none is needed if you safespot it or keep out of its reach." + partial);
		}
		if (!meleeRange)
		{
			maxHits.remove(Overhead.MELEE);
		}
		// Highest max hit first; ties favour ranged, then magic (melee setups can usually step out of melee).
		List<Overhead> order = new ArrayList<>(maxHits.keySet());
		order.sort((a, b) -> maxHits.get(b).equals(maxHits.get(a))
			? Integer.compare(tieRank(a), tieRank(b)) : Integer.compare(maxHits.get(b), maxHits.get(a)));
		String styles = order.stream().map(o -> o.toString().replace("Protect from ", "").toLowerCase(Locale.ROOT)
			+ (maxHits.get(o) > 0 ? " (max " + maxHits.get(o) + ")" : "")).collect(Collectors.joining(", "));
		Overhead chosen = order.stream().filter(o -> o.getLevel() <= prayerLevel).findFirst().orElse(order.get(0));
		String note = order.size() == 1 ? "Wiki attack style: " + styles + ". Keep the prayer active before it attacks."
			: "Wiki attack styles: " + styles + ". Keep " + chosen + " active before it attacks. "
				+ "This fallback covers the highest max hit; attack frequency and accuracy are unknown. "
				+ "A mixed attack list does not establish a reactive switch window.";
		if (chosen != order.get(0))
		{
			note += " " + order.get(0) + " needs " + order.get(0).getLevel() + " Prayer; using the highest unlocked alternative.";
		}
		return advice(note + partial, chosen);
	}

	private static int tieRank(Overhead overhead)
	{
		return overhead == Overhead.MISSILES ? 0 : overhead == Overhead.MAGIC ? 1 : 2;
	}

	/** Attack styles no overhead blocks: typeless, dragonfire and breath, poison, bleed, curses and prayer-piercing. */
	static boolean unprotectable(String style)
	{
		String s = style.toLowerCase(Locale.ROOT);
		return s.contains("typeless") || s.contains("100% prayer penetration") || s.contains("dragonfire")
			|| s.contains("breath") || s.equals("poison") || s.equals("bleed") || s.equals("curse");
	}

	/**
	 * The overheads that block an attack, from the Wiki's attack style names (e.g. "magical ranged", "crush"); empty
	 * for attacks no overhead blocks and for names that can't be read. "All" is all three styles.
	 */
	static Set<Overhead> protectedBy(String style)
	{
		Set<Overhead> out = EnumSet.noneOf(Overhead.class);
		String s = style.toLowerCase(Locale.ROOT).trim();
		if (unprotectable(s))
		{
			return out;
		}
		if (s.equals("all"))
		{
			return EnumSet.allOf(Overhead.class);
		}
		// "Magical melee" and "magical ranged" are protected by the prayer for how they are delivered.
		if (s.contains("magical melee"))
		{
			out.add(Overhead.MELEE);
			return out;
		}
		if (s.contains("magical ranged"))
		{
			out.add(Overhead.MISSILES);
			return out;
		}
		if (s.contains("ranged magic"))
		{
			out.add(Overhead.MAGIC);
			return out;
		}
		if (s.contains("ranged melee"))
		{
			out.add(Overhead.MELEE);
			return out;
		}
		if (s.contains("melee") || s.contains("stab") || s.contains("slash") || s.contains("crush"))
		{
			out.add(Overhead.MELEE);
		}
		if (s.contains("ranged"))
		{
			out.add(Overhead.MISSILES);
		}
		if (s.contains("magic"))
		{
			out.add(Overhead.MAGIC);
		}
		return out;
	}

	/** Drop overheads the player's Prayer level can't use and say so. */
	private static Advice unlocked(Advice advice, int prayerLevel)
	{
		List<Overhead> usable = advice.getSwitches().stream().filter(o -> o.getLevel() <= prayerLevel)
			.collect(Collectors.toList());
		if (usable.size() == advice.getSwitches().size())
		{
			return advice;
		}
		List<Overhead> missing = new ArrayList<>(advice.getSwitches());
		missing.removeAll(usable);
		String needs = missing.stream().map(o -> o + " needs " + o.getLevel() + " Prayer")
			.collect(Collectors.joining(", ")) + " (you have " + prayerLevel + ").";
		String note = advice.getNote() == null ? needs : advice.getNote() + " " + needs;
		if (usable.isEmpty())
		{
			// Still name what the fight calls for, rather than suggesting nothing is needed.
			int level = advice.getSwitches().stream().mapToInt(Overhead::getLevel).min().orElse(0);
			return new Advice(advice.getPrayer(), advice.getSwitches(), note, false, level, advice.getSource());
		}
		return new Advice(usable.get(0), Collections.unmodifiableList(usable), note, false, 0, advice.getSource());
	}
}
