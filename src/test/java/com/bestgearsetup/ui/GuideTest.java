package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.Options;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.Names;
import com.bestgearsetup.data.Slot;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** The Getting started guide outlines real controls, moves on when the player acts, and is seen only once closed. */
public class GuideTest
{
	@org.junit.Before public void trackTips() { HoverTextLengthTest.trackTips(); }
	@org.junit.After public void tipsAreShort() throws Exception { HoverTextLengthTest.assertTrackedTipsShort(); }

	private static Sidebar panel(List<String> writes)
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override
			public void setConfig(String key, Object value)
			{
				writes.add(key + "=" + value);
			}

			@Override
			public net.runelite.client.config.ConfigDescriptor getConfigDescriptor()
			{
				return com.bestgearsetup.ConfigItemsForTests.descriptor();
			}

			@Override
			public OwnedItems getOwnedItems()
			{
				return com.bestgearsetup.ConfigItemsForTests.emptyOwnedItems();
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
		};
		return new Sidebar(plugin, new Options() {}, null, null, null);
	}

	@Test
	public void everyStepTargetIsARealControl() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Sidebar panel = panel(new ArrayList<>());
			for (JsonElement step : Names.load("/com/bestgearsetup/guide.json").getAsJsonArray("steps"))
			{
				JsonObject entry = step.getAsJsonObject();
				assertNotNull(entry.get("title"));
				assertNotNull(entry.get("text"));
				// The bank's button is in game and the style tabs come with results; everything else is built with the panel.
				if (entry.has("target") && !Set.of("bankButton", "styleTabs").contains(entry.get("target").getAsString()))
				{
					assertNotNull("outlined control " + entry.get("target"), Sidebar.named(panel, entry.get("target").getAsString()));
				}
				if (entry.has("done"))
				{
					assertTrue(Set.of("target", "bank", "fight", "gear", "settings", "results").contains(entry.get("done").getAsString()));
				}
			}
		});
	}

	@Test
	public void pagesThroughStepsAndIsSeenOnceClosed() throws Exception
	{
		List<String> writes = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			Sidebar panel = panel(writes);
			assertFalse("hidden unless the plugin opens it", panel.guideRow.isVisible());
			assertNull(panel.guideTarget());
			panel.showGuide(false);
			panel.guideDone("target");
			assertTrue("closing or acting on a hidden guide writes nothing", writes.isEmpty());

			panel.showGuide(true);
			assertTrue(panel.guideRow.isVisible());
			assertFalse(panel.guideBack.isEnabled());
			// With no data loaded and no results, every step still shows.
			int steps = 1;
			while (panel.guideNext.getText().equals("Next"))
			{
				panel.guideNext.doClick();
				steps++;
			}
			assertTrue("walks through the panel in detail", steps >= 20);
			assertTrue(panel.guideBack.isEnabled());
			assertTrue(writes.isEmpty());

			panel.guideNext.doClick();
			assertFalse(panel.guideRow.isVisible());
			assertNull("no outline once closed", panel.guideTarget());
			assertEquals(Collections.singletonList(Options.GUIDE_KEY + "=true"), writes);
		});
	}

	@Test
	public void movesOnOnlyForTheCurrentStepsAction() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Sidebar panel = panel(new ArrayList<>());
			panel.showGuide(true);
			assertNull("the welcome outlines nothing", panel.guideTarget());
			while (!"search".equals(panel.guideTarget())) { panel.guideNext.doClick(); }
			panel.guideDone("bank");
			assertEquals("another step's action is ignored", "search", panel.guideTarget());
			panel.guideDone("target");
			assertEquals("picking a target moves on", "version", panel.guideTarget());
			while (!"settingsButton".equals(panel.guideTarget())) { panel.guideNext.doClick(); }
			((javax.swing.JButton) Sidebar.named(panel, "settingsButton")).doClick();
			assertEquals("opening Settings moves on to a section inside it", "optionsSection", panel.guideTarget());
			while (!"settingsBack".equals(panel.guideTarget())) { panel.guideNext.doClick(); }
			((javax.swing.JButton) Sidebar.named(panel, "settingsBack")).doClick();
			assertEquals("going back moves on", "fightEditorButton", panel.guideTarget());
			while (!"bankButton".equals(panel.guideTarget())) { panel.guideNext.doClick(); }
			panel.guideDone("target");
			assertEquals("bankButton", panel.guideTarget());
			panel.guideDone("bank");
			assertFalse("opening the gear layout moves on", "bankButton".equals(panel.guideTarget()));
		});
	}
}
