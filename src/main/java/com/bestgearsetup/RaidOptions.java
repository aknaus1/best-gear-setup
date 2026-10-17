package com.bestgearsetup;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import lombok.Builder;
import lombok.Value;

/**
 * Raid planning choices that change the encounter list: the ToA path order and More Overlords invocation, Theatre
 * of Blood roles, per-encounter attack styles and the quick-switch swap limit. All are saved in the plugin
 * configuration.
 */
@Value
@Builder(toBuilder = true)
public class RaidOptions
{
	public static final RaidOptions DEFAULT = RaidOptions.builder().build();

	@Builder.Default
	ToaPathOrder toaPathOrder = ToaPathOrder.BA_BA_FIRST;
	@Builder.Default
	TobRole tobRole = TobRole.MELEE_DPS;
	/** The More Overlords invocation, which doubles Kephri's soldier and spitting scarabs. */
	boolean toaMoreOverlords;
	/** Saved style choices by {@link #key} (raid and {@link RaidEncounter#getStyleKey}); others use Auto. */
	@Builder.Default
	Map<String, RaidStyle> styles = Collections.emptyMap();
	/** Most swaps a switch group's quick switches may make, weapon included; see {@link RaidEncounter#getSwitchLimit}. */
	@Builder.Default
	int switchLimit = RaidEncounter.DEFAULT_SWITCH_LIMIT;

	/**
	 * The two common ToA path orders; the Wardens are always last. Auto plans the raid in each order and keeps the
	 * faster kit; before that search it lists the encounters Ba-Ba first.
	 */
	public enum ToaPathOrder
	{
		AUTO("Auto (fastest)"), BA_BA_FIRST("Ba-Ba, Kephri, Akkha, Zebak"), ZEBAK_FIRST("Zebak, Kephri, Akkha, Ba-Ba");
		private final String label;
		ToaPathOrder(String label) { this.label = label; }

		/** The fixed orders a raid search plans: both for Auto, otherwise just this one. */
		public List<ToaPathOrder> candidates()
		{
			return this == AUTO ? Arrays.asList(BA_BA_FIRST, ZEBAK_FIRST) : Collections.singletonList(this);
		}

		@Override public String toString() { return label; }
	}

	/**
	 * One Theatre role determines both Maiden duties and the wave colour killed in the Nylocas room.
	 * Every role still fights Maiden and each Vasilias form.
	 */
	public enum TobRole
	{
		SOUTH_FREEZE("South freeze"), NORTH_FREEZE("North freeze"), RANGED_DPS("Range DPS"), MELEE_DPS("Melee DPS");
		private final String label;
		TobRole(String label) { this.label = label; }

		/** Whether the role barrages Maiden's nylocas, which needs Ancient Magicks. */
		public boolean freezes() { return this == SOUTH_FREEZE || this == NORTH_FREEZE; }

		/** North freeze melees waves in smaller teams and mages them in a five-player team. */
		public RaidStyle waveStyle(int partySize)
		{
			if (this == SOUTH_FREEZE || this == NORTH_FREEZE && partySize >= 5) { return RaidStyle.MAGIC; }
			return this == RANGED_DPS ? RaidStyle.RANGED : RaidStyle.MELEE;
		}

		@Override public String toString() { return label; }
	}

	public static RaidOptions of(BestGearSetupConfig config)
	{
		return RaidOptions.builder().toaPathOrder(config.toaPathOrder()).tobRole(config.tobRole())
			.toaMoreOverlords(config.toaMoreOverlords())
			.styles(parse(config.raidStyles())).switchLimit(config.quickSwitchLimit()).build();
	}

	/** The saved choice for an encounter's {@link RaidEncounter#getStyleKey}. */
	public RaidStyle style(RaidPlanType type, String styleKey)
	{
		return styles.getOrDefault(key(type, styleKey), RaidStyle.AUTO);
	}

	static String key(RaidPlanType type, String styleKey)
	{
		return type.name() + "|" + styleKey;
	}

	/** Saved choices, such as "COX_CM|Mystics=MELEE;TOA|Zebak=MAGIC"; unknown entries are ignored. */
	public static Map<String, RaidStyle> parse(String saved)
	{
		Map<String, RaidStyle> styles = new LinkedHashMap<>();
		if (saved == null) { return styles; }
		for (String entry : saved.split(";"))
		{
			int split = entry.lastIndexOf('=');
			if (split <= 0) { continue; }
			try
			{
				RaidStyle style = RaidStyle.valueOf(entry.substring(split + 1).trim().toUpperCase(Locale.ROOT));
				if (style != RaidStyle.AUTO) { styles.put(entry.substring(0, split).trim(), style); }
			}
			catch (IllegalArgumentException e)
			{
				// A style removed in a later version falls back to Auto.
			}
		}
		return styles;
	}

	/** The saved value with one style key's choice replaced; Auto removes it. Entries are sorted for stable values. */
	public static String withStyle(String saved, RaidPlanType type, String styleKey, RaidStyle style)
	{
		Map<String, RaidStyle> styles = new TreeMap<>(parse(saved));
		if (style == null || style == RaidStyle.AUTO) { styles.remove(key(type, styleKey)); }
		else { styles.put(key(type, styleKey), style); }
		return format(styles);
	}

	/** Whether any boss of the raid has a saved choice. */
	public static boolean hasStyles(String saved, RaidPlanType type)
	{
		return parse(saved).keySet().stream().anyMatch(key -> key.startsWith(type.name() + "|"));
	}

	/** The saved value with every choice for one raid removed, so each of its bosses is back on Auto. */
	public static String withoutStyles(String saved, RaidPlanType type)
	{
		Map<String, RaidStyle> styles = new TreeMap<>(parse(saved));
		styles.keySet().removeIf(key -> key.startsWith(type.name() + "|"));
		return format(styles);
	}

	private static String format(Map<String, RaidStyle> styles)
	{
		StringBuilder out = new StringBuilder();
		styles.forEach((key, value) -> out.append(out.length() == 0 ? "" : ";").append(key).append('=').append(value.name()));
		return out.toString();
	}
}
