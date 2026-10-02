package com.bestgearsetup.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.regex.Pattern;
import lombok.Getter;
import net.runelite.api.ItemComposition;
import net.runelite.api.IterableHashTable;
import net.runelite.api.Node;

/**
 * Cache wear parameters plus a curated, source-linked table for legacy equipment. Missing parameters
 * do not establish that an item has no requirements. Neither input models quest or diary access.
 */
public final class EquipmentRequirements
{
	private static final String RESOURCE = "/com/bestgearsetup/equipment-requirements.json";
	private static final int[][] PAIRS = {{434, 436}, {435, 437}, {191, 613}, {579, 614},
		{610, 615}, {611, 616}, {612, 617}};
	private static final Map<Integer, String> SKILLS;
	private static volatile EquipmentRequirements instance;

	static
	{
		Map<Integer, String> skills = new LinkedHashMap<>();
		skills.put(0, "attack");
		skills.put(1, "defence");
		skills.put(2, "strength");
		skills.put(3, "hitpoints");
		skills.put(4, "ranged");
		skills.put(5, "prayer");
		skills.put(6, "magic");
		skills.put(18, "slayer");
		SKILLS = Collections.unmodifiableMap(skills);
	}

	private final List<Rule> rules;

	private EquipmentRequirements(List<Rule> rules)
	{
		this.rules = Collections.unmodifiableList(rules);
	}

	/** Load the bundled rules, without any network or client access. */
	public static EquipmentRequirements get()
	{
		EquipmentRequirements loaded = instance;
		if (loaded == null)
		{
			synchronized (EquipmentRequirements.class)
			{
				loaded = instance;
				if (loaded == null)
				{
					loaded = load();
					instance = loaded;
				}
			}
		}
		return loaded;
	}

	private static EquipmentRequirements load()
	{
		InputStream in = EquipmentRequirements.class.getResourceAsStream(RESOURCE);
		if (in == null)
		{
			throw new IllegalStateException("Bundled equipment requirements are missing");
		}
		List<Rule> rules = new ArrayList<>();
		try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
			if (root.get("schema").getAsInt() != 1)
			{
				throw new IllegalStateException("Unsupported equipment requirements schema");
			}
			for (JsonElement element : root.getAsJsonArray("rules"))
			{
				JsonObject row = element.getAsJsonObject();
				Rule rule = new Rule();
				rule.label = row.get("label").getAsString();
				rule.source = row.get("source").getAsString();
				rule.pattern = Pattern.compile(row.get("pattern").getAsString());
				rule.combat = row.get("combat").getAsInt();
				rule.reviewRequired = row.get("reviewRequired").getAsBoolean();
				for (JsonElement slot : row.getAsJsonArray("slots"))
				{
					Slot value = Slot.fromApiKey(slot.getAsString());
					if (value == null)
					{
						throw new IllegalStateException("Unknown requirement slot");
					}
					rule.slots.add(value);
				}
				for (JsonElement id : row.getAsJsonArray("ids"))
				{
					rule.ids.add(id.getAsInt());
				}
				for (Map.Entry<String, JsonElement> e : row.getAsJsonObject("requirements").entrySet())
				{
					int level = e.getValue().getAsInt();
					if (!SKILLS.containsValue(e.getKey()) || level < 1 || level > 99)
					{
						throw new IllegalStateException("Invalid requirement for " + rule.label);
					}
					rule.requirements.put(e.getKey(), level);
				}
				rules.add(rule);
			}
		}
		catch (IOException | RuntimeException e)
		{
			throw new IllegalStateException("Bundled equipment requirements are unreadable", e);
		}
		return new EquipmentRequirements(rules);
	}

	/**
	 * Read an item's actual parameter table on the client thread. Testing presence is necessary:
	 * getIntValue returns a param's default for an absent key, which can look like an Attack skill ID.
	 */
	public Resolution resolve(GearItem item, ItemComposition composition)
	{
		IterableHashTable<Node> params = composition.getParams();
		return resolve(item, composition::getIntValue, id -> params != null && params.get(id) != null);
	}

	/** Resolve against a parameter reader; no client objects escape into the result. */
	public Resolution resolve(GearItem item, IntUnaryOperator value, IntPredicate present)
	{
		Map<String, Integer> requirements = new HashMap<>();
		Map<Integer, Integer> unmodelled = new HashMap<>();
		boolean invalid = false;
		for (int[] pair : PAIRS)
		{
			boolean skillPresent = present.test(pair[0]);
			boolean levelPresent = present.test(pair[1]);
			if (!skillPresent && !levelPresent)
			{
				continue;
			}
			if (!skillPresent || !levelPresent)
			{
				invalid = true;
				continue;
			}
			int skill = value.applyAsInt(pair[0]);
			int level = value.applyAsInt(pair[1]);
			if (skill < 0 || level < 1 || level > 99)
			{
				invalid = true;
				continue;
			}
			String key = SKILLS.get(skill);
			if (key == null)
			{
				unmodelled.merge(skill, level, Math::max);
			}
			else
			{
				requirements.merge(key, level, Math::max);
			}
		}
		boolean cache = !requirements.isEmpty();
		Rule rule = match(item);
		if (rule != null)
		{
			for (Map.Entry<String, Integer> e : rule.requirements.entrySet())
			{
				requirements.merge(e.getKey(), e.getValue(), Math::max);
			}
		}
		// Do not silently bless the offline pegasian parameter anomaly before an equip check.
		boolean review = rule != null && rule.reviewRequired;
		if (item.getId() == 13237)
		{
			requirements.remove("attack");
		}
		Source source = invalid || !unmodelled.isEmpty() || review ? Source.REVIEW_REQUIRED
			: rule != null ? cache ? Source.RULE_AND_CACHE : Source.RULE
			: cache ? Source.CACHE : Source.UNRESOLVED;
		return new Resolution(requirements, unmodelled, source, rule == null ? "" : rule.label,
			rule == null ? "" : rule.source, rule == null ? 0 : rule.combat);
	}

	private Rule match(GearItem item)
	{
		Rule matched = null;
		String name = item.getName().toLowerCase(Locale.ROOT).trim();
		for (Rule rule : rules)
		{
			if (rule.slots.contains(item.getSlot()) && (rule.ids.isEmpty()
				? rule.pattern.matcher(name).matches() : rule.ids.contains(item.getId())))
			{
				if (matched != null)
				{
					throw new IllegalStateException("Overlapping wear rules for " + item.getName());
				}
				matched = rule;
			}
		}
		return matched;
	}

	/**
	 * Replace API skill fields only for reviewed families. All other equipment keeps its current
	 * baseline until the local equipment loader is ready. The pegasian exception stays review-only.
	 */
	public boolean applyReviewed(GearItem item)
	{
		Resolution resolution = resolve(item, id -> 0, id -> false);
		return resolution.applyTo(item);
	}

	/** Input provenance, not a certificate of quest access or server-side requirement completeness. */
	public enum Source
	{
		RULE, CACHE, RULE_AND_CACHE, REVIEW_REQUIRED, UNRESOLVED
	}

	@Getter
	public static final class Resolution
	{
		private final Map<String, Integer> requirements;
		private final Map<Integer, Integer> unmodelledSkills;
		private final Source source;
		private final String rule;
		private final String citation;
		private final int combat;

		private Resolution(Map<String, Integer> requirements, Map<Integer, Integer> unmodelledSkills,
			Source source, String rule, String citation, int combat)
		{
			this.requirements = Collections.unmodifiableMap(new HashMap<>(requirements));
			this.unmodelledSkills = Collections.unmodifiableMap(new HashMap<>(unmodelledSkills));
			this.source = source;
			this.rule = rule;
			this.citation = citation;
			this.combat = combat;
		}

		/** Unresolved or review-only items must not become unrestricted in a local catalogue. */
		public boolean isResolved()
		{
			return source != Source.UNRESOLVED && source != Source.REVIEW_REQUIRED;
		}

		/**
		 * Raise the item's levels to any stricter level this resolution states, never lowering one. Used for
		 * the live cache over the bundled snapshot, whose levels already include the reviewed rules.
		 */
		public boolean raise(GearItem item)
		{
			int[] before = levels(item);
			item.setAttackReq(Math.max(item.getAttackReq(), requirements.getOrDefault("attack", 1)));
			item.setDefenceReq(Math.max(item.getDefenceReq(), requirements.getOrDefault("defence", 1)));
			item.setStrengthReq(Math.max(item.getStrengthReq(), requirements.getOrDefault("strength", 1)));
			item.setHitpointsReq(Math.max(item.getHitpointsReq(), requirements.getOrDefault("hitpoints", 1)));
			item.setRangedReq(Math.max(item.getRangedReq(), requirements.getOrDefault("ranged", 1)));
			item.setPrayerReq(Math.max(item.getPrayerReq(), requirements.getOrDefault("prayer", 1)));
			item.setMagicReq(Math.max(item.getMagicReq(), requirements.getOrDefault("magic", 1)));
			item.setSlayerReq(Math.max(item.getSlayerReq(), requirements.getOrDefault("slayer", 1)));
			item.setCombatReq(Math.max(item.getCombatReq(), combat));
			return !java.util.Arrays.equals(before, levels(item));
		}

		private static int[] levels(GearItem item)
		{
			return new int[]{item.getAttackReq(), item.getDefenceReq(), item.getStrengthReq(), item.getHitpointsReq(),
				item.getRangedReq(), item.getPrayerReq(), item.getMagicReq(), item.getSlayerReq(), item.getCombatReq()};
		}

		/** Apply a resolved combat-skill vector, returning false without mutation for a gap. */
		public boolean applyTo(GearItem item)
		{
			if (!isResolved())
			{
				return false;
			}
			item.setAttackReq(requirements.getOrDefault("attack", 1));
			item.setDefenceReq(requirements.getOrDefault("defence", 1));
			item.setStrengthReq(requirements.getOrDefault("strength", 1));
			item.setHitpointsReq(requirements.getOrDefault("hitpoints", 1));
			item.setRangedReq(requirements.getOrDefault("ranged", 1));
			item.setPrayerReq(requirements.getOrDefault("prayer", 1));
			item.setMagicReq(requirements.getOrDefault("magic", 1));
			item.setSlayerReq(requirements.getOrDefault("slayer", 1));
			item.setCombatReq(combat);
			return true;
		}
	}

	private static final class Rule
	{
		private String label;
		private String source;
		private Pattern pattern;
		private int combat;
		private boolean reviewRequired;
		private final List<Slot> slots = new ArrayList<>();
		private final List<Integer> ids = new ArrayList<>();
		private final Map<String, Integer> requirements = new HashMap<>();
	}
}
