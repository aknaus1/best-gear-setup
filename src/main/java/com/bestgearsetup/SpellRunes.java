package com.bestgearsetup;

import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SupportSpell;
import com.bestgearsetup.calc.Thrall;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.NameLists;
import com.bestgearsetup.data.Slot;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import net.runelite.api.gameval.SpriteID;

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
	/** The base runes, in the order results list them, and their short names. */
	static final List<Integer> RUNES = List.of(AIR, WATER, EARTH, FIRE, MIND, CHAOS, DEATH, BLOOD, SOUL, WRATH, COSMIC);
	private static final List<String> RUNE_NAMES = List.of("air", "water", "earth", "fire", "mind", "chaos", "death",
		"blood", "soul", "wrath", "cosmic");
	private static final Map<String, Map<Integer, Integer>> SPELLS = new HashMap<>();
	private static final Map<String, Integer> SPRITES = new HashMap<>();
	/** Rune pouches, best first; the bank can't see the runes inside them. */
	static final List<Integer> POUCHES = Collections.unmodifiableList(Arrays.asList(ItemID.DIVINE_RUNE_POUCH,
		ItemID.DIVINE_RUNE_POUCH_TROUVER, ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER));
	private static final Map<String, Map<Integer, Integer>> SUPPORT = new HashMap<>();
	private static final Map<Integer, List<Integer>> SUBSTITUTES = new HashMap<>();
	private static final Map<String, List<Integer>> STAFF_ELEMENTS = new HashMap<>();
	// "air battlestaff", "mystic dust staff", "staff of fire"; sceptres and other staves supply nothing.
	private static final Pattern ELEMENTAL_STAFF = Pattern.compile("(?:mystic )?(\\w+) (?:battle)?staff\\b.*");
	private static final Pattern BASIC_STAFF = Pattern.compile("staff of (\\w+)");
	private static final Pattern TOME = Pattern.compile("tome of (\\w+)");

	static
	{
		for (JsonElement row : NameLists.rows("spell runes"))
		{
			SPELLS.put(row.getAsJsonArray().get(0).getAsString(), cost(row.getAsJsonArray()));
		}
		for (JsonElement row : NameLists.rows("support spell runes"))
		{
			SUPPORT.put(row.getAsJsonArray().get(0).getAsString(), cost(row.getAsJsonArray()));
		}
		for (JsonElement row : NameLists.rows("staff runes"))
		{
			List<Integer> runes = new ArrayList<>();
			for (int i = 1; i < row.getAsJsonArray().size(); i++)
			{
				runes.add(rune(row.getAsJsonArray().get(i).getAsString()));
			}
			STAFF_ELEMENTS.put(row.getAsJsonArray().get(0).getAsString(), runes);
		}
		// Standard spellbook
		SPRITES.put("wind strike", SpriteID.Magicon.WIND_STRIKE);
		SPRITES.put("water strike", SpriteID.Magicon.WATER_STRIKE);
		SPRITES.put("earth strike", SpriteID.Magicon.EARTH_STRIKE);
		SPRITES.put("fire strike", SpriteID.Magicon.FIRE_STRIKE);
		SPRITES.put("wind bolt", SpriteID.Magicon.WIND_BOLT);
		SPRITES.put("water bolt", SpriteID.Magicon.WATER_BOLT);
		SPRITES.put("earth bolt", SpriteID.Magicon.EARTH_BOLT);
		SPRITES.put("fire bolt", SpriteID.Magicon.FIRE_BOLT);
		SPRITES.put("wind blast", SpriteID.Magicon.WIND_BLAST);
		SPRITES.put("water blast", SpriteID.Magicon.WATER_BLAST);
		SPRITES.put("earth blast", SpriteID.Magicon.EARTH_BLAST);
		SPRITES.put("fire blast", SpriteID.Magicon.FIRE_BLAST);
		SPRITES.put("wind wave", SpriteID.Magicon.WIND_WAVE);
		SPRITES.put("water wave", SpriteID.Magicon.WATER_WAVE);
		SPRITES.put("earth wave", SpriteID.Magicon.EARTH_WAVE);
		SPRITES.put("fire wave", SpriteID.Magicon.FIRE_WAVE);
		SPRITES.put("wind surge", SpriteID.Magicon2.WIND_SURGE);
		SPRITES.put("water surge", SpriteID.Magicon2.WATER_SURGE);
		SPRITES.put("earth surge", SpriteID.Magicon2.EARTH_SURGE);
		SPRITES.put("fire surge", SpriteID.Magicon2.FIRE_SURGE);
		SPRITES.put("crumble undead", SpriteID.Magicon.CRUMBLE_UNDEAD);
		SPRITES.put("iban blast", SpriteID.Magicon.IBAN_BLAST);
		SPRITES.put("magic dart", SpriteID.Magicon2.MAGIC_DART);
		SPRITES.put("saradomin strike", SpriteID.Magicon.SARADOMIN_STRIKE);
		SPRITES.put("claws of guthix", SpriteID.Magicon.CLAWS_OF_GUTHIX);
		SPRITES.put("flames of zamorak", SpriteID.Magicon.FLAMES_OF_ZAMORAK);

		// Ancient spellbook
		SPRITES.put("smoke rush", SpriteID.Magicon2.SMOKE_RUSH);
		SPRITES.put("shadow rush", SpriteID.Magicon2.SHADOW_RUSH);
		SPRITES.put("blood rush", SpriteID.Magicon2.BLOOD_RUSH);
		SPRITES.put("ice rush", SpriteID.Magicon2.ICE_RUSH);
		SPRITES.put("smoke burst", SpriteID.Magicon2.SMOKE_BURST);
		SPRITES.put("shadow burst", SpriteID.Magicon2.SHADOW_BURST);
		SPRITES.put("blood burst", SpriteID.Magicon2.BLOOD_BURST);
		SPRITES.put("ice burst", SpriteID.Magicon2.ICE_BURST);
		SPRITES.put("smoke blitz", SpriteID.Magicon2.SMOKE_BLITZ);
		SPRITES.put("shadow blitz", SpriteID.Magicon2.SHADOW_BLITZ);
		SPRITES.put("blood blitz", SpriteID.Magicon2.BLOOD_BLITZ);
		SPRITES.put("ice blitz", SpriteID.Magicon2.ICE_BLITZ);
		SPRITES.put("smoke barrage", SpriteID.Magicon2.SMOKE_BARRAGE);
		SPRITES.put("shadow barrage", SpriteID.Magicon2.SHADOW_BARRAGE);
		SPRITES.put("blood barrage", SpriteID.Magicon2.BLOOD_BARRAGE);
		SPRITES.put("ice barrage", SpriteID.Magicon2.ICE_BARRAGE);

		// Arceuus spellbook
		SPRITES.put("ghostly grasp", SpriteID.MagicNecroOn.GHOSTLY_GRASP);
		SPRITES.put("skeletal grasp", SpriteID.MagicNecroOn.SKELETAL_GRASP);
		SPRITES.put("undead grasp", SpriteID.MagicNecroOn.UNDEAD_GRASP);
		SPRITES.put("inferior demonbane", SpriteID.MagicNecroOn.INFERIOR_DEMONBANE);
		SPRITES.put("superior demonbane", SpriteID.MagicNecroOn.SUPERIOR_DEMONBANE);
		SPRITES.put("dark demonbane", SpriteID.MagicNecroOn.DARK_DEMONBANE);

		SUBSTITUTES.put(AIR, Arrays.asList(ItemID.MISTRUNE, ItemID.DUSTRUNE, ItemID.SMOKERUNE));
		SUBSTITUTES.put(WATER, Arrays.asList(ItemID.MISTRUNE, ItemID.MUDRUNE, ItemID.STEAMRUNE));
		SUBSTITUTES.put(EARTH, Arrays.asList(ItemID.DUSTRUNE, ItemID.MUDRUNE, ItemID.LAVARUNE));
		SUBSTITUTES.put(FIRE, Arrays.asList(ItemID.SUNFIRERUNE, ItemID.SMOKERUNE, ItemID.STEAMRUNE,
			ItemID.LAVARUNE));
		SUBSTITUTES.put(SOUL, Collections.singletonList(ItemID.AETHERRUNE));
		SUBSTITUTES.put(COSMIC, Collections.singletonList(ItemID.AETHERRUNE));

	}

	private SpellRunes()
	{
	}

	/** Rune ids and counts from a bundled row of [spell, rune, count, rune, count...], in order. */
	private static Map<Integer, Integer> cost(JsonArray row)
	{
		Map<Integer, Integer> cost = new LinkedHashMap<>();
		for (int i = 1; i < row.size(); i += 2)
		{
			cost.put(rune(row.get(i).getAsString()), row.get(i + 1).getAsInt());
		}
		return Collections.unmodifiableMap(cost);
	}

	private static int rune(String name)
	{
		return RUNES.get(RUNE_NAMES.indexOf(name));
	}

	/** The spellbook icon for a combat spell, or -1 when unknown. */
	static int sprite(String spellName)
	{
		return spellName == null ? -1 : SPRITES.getOrDefault(spellName.toLowerCase(Locale.ROOT), -1);
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
		String name = spell == SupportSpell.THRALL ? (tier == null ? Thrall.GREATER : tier).name() + " thrall"
			: String.valueOf(spell);
		return SUPPORT.getOrDefault(name.toLowerCase(Locale.ROOT).replace('_', ' '), Collections.emptyMap());
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
		int index = RUNES.indexOf(runeId);
		return index < 0 ? "rune" : RUNE_NAMES.get(index);
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
