package com.bestgearsetup;

import com.bestgearsetup.calc.AttackStyle;
import com.bestgearsetup.calc.DpsResult;
import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.GameData;
import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Potion;
import com.bestgearsetup.data.Slot;
import com.bestgearsetup.data.Spell;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Saved results in Gson's field format, with each plugin object written once and then as its index in write order,
 * so shared loadouts and the DPS keys of the identity-keyed setup potions survive. Types come from the declared
 * fields, never from the record.
 */
final class SearchResultCodec
{
	private static final int MAX_NODES = 200_000;
	private static final TypeToken<?> SETUP_POTIONS = TypeToken.getParameterized(Map.class, DpsResult.class,
		TypeToken.getParameterized(List.class, Potion.class).getType());
	private final Gson gson;

	SearchResultCodec(Gson gson) { this.gson = gson; }

	JsonElement encode(Object root)
	{
		return graph(null).toJsonTree(root);
	}

	<T> T decode(JsonElement stored, Class<T> expected)
	{
		return decode(stored, expected, null);
	}

	<T> T decode(JsonElement stored, Class<T> expected, GameData catalogue)
	{
		return graph(catalogue).fromJson(stored, expected);
	}

	/** A Gson for one record: the reference numbering lives in its factory. */
	private Gson graph(GameData catalogue)
	{
		return gson.newBuilder().serializeNulls().serializeSpecialFloatingPointValues().enableComplexMapKeySerialization()
			.registerTypeAdapterFactory(new References(catalogue)).create();
	}

	private static final class References implements TypeAdapterFactory
	{
		private final GameData catalogue;
		private final IdentityHashMap<Object, Integer> written = new IdentityHashMap<>();
		private final List<Object> read = new ArrayList<>();

		References(GameData catalogue) { this.catalogue = catalogue; }

		@Override
		@SuppressWarnings("unchecked")
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
		{
			if (type.equals(SETUP_POTIONS)) { return (TypeAdapter<T>) potions(gson); }
			Class<? super T> raw = type.getRawType();
			if (!raw.getName().startsWith("com.bestgearsetup.") || Enum.class.isAssignableFrom(raw)) { return null; }
			TypeAdapter<T> fields = gson.getDelegateAdapter(this, type);
			TypeAdapter<Slot> slots = gson.getAdapter(Slot.class);
			TypeAdapter<EncounterPhase> phases = gson.getAdapter(EncounterPhase.class);
			// Gson skips transient fields; these three are search state, so they lead a [state..., fields] array.
			boolean gear = raw == GearItem.class;
			boolean monster = raw == Monster.class;
			return new TypeAdapter<T>()
			{
				@Override
				public void write(JsonWriter out, T value) throws IOException
				{
					Integer known = written.get(value);
					if (value == null || known != null)
					{
						if (value == null) { out.nullValue(); } else { out.value(known); }
						return;
					}
					if (written.size() >= MAX_NODES) { throw new IllegalArgumentException("Cache graph is too large"); }
					written.put(value, written.size());
					if (gear || monster) { out.beginArray(); }
					if (gear) { slots.write(out, ((GearItem) value).getSlot()); }
					if (monster) { phases.write(out, ((Monster) value).getPhase()); out.value(((Monster) value).getToaRaidLevel()); }
					fields.write(out, value);
					if (gear || monster) { out.endArray(); }
				}

				@Override
				public T read(JsonReader in) throws IOException
				{
					if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
					if (in.peek() == JsonToken.NUMBER)
					{
						int id = in.nextInt();
						Object value = id >= 0 && id < read.size() ? read.get(id) : null;
						if (value == null) { throw new IllegalArgumentException("Malformed cache reference"); }
						return (T) raw.cast(value);
					}
					if (read.size() >= MAX_NODES) { throw new IllegalArgumentException("Cache graph is too large"); }
					int id = read.size();
					read.add(null);
					if (gear || monster) { in.beginArray(); }
					Slot slot = gear ? slots.read(in) : null;
					EncounterPhase phase = monster ? phases.read(in) : null;
					int toaRaidLevel = monster ? in.nextInt() : 0;
					T value = fields.read(in);
					if (gear || monster) { in.endArray(); }
					if (gear) { ((GearItem) value).setSlot(slot); }
					if (monster) { ((Monster) value).setPhase(phase); ((Monster) value).setToaRaidLevel(toaRaidLevel); }
					value = (T) current(value);
					read.set(id, value);
					return value;
				}
			};
		}

		/** Saved as alternating keys and values: the keys are the setups' own DPS results, matched by identity. */
		private TypeAdapter<Map<DpsResult, List<Potion>>> potions(Gson gson)
		{
			TypeAdapter<DpsResult> keys = gson.getAdapter(DpsResult.class);
			@SuppressWarnings("unchecked")
			TypeAdapter<List<Potion>> values = (TypeAdapter<List<Potion>>) gson.getAdapter(TypeToken.getParameterized(List.class, Potion.class));
			return new TypeAdapter<Map<DpsResult, List<Potion>>>()
			{
				@Override
				public void write(JsonWriter out, Map<DpsResult, List<Potion>> map) throws IOException
				{
					if (map == null) { out.nullValue(); return; }
					out.beginArray();
					for (Map.Entry<DpsResult, List<Potion>> entry : map.entrySet())
					{
						keys.write(out, entry.getKey());
						values.write(out, entry.getValue());
					}
					out.endArray();
				}

				@Override
				public Map<DpsResult, List<Potion>> read(JsonReader in) throws IOException
				{
					if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
					Map<DpsResult, List<Potion>> map = new IdentityHashMap<>();
					in.beginArray();
					while (in.hasNext()) { map.put(keys.read(in), values.read(in)); }
					in.endArray();
					return map;
				}
			};
		}

		/** Optimizer switch checks compare catalogue weapons by identity; never mutate the live catalogue on restore. */
		private Object current(Object value)
		{
			if (value instanceof AttackStyle)
			{
				AttackStyle style = (AttackStyle) value;
				return AttackStyle.parse(style.getName() + "," + style.getType().name().toLowerCase(Locale.ROOT) + "," + style.getStance());
			}
			Object live = null;
			if (catalogue != null && value instanceof GearItem && ((GearItem) value).getSlot() != null)
			{
				live = catalogue.getItem(((GearItem) value).getSlot(), ((GearItem) value).getId());
			}
			else if (catalogue != null && value instanceof Spell)
			{
				Spell saved = (Spell) value;
				live = catalogue.getSpells().stream().filter(spell -> spell.getName().equals(saved.getName())
					&& spell.getSpellbook().equals(saved.getSpellbook())).findFirst().orElse(null);
			}
			return live == null ? value : live;
		}
	}
}
