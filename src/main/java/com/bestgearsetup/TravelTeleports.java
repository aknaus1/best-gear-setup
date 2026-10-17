package com.bestgearsetup;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

/**
 * Hand-maintained travel table: where a boss is fought and the teleport items that reach it. Locations and
 * teleports come from the OSRS Wiki pages of each boss or area (see THIRD_PARTY_NOTICES.md); nothing here is
 * taken from the Shortest Path plugin, which is only asked for a route at run time when the player opts in.
 */
final class TravelTeleports
{
	/** Tiles (in either axis, any plane) from a destination within which no teleport is suggested. */
	static final int NEAR_TILES = 64;
	/** Route steps listed in the sidebar before the rest are elided. */
	private static final int MAX_ROUTE_STEPS = 4;

	/** Variants fought somewhere else (Deadman, PvM Arena, Leagues echoes, LMS-style arenas): no travel advice. */
	private static final List<String> ELSEWHERE = Arrays.asList("(deadman", "(pvm arena)", "(echo)", "(lanscape)");

	/** A teleport item; any one of its ids (charge states) will do, the first is shown. */
	@Value
	static class Teleport
	{
		String name;
		/** Lower-case words that identify this teleport in a Shortest Path transport description. */
		String keyword;
		List<Integer> itemIds;
	}

	@Value
	static class Destination
	{
		String name;
		/** Where a route should end: the boss's entrance or arrival point. */
		WorldPoint target;
		/** Points counted as already there, including the fight area itself (which may be an instance). */
		List<WorldPoint> near;
		/** In order of preference when the player owns several. */
		List<Teleport> teleports;
	}

	/** A suggested teleport for a destination the player isn't near. */
	@Value
	static class Advice
	{
		Destination destination;
		Teleport teleport;
		boolean owned;
		/** True when the teleport is the one Shortest Path's route uses. */
		boolean routed;
	}

	static final Destination ZULRAH = destination("Zulrah", point(2193, 3060),
		Arrays.asList(point(2193, 3060), point(2268, 3070)),
		teleport("Zul-andra teleport", "zul-andra", ItemID.TELEPORTSCROLL_ZULANDRA));
	static final Destination VORKATH = destination("Vorkath", point(2641, 3697),
		Arrays.asList(point(2641, 3697), point(2269, 4062)),
		teleport("Rellekka teleport", "rellekka", ItemID.NZONE_TELETAB_RELLEKKA),
		teleport("Enchanted lyre", "lyre", ItemID.MAGIC_STRUNG_LYRE_5, ItemID.MAGIC_STRUNG_LYRE_4,
			ItemID.MAGIC_STRUNG_LYRE_3, ItemID.MAGIC_STRUNG_LYRE_2, ItemID.MAGIC_STRUNG_LYRE));
	static final Destination BARROWS = destination("Barrows", point(3567, 3291),
		Collections.singletonList(point(3567, 3291)),
		teleport("Barrows teleport", "barrows", ItemID.TELETAB_BARROWS));
	static final Destination CERBERUS = destination("Cerberus", point(1302, 1250),
		Arrays.asList(point(1238, 1250), point(1366, 1250), point(1302, 1314)),
		teleport("Key master teleport", "key master", ItemID.TELEPORTSCROLL_CERBERUS));
	static final Destination GIANT_MOLE = destination("Giant Mole", point(2993, 3378),
		Arrays.asList(point(2993, 3378), point(1759, 5189)),
		teleport("Falador teleport", "falador", ItemID.POH_TABLET_FALADORTELEPORT));
	static final Destination CORPOREAL_BEAST = destination("Corporeal Beast", point(2993, 4254, 2),
		Arrays.asList(point(3203, 3681), point(2993, 4254), point(2993, 4382)),
		teleport("Games necklace", "games necklace", ItemID.NECKLACE_OF_MINIGAMES_1, ItemID.NECKLACE_OF_MINIGAMES_2,
			ItemID.NECKLACE_OF_MINIGAMES_3, ItemID.NECKLACE_OF_MINIGAMES_4, ItemID.NECKLACE_OF_MINIGAMES_5,
			ItemID.NECKLACE_OF_MINIGAMES_6, ItemID.NECKLACE_OF_MINIGAMES_7, ItemID.NECKLACE_OF_MINIGAMES_8));
	static final Destination KRAKEN = destination("Kraken Cove", point(2278, 3611),
		Arrays.asList(point(2278, 3611), point(2272, 10016)),
		teleport("Piscatoris teleport", "piscatoris", ItemID.TELEPORTSCROLL_PISCATORIS));
	static final Destination GOD_WARS = destination("God Wars Dungeon", point(2918, 3745),
		Arrays.asList(point(2918, 3745), point(2892, 5300)),
		teleport("Trollheim teleport", "trollheim", ItemID.NZONE_TELETAB_TROLLHEIM));
	static final Destination SLAYER_TOWER = destination("Slayer Tower", point(3428, 3535),
		Collections.singletonList(point(3428, 3535)),
		teleport("Slayer ring", "slayer ring", ItemID.SLAYER_RING_ETERNAL, ItemID.SLAYER_RING_1, ItemID.SLAYER_RING_2,
			ItemID.SLAYER_RING_3, ItemID.SLAYER_RING_4, ItemID.SLAYER_RING_5, ItemID.SLAYER_RING_6,
			ItemID.SLAYER_RING_7, ItemID.SLAYER_RING_8));
	static final Destination THEATRE_OF_BLOOD = destination("Theatre of Blood", point(3663, 3219),
		Arrays.asList(point(3663, 3219), point(3649, 3230)),
		teleport("Drakan's medallion", "drakan", ItemID.DRAKANS_MEDALLION));
	/** Xeric's Honour needs an ancient tablet used on the talisman first; the panel says so. */
	static final Destination CHAMBERS_OF_XERIC = destination("Chambers of Xeric", point(1245, 3557),
		Collections.singletonList(point(1245, 3557)),
		teleport("Xeric's talisman (Xeric's Honour)", "xeric", ItemID.XERIC_TALISMAN));

	/** Base monster names (lower case, without the Wiki's version suffix) to their destination. */
	private static final Map<String, Destination> BY_MONSTER = new HashMap<>();

	static
	{
		BY_MONSTER.put("zulrah", ZULRAH);
		BY_MONSTER.put("vorkath", VORKATH);
		for (String brother : Arrays.asList("ahrim the blighted", "dharok the wretched", "guthan the infested",
			"karil the tainted", "torag the corrupted", "verac the defiled"))
		{
			BY_MONSTER.put(brother, BARROWS);
		}
		BY_MONSTER.put("cerberus", CERBERUS);
		BY_MONSTER.put("giant mole", GIANT_MOLE);
		BY_MONSTER.put("corporeal beast", CORPOREAL_BEAST);
		BY_MONSTER.put("kraken", KRAKEN);
		for (String general : Arrays.asList("general graardor", "commander zilyana", "kree'arra", "k'ril tsutsaroth"))
		{
			BY_MONSTER.put(general, GOD_WARS);
		}
		BY_MONSTER.put("dusk", SLAYER_TOWER);
		BY_MONSTER.put("dawn", SLAYER_TOWER);
	}

	private TravelTeleports()
	{
	}

	/** The destination for a monster name as the bundled data spells it, e.g. "zulrah (serpentine)"; else null. */
	static Destination forMonster(String name)
	{
		if (name == null) { return null; }
		String lower = name.toLowerCase(Locale.ROOT);
		for (String elsewhere : ELSEWHERE)
		{
			if (lower.contains(elsewhere)) { return null; }
		}
		int version = lower.indexOf(" (");
		return BY_MONSTER.get((version < 0 ? lower : lower.substring(0, version)).trim());
	}

	/** Raid kits travel to the raid's lobby; Tombs of Amascut has no entry yet. */
	static Destination forRaid(RaidPlanType raid)
	{
		if (raid == RaidPlanType.COX_CM) { return CHAMBERS_OF_XERIC; }
		if (raid == RaidPlanType.TOB) { return THEATRE_OF_BLOOD; }
		return null;
	}

	/** Whether the player is within {@link #NEAR_TILES} of any of the destination's points. */
	static boolean isNear(Destination destination, WorldPoint player)
	{
		for (WorldPoint point : destination.getNear())
		{
			if (Math.max(Math.abs(point.getX() - player.getX()), Math.abs(point.getY() - player.getY())) <= NEAR_TILES)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The teleport to bring, or null when there's no destination, no known position, or the player is already near.
	 * A teleport Shortest Path's route uses comes first, then the first one owned, then the first listed.
	 *
	 * @param routeSteps Shortest Path's transport descriptions for this destination; empty when unknown
	 */
	static Advice advise(Destination destination, WorldPoint player, IntPredicate owns, List<String> routeSteps)
	{
		if (destination == null || player == null || isNear(destination, player)) { return null; }
		for (String step : routeSteps)
		{
			String lower = step.toLowerCase(Locale.ROOT);
			for (Teleport teleport : destination.getTeleports())
			{
				if (lower.contains(teleport.getKeyword())) { return new Advice(destination, teleport, owned(teleport, owns), true); }
			}
		}
		for (Teleport teleport : destination.getTeleports())
		{
			if (owned(teleport, owns)) { return new Advice(destination, teleport, true, false); }
		}
		Teleport first = destination.getTeleports().get(0);
		return new Advice(destination, first, false, false);
	}

	/**
	 * Sidebar lines: the teleport, then Shortest Path's route when known. Empty when nothing is suggested.
	 *
	 * @param cost the route's length in ticks, or -1 when unknown
	 */
	static List<String> describe(Advice advice, List<String> routeSteps, int cost)
	{
		if (advice == null) { return Collections.emptyList(); }
		List<String> lines = new java.util.ArrayList<>();
		lines.add("Teleport to " + advice.getDestination().getName() + ": " + advice.getTeleport().getName()
			+ (advice.isOwned() ? "" : " (not owned)"));
		if (!routeSteps.isEmpty())
		{
			int shown = Math.min(MAX_ROUTE_STEPS, routeSteps.size());
			String route = String.join(" > ", routeSteps.subList(0, shown)) + (shown < routeSteps.size() ? " > ..." : "");
			lines.add("Shortest Path: " + route + (cost > 0 ? " (" + duration(cost) + ")" : ""));
		}
		return lines;
	}

	/** Game ticks as an approximate travel time. */
	static String duration(int ticks)
	{
		int seconds = (int) Math.round(ticks * 0.6);
		return seconds < 90 ? "~" + seconds + "s" : "~" + Math.round(seconds / 60.0) + " min";
	}

	private static boolean owned(Teleport teleport, IntPredicate owns)
	{
		return teleport.getItemIds().stream().anyMatch(owns::test);
	}

	private static WorldPoint point(int x, int y)
	{
		return new WorldPoint(x, y, 0);
	}

	private static WorldPoint point(int x, int y, int plane)
	{
		return new WorldPoint(x, y, plane);
	}

	private static Teleport teleport(String name, String keyword, int... ids)
	{
		Integer[] boxed = Arrays.stream(ids).boxed().toArray(Integer[]::new);
		return new Teleport(name, keyword, Collections.unmodifiableList(Arrays.asList(boxed)));
	}

	private static Destination destination(String name, WorldPoint target, List<WorldPoint> near, Teleport... teleports)
	{
		return new Destination(name, target, Collections.unmodifiableList(near),
			Collections.unmodifiableList(Arrays.asList(teleports)));
	}
}
