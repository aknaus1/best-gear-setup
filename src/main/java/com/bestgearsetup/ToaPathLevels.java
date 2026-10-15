package com.bestgearsetup;

import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.data.MonsterSummary;
import java.util.List;
import java.util.Locale;

/** Resolve a path's planned progression or its authoritative live level, including supporting targets. */
final class ToaPathLevels
{
	private ToaPathLevels() { }

	static String pathKey(String name)
	{
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.startsWith("akkha")) { return "akkha"; }
		if (lower.startsWith("kephri") || lower.startsWith("soldier scarab")
			|| lower.startsWith("arcane scarab") || lower.startsWith("spitting scarab")) { return "kephri"; }
		if (lower.startsWith("ba-ba") || lower.startsWith("baboon")) { return "ba-ba"; }
		return lower.startsWith("zebak") ? "zebak" : null;
	}

	/** The fixed path order a planned raid's encounters follow: whichever of Zebak and Ba-Ba is fought first. */
	static RaidOptions.ToaPathOrder orderOf(List<RaidEncounter> encounters)
	{
		for (RaidEncounter encounter : encounters)
		{
			String key = pathKey(encounter.getTarget().getName());
			if ("zebak".equals(key)) { return RaidOptions.ToaPathOrder.ZEBAK_FIRST; }
			if ("ba-ba".equals(key)) { return RaidOptions.ToaPathOrder.BA_BA_FIRST; }
		}
		return RaidOptions.ToaPathOrder.BA_BA_FIRST;
	}

	static RaidScaling.Settings resolve(MonsterSummary target, RaidScaling.Settings base,
		BestGearSetupConfig config, LiveRaid live, boolean planned)
	{
		String key = pathKey(target.getName());
		if (key == null) { return base; }
		Integer actual = live.pathLevels.get(key);
		if (actual != null) { return base.toBuilder().toaPathLevel(actual).toaWalkThePath(false).build(); }
		if (!planned) { return base; }
		boolean zebakFirst = config.toaPathOrder() == RaidOptions.ToaPathOrder.ZEBAK_FIRST;
		int completed = key.equals("kephri") ? 1 : key.equals("akkha") ? 2
			: key.equals(zebakFirst ? "zebak" : "ba-ba") ? 0 : 3;
		return base.toBuilder().toaPathLevel(Math.max(0, Math.min(3, config.toaStartingPathLevel())))
			.toaWalkThePath(config.toaWalkThePath()).toaCompletedPaths(completed).build();
	}
}
