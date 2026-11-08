package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.monster;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

/** Temporary: the JSON-driven table must give exactly the specials the hand-written chain gave. */
public class SpecialAttackParityTest
{
	@Test
	public void tableMatchesTheLegacyChainForEveryWeaponStyleTargetAndStack() throws Exception
	{
		GameData data = WikiGameData.get(RuneLiteAPI.GSON).gameData(RuneLiteAPI.GSON);
		List<GearItem> weapons = new ArrayList<>(data.getItems(Slot.WEAPON));
		// Names the legacy chain tests, with suffixes that exercise prefix, (i) and uncharged checks.
		String legacy = new String(Files.readAllBytes(Paths.get("src/test/java/com/bestgearsetup/calc/LegacySpecialAttack.java")));
		Matcher m = Pattern.compile("n\\.(?:startsWith|equals|contains)\\(\"([^\"]+)\"\\)").matcher(legacy);
		Set<String> names = new LinkedHashSet<>();
		while (m.find())
		{
			for (String suffix : new String[]{"", " (i)", " (uncharged)", " (or)", "x", " (bh)"})
			{
				names.add(m.group(1) + suffix);
			}
		}
		int id = 900_000;
		for (String name : names)
		{
			for (String styles : new String[]{"slash,slash,aggressive", "rapid,ranged,rapid", "accurate,magic,accurate"})
			{
				weapons.add(TestData.weapon(id++, name, "slash sword", 4, styles));
			}
		}
		Monster plain = monster(100, 50);
		Monster demon = monster(100, 50);
		Monster.Attribute tag = new Monster.Attribute();
		tag.setName("demon");
		demon.setAttributes(Collections.singletonList(tag));
		List<GearItem> bloodMoon = new ArrayList<>();
		for (String piece : new String[]{"blood moon helm", "blood moon chestplate", "blood moon tassets"})
		{
			for (Slot slot : Slot.values())
			{
				for (GearItem item : data.getItems(slot))
				{
					if (item.getLowerCombatName().equals(piece))
					{
						bloodMoon.add(item);
					}
				}
			}
		}
		assertEquals(3, bloodMoon.size());
		int compared = 0;
		for (GearItem weapon : weapons)
		{
			for (String style : weapon.getStyles())
			{
				for (boolean wearingSet : new boolean[]{false, true})
				{
					Loadout l = new Loadout();
					l.set(Slot.WEAPON, weapon);
					l.setStyle(AttackStyle.parse(style));
					if (wearingSet)
					{
						bloodMoon.forEach(piece -> l.set(piece.getSlot(), piece));
					}
					for (Monster target : new Monster[]{plain, demon})
					{
						for (int stacks : new int[]{0, 3})
						{
							CombatContext ctx = new CombatContext(target, PlayerLevels.maxed(), false, false, null)
								.withModifiers(CombatModifiers.builder().specialAttack(true).soulreaperStacks(stacks).build());
							SpecialAttack now = SpecialAttack.of(l, ctx);
							LegacySpecialAttack was = LegacySpecialAttack.of(l, ctx);
							String where = weapon.getName() + " / " + style + " / set " + wearingSet + " / "
								+ target.getAttributes().size() + " / " + stacks;
							if (was == null)
							{
								assertNull(where, now);
								continue;
							}
							assertEquals(where, was.getName(), now.getName());
							assertEquals(where, was.getEnergy(), now.getEnergy());
							assertEquals(where, was.getKind().name(), now.getKind().name());
							assertEquals(where, was.getHits(), now.getHits());
							assertEquals(where, was.getAccuracyNumerator(), now.getAccuracyNumerator());
							assertEquals(where, was.getAccuracyDenominator(), now.getAccuracyDenominator());
							assertArrayEquals(where, was.getDamage(), now.getDamage());
							assertEquals(where, was.getDefence(), now.getDefence());
							assertEquals(where, was.isGuaranteed(), now.isGuaranteed());
							assertEquals(where, was.getMinimumPercent(), now.getMinimumPercent());
							assertEquals(where, was.getExtraTicks(), now.getExtraTicks());
							assertEquals(where, was.getDrain().name(), now.getDrain().name());
							compared++;
						}
					}
				}
			}
		}
		System.out.println("PARITY specials compared: " + compared + " across " + weapons.size() + " weapons");
	}
}
