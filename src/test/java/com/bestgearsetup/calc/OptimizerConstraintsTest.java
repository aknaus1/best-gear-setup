package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class OptimizerConstraintsTest
{
	/** Main code gets the client's Gson in startUp; tests use the same instance. */
	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
	}

	private GearItem whip;
	private GearItem stabSword;
	private GearItem godsword;
	private GearItem strongHelm;
	private GearItem tankHelm;
	private GearItem prayerHelm;
	private GearItem tankBoots;
	private GearItem membersRing;
	private List<GearItem> items;

	@Before
	public void setUp()
	{
		whip = weapon(4151, "whip", "whip", 4, "flick,slash,accurate", "lash,slash,controlled", "deflect,slash,defensive");
		whip.setSlashBonus(82);
		whip.setMeleeStr(82);
		whip.setPrice(1_500_000);

		stabSword = weapon(4587, "stab sword", "stab sword", 4, "stab,stab,accurate", "lunge,stab,aggressive",
			"slash,slash,aggressive", "block,stab,defensive");
		stabSword.setStabBonus(60);
		stabSword.setSlashBonus(60);
		stabSword.setMeleeStr(60);
		stabSword.setPrice(100_000);

		godsword = weapon(11804, "godsword", "2h sword", 5, "chop,slash,accurate", "slash,slash,aggressive");
		godsword.setSlashBonus(200);
		godsword.setMeleeStr(200);
		godsword.setTwoHanded(true);
		godsword.setPrice(20_000_000);

		strongHelm = item(100, "strong helm", Slot.HEAD);
		strongHelm.setMeleeStr(5);
		strongHelm.setStabDef(10);
		strongHelm.setPrice(1_000);
		tankHelm = item(101, "tank helm", Slot.HEAD);
		tankHelm.setStabDef(60);
		tankHelm.setSlashDef(60);
		tankHelm.setCrushDef(60);
		tankHelm.setPrice(1_000);
		prayerHelm = item(102, "prayer helm", Slot.HEAD);
		prayerHelm.setPrayerBonus(5);
		prayerHelm.setPrice(1_000);
		tankBoots = item(200, "tank boots", Slot.FEET);
		tankBoots.setSlashDef(20);
		tankBoots.setPrice(500);
		membersRing = item(300, "members ring", Slot.RING);
		membersRing.setMeleeStr(8);
		membersRing.setMembers(true);
		membersRing.setPrice(1_000);

		items = new ArrayList<>(Arrays.asList(whip, stabSword, godsword, strongHelm, tankHelm, prayerHelm, tankBoots, membersRing));
	}

	private SetupResult best(OptimizerSettings.OptimizerSettingsBuilder b)
	{
		return best(b, monster(100, 20));
	}

	private SetupResult best(OptimizerSettings.OptimizerSettingsBuilder b, Monster target)
	{
		List<SetupResult> r = run(b, target, false);
		return r.isEmpty() ? null : r.get(0);
	}

	private List<SetupResult> run(OptimizerSettings.OptimizerSettingsBuilder b, Monster target, boolean thrall)
	{
		GameData data = TestData.gameData(items, Collections.emptyList());
		CombatContext base = new CombatContext(target, PlayerLevels.maxed(), false, true, piety());
		CombatContext ctx = new CombatContext(target, PlayerLevels.maxed(), false, base.getAttack(), base.getStrength(),
			base.getRanged(), base.getMagic(), piety(), thrall);
		OptimizerSettings settings = b.mode(SearchMode.UNLIMITED)
			.spellbooks(Collections.singleton("standard"))
			.build();
		return new Optimizer(data, ctx, settings, id -> false, GearItem::getPrice).optimize(CombatClass.MELEE, () -> false);
	}

	private static OptimizerSettings.OptimizerSettingsBuilder settings()
	{
		return OptimizerSettings.builder();
	}

	@Test
	public void baselinePicksGodswordAndStrongHelm()
	{
		SetupResult r = best(settings());
		assertEquals(godsword, r.getLoadout().getWeapon());
		assertEquals(strongHelm, r.getLoadout().get(Slot.HEAD));
	}

	@Test
	public void weaponLockForcesWeapon()
	{
		Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
		locks.put(Slot.WEAPON, SlotLock.item(4151));
		SetupResult r = best(settings().locks(locks));
		assertEquals(whip, r.getLoadout().getWeapon());
	}

	@Test
	public void emptyLockAndExclusion()
	{
		Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
		locks.put(Slot.HEAD, SlotLock.empty());
		assertNull(best(settings().locks(locks)).getLoadout().get(Slot.HEAD));

		SetupResult r = best(settings().excluded(new HashSet<>(Arrays.asList(11804, 100))));
		assertEquals(whip, r.getLoadout().getWeapon());
		assertTrue(r.getLoadout().get(Slot.HEAD) != strongHelm);
	}

	@Test
	public void shieldLockRulesOutTwoHanders()
	{
		GearItem shield = item(400, "shield", Slot.SHIELD);
		items.add(shield);
		Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
		locks.put(Slot.SHIELD, SlotLock.item(400));
		SetupResult r = best(settings().locks(locks));
		assertEquals(whip, r.getLoadout().getWeapon());
		assertEquals(shield, r.getLoadout().get(Slot.SHIELD));
	}

	@Test
	public void membersAndHandsFilters()
	{
		assertEquals(membersRing, best(settings()).getLoadout().get(Slot.RING));
		assertNull(best(settings().membersItems(false)).getLoadout().get(Slot.RING));
		assertTrue(!best(settings().weaponHands(WeaponHands.ONE_HANDED)).getLoadout().getWeapon().isTwoHanded());
		assertEquals(godsword, best(settings().weaponHands(WeaponHands.TWO_HANDED)).getLoadout().getWeapon());
	}

	@Test
	public void styleFilterRemovesSlash()
	{
		SetupResult r = best(settings().styles(EnumSet.of(AttackStyle.Type.STAB, AttackStyle.Type.CRUSH)));
		assertEquals(stabSword, r.getLoadout().getWeapon());
		assertEquals(AttackStyle.Type.STAB, r.getLoadout().getStyle().getType());
	}

	@Test
	public void experienceFilter()
	{
		// Only the whip's controlled style gives defence; defence XP off must never pick it.
		Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
		locks.put(Slot.WEAPON, SlotLock.item(4151));
		SetupResult noDef = best(settings().locks(locks).defenceXp(false));
		assertEquals("accurate", noDef.getLoadout().getStyle().getStance());

		// Strength only: Attack and Defence off leave aggressive, never accurate or the shared controlled style.
		String stance = best(settings().attackXp(false).defenceXp(false)).getLoadout().getStyle().getStance();
		assertEquals("aggressive", stance);
	}

	@Test
	public void fillDefenceRespectsMargin()
	{
		// Boots add no DPS, so they are filled; the tank helm costs DPS so needs a margin.
		SetupResult r = best(settings().fillMode(FillMode.DEFENCE).defenceFocus(DefenceFocus.MELEE));
		assertEquals(tankBoots, r.getLoadout().get(Slot.FEET));
		assertEquals(strongHelm, r.getLoadout().get(Slot.HEAD));
		assertTrue(r.getFilledSlots().contains(Slot.FEET));

		SetupResult generous = best(settings().fillMode(FillMode.DEFENCE).defenceFocus(DefenceFocus.MELEE).fillMarginPercent(50));
		assertEquals(tankHelm, generous.getLoadout().get(Slot.HEAD));
	}

	@Test
	public void fillingReordersResultsBeforeApplyingTheResultLimit()
	{
		GearItem fast = weapon(501, "fast sword", "sword", 4, "slash,slash,aggressive");
		fast.setMeleeStr(52);
		fast.setSlashBonus(80);
		GearItem slow = weapon(502, "slow sword", "sword", 5, "slash,slash,aggressive");
		slow.setMeleeStr(120);
		slow.setSlashBonus(80);
		GearItem strengthHelm = item(503, "strength helm", Slot.HEAD);
		strengthHelm.setMeleeStr(30);
		GearItem defenceHelm = item(504, "tank helm", Slot.HEAD);
		defenceHelm.setSlashDef(100);
		GameData data = TestData.gameData(Arrays.asList(fast, slow, strengthHelm, defenceHelm), Collections.emptyList());
		CombatContext ctx = new CombatContext(monster(100, 20), PlayerLevels.maxed(), false, true, piety());
		OptimizerSettings settings = settings().mode(SearchMode.UNLIMITED).fillMode(FillMode.DEFENCE)
			.fillMarginPercent(15).resultsPerClass(2).build();
		List<SetupResult> before = new Optimizer(data, ctx, settings.toBuilder().fillMode(FillMode.NONE).build(),
			id -> true, GearItem::getPrice).optimize(CombatClass.MELEE, () -> false);
		assertEquals(slow, before.get(0).getLoadout().getWeapon());
		List<SetupResult> after = new Optimizer(data, ctx, settings, id -> true, GearItem::getPrice)
			.optimize(CombatClass.MELEE, () -> false);
		assertEquals(2, after.size());
		assertEquals(fast, after.get(0).getLoadout().getWeapon());
		assertTrue(after.get(0).getDps().getDps() > after.get(1).getDps().getDps());
		assertEquals(defenceHelm, after.get(1).getLoadout().get(Slot.HEAD));
		List<SetupResult> limited = new Optimizer(data, ctx, settings.toBuilder().resultsPerClass(1).build(),
			id -> true, GearItem::getPrice).optimize(CombatClass.MELEE, () -> false);
		assertEquals(1, limited.size());
		assertEquals(fast, limited.get(0).getLoadout().getWeapon());
	}

	@Test
	public void forcedFillAndPrayerFill()
	{
		Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
		locks.put(Slot.HEAD, SlotLock.fill());
		SetupResult r = best(settings().locks(locks).fillMode(FillMode.PRAYER));
		assertEquals(prayerHelm, r.getLoadout().get(Slot.HEAD));
		assertTrue(r.getFilledSlots().contains(Slot.HEAD));
	}

	@Test
	public void maxHitModePrefersBiggestHits()
	{
		GearItem slowHeavy = weapon(500, "slow heavy", "2h sword", 9, "chop,slash,aggressive");
		slowHeavy.setMeleeStr(260);
		slowHeavy.setTwoHanded(true);
		items.add(slowHeavy);
		assertEquals(godsword, best(settings()).getLoadout().getWeapon());
		assertEquals(slowHeavy, best(settings().calcMode(CalcMode.MAX_HIT)).getLoadout().getWeapon());
	}

	@Test
	public void ammoCountIsPricedInBudget()
	{
		GearItem bow = weapon(600, "bow", "bow", 5, "rapid,ranged,rapid");
		bow.setRangedBonus(60);
		bow.setAmmunition(Arrays.asList(700, 701));
		GearItem goodArrow = item(700, "good arrow", Slot.AMMO);
		goodArrow.setRangedStr(60);
		goodArrow.setPrice(1_000);
		GearItem cheapArrow = item(701, "cheap arrow", Slot.AMMO);
		cheapArrow.setRangedStr(10);
		cheapArrow.setPrice(10);
		GameData data = TestData.gameData(Arrays.asList(bow, goodArrow, cheapArrow), Collections.emptyList());
		CombatContext ctx = new CombatContext(monster(100, 20), PlayerLevels.maxed(), false, true, piety());
		OptimizerSettings.OptimizerSettingsBuilder b = OptimizerSettings.builder().mode(SearchMode.BUDGET).budget(200_000)
			.spellbooks(Collections.singleton("standard"));

		SetupResult ignoreAmmo = new Optimizer(data, ctx, b.build(), id -> id == 600, GearItem::getPrice)
			.optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(goodArrow, ignoreAmmo.getLoadout().get(Slot.AMMO));

		// 1000 good arrows cost 1m, over the 200k budget.
		SetupResult priced = new Optimizer(data, ctx, b.ammoCount(1000).build(), id -> id == 600, GearItem::getPrice)
			.optimize(CombatClass.RANGED, () -> false).get(0);
		assertEquals(cheapArrow, priced.getLoadout().get(Slot.AMMO));
		assertEquals(10_000, priced.getBuyCost());
	}

	@Test
	public void thrallAddsFlatDps()
	{
		double without = run(settings(), monster(100, 20), false).get(0).getDps().getDps();
		double with = run(settings(), monster(100, 20), true).get(0).getDps().getDps();
		assertEquals(without + Thrall.GREATER.getDps(), with, 1e-9);
	}

	@Test
	public void atlatlHasItsOwnTab()
	{
		GearItem bow = weapon(600, "bow", "bow", 5, "rapid,ranged,rapid");
		bow.setRangedBonus(60);
		GearItem atlatl = weapon(29000, "eclipse atlatl", "bow", 4, "rapid,ranged,rapid");
		atlatl.setRangedBonus(87);
		atlatl.setMeleeStr(40);
		GameData data = TestData.gameData(Arrays.asList(bow, atlatl), Collections.emptyList());
		CombatContext ctx = new CombatContext(monster(100, 20), PlayerLevels.maxed(), false, true, piety());
		OptimizerSettings.OptimizerSettingsBuilder b = OptimizerSettings.builder().mode(SearchMode.UNLIMITED)
			.spellbooks(Collections.singleton("standard"));

		List<SetupResult> ranged = new Optimizer(data, ctx, b.styles(EnumSet.of(AttackStyle.Type.RANGED)).build(),
			id -> true, GearItem::getPrice).optimize(CombatClass.RANGED, () -> false);
		assertEquals(1, ranged.size());
		assertEquals(bow, ranged.get(0).getLoadout().getWeapon());
		assertEquals(AttackStyle.Type.RANGED, ranged.get(0).getAttackType());

		List<SetupResult> atl = new Optimizer(data, ctx, b.styles(EnumSet.of(AttackStyle.Type.ATLATL)).build(),
			id -> true, GearItem::getPrice).optimize(CombatClass.RANGED, () -> false);
		assertEquals(1, atl.size());
		assertEquals(atlatl, atl.get(0).getLoadout().getWeapon());
		assertEquals(AttackStyle.Type.ATLATL, atl.get(0).getAttackType());
	}
}
