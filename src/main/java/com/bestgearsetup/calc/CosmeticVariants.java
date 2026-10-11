package com.bestgearsetup.calc;

import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
	public static boolean equivalent(GearItem a, GearItem b)
	{
		if (a == b)
		{
			return true;
		}
		return a != null && b != null
			&& a.getSlot() == b.getSlot()
			&& a.getCombatId() == b.getCombatId()
			&& a.getLowerCombatName().equals(b.getLowerCombatName())
			&& a.getStabBonus() == b.getStabBonus()
			&& a.getSlashBonus() == b.getSlashBonus()
			&& a.getCrushBonus() == b.getCrushBonus()
			&& a.getRangedBonus() == b.getRangedBonus()
			&& a.getMagicBonus() == b.getMagicBonus()
			&& a.getMeleeStr() == b.getMeleeStr()
			&& a.getRangedStr() == b.getRangedStr()
			&& Double.compare(a.getMagicStr(), b.getMagicStr()) == 0
			&& a.getPrayerBonus() == b.getPrayerBonus()
			&& a.getStabDef() == b.getStabDef()
			&& a.getSlashDef() == b.getSlashDef()
			&& a.getCrushDef() == b.getCrushDef()
			&& a.getRangedDef() == b.getRangedDef()
			&& a.getMagicDef() == b.getMagicDef()
			&& a.getAttackSpeed() == b.getAttackSpeed()
			&& a.isTwoHanded() == b.isTwoHanded()
			&& a.getSubcategory().equals(b.getSubcategory())
			&& a.getStyles().equals(b.getStyles())
			&& a.getAmmunition().equals(b.getAmmunition())
			// The uncharged Tonalztics is folded with the charged one but throws a tile shorter.
			&& AttackReach.baseRange(a) == AttackReach.baseRange(b);
	}

	/** The item's other cosmetic variants in the catalogue, in catalogue order. */
	public static List<GearItem> others(GameData data, GearItem item)
	{
		if (data == null || item == null || item.getSlot() == null)
		{
			return Collections.emptyList();
		}
		List<GearItem> out = new ArrayList<>();
		for (GearItem other : data.getItems(item.getSlot()))
		{
			if (other.getId() != item.getId() && equivalent(item, other))
			{
				out.add(other);
			}
		}
		return out;
	}
}
