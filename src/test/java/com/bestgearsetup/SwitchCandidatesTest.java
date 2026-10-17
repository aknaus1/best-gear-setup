package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SpecialPlan;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class SwitchCandidatesTest
{
	/** A setup instance two searches both return is listed once, with the fewer boosts, not overwritten. */
	@Test
	public void sharedSetupInstanceKeepsOneEntryWithFewerBoosts() throws Exception
	{
		SetupResult setup = setup();
		Potion boost = new Potion();
		boost.setId(ItemID._4DOSE2COMBAT);
		Map<AttackStyle.Type, List<SetupResult>> candidates = new LinkedHashMap<>();
		Map<DpsResult, List<Potion>> boosts = new IdentityHashMap<>();
		addCandidates(candidates, boosts, result(setup, boost));
		addCandidates(candidates, boosts, result(setup, null));
		assertEquals(1, candidates.get(AttackStyle.Type.CRUSH).size());
		assertTrue(boosts.get(setup.getDps()).isEmpty());

		// The other order keeps the unboosted entry too.
		candidates.clear();
		boosts.clear();
		addCandidates(candidates, boosts, result(setup, null));
		addCandidates(candidates, boosts, result(setup, boost));
		assertEquals(1, candidates.get(AttackStyle.Type.CRUSH).size());
		assertTrue(boosts.get(setup.getDps()).isEmpty());
	}

	/**
	 * A boosted and an unboosted quick switch can land on equal DPS results; each keeps its own boosts through the
	 * regenerated-energy copies, and even when a caller hands over a value-keyed map.
	 */
	@Test
	public void equalDpsResultsKeepTheirOwnBoosts()
	{
		Potion boost = new Potion();
		boost.setId(ItemID._4DOSE2COMBAT);
		SetupResult boosted = fullBarSetup();
		SetupResult plain = fullBarSetup();
		assertEquals(boosted.getDps(), plain.getDps());
		assertNotSame(boosted.getDps(), plain.getDps());
		Map<DpsResult, List<Potion>> boosts = new IdentityHashMap<>();
		boosts.put(boosted.getDps(), Collections.singletonList(boost));
		boosts.put(plain.getDps(), Collections.emptyList());
		SearchResults merged = result(boosted, null).withCandidates(
			Collections.singletonMap(AttackStyle.Type.CRUSH, Arrays.asList(boosted, plain)), boosts)
			.withRegeneratedChoices();

		List<SetupResult> choices = merged.getByType().get(AttackStyle.Type.CRUSH);
		assertEquals(4, choices.size());
		for (int i = 0; i < choices.size(); i++)
		{
			int expected = i < 2 ? 1 : 0;
			assertEquals("Choice " + i, expected, merged.potionsForSetup(choices.get(i)).size());
			assertEquals("Choice " + i, expected, merged.carriedPotions(choices.get(i)).size());
		}

		// A value-keyed copy taken later can't merge the setups the constructor already keyed by instance.
		Map<DpsResult, List<Potion>> copied = new HashMap<>();
		copied.put(plain.getDps(), Collections.emptyList());
		SearchResults keyed = merged.withCandidates(merged.getByType(), copied);
		assertTrue(keyed.getSetupPotions().containsKey(plain.getDps()));
		assertFalse(keyed.getSetupPotions().containsKey(boosted.getDps()));
	}

	private static void addCandidates(Map<AttackStyle.Type, List<SetupResult>> candidates, Map<DpsResult, List<Potion>> boosts,
		SearchResults found) throws Exception
	{
		Method add = BestGearSetupPlugin.class.getDeclaredMethod("addCandidates", Map.class, Map.class, SearchResults.class);
		add.setAccessible(true);
		add.invoke(null, candidates, boosts, found);
	}

	private static SearchResults result(SetupResult setup, Potion boost)
	{
		Monster monster = new Monster();
		monster.setName("test boss");
		return new SearchResults(monster, 100, Collections.singletonMap(AttackStyle.Type.CRUSH, Collections.singletonList(setup)),
			Collections.emptyList(), Collections.emptyMap(), boost == null ? Collections.emptyMap()
				: Collections.singletonMap(CombatClass.MELEE, Collections.singletonList(boost)), false);
	}

	/** A setup opening on a full spec bar, whose DPS results are new but value-equal instances on each call. */
	private static SetupResult fullBarSetup()
	{
		SetupResult base = setup();
		DpsResult regen = new DpsResult(4, 20, 1, 4, 10, null);
		SpecialPlan special = new SpecialPlan(base.getLoadout().getWeapon(), null, null, base.getLoadout().getStyle(), null,
			null, null, base.getDps(), regen, null, 2, 0, 0, regen, 1);
		return new SetupResult(CombatClass.MELEE, base.getLoadout(), base.getDps(), 0, 0, Collections.emptySet(),
			Collections.emptySet(), special);
	}

	private static SetupResult setup()
	{
		GearItem weapon = new GearItem();
		weapon.setId(100); weapon.setName("item 100"); weapon.setSlot(Slot.WEAPON);
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, weapon);
		loadout.setStyle(AttackStyle.parse("pound,crush,aggressive"));
		return new SetupResult(CombatClass.MELEE, loadout, new DpsResult(5, 20, 1, 4, 12, null), 0);
	}
}
