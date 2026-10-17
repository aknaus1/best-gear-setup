package com.bestgearsetup;

import com.bestgearsetup.calc.RaidScaling;
import com.bestgearsetup.data.Monster;
import com.bestgearsetup.data.MonsterSummary;
import com.bestgearsetup.data.EncounterPhase;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;

/** Raid scaling read once from the matching live raid; never written to planning configuration. */
final class LiveRaid
{
	final Map<String, Object> values = new HashMap<>();
	final Map<String, Integer> pathLevels = new HashMap<>();
	int maxCombat;
	boolean raidDetected;
	/** Live offensive levels, player HP, available spec percent and target HP decile; empty for planning. */
	List<Integer> combat = Collections.emptyList();
	/** Canonical rune types and spell items available to this live refresh, including planned pouch contents. */
	Set<Integer> spellSupplies = Collections.emptySet();
	/** Publication guard for this transient encounter refresh; never part of the persisted raid plan. */
	SearchInputs inputs;
	/** Exact saved row: Warden P2's magic and ranged rows share the same catalogue target and phase. */
	String encounterLabel;

	boolean adaptive() { return !combat.isEmpty(); }

	/** Read after this tick's container and NPC updates, without changing the pre-raid assumptions. */
	static LiveRaid encounter(Client client, MonsterSummary summary, NPC npc)
	{
		LiveRaid live = read(client, summary);
		int ratio = npc.getHealthRatio(), scale = npc.getHealthScale();
		int decile = ratio > 0 && scale > 0 ? Math.min(10, (ratio * 10 + scale - 1) / scale) : 0;
		live.combat = Arrays.asList(client.getBoostedSkillLevel(Skill.ATTACK), client.getBoostedSkillLevel(Skill.STRENGTH),
			client.getBoostedSkillLevel(Skill.RANGED), client.getBoostedSkillLevel(Skill.MAGIC),
			client.getBoostedSkillLevel(Skill.HITPOINTS), Math.max(0, Math.min(100, client.getVarpValue(VarPlayerID.SA_ENERGY) / 10)), decile);
		return live;
	}

	/** Instanced source region, rather than the raid's allocated world coordinates. */
	static int region(Client client)
	{
		LocalPoint point = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getLocalLocation();
		return point == null ? -1 : WorldPoint.fromLocalInstance(client, point).getRegionID();
	}

	/** Party membership alone is insufficient: don't start a raid while the player is preparing in the lobby. */
	static boolean inProgress(Client client, RaidPlanType type, int region)
	{
		switch (type)
		{
			case COX_CM:
				return client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1
					&& client.getVarbitValue(VarbitID.RAIDS_CLIENT_PROGRESS) > 0
					&& client.getVarbitValue(VarbitID.RAIDS_CHALLENGE_MODE) == 1;
			case TOB:
				int status = client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS);
				return status == 2 || status == 3;
			case TOA: return toaRoom(region) != null || region == 14160 || region == 14672;
			default: return false;
		}
	}

	/** ToA template regions identify the actual path even if the player chooses a different order from the plan. */
	static String toaRoom(int region)
	{
		switch (region)
		{
			case 15186: case 15188: return "apmeken";
			case 14162: case 14164: return "scabaras";
			case 14674: case 14676: return "het";
			case 15698: case 15700: return "crondis";
			case 15184: case 15696: return "wardens";
			default: return null;
		}
	}

	static boolean toaPuzzle(int region)
	{
		return region == 15186 || region == 14162 || region == 14674 || region == 15698;
	}

	/**
	 * Akkha's phase from his NPC form, as with any other monster's variants. Each style form prays against the
	 * style it attacks with (OSRS Wiki), and the final stand prays magic and ranged; other forms leave it unchanged.
	 */
	static EncounterPhase akkhaPhase(NPC npc)
	{
		if (npc == null) { return null; }
		switch (npc.getId())
		{
			case NpcID.AKKHA_MELEE: return EncounterPhase.AKKHA_IMMUNE_MELEE;
			case NpcID.AKKHA_RANGE: return EncounterPhase.AKKHA_IMMUNE_RANGED;
			case NpcID.AKKHA_MAGE: return EncounterPhase.AKKHA_IMMUNE_MAGIC;
			case NpcID.AKKHA_ENRAGE_INITIAL:
			case NpcID.AKKHA_ENRAGE:
				return EncounterPhase.AKKHA_ENRAGE;
			default: return null;
		}
	}

	/**
	 * Live damage target in Akkha's boss room, before the player clicks it. Ordinary shadows remain present while
	 * inactive, when the game whitens their models. Missing models are unknown, not evidence of vulnerability.
	 * Final-stand shadows/dummies never select the ordinary shadow setup.
	 */
	static NPC akkhaTarget(Iterable<? extends NPC> npcs)
	{
		NPC boss = null, shadow = null;
		boolean unknownShadow = false;
		for (NPC npc : npcs)
		{
			if (npc == null || npc.isDead() || npc.getHealthRatio() == 0) { continue; }
			switch (npc.getId())
			{
				case NpcID.AKKHA_MELEE:
				case NpcID.AKKHA_RANGE:
				case NpcID.AKKHA_MAGE:
				case NpcID.AKKHA_ENRAGE_INITIAL:
				case NpcID.AKKHA_ENRAGE:
					boss = npc;
					break;
				case NpcID.AKKHA_SHADOW:
					Model model = npc.getModel();
					if (model == null) { unknownShadow = true; }
					else if (model.getOverrideAmount() == 0) { shadow = npc; }
					break;
				default: break;
			}
		}
		if (boss == null) { return null; }
		EncounterPhase phase = akkhaPhase(boss);
		if (phase == EncounterPhase.AKKHA_ENRAGE) { return boss; }
		if (shadow != null) { return shadow; }
		return unknownShadow || phase == null ? null : boss;
	}

	@Value
	static class WardenTarget
	{
		String label;
		/** Null during ejection until the real core NPC appears; its saved setup can already be shown. */
		NPC npc;
	}

	/** P2 forms are explicit: MAGE protects ranged/melee (icon 7), RANGE protects magic/melee (icon 8). */
	static String wardenEncounter(NPC npc)
	{
		if (npc == null) { return null; }
		switch (npc.getId())
		{
			case NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE: return "Elidinis P2: Magic";
			case NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE: return "Elidinis P2: Ranged";
			case NpcID.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED:
			case NpcID.TOA_WARDEN_ELIDINIS_CORE: return "Elidinis Core";
			case NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE: return "Tumeken P2: Magic";
			case NpcID.TOA_WARDEN_TUMEKEN_PHASE2_RANGE: return "Tumeken P2: Ranged";
			case NpcID.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED:
			case NpcID.TOA_WARDEN_TUMEKEN_CORE: return "Tumeken Core";
			default: return null;
		}
	}

	/** The boss's current form leads over a lingering core; an exposed boss opens core gear before it lands. */
	static WardenTarget wardenTarget(Iterable<? extends NPC> npcs)
	{
		NPC body = null, exposed = null, core = null;
		for (NPC npc : npcs)
		{
			if (npc == null || npc.isDead()) { continue; }
			switch (npc.getId())
			{
				case NpcID.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED:
				case NpcID.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED:
					exposed = npc; break;
				case NpcID.TOA_WARDEN_ELIDINIS_CORE:
				case NpcID.TOA_WARDEN_TUMEKEN_CORE:
					if (npc.getHealthRatio() != 0) { core = npc; }
					break;
				case NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE:
				case NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE:
				case NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE:
				case NpcID.TOA_WARDEN_TUMEKEN_PHASE2_RANGE:
					if (npc.getHealthRatio() != 0) { body = npc; }
					break;
				default: break;
			}
		}
		if (exposed != null)
		{
			String label = wardenEncounter(exposed);
			return new WardenTarget(label, core != null && label.equals(wardenEncounter(core)) ? core : null);
		}
		NPC target = body != null ? body : core;
		return target == null ? null : new WardenTarget(wardenEncounter(target), target);
	}

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
			live.pathLevels.put("zebak", Math.max(0, Math.min(6, client.getVarbitValue(VarbitID.TOA_CLIENT_CRONDIS_LEVEL))));
			live.pathLevels.put("kephri", Math.max(0, Math.min(6, client.getVarbitValue(VarbitID.TOA_CLIENT_SCABARAS_LEVEL))));
			live.pathLevels.put("akkha", Math.max(0, Math.min(6, client.getVarbitValue(VarbitID.TOA_CLIENT_HET_LEVEL))));
			live.pathLevels.put("ba-ba", Math.max(0, Math.min(6, client.getVarbitValue(VarbitID.TOA_CLIENT_APMEKEN_LEVEL))));
			Integer path = live.pathLevels.get(ToaPathLevels.pathKey(name));
			if (path != null) { live.values.put("toaPathLevel", path); }
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
			live.pathLevels.clear();
		}
		return live;
	}
}
