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

	@Test
	public void blockedAndOnlyRunesAreListedSeparatelyInRuneOrder() throws Exception
	{
		filter = RuneFilter.of(Arrays.asList("death", "blood"), Collections.singleton("mind"));
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			Container blocked = (Container) named(panel, "blockedRunesContent");
			Container only = (Container) named(panel, "onlyRunesContent");
			assertNotNull(label(blocked, "Blood rune"));
			assertNotNull(label(blocked, "Death rune"));
			assertNull(label(blocked, "Mind rune"));
			assertNotNull(label(only, "Mind rune"));
			assertTrue(labelIndex(blocked, "Death rune") < labelIndex(blocked, "Blood rune"));
			assertNotNull(label((Container) named(panel, "blockedRunesSection"), "Blocked runes (2)"));
			assertNotNull(label((Container) named(panel, "onlyRunesSection"), "Only these runes (1)"));
			button(blocked, "Clear blocked runes").doClick();
			assertEquals(Collections.singletonList("clearBlocked"), actions);
			assertNull(button(only, "Clear only these runes"));
		});
	}

	@Test
	public void removingARuneAllowsIt() throws Exception
	{
		filter = RuneFilter.of(Collections.singleton("blood"), Collections.emptySet());
		SwingUtilities.invokeAndWait(() ->
		{
			button((Container) named(panel(), "blockedRunesContent"), "×").doClick();
			assertEquals(Collections.singletonList("blood:ALLOW"), actions);
		});
	}

	@Test
	public void pickerExcludesListedRunesAndPicksApplyRules() throws Exception
	{
		filter = RuneFilter.of(Collections.singleton("blood"), Collections.singleton("fire"));
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			Container blockedPicker = (Container) named(panel, "blockedRunesPicker");
			Container onlyPicker = (Container) named(panel, "onlyRunesPicker");
			assertNull(named(blockedPicker, "blockedRunesPick:blood"));
			assertNotNull(named(blockedPicker, "blockedRunesPick:fire"));
			assertNull(named(onlyPicker, "onlyRunesPick:fire"));
			assertNotNull(named(onlyPicker, "onlyRunesPick:blood"));
			assertEquals(10, blockedPicker.getComponentCount());
			((JButton) named(panel, "blockedRunesPick:air")).doClick();
			((JButton) named(panel, "onlyRunesPick:mind")).doClick();
			assertEquals(Arrays.asList("air:BLOCK", "mind:FORCE"), actions);
			assertEquals("Block Air rune", ((JButton) named(panel, "blockedRunesPick:air")).getToolTipText());
			assertEquals("Only use Mind rune (with any others listed)",
				((JButton) named(panel, "onlyRunesPick:mind")).getToolTipText());
		});
	}

	@Test
	public void unknownNamesAreFlagged() throws Exception
	{
		filter = RuneFilter.of(Collections.singleton("nonsense"), Collections.emptySet());
		SwingUtilities.invokeAndWait(() ->
			assertNotNull(label((Container) named(panel(), "blockedRunesContent"), "not a base rune")));
	}

	@Test
	public void emptyCardsSayNoneAndOfferEveryRune() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			assertNotNull(label((Container) named(panel, "blockedRunesContent"), "No blocked runes"));
			assertNotNull(label((Container) named(panel, "onlyRunesContent"), "No limit: any rune may be used"));
			assertEquals(11, ((Container) named(panel, "blockedRunesPicker")).getComponentCount());
			assertEquals(11, ((Container) named(panel, "onlyRunesPicker")).getComponentCount());
		});
	}

	@Test
	public void fullyListedCardHidesItsPickerAndCaption() throws Exception
	{
		filter = RuneFilter.of(Arrays.asList("air", "water", "earth", "fire", "mind", "chaos",
			"death", "blood", "soul", "wrath", "cosmic"), Collections.emptySet());
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = panel();
			Container picker = (Container) named(panel, "blockedRunesPicker");
			assertEquals(0, picker.getComponentCount());
			assertTrue(!picker.isVisible());
			JLabel caption = label((Container) named(panel, "blockedRunesContent"), "Block a rune");
			assertTrue(caption == null || !caption.getParent().isVisible());
		});
	}

	private static int labelIndex(Container root, String text)
	{
		List<String> texts = new ArrayList<>();
		collect(root, texts);
		return texts.indexOf(text);
	}

	private static void collect(Container root, List<String> texts)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof JLabel && ((JLabel) child).getText() != null)
			{
				texts.add(((JLabel) child).getText());
			}
			if (child instanceof Container)
			{
				collect((Container) child, texts);
			}
		}
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

	private static JLabel label(Container root, String text)
	{
		if (root == null)
		{
			return null;
		}
		for (Component child : root.getComponents())
		{
			if (child instanceof JLabel && text.equals(((JLabel) child).getText()))
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
