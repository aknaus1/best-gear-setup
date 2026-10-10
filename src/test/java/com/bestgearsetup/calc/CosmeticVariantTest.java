package com.bestgearsetup.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import com.bestgearsetup.OwnershipRules;
import com.bestgearsetup.data.CombatClass;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.bestgearsetup.data.WikiMonsters;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A cosmetic shown as its own item fights like the item it copies, although the Wiki's spell and ammunition
 * lists only name the original's id.
 */
public class CosmeticVariantTest
{
	private static GameData data;
	private static WikiMonsters monsters;

	@BeforeClass
	public static void load() throws Exception
	{
		Gson gson = RuneLiteAPI.GSON;
		OwnershipRules.init(gson);
		data = WikiGameData.get(gson).gameData(gson);
		// The live client names folded variants from the cache: the Armageddon reward staff, poisoned arrows and a
		// Leagues Reward Shop ornament.
		data.addVariantItems(gson, id -> id == ItemID.DEADMAN_NIGHTMARE_STAFF_VOLATILE ? "Volatile nightmare staff (deadman)"
			: id == ItemID.DRAGON_ARROW_P ? "Dragon arrow(p)" : id == ItemID.SOULREAPER_AXE_ORN ? "Soulreaper axe (o)" : null,
			id -> false);
		monsters = WikiMonsters.get(gson);
	}

	private static SetupResult best(String monster, CombatClass cls, SearchMode mode, Integer... held)
	{
		Monster target = monsters.monster(monster);
		CombatContext context = new CombatContext(target, PlayerLevels.maxed(), false, false, null);
		OptimizerSettings settings = OptimizerSettings.builder().mode(mode).killSpecials(true)
			.spellbooks(new HashSet<>(Arrays.asList("standard", "ancient"))).build();
		Set<Integer> owned = new HashSet<>(Arrays.asList(held));
		List<SetupResult> results = new Optimizer(data, context, settings, owned::contains, item -> 0L)
			.optimize(cls, () -> false);
		assertFalse("no " + cls + " setup holding " + owned, results.isEmpty());
		return results.get(0);
	}

	@Test
	public void deadmanVolatileStaffCastsSpellsAndSpecsLikeTheOriginal()
	{
		SetupResult original = best("general graardor", CombatClass.MAGIC, SearchMode.OWNED_ONLY,
			ItemID.NIGHTMARE_STAFF_VOLATILE);
		SetupResult deadman = best("general graardor", CombatClass.MAGIC, SearchMode.OWNED_ONLY,
			ItemID.DEADMAN_NIGHTMARE_STAFF_VOLATILE);
		assertEquals(ItemID.DEADMAN_NIGHTMARE_STAFF_VOLATILE, deadman.getLoadout().get(Slot.WEAPON).getId());
		assertEquals(original.getLoadout().getSpell(), deadman.getLoadout().getSpell());
		assertNotNull(deadman.getSpecial());
		assertEquals("Volatile nightmare staff", deadman.getSpecial().getSpecial().getName());
		assertEquals(original.getDps().getDps(), deadman.getDps().getDps(), 1e-9);
	}

	@Test
	public void twistedBowFiresPoisonedDragonArrows()
	{
		String nylocas = "nylocas vasilias (ranged) (normal)";
		SetupResult plain = best(nylocas, CombatClass.RANGED, SearchMode.INVENTORY_ONLY, ItemID.TWISTED_BOW,
			ItemID.DRAGON_ARROW);
		SetupResult poisoned = best(nylocas, CombatClass.RANGED, SearchMode.INVENTORY_ONLY, ItemID.TWISTED_BOW,
			ItemID.DRAGON_ARROW_P);
		assertEquals(ItemID.TWISTED_BOW, poisoned.getLoadout().get(Slot.WEAPON).getId());
		assertEquals(ItemID.DRAGON_ARROW_P, poisoned.getLoadout().get(Slot.AMMO).getId());
		assertEquals(plain.getDps().getDps(), poisoned.getDps().getDps(), 1e-9);
	}

	@Test
	public void leagueOrnamentedSoulreaperAxeFightsLikeTheAxe()
	{
		SetupResult plain = best("general graardor", CombatClass.MELEE, SearchMode.OWNED_ONLY, ItemID.SOULREAPER);
		SetupResult ornamented = best("general graardor", CombatClass.MELEE, SearchMode.OWNED_ONLY,
			ItemID.SOULREAPER_AXE_ORN);
		assertEquals(ItemID.SOULREAPER_AXE_ORN, ornamented.getLoadout().get(Slot.WEAPON).getId());
		assertEquals(plain.getDps().getDps(), ornamented.getDps().getDps(), 1e-9);
	}
}
