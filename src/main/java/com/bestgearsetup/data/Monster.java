package com.bestgearsetup.data;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
	/** Null when the snapshot doesn't supply the NPC's melee / ranged accuracy bonuses. */
	@SerializedName("agg_melee")
	private Integer meleeAttackBonus;
	@SerializedName("agg_ranged")
	private Integer rangedAttackBonus;

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

	/**
	 * Lower-case attribute names mapped to true, then earlier answers by the queried string: damage rules ask about the
	 * same literals for every setup a search scores. Built on first use; volatile so other threads see the whole map.
	 */
	private transient volatile Map<String, Boolean> attributeAnswers;
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
		m.meleeAttackBonus = meleeAttackBonus;
		m.rangedAttackBonus = rangedAttackBonus;
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
		m.attributeAnswers = attributeAnswers;
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
		Map<String, Boolean> answers = attributeAnswers;
		if (answers == null)
		{
			answers = new ConcurrentHashMap<>();
			for (Attribute a : attributes == null ? Collections.<Attribute>emptyList() : attributes)
			{
				if (a != null && a.getName() != null)
				{
					answers.put(a.getName().toLowerCase(Locale.ROOT), Boolean.TRUE);
				}
			}
			attributeAnswers = answers;
		}
		Boolean known = answers.get(attribute);
		if (known == null)
		{
			// Every name is in the map, so a lower-case query that is missing is not an attribute.
			known = Boolean.TRUE.equals(answers.get(attribute.toLowerCase(Locale.ROOT)));
			answers.put(attribute, known);
		}
		return known;
	}

	/** Invalidate the derived tag cache when reference data is replaced. */
	public void setAttributes(List<Attribute> attributes)
	{
		this.attributes = attributes;
		attributeAnswers = null;
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
