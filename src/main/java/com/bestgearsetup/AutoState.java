package com.bestgearsetup;

/** Automatic account detection, or an explicit planning override. */
public enum AutoState
{
	AUTO("Auto"), ON("On"), OFF("Off");
	private final String label;
	AutoState(String label) { this.label = label; }
	@Override public String toString() { return label; }

	boolean resolve(Boolean detected, boolean fallback)
	{
		return this == ON || this == AUTO && (detected == null ? fallback : detected);
	}
}
