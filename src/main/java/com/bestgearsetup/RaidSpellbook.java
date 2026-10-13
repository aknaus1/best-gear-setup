package com.bestgearsetup;

/** One spellbook used for the entire raid; avoids planning incompatible spells between rooms. */
public enum RaidSpellbook
{
	STANDARD("Standard", "standard"), ANCIENT("Ancient", "ancient"), ARCEUUS("Arceuus", "arceuus");
	private final String label;
	private final String key;
	RaidSpellbook(String label, String key) { this.label = label; this.key = key; }
	public String key() { return key; }
	@Override public String toString() { return label; }
}
