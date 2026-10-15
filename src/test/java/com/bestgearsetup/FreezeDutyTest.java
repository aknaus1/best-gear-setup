package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.CombatContext;
import com.bestgearsetup.calc.DpsCalculator;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.FreezeDuty;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.SearchMode;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import net.runelite.api.gameval.ItemID;

/** A ToB freezer's Maiden freeze: the fewest items from the Maiden setup that land each Ice Barrage often enough. */
public class FreezeDutyTest
{
	private static final Slot[] SWAPPED = {Slot.WEAPON, Slot.BODY, Slot.LEGS, Slot.HEAD, Slot.NECK};

	private static GearItem item(int id, Slot slot)
	{
		GearItem item = new GearItem();
		item.setId(id);
		item.setName("item " + id);
		item.setSlot(slot);
		return item;
	}

	private static Loadout worn(int base)
	{
		Loadout worn = new Loadout();
		for (int slot = 0; slot < SWAPPED.length; slot++) { worn.set(SWAPPED[slot], item(base + slot, SWAPPED[slot])); }
		return worn;
	}

	/** A switch from {@code worn} swapping its first {@code items} slots, landing {@code accuracy} of casts. */
	private static SetupResult freeze(Loadout worn, int items, double accuracy)
	{
		Loadout loadout = worn.copy();
		for (int slot = 0; slot < items; slot++) { loadout.set(SWAPPED[slot], item(900 + slot, SWAPPED[slot])); }
		return new SetupResult(CombatClass.MAGIC, loadout, new DpsResult(accuracy, 30, accuracy, 5, 15 * accuracy, null), 0, 0,
			Collections.emptySet(), Collections.emptySet(), null, worn);
	}

	private static SearchResults results(SetupResult... setups)
	{
		Monster monster = new Monster();
		monster.setName("nylocas matomenos (normal mode maiden)");
		return new SearchResults(monster, 100, Collections.singletonMap(AttackStyle.Type.MAGIC, Arrays.asList(setups)),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false);
	}

	/** A full Maiden setup wearing {@code worn} at this DPS. */
	private static SetupResult maiden(Loadout worn, double dps)
	{
		return new SetupResult(CombatClass.MAGIC, worn, new DpsResult(dps, 0, 1, 4, dps * 2.4, null), 0);
	}

	/** Maiden and her freeze, the freeze a switch from the Maiden setup. */
	private static List<RaidEncounter> maidenFreeze()
	{
		MonsterSummary boss = new MonsterSummary();
		boss.setName("the maiden of sugadinti (normal)");
		MonsterSummary nylocas = new MonsterSummary();
		nylocas.setName("nylocas matomenos (normal mode maiden)");
		return Arrays.asList(new RaidEncounter("Maiden", boss).withSwitchGroup(RaidPlanType.MAIDEN_FREEZE).withFreezeSwitches(),
			new RaidEncounter("Maiden nylocas: freeze", nylocas, AttackStyle.Type.MAGIC)
				.withLoadoutRequirement(com.bestgearsetup.calc.RaidLoadout.ICE_BARRAGE)
				.withSwitchGroup(RaidPlanType.MAIDEN_FREEZE, false).withFreezeSwitches());
	}

	private static RaidPlan plan(List<RaidEncounter> encounters, SearchResults... results)
	{
		return RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ANCIENT, encounters, Arrays.asList(results), 28,
			Long.MAX_VALUE, id -> true, item -> 0, 0, id -> 0, () -> false);
	}

	@Test public void aFreezeOnlySwitchesFromTheMaidenGearItWasSearchedFrom()
	{
		// Regression: a max mage Maiden setup took a freeze searched from melee gear, putting melee pieces on to freeze.
		List<RaidEncounter> encounters = maidenFreeze();
		Loadout mageGear = worn(200);
		Loadout meleeGear = worn(100);
		SetupResult mage = maiden(mageGear, 10);
		SetupResult melee = maiden(meleeGear, 8);
		SetupResult fromMelee = freeze(meleeGear, 2, 1);
		int[] group = {0, 1};
		assertTrue(RaidEncounter.fullSetups(group, Arrays.asList(mage, fromMelee), encounters).isEmpty());
		assertEquals(Collections.singletonList(0), RaidEncounter.fullSetups(group, Arrays.asList(melee, fromMelee), encounters));
		// The finished kit may wear a carried piece in an empty Maiden slot, but not one that lowers magic attack.
		Loadout filled = meleeGear.copy();
		filled.set(Slot.RING, item(300, Slot.RING));
		assertTrue(encounters.get(1).switchableFrom(fromMelee, filled));
		filled.get(Slot.RING).setMagicBonus(-5);
		assertTrue(!encounters.get(1).switchableFrom(fromMelee, filled));
		// With only that freeze, the faster mage setup can't be worn at Maiden.
		RaidPlan plan = plan(encounters, results(mage, melee), results(fromMelee));
		assertEquals(meleeGear.getWeapon().getId(), plan.getSetups().get(0).getLoadout().getWeapon().getId());
		// A freeze searched from the mage gear needs no switch, and the mage setup is worn.
		SetupResult fromMage = freeze(mageGear, 0, 1);
		plan = plan(encounters, results(mage, melee), results(fromMelee, fromMage));
		assertEquals(mageGear.getWeapon().getId(), plan.getSetups().get(0).getLoadout().getWeapon().getId());
		assertEquals(0, FreezeDuty.items(plan.getSetups().get(1)));
		assertTrue(BestGearSetupPlugin.freezeNote(encounters.get(1), encounters.get(0), plan.getSetups().get(1))
			.contains("needs no gear switch"));
	}

	@Test public void aFreezeCannotPassPlanningByLeavingWornArmourOff()
	{
		List<RaidEncounter> encounters = maidenFreeze();
		Loadout worn = worn(100);
		SetupResult unequips = freeze(worn, 1, 1);
		unequips.getLoadout().set(Slot.BODY, null);
		assertTrue(!encounters.get(1).switchableFrom(unequips, worn));
		assertNull(plan(encounters, results(maiden(worn, 10)), results(unequips)));
	}

	@Test public void theFewestItemsReachingTheTargetComeFirstThenAccuracy()
	{
		Loadout worn = worn(100);
		SetupResult staff = freeze(worn, 1, 0.70);
		SetupResult three = freeze(worn, 3, 0.86);
		SetupResult threeBetter = freeze(worn, 3, 0.88);
		SetupResult five = freeze(worn, 5, 0.90);
		assertEquals(3, FreezeDuty.items(three));
		List<SetupResult> ranked = Arrays.asList(five, staff, three, threeBetter);
		ranked.sort(FreezeDuty.preference(0.85));
		assertEquals(Arrays.asList(threeBetter, three, five, staff), ranked);
		// Out of reach, the most accurate comes first.
		ranked.sort(FreezeDuty.preference(0.95));
		assertEquals(Arrays.asList(five, threeBetter, three, staff), ranked);
		assertTrue(FreezeDuty.reaches(freeze(worn, 2, 0.85), 0.85));
	}

	@Test public void anAutocastFreezeBeatsCastingByHandWheneverItLandsOftenEnough()
	{
		Loadout worn = worn(100);
		SetupResult manual = freeze(worn, 1, 1);
		manual.getLoadout().setStyle(AttackStyle.parse("manual cast,magic,manual"));
		SetupResult autocast = freeze(worn, 4, 1);
		autocast.getLoadout().setStyle(AttackStyle.parse("spell,magic,magic"));
		SetupResult missing = freeze(worn, 1, 0.9);
		missing.getLoadout().setStyle(AttackStyle.parse("spell,magic,magic"));
		assertTrue(FreezeDuty.manualCast(manual));
		assertTrue(FreezeDuty.burden(manual) > FreezeDuty.burden(autocast));
		List<SetupResult> ranked = Arrays.asList(manual, missing, autocast);
		ranked.sort(FreezeDuty.preference(FreezeDuty.GUARANTEED));
		// Casting by hand only when no autocast setup guarantees the freeze.
		assertEquals(Arrays.asList(autocast, manual, missing), ranked);
		MonsterSummary nylocas = new MonsterSummary();
		nylocas.setName("nylocas matomenos (normal mode maiden)");
		RaidEncounter freeze = new RaidEncounter("Maiden nylocas: freeze", nylocas, AttackStyle.Type.MAGIC)
			.withLoadoutRequirement(com.bestgearsetup.calc.RaidLoadout.ICE_BARRAGE);
		SearchResults found = results(manual, autocast);
		assertTrue(freeze.estimatedSeconds(found, autocast) < freeze.estimatedSeconds(found, manual));
	}

	@Test public void freezeCandidatesKeepOnlyThoseLandingOftenEnoughWhenAnyDoes()
	{
		Loadout tank = worn(100);
		Loadout scythe = worn(200);
		SetupResult tankFew = freeze(tank, 2, 0.80);
		SetupResult tankMany = freeze(tank, 4, 0.87);
		SetupResult scytheMany = freeze(scythe, 5, 0.83);
		SetupResult scytheFew = freeze(scythe, 1, 0.65);
		List<SetupResult> kept = results(tankFew, tankMany, scytheMany, scytheFew).forFreeze(0.85).getByType()
			.get(AttackStyle.Type.MAGIC);
		assertEquals(Collections.singletonList(tankMany), kept);
		// When none reaches it, each Maiden setup keeps its most accurate switch.
		kept = results(tankFew, tankMany, scytheMany, scytheFew).forFreeze(0.95).getByType().get(AttackStyle.Type.MAGIC);
		assertEquals(Arrays.asList(tankMany, scytheMany), kept);
	}

	@Test public void aFreezeAddsOnlyATokenTimePerItem()
	{
		MonsterSummary nylocas = new MonsterSummary();
		nylocas.setName("nylocas matomenos (normal mode maiden)");
		RaidEncounter freeze = new RaidEncounter("Maiden nylocas: freeze", nylocas, AttackStyle.Type.MAGIC)
			.withLoadoutRequirement(com.bestgearsetup.calc.RaidLoadout.ICE_BARRAGE);
		Loadout worn = worn(100);
		SearchResults found = results(freeze(worn, 2, 0.8));
		double two = freeze.estimatedSeconds(found, freeze(worn, 2, 0.8));
		double four = freeze.estimatedSeconds(found, freeze(worn, 4, 0.8));
		assertEquals(2 * RaidEncounter.FREEZE_ITEM_SECONDS, two, 1e-12);
		assertTrue(two < four);
		assertTrue("Never shown as combat time", four < 0.5);
	}

	@Test public void aSouthFreezerHeartsWhenAHeartCanBeUsed() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		Set<Integer> none = Collections.emptySet();
		assertNull(BestGearSetupPlugin.southFreezeHeart(data, SearchMode.OWNED_ONLY, none, none));
		assertEquals("imbued heart", BestGearSetupPlugin.southFreezeHeart(data, SearchMode.OWNED_ONLY,
			Collections.singleton(ItemID.IMBUED_HEART), none));
		Set<Integer> both = new HashSet<>(Arrays.asList(ItemID.IMBUED_HEART, ItemID.SATURATED_HEART));
		assertEquals("saturated heart", BestGearSetupPlugin.southFreezeHeart(data, SearchMode.OWNED_ONLY, both, none));
		// Hearts can't be bought, and best in slot assumes every boost unless it is excluded.
		assertNull(BestGearSetupPlugin.southFreezeHeart(data, SearchMode.BUDGET, none, none));
		assertEquals("saturated heart", BestGearSetupPlugin.southFreezeHeart(data, SearchMode.UNLIMITED, none, none));
		assertEquals("imbued heart", BestGearSetupPlugin.southFreezeHeart(data, SearchMode.UNLIMITED, none,
			Collections.singleton(ItemID.SATURATED_HEART)));
		List<String> magic = data.getPotions("magic").stream().map(potion -> potion.getName()).collect(Collectors.toList());
		assertTrue(magic.containsAll(Arrays.asList("imbued heart", "saturated heart")));
	}

	@Test public void packingSpecsOrOtherGearCannotLowerTheAvailableFreezeAccuracy() throws Exception
	{
		Loadout worn = new Loadout();
		worn.set(Slot.WEAPON, item(9500, Slot.WEAPON));
		GearItem staff = item(9501, Slot.WEAPON);
		GearItem robes = item(9502, Slot.BODY);
		GearItem damageBody = item(9503, Slot.BODY);
		GameData data = new GameData(Collections.emptyList(), Collections.emptyMap(),
			Arrays.asList(worn.getWeapon(), staff, robes, damageBody), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		List<RaidEncounter> encounters = new java.util.ArrayList<>(maidenFreeze());
		encounters.add(new RaidEncounter("Later boss", new MonsterSummary()));
		SetupResult maiden = maiden(worn, 10);
		Loadout strongBody = worn.copy(); strongBody.set(Slot.BODY, damageBody);
		Loadout sharedBody = worn.copy(); sharedBody.set(Slot.BODY, robes);
		SetupResult fast = maiden(strongBody, 10);
		SetupResult shared = maiden(sharedBody, 9);
		for (double availableAccuracy : new double[]{1, 0.9})
		{
			Loadout full = worn.copy(); full.set(Slot.WEAPON, staff); full.set(Slot.BODY, robes);
			Loadout weak = worn.copy(); weak.set(Slot.WEAPON, staff);
			SetupResult accurate = new SetupResult(CombatClass.MAGIC, full,
				new DpsResult(5, 30, availableAccuracy, 5, 15, null), 0, 0,
				Collections.emptySet(), Collections.emptySet(), null, worn);
			SetupResult inaccurate = new SetupResult(CombatClass.MAGIC, weak,
				new DpsResult(5, 30, 0.8, 5, 15, null), 0, 0,
				Collections.emptySet(), Collections.emptySet(), null, worn);
			RaidPlan plan = RaidConsolidator.select(data, RaidPlanType.TOB, RaidSpellbook.ANCIENT, encounters,
				Arrays.asList(results(maiden), results(accurate), results(fast)), Collections.emptySet(), 1, Long.MAX_VALUE,
				id -> true, gear -> 0, 0, id -> 0, () -> false, (index, excluded, pass) -> index == 0 ? results(maiden)
					: index == 1 ? results(excluded.contains(robes.getId()) ? inaccurate : accurate).forFreeze(FreezeDuty.GUARANTEED)
					: results(excluded.contains(damageBody.getId()) ? shared : fast));
			org.junit.Assert.assertNotNull(plan);
			assertEquals("Packing must preserve the accuracy found before dropping gear", availableAccuracy,
				plan.getSetups().get(1).getDps().getAccuracy(), 1e-12);
			assertTrue(plan.getEquipment().contains(robes));
		}
	}

	@Test public void theReportedFourItemSwitchNeedsItsHeartAndAugury() throws Exception
	{
		GameDataLoader loader = new GameDataLoader(new Gson());
		GameData data = loader.loadGameData();
		Loadout loadout = new Loadout();
		String[] names = {"torva full helm", "infernal cape", "amulet of rancour", "eye of ayak", "oathplate chest",
			"elidinis' ward (or)", "ancestral robe bottom", "confliction gauntlets", "avernic treads (max)", "ultor ring"};
		for (String name : names)
		{
			GearItem item = Arrays.stream(Slot.values()).flatMap(slot -> data.getItems(slot).stream())
				.filter(gear -> gear.getName().equals(name)).findFirst().get();
			loadout.set(item.getSlot(), item);
		}
		assertEquals(86, Arrays.stream(Slot.values()).map(loadout::get).filter(java.util.Objects::nonNull)
			.mapToInt(GearItem::getMagicBonus).sum());
		loadout.setStyle(AttackStyle.parse("manual cast,magic,manual"));
		loadout.setSpell(data.getSpells().stream().filter(spell -> spell.getName().equals("ice barrage")).findFirst().get());
		Monster target = loader.getMonster(data.getMonsters().stream()
			.filter(monster -> monster.getName().equals("nylocas matomenos (normal mode maiden)")).findFirst().get());
		com.bestgearsetup.data.OffensivePrayer augury = data.getPrayers(CombatClass.MAGIC).stream()
			.filter(prayer -> prayer.getName().equals("augury")).findFirst().get();
		com.bestgearsetup.data.Potion heart = data.getPotions("magic").stream()
			.filter(potion -> potion.getName().equals("saturated heart")).findFirst().get();
		java.util.Map<CombatClass, com.bestgearsetup.data.OffensivePrayer> prayers = Collections.singletonMap(CombatClass.MAGIC, augury);
		DpsResult boosted = DpsCalculator.calculate(loadout, new CombatContext(target, PlayerLevels.maxed(), false,
			99, 99, 99, heart.boost(99), prayers, false));
		assertEquals(1, boosted.getAccuracy(), 1e-12);
		assertTrue(DpsCalculator.calculate(loadout, new CombatContext(target, PlayerLevels.maxed(), false,
			99, 99, 99, 99, prayers, false)).getAccuracy() < 1);
		assertTrue(DpsCalculator.calculate(loadout, new CombatContext(target, PlayerLevels.maxed(), false,
			99, 99, 99, heart.boost(99), new java.util.EnumMap<>(CombatClass.class), false)).getAccuracy() < 1);
		SetupResult setup = new SetupResult(CombatClass.MAGIC, loadout, boosted, 0);
		SearchResults result = new SearchResults(target, 100, Collections.singletonMap(AttackStyle.Type.MAGIC, Collections.singletonList(setup)),
			Collections.emptyList(), prayers, Collections.singletonMap(CombatClass.MAGIC, Collections.singletonList(heart)), false);
		String note = BestGearSetupPlugin.freezeAssumptions(result, setup);
		assertTrue(note, note.contains("Augury") && note.contains("Saturated Heart"));
	}
}
