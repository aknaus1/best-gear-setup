package com.bestgearsetup;

import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/** Raid scaling read once from the matching live raid; never written to planning configuration. */
final class LiveRaid
{
	final Map<String, Object> values = new HashMap<>();
	int maxCombat;
	boolean raidDetected;

	static Monster target(MonsterSummary summary)
	{
		Monster target = new Monster();
		target.setId(summary.getId()); target.setName(summary.getName());
		java.util.List<Monster.Attribute> attrs = new java.util.ArrayList<>();
		for (String name : summary.getAttributes())
		{
			Monster.Attribute attribute = new Monster.Attribute(); attribute.setName(name); attrs.add(attribute);
		}
		target.setAttributes(attrs);
		return target;
	}

	static LiveRaid read(Client client, MonsterSummary summary)
	{
		LiveRaid live = new LiveRaid();
		RaidScaling.Raid raid = RaidScaling.raid(target(summary));
		int size = 0;
		if (raid == RaidScaling.Raid.COX && client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1)
		{
			size = client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE_SCALED);
			if (size < 1) { size = client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE); }
			live.values.put("coxChallengeMode", client.getVarbitValue(VarbitID.RAIDS_CHALLENGE_MODE) == 1);
			live.maxCombat = client.getVarbitValue(VarbitID.RAIDS_CLIENT_HIGHESTCOMBAT);
		}
		else if (raid == RaidScaling.Raid.TOA && client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS) > 0)
		{
			for (int id : new int[]{VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1, VarbitID.TOA_CLIENT_P2, VarbitID.TOA_CLIENT_P3,
				VarbitID.TOA_CLIENT_P4, VarbitID.TOA_CLIENT_P5, VarbitID.TOA_CLIENT_P6, VarbitID.TOA_CLIENT_P7})
			{ size += client.getVarbitValue(id) > 0 ? 1 : 0; }
			live.values.put("toaRaidLevel", Math.max(0, Math.min(600, client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL))));
			String name = summary.getName().toLowerCase(Locale.ROOT);
			int path = name.contains("zebak") ? VarbitID.TOA_CLIENT_CRONDIS_LEVEL : name.contains("kephri") ? VarbitID.TOA_CLIENT_SCABARAS_LEVEL
				: name.contains("akkha") ? VarbitID.TOA_CLIENT_HET_LEVEL : name.contains("ba-ba") ? VarbitID.TOA_CLIENT_APMEKEN_LEVEL : -1;
			if (path >= 0) { live.values.put("toaPathLevel", Math.max(0, Math.min(6, client.getVarbitValue(path)))); }
		}
		else if ((raid == RaidScaling.Raid.TOB || raid == RaidScaling.Raid.TOB_ENTRY)
			&& client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS) > 0)
		{
			for (int id : new int[]{VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1, VarbitID.TOB_CLIENT_P2, VarbitID.TOB_CLIENT_P3, VarbitID.TOB_CLIENT_P4})
			{ size += client.getVarbitValue(id) > 0 ? 1 : 0; }
		}
		if (size >= 1 && size <= 100)
		{
			live.values.put("raidPartySize", size); live.raidDetected = true;
		}
		else
		{
			live.values.keySet().removeIf(key -> key.startsWith("toa") || key.equals("coxChallengeMode"));
			live.maxCombat = 0;
		}
		return live;
	}
}
