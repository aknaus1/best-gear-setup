package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.calc.CombatContext;
import com.bestgearsetup.calc.CombatModifiers;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.Thrall;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.data.Monster;
import java.awt.Component;
import java.util.Collections;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class ResultViewTest
{
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
