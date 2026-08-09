package com.gearforge;

import com.gearforge.dps.DamageDistribution;
import com.gearforge.dps.DpsEngine;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The distribution has to agree with the closed-form average the engine already uses, or introducing
 * it would quietly move every DPS number in the plugin.
 */
public class DamageDistributionTest
{
	private static final double EXACT = 1e-9;

	private final DpsEngine engine = new DpsEngine();

	@Test
	public void aSingleRollMatchesTheClosedFormAverage()
	{
		for (int maxHit = 1; maxHit <= 60; maxHit++)
		{
			for (double hitChance = 0.05; hitChance < 1.0; hitChance += 0.05)
			{
				assertEquals("max hit " + maxHit + " at " + hitChance,
					engine.averageDamage(hitChance, maxHit),
					DamageDistribution.roll(hitChance, maxHit).mean(),
					EXACT);
			}
		}
	}

	@Test
	public void probabilitiesSumToOne()
	{
		DamageDistribution triple = DamageDistribution.roll(0.6, 40)
			.plus(DamageDistribution.roll(0.6, 20))
			.plus(DamageDistribution.roll(0.6, 10));

		// Every outcome is covered exactly once, which is the property convolution can silently break.
		double total = 0;
		for (int damage = 0; damage <= triple.maximum(); damage++)
		{
			total += triple.probabilityOf(damage);
		}

		assertEquals(1.0, total, EXACT);
	}

	@Test
	public void independentHitsAddTheirAverages()
	{
		DamageDistribution first = DamageDistribution.roll(0.75, 30);
		DamageDistribution second = DamageDistribution.roll(0.75, 15);

		assertEquals(first.mean() + second.mean(), first.plus(second).mean(), EXACT);
		assertEquals(45, first.plus(second).maximum());
	}

	@Test
	public void bothHitsMustMissForTheAttackToDoNothing()
	{
		DamageDistribution first = DamageDistribution.roll(0.5, 20);
		DamageDistribution second = DamageDistribution.roll(0.5, 20);

		// A three-swing weapon connects far more often than a one-swing weapon of the same accuracy;
		// that is most of why the scythe cannot be modelled as a single roll.
		assertEquals(0.25, first.plus(second).missChance(), EXACT);
	}

	@Test
	public void aGuaranteedHitNeverMisses()
	{
		DamageDistribution voidwaker = DamageDistribution.uniform(25, 75);

		assertEquals(0.0, voidwaker.missChance(), EXACT);
		assertEquals(50.0, voidwaker.mean(), EXACT);
		assertEquals(75, voidwaker.maximum());
	}

	@Test
	public void weightedOutcomesMixInProportion()
	{
		DamageDistribution big = DamageDistribution.certain(100);
		DamageDistribution small = DamageDistribution.certain(0);

		assertEquals(30.0, big.or(0.3, small).mean(), EXACT);
	}

	@Test
	public void aZeroMaxHitCannotDealDamage()
	{
		assertEquals(0.0, DamageDistribution.roll(1.0, 0).mean(), EXACT);
		assertTrue(DamageDistribution.roll(1.0, 0).missChance() > 0.99);
	}
}
