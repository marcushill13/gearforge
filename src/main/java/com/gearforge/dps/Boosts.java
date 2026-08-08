package com.gearforge.dps;

/**
 * Potion boosts, as flat level increases.
 * <p>
 * Modelled because leaving them out makes GearForge disagree with every other DPS tool: prayers and
 * potions do not scale gear evenly, so an item that wins unbuffed can lose once they are on. That was
 * reported in the wild — a cape swap GearForge recommended was worse in a full-buff calculation.
 * <p>
 * Boost formulas are the standard ones: a potion raises the level by a flat amount plus a percentage
 * of the base level, floored.
 */
public enum Boosts
{
	/** No potion. */
	NONE,

	/**
	 * Super combat for melee, ranging potion for ranged, magic potion for magic — the ordinary choice
	 * for each style rather than the most exotic one.
	 */
	STANDARD;

	/**
	 * Super combat potion: +5 and 15% of the base level, to attack, strength and defence.
	 */
	public int meleeBoost(int level)
	{
		return this == NONE ? 0 : (int) Math.floor(5 + 0.15 * level);
	}

	/**
	 * Ranging potion: +4 and 10% of the base level.
	 */
	public int rangedBoost(int level)
	{
		return this == NONE ? 0 : (int) Math.floor(4 + 0.10 * level);
	}

	/**
	 * Magic potion: a flat +4. Note this only improves accuracy — a standard spell's max hit does not
	 * scale with Magic level, so the damage is unchanged.
	 */
	public int magicBoost(int level)
	{
		return this == NONE ? 0 : 4;
	}

	@Override
	public String toString()
	{
		return this == NONE ? "No potion" : "Potion";
	}
}
