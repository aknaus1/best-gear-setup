package com.bestgearsetup.calc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Summary;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.ToLongFunction;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/** Sweeps over the bundled Wiki catalogue; these supplement, rather than replace, exact mechanic vectors. */
public class CombatCatalogTest
{
	private static final Gson GSON = RuneLiteAPI.GSON;
	/** Offline stand-in for live prices: every tradeable item costs 1m. */
	private static final ToLongFunction<Gear> PRICE = item -> item.isTradeable() ? 1_000_000L : 0;
	private static GameData data;
	private static WikiMonsters monsters;

	@BeforeClass
	public static void loadSnapshot() throws Exception
	{
		data = WikiGameData.get(GSON).gameData(GSON);
		monsters = WikiMonsters.get(GSON);
	}

	private static Monster target(int npcId)
	{
		Summary summary = data.matchNpc(npcId, null, 0);
		assertNotNull("Missing bundled monster " + npcId, summary);
		return monsters.monster(summary.getName());
	}

	private static Gear weapon(String name)
	{
		for (Gear w : data.getItems(Slot.WEAPON))
		{
			if (w.getName().equals(name))
			{
				return w;
			}
		}
		throw new AssertionError("Missing bundled weapon " + name);
	}

	private static void sane(Dps result)
	{
		assertTrue(Double.isFinite(result.getDps()));
		assertTrue(Double.isFinite(result.getAverageHit()));
		assertTrue(result.getDps() >= 0);
		assertTrue(result.getAverageHit() >= 0);
		assertTrue(result.getMaxHit() >= 0);
		assertTrue(result.getAccuracy() >= 0 && result.getAccuracy() <= 1);
		assertTrue(result.getAverageHit() <= result.getMaxHit() + 1e-8);
	}

	private static Outfit loadout(Gear weapon, Style style)
	{
		Outfit l = new Outfit();
		l.set(Slot.WEAPON, weapon);
		l.setStyle(style);
		for (int id : weapon.getAmmunition())
		{
			Gear ammo = data.getItem(Slot.AMMO, id);
			if (ammo != null)
			{
				if (Weapons.loadsAmmo(weapon))
				{
					l.setLoadedAmmo(ammo);
				}
				else
				{
					l.set(Slot.AMMO, ammo);
				}
				break;
			}
		}
		if (style.isAutocast() && !weapon.getSubcategory().equals("salamander"))
		{
			for (Spell spell : data.getSpells())
			{
				if (spell.getMaxHit() > 0 && spell.castableWith(weapon.getId()))
				{
					l.setSpell(spell);
					break;
				}
			}
		}
		return l;
	}

	private static OptimizerSettings budget(long gp, int results)
	{
		return OptimizerSettings.builder().mode(SearchMode.BUDGET).budget(gp)
			.resultsPerClass(results)
			.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient", "arceuus"))).build();
	}

	@Test
	public void everyWeaponStyleScoresAcrossRepresentativeTargets()
	{
		List<Gear> weapons = data.getItems(Slot.WEAPON);
		assertTrue(weapons.size() > 600);
		int calculations = 0;
		for (Gear weapon : weapons)
		{
			for (String raw : weapon.getStyles())
			{
				Style style = Style.parse(raw);
				if (style == null)
				{
					// Only the bulwark's block stance has no attack.
					assertTrue(raw, raw.startsWith("block,"));
					continue;
				}
				Outfit l = loadout(weapon, style);
				for (int id : new int[]{13011, 13012, 13013, 11753, 11755, 7223, 7550, 7584, 12223})
				{
					CombatContext ctx = TestFactories.combatContext(target(id), PlayerLevels.maxed(), false, false, null);
					sane(DpsCalculator.calculate(l, ctx));
					calculations++;
				}
			}
		}
		assertTrue(calculations > 15_000);
	}

	@Test
	public void everyMonsterVariantProducesFiniteBoundedResults()
	{
		List<Summary> all = data.getMonsters();
		assertTrue(all.size() > 3000);
		Gear[] weapons = {weapon("abyssal whip"), weapon("rune crossbow"), weapon("trident of the seas"),
			weapon("magic shortbow"), weapon("osmumten's fang")};
		for (Summary summary : all)
		{
			Monster monster = monsters.monster(summary.getName());
			assertNotNull(summary.getName(), monster);
			CombatContext ctx = TestFactories.combatContext(monster, PlayerLevels.maxed(), false, false, null);
			for (Gear weapon : weapons)
			{
				Style style = Style.parse(weapon.getStyles().get(0));
				sane(DpsCalculator.calculate(loadout(weapon, style), ctx));
			}
		}
	}

	@Test
	public void budgetSearchesRespectBudgetAndActivityAccess()
	{
		for (int id : new int[]{13011, 11753, 7223})
		{
			Monster monster = target(id);
			CombatContext ctx = TestFactories.combatContext(monster, PlayerLevels.maxed(), false, true, null);
			for (Combat cls : Combat.values())
			{
				List<Setup> results = com.bestgearsetup.calc.Optimizers.of(data, ctx, budget(20_000_000, 1), itemId -> false, PRICE)
					.optimize(cls, () -> false, done -> { });
				for (Setup result : results)
				{
					sane(result.getDps());
					assertTrue(result.getBuyCost() <= 20_000_000);
					for (Slot slot : Slot.values())
					{
						Gear item = result.getLoadout().get(slot);
						if (item != null)
						{
							assertTrue(CombatRules.equipmentAllowed(monster, item));
							assertFalse(item.isDmmEquipment());
							assertFalse(item.isBetaEquipment());
							assertFalse(item.isBountyHunterEquipment());
						}
					}
				}
			}
		}
	}

	@Test
	public void healthDependentAndRaidScaledSearchesStaySane()
	{
		Monster vardorvis = target(12223);
		Monster akkha = RaidScaling.apply(target(11789),
			RaidScaling.Settings.builder().partySize(3).toaRaidLevel(300).toaPathLevel(2).build());
		assertTrue(akkha.getHitpoints() > 400);
		for (Monster monster : new Monster[]{vardorvis, akkha})
		{
			CombatContext ctx = TestFactories.combatContext(monster, PlayerLevels.maxed(), false, true, null)
				.withHealthStates(hp -> MonsterStates.atHealth(monster, hp));
			for (Combat cls : Combat.values())
			{
				for (Setup result : com.bestgearsetup.calc.Optimizers.of(data, ctx, budget(50_000_000, 1), itemId -> false, PRICE)
					.optimize(cls, () -> false, done -> { }))
				{
					sane(result.getDps());
					assertTrue(result.getBuyCost() <= 50_000_000);
				}
			}
		}
	}

	@Test
	public void specialAttackModeIsSaneForEveryWeaponAndSearchesOnlySpecWeapons()
	{
		CombatModifiers spec = CombatModifiers.builder().specialAttack(true).soulreaperStacks(5).build();
		java.util.Set<String> scored = new java.util.TreeSet<>();
		for (Gear weapon : data.getItems(Slot.WEAPON))
		{
			for (String raw : weapon.getStyles())
			{
				Style style = Style.parse(raw);
				if (style == null)
				{
					continue;
				}
				Outfit l = loadout(weapon, style);
				for (int id : new int[]{13011, 11753, 7584, 12223, 7550})
				{
					CombatContext ctx = TestFactories.combatContext(target(id), PlayerLevels.maxed(), false, true, null)
						.withModifiers(spec);
					Dps r = DpsCalculator.calculate(l, ctx);
					sane(r);
					if (r.getDps() > 0)
					{
						scored.add(weapon.getName());
					}
				}
			}
		}
		assertTrue(scored.contains("dragon claws"));
		assertTrue(scored.contains("armadyl godsword"));
		assertTrue(scored.contains("dark bow"));
		assertFalse(scored.contains("scythe of vitur"));

		CombatContext ctx = TestFactories.combatContext(target(12223), PlayerLevels.maxed(), false, true, null).withModifiers(spec);
		for (Combat cls : Combat.values())
		{
			for (Setup result : com.bestgearsetup.calc.Optimizers.of(data, ctx, budget(200_000_000, 3), itemId -> false, PRICE)
				.optimize(cls, () -> false, done -> { }))
			{
				sane(result.getDps());
				assertNotNull(result.getLoadout().getWeapon().getName(), SpecialAttack.of(result.getLoadout(), ctx));
			}
		}
	}

	@Test
	public void damageOverTimeSearchesStaySane()
	{
		CombatModifiers poison = CombatModifiers.builder().weaponPoison(WeaponPoison.POISON_PLUS_PLUS).build();
		for (int id : new int[]{415, 2267, 12223})
		{
			CombatContext ctx = TestFactories.combatContext(target(id), PlayerLevels.maxed(), false, true, null).withModifiers(poison);
			for (Combat cls : Combat.values())
			{
				for (Setup result : com.bestgearsetup.calc.Optimizers.of(data, ctx, budget(100_000_000, 1), itemId -> false, PRICE)
					.optimize(cls, () -> false, done -> { }))
				{
					sane(result.getDps());
				}
			}
		}
	}
}
