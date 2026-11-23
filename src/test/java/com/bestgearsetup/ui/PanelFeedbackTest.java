package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.Options;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.SearchResults;
import com.bestgearsetup.calc.LockStatus;
import com.bestgearsetup.calc.PotionChoice;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

/** Rejected input and empty searches explain themselves; estimate-changing assumptions sit beside the results. */
public class PanelFeedbackTest
{
	@org.junit.Before public void trackTips() { HoverTextLengthTest.trackTips(); }
	@org.junit.After public void tipsAreShort() throws Exception { HoverTextLengthTest.assertTrackedTipsShort(); }

	private static BestGearSetupPlugin plugin()
	{
		return new BestGearSetupPlugin()
		{
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

			@Override
			public String getPotionChoice(Combat cls)
			{
				return PotionChoice.BEST;
			}

			@Override
			public void setConfig(String key, Object value)
			{
			}
		};
	}

	@Test
	public void invalidBudgetIsExplainedAndTheSavedBudgetKept() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Options config = new Options()
			{
			};
			Sidebar panel = new Sidebar(plugin(), config, null, null, null);
			JTextField budget = panel.budgetField;
			JLabel error = (JLabel) named(panel, "budgetError");
			assertFalse(error.isVisible());
			budget.setText("nonsense");
			budget.postActionEvent();
			assertEquals(config.budget(), budget.getText());
			assertTrue(error.isVisible());
			assertTrue(error.getText().contains("nonsense"));
			assertTrue(error.getText().contains("750k"));
			budget.setText(config.budget());
			budget.postActionEvent();
			assertFalse(error.isVisible());
		});
	}

	@Test
	public void emptySearchesSayWhyAndWhichTargetIsKept() throws Exception
	{
		GameData data = WikiGameData.get(RuneLiteAPI.GSON).gameData(RuneLiteAPI.GSON);
		SwingUtilities.invokeAndWait(() ->
		{
			Sidebar panel = new Sidebar(plugin(), new Options()
			{
				@Override
				public boolean bossesOnly()
				{
					return true;
				}
			}, null, null, null);
			panel.onDataLoaded(data);
			IconTextField search = panel.searchField;
			JLabel hint = (JLabel) named(panel, "noMatches");
			search.setText("zzzz-no-monster");
			assertTrue(hint.isVisible());
			assertTrue(hint.getText().contains("No matching monsters"));
			search.setText("dust devil");
			assertTrue("Bosses filter hides dust devils", hint.isVisible());
			assertTrue(hint.getText().contains("Search all monsters"));
			search.setText("vorkath");
			assertFalse(hint.isVisible());
		});
		assertTrue(Sidebar.noMatchesMessage(false, "Vorkath").contains("Still targeting <b>Vorkath</b>"));
	}

	@Test
	public void assumptionWarningsAreShownWithTheResults() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Sidebar panel = new Sidebar(plugin(), new Options()
			{
			}, null, null, null);
			panel.showResults(com.bestgearsetup.TestResults.of(new Monster(), 100, Collections.emptyMap(), Collections.emptyList(),
				Collections.emptyMap(), Collections.emptyMap(), false, Collections.emptyList(), false, false,
				Collections.singletonList("Ranged potion picked by name: Smelling Salts - only usable in the Tombs of Amascut.")));
			JLabel warnings = (JLabel) named(panel, "assumptionWarnings");
			assertNotNull(warnings);
			assertTrue(warnings.isVisible());
			assertTrue(warnings.getText().contains("Smelling Salts"));
		});
	}

	@Test
	public void locksAreNotListedAboveResultsButABlockedLockIsExplained() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Sidebar panel = new Sidebar(plugin(), new Options()
			{
			}, null, null, null);
			LockStatus used = new LockStatus(Slot.FEET, SlotLock.item(1), null, null, null);
			LockStatus blocked = new LockStatus(Slot.HEAD, SlotLock.item(2), null, "its requirements are too high", null);
			panel.showResults(com.bestgearsetup.TestResults.of(new Monster(), 100, Collections.emptyMap(), Collections.emptyList(),
				Collections.emptyMap(), Collections.emptyMap(), false, Arrays.asList(used, blocked), false, false,
				Collections.emptyList()));
			assertTrue("No lock rows above the results", named(panel, "lockFEET") == null && named(panel, "lockHEAD") == null);
			String status = panel.statusLabel.getText();
			assertTrue(status, status.contains("Head lock can't be used: its requirements are too high. Remove it under Gear."));
			assertFalse(status, status.contains("Feet lock"));
		});
	}


	@Test
	public void startingASearchKeepsThePreviousResultsAndBankLayout() throws Exception
	{
		java.util.List<Object> highlights = new java.util.ArrayList<>();
		BestGearSetupPlugin recording = new BestGearSetupPlugin()
		{
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

			@Override
			public String getPotionChoice(Combat cls)
			{
				return PotionChoice.BEST;
			}

			@Override
			public void setConfig(String key, Object value)
			{
			}

			@Override
			public void setBankHighlightedSetup(com.bestgearsetup.calc.Setup setup)
			{
				highlights.add(setup);
			}
		};
		SwingUtilities.invokeAndWait(() ->
		{
			Sidebar panel = new Sidebar(recording, new Options()
			{
			}, null, null, null);
			panel.showResults(com.bestgearsetup.TestResults.of(new Monster(), 100, Collections.emptyMap(), Collections.emptyList(),
				Collections.emptyMap(), Collections.emptyMap(), false, Collections.emptyList(), false, false,
				Collections.singletonList("Earlier search warning")));
			highlights.clear();
			com.bestgearsetup.data.Summary next = new com.bestgearsetup.data.Summary();
			next.setName("Vorkath");
			panel.showSearching(next);
			assertNotNull("Previous results stay until the new ones arrive", named(panel, "assumptionWarnings"));
			assertTrue("The bank layout isn't dropped while searching", highlights.isEmpty());
			assertTrue(panel.statusLabel.getText().contains("Searching setups for"));
			// Clearing forgets the shown view, so a supplies-only setting change can't bring its bank layout back.
			panel.resultView = new ResultView(com.bestgearsetup.TestResults.of(new Monster(), 100, Collections.emptyMap(),
				Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false), null, null, null, null, 0, null, style -> { });
			panel.clearResults("Account changed");
			highlights.clear();
			panel.refreshSupplies();
			assertTrue(panel.resultView == null && highlights.isEmpty());
		});
	}


	@Test
	public void anotherTargetOrFindBestSetupForgetsThePickedStyle() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Sidebar panel = new Sidebar(plugin(), new Options()
			{
			}, null, null, null);
			com.bestgearsetup.data.Summary vorkath = new com.bestgearsetup.data.Summary();
			vorkath.setName("Vorkath");
			vorkath.setId(1);
			panel.selectMonster(vorkath, false);
			panel.pickedStyle = com.bestgearsetup.calc.Style.Type.STAB;
			panel.selectMonster(vorkath, false);
			assertEquals("The same target keeps it", com.bestgearsetup.calc.Style.Type.STAB, panel.pickedStyle);
			com.bestgearsetup.data.Summary zulrah = new com.bestgearsetup.data.Summary();
			zulrah.setName("Zulrah");
			zulrah.setId(2);
			panel.selectMonster(zulrah, false);
			assertNull(panel.pickedStyle);
			panel.pickedStyle = com.bestgearsetup.calc.Style.Type.STAB;
			panel.findButton.doClick();
			assertNull(panel.pickedStyle);
		});
	}

	/**
	 * The full-fight card lists the worn setup, the carried items and each phase with its style and picks; a phase of the
	 * same fight keeps the card and its kit, a changed input or the next results clear it.
	 */
	@Test
	public void fullFightCardListsThePlanUntilTheNextResults() throws Exception
	{
		ItemManager images = org.mockito.Mockito.mock(ItemManager.class);
		org.mockito.Mockito.when(images.getImage(org.mockito.ArgumentMatchers.anyInt()))
			.thenReturn(org.mockito.Mockito.mock(net.runelite.client.util.AsyncBufferedImage.class));
		java.util.List<com.bestgearsetup.TripSupplies.Kit> kits = new java.util.ArrayList<>();
		java.util.List<String> calls = new java.util.ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			Sidebar panel = new Sidebar(new BestGearSetupPlugin()
			{
				@Override
				public void showFightKit(com.bestgearsetup.TripSupplies.Kit kit, java.util.List<com.bestgearsetup.data.Potion> potions,
					java.util.List<com.bestgearsetup.TripSupplies.Supply> supplies)
				{
					kits.add(kit);
				}

				@Override
				public void stepFight(int row)
				{
					calls.add("step " + row);
				}

				@Override
				public void findBestSetup(com.bestgearsetup.data.Summary summary)
				{
					calls.add("search " + summary.getName());
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

				@Override
				public String getPotionChoice(Combat cls)
				{
					return PotionChoice.BEST;
				}

				@Override
				public boolean owns(com.bestgearsetup.data.Gear item)
				{
					return false;
				}

				@Override
				public boolean wears(com.bestgearsetup.data.Gear item)
				{
					return false;
				}
			}, new Options()
			{
			}, images, null, null);
			com.bestgearsetup.calc.Setup bow = fightSetup(1, "twisted bow", "rapid,ranged,rapid");
			com.bestgearsetup.calc.Setup staff = fightSetup(2, "trident of the swamp", "accurate,magic,magic");
			com.bestgearsetup.FightPlan.Plan plan = com.bestgearsetup.FightPlans.plan(
				Arrays.asList(bow.getLoadout(), staff.getLoadout()), new double[]{100, 100, 100},
				(outfit, phase) -> outfit.equals(bow.getLoadout()) == (phase != 1) ? 10 : 1, 8, Collections.emptySet(),
				items -> true, () -> false);
			panel.showResults(com.bestgearsetup.TestResults.of(new Monster(), 100, Collections.emptyMap(), Collections.emptyList(),
				Collections.emptyMap(), Collections.emptyMap(), false));
			panel.showFightStatus("Planning the full fight...");
			assertTrue(all(PanelInternals.resultsPanel(panel)).stream().anyMatch(c -> c instanceof JLabel
				&& ((JLabel) c).getText().contains("Planning the full fight...")));
			panel.showFightPlan("zulrah", plan, bow, 8, 1, Collections.emptyMap(), Collections.emptyMap(), false, "inputs");
			java.util.List<Component> shown = all(PanelInternals.resultsPanel(panel));
			String text = texts(shown);
			assertTrue(text, text.contains("Full fight: Zulrah"));
			assertFalse("the plan replaces the placeholder", text.contains("Planning"));
			assertTrue(text, text.contains("30 s in total, 1 of 8 switch slots. No specials planned."));
			assertTrue(text, text.contains("Carry: Trident of the Swamp"));
			assertTrue(text, text.contains("Serpentine (Ranged): 10 s, worn</body>"));
			assertTrue(text, text.contains("<b>Magma (Magic): 10 s, switch to Trident of the Swamp (selected)</b>"));
			assertTrue(text, text.contains("Tanzanite (Ranged): 10 s, worn</body>") && text.contains("Phase 2 of 3"));
			assertEquals("the kit goes to the bank", 2, kits.size());
			assertEquals(Collections.singletonList(staff.getLoadout().getWeapon()), kits.get(1).getCarried());
			// Picks are marked; one not used says why on its row, and only the selected row says its tab differs.
			java.util.Map<Integer, com.bestgearsetup.calc.Style.Type> picks = new java.util.HashMap<>();
			picks.put(0, com.bestgearsetup.calc.Style.Type.RANGED);
			picks.put(2, com.bestgearsetup.calc.Style.Type.MAGIC);
			panel.showFightPlan("zulrah", plan, bow, 8, 1, picks, Collections.singletonMap(2, "doesn't fit 8 Fight switches"), true,
				"inputs");
			String picked = texts(all(PanelInternals.resultsPanel(panel)));
			assertTrue(picked, picked.contains("Serpentine (Ranged, picked): 10 s, worn</body>"));
			assertTrue(picked, picked.contains("Tanzanite (Ranged): 10 s, worn<br>Magic picked; doesn't fit 8 Fight switches, so Ranged is planned."));
			assertTrue(picked, picked.contains("Trident of the Swamp (selected)<br>The plan's setup differs from this tab (fight conditions).</b>"));
			assertEquals(picked.indexOf("differs"), picked.lastIndexOf("differs"));
			assertEquals("an equal plan keeps the kit", 2, kits.size());
			// Clicking a row searches its target as usual and moves the marker; the kit stays.
			com.bestgearsetup.data.Summary tanzanite = new com.bestgearsetup.data.Summary();
			tanzanite.setName("zulrah (tanzanite)");
			tanzanite.setId(3);
			panel.onDataLoaded(new GameData(Collections.singletonList(tanzanite), Collections.emptyMap(), Collections.emptyList(),
				Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap()));
			all(PanelInternals.resultsPanel(panel)).stream().filter(c -> c instanceof javax.swing.JButton
				&& ((javax.swing.JButton) c).getText().contains("Tanzanite (")).findFirst()
				.map(javax.swing.JButton.class::cast).get().doClick();
			assertEquals(Arrays.asList("step 2", "search zulrah (tanzanite)"), calls);
			String stepped = texts(all(PanelInternals.resultsPanel(panel)));
			assertTrue(stepped, stepped.contains("Phase 3 of 3") && stepped.contains("<b>Tanzanite (Ranged): 10 s, worn (selected)<br>"));
			assertFalse("the tab difference is said only for the planned row", stepped.contains("differs"));
			assertEquals(2, kits.size());
			// Another phase of the fight keeps the card while the same inputs are planned; changed inputs replace it.
			Monster magma = new Monster();
			magma.setName("zulrah (magma)");
			panel.showResults(com.bestgearsetup.TestResults.of(magma, 100, Collections.emptyMap(), Collections.emptyList(),
				Collections.emptyMap(), Collections.emptyMap(), false));
			panel.planningFight("inputs");
			assertTrue(texts(all(PanelInternals.resultsPanel(panel))).contains("Full fight: Zulrah"));
			panel.showFightPlan("zulrah", plan, bow, 8, 1, picks, Collections.emptyMap(), false, "inputs");
			assertEquals("empty results released the kit, so it is shown again", 3, kits.size());
			panel.planningFight("other inputs");
			String planning = texts(all(PanelInternals.resultsPanel(panel)));
			assertTrue(planning, planning.contains("Planning the full fight...") && !planning.contains("Full fight: Zulrah"));
			assertEquals("the kit stays while new inputs are planned", 3, kits.size());
			panel.showFightPlan("zulrah", plan, bow, 8, 1, picks, Collections.emptyMap(), false, "other inputs");
			assertEquals("new inputs replace the kit", 4, kits.size());
			panel.showFightStatus("Full fight plan unavailable: test");
			assertEquals("a failure releases it", 5, kits.size());
			assertNull(kits.get(4));
			assertTrue("the worn setup is drawn", shown.stream().anyMatch(c -> c instanceof EquipmentGrid));
			panel.showFightPlan("zulrah", new com.bestgearsetup.FightPlan.Plan(plan.getWorn(), plan.getCarried(), 1, plan.getUsed(),
				plan.getSeconds(), Arrays.asList(new com.bestgearsetup.FightPlan.Spec(staff.getLoadout(), 0, 2, true),
				new com.bestgearsetup.FightPlan.Spec(bow.getLoadout(), 2, 1, false)), 25), bow, 8, 1, Collections.emptyMap(),
				Collections.emptyMap(), false, "inputs");
			String specs = all(PanelInternals.resultsPanel(panel)).stream().filter(c -> c instanceof JLabel)
				.map(c -> ((JLabel) c).getText()).collect(java.util.stream.Collectors.joining(" | "));
			assertTrue(specs, specs.contains("switch slots. Specs counted from 25% energy (Spec energy: Regenerating)."));
			panel.showFightPlan("zulrah", plan.withEnergy(15), bow, 8, 1, Collections.emptyMap(), Collections.emptyMap(), false,
				"inputs");
			assertTrue(all(PanelInternals.resultsPanel(panel)).stream().anyMatch(c -> c instanceof JLabel && ((JLabel) c).getText()
				.contains("switch slots. No specials planned from 15% energy (Spec energy: Regenerating).")));
			assertTrue(specs, specs.contains("Spec: Trident of the Swamp ×2 on Serpentine; the drain lasts its health bar"
				+ "<br>Spec: Twisted Bow ×1 on Tanzanite</body>"));
			panel.showResults(com.bestgearsetup.TestResults.of(new Monster(), 100, Collections.emptyMap(), Collections.emptyList(),
				Collections.emptyMap(), Collections.emptyMap(), false));
			assertFalse(all(PanelInternals.resultsPanel(panel)).stream().anyMatch(c -> c instanceof JLabel
				&& ((JLabel) c).getText() != null && ((JLabel) c).getText().contains("Full fight")));
		});
	}

	private static com.bestgearsetup.calc.Setup fightSetup(int id, String name, String style)
	{
		com.bestgearsetup.data.Gear weapon = new com.bestgearsetup.data.Gear();
		weapon.setId(id);
		weapon.setName(name);
		weapon.setSlot(Slot.WEAPON);
		com.bestgearsetup.calc.Outfit loadout = new com.bestgearsetup.calc.Outfit();
		loadout.set(Slot.WEAPON, weapon);
		loadout.setStyle(com.bestgearsetup.calc.Style.parse(style));
		return new com.bestgearsetup.calc.Setup(Combat.RANGED, loadout, new com.bestgearsetup.calc.Dps(10, 0, 1, 4, 12, null), 0, 0,
			Collections.emptySet(), Collections.emptySet(), null, null);
	}

	private static String texts(java.util.List<Component> shown)
	{
		return shown.stream().map(c -> c instanceof JLabel ? ((JLabel) c).getText() : c instanceof javax.swing.AbstractButton
			? ((javax.swing.AbstractButton) c).getText() : null).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining(" | "));
	}

	private static java.util.List<Component> all(Container root)
	{
		java.util.List<Component> found = new java.util.ArrayList<>();
		for (Component child : root.getComponents())
		{
			found.add(child);
			if (child instanceof Container)
			{
				found.addAll(all((Container) child));
			}
		}
		return found;
	}

	private static Component named(Container container, String name)
	{
		for (Component child : container.getComponents())
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
}
