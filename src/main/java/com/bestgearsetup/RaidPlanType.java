package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.RaidScaling;
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
				// Challenge Mode layouts are fixed for each raid, so no single order is exact. This follows the common
				// rotation of combat rooms (Tekton, Muttadile, Guardians, Vespula, Shamans, Vasa, Vanguards, Mystics)
				// with Olm last; puzzle and scavenger rooms sit between them and their positions vary by layout.
				add(out, data, "Tekton", "tekton (normal)");
				add(out, data, "Tekton enraged", "tekton (enraged)");
				// Overloads are brewed after Tekton from herbs grown in the raid; Tekton uses your own boosts.
				supplied = out.size();
				add(out, data, "Scavengers", "scavenger beast (normal)");
				add(out, data, "Small muttadile", "muttadile (small)");
				// Fought from distance in practice: the large muttadile's melee bite hits far harder than its other attacks.
				distance(out, data, "Large muttadile", "muttadile (large)");
				add(out, data, "Guardians", "guardian (chambers of xeric) (normal)");
				add(out, data, "Ice demon", "ice demon (normal)");
				add(out, data, "Vespula", "vespula (normal)");
				add(out, data, "Vespula portal", "abyssal portal (normal)");
				add(out, data, "Shamans", "lizardman shaman (chambers of xeric) (normal)");
				add(out, data, "Vasa", "vasa nistirio (normal)");
				add(out, data, "Vasa crystal", "glowing crystal (normal)");
				add(out, data, "Tightrope ranger", "deathly ranger (normal)");
				add(out, data, "Tightrope mage", "deathly mage (normal)");
				add(out, data, "Vanguard: melee", "vanguard (melee)", AttackStyle.Type.MAGIC);
				add(out, data, "Vanguard: ranged", "vanguard (ranged)", MELEE_TYPES);
				add(out, data, "Vanguard: magic", "vanguard (magic)", RANGED_TYPES);
				// Mystics are ranged or maged from a distance rather than meleed in their group.
				distance(out, data, "Mystics", "skeletal mystic (normal)");
				add(out, data, "Olm melee hand", "great olm (melee hand)", RaidScaling.olmHandPhases(scaling));
				add(out, data, "Olm mage hand", "great olm (mage hand)", RaidScaling.olmHandPhases(scaling));
				add(out, data, "Olm head", "great olm (head)");
				break;
			case TOA:
			{
				// Each path is its puzzle room then its boss; only Apmeken's puzzle (the baboons) is a fight.
				List<RaidEncounter> apmeken = new ArrayList<>();
				add(apmeken, data, "Baboon brawlers", "baboon brawler (level 56)");
				add(apmeken, data, "Baboon throwers", "baboon thrower (level 56)");
				add(apmeken, data, "Baboon mages", "baboon mage (level 56)");
				add(apmeken, data, "Baboon shamans", "baboon shaman");
				add(apmeken, data, "Ba-Ba", "ba-ba");
				List<RaidEncounter> scabaras = new ArrayList<>();
				add(scabaras, data, "Kephri shield", "kephri (shielded)");
				add(scabaras, data, "Kephri", "kephri (aggressive)");
				add(scabaras, data, "Soldier scarab", "soldier scarab");
				add(scabaras, data, "Arcane scarab", "arcane scarab");
				add(scabaras, data, "Spitting scarab", "spitting scarab");
				List<RaidEncounter> het = new ArrayList<>();
				// Current protection prayers block the style Akkha is using, leaving two valid classes.
				for (EncounterPhase phase : new EncounterPhase[]{EncounterPhase.AKKHA_IMMUNE_MELEE,
					EncounterPhase.AKKHA_IMMUNE_RANGED, EncounterPhase.AKKHA_IMMUNE_MAGIC, EncounterPhase.AKKHA_ENRAGE})
				{
					MonsterSummary akkha = data.getMonsters().stream().filter(m -> m.getName().equals("akkha")).findFirst().get();
					het.add(new RaidEncounter(GameData.titleCase(phase.toString()), akkha, 1, phase));
				}
				add(het, data, "Akkha shadows", "akkha's shadow");
				List<RaidEncounter> crondis = new ArrayList<>();
				// Zebak can't be tanked in melee range (players step back between attacks), so Auto plans distance.
				distance(crondis, data, "Zebak", "zebak (normal)");
				distance(crondis, data, "Zebak enraged", "zebak (enraged)");
				List<List<RaidEncounter>> paths = options.getToaPathOrder() == RaidOptions.ToaPathOrder.ZEBAK_FIRST
					? Arrays.asList(crondis, scabaras, het, apmeken) : Arrays.asList(apmeken, scabaras, het, crondis);
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
					add(out, data, warden + " P2: ranged", name + " (phase 2)", RANGED_TYPES);
					add(out, data, warden + " P2: magic", name + " (phase 2)", AttackStyle.Type.MAGIC);
					add(out, data, warden + " core", name + " (core-ejected)");
					add(out, data, warden + " P3", name + " (phase 3)");
				}
				break;
			}
			case TOB:
			{
				// The Theatre hands out no boosts: every room uses the potions you bring.
				RaidOptions.MaidenRole maiden = options.getMaidenRole();
				int start = out.size();
				add(out, data, "Maiden", "the maiden of sugadinti (normal)");
				// Freezers stay on magic at Maiden to barrage the nylocas between casts on the boss.
				if (maiden == RaidOptions.MaidenRole.FREEZER) { out.set(start, out.get(start).withPreferredStyle(RaidStyle.MAGIC)); }
				if (maiden != RaidOptions.MaidenRole.DPS)
				{ add(out, data, "Maiden nylocas", "nylocas matomenos (normal mode maiden)", AttackStyle.Type.MAGIC); }
				add(out, data, "Bloat", "pestilent bloat (normal)");
				RaidOptions.NylocasRole nylocas = options.getNylocasRole();
				// Nylocas only take damage from the style of their colour (and Vasilias from its current form's);
				// a role kills only its own colour of wave nylocas, while everyone fights each Vasilias form.
				if (nylocas == RaidOptions.NylocasRole.ALL || nylocas == RaidOptions.NylocasRole.MELEE)
				{ add(out, data, "Nylocas waves: melee", "nylocas ischyros (small)", MELEE_TYPES); }
				if (nylocas == RaidOptions.NylocasRole.ALL || nylocas == RaidOptions.NylocasRole.RANGED)
				{ add(out, data, "Nylocas waves: ranged", "nylocas toxobolos (small)", RANGED_TYPES); }
				if (nylocas == RaidOptions.NylocasRole.ALL || nylocas == RaidOptions.NylocasRole.MAGIC)
				{ add(out, data, "Nylocas waves: magic", "nylocas hagios (small)", AttackStyle.Type.MAGIC); }
				add(out, data, "Nylocas: melee", "nylocas vasilias (melee) (normal)", MELEE_TYPES);
				add(out, data, "Nylocas: ranged", "nylocas vasilias (ranged) (normal)", RANGED_TYPES);
				add(out, data, "Nylocas: magic", "nylocas vasilias (magic) (normal)", AttackStyle.Type.MAGIC);
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
			RaidEncounter encounter = out.get(index);
			out.set(index, encounter.withRaidSupplies(index >= supplied)
				.withStyleChoice(options.style(this, encounter.getLabel())));
		}
		return out;
	}

	private static void distance(List<RaidEncounter> out, GameData data, String label, String name)
	{
		add(out, data, label, name);
		out.set(out.size() - 1, out.get(out.size() - 1).withPreferredStyle(RaidStyle.DISTANCE));
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
