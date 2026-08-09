package com.gearforge.dps;

/**
 * Turns a special attack into the damage it actually deals, as a distribution.
 * <p>
 * These are the shapes an ordinary attack roll cannot express, which is the whole reason
 * {@link DamageDistribution} exists.
 */
public final class SpecDamage
{
	private SpecDamage()
	{
	}

	/**
	 * @param hitChance accuracy of the spec itself, after its own multiplier
	 * @param maxHit    the setup's ordinary max hit, after the spec's damage multiplier
	 */
	public static DamageDistribution of(SpecialAttack special, double hitChance, int maxHit)
	{
		return of(special, hitChance, maxHit, 1);
	}

	/**
	 * @param targetSize tiles across, which decides whether a halberd's second hit lands
	 */
	public static DamageDistribution of(
		SpecialAttack special, double hitChance, int maxHit, int targetSize)
	{
		switch (special.getShape())
		{
			case SWEEP:
			{
				DamageDistribution first = DamageDistribution.roll(hitChance, maxHit);
				// The second swing only reaches something bigger than a single tile, and lands less
				// often when it does.
				return targetSize > 1
					? first.plus(DamageDistribution.roll(hitChance * 0.75, maxHit))
					: first;
			}
			case CASCADE:
				return cascade(hitChance, maxHit);
			case GUARANTEED_HALF_TO_ONE_AND_A_HALF:
				return DamageDistribution.uniform(maxHit / 2, maxHit * 3 / 2);
			case FOUR_HITS:
			{
				DamageDistribution shot = DamageDistribution.roll(hitChance, maxHit);
				return shot.plus(shot).plus(shot).plus(shot);
			}
			case MAGIC_LEVEL_MAX:
				return DamageDistribution.roll(hitChance, maxHit);
			case TWO_HITS:
				return DamageDistribution.roll(hitChance, maxHit)
					.plus(DamageDistribution.roll(hitChance, maxHit));
			case DEFENCE_REDUCTION:
			case SINGLE_HIT:
			default:
				return DamageDistribution.roll(hitChance, maxHit);
		}
	}

	/**
	 * Dragon claws. Four accuracy rolls; the first one that connects decides the shape of all four
	 * hits, and every hit after it is guaranteed.
	 * <p>
	 * Transcribed from the weapon's page:
	 * <ul>
	 *     <li>first connects — the hit rolls between half the max and one below it, then each following
	 *     hit is half the one before, with the last a point higher (35–17–8–9)</li>
	 *     <li>second connects — it rolls between 3/8 and 7/8 of max, then halves down (0–30–15–16)</li>
	 *     <li>third connects — it rolls between a quarter and three quarters, and the fourth matches it
	 *     (0–0–22–23)</li>
	 *     <li>fourth connects — it rolls between a quarter and five quarters of max (0–0–0–46)</li>
	 *     <li>all four miss — 2 damage two thirds of the time, otherwise nothing</li>
	 * </ul>
	 * That last line is why claws are never a dead spec, and it is exactly the sort of detail a
	 * multiplier model throws away.
	 */
	private static DamageDistribution cascade(double hitChance, int maxHit)
	{
		if (maxHit <= 0)
		{
			return DamageDistribution.certain(0);
		}

		DamageDistribution first = cascadeFrom(maxHit / 2, maxHit - 1, true);
		DamageDistribution second = cascadeFrom(maxHit * 3 / 8, maxHit * 7 / 8, true);
		DamageDistribution third = pairedFrom(maxHit / 4, maxHit * 3 / 4);
		DamageDistribution fourth = DamageDistribution.uniform(maxHit / 4, maxHit * 5 / 4);

		// Two thirds of a complete miss still lands the consolation 2.
		DamageDistribution allMissed = DamageDistribution.certain(2)
			.or(2.0 / 3.0, DamageDistribution.certain(0));

		// Walked backwards: the fourth roll only matters if the first three missed, and so on.
		DamageDistribution outcome = fourth.or(hitChance, allMissed);
		outcome = third.or(hitChance, outcome);
		outcome = second.or(hitChance, outcome);
		return first.or(hitChance, outcome);
	}

	/**
	 * A landed first hit plus the halving tail that follows it.
	 *
	 * @param withFourth whether a fourth hit follows, one point above the third
	 */
	private static DamageDistribution cascadeFrom(int minimum, int maximum, boolean withFourth)
	{
		int low = Math.max(0, minimum);
		int high = Math.max(low, maximum);

		double[] totals = new double[high * 2 + 4];
		double each = 1.0 / (high - low + 1);

		for (int hit = low; hit <= high; hit++)
		{
			int second = hit / 2;
			int third = second / 2;
			int total = hit + second + third + (withFourth ? third + 1 : 0);
			totals[Math.min(total, totals.length - 1)] += each;
		}

		return DamageDistribution.fromProbabilities(totals);
	}

	/**
	 * The third-roll case: the hit lands and the fourth matches it, a point higher.
	 */
	private static DamageDistribution pairedFrom(int minimum, int maximum)
	{
		int low = Math.max(0, minimum);
		int high = Math.max(low, maximum);

		double[] totals = new double[high * 2 + 3];
		double each = 1.0 / (high - low + 1);

		for (int hit = low; hit <= high; hit++)
		{
			totals[hit + hit + 1] += each;
		}

		return DamageDistribution.fromProbabilities(totals);
	}
}
