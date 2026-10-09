package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.specialcounter.SpecialCounterUpdate;
import net.runelite.client.plugins.specialcounter.SpecialWeapon;
import org.junit.Test;

public class LiveSpecsTest
{
	@Test public void weaponsMapToTheirPreparationSetting()
	{
		assertEquals("specDwh", LiveSpecs.key(SpecialWeapon.DRAGON_WARHAMMER));
		assertEquals("specBgsDamage", LiveSpecs.key(SpecialWeapon.BANDOS_GODSWORD));
		assertEquals("specTonalztics", LiveSpecs.key(SpecialWeapon.TONALZTICS_OF_RALOS));
		assertNull(LiveSpecs.key(SpecialWeapon.DARKLIGHT));
		assertEquals(1, LiveSpecs.amount(SpecialWeapon.DRAGON_WARHAMMER, 45));
		assertEquals(0, LiveSpecs.amount(SpecialWeapon.DRAGON_WARHAMMER, 0));
		assertEquals(37, LiveSpecs.amount(SpecialWeapon.BANDOS_GODSWORD, 37));
		assertEquals(2, LiveSpecs.amount(SpecialWeapon.TONALZTICS_OF_RALOS, 2));
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

		LiveSpecs specs = new LiveSpecs(client, thread, party, config, changes::add);
		specs.onSpecialCounterUpdate(update(SpecialWeapon.BANDOS_GODSWORD, 40));
		specs.onSpecialCounterUpdate(update(SpecialWeapon.DRAGON_WARHAMMER, 31));
		specs.onSpecialCounterUpdate(update(SpecialWeapon.DRAGON_WARHAMMER, 0));
		Map<String, Object> values = specs.overrides("verzik vitur (phase 2)");
		assertEquals(1, values.get("specDwh"));
		assertEquals(40, values.get("specBgsDamage"));
		assertEquals(0, values.get("specElderMaul"));
		assertTrue(specs.overrides("xarpus").isEmpty());
		assertEquals(3, changes.size());

		NpcDespawned died = mock(NpcDespawned.class);
		when(died.getNpc()).thenReturn(boss);
		specs.onNpcDespawned(died);
		assertTrue(specs.overrides("verzik vitur (phase 2)").isEmpty());
		assertEquals("verzik vitur", changes.get(3));
	}

	private static SpecialCounterUpdate update(SpecialWeapon weapon, int hit)
	{
		SpecialCounterUpdate update = new SpecialCounterUpdate(7, weapon, hit, 330, 99);
		update.setMemberId(2);
		return update;
	}
}
