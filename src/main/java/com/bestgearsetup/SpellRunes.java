package com.bestgearsetup;

import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SupportSpell;
import com.bestgearsetup.calc.Thrall;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.ItemID;

/**
 * The rune types a setup's combat and support spells consume, less any the equipped staff or tome
 * supplies, plus the Book of the dead a thrall needs.
 */
final class SpellRunes
{
	private static final int AIR = ItemID.AIRRUNE;
	private static final int WATER = ItemID.WATERRUNE;
	private static final int EARTH = ItemID.EARTHRUNE;
	private static final int FIRE = ItemID.FIRERUNE;
	private static final int MIND = ItemID.MINDRUNE;
	private static final int CHAOS = ItemID.CHAOSRUNE;
	private static final int DEATH = ItemID.DEATHRUNE;
	private static final int BLOOD = ItemID.BLOODRUNE;
	private static final int SOUL = ItemID.SOULRUNE;
	private static final int WRATH = ItemID.WRATHRUNE;
	private static final int COSMIC = ItemID.COSMICRUNE;
	private static final Map<String, Map<Integer, Integer>> SPELLS = new LinkedHashMap<>();
	private static final Map<SupportSpell, Map<Integer, Integer>> SUPPORT = new EnumMap<>(SupportSpell.class);
	private static final Map<Thrall, Map<Integer, Integer>> THRALL_COSTS = new EnumMap<>(Thrall.class);
	private static final Map<Integer, List<Integer>> SUBSTITUTES = new HashMap<>();
	private static final Map<String, List<Integer>> STAFF_ELEMENTS = new HashMap<>();
	// "air battlestaff", "mystic dust staff", "staff of fire"; sceptres and other staves supply nothing.
	private static final Pattern ELEMENTAL_STAFF = Pattern.compile("(?:mystic )?(\\w+) (?:battle)?staff\\b.*");
	private static final Pattern BASIC_STAFF = Pattern.compile("staff of (\\w+)");
	private static final Pattern TOME = Pattern.compile("tome of (\\w+)");

	static
	{
		spell("wind strike", AIR, 1, MIND, 1);
		spell("smoke rush", AIR, 1, FIRE, 1, CHAOS, 2, DEATH, 2);
		spell("water strike", AIR, 1, WATER, 1, MIND, 1);
		spell("shadow rush", AIR, 1, CHAOS, 2, DEATH, 2, SOUL, 1);
		spell("earth strike", AIR, 1, EARTH, 2, MIND, 1);
		spell("blood rush", CHAOS, 2, DEATH, 2, BLOOD, 1);
		spell("fire strike", AIR, 2, FIRE, 3, MIND, 1);
		spell("ice rush", WATER, 2, CHAOS, 2, DEATH, 2);
		spell("wind bolt", AIR, 2, CHAOS, 1);
		spell("smoke burst", AIR, 2, FIRE, 2, CHAOS, 4, DEATH, 2);
		spell("water bolt", AIR, 2, WATER, 2, CHAOS, 1);
		spell("shadow burst", AIR, 1, CHAOS, 4, DEATH, 2, SOUL, 2);
		spell("earth bolt", AIR, 2, EARTH, 3, CHAOS, 1);
		spell("blood burst", CHAOS, 4, DEATH, 2, BLOOD, 2);
		spell("fire bolt", AIR, 3, FIRE, 4, CHAOS, 1);
		spell("ice burst", WATER, 4, CHAOS, 4, DEATH, 2);
		spell("wind blast", AIR, 3, DEATH, 1);
		spell("smoke blitz", AIR, 2, FIRE, 2, DEATH, 2, BLOOD, 2);
		spell("water blast", AIR, 3, WATER, 3, DEATH, 1);
		spell("shadow blitz", AIR, 2, DEATH, 2, BLOOD, 2, SOUL, 2);
		spell("earth blast", AIR, 3, EARTH, 4, DEATH, 1);
		spell("blood blitz", DEATH, 2, BLOOD, 4);
		spell("fire blast", AIR, 4, FIRE, 5, DEATH, 1);
		spell("ice blitz", WATER, 3, DEATH, 2, BLOOD, 2);
		spell("wind wave", AIR, 5, BLOOD, 1);
		spell("smoke barrage", AIR, 4, FIRE, 4, DEATH, 4, BLOOD, 2);
		spell("water wave", AIR, 5, WATER, 7, BLOOD, 1);
		spell("shadow barrage", AIR, 4, DEATH, 4, BLOOD, 2, SOUL, 3);
		spell("earth wave", AIR, 5, EARTH, 7, BLOOD, 1);
		spell("blood barrage", DEATH, 4, BLOOD, 4, SOUL, 1);
		spell("fire wave", AIR, 5, FIRE, 7, BLOOD, 1);
		spell("ice barrage", WATER, 6, DEATH, 4, BLOOD, 2);
		spell("wind surge", AIR, 7, WRATH, 1);
		spell("ghostly grasp", AIR, 4, CHAOS, 1);
		spell("water surge", AIR, 7, WATER, 10, WRATH, 1);
		spell("skeletal grasp", EARTH, 8, DEATH, 1);
		spell("earth surge", AIR, 7, EARTH, 10, WRATH, 1);
		spell("undead grasp", FIRE, 12, BLOOD, 1);
		spell("fire surge", AIR, 7, FIRE, 10, WRATH, 1);
		spell("inferior demonbane", FIRE, 3, CHAOS, 1);
		spell("crumble undead", AIR, 2, EARTH, 2, CHAOS, 1);
		spell("superior demonbane", FIRE, 5, SOUL, 1);
		spell("iban blast", FIRE, 5, DEATH, 1);
		spell("dark demonbane", FIRE, 7, SOUL, 2);
		spell("magic dart", MIND, 4, DEATH, 1);
		spell("saradomin strike", AIR, 4, FIRE, 2, BLOOD, 2);
		spell("claws of guthix", AIR, 4, FIRE, 1, BLOOD, 2);
		spell("flames of zamorak", AIR, 1, FIRE, 4, BLOOD, 2);
		support(SupportSpell.MARK_OF_DARKNESS, COSMIC, 1, SOUL, 1);
		support(SupportSpell.CHARGE, AIR, 3, FIRE, 3, BLOOD, 3);
		thrall(Thrall.LESSER, AIR, 10, MIND, 5, COSMIC, 1);
		thrall(Thrall.SUPERIOR, EARTH, 10, DEATH, 5, COSMIC, 1);
		thrall(Thrall.GREATER, FIRE, 10, BLOOD, 5, COSMIC, 1);

		SUBSTITUTES.put(AIR, Arrays.asList(ItemID.MISTRUNE, ItemID.DUSTRUNE, ItemID.SMOKERUNE));
		SUBSTITUTES.put(WATER, Arrays.asList(ItemID.MISTRUNE, ItemID.MUDRUNE, ItemID.STEAMRUNE));
		SUBSTITUTES.put(EARTH, Arrays.asList(ItemID.DUSTRUNE, ItemID.MUDRUNE, ItemID.LAVARUNE));
		SUBSTITUTES.put(FIRE, Arrays.asList(ItemID.SUNFIRERUNE, ItemID.SMOKERUNE, ItemID.STEAMRUNE,
			ItemID.LAVARUNE));
		SUBSTITUTES.put(SOUL, Collections.singletonList(ItemID.AETHERRUNE));
		SUBSTITUTES.put(COSMIC, Collections.singletonList(ItemID.AETHERRUNE));

		STAFF_ELEMENTS.put("air", Collections.singletonList(AIR));
		STAFF_ELEMENTS.put("water", Collections.singletonList(WATER));
		STAFF_ELEMENTS.put("earth", Collections.singletonList(EARTH));
		STAFF_ELEMENTS.put("fire", Collections.singletonList(FIRE));
		STAFF_ELEMENTS.put("mist", Arrays.asList(AIR, WATER));
		STAFF_ELEMENTS.put("dust", Arrays.asList(AIR, EARTH));
		STAFF_ELEMENTS.put("smoke", Arrays.asList(AIR, FIRE));
		STAFF_ELEMENTS.put("mud", Arrays.asList(WATER, EARTH));
		STAFF_ELEMENTS.put("steam", Arrays.asList(WATER, FIRE));
		STAFF_ELEMENTS.put("lava", Arrays.asList(EARTH, FIRE));
	}

	private SpellRunes()
	{
	}

	private static void spell(String name, int... pairs)
	{
		SPELLS.put(name, cost(pairs));
	}

	private static void support(SupportSpell spell, int... pairs)
	{
		SUPPORT.put(spell, cost(pairs));
	}

	private static void thrall(Thrall tier, int... pairs)
	{
		THRALL_COSTS.put(tier, cost(pairs));
	}

	private static Map<Integer, Integer> cost(int... pairs)
	{
		Map<Integer, Integer> cost = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			cost.put(pairs[i], pairs[i + 1]);
		}
		return Collections.unmodifiableMap(cost);
	}

	/** Rune quantities for one cast of a combat spell, in spellbook order. */
	static Map<Integer, Integer> perCast(String spellName)
	{
		return spellName == null ? Collections.emptyMap()
			: SPELLS.getOrDefault(spellName.toLowerCase(Locale.ROOT), Collections.emptyMap());
	}

	/** Rune quantities for one support spell cast; a missing thrall tier means greater. */
	static Map<Integer, Integer> supportPerCast(SupportSpell spell, Thrall tier)
	{
		if (spell == SupportSpell.THRALL)
		{
			return THRALL_COSTS.get(tier == null ? Thrall.GREATER : tier);
		}
		return SUPPORT.getOrDefault(spell, Collections.emptyMap());
	}

	/** Duration in seconds of one support spell cast, without Master Combat Achievements rewards. */
	static double supportDurationSeconds(SupportSpell spell, Loadout loadout, int baseMagic, int boostedMagic)
	{
		switch (spell)
		{
			case MARK_OF_DARKNESS:
				return 0.6 * 3 * baseMagic * (name(loadout == null ? null : loadout.get(Slot.WEAPON))
					.startsWith("purging staff") ? 5 : 1);
			case CHARGE:
				return 420.0;
			case THRALL:
				return 0.6 * boostedMagic;
			default:
				throw new IllegalArgumentException("Unknown support spell: " + spell);
		}
	}

	/** Expected fraction of attack casts whose runes are saved by the weapon. */
	static double runeSaveChance(Loadout loadout)
	{
		String weapon = name(loadout == null ? null : loadout.get(Slot.WEAPON));
		if ("kodai wand".equals(weapon))
		{
			return 0.15;
		}
		switch (weapon)
		{
			case "staff of the dead":
			case "toxic staff of the dead":
			case "staff of light":
			case "staff of balance":
				return 1.0 / 7;
			default:
				return 0.0;
		}
	}

	/** A short base-rune name used in estimates; unknown ids remain generic. */
	static String runeName(int runeId)
	{
		switch (runeId)
		{
			case AIR:
				return "air";
			case WATER:
				return "water";
			case EARTH:
				return "earth";
			case FIRE:
				return "fire";
			case MIND:
				return "mind";
			case CHAOS:
				return "chaos";
			case DEATH:
				return "death";
			case BLOOD:
				return "blood";
			case SOUL:
				return "soul";
			case WRATH:
				return "wrath";
			case COSMIC:
				return "cosmic";
			default:
				return "rune";
		}
	}

	/** Item ids to bring for the setup's spells: the attack spell's runes first, then its support spells'. */
	static List<Integer> required(SetupResult setup)
	{
		return setup == null ? Collections.emptyList()
			: required(setup.getLoadout(), setup.getSupportSpells(), setup.getThrall());
	}

	static List<Integer> required(Loadout loadout, Set<SupportSpell> support)
	{
		return required(loadout, support, null);
	}

	static List<Integer> required(Loadout loadout, Set<SupportSpell> support, Thrall tier)
	{
		if (loadout == null)
		{
			return Collections.emptyList();
		}
		Set<Integer> items = new LinkedHashSet<>();
		if (loadout.getSpell() != null)
		{
			items.addAll(perCast(loadout.getSpell().getName()).keySet());
		}
		for (SupportSpell spell : support)
		{
			items.addAll(supportPerCast(spell, tier).keySet());
			if (spell == SupportSpell.THRALL)
			{
				items.add(ItemID.BOOK_OF_THE_DEAD);
			}
		}
		items.removeAll(supplied(loadout));
		return new ArrayList<>(items);
	}

	/** Combination, sunfire and aether runes that can be cast in place of {@code rune}. */
	static List<Integer> substitutes(int rune)
	{
		return SUBSTITUTES.getOrDefault(rune, Collections.emptyList());
	}

	/** Elemental runes the equipped staff or tome provides without limit. */
	static Set<Integer> supplied(Loadout loadout)
	{
		Set<Integer> runes = new HashSet<>();
		String weapon = name(loadout.get(Slot.WEAPON));
		if (weapon.startsWith("kodai wand"))
		{
			runes.add(WATER);
		}
		else if (weapon.startsWith("twinflame staff"))
		{
			runes.add(WATER);
			runes.add(FIRE);
		}
		else
		{
			addElements(runes, BASIC_STAFF.matcher(weapon));
			addElements(runes, ELEMENTAL_STAFF.matcher(weapon));
		}
		// Only a charged tome matches; "tome of fire (empty)" supplies nothing.
		addElements(runes, TOME.matcher(name(loadout.get(Slot.SHIELD))));
		return runes;
	}

	private static void addElements(Set<Integer> runes, Matcher matcher)
	{
		if (matcher.matches())
		{
			runes.addAll(STAFF_ELEMENTS.getOrDefault(matcher.group(1), Collections.emptyList()));
		}
	}

	private static String name(GearItem item)
	{
		return item == null ? "" : item.getName().toLowerCase(Locale.ROOT);
	}
}
