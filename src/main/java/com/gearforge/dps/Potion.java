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
	NONE("No potion"),

	OVERLOAD_PLUS("Overload (+)"),
	SMELLING_SALTS("Smelling salts"),
	SUPER_COMBAT("Super combat"),
	RANGING("Ranging potion"),
	SATURATED_HEART("Saturated heart"),
	IMBUED_HEART("Imbued heart"),
	FORGOTTEN_BREW("Forgotten brew"),
	SUPER_ATTACK("Super attack"),
	SUPER_STRENGTH("Super strength"),
	ANCIENT_BREW("Ancient brew"),
	OVERLOAD("Overload"),
	MAGIC("Magic potion"),
	ATTACK("Attack potion"),
	STRENGTH("Strength potion"),
	SUPER_RANGING("Super ranging"),
	SUPER_MAGIC("Super magic"),
	DEFENCE("Defence potion"),
	SUPER_DEFENCE("Super defence"),
	RUBY_HARVEST("Ruby Harvest"),
	BLACK_WARLOCK("Black Warlock"),
	SAPPHIRE_GLACIALIS("Sapphire Glacialis"),
	MOONLIGHT("Moonlight potion");

	private final String displayName;

	Potion(String displayName)
	{
		this.displayName = displayName;
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
