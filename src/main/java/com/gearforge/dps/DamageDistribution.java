package com.gearforge.dps;

import java.util.Arrays;

/**
 * The probability of each damage value a single attack can deal.
 * <p>
 * The engine's ordinary scoring collapses an attack to one average, which is exact for a weapon that
 * rolls once and enough for ranking gear. It cannot express anything that rolls more than once: a
 * scythe's three swings, or a claw special where a miss cascades into smaller guaranteed hits. The
 * average of those is not the average of one roll times a count, because the rolls are not
 * independent and their sizes differ.
 * <p>
 * Index is damage, value is probability. Index 0 is a miss or a zero-damage hit.
 */
public final class DamageDistribution
{
	private final double[] probabilities;

	private DamageDistribution(double[] probabilities)
	{
		this.probabilities = probabilities;
	}

	/**
	 * The standard attack: a miss deals nothing, a hit rolls uniformly up to the max hit.
	 * <p>
	 * The zero roll landing as 1 is what puts the {@code 1/(maxHit+1)} term in the closed-form average
	 * the engine already uses, so this reproduces that number exactly rather than approximating it.
	 */
	public static DamageDistribution roll(double hitChance, int maxHit)
	{
		if (maxHit <= 0)
		{
			return certain(0);
		}

		double[] probabilities = new double[maxHit + 1];
		double perRoll = hitChance / (maxHit + 1);

		probabilities[0] = 1 - hitChance;
		for (int damage = 1; damage <= maxHit; damage++)
		{
			probabilities[damage] = perRoll;
		}

		// A damage roll of zero on a landed hit still deals 1.
		probabilities[1] += perRoll;

		return new DamageDistribution(probabilities);
	}

	/**
	 * A hit that cannot miss and rolls uniformly between two bounds — the voidwaker's special, where
	 * accuracy plays no part at all.
	 */
	public static DamageDistribution uniform(int minimum, int maximum)
	{
		int low = Math.max(0, Math.min(minimum, maximum));
		int high = Math.max(0, Math.max(minimum, maximum));

		double[] probabilities = new double[high + 1];
		double each = 1.0 / (high - low + 1);
		for (int damage = low; damage <= high; damage++)
		{
			probabilities[damage] = each;
		}

		return new DamageDistribution(probabilities);
	}

	/**
	 * A fixed outcome. Used as the identity for summing and for attacks that cannot damage.
	 */
	public static DamageDistribution certain(int damage)
	{
		double[] probabilities = new double[Math.max(0, damage) + 1];
		probabilities[Math.max(0, damage)] = 1.0;
		return new DamageDistribution(probabilities);
	}

	/**
	 * Two independent hits landing together, as one distribution.
	 */
	public DamageDistribution plus(DamageDistribution other)
	{
		double[] combined = new double[probabilities.length + other.probabilities.length - 1];

		for (int mine = 0; mine < probabilities.length; mine++)
		{
			if (probabilities[mine] == 0)
			{
				continue;
			}

			for (int theirs = 0; theirs < other.probabilities.length; theirs++)
			{
				combined[mine + theirs] += probabilities[mine] * other.probabilities[theirs];
			}
		}

		return new DamageDistribution(combined);
	}

	/**
	 * Either this distribution or the other, weighted — for a spec whose outcome depends on an earlier
	 * roll, such as claws cascading differently depending on which swing first connects.
	 *
	 * @param chance probability of this distribution rather than the other, 0 to 1
	 */
	public DamageDistribution or(double chance, DamageDistribution other)
	{
		double[] mixed = new double[Math.max(probabilities.length, other.probabilities.length)];

		for (int damage = 0; damage < probabilities.length; damage++)
		{
			mixed[damage] += probabilities[damage] * chance;
		}

		for (int damage = 0; damage < other.probabilities.length; damage++)
		{
			mixed[damage] += other.probabilities[damage] * (1 - chance);
		}

		return new DamageDistribution(mixed);
	}

	public double mean()
	{
		double total = 0;
		for (int damage = 1; damage < probabilities.length; damage++)
		{
			total += damage * probabilities[damage];
		}

		return total;
	}

	/**
	 * The largest damage that can actually occur.
	 */
	public int maximum()
	{
		for (int damage = probabilities.length - 1; damage > 0; damage--)
		{
			if (probabilities[damage] > 0)
			{
				return damage;
			}
		}

		return 0;
	}

	/**
	 * Probability of dealing no damage at all.
	 */
	public double missChance()
	{
		return probabilities.length == 0 ? 1 : probabilities[0];
	}

	/**
	 * Probability of dealing exactly this much damage. Out-of-range values are simply impossible.
	 */
	public double probabilityOf(int damage)
	{
		return damage < 0 || damage >= probabilities.length ? 0 : probabilities[damage];
	}

	@Override
	public String toString()
	{
		return "DamageDistribution{mean=" + mean() + ", max=" + maximum() + '}';
	}

	@Override
	public boolean equals(Object other)
	{
		return other instanceof DamageDistribution
			&& Arrays.equals(probabilities, ((DamageDistribution) other).probabilities);
	}

	@Override
	public int hashCode()
	{
		return Arrays.hashCode(probabilities);
	}
}
