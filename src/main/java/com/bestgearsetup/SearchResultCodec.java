package com.bestgearsetup;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A bounded DTO graph format: preserves shared loadouts, DPS identities and identity-keyed potion maps. */
final class SearchResultCodec
{
	private static final int MAX_NODES = 200_000;
	private static final Set<String> DTO = Set.of("RaidPlan", "RaidPlan$InventoryItem", "RaidEncounter", "RaidEncounter$Downs", "SearchResults", "BoostTiming",
		"calc.SetupResult", "calc.Loadout", "calc.DpsResult", "calc.AttackStyle", "calc.SpecialPlan",
		"calc.SpecialAttack", "calc.SpecialAttack$Drain", "calc.LockStatus", "calc.SlotLock", "calc.PlayerLevels",
		"calc.OptimizerSettings", "calc.ProtectionPrayers$Context", "data.GearItem", "data.Monster",
		"data.Monster$Attribute", "data.Monster$MonsterAttack", "data.MonsterSummary", "data.Spell",
		"data.Potion", "data.OffensivePrayer");
	private final Gson gson;

	SearchResultCodec(Gson gson) { this.gson = gson; }

	JsonObject encode(Object root) throws ReflectiveOperationException
	{
		JsonArray nodes = new JsonArray();
		IdentityHashMap<Object, Integer> ids = new IdentityHashMap<>();
		JsonElement reference = encode(root, ids, nodes);
		JsonObject graph = new JsonObject();
		graph.add("root", reference);
		graph.add("nodes", nodes);
		return positional(graph);
	}

	/**
	 * The stored form: each object type's field names are listed once, and each node is an array — an object as its type's
	 * index then its field values in that order, any other node as its kind's letter, an array's component type, and its
	 * entries. Saved raid plans have thousands of nodes of a dozen types, so this saves the repeated names.
	 */
	private static JsonObject positional(JsonObject graph)
	{
		Map<String, Integer> typeIndex = new LinkedHashMap<>();
		JsonArray types = new JsonArray();
		JsonArray nodes = new JsonArray();
		for (JsonElement element : graph.getAsJsonArray("nodes"))
		{
			JsonObject node = element.getAsJsonObject();
			String kind = node.get("kind").getAsString();
			JsonArray packed = new JsonArray();
			if ("object".equals(kind))
			{
				JsonObject fields = node.getAsJsonObject("fields");
				JsonArray names = new JsonArray();
				fields.entrySet().forEach(field -> names.add(field.getKey()));
				String signature = node.get("type").getAsString() + names;
				Integer index = typeIndex.get(signature);
				if (index == null)
				{
					index = types.size(); typeIndex.put(signature, index);
					JsonArray type = new JsonArray(); type.add(node.get("type")); type.add(names); types.add(type);
				}
				packed.add(index);
				fields.entrySet().forEach(field -> packed.add(field.getValue()));
			}
			else
			{
				packed.add(letter(kind));
				if ("array".equals(kind)) { packed.add(node.get("type")); }
				packed.add(node.getAsJsonArray("entries"));
			}
			nodes.add(packed);
		}
		JsonObject stored = new JsonObject();
		stored.add("types", types);
		stored.add("root", graph.get("root"));
		stored.add("nodes", nodes);
		return stored;
	}

	/** The working form that {@link #decode} reads, rebuilt from {@link #positional}. */
	private static JsonObject named(JsonObject stored)
	{
		JsonArray types = stored.getAsJsonArray("types");
		JsonArray source = stored.getAsJsonArray("nodes");
		if (source.size() > MAX_NODES) { throw new IllegalArgumentException("Cache graph is too large"); }
		JsonArray nodes = new JsonArray();
		for (JsonElement element : source)
		{
			JsonArray packed = element.getAsJsonArray();
			JsonObject node = new JsonObject();
			JsonElement head = packed.get(0);
			if (head.getAsJsonPrimitive().isNumber())
			{
				JsonArray type = types.get(head.getAsInt()).getAsJsonArray();
				JsonArray names = type.get(1).getAsJsonArray();
				if (packed.size() != names.size() + 1) { throw new IllegalArgumentException("Malformed cache node"); }
				JsonObject fields = new JsonObject();
				for (int i = 0; i < names.size(); i++) { fields.add(names.get(i).getAsString(), packed.get(i + 1)); }
				node.addProperty("kind", "object");
				node.add("type", type.get(0));
				node.add("fields", fields);
				node.add("entries", new JsonArray());
			}
			else
			{
				String kind = kind(head.getAsString());
				node.addProperty("kind", kind);
				int next = 1;
				if ("array".equals(kind)) { node.add("type", packed.get(next++)); }
				node.add("entries", packed.get(next).getAsJsonArray());
			}
			nodes.add(node);
		}
		JsonObject graph = new JsonObject();
		graph.add("root", stored.get("root"));
		graph.add("nodes", nodes);
		return graph;
	}

	/** One-letter names for the non-object node kinds in the stored form, and back. */
	private static final Map<String, String> LETTERS = Map.of("map", "m", "identityMap", "i", "list", "l", "set", "s", "array", "a");
	private static final Map<String, String> KINDS = Map.of("m", "map", "i", "identityMap", "l", "list", "s", "set", "a", "array");

	private static String letter(String kind)
	{
		String letter = LETTERS.get(kind);
		if (letter == null) { throw new IllegalArgumentException("Unknown cache node"); }
		return letter;
	}

	private static String kind(String letter)
	{
		String kind = KINDS.get(letter);
		if (kind == null) { throw new IllegalArgumentException("Unknown cache node"); }
		return kind;
	}

	/**
	 * Numbers are bare where the decoder knows their type: fields and arrays declared with a primitive or the exact
	 * wrapper type, and Integers in untyped collections (the only bare number those can hold). Others stay tagged.
	 */
	private JsonElement encode(Object value, Class<?> declared, IdentityHashMap<Object, Integer> ids, JsonArray nodes)
		throws ReflectiveOperationException
	{
		if ((value instanceof Integer && declared == Object.class) || (value instanceof Number && bare(declared, value)))
		{
			return new JsonPrimitive((Number) value);
		}
		return encode(value, ids, nodes);
	}

	/** Floats stay tagged: Gson reads them through a double. Non-finite doubles are not valid JSON numbers. */
	private static boolean bare(Class<?> declared, Object value)
	{
		if (value instanceof Float || (value instanceof Double && !Double.isFinite((Double) value))) { return false; }
		return declared.isPrimitive() || declared == value.getClass();
	}

	private JsonElement encode(Object value, IdentityHashMap<Object, Integer> ids, JsonArray nodes) throws ReflectiveOperationException
	{
		if (value == null) { return JsonNull.INSTANCE; }
		if (value instanceof String) { return new JsonPrimitive((String) value); }
		if (value instanceof Boolean) { return new JsonPrimitive((Boolean) value); }
		if (value instanceof Number)
		{
			JsonObject number = new JsonObject(); number.addProperty("number", value.getClass().getSimpleName());
			number.addProperty("value", value.toString()); return number;
		}
		Class<?> type = value instanceof Enum ? ((Enum<?>) value).getDeclaringClass() : value.getClass();
		if (type.isEnum())
		{
			checked(type.getName());
			JsonObject item = new JsonObject(); item.addProperty("enum", type.getName());
			item.addProperty("name", ((Enum<?>) value).name()); return item;
		}
		Integer known = ids.get(value);
		if (known != null) { return reference(known); }
		if (nodes.size() >= MAX_NODES) { throw new IllegalArgumentException("Cache graph is too large"); }
		int id = nodes.size();
		ids.put(value, id);
		JsonObject node = new JsonObject(); nodes.add(node);
		JsonArray entries = new JsonArray();
		if (value instanceof Map)
		{
			node.addProperty("kind", value instanceof IdentityHashMap ? "identityMap" : "map");
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
			{
				JsonArray pair = new JsonArray(); pair.add(encode(entry.getKey(), Object.class, ids, nodes));
				pair.add(encode(entry.getValue(), Object.class, ids, nodes)); entries.add(pair);
			}
		}
		else if (value instanceof Iterable)
		{
			node.addProperty("kind", value instanceof Set ? "set" : "list");
			for (Object item : (Iterable<?>) value) { entries.add(encode(item, Object.class, ids, nodes)); }
		}
		else if (type.isArray())
		{
			node.addProperty("kind", "array"); node.addProperty("type", type.getComponentType().getName());
			for (int i = 0; i < Array.getLength(value); i++)
			{ entries.add(encode(Array.get(value, i), type.getComponentType(), ids, nodes)); }
		}
		else
		{
			checked(type.getName()); node.addProperty("kind", "object"); node.addProperty("type", type.getName());
			JsonObject fields = new JsonObject();
			for (Field field : fields(type))
			{
				Object item = field.get(value);
				// SearchResults wraps this identity map in an unmodifiable view.
				if ("setupPotions".equals(field.getName()) && item instanceof Map) { item = new IdentityHashMap<>((Map<?, ?>) item); }
				fields.add(field.getName(), encode(item, field.getType(), ids, nodes));
			}
			node.add("fields", fields);
		}
		node.add("entries", entries);
		return reference(id);
	}

	<T> T decode(JsonObject graph, Class<T> expected) throws ReflectiveOperationException
	{
		return decode(graph, expected, null);
	}

	<T> T decode(JsonObject stored, Class<T> expected, com.bestgearsetup.data.GameData catalogue) throws ReflectiveOperationException
	{
		JsonObject graph = named(stored);
		JsonArray nodes = graph.getAsJsonArray("nodes");
		if (nodes.size() > MAX_NODES) { throw new IllegalArgumentException("Cache graph is too large"); }
		Object[] values = new Object[nodes.size()];
		boolean[] shared = new boolean[values.length];
		for (int i = 0; i < values.length; i++)
		{
			JsonObject node = nodes.get(i).getAsJsonObject();
			switch (node.get("kind").getAsString())
			{
				case "map": values[i] = new LinkedHashMap<>(); break;
				case "identityMap": values[i] = new IdentityHashMap<>(); break;
				case "list": values[i] = new ArrayList<>(); break;
				case "set": values[i] = new LinkedHashSet<>(); break;
				case "array": values[i] = Array.newInstance(component(node.get("type").getAsString()), node.getAsJsonArray("entries").size()); break;
				case "object":
					Class<?> type = checked(node.get("type").getAsString());
					values[i] = currentCatalogue(node, type, catalogue);
					shared[i] = values[i] != null;
					if (!shared[i]) { values[i] = gson.fromJson("{}", type); }
					break;
				default: throw new IllegalArgumentException("Unknown cache node");
			}
		}
		// Fill object fields before populating value-keyed maps/sets, so keys already have their final hash.
		for (int i = 0; i < values.length; i++)
		{
			JsonObject node = nodes.get(i).getAsJsonObject();
			if ("object".equals(node.get("kind").getAsString()) && !shared[i])
			{
				for (Field field : fields(values[i].getClass()))
				{
					JsonElement saved = node.getAsJsonObject("fields").get(field.getName());
					if (saved == null) { throw new IllegalArgumentException("Missing cache field"); }
					field.set(values[i], scalar(saved, field.getType(), values));
				}
			}
			else if (values[i].getClass().isArray())
			{
				JsonArray entries = node.getAsJsonArray("entries");
				for (int j = 0; j < entries.size(); j++) { Array.set(values[i], j, scalar(entries.get(j), values[i].getClass().getComponentType(), values)); }
			}
		}
		for (int i = values.length - 1; i >= 0; i--)
		{
			JsonArray entries = nodes.get(i).getAsJsonObject().getAsJsonArray("entries");
			if (values[i] instanceof Map)
			{
				for (JsonElement entry : entries)
				{
					JsonArray pair = entry.getAsJsonArray();
					((Map<Object, Object>) values[i]).put(scalar(pair.get(0), Object.class, values), scalar(pair.get(1), Object.class, values));
				}
			}
			else if (values[i] instanceof java.util.Collection)
			{
				for (JsonElement entry : entries) { ((java.util.Collection<Object>) values[i]).add(scalar(entry, Object.class, values)); }
			}
		}
		return expected.cast(scalar(graph.get("root"), expected, values));
	}

	/** Optimizer switch checks compare catalogue weapons by identity; never mutate the live catalogue on restore. */
	private Object currentCatalogue(JsonObject node, Class<?> type, com.bestgearsetup.data.GameData data) throws ClassNotFoundException
	{
		JsonObject fields = node.getAsJsonObject("fields");
		if (type == com.bestgearsetup.data.GearItem.class && data != null)
		{
			com.bestgearsetup.data.Slot slot = (com.bestgearsetup.data.Slot) scalar(fields.get("slot"), com.bestgearsetup.data.Slot.class, new Object[0]);
			int id = (Integer) scalar(fields.get("id"), int.class, new Object[0]);
			return slot == null ? null : data.getItem(slot, id);
		}
		if (type == com.bestgearsetup.data.Spell.class && data != null)
		{
			String name = fields.get("name").getAsString();
			String book = fields.get("spellbook").getAsString();
			return data.getSpells().stream().filter(spell -> name.equals(spell.getName()) && book.equals(spell.getSpellbook())).findFirst().orElse(null);
		}
		if (type == com.bestgearsetup.calc.AttackStyle.class)
		{
			String name = fields.get("name").getAsString();
			com.bestgearsetup.calc.AttackStyle.Type style = (com.bestgearsetup.calc.AttackStyle.Type)
				scalar(fields.get("type"), com.bestgearsetup.calc.AttackStyle.Type.class, new Object[0]);
			return com.bestgearsetup.calc.AttackStyle.parse(name + "," + style.name().toLowerCase(java.util.Locale.ROOT)
				+ "," + fields.get("stance").getAsString());
		}
		return null;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Object scalar(JsonElement item, Class<?> expected, Object[] values) throws ClassNotFoundException
	{
		if (item.isJsonNull()) { return null; }
		if (item.isJsonObject())
		{
			JsonObject object = item.getAsJsonObject();
			if (object.has("ref")) { return values[object.get("ref").getAsInt()]; }
			if (object.has("number"))
			{
				String value = object.get("value").getAsString();
				switch (object.get("number").getAsString())
				{
					case "Integer": return Integer.valueOf(value);
					case "Long": return Long.valueOf(value);
					case "Double": return Double.valueOf(value);
					case "Float": return Float.valueOf(value);
					case "Short": return Short.valueOf(value);
					case "Byte": return Byte.valueOf(value);
					default: throw new IllegalArgumentException("Unsupported number");
				}
			}
			Class<?> type = checked(object.get("enum").getAsString());
			if (!type.isEnum()) { throw new IllegalArgumentException("Expected enum"); }
			return Enum.valueOf((Class) type, object.get("name").getAsString());
		}
		if (expected != Object.class) { return gson.fromJson(item, expected); }
		JsonPrimitive primitive = item.getAsJsonPrimitive();
		if (primitive.isBoolean()) { return primitive.getAsBoolean(); }
		if (primitive.isString()) { return primitive.getAsString(); }
		return Integer.valueOf(primitive.getAsString());
	}

	private static JsonObject reference(int id)
	{
		JsonObject result = new JsonObject(); result.addProperty("ref", id); return result;
	}

	private static Class<?> component(String name) throws ClassNotFoundException
	{
		switch (name)
		{
			case "int": return int.class;
			case "double": return double.class;
			case "long": return long.class;
			default: return checked(name);
		}
	}

	private static Class<?> checked(String name) throws ClassNotFoundException
	{
		if (!name.startsWith("com.bestgearsetup.")) { throw new IllegalArgumentException("Unsupported cache type"); }
		Class<?> type = Class.forName(name, false, SearchResultCodec.class.getClassLoader());
		if (!type.isEnum() && !DTO.contains(name.substring(18))) { throw new IllegalArgumentException("Unsupported cache DTO " + name); }
		return type;
	}

	private static List<Field> fields(Class<?> type)
	{
		List<Field> fields = new ArrayList<>();
		for (Class<?> current = type; current != Object.class; current = current.getSuperclass())
		{
			for (Field field : current.getDeclaredFields())
			{
				if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) { continue; }
				if (Modifier.isTransient(field.getModifiers()) && !Set.of("slot", "phase", "toaRaidLevel").contains(field.getName())) { continue; }
				field.setAccessible(true); fields.add(field);
			}
		}
		fields.sort(Comparator.comparing(Field::getName));
		return fields;
	}
}
