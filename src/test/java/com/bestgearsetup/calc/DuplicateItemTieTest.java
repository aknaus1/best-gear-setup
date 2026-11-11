package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.function.IntPredicate;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class DuplicateItemTieTest
{
	private final CombatContext ctx = new CombatContext(monster(100, 20), PlayerLevels.maxed(), false, true, piety());
	private GameData data;
	private GearItem avas;
	private GearItem masori;

	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
	}

	@Before
	public void setUp()
	{
		GearItem bow = weapon(861, "magic shortbow", "bow", 4, "accurate,ranged,accurate");
		bow.setRangedBonus(69);
		GearItem arrows = item(892, "rune arrow", Slot.AMMO);
		arrows.setRangedStr(49);
		avas = assembler(22109, "ava's assembler");
		masori = assembler(27374, "masori assembler");
		data = TestData.gameData(Arrays.asList(bow, arrows, avas, masori), Collections.emptyList());
	}

	private static GearItem assembler(int id, String name)
	{
		GearItem cape = item(id, name, Slot.CAPE);
		cape.setRangedBonus(8);
		cape.setRangedStr(2);
		cape.setTradeable(false);
		return cape;
	}

	private GearItem cape(SearchMode mode, IntPredicate owned)
	{
		return cape(mode, owned, false);
	}

	private GearItem cape(SearchMode mode, IntPredicate owned, boolean recovery)
	{
		OptimizerSettings settings = OptimizerSettings.builder().mode(mode).spellbooks(Collections.emptySet())
			.styles(EnumSet.of(AttackStyle.Type.RANGED, AttackStyle.Type.ATLATL))
			.requireAtlatlAmmoRecovery(recovery).build();
		return new Optimizer(data, ctx, settings, owned, GearItem::getPrice)
			.optimize(CombatClass.RANGED, () -> false).get(0).getLoadout().get(Slot.CAPE);
	}

	@Test
	public void kittedVersionWinsAnExactTie()
	{
		assertEquals(masori, cape(SearchMode.UNLIMITED, id -> false));
		assertEquals(masori, cape(SearchMode.UNLIMITED, id -> true));
		assertEquals(masori, cape(SearchMode.OWNED_ONLY, id -> true));
	}

	@Test
	public void kittedVersionWinsWhenAmmunitionRecoveryIsRequired()
	{
		GearItem atlatl = weapon(29000, "eclipse atlatl", "bow", 4, "rapid,ranged,rapid");
		atlatl.setRangedBonus(87);
		atlatl.setMeleeStr(40);
		atlatl.setTwoHanded(true);
		atlatl.setAmmunition(Collections.singletonList(29001));
		data = TestData.gameData(Arrays.asList(atlatl, item(29001, "atlatl dart", Slot.AMMO), avas, masori),
			Collections.emptyList());
		assertEquals(masori, cape(SearchMode.UNLIMITED, id -> true, true));
		assertEquals(masori, cape(SearchMode.OWNED_ONLY, id -> true, true));
		assertEquals(avas, cape(SearchMode.OWNED_ONLY, id -> id != masori.getId(), true));
	}

	@Test
	public void heldCopyWinsOverAnUnheldKittedOne()
	{
		assertEquals(avas, cape(SearchMode.UNLIMITED, id -> id == avas.getId()));
	}
}
