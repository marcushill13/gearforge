package com.gearforge.dps;

/**
 * Monster traits that item effects key off. Populated properly by {@code MonsterRepository} later;
 * present now so target data and the set effect registry agree on vocabulary from the start.
 */
public enum MonsterAttribute
{
	UNDEAD,
	DEMON,
	DRAGON,
	KALPHITE,
	GOLEM,
	VAMPYRE,
	/** Chambers of Xeric creatures, which raise the twisted bow's scaling cap. */
	XERICIAN,
	LEAFY,
	SHADE,
	FIERY,
	RAT
}
