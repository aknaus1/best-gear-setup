package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Summary;
import com.bestgearsetup.ui.PanelInternals;
import com.bestgearsetup.ui.Sidebar;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/** The PluginMessage link with the Field Guide plugin, in both directions. */
public class FieldGuideLinkTest
{
	@PluginDescriptor(name = "Field Guide")
	static class FieldGuideStandIn extends Plugin
	{
	}

	@PluginDescriptor(name = "Shortest Path")
	static class OtherPlugin extends Plugin
	{
	}

	private final List<Summary> searched = new ArrayList<>();
	private final AtomicInteger invokes = new AtomicInteger();
	private final ClientToolbar toolbar = mock(ClientToolbar.class);
	private final Summary target = monster(1234, "test target", 50);
	private final Summary other = monster(5678, "other target", 10);

	private BestGearSetupPlugin plugin()
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override public net.runelite.client.config.ConfigDescriptor getConfigDescriptor()
			{ return ConfigItemsForTests.descriptor(); }
			@Override public Map<Slot, SlotLock> getLocks() { return Collections.emptyMap(); }
			@Override public Set<Integer> getExcluded() { return Collections.emptySet(); }
			@Override public boolean owns(Gear item) { return true; }
			@Override public void findBestSetup(Summary summary) { searched.add(summary); }
		};
		plugin.ownedItems = new OwnedItems(null, null);
		plugin.clientToolbar = toolbar;
		// Field Guide may post from any thread; the lookup itself always runs on the client thread.
		plugin.clientThread = new ClientThread()
		{
			@Override
			public void invoke(Runnable runnable)
			{
				invokes.incrementAndGet();
				runnable.run();
			}
		};
		return plugin;
	}

	private BestGearSetupPlugin pluginWithPanel() throws Exception
	{
		BestGearSetupPlugin plugin = plugin();
		GameData data = new GameData(Arrays.asList(target, other), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		plugin.gameData = data;
		Sidebar[] panels = new Sidebar[1];
		SwingUtilities.invokeAndWait(() ->
		{
			panels[0] = new Sidebar(plugin, new Options() {}, null, null, null);
			panels[0].onDataLoaded(data);
		});
		plugin.panel = panels[0];
		return plugin;
	}

	private static Summary monster(int id, String name, int level)
	{
		Summary s = new Summary();
		s.setId(id);
		s.setName(name);
		s.setCombatLevel(level);
		return s;
	}

	private static Map<String, Object> request(Object... keysAndValues)
	{
		Map<String, Object> data = new HashMap<>();
		for (int i = 0; i < keysAndValues.length; i += 2)
		{
			data.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}
		return data;
	}

	private static void send(BestGearSetupPlugin plugin, String namespace, String name, Map<String, Object> data)
		throws Exception
	{
		plugin.onPluginMessage(new PluginMessage(namespace, name, data));
		SwingUtilities.invokeAndWait(() -> {});
	}

	@Test
	public void aLookupOpensThePanelOnTheMatchingMonster() throws Exception
	{
		BestGearSetupPlugin plugin = pluginWithPanel();
		send(plugin, "bestgearsetup", "lookup", request("npc", 1234, "name", "Test target", "level", 50));
		assertEquals(Collections.singletonList(target), searched);
		verify(toolbar).openPanel(null);
		// The name and level are optional; an unknown id falls back to the name, as the right-click entry does.
		send(plugin, "bestgearsetup", "lookup", request("npc", 5678));
		send(plugin, "bestgearsetup", "lookup", request("npc", 9999, "name", "Other target"));
		assertEquals(Arrays.asList(target, other, other), searched);
		assertEquals(3, invokes.get());
		// No match: the panel says so instead of searching.
		send(plugin, "bestgearsetup", "lookup", request("npc", 9999));
		assertEquals(3, searched.size());
		assertTrue(PanelInternals.statusLabel(plugin.panel).getText().contains("No monster data for NPC (id 9999)."));
	}

	@Test
	public void malformedOrOtherMessagesAreIgnored() throws Exception
	{
		BestGearSetupPlugin plugin = pluginWithPanel();
		send(plugin, "fieldguide", "lookup", request("npc", 1234));
		send(plugin, "bestgearsetup", "other", request("npc", 1234));
		send(plugin, "bestgearsetup", "lookup", request());
		send(plugin, "bestgearsetup", "lookup", request("npc", null));
		send(plugin, "bestgearsetup", "lookup", request("npc", "1234"));
		send(plugin, "bestgearsetup", "lookup", request("npc", 1234L));
		send(plugin, "bestgearsetup", "lookup", request("npc", 1234, "name", 7));
		send(plugin, "bestgearsetup", "lookup", request("npc", 1234, "level", "50"));
		assertTrue(searched.isEmpty());
		assertEquals(0, invokes.get());
		verifyNoInteractions(toolbar);
	}

	@Test
	public void theLookUpOptionShowsOnlyWhileFieldGuideIsOnAndPostsTheItem()
	{
		BestGearSetupPlugin plugin = plugin();
		Plugin fieldGuide = new FieldGuideStandIn();
		Plugin shortestPath = new OtherPlugin();
		PluginManager manager = mock(PluginManager.class);
		when(manager.getPlugins()).thenReturn(Arrays.asList(shortestPath, fieldGuide));
		when(manager.isPluginActive(shortestPath)).thenReturn(true);
		plugin.pluginManager = manager;
		EventBus bus = mock(EventBus.class);
		plugin.eventBus = bus;
		Gear whip = new Gear();
		whip.setId(4151);
		whip.setName("abyssal whip");
		JPopupMenu menu = PanelInternals.itemMenu(whip, Slot.WEAPON, plugin);
		JMenuItem lookUp = item(menu, "Look up in Field Guide");
		assertNotNull(lookUp);

		// Installed but off (and another plugin on): hidden.
		open(menu);
		assertFalse(lookUp.isVisible());
		// Checked again each time the menu opens.
		when(manager.isPluginActive(fieldGuide)).thenReturn(true);
		open(menu);
		assertTrue(lookUp.isVisible());

		lookUp.doClick();
		ArgumentCaptor<Object> posted = ArgumentCaptor.forClass(Object.class);
		verify(bus).post(posted.capture());
		PluginMessage message = (PluginMessage) posted.getValue();
		assertEquals("fieldguide", message.getNamespace());
		assertEquals("lookup", message.getName());
		assertEquals(Collections.singletonMap("item", 4151), message.getData());

		// Not installed: hidden.
		when(manager.getPlugins()).thenReturn(Collections.singletonList(shortestPath));
		open(menu);
		assertFalse(lookUp.isVisible());
	}

	private static void open(JPopupMenu menu)
	{
		for (PopupMenuListener l : menu.getPopupMenuListeners())
		{
			l.popupMenuWillBecomeVisible(new PopupMenuEvent(menu));
		}
	}

	private static JMenuItem item(JPopupMenu menu, String text)
	{
		for (Component c : menu.getComponents())
		{
			if (c instanceof JMenuItem && text.equals(((JMenuItem) c).getText()))
			{
				return (JMenuItem) c;
			}
		}
		return null;
	}
}
