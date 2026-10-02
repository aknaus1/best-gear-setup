package com.bestgearsetup.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;

/** The bundled Wiki equipment, spell, prayer and potion snapshot, against facts from the Wiki pages. */
public class WikiGameDataTest
{
	private static GameData data;

	@BeforeClass
	public static void load() throws Exception
	{
		data = WikiGameData.get(new Gson()).gameData(new Gson());
	}

	private static GearItem item(Slot slot, String name)
	{
		for (GearItem item : data.getItems(slot))
		{
			if (item.getName().equals(name))
			{
				return item;
			}
		}
		throw new AssertionError("Missing " + name);
	}

	private static Spell spell(String name)
	{
		return data.getSpells().stream().filter(s -> s.getName().equals(name)).findFirst()
			.orElseThrow(() -> new AssertionError("Missing " + name));
	}

	private static boolean fires(String weapon, String ammo)
	{
		return item(Slot.WEAPON, weapon).getAmmunition().contains(item(Slot.AMMO, ammo).getId());
	}

	@Test
	public void catalogueCoversTheGameWithoutLeaguesBetaOrLastManStanding()
	{
		assertTrue(data.getItems(Slot.WEAPON).size() > 600);
		assertTrue(data.getItems(Slot.HEAD).size() > 200);
		assertTrue(data.getItems(Slot.AMMO).size() > 100);
		for (Slot slot : Slot.values())
		{
			for (GearItem item : data.getItems(slot))
			{
				assertFalse(item.getName(), item.isLeagueEquipment() || item.isBetaEquipment());
				assertTrue(item.getName(), item.getId() > 0);
			}
		}
		List<String> weapons = data.getItems(Slot.WEAPON).stream().map(GearItem::getName).collect(Collectors.toList());
		assertFalse(weapons.contains("the dogsword"));
		assertFalse(weapons.contains("nature's recurve"));
		assertTrue(weapons.contains("keris partisan of amascut (inside toa)"));
		assertTrue(weapons.contains("keris partisan of amascut (outside toa)"));
		assertTrue(weapons.contains("crystal axe (gauntlet)"));
	}

	@Test
	public void weaponsCarryWikiStatsCategoriesAndStyles()
	{
		GearItem whip = item(Slot.WEAPON, "abyssal whip");
		assertEquals(4151, whip.getId());
		assertEquals(82, whip.getSlashBonus());
		assertEquals(82, whip.getMeleeStr());
		assertEquals(4, whip.getAttackSpeed());
		assertEquals("whip", whip.getSubcategory());
		assertEquals(3, whip.getStyles().size());
		GearItem bow = item(Slot.WEAPON, "twisted bow");
		assertTrue(bow.isTwoHanded());
		assertEquals("bow", bow.getSubcategory());
		GearItem shadow = item(Slot.WEAPON, "tumeken's shadow");
		assertEquals("powered staff", shadow.getSubcategory());
		assertEquals(5.0, item(Slot.NECK, "occult necklace").getMagicStr(), 1e-9);
		assertTrue(item(Slot.WEAPON, "kodai wand").getStyles().contains("spell,magic,magic"));
	}

	@Test
	public void ammunitionFollowsTheWikiTierTables()
	{
		assertTrue(fires("eclipse atlatl", "atlatl dart"));
		assertTrue(fires("silvthrill ballista", "silvthrill javelin"));
		assertTrue(fires("dragon hunter crossbow (b)", "dragon bolts"));
		assertTrue(fires("signed oak bow", "steel arrow"));
		assertFalse(fires("signed oak bow", "mithril arrow"));
		assertTrue(fires("twisted bow", "dragon arrow"));
		assertTrue(fires("magic shortbow (i)", "amethyst arrow"));
		assertFalse(fires("magic shortbow (i)", "dragon arrow"));
		assertFalse(fires("maple shortbow", "rune arrow"));
		assertTrue(fires("rune crossbow", "runite bolts"));
		assertTrue(fires("rune crossbow", "diamond bolts (e)"));
		assertFalse(fires("rune crossbow", "dragon bolts"));
		assertTrue(fires("zaryte crossbow", "ruby dragon bolts (e)"));
		assertTrue(fires("toxic blowpipe", "dragon dart"));
		assertFalse(fires("rosewood blowpipe", "dragon dart"));
		assertTrue(fires("heavy ballista", "dragon javelin"));
		assertTrue(item(Slot.WEAPON, "bow of faerdhinen").getAmmunition().isEmpty());
	}

	@Test
	public void wearRequirementsCombineCacheTextAndReviewedRules()
	{
		assertEquals(85, item(Slot.WEAPON, "twisted bow").getRangedReq());
		GearItem scythe = item(Slot.WEAPON, "scythe of vitur");
		assertEquals(80, scythe.getAttackReq());
		assertEquals(90, scythe.getStrengthReq());
		// Ornament kits and charge states fold into the base item, so owning either id counts.
		assertTrue(scythe.getVariants().contains(25736));
		assertTrue(item(Slot.WEAPON, "tumeken's shadow").getVariants().contains(27277));
		assertTrue(item(Slot.HEAD, "slayer helmet (i)").getVariants().contains(19649));
		// Same stats, different effects: these stay separate.
		assertEquals(75, item(Slot.WEAPON, "blood ancient sceptre").getMagicReq());
		assertTrue(item(Slot.WEAPON, "eldritch nightmare staff").getVariants().size() <= 2);
		GearItem mask = item(Slot.HEAD, "black mask (i)");
		assertEquals(10, mask.getDefenceReq());
		assertEquals(20, mask.getStrengthReq());
		assertEquals(40, mask.getCombatReq());
		GearItem salve = item(Slot.NECK, "salve amulet(ei)");
		assertEquals(1, salve.getStrengthReq());
		assertEquals(1, salve.getMagicReq());
		GearItem pegasian = item(Slot.FEET, "pegasian boots");
		assertEquals(75, pegasian.getRangedReq());
		assertEquals(75, pegasian.getDefenceReq());
		assertEquals(1, pegasian.getAttackReq());
		assertEquals(99, item(Slot.CAPE, "max cape").getSlayerReq());
		// Stated in the item's Wiki text only (no cache parameters).
		assertEquals(40, item(Slot.HANDS, "ranger gloves").getRangedReq());
		assertEquals(1, item(Slot.HANDS, "ranger gloves").getDefenceReq());
		// Diary and quest skill levels are not wear requirements.
		assertEquals(1, item(Slot.BODY, "varrock armour 4").getMagicReq());
	}

	@Test
	public void spellsAndAutocastFollowTheWikiAutocastPage()
	{
		Spell surge = spell("fire surge");
		assertEquals(24, surge.getMaxHit());
		assertEquals(95, surge.getLevel());
		assertEquals("standard", surge.getSpellbook());
		assertEquals("ancient", spell("ice barrage").getSpellbook());
		assertEquals(30, spell("ice barrage").getMaxHit());
		int kodai = item(Slot.WEAPON, "kodai wand").getId();
		assertTrue(surge.castableWith(kodai));
		assertTrue(spell("ice barrage").castableWith(kodai));
		assertTrue(spell("ghostly grasp").castableWith(kodai));
		int slayerStaff = item(Slot.WEAPON, "slayer's staff").getId();
		assertTrue(spell("fire wave").castableWith(slayerStaff));
		assertTrue(spell("magic dart").castableWith(slayerStaff));
		assertFalse(spell("fire bolt").castableWith(slayerStaff));
		int skull = item(Slot.WEAPON, "skull sceptre (i)").getId();
		assertTrue(spell("crumble undead").castableWith(skull));
		assertFalse(spell("fire strike").castableWith(skull));
		assertTrue(spell("iban blast").castableWith(item(Slot.WEAPON, "iban's staff").getId()));
		int harmonised = item(Slot.WEAPON, "harmonised nightmare staff").getId();
		assertTrue(surge.castableWith(harmonised));
		assertFalse(spell("ice barrage").castableWith(harmonised));
		assertFalse(surge.castableWith(item(Slot.WEAPON, "tumeken's shadow").getId()));
	}

	@Test
	public void prayersAndPotionsFollowTheirWikiPages()
	{
		OffensivePrayer piety = data.getPrayers(CombatClass.MELEE).stream().filter(p -> p.getName().equals("piety"))
			.findFirst().orElse(null);
		assertNotNull(piety);
		assertEquals(20, piety.getAccuracyPercent(), 1e-9);
		assertEquals(23, piety.getDamagePercent(), 1e-9);
		OffensivePrayer augury = data.getPrayers(CombatClass.MAGIC).stream().filter(p -> p.getName().equals("augury"))
			.findFirst().orElse(null);
		assertNotNull(augury);
		assertEquals(25, augury.getAccuracyPercent(), 1e-9);
		assertEquals(4, augury.getDamagePercent(), 1e-9);
		assertEquals(70, augury.getDefenceLevel());
		Potion heart = data.getPotions("magic").stream().filter(p -> p.getName().equals("saturated heart"))
			.findFirst().orElse(null);
		assertNotNull(heart);
		assertEquals(4 + 9, heart.boost(99) - 99);
		Potion overload = data.getPotions("strength").stream().filter(p -> p.getName().equals("overload (+)"))
			.findFirst().orElse(null);
		assertNotNull(overload);
		assertFalse(overload.isUnrestricted());
		assertNull(data.getPotions("strength").stream().filter(p -> p.getName().equals("dragon battleaxe"))
			.findFirst().orElse(null));
	}
}
