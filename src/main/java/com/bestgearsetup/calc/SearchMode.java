package com.bestgearsetup.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SearchMode
{
	OWNED_ONLY("Owned items only"),
	BUDGET("Owned + budget"),
	UNLIMITED("Best in slot (no limit)");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
