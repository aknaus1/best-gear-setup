package com.bestgearsetup.calc;

import com.bestgearsetup.data.GearItem;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.Slot;
import java.util.Locale;

/** Equipment requirements for dragonfire, independent of ordinary defence bonuses. */
public final class DragonfireProtection
{
	private DragonfireProtection()
	{
	}

	private static String name(Monster monster)
	{
		return monster.getName().toLowerCase(Locale.ROOT);
	}

	static boolean isProtectiveShield(GearItem item)
	{
		String n = item == null ? "" : item.getName().toLowerCase(Locale.ROOT);
		return n.startsWith("anti-dragon shield") || n.startsWith("dragonfire shield")
			|| n.startsWith("dragonfire ward") || n.startsWith("ancient wyvern shield")
			|| n.equals("elemental shield") || n.equals("mind shield");
	}

	private static boolean wyvern(Monster monster)
	{
		return name(monster).contains("wyvern");
	}

	private static boolean metal(Monster monster)
	{
		return name(monster).matches("(bronze|iron|steel|mithril|adamant|rune) dragon.*")
			|| name(monster).startsWith("drake");
	}

	/** Whether this encounter requires dragonfire or wyvern breath protection. */
	public static boolean applies(Monster monster)
	{
		String n = name(monster);
		return !n.contains("baby") && (wyvern(monster) || metal(monster)
			|| n.contains("brutal") && n.contains("dragon")
			|| n.matches("(green|blue|red|black|lava|frost|reanimated) dragon.*")
			|| n.startsWith("vorkath") || n.startsWith("king black dragon") || n.startsWith("galvek")
			|| n.startsWith("elvarg"));
	}

	/** Meets normal protection requirements; boss chip damage and avoidable specials may remain. */
	public static boolean allowed(Monster monster, Loadout loadout, OptimizerSettings settings, PlayerLevels levels)
	{
		if (!settings.isRequireFireProtection() || !applies(monster))
		{
			return true;
		}
		GearItem item = loadout.getWeapon() == null || loadout.getWeapon().isTwoHanded() ? null : loadout.get(Slot.SHIELD);
		String shieldName = item == null ? "" : item.getName().toLowerCase(Locale.ROOT);
		boolean shield = isProtectiveShield(item);
		if (wyvern(monster))
		{
			return shield && !shieldName.startsWith("anti-dragon shield");
		}
		shield &= !shieldName.equals("elemental shield") && !shieldName.equals("mind shield");
		boolean magic = settings.isProtectMagic() && levels.getPrayer() >= 37;
		boolean potion = settings.getAntifire() != Antifire.NONE;
		boolean superPotion = settings.getAntifire() == Antifire.SUPER;
		String n = name(monster);
		if (n.startsWith("vorkath"))
		{
			return potion && shield || superPotion && magic;
		}
		if (n.startsWith("galvek") || n.startsWith("elvarg"))
		{
			return potion && shield;
		}
		if (n.startsWith("king black dragon"))
		{
			return potion && shield || superPotion && magic;
		}
		return superPotion || potion && (shield || magic && !metal(monster));
	}

	/** Summary of the active protection assumptions for the lookup. */
	public static String note(Monster monster, OptimizerSettings settings)
	{
		if (!applies(monster))
		{
			return null;
		}
		if (!settings.isRequireFireProtection())
		{
			return "Dragonfire / icy breath protection is not required by this lookup.";
		}
		String base = "Fire protection required: " + settings.getAntifire()
			+ (settings.isProtectMagic() ? " + Protect from Magic (requires 37 Prayer). " : ". ");
		if (wyvern(monster))
		{
			return base + "Wyverns need an icy breath shield; antifire and anti-dragon shields do not work. "
				+ "Icy breath can still hit 10; only the ancient wyvern shield prevents freezing.";
		}
		String n = name(monster);
		if (n.startsWith("vorkath"))
		{
			return base + "Requires potion + fire shield, or super antifire + Protect from Magic. "
				+ "Standard fire can still hit 10 without super antifire + shield; avoidable specials remain.";
		}
		if (n.startsWith("king black dragon"))
		{
			return base + "Requires antifire + shield or super antifire + Protect from Magic. "
				+ "Special breaths can still hit 10 with a shield, or 15 with super antifire + prayer.";
		}
		if (n.startsWith("galvek") || n.startsWith("elvarg"))
		{
			return base + "Requires antifire + fire shield; quest boss chip damage and special attacks may remain.";
		}
		return base + (metal(monster) ? "Metal dragons and drakes ignore Protect from Magic. " : "")
			+ (n.contains("brutal") ? "Brutal dragons also have a separate magic attack. " : "")
			+ "Protection constrains gear; eating and movement downtime are not simulated.";
	}
}
