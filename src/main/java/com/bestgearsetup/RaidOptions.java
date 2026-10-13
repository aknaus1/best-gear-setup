package com.bestgearsetup;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import lombok.Builder;
import lombok.Value;

/**
 * Raid planning choices that change the encounter list: the ToA path order, Theatre of Blood roles and
 * per-encounter attack styles. All are saved in the plugin configuration.
 */
@Value
@Builder(toBuilder = true)
public class RaidOptions
{
	public static final RaidOptions DEFAULT = RaidOptions.builder().build();

	@Builder.Default
	ToaPathOrder toaPathOrder = ToaPathOrder.BA_BA_FIRST;
	@Builder.Default
	MaidenRole maidenRole = MaidenRole.BOTH;
	@Builder.Default
	NylocasRole nylocasRole = NylocasRole.ALL;
	/** Saved style choices by {@link #key} (raid and {@link RaidEncounter#getStyleKey}); others use Auto. */
	@Builder.Default
	Map<String, RaidStyle> styles = Collections.emptyMap();

	/** The two common ToA path orders; the Wardens are always last. */
	public enum ToaPathOrder
	{
		BA_BA_FIRST("Ba-Ba, Kephri, Akkha, Zebak"), ZEBAK_FIRST("Zebak, Kephri, Akkha, Ba-Ba");
		private final String label;
		ToaPathOrder(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	/**
	 * Theatre of Blood Maiden role. Freezers barrage the Nylocas Matomenos and stay on magic; DPS players leave the
	 * nylocas to them; Both (solo and small teams) plans the boss and the nylocas.
	 */
	public enum MaidenRole
	{
		BOTH("DPS and freeze"), DPS("DPS"), FREEZER("Freezer (magic)");
		private final String label;
		MaidenRole(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	/** Theatre of Blood Nylocas role: the wave nylocas the player kills. Every player fights each Vasilias form. */
	public enum NylocasRole
	{
		ALL("All styles"), MELEE("Melee"), RANGED("Ranged"), MAGIC("Magic");
		private final String label;
		NylocasRole(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	public static RaidOptions of(BestGearSetupConfig config)
	{
		return RaidOptions.builder().toaPathOrder(config.toaPathOrder()).maidenRole(config.tobMaidenRole())
			.nylocasRole(config.tobNylocasRole()).styles(parse(config.raidStyles())).build();
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
