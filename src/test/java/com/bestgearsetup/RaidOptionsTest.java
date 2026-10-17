package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.calc.RaidLoadout;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GameDataLoader;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;

public class RaidOptionsTest
{
	private static final EnumSet<AttackStyle.Type> DISTANCE = EnumSet.of(AttackStyle.Type.RANGED, AttackStyle.Type.ATLATL,
		AttackStyle.Type.MAGIC);
	private static GameData data;

	@BeforeClass
	public static void load() throws Exception
	{
		data = new GameDataLoader(new Gson()).loadGameData();
	}

	private static List<RaidEncounter> encounters(RaidPlanType type, RaidOptions options)
	{
		return type.encounters(data, RaidScaling.Settings.SOLO, options);
	}

	private static RaidEncounter find(List<RaidEncounter> encounters, String label)
	{
		return encounters.stream().filter(e -> e.getLabel().equals(label)).findFirst()
			.orElseThrow(() -> new AssertionError("Missing " + label));
	}

	/** Boss groups in selector order, as the raid view lists them. */
	private static List<String> bosses(List<RaidEncounter> encounters)
	{
		return encounters.stream().map(RaidEncounter::getBossName).distinct().collect(Collectors.toList());
	}

	private static void assertInOrder(List<String> actual, String... expected)
	{
		List<String> filtered = new ArrayList<>(actual);
		filtered.retainAll(Arrays.asList(expected));
		assertEquals(Arrays.asList(expected), filtered);
	}

	@Test
	public void tobRoomsFollowTheRaid()
	{
		List<String> bosses = bosses(encounters(RaidPlanType.TOB, RaidOptions.DEFAULT));
		assertInOrder(bosses, "The Maiden Of Sugadinti", "Pestilent Bloat", "Nylocas Vasilias", "Sotetseg", "Xarpus", "Verzik Vitur");
		List<String> labels = encounters(RaidPlanType.TOB, RaidOptions.DEFAULT).stream().map(RaidEncounter::getLabel)
			.collect(Collectors.toList());
		assertInOrder(labels, "Maiden", "Bloat", "Nylocas: Melee", "Sotetseg", "Xarpus", "Verzik P1", "Verzik P2", "Verzik P3");
	}

	@Test
	public void coxChallengeModeFollowsTheFixedRoomOrder()
	{
		List<RaidEncounter> cox = encounters(RaidPlanType.COX_CM, RaidOptions.DEFAULT);
		assertEquals("tekton (normal)", cox.get(0).getTarget().getName());
		assertTrue(cox.get(cox.size() - 1).getTarget().getName().startsWith("great olm"));
		assertEquals(Arrays.asList("Tekton", "Scavenger Beast", "Ice Demon", "Lizardman Shaman", "Vanguard",
			"Vespula", "Abyssal Portal", "Deathly Mage", "Deathly Ranger", "Guardian", "Vasa Nistirio",
			"Glowing Crystal", "Skeletal Mystic", "Muttadile", "Great Olm"), bosses(cox));
		assertEquals(80, find(cox, "Tightrope Mage").getRestBefore(), 0);
		assertEquals(0, find(cox, "Tightrope Ranger").getRestBefore(), 0);
	}

	@Test
	public void toaFollowsTheSelectedPathOrderWithTheWardensLast()
	{
		List<String> baBa = bosses(encounters(RaidPlanType.TOA, RaidOptions.DEFAULT));
		assertInOrder(baBa, "Ba-Ba", "Kephri", "Akkha", "Zebak", "Obelisk");
		// The Apmeken puzzle's baboons come before Ba-Ba.
		assertEquals("baboon brawler (level 56)", encounters(RaidPlanType.TOA, RaidOptions.DEFAULT).get(0).getTarget().getName());
		RaidOptions zebakFirst = RaidOptions.builder().toaPathOrder(RaidOptions.ToaPathOrder.ZEBAK_FIRST).build();
		List<RaidEncounter> zebak = encounters(RaidPlanType.TOA, zebakFirst);
		assertEquals("zebak (normal)", zebak.get(0).getTarget().getName());
		assertInOrder(bosses(zebak), "Zebak", "Kephri", "Akkha", "Ba-Ba", "Obelisk");
		assertTrue(zebak.get(zebak.size() - 1).getTarget().getName().contains("warden"));
	}

	@Test
	public void autoPathOrderComparesBothOrdersAndListsBaBaFirst()
	{
		assertEquals(RaidOptions.ToaPathOrder.BA_BA_FIRST, RaidOptions.DEFAULT.getToaPathOrder());
		assertEquals(Arrays.asList(RaidOptions.ToaPathOrder.BA_BA_FIRST, RaidOptions.ToaPathOrder.ZEBAK_FIRST),
			RaidOptions.ToaPathOrder.AUTO.candidates());
		assertEquals(Collections.singletonList(RaidOptions.ToaPathOrder.ZEBAK_FIRST), RaidOptions.ToaPathOrder.ZEBAK_FIRST.candidates());
		RaidOptions auto = RaidOptions.builder().toaPathOrder(RaidOptions.ToaPathOrder.AUTO).build();
		assertEquals("baboon brawler (level 56)", encounters(RaidPlanType.TOA, auto).get(0).getTarget().getName());
	}

	@Test
	public void coxOverloadsStartAfterTekton()
	{
		for (RaidEncounter encounter : encounters(RaidPlanType.COX_CM, RaidOptions.DEFAULT))
		{
			boolean tekton = encounter.getTarget().getName().startsWith("tekton");
			assertEquals(encounter.getLabel(), !tekton, encounter.isRaidSupplies());
		}
	}

	@Test
	public void toaSmellingSaltsStartAfterTheFirstTwoPathsInEitherOrder()
	{
		List<RaidEncounter> baBa = encounters(RaidPlanType.TOA, RaidOptions.DEFAULT);
		for (String label : new String[]{"Baboon Brawlers", "Ba-Ba", "Kephri Shield", "Kephri", "Spitting Scarab"})
		{ assertFalse(label, find(baBa, label).isRaidSupplies()); }
		for (String label : new String[]{"Akkha Shadows", "Zebak", "Zebak Enraged", "Obelisk", "Tumeken P3"})
		{ assertTrue(label, find(baBa, label).isRaidSupplies()); }
		assertTrue(baBa.stream().filter(e -> e.getBossName().equals("Akkha")).allMatch(RaidEncounter::isRaidSupplies));

		List<RaidEncounter> zebak = encounters(RaidPlanType.TOA,
			RaidOptions.builder().toaPathOrder(RaidOptions.ToaPathOrder.ZEBAK_FIRST).build());
		for (String label : new String[]{"Zebak", "Zebak Enraged", "Kephri Shield", "Kephri", "Arcane Scarab"})
		{ assertFalse(label, find(zebak, label).isRaidSupplies()); }
		for (String label : new String[]{"Akkha Shadows", "Baboon Brawlers", "Ba-Ba", "Obelisk", "Elidinis P2: Magic"})
		{ assertTrue(label, find(zebak, label).isRaidSupplies()); }
	}

	@Test
	public void tobAssumesNoRaidBoosts()
	{
		assertTrue(encounters(RaidPlanType.TOB, RaidOptions.DEFAULT).stream().noneMatch(RaidEncounter::isRaidSupplies));
	}

	@Test
	public void bossesFoughtFromDistanceDefaultToRangedOrMagic()
	{
		RaidEncounter mystics = find(encounters(RaidPlanType.COX_CM, RaidOptions.DEFAULT), "Mystics");
		RaidEncounter large = find(encounters(RaidPlanType.COX_CM, RaidOptions.DEFAULT), "Large Muttadile");
		RaidEncounter zebak = find(encounters(RaidPlanType.TOA, RaidOptions.DEFAULT), "Zebak");
		RaidEncounter enraged = find(encounters(RaidPlanType.TOA, RaidOptions.DEFAULT), "Zebak Enraged");
		for (RaidEncounter encounter : Arrays.asList(mystics, large, zebak, enraged))
		{
			assertEquals(encounter.getLabel(), DISTANCE, encounter.getStyles());
			assertEquals(RaidStyle.DISTANCE, encounter.getPreferredStyle());
			assertEquals(RaidStyle.AUTO, encounter.getStyleChoice());
			assertTrue(encounter.getStyleChoices().contains(RaidStyle.MELEE));
		}
		assertTrue(find(encounters(RaidPlanType.COX_CM, RaidOptions.DEFAULT), "Tekton").getStyles().isEmpty());
		assertTrue(find(encounters(RaidPlanType.COX_CM, RaidOptions.DEFAULT), "Small Muttadile").getStyles().isEmpty());
	}

	@Test
	public void savedStyleChoicesApplyToEveryPhaseOfTheFightAndRoundTrip()
	{
		String saved = RaidOptions.withStyle("", RaidPlanType.TOA, "Zebak", RaidStyle.MAGIC);
		saved = RaidOptions.withStyle(saved, RaidPlanType.COX_CM, "Mystics", RaidStyle.MELEE);
		saved = RaidOptions.withStyle(saved, RaidPlanType.COX_CM, "Vanguard: Melee", RaidStyle.RANGED);
		RaidOptions options = RaidOptions.builder().styles(RaidOptions.parse(saved)).build();
		assertEquals(RaidStyle.MAGIC, options.style(RaidPlanType.TOA, "Zebak"));
		assertEquals(RaidStyle.AUTO, options.style(RaidPlanType.TOB, "Zebak"));
		List<RaidEncounter> toa = encounters(RaidPlanType.TOA, options);
		assertEquals(EnumSet.of(AttackStyle.Type.MAGIC), find(toa, "Zebak").getStyles());
		assertEquals(EnumSet.of(AttackStyle.Type.MAGIC), find(toa, "Zebak Enraged").getStyles());
		assertEquals(RaidStyle.MAGIC, find(toa, "Zebak Enraged").getDisplayedStyleChoice());
		RaidEncounter mystics = find(encounters(RaidPlanType.COX_CM, options), "Mystics");
		assertEquals(EnumSet.of(AttackStyle.Type.STAB, AttackStyle.Type.SLASH, AttackStyle.Type.CRUSH), mystics.getStyles());
		// A style the target can't be damaged by keeps Auto for that phase.
		RaidEncounter vanguard = find(encounters(RaidPlanType.COX_CM, options), "Vanguard: Melee");
		assertEquals(EnumSet.of(AttackStyle.Type.MAGIC), vanguard.getStyles());
		assertTrue(vanguard.isStyleFallback());
		assertEquals(Collections.singletonList(RaidStyle.AUTO), vanguard.getStyleChoices());
		assertEquals(RaidStyle.AUTO, vanguard.getDisplayedStyleChoice());
		// Auto removes the saved entry; unknown entries are ignored.
		assertEquals(RaidOptions.withStyle(RaidOptions.withStyle("", RaidPlanType.COX_CM, "Mystics", RaidStyle.MELEE),
			RaidPlanType.TOA, "Zebak", RaidStyle.MAGIC), RaidOptions.withStyle(saved, RaidPlanType.COX_CM, "Vanguard: Melee", RaidStyle.AUTO));
		assertEquals(Collections.singletonMap("TOA|Zebak", RaidStyle.MAGIC), RaidOptions.parse("TOA|Zebak=MAGIC;bad;TOB|Maiden=NOPE"));
		assertTrue(RaidOptions.hasStyles(saved, RaidPlanType.COX_CM));
		assertFalse(RaidOptions.hasStyles(saved, RaidPlanType.TOB));
		assertEquals("TOA|Zebak=MAGIC", RaidOptions.withoutStyles(saved, RaidPlanType.COX_CM));
	}

	@Test
	public void styleChoicesLeaveOutStylesTheTargetIsImmuneTo()
	{
		List<RaidEncounter> cox = encounters(RaidPlanType.COX_CM, RaidOptions.DEFAULT);
		assertFalse(find(cox, "Tekton").getStyleChoices().contains(RaidStyle.RANGED));
		assertTrue(find(cox, "Tekton").getStyleChoices().containsAll(Arrays.asList(RaidStyle.MELEE, RaidStyle.MAGIC)));
		assertFalse(find(cox, "Vasa Crystal").getStyleChoices().contains(RaidStyle.RANGED));
		assertEquals(Collections.singletonList(RaidStyle.AUTO), find(cox, "Guardians").getStyleChoices());
		assertFalse(find(cox, "Vespula").getStyleChoices().contains(RaidStyle.MELEE));
		assertFalse(find(cox, "Vespula Portal").getStyleChoices().contains(RaidStyle.MELEE));
		List<RaidEncounter> toa = encounters(RaidPlanType.TOA, RaidOptions.DEFAULT);
		assertFalse(find(toa, "Akkha: Immune To Melee").getStyleChoices().contains(RaidStyle.MELEE));
		assertEquals(Collections.singletonList(RaidStyle.AUTO), find(toa, "Akkha: Final Stand (Melee Only)").getStyleChoices());

		// One choice for Akkha: phases immune to it keep Auto, the others use it.
		RaidOptions melee = RaidOptions.builder().styles(RaidOptions.parse("TOA|Akkha=MELEE")).build();
		List<RaidEncounter> akkha = encounters(RaidPlanType.TOA, melee);
		assertTrue(find(akkha, "Akkha: Immune To Melee").isStyleFallback());
		assertTrue(find(akkha, "Akkha: Immune To Melee").getStyles().isEmpty());
		assertEquals(EnumSet.of(AttackStyle.Type.STAB, AttackStyle.Type.SLASH, AttackStyle.Type.CRUSH),
			find(akkha, "Akkha: Immune To Ranged").getStyles());
		assertFalse(find(akkha, "Akkha: Immune To Ranged").isStyleFallback());
	}

	@Test
	public void onlyPhasesOfOneFightShareAStyleChoice()
	{
		List<RaidEncounter> cox = encounters(RaidPlanType.COX_CM, RaidOptions.DEFAULT);
		assertEquals("Tekton", find(cox, "Tekton Enraged").getStyleKey());
		List<RaidEncounter> toa = encounters(RaidPlanType.TOA, RaidOptions.DEFAULT);
		assertEquals("Zebak", find(toa, "Zebak Enraged").getStyleKey());
		assertEquals("Kephri", find(toa, "Kephri Shield").getStyleKey());
		assertEquals("Akkha", find(toa, "Akkha: Final Stand (Melee Only)").getStyleKey());
		assertEquals("Akkha Shadows", find(toa, "Akkha Shadows").getStyleKey());
		assertEquals("Tumeken P3", find(toa, "Tumeken Core").getStyleKey());

		// Olm's parts are separate fights: ranged on the head leaves both hands on Auto.
		List<RaidEncounter> olm = encounters(RaidPlanType.COX_CM,
			RaidOptions.builder().styles(RaidOptions.parse("COX_CM|Olm Head=RANGED")).build());
		assertEquals(EnumSet.of(AttackStyle.Type.RANGED, AttackStyle.Type.ATLATL), find(olm, "Olm Head").getStyles());
		assertEquals(RaidStyle.AUTO, find(olm, "Olm Melee Hand").getStyleChoice());
		assertEquals(RaidStyle.AUTO, find(olm, "Olm Mage Hand").getStyleChoice());
		assertTrue(find(olm, "Olm Melee Hand").getStyles().isEmpty());

		// Melee on the small Muttadile keeps the large one's distance default.
		List<RaidEncounter> mutta = encounters(RaidPlanType.COX_CM,
			RaidOptions.builder().styles(RaidOptions.parse("COX_CM|Small Muttadile=MELEE")).build());
		assertEquals(EnumSet.of(AttackStyle.Type.STAB, AttackStyle.Type.SLASH, AttackStyle.Type.CRUSH),
			find(mutta, "Small Muttadile").getStyles());
		assertEquals(DISTANCE, find(mutta, "Large Muttadile").getStyles());

		// Melee on Verzik P3 leaves P1 (Dawnbringer) and P2 on Auto.
		List<RaidEncounter> verzik = encounters(RaidPlanType.TOB,
			RaidOptions.builder().styles(RaidOptions.parse("TOB|Verzik P3=MELEE")).build());
		assertEquals(RaidStyle.MELEE, find(verzik, "Verzik P3").getStyleChoice());
		assertTrue(find(verzik, "Verzik P1").getStyles().isEmpty());
		assertTrue(find(verzik, "Verzik P2").getStyles().isEmpty());
	}

	@Test
	public void aSavedChoiceThatMatchesAutoIsShownAsAuto()
	{
		RaidOptions magic = RaidOptions.builder().styles(RaidOptions.parse("TOB|Maiden=MAGIC")).build();
		RaidEncounter dps = find(encounters(RaidPlanType.TOB, magic), "Maiden");
		assertEquals(RaidStyle.MAGIC, dps.getDisplayedStyleChoice());
		RaidOptions south = RaidOptions.builder().tobRole(RaidOptions.TobRole.SOUTH_FREEZE)
			.styles(RaidOptions.parse("TOB|Maiden Nylocas: Freeze=MAGIC")).build();
		RaidEncounter clump = find(encounters(RaidPlanType.TOB, south), "Maiden Nylocas: Freeze");
		assertFalse(clump.getStyleChoices().contains(RaidStyle.MAGIC));
		assertEquals(RaidStyle.AUTO, clump.getDisplayedStyleChoice());
		assertEquals(EnumSet.of(AttackStyle.Type.MAGIC), clump.getStyles());
	}

	@Test
	public void freezersFreezeMaidensNylocasWhileEveryoneElseKillsThem()
	{
		for (RaidOptions.TobRole role : RaidOptions.TobRole.values())
		{
			List<RaidEncounter> plan = encounters(RaidPlanType.TOB, RaidOptions.builder().tobRole(role)
				.styles(RaidOptions.parse("TOB|Maiden Nylocas: Freeze=MELEE;TOB|Maiden Nylocas: Chins=MELEE")).build());
			assertEquals("Maiden", plan.get(0).getLabel());
			assertTrue(find(plan, "Maiden").getStyles().isEmpty());
			List<String> nylocas = plan.stream().map(RaidEncounter::getLabel).filter(l -> l.startsWith("Maiden Nylocas"))
				.collect(Collectors.toList());
			// North freezes, then helps with the unclumped nylocas; Range DPS helps until the clump forms, then chins it.
			List<String> expected = role == RaidOptions.TobRole.SOUTH_FREEZE ? Collections.singletonList("Maiden Nylocas: Freeze")
				: role == RaidOptions.TobRole.NORTH_FREEZE ? Arrays.asList("Maiden Nylocas: Freeze", "Maiden Nylocas")
				: role == RaidOptions.TobRole.RANGED_DPS ? Arrays.asList("Maiden Nylocas", "Maiden Nylocas: Chins")
				: Collections.singletonList("Maiden Nylocas");
			assertEquals(role.name(), expected, nylocas);
			assertEquals(nylocas, plan.subList(1, 1 + nylocas.size()).stream().map(RaidEncounter::getLabel).collect(Collectors.toList()));
			if (role.freezes())
			{
				RaidEncounter freeze = find(plan, "Maiden Nylocas: Freeze");
				assertEquals(EnumSet.of(AttackStyle.Type.MAGIC), freeze.getStyles());
				assertEquals(RaidLoadout.ICE_BARRAGE, freeze.getLoadoutRequirement());
				assertTrue("Conflicting saved styles cannot bypass the role", freeze.isStyleFallback());
				assertTrue(freeze.isFreeze());
				// The freeze is only ever a switch from the Maiden setup, which may swap every slot to land.
				assertEquals(RaidPlanType.MAIDEN_FREEZE, freeze.getSwitchGroup());
				assertEquals(RaidPlanType.MAIDEN_FREEZE, plan.get(0).getSwitchGroup());
				assertFalse(freeze.isFullSwitchSetup());
				assertTrue(plan.get(0).isFullSwitchSetup());
				assertFalse("Nobody enters wearing the freeze switch", freeze.isStartable());
				assertTrue(plan.get(0).isStartable());
				assertEquals(RaidEncounter.FREEZE_SWITCH_LIMIT, freeze.getSwitchLimit());
				assertEquals(RaidEncounter.FREEZE_SWITCH_LIMIT, plan.get(0).getSwitchLimit());
			}
			else
			{
				assertNull(plan.get(0).getSwitchGroup());
				assertFalse(plan.stream().anyMatch(RaidEncounter::isFreeze));
			}
			if (role == RaidOptions.TobRole.RANGED_DPS)
			{
				RaidEncounter chins = find(plan, "Maiden Nylocas: Chins");
				assertEquals(EnumSet.of(AttackStyle.Type.RANGED), chins.getStyles());
				assertEquals(RaidLoadout.CHINCHOMPAS, chins.getLoadoutRequirement());
				assertTrue(chins.isStyleFallback());
			}
			if (role != RaidOptions.TobRole.SOUTH_FREEZE)
			{
				RaidEncounter helping = find(plan, "Maiden Nylocas");
				if (role == RaidOptions.TobRole.RANGED_DPS)
				{ assertEquals(RaidStyle.RANGED.types(), helping.getStyles()); }
				else { assertTrue("Other helpers may use any style", helping.getStyles().isEmpty()); }
				assertEquals(RaidLoadout.ANY, helping.getLoadoutRequirement());
			}
		}
	}

	@Test
	public void theVasiliasFormsShareOneSwitchGroupAndTheFirstRoomIsPrepotted()
	{
		for (RaidOptions.TobRole role : RaidOptions.TobRole.values())
		{
			List<RaidEncounter> plan = encounters(RaidPlanType.TOB, RaidOptions.builder().tobRole(role).build());
			List<int[]> groups = RaidEncounter.switchGroups(plan);
			// A freezer's Maiden setup is also switched from, to freeze.
			assertEquals(role.freezes() ? 2 : 1, groups.size());
			assertEquals(Arrays.asList("Nylocas: Melee", "Nylocas: Ranged", "Nylocas: Magic"),
				Arrays.stream(groups.get(groups.size() - 1)).mapToObj(index -> plan.get(index).getLabel()).collect(Collectors.toList()));
			assertEquals(CombatClass.MAGIC, find(plan, "Nylocas: Magic").getFixedClass());
			assertEquals(CombatClass.RANGED, find(plan, "Nylocas: Ranged").getFixedClass());
			assertEquals(CombatClass.MELEE, find(plan, "Nylocas: Melee").getFixedClass());
			// Maiden and her nylocas are the first room, fought on boosts drunk before entering.
			for (RaidEncounter encounter : plan)
			{
				boolean maiden = encounter.getLabel().startsWith("Maiden");
				assertEquals(role + " " + encounter, maiden, encounter.isPrepotted());
			}
		}
		assertTrue(RaidEncounter.switchGroups(encounters(RaidPlanType.COX_CM, RaidOptions.DEFAULT)).isEmpty());
	}

	@Test
	public void theBaboonsShareOneSwitchGroupWithTheChosenSwapLimit()
	{
		for (RaidOptions.ToaPathOrder order : RaidOptions.ToaPathOrder.values())
		{
			List<RaidEncounter> plan = encounters(RaidPlanType.TOA, RaidOptions.builder().toaPathOrder(order).switchLimit(4).build());
			List<int[]> groups = RaidEncounter.switchGroups(plan);
			assertEquals(1, groups.size());
			assertEquals(Arrays.asList("Baboon Brawlers", "Baboon Throwers", "Baboon Mages", "Baboon Shamans"),
				Arrays.stream(groups.get(0)).mapToObj(index -> plan.get(index).getLabel()).collect(Collectors.toList()));
			Arrays.stream(groups.get(0)).forEach(index -> assertTrue(plan.get(index).isFullSwitchSetup()));
			assertTrue(plan.stream().allMatch(encounter -> encounter.getSwitchLimit() == 4));
			assertNull("Ba-Ba is her own fight", find(plan, "Ba-Ba").getSwitchGroup());
		}
		assertTrue(encounters(RaidPlanType.TOA, RaidOptions.DEFAULT).stream()
			.allMatch(encounter -> encounter.getSwitchLimit() == RaidEncounter.DEFAULT_SWITCH_LIMIT));
		assertEquals(RaidEncounter.MAX_SWITCH_LIMIT, encounters(RaidPlanType.TOB, RaidOptions.builder().switchLimit(99).build())
			.get(0).getSwitchLimit());
	}

	@Test
	public void onlyKephriIsBoostedOnHerPath()
	{
		for (RaidEncounter encounter : encounters(RaidPlanType.TOA, RaidOptions.DEFAULT))
		{
			boolean scarab = encounter.getLabel().toLowerCase(java.util.Locale.ROOT).endsWith("scarab");
			assertEquals(encounter.toString(), scarab, encounter.isUnboosted());
		}
		assertTrue(encounters(RaidPlanType.COX_CM, RaidOptions.DEFAULT).stream().noneMatch(RaidEncounter::isUnboosted));
		assertTrue(encounters(RaidPlanType.TOB, RaidOptions.DEFAULT).stream().noneMatch(RaidEncounter::isUnboosted));
	}

	@Test
	public void tektonAndTheFirstToaPathArePrepotted()
	{
		List<RaidEncounter> cox = encounters(RaidPlanType.COX_CM, RaidOptions.DEFAULT);
		for (RaidEncounter encounter : cox)
		{
			assertEquals(encounter.toString(), encounter.getLabel().startsWith("Tekton"), encounter.isPrepotted());
		}
		for (RaidOptions.ToaPathOrder order : RaidOptions.ToaPathOrder.values())
		{
			List<RaidEncounter> toa = encounters(RaidPlanType.TOA, RaidOptions.builder().toaPathOrder(order).build());
			boolean zebakFirst = order == RaidOptions.ToaPathOrder.ZEBAK_FIRST;
			for (RaidEncounter encounter : toa)
			{
				boolean firstPath = zebakFirst ? encounter.getLabel().startsWith("Zebak")
					: encounter.getLabel().startsWith("Baboon") || encounter.getLabel().equals("Ba-Ba");
				assertEquals(order + " " + encounter, firstPath, encounter.isPrepotted());
				// The second path is before smelling salts too, but its boosts are carried.
				assertFalse(encounter.isPrepotted() && encounter.isRaidSupplies());
			}
		}
	}

	@Test
	public void tobRolesKeepTheirOwnWavesAndNorthChangesWithPartySize()
	{
		for (int party : new int[]{3, 4, 5})
		{
			for (RaidOptions.TobRole role : RaidOptions.TobRole.values())
			{
				List<RaidEncounter> plan = RaidPlanType.TOB.encounters(data, RaidScaling.Settings.builder().partySize(party).build(),
					RaidOptions.builder().tobRole(role).build());
				String colour = role == RaidOptions.TobRole.SOUTH_FREEZE || role == RaidOptions.TobRole.NORTH_FREEZE && party == 5
					? "Magic" : role == RaidOptions.TobRole.RANGED_DPS ? "Ranged" : "Melee";
				List<String> waves = plan.stream().map(RaidEncounter::getLabel).filter(l -> l.startsWith("Nylocas Waves"))
					.collect(Collectors.toList());
				// The small and big nylocas of the role's own colour.
				assertEquals(role + " in " + party, Arrays.asList("Nylocas Waves: " + colour, "Nylocas Waves: " + colour + " (Big)"), waves);
				for (String wave : waves)
				{
					assertEquals(colour.equals("Magic") ? RaidStyle.MAGIC.types() : colour.equals("Ranged")
						? RaidStyle.RANGED.types() : RaidStyle.MELEE.types(), find(plan, wave).getStyles());
				}
				// Every role still plans each Vasilias form, with that form's style.
				assertEquals(RaidStyle.MAGIC.types(), find(plan, "Nylocas: Magic").getStyles());
				assertEquals(RaidStyle.RANGED.types(), find(plan, "Nylocas: Ranged").getStyles());
				assertEquals(RaidStyle.MELEE.types(), find(plan, "Nylocas: Melee").getStyles());
				List<RaidEncounter> full = plan.stream().filter(e -> e.getSwitchGroup() != null && e.isFullSwitchSetup())
					.collect(Collectors.toList());
				List<String> bases = new ArrayList<>(Arrays.asList("Nylocas: Melee", "Nylocas: Ranged", "Nylocas: Magic"));
				// A freezer switches from the Maiden setup to freeze.
				if (role.freezes()) { bases.add(0, "Maiden"); }
				assertEquals(role + " in " + party, bases, full.stream().map(RaidEncounter::getLabel).collect(Collectors.toList()));
			}
		}
	}
}
