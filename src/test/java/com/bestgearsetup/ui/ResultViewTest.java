package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.RuneUse;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.calc.AttackStyle;
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
	@org.junit.Before public void trackTips() { HoverTextLengthTest.trackTips(); }
	@org.junit.After public void tipsAreShort() throws Exception { HoverTextLengthTest.assertTrackedTipsShort(); }

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
			assertTrue(named(box, "runesPerKill").getToolTipText().contains("Expected per kill"));
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

	@Test
	public void tripLinesShowCombatTimePrayerAndSupplies() throws Exception
	{
		Loadout loadout = new Loadout();
		SetupResult setup = new SetupResult(CombatClass.MELEE, loadout, dps(5), 0);
		java.util.List<com.bestgearsetup.TripSupplies.Supply> supplies = Collections.singletonList(
			new com.bestgearsetup.TripSupplies.Supply("Prayer potion", Collections.singletonList(2434), 3));
		SwingUtilities.invokeAndWait(() ->
		{
			JPanel box = view(100, "vorkath (post-quest)").statsBox(dps(5), null);
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
			JPanel single = view(100).statsBox(dps(5), null);
			view(100).addTripLines(single, setup, 1, Collections.emptyList());
			assertNull(named(single, "tripCombatTime"));
			assertNull(named(single, "tripSupplies"));
			ResultView vorkath = view(100, "vorkath (post-quest)");
			JPanel advised = vorkath.statsBox(dps(5), null);
			vorkath.addTripLines(advised, setup, 1, Collections.emptyList());
			assertTrue(named(advised, "protectionPrayer").getText().contains("Overhead: Protect from Magic"));
			// A note opening with the summary continues it rather than repeating "None needed" on a second line.
			ResultView dragon = view(100, "steel dragon (level 246 (task only))");
			JPanel magic = dragon.statsBox(dps(5), null);
			dragon.addTripLines(magic, new SetupResult(CombatClass.MAGIC, loadout, dps(5), 0), 1, Collections.emptyList());
			assertTrue(named(magic, "protectionPrayer").getText(),
				named(magic, "protectionPrayer").getText().contains("Overhead: None needed at range: "));
			ResultView bloat = view(100, "pestilent bloat (normal)");
			JPanel bloatBox = bloat.statsBox(dps(5), null);
			bloat.addTripLines(bloatBox, setup, 1, Collections.emptyList());
			String bloatText = named(bloatBox, "protectionPrayer").getText();
			assertTrue(bloatText, bloatText.contains("Overhead: None needed: step out"));
			// Raid targets show their overhead but no trip lines.
			Monster tekton = new Monster();
			tekton.setName("tekton (normal)");
			tekton.setHitpoints(100);
			Monster.Attribute xerician = new Monster.Attribute();
			xerician.setName("xerician");
			tekton.setAttributes(Collections.singletonList(xerician));
			ResultView raid = view(tekton);
			JPanel raidBox = raid.statsBox(dps(5), null);
			raid.addTripLines(raidBox, setup, 30, Collections.emptyList());
			assertNull(named(raidBox, "tripCombatTime"));
			assertNull(named(raidBox, "prayerPerKill"));
			// More supplies than one inventory holds get a warning.
			JPanel full = view(100, "vorkath (post-quest)").statsBox(dps(5), null);
			view(100, "vorkath (post-quest)").addTripLines(full, setup, 30, Collections.singletonList(
				new com.bestgearsetup.TripSupplies.Supply("Prayer potion", Collections.singletonList(2434), 29)));
			assertNotNull(named(full, "tripOverflow"));
		});
		assertEquals("1h 05m", ResultView.duration(3900));
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
		SearchResults results = new SearchResults(monster, hp, Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), false);
		return new ResultView(results, null, null, null, null, 0, null, style -> { });
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
				ResultView view = new ResultView(results, null, null, null, null, 0, null, style -> { });
				JPanel box = view.statsBox(new DpsResult(2, 10, 0.5, 4, 4.8, null), null);
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
				JPanel withThrall = view.statsBox(new DpsResult(2, 10, 0.5, 4, 4.8, null), Thrall.SUPERIOR);
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
			assertEquals("<html>TTK: ~8.0s</html>", label(view.statsBox(dps(12.5), null), "TTK:").getText());
			// 9.97s rounds to 10, so it drops the decimal like 10s does.
			assertEquals("<html>TTK: ~10s</html>", label(view.statsBox(dps(10.03), null), "TTK:").getText());
			assertEquals("<html>TTK: ~13s</html>", label(view.statsBox(dps(8), null), "TTK:").getText());
		});
	}

	@Test
	public void costLineNamesWhatYouOwn() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JPanel box = view(100).costBox(new Loadout(), null);
			String text = ((JLabel) box.getComponent(0)).getText();
			assertTrue(text, text.contains(" (own <font color='#6BD56B'>0</font>)<br>To buy: "));
		});
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
		java.util.List<AttackStyle.Type> picked = new java.util.ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			SearchResults results = styles();
			ResultView view = new ResultView(results, plugin, images, null, null, 0, null, picked::add);
			assertEquals(best(results, AttackStyle.Type.CRUSH), highlighted(plugin));
			Component stab = all(view).stream().filter(c -> c instanceof javax.swing.JComponent
				&& String.valueOf(((javax.swing.JComponent) c).getToolTipText()).startsWith("Stab:")).findFirst().get();
			stab.dispatchEvent(new java.awt.event.MouseEvent(stab, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false));
			assertEquals(Collections.singletonList(AttackStyle.Type.STAB), picked);
			assertEquals(best(results, AttackStyle.Type.STAB), highlighted(plugin));

			SearchResults recalculated = styles();
			new ResultView(recalculated, plugin, images, null, null, 0, AttackStyle.Type.STAB, picked::add);
			assertEquals(best(recalculated, AttackStyle.Type.STAB), highlighted(plugin));
			new ResultView(recalculated, plugin, images, null, null, 0, AttackStyle.Type.MAGIC, picked::add);
			assertEquals("A kept style these results lack opens on the best one", best(recalculated, AttackStyle.Type.CRUSH),
				highlighted(plugin));
		});
	}

	/** Crush beats stab; each call builds new setups. */
	private static SearchResults styles()
	{
		java.util.Map<AttackStyle.Type, java.util.List<SetupResult>> byType = new java.util.EnumMap<>(AttackStyle.Type.class);
		byType.put(AttackStyle.Type.CRUSH, Collections.singletonList(styleSetup("mace", 5)));
		byType.put(AttackStyle.Type.STAB, Collections.singletonList(styleSetup("spear", 3)));
		return new SearchResults(new Monster(), 100, byType, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false);
	}

	private static SetupResult styleSetup(String weaponName, double dps)
	{
		GearItem weapon = new GearItem();
		weapon.setId(100);
		weapon.setName(weaponName);
		weapon.setSlot(Slot.WEAPON);
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, weapon);
		loadout.setStyle(AttackStyle.parse("pound,crush,aggressive"));
		return new SetupResult(CombatClass.MELEE, loadout, new DpsResult(dps, 0, 1, 4, 12, null), 0);
	}

	private static SetupResult best(SearchResults results, AttackStyle.Type type)
	{
		return results.getByType().get(type).get(0);
	}

	/** The setup last laid out in the bank; SetupResult is a value class, so this captures rather than counts calls. */
	private static SetupResult highlighted(com.bestgearsetup.BestGearSetupPlugin plugin)
	{
		org.mockito.ArgumentCaptor<SetupResult> setup = org.mockito.ArgumentCaptor.forClass(SetupResult.class);
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
