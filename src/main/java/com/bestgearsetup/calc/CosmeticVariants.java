package com.bestgearsetup.calc;

import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Gear;
import java.util.*;

/**
 * Cosmetics the snapshot folded together (ornament kits, recolours, kitted or league copies) and the client shows
 * as separate items. They fight identically, so the search needs only one of them; they still differ in name,
 * price and ownership, so each stays its own catalogue entry.
 */
public final class CosmeticVariants
{
	private CosmeticVariants()
	{
	}

	/** Whether the two items share a combat identity and everything the search scores. */
	public static boolean equivalent(Gear a, Gear b)
	{
		if (a == b)
		{
			return true;
		}
		// Each bonus no lower both ways is each bonus equal.
		return Optimizer.noWorseBonuses(a, b) && Optimizer.noWorseBonuses(b, a)
			&& Double.compare(a.getMagicStr(), b.getMagicStr()) == 0
			&& a.getSlot() == b.getSlot()
			&& a.getCombatId() == b.getCombatId()
			&& a.getLowerCombatName().equals(b.getLowerCombatName())
			&& a.getAttackSpeed() == b.getAttackSpeed()
			&& a.isTwoHanded() == b.isTwoHanded()
			&& a.getSubcategory().equals(b.getSubcategory())
			&& a.getStyles().equals(b.getStyles())
			&& a.getAmmunition().equals(b.getAmmunition())
			// The uncharged Tonalztics is folded with the charged one but throws a tile shorter.
			&& AttackReach.baseRange(a) == AttackReach.baseRange(b);
	}

	/** The item's other cosmetic variants in the catalogue, in catalogue order. */
	public static List<Gear> others(GameData data, Gear item)
	{
		if (data == null || item == null || item.getSlot() == null)
		{
			return Collections.emptyList();
		}
		List<Gear> out = new ArrayList<>();
		for (Gear other : data.getItems(item.getSlot()))
		{
			if (other.getId() != item.getId() && equivalent(item, other))
			{
				out.add(other);
			}
		}
		return out;
	}
}
