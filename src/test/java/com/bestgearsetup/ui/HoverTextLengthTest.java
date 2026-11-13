package com.bestgearsetup.ui;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.bestgearsetup.BestGearSetupConfig;
import com.bestgearsetup.BestGearSetupPlugin;
import com.bestgearsetup.OwnedItems;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.SetupResult;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.ContainerEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;

/**
 * Hover text stays under 10 words in total, line breaks included: every tooltip a component shows, every setting
 * and section description (shown as hover text in the panels and RuneLite's config), and every alternative of a
 * tooltip call's fixed text. Item names and numbers count as words.
 */
public class HoverTextLengthTest
{
	public static final int MAX_WORDS = 9;
	private static final String[] TIP_CALLS = {"setToolTipText(", "setToolTip(", "new Tooltip(", ".tooltip("};

	@Test
	public void tooltipCallsAreUnderTenWords() throws IOException
	{
		List<String> tooLong = new ArrayList<>();
		List<Path> sources;
		try (Stream<Path> files = Files.walk(Paths.get("src/main/java")))
		{
			sources = files.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
		}
		for (Path source : sources)
		{
			String code = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
			for (String tipCall : TIP_CALLS)
			{
				int from = 0;
				int call;
				while ((call = code.indexOf(tipCall, from)) >= 0)
				{
					from = call + tipCall.length();
					for (String text : alternatives(code, from))
					{
						if (words(text) > MAX_WORDS) { tooLong.add(source.getFileName() + ": " + text.trim()); }
					}
				}
			}
		}
		assertTrue("Hover text over " + MAX_WORDS + " words:\n" + String.join("\n", tooLong), tooLong.isEmpty());
	}

	@Test
	public void itemHoverKeepsWhereItIsStoredWithinTheLimit() throws Exception
	{
		GearItem amulet = new GearItem();
		amulet.setId(12851);
		amulet.setName("amulet of the damned (full)");
		amulet.setSlot(Slot.NECK);
		Loadout loadout = new Loadout();
		loadout.set(Slot.NECK, amulet);
		BestGearSetupPlugin plugin = mock(BestGearSetupPlugin.class);
		when(plugin.getExcluded()).thenReturn(Collections.emptySet());
		when(plugin.getOwnedItems()).thenReturn(mock(OwnedItems.class));
		when(plugin.owns(amulet)).thenReturn(true);
		ItemManager images = mock(ItemManager.class);
		when(images.getImage(anyInt())).thenReturn(mock(AsyncBufferedImage.class));
		SwingUtilities.invokeAndWait(() ->
		{
			EquipmentGrid grid = new EquipmentGrid(new SetupResult(CombatClass.MELEE, loadout, null, 0),
				Collections.emptyMap(), plugin, images, null, 0);
			JComponent neck = (JComponent) grid.getComponent(4);
			MouseEvent hover = new MouseEvent(neck, MouseEvent.MOUSE_MOVED, 0, 0, 1, 1, 0, false);
			when(plugin.itemLocations(amulet)).thenReturn(Collections.singleton("STASH: Bob's <tower>"));
			String tip = neck.getToolTipText(hover);
			assertTrue(tip, tip.contains("Location: STASH: Bob's &lt;tower&gt;"));
			assertTrue(tip, words(tip) <= MAX_WORDS);
			when(plugin.itemLocations(amulet)).thenReturn(new LinkedHashSet<>(Arrays.asList("Bank", "POH: Costume room",
				"STASH: Bob's tower", "Marked by hand")));
			tip = neck.getToolTipText(hover);
			assertTrue(tip, tip.contains("Location: Bank +3"));
			assertTrue(tip, words(tip) <= MAX_WORDS);
			// A place too long to fit is still shown: it is the only place the panel says where the item is.
			when(plugin.itemLocations(amulet)).thenReturn(Collections.singleton("STASH: Gnome Stronghold slayer cave"));
			assertTrue(neck.getToolTipText(hover).contains("Location: STASH: Gnome Stronghold slayer cave"));
		});
	}

	@Test
	public void settingAndSectionDescriptionsAreUnderTenWords()
	{
		List<String> tooLong = new ArrayList<>();
		for (Method method : BestGearSetupConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null && words(item.description()) > MAX_WORDS) { tooLong.add(item.keyName() + ": " + item.description()); }
		}
		for (Field field : BestGearSetupConfig.class.getDeclaredFields())
		{
			ConfigSection section = field.getAnnotation(ConfigSection.class);
			if (section != null && words(section.description()) > MAX_WORDS) { tooLong.add(section.name() + ": " + section.description()); }
		}
		assertTrue("Descriptions over " + MAX_WORDS + " words:\n" + String.join("\n", tooLong), tooLong.isEmpty());
	}

	/** Every tooltip in {@code root}'s component tree has at most {@link #MAX_WORDS} words. */
	public static void assertShortTips(Component root)
	{
		List<String> tooLong = new ArrayList<>();
		collect(root, tooLong);
		assertTrue("Hover text over " + MAX_WORDS + " words:\n" + String.join("\n", tooLong), tooLong.isEmpty());
	}

	private static final List<Component> ADDED = Collections.synchronizedList(new ArrayList<>());
	private static boolean listening;

	/** Starts recording every component added to a container, for {@link #assertTrackedTipsShort}. */
	public static synchronized void trackTips()
	{
		ADDED.clear();
		if (listening) { return; }
		Toolkit.getDefaultToolkit().addAWTEventListener(event ->
		{
			if (event.getID() == ContainerEvent.COMPONENT_ADDED) { ADDED.add(((ContainerEvent) event).getChild()); }
		}, AWTEvent.CONTAINER_EVENT_MASK);
		listening = true;
	}

	/** Every component added since {@link #trackTips} shows a tooltip of at most {@link #MAX_WORDS} words now. */
	public static void assertTrackedTipsShort() throws Exception
	{
		List<String> tooLong = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			synchronized (ADDED)
			{
				for (Component component : ADDED) { collect(component, tooLong); }
				ADDED.clear();
			}
		});
		List<String> distinct = tooLong.stream().distinct().collect(Collectors.toList());
		assertTrue("Hover text over " + MAX_WORDS + " words:\n" + String.join("\n", distinct), distinct.isEmpty());
	}

	private static void collect(Component component, List<String> tooLong)
	{
		if (component instanceof JComponent)
		{
			String tip = ((JComponent) component).getToolTipText();
			if (tip != null && words(tip) > MAX_WORDS) { tooLong.add(component.getClass().getSimpleName() + ": " + tip); }
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents()) { collect(child, tooLong); }
		}
	}

	/** The call's fixed text from {@code start}, split at {@code ?} / {@code :} alternatives. */
	private static List<String> alternatives(String code, int start)
	{
		List<String> out = new ArrayList<>();
		StringBuilder text = new StringBuilder();
		int depth = 1;
		for (int i = start; i < code.length() && depth > 0; i++)
		{
			char c = code.charAt(i);
			if (c == '"')
			{
				int end = i + 1;
				while (code.charAt(end) != '"')
				{
					if (code.charAt(end) == '\\') { end++; }
					text.append(code.charAt(end++));
				}
				text.append(' ');
				i = end;
			}
			else if (c == '(') { depth++; }
			else if (c == ')') { depth--; }
			else if (c == '?' || c == ':')
			{
				out.add(text.toString());
				text.setLength(0);
			}
		}
		out.add(text.toString());
		return out;
	}

	/** Words in hover text: HTML tags and line breaks are spaces; a word has a letter or digit. */
	public static int words(String text)
	{
		String plain = text.replaceAll("<[^>]*>|&[a-z]+;", " ").trim();
		if (plain.isEmpty()) { return 0; }
		int count = 0;
		for (String word : plain.split("\\s+"))
		{
			if (word.chars().anyMatch(Character::isLetterOrDigit)) { count++; }
		}
		return count;
	}
}
