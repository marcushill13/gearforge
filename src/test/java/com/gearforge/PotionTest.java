package com.gearforge;

import com.gearforge.data.PlayerLevels;
import com.gearforge.dps.Potion;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Boost values checked against the OSRS Wiki DPS calculator's own potion definitions.
 */
public class PotionTest
{
	private static final PlayerLevels MAXED = PlayerLevels.maxed();

	@Test
	public void superCombatBoostsMeleeOnly()
	{
		// floor(5 + 99 * 0.15) = 19
		assertEquals(19, Potion.SUPER_COMBAT.attackBoost(MAXED));
		assertEquals(19, Potion.SUPER_COMBAT.strengthBoost(MAXED));
		assertEquals(0, Potion.SUPER_COMBAT.rangedBoost(MAXED));
		assertEquals(0, Potion.SUPER_COMBAT.magicBoost(MAXED));
	}

	@Test
	public void rangingAndSuperRangingDiffer()
	{
		// floor(4 + 99 * 0.10) = 13, versus floor(5 + 99 * 0.15) = 19
		assertEquals(13, Potion.RANGING.rangedBoost(MAXED));
		assertEquals(19, Potion.SUPER_RANGING.rangedBoost(MAXED));
	}

	@Test
	public void overloadsBoostEverything()
	{
		// Overload floor(5 + 99 * 0.13) = 17; Overload (+) floor(6 + 99 * 0.16) = 21
		assertEquals(17, Potion.OVERLOAD.attackBoost(MAXED));
		assertEquals(17, Potion.OVERLOAD.rangedBoost(MAXED));
		assertEquals(17, Potion.OVERLOAD.magicBoost(MAXED));

		assertEquals(21, Potion.OVERLOAD_PLUS.attackBoost(MAXED));
		assertEquals(21, Potion.OVERLOAD_PLUS.magicBoost(MAXED));

		// floor(11 + 99 * 0.16) = 26
		assertEquals(26, Potion.SMELLING_SALTS.strengthBoost(MAXED));
	}

	@Test
	public void brewsTradeMeleeAwayForMagic()
	{
		// The drain is the point: a brew is not a free upgrade. floor(-2 - 99 * 0.10) = -12, and it
		// floors away from zero rather than truncating toward it.
		assertEquals(-12, Potion.FORGOTTEN_BREW.attackBoost(MAXED));
		assertEquals(-12, Potion.ANCIENT_BREW.strengthBoost(MAXED));

		// floor(3 + 99 * 0.08) = 10, floor(2 + 99 * 0.05) = 6
		assertEquals(10, Potion.FORGOTTEN_BREW.magicBoost(MAXED));
		assertEquals(6, Potion.ANCIENT_BREW.magicBoost(MAXED));
	}

	@Test
	public void heartsAndMagicPotionsDiffer()
	{
		// floor(4 + 9.9) = 13, floor(1 + 9.9) = 10, and a flat 4
		assertEquals(13, Potion.SATURATED_HEART.magicBoost(MAXED));
		assertEquals(10, Potion.IMBUED_HEART.magicBoost(MAXED));
		assertEquals(4, Potion.MAGIC.magicBoost(MAXED));
	}

	@Test
	public void moonlightScalesWithHerblore()
	{
		PlayerLevels lowHerblore = PlayerLevels.builder()
			.attack(99).strength(99).magic(99).ranged(99).herblore(10).build();
		PlayerLevels midHerblore = PlayerLevels.builder()
			.attack(99).strength(99).magic(99).ranged(99).herblore(50).build();

		// Below 45 Herblore the attack boost is the weaker tier; above it, the stronger one.
		assertEquals(12, Potion.MOONLIGHT.attackBoost(lowHerblore));
		assertEquals(19, Potion.MOONLIGHT.attackBoost(midHerblore));

		PlayerLevels noHerblore = PlayerLevels.builder()
			.attack(99).strength(99).magic(99).ranged(99).herblore(1).build();
		assertEquals(0, Potion.MOONLIGHT.attackBoost(noHerblore));
	}

	@Test
	public void noPotionBoostsNothing()
	{
		assertEquals(0, Potion.NONE.attackBoost(MAXED));
		assertEquals(0, Potion.NONE.strengthBoost(MAXED));
		assertEquals(0, Potion.NONE.rangedBoost(MAXED));
		assertEquals(0, Potion.NONE.magicBoost(MAXED));
	}

	@Test
	public void everyPotionIsNamedForDisplay()
	{
		for (Potion potion : Potion.values())
		{
			assertTrue(potion.name(), potion.toString().length() > 2);
		}
	}
}
