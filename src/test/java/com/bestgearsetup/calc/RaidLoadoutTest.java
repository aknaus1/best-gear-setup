package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;

public class RaidLoadoutTest
{
	private static GameData data;
	private static GearItem staff;
	private static GearItem powered;
	private static GearItem bow;
	private static GearItem chins;

	@BeforeClass public static void load() throws Exception
	{
		Gson gson = new Gson();
		OwnershipRules.init(gson);
		GameData full = new GameDataLoader(gson).loadGameData();
		Set<String> names = new HashSet<>(Arrays.asList("ancient staff", "trident of the swamp", "magic shortbow",
			"red chinchompa"));
		List<GearItem> weapons = full.getItems(Slot.WEAPON).stream().filter(w -> names.contains(w.getName())).collect(Collectors.toList());
		GearItem arrows = full.getItems(Slot.AMMO).stream().filter(w -> w.getName().equals("rune arrow")).findFirst().get();
		data = new GameData(Collections.emptyList(), Collections.singletonMap(Slot.AMMO.getApiKey(), Collections.singletonList(arrows)),
			weapons, full.getSpells(), Collections.emptyMap(), Collections.emptyMap());
		staff = named("ancient staff"); powered = named("trident of the swamp");
		bow = named("magic shortbow"); chins = named("red chinchompa");
	}

	private static GearItem named(String name)
	{
		return data.getItems(Slot.WEAPON).stream().filter(w -> w.getName().equals(name)).findFirst().get();
	}

	private OptimizerSettings.OptimizerSettingsBuilder settings(RaidLoadout requirement)
	{
		return OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY).spellbooks(Collections.singleton("ancient"))
			.styles(requirement == RaidLoadout.CHINCHOMPAS ? Collections.singleton(AttackStyle.Type.RANGED)
				: Collections.singleton(AttackStyle.Type.MAGIC)).raidLoadout(requirement);
	}

	private List<SetupResult> run(OptimizerSettings settings, int missingItem, int magic)
	{
		CombatContext ctx = new CombatContext(TestData.monster(20, 0), PlayerLevels.maxed(), false, 99, 99, 99,
			magic, new EnumMap<>(CombatClass.class), false);
		return new Optimizer(data, ctx, settings, id -> data.findItem(id) != null && id != missingItem, GearItem::getPrice)
			.optimize(settings.getRaidLoadout() == RaidLoadout.CHINCHOMPAS ? CombatClass.RANGED : CombatClass.MAGIC, () -> false);
	}

	@Test public void freezeUsesIceBarrageAcrossSearchDepths()
	{
		for (SearchDepth depth : SearchDepth.values())
		{
			List<SetupResult> results = run(settings(RaidLoadout.ICE_BARRAGE).depth(depth).build(), -1, 99);
			assertFalse(depth.name(), results.isEmpty());
			for (SetupResult result : results)
			{
				assertEquals("ice barrage", result.getLoadout().getSpell().getLowerName());
				assertTrue(result.getLoadout().getStyle().isSpellCast());
				assertEquals(5, result.getDps().getAttackSpeedTicks());
			}
		}
	}

	@Test public void ownedPoweredStaffCanManuallyFreezeWithoutAnAutocastStaff()
	{
		for (SearchDepth depth : SearchDepth.values())
		{
			OptimizerSettings required = settings(RaidLoadout.ICE_BARRAGE).depth(depth)
				.locks(Collections.singletonMap(Slot.WEAPON, SlotLock.item(powered.getId()))).build();
			List<SetupResult> results = run(required, staff.getId(), 99);
			assertFalse(depth.name(), results.isEmpty());
			for (SetupResult result : results)
			{
				assertEquals(powered, result.getLoadout().getWeapon());
				assertEquals("ice barrage", result.getLoadout().getSpell().getLowerName());
				assertTrue(result.getLoadout().getStyle().isManualCast());
				assertEquals(5, result.getDps().getAttackSpeedTicks());
			}
		}
	}

	@Test public void unavailableFreezeNeverFallsBackToBuiltInAttacksOrBloodSpells()
	{
		assertTrue(run(settings(RaidLoadout.ICE_BARRAGE).build(), -1, 93).isEmpty());
		assertTrue(run(settings(RaidLoadout.ICE_BARRAGE).spellbooks(Collections.emptySet()).build(), -1, 99).isEmpty());
		assertTrue(run(settings(RaidLoadout.ICE_BARRAGE).styles(Collections.singleton(AttackStyle.Type.RANGED)).build(), -1, 99).isEmpty());
	}

	@Test public void manualIceBarrageUsesTheSpellsDamageAndReach()
	{
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, TestData.weapon(1, "black salamander", "salamander", 4, "blaze,magic,defensive"));
		loadout.setStyle(AttackStyle.parse("manual cast,magic,manual"));
		loadout.setSpell(data.getSpells().stream().filter(s -> s.getLowerName().equals("ice barrage")).findFirst().get());
		CombatContext ctx = new CombatContext(TestData.monster(20, 0), PlayerLevels.maxed(), false, 99, 99, 99,
			99, new EnumMap<>(CombatClass.class), false).withFightOptions(1, 8);
		DpsResult result = DpsCalculator.calculate(loadout, ctx);
		assertTrue(result.getDps() > 0);
		assertEquals(30, result.getMaxHit());
		assertEquals(5, result.getAttackSpeedTicks());
	}

	@Test public void clumpUsesChinsAndCannotFallBackToALockedOrOwnedBow()
	{
		List<SetupResult> results = run(settings(RaidLoadout.CHINCHOMPAS).build(), -1, 99);
		assertFalse(results.isEmpty());
		assertTrue(results.stream().allMatch(r -> r.getLoadout().getWeapon().equals(chins)));
		assertTrue(run(settings(RaidLoadout.CHINCHOMPAS).build(), chins.getId(), 99).isEmpty());
		assertTrue(run(settings(RaidLoadout.CHINCHOMPAS).locks(Collections.singletonMap(Slot.WEAPON,
			SlotLock.item(bow.getId()))).build(), -1, 99).isEmpty());
	}
}
