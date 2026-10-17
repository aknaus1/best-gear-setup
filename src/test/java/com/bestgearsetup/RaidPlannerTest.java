package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SpecialPlan;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.SupportSpell;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.EnumSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class RaidPlannerTest
{
	@Test public void beamPackingReportsComparisonsWithinAnEncounter()
	{
		List<RaidEncounter> fights = Arrays.asList(new RaidEncounter("Start", new MonsterSummary()),
			new RaidEncounter("Boss", new MonsterSummary()));
		SetupResult fastest = setup(item(9100, Slot.WEAPON), null, 10);
		SetupResult alternative = setup(item(9101, Slot.WEAPON), null, 9);
		List<Double> progress = new ArrayList<>();
		RaidPlan plan = RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ANCIENT, fights,
			Arrays.asList(result(fastest, alternative), result(fastest, alternative)), 10, Long.MAX_VALUE,
			id -> true, item -> 0, 0, id -> 100, () -> false, progress::add);
		assertNotNull(plan);
		assertTrue("The first encounter's beam must report before its boundary", progress.stream().anyMatch(value -> value > 10 && value < 55));
		for (int index = 1; index < progress.size(); index++) { assertTrue(progress.get(index) >= progress.get(index - 1)); }
		assertEquals(100, progress.get(progress.size() - 1), 0);
	}

	@Test public void everyRaidTargetExistsAndStartsAtTheRequestedBoss() throws Exception
	{
		GameDataLoader loader = new GameDataLoader(new Gson());
		GameData data = loader.loadGameData();
		assertEquals("tekton (normal)", RaidPlanType.COX_CM.encounters(data).get(0).getTarget().getName());
		// ToA starts on the first path's puzzle: the Apmeken baboons before Ba-Ba.
		assertEquals("baboon brawler (level 56)", RaidPlanType.TOA.encounters(data).get(0).getTarget().getName());
		assertEquals("the maiden of sugadinti (normal)", RaidPlanType.TOB.encounters(data).get(0).getTarget().getName());
		for (RaidPlanType type : RaidPlanType.values())
		{
			for (RaidEncounter encounter : type.encounters(data))
			{
				assertTrue(loader.getMonster(encounter.getTarget()).getHitpoints() > 0);
				assertTrue(!encounter.getTarget().getName().contains("deadman"));
			}
		}
		assertTrue(RaidPlanType.TOA.encounters(data).stream().anyMatch(e -> e.getPhase() == EncounterPhase.AKKHA_IMMUNE_MAGIC));
		assertEquals(4, RaidPlanType.TOA.encounters(data).stream().filter(e -> e.getBossName().equals("Akkha")).count());
		assertTrue(RaidPlanType.COX_CM.encounters(data).stream().anyMatch(e -> e.getTarget().getName().equals("great olm (head)")));
		assertTrue(RaidPlanType.TOB.encounters(data).stream().anyMatch(e -> e.getTarget().getName().equals("verzik vitur (phase 3)")));
	}

	@Test public void scavengersAndVespulaAreShownButNeverWeighOnTheKit() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		List<String> displayOnly = new ArrayList<>();
		for (RaidPlanType type : RaidPlanType.values())
		{
			List<RaidEncounter> encounters = type.encounters(data);
			if (encounters.isEmpty()) { continue; }
			// The first encounter is the starting worn setup, so it always shapes the kit.
			assertTrue(!encounters.get(0).isDisplayOnly());
			encounters.stream().filter(RaidEncounter::isDisplayOnly).forEach(e -> displayOnly.add(e.getLabel()));
		}
		assertEquals(Arrays.asList("Scavengers", "Vespula"), displayOnly);
		RaidEncounter vespula = RaidPlanType.COX_CM.encounters(data).stream().filter(e -> e.getLabel().equals("Vespula")).findFirst().get();
		assertTrue(vespula.withStyleChoice(RaidStyle.RANGED).withRaidSupplies(false).isDisplayOnly());
		// The portal is what players attack, so it still shapes the kit.
		assertTrue(RaidPlanType.COX_CM.encounters(data).stream().anyMatch(e -> e.getLabel().equals("Vespula Portal") && !e.isDisplayOnly()));
		SetupResult setup = setup(item(1, Slot.WEAPON), null, 10);
		assertEquals(0, vespula.estimatedSeconds(result(setup), setup), 0);
		assertEquals(10, new RaidEncounter("Boss", new MonsterSummary()).estimatedSeconds(result(setup), setup), 1e-9);
	}

	@Test public void repeatedTargetsFollowPartySizeWhileSingleBossesStaySingle() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		for (int party : new int[]{1, 3, 5, 7, 8, 15, 16})
		{
			RaidScaling.Settings scaling = RaidScaling.Settings.builder().partySize(party).coxChallengeMode(true).build();
			for (RaidPlanType type : new RaidPlanType[]{RaidPlanType.COX_CM, RaidPlanType.TOA, RaidPlanType.TOB})
			{
				// Two nylocas per player per Maiden spawn, at most 10; all but three are clumped.
				int loose = 3 * Math.min(3, Math.min(10, 2 * Math.min(party, 5)));
				for (RaidEncounter encounter : type.encounters(data, scaling))
				{
					String name = encounter.getTarget().getName();
					double expected = name.startsWith("great olm") && name.contains("hand") ? 1
						: name.startsWith("guardian") ? 2
						: name.startsWith("skeletal mystic") ? Math.min(12, 3 + party / 3)
						: name.startsWith("lizardman shaman") ? Math.min(5, 2 + party / 5)
						: name.startsWith("deathly") ? 2
						: name.startsWith("baboon brawler") ? party > 1 ? 8 : 5
						: name.startsWith("baboon thrower") ? party > 1 ? 7 : 5
						: name.startsWith("baboon mage") ? party > 1 ? 6 : 4
						: name.equals("baboon shaman") ? 5
						: name.equals("akkha's shadow") ? 4
						: encounter.getLabel().equals("Maiden Nylocas") ? loose
						: name.matches("nylocas (ischyros|toxobolos|hagios) \\(small\\)") ? 164
						: name.matches("nylocas (ischyros|toxobolos|hagios) \\(big\\)") ? 44
						: name.contains("warden") && (name.endsWith("(phase 2)") || name.endsWith("(core-ejected)")) ? 0.5
						: name.endsWith("warden (phase 3)") ? 0.6
						: 1;
					assertEquals(encounter.getLabel() + " with " + party, expected, encounter.getRepetitions(), 1e-12);
				}
			}
		}
	}

	@Test public void moreOverlordsDoublesTheSoldierAndSpittingScarabs() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		for (boolean more : new boolean[]{false, true})
		{
			List<RaidEncounter> encounters = RaidPlanType.TOA.encounters(data, RaidScaling.Settings.SOLO,
				RaidOptions.builder().toaMoreOverlords(more).build());
			for (String scarab : new String[]{"soldier scarab", "spitting scarab", "arcane scarab"})
			{
				RaidEncounter encounter = encounters.stream().filter(e -> e.getTarget().getName().equals(scarab)).findFirst().get();
				assertEquals(scarab, more && !scarab.startsWith("arcane") ? 2 : 1, encounter.getRepetitions(), 0);
			}
		}
	}

	@Test public void theChinnedClumpIsOneKillOfThePartysTimePerSpawn() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		for (int party = 1; party <= 5; party++)
		{
			List<RaidEncounter> encounters = RaidPlanType.TOB.encounters(data, RaidScaling.Settings.builder().partySize(party).build(),
				RaidOptions.builder().tobRole(RaidOptions.TobRole.RANGED_DPS).build());
			RaidEncounter chins = encounters.stream().filter(e -> e.getLabel().equals("Maiden Nylocas: Chins")).findFirst().get();
			assertEquals(3 * party, chins.getRepetitions(), 0);
		}
		// Seven of a five-player spawn's ten are clumped, five of four players' eight and three of three players' six.
		assertEquals(10, RaidScaling.maidenNylocasPerSpawn(RaidScaling.Settings.builder().partySize(5).build()));
		int[] clumps = {0, 1, 3, 5, 7};
		for (int party = 1; party <= 5; party++)
		{ assertEquals(clumps[party - 1], RaidScaling.maidenClump(RaidScaling.Settings.builder().partySize(party).build())); }
	}

	@Test public void vasaCrystalsFollowTheDamageDealtWhileHeWalks() throws Exception
	{
		// The first walk to a crystal is 6 seconds; each later window (back to the centre, out again) 8.4.
		double first = RaidPlanType.VASA_TO_CRYSTAL_TICKS * 0.6;
		double later = (RaidPlanType.VASA_TO_CENTRE_TICKS + RaidPlanType.VASA_TO_CRYSTAL_TICKS) * 0.6;
		assertEquals(6, first, 1e-9);
		assertEquals(8.4, later, 1e-9);
		// Killed during the first walk: no crystal. Each further window follows a crystal.
		assertEquals(0, RaidPlanType.vasaCrystals(1000, 1000.1 / first));
		assertEquals(1, RaidPlanType.vasaCrystals(1000, 999 / first));
		assertEquals(1, RaidPlanType.vasaCrystals(1000, 1000.1 / (first + later)));
		assertEquals(2, RaidPlanType.vasaCrystals(1000, 999 / (first + later)));
		assertEquals(3, RaidPlanType.vasaCrystals(1000, 1000.1 / (first + 3 * later)));
		assertEquals(50, RaidPlanType.vasaCrystals(1000, 0));
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		List<RaidEncounter> encounters = RaidPlanType.COX_CM.encounters(data);
		List<SearchResults> results = new ArrayList<>();
		for (RaidEncounter encounter : encounters)
		{
			boolean vasa = encounter.getTarget().getName().startsWith("vasa");
			SetupResult setup = setup(item(1, Slot.WEAPON), null, vasa ? 1000.1 / (first + 2 * later) / 2 : 10);
			Monster monster = new Monster();
			monster.setName(encounter.getTarget().getName());
			results.add(new SearchResults(monster, vasa ? 1000 : 100, Collections.singletonMap(AttackStyle.Type.CRUSH,
				Collections.singletonList(setup)), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false));
		}
		// A party of two takes three windows, so Vasa reaches two crystals.
		List<RaidEncounter> cycled = RaidPlanType.withCycles(encounters, results, 2);
		RaidEncounter crystal = cycled.stream().filter(e -> e.getTarget().getName().startsWith("glowing crystal")).findFirst().get();
		assertEquals(2, crystal.getRepetitions(), 0);
	}

	/** A setup specing on its own weapon: this DPS on a full bar, {@code regenerated} on regenerated energy alone. */
	private static SetupResult barSetup(GearItem weapon, double dps, double regenerated)
	{
		SetupResult base = setup(weapon, null, dps);
		DpsResult regen = new DpsResult(regenerated, 0, 1, 4, regenerated * 2.4, null);
		SpecialPlan special = new SpecialPlan(weapon, null, null, base.getLoadout().getStyle(), null, null, null,
			base.getDps(), regen, null, 2, 0, 0, regen, 1);
		return new SetupResult(CombatClass.MELEE, base.getLoadout(), base.getDps(), 0, 0, Collections.emptySet(),
			Collections.emptySet(), special);
	}

	private static SearchResults hitpoints(int hitpoints, SetupResult setup)
	{
		Monster monster = new Monster();
		monster.setName("test boss");
		return new SearchResults(monster, hitpoints, Collections.singletonMap(AttackStyle.Type.CRUSH,
			Collections.singletonList(setup)), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false)
			.withRegeneratedChoices();
	}

	/** The fights' setups, in order, that open on a full bar. */
	private static List<Boolean> fullBars(List<RaidEncounter> encounters, List<SearchResults> results)
	{
		RaidPlan plan = RaidPlanner.select(RaidPlanType.COX_CM, RaidSpellbook.STANDARD, encounters, results, 0,
			Long.MAX_VALUE, id -> false, gear -> 0, 0, id -> 0, () -> false);
		assertNotNull(plan);
		List<Boolean> out = new ArrayList<>();
		plan.getSetups().forEach(setup -> out.add(setup.usesFullBar()));
		return out;
	}

	@Test public void specEnergyCarriesFromFightToFightAndRegeneratesBetweenThem()
	{
		GearItem weapon = item(801, Slot.WEAPON);
		// Each full bar doubles the DPS, so it saves the most on the longest fight.
		SetupResult setup = barSetup(weapon, 10, 5);
		List<SearchResults> results = Arrays.asList(hitpoints(200, setup), hitpoints(1000, setup), hitpoints(600, setup));
		RaidEncounter first = new RaidEncounter("First", new MonsterSummary());
		RaidEncounter second = new RaidEncounter("Second", new MonsterSummary());
		RaidEncounter third = new RaidEncounter("Third", new MonsterSummary());
		// The raid starts on one bar, which the longest fight takes.
		assertEquals(Arrays.asList(false, true, false), fullBars(Arrays.asList(first, second, third), results));
		// Five minutes before the third fight regenerate another bar.
		assertEquals(Arrays.asList(false, true, true),
			fullBars(Arrays.asList(first, second, third.withEnergy(300, false, 1)), results));
		// A refilled bar as the second fight starts leaves the first one free to spend the raid's opening bar.
		assertEquals(Arrays.asList(true, true, false),
			fullBars(Arrays.asList(first, second.withEnergy(0, true, 1), third), results));
	}

	@Test public void aFightWithoutSpecsLetsTheBarRegenerate()
	{
		SetupResult setup = barSetup(item(804, Slot.WEAPON), 10, 5);
		// 300 HP at 1 DPS: five minutes without specs, regenerating a whole bar.
		SetupResult plain = setup(item(804, Slot.WEAPON), null, 1);
		RaidEncounter fight = new RaidEncounter("Fight", new MonsterSummary());
		assertEquals(100, fight.energyAfter(0, hitpoints(300, plain), plain), 1e-9);
		assertEquals(0, fight.energyAfter(100, hitpoints(300, setup), setup), 1e-9);
		// Speccing on regenerated energy spends what regenerates, keeping what was left.
		assertEquals(40, fight.energyAfter(40, hitpoints(300, setup), setup.withRegeneratedEnergy()), 1e-9);
		assertTrue(Double.isNaN(fight.energyAfter(90, hitpoints(300, setup), setup)));
		// Each player's share of a party fight is the party's time, so the bar regenerates over that.
		assertEquals(50, fight.withEnergy(0, false, 2).energyAfter(0, hitpoints(300, plain), plain), 1e-9);
		assertEquals(100, fight.withEnergy(0, true, 1).energyAfter(0, hitpoints(300, setup), setup.withRegeneratedEnergy()), 1e-9);
		List<SearchResults> results = Arrays.asList(hitpoints(1000, setup), hitpoints(300, plain), hitpoints(1000, setup));
		assertEquals(Arrays.asList(true, false, true), fullBars(Arrays.asList(new RaidEncounter("A", new MonsterSummary()),
			new RaidEncounter("B", new MonsterSummary()), new RaidEncounter("C", new MonsterSummary())), results));
	}

	@Test public void concreteEntryEnergyGatesEachHammerCountAndKeepsUnusedRegeneration()
	{
		SetupResult one = hammerOpening(1);
		SetupResult two = hammerOpening(2);
		RaidEncounter hand = new RaidEncounter("Olm hand phase", new MonsterSummary());
		for (double entry : new double[]{0, 40})
		{
			assertTrue(Double.isNaN(hand.energyAfter(entry, hitpoints(300, one), one)));
			assertTrue(Double.isNaN(hand.energyAfter(entry, hitpoints(300, two), two)));
		}
		for (double entry : new double[]{50, 99})
		{
			assertFalse(Double.isNaN(hand.energyAfter(entry, hitpoints(300, one), one)));
			assertTrue(Double.isNaN(hand.energyAfter(entry, hitpoints(300, two), two)));
		}
		assertFalse(Double.isNaN(hand.energyAfter(100, hitpoints(300, two), two)));
		// One actual 50% hammer leaves combat regeneration banked, rather than pretending it was all spent.
		assertEquals(50 + (300 / 6.0) / 3, hand.energyAfter(100, hitpoints(300, one), one), 1e-9);
		assertNull(one.withRegeneratedEnergy().getSpecial());
	}

	@Test public void olmHandsHaveSequentialEntryGatesAndSharedSavedStyles() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		List<RaidEncounter> hands = RaidPlanType.COX_CM.encounters(data).stream()
			.filter(e -> e.getTarget().getName().startsWith("great olm") && e.getTarget().getName().contains("hand"))
			.collect(java.util.stream.Collectors.toList());
		assertEquals(6, hands.size());
		for (int phase = 0; phase < 3; phase++)
		{
			assertEquals("great olm (melee hand)", hands.get(2 * phase).getTarget().getName());
			assertEquals("great olm (mage hand)", hands.get(2 * phase + 1).getTarget().getName());
			assertEquals("Olm Melee Hand", hands.get(2 * phase).getStyleKey());
			assertEquals("Olm Mage Hand", hands.get(2 * phase + 1).getStyleKey());
			assertEquals(1, hands.get(2 * phase).getRepetitions(), 0);
			assertEquals(phase == 0 ? 90 : 0, hands.get(2 * phase).getRestBefore(), 0);
		}
		SetupResult hammer = hammerOpening(2);
		SetupResult ordinary = setup(hammer.getLoadout().getWeapon(), null, 5);
		double afterFirst = hands.get(0).withEnergy(0, false, 1).energyAfter(100, hitpoints(300, hammer), hammer);
		assertTrue(afterFirst < 50);
		assertTrue(Double.isNaN(hands.get(2).energyAfter(afterFirst, hitpoints(300, hammer), hammer)));
		// Only time already earned on the intervening mage hand can fund another entry opener.
		double afterMage = hands.get(1).withEnergy(0, false, 1).energyAfter(afterFirst, hitpoints(1500, ordinary), ordinary);
		assertFalse(Double.isNaN(hands.get(2).energyAfter(afterMage, hitpoints(300, hammer), hammer)));
	}

	private static SetupResult hammerOpening(int count)
	{
		return hammerOpening(count, 5, count == 1 ? 6 : 7);
	}

	private static SetupResult hammerOpening(int count, double ordinaryDps, double dps)
	{
		GearItem hammer = item(13576, Slot.WEAPON); hammer.setName("dragon warhammer");
		SetupResult ordinary = setup(hammer, null, ordinaryDps);
		com.bestgearsetup.calc.CombatContext context = new com.bestgearsetup.calc.CombatContext(new Monster(),
			com.bestgearsetup.calc.PlayerLevels.maxed(), false, true, null);
		com.bestgearsetup.calc.SpecialAttack special = com.bestgearsetup.calc.SpecialAttack.of(ordinary.getLoadout(), context);
		DpsResult boosted = new DpsResult(dps, 0, 1, 4, 12, null);
		SpecialPlan plan = new SpecialPlan(hammer, null, null, ordinary.getLoadout().getStyle(), null, null,
			special, ordinary.getDps(), ordinary.getDps(), boosted, count, 0, 0, ordinary.getDps(), 0,
			Collections.emptyList(), count * 50, null);
		return new SetupResult(CombatClass.MELEE, ordinary.getLoadout(), boosted, 0, 0,
			Collections.emptySet(), Collections.emptySet(), plan);
	}

	@Test public void plannerChoosesAnAffordableHammerCountAtEachPhaseEntry()
	{
		SetupResult one = hammerOpening(1), two = hammerOpening(2);
		SetupResult ordinary = setup(one.getLoadout().getWeapon(), null, 5);
		SetupResult initialBar = barSetup(item(181, Slot.WEAPON), 10, 5);
		SetupResult waiting = setup(one.getLoadout().getWeapon(), null, 1);
		for (int entry : new int[]{0, 40, 50, 99, 100})
		{
			List<RaidEncounter> encounters = Arrays.asList(new RaidEncounter("Earlier fight", new MonsterSummary()),
				new RaidEncounter("Earned before this phase", new MonsterSummary()).withRepetitions(entry == 0 ? 0 : 1),
				new RaidEncounter("Olm hand phase", new MonsterSummary()));
			RaidPlan selected = RaidPlanner.select(RaidPlanType.COX_CM, RaidSpellbook.STANDARD, encounters,
				Arrays.asList(hitpoints(100, initialBar).withCandidates(Collections.singletonMap(AttackStyle.Type.CRUSH,
					Collections.singletonList(initialBar))), hitpoints(Math.max(1, entry * 3), waiting),
					hitpoints(300, two).withCandidates(Collections.singletonMap(AttackStyle.Type.CRUSH, Arrays.asList(two, one, ordinary)))),
				28, Long.MAX_VALUE, id -> true, gear -> 0, 0, id -> 0, () -> false);
			assertNotNull("entry " + entry, selected);
			SpecialPlan special = selected.getSetups().get(2).getSpecial();
			if (entry < 50) { assertNull(special); }
			else { assertEquals(entry == 100 ? 100 : 50, special.getOpeningEnergy(), 0); }
		}
	}

	/**
	 * Hammers opened in each of three Olm melee hand phases (780 HP; {@code none}, {@code one} and {@code two} seconds
	 * with no, one or two of this player's hammers) with a {@code mage}-second mage hand between them.
	 */
	private static List<Integer> olmHammerOpenings(double none, double one, double two, double mage)
	{
		SetupResult single = hammerOpening(1, 780 / none, 780 / one);
		SetupResult pair = hammerOpening(2, 780 / none, 780 / two);
		SetupResult ordinary = setup(single.getLoadout().getWeapon(), null, 780 / none);
		SetupResult trident = setup(item(4675, Slot.WEAPON), null, 450 / mage);
		List<RaidEncounter> encounters = new ArrayList<>();
		List<SearchResults> results = new ArrayList<>();
		for (int phase = 1; phase <= 3; phase++)
		{
			encounters.add(new RaidEncounter("Olm melee hand " + phase, new MonsterSummary()));
			results.add(hitpoints(780, pair).withCandidates(Collections.singletonMap(AttackStyle.Type.CRUSH,
				Arrays.asList(pair, single, ordinary))));
			if (phase < 3)
			{
				encounters.add(new RaidEncounter("Olm mage hand " + phase, new MonsterSummary()));
				results.add(hitpoints(450, trident));
			}
		}
		RaidPlan plan = RaidPlanner.select(RaidPlanType.COX_CM, RaidSpellbook.STANDARD, encounters, results, 28,
			Long.MAX_VALUE, id -> true, gear -> 0, 0, id -> 0, () -> false);
		assertNotNull(plan);
		List<Integer> openings = new ArrayList<>();
		for (int index = 0; index < encounters.size(); index += 2)
		{
			SpecialPlan special = plan.getSetups().get(index).getSpecial();
			openings.add(special == null ? 0 : (int) Math.round(special.getOpeningEnergy() / 50));
		}
		return openings;
	}

	/**
	 * An energy-allocation guard, not a valuation test: it passes with either model's hammer values. Two phase-1
	 * hammers are faster there and leave just under 50% for phase 2, yet one hammer in each of the first two phases
	 * (the bar refills over the mage hand) still affords two in phase 3, and 1/1/2 beats 2/0/2. The beam must keep
	 * the slower phase-1 kit with more energy rather than greedily spending the bar (it fails without the energy
	 * term in the beam's signature).
	 */
	@Test public void olmPhaseHammersAreAllocatedJointlyAcrossTheRaidsEnergy()
	{
		// Seconds per phase from the saved-profile diagnostic with teammates' openers ...
		assertEquals(Arrays.asList(1, 1, 2), olmHammerOpenings(93.8, 88.4, 84.4, 63));
		// ... and with the earlier shared model.
		assertEquals(Arrays.asList(1, 1, 2), olmHammerOpenings(123.0, 110.8, 101.2, 45));
	}

	@Test public void aLaterStartingEncounterStillSpendsEnergyInTheRaidsOrder()
	{
		SetupResult setup = barSetup(item(805, Slot.WEAPON), 10, 5);
		List<SearchResults> results = Arrays.asList(hitpoints(2000, setup), hitpoints(1000, setup));
		// Maiden refills the bar and, as the longer fight, takes it; her nylocas follow on what is left, whichever fight
		// the kit is worn in for.
		List<RaidEncounter> room = Arrays.asList(new RaidEncounter("Maiden", new MonsterSummary()).withEnergy(0, true, 1).withStartable(),
			new RaidEncounter("Nylocas", new MonsterSummary()).withStartable());
		assertEquals(Arrays.asList(true, false), fullBars(room, results));
		for (int start = 0; start < room.size(); start++)
		{
			RaidPlan plan = RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.STANDARD, room, results, 0, Long.MAX_VALUE,
				id -> false, gear -> 0, 0, id -> 0, () -> false, percent -> { }, Double.POSITIVE_INFINITY, new int[]{start});
			assertNotNull(plan);
			assertEquals(room.get(start), plan.getStartingEncounter());
			assertEquals(Arrays.asList(true, false), Arrays.asList(plan.getSetups().get(0).usesFullBar(),
				plan.getSetups().get(1).usesFullBar()));
		}
	}

	@Test public void onlyTheFirstOfRepeatedFightsOpensOnTheFullBar()
	{
		SetupResult setup = barSetup(item(802, Slot.WEAPON), 10, 5);
		RaidEncounter hands = new RaidEncounter("Olm hand", new MonsterSummary(), 3);
		// 100 HP at 10 DPS on the bar, then twice at 5 DPS on regenerated energy.
		assertEquals(10 + 2 * 20, hands.estimatedSeconds(hitpoints(100, setup), setup), 1e-9);
		assertEquals(1, hands.barsSpent(), 0);
		assertEquals(0.5, hands.withRepetitions(0.5).barsSpent(), 0);
		assertEquals(3 * 20, hands.estimatedSeconds(hitpoints(100, setup), setup.withRegeneratedEnergy()), 1e-9);
	}

	@Test public void bloatTakesAsLongAsHisDownsNotHisHitpointsOverDps() throws Exception
	{
		RaidEncounter.Downs downs = new RaidEncounter.Downs(43, 32, 38, 4);
		// Four players at 10 DPS deal 768 per 32-tick down: 1,750 HP takes three downs and two walks between them.
		assertEquals(4 * (43 * 0.6 + 2 * 70 * 0.6 + (1750 - 2 * 768) / 40.0), downs.seconds(10, 1750), 1e-9);
		// At 12 DPS it's two downs, saving more than the walk it no longer needs.
		assertEquals(4 * (43 * 0.6 + 70 * 0.6 + (1750 - 921.6) / 48), downs.seconds(12, 1750), 1e-9);
		assertTrue(downs.seconds(10, 1750) - downs.seconds(12, 1750) > 4 * 38 * 0.6);
		assertEquals(Double.POSITIVE_INFINITY, downs.seconds(0, 1750), 0);
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		RaidEncounter bloat = RaidPlanType.TOB.encounters(data, RaidScaling.Settings.builder().partySize(4).build()).stream()
			.filter(e -> e.getTarget().getName().startsWith("pestilent bloat")).findFirst().get();
		assertEquals(downs, bloat.getDowns());
		SetupResult setup = setup(item(803, Slot.WEAPON), null, 10);
		assertEquals(downs.seconds(10, 1750), bloat.estimatedSeconds(hitpoints(1750, setup), setup), 1e-9);
	}

	@Test public void eachRaidRegeneratesSpecEnergyBetweenRooms() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		RaidScaling.Settings trio = RaidScaling.Settings.builder().partySize(3).build();
		// The Theatre refills the bar as each room starts.
		List<String> refilled = new ArrayList<>();
		for (RaidEncounter encounter : RaidPlanType.TOB.encounters(data, trio))
		{
			if (encounter.isRefill()) { refilled.add(encounter.getTarget().getName()); }
			assertEquals(0, encounter.getRestBefore(), 0);
			assertEquals(3, encounter.getPartySize());
		}
		assertEquals(Arrays.asList("the maiden of sugadinti (normal)", "pestilent bloat (normal)", "nylocas ischyros (small)",
			"sotetseg (normal)", "xarpus", "verzik vitur (phase 1)"), refilled);
		// The Tombs and the Chambers regenerate it over each room's downtime instead.
		java.util.Map<String, Double> rests = new java.util.HashMap<>();
		for (RaidPlanType type : new RaidPlanType[]{RaidPlanType.TOA, RaidPlanType.COX_CM})
		{
			for (RaidEncounter encounter : type.encounters(data, trio))
			{
				assertTrue(!encounter.isRefill());
				if (encounter.getRestBefore() > 0) { rests.put(encounter.getLabel(), encounter.getRestBefore()); }
			}
		}
		assertEquals(60, rests.get("Ba-Ba"), 0);
		assertEquals(RaidPlanType.TOA_PATH_WALK + 55, rests.get("Kephri Shield"), 0);
		assertEquals(RaidPlanType.TOA_PATH_WALK + 40, rests.get("Akkha: Immune To Melee"), 0);
		assertEquals(RaidPlanType.TOA_PATH_WALK + 32, rests.get("Zebak"), 0);
		assertEquals(30, rests.get("Obelisk"), 0);
		assertEquals(195, rests.get("Ice Demon"), 0);
		assertEquals(90, rests.get("Olm Melee Hand"), 0);
		assertTrue(!rests.containsKey("Tekton") && !rests.containsKey("Akkha: Immune To Ranged"));
	}

	@Test public void everySpecWeaponOfARotationIsCarried()
	{
		SetupResult base = setup(item(161, Slot.WEAPON), null, 5);
		GearItem claws = item(162, Slot.WEAPON);
		GearItem halberd = item(163, Slot.WEAPON); halberd.setTwoHanded(true);
		SpecialPlan damage = new SpecialPlan(halberd, null, null, base.getLoadout().getStyle(), null, null, null,
			base.getDps(), base.getDps(), null, 1, 0, 0, null, 0, Collections.emptyList(), -1, null);
		SpecialPlan rotation = new SpecialPlan(claws, null, null, base.getLoadout().getStyle(), null, null, null,
			base.getDps(), base.getDps(), null, 2, 0, 0, null, 0, Collections.singletonList(damage), 100, null);
		SetupResult setup = new SetupResult(CombatClass.MELEE, base.getLoadout(), base.getDps(), 0, 0,
			Collections.emptySet(), Collections.emptySet(), rotation);
		assertEquals(Arrays.asList(rotation, damage), setup.getSpecials());
		assertEquals(100, rotation.getOpeningEnergy(), 0);
		List<RaidPlan.InventoryItem> inventory = RaidPlan.inventory(Collections.singletonList(setup),
			Collections.singletonList(result(setup)), id -> false);
		assertEquals(2, inventory.size());
		assertTrue(RaidPlan.gear(setup).containsKey(halberd.getId()) && RaidPlan.gear(setup).containsKey(claws.getId()));
	}

	@Test public void raidSharesOneDamageSpecPerCombatStyleAndKeepsFeasibleOrdinaryAlternatives()
	{
		GearItem sword = item(170, Slot.WEAPON);
		SetupResult ordinary = setup(sword, null, 5);
		SetupResult claws = withSpec(setup(sword, null, 8), item(171, Slot.WEAPON));
		SetupResult dagger = withSpec(setup(sword, null, 9), item(172, Slot.WEAPON));
		assertFalse(RaidPlanner.specialWeaponsAllowed(Arrays.asList(claws, dagger)));
		RaidPlan selected = plan(Arrays.asList(result(claws, ordinary), result(dagger, ordinary)), 28, Long.MAX_VALUE);
		assertNotNull(selected);
		assertTrue(RaidPlanner.specialWeaponsAllowed(selected.getSetups()));
		assertEquals(1, selected.getSetups().stream().flatMap(s -> s.getSpecials().stream()).count());
		assertEquals(dagger, selected.getSetups().get(1));
	}

	@Test public void raidCanPackOneDamageSpecForEachOfMeleeRangedAndMagic()
	{
		List<SetupResult> setups = new ArrayList<>();
		String[] styles = {"slash,slash,aggressive", "rapid,ranged,rapid", "spell,magic,accurate"};
		for (int index = 0; index < styles.length; index++)
		{
			SetupResult ordinary = setup(item(9300 + index, Slot.WEAPON), null, 5);
			ordinary.getLoadout().setStyle(AttackStyle.parse(styles[index]));
			setups.add(withSpec(ordinary, item(9310 + index, Slot.WEAPON)));
		}
		assertTrue(RaidPlanner.specialWeaponsAllowed(setups));
		RaidPlan selected = plan(Arrays.asList(result(setups.get(0)), result(setups.get(1)), result(setups.get(2))),
			28, Long.MAX_VALUE);
		assertNotNull(selected);
		assertEquals(3, selected.getSetups().stream().flatMap(setup -> setup.getSpecials().stream()).count());
		assertTrue(selected.getEquipment().stream().anyMatch(item -> item.getId() == 9310));
		assertTrue(selected.getEquipment().stream().anyMatch(item -> item.getId() == 9311));
		assertTrue(selected.getEquipment().stream().anyMatch(item -> item.getId() == 9312));
		// The three class allowances do not bypass the carried equipment limit.
		assertNull(plan(Arrays.asList(result(setups.get(0)), result(setups.get(1)), result(setups.get(2))),
			1, Long.MAX_VALUE));
	}

	@Test public void aSecondDamageSpecInAnyCombatClassIsRejected()
	{
		for (String style : new String[]{"slash,slash,aggressive", "rapid,ranged,rapid", "spell,magic,accurate"})
		{
			SetupResult ordinary = setup(item(9320, Slot.WEAPON), null, 5);
			ordinary.getLoadout().setStyle(AttackStyle.parse(style));
			SetupResult first = withSpec(ordinary, item(9321, Slot.WEAPON));
			assertTrue(RaidPlanner.specialWeaponsAllowed(Arrays.asList(first, first)));
			assertFalse(style, RaidPlanner.specialWeaponsAllowed(Arrays.asList(first,
				withSpec(ordinary, item(9322, Slot.WEAPON)))));
		}
		SetupResult slash = setup(item(9330, Slot.WEAPON), null, 5);
		slash.getLoadout().setStyle(AttackStyle.parse("slash,slash,aggressive"));
		SetupResult stab = setup(item(9330, Slot.WEAPON), null, 5);
		stab.getLoadout().setStyle(AttackStyle.parse("stab,stab,accurate"));
		assertFalse("Stab and slash share the melee allowance", RaidPlanner.specialWeaponsAllowed(Arrays.asList(
			withSpec(slash, item(9331, Slot.WEAPON)), withSpec(stab, item(9332, Slot.WEAPON)))));
	}

	@Test public void providedDawnbringerDoesNotConsumeTheCarriedDamageSpecChoice()
	{
		SetupResult ordinary = setup(item(173, Slot.WEAPON), null, 5);
		GearItem dawnbringer = item(174, Slot.WEAPON); dawnbringer.setName("dawnbringer");
		assertTrue(RaidPlanner.specialWeaponsAllowed(Arrays.asList(withSpec(ordinary, item(175, Slot.WEAPON)),
			withSpec(ordinary, dawnbringer))));
	}

	@Test public void reducersCountAsReducersEvenWhenNoDrainIsCredited()
	{
		SetupResult ordinary = setup(item(176, Slot.WEAPON), null, 5);
		GearItem hammer = item(13576, Slot.WEAPON); hammer.setName("dragon warhammer");
		GearItem bgs = item(11804, Slot.WEAPON); bgs.setName("bandos godsword");
		List<SetupResult> reducers = new ArrayList<>();
		for (GearItem weapon : Arrays.asList(hammer, bgs))
		{
			Loadout held = ordinary.getLoadout().copy(); held.set(Slot.WEAPON, weapon);
			com.bestgearsetup.calc.CombatContext context = new com.bestgearsetup.calc.CombatContext(new Monster(),
				com.bestgearsetup.calc.PlayerLevels.maxed(), false, true, null);
			SpecialPlan special = new SpecialPlan(weapon, null, null, held.getStyle(), null, null,
				com.bestgearsetup.calc.SpecialAttack.of(held, context), ordinary.getDps(), ordinary.getDps(), null, 1, 0, 0);
			reducers.add(new SetupResult(ordinary.getCombatClass(), ordinary.getLoadout(), ordinary.getDps(), 0, 0,
				Collections.emptySet(), Collections.emptySet(), special));
		}
		assertFalse(RaidPlanner.specialWeaponsAllowed(reducers));
	}

	private static SetupResult withSpec(SetupResult base, GearItem weapon)
	{
		SpecialPlan special = new SpecialPlan(weapon, null, null, base.getLoadout().getStyle(), null, null, null,
			base.getDps(), base.getDps(), null, 1, 0, 0);
		return new SetupResult(base.getCombatClass(), base.getLoadout(), base.getDps(), 0, 0,
			Collections.emptySet(), Collections.emptySet(), special);
	}

	@Test public void bossesAreKilledTogetherAndAddsOneEach() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		RaidScaling.Settings trio = RaidScaling.Settings.builder().partySize(3).build();
		java.util.Map<String, RaidEncounter> byLabel = new java.util.HashMap<>();
		for (RaidPlanType type : new RaidPlanType[]{RaidPlanType.COX_CM, RaidPlanType.TOA, RaidPlanType.TOB})
		{
			type.encounters(data, trio).forEach(encounter -> byLabel.put(encounter.getLabel(), encounter));
		}
		// A boss is the party's, and its kill ends the fight.
		for (String boss : new String[]{"Maiden", "Verzik P3", "Ba-Ba", "Olm Head", "Akkha: Final Stand (Melee Only)", "Zebak Enraged"})
		{
			assertEquals(boss, 3, byLabel.get(boss).getKillShare());
			assertEquals(boss, 1, byLabel.get(boss).getFinalBlow(), 0);
		}
		// Adds are each player's own, killed back to back.
		for (String add : new String[]{"Nylocas Waves: Melee", "Maiden Nylocas", "Baboon Brawlers", "Soldier Scarab", "Mystics"})
		{
			assertEquals(add, 1, byLabel.get(add).getKillShare());
			assertEquals(add, 0, byLabel.get(add).getFinalBlow(), 0);
		}
		// Phases ending at a threshold, and Bloat's downs, never end the fight with a free attack.
		for (String phase : new String[]{"Akkha: Immune To Melee", "Zebak", "Bloat", "Vasa Crystal", "Elidinis P2: Ranged"})
		{ assertEquals(phase, 0, byLabel.get(phase).getFinalBlow(), 0); }
		assertEquals(0.75, byLabel.get("Tekton").getFinalBlow(), 1e-12);
		assertEquals(1 / 3.0, byLabel.get("Nylocas: Magic").getFinalBlow(), 1e-12);
	}

	@Test public void phasesOfOneHealthPoolShareIt() throws Exception
	{
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		for (RaidPlanType type : new RaidPlanType[]{RaidPlanType.COX_CM, RaidPlanType.TOA, RaidPlanType.TOB})
		{
			java.util.Map<String, Double> pools = new java.util.HashMap<>();
			for (RaidEncounter encounter : type.encounters(data))
			{
				String name = encounter.getTarget().getName();
				String pool = name.startsWith("akkha") && !name.contains("shadow") ? "akkha"
					: name.startsWith("tekton") ? "tekton" : name.startsWith("zebak") ? "zebak"
					: name.startsWith("nylocas vasilias") ? "vasilias" : null;
				if (pool != null) { pools.merge(pool, encounter.getShare(), Double::sum); }
				else if (!name.endsWith("(core-ejected)")) { assertEquals(name, 1, encounter.getShare(), 0); }
			}
			// Akkha's final stand restores a fifth of her health on top of the whole pool.
			pools.forEach((pool, share) -> assertEquals(pool, pool.equals("akkha") ? 1.2 : 1, share, 1e-12));
		}
		RaidEncounter core = RaidPlanType.TOA.encounters(data).stream()
			.filter(e -> e.getTarget().getName().endsWith("(core-ejected)")).findFirst().get();
		assertEquals(0.2, core.getShare(), 0);
	}

	@Test public void wardenShieldBreaksFollowTheCoreDamage() throws Exception
	{
		// The first three windows last 21, 29 and 37 ticks while the Warden is above 80%, above 60% and below.
		assertEquals(1, RaidPlanType.coreEjections(1000, 1000.1 / 12.6));
		assertEquals(2, RaidPlanType.coreEjections(1000, 999 / 12.6));
		assertEquals(3, RaidPlanType.coreEjections(1000, 1000.1 / (12.6 + 17.4 + 22.2)));
		assertEquals(4, RaidPlanType.coreEjections(1000, 999 / (12.6 + 17.4 + 22.2)));
		assertEquals(50, RaidPlanType.coreEjections(1000, 0));
		GameData data = new GameDataLoader(new Gson()).loadGameData();
		List<RaidEncounter> encounters = RaidPlanType.TOA.encounters(data);
		List<SearchResults> results = new ArrayList<>();
		// A party of two deals the core's 1,000 effective HP (a fifth of 5,000) in three windows.
		double coreDps = 1000.1 / (12.6 + 17.4 + 22.2) / 2;
		for (RaidEncounter encounter : encounters)
		{
			boolean core = encounter.getTarget().getName().endsWith("(core-ejected)");
			SetupResult setup = setup(item(1, Slot.WEAPON), null, core ? coreDps : 10);
			Monster monster = new Monster();
			monster.setName(encounter.getTarget().getName());
			results.add(new SearchResults(monster, core ? 5000 : 100, Collections.singletonMap(AttackStyle.Type.CRUSH,
				Collections.singletonList(setup)), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false));
		}
		List<RaidEncounter> cycled = RaidPlanType.withCycles(encounters, results, 2);
		for (int index = 0; index < encounters.size(); index++)
		{
			String name = encounters.get(index).getTarget().getName();
			// Half of raids fight each Warden in P2, and its three breaks alternate the two shield styles.
			assertEquals(name, name.endsWith("(phase 2)") ? 0.5 * 3 / 2 : encounters.get(index).getRepetitions(),
				cycled.get(index).getRepetitions(), 1e-12);
		}
	}

	@Test public void repeatedFightTimeChangesWhichSwitchThePlannerKeeps()
	{
		GearItem weapon = item(91, Slot.WEAPON);
		GearItem shared = item(92, Slot.BODY);
		SetupResult start = setup(weapon, shared, 10);
		SetupResult early = setup(weapon, item(93, Slot.BODY), 10);
		SetupResult earlyShared = setup(weapon, shared, 8);
		SetupResult olm = setup(weapon, item(94, Slot.BODY), 10);
		SetupResult olmShared = setup(weapon, shared, 9);
		List<SearchResults> results = Arrays.asList(result(start), result(early, earlyShared), result(olm, olmShared));
		for (int repetitions : new int[]{1, 3})
		{
			List<RaidEncounter> encounters = Arrays.asList(new RaidEncounter("Start", new MonsterSummary()),
				new RaidEncounter("Early boss", new MonsterSummary()), new RaidEncounter("Olm hand", new MonsterSummary(), repetitions));
			RaidPlan plan = RaidPlanner.select(RaidPlanType.COX_CM, RaidSpellbook.ARCEUUS, encounters, results, 1,
				Long.MAX_VALUE, id -> false, gear -> 0, 0, id -> 0, () -> false);
			assertNotNull(plan);
			assertEquals(repetitions == 1 ? early : earlyShared, plan.getSetups().get(1));
			assertEquals(repetitions == 1 ? olmShared : olm, plan.getSetups().get(2));
			assertEquals(1, plan.getInventory().size());
			assertEquals(100, results.get(2).getStartingHitpoints());
		}
	}

	@Test public void choosesSlowerSharedGearInsteadOfDroppingItemsToFitTheCap()
	{
		GearItem shared = item(101, Slot.BODY);
		SetupResult start = setup(item(102, Slot.WEAPON), shared, 5);
		SetupResult fastest = setup(item(103, Slot.WEAPON), item(104, Slot.BODY), 10);
		SetupResult compact = setup(item(103, Slot.WEAPON), shared, 8);
		RaidPlan plan = plan(Arrays.asList(result(start), result(fastest, compact)), 1, Long.MAX_VALUE);
		assertNotNull(plan);
		assertEquals(compact, plan.getSetups().get(1));
		assertEquals(1, plan.getInventory().size());
		assertEquals(Integer.valueOf(103), plan.getInventory().get(0).getIds().get(0));
		assertNull(plan(Arrays.asList(result(start), result(fastest)), 1, Long.MAX_VALUE));
	}

	@Test public void raidAmmoStaysWornAndCountsAsEquipmentRatherThanInventory()
	{
		GearItem arrows = item(160, Slot.AMMO); arrows.setRangedStr(40);
		GearItem bow = item(161, Slot.WEAPON); bow.setAmmunition(Collections.singletonList(160));
		SetupResult melee = setup(item(162, Slot.WEAPON), null, 5);
		SetupResult ranged = setup(bow, null, 7); ranged.getLoadout().set(Slot.AMMO, arrows);
		RaidPlan plan = plan(Arrays.asList(result(melee), result(ranged)), 1, Long.MAX_VALUE);
		assertNotNull(plan);
		assertEquals(arrows, plan.getStartingSetup().getLoadout().get(Slot.AMMO));
		assertNull(melee.getLoadout().get(Slot.AMMO)); // published filling does not mutate optimizer candidates
		assertEquals(arrows, plan.getSetups().get(1).getLoadout().get(Slot.AMMO));
		assertEquals(1, plan.getInventory().size());
		assertTrue(plan.getInventory().stream().noneMatch(item -> item.getIds().contains(160)));
		assertEquals(1, RaidPlan.maximumInventory(plan.getSetups(), plan.getResults(), id -> false));
		assertEquals(Integer.valueOf(10), BestGearSetupBankView.raidCellsFor(plan).get(160));
	}

	@Test public void raidAmmoPreservesBlessingsAndExplicitEmptyLocks()
	{
		GearItem arrows = item(170, Slot.AMMO); arrows.setRangedStr(40);
		GearItem blessing = item(171, Slot.AMMO);
		SetupResult melee = setup(item(172, Slot.WEAPON), null, 5); melee.getLoadout().set(Slot.AMMO, blessing);
		SetupResult bow = setup(item(173, Slot.WEAPON), null, 7); bow.getLoadout().set(Slot.AMMO, arrows);
		assertEquals(blessing, RaidPlan.fill(Arrays.asList(melee, bow), Arrays.asList(result(melee), result(bow))).get(0).getLoadout().get(Slot.AMMO));
		melee.getLoadout().set(Slot.AMMO, null);
		SearchResults empty = new SearchResults(new Monster(), 100, result(melee).getByType(), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), false,
			Collections.singletonList(new LockStatus(Slot.AMMO, SlotLock.empty(), null, null, null)), false, false, Collections.emptyList());
		assertNull(RaidPlan.fill(Arrays.asList(melee, bow), Arrays.asList(empty, result(bow))).get(0).getLoadout().get(Slot.AMMO));
	}

	@Test public void inventoryBlowpipeShowsLoadedDartsWithoutAnExtraCarriedSlot()
	{
		SetupResult start = setup(item(180, Slot.WEAPON), null, 5);
		GearItem pipe = item(181, Slot.WEAPON); pipe.setName("toxic blowpipe");
		GearItem darts = item(182, Slot.AMMO); darts.setName("amethyst dart"); darts.setRangedStr(28);
		SetupResult ranged = setup(pipe, null, 7); ranged.getLoadout().setLoadedAmmo(darts);
		RaidPlan plan = plan(Arrays.asList(result(start), result(ranged)), 1, Long.MAX_VALUE);
		assertNotNull(plan);
		assertNull(plan.getStartingSetup().getLoadout().get(Slot.AMMO));
		assertNull(plan.getSetups().get(1).getLoadout().get(Slot.AMMO));
		assertEquals(1, plan.getInventory().size());
		assertEquals("amethyst dart", plan.getInventory().get(0).getContents().get(0).getName());
		assertEquals(Integer.valueOf(182), plan.inventoryFor(start).get(0).getContents().get(0).getIds().get(0));
		assertEquals("Loaded darts are not duplicated as potion copies", 1,
			BestGearSetupBankView.raidCellsFor(plan, 182).size());
	}

	@Test public void multipleAmmoTypesRemainCorrectForTheirWeapons()
	{
		GearItem arrows = item(190, Slot.AMMO); arrows.setRangedStr(40);
		GearItem bolts = item(191, Slot.AMMO); bolts.setRangedStr(100);
		SetupResult start = setup(item(192, Slot.WEAPON), null, 5);
		SetupResult bow = setup(item(193, Slot.WEAPON), null, 7); bow.getLoadout().set(Slot.AMMO, arrows);
		SetupResult crossbow = setup(item(194, Slot.WEAPON), null, 7); crossbow.getLoadout().set(Slot.AMMO, bolts);
		List<SetupResult> filled = RaidPlan.fill(Arrays.asList(start, bow, crossbow, crossbow),
			Arrays.asList(result(start), result(bow), result(crossbow), result(crossbow)));
		assertEquals(bolts, filled.get(0).getLoadout().get(Slot.AMMO));
		assertEquals(arrows, filled.get(1).getLoadout().get(Slot.AMMO));
		assertEquals(bolts, filled.get(2).getLoadout().get(Slot.AMMO));
	}

	@Test public void startingSetupWearsACarriedSwitchInAnEmptySlotInsteadOfPackingIt()
	{
		// A mage start with an empty ring slot wears the melee ring, so the god cape fits instead of a mage ring.
		GearItem godCape = item(200, Slot.CAPE); godCape.setMagicBonus(15);
		GearItem meleeRing = item(201, Slot.RING); meleeRing.setMeleeStr(8);
		SetupResult mage = setup(item(202, Slot.WEAPON), null, 7); mage.getLoadout().set(Slot.CAPE, godCape);
		GearItem platebody = item(204, Slot.BODY); platebody.setMagicBonus(-30);
		SetupResult melee = setup(item(203, Slot.WEAPON), platebody, 5); melee.getLoadout().set(Slot.RING, meleeRing);
		RaidPlan plan = plan(Arrays.asList(result(mage), result(melee)), 2, Long.MAX_VALUE);
		assertNotNull(plan);
		assertEquals(meleeRing, plan.getStartingSetup().getLoadout().get(Slot.RING));
		assertTrue(plan.getStartingSetup().getFilledSlots().contains(Slot.RING));
		assertEquals(mage.getDps(), plan.getStartingSetup().getDps());
		assertNull(mage.getLoadout().get(Slot.RING)); // published filling does not mutate optimizer candidates
		assertNull(plan.getStartingSetup().getLoadout().get(Slot.BODY)); // armour that lowers magic stays carried
		assertEquals(2, plan.getInventory().size());
		assertTrue(plan.getInventory().stream().noneMatch(item -> item.getIds().contains(201)));

		// An item that could lower the start's damage stays carried, so the same kit no longer fits.
		meleeRing.setMagicBonus(-5);
		assertNull(plan(Arrays.asList(result(mage), result(melee)), 2, Long.MAX_VALUE));
		assertNotNull(plan(Arrays.asList(result(mage), result(melee)), 3, Long.MAX_VALUE));
	}

	@Test public void kitIsWornInForWhicheverFirstRoomEncounterCarriesFewestSwitches()
	{
		// Magic is fought first, but wearing in the melee gear (needed twice) leaves fewer switches to carry.
		GearItem godCape = item(220, Slot.CAPE); godCape.setMagicBonus(15);
		GearItem helm = item(221, Slot.HEAD); helm.setMagicBonus(-6);
		GearItem platebody = item(222, Slot.BODY); platebody.setMagicBonus(-30);
		SetupResult mage = setup(item(223, Slot.WEAPON), null, 7); mage.getLoadout().set(Slot.CAPE, godCape);
		SetupResult melee = setup(item(224, Slot.WEAPON), platebody, 5); melee.getLoadout().set(Slot.HEAD, helm);
		List<SearchResults> results = Arrays.asList(result(mage), result(melee), result(melee));
		List<RaidEncounter> firstRoom = Arrays.asList(new RaidEncounter("Magic", new MonsterSummary()).withStartable(),
			new RaidEncounter("Melee", new MonsterSummary()).withStartable(), new RaidEncounter("Later melee", new MonsterSummary()));
		RaidPlan plan = RaidPlanner.select(RaidPlanType.TOA, RaidSpellbook.ARCEUUS, firstRoom, results, 2, Long.MAX_VALUE,
			id -> false, item -> 10, 0, id -> 0, () -> false);
		assertNotNull(plan);
		assertEquals(firstRoom.get(1), plan.getStartingEncounter());
		assertEquals(platebody, plan.getStartingSetup().getLoadout().get(Slot.BODY));
		// The god cape has no negative bonus, so the melee start wears it too: only the staff is carried.
		assertEquals(godCape, plan.getStartingSetup().getLoadout().get(Slot.CAPE));
		assertEquals(mage, plan.getSetups().get(0));
		assertEquals(1, plan.getInventory().stream().filter(RaidPlan.InventoryItem::isEquipment).count());

		// Starting in the first encounter only, the same kit carries three switches.
		assertNull(plan(results, 2, Long.MAX_VALUE));
	}

	@Test public void sharedBudgetPricesUniqueItemsAcrossAllBosses()
	{
		GearItem body = item(110, Slot.BODY);
		SetupResult first = setup(item(111, Slot.WEAPON), body, 5);
		SetupResult second = setup(item(112, Slot.WEAPON), body, 7);
		assertNull(plan(Arrays.asList(result(first), result(second)), 28, 25));
		RaidPlan plan = plan(Arrays.asList(result(first), result(second)), 28, 30);
		assertNotNull(plan);
		assertEquals(30, plan.getBuyCost());
	}

	@Test public void inventoryAndBankHaveNoDuplicateStartingItemsAndUseFourColumns()
	{
		SetupResult first = setup(item(121, Slot.WEAPON), item(122, Slot.BODY), 5);
		SetupResult second = setup(item(123, Slot.WEAPON), item(124, Slot.BODY), 7);
		RaidPlan plan = plan(Arrays.asList(result(first), result(second)), 2, Long.MAX_VALUE);
		Map<Integer, Integer> cells = BestGearSetupBankView.raidCellsFor(plan);
		assertEquals(Integer.valueOf(16), cells.get(121));
		assertEquals(Integer.valueOf(17), cells.get(122));
		assertEquals(Integer.valueOf(4), cells.get(123));
		assertEquals(Integer.valueOf(12), cells.get(124));
		assertEquals(55, BestGearSetupBankView.inventoryCell(27));
		assertEquals(4, cells.size());
	}

	@Test public void checksCapacityAfterRemovingWornItemsDuringSwitches()
	{
		SetupResult first = setup(item(131, Slot.WEAPON), item(132, Slot.BODY), 5);
		SetupResult second = setup(item(133, Slot.WEAPON), null, 7);
		assertEquals(1, RaidPlan.inventory(Arrays.asList(first, second), Arrays.asList(result(first), result(second)), id -> false).size());
		assertEquals(2, RaidPlan.maximumInventory(Arrays.asList(first, second), Arrays.asList(result(first), result(second)), id -> false));
	}

	@Test public void cancellationAndMissingEncounterPreventIncompletePublication()
	{
		SetupResult first = setup(item(141, Slot.WEAPON), null, 5);
		assertNull(plan(Arrays.asList(result(first), result()), 28, Long.MAX_VALUE));
		assertNull(RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, Collections.emptyList(), Collections.singletonList(result(first)),
			28, Long.MAX_VALUE, id -> false, item -> 10, 0, id -> 0, () -> true));
	}

	@Test public void runePouchPacksThreeTypesAndDivinePouchPacksFourWithoutExtraSlots()
	{
		SetupResult ordinary = setup(item(145, Slot.WEAPON), null, 5);
		SetupResult spells = new SetupResult(CombatClass.MELEE, ordinary.getLoadout(), ordinary.getDps(), 0, 0,
			Collections.emptySet(), EnumSet.of(SupportSpell.THRALL, SupportSpell.MARK_OF_DARKNESS));
		List<SetupResult> setups = Collections.singletonList(spells);
		List<SearchResults> results = Collections.singletonList(result(spells));
		List<RaidPlan.InventoryItem> regular = RaidPlan.inventory(setups, results, id -> id == ItemID.BH_RUNE_POUCH);
		RaidPlan.InventoryItem pouch = regular.stream().filter(i -> !i.getContents().isEmpty()).findFirst().get();
		assertEquals(3, pouch.getContents().size());
		assertEquals(3, regular.size()); // pouch, overflow rune stack, Book of the dead
		assertEquals(3, RaidPlan.maximumInventory(setups, results, id -> id == ItemID.BH_RUNE_POUCH));
		List<RaidPlan.InventoryItem> divine = RaidPlan.inventory(setups, results,
			id -> id == ItemID.DIVINE_RUNE_POUCH || id == ItemID.BH_RUNE_POUCH);
		RaidPlan.InventoryItem divinePouch = divine.stream().filter(i -> !i.getContents().isEmpty()).findFirst().get();
		assertEquals(4, divinePouch.getContents().size());
		assertEquals(2, divine.size());
		assertTrue(!divinePouch.getIds().contains(ItemID.BH_RUNE_POUCH));
		// The divine pouch and the Book of the dead use two of the equipment limit's slots; the packed runes none.
		assertNull(RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS,
			Collections.singletonList(new RaidEncounter("Boss", new MonsterSummary())), results, 1, Long.MAX_VALUE,
			id -> id == ItemID.DIVINE_RUNE_POUCH, gear -> 10, 0, id -> 0, () -> false));
		RaidPlan plan = RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS,
			Collections.singletonList(new RaidEncounter("Boss", new MonsterSummary())), results, 2, Long.MAX_VALUE,
			id -> id == ItemID.DIVINE_RUNE_POUCH, gear -> 10, 0, id -> 0, () -> false);
		assertNotNull(plan);
		assertEquals(2, RaidPlan.limitedSlots(plan.getInventory()));
		assertTrue(plan.supportsSupplies(spells, result(spells), id -> id == ItemID.DIVINE_RUNE_POUCH));
		Map<Integer, Integer> bank = BestGearSetupBankView.raidCellsFor(plan);
		assertEquals(Integer.valueOf(4), bank.get(ItemID.DIVINE_RUNE_POUCH));
		assertTrue("A raid without switches keeps packed runes beside the equipment", bank.get(ItemID.FIRERUNE) < 16);
		assertEquals(5, RaidPlan.inventory(setups, results, id -> false).size());
	}

	@Test public void spellSuppliesCountTowardTheEquipmentLimitButBoostsDoNot()
	{
		RaidPlan.InventoryItem pouch = new RaidPlan.InventoryItem("Divine rune pouch", Collections.singletonList(ItemID.DIVINE_RUNE_POUCH),
			false, Collections.singletonList(new RaidPlan.InventoryItem("Fire rune", Collections.singletonList(ItemID.FIRERUNE), false)));
		List<RaidPlan.InventoryItem> inventory = Arrays.asList(
			new RaidPlan.InventoryItem("item 1", Collections.singletonList(1), true),
			pouch,
			new RaidPlan.InventoryItem("Blood rune", Collections.singletonList(ItemID.BLOODRUNE), false),
			new RaidPlan.InventoryItem("Book of the dead", Collections.singletonList(ItemID.BOOK_OF_THE_DEAD), false),
			new RaidPlan.InventoryItem("Combat potion", Collections.singletonList(ItemID._4DOSE2COMBAT), false,
				Collections.emptyList(), RaidPlan.InventoryGroup.BOOSTS),
			new RaidPlan.InventoryItem("Combat potion", Collections.singletonList(ItemID._4DOSE2COMBAT), false,
				Collections.emptyList(), RaidPlan.InventoryGroup.BOOSTS));
		// The switch, the pouch (its runes take no slot), the loose rune and the book; never the boost bottles.
		assertEquals(4, RaidPlan.limitedSlots(inventory));
	}

	/**
	 * A Vasilias-style group where many fast ranged switches each change the neck and cape as well as the weapon, so
	 * none is a quick switch from any form's setup. Only the slow weapon-only switch completes the group.
	 */
	private static List<SearchResults> inconsistentSwitches(SetupResult full, SetupResult consistent, SetupResult magic)
	{
		List<SetupResult> ranged = new ArrayList<>();
		for (int i = 0; i < 130; i++)
		{
			SetupResult fast = wearing(CombatClass.RANGED, 10 + i * 0.01, 200, 101, 102, 103, 104);
			fast.getLoadout().set(Slot.NECK, item(1000 + i, Slot.NECK));
			fast.getLoadout().set(Slot.CAPE, item(2000 + i, Slot.CAPE));
			ranged.add(fast);
		}
		ranged.add(consistent);
		return Arrays.asList(result(full), result(ranged.toArray(new SetupResult[0])), result(magic));
	}

	private static List<RaidEncounter> forms()
	{
		return Arrays.asList(
			new RaidEncounter("Melee", new MonsterSummary()).withSwitchGroup("forms"),
			new RaidEncounter("Ranged", new MonsterSummary()).withSwitchGroup("forms"),
			new RaidEncounter("Magic", new MonsterSummary()).withSwitchGroup("forms"));
	}

	@Test public void switchGroupsStayConsistentAsEachMemberJoinsTheKit()
	{
		SetupResult full = wearing(CombatClass.MELEE, 5, 100, 101, 102, 103, 104);
		SetupResult consistent = wearing(CombatClass.RANGED, 1, 200, 101, 102, 103, 104);
		SetupResult magic = wearing(CombatClass.MAGIC, 5, 300, 101, 102, 103, 104);
		List<SearchResults> results = inconsistentSwitches(full, consistent, magic);
		// Once complete, the beam used to hold only the faster, inconsistent ranged switches and found no kit at all.
		RaidPlan plan = RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, forms(), results, 28, Long.MAX_VALUE,
			id -> true, gear -> 0, 0, id -> 0, () -> false);
		assertNotNull(plan);
		assertEquals(Arrays.asList(full, consistent, magic), plan.getSetups());
	}

	@Test public void raisingTheEquipmentLimitNeverSlowsOrLosesTheKit()
	{
		SetupResult full = wearing(CombatClass.MELEE, 5, 100, 101, 102, 103, 104);
		SetupResult consistent = wearing(CombatClass.RANGED, 1, 200, 101, 102, 103, 104);
		SetupResult magic = wearing(CombatClass.MAGIC, 5, 300, 101, 102, 103, 104);
		List<SearchResults> results = inconsistentSwitches(full, consistent, magic);
		// The two weapon switches fit two slots; before, three or more let the inconsistent switches crowd them out.
		double previous = Double.POSITIVE_INFINITY;
		for (int limit = 0; limit <= 28; limit++)
		{
			RaidPlan plan = RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, forms(), results, limit, Long.MAX_VALUE,
				id -> true, gear -> 0, 0, id -> 0, () -> false);
			if (limit < 2) { assertNull(plan); continue; }
			assertNotNull("limit " + limit, plan);
			assertTrue("limit " + limit, RaidPlan.limitedSlots(plan.getInventory()) <= limit);
			double seconds = RaidConsolidator.seconds(plan.getSetups(), plan.getResults(), plan.getEncounters());
			assertTrue("limit " + limit, seconds <= previous);
			previous = seconds;
		}
	}

	@Test public void specialSwitchAlsoNeedsRoomForTheUnequippedShield()
	{
		SetupResult ordinary = setup(item(151, Slot.WEAPON), null, 5);
		ordinary.getLoadout().set(Slot.SHIELD, item(152, Slot.SHIELD));
		GearItem specWeapon = item(153, Slot.WEAPON); specWeapon.setTwoHanded(true);
		SpecialPlan special = new SpecialPlan(specWeapon, null, null, ordinary.getLoadout().getStyle(), null, null,
			null, ordinary.getDps(), ordinary.getDps(), null, 1, 0, 0);
		SetupResult setup = new SetupResult(CombatClass.MELEE, ordinary.getLoadout(), ordinary.getDps(), 0, 0,
			Collections.emptySet(), Collections.emptySet(), special);
		assertEquals(1, RaidPlan.inventory(Collections.singletonList(setup), Collections.singletonList(result(setup)), id -> false).size());
		assertEquals(2, RaidPlan.maximumInventory(Collections.singletonList(setup), Collections.singletonList(result(setup)), id -> false));
	}

	@Test public void inventoryOrdersCombatSwitchesThenSpecialsToolsBoostsAndPackedRunes()
	{
		SetupResult start = setup(item(100, Slot.WEAPON), item(101, Slot.BODY), 5);
		SetupResult magicBase = setup(item(200, Slot.WEAPON), item(201, Slot.BODY), 5);
		GearItem specialWeapon = item(500, Slot.WEAPON);
		SpecialPlan special = new SpecialPlan(specialWeapon, null, null, magicBase.getLoadout().getStyle(), null, null,
			null, magicBase.getDps(), magicBase.getDps(), null, 1, 0, 0);
		SetupResult magic = new SetupResult(CombatClass.MAGIC, magicBase.getLoadout(), magicBase.getDps(), 0, 0,
			Collections.emptySet(), EnumSet.of(SupportSpell.THRALL), special);
		SetupResult rangeBase = setup(item(300, Slot.WEAPON), item(301, Slot.BODY), 5);
		SetupResult ranged = new SetupResult(CombatClass.RANGED, rangeBase.getLoadout(), rangeBase.getDps(), 0);
		SetupResult melee = setup(item(400, Slot.WEAPON), item(401, Slot.BODY), 5);
		GearItem pickaxe = item(600, Slot.WEAPON); pickaxe.setSubcategory("pickaxe");
		SetupResult tool = setup(pickaxe, null, 5);
		List<SetupResult> setups = Arrays.asList(start, magic, ranged, melee, tool);
		com.bestgearsetup.data.Potion combat = new com.bestgearsetup.data.Potion();
		combat.setId(ItemID._4DOSE2COMBAT);
		SearchResults magicResult = new SearchResults(new Monster(), 100,
			Collections.singletonMap(AttackStyle.Type.MAGIC, Collections.singletonList(magic)), Collections.emptyList(),
			Collections.emptyMap(), Collections.singletonMap(CombatClass.MAGIC, Collections.singletonList(combat)), false);
		List<SearchResults> results = Arrays.asList(result(start), magicResult, result(ranged), result(melee), result(tool));
		List<RaidPlan.InventoryItem> inventory = RaidPlan.inventory(setups, results, id -> id == ItemID.DIVINE_RUNE_POUCH);
		// The carried melee alternative has the largest switch even though the starting attack is melee.
		assertEquals(Arrays.asList(400, 500, 600, ItemID.DIVINE_RUNE_POUCH, 401, ItemID.BOOK_OF_THE_DEAD,
			300, 301, 200, 201, ItemID._4DOSEDIVINECOMBAT), inventory.stream().map(i -> i.getIds().get(0))
			.collect(java.util.stream.Collectors.toList()));
		Map<Integer, GearItem> gear = new java.util.LinkedHashMap<>();
		setups.forEach(s -> gear.putAll(RaidPlan.gear(s)));
		RaidPlan plan = new RaidPlan(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, Collections.emptyList(), results,
			setups, new ArrayList<>(gear.values()), inventory, 28, 0);
		assertEquals(inventory, plan.inventoryFor(start));
		Map<Integer, Integer> bank = BestGearSetupBankView.raidCellsFor(plan);
		List<RaidPlan.InventoryItem> display = RaidPlan.inventoryLayout(inventory);
		for (int i = 0; i < display.size(); i++)
		{ if (display.get(i) != null) { assertEquals(Integer.valueOf(BestGearSetupBankView.inventoryCell(i)), bank.get(display.get(i).getIds().get(0))); } }
		List<RaidPlan.InventoryItem> switched = plan.inventoryFor(magic);
		// Each two-piece switch follows either the row path (vertical) or column path (horizontal).
		List<Integer> switchedIds = RaidPlan.inventoryLayout(switched).stream().map(i -> i == null ? 0 : i.getIds().get(0))
			.collect(java.util.stream.Collectors.toList());
		for (List<Integer> pair : Arrays.asList(Arrays.asList(100, 101), Arrays.asList(400, 401), Arrays.asList(300, 301)))
		{
			int first = switchedIds.indexOf(pair.get(0));
			int second = switchedIds.indexOf(pair.get(1));
			assertTrue(second == first + 4 || (second == first + 1 && first / 4 == second / 4));
		}
		assertEquals(switched.size(), switched.stream().map(i -> i.getIds().get(0)).distinct().count());
		List<RaidPlan.InventoryItem> single = RaidPlan.inventoryForSetup(magic, magicResult, id -> id == ItemID.DIVINE_RUNE_POUCH);
		// Only a spec switch, so no row for spell supplies to fill: they go last.
		assertEquals(Arrays.asList(500, ItemID._4DOSEDIVINECOMBAT, ItemID.DIVINE_RUNE_POUCH, ItemID.BOOK_OF_THE_DEAD),
			single.stream().map(i -> i.getIds().get(0)).collect(java.util.stream.Collectors.toList()));
		assertEquals(single, RaidPlan.inventoryForSetup(magic, Collections.singletonList(combat), id -> id == ItemID.DIVINE_RUNE_POUCH));
		Map<Integer, Integer> singleBank = BestGearSetupBankView.cellsFor(magic, Collections.singletonList(combat),
			id -> id == ItemID.DIVINE_RUNE_POUCH);
		for (int i = 0; i < single.size(); i++)
		{ assertEquals(Integer.valueOf(BestGearSetupBankView.inventoryCell(i)), singleBank.get(single.get(i).getIds().get(0))); }
	}

	@Test public void stopsOnceNoKitCanFitALaterRoomOrBeatTheBound()
	{
		GearItem shared = item(701, Slot.BODY);
		SetupResult start = setup(item(702, Slot.WEAPON), shared, 5);
		SetupResult middle = setup(item(702, Slot.WEAPON), shared, 6);
		// Every setup for the last room needs two new switches.
		SetupResult wide = setup(item(703, Slot.WEAPON), item(704, Slot.BODY), 7);
		List<SearchResults> results = Arrays.asList(result(start), result(middle), result(wide));
		assertNull(plan(results, 1, Long.MAX_VALUE));
		RaidPlan fitted = plan(results, 2, Long.MAX_VALUE);
		assertNotNull(fitted);
		List<RaidEncounter> encounters = fitted.getEncounters();
		double seconds = RaidConsolidator.seconds(fitted.getSetups(), results, encounters);
		// Only kits within the bound are returned; an equal time is kept (rounding slack).
		assertNotNull(RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, encounters, results, 2, Long.MAX_VALUE,
			id -> false, item -> 10, 0, id -> 0, () -> false, percent -> { }, seconds));
		assertNull(RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, encounters, results, 2, Long.MAX_VALUE,
			id -> false, item -> 10, 0, id -> 0, () -> false, percent -> { }, seconds * 0.999));
	}

	@Test public void equallyFastKitsKeepTheHigherRankedSetup()
	{
		GearItem body = item(712, Slot.BODY);
		SetupResult start = setup(item(711, Slot.WEAPON), body, 5);
		SetupResult first = setup(item(713, Slot.WEAPON), body, 6);
		SetupResult second = setup(item(714, Slot.WEAPON), body, 6);
		assertEquals(first, plan(Arrays.asList(result(start), result(first, second)), 28, Long.MAX_VALUE).getSetups().get(1));
		assertEquals(second, plan(Arrays.asList(result(start), result(second, first)), 28, Long.MAX_VALUE).getSetups().get(1));
	}

	@Test public void kitSearchInventoryCountsMatchThePublishedInventory()
	{
		GearItem arrows = item(720, Slot.AMMO); arrows.setRangedStr(40);
		SetupResult start = setup(item(721, Slot.WEAPON), item(722, Slot.BODY), 5);
		SetupResult bow = setup(item(723, Slot.WEAPON), item(722, Slot.BODY), 7); bow.getLoadout().set(Slot.AMMO, arrows);
		SetupResult base = setup(item(724, Slot.WEAPON), item(725, Slot.BODY), 5);
		SpecialPlan special = new SpecialPlan(item(726, Slot.WEAPON), null, null, base.getLoadout().getStyle(), null, null,
			null, base.getDps(), base.getDps(), null, 1, 0, 0);
		SetupResult magic = new SetupResult(CombatClass.MAGIC, base.getLoadout(), base.getDps(), 0, 0,
			Collections.emptySet(), EnumSet.of(SupportSpell.THRALL, SupportSpell.MARK_OF_DARKNESS), special);
		List<SetupResult> setups = Arrays.asList(start, bow, magic);
		List<SearchResults> results = Arrays.asList(result(start), result(bow), result(magic));
		for (java.util.function.IntPredicate owns : Arrays.<java.util.function.IntPredicate>asList(id -> false,
			id -> id == ItemID.BH_RUNE_POUCH, id -> id == ItemID.DIVINE_RUNE_POUCH))
		{
			RaidPlan.Memo memo = new RaidPlan.Memo();
			List<SetupResult> filled = RaidPlan.fill(setups, results, memo);
			assertEquals(arrows, filled.get(0).getLoadout().get(Slot.AMMO));
			List<RaidPlan.InventoryItem> shown = RaidPlan.filledInventory(filled, results, owns, memo);
			List<RaidPlan.InventoryItem> packed = RaidPlan.packedInventory(filled, results, owns, memo);
			assertEquals(firstIds(shown), firstIds(packed));
			assertEquals(shown.size(), packed.size());
			Map<Integer, GearItem> gear = new java.util.LinkedHashMap<>();
			setups.forEach(s -> gear.putAll(RaidPlan.gear(s)));
			long equipment = shown.stream().filter(RaidPlan.InventoryItem::isEquipment).count();
			assertEquals(equipment, RaidPlan.carriedEquipment(gear.keySet(), filled.get(0)));
			assertEquals(RaidPlan.filledMaximumInventory(filled, shown, memo),
				RaidPlan.filledMaximumInventory(filled, gear.keySet(), packed, memo));
		}
	}

	/**
	 * A Vasilias-style group: A is the only setup the other two are quick switches from. B's switch comes with and
	 * without a boost, and keeps the boost only when a full setup after the first room brings that potion.
	 */
	private static RaidPlan switchGroupPlan(boolean firstRoomBoost, boolean laterRoomBoost)
	{
		return switchGroupPlan(firstRoomBoost, laterRoomBoost, BoostTiming.DEFAULT);
	}

	private static RaidPlan switchGroupPlan(boolean firstRoomBoost, boolean laterRoomBoost, BoostTiming timing)
	{
		com.bestgearsetup.data.Potion boost = new com.bestgearsetup.data.Potion();
		boost.setId(ItemID._4DOSE2COMBAT);
		boost.setName("combat potion");
		SetupResult full = wearing(CombatClass.MELEE, 5, 100, 101, 102, 103, 104);
		SetupResult boosted = wearing(CombatClass.RANGED, 6, 200, 201, 102, 103, 104);
		SetupResult plain = wearing(CombatClass.RANGED, 5, 200, 201, 102, 103, 104);
		SetupResult other = wearing(CombatClass.MAGIC, 5, 300, 301, 102, 103, 104);
		Map<DpsResult, List<com.bestgearsetup.data.Potion>> boosts = new java.util.IdentityHashMap<>();
		boosts.put(boosted.getDps(), Collections.singletonList(boost));
		boosts.put(plain.getDps(), Collections.emptyList());
		SearchResults quick = result(boosted, plain).withCandidates(
			Collections.singletonMap(AttackStyle.Type.RANGED, Arrays.asList(boosted, plain)), boosts);
		SetupResult first = wearing(CombatClass.RANGED, 5, 100, 101, 102, 103, 104);
		SetupResult later = wearing(CombatClass.RANGED, 5, 100, 101, 102, 103, 104);
		List<SearchResults> results = Stream.of(
			boosted(first, firstRoomBoost ? boost : null).withPrepotted(),
			result(full), quick, result(other),
			boosted(later, laterRoomBoost ? boost : null)).map(r -> r.withBoostTiming(timing)).collect(Collectors.toList());
		List<RaidEncounter> encounters = new ArrayList<>();
		encounters.add(new RaidEncounter("First room", new MonsterSummary()).withPrepotted());
		for (String form : new String[]{"A", "B", "C"})
		{ encounters.add(new RaidEncounter("Form " + form, new MonsterSummary()).withSwitchGroup("forms", form.equals("A"))); }
		// Known downtime lets the first-room prepot expire before the later full setup needs a fresh dose.
		encounters.add(new RaidEncounter("Later room", new MonsterSummary()).withEnergy(600, false, 1));
		return RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, encounters, results, 28, Long.MAX_VALUE,
			id -> false, item -> 10, 0, id -> 0, () -> false);
	}

	@Test public void aQuickSwitchKeepsItsBoostOnlyWhenALaterFullSetupBringsIt()
	{
		RaidPlan brought = switchGroupPlan(false, true);
		assertEquals(6, brought.getSetups().get(2).getDps().getDps(), 1e-9);
		assertEquals(1, RaidPlanner.fullSetupChoice(brought.getEncounters(), brought.getResults(), brought.getSetups())[0]);
		RaidPlan alone = switchGroupPlan(false, false);
		assertEquals("A switch alone isn't worth bringing a potion for", 5, alone.getSetups().get(2).getDps().getDps(), 1e-9);
		assertTrue(alone.getResults().get(2).potionsForSetup(alone.getSetups().get(2)).isEmpty());
		RaidPlan prepotted = switchGroupPlan(true, false);
		assertEquals("The first room's boost is drunk before entering, not carried", 5,
			prepotted.getSetups().get(2).getDps().getDps(), 1e-9);
		assertTrue(prepotted.getInventory().stream().noneMatch(item -> item.getIds().contains(ItemID._4DOSE2COMBAT)));
	}

	@Test public void boostsSayWhetherTheyArePrepotBroughtOrBoth()
	{
		com.bestgearsetup.data.Potion boost = new com.bestgearsetup.data.Potion();
		boost.setId(ItemID._4DOSE2COMBAT);
		assertEquals(RaidPlan.PotionUse.PREPOT, switchGroupPlan(true, false).potionUse(boost));
		assertEquals(RaidPlan.PotionUse.BROUGHT, switchGroupPlan(false, true).potionUse(boost));
		RaidPlan both = switchGroupPlan(true, true);
		assertEquals(RaidPlan.PotionUse.BOTH, both.potionUse(boost));
		RaidPlan.InventoryItem carried = both.getInventory().stream()
			.filter(item -> item.getGroup() == RaidPlan.InventoryGroup.BOOSTS).findFirst().get();
		assertEquals("The carried potion is also drunk before entering", RaidPlan.PotionUse.BOTH, both.potionUse(carried));
		assertNull(switchGroupPlan(false, false).potionUse(boost));
		com.bestgearsetup.data.Potion overload = new com.bestgearsetup.data.Potion();
		overload.setId(20993);
		overload.setAttributes(Collections.singletonList("xerician"));
		assertEquals(RaidPlan.PotionUse.SUPPLIED, both.potionUse(overload));
		assertNull("Raid supplies aren't tagged", RaidPlan.PotionUse.SUPPLIED.getTag());
	}

	@Test public void divineBottlesAreTaggedAndPrepottedAsDivine()
	{
		BoostTiming divine = new BoostTiming(true, 50, PlayerLevels.maxed());
		com.bestgearsetup.data.Potion boost = new com.bestgearsetup.data.Potion();
		boost.setId(ItemID._4DOSE2COMBAT);
		RaidPlan both = switchGroupPlan(true, true, divine);
		RaidPlan.InventoryItem carried = both.getInventory().stream()
			.filter(item -> item.getGroup() == RaidPlan.InventoryGroup.BOOSTS).findFirst().get();
		assertEquals(Integer.valueOf(ItemID._4DOSEDIVINECOMBAT), carried.getIds().get(0));
		assertEquals(RaidPlan.PotionUse.BOTH, both.potionUse(boost));
		assertEquals(RaidPlan.PotionUse.BOTH, both.potionUse(carried));
		assertEquals(RaidPlan.PotionUse.BOTH, BestGearSetupBankView.raidTagsFor(both).get(ItemID._4DOSEDIVINECOMBAT));
		List<RaidPlan.InventoryItem> prepots = switchGroupPlan(true, false, divine).prepotItems();
		assertEquals(1, prepots.size());
		assertEquals("divine combat potion", prepots.get(0).getName());
		assertEquals(Integer.valueOf(ItemID._1DOSEDIVINECOMBAT), prepots.get(0).getIds().get(0));
		assertTrue("An ordinary bottle still matches", prepots.get(0).getIds().contains(ItemID._4DOSE2COMBAT));
	}

	@Test public void prepotBoostsAreTaggedAndLaidOutInTheBank()
	{
		RaidPlan prepotted = switchGroupPlan(true, false);
		List<RaidPlan.InventoryItem> prepots = prepotted.prepotItems();
		assertEquals(1, prepots.size());
		assertEquals(Integer.valueOf(ItemID._1DOSEDIVINECOMBAT), prepots.get(0).getIds().get(0));
		Map<Integer, Integer> bank = BestGearSetupBankView.raidCellsFor(prepotted);
		assertTrue(RaidPlan.hasEquipmentSwitches(prepotted.getInventory()));
		assertEquals("Prepots sit below the worn equipment", Integer.valueOf(40), bank.get(ItemID._4DOSE2COMBAT));
		assertEquals(RaidPlan.PotionUse.PREPOT, BestGearSetupBankView.raidTagsFor(prepotted).get(ItemID._4DOSE2COMBAT));
		RaidPlan both = switchGroupPlan(true, true);
		assertEquals("A boost carried into the raid is still listed as a prepot", 1, both.prepotItems().size());
		assertEquals("Prepot and carried inventory each get a cell", 2,
			BestGearSetupBankView.raidCellsFor(both, ItemID._4DOSE2COMBAT).size());
		assertEquals(Integer.valueOf(40), BestGearSetupBankView.raidCellsFor(both, ItemID._4DOSE2COMBAT).get(0));
		assertEquals(RaidPlan.PotionUse.BOTH, BestGearSetupBankView.raidTagsFor(both).get(ItemID._4DOSE2COMBAT));
		assertEquals(RaidPlan.PotionUse.BROUGHT,
			BestGearSetupBankView.raidTagsFor(switchGroupPlan(false, true)).get(ItemID._4DOSE2COMBAT));
		assertTrue(BestGearSetupBankView.raidTagsFor(switchGroupPlan(false, false)).isEmpty());
	}

	@Test public void fullSetupChoiceHonorsTheRoleWhenEveryFormCouldBeTheBase()
	{
		List<RaidEncounter> encounters = Arrays.asList(
			new RaidEncounter("Melee", new MonsterSummary()).withSwitchGroup("forms", false),
			new RaidEncounter("Ranged", new MonsterSummary()).withSwitchGroup("forms", true),
			new RaidEncounter("Magic", new MonsterSummary()).withSwitchGroup("forms", false));
		List<SetupResult> setups = Arrays.asList(wearing(CombatClass.MELEE, 5, 100, 101, 102, 103, 104),
			wearing(CombatClass.RANGED, 5, 200, 101, 102, 103, 104),
			wearing(CombatClass.MAGIC, 5, 300, 101, 102, 103, 104));
		List<SearchResults> results = Arrays.asList(result(setups.get(0)), result(setups.get(1)), result(setups.get(2)));
		assertEquals(Collections.singletonList(1), RaidEncounter.fullSetups(new int[]{0, 1, 2}, setups, encounters));
		assertEquals(1, RaidPlanner.fullSetupChoice(encounters, results, setups)[0]);
		// Copies made for style overrides and kit search must retain the role restriction.
		assertFalse(encounters.get(0).withStyleChoice(RaidStyle.AUTO).withPrepotted().withStartable().isFullSwitchSetup());
	}

	@Test public void quickSwitchesShareOneExtraSlotAndPlannerKeepsTheFasterCompatiblePair()
	{
		List<RaidEncounter> encounters = Arrays.asList(
			new RaidEncounter("Melee", new MonsterSummary()).withSwitchGroup("forms", true),
			new RaidEncounter("Ranged", new MonsterSummary()).withSwitchGroup("forms", false),
			new RaidEncounter("Magic", new MonsterSummary()).withSwitchGroup("forms", false));
		SetupResult full = wearing(CombatClass.MELEE, 5, 100, 101, 102, 103, 104);
		SetupResult rangedNeck = wearing(CombatClass.RANGED, 10, 200, 101, 102, 103, 104);
		rangedNeck.getLoadout().set(Slot.NECK, item(800, Slot.NECK));
		SetupResult rangedCape = wearing(CombatClass.RANGED, 9, 200, 101, 102, 103, 104);
		rangedCape.getLoadout().set(Slot.CAPE, item(801, Slot.CAPE));
		SetupResult magicCape = wearing(CombatClass.MAGIC, 10, 300, 101, 102, 103, 104);
		magicCape.getLoadout().set(Slot.CAPE, item(802, Slot.CAPE));
		SetupResult magicNeck = wearing(CombatClass.MAGIC, 1, 300, 101, 102, 103, 104);
		magicNeck.getLoadout().set(Slot.NECK, item(803, Slot.NECK));
		assertTrue(RaidEncounter.fullSetups(new int[]{0, 1, 2}, Arrays.asList(full, rangedNeck, magicCape), encounters).isEmpty());
		assertEquals(Collections.singletonList(0), RaidEncounter.fullSetups(new int[]{0, 1, 2}, Arrays.asList(full, rangedCape, magicCape), encounters));
		List<SearchResults> results = Arrays.asList(result(full), result(rangedNeck, rangedCape), result(magicCape, magicNeck));
		RaidPlan plan = RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, encounters, results, 28, Long.MAX_VALUE,
			id -> true, gear -> 0, 0, id -> 0, () -> false);
		assertNotNull(plan);
		assertEquals(rangedCape, plan.getSetups().get(1));
		assertEquals(magicCape, plan.getSetups().get(2));
		assertEquals(Arrays.asList(full, rangedCape, magicCape), RaidConsolidator.best(results, encounters));
		SetupResult weaponOnly = wearing(CombatClass.MAGIC, 3, 300, 101, 102, 103, 104);
		assertEquals(Collections.singletonList(0), RaidEncounter.fullSetups(new int[]{0, 1, 2}, Arrays.asList(full, rangedCape, weaponOnly), encounters));
	}

	@Test public void aLargerSwitchLimitLetsQuickSwitchesShareMoreSlots()
	{
		List<RaidEncounter> encounters = Arrays.asList(
			new RaidEncounter("Melee", new MonsterSummary()).withSwitchGroup("forms", true).withSwitchLimit(3),
			new RaidEncounter("Ranged", new MonsterSummary()).withSwitchGroup("forms", false).withSwitchLimit(3),
			new RaidEncounter("Magic", new MonsterSummary()).withSwitchGroup("forms", false).withSwitchLimit(3));
		SetupResult full = wearing(CombatClass.MELEE, 5, 100, 101, 102, 103, 104);
		SetupResult rangedNeck = wearing(CombatClass.RANGED, 10, 200, 101, 102, 103, 104);
		rangedNeck.getLoadout().set(Slot.NECK, item(800, Slot.NECK));
		SetupResult rangedCape = wearing(CombatClass.RANGED, 9, 200, 101, 102, 103, 104);
		rangedCape.getLoadout().set(Slot.CAPE, item(801, Slot.CAPE));
		SetupResult magicCape = wearing(CombatClass.MAGIC, 10, 300, 101, 102, 103, 104);
		magicCape.getLoadout().set(Slot.CAPE, item(802, Slot.CAPE));
		// Neck and cape are two shared extra slots: allowed with three swaps, so the faster ranged neck is kept.
		assertEquals(Collections.singletonList(0), RaidEncounter.fullSetups(new int[]{0, 1, 2}, Arrays.asList(full, rangedNeck, magicCape), encounters));
		List<SearchResults> results = Arrays.asList(result(full), result(rangedNeck, rangedCape), result(magicCape));
		RaidPlan plan = RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, encounters, results, 28, Long.MAX_VALUE,
			id -> true, gear -> 0, 0, id -> 0, () -> false);
		assertNotNull(plan);
		assertEquals(rangedNeck, plan.getSetups().get(1));
		assertEquals(Arrays.asList(full, rangedNeck, magicCape), RaidConsolidator.best(results, encounters));
		// A third extra slot would make the switch between the two quick switches four swaps.
		SetupResult magicCapeRing = wearing(CombatClass.MAGIC, 11, 300, 101, 102, 103, 104);
		magicCapeRing.getLoadout().set(Slot.CAPE, item(802, Slot.CAPE));
		magicCapeRing.getLoadout().set(Slot.RING, item(804, Slot.RING));
		assertTrue(RaidEncounter.fullSetups(new int[]{0, 1, 2}, Arrays.asList(full, rangedNeck, magicCapeRing), encounters).isEmpty());
		// One swap is the weapon alone.
		List<RaidEncounter> weaponOnly = new ArrayList<>();
		encounters.forEach(encounter -> weaponOnly.add(encounter.withSwitchLimit(1)));
		assertTrue(RaidEncounter.fullSetups(new int[]{0, 1, 2}, Arrays.asList(full, rangedCape, magicCape), weaponOnly).isEmpty());
		SetupResult rangedWeapon = wearing(CombatClass.RANGED, 4, 200, 101, 102, 103, 104);
		SetupResult magicWeapon = wearing(CombatClass.MAGIC, 3, 300, 101, 102, 103, 104);
		assertEquals(Collections.singletonList(0), RaidEncounter.fullSetups(new int[]{0, 1, 2}, Arrays.asList(full, rangedWeapon, magicWeapon), weaponOnly));
	}

	@Test public void extraSlotChoicesAreTheLargestSetsBesideTheWeapon()
	{
		java.util.Set<Slot> used = EnumSet.of(Slot.WEAPON, Slot.CAPE, Slot.NECK, Slot.RING);
		assertEquals(Collections.singletonList(EnumSet.noneOf(Slot.class)), RaidEncounter.extraSlotChoices(used, 1));
		assertEquals(3, RaidEncounter.extraSlotChoices(used, 2).size());
		assertEquals(3, RaidEncounter.extraSlotChoices(used, 3).size());
		assertEquals(Collections.singletonList(EnumSet.of(Slot.CAPE, Slot.NECK, Slot.RING)), RaidEncounter.extraSlotChoices(used, 6));
		assertEquals(Collections.singletonList(EnumSet.noneOf(Slot.class)),
			RaidEncounter.extraSlotChoices(EnumSet.noneOf(Slot.class), 2));
	}

	@Test public void bossMainStyleFollowsTheFastestKitAcrossAllThreeStyles()
	{
		List<RaidEncounter> encounters = Arrays.asList(
			new RaidEncounter("Melee", new MonsterSummary()).withSwitchGroup("forms", true),
			new RaidEncounter("Ranged", new MonsterSummary()).withSwitchGroup("forms", true),
			new RaidEncounter("Magic", new MonsterSummary()).withSwitchGroup("forms", true));
		CombatClass[] classes = {CombatClass.MELEE, CombatClass.RANGED, CombatClass.MAGIC};
		for (int fastest = 0; fastest < classes.length; fastest++)
		{
			List<List<SetupResult>> candidates = Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
			for (int base = 0; base < classes.length; base++)
			{
				int armour = 1000 + base * 10;
				SetupResult full = wearing(classes[base], base == fastest ? 12 : 1,
					100 + base, armour + 1, armour + 2, armour + 3, armour + 4);
				candidates.get(base).add(full);
				for (int form = 0; form < classes.length; form++)
				{
					if (form == base) { continue; }
					SetupResult quick = wearing(classes[form], 8, 100 + form, armour + 1, armour + 2, armour + 3, armour + 4);
					candidates.get(form).add(quickFrom(quick, full));
				}
			}
			List<SearchResults> results = new ArrayList<>();
			for (List<SetupResult> options : candidates) { results.add(result(options.toArray(new SetupResult[0]))); }
			RaidPlan plan = RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, encounters, results, 28, Long.MAX_VALUE,
				id -> true, gear -> 0, 0, id -> 0, () -> false);
			assertNotNull(plan);
			assertEquals(classes[fastest].toString(), fastest, RaidPlanner.fullSetupChoice(encounters, results, plan.getSetups())[0]);
			assertEquals(plan.getSetups(), RaidConsolidator.best(results, encounters));
		}
	}

	private static SetupResult quickFrom(SetupResult quick, SetupResult full)
	{
		return new SetupResult(quick.getCombatClass(), quick.getLoadout(), quick.getDps(), quick.getBuyCost(),
			quick.getUnpricedItems(), quick.getFilledSlots(), quick.getSupportSpells(), quick.getSpecial(), full.getLoadout());
	}

	private static SearchResults boosted(SetupResult setup, com.bestgearsetup.data.Potion boost)
	{
		Monster monster = new Monster();
		monster.setName("test boss");
		return new SearchResults(monster, 100, Collections.singletonMap(AttackStyle.Type.RANGED, Collections.singletonList(setup)),
			Collections.emptyList(), Collections.emptyMap(), boost == null ? Collections.emptyMap()
				: Collections.singletonMap(setup.getCombatClass(), Collections.singletonList(boost)), false);
	}

	private static SetupResult wearing(CombatClass cls, double dps, int weapon, int body, int legs, int head, int feet)
	{
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, item(weapon, Slot.WEAPON)); loadout.set(Slot.BODY, item(body, Slot.BODY));
		loadout.set(Slot.LEGS, item(legs, Slot.LEGS)); loadout.set(Slot.HEAD, item(head, Slot.HEAD));
		loadout.set(Slot.FEET, item(feet, Slot.FEET));
		loadout.setStyle(AttackStyle.parse("pound,crush,aggressive"));
		return new SetupResult(cls, loadout, new DpsResult(dps, 0, 1, 4, dps * 2.4, null), 0);
	}

	private static java.util.Set<Integer> firstIds(List<RaidPlan.InventoryItem> items)
	{
		return items.stream().map(i -> i.getIds().get(0)).collect(java.util.stream.Collectors.toSet());
	}

	private static RaidPlan plan(List<SearchResults> results, int limit, long budget)
	{
		List<RaidEncounter> encounters = new ArrayList<>();
		for (int i = 0; i < results.size(); i++) { encounters.add(new RaidEncounter("Boss " + i, new MonsterSummary())); }
		return RaidPlanner.select(RaidPlanType.TOB, RaidSpellbook.ARCEUUS, encounters, results, limit, budget, id -> false, item -> 10, 0, id -> 0, () -> false);
	}

	private static SearchResults result(SetupResult... setups)
	{
		Monster monster = new Monster();
		monster.setName("test boss");
		return new SearchResults(monster, 100, setups.length == 0 ? Collections.emptyMap()
			: Collections.singletonMap(AttackStyle.Type.CRUSH, Arrays.asList(setups)), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), false);
	}

	private static GearItem item(int id, Slot slot)
	{
		GearItem item = new GearItem();
		item.setId(id); item.setName("item " + id); item.setSlot(slot);
		return item;
	}

	/** No max hit leaves the hit spread unknown, so each fight takes exactly HP over DPS (no overkill). */
	private static SetupResult setup(GearItem weapon, GearItem body, double dps)
	{
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, weapon); loadout.set(Slot.BODY, body);
		loadout.setStyle(AttackStyle.parse("pound,crush,aggressive"));
		return new SetupResult(CombatClass.MELEE, loadout, new DpsResult(dps, 0, 1, 4, dps * 2.4, null), 0);
	}
}
