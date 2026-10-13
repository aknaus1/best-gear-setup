package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.RaidScaling;
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
	public void coxChallengeModeStartsAtTektonAndEndsAtOlm()
	{
		List<RaidEncounter> cox = encounters(RaidPlanType.COX_CM, RaidOptions.DEFAULT);
		assertEquals("tekton (normal)", cox.get(0).getTarget().getName());
		assertTrue(cox.get(cox.size() - 1).getTarget().getName().startsWith("great olm"));
		assertInOrder(bosses(cox), "Tekton", "Muttadile", "Guardian", "Vespula", "Lizardman Shaman", "Vasa Nistirio",
			"Vanguard", "Skeletal Mystic", "Great Olm");
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
		RaidEncounter freezer = find(encounters(RaidPlanType.TOB, magic.toBuilder().maidenRole(RaidOptions.MaidenRole.FREEZER).build()), "Maiden");
		assertFalse(freezer.getStyleChoices().contains(RaidStyle.MAGIC));
		assertEquals(RaidStyle.AUTO, freezer.getDisplayedStyleChoice());
		assertEquals(EnumSet.of(AttackStyle.Type.MAGIC), freezer.getStyles());
	}

	@Test
	public void maidenRolesChangeTheMaidenEncounters()
	{
		List<RaidEncounter> both = encounters(RaidPlanType.TOB, RaidOptions.DEFAULT);
		assertTrue(find(both, "Maiden").getStyles().isEmpty());
		assertEquals(EnumSet.of(AttackStyle.Type.MAGIC), find(both, "Maiden Nylocas").getStyles());

		List<RaidEncounter> dps = encounters(RaidPlanType.TOB, RaidOptions.builder().maidenRole(RaidOptions.MaidenRole.DPS).build());
		assertTrue(dps.stream().noneMatch(e -> e.getLabel().equals("Maiden Nylocas")));
		assertTrue(find(dps, "Maiden").getStyles().isEmpty());

		List<RaidEncounter> freezer = encounters(RaidPlanType.TOB, RaidOptions.builder().maidenRole(RaidOptions.MaidenRole.FREEZER).build());
		assertEquals("Maiden", freezer.get(0).getLabel());
		assertEquals(EnumSet.of(AttackStyle.Type.MAGIC), freezer.get(0).getStyles());
		assertEquals(EnumSet.of(AttackStyle.Type.MAGIC), find(freezer, "Maiden Nylocas").getStyles());
	}

	@Test
	public void nylocasRolesKeepOnlyTheirOwnWaves()
	{
		List<RaidEncounter> all = encounters(RaidPlanType.TOB, RaidOptions.DEFAULT);
		assertEquals(3, all.stream().filter(e -> e.getLabel().startsWith("Nylocas Waves")).count());
		assertEquals(EnumSet.of(AttackStyle.Type.RANGED, AttackStyle.Type.ATLATL), find(all, "Nylocas Waves: Ranged").getStyles());
		for (RaidOptions.NylocasRole role : new RaidOptions.NylocasRole[]{RaidOptions.NylocasRole.MELEE,
			RaidOptions.NylocasRole.RANGED, RaidOptions.NylocasRole.MAGIC})
		{
			List<RaidEncounter> encounters = encounters(RaidPlanType.TOB, RaidOptions.builder().nylocasRole(role).build());
			List<String> waves = encounters.stream().map(RaidEncounter::getLabel).filter(l -> l.startsWith("Nylocas Waves"))
				.collect(Collectors.toList());
			assertEquals(role.name(), Collections.singletonList("Nylocas Waves: " + GameData.titleCase(
				role.name().toLowerCase(java.util.Locale.ROOT))), waves);
			// Every role still plans each Vasilias form, with that form's style.
			assertEquals(EnumSet.of(AttackStyle.Type.MAGIC), find(encounters, "Nylocas: Magic").getStyles());
			assertEquals(EnumSet.of(AttackStyle.Type.STAB, AttackStyle.Type.SLASH, AttackStyle.Type.CRUSH),
				find(encounters, "Nylocas: Melee").getStyles());
		}
	}
}
