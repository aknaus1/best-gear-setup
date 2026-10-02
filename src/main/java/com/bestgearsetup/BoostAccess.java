package com.bestgearsetup;

import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.data.Potion;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.runelite.api.gameval.ItemID;

/**
 * Which stat boosts (potions, hearts) a search may assume when the potion choice is "best".
 * Owned items only uses boosts the player owns; Owned + budget adds anything tradeable (boosts are
 * not counted against the budget); Best in slot assumes every boost. Any dose counts as owning a
 * potion, and a divine potion counts as its ordinary version.
 */
final class BoostAccess
{
	/** Divine variants, keyed by the ordinary potion's 4-dose id in the bundled potion table. */
	private static final Map<Integer, Integer> DIVINE = new HashMap<>();
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
	}

	private BoostAccess()
	{
	}

	/**
	 * @param owned owned item ids, expanded so that any dose of a potion includes its 4-dose id
	 */
	static Predicate<Potion> allowed(SearchMode mode, Set<Integer> owned)
	{
		switch (mode)
		{
			case OWNED_ONLY:
				return p -> owns(p, owned);
			case BUDGET:
				return p -> !UNTRADEABLE.contains(p.getId()) || owns(p, owned);
			default:
				return p -> true;
		}
	}

	static boolean owns(Potion potion, Set<Integer> owned)
	{
		Integer divine = DIVINE.get(potion.getId());
		return owned.contains(potion.getId()) || divine != null && owned.contains(divine);
	}
}
