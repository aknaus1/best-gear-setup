package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/** The Dawnbringer is passed around at Verzik P1 and only used for its special, never as the worn weapon. */
public class DawnbringerSpecTest
{
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
	}

	private final GearItem sword = weapon(100, "sword", "slash sword", 4, "chop,slash,aggressive");
	private final GearItem dawnbringer = weapon(22516, "dawnbringer", "powered staff", 4, "accurate,magic,accurate");

	public DawnbringerSpecTest()
	{
		sword.setSlashBonus(90);
		sword.setMeleeStr(90);
		dawnbringer.setMagicBonus(10);
		dawnbringer.setTradeable(false);
		dawnbringer.setPrice(-1);
	}

	private List<SetupResult> run(String name, int id, CombatClass cls, boolean specs)
	{
		Monster target = monster(200, 20);
		target.setName(name);
		target.setId(id);
		target.setHitpoints(2000);
		GameData data = TestData.gameData(Arrays.asList(sword, dawnbringer), Collections.emptyList());
		CombatContext ctx = new CombatContext(target, PlayerLevels.maxed(), false, true, piety());
		// Only the sword is owned: the Dawnbringer is picked up in the room.
		return new Optimizer(data, ctx, OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY).killSpecials(specs)
			.spellbooks(Collections.singleton("standard")).build(), id2 -> id2 == sword.getId(), GearItem::getPrice)
			.optimize(cls, () -> false);
	}

	@Test
	public void verzikP1SetupsSpecWithTheDawnbringerButNeverWearIt()
	{
		List<SetupResult> melee = run("verzik vitur (phase 1)", 8370, CombatClass.MELEE, true);
		assertTrue(melee.stream().noneMatch(r -> WeaponRules.specOnly(r.getLoadout().getWeapon())));
		SetupResult best = melee.get(0);
		assertEquals(sword, best.getLoadout().getWeapon());
		SpecialPlan plan = best.getSpecial();
		assertNotNull(plan);
		assertEquals(dawnbringer, plan.getWeapon());
		assertEquals("Dawnbringer", plan.getSpecial().getName());
		assertEquals(35, plan.getSpecial().getEnergy());
		assertTrue(plan.getSpecsPerKill() > 0);
		// Picked up in the room: nothing to buy and no unknown price.
		assertEquals(0, plan.getExtraCost());
		assertEquals(0, plan.getUnpricedItems());
		// Its special always hits for 75-150, past P1's per-hit caps.
		assertEquals(150, plan.getSpec().getMaxHit());
		assertEquals(1, plan.getSpec().getAccuracy(), 1e-9);
		assertTrue(best.getDps().getDps() > plan.getOrdinary().getDps());
		// Never the worn weapon, even for magic, where it is the only staff.
		assertTrue(run("verzik vitur (phase 1)", 8370, CombatClass.MAGIC, true).stream()
			.noneMatch(r -> WeaponRules.specOnly(r.getLoadout().getWeapon())));
	}

	@Test
	public void theDawnbringerIsOnlyAvailableAtVerzikP1AndOnlyWithSpecials()
	{
		assertNull(run("verzik vitur (phase 1)", 8370, CombatClass.MELEE, false).get(0).getSpecial());
		SetupResult p2 = run("verzik vitur (phase 2)", 1, CombatClass.MELEE, true).get(0);
		assertTrue(p2.getSpecial() == null || !WeaponRules.specOnly(p2.getSpecial().getWeapon()));
	}
}
