package com.bestgearsetup.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.bestgearsetup.calc.PlayerLevels;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.ItemComposition;
import net.runelite.api.IterableHashTable;
import net.runelite.api.Node;
import org.junit.Test;

public class EquipmentRequirementsTest
{
	private final EquipmentRequirements rules = EquipmentRequirements.get();

	private static GearItem item(int id, String name, Slot slot)
	{
		GearItem item = new GearItem();
		item.setId(id);
		item.setName(name);
		item.setSlot(slot);
		return item;
	}

	private EquipmentRequirements.Resolution resolve(GearItem item, int... values)
	{
		Map<Integer, Integer> params = new HashMap<>();
		for (int i = 0; i < values.length; i += 2)
		{
			params.put(values[i], values[i + 1]);
		}
		return rules.resolve(item, id -> params.getOrDefault(id, 0), params::containsKey);
	}

	@Test
	public void liveCacheOnlyRaisesBundledLevels()
	{
		// Bundled master wand: Magic 60 from its Wiki text, above the cache's 55.
		GearItem wand = item(12422, "master wand", Slot.WEAPON);
		wand.setMagicReq(60);
		assertFalse(resolve(wand, 434, 6, 436, 55).raise(wand));
		assertEquals(60, wand.getMagicReq());
		// A later game update raising a level, or adding one, takes effect.
		assertTrue(resolve(wand, 434, 6, 436, 65, 435, 0, 437, 40).raise(wand));
		assertEquals(65, wand.getMagicReq());
		assertEquals(40, wand.getAttackReq());
		// The pegasian boots' cached Attack pair is still not a wear gate.
		GearItem boots = item(13237, "pegasian boots", Slot.FEET);
		resolve(boots, 434, 0, 436, 75, 435, 4, 437, 75).raise(boots);
		assertEquals(1, boots.getAttackReq());
		assertEquals(75, boots.getRangedReq());
		assertEquals(75, boots.getDefenceReq());
	}

	@Test
	public void absentParamsAndUnknownItemsAreNotUnrestricted()
	{
		GearItem unknown = item(1, "unreviewed weapon", Slot.WEAPON);
		unknown.setAttackReq(75);
		EquipmentRequirements.Resolution result = resolve(unknown);
		assertEquals(EquipmentRequirements.Source.UNRESOLVED, result.getSource());
		assertFalse(result.applyTo(unknown));
		assertEquals(75, unknown.getAttackReq());
	}

	@Test
	public void readsAllSevenPairsIncludingAttackSkillZeroAndUsesTheStricterDuplicate()
	{
		GearItem voidTop = item(8839, "void knight top", Slot.BODY);
		EquipmentRequirements.Resolution result = resolve(voidTop, 434, 0, 436, 42,
			435, 1, 437, 42, 191, 2, 613, 42, 579, 3, 614, 42,
			610, 4, 615, 42, 611, 5, 616, 22, 612, 6, 617, 42);
		assertEquals(EquipmentRequirements.Source.CACHE, result.getSource());
		assertTrue(result.applyTo(voidTop));
		assertEquals(42, voidTop.getAttackReq());
		assertEquals(42, voidTop.getDefenceReq());
		assertEquals(42, voidTop.getStrengthReq());
		assertEquals(42, voidTop.getHitpointsReq());
		assertEquals(42, voidTop.getRangedReq());
		assertEquals(22, voidTop.getPrayerReq());
		assertEquals(42, voidTop.getMagicReq());
		GearItem whip = item(4151, "abyssal whip", Slot.WEAPON);
		assertTrue(resolve(whip, 434, 0, 436, 70, 435, 0, 437, 75).applyTo(whip));
		assertEquals(75, whip.getAttackReq());
	}

	@Test
	public void partialInvalidAndUnmodelledParamsStayReviewOnly()
	{
		GearItem gear = item(1, "unreviewed weapon", Slot.WEAPON);
		assertFalse(resolve(gear, 436, 75).isResolved());
		assertFalse(resolve(gear, 434, 0).isResolved());
		assertFalse(resolve(gear, 434, -1, 436, 75).isResolved());
		assertFalse(resolve(gear, 434, 0, 436, 100).isResolved());
		assertFalse(resolve(gear, 434, 0, 436, 0).isResolved());
		EquipmentRequirements.Resolution noncombat = resolve(gear, 434, 16, 436, 70);
		assertEquals(Integer.valueOf(70), noncombat.getUnmodelledSkills().get(16));
		assertEquals(EquipmentRequirements.Source.REVIEW_REQUIRED, noncombat.getSource());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void runtimeAdapterChecksPresenceBeforeReadingParamDefaults()
	{
		Map<Integer, Integer> params = new HashMap<>();
		params.put(436, 75); // Orphan level; a default skill value of zero must not become Attack 75.
		IterableHashTable<Node> table = mock(IterableHashTable.class);
		when(table.get(anyLong())).thenAnswer(i -> params.containsKey((int) i.<Long>getArgument(0).longValue())
			? mock(Node.class) : null);
		ItemComposition composition = mock(ItemComposition.class);
		when(composition.getParams()).thenReturn(table);
		when(composition.getIntValue(anyInt())).thenAnswer(i -> params.getOrDefault(i.<Integer>getArgument(0), 0));
		EquipmentRequirements.Resolution result = rules.resolve(item(1, "unreviewed weapon", Slot.WEAPON), composition);
		assertEquals(EquipmentRequirements.Source.REVIEW_REQUIRED, result.getSource());
		assertFalse(result.getRequirements().containsKey("attack"));
	}

	@Test
	public void metalFamilyGuardsDoNotCatchRfdGlovesOrUnrelatedBlackAndDragonGear()
	{
		GearItem sword = item(1301, "Adamant longsword", Slot.WEAPON);
		assertTrue(rules.applyReviewed(sword));
		assertEquals(30, sword.getAttackReq());
		GearItem gloves = item(7461, "dragon gloves", Slot.HANDS);
		gloves.setDefenceReq(41);
		assertFalse(rules.applyReviewed(gloves));
		assertEquals(41, gloves.getDefenceReq());
		assertFalse(rules.applyReviewed(item(1, "dragon hunter lance", Slot.WEAPON)));
		assertFalse(rules.applyReviewed(item(1, "black robe", Slot.BODY)));
		assertFalse(rules.applyReviewed(item(11720, "mithril pickaxe (nz)", Slot.WEAPON)));
		assertFalse(rules.applyReviewed(item(1, "rune sword (beta)", Slot.WEAPON)));
		assertFalse(rules.applyReviewed(item(1, "rune sword", Slot.BODY)));
	}

	@Test
	public void metalWarhammersUseStrengthAndThrownaxesKeepTheirException()
	{
		GearItem hammer = item(1347, "rune warhammer", Slot.WEAPON);
		hammer.setAttackReq(40);
		assertTrue(rules.applyReviewed(hammer));
		assertEquals(1, hammer.getAttackReq());
		assertEquals(40, hammer.getStrengthReq());
		GearItem axe = item(20849, "dragon thrownaxe", Slot.WEAPON);
		assertTrue(rules.applyReviewed(axe));
		assertEquals(61, axe.getRangedReq());
	}

	@Test
	public void blessedChapsAndBracersDoNotInheritTheBodysDefenceRequirement()
	{
		GearItem body = item(10386, "saradomin d'hide body", Slot.BODY);
		GearItem chaps = item(10388, "saradomin chaps", Slot.LEGS);
		GearItem bracers = item(10384, "saradomin bracers", Slot.HANDS);
		assertTrue(rules.applyReviewed(body));
		assertTrue(rules.applyReviewed(chaps));
		assertTrue(rules.applyReviewed(bracers));
		assertEquals(40, body.getDefenceReq());
		assertEquals(1, chaps.getDefenceReq());
		assertEquals(1, bracers.getDefenceReq());
		assertEquals(70, body.getRangedReq());
		assertEquals(70, chaps.getRangedReq());
	}

	@Test
	public void questAcquisitionLevelsDoNotBecomeSalveWearRequirements()
	{
		GearItem salve = item(12018, "salve amulet(ei)", Slot.NECK);
		salve.setStrengthReq(34);
		salve.setSlayerReq(40);
		assertTrue(rules.applyReviewed(salve));
		assertEquals(1, salve.getStrengthReq());
		assertEquals(1, salve.getSlayerReq());
	}

	@Test
	public void unknownAndReviewOnlyItemsRetainTheApiBaseline()
	{
		GearItem pegasian = item(13237, "pegasian boots", Slot.FEET);
		pegasian.setDefenceReq(75);
		pegasian.setRangedReq(75);
		EquipmentRequirements.Resolution result = resolve(pegasian, 434, 4, 436, 75, 435, 0, 437, 75);
		assertEquals(EquipmentRequirements.Source.REVIEW_REQUIRED, result.getSource());
		assertFalse(result.applyTo(pegasian));
		assertEquals(1, pegasian.getAttackReq());
		assertEquals(75, pegasian.getDefenceReq());
		assertFalse(rules.applyReviewed(item(3194, "steel halberd", Slot.WEAPON)));
	}

	@Test
	public void cacheCanSupplementARuleButCannotLowerIt()
	{
		GearItem gear = item(1303, "rune longsword", Slot.WEAPON);
		EquipmentRequirements.Resolution result = resolve(gear, 434, 0, 436, 30, 435, 3, 437, 50);
		assertEquals(EquipmentRequirements.Source.RULE_AND_CACHE, result.getSource());
		assertTrue(result.applyTo(gear));
		assertEquals(40, gear.getAttackReq());
		assertEquals(50, gear.getHitpointsReq());
	}

	@Test
	public void blackMaskChecksStrengthAndCombatLevelUsingBaseSkills()
	{
		GearItem mask = item(8921, "black mask", Slot.HEAD);
		assertTrue(rules.applyReviewed(mask));
		assertEquals(20, mask.getStrengthReq());
		assertEquals(40, mask.getCombatReq());
		PlayerLevels lowCombat = new PlayerLevels(1, 20, 10, 1, 1, 1, 10, 1);
		assertFalse(lowCombat.canEquip(mask));
		PlayerLevels lowStrength = new PlayerLevels(70, 19, 10, 70, 70, 70, 70, 1);
		assertFalse(lowStrength.canEquip(mask));
		assertTrue(new PlayerLevels(70, 20, 10, 70, 70, 70, 70, 1).canEquip(mask));
	}
}
