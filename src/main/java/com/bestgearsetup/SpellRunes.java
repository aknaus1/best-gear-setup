package com.bestgearsetup;

import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SupportSpell;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.HashSet;
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
	private static final Map<String, List<Integer>> SPELLS = new HashMap<>();
	private static final Map<SupportSpell, List<Integer>> SUPPORT = new EnumMap<>(SupportSpell.class);
	private static final Map<Integer, List<Integer>> SUBSTITUTES = new HashMap<>();
	private static final Map<String, List<Integer>> STAFF_ELEMENTS = new HashMap<>();
	// "air battlestaff", "mystic dust staff", "staff of fire"; sceptres and other staves supply nothing.
	private static final Pattern ELEMENTAL_STAFF = Pattern.compile("(?:mystic )?(\\w+) (?:battle)?staff\\b.*");
	private static final Pattern BASIC_STAFF = Pattern.compile("staff of (\\w+)");
	private static final Pattern TOME = Pattern.compile("tome of (\\w+)");

	static
	{
		spell("wind strike", AIR, MIND);
		spell("water strike", AIR, WATER, MIND);
		spell("earth strike", AIR, EARTH, MIND);
		spell("fire strike", AIR, FIRE, MIND);
		spell("wind bolt", AIR, CHAOS);
		spell("water bolt", AIR, WATER, CHAOS);
		spell("earth bolt", AIR, EARTH, CHAOS);
		spell("fire bolt", AIR, FIRE, CHAOS);
		spell("wind blast", AIR, DEATH);
		spell("water blast", AIR, WATER, DEATH);
		spell("earth blast", AIR, EARTH, DEATH);
		spell("fire blast", AIR, FIRE, DEATH);
		spell("wind wave", AIR, BLOOD);
		spell("water wave", AIR, WATER, BLOOD);
		spell("earth wave", AIR, EARTH, BLOOD);
		spell("fire wave", AIR, FIRE, BLOOD);
		spell("wind surge", AIR, WRATH);
		spell("water surge", AIR, WATER, WRATH);
		spell("earth surge", AIR, EARTH, WRATH);
		spell("fire surge", AIR, FIRE, WRATH);
		spell("crumble undead", AIR, EARTH, CHAOS);
		spell("iban blast", FIRE, DEATH);
		spell("magic dart", MIND, DEATH);
		spell("saradomin strike", AIR, FIRE, BLOOD);
		spell("claws of guthix", AIR, FIRE, BLOOD);
		spell("flames of zamorak", AIR, FIRE, BLOOD);
		spell("smoke rush", AIR, FIRE, CHAOS, DEATH);
		spell("shadow rush", AIR, CHAOS, DEATH, SOUL);
		spell("blood rush", CHAOS, DEATH, BLOOD);
		spell("ice rush", WATER, CHAOS, DEATH);
		spell("smoke burst", AIR, FIRE, CHAOS, DEATH);
		spell("shadow burst", AIR, CHAOS, DEATH, SOUL);
		spell("blood burst", CHAOS, DEATH, BLOOD);
		spell("ice burst", WATER, CHAOS, DEATH);
		spell("smoke blitz", AIR, FIRE, DEATH, BLOOD);
		spell("shadow blitz", AIR, DEATH, BLOOD, SOUL);
		spell("blood blitz", DEATH, BLOOD);
		spell("ice blitz", WATER, DEATH, BLOOD);
		spell("smoke barrage", AIR, FIRE, DEATH, BLOOD);
		spell("shadow barrage", AIR, DEATH, BLOOD, SOUL);
		spell("blood barrage", DEATH, BLOOD, SOUL);
		spell("ice barrage", WATER, DEATH, BLOOD);
		spell("ghostly grasp", AIR, CHAOS);
		spell("skeletal grasp", EARTH, DEATH);
		spell("undead grasp", FIRE, BLOOD);
		spell("inferior demonbane", FIRE, CHAOS);
		spell("superior demonbane", FIRE, SOUL);
		spell("dark demonbane", FIRE, SOUL);
		// Every greater thrall costs the same runes and needs the Book of the dead equipped or carried.
		SUPPORT.put(SupportSpell.THRALL, Arrays.asList(FIRE, BLOOD, COSMIC, ItemID.BOOK_OF_THE_DEAD));
		SUPPORT.put(SupportSpell.MARK_OF_DARKNESS, Arrays.asList(COSMIC, SOUL));
		SUPPORT.put(SupportSpell.CHARGE, Arrays.asList(AIR, FIRE, BLOOD));

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

	private static void spell(String name, Integer... runes)
	{
		SPELLS.put(name, Collections.unmodifiableList(Arrays.asList(runes)));
	}

	/** Item ids to bring for the setup's spells: the attack spell's runes first, then its support spells'. */
	static List<Integer> required(SetupResult setup)
	{
		return setup == null ? Collections.emptyList() : required(setup.getLoadout(), setup.getSupportSpells());
	}

	static List<Integer> required(Loadout loadout, Set<SupportSpell> support)
	{
		if (loadout == null)
		{
			return Collections.emptyList();
		}
		Set<Integer> items = new LinkedHashSet<>();
		if (loadout.getSpell() != null)
		{
			items.addAll(SPELLS.getOrDefault(loadout.getSpell().getName().toLowerCase(Locale.ROOT),
				Collections.emptyList()));
		}
		for (SupportSpell spell : support)
		{
			items.addAll(SUPPORT.get(spell));
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
	private static Set<Integer> supplied(Loadout loadout)
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
