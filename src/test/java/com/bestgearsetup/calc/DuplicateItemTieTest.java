package com.bestgearsetup.calc;

import static com.bestgearsetup.calc.TestData.item;
import static com.bestgearsetup.calc.TestData.monster;
import static com.bestgearsetup.calc.TestData.piety;
import static com.bestgearsetup.calc.TestData.weapon;
import static org.junit.Assert.assertEquals;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.Combat;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Gear;
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
	private final CombatContext ctx = TestFactories.combatContext(monster(100, 20), PlayerLevels.maxed(), false, true, piety());
	private GameData data;
	private Gear avas;
	private Gear masori;

	@BeforeClass
	public static void ownershipRules()
	{
		OwnershipRules.init(RuneLiteAPI.GSON);
	}

	@Before
	public void setUp()
	{
		Gear bow = weapon(861, "magic shortbow", "bow", 4, "accurate,ranged,accurate");
		bow.setRangedBonus(69);
		Gear arrows = item(892, "rune arrow", Slot.AMMO);
		arrows.setRangedStr(49);
		avas = assembler(22109, "ava's assembler");
		masori = assembler(27374, "masori assembler");
		data = TestData.gameData(Arrays.asList(bow, arrows, avas, masori), Collections.emptyList());
	}

	private static Gear assembler(int id, String name)
	{
		Gear cape = item(id, name, Slot.CAPE);
		cape.setRangedBonus(8);
		cape.setRangedStr(2);
		cape.setTradeable(false);
		return cape;
	}

	private Gear cape(SearchMode mode, IntPredicate owned)
	{
		return cape(mode, owned, false);
	}

	private Gear cape(SearchMode mode, IntPredicate owned, boolean recovery)
	{
		OptimizerSettings settings = OptimizerSettings.builder().mode(mode).spellbooks(Collections.emptySet())
			.styles(EnumSet.of(Style.Type.RANGED, Style.Type.ATLATL))
			.requireAtlatlAmmoRecovery(recovery).build();
		return com.bestgearsetup.calc.Optimizers.of(data, ctx, settings, owned, Gear::getPrice)
			.optimize(Combat.RANGED, () -> false, done -> { }).get(0).getLoadout().get(Slot.CAPE);
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
		Gear atlatl = weapon(29000, "eclipse atlatl", "bow", 4, "rapid,ranged,rapid");
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
