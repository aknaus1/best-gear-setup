package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.RaidLoadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.EncounterPhase;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;

/** Explicit raid targets; exact catalogue names prevent picking an entry/hard/DMM variant. */
public enum RaidPlanType
{
	SINGLE("Single encounter"), COX_CM("Challenge Mode: Chambers of Xeric"), TOA("Tombs of Amascut"), TOB("Theatre of Blood");
	private static final AttackStyle.Type[] MELEE_TYPES = {AttackStyle.Type.STAB, AttackStyle.Type.SLASH, AttackStyle.Type.CRUSH};
	private static final AttackStyle.Type[] RANGED_TYPES = {AttackStyle.Type.RANGED, AttackStyle.Type.ATLATL};
	/**
	 * Nylocas room totals over its 31 fixed waves (Wiki wave table, the same at every party size): 76 small and 44 big
	 * nylocas, and each big one splits into two small ones when it dies (Blert). The party shares the room, so with
	 * every player's time divided by the party (as for boss HP) each wave encounter weighs the room's whole count.
	 */
	static final int NYLOCAS_WAVE_SMALL = 76 + 2 * 44;
	static final int NYLOCAS_WAVE_BIG = 44;
	/** The switch group of a ToB freezer's Maiden setup and the freeze switched into from it. */
	static final String MAIDEN_FREEZE = "Maiden freeze";
	/**
	 * One Warden is fought in P2 and the other in P3, so each Warden's P2, core and P3 is fought in half of raids.
	 * Which one dies first depends on the team's Obelisk play, so the plan takes the even expectation.
	 */
	static final double WARDEN_BRANCH = 0.5;
	/** Each hit on the ejected core deals five times its damage to the Warden (Wiki "Core"). */
	static final double WARDEN_CORE_SHARE = 1 / 5.0;
	/** Below 5% the P3 Warden enrages and heals 20% of its maximum HP, so a fifth more is dealt (Wiki). */
	static final double WARDEN_P3_HEALED = 1.2;
	/** Exposure ticks of the ejected core by the Warden's HP at ejection: from 80%, from 60%, below (Wiki "Core"). */
	private static final int[] CORE_EXPOSURE_TICKS = {21, 29, 37};
	/** Most core ejections assumed, for a core setup too weak to finish the Warden in any reasonable number. */
	private static final int MAX_CORE_EJECTIONS = 50;
	/**
	 * Ticks Vasa can be attacked: walking from the centre to a crystal, then (after a killed crystal) back to the
	 * centre, where he immediately picks the next crystal. He only stops to charge his teleport, when he can't be
	 * attacked, after the 40-second siphon timer runs out. Commonly seen timings rather than Wiki values.
	 */
	static final int VASA_TO_CRYSTAL_TICKS = 10;
	static final int VASA_TO_CENTRE_TICKS = 4;
	/** Seconds walking from one Tombs path, through the Nexus, to the next path's room: an estimate. */
	static final double TOA_PATH_WALK = 20;
	/** Ticks the Pestilent Bloat stays down, when he takes full damage. */
	static final int BLOAT_DOWN_TICKS = 32;
	/** Bloat's first walk and his walks after each stomp, the middle of the Wiki's 39-47 and 34-42 ticks. */
	static final int BLOAT_FIRST_WALK_TICKS = 43;
	static final int BLOAT_WALK_TICKS = 38;
	/** Most crystals assumed, for a Vasa setup too weak to kill him in any reasonable number of walks. */
	private static final int MAX_VASA_CRYSTALS = 50;
	private final String label;
	RaidPlanType(String label) { this.label = label; }
	@Override public String toString() { return label; }

	/** Full raids use the same search input as individual monsters, including common abbreviations. */
	public static List<RaidPlanType> search(String query)
	{
		List<RaidPlanType> matches = new ArrayList<>();
		String q = normalize(query);
		if (q.length() < 2) { return matches; }
		for (RaidPlanType raid : Arrays.asList(COX_CM, TOA, TOB))
		{
			String aliases = raid == COX_CM ? "cm cox|cox cm|cox|chambers of xeric|challenge mode|cox raid"
				: raid == TOA ? "toa|toa raid" : "tob|tob raid|theater of blood";
			if (normalize(raid.label).contains(q) || Arrays.stream(aliases.split("\\|")).anyMatch(alias -> normalize(alias).contains(q)))
			{ matches.add(raid); }
		}
		return matches;
	}

	private static String normalize(String text)
	{
		return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	public List<RaidEncounter> encounters(GameData data)
	{
		return encounters(data, RaidScaling.Settings.SOLO);
	}

	public List<RaidEncounter> encounters(GameData data, RaidScaling.Settings scaling)
	{
		return encounters(data, scaling, RaidOptions.DEFAULT);
	}

	/**
	 * Every fight in the order the rooms appear in the raid, which is also the selector order; the first is the
	 * starting worn setup. Each encounter records whether the raid's own boosts are available by then.
	 */
	public List<RaidEncounter> encounters(GameData data, RaidScaling.Settings scaling, RaidOptions options)
	{
		List<RaidEncounter> out = new ArrayList<>();
		// Fights before this index happen before raid-handed boosts (overloads, smelling salts) are available.
		int supplied = 0;
		switch (this)
		{
			case COX_CM:
				// Challenge Mode has a fixed layout. Keep combat targets in room order, with each room's
				// phases and supporting targets together; non-combat puzzles have no planned setup.
				// One HP pool: Tekton enrages briefly after each anvil repair (Wiki gives no share; a quarter is an estimate).
				add(out, data, "Tekton", "tekton (normal)");
				out.set(out.size() - 1, out.get(out.size() - 1).withShare(0.75));
				add(out, data, "Tekton enraged", "tekton (enraged)");
				out.set(out.size() - 1, out.get(out.size() - 1).withShare(0.25));
				// The kit is worn in for either Tekton phase, and its boosts are drunk before entering.
				out.replaceAll(e -> e.withPrepotted().withStartable());
				// Overloads are brewed after Tekton from herbs grown in the raid; Tekton uses your own boosts.
				supplied = out.size();
				// Scavengers are killed until the potion secondaries drop, so their count varies: show them, don't plan for them.
				displayOnly(out, data, "Scavengers", "scavenger beast (normal)");
				add(out, data, "Ice demon", "ice demon (normal)");
				add(out, data, "Shamans", "lizardman shaman (chambers of xeric) (normal)", RaidScaling.coxShamans(scaling));
				add(out, data, "Vanguard: melee", "vanguard (melee)", AttackStyle.Type.MAGIC);
				add(out, data, "Vanguard: ranged", "vanguard (ranged)", MELEE_TYPES);
				add(out, data, "Vanguard: magic", "vanguard (magic)", RANGED_TYPES);
				// Players pray Redemption and kill the portal; Vespula itself is rarely attacked.
				displayOnly(out, data, "Vespula", "vespula (normal)");
				add(out, data, "Vespula portal", "abyssal portal (normal)");
				// Two of each in solo and small groups (Wiki); larger raids have more, in numbers the Wiki doesn't give.
				// Kill the mages before the rangers.
				add(out, data, "Tightrope mage", "deathly mage (normal)", 2);
				add(out, data, "Tightrope ranger", "deathly ranger (normal)", 2);
				// Two guardians, at every party size (Wiki strategies).
				add(out, data, "Guardians", "guardian (chambers of xeric) (normal)", 2);
				// Vasa is attacked while he walks between the centre and a crystal; the crystal is then killed before
				// he can be attacked again. One crystal is assumed until Vasa has been searched (withCycles).
				add(out, data, "Vasa", "vasa nistirio (normal)");
				add(out, data, "Vasa crystal", "glowing crystal (normal)");
				// Mystics are ranged or maged from a distance rather than meleed in their group.
				distance(out, data, "Mystics", "skeletal mystic (normal)");
				out.set(out.size() - 1, out.get(out.size() - 1).withRepetitions(RaidScaling.coxMystics(scaling)));
				add(out, data, "Small muttadile", "muttadile (small)");
				// Fought from distance in practice: the large muttadile's melee bite hits far harder than its other attacks.
				distance(out, data, "Large muttadile", "muttadile (large)");
				for (int phase = 1; phase <= RaidScaling.olmHandPhases(scaling); phase++)
				{
					String suffix = phase == 1 ? "" : ": Phase " + phase;
					add(out, data, "Olm melee hand" + suffix, "great olm (melee hand)");
					add(out, data, "Olm mage hand" + suffix, "great olm (mage hand)");
				}
				add(out, data, "Olm head", "great olm (head)");
				break;
			case TOA:
			{
				// Each path is its puzzle room then its boss; only Apmeken's puzzle (the baboons) is a fight.
				List<RaidEncounter> apmeken = new ArrayList<>();
				// The puzzle's eight solo waves hold 5 brawlers, 5 throwers, 4 mages and 5 shamans besides their
				// volatile, cursed and special baboons (Wiki strategies, Path of Apmeken). A group adds one combat
				// baboon to each wave, of the wave's style: three melee, two ranged and two magic waves.
				boolean group = scaling.getPartySize() > 1;
				add(apmeken, data, "Baboon brawlers", "baboon brawler (level 56)", group ? 8 : 5);
				add(apmeken, data, "Baboon throwers", "baboon thrower (level 56)", group ? 7 : 5);
				add(apmeken, data, "Baboon mages", "baboon mage (level 56)", group ? 6 : 4);
				add(apmeken, data, "Baboon shamans", "baboon shaman", 5);
				// Baboons come in mixed waves and always take a max hit from the style they're weak to, so players
				// wear one setup and quick-switch to the others, swapping only pieces that raise the max hit.
				for (int baboon = 0; baboon < apmeken.size(); baboon++)
				{
					apmeken.set(baboon, apmeken.get(baboon).withSwitchGroup("Baboons"));
				}
				add(apmeken, data, "Ba-Ba", "ba-ba");
				List<RaidEncounter> scabaras = new ArrayList<>();
				add(scabaras, data, "Kephri shield", "kephri (shielded)");
				add(scabaras, data, "Kephri", "kephri (aggressive)");
				// One of each overlord; More Overlords adds a second soldier and spitting scarab.
				int overlords = options.isToaMoreOverlords() ? 2 : 1;
				add(scabaras, data, "Soldier scarab", "soldier scarab", overlords);
				add(scabaras, data, "Arcane scarab", "arcane scarab");
				add(scabaras, data, "Spitting scarab", "spitting scarab", overlords);
				// Only Kephri is worth a potion; her scarabs are fought unboosted.
				scabaras.replaceAll(e -> e.getLabel().startsWith("Kephri") ? e : e.withUnboosted());
				List<RaidEncounter> het = new ArrayList<>();
				// Current protection prayers block the style Akkha is using, leaving two valid classes. Her styles
				// rotate while her whole health is dealt, so each immune phase takes a third of it; her final stand
				// then restores a fifth of her maximum HP (Wiki).
				for (EncounterPhase phase : new EncounterPhase[]{EncounterPhase.AKKHA_IMMUNE_MELEE,
					EncounterPhase.AKKHA_IMMUNE_RANGED, EncounterPhase.AKKHA_IMMUNE_MAGIC, EncounterPhase.AKKHA_ENRAGE})
				{
					MonsterSummary akkha = data.getMonsters().stream().filter(m -> m.getName().equals("akkha")).findFirst().get();
					het.add(new RaidEncounter(GameData.titleCase(phase.toString()), akkha, 1, phase)
						.withShare(phase == EncounterPhase.AKKHA_ENRAGE ? 0.2 : 1 / 3.0));
				}
				// One shadow at each of 80%, 60%, 40% and 20% of her health.
				add(het, data, "Akkha shadows", "akkha's shadow", 4);
				List<RaidEncounter> crondis = new ArrayList<>();
				// Zebak can't be tanked in melee range (players step back between attacks), so Auto plans distance.
				// One HP pool: Zebak enrages at about 25% of his health (Wiki).
				distance(crondis, data, "Zebak", "zebak (normal)");
				crondis.set(crondis.size() - 1, crondis.get(crondis.size() - 1).withShare(0.75));
				distance(crondis, data, "Zebak enraged", "zebak (enraged)");
				crondis.set(crondis.size() - 1, crondis.get(crondis.size() - 1).withShare(0.25));
				List<List<RaidEncounter>> paths = options.getToaPathOrder() == RaidOptions.ToaPathOrder.ZEBAK_FIRST
					? Arrays.asList(crondis, scabaras, het, apmeken) : Arrays.asList(apmeken, scabaras, het, crondis);
				// The kit is worn in for any fight of the first path (its puzzle room or its boss), and that
				// path's boosts are drunk before entering; the second path's are carried.
				paths.get(0).replaceAll(e -> e.withPrepotted().withStartable());
				for (int path = 0; path < paths.size(); path++)
				{
					out.addAll(paths.get(path));
					// Smelling salts are available once the first two paths are complete.
					if (path == 1) { supplied = out.size(); }
				}
				add(out, data, "Obelisk", "obelisk (tombs of amascut)");
				for (String warden : new String[]{"elidinis", "tumeken"})
				{
					String name = warden + (warden.equals("elidinis") ? "' warden" : "'s warden");
					// P2 alternates the styles that break the Warden's shield, each break ejecting the core: the
					// Warden's real health, dealt only through the core. One break of each style is assumed until
					// the core has been searched (withCycles).
					add(out, data, warden + " P2: ranged", name + " (phase 2)", RANGED_TYPES);
					out.set(out.size() - 1, out.get(out.size() - 1).withRepetitions(WARDEN_BRANCH));
					add(out, data, warden + " P2: magic", name + " (phase 2)", AttackStyle.Type.MAGIC);
					out.set(out.size() - 1, out.get(out.size() - 1).withRepetitions(WARDEN_BRANCH));
					add(out, data, warden + " core", name + " (core-ejected)");
					out.set(out.size() - 1, out.get(out.size() - 1).withRepetitions(WARDEN_BRANCH).withShare(WARDEN_CORE_SHARE));
					add(out, data, warden + " P3", name + " (phase 3)");
					out.set(out.size() - 1, out.get(out.size() - 1).withRepetitions(WARDEN_BRANCH * WARDEN_P3_HEALED));
				}
				break;
			}
			case TOB:
			{
				// The Theatre hands out no boosts: every room uses the potions you bring.
				RaidOptions.TobRole role = options.getTobRole();
				add(out, data, "Maiden", "the maiden of sugadinti (normal)");
				// Freezers barrage Maiden's nylocas and everyone else kills them. North freezes, then helps with
				// the unclumped nylocas; Range DPS helps with those until the clump forms, then chins the clump.
				String nylocas = "nylocas matomenos (normal mode maiden)";
				if (role.freezes())
				{
					// The freeze is a switch from the Maiden setup, swapping as few items as land every barrage often
					// enough (FreezeDuty); it is never worn for the whole room.
					out.set(0, out.get(0).withSwitchGroup(MAIDEN_FREEZE));
					add(out, data, "Maiden nylocas: freeze", nylocas, AttackStyle.Type.MAGIC);
					out.set(out.size() - 1, out.get(out.size() - 1).withLoadoutRequirement(RaidLoadout.ICE_BARRAGE)
						.withSwitchGroup(MAIDEN_FREEZE, false));
				}
				// At each of three spawns the frozen clump is chinned and the rest are killed one by one, shared by the
				// party like Maiden's own HP. One player chins each clump, whose nylocas all take each throw, so a
				// clump weighs one kill of the party's time per spawn. The freeze is a duty, not a kill.
				int perSpawn = RaidScaling.maidenNylocasPerSpawn(scaling);
				int clump = RaidScaling.maidenClump(scaling);
				int loose = 3 * (perSpawn - clump);
				if (role == RaidOptions.TobRole.RANGED_DPS) { add(out, data, "Maiden nylocas", nylocas, loose, RANGED_TYPES); }
				else if (role != RaidOptions.TobRole.SOUTH_FREEZE) { add(out, data, "Maiden nylocas", nylocas, loose); }
				if (role == RaidOptions.TobRole.RANGED_DPS)
				{
					add(out, data, "Maiden nylocas: chins", nylocas, 3 * Math.max(1, scaling.getPartySize()), AttackStyle.Type.RANGED);
					out.set(out.size() - 1, out.get(out.size() - 1).withLoadoutRequirement(RaidLoadout.CHINCHOMPAS));
				}
				// Players drink their boosts before entering, so the kit only carries a boost a later room needs.
				// They also enter in whichever of the room's setups leaves the fewest switches to carry; a freeze is
				// only ever a switch.
				for (int index = 0; index < out.size(); index++)
				{
					RaidEncounter encounter = out.get(index).withPrepotted();
					out.set(index, encounter.isFreeze() ? encounter : encounter.withStartable());
				}
				// Bloat takes full damage only while he's stopped: 32 ticks at a time, after walks of 39-47 ticks
				// at first and 34-42 ticks after each stomp (Wiki; the down's length is a commonly seen value).
				add(out, data, "Bloat", "pestilent bloat (normal)");
				out.set(out.size() - 1, out.get(out.size() - 1).withDowns(new RaidEncounter.Downs(BLOAT_FIRST_WALK_TICKS,
					BLOAT_DOWN_TICKS, BLOAT_WALK_TICKS, scaling.getPartySize())));
				RaidStyle waves = role.waveStyle(scaling.getPartySize());
				// Nylocas only take damage from the style of their colour (and Vasilias from its current form's);
				// a role kills only its own colour of wave nylocas, while everyone fights each Vasilias form.
				String colour = waves == RaidStyle.MELEE ? "ischyros" : waves == RaidStyle.RANGED ? "toxobolos" : "hagios";
				AttackStyle.Type[] waveTypes = waves == RaidStyle.MELEE ? MELEE_TYPES : waves == RaidStyle.RANGED ? RANGED_TYPES
					: new AttackStyle.Type[]{AttackStyle.Type.MAGIC};
				String waveLabel = "Nylocas waves: " + waves.name().toLowerCase(Locale.ROOT);
				add(out, data, waveLabel, "nylocas " + colour + " (small)", NYLOCAS_WAVE_SMALL, waveTypes);
				add(out, data, waveLabel + " (big)", "nylocas " + colour + " (big)", NYLOCAS_WAVE_BIG, waveTypes);
				// Every role compares all three Vasilias bases; role duties apply to waves and Maiden. The Vasilias
				// changes to a random form every 10 ticks (Blert), so each form takes a third of its one HP pool.
				add(out, data, "Nylocas: melee", "nylocas vasilias (melee) (normal)", MELEE_TYPES);
				add(out, data, "Nylocas: ranged", "nylocas vasilias (ranged) (normal)", RANGED_TYPES);
				add(out, data, "Nylocas: magic", "nylocas vasilias (magic) (normal)", AttackStyle.Type.MAGIC);
				for (int form = out.size() - 3; form < out.size(); form++)
				{
					out.set(form, out.get(form).withSwitchGroup("Nylocas Vasilias").withShare(1 / 3.0));
				}
				add(out, data, "Sotetseg", "sotetseg (normal)");
				add(out, data, "Xarpus", "xarpus");
				for (int phase = 1; phase <= 3; phase++)
				{
					add(out, data, "Verzik P" + phase, "verzik vitur (phase " + phase + ")");
				}
				supplied = out.size();
				break;
			}
			default: break;
		}
		for (int index = 0; index < out.size(); index++)
		{
			RaidEncounter encounter = out.get(index).withRaidSupplies(index >= supplied).withSwitchLimit(options.getSwitchLimit());
			if (MAIDEN_FREEZE.equals(encounter.getSwitchGroup())) { encounter = encounter.withFreezeSwitches(); }
			encounter = withKills(withEnergy(encounter.withStyleKey(styleKey(encounter.getLabel())), scaling.getPartySize()));
			// A phase the shared choice can't damage keeps Auto.
			out.set(index, encounter.withStyleChoice(options.style(this, encounter.getStyleKey())));
		}
		return out;
	}

	/**
	 * When spec energy regenerates between fights. The raid starts on a full bar. The Theatre refills every player's
	 * bar as each room starts, and it regenerates on top during the room. In the Tombs and the Chambers the bar
	 * carries from room to room, regenerating over the rooms' own fights and the downtime before each room's first
	 * fight ({@link #restBefore}).
	 */
	private RaidEncounter withEnergy(RaidEncounter encounter, int party)
	{
		String name = encounter.getTarget().getName();
		boolean tobRoom = this == TOB && (name.equals("the maiden of sugadinti (normal)") || name.equals("pestilent bloat (normal)")
			|| name.matches("nylocas (ischyros|toxobolos|hagios) \\(small\\)") || name.equals("sotetseg (normal)")
			|| name.equals("xarpus") || name.equals("verzik vitur (phase 1)"));
		return encounter.withEnergy(restBefore(encounter), tobRoom, party);
	}

	/**
	 * Who kills each target and whether the kill is followed by a pause. Adds are killed by one player each, back to
	 * back (the waves' and Maiden's nylocas, baboons, scarabs, mystics, shamans, the tightrope's rangers and mages).
	 * Bosses are the party's, and the kill that ends a fight lets each player's last attack skip its interval: the
	 * last phase of a pool (Akkha's final stand, enraged Zebak), a share of the pools that may end in any phase
	 * (Tekton, the Vasilias forms), never a phase ending at a threshold, a shield break, Vasa's crystal or Bloat,
	 * whose downs follow each other.
	 */
	private static RaidEncounter withKills(RaidEncounter encounter)
	{
		String name = encounter.getTarget().getName();
		if (name.matches("nylocas (ischyros|toxobolos|hagios) \\((small|big)\\)") || name.startsWith("nylocas matomenos")
			|| name.startsWith("baboon") || name.endsWith(" scarab") || name.startsWith("skeletal mystic")
			|| name.startsWith("lizardman shaman") || name.startsWith("deathly"))
		{
			return encounter.withKillsEach();
		}
		boolean thresholds = name.equals("akkha") && encounter.getPhase() != EncounterPhase.AKKHA_ENRAGE
			|| name.equals("zebak (normal)") || name.equals("akkha's shadow") || name.endsWith("(phase 2)") && name.contains("warden")
			|| name.startsWith("glowing crystal") || name.startsWith("pestilent bloat");
		if (thresholds) { return encounter.withFinalBlow(0); }
		// Tekton and the Vasilias forms may die in any of their phases, in proportion to each phase's share.
		if (name.startsWith("tekton") || name.startsWith("nylocas vasilias")) { return encounter.withFinalBlow(encounter.getShare()); }
		return encounter.withFinalBlow(1);
	}

	/**
	 * Estimated seconds without combat before a room's first fight, while spec energy regenerates; 0 within a room.
	 * Tombs: each path's puzzle (the Wiki's solo 545 splits: Scabaras about 0:50-0:55, Het 0:35-0:45, Crondis about
	 * 0:32, and the Apmeken room's tasks around its baboon fights) plus about 20 seconds of walking between paths, and
	 * the walk to the Wardens. Chambers, in the Challenge Mode layout: the puzzles (jewelled crabs, thieving,
	 * tightrope, the ice demon's kindling), the scavengers, preparation in the resource rooms and walking.
	 */
	private double restBefore(RaidEncounter encounter)
	{
		String name = encounter.getTarget().getName();
		switch (this)
		{
			case TOA:
				return name.equals("baboon brawler (level 56)") ? TOA_PATH_WALK : name.equals("ba-ba") ? 60
					: name.equals("kephri (shielded)") ? TOA_PATH_WALK + 55
					: name.equals("akkha") && encounter.getPhase() == EncounterPhase.AKKHA_IMMUNE_MELEE ? TOA_PATH_WALK + 40
					: name.equals("zebak (normal)") ? TOA_PATH_WALK + 32 : name.startsWith("obelisk") ? 30 : 0;
			case COX_CM:
				// Crabs, the scavengers and the ice demon's kindling; prep after the shamans; thieving and the scavengers;
				// the second resource room; crossing the tightrope; the scavengers; the third resource room and Olm rising.
				return name.equals("ice demon (normal)") ? 60 + 45 + 60 + 30 : name.startsWith("lizardman shaman") ? 20
					: name.equals("vanguard (melee)") ? 120 + 20 : name.equals("abyssal portal (normal)") ? 60 + 45 + 20
					: name.equals("deathly mage (normal)") ? 60 + 20 : name.startsWith("guardian") ? 30 + 15
					: name.equals("vasa nistirio (normal)") ? 20 : name.startsWith("skeletal mystic") ? 45 + 20
					: name.equals("muttadile (small)") ? 20 : name.equals("great olm (melee hand)")
					&& !encounter.getLabel().contains(": Phase ") ? 60 + 30 : 0;
			default: return 0;
		}
	}

	/**
	 * Counts that follow from how fast the party damages a boss, once it has been searched, using its fastest setup:
	 * <ul>
	 * <li>The Wardens' P2 shield breaks: each break ejects the core for a window set by the Warden's HP at that moment,
	 * so a weaker core setup needs more breaks. Breaks alternate the two shield styles, so each takes half of them.</li>
	 * <li>Vasa's crystals: he is attacked only on his way between the centre and a crystal, which must be killed before
	 * he can be attacked again, so every window but the killing one ends in a crystal.</li>
	 * </ul>
	 * The lists are aligned; other encounters are returned unchanged.
	 */
	static List<RaidEncounter> withCycles(List<RaidEncounter> encounters, List<SearchResults> results, int partySize)
	{
		List<RaidEncounter> out = new ArrayList<>(encounters);
		for (int boss = 0; boss < encounters.size(); boss++)
		{
			if (!encounters.get(boss).getTarget().getName().equals("vasa nistirio (normal)")) { continue; }
			SetupResult fastest = RaidConsolidator.fastest(results.get(boss), encounters.get(boss));
			double partyDps = fastest == null ? 0 : Math.max(1, partySize) * fastest.getDps().getPrimaryDps();
			int crystals = vasaCrystals(encounters.get(boss).hitpoints(results.get(boss)), partyDps);
			for (int index = 0; index < encounters.size(); index++)
			{
				if (encounters.get(index).getTarget().getName().equals("glowing crystal (normal)"))
				{ out.set(index, encounters.get(index).withRepetitions(crystals)); }
			}
		}
		for (int core = 0; core < encounters.size(); core++)
		{
			String name = encounters.get(core).getTarget().getName();
			if (!name.endsWith(" (core-ejected)")) { continue; }
			RaidEncounter encounter = encounters.get(core);
			SetupResult fastest = RaidConsolidator.fastest(results.get(core), encounter);
			double partyDps = fastest == null ? 0 : Math.max(1, partySize) * fastest.getDps().getPrimaryDps();
			int breaks = coreEjections(encounter.hitpoints(results.get(core)), partyDps);
			String shield = name.substring(0, name.length() - " (core-ejected)".length()) + " (phase 2)";
			for (int index = 0; index < encounters.size(); index++)
			{
				if (encounters.get(index).getTarget().getName().equals(shield))
				{ out.set(index, encounters.get(index).withRepetitions(WARDEN_BRANCH * breaks / 2.0)); }
			}
		}
		return out;
	}

	/**
	 * Crystals Vasa reaches before the party, attacking only between crystals, deals {@code hitpoints}: he first walks
	 * from the centre to a crystal, and after each killed crystal returns to the centre and walks to the next.
	 */
	static int vasaCrystals(int hitpoints, double partyDps)
	{
		if (!(partyDps > 0)) { return MAX_VASA_CRYSTALS; }
		double dealt = partyDps * VASA_TO_CRYSTAL_TICKS * 0.6;
		int crystals = 0;
		while (dealt < hitpoints && crystals < MAX_VASA_CRYSTALS)
		{
			crystals++;
			dealt += partyDps * (VASA_TO_CENTRE_TICKS + VASA_TO_CRYSTAL_TICKS) * 0.6;
		}
		return crystals;
	}

	/**
	 * Core ejections until the party has dealt {@code hitpoints} through it: each lasts 21 ticks while the Warden is
	 * above 80% of its health, 29 above 60% and 37 below (Wiki "Core").
	 */
	static int coreEjections(int hitpoints, double partyDps)
	{
		if (!(partyDps > 0)) { return MAX_CORE_EJECTIONS; }
		double dealt = 0;
		int breaks = 0;
		while (dealt < hitpoints && breaks < MAX_CORE_EJECTIONS)
		{
			double remaining = 1 - dealt / hitpoints;
			int ticks = remaining >= 0.8 ? CORE_EXPOSURE_TICKS[0] : remaining >= 0.6 ? CORE_EXPOSURE_TICKS[1] : CORE_EXPOSURE_TICKS[2];
			dealt += partyDps * ticks * 0.6;
			breaks++;
		}
		return breaks;
	}

	/**
	 * Phases of one fight share a style choice; everything else, including the Olm parts, the two Muttadiles and
	 * Verzik's phases (each fought with different gear), is chosen on its own.
	 */
	static String styleKey(String label)
	{
		if (label.startsWith("Olm Melee Hand: Phase ")) { return "Olm Melee Hand"; }
		if (label.startsWith("Olm Mage Hand: Phase ")) { return "Olm Mage Hand"; }
		if (label.startsWith("Akkha: ")) { return "Akkha"; }
		switch (label)
		{
			case "Tekton Enraged": return "Tekton";
			case "Zebak Enraged": return "Zebak";
			case "Kephri Shield": return "Kephri";
			case "Elidinis Core": return "Elidinis P3";
			case "Tumeken Core": return "Tumeken P3";
			default: return label;
		}
	}

	private static void distance(List<RaidEncounter> out, GameData data, String label, String name)
	{
		add(out, data, label, name);
		out.set(out.size() - 1, out.get(out.size() - 1).withPreferredStyle(RaidStyle.DISTANCE));
	}

	private static void displayOnly(List<RaidEncounter> out, GameData data, String label, String name)
	{
		add(out, data, label, name);
		out.set(out.size() - 1, out.get(out.size() - 1).withDisplayOnly());
	}

	private static void add(List<RaidEncounter> out, GameData data, String label, String name, AttackStyle.Type... styles)
	{
		add(out, data, label, name, 1, styles);
	}

	private static void add(List<RaidEncounter> out, GameData data, String label, String name, int repetitions, AttackStyle.Type... styles)
	{
		MonsterSummary target = data.getMonsters().stream().filter(m -> name.equals(m.getName())).findFirst()
			.orElseThrow(() -> new IllegalStateException("Missing raid target: " + name));
		out.add(new RaidEncounter(GameData.titleCase(label), target, repetitions, styles));
	}
}
