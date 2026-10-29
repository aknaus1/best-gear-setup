package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.RuneUse;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.calc.CombatContext;
import com.bestgearsetup.calc.CombatModifiers;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.SupportSpell;
import com.bestgearsetup.calc.Thrall;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.awt.Component;
import java.util.Collections;
import java.util.EnumSet;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class ResultViewTest
{
	@Test
	public void runesRowListsExpectedCountsPerKill() throws Exception
	{
		SetupResult setup = setup("ice barrage", "kodai wand", 4, Collections.emptySet());
		RuneUse runes = RuneUse.of(setup, 200, 99, 99);
		SwingUtilities.invokeAndWait(() ->
		{
			JPanel box = view(200).statsBox(setup.getDps(), null, runes, false, false);
			assertEquals("<html>Runes/kill: 56.7 death, 28.3 blood</html>", named(box, "runesPerKill").getText());
			assertNull(named(box, "supportRunesPerKill"));
			assertTrue(named(box, "runesPerKill").getToolTipText().contains("expected"));
			assertTrue(named(box, "runesPerKill").getToolTipText().contains("averaged over their duration"));
		});
	}

	@Test
	public void supportRowIsSeparate() throws Exception
	{
		SetupResult setup = setup(null, "abyssal whip", 2, EnumSet.of(SupportSpell.THRALL));
		RuneUse runes = RuneUse.of(setup, 100, 99, 99);
		SwingUtilities.invokeAndWait(() ->
		{
			JPanel box = view(100).statsBox(setup.getDps(), Thrall.GREATER, runes, false, false);
			assertEquals("<html>Support/kill: 8.4 fire, 4.2 blood, 0.8 cosmic</html>",
				named(box, "supportRunesPerKill").getText());
			assertNull(named(box, "runesPerKill"));
			assertTrue(named(box, "supportRunesPerKill").getToolTipText()
				.contains("without the Master Combat Achievements reward"));
		});
	}

	@Test
	public void tinyCountsShowAsLessThanATenth() throws Exception
	{
		SetupResult setup = setup("dark demonbane", "purging staff", 5,
			EnumSet.of(SupportSpell.MARK_OF_DARKNESS));
		RuneUse runes = RuneUse.of(setup, 200, 99, 99);
		SwingUtilities.invokeAndWait(() -> assertEquals("<html>Support/kill: <0.1 soul, <0.1 cosmic</html>",
			named(view(200).statsBox(setup.getDps(), null, runes, false, false), "supportRunesPerKill").getText()));
	}

	@Test
	public void sunfireLabelsFire() throws Exception
	{
		SetupResult setup = setup("fire surge", "magic staff", 5, Collections.emptySet());
		RuneUse runes = RuneUse.of(setup, 200, 99, 99);
		SwingUtilities.invokeAndWait(() -> assertTrue(named(view(200).statsBox(setup.getDps(), null,
			runes, true, false), "runesPerKill").getText().contains("fire/sunfire")));
	}

	@Test
	public void poweredStaffSaysChargesAreNotCounted() throws Exception
	{
		DpsResult dps = dps(5);
		SwingUtilities.invokeAndWait(() -> assertEquals("<html>Runes/kill: uses charges, not counted</html>",
			named(view(100).statsBox(dps, null, RuneUse.of(null, 100, 99, 99), false, true),
				"runesPerKill").getText()));
	}

	@Test
	public void noRowsWithoutRunes() throws Exception
	{
		DpsResult dps = dps(5);
		SwingUtilities.invokeAndWait(() ->
		{
			ResultView view = view(100);
			assertNull(named(view.statsBox(dps, null), "runesPerKill"));
			assertNull(named(view.statsBox(dps, null), "supportRunesPerKill"));
			JPanel empty = view.statsBox(dps, null, RuneUse.of(null, 100, 99, 99), false, false);
			assertNull(named(empty, "runesPerKill"));
			assertNull(named(empty, "supportRunesPerKill"));
		});
	}

	private static ResultView view(int hp)
	{
		Monster monster = new Monster();
		monster.setHitpoints(hp);
		SearchResults results = new SearchResults(monster, hp, Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), false);
		return new ResultView(results, null, null, null, null, 0);
	}

	private static SetupResult setup(String spellName, String weapon, double dps, java.util.Set<SupportSpell> support)
	{
		Loadout loadout = new Loadout();
		GearItem item = new GearItem();
		item.setName(weapon);
		loadout.set(Slot.WEAPON, item);
		if (spellName != null)
		{
			Spell spell = new Spell();
			spell.setName(spellName);
			loadout.setSpell(spell);
		}
		return new SetupResult(spellName == null ? CombatClass.MELEE : CombatClass.MAGIC,
			loadout, dps(dps), 0, 0, Collections.emptySet(), support, null,
			support.contains(SupportSpell.THRALL) ? Thrall.GREATER : null);
	}

	private static DpsResult dps(double value)
	{
		return new DpsResult(value, 10, 0.5, 5, 4.8, null, value, 1, 5);
	}

	private static JLabel named(JPanel box, String name)
	{
		for (Component component : box.getComponents())
		{
			if (component instanceof JLabel && name.equals(component.getName()))
			{
				return (JLabel) component;
			}
		}
		return null;
	}

	@Test
	public void killTimeUsesEffectiveStartingHitpoints() throws Exception
	{
		Monster monster = new Monster();
		monster.setHitpoints(750);
		for (int selected : new int[]{100, 0, 1000})
		{
			CombatContext ctx = new CombatContext(monster, PlayerLevels.maxed(), false, false, null)
				.withModifiers(CombatModifiers.builder().monsterHitpoints(selected).build());
			int starting = selected == 100 ? 100 : 750;
			assertEquals(starting, ctx.getTargetHitpoints());
			SearchResults results = new SearchResults(monster, ctx.getTargetHitpoints(), Collections.emptyMap(),
				Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false);
			SwingUtilities.invokeAndWait(() ->
			{
				ResultView view = new ResultView(results, null, null, null, null, 0);
				JPanel box = view.statsBox(new DpsResult(2, 10, 0.5, 4, 4.8, null), null);
				boolean found = false;
				for (Component component : box.getComponents())
				{
					if (component instanceof JLabel && ((JLabel) component).getText().contains("TTK:"))
					{
						assertEquals("<html>TTK: ~" + (starting / 2.0) + "s</html>", ((JLabel) component).getText());
						found = true;
					}
				}
				assertTrue(found);
				assertTrue("No thrall line unless the setup includes one", label(box, "Incl. thrall") == null);
				JPanel withThrall = view.statsBox(new DpsResult(2, 10, 0.5, 4, 4.8, null), Thrall.SUPERIOR);
				assertEquals("<html>Incl. thrall: +0.417</html>", label(withThrall, "Incl. thrall").getText());
			});
		}
	}

	private static JLabel label(JPanel box, String text)
	{
		for (Component component : box.getComponents())
		{
			if (component instanceof JLabel && ((JLabel) component).getText().contains(text))
			{
				return (JLabel) component;
			}
		}
		return null;
	}
}
