package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.Budget;
import com.bestgearsetup.RaidPlan;
import com.bestgearsetup.calc.SpecialPlan;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.CombatContext;
import com.bestgearsetup.calc.CombatModifiers;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.Thrall;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.calc.ProtectionPrayers;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import java.awt.Component;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class ResultViewTest
{
	@Test public void sharedSpecialEquipmentAndLoadedAmmunitionArePricedOnce() throws Exception
	{
		GearItem weapon = pricedItem(1, "sword", Slot.WEAPON);
		GearItem provided = pricedItem(2, "dawnbringer", Slot.WEAPON);
		GearItem arrows = pricedItem(3, "arrows", Slot.AMMO);
		GearItem shield = pricedItem(4, "shield", Slot.SHIELD);
		GearItem darts = pricedItem(5, "darts", Slot.AMMO);
		GearItem bow = pricedItem(6, "special bow", Slot.WEAPON);
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, weapon); loadout.set(Slot.AMMO, arrows); loadout.set(Slot.SHIELD, shield);
		loadout.setLoadedAmmo(darts);
		SpecialPlan partner = new SpecialPlan(bow, shield, arrows, null, null, darts, null, null, null, null, 1, 0, 0);
		SpecialPlan rotation = new SpecialPlan(provided, shield, arrows, null, null, null, null, null, null, null, 1, 0, 0,
			null, 0, Collections.singletonList(partner), -1, null);
		SetupResult setup = new SetupResult(CombatClass.MELEE, loadout, null, 0, 0,
			Collections.emptySet(), Collections.emptySet(), rotation);
		assertEquals(new java.util.HashSet<>(Arrays.asList(1, 3, 4, 5, 6)), RaidPlan.requiredItems(setup).keySet());
		BestGearSetupPlugin plugin = org.mockito.Mockito.mock(BestGearSetupPlugin.class);
		org.mockito.Mockito.when(plugin.price(org.mockito.ArgumentMatchers.any(GearItem.class))).thenReturn(100L);
		org.mockito.Mockito.when(plugin.owns(org.mockito.ArgumentMatchers.any(GearItem.class)))
			.thenAnswer(call -> ((GearItem) call.getArgument(0)).getId() == shield.getId());
		SearchResults empty = new SearchResults(new Monster(), 100, Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), false);
		SwingUtilities.invokeAndWait(() ->
		{
			ResultView view = new ResultView(empty, plugin, null, null, null, 10, false);
			JLabel cost = label(view.costBox(setup), "Cost:");
			assertTrue(cost.getText(), cost.getText().contains(">" + Budget.format(2300) + "</font>"));
			assertTrue(cost.getText(), cost.getText().contains(">" + Budget.format(2200) + "</font>"));
		});
	}

	private static GearItem pricedItem(int id, String name, Slot slot)
	{
		GearItem item = new GearItem(); item.setId(id); item.setName(name); item.setSlot(slot);
		if (slot == Slot.AMMO) { item.setRangedStr(1); }
		return item;
	}

	@Test
	public void atlatlUsesRangingAndStrengthWithoutSuperAttack() throws Exception
	{
		Potion ranging = potion(2444, "ranging potion", "ranged");
		Potion attack = potion(2436, "super attack", "attack");
		Potion strength = potion(2440, "super strength", "strength");
		ResultView view = potionView(Collections.singletonList(ranging), Arrays.asList(attack, strength));
		assertEquals(Arrays.asList(ranging, strength), view.potionsFor(setup("eclipse atlatl", CombatClass.RANGED)));
		assertEquals(Arrays.asList(ranging, strength), view.potionsFor(setup("hunter's spear", CombatClass.RANGED)));
		assertEquals(Collections.singletonList(ranging), view.potionsFor(setup("magic shortbow", CombatClass.RANGED)));
		assertEquals(Arrays.asList(attack, strength), view.potionsFor(setup("dragon scimitar", CombatClass.MELEE)));
	}

	@Test
	public void combinedPotionsRetainStrengthAndDisplayOnce() throws Exception
	{
		Potion ranging = potion(2444, "ranging potion", "ranged");
		Potion attack = potion(12695, "super combat potion", "attack");
		Potion strength = potion(12695, "super combat potion", "strength");
		ResultView view = potionView(Collections.singletonList(ranging), Arrays.asList(attack, strength));
		assertEquals(Arrays.asList(ranging, strength), view.potionsFor(setup("eclipse atlatl", CombatClass.RANGED)));
		assertEquals(Collections.singletonList(attack), view.potionsFor(setup("dragon scimitar", CombatClass.MELEE)));
		Potion overload = potion(20996, "overload (+)", "ranged");
		view = potionView(Collections.singletonList(overload), Arrays.asList(
			potion(20996, "overload (+)", "attack"), potion(20996, "overload (+)", "strength")));
		assertEquals(Collections.singletonList(overload), view.potionsFor(setup("eclipse atlatl", CombatClass.RANGED)));
	}

	@Test
	public void protectionRowNamesTheOverheadAndExplainsSwitches()
	{
		JLabel single = (JLabel) ResultView.protectionRow(new ProtectionPrayers.Advice(ProtectionPrayers.Overhead.MAGIC,
			Collections.singletonList(ProtectionPrayers.Overhead.MAGIC), "Maiden's blood attacks are magic."));
		assertEquals("protectionPrayer", single.getName());
		assertTrue(single.getText().contains("Protect from Magic"));
		assertTrue("A single prayer keeps its reason in the tooltip", !single.getText().contains("blood"));
		assertTrue(single.getToolTipText().contains("blood"));
		JLabel switching = (JLabel) ResultView.protectionRow(new ProtectionPrayers.Advice(ProtectionPrayers.Overhead.MAGIC,
			Arrays.asList(ProtectionPrayers.Overhead.MAGIC, ProtectionPrayers.Overhead.MISSILES), "Olm alternates."));
		assertTrue(switching.getText().contains("Switch: Magic / Missiles"));
		assertTrue(switching.getText().contains("Olm alternates."));
		assertEquals(null, ResultView.protectionRow(null));
		Monster maiden = new Monster();
		maiden.setName("the maiden of sugadinti (normal)");
		SearchResults results = new SearchResults(maiden, 100, Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), false);
		assertEquals(ProtectionPrayers.Overhead.MAGIC, results.protectionFor(setup("sanguinesti staff", CombatClass.MAGIC)).getPrayer());
	}

	private static Potion potion(int id, String name, String skill)
	{
		Potion potion = new Potion();
		potion.setId(id);
		potion.setName(name);
		potion.setSkill(skill);
		return potion;
	}

	private static SetupResult setup(String weaponName, CombatClass combatClass)
	{
		GearItem weapon = new GearItem();
		weapon.setName(weaponName);
		Loadout loadout = new Loadout();
		loadout.set(Slot.WEAPON, weapon);
		loadout.setStyle(AttackStyle.parse(combatClass == CombatClass.RANGED ? "rapid,ranged,rapid" : "slash,slash,aggressive"));
		return new SetupResult(combatClass, loadout, null, 0);
	}

	private static ResultView potionView(List<Potion> ranged, List<Potion> melee) throws Exception
	{
		Map<CombatClass, List<Potion>> potions = new EnumMap<>(CombatClass.class);
		potions.put(CombatClass.RANGED, ranged);
		potions.put(CombatClass.MELEE, melee);
		Monster glough = new Monster();
		glough.setName("glough");
		SearchResults results = new SearchResults(glough, 600, Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyMap(), potions, false);
		ResultView[] view = new ResultView[1];
		SwingUtilities.invokeAndWait(() -> view[0] = new ResultView(results, null, null, null, null, 0));
		return view[0];
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

	@Test
	public void prayerBoxCountsTheAssumedPrayersOverTheKillTime() throws Exception
	{
		// 100 HP at 2 DPS is a 50-second kill. Piety (24) at +15 bonus drains a point every 2.25 seconds:
		// 22.2 points a kill, 0.72 prayer potion doses (31 points) and 0.69 super restores (32) at 99 Prayer.
		Monster monster = new Monster();
		monster.setName("Combat dummy");
		monster.setHitpoints(100);
		Map<CombatClass, com.bestgearsetup.data.OffensivePrayer> prayers = new EnumMap<>(CombatClass.class);
		prayers.put(CombatClass.MELEE, new com.bestgearsetup.data.OffensivePrayer("piety", 20, 23, 70, 70));
		SearchResults results = new SearchResults(monster, 100, Collections.emptyMap(), Collections.emptyList(),
			prayers, Collections.emptyMap(), false);
		GearItem cape = pricedItem(7, "cape", Slot.CAPE);
		cape.setPrayerBonus(15);
		Loadout praying = new Loadout();
		praying.set(Slot.CAPE, cape);
		DpsResult dps = new DpsResult(2, 10, 0.5, 4, 4.8, null);
		SetupResult melee = new SetupResult(CombatClass.MELEE, praying, dps, 0);
		SetupResult magic = new SetupResult(CombatClass.MAGIC, praying, dps, 0);
		SwingUtilities.invokeAndWait(() ->
		{
			ResultView view = new ResultView(results, null, null, null, null, 0);
			JPanel box = view.prayerBox(melee);
			assertEquals("<html>Prayer: ~22.2 pts/kill</html>", label(box, "Prayer:").getText());
			assertEquals("<html>Prayer pots: ~0.72 doses/kill</html>", label(box, "Prayer pots").getText());
			assertEquals("<html>Super restores: ~0.69 doses/kill</html>", label(box, "Super restores").getText());
			assertTrue(label(box, "Prayer:").getToolTipText().contains("Piety drain 1 point every 2.25s at +15 prayer bonus"));
			assertTrue("No box when the setup prays nothing", view.prayerBox(magic) == null);
			assertTrue("No box when the kill never ends", view.prayerBox(new SetupResult(CombatClass.MELEE, praying,
				DpsResult.ZERO, 0)) == null);
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
}
