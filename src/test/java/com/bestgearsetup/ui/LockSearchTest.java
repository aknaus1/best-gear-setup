package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.components.IconTextField;
import org.junit.Test;

public class LockSearchTest
{
	private static final GameData DATA = load();

	private static GameData load()
	{
		try
		{
			return WikiGameData.get(new Gson()).gameData(new Gson());
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	@Test
	public void anyItemCanBeLockedFromTheGearView() throws Exception
	{
		List<GearItem> locked = new ArrayList<>();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override
			public GameData getGameData()
			{
				return DATA;
			}

			@Override
			public boolean owns(GearItem item)
			{
				return false;
			}

			@Override
			public void lockItem(GearItem item)
			{
				locked.add(item);
			}
		};
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			IconTextField search = (IconTextField) named(panel, "lockSearch");
			Container suggestions = (Container) named(panel, "lockSuggestions");
			search.setText("lightbear");
			JButton any = button(suggestions, "Lightbearer");
			assertNotNull("Any slot finds the ring", any);
			assertEquals("Lightbearer (Ring)", any.getText());

			panel.startLockSearch(Slot.RING);
			assertEquals("Ring", ((JComboBox<?>) named(panel, "lockSlot")).getSelectedItem());
			search.setText("lightbear");
			JButton ring = button(suggestions, "Lightbearer");
			assertEquals("The slot is implied once it is chosen", "Lightbearer", ring.getText());
			ring.doClick();
			assertEquals(1, locked.size());
			assertEquals(Slot.RING, locked.get(0).getSlot());
			assertEquals("", search.getText());

			panel.startLockSearch(Slot.HEAD);
			search.setText("lightbear");
			assertEquals("A ring isn't offered for the head slot", 0, suggestions.getComponentCount());
		});
	}

	@Test
	public void lockingAnExcludedItemRestoresIt()
	{
		GearItem lightbearer = DATA.searchItems("lightbearer", 1, Slot.RING).get(0);
		Map<String, Object> writes = new HashMap<>();
		Set<Integer> excluded = new LinkedHashSet<>(Collections.singleton(lightbearer.getId()));
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override
			public Set<Integer> getExcluded()
			{
				return new LinkedHashSet<>(excluded);
			}

			@Override
			public Map<Slot, SlotLock> getLocks()
			{
				return new EnumMap<>(Slot.class);
			}

			@Override
			public void setConfig(String key, Object value)
			{
				writes.put(key, value);
			}
		};
		plugin.lockItem(lightbearer);
		assertEquals("", writes.get("excluded"));
		assertEquals(SlotLock.format(Collections.singletonMap(Slot.RING, SlotLock.item(lightbearer.getId()))),
			writes.get("locks"));
	}

	@Test
	public void everySlotMenuOffersAnotherItem()
	{
		List<Slot> chosen = new ArrayList<>();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override
			public void chooseLockItem(Slot slot)
			{
				chosen.add(slot);
			}
		};
		// An empty, unlocked ring slot: before this, it could only be kept empty or filled.
		JPopupMenu menu = ItemMenus.create(null, Slot.RING, false, plugin);
		JMenuItem another = null;
		for (Component c : menu.getComponents())
		{
			if (c instanceof JMenuItem && ((JMenuItem) c).getText().equals("Lock ring to another item..."))
			{
				another = (JMenuItem) c;
			}
		}
		assertNotNull(another);
		another.doClick();
		assertEquals(Collections.singletonList(Slot.RING), chosen);
		assertTrue(DATA.searchItems("lightbearer", 8, Slot.HEAD).isEmpty());
	}

	private static Component named(Container root, String name)
	{
		for (Component c : root.getComponents())
		{
			if (name.equals(c.getName()))
			{
				return c;
			}
			if (c instanceof Container)
			{
				Component found = named((Container) c, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static JButton button(Container root, String prefix)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JButton && ((JButton) c).getText().startsWith(prefix))
			{
				return (JButton) c;
			}
			if (c instanceof Container)
			{
				JButton found = button((Container) c, prefix);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
