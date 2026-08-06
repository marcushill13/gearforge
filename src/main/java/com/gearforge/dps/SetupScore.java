package com.gearforge.dps;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * The result of scoring one setup, including the intermediate rolls.
 * <p>
 * The breakdown is not debug output — the spec requires the BiS tab to always show its reasoning, so
 * accuracy, max hit and the applied effects are part of the product.
 */
@Value
@Builder
public class SetupScore
{
	/** Damage per second against this target. */
	double dps;

	/** Probability an attack lands, 0 to 1. */
	double hitChance;

	int maxHit;

	int attackRoll;
	int defenceRoll;

	int effectiveAttackLevel;
	int effectiveStrengthLevel;

	/** Attack interval in game ticks, after any style modifier. */
	int attackSpeedTicks;

	/** Human-readable reasons, e.g. "Salve (ei): target is undead". */
	@Singular
	List<String> appliedEffects;

	public double accuracyPercent()
	{
		return hitChance * 100.0;
	}
}
