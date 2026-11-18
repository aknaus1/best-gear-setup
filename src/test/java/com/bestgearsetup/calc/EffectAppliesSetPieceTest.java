package com.bestgearsetup.calc;

import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Names;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

/**
 * Optimizer.effectApplies has no rules for the pieces of whole sets (Barrows, moon, obsidian armour, void helms):
 * effect() takes every "optimizer sets" piece first, so those items never reach it. An item named like a set piece
 * that is not caught as one (a new variant, an uncharged or broken state, a piece dropped from the list) would reach
 * effectApplies without the rule that tied it to its set's weapon or class, and fall to its later rules instead.
 */
public class EffectAppliesSetPieceTest
{
	/** Name prefixes of the set-piece rules effectApplies no longer has, in their old order, and what each required. */
	private static final String[][] SET_RULES = {
		{"dharok's ", "melee with dharok's greataxe"},
		{"verac's ", "melee with verac's flail"},
		{"blood moon ", "melee with dual macuahuitl"},
		{"ahrim's ", "magic with ahrim's staff"},
		{"karil's ", "ranged with karil's crossbow"},
		{"amulet of the damned", "ahrim's staff (magic) or karil's crossbow (ranged)"},
		{"eclipse moon ", "ranged with an atlatl"},
		{"obsidian ", "melee with a toktz- or tzhaar-ket- weapon"},
		{"void melee helm", "melee"},
		{"void ranger helm", "ranged"},
		{"void mage helm", "magic"},
	};

	@Test
	public void setPiecesNeverReachEffectApplies() throws Exception
	{
		GameData data = WikiGameData.get(RuneLiteAPI.GSON).gameData(RuneLiteAPI.GSON);
		Names.NameList special = Names.list("special gear");
		List<String> reached = new ArrayList<>();
		int named = 0;
		for (Slot slot : Slot.values())
		{
			// The gear search never offers the weapon or ammunition slot to effect() (Optimizer.exactWeapon).
			if (slot == Slot.WEAPON || slot == Slot.AMMO)
			{
				continue;
			}
			for (Gear item : data.getItems(slot))
			{
				String name = Encounter.lower(item);
				String rule = setRule(name);
				if (rule == null)
				{
					continue;
				}
				named++;
				// Optimizer.plain: only special gear, max capes and slayer helms get as far as effectApplies.
				boolean gated = special.matches(item.getLowerCombatName()) || special.matches(name)
					|| name.contains("max cape") || DpsCalculator.isSlayerHelm(item);
				if (gated && !setPiece(item))
				{
					reached.add(slot + " " + item.getId() + " \"" + name + "\" is not an optimizer set piece, so it reaches"
						+ " effectApplies, which no longer has its rule (" + rule + ")");
				}
			}
		}
		assertTrue("no catalogue item is named like a set piece; the prefixes are out of date", named > 0);
		assertTrue(String.join("\n", reached), reached.isEmpty());
	}

	private static String setRule(String name)
	{
		for (String[] rule : SET_RULES)
		{
			if (name.startsWith(rule[0]))
			{
				return "\"" + rule[0].trim() + "\": " + rule[1];
			}
		}
		return null;
	}

	/** Optimizer.setPiece: any slot's name of any "optimizer sets" piece, matched as the calculator matches it. */
	private static boolean setPiece(Gear item)
	{
		for (JsonElement set : Names.rows("optimizer sets"))
		{
			for (JsonElement piece : set.getAsJsonObject().getAsJsonArray("pieces"))
			{
				JsonArray names = piece.getAsJsonArray();
				for (int i = 1; i < names.size(); i++)
				{
					if (Effects.namedWeapon(item, names.get(i).getAsString()))
					{
						return true;
					}
				}
			}
		}
		return false;
	}
}
