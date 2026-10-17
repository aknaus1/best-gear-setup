package com.bestgearsetup;

import com.bestgearsetup.data.MonsterSummary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a full fight's plan cache key keeps of the target it was searched from. A full fight plans every phase
 * whichever phase opened it, so keying the plan by that target would plan the whole fight again when it is reopened
 * from another phase (Zulrah changes form between right-clicks). The target only decides which encounters take the
 * landed-spec settings and the detected notes ({@link BestGearSetupPlugin#planRaidSpellbook}), so the key holds the
 * labels of those encounters, and nothing when neither applies.
 */
final class FightSearchKey
{
	/** Pre-fight defence reductions, as hits or damage: a raid or fight applies them only to the encounters of the target it was searched from. */
	static final List<String> LANDED_SPEC_HITS = List.of("specElderMaul", "specDwh", "specEmberlight", "specArclight",
		"specTonalztics", "specBgsDamage", "specSeercullDamage", "specAyakDamage");
	/** The same for reductions that are on or off. */
	static final List<String> LANDED_SPEC_FLAGS = List.of("specVulnerability", "specTomeOfWater");
	/** Settings each phase of a fight sets for itself (raidOverrides), so the player's own value never reaches the plan. */
	static final Set<String> PER_PHASE = Set.of("encounterPhase");

	private FightSearchKey()
	{
	}

	/** The labels of the encounters that fight {@code searched}, when landed specs or detected notes make that matter; otherwise empty. */
	static List<String> searchedFrom(List<RaidEncounter> encounters, MonsterSummary searched, BestGearSetupConfig config,
		List<String> notes)
	{
		if (notes.isEmpty() && !specsLanded(config)) { return Collections.emptyList(); }
		List<String> same = new ArrayList<>();
		for (RaidEncounter encounter : encounters)
		{
			if (encounter.getTarget().getId() == searched.getId()) { same.add(encounter.getLabel()); }
		}
		return Collections.unmodifiableList(same);
	}

	static boolean specsLanded(BestGearSetupConfig config)
	{
		Map<String, Object> values = ConfigSettings.read(config);
		for (String name : LANDED_SPEC_HITS)
		{
			if (((Number) values.get(name)).doubleValue() != 0) { return true; }
		}
		for (String name : LANDED_SPEC_FLAGS)
		{
			if (Boolean.TRUE.equals(values.get(name))) { return true; }
		}
		return false;
	}
}
