package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/** A melee player's magic switch keeps the worn melee setup except for the weapon and one other piece. */
public class RaidSwitchTest
{
	private static final List<String> MELEE = Arrays.asList("abyssal whip", "avernic defender", "bandos chestplate",
		"bandos tassets", "neitiznot faceguard", "amulet of torture", "infernal cape", "ferocious gloves", "primordial boots");
	private static final List<String> MAGIC = Arrays.asList("trident of the swamp", "sanguinesti staff", "ancestral robe top",
		"ancestral robe bottom", "ancestral hat", "occult necklace", "tormented bracelet", "book of the dead", "eternal boots");
	private static final List<String> RANGED = Arrays.asList("toxic blowpipe", "dragon dart", "magic shortbow", "rune arrow",
		"black d'hide body", "black d'hide chaps", "ava's assembler", "necklace of anguish");
	private static GameData data;
	private static final Set<Integer> owned = new HashSet<>();

	@BeforeClass public static void load() throws Exception
	{
		Gson gson = new Gson();
		OwnershipRules.init(gson);
		data = new GameDataLoader(gson).loadGameData();
		for (String name : MELEE) { owned.add(named(name).getId()); }
		for (String name : MAGIC) { owned.add(named(name).getId()); }
		for (String name : RANGED) { owned.add(named(name).getId()); }
	}

	private static GearItem named(String name)
	{
		for (Slot slot : Slot.values())
		{
			for (GearItem item : data.getItems(slot))
			{
				if (item.getName().equals(name)) { return item; }
			}
		}
		throw new AssertionError("Missing " + name);
	}

	private static Loadout melee()
	{
		Loadout worn = new Loadout();
		for (String name : MELEE)
		{
			GearItem item = named(name);
			worn.set(item.getSlot(), item);
		}
		return worn;
	}

	private static List<SetupResult> magic(Loadout switchFrom)
	{
		return search(CombatClass.MAGIC, AttackStyle.Type.MAGIC, switchFrom);
	}

	private static List<SetupResult> search(CombatClass cls, AttackStyle.Type type, Loadout switchFrom)
	{
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY)
			.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient", "arceuus")))
			.styles(Collections.singleton(type)).switchFrom(switchFrom).build();
		CombatContext ctx = new CombatContext(TestData.monster(100, 0), PlayerLevels.maxed(), false, 99, 99, 99, 99,
			new EnumMap<>(CombatClass.class), false);
		return new Optimizer(data, ctx, settings, owned::contains, GearItem::getPrice).optimize(cls, () -> false);
	}

	@Test public void switchesCountChangedSlotsButNotAShieldRemovedByATwoHandedWeapon()
	{
		Loadout worn = melee();
		assertEquals(0, worn.switchesFrom(worn));
		Loadout staff = worn.copy();
		staff.set(Slot.WEAPON, named("tumeken's shadow"));
		assertTrue(staff.getWeapon().isTwoHanded());
		staff.set(Slot.SHIELD, null);
		assertEquals(1, staff.switchesFrom(worn));
		Loadout book = worn.copy();
		book.set(Slot.WEAPON, named("trident of the swamp"));
		book.set(Slot.SHIELD, named("book of the dead"));
		assertEquals(2, book.switchesFrom(worn));
		// Taking the shield off for a one-handed weapon is a change of its own.
		book.set(Slot.SHIELD, null);
		assertEquals(2, book.switchesFrom(worn));
	}

	@Test public void magicSwitchKeepsAllButTheWeaponAndOnePiece()
	{
		Loadout worn = melee();
		List<SetupResult> free = magic(null);
		assertFalse(free.isEmpty());
		assertTrue("A free search would bring a bigger switch", free.get(0).getLoadout().switchesFrom(worn) > 2);
		List<SetupResult> switches = magic(worn);
		assertFalse(switches.isEmpty());
		for (SetupResult setup : switches)
		{
			assertTrue(setup.getLoadout().switchesFrom(worn) <= 2);
			assertEquals(CombatClass.MAGIC, setup.getCombatClass());
			assertNull("Specs would be another switch", setup.getSpecial());
			assertTrue(setup.getFilledSlots().isEmpty());
			// Everything not switched stays worn.
			int kept = 0;
			for (Slot slot : Slot.values())
			{
				if (setup.getLoadout().get(slot) != null && setup.getLoadout().get(slot) == worn.get(slot)) { kept++; }
			}
			assertTrue(kept >= 7);
		}
		assertTrue(switches.get(0).getDps().getDps() < free.get(0).getDps().getDps());
		assertTrue(switches.stream().map(s -> s.getLoadout().getWeapon().getName()).anyMatch(n -> n.equals("sanguinesti staff")
			|| n.equals("trident of the swamp")));
	}

	@Test public void rangedSwitchCountsFiredAmmunitionButNotLoadedDarts()
	{
		Loadout worn = melee();
		assertTrue("A free search would bring a bigger switch",
			search(CombatClass.RANGED, AttackStyle.Type.RANGED, null).get(0).getLoadout().switchesFrom(worn) > 2);
		List<SetupResult> switches = search(CombatClass.RANGED, AttackStyle.Type.RANGED, worn);
		assertFalse(switches.isEmpty());
		boolean blowpipe = false;
		boolean bow = false;
		for (SetupResult setup : switches)
		{
			Loadout l = setup.getLoadout();
			assertTrue(l.switchesFrom(worn) <= 2);
			assertNull(setup.getSpecial());
			if (l.getWeapon().getName().equals("toxic blowpipe"))
			{
				blowpipe = true;
				assertEquals("dragon dart", l.getLoadedAmmo().getName());
			}
			if (l.getWeapon().getName().equals("magic shortbow"))
			{
				bow = true;
				// The arrows are the second change, so nothing else is swapped.
				assertEquals("rune arrow", l.get(Slot.AMMO).getName());
				assertEquals(2, l.switchesFrom(worn));
			}
		}
		assertTrue(blowpipe && bow);
	}

	@Test public void quickSearchRetainsAlternativesForEveryExtraSlotEvenWithOneResultPerPattern()
	{
		Loadout worn = melee();
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY)
			.styles(Collections.singleton(AttackStyle.Type.MAGIC)).spellbooks(Collections.emptySet())
			.switchFrom(worn).resultsPerClass(1).build();
		CombatContext ctx = new CombatContext(TestData.monster(100, 0), PlayerLevels.maxed(), false, 99, 99, 99, 99,
			new EnumMap<>(CombatClass.class), false);
		List<SetupResult> results = new Optimizer(data, ctx, settings, owned::contains, GearItem::getPrice).optimize(CombatClass.MAGIC, () -> false);
		Set<Slot> patterns = new HashSet<>();
		for (SetupResult result : results)
		{
			Set<Slot> changed = result.getLoadout().switchSlotsFrom(worn);
			changed.remove(Slot.WEAPON);
			assertTrue(changed.size() <= 1);
			assertTrue(patterns.add(changed.isEmpty() ? Slot.WEAPON : changed.iterator().next()));
		}
		assertTrue(patterns.containsAll(Arrays.asList(Slot.WEAPON, Slot.BODY, Slot.NECK)));
	}

	private static List<SetupResult> magic(Loadout switchFrom, int limit)
	{
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY)
			.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient", "arceuus")))
			.styles(Collections.singleton(AttackStyle.Type.MAGIC)).switchFrom(switchFrom).switchLimit(limit).build();
		CombatContext ctx = new CombatContext(TestData.monster(100, 0), PlayerLevels.maxed(), false, 99, 99, 99, 99,
			new EnumMap<>(CombatClass.class), false);
		return new Optimizer(data, ctx, settings, owned::contains, GearItem::getPrice).optimize(CombatClass.MAGIC, () -> false);
	}

	private static Set<Slot> extras(SetupResult setup, Loadout worn)
	{
		Set<Slot> changed = setup.getLoadout().switchSlotsFrom(worn);
		changed.remove(Slot.WEAPON);
		return changed;
	}

	@Test public void aLargerSwapLimitIsAMostNotATarget()
	{
		Loadout worn = melee();
		List<SetupResult> two = magic(worn, 2);
		List<SetupResult> five = magic(worn, 5);
		assertFalse(five.isEmpty());
		assertTrue("More swaps can only help", five.get(0).getDps().getDps() >= two.get(0).getDps().getDps() - 1e-9);
		assertTrue("Magic gear on a melee setup is worth more than one piece",
			five.stream().anyMatch(setup -> setup.getLoadout().switchesFrom(worn) > 2));
		for (SetupResult larger : five)
		{
			assertTrue(larger.getLoadout().switchesFrom(worn) <= 5);
			assertNull(larger.getSpecial());
			// No switch swaps a piece that doesn't beat a smaller switch of the same weapon it contains.
			for (SetupResult smaller : five)
			{
				if (smaller.getLoadout().getWeapon() != larger.getLoadout().getWeapon()) { continue; }
				Set<Slot> big = extras(larger, worn);
				Set<Slot> small = extras(smaller, worn);
				if (small.size() < big.size() && big.containsAll(small))
				{
					assertTrue(big + " over " + small, larger.getDps().getDps() > smaller.getDps().getDps());
				}
			}
		}
		// One swap is the weapon alone.
		for (SetupResult setup : magic(worn, 1)) { assertTrue(extras(setup, worn).isEmpty()); }
	}

	private static final int KILL_SHARE = 4;

	private static CombatContext raidContext()
	{
		Monster boss = TestData.monster(100, 0);
		boss.setHitpoints(2000);
		return new CombatContext(boss, PlayerLevels.maxed(), false, 99, 99, 99, 99, new EnumMap<>(CombatClass.class), false);
	}

	/** A magic raid search over one player's share of each kill, with this share of kills followed by a pause. */
	private static List<SetupResult> raidMagic(Loadout switchFrom, double finalBlows)
	{
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY)
			.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient", "arceuus")))
			.styles(Collections.singleton(AttackStyle.Type.MAGIC)).switchFrom(switchFrom).resultsPerClass(50)
			.raidKillShare(KILL_SHARE).finalBlows(finalBlows).build();
		return new Optimizer(data, raidContext(), settings, owned::contains, GearItem::getPrice).optimize(CombatClass.MAGIC,
			() -> false);
	}

	/** The paused fight's result for the best plain result's loadout, checked against the final-blow model. */
	private static void assertFinalBlows(Loadout switchFrom)
	{
		SetupResult plain = raidMagic(switchFrom, 0).get(0);
		SetupResult paused = raidMagic(switchFrom, 1).stream().filter(r -> r.getLoadout().equals(plain.getLoadout()))
			.findFirst().orElseThrow(() -> new AssertionError("Missing " + plain.getLoadout()));
		int hp = (int) Math.ceil(raidContext().getTargetHitpoints() / (double) KILL_SHARE);
		DpsResult expected = SpecialRotation.rotation(plain.getDps(), hp / plain.getDps().getPrimaryDps(), hp, 0, 0,
			Collections.emptyList(), 1).getResult();
		assertTrue(paused.getDps().getDps() > plain.getDps().getDps());
		assertEquals(expected.getDps(), paused.getDps().getDps(), 1e-9);
		assertEquals(expected.getPrimaryDps(), paused.getDps().getPrimaryDps(), 1e-9);
	}

	@Test public void aRaidSwitchGetsTheSameFinalBlowsAsAFullSetup()
	{
		// Full setups and quick switches are compared in one fight, so both skip the last attack's interval alike.
		assertFinalBlows(null);
		assertFinalBlows(melee());
	}

	/** A freeze duty's switches from the worn setup, aiming for each Ice Barrage to land {@code target} of the time. */
	private static List<SetupResult> freeze(Loadout worn, double target)
	{
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY)
			.spellbooks(Collections.singleton("ancient")).styles(Collections.singleton(AttackStyle.Type.MAGIC))
			.raidLoadout(RaidLoadout.ICE_BARRAGE).calcMode(CalcMode.ACCURACY).freezeAccuracy(target)
			.switchFrom(worn).switchLimit(Slot.values().length).build();
		CombatContext ctx = new CombatContext(TestData.monster(100, 0), PlayerLevels.maxed(), false, 99, 99, 99, 99,
			new EnumMap<>(CombatClass.class), false);
		return new Optimizer(data, ctx, settings, owned::contains, GearItem::getPrice).optimize(CombatClass.MAGIC, () -> false);
	}

	@Test public void aFreezeSwitchesTheFewestItemsThatLandOftenEnough()
	{
		Loadout worn = melee();
		// No switch lands every cast: the most accurate comes first.
		List<SetupResult> unreachable = freeze(worn, 0.99);
		assertFalse(unreachable.isEmpty());
		SetupResult mostAccurate = unreachable.get(0);
		assertFalse(FreezeDuty.reaches(mostAccurate, 0.99));
		for (SetupResult setup : unreachable)
		{
			assertEquals("ice barrage", setup.getLoadout().getSpell().getLowerName());
			assertTrue(setup.getDps().getAccuracy() <= mostAccurate.getDps().getAccuracy() + 1e-9);
		}
		assertTrue("Magic gear on a melee setup takes several pieces", FreezeDuty.items(mostAccurate) > 2);
		// Just below that, fewer items may reach it; a low target needs only the staff and at most one piece.
		double high = mostAccurate.getDps().getAccuracy() - 1e-6;
		SetupResult highest = freeze(worn, high).get(0);
		assertTrue(FreezeDuty.reaches(highest, high));
		SetupResult low = freeze(worn, 0.5).get(0);
		assertTrue(FreezeDuty.reaches(low, 0.5));
		assertTrue(FreezeDuty.items(low) <= 2);
		assertTrue(FreezeDuty.items(low) < FreezeDuty.items(highest));
		// No switch reaching a target swaps fewer items than the first one.
		for (double target : new double[]{0.5, high})
		{
			List<SetupResult> switches = freeze(worn, target);
			int fewest = FreezeDuty.items(switches.get(0));
			switches.stream().filter(setup -> FreezeDuty.reaches(setup, target))
				.forEach(setup -> assertTrue(FreezeDuty.items(setup) >= fewest));
		}
	}

	/** Guaranteed freezes of Maiden's nylocas from the worn setup, with Augury at this boosted Magic level. */
	private static List<SetupResult> maidenFreeze(Loadout worn, int magic)
	{
		return maidenFreeze(worn, magic, owned);
	}

	private static List<SetupResult> maidenFreeze(Loadout worn, int magic, Set<Integer> owned)
	{
		Monster nylocas = TestData.monster(100, 0);
		nylocas.setName("nylocas matomenos (normal mode maiden)");
		nylocas.setHitpoints(100);
		EnumMap<CombatClass, com.bestgearsetup.data.OffensivePrayer> prayers = new EnumMap<>(CombatClass.class);
		data.getPrayers(CombatClass.MAGIC).stream().filter(p -> p.getName().equalsIgnoreCase("augury")).findFirst()
			.ifPresent(augury -> prayers.put(CombatClass.MAGIC, augury));
		assertFalse(prayers.isEmpty());
		OptimizerSettings settings = OptimizerSettings.builder().mode(SearchMode.OWNED_ONLY)
			.spellbooks(Collections.singleton("ancient")).styles(Collections.singleton(AttackStyle.Type.MAGIC))
			.raidLoadout(RaidLoadout.ICE_BARRAGE).calcMode(CalcMode.ACCURACY).freezeAccuracy(FreezeDuty.GUARANTEED)
			.switchFrom(worn).switchLimit(Slot.values().length).build();
		CombatContext ctx = new CombatContext(nylocas, PlayerLevels.maxed(), false, 99, 99, 99, magic, prayers, false);
		return new Optimizer(data, ctx, settings, owned::contains, GearItem::getPrice).optimize(CombatClass.MAGIC, () -> false);
	}

	@Test public void aHeartGuaranteesTheMaidenFreezeWithNoMoreItems()
	{
		Loadout worn = melee();
		// A saturated heart raises Magic to 112, but the threshold stays at (99 + 9) x 204.
		SetupResult hearted = maidenFreeze(worn, 112).get(0);
		SetupResult plain = maidenFreeze(worn, 99).get(0);
		assertTrue(FreezeDuty.reaches(hearted, FreezeDuty.GUARANTEED));
		assertTrue(FreezeDuty.items(hearted) <= FreezeDuty.items(plain));
		assertTrue("Melee gear alone can't guarantee it", FreezeDuty.items(hearted) > 1);
	}

	@Test public void freezingNeverAssumesTakingOffWornArmourToImproveAccuracy()
	{
		Loadout worn = melee();
		Set<Integer> staffOnly = new HashSet<>();
		for (String name : MELEE) { staffOnly.add(named(name).getId()); }
		staffOnly.add(named("sanguinesti staff").getId());
		List<SetupResult> switches = maidenFreeze(worn, 112, staffOnly);
		assertFalse(switches.isEmpty());
		for (SetupResult setup : switches)
		{
			for (Slot slot : Slot.values())
			{
				if (worn.get(slot) != null)
				{ assertTrue("Freeze must keep or replace worn " + slot, setup.getLoadout().get(slot) != null); }
			}
		}
	}

	@Test public void anOwnedIceAncientSceptreFreezesWithFewerItems()
	{
		Loadout worn = melee();
		Set<Integer> withSceptre = new HashSet<>(owned);
		withSceptre.add(named("ice ancient sceptre").getId());
		SetupResult sceptre = maidenFreeze(worn, 99, withSceptre).get(0);
		SetupResult without = maidenFreeze(worn, 99).get(0);
		// Its 10% accuracy on targets that aren't frozen counts toward the guarantee (Wiki: +88 with Augury at 99).
		assertEquals("ice ancient sceptre", sceptre.getLoadout().getWeapon().getName());
		assertFalse("The sceptre autocasts Ice Barrage", FreezeDuty.manualCast(sceptre));
		assertTrue(FreezeDuty.reaches(sceptre, FreezeDuty.GUARANTEED));
		assertTrue(FreezeDuty.items(sceptre) <= FreezeDuty.items(without));
	}

	@Test public void aFreezeAutocastsWheneverAnAutocastingWeaponGuaranteesIt()
	{
		Loadout worn = melee();
		Set<Integer> withStaff = new HashSet<>(owned);
		withStaff.add(named("ancient staff").getId());
		// The sanguinesti staff has more magic attack but can only cast Ice Barrage by hand.
		assertTrue(FreezeDuty.manualCast(maidenFreeze(worn, 112).get(0)));
		SetupResult staff = maidenFreeze(worn, 112, withStaff).get(0);
		assertTrue(FreezeDuty.reaches(staff, FreezeDuty.GUARANTEED));
		assertFalse(FreezeDuty.manualCast(staff));
		assertEquals("ancient staff", staff.getLoadout().getWeapon().getName());
	}

	@Test public void aSwitchSwapsNothingWhenNoPieceHelps()
	{
		// Already in magic gear: a magic switch only changes the weapon, however many swaps are allowed.
		Loadout worn = new Loadout();
		for (String name : MAGIC.subList(2, MAGIC.size()))
		{
			GearItem item = named(name);
			worn.set(item.getSlot(), item);
		}
		worn.set(Slot.WEAPON, named("abyssal whip"));
		worn.set(Slot.CAPE, named("infernal cape"));
		SetupResult best = magic(worn, 6).get(0);
		assertTrue(best.getLoadout().switchSlotsFrom(worn).toString(), extras(best, worn).isEmpty());
	}
}
