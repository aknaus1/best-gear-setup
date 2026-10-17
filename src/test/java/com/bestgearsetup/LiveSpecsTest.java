package com.bestgearsetup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.specialcounter.SpecialCounterUpdate;
import net.runelite.client.plugins.specialcounter.SpecialWeapon;
import org.junit.Test;

public class LiveSpecsTest
{
	@SuppressWarnings("unchecked")
	@Test public void returningToABossKeepsItsOwnDrainsAndDoesNotBorrowAnAddsDrains()
	{
		NPC boss = mock(NPC.class), add = mock(NPC.class);
		when(boss.getIndex()).thenReturn(7); when(boss.getId()).thenReturn(1); when(boss.getName()).thenReturn("Ba-Ba");
		when(add.getIndex()).thenReturn(8); when(add.getId()).thenReturn(2); when(add.getName()).thenReturn("Baboon");
		Client client = mock(Client.class); WorldView world = mock(WorldView.class);
		IndexedObjectSet<NPC> npcs = mock(IndexedObjectSet.class);
		when(npcs.byIndex(7)).thenReturn(boss); when(npcs.byIndex(8)).thenReturn(add);
		when(world.npcs()).thenReturn((IndexedObjectSet) npcs); when(client.getTopLevelWorldView()).thenReturn(world);
		LiveSpecs tracker = new LiveSpecs(client, mock(ClientThread.class), new BestGearSetupConfig() { }, ignored -> { });
		tracker.partySpec(7, "BANDOS_GODSWORD", 20);
		tracker.partySpec(8, "BANDOS_GODSWORD", 5);
		tracker.trackTarget(boss); assertEquals(20, tracker.overrides(boss).get("specBgsDamage"));
		tracker.trackTarget(add); assertEquals(5, tracker.overrides(add).get("specBgsDamage"));
		tracker.onNpcDespawned(new NpcDespawned(add));
		tracker.trackTarget(boss); assertEquals(20, tracker.overrides(boss).get("specBgsDamage"));
		when(boss.getId()).thenReturn(3); tracker.trackTarget(boss);
		assertEquals("A form reset clears that NPC's drains", 0, tracker.overrides(boss).get("specBgsDamage"));
		tracker.end(); assertTrue(tracker.overrides(boss).isEmpty());
	}
	@Test public void weaponTableMatchesTheSpecialAttackCounter()
	{
		// Party updates map by name, and own specs by item: both must stay in step with the Special Attack Counter.
		for (SpecWeapon weapon : SpecWeapon.values())
		{
			SpecialWeapon counter = SpecialWeapon.valueOf(weapon.name());
			for (int item : counter.getItemID())
			{
				assertSame(weapon, SpecWeapon.byItem(item));
			}
			for (int distance = 1; distance <= 10; distance++)
			{
				assertEquals(weapon.name(), counter.getHitDelay(distance), weapon.hitDelay(distance));
			}
			assertEquals(weapon.name(), counter.isDamage(), weapon.damage);
		}
		assertArrayEquals(new int[]{ItemID.BGS, ItemID.BGSG}, SpecialWeapon.BANDOS_GODSWORD.getItemID());
		assertNull(SpecWeapon.byName("DARKLIGHT"));
	}

	@Test public void landedAmountsFollowEachSetting()
	{
		assertEquals(1, SpecWeapon.DRAGON_WARHAMMER.amount(45, null));
		assertEquals(0, SpecWeapon.DRAGON_WARHAMMER.amount(0, null));
		assertEquals(37, SpecWeapon.BANDOS_GODSWORD.amount(37, null));
		assertEquals(2, SpecWeapon.TONALZTICS_OF_RALOS.amount(2, null));
	}

	@Test public void catalogueVariantsMatchTheLiveNpcName()
	{
		assertTrue(LiveSpecs.sameTarget("verzik vitur", "verzik vitur (phase 2)"));
		assertTrue(LiveSpecs.sameTarget("xarpus", "xarpus"));
		assertFalse(LiveSpecs.sameTarget("verzik", "verzik vitur (phase 2)"));
	}

	@SuppressWarnings("unchecked")
	@Test public void partySpecsOverrideSearchesOfTheTargetUntilItDies()
	{
		NPC boss = mock(NPC.class);
		when(boss.getIndex()).thenReturn(7);
		when(boss.getName()).thenReturn("Verzik Vitur");
		IndexedObjectSet<NPC> npcs = mock(IndexedObjectSet.class);
		when(npcs.byIndex(7)).thenReturn(boss);
		WorldView view = mock(WorldView.class);
		when(view.npcs()).thenReturn((IndexedObjectSet) npcs);
		Client client = mock(Client.class);
		when(client.getTopLevelWorldView()).thenReturn(view);
		when(client.getWorld()).thenReturn(330);
		ClientThread thread = mock(ClientThread.class);
		doAnswer(i -> { i.<Runnable>getArgument(0).run(); return null; }).when(thread).invoke(any(Runnable.class));
		PartyService party = mock(PartyService.class);
		when(party.getLocalMember()).thenReturn(new PartyMember(1));
		BestGearSetupConfig config = mock(BestGearSetupConfig.class);
		when(config.autoSpecs()).thenReturn(true);
		List<String> changes = new ArrayList<>();

		LiveSpecs specs = new LiveSpecs(client, thread, config, changes::add);
		PartySpecs bridge = new PartySpecs(client, thread, party, specs);
		bridge.onSpecialCounterUpdate(update(SpecialWeapon.BANDOS_GODSWORD, 40, 2));
		bridge.onSpecialCounterUpdate(update(SpecialWeapon.DRAGON_WARHAMMER, 31, 2));
		bridge.onSpecialCounterUpdate(update(SpecialWeapon.DRAGON_WARHAMMER, 0, 2));
		// Unmodelled weapons and the player's own echoed updates are ignored.
		bridge.onSpecialCounterUpdate(update(SpecialWeapon.DARKLIGHT, 1, 2));
		bridge.onSpecialCounterUpdate(update(SpecialWeapon.DRAGON_WARHAMMER, 31, 1));
		Map<String, Object> values = specs.overrides("verzik vitur (phase 2)");
		assertEquals(1, values.get("specDwh"));
		assertEquals(40, values.get("specBgsDamage"));
		assertEquals(40, specs.overrides(boss).get("specBgsDamage"));
		assertEquals(0, values.get("specElderMaul"));
		assertTrue(specs.overrides("xarpus").isEmpty());
		assertEquals(Arrays.asList("verzik vitur", "verzik vitur", "verzik vitur"), changes);
		when(boss.getId()).thenReturn(8374);
		assertTrue(specs.overrides(boss).isEmpty());
		specs.trackTarget(boss);
		assertEquals(0, specs.overrides(boss).get("specBgsDamage"));

		NpcDespawned died = mock(NpcDespawned.class);
		when(died.getNpc()).thenReturn(boss);
		specs.onNpcDespawned(died);
		assertTrue(specs.overrides("verzik vitur (phase 2)").isEmpty());
		assertEquals(4, changes.size());
	}

	private static SpecialCounterUpdate update(SpecialWeapon weapon, int hit, long member)
	{
		SpecialCounterUpdate update = new SpecialCounterUpdate(7, weapon, hit, 330, 99);
		update.setMemberId(member);
		return update;
	}

	@Test public void activeDefenceUsesNpcIdentityAndResetsOnSpawnOrPhaseChange()
	{
		Client client = mock(Client.class);
		ClientThread thread = mock(ClientThread.class);
		BestGearSetupConfig config = new BestGearSetupConfig() { };
		LiveSpecs specs = new LiveSpecs(client, thread, config, name -> { });
		NPC first = mock(NPC.class);
		NPC other = mock(NPC.class);
		when(first.getName()).thenReturn("Tekton"); when(first.getIndex()).thenReturn(7); when(first.getId()).thenReturn(7540);
		when(other.getName()).thenReturn("Tekton"); when(other.getIndex()).thenReturn(7); when(other.getId()).thenReturn(7540);
		specs.trackTarget(first);
		assertEquals(0, specs.overrides(first).get("specDwh"));
		assertTrue(specs.overrides(other).isEmpty());
		when(first.getId()).thenReturn(7543);
		assertTrue(specs.overrides(first).isEmpty());
		specs.trackTarget(first);
		assertEquals(0, specs.overrides(first).get("specBgsDamage"));
		specs.trackTarget(other);
		assertTrue(specs.overrides(first).isEmpty());
		assertEquals(0, specs.overrides(other).get("specDwh"));
		specs.end();
		assertTrue(specs.overrides(other).isEmpty());
	}
}
