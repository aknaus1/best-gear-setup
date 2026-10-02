package com.bestgearsetup.calc;

import com.bestgearsetup.data.Monster;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

/**
 * Raid stat scaling, applied to the solo, unscaled raid records before health states and pre-fight drains.
 * Theatre of Blood and Tombs of Amascut follow their Wiki pages; the Chambers of Xeric values follow the
 * OSRS Wiki DPS calculator (lib/scaling at the audited revision), as the Wiki pages do not state them.
 * Chambers of Xeric scales levels and HP, Theatre of Blood scales HP, and Tombs of Amascut scales HP
 * and multiplies the target's defence roll by its raid level.
 */
public final class RaidScaling
{
	private static final int COX_CM_PERCENT = 50;

	private RaidScaling()
	{
	}

	/** Party inputs. CoX uses the highest party combat and HP levels, normally the player's own. */
	@Value
	@Builder(toBuilder = true)
	public static class Settings
	{
		public static final Settings SOLO = Settings.builder().build();

		@Builder.Default
		int partySize = 1;
		int toaRaidLevel;
		int toaPathLevel;
		boolean coxChallengeMode;
		@Builder.Default
		int partyMaxCombat = 126;
		@Builder.Default
		int partyMaxHitpoints = 99;
		/** Average party Mining level, used for CoX guardian HP. */
		@Builder.Default
		int partyAverageMining = 99;
	}

	public enum Raid
	{
		NONE, COX, TOB, TOB_ENTRY, TOA
	}

	public static Raid raid(Monster m)
	{
		if (m.hasAttribute("xerician"))
		{
			return Raid.COX;
		}
		String name = m.getName().toLowerCase(Locale.ROOT);
		int id = m.getId();
		if (m.hasAttribute("theatre of blood"))
		{
			// Names take priority: some ToB variant IDs are shared between modes.
			boolean entry = name.contains("entry") || name.contains("story")
				|| !name.contains("normal") && !name.contains("hard") && TOB_ENTRY_IDS.contains(id);
			return entry ? Raid.TOB_ENTRY : Raid.TOB;
		}
		return toaScaled(m) ? Raid.TOA : Raid.NONE;
	}

	/** Returns a scaled copy; the cached API monster is never changed. */
	public static Monster apply(Monster base, Settings s)
	{
		Monster m = base.copy();
		switch (raid(base))
		{
			case COX:
				cox(m, s);
				break;
			case TOB:
				m.setHitpoints(m.getHitpoints() * tobHitpointsPerMille(s.getPartySize()) / 1000);
				break;
			case TOB_ENTRY:
				m.setHitpoints(m.getHitpoints() * ENTRY_FORTIETHS[clamp(s.getPartySize(), 1, 5)] / 40);
				break;
			case TOA:
				toa(m, s);
				break;
			default:
				break;
		}
		return m;
	}

	/**
	 * Theatre of Blood (Wiki "Theatre of Blood"): bosses have 75% of their hitpoints with three or fewer
	 * players, 87.5% with four and 100% with five.
	 */
	private static int tobHitpointsPerMille(int party)
	{
		return party >= 5 ? 1000 : party == 4 ? 875 : 750;
	}

	/** Entry mode hitpoints in fortieths for one to five players (game values; the Wiki gives no table). */
	private static final int[] ENTRY_FORTIETHS = {0, 10, 19, 27, 34, 40};

	// ------------------------------------------------------------------ Tombs of Amascut

	/**
	 * Tombs of Amascut hitpoints (Wiki "Tombs of Amascut"), applied in order: +2% per 5 raid levels; +8% for
	 * the first path level and +5% per further level; +90% of the base for each of the 2nd and 3rd party
	 * members and +60% for each member after that. The ejected Wardens' core (4,500 hitpoints, +0.1% per raid
	 * level) and the final rounding (to 5 above 100 hitpoints, to 10 above 300) are game values the Wiki pages
	 * do not state.
	 */
	private static void toa(Monster m, Settings s)
	{
		boolean core = CombatRules.wardenCore(m);
		int hp = core ? 4500 : m.getHitpoints();
		int raidLevel = clamp(s.getToaRaidLevel(), 0, 600);
		int raidPercent = core ? raidLevel / 10 : raidLevel * 2 / 5;
		hp += hp * raidPercent / 100;
		int path = clamp(s.getToaPathLevel(), 0, 6);
		if (toaPath(m) && path >= 1)
		{
			int pathPercent = 8 + 5 * (path - 1);
			hp = hp * (100 + pathPercent) / 100;
		}
		int party = clamp(s.getPartySize(), 1, 8);
		int partyPercent = 90 * Math.min(party - 1, 2) + 60 * Math.max(party - 3, 0);
		hp = hp * (100 + partyPercent) / 100;
		if (hp > 100)
		{
			hp = roundToNearest(hp, hp > 300 ? 10 : 5);
		}
		m.setHitpoints(hp);
		// Kephri's overlord scarabs keep their HP scaling but not the defence-roll multiplier.
		m.setToaRaidLevel(overlord(m) ? 0 : raidLevel);
	}

	/** The reference scales ToA bosses and their path minions, not puzzle-room NPCs such as baboons. */
	static boolean toaScaled(Monster m)
	{
		String name = m.getName().toLowerCase(Locale.ROOT);
		int id = m.getId();
		if (!CombatRules.toa(m) && !(id >= 11719 && id <= 11764 || id >= 11778 && id <= 11797))
		{
			return false;
		}
		return toaPath(m) || name.contains("warden") || name.startsWith("obelisk")
			|| id >= 11750 && id <= 11764 && !name.contains("baboon");
	}

	/** Whether this ToA target is affected by path levels. */
	public static boolean toaPath(Monster m)
	{
		String name = m.getName().toLowerCase(Locale.ROOT);
		return name.startsWith("akkha") || name.startsWith("ba-ba") || name.startsWith("kephri")
			|| name.startsWith("zebak") || overlord(m);
	}

	static boolean overlord(Monster m)
	{
		String name = m.getName().toLowerCase(Locale.ROOT);
		return name.startsWith("soldier scarab") || name.startsWith("spitting scarab")
			|| name.startsWith("arcane scarab");
	}

	// ------------------------------------------------------------------ Theatre of Blood

	/** Entry-mode IDs from the reference calculator, used only when the name does not identify the mode. */
	private static final Set<Integer> TOB_ENTRY_IDS = new HashSet<>(Arrays.asList(
		10814, 10815, 10816, 10817, 10818, 10819, 10820, 10821, 10812, 10774, 10775, 10776, 10777, 10778, 10779,
		10780, 10781, 10782, 10783, 10784, 10785, 10787, 10788, 10789, 10864, 10865, 10767, 10768, 10833, 10834,
		10835, 10837, 10841, 10842, 10843, 10844, 10845));

	// ------------------------------------------------------------------ Chambers of Xeric

	/*
	 * Chambers of Xeric. The Wiki pages say only that monsters scale with the party; these are the game's values
	 * as implemented by the OSRS Wiki DPS calculator at the audited revision. Each target has two linked level
	 * groups that scale as one: offence (Attack, Strength, Ranged, and Magic unless Magic counts as defence) and
	 * defence (Defence, plus Magic where it is defensive). A level of 1 never scales. Every step truncates.
	 */

	/** Party-scaled offence, as a percentage, by the number of extra party members. */
	private static int coxOffencePercent(int extraMembers)
	{
		return 100 + 7 * isqrt(extraMembers) + extraMembers;
	}

	/** Party-scaled defence, as a percentage, by the number of extra party members. */
	private static int coxDefencePercent(int extraMembers)
	{
		return 100 + isqrt(extraMembers) + extraMembers * 7 / 10;
	}

	private static void cox(Monster m, Settings s)
	{
		boolean magicDefends = coxMagicIsDefensive(m);
		int offence = highestScalingLevel(m.getAttackLevel(), m.getStrengthLevel(), m.getRangedLevel(),
			magicDefends ? 1 : m.getMagicLevel());
		int defence = highestScalingLevel(m.getDefenceLevel(), magicDefends ? m.getMagicLevel() : 1);
		boolean guardian = EncounterDamage.guardian(m) || EncounterDamage.named(m, "guardian");
		int hp = guardian ? 151 + clamp(s.getPartyAverageMining(), 1, 99) : m.getHitpoints();

		int[] scaled = coxScaledAlone(m) ? coxSolo(offence, defence, hp, s) : coxParty(m, offence, defence, hp, s);
		offence = scaled[0];
		defence = scaled[1];
		m.setHitpoints(scaled[2]);
		m.setAttackLevel(m.getAttackLevel() == 1 ? 1 : offence);
		m.setStrengthLevel(m.getStrengthLevel() == 1 ? 1 : offence);
		m.setRangedLevel(m.getRangedLevel() == 1 ? 1 : offence);
		m.setDefenceLevel(m.getDefenceLevel() == 1 ? 1 : defence);
		m.setMagicLevel(m.getMagicLevel() == 1 ? 1 : magicDefends ? defence : offence);

		if (olm(m))
		{
			// Olm's hitpoints replace the scaled value: a base per part plus a share per member, less three
			// members' worth for each extra phase (one per eight players). The mage hand's Magic is halved.
			int party = clamp(s.getPartySize(), 1, 100);
			int members = Math.min(party - 1, 50) - 3 * (Math.min(party, 50) / 8);
			m.setHitpoints(olmHand(m) ? 600 + 300 * members : 800 + 400 * members);
			if (olmMageHand(m))
			{
				m.setMagicLevel(m.getMagicLevel() / 2);
			}
		}
	}

	/** Scavenger beasts and vespine soldiers scale with the party's levels only, not its size. */
	private static int[] coxSolo(int offence, int defence, int hp, Settings s)
	{
		int levelScale = clamp(s.getPartyMaxHitpoints(), 55, 99);
		int hpScale = clamp(s.getPartyMaxCombat(), 60, 126);
		if (s.isCoxChallengeMode())
		{
			levelScale = addPercent(levelScale, COX_CM_PERCENT);
			hpScale = addPercent(hpScale, COX_CM_PERCENT);
		}
		return new int[]{
			Math.max(1, offence * levelScale / 99),
			Math.max(1, defence * levelScale / 99),
			Math.max(5, hp * hpScale / 126),
		};
	}

	/** Everything else scales with the party's highest levels and its size. */
	private static int[] coxParty(Monster m, int offence, int defence, int hp, Settings s)
	{
		int party = clamp(s.getPartySize(), 1, 100);
		int extra = party - 1;
		// The highest Hitpoints level maps onto 55-99 for levels; the highest combat level (60-126) scales HP.
		int levelScale = clamp(55 + 44 * s.getPartyMaxHitpoints() / 99, 55, 99);
		int hpScale = clamp(s.getPartyMaxCombat(), 60, 126);

		offence = (offence * levelScale / 99) * coxOffencePercent(extra) / 100;
		defence = (defence * levelScale / 99) * coxDefencePercent(extra) / 100;
		hp = hp * hpScale / 126;
		hp += hp * (party / 2);

		if (s.isCoxChallengeMode())
		{
			// Challenge Mode: +50% to everything, except that Tekton's defence gains 20% (35% from four players)
			// and the glowing crystal keeps its hitpoints and defence.
			boolean crystal = EncounterDamage.crystal(m);
			offence = addPercent(offence, COX_CM_PERCENT);
			if (!crystal)
			{
				hp = addPercent(hp, COX_CM_PERCENT);
				defence = addPercent(defence, EncounterDamage.tekton(m) ? party < 4 ? 20 : 35 : COX_CM_PERCENT);
			}
		}
		return new int[]{clamp(offence, 50, 5_000), clamp(defence, 50, 20_000), clamp(hp, 50, 30_000)};
	}

	/** The highest of the linked levels that are not 1, or 1 if all are. */
	private static int highestScalingLevel(int... levels)
	{
		int highest = 1;
		for (int level : levels)
		{
			if (level != 1)
			{
				highest = Math.max(highest, level);
			}
		}
		return highest;
	}

	/** Targets whose Magic level counts as a defensive level. */
	private static boolean coxMagicIsDefensive(Monster m)
	{
		String name = m.getName().toLowerCase(Locale.ROOT);
		int id = m.getId();
		return EncounterDamage.tekton(m) || olmHand(m) || id == 7533 || id == 7559
			|| id >= 7530 && id <= 7532 || id == 7538 || id == 7539 || name.startsWith("abyssal portal")
			|| name.startsWith("deathly ranger") || EncounterDamage.named(m, "vespula")
			|| name.startsWith("vespine soldier");
	}

	private static boolean coxScaledAlone(Monster m)
	{
		String name = m.getName().toLowerCase(Locale.ROOT);
		int id = m.getId();
		return id == 7548 || id == 7549 || id == 7538 || id == 7539
			|| name.startsWith("scavenger beast") || name.startsWith("vespine soldier");
	}

	private static int isqrt(int value)
	{
		return (int) Math.sqrt(value);
	}

	private static boolean olm(Monster m)
	{
		int id = m.getId();
		return id >= 7550 && id <= 7555 || m.getName().toLowerCase(Locale.ROOT).startsWith("great olm");
	}

	private static boolean olmHand(Monster m)
	{
		String name = m.getName().toLowerCase(Locale.ROOT);
		int id = m.getId();
		return olm(m) && (name.contains("hand") || !name.startsWith("great olm") && id != 7551 && id != 7554);
	}

	private static boolean olmMageHand(Monster m)
	{
		String name = m.getName().toLowerCase(Locale.ROOT);
		int id = m.getId();
		return name.contains("mage hand") || !name.startsWith("great olm") && (id == 7550 || id == 7553);
	}

	// ------------------------------------------------------------------ helpers

	/** One-line summary of the applied scaling, or null for non-raid targets. */
	public static String describe(Monster base, Monster scaled, Settings s)
	{
		switch (raid(base))
		{
			case COX:
				return String.format("CoX scaling: party %d, highest combat %d, highest HP %d%s; HP %d -> %d, Defence %d -> %d.",
					clamp(s.getPartySize(), 1, 100), clamp(s.getPartyMaxCombat(), 3, 126), s.getPartyMaxHitpoints(),
					s.isCoxChallengeMode() ? ", Challenge Mode" : "", base.getHitpoints(), scaled.getHitpoints(),
					base.getDefenceLevel(), scaled.getDefenceLevel());
			case TOB:
			case TOB_ENTRY:
				return String.format("ToB %sHP scaling: party %d (%s); HP %d -> %d.",
					raid(base) == Raid.TOB_ENTRY ? "entry-mode " : "", s.getPartySize(),
					raid(base) == Raid.TOB_ENTRY ? "1-5" : "below 3 counts as 3",
					base.getHitpoints(), scaled.getHitpoints());
			case TOA:
				return String.format("ToA scaling: raid level %d, path level %d, party %d; HP %d -> %d%s.",
					clamp(s.getToaRaidLevel(), 0, 600), clamp(s.getToaPathLevel(), 0, 6), clamp(s.getPartySize(), 1, 8),
					base.getHitpoints(), scaled.getHitpoints(),
					scaled.getToaRaidLevel() > 0 ? ", defence roll x" + (250 + scaled.getToaRaidLevel()) + "/250" : "");
			default:
				if (CombatRules.toa(base))
				{
					return "ToA: this puzzle-room target uses its unscaled stats.";
				}
				return null;
		}
	}

	private static int roundToNearest(int value, int step)
	{
		return (value + step / 2) / step * step;
	}

	private static int addPercent(int value, int percent)
	{
		return value + value * percent / 100;
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
