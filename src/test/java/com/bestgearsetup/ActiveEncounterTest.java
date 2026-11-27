package com.bestgearsetup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Summary;
import com.bestgearsetup.ui.Sidebar;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class ActiveEncounterTest
{
	/** The Kalphite Queen's airborne form is its own monster, so changing into it searches again. */
	@Test
	public void searchesAFormListedAsItsOwnMonster() throws Exception
	{
		Summary crawling = monster(963, "kalphite queen (crawling)");
		Summary airborne = monster(965, "kalphite queen (airborne)");
		Fixture f = new Fixture(crawling, airborne);
		NPC queen = f.npc(963, "Kalphite Queen");
		f.engage(queen);
		verify(f.plugin.panel).selectMonster(crawling, true);
		f.shown(crawling);
		when(queen.getId()).thenReturn(965);
		f.tick();
		verify(f.plugin.panel).selectMonster(airborne, true);
	}

	/** Akkha's forms share one monster entry, so its shown results stand through the fight. */
	@Test
	public void formsOfOneMonsterKeepItsResults() throws Exception
	{
		Summary akkha = monster(11789, "akkha");
		Fixture f = new Fixture(akkha);
		NPC npc = f.npc(11789, "Akkha");
		f.engage(npc);
		verify(f.plugin.panel).selectMonster(akkha, true);
		f.shown(akkha);
		for (int form : new int[]{NpcID.AKKHA_RANGE, NpcID.AKKHA_MAGE, NpcID.AKKHA_ENRAGE})
		{
			when(npc.getId()).thenReturn(form);
			f.tick();
		}
		verify(f.plugin.panel, times(1)).selectMonster(akkha, true);
	}

	/** Results found at one raid level (or party size) are searched again once the live raid differs. */
	@Test
	public void aChangedLiveRaidSearchesTheShownTypeAgain() throws Exception
	{
		Summary kephri = monster(11719, "kephri");
		kephri.setAttributes(Collections.singletonList("tombs of amascut"));
		Fixture f = new Fixture(kephri);
		Map<Integer, Integer> bits = new HashMap<>();
		bits.put(VarbitID.TOA_CLIENT_PARTYSTATUS, 1);
		bits.put(VarbitID.TOA_CLIENT_P0, 1);
		bits.put(VarbitID.TOA_CLIENT_RAID_LEVEL, 300);
		when(f.client.getVarbitValue(anyInt())).thenAnswer(i -> bits.getOrDefault(i.<Integer>getArgument(0), 0));
		f.shown(kephri);
		f.plugin.searchedRaid = LiveRaid.read(f.client, kephri).key();
		f.engage(f.npc(11719, "Kephri"));
		verify(f.plugin.panel, never()).selectMonster(any(), anyBoolean());
		bits.put(VarbitID.TOA_CLIENT_RAID_LEVEL, 400);
		f.engage(f.npc(11719, "Kephri"));
		verify(f.plugin.panel).selectMonster(kephri, true);
	}

	@Test
	public void deathEndsTheEncounterAndUnattackableNpcsAreNotFollowed() throws Exception
	{
		Fixture f = new Fixture();
		NPC boss = f.npc(200, "Boss");
		f.engage(boss);
		when(boss.getHealthRatio()).thenReturn(0);
		f.tick();
		when(boss.getHealthRatio()).thenReturn(100);
		f.engage(boss);
		verify(f.plugin.panel, times(2)).selectMonster(f.boss, true);
		NPC pet = f.npc(201, "Add");
		when(pet.getTransformedComposition().getActions()).thenReturn(new String[]{"Talk-to"});
		f.engage(pet);
		verify(f.plugin.panel, never()).selectMonster(f.add, true);
	}

	private static Summary monster(int id, String name)
	{
		Summary monster = new Summary();
		monster.setId(id);
		monster.setName(name);
		return monster;
	}

	@Test
	public void followsEachNewTransformedIdOnceAndHonoursDisable() throws Exception
	{
		Fixture f = new Fixture();
		NPC npc = f.npc(200, "Boss");
		when(f.player.getInteracting()).thenReturn(npc);
		when(npc.getInteracting()).thenReturn(f.player);
		f.tick();
		f.tick();
		verify(f.plugin.panel, times(1)).selectMonster(f.boss, true);
		when(npc.getId()).thenReturn(201);
		f.tick();
		verify(f.plugin.panel).selectMonster(f.add, true);
		f.following = false;
		when(npc.getId()).thenReturn(200);
		f.tick();
		verify(f.plugin.panel, times(1)).selectMonster(f.boss, true);
	}

	@Test
	public void targetingOrStandingNearAnNpcIsNotCombat() throws Exception
	{
		Fixture f = new Fixture();
		NPC boss = f.npc(200, "Boss");
		when(f.player.getInteracting()).thenReturn(boss);
		f.tick();
		verify(f.plugin.panel, never()).selectMonster(any(), anyBoolean());
		f.plugin.onHitsplatApplied(f.hit(boss, false));
		f.tick();
		verify(f.plugin.panel, never()).selectMonster(any(), anyBoolean());
		f.plugin.onHitsplatApplied(f.hit(boss, true));
		f.tick();
		verify(f.plugin.panel).selectMonster(f.boss, true);
	}

	@Test
	public void ownHitsLeadAndTheTargetedNpcWinsAoeHits() throws Exception
	{
		Fixture f = new Fixture();
		NPC boss = f.npc(200, "Boss");
		NPC add = f.npc(201, "Add");
		f.plugin.onHitsplatApplied(f.hit(boss, true));
		f.plugin.onHitsplatApplied(f.hit(add, true));
		f.tick();
		verify(f.plugin.panel).selectMonster(f.boss, true);
		verify(f.plugin.panel, never()).selectMonster(f.add, true);
		when(f.player.getInteracting()).thenReturn(add);
		f.plugin.onHitsplatApplied(f.hit(add, true));
		f.tick();
		verify(f.plugin.panel).selectMonster(f.add, true);
	}

	@Test
	public void combatLapsesAfterIdleTicksAndOnDespawn() throws Exception
	{
		Fixture f = new Fixture();
		NPC boss = f.npc(200, "Boss");
		f.plugin.onHitsplatApplied(f.hit(boss, true));
		f.tick();
		when(f.client.getTickCount()).thenReturn(16);
		f.tick();
		verify(f.plugin.panel, times(1)).selectMonster(f.boss, true);
		when(f.client.getTickCount()).thenReturn(17);
		f.tick();
		f.plugin.onHitsplatApplied(f.hit(boss, true));
		f.tick();
		verify(f.plugin.panel, times(2)).selectMonster(f.boss, true);
		f.plugin.onNpcDespawned(new NpcDespawned(boss));
		f.tick();
		f.plugin.onHitsplatApplied(f.hit(boss, true));
		f.tick();
		verify(f.plugin.panel, times(3)).selectMonster(f.boss, true);
	}

	@Test
	public void anotherNpcOfTheShownTypeKeepsTheResults() throws Exception
	{
		Fixture f = new Fixture();
		// The boss's results are already shown, from a search of a live NPC.
		f.shown(f.boss);
		// Neither this NPC nor the next one with the same id, e.g. after a kill, searches again.
		for (int i = 0; i < 2; i++)
		{
			f.engage(f.npc(200, "Boss"));
		}
		verify(f.plugin.panel, never()).selectMonster(any(), anyBoolean());
		// A different monster does.
		f.engage(f.npc(201, "Add"));
		verify(f.plugin.panel).selectMonster(f.add, true);
	}

	@Test
	public void failedResultsAreSearchedAgain() throws Exception
	{
		Fixture f = new Fixture();
		f.plugin.lastSearched = f.boss;
		f.plugin.searchedLive = true;
		f.plugin.searchFailed = true;
		f.engage(f.npc(200, "Boss"));
		verify(f.plugin.panel).selectMonster(f.boss, true);
		f.plugin.searchFailed = false;
		assertTrue(f.plugin.showsSameType(f.boss));
		assertFalse(f.plugin.showsSameType(f.add));
		f.plugin.lastSearched = null;
		assertFalse(f.plugin.showsSameType(f.boss));
	}

	@Test
	public void resultsPlannedWithoutALiveNpcAreSearchedOnTheFirstEngagement() throws Exception
	{
		Fixture f = new Fixture();
		// Picked in the panel: no task location was read.
		f.plugin.lastSearched = f.boss;
		f.plugin.searchedLive = false;
		f.engage(f.npc(200, "Boss"));
		verify(f.plugin.panel).selectMonster(f.boss, true);
		assertFalse(f.plugin.showsSameType(f.boss));
		// Once that live search has run, the next NPC of the type keeps its results.
		f.shown(f.boss);
		f.engage(f.npc(200, "Boss"));
		verify(f.plugin.panel, times(1)).selectMonster(f.boss, true);
	}

	/** An active-encounter plugin with known monsters, by default a boss (id 200) and an add (id 201). */
	private static final class Fixture
	{
		final BestGearSetupPlugin plugin = new BestGearSetupPlugin();
		final Client client = mock(Client.class);
		final Player player = mock(Player.class);
		final Summary boss = new Summary();
		final Summary add = new Summary();
		boolean following = true;

		Fixture(Summary... monsters)
		{
			when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
			when(client.getLocalPlayer()).thenReturn(player);
			boss.setId(200);
			boss.setName("boss");
			add.setId(201);
			add.setName("add");
			plugin.gameData = new GameData(monsters.length == 0 ? Arrays.asList(boss, add) : Arrays.asList(monsters),
				Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(),
				Collections.emptyMap());
			plugin.config = new Options()
			{
				@Override
				public boolean activeEncounter()
				{
					return following;
				}
			};
			plugin.panel = mock(Sidebar.class);
			plugin.client = client;
			plugin.ownedItems = ConfigItemsForTests.emptyOwnedItems();
		}

		NPC npc(int id, String name)
		{
			NPC npc = mock(NPC.class);
			NPCComposition composition = mock(NPCComposition.class);
			when(npc.getId()).thenReturn(id);
			when(npc.getName()).thenReturn(name);
			when(npc.getHealthRatio()).thenReturn(100);
			when(npc.getTransformedComposition()).thenReturn(composition);
			when(composition.getActions()).thenReturn(new String[]{"Attack"});
			return npc;
		}

		HitsplatApplied hit(NPC npc, boolean mine)
		{
			Hitsplat hitsplat = mock(Hitsplat.class);
			when(hitsplat.isMine()).thenReturn(mine);
			HitsplatApplied event = new HitsplatApplied();
			event.setActor(npc);
			event.setHitsplat(hitsplat);
			return event;
		}

		/** The monster's results are shown, from a search of a live NPC outside any raid. */
		void shown(Summary monster)
		{
			plugin.lastSearched = monster;
			plugin.searchedLive = true;
			plugin.searchedRaid = new LiveRaid().key();
		}

		/** Target the NPC and land a hit on it, then let a tick pass. */
		void engage(NPC npc) throws Exception
		{
			when(player.getInteracting()).thenReturn(npc);
			plugin.onHitsplatApplied(hit(npc, true));
			tick();
		}

		void tick() throws Exception
		{
			plugin.onGameTick(new GameTick());
			SwingUtilities.invokeAndWait(() ->
			{
			});
		}
	}
}
