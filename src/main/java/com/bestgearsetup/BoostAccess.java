package com.bestgearsetup;

import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.data.Potion;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Which stat boosts (potions, hearts) a search may assume when the potion choice is "best".
 * Owned items only uses boosts the player owns (Inventory + equipped only, those carried); Owned + budget adds anything tradeable (boosts are
 * not counted against the budget); Best in slot assumes every boost. Any dose counts as owning a
 * potion, and a divine potion counts as its ordinary version. Raid supplies (overloads, smelling salts) are handed
 * out inside the raid, so they need no ownership; they can be left out for the start of a raid, before you have them.
 */
final class BoostAccess
{
	/** Divine variants, keyed by the ordinary potion's 4-dose id in the bundled potion table. */
	private static final Map<Integer, Integer> DIVINE = new HashMap<>();
	/**
	 * Potions that give the same boost, keyed like {@link #DIVINE}: a (super) combat potion is an attack and a
	 * strength potion in one.
	 */
	private static final Map<Integer, List<Integer>> SAME_BOOST = new HashMap<>();
	/** Boosts that cannot be bought. */
	private static final Set<Integer> UNTRADEABLE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		ItemID.IMBUED_HEART, ItemID.SATURATED_HEART)));

	static
	{
		DIVINE.put(ItemID._4DOSE2COMBAT, ItemID._4DOSEDIVINECOMBAT);
		DIVINE.put(ItemID._4DOSE2ATTACK, ItemID._4DOSEDIVINEATTACK);
		DIVINE.put(ItemID._4DOSE2STRENGTH, ItemID._4DOSEDIVINESTRENGTH);
		DIVINE.put(ItemID._4DOSERANGERSPOTION, ItemID._4DOSEDIVINERANGE);
		DIVINE.put(ItemID._4DOSE1MAGIC, ItemID._4DOSEDIVINEMAGIC);
		DIVINE.put(ItemID._4DOSEBATTLEMAGE, ItemID._4DOSEDIVINEBATTLEMAGE);
		DIVINE.put(ItemID._4DOSEBASTION, ItemID._4DOSEDIVINEBASTION);
		SAME_BOOST.put(ItemID._4DOSE2ATTACK, Collections.singletonList(ItemID._4DOSE2COMBAT));
		SAME_BOOST.put(ItemID._4DOSE2STRENGTH, Collections.singletonList(ItemID._4DOSE2COMBAT));
		SAME_BOOST.put(ItemID._4DOSE2COMBAT, Arrays.asList(ItemID._4DOSE2ATTACK, ItemID._4DOSE2STRENGTH));
		SAME_BOOST.put(ItemID._4DOSE1ATTACK, Collections.singletonList(ItemID._4DOSECOMBAT));
		SAME_BOOST.put(ItemID.STRENGTH4, Collections.singletonList(ItemID._4DOSECOMBAT));
		SAME_BOOST.put(ItemID._4DOSECOMBAT, Arrays.asList(ItemID._4DOSE1ATTACK, ItemID.STRENGTH4));
	}

	private BoostAccess()
	{
	}

	/**
	 * @param owned owned item ids, expanded so that any dose of a potion includes its 4-dose id
	 * @param raidPotions whether raid supplies may be assumed
	 */
	static Predicate<Potion> allowed(SearchMode mode, Set<Integer> owned, boolean raidPotions)
	{
		switch (mode)
		{
			case OWNED_ONLY:
			case INVENTORY_ONLY:
				return p -> p.isRaidSupply() ? raidPotions : owns(p, owned);
			case BUDGET:
				return p -> p.isRaidSupply() ? raidPotions : !UNTRADEABLE.contains(p.getId()) || owns(p, owned);
			default:
				return p -> raidPotions || !p.isRaidSupply();
		}
	}

	/**
	 * For finding the boost in the bank: the potion's 4-dose id first, then its other doses, its divine version and
	 * potions giving the same boost (super combat for super attack, and the reverse).
	 */
	static Set<Integer> itemIds(Potion potion)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		addDoses(ids, potion.getId());
		for (int same : SAME_BOOST.getOrDefault(potion.getId(), Collections.emptyList()))
		{
			addDoses(ids, same);
		}
		return ids;
	}

	private static void addDoses(Set<Integer> ids, int potion)
	{
		addFamily(ids, potion);
		Integer divine = DIVINE.get(potion);
		if (divine != null)
		{
			addFamily(ids, divine);
		}
	}

	/** The 4-dose id, then the other doses highest first: their ids rise as the dose falls, (3) to (1). */
	private static void addFamily(Set<Integer> ids, int fourDose)
	{
		ids.add(fourDose);
		// RuneLite's variation order is unspecified.
		ids.addAll(new TreeSet<>(ItemVariationMapping.getVariations(ItemVariationMapping.map(fourDose))));
	}

	static boolean owns(Potion potion, Set<Integer> owned)
	{
		Integer divine = DIVINE.get(potion.getId());
		return owned.contains(potion.getId()) || divine != null && owned.contains(divine);
	}
}
