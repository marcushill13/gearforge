package com.gearforge.dps;

/**
 * Void multipliers. Only applies when the full set is worn, which the caller is responsible for
 * establishing — {@code SetEffectRegistry} will do that once it exists.
 * <p>
 * Elite void is identical to regular void for melee and for ranged accuracy; it differs only in
 * ranged strength (1.125) and in magic damage, which is handled as a damage-side set effect rather
 * than here.
 */
public enum VoidSet
{
	NONE(1.0, 1.0),
	MELEE(1.1, 1.1),
	RANGED(1.1, 1.1),
	ELITE_RANGED(1.1, 1.125),
	MAGIC(1.45, 1.0);

	private final double accuracy;
	private final double strength;

	VoidSet(double accuracy, double strength)
	{
		this.accuracy = accuracy;
		this.strength = strength;
	}

	public double accuracyMultiplier()
	{
		return accuracy;
	}

	public double strengthMultiplier()
	{
		return strength;
	}
}
