package com.bestgearsetup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One spellbook used for the entire raid; avoids planning incompatible spells between rooms. */
public enum RaidSpellbook
{
	AUTO("Auto", ""), STANDARD("Standard", "standard"), ANCIENT("Ancient", "ancient"), ARCEUUS("Arceuus", "arceuus");
	private final String label;
	private final String key;
	RaidSpellbook(String label, String key) { this.label = label; this.key = key; }
	public String key() { return key; }

	/** Compare available books; freeze duties always take precedence over a saved manual choice. */
	static List<RaidSpellbook> candidates(RaidPlanType type, BestGearSetupConfig config)
	{
		if (type == RaidPlanType.TOB && config.tobRole().freezes())
		{
			if (!config.ancientMagicks() || !config.styleMagic())
			{ throw new IllegalStateException("South and North freeze require Ancient Magicks and the Magic attack style to be enabled."); }
			return Collections.singletonList(ANCIENT);
		}
		RaidSpellbook choice = config.raidSpellbook();
		List<RaidSpellbook> books = new ArrayList<>();
		// Prefer Arceuus for equal estimated combat times; thralls are still gated by the player's settings/unlocks.
		for (RaidSpellbook book : new RaidSpellbook[]{ARCEUUS, STANDARD, ANCIENT})
		{
			if ((choice == AUTO || choice == book)
				&& (book != ANCIENT || config.ancientMagicks()) && (book != ARCEUUS || config.arceuusSpellbook()))
			{ books.add(book); }
		}
		if (books.isEmpty()) { throw new IllegalStateException("The selected raid spellbook is disabled under Spellbooks. Enable it or use Auto."); }
		return books;
	}

	/** The same objective used for shared-kit selection, rather than adding unrelated encounter DPS values. */
	static double seconds(RaidPlan plan)
	{
		return plan.getEstimatedSeconds();
	}

	static boolean faster(RaidPlan candidate, RaidPlan current)
	{
		return candidate != null && (current == null || seconds(candidate) < seconds(current) - 1e-6);
	}
	@Override public String toString() { return label; }
}
