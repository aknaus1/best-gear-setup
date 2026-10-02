package com.bestgearsetup.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;

/**
 * Immutable snapshot of the reference data (monsters, equipment, spells, prayers, potions).
 */
public class GameData
{
	private static final String[] EXCLUDED_NAME_PARTS = {
		"(fractured archive)", "(uncharged)", "(no burn dmg)", "(unranked)", "(unfrozen target)",
	};

	/** Minigame-only items (Barbarian Assault arrows) that cannot be used elsewhere. */
	private static final java.util.Set<String> EXCLUDED_NAMES = new java.util.HashSet<>(java.util.Arrays.asList(
		"bullet arrow", "field arrow", "blunt arrow", "barbed arrow"));

	private static final String[] EXCLUDED_NAME_PREFIXES = {
		"starter staff",
	};

	@Getter
	private final List<MonsterSummary> monsters;
	private final Map<Slot, List<GearItem>> itemsBySlot = new EnumMap<>(Slot.class);
	private final Map<Slot, Map<Integer, GearItem>> itemsBySlotAndId = new EnumMap<>(Slot.class);
	@Getter
	private final List<Spell> spells;
	private final Map<CombatClass, List<OffensivePrayer>> prayers = new EnumMap<>(CombatClass.class);
	private final Map<Integer, MonsterSummary> monstersById = new HashMap<>();
	/** Variants grouped by base name, in bundle order of each group's first variant. */
	private final List<MonsterGroup> groups = new ArrayList<>();
	/** The same groups holding only their boss variants. */
	private final List<MonsterGroup> bossGroups = new ArrayList<>();
	private final Map<String, List<Potion>> potions = new HashMap<>();

	/**
	 * Wear requirements are taken as given: the bundled Wiki snapshot already combines cache parameters,
	 * Wiki text and the reviewed rule table.
	 */
	public GameData(
		List<MonsterSummary> monsters,
		Map<String, List<GearItem>> equipmentBySlotKey,
		List<GearItem> weapons,
		List<Spell> spells,
		Map<String, List<Prayer>> prayersByCategory,
		Map<String, List<Potion>> potionsBySkill)
	{
		this.monsters = Collections.unmodifiableList(new ArrayList<>(monsters));
		indexMonsters();

		for (Slot slot : Slot.values())
		{
			itemsBySlot.put(slot, new ArrayList<>());
			itemsBySlotAndId.put(slot, new HashMap<>());
		}
		for (Map.Entry<String, List<GearItem>> e : equipmentBySlotKey.entrySet())
		{
			Slot slot = Slot.fromApiKey(e.getKey());
			if (slot == null || slot == Slot.WEAPON)
			{
				continue;
			}
			for (GearItem item : e.getValue())
			{
				addItem(slot, item);
			}
		}
		for (GearItem weapon : weapons)
		{
			addItem(Slot.WEAPON, weapon);
		}

		this.spells = Collections.unmodifiableList(new ArrayList<>(spells));
		buildPrayers(prayersByCategory);
		if (potionsBySkill != null)
		{
			for (Map.Entry<String, List<Potion>> e : potionsBySkill.entrySet())
			{
				List<Potion> list = new ArrayList<>();
				for (Potion p : e.getValue())
				{
					// The dragon battleaxe "boost" depends on levels drained elsewhere; not modelled.
					if (p != null && !p.getName().equalsIgnoreCase("dragon battleaxe"))
					{
						list.add(p);
					}
				}
				potions.put(e.getKey().toLowerCase(Locale.ROOT), Collections.unmodifiableList(list));
			}
		}
	}

	private void indexMonsters()
	{
		for (MonsterSummary m : this.monsters)
		{
			for (int id : m.getIds())
			{
				monstersById.putIfAbsent(id, m);
			}
		}
		Map<String, List<MonsterSummary>> byBase = new LinkedHashMap<>();
		Map<String, List<MonsterSummary>> bossesByBase = new LinkedHashMap<>();
		for (MonsterSummary m : this.monsters)
		{
			String base = MonsterGroup.baseName(m.getName());
			byBase.computeIfAbsent(base, k -> new ArrayList<>()).add(m);
			if (m.isBoss())
			{
				bossesByBase.computeIfAbsent(base, k -> new ArrayList<>()).add(m);
			}
		}
		byBase.forEach((base, variants) -> groups.add(new MonsterGroup(base, variants)));
		bossesByBase.forEach((base, variants) -> bossGroups.add(new MonsterGroup(base, variants)));
	}

	private GameData(GameData base, List<MonsterSummary> monsters)
	{
		this.monsters = Collections.unmodifiableList(new ArrayList<>(monsters));
		indexMonsters();
		itemsBySlot.putAll(base.itemsBySlot);
		itemsBySlotAndId.putAll(base.itemsBySlotAndId);
		spells = base.spells;
		prayers.putAll(base.prayers);
		potions.putAll(base.potions);
	}

	/** The same equipment, spells, prayers and potions with another monster list. */
	public GameData withMonsters(List<MonsterSummary> replacement)
	{
		return new GameData(this, replacement);
	}

	private void addItem(Slot slot, GearItem item)
	{
		// The same id can appear in two slots (darts are both blowpipe ammo and thrown weapons). Within a slot
		// one id may name two states with different stats (the keris partisan of amascut inside and outside
		// the Tombs of Amascut); encounter rules pick one, and id lookups return the first.
		Map<Integer, GearItem> byId = itemsBySlotAndId.get(slot);
		GearItem existing = item == null ? null : byId.get(item.getId());
		if (item == null || isExcluded(item) || existing != null && existing.getName().equals(item.getName()))
		{
			return;
		}
		item.setSlot(slot);
		itemsBySlot.get(slot).add(item);
		byId.putIfAbsent(item.getId(), item);
	}

	/**
 * Unsupported custom Leagues items and unusable uncharged weapons. Activity gear is filtered per encounter.
	 * Optional DMM, Bounty Hunter and beta equipment stays in the catalogue for search-time filtering.
	 */
	static boolean isExcluded(GearItem item)
	{
		if (item.isLeagueEquipment())
		{
			return true;
		}
		if (item.getId() <= 0)
		{
			return true;
		}
		String name = item.getName().toLowerCase(Locale.ROOT);
		if (EXCLUDED_NAMES.contains(name))
		{
			return true;
		}
		for (String part : EXCLUDED_NAME_PARTS)
		{
			if (name.contains(part))
			{
				return true;
			}
		}
		for (String prefix : EXCLUDED_NAME_PREFIXES)
		{
			if (name.startsWith(prefix))
			{
				return true;
			}
		}
		return false;
	}

	private void buildPrayers(Map<String, List<Prayer>> byCategory)
	{
		// Melee pairs the attack and strength entries of the same prayer (e.g. piety 20% / 23%).
		Map<String, Prayer> meleeAttack = standardByName(byCategory.get("attack"));
		Map<String, Prayer> meleeStrength = standardByName(byCategory.get("strength"));
		List<OffensivePrayer> melee = new ArrayList<>();
		for (Map.Entry<String, Prayer> e : meleeStrength.entrySet())
		{
			Prayer str = e.getValue();
			Prayer att = meleeAttack.get(e.getKey());
			melee.add(new OffensivePrayer(str.getName(), att == null ? 0 : att.getBoost(), str.getBoost(),
				str.getPrayerLevel(), str.getDefenceLevel()));
		}
		for (Map.Entry<String, Prayer> e : meleeAttack.entrySet())
		{
			if (!meleeStrength.containsKey(e.getKey()))
			{
				Prayer att = e.getValue();
				melee.add(new OffensivePrayer(att.getName(), att.getBoost(), 0, att.getPrayerLevel(), att.getDefenceLevel()));
			}
		}
		prayers.put(CombatClass.MELEE, melee);
		prayers.put(CombatClass.RANGED, toOffensive(byCategory.get("ranged")));
		prayers.put(CombatClass.MAGIC, toOffensive(byCategory.get("magic")));
	}

	private static Map<String, Prayer> standardByName(List<Prayer> list)
	{
		Map<String, Prayer> out = new LinkedHashMap<>();
		if (list != null)
		{
			for (Prayer p : list)
			{
				if (p != null && p.getName() != null && "standard".equalsIgnoreCase(p.getPrayerbook())
					&& isGamePrayer(p.getName()))
				{
					out.put(p.getName(), p);
				}
			}
		}
		return out;
	}

	private static boolean isGamePrayer(String name)
	{
		for (net.runelite.api.Prayer prayer : net.runelite.api.Prayer.values())
		{
			if (prayer.name().replace('_', ' ').equalsIgnoreCase(name))
			{
				return true;
			}
		}
		return false;
	}

	private static List<OffensivePrayer> toOffensive(List<Prayer> list)
	{
		List<OffensivePrayer> out = new ArrayList<>();
		for (Prayer p : standardByName(list).values())
		{
			out.add(new OffensivePrayer(p.getName(), p.getBoost(), p.getStrengthBoost(), p.getPrayerLevel(), p.getDefenceLevel()));
		}
		return out;
	}

	public List<GearItem> getItems(Slot slot)
	{
		return Collections.unmodifiableList(itemsBySlot.get(slot));
	}

	public GearItem getItem(Slot slot, int id)
	{
		return itemsBySlotAndId.get(slot).get(id);
	}

	/** Potions boosting a skill: attack, strength, defence, ranged or magic. */
	public List<Potion> getPotions(String skill)
	{
		return potions.getOrDefault(skill, Collections.emptyList());
	}

	/** Finds an item in any slot by id. */
	public GearItem findItem(int id)
	{
		for (Slot slot : Slot.values())
		{
			GearItem item = itemsBySlotAndId.get(slot).get(id);
			if (item != null)
			{
				return item;
			}
		}
		return null;
	}

	/** Case-insensitive substring search over all equipment names, one entry per item id. */
	public List<GearItem> searchItems(String query, int limit)
	{
		String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
		List<GearItem> prefix = new ArrayList<>();
		List<GearItem> contains = new ArrayList<>();
		if (q.isEmpty())
		{
			return prefix;
		}
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (Slot slot : Slot.values())
		{
			for (GearItem item : itemsBySlot.get(slot))
			{
				String n = item.getName();
				if (!n.contains(q) || !seen.add(item.getId()))
				{
					continue;
				}
				(n.startsWith(q) ? prefix : contains).add(item);
			}
		}
		prefix.sort(java.util.Comparator.comparing(GearItem::getName));
		contains.sort(java.util.Comparator.comparing(GearItem::getName));
		prefix.addAll(contains);
		return prefix.size() > limit ? new ArrayList<>(prefix.subList(0, limit)) : prefix;
	}

	public List<OffensivePrayer> getPrayers(CombatClass combatClass)
	{
		return Collections.unmodifiableList(prayers.getOrDefault(combatClass, Collections.emptyList()));
	}

	/**
	 * Find the monster variant for an in-game NPC: exact NPC id first, then the same name and
	 * combat level, then the same name (variants carry suffixes, e.g. "vorkath (post-quest)").
	 */
	public MonsterSummary matchNpc(int npcId, String npcName, int combatLevel)
	{
		MonsterSummary byId = monstersById.get(npcId);
		if (byId != null)
		{
			return byId;
		}
		if (npcName == null)
		{
			return null;
		}
		String name = npcName.toLowerCase(Locale.ROOT).trim();
		MonsterSummary sameName = null;
		MonsterSummary prefixLevel = null;
		MonsterSummary prefix = null;
		for (MonsterSummary m : monsters)
		{
			String n = m.getName();
			if (n.equals(name))
			{
				if (m.getCombatLevel() == combatLevel)
				{
					return m;
				}
				if (sameName == null)
				{
					sameName = m;
				}
			}
			else if (n.startsWith(name + " ("))
			{
				if (m.getCombatLevel() == combatLevel && prefixLevel == null)
				{
					prefixLevel = m;
				}
				if (prefix == null)
				{
					prefix = m;
				}
			}
		}
		if (prefixLevel != null)
		{
			return prefixLevel;
		}
		return sameName != null ? sameName : prefix;
	}

	/**
	 * Case-insensitive substring search over monster groups, matching the base name or any variant's full
	 * name; exact and prefix matches rank first.
	 *
	 * @param bossesOnly search only bosses, each group holding only its boss variants
	 */
	public List<MonsterGroup> searchMonsterGroups(String query, int limit, boolean bossesOnly)
	{
		String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
		if (q.isEmpty())
		{
			return new ArrayList<>();
		}
		// Exact, prefix and substring matches.
		List<List<MonsterGroup>> ranked = Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		for (MonsterGroup group : bossesOnly ? bossGroups : groups)
		{
			int rank = matchRank(group.getName(), q);
			for (MonsterSummary variant : group.getVariants())
			{
				rank = Math.min(rank, matchRank(variant.getName(), q));
			}
			if (rank < ranked.size())
			{
				ranked.get(rank).add(group);
			}
		}
		List<MonsterGroup> out = new ArrayList<>();
		ranked.forEach(out::addAll);
		return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
	}

	/** 0 for an exact match, 1 for a prefix, 2 for a substring, 3 for none. */
	private static int matchRank(String name, String q)
	{
		if (name.equals(q))
		{
			return 0;
		}
		if (name.startsWith(q))
		{
			return 1;
		}
		return name.contains(q) ? 2 : 3;
	}

	/**
	 * The group a variant belongs to, restricted to bosses when asked and the variant is one; a target outside
	 * the bundled list forms a group of its own.
	 */
	public MonsterGroup groupOf(MonsterSummary monster, boolean bossesOnly)
	{
		if (bossesOnly)
		{
			for (MonsterGroup group : bossGroups)
			{
				if (group.contains(monster))
				{
					return group;
				}
			}
		}
		for (MonsterGroup group : groups)
		{
			if (group.contains(monster))
			{
				return group;
			}
		}
		return MonsterGroup.single(monster);
	}

	public static String titleCase(String s)
	{
		StringBuilder sb = new StringBuilder(s.length());
		boolean upper = true;
		for (char c : s.toCharArray())
		{
			sb.append(upper ? Character.toUpperCase(c) : c);
			upper = c == ' ' || c == '(' || c == '-';
		}
		return sb.toString();
	}
}
