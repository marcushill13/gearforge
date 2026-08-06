package com.gearforge.data;

/**
 * Where an owned item currently lives. "Gear I own" is the union of all three.
 */
public enum Storage
{
	BANK("Bank"),
	INVENTORY("Inventory"),
	EQUIPMENT("Worn");

	private final String displayName;

	Storage(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
