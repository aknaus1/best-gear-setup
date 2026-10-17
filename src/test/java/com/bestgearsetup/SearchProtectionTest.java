package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.OptimizerSettings;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.ProtectionPrayers;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class SearchProtectionTest
{
	@Test
	public void incomingContextSurvivesResultCopiesAndDoesNotUseDrainedAttackStats()
	{
		Monster incoming = new Monster();
		incoming.setName("mixed attacker");
		incoming.setMagicLevel(500);
		incoming.setRangedLevel(1);
		Map<String, Monster.MonsterAttack> attacks = new LinkedHashMap<>();
		Monster.MonsterAttack magic = new Monster.MonsterAttack();
		magic.setMaxHit(20);
		Monster.MonsterAttack ranged = new Monster.MonsterAttack();
		ranged.setMaxHit(30);
		attacks.put("magic", magic);
		attacks.put("ranged", ranged);
		incoming.setAttackStyles(attacks);
		Monster drained = incoming.copy();
		drained.setMagicLevel(1);
		GearItem weapon = new GearItem();
		weapon.setName("rune crossbow");
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, weapon);
		loadout.setStyle(AttackStyle.parse("rapid,ranged,rapid"));
		SetupResult setup = new SetupResult(CombatClass.RANGED, loadout, null, 0);
		SearchResults results = new SearchResults(drained, 100, Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), false).withProtectionContext(
				new ProtectionPrayers.Context(incoming, PlayerLevels.maxed(), OptimizerSettings.builder().build()));
		for (SearchResults copy : Arrays.asList(results, results.withSetup(setup), results.withPrepotted(),
			results.withNotes(Collections.singletonList("extra")), results.withWarnings(Collections.singletonList("warning")),
			results.wearing(Collections.singleton(Slot.AMMO)), results.withCandidates(Collections.emptyMap(), Collections.emptyMap())))
		{
			assertSame(results.getProtectionContext(), copy.getProtectionContext());
			assertEquals(ProtectionPrayers.Overhead.MAGIC, copy.protectionFor(setup).getPrayer());
		}
	}
}
