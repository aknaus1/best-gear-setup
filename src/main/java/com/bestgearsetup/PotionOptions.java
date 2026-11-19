package com.bestgearsetup;

import java.util.Locale;

/** Selectable boosts from the bundled potion catalog; legacy names are retained. */
public final class PotionOptions
{
	private PotionOptions() {}

	/** A choice's stored potion name, from its constant: OVERLOAD_PLUS is "overload (+)", WIZARD_S_MIND_BOMB "wizard's mind bomb". */
	static String value(Enum<?> choice)
	{
		return choice.name().toLowerCase(Locale.ROOT).replace("_s_", "'s ").replace('_', ' ').replaceAll(" (minus|plus|nmz)$", " ($1)")
			.replace("(minus)", "(-)").replace("(plus)", "(+)");
	}

	/** Short sentence-case labels so the settings dropdowns leave room for their names. */
	static String display(String name)
	{
		if (name.equals("best")) { return "Best available"; }
		String shown = name.replace("mature wizard's mind bomb", "mature mind bomb").replace("wizard's mind bomb", "mind bomb");
		if (shown.endsWith(" potion")) { shown = shown.substring(0, shown.length() - " potion".length()); }
		return Character.toUpperCase(shown.charAt(0)) + shown.substring(1);
	}

	public enum Melee
	{
		BEST,
		NONE,
		ATTACK_POTION,
		COMBAT_POTION,
		SUPER_ATTACK,
		SUPER_COMBAT_POTION,
		ZAMORAK_BREW,
		RUBY_HARVEST,
		OVERLOAD_MINUS,
		OVERLOAD,
		OVERLOAD_PLUS,
		OVERLOAD_NMZ,
		SMELLING_SALTS,
		STRENGTH_POTION,
		SUPER_STRENGTH,
		BLACK_WARLOCK;
		@Override public String toString() { return display(value(this)); }
	}

	public enum Ranged
	{
		BEST,
		NONE,
		RANGING_POTION,
		BASTION_POTION,
		LIZARDKICKER,
		WILD_PIE,
		OVERLOAD_MINUS,
		OVERLOAD,
		OVERLOAD_PLUS,
		OVERLOAD_NMZ,
		SUPER_RANGING,
		SMELLING_SALTS;
		@Override public String toString() { return display(value(this)); }
	}

	public enum Magic
	{
		BEST,
		NONE,
		MAGIC_POTION,
		BATTLEMAGE_POTION,
		ANCIENT_BREW,
		FORGOTTEN_BREW,
		IMBUED_HEART,
		SATURATED_HEART,
		WIZARD_S_MIND_BOMB,
		MATURE_WIZARD_S_MIND_BOMB,
		OVERLOAD_MINUS,
		OVERLOAD,
		OVERLOAD_PLUS,
		OVERLOAD_NMZ,
		SUPER_MAGIC_POTION,
		SMELLING_SALTS;
		@Override public String toString() { return display(value(this)); }
	}
}
