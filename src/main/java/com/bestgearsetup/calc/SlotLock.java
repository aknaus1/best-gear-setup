package com.bestgearsetup.calc;

import com.bestgearsetup.data.Slot;
import java.util.EnumMap;
import java.util.Map;
import lombok.Value;

/**
 * A user constraint on one equipment slot: a specific item, always empty, or always filled with
 * the fill-mode item (prayer / defence) even when that costs DPS.
 */
@Value
public class SlotLock
{
	public enum Kind
	{
		ITEM, EMPTY, FILL
	}

	Kind kind;
	/** Item id for {@link Kind#ITEM}, otherwise 0. */
	int itemId;

	public static SlotLock item(int id)
	{
		return new SlotLock(Kind.ITEM, id);
	}

	public static SlotLock empty()
	{
		return new SlotLock(Kind.EMPTY, 0);
	}

	public static SlotLock fill()
	{
		return new SlotLock(Kind.FILL, 0);
	}

	/** Serialise as "WEAPON:4151,SHIELD:empty,BODY:fill". */
	public static String format(Map<Slot, SlotLock> locks)
	{
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Slot, SlotLock> e : locks.entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			SlotLock l = e.getValue();
			sb.append(e.getKey().name()).append(':')
				.append(l.kind == Kind.ITEM ? String.valueOf(l.itemId) : l.kind.name().toLowerCase());
		}
		return sb.toString();
	}

	public static Map<Slot, SlotLock> parse(String text)
	{
		Map<Slot, SlotLock> out = new EnumMap<>(Slot.class);
		if (text == null || text.trim().isEmpty())
		{
			return out;
		}
		for (String part : text.split(","))
		{
			String[] kv = part.trim().split(":");
			if (kv.length != 2)
			{
				continue;
			}
			try
			{
				Slot slot = Slot.valueOf(kv[0].trim());
				String v = kv[1].trim();
				if (v.equalsIgnoreCase("empty"))
				{
					out.put(slot, empty());
				}
				else if (v.equalsIgnoreCase("fill"))
				{
					out.put(slot, fill());
				}
				else
				{
					out.put(slot, item(Integer.parseInt(v)));
				}
			}
			catch (IllegalArgumentException ignored)
			{
				// skip malformed entries (NumberFormatException is an IllegalArgumentException)
			}
		}
		return out;
	}
}
