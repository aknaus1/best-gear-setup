package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.ConfigItemsForTests;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.calc.SpellFilter;
import com.bestgearsetup.calc.SpellRule;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Spell;
import com.bestgearsetup.data.Slot;
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

public class SpellRulesCardsTest
{
	private SpellFilter filter = SpellFilter.NONE;
	private final List<String> actions = new ArrayList<>();
	private final List<Spell> spells = Arrays.asList(spell("ice barrage", "ancient"),
		spell("fire bolt", "standard"), spell("wind strike", "standard"));
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
			actions.add(name + ":" + rule);
		}

		@Override
		public void clearBlockedSpells()
		{
			actions.add("clearBlocked");
		}

		@Override
		public void clearForcedSpells()
		{
			actions.add("clearForced");
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

	private static Spell spell(String name, String book)
	{
		Spell spell = new Spell();
		spell.setName(name);
		spell.setSpellbook(book);
		return spell;
	}

	@Test
	public void blockedAndForcedSpellsAreListedSeparately() throws Exception
	{
		filter = SpellFilter.of(Arrays.asList("ice barrage", "fire bolt"), Collections.singleton("wind strike"));
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			panel.refresh();
			Container blocked = (Container) named(panel, "blockedSpellsContent");
			Container forced = (Container) named(panel, "forcedSpellsContent");
			assertNotNull(label(blocked, "Ice Barrage"));
			assertNotNull(label(blocked, "Fire Bolt"));
			assertNull(label(blocked, "Wind Strike"));
			assertNotNull(label(forced, "Wind Strike"));
			assertEquals("Blocked spells (2)", label((Container) named(panel, "blockedSpellsSection"), "Blocked spells (2)").getText());
			assertEquals("Forced spells (1)", label((Container) named(panel, "forcedSpellsSection"), "Forced spells (1)").getText());
			button(blocked, "Clear blocked spells").doClick();
			assertEquals(Collections.singletonList("clearBlocked"), actions);
			assertNull(button(forced, "Clear forced spells"));
		});
	}

	@Test
	public void removingASpellAllowsIt() throws Exception
	{
		filter = SpellFilter.of(Collections.singleton("fire bolt"), Collections.emptySet());
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			panel.refresh();
			button((Container) named(panel, "blockedSpellsContent"), "×").doClick();
			assertEquals(Collections.singletonList("fire bolt:ALLOW"), actions);
		});
	}

	@Test
	public void unknownNamesAreFlagged() throws Exception
	{
		filter = SpellFilter.of(Collections.emptySet(), Collections.singleton("not a spell"));
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			panel.refresh();
			assertNotNull(label((Container) named(panel, "forcedSpellsContent"), "not in game data"));
		});
	}

	@Test
	public void emptyCardsSayNone() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			panel.refresh();
			assertNotNull(label((Container) named(panel, "blockedSpellsContent"), "No blocked spells"));
			assertNotNull(label((Container) named(panel, "forcedSpellsContent"), "No forced spells"));
			assertNotNull(label((Container) named(panel, "blockedSpellsSection"), "Blocked spells"));
			assertNotNull(label((Container) named(panel, "forcedSpellsSection"), "Forced spells"));
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
