package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestgearsetup.calc.SlotLock;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.ui.PanelInternals;
import com.bestgearsetup.ui.Sidebar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** "Use my GP" fills the budget with the coins and platinum tokens the searches see as owned. */
public class UseMyGpTest
{
	private final List<String> writes = new ArrayList<>();

	private static Map<Integer, Long> stacks(long coins, long tokens)
	{
		Map<Integer, Long> stacks = new HashMap<>();
		if (coins > 0) { stacks.put(ItemID.COINS, coins); }
		if (tokens > 0) { stacks.put(ItemID.PLATINUM, tokens); }
		return stacks;
	}

	private static OwnedItems owned(boolean bankKnown, Map<Integer, Long> bank, Map<Integer, Long> inventory)
	{
		OwnedItems owned = new OwnedItems(null, null)
		{
			@Override
			public boolean isBankKnown()
			{
				return bankKnown;
			}
		};
		owned.bank = bank;
		owned.inventory = inventory;
		owned.rebuild();
		return owned;
	}

	private Sidebar panel(OwnedItems owned) throws Exception
	{
		BestGearSetupPlugin plugin = new BestGearSetupPlugin()
		{
			@Override public net.runelite.client.config.ConfigDescriptor getConfigDescriptor()
			{ return ConfigItemsForTests.descriptor(); }
			@Override public Map<Slot, SlotLock> getLocks() { return Collections.emptyMap(); }
			@Override public Set<Integer> getExcluded() { return Collections.emptySet(); }
			@Override public void setConfig(String key, Object value) { writes.add(key + "=" + value); }
		};
		plugin.ownedItems = owned;
		Sidebar[] panels = new Sidebar[1];
		SwingUtilities.invokeAndWait(() ->
		{
			panels[0] = new Sidebar(plugin, new Options() {}, null, null, null);
			panels[0].updateOwnedStatus();
		});
		return panels[0];
	}

	@Test
	public void coinsAndPlatinumTokensAddUpAcrossTheBankAndInventory()
	{
		assertEquals(3_500_000, OwnedItems.coins(stacks(1_500_000, 2_000)));
		assertEquals(1_253_000, owned(true, stacks(1_000_000, 3), stacks(250_000, 0)).coins());
		assertEquals(0, OwnedItems.coins(Collections.emptyMap()));
	}

	@Test
	public void hugeOrUncountedStacksNeitherOverflowNorCount()
	{
		// A stack marked owned by hand has no count.
		assertEquals(0, OwnedItems.coins(Collections.singletonMap(ItemID.COINS, OwnedItems.UNLIMITED)));
		assertEquals(OwnedItems.UNLIMITED, OwnedItems.coins(stacks(Integer.MAX_VALUE, Long.MAX_VALUE / 10)));
		assertTrue(OwnedItems.coins(stacks(Integer.MAX_VALUE, Integer.MAX_VALUE)) > 0);
		// The field rounds amounts to what it shows; the filled budget never rounds above the coins.
		assertEquals(1_230_000, Budget.floorShown(1_235_999));
		assertEquals(2_140_000_000L, Budget.floorShown(2_147_483_647L));
		assertEquals(12_340, Budget.floorShown(12_345));
		assertEquals(9_999, Budget.floorShown(9_999));
		assertTrue(Budget.parse(Budget.format(Budget.floorShown(1_235_999))) <= 1_235_999);
	}

	@Test
	public void theButtonDoesWhatTypingTheAmountDoes() throws Exception
	{
		Sidebar panel = panel(owned(true, stacks(1_200_000, 34), stacks(567, 0)));
		JTextField field = PanelInternals.budgetField(panel);
		JButton button = PanelInternals.useGpButton(panel);
		SwingUtilities.invokeAndWait(() ->
		{
			assertTrue(button.isEnabled());
			assertEquals("Set the budget to your coins and platinum tokens", button.getToolTipText());
			button.doClick();
		});
		assertEquals(Collections.singletonList("budget=1.23m"), writes);
		SwingUtilities.invokeAndWait(() ->
		{
			assertEquals("1.23m", field.getText());
			field.setText("1234567");
			field.postActionEvent();
		});
		assertEquals(Collections.nCopies(2, "budget=1.23m"), writes);
	}

	@Test
	public void beforeTheBankIsOpenedItUsesWhatIsKnownAndSaysSo() throws Exception
	{
		Sidebar panel = panel(owned(false, Collections.emptyMap(), stacks(25_000, 0)));
		JButton button = PanelInternals.useGpButton(panel);
		SwingUtilities.invokeAndWait(() ->
		{
			assertTrue(button.isEnabled());
			assertEquals("Bank coins count once you open your bank", button.getToolTipText());
			button.doClick();
		});
		assertEquals(Collections.singletonList("budget=25k"), writes);
	}

	@Test
	public void withNoKnownItemsTheButtonIsOffAndSaysWhy() throws Exception
	{
		Sidebar panel = panel(owned(false, Collections.emptyMap(), Collections.emptyMap()));
		JButton button = PanelInternals.useGpButton(panel);
		SwingUtilities.invokeAndWait(() ->
		{
			assertFalse(button.isEnabled());
			assertEquals("No items seen yet; log in first", button.getToolTipText());
		});
		assertTrue(writes.isEmpty());
	}
}
