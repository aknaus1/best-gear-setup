package com.bestgearsetup.calc;

import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Summary;
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

	static Gear item(int id, String name, Slot slot)
	{
		Gear i = new Gear();
		i.setId(id);
		i.setName(name);
		i.setSlot(slot);
		i.setTradeable(true);
		return i;
	}

	static Gear weapon(int id, String name, String subcategory, int speed, String... styles)
	{
		Gear w = item(id, name, Slot.WEAPON);
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

	static Map<Combat, OffensivePrayer> piety()
	{
		Map<Combat, OffensivePrayer> p = new EnumMap<>(Combat.class);
		p.put(Combat.MELEE, new OffensivePrayer("piety", 20, 23, 70, 70));
		p.put(Combat.RANGED, new OffensivePrayer("rigour", 20, 23, 74, 70));
		p.put(Combat.MAGIC, new OffensivePrayer("augury", 25, 4, 77, 70));
		return p;
	}

	static GameData gameData(List<Gear> items, List<Spell> spells)
	{
		Map<String, List<Gear>> bySlot = new HashMap<>();
		List<Gear> weapons = new ArrayList<>();
		for (Gear i : items)
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
		return new GameData(Collections.<Summary>emptyList(), bySlot, weapons, spells,
			Collections.<String, List<Prayer>>emptyMap(), null);
	}
}
