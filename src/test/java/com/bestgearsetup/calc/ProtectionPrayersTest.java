package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.ProtectionPrayers.Advice;
import com.bestgearsetup.calc.ProtectionPrayers.Overhead;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameDataLoader;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

public class ProtectionPrayersTest
{
	private static GameDataLoader loader;

	@BeforeClass
	public static void load() throws Exception
	{
		loader = new GameDataLoader(new Gson());
		loader.loadGameData();
	}

	private static Monster bundled(String name) throws Exception
	{
		MonsterSummary summary = loader.loadGameData().getMonsters().stream().filter(m -> m.getName().equals(name))
			.findFirst().orElseThrow(() -> new AssertionError("Missing " + name));
		return loader.getMonster(summary);
	}

	private static Monster attacking(String name, Object... styleAndMax)
	{
		Monster m = new Monster();
		m.setName(name);
		Map<String, Monster.MonsterAttack> attacks = new LinkedHashMap<>();
		for (int i = 0; i < styleAndMax.length; i += 2)
		{
			Monster.MonsterAttack attack = new Monster.MonsterAttack();
			attack.setMaxHit((Integer) styleAndMax[i + 1]);
			attacks.put((String) styleAndMax[i], attack);
		}
		m.setAttackStyles(attacks);
		return m;
	}

	@Test
	public void meleeOnlyMonstersStillNeedMeleeFromDistanceUnlessSafespotted()
	{
		Monster demon = attacking("abyssal demon (standard)", "stab", 8);
		Advice close = ProtectionPrayers.recommend(demon, CombatClass.MELEE, 99);
		assertEquals(Overhead.MELEE, close.getPrayer());
		assertFalse(close.isSwitching());
		Advice far = ProtectionPrayers.recommend(demon, CombatClass.RANGED, 99);
		assertEquals(Overhead.MELEE, far.getPrayer());
		assertEquals("Protect from Melee", far.summary());
		assertTrue(far.getNote().contains("safespot"));
	}

	@Test
	public void unreadableAttackDataIsUnknownRatherThanNoneNeeded()
	{
		Advice all = ProtectionPrayers.recommend(attacking("tormented demon", "all", 30), CombatClass.MELEE, 99);
		assertEquals(Arrays.asList(Overhead.MISSILES, Overhead.MAGIC, Overhead.MELEE), all.getSwitches());
		Advice unknown = ProtectionPrayers.recommend(attacking("mystery", "none", 22), CombatClass.MELEE, 99);
		assertNull(unknown.getPrayer());
		assertTrue(unknown.isUnknown());
		assertEquals("Unknown \u2013 check the Wiki", unknown.summary());
		assertTrue(ProtectionPrayers.recommend(attacking("mystery", "various", 10), CombatClass.MAGIC, 99).isUnknown());
		assertTrue(ProtectionPrayers.recommend(new Monster(), CombatClass.MAGIC, 99).isUnknown());
		// No attack at all, or only attacks no overhead blocks: nothing to pray.
		Advice idle = ProtectionPrayers.recommend(attacking("practice dummy", "n/a", 0), CombatClass.MELEE, 99);
		assertEquals("None needed", idle.summary());
		assertFalse(idle.isUnknown());
		Advice typeless = ProtectionPrayers.recommend(attacking("spitter", "typeless ranged", 11, "poison", 11), CombatClass.RANGED, 99);
		assertEquals("None needed", typeless.summary());
		assertTrue(typeless.getNote().contains("aren't blocked"));
		// Several attacks in one entry are read separately.
		assertEquals(Overhead.MISSILES, ProtectionPrayers.recommend(attacking("wasp", "ranged <br/> typeless", 20),
			CombatClass.MAGIC, 99).getPrayer());
	}

	@Test
	public void mixedAttackersPrayAgainstTheHardestHitAndDropMeleeFromDistance()
	{
		Monster graardor = attacking("general graardor", "crush", 60, "ranged", 35);
		assertEquals(Overhead.MELEE, ProtectionPrayers.recommend(graardor, CombatClass.MELEE, 99).getPrayer());
		Advice far = ProtectionPrayers.recommend(graardor, CombatClass.MAGIC, 99);
		assertEquals(Overhead.MISSILES, far.getPrayer());
		assertFalse(far.isSwitching());
		Advice vorkath = ProtectionPrayers.recommend(attacking("vorkath (post-quest)", "dragonfire", 80, "magic", 30,
			"ranged", 32, "slash", 32), CombatClass.RANGED, 99);
		assertEquals(Overhead.MISSILES, vorkath.getPrayer());
		assertEquals(Arrays.asList(Overhead.MISSILES, Overhead.MAGIC), vorkath.getSwitches());
		assertEquals("Switch: Missiles / Magic", vorkath.summary());
	}

	@Test
	public void unprotectableAndMagicalStylesFollowTheirDelivery()
	{
		assertTrue(ProtectionPrayers.protectedBy("typeless ranged").isEmpty());
		assertTrue(ProtectionPrayers.protectedBy("dragonfire").isEmpty());
		assertTrue(ProtectionPrayers.protectedBy("magic (100% prayer penetration)").isEmpty());
		assertEquals(Collections.singleton(Overhead.MELEE), ProtectionPrayers.protectedBy("magical melee"));
		assertEquals(Collections.singleton(Overhead.MISSILES), ProtectionPrayers.protectedBy("magical ranged"));
		Advice none = ProtectionPrayers.recommend(attacking("glowing crystal", "none", 0), CombatClass.MELEE, 99);
		assertNull(none.getPrayer());
	}

	@Test
	public void prayerLevelLimitsTheOverheads()
	{
		Monster graardor = attacking("general graardor", "crush", 60, "ranged", 35);
		Advice low = ProtectionPrayers.recommend(graardor, CombatClass.MELEE, 42);
		assertEquals(Overhead.MISSILES, low.getPrayer());
		assertTrue(low.getNote().contains("Protect from Melee needs 43 Prayer"));
		Advice none = ProtectionPrayers.recommend(graardor, CombatClass.MELEE, 36);
		assertNull(none.getPrayer());
		assertEquals(37, Overhead.MAGIC.getLevel());
		assertEquals(40, Overhead.MISSILES.getLevel());
	}

	@Test
	public void coxBossesUseCuratedAdvice() throws Exception
	{
		assertEquals(Overhead.MELEE, ProtectionPrayers.recommend(bundled("tekton (normal)"), CombatClass.MELEE, 99).getPrayer());
		assertEquals(Overhead.MELEE, ProtectionPrayers.recommend(bundled("tekton (enraged)"), CombatClass.MELEE, 99).getPrayer());
		Advice mystics = ProtectionPrayers.recommend(bundled("skeletal mystic (normal)"), CombatClass.RANGED, 99);
		assertEquals(Overhead.MAGIC, mystics.getPrayer());
		assertFalse(mystics.isSwitching());
		Monster large = bundled("muttadile (large)");
		assertEquals(Overhead.MELEE, ProtectionPrayers.recommend(large, CombatClass.MELEE, 99).getPrayer());
		Advice far = ProtectionPrayers.recommend(large, CombatClass.MAGIC, 99);
		assertEquals(Arrays.asList(Overhead.MISSILES, Overhead.MAGIC), far.getSwitches());
		Monster small = bundled("muttadile (small)");
		assertEquals(Overhead.MELEE, ProtectionPrayers.recommend(small, CombatClass.MELEE, 99).getPrayer());
		Advice olm = ProtectionPrayers.recommend(bundled("great olm (melee hand)"), CombatClass.MELEE, 99);
		assertTrue(olm.isSwitching());
		assertTrue(olm.getSwitches().containsAll(Arrays.asList(Overhead.MAGIC, Overhead.MISSILES)));
		assertFalse(olm.getSwitches().contains(Overhead.MELEE));
		assertNull(ProtectionPrayers.recommend(bundled("guardian (chambers of xeric) (normal)"), CombatClass.MELEE, 99).getPrayer());
		assertEquals(Overhead.MISSILES, ProtectionPrayers.recommend(bundled("vespula (normal)"), CombatClass.RANGED, 99).getPrayer());
		assertEquals(Overhead.MISSILES, ProtectionPrayers.recommend(
			bundled("lizardman shaman (chambers of xeric) (normal)"), CombatClass.RANGED, 99).getPrayer());
	}

	@Test
	public void toaAndTobBossesUseCuratedAdvice() throws Exception
	{
		assertEquals(Overhead.MELEE, ProtectionPrayers.recommend(bundled("ba-ba"), CombatClass.MELEE, 99).getPrayer());
		Advice zebak = ProtectionPrayers.recommend(bundled("zebak (normal)"), CombatClass.RANGED, 99);
		assertEquals(Arrays.asList(Overhead.MISSILES, Overhead.MAGIC), zebak.getSwitches());
		Advice akkha = ProtectionPrayers.recommend(bundled("akkha"), CombatClass.MELEE, 99);
		assertEquals(3, akkha.getSwitches().size());
		assertFalse(ProtectionPrayers.recommend(bundled("akkha"), CombatClass.MAGIC, 99).getSwitches().contains(Overhead.MELEE));
		assertEquals(Overhead.MAGIC, ProtectionPrayers.recommend(bundled("the maiden of sugadinti (normal)"), CombatClass.MAGIC, 99).getPrayer());
		assertEquals(Overhead.MAGIC, ProtectionPrayers.recommend(bundled("nylocas matomenos (normal mode maiden)"), CombatClass.MAGIC, 99).getPrayer());
		Monster verzikNylocas = bundled("nylocas matomenos (normal mode verzik)");
		assertNull("The Maiden advice is only for the Maiden room", ProtectionPrayers.curated(verzikNylocas, false));
		assertNull(ProtectionPrayers.recommend(bundled("pestilent bloat (normal)"), CombatClass.MELEE, 99).getPrayer());
		assertEquals(Overhead.MELEE, ProtectionPrayers.recommend(bundled("nylocas vasilias (melee) (normal)"), CombatClass.MELEE, 99).getPrayer());
		assertEquals(Overhead.MISSILES, ProtectionPrayers.recommend(bundled("nylocas vasilias (ranged) (normal)"), CombatClass.RANGED, 99).getPrayer());
		assertEquals(Overhead.MAGIC, ProtectionPrayers.recommend(bundled("nylocas vasilias (magic) (normal)"), CombatClass.MAGIC, 99).getPrayer());
		Advice sotetseg = ProtectionPrayers.recommend(bundled("sotetseg (normal)"), CombatClass.MELEE, 99);
		assertEquals(Arrays.asList(Overhead.MELEE, Overhead.MAGIC, Overhead.MISSILES), sotetseg.getSwitches());
		assertFalse(ProtectionPrayers.recommend(bundled("sotetseg (normal)"), CombatClass.RANGED, 99).getSwitches().contains(Overhead.MELEE));
		// Xarpus's spit is typeless in the Wiki data, so no overhead reduces it.
		Advice xarpus = ProtectionPrayers.recommend(bundled("xarpus"), CombatClass.RANGED, 99);
		assertNull(xarpus.getPrayer());
		assertTrue(xarpus.getNote().contains("typeless"));
		// Verzik P3's melee is typeless crush: even in melee range the overheads are for her magic and ranged.
		Advice verzik = ProtectionPrayers.recommend(bundled("verzik vitur (phase 3)"), CombatClass.MELEE, 99);
		assertEquals(Arrays.asList(Overhead.MAGIC, Overhead.MISSILES), verzik.getSwitches());
		assertTrue(verzik.getNote().contains("typeless"));
		assertFalse(ProtectionPrayers.recommend(bundled("verzik vitur (phase 3)"), CombatClass.RANGED, 99)
			.getSwitches().contains(Overhead.MELEE));
		assertNull(ProtectionPrayers.recommend(bundled("tumeken's warden (core-ejected)"), CombatClass.MELEE, 99).getPrayer());
		Advice warden = ProtectionPrayers.recommend(bundled("tumeken's warden (phase 3)"), CombatClass.MAGIC, 99);
		assertTrue(warden.isSwitching());
	}

	@Test
	public void akkhasShadowsUseTheirOwnData() throws Exception
	{
		Monster shadow = bundled("akkha's shadow");
		assertEquals(ProtectionPrayers.fromAttackStyles(shadow, true), ProtectionPrayers.recommend(shadow, CombatClass.MELEE, 99));
	}
}
