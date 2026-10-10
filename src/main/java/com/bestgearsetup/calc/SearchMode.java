package com.bestgearsetup.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SearchMode
{
	OWNED_ONLY("Owned items only"),
	INVENTORY_ONLY("Inventory + equipped only"),
	BUDGET("Owned + budget"),
	UNLIMITED("Best in slot (no limit)");

	private final String displayName;

	/** Only items already held count and nothing is bought. */
	public boolean isHeldOnly()
	{
		return this == OWNED_ONLY || this == INVENTORY_ONLY;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
