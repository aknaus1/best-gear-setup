package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.ConfigItemsForTests;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.calc.RuneFilter;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.calc.SpellFilter;
import com.bestgearsetup.calc.SpellRule;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.accessibility.AccessibleState;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class RuneRulesCardsTest
{
	private RuneFilter filter = RuneFilter.NONE;
	private final List<String> actions = new ArrayList<>();
	private final BestGearSetupPlugin plugin = new BestGearSetupPlugin()
	{
		@Override
		public GameData getGameData()
		{
			return null;
		}

		@Override
		public SpellFilter getSpellFilter()
		{
			return SpellFilter.NONE;
		}

		@Override
		public List<Spell> getCombatSpells()
		{
			return Collections.emptyList();
		}

		@Override
		public RuneFilter getRuneFilter()
		{
			return filter;
		}

		@Override
		public void setRuneRule(String name, SpellRule rule)
		{
			actions.add(name + ":" + rule);
		}

		@Override
		public void clearBlockedRunes()
		{
			actions.add("clearBlocked");
		}

		@Override
		public void clearOnlyRunes()
		{
			actions.add("clearOnly");
		}

		@Override
		public Map<Slot, SlotLock> getLocks()
		{
			return Collections.emptyMap();
		}

		@Override
		public Set<Integer> getExcluded()
		{
			return Collections.emptySet();
		}

		@Override
		public OwnedItems getOwnedItems()
		{
			return ConfigItemsForTests.emptyOwnedItems();
		}
	};

	private ConstraintsPanel panel()
	{
		ConstraintsPanel panel = new ConstraintsPanel(plugin);
		panel.refresh();
		return panel;
	}

	private static final List<String> RUNE_ORDER = Arrays.asList("air", "water", "earth", "fire", "mind", "chaos",
		"death", "blood", "soul", "wrath", "cosmic");

	private static RuneToggle rune(Container root, String key, String name)
	{
		return (RuneToggle) named(root, key + "Rune:" + name);
	}

	private static Container grid(Container root, String key)
	{
		return (Container) named(root, key + "Grid");
	}

	@Test
	public void eachGridHasElevenButtonsInRuneOrder() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			for (String key : Arrays.asList("blockedRunes", "onlyRunes"))
			{
				Component[] cells = grid(panel, key).getComponents();
				assertEquals(11, cells.length);
				for (int i = 0; i < 11; i++)
				{
					assertEquals(key + "Rune:" + RUNE_ORDER.get(i), cells[i].getName());
					assertTrue(cells[i] instanceof RuneToggle);
					assertTrue(cells[i].isFocusable());
				}
			}
			assertEquals("blockedRunesRune:air", grid(panel, "blockedRunes").getComponent(0).getName());
			assertEquals("onlyRunesRune:cosmic", grid(panel, "onlyRunes").getComponent(10).getName());
		});
	}

	@Test
	public void selectedStateMatchesTheFilterInEachCard() throws Exception
	{
		filter = RuneFilter.of(Arrays.asList("death", "blood"), Collections.singleton("air"));
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			for (String name : RUNE_ORDER)
			{
				assertEquals(name, name.equals("death") || name.equals("blood"), rune(panel, "blockedRunes", name).isSelected());
				assertEquals(name, name.equals("air"), rune(panel, "onlyRunes", name).isSelected());
			}
			// A rune listed in the other card is unselected here.
			assertTrue(!rune(panel, "onlyRunes", "blood").isSelected());
			assertTrue(!rune(panel, "blockedRunes", "air").isSelected());
		});
	}

	@Test
	public void dimmedStateFollowsWhatIsUsable() throws Exception
	{
		filter = RuneFilter.of(Collections.singleton("blood"), Collections.singleton("air"));
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			assertTrue(rune(panel, "blockedRunes", "blood").isDimmed());
			assertTrue(!rune(panel, "blockedRunes", "air").isDimmed());
			assertTrue(!rune(panel, "onlyRunes", "air").isDimmed());
			assertTrue(rune(panel, "onlyRunes", "mind").isDimmed());
		});
		filter = RuneFilter.NONE;
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			for (String name : RUNE_ORDER)
			{
				assertTrue(!rune(panel, "onlyRunes", name).isDimmed());
				assertTrue(!rune(panel, "blockedRunes", name).isDimmed());
			}
		});
	}

	@Test
	public void clickingTogglesTheRule() throws Exception
	{
		filter = RuneFilter.of(Collections.singleton("blood"), Collections.singleton("air"));
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			rune(panel, "blockedRunes", "blood").doClick();
			rune(panel, "blockedRunes", "air").doClick();
			rune(panel, "onlyRunes", "air").doClick();
			rune(panel, "onlyRunes", "mind").doClick();
			assertEquals(Arrays.asList("blood:ALLOW", "air:BLOCK", "air:ALLOW", "mind:FORCE"), actions);
		});
	}

	@Test
	public void tooltipsAndAccessibleNamesAreExact() throws Exception
	{
		filter = RuneFilter.of(Collections.singleton("blood"), Collections.singleton("air"));
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			assertTip(rune(panel, "blockedRunes", "blood"), "Blood rune: blocked. Click to allow.");
			assertTip(rune(panel, "blockedRunes", "air"), "Air rune. Click to block.");
			assertTip(rune(panel, "onlyRunes", "air"), "Air rune: in the list. Click to remove.");
			assertTip(rune(panel, "onlyRunes", "blood"), "Blood rune. Click to add to Only these runes.");
		});
	}

	private static void assertTip(RuneToggle toggle, String expected)
	{
		assertEquals(expected, toggle.getToolTipText());
		assertEquals(expected, toggle.getAccessibleContext().getAccessibleName());
		assertEquals(toggle.isSelected(),
			toggle.getAccessibleContext().getAccessibleStateSet().contains(AccessibleState.CHECKED));
	}

	@Test
	public void summaryListsNamesInRuneOrderAndFlagsUnknownOnes() throws Exception
	{
		filter = RuneFilter.of(Arrays.asList("death", "blood"), Arrays.asList("mind", "air"));
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			assertNotNull(label((Container) named(panel, "blockedRunesContent"), "Blocked: Death, Blood"));
			assertNotNull(label((Container) named(panel, "onlyRunesContent"), "Only: Air, Mind"));
		});
		filter = RuneFilter.of(Arrays.asList("zzz", "blood", "yyy"), Collections.emptySet());
		SwingUtilities.invokeAndWait(() ->
			assertNotNull(label((Container) named(panel(), "blockedRunesContent"),
				"Blocked: Blood, Zzz (not a base rune), Yyy (not a base rune)")));
	}

	@Test
	public void clearShowsForTwoRunesOrAnUnknownName() throws Exception
	{
		filter = RuneFilter.of(Arrays.asList("death", "blood"), Collections.singleton("air"));
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			assertNotNull(button((Container) named(panel, "blockedRunesContent"), "Clear blocked runes"));
			assertNull(button((Container) named(panel, "onlyRunesContent"), "Clear only these runes"));
			button((Container) named(panel, "blockedRunesContent"), "Clear blocked runes").doClick();
			assertEquals(Collections.singletonList("clearBlocked"), actions);
		});
		actions.clear();
		filter = RuneFilter.of(Collections.emptySet(), Collections.singleton("nonsense"));
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			assertNotNull(label((Container) named(panel, "onlyRunesContent"), "Only: Nonsense (not a base rune)"));
			button((Container) named(panel, "onlyRunesContent"), "Clear only these runes").doClick();
			assertEquals(Collections.singletonList("clearOnly"), actions);
		});
	}

	@Test
	public void emptyCardsShowEmptyTextsAndNoClear() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			assertNotNull(label((Container) named(panel, "blockedRunesContent"), "No blocked runes"));
			assertNotNull(label((Container) named(panel, "onlyRunesContent"), "No limit: any rune may be used"));
			assertNull(button((Container) named(panel, "blockedRunesContent"), "Clear"));
			assertNull(button((Container) named(panel, "onlyRunesContent"), "Clear"));
		});
	}

	@Test
	public void sectionTitlesShowCounts() throws Exception
	{
		filter = RuneFilter.of(Arrays.asList("death", "blood"), Collections.singleton("air"));
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			assertNotNull(label((Container) named(panel, "blockedRunesSection"), "Blocked runes (2)"));
			assertNotNull(label((Container) named(panel, "onlyRunesSection"), "Only these runes (1)"));
		});
	}

	private static Component named(Container root, String name)
	{
		for (Component child : root.getComponents())
		{
			if (name.equals(child.getName()))
			{
				return child;
			}
			if (child instanceof Container)
			{
				Component found = named((Container) child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Finds a label by its text, ignoring the html wrapper that wraps long text. */
	private static JLabel label(Container root, String text)
	{
		if (root == null)
		{
			return null;
		}
		for (Component child : root.getComponents())
		{
			if (child instanceof JLabel && text.equals(plain(((JLabel) child).getText())))
			{
				return (JLabel) child;
			}
			if (child instanceof Container)
			{
				JLabel found = label((Container) child, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static String plain(String text)
	{
		return text == null ? null : text.replaceAll("<[^>]*>", "");
	}

	private static JButton button(Container root, String text)
	{
		if (root == null)
		{
			return null;
		}
		for (Component child : root.getComponents())
		{
			if (child instanceof JButton && ((JButton) child).getText().startsWith(text))
			{
				return (JButton) child;
			}
			if (child instanceof Container)
			{
				JButton found = button((Container) child, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
