package com.bestgearsetup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import com.bestgearsetup.calc.Loadout;
import com.bestgearsetup.calc.PlayerLevels;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.Slot;
import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

public class SearchInputsTest
{
	private final BestGearSetupConfig config = new BestGearSetupConfig() { @Override public int ammoCount() { return 100; } };
	private final MonsterSummary target = new MonsterSummary();
	private final Set<Integer> owned = new HashSet<>(Set.of(9242, 379));
	private final Map<Integer, Long> quantities = new HashMap<>(Map.of(9242, 159L, 379, 28L));
	private final Map<Integer, Long> prices = new HashMap<>(Map.of(9242, 100L));
	private final Set<String> prayers = new HashSet<>();
	private final LiveRaid live = new LiveRaid();
	private final List<String> notes = new ArrayList<>();
	private final GameData data;

	public SearchInputsTest()
	{
		GearItem bolts = new GearItem();
		bolts.setId(9242); bolts.setName("ruby bolts (e)"); bolts.setSlot(Slot.AMMO); bolts.setSubcategory("bolt");
		bolts.setRangedStr(103);
		GearItem whip = new GearItem();
		whip.setId(4151); whip.setName("abyssal whip"); whip.setSlot(Slot.WEAPON);
		data = new GameData(Collections.emptyList(), Map.of(Slot.AMMO.getApiKey(), List.of(bolts)),
			List.of(whip), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
		target.setId(1); target.setName("boss");
	}

	private SearchInputs key()
	{
		return SearchInputs.capture(data, data, target, PlayerLevels.maxed(), false, prayers, true, false,
			99, prices, owned, quantities, config, live, notes, false);
	}

	@Test
	public void keysSnapshotMutableInputsAndCompareTheirValues()
	{
		SearchInputs before = key();
		int hash = before.hashCode();
		assertEquals(before, key());
		prices.put(9242, 101L);
		assertNotEquals(before, key());
		assertEquals(hash, before.hashCode());
		prices.put(9242, 100L);
		assertEquals(before, key());
		owned.add(4151); assertNotEquals(before, key()); owned.remove(4151);
		owned.add(995); assertEquals("Items no search reads, like coins, must not change the key", before, key()); owned.remove(995);
		prayers.add("rigour"); assertNotEquals(before, key()); prayers.clear();
		live.pathLevels.put("zebak", 2); assertNotEquals(before, key()); live.pathLevels.clear();
		live.maxCombat = 120; assertNotEquals(before, key()); live.maxCombat = 0;
		live.raidDetected = true; assertNotEquals(before, key()); live.raidDetected = false;
		live.values.put("raidPartySize", 4); assertNotEquals(before, key()); live.values.clear();
		notes.add("unknown diary"); assertNotEquals(before, key()); notes.clear();
		target.setName("other boss"); assertNotEquals(before, key()); target.setName("boss");
		assertEquals(before, key());
	}

	@Test
	public void onlyRelevantCappedAmmunitionQuantitiesMatter()
	{
		SearchInputs before = key();
		quantities.put(379, 10L);
		quantities.put(9242, 100L);
		assertEquals(before, key());
		quantities.put(9242, 99L);
		assertNotEquals(before, key());
	}

	@Test
	public void allCalculationSettingsParticipateAndPresentationSettingsDoNot() throws Exception
	{
		for (Method method : BestGearSetupConfig.class.getDeclaredMethods())
		{
			if (!method.isAnnotationPresent(ConfigItem.class)) { continue; }
			Object value = method.invoke(config);
			Object different = value instanceof Boolean ? !(Boolean) value : value instanceof Integer ? (Integer) value + 1
				: value instanceof Double ? (Double) value + 1 : value instanceof String ? value + "changed"
				: value instanceof Color ? Color.PINK
				: value.getClass().getEnumConstants()[(((Enum<?>) value).ordinal() + 1) % value.getClass().getEnumConstants().length];
			BestGearSetupConfig changed = SearchAssumptions.capture(config, Collections.singletonMap(method.getName(), different));
			for (boolean encounter : new boolean[]{false, true})
			{
				assertEquals(method.getName(), SearchInputs.PRESENTATION.contains(method.getName())
					|| encounter && SearchInputs.KIT_ONLY.contains(method.getName()),
					SearchInputs.settings(config, encounter).equals(SearchInputs.settings(changed, encounter)));
			}
		}
	}

	@Test
	public void switchBaseKeyDoesNotChangeWhenItsLoadoutIsMutated()
	{
		Loadout loadout = new Loadout();
		GearItem weapon = new GearItem(); weapon.setId(4151);
		loadout.set(Slot.WEAPON, weapon);
		SearchInputs before = key().with(SearchInputs.loadout(loadout));
		assertEquals(before, key().with(SearchInputs.loadout(loadout.copy())));
		loadout.set(Slot.WEAPON, null);
		assertNotEquals(before, key().with(SearchInputs.loadout(loadout)));
		loadout.set(Slot.WEAPON, weapon);
		assertEquals(before, key().with(SearchInputs.loadout(loadout)));
	}
}
