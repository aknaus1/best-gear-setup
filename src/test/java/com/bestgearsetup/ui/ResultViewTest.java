package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.CombatContext;
import com.bestgearsetup.calc.CombatModifiers;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.calc.Thrall;
import com.bestgearsetup.calc.PlayerLevels;
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
