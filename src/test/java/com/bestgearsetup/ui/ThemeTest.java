package com.bestgearsetup.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.Options;
import com.bestgearsetup.data.Combat;
import com.bestgearsetup.calc.Dps;
import com.bestgearsetup.calc.Outfit;
import com.bestgearsetup.calc.Setup;
import com.bestgearsetup.calc.Style;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Gear;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Summary;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.components.ColorJButton;
import org.junit.After;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

public class ThemeTest
{

	@After
	public void backToClassic()
	{
		Theme.use(Theme.CLASSIC);
		Theme.setCustom("");
	}

	@Test
	public void classicIsTheDefault()
	{
		assertEquals(Theme.CLASSIC, new Options() {}.theme());
	}

	@Test
	public void everyThemeLoadsAndNamesOnlyClassicsKeysAndKnownPainters()
	{
		Set<String> keys = Theme.CLASSIC.data().keySet();
		for (Theme theme : Theme.values())
		{
			for (String key : theme.data().keySet())
			{
				assertTrue(theme + " has a key Classic lacks, so it has no fallback: " + key, keys.contains(key));
			}
			Theme.use(theme);
			assertEquals(theme + " is valid as an import", null, Theme.check(Theme.export(theme)));
			for (String key : keys)
			{
				if (Theme.CLASSIC.data().get(key).getAsString().startsWith("#"))
				{
					Theme.color(key);
				}
			}
		}
	}

	@Test
	public void theMainButtonIsAsTallAsClassicsInThePaintedThemes() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			// Classic's height is FlatLaf's minimum height, which these tests' look and feel doesn't apply
			for (Theme theme : new Theme[]{Theme.FORGED, Theme.EDGE})
			{
				Theme.use(theme);
				Sidebar panel = sidebar();
				render(panel);
				assertEquals(theme.toString(), Controls.PRIMARY_HEIGHT, panel.findButton.getHeight());
			}
		});
	}

	@Test
	public void everyThemeExportedAndImportedAsCustomLooksTheSame() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			for (Theme theme : Theme.values())
			{
				if (theme == Theme.CUSTOM)
				{
					continue;
				}
				Theme.use(theme);
				BufferedImage builtIn = render(sidebar());
				Theme.setCustom(Theme.export(theme));
				Theme.use(Theme.CUSTOM);
				assertSamePixels("Custom from " + theme, builtIn, render(sidebar()));
			}
		});
	}

	@Test
	public void importsAreCheckedButKeysFromOtherVersionsAreIgnored()
	{
		assertTrue(Theme.check("{").contains("isn't a theme"));
		assertTrue(Theme.check("[1, 2]").contains("isn't a theme"));
		assertTrue(Theme.check("{\"accent\": \"orange\"}").contains("accent must be a colour"));
		assertTrue(Theme.check("{\"primary\": \"laser\"}").contains("primary must be one of"));
		assertTrue(Theme.check("{\"secondary\": \"ribbon\"}").contains("secondary must be one of"));
		assertEquals(null, Theme.check("{\"wiki\": \"ribbon\", \"sparkles\": \"lots\", \"card\": \"#123456\"}"));
		assertTrue("JSON with no theme keys isn't a theme", Theme.check("{}").contains("isn't a theme"));
		assertTrue(Theme.check("{\"sparkles\": \"lots\"}").contains("isn't a theme"));
		assertEquals("an export of a Custom that is all Classic has only its comment", null,
			Theme.check(Theme.export(Theme.CUSTOM)));
	}

	@Test
	public void theEditorShowsTheThemeItsFirstChangeStartsFrom() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ThemeEditor editor = editor(new HashMap<>(), Theme.FORGED, "", Color.RED, new Theme[1]);
			button(editor, "Cards").doClick();
			assertEquals(Color.decode(Theme.FORGED.get("card")), swatch(editor, "card").getColor());
			assertEquals("chamfer", frames(editor).getSelectedItem());
			Theme.setCustom(editor.exported());
			assertEquals("Export copies the theme shown", "forged", Theme.CUSTOM.get("primary"));
		});
	}

	@Test
	public void withCustomSavedTheEditorShowsAndChangesCustomWhateverThePanelShows() throws Exception
	{
		Map<String, Object> saved = new HashMap<>();
		SwingUtilities.invokeAndWait(() ->
		{
			ThemeEditor editor = editor(saved, Theme.FORGED, "{\"card\": \"#123456\"}", Color.RED, new Theme[1]);
			button(editor, "Cards").doClick();
			assertEquals(new Color(0x123456), swatch(editor, "card").getColor());
			assertEquals("Custom's frame, not Forged's", "flat", frames(editor).getSelectedItem());
			frames(editor).setSelectedItem("plank");
		});
		Theme.setCustom((String) saved.get(Options.CUSTOM_THEME_KEY));
		assertEquals("plank", Theme.CUSTOM.get("frame"));
		assertEquals("#123456", Theme.CUSTOM.get("card"));
		assertEquals("Custom's painters, not Forged's", "flat", Theme.CUSTOM.get("primary"));
		assertEquals(Theme.CUSTOM, saved.get(Options.THEME_KEY));
	}

	@Test
	public void anotherProfilesCustomStartsTheUndoHistoryAgain() throws Exception
	{
		Map<String, Object> saved = new HashMap<>();
		SwingUtilities.invokeAndWait(() ->
		{
			ThemeEditor editor = editor(saved, Theme.CLASSIC, "", Color.RED, new Theme[1]);
			frames(editor).setSelectedItem("plank");
			assertTrue(button(editor, "Undo").isVisible());
			saved.put(Options.CUSTOM_THEME_KEY, "{\"card\": \"#654321\"}");
			editor.refresh();
			assertFalse("Undo would write this profile's Custom over the other's", button(editor, "Undo").isVisible());
		});
	}

	@Test
	public void pickingWithEveryUseChangesEachPartInThatColourAndPreviewsItAsCustom() throws Exception
	{
		Map<String, Object> saved = new HashMap<>();
		Theme[] previewed = new Theme[1];
		SwingUtilities.invokeAndWait(() ->
		{
			ThemeEditor editor = editor(saved, Theme.CLASSIC, "", new Color(0x123456), previewed);
			find(editor, JCheckBox.class, box -> true).setSelected(true);
			button(editor, "Cards").doClick();
			swatch(editor, "accent").doClick();
		});
		assertEquals("the colour shows on the panel before it is saved", Theme.CUSTOM, previewed[0]);
		Theme.setCustom((String) saved.get(Options.CUSTOM_THEME_KEY));
		for (String key : new String[]{"accent", "cardEdge", "primaryFill", "focus", "badge", "progress", "boss"})
		{
			assertEquals(key, "#123456", Theme.CUSTOM.get(key));
		}
		assertEquals("other colours stay", Theme.CLASSIC.get("card"), Theme.CUSTOM.get("card"));
	}

	@Test
	public void undoStepsBackThroughEachChangeToTheThemeShownBefore() throws Exception
	{
		Map<String, Object> saved = new HashMap<>();
		SwingUtilities.invokeAndWait(() ->
		{
			ThemeEditor editor = editor(saved, Theme.FORGED, "", Color.RED, new Theme[1]);
			JButton undo = button(editor, "Undo");
			assertFalse("nothing to undo yet", undo.isVisible());
			frames(editor).setSelectedItem("plank");
			button(editor, "Reset").doClick();
			assertEquals("", saved.get(Options.CUSTOM_THEME_KEY));
			undo.doClick();
			Theme.setCustom((String) saved.get(Options.CUSTOM_THEME_KEY));
			assertEquals("plank", Theme.CUSTOM.get("frame"));
			assertEquals(Theme.CUSTOM, saved.get(Options.THEME_KEY));
			undo.doClick();
			assertEquals(Theme.FORGED, saved.get(Options.THEME_KEY));
			assertEquals("", saved.get(Options.CUSTOM_THEME_KEY));
			assertFalse(undo.isVisible());
		});
	}

	@Test
	public void startFromKeepsTheThemePickedInItWhileTheEditorRefreshes() throws Exception
	{
		Map<String, Object> saved = new HashMap<>();
		SwingUtilities.invokeAndWait(() ->
		{
			ThemeEditor editor = editor(saved, Theme.CUSTOM, "{\"card\": \"#123456\"}", Color.RED, new Theme[1]);
			JComboBox<?> base = find(editor, JComboBox.class, c -> c.getItemAt(0) == Theme.CLASSIC);
			base.setSelectedItem(Theme.INFERNO);
			button(editor, "Cards").doClick();
			editor.refresh();
			assertEquals("opening a group or a refresh keeps the pick", Theme.INFERNO, base.getSelectedItem());
			button(editor, "Start from").doClick();
		});
		Theme.setCustom((String) saved.get(Options.CUSTOM_THEME_KEY));
		assertEquals("molten", Theme.CUSTOM.get("primary"));
	}

	@Test
	public void closingThePickerOnTheColourItOpenedWithChangesNothing() throws Exception
	{
		Map<String, Object> saved = new HashMap<>();
		Theme[] previewed = new Theme[1];
		SwingUtilities.invokeAndWait(() ->
		{
			ThemeEditor editor = editor(saved, Theme.FORGED, "", Color.decode(Theme.FORGED.get("card")), previewed);
			button(editor, "Cards").doClick();
			swatch(editor, "card").doClick();
			assertFalse("nothing to undo", button(editor, "Undo").isVisible());
		});
		assertEquals("the panel stays on Forged", Theme.FORGED, saved.get(Options.THEME_KEY));
		assertEquals("", saved.get(Options.CUSTOM_THEME_KEY));
		assertEquals("the preview is put back", Theme.FORGED, Theme.current());
	}

	@Test
	public void swatchesKeepTextThatContrastsWithTheirColourThroughARestyle() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			ThemeEditor editor = editor(new HashMap<>(), Theme.CLASSIC, "", Color.RED, new Theme[1]);
			button(editor, "Cards").doClick();
			Controls.restyle(editor);
			assertEquals("dark text on the orange card edge", Color.BLACK, swatch(editor, "cardEdge").getForeground());
		});
	}

	@Test
	public void hardToReadTextIsFlaggedButNoBuiltInThemesIs()
	{
		for (Theme theme : Theme.values())
		{
			assertNull(theme.toString(), ThemeEditor.hardToRead(theme,
				Arrays.asList("panel", "cards", theme.get("primary"), theme.get("secondary"), theme.get("wiki"))));
		}
		Theme.setCustom("{\"buttonText\": \"#404040\", \"buttonFill\": \"#363636\"}");
		String flagged = ThemeEditor.hardToRead(Theme.CUSTOM, Arrays.asList("panel", "cards", "flat"));
		assertTrue(flagged, flagged.startsWith("Button text (1."));
	}

	/** An editor whose settings live in {@code saved}, opened on {@code shown} with {@code custom} saved. */
	private static ThemeEditor editor(Map<String, Object> saved, Theme shown, String custom, Color picked,
		Theme[] previewed)
	{
		saved.put(Options.THEME_KEY, shown);
		saved.put(Options.CUSTOM_THEME_KEY, custom);
		Theme.setCustom(custom);
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override
			public void setConfig(String key, Object value)
			{
				saved.put(key, value);
			}

			@Override
			public void pickColor(java.awt.Component owner, Color current, String title, Consumer<Color> onChange,
				Consumer<Color> onClose)
			{
				onChange.accept(picked);
				previewed[0] = Theme.current();
				onClose.accept(picked);
			}
		};
		return new ThemeEditor(plugin, new Options()
		{
			@Override
			public Theme theme()
			{
				return (Theme) saved.get(Options.THEME_KEY);
			}

			@Override
			public String customTheme()
			{
				return (String) saved.get(Options.CUSTOM_THEME_KEY);
			}
		});
	}

	private static <T> T find(java.awt.Container root, Class<T> type, Predicate<T> test)
	{
		return all(root).stream().filter(type::isInstance).map(type::cast).filter(test).findFirst().get();
	}

	private static JButton button(java.awt.Container root, String text)
	{
		return find(root, JButton.class, b -> text.equals(b.getText()));
	}

	private static ColorJButton swatch(java.awt.Container root, String key)
	{
		String tip = "Pick the " + ThemeEditor.label(key).toLowerCase(java.util.Locale.ROOT);
		return find(root, ColorJButton.class, b -> tip.equals(b.getToolTipText()));
	}

	/** The card frame's drop-down, the only one with three choices. */
	private static JComboBox<?> frames(java.awt.Container root)
	{
		return find(root, JComboBox.class, c -> c.getItemCount() == 3);
	}

	@Test
	public void aCorruptSavedThemeKeepsItsGoodValuesAndStillPaints() throws Exception
	{
		Theme.setCustom("{\"accent\": \"red\", \"primary\": \"laser\", \"card\": \"#123456\", \"frame\": 7}");
		Theme.use(Theme.CUSTOM);
		assertEquals(new java.awt.Color(0x123456), Theme.color("card"));
		assertEquals("Classic's accent stands in for the bad one", Theme.CLASSIC.get("accent"), Theme.CUSTOM.get("accent"));
		assertEquals("flat", Theme.style("primary"));
		SwingUtilities.invokeAndWait(() -> render(sidebar()));
		Theme.setCustom("not json at all");
		assertEquals(Theme.CLASSIC.get("card"), Theme.CUSTOM.get("card"));
	}

	@Test
	public void editingWhileAnotherThemeShowsStartsCustomFromItAndSwitchesToCustom() throws Exception
	{
		Map<String, Object> saved = new java.util.HashMap<>();
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override
			public void setConfig(String key, Object value)
			{
				saved.put(key, value);
			}
		};
		Options forged = new Options()
		{
			@Override
			public Theme theme()
			{
				return Theme.FORGED;
			}
		};
		Theme.setCustom("");
		SwingUtilities.invokeAndWait(() ->
		{
			ThemeEditor editor = new ThemeEditor(plugin, forged);
			assertTrue("Opening the editor saves nothing", saved.isEmpty());
			javax.swing.JComboBox<?> cards = (javax.swing.JComboBox<?>) all(editor).stream()
				.filter(c -> c instanceof javax.swing.JComboBox && ((javax.swing.JComboBox<?>) c).getItemCount() == 3)
				.findFirst().get();
			cards.setSelectedItem("plank");
		});
		assertEquals(Theme.CUSTOM, saved.get(Options.THEME_KEY));
		Theme.setCustom((String) saved.get(Options.CUSTOM_THEME_KEY));
		assertEquals("plank", Theme.CUSTOM.get("frame"));
		assertEquals("Forged's painters came along", "forged", Theme.CUSTOM.get("primary"));
		assertEquals(Theme.FORGED.get("card"), Theme.CUSTOM.get("card"));
	}

	private static List<java.awt.Component> all(java.awt.Container root)
	{
		List<java.awt.Component> found = new java.util.ArrayList<>();
		for (java.awt.Component child : root.getComponents())
		{
			found.add(child);
			if (child instanceof java.awt.Container)
			{
				found.addAll(all((java.awt.Container) child));
			}
		}
		return found;
	}

	@Test
	public void switchingThemeLiveLooksTheSameAsOpeningThePanelInIt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			// Every theme, then back across painters: flat to forged, forged to edge, edge to flat
			List<Theme> order = new java.util.ArrayList<>(Arrays.asList(Theme.values()));
			order.addAll(Arrays.asList(Theme.FORGED, Theme.EDGE, Theme.CLASSIC));
			Theme.use(Theme.CLASSIC);
			Sidebar live = sidebar();
			for (Theme theme : order)
			{
				Theme.use(theme);
				live.applyTheme();
				assertSamePixels(theme.toString(), render(sidebar()), render(live));
			}
		});
	}

	private static Sidebar sidebar()
	{
		BestGearSetupPlugin plugin = Mockito.mock(BestGearSetupPlugin.class, Answers.RETURNS_MOCKS);
		Mockito.doReturn(com.bestgearsetup.ConfigItemsForTests.descriptor()).when(plugin).getConfigDescriptor();
		Mockito.doReturn(com.bestgearsetup.ConfigItemsForTests.emptyOwnedItems()).when(plugin).getOwnedItems();
		net.runelite.client.game.ItemManager images = Mockito.mock(net.runelite.client.game.ItemManager.class);
		Mockito.when(images.getImage(Mockito.anyInt()))
			.thenAnswer(i -> new net.runelite.client.util.AsyncBufferedImage(null, 32, 32, BufferedImage.TYPE_INT_ARGB));
		Sidebar panel = new Sidebar(plugin, new Options() {}, images,
			Mockito.mock(net.runelite.client.game.SpriteManager.class),
			Mockito.mock(net.runelite.client.game.SkillIconManager.class));
		Summary target = new Summary();
		target.setName("Vorkath");
		target.setCombatLevel(732);
		panel.onDataLoaded(new GameData(Collections.singletonList(target), Collections.emptyMap(),
			Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap()));
		panel.selectMonster(target, false);
		Monster monster = new Monster();
		monster.setName("Vorkath");
		monster.setHitpoints(750);
		Map<Style.Type, List<Setup>> byType = new EnumMap<>(Style.Type.class);
		byType.put(Style.Type.SLASH, Arrays.asList(setup("Dragon hunter lance", 13), setup("Ghrazi rapier", 12)));
		byType.put(Style.Type.CRUSH, Collections.singletonList(setup("Inquisitor's mace", 10)));
		panel.showResults(com.bestgearsetup.TestResults.of(monster, 750, byType, Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), false));
		return panel;
	}

	private static Setup setup(String weaponName, double dps)
	{
		Gear weapon = new Gear();
		weapon.setId(100);
		weapon.setName(weaponName);
		weapon.setSlot(Slot.WEAPON);
		Outfit loadout = new Outfit();
		loadout.set(Slot.WEAPON, weapon);
		loadout.setStyle(Style.parse("chop,slash,aggressive"));
		return new Setup(Combat.MELEE, loadout, new Dps(dps, 0, 0.9, 4, 12, null), 0, 0,
			Collections.emptySet(), Collections.emptySet(), null, null);
	}

	private static BufferedImage render(Sidebar panel)
	{
		panel.setSize(242, 900);
		for (int pass = 0; pass < 4; pass++)
		{
			layout(panel);
		}
		BufferedImage image = new BufferedImage(242, 900, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = image.createGraphics();
		panel.printAll(g);
		g.dispose();
		return image;
	}

	private static void layout(java.awt.Container container)
	{
		container.doLayout();
		for (java.awt.Component child : container.getComponents())
		{
			if (child instanceof java.awt.Container)
			{
				layout((java.awt.Container) child);
			}
		}
	}

	private static void assertSamePixels(String theme, BufferedImage expected, BufferedImage actual)
	{
		for (int y = 0; y < expected.getHeight(); y++)
		{
			for (int x = 0; x < expected.getWidth(); x++)
			{
				if (expected.getRGB(x, y) != actual.getRGB(x, y))
				{
					throw new AssertionError("Switching to " + theme + " differs from opening in it at " + x + "," + y);
				}
			}
		}
	}
}
