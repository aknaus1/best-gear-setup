package com.bestgearsetup;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.NPC;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;

/**
 * Special attack weapons whose landed specs set a pre-fight preparation value. Constant names match RuneLite's
 * Special Attack Counter weapons, so party updates map across by name; timings follow that plugin's measurements.
 */
enum SpecWeapon
{
	DRAGON_WARHAMMER("specDwh", false, 0, 0, ItemID.DRAGON_WARHAMMER, ItemID.BH_DRAGON_WARHAMMER_CORRUPTED),
	ELDER_MAUL("specElderMaul", false, 50, 0, ItemID.ELDER_MAUL, ItemID.ELDER_MAUL_ORNAMENT),
	ARCLIGHT("specArclight", false, 0, 0, ItemID.ARCLIGHT),
	EMBERLIGHT("specEmberlight", false, 0, 0, ItemID.EMBERLIGHT),
	// The hit counts of its double hit, reported as one value like the Special Attack Counter does.
	TONALZTICS_OF_RALOS("specTonalztics", true, 50, 0, ItemID.TONALZTICS_OF_RALOS_CHARGED),
	BANDOS_GODSWORD("specBgsDamage", true, 0, 0, ItemID.BGS, ItemID.BGSG),
	SEERCULL("specSeercullDamage", true, 46, 5, ItemID.DAGANOTH_CAVE_MAGIC_SHORTBOW),
	EYE_OF_AYAK("specAyakDamage", true, 120, 0, ItemID.EYE_OF_AYAK);

	/** Tekton loses Defence even when a Bandos godsword special misses. */
	private static final Set<Integer> TEKTON = new HashSet<>(Arrays.asList(NpcID.RAIDS_TEKTON_WAITING,
		NpcID.RAIDS_TEKTON_WALKING_STANDARD, NpcID.RAIDS_TEKTON_FIGHTING_STANDARD, NpcID.RAIDS_TEKTON_HAMMERING,
		NpcID.RAIDS_TEKTON_WALKING_ENRAGED, NpcID.RAIDS_TEKTON_FIGHTING_ENRAGED));
	private static final Map<Integer, SpecWeapon> BY_ITEM = new HashMap<>();
	private static final Map<String, SpecWeapon> BY_NAME = new HashMap<>();

	static
	{
		for (SpecWeapon weapon : values())
		{
			BY_NAME.put(weapon.name(), weapon);
			for (int id : weapon.items)
			{
				BY_ITEM.put(id, weapon);
			}
		}
	}

	/** The preparation setting the special fills. */
	final String key;
	/** Whether the setting counts damage (or Tonalztics hits) rather than successful specials. */
	final boolean damage;
	private final int baseCycles;
	private final int cyclesPerTile;
	private final int[] items;

	SpecWeapon(String key, boolean damage, int baseCycles, int cyclesPerTile, int... items)
	{
		this.key = key;
		this.damage = damage;
		this.baseCycles = baseCycles;
		this.cyclesPerTile = cyclesPerTile;
		this.items = items;
	}

	static SpecWeapon byItem(int itemId)
	{
		return BY_ITEM.get(itemId);
	}

	/** The weapon a Special Attack Counter weapon name refers to, or null when it isn't modelled. */
	static SpecWeapon byName(String name)
	{
		return BY_NAME.get(name);
	}

	/** Game ticks from the spec energy drop to the hitsplat, at this distance in tiles. */
	int hitDelay(int distance)
	{
		// Projectile travel in client cycles (30 per tick), plus the tick every attack waits.
		return (baseCycles + cyclesPerTile * distance) / 30 + 1;
	}

	/** What one special adds to its setting. */
	int amount(int hit, NPC target)
	{
		if (this == BANDOS_GODSWORD && hit == 0 && target != null && TEKTON.contains(target.getId()))
		{
			return 10;
		}
		return damage ? Math.max(0, hit) : hit > 0 ? 1 : 0;
	}
}
