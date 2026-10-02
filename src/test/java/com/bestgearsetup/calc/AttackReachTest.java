package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldArea;
import org.junit.Test;

public class AttackReachTest
{
	private final GearItem sword = weapon(4151, "abyssal whip", "whip", 4, "lash,slash,aggressive");
	private final GearItem halberd = weapon(29796, "noxious halberd", "polearm", 5, "swipe,slash,aggressive");
	private final GearItem blowpipe = weapon(12926, "toxic blowpipe", "thrown", 3,
		"accurate,ranged,accurate", "rapid,ranged,rapid", "longrange,ranged,longrange");

	public AttackReachTest()
	{
		sword.setSlashBonus(200);
		sword.setMeleeStr(200);
		halberd.setSlashBonus(100);
		halberd.setMeleeStr(100);
		blowpipe.setRangedBonus(50);
		blowpipe.setRangedStr(50);
	}

	private List<SetupResult> run(Monster target, CombatClass cls,
		OptimizerSettings.OptimizerSettingsBuilder builder, GearItem... weapons)
	{
		CombatContext ctx = new CombatContext(target, PlayerLevels.maxed(), false, true, piety());
		OptimizerSettings settings = builder.mode(SearchMode.UNLIMITED)
			.spellbooks(Collections.singleton("standard")).resultsPerClass(3).build();
		return new Optimizer(TestData.gameData(Arrays.asList(weapons), Collections.emptyList()), ctx,
			settings, id -> true, GearItem::getPrice).optimize(cls, () -> false);
	}

	@Test
	public void everyZulrahPhaseRequiresHalberdEvenWhenCloserDistanceIsRequested()
	{
		for (int id = 2042; id <= 2044; id++)
		{
			Monster zulrah = monster(100, 20);
			zulrah.setId(id);
			zulrah.setName("zulrah");
			List<SetupResult> results = run(zulrah, CombatClass.MELEE,
				OptimizerSettings.builder().targetDistance(1), sword, halberd);
			assertEquals(1, results.size());
			assertEquals(halberd, results.get(0).getLoadout().getWeapon());
			assertEquals(2, AttackReach.distance(zulrah, 0));
		}
	}

	@Test
	public void lockedWhipCannotBypassZulrahReachAndFartherDistanceExcludesHalberds()
	{
		Monster zulrah = monster(100, 20);
		zulrah.setName("zulrah (magma)");
		assertTrue(run(zulrah, CombatClass.MELEE, OptimizerSettings.builder()
			.locks(Collections.singletonMap(Slot.WEAPON, SlotLock.item(sword.getId()))), sword, halberd).isEmpty());
		assertTrue(run(zulrah, CombatClass.MELEE, OptimizerSettings.builder().targetDistance(3), sword, halberd).isEmpty());
	}

	@Test
	public void olmHeadAllowsOnlyHalberdMeleeWithoutApplyingItsDistanceToEitherHand()
	{
		Monster olm = monster(100, 20);
		olm.setName("great olm (head)");
		for (int id : new int[]{7551, 7554})
		{
			olm.setId(id);
			List<SetupResult> results = run(olm, CombatClass.MELEE,
				OptimizerSettings.builder().targetDistance(1), sword, halberd);
			assertEquals(1, results.size());
			assertEquals(halberd, results.get(0).getLoadout().getWeapon());
			assertFalse(run(olm, CombatClass.RANGED, OptimizerSettings.builder(), blowpipe).isEmpty());
			assertTrue(run(olm, CombatClass.MELEE, OptimizerSettings.builder()
				.locks(Collections.singletonMap(Slot.WEAPON, SlotLock.item(sword.getId()))), sword, halberd).isEmpty());
			assertTrue(run(olm, CombatClass.MELEE, OptimizerSettings.builder().targetDistance(3), halberd).isEmpty());
		}
		for (int id : new int[]{7550, 7552})
		{
			olm.setId(id);
			olm.setName("great olm (" + (id == 7550 ? "mage" : "melee") + " hand)");
			assertFalse(run(olm, CombatClass.MELEE, OptimizerSettings.builder(), sword).isEmpty());
		}
	}

	@Test
	public void distantBlowpipeUsesLongrangeAndStillObeysDefenceExperienceFilter()
	{
		Monster target = monster(100, 20);
		List<SetupResult> atFive = run(target, CombatClass.RANGED,
			OptimizerSettings.builder().targetDistance(5), blowpipe);
		assertEquals("rapid", atFive.get(0).getLoadout().getStyle().getStance());
		List<SetupResult> atSix = run(target, CombatClass.RANGED,
			OptimizerSettings.builder().targetDistance(6), blowpipe);
		assertEquals("longrange", atSix.get(0).getLoadout().getStyle().getStance());
		assertTrue(run(target, CombatClass.RANGED, OptimizerSettings.builder()
			.targetDistance(6).defenceXp(false), blowpipe).isEmpty());
		assertTrue(run(target, CombatClass.RANGED, OptimizerSettings.builder().targetDistance(8), blowpipe).isEmpty());
	}

	@Test
	public void meleeOnAStaffDoesNotInheritItsSpellRange()
	{
		GearItem staff = weapon(1409, "iban's staff", "staff", 5);
		assertEquals(1, AttackReach.range(staff, AttackStyle.parse("bash,crush,aggressive")));
		assertEquals(10, AttackReach.range(staff, AttackStyle.parse("spell,magic,magic")));
	}

	@Test
	public void poweredStaffAndRangedRangesRespectWeaponExceptionsAndCap()
	{
		AttackStyle magic = AttackStyle.parse("accurate,magic,accurate");
		AttackStyle longMagic = AttackStyle.parse("longrange,magic,longrange");
		assertEquals(7, AttackReach.range(weapon(11907, "trident of the seas", "powered staff", 4), magic));
		assertEquals(9, AttackReach.range(weapon(11907, "trident of the seas", "powered staff", 4), longMagic));
		assertEquals(8, AttackReach.range(weapon(31115, "eye of ayak", "powered staff", 3), longMagic));
		assertEquals(10, AttackReach.range(weapon(27275, "tumeken's shadow", "powered staff", 5), longMagic));
		AttackStyle rapid = AttackStyle.parse("rapid,ranged,rapid");
		AttackStyle longRanged = AttackStyle.parse("longrange,ranged,longrange");
		assertEquals(7, AttackReach.range(weapon(861, "magic shortbow (i)", "bow", 4), rapid));
		assertEquals(7, AttackReach.range(weapon(21012, "dragon hunter crossbow", "crossbow", 6), rapid));
		assertEquals(10, AttackReach.range(weapon(26374, "zaryte crossbow", "crossbow", 6), longRanged));
		assertEquals(10, AttackReach.range(weapon(25867, "bow of faerdhinen (c)", "bow", 5), longRanged));
		assertEquals(3, AttackReach.range(weapon(11230, "dragon dart", "thrown", 3), rapid));
		assertEquals(5, AttackReach.range(weapon(31583, "rosewood blowpipe", "thrown", 3), rapid));
		assertEquals(9, AttackReach.range(weapon(28922, "tonalztics of ralos", "thrown", 7), longRanged));
	}

	@Test
	public void unknownRangedWeaponIsNotAssumedToReachDistantTargets()
	{
		GearItem unknown = weapon(99901, "test bow (beta)", "bow", 4, "rapid,ranged,rapid");
		unknown.setRangedBonus(80);
		unknown.setRangedStr(80);
		assertFalse(run(monster(100, 20), CombatClass.RANGED,
			OptimizerSettings.builder().betaItems(true), unknown).isEmpty());
		assertTrue(run(monster(100, 20), CombatClass.RANGED,
			OptimizerSettings.builder().betaItems(true).targetDistance(2), unknown).isEmpty());
	}

	@Test
	public void flyingRestrictionStillAllowsOnlyPolearmsForMelee()
	{
		List<SetupResult> results = run(monster(100, 20, "flying"), CombatClass.MELEE,
			OptimizerSettings.builder(), sword, halberd);
		assertEquals(1, results.size());
		assertEquals(halberd, results.get(0).getLoadout().getWeapon());
	}

	@Test
	public void knownFlyingTargetsRejectOrdinaryMeleeEvenWithoutAttributeTags()
	{
		for (int id : new int[]{3162, 3163, 3164, 3165, 3169, 3183, 7037, 12443, 6492, 15697,
			7850, 7852, 7853, 7884, 7885, 15623, 15635})
		{
			Monster target = monster(100, 20);
			target.setId(id);
			List<SetupResult> results = run(target, CombatClass.MELEE, OptimizerSettings.builder(), sword, halberd);
			boolean aviansie = id >= 3169 && id <= 3183 || id == 7037 || id == 12443 || id == 6492 || id == 15697;
			assertEquals("NPC " + id, aviansie ? 0 : 1, results.size());
			if (!aviansie)
			{
				assertEquals(halberd, results.get(0).getLoadout().getWeapon());
			}
			assertTrue(run(target, CombatClass.MELEE, OptimizerSettings.builder()
				.locks(Collections.singletonMap(Slot.WEAPON, SlotLock.item(sword.getId()))), sword).isEmpty());
			assertFalse(run(target, CombatClass.RANGED, OptimizerSettings.builder(), blowpipe).isEmpty());
		}
	}

	@Test
	public void flyingNameFallbacksRecognizeVariantsWithoutRestrictingDusk()
	{
		for (String name : new String[]{"Kree'arra (Deadman) (Apocalypse)", "Wingman Skree",
			"Flockleader Geerin", "Flight Kilisa", "Aviansie", "Reanimated aviansie", "Dawn (Echo)"})
		{
			Monster target = monster(100, 20);
			target.setName(name);
			assertFalse(name, AttackReach.canReach(target, sword, AttackStyle.parse("lash,slash,aggressive"), 0));
			assertEquals(name, !name.toLowerCase(java.util.Locale.ROOT).contains("aviansie"),
				AttackReach.canReach(target, halberd, AttackStyle.parse("swipe,slash,aggressive"), 0));
		}
		Monster dusk = monster(100, 20, "melee only");
		dusk.setId(7854);
		dusk.setName("Dusk (Echo)");
		assertFalse(run(dusk, CombatClass.MELEE, OptimizerSettings.builder(), sword).isEmpty());
	}

	@Test
	public void zukExcludesEveryMeleeWeaponAndAutoRequiresShieldCornerRange()
	{
		GearItem crossbow = weapon(11785, "armadyl crossbow", "crossbow", 6,
			"rapid,ranged,rapid", "longrange,ranged,longrange");
		crossbow.setRangedBonus(100);
		crossbow.setRangedStr(100);
		GearItem shadow = weapon(27275, "tumeken's shadow", "powered staff", 5, "accurate,magic,accurate");
		shadow.setMagicBonus(100);
		for (int id : new int[]{1, 7706})
		{
			Monster zuk = monster(100, 20);
			zuk.setId(id);
			if (id == 1)
			{
				zuk.setName("TzKal-Zuk (enraged)");
			}
			assertTrue(run(zuk, CombatClass.MELEE, OptimizerSettings.builder().targetDistance(1), sword, halberd).isEmpty());
			assertTrue(run(zuk, CombatClass.MELEE, OptimizerSettings.builder()
				.locks(Collections.singletonMap(Slot.WEAPON, SlotLock.item(halberd.getId()))), halberd).isEmpty());
			assertTrue(run(zuk, CombatClass.RANGED, OptimizerSettings.builder(), blowpipe).isEmpty());
			List<SetupResult> results = run(zuk, CombatClass.RANGED, OptimizerSettings.builder(), crossbow);
			assertEquals(1, results.size());
			assertEquals("longrange", results.get(0).getLoadout().getStyle().getStance());
			assertFalse(run(zuk, CombatClass.MAGIC, OptimizerSettings.builder(), shadow).isEmpty());
			assertEquals(9, AttackReach.distance(zuk, 0));
			assertEquals(6, AttackReach.distance(zuk, 1));
			assertEquals("longrange", run(zuk, CombatClass.RANGED, OptimizerSettings.builder()
				.targetDistance(6), blowpipe).get(0).getLoadout().getStyle().getStance());
			assertTrue(run(zuk, CombatClass.RANGED, OptimizerSettings.builder().defenceXp(false), crossbow).isEmpty());
		}
		Monster zuk = monster(100, 20, "melee only");
		zuk.setName("TzKal-Zuk (enraged)");
		assertFalse(Optimizer.classAllowed(zuk, CombatClass.MELEE));
		Monster jadHealer = monster(100, 20);
		jadHealer.setId(7705);
		jadHealer.setName("Yt-HurKot");
		assertFalse(AttackReach.isZuk(jadHealer));
		assertFalse(run(jadHealer, CombatClass.MELEE, OptimizerSettings.builder(), sword).isEmpty());
	}

	@Test
	public void krakenTargetsExcludeHalberdsButSailingKrakensRemainSeparate()
	{
		for (int id : new int[]{492, 494, 5535})
		{
			Monster target = monster(100, 20);
			target.setId(id);
			assertTrue(run(target, CombatClass.MELEE, OptimizerSettings.builder(), sword, halberd).isEmpty());
			assertFalse(run(target, CombatClass.RANGED, OptimizerSettings.builder(), blowpipe).isEmpty());
		}
		for (String name : new String[]{"Kraken (Kraken)", "Cave kraken", "Enormous tentacle"})
		{
			Monster target = monster(100, 20);
			target.setName(name);
			assertTrue(name, run(target, CombatClass.MELEE, OptimizerSettings.builder(), halberd).isEmpty());
		}
		Monster sailing = monster(100, 20);
		sailing.setId(15210);
		sailing.setName("Armoured kraken");
		assertFalse(AttackReach.isKraken(sailing));
		assertFalse(run(sailing, CombatClass.MELEE, OptimizerSettings.builder(), sword).isEmpty());
	}

	@Test
	public void zebakAllowsAdjacentMeleeAndOnlyHalberdsAtTwoTiles()
	{
		Monster zebak = monster(100, 20);
		zebak.setId(11730);
		zebak.setName("Zebak");
		assertEquals(sword, run(zebak, CombatClass.MELEE, OptimizerSettings.builder(), sword, halberd)
			.get(0).getLoadout().getWeapon());
		assertEquals(halberd, run(zebak, CombatClass.MELEE, OptimizerSettings.builder().targetDistance(2), sword, halberd)
			.get(0).getLoadout().getWeapon());
		assertTrue(run(zebak, CombatClass.MELEE, OptimizerSettings.builder().targetDistance(3), sword, halberd).isEmpty());
	}

	@Test
	public void liveDistanceMeasuresNearestNpcTileIncludingDiagonalAndDifferentPlanes()
	{
		WorldArea target = new WorldArea(100, 100, 5, 5, 0);
		assertEquals(1, new WorldArea(105, 102, 1, 1, 0).distanceTo(target));
		assertEquals(2, new WorldArea(106, 106, 1, 1, 0).distanceTo(target));
		assertEquals(Integer.MAX_VALUE, new WorldArea(105, 102, 1, 1, 1).distanceTo(target));
	}
}
