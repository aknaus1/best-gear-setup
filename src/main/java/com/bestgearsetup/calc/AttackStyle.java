package com.bestgearsetup.calc;

import com.bestgearsetup.data.GameData;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Value;

/**
 * One weapon combat style, parsed from the catalogue's "name,type,stance" strings,
 * e.g. "flick,slash,accurate", "rapid,ranged,rapid", "spell,magic,magic".
 */
@Value
public class AttackStyle
{
	public enum Type
	{
		/** ATLATL is never parsed from a style string; see {@link WeaponRules#tabType}. */
		STAB, SLASH, CRUSH, RANGED, MAGIC, ATLATL
	}

	/**
	 * Parsed styles by catalogue string. Styles are immutable, and one instance per string lets the optimiser's
	 * identity-keyed score memo recognise the same style across weapons and calls.
	 */
	private static final Map<String, AttackStyle> PARSED = new ConcurrentHashMap<>();

	String name;
	Type type;
	/** accurate, aggressive, controlled, defensive, rapid, longrange, magic (autocast) or manual. */
	String stance;

	public static AttackStyle parse(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		AttackStyle known = PARSED.get(raw);
		if (known != null)
		{
			return known;
		}
		AttackStyle parsed = parseUncached(raw);
		if (parsed != null)
		{
			PARSED.put(raw, parsed);
		}
		return parsed;
	}

	private static AttackStyle parseUncached(String raw)
	{
		String[] parts = raw.toLowerCase(Locale.ROOT).split(",");
		if (parts.length < 3)
		{
			return null;
		}
		Type type;
		switch (parts[1].trim())
		{
			case "stab":
				type = Type.STAB;
				break;
			case "slash":
				type = Type.SLASH;
				break;
			case "crush":
				type = Type.CRUSH;
				break;
			case "ranged":
				type = Type.RANGED;
				break;
			case "magic":
				type = Type.MAGIC;
				break;
			default:
				return null;
		}
		return new AttackStyle(parts[0].trim(), type, parts[2].trim());
	}

	public boolean isMelee()
	{
		return type == Type.STAB || type == Type.SLASH || type == Type.CRUSH;
	}

	/** Autocast with a spell, as opposed to a powered staff's built-in attack. */
	public boolean isAutocast()
	{
		return type == Type.MAGIC && "magic".equals(stance);
	}

	/** A spell selected from the spellbook, independent of the weapon's autocast options. */
	public boolean isManualCast()
	{
		return type == Type.MAGIC && "manual".equals(stance);
	}

	/** An explicit spell, rather than a weapon's built-in magic attack. */
	public boolean isSpellCast()
	{
		return isAutocast() || isManualCast();
	}

	@Override
	public String toString()
	{
		return GameData.titleCase(name) + " (" + GameData.titleCase(type.name().toLowerCase(Locale.ROOT))
			+ ", " + GameData.titleCase(stance) + ")";
	}
}
