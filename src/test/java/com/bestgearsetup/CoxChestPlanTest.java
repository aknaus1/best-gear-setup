package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SpecialPlan;
import com.bestgearsetup.calc.SupportSpell;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

public class CoxChestPlanTest
{
	@BeforeClass public static void ownership() { OwnershipRules.init(RuneLiteAPI.GSON); }

	@Test public void fixedLayoutUsesFourFloorsAndKeepsEveryRoomPhaseTogether() throws Exception
	{
		GameData data = new GameDataLoader(RuneLiteAPI.GSON).loadGameData();
		List<RaidEncounter> fights = RaidPlanType.COX_CM.encounters(data);
		assertEquals(Arrays.asList(1, 2, 3, 4), fights.stream().map(CoxChestPlan::floor)
			.filter(floor -> floor > 0).distinct().collect(Collectors.toList()));
		for (RaidEncounter fight : fights)
		{
			String room = RaidProgress.room(RaidPlanType.COX_CM, fight.getTarget().getName());
			if (room == null) { assertTrue(fight.isDisplayOnly()); continue; }
			int expected = Arrays.asList("tekton", "ice demon", "shamans").contains(room) ? 1
				: Arrays.asList("vanguards", "vespula", "tightrope").contains(room) ? 2 : room.equals("olm") ? 4 : 3;
			assertEquals(fight.getLabel(), expected, CoxChestPlan.floor(fight));
		}
	}

	@Test public void separatesFinishedGearFromLaterFloorGearAndRetainsLaterRoomsOnThisFloor()
	{
		GearItem maul = item(100, "Elder maul", Slot.WEAPON);
		GearItem bow = item(101, "Twisted bow", Slot.WEAPON);
		GearItem pick = item(102, "Dragon pickaxe", Slot.WEAPON);
		GearItem salve = item(103, "Salve amulet(ei)", Slot.NECK);
		RaidPlan plan = plan(new String[]{"tekton (normal)", "lizardman shaman (normal)", "vanguard (magic)",
			"guardian (normal)", "skeletal mystic (normal)", "great olm (head)"},
			new SetupResult[]{setup(maul), setup(bow), setup(bow), setup(pick), setup(bow, salve), setup(bow)}, maul, bow, pick, salve);
		CoxChestPlan first = advice(plan, 1);
		assertEquals(CoxChestPlan.FINISHED_COLOR, deposit(first, maul).getColor());
		assertNull(deposit(first, maul).getNextUse());
		assertEquals(3, deposit(first, pick).getNextFloor());
		assertEquals(CoxChestPlan.LATER_COLOR, deposit(first, pick).getColor());
		assertFalse(ids(first).contains(bow.getId()));
		CoxChestPlan guardians = advice(plan, 3);
		assertFalse("Keep the pickaxe during Guardians", ids(guardians).contains(pick.getId()));
		assertFalse("Keep salve for Mystics later this floor", ids(guardians).contains(salve.getId()));
		assertNull(deposit(advice(plan, 4), pick).getNextUse());
		assertNull(deposit(advice(plan, 5), salve).getNextUse());
	}

	@Test public void previewNeverBecomesAnImmediateDepositAndLastFloorHasNoPreview()
	{
		GearItem crush = item(100, "Crush weapon", Slot.WEAPON);
		GearItem bow = item(101, "Bow", Slot.WEAPON);
		RaidPlan plan = plan(new String[]{"tekton (normal)", "lizardman shaman (normal)", "vanguard (magic)", "great olm (head)"},
			new SetupResult[]{setup(crush), setup(bow), setup(bow), setup(bow)}, crush, bow);
		CoxChestPlan first = advice(plan, 0);
		assertTrue(first.getDeposits().isEmpty());
		assertEquals(2, first.getPreviewFloor());
		assertEquals(crush, first.getPreview().get(0).getItem());
		RaidPlan last = plan(new String[]{"great olm (head)"}, new SetupResult[]{setup(bow)}, bow);
		assertTrue(advice(last, 0).getDeposits().isEmpty());
		assertEquals(0, advice(last, 0).getPreviewFloor());
	}

	@Test public void keepsOtherVanguardsAndOlmHandsWhenBrowsingLastPhase()
	{
		GearItem melee = item(100, "Lance", Slot.WEAPON);
		GearItem magic = item(101, "Staff", Slot.WEAPON);
		GearItem ranged = item(102, "Bow", Slot.WEAPON);
		RaidPlan plan = plan(new String[]{"vanguard (ranged)", "vanguard (melee)", "vanguard (magic)",
			"great olm (melee hand)", "great olm (mage hand)", "great olm (head)"},
			new SetupResult[]{setup(melee), setup(magic), setup(ranged), setup(melee), setup(magic), setup(ranged)}, melee, magic, ranged);
		assertTrue(advice(plan, 2).getDeposits().isEmpty());
		assertTrue(advice(plan, 5).getDeposits().isEmpty());
	}

	@Test public void greenIncludesLaterRoomsAndChangesToTheNextFloorAfterTheLastRoomDies()
	{
		GearItem ranged = item(100, "Ranged weapon", Slot.WEAPON);
		GearItem magic = item(101, "Magic weapon", Slot.WEAPON);
		GearItem melee = item(102, "Melee weapon", Slot.WEAPON);
		RaidPlan plan = plan(new String[]{"lizardman shaman (normal)", "vanguard (melee)",
			"vanguard (ranged)", "great olm (head)"},
			new SetupResult[]{setup(ranged), setup(magic), setup(melee), setup(ranged)}, ranged, magic, melee);
		CoxChestPlan first = advice(plan, 0);
		assertEquals(CoxChestPlan.NEEDED_COLOR, first.colorFor(ranged));
		assertEquals(CoxChestPlan.LATER_COLOR, first.colorFor(magic));
		assertEquals(CoxChestPlan.LATER_COLOR, first.colorFor(melee));
		RaidProgress progress = new RaidProgress();
		progress.tick(RaidPlanType.COX_CM, plan, true);
		progress.died(new Object(), plan.getEncounters().get(0).getTarget());
		progress.settleDeaths(Collections.emptySet());
		RaidEncounter next = plan.getEncounters().stream().filter(fight -> fight.getLabel().equals(progress.current().getEncounter()))
			.findFirst().get();
		CoxChestPlan following = CoxChestPlan.forEncounter(plan, next);
		assertEquals(2, following.getFloor());
		assertEquals(CoxChestPlan.NEEDED_COLOR, following.colorFor(magic));
		assertEquals("Another Vanguard style is needed on the same floor", CoxChestPlan.NEEDED_COLOR, following.colorFor(melee));
		assertEquals("Save ranged gear for Olm", CoxChestPlan.LATER_COLOR, following.colorFor(ranged));
	}

	@Test public void preservesSpecialPartnersOffhandsAmmunitionAndFilledPieces()
	{
		GearItem bow = item(100, "Bow", Slot.WEAPON);
		GearItem specWeapon = item(101, "Spec weapon", Slot.WEAPON);
		GearItem offhand = item(102, "Spec shield", Slot.SHIELD);
		GearItem ammo = item(103, "Spec ammo", Slot.AMMO);
		GearItem partner = item(104, "Second spec", Slot.WEAPON);
		GearItem helm = item(105, "Prayer helm", Slot.HEAD);
		SpecialPlan other = new SpecialPlan(partner, null, null, null, null, null, null, null, null, null, 1, 0, 0);
		SpecialPlan special = new SpecialPlan(specWeapon, offhand, ammo, null, null, null, null, null, null, null, 1, 0, 0,
			null, 0, Collections.singletonList(other), -1, null);
		Loadout loadout = setup(bow, helm).getLoadout();
		SetupResult setup = new SetupResult(CombatClass.RANGED, loadout, null, 0, 0, Collections.singleton(Slot.HEAD),
			Collections.emptySet(), special);
		RaidPlan plan = plan(new String[]{"vasa nistirio (normal)"}, new SetupResult[]{setup}, bow, specWeapon, offhand, ammo, partner, helm);
		assertTrue(advice(plan, 0).getDeposits().isEmpty());
	}

	@Test public void keepsAxesForMeatTreeAndBookForThrallsWithoutRecommendingSupplies()
	{
		GearItem bow = item(100, "Bow", Slot.WEAPON);
		GearItem axe = item(101, "Bronze axe", Slot.WEAPON);
		GearItem book = item(ItemID.BOOK_OF_THE_DEAD, "Book of the dead", Slot.SHIELD);
		Loadout loadout = setup(bow).getLoadout();
		SetupResult thralls = new SetupResult(CombatClass.RANGED, loadout, null, 0, 0, Collections.emptySet(),
			Collections.singleton(SupportSpell.THRALL), null);
		RaidPlan plan = plan(new String[]{"vanguard (magic)", "muttadile (small)", "great olm (head)"},
			new SetupResult[]{thralls, setup(bow), thralls}, bow, axe, book);
		assertEquals(3, deposit(advice(plan, 0), axe).getNextFloor());
		assertFalse(ids(advice(plan, 1)).contains(axe.getId()));
		assertFalse(ids(advice(plan, 0)).contains(book.getId()));
		assertEquals(Collections.singletonList(axe.getId()), ids(advice(plan, 2)));
	}

	@Test public void liveRefreshCannotRecommendItsNewlySelectedWeaponForDeposit()
	{
		GearItem bow = item(100, "Bow", Slot.WEAPON);
		GearItem other = item(101, "Alternative bow", Slot.WEAPON);
		RaidPlan plan = plan(new String[]{"skeletal mystic (normal)", "great olm (head)"},
			new SetupResult[]{setup(bow), setup(bow)}, bow, other);
		assertTrue(ids(advice(plan, 0)).contains(other.getId()));
		assertTrue(CoxChestPlan.forEncounter(plan, plan.getEncounters().get(0), setup(other)).getDeposits().isEmpty());
	}

	@Test public void otherRaidsAndUnknownRoomsProduceNoAdvice()
	{
		GearItem weapon = item(100, "Weapon", Slot.WEAPON);
		RaidPlan unknown = plan(new String[]{"unknown"}, new SetupResult[]{setup(weapon)}, weapon);
		assertEquals(0, advice(unknown, 0).getFloor());
		RaidPlan other = new RaidPlan(RaidPlanType.TOB, unknown.getSpellbook(), unknown.getEncounters(), unknown.getResults(),
			unknown.getSetups(), unknown.getEquipment(), unknown.getInventory(), 10, 0);
		assertTrue(advice(other, 0).getDeposits().isEmpty());
	}

	private static CoxChestPlan advice(RaidPlan plan, int index) { return CoxChestPlan.forEncounter(plan, plan.getEncounters().get(index)); }
	private static List<Integer> ids(CoxChestPlan advice)
	{ return advice.getDeposits().stream().map(deposit -> deposit.getItem().getId()).collect(Collectors.toList()); }
	private static CoxChestPlan.Deposit deposit(CoxChestPlan advice, GearItem item)
	{ return advice.getDeposits().stream().filter(deposit -> deposit.getItem() == item).findFirst().get(); }
	private static GearItem item(int id, String name, Slot slot)
	{
		GearItem item = new GearItem(); item.setId(id); item.setName(name); item.setSlot(slot); return item;
	}
	private static SetupResult setup(GearItem... items)
	{
		Loadout loadout = new Loadout();
		for (GearItem item : items) { loadout.set(item.getSlot(), item); }
		return new SetupResult(CombatClass.RANGED, loadout, null, 0);
	}
	private static RaidPlan plan(String[] names, SetupResult[] setups, GearItem... gear)
	{
		List<RaidEncounter> encounters = Arrays.stream(names).map(name ->
		{
			MonsterSummary target = new MonsterSummary(); target.setName(name);
			return new RaidEncounter(name, target, new AttackStyle.Type[0]);
		}).collect(Collectors.toList());
		return new RaidPlan(RaidPlanType.COX_CM, RaidSpellbook.ARCEUUS, encounters, Collections.emptyList(), Arrays.asList(setups),
			Arrays.asList(gear), Collections.singletonList(new RaidPlan.InventoryItem("Prayer potion", Collections.singletonList(2434), false)), 20, 0);
	}
}
