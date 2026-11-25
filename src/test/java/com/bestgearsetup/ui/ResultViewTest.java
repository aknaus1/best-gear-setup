package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.TestFactories;
import com.bestgearsetup.RuneUse;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.TestResults;
import com.bestgearsetup.calc.Style;
import com.bestgearsetup.calc.CombatContext;
import com.bestgearsetup.calc.CombatModifiers;
import com.bestgearsetup.calc.Dps;
import com.bestgearsetup.calc.Outfit;
import com.bestgearsetup.calc.Overheads;
import com.bestgearsetup.calc.Setup;
import com.bestgearsetup.calc.SupportSpell;
import com.bestgearsetup.calc.Thrall;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class ResultViewTest
{
	@org.junit.Before public void trackTips() { HoverTextLengthTest.trackTips(); }
	@org.junit.After public void tipsAreShort() throws Exception { HoverTextLengthTest.assertTrackedTipsShort(); }

	@Test
	public void runesRowListsExpectedCountsPerKill() throws Exception
	{
		Setup setup = setup("ice barrage", "kodai wand", 4, Collections.emptySet());
		RuneUse runes = RuneUse.of(setup, 200, 99, 99);
		SwingUtilities.invokeAndWait(() ->
		{
			JPanel box = view(200).statsBox(setup.getDps(), null, runes, false, false);
			assertEquals("<html>Runes/kill: 56.7 death, 28.3 blood</html>", named(box, "runesPerKill").getText());
			assertNull(named(box, "supportRunesPerKill"));
			assertTrue(named(box, "runesPerKill").getToolTipText().contains("Expected per kill"));
		});
	}

	@Test
	public void supportRowIsSeparate() throws Exception
	{
		Setup setup = setup(null, "abyssal whip", 2, EnumSet.of(SupportSpell.THRALL));
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
		Setup setup = setup("dark demonbane", "purging staff", 5,
			EnumSet.of(SupportSpell.MARK_OF_DARKNESS));
		RuneUse runes = RuneUse.of(setup, 200, 99, 99);
		SwingUtilities.invokeAndWait(() -> assertEquals("<html>Support/kill: <0.1 soul, <0.1 cosmic</html>",
			named(view(200).statsBox(setup.getDps(), null, runes, false, false), "supportRunesPerKill").getText()));
	}

	@Test
	public void sunfireLabelsFire() throws Exception
	{
		Setup setup = setup("fire surge", "magic staff", 5, Collections.emptySet());
		RuneUse runes = RuneUse.of(setup, 200, 99, 99);
		SwingUtilities.invokeAndWait(() -> assertTrue(named(view(200).statsBox(setup.getDps(), null,
			runes, true, false), "runesPerKill").getText().contains("fire/sunfire")));
	}

	@Test
	public void poweredStaffSaysChargesAreNotCounted() throws Exception
	{
		Dps dps = dps(5);
		SwingUtilities.invokeAndWait(() -> assertEquals("<html>Runes/kill: uses charges, not counted</html>",
			named(view(100).statsBox(dps, null, RuneUse.of(null, 100, 99, 99), false, true),
				"runesPerKill").getText()));
	}

	@Test
	public void noRowsWithoutRunes() throws Exception
	{
		Dps dps = dps(5);
		SwingUtilities.invokeAndWait(() ->
		{
			ResultView view = view(100);
			assertNull(named(view.statsBox(dps, null, null, false, false), "runesPerKill"));
			assertNull(named(view.statsBox(dps, null, null, false, false), "supportRunesPerKill"));
			JPanel empty = view.statsBox(dps, null, RuneUse.of(null, 100, 99, 99), false, false);
			assertNull(named(empty, "runesPerKill"));
			assertNull(named(empty, "supportRunesPerKill"));
		});
	}

	@Test
	public void tripLinesShowCombatTimePrayerAndSupplies() throws Exception
	{
		Outfit loadout = new Outfit();
		Setup setup = new Setup(Combat.MELEE, loadout, dps(5), 0, 0, java.util.Collections.emptySet(), java.util.Collections.emptySet(), null, null);
		java.util.List<com.bestgearsetup.TripSupplies.Supply> supplies = Collections.singletonList(
			new com.bestgearsetup.TripSupplies.Supply("Prayer potion", Collections.singletonList(2434), 3));
		SwingUtilities.invokeAndWait(() ->
		{
			JPanel box = view(100, "vorkath (post-quest)").statsBox(dps(5), null, null, false, false);
			view(100, "vorkath (post-quest)").addTripLines(box, setup, 30, supplies);
			// 30 kills of 100 HP at 5 DPS.
			assertEquals("<html>Trip combat: ~10m 00s</html>", named(box, "tripCombatTime").getText());
			assertTrue(named(box, "prayerPerKill").getText().contains("Prayer/kill: ~6.7 points"));
			assertTrue(named(box, "tripSupplies").getText().contains("Supplies: 3 Prayer potion"));
			// A rule opens the trip lines and another the overhead advice; lines within a group have none.
			assertNotNull(named(box, "tripCombatTime").getBorder());
			assertNull(named(box, "prayerPerKill").getBorder());
			assertNull(named(box, "tripSupplies").getBorder());
			assertNotNull(named(box, "protectionPrayer").getBorder());
			assertTrue(named(box, "protectionPrayer").getText().contains("Overhead: Protect from Magic</b>: "));
			JPanel single = view(100).statsBox(dps(5), null, null, false, false);
			view(100).addTripLines(single, setup, 1, Collections.emptyList());
			assertNull(named(single, "tripCombatTime"));
			assertNull(named(single, "tripSupplies"));
			// Raid targets show their overhead but no trip lines.
			Monster tekton = new Monster();
			tekton.setName("tekton (normal)");
			tekton.setHitpoints(100);
			Monster.Attribute xerician = new Monster.Attribute();
			xerician.setName("xerician");
			tekton.setAttributes(Collections.singletonList(xerician));
			ResultView raid = view(tekton);
			JPanel raidBox = raid.statsBox(dps(5), null, null, false, false);
			raid.addTripLines(raidBox, setup, 30, Collections.emptyList());
			assertNull(named(raidBox, "tripCombatTime"));
			assertNull(named(raidBox, "prayerPerKill"));
			// More supplies than one inventory holds get a warning.
			JPanel full = view(100, "vorkath (post-quest)").statsBox(dps(5), null, null, false, false);
			view(100, "vorkath (post-quest)").addTripLines(full, setup, 30, Collections.singletonList(
				new com.bestgearsetup.TripSupplies.Supply("Prayer potion", Collections.singletonList(2434), 29)));
			assertNotNull(named(full, "tripOverflow"));
		});
		assertEquals("1h 05m", ResultView.duration(3900));
	}

	@Test
	public void theStatsBoxOpensOnItsDpsLineAndTogglesOnAClick() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ResultView.statsOpen = false;
			ResultView view = view(100, "vorkath (post-quest)");
			JPanel box = view.statsBox(dps(5), null, null, false, false);
			view.addTripLines(box, new Setup(Combat.MELEE, new Outfit(), dps(5), 0, 0, Collections.emptySet(),
				Collections.emptySet(), null, null), 1, Collections.emptyList());
			ResultView.collapsible(box);
			JLabel head = named(box, "statsToggle");
			assertTrue(head.isVisible());
			assertTrue(!named(box, "protectionPrayer").isVisible());
			press(head);
			assertTrue(named(box, "protectionPrayer").isVisible());
			press(head);
			assertTrue(!named(box, "protectionPrayer").isVisible());
		});
	}

	private static void press(Component c)
	{
		for (java.awt.event.MouseListener l : c.getMouseListeners())
			l.mousePressed(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_PRESSED, 0, 0, 1, 1, 1, false));
	}

	@Test
	public void theOverheadIconCarriesTheWikiAdviceAndItsExclusions() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ResultView view = view(100);
			Overheads.Advice vorkath = Overheads.recommend(monster("vorkath (post-quest)"), Combat.MELEE);
			javax.swing.JComponent icon = view.overheadIcon(vorkath);
			assertEquals("overheadIcon", icon.getName());
			assertTrue(icon.getToolTipText().contains("Overhead: Protect from Magic</b>: "));
			assertEquals(1, icon.getComponentPopupMenu().getComponentCount());
			// A note opening with the summary continues it rather than repeating "None needed".
			String dragon = view.overheadIcon(Overheads.recommend(monster("steel dragon (level 246 (task only))"), Combat.MAGIC))
				.getToolTipText();
			assertTrue(dragon, dragon.contains("Overhead: None needed</b> at range: "));
			javax.swing.JComponent bloat = view.overheadIcon(Overheads.recommend(monster("pestilent bloat (normal)"), Combat.MELEE));
			assertTrue(bloat.getToolTipText(), bloat.getToolTipText().contains("Overhead: None needed</b>: step out"));
			assertNull(bloat.getComponentPopupMenu());
			// Excluding the advised overhead says so and offers it back.
			javax.swing.JComponent excluded = view.overheadIcon(vorkath.without(Collections.singleton("protect from magic")));
			assertTrue(excluded.getToolTipText().contains("Overhead: No overhead</b>"));
			assertTrue(excluded.getToolTipText().contains("Protect from Magic excluded in Rules."));
			assertEquals("Allow Protect from Magic",
				((javax.swing.JMenuItem) excluded.getComponentPopupMenu().getComponent(0)).getText());
		});
	}

	private static Monster monster(String name)
	{
		Monster monster = new Monster();
		monster.setName(name);
		return monster;
	}

	private static ResultView view(int hp)
	{
		return view(hp, null);
	}

	private static ResultView view(int hp, String name)
	{
		Monster monster = new Monster();
		if (name != null)
			monster.setName(name);
		monster.setHitpoints(hp);
		return view(monster);
	}

	private static ResultView view(Monster monster)
	{
		int hp = monster.getHitpoints();
		SearchResults results = com.bestgearsetup.TestResults.of(monster, hp, Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), false);
		return new ResultView(results, null, null, null, null, 0, null, style -> { });
	}

	private static Setup setup(String spellName, String weapon, double dps, java.util.Set<SupportSpell> support)
	{
		Outfit loadout = new Outfit();
		Gear item = new Gear();
		item.setName(weapon);
		loadout.set(Slot.WEAPON, item);
		if (spellName != null)
		{
			Spell spell = new Spell();
			spell.setName(spellName);
			loadout.setSpell(spell);
		}
		return new Setup(spellName == null ? Combat.MELEE : Combat.MAGIC,
			loadout, dps(dps), 0, 0, Collections.emptySet(), support, null,
			support.contains(SupportSpell.THRALL) ? Thrall.GREATER : null);
	}

	private static Dps dps(double value)
	{
		return new Dps(value, 10, 0.5, 5, 4.8, null, value, 1, 5);
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
			CombatContext ctx = TestFactories.combatContext(monster, PlayerLevels.maxed(), false, false, null)
				.withModifiers(CombatModifiers.builder().monsterHitpoints(selected).build());
			int starting = selected == 100 ? 100 : 750;
			assertEquals(starting, ctx.getTargetHitpoints());
			SearchResults results = com.bestgearsetup.TestResults.of(monster, ctx.getTargetHitpoints(), Collections.emptyMap(),
				Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false);
			SwingUtilities.invokeAndWait(() ->
			{
				ResultView view = new ResultView(results, null, null, null, null, 0, null, style -> { });
				JPanel box = view.statsBox(new Dps(2, 10, 0.5, 4, 4.8, null), null, null, false, false);
				boolean found = false;
				for (Component component : box.getComponents())
				{
					if (component instanceof JLabel && ((JLabel) component).getText().contains("TTK:"))
					{
						assertEquals("<html>TTK: ~" + starting / 2 + "s</html>", ((JLabel) component).getText());
						found = true;
					}
				}
				assertTrue(found);
				assertTrue("No thrall line unless the setup includes one", label(box, "Incl. thrall") == null);
				JPanel withThrall = view.statsBox(new Dps(2, 10, 0.5, 4, 4.8, null), Thrall.SUPERIOR, null, false, false);
				assertEquals("<html>Incl. thrall: +0.42</html>", label(withThrall, "Incl. thrall").getText());
				assertTrue(label(withThrall, "Incl. thrall").getToolTipText().startsWith("Superior ghostly thrall"));
			});
		}
	}

	@Test
	public void ttkKeepsADecimalOnlyBelowTenSeconds() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ResultView view = view(100);
			assertEquals("<html>TTK: ~8.0s</html>", label(view.statsBox(dps(12.5), null, null, false, false), "TTK:").getText());
			// 9.97s rounds to 10, so it drops the decimal like 10s does.
			assertEquals("<html>TTK: ~10s</html>", label(view.statsBox(dps(10.03), null, null, false, false), "TTK:").getText());
			assertEquals("<html>TTK: ~13s</html>", label(view.statsBox(dps(8), null, null, false, false), "TTK:").getText());
		});
	}

	@Test
	public void costLineNamesWhatYouOwn() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JPanel box = view(100).costBox(new Outfit(), null);
			String text = ((JLabel) box.getComponent(0)).getText();
			assertTrue(text, text.contains(" (own <font color='#6BD56B'>0</font>)<br>To buy: "));
		});
	}

	@Test
	public void atlatlAddsOnlyTheStrengthBoost()
	{
		Map<Combat, List<Potion>> potions = new EnumMap<>(Combat.class);
		potions.put(Combat.RANGED, Collections.singletonList(potion(2444, "ranging potion", "ranged")));
		potions.put(Combat.MELEE, Arrays.asList(potion(2436, "super attack", "attack"),
			potion(2440, "super strength", "strength")));
		SearchResults results = TestResults.of(new Monster(), 100, Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyMap(), potions, false);
		List<String> atlatl = new ArrayList<>();
		ResultView.potionsFor(results, Style.Type.ATLATL, Combat.RANGED).forEach(p -> atlatl.add(p.getName()));
		assertEquals(Arrays.asList("ranging potion", "super strength"), atlatl);
		assertEquals(1, ResultView.potionsFor(results, Style.Type.RANGED, Combat.RANGED).size());
	}

	private static Potion potion(int id, String name, String skill)
	{
		Potion p = new Potion();
		p.setId(id);
		p.setName(name);
		p.setSkill(skill);
		return p;
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

	@Test
	public void aRecalculationOpensOnThePickedStyle() throws Exception
	{
		com.bestgearsetup.BestGearSetupPlugin plugin = org.mockito.Mockito.mock(com.bestgearsetup.BestGearSetupPlugin.class);
		org.mockito.Mockito.when(plugin.getOwnedItems()).thenReturn(org.mockito.Mockito.mock(com.bestgearsetup.OwnedItems.class));
		net.runelite.client.game.ItemManager images = org.mockito.Mockito.mock(net.runelite.client.game.ItemManager.class);
		org.mockito.Mockito.when(images.getImage(org.mockito.ArgumentMatchers.anyInt()))
			.thenReturn(org.mockito.Mockito.mock(net.runelite.client.util.AsyncBufferedImage.class));
		java.util.List<Style.Type> picked = new java.util.ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			SearchResults results = styles();
			ResultView view = new ResultView(results, plugin, images, null, null, 0, null, picked::add);
			assertEquals(best(results, Style.Type.CRUSH), highlighted(plugin));
			Component stab = all(view).stream().filter(c -> c instanceof javax.swing.JComponent
				&& String.valueOf(((javax.swing.JComponent) c).getToolTipText()).startsWith("Stab:")).findFirst().get();
			stab.dispatchEvent(new java.awt.event.MouseEvent(stab, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false));
			assertEquals(Collections.singletonList(Style.Type.STAB), picked);
			assertEquals(best(results, Style.Type.STAB), highlighted(plugin));

			SearchResults recalculated = styles();
			new ResultView(recalculated, plugin, images, null, null, 0, Style.Type.STAB, picked::add);
			assertEquals(best(recalculated, Style.Type.STAB), highlighted(plugin));
			new ResultView(recalculated, plugin, images, null, null, 0, Style.Type.MAGIC, picked::add);
			assertEquals("A kept style these results lack opens on the best one", best(recalculated, Style.Type.CRUSH),
				highlighted(plugin));
		});
	}

	/** Crush beats stab; each call builds new setups. */
	private static SearchResults styles()
	{
		java.util.Map<Style.Type, java.util.List<Setup>> byType = new java.util.EnumMap<>(Style.Type.class);
		byType.put(Style.Type.CRUSH, Collections.singletonList(styleSetup("mace", 5)));
		byType.put(Style.Type.STAB, Collections.singletonList(styleSetup("spear", 3)));
		return com.bestgearsetup.TestResults.of(new Monster(), 100, byType, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false);
	}

	private static Setup styleSetup(String weaponName, double dps)
	{
		Gear weapon = new Gear();
		weapon.setId(100);
		weapon.setName(weaponName);
		weapon.setSlot(Slot.WEAPON);
		Outfit loadout = new Outfit();
		loadout.set(Slot.WEAPON, weapon);
		loadout.setStyle(Style.parse("pound,crush,aggressive"));
		return new Setup(Combat.MELEE, loadout, new Dps(dps, 0, 1, 4, 12, null), 0, 0, java.util.Collections.emptySet(), java.util.Collections.emptySet(), null, null);
	}

	private static Setup best(SearchResults results, Style.Type type)
	{
		return results.getByType().get(type).get(0);
	}

	/** The setup last laid out in the bank; Setup is a value class, so this captures rather than counts calls. */
	private static Setup highlighted(com.bestgearsetup.BestGearSetupPlugin plugin)
	{
		org.mockito.ArgumentCaptor<Setup> setup = org.mockito.ArgumentCaptor.forClass(Setup.class);
		org.mockito.Mockito.verify(plugin, org.mockito.Mockito.atLeastOnce())
			.setBankHighlightedSetup(setup.capture(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		org.mockito.Mockito.clearInvocations(plugin);
		return setup.getValue();
	}

	private static java.util.List<Component> all(java.awt.Container root)
	{
		java.util.List<Component> found = new java.util.ArrayList<>();
		for (Component child : root.getComponents())
		{
			found.add(child);
			if (child instanceof java.awt.Container) { found.addAll(all((java.awt.Container) child)); }
		}
		return found;
	}
}
