package com.gearforge.dps;

import com.gearforge.data.PlayerLevels;

/**
 * Potion boosts, as flat level increases on top of base levels.
 * <p>
 * Every formula here is transcribed from the OSRS Wiki DPS calculator's own potion definitions, so
 * GearForge's numbers line up with the tool people check against. Each is "flat amount plus a
 * percentage of the base level, floored" — the percentage is of the <em>base</em> level, not the
 * boosted one.
 * <p>
 * Some brews are a genuine trade: Forgotten brew and Ancient brew raise Magic while draining Attack,
 * Strength and Defence, which is why the drains are modelled rather than ignored.
 */
public enum Potion
{
	NONE("No potion", -1),

	OVERLOAD_PLUS("Overload (+)", 20996),
	SMELLING_SALTS("Smelling salts", 27343),
	SUPER_COMBAT("Super combat", 12695),
	RANGING("Ranging potion", 2444),
	SATURATED_HEART("Saturated heart", 27641),
	IMBUED_HEART("Imbued heart", 20724),
	FORGOTTEN_BREW("Forgotten brew", 27629),
	SUPER_ATTACK("Super attack", 2436),
	SUPER_STRENGTH("Super strength", 2440),
	ANCIENT_BREW("Ancient brew", 26340),
	OVERLOAD("Overload", 11730),
	MAGIC("Magic potion", 3040),
	ATTACK("Attack potion", 2428),
	STRENGTH("Strength potion", 113),
	SUPER_RANGING("Super ranging", 11722),
	SUPER_MAGIC("Super magic", 11727),
	DEFENCE("Defence potion", 2432),
	SUPER_DEFENCE("Super defence", 2442),
	RUBY_HARVEST("Ruby Harvest", 10020),
	BLACK_WARLOCK("Black Warlock", 10014),
	SAPPHIRE_GLACIALIS("Sapphire Glacialis", 10018),
	MOONLIGHT("Moonlight potion", 29081);

	private final String displayName;

	/** The item to draw beside the name. Dose does not matter — the icons are the same. */
	private final int itemId;

	Potion(String displayName, int itemId)
	{
		this.displayName = displayName;
		this.itemId = itemId;
	}

	public int getItemId()
	{
		return itemId;
	}

	public int attackBoost(PlayerLevels levels)
	{
		int level = levels.getAttack();

		switch (this)
		{
			case OVERLOAD_PLUS:
				return scaled(6, 0.16, level);
			case SMELLING_SALTS:
				return scaled(11, 0.16, level);
			case OVERLOAD:
				return scaled(5, 0.13, level);
			case SUPER_COMBAT:
			case SUPER_ATTACK:
				return scaled(5, 0.15, level);
			case RUBY_HARVEST:
				return scaled(4, 0.15, level);
			case ATTACK:
				return scaled(3, 0.10, level);
			// The brews trade melee levels away for Magic.
			case FORGOTTEN_BREW:
			case ANCIENT_BREW:
				return drain(level);
			case MOONLIGHT:
				return moonlight(levels.getHerblore(), 45, 3, level);
			default:
				return 0;
		}
	}

	public int strengthBoost(PlayerLevels levels)
	{
		int level = levels.getStrength();

		switch (this)
		{
			case OVERLOAD_PLUS:
				return scaled(6, 0.16, level);
			case SMELLING_SALTS:
				return scaled(11, 0.16, level);
			case OVERLOAD:
				return scaled(5, 0.13, level);
			case SUPER_COMBAT:
			case SUPER_STRENGTH:
				return scaled(5, 0.15, level);
			case BLACK_WARLOCK:
				return scaled(4, 0.15, level);
			case STRENGTH:
				return scaled(3, 0.10, level);
			case FORGOTTEN_BREW:
			case ANCIENT_BREW:
				return drain(level);
			case MOONLIGHT:
				return moonlight(levels.getHerblore(), 55, 12, level);
			default:
				return 0;
		}
	}

	public int rangedBoost(PlayerLevels levels)
	{
		int level = levels.getRanged();

		switch (this)
		{
			case OVERLOAD_PLUS:
				return scaled(6, 0.16, level);
			case SMELLING_SALTS:
				return scaled(11, 0.16, level);
			case OVERLOAD:
				return scaled(5, 0.13, level);
			case SUPER_RANGING:
				return scaled(5, 0.15, level);
			case RANGING:
				return scaled(4, 0.10, level);
			default:
				return 0;
		}
	}

	public int magicBoost(PlayerLevels levels)
	{
		int level = levels.getMagic();

		switch (this)
		{
			case OVERLOAD_PLUS:
				return scaled(6, 0.16, level);
			case SMELLING_SALTS:
				return scaled(11, 0.16, level);
			case OVERLOAD:
				return scaled(5, 0.13, level);
			case SUPER_MAGIC:
				return scaled(5, 0.15, level);
			case SATURATED_HEART:
				return scaled(4, 0.10, level);
			case FORGOTTEN_BREW:
				return scaled(3, 0.08, level);
			case ANCIENT_BREW:
				return scaled(2, 0.05, level);
			case IMBUED_HEART:
				return scaled(1, 0.10, level);
			// The only flat boost in the list.
			case MAGIC:
				return 4;
			default:
				return 0;
		}
	}

	private static int scaled(int flat, double proportion, int level)
	{
		return (int) Math.floor(flat + level * proportion);
	}

	/**
	 * Ancient and Forgotten brews drain melee stats to buy Magic.
	 */
	private static int drain(int level)
	{
		return (int) Math.floor(-2 - level * 0.10);
	}

	/**
	 * Moonlight potion scales with Herblore rather than being a fixed strength.
	 */
	private static int moonlight(int herblore, int highThreshold, int lowThreshold, int level)
	{
		if (herblore >= highThreshold)
		{
			return scaled(5, 0.15, level);
		}

		if (herblore >= lowThreshold)
		{
			return scaled(3, 0.10, level);
		}

		return 0;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
