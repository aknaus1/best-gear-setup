package com.bestgearsetup.data;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Data;

/**
 * Full monster stats from the bundled Wiki snapshot.
 */
@Data
public class Monster
{
	private int id;
	private String name;
	@SerializedName("level_cb")
	private int combatLevel;
	@SerializedName("level_hp")
	private int hitpoints;
	@SerializedName("level_attack")
	private int attackLevel;
	@SerializedName("level_strength")
	private int strengthLevel;
	@SerializedName("level_defence")
	private int defenceLevel;
	/** Specials cannot drain Defence below this level. */
	@SerializedName("defence_floor")
	private int defenceFloor;
	@SerializedName("immune_thrall")
	private boolean immuneThrall;
	@SerializedName("immune_poison")
	private boolean immunePoison;
	@SerializedName("immune_venom")
	private boolean immuneVenom;
	@SerializedName("level_magic")
	private int magicLevel;
	@SerializedName("level_ranged")
	private int rangedLevel;
	@SerializedName("agg_magic")
	private int magicAttackBonus;

	@SerializedName("def_stab")
	private int defStab;
	@SerializedName("def_slash")
	private int defSlash;
	@SerializedName("def_crush")
	private int defCrush;
	@SerializedName("def_magic")
	private int defMagic;
	/** Standard ranged defence (arrows). */
	@SerializedName("def_ranged")
	private int defRanged;
	/** Light ranged defence (darts, knives, thrown). Null when the API has no split value. */
	@SerializedName("def_light")
	private Integer defLight;
	/** Heavy ranged defence (bolts). Null when the API has no split value. */
	@SerializedName("def_heavy")
	private Integer defHeavy;
	@SerializedName("weakness_type")
	private String weaknessType;
	private int weakness;
	@SerializedName(value = "flat", alternate = {"flat_armour"})
	private Integer flatArmour;
	@SerializedName("demonbane_vulnerability")
	private Integer demonbaneVulnerability;

	private int size = 1;
	private boolean task;
	private List<Attribute> attributes;
	/** The monster's own attacks, keyed by name ("slash", "magic", "dragonfire"...). */
	private Map<String, MonsterAttack> attackStyles;

	/** Built on first use; volatile so searches on several threads see the whole set. */
	private transient volatile Set<String> attributeNames;
	/** Derived from the name; cleared by {@link #setName}. */
	private transient String lowerName;
	/** Selected temporary boss state; set by the search, never by the API. */
	private transient EncounterPhase phase = EncounterPhase.STANDARD;
	/** Tombs of Amascut raid level applied to this target's defence roll; zero when unscaled. */
	private transient int toaRaidLevel;

	@Data
	public static class Attribute
	{
		private String name;
	}

	@Data
	public static class MonsterAttack
	{
		@SerializedName("max_hit")
		private int maxHit;
		private double weighting;
		private boolean typeless;
	}

	/** Shallow copy, used to apply stat drains without touching the cached monster. */
	public Monster copy()
	{
		Monster m = new Monster();
		m.id = id;
		m.name = name;
		m.combatLevel = combatLevel;
		m.hitpoints = hitpoints;
		m.attackLevel = attackLevel;
		m.strengthLevel = strengthLevel;
		m.defenceLevel = defenceLevel;
		m.defenceFloor = defenceFloor;
		m.immuneThrall = immuneThrall;
		m.immunePoison = immunePoison;
		m.immuneVenom = immuneVenom;
		m.magicLevel = magicLevel;
		m.rangedLevel = rangedLevel;
		m.magicAttackBonus = magicAttackBonus;
		m.defStab = defStab;
		m.defSlash = defSlash;
		m.defCrush = defCrush;
		m.defMagic = defMagic;
		m.defRanged = defRanged;
		m.defLight = defLight;
		m.defHeavy = defHeavy;
		m.weaknessType = weaknessType;
		m.weakness = weakness;
		m.flatArmour = flatArmour;
		m.demonbaneVulnerability = demonbaneVulnerability;
		m.size = size;
		m.task = task;
		m.attributes = attributes;
		m.attackStyles = attackStyles;
		m.attributeNames = attributeNames;
		m.lowerName = lowerName;
		m.phase = phase;
		m.toaRaidLevel = toaRaidLevel;
		return m;
	}

	public String getName()
	{
		return name == null ? "" : name;
	}

	public void setName(String name)
	{
		this.name = name;
		lowerName = null;
	}

	/** {@link #getName()} in lower case; encounter rules match it for every setup the search scores. */
	public String getLowerName()
	{
		String lower = lowerName;
		if (lower == null)
		{
			lower = getName().toLowerCase(Locale.ROOT);
			lowerName = lower;
		}
		return lower;
	}

	public EncounterPhase getPhase()
	{
		return phase == null ? EncounterPhase.STANDARD : phase;
	}

	public String getDisplayName()
	{
		return GameData.titleCase(getName());
	}

	public boolean hasAttribute(String attribute)
	{
		if (attributeNames == null)
		{
			Set<String> names = new HashSet<>();
			for (Attribute a : attributes == null ? Collections.<Attribute>emptyList() : attributes)
			{
				if (a != null && a.getName() != null)
				{
					names.add(a.getName().toLowerCase(Locale.ROOT));
				}
			}
			attributeNames = names;
		}
		return attributeNames.contains(attribute.toLowerCase(Locale.ROOT));
	}

	/** Invalidate the derived tag cache when reference data is replaced. */
	public void setAttributes(List<Attribute> attributes)
	{
		this.attributes = attributes;
		attributeNames = null;
	}

	public int getDefLightOrStandard()
	{
		return defLight == null ? defRanged : defLight;
	}

	public int getDefHeavyOrStandard()
	{
		return defHeavy == null ? defRanged : defHeavy;
	}
}
