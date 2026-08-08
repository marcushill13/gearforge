package com.gearforge.dps;

import lombok.Getter;

/**
 * Prayer multipliers, applied to the base level before anything else.
 * <p>
 * Attack and strength multipliers differ for the same prayer — Piety is 1.20 accuracy but 1.23
 * strength, and Rigour is 1.20 ranged attack but 1.23 ranged strength. Collapsing them into one
 * number is a common source of wrong DPS numbers.
 */
@Getter
public enum CombatPrayer
{
	NONE(1.0, 1.0, 1.0, 1.0, 1.0),

	BURST_OF_STRENGTH(1.0, 1.05, 1.0, 1.0, 1.0),
	SUPERHUMAN_STRENGTH(1.0, 1.10, 1.0, 1.0, 1.0),
	ULTIMATE_STRENGTH(1.0, 1.15, 1.0, 1.0, 1.0),
	CLARITY_OF_THOUGHT(1.05, 1.0, 1.0, 1.0, 1.0),
	IMPROVED_REFLEXES(1.10, 1.0, 1.0, 1.0, 1.0),
	INCREDIBLE_REFLEXES(1.15, 1.0, 1.0, 1.0, 1.0),
	CHIVALRY(1.15, 1.18, 1.0, 1.0, 1.0),
	PIETY(1.20, 1.23, 1.0, 1.0, 1.0),

	SHARP_EYE(1.0, 1.0, 1.05, 1.05, 1.0),
	HAWK_EYE(1.0, 1.0, 1.10, 1.10, 1.0),
	EAGLE_EYE(1.0, 1.0, 1.15, 1.15, 1.0),
	DEADEYE(1.0, 1.0, 1.18, 1.18, 1.0),
	RIGOUR(1.0, 1.0, 1.20, 1.23, 1.0),

	MYSTIC_WILL(1.0, 1.0, 1.0, 1.0, 1.05),
	MYSTIC_LORE(1.0, 1.0, 1.0, 1.0, 1.10),
	MYSTIC_MIGHT(1.0, 1.0, 1.0, 1.0, 1.15),
	AUGURY(1.0, 1.0, 1.0, 1.0, 1.25);

	private final double attack;
	private final double strength;
	private final double rangedAttack;
	private final double rangedStrength;
	private final double magic;

	CombatPrayer(double attack, double strength, double rangedAttack, double rangedStrength, double magic)
	{
		this.attack = attack;
		this.strength = strength;
		this.rangedAttack = rangedAttack;
		this.rangedStrength = rangedStrength;
		this.magic = magic;
	}

	/**
	 * The strongest prayer for a style — what someone planning a trip would actually have on.
	 */
	public static CombatPrayer bestFor(CombatStyle style)
	{
		if (style.isRanged())
		{
			return RIGOUR;
		}

		if (style.isMagic())
		{
			return AUGURY;
		}

		return PIETY;
	}
}
