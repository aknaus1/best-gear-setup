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
		List<RaidEncounter> out = new ArrayList<>();
		switch (this)
		{
			case COX_CM:
				add(out, data, "Tekton", "tekton (normal)");
				add(out, data, "Tekton enraged", "tekton (enraged)");
				add(out, data, "Scavengers", "scavenger beast (normal)");
				add(out, data, "Ice demon", "ice demon (normal)");
				add(out, data, "Shamans", "lizardman shaman (chambers of xeric) (normal)");
				add(out, data, "Vanguard: melee", "vanguard (melee)", AttackStyle.Type.MAGIC);
				add(out, data, "Vanguard: ranged", "vanguard (ranged)", AttackStyle.Type.STAB, AttackStyle.Type.SLASH, AttackStyle.Type.CRUSH);
				add(out, data, "Vanguard: magic", "vanguard (magic)", AttackStyle.Type.RANGED, AttackStyle.Type.ATLATL);
				add(out, data, "Vespula", "vespula (normal)");
				add(out, data, "Vespula portal", "abyssal portal (normal)");
				add(out, data, "Guardians", "guardian (chambers of xeric) (normal)");
				add(out, data, "Vasa", "vasa nistirio (normal)");
				add(out, data, "Vasa crystal", "glowing crystal (normal)");
				add(out, data, "Mystics", "skeletal mystic (normal)");
				add(out, data, "Small muttadile", "muttadile (small)");
				add(out, data, "Large muttadile", "muttadile (large)");
				add(out, data, "Tightrope ranger", "deathly ranger (normal)");
				add(out, data, "Tightrope mage", "deathly mage (normal)");
				add(out, data, "Olm melee hand", "great olm (melee hand)", RaidScaling.olmHandPhases(scaling));
				add(out, data, "Olm mage hand", "great olm (mage hand)", RaidScaling.olmHandPhases(scaling));
				add(out, data, "Olm head", "great olm (head)");
				break;
			case TOA:
				add(out, data, "Ba-Ba", "ba-ba");
				add(out, data, "Baboon brawlers", "baboon brawler (level 56)");
				add(out, data, "Baboon throwers", "baboon thrower (level 56)");
				add(out, data, "Baboon mages", "baboon mage (level 56)");
				add(out, data, "Baboon shamans", "baboon shaman");
				add(out, data, "Kephri shield", "kephri (shielded)");
				add(out, data, "Kephri", "kephri (aggressive)");
				add(out, data, "Soldier scarab", "soldier scarab");
				add(out, data, "Arcane scarab", "arcane scarab");
				add(out, data, "Spitting scarab", "spitting scarab");
				// Current protection prayers block the style Akkha is using, leaving two valid classes.
				for (EncounterPhase phase : new EncounterPhase[]{EncounterPhase.AKKHA_IMMUNE_MELEE,
					EncounterPhase.AKKHA_IMMUNE_RANGED, EncounterPhase.AKKHA_IMMUNE_MAGIC, EncounterPhase.AKKHA_ENRAGE})
				{
					MonsterSummary akkha = data.getMonsters().stream().filter(m -> m.getName().equals("akkha")).findFirst().get();
					out.add(new RaidEncounter(GameData.titleCase(phase.toString()), akkha, 1, phase));
				}
				add(out, data, "Akkha shadows", "akkha's shadow");
				add(out, data, "Zebak", "zebak (normal)");
				add(out, data, "Zebak enraged", "zebak (enraged)");
				add(out, data, "Obelisk", "obelisk (tombs of amascut)");
				for (String warden : new String[]{"elidinis", "tumeken"})
				{
					String name = warden + (warden.equals("elidinis") ? "' warden" : "'s warden");
					add(out, data, warden + " P2: ranged", name + " (phase 2)", AttackStyle.Type.RANGED, AttackStyle.Type.ATLATL);
					add(out, data, warden + " P2: magic", name + " (phase 2)", AttackStyle.Type.MAGIC);
					add(out, data, warden + " core", name + " (core-ejected)");
					add(out, data, warden + " P3", name + " (phase 3)");
				}
				break;
			case TOB:
				add(out, data, "Maiden", "the maiden of sugadinti (normal)");
				add(out, data, "Maiden nylocas", "nylocas matomenos (normal mode maiden)", AttackStyle.Type.MAGIC);
				add(out, data, "Bloat", "pestilent bloat (normal)");
				add(out, data, "Nylocas waves: melee", "nylocas ischyros (small)");
				add(out, data, "Nylocas waves: ranged", "nylocas toxobolos (small)");
				add(out, data, "Nylocas waves: magic", "nylocas hagios (small)");
				for (String style : new String[]{"melee", "ranged", "magic"})
				{
					add(out, data, "Nylocas: " + style, "nylocas vasilias (" + style + ") (normal)");
				}
				add(out, data, "Sotetseg", "sotetseg (normal)");
				add(out, data, "Xarpus", "xarpus");
				for (int phase = 1; phase <= 3; phase++)
				{
					add(out, data, "Verzik P" + phase, "verzik vitur (phase " + phase + ")");
				}
				break;
			default: break;
		}
		return out;
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
