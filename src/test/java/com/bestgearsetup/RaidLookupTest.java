package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.ui.BestGearSetupPanel;
import java.util.Collections;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.ui.ClientToolbar;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/** Exercise the real right-click menu callback rather than calling the panel directly. */
public class RaidLookupTest
{
	private final BestGearSetupPlugin plugin = new BestGearSetupPlugin();
	private final MonsterSummary akkha = new MonsterSummary();
	private final MenuEntry action = mock(MenuEntry.class, RETURNS_SELF);
	private MenuEntry examine;

	@Before
	public void setUp() throws Exception
	{
		plugin.config = new BestGearSetupConfig() { };
		plugin.panel = mock(BestGearSetupPanel.class);
		plugin.search = new FutureTask<>(() -> null);
		plugin.searchGeneration.set(42);
		akkha.setId(11789); akkha.setName("akkha");
		plugin.gameData = new GameData(Collections.singletonList(akkha), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		Client client = mock(Client.class);
		Menu menu = mock(Menu.class);
		when(client.getMenu()).thenReturn(menu);
		when(menu.createMenuEntry(-1)).thenReturn(action);
		plugin.client = client;
		plugin.clientToolbar = mock(ClientToolbar.class);
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(NpcID.AKKHA_MAGE); when(npc.getName()).thenReturn("Akkha");
		NPCComposition composition = mock(NPCComposition.class);
		when(composition.getActions()).thenReturn(new String[]{"Attack"});
		when(npc.getTransformedComposition()).thenReturn(composition);
		examine = mock(MenuEntry.class);
		when(examine.getType()).thenReturn(MenuAction.EXAMINE_NPC);
		when(examine.getNpc()).thenReturn(npc);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void click() throws Exception
	{
		plugin.onMenuEntryAdded(new MenuEntryAdded(examine));
		ArgumentCaptor<Consumer<MenuEntry>> callback = ArgumentCaptor.forClass((Class) Consumer.class);
		verify(action).onClick(callback.capture());
		callback.getValue().accept(action);
		SwingUtilities.invokeAndWait(() -> { });
	}

	@Test
	public void rightClickSelectsTheLivePhaseWithoutStartingOrCancellingASearch() throws Exception
	{
		when(plugin.panel.selectRaidMonster(akkha, EncounterPhase.AKKHA_IMMUNE_MAGIC)).thenReturn(true);
		click();
		verify(plugin.panel).selectRaidMonster(akkha, EncounterPhase.AKKHA_IMMUNE_MAGIC);
		verify(plugin.panel, never()).selectMonster(any(), org.mockito.ArgumentMatchers.anyBoolean());
		verify(plugin.panel, never()).selectActiveMonster(any());
		assertEquals(42, plugin.searchGeneration.get());
		assertFalse(plugin.search.isCancelled());
	}

	@Test
	public void withoutAMatchingRaidTheNormalMonsterLookupStillRuns() throws Exception
	{
		click();
		verify(plugin.panel).selectMonster(akkha, true);
	}
}
