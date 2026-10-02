package com.bestgearsetup.calc;

import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.OffensivePrayer;
import com.bestgearsetup.data.Prayer;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builders for synthetic items and monsters. */
final class TestData
{
	private TestData()
	{
	}

	static GearItem item(int id, String name, Slot slot)
	{
		GearItem i = new GearItem();
		i.setId(id);
		i.setName(name);
		i.setSlot(slot);
		i.setTradeable(true);
		return i;
	}

	static GearItem weapon(int id, String name, String subcategory, int speed, String... styles)
	{
		GearItem w = item(id, name, Slot.WEAPON);
		w.setSubcategory(subcategory);
		w.setAttackSpeed(speed);
		w.setStyles(Arrays.asList(styles));
		return w;
	}

	static Monster monster(int defence, int defBonus, String... attributes)
	{
		Monster m = new Monster();
		m.setId(1);
		m.setName("test monster");
		m.setDefenceLevel(defence);
		m.setMagicLevel(defence);
		m.setDefStab(defBonus);
		m.setDefSlash(defBonus);
		m.setDefCrush(defBonus);
		m.setDefRanged(defBonus);
		m.setDefMagic(defBonus);
		List<Monster.Attribute> attrs = new ArrayList<>();
		for (String a : attributes)
		{
			Monster.Attribute attr = new Monster.Attribute();
			attr.setName(a);
			attrs.add(attr);
		}
		m.setAttributes(attrs);
		return m;
	}

	static Map<CombatClass, OffensivePrayer> piety()
	{
		Map<CombatClass, OffensivePrayer> p = new EnumMap<>(CombatClass.class);
		p.put(CombatClass.MELEE, new OffensivePrayer("piety", 20, 23, 70, 70));
		p.put(CombatClass.RANGED, new OffensivePrayer("rigour", 20, 23, 74, 70));
		p.put(CombatClass.MAGIC, new OffensivePrayer("augury", 25, 4, 77, 70));
		return p;
	}

	static GameData gameData(List<GearItem> items, List<Spell> spells)
	{
		Map<String, List<GearItem>> bySlot = new HashMap<>();
		List<GearItem> weapons = new ArrayList<>();
		for (GearItem i : items)
		{
			if (i.getSlot() == Slot.WEAPON)
			{
				weapons.add(i);
			}
			else
			{
				bySlot.computeIfAbsent(i.getSlot().getApiKey(), k -> new ArrayList<>()).add(i);
			}
		}
		return new GameData(Collections.<MonsterSummary>emptyList(), bySlot, weapons, spells,
			Collections.<String, List<Prayer>>emptyMap(), null);
	}
}
