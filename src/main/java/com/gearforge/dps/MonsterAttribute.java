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
	/**
	 * The three vampyre tiers, named to match the data exactly. This was a single {@code VAMPYRE}
	 * constant, which the source spells {@code vampyre1}, {@code vampyre2} and {@code vampyre3} — so it
	 * never matched anything and every vampyre bane was silently dead.
	 */
	VAMPYRE1,
	VAMPYRE2,
	VAMPYRE3,
	/** Chambers of Xeric creatures, which raise the twisted bow's scaling cap. */
	XERICIAN,
	LEAFY,
	SHADE,
	FIERY,
	RAT
}
