package com.bestgearsetup;

/** Selectable boosts from the bundled potion catalog; legacy names are retained. */
public final class PotionOptions
{
	private PotionOptions() {}

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
		BEST("best"),
		NONE("none"),
		ATTACK_POTION("attack potion"),
		COMBAT_POTION("combat potion"),
		SUPER_ATTACK("super attack"),
		SUPER_COMBAT_POTION("super combat potion"),
		ZAMORAK_BREW("zamorak brew"),
		RUBY_HARVEST("ruby harvest"),
		OVERLOAD_MINUS("overload (-)"),
		OVERLOAD("overload"),
		OVERLOAD_PLUS("overload (+)"),
		OVERLOAD_NMZ("overload (nmz)"),
		SMELLING_SALTS("smelling salts"),
		STRENGTH_POTION("strength potion"),
		SUPER_STRENGTH("super strength"),
		BLACK_WARLOCK("black warlock");
		private final String name;
		Melee(String name) { this.name = name; }
		public String value() { return name; }
		@Override public String toString() { return display(name); }
	}

	public enum Ranged
	{
		BEST("best"),
		NONE("none"),
		RANGING_POTION("ranging potion"),
		BASTION_POTION("bastion potion"),
		LIZARDKICKER("lizardkicker"),
		WILD_PIE("wild pie"),
		OVERLOAD_MINUS("overload (-)"),
		OVERLOAD("overload"),
		OVERLOAD_PLUS("overload (+)"),
		OVERLOAD_NMZ("overload (nmz)"),
		SUPER_RANGING("super ranging"),
		SMELLING_SALTS("smelling salts");
		private final String name;
		Ranged(String name) { this.name = name; }
		public String value() { return name; }
		@Override public String toString() { return display(name); }
	}

	public enum Magic
	{
		BEST("best"),
		NONE("none"),
		MAGIC_POTION("magic potion"),
		BATTLEMAGE_POTION("battlemage potion"),
		ANCIENT_BREW("ancient brew"),
		FORGOTTEN_BREW("forgotten brew"),
		IMBUED_HEART("imbued heart"),
		SATURATED_HEART("saturated heart"),
		WIZARD_S_MIND_BOMB("wizard's mind bomb"),
		MATURE_WIZARD_S_MIND_BOMB("mature wizard's mind bomb"),
		OVERLOAD_MINUS("overload (-)"),
		OVERLOAD("overload"),
		OVERLOAD_PLUS("overload (+)"),
		OVERLOAD_NMZ("overload (nmz)"),
		SUPER_MAGIC_POTION("super magic potion"),
		SMELLING_SALTS("smelling salts");
		private final String name;
		Magic(String name) { this.name = name; }
		public String value() { return name; }
		@Override public String toString() { return display(name); }
	}
}
