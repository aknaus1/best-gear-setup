package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
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

	/** Curated raid advice by name prefix, most specific first: {melee range, from distance}. */
	private static final Map<String, Advice[]> RAIDS = new LinkedHashMap<>();
	/** The Wiki strategy pages each raid's advice was checked against. */
	private static final String COX_SOURCE = "https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Strategies";
	private static final String TOA_SOURCE = "https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies";
	private static final String TOB_SOURCE = "https://oldschool.runescape.wiki/w/Theatre_of_Blood/Strategies";

	static
	{
		// Chambers of Xeric
		both(COX_SOURCE, "tekton", advice("Tekton only attacks with melee.", Overhead.MELEE));
		both(COX_SOURCE, "ice demon", advice("Keep Protect from Missiles active: it makes the ice demon use snowballs rather "
			+ "than Ice Burst and reduces their damage. Step away from the targeted tiles.", Overhead.MISSILES));
		both(COX_SOURCE, "lizardman shaman (chambers of xeric)", advice("Shamans spit ranged attacks; they also melee adjacent "
			+ "players. Their jump and purple spawns aren't prayable: move away.", Overhead.MISSILES));
		both(COX_SOURCE, "vanguard (melee)", advice("Use Melee against the melee Vanguard, which can approach you. Keep other "
			+ "Vanguards out of range where possible.", Overhead.MELEE));
		both(COX_SOURCE, "vanguard (ranged)", advice("Use Missiles against the ranged Vanguard. Prayer reduces its projectile "
			+ "damage and accuracy; move away from the other targeted tiles.", Overhead.MISSILES));
		both(COX_SOURCE, "vanguard (magic)", advice("Use Magic against the magic Vanguard. Prayer reduces its projectile "
			+ "damage and accuracy; move away from the other targeted tiles.", Overhead.MAGIC));
		both(COX_SOURCE, "vespula", advice("Vespula's stings are ranged.", Overhead.MISSILES));
		both(COX_SOURCE, "abyssal portal", advice("Vespula keeps stinging with ranged while you attack the portal.", Overhead.MISSILES));
		both(COX_SOURCE, "guardian (chambers of xeric)", advice("Usually none: step out of the slam. Its slam is melee, so use "
			+ "Protect from Melee only if you tank it."));
		both(COX_SOURCE, "vasa nistirio", advice("Use Missiles for Vasa's boulders and dodge their targeted area. Use Magic "
			+ "when joining the stunned players during his teleport special; stunned players cannot pray.",
			Overhead.MISSILES, Overhead.MAGIC));
		both(COX_SOURCE, "glowing crystal", advice("The crystal doesn't attack."));
		both(COX_SOURCE, "skeletal mystic", advice("Mystics cast magic at range and also melee adjacent players; fight them from "
			+ "distance.", Overhead.MAGIC));
		raid(COX_SOURCE, "muttadile (small)", advice("The small muttadile bites in melee range.", Overhead.MELEE),
			advice("At distance the small muttadile uses ranged.", Overhead.MISSILES));
		raid(COX_SOURCE, "muttadile (large)", advice("The large muttadile bites hard in melee range; step back and it uses ranged "
			+ "and magic.", Overhead.MELEE), advice("Keep Protect from Missiles at distance: the large muttadile's "
			+ "ranged attacks hit harder and more accurately than its magic. Stay out of melee and stomp range.", Overhead.MISSILES));
		both(COX_SOURCE, "deathly ranger", advice("Deathly rangers use ranged.", Overhead.MISSILES));
		both(COX_SOURCE, "deathly mage", advice("Deathly mages use magic.", Overhead.MAGIC));
		both(COX_SOURCE, "great olm", advice("Maintain the prayer for Olm's head's current style, updating it for subsequent "
			+ "attacks after a style change; ordinary attacks aren't reliably telegraphed. His coloured spheres can be "
			+ "prayed against before impact. The head keeps attacking while you damage either hand.", Overhead.MAGIC, Overhead.MISSILES));
		// Tombs of Amascut
		both(TOA_SOURCE, "ba-ba", advice("Ba-Ba's attacks are melee; dodge her boulders and falling rocks.", Overhead.MELEE));
		// Listed before "akkha" so the shadows get their own advice; the Wiki gives them no attack style.
		both(TOA_SOURCE, "akkha's shadow", advice("The shadow doesn't make ordinary attacks; Akkha keeps attacking while you "
			+ "damage it. Match Akkha's current form with Melee, Missiles or Magic before he attacks; he can approach "
			+ "ranged and magic setups too.", Overhead.MELEE, Overhead.MISSILES, Overhead.MAGIC));
		both(TOA_SOURCE, "akkha", advice("Akkha cycles melee, ranged and magic forms: maintain the matching prayer before he "
			+ "attacks, using his form / overhead as the cue. He can approach ranged and magic setups. Perfect "
			+ "butterfly kiting avoids melee; an ordinary ranged setup doesn't guarantee that.",
			Overhead.MELEE, Overhead.MISSILES, Overhead.MAGIC));
		raid(TOA_SOURCE, "zebak", advice("Zebak can't be tanked in melee range; step back between his ranged and magic rocks.",
			Overhead.MISSILES, Overhead.MAGIC, Overhead.MELEE), advice("Zebak alternates ranged and magic rocks: pray "
			+ "against each. He bites players next to him.", Overhead.MISSILES, Overhead.MAGIC));
		both(TOA_SOURCE, "obelisk (tombs of amascut)", advice("P1 charged orbs and energy trough damage aren't blocked by "
			+ "overhead prayers. Desynchronise the Wardens, share / separate for the appropriate orb and heal; avoid UFOs."));
		raid(TOA_SOURCE, "elidinis' warden (core-ejected)", advice("The core doesn't attack."), advice("The core doesn't attack."));
		raid(TOA_SOURCE, "tumeken's warden (core-ejected)", advice("The core doesn't attack."), advice("The core doesn't attack."));
		both(TOA_SOURCE, "elidinis' warden (phase 3)", advice("Dodge the Warden's floor attacks. Match the supporting Akkha / Zebak "
			+ "echo's Magic or Missiles attacks; prayer does not block the Warden's floor damage.", Overhead.MAGIC, Overhead.MISSILES));
		both(TOA_SOURCE, "tumeken's warden (phase 3)", advice("Dodge the Warden's floor attacks. Match the supporting Akkha / Zebak "
			+ "echo's Magic or Missiles attacks; prayer does not block the Warden's floor damage.", Overhead.MAGIC, Overhead.MISSILES));
		both(TOA_SOURCE, "elidinis' warden", advice("The Warden alternates magic and ranged projectiles: pray against each. Dodge "
			+ "its slam.", Overhead.MAGIC, Overhead.MISSILES));
		raid(TOA_SOURCE, "tumeken's warden", advice("P2: use Melee between projectile attacks if Tumeken's Warden reaches you; "
			+ "match its Magic / Missiles projectiles. Dodge obelisk beams and floor attacks.", Overhead.MELEE, Overhead.MAGIC, Overhead.MISSILES),
			advice("P2: match the Warden's Magic / Missiles projectiles and keep out of melee reach; Tumeken's Warden "
				+ "can approach you. Dodge obelisk beams and floor attacks.", Overhead.MAGIC, Overhead.MISSILES));
		// Theatre of Blood
		both(TOB_SOURCE, "the maiden of sugadinti", advice("Maiden's blood attacks are magic.", Overhead.MAGIC));
		// Only the Maiden room's nylocas: the Verzik room's use their own Wiki data.
		for (String mode : new String[]{"entry", "normal", "hard"})
		{
			both(TOB_SOURCE, "nylocas matomenos (" + mode + " mode maiden)", advice("The nylocas don't attack; keep Protect from Magic "
				+ "against Maiden.", Overhead.MAGIC));
		}
		both(TOB_SOURCE, "pestilent bloat", advice("None: step out from under Bloat before it stomps and dodge the falling flesh."));
		both(TOB_SOURCE, "nylocas vasilias (melee)", advice("Vasilias attacks with its current form's style and changes form every "
			+ "few attacks.", Overhead.MELEE));
		both(TOB_SOURCE, "nylocas vasilias (ranged)", advice("Vasilias attacks with its current form's style and changes form every "
			+ "few attacks.", Overhead.MISSILES));
		both(TOB_SOURCE, "nylocas vasilias (magic)", advice("Vasilias attacks with its current form's style and changes form every "
			+ "few attacks.", Overhead.MAGIC));
		raid(TOB_SOURCE, "sotetseg", advice("Tanking in melee range: Protect from Melee against its melee, switching to Magic or "
			+ "Missiles for its magic and ranged orbs.", Overhead.MELEE, Overhead.MAGIC, Overhead.MISSILES),
			advice("Sotetseg alternates magic and ranged orbs: pray against each.", Overhead.MAGIC, Overhead.MISSILES));
		both(TOB_SOURCE, "xarpus", advice("None needed: Xarpus's spit is typeless, so no overhead reduces it (Protect from Missiles "
			+ "is only habit). Avoid the acid pools."));
		both(TOB_SOURCE, "verzik vitur (phase 1)", advice("Hide behind a pillar; her P1 attack is magic.", Overhead.MAGIC));
		both(TOB_SOURCE, "verzik vitur (phase 2)", advice("Keep Protect from Missiles during Verzik P2. Step off the urn bomb "
			+ "tiles and step back between melee attacks to avoid her slam.", Overhead.MISSILES));
		raid(TOB_SOURCE, "verzik vitur (phase 3)", advice("Pray against her magic and ranged attacks. Her melee is typeless and no "
			+ "prayer blocks it: stay out of her melee range unless you are tanking. Every fifth attack is a special.",
			Overhead.MAGIC, Overhead.MISSILES),
			advice("Verzik P3 rotates magic and ranged at distance; every fifth attack is a special.",
				Overhead.MAGIC, Overhead.MISSILES));
	}

	private static void both(String source, String prefix, Advice advice)
	{
		raid(source, prefix, advice, advice);
	}

	private static void raid(String source, String prefix, Advice meleeRange, Advice distance)
	{
		RAIDS.put(prefix, new Advice[]{meleeRange.withSource(source), distance.withSource(source)});
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
