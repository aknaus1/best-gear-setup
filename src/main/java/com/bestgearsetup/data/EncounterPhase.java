package com.bestgearsetup.data;

/**
 * Temporary boss states selected separately from the bundled monster variants.
 * A phase only changes the target it names; other targets ignore it.
 */
public enum EncounterPhase
{
	STANDARD("Standard"),
	HUEYCOATL_PILLAR("Hueycoatl pillar"),
	ROYAL_TITANS_OUT_OF_MELEE("Titans out of melee"),
	ABYSSAL_SIRE_TRANSITION("Sire transition"),
	MOKHAIOTL_SHIELDED("Mokhaiotl shielded"),
	MOKHAIOTL_BURROWING("Mokhaiotl burrowing"),
	TD_UNSHIELDED("Tormented Demon unshielded"),
	TD_DEFENCELESS("TD shield, defenceless"),
	YAMA_MAGIC_TANK("Yama tank on Magic");

	private final String label;

	EncounterPhase(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
