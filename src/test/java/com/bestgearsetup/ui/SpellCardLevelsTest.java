package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.ConfigItemsForTests;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.calc.SpellFilter;
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

public class SpellCardLevelsTest
{
	private SpellFilter filter = SpellFilter.NONE;
	private List<Spell> spells = Arrays.asList(
		spell("wind strike", "standard", 1),
		spell("fire bolt", "standard", 35),
		spell("ice barrage", "ancient", 94));
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

	@Test
	public void rowsFollowCombatSpellOrderAndKeepUnknownNamesLast() throws Exception
	{
		List<String> stored = Arrays.asList("ice barrage", "unknown one", "fire bolt", "unknown two", "wind strike");
		filter = SpellFilter.of(stored, Collections.emptyList());
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			panel.refresh();
			Container content = (Container) named(panel, "blockedSpellsContent");
			assertEquals(Arrays.asList("Wind Strike", "Fire Bolt", "Ice Barrage", "Unknown One", "Unknown Two"),
				rowTitles(content));
			assertEquals(stored, new ArrayList<>(filter.getBlocked()));
			filter = SpellFilter.of(Collections.emptyList(), stored);
			panel.refresh();
			assertEquals(Arrays.asList("Wind Strike", "Fire Bolt", "Ice Barrage", "Unknown One", "Unknown Two"),
				rowTitles((Container) named(panel, "forcedSpellsContent")));
			assertEquals(stored, new ArrayList<>(filter.getForced()));
		});
	}

	@Test
	public void rowsShowLevelAndSpellbookWithFallbacks() throws Exception
	{
		spells = Arrays.asList(spell("fire bolt", "standard", 35),
			spell("wind strike", null, 13), spell("ice barrage", "ancient", 0));
		filter = SpellFilter.of(Collections.emptyList(), Arrays.asList("fire bolt", "wind strike", "ice barrage", "stale"));
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			panel.refresh();
			Container content = (Container) named(panel, "forcedSpellsContent");
			assertNotNull(label(content, "lvl 35, Standard"));
			assertNotNull(label(content, "lvl 13, unknown spellbook"));
			assertNotNull(label(content, "Ancient"));
			assertNotNull(label(content, "not in game data"));
		});
	}

	@Test
	public void suggestionsShowLevelsInCombatSpellOrder() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ConstraintsPanel panel = new ConstraintsPanel(plugin);
			panel.refresh();
			List<String> expected = Arrays.asList("Wind Strike (lvl 1, Standard)",
				"Fire Bolt (lvl 35, Standard)", "Ice Barrage (lvl 94, Ancient)");
			assertEquals(expected, buttons((Container) named(panel, "blockedSpellsSuggestions")));
			assertEquals(expected, buttons((Container) named(panel, "forcedSpellsSuggestions")));
		});
	}

	private static Spell spell(String name, String book, int level)
	{
		Spell spell = new Spell();
		spell.setName(name);
		spell.setSpellbook(book);
		spell.setLevel(level);
		return spell;
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

	private static List<String> rowTitles(Container content)
	{
		List<String> titles = new ArrayList<>();
		for (Component child : content.getComponents())
		{
			if (child instanceof JLabel)
			{
				String text = ((JLabel) child).getText();
				if (Arrays.asList("Wind Strike", "Fire Bolt", "Ice Barrage", "Unknown One", "Unknown Two").contains(text))
				{
					titles.add(text);
				}
			}
			if (child instanceof Container)
			{
				titles.addAll(rowTitles((Container) child));
			}
		}
		return titles;
	}

	private static List<String> buttons(Container root)
	{
		List<String> texts = new ArrayList<>();
		for (Component child : root.getComponents())
		{
			if (child instanceof JButton)
			{
				texts.add(((JButton) child).getText());
			}
			if (child instanceof Container)
			{
				texts.addAll(buttons((Container) child));
			}
		}
		return texts;
	}
}
