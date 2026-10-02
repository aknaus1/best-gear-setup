package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.calc.CombatContext;
import com.bestgearsetup.calc.CombatModifiers;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.data.Monster;
import java.awt.Component;
import java.lang.reflect.Method;
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
				try
				{
					ResultView view = new ResultView(results, null, null, null, null, 0);
					Method stats = ResultView.class.getDeclaredMethod("statsBox", DpsResult.class);
					stats.setAccessible(true);
					JPanel box = (JPanel) stats.invoke(view, new DpsResult(2, 10, 0.5, 4, 4.8, null));
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
				}
				catch (ReflectiveOperationException e)
				{
					throw new AssertionError(e);
				}
			});
		}
	}
}
