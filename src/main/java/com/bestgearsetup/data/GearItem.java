package com.bestgearsetup.data;

import com.bestgearsetup.ItemCosts;
import com.bestgearsetup.OwnershipRules;
import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.Data;
import net.runelite.api.gameval.ItemID;

/**
 * A piece of equipment or a weapon from the bundled data. Weapons carry the extra
 * weapon-only fields (speed, styles, ammunition); armour leaves them at their defaults.
 */
@Data
public class GearItem
{
	private int id;
	private String name;
	/** Original snapshot identity for stat-identical cosmetics whose display name has a prefix. */
	private String combatName;
	/** Snapshot id of the item a stat-identical cosmetic copies, so it casts the same spells; null for snapshot items. */
	private Integer combatId;
	/** Fixed acquisition price used by offline tests; the plugin prices items from RuneLite at search time. */
	private long price;
	private boolean tradeable;
	private boolean members;

	/** Item ids folded into this entry (ornament kits, charges, recolours). */
	private List<Integer> variants;

	/** Filled in by {@link GameData} from the slot the item was listed under. */
	private transient Slot slot;
	/** Derived from the names and id; cleared by their setters. Transient keeps them out of Gson and equals. */
	private transient String lowerCombatName;
	private transient Boolean dmmEquipment;

	@SerializedName("stab_bonus")
	private int stabBonus;
	@SerializedName("slash_bonus")
	private int slashBonus;
	@SerializedName("crush_bonus")
	private int crushBonus;
	@SerializedName("ranged_bonus")
	private int rangedBonus;
	@SerializedName("magic_bonus")
	private int magicBonus;
	@SerializedName("melee_str")
	private int meleeStr;
	@SerializedName("ranged_str")
	private int rangedStr;
	/** Magic damage bonus in percent. */
	@SerializedName("magic_str")
	private double magicStr;
	@SerializedName("prayer_bonus")
	private int prayerBonus;

	@SerializedName("stab_def")
	private int stabDef;
	@SerializedName("slash_def")
	private int slashDef;
	@SerializedName("crush_def")
	private int crushDef;
	@SerializedName("ranged_def")
	private int rangedDef;
	@SerializedName("magic_def")
	private int magicDef;

	@SerializedName("attack_req")
	private int attackReq = 1;
	@SerializedName("strength_req")
	private int strengthReq = 1;
	@SerializedName("defence_req")
	private int defenceReq = 1;
	@SerializedName("ranged_req")
	private int rangedReq = 1;
	@SerializedName("magic_req")
	private int magicReq = 1;
	@SerializedName("prayer_req")
	private int prayerReq = 1;
	@SerializedName("hitpoints_req")
	private int hitpointsReq = 1;
	@SerializedName("slayer_req")
	private int slayerReq = 1;
	/** Combined combat level gate (e.g. black masks), separate from individual skill levels. */
	private int combatReq;

	// Weapon-only fields
	@SerializedName("two_handed")
	private boolean twoHanded;
	private String subcategory;
	@SerializedName("attack_speed")
	private int attackSpeed;
	private List<String> styles;
	private List<Integer> ammunition;

	public List<String> getStyles()
	{
		return styles == null ? Collections.emptyList() : styles;
	}

	public List<Integer> getVariants()
	{
		return variants == null ? Collections.emptyList() : variants;
	}

	/** Catalogue folding alone is not evidence that a cosmetic or earned upgrade is owned. */
	public List<Integer> getOwnershipVariants()
	{
		return getVariants().stream().filter(variant -> variant == id || OwnershipRules.equivalent(id, variant)
			&& ItemCosts.equivalent(id, variant))
			.collect(Collectors.toList());
	}

	public List<Integer> getAmmunition()
	{
		return ammunition == null ? Collections.emptyList() : ammunition;
	}

	public String getSubcategory()
	{
		return subcategory == null ? "" : subcategory;
	}

	public String getName()
	{
		return name == null ? "" : name;
	}

	public void setName(String name)
	{
		this.name = name;
		lowerCombatName = null;
		dmmEquipment = null;
	}

	public void setCombatName(String combatName)
	{
		this.combatName = combatName;
		lowerCombatName = null;
	}

	public void setId(int id)
	{
		this.id = id;
		dmmEquipment = null;
	}

	/** {@link #getCombatName()} in lower case; the search matches it for every candidate it scores. */
	public String getLowerCombatName()
	{
		String lower = lowerCombatName;
		if (lower == null)
		{
			lower = getCombatName().toLowerCase(Locale.ROOT);
			lowerCombatName = lower;
		}
		return lower;
	}

	/** Spell lists name snapshot ids, so a folded cosmetic shown separately checks them as its original. */
	public int getCombatId()
	{
		return combatId == null ? id : combatId;
	}

	/** Combat effects keep the original snapshot identity when a folded cosmetic is shown separately. */
	public String getCombatName()
	{
		return combatName == null ? getName() : combatName;
	}

	/** Deadman variants, including corrupted weapons whose API names omit the DMM suffix. */
	public boolean isDmmEquipment()
	{
		Boolean dmm = dmmEquipment;
		if (dmm == null)
		{
			dmm = dmmEquipmentUncached();
			dmmEquipment = dmm;
		}
		return dmm;
	}

	private boolean dmmEquipmentUncached()
	{
		switch (id)
		{
			// Armageddon reward cosmetics: named "(Deadman)" but usable on main-game worlds.
			case ItemID.DEADMAN_AGS:
			case ItemID.DEADMAN_VOIDWAKER:
			case ItemID.DEADMAN_NIGHTMARE_STAFF_VOLATILE:
			case ItemID.DEADMAN_DARKBOW:
			case ItemID.DEADMAN_MA2_ZAMORAK_CAPE:
			case ItemID.DEADMAN_MA2_GUTHIX_CAPE:
			case ItemID.DEADMAN_MA2_SARADOMIN_CAPE:
				return false;
			default:
				break;
		}
		String lower = getName().toLowerCase(Locale.ROOT);
		if (lower.contains("(dmm)") || lower.contains("(deadman)"))
		{
			return true;
		}
		switch (id)
		{
			case ItemID.DEADMAN_BLIGHTED_VOIDWAKER:
			case ItemID.DEADMAN_BLIGHTED_DRAGON_CLAWS:
			case ItemID.DEADMAN_BLIGHTED_AGS:
			case ItemID.DEADMAN_BLIGHTED_TWISTED_BOW:
			case ItemID.DEADMAN_BLIGHTED_SCYTHE_OF_VITUR:
			case ItemID.DEADMAN_BLIGHTED_SCYTHE_OF_VITUR_UNCHARGED:
			case ItemID.DEADMAN_BLIGHTED_TUMEKENS_SHADOW:
			case ItemID.DEADMAN_BLIGHTED_DARK_BOW:
			case ItemID.DEADMAN_BLIGHTED_VOLATILE_STAFF:
				return true;
			default:
				return false;
		}
	}

	/** Bounty Hunter variants, including the untagged blighted Vesta's longsword. */
	public boolean isBountyHunterEquipment()
	{
		String lower = getName().toLowerCase(Locale.ROOT);
		return lower.contains("(bh)") || lower.contains("(bounty hunter)")
			|| lower.equals("vesta's blighted longsword")
			|| id == ItemID.BH_VESTAS_LONGSWORD || id == ItemID.BH_VESTAS_LONGSWORD_INACTIVE;
	}

	/** Historical Leagues rewards; a separate DMM variant keeps its existing access category. */
	public boolean isLeagueEquipment()
	{
		if (isDmmEquipment())
		{
			return false;
		}
		String lower = getName().toLowerCase(Locale.ROOT);
		for (String name : new String[]{"the dogsword", "drygore blowpipe", "amulet of the monarchs", "emperor ring",
			"devil's element", "nature's reprisal", "gloves of the damned", "crystal blessing", "sunlight spear",
			"sunlit bracers", "thunder khopesh", "thousand-dragon ward"})
		{
			if (lower.equals(name) || lower.startsWith(name + " ("))
			{
				return true;
			}
		}
		return false;
	}

	/** Explicitly labelled beta equipment. */
	public boolean isBetaEquipment()
	{
		String lower = getName().toLowerCase(Locale.ROOT);
		return lower.contains("(beta)") || lower.startsWith("beta ");
	}

	@Override
	public String toString()
	{
		return name;
	}
}
