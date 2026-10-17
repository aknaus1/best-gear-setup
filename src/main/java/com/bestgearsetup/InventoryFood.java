package com.bestgearsetup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;
import lombok.Value;
import net.runelite.api.gameval.ItemID;

/**
 * Food for an individual search's free inventory slots: whatever the gear, boosts, runes and switches leave of the
 * 28 slots, filled with the best-healing food the player owns, or sharks when they own none of the listed foods.
 * Raid kits keep their own supply rules and get no food.
 */
public final class InventoryFood
{
	static final int INVENTORY_SLOTS = 28;

	/**
	 * One-bite foods, best heal at 99 Hitpoints first; anglerfish leads, since it can heal above the maximum.
	 * Combo eating (karambwans after another food in the same tick) isn't planned; a karambwan ranks by its heal.
	 */
	static final List<Food> FOODS = Collections.unmodifiableList(Arrays.asList(
		new Food("Anglerfish", ItemID.ANGLERFISH, 22),
		new Food("Manta ray", ItemID.MANTARAY, 22),
		new Food("Dark crab", ItemID.DARK_CRAB, 22),
		new Food("Tuna potato", ItemID.POTATO_TUNA_SWEETCORN, 22),
		new Food("Sea turtle", ItemID.SEATURTLE, 21),
		new Food("Shark", ItemID.SHARK, 20),
		new Food("Cooked karambwan", ItemID.TBWT_COOKED_KARAMBWAN, 18),
		new Food("Monkfish", ItemID.MONKFISH, 16),
		new Food("Potato with cheese", ItemID.POTATO_CHEESE, 16),
		new Food("Swordfish", ItemID.SWORDFISH, 14),
		new Food("Bass", ItemID.BASS, 13),
		new Food("Lobster", ItemID.LOBSTER, 12)));

	/** Recommended when the player owns none of {@link #FOODS}, or ownership isn't known yet. */
	static final Food DEFAULT = FOODS.get(5);

	private InventoryFood()
	{
	}

	@Value
	static class Food
	{
		String name;
		int id;
		int heals;
	}

	/** The best-healing food the player owns, else {@link #DEFAULT}. */
	static Food choose(IntPredicate owns)
	{
		for (Food food : FOODS)
		{
			if (owns.test(food.getId())) { return food; }
		}
		return DEFAULT;
	}

	/**
	 * The inventory with its free slots filled by one food entry each. Every entry takes one slot (pouch contents
	 * and loaded darts don't), so the food is 28 less the entries already listed; a full inventory is returned as is.
	 */
	public static List<RaidPlan.InventoryItem> fill(List<RaidPlan.InventoryItem> inventory, IntPredicate owns)
	{
		int free = INVENTORY_SLOTS - inventory.size();
		if (free <= 0) { return inventory; }
		Food food = choose(owns);
		RaidPlan.InventoryItem item = new RaidPlan.InventoryItem(food.getName(), Collections.singletonList(food.getId()),
			false, Collections.emptyList(), RaidPlan.InventoryGroup.FOOD);
		List<RaidPlan.InventoryItem> filled = new ArrayList<>(inventory);
		for (int slot = 0; slot < free; slot++) { filled.add(item); }
		return filled;
	}
}
