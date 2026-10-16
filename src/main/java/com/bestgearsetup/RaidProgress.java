package com.bestgearsetup;

import com.bestgearsetup.data.EncounterPhase;
import com.bestgearsetup.data.MonsterSummary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/** Live raid navigation only. Completion and browsing never change or recalculate the saved kit. */
final class RaidProgress
{
	@Value
	static class Update
	{
		RaidPlanType type;
		String encounter;
		String status;
		long revision;
		boolean active;
	}

	private RaidPlanType type = RaidPlanType.SINGLE;
	private RaidPlan plan;
	private final Map<String, List<RaidEncounter>> rooms = new LinkedHashMap<>();
	private final Set<String> completed = new HashSet<>();
	private final Map<String, Set<Object>> deaths = new HashMap<>();
	private boolean active;
	private boolean finished;
	private int outsideTicks;
	private long revision;
	private String room;
	private String encounter;
	private String roomHint;
	private boolean puzzleHint;
	private Update update = new Update(RaidPlanType.SINGLE, null, "", 0, false);

	synchronized void reset()
	{
		type = RaidPlanType.SINGLE;
		plan = null;
		rooms.clear();
		clearRun();
		active = false;
		emit();
	}

	private void clearRun()
	{
		completed.clear(); deaths.clear();
		finished = false; outsideTicks = 0; room = null; encounter = null;
		roomHint = null; puzzleHint = false;
	}

	/** Brief loading gaps don't end a run. A different plan of the same raid keeps live progress. */
	synchronized void tick(RaidPlanType selected, RaidPlan latest, boolean inside)
	{
		if (selected == null) { selected = RaidPlanType.SINGLE; }
		if (selected != type)
		{
			reset();
			type = selected;
		}
		if (type == RaidPlanType.SINGLE) { return; }
		if (!inside)
		{
			if (active && ++outsideTicks >= 3) { active = false; clearRun(); emit(); }
			return;
		}
		outsideTicks = 0;
		boolean entered = !active;
		if (entered) { clearRun(); active = true; }
		boolean changed = latest != null && latest.getType() == type && latest != plan;
		if (changed)
		{
			plan = latest;
			rooms.clear();
			for (RaidEncounter fight : plan.getEncounters())
			{
				String key = room(type, fight.getTarget().getName());
				if (key != null) { rooms.computeIfAbsent(key, unused -> new ArrayList<>()).add(fight); }
			}
			rooms.entrySet().removeIf(entry -> entry.getValue().stream().allMatch(RaidEncounter::isDisplayOnly));
		}
		if (entered || changed)
		{
			if (plan != null && !finished)
			{
				RaidEncounter kept = encounter == null ? null : plan.getEncounters().stream()
					.filter(fight -> fight.getLabel().equals(encounter)).findFirst().orElse(null);
				if (kept != null) { choose(kept); }
				else if (roomHint != null && !completed.contains(roomHint) && rooms.containsKey(roomHint))
				{
					RaidEncounter start = plan.getStartingEncounter();
					choose(puzzleHint && completed.isEmpty() && roomHint.equals(room(type, start.getTarget().getName()))
						? start : first(roomHint, !puzzleHint));
				}
				else if (completed.isEmpty() && room == null) { choose(plan.getStartingEncounter()); }
				else { chooseNext(room); }
			}
			emit();
		}
	}

	synchronized boolean isActive() { return active; }
	synchronized boolean isFinished() { return finished; }
	synchronized Update current() { return update; }

	/** A target or live phase selects its setup once; manually browsing between events remains possible. */
	synchronized void target(MonsterSummary target, EncounterPhase phase)
	{
		if (!active || finished || plan == null) { return; }
		String key = room(type, target.getName());
		if (key == null || completed.contains(key)) { return; }
		if (phase == null && plan.getEncounters().stream().anyMatch(fight -> fight.getLabel().equals(encounter)
			&& fight.getTarget().getName().equals(target.getName()))) { return; }
		RaidEncounter match = plan.encounterFor(target.getName(), phase);
		if (match == null) { return; }
		if (!match.getLabel().equals(encounter)) { choose(match); emit(); }
	}

	/** Vasa's live form leads over stale attack targets; unchanged phases leave manual browsing alone. */
	synchronized boolean vasaPhase(boolean crystal)
	{
		if (!active || finished || type != RaidPlanType.COX_CM || !"vasa".equals(room)
			|| completed.contains("vasa")) { return false; }
		String name = crystal ? "glowing crystal" : "vasa nistirio";
		RaidEncounter match = rooms.get("vasa").stream()
			.filter(fight -> fight.getTarget().getName().startsWith(name)).findFirst().orElse(null);
		if (match == null) { return false; }
		if (!match.getLabel().equals(encounter)) { choose(match); emit(); }
		return true;
	}

	/** ToA room changes follow the player's actual chosen path rather than assuming the planned path order. */
	synchronized void enteredRoom(String key, boolean puzzle)
	{
		if (!active || finished || completed.contains(key)) { return; }
		roomHint = key; puzzleHint = puzzle;
		if (!rooms.containsKey(key)) { return; }
		if (key.equals(room) && encounter != null && (puzzle || key.equals("wardens"))) { return; }
		RaidEncounter match = first(key, !puzzle);
		if (match != null) { choose(match); emit(); }
	}

	/** Trusted game messages, with markup already removed. Puzzle completion opens the boss, not the next path. */
	synchronized void message(String text)
	{
		if (!active || finished) { return; }
		String message = text.toLowerCase(Locale.ROOT).trim();
		if (type == RaidPlanType.TOA && message.startsWith("challenge complete: "))
		{
			String name = message.substring("challenge complete: ".length()).split("[.]|duration:", 2)[0].trim();
			name = name.replaceFirst("\\s*\\([^)]*\\)$", "");
			boolean puzzle = name.startsWith("path of ");
			String key = toaChallenge(puzzle ? name.substring("path of ".length()) : name);
			if (key == null) { return; }
			if (puzzle) { enteredRoom(key, false); }
			else { complete(key); }
		}
		else if (type == RaidPlanType.TOB)
		{
			int start = message.indexOf("wave '");
			int end = start < 0 ? -1 : message.indexOf('\'', start + 6);
			if (end < 0 || !message.substring(end + 1).trim().matches("(?:\\([^)]*\\)\\s+)?complete(?:d)?!.*")) { return; }
			String name = message.substring(start + 6, end);
			String key = name.equals("the nylocas") ? "nylocas" : name.equals("the final challenge") ? "verzik"
				: name.equals("the pestilent bloat") ? "bloat" : room(type, name);
			if (key != null) { complete(key); }
		}
		else if (type == RaidPlanType.COX_CM && message.startsWith("congratulations - your raid is complete!"))
		{ complete("olm"); }
	}

	/** CoX room completion counts actual deaths of the room's required targets, not disappearances or adds. */
	synchronized void died(Object actor, MonsterSummary target)
	{
		if (!active || finished || type != RaidPlanType.COX_CM || !coxCompletionTarget(target.getName())) { return; }
		String key = room(type, target.getName());
		if (key != null && !completed.contains(key))
		{
			deaths.computeIfAbsent(key, unused -> Collections.newSetFromMap(new IdentityHashMap<>())).add(actor);
		}
	}

	/** A resurrected/reset actor isn't a kill. Defer completion until all of this tick's NPC updates are seen. */
	synchronized void alive(Object actor, MonsterSummary target)
	{
		Set<Object> pending = deaths.get(room(type, target.getName()));
		if (pending != null) { pending.remove(actor); }
	}

	synchronized void settleDeaths(Set<String> livingRooms)
	{
		if (!active || plan == null || type != RaidPlanType.COX_CM) { return; }
		for (String key : new ArrayList<>(deaths.keySet()))
		{
			if (rooms.containsKey(key) && !livingRooms.contains(key) && deaths.get(key).size() >= requiredDeaths(key))
			{ complete(key); deaths.remove(key); }
		}
	}

	private int requiredDeaths(String key)
	{
		if (key.equals("vanguards")) { return 3; }
		if (key.equals("muttadiles")) { return 2; }
		if (key.equals("shamans") || key.equals("tightrope") || key.equals("guardians") || key.equals("mystics"))
		{ return Math.max(1, (int) Math.round(rooms.get(key).stream().mapToDouble(RaidEncounter::getRepetitions).sum())); }
		return 1;
	}

	private void complete(String key)
	{
		if (finished || !completed.add(key)) { return; }
		room = key;
		if (key.equals("olm") || key.equals("verzik") || key.equals("wardens")) { finished = true; }
		else { chooseNext(key); }
		emit();
	}

	private void chooseNext(String after)
	{
		boolean following = type == RaidPlanType.TOA || after == null || !rooms.containsKey(after);
		for (String key : rooms.keySet())
		{
			if (following && !completed.contains(key)) { choose(first(key, false)); return; }
			if (key.equals(after)) { following = true; }
		}
	}

	private RaidEncounter first(String key, boolean boss)
	{
		List<RaidEncounter> fights = rooms.get(key);
		if (fights == null || fights.isEmpty()) { return null; }
		if (boss && key.equals("apmeken"))
		{ return fights.stream().filter(fight -> fight.getTarget().getName().equals("ba-ba")).findFirst().orElse(fights.get(0)); }
		// Optional targets remain available to browse, but room progression opens the planned fight.
		return fights.stream().filter(fight -> !fight.isDisplayOnly()).findFirst().orElse(null);
	}

	private void choose(RaidEncounter fight)
	{
		if (fight == null) { return; }
		room = room(type, fight.getTarget().getName());
		encounter = fight.getLabel();
	}

	private void emit()
	{
		String status = !active ? "" : finished ? "Raid complete" : plan == null ? "Raid in progress"
			: "Raid progress: " + completed.size() + " / " + rooms.size() + " rooms completed"
				+ (encounter == null ? "" : ". Gear: " + encounter);
		update = new Update(type, encounter, status, ++revision, active);
	}

	private static String toaChallenge(String name)
	{
		switch (name)
		{
			case "ba-ba": case "apmeken": return "apmeken";
			case "kephri": case "scabaras": return "scabaras";
			case "akkha": case "het": return "het";
			case "zebak": case "crondis": return "crondis";
			case "the wardens": case "wardens": return "wardens";
			default: return null;
		}
	}

	static boolean coxCompletionTarget(String name)
	{
		return name.startsWith("tekton") || name.startsWith("ice demon") || name.startsWith("lizardman shaman")
			|| name.startsWith("vanguard") || name.startsWith("abyssal portal") || name.startsWith("deathly")
			|| name.startsWith("guardian (") || name.startsWith("vasa nistirio") || name.startsWith("skeletal mystic")
			|| name.startsWith("muttadile") || name.equals("great olm (head)");
	}

	static String room(RaidPlanType type, String raw)
	{
		if (raw == null) { return null; }
		String name = raw.toLowerCase(Locale.ROOT);
		if (type == RaidPlanType.TOB)
		{
			if (name.startsWith("the maiden") || name.startsWith("nylocas matomenos")) { return "maiden"; }
			if (name.startsWith("pestilent bloat")) { return "bloat"; }
			if (name.startsWith("nylocas")) { return "nylocas"; }
			if (name.startsWith("sotetseg")) { return "sotetseg"; }
			if (name.startsWith("xarpus")) { return "xarpus"; }
			if (name.startsWith("verzik")) { return "verzik"; }
		}
		else if (type == RaidPlanType.TOA)
		{
			if (name.startsWith("baboon") || name.equals("ba-ba")) { return "apmeken"; }
			if (name.startsWith("kephri") || name.endsWith(" scarab")) { return "scabaras"; }
			if (name.startsWith("akkha")) { return "het"; }
			if (name.startsWith("zebak")) { return "crondis"; }
			if (name.startsWith("obelisk") || name.contains("warden")) { return "wardens"; }
		}
		else if (type == RaidPlanType.COX_CM)
		{
			if (name.startsWith("tekton")) { return "tekton"; }
			if (name.startsWith("ice demon")) { return "ice demon"; }
			if (name.startsWith("lizardman shaman")) { return "shamans"; }
			if (name.startsWith("vanguard")) { return "vanguards"; }
			if (name.startsWith("vespula") || name.startsWith("abyssal portal")) { return "vespula"; }
			if (name.startsWith("deathly")) { return "tightrope"; }
			if (name.startsWith("guardian")) { return "guardians"; }
			if (name.startsWith("vasa") || name.startsWith("glowing crystal")) { return "vasa"; }
			if (name.startsWith("skeletal mystic")) { return "mystics"; }
			if (name.startsWith("muttadile")) { return "muttadiles"; }
			if (name.startsWith("great olm")) { return "olm"; }
		}
		return null;
	}
}
