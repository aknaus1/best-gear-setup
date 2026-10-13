package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * The overhead protection prayer to use against a target with a setup. Raid bosses use curated advice, since
 * their Wiki attack lists merge phases and forms; other monsters use the Wiki's attack styles and max hits.
 * A melee setup stands in the target's melee range; ranged and magic setups are assumed to fight from distance,
 * where melee-only attackers have nothing to pray against.
 */
public final class ProtectionPrayers
{
	private ProtectionPrayers()
	{
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

		public Advice(Overhead prayer, List<Overhead> switches, String note)
		{
			this(prayer, switches, note, false);
		}

		public Advice(Overhead prayer, List<Overhead> switches, String note, boolean unknown)
		{
			this.prayer = prayer;
			this.switches = switches;
			this.note = note;
			this.unknown = unknown;
		}

		public boolean isSwitching()
		{
			return switches.size() > 1;
		}

		/** Short form for the result view, such as "Protect from Magic" or "Switch: Magic / Missiles". */
		public String summary()
		{
			if (isSwitching())
			{
				return "Switch: " + switches.stream().map(o -> o.toString().replace("Protect from ", ""))
					.collect(Collectors.joining(" / "));
			}
			return prayer != null ? prayer.toString() : unknown ? "Unknown \u2013 check the Wiki" : "None needed";
		}
	}

	private static final Advice UNKNOWN = new Advice(null, Collections.emptyList(),
		"The Wiki attack data for this target can't be read, so check its Wiki page.", true);

	/** Curated raid advice by name prefix, most specific first: {melee range, from distance}. */
	private static final Map<String, Advice[]> RAIDS = new LinkedHashMap<>();

	static
	{
		// Chambers of Xeric
		both("tekton", advice("Tekton only attacks with melee.", Overhead.MELEE));
		both("ice demon", advice("The ice demon uses magic and ranged: pray against the incoming attack.",
			Overhead.MAGIC, Overhead.MISSILES));
		both("lizardman shaman (chambers of xeric)", advice("Shamans spit ranged attacks; they also melee adjacent "
			+ "players. Their jump and purple spawns aren't prayable: move away.", Overhead.MISSILES));
		raid("vanguard", advice("Protect from Melee beside the melee vanguard. The ranged and magic vanguards fire at "
			+ "an area: step off the targeted tiles.", Overhead.MELEE), advice("Only the melee vanguard needs a prayer, "
			+ "when you stand beside it. Step off the ranged and magic vanguards' targeted tiles."));
		both("vespula", advice("Vespula's stings are ranged.", Overhead.MISSILES));
		both("abyssal portal", advice("Vespula keeps stinging with ranged while you attack the portal.", Overhead.MISSILES));
		both("guardian (chambers of xeric)", advice("Usually none: step out of the slam. Its slam is melee, so use "
			+ "Protect from Melee only if you tank it."));
		both("vasa nistirio", advice("Vasa alternates magic and ranged rocks; pray against the incoming rock.",
			Overhead.MAGIC, Overhead.MISSILES));
		both("glowing crystal", advice("The crystal doesn't attack."));
		both("skeletal mystic", advice("Mystics cast magic at range and also melee adjacent players; fight them from "
			+ "distance.", Overhead.MAGIC));
		raid("muttadile (small)", advice("The small muttadile bites in melee range.", Overhead.MELEE),
			advice("At distance the small muttadile uses ranged.", Overhead.MISSILES));
		raid("muttadile (large)", advice("The large muttadile bites hard in melee range; step back and it uses ranged "
			+ "and magic.", Overhead.MELEE), advice("At distance the large muttadile uses ranged and weaker magic "
			+ "attacks: pray against each.", Overhead.MISSILES, Overhead.MAGIC));
		both("deathly ranger", advice("Deathly rangers use ranged.", Overhead.MISSILES));
		both("deathly mage", advice("Deathly mages use magic.", Overhead.MAGIC));
		both("great olm", advice("Olm's head alternates magic and ranged auto-attacks: pray against its current style. "
			+ "The hands' attacks aren't prayable.", Overhead.MAGIC, Overhead.MISSILES));
		// Tombs of Amascut
		both("ba-ba", advice("Ba-Ba's attacks are melee; dodge her boulders and falling rocks.", Overhead.MELEE));
		// No curated advice: keeps "akkha" from matching her shadows, which use their Wiki attack styles.
		raid("akkha's shadow", null, null);
		raid("akkha", advice("Akkha cycles melee, ranged and magic attacks: pray against the incoming style.",
			Overhead.MELEE, Overhead.MISSILES, Overhead.MAGIC), advice("Akkha alternates ranged and magic at distance "
			+ "(melee only next to her): pray against the incoming style.", Overhead.MISSILES, Overhead.MAGIC));
		raid("zebak", advice("Zebak can't be tanked in melee range; step back between his ranged and magic rocks.",
			Overhead.MISSILES, Overhead.MAGIC, Overhead.MELEE), advice("Zebak alternates ranged and magic rocks: pray "
			+ "against each. He bites players next to him.", Overhead.MISSILES, Overhead.MAGIC));
		both("obelisk (tombs of amascut)", advice("The Wardens alternate magic and ranged attacks: pray against each "
			+ "projectile.", Overhead.MAGIC, Overhead.MISSILES));
		raid("elidinis' warden (core-ejected)", advice("The core doesn't attack."), advice("The core doesn't attack."));
		raid("tumeken's warden (core-ejected)", advice("The core doesn't attack."), advice("The core doesn't attack."));
		both("elidinis' warden", advice("The Warden alternates magic and ranged projectiles: pray against each. Dodge "
			+ "its slam.", Overhead.MAGIC, Overhead.MISSILES));
		both("tumeken's warden", advice("The Warden alternates magic and ranged projectiles: pray against each. Dodge "
			+ "its slam.", Overhead.MAGIC, Overhead.MISSILES));
		// Theatre of Blood
		both("the maiden of sugadinti", advice("Maiden's blood attacks are magic.", Overhead.MAGIC));
		// Only the Maiden room's nylocas: the Verzik room's use their own Wiki data.
		for (String mode : new String[]{"entry", "normal", "hard"})
		{
			both("nylocas matomenos (" + mode + " mode maiden)", advice("The nylocas don't attack; keep Protect from Magic "
				+ "against Maiden.", Overhead.MAGIC));
		}
		both("pestilent bloat", advice("None: step out from under Bloat before it stomps and dodge the falling flesh."));
		both("nylocas vasilias (melee)", advice("Vasilias attacks with its current form's style and changes form every "
			+ "few attacks.", Overhead.MELEE));
		both("nylocas vasilias (ranged)", advice("Vasilias attacks with its current form's style and changes form every "
			+ "few attacks.", Overhead.MISSILES));
		both("nylocas vasilias (magic)", advice("Vasilias attacks with its current form's style and changes form every "
			+ "few attacks.", Overhead.MAGIC));
		raid("sotetseg", advice("Tanking in melee range: Protect from Melee against its melee, switching to Magic or "
			+ "Missiles for its magic and ranged orbs.", Overhead.MELEE, Overhead.MAGIC, Overhead.MISSILES),
			advice("Sotetseg alternates magic and ranged orbs: pray against each.", Overhead.MAGIC, Overhead.MISSILES));
		both("xarpus", advice("None needed: Xarpus's spit is typeless, so no overhead reduces it (Protect from Missiles "
			+ "is only habit). Avoid the acid pools."));
		both("verzik vitur (phase 1)", advice("Hide behind a pillar; her P1 attack is magic.", Overhead.MAGIC));
		both("verzik vitur (phase 2)", advice("Verzik P2 alternates magic and ranged attacks; step off the ranged "
			+ "bomb tiles and don't stand under her.", Overhead.MAGIC, Overhead.MISSILES));
		raid("verzik vitur (phase 3)", advice("Pray against her magic and ranged attacks. Her melee is typeless and no "
			+ "prayer blocks it: stay out of her melee range unless you are tanking. Every fifth attack is a special.",
			Overhead.MAGIC, Overhead.MISSILES),
			advice("Verzik P3 rotates magic and ranged at distance; every fifth attack is a special.",
				Overhead.MAGIC, Overhead.MISSILES));
	}

	private static void both(String prefix, Advice advice)
	{
		raid(prefix, advice, advice);
	}

	private static void raid(String prefix, Advice meleeRange, Advice distance)
	{
		RAIDS.put(prefix, new Advice[]{meleeRange, distance});
	}

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
		Advice advice = curated(monster, meleeRange);
		if (advice == null)
		{
			advice = fromAttackStyles(monster, meleeRange);
		}
		return unlocked(advice, prayerLevel);
	}

	/** Curated raid advice, or null when the target has none (falling back to its Wiki attack styles). */
	static Advice curated(Monster monster, boolean meleeRange)
	{
		String name = monster.getLowerName();
		for (Map.Entry<String, Advice[]> rule : RAIDS.entrySet())
		{
			if (name.startsWith(rule.getKey()))
			{
				return rule.getValue()[meleeRange ? 0 : 1];
			}
		}
		return null;
	}

	/**
	 * The Wiki attack styles: every protectable style, highest max hit first. Typeless, dragonfire, poison and
	 * fully prayer-penetrating attacks are left out, and "all" means all three styles. From distance, melee attacks
	 * are dropped unless the monster only has melee, since it walks up to you unless you safespot it. Data that
	 * can't be read (e.g. "none" with a max hit, or "various") gives no recommendation rather than "None needed".
	 */
	static Advice fromAttackStyles(Monster monster, boolean meleeRange)
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
		String note = order.size() == 1 ? "Wiki attack style: " + styles + "."
			: "Wiki attack styles: " + styles + ". Pray against the incoming style; " + order.get(0).toString()
				+ (maxHits.get(order.get(0)) > 0 ? " covers the hardest hit." : " is a safe default.");
		return new Advice(order.get(0), Collections.unmodifiableList(order), note + partial);
	}

	private static int tieRank(Overhead overhead)
	{
		return overhead == Overhead.MISSILES ? 0 : overhead == Overhead.MAGIC ? 1 : 2;
	}

	/** Attack styles no overhead blocks: typeless, dragonfire and breath, poison, bleed, curses and prayer-piercing. */
	static boolean unprotectable(String style)
	{
		String s = style.toLowerCase(Locale.ROOT);
		return s.contains("typeless") || s.contains("prayer penetration") || s.contains("dragonfire")
			|| s.contains("breath") || s.contains("poison") || s.contains("bleed") || s.contains("curse");
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
		return new Advice(usable.isEmpty() ? null : usable.get(0), Collections.unmodifiableList(usable), note);
	}
}
