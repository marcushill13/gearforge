package com.gearforge.setups;

/**
 * Where a setup came from. Shown on the card so a generated setup is distinguishable from one the
 * player built.
 */
public enum SetupSource
{
	MANUAL("Saved by hand"),
	CURRENT_GEAR("From what you were wearing"),
	BIS("From the BiS tab"),
	IMPORTED("Imported"),
	SHARED("From a share code");

	private final String displayName;

	SetupSource(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
