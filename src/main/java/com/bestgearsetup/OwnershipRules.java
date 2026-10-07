package com.bestgearsetup;

import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.WikiGameData;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/** Ownership identities are separate from RuneLite's resale and broad cosmetic families. */
public final class OwnershipRules
{
	/** RuneLite's injected Gson: plugins may not create their own (Plugin Hub disallowed API). */
	private static volatile Gson gson;
	private static volatile Rules rules;

	/** Built once from the bundled catalogue, on first use rather than at class load, so it can use the client's Gson. */
	private static final class Rules
	{
		private final Map<Integer, Integer> states = new HashMap<>();
		private final Set<Integer> equipmentFamilies = new HashSet<>();
		private final Set<Integer> earned = new HashSet<>();

		Rules()
		{
			try
			{
				// The cached catalogue the plugin also loads: no second parse, monster list or deep copy.
				GameData data = catalogue().getEquipment();
				for (Slot slot : Slot.values())
				{
					for (GearItem item : data.getItems(slot))
					{
						equipmentFamilies.add(ItemVariationMapping.map(item.getId()));
						for (int variant : item.getVariants())
						{
							equipmentFamilies.add(ItemVariationMapping.map(variant));
						}
						// These entries contain charge counts or alternative providers of the same earned imbue.
						String name = item.getName();
						if (name.equals("black mask") || name.equals("black mask (i)")
							|| name.endsWith("ring (i)") || name.equals("ring of the gods (i)")
							|| name.equals("ring of suffering (i)") || name.equals("salve amulet(i)")
							|| name.equals("salve amulet(ei)"))
						{
							states.put(item.getId(), item.getId());
							for (int variant : item.getVariants())
							{
								states.put(variant, item.getId());
							}
						}
						if (name.endsWith("ring (i)") || name.equals("ring of the gods (i)")
							|| name.equals("ring of suffering (i)") || name.equals("black mask (i)")
							|| name.equals("slayer helmet (i)") || name.startsWith("salve amulet")
							|| name.equals("slayer helmet") || name.endsWith("cape (i)"))
						{
							earned.add(item.getId());
							earned.addAll(item.getVariants());
						}
					}
				}
			}
			catch (IOException e)
			{
				throw new UncheckedIOException(e);
			}
			group(ItemID.AMULET_OF_GLORY, ItemID.AMULET_OF_GLORY_1, ItemID.AMULET_OF_GLORY_2, ItemID.AMULET_OF_GLORY_3, ItemID.AMULET_OF_GLORY_4, ItemID.AMULET_OF_GLORY_5, ItemID.AMULET_OF_GLORY_6);
			group(ItemID.TRAIL_AMULET_OF_GLORY, ItemID.TRAIL_AMULET_OF_GLORY_1, ItemID.TRAIL_AMULET_OF_GLORY_2, ItemID.TRAIL_AMULET_OF_GLORY_3, ItemID.TRAIL_AMULET_OF_GLORY_4, ItemID.TRAIL_AMULET_OF_GLORY_5, ItemID.TRAIL_AMULET_OF_GLORY_6);
			group(ItemID.RING_OF_WEALTH, ItemID.RING_OF_WEALTH_5, ItemID.RING_OF_WEALTH_4, ItemID.RING_OF_WEALTH_3, ItemID.RING_OF_WEALTH_2, ItemID.RING_OF_WEALTH_1);
			group(ItemID.RING_OF_WEALTH_I, ItemID.RING_OF_WEALTH_I5, ItemID.RING_OF_WEALTH_I4, ItemID.RING_OF_WEALTH_I3, ItemID.RING_OF_WEALTH_I2, ItemID.RING_OF_WEALTH_I1);
			group(ItemID.TOTS_UNCHARGED, ItemID.TOTS_CHARGED); // Trident of the seas, uncharged/charged.
			group(ItemID.TOXIC_TOTS_UNCHARGED, ItemID.TOXIC_TOTS_CHARGED); // Trident of the swamp, uncharged/charged.
			group(ItemID.TOTS_I_UNCHARGED, ItemID.TOTS_I_CHARGED);
			group(ItemID.TOXIC_TOTS_I_UNCHARGED, ItemID.TOXIC_TOTS_I_CHARGED);
			group(ItemID.TOXIC_BLOWPIPE, ItemID.TOXIC_BLOWPIPE_LOADED); // Blowpipe, empty/loaded; ornaments retain their own identities.
			group(ItemID.TOXIC_SOTD, ItemID.TOXIC_SOTD_CHARGED);
			group(ItemID.SERPENTINE_HELM, ItemID.SERPENTINE_HELM_CHARGED);
			group(ItemID.SERPENTINE_HELM_CYAN, ItemID.SERPENTINE_HELM_CHARGED_CYAN);
			group(ItemID.SERPENTINE_HELM_RED, ItemID.SERPENTINE_HELM_CHARGED_RED);
			group(ItemID.SANGUINESTI_STAFF_UNCHARGED, ItemID.SANGUINESTI_STAFF);
			group(ItemID.SANGUINESTI_STAFF_UNCHARGED_OR, ItemID.SANGUINESTI_STAFF_OR);
			group(ItemID.SCYTHE_OF_VITUR_UNCHARGED, ItemID.SCYTHE_OF_VITUR);
			group(ItemID.SCYTHE_OF_VITUR_UNCHARGED_OR, ItemID.SCYTHE_OF_VITUR_OR);
			group(ItemID.SCYTHE_OF_VITUR_UNCHARGED_BL, ItemID.SCYTHE_OF_VITUR_BL);
			group(ItemID.TUMEKENS_SHADOW_UNCHARGED, ItemID.TUMEKENS_SHADOW);
			group(ItemID.TOXIC_BLOWPIPE_ORNAMENT, ItemID.TOXIC_BLOWPIPE_LOADED_ORNAMENT);
			group(ItemID.WILD_CAVE_CHAINMACE_UNCHARGED, ItemID.WILD_CAVE_CHAINMACE_CHARGED);
			group(ItemID.WILD_CAVE_BOW_UNCHARGED, ItemID.WILD_CAVE_BOW_CHARGED);
			group(ItemID.WILD_CAVE_SCEPTRE_UNCHARGED, ItemID.WILD_CAVE_SCEPTRE_CHARGED);
			group(ItemID.WILD_CAVE_WEBWEAVER_UNCHARGED, ItemID.WILD_CAVE_WEBWEAVER_CHARGED);
			group(ItemID.WILD_CAVE_URSINE_UNCHARGED, ItemID.WILD_CAVE_URSINE_CHARGED);
			group(ItemID.WILD_CAVE_ACCURSED_UNCHARGED, ItemID.WILD_CAVE_ACCURSED_CHARGED);
			group(ItemID.WILD_CAVE_ACCURSED_UNCHARGED_RECOL, ItemID.WILD_CAVE_ACCURSED_CHARGED_RECOL);
			group(ItemID.WILD_CAVE_SCEPTRE_UNCHARGED_RECOL, ItemID.WILD_CAVE_SCEPTRE_CHARGED_RECOL);
			group(ItemID.TOME_OF_FIRE_UNCHARGED, ItemID.TOME_OF_FIRE);
			group(ItemID.TOME_OF_WATER_UNCHARGED, ItemID.TOME_OF_WATER);
			group(ItemID.TOME_OF_EARTH_UNCHARGED, ItemID.TOME_OF_EARTH);
		}

		private void group(int base, int... variants)
		{
			states.put(base, base);
			for (int variant : variants)
			{
				states.put(variant, base);
			}
		}
	}

	private OwnershipRules()
	{
	}

	/** Give the rules the client's Gson; the plugin calls this before anything can query them. */
	public static void init(Gson clientGson)
	{
		gson = clientGson;
	}

	/** Build the rules on a background thread before UI or client-thread code first queries them. */
	public static void load()
	{
		rules();
	}

	private static Rules rules()
	{
		Rules loaded = rules;
		if (loaded == null)
		{
			synchronized (OwnershipRules.class)
			{
				loaded = rules;
				if (loaded == null)
				{
					loaded = new Rules();
					rules = loaded;
				}
			}
		}
		return loaded;
	}

	/** The cached catalogue the plugin also loads: no second parse, monster list or deep copy. */
	private static WikiGameData catalogue() throws IOException
	{
		WikiGameData loaded = WikiGameData.loaded();
		if (loaded != null)
		{
			return loaded;
		}
		Gson client = gson;
		if (client == null)
		{
			throw new IllegalStateException("OwnershipRules.init must be given the client's Gson first");
		}
		return WikiGameData.get(client);
	}

	/** Only reviewed charge states and identical earned tiers share ownership; unknown gear stays exact. */
	public static boolean equivalent(int first, int second)
	{
		return first == second || identity(first) == identity(second);
	}

	/** Acquisition identity for gear; non-equipment such as potion doses keeps its RuneLite family. */
	public static int identity(int id)
	{
		if (DiaryRewards.isReward(id))
		{
			return id;
		}
		Integer state = rules().states.get(id);
		if (state != null)
		{
			return state;
		}
		int base = ItemVariationMapping.map(id);
		return rules().equipmentFamilies.contains(base) ? id : base;
	}

	/** Earned imbues cannot be acquired just by buying the tradable base in a budget search. */
	public static boolean requiresOwnership(int id)
	{
		return DiaryRewards.isReward(id) || rules().earned.contains(id);
	}
}
