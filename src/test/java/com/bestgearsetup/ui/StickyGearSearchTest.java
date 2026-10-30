package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.calc.SpellFilter;
import com.bestgearsetup.calc.SpellRule;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import com.bestgearsetup.data.WikiGameData;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

/** The Gear view's rule searches keep their text after a pick, so several matches can be added in a row. */
public class StickyGearSearchTest
{
	private static final GameData DATA = load();
	private static final GearItem BERSERKER = DATA.searchItems("berserker ring", 2, Slot.RING).get(0);
	private static final GearItem BERSERKER_I = DATA.searchItems("berserker ring", 2, Slot.RING).get(1);
	private static final GearItem LIGHTBEARER = DATA.searchItems("lightbearer", 1, Slot.RING).get(0);
	private static final String BERSERKER_BUTTON = titled(BERSERKER) + " (Ring)";
	private static final String BERSERKER_I_BUTTON = titled(BERSERKER_I) + " (Ring)";

	private final Map<Slot, SlotLock> locks = new EnumMap<>(Slot.class);
	private final Set<Integer> excluded = new LinkedHashSet<>();
	private final Set<Integer> manual = new LinkedHashSet<>();
	private final List<GearItem> lockCalls = new ArrayList<>();
	private SpellFilter filter = SpellFilter.NONE;
	private final List<Spell> spells = Arrays.asList(spell("fire bolt"), spell("fire strike"), spell("fire blast"),
		spell("wind strike"));
	private final OwnedItems ownedItems = ownedItems();
	private final BestGearSetupPlugin plugin = new BestGearSetupPlugin()
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
		public Map<Slot, SlotLock> getLocks()
		{
			return new EnumMap<>(locks);
		}

		@Override
		public void lockItem(GearItem item)
		{
			lockCalls.add(item);
			excluded.remove(item.getId());
			locks.put(item.getSlot(), SlotLock.item(item.getId()));
		}

		@Override
		public Set<Integer> getExcluded()
		{
			return new LinkedHashSet<>(excluded);
		}

		@Override
		public void setExcluded(int itemId, boolean exclude)
		{
			if (exclude)
			{
				excluded.add(itemId);
			}
			else
			{
				excluded.remove(itemId);
			}
		}

		@Override
		public OwnedItems getOwnedItems()
		{
			return ownedItems;
		}

		@Override
		public void setManualOwned(int itemId, boolean owned)
		{
			if (owned)
			{
				manual.add(itemId);
			}
			else
			{
				manual.remove(itemId);
			}
		}

		@Override
		public SpellFilter getSpellFilter()
		{
			return filter;
		}

		@Override
		public List<Spell> getCombatSpells()
		{
			return spells;
		}

		@Override
		public void setSpellRule(String name, SpellRule rule)
		{
			filter = filter.withRule(name, rule);
		}
	};

	private static GameData load()
	{
		try
		{
			return WikiGameData.get(RuneLiteAPI.GSON).gameData(RuneLiteAPI.GSON);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	private OwnedItems ownedItems()
	{
		OwnedItems owned = mock(OwnedItems.class);
		when(owned.getManual()).thenAnswer(call -> new LinkedHashSet<>(manual));
		return owned;
	}

	private static Spell spell(String name)
	{
		Spell spell = new Spell();
		spell.setName(name);
		spell.setSpellbook("standard");
		return spell;
	}

	// S1: the text stays after a pick

	@Test
	public void lockSearchKeepsItsTextAfterAPick() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			IconTextField search = field(panel, "lockSearch");
			Container suggestions = container(panel, "lockSuggestions");
			search.setText("berserker ring");
			exact(suggestions, BERSERKER_BUTTON).doClick();
			assertEquals(List.of(BERSERKER), lockCalls);
			assertEquals("berserker ring", search.getText());
			assertNotNull("Other matches are still listed", exact(suggestions, BERSERKER_I_BUTTON));
		});
	}

	@Test
	public void excludeSearchKeepsItsTextAfterAPick() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			IconTextField search = field(panel, "excludeSearch");
			Container suggestions = container(panel, "excludeSuggestions");
			search.setText("berserker ring");
			exact(suggestions, BERSERKER_BUTTON).doClick();
			assertTrue(excluded.contains(BERSERKER.getId()));
			assertEquals("berserker ring", search.getText());
			assertNotNull("Other matches are still listed", exact(suggestions, BERSERKER_I_BUTTON));
		});
	}

	@Test
	public void ownedSearchKeepsItsTextAfterAPick() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			IconTextField search = field(panel, "ownedSearch");
			Container suggestions = container(panel, "ownedSuggestions");
			search.setText("berserker ring");
			exact(suggestions, titled(BERSERKER)).doClick();
			assertTrue(manual.contains(BERSERKER.getId()));
			assertEquals("berserker ring", search.getText());
			assertNotNull("Other matches are still listed", exact(suggestions, titled(BERSERKER_I)));
		});
	}

	@Test
	public void allowedSpellsSearchKeepsItsTextAfterAPick() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			IconTextField search = field(panel, "forcedSpellsSearch");
			Container suggestions = container(panel, "forcedSpellsSuggestions");
			search.setText("fire");
			button(suggestions, "Fire Bolt (").doClick();
			assertTrue(filter.getForced().contains("fire bolt"));
			assertEquals("fire", search.getText());
			assertNotNull("Other matches are still listed", button(suggestions, "Fire Strike ("));
		});
	}

	@Test
	public void excludedSpellsSearchKeepsItsTextAfterAPick() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			IconTextField search = field(panel, "blockedSpellsSearch");
			Container suggestions = container(panel, "blockedSpellsSuggestions");
			search.setText("fire");
			button(suggestions, "Fire Bolt (").doClick();
			assertTrue(filter.getBlocked().contains("fire bolt"));
			assertEquals("fire", search.getText());
			assertNotNull("Other matches are still listed", button(suggestions, "Fire Strike ("));
		});
	}

	// S2: the list no longer offers what was added

	@Test
	public void lockSuggestionsDropTheItemJustLocked() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			field(panel, "lockSearch").setText("berserker ring");
			Container suggestions = container(panel, "lockSuggestions");
			exact(suggestions, BERSERKER_BUTTON).doClick();
			assertNull("The locked item isn't offered again", exact(suggestions, BERSERKER_BUTTON));
			assertNotNull("Other matches stay listed", exact(suggestions, BERSERKER_I_BUTTON));
		});
	}

	@Test
	public void lockSuggestionsDropAnItemLockedBefore() throws Exception
	{
		locks.put(Slot.RING, SlotLock.item(BERSERKER.getId()));
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			Container suggestions = container(panel, "lockSuggestions");
			field(panel, "lockSearch").setText("berserker ring");
			assertNull("The item already locked in its slot isn't offered", exact(suggestions, BERSERKER_BUTTON));
			assertNotNull("Other matches stay listed", exact(suggestions, BERSERKER_I_BUTTON));

			panel.startLockSearch(Slot.RING);
			field(panel, "lockSearch").setText("berserker ring");
			assertNull(exact(suggestions, titled(BERSERKER)));
			assertNotNull("Other matches stay listed", exact(suggestions, titled(BERSERKER_I)));
		});
	}

	@Test
	public void keepEmptyAndFillLocksDoNotHideItems() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			Container suggestions = container(panel, "lockSuggestions");
			for (SlotLock lock : new SlotLock[]{SlotLock.empty(), SlotLock.fill()})
			{
				locks.put(Slot.RING, lock);
				field(panel, "lockSearch").setText("");
				field(panel, "lockSearch").setText("berserker ring");
				assertNotNull("Other matches stay listed", exact(suggestions, BERSERKER_BUTTON));
				assertNotNull("Other matches stay listed", exact(suggestions, BERSERKER_I_BUTTON));
			}
		});
	}

	@Test
	public void excludeSuggestionsDropTheItemJustExcluded() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			field(panel, "excludeSearch").setText("berserker ring");
			Container suggestions = container(panel, "excludeSuggestions");
			exact(suggestions, BERSERKER_BUTTON).doClick();
			assertNull("The excluded item isn't offered again", exact(suggestions, BERSERKER_BUTTON));
			assertNotNull("Other matches stay listed", exact(suggestions, BERSERKER_I_BUTTON));
		});
	}

	@Test
	public void ownedSuggestionsDropTheItemJustMarked() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			field(panel, "ownedSearch").setText("berserker ring");
			Container suggestions = container(panel, "ownedSuggestions");
			exact(suggestions, titled(BERSERKER)).doClick();
			assertNull("The item marked as owned isn't offered again", exact(suggestions, titled(BERSERKER)));
			assertNotNull("Other matches stay listed", exact(suggestions, titled(BERSERKER_I)));
		});
	}

	@Test
	public void ownedSuggestionsDropItemsMarkedBefore() throws Exception
	{
		manual.add(BERSERKER.getId());
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			Container suggestions = container(panel, "ownedSuggestions");
			field(panel, "ownedSearch").setText("berserker ring");
			assertNull(exact(suggestions, titled(BERSERKER)));
			assertNotNull("Other matches stay listed", exact(suggestions, titled(BERSERKER_I)));
		});
	}

	@Test
	public void allowedSpellSuggestionsDropTheSpellJustAllowed() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			field(panel, "forcedSpellsSearch").setText("fire");
			Container suggestions = container(panel, "forcedSpellsSuggestions");
			button(suggestions, "Fire Bolt (").doClick();
			assertNull("The allowed spell isn't offered again", button(suggestions, "Fire Bolt ("));
			assertNotNull("Other matches stay listed", button(suggestions, "Fire Strike ("));
			assertNotNull("Other matches stay listed", button(suggestions, "Fire Blast ("));
		});
	}

	@Test
	public void excludedSpellSuggestionsDropTheSpellJustExcluded() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			field(panel, "blockedSpellsSearch").setText("fire");
			Container suggestions = container(panel, "blockedSpellsSuggestions");
			button(suggestions, "Fire Bolt (").doClick();
			assertNull("The excluded spell isn't offered again", button(suggestions, "Fire Bolt ("));
			assertNotNull("Other matches stay listed", button(suggestions, "Fire Strike ("));
			assertNotNull("Other matches stay listed", button(suggestions, "Fire Blast ("));
		});
	}

	@Test
	public void refreshRequeriesTheExcludeSuggestions() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			Container suggestions = container(panel, "excludeSuggestions");
			field(panel, "excludeSearch").setText("berserker ring");
			assertNotNull("Other matches stay listed", exact(suggestions, BERSERKER_BUTTON));
			excluded.add(BERSERKER.getId());
			panel.refresh();
			assertNull("An exclusion made elsewhere is no longer offered", exact(suggestions, BERSERKER_BUTTON));
			assertNotNull("Other matches stay listed", exact(suggestions, BERSERKER_I_BUTTON));
			excluded.clear();
			panel.refresh();
			assertNotNull("Removing the exclusion offers it again", exact(suggestions, BERSERKER_BUTTON));
		});
	}

	@Test
	public void refreshRequeriesTheLockSuggestions() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			Container suggestions = container(panel, "lockSuggestions");
			field(panel, "lockSearch").setText("berserker ring");
			assertNotNull("Other matches stay listed", exact(suggestions, BERSERKER_BUTTON));
			locks.put(Slot.RING, SlotLock.item(BERSERKER.getId()));
			panel.refresh();
			assertNull("A lock made elsewhere hides the item", exact(suggestions, BERSERKER_BUTTON));
			assertNotNull("Other matches stay listed", exact(suggestions, BERSERKER_I_BUTTON));
			locks.clear();
			panel.refresh();
			assertNotNull("Unlocking offers it again", exact(suggestions, BERSERKER_BUTTON));
		});
	}

	@Test
	public void refreshRequeriesTheOwnedSuggestions() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			Container suggestions = container(panel, "ownedSuggestions");
			field(panel, "ownedSearch").setText("berserker ring");
			assertNotNull("Other matches stay listed", exact(suggestions, titled(BERSERKER)));
			manual.add(BERSERKER.getId());
			panel.refresh();
			assertNull("An item marked elsewhere is no longer offered", exact(suggestions, titled(BERSERKER)));
			assertNotNull("Other matches stay listed", exact(suggestions, titled(BERSERKER_I)));
		});
	}

	// S3: replacing a lock says so

	@Test
	public void lockTooltipOffersToLockAnUnlockedSlot() throws Exception
	{
		locks.put(Slot.HEAD, SlotLock.empty());
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			field(panel, "lockSearch").setText("lightbearer");
			assertEquals("Lock ring slot to Lightbearer",
				button(container(panel, "lockSuggestions"), "Lightbearer").getToolTipText());
		});
	}

	@Test
	public void lockTooltipNamesTheItemBeingReplaced() throws Exception
	{
		locks.put(Slot.RING, SlotLock.item(BERSERKER.getId()));
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			Container suggestions = container(panel, "lockSuggestions");
			field(panel, "lockSearch").setText("lightbearer");
			assertEquals("Replace ring lock (Berserker Ring) with Lightbearer",
				button(suggestions, "Lightbearer").getToolTipText());

			panel.startLockSearch(Slot.RING);
			field(panel, "lockSearch").setText("lightbearer");
			assertEquals("Replace ring lock (Berserker Ring) with Lightbearer",
				button(suggestions, "Lightbearer").getToolTipText());
		});
	}

	@Test
	public void lockTooltipNamesAKeepEmptyLock() throws Exception
	{
		locks.put(Slot.RING, SlotLock.empty());
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			field(panel, "lockSearch").setText("lightbearer");
			assertEquals("Replace ring lock (Keep empty) with Lightbearer",
				button(container(panel, "lockSuggestions"), "Lightbearer").getToolTipText());
		});
	}

	@Test
	public void lockTooltipNamesAnAlwaysFillLock() throws Exception
	{
		locks.put(Slot.RING, SlotLock.fill());
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			field(panel, "lockSearch").setText("lightbearer");
			assertEquals("Replace ring lock (Always fill) with Lightbearer",
				button(container(panel, "lockSuggestions"), "Lightbearer").getToolTipText());
		});
	}

	@Test
	public void replacingALockStillLocksTheNewItem() throws Exception
	{
		locks.put(Slot.RING, SlotLock.item(BERSERKER.getId()));
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			field(panel, "lockSearch").setText("lightbearer");
			button(container(panel, "lockSuggestions"), "Lightbearer").doClick();
			assertEquals(List.of(LIGHTBEARER), lockCalls);
			assertEquals(SlotLock.item(LIGHTBEARER.getId()), locks.get(Slot.RING));
		});
	}

	// S5: Escape clears the field

	@Test
	public void escapeClearsTheLockSearch() throws Exception
	{
		escapeClears("lockSearch", "lockSuggestions", "lightbear");
	}

	@Test
	public void escapeClearsTheExcludeSearch() throws Exception
	{
		escapeClears("excludeSearch", "excludeSuggestions", "lightbear");
	}

	@Test
	public void escapeClearsTheOwnedSearch() throws Exception
	{
		escapeClears("ownedSearch", "ownedSuggestions", "lightbear");
	}

	@Test
	public void escapeClearsTheAllowedSpellsSearch() throws Exception
	{
		escapeClears("forcedSpellsSearch", "forcedSpellsSuggestions", "fire");
	}

	@Test
	public void escapeClearsTheExcludedSpellsSearch() throws Exception
	{
		escapeClears("blockedSpellsSearch", "blockedSpellsSuggestions", "fire");
	}

	private void escapeClears(String searchName, String suggestionsName, String text) throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			IconTextField search = field(panel, searchName);
			Container suggestions = container(panel, suggestionsName);
			search.setText(text);
			assertTrue("There is something to clear", suggestions.getComponentCount() > 0);
			JTextComponent inner = textComponent(search);
			assertNotNull("The field has a text component", inner);
			KeyEvent escape = new KeyEvent(inner, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
				KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
			// Not dispatched: a field that isn't showing never gets key events from the focus manager.
			for (KeyListener listener : inner.getKeyListeners())
			{
				listener.keyPressed(escape);
			}
			assertEquals("", search.getText());
			assertEquals("The suggestions empty with the text", 0, suggestions.getComponentCount());
		});
	}

	// S6: starting a lock search

	@Test
	public void startingALockSearchSelectsTheSlotAndClearsTheText() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			IconTextField search = field(panel, "lockSearch");
			Container suggestions = container(panel, "lockSuggestions");
			search.setText("lightbear");
			assertEquals(1, suggestions.getComponentCount());
			panel.startLockSearch(Slot.RING);
			assertEquals("Ring", ((JComboBox<?>) named(panel, "lockSlot")).getSelectedItem());
			assertEquals("", search.getText());
			assertEquals(0, suggestions.getComponentCount());
		});
	}

	@Test
	public void changingTheLockSlotRequeriesTheSuggestions() throws Exception
	{
		run(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			JComboBox<?> slot = (JComboBox<?>) named(panel, "lockSlot");
			Container suggestions = container(panel, "lockSuggestions");
			field(panel, "lockSearch").setText("lightbear");
			assertEquals("Lightbearer (Ring)", button(suggestions, "Lightbearer").getText());
			slot.setSelectedItem("Head");
			assertEquals("A ring isn't offered for the head slot", 0, suggestions.getComponentCount());
			slot.setSelectedItem("Ring");
			assertEquals("Lightbearer", button(suggestions, "Lightbearer").getText());
		});
	}

	private static String titled(GearItem item)
	{
		return GameData.titleCase(item.getName());
	}

	/** Run on the event thread, rethrowing an assertion failure as itself. */
	private static void run(Runnable action) throws Exception
	{
		try
		{
			SwingUtilities.invokeAndWait(action);
		}
		catch (InvocationTargetException e)
		{
			if (e.getCause() instanceof Error)
			{
				throw (Error) e.getCause();
			}
			throw e;
		}
	}

	private static IconTextField field(Container root, String name)
	{
		return (IconTextField) named(root, name);
	}

	private static Container container(Container root, String name)
	{
		return (Container) named(root, name);
	}

	private static JTextComponent textComponent(Container root)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JTextComponent)
			{
				return (JTextComponent) c;
			}
			if (c instanceof Container)
			{
				JTextComponent found = textComponent((Container) c);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
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

	private static JButton exact(Container root, String text)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JButton && ((JButton) c).getText().equals(text))
			{
				return (JButton) c;
			}
			if (c instanceof Container)
			{
				JButton found = exact((Container) c, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
